package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.data.privacy.PrivacyPreferences
import com.ChronosFlow.VBCR.core.domain.model.FocusSession
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SemanticPlanningCorpusRefresher @Inject constructor(
    private val semanticIndex: SemanticPlanningIndex,
    private val appSearchBridge: SemanticAppSearchBridge,
    private val privacyPreferences: PrivacyPreferences,
    private val timeBlockRepository: TimeBlockRepository,
    private val reviewRepository: ReviewRepository,
    private val habitRepository: HabitRepository,
    private val medicationRepository: MedicationRepository,
    private val taskRepository: TaskRepository,
    private val focusSessionRepository: FocusSessionRepository
) {
    suspend fun refresh(
        redactMedicationNames: Boolean? = null,
        clock: Clock = Clock.systemDefaultZone()
    ): Boolean {
        val today = LocalDate.now(clock)
        val zoneId = clock.zone
        val shouldRedactMedicationNames = redactMedicationNames
            ?: privacyPreferences.redactCommandPaletteHistory()
        val focusSessions = focusSessionRepository.observeRecoverableSession().first()
            ?.toSemanticFocusSessions(today = today, zoneId = zoneId)
            .orEmpty()

        semanticIndex.rebuild(
            reviews = listOfNotNull(reviewRepository.observeDailyReview(today).first()),
            blocks = timeBlockRepository.getTimeBlocksByDate(today).first(),
            tasks = taskRepository.getAllTasks().first(),
            habits = habitRepository.observeHabits().first(),
            medications = medicationRepository.observeMedicationPlans().first(),
            focusSessions = focusSessions,
            redactMedicationNames = shouldRedactMedicationNames
        )
        return appSearchBridge.syncDocuments()
    }
}

private fun FocusSessionState.toSemanticFocusSessions(
    today: LocalDate,
    zoneId: ZoneId
): List<FocusSession> = when (this) {
    is FocusSessionState.Running -> listOf(
        FocusSession(
            id = sessionId,
            blockId = blockId,
            date = startedAt.toLocalDate(zoneId),
            plannedDurationMinutes = plannedDurationMinutes(startedAt, plannedEndAt),
            actualDurationMinutes = null,
            interruptions = 0,
            startedAt = startedAt,
            completedAt = null,
            notes = null,
            isCompleted = false
        )
    )
    is FocusSessionState.Paused -> listOf(
        FocusSession(
            id = sessionId,
            blockId = blockId,
            date = startedAt.toLocalDate(zoneId),
            plannedDurationMinutes = plannedDurationMinutes(startedAt, plannedEndAt),
            actualDurationMinutes = null,
            interruptions = 0,
            startedAt = startedAt,
            completedAt = null,
            notes = null,
            isCompleted = false
        )
    )
    is FocusSessionState.ServiceKilledRecoverable -> listOf(
        FocusSession(
            id = sessionId,
            blockId = null,
            date = today,
            plannedDurationMinutes = 0,
            actualDurationMinutes = null,
            interruptions = 0,
            startedAt = null,
            completedAt = null,
            notes = null,
            isCompleted = false
        )
    )
    else -> emptyList()
}

private fun Instant.toLocalDate(zoneId: ZoneId): LocalDate =
    atZone(zoneId).toLocalDate()

private fun plannedDurationMinutes(
    startedAt: Instant,
    plannedEndAt: Instant
): Int = Duration.between(startedAt, plannedEndAt)
    .toMinutes()
    .coerceAtLeast(0)
    .coerceAtMost(Int.MAX_VALUE.toLong())
    .toInt()
