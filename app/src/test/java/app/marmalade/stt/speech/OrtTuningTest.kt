package app.marmalade.stt.speech

import app.marmalade.stt.install.EngineSpeed
import app.marmalade.stt.install.LeanMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [OrtTuning]'s stored id and the sherpa-onnx provider string it builds (#31). */
class OrtTuningTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `every preset and a core override round-trip through their id`() {
        for (tuning in OrtTuning.PRESETS) assertEquals(tuning, OrtTuning.parse(tuning.id))
        val pinned = OrtTuning(pin = true, cores = listOf(0, 1))
        assertEquals("cpu-pin:0.1", pinned.id)
        assertEquals(pinned, OrtTuning.parse("cpu-pin:0.1"))
    }

    @Test
    fun `parse rejects what isn't an id`() {
        for (id in listOf("gpu", "cpu-fast", "cpu-pin-pin", "cpu:0.1")) assertNull(id, OrtTuning.parse(id))
    }

    @Test
    fun `the default keeps ORT's memory arena and needs no config file`() {
        assertEquals("cpu", OrtTuning.DEFAULT.id)
        assertNull(OrtTuning.DEFAULT.arena)
        assertEquals("cpu", OrtTuning.DEFAULT.sherpaProvider(tmp.root))
        assertTrue(OrtTuning.PRESETS.all { it.arena == null })
    }

    @Test
    fun `the arena can be forced on or off, and off writes EnableCpuMemArena=0`() {
        val on = OrtTuning(arena = true)
        val off = OrtTuning(arena = false)
        assertEquals("cpu-arena", on.id)
        assertEquals("cpu-noarena", off.id)
        for (tuning in listOf(on, off)) assertEquals(tuning, OrtTuning.parse(tuning.id))
        assertNull(OrtTuning.parse("cpu-arena-noarena"))
        assertEquals("cpu", on.sherpaProvider(tmp.root))
        val file = File(off.sherpaProvider(tmp.root).removePrefix("cpu:"))
        assertEquals(listOf("EnableCpuMemArena=0"), file.readLines())
    }

    @Test
    fun `forPhone turns the arena off only where the engine fits the phone only without it`() {
        // Ultra-like: 1500 MB with the arena, 1000 without; 40 % of RAM is the limit.
        val reference = mapOf("big" to EngineSpeed(1.0, 1500, LeanMode(1000, 1.3)), "plain" to EngineSpeed(1.0, 1500))
        val gib = 1L shl 30
        fun arena(id: String, ram: Long?) = OrtTuning.DEFAULT.forPhone(id, ram, reference).arena
        assertNull(arena("big", 4 * gib)) // 1638 MB allowed: fits with the arena
        assertEquals(false, arena("big", 3 * gib)) // 1228 MB: only lean fits
        assertEquals(false, arena("big", 2 * gib)) // neither fits: the smaller one
        assertNull(arena("plain", 3 * gib)) // no lean mode measured
        assertNull(arena("big", null)) // RAM unknown
        assertNull(arena("unknown", 3 * gib))
        // A tuning that chose the arena itself keeps it.
        assertEquals(true, OrtTuning(arena = true).forPhone("big", 3 * gib, reference).arena)
        // The pick carries the rest of the tuning.
        assertEquals(OrtTuning(spinning = false, arena = false), OrtTuning(spinning = false).forPhone("big", 3 * gib, reference))
    }

    @Test
    fun `an existing config file with the same lines is reused, a stale one replaced in place`() {
        val off = OrtTuning(arena = false)
        val file = File(off.sherpaProvider(tmp.root).removePrefix("cpu:"))
        val written = file.lastModified()
        file.setLastModified(written - 10_000)
        assertEquals("cpu:${file.absolutePath}", off.sherpaProvider(tmp.root))
        assertEquals("reused, not rewritten", written - 10_000, file.lastModified())
        file.writeText("") // e.g. a write cut short
        off.sherpaProvider(tmp.root)
        assertEquals(listOf("EnableCpuMemArena=0"), file.readLines())
        // No temporary files left behind.
        assertEquals(listOf(file.name), tmp.root.list()!!.toList())
    }

    @Test
    fun `concurrent loads never see a missing or partial config file`() {
        val off = OrtTuning(arena = false, spinning = false)
        val expected = off.configLines
        val threads = List(8) {
            Thread {
                repeat(50) {
                    val path = off.sherpaProvider(tmp.root).removePrefix("cpu:")
                    assertEquals(expected, File(path).readLines())
                }
            }
        }
        val failures = mutableListOf<Throwable>()
        threads.forEach { t -> t.setUncaughtExceptionHandler { _, e -> synchronized(failures) { failures += e } } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    @Test
    fun `no spinning passes a session config file`() {
        val provider = OrtTuning(spinning = false).sherpaProvider(tmp.root)
        assertTrue(provider, provider.startsWith("cpu:"))
        val file = File(provider.removePrefix("cpu:"))
        assertEquals(
            listOf(
                "SessionConfig.session.intra_op.allow_spinning=0",
                "SessionConfig.session.inter_op.allow_spinning=0",
            ),
            file.readLines(),
        )
    }

    @Test
    fun `xnnpack asks sherpa-onnx for xnnpack, but isn't a Settings choice`() {
        val xnnpack = OrtTuning(xnnpack = true, spinning = false)
        assertTrue(OrtTuning.PRESETS.none { it.xnnpack })
        assertTrue(xnnpack.sherpaProvider(tmp.root).startsWith("xnnpack:"))
    }

    @Test
    fun `pinned cores follow pin and the override`() {
        assertNull(OrtTuning().pinnedCores)
        assertNull(OrtTuning(cores = listOf(2, 3)).pinnedCores)
        assertEquals(listOf(4, 5), OrtTuning(pin = true, cores = listOf(4, 5)).pinnedCores)
    }

    @Test
    fun `verbose adds ORT's log level to the config file`() {
        val tuning = OrtTuning(xnnpack = true, spinning = false, verbose = true)
        assertEquals("xnnpack-nospin-verbose", tuning.id)
        assertEquals(tuning, OrtTuning.parse(tuning.id))
        val file = File(tuning.sherpaProvider(tmp.root).substringAfter(':'))
        assertTrue(file.readLines().contains("LogSeverityLevel=0"))
    }

    @Test
    fun `an optimisation level writes GraphOptimizationLevel and round-trips`() {
        val tuning = OrtTuning(optLevel = 1)
        assertEquals("cpu-opt1", tuning.id)
        assertEquals(tuning, OrtTuning.parse(tuning.id))
        val file = File(tuning.sherpaProvider(tmp.root).substringAfter(':'))
        assertTrue(file.readLines().contains("GraphOptimizationLevel=1"))
        assertNull(OrtTuning.parse("cpu-opt0-opt1"))
        assertNull(OrtTuning.parse("cpu-opt9"))
    }

    @Test
    fun `noprepack turns ORT's weight prepacking off and round-trips`() {
        val tuning = OrtTuning(prepack = false)
        assertEquals("cpu-noprepack", tuning.id)
        assertEquals(tuning, OrtTuning.parse(tuning.id))
        val file = File(tuning.sherpaProvider(tmp.root).substringAfter(':'))
        assertTrue(file.readLines().contains("SessionConfig.session.disable_prepacking=1"))
    }

    @Test
    fun `an unwritable config dir falls back to the plain provider, and is counted`() {
        val blocker = tmp.newFile("not-a-dir")
        val before = OrtTuning.configFallbacks.get()
        assertEquals("cpu", OrtTuning(arena = false).sherpaProvider(File(blocker, "ort")))
        assertEquals("cpu", OrtTuning(spinning = false).sherpaProvider(File(blocker, "ort")))
        assertEquals(before + 2, OrtTuning.configFallbacks.get())
        // A tuning with nothing to write never falls back.
        assertEquals("cpu", OrtTuning.DEFAULT.sherpaProvider(File(blocker, "ort")))
        assertEquals(before + 2, OrtTuning.configFallbacks.get())
    }
}
