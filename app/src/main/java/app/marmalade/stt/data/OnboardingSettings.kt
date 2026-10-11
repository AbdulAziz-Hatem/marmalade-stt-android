package app.marmalade.stt.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Whether the first-run setup guide has been finished (or skipped to its end),
 * and which step an unfinished guide was on.
 */
@Singleton
class OnboardingSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * False until [setDone]. An unreadable settings file reads as false, so
     * the worst case is seeing the setup guide again, never a crash at launch.
     */
    val done: Flow<Boolean> = prefs().map { it[DONE] ?: false }

    /**
     * The step name last saved by [setStep], or null once the guide is done:
     * a first run killed half-way (swiped away, or the process died in the
     * foreground) picks up where it was, but opening the guide again from
     * Settings starts at the beginning.
     */
    val resumeStep: Flow<String?> = prefs().map { if (it[DONE] == true) null else it[STEP] }

    suspend fun setDone() {
        dataStore.edit { it[DONE] = true }
    }

    suspend fun setStep(step: String) {
        dataStore.edit { it[STEP] = step }
    }

    private fun prefs(): Flow<Preferences> = dataStore.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Couldn't read the settings; showing the setup guide", e)
            emit(emptyPreferences())
        }

    private companion object {
        const val TAG = "OnboardingSettings"
        val DONE = booleanPreferencesKey("onboarding_done")
        val STEP = stringPreferencesKey("onboarding_step")
    }
}
