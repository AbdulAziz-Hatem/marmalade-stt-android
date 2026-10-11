package app.marmalade.stt.ui.screen

import app.marmalade.stt.install.SpeedTier
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.install.SttEngineCatalog.NEMOTRON_35_1120MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineFactsTest {

    private fun engine(id: String) = checkNotNull(SttEngineCatalog.byId(id))

    private val parakeet = SttEngineCatalog.default
    private val granite = engine("granite-speech-5-470m") // a developer engine
    private val nemotron = engine(NEMOTRON_35_1120MS)

    @Test
    fun `facts come in the row's order`() {
        assertEquals(
            listOf(EngineFact.Recommended, EngineFact.InUse, EngineFact.MayBeSlow, EngineFact.Developer),
            engineFacts(granite, recommendedId = granite.id, showDeveloper = false, inUse = true, tier = SpeedTier.SLOW),
        )
        assertEquals(
            listOf(EngineFact.Recommended, EngineFact.Streaming(stepMs = 1120)),
            engineFacts(nemotron, recommendedId = nemotron.id, showDeveloper = false, inUse = false, tier = SpeedTier.FAST),
        )
    }

    @Test
    fun `a plain engine has no facts, and an unmeasured one isn't slow`() {
        assertEquals(emptyList<EngineFact>(), engineFacts(parakeet, recommendedId = null, showDeveloper = false, inUse = false, tier = null))
        assertEquals(
            listOf(EngineFact.Developer),
            engineFacts(granite, recommendedId = null, showDeveloper = false, inUse = false, tier = null),
        )
    }

    @Test
    fun `Nemotron 3_5 shows its chunk, as a plain fact`() {
        assertEquals(1120, streamingStepMs(nemotron))
        assertNull(streamingStepMs(parakeet))
        assertNull(streamingStepMs(granite))
        assertEquals(
            listOf(EngineFact.Streaming(stepMs = 1120)),
            engineFacts(nemotron, recommendedId = null, showDeveloper = false, inUse = false, tier = SpeedTier.MODERATE),
        )
    }
}
