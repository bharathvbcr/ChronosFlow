package com.ChronosFlow.VBCR.feature.daydial.ui

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.ai.genai.GenerationProfile
import com.ChronosFlow.VBCR.core.data.health.HealthConnectAvailability
import com.ChronosFlow.VBCR.core.data.health.HealthConnectWorkoutDataSource
import com.ChronosFlow.VBCR.core.domain.model.JournalAttachment
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import com.ChronosFlow.VBCR.core.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of the AI "reflect on my recent entries" card. */
sealed interface JournalInsightState {
    data object Idle : JournalInsightState
    data object Loading : JournalInsightState
    /** [fromAi] is false when the gateway was unavailable and a local heuristic was used instead. */
    data class Ready(val text: String, val fromAi: Boolean) : JournalInsightState
}

/** Result of an on-device AI text action (grammar / expand) on a single field. */
sealed interface JournalAiOutcome {
    /** The model produced [text] (may equal the input when it had no change to suggest). */
    data class Applied(val text: String) : JournalAiOutcome

    /** AI is disabled or the on-device model couldn't run; the field is left untouched. */
    data object Unavailable : JournalAiOutcome
}

/** State of the "import workouts from Health Connect" action. */
sealed interface WorkoutImportState {
    data object Idle : WorkoutImportState
    data object Importing : WorkoutImportState
    data class Done(val count: Int) : WorkoutImportState
    data class Unavailable(val reason: String) : WorkoutImportState
}

