package app.marmalade.stt.speech

/**
 * The native stream an [OfflineTranscriber] is decoding, so another thread
 * can stop it ([OfflineTranscriber.cancel], #49). Pure JVM: [cancelStream]
 * does the native call.
 *
 * A session cancels by setting its own flag and then calling [cancel]. If
 * that falls after the session checked its flag but before the stream
 * existed, [cancel] finds nothing to stop; so [begin] publishes the stream
 * and reads the session's flag under the same lock [cancel] takes, and
 * cancels the stream itself when the flag is already set. [end] unpublishes
 * the stream before it is released, so a cancel never reaches a released one.
 */
internal class InFlightDecode<S : Any>(private val cancelStream: (S) -> Unit) {
    private val lock = Any()
    private var current: S? = null

    /** [stream] is about to be decoded; [isCancelled] is the caller's session's flag. */
    fun begin(stream: S, isCancelled: () -> Boolean) = synchronized(lock) {
        current = stream
        if (isCancelled()) cancelStream(stream)
    }

    /** The decode returned; its stream is about to be released. */
    fun end() = synchronized(lock) { current = null }

    /** Stop the decode in progress, if any. Safe from any thread. */
    fun cancel() = synchronized(lock) { current?.let(cancelStream) ?: Unit }
}
