package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlockBlueprint
import java.util.Locale

private const val DAY_IN_MINUTES = 1440

fun formatDayDialMinute(minute: Int): String {
    val normalized = ((minute % DAY_IN_MINUTES) + DAY_IN_MINUTES) % DAY_IN_MINUTES
    val h = (normalized / 60) % 24
    val m = normalized % 60
    return String.format(Locale.getDefault(), "%02d:%02d", h, m)
}

internal fun parseDayDialBackupBlocks(text: String): List<TemplateBlockBlueprint> {
    return text.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("block|") }
        .mapNotNull { line ->
            val parts = splitBackupFields(line)
            if (parts.size < 5) return@mapNotNull null
            val start = parseMinuteOfDay(parts[2]) ?: return@mapNotNull null
            val duration = parts[3].toIntOrNull()?.coerceIn(5, 240) ?: return@mapNotNull null
            TemplateBlockBlueprint(
                title = parts[1].ifBlank { "Imported Block" },
                startMinute = start,
                durationMinutes = duration,
                category = parts[4].ifBlank { "WORK" }
            )
        }
        .toList()
}

internal fun escapeDayDialBackupField(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '|' -> append("\\|")
            '\n', '\r' -> append(' ')
            else -> append(char)
        }
    }
}

fun inferDayDialCategory(block: TimeBlockUiModel): String = when {
    block.title.contains("meeting", ignoreCase = true) -> "Meeting"
    block.title.contains("break", ignoreCase = true) -> "Break"
    block.title.contains("admin", ignoreCase = true) -> "Admin"
    block.title.contains("personal", ignoreCase = true) -> "Personal"
    block.provenance.contains("AI", ignoreCase = true) -> "AI"
    else -> "Work"
}

fun findActiveBlock(
    blocks: List<TimeBlockUiModel>,
    currentMinute: Int,
    forToday: Boolean = true
): TimeBlockUiModel? {
    if (!forToday) return null
    return blocks.filterNot { it.isAllDayCalendarImport() }.firstOrNull { block ->
        if (block.actualEndMinuteOfDay != null) {
            return@firstOrNull false
        }
        val end = block.startMinuteOfDay + block.durationMinutes
        if (end <= DAY_IN_MINUTES) {
            currentMinute in block.startMinuteOfDay until end
        } else {
            currentMinute >= block.startMinuteOfDay || currentMinute < end % DAY_IN_MINUTES
        }
    }
}

fun findNextBlock(
    blocks: List<TimeBlockUiModel>,
    currentMinute: Int,
    forToday: Boolean = true
): TimeBlockUiModel? {
    val timelineBlocks = blocks.filterNot { it.isAllDayCalendarImport() }
    if (!forToday) {
        return timelineBlocks.minByOrNull { it.startMinuteOfDay }
    }
    return timelineBlocks
        .filter { it.actualEndMinuteOfDay == null }
        .filter { it.startMinuteOfDay > currentMinute }
        .minByOrNull { it.startMinuteOfDay }
}

private fun splitBackupFields(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var escaping = false
    line.forEach { char ->
        when {
            escaping -> {
                current.append(char)
                escaping = false
            }
            char == '\\' -> escaping = true
            char == '|' -> {
                fields += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
    }
    if (escaping) current.append('\\')
    fields += current.toString()
    return fields
}
