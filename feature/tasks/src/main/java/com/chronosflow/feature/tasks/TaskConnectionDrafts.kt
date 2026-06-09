package com.chronosflow.feature.tasks

import com.chronosflow.core.domain.model.TaskAction
import com.chronosflow.core.domain.model.TaskActionType
import com.chronosflow.core.domain.model.normalizeAppLaunchValue

internal data class TaskActionDraft(
    val id: String,
    val type: TaskActionType,
    val label: String,
    val value: String,
    val isPrimary: Boolean
)

internal fun normalizeTaskActionDraft(draft: TaskActionDraft): TaskAction? {
    val label = draft.label.trim()
    val value = draft.value.trim()
    if (label.isEmpty() || value.isEmpty()) return null

    val normalizedValue = when (draft.type) {
        TaskActionType.WEBSITE,
        TaskActionType.DOCUMENT -> normalizeWebLikeValue(value) ?: return null
        TaskActionType.PHONE -> normalizePhoneValue(value) ?: return null
        TaskActionType.EMAIL -> normalizeEmailValue(value) ?: return null
        TaskActionType.MAP,
        TaskActionType.CUSTOM_DEEP_LINK -> value
        TaskActionType.APP -> normalizeAppLaunchValue(value) ?: return null
    }

    return TaskAction(
        id = draft.id,
        type = draft.type,
        label = label,
        value = normalizedValue,
        isPrimary = draft.isPrimary
    )
}

internal fun normalizeTaskActionDrafts(drafts: List<TaskActionDraft>): List<TaskAction> {
    val normalized = drafts.mapNotNull(::normalizeTaskActionDraft)
    val primaryIndex = normalized.indexOfFirst { it.isPrimary }
    if (primaryIndex <= 0) return normalized
    return normalized.mapIndexed { index, action ->
        action.copy(isPrimary = index == primaryIndex)
    }
}

internal fun hasInvalidTaskActionDraft(drafts: List<TaskActionDraft>): Boolean {
    return drafts.any { draft ->
        val hasAnyContent = draft.label.isNotBlank() || draft.value.isNotBlank()
        hasAnyContent && normalizeTaskActionDraft(draft) == null
    }
}

private fun normalizeWebLikeValue(value: String): String? {
    return when {
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true) -> value
        "://" in value -> null
        else -> "https://$value"
    }
}

private fun normalizePhoneValue(value: String): String? {
    val normalized = value.filterNot(Char::isWhitespace)
    return normalized.takeIf { candidate ->
        candidate.isNotEmpty() && candidate.all { it.isDigit() || it == '+' || it == '-' || it == '(' || it == ')' }
    }
}

private fun normalizeEmailValue(value: String): String? {
    return value.takeIf { '@' in it && !it.startsWith("mailto:", ignoreCase = true) }
}