/**
 * Backs the standalone Journal page. Observes roughly a year of daily reflections (newest first) so
 * the page can show full history, the current streak, and a mood overview, and persists edits through
 * the same [DayDialJournalDelegate] the evening companion uses (so mood/streak semantics stay aligned).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val journalRepository: JournalRepository,
    private val assistCoordinator: GenAiAssistCoordinator,
    private val workoutDataSource: HealthConnectWorkoutDataSource
) : ViewModel() {

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _writeError = MutableStateFlow<String?>(null)
    val writeError: StateFlow<String?> = _writeError.asStateFlow()
    fun clearWriteError() { _writeError.value = null }

    private val _insight = MutableStateFlow<JournalInsightState>(JournalInsightState.Idle)
    val insight: StateFlow<JournalInsightState> = _insight.asStateFlow()

    private val _workoutImport = MutableStateFlow<WorkoutImportState>(WorkoutImportState.Idle)
    val workoutImport: StateFlow<WorkoutImportState> = _workoutImport.asStateFlow()

    /** Permissions the workout-import button requests, and the contract the screen launches. */
    val workoutRequestPermissions: Set<String> get() = workoutDataSource.requestPermissions
    fun workoutPermissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        workoutDataSource.permissionRequestContract()
    fun isHealthConnectAvailable(): Boolean = workoutDataSource.isAvailable()

    /**
     * Every reflection over the trailing year — including multiple entries for the same day — ordered
     * newest day first, then newest-written first within a day. The page renders all of them so a day
     * can hold several distinct entries.
     */
    val entries: StateFlow<List<JournalEntry>> = _today
        .flatMapLatest { date ->
            journalRepository.observeForDateRange(date.minusDays(HISTORY_WINDOW_DAYS), date)
        }
        .map { list ->
            list.sortedWith(
                compareByDescending<JournalEntry> { it.entryDate }
                    // Within a day: timed points in chronological order, untimed points last.
                    .thenBy { it.entryMinuteOfDay ?: Int.MAX_VALUE }
                    .thenByDescending { it.createdAt }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Persist a composed entry. When [entryId] is set, that single row is updated in place; otherwise a
     * new row is created for each date in [dates] (multiday). The first entry on a day stays the day's
     * primary; additional entries are saved as secondary so they coexist without demoting each other.
     */
    fun saveComposed(
        entryId: String?,
        dates: List<LocalDate>,
        body: String,
        promptType: String?,
        dayRating: Int?,
        minuteOfDay: Int? = null,
        photoUris: List<Uri> = emptyList()
    ) {
        val trimmed = body.trim()
        val rating = dayRating?.coerceIn(1, 5)
        val minute = minuteOfDay?.coerceIn(0, MAX_MINUTE_OF_DAY)
        if (trimmed.isBlank() && rating == null) return
        viewModelScope.launch {
            val now = Instant.now()
            if (entryId != null) {
                val existing = journalRepository.getById(entryId) ?: return@launch
                runCatching {
                    journalRepository.save(
                        existing.copy(
                            body = trimmed,
                            promptType = promptType,
                            dayRating = rating,
                            updatedAt = now,
                            entryMinuteOfDay = minute
                        )
                    )
                }.onFailure { e -> _writeError.value = e.message }
                // Photos buffered while editing are flushed once the row is persisted.
                if (photoUris.isNotEmpty()) addPhotos(entryId, photoUris)
            } else {
                val created = dates.distinct().map { date ->
                    newEntry(date, now, trimmed, promptType, rating, minute)
                }
                created.forEach { entry ->
                    runCatching { journalRepository.save(entry) }
                        .onFailure { e -> _writeError.value = e.message }
                }
                // Photos buffered before the first save attach to the (primary) entry just created.
                if (photoUris.isNotEmpty()) created.firstOrNull()?.let { addPhotos(it.id, photoUris) }
            }
        }
    }

    /**
     * Quickly add a timestamped "point" to a day — the lightweight, subtask-style capture path
     * (body required, optional [minuteOfDay], no mood). Each point is its own row; the first of a day
     * is the primary reflection.
     */
    fun addPoint(date: LocalDate, body: String, minuteOfDay: Int?) {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching {
                journalRepository.save(
                    newEntry(
                        date = date,
                        now = Instant.now(),
                        body = trimmed,
                        promptType = null,
                        rating = null,
                        minute = minuteOfDay?.coerceIn(0, MAX_MINUTE_OF_DAY)
                    )
                )
            }.onFailure { e -> _writeError.value = e.message }
        }
    }

    private fun newEntry(
        date: LocalDate,
        now: Instant,
        body: String,
        promptType: String?,
        rating: Int?,
        minute: Int?
    ): JournalEntry {
        val dayHasPrimary = entries.value.any { it.entryDate == date && it.isPrimary }
        return JournalEntry(
            id = UUID.randomUUID().toString(),
            entryDate = date,
            createdAt = now,
            updatedAt = now,
            body = body,
            promptType = promptType,
            isPrimary = !dayHasPrimary,
            dayRating = rating,
            entryMinuteOfDay = minute
        )
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { journalRepository.delete(id) }
                .onFailure { e -> _writeError.value = e.message }
        }
    }

    /** Attachments grouped by entry id, so the history list can render thumbnails inline. */
    val attachmentsByEntry: StateFlow<Map<String, List<JournalAttachment>>> =
        journalRepository.observeAllAttachments()
            .map { all -> all.groupBy { it.journalEntryId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Photos attached to [entryId], in display order. */
    fun attachments(entryId: String): Flow<List<JournalAttachment>> =
        journalRepository.observeAttachments(entryId)

    /**
     * Import photos picked from the Android photo picker. The picker only grants transient access, so
     * each image is copied into app-internal storage and the stored attachment points at that owned
     * copy — guaranteeing the photo stays viewable for the life of the entry. Runs off the main thread.
     */
    fun addPhotos(entryId: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dir = File(appContext.filesDir, JOURNAL_IMAGE_DIR).apply { mkdirs() }
                uris.forEach { uri ->
                    runCatching {
                        val mime = appContext.contentResolver.getType(uri)
                        val file = File(dir, "${UUID.randomUUID()}${extensionForMime(mime)}")
                        appContext.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        } ?: return@runCatching
                        journalRepository.addAttachment(entryId, Uri.fromFile(file).toString(), mime)
                    }
                }
            }
        }
    }

    fun removeAttachment(attachment: JournalAttachment) {
        viewModelScope.launch {
            // Drop the owned internal copy too, so deleting a photo reclaims its disk.
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = Uri.parse(attachment.uri).path?.let(::File)
                    if (file != null && file.parentFile?.name == JOURNAL_IMAGE_DIR && file.exists()) {
                        file.delete()
                    }
                }
            }
            journalRepository.removeAttachment(attachment.id)
        }
    }

    /**
     * Generate a short reflection over recent entries. Tries the GenAI assist gateway and falls back to
     * a deterministic local summary when AI is disabled/unavailable, so the card is always useful.
     */
    fun generateInsight() {
        val recent = entries.value
        val anchor = _today.value
        _insight.value = JournalInsightState.Loading
        viewModelScope.launch {
            val aiText = if (recent.isEmpty()) {
                null
            } else {
                runCatching {
                    assistCoordinator.generateAssistText(
                        prompt = buildJournalInsightPrompt(recent, anchor),
                        profile = GenerationProfile.CREATIVE
                    )
                }.getOrNull()
                    ?.takeIf { it.source != AssistGenAiSource.LOCAL }
                    ?.text
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            _insight.value = if (aiText != null) {
                JournalInsightState.Ready(aiText, fromAi = true)
            } else {
                JournalInsightState.Ready(localJournalInsight(recent, anchor), fromAi = false)
            }
        }
    }

    fun dismissInsight() {
        _insight.value = JournalInsightState.Idle
    }

    /**
     * Pull recent Health Connect exercise sessions and surface each as a timed journal point. The
     * entry id is derived from the Health Connect record id, so a re-import updates the same point
     * rather than duplicating it. Imported workouts are saved as secondary (non-primary) points so
     * they never displace a day's written reflection. Call after the permission grant resolves.
     */
    fun importWorkouts() {
        if (!workoutDataSource.isAvailable()) {
            _workoutImport.value = WorkoutImportState.Unavailable("Health Connect isn't set up on this device.")
            return
        }
        _workoutImport.value = WorkoutImportState.Importing
        viewModelScope.launch {
            if (!workoutDataSource.hasWorkoutReadPermission()) {
                _workoutImport.value = WorkoutImportState.Unavailable("Workout access wasn't granted.")
                return@launch
            }
            val end = Instant.now()
            val start = end.minus(Duration.ofDays(WORKOUT_LOOKBACK_DAYS))
            val workouts = runCatching { workoutDataSource.readWorkouts(start, end) }.getOrDefault(emptyList())
            val now = Instant.now()
            workouts.forEach { workout ->
                journalRepository.save(
                    JournalEntry(
                        id = "hc-workout-${workout.recordId}",
                        entryDate = workout.date,
                        createdAt = now,
                        updatedAt = now,
                        body = workout.label,
                        isPrimary = false,
                        entryMinuteOfDay = workout.startMinuteOfDay
                    )
                )
            }
            _workoutImport.value = WorkoutImportState.Done(workouts.size)
        }
    }

    fun dismissWorkoutImport() {
        _workoutImport.value = WorkoutImportState.Idle
    }

    /** Whether on-device AI text actions should be offered at all (off only when privacy = DISABLED). */
    fun aiTextActionsEnabled(): Boolean = assistCoordinator.privacyMode() != PrivacyMode.DISABLED

    /**
     * On-device grammar/spelling clean-up for a note or sub-note. Calls back [JournalAiOutcome.Applied]
     * with the polished text, or [JournalAiOutcome.Unavailable] when AI is disabled/unavailable.
     */
    fun refineGrammar(text: String, onResult: (JournalAiOutcome) -> Unit) {
        val input = text.trim()
        if (input.isBlank()) {
            onResult(JournalAiOutcome.Unavailable)
            return
        }
        viewModelScope.launch {
            val polished = runCatching { assistCoordinator.proofread(input) }.getOrNull()
                ?.text?.trim()?.takeIf { it.isNotBlank() }
            onResult(if (polished != null) JournalAiOutcome.Applied(polished) else JournalAiOutcome.Unavailable)
        }
    }

    /**
     * Expand/rewrite a terse note into a fuller reflection via the on-device assist gateway. Calls back
     * [JournalAiOutcome.Applied] with the rewrite, or [JournalAiOutcome.Unavailable] when AI can't run.
     */
    fun expandText(text: String, onResult: (JournalAiOutcome) -> Unit) {
        val input = text.trim()
        if (input.isBlank()) {
            onResult(JournalAiOutcome.Unavailable)
            return
        }
        viewModelScope.launch {
            val generated = runCatching {
                assistCoordinator.generateAssistText(
                    prompt = journalExpandPrompt(input),
                    profile = GenerationProfile.CREATIVE
                )
            }.getOrNull()
            val expanded = generated
                ?.takeIf { it.source != AssistGenAiSource.LOCAL }
                ?.text?.trim()?.takeIf { it.isNotBlank() }
            onResult(if (expanded != null) JournalAiOutcome.Applied(expanded) else JournalAiOutcome.Unavailable)
        }
    }

    /** Re-anchor "today" (e.g. after midnight or returning to the screen) so the streak stays current. */
    fun refreshToday() {
        _today.value = LocalDate.now()
    }

    private companion object {
        const val HISTORY_WINDOW_DAYS = 364L
        const val MAX_MINUTE_OF_DAY = 1439
        const val WORKOUT_LOOKBACK_DAYS = 30L
        const val JOURNAL_IMAGE_DIR = "journal_images"

        fun extensionForMime(mime: String?): String = when (mime) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "image/heic", "image/heif" -> ".heic"
            else -> ".jpg"
        }
    }
}
