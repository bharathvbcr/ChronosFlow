package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.SleepTrack
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.SleepTrackRepository
import javax.inject.Inject

/**
 * Persists a night's sleep log, snapshotting the planned bed/wake times from the day-dial sleep
 * schedule when the caller does not supply them.
 */
class RecordSleepUseCase @Inject constructor(
    private val sleepTrackRepository: SleepTrackRepository,
    private val sleepScheduleRepository: SleepScheduleRepository
) {
    suspend operator fun invoke(track: SleepTrack) {
        val resolved = if (track.plannedStartMinute == null && track.plannedEndMinute == null) {
            val schedule = sleepScheduleRepository.getSleepSchedule()
            if (schedule.enabled) {
                track.copy(
                    plannedStartMinute = schedule.startMinute,
                    plannedEndMinute = schedule.endMinute
                )
            } else {
                track
            }
        } else {
            track
        }
        sleepTrackRepository.upsert(resolved)
    }
}
