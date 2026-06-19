package com.ChronosFlow.VBCR.core.notifications

import com.ChronosFlow.VBCR.core.domain.notifications.ScreenTimeNudgePresenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenTimeNotificationsModule {
    @Binds
    abstract fun bindScreenTimeNudgePresenter(impl: ScreenTimeNudgeNotifier): ScreenTimeNudgePresenter
}
