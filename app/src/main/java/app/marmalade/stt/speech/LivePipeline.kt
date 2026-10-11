package app.marmalade.stt.speech

import app.marmalade.stt.data.TextProcSettings
import app.marmalade.stt.textproc.TextPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The `TextPipeline` for the user's current cleanup settings, rebuilt each
 * time [TextProcSettings.config] emits a new config. The voice keyboard, the
 * speech-input activity and the RecognitionService each hold one for as long
 * as they live, in their own [scope].
 */
class LivePipeline(scope: CoroutineScope, settings: TextProcSettings) {

    private val latest: StateFlow<TextPipeline?> = settings.config
        .distinctUntilChanged()
        .map { TextPipeline(it) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The pipeline for the current settings. Waits only until the settings are first read. */
    suspend fun get(): TextPipeline = latest.filterNotNull().first()

    @Volatile
    private var overridden: TextPipeline? = null

    /**
     * The pipeline for the current settings, cleaning up in [language]
     * instead of the settings' language (a caller's EXTRA_LANGUAGE). The last
     * one is kept, so a request doesn't rebuild it for every event.
     */
    suspend fun get(language: String): TextPipeline {
        val current = get()
        if (current.config.language == language) return current
        val config = current.config.copy(language = language)
        overridden?.takeIf { it.config == config }?.let { return it }
        return TextPipeline(config).also { overridden = it }
    }
}
