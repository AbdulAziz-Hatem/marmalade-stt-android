package app.marmalade.stt.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.speech.OrtTuning
import app.marmalade.stt.speech.RecognizerThreads
import app.marmalade.stt.speech.SpeechEngineManager
import app.marmalade.stt.speech.SpeedCalibrator
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Debug-only, headless engine speed benchmark (#23). Only the adb shell can
 * start it (DUMP permission, like [TestAudioPlayerService]). Driven by
 * `tools/speed-bench/` on the host; every command logs under tag
 * [SpeedBenchRun.TAG].
 *
 * ```
 * S=app.marmalade.stt.debug/app.marmalade.stt.debug.SpeedBenchmarkService
 * adb shell am start-foreground-service -n $S --es cmd catalog
 *     # writes files/bench/catalog.tsv: engine, file name, size, sha256
 * adb shell am start-foreground-service -n $S --es cmd install --es engines all
 *     # installs engines whose files were pushed into models/<id>.partial/<name>.part:
 *     # the installer hashes each file against the catalog. Never downloads:
 *     # an engine with a missing or short file is skipped. --ez download true fetches the
 *     # missing files from the catalog URLs instead (#78).
 * adb shell am start-foreground-service -n $S --es cmd run [--es engines a,b] [--es threads 2,4]
 *     [--ei cooldown 60] [--el seed 1] [--ez sessions true] [--ez screenOk false] [--es tag pixel]
 *     [--es clips ls10-1,ls20-1] [--ei repeats 3] [--ez unplugged false] [--ei stopBattery 20]
 *     [--es tunings cpu,cpu-nospin,xnnpack-nospin,cpu-nospin-pin] [--ez shuffleClips false]
 *     # see SpeedBenchRun; results in files/bench/<tag>-<time>.json. Tunings are OrtTuning ids
 *     # (#31; default: OrtTuning.DEFAULT); cpu-pin:0.1 pins to cores 0 and 1 instead of the fast cores.
 *     # shuffleClips (#47): each clip once, in a shuffled order, after a silence warm-up (with --ei repeats 1).
 *     # --es formats onnx,ort (#78): each engine also as the <name>.ort files pushed next to its .onnx files.
 * adb shell am start-foreground-service -n $S --es cmd probe
 *     # the #27 CPU probe (SpeedProbe) 5 times, the fastest counts: the Pixel 8a's is SpeedBudget.PROBE_REFERENCE_MS
 * adb shell am start-foreground-service -n $S --es cmd stop
 * ```
 *
 * A run holds a partial wake lock so the CPU keeps going with the screen off.
 * The user's active engine is released first so two models never share RAM.
 */
@AndroidEntryPoint
class SpeedBenchmarkService : Service() {

    @Inject lateinit var installer: ModelInstaller

    @Inject lateinit var engines: SpeechEngineManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Speed benchmark", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Speed benchmark running")
            .build()
        ServiceCompat.startForeground(this, 2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        val cmd = intent?.getStringExtra("cmd")
        if (cmd == "stop") {
            log("stop requested")
            job?.cancel()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) {
            log("busy: a command is running; send cmd stop first")
            return START_NOT_STICKY
        }
        job = scope.launch {
            try {
                when (cmd) {
                    "catalog" -> catalog()
                    "probe" -> probe()
                    "install" -> install(intent.engines(), download = intent.getBooleanExtra("download", false))
                    "run" -> run(intent)
                    else -> log("unknown cmd: $cmd")
                }
            } catch (e: CancellationException) {
                log("cancelled")
                throw e
            } catch (e: SpeedBenchRun.Aborted) {
                log("ABORTED: ${e.message}")
            } catch (e: Exception) {
                Log.e(SpeedBenchRun.TAG, "FAILED: $cmd", e)
            } finally {
                wakeLock?.takeIf { it.isHeld }?.release()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun Intent.engines(): List<String> =
        getStringExtra("engines")?.takeIf { it != "all" }?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun catalog() {
        val out = File(File(filesDir, "bench").apply { mkdirs() }, "catalog.tsv")
        out.writeText(
            SttEngineCatalog.engines.flatMap { d -> d.files.map { f -> "${d.id}\t${f.name}\t${f.sizeBytes}\t${f.sha256}" } }
                .joinToString("\n", postfix = "\n"),
        )
        log("catalog written: ${out.path}")
    }

    private fun probe() {
        val runs = List(PROBE_RUNS) { SpeedCalibrator.probeMs(this) }
        log("probe ms=${runs.min()} runs=$runs threads=${RecognizerThreads.count}")
    }

    /** [download]: fetch what isn't staged from the catalog URLs (#78) instead of skipping the engine. */
    private suspend fun install(ids: List<String>, download: Boolean) {
        val models = File(filesDir, ModelInstaller.MODELS_DIR)
        for (d in SttEngineCatalog.engines.filter { ids.isEmpty() || it.id in ids }) {
            if (installer.installedModel(d.id) != null) {
                log("install ${d.id}: already installed")
                continue
            }
            val staging = File(models, d.id + ModelInstaller.STAGING_SUFFIX)
            val missing = d.files.filter { f ->
                val part = File(staging, f.name + ModelInstaller.PART_SUFFIX)
                !(part.isFile && part.length() == f.sizeBytes)
            }
            if (missing.isNotEmpty() && !download) {
                if (ids.isNotEmpty()) log("install ${d.id}: skipped, not staged: ${missing.joinToString { it.name }}")
                continue
            }
            // Every file is complete in staging, so the installer only hashes them (no network), unless [download].
            val result = installer.install(d.id)
            log("install ${d.id}: ${if (result.isSuccess) "OK (sha256 verified)" else "FAILED ${result.exceptionOrNull()?.message}"}")
        }
    }

    private suspend fun run(intent: Intent) {
        val config = SpeedBenchRun.Config(
            engines = intent.engines(),
            threads = intent.getStringExtra("threads")?.split(',')?.map { it.trim().toInt() }
                ?: listOf(RecognizerThreads.count),
            cooldownSeconds = intent.getIntExtra("cooldown", 60),
            seed = intent.getLongExtra("seed", System.currentTimeMillis()),
            sessions = intent.getBooleanExtra("sessions", true),
            screenOk = intent.getBooleanExtra("screenOk", false),
            tag = intent.getStringExtra("tag")?.filter { it.isLetterOrDigit() || it == '-' }?.ifEmpty { null } ?: "run",
            clips = intent.getStringExtra("clips")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            repeats = intent.getIntExtra("repeats", 1).coerceAtLeast(1),
            unplugged = intent.getBooleanExtra("unplugged", false),
            stopBattery = intent.getIntExtra("stopBattery", SpeedBenchRun.STOP_BATTERY),
            tunings = intent.getStringExtra("tunings")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.map { OrtTuning.parse(it) ?: throw SpeedBenchRun.Aborted("unknown tuning $it") }
                ?: listOf(OrtTuning.DEFAULT),
            shuffleClips = intent.getBooleanExtra("shuffleClips", false),
            formats = intent.getStringExtra("formats")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.onEach { if (it != SpeedBenchRun.FORMAT_ONNX && it != SpeedBenchRun.FORMAT_ORT) throw SpeedBenchRun.Aborted("unknown format $it") }
                ?: listOf(SpeedBenchRun.FORMAT_ONNX),
        )
        engines.releaseModel()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "marmalade-stt:speed-bench")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        log("start: $config (device threads ${RecognizerThreads.count}, fast cores ${RecognizerThreads.fastCores})")
        val file = SpeedBenchRun(this, installer, config).run()
        log("done ${file.path}")
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private fun log(msg: String) = Log.i(SpeedBenchRun.TAG, msg)

    private companion object {
        const val CHANNEL = "speed-bench"
        const val PROBE_RUNS = 5
        const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L
    }
}
