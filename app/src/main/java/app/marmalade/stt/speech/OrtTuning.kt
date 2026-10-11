package app.marmalade.stt.speech

import android.util.Log
import app.marmalade.stt.install.EngineSpeed
import app.marmalade.stt.install.SpeedBudget
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * How recognizers run on ONNX Runtime (#31), ported from marmalade-tts-android
 * (`PocketEngine`, `KittenDirectEngine`):
 *
 * - [xnnpack]: the XNNPACK execution provider (Arm kernels) before the CPU EP,
 *   with its own thread pool of the recognizer's thread count.
 * - [spinning] off: ORT's pool threads park instead of spinning between
 *   operators (`session.intra_op.allow_spinning=0`), so they don't fight
 *   XNNPACK's pool, or the decoder and joiner sessions' pools of a transducer.
 * - [pin]: pool threads and the decoding thread run only on the fast cores
 *   ([RecognizerThreads.fastCores], [CpuAffinity]), never an efficiency core.
 * - [arena]: ORT's CPU memory arena (#47). On (ORT's default) unless this
 *   phone needs an engine's lean mode to fit it ([forPhone],
 *   [SpeedBudget.runsLean]). The arena keeps the largest buffers a session
 *   ever needed and grows in steps, so a long utterance raises an offline
 *   engine's memory for good; without it Parakeet Ultra's own memory at a
 *   27 s utterance is a third lower, but every decode of a new length
 *   allocates afresh and is slower
 *   (`docs/benchmarks/2026-10-pixel8a-mem.md`). The benchmark can force it
 *   on (`-arena`) or off (`-noarena`).
 *
 * Graph optimisation is ORT's default, all levels, with memory patterns on,
 * as in the TTS app. [DEFAULT] is what users get; Settings → Developer can
 * pick another ([PRESETS]) and the speed benchmark measures them.
 *
 * The Pixel 8a decided (docs/benchmarks/2026-10-pixel8a-ort.md): the default
 * stays. XNNPACK takes no node of any of our int8 graphs (ORT's verbose log:
 * all on the CPU EP). Spinning off is 3-4x slower: parked threads wake late,
 * and on any core. Pinned, spinning off only ties the default; pinning alone
 * gave Parakeet Ultra 8 % but cost Nemotron 28 %.
 *
 * @property cores the cores to pin to instead of the detected fast cores (the
 *   benchmark's override, e.g. on an emulator with no cluster information).
 * @property verbose ORT's verbose log (logcat tag `onnxruntime`), which lists
 *   the nodes each execution provider takes: does XNNPACK take any int8 node?
 *   The benchmark's, never a Settings choice.
 * @property optLevel ORT's graph optimisation level (0 none, 1 basic, 2
 *   extended; null: sherpa-onnx's default, all levels). The benchmark's, to
 *   measure how much of a load is graph optimisation (#78).
 * @property prepack ORT's weight prepacking (`session.disable_prepacking=1`
 *   when false). On by default; the benchmark's `-noprepack` measures the
 *   load time and memory it costs against the decode speed it buys (#78).
 */
data class OrtTuning(
    val xnnpack: Boolean = false,
    val spinning: Boolean = true,
    val pin: Boolean = false,
    val cores: List<Int>? = null,
    val verbose: Boolean = false,
    val arena: Boolean? = null,
    val optLevel: Int? = null,
    val prepack: Boolean = true,
) {
    /**
     * Stable id: `cpu` or `xnnpack`, then `-nospin`, `-pin`, `-verbose`,
     * `-arena` or `-noarena`, `-opt<level>` (with `:<cores>` for a pinning override).
     */
    val id: String
        get() = buildString {
            append(if (xnnpack) "xnnpack" else "cpu")
            if (!spinning) append("-nospin")
            if (pin) append("-pin")
            if (verbose) append("-verbose")
            when (arena) {
                true -> append("-arena")
                false -> append("-noarena")
                null -> {}
            }
            optLevel?.let { append("-opt").append(it) }
            if (!prepack) append("-noprepack")
            cores?.let { append(':').append(it.joinToString(".")) }
        }

    /** The cores to pin to, or null for no pinning (off, or nothing to pin to on this device). */
    internal val pinnedCores: List<Int>?
        get() = if (!pin) null else (cores ?: RecognizerThreads.fastCores).ifEmpty { null }

    /**
     * This tuning for [engineId] on a phone with [totalMemBytes] of RAM: the
     * arena off when the engine fits the phone only without it
     * ([SpeedBudget.runsLean]), as the recommender counted it. A tuning that
     * sets [arena] itself (the benchmark's) is kept, and so is everything
     * with the RAM unknown.
     */
    fun forPhone(
        engineId: String,
        totalMemBytes: Long?,
        reference: Map<String, EngineSpeed> = SpeedBudget.REFERENCE,
    ): OrtTuning =
        if (arena != null || totalMemBytes == null || !SpeedBudget.runsLean(engineId, totalMemBytes, reference)) this
        else copy(arena = false)

    /** ORT session config entries for this tuning. */
    internal val sessionConfig: Map<String, String>
        get() = buildMap {
            if (!spinning) {
                put("session.intra_op.allow_spinning", "0")
                put("session.inter_op.allow_spinning", "0")
            }
            if (!prepack) put("session.disable_prepacking", "1")
        }

    /** Lines of the provider config file sherpa-onnx reads (`csrc/session.cc`); empty for none. */
    internal val configLines: List<String>
        get() = sessionConfig.map { "SessionConfig.${it.key}=${it.value}" } +
            listOfNotNull(
                "LogSeverityLevel=0".takeIf { verbose },
                "EnableCpuMemArena=0".takeIf { arena == false },
                optLevel?.let { "GraphOptimizationLevel=$it" },
            )

    /**
     * sherpa-onnx's `provider` string. sherpa-onnx v1.13.8 reads
     * `<provider>:<file>`, forwards the file's `SessionConfig.<key>=<value>`
     * lines to ORT and turns the arena off for `EnableCpuMemArena=0`
     * (`csrc/session.cc`), so this needs no patch. The file goes in [dir], by
     * default the app's cache (Android points `java.io.tmpdir` there), named
     * by [id], so one name always holds the same lines.
     *
     * sherpa-onnx exits the process when that file is missing, so it is
     * checked before every load: kept if it already holds these lines, else
     * written to a temporary file and renamed into place, so a load running
     * at the same time never reads a half-written one. If that fails, the
     * load gets the plain provider (ORT's defaults: spinning, arena on),
     * logged as an error and counted in [configFallbacks]: for a phone that
     * needs the lean mode, that means more memory than the recommender
     * allowed for.
     */
    internal fun sherpaProvider(dir: File = File(System.getProperty("java.io.tmpdir"), "ort")): String {
        val provider = if (xnnpack) "xnnpack" else "cpu"
        val lines = configLines
        if (lines.isEmpty()) return provider
        val text = lines.joinToString("\n", postfix = "\n")
        val file = File(dir, "ort-$id.cfg".replace(':', '_'))
        return try {
            require(':' !in file.absolutePath) { "sherpa-onnx splits the provider string at ':': $file" }
            if (!(file.isFile && file.readText() == text)) {
                dir.mkdirs()
                val tmp = File.createTempFile("ort-", ".tmp", dir)
                try {
                    tmp.writeText(text)
                    check(tmp.renameTo(file)) { "couldn't rename $tmp to $file" }
                } finally {
                    tmp.delete()
                }
            }
            check(file.isFile) { "$file not written" }
            "$provider:${file.absolutePath}"
        } catch (e: Exception) {
            configFallbacks.incrementAndGet()
            Log.e(TAG, "Couldn't write $file; $provider without its session config ($lines)", e)
            provider
        }
    }

    companion object {
        private const val TAG = "OrtTuning"

        /**
         * Loads that got the plain provider because the config file couldn't
         * be written ([sherpaProvider]). Only `OrtTuningTest` reads it, to
         * prove the fallback path runs; on a phone the error log is the record.
         */
        internal val configFallbacks = AtomicInteger()

        /** What users get. */
        val DEFAULT = OrtTuning()

        /**
         * Settings → Developer's choices, [DEFAULT] first. No XNNPACK: it
         * takes nothing from our int8 graphs, so it only adds idle threads
         * (the benchmark can still ask for it, e.g. for an fp32 model).
         */
        val PRESETS: List<OrtTuning> = listOf(
            DEFAULT,
            OrtTuning(spinning = false),
            OrtTuning(pin = true),
            OrtTuning(spinning = false, pin = true),
        )

        private val FLAGS = setOf("nospin", "pin", "verbose", "arena", "noarena", "opt0", "opt1", "opt2", "noprepack")

        /** The tuning for [id] ([OrtTuning.id]), or null if it isn't one. */
        fun parse(id: String): OrtTuning? {
            val (base, coreList) = id.split(':', limit = 2).let { it[0] to it.getOrNull(1) }
            val parts = base.split('-')
            val ep = parts.first()
            if (ep != "cpu" && ep != "xnnpack") return null
            val flags = parts.drop(1)
            if (flags.any { it !in FLAGS } || flags.distinct().size != flags.size) return null
            if ("arena" in flags && "noarena" in flags) return null
            if (flags.count { it.startsWith("opt") } > 1) return null
            val cores = coreList?.split('.')?.map { it.toIntOrNull() ?: return null }
            if (cores != null && "pin" !in flags) return null
            return OrtTuning(
                xnnpack = ep == "xnnpack",
                spinning = "nospin" !in flags,
                pin = "pin" in flags,
                cores = cores,
                verbose = "verbose" in flags,
                arena = if ("arena" in flags) true else if ("noarena" in flags) false else null,
                optLevel = flags.firstOrNull { it.startsWith("opt") }?.removePrefix("opt")?.toInt(),
                prepack = "noprepack" !in flags,
            )
        }
    }
}
