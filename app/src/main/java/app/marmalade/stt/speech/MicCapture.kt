package app.marmalade.stt.speech

import kotlinx.coroutines.flow.Flow

/** Sample rate every [MicCapture] delivers. The speech stack is 16 kHz mono throughout. */
const val MIC_SAMPLE_RATE = 16_000

/** Thrown when the microphone can't be opened, started or read. */
class MicCaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A started 16 kHz mono capture that yields fixed-size hops of float PCM in
 * [-1, 1]. The production implementation is [openMicCapture]; tests use fakes.
 *
 * Contract:
 * - The factory returns with the capture live (recording started) or throws
 *   [MicCaptureException]. There is no half-open state, so a caller can
 *   report "listening" as soon as it returns.
 * - [hops] is cold and adds no `flowOn`; collect it on a dispatcher that may
 *   block (the Android read blocks in JNI). A hop is normally `hopSamples`
 *   long but may be shorter after a partial read. It may throw
 *   [MicCaptureException] if the mic fails mid-capture.
 * - [close] is idempotent and callable from another thread. It unblocks a
 *   pending read and ends [hops].
 */
interface MicCapture : AutoCloseable {
    val hops: Flow<FloatArray>

    /**
     * The platform `AutomaticGainControl` is running on this capture. When
     * false, the sessions apply [SoftAgc] instead. Defaults to true: only a
     * capture that knows it has no gain control says otherwise.
     */
    val hasPlatformAgc: Boolean get() = true

    /**
     * A live source delivers audio in real time, whether or not it is read,
     * so it is read at once and kept in a queue (the mic). A non-live one (a
     * caller's stream, #90, possibly a file read as fast as possible) is read
     * only as fast as the session consumes it: nothing is dropped, and no lag
     * is measured against it, since being behind it says nothing about the
     * engine's speed ([EarlyMic], [micSession]). A caller streaming its own
     * mic in real time is fine as non-live too: waiting for the session never
     * holds a real-time producer up for long.
     */
    val live: Boolean get() = true

    /**
     * Hops at the start of [hops] that were captured before it was collected
     * ([EarlyMic]: the audio kept while the model loaded, #78). They come in
     * a burst; their level was already reported, and they count as captured
     * from the start, so the [LagMonitor] doesn't take the burst for an
     * engine falling behind. Fixed once [hops] is collected; 0 for a live mic.
     */
    val backlogHops: Int get() = 0
}

/**
 * Replaces the microphone [openMicCapture] opens. Only debug builds set it
 * (#91: `debug/ClipMic` feeds a test clip in real time, so timing passes are
 * silent and identical turn to turn); release builds never do. A factory
 * that returns null leaves the real microphone in place.
 */
object MicOverride {
    @Volatile
    var factory: ((hopSamples: Int, hardwareEffects: Boolean) -> MicCapture?)? = null
}
