package app.marmalade.stt.speech

/**
 * A short CPU probe that needs no engine (#27): the bundled Silero VAD over
 * the bundled calibration clip, [PASSES] times per run, [RUNS] timed runs
 * after a warm-up pass; the fastest run counts. Other work on the phone
 * (an app starting, a download being verified) can only slow a run down, so
 * the fastest is the closest to what the CPU can do: on the emulator, a busy
 * host moved the median from 197 to 525 ms. It gives the phone's speed
 * against the Pixel 8a before anything is downloaded
 * ([app.marmalade.stt.install.SpeedBudget.speedFactor]). Silero is a small
 * ONNX model on one thread: it tracks the single-core speed that engines
 * then spread over 2-4 threads.
 */
internal object SpeedProbe {
    const val PASSES = 8
    const val RUNS = 5

    /** The fastest run in ms. Blocking; [vad] is reset before each pass and left reset. */
    fun time(vad: SpeechVad, clip: FloatArray, nanoTime: () -> Long = System::nanoTime): Long {
        pass(vad, clip)
        val runs = List(RUNS) {
            val t0 = nanoTime()
            repeat(PASSES) { pass(vad, clip) }
            (nanoTime() - t0) / 1_000_000.0
        }
        vad.reset()
        return runs.min().toLong()
    }

    private fun pass(vad: SpeechVad, clip: FloatArray) {
        vad.reset()
        val window = StreamingSession.VAD_WINDOW_SIZE
        var i = 0
        while (i + window <= clip.size) {
            vad.acceptWaveform(clip.copyOfRange(i, i + window))
            i += window
        }
        while (!vad.empty()) vad.pop()
    }
}
