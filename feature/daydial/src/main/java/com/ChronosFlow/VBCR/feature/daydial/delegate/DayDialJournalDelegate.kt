package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import com.ChronosFlow.VBCR.core.domain.model.SleepSource
import com.ChronosFlow.VBCR.core.domain.model.SleepTrack
import com.ChronosFlow.VBCR.core.domain.repository.JournalRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepTrackRepository
import com.ChronosFlow.VBCR.core.domain.usecase.RecordSleepUseCase
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Evening-companion state: the selected day's journal entry and sleep log. */
@OptIn(ExperimentalCoroutinesApi::class)
class DayDialJournalDelegate @Inject constructor(
    private val journalRepository: JournalRepository,
    private val sleepTrackRepository: SleepTrackRepository,
    private val recordSleepUseCase: RecordSleepUseCase
) {
    fun journalEntry(
        scope: CoroutineScope,
        selectedDate: StateFlow<LocalDate>
    ): StateFlow<JournalEntry?> = selectedDate.flatMapLatest { date ->
        journalRepository.observeForDate(date).map { entries ->
            entries.firstOrNull { it.isPrimary } ?: entries.firstOrNull()
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Sleep is keyed by wake-day: the entry for [selectedDate] covers the night ending that morning. */
    fun sleepTrack(
        scope: CoroutineScope,
        selectedDate: StateFlow<LocalDate>
    ): StateFlow<SleepTrack?> = selectedDate.flatMapLatest { date ->
        sleepTrackRepository.observeForDate(date)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun saveSleepLog(
        date: LocalDate,
        quality: Int,
        actualStartMinute: Int?,
        actualEndMinute: Int?,
        interruptions: Int,
        windDownNotes: String?,
        existing: SleepTrack?
    ) {
        val base = existing?.takeIf { it.date == date } ?: SleepTrack(
            id = UUID.randomUUID().toString(),
            date = date,
            plannedStartMinute = null,
            plannedEndMinute = null,
            actualStartMinute = null,
            actualEndMinute = null,
            sleepQuality = 3,
            windDownNotes = null,
            interruptedCount = 0
        )
        recordSleepUseCase(
            base.copy(
                sleepQuality = quality.coerceIn(1, 5),
                actualStartMinute = actualStartMinute,
                actualEndMinute = actualEndMinute,
                interruptedCount = interruptions.coerceAtLeast(0),
                windDownNotes = windDownNotes?.trim()?.takeIf { it.isNotBlank() },
                // A hand-edit claims the night as user-owned, so the Health Connect sync won't overwrite it.
                source = SleepSource.MANUAL
            )
        )
    }
}
