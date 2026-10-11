package app.marmalade.stt.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLinksTest {

    private val home = SettingsTarget.Action("android.settings.SETTINGS", SettingsPage.HOME)
    private val sdks = 28..37

    private fun voiceInput(sdk: Int) = SettingsLinks.targets(SettingsLink.VOICE_INPUT, sdk)

    private fun component(activity: String, page: SettingsPage, highlight: String) =
        SettingsTarget.Component("com.android.settings", "com.android.settings.Settings\$$activity", page, highlight)

    @Test
    fun `every link ends on Settings' home on every Android version`() {
        for (link in SettingsLink.entries) for (sdk in sdks) {
            val targets = SettingsLinks.targets(link, sdk)
            assertEquals("$link on $sdk", home, targets.last())
            assertEquals("$link on $sdk: home only as the last resort", 1, targets.count { it.page == SettingsPage.HOME })
        }
    }

    @Test
    fun `up to 12 the voice input action opens the Assist page, its row highlighted`() {
        for (sdk in 28..32) {
            assertEquals(
                listOf(
                    SettingsTarget.Action("android.settings.VOICE_INPUT_SETTINGS", SettingsPage.ASSIST, highlight = "voice_input_settings"),
                    home,
                ),
                voiceInput(sdk),
            )
        }
    }

    @Test
    fun `the Assist page lost the picker in 13, so the action is never tried from then on`() {
        for (sdk in 33..37) {
            assertFalse(voiceInput(sdk).any { it is SettingsTarget.Action && it.action == "android.settings.VOICE_INPUT_SETTINGS" })
        }
    }

    @Test
    fun `13 opens Languages and input, then the System page`() {
        assertEquals(
            listOf(
                component("LanguageAndInputSettingsActivity", SettingsPage.LANGUAGES_AND_INPUT, "voice_input_settings"),
                component("SystemDashboardActivity", SettingsPage.SYSTEM, "language_input_settings"),
                home,
            ),
            voiceInput(33),
        )
    }

    @Test
    fun `14 and later try Language and region, Languages, then System`() {
        for (sdk in 34..37) {
            val systemRow = if (sdk >= 36) "language_and_region_settings" else "language_settings"
            assertEquals(
                "sdk $sdk",
                listOf(
                    // With its action: Android 17's page crashes Settings on a null one.
                    component("LanguageAndRegionSettingsActivity", SettingsPage.LANGUAGE_AND_REGION, "speech_preference")
                        .copy(action = "android.settings.LANGUAGE_SETTINGS"),
                    component("LanguageSettingsActivity", SettingsPage.LANGUAGES, "voice_input_settings"),
                    component("SystemDashboardActivity", SettingsPage.SYSTEM, systemRow),
                    home,
                ),
                voiceInput(sdk),
            )
        }
    }

    /** #80: on 15 it resolves, but Settings crashes ("Invalid fragment for this activity"). */
    @Test
    fun `Languages and input is only tried on 13`() {
        for (sdk in 28..37) {
            val tried = voiceInput(sdk).any { it is SettingsTarget.Component && it.className.endsWith("LanguageAndInputSettingsActivity") }
            assertEquals("sdk $sdk", sdk == 33, tried)
        }
    }

    @Test
    fun `keyboards open the keyboard list, app details this app's page`() {
        for (sdk in sdks) {
            assertEquals(
                listOf(SettingsTarget.Action("android.settings.INPUT_METHOD_SETTINGS", SettingsPage.KEYBOARDS), home),
                SettingsLinks.targets(SettingsLink.KEYBOARDS, sdk),
            )
            assertEquals(
                listOf(
                    SettingsTarget.Action("android.settings.APPLICATION_DETAILS_SETTINGS", SettingsPage.APP_DETAILS, forApp = true),
                    SettingsTarget.Action("android.settings.MANAGE_APPLICATIONS_SETTINGS", SettingsPage.ALL_APPS),
                    home,
                ),
                SettingsLinks.targets(SettingsLink.APP_DETAILS, sdk),
            )
        }
    }

    @Test
    fun `directions follow the landed page and the release's labels`() {
        assertEquals(VoiceInputPath.ASSIST_9, SettingsLinks.voiceInputPath(SettingsPage.ASSIST, 28))
        assertEquals(VoiceInputPath.ASSIST_10, SettingsLinks.voiceInputPath(SettingsPage.ASSIST, 29))
        assertEquals(VoiceInputPath.ASSIST_11, SettingsLinks.voiceInputPath(SettingsPage.ASSIST, 30))
        assertEquals(VoiceInputPath.ASSIST_12, SettingsLinks.voiceInputPath(SettingsPage.ASSIST, 31))
        assertEquals(VoiceInputPath.ASSIST_12, SettingsLinks.voiceInputPath(SettingsPage.ASSIST, 32))
        assertEquals(VoiceInputPath.LANGUAGES_AND_INPUT, SettingsLinks.voiceInputPath(SettingsPage.LANGUAGES_AND_INPUT, 33))
        // 14 and 15 can still land on Languages & input (14 until the System page disables it).
        assertEquals(VoiceInputPath.LANGUAGES_AND_INPUT, SettingsLinks.voiceInputPath(SettingsPage.LANGUAGES_AND_INPUT, 34))
        for (sdk in 34..36) assertEquals(VoiceInputPath.LANGUAGES, SettingsLinks.voiceInputPath(SettingsPage.LANGUAGES, sdk))
        for (sdk in 34..37) {
            assertEquals(VoiceInputPath.LANGUAGE_AND_REGION, SettingsLinks.voiceInputPath(SettingsPage.LANGUAGE_AND_REGION, sdk))
        }
    }

    @Test
    fun `the System page fallback names the language page of its release`() {
        assertEquals(VoiceInputPath.LANGUAGES_AND_INPUT, SettingsLinks.voiceInputPath(SettingsPage.SYSTEM, 33))
        assertEquals(VoiceInputPath.LANGUAGES, SettingsLinks.voiceInputPath(SettingsPage.SYSTEM, 35))
        // 16 only falls back to System when Language & region replaced Languages (the API 36 emulator).
        assertEquals(VoiceInputPath.LANGUAGE_AND_REGION, SettingsLinks.voiceInputPath(SettingsPage.SYSTEM, 36))
        assertEquals(VoiceInputPath.LANGUAGE_AND_REGION, SettingsLinks.voiceInputPath(SettingsPage.SYSTEM, 37))
    }

    @Test
    fun `when only home or nothing opens, the directions say to search`() {
        for (sdk in sdks) {
            assertEquals(VoiceInputPath.SEARCH, SettingsLinks.voiceInputPath(SettingsPage.HOME, sdk))
            assertEquals(VoiceInputPath.SEARCH, SettingsLinks.voiceInputPath(null, sdk))
        }
    }

    @Test
    fun `every target's page has directions that don't say search, except home`() {
        for (sdk in sdks) for (target in voiceInput(sdk)) {
            val path = SettingsLinks.voiceInputPath(target.page, sdk)
            assertEquals("$target on $sdk", target.page == SettingsPage.HOME, path == VoiceInputPath.SEARCH)
        }
    }

    @Test
    fun `a hint shows only when a fallback opened`() {
        for (link in SettingsLink.entries) for (sdk in sdks) {
            val targets = SettingsLinks.targets(link, sdk)
            assertFalse("$link best target on $sdk", SettingsLinks.needsHint(link, targets.first().page))
            assertTrue("$link home on $sdk", SettingsLinks.needsHint(link, SettingsPage.HOME))
        }
        assertTrue(SettingsLinks.needsHint(SettingsLink.APP_DETAILS, SettingsPage.ALL_APPS))
        // Every voice input page but home holds the way to the picker: its directions say the rest.
        assertFalse(SettingsLinks.needsHint(SettingsLink.VOICE_INPUT, SettingsPage.SYSTEM))
    }
}
