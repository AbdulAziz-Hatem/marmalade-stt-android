package app.marmalade.stt.system

// Every link from the app into Android's Settings (#79), as pure lists of
// screens to try, best first, so they are unit-tested per Android version.
// SystemSettings resolves each before launching it and opens the first that
// this phone has and lets us start. The page a target lands on decides the
// written directions and the hint shown when only a fallback opened. Sources
// per release and OEM: docs/RESEARCH-keyboards-2026-10.md §3.1 and §3.1a.

/** A place in the app that opens system Settings. */
enum class SettingsLink {
    /** The speech recognition service's Voice input picker (the setup guide's speech step). */
    VOICE_INPUT,

    /** The keyboard list, where our voice keyboard is turned on. */
    KEYBOARDS,

    /** This app's page in Settings, where a blocked permission is turned back on. */
    APP_DETAILS,
}

/** The Settings page a [SettingsTarget] lands on. */
enum class SettingsPage {
    /** Android 9 to 12: Assist & voice input (Digital assistant app from 11), with the Voice input row. */
    ASSIST,

    /** Android 13: Languages & input, with Voice input under Speech. */
    LANGUAGES_AND_INPUT,

    /** Android 14 to 16: Languages, with Voice input under Speech. */
    LANGUAGES,

    /** Android 16 (with its regional preferences) and 17: Language & region, with the Speech row. */
    LANGUAGE_AND_REGION,

    /** The System page, with the row that leads to the language page. */
    SYSTEM,

    /** The keyboard list (Virtual keyboard, On-screen keyboard, Keyboard apps). */
    KEYBOARDS,

    /** This app's App info page. */
    APP_DETAILS,

    /** The list of all apps. */
    ALL_APPS,

    /** Settings' home: the user has to find the page from there. */
    HOME,
}

/**
 * Where the speech step's written directions send the user, from Settings'
 * home, with each release's own labels (§3.1a).
 */
enum class VoiceInputPath {
    /** Android 9: Apps & notifications → Advanced → Default apps → Assist & voice input. */
    ASSIST_9,

    /** Android 10: … → Default apps → Assist app. */
    ASSIST_10,

    /** Android 11: … → Default apps → Digital assistant app. */
    ASSIST_11,

    /** Android 12: Apps → Default apps → Digital assistant app → Voice input. */
    ASSIST_12,

    /** Android 13: System → Languages & input → Voice input. */
    LANGUAGES_AND_INPUT,

    /** Android 14 to 16: System → Languages → Voice input. */
    LANGUAGES,

    /** Android 16 with Language & region, and 17: System → Language & region → Speech → Voice input. */
    LANGUAGE_AND_REGION,

    /** None of the known pages opens: search Settings for Voice input. */
    SEARCH,
}

/** A settings screen to try opening. */
sealed interface SettingsTarget {
    /** Where it lands. */
    val page: SettingsPage

    /**
     * The key of the row to scroll to and highlight (Settings'
     * `:settings:fragment_args_key`, the one its own search results use),
     * or null. A key the page doesn't have is ignored.
     */
    val highlight: String?

    /**
     * An explicit activity of the Settings app, sent with [action] when the
     * page needs one: Android 17's Language & region crashes Settings on a
     * null action (the Pixel 8a, 2026-10-10).
     */
    data class Component(
        val packageName: String,
        val className: String,
        override val page: SettingsPage,
        override val highlight: String? = null,
        val action: String? = null,
    ) : SettingsTarget

    /** An intent action; with [forApp] its data is this app's `package:` URI. */
    data class Action(
        val action: String,
        override val page: SettingsPage,
        val forApp: Boolean = false,
        override val highlight: String? = null,
    ) : SettingsTarget
}

object SettingsLinks {

    private const val SETTINGS = "com.android.settings"
    private const val ACTION_VOICE_INPUT_SETTINGS = "android.settings.VOICE_INPUT_SETTINGS"
    private const val ACTION_INPUT_METHOD_SETTINGS = "android.settings.INPUT_METHOD_SETTINGS"
    private const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    private const val ACTION_MANAGE_APPLICATIONS_SETTINGS = "android.settings.MANAGE_APPLICATIONS_SETTINGS"
    private const val ACTION_SETTINGS = "android.settings.SETTINGS"
    private const val ACTION_LANGUAGE_SETTINGS = "android.settings.LANGUAGE_SETTINGS"

    /** The Voice input row (Assist page up to 12, Languages & input on 13, Languages on 14 to 16). */
    private const val VOICE_INPUT_ROW = "voice_input_settings"

    /** The Speech row of Language & region. */
    private const val SPEECH_ROW = "speech_preference"

    private val HOME = SettingsTarget.Action(ACTION_SETTINGS, SettingsPage.HOME)

    private fun settings(activity: String, page: SettingsPage, highlight: String? = null) =
        SettingsTarget.Component(SETTINGS, "$SETTINGS.Settings\$$activity", page, highlight)

