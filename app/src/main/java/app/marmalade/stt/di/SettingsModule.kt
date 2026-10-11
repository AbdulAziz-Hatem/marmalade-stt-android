package app.marmalade.stt.di

import app.marmalade.stt.data.DataStoreTextProcSettings
import app.marmalade.stt.data.TextProcSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Interface bindings for app settings. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindTextProcSettings(impl: DataStoreTextProcSettings): TextProcSettings
}
