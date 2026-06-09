package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.toTaskAssistSource
import com.chronosflow.core.domain.model.TaskActionType
import com.chronosflow.core.domain.model.normalizeAppLaunchValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class TaskAssistRequest(
    val title: String,
    val description: String = "",
    val priority: Int = 0,
    val targetDate: LocalDate? = null,
    val preferredDurationMinutes: Int? = null,
    val preferredStartMinuteOfDay: Int? = null,
    val checklistLabels: List<String> = emptyList()
)

sealed interface TaskAssistSuggestion {
    val id: String
    val label: String
    val reason: String
    val source: TaskAssistSource

    data class Title(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: TaskAssistSource,
        val title: String
    ) : TaskAssistSuggestion

    data class ActionDraft(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: TaskAssistSource,
        val payload: TaskAssistActionDraftPayload
    ) : TaskAssistSuggestion

    data class Schedule(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: TaskAssistSource,
        val payload: TaskAssistSchedulePayload
    ) : TaskAssistSuggestion

    data class Checklist(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: TaskAssistSource,
        val items: List<String>
    ) : TaskAssistSuggestion

    data class Priority(
        override val id: String,
        override val label: String,
        override val reason: String,
        override val source: TaskAssistSource,
        val priority: Int
    ) : TaskAssistSuggestion
}

enum class TaskAssistSource {
    GEMINI_NANO,
    CLOUD_GEMINI,
    LOCAL
}

data class TaskAssistActionDraftPayload(
    val type: TaskActionType,
    val label: String,
    val value: String
)

data class TaskAssistSchedulePayload(
    val targetDate: LocalDate? = null,
    val preferredDurationMinutes: Int? = null,
    val preferredStartMinuteOfDay: Int? = null
)

class TaskAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggest(request: TaskAssistRequest): List<TaskAssistSuggestion> {
        val generation = genAiAssistCoordinator.generateAssistText(buildPrompt(request))
        generation.text?.let { raw ->
            val parsed = parseAssistSuggestions(raw, generation.source.toTaskAssistSource())
            if (parsed.isNotEmpty()) return parsed
        }
        return localSuggestions(request)
    }

    private fun buildPrompt(request: TaskAssistRequest): String {
        return buildString {
            appendLine("Parse this typed or dictated capture into manual task suggestions for ChronosFlow.")
            appendLine("Return one suggestion per line as kind|label|value|reason.")
            appendLine("Kinds: title, action_phone, action_email, action_link, action_document, action_map, action_app, action_deep_link, schedule_date, schedule_duration, schedule_time, checklist, priority.")
            appendLine("Extract only fields supported by the user text. Prefer concise task titles, explicit dates/times/durations, concrete next steps, and launch/contact actions.")
            appendLine("If the text sounds like medication or habit tracking, still return task-safe scheduling/checklist suggestions; do not invent clinical or wellness advice.")
            appendLine("Do not say to apply automatically.")
            appendLine("Examples:")
            appendLine("Input: call mom tomorrow")
            appendLine("title|Call mom|Call mom|Remove the timing word from the title.")
            appendLine("action_phone|Call mom||The user wants a call action but did not provide a number.")
            appendLine("schedule_date|Target tomorrow|tomorrow|The capture names tomorrow.")
            appendLine("schedule_duration|Quick call|15m|Calls usually need a short protected block.")
            appendLine("checklist|Call steps|Confirm the right contact;Make the call;Capture follow-up|Make the task actionable.")
            appendLine("Input: review launch brief https://docs.example.com/brief at 2pm for 45 minutes")
            appendLine("action_document|Open brief|https://docs.example.com/brief|The capture includes a document link.")
            appendLine("schedule_time|Start at 2 PM|2pm|The capture names a start time.")
            appendLine("schedule_duration|45m block|45m|The capture names a duration.")
            appendLine("Input: ask Alex about the invoice by Friday")
            appendLine("title|Ask Alex about the invoice|Ask Alex about the invoice|Remove deadline wording but preserve the message intent.")
            appendLine("action_phone|Message Alex||The user wants to ask someone, but no phone or email destination was provided.")
            appendLine("schedule_date|Target Friday|by Friday|The capture names a deadline day.")
            appendLine("schedule_duration|Quick message|15m|Messages usually need a short protected block.")
            appendLine("checklist|Message steps|Confirm the right recipient;Send the message;Capture any follow-up|Make the task actionable.")
            appendLine("Input: email Jordan the proposal tomorrow")
            appendLine("title|Email Jordan the proposal|Email Jordan the proposal|Remove timing words but preserve the email intent and recipient.")
            appendLine("action_email|Email Jordan||The user wants an email action but did not provide an address.")
            appendLine("schedule_date|Target tomorrow|tomorrow|The capture names tomorrow.")
            appendLine("schedule_duration|Quick email|15m|Email tasks usually need a short protected block.")
            appendLine("checklist|Email steps|Confirm recipient and attachment;Send the email;Capture any follow-up|Make the email actionable.")
            appendLine("Input: reply to email from Jordan by Friday")
            appendLine("title|Reply to email from Jordan|Reply to email from Jordan|Remove deadline wording but preserve the email-reply intent.")
            appendLine("action_email|Reply to Jordan||The user wants an email reply but did not provide an address.")
            appendLine("schedule_date|Target Friday|by Friday|The capture names a deadline day.")
            appendLine("schedule_duration|Quick email reply|15m|Email replies usually need a short protected block.")
            appendLine("checklist|Reply steps|Review the original email;Send the reply;Capture any follow-up|Make the reply actionable.")
            appendLine("Input: pay utility bill by Friday")
            appendLine("title|Pay utility bill|Pay utility bill|Remove deadline wording but preserve the payment intent.")
            appendLine("schedule_date|Target Friday|by Friday|The capture names a deadline day.")
            appendLine("schedule_duration|Quick payment|15m|Bill payments usually need a short protected block.")
            appendLine("checklist|Payment steps|Confirm amount and account;Submit payment;Save confirmation|Make the payment task actionable.")
            appendLine("Input: text Priya the address tonight")
            appendLine("title|Text Priya the address|Text Priya the address|Remove timing words but preserve the message intent and recipient.")
            appendLine("action_phone|Text Priya||The user wants a text action but did not provide a number.")
            appendLine("schedule_date|Target tonight|tonight|The capture names tonight.")
            appendLine("schedule_duration|Quick text|10m|Text tasks usually need a short block.")
            appendLine("checklist|Text steps|Confirm the right recipient;Send the address;Capture any reply|Make the message actionable.")
            appendLine("Input: buy groceries tomorrow")
            appendLine("title|Buy groceries|Buy groceries|Remove timing words but keep the purchase intent.")
            appendLine("schedule_date|Target tomorrow|tomorrow|The capture names tomorrow.")
            appendLine("schedule_duration|Errand block|30m|Shopping errands usually need a short protected block.")
            appendLine("checklist|Shopping steps|List needed items;Check what is already at home;Buy the missing items|Make the purchase task actionable.")
            appendLine("Input: buy birthday gift for Maya by Saturday")
            appendLine("title|Buy birthday gift for Maya|Buy birthday gift for Maya|Remove deadline wording but preserve the gift and recipient.")
            appendLine("schedule_date|Target Saturday|by Saturday|The capture names a deadline day.")
            appendLine("schedule_duration|Gift errand|30m|Gift tasks usually need a short protected block.")
            appendLine("checklist|Gift steps|Choose gift or card;Buy or order it;Wrap or send it before the event|Make the event task actionable.")
            appendLine("Input: return package Friday")
            appendLine("title|Return package|Return package|Remove deadline wording but keep the errand intent.")
            appendLine("schedule_date|Target Friday|Friday|The capture names Friday.")
            appendLine("schedule_duration|Errand block|30m|Package returns usually need a short protected block.")
            appendLine("checklist|Return steps|Find return label and package;Confirm drop-off location;Drop off the package|Make the return task actionable.")
            appendLine("Input: clean kitchen tonight")
            appendLine("title|Clean kitchen|Clean kitchen|Remove timing words but preserve the home task.")
            appendLine("schedule_date|Target tonight|tonight|The capture names tonight.")
            appendLine("schedule_duration|Chore block|30m|Household chores usually need a short protected block.")
            appendLine("checklist|Chore steps|Gather supplies;Do the quick chore;Reset anything needed afterward|Make the home task actionable.")
            appendLine("Input: mail return package at post office Friday")
            appendLine("title|Mail return package at post office|Mail return package at post office|Preserve the shipping action and location while removing date words.")
            appendLine("schedule_date|Target Friday|Friday|The capture names Friday.")
            appendLine("schedule_duration|Shipping errand|30m|Shipping tasks usually need a protected errand block.")
            appendLine("checklist|Shipping steps|Find return label and package;Confirm carrier or drop-off location;Save receipt or tracking|Make the shipping task actionable.")
            appendLine("Input: meeting with Sam tomorrow at 10am for 30 minutes")
            appendLine("title|Meeting with Sam|Meeting with Sam|Remove timing words but preserve the meeting context.")
            appendLine("schedule_date|Target tomorrow|tomorrow|The capture names tomorrow.")
            appendLine("schedule_time|Start at 10 AM|10am|The capture names a start time.")
            appendLine("schedule_duration|30m meeting|30m|The capture names a duration.")
            appendLine("checklist|Meeting prep|Confirm agenda;Bring notes or links;Capture follow-up actions|Make the meeting actionable.")
            appendLine("Input: rsvp to meeting invite by Friday")
            appendLine("title|RSVP to meeting invite|RSVP to meeting invite|Remove deadline wording but preserve the invite-response intent.")
            appendLine("schedule_date|Target Friday|by Friday|The capture names a deadline day.")
            appendLine("schedule_duration|Quick RSVP|10m|Invite responses usually need a short protected block.")
            appendLine("checklist|Invite steps|Review meeting details;Send RSVP;Add calendar or follow-up notes|Make the invite task actionable.")
            appendLine("Input: send calendar invite to Sam tomorrow")
            appendLine("title|Send calendar invite to Sam|Send calendar invite to Sam|Remove timing wording but preserve the outbound invite intent.")
            appendLine("schedule_date|Target tomorrow|tomorrow|The capture names a target day.")
            appendLine("schedule_duration|Invite setup|15m|Sending an invite usually needs a short planning block.")
            appendLine("checklist|Invite setup|Confirm attendees;Add agenda or location;Send calendar invite|Make the outbound invite actionable.")
            appendLine("Input: book dentist appointment next Friday at noon")
            appendLine("title|Book dentist appointment|Book dentist appointment|Remove timing words but preserve the appointment intent.")
            appendLine("schedule_date|Target next Friday|next Friday|The capture names a target day.")
            appendLine("schedule_time|Start at noon|noon|The capture names noon.")
            appendLine("schedule_duration|Appointment block|45m|Appointments usually need a protected block plus buffer.")
            appendLine("checklist|Appointment prep|Confirm provider and location;Bring insurance or forms;Add travel buffer|Make the appointment actionable.")
            appendLine("Input: drive to CVS tomorrow at 5pm")
            appendLine("title|Drive to CVS|Drive to CVS|Keep the destination in the title and remove timing words.")
            appendLine("action_map|Map to CVS|CVS|The capture includes a natural destination.")
            appendLine("schedule_date|Target tomorrow|tomorrow|The capture names tomorrow.")
            appendLine("schedule_time|Start at 5 PM|5pm|The capture names a start time.")
            appendLine("checklist|Travel steps|Confirm the destination;Check travel time;Leave with enough buffer|Make the errand easier to execute.")
            appendLine("Input: drop off package at UPS tomorrow")
            appendLine("title|Drop off package at UPS|Drop off package at UPS|Preserve the destination and remove timing words.")
            appendLine("action_map|Map to UPS|UPS|The capture includes a natural destination.")
            appendLine("schedule_date|Target tomorrow|tomorrow|The capture names tomorrow.")
            appendLine("schedule_duration|Errand block|30m|Drop-offs usually need a protected travel block.")
            appendLine("checklist|Drop-off steps|Confirm package and label;Check drop-off location;Leave with enough buffer|Make the travel errand actionable.")
            appendLine("Input: open Spotify focus playlist tomorrow morning")
            appendLine("title|Open Spotify focus playlist|Open Spotify focus playlist|Keep the app task intent and remove timing words.")
            appendLine("action_app|Open Spotify|spotify://|The capture names a known app launch target.")
            appendLine("schedule_date|Target tomorrow|tomorrow|The capture names tomorrow.")
            appendLine("schedule_time|Start in the morning|morning|The capture names a morning window.")
            appendLine("schedule_duration|Quick app task|15m|App launch tasks usually need a short protected block.")
            appendLine("checklist|App task steps|Open Spotify;Start the focus playlist;Return to the planned work|Make the app-assisted task actionable.")
            appendLine("Input: asap call mom")
            appendLine("title|Call mom|Call mom|Remove priority wording from the title.")
            appendLine("action_phone|Call mom|mom|The capture is a phone call.")
            appendLine("priority|Mark urgent|urgent|ASAP means the task should be treated as urgent.")
            appendLine("checklist|Call steps|Find the right number;Make the call;Note any follow-up|Add useful call follow-through steps.")
            appendLine("Title: ${request.title}")
            appendLine("Description: ${request.description}")
            appendLine("Priority: ${request.priority}")
            appendLine("Target date: ${request.targetDate ?: "none"}")
            appendLine("Duration minutes: ${request.preferredDurationMinutes ?: "none"}")
            appendLine("Start minute: ${request.preferredStartMinuteOfDay ?: "none"}")
            appendLine("Checklist: ${request.checklistLabels.joinToString("; ").ifBlank { "none" }}")
        }
    }

    private fun parseAssistSuggestions(text: String, source: TaskAssistSource): List<TaskAssistSuggestion> {
        return text.lineSequence()
            .map { it.trim().trim('-', '*') }
            .filter { it.isNotBlank() && it.count { char -> char == '|' } >= 3 }
            .mapIndexedNotNull { index, line ->
                val parts = line.split("|").map { it.trim() }
                val kind = parts.getOrNull(0)?.lowercase(Locale.getDefault()) ?: return@mapIndexedNotNull null
                val label = parts.getOrNull(1).orEmpty()
                val value = parts.getOrNull(2).orEmpty()
                val reason = parts.drop(3).joinToString("|").ifBlank { defaultReasonFor(source) }
                suggestionFromParts(index, kind, label, value, reason, source)
            }
            .take(MAX_SUGGESTIONS)
            .toList()
    }

    private fun defaultReasonFor(source: TaskAssistSource): String = when (source) {
        TaskAssistSource.GEMINI_NANO -> "Suggested with Gemini Nano on-device."
        TaskAssistSource.CLOUD_GEMINI -> "Suggested with cloud Gemini."
        TaskAssistSource.LOCAL -> "Suggested locally."
    }

    private fun suggestionFromParts(
        index: Int,
        kind: String,
        label: String,
        value: String,
        reason: String,
        source: TaskAssistSource
    ): TaskAssistSuggestion? {
        val id = "${source.name.lowercase(Locale.getDefault())}:$kind:$index"
        return when (kind) {
            "title" -> value.takeIf { it.isNotBlank() }?.let {
                TaskAssistSuggestion.Title(id, label.ifBlank { it }, reason, source, it)
            }
            "action_phone" -> actionSuggestion(id, label, value, reason, source, TaskActionType.PHONE, "Call")
            "action_email" -> actionSuggestion(id, label, value, reason, source, TaskActionType.EMAIL, "Email")
            "action_link" -> actionSuggestion(id, label, value, reason, source, TaskActionType.WEBSITE, "Link")
            "action_document" -> actionSuggestion(id, label, value, reason, source, TaskActionType.DOCUMENT, "Document")
            "action_map" -> actionSuggestion(id, label, value, reason, source, TaskActionType.MAP, "Map")
            "action_app" -> actionSuggestion(
                id,
                label,
                normalizeAppLaunchValue(value) ?: value,
                reason,
                source,
                TaskActionType.APP,
                "Open app"
            )
            "action_deep_link" -> actionSuggestion(
                id,
                label,
                value,
                reason,
                source,
                TaskActionType.CUSTOM_DEEP_LINK,
                "Deep link"
            )
            "schedule_duration" -> parseSuggestedDurationMinutes(value)?.let {
                TaskAssistSuggestion.Schedule(
                    id = id,
                    label = label.ifBlank { "${it}m block" },
                    reason = reason,
                    source = source,
                    payload = TaskAssistSchedulePayload(preferredDurationMinutes = it)
                )
            }
            "schedule_time" -> parseSuggestedStartMinute(value)?.let {
                TaskAssistSuggestion.Schedule(
                    id = id,
                    label = label.ifBlank { "Start around ${it / 60}:${(it % 60).toString().padStart(2, '0')}" },
                    reason = reason,
                    source = source,
                    payload = TaskAssistSchedulePayload(preferredStartMinuteOfDay = it)
                )
            }
            "schedule_date" -> parseSuggestedDate(value)?.let {
                TaskAssistSuggestion.Schedule(
                    id = id,
                    label = label.ifBlank { "Target ${it}" },
                    reason = reason,
                    source = source,
                    payload = TaskAssistSchedulePayload(targetDate = it)
                )
            }
            "checklist" -> {
                val items = value.split(";", ",").map { it.trim() }.filter { it.isNotBlank() }.take(5)
                items.takeIf { it.isNotEmpty() }?.let {
                    TaskAssistSuggestion.Checklist(id, label.ifBlank { "Add checklist" }, reason, source, it)
                }
            }
            "priority" -> parseSuggestedPriority(value)?.let {
                TaskAssistSuggestion.Priority(id, label.ifBlank { "Set priority" }, reason, source, it)
            }
            else -> null
        }
    }

    private fun actionSuggestion(
        id: String,
        label: String,
        value: String,
        reason: String,
        source: TaskAssistSource,
        type: TaskActionType,
        fallbackLabel: String
    ): TaskAssistSuggestion? {
        val actionLabel = label.ifBlank { fallbackLabel }
        val normalizedValue = value.trimContactDestination()
        return TaskAssistSuggestion.ActionDraft(
            id = id,
            label = actionLabel,
            reason = reason,
            source = source,
            payload = TaskAssistActionDraftPayload(type, actionLabel, normalizedValue)
        )
    }

    private fun localSuggestions(request: TaskAssistRequest): List<TaskAssistSuggestion> {
        val rawText = "${request.title} ${request.description}"
        val normalized = rawText.lowercase(Locale.getDefault())
        val emailDestination = firstEmailAddress(rawText)
        val phoneDestination = firstPhoneNumber(rawText)
        val documentDestination = firstDocumentLink(rawText, normalized)
        val linkDestination = firstWebLink(rawText, excludedDestination = documentDestination)
        val mapDestination = firstMapDestination(rawText)
        val appDestination = firstAppLaunchTarget(rawText, normalized)
        val deepLinkDestination = firstDeepLink(rawText, excludedDestination = appDestination)
        val capturedTitle = smartTaskTitleCandidate(rawText)
        val genericSuggestions = buildList {
            if (request.title.isBlank()) {
                add(
                    TaskAssistSuggestion.Title(
                        id = "local:title:0",
                        label = "Draft title",
                        reason = "Local suggestion because Gemini Nano is unavailable.",
                        source = TaskAssistSource.LOCAL,
                        title = "Follow up"
                    )
                )
            } else if (
                capturedTitle != null &&
                !capturedTitle.equals(request.title.trim(), ignoreCase = true)
            ) {
                add(
                    TaskAssistSuggestion.Title(
                        id = "local:title:captured",
                        label = capturedTitle,
                        reason = "Cleaned timing and routing words out of the capture.",
                        source = TaskAssistSource.LOCAL,
                        title = capturedTitle
                    )
                )
            }
            if (request.preferredDurationMinutes == null) {
                val explicitDuration = firstExplicitDurationMinutes(rawText)
                if (explicitDuration != null || rawText.isNotBlank()) {
                    val duration = explicitDuration ?: inferredTaskDurationMinutes(normalized)
                    add(
                        TaskAssistSuggestion.Schedule(
                            id = "local:schedule_duration:0",
                            label = localDurationLabel(duration),
                            reason = if (explicitDuration != null) {
                                "The task text names a duration."
                            } else {
                                "Matched the task shape to a protected planning block."
                            },
                            source = TaskAssistSource.LOCAL,
                            payload = TaskAssistSchedulePayload(preferredDurationMinutes = duration)
                        )
                    )
                }
            }
            if (request.checklistLabels.isEmpty() && request.title.isNotBlank()) {
                val checklistItems = contextualTaskChecklistItems(normalized, capturedTitle ?: request.title)
                add(
                    TaskAssistSuggestion.Checklist(
                        id = "local:checklist:0",
                        label = "Add relevant steps",
                        reason = "Matched the task type to concrete next steps.",
                        source = TaskAssistSource.LOCAL,
                        items = checklistItems
                    )
                )
            }
            if (request.priority < 2 && HIGH_PRIORITY_TEXT_HINTS.any { it in normalized }) {
                add(
                    TaskAssistSuggestion.Priority(
                        id = "local:priority:0",
                        label = "Mark urgent",
                        reason = "The task text looks time-sensitive.",
                        source = TaskAssistSource.LOCAL,
                        priority = 2
                    )
                )
            }
            if (request.targetDate == null) {
                val date = when {
                    "today" in normalized -> LocalDate.now()
                    "tonight" in normalized -> LocalDate.now()
                    "tomorrow" in normalized -> LocalDate.now().plusDays(1)
                    else -> firstExplicitTargetDate(rawText)
                }
                if (date != null) {
                    add(
                        TaskAssistSuggestion.Schedule(
                            id = "local:schedule_date:0",
                            label = localTargetDateLabel(date),
                            reason = "The task text names a target day.",
                            source = TaskAssistSource.LOCAL,
                            payload = TaskAssistSchedulePayload(targetDate = date)
                        )
                    )
                }
            }
            if (request.preferredStartMinuteOfDay == null) {
                val startMinute = firstExplicitStartMinute(rawText) ?: contextualStartMinute(normalized)
                if (startMinute != null) {
                    add(
                        TaskAssistSuggestion.Schedule(
                            id = "local:schedule_time:0",
                            label = localStartTimeLabel(startMinute),
                            reason = "The task text names a start time.",
                            source = TaskAssistSource.LOCAL,
                            payload = TaskAssistSchedulePayload(preferredStartMinuteOfDay = startMinute)
                        )
                    )
                }
            }
        }
        val actionSuggestions = buildList {
            val messageLike = Regex("""\b(text|message|sms|dm|ping|ask|tell|send)\b""").containsMatchIn(normalized)
            val replyLike = Regex("""\b(reply|respond|response|follow up|follow-up)\b""").containsMatchIn(normalized)
            if ("call" in normalized || "phone" in normalized || messageLike) {
                val fallbackLabel = if (messageLike && !("call" in normalized || "phone" in normalized)) {
                    "Message"
                } else {
                    "Call"
                }
                add(
                    TaskAssistSuggestion.ActionDraft(
                        id = "local:action_phone:0",
                        label = contextualTaskActionLabel(fallbackLabel, capturedTitle, "call", "phone", "text", "message", "sms"),
                        reason = if (messageLike) {
                            "The task text mentions a message."
                        } else {
                            "The task text mentions a call."
                        },
                        source = TaskAssistSource.LOCAL,
                        payload = TaskAssistActionDraftPayload(
                            TaskActionType.PHONE,
                            contextualTaskActionLabel(fallbackLabel, capturedTitle, "call", "phone", "text", "message", "sms"),
                            phoneDestination.orEmpty()
                        )
                    )
                )
            }
            if ("email" in normalized || "mail" in normalized || emailDestination != null || replyLike) {
                val fallbackLabel = if (replyLike && emailDestination == null) "Reply" else "Email"
                add(
                    TaskAssistSuggestion.ActionDraft(
                        id = "local:action_email:0",
                        label = contextualTaskActionLabel(fallbackLabel, capturedTitle, "email", "mail", "reply", "respond"),
                        reason = if (replyLike && emailDestination == null) {
                            "The task text mentions a reply or follow-up."
                        } else {
                            "The task text mentions email."
                        },
                        source = TaskAssistSource.LOCAL,
                        payload = TaskAssistActionDraftPayload(
                            TaskActionType.EMAIL,
                            contextualTaskActionLabel(fallbackLabel, capturedTitle, "email", "mail", "reply", "respond"),
                            emailDestination.orEmpty()
                        )
                    )
                )
            }
            if (linkDestination != null && linkDestination != documentDestination) {
                add(
                    TaskAssistSuggestion.ActionDraft(
                        id = "local:action_link:0",
                        label = "Link",
                        reason = "The task text includes a link.",
                        source = TaskAssistSource.LOCAL,
                        payload = TaskAssistActionDraftPayload(TaskActionType.WEBSITE, "Link", linkDestination)
                    )
                )
            }
            if (documentDestination != null) {
                add(
                    TaskAssistSuggestion.ActionDraft(
                        id = "local:action_document:0",
                        label = "Document",
                        reason = "The task text includes a document link.",
                        source = TaskAssistSource.LOCAL,
                        payload = TaskAssistActionDraftPayload(
                            TaskActionType.DOCUMENT,
                            "Document",
                            documentDestination
                        )
                    )
                )
            }
            if (mapDestination != null) {
                add(
                    TaskAssistSuggestion.ActionDraft(
                        id = "local:action_map:0",
                        label = "Map",
                        reason = "The task text includes a map destination.",
                        source = TaskAssistSource.LOCAL,
                        payload = TaskAssistActionDraftPayload(TaskActionType.MAP, "Map", mapDestination)
                    )
                )
            }
            if (appDestination != null) {
                add(
                    TaskAssistSuggestion.ActionDraft(
                        id = "local:action_app:0",
                        label = "Open app",
                        reason = "The task text includes an app launch target.",
                        source = TaskAssistSource.LOCAL,
                        payload = TaskAssistActionDraftPayload(TaskActionType.APP, "Open app", appDestination)
                    )
                )
            }
            if (deepLinkDestination != null) {
                add(
                    TaskAssistSuggestion.ActionDraft(
                        id = "local:action_deep_link:0",
                        label = "Deep link",
                        reason = "The task text includes an app link.",
                        source = TaskAssistSource.LOCAL,
                        payload = TaskAssistActionDraftPayload(
                            TaskActionType.CUSTOM_DEEP_LINK,
                            "Deep link",
                            deepLinkDestination
                        )
                    )
                )
            }
        }
        val genericLimit = MAX_SUGGESTIONS - actionSuggestions.size.coerceAtMost(MAX_SUGGESTIONS)
        return genericSuggestions.take(genericLimit) + actionSuggestions.take(MAX_SUGGESTIONS)
    }

    private fun localTargetDateLabel(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Target today"
            today.plusDays(1) -> "Target tomorrow"
            else -> "Target $date"
        }
    }

    private fun localStartTimeLabel(minuteOfDay: Int): String {
        return "Start around ${minuteOfDay / 60}:${(minuteOfDay % 60).toString().padStart(2, '0')}"
    }

    private fun localDurationLabel(durationMinutes: Int): String {
        return "${durationMinutes}m block"
    }

    private fun contextualTaskActionLabel(
        fallback: String,
        capturedTitle: String?,
        vararg prefixes: String
    ): String {
        return capturedTitle
            ?.trim()
            ?.takeIf { candidate ->
                candidate.length in 3..48 &&
                    prefixes.any { prefix -> candidate.startsWith(prefix, ignoreCase = true) }
            }
            ?: fallback
    }

    private fun contextualTaskChecklistItems(normalizedText: String, title: String): List<String> {
        val readableTitle = title.trim().ifBlank { "task" }
        return when {
            "call" in normalizedText || "phone" in normalizedText -> listOf(
                "Confirm the right contact",
                "Make the call",
                "Capture follow-up from $readableTitle"
            )
            "email" in normalizedText || "mail" in normalizedText || "reply" in normalizedText -> listOf(
                "Draft the message",
                "Attach or link context",
                "Send and note any follow-up"
            )
            Regex("""\b(text|message|sms|dm|ping|ask|tell|send)\b""").containsMatchIn(normalizedText) -> listOf(
                "Confirm the right recipient",
                "Send the message",
                "Capture any follow-up"
            )
            "pay" in normalizedText || "bill" in normalizedText || "invoice" in normalizedText -> listOf(
                "Confirm amount and due date",
                "Pay from the right account",
                "Save confirmation"
            )
            "grocery" in normalizedText || "groceries" in normalizedText || "buy" in normalizedText -> listOf(
                "List the needed items",
                "Check what is already at home",
                "Buy the missing items"
            )
            "return" in normalizedText && ("package" in normalizedText || "order" in normalizedText) -> listOf(
                "Find return label and package",
                "Confirm drop-off location",
                "Drop off the package"
            )
            APPOINTMENT_TEXT_HINTS.any { it in normalizedText } -> listOf(
                "Confirm provider and location",
                "Bring insurance or forms",
                "Add travel buffer"
            )
            "meeting" in normalizedText || "meet " in normalizedText -> listOf(
                "Confirm agenda or purpose",
                "Bring notes or links",
                "Capture follow-up actions"
            )
            "order" in normalizedText || "shop" in normalizedText -> listOf(
                "Confirm what is needed",
                "Place or pick up the order",
                "Save receipt or tracking"
            )
            TASK_APP_LAUNCH_HINTS.any { hint ->
                hint.terms.any { term ->
                    Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(normalizedText)
                }
            } -> listOf(
                "Open the app",
                "Complete the intended action",
                "Return to the planned work"
            )
            "drive to" in normalizedText ||
                "go to" in normalizedText ||
                "directions to" in normalizedText ||
                "navigate to" in normalizedText ||
                "meet at" in normalizedText ||
                "appointment at" in normalizedText ||
                "return" in normalizedText ||
                "bring" in normalizedText ||
                "deliver" in normalizedText ||
                "visit" in normalizedText -> listOf(
                    "Confirm the destination",
                    "Check travel time",
                    "Leave with enough buffer"
                )
            "water plants" in normalizedText || "walk dog" in normalizedText || "take out" in normalizedText || "trash" in normalizedText -> listOf(
                "Do the quick chore",
                "Reset anything needed afterward",
                "Mark complete"
            )
            "review" in normalizedText || "read" in normalizedText -> listOf(
                "Open the source material",
                "Capture key decisions",
                "Send or file the outcome"
            )
            else -> listOf("Clarify outcome", "Gather context", "Complete first pass")
        }
    }

    private fun smartTaskTitleCandidate(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.length < 8) return null
        val withoutLeadIn = TASK_CAPTURE_PREFIXES.fold(trimmed) { current, prefix ->
            current.replace(prefix, "")
        }
        val candidate = withoutLeadIn
            .replace(EXPLICIT_DURATION_PATTERN, " ")
            .replace(EXPLICIT_TIME_PATTERN, " ")
            .replace(TASK_TITLE_NOISE_PATTERN, " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', ',', ';', '-', ':')
            .take(MAX_TITLE_SUGGESTION_LENGTH)
            .trim()
        return candidate
            .takeIf { it.length >= 3 }
            ?.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }
    }

    private fun inferredTaskDurationMinutes(normalizedText: String): Int {
        return when {
            APPOINTMENT_TEXT_HINTS.any { it in normalizedText } -> 45
            QUICK_TASK_HINTS.any { it in normalizedText } -> 15
            TASK_APP_LAUNCH_HINTS.any { hint ->
                hint.terms.any { term ->
                    Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(normalizedText)
                }
            } -> 15
            DEEP_WORK_TASK_HINTS.any { it in normalizedText } -> 60
            else -> 30
        }
    }

    private fun contextualStartMinute(normalizedText: String): Int? {
        return when {
            "early morning" in normalizedText -> 7 * 60
            "morning" in normalizedText || "breakfast" in normalizedText -> 9 * 60
            "lunch" in normalizedText || "noon" in normalizedText -> 12 * 60
            "afternoon" in normalizedText -> 13 * 60
            "evening" in normalizedText || "dinner" in normalizedText -> 18 * 60
            "tonight" in normalizedText || "night" in normalizedText || "bedtime" in normalizedText -> 21 * 60
            else -> null
        }
    }

    private fun firstExplicitTargetDate(text: String): LocalDate? {
        return EXPLICIT_DATE_PATTERNS.asSequence()
            .flatMap { pattern -> pattern.findAll(text).map { match -> match.range.first to match.value } }
            .sortedBy { (index, _) -> index }
            .mapNotNull { (_, value) -> parseSuggestedDate(value) }
            .firstOrNull()
    }

    private fun firstExplicitStartMinute(text: String): Int? {
        return EXPLICIT_TIME_PATTERN.findAll(text)
            .mapNotNull { match -> parseSuggestedStartMinute(match.value) }
            .firstOrNull()
    }

    private fun firstExplicitDurationMinutes(text: String): Int? {
        return EXPLICIT_DURATION_PATTERN.findAll(text)
            .mapNotNull { match -> parseSuggestedDurationMinutes(match.value) }
            .firstOrNull()
    }

    private fun parseSuggestedDate(value: String): LocalDate? {
        val cleaned = value.trimGeneratedScalarValue().trimOrdinalDateSuffix()
        val normalized = cleaned.lowercase(Locale.getDefault())
        return when (normalized) {
            "today" -> LocalDate.now()
            "tonight" -> LocalDate.now()
            "tomorrow" -> LocalDate.now().plusDays(1)
            else -> parseRelativeDayOffsetDate(normalized)
                ?: parseRelativeWeekdayDate(normalized)
                ?: DATE_FORMATTERS.firstNotNullOfOrNull { formatter ->
                    runCatching { LocalDate.parse(cleaned, formatter) }.getOrNull()
                }
        }
    }

    private fun parseRelativeDayOffsetDate(value: String): LocalDate? {
        val match = RELATIVE_DAY_OFFSET_PATTERN.matchEntire(value) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2]
        return when {
            unit.startsWith("week") && amount in 1..52 -> LocalDate.now().plusWeeks(amount)
            unit.startsWith("day") && amount in 1..365 -> LocalDate.now().plusDays(amount)
            else -> null
        }
    }

    private fun parseRelativeWeekdayDate(value: String): LocalDate? {
        val match = NEXT_WEEKDAY_PATTERN.matchEntire(value) ?: return null
        val qualifier = match.groupValues[1]
        val weekday = WEEKDAY_NAMES[match.groupValues[2]] ?: return null
        return if (qualifier == "next") {
            LocalDate.now().with(TemporalAdjusters.next(weekday))
        } else {
            LocalDate.now().with(TemporalAdjusters.nextOrSame(weekday))
        }
    }

    private fun parseSuggestedStartMinute(value: String): Int? {
        val cleaned = value.trimGeneratedScalarValue()
        cleaned.toIntOrNull()?.let { return it.coerceIn(0, MINUTES_PER_DAY - 1) }

        val normalized = cleaned
            .lowercase(Locale.getDefault())
            .replace(".", "")
        when (normalized) {
            "noon" -> return 12 * 60
            "midnight" -> return 0
        }
        val match = CLOCK_TIME_PATTERN.matchEntire(normalized) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()
            ?: 0
        if (minute !in 0..59) return null

        val meridiem = match.groupValues.getOrNull(3).orEmpty()
        val hourOfDay = when (meridiem) {
            "am" -> if (hour == 12) 0 else hour
            "pm" -> if (hour == 12) 12 else hour + 12
            else -> hour
        }
        if (hourOfDay !in 0..23) return null
        return hourOfDay * 60 + minute
    }

    private fun parseSuggestedDurationMinutes(value: String): Int? {
        val cleaned = value.trimGeneratedScalarValue()
        cleaned.toIntOrNull()?.let { return it.coerceIn(5, 240) }

        val normalized = cleaned
            .lowercase(Locale.getDefault())
        val matches = DURATION_PART_PATTERN.findAll(normalized).toList()
        if (matches.isEmpty()) return null

        var minutes = 0
        matches.forEach { match ->
            val amount = match.groupValues[1].toDoubleOrNull() ?: return null
            val unit = match.groupValues[2]
            minutes += if (unit.startsWith("h")) {
                (amount * 60).roundToInt()
            } else {
                amount.roundToInt()
            }
        }
        return minutes.coerceIn(5, 240)
    }

    private fun parseSuggestedPriority(value: String): Int? {
        val cleaned = value.trimGeneratedScalarValue()
        cleaned.toIntOrNull()?.let { return it.coerceIn(0, 2) }

        return when (cleaned.lowercase(Locale.getDefault())) {
            "0", "low", "lowest", "later", "normal" -> 0
            "1", "medium", "med", "default" -> 1
            "2", "high", "highest", "urgent", "critical", "asap", "important", "high priority", "now" -> 2
            else -> null
        }
    }

    private fun firstEmailAddress(text: String): String? {
        return EMAIL_ADDRESS_PATTERN.find(text)?.value?.trimContactDestination()
    }

    private fun firstPhoneNumber(text: String): String? {
        return PHONE_NUMBER_PATTERN.findAll(text)
            .map { it.value.trimContactDestination() }
            .firstOrNull { candidate ->
                candidate.count(Char::isDigit) >= 7 && !DATE_LIKE_PATTERN.matches(candidate)
            }
    }

    private fun firstWebLink(text: String, excludedDestination: String? = null): String? {
        return WEB_LINK_PATTERN.findAll(text)
            .map { it.value.trimContactDestination() }
            .firstOrNull { it != excludedDestination }
    }

    private fun firstDocumentLink(text: String, normalizedText: String): String? {
        val links = WEB_LINK_PATTERN.findAll(text)
            .map { it.value.trimContactDestination() }
            .toList()
        if (links.isEmpty()) return null

        val hintedLink = links.firstOrNull { link ->
            DOCUMENT_LINK_HINTS.any { hint -> hint in link.lowercase(Locale.getDefault()) }
        }
        return hintedLink ?: links.firstOrNull().takeIf {
            DOCUMENT_TEXT_HINTS.any { hint -> hint in normalizedText }
        }
    }

    private fun firstMapDestination(text: String): String? {
        return MAP_URI_PATTERN.find(text)?.value?.trimContactDestination()
            ?: NATURAL_MAP_DESTINATION_PATTERN.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trimContactDestination()
                ?.trim()
                ?.takeIf { it.length >= 3 }
    }

    private fun firstAppLaunchTarget(text: String, normalizedText: String): String? {
        val explicitTarget = if (APP_TEXT_HINTS.any { it in normalizedText }) {
            APP_LAUNCH_PATTERN.findAll(text)
                .mapNotNull { match -> normalizeAppLaunchValue(match.value.trimContactDestination()) }
                .firstOrNull()
        } else {
            null
        }
        return explicitTarget ?: inferredTaskAppLaunchTarget(normalizedText)
    }

    private fun inferredTaskAppLaunchTarget(normalizedText: String): String? {
        return TASK_APP_LAUNCH_HINTS.firstOrNull { hint ->
            hint.terms.any { term ->
                Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(normalizedText)
            }
        }?.value
    }

    private fun firstDeepLink(text: String, excludedDestination: String? = null): String? {
        return DEEP_LINK_PATTERN.findAll(text)
            .map { it.value.trimContactDestination() }
            .firstOrNull { candidate ->
                candidate != excludedDestination &&
                    candidate.substringBefore(':').lowercase(Locale.getDefault()) !in WEB_LINK_SCHEMES
            }
    }

    private fun String.trimContactDestination(): String {
        return trim().trimEnd('.', ',', ';', ':', ')', ']')
    }

    private fun String.trimGeneratedScalarValue(): String {
        return trim().trimEnd('.', ',', ';', ':', ')', ']')
    }

    private fun String.trimOrdinalDateSuffix(): String {
        return replace(ORDINAL_DAY_SUFFIX_PATTERN, "$1")
    }

    private data class TaskAppLaunchHint(
        val value: String,
        val terms: List<String>
    )

    private companion object {
        const val MAX_SUGGESTIONS = 6
        const val MAX_TITLE_SUGGESTION_LENGTH = 72
        const val MINUTES_PER_DAY = 24 * 60
        val DATE_FORMATTERS = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d", Locale.getDefault()),
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.getDefault()),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()),
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.getDefault()),
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.getDefault())
        )
        val EMAIL_ADDRESS_PATTERN = Regex(
            pattern = """[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""",
            option = RegexOption.IGNORE_CASE
        )
        val CLOCK_TIME_PATTERN = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
        val DURATION_PART_PATTERN =
            Regex("""(\d+(?:\.\d+)?)\s*(hours|hour|hrs|hr|h|minutes|minute|mins|min|m)""")
        val PHONE_NUMBER_PATTERN = Regex("""(?<!\w)\+?\d[\d\s().-]{5,}\d(?!\w)""")
        val WEB_LINK_PATTERN = Regex("""(?<!\w)(?:https?://|www\.)[^\s<>()\[\]]+""", RegexOption.IGNORE_CASE)
        val MAP_URI_PATTERN = Regex("""(?<!\w)geo:[^\s<>()\[\]]+""", RegexOption.IGNORE_CASE)
        val NATURAL_MAP_DESTINATION_PATTERN = Regex(
            """(?i)\b(?:directions to|navigate to|drive to|go to|meet at|appointment at|visit|pick up .{1,32}? (?:from|at)|drop off .{1,32}? (?:to|at)|return .{1,32}? (?:to|at)|bring .{1,32}? (?:to|at)|deliver .{1,32}? (?:to|at))\s+([a-z0-9][a-z0-9 .&'/-]{2,48}?)(?=\s+(?:today|tomorrow|tonight|next\b|this\b|by\b|at\s+(?:noon|midnight|\d)|around\b|for\s+\d)|[.,;:]?$)"""
        )
        val APP_LAUNCH_PATTERN = Regex(
            """(?<!\w)(?:component:[^\s<>()\[\]]+|intent:[^\s<>()\[\]]+|package:[A-Z][A-Z0-9_]*(?:\.[A-Z][A-Z0-9_]*)+|[A-Z][A-Z0-9_]*(?:\.[A-Z][A-Z0-9_]*)+|[A-Z][A-Z0-9+.-]*://[^\s<>()\[\]]+)(?!\w)""",
            RegexOption.IGNORE_CASE
        )
        val DEEP_LINK_PATTERN = Regex("""(?<!\w)[a-z][a-z0-9+.-]*://[^\s<>()\[\]]+""", RegexOption.IGNORE_CASE)
        val WEB_LINK_SCHEMES = setOf("http", "https")
        val DOCUMENT_TEXT_HINTS = listOf("document", "doc", "file", "pdf", "sheet", "slides")
        val DOCUMENT_LINK_HINTS = listOf("docs.", "/doc", ".pdf", "/file", "/sheet", "/slides")
        val APP_TEXT_HINTS = listOf("open app", "launch app", "start app")
        val TASK_APP_LAUNCH_HINTS = listOf(
            TaskAppLaunchHint("spotify://", listOf("spotify")),
            TaskAppLaunchHint("youtube://", listOf("youtube", "you tube")),
            TaskAppLaunchHint("zoomus://", listOf("zoom")),
            TaskAppLaunchHint("msteams://", listOf("teams", "microsoft teams")),
            TaskAppLaunchHint("slack://", listOf("slack")),
            TaskAppLaunchHint("notion://", listOf("notion")),
            TaskAppLaunchHint("com.google.android.apps.maps", listOf("google maps")),
            TaskAppLaunchHint("com.google.android.gm", listOf("gmail")),
            TaskAppLaunchHint("com.google.android.calendar", listOf("google calendar")),
            TaskAppLaunchHint("com.google.android.apps.docs", listOf("google docs")),
            TaskAppLaunchHint("com.google.android.apps.docs.editors.sheets", listOf("google sheets")),
            TaskAppLaunchHint("com.google.android.apps.docs.editors.slides", listOf("google slides")),
            TaskAppLaunchHint("com.google.android.apps.drive", listOf("google drive"))
        )
        val HIGH_PRIORITY_TEXT_HINTS = listOf(
            "urgent",
            "due",
            "deadline",
            "critical",
            "asap",
            "important",
            "high priority",
            "due now",
            "right now",
            "now"
        )
        val QUICK_TASK_HINTS = listOf(
            "call",
            "text",
            "message",
            "ask",
            "tell",
            "send",
            "email",
            "reply",
            "pay",
            "pick up",
            "drop off",
            "buy",
            "grocery",
            "groceries",
            "water plants",
            "walk dog",
            "take out",
            "trash",
            "clean"
        )
        val DEEP_WORK_TASK_HINTS = listOf("review", "write", "draft", "prepare", "plan", "research", "study")
        val DATE_LIKE_PATTERN = Regex("""\d{4}-\d{1,2}-\d{1,2}""")
        val TASK_CAPTURE_PREFIXES = listOf(
            Regex("""(?i)^\s*(?:can you\s+)?(?:remind me to|set a reminder to|remember to|i need to|need to|i have to|have to|please|todo:?|task:?|add task:?|create task:?)\s+""")
        )
        val TASK_TITLE_NOISE_PATTERN = Regex(
            """(?i)\b(?:today|tomorrow|tonight|(?:next|this|on|by)\s+(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)|in\s+\d{1,3}\s+(?:days?|weeks?)|by\s+(?:today|tomorrow|tonight)|at\s+(?:noon|midnight|\d{1,2}(?::\d{2})?\s*(?:am|pm)?|morning|afternoon|evening|night|bedtime)|around\s+(?:noon|midnight|\d{1,2}(?::\d{2})?\s*(?:am|pm)?|morning|afternoon|evening|night|bedtime))\b"""
        )
        val EXPLICIT_TIME_PATTERN =
            Regex("""\b(?:noon|midnight|\d{1,2}(?::\d{2})?\s*(?:am|pm)|\d{1,2}:\d{2})\b""", RegexOption.IGNORE_CASE)
        val EXPLICIT_DURATION_PATTERN = Regex(
            """\b(?:\d+(?:\.\d+)?\s*(?:hours|hour|hrs|hr|h)(?:\s+\d+(?:\.\d+)?\s*(?:minutes|minute|mins|min|m))?|\d+(?:\.\d+)?\s*(?:minutes|minute|mins|min|m))\b""",
            RegexOption.IGNORE_CASE
        )
        val EXPLICIT_DATE_PATTERNS = listOf(
            Regex("""(?<!\d)\d{4}[-/]\d{1,2}[-/]\d{1,2}(?!\d)"""),
            Regex("""(?<!\d)\d{1,2}/\d{1,2}/\d{4}(?!\d)"""),
            Regex("""(?i)\b[A-Z][a-z]{2,8}\s+\d{1,2}(?:st|nd|rd|th)?,?\s+\d{4}\b"""),
            Regex("""(?i)\b(?:next|this|on|by)\s+(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\b"""),
            Regex("""(?i)\bin\s+\d{1,3}\s+(?:days?|weeks?)\b""")
        )
        val RELATIVE_DAY_OFFSET_PATTERN = Regex("""in\s+(\d{1,3})\s+(days?|weeks?)""")
        val NEXT_WEEKDAY_PATTERN =
            Regex("""(next|this|on|by)\s+(mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)""")
        val WEEKDAY_NAMES = mapOf(
            "mon" to DayOfWeek.MONDAY,
            "monday" to DayOfWeek.MONDAY,
            "tue" to DayOfWeek.TUESDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "wed" to DayOfWeek.WEDNESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thu" to DayOfWeek.THURSDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY,
            "friday" to DayOfWeek.FRIDAY,
            "sat" to DayOfWeek.SATURDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sun" to DayOfWeek.SUNDAY,
            "sunday" to DayOfWeek.SUNDAY
        )
        val ORDINAL_DAY_SUFFIX_PATTERN = Regex("""(?i)\b(\d{1,2})(st|nd|rd|th)\b""")
    }
}

private val APPOINTMENT_TEXT_HINTS = listOf(
    "appointment",
    "appt",
    "doctor",
    "dentist",
    "clinic",
    "checkup",
    "physical",
    "exam",
    "therapy",
    "therapist",
    "pediatrician",
    "optometrist",
    "lab",
    "blood test",
    "vaccination",
    "vaccine"
)
