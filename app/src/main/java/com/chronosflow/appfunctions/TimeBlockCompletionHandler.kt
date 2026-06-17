package com.chronosflow.appfunctions

import com.chronosflow.core.data.focus.ManualMissedBlockRegistry
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.usecase.AdvanceRecurringTaskOccurrenceUseCase
import com.chronosflow.core.domain.usecase.CompleteTimeBlockUseCase
import com.chronosflow.core.domain.usecase.SyncRecurringTaskAlarmsUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully completes a planned time block: logs its planned window as actual time (idempotent), and on
 * a fresh completion advances any linked recurring task and re-syncs its alarms, then clears a stale
 * missed flag. Shared by every non-UI completion entry point — the agent `completeTimeBlock`
 * AppFunction and the watch's complete-block action — so a wrist tap and an agent call take the same
 * path the in-app action ([com.chronosflow.feature.daydial.delegate.DayDialReviewDelegate]) does.
 */
@Singleton
class TimeBlockCompletionHandler @Inject constructor(
    private val completeTimeBlockUseCase: CompleteTimeBlockUseCase,
    private val advanceRecurringTaskOccurrenceUseCase: AdvanceRecurringTaskOccurrenceUseCase,
    private val syncRecurringTaskAlarmsUseCase: SyncRecurringTaskAlarmsUseCase,
    private val manualMissedBlockRegistry: ManualMissedBlockRegistry
) {
    suspend fun complete(block: TimeBlock) {
        if (completeTimeBlockUseCase(block)) {
            advanceRecurringTaskOccurrenceUseCase(block)?.let { (task, schedule) ->
                syncRecurringTaskAlarmsUseCase(task, schedule)
            }
        }
        manualMissedBlockRegistry.clearMissed(block.id, block.date)
    }
}
