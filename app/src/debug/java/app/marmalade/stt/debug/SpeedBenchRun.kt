package app.marmalade.stt.debug

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import app.marmalade.stt.benchmark.ProcRss
import app.marmalade.stt.benchmark.SessionRecorder
import app.marmalade.stt.benchmark.SessionTiming
import app.marmalade.stt.benchmark.Wav16k
import app.marmalade.stt.benchmark.median
import app.marmalade.stt.benchmark.wordErrorRate
import app.marmalade.stt.install.InstalledModel
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.speech.MIC_SAMPLE_RATE
import app.marmalade.stt.speech.OrtTuning
import app.marmalade.stt.speech.RecognizerThreads
import app.marmalade.stt.speech.SessionOptions
import app.marmalade.stt.speech.SpeedCalibrator
import app.marmalade.stt.speech.SttEngines
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * One headless speed-benchmark run (#23): every requested engine, one at a
 * time, in a shuffled order. Per engine, thread count and ONNX Runtime
 * tuning ([OrtTuning], #31):
 *
 * 1. load the model for whole-clip decoding (timed), decode the first clip
 *    once as a warm-up, then decode every clip [Config.repeats] times: the VAD
 *    split is untimed, the model's decode of each utterance is timed.
 *    RTF = decode time / clip length. With [Config.shuffleClips] (#47) the
 *    clips come in a shuffled order and the warm-up decodes 0.5 s of silence
 *    instead (as [app.marmalade.stt.speech.SttEngine.warmUp]), so with one
 *    repeat every timed decode is the first of its length, as dictation's
 *    utterances are. Without ONNX Runtime's arena that first decode is the
 *    slow one.
 * 2. load it again as a listening engine and play each session clip through a
 *    real session in real time ([SessionRecorder]): first partial after the
 *    onset, end of speech to Final. The first clip plays once more before
 *    them, recorded as the cold start, and once after a fresh
 *    [app.marmalade.stt.speech.SttEngine.warmUp] (#31: the cold start with the
 *    warm-up decode).
 * 3. peak RSS over the whole turn (sampled every 100 ms), thermal status and
 *    battery temperature before and after, and after the load which cores
 *    the process's threads may run on (proof that pinning took).
 *
 * A cool-down separates engines. An engine that saw thermal status moderate
 * or worse, or the screen on, is measured again once at the end. The run stops
 * if the battery falls below 20 % or thermal status reaches severe. While the
 * screen is on (someone is using the phone) it waits between steps.
 *
 * Results go to `files/bench/<tag>-<time>.json`, rewritten after each engine,
 * and a one-line summary per engine to logcat (tag [TAG]).
 */
internal class SpeedBenchRun(
    private val context: Context,
    private val installer: ModelInstaller,
    private val config: Config,
) {
    data class Config(
        /** Catalog ids; empty = every installed engine. */
        val engines: List<String>,
        /** Thread counts to measure each engine at. */
        val threads: List<Int>,
        val cooldownSeconds: Int,
        val seed: Long,
        /** Play the session clips through a live session too. */
        val sessions: Boolean,
        /** Keep going while the screen is on (the emulator's always is). */
        val screenOk: Boolean,
        val tag: String,
        /** Clip names (without .wav) to use; empty = every WAV in files/bench-audio. */
        val clips: List<String> = emptyList(),
        /** Timed decodes of each clip after the warm-up (#34: 3). */
        val repeats: Int = 1,
        /** Run off the charger too (#34, Max's call); battery is logged per turn either way. */
        val unplugged: Boolean = false,
        /** Stop below this battery level. */
        val stopBattery: Int = STOP_BATTERY,
        /** ONNX Runtime tunings to measure each engine and thread count with (#31). */
        val tunings: List<OrtTuning> = listOf(OrtTuning.DEFAULT),
        /** Clips in a shuffled order (by [seed]) after a silence warm-up (#47). */
        val shuffleClips: Boolean = false,
        /**
         * Model formats to load each engine in (#78): `onnx` (the installed
         * files) and/or `ort` (pre-optimised ONNX Runtime files pushed next
         * to them, `<name>.ort` for each `<name>.onnx`; an engine without them
         * is skipped for `ort`).
         */
        val formats: List<String> = listOf(FORMAT_ONNX),
    )

    private class Clip(val name: String, val audio: FloatArray, val reference: String?) {
        val audioMs get() = audio.size * 1_000L / MIC_SAMPLE_RATE
    }

    private val power = context.getSystemService(PowerManager::class.java)
    private val outDir = File(context.filesDir, "bench").apply { mkdirs() }
    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
    private val outFile = File(outDir, "${config.tag}-$stamp.json")
    class Aborted(message: String) : Exception(message)

    /** Runs to the end (or throws [Aborted]); returns the results file. */
    suspend fun run(): File = withContext(Dispatchers.Default) {
        val clips = loadClips().let { if (config.shuffleClips) it.shuffled(Random(config.seed)) else it }
        require(clips.isNotEmpty()) { "no WAVs in files/bench-audio" }
        val sessionClips = clips.filter { it.name.startsWith(SESSION_PREFIX) }
        val installed = (config.engines.ifEmpty { SttEngineCatalog.engines.map { it.id } })
            .mapNotNull { id -> installer.installedModel(id).also { if (it == null) log("skip $id: not installed") } }
            .flatMap { m ->
                config.formats.mapNotNull { format ->
                    if (format == FORMAT_ORT) ortVariant(m).also { if (it == null) log("skip ${m.descriptor.id} as ort: no .ort files") } else m
                }
            }
        require(installed.isNotEmpty()) { "no installed engines to run" }

        val start = env()
        if (!config.unplugged && (!start.plugged || start.batteryPct < START_MIN_BATTERY)) {
            throw Aborted("needs a charger and >= $START_MIN_BATTERY % (plugged=${start.plugged}, ${start.batteryPct} %)")
        }

        val queue = installed.flatMap { m -> config.threads.flatMap { t -> config.tunings.map { u -> Triple(m, t, u) } } }
            .shuffled(Random(config.seed))
        fun Triple<InstalledModel, Int, OrtTuning>.label() = "${first.descriptor.id}@$second/${third.id}/${first.format}"
        val results = JSONArray()
        val root = JSONObject()
            .put("tag", config.tag)
            .put("started", stamp)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            .put("seed", config.seed)
            .put("order", JSONArray(queue.map { it.label() }))
            .put("fastCores", JSONArray(RecognizerThreads.fastCores))
            .put("cooldownSeconds", config.cooldownSeconds)
            .put("clips", JSONArray(clips.map { JSONObject().put("name", it.name).put("audioMs", it.audioMs) }))
            .put("results", results)
        // The probe after the phone is idle, [PROBE_REPEATS] times (#31: #34's
        // single probe ran under load and set the Pixel 8a reference ~1.7x too slow).
        awaitIdle()
        val probes = List(PROBE_REPEATS) { SpeedCalibrator.probeMs(context) }
        root.put("probeMs", median(probes.map { it.toDouble() })!!.toLong()).put("probeRuns", JSONArray(probes))
            .put("probeThreads", RecognizerThreads.count).put("probeEnv", env().json())
        log("probe ms=$probes")
        log("run ${config.tag}: ${queue.size} turns, order ${queue.joinToString { it.label() }}")

        val retried = mutableSetOf<Triple<InstalledModel, Int, OrtTuning>>()
        val pending = ArrayDeque(queue)
        var first = true
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            val (model, threads, tuning) = next
            if (!first) coolDown()
            first = false
            val turn = turn(model, threads, tuning, clips, sessionClips)
            results.put(turn.json)
            outFile.writeText(root.toString(2))
            val key = next
            if (turn.disturbed != null && key !in retried) {
                retried += key
                log("${next.label()} again at the end: ${turn.disturbed}")
                pending.addLast(next)
            }
        }
        root.put("finished", SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()))
        outFile.writeText(root.toString(2))
        outFile
    }

    private class Turn(val json: JSONObject, val disturbed: String?)

    private suspend fun turn(
        model: InstalledModel,
        threads: Int,
        tuning: OrtTuning,
        clips: List<Clip>,
        sessionClips: List<Clip>,
    ): Turn {
        val d = model.descriptor
        val language = if (d.languageOptions != null) "en" else ""
        awaitIdle()
        val before = env()
        val baseline = ProcRss.read() ?: 0L
        val peak = AtomicLong(baseline)
        var maxThermal = before.thermal
        var screenOn = false
        fun checkEnv() {
            val e = env()
            maxThermal = maxOf(maxThermal, e.thermal)
            if (e.interactive) screenOn = true
            safetyStop(e)
        }

        val json = JSONObject().put("engine", d.id).put("displayName", d.displayName).put("threads", threads)
            .put("tuning", tuning.id).put("arena", tuning.arena != false).put("pinnedCores", tuning.pinnedCores?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("family", d.family.name).put("developer", d.developer).put("format", model.format)
        coroutineScope {
            val sampler = launch(Dispatchers.IO) {
                while (true) {
                    ProcRss.read()?.let { v -> peak.accumulateAndGet(v) { a, b -> maxOf(a, b) } }
                    delay(RSS_INTERVAL_MS)
                }
            }
            try {
                // 1. Whole-clip decoding: load, warm-up, RTF.
                val loadStart = System.nanoTime()
                val take = SttEngines.loadForTake(model, context.assets, language, threads, tuning)
                json.put("loadMs", ms(System.nanoTime() - loadStart))
                json.put("threadAffinity", threadAffinity())
                val decodes = JSONArray()
                try {
                    val warm = System.nanoTime()
                    if (config.shuffleClips) {
                        take.transcribeUtterance(FloatArray(WARMUP_SILENCE_SAMPLES))
                    } else {
                        take.utterances(clips.first().audio).forEach { take.transcribeUtterance(it) }
                    }
                    json.put("warmupMs", ms(System.nanoTime() - warm))
                        .put("warmup", if (config.shuffleClips) "silence" else clips.first().name)
                    for (clip in clips) repeat(config.repeats) {
                        ensureActive()
                        checkEnv()
                        val utterances = take.utterances(clip.audio)
                        val t0 = System.nanoTime()
                        val text = utterances.map { take.transcribeUtterance(it) }.filter { it.isNotEmpty() }.joinToString(" ")
                        val decodeMs = ms(System.nanoTime() - t0)
                        decodes.put(
                            JSONObject().put("clip", clip.name).put("audioMs", clip.audioMs)
                                .put("utterances", utterances.size).put("decodeMs", decodeMs)
                                .put("rtf", decodeMs.toDouble() / clip.audioMs)
                                .put("wer", clip.reference?.let { wordErrorRate(it, text) } ?: JSONObject.NULL)
                                .put("text", text),
                        )
                    }
                } finally {
                    take.release()
                }
                json.put("decodes", decodes)
                json.put("rtfByLength", rtfByLength(decodes))

                // 2. Real-time sessions.
                if (config.sessions && sessionClips.isNotEmpty()) {
                    json.put("sessions", sessions(model, language, threads, tuning, sessionClips, ::checkEnv))
                }
            } finally {
                sampler.cancel()
            }
        }
        val after = env()
        maxThermal = maxOf(maxThermal, after.thermal)
        json.put("peakRssMb", peak.get() / MB).put("baselineRssMb", baseline / MB)
            .put("envBefore", before.json()).put("envAfter", after.json()).put("maxThermal", maxThermal)
        val disturbed = when {
            maxThermal >= THERMAL_MODERATE -> "thermal status $maxThermal"
            screenOn && !config.screenOk -> "screen was on"
            else -> null
        }
        json.put("disturbed", disturbed ?: JSONObject.NULL)
        log(summary(json))
        return Turn(json, disturbed)
    }

    private suspend fun sessions(
        model: InstalledModel,
        language: String,
        threads: Int,
        tuning: OrtTuning,
        clips: List<Clip>,
        checkEnv: () -> Unit,
    ): JSONObject {
        var recorder: SessionRecorder? = null
        val engine = SttEngines.create(
            model,
            context.assets,
            language,
            openMic = { hop, effects -> checkNotNull(recorder).openMic(hop, effects) },
            numThreads = threads,
            tuning = tuning,
        )
        val out = JSONObject()
        try {
            val t0 = System.nanoTime()
            engine.load()
            out.put("loadMs", ms(System.nanoTime() - t0))
            suspend fun play(clip: Clip): JSONObject {
                awaitIdle()
                checkEnv()
                val r = SessionRecorder(clip.audio)
                recorder = r
                return withTimeout(clip.audioMs + SESSION_TIMEOUT_MARGIN_MS) {
                    r.record(engine.listen(SessionOptions()))
                }.json(clip)
            }
            // The first session after a load is slower (ONNX Runtime warms up):
            // kept apart as the cold start, so the runs compare steady states.
            out.put("cold", play(clips.first()))
            val runs = JSONArray()
            for (clip in clips) runs.put(play(clip))
            out.put("runs", runs)
            out.put("byLength", sessionsByLength(runs))
            // #31: the cold start again, after SttEngine.warmUp (load plus a silence decode) instead of a plain load.
            engine.release()
            val w0 = System.nanoTime()
            engine.warmUp()
            out.put("warmUpMs", ms(System.nanoTime() - w0))
            out.put("coldWarmed", play(clips.first()))
        } finally {
            engine.release()
        }
        return out
    }

    private fun SessionTiming.json(clip: Clip) = JSONObject()
        .put("clip", clip.name).put("audioMs", audioMs)
        .put("firstPartialMs", firstPartialMs ?: JSONObject.NULL)
        .put("endToFinalMs", endToFinalMs ?: JSONObject.NULL)
        .put("partials", partials).put("finals", finals)
        .put("wer", clip.reference?.let { wordErrorRate(it, text) } ?: JSONObject.NULL)
        .put("text", text)
        .put("error", error ?: JSONObject.NULL)

    /** Per clip-length group ("ls05", "ls10", …, "kokoro"): pooled RTF = total decode / total audio. */
    private fun rtfByLength(decodes: JSONArray): JSONObject {
        val out = JSONObject()
        val all = (0 until decodes.length()).map { decodes.getJSONObject(it) }
        for ((group, rows) in all.groupBy { group(it.getString("clip")) }) {
            out.put(group, rows.sumOf { it.getLong("decodeMs") }.toDouble() / rows.sumOf { it.getLong("audioMs") })
        }
        val ls = all.filter { it.getString("clip").startsWith(SESSION_PREFIX) }
        if (ls.isNotEmpty()) {
            out.put("ls", ls.sumOf { it.getLong("decodeMs") }.toDouble() / ls.sumOf { it.getLong("audioMs") })
        }
        return out
    }

    /** Per clip-length group: median first partial and median end of speech to Final, ms. */
    private fun sessionsByLength(runs: JSONArray): JSONObject {
        val out = JSONObject()
        val all = (0 until runs.length()).map { runs.getJSONObject(it) }
        for ((group, rows) in all.groupBy { group(it.getString("clip")) }) {
            fun med(key: String) = median(rows.filter { !it.isNull(key) }.map { it.getDouble(key) }) ?: JSONObject.NULL
            out.put(group, JSONObject().put("firstPartialMs", med("firstPartialMs")).put("endToFinalMs", med("endToFinalMs")))
        }
        return out
    }

    private fun group(clip: String) = clip.substringBefore('-')

    /**
     * How many of this process's threads may run on which cores, e.g.
     * `{"0-8": 41, "4-8": 9}`: with pinning, the recognizer's ORT threads show
     * up under the fast cores ([OrtTuning.pinnedCores]).
     */
    private fun threadAffinity(): JSONObject {
        val counts = (File("/proc/self/task").listFiles() ?: emptyArray()).mapNotNull { task ->
            runCatching { File(task, "status").readLines() }.getOrNull()
                ?.firstOrNull { it.startsWith("Cpus_allowed_list:") }?.substringAfter(':')?.trim()
        }.groupingBy { it }.eachCount()
        return JSONObject(counts.toSortedMap())
    }

    private fun summary(j: JSONObject): String {
        val rtf = j.getJSONObject("rtfByLength")
        val s = j.optJSONObject("sessions")?.optJSONObject("byLength")
        fun lat(g: String) = s?.optJSONObject(g)?.let { "${it.opt("firstPartialMs")}/${it.opt("endToFinalMs")}" } ?: "-"
        return String.format(
            Locale.ROOT,
            "%s@%d/%s load=%dms rtf ls=%.3f ls05=%.3f ls10=%.3f ls20=%.3f firstPartial/eos->final ls05=%s ls10=%s ls20=%s rss=%dMB thermal=%d disturbed=%s",
            j.getString("engine"), j.getInt("threads"), j.getString("tuning"), j.getLong("loadMs"),
            rtf.optDouble("ls"), rtf.optDouble("ls05"), rtf.optDouble("ls10"), rtf.optDouble("ls20"),
            lat("ls05"), lat("ls10"), lat("ls20"), j.getLong("peakRssMb"), j.getInt("maxThermal"), j.opt("disturbed"),
        )
    }

    private fun loadClips(): List<Clip> {
        val dir = File(context.filesDir, "bench-audio")
        return (dir.listFiles { f -> f.name.endsWith(".wav") } ?: emptyArray()).sortedBy { it.name }
            .filter { config.clips.isEmpty() || it.name.removeSuffix(".wav") in config.clips }.map { f ->
            val ref = File(dir, f.name.removeSuffix(".wav") + ".txt").takeIf { it.isFile }?.readText()?.trim()
            Clip(f.name.removeSuffix(".wav"), Wav16k.decode(f.readBytes()), ref)
        }
    }

    private suspend fun coolDown() {
        log("cool-down ${config.cooldownSeconds} s")
        delay(config.cooldownSeconds * 1_000L)
    }

    /** Wait while someone uses the phone (screen on) or it's off the charger. */
    private suspend fun awaitIdle() {
        var logged = false
        while (true) {
            val e = env()
            safetyStop(e)
            val busy = (!config.screenOk && e.interactive) || (!config.unplugged && !e.plugged)
            if (!busy) return
            if (!logged) log("waiting: screen on=${e.interactive}, plugged=${e.plugged}")
            logged = true
            delay(IDLE_POLL_MS)
        }
    }

    private fun safetyStop(e: Env) {
        if (e.batteryPct in 0 until config.stopBattery) throw Aborted("battery ${e.batteryPct} %")
        if (e.thermal >= THERMAL_SEVERE) throw Aborted("thermal status ${e.thermal}")
    }

    private data class Env(
        val thermal: Int,
        val batteryPct: Int,
        val batteryTempC: Double,
        val plugged: Boolean,
        val interactive: Boolean,
    ) {
        fun json() = JSONObject().put("thermal", thermal).put("batteryPct", batteryPct)
            .put("batteryTempC", batteryTempC).put("plugged", plugged).put("screenOn", interactive)
    }

    private fun env(): Env {
        val battery: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus else 0
        return Env(
            thermal = thermal,
            batteryPct = if (level < 0) -1 else level * 100 / scale,
            batteryTempC = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0,
            plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            interactive = power.isInteractive,
        )
    }

    private fun ms(nanos: Long) = nanos / 1_000_000L

    private fun log(msg: String) = Log.i(TAG, msg)

    companion object {
        const val TAG = "SpeedBench"
        const val FORMAT_ONNX = "onnx"
        const val FORMAT_ORT = "ort"

        /** [model] with each `.onnx` file swapped for the `<name>.ort` next to it (#78); null if one is missing. */
        fun ortVariant(model: InstalledModel): InstalledModel? {
            val files = model.descriptor.files.map { f ->
                if (!f.name.endsWith(".onnx")) return@map f
                val ort = f.name.removeSuffix(".onnx") + ".ort"
                if (!File(model.dir, ort).isFile) return null
                f.copy(name = ort)
            }
            return model.copy(descriptor = model.descriptor.copy(files = files))
        }

        private val InstalledModel.format: String
            get() = if (descriptor.files.any { it.name.endsWith(".ort") }) FORMAT_ORT else FORMAT_ONNX
        const val SESSION_PREFIX = "ls"
        private const val START_MIN_BATTERY = 30
        private const val PROBE_REPEATS = 5
        const val STOP_BATTERY = 20
        private const val THERMAL_MODERATE = 2 // PowerManager.THERMAL_STATUS_MODERATE
        private const val THERMAL_SEVERE = 3 // PowerManager.THERMAL_STATUS_SEVERE
        private const val RSS_INTERVAL_MS = 100L
        private const val IDLE_POLL_MS = 15_000L
        private const val SESSION_TIMEOUT_MARGIN_MS = 120_000L
        private const val MB = 1024L * 1024L
        private const val WARMUP_SILENCE_SAMPLES = MIC_SAMPLE_RATE / 2
    }
}
