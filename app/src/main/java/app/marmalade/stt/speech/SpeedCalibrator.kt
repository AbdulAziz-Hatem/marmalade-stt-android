package app.marmalade.stt.speech

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import app.marmalade.stt.benchmark.Wav16k
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.install.EngineRecommender
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.install.SpeedBudget
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.install.SttEngineDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What [SpeedCalibrator] reads from the phone: its RAM and the speed probe.
 * An interface so the JVM tests can run the calibrator without Android.
 */
interface PhoneHardware {
    /** Total RAM in bytes (`ActivityManager.MemoryInfo.totalMem`). */
    fun totalMemBytes(): Long

    /** The speed probe's time in ms ([SpeedCalibrator.probeMs]). Blocking, about a second. */
    fun probeMs(): Long
}

/** [PhoneHardware] on the device. */
class AndroidPhoneHardware @Inject constructor(@ApplicationContext private val context: Context) : PhoneHardware {
    override fun totalMemBytes(): Long = ActivityManager.MemoryInfo().also {
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
    }.totalMem

    override fun probeMs(): Long = SpeedCalibrator.probeMs(context)
}

/**
 * Measures how fast this phone is against the Pixel 8a (#24, #27) and
 * recommends an engine for it. The measurement is [SpeedProbe]: the bundled
 * Silero VAD over the bundled calibration clip, about a second of one core,
 * with no engine installed. Its time and the phone's recognizer thread count
 * are stored in [SpeechSettings]; the speed factor is computed from them
 * ([SpeedBudget.speedFactor]), so a new reference time applies without
 * measuring again. Runs in its own scope, one measurement at a time.
 *
 * Nothing is recorded: the clip is a bundled asset.
 */
@Singleton
class SpeedCalibrator @Inject constructor(
    private val hardware: PhoneHardware,
    private val settings: SpeechSettings,
    private val installer: ModelInstaller,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)

    /** True while the probe runs. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Measure if this phone never was, then store the recommendation (the Engines tab calls it). */
    fun ensureMeasured() {
        scope.launch { recommend() }
    }

    /**
     * Recommend again without measuring: Settings → Recognition language
     * changed, the stored engine left the app (#45), or the recommendation
     * rules changed (#50). The returned job is for tests.
     */
    fun recommendAgain(): Job = scope.launch { storeRecommendation() }

    /**
     * Keep [tooSlow] ([SpeechEngineManager.tooSlow]) as evidence for the
     * speed budget: each engine that fell behind real time here is stored
     * ([SpeechSettings.addTooSlowEngine]) and the phone gets a new
     * recommendation. Started once, by the Application: sessions run in the
     * voice keyboard and RecognitionService too, with no screen open.
     */
    fun watch(tooSlow: Flow<SttEngineDescriptor>) {
        scope.launch {
            tooSlow.collect { engine ->
                Log.i(TAG, "${engine.id} fell behind real time: over budget on this phone")
                try {
                    settings.addTooSlowEngine(engine.id)
                    storeRecommendation()
                } catch (e: IOException) {
                    Log.w(TAG, "Couldn't store that ${engine.id} is too slow", e)
                }
            }
        }
    }

    /**
     * Settings → Developer → "Re-measure phone speed". Starts over: forgets
     * which engines fell behind. [onDone] gets the new speed factor, null on
     * failure.
     */
    fun remeasure(onDone: (Double?) -> Unit = {}) {
        scope.launch {
            settings.clearTooSlowEngines()
            val factor = measure()
            if (factor != null) storeRecommendation()
            onDone(factor)
        }
    }

    /**
     * The engine for this phone ([EngineRecommender]): the spoken language
     * (Settings → Recognition language, or the system's), RAM and
     * speed (measured first if it never was). Also stored as
     * [app.marmalade.stt.data.SpeechPrefs.recommendedEngineId].
     */
    suspend fun recommend(): EngineRecommender.Recommendation {
        if (settings.current().deviceProbeMs == null) measure()
        return storeRecommendation()
    }

    private suspend fun storeRecommendation(): EngineRecommender.Recommendation {
        val totalMem = hardware.totalMemBytes()
        settings.setDeviceTotalMem(totalMem)
        val prefs = settings.current()
        val recommendation = prefs.recommendation(totalMem)
        keepRunningEngine(prefs, recommendation.engine.id)
        settings.setRecommendedEngine(recommendation.engine.id)
        Log.i(TAG, "Recommended ${recommendation.engine.id} (${recommendation.reason}, ${recommendation.language})")
        return recommendation
    }

    /**
     * With nothing selected, the recommended engine runs
     * ([SttEngineCatalog.effective]). Before the recommendation moves (a new
     * recognition language, an engine that fell behind), pin the engine
     * running now if it is installed: a new recommendation is a suggestion,
     * never a switch to an engine that isn't downloaded.
     */
    private suspend fun keepRunningEngine(prefs: SpeechPrefs, newRecommendation: String) {
        val pin = withContext(Dispatchers.IO) {
            prefs.engineToPin(newRecommendation) { installer.installedModel(it) != null }
        }
        if (pin != null) settings.setSelectedEngine(pin)
    }

    /** Run the probe and store it; the new speed factor, or null when it failed. */
    private suspend fun measure(): Double? = mutex.withLock {
        _running.value = true
        try {
            val threads = RecognizerThreads.count
            val ms = withContext(Dispatchers.Default) { hardware.probeMs() }
            settings.setDeviceProbe(ms, threads)
            SpeedBudget.speedFactor(ms).also {
                Log.i(TAG, "Probe $ms ms at $threads threads: ${"%.2f".format(it)}x a Pixel 8a")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't measure the phone's speed", e)
            null
        } catch (e: LinkageError) {
            Log.w(TAG, "Speech runtime failed to load", e)
            null
        } finally {
            _running.value = false
        }
    }

    companion object {
        private const val TAG = "SpeedCalibrator"

        /** The probe's time in ms on this phone. Blocking (about a second). The benchmark service calls it too. */
        internal fun probeMs(context: Context): Long {
            val clip = context.assets.open(SpeedBudget.CALIBRATION_ASSET).use { Wav16k.decode(it.readBytes()) }
            val vad = SherpaSileroVad.create(context.assets, SessionOptions.DEFAULT_MIN_SILENCE_SECONDS)
            return try {
                SpeedProbe.time(vad, clip)
            } finally {
                vad.release()
            }
        }
    }
}
