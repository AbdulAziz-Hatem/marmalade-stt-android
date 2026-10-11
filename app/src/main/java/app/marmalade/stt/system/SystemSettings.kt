package app.marmalade.stt.system

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.marmalade.stt.R

/**
 * Opens [SettingsLinks]' screens (#79). Each target is resolved before it is
 * launched: it must exist on this phone, be exported, need no permission we
 * lack, and match one activity (an action matching several would show a
 * chooser; only the last fallback may). The manifest's `<queries>` makes the
 * Settings app visible to these lookups.
 */
object SystemSettings {

    private const val TAG = "SystemSettings"

    /**
     * Settings' extra naming the row to scroll to and highlight
     * ([SettingsTarget.highlight]). Up to 16 Settings reads it only from the
     * fragment-arguments bundle; 17 also reads it from the intent.
     */
    private const val HIGHLIGHT_KEY = ":settings:fragment_args_key"
    private const val FRAGMENT_ARGS = ":settings:show_fragment_args"

    /** How soon a return to our screen counts as the page bouncing (#79: Settings' crash took ~0.5 s). */
    private const val BOUNCE_MS = 1_500L

    /** The first of [link]'s targets this phone can open, or null if none. */
    fun resolve(context: Context, link: SettingsLink): SettingsTarget? {
        val targets = SettingsLinks.targets(link, Build.VERSION.SDK_INT)
        return targets.firstOrNull { canOpen(context, it, last = it == targets.last()) }
    }

    /**
     * Open [link]'s best screen and return where it landed, or null if no
     * screen opened. When only a fallback opened, a toast says where to go
     * from there.
     *
     * Settings always opens in its own task, fresh (CLEAR_TASK, so Back
     * doesn't walk pages left from an earlier visit; it returns to us). In
     * our task, a Settings page that crashes takes our screen down with it:
     * Android 17's Language & region did, and the app vanished to the home
     * screen (the Pixel 8a, 2026-10-10). If the page bounces straight back
     * anyway ([watchForBounce]), Settings' home opens instead, so the user
     * always gets somewhere in Settings.
     */
    fun open(context: Context, link: SettingsLink): SettingsTarget? {
        val targets = SettingsLinks.targets(link, Build.VERSION.SDK_INT)
        val landed = targets.firstOrNull { target ->
            canOpen(context, target, last = target == targets.last()) && start(context, intentFor(context, target))
        }
        Log.i(TAG, "$link landed on ${landed?.page}")
        if (landed != null && SettingsLinks.needsHint(link, landed.page)) {
            Toast.makeText(context.applicationContext, link.hintRes(), Toast.LENGTH_LONG).show()
        }
        if (landed != null && landed != targets.last()) watchForBounce(context, link, targets.last())
        return landed
    }

    /**
     * A page that crashes or refuses us closes at once and our screen comes
     * back within [BOUNCE_MS] of leaving; a person reading it takes longer.
     * Then open [home] (Settings' home) with the hint. Activities that finish
     * as they open Settings aren't watched: they don't come back.
     */
    private fun watchForBounce(context: Context, link: SettingsLink, home: SettingsTarget) {
        val owner = context.findActivity() as? LifecycleOwner ?: return
        val started = SystemClock.elapsedRealtime()
        var left = false
        owner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                val elapsed = SystemClock.elapsedRealtime() - started
                when {
                    elapsed > BOUNCE_MS || event == Lifecycle.Event.ON_DESTROY -> source.lifecycle.removeObserver(this)
                    event == Lifecycle.Event.ON_PAUSE -> left = true
                    event == Lifecycle.Event.ON_RESUME && left -> {
                        source.lifecycle.removeObserver(this)
                        Log.w(TAG, "$link bounced back after $elapsed ms; opening Settings' home")
                        val activity = source as? Activity ?: return
                        if (start(activity, intentFor(activity, home))) {
                            Toast.makeText(activity.applicationContext, link.hintRes(), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    @StringRes
    private fun SettingsLink.hintRes(): Int = when (this) {
        SettingsLink.VOICE_INPUT -> R.string.settings_hint_voice_input
        SettingsLink.KEYBOARDS -> R.string.settings_hint_keyboards
        SettingsLink.APP_DETAILS -> R.string.settings_hint_app_details
    }

    fun intentFor(context: Context, target: SettingsTarget): Intent {
        val intent = when (target) {
            is SettingsTarget.Component -> Intent(target.action).setClassName(target.packageName, target.className)
            is SettingsTarget.Action -> Intent(target.action).apply {
                if (target.forApp) data = Uri.fromParts("package", context.packageName, null)
            }
        }
        target.highlight?.let { key ->
            intent.putExtra(HIGHLIGHT_KEY, key)
            // 17 reads the top-level extra; the bundle is checked only up to 16.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.BAKLAVA) {
                intent.putExtra(FRAGMENT_ARGS, Bundle().apply { putString(HIGHLIGHT_KEY, key) })
            }
        }
        return intent
    }

    /** Whether [target] resolves to one activity we may start ([last]: a chooser will do). */
    fun canOpen(context: Context, target: SettingsTarget, last: Boolean = false): Boolean {
        val matches = query(context.packageManager, intentFor(context, target))
            .map { it.activityInfo }
            .filter { it.exported && (it.permission == null || context.checkSelfPermission(it.permission) == PackageManager.PERMISSION_GRANTED) }
        val ok = matches.size == 1 || (last && matches.isNotEmpty())
        if (!ok) Log.i(TAG, "Skipping $target: ${matches.size} startable matches")
        return ok
    }

    private fun query(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

    private fun start(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No settings page for ${intent.action ?: intent.component}", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Settings page not open to us: ${intent.action ?: intent.component}", e)
            false
        }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
