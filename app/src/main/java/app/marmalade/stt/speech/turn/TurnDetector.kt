package app.marmalade.stt.speech.turn

/** "Has the speaker finished their turn?" for the audio of the turn so far. */
fun interface TurnDetector {
    /** Probability (0..1) that the turn is complete. [audio]: 16 kHz mono float, any length; only the last 8 s count. */
    fun completeProbability(audio: FloatArray): Float
}
