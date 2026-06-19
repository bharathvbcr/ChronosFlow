package com.ChronosFlow.VBCR.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.repository.SleepScheduleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepScheduleRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : SleepScheduleRepository {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        SleepSchedule.PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun getSleepSchedule(): SleepSchedule {
        return SleepSchedule(
            enabled = preferences.getBoolean(SleepSchedule.KEY_ENABLED, false),
            startMinute = preferences.getInt(SleepSchedule.KEY_START_MINUTE, SleepSchedule.DEFAULT_START_MINUTE),
            endMinute = preferences.getInt(SleepSchedule.KEY_END_MINUTE, SleepSchedule.DEFAULT_END_MINUTE)
        )
    }
}
