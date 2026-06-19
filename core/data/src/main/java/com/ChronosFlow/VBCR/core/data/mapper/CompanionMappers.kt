package com.ChronosFlow.VBCR.core.data.mapper

import com.ChronosFlow.VBCR.core.data.model.GoalEntity
import com.ChronosFlow.VBCR.core.data.model.JournalEntryEntity
import com.ChronosFlow.VBCR.core.data.model.SleepTrackEntity
import com.ChronosFlow.VBCR.core.domain.model.Goal
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import com.ChronosFlow.VBCR.core.domain.model.SleepSource
import com.ChronosFlow.VBCR.core.domain.model.SleepTrack

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
    isPrimary = isPrimary,
    dayRating = dayRating,
    entryMinuteOfDay = entryMinuteOfDay
)

fun JournalEntry.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id,
    entryDate = entryDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    body = body,
    promptType = promptType,
    moodCheckInId = moodCheckInId,
    isPrimary = isPrimary,
    dayRating = dayRating,
    entryMinuteOfDay = entryMinuteOfDay
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
