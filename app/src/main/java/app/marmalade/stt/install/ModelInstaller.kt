package app.marmalade.stt.install

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   Engines screen ──ModelDownloads.start(id)──► ModelInstaller.install(id)
//     │                                             (per-engine Mutex, Dispatchers.IO)
//     │  state(id): StateFlow<InstallState> ◄──────────┤
//     │                                                ├─ delete staging files not of the current set
//     │                                                ├─ free-space check (rest of download + 50 MB)
//     │                                                ├─ for each ModelFile not yet verified in staging:
//     │                                                │    HttpFetcher.openRange(url, bytes in <name>.part)
//     │                                                │    ─► append to models/<id>.partial/<name>.part,
//     │                                                │       streaming SHA-256 (existing bytes re-hashed)
//     │                                                │    ─► sha ok: rename .part → <name>  (else CHECKSUM)
//     │                                                │    ─► the stated file size must match the catalog's
//     │                                                │       before the .part is touched (else NETWORK)
//     │                                                │    a dropped connection, wrong size or HTTP 408/429/5xx:
//     │                                                │       retried after 2/5/10/20 s, resuming the .part
//     │                                                │       (NETWORK once they run out; other 4xx at once;
//     │                                                │       416: the .part is deleted, CHECKSUM)
//     │                                                ├─ write .install.json (name → sha256)
//     │                                                ├─ EngineRelease.release(id); delete old models/<id>
//     │                                                └─ rename models/<id>.partial → models/<id> (atomic)
//     │
//   Speech layer ──installedModel(id)──► InstalledModel(descriptor, models/<id>) ─► path(role)
//     (an install of the descriptor's previousFiles, #78, comes back with
//      descriptor.files = those files and previousFormat = true)
//   MarmaladeSttApplication ──removeUnknownModels()──► deletes every models/<id>
//     (and <id>.partial) whose id is no longer in the catalog (#44, #45)
//
//   Staging survives cancel and process death: a later state() probe reports
//   Paused(bytesOnDisk) and install() resumes from the bytes on disk.
// -----------------------------------------------------------------------------

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The directory `models/` lives in: `Context.filesDir` in production, a temp dir in tests. */
fun interface ModelFilesDir {
    fun get(): File
}

/**
 * Frees an engine's loaded model before its files are deleted or replaced.
 * Must be idempotent and safe to call when the engine isn't loaded.
 */
fun interface EngineRelease {
    fun release(engineId: String)
}

/**
 * An engine whose files passed the install probe. [descriptor]'s files are
 * the set on disk: the catalog's, or, when [previousFormat], the
 * [SttEngineDescriptor.previousFiles] an older version installed (#78), which
 * still run. [SttEngineDescriptor.sizeBytes] is then their size.
 */
data class InstalledModel(
    val descriptor: SttEngineDescriptor,
    val dir: File,
    val previousFormat: Boolean = false,
) {
    fun path(role: FileRole): File = File(dir, descriptor.file(role).name)
}

/**
 * Downloads, verifies and removes engine model files (ARCHITECTURE.md D5)
 * under `models/` in app-private storage.
 *
 * Invariants:
 *  1. Nothing the speech layer reads is ever partial: files land in
 *     `models/<id>.partial/` and the whole directory is renamed into place
 *     once every file matched its SHA-256.
 *  2. A partial download survives cancel and process death; there is no
 *     startup sweep of catalog engines. A verified file in staging is never
 *     fetched again.
 *  3. One operation per engine at a time (a [Mutex] per engine). While an
 *     install holds it, [verify] reports the live state instead of
 *     publishing a disk probe over the progress.
 *  4. The network is used only by [install].
 *  5. A final dir holding an engine's [SttEngineDescriptor.previousFiles]
 *     (exactly that set, by its install record) is Installed and runs from
 *     them (#78); [install] leaves it alone, [uninstall] removes it.
 */
