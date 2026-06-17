package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.ChronosDayOverview
import com.chronosflow.core.domain.model.DayOverviewBlock
import com.chronosflow.core.domain.model.DayOverviewFocus
import com.chronosflow.core.domain.model.DayOverviewHabit
import com.chronosflow.core.domain.model.DayOverviewMedication
import com.chronosflow.core.domain.model.DayOverviewTask
import com.chronosflow.core.domain.model.FocusSessionState
import com.chronosflow.core.domain.model.MedicationDoseEventType
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.occupiesScheduleTime
import com.chronosflow.core.domain.model.WidgetFocusState
import com.chronosflow.core.domain.repository.FocusSessionRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Builds the [ChronosDayOverview] shared by every home-screen widget (Focus, Today's Schedule,
 * Tasks, Habits, Medication) and the Wear OS day-summary sync. Single-shot because Glance
 * widgets and Data Layer pushes render snapshots, not live streams.
 */
class GetChronosDayOverviewUseCase @Inject constructor(
    private val timeBlockRepository: TimeBlockRepository,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val medicationRepository: MedicationRepository,
    private val focusSessionRepository: FocusSessionRepository
) {
    suspend operator fun invoke(
        today: LocalDate = LocalDate.now(),
        nowMinuteOfDay: Int = LocalTime.now().let { it.hour * 60 + it.minute },
        now: Instant = Instant.now()
    ): ChronosDayOverview {
        val blocks = timeBlockRepository.getTimeBlocksByDate(today).first()
            .sortedBy { it.startMinuteOfDay }
            // Exclude all-day calendar notes (they'd otherwise read as the "current block" all
            // day), blocks the user already completed, and blocks whose planned window has
            // elapsed — so nothing finished or non-scheduling lingers as the widget/tile current block.
            .filter {
                it.occupiesScheduleTime() &&
                    it.actualEndMinuteOfDay == null &&
                    nowMinuteOfDay < it.startMinuteOfDay + it.durationMinutes
            }
            .map { block ->
                DayOverviewBlock(
                    id = block.id,
                    title = block.title,
                    startMinuteOfDay = block.startMinuteOfDay,
                    endMinuteOfDay = block.startMinuteOfDay + block.durationMinutes,
                    isCurrent = nowMinuteOfDay >= block.startMinuteOfDay,
                    category = block.category
                )
            }

        // Same ordering as the Tasks screen so the widget never disagrees with the app.
        val openTasks = taskRepository.getAllTasks().first()
            .filter { !it.isCompleted }
            .sortedWith(compareByDescending<Task> { it.priority }.thenBy { it.createdAt })
            .map { DayOverviewTask(id = it.id, title = it.title, priority = it.priority) }

        val habits = habitRepository.observeHabits().first()
            .filter { it.isActive }
            .map { habit ->
                DayOverviewHabit(
                    id = habit.id,
                    title = habit.title,
                    streakCount = habit.streakCount,
                    isDoneToday = habit.lastCompletedDate == today
                )
            }
            .sortedBy { it.isDoneToday }

        val medications = medicationRepository.observeMedicationPlans().first()
            .filter { it.isActive }
            .map { plan ->
                DayOverviewMedication(
                    id = plan.id,
                    name = plan.name,
                    doseLabel = "${plan.dosage} ${plan.unit}".trim(),
                    reminderMinuteOfDay = plan.reminderMinuteOfDay,
                    isTakenToday = plan.recentDoseEvents.any {
                        it.eventDate == today && it.type == MedicationDoseEventType.TAKEN
                    }
                )
            }
            .sortedWith(compareBy({ it.isTakenToday }, { it.reminderMinuteOfDay }))

        val focus = when (val session = focusSessionRepository.observeRecoverableSession().first()) {
            is FocusSessionState.Running -> DayOverviewFocus(
                state = WidgetFocusState.RUNNING,
                timeLeftSeconds = Duration.between(now, session.plannedEndAt)
                    .seconds.coerceAtLeast(0L).toInt()
            )
            is FocusSessionState.Paused -> DayOverviewFocus(
                state = WidgetFocusState.PAUSED,
                timeLeftSeconds = Duration.between(session.pausedAt, session.plannedEndAt)
                    .seconds.coerceAtLeast(0L).toInt()
            )
            else -> DayOverviewFocus()
        }

        return ChronosDayOverview(
            blocks = blocks,
            openTasks = openTasks,
            habits = habits,
            medications = medications,
            focus = focus
        )
    }
}
