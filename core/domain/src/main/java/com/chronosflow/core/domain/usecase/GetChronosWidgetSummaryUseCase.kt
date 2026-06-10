package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.ChronosWidgetSummary
import com.chronosflow.core.domain.model.FocusSessionState
import com.chronosflow.core.domain.model.WidgetFocusState
import com.chronosflow.core.domain.repository.FocusSessionRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GetChronosWidgetSummaryUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val medicationRepository: MedicationRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val timeBlockRepository: TimeBlockRepository
) {
    suspend operator fun invoke(
        today: LocalDate = LocalDate.now(),
        nowMinuteOfDay: Int = LocalTime.now().let { it.hour * 60 + it.minute },
        now: Instant = Instant.now()
    ): ChronosWidgetSummary {
        val habit = habitRepository.observeHabits().first().firstOrNull { it.isActive }
        val medication = medicationRepository.observeMedicationPlans().first().firstOrNull { it.isActive }

        val (focusState, focusTimeLeftSeconds) = when (val session = focusSessionRepository.observeRecoverableSession().first()) {
            is FocusSessionState.Running ->
                WidgetFocusState.RUNNING to Duration.between(now, session.plannedEndAt).seconds.coerceAtLeast(0L).toInt()
            is FocusSessionState.Paused ->
                WidgetFocusState.PAUSED to Duration.between(session.pausedAt, session.plannedEndAt).seconds.coerceAtLeast(0L).toInt()
            else -> WidgetFocusState.IDLE to 0
        }

        val blocks = timeBlockRepository.getTimeBlocksByDate(today).first().sortedBy { it.startMinuteOfDay }
        val currentBlock = blocks.firstOrNull {
            nowMinuteOfDay >= it.startMinuteOfDay && nowMinuteOfDay < it.startMinuteOfDay + it.durationMinutes
        }
        val nextBlock = blocks.firstOrNull { it.startMinuteOfDay > nowMinuteOfDay }

        return ChronosWidgetSummary(
            habitId = habit?.id,
            habitTitle = habit?.title,
            medicationId = medication?.id,
            medicationName = medication?.name,
            focusState = focusState,
            focusTimeLeftSeconds = focusTimeLeftSeconds,
            currentBlockTitle = currentBlock?.title,
            nextBlockTitle = nextBlock?.title,
            nextBlockStartMinuteOfDay = nextBlock?.startMinuteOfDay
        )
    }
}
