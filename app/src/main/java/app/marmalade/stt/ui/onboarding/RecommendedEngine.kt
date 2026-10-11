package app.marmalade.stt.ui.onboarding

import androidx.annotation.StringRes
import app.marmalade.stt.R
import app.marmalade.stt.install.EngineRecommender.Reason
import app.marmalade.stt.install.EngineRecommender.Recommendation
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.LanguageOptions
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.ui.screen.languageNameInText
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// The model step's engine (#27): the one EngineRecommender picks for this
// phone, and the line that says why (the C1 card, RecommendedEngineCard.kt).
// No Android types, so it is unit-tested (RecommendedEngineTest);
// OnboardingViewModel wires in SpeedCalibrator, ModelInstaller and
// SpeechSettings.

/**
 * Asks [recommend] once, then follows the recommended engine's install state
 * and selects it ([select]) when it finishes installing while the guide is
 * open. An engine already installed when the guide opens isn't selected: the
 * user may have picked another since.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecommendedEngine(
    scope: CoroutineScope,
    recommend: suspend () -> Recommendation,
    installState: (engineId: String) -> StateFlow<InstallState>,
    select: suspend (engineId: String) -> Unit,
) {
    private val _recommendation = MutableStateFlow<Recommendation?>(null)

    /** Null while the phone is checked (about a second, the first time). */
    val recommendation: StateFlow<Recommendation?> = _recommendation.asStateFlow()

    /** The recommended engine's install state; [InstallState.NotInstalled] until it is known. */
    val state: StateFlow<InstallState> = recommendation
        .flatMapLatest { rec -> rec?.let { installState(it.engine.id) } ?: flowOf(InstallState.NotInstalled) }
        .stateIn(scope, SharingStarted.Eagerly, InstallState.NotInstalled)

    init {
        scope.launch {
            val rec = recommend()
            _recommendation.value = rec
            finishedInstalling(installState(rec.engine.id)).collect { select(rec.engine.id) }
        }
    }
}

/**
 * Emits each time [states] turns [InstallState.Installed] after being
 * something else. The first state doesn't count: installed already is not
 * "finished installing".
 */
fun finishedInstalling(states: Flow<InstallState>): Flow<Unit> =
    states
        .map { it == InstallState.Installed }
        .distinctUntilChanged()
        .drop(1)
        .filter { it }
        .map { }

/** A string resource and its format arguments. */
data class StringWithArgs(@StringRes val res: Int, val args: List<String> = emptyList())

/**
 * The model card's one line of why, for [rec]: by its reason, naming the
 * phone's language in [locale], the UI's (rememberUiLocale).
 */
fun recommendationText(rec: Recommendation, locale: Locale): StringWithArgs {
    val language = languageNameInText(rec.language, locale)
    // EngineRecommender falls back to the English engines.
    if (!rec.languageCovered) {
        return StringWithArgs(R.string.onboarding_recommend_uncovered, listOf(language, languageNameInText("en", locale)))
    }
    return when (rec.reason) {
        Reason.BEST_FIT -> StringWithArgs(R.string.onboarding_recommend_best_fit, listOf(language))
        Reason.LIGHTER_FOR_PHONE -> StringWithArgs(R.string.onboarding_recommend_lighter)
        Reason.MAY_BE_SLOW -> StringWithArgs(R.string.onboarding_recommend_may_be_slow, listOf(language))
        Reason.LOW_MEMORY -> StringWithArgs(R.string.onboarding_recommend_low_memory, listOf(language))
    }
}

/**
 * How many languages [engine] tells apart by itself, for the model card's
 * "25 languages, detected automatically."; null for a one-language engine
 * (its name says which) and for one told the language by default.
 */
fun autoDetectedLanguages(engine: SttEngineDescriptor): Int? {
    val count = EngineLanguages.of(engine).size
    val detects = (engine.languageOptions?.default ?: LanguageOptions.AUTO) == LanguageOptions.AUTO
    return count.takeIf { it > 1 && detects }
}
