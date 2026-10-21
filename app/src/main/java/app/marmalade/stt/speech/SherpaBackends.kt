package app.marmalade.stt.speech

import android.content.res.AssetManager
import app.marmalade.stt.install.EngineFamily
import app.marmalade.stt.install.FileRole
import app.marmalade.stt.install.SttEngineDescriptor
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineCanaryModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.ceil

/** The bundled Silero VAD model, relative to `assets/` (ARCHITECTURE.md D5). */
internal const val SILERO_VAD_ASSET = "vad/silero_vad.onnx"

/**
 * Longest single utterance; Whisper's window is 30 s (see [SherpaSileroVad]). Applies to every engine:
 * offline sessions decode at most this plus the pre-roll ([UtteranceCap.MAX_DECODE_SAMPLES]).
 * Offline engines' peak memory grows with it: [app.marmalade.stt.install.SpeedBudget.REFERENCE]'s
 * memory is for this length, so re-measure it when this changes.
 */
internal const val MAX_UTTERANCE_SECONDS = 28f

/**
 * The absolute path of each of [descriptor]'s files inside [modelDir].
 *
 * @throws FileNotFoundException naming every missing file, so a half-finished
 *   or deleted install reports what is gone instead of a native load error.
 */
internal fun modelFilePaths(descriptor: SttEngineDescriptor, modelDir: File): Map<FileRole, File> {
    val paths = descriptor.files.associate { it.role to File(modelDir, it.name) }
    val missing = paths.values.filterNot { it.isFile }
    if (missing.isNotEmpty()) {
        throw FileNotFoundException(
            "Model files missing for ${descriptor.id}: ${missing.joinToString { it.name }} (in $modelDir)",
        )
    }
    return paths
}

/**
 * Silence appended to every decode of an offline NeMo model (Parakeet), 0.3 s (#25).
 * Parakeet-ja's CTC head (#60) gets it too: #52 measured it that way.
 *
 * A Final is the pre-roll plus Silero's segment, which ends where the speech
 * ends: the model gets no silence after the last word. Parakeet v3's int8 export
 * then drifts into English partway through some German utterances ("Denken Sie
 * daran, dass…" came out as "Think that Festival…"): FLEURS-de WER 16.2 through
 * the app's pipeline against 7.0 for the whole clip. The fp32 export doesn't
 * drift (5.9). 0.2 s of zeros already brought it to 7.9; 0.3 s gives 7.4.
 * Whisper gets the same in its own config (`tailPaddings`).
 */
internal const val NEMO_TAIL_PADDING_SAMPLES = 4_800

/**
 * Zeros fed to a streaming NeMo transducer before each utterance's
 * `inputFinished`: its chunk plus 0.2 s. sherpa-onnx v1.13.8 decodes such a
 * stream only in whole windows (121 feature frames for the 1120 ms export)
 * and `inputFinished` pads nothing, so up to a window of speech at the end of
 * an utterance was never decoded. On the host (2026-09-28,
 * `docs/benchmarks/2026-09-nemotron-chunk.md`) 1.3 s of zeros cut Nemotron
 * 3.5 1120 ms's dropped endings on AMI from 41 to 16 clips (WER 14.5 → 12.9)
 * and LibriSpeech clean WER from 3.2 to 2.8; 0.7 s helped 560 ms a little
 * (AMI 13.8 → 13.4).
 */
internal fun streamingTailPaddingSamples(chunkMs: Int?): Int =
    chunkMs?.let { (it + 200) * MIC_SAMPLE_RATE / 1000 } ?: 0

/** [samples] followed by [padding] zeros; [samples] itself when [padding] is 0. */
internal fun withTailPadding(samples: FloatArray, padding: Int): FloatArray =
    if (padding == 0) samples else samples.copyOf(samples.size + padding)

/**
 * Whisper's own code where it differs from the app's ([app.marmalade.stt.install.EngineLanguages.normalize]).
 * sherpa-onnx exits the process on a code Whisper doesn't know.
 */
internal val WHISPER_LANGUAGE_CODES = mapOf("nb" to "no")

/**
 * Qwen3-ASR takes its language hint as an English name in the prompt
 * ("language English"), not a code: the names of `SUPPORTED_LANGUAGES` in
 * QwenLM/Qwen3-ASR's `qwen_asr/inference/utils.py`, one per
 * [app.marmalade.stt.install.EngineLanguages.QWEN3] code (#88).
 */
