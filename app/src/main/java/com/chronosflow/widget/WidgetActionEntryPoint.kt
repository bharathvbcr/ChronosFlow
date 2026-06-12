package com.chronosflow.widget

import com.chronosflow.core.ai.ProactiveAssistGenerator
import com.chronosflow.core.data.privacy.PrivacyPreferences
import com.chronosflow.core.domain.usecase.CompleteHabitByIdUseCase
import com.chronosflow.core.domain.usecase.GetChronosDayOverviewUseCase
import com.chronosflow.core.domain.usecase.RecordMedicationWidgetActionUseCase
import com.chronosflow.core.domain.usecase.ToggleTaskCompletionUseCase
import com.chronosflow.feature.focus.FocusWidgetCommandDispatcher
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
    fun focusWidgetCommandDispatcher(): FocusWidgetCommandDispatcher
    fun privacyPreferences(): PrivacyPreferences
    fun wearDaySummaryBridge(): WearDaySummaryBridge
    fun wearThemeBridge(): WearThemeBridge
    fun proactiveAssistGenerator(): ProactiveAssistGenerator
}
