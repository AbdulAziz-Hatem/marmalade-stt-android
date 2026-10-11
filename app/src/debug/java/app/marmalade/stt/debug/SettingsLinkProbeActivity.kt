package app.marmalade.stt.debug

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import app.marmalade.stt.system.SettingsLink
import app.marmalade.stt.system.SettingsLinks
import app.marmalade.stt.system.SystemSettings

/**
 * Debug-only probe for the Settings links (#79). Logs, under tag
 * SettingsLinkProbe, whether each target of every link resolves on this
 * phone, then opens `--es link <VOICE_INPUT|KEYBOARDS|APP_DETAILS>` the way
 * the app does and finishes:
 * `adb shell am start -n app.marmalade.stt.debug/app.marmalade.stt.debug.SettingsLinkProbeActivity --es link VOICE_INPUT`
 */
class SettingsLinkProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "sdk=${Build.VERSION.SDK_INT} manufacturer=${Build.MANUFACTURER} model=${Build.MODEL}")
        for (link in SettingsLink.entries) {
            val targets = SettingsLinks.targets(link, Build.VERSION.SDK_INT)
            for (target in targets) {
                Log.i(TAG, "$link ${if (SystemSettings.canOpen(this, target, target == targets.last())) "OK  " else "SKIP"} $target")
            }
            Log.i(TAG, "$link resolves to ${SystemSettings.resolve(this, link)?.page}")
        }
        intent.getStringExtra("link")?.let { name ->
            val landed = SystemSettings.open(this, SettingsLink.valueOf(name))
            Log.i(TAG, "$name opened ${landed?.page} via $landed")
        }
        finish()
    }

    private companion object {
        const val TAG = "SettingsLinkProbe"
    }
}