internal val QWEN3_LANGUAGE_NAMES = mapOf(
    "zh" to "Chinese", "en" to "English", "yue" to "Cantonese", "ar" to "Arabic", "de" to "German",
    "fr" to "French", "es" to "Spanish", "pt" to "Portuguese", "id" to "Indonesian", "it" to "Italian",
    "ko" to "Korean", "ru" to "Russian", "th" to "Thai", "vi" to "Vietnamese", "ja" to "Japanese",
    "tr" to "Turkish", "hi" to "Hindi", "ms" to "Malay", "nl" to "Dutch", "sv" to "Swedish",
    "da" to "Danish", "fi" to "Finnish", "pl" to "Polish", "cs" to "Czech", "fil" to "Filipino",
    "fa" to "Persian", "el" to "Greek", "ro" to "Romanian", "hu" to "Hungarian", "mk" to "Macedonian",
)

/**
 * Qwen3-ASR's output cap for one decode of [seconds] of audio (#50,
 * `docs/benchmarks/2026-10-qwen3-speed.md` §4): 16 tokens a second plus
 * 30, never below sherpa's own 128. Hindi, the densest language measured,
 * needed up to 15.3 a second (Chinese 4.5, Korean 7.3); sherpa's fixed 128
 * cut 10 of 40 FLEURS Hindi clips and left fast Korean at 28 s 122 tokens.
 * Qwen3 transcribes 30 languages, so the cap covers the densest; runaways
 * are #49's.
 */
internal fun qwen3MaxNewTokens(seconds: Double): Int = maxOf(QWEN3_SHERPA_MAX_NEW_TOKENS, ceil(16 * seconds).toInt() + 30)

/** sherpa-onnx v1.13.8's default `max_new_tokens` for Qwen3-ASR. */
internal const val QWEN3_SHERPA_MAX_NEW_TOKENS = 128

/**
 * Qwen3-ASR's KV cache length in tokens: prompt + audio + output. At the
 * longest decode (28 s plus the 0.6 s pre-roll) prompt and audio take 390
 * positions and [qwen3MaxNewTokens] allows 488: 878. sherpa's 512 left
 * 122. Each position costs 229 KB (28 layers × K and V × 8 heads × 128 ×
 * 4 B), so 896 is +88 MB (`SpeedBudget.REFERENCE` counts it). (#50)
 */
internal const val QWEN3_MAX_TOTAL_LEN = 896

/**
 * The sherpa-onnx model config for an offline [descriptor] whose files are
 * at [path], in [language] (a code from its language options, "" for
 * auto-detect). Threads and provider are the caller's.
 */
internal fun offlineModelConfig(descriptor: SttEngineDescriptor, path: (FileRole) -> String, language: String): OfflineModelConfig =
    when (descriptor.family) {
        EngineFamily.WHISPER -> OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = path(FileRole.ENCODER),
                decoder = path(FileRole.DECODER),
                // English-only models take "en"; "" makes a multilingual one detect.
                language = if (descriptor.languageOptions == null) "en" else WHISPER_LANGUAGE_CODES[language] ?: language,
                task = "transcribe",
                tailPaddings = 1000,
            ),
            tokens = path(FileRole.TOKENS),
            modelType = "whisper",
        )
        EngineFamily.NEMO_TRANSDUCER -> OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = path(FileRole.ENCODER),
                decoder = path(FileRole.DECODER),
                joiner = path(FileRole.JOINER),
            ),
            tokens = path(FileRole.TOKENS),
            modelType = "nemo_transducer",
        )
        EngineFamily.NEMO_CTC -> OfflineModelConfig(
            nemo = OfflineNemoEncDecCtcModelConfig(model = path(FileRole.ENCODER)),
            tokens = path(FileRole.TOKENS),
        )
        EngineFamily.CANARY -> OfflineModelConfig(
            canary = OfflineCanaryModelConfig(
                encoder = path(FileRole.ENCODER),
                decoder = path(FileRole.DECODER),
                // Transcribe, don't translate: target = source.
                srcLang = language.ifEmpty { "en" },
                tgtLang = language.ifEmpty { "en" },
                usePnc = true,
            ),
            tokens = path(FileRole.TOKENS),
        )
        EngineFamily.QWEN3_ASR -> OfflineModelConfig(
            qwen3Asr = OfflineQwen3AsrModelConfig(
                convFrontend = path(FileRole.CONV_FRONTEND),
                encoder = path(FileRole.ENCODER),
                decoder = path(FileRole.DECODER),
                // vocab.json, merges.txt and tokenizer_config.json are installed flat.
                tokenizer = File(path(FileRole.TOKENIZER_VOCAB)).parent.orEmpty(),
                maxTotalLen = QWEN3_MAX_TOTAL_LEN,
            ),
        )
        EngineFamily.STREAMING_TRANSDUCER ->
            error("${descriptor.id} is a streaming model; use SherpaStreamingRecognizer")
        EngineFamily.GRANITE_CTC -> error("${descriptor.id} runs on GraniteOfflineTranscriber")
    }

