package com.ChronosFlow.VBCR.core.ai.genai

import com.ChronosFlow.VBCR.core.ai.ProposedSuggestionBlock
import com.ChronosFlow.VBCR.core.ai.StructuredDayPlanSuggestion
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightSeverity
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal object LocalPlanningHeuristics {
    fun generateIdealDayPlan(
        packageName: String,
        userPreferences: String,
        date: LocalDate,
        currentTimeZone: String,
        pendingTasks: List<Task> = emptyList()
    ): StructuredDayPlanSuggestion {
        val todayLabel = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        val normalizedPreferences = userPreferences.lowercase(Locale.getDefault())
        val wantsRecovery = listOf("recovery", "break", "low energy", "tired").any { it in normalizedPreferences }
        val wantsStudy = listOf("study", "read", "exam", "learn").any { it in normalizedPreferences }
        val wantsExercise = listOf("workout", "exercise", "walk", "run").any { it in normalizedPreferences }
        val topTasks = selectTopTasks(pendingTasks, date, currentTimeZone, limit = 2)

        val start = LocalTime.of(if (wantsRecovery) 9 else 8, 0)
        val blocks = buildList {
            add(
                suggestion(
                    title = if (wantsRecovery) "Slow start and planning" else "Morning planning",
                    category = "PLANNING",
                    startMinuteOfDay = start.hour * 60,
                    durationMinutes = if (wantsRecovery) 45 else 30,
                    flexibility = BlockFlexibility.MOVABLE,
                    timezone = currentTimeZone
                )
            )
            // The user's own work beats canned placeholders: the deep-work and
            // admin slots take the top pending tasks when any exist.
            add(
                suggestion(
                    title = topTasks.getOrNull(0)?.title
                        ?: if (wantsStudy) "Deep study block" else "Deep work block",
                    category = if (wantsStudy) "STUDY" else "WORK",
                    startMinuteOfDay = start.hour * 60 + if (wantsRecovery) 60 else 45,
                    durationMinutes = topTasks.getOrNull(0)?.preferredDurationMinutes
                        ?: if (wantsRecovery) 75 else 110,
                    flexibility = BlockFlexibility.RESIZABLE,
                    timezone = currentTimeZone
                )
            )
            add(
                suggestion(
                    title = "Recovery break",
                    category = "RECOVERY",
                    startMinuteOfDay = 12 * 60,
                    durationMinutes = if (wantsRecovery) 45 else 30,
                    flexibility = BlockFlexibility.MOVABLE,
                    timezone = currentTimeZone
                )
            )
            add(
                suggestion(
                    title = topTasks.getOrNull(1)?.title
                        ?: if (wantsExercise) "Workout window" else "Admin and communication",
                    category = if (wantsExercise && topTasks.getOrNull(1) == null) "WORKOUT" else "ADMIN",
                    startMinuteOfDay = 14 * 60,
                    durationMinutes = topTasks.getOrNull(1)?.preferredDurationMinutes
                        ?: if (wantsExercise) 60 else 45,
                    flexibility = BlockFlexibility.MOVABLE,
                    timezone = currentTimeZone
                )
            )
            add(
                suggestion(
                    title = "Daily review",
                    category = "REVIEW",
                    startMinuteOfDay = 17 * 60 + 30,
                    durationMinutes = 25,
                    flexibility = BlockFlexibility.FIXED,
                    timezone = currentTimeZone,
                    isProtected = true
                )
            )
        }
        return StructuredDayPlanSuggestion(
            proposedBlocks = blocks,
            reason = "Local structured suggestion generated for $todayLabel in $packageName.",
            conflictsResolved = listOf("Separated deep work from recovery time", "Reserved review time before evening"),
            requireConfirmation = true,
            explanation = "ChronosFlow used a local planning heuristic because Gemini was unavailable. Review every suggestion before applying it."
        )
    }

    fun generateReviewBackedDayPlan(
        packageName: String,
        userPreferences: String,
        review: DailyReviewSummary,
        existingBlocks: List<TimeBlock>,
        currentTimeZone: String,
        baseline: StructuredDayPlanSuggestion
    ): StructuredDayPlanSuggestion {
        val reviewBlocks = buildReviewRecoveryBlocks(review, existingBlocks, currentTimeZone)
        val conflictsResolved = baseline.conflictsResolved + review.insights
            .filter { it.severity != ReviewInsightSeverity.INFO }
            .map { "${it.type.name}: ${it.title}" }

        return baseline.copy(
            proposedBlocks = (reviewBlocks + baseline.proposedBlocks).distinctBy { it.title to it.startMinuteOfDay },
            reason = "Review-backed plan for ${review.date}: ${review.actualMinutes}/${review.plannedMinutes} actual/planned minutes, ${review.missedBlockCount} missed block(s), drift ${review.driftMinutes}m.",
            conflictsResolved = conflictsResolved,
            explanation = buildReviewExplanation(review, baseline.explanation)
        )
    }

    fun repairDayPlan(currentPlan: String, conflictDescription: String): String {
        val conflict = classifyConflict(conflictDescription)
        val planLoad = classifyPlanLoad(currentPlan)
        val steps = when (conflict) {
            RepairConflict.OVERLAP -> listOf(
                "Keep locked or protected commitments fixed.",
                "Move the least-protected flexible block to the next open 30-minute boundary.",
                "Resize optional work before displacing medication, calendar, or active focus commitments."
            )
            RepairConflict.OVERLOAD -> listOf(
                "Cap the plan to the available day window before adding new work.",
                "Convert optional blocks under 30 minutes into a single admin batch.",
                "Reserve one recovery buffer after the longest focus block."
            )
            RepairConflict.MISSED_WORK -> listOf(
                "Create a recovery block for the missed commitment instead of silently deleting it.",
                "Place recovery after the next protected block with at least 15 minutes of transition time.",
                "Mark the original block as missed so daily review keeps the planned-vs-actual trace."
            )
            RepairConflict.DEEP_WORK -> listOf(
                "Find an uninterrupted window of at least 90 minutes.",
                "Prefer morning or early midday windows unless fixed commitments already occupy them.",
                "Move flexible admin, break, or communication blocks away from both sides of the window."
            )
            RepairConflict.GENERAL -> listOf(
                "Resolve fixed commitments first, then protected commitments, then flexible work.",
                "Preserve medication and calendar anchors before moving optional blocks.",
                "Leave an explicit review note explaining what changed and why."
            )
        }

        return buildString {
            append("On-device repair plan")
            append(" (load=$planLoad, conflict=$conflict). ")
            append("Input conflict: ")
            append(conflictDescription.ifBlank { "No conflict details provided." })
            append(" Recommended steps: ")
            append(steps.joinToString(" "))
            if (currentPlan.isNotBlank()) {
                append(" Current plan signal: ")
                append(currentPlan.take(260))
                if (currentPlan.length > 260) append("...")
            }
        }
    }

    fun blocksToDomainTimeBlocks(
        sourceBlocks: List<ProposedSuggestionBlock>,
        date: LocalDate
    ): List<TimeBlock> = sourceBlocks.map {
        TimeBlock(
            id = it.id,
            date = date,
            title = it.title,
            category = it.category,
            startMinuteOfDay = it.startMinuteOfDay,
            durationMinutes = it.durationMinutes,
            timezone = it.timezone,
            provenance = it.provenance,
            flexibility = it.flexibility,
            energyLevel = EnergyIntensity.MODERATE,
            source = "LOCAL_PLANNER",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = it.isLocked,
            isProtected = it.isProtected,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now()
        )
    }

    /**
     * Picks the tasks the local (Nano-unavailable) planner should schedule into the deep-work and
     * admin slots. Tasks due by the end of [date] are urgent and come first, then highest priority,
     * then earliest deadline — so an offline user's overdue work isn't displaced by a merely
     * high-priority task that isn't due yet.
     */
    private fun selectTopTasks(
        pendingTasks: List<Task>,
        date: LocalDate,
        timezone: String,
        limit: Int
    ): List<Task> {
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
        val endOfDay = date.atTime(LocalTime.MAX).atZone(zone).toInstant()
        return pendingTasks
            .filterNot(Task::isCompleted)
            .sortedWith(
                compareByDescending<Task> { task -> task.dueDate?.let { !it.isAfter(endOfDay) } ?: false }
                    .thenByDescending(Task::priority)
                    .thenBy { it.dueDate ?: Instant.MAX }
            )
            .take(limit)
    }

    private fun suggestion(
        title: String,
        category: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        flexibility: BlockFlexibility,
        timezone: String,
        isProtected: Boolean = false
    ) = ProposedSuggestionBlock(
        id = UUID.randomUUID().toString(),
        title = title,
        category = category,
        startMinuteOfDay = startMinuteOfDay.coerceIn(0, 1439),
        durationMinutes = durationMinutes.coerceIn(1, 1440),
        provenance = BlockProvenance.AI_SUGGESTED,
        flexibility = flexibility,
        isLocked = flexibility == BlockFlexibility.FIXED,
        isProtected = isProtected,
        timezone = timezone
    )

    private fun buildReviewRecoveryBlocks(
        review: DailyReviewSummary,
        existingBlocks: List<TimeBlock>,
        timezone: String
    ): List<ProposedSuggestionBlock> {
        val anchor = existingBlocks.maxOfOrNull { it.startMinuteOfDay + it.durationMinutes } ?: (9 * 60)
        val start = nextQuarterHour(anchor + 15).coerceAtMost(20 * 60)
        return buildList {
            if (review.missedMinutes >= 20 || review.missedBlockCount > 0) {
                add(
                    suggestion(
                        title = "Recovery for missed work",
                        category = "RECOVERY_PLAN",
                        startMinuteOfDay = start,
                        durationMinutes = review.missedMinutes.coerceIn(20, 60),
                        flexibility = BlockFlexibility.RESIZABLE,
                        timezone = timezone
                    )
                )
            }
            if (kotlin.math.abs(review.driftMinutes) >= 30) {
                add(
                    suggestion(
                        title = if (review.driftMinutes > 0) "Schedule compression buffer" else "Fill underused focus window",
                        category = "DRIFT_REPAIR",
                        startMinuteOfDay = nextQuarterHour(start + 75).coerceAtMost(21 * 60),
                        durationMinutes = 30,
                        flexibility = BlockFlexibility.MOVABLE,
                        timezone = timezone
                    )
                )
            }
            if (review.insights.any { it.type == ReviewInsightType.FOCUS_UNDERRUN || it.type == ReviewInsightType.SCHEDULE_BALANCE }) {
                add(
                    suggestion(
                        title = "Protected focus reset",
                        category = "FOCUS",
                        startMinuteOfDay = nextQuarterHour(start + 120).coerceAtMost(21 * 60),
                        durationMinutes = 45,
                        flexibility = BlockFlexibility.RESIZABLE,
                        timezone = timezone,
                        isProtected = true
                    )
                )
            }
        }
    }

    private fun buildReviewExplanation(review: DailyReviewSummary, baselineExplanation: String): String {
        val insightText = review.insights.take(3).joinToString("; ") {
            "${it.type.name.lowercase(Locale.getDefault())}: ${it.title}"
        }
        return buildString {
            append("ChronosFlow used structured daily review data before proposing changes. ")
            append("Planned ${review.plannedMinutes}m, actual ${review.actualMinutes}m, missed ${review.missedMinutes}m, drift ${review.driftMinutes}m. ")
            if (insightText.isNotBlank()) append("Signals: $insightText. ")
            append(baselineExplanation)
        }
    }

    private fun nextQuarterHour(minute: Int): Int {
        return (((minute + 14) / 15) * 15).coerceIn(0, 1439)
    }

    private fun classifyPlanLoad(currentPlan: String): String {
        val normalized = currentPlan.lowercase(Locale.getDefault())
        return when {
            listOf("overbook", "full", "busy", "no gap", "no free").any { it in normalized } -> "overloaded"
            listOf("missed", "late", "drift").any { it in normalized } -> "drifted"
            currentPlan.isBlank() -> "unknown"
            else -> "normal"
        }
    }

    private fun classifyConflict(conflictDescription: String): RepairConflict {
        val normalized = conflictDescription.lowercase(Locale.getDefault())
        return when {
            listOf("overlap", "collision", "conflict", "same time").any { it in normalized } -> RepairConflict.OVERLAP
            listOf("overbook", "too much", "no gap", "no free", "full").any { it in normalized } -> RepairConflict.OVERLOAD
            listOf("missed", "late", "drift", "behind").any { it in normalized } -> RepairConflict.MISSED_WORK
            listOf("deep work", "focus", "fragment", "interruption").any { it in normalized } -> RepairConflict.DEEP_WORK
            else -> RepairConflict.GENERAL
        }
    }

    private enum class RepairConflict {
        OVERLAP,
        OVERLOAD,
        MISSED_WORK,
        DEEP_WORK,
        GENERAL
    }
}
