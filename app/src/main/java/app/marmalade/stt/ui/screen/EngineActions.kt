package app.marmalade.stt.ui.screen

import androidx.annotation.StringRes
import app.marmalade.stt.R
import app.marmalade.stt.install.InstallState

/**
 * A button on an engine card, the engine info dialog or the setup guide. [primary] actions
 * move the install forward and get the filled button; the rest are outlined.
 */
enum class EngineAction(val primary: Boolean) {
    /** Start a first download (asks for confirmation first). */
    DOWNLOAD(true),

    /** Continue a paused download. */
    RESUME(true),

    /** Try a failed download again, resuming from what was kept. */
    RETRY(true),

    /** Replace damaged installed files with a fresh download. */
    DOWNLOAD_AGAIN(true),

    /** Stop a running download, keeping what's on disk. */
    CANCEL(false),

    /** Delete a partial download. */
    REMOVE_DOWNLOAD(false),

    /** Re-hash the installed files (info dialog only). */
    CHECK_FILES(false),

    /** Delete the installed model (asks for confirmation). Not in the setup guide. */
    REMOVE(false),
}

/**
 * The buttons to show for an engine in [state], secondary actions first so
 * the primary one sits at the end of the row.
 *
 * Without [onDetail] (the setup guide) it keeps to the install lifecycle.
 * [onDetail] is the Engines tab's info dialog, which adds file management for
 * a model that is on disk: "Check files" and "Remove" once installed, and
 * "Remove" next to "Download again" when the installed files are damaged (so
 * a user who gives up on the model can still get the space back). The
 * Engines cards use [engineCardUi], which drops "Check files".
 */
fun engineActions(state: InstallState, onDetail: Boolean): List<EngineAction> = when (state) {
    InstallState.NotInstalled -> listOf(EngineAction.DOWNLOAD)
    is InstallState.Downloading -> listOf(EngineAction.CANCEL)
    is InstallState.Paused -> listOf(EngineAction.REMOVE_DOWNLOAD, EngineAction.RESUME)
    is InstallState.Failed ->
        if (state.bytesOnDisk > 0L) {
            listOf(EngineAction.REMOVE_DOWNLOAD, EngineAction.RETRY)
        } else {
            listOf(EngineAction.RETRY)
        }
    is InstallState.Corrupt ->
        if (onDetail) listOf(EngineAction.REMOVE, EngineAction.DOWNLOAD_AGAIN) else listOf(EngineAction.DOWNLOAD_AGAIN)
    InstallState.Installed ->
        if (onDetail) listOf(EngineAction.REMOVE, EngineAction.CHECK_FILES) else emptyList()
}

/**
 * A download that just stopped, for the Engines tab and the setup guide to
 * announce to TalkBack (#68): [announcement] takes the engine's name.
 */
enum class DownloadOutcome(@StringRes val announcement: Int) {
    READY(R.string.a11y_download_ready),
    PAUSED(R.string.a11y_download_paused),
    FAILED(R.string.a11y_download_failed),
}

/**
 * The downloads that stopped between [previous] and [current] (install
 * states by engine id), in [current]'s order: Downloading → Installed is
 * [DownloadOutcome.READY], Downloading → Paused (Cancel, or the system
 * stopping the work) is [DownloadOutcome.PAUSED], and any change into Failed
 * is [DownloadOutcome.FAILED] (a failure can come straight from Paused or
 * NotInstalled, before any byte arrives, and a retry that fails again with
 * other numbers is news too). Damaged files aren't here: they turn up only
 * from Check files, whose snackbar says so, or from the disk probe as the
 * screen opens, which isn't a change the user is waiting on. An id missing
 * from [previous] says nothing: the first look after the screen opens isn't
 * a change.
 */
fun downloadOutcomes(
    previous: Map<String, InstallState>,
    current: Map<String, InstallState>,
): List<Pair<String, DownloadOutcome>> = current.mapNotNull { (id, state) ->
    val before = previous[id] ?: return@mapNotNull null
    when {
        before is InstallState.Downloading && state == InstallState.Installed -> id to DownloadOutcome.READY
        before is InstallState.Downloading && state is InstallState.Paused -> id to DownloadOutcome.PAUSED
        state is InstallState.Failed && state != before -> id to DownloadOutcome.FAILED
        else -> null
    }
}

/**
 * "github.com/owner/repo" for a GitHub release asset
 * (`https://github.com/owner/repo/releases/download/<tag>/<file>`). Any other
 * URL is shown without its scheme, trimmed to the host and path before the file.
 */
fun modelSourceLabel(fileUrl: String): String {
    val noScheme = fileUrl.substringAfter("://")
    return when {
        "/releases/download/" in noScheme -> noScheme.substringBefore("/releases/download/")
        else -> noScheme.substringBeforeLast('/')
    }
}
