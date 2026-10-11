package app.marmalade.stt.speech

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.SttEngineCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The tuning [SpeechEngineManager] loads the active engine with (#47): ONNX
 * Runtime's arena off only on a phone the engine fits only that way, from the
 * live RAM ([SpeechEngineManager.tuningFor], which the manager's choice and
 * the A/B benchmark both call).
 */
class SpeechEngineManagerTuningTest {

    private val gib = 1L shl 30
    private val ultra = SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_ULTRA)!!
    private val prefs = SpeechPrefs(selectedEngineId = ultra.id)

    private fun arena(ram: Long?, prefs: SpeechPrefs = this.prefs) =
        SpeechEngineManager.tuningFor(prefs, prefs.activeEngine, ram).arena

    @Test
    fun `a 4 GB phone loads Parakeet Ultra without the arena, a 6 GB one with it`() {
        assertEquals(ultra, prefs.activeEngine)
        assertEquals(false, arena((3.6 * gib).toLong()))
        assertNull(arena((5.5 * gib).toLong()))
        assertNull(arena(null)) // RAM unreadable: ORT's default
    }

    @Test
    fun `engines without a lean mode keep the arena however small the phone`() {
        val turbo = SpeechPrefs(selectedEngineId = "whisper-turbo")
        assertNull(arena(gib, turbo))
    }

    @Test
    fun `the rest of a developer tuning is kept`() {
        val noSpin = prefs.copy(developerEngines = true, ortTuningId = OrtTuning(spinning = false).id)
        assertEquals(
            OrtTuning(spinning = false, arena = false),
            SpeechEngineManager.tuningFor(noSpin, noSpin.activeEngine, (3.6 * gib).toLong()),
        )
    }
}
