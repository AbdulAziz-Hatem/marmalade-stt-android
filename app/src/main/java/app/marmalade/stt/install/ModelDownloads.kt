package app.marmalade.stt.install

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the app allowed to use the network while a download runs.
 * Production starts [ModelDownloadService], a foreground service; tests
 * count the calls.
 */
fun interface DownloadKeepAlive {
    /** A download just started. Called on the thread that started it, while the app is in front. */
    fun downloadStarted()
}

/**
 * Runs [ModelInstaller.install] in an app-wide scope, so a download keeps going
 * when the user leaves the screen that started it. Progress and outcome are
 * read from [ModelInstaller.state]; this class only starts and cancels.
 *
 * The scope alone isn't enough: Android 15+ cuts the network of an app that
 * has no activity in front and no foreground service, a few seconds after
 * the screen turns off or the user switches apps (#74: "Software caused
 * connection abort", verified on the API 35 emulator). So every start also
 * calls [keepAlive], and [ModelDownloadService] stays in the foreground
 * while [running] isn't empty.
 */
@Singleton
class ModelDownloads @Inject constructor(
    private val installer: ModelInstaller,
    private val keepAlive: DownloadKeepAlive,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val _running = MutableStateFlow<Set<String>>(emptySet())

    /** The engines whose download is running now. */
    val running: StateFlow<Set<String>> = _running.asStateFlow()

    /** Start (or resume) [engineId]'s download. A no-op while one is running. */
    fun start(engineId: String) {
        synchronized(jobs) {
            if (jobs[engineId]?.isActive == true) return
            val job = scope.launch { installer.install(engineId) }
            jobs[engineId] = job
            _running.value = jobs.keys.toSet()
            job.invokeOnCompletion {
                synchronized(jobs) {
                    if (jobs[engineId] === job) jobs.remove(engineId)
                    _running.value = jobs.keys.toSet()
                }
            }
        }
        keepAlive.downloadStarted()
    }

    /** Stop [engineId]'s download. The installer leaves it Paused with the bytes kept. */
    fun cancel(engineId: String): Unit = synchronized(jobs) { jobs[engineId]?.cancel() }

    /** Stop every download (the service's time ran out). Each is left Paused. */
    fun cancelAll(): Unit = synchronized(jobs) { jobs.values.forEach { it.cancel() } }

    fun isRunning(engineId: String): Boolean = synchronized(jobs) { jobs[engineId]?.isActive == true }
}
