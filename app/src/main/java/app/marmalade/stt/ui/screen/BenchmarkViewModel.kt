package app.marmalade.stt.ui.screen

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.marmalade.stt.benchmark.BenchmarkEvent
import app.marmalade.stt.benchmark.BenchmarkReport
import app.marmalade.stt.benchmark.BenchmarkRunner
import app.marmalade.stt.benchmark.TakeCleanup
import app.marmalade.stt.benchmark.TakeRecorder
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.data.TextProcSettings
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.speech.MIC_SAMPLE_RATE
import app.marmalade.stt.speech.MicCaptureException
import app.marmalade.stt.speech.PhoneHardware
import app.marmalade.stt.speech.SpeechEngineManager
import app.marmalade.stt.speech.SttEngines
import app.marmalade.stt.speech.openMicCapture
import app.marmalade.stt.textproc.TextProcConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the benchmark screen stands. */
@Immutable
sealed interface BenchmarkPhase {
    /** No take yet. */
    data object Idle : BenchmarkPhase

    /** Recording a take. [level] is 0..1 for the meter. */
    data class Recording(val level: Float, val elapsedSeconds: Float) : BenchmarkPhase

    /** A take is in memory; nothing has run on it yet. */
    data object Recorded : BenchmarkPhase

    /** Running the engines. [results] are the engines done so far, in run order. */
    data class Running(val current: SttEngineDescriptor, val results: List<BenchmarkEvent.Outcome>) : BenchmarkPhase

    /** The run ended (or was stopped). The take is still in memory, so it can run again. */
    data class Done(val results: List<BenchmarkEvent.Outcome>) : BenchmarkPhase
}

/** A one-off message for the snackbar. */
enum class BenchmarkNotice { MIC_ERROR, EMPTY_TAKE, PLAYBACK_ERROR, NO_ENGINES, RUN_ERROR }

/**
 * @property takeSeconds the take's length, or null with no take.
 * @property softAgc the take went through [app.marmalade.stt.speech.SoftAgc]
 *   (the mic has no platform AGC), as live sessions on this phone do.
 * @property engines the installed, visible engines a run would use, in catalog order.
 */
