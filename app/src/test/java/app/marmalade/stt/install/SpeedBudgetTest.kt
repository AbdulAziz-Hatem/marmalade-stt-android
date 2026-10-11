package app.marmalade.stt.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The speed budget's rules (#24, #27), against a made-up reference table so
 * the tests don't depend on [SpeedBudget.REFERENCE]'s (provisional) values.
 */
class SpeedBudgetTest {

    private val ultra = SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_ULTRA)!!
    private val p110 = SttEngineCatalog.byId("parakeet-tdt-110m-en")!!
    private val turbo = SttEngineCatalog.byId("whisper-turbo")!!
    private val qwen = SttEngineCatalog.byId("qwen3-asr-0-6b")!!

    private val reference = mapOf(
        ultra.id to EngineSpeed(relativeCost = 1.0, peakRssMb = 1000),
        p110.id to EngineSpeed(relativeCost = 0.3, peakRssMb = 500),
        turbo.id to EngineSpeed(relativeCost = 4.0, peakRssMb = 3000),
    )

    @Test
    fun `the budget is the speed factor in Parakeet Ultra units, the reference phone's until measured, clamped`() {
        val unit = SpeedBudget.REFERENCE_BUDGET
        assertEquals(unit, SpeedBudget.budget(null), 0.0)
        assertEquals(unit, SpeedBudget.budget(1.0), 0.0)
        assertEquals(SpeedBudget.MIN_FACTOR * unit, SpeedBudget.budget(0.1), 0.0)
        assertEquals(SpeedBudget.MAX_FACTOR * unit, SpeedBudget.budget(10.0), 0.0)
        assertEquals(1.7 * unit, SpeedBudget.budget(1.7), 1e-9)
    }

    @Test
    fun `the reference phone's budget is what distil-small_en cost, Max's rule`() {
        // distil-small.en, the unit until #45, cost 1 ÷ 0.28 Parakeet Ultras (#25's host ratio).
        assertEquals(1 / 0.28, SpeedBudget.REFERENCE_BUDGET, 0.005)
    }

    @Test
    fun `over budget only beyond the tolerance band`() {
        val budget = 2.0
        val edge = budget * SpeedBudget.TOLERANCE
        fun costing(cost: Double) = mapOf(turbo.id to EngineSpeed(cost, 1))
        assertFalse(SpeedBudget.overBudget(turbo, budget, costing(edge)))
        assertTrue(SpeedBudget.overBudget(turbo, budget, costing(edge + 1e-9)))
        assertFalse(SpeedBudget.overBudget(turbo, budget, costing(budget)))
    }

    @Test
    fun `an engine without a measurement is never over budget and has no tier`() {
        assertFalse(SpeedBudget.overBudget(qwen, SpeedBudget.budget(SpeedBudget.MIN_FACTOR), reference))
        assertNull(SpeedBudget.speedTier(qwen, 1.0, reference))
    }

    @Test
    fun `speed tiers are the cost as a share of the budget`() {
        fun tier(cost: Double, budget: Double) =
            SpeedBudget.speedTier(turbo, budget, mapOf(turbo.id to EngineSpeed(cost, 1)))
        assertEquals(SpeedTier.FASTEST, tier(0.2, 1.0))
        assertEquals(SpeedTier.VERY_FAST, tier(0.21, 1.0))
        assertEquals(SpeedTier.VERY_FAST, tier(0.35, 1.0))
        assertEquals(SpeedTier.FAST, tier(0.36, 1.0))
        assertEquals(SpeedTier.FAST, tier(0.7, 1.0))
        assertEquals(SpeedTier.MODERATE, tier(1.0, 1.0))
        assertEquals(SpeedTier.MODERATE, tier(1.25, 1.0))
        assertEquals(SpeedTier.SLOW, tier(1.26, 1.0))
        // A phone twice as fast moves the same engine up.
        assertEquals(SpeedTier.FAST, tier(1.26, 2.0))
    }

    /** #80: five tiers, one per segment, as many as the quality meter has. */
    @Test
    fun `the five tiers fill one to five segments`() {
        assertEquals(listOf(5, 4, 3, 2, 1), SpeedTier.entries.map { it.meterFill })
    }

    /** #80: on the reference phone 110M is fastest and Ultra very fast. */
    @Test
    fun `on a Pixel 8a 110M is fastest and Ultra very fast`() {
        val pixel8a = SpeedBudget.budget(1.0)
        assertEquals(SpeedTier.FASTEST, SpeedBudget.speedTier(p110, pixel8a))
        assertEquals(SpeedTier.VERY_FAST, SpeedBudget.speedTier(ultra, pixel8a))
        assertEquals(SpeedTier.SLOW, SpeedBudget.speedTier(turbo, pixel8a))
    }

    @Test
    fun `the slow tier and over budget agree`() {
        for (cost in listOf(0.1, 0.5, 1.0, 1.25, 1.3, 3.0)) {
            val ref = mapOf(turbo.id to EngineSpeed(cost, 1))
            assertEquals(
                SpeedBudget.overBudget(turbo, 1.0, ref),
                SpeedBudget.speedTier(turbo, 1.0, ref) == SpeedTier.SLOW,
            )
        }
    }

    @Test
    fun `speed factor is the reference probe time over this phone's`() {
        val ref = SpeedBudget.PROBE_REFERENCE_MS
        assertEquals(1.0, SpeedBudget.speedFactor(ref), 1e-9)
        assertEquals(2.0, SpeedBudget.speedFactor(ref / 2), 0.02) // ref / 2 rounds down to whole ms
        assertEquals(0.5, SpeedBudget.speedFactor(ref * 2), 1e-9)
        // A zero time can't divide by zero.
        assertTrue(SpeedBudget.speedFactor(0).isFinite())
    }

    @Test
    fun `fewer threads slow Parakeet and Nemotron, not Whisper (the Pixel 8a sweep, #34)`() {
        fun penalty(family: EngineFamily, threads: Int) = SpeedBudget.threadPenalty(family, threads)
        for (family in EngineFamily.entries) {
            assertEquals(family.name, 1.0, penalty(family, SpeedBudget.REFERENCE_THREADS), 0.0)
            // More threads than the reference never make an engine cheaper: the phone gets at most 4.
            assertEquals(family.name, 1.0, penalty(family, 8), 0.0)
            // Below 2 is clamped to 2, as RecognizerThreads never gives fewer.
            assertEquals(family.name, penalty(family, 2), penalty(family, 1), 0.0)
        }
        assertEquals(1.45, penalty(EngineFamily.NEMO_TRANSDUCER, 2), 0.0)
        assertEquals(1.14, penalty(EngineFamily.NEMO_TRANSDUCER, 3), 0.0)
        assertEquals(1.37, penalty(EngineFamily.STREAMING_TRANSDUCER, 2), 0.0)
        assertEquals(1.0, penalty(EngineFamily.STREAMING_TRANSDUCER, 3), 0.0)
        assertEquals(1.0, penalty(EngineFamily.WHISPER, 2), 0.0)
    }

    @Test
    fun `costs at a phone's thread count scale each engine by its family, memory unchanged`() {
        val nemotron = SttEngineCatalog.byId(SttEngineCatalog.NEMOTRON_35_1120MS)!!
        val ref = mapOf(
            turbo.id to EngineSpeed(9.79, 3000),
            ultra.id to EngineSpeed(1.0, 2000),
            nemotron.id to EngineSpeed(4.29, 2000),
            "not-an-engine" to EngineSpeed(0.5, 10),
        )
        assertEquals(ref, SpeedBudget.costsAt(4, ref))
        val two = SpeedBudget.costsAt(2, ref)
        assertEquals(9.79, two.getValue(turbo.id).relativeCost, 1e-9)
        assertEquals(1.0 * 1.45, two.getValue(ultra.id).relativeCost, 1e-9)
        assertEquals(4.29 * 1.37, two.getValue(nemotron.id).relativeCost, 1e-9)
        assertEquals(2000, two.getValue(ultra.id).peakRssMb)
        // An id the catalog doesn't know keeps its cost.
        assertEquals(ref.getValue("not-an-engine"), two.getValue("not-an-engine"))
        // On a 2-thread phone at the Pixel's speed Nemotron (5.88) is over 3.57 x 1.25; at 4 threads it isn't.
        val pixel = SpeedBudget.budget(1.0)
        assertTrue(SpeedBudget.overBudget(nemotron, pixel, two))
        assertFalse(SpeedBudget.overBudget(nemotron, pixel, SpeedBudget.costsAt(4, ref)))
    }

    @Test
    fun `an engine that fell behind on this phone is over budget and slow, whatever its cost`() {
        val slow = setOf(p110.id)
        val fastest = SpeedBudget.budget(SpeedBudget.MAX_FACTOR)
        assertTrue(SpeedBudget.overBudget(p110, fastest, reference, slow))
        assertEquals(SpeedTier.SLOW, SpeedBudget.speedTier(p110, fastest, reference, slow))
        // Even without a reference measurement.
        assertTrue(SpeedBudget.overBudget(qwen, 1.0, reference, setOf(qwen.id)))
        assertEquals(SpeedTier.SLOW, SpeedBudget.speedTier(qwen, 1.0, reference, setOf(qwen.id)))
        // Others are unaffected.
        assertFalse(SpeedBudget.overBudget(ultra, 1.0, reference, slow))
    }

    @Test
    fun `every catalog engine has a reference cost and Parakeet Ultra is the unit`() {
        assertEquals(SttEngineCatalog.engines.map { it.id }.toSet(), SpeedBudget.REFERENCE.keys)
        assertEquals(1.0, SpeedBudget.REFERENCE.getValue(SttEngineCatalog.PARAKEET_ULTRA).relativeCost, 0.0)
    }

    @Test
    fun `an engine runs lean only on phones it doesn't fit with the arena, at its lean memory and cost`() {
        val gib = 1L shl 30
        val withLean = reference + (ultra.id to EngineSpeed(1.0, 1500, LeanMode(peakRssMb = 1000, costFactor = 1.3)))
        // 4 GiB: 1638 MB allowed, so the arena fits: unchanged, lean mode kept for later.
        assertFalse(SpeedBudget.runsLean(ultra.id, 4 * gib, withLean))
        assertEquals(withLean, SpeedBudget.forPhone(4 * gib, withLean))
        // 3 GiB: 1228 MB, only lean fits: its memory, 1.3x its cost.
        assertTrue(SpeedBudget.runsLean(ultra.id, 3 * gib, withLean))
        val phone = SpeedBudget.forPhone(3 * gib, withLean)
        assertEquals(EngineSpeed(1.3, 1000), phone.getValue(ultra.id))
        assertEquals(withLean.getValue(p110.id), phone.getValue(p110.id))
        // Applying it again changes nothing, and neither does an unknown RAM.
        assertEquals(phone, SpeedBudget.forPhone(3 * gib, phone))
        assertEquals(withLean, SpeedBudget.forPhone(null, withLean))
        // Engines without a lean mode never run lean.
        assertFalse(SpeedBudget.runsLean(turbo.id, 1 * gib, withLean))
        // The thread penalty applies on top, and keeps the lean mode.
        val costs = SpeedBudget.costsAt(2, withLean)
        assertEquals(withLean.getValue(ultra.id).lean, costs.getValue(ultra.id).lean)
    }

    @Test
    fun `every lean mode in the reference is leaner and slower than the engine with its arena`() {
        for ((id, speed) in SpeedBudget.REFERENCE) {
            val lean = speed.lean ?: continue
            assertTrue(id, lean.peakRssMb < speed.peakRssMb)
            assertTrue(id, lean.costFactor >= 1.0)
        }
    }
}
