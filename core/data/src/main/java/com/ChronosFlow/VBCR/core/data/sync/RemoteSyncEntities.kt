package com.ChronosFlow.VBCR.core.data.sync

import com.ChronosFlow.VBCR.core.data.model.TimeBlockEntity
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskChecklistItem
import com.ChronosFlow.VBCR.core.domain.model.TaskContactMethod
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import java.time.Instant
import java.time.LocalDate

data class RemoteSyncBatch(
    val tasks: List<RemoteTaskEntity>,
    val timeBlocks: List<RemoteTimeBlockEntity>
)

data class RemoteTaskEntity(
    val id: String,
    val title: String,
    val description: String?,
    val isCompleted: Boolean,
    val priority: Int,
    val dueDateEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val preferredDurationMinutes: Int?,
    val preferredStartMinuteOfDay: Int?,
    val targetDate: String?,
    val checklist: List<RemoteTaskChecklistItemEntity>,
    val linkedContact: RemoteTaskContactEntity?,
    val actions: List<RemoteTaskActionEntity>,
    val attachments: List<RemoteTaskAttachmentEntity>
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "description" to description,
        "isCompleted" to isCompleted,
        "priority" to priority,
        "dueDateEpochMillis" to dueDateEpochMillis,
        "createdAtEpochMillis" to createdAtEpochMillis,
        "updatedAtEpochMillis" to updatedAtEpochMillis,
        "preferredDurationMinutes" to preferredDurationMinutes,
        "preferredStartMinuteOfDay" to preferredStartMinuteOfDay,
        "targetDate" to targetDate,
        "checklist" to checklist.map { it.toFirestoreMap() },
        "linkedContact" to linkedContact?.toFirestoreMap(),
        "actions" to actions.map { it.toFirestoreMap() },
        "attachments" to attachments.map { it.toFirestoreMap() }
    )
}

data class RemoteTaskChecklistItemEntity(
    val id: String,
    val label: String,
    val isCompleted: Boolean
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "label" to label,
        "isCompleted" to isCompleted
    )
}

data class RemoteTaskContactEntity(
    val displayName: String,
    val lookupKey: String?,
    val methods: List<RemoteTaskContactMethodEntity>
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "displayName" to displayName,
        "lookupKey" to lookupKey,
        "methods" to methods.map { it.toFirestoreMap() }
    )
}

data class RemoteTaskContactMethodEntity(
    val id: String,
    val kind: String,
    val label: String?,
    val value: String,
    val normalizedValue: String?,
    val isPrimary: Boolean
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "kind" to kind,
        "label" to label,
        "value" to value,
        "normalizedValue" to normalizedValue,
        "isPrimary" to isPrimary
    )
}

data class RemoteTaskActionEntity(
    val id: String,
    val type: String,
    val label: String,
    val value: String,
    val isPrimary: Boolean
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "type" to type,
        "label" to label,
        "value" to value,
        "isPrimary" to isPrimary
    )
}

data class RemoteTaskAttachmentEntity(
    val id: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val kind: String,
    val storageMode: String,
    val reference: String,
    val persistedUriPermission: Boolean,
    val isFeaturedImage: Boolean
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "displayName" to displayName,
        "mimeType" to mimeType,
        "sizeBytes" to sizeBytes,
        "kind" to kind,
        "storageMode" to storageMode,
        "reference" to reference,
        "persistedUriPermission" to persistedUriPermission,
        "isFeaturedImage" to isFeaturedImage
    )
}

