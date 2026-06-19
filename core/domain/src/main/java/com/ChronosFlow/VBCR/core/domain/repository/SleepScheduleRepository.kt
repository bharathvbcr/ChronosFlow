package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule

interface SleepScheduleRepository {
    fun getSleepSchedule(): SleepSchedule
}
