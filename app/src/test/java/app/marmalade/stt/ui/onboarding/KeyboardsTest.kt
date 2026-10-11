package app.marmalade.stt.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardsTest {

    private val ours = EnabledKeyboard("app.marmalade.stt/app.marmalade.stt.ime.VoiceInputService", "Marmalade voice typing", listOf("voice"))
    private val futo = EnabledKeyboard("org.futo.inputmethod.latin/org.futo.inputmethod.latin.LatinIME", "FUTO Keyboard", listOf("keyboard", "keyboard"))
    private val heli = EnabledKeyboard("helium314.keyboard/helium314.keyboard.latin.LatinIME", "HeliBoard", listOf("keyboard"))
    private val thumb = EnabledKeyboard("com.dessalines.thumbkey/com.dessalines.thumbkey.IMEService", "Thumb-Key") // no subtypes
    private val googleVoice = EnabledKeyboard(
        "com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService",
        "Google voice typing",
        listOf("voice"),
    )

    @Test
    fun `known keyboards map to their kind, unknown ones to UNKNOWN`() {
        assertEquals(KeyboardKind.FUTO, Keyboards.kindOf("org.futo.inputmethod.latin.playstore"))
        assertEquals(KeyboardKind.FIRST_VOICE_KEYBOARD, Keyboards.kindOf("com.android.inputmethod.latin"))
        assertEquals(KeyboardKind.FIRST_VOICE_KEYBOARD, Keyboards.kindOf("helium314.keyboard"))
        assertEquals(KeyboardKind.FIRST_VOICE_KEYBOARD, Keyboards.kindOf("dev.patrickgold.florisboard"))
        assertEquals(KeyboardKind.FIRST_VOICE_KEYBOARD, Keyboards.kindOf("com.menny.android.anysoftkeyboard"))
        assertEquals(KeyboardKind.FIRST_VOICE_KEYBOARD_HIDDEN, Keyboards.kindOf("org.dslul.openboard.inputmethod.latin"))
        assertEquals(KeyboardKind.FIRST_VOICE_KEYBOARD_HIDDEN, Keyboards.kindOf("com.dessalines.thumbkey"))
        assertEquals(KeyboardKind.CHOOSER, Keyboards.kindOf("juloo.keyboard2"))
        assertEquals(KeyboardKind.FOSSIFY, Keyboards.kindOf("org.fossify.keyboard"))
        assertEquals(KeyboardKind.SWIFTKEY, Keyboards.kindOf("com.touchtype.swiftkey"))
        assertEquals(KeyboardKind.OWN_ENGINE, Keyboards.kindOf("com.google.android.inputmethod.latin"))
        assertEquals(KeyboardKind.OWN_ENGINE, Keyboards.kindOf("com.samsung.android.honeyboard"))
        assertEquals(KeyboardKind.GOOGLE_ONLY, Keyboards.kindOf("org.pocketworkstation.pckeyboard"))
        assertEquals(KeyboardKind.NO_MIC_KEY, Keyboards.kindOf("rkr.simplekeyboard.inputmethod"))
        assertEquals(KeyboardKind.UNKNOWN, Keyboards.kindOf("com.example.keyboard"))
    }

    @Test
    fun `debug and beta builds of a known keyboard count as it, lookalike packages don't`() {
        assertEquals(KeyboardKind.FIRST_VOICE_KEYBOARD, Keyboards.kindOf("dev.patrickgold.florisboard.beta"))
        assertEquals(KeyboardKind.FIRST_VOICE_KEYBOARD, Keyboards.kindOf("helium314.keyboard.debug"))
        assertEquals(KeyboardKind.SWIFTKEY, Keyboards.kindOf("com.touchtype.swiftkey.beta"))
        assertEquals(KeyboardKind.UNKNOWN, Keyboards.kindOf("helium314.keyboardx"))
    }

    @Test
    fun `the guide lists keyboards you type with, the one in use first, voice-only ones left out`() {
        val guided = Keyboards.toGuide(listOf(futo, googleVoice, ours, heli, thumb), defaultId = heli.id)
        assertEquals(listOf("HeliBoard", "FUTO Keyboard", "Thumb-Key"), guided.map { it.label })
        assertEquals(listOf(true, false, false), guided.map { it.isDefault })
        assertEquals(
            listOf(KeyboardKind.FIRST_VOICE_KEYBOARD, KeyboardKind.FUTO, KeyboardKind.FIRST_VOICE_KEYBOARD_HIDDEN),
            guided.map { it.kind },
        )
    }

    @Test
    fun `an unknown default keeps Android's order`() {
        assertEquals(listOf("FUTO Keyboard", "HeliBoard"), Keyboards.toGuide(listOf(futo, heli), defaultId = null).map { it.label })
    }

    @Test
    fun `a keyboard with no subtypes is a keyboard, not voice-only`() {
        assertFalse(thumb.isVoiceOnly)
        assertFalse(thumb.hasVoiceSubtype)
        assertTrue(ours.isVoiceOnly)
        assertEquals("com.dessalines.thumbkey", thumb.packageName)
    }

    @Test
    fun `other voice keyboards are the enabled ones with a voice subtype, not ours`() {
        assertEquals(listOf("Google voice typing"), Keyboards.otherVoiceKeyboards(listOf(futo, googleVoice, ours), ours.id, futo.id))
    }

    @Test
    fun `no other voice keyboard when only ours and plain keyboards are on`() {
        assertEquals(emptyList<String>(), Keyboards.otherVoiceKeyboards(listOf(futo, ours, thumb), ours.id, futo.id))
        assertEquals(emptyList<String>(), Keyboards.otherVoiceKeyboards(emptyList(), ours.id, null))
    }

    @Test
    fun `our other build counts as another voice keyboard`() {
        // Debug and release installed side by side: a mic key could open either.
        val debug = EnabledKeyboard("app.marmalade.stt.debug/app.marmalade.stt.ime.VoiceInputService", "Marmalade voice typing", listOf("voice"))
        assertEquals(listOf("Marmalade voice typing"), Keyboards.otherVoiceKeyboards(listOf(ours, debug), debug.id, null))
    }

    @Test
    fun `a typing keyboard that also declares a voice subtype is not a rival voice keyboard`() {
        val typingWithVoice = EnabledKeyboard("com.example.kb/com.example.kb.Ime", "Typing keyboard", listOf("keyboard", "voice"))
        assertFalse(typingWithVoice.isVoiceKeyboard)
        assertTrue(googleVoice.isVoiceKeyboard)
        assertEquals(
            listOf("Google voice typing"),
            Keyboards.otherVoiceKeyboards(listOf(typingWithVoice, googleVoice, ours), ours.id, defaultId = heli.id),
        )
        // Still listed as a keyboard to advise on.
        assertEquals(listOf("Typing keyboard"), Keyboards.toGuide(listOf(typingWithVoice, ours), null).map { it.label })
    }

    @Test
    fun `the keyboard in use is never named as one to turn off`() {
        // A voice-only input method can be the default too (the user switched to it).
        assertEquals(emptyList<String>(), Keyboards.otherVoiceKeyboards(listOf(googleVoice, ours), ours.id, googleVoice.id))
    }
}
