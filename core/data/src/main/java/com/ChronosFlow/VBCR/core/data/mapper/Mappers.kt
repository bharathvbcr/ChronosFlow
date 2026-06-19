package com.ChronosFlow.VBCR.core.data.mapper

import com.ChronosFlow.VBCR.core.data.model.ActualTimeSegmentEntity
import com.ChronosFlow.VBCR.core.data.model.AlarmRequestEntity
import com.ChronosFlow.VBCR.core.data.model.CalendarEventEntity
import com.ChronosFlow.VBCR.core.data.model.DayPlanEntity
import com.ChronosFlow.VBCR.core.data.model.DailyReviewEntity
import com.ChronosFlow.VBCR.core.data.model.FocusSessionEntity
import com.ChronosFlow.VBCR.core.data.model.HabitEntity
import com.ChronosFlow.VBCR.core.data.model.MedicationPlanEntity
import com.ChronosFlow.VBCR.core.data.model.MoodEnergyCheckInEntity
import com.ChronosFlow.VBCR.core.data.model.RecurrenceRuleEntity
import com.ChronosFlow.VBCR.core.data.model.ReviewInsightEntity
import com.ChronosFlow.VBCR.core.data.model.TaskEntity
import com.ChronosFlow.VBCR.core.data.model.TaskChecklistItemEntity
import com.ChronosFlow.VBCR.core.data.model.TaskContactMethodEntity
import com.ChronosFlow.VBCR.core.data.model.TaskContactSnapshotEntity
import com.ChronosFlow.VBCR.core.data.model.TaskActionEntity
import com.ChronosFlow.VBCR.core.data.model.TaskAttachmentEntity
import com.ChronosFlow.VBCR.core.data.model.TaskContactWithMethods
import com.ChronosFlow.VBCR.core.data.model.TaskWithChecklistItems
import com.ChronosFlow.VBCR.core.data.model.TimeBlockEntity
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSource
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.CalendarEvent
import com.ChronosFlow.VBCR.core.domain.model.DayPlan
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.DayPlanStatus
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyCheckIn
import com.ChronosFlow.VBCR.core.domain.model.normalizeAppLaunchTarget
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightSeverity
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import com.ChronosFlow.VBCR.core.domain.model.TaskChecklistItem
import com.ChronosFlow.VBCR.core.domain.model.TaskContactMethod
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import com.ChronosFlow.VBCR.core.domain.model.ContactMethodKind
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.model.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    priority = priority,
    dueDate = dueDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    preferredDurationMinutes = preferredDurationMinutes,
    preferredStartMinuteOfDay = preferredStartMinuteOfDay,
    targetDate = targetDate,
    goalId = goalId,
    origin = origin,
    externalId = externalId
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    priority = priority,
    dueDate = dueDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    preferredDurationMinutes = preferredDurationMinutes,
    preferredStartMinuteOfDay = preferredStartMinuteOfDay,
    targetDate = targetDate,
    goalId = goalId,
    origin = origin,
    externalId = externalId
)

fun TaskWithChecklistItems.toDomain(): Task = task.toDomain().copy(
    checklist = checklistItems
        .sortedBy { it.sortOrder }
        .map { it.toDomain() },
    linkedContact = contactSnapshot?.toDomain(),
    actions = taskActions
        .sortedBy { it.sortOrder }
        .map { it.toDomain() },
    attachments = taskAttachments
        .sortedBy { it.sortOrder }
        .map { it.toDomain() }
)

fun TaskChecklistItemEntity.toDomain(): TaskChecklistItem = TaskChecklistItem(
    id = id,
    label = label,
    isCompleted = isCompleted
)

fun TaskChecklistItem.toEntity(taskId: String, sortOrder: Int): TaskChecklistItemEntity =
    TaskChecklistItemEntity(
        id = id,
        taskId = taskId,
        label = label,
        isCompleted = isCompleted,
        sortOrder = sortOrder
    )

fun TaskContactWithMethods.toDomain(): TaskContactSnapshot = TaskContactSnapshot(
    displayName = snapshot.displayName,
    lookupKey = snapshot.lookupKey,
    methods = methods
        .sortedBy { it.sortOrder }
        .map { it.toDomain() }
)

