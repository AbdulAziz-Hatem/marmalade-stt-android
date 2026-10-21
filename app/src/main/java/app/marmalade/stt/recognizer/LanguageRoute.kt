package app.marmalade.stt.recognizer

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.EngineRecommender
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.install.SttEngineDescriptor

/**
 * Which engine serves a request in a caller's language (#90): another app
 * asking for a language means it (a language-learning app asks for the
 * language being learned), so a request is never run by an engine that
 * can't hear it.
 */
sealed interface LanguageRoute {
    /** The active engine: no language asked, or the active engine transcribes it (or nothing is installed yet). */
    data object Active : LanguageRoute

    /** Another installed engine transcribes it: [engine] runs this request, the stored choice stays. */
    data class Engine(val engine: SttEngineDescriptor) : LanguageRoute

    /**
     * No installed engine transcribes it. [downloadable]: an engine the user
     * could download does (`ERROR_LANGUAGE_UNAVAILABLE`); otherwise none
     * does (`ERROR_LANGUAGE_NOT_SUPPORTED`).
     */
    data class Unavailable(val downloadable: Boolean) : LanguageRoute
}

/**
 * The route for a request in [language] (a BCP-47 tag, or null when the
 * caller set none).
 *
 * - The active engine keeps it when it transcribes the language.
 * - Otherwise the best installed engine for it that may run under [prefs]
 *   (the recommender's near-best order, `EngineRecommender.nearBest`, then
 *   its preference order).
 * - With nothing installed at all, the active engine still takes a language
 *   some engine could transcribe, so the caller hears "no model" as before
 *   (`ERROR_LANGUAGE_UNAVAILABLE` too); one no engine knows is unsupported.
 *
 * @param installed whether an engine's model is installed.
 */
fun routeLanguage(prefs: SpeechPrefs, language: String?, installed: (String) -> Boolean): LanguageRoute {
    if (language == null) return LanguageRoute.Active
    val code = EngineLanguages.normalize(language)
    val downloadable = prefs.visibleEngines.any { EngineLanguages.supports(it, code) }
    val active = prefs.activeEngine
    val usable = SttEngineCatalog.engines.filter { prefs.runnable(it) && installed(it.id) }
    if (usable.isEmpty()) return if (downloadable) LanguageRoute.Active else LanguageRoute.Unavailable(downloadable = false)
    if (active in usable && EngineLanguages.supports(active, code)) return LanguageRoute.Active
    val candidates = byPreference(usable.filter { EngineLanguages.supports(it, code) })
    EngineRecommender.nearBest(candidates, code).firstOrNull()?.let { return LanguageRoute.Engine(it) }
    return LanguageRoute.Unavailable(downloadable)
}

/** [engines] in the recommender's preference order, the rest after them in catalog order. */
private fun byPreference(engines: List<SttEngineDescriptor>): List<SttEngineDescriptor> =
    engines.sortedBy { EngineRecommender.preference.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }

/**
 * The languages the RecognitionService reports (`onCheckRecognitionSupport`,
 * the language-details broadcast), as BCP-47 tags.
 *
 * @property installed what the installed engines that may run transcribe now:
 *   the active engine's first, the spoken language
 *   ([SpeechPrefs.spokenLanguage]) leading, then the other engines'.
 * @property downloadable what an engine the user could download would add.
 */
data class RecognitionLanguages(val installed: List<String>, val downloadable: List<String>)

/**
 * [RecognitionLanguages] under [prefs]. The phone's full tag ([systemLocale],
 * e.g. "en-US") follows its language, so a caller asking for its own locale
 * finds it.
 */
fun recognitionLanguages(prefs: SpeechPrefs, systemLocale: String, installed: (String) -> Boolean): RecognitionLanguages {
    val spoken = prefs.spokenLanguage(systemLocale)
    val active = prefs.activeEngine
    val usable = SttEngineCatalog.engines.filter { prefs.runnable(it) && installed(it.id) }
    val installedCodes = listOfNotNull(active.takeIf { it in usable }).plus(usable - active)
        .flatMap { EngineLanguages.of(it).sorted() }
        .distinct()
        .sortedByDescending { it == spoken }
    val downloadable = prefs.visibleEngines.filterNot { it in usable }
        .flatMap { EngineLanguages.of(it) }
        .distinct()
        .filterNot { it in installedCodes }
        .sorted()
    val system = EngineLanguages.normalize(systemLocale)
    fun withSystemTag(codes: List<String>) =
        codes.flatMap { if (it == system && systemLocale != it) listOf(it, systemLocale) else listOf(it) }
    return RecognitionLanguages(withSystemTag(installedCodes), withSystemTag(downloadable))
}
