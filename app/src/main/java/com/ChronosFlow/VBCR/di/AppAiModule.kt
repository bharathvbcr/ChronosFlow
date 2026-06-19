package com.ChronosFlow.VBCR.di

import android.content.Context
import com.ChronosFlow.VBCR.appfunctions.ChronosFeatureFlagsSource
import com.ChronosFlow.VBCR.core.ai.genai.AppForegroundGate
import com.ChronosFlow.VBCR.core.ai.genai.ProcessLifecycleAppForegroundGate
import com.ChronosFlow.VBCR.core.ui.settings.readChronosUiSettingsSnapshotFromDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppAiModule {
    @Binds
    @Singleton
    abstract fun bindAppForegroundGate(impl: ProcessLifecycleAppForegroundGate): AppForegroundGate

    companion object {
        @Provides
        @Singleton
        fun provideFeatureFlagsSource(
            @ApplicationContext context: Context
        ): ChronosFeatureFlagsSource = ChronosFeatureFlagsSource {
            context.readChronosUiSettingsSnapshotFromDataStore().featureFlags
        }
    }
}
