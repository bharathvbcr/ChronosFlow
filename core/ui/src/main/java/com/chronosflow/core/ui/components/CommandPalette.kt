package com.chronosflow.core.ui.components

import com.chronosflow.core.ui.motion.chronosHapticClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import com.chronosflow.core.ui.theme.LocalFocusAwareColorState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.shell.ChronosModalBottomSheet
import com.chronosflow.core.ui.theme.liquidGlass

data class CommandPaletteItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: Set<String> = emptySet(),
    val group: String? = null,
    val shortcutLabel: String? = null,
    val priority: Int = 0,
    val onRun: () -> Unit
)

/** Canonical palette section labels — keep provider `group` values aligned with these. */
object CommandPaletteGroups {
    const val ASSISTANT = "Assistant"
    const val QUICK_CREATE = "Quick create"
    const val DAY = "Day"
    const val PLAN = "Plan"
    const val FOCUS = "Focus"
    const val SUPPORTING = "Supporting screens"
    const val SETTINGS = "Settings"
    const val OTHER = "Other"

    val displayOrder: List<String> = listOf(
        ASSISTANT,
        QUICK_CREATE,
        DAY,
        PLAN,
        FOCUS,
        SUPPORTING,
        SETTINGS,
        OTHER
    )
}

fun interface CommandProvider {
    fun commands(): List<CommandPaletteItem>
}