fun TaskContactSnapshot.toEntity(taskId: String): TaskContactSnapshotEntity = TaskContactSnapshotEntity(
    taskId = taskId,
    displayName = displayName,
    lookupKey = lookupKey
)

fun TaskContactMethodEntity.toDomain(): TaskContactMethod = TaskContactMethod(
    id = id,
    kind = ContactMethodKind.valueOf(kind),
    label = label,
    value = value,
    normalizedValue = normalizedValue,
    isPrimary = isPrimary
)

fun TaskContactMethod.toEntity(taskId: String, sortOrder: Int): TaskContactMethodEntity =
    TaskContactMethodEntity(
        id = id,
        taskId = taskId,
        kind = kind.name,
        label = label,
        value = value,
        normalizedValue = normalizedValue,
        isPrimary = isPrimary,
        sortOrder = sortOrder
    )

fun TaskActionEntity.toDomain(): TaskAction = TaskAction(
    id = id,
    type = TaskActionType.valueOf(type),
    label = label,
    value = value,
    isPrimary = isPrimary
)

fun TaskAction.toEntity(taskId: String, sortOrder: Int): TaskActionEntity =
    TaskActionEntity(
        id = id,
        taskId = taskId,
        type = type.name,
        label = label,
        value = value,
        isPrimary = isPrimary,
        sortOrder = sortOrder
    )

fun TaskAttachmentEntity.toDomain(): TaskAttachment = TaskAttachment(
    id = id,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    kind = TaskAttachmentKind.valueOf(kind),
    storageMode = TaskAttachmentStorageMode.valueOf(storageMode),
    reference = reference,
    persistedUriPermission = persistedUriPermission,
    isFeaturedImage = isFeaturedImage
)

fun TaskAttachment.toEntity(taskId: String, sortOrder: Int): TaskAttachmentEntity =
    TaskAttachmentEntity(
        id = id,
        taskId = taskId,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        kind = kind.name,
        storageMode = storageMode.name,
        reference = reference,
        persistedUriPermission = persistedUriPermission,
        isFeaturedImage = isFeaturedImage,
        sortOrder = sortOrder
    )

fun TimeBlockEntity.toDomain(): TimeBlock = TimeBlock(
    id = id,
    date = date,
    title = title,
    category = category,
    startMinuteOfDay = startMinuteOfDay,
    durationMinutes = durationMinutes,
    timezone = timezone,
    source = source,
    provenance = runCatching { BlockProvenance.valueOf(provenance.uppercase()) }.getOrElse {
        BlockProvenance.fromSource(provenance)
    },
    flexibility = BlockFlexibility.valueOf(flexibility),
    energyLevel = EnergyIntensity.fromLevel(energyLevel),
    taskId = taskId,
    calendarEventId = calendarEventId,
    medicationPlanId = medicationPlanId,
    habitId = habitId,
    isLocked = isLocked,
    isProtected = isProtected,
    recurrenceRuleId = recurrenceRuleId,
    taskOccurrenceDate = taskOccurrenceDate,
    actualStartMinuteOfDay = actualStartMinuteOfDay,
    actualEndMinuteOfDay = actualEndMinuteOfDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    goalId = goalId,
    routineId = routineId
)

fun TimeBlock.toEntity(): TimeBlockEntity = TimeBlockEntity(
    id = id,
    date = date,
    title = title,
    category = category,
    startMinuteOfDay = startMinuteOfDay,
    durationMinutes = durationMinutes,
    timezone = timezone,
    source = source,
    provenance = provenance.name,
    flexibility = flexibility.name,
    energyLevel = energyLevel.level,
    taskId = taskId,
    calendarEventId = calendarEventId,
    medicationPlanId = medicationPlanId,
    habitId = habitId,
    isLocked = isLocked,
    isProtected = isProtected,
    recurrenceRuleId = recurrenceRuleId,
    taskOccurrenceDate = taskOccurrenceDate,
    actualStartMinuteOfDay = actualStartMinuteOfDay,
    actualEndMinuteOfDay = actualEndMinuteOfDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    goalId = goalId,
    routineId = routineId
)

