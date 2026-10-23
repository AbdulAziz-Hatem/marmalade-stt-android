package app.marmalade.stt.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import app.marmalade.stt.benchmark.Wav16k
import app.marmalade.stt.speech.MIC_SAMPLE_RATE
import app.marmalade.stt.speech.MicCapture
import app.marmalade.stt.speech.MicOverride
import app.marmalade.stt.speech.StreamingSession
import app.marmalade.stt.speech.SpeedKnobs
import app.marmalade.stt.speech.openAndroidMic
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Debug builds only (#91): every listen's mic plays `files/debug-mic/clip.wav`
 * (16 kHz mono PCM16) in real time while the file exists, so timing passes on
 * the keyboard, the speak-now popup and Dictate are silent and identical turn
 * to turn. Remove the file and the real mic is back.
 *
 * The real microphone still opens and closes as in a normal listen (so the
 * mic-open cost and the platform AGC flag are real); its samples are read
 * and dropped. The session gets [LEAD_MS] of zeros, the clip, then zeros
 * until the listen stops, as a quiet room after the speaker finishes.
 *
 * ```
 * P=app.marmalade.stt.speedaudit   # or .debug
 * adb push ls10-1.wav /data/local/tmp/c.wav
 * adb shell run-as $P sh -c 'mkdir -p files/debug-mic && cp /data/local/tmp/c.wav files/debug-mic/clip.wav'
 * adb shell run-as $P rm files/debug-mic/clip.wav   # real mic again
 * ```
 *
 * Logs under [StreamingSession.TIMING_TAG] with [SystemClock.elapsedRealtime]
 * stamps: `clip mic open t=…` and `clip speech end t=…` (the clip's last
 * sample handed to the session), which the pass scripts pair with the
 * session's and the surfaces' own stamps.
 */
class ClipMic private constructor(
    private val clip: FloatArray,
    private val real: MicCapture,
) : MicCapture {

    @Volatile
    private var closed = false

    override val hasPlatformAgc: Boolean get() = real.hasPlatformAgc

    override val hops: Flow<FloatArray> = flow {
        val lead = LEAD_MS * MIC_SAMPLE_RATE / 1000
        val start = System.nanoTime()
        Log.d(StreamingSession.TIMING_TAG, "clip mic open t=${SystemClock.elapsedRealtime()} lead=${LEAD_MS}ms clip=${clip.size * 1000L / MIC_SAMPLE_RATE}ms")
        var from = 0
        var endLogged = false
        while (!closed) {
            val to = from + HOP
            val wait = (start + to * NANOS_PER_SAMPLE - System.nanoTime()) / 1_000_000L
            if (wait > 0) delay(wait)
            if (closed) break
            emit(FloatArray(HOP) { i -> clip.getOrElse(from + i - lead) { 0f } })
            from = to
            if (!endLogged && from >= lead + clip.size) {
                endLogged = true
                Log.d(StreamingSession.TIMING_TAG, "clip speech end t=${SystemClock.elapsedRealtime()}")
            }
        }
    }

    override fun close() {
        closed = true
        real.close()
    }

    /** Reads and drops the real mic's audio, so it records as in a normal listen. */
    private suspend fun drainReal() {
        real.hops.collect { }
    }

    companion object {
        const val LEAD_MS = 300
        private const val HOP = 1_600
        private const val NANOS_PER_SAMPLE = 1_000_000_000L / MIC_SAMPLE_RATE

        fun clipFile(context: Context) = File(context.filesDir, "debug-mic/clip.wav")

        /** The clip mic when [clipFile] exists, else null (the real mic). */
        fun openIfArmed(context: Context, hopSamples: Int, hardwareEffects: Boolean): MicCapture? {
            val file = clipFile(context)
            if (!file.isFile) return null
            require(hopSamples == HOP) { "ClipMic emits ${HOP}-sample hops, asked for $hopSamples" }
            val clip = Wav16k.decode(file.readBytes())
            return ClipMic(clip, openAndroidMic(hopSamples, hardwareEffects)).also { mic ->
                Thread({ kotlinx.coroutines.runBlocking { runCatching { mic.drainReal() } } }, "clip-mic-drain").start()
            }
        }
    }
}

/**
 * Installs [ClipMic] as the [MicOverride] when the process starts (a content
 * provider's onCreate runs before the Application's), so every surface,
 * including the keyboard's freshly started process, can use it. Debug
 * manifest only; not exported.
 */
class ClipMicInstaller : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context!!.applicationContext
        MicOverride.factory = { hop, effects -> ClipMic.openIfArmed(app, hop, effects) }
        DebugKnobs.apply(app)
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}

/**
 * #91: [SpeedKnobs] from `files/debug-mic/knobs` (`name=value` lines; a name
 * that isn't there keeps its default), applied
 * at process start and by SpeedBenchmarkService's `use --es knobs a=b,c=d`
 * (which writes the file), so a restarted keyboard process keeps them.
 */
object DebugKnobs {
    fun file(context: Context) = File(context.filesDir, "debug-mic/knobs")

    fun write(context: Context, spec: String) {
        file(context).apply { parentFile?.mkdirs() }.writeText(spec.split(',').joinToString("\n", postfix = "\n") { it.trim() })
        apply(context)
    }

    fun apply(context: Context) {
        val values = file(context).takeIf { it.isFile }?.readLines().orEmpty()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
            .toMap()
        SpeedKnobs.reset()
        values["speculativeFinal"]?.let { SpeedKnobs.speculativeFinal = it == "true" }
        values["trimAtStop"]?.let { SpeedKnobs.trimAtStop = it == "true" }
        values["partialAtOnset"]?.let { SpeedKnobs.partialAtOnset = it == "true" }
        values["verifySpeculation"]?.let { SpeedKnobs.verifySpeculation = it == "true" }
        SpeedKnobs.threads = values["threads"]?.toIntOrNull()
        SpeedKnobs.tuningId = values["tuning"]?.takeIf { it.isNotEmpty() }
        SpeedKnobs.smartTurnThreads = values["smartTurnThreads"]?.toIntOrNull()
        Log.i(StreamingSession.TIMING_TAG, "knobs: $SpeedKnobs")
    }
}
