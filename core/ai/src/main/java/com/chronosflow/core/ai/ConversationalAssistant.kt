package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.GenerationProfile
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class AssistantRole {
    USER,
    ASSISTANT
}

data class AssistantMessage(
    val role: AssistantRole,
    val text: String
)

/**
 * An action the assistant proposes in response to a request. It maps onto an existing ChronosFlow
 * command (the same catalog the command palette and AppFunctions use). [requiresConfirmation] is
 * always true — the assistant never executes anything; the UI must get an explicit tap first.
 * [capturePayload] is only set for quick-capture create commands: the full item text to capture,
 * composed from the whole conversation (so follow-up details like "make it tomorrow" are included).
 */
data class AssistantActionProposal(
    val commandId: String,
    val title: String,
    val requiresConfirmation: Boolean = true,
    val capturePayload: String? = null
)

data class AssistantResponse(
    val reply: String,
    /** Up to [ConversationalAssistant.MAX_PROPOSALS] confirmable actions, most useful first. */
    val proposals: List<AssistantActionProposal>,
    val source: AssistGenAiSource
) {
    /** The primary (first) proposal — kept for callers that only surface one action. */
    val proposal: AssistantActionProposal? get() = proposals.firstOrNull()
}

sealed interface AssistantStreamEvent {
    /** Cumulative reply text so far, with any control lines stripped — safe to render live. */
    data class Partial(val text: String) : AssistantStreamEvent

    /** Terminal event carrying the parsed reply plus any (unconfirmed) action proposal. */
    data class Final(val response: AssistantResponse) : AssistantStreamEvent
}

/**
 * Free-text, multi-turn assistant. It converses with Gemini Nano (on-device, streaming) and, when
 * the user asks to *do* something, maps the request onto one of the [CommandAssistCandidate]s the
 * caller supplies — surfacing it as an [AssistantActionProposal] the user must confirm. Falls back
 * to deterministic local command-routing whenever on-device AI is disabled or unavailable.
 */
class ConversationalAssistant @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator,
    private val commandAssistPlanner: CommandAssistPlanner
) {
    suspend fun respond(
        history: List<AssistantMessage>,
        userMessage: String,
        commands: List<CommandAssistCandidate>
    ): AssistantResponse {
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(history, userMessage, commands),
            profile = GenerationProfile.CREATIVE
        )
        return generation.text
            ?.let { parseResponse(it, commands, generation.source) }
            ?: localResponse(userMessage, commands)
    }

    fun respondStream(
        history: List<AssistantMessage>,
        userMessage: String,
        commands: List<CommandAssistCandidate>
    ): Flow<AssistantStreamEvent> = flow {
        val prompt = buildPrompt(history, userMessage, commands)
        var lastCumulative = ""
        genAiAssistCoordinator.generateAssistTextStream(prompt, profile = GenerationProfile.CREATIVE).collect { cumulative ->
            lastCumulative = cumulative
            emit(AssistantStreamEvent.Partial(displayText(cumulative)))
        }
        // An empty stream means Nano never produced text (disabled, unsupported, or still
        // downloading) — retry through the full non-streaming path so cloud-allowed users
        // still get a generated reply before the deterministic local routing kicks in.
        val response = if (lastCumulative.isBlank()) {
            respond(history, userMessage, commands)
        } else {
            parseResponse(lastCumulative, commands, AssistGenAiSource.GEMINI_NANO)
        }
        emit(AssistantStreamEvent.Final(response))
    }

    private fun buildPrompt(
        history: List<AssistantMessage>,
        userMessage: String,
        commands: List<CommandAssistCandidate>
    ): String = buildString {
        appendLine("You are ChronosFlow's planning assistant. Reply in one or two short, warm, specific sentences.")
        appendLine("Use the conversation so far to resolve follow-ups like \"yes\", \"the second one\", or \"make it tomorrow\".")
        appendLine("If the user clearly wants to perform actions that match commands below, end your reply")
        appendLine("with one line per action, each exactly: ACTION: <command_id> (use only ids from the list,")
        appendLine("at most three, most useful first).")
        appendLine("For capture.create commands, append a pipe and the full item to create, combining every")
        appendLine("detail from the conversation, e.g. ACTION: capture.create.task | call mom tomorrow at 2pm")
        appendLine("Never claim you performed the action — the user confirms it. Do not invent command ids.")
        appendLine("If nothing matches, briefly say what you can help with (planning the day, tasks, habits, meds, focus) instead of apologizing.")
        appendLine("Commands:")
        commands.take(MAX_COMMANDS_IN_PROMPT).forEach { candidate ->
            appendLine("- ${candidate.id} | ${candidate.title} | ${candidate.keywords.joinToString(", ")}")
        }
        if (history.isNotEmpty()) {
            appendLine("Conversation so far:")
            history.takeLast(MAX_HISTORY).forEach { message ->
                val speaker = if (message.role == AssistantRole.USER) "User" else "Assistant"
                appendLine("$speaker: ${message.text}")
            }
        }
        appendLine("User: $userMessage")
        appendLine("Assistant:")
    }

    private fun parseResponse(
        raw: String,
        commands: List<CommandAssistCandidate>,
        source: AssistGenAiSource
    ): AssistantResponse {
        val allowed = commands.associateBy { it.id }
        val proposals = ACTION_LINE_PATTERN.findAll(raw)
            .mapNotNull { match ->
                val id = match.groupValues.getOrNull(1)?.trim()
                val payload = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                allowed[id]?.let { candidate ->
                    AssistantActionProposal(
                        commandId = candidate.id,
                        title = candidate.title,
                        capturePayload = payload.takeIf { candidate.id.startsWith(CAPTURE_COMMAND_ID_PREFIX) }
                    )
                }
            }
            .distinctBy { it.commandId }
            .take(MAX_PROPOSALS)
            .toList()
        val reply = displayText(raw).ifBlank {
            proposals.firstOrNull()?.let { "I can open “${it.title}” for you — confirm to continue." }
                ?: "Done — anything else?"
        }
        return AssistantResponse(reply = reply, proposals = proposals, source = source)
    }

    private fun displayText(raw: String): String = raw
        .lineSequence()
        .filterNot { ACTION_LINE_PATTERN.containsMatchIn(it) }
        .joinToString("\n")
        .trim()

    private fun localResponse(
        userMessage: String,
        commands: List<CommandAssistCandidate>
    ): AssistantResponse {
        val match = commandAssistPlanner.localRankCommandIds(userMessage, commands, limit = 1)
            .firstOrNull()
            ?.let { id -> commands.firstOrNull { it.id == id } }
        return if (match != null) {
            AssistantResponse(
                reply = "Closest match: “${match.title}”. Tap Run to confirm — nothing happens until you do.",
                proposals = listOf(AssistantActionProposal(match.id, match.title)),
                source = AssistGenAiSource.LOCAL
            )
        } else {
            AssistantResponse(
                reply = "I couldn't match that to an action yet. Try something like “plan my morning”, “add task call mom tomorrow”, or “start a focus session”.",
                proposals = emptyList(),
                source = AssistGenAiSource.LOCAL
            )
        }
    }

    companion object {
        const val MAX_PROPOSALS = 3
        private const val MAX_COMMANDS_IN_PROMPT = 40
        private const val MAX_HISTORY = 6
        private const val CAPTURE_COMMAND_ID_PREFIX = "capture.create."
        private val ACTION_LINE_PATTERN = Regex("""(?im)^\s*ACTION:\s*([A-Za-z0-9._:-]+)\s*(?:\|\s*(.+?))?\s*$""")
    }
}