fun RecurrenceRule.toEntity(): RecurrenceRuleEntity = RecurrenceRuleEntity(
    id = id,
    blockId = blockId,
    pattern = pattern,
    intervalWeeks = intervalWeeks,
    startsOn = startsOn?.toString(),
    endsOn = endsOn?.toString(),
    maxOccurrences = maxOccurrences,
    weekdays = weekdays,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RecurrenceRuleEntity.toDomain(): RecurrenceRule = RecurrenceRule(
    id = id,
    blockId = blockId,
    pattern = pattern,
    intervalWeeks = intervalWeeks,
    startsOn = startsOn?.let { LocalDate.parse(it) },
    endsOn = endsOn?.let { LocalDate.parse(it) },
    maxOccurrences = maxOccurrences,
    weekdays = weekdays,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DayPlanEntity.toDomain(): DayPlan = DayPlan(
    date = date,
    timezone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.of("UTC") },
    status = runCatching { DayPlanStatus.valueOf(status) }.getOrElse { DayPlanStatus.DRAFT },
    blocks = emptyList(),
    conflicts = emptyList(),
    review = null
)

fun DayPlan.toEntity(updatedAt: Instant): DayPlanEntity = DayPlanEntity(
    date = date,
    timezone = timezone.id,
    status = status.name,
    lastAiSuggestionAt = null,
    totalPlannedMinutes = blocks.sumOf { it.durationMinutes },
    conflictCount = conflicts.size,
    updatedAt = updatedAt
)

fun ActualTimeSegmentEntity.toDomain(): ActualTimeSegment = ActualTimeSegment(
    id = id,
    blockId = blockId,
    date = date,
    startInstant = startInstant,
    endInstant = endInstant,
    source = runCatching { ActualTimeSource.valueOf(source) }.getOrElse { ActualTimeSource.SYSTEM_INFERENCE },
    confidence = confidence
)

fun ActualTimeSegment.toEntity(): ActualTimeSegmentEntity = ActualTimeSegmentEntity(
    id = id,
    blockId = blockId,
    date = date,
    startInstant = startInstant,
    endInstant = endInstant,
    source = source.name,
    confidence = confidence
)

fun DailyReviewEntity.toDomain(): DailyReviewSummary = DailyReviewSummary(
    date = date,
    plannedMinutes = plannedMinutes,
    actualMinutes = actualMinutes,
    missedMinutes = missedMinutes,
    driftMinutes = driftMinutes,
    completedBlockCount = completedBlockCount,
    missedBlockCount = missedBlockCount,
    insights = decodeInsights(insightsJson)
)

fun DailyReviewSummary.toEntity(): DailyReviewEntity = DailyReviewEntity(
    date = date,
    plannedMinutes = plannedMinutes,
    actualMinutes = actualMinutes,
    missedMinutes = missedMinutes,
    driftMinutes = driftMinutes,
    completedBlockCount = completedBlockCount,
    missedBlockCount = missedBlockCount,
    insightsJson = encodeInsights(insights)
)

fun ReviewInsightEntity.toDomain(): ReviewInsight = ReviewInsight(
    id = id,
    type = runCatching { ReviewInsightType.valueOf(type) }.getOrElse { ReviewInsightType.SCHEDULE_BALANCE },
    title = title,
    detail = detail,
    relatedBlockId = relatedBlockId,
    severity = runCatching { ReviewInsightSeverity.valueOf(severity) }.getOrElse { ReviewInsightSeverity.INFO },
    assistSource = assistSource
)

fun ReviewInsight.toEntity(date: LocalDate): ReviewInsightEntity = ReviewInsightEntity(
    id = id,
    date = date,
    type = type.name,
    title = title,
    detail = detail,
    relatedBlockId = relatedBlockId,
    severity = severity.name,
    assistSource = assistSource
)

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    title = title,
    cadence = cadence,
    windowStartMinute = windowStartMinute,
    windowEndMinute = windowEndMinute,
    difficulty = difficulty,
    isBundled = isBundled,
    streakCount = streakCount,
    lastCompletedDate = lastCompletedDate,
    isActive = isActive,
    launchTarget = launchAppValue?.let { value ->
        normalizeAppLaunchTarget(launchAppLabel.orEmpty(), value)
    },
    goalId = goalId
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    title = title,
    cadence = cadence,
    windowStartMinute = windowStartMinute,
    windowEndMinute = windowEndMinute,
    difficulty = difficulty,
    isBundled = isBundled,
    streakCount = streakCount,
    lastCompletedDate = lastCompletedDate,
    isActive = isActive,
    launchAppLabel = launchTarget?.label,
    launchAppValue = launchTarget?.value,
    goalId = goalId
)

