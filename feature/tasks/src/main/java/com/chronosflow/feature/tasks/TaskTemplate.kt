package com.chronosflow.feature.tasks

import java.time.DayOfWeek
import java.util.Base64
import java.util.UUID

/**
 * A reusable shape for a frequently-created task. Captures the core authoring fields (not one-off
 * context like a specific contact/date) so a user can spin up a familiar task in one tap.
 *
 * Persisted as a compact, dependency-free string (see [encodeTaskTemplates]) in the existing UI
 * settings DataStore — no Room table/migration required.
 */
internal data class TaskTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val priority: Int = 0,
    val durationMinutes: Int? = null,
    val preferredStartMinute: Int? = null,
    val checklistLabels: List<String> = emptyList(),
    val recurrenceCadence: TaskRecurringCadence? = null,
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdays: Set<DayOfWeek> = emptySet()
)

private const val TEMPLATE_FIELD_DELIM = "|"
private const val TEMPLATE_RECORD_DELIM = "\n"
private const val TEMPLATE_LABEL_DELIM = ""

private fun encodeField(value: String): String =
    Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

private fun decodeField(value: String): String =
    if (value.isEmpty()) "" else String(Base64.getDecoder().decode(value), Charsets.UTF_8)

/**
 * Encodes templates to a single string. Text fields are Base64-encoded so titles/labels can contain
 * any character (including the field/record delimiters) without corrupting the format.
 */
internal fun encodeTaskTemplates(templates: List<TaskTemplate>): String =
    templates.joinToString(TEMPLATE_RECORD_DELIM) { template ->
        listOf(
            encodeField(template.id),
            encodeField(template.name),
            encodeField(template.description.orEmpty()),
            template.priority.toString(),
            template.durationMinutes?.toString().orEmpty(),
            template.preferredStartMinute?.toString().orEmpty(),
            encodeField(template.checklistLabels.joinToString(TEMPLATE_LABEL_DELIM)),
            template.recurrenceCadence?.name.orEmpty(),
            template.recurrenceInterval.toString(),
            template.recurrenceWeekdays.joinToString(",") { it.name }
        ).joinToString(TEMPLATE_FIELD_DELIM)
    }

/**
 * Decodes [encodeTaskTemplates] output, silently dropping any malformed record. Records persisted
 * before the recurrence fields existed (7 fields) still decode — the extra fields default to "none".
 */
internal fun decodeTaskTemplates(encoded: String): List<TaskTemplate> {
    if (encoded.isBlank()) return emptyList()
    return encoded.split(TEMPLATE_RECORD_DELIM).mapNotNull { record ->
        val parts = record.split(TEMPLATE_FIELD_DELIM)
        if (parts.size < 7) return@mapNotNull null
        runCatching {
            val labels = decodeField(parts[6])
                .split(TEMPLATE_LABEL_DELIM)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val cadence = parts.getOrNull(7)
                ?.takeIf { it.isNotBlank() }
                ?.let { name -> TaskRecurringCadence.entries.firstOrNull { it.name == name } }
            val weekdays = parts.getOrNull(9).orEmpty()
                .split(",")
                .mapNotNull { token ->
                    token.takeIf { it.isNotBlank() }
                        ?.let { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                }
                .toSet()
            TaskTemplate(
                id = decodeField(parts[0]).ifBlank { UUID.randomUUID().toString() },
                name = decodeField(parts[1]),
                description = decodeField(parts[2]).ifBlank { null },
                priority = parts[3].toIntOrNull()?.coerceIn(0, 2) ?: 0,
                durationMinutes = parts[4].toIntOrNull(),
                preferredStartMinute = parts[5].toIntOrNull(),
                checklistLabels = labels,
                recurrenceCadence = cadence,
                recurrenceInterval = parts.getOrNull(8)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                recurrenceWeekdays = weekdays
            )
        }.getOrNull()?.takeIf { it.name.isNotBlank() }
    }
}

/** Upserts a template by case-insensitive name, newest last, capped at [limit]. */
internal fun upsertTaskTemplate(
    existing: List<TaskTemplate>,
    template: TaskTemplate,
    limit: Int = 12
): List<TaskTemplate> {
    val deduped = existing.filterNot { it.name.trim().equals(template.name.trim(), ignoreCase = true) }
    return (deduped + template).takeLast(limit)
}
