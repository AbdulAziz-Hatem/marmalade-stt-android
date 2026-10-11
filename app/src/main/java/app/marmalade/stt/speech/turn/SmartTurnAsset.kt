package app.marmalade.stt.speech.turn

/**
 * The Smart Turn end-of-turn model (#43): pipecat's smart-turn v3.2 CPU ONNX
 * (`pipecat-ai/smart-turn-v3`, BSD-2-Clause), bundled in the APK's assets
 * since #44 (Max, 2026-09-28; downloaded before, D5), like the Silero VAD.
 * Smart Turn therefore works from the first session, offline. The pin lives
 * here: `SmartTurnAssetTest` checks the committed file against it, and
 * NOTICE.md must name the same file, hash and revision.
 */
object SmartTurnAsset {
    /** The model's path under `assets/`. */
    const val PATH = "turn/smart-turn-v3.2-cpu.onnx"

    /** The HuggingFace commit the file came from ("Smart Turn v3.2", 2026-01-07). */
    const val REVISION = "f766f81d3cfdf7737ac64aad813d91bbfd56bf93"

    const val SHA256 = "2bb026316b14a660486a75b1733cd3fbab8c2fd0314dc9af7be49f8cca967e4f"

    const val SIZE_BYTES = 8_679_182L

    /** The file's name, as NOTICE.md and HuggingFace give it. */
    val fileName: String get() = PATH.substringAfterLast('/')
}
