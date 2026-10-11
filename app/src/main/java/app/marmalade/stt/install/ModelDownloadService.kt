package app.marmalade.stt.install

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.marmalade.stt.MainActivity
import app.marmalade.stt.R
import app.marmalade.stt.ui.byteProgress
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   ModelDownloads.start(id) ──keepAlive──► startForegroundService(this)
//     onStartCommand: startForeground(dataSync) at once (Android's deadline)
//     downloads.running ──first id──► installer.state(id) ──► the notification
//     running empty ──► stopForeground + stopSelfResult(last start id)
//   Android 15+'s 6-hour dataSync limit ──onTimeout──► downloads.cancelAll()
//     (each download is left Paused) and stop.
// -----------------------------------------------------------------------------

/**
 * A foreground service ("data sync") that runs while any model download does,
 * so Android doesn't cut the app's network when the screen turns off or the
 * user leaves the app (#74). It does no work itself: [ModelDownloads] runs
 * the downloads, this keeps the process in a state where they may use the
 * network, and shows their progress.
 *
 * The app doesn't ask for the notification permission: without it Android
 * 13+ hides the progress notification but still runs the service (checked
 * on the API 35 emulator: the download survived the screen turning off).
 * Android 12 and older show it.
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {

    @Inject lateinit var downloads: ModelDownloads

    @Inject lateinit var installer: ModelInstaller

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching = false
    private var lastStartId = 0
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Android requires startForeground soon after startForegroundService,
        // even when the download has already finished.
        val foreground = goForegroundOrPause(
            goForeground = { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(null), foregroundType()) },
            pause = downloads::cancelAll,
            stop = { stopSelfResult(startId) },
        )
        if (foreground && !watching) {
            watching = true
            scope.launch { watch() }
        }
        return START_NOT_STICKY
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun watch() {
        downloads.running
            .flatMapLatest { ids ->
                val id = ids.firstOrNull() ?: return@flatMapLatest flowOf(null)
                installer.state(id).map { id to it }
            }
            .collect { current ->
                if (current == null) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    // False when a newer start is on its way; its onStartCommand goes foreground again.
                    stopSelfResult(lastStartId)
                } else {
                    update(current.first, current.second)
                }
            }
    }

    /** Called by Android 15+ when the day's 6 hours of data sync are used up. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground time used up; pausing the downloads")
        downloads.cancelAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(lastStartId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * At most one notification update a second: Android drops an app's updates past a few per second.
     * The app doesn't ask for POST_NOTIFICATIONS (ARCHITECTURE, #74): on Android 13+ without it
     * the notification just stays hidden, and the foreground service runs regardless.
     */
    @SuppressLint("NotificationPermission")
    private fun update(engineId: String, state: InstallState) {
        val now = SystemClock.elapsedRealtime()
        if (state is InstallState.Downloading && now - lastNotified < UPDATE_INTERVAL_MS) return
        lastNotified = now
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification(engineId to state))
    }

    private fun notification(current: Pair<String, InstallState>?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
        val engine = current?.first?.let(SttEngineCatalog::byId)
        val state = current?.second
        if (engine != null) builder.setContentTitle(engine.displayName)
        if (state is InstallState.Downloading) {
            val progress = byteProgress(state.bytesDone, state.totalBytes)
            builder.setContentText(
                getString(R.string.engine_status_downloading, getString(R.string.engine_progress_bytes, progress.done, progress.total)),
            )
            builder.setProgress(PROGRESS_MAX, scaled(state.bytesDone, state.totalBytes), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun scaled(done: Long, total: Long): Int =
        if (total <= 0L) 0 else (done.coerceIn(0L, total) * PROGRESS_MAX / total).toInt()

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0

    companion object {
        internal const val TAG = "ModelDownloadService"
        private const val CHANNEL = "model_downloads"
        private const val NOTIFICATION_ID = 74
        private const val PROGRESS_MAX = 1000
        private const val UPDATE_INTERVAL_MS = 1_000L

        /**
         * Start the service. Only while the app is in front: Android 12+
         * refuses a foreground service started from the background, and
         * then the download runs without one (logged, not fatal).
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException.
                Log.w(TAG, "Couldn't start the download service", e)
            }
        }
    }
}

/**
 * Runs [goForeground]; when Android refuses (ForegroundServiceStartNotAllowedException,
 * an [IllegalStateException], e.g. a start that raced the app into the
 * background), the downloads would lose the network anyway, so they are
 * paused ([pause]: each left Paused with its bytes) and the service stops
 * ([stop]). Returns whether the service is in the foreground.
 */
internal fun goForegroundOrPause(goForeground: () -> Unit, pause: () -> Unit, stop: () -> Unit): Boolean =
    try {
        goForeground()
        true
    } catch (e: IllegalStateException) {
        Log.w(ModelDownloadService.TAG, "Android refused the foreground service; pausing the downloads", e)
        pause()
        stop()
        false
    }