/**
 * Whisper's token budget for one decode of [seconds] of audio in [language]
 * (#54, sherpa patch 0005): ceil(k × seconds) + c tokens instead of
 * Whisper's own 224. Below 224, a decode that falls into a loop stops
 * sooner: a 224-step runaway takes 8–10 s on the host and roughly 20–40 s on
 * the Pixel 8a, which the user waits through after Stop (a cancel stops it
 * at once). Above it, Hindi, whose byte-level BPE needs up to 17 tokens a
 * second, no longer loses the end of anything over about 13 s; at most
 * [WHISPER_MAX_TOKENS]. Only for the languages whose token rate was
 * measured (`docs/benchmarks/2026-10-whisper-token-budget.md`); null for the
 * others, which keep Whisper's 224.
 */
internal fun whisperMaxNewTokens(language: String, seconds: Double): Int? =
    WHISPER_TOKEN_BUDGETS[language]?.let { (k, c) -> ceil(k * seconds).toInt() + c }

/**
 * (k, c) per language for [whisperMaxNewTokens]. The densest Finals measured
 * needed English 4.8 tokens a second, Japanese 8.1 and Hindi 17.2; at these
 * budgets none was cut, and the 6.9 s Japanese runaway stops at 79 tokens
 * instead of 224. Hindi's reaches [WHISPER_MAX_TOKENS] at 21.6 s, so
 * Whisper's Hindi decodes are capped below that ([UtteranceCap.maxDecodeSamples]).
 */
internal val WHISPER_TOKEN_BUDGETS = mapOf("en" to (6 to 10), "ja" to (10 to 10), "hi" to (20 to 10))

/**
 * The most tokens one Whisper decode can hold: the decoder's self-attention
 * cache has n_text_ctx = 448 positions, the prompt takes 4 (start of
 * transcript, language, task, no timestamps), and sherpa stops one short of
 * the end.
 */
internal const val WHISPER_MAX_TOKENS = 443

/**
 * The per-stream options for one decode of [seconds] of audio by a
 * [family] engine in [language]: Whisper's token budget
 * ([whisperMaxNewTokens]), Qwen3-ASR's language hint (an English name) and
 * output cap ([qwen3MaxNewTokens]); none for the others.
 */
internal fun offlineStreamOptions(family: EngineFamily, language: String, seconds: Double): Map<String, String> =
    when (family) {
        EngineFamily.WHISPER ->
            whisperMaxNewTokens(language, seconds)?.let { mapOf("max_new_tokens" to it.toString()) }.orEmpty()
        EngineFamily.QWEN3_ASR -> {
            val hint = QWEN3_LANGUAGE_NAMES[language]?.let { mapOf("language" to it) }.orEmpty()
            hint + ("max_new_tokens" to qwen3MaxNewTokens(seconds).toString())
        }
        else -> emptyMap()
    }

/**
 * [OfflineTranscriber] over sherpa-onnx's OfflineRecognizer, for every
 * offline family: Whisper, Parakeet (NeMo transducer and CTC), Canary and Qwen3-ASR.
 */
