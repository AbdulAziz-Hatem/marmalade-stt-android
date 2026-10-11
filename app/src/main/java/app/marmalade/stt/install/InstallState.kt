package app.marmalade.stt.install

import java.io.IOException

/**
 * Where one engine's model files stand. [ModelInstaller.state] publishes it.
 *
 * ```
 *   NotInstalled ──install──► Downloading ──► Installed
 *                                │  ▲
 *                 cancel / death │  │ install (resumes from the bytes on disk)
 *                                ▼  │
 *                              Paused / Failed
 *
 *   Corrupt: the final dir exists but doesn't match the catalog. The UI
 *   offers a re-download; the installer never falls back silently.
 * ```
 */
sealed class InstallState {
    data object NotInstalled : InstallState()

    /** A download is running. [currentFile] is the [ModelFile.name] being fetched. */
    data class Downloading(
        val bytesDone: Long,
        val totalBytes: Long,
        val currentFile: String,
    ) : InstallState()

    /**
     * Part of the download is on disk and nothing is running: the user
     * cancelled, or the process died mid-download. [ModelInstaller.install]
     * resumes from [bytesOnDisk].
     */
    data class Paused(val bytesOnDisk: Long, val totalBytes: Long) : InstallState()

    data object Installed : InstallState()

    /**
     * The last install attempt failed. [reason] is a plain English sentence;
     * [failure] names the kind when known so the UI can localize it.
     * [bytesOnDisk] is how much of the download was kept for a retry (0 when
     * it was discarded), so the UI can offer "Remove download".
     */
    data class Failed(
        val reason: String,
        val failure: InstallFailure? = null,
        val bytesOnDisk: Long = 0L,
    ) : InstallState()

    /**
     * The installed files are unusable: one is missing, has the wrong size or
     * (on a deep verify) the wrong SHA-256, or the install record doesn't
     * match the catalog this app ships: [problem], and the [file] it is
     * about (for every problem but [CorruptProblem.OUTDATED]). The UI words
     * it (#51).
     */
    data class Corrupt(val problem: CorruptProblem, val file: String = "") : InstallState()
}

/** What is wrong with an installed engine's files. */
enum class CorruptProblem {
    /** The install record doesn't match this app's catalog (another version's files). */
    OUTDATED,

    /** A file is missing. */
    MISSING,

    /** A file has the wrong size. */
    WRONG_SIZE,

    /** A file's SHA-256 is wrong (deep verify). */
    DAMAGED,
}

/** True while a download runs. The installer owns the state until it ends. */
val InstallState.isInFlight: Boolean
    get() = this is InstallState.Downloading

/** Install failures the UI explains in the user's language. */
enum class InstallFailure {
    /** HTTP 404/410: the file isn't at its catalog URL. */
    NOT_AVAILABLE,

    /** Not enough free storage for the rest of the download. */
    NO_SPACE,

    /** The connection failed or dropped. The partial download is kept. */
    NETWORK,

    /** A downloaded file didn't match its catalog SHA-256. That file was deleted. */
    CHECKSUM,
}

/**
 * An install failure of a known [failure] kind. [message] is a plain English
 * sentence (never a URL or errno); the technical detail goes to logcat.
 */
class InstallException(
    val failure: InstallFailure,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

internal const val NOT_AVAILABLE_MESSAGE = "This model isn't available for download right now. Try again later."
internal const val NO_SPACE_MESSAGE =
    "Not enough free storage for this model. Free up some space and try again."
internal const val NETWORK_MESSAGE =
    "The download was interrupted. Check your connection and try again; it will pick up where it left off."
internal const val CHECKSUM_MESSAGE =
    "A downloaded file was damaged, so it was deleted. Try again to download it fresh."
internal const val GENERIC_FAILURE_MESSAGE = "The model couldn't be installed. Try again."