    /**
     * The Voice input picker is a fragment of Settings' unexported
     * SubSettings, so no app can open it: the guide opens the page that
     * holds its row (or the Speech row) and highlights it.
     * - Up to 12, `ACTION_VOICE_INPUT_SETTINGS` opens the Assist page. 13
     *   still maps it there, but the picker left that page, so it is only
     *   used up to 12.
     * - 13: Languages & input. 14 to 16: Languages; 14 disables Languages &
     *   input once the System page is opened, and `android.settings.
     *   LANGUAGE_SETTINGS` shows a chooser on 14 and 15, so the guide names
     *   the activities. Languages & input is never tried from 14 on: it
     *   still resolves on 15, but Settings crashes on it ("Invalid fragment
     *   for this activity", the API 35 emulator; found by the TTS app, #80).
     * - Android 16 with regional preferences on disables
     *   `LanguageSettingsActivity` and shows Language & region, which has no
     *   activity of its own until 16 QPR2 (`LanguageAndRegionSettingsActivity`,
     *   the only one on 17). Then the System page, its row highlighted, is
     *   the nearest (the API 36 emulator, 2026-10-10).
     */
    private fun voiceInput(sdk: Int): List<SettingsTarget> {
        val assist = SettingsTarget.Action(ACTION_VOICE_INPUT_SETTINGS, SettingsPage.ASSIST, highlight = VOICE_INPUT_ROW)
        val languagesAndInput = settings("LanguageAndInputSettingsActivity", SettingsPage.LANGUAGES_AND_INPUT, VOICE_INPUT_ROW)
        val languages = settings("LanguageSettingsActivity", SettingsPage.LANGUAGES, VOICE_INPUT_ROW)
        // Its onViewCreated compares the intent's action: without one, Settings crashes (Android 17).
        val languageAndRegion = settings("LanguageAndRegionSettingsActivity", SettingsPage.LANGUAGE_AND_REGION, SPEECH_ROW)
            .copy(action = ACTION_LANGUAGE_SETTINGS)
        val systemRow = when {
            sdk >= 36 -> "language_and_region_settings"
            sdk >= 34 -> "language_settings"
            else -> "language_input_settings"
        }
        val system = settings("SystemDashboardActivity", SettingsPage.SYSTEM, systemRow)
        return when {
            sdk >= 34 -> listOf(languageAndRegion, languages, system, HOME)
            sdk == 33 -> listOf(languagesAndInput, system, HOME)
            else -> listOf(assist, HOME)
        }
    }

    /** `ACTION_INPUT_METHOD_SETTINGS` opens the keyboard list on every release from 9 to 17. */
    private fun keyboards(): List<SettingsTarget> = listOf(
        SettingsTarget.Action(ACTION_INPUT_METHOD_SETTINGS, SettingsPage.KEYBOARDS),
        HOME,
    )

    private fun appDetails(): List<SettingsTarget> = listOf(
        SettingsTarget.Action(ACTION_APPLICATION_DETAILS_SETTINGS, SettingsPage.APP_DETAILS, forApp = true),
        SettingsTarget.Action(ACTION_MANAGE_APPLICATIONS_SETTINGS, SettingsPage.ALL_APPS),
        HOME,
    )

    /** The screens to try for [link] on Android [sdk], best first; the last one is Settings' home. */
    fun targets(link: SettingsLink, sdk: Int): List<SettingsTarget> = when (link) {
        SettingsLink.VOICE_INPUT -> voiceInput(sdk)
        SettingsLink.KEYBOARDS -> keyboards()
        SettingsLink.APP_DETAILS -> appDetails()
    }

    /**
     * The speech step's directions when "Open settings" lands on [page]
     * (null: nothing opens) on Android [sdk]. The System page only opens
     * when the language page has no activity, which on 16 means Language &
     * region.
     */
    fun voiceInputPath(page: SettingsPage?, sdk: Int): VoiceInputPath = when (page) {
        SettingsPage.ASSIST -> when {
            sdk <= 28 -> VoiceInputPath.ASSIST_9
            sdk == 29 -> VoiceInputPath.ASSIST_10
            sdk == 30 -> VoiceInputPath.ASSIST_11
            else -> VoiceInputPath.ASSIST_12
        }
        SettingsPage.LANGUAGES_AND_INPUT -> VoiceInputPath.LANGUAGES_AND_INPUT
        SettingsPage.LANGUAGES -> VoiceInputPath.LANGUAGES
        SettingsPage.LANGUAGE_AND_REGION -> VoiceInputPath.LANGUAGE_AND_REGION
        SettingsPage.SYSTEM -> when {
            sdk >= 36 -> VoiceInputPath.LANGUAGE_AND_REGION
            sdk >= 34 -> VoiceInputPath.LANGUAGES
            else -> VoiceInputPath.LANGUAGES_AND_INPUT
        }
        else -> VoiceInputPath.SEARCH
    }

    /**
     * Whether landing on [page] for [link] needs a hint of where to go next:
     * true when only a fallback opened.
     */
    fun needsHint(link: SettingsLink, page: SettingsPage): Boolean = when (link) {
        SettingsLink.VOICE_INPUT -> page == SettingsPage.HOME
        SettingsLink.KEYBOARDS -> page != SettingsPage.KEYBOARDS
        SettingsLink.APP_DETAILS -> page != SettingsPage.APP_DETAILS
    }
}