fun MedicationPlanEntity.toDomain(): MedicationPlan = MedicationPlan(
    id = id,
    name = name,
    dosage = dosage,
    unit = unit,
    notes = notes,
    startAt = startAt,
    endAt = endAt,
    reminderMinuteOfDay = reminderMinuteOfDay,
    takeWithFood = takeWithFood,
    missedCount = missedCount,
    refillNeededAfterDoses = refillNeededAfterDoses,
    isActive = isActive
)

fun MedicationPlan.toEntity(): MedicationPlanEntity = MedicationPlanEntity(
    id = id,
    name = name,
    dosage = dosage,
    unit = unit,
    notes = notes,
    startAt = startAt,
    endAt = endAt,
    reminderMinuteOfDay = reminderMinuteOfDay,
    takeWithFood = takeWithFood,
    missedCount = missedCount,
    refillNeededAfterDoses = refillNeededAfterDoses,
    isActive = isActive
)

fun CalendarEventEntity.toDomain(): CalendarEvent = CalendarEvent(
    id = id,
    title = title,
    description = description,
    startAt = startAt,
    endAt = endAt,
    timezone = timezone,
    location = location,
    externalId = externalId,
    isAllDay = isAllDay
)

fun CalendarEvent.toEntity(): CalendarEventEntity = CalendarEventEntity(
    id = id,
    title = title,
    description = description,
    startAt = startAt,
    endAt = endAt,
    timezone = timezone,
    location = location,
    externalId = externalId,
    isAllDay = isAllDay
)

fun AlarmRequestEntity.toDomain(): AlarmRequest = AlarmRequest(
    id = id,
    type = runCatching { AlarmRequestType.valueOf(type) }.getOrElse { AlarmRequestType.BLOCK_START },
    scheduledFor = scheduledFor,
    title = title,
    message = message,
    medicationPlanId = medicationPlanId,
    blockId = blockId,
    reliability = runCatching { AlarmReliability.valueOf(reliability) }.getOrElse { AlarmReliability.BLOCKED },
    deliveryState = runCatching { AlarmDeliveryState.valueOf(deliveryState) }.getOrElse { AlarmDeliveryState.FAILED },
    createdAt = createdAt,
    updatedAt = updatedAt,
    deliveredAt = deliveredAt,
    failureReason = failureReason
)

fun AlarmRequest.toEntity(): AlarmRequestEntity = AlarmRequestEntity(
    id = id,
    type = type.name,
    scheduledFor = scheduledFor,
    title = title,
    message = message,
    medicationPlanId = medicationPlanId,
    blockId = blockId,
    reliability = reliability.name,
    deliveryState = deliveryState.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deliveredAt = deliveredAt,
    failureReason = failureReason
)

fun FocusSessionEntity.toDomain(): FocusSessionState {
    return when (state) {
        "RUNNING" -> FocusSessionState.Running(
            sessionId = id,
            blockId = blockId,
            startedAt = requireNotNull(startedAt),
            plannedEndAt = requireNotNull(plannedEndAt)
        )
        "PAUSED" -> FocusSessionState.Paused(
            sessionId = id,
            blockId = blockId,
            startedAt = requireNotNull(startedAt),
            pausedAt = requireNotNull(pausedAt),
            plannedEndAt = requireNotNull(plannedEndAt)
        )
        "COMPLETING" -> FocusSessionState.Completing(id)
        "REVIEWING" -> FocusSessionState.Reviewing(id)
        "ARCHIVED" -> FocusSessionState.Archived(id)
        "SERVICE_KILLED_RECOVERABLE" -> FocusSessionState.ServiceKilledRecoverable(id)
        else -> FocusSessionState.Idle
    }
}