@Singleton
open class ModelInstaller @Inject constructor(
    private val filesDir: ModelFilesDir,
    private val fetcher: HttpFetcher,
    private val release: EngineRelease,
) {
    private val states = mutableMapOf<String, MutableStateFlow<InstallState>>()
    private val mutexes = mutableMapOf<String, Mutex>()
    private val loggedPrevious = mutableSetOf<String>()

    /**
     * The live state of [engineId]. The first call per engine probes the disk
     * (presence, sizes, install record; no hashing). Unknown ids are
     * [InstallState.NotInstalled].
     */
    fun state(engineId: String): StateFlow<InstallState> = stateFlow(engineId).asStateFlow()

    /**
     * Download [engineId]'s files, resuming whatever is on disk, and install
     * them. A no-op success when the engine is already installed, its previous
     * files included (invariant 5). The state
     * flow is updated before this returns. Cancelling the calling coroutine
     * stops the download promptly, leaves [InstallState.Paused] with the bytes
     * kept, and rethrows the [CancellationException].
     */
    suspend fun install(engineId: String): Result<Unit> {
        val descriptor = descriptor(engineId) ?: return unknownEngine(engineId)
        return withContext(Dispatchers.IO) { mutex(engineId).withLock { runInstall(descriptor) } }
    }

    /**
     * Import [engineId]'s files from a [zipInput] stream, verifying their sizes
     * and SHA-256 hashes against the catalog before activating them.
     */
    suspend fun importArchive(engineId: String, zipInput: InputStream): Result<Unit> {
        val descriptor = descriptor(engineId) ?: return unknownEngine(engineId)
        return withContext(Dispatchers.IO) { mutex(engineId).withLock { runImport(descriptor, zipInput) } }
    }

    /**
     * Export [engineId]'s installed files into a [zipOutput] stream.
     */
    suspend fun exportArchive(engineId: String, zipOutput: OutputStream): Result<Unit> {
        val descriptor = descriptor(engineId) ?: return unknownEngine(engineId)
        return withContext(Dispatchers.IO) { mutex(engineId).withLock { runExport(descriptor, zipOutput) } }
    }

    /** Release the engine, then delete its installed files and any partial download. */
    suspend fun uninstall(engineId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val descriptor = descriptor(engineId) ?: return@withContext unknownEngine(engineId)
        mutex(engineId).withLock {
            release.release(engineId)
            val deleted = deleteDir(finalDir(engineId)) && deleteDir(stagingDir(engineId))
            stateFlow(engineId).value = probe(descriptor)
            if (deleted) Result.success(Unit) else Result.failure(IOException("Couldn't delete the model files."))
        }
    }

    /** Delete a partial download (the "Remove download" action), then re-probe. */
    suspend fun discardDownload(engineId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val descriptor = descriptor(engineId) ?: return@withContext unknownEngine(engineId)
        mutex(engineId).withLock {
            val deleted = deleteDir(stagingDir(engineId))
            stateFlow(engineId).value = probe(descriptor)
            if (deleted) Result.success(Unit) else Result.failure(IOException("Couldn't delete the download."))
        }
    }

    /**
     * Re-check [engineId] on disk and publish the result. [deep] also re-hashes
     * every installed file (the user-facing "Check files"). While an install
     * of the engine is running, returns its live state and publishes nothing.
     */
    suspend fun verify(engineId: String, deep: Boolean = false): InstallState = withContext(Dispatchers.IO) {
        val descriptor = descriptor(engineId) ?: return@withContext InstallState.NotInstalled
        val mutex = mutex(engineId)
        if (!mutex.tryLock()) return@withContext stateFlow(engineId).value
        try {
            var result = probe(descriptor)
            if (deep && result == InstallState.Installed) result = deepCheck(descriptor)
            stateFlow(engineId).value = result
            result
        } finally {
            mutex.unlock()
        }
    }

    /**
     * The installed files of [engineId], or null unless the (cheap) probe says
     * Installed. An install of its previous files (invariant 5) comes back
     * with them as the descriptor's files and [InstalledModel.previousFormat].
     */
    fun installedModel(engineId: String): InstalledModel? {
        val descriptor = descriptor(engineId) ?: return null
        val dir = finalDir(engineId)
        val files = recordedSet(descriptor, dir) ?: return null
        if (checkFiles(files, dir) != InstallState.Installed) return null
        if (files == descriptor.files) return InstalledModel(descriptor, dir)
        if (synchronized(loggedPrevious) { loggedPrevious.add(engineId) }) {
            Log.i(TAG, "$engineId runs from its previous files: ${files.joinToString { it.name }}")
        }
        return InstalledModel(descriptor.copy(files = files), dir, previousFormat = true)
    }

    /**
     * Delete everything under `models/` whose id is no longer in the catalog:
     * installed files and partial downloads of engines that left the app
     * (#45), and the Smart Turn download from before the model was bundled
     * (#44). The app calls it at startup. Catalog engines are left alone (that
     * is [uninstall]). Returns the ids it removed.
     */
    suspend fun removeUnknownModels(): List<String> = withContext(Dispatchers.IO) {
        val ids = modelsRoot().listFiles { f -> f.isDirectory }.orEmpty()
            .map { it.name.removeSuffix(STAGING_SUFFIX) }
            .distinct()
            .filter { descriptor(it) == null }
        ids.filter { id ->
            mutex(id).withLock { deleteDir(finalDir(id)) && deleteDir(stagingDir(id)) }.also { deleted ->
                if (deleted) Log.i(TAG, "Removed $id, no longer in the catalog") else Log.w(TAG, "Couldn't remove $id")
            }
        }
    }

    /** Catalog lookup. A seam so tests can install synthetic engines. */
    internal open fun descriptor(engineId: String): SttEngineDescriptor? = SttEngineCatalog.byId(engineId)

    /** Bytes this app may still write on [dir]'s volume. A seam so tests can fake a full disk. */
    internal open fun usableSpaceBytes(dir: File): Long = dir.usableSpace

    /** The pause before a dropped download is retried. A seam so tests don't wait. */
    internal open suspend fun networkRetryDelay(millis: Long) = delay(millis)

    // -- install pipeline ------------------------------------------------------

    /** Caller holds the engine's mutex and runs on an IO dispatcher. */
    private suspend fun runInstall(d: SttEngineDescriptor): Result<Unit> {
        val sf = stateFlow(d.id)
        val staging = stagingDir(d.id)
        val final = finalDir(d.id)
        if (probeFinal(d, final) == InstallState.Installed) {
            deleteDir(staging)
            sf.value = InstallState.Installed
            return Result.success(Unit)
        }
        return try {
            if (!staging.isDirectory && !staging.mkdirs()) throw IOException("Could not create $staging")
            // Anything else would ride into the final dir with the rename: a
            // partial of the previous files (#78) or of an older catalog entry.
            val wanted = d.files.flatMap { listOf(it.name, it.name + PART_SUFFIX) }.toSet()
            staging.listFiles().orEmpty().filter { it.name !in wanted }.forEach {
                Log.i(TAG, "Deleting ${d.id}.partial/${it.name}, not one of its files")
                if (!it.deleteRecursively()) throw IOException("Could not delete $it")
            }
            // A "verified" file of the wrong size can only be a catalog change; fetch it again.
            for (f in d.files) File(staging, f.name).takeIf { it.isFile && it.length() != f.sizeBytes }?.delete()
            requireFreeSpace(d, staging)

            val total = d.sizeBytes
            val step = maxOf(total / 100L, PROGRESS_MIN_BYTES)
            var done = bytesOnDisk(d, staging)
            var lastEmit = done
            for (file in d.files) {
                if (File(staging, file.name).isFile) continue // hashed before it was renamed
                sf.value = InstallState.Downloading(done, total, file.name)
                // A dropped connection (a cell handover, a VPN reconnecting) is
                // retried here a few times before the user sees it; any bytes
                // gained since the last drop start the count again.
                var retries = 0
                while (true) {
                    val before = done
                    try {
                        downloadFile(d, file, staging) { delta ->
                            done += delta
                            if (done - lastEmit >= step || lastEmit - done >= step) {
                                lastEmit = done
                                sf.value = InstallState.Downloading(done, total, file.name)
                            }
                        }
                        break
                    } catch (e: InstallException) {
                        if (!e.isRetryable()) throw e
                        if (done > before) retries = 0
                        if (retries == NETWORK_RETRY_DELAYS_MS.size) throw e
                        Log.i(TAG, "Connection dropped for ${d.id}/${file.name} (${e.cause}); retry ${retries + 1}")
                        networkRetryDelay(NETWORK_RETRY_DELAYS_MS[retries++])
                    }
                }
            }
            sf.value = InstallState.Downloading(total, total, d.files.last().name)

            writeRecord(d, staging)
            if (final.exists()) {
                release.release(d.id)
                if (!final.deleteRecursively()) throw IOException("Could not delete the old $final")
            }
            if (!staging.renameTo(final)) throw IOException("Could not rename $staging to $final")
            sf.value = InstallState.Installed
            Log.i(TAG, "Installed ${d.id}")
            Result.success(Unit)
        } catch (t: Throwable) {
            if (t is CancellationException || !coroutineContext.isActive) {
                Log.i(TAG, "Download of ${d.id} paused")
                sf.value = InstallState.Paused(bytesOnDisk(d, staging), d.sizeBytes)
                throw t as? CancellationException ?: CancellationException("Download cancelled").apply { initCause(t) }
            }
            Log.w(TAG, "Install of ${d.id} failed", t)
            // ENOSPC anywhere in the cause chain wins over how the step was labelled.
            val failure = if (t.isOutOfSpace()) InstallFailure.NO_SPACE else (t as? InstallException)?.failure
            // A full disk would fail every resume while the partial holds the space hostage.
            if (failure == InstallFailure.NO_SPACE) deleteDir(staging)
            val reason = when {
                failure == InstallFailure.NO_SPACE -> NO_SPACE_MESSAGE
                t is InstallException -> t.message ?: GENERIC_FAILURE_MESSAGE
                else -> GENERIC_FAILURE_MESSAGE
            }
            sf.value = InstallState.Failed(reason, failure, bytesOnDisk(d, staging))
            val sameKind = t is InstallException && t.failure == failure
            Result.failure(if (failure == null || sameKind) t else InstallException(failure, reason, t))
        }
    }

    /**
     * Fetch [file] into `<name>.part` (resuming it), check its SHA-256 and
     * rename it to `<name>`. [onBytes] gets byte-count deltas (negative when a
     * partial is discarded because the server ignored the Range).
     */
    private suspend fun downloadFile(
        d: SttEngineDescriptor,
        file: ModelFile,
        staging: File,
        onBytes: (Long) -> Unit,
    ) {
        val part = File(staging, file.name + PART_SUFFIX)
        val digest = MessageDigest.getInstance("SHA-256")
        val have = if (part.isFile) part.length() else 0L
        if (have >= file.sizeBytes) {
            // Asking for `bytes=<size>-` would get HTTP 416. The hash decides.
            part.inputStream().use { hashInto(digest, it) }
        } else {
            val range = try {
                network { fetcher.openRange(file.url, have) }
            } catch (e: InstallException) {
                if ((e.cause as? HttpStatusException)?.code != HTTP_RANGE_NOT_SATISFIABLE) throw e
                // The server's file is shorter than our partial, so neither is this file.
                Log.w(TAG, "HTTP 416 resuming ${d.id}/${file.name} at $have bytes; deleting the partial")
                part.delete()
                throw InstallException(InstallFailure.CHECKSUM, CHECKSUM_MESSAGE, e)
            }
            range.stream.use { input ->
                // Checked before the .part is touched: a captive portal answers
                // 200 with its login page, which would truncate and overwrite it (#74).
                if (range.totalBytes != file.sizeBytes) {
                    Log.w(TAG, "Server states ${range.totalBytes} bytes for ${d.id}/${file.name}, expected ${file.sizeBytes}")
                    throw InstallException(InstallFailure.NETWORK, NETWORK_MESSAGE, IOException("Wrong file size"))
                }
                val append = when {
                    have > 0L && range.startedAtBytes == have -> true
                    range.startedAtBytes == 0L -> false
                    else -> throw InstallException(InstallFailure.NETWORK, NETWORK_MESSAGE)
                }
                if (append) {
                    part.inputStream().use { hashInto(digest, it) }
                    Log.d(TAG, "Resuming ${d.id}/${file.name} at $have bytes")
                } else if (have > 0L) {
                    Log.d(TAG, "Server ignored Range for ${d.id}/${file.name}; restarting it")
                    onBytes(-have)
                }
                closeOnCancel(input) {
                    FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = network { input.read(buf) }
                            if (n == -1) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            onBytes(n.toLong())
                        }
                    }
                }
            }
            // A body that ended early is a dropped connection, not a damaged
            // file: keep the bytes for a resume instead of hashing them.
            if (part.length() < file.sizeBytes) throw InstallException(InstallFailure.NETWORK, NETWORK_MESSAGE)
        }
        val actual = digest.digest().toHex()
        if (!actual.equals(file.sha256, ignoreCase = true)) {
            Log.w(TAG, "SHA-256 mismatch for ${d.id}/${file.name}: expected ${file.sha256}, got $actual")
            part.delete()
            throw InstallException(InstallFailure.CHECKSUM, CHECKSUM_MESSAGE)
        }
        if (!part.renameTo(File(staging, file.name))) throw IOException("Could not rename $part")
    }

    /**
     * Close [stream] as soon as the coroutine is cancelled, so a read blocked on
     * a stalled connection ends at once instead of after the 30 s read timeout.
     */
    private suspend fun <T> closeOnCancel(stream: InputStream, block: suspend () -> T): T = coroutineScope {
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                runCatching { stream.close() }
            }
        }
        try {
            block()
        } finally {
            watcher.cancel()
        }
    }

    private fun requireFreeSpace(d: SttEngineDescriptor, staging: File) {
        val needed = (d.sizeBytes - bytesOnDisk(d, staging)).coerceAtLeast(0L) + FREE_SPACE_MARGIN_BYTES
        val available = usableSpaceBytes(staging)
        if (available < needed) {
            Log.w(TAG, "Not enough space for ${d.id}: need $needed bytes, $available available")
            throw InstallException(InstallFailure.NO_SPACE, NO_SPACE_MESSAGE)
        }
    }

    // -- probing ---------------------------------------------------------------

    /**
     * Cheap disk probe: an Installed final dir wins; otherwise a partial
     * download is Paused; otherwise a final dir that exists is Corrupt.
     */
    private fun probe(d: SttEngineDescriptor): InstallState {
        val finalState = probeFinal(d, finalDir(d.id))
        if (finalState == InstallState.Installed) return finalState
        val staged = bytesOnDisk(d, stagingDir(d.id))
        if (staged > 0L) return InstallState.Paused(staged, d.sizeBytes)
        return finalState ?: InstallState.NotInstalled
    }

    /**
     * Installed / Corrupt for an existing final dir, judged against the file
     * set its record names (invariant 5); null when there is no final dir.
     */
    private fun probeFinal(d: SttEngineDescriptor, dir: File): InstallState? {
        if (!dir.isDirectory) return null
        val files = recordedSet(d, dir) ?: return InstallState.Corrupt(CorruptProblem.OUTDATED)
        return checkFiles(files, dir)
    }

    /**
     * Which of [d]'s file sets [dir]'s install record names exactly: its
     * current files, or its previous files (#78). Null for neither: no record,
     * a garbled one, or one from an older catalog.
     */
    private fun recordedSet(d: SttEngineDescriptor, dir: File): List<ModelFile>? {
        val record = readRecord(dir) ?: return null
        return when {
            record == expectedRecord(d.files) -> d.files
            d.previousFiles.isNotEmpty() && record == expectedRecord(d.previousFiles) -> d.previousFiles
            else -> null
        }
    }

    /** Installed when each of [files] is in [dir] at its size, else Corrupt. */
    private fun checkFiles(files: List<ModelFile>, dir: File): InstallState {
        for (f in files) {
            val file = File(dir, f.name)
            if (!file.isFile) return InstallState.Corrupt(CorruptProblem.MISSING, f.name)
            if (file.length() != f.sizeBytes) return InstallState.Corrupt(CorruptProblem.WRONG_SIZE, f.name)
        }
        return InstallState.Installed
    }

    /** Re-hash the installed files: the set the record names, previous files included. */
    private suspend fun deepCheck(d: SttEngineDescriptor): InstallState {
        val files = recordedSet(d, finalDir(d.id)) ?: return InstallState.Corrupt(CorruptProblem.OUTDATED)
        for (f in files) {
            val digest = MessageDigest.getInstance("SHA-256")
            File(finalDir(d.id), f.name).inputStream().use { hashInto(digest, it) }
            if (!digest.digest().toHex().equals(f.sha256, ignoreCase = true)) {
                Log.w(TAG, "Deep verify: ${d.id}/${f.name} is damaged")
                return InstallState.Corrupt(CorruptProblem.DAMAGED, f.name)
            }
        }
        return InstallState.Installed
    }

    /** Bytes of [d]'s files in staging: verified files plus `.part` files. */
    private fun bytesOnDisk(d: SttEngineDescriptor, staging: File): Long = d.files.sumOf { f ->
        val done = File(staging, f.name)
        val part = File(staging, f.name + PART_SUFFIX)
        when {
            done.isFile -> done.length()
            part.isFile -> part.length()
            else -> 0L
        }
    }

    // -- install record (.install.json) ----------------------------------------
    // A flat {"files": {"<name>": "<sha256>"}} map, written by hand: org.json is
    // a stub in JVM unit tests and names are catalog-controlled [A-Za-z0-9._-].

    private fun expectedRecord(files: List<ModelFile>): Map<String, String> =
        files.associate { it.name to it.sha256.lowercase() }

    private fun writeRecord(d: SttEngineDescriptor, dir: File) {
        val entries = expectedRecord(d.files).entries.joinToString(",\n") { (name, sha) -> "    \"$name\": \"$sha\"" }
        File(dir, RECORD_FILE).writeText("{\n  \"files\": {\n$entries\n  }\n}\n")
    }

    private fun readRecord(dir: File): Map<String, String>? {
        val file = File(dir, RECORD_FILE)
        if (!file.isFile) return null
        return runCatching {
            RECORD_ENTRY.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2].lowercase() }
        }.getOrNull()
    }

    // -- import / export -------------------------------------------------------

    private suspend fun runImport(d: SttEngineDescriptor, zipInput: InputStream): Result<Unit> {
        val sf = stateFlow(d.id)
        val staging = stagingDir(d.id)
        val final = finalDir(d.id)
        return try {
            if (!staging.isDirectory && !staging.mkdirs()) throw IOException("Could not create $staging")
            staging.listFiles().orEmpty().forEach { it.deleteRecursively() }
            requireFreeSpace(d, staging)

            val wantedFiles = d.files.associateBy { it.name }
            val extractedFiles = mutableSetOf<String>()

            ZipInputStream(zipInput).use { zis ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }
                    val entryName = File(entry.name).name
                    val modelFile = wantedFiles[entryName]
                    if (modelFile == null) {
                        zis.closeEntry()
                        continue
                    }

                    val targetFile = File(staging, modelFile.name)
                    if (!targetFile.canonicalPath.startsWith(staging.canonicalPath)) {
                        zis.closeEntry()
                        continue
                    }

                    val part = File(staging, modelFile.name + PART_SUFFIX)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var bytesWritten = 0L
                    FileOutputStream(part).use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = zis.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            bytesWritten += n
                        }
                    }
                    zis.closeEntry()

                    if (bytesWritten != modelFile.sizeBytes) {
                        part.delete()
                        Log.w(TAG, "Imported file size mismatch for ${d.id}/${modelFile.name}: expected ${modelFile.sizeBytes}, got $bytesWritten")
                        throw InstallException(InstallFailure.CHECKSUM, CHECKSUM_MESSAGE)
                    }

                    val actualSha = digest.digest().toHex()
                    if (!actualSha.equals(modelFile.sha256, ignoreCase = true)) {
                        part.delete()
                        Log.w(TAG, "Imported file SHA mismatch for ${d.id}/${modelFile.name}: expected ${modelFile.sha256}, got $actualSha")
                        throw InstallException(InstallFailure.CHECKSUM, CHECKSUM_MESSAGE)
                    }

                    if (!part.renameTo(targetFile)) {
                        throw IOException("Could not rename $part to $targetFile")
                    }
                    extractedFiles.add(modelFile.name)
                }
            }

            val missing = d.files.filter { it.name !in extractedFiles }
            if (missing.isNotEmpty()) {
                Log.w(TAG, "Archive missing files for ${d.id}: ${missing.map { it.name }}")
                deleteDir(staging)
                throw InstallException(InstallFailure.CHECKSUM, "Archive is missing required files for ${d.displayName}.")
            }

            writeRecord(d, staging)
            if (final.exists()) {
                release.release(d.id)
                if (!final.deleteRecursively()) throw IOException("Could not delete old $final")
            }
            if (!staging.renameTo(final)) throw IOException("Could not rename $staging to $final")

            sf.value = InstallState.Installed
            Log.i(TAG, "Successfully imported ${d.id} from archive")
            Result.success(Unit)
        } catch (t: Throwable) {
            deleteDir(staging)
            if (t is CancellationException || !coroutineContext.isActive) {
                sf.value = probe(d)
                throw t as? CancellationException ?: CancellationException("Import cancelled").apply { initCause(t) }
            }
            Log.w(TAG, "Import of ${d.id} failed", t)
            val failure = if (t.isOutOfSpace()) InstallFailure.NO_SPACE else (t as? InstallException)?.failure
            val reason = when {
                failure == InstallFailure.NO_SPACE -> NO_SPACE_MESSAGE
                t is InstallException -> t.message ?: GENERIC_FAILURE_MESSAGE
                else -> GENERIC_FAILURE_MESSAGE
            }
            sf.value = InstallState.Failed(reason, failure, 0L)
            val sameKind = t is InstallException && t.failure == failure
            Result.failure(if (failure == null || sameKind) t else InstallException(failure, reason, t))
        }
    }

    private fun runExport(d: SttEngineDescriptor, zipOutput: OutputStream): Result<Unit> {
        val final = finalDir(d.id)
        if (probeFinal(d, final) != InstallState.Installed) {
            return Result.failure(IOException("${d.displayName} is not installed."))
        }
        return try {
            ZipOutputStream(zipOutput.buffered()).use { zos ->
                val buf = ByteArray(BUFFER_SIZE)
                for (file in d.files) {
                    val src = File(final, file.name)
                    if (!src.isFile) throw IOException("Missing file ${file.name}")
                    val entry = ZipEntry(file.name).apply {
                        size = src.length()
                        time = src.lastModified()
                    }
                    zos.putNextEntry(entry)
                    src.inputStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            zos.write(buf, 0, n)
                        }
                    }
                    zos.closeEntry()
                }
            }
            Log.i(TAG, "Successfully exported ${d.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to export ${d.id}", e)
            Result.failure(e)
        }
    }

    // -- helpers ---------------------------------------------------------------

    private fun stateFlow(engineId: String): MutableStateFlow<InstallState> = synchronized(states) {
        states.getOrPut(engineId) {
            MutableStateFlow(descriptor(engineId)?.let { probe(it) } ?: InstallState.NotInstalled)
        }
    }

    private fun mutex(engineId: String): Mutex = synchronized(mutexes) { mutexes.getOrPut(engineId) { Mutex() } }

    private fun modelsRoot(): File = File(filesDir.get(), MODELS_DIR)
    private fun finalDir(engineId: String): File = File(modelsRoot(), engineId)
    private fun stagingDir(engineId: String): File = File(modelsRoot(), engineId + STAGING_SUFFIX)

    /** Unknown ids are rejected before they can become a path under `models/`. */
    private fun unknownEngine(engineId: String): Result<Unit> =
        Result.failure(IllegalArgumentException("Unknown engine: $engineId"))

    private fun deleteDir(dir: File): Boolean = !dir.exists() || dir.deleteRecursively()

    private suspend fun hashInto(digest: MessageDigest, input: InputStream) {
        val buf = ByteArray(BUFFER_SIZE)
        while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buf)
            if (n == -1) return
            digest.update(buf, 0, n)
        }
    }

    /**
     * Whether retrying can help: a dropped connection, a wrong-size answer, or
     * an HTTP 408/429/5xx. Not a full disk, nor 401/403/416 and other 4xx (#74).
     */
    private fun InstallException.isRetryable(): Boolean =
        failure == InstallFailure.NETWORK && !isOutOfSpace() && (cause as? HttpStatusException)?.retryable != false

    /** Run a network step; a plain [IOException] from it becomes [InstallFailure.NETWORK]. */
    private inline fun <T> network(block: () -> T): T = try {
        block()
    } catch (e: InstallException) {
        throw e
    } catch (e: IOException) {
        throw InstallException(InstallFailure.NETWORK, NETWORK_MESSAGE, e)
    }

    internal companion object {
        private const val TAG = "ModelInstaller"
        const val MODELS_DIR = "models"
        const val STAGING_SUFFIX = ".partial"
        const val PART_SUFFIX = ".part"
        const val RECORD_FILE = ".install.json"
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_MIN_BYTES = 256L * 1024L
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416

        /**
         * Pauses before each automatic retry of a dropped download, about 40 s
         * in all: long enough for a network handover or a VPN to reconnect.
         */
        val NETWORK_RETRY_DELAYS_MS = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L)

        /** Headroom left beyond the download so the app can still write its settings. */
        const val FREE_SPACE_MARGIN_BYTES = 50L * 1024L * 1024L

        private val RECORD_ENTRY = Regex(""""([A-Za-z0-9._-]+)"\s*:\s*"([0-9a-fA-F]{64})"""")
    }
}

/** True when this or a cause is the filesystem reporting a full disk. */
private fun Throwable.isOutOfSpace(): Boolean = generateSequence(this) { it.cause }.any { t ->
    val message = t.message ?: return@any false
    "ENOSPC" in message || "No space left on device" in message
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
