package app.marmalade.stt.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.marmalade.stt.speech.AndroidPhoneHardware
import app.marmalade.stt.speech.PhoneHardware
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// DataStore singleton — one instance per process via extension property.
// DataStore throws if two instances ever open the same file, so this is the
// only place the file is named; everything else injects DataStore<Preferences>.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "marmalade_stt_settings",
)

/**
 * App-wide Hilt bindings: the Preferences DataStore that app settings live
 * in (excluded from backup like everything else, data_extraction_rules.xml),
 * and the phone's RAM and speed probe for the speed calibrator.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.dataStore

    @Provides
    fun providePhoneHardware(hardware: AndroidPhoneHardware): PhoneHardware = hardware
}
