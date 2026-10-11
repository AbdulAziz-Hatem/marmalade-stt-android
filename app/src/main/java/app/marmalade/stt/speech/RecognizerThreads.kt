package app.marmalade.stt.speech

import android.util.Log
import java.io.File

/**
 * How many ONNX Runtime threads each recognizer gets: the device's
 * performance cores (every cluster but the slowest), between [MIN] and [MAX].
 * Also which cores those are ([fastCores]), for pinning (#31, [OrtTuning]).
 *
 * Phones mix a prime core, big cores and efficiency cores (Pixel 8a: 1
 * Cortex-X3, 4 A715, 4 A510). ORT's matmuls speed up across the prime and big
 * cores, but a thread on an efficiency core is 2-4x slower and the others wait
 * for it. The #34 sweep on the Pixel 8a (docs/benchmarks/2026-09-pixel8a-threads.md)
 * found 4 the fastest count for Parakeet v3 (1.14x faster than 3, 1.4x than
 * 2) and 5 or more 1.7-1.8x slower than 4, although the phone has 5
 * performance cores: ORT's pool plus the calling thread, the audio pipeline
 * and the system don't all fit on them. Hence [MAX] = 4, and one count for
 * every engine: distil-small.en is flat from 2 to 8 threads and Nemotron
 * streaming is about the same at 3 and 4.
 *
 * Clusters come from sysfs with no per-phone table (#31): each core's
 * `cpu_capacity` (the scheduler's relative capacity on big.LITTLE ARM), else
 * its cpufreq policy's top frequency; cores with the same value form a
 * cluster ([readClusters]).
 * Ported from marmalade-tts-android's `CpuClusterDetector` (same authors,
 * MIT). The count is the same for every engine on a device, so the A/B
 * benchmark still compares models, not thread counts.
 */
internal object RecognizerThreads {
    const val MIN = 2

    /** The Pixel 8a's measured best (#34); 5+ threads was 1.7-1.8x slower for Parakeet. */
    const val MAX = 4

    private const val TAG = "RecognizerThreads"
    private const val CPU_ROOT = "/sys/devices/system/cpu"
    private val CPU_DIR = Regex("cpu\\d+")

    /**
     * One CPU cluster: cores of equal [speed] (`cpu_capacity`, or top
     * frequency in kHz when the capacities don't tell the cores apart) and
     * their ids.
     */
    data class Cluster(val speed: Long, val cpus: List<Int>)

    /** Read once per process: the clusters don't change at runtime. */
    private val clusters: List<Cluster> by lazy { readClusters(File(CPU_ROOT)) }

    val count: Int by lazy {
        val n = forClusters(clusters, Runtime.getRuntime().availableProcessors())
        Log.i(TAG, "$n recognizer threads, fast cores ${fastCores(clusters)} (clusters=$clusters)")
        n
    }

    /** The cores of every cluster but the slowest ([fastCores]); empty when sysfs is unreadable. */
    val fastCores: List<Int> by lazy { fastCores(clusters) }

    /**
     * The thread count for [clusters]: the cores of every cluster faster than
     * the slowest one; on a single-cluster device all cores but one. When
     * sysfs is unreadable ([clusters] empty), half of [availableProcessors].
     */
    fun forClusters(clusters: List<Cluster>, availableProcessors: Int): Int {
        val perfCores = when {
            clusters.isEmpty() -> (availableProcessors + 1) / 2
            else -> fastCores(clusters).size.takeIf { it > 0 } ?: (clusters.sumOf { it.cpus.size } - 1)
        }
        return perfCores.coerceIn(MIN, MAX)
    }

    /**
     * The cores of every cluster faster than the slowest, in id order; empty
     * for a single cluster (nothing to avoid) or no clusters.
     */
    fun fastCores(clusters: List<Cluster>): List<Int> {
        if (clusters.isEmpty()) return emptyList()
        val slowest = clusters.minOf { it.speed }
        return clusters.filter { it.speed > slowest }.flatMap { it.cpus }.sorted()
    }

    /**
     * Groups per-core speeds (core id to `cpu_capacity`, or to top frequency)
     * into clusters, fastest first.
     */
    fun clustersOf(speeds: Map<Int, Long>): List<Cluster> =
        speeds.entries.groupBy({ it.value }, { it.key })
            .map { (speed, cpus) -> Cluster(speed, cpus.sorted()) }
            .sortedByDescending { it.speed }

    /**
     * The clusters under [root] (`/sys/devices/system/cpu`), or empty if
     * sysfs isn't readable (some vendors restrict it):
     *
     * - each core's `cpu_capacity`, when every core has one and they differ;
     * - else the cpufreq policies (`cpufreq/policy*`: `cpuinfo_max_freq` and
     *   `related_cpus`). Kernels without `capacity-dmips-mhz` in the device
     *   tree report 1024 for every core even on big.LITTLE, which would make
     *   the efficiency cores count as fast;
     * - else one cluster of the equal capacities.
     */
    fun readClusters(root: File): List<Cluster> {
        fun read(file: File) = runCatching { file.readText().trim().toLong() }.getOrNull()
        val capacities = (root.listFiles { f -> f.isDirectory && CPU_DIR.matches(f.name) } ?: emptyArray())
            .associate { it.name.removePrefix("cpu").toInt() to read(File(it, "cpu_capacity")) }
            .takeIf { it.isNotEmpty() && it.values.all { c -> c != null } }
            ?.mapValues { it.value!! }
        if (capacities != null && capacities.values.distinct().size > 1) return clustersOf(capacities)
        return policyClusters(File(root, "cpufreq")).ifEmpty { capacities?.let { clustersOf(it) } ?: emptyList() }
    }

    /** One cluster per cpufreq policy (merged when two have the same top frequency), fastest first. */
    private fun policyClusters(cpufreq: File): List<Cluster> {
        val speeds = mutableMapOf<Int, Long>()
        for (policy in cpufreq.listFiles { f -> f.isDirectory && f.name.startsWith("policy") } ?: emptyArray()) {
            val maxFreq = runCatching { File(policy, "cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
            val cpus = runCatching { File(policy, "related_cpus").readText() }.getOrNull()
                ?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }
            if (maxFreq == null || cpus.isNullOrEmpty()) return emptyList()
            cpus.forEach { speeds[it] = maxFreq }
        }
        return clustersOf(speeds)
    }
}