internal class SherpaOfflineTranscriber private constructor(
    private val recognizer: OfflineRecognizer,
    private val family: EngineFamily,
    /** The language hint, for the per-stream options ([offlineStreamOptions]). */
    private val language: String,
    /** Zeros appended to every decode ([NEMO_TAIL_PADDING_SAMPLES] for Parakeet). */
    private val tailPadding: Int,
    /** Cores the decoding thread is pinned to while it decodes ([OrtTuning.pinnedCores]); null: not pinned. */
    private val cores: List<Int>?,
) : OfflineTranscriber {

    // sherpa patches 0003 and 0004: the decoder loops check the stream's flag
    // before every step, and the flag also terminates the encoder run.
    private val inFlight = InFlightDecode<OfflineStream> { it.cancel() }

    override fun transcribe(samples: FloatArray, sampleRate: Int, isCancelled: () -> Boolean): String {
        val stream = recognizer.createStream()
        // Before the features are computed: a cancel from then on stops this decode.
        inFlight.begin(stream, isCancelled)
        try {
            val seconds = samples.size.toDouble() / sampleRate
            offlineStreamOptions(family, language, seconds).forEach { (key, value) -> stream.setOption(key, value) }
            stream.acceptWaveform(withTailPadding(samples, tailPadding), sampleRate)
            CpuAffinity.pinned(cores) { recognizer.decode(stream) }
            return recognizer.getResult(stream).text
        } finally {
            inFlight.end()
            stream.release()
        }
    }

    override fun cancel() = inFlight.cancel()

    override fun release() = recognizer.release()

    companion object {
        /**
         * Loads [descriptor]'s model from absolute paths in [modelDir]
         * (downloaded files, so no AssetManager). Blocking and slow (seconds):
         * call it off the main thread.
         *
         * @param language BCP-47 code from the engine's language options, or
         *   "" for auto-detect (and for engines without options).
         * @param numThreads ONNX Runtime threads; the speed benchmark (#23) varies it.
         * @param tuning execution provider, spinning and pinning (#31); the benchmark varies it.
         */
        fun load(
            descriptor: SttEngineDescriptor,
            modelDir: File,
            language: String,
            numThreads: Int = RecognizerThreads.count,
            tuning: OrtTuning = OrtTuning.DEFAULT,
        ): SherpaOfflineTranscriber {
            val files = modelFilePaths(descriptor, modelDir)
            fun path(role: FileRole) = checkNotNull(files[role]) {
                "${descriptor.id} has no ${role.name.lowercase()} file"
            }.absolutePath

            val model = offlineModelConfig(descriptor, ::path, language).apply {
                this.numThreads = numThreads
                provider = tuning.sherpaProvider()
            }
            val config = OfflineRecognizerConfig(
                // NeMo and Qwen models override the feature size from their metadata.
                featConfig = FeatureConfig(sampleRate = MIC_SAMPLE_RATE, featureDim = 80),
                modelConfig = model,
                decodingMethod = "greedy_search",
            )
            val tailPadding = when (descriptor.family) {
                EngineFamily.NEMO_TRANSDUCER, EngineFamily.NEMO_CTC -> NEMO_TAIL_PADDING_SAMPLES
                else -> 0
            }
            // Pinned here, the recognizer's ORT thread pools inherit the cores (CpuAffinity).
            val cores = tuning.pinnedCores
            val recognizer = CpuAffinity.pinned(cores) { OfflineRecognizer(config = config) }
            return SherpaOfflineTranscriber(recognizer, descriptor.family, language, tailPadding, cores)
        }
    }
}

/**
 * Nemotron 3.5 reads the language as a key of its `prompt_dictionary`
 * (encoder metadata). Most of its 28 languages are keys as they are;
 * Vietnamese exists only with a region (so do Japanese and Chinese, which
 * the app no longer offers it for, #37).
 */
internal val NEMOTRON_LANGUAGE_KEYS = mapOf("vi" to "vi-VN")

private val RUNS_OF_SPACES = Regex(" {2,}")

