package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.data.focus.ManualMissedBlockRegistry
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSource
import com.ChronosFlow.VBCR.core.ai.CompanionTrendContext
import com.ChronosFlow.VBCR.core.ai.EnergyCorrelationEngine
import com.ChronosFlow.VBCR.core.ai.EnergyInsight
import com.ChronosFlow.VBCR.core.ai.InsightRecommendation
import com.ChronosFlow.VBCR.core.ai.DeepWorkAssistPlanner
import com.ChronosFlow.VBCR.core.ai.InsightsRecommendationsPlanner
import com.ChronosFlow.VBCR.core.ai.ReviewAssistPlanner
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.ai.genai.refreshAssistUiSnapshot
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightSeverity
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.MoodEnergyRepository
import com.ChronosFlow.VBCR.core.domain.planner.DailyReviewCalculator
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.domain.usecase.AdvanceRecurringTaskOccurrenceUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteTaskOccurrenceUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteDailyReviewUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteTimeBlockUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.LogActualTimeUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveHabitCompletionTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveMedicationAdherenceTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveMoodEnergyTrendsUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.SyncRecurringTaskAlarmsUseCase
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduleResult
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
import com.ChronosFlow.VBCR.feature.daydial.DailyReview
import com.ChronosFlow.VBCR.feature.daydial.model.InsightsPeriod
import com.ChronosFlow.VBCR.feature.daydial.model.InsightsPeriodSummary
import com.ChronosFlow.VBCR.feature.daydial.ui.insightCategoryBreakdownRowsFromBlocks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialReviewDelegate @Inject constructor(
    private val repository: TimeBlockRepository,
    private val reviewRepository: ReviewRepository,
    private val moodEnergyRepository: MoodEnergyRepository,
    private val habitRepository: HabitRepository,
    private val medicationRepository: MedicationRepository,
    private val taskRepository: TaskRepository,
    private val taskScheduleRepository: TaskScheduleRepository,
    private val completeDailyReviewUseCase: CompleteDailyReviewUseCase,
    private val completeTaskOccurrenceUseCase: CompleteTaskOccurrenceUseCase,
    private val logActualTimeUseCase: LogActualTimeUseCase,
    private val syncRecurringTaskAlarmsUseCase: SyncRecurringTaskAlarmsUseCase,
    private val alarmScheduler: AlarmScheduler,
    private val alarmRequestRepository: AlarmRequestRepository,
    private val energyCorrelationEngine: EnergyCorrelationEngine,
    private val insightsRecommendationsPlanner: InsightsRecommendationsPlanner,
    private val deepWorkAssistPlanner: DeepWorkAssistPlanner,
    private val genAiAssistCoordinator: GenAiAssistCoordinator,
    private val reviewAssistPlanner: ReviewAssistPlanner,
    private val manualMissedBlockRegistry: ManualMissedBlockRegistry,
    private val plannerService: PlannerService = PlannerService(repository),
    // Shared with the agent-facing completeTimeBlock AppFunction so both completion paths stay identical.
    private val completeTimeBlockUseCase: CompleteTimeBlockUseCase =
        CompleteTimeBlockUseCase(plannerService, reviewRepository),
    private val advanceRecurringTaskOccurrenceUseCase: AdvanceRecurringTaskOccurrenceUseCase =
        AdvanceRecurringTaskOccurrenceUseCase(taskRepository, taskScheduleRepository, completeTaskOccurrenceUseCase),
    private val dailyReviewCalculator: DailyReviewCalculator = DailyReviewCalculator(),
    // Trend derivation lives in these use cases; defaults keep manual (test) construction simple while
    // Hilt injects the shared instances.
    private val observeMoodEnergyTrendsUseCase: ObserveMoodEnergyTrendsUseCase =
        ObserveMoodEnergyTrendsUseCase(moodEnergyRepository),
    private val observeHabitCompletionTrendUseCase: ObserveHabitCompletionTrendUseCase =
        ObserveHabitCompletionTrendUseCase(habitRepository),
    private val observeMedicationAdherenceTrendUseCase: ObserveMedicationAdherenceTrendUseCase =
        ObserveMedicationAdherenceTrendUseCase(medicationRepository)
) {
    fun missedBlocks(
        scope: CoroutineScope,
        selectedDate: StateFlow<LocalDate>
    ) = selectedDate.flatMapLatest { date ->
        combine(
            repository.getTimeBlocksByDate(date),
            manualMissedBlockRegistry.ids
        ) { blocks, entries ->
            val manual = manualMissedBlockRegistry.missedIdsForDate(date, entries)
            blocks.filter { it.id in manual }
        }
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun dailyReview(
        scope: CoroutineScope,
        selectedDate: StateFlow<LocalDate>,
        timeBlocksDomain: StateFlow<List<TimeBlock>>
    ) = selectedDate.flatMapLatest { date ->
        reviewRepository.observeActualTimeSegments(date)
    }.let { actualTimeSegments ->
        combine(selectedDate, timeBlocksDomain, actualTimeSegments) { date, blocks, actualSegments ->
            val summary = dailyReviewCalculator.calculate(
                date = date,
                plannedBlocks = blocks,
                actualSegments = actualSegments.ifEmpty { blocks.mapNotNull { toLegacyActualSegmentOrNull(it) } }
            )
            DailyReview(
                plannedMinutes = summary.plannedMinutes,
                actualMinutes = summary.actualMinutes,
                missedMinutes = summary.missedMinutes,
                completedBlocks = summary.completedBlockCount
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), DailyReview(0, 0, 0, 0))

    fun reviewInsights(
        scope: CoroutineScope,
        selectedDate: StateFlow<LocalDate>
    ): StateFlow<List<ReviewInsight>> = selectedDate.flatMapLatest { date ->
        reviewRepository.observeDailyReview(date).map { review -> review?.insights.orEmpty() }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun refreshInsightsTab(date: LocalDate): InsightsTabRefreshResult {
        persistRefreshedInsights(date)
        val blocks = repository.getTimeBlocksByDate(date).first()
        val summary = currentReviewSummary(date, blocks)
        val snapshot = runCatching { genAiAssistCoordinator.refreshAssistUiSnapshot() }.getOrNull()
        // Trends are additive context: a fetch failure degrades to today-only.
        val trends = runCatching { companionTrendContext(date) }.getOrDefault(CompanionTrendContext())
        val recommendations = insightsRecommendationsPlanner.suggest(
            summary = summary,
            insights = summary.insights,
            trends = trends
        )
        val digest = runCatching { reviewAssistPlanner.suggestDigest(summary.insights) }.getOrNull()
        return InsightsTabRefreshResult(
            reviewInsights = summary.insights,
            recommendations = recommendations,
            assistSnapshot = snapshot,
            digest = digest
        )
    }

    fun localInsightRecommendations(
        summary: DailyReviewSummary?,
        insights: List<ReviewInsight>
    ): List<InsightRecommendation> = insightsRecommendationsPlanner.localRecommendations(summary, insights)

    private suspend fun companionTrendContext(today: LocalDate): CompanionTrendContext =
        CompanionTrendContext(
            moodEnergyTrends = observeMoodEnergyTrendsUseCase(today = today).first(),
            habitCompletion = observeHabitCompletionTrendUseCase(today = today).first(),
            medicationAdherence = observeMedicationAdherenceTrendUseCase(today = today).first()
        )

    /**
     * Live execution metrics across the trailing window of [period] ending at the selected date.
     * Each day in the window is scored with the same [DailyReviewCalculator] the day view uses and
     * re-aggregated whenever any day's blocks or actual-time segments change, so weekly/monthly
     * rollups update in real time and stay consistent with the per-day numbers. Emits null for the
     * DAY period (callers fall back to the live selected-day data) and a loading summary while a
     * freshly selected window is first read.
     */
    fun observePeriodInsights(
        scope: CoroutineScope,
        selectedDate: StateFlow<LocalDate>,
        period: StateFlow<InsightsPeriod>
    ): StateFlow<InsightsPeriodSummary?> =
        combine(selectedDate, period) { date, activePeriod -> date to activePeriod }
            .flatMapLatest { (date, activePeriod) ->
                if (activePeriod == InsightsPeriod.DAY) {
                    flowOf<InsightsPeriodSummary?>(null)
                } else {
                    periodSummaryFlow(date, activePeriod)
                }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    private fun periodSummaryFlow(
        date: LocalDate,
        period: InsightsPeriod
    ): Flow<InsightsPeriodSummary> {
        val dayFlows = period.dateRange(date).map { day ->
            combine(
                repository.getTimeBlocksByDate(day),
                reviewRepository.observeActualTimeSegments(day)
            ) { blocks, segments ->
                val effectiveSegments = segments
                    .ifEmpty { blocks.mapNotNull { toLegacyActualSegmentOrNull(it) } }
                PeriodDayAggregate(
                    summary = dailyReviewCalculator.calculate(
                        date = day,
                        plannedBlocks = blocks,
                        actualSegments = effectiveSegments
                    ),
                    blocks = blocks
                )
            }
        }
        return combine(dayFlows) { days -> aggregatePeriod(days.toList()) }
            .onStart { emit(InsightsPeriodSummary.Loading) }
    }

    private fun aggregatePeriod(days: List<PeriodDayAggregate>): InsightsPeriodSummary {
        var planned = 0
        var actual = 0
        var missed = 0
        var missedCount = 0
        var completed = 0
        val allBlocks = mutableListOf<TimeBlock>()
        days.forEach { day ->
            planned += day.summary.plannedMinutes
            actual += day.summary.actualMinutes
            missed += day.summary.missedMinutes
            missedCount += day.summary.missedBlockCount
            completed += day.summary.completedBlockCount
            allBlocks += day.blocks
        }
        return InsightsPeriodSummary(
            review = DailyReview(
                plannedMinutes = planned,
                actualMinutes = actual,
                missedMinutes = missed,
                completedBlocks = completed
            ),
            missedCount = missedCount,
            categoryRows = insightCategoryBreakdownRowsFromBlocks(allBlocks),
            isLoading = false
        )
    }

    private suspend fun persistRefreshedInsights(date: LocalDate) {
        val blocks = repository.getTimeBlocksByDate(date).first()
        val summary = currentReviewSummary(date, blocks)
        completeDailyReviewUseCase(summary)
    }

    fun markCurrentBlockMissed(
        scope: CoroutineScope,
        blockId: String,
        missed: Boolean
    ) {
        scope.launch {
            val blockDate = repository.getTimeBlockById(blockId)?.date ?: LocalDate.now()
            if (missed) {
                manualMissedBlockRegistry.markMissed(blockId, blockDate)
            } else {
                manualMissedBlockRegistry.clearMissed(blockId, blockDate)
            }
        }
        if (!missed) {
            scope.launch {
                val source = repository.getTimeBlockById(blockId) ?: return@launch
                val cleared = source.copy(
                    actualStartMinuteOfDay = null,
                    actualEndMinuteOfDay = null,
                    updatedAt = Instant.now()
                )
                repository.saveTimeBlock(cleared)
            }
        }
    }

    fun removeMissedBlock(blockId: String, date: LocalDate) {
        manualMissedBlockRegistry.clearMissed(blockId, date)
    }

    fun markBlockComplete(scope: CoroutineScope, blockId: String) {
        scope.launch {
            val source = repository.getTimeBlockById(blockId) ?: return@launch
            applyBlockCompletion(source)
        }
    }

    /**
     * Idempotently records [block] as completed: fills its planned window as actual time, advances
     * any linked recurring task, and clears a stale missed flag. A block that already has actual
     * time is left untouched (only the missed flag is cleared) so re-completing never appends a
     * duplicate actual-time segment — segments are keyed by random id and are not deduped on insert.
     */
    private suspend fun applyBlockCompletion(block: TimeBlock) {
        // Shared idempotent completion (also used by the agent completeTimeBlock AppFunction):
        // logs the planned window as actual time, or no-ops if the block is already complete.
        if (completeTimeBlockUseCase(block)) {
            completeRecurringTaskOccurrence(block)
        }
        manualMissedBlockRegistry.clearMissed(block.id, block.date)
    }

    fun logActualRange(
        scope: CoroutineScope,
        blockId: String,
        actualStartMinute: Int,
        actualEndMinute: Int
    ) {
        scope.launch {
            val block = repository.getTimeBlockById(blockId) ?: return@launch
            plannerService.logActualWindow(block.id, actualStartMinute, actualEndMinute)
            logActualTimeUseCase(block.toActualSegment(actualStartMinute, actualEndMinute))
        }
    }

    private suspend fun completeRecurringTaskOccurrence(block: TimeBlock) {
        // Schedule advancement is shared with the agent path; this layer adds alarm rescheduling.
        val (task, updatedSchedule) = advanceRecurringTaskOccurrenceUseCase(block) ?: return
        syncRecurringTaskAlarmsUseCase(task, updatedSchedule)
        schedulePersistedTaskAlarms(task.id)
    }

    private suspend fun schedulePersistedTaskAlarms(taskId: String) {
        alarmScheduler.cancelAlarm("task:$taskId")
        val matchingRequests = alarmRequestRepository
            .observeRequestsByType(AlarmRequestType.URGENT_TASK)
            .first()
            .filter { request ->
                (request.blockId == taskId || request.id == "task:$taskId" || request.id.startsWith("task:$taskId:")) &&
                    request.deliveryState != AlarmDeliveryState.CANCELLED
            }
        matchingRequests.forEach { request ->
            val result = alarmScheduler.scheduleAlarmRequest(request)
            alarmRequestRepository.saveAlarmRequest(request.withScheduleResult(result))
        }
    }

    fun endDayReview(
        scope: CoroutineScope,
        date: LocalDate,
        markCompleted: List<String> = emptyList(),
        markMissed: List<String> = emptyList()
    ) {
        scope.launch {
            val blocks = repository.getTimeBlocksByDate(date).first()
            val byId = blocks.associateBy { it.id }
            markCompleted.forEach { blockId ->
                val source = byId[blockId] ?: return@forEach
                applyBlockCompletion(source)
            }
            markMissed.forEach { blockId ->
                manualMissedBlockRegistry.markMissed(blockId, date)
            }

            val refreshedBlocks = repository.getTimeBlocksByDate(date).first()
            val segments = reviewRepository.observeActualTimeSegments(date).first()
                .ifEmpty { refreshedBlocks.mapNotNull { toLegacyActualSegmentOrNull(it) } }
            val baseSummary = dailyReviewCalculator.calculate(
                date = date,
                plannedBlocks = refreshedBlocks,
                actualSegments = segments
            )
            val mergedInsights = mergeInsights(
                date = date,
                existing = baseSummary.insights,
                energyInsights = buildEnergyInsights(date, refreshedBlocks, segments),
                plannerInsights = buildPlannerInsights(date, refreshedBlocks)
            )
            completeDailyReviewUseCase(baseSummary.copy(insights = mergedInsights))
        }
    }

    suspend fun energyInsights(date: LocalDate, blocks: List<TimeBlock>): List<EnergyInsight> {
        val segments = reviewRepository.observeActualTimeSegments(date).first()
            .ifEmpty { blocks.mapNotNull { toLegacyActualSegmentOrNull(it) } }
        return buildEnergyInsights(date, blocks, segments)
    }

    suspend fun currentReviewSummary(date: LocalDate, blocks: List<TimeBlock>): DailyReviewSummary {
        val persisted = reviewRepository.observeDailyReview(date).first()
        if (persisted != null) {
            return persisted.copy(
                insights = mergeInsights(
                    date = date,
                    existing = persisted.insights,
                    energyInsights = buildEnergyInsights(
                        date = date,
                        blocks = blocks,
                        segments = reviewRepository.observeActualTimeSegments(date).first()
                            .ifEmpty { blocks.mapNotNull { toLegacyActualSegmentOrNull(it) } }
                    ),
                    plannerInsights = buildPlannerInsights(date, blocks)
                )
            )
        }
        val actualSegments = reviewRepository.observeActualTimeSegments(date).first()
            .ifEmpty { blocks.mapNotNull { toLegacyActualSegmentOrNull(it) } }
        val base = dailyReviewCalculator.calculate(
            date = date,
            plannedBlocks = blocks,
            actualSegments = actualSegments
        )
        return base.copy(
            insights = mergeInsights(
                date = date,
                existing = base.insights,
                energyInsights = buildEnergyInsights(date, blocks, actualSegments),
                plannerInsights = buildPlannerInsights(date, blocks)
            )
        )
    }

    private fun TimeBlock.toActualSegment(startMinute: Int, endMinute: Int): ActualTimeSegment {
        val zone = ZoneId.systemDefault()
        val normalizedStart = ((startMinute % 1440) + 1440) % 1440
        val normalizedEnd = ((endMinute % 1440) + 1440) % 1440
        val start = date.atStartOfDay(zone).plusMinutes(normalizedStart.toLong()).toInstant()
        val endDate = if (endMinute > startMinute || normalizedEnd > normalizedStart) date else date.plusDays(1)
        val end = endDate.atStartOfDay(zone).plusMinutes(normalizedEnd.toLong()).toInstant()
        return ActualTimeSegment(
            id = UUID.randomUUID().toString(),
            blockId = id,
            date = date,
            startInstant = start,
            endInstant = end,
            source = ActualTimeSource.FOCUS_SESSION,
            confidence = 1f
        )
    }

    private suspend fun buildEnergyInsights(
        date: LocalDate,
        blocks: List<TimeBlock>,
        segments: List<ActualTimeSegment>
    ): List<EnergyInsight> {
        val checkIns = moodEnergyRepository.getForDateRange(date.minusDays(14), date)
        val plannerSignals = plannerSignals()
        return energyCorrelationEngine.analyzeWithAssist(
            blocks = blocks,
            actualSegments = segments,
            focusSessions = emptyList(),
            checkIns = checkIns,
            habitCompletionRate = plannerSignals.habitCompletionRate,
            medicationAdherenceRate = plannerSignals.medicationAdherenceRate,
            date = date
        )
    }

    private fun mergeInsights(
        date: LocalDate,
        existing: List<ReviewInsight>,
        energyInsights: List<EnergyInsight>,
        plannerInsights: List<ReviewInsight>
    ): List<ReviewInsight> {
        val converted = energyCorrelationEngine.toReviewInsights(energyInsights, date) + plannerInsights
        val existingIds = existing.map { it.id }.toSet()
        return existing + converted.filterNot { it.id in existingIds }
    }

    private suspend fun buildPlannerInsights(date: LocalDate, blocks: List<TimeBlock>): List<ReviewInsight> {
        val signals = plannerSignals()
        val checkIns = moodEnergyRepository.getForDateRange(date.minusDays(14), date)
        return buildList {
            deepWorkAssistPlanner.suggestInsight(blocks = blocks, checkIns = checkIns, date = date)?.let { add(it) }
            if (signals.medicationAdherenceRate in 0f..0.79f) {
                add(
                    ReviewInsight(
                        id = "medication-adherence-$date",
                        type = ReviewInsightType.MEDICATION_ADHERENCE,
                        title = "Medication adherence slipped",
                        detail = "Recent medication adherence is ${(signals.medicationAdherenceRate * 100).toInt()}%.",
                        severity = ReviewInsightSeverity.WARNING
                    )
                )
            }
            if (signals.refillSoonCount > 0) {
                add(
                    ReviewInsight(
                        id = "medication-refill-$date",
                        type = ReviewInsightType.MEDICATION_PATTERN,
                        title = "Refill follow-up needed",
                        detail = "${signals.refillSoonCount} medication plan(s) are close to refill threshold.",
                        severity = ReviewInsightSeverity.INFO
                    )
                )
            }
            if (signals.habitCompletionRate in 0f..0.64f) {
                add(
                    ReviewInsight(
                        id = "habit-window-$date",
                        type = ReviewInsightType.HABIT_WINDOW,
                        title = "Habit windows are being missed",
                        detail = "Recent habit adherence is ${(signals.habitCompletionRate * 100).toInt()}%. Consider tightening or shifting windows.",
                        severity = ReviewInsightSeverity.WARNING
                    )
                )
            }
            signals.strongestHabitStreak?.takeIf { it.second >= 3 }?.let { (title, streak) ->
                add(
                    ReviewInsight(
                        id = "habit-streak-$date",
                        type = ReviewInsightType.HABIT_CORRELATION,
                        title = "$title is building momentum",
                        detail = "Current streak is $streak day(s), making it a strong anchor for the day plan.",
                        severity = ReviewInsightSeverity.INFO
                    )
                )
            }
        }
    }

    private suspend fun plannerSignals(): PlannerReviewSignals {
        val habits = habitRepository.observeHabits().first().filter { it.isActive }
        val medications = medicationRepository.observeMedicationPlans().first().filter { it.isActive }
        val habitRate = habits.map { it.analytics.adherenceRate }.average().toFloatOrZero()
        val medicationRate = medications.map { it.analytics.adherenceRate }.average().toFloatOrZero()
        val strongestHabit = habits.maxByOrNull { it.analytics.currentStreak }
            ?.let { it.title to it.analytics.currentStreak }
        return PlannerReviewSignals(
            habitCompletionRate = habitRate,
            medicationAdherenceRate = medicationRate,
            refillSoonCount = medications.count { it.safetyProfile?.refillSoon == true },
            strongestHabitStreak = strongestHabit
        )
    }

    private fun toLegacyActualSegmentOrNull(block: TimeBlock): ActualTimeSegment? {
        val start = block.actualStartMinuteOfDay ?: return null
        val end = block.actualEndMinuteOfDay ?: return null
        val startInstant = block.date.atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(start.toLong())
            .toInstant()
        val duration = ((end - start + 1440) % 1440).let { if (it == 0) 1440 else it }
        return ActualTimeSegment(
            id = "legacy-${block.id}-$start-$end",
            blockId = block.id,
            date = block.date,
            startInstant = startInstant,
            endInstant = startInstant.plus(duration.toLong(), ChronoUnit.MINUTES),
            source = ActualTimeSource.MANUAL_ENTRY,
            confidence = 1f
        )
    }

    private fun AlarmRequest.withScheduleResult(result: AlarmScheduleResult): AlarmRequest {
        val now = Instant.now()
        val (reliability, deliveryState, failureReason) = when (result) {
            is AlarmScheduleResult.Scheduled -> if (result.exact) {
                Triple(AlarmReliability.EXACT, AlarmDeliveryState.SCHEDULED, null)
            } else {
                Triple(
                    AlarmReliability.DEGRADED_WINDOW,
                    AlarmDeliveryState.DEGRADED,
                    "Exact alarm permission unavailable; scheduled with fallback window"
                )
            }
            is AlarmScheduleResult.ExactDenied -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                "Exact alarm permission denied"
            )
            is AlarmScheduleResult.PermissionDenied -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                "Notification permission denied"
            )
            is AlarmScheduleResult.Skipped -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                result.reason
            )
        }
        return copy(
            reliability = reliability,
            deliveryState = deliveryState,
            updatedAt = now,
            failureReason = failureReason
        )
    }
}

private data class PeriodDayAggregate(
    val summary: DailyReviewSummary,
    val blocks: List<TimeBlock>
)

private data class PlannerReviewSignals(
    val habitCompletionRate: Float,
    val medicationAdherenceRate: Float,
    val refillSoonCount: Int,
    val strongestHabitStreak: Pair<String, Int>?
)

private fun Double.toFloatOrZero(): Float = if (isNaN()) 0f else toFloat()