fun FocusSessionState.toEntity(
    now: Instant,
    totalSeconds: Int? = null
): FocusSessionEntity {
    return when (this) {
        FocusSessionState.Idle -> FocusSessionEntity("idle", null, "IDLE", null, null, null, null, null, now)
        is FocusSessionState.Preparing -> FocusSessionEntity(blockId, blockId, "PREPARING", null, null, null, null, totalSeconds, now)
        is FocusSessionState.Running -> FocusSessionEntity(sessionId, blockId, "RUNNING", startedAt, plannedEndAt, null, null, totalSeconds, now)
        is FocusSessionState.Paused -> FocusSessionEntity(sessionId, blockId, "PAUSED", startedAt, plannedEndAt, pausedAt, null, totalSeconds, now)
        is FocusSessionState.Extending -> FocusSessionEntity(sessionId, null, "EXTENDING", null, newPlannedEndAt, null, null, totalSeconds, now)
        is FocusSessionState.Completing -> FocusSessionEntity(sessionId, null, "COMPLETING", null, null, null, null, totalSeconds, now)
        is FocusSessionState.Completed -> FocusSessionEntity(sessionId, null, "COMPLETED", null, null, null, now, totalSeconds, now)
        is FocusSessionState.Reviewing -> FocusSessionEntity(sessionId, null, "REVIEWING", null, null, null, null, totalSeconds, now)
        is FocusSessionState.Archived -> FocusSessionEntity(sessionId, null, "ARCHIVED", null, null, null, null, totalSeconds, now)
        is FocusSessionState.InterruptedBySystem -> FocusSessionEntity(sessionId, null, "INTERRUPTED_BY_SYSTEM", null, null, null, null, totalSeconds, now)
        is FocusSessionState.PermissionBlocked -> FocusSessionEntity(permission, null, "PERMISSION_BLOCKED", null, null, null, null, totalSeconds, now)
        is FocusSessionState.ServiceKilledRecoverable -> FocusSessionEntity(sessionId, null, "SERVICE_KILLED_RECOVERABLE", null, null, null, null, totalSeconds, now)
    }
}

private fun encodeInsights(insights: List<ReviewInsight>): String {
    return insights.joinToString("\n") { insight ->
        listOf(
            insight.id,
            insight.type.name,
            insight.title,
            insight.detail,
            insight.relatedBlockId.orEmpty(),
            insight.severity.name,
            insight.assistSource.orEmpty()
        ).joinToString("|") { it.escapeInsightPart() }
    }
}

private fun decodeInsights(value: String): List<ReviewInsight> {
    if (value.isBlank()) return emptyList()
    return value.lineSequence().mapNotNull { line ->
        val parts = line.split("|").map { it.unescapeInsightPart() }
        if (parts.size < 6) return@mapNotNull null
        ReviewInsight(
            id = parts[0],
            type = runCatching { ReviewInsightType.valueOf(parts[1]) }.getOrElse { ReviewInsightType.SCHEDULE_BALANCE },
            title = parts[2],
            detail = parts[3],
            relatedBlockId = parts[4].ifBlank { null },
            severity = runCatching { ReviewInsightSeverity.valueOf(parts[5]) }.getOrElse { ReviewInsightSeverity.INFO },
            assistSource = parts.getOrNull(6)?.ifBlank { null }
        )
    }.toList()
}

private fun String.escapeInsightPart(): String = replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")

private fun String.unescapeInsightPart(): String {
    val builder = StringBuilder()
    var escaping = false
    for (char in this) {
        if (escaping) {
            builder.append(
                when (char) {
                    'p' -> '|'
                    'n' -> '\n'
                    '\\' -> '\\'
                    else -> char
                }
            )
            escaping = false
        } else if (char == '\\') {
            escaping = true
        } else {
            builder.append(char)
        }
    }
    if (escaping) builder.append('\\')
    return builder.toString()
}

fun MoodEnergyCheckInEntity.toDomain(zoneId: ZoneId): MoodEnergyCheckIn = MoodEnergyCheckIn(
    id = id,
    blockId = blockId,
    moodScore = moodScore,
    stressScore = stressScore,
    energyScore = energyScore,
    focusScore = focusScore,
    notes = notes,
    recordedAt = recordedAt.atZone(zoneId).toLocalDateTime(),
    checkInDate = checkInDate
)

fun MoodEnergyCheckIn.toEntity(zoneId: ZoneId): MoodEnergyCheckInEntity = MoodEnergyCheckInEntity(
    id = id,
    checkInDate = checkInDate,
    recordedAt = recordedAt.atZone(zoneId).toInstant(),
    blockId = blockId,
    moodScore = moodScore,
    stressScore = stressScore,
    energyScore = energyScore,
    focusScore = focusScore,
    notes = notes
)
