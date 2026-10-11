package app.marmalade.stt.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import java.io.File

/**
 * Debug-only: plays a test WAV through the phone's speaker, in the
 * background, so an automated device pass can "speak" to the voice keyboard
 * or the speech sheet without another app coming to the front (#11).
 *
 * Copy WAVs into `files/test-audio/` with `run-as`, then:
 * `adb shell am start-foreground-service -n app.marmalade.stt.debug/app.marmalade.stt.debug.TestAudioPlayerService --es file s1.wav`
 * Logs "done <file>" under tag [TAG] when playback ends.
 */
class TestAudioPlayerService : Service() {

    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Test audio", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Playing test audio")
            .build()
        ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        player?.release()
        val name = intent?.getStringExtra("file")
        // A bare file name only: the service is exported, so no paths out of test-audio/.
        val file = name?.takeIf { it == File(it).name && it != ".." }
            ?.let { File(File(filesDir, "test-audio"), it) }
        if (file == null || !file.isFile) {
            Log.w(TAG, "no such file: $name")
            stopSelf()
            return START_NOT_STICKY
        }
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            setDataSource(file.path)
            setOnCompletionListener {
                Log.i(TAG, "done $name")
                stopSelf()
            }
            prepare()
            start()
        }
        Log.i(TAG, "playing $name")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release()
        player = null
    }

    private companion object {
        const val TAG = "TestAudio"
        const val CHANNEL = "test-audio"
    }
}
