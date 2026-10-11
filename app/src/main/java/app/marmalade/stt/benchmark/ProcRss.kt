package app.marmalade.stt.benchmark

import java.io.File
import java.io.IOException

/**
 * This process's resident set size, from the `VmRSS:` line of
 * `/proc/self/status` (Linux reports it in kB, meaning KiB). Native model
 * memory (ONNX Runtime) counts here, which the Java heap numbers miss. The
 * benchmark samples it while an engine is loaded ([BenchmarkRunner]).
 */
object ProcRss {

    private val status = File("/proc/self/status")

    /** RSS in bytes, or null when /proc can't be read or has no VmRSS line. */
    fun read(): Long? = try {
        status.useLines { lines -> lines.firstOrNull { it.startsWith("VmRSS:") } }?.let(::parseVmRssLine)
    } catch (e: IOException) {
        null
    }

    /** "VmRSS:\t  123456 kB" → 123456 × 1024 bytes; null for anything else. */
    internal fun parseVmRssLine(line: String): Long? {
        val parts = line.removePrefix("VmRSS:").trim().split(Regex("\\s+"))
        if (parts.size != 2 || !parts[1].equals("kB", ignoreCase = true)) return null
        return parts[0].toLongOrNull()?.times(1024L)
    }
}