private val commandQueryTokenSplit = Regex("\\s+")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CommandPaletteDialog(
    commands: List<CommandPaletteItem>,
    onDismiss: () -> Unit,
    semanticCommands: (String) -> List<CommandPaletteItem> = { emptyList() },
    asyncSemanticCommands: ((String, (List<CommandPaletteItem>) -> Unit) -> Unit)? = null,
    query: String? = null,
    displayCommands: List<CommandPaletteItem>? = null,
    isExecuting: Boolean = false,
    onQueryChange: ((String) -> Unit)? = null,
    onRunCommand: ((CommandPaletteItem) -> Unit)? = null,
    onSubmitQuery: ((String) -> Unit)? = null,
    onAskAssistant: ((String) -> Unit)? = null,
    assistantIsAsking: Boolean = false,
    assistantStreamingReply: String? = null,
    assistantQuestion: String? = null,
    assistantHasConversation: Boolean = false,
    assistantReply: String? = null,
    assistantSourceLabel: String? = null,
    assistantProposalLabels: List<String> = emptyList(),
    onConfirmAssistantProposal: ((Int) -> Unit)? = null,
    onDismissAssistantReply: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var localQuery by remember { mutableStateOf("") }
    var asyncSemanticResults by remember { mutableStateOf<List<CommandPaletteItem>>(emptyList()) }
    val activeQuery = query ?: localQuery
    val queryFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        queryFocusRequester.requestFocus()
        keyboardController?.show()
    }
    LaunchedEffect(activeQuery, asyncSemanticCommands, displayCommands) {
        if (displayCommands != null || activeQuery.length < 3 || asyncSemanticCommands == null) {
            asyncSemanticResults = emptyList()
            return@LaunchedEffect
        }
        asyncSemanticCommands(activeQuery) { asyncSemanticResults = it }
    }
    val filteredCommands = remember(activeQuery, commands, semanticCommands, asyncSemanticResults, displayCommands) {
        val merged = displayCommands ?: mergeCommandPaletteResults(
            filterCommandPaletteItems(commands, activeQuery),
            semanticCommands(activeQuery),
            asyncSemanticResults
        )
        groupCommandsForDisplay(merged)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ChronosModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        chromeTag = "command-palette",
        containerColor = Color.Transparent,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .liquidGlass(cornerRadius = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val commandHeader = if (activeQuery.isBlank()) {
                    "Command palette"
                } else {
                    "Command palette for \"${activeQuery.take(48)}\""
                }
                Text(
                    text = commandHeader,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                OutlinedTextField(
                    value = activeQuery,
                    onValueChange = { nextQuery ->
                        if (onQueryChange != null) {
                            onQueryChange(nextQuery)
                        } else {
                            localQuery = nextQuery
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(queryFocusRequester),
                    singleLine = true,
                    keyboardOptions = if (onSubmitQuery != null) {
                        KeyboardOptions(imeAction = ImeAction.Go)
                    } else {
                        KeyboardOptions.Default
                    },
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val submitted = activeQuery.trim()
                            if (submitted.isNotBlank()) onSubmitQuery?.invoke(submitted)
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    placeholder = {
                        Text(
                            "Type a task, med, or habit",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingText = {
                        Text("Try \"call mom tomorrow\", \"vitamin D morning\", or \"gym three times a week\".")
                    }
                )
                ChronosSpeechInputButton(
                    prompt = "Say what you want to add, for example call mom tomorrow, vitamin D morning, or gym three times a week.",
                    label = "Speak a task, med, or habit",
                    onTranscript = { transcript ->
                        val spokenQuery = commandPaletteSpeechQuery(transcript)
                        if (spokenQuery.isBlank()) return@ChronosSpeechInputButton
                        if (onQueryChange != null) {
                            onQueryChange(spokenQuery)
                        } else {
                            localQuery = spokenQuery
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (onAskAssistant != null) {
                    ChronosTextButton(
                        onClick = { if (activeQuery.isNotBlank()) onAskAssistant(activeQuery) },
                        enabled = activeQuery.isNotBlank() && !assistantIsAsking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                assistantIsAsking && assistantStreamingReply != null -> "Replying…"
                                assistantIsAsking -> "Thinking…"
                                assistantHasConversation -> "Ask a follow-up"
                                else -> "Ask the assistant"
                            }
                        )
                    }
                }
                if (assistantIsAsking && assistantStreamingReply != null) {
                    AssistantReplyPanel(
                        reply = assistantStreamingReply,
                        question = assistantQuestion,
                        sourceLabel = "Gemini Nano — replying live",
                        proposalLabels = emptyList(),
                        onConfirm = null,
                        onDismiss = null
                    )
                } else if (assistantReply != null) {
                    AssistantReplyPanel(
                        reply = assistantReply,
                        question = assistantQuestion,
                        sourceLabel = assistantSourceLabel,
                        proposalLabels = assistantProposalLabels,
                        onConfirm = onConfirmAssistantProposal,
                        onDismiss = onDismissAssistantReply
                    )
                    if (onAskAssistant != null) {
                        ChronosSpeechInputButton(
                            prompt = "Ask a follow-up, for example make it tomorrow at 2pm.",
                            label = "Speak a follow-up",
                            onTranscript = { transcript ->
                                val followUp = commandPaletteSpeechQuery(transcript)
                                if (followUp.isNotBlank()) onAskAssistant(followUp)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                HorizontalDivider()
                when {
                    commands.isEmpty() -> {
                        val fallbackText = if (activeQuery.isBlank()) "No commands available." else "No commands match \"${activeQuery.take(48)}\"."
                        Text(
                            text = fallbackText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                    filteredCommands.isEmpty() -> {
                        val noMatchText = if (onAskAssistant != null) {
                            "No matching actions for \"${activeQuery.take(48)}\" — try Ask the assistant above."
                        } else {
                            "No matching actions for \"${activeQuery.take(48)}\"."
                        }
                        Text(
                            text = noMatchText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(
                                items = filteredCommands,
                                key = { entry ->
                                    when (entry) {
                                        is CommandPaletteDisplayEntry.GroupHeader -> "group-${entry.label}"
                                        is CommandPaletteDisplayEntry.Command -> entry.command.id
                                    }
                                }
                            ) { entry ->
                                when (entry) {
                                    is CommandPaletteDisplayEntry.GroupHeader -> {
                                        Text(
                                            text = entry.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                                        )
                                    }
                                    is CommandPaletteDisplayEntry.Command -> {
                                        CommandRow(
                                            command = entry.command,
                                            onRun = {
                                                if (!isExecuting) {
                                                    if (onRunCommand != null) {
                                                        onRunCommand(entry.command)
                                                    } else {
                                                        entry.command.onRun()
                                                        onDismiss()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ctrl+K",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChronosTextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics {
                            contentDescription = commandPaletteCloseActionLabel(activeQuery)
                        }
                    ) {
                        Text(commandPaletteCloseActionLabel(activeQuery))
                    }
                }
            }
        }
    }
}

internal sealed interface CommandPaletteDisplayEntry {
    data class GroupHeader(val label: String) : CommandPaletteDisplayEntry
    data class Command(val command: CommandPaletteItem) : CommandPaletteDisplayEntry
}

internal fun commandPaletteCloseActionLabel(query: String = ""): String {
    return if (query.isBlank()) {
        "Close command palette"
    } else {
        "Close command search"
    }
}

fun commandPaletteSpeechQuery(transcript: String): String {
    val normalized = transcript
        .trim()
        .replace(Regex("\\s+"), " ")
    val normalizedPriority = SPOKEN_ASAP_PATTERN.replace(normalized, "asap")
    val normalizedMedicationShortcuts = SPOKEN_PRN_PATTERN.replace(normalizedPriority, "prn")
    val normalizedClockPeriods = SPOKEN_AM_PM_PATTERN.replace(normalizedMedicationShortcuts) { match ->
        "${match.groupValues[1].lowercase()}m"
    }
    val normalizedClockTimeWords = SPOKEN_CLOCK_TIME_WORD_PATTERN.replace(normalizedClockPeriods) { match ->
        val hour = SPOKEN_QUANTITY_WORDS[match.groupValues[1].lowercase()] ?: match.groupValues[1]
        "$hour ${match.groupValues[2].lowercase()}"
    }
    val normalizedNamedClockTimes = SPOKEN_NAMED_CLOCK_TIME_PATTERN.replace(normalizedClockTimeWords) { match ->
        when (match.groupValues[1].lowercase()) {
            "noon" -> "12 pm"
            "midnight" -> "12 am"
            else -> match.value
        }
    }
    val normalizedPdf = SPOKEN_PDF_PATTERN.replace(normalizedNamedClockTimes, "pdf")
    val normalizedUps = SPOKEN_UPS_PATTERN.replace(normalizedPdf, "ups")
    val normalizedUsps = SPOKEN_USPS_PATTERN.replace(normalizedUps, "usps")
    val normalizedCbd = SPOKEN_CBD_PATTERN.replace(normalizedUsps, "cbd")
    val normalizedUrl = SPOKEN_URL_PATTERN.replace(normalizedCbd, "url")
    val normalizedWww = SPOKEN_WWW_PATTERN.replace(normalizedUrl, "www")
    val normalizedHttps = SPOKEN_HTTPS_PATTERN.replace(normalizedWww, "https")
    val normalizedHttp = SPOKEN_HTTP_PATTERN.replace(normalizedHttps, "http")
    val normalizedRx = SPOKEN_RX_PATTERN.replace(normalizedHttp, "rx")
    val normalizedSpokenAcronyms = SPOKEN_RSVP_PATTERN.replace(normalizedRx, "rsvp")
    val normalizedAsNeeded = SPOKEN_AS_NEEDED_PATTERN.replace(normalizedSpokenAcronyms, "as needed")
    val normalizedQuarterDoses = SPOKEN_QUARTER_DOSE_PATTERN.replace(normalizedAsNeeded) { match ->
        "1/4 ${match.groupValues[1]}"
    }
    val normalizedHalfDoses = SPOKEN_HALF_DOSE_PATTERN.replace(normalizedQuarterDoses) { match ->
        "1/2 ${match.groupValues[1]}"
    }
    val normalizedQuantities = SPOKEN_UNIT_QUANTITY_PATTERN.replace(normalizedHalfDoses) { match ->
        val quantity = SPOKEN_QUANTITY_WORDS[match.groupValues[1].lowercase()] ?: match.groupValues[1]
        "$quantity ${match.groupValues[2]}"
    }
    val normalizedXFrequencies = SPOKEN_X_FREQUENCY_PATTERN.replace(normalizedQuantities) { match ->
        val quantity = SPOKEN_QUANTITY_WORDS[match.groupValues[1].lowercase()] ?: match.groupValues[1]
        val period = when (match.groupValues[2].lowercase()) {
            "daily" -> "day"
            "weekly" -> "week"
            else -> match.groupValues[2].lowercase()
        }
        "$quantity x/$period"
    }
    val normalizedDailyFrequencies = SPOKEN_TIMES_DAILY_PATTERN.replace(normalizedXFrequencies) { match ->
        val quantity = SPOKEN_QUANTITY_WORDS[match.groupValues[1].lowercase()] ?: match.groupValues[1]
        if (quantity == "1") "once daily" else "$quantity times per day"
    }
    val normalizedWeeklyFrequencies = SPOKEN_TIMES_WEEKLY_PATTERN.replace(normalizedDailyFrequencies) { match ->
        val quantity = SPOKEN_QUANTITY_WORDS[match.groupValues[1].lowercase()] ?: match.groupValues[1]
        if (quantity == "1") "once per week" else "$quantity times per week"
    }
    val normalizedSimpleDailyFrequencies = SPOKEN_SIMPLE_DAILY_FREQUENCY_PATTERN.replace(normalizedWeeklyFrequencies) { match ->
        "${match.groupValues[1].lowercase()} daily"
    }
    val normalizedSimpleWeeklyFrequencies = SPOKEN_SIMPLE_WEEKLY_FREQUENCY_PATTERN.replace(normalizedSimpleDailyFrequencies) { match ->
        "${match.groupValues[1].lowercase()} per week"
    }
    val compactedSupplementWords = SPOKEN_SUPPLEMENT_WORD_PATTERN.replace(normalizedSimpleWeeklyFrequencies) { match ->
        val letter = when (match.groupValues[1].lowercase()) {
            "b", "be", "bee" -> "b"
            else -> "d"
        }
        val number = SPOKEN_QUANTITY_WORDS[match.groupValues[2].lowercase()] ?: match.groupValues[2]
        "$letter$number"
    }
    val compactedSupplement = SPOKEN_SUPPLEMENT_LETTER_PATTERN.replace(compactedSupplementWords) { match ->
        "${match.groupValues[1]}${match.groupValues[2]}"
    }
    val normalizedVitaminC = SPOKEN_VITAMIN_C_PATTERN.replace(compactedSupplement, "vitamin c")
    return SPOKEN_VITAMIN_D_PATTERN.replace(normalizedVitaminC, "vitamin d")
}

private val SPOKEN_QUANTITY_WORDS = mapOf(
    "a" to "1",
    "one" to "1",
    "two" to "2",
    "three" to "3",
    "four" to "4",
    "five" to "5",
    "six" to "6",
    "seven" to "7",
    "eight" to "8",
    "nine" to "9",
    "ten" to "10",
    "eleven" to "11",
    "twelve" to "12"
)
private val SPOKEN_UNIT_QUANTITY_PATTERN = Regex(
    """\b(a|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+(glasses?|cups?|ounces?|oz|liters?|litres?|milliliters?|millilitres?|ml|milligrams?|mg|micrograms?|mcg|grams?|g|tablets?|tabs?|capsules?|caps?|pills?|doses?|drops?|puffs?|sprays?|units?|teaspoons?|tsp|tablespoons?|tbsp)\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_X_FREQUENCY_PATTERN = Regex(
    """\b(one|two|three|four|five|six|\d+)\s+x\s+(?:a\s+|per\s+)?(day|daily|week|weekly)\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_TIMES_DAILY_PATTERN = Regex(
    """\b(one|two|three|four|five|six|\d+)\s+times?\s+(?:daily|(?:a|per)\s+day)\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_TIMES_WEEKLY_PATTERN = Regex(
    """\b(one|two|three|four|five|six|\d+)\s+times?\s+(?:weekly|(?:a|per)\s+week)\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_SIMPLE_DAILY_FREQUENCY_PATTERN = Regex(
    """\b(once|twice)\s+daily\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_SIMPLE_WEEKLY_FREQUENCY_PATTERN = Regex(
    """\b(once|twice)\s+weekly\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_HALF_DOSE_PATTERN = Regex(
    """\b(?:one\s+)?half\s+(tablets?|tabs?|capsules?|caps?|pills?|doses?)\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_QUARTER_DOSE_PATTERN = Regex(
    """\b(?:(?:one|a)\s+)?quarter\s+(tablets?|tabs?|capsules?|caps?|pills?|doses?)\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_ASAP_PATTERN = Regex("""\ba\s+s\s+a\s+p\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_PRN_PATTERN = Regex("""\bp\s+r\s+n\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_AM_PM_PATTERN = Regex("""\b([ap])\s+m\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_CLOCK_TIME_WORD_PATTERN = Regex(
    """\b(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+([ap]m)\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_NAMED_CLOCK_TIME_PATTERN = Regex("""\b(noon|midnight)\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_AS_NEEDED_PATTERN = Regex("""\b(?:as\s+need\s+it|when\s+needed)\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_PDF_PATTERN = Regex("""\bp\s+d\s+f\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_UPS_PATTERN = Regex("""\bu\s+p\s+s\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_USPS_PATTERN = Regex("""\bu\s+s\s+p\s+s\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_CBD_PATTERN = Regex("""\bc\s+b\s+d\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_URL_PATTERN = Regex("""\bu\s+r\s+l\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_WWW_PATTERN = Regex("""\bw\s+w\s+w\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_HTTP_PATTERN = Regex("""\bh\s+t\s+t\s+p\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_HTTPS_PATTERN = Regex("""\bh\s+t\s+t\s+p\s+s\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_RX_PATTERN = Regex("""\br\s+x\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_RSVP_PATTERN = Regex("""\br\s+s\s+v\s+p\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_SUPPLEMENT_WORD_PATTERN = Regex(
    """\b(b|be|bee|d|dee)\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|\d{1,2})\b""",
    RegexOption.IGNORE_CASE
)
private val SPOKEN_SUPPLEMENT_LETTER_PATTERN = Regex("""\b([bBdD])\s+(\d{1,2})\b""")
private val SPOKEN_VITAMIN_C_PATTERN = Regex("""\bvit(?:amin)?\s+(?:c|sea)\b""", RegexOption.IGNORE_CASE)
private val SPOKEN_VITAMIN_D_PATTERN = Regex("""\bvit(?:amin)?\s+d\b""", RegexOption.IGNORE_CASE)

fun mergeCommandPaletteResults(
    vararg sources: List<CommandPaletteItem>
): List<CommandPaletteItem> {
    val byId = linkedMapOf<String, CommandPaletteItem>()
    sources.flatMap { it }.forEach { command ->
        val existing = byId[command.id]
        if (existing == null || command.priority > existing.priority) {
            byId[command.id] = command
        }
    }
    return byId.values.toList()
}

internal fun groupCommandsForDisplay(commands: List<CommandPaletteItem>): List<CommandPaletteDisplayEntry> {
    if (commands.isEmpty()) return emptyList()
    val grouped = commands.groupBy { it.group ?: CommandPaletteGroups.OTHER }
    val entries = mutableListOf<CommandPaletteDisplayEntry>()
    CommandPaletteGroups.displayOrder.forEach { group ->
        val items = grouped[group] ?: return@forEach
        if (items.isEmpty()) return@forEach
        entries += CommandPaletteDisplayEntry.GroupHeader(group)
        items.sortedWith(compareByDescending<CommandPaletteItem> { it.priority }.thenBy { it.title })
            .forEach { command ->
                entries += CommandPaletteDisplayEntry.Command(command)
            }
    }
    grouped.keys
        .filter { it !in CommandPaletteGroups.displayOrder }
        .sorted()
        .forEach { group ->
            entries += CommandPaletteDisplayEntry.GroupHeader(group)
            grouped[group].orEmpty()
                .sortedWith(compareByDescending<CommandPaletteItem> { it.priority }.thenBy { it.title })
                .forEach { command ->
                    entries += CommandPaletteDisplayEntry.Command(command)
                }
        }
    return entries
}

@Composable
private fun AssistantReplyPanel(
    reply: String,
    question: String?,
    sourceLabel: String?,
    proposalLabels: List<String>,
    onConfirm: ((Int) -> Unit)?,
    onDismiss: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question?.takeIf { it.isNotBlank() }?.let { asked ->
                Text(
                    text = "You asked: ${asked.take(96)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = reply,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            sourceLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if ((proposalLabels.isNotEmpty() && onConfirm != null) || onDismiss != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (onConfirm != null) {
                        proposalLabels.forEachIndexed { index, label ->
                            ChronosTextButton(onClick = { onConfirm(index) }) {
                                Text("Run: ${label.take(40)}")
                            }
                        }
                    }
                    if (onDismiss != null) {
                        ChronosTextButton(onClick = onDismiss) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(
    command: CommandPaletteItem,
    onRun: () -> Unit
) {
    val highContrast = LocalFocusAwareColorState.current.isHighContrast
    ListItem(
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = command.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                command.shortcutLabel?.let { shortcut ->
                    Text(
                        text = shortcut,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = command.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .chronosHapticClick(onClick = onRun),
        colors = ListItemDefaults.colors(
            containerColor = if (highContrast) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
            }
        )
    )
}

fun filterCommandPaletteItems(commands: List<CommandPaletteItem>, query: String): List<CommandPaletteItem> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return commands
    val tokens = normalizedQuery.split(commandQueryTokenSplit).filter { it.isNotBlank() }
    return commands
        .mapNotNull { command ->
            val score = command.scoreForTokens(tokens)
            if (score > 0) command to score else null
        }
        .sortedWith(compareByDescending<Pair<CommandPaletteItem, Int>> { it.second }.thenBy { it.first.title })
        .map { it.first }
}

internal fun filterCommands(commands: List<CommandPaletteItem>, query: String): List<CommandPaletteItem> =
    filterCommandPaletteItems(commands, query)

private fun CommandPaletteItem.scoreForTokens(tokens: List<String>): Int = tokens.sumOf { token ->
    when {
        matchesToken(token, prefixOnly = true) -> 3
        matchesToken(token, prefixOnly = false) -> 1
        else -> 0
    }
}

private fun CommandPaletteItem.matchesToken(token: String, prefixOnly: Boolean): Boolean =
    title.matchesCommandToken(token, prefixOnly) ||
        subtitle.matchesCommandToken(token, prefixOnly) ||
        group?.matchesCommandToken(token, prefixOnly) == true ||
        shortcutLabel?.matchesCommandToken(token, prefixOnly) == true ||
        keywords.any { it.matchesCommandToken(token, prefixOnly) }

private fun String.matchesCommandToken(token: String, prefixOnly: Boolean): Boolean =
    if (prefixOnly) {
        startsWith(token, ignoreCase = true)
    } else {
        contains(token, ignoreCase = true)
    }
