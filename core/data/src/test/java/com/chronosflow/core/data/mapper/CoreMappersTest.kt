package com.chronosflow.core.data.mapper

import com.chronosflow.core.data.model.AlarmRequestEntity
import com.chronosflow.core.data.model.ActualTimeSegmentEntity
import com.chronosflow.core.data.model.MoodEnergyCheckInEntity
import com.chronosflow.core.data.model.TaskActionEntity
import com.chronosflow.core.data.model.TaskAttachmentEntity
import com.chronosflow.core.data.model.TaskChecklistItemEntity
import com.chronosflow.core.data.model.TaskContactMethodEntity
import com.chronosflow.core.data.model.TaskContactSnapshotEntity
import com.chronosflow.core.data.model.TaskContactWithMethods
import com.chronosflow.core.data.model.TaskEntity
import com.chronosflow.core.data.model.TaskWithChecklistItems
import com.chronosflow.core.data.model.TimeBlockEntity
import com.chronosflow.core.domain.model.ActualTimeSource
import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmReliability
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.ContactMethodKind
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.DayPlan
import com.chronosflow.core.domain.model.DayPlanStatus
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.ReviewInsight
import com.chronosflow.core.domain.model.ReviewInsightSeverity
import com.chronosflow.core.domain.model.ReviewInsightType
import com.chronosflow.core.domain.model.ScheduleConflict
import com.chronosflow.core.domain.model.ScheduleConflictSeverity
import com.chronosflow.core.domain.model.TaskActionType
import com.chronosflow.core.domain.model.TaskAttachmentKind
import com.chronosflow.core.domain.model.TaskAttachmentStorageMode
import com.chronosflow.core.domain.model.TimeBlock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreMappersTest {

    @Test
    fun `task aggregate mapper sorts nested rows and preserves connection fields`() {
        val task = TaskWithChecklistItems(
            task = sampleTaskEntity(),
            checklistItems = listOf(
                TaskChecklistItemEntity("item-2", "task-1", "Send note", true, sortOrder = 2),
                TaskChecklistItemEntity("item-1", "task-1", "Confirm owner", false, sortOrder = 1)
            ),
            contactSnapshot = TaskContactWithMethods(
                snapshot = TaskContactSnapshotEntity("task-1", "Avery Chen", "lookup-1"),
                methods = listOf(
                    TaskContactMethodEntity("method-2", "task-1", ContactMethodKind.EMAIL.name, "Work", "avery@example.com", "avery@example.com", false, sortOrder = 2),
                    TaskContactMethodEntity("method-1", "task-1", ContactMethodKind.PHONE.name, "Mobile", "+1 555 0100", "15550100", true, sortOrder = 1)
                )
            ),
            taskActions = listOf(
                TaskActionEntity("action-2", "task-1", TaskActionType.DOCUMENT.name, "Spec", "content://spec", false, sortOrder = 2),
                TaskActionEntity("action-1", "task-1", TaskActionType.WEBSITE.name, "Portal", "https://example.com", true, sortOrder = 1)
            ),
            taskAttachments = listOf(
                TaskAttachmentEntity("attachment-2", "task-1", "brief.pdf", "application/pdf", 1000L, TaskAttachmentKind.FILE.name, TaskAttachmentStorageMode.LINKED.name, "content://brief", true, false, sortOrder = 2),
                TaskAttachmentEntity("attachment-1", "task-1", "photo.jpg", "image/jpeg", 2000L, TaskAttachmentKind.IMAGE.name, TaskAttachmentStorageMode.IMPORTED.name, "task_attachments/photo.jpg", false, true, sortOrder = 1)
            )
        )

        val domain = task.toDomain()

        assertEquals(listOf("Confirm owner", "Send note"), domain.checklist.map { it.label })
        assertEquals("Avery Chen", domain.linkedContact?.displayName)
        assertEquals(listOf(ContactMethodKind.PHONE, ContactMethodKind.EMAIL), domain.linkedContact?.methods?.map { it.kind })
        assertEquals(listOf("Portal", "Spec"), domain.actions.map { it.label })
        assertEquals(listOf("photo.jpg", "brief.pdf"), domain.attachments.map { it.displayName })
        assertEquals(TaskAttachmentStorageMode.IMPORTED, domain.attachments.first().storageMode)
    }

    @Test
    fun `time block mapper preserves planner metadata and normalizes legacy provenance`() {
        val now = Instant.parse("2026-05-27T12:00:00Z")
        val entity = TimeBlockEntity(
            id = "block-1",
            date = LocalDate.of(2026, 5, 27),
            title = "Imported hold",
            category = "CALENDAR",
            startMinuteOfDay = 9 * 60,
            durationMinutes = 45,
            timezone = "America/Chicago",
            source = "CALENDAR_IMPORTED",
            provenance = "calendar",
            flexibility = BlockFlexibility.FIXED.name,
            energyLevel = EnergyIntensity.LOW.level,
            taskId = "task-1",
            calendarEventId = 42L,
            medicationPlanId = null,
            habitId = null,
            isLocked = true,
            isProtected = true,
            recurrenceRuleId = "rule-1",
            taskOccurrenceDate = LocalDate.of(2026, 5, 28),
            actualStartMinuteOfDay = 9 * 60,
            actualEndMinuteOfDay = 9 * 60 + 40,
            createdAt = now,
            updatedAt = now
        )

        val domain = entity.toDomain()
        val roundTrip = domain.toEntity()

        assertEquals(BlockProvenance.CALENDAR_IMPORTED, domain.provenance)
        assertEquals(LocalDate.of(2026, 5, 28), domain.taskOccurrenceDate)
        assertEquals(42L, domain.calendarEventId)
        assertEquals(BlockProvenance.CALENDAR_IMPORTED.name, roundTrip.provenance)
        assertEquals(EnergyIntensity.LOW.level, roundTrip.energyLevel)
    }

    @Test
    fun `daily review mapper escapes insight delimiters without losing source metadata`() {
        val summary = DailyReviewSummary(
            date = LocalDate.of(2026, 5, 27),
            plannedMinutes = 120,
            actualMinutes = 90,
            missedMinutes = 30,
            driftMinutes = -15,
            completedBlockCount = 2,
            missedBlockCount = 1,
            insights = listOf(
                ReviewInsight(
                    id = "insight-1",
                    type = ReviewInsightType.DRIFT,
                    title = "Plan | drift",
                    detail = "Line 1\nLine 2 with | delimiter",
                    relatedBlockId = "block-1",
                    severity = ReviewInsightSeverity.WARNING,
                    assistSource = "ON_DEVICE"
                )
            )
        )

        val roundTrip = summary.toEntity().toDomain()

        assertEquals(summary, roundTrip)
    }

    @Test
    fun `day plan mapper uses caller supplied timestamp`() {
        val updatedAt = Instant.parse("2026-05-27T13:00:00Z")
        val plan = DayPlan(
            date = LocalDate.of(2026, 5, 27),
            timezone = ZoneId.of("America/Chicago"),
            status = DayPlanStatus.PLANNED,
            blocks = listOf(sampleTimeBlock(durationMinutes = 50), sampleTimeBlock(id = "block-2", durationMinutes = 25)),
            conflicts = listOf(
                ScheduleConflict(
                    primaryBlockId = "block-1",
                    conflictingBlockId = "block-2",
                    overlapStartMinute = 9 * 60,
                    overlapEndMinute = 9 * 60 + 15,
                    severity = ScheduleConflictSeverity.WARNING,
                    reason = "Overlap"
                )
            ),
            review = null
        )

        val entity = plan.toEntity(updatedAt = updatedAt)

        assertEquals(updatedAt, entity.updatedAt)
        assertEquals(75, entity.totalPlannedMinutes)
        assertEquals(1, entity.conflictCount)
    }

    @Test
    fun `mood energy mapper uses explicit zone for instant conversion`() {
        val zoneId = ZoneId.of("America/Chicago")
        val entity = MoodEnergyCheckInEntity(
            id = "mood-1",
            checkInDate = LocalDate.of(2026, 5, 27),
            recordedAt = Instant.parse("2026-05-27T15:30:00Z"),
            blockId = "block-1",
            moodScore = 4,
            stressScore = 2,
            energyScore = 5,
            focusScore = 4,
            notes = "clear"
        )

        val domain = entity.toDomain(zoneId)
        val roundTrip = domain.toEntity(zoneId)

        assertEquals(LocalDateTime.of(2026, 5, 27, 10, 30), domain.recordedAt)
        assertEquals(entity, roundTrip)
    }

    @Test
    fun `alarm and actual-time mappers fall back deterministically for legacy enum values`() {
        val now = Instant.parse("2026-05-27T12:00:00Z")
        val alarm = AlarmRequestEntity(
            id = "alarm-1",
            type = "UNKNOWN",
            scheduledFor = now,
            title = "Start",
            message = "Begin",
            medicationPlanId = null,
            blockId = "block-1",
            reliability = "STALE",
            deliveryState = "STALE",
            createdAt = now,
            updatedAt = now,
            deliveredAt = null,
            failureReason = "legacy"
        ).toDomain()
        val segment = ActualTimeSegmentEntity(
            id = "segment-1",
            blockId = "block-1",
            date = LocalDate.of(2026, 5, 27),
            startInstant = now,
            endInstant = now.plusSeconds(300),
            source = "STALE",
            confidence = 0.5f
        ).toDomain()

        assertEquals(AlarmRequestType.BLOCK_START, alarm.type)
        assertEquals(AlarmReliability.BLOCKED, alarm.reliability)
        assertEquals(AlarmDeliveryState.FAILED, alarm.deliveryState)
        assertEquals(ActualTimeSource.SYSTEM_INFERENCE, segment.source)
    }

    private fun sampleTaskEntity(): TaskEntity {
        val now = Instant.parse("2026-05-27T12:00:00Z")
        return TaskEntity(
            id = "task-1",
            title = "Launch prep",
            description = "Prepare launch",
            isCompleted = false,
            priority = 2,
            dueDate = now.plusSeconds(3600),
            createdAt = now,
            updatedAt = now,
            preferredDurationMinutes = 45,
            preferredStartMinuteOfDay = 9 * 60,
            targetDate = LocalDate.of(2026, 5, 28)
        )
    }

    private fun sampleTimeBlock(
        id: String = "block-1",
        durationMinutes: Int = 45
    ): TimeBlock {
        val now = Instant.parse("2026-05-27T12:00:00Z")
        return TimeBlock(
            id = id,
            date = LocalDate.of(2026, 5, 27),
            title = "Focus",
            category = "WORK",
            startMinuteOfDay = 9 * 60,
            durationMinutes = durationMinutes,
            timezone = "America/Chicago",
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "USER",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = now,
            updatedAt = now
        )
    }
}
