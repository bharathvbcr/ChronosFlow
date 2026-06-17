package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reactive journal history across the trailing [windowDays], newest day first with the day's primary
 * reflection ahead of any secondary captures for the same date. Emits again whenever an entry is
 * saved, so the Insights journal timeline stays live without a manual refresh.
 */
class ObserveRecentJournalEntriesUseCase @Inject constructor(
    private val journalRepository: JournalRepository
) {
    operator fun invoke(
        windowDays: Int = 14,
        today: LocalDate = LocalDate.now()
    ): Flow<List<JournalEntry>> {
        if (windowDays <= 0) return flowOf(emptyList())
        val start = today.minusDays((windowDays - 1).toLong())
        return journalRepository.observeForDateRange(start, today)
            .map { entries ->
                entries.sortedWith(
                    compareByDescending<JournalEntry> { it.entryDate }
                        .thenByDescending { it.isPrimary }
                        .thenByDescending { it.createdAt }
                )
            }
    }
}
