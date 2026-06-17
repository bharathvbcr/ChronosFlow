package com.chronosflow.core.data.mapper

import com.chronosflow.core.data.model.GoalEntity
import com.chronosflow.core.data.model.JournalEntryEntity
import com.chronosflow.core.data.model.SleepTrackEntity
import com.chronosflow.core.domain.model.Goal
import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.domain.model.SleepSource
import com.chronosflow.core.domain.model.SleepTrack

fun GoalEntity.toDomain(): Goal = Goal(
    id = id,
    title = title,
    description = description,
    category = category,
    targetValue = targetValue,
    startDate = startDate,
    targetDate = targetDate,
    progressValue = progressValue,
    isCompleted = isCompleted
)

fun Goal.toEntity(): GoalEntity = GoalEntity(
    id = id,
    title = title,
    description = description,
    category = category,
    targetValue = targetValue,
    startDate = startDate,
    targetDate = targetDate,
    progressValue = progressValue,
    isCompleted = isCompleted
)

fun JournalEntryEntity.toDomain(): JournalEntry = JournalEntry(
    id = id,
    entryDate = entryDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    body = body,
    promptType = promptType,
    moodCheckInId = moodCheckInId,
    isPrimary = isPrimary
)

fun JournalEntry.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id,
    entryDate = entryDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    body = body,
    promptType = promptType,
    moodCheckInId = moodCheckInId,
    isPrimary = isPrimary
)

fun SleepTrackEntity.toDomain(): SleepTrack = SleepTrack(
    id = id,
    date = date,
    plannedStartMinute = plannedStartMinute,
    plannedEndMinute = plannedEndMinute,
    actualStartMinute = actualStartMinute,
    actualEndMinute = actualEndMinute,
    sleepQuality = sleepQuality,
    windDownNotes = windDownNotes,
    interruptedCount = interruptedCount,
    source = runCatching { SleepSource.valueOf(source) }.getOrDefault(SleepSource.MANUAL)
)

fun SleepTrack.toEntity(): SleepTrackEntity = SleepTrackEntity(
    id = id,
    date = date,
    plannedStartMinute = plannedStartMinute,
    plannedEndMinute = plannedEndMinute,
    actualStartMinute = actualStartMinute,
    actualEndMinute = actualEndMinute,
    sleepQuality = sleepQuality,
    windDownNotes = windDownNotes,
    interruptedCount = interruptedCount,
    source = source.name
)
