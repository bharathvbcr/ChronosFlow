package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.SleepSchedule

interface SleepScheduleRepository {
    fun getSleepSchedule(): SleepSchedule
}
