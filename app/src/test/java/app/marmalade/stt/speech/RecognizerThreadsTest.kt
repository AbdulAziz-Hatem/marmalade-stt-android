package app.marmalade.stt.speech

import app.marmalade.stt.speech.RecognizerThreads.Cluster
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecognizerThreadsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * A fake `/sys/devices/system/cpu`: `cpuN/cpu_capacity` from [capacities]
     * (null: no file) and one `cpufreq/policyN` per entry of [policies]
     * (top frequency to its cores).
     */
    private fun sysfs(capacities: List<Long?>, policies: List<Pair<Long, List<Int>>>): File {
        val root = File(tmp.newFolder(), "cpu")
        capacities.forEachIndexed { cpu, capacity ->
            val dir = File(root, "cpu$cpu").apply { mkdirs() }
            capacity?.let { File(dir, "cpu_capacity").writeText("$it\n") }
        }
        policies.forEach { (freq, cpus) ->
            val dir = File(root, "cpufreq/policy${cpus.first()}").apply { mkdirs() }
            File(dir, "cpuinfo_max_freq").writeText("$freq\n")
            File(dir, "related_cpus").writeText(cpus.joinToString(" ", postfix = "\n"))
        }
        return root
    }

    /** Clusters of [sizes] cores at the given speeds, numbered from 0 in order. */
    private fun clusters(vararg sizes: Pair<Long, Int>): List<Cluster> {
        var next = 0
        return sizes.map { (speed, n) -> Cluster(speed, List(n) { next++ }) }
    }

    private fun threads(vararg sizes: Pair<Long, Int>, cpus: Int = 8) =
        RecognizerThreads.forClusters(clusters(*sizes), cpus)

    @Test
    fun `the Pixel 8a gets 4 of its 5 performance cores, its measured best`() {
        // Tensor G3: 1 Cortex-X3, 4 A715, 4 A510. #34: 4 beat 3 and 5-8 for Parakeet v3.
        assertEquals(4, threads(2_910_000L to 1, 2_370_000L to 4, 1_700_000L to 4, cpus = 9))
    }

    @Test
    fun `the efficiency cluster doesn't count`() {
        assertEquals(4, threads(2_400_000L to 4, 1_800_000L to 4))
        assertEquals(2, threads(2_200_000L to 2, 1_800_000L to 6))
    }

    @Test
    fun `never more than 4, even with more performance cores`() {
        // 5+ threads was 1.7-1.8x slower than 4 on the Pixel 8a (#34).
        assertEquals(4, threads(3_000_000L to 1, 2_800_000L to 5, 2_000_000L to 2))
        assertEquals(4, threads(2_400_000L to 6, 1_800_000L to 2))
    }

    @Test
    fun `never fewer than 2`() {
        assertEquals(2, threads(2_800_000L to 1, 1_800_000L to 7))
        assertEquals(2, threads(1_500_000L to 2, cpus = 2))
    }

    @Test
    fun `a single cluster leaves one core free`() {
        assertEquals(3, threads(2_000_000L to 4, cpus = 4))
    }

    @Test
    fun `unreadable sysfs falls back to half the cores`() {
        assertEquals(4, threads(cpus = 8))
        assertEquals(3, threads(cpus = 6))
    }

    @Test
    fun `cores group into clusters by capacity, fastest first`() {
        // The Pixel 8a's layout: A510s on 0-3, A715s on 4-7, the X3 on 8 (capacities illustrative).
        val speeds = (0..3).associateWith { 250L } + (4..7).associateWith { 620L } + mapOf(8 to 1024L)
        assertEquals(
            listOf(Cluster(1024, listOf(8)), Cluster(620, listOf(4, 5, 6, 7)), Cluster(250, listOf(0, 1, 2, 3))),
            RecognizerThreads.clustersOf(speeds),
        )
    }

    @Test
    fun `fast cores are every cluster but the slowest`() {
        val pixel = RecognizerThreads.clustersOf((0..3).associateWith { 250L } + (4..7).associateWith { 620L } + mapOf(8 to 1024L))
        assertEquals(listOf(4, 5, 6, 7, 8), RecognizerThreads.fastCores(pixel))
        // Two big cores of different capacity (same frequency policy on some Snapdragons) both count.
        val mixed = RecognizerThreads.clustersOf(mapOf(0 to 300L, 1 to 300L, 2 to 700L, 3 to 750L, 4 to 1024L))
        assertEquals(listOf(2, 3, 4), RecognizerThreads.fastCores(mixed))
    }

    @Test
    fun `nothing to pin to on one cluster or none`() {
        assertEquals(emptyList<Int>(), RecognizerThreads.fastCores(clusters(2_000_000L to 4)))
        assertEquals(emptyList<Int>(), RecognizerThreads.fastCores(emptyList()))
    }

    @Test
    fun `sysfs capacities that differ make the clusters`() {
        // The Pixel 8a: capacities split the X3 from the A715s, which share no policy with it.
        val root = sysfs(
            capacities = List(4) { 160L } + List(4) { 640L } + 1024L,
            policies = listOf(1_704_000L to (0..3).toList(), 2_367_000L to (4..7).toList(), 2_914_000L to listOf(8)),
        )
        val clusters = RecognizerThreads.readClusters(root)
        assertEquals(listOf(Cluster(1024, listOf(8)), Cluster(640, (4..7).toList()), Cluster(160, (0..3).toList())), clusters)
        assertEquals(4, RecognizerThreads.forClusters(clusters, 9))
    }

    @Test
    fun `equal sysfs capacities fall back to the policies' frequencies`() {
        // No capacity-dmips-mhz in the device tree: 1024 everywhere on a 2+6 phone.
        val root = sysfs(
            capacities = List(8) { 1024L },
            policies = listOf(1_800_000L to (0..5).toList(), 2_200_000L to listOf(6, 7)),
        )
        val clusters = RecognizerThreads.readClusters(root)
        assertEquals(listOf(Cluster(2_200_000, listOf(6, 7)), Cluster(1_800_000, (0..5).toList())), clusters)
        assertEquals(listOf(6, 7), RecognizerThreads.fastCores(clusters))
        assertEquals(2, RecognizerThreads.forClusters(clusters, 8))
    }

    @Test
    fun `missing sysfs capacities fall back to the policies' frequencies`() {
        val root = sysfs(
            capacities = List(3) { 1024L } + null,
            policies = listOf(1_800_000L to listOf(0, 1), 2_400_000L to listOf(2, 3)),
        )
        assertEquals(
            listOf(Cluster(2_400_000, listOf(2, 3)), Cluster(1_800_000, listOf(0, 1))),
            RecognizerThreads.readClusters(root),
        )
    }

    @Test
    fun `equal sysfs capacities with no readable policy are one cluster`() {
        val root = sysfs(capacities = List(4) { 1024L }, policies = emptyList())
        val clusters = RecognizerThreads.readClusters(root)
        assertEquals(listOf(Cluster(1024, listOf(0, 1, 2, 3))), clusters)
        assertEquals(3, RecognizerThreads.forClusters(clusters, 4))
    }

    @Test
    fun `unreadable sysfs reads as no clusters`() {
        assertEquals(emptyList<Cluster>(), RecognizerThreads.readClusters(sysfs(List(4) { null }, emptyList())))
        assertEquals(emptyList<Cluster>(), RecognizerThreads.readClusters(File(tmp.root, "missing")))
        // A policy without its frequency: no half-read layout.
        val root = sysfs(List(2) { null }, listOf(1_800_000L to listOf(0), 2_400_000L to listOf(1)))
        File(root, "cpufreq/policy1/cpuinfo_max_freq").delete()
        assertEquals(emptyList<Cluster>(), RecognizerThreads.readClusters(root))
    }
}
