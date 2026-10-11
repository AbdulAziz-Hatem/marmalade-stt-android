package app.marmalade.stt.util

import java.net.URLEncoder

// -----------------------------------------------------------------------------
// BugReportUrl — prefilled GitHub new-issue link for Settings → Support → Report a bug.
//
// Deliberately a lightweight markdown body, not a GitHub issue form: the
// bar for reporting should be "type what went wrong and hit submit". The
// environment block users can never remember (version, flavor, Android,
// device) is filled in for them; the prompts are questions, not fields.
//
// The prompts never ask for a recording: audio never leaves the device, and
// a bug report is no exception. A description of what was said is enough.
// -----------------------------------------------------------------------------

object BugReportUrl {

    /**
     * The project's public repository — the one place the URL is spelled out
     * (the licenses screen's "Source code" link reuses it). Assumed: the repo
     * is not public yet; confirm before the first public release.
     */
    const val REPO_URL = "https://github.com/maxwhipw/marmalade-stt-android"

    private const val NEW_ISSUE = "$REPO_URL/issues/new"

    fun build(
        versionName: String,
        flavor: String,
        androidVersion: String,
        deviceModel: String,
    ): String {
        val body = """
            **What happened?**


            **What did you expect instead?**


            **If words came out wrong:** what did you say, and what did the app type? Which engine and keyboard were you using?

            ---
            _Marmalade STT $versionName ($flavor) · Android $androidVersion · ${deviceModel}_
        """.trimIndent()
        return "$NEW_ISSUE?body=${encode(body)}"
    }

    /** URLEncoder is form-encoding (space → `+`); GitHub wants percent-encoding. */
    private fun encode(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
