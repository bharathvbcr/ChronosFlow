package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ChronosFlow.VBCR.core.domain.model.JournalAttachment
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosCheckbox
import com.ChronosFlow.VBCR.core.ui.components.ChronosCollapsibleSection
import com.ChronosFlow.VBCR.core.ui.components.ChronosDropdownMenuItem
import com.ChronosFlow.VBCR.core.ui.components.ChronosIconButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosSectionTitle
import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.components.formatDisplayMinute
import com.ChronosFlow.VBCR.core.ui.components.parseFlexibleMinute
import com.ChronosFlow.VBCR.core.ui.motion.chronosHapticClick
import com.ChronosFlow.VBCR.core.ui.shell.ChronosModalBottomSheet
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val journalPageDateFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

/** Entry point used by the Journal sidebar page. Self-contained: pulls its own [JournalViewModel]. */
@Composable
internal fun JournalPageRoute(
    onMessage: (String) -> Unit = {},
    viewModel: JournalViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val insight by viewModel.insight.collectAsStateWithLifecycle()
    val workoutImport by viewModel.workoutImport.collectAsStateWithLifecycle()
    val attachmentsByEntry by viewModel.attachmentsByEntry.collectAsStateWithLifecycle()
    val healthConnectAvailable = remember { viewModel.isHealthConnectAvailable() }
    val aiEnabled = remember { viewModel.aiTextActionsEnabled() }
    // Requesting the grant returns immediately if already held; the callback then runs the import.
    val workoutPermissionLauncher = rememberLauncherForActivityResult(
        viewModel.workoutPermissionContract()
    ) { viewModel.importWorkouts() }
    JournalPageContent(
        entries = entries,
        today = today,
        insight = insight,
        workoutImport = workoutImport,
        healthConnectAvailable = healthConnectAvailable,
        attachmentsByEntry = attachmentsByEntry,
        attachmentsProvider = viewModel::attachments,
        onSave = viewModel::saveComposed,
        onAddPoint = viewModel::addPoint,
        onDelete = viewModel::delete,
        onGenerateInsight = viewModel::generateInsight,
        onDismissInsight = viewModel::dismissInsight,
        onImportWorkouts = { workoutPermissionLauncher.launch(viewModel.workoutRequestPermissions) },
        onDismissWorkoutImport = viewModel::dismissWorkoutImport,
        onRemoveAttachment = viewModel::removeAttachment,
        onRefineGrammar = if (aiEnabled) viewModel::refineGrammar else null,
        onExpandText = if (aiEnabled) viewModel::expandText else null,
        onMessage = onMessage
    )
}

/**
 * The unified journal editor surfaced as a sheet (e.g. from the "journal" deep-link / command). Uses
 * the SAME [JournalEntryComposer] as the Journal page — pulling its own [JournalViewModel] — so there
 * is one journal modal everywhere: mood, sub-notes, optional time, multiday, and photos all included.
 */
