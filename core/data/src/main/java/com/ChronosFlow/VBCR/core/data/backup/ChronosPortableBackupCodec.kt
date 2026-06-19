package com.ChronosFlow.VBCR.core.data.backup

import com.ChronosFlow.VBCR.core.data.sync.RemoteSyncBatch
import com.ChronosFlow.VBCR.core.data.sync.RemoteTaskActionEntity
import com.ChronosFlow.VBCR.core.data.sync.RemoteTaskAttachmentEntity
import com.ChronosFlow.VBCR.core.data.sync.RemoteTaskChecklistItemEntity
import com.ChronosFlow.VBCR.core.data.sync.RemoteTaskContactEntity
import com.ChronosFlow.VBCR.core.data.sync.RemoteTaskContactMethodEntity
import com.ChronosFlow.VBCR.core.data.sync.RemoteTaskEntity
import com.ChronosFlow.VBCR.core.data.sync.RemoteTimeBlockEntity
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ChronosPortableBackupCodec @Inject constructor() {
    fun encode(batch: RemoteSyncBatch): String = JSONObject()
        .put(KEY_FORMAT_VERSION, FORMAT_VERSION)
        .put(KEY_TASKS, JSONArray(batch.tasks.map { it.toJson() }))
        .put(KEY_TIME_BLOCKS, JSONArray(batch.timeBlocks.map { it.toJson() }))
        .toString()

    fun decode(value: String): RemoteSyncBatch {
        val root = JSONObject(value)
        val version = root.optInt(KEY_FORMAT_VERSION, 0)
        require(version == FORMAT_VERSION) { "Unsupported ChronosFlow backup format version: $version" }
        return RemoteSyncBatch(
            tasks = root.optJSONArray(KEY_TASKS).orEmptyObjects { it.toRemoteTaskEntity() },
            timeBlocks = root.optJSONArray(KEY_TIME_BLOCKS).orEmptyObjects { it.toRemoteTimeBlockEntity() }
        )
    }

    private fun RemoteTaskEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .putNullable("description", description)
        .put("isCompleted", isCompleted)
        .put("priority", priority)
        .putNullable("dueDateEpochMillis", dueDateEpochMillis)
        .put("createdAtEpochMillis", createdAtEpochMillis)
        .put("updatedAtEpochMillis", updatedAtEpochMillis)
        .putNullable("preferredDurationMinutes", preferredDurationMinutes)
        .putNullable("preferredStartMinuteOfDay", preferredStartMinuteOfDay)
        .putNullable("targetDate", targetDate)
        .put("checklist", JSONArray(checklist.map { it.toJson() }))
        .putNullable("linkedContact", linkedContact?.toJson())
        .put("actions", JSONArray(actions.map { it.toJson() }))
        .put("attachments", JSONArray(attachments.map { it.toJson() }))

    private fun RemoteTaskChecklistItemEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("isCompleted", isCompleted)

    private fun RemoteTaskContactEntity.toJson(): JSONObject = JSONObject()
        .put("displayName", displayName)
        .putNullable("lookupKey", lookupKey)
        .put("methods", JSONArray(methods.map { it.toJson() }))

    private fun RemoteTaskContactMethodEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind)
        .putNullable("label", label)
        .put("value", value)
        .putNullable("normalizedValue", normalizedValue)
        .put("isPrimary", isPrimary)

    private fun RemoteTaskActionEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type)
        .put("label", label)
        .put("value", value)
        .put("isPrimary", isPrimary)

    private fun RemoteTaskAttachmentEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("displayName", displayName)
        .putNullable("mimeType", mimeType)
        .putNullable("sizeBytes", sizeBytes)
        .put("kind", kind)
        .put("storageMode", storageMode)
        .put("reference", reference)
        .put("persistedUriPermission", persistedUriPermission)
        .put("isFeaturedImage", isFeaturedImage)

    private fun RemoteTimeBlockEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("date", date)
        .put("title", title)
        .put("category", category)
        .put("startMinuteOfDay", startMinuteOfDay)
        .put("durationMinutes", durationMinutes)
        .put("timezone", timezone)
        .put("source", source)
        .put("provenance", provenance)
        .put("flexibility", flexibility)
        .put("energyLevel", energyLevel)
        .putNullable("taskId", taskId)
        .putNullable("calendarEventId", calendarEventId)
        .putNullable("medicationPlanId", medicationPlanId)
        .putNullable("habitId", habitId)
        .put("isLocked", isLocked)
        .put("isProtected", isProtected)
        .putNullable("recurrenceRuleId", recurrenceRuleId)
        .putNullable("taskOccurrenceDate", taskOccurrenceDate)
        .putNullable("actualStartMinuteOfDay", actualStartMinuteOfDay)
        .putNullable("actualEndMinuteOfDay", actualEndMinuteOfDay)
        .put("createdAtEpochMillis", createdAtEpochMillis)
        .put("updatedAtEpochMillis", updatedAtEpochMillis)

    private fun JSONObject.toRemoteTaskEntity(): RemoteTaskEntity = RemoteTaskEntity(
        id = getString("id"),
        title = getString("title"),
        description = nullableString("description"),
        isCompleted = getBoolean("isCompleted"),
        priority = getInt("priority"),
        dueDateEpochMillis = nullableLong("dueDateEpochMillis"),
        createdAtEpochMillis = getLong("createdAtEpochMillis"),
        updatedAtEpochMillis = getLong("updatedAtEpochMillis"),
        preferredDurationMinutes = nullableInt("preferredDurationMinutes"),
        preferredStartMinuteOfDay = nullableInt("preferredStartMinuteOfDay"),
        targetDate = nullableString("targetDate"),
        checklist = optJSONArray("checklist").orEmptyObjects { it.toRemoteTaskChecklistItemEntity() },
        linkedContact = nullableObject("linkedContact")?.toRemoteTaskContactEntity(),
        actions = optJSONArray("actions").orEmptyObjects { it.toRemoteTaskActionEntity() },
        attachments = optJSONArray("attachments").orEmptyObjects { it.toRemoteTaskAttachmentEntity() }
    )

    private fun JSONObject.toRemoteTaskChecklistItemEntity(): RemoteTaskChecklistItemEntity =
        RemoteTaskChecklistItemEntity(
            id = getString("id"),
            label = getString("label"),
            isCompleted = getBoolean("isCompleted")
        )

    private fun JSONObject.toRemoteTaskContactEntity(): RemoteTaskContactEntity =
        RemoteTaskContactEntity(
            displayName = getString("displayName"),
            lookupKey = nullableString("lookupKey"),
            methods = optJSONArray("methods").orEmptyObjects { it.toRemoteTaskContactMethodEntity() }
        )

    private fun JSONObject.toRemoteTaskContactMethodEntity(): RemoteTaskContactMethodEntity =
        RemoteTaskContactMethodEntity(
            id = getString("id"),
            kind = getString("kind"),
            label = nullableString("label"),
            value = getString("value"),
            normalizedValue = nullableString("normalizedValue"),
            isPrimary = getBoolean("isPrimary")
        )

    private fun JSONObject.toRemoteTaskActionEntity(): RemoteTaskActionEntity =
        RemoteTaskActionEntity(
            id = getString("id"),
            type = getString("type"),
            label = getString("label"),
            value = getString("value"),
            isPrimary = getBoolean("isPrimary")
        )

    private fun JSONObject.toRemoteTaskAttachmentEntity(): RemoteTaskAttachmentEntity =
        RemoteTaskAttachmentEntity(
            id = getString("id"),
            displayName = getString("displayName"),
            mimeType = nullableString("mimeType"),
            sizeBytes = nullableLong("sizeBytes"),
            kind = getString("kind"),
            storageMode = getString("storageMode"),
            reference = getString("reference"),
            persistedUriPermission = getBoolean("persistedUriPermission"),
            isFeaturedImage = getBoolean("isFeaturedImage")
        )

    private fun JSONObject.toRemoteTimeBlockEntity(): RemoteTimeBlockEntity = RemoteTimeBlockEntity(
        id = getString("id"),
        date = getString("date"),
        title = getString("title"),
        category = getString("category"),
        startMinuteOfDay = getInt("startMinuteOfDay"),
        durationMinutes = getInt("durationMinutes"),
        timezone = getString("timezone"),
        source = getString("source"),
        provenance = getString("provenance"),
        flexibility = getString("flexibility"),
        energyLevel = getInt("energyLevel"),
        taskId = nullableString("taskId"),
        calendarEventId = nullableLong("calendarEventId"),
        medicationPlanId = nullableString("medicationPlanId"),
        habitId = nullableString("habitId"),
        isLocked = getBoolean("isLocked"),
        isProtected = getBoolean("isProtected"),
        recurrenceRuleId = nullableString("recurrenceRuleId"),
        taskOccurrenceDate = nullableString("taskOccurrenceDate"),
        actualStartMinuteOfDay = nullableInt("actualStartMinuteOfDay"),
        actualEndMinuteOfDay = nullableInt("actualEndMinuteOfDay"),
        createdAtEpochMillis = getLong("createdAtEpochMillis"),
        updatedAtEpochMillis = getLong("updatedAtEpochMillis")
    )

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.nullableLong(name: String): Long? =
        if (isNull(name)) null else getLong(name)

    private fun JSONObject.nullableInt(name: String): Int? =
        if (isNull(name)) null else getInt(name)

    private fun JSONObject.nullableObject(name: String): JSONObject? =
        if (isNull(name)) null else getJSONObject(name)

    private inline fun <T> JSONArray?.orEmptyObjects(transform: (JSONObject) -> T): List<T> =
        if (this == null) {
            emptyList()
        } else {
            (0 until length()).map { index -> transform(getJSONObject(index)) }
        }

    private companion object {
        const val FORMAT_VERSION = 1
        const val KEY_FORMAT_VERSION = "formatVersion"
        const val KEY_TASKS = "tasks"
        const val KEY_TIME_BLOCKS = "timeBlocks"
    }
}
