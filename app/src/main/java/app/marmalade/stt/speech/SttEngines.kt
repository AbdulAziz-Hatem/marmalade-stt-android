package app.marmalade.stt.speech

import android.content.res.AssetManager
import app.marmalade.stt.install.EngineFamily
import app.marmalade.stt.install.InstalledModel

/**
 * Builds the runtime for an installed engine from its family (ARCHITECTURE.md
 * D4): offline families get [StreamingSession] over [SherpaOfflineTranscriber]
 * (Granite over [GraniteOfflineTranscriber]), the streaming family
 * [OnlineSession] over [SherpaStreamingRecognizer].
 */
object SttEngines {

    /**
     * The listening engine for [installed].
     *
     * @param language the engine's language hint (`SpeechPrefs.languageFor`);
     *   "" for auto-detect or no hint. Fixed for the engine's life: a new
     *   choice needs a new engine.
     * @param numThreads ONNX Runtime threads; only the speed benchmark (#23)
     *   passes anything but the device's [RecognizerThreads.count].
     * @param tuning ONNX Runtime execution provider, spinning and pinning
     *   (#31): Settings → Developer's choice, or the benchmark's.
     */
    fun create(
        installed: InstalledModel,
        assets: AssetManager,
        language: String,
        openMic: (hopSamples: Int, hardwareEffects: Boolean) -> MicCapture = ::openMicCapture,
        numThreads: Int = RecognizerThreads.count,
        tuning: OrtTuning = OrtTuning.DEFAULT,
    ): SttEngine {
        val d = installed.descriptor
        val createVad = { minSilence: Float -> SherpaSileroVad.create(assets, minSilence) }
        return if (d.family.streaming) {
            SherpaSttEngine(
                descriptor = d,
                loadModel = { SherpaStreamingRecognizer.load(d, installed.dir, language, numThreads, tuning) },
                createVad = createVad,
                openMic = openMic,
                newSession = { model, vad, options, mic, _ -> OnlineSession(model, vad, options, openMic = mic) },
            )
        } else {
            SherpaSttEngine(
                descriptor = d,
                loadModel = { loadOffline(installed, assets, language, numThreads, tuning) },
                createVad = createVad,
                openMic = openMic,
                newSession = { model, vad, options, mic, earlyVad ->
                    StreamingSession(
                        model, vad, options, mic,
                        livePartials = d.livePartials,
                        maxDecodeSamples = UtteranceCap.maxDecodeSamples(d.family, language),
                        isPreSpeechPhantom = d::isPreSpeechPhantom,
                        earlyVad = earlyVad,
                        trimAtStop = SpeedKnobs.trimAtStop,
                        partialAtOnset = SpeedKnobs.partialAtOnset,
                        verifySpeculation = SpeedKnobs.verifySpeculation,
                    )
                },
            )
        }
    }

    /**
     * [installed]'s model, loaded for transcribing a recorded take (the A/B
     * benchmark). Blocking and slow: call it off the main thread. The caller
     * releases it. [numThreads] and [tuning]: as for [create].
     */
    fun loadForTake(
        installed: InstalledModel,
        assets: AssetManager,
        language: String,
        numThreads: Int = RecognizerThreads.count,
        tuning: OrtTuning = OrtTuning.DEFAULT,
    ): TakeTranscriber {
        val d = installed.descriptor
        val vad = SherpaSileroVad.create(assets, SessionOptions.DEFAULT_MIN_SILENCE_SECONDS)
        return try {
            when (d.family) {
                EngineFamily.STREAMING_TRANSDUCER ->
                    StreamingTakeTranscriber(SherpaStreamingRecognizer.load(d, installed.dir, language, numThreads, tuning), vad)
                else -> OfflineTakeTranscriber(
                    loadOffline(installed, assets, language, numThreads, tuning),
                    vad,
                    UtteranceCap.maxDecodeSamples(d.family, language),
                )
            }
        } catch (e: Throwable) {
            vad.release()
            throw e
        }
    }

    private fun loadOffline(
        installed: InstalledModel,
        assets: AssetManager,
        language: String,
        numThreads: Int,
        tuning: OrtTuning,
    ): OfflineTranscriber {
        val d = installed.descriptor
        return if (d.family == EngineFamily.GRANITE_CTC) {
            GraniteOfflineTranscriber.load(d, installed.dir, graniteFormatter(assets), numThreads, tuning)
        } else {
            SherpaOfflineTranscriber.load(d, installed.dir, language, numThreads, tuning)
        }
    }

    /** The Granite formatter (#33), bundled in the APK: Granite's lowercase text gets case and punctuation. */
    private fun graniteFormatter(assets: AssetManager): ByteArray =
        assets.open(GraniteOfflineTranscriber.FORMATTER_ASSET).use { it.readBytes() }
}