@Composable
internal fun JournalComposerSheet(
    date: LocalDate,
    onSaved: () -> Unit,
    onMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val attachmentsByEntry by viewModel.attachmentsByEntry.collectAsStateWithLifecycle()
    val aiEnabled = remember { viewModel.aiTextActionsEnabled() }
    // Edit the day's primary reflection when one exists; otherwise compose a fresh entry for the day.
    val editing = remember(entries, date) {
        entries.firstOrNull { it.entryDate == date && it.isPrimary }
            ?: entries.firstOrNull { it.entryDate == date }
    }
    val streak = remember(entries, date) { journalStreak(entries, date) }
    // Hosted inside SheetContent's own padded, already-scrolling ColumnScope. This composer must NOT
    // add its own verticalScroll — nesting two same-direction scrolls measures the inner one with an
    // infinite max height and crashes the sheet ("…measured with an infinity maximum height"), which
    // was why the add-FAB journal sheet failed to open. We only add imePadding so the keyboard never
    // covers the field; the host provides the scroll and horizontal padding.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        JournalEntryComposer(
            editing = editing,
            initialDate = date,
            streak = streak,
            attachments = editing?.let { attachmentsByEntry[it.id] }.orEmpty(),
            onRemoveAttachment = viewModel::removeAttachment,
            onRefineGrammar = if (aiEnabled) viewModel::refineGrammar else null,
            onExpandText = if (aiEnabled) viewModel::expandText else null,
            onMessage = onMessage,
            onSave = { entryId, dates, body, promptType, dayRating, minuteOfDay, photoUris ->
                viewModel.saveComposed(entryId, dates, body, promptType, dayRating, minuteOfDay, photoUris)
                onSaved()
            },
            onDelete = editing?.let { e -> { viewModel.delete(e.id); onSaved() } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalPageContent(
    entries: List<JournalEntry>,
    today: LocalDate,
    insight: JournalInsightState = JournalInsightState.Idle,
    workoutImport: WorkoutImportState = WorkoutImportState.Idle,
    healthConnectAvailable: Boolean = false,
    attachmentsByEntry: Map<String, List<JournalAttachment>> = emptyMap(),
    attachmentsProvider: (String) -> Flow<List<JournalAttachment>> = { flowOf(emptyList()) },
    onSave: (entryId: String?, dates: List<LocalDate>, body: String, promptType: String?, dayRating: Int?, minuteOfDay: Int?, photoUris: List<Uri>) -> Unit,
    onAddPoint: (date: LocalDate, body: String, minuteOfDay: Int?) -> Unit = { _, _, _ -> },
    onDelete: (String) -> Unit,
    onGenerateInsight: () -> Unit = {},
    onDismissInsight: () -> Unit = {},
    onImportWorkouts: () -> Unit = {},
    onDismissWorkoutImport: () -> Unit = {},
    onRemoveAttachment: (JournalAttachment) -> Unit = {},
    onRefineGrammar: JournalAiTransform? = null,
    onExpandText: JournalAiTransform? = null,
    onMessage: (String) -> Unit = {}
) {
    // Editor target: a specific entry to edit, or null + a date to compose a brand-new entry.
    var composing by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var composeDate by remember { mutableStateOf(today) }
    var query by rememberSaveable { mutableStateOf("") }
    // Tapped thumbnail to view full-size in a lightbox dialog.
    var previewUri by remember { mutableStateOf<String?>(null) }

    val openNew: (LocalDate) -> Unit = { date -> editingEntry = null; composeDate = date; composing = true }
    val openEdit: (JournalEntry) -> Unit = { entry -> editingEntry = entry; composing = true }

    val streak = remember(entries, today) { journalStreak(entries, today) }
    val filtered = remember(entries, query) {
        val q = query.trim()
        if (q.isBlank()) entries else entries.filter { it.body.contains(q, ignoreCase = true) }
    }
    // Entries are already date-desc, createdAt-desc; groupBy keeps that order, so days stay newest-first
    // and multiple entries on a day stay newest-written-first within their group.
    val grouped = remember(filtered) { filtered.groupBy { it.entryDate } }

    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
                        Text(
                            text = "Journal",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = journalStreakLabel(streak) ?: "Start a streak today",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (streak > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    ChronosButton(onClick = { openNew(today) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("New entry", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Calendar overview: each day's mood emoji at a glance, or workout days via the pills.
        JournalCalendarCard(
            entries = entries,
            today = today,
            onSelectDate = { date ->
                // Tap a day → edit its written reflection if there is one, else start a fresh entry.
                val existing = entries.firstOrNull { it.entryDate == date && it.isPrimary && !isJournalWorkoutEntry(it) }
                    ?: entries.firstOrNull { it.entryDate == date && !isJournalWorkoutEntry(it) }
                if (existing != null) openEdit(existing) else openNew(date)
            }
        )

        JournalInsightCard(
            insight = insight,
            onGenerate = onGenerateInsight,
            onDismiss = onDismissInsight
        )

        if (healthConnectAvailable || workoutImport !is WorkoutImportState.Idle) {
            WorkoutImportCard(
                state = workoutImport,
                onImport = onImportWorkouts,
                onDismiss = onDismissWorkoutImport
            )
        }

        // Always-available quick capture: add a timestamped point to today, subtask-style.
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
                Text(
                    text = "Add a point to today",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                JournalPointAdder(onAdd = { body, minute -> onAddPoint(today, body, minute) })
            }
        }

        if (entries.isEmpty()) {
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No reflections yet. Add a point above, or tap “New entry” for a fuller reflection with a mood.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search your reflections") }
            )

            ChronosSectionTitle(
                title = "History",
                subtitle = journalHistoryPageSummary(total = entries.size, shown = filtered.size, query = query)
            )
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                if (filtered.isEmpty()) {
                    Text(
                        text = "No reflections match “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                        grouped.forEach { (date, dayEntries) ->
                            // Per-day header; the "+" opens the full composer (mood / longer reflection).
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = journalDayGroupLabel(date, dayEntries.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                ChronosIconButton(onClick = { openNew(date) }) {
                                    Icon(
                                        Icons.Filled.CalendarMonth,
                                        contentDescription = "Add a full reflection on ${date.format(journalPageDateFormatter)}",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            dayEntries.forEachIndexed { index, entry ->
                                JournalHistoryRow(
                                    entry = entry,
                                    attachments = attachmentsByEntry[entry.id].orEmpty(),
                                    isLastInDay = index == dayEntries.lastIndex,
                                    onClick = { openEdit(entry) },
                                    onDelete = { onDelete(entry.id) },
                                    onPreviewImage = { previewUri = it }
                                )
                            }
                            // Inline subtask-style adder for another timed point on this day.
                            JournalPointAdder(
                                onAdd = { body, minute -> onAddPoint(date, body, minute) },
                                compact = true
                            )
                        }
                    }
                }
            }
        }
    }

    if (composing) {
        val editing = editingEntry
        // Photos attach to a saved entry, so only an edit target has a live attachment stream.
        val attachments by remember(editing?.id) {
            editing?.let { attachmentsProvider(it.id) } ?: flowOf(emptyList())
        }.collectAsState(initial = emptyList())
        ChronosModalBottomSheet(onDismissRequest = { composing = false }) {
            // A weighted, fill=false scroll column is bounded by the sheet height. A plain
            // verticalScroll directly inside the sheet's Column is measured with infinite height and
            // crashes ("Vertically scrollable component was measured with an infinity maximum height").
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                JournalEntryComposer(
                    editing = editing,
                    initialDate = composeDate,
                    streak = streak,
                    attachments = attachments,
                    onRemoveAttachment = onRemoveAttachment,
                    onRefineGrammar = onRefineGrammar,
                    onExpandText = onExpandText,
                    onMessage = onMessage,
                    onSave = { entryId, dates, body, promptType, dayRating, minuteOfDay, photoUris ->
                        onSave(entryId, dates, body, promptType, dayRating, minuteOfDay, photoUris)
                        composing = false
                    },
                    onDelete = editing?.let { entry -> { onDelete(entry.id); composing = false } }
                )
            }
        }
    }

    previewUri?.let { uri ->
        JournalImagePreviewDialog(uri = uri, onDismiss = { previewUri = null })
    }
}

/** Full-size photo viewer shown when a history thumbnail is tapped. */
@Composable
private fun JournalImagePreviewDialog(uri: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val bitmap = rememberContentThumbnail(uri, targetPx = 1280)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .chronosHapticClick(onClick = onDismiss, onClickLabel = "Close photo", role = Role.Button)
                .padding(ChronosSpacing.Small),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Journal photo",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }
        }
    }
}

/** Header label for a day group in the history list, e.g. "Mon, Jun 16 · 2 entries". */
internal fun journalDayGroupLabel(date: LocalDate, count: Int): String {
    val suffix = if (count == 1) "1 entry" else "$count entries"
    return "${date.format(journalPageDateFormatter)} · $suffix"
}

/** Callback shape for an on-device AI text transform: takes the field text and an outcome handler. */
internal typealias JournalAiTransform = (text: String, onResult: (JournalAiOutcome) -> Unit) -> Unit

/**
 * A "✨" affordance for a single text field: tap to open a menu offering on-device grammar fix and
 * expand/rewrite. Runs the chosen transform on [text] and applies the result via [onApply]. Shows a
 * spinner while the on-device model works.
 */
@Composable
private fun JournalAiAssistButton(
    text: String,
    onRefineGrammar: JournalAiTransform,
    onExpandText: JournalAiTransform,
    onApply: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Original text from before the last AI apply, so the edit is non-destructive (one-tap undo).
    var undoTo by remember { mutableStateOf<String?>(null) }

    // Defined here so both menu items share the apply/feedback handling.
    fun handle(outcome: JournalAiOutcome, original: String) {
        busy = false
        when (outcome) {
            is JournalAiOutcome.Applied -> {
                if (outcome.text != original) {
                    onApply(outcome.text)
                    undoTo = original
                    onMessage("Updated by AI — undo from the ✨ menu")
                } else {
                    onMessage("AI had no changes to suggest")
                }
            }
            JournalAiOutcome.Unavailable -> onMessage("On-device AI isn't available right now")
        }
    }

    Box {
        ChronosIconButton(onClick = { menuOpen = true }, enabled = text.isNotBlank() && !busy) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = "Polish with AI",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ChronosDropdownMenuItem(
                text = { Text("Fix grammar") },
                onClick = {
                    menuOpen = false
                    val original = text
                    busy = true
                    onRefineGrammar(original) { handle(it, original) }
                }
            )
            ChronosDropdownMenuItem(
                text = { Text("Expand / rewrite") },
                onClick = {
                    menuOpen = false
                    val original = text
                    busy = true
                    onExpandText(original) { handle(it, original) }
                }
            )
            undoTo?.let { original ->
                ChronosDropdownMenuItem(
                    text = { Text("Undo AI edit") },
                    onClick = {
                        menuOpen = false
                        onApply(original)
                        undoTo = null
                        onMessage("Reverted")
                    }
                )
            }
        }
    }
}

/** A small section heading inside the journal composer (e.g. "How was your day?", "Your reflection"). */
@Composable
private fun JournalComposerLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Subtask-style sub-notes editor inside the entry composer: an editable bullet list (each removable)
 * plus an "add" row. Mutates the shared [subNotes] snapshot list in place. When AI transforms are
 * provided, each sub-note also gets a "✨" polish button.
 */
@Composable
private fun JournalSubNoteEditor(
    subNotes: SnapshotStateList<String>,
    onRefineGrammar: JournalAiTransform? = null,
    onExpandText: JournalAiTransform? = null,
    onMessage: (String) -> Unit = {}
) {
    var draft by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
        Text(
            text = "Highlights",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        subNotes.forEachIndexed { index, note ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("•", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = note,
                    onValueChange = { subNotes[index] = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Sub-note") }
                )
                if (onRefineGrammar != null && onExpandText != null) {
                    JournalAiAssistButton(
                        text = note,
                        onRefineGrammar = onRefineGrammar,
                        onExpandText = onExpandText,
                        onApply = { subNotes[index] = it },
                        onMessage = onMessage
                    )
                }
                ChronosIconButton(onClick = { subNotes.removeAt(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove sub-note", modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Add a sub-note…") }
            )
            ChronosIconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        subNotes.add(draft.trim())
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank()
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add sub-note")
            }
        }
    }
}

/**
 * Subtask-style inline adder for a timed "point": a text field, an optional time field, and an add
 * button. Clears itself after each add so several points can be captured in a row.
 */
@Composable
private fun JournalPointAdder(
    onAdd: (body: String, minuteOfDay: Int?) -> Unit,
    compact: Boolean = false
) {
    var text by rememberSaveable { mutableStateOf("") }
    var timeText by rememberSaveable { mutableStateOf("") }
    val parsedMinute = parseFlexibleMinute(timeText)
    val timeError = timeText.isNotBlank() && parsedMinute == null
    val canAdd = text.isNotBlank() && !timeError

    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(if (compact) "Add another point…" else "What happened?") }
            )
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it },
                modifier = Modifier.width(104.dp),
                singleLine = true,
                isError = timeError,
                leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                placeholder = { Text("time") }
            )
            ChronosIconButton(
                onClick = {
                    if (canAdd) {
                        onAdd(text.trim(), parsedMinute)
                        text = ""
                        timeText = ""
                    }
                },
                enabled = canAdd
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add point")
            }
        }
        if (timeError) {
            Text(
                text = "Use a time like 2:30 PM or 14:30.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalEntryComposer(
    editing: JournalEntry?,
    initialDate: LocalDate,
    streak: Int,
    attachments: List<JournalAttachment> = emptyList(),
    onRemoveAttachment: (JournalAttachment) -> Unit = {},
    onRefineGrammar: JournalAiTransform? = null,
    onExpandText: JournalAiTransform? = null,
    onMessage: (String) -> Unit = {},
    onSave: (entryId: String?, dates: List<LocalDate>, body: String, promptType: String?, dayRating: Int?, minuteOfDay: Int?, photoUris: List<Uri>) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isNew = editing == null
    val parsedBody = remember(editing?.id) { journalParseBody(editing?.body.orEmpty()) }
    var mainNote by rememberSaveable(editing?.id) { mutableStateOf(parsedBody.mainNote) }
    // Subtask-style sub-notes. Not Saveable across process death, but fine for a transient editor.
    val subNotes = remember(editing?.id) { mutableStateListOf<String>().apply { addAll(parsedBody.subNotes) } }
    var timeText by rememberSaveable(editing?.id) {
        mutableStateOf(editing?.entryMinuteOfDay?.let(::formatDisplayMinute).orEmpty())
    }
    val parsedMinute = parseFlexibleMinute(timeText)
    val timeError = timeText.isNotBlank() && parsedMinute == null
    var dayRating by rememberSaveable(editing?.id) { mutableStateOf(editing?.dayRating) }
    // LocalDate isn't Saveable, so the picked days are held as epoch-day longs.
    var startEpochDay by rememberSaveable(editing?.id) {
        mutableStateOf((editing?.entryDate ?: initialDate).toEpochDay())
    }
    var endEpochDay by rememberSaveable(editing?.id) {
        mutableStateOf((editing?.entryDate ?: initialDate).toEpochDay())
    }
    var applyRange by rememberSaveable(editing?.id) { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    // Photos chosen this session (gallery or in-app camera). They're buffered here — so they work even
    // before a brand-new entry is saved — and flushed to the entry via onSave's photoUris.
    val pendingPhotos = remember(editing?.id) { mutableStateListOf<Uri>() }
    var showCamera by remember { mutableStateOf(false) }
    // Lets the writer cycle past the day's default reflection prompt to a fresh one if it doesn't land.
    var promptOffset by rememberSaveable(editing?.id) { mutableStateOf(0) }
    // Progressive disclosure: the rich extras (highlights, time, range, photos) live in one expandable
    // section so the default view stays clean (mood + reflection). Open it by default when editing an
    // entry that already carries such details, so they're visible without a tap.
    var detailsExpanded by rememberSaveable(editing?.id) {
        mutableStateOf(!isNew && (parsedBody.subNotes.isNotEmpty() || editing?.entryMinuteOfDay != null))
    }

    val startDate = LocalDate.ofEpochDay(startEpochDay)
    val endDate = LocalDate.ofEpochDay(endEpochDay)
    val dailyPrompt = remember(startEpochDay, promptOffset) {
        val base = journalPromptOfTheDay(startDate)
        if (promptOffset == 0) {
            base
        } else {
            val baseIndex = JournalDailyPrompts.indexOf(base).coerceAtLeast(0)
            JournalDailyPrompts[(baseIndex + promptOffset).mod(JournalDailyPrompts.size)]
        }
    }
    val dates = remember(isNew, applyRange, startEpochDay, endEpochDay) {
        if (isNew && applyRange && endDate >= startDate) {
            generateSequence(startDate) { d -> d.plusDays(1).takeIf { it <= endDate } }.toList()
        } else {
            listOf(startDate)
        }
    }
    val hasSubNotes = subNotes.any { it.isNotBlank() }
    val canSave = mainNote.isNotBlank() || hasSubNotes || dayRating != null
    val composedBody = journalSerializeBody(mainNote, subNotes)
    val photoCount = attachments.size + pendingPhotos.size
    // Live one-liner under the collapsed "Add details" header, so what's set is visible without expanding.
    val detailsSummary = run {
        val bits = buildList {
            val highlights = subNotes.count { it.isNotBlank() }
            if (highlights > 0) add(if (highlights == 1) "1 highlight" else "$highlights highlights")
            parsedMinute?.let { add(formatDisplayMinute(it)) }
            if (photoCount > 0) add(if (photoCount == 1) "1 photo" else "$photoCount photos")
            if (isNew && applyRange && dates.size > 1) add("${dates.size} days")
        }
        if (bits.isEmpty()) "Highlights, time of day, photos" else bits.joinToString(" · ")
    }

    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
        // Header: title + live streak badge.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isNew) "New entry" else "Edit entry",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            journalStreakLabel(streak)?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Date. A new entry can target any day (tap the pill); editing keeps the entry's day fixed.
        if (isNew) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .chronosHapticClick(
                        onClick = { showStartPicker = true },
                        onClickLabel = "Choose date",
                        role = Role.Button
                    )
                    .padding(ChronosSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (applyRange) "From ${startDate.format(journalPageDateFormatter)}"
                    else startDate.format(journalPageDateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Text(
                text = startDate.format(journalPageDateFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Mood — the quickest, most inviting thing to log, so it leads.
        JournalComposerLabel("How was your day?")
        JournalMoodPicker(selected = dayRating, onSelect = { dayRating = it })

        // Reflection. The day's rotating prompt rides along as the placeholder (a hint that vanishes as
        // soon as you type, never dumped into the text); the shuffle swaps it for another. The chosen
        // prompt is still recorded on the entry (promptType on save) so insights can reference it.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            JournalComposerLabel("Your reflection")
            ChronosIconButton(onClick = { promptOffset++ }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Show a different prompt",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        OutlinedTextField(
            value = mainNote,
            onValueChange = { mainNote = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            placeholder = { Text(dailyPrompt) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = journalWordCount(composedBody).let { if (it == 1) "1 word" else "$it words" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onRefineGrammar != null && onExpandText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Polish with AI",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    JournalAiAssistButton(
                        text = mainNote,
                        onRefineGrammar = onRefineGrammar,
                        onExpandText = onExpandText,
                        onApply = { mainNote = it },
                        onMessage = onMessage
                    )
                }
            }
        }

        // Progressive disclosure: highlights, time of day, range, and photos in one expandable section,
        // so the default view stays clean (mood + reflection) while the rich extras stay one tap away.
        ChronosCollapsibleSection(
            title = "Add details",
            summary = detailsSummary,
            expanded = detailsExpanded,
            onExpandedChange = { detailsExpanded = it }
        ) {
            // Highlights: subtask-style bullet items captured within this one entry.
            JournalSubNoteEditor(
                subNotes = subNotes,
                onRefineGrammar = onRefineGrammar,
                onExpandText = onExpandText,
                onMessage = onMessage
            )

            // Time of day this entry refers to (optional).
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = timeError,
                leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Time (optional)") },
                placeholder = { Text("e.g. 2:30 PM") }
            )
            if (timeError) {
                Text(
                    text = "Use a time like 2:30 PM or 14:30.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Apply to a whole range of days at once (new entries only).
            if (isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChronosCheckbox(checked = applyRange, onCheckedChange = { applyRange = it })
                    Text(
                        text = "Apply to a range of days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (applyRange) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .chronosHapticClick(
                                onClick = { showEndPicker = true },
                                onClickLabel = "Choose end date",
                                role = Role.Button
                            )
                            .padding(ChronosSpacing.Small),
                        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = "To ${endDate.format(journalPageDateFormatter)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = journalRangeSummary(dates.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Photos: already-saved attachments (when editing) plus any added this session. New photos
            // are buffered in pendingPhotos and persisted on save, so they work even on an unsaved entry.
            JournalAttachmentRow(
                saved = attachments,
                pending = pendingPhotos,
                onPick = { uris -> pendingPhotos.addAll(uris) },
                onRemoveSaved = onRemoveAttachment,
                onRemovePending = { uri -> pendingPhotos.remove(uri) },
                onOpenCamera = { showCamera = true }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            onDelete?.let { delete ->
                ChronosOutlinedButton(
                    onClick = delete,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Delete")
                }
            }
            ChronosButton(
                onClick = {
                    // Keep an edited entry's own prompt; tag a new one with the day's prompt for insights.
                    val promptType = editing?.promptType ?: dailyPrompt
                    onSave(editing?.id, dates, composedBody, promptType, dayRating, parsedMinute, pendingPhotos.toList())
                },
                enabled = canSave && !timeError,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when {
                        !isNew -> "Update entry"
                        dates.size > 1 -> "Save ${dates.size} entries"
                        else -> "Save entry"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showStartPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDate.toUtcStartOfDayMillis())
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                ChronosTextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startEpochDay = utcMillisToEpochDay(it) }
                    if (endEpochDay < startEpochDay) endEpochDay = startEpochDay
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                ChronosTextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }
    if (showEndPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = endDate.toUtcStartOfDayMillis())
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                ChronosTextButton(onClick = {
                    pickerState.selectedDateMillis?.let { endEpochDay = utcMillisToEpochDay(it) }
                    if (endEpochDay < startEpochDay) endEpochDay = startEpochDay
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                ChronosTextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showCamera) {
        JournalCameraDialog(
            onCaptured = { uri -> pendingPhotos.add(uri); showCamera = false },
            onDismiss = { showCamera = false }
        )
    }
}

private fun LocalDate.toUtcStartOfDayMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToEpochDay(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

/** Summary line for a multiday save, e.g. "Adds 5 entries, one per day." */
internal fun journalRangeSummary(dayCount: Int): String =
    if (dayCount <= 1) "Adds 1 entry." else "Adds $dayCount entries, one per day."

@Composable
private fun moodDotColor(rating: Int) = when {
    rating >= 4 -> MaterialTheme.colorScheme.primaryContainer
    rating == 3 -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun JournalHistoryRow(
    entry: JournalEntry,
    attachments: List<JournalAttachment> = emptyList(),
    isLastInDay: Boolean = true,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPreviewImage: (String) -> Unit = {}
) {
    val mood = journalMoodFor(entry.dayRating)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .chronosHapticClick(onClick = onClick, onClickLabel = "Edit reflection", role = Role.Button)
            .padding(vertical = ChronosSpacing.Micro),
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        // Timeline rail: a dot per point with a connector down to the next, so a day of several
        // points reads as a vertical timeline rather than a flat list.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (mood != null) moodDotColor(mood.rating) else MaterialTheme.colorScheme.primary
                    )
            )
            if (!isLastInDay) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(2.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
        ) {
            // Time (when set) + mood emoji on one line; rows sit under a day header, so no date here.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
            ) {
                entry.entryMinuteOfDay?.let { minute ->
                    Text(
                        text = formatDisplayMinute(minute),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                mood?.let { Text(text = it.emoji, style = MaterialTheme.typography.labelMedium) }
            }
            val body = entry.body.trim()
            if (body.isNotEmpty()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (mood != null) {
                Text(
                    text = "Felt ${mood.label.lowercase(Locale.getDefault())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (attachments.isNotEmpty()) {
                JournalThumbnailStrip(
                    attachments = attachments,
                    onPreviewImage = onPreviewImage
                )
            }
        }
        ChronosIconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete reflection",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Read-only inline strip of an entry's photos in the history list; tapping one opens the lightbox. */
@Composable
private fun JournalThumbnailStrip(
    attachments: List<JournalAttachment>,
    onPreviewImage: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
    ) {
        attachments.forEach { attachment ->
            val bitmap = rememberContentThumbnail(attachment.uri, targetPx = 200)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .chronosHapticClick(
                        onClick = { onPreviewImage(attachment.uri) },
                        onClickLabel = "View photo",
                        role = Role.Button
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Journal photo",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Filled.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** AI (with offline fallback) reflection over recent entries. */
@Composable
private fun JournalInsightCard(
    insight: JournalInsightState,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit
) {
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Reflect with AI",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            when (insight) {
                JournalInsightState.Idle -> {
                    Text(
                        text = "Get a short, gentle read on your recent mood and themes — runs on-device when available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChronosButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                        Text("Reflect on my recent entries", fontWeight = FontWeight.SemiBold)
                    }
                }
                JournalInsightState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current
                        )
                        Text("Reflecting…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is JournalInsightState.Ready -> {
                    Text(
                        text = insight.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (insight.fromAi) "Generated by on-device AI" else "Offline summary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                        ChronosOutlinedButton(onClick = onGenerate) { Text("Regenerate") }
                        ChronosTextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}

/**
 * Photo strip for an entry: [saved] attachments and [pending] (added-this-session, not-yet-persisted)
 * thumbnails, each removable, plus tiles to add from the photo picker or capture in-app with CameraX.
 * Picked URIs are only transiently readable; the ViewModel copies them into app storage on save.
 */
@Composable
private fun JournalAttachmentRow(
    saved: List<JournalAttachment>,
    pending: List<Uri>,
    onPick: (uris: List<Uri>) -> Unit,
    onRemoveSaved: (JournalAttachment) -> Unit,
    onRemovePending: (Uri) -> Unit,
    onOpenCamera: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> if (uris.isNotEmpty()) onPick(uris) }
    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
        Text(
            text = "Photos",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            saved.forEach { attachment ->
                JournalPhotoThumb(uri = attachment.uri, onRemove = { onRemoveSaved(attachment) })
            }
            pending.forEach { uri ->
                JournalPhotoThumb(uri = uri.toString(), onRemove = { onRemovePending(uri) })
            }
            JournalAddPhotoTile(
                icon = Icons.Filled.AddAPhoto,
                label = "Add photos",
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
            JournalAddPhotoTile(
                icon = Icons.Filled.CameraAlt,
                label = "Take photo",
                onClick = onOpenCamera
            )
        }
    }
}

/** One removable 84dp photo thumbnail in the attachment strip. */
@Composable
private fun JournalPhotoThumb(uri: String, onRemove: () -> Unit) {
    Box {
        val bitmap = rememberContentThumbnail(uri)
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Journal photo",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.Photo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ChronosIconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove photo",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** An 84dp "add a photo" action tile (gallery picker or in-app camera). */
@Composable
private fun JournalAddPhotoTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .chronosHapticClick(onClick = onClick, onClickLabel = label, role = Role.Button),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Import recent Health Connect workouts as timed journal points. */
@Composable
private fun WorkoutImportCard(
    state: WorkoutImportState,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
            ) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Workouts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            when (state) {
                WorkoutImportState.Idle -> {
                    Text(
                        text = "Import your recent Health Connect workouts as timed points.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChronosButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Text("Import workouts", fontWeight = FontWeight.SemiBold)
                    }
                }
                WorkoutImportState.Importing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current
                        )
                        Text("Importing…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is WorkoutImportState.Done -> {
                    Text(
                        text = if (state.count == 0) {
                            "No workouts found in the last 30 days."
                        } else {
                            "Imported ${state.count} workout${if (state.count == 1) "" else "s"} from Health Connect."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                        ChronosOutlinedButton(onClick = onImport) { Text("Import again") }
                        ChronosTextButton(onClick = onDismiss) { Text("Done") }
                    }
                }
                is WorkoutImportState.Unavailable -> {
                    Text(
                        text = state.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChronosOutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}

/** Subtitle for the page history section, reflecting an active [query] filter. */
internal fun journalHistoryPageSummary(total: Int, shown: Int, query: String): String {
    val label = if (total == 1) "reflection" else "reflections"
    return if (query.isBlank()) {
        "$total $label"
    } else {
        "$shown of $total $label match"
    }
}
