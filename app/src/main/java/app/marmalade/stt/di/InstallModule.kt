package app.marmalade.stt.di

import android.content.Context
import app.marmalade.stt.install.DownloadKeepAlive
import app.marmalade.stt.install.EngineRelease
import app.marmalade.stt.install.HttpFetcher
import app.marmalade.stt.install.ModelDownloadService
import app.marmalade.stt.install.ModelFilesDir
import app.marmalade.stt.install.UrlHttpFetcher
import app.marmalade.stt.speech.SpeechEngineManager
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Bindings for the model installer (ARCHITECTURE.md D5): where models live and how they're fetched. */
@Module
@InstallIn(SingletonComponent::class)
object InstallModule {

    @Provides
    @Singleton
    fun provideModelFilesDir(@ApplicationContext context: Context): ModelFilesDir =
        ModelFilesDir { context.filesDir }

    @Provides
    @Singleton
    fun provideHttpFetcher(): HttpFetcher = UrlHttpFetcher()

    // The installer frees the loaded model before it deletes or replaces an
    // engine's files. Lazy breaks the cycle (the manager needs the installer).
    @Provides
    @Singleton
    fun provideEngineRelease(manager: Lazy<SpeechEngineManager>): EngineRelease =
        EngineRelease { engineId -> manager.get().release(engineId) }

    // A running download needs a foreground service, or Android 15+ cuts its
    // network when the screen turns off (#74).
    @Provides
    @Singleton
    fun provideDownloadKeepAlive(@ApplicationContext context: Context): DownloadKeepAlive =
        DownloadKeepAlive { ModelDownloadService.start(context) }
}
