package app.marmalade.stt.ui.screen

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.LanguageOptions
import app.marmalade.stt.install.SpeedBudget
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.install.SttEngineDescriptor

// What an Engines card offers and where it sits in the list (#27). Plain
// functions so they are unit-tested; EnginesScreen only draws the result.

/** The card's radio row: shown for an installed engine, which one tap picks. */
enum class EngineSelection {
    /** Installed and in use: the radio is on. */
    SELECTED,

    /** Installed and pickable ([EngineUse.AVAILABLE]): tapping the card picks it. */
    SELECTABLE,

    /** Not installed, downloading or damaged: no radio row, the install actions instead. */
    NONE,
}

/** An engine card's radio row and its buttons. */
data class EngineCardUi(val selection: EngineSelection, val actions: List<EngineAction>)

/**
 * The card for [engine] in [state]. An intact install gets the radio row and
 * a "Remove" button ("Check files" stays in the info dialog); everything else
 * gets the info dialog's install actions ([engineActions]), so a damaged model
 * offers "Remove" next to "Download again" on the card too. [prefs] is null
 * until the settings have been read: no radio then, rather than a guess.
 */
fun engineCardUi(engine: SttEngineDescriptor, state: InstallState, prefs: SpeechPrefs?): EngineCardUi {
    if (state != InstallState.Installed) return EngineCardUi(EngineSelection.NONE, engineActions(state, onDetail = true))
    val selection = when (prefs?.let { engineUse(engine, state, it) }) {
        EngineUse.IN_USE -> EngineSelection.SELECTED
        EngineUse.AVAILABLE -> EngineSelection.SELECTABLE
        EngineUse.UNAVAILABLE, null -> EngineSelection.NONE
    }
    return EngineCardUi(selection, listOf(EngineAction.REMOVE))
}

/**
 * The Engines list: [main] as full cards, then [other] as compact rows
 * behind the "Other engines" button (empty: no button).
 */
data class EngineGroups(val main: List<SttEngineDescriptor>, val other: List<SttEngineDescriptor>)

/**
 * Splits the catalog for the Engines tab (Max, 2026-09-28). [prefs] gives the
 * phone's speed and the settings; [installed] the ids with files on the phone.
 *
 * - [EngineGroups.main]: the user (non-developer) engines within this
 *   phone's speed budget, plus the default, the engine in use and the
 *   recommended one whatever their speed. The recommended one first.
 * - [EngineGroups.other]: the user engines over the budget, installed
 *   engines not in [EngineGroups.main] (so their files can be removed), and
 *   with Developer engines on every other engine, developer engines last.
 *
 * Each group keeps catalog order, which runs from the everyday engines to the
 * experimental ones, so the list doesn't reshuffle when the phone's speed is
 * re-measured.
 */
fun engineGroups(prefs: SpeechPrefs, installed: Set<String>): EngineGroups {
    val recommendedId = prefs.recommendedEngineId
    val pinned = setOfNotNull(SttEngineCatalog.default.id, prefs.activeEngine.id, recommendedId)
    val main = SttEngineCatalog.engines.filter { engine ->
        engine.id in pinned || !engine.developer &&
            !SpeedBudget.overBudget(engine, prefs.speedBudget, prefs.engineCosts, prefs.tooSlowEngineIds)
    }
    val recommended = main.firstOrNull { it.id == recommendedId }
    val other = SttEngineCatalog.engines.filter { engine ->
        engine !in main && (prefs.developerEngines || engine.id in installed || !engine.developer)
    }
    return EngineGroups(
        main = listOfNotNull(recommended) + (main - setOfNotNull(recommended)),
        other = other.sortedBy { it.developer },
    )
}

/**
 * The card's language line (#27): [engine] doesn't transcribe the language
 * the user speaks ([language], [SpeechPrefs.spokenLanguage]). On the engine
 * in use it names [suggestion], the engine recommended for the phone, when
 * that one does.
 *
 * @property choices non-empty for an engine that can't detect the language
 *   (Canary, #45) while Settings → Recognition language is Auto and the
 *   system language isn't one of its own: the card asks for one of these
 *   codes instead, as the engine would otherwise get its default language.
 */
data class LanguageNote(
    val language: String,
    val suggestion: SttEngineDescriptor?,
    val choices: List<String> = emptyList(),
)

/**
 * The note for [engine], or null: it transcribes the language, the settings
 * aren't read yet, or no listed engine transcribes the language either (then
 * every card would say so, and the recommendation already fell back to
 * English). An engine without auto-detect on Auto always gets its note
 * ([LanguageNote.choices]): it would run in the wrong language whatever the
 * other engines cover. Not when Developer engines set its language
 * ([SpeechPrefs.languageOverride]): then it runs in a language it has, and
 * only the usual note can show.
 */
fun languageNote(engine: SttEngineDescriptor, prefs: SpeechPrefs?, systemLocale: String): LanguageNote? {
    if (prefs == null) return null
    val language = prefs.spokenLanguage(systemLocale)
    if (EngineLanguages.supports(engine, language)) return null
    val options = engine.languageOptions
    if (prefs.recognitionLanguage == SpeechPrefs.AUTO && options != null && LanguageOptions.AUTO !in options.codes &&
        prefs.languageOverride(engine) == null
    ) {
        return LanguageNote(language, suggestion = null, choices = options.codes)
    }
    val visible = prefs.visibleEngines
    if (visible.none { EngineLanguages.supports(it, language) }) return null
    val suggestion = prefs.recommendedEngineId?.let(SttEngineCatalog::byId)
        ?.takeIf { prefs.activeEngine.id == engine.id && it.id != engine.id && EngineLanguages.supports(it, language) }
    return LanguageNote(language, suggestion)
}

/**
 * The language the QUALITY meters rate engines in (#76): the one the user
 * dictates in, Settings → Recognition language, or on Auto [systemLocale]
 * (the UI locale, a BCP-47 tag) ([SpeechPrefs.spokenLanguage]); the UI locale
 * too until the settings have been read ([prefs] null). Normalised.
 */
fun ratingLanguage(prefs: SpeechPrefs?, systemLocale: String): String =
    prefs?.spokenLanguage(systemLocale) ?: EngineLanguages.normalize(systemLocale)
