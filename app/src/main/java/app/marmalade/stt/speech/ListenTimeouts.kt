package app.marmalade.stt.speech

/**
 * How long each listening surface waits for speech before it lets go of the
 * mic. A stop is always graceful: whatever was heard is still transcribed.
 */
object ListenTimeouts {
    /**
     * The speech-input sheet and the RecognitionService: one request for one
     * result, so a request that hears nothing this long gives up.
     */
    const val NO_SPEECH_TIMEOUT_MS = 6_000L

    /**
     * The voice keyboard: stop after this long without speech, for privacy
     * and battery. Much longer than [NO_SPEECH_TIMEOUT_MS] because people
     * pause to think while composing. The panel stays open, paused; one mic
     * tap resumes.
     */
    const val IME_IDLE_TIMEOUT_MS = 30_000L
}
