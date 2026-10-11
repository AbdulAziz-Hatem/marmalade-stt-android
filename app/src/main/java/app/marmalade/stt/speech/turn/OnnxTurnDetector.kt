package app.marmalade.stt.speech.turn

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * pipecat's smart-turn v3 end-of-turn model (`smart-turn-v3.2-cpu.onnx`, an
 * 8.7 MB int8 Whisper-encoder classifier; BSD-2-Clause, Copyright (c)
 * 2024-2025 Daily) on ONNX Runtime's Java API. A port of marmalade-stt-cli's
 * `turn.SmartTurnDetector`: the last 8 s of the turn ([WhisperFeatures.lastWindow])
 * as [WhisperFeatures.logMel] features, `input_features` `(1, 80, 800)`
 * float32, and the single `(1, 1)` output is already a probability (the
 * sigmoid is inside the graph). The complete/incomplete threshold is the
 * caller's.
 *
 * Loading and every check block (on a desktop JVM ~45 ms and ~65 ms, features
 * included), so keep both off the main thread. One instance can be shared:
 * ORT sessions run concurrently and [WhisperFeatures] is thread-safe.
 */
class OnnxTurnDetector(model: ByteArray, threads: Int = DEFAULT_THREADS) : TurnDetector, AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = OrtSession.SessionOptions().use { options ->
        // One inference per turn-end check, so nothing for inter-op threads to do.
        options.setIntraOpNumThreads(threads)
        options.setInterOpNumThreads(1)
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        env.createSession(model, options)
    }

    init {
        val inputs = session.inputNames
        if (INPUT !in inputs) {
            session.close()
            error("The model has no $INPUT input (it has $inputs): not a smart-turn v3 model")
        }
    }

    override fun completeProbability(audio: FloatArray): Float {
        val mel = WhisperFeatures.logMel(WhisperFeatures.lastWindow(audio))
        val features = FloatBuffer.allocate(WhisperFeatures.N_MELS * WhisperFeatures.N_FRAMES)
        for (row in mel) features.put(row)
        features.rewind()
        OnnxTensor.createTensor(env, features, SHAPE).use { input ->
            session.run(mapOf(INPUT to input)).use { result ->
                return (result.get(0) as OnnxTensor).floatBuffer.get(0)
            }
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        /**
         * Two intra-op threads. The check runs between 100 ms mic hops, often
         * while the recognizer decodes a partial on up to four performance
         * cores ([app.marmalade.stt.speech.RecognizerThreads]), so it must be
         * quick without taking every core. On a desktop JVM (i7-8750H, the
         * model alone) one thread took ~81 ms, two ~46 ms and four ~30 ms:
         * two gets two thirds of the gain and leaves the recognizer room.
         * `OnnxTurnDetectorTest` prints them (with the features, ~19 ms, added).
         */
        const val DEFAULT_THREADS = 2

        private const val INPUT = "input_features"
        private val SHAPE = longArrayOf(1, WhisperFeatures.N_MELS.toLong(), WhisperFeatures.N_FRAMES.toLong())
    }
}
