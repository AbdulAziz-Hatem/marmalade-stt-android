package app.marmalade.stt.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FutoVoiceInputTest {

    private val stable = "org.futo.inputmethod.latin"
    private val unstable = "org.futo.inputmethod.latin.unstable"
    private val playstore = "org.futo.inputmethod.latin.playstore"

    @Test
    fun `popup - 65 is yes, a cancel is no, anything else is unknown`() {
        assertEquals(FutoStatus.SELECTED, FutoVoiceInput.resultFromCode(FutoMode.POPUP, 65))
        assertEquals(FutoStatus.NOT_SELECTED, FutoVoiceInput.resultFromCode(FutoMode.POPUP, 0))
        assertEquals(FutoStatus.UNKNOWN, FutoVoiceInput.resultFromCode(FutoMode.POPUP, -1)) // RESULT_OK
        assertEquals(FutoStatus.UNKNOWN, FutoVoiceInput.resultFromCode(FutoMode.POPUP, 99))
    }

    @Test
    fun `check - 67 is selected, 68 is not, anything else is unknown`() {
        assertEquals(FutoStatus.SELECTED, FutoVoiceInput.resultFromCode(FutoMode.CHECK, 67))
        assertEquals(FutoStatus.NOT_SELECTED, FutoVoiceInput.resultFromCode(FutoMode.CHECK, 68))
        // A plain cancel from check isn't an answer.
        assertEquals(FutoStatus.UNKNOWN, FutoVoiceInput.resultFromCode(FutoMode.CHECK, 0))
        assertEquals(FutoStatus.UNKNOWN, FutoVoiceInput.resultFromCode(FutoMode.CHECK, -1))
    }

    @Test
    fun `result codes only count in their own mode`() {
        assertEquals(FutoStatus.UNKNOWN, FutoVoiceInput.resultFromCode(FutoMode.POPUP, 67))
        assertEquals(FutoStatus.UNKNOWN, FutoVoiceInput.resultFromCode(FutoMode.POPUP, 68))
        assertEquals(FutoStatus.UNKNOWN, FutoVoiceInput.resultFromCode(FutoMode.CHECK, 65))
    }

    @Test
    fun `prefers stable over unstable over playstore`() {
        assertEquals(stable, FutoVoiceInput.pickPackage(setOf(playstore, unstable, stable), null))
        assertEquals(unstable, FutoVoiceInput.pickPackage(setOf(playstore, unstable), null))
        assertEquals(playstore, FutoVoiceInput.pickPackage(setOf(playstore), null))
    }

    @Test
    fun `the FUTO package that is the current keyboard wins`() {
        assertEquals(unstable, FutoVoiceInput.pickPackage(setOf(stable, unstable), defaultImePackage = unstable))
        // Some other keyboard is current: fall back to the preference order.
        assertEquals(stable, FutoVoiceInput.pickPackage(setOf(stable, unstable), defaultImePackage = "com.example.keyboard"))
        // The current keyboard claims to be FUTO but isn't in the installed set: ignore it.
        assertEquals(stable, FutoVoiceInput.pickPackage(setOf(stable), defaultImePackage = playstore))
    }

    @Test
    fun `no FUTO installed means no package`() {
        assertNull(FutoVoiceInput.pickPackage(emptySet(), null))
        assertNull(FutoVoiceInput.pickPackage(setOf("com.example.keyboard"), "com.example.keyboard"))
    }

    @Test
    fun `mode extras are FUTO's words`() {
        assertEquals("popup", FutoMode.POPUP.extra)
        assertEquals("check", FutoMode.CHECK.extra)
    }
}