data class RemoteTimeBlockEntity(
    val id: String,
    val date: String,
    val title: String,
    val category: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val timezone: String,
    val source: String,
    val provenance: String,
    val flexibility: String,
    val energyLevel: Int,
    val taskId: String?,
    val calendarEventId: Long?,
    val medicationPlanId: String?,
    val habitId: String?,
    val isLocked: Boolean,
    val isProtected: Boolean,
    val recurrenceRuleId: String?,
    val taskOccurrenceDate: String?,
    val actualStartMinuteOfDay: Int?,
    val actualEndMinuteOfDay: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "date" to date,
        "title" to title,
        "category" to category,
        "startMinuteOfDay" to startMinuteOfDay,
        "durationMinutes" to durationMinutes,
        "timezone" to timezone,
        "source" to source,
        "provenance" to provenance,
        "flexibility" to flexibility,
        "energyLevel" to energyLevel,
        "taskId" to taskId,
        "calendarEventId" to calendarEventId,
        "medicationPlanId" to medicationPlanId,
        "habitId" to habitId,
        "isLocked" to isLocked,
        "isProtected" to isProtected,
        "recurrenceRuleId" to recurrenceRuleId,
        "taskOccurrenceDate" to taskOccurrenceDate,
        "actualStartMinuteOfDay" to actualStartMinuteOfDay,
        "actualEndMinuteOfDay" to actualEndMinuteOfDay,
        "createdAtEpochMillis" to createdAtEpochMillis,
        "updatedAtEpochMillis" to updatedAtEpochMillis
    )
}

fun Task.toRemoteEntity(): RemoteTaskEntity = RemoteTaskEntity(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    priority = priority,
    dueDateEpochMillis = dueDate.toEpochMillisOrNull(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
    preferredDurationMinutes = preferredDurationMinutes,
    preferredStartMinuteOfDay = preferredStartMinuteOfDay,
    targetDate = targetDate.toIsoStringOrNull(),
    checklist = checklist.map { it.toRemoteEntity() },
    linkedContact = linkedContact?.toRemoteEntity(),
    actions = actions.map { it.toRemoteEntity() },
    attachments = attachments.map { it.toRemoteEntity() }
)

fun TimeBlockEntity.toRemoteEntity(): RemoteTimeBlockEntity = RemoteTimeBlockEntity(
    id = id,
    date = date.toString(),
    title = title,
    category = category,
    startMinuteOfDay = startMinuteOfDay,
    durationMinutes = durationMinutes,
    timezone = timezone,
    source = source,
    provenance = provenance,
    flexibility = flexibility,
    energyLevel = energyLevel,
    taskId = taskId,
    calendarEventId = calendarEventId,
    medicationPlanId = medicationPlanId,
    habitId = habitId,
    isLocked = isLocked,
    isProtected = isProtected,
    recurrenceRuleId = recurrenceRuleId,
    taskOccurrenceDate = taskOccurrenceDate.toIsoStringOrNull(),
    actualStartMinuteOfDay = actualStartMinuteOfDay,
    actualEndMinuteOfDay = actualEndMinuteOfDay,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli()
)

private fun TaskChecklistItem.toRemoteEntity(): RemoteTaskChecklistItemEntity =
    RemoteTaskChecklistItemEntity(
        id = id,
        label = label,
        isCompleted = isCompleted
    )

private fun TaskContactSnapshot.toRemoteEntity(): RemoteTaskContactEntity =
    RemoteTaskContactEntity(
        displayName = displayName,
        lookupKey = lookupKey,
        methods = methods.map { it.toRemoteEntity() }
    )

private fun TaskContactMethod.toRemoteEntity(): RemoteTaskContactMethodEntity =
    RemoteTaskContactMethodEntity(
        id = id,
        kind = kind.name,
        label = label,
        value = value,
        normalizedValue = normalizedValue,
        isPrimary = isPrimary
    )

private fun TaskAction.toRemoteEntity(): RemoteTaskActionEntity =
    RemoteTaskActionEntity(
        id = id,
        type = type.name,
        label = label,
        value = value,
        isPrimary = isPrimary
    )

private fun TaskAttachment.toRemoteEntity(): RemoteTaskAttachmentEntity =
    RemoteTaskAttachmentEntity(
        id = id,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        kind = kind.name,
        storageMode = storageMode.name,
        reference = reference,
        persistedUriPermission = persistedUriPermission,
        isFeaturedImage = isFeaturedImage
    )

private fun Instant?.toEpochMillisOrNull(): Long? = this?.toEpochMilli()

private fun LocalDate?.toIsoStringOrNull(): String? = this?.toString()