@Immutable
data class BenchmarkUiState(
    val phase: BenchmarkPhase = BenchmarkPhase.Idle,
    val takeSeconds: Float? = null,
    val softAgc: Boolean = false,
    val engines: List<SttEngineDescriptor> = emptyList(),
    val playing: Boolean = false,
    val notice: BenchmarkNotice? = null,
) {
    val results: List<BenchmarkEvent.Outcome>
        get() = when (phase) {
            is BenchmarkPhase.Running -> phase.results
            is BenchmarkPhase.Done -> phase.results
            else -> emptyList()
        }
}

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   Record ─► TakeRecorder(openMicCapture) ─► hops high-passed (+ SoftAgc
//             without a platform AGC, as live) ─► RecordedTake(samples, softAgc)
//     │         level/elapsed per hop ─► phase Recording(level, elapsed)
//     └─► take: FloatArray held HERE ONLY (never on disk, never logged; D10)
//   Play  ─► AudioTrack(PCM_FLOAT, 16 kHz mono, MODE_STATIC) ◄─ take
//   Run   ─► targets = SttEngineCatalog.runnable engines
//                        ∩ installer.installedModel(id) != null
//          ─► engines.releaseModel()        (the user's engine leaves RAM)
//          ─► BenchmarkRunner.run(take, targets)
//               per engine: SttEngines.loadForTake(installed, assets,
//                 prefs.languageFor(engine)) → transcribeUtterances → release
//                 → TakeCleanup(textProc config, prefs).clean: each utterance
//                   a Final through dictation's cleanup (Dictation.commitFinal)
//               Started ─► phase Running(current, results)
//               Finished / Failed ─► appended to results
//          ─► finally: phase Done(results); engines.warmUp()
//   Reference text lives in the screen (WER of the cleaned text is computed
//   from it at display and export time: wordErrorRate). Copy ─►
//   BenchmarkReport.markdownTable.
//   onCleared: cancel (the runner releases the engine in flight), stop
//   playback, zero and drop the take.
// -----------------------------------------------------------------------------

/**
 * State and actions for [BenchmarkScreen], the developer A/B benchmark (#13):
 * one mic take, run through every installed engine in turn.
 */
@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installer: ModelInstaller,
    private val settings: SpeechSettings,
    private val textProc: TextProcSettings,
    private val engines: SpeechEngineManager,
    private val hardware: PhoneHardware,
) : ViewModel() {

    private val _state = MutableStateFlow(BenchmarkUiState())
    val state: StateFlow<BenchmarkUiState> = _state.asStateFlow()

    /** The recorded take. In memory only; zeroed and dropped in [onCleared]. */
    private var take: FloatArray? = null

    private var recorder: TakeRecorder? = null
    private var recordJob: Job? = null
    private var runJob: Job? = null
    private var playJob: Job? = null
    private var track: AudioTrack? = null

    init {
        // The engine list follows Settings → Developer engines.
        viewModelScope.launch {
            settings.prefs
                .catch { e -> Log.w(TAG, "Couldn't read the speech settings", e) }
                .collect { prefs -> refreshEngines(prefs) }
        }
    }

    /** Re-check which engines are installed (the screen calls this on resume). */
    fun refreshEngines() {
        viewModelScope.launch {
            try {
                refreshEngines(settings.current())
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't read the speech settings", e)
            }
        }
    }

    private suspend fun refreshEngines(prefs: SpeechPrefs) {
        val list = installedEngines(prefs)
        _state.update { it.copy(engines = list) }
    }

    /** Engines that could run ([SttEngineCatalog.runnable]) whose files are on disk. File probes, so off the main thread. */
    private suspend fun installedEngines(prefs: SpeechPrefs): List<SttEngineDescriptor> = withContext(Dispatchers.IO) {
        SttEngineCatalog.engines
            .filter { prefs.runnable(it) }
            .filter { installer.installedModel(it.id) != null }
    }

    private val busy: Boolean
        get() = recordJob?.isActive == true || runJob?.isActive == true

    /** Record a new take, replacing the old one and its results. The caller has checked the mic permission. */
    fun record() {
        if (busy) return
        stopPlayback()
        take?.fill(0f)
        take = null
        val rec = TakeRecorder(::openMicCapture)
        recorder = rec
        _state.update { it.copy(phase = BenchmarkPhase.Recording(0f, 0f), takeSeconds = null) }
        recordJob = viewModelScope.launch {
            try {
                val recorded = rec.record { db, elapsed ->
                    _state.update {
                        if (it.phase is BenchmarkPhase.Recording) {
                            it.copy(phase = BenchmarkPhase.Recording(Dictation.levelFraction(db), elapsed))
                        } else {
                            it
                        }
                    }
                }
                val samples = recorded.samples
                if (samples.isEmpty()) {
                    _state.update { it.copy(phase = BenchmarkPhase.Idle, notice = BenchmarkNotice.EMPTY_TAKE) }
                } else {
                    take = samples
                    _state.update {
                        it.copy(
                            phase = BenchmarkPhase.Recorded,
                            takeSeconds = samples.size.toFloat() / MIC_SAMPLE_RATE,
                            softAgc = recorded.softAgc,
                        )
                    }
                }
            } catch (e: MicCaptureException) {
                Log.w(TAG, "Couldn't record the take", e)
                _state.update { it.copy(phase = BenchmarkPhase.Idle, notice = BenchmarkNotice.MIC_ERROR) }
            } finally {
                recorder = null
            }
        }
    }

    /** End the take now. */
    fun stopRecording() {
        recorder?.stop()
    }

    /** Run the take through every installed, visible engine, one at a time. */
    fun run() {
        val t = take ?: return
        if (busy) return
        stopPlayback()
        runJob = viewModelScope.launch {
            val prefs = try {
                settings.current()
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't read the speech settings", e)
                SpeechPrefs()
            }
            val targets = installedEngines(prefs)
            _state.update { it.copy(engines = targets) }
            if (targets.isEmpty()) {
                _state.update { it.copy(notice = BenchmarkNotice.NO_ENGINES) }
                return@launch
            }
            val results = mutableListOf<BenchmarkEvent.Outcome>()
            _state.update { it.copy(phase = BenchmarkPhase.Running(targets.first(), emptyList())) }
            val cleanup = TakeCleanup(cleanupConfig(), prefs)
            val runner = BenchmarkRunner(load = { d -> loadForTake(d, prefs) }, clean = cleanup::clean)
            try {
                // Only one model in memory at a time: the user's engine goes too.
                withContext(Dispatchers.Default) { engines.releaseModel() }
                runner.run(t, targets).collect { event ->
                    when (event) {
                        is BenchmarkEvent.Started -> _state.update {
                            it.copy(phase = BenchmarkPhase.Running(event.engine, results.toList()))
                        }
                        is BenchmarkEvent.Outcome -> {
                            results += event
                            _state.update {
                                val phase = it.phase
                                if (phase is BenchmarkPhase.Running) {
                                    it.copy(phase = phase.copy(results = results.toList()))
                                } else {
                                    it
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Engine failures arrive as Failed events; this is anything else.
                Log.e(TAG, "Benchmark run failed", e)
                _state.update { it.copy(notice = BenchmarkNotice.RUN_ERROR) }
            } finally {
                _state.update { it.copy(phase = BenchmarkPhase.Done(results.toList())) }
                // After the last benchmark engine is released (the runner's
                // flow completes only then), bring the user's engine back.
                withContext(NonCancellable + Dispatchers.Default) { engines.warmUp() }
            }
        }
    }

    /** The user's cleanup settings, as dictation reads them; the stock ones if they can't be read. */
    private suspend fun cleanupConfig(): TextProcConfig = try {
        textProc.config.first()
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't read the cleanup settings", e)
        TextProcConfig()
    }

    private fun loadForTake(engine: SttEngineDescriptor, prefs: SpeechPrefs) = SttEngines.loadForTake(
        installed = installer.installedModel(engine.id)
            ?: throw IllegalStateException("${engine.displayName} is no longer installed"),
        assets = context.assets,
        language = prefs.languageFor(engine),
        // As dictation loads it: without ONNX Runtime's arena where only that fits (#47).
        tuning = SpeechEngineManager.tuningFor(prefs, engine, hardware.totalMemBytes()),
    )

    /** Play the take through the speaker, or stop it if it is playing. */
    fun togglePlayback() {
        if (_state.value.playing) {
            stopPlayback()
            return
        }
        val t = take ?: return
        if (busy) return
        val audio = try {
            buildTrack(t)
        } catch (e: Exception) {
            // IllegalArgumentException / UnsupportedOperationException / IllegalStateException from AudioTrack.
            Log.w(TAG, "Couldn't play the take", e)
            _state.update { it.copy(notice = BenchmarkNotice.PLAYBACK_ERROR) }
            return
        }
        track = audio
        _state.update { it.copy(playing = true) }
        playJob = viewModelScope.launch {
            try {
                audio.play()
                // Static tracks stop by themselves at the end; poll the head to know when.
                while (isActive && audio.playState == AudioTrack.PLAYSTATE_PLAYING &&
                    audio.playbackHeadPosition < t.size
                ) {
                    delay(PLAYBACK_POLL_MS)
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Playback failed", e)
                _state.update { it.copy(notice = BenchmarkNotice.PLAYBACK_ERROR) }
            } finally {
                releaseTrack(audio)
                _state.update { it.copy(playing = false) }
            }
        }
    }

    private fun buildTrack(samples: FloatArray): AudioTrack {
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(MIC_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
            .build()
        try {
            val written = audio.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            check(written == samples.size) { "AudioTrack took $written of ${samples.size} samples" }
        } catch (e: Exception) {
            audio.release()
            throw e
        }
        return audio
    }

    private fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        track?.let(::releaseTrack)
    }

    private fun releaseTrack(audio: AudioTrack) {
        runCatching { audio.stop() }
        audio.release()
        if (track === audio) track = null
    }

    /** The screen went to the background: never keep the mic open or audio playing behind the user's back. */
    fun onBackground() {
        stopRecording()
        stopPlayback()
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    /** The results as a Markdown table for the clipboard (text only), or null before any result. */
    fun markdown(reference: String): String? {
        val s = _state.value
        val seconds = s.takeSeconds ?: return null
        if (s.results.isEmpty()) return null
        return BenchmarkReport.markdownTable(
            takeMs = (seconds * 1_000).toLong(),
            deviceModel = Build.MODEL,
            outcomes = s.results,
            softAgc = s.softAgc,
            reference = reference,
        )
    }

    override fun onCleared() {
        // The runner releases the engine it holds once its blocking call
        // returns, then the run's finally warms the user's engine back up.
        runJob?.cancel()
        recorder?.stop()
        recordJob?.cancel()
        stopPlayback()
        take?.fill(0f)
        take = null
    }

    private companion object {
        const val TAG = "BenchmarkViewModel"
        const val PLAYBACK_POLL_MS = 50L
    }
}
