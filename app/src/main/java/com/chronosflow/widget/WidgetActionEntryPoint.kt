package com.chronosflow.widget

import com.chronosflow.core.data.assist.ProactiveAssistCache
import com.chronosflow.core.data.privacy.PrivacyPreferences
import com.chronosflow.core.domain.usecase.CompleteHabitByIdUseCase
import com.chronosflow.core.domain.usecase.GetChronosWidgetSummaryUseCase
import com.chronosflow.core.domain.usecase.RecordMedicationWidgetActionUseCase
import com.chronosflow.feature.focus.FocusWidgetCommandDispatcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetActionEntryPoint {
    fun widgetSummaryUseCase(): GetChronosWidgetSummaryUseCase
    fun completeHabitByIdUseCase(): CompleteHabitByIdUseCase
    fun recordMedicationWidgetActionUseCase(): RecordMedicationWidgetActionUseCase
    fun focusWidgetCommandDispatcher(): FocusWidgetCommandDispatcher
    fun privacyPreferences(): PrivacyPreferences
    fun proactiveAssistCache(): ProactiveAssistCache
}
