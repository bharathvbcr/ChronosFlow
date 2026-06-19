package com.ChronosFlow.VBCR.di

import com.ChronosFlow.VBCR.core.domain.wear.WearLinkStatusProvider
import com.ChronosFlow.VBCR.widget.WearLinkStatusProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the app-module Wear implementations to their domain-level interfaces. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {
    @Binds
    @Singleton
    abstract fun bindWearLinkStatusProvider(
        impl: WearLinkStatusProviderImpl
    ): WearLinkStatusProvider
}
