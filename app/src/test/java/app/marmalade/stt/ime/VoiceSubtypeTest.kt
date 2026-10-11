package app.marmalade.stt.ime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the voice keyboard's subtype (#61): keyboards' mic keys find voice
 * keyboards by the voice mode, and overridesImplicitlyEnabledSubtype keeps
 * the en_US subtype enabled on phones without English, where Android would
 * otherwise leave it off and the mic keys would skip Marmalade
 * (docs/RESEARCH-keyboards-2026-10.md §3.3).
 */
class VoiceSubtypeTest {

    private val xml = File("src/main/res/xml/voice_input_method.xml").readText()
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    @Test
    fun `one voice subtype, implicitly enabled whatever the phone's languages`() {
        val subtypes = Regex("<subtype\\b[^>]*>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.value }.toList()
        assertEquals(1, subtypes.size)
        val subtype = subtypes.single()
        assertTrue(subtype, "android:imeSubtypeMode=\"voice\"" in subtype)
        assertTrue(subtype, "android:overridesImplicitlyEnabledSubtype=\"true\"" in subtype)
        assertTrue(subtype, "android:isAuxiliary=\"true\"" in subtype)
    }
}
