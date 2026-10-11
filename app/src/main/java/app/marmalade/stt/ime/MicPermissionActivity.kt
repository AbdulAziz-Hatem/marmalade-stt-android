package app.marmalade.stt.ime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.marmalade.stt.R
import app.marmalade.stt.system.SettingsLink
import app.marmalade.stt.system.SystemSettings

/**
 * Asks for the microphone on the voice keyboard's behalf: an input method
 * can't show the permission dialog itself. Shows nothing of its own (a
 * translucent window under the system dialog) and finishes once answered.
 * The keyboard re-checks the permission when its panel comes back.
 */
class MicPermissionActivity : ComponentActivity() {

    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Denied without a rationale: "don't ask again", or denied twice. The
        // system dialog won't come back, so send the user to the app's page.
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(applicationContext, R.string.ime_mic_blocked, Toast.LENGTH_LONG).show()
            openAppSettings()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }
        // After a rotation the pending request's result is still delivered.
        if (savedInstanceState == null) request.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun openAppSettings() {
        SystemSettings.open(this, SettingsLink.APP_DETAILS)
    }
}
