package app.marmalade.stt.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Open [url] in the user's browser. Returns false (and logs) when no app can
 * handle it — possible on de-Googled or work-profile devices with no browser —
 * so the caller can decide whether to tell the user.
 */
fun openUrl(context: Context, url: String): Boolean =
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        Log.w("OpenUrl", "No app can open $url", e)
        false
    }