/** [StreamingRecognizer] over sherpa-onnx's OnlineRecognizer (Nemotron, cache-aware streaming). */
internal class SherpaStreamingRecognizer private constructor(
    private val recognizer: OnlineRecognizer,
    private val language: String,
    /** Zeros fed before each `inputFinished` ([streamingTailPaddingSamples]). */
    private val tailPadding: Int,
    /** As for [SherpaOfflineTranscriber]: the decoding thread's cores, or null. */
    private val cores: List<Int>?,
) : StreamingRecognizer {

    override fun createStream(): RecognizerStream {
        val stream = recognizer.createStream()
        // Multilingual Nemotron reads the language per stream; "" (unset) is
        // auto. The English model ignores it.
        if (language.isNotEmpty()) stream.setOption("language", NEMOTRON_LANGUAGE_KEYS[language] ?: language)
        return object : RecognizerStream {
            override fun acceptWaveform(samples: FloatArray) = stream.acceptWaveform(samples, MIC_SAMPLE_RATE)

            override fun decode() = CpuAffinity.pinned(cores) {
                while (recognizer.isReady(stream)) recognizer.decode(stream)
            }

            // Nemotron 3.5 emits a space token before some sentence starts
            // ("benchmark.  The"); partials and finals get single spaces.
            override fun text(): String = recognizer.getResult(stream).text.replace(RUNS_OF_SPACES, " ")
            override fun inputFinished() {
                if (tailPadding > 0) stream.acceptWaveform(FloatArray(tailPadding), MIC_SAMPLE_RATE)
                stream.inputFinished()
            }
            override fun release() = stream.release()
        }
    }

    override fun release() = recognizer.release()

    companion object {
        /**
         * Blocking and slow (seconds): call it off the main thread. [numThreads]
         * and [tuning]: as for [SherpaOfflineTranscriber.load].
         */
        fun load(
            descriptor: SttEngineDescriptor,
            modelDir: File,
            language: String,
            numThreads: Int = RecognizerThreads.count,
            tuning: OrtTuning = OrtTuning.DEFAULT,
        ): SherpaStreamingRecognizer {
            require(descriptor.family == EngineFamily.STREAMING_TRANSDUCER) {
                "${descriptor.id} is not a streaming model"
            }
            val files = modelFilePaths(descriptor, modelDir)
            fun path(role: FileRole) = checkNotNull(files[role]) {
                "${descriptor.id} has no ${role.name.lowercase()} file"
            }.absolutePath

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = MIC_SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = path(FileRole.ENCODER),
                        decoder = path(FileRole.DECODER),
                        joiner = path(FileRole.JOINER),
                    ),
                    tokens = path(FileRole.TOKENS),
                    numThreads = numThreads,
                    provider = tuning.sherpaProvider(),
                ),
                // Silero VAD ends utterances (see OnlineSession), not the model.
                enableEndpoint = false,
                decodingMethod = "greedy_search",
            )
            val cores = tuning.pinnedCores
            return SherpaStreamingRecognizer(
                CpuAffinity.pinned(cores) { OnlineRecognizer(config = config) },
                language,
                streamingTailPaddingSamples(descriptor.chunkMs),
                cores,
            )
        }
    }
}

/** [SpeechVad] over sherpa-onnx's Silero VAD, loaded from the bundled asset. */
internal class SherpaSileroVad private constructor(private val vad: Vad) : SpeechVad {
    override fun acceptWaveform(samples: FloatArray) = vad.acceptWaveform(samples)
    override fun isSpeechDetected(): Boolean = vad.isSpeechDetected()
    override fun empty(): Boolean = vad.empty()
    override fun front(): VadSegment = vad.front().let { VadSegment(it.start, it.samples) }
    override fun pop() = vad.pop()
    override fun flush() = vad.flush()
    override fun reset() = vad.reset()
    override fun release() = vad.release()

    companion object {
        /** Blocking native load (fast; the model is ~0.6 MB). */
        fun create(assets: AssetManager, minSilenceSeconds: Float): SherpaSileroVad {
            val config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = SILERO_VAD_ASSET,
                    threshold = 0.5f,
                    minSilenceDuration = minSilenceSeconds,
                    minSpeechDuration = 0.25f,
                    windowSize = StreamingSession.VAD_WINDOW_SIZE,
                    // Marmalade used 60 s, but sherpa-onnx's Whisper decodes at
                    // most 29.5 s and silently drops the rest. Past this, Silero
                    // only gets eager to end the segment (a 0.1 s pause at
                    // threshold 0.9), so continuous speech can run longer; the
                    // session cuts it (UtteranceCap, #48).
                    maxSpeechDuration = MAX_UTTERANCE_SECONDS,
                ),
                sampleRate = MIC_SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
            )
            return SherpaSileroVad(Vad(assetManager = assets, config = config))
        }
    }
}
