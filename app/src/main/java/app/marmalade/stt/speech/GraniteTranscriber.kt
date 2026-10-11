package app.marmalade.stt.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import app.marmalade.stt.install.EngineFamily
import app.marmalade.stt.install.FileRole
import app.marmalade.stt.install.SttEngineDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.FloatBuffer

/**
 * [OfflineTranscriber] for IBM Granite Speech 5.0 TurboCTC (#32), which
 * sherpa-onnx can't run: [GraniteFrontend] features into one ONNX graph on
 * ONNX Runtime's Java API (the same libonnxruntime.so sherpa-onnx links),
 * then [graniteCtcGreedy] and [GraniteVocab]. A port of the harness's
 * `asrbench.granite.GraniteTranscriber`; `GraniteParityTest` checks the two
 * give the same text.
 *
 * The graph takes `[1, T, 320]` features and returns `[1, T / 4, 16384]`
 * logits (12.5 frames a second). Its text is lowercase and unpunctuated.
 */
internal class GraniteOfflineTranscriber private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val vocab: GraniteVocab,
    /** The #33 post-pass for case, punctuation and apostrophes; null leaves Granite's raw text. */
    private val formatter: GraniteFormatter?,
    /** Cores the decoding thread is pinned to while the graph runs ([OrtTuning.pinnedCores]); null: not pinned. */
    private val cores: List<Int>?,
) : OfflineTranscriber {

    private val inputName = session.inputNames.first()

    override fun transcribe(samples: FloatArray, sampleRate: Int, isCancelled: () -> Boolean): String {
        require(sampleRate == GraniteFrontend.SAMPLE_RATE) { "Granite takes 16 kHz audio, not $sampleRate Hz" }
        if (samples.size < GraniteFrontend.MIN_SAMPLES) return ""
        return transcribeFeatures(GraniteFrontend.features(samples))
    }

    /** Text for [GraniteFrontend.features] output (`[T * 320]`, row-major). */
    fun transcribeFeatures(features: FloatArray): String {
        val shape = longArrayOf(1, (features.size / GraniteFrontend.WIDTH).toLong(), GraniteFrontend.WIDTH.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(features), shape).use { input ->
            CpuAffinity.pinned(cores) { session.run(mapOf(inputName to input)) }.use { result ->
                val logits = result.get(0) as OnnxTensor
                val (_, frames, width) = logits.info.shape
                check(width.toInt() == vocab.size) { "logits are $width wide, the vocabulary has ${vocab.size} pieces" }
                val text = vocab.decode(graniteCtcGreedy(logits.floatBuffer, frames.toInt(), width.toInt()))
                return formatter?.format(text) ?: text
            }
        }
    }

    override fun release() {
        formatter?.close()
        session.close()
    }

    companion object {
        /**
         * Loads [descriptor]'s graph ([FileRole.ENCODER]) and `tokenizer.json`
         * ([FileRole.TOKENS]) from [modelDir], plus the formatter when
         * [formatterModel] (the bundled `formatter.int8.onnx`) is given.
         * Blocking and slow (seconds): call it off the main thread.
         */
        fun load(
            descriptor: SttEngineDescriptor,
            modelDir: File,
            formatterModel: ByteArray?,
            threads: Int = RecognizerThreads.count,
            tuning: OrtTuning = OrtTuning.DEFAULT,
        ): GraniteOfflineTranscriber {
            require(descriptor.family == EngineFamily.GRANITE_CTC) { "${descriptor.id} is not a Granite model" }
            val files = modelFilePaths(descriptor, modelDir)
            fun path(role: FileRole) = checkNotNull(files[role]) {
                "${descriptor.id} has no ${role.name.lowercase()} file"
            }
            return load(path(FileRole.ENCODER), path(FileRole.TOKENS), formatterModel, threads, tuning)
        }

        /** The graph at [model] and the vocabulary in [tokenizerJson]; also the parity test's entry point. */
        fun load(
            model: File,
            tokenizerJson: File,
            formatterModel: ByteArray?,
            threads: Int,
            tuning: OrtTuning = OrtTuning.DEFAULT,
        ): GraniteOfflineTranscriber {
            val tokenizer = Json.parseToJsonElement(tokenizerJson.readText()).jsonObject
            val vocab = GraniteVocab.fromTokenizerJson(tokenizer)
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                // As the harness (and sherpa-onnx's recognizers): every performance core on
                // one operator at a time.
                setIntraOpNumThreads(threads)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // #31: the recognizer's graph gets the same tuning as the sherpa-onnx engines
                // ([OrtTuning]). The formatter's session keeps ORT's defaults (unpinned,
                // spinning on): the tuning is a recognizer setting and its benchmark times
                // recognition only.
                tuning.sessionConfig.forEach { (key, value) -> addConfigEntry(key, value) }
                if (tuning.xnnpack) addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
                if (tuning.verbose) setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_VERBOSE)
                setCPUArenaAllocator(tuning.arena != false)
            }
            val cores = tuning.pinnedCores
            val session = options.use { CpuAffinity.pinned(cores) { env.createSession(model.absolutePath, it) } }
            val formatter = try {
                formatterModel?.let { GraniteFormatter.load(env, it, GraniteBpe.fromTokenizerJson(tokenizer), threads) }
            } catch (e: Throwable) {
                session.close()
                throw e
            }
            return GraniteOfflineTranscriber(env, session, vocab, formatter, cores)
        }

        /** The formatter model bundled in the APK's assets (#33). */
        const val FORMATTER_ASSET = "granite/formatter.int8.onnx"
    }
}
