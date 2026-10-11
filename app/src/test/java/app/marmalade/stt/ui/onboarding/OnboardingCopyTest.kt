package app.marmalade.stt.ui.onboarding

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The setup guide names other apps' settings by their labels, so these must
 * match what the user sees (#11: FUTO Keyboard 0.1.30 and Android's keyboard
 * list, checked on the emulator).
 */
class OnboardingCopyTest {

    private val strings: String = File("src/main/res/values/strings.xml").readText()

    private fun string(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""").find(strings)?.groupValues?.get(1)
            ?: error("no string $name")

    @Test
    fun `the manual FUTO route uses FUTO 0_1_30's labels`() {
        val body = string("onboarding_futo_manual_body")
        assertTrue(body, "Voice Input" in body)
        assertTrue(body, "Disable built-in voice input" in body)
        assertTrue(body, "Use voice input provided by external app" in body)
    }

    @Test
    fun `the keyboard step names the keyboard as Android lists it`() {
        assertTrue(string("onboarding_keyboard_body"), string("ime_name") in string("onboarding_keyboard_body"))
    }
}
