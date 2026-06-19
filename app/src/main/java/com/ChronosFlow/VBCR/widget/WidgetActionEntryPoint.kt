package com.ChronosFlow.VBCR.widget

import com.ChronosFlow.VBCR.appfunctions.TimeBlockCompletionHandler
import com.ChronosFlow.VBCR.core.ai.ProactiveAssistGenerator
import com.ChronosFlow.VBCR.core.data.privacy.PrivacyPreferences
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteHabitByIdUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.GetChronosDayOverviewUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.RecordMedicationWidgetActionUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ToggleTaskCompletionUseCase
import com.ChronosFlow.VBCR.feature.focus.FocusWidgetCommandDispatcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetActionEntryPoint {
    fun dayOverviewUseCase(): GetChronosDayOverviewUseCase
    fun completeHabitByIdUseCase(): CompleteHabitByIdUseCase
    fun recordMedicationWidgetActionUseCase(): RecordMedicationWidgetActionUseCase
    fun toggleTaskCompletionUseCase(): ToggleTaskCompletionUseCase
    fun timeBlockRepository(): TimeBlockRepository
    fun timeBlockCompletionHandler(): TimeBlockCompletionHandler
    fun focusWidgetCommandDispatcher(): FocusWidgetCommandDispatcher
    fun privacyPreferences(): PrivacyPreferences
    fun wearDaySummaryBridge(): WearDaySummaryBridge
    fun wearThemeBridge(): WearThemeBridge
    fun wearLinkStatusStore(): WearLinkStatusStore
    fun proactiveAssistGenerator(): ProactiveAssistGenerator
}
