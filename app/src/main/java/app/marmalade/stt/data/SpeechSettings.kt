package app.marmalade.stt.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.EngineRecommender
import app.marmalade.stt.install.EngineSpeed
import app.marmalade.stt.install.LanguageOptions
import app.marmalade.stt.install.SpeedBudget
import app.marmalade.stt.install.SpeedTier
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.speech.EndOfDictation
import app.marmalade.stt.speech.EndWords
import app.marmalade.stt.speech.ListenSurface
import app.marmalade.stt.speech.OrtTuning
import app.marmalade.stt.speech.Patience
import app.marmalade.stt.speech.SessionOptions
import app.marmalade.stt.speech.turn.TurnDetector
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The user's speech settings.
 *
 * @property minSilenceSeconds how long a pause ends an utterance (0.3–1.5 s,
 *   default [SessionOptions.DEFAULT_MIN_SILENCE_SECONDS]), in
 *   [EndOfDictation.PAUSE] mode, on the Dictate screen, and for Smart Turn
 *   if its model fails to load (and between utterances in End word and Tap
 *   stop).
 * @property keyboardEnd Settings → End of speech → Voice keyboard (#43):
 *   [EndOfDictation.TAP_STOP] by default, or one of the auto modes
 *   ([ListenSurface.KEYBOARD]). Replaces "patient mode": whoever had it on
 *   gets Tap to stop, the default, so the old key isn't read.
 * @property appsEnd Settings → End of speech → Other apps (#43): the
 *   speak-now sheet and the RecognitionService. [EndOfDictation.SMART_TURN]
 *   by default; one of [APPS_ENDS].
 * @property appsEndChosen the user chose [appsEnd] in Settings (it is
 *   stored): short commands then get it too ([endFor], #120).
 * @property patience how patient Smart Turn is, on every surface that uses it.
 * @property endWords the end words for [EndOfDictation.END_WORD], lower case;
 *   never empty.
 * @property keepModelLoaded Settings → Keep engine ready: keep the model in
 *   memory between sessions. Off (the default since #42, Max 2026-09-29)
 *   frees the engine's memory (about 0.6–4 GB, [SpeedBudget.REFERENCE]) after
 *   each session at the cost of a reload next time; on suits people who
 *   dictate often. A value the user set is kept.
 * @property selectedEngineId the engine the user picked on the Engines tab;
 *   null until they pick one. What runs is [activeEngine].
 * @property developerEngines show the developer engines (#13) on the Engines
 *   tab and let one be picked. Off by default.
 * @property recognitionLanguage Settings → Recognition language (#27): an
 *   ISO 639 code from [EngineLanguages.userChoices], or [AUTO] ("") to follow
 *   the system language. Picks the recommended engine and is the language
 *   hint for engines that take one ([languageFor]).
 * @property engineLanguages per-engine language override, by engine id: the
 *   info dialog's Language row, offered only with Developer engines on, and
 *   only applied then.
 * @property deviceProbeMs the phone's CPU probe time (#27,
 *   [app.marmalade.stt.speech.SpeedProbe]); null until measured.
 * @property deviceProbeThreads the recognizer thread count when it was
 *   measured, which sets each engine's cost ([engineCosts]).
 * @property deviceTotalMemBytes the phone's RAM (`totalMem`), stored with each
 *   recommendation; null until then. Engines that fit it only without ONNX
 *   Runtime's memory arena cost more ([engineCosts], #47).
 * @property tooSlowEngineIds engines that fell behind real time in a session
 *   on this phone (#30, [app.marmalade.stt.speech.SpeechEngineManager.tooSlow]):
 *   over the speed budget whatever their reference cost. Cleared by
 *   re-measuring the phone's speed.
 * @property recommendedEngineId the engine recommended for this phone's
 *   language and hardware ([app.marmalade.stt.install.EngineRecommender]),
 *   always a user engine.
 * @property recommendationRules the [EngineRecommender.RULES_VERSION] that
 *   [recommendedEngineId] was chosen under; null when stored before #50.
 *   When it isn't the current one, startup recommends again
 *   ([recommendationOutdated]).
 * @property ortTuningId Settings → Developer: ONNX Runtime tuning (#31,
 *   [OrtTuning.id]); null for [OrtTuning.DEFAULT]. Only applied with
 *   Developer engines on.
 * @property otherEnginesExpanded the Engines tab shows its "Other engines"
 *   (remembered, collapsed by default).
 */
data class SpeechPrefs(
    val minSilenceSeconds: Float = SessionOptions.DEFAULT_MIN_SILENCE_SECONDS,
    val keyboardEnd: EndOfDictation = EndOfDictation.TAP_STOP,
    val appsEnd: EndOfDictation = EndOfDictation.SMART_TURN,
    val appsEndChosen: Boolean = false,
    val patience: Patience = Patience.NORMAL,
    val endWords: List<String> = EndWords.DEFAULT,
    val keepModelLoaded: Boolean = false,
    val selectedEngineId: String? = null,
    val developerEngines: Boolean = false,
    val recognitionLanguage: String = AUTO,
    val engineLanguages: Map<String, String> = emptyMap(),
    val deviceProbeMs: Long? = null,
    val deviceProbeThreads: Int = SpeedBudget.REFERENCE_THREADS,
    val deviceTotalMemBytes: Long? = null,
    val tooSlowEngineIds: Set<String> = emptySet(),
    val recommendedEngineId: String? = null,
    val recommendationRules: String? = null,
    val ortTuningId: String? = null,
    val otherEnginesExpanded: Boolean = false,
) {
    /** How recognizers run on ONNX Runtime: [ortTuningId] while Developer engines are on, else [OrtTuning.DEFAULT]. */
    val ortTuning: OrtTuning
        get() = ortTuningId?.takeIf { developerEngines }?.let { OrtTuning.parse(it) } ?: OrtTuning.DEFAULT

    /** This phone's speed against the Pixel 8a (2.0 = twice as fast), or null until measured. */
    val deviceSpeedFactor: Double? get() = deviceProbeMs?.let { SpeedBudget.speedFactor(it) }

    /** How much decode cost this phone affords, in Parakeet Ultra units ([SpeedBudget.budget]). */
    val speedBudget: Double get() = SpeedBudget.budget(deviceSpeedFactor)

    /**
     * Each engine's cost and memory on this phone: at its recognizer threads
     * ([SpeedBudget.costsAt]) and, once its RAM is known, as it runs there
     * ([SpeedBudget.forPhone]).
     */
    val engineCosts: Map<String, EngineSpeed>
        get() = SpeedBudget.costsAt(deviceProbeThreads, SpeedBudget.forPhone(deviceTotalMemBytes))

    /** [engine]'s speed tier on this phone ([SpeedBudget.speedTier]); null when it was never measured. */
    fun speedTier(engine: SttEngineDescriptor): SpeedTier? =
        SpeedBudget.speedTier(engine, speedBudget, engineCosts, tooSlowEngineIds)

    /**
     * The engine to recommend for this phone ([EngineRecommender.recommend]):
     * the language spoken ([spokenLanguage] of [systemLocale]), [totalMemBytes]
     * of RAM and the speed budget.
     */
    fun recommendation(
        totalMemBytes: Long,
        systemLocale: String = Locale.getDefault().toLanguageTag(),
    ): EngineRecommender.Recommendation = EngineRecommender.recommend(
        spokenLanguage(systemLocale),
        totalMemBytes,
        speedBudget,
        // Not yet for the phone's RAM: recommend() applies that itself.
        reference = SpeedBudget.costsAt(deviceProbeThreads),
        tooSlow = tooSlowEngineIds,
    )

    /**
     * The end of speech on [surface] (#43): Pause on the Dictate screen,
     * [keyboardEnd] on the voice keyboard, [appsEnd] for other apps. One-shot
     * requests never get Tap stop or anything outside [APPS_ENDS] (#41: with
     * Tap stop no utterance would end until the caller stopped, holding the
     * mic open).
     *
     * A [shortCommand] from another app (#120: its own audio stream, or a
     * web-search hint) gets Pause while the user hasn't chosen an end of
     * speech for other apps: Max, 2026-10-25, "for the waze short commands
     * I think auto-stop is better. Longer speech yes smart turn". Smart
     * Turn's check costs about 0.9 s on a Pixel 6a, and a command has no
     * pause to think in. A choice the user made always wins.
     */
    fun endFor(surface: ListenSurface, shortCommand: Boolean = false): EndOfDictation = when (surface) {
        ListenSurface.DICTATE -> EndOfDictation.PAUSE
        ListenSurface.KEYBOARD -> keyboardEnd
        ListenSurface.APPS -> when {
            shortCommand && !appsEndChosen -> EndOfDictation.PAUSE
            else -> appsEnd.takeIf { it in APPS_ENDS } ?: DEFAULT_APPS_END
        }
    }

    /**
     * The engine's options for one session on [surface] ([endFor]).
     *
     * @param shortCommand see [endFor].
     * @param turnDetector the loaded Smart Turn model (`SmartTurnModel`), or
     *   null if it failed to load: Smart Turn then behaves as Pause.
     *   Passed on only in Smart Turn mode.
     */
    fun sessionOptions(surface: ListenSurface, shortCommand: Boolean = false, turnDetector: TurnDetector? = null): SessionOptions {
        val end = endFor(surface, shortCommand)
        return SessionOptions(
            end = end,
            minSilenceSeconds = minSilenceSeconds,
            endWords = endWords,
            turnDetector = turnDetector?.takeIf { end == EndOfDictation.SMART_TURN },
            patience = patience,
        )
    }

    /** Some surface uses Smart Turn: its model is worth loading ahead of a session, and Settings shows its patience (#42). */
    val usesSmartTurn: Boolean
        get() = ListenSurface.entries.any { endFor(it) == EndOfDictation.SMART_TURN }

    /** True when any surface ends at an end word: Settings then shows the end words (#42). */
    val usesEndWord: Boolean
        get() = ListenSurface.entries.any { endFor(it) == EndOfDictation.END_WORD }

    /** The engine that runs: the selected one while it is visible, else the recommended one, else the default. */
    val activeEngine: SttEngineDescriptor
        get() = SttEngineCatalog.effective(selectedEngineId, developerEngines, recommendedEngineId)

    /** [engine] runs if selected ([SttEngineCatalog.runnable] under these settings). */
    fun runnable(engine: SttEngineDescriptor): Boolean = SttEngineCatalog.runnable(engine, developerEngines)

    /** The engines listed under these settings ([SttEngineCatalog.visibleTo]). */
    val visibleEngines: List<SttEngineDescriptor>
        get() = SttEngineCatalog.visibleTo(developerEngines)

    /**
     * The language the user speaks: [recognitionLanguage], or on Auto the
     * system's ([systemLocale], a BCP-47 tag), as a normalised ISO 639 code.
     * The recommendation ([app.marmalade.stt.install.EngineRecommender]) and
     * the engine cards' language check use it.
     */
    fun spokenLanguage(systemLocale: String = Locale.getDefault().toLanguageTag()): String =
        EngineLanguages.normalize(recognitionLanguage.ifEmpty { systemLocale })

    /**
     * The info dialog's "Language for this engine" for [engine] when it
     * applies: Developer engines on and one of the engine's codes. Else null.
     */
    fun languageOverride(engine: SttEngineDescriptor): String? {
        val options = engine.languageOptions ?: return null
        return engineLanguages[engine.id]?.takeIf { developerEngines && it in options.codes }
    }

    /**
     * The language hint for [engine]; empty for auto-detect, and for engines
     * that take no hint.
     *
     * 1. With Developer engines on, the engine's own override
     *    ([engineLanguages]) wins while it is one of its options.
     * 2. A chosen [recognitionLanguage] is the hint wherever the engine takes
     *    it; an engine that doesn't gets its default (auto-detect where it
     *    has it, so it can still hear the language if it knows it).
     * 3. On Auto, engines that detect the language do; the rest (Canary
     *    needs one) get the system language if they take it, else their
     *    default. The system language is only a guess at what is spoken, so
     *    it never overrides detection.
     */
    fun languageFor(engine: SttEngineDescriptor, systemLocale: String = Locale.getDefault().toLanguageTag()): String {
        val options = engine.languageOptions ?: return ""
        languageOverride(engine)?.let { return it }
        val wanted = when {
            recognitionLanguage != AUTO -> recognitionLanguage
            LanguageOptions.AUTO in options.codes -> return LanguageOptions.AUTO
            else -> EngineLanguages.normalize(systemLocale)
        }
        return wanted.takeIf { it in options.codes } ?: options.default
    }

    /**
     * The language the text cleanup works in (#41, `TextProcConfig.language`):
     * its English-only rules (filler words, spoken commands and punctuation,
     * numbers as digits) run only when this is "en".
     *
     * 1. The active engine's language hint, when it sets one ([languageFor]):
     *    the engine writes that language.
     * 2. Else the engine's only language: an English-only engine writes
     *    English whatever the phone's language.
     * 3. Else the language the user speaks ([spokenLanguage]).
     *
     * A multilingual engine on Auto (or Ultra, which takes no hint) may hear
     * another language than the phone's. The English rules then don't run on
     * English speech, which only leaves the text less tidy; the other way round
     * they would delete words. The Japanese rules check the text itself: they
     * run only on a Final with a kana or kanji in it, so English heard on a
     * Japanese phone stays plain English (#60).
     */
    fun cleanupLanguage(systemLocale: String = Locale.getDefault().toLanguageTag()): String =
        cleanupLanguageFor(activeEngine, systemLocale)

    /**
     * [cleanupLanguage] as it would be with [engine] the active engine. The A/B
     * benchmark (#45) cleans each engine's transcript in this language.
     */
    fun cleanupLanguageFor(
        engine: SttEngineDescriptor,
        systemLocale: String = Locale.getDefault().toLanguageTag(),
    ): String {
        val hint = languageFor(engine, systemLocale)
        if (hint != LanguageOptions.AUTO) return EngineLanguages.normalize(hint)
        return EngineLanguages.of(engine).singleOrNull() ?: spokenLanguage(systemLocale)
    }

    /**
     * These settings for one request that asked for [languageTag] (a caller's
     * `RecognizerIntent.EXTRA_LANGUAGE`, a BCP-47 tag): the language counts as
     * the chosen recognition language, so it is the hint wherever the engine
     * takes it ([languageFor]) and the cleanup language ([cleanupLanguage]).
     * The developer per-engine override doesn't apply: the caller asked. The
     * stored settings don't change.
     */
    fun forRequestLanguage(languageTag: String): SpeechPrefs =
        copy(recognitionLanguage = EngineLanguages.normalize(languageTag), engineLanguages = emptyMap())

    /**
     * A recommendation is stored, chosen under other rules than the current
     * [EngineRecommender.RULES_VERSION] (or before rules had a version). A
     * phone that never got one has nothing to update: the setup guide
     * recommends when it runs.
     */
    val recommendationOutdated: Boolean
        get() = recommendedEngineId != null && recommendationRules != EngineRecommender.RULES_VERSION

    /**
     * The engine to store as the selection before the recommendation becomes
     * [newRecommendation], or null: a new recommendation never switches to
     * an engine that isn't downloaded. With nothing selected the
     * recommended engine runs, so the engine running now is pinned when it
     * is installed ([isInstalled]).
     */
    fun engineToPin(newRecommendation: String, isInstalled: (String) -> Boolean): String? {
        if (selectedEngineId != null) return null
        val running = activeEngine
        if (running.id == newRecommendation) return null
        return running.id.takeIf(isInstalled)
    }

    companion object {
        /** [recognitionLanguage]: follow the system language. */
        const val AUTO = ""

        /** The voice keyboard's choices, in the order Settings lists them; the first is the default. */
        val KEYBOARD_ENDS = listOf(
            EndOfDictation.TAP_STOP,
            EndOfDictation.SMART_TURN,
            EndOfDictation.PAUSE,
            EndOfDictation.END_WORD,
        )

        /** Other apps' choices, in the order Settings lists them; the first is the default. */
        val APPS_ENDS = listOf(EndOfDictation.SMART_TURN, EndOfDictation.PAUSE, EndOfDictation.END_WORD)

        private val DEFAULT_APPS_END = APPS_ENDS.first()

        /** A stored end mode (its enum name) if it is one of [allowed], else the first of them. */
        fun endFrom(stored: String?, allowed: List<EndOfDictation>): EndOfDictation =
            allowed.firstOrNull { it.name == stored } ?: allowed.first()

        /** The stored patience (its enum name), else Normal. */
        fun patienceFrom(stored: String?): Patience = Patience.entries.firstOrNull { it.name == stored } ?: Patience.NORMAL

        /** The stored end words (one per line), normalised; the default when none are stored. */
        fun endWordsFrom(stored: String?): List<String> =
            stored?.let { EndWords.normalize(it.lines()) }?.takeIf { it.isNotEmpty() } ?: EndWords.DEFAULT
    }
}

/** [SpeechPrefs] in the app's Preferences DataStore. */
@Singleton
class SpeechSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val prefs: Flow<SpeechPrefs> = dataStore.data.map { p ->
        SpeechPrefs(
            minSilenceSeconds = (p[MIN_SILENCE] ?: SessionOptions.DEFAULT_MIN_SILENCE_SECONDS)
                .coerceIn(SessionOptions.MIN_SILENCE_RANGE_START, SessionOptions.MIN_SILENCE_RANGE_END),
            keyboardEnd = SpeechPrefs.endFrom(p[KEYBOARD_END], SpeechPrefs.KEYBOARD_ENDS),
            appsEnd = SpeechPrefs.endFrom(p[APPS_END], SpeechPrefs.APPS_ENDS),
            // Only a choice made in Settings is stored (setAppsEnd); one we can't read isn't a choice.
            appsEndChosen = SpeechPrefs.APPS_ENDS.any { it.name == p[APPS_END] },
            patience = SpeechPrefs.patienceFrom(p[PATIENCE]),
            endWords = SpeechPrefs.endWordsFrom(p[END_WORDS]),
            keepModelLoaded = p[KEEP_MODEL_LOADED] ?: false,
            selectedEngineId = p[SELECTED_ENGINE],
            developerEngines = p[DEVELOPER_ENGINES] ?: false,
            recognitionLanguage = p[RECOGNITION_LANGUAGE] ?: SpeechPrefs.AUTO,
            engineLanguages = p.asMap().entries
                .filter { it.key.name.startsWith(ENGINE_LANGUAGE_PREFIX) }
                .associate { it.key.name.removePrefix(ENGINE_LANGUAGE_PREFIX) to it.value.toString() },
            deviceProbeMs = p[DEVICE_PROBE_MS]?.takeIf { it > 0 },
            deviceProbeThreads = p[DEVICE_PROBE_THREADS] ?: SpeedBudget.REFERENCE_THREADS,
            deviceTotalMemBytes = p[DEVICE_TOTAL_MEM]?.takeIf { it > 0 },
            tooSlowEngineIds = p[TOO_SLOW_ENGINES] ?: emptySet(),
            recommendedEngineId = p[RECOMMENDED_ENGINE],
            recommendationRules = p[RECOMMENDATION_RULES],
            ortTuningId = p[ORT_TUNING]?.takeIf { id -> OrtTuning.PRESETS.any { it.id == id } },
            otherEnginesExpanded = p[OTHER_ENGINES_EXPANDED] ?: false,
        )
    }

    suspend fun current(): SpeechPrefs = prefs.first()

    /** Only the slider calls this, when the user lets go of it: unmoved, the default applies. */
    suspend fun setMinSilenceSeconds(seconds: Float) {
        dataStore.edit {
            it[MIN_SILENCE] = seconds.coerceIn(SessionOptions.MIN_SILENCE_RANGE_START, SessionOptions.MIN_SILENCE_RANGE_END)
        }
    }

    /** [mode] is one of [SpeechPrefs.KEYBOARD_ENDS]. */
    suspend fun setKeyboardEnd(mode: EndOfDictation) {
        dataStore.edit { it[KEYBOARD_END] = mode.name }
    }

    /** [mode] is one of [SpeechPrefs.APPS_ENDS]; anything else reads back as the default. */
    suspend fun setAppsEnd(mode: EndOfDictation) {
        dataStore.edit { it[APPS_END] = mode.name }
    }

    suspend fun setPatience(patience: Patience) {
        dataStore.edit { it[PATIENCE] = patience.name }
    }

    /**
     * Store the end words, normalised ([EndWords.normalize]). An empty list is
     * never stored (End word mode must always have a word): the words stay
     * as they were.
     */
    suspend fun setEndWords(words: List<String>) {
        val normalized = EndWords.normalize(words)
        if (normalized.isEmpty()) return
        dataStore.edit { it[END_WORDS] = normalized.joinToString("\n") }
    }

    suspend fun setKeepModelLoaded(on: Boolean) {
        dataStore.edit { it[KEEP_MODEL_LOADED] = on }
    }

    /** Run [engineId] from the next session on. */
    suspend fun setSelectedEngine(engineId: String) {
        dataStore.edit { it[SELECTED_ENGINE] = engineId }
    }

    suspend fun setDeveloperEngines(on: Boolean) {
        dataStore.edit { it[DEVELOPER_ENGINES] = on }
    }

    /** [code]: an ISO 639 code from [EngineLanguages.userChoices], or [SpeechPrefs.AUTO]. */
    suspend fun setRecognitionLanguage(code: String) {
        dataStore.edit { it[RECOGNITION_LANGUAGE] = code }
    }

    /** [code] is a BCP-47 code from the engine's language options, or "" for auto-detect. */
    suspend fun setEngineLanguage(engineId: String, code: String) {
        dataStore.edit { it[stringPreferencesKey(ENGINE_LANGUAGE_PREFIX + engineId)] = code }
    }

    /** Store the CPU probe's result: [ms] with [threads] recognizer threads. */
    suspend fun setDeviceProbe(ms: Long, threads: Int) {
        dataStore.edit {
            it[DEVICE_PROBE_MS] = ms
            it[DEVICE_PROBE_THREADS] = threads
        }
    }

    /** The phone's RAM, for [SpeechPrefs.engineCosts] (#47). */
    suspend fun setDeviceTotalMem(bytes: Long) {
        dataStore.edit { it[DEVICE_TOTAL_MEM] = bytes }
    }

    /** [engineId] fell behind real time on this phone (#30). */
    suspend fun addTooSlowEngine(engineId: String) {
        dataStore.edit { it[TOO_SLOW_ENGINES] = (it[TOO_SLOW_ENGINES] ?: emptySet()) + engineId }
    }

    suspend fun clearTooSlowEngines() {
        dataStore.edit { it.remove(TOO_SLOW_ENGINES) }
    }

    /** [engineId] is this phone's recommendation under the current [EngineRecommender.RULES_VERSION]. */
    suspend fun setRecommendedEngine(engineId: String) {
        dataStore.edit {
            it[RECOMMENDED_ENGINE] = engineId
            it[RECOMMENDATION_RULES] = EngineRecommender.RULES_VERSION
        }
    }

    /** [id]: an [OrtTuning.id], or null for [OrtTuning.DEFAULT]. */
    suspend fun setOrtTuning(id: String?) {
        dataStore.edit { if (id == null) it.remove(ORT_TUNING) else it[ORT_TUNING] = id }
    }

    suspend fun setOtherEnginesExpanded(expanded: Boolean) {
        dataStore.edit { it[OTHER_ENGINES_EXPANDED] = expanded }
    }

    /**
     * Forget every stored engine id that is no longer in the catalog (#45:
     * engines that left the app): the selection, the recommendation, the
     * engines that fell behind and per-engine languages. Also drops
     * [NEMOTRON_CHUNK_MS], Settings → Developer's choice between Nemotron
     * 3.5's 560 ms and 1120 ms exports, which went with the 560 ms one
     * (#37), and [SELECTION_KEPT] (#89). True when the selection or the recommendation was one of them:
     * the caller then
     * recommends again, so what runs is chosen for this phone rather than
     * falling back to the default, which may not be downloaded or may not
     * fit the phone.
     */
    suspend fun forgetUnknownEngines(): Boolean {
        fun gone(id: String) = SttEngineCatalog.byId(id) == null
        var choiceGone = false
        dataStore.edit { p ->
            for (key in listOf(SELECTED_ENGINE, RECOMMENDED_ENGINE)) {
                if (p[key]?.let(::gone) == true) {
                    p.remove(key)
                    choiceGone = true
                }
            }
            p[TOO_SLOW_ENGINES]?.let { ids -> if (ids.any(::gone)) p[TOO_SLOW_ENGINES] = ids.filterNot(::gone).toSet() }
            p.asMap().keys
                .filter { it.name.startsWith(ENGINE_LANGUAGE_PREFIX) && gone(it.name.removePrefix(ENGINE_LANGUAGE_PREFIX)) }
                .forEach { p.remove(stringPreferencesKey(it.name)) }
            p.remove(NEMOTRON_CHUNK_MS)
            p.remove(SELECTION_KEPT)
        }
        return choiceGone
    }

    private companion object {
        /** No longer read or written: [forgetUnknownEngines] removes it from older installs (#37). */
        val NEMOTRON_CHUNK_MS = intPreferencesKey("nemotron_chunk_ms")

        /**
         * No longer read or written: [forgetUnknownEngines] removes it from older installs
         * (#89). It marked a developer engine selected while it was the recommendation
         * (#50); the recommender has picked only user engines since Qwen3-ASR became one.
         */
        val SELECTION_KEPT = booleanPreferencesKey("speech_engine_kept")
        val ORT_TUNING = stringPreferencesKey("ort_tuning")
        val OTHER_ENGINES_EXPANDED = booleanPreferencesKey("other_engines_expanded")
        val DEVICE_PROBE_MS = longPreferencesKey("device_probe_ms")
        val DEVICE_PROBE_THREADS = intPreferencesKey("device_probe_threads")
        val DEVICE_TOTAL_MEM = longPreferencesKey("device_total_mem")
        val RECOMMENDED_ENGINE = stringPreferencesKey("recommended_engine")
        val RECOMMENDATION_RULES = stringPreferencesKey("recommendation_rules")
        val TOO_SLOW_ENGINES = stringSetPreferencesKey("too_slow_engines")
        val SELECTED_ENGINE = stringPreferencesKey("speech_engine")
        val DEVELOPER_ENGINES = booleanPreferencesKey("developer_engines")
        val RECOGNITION_LANGUAGE = stringPreferencesKey("recognition_language")
        const val ENGINE_LANGUAGE_PREFIX = "engine_language_"
        val MIN_SILENCE = floatPreferencesKey("speech_min_silence_seconds")
        val KEYBOARD_END = stringPreferencesKey("speech_keyboard_end")
        val APPS_END = stringPreferencesKey("speech_apps_end")
        val PATIENCE = stringPreferencesKey("speech_smart_turn_patience")
        val END_WORDS = stringPreferencesKey("speech_end_words")
        val KEEP_MODEL_LOADED = booleanPreferencesKey("speech_keep_model_loaded")
    }
}
