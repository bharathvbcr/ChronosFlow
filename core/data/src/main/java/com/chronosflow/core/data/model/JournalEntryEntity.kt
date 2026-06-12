package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "journal_entries",
    indices = [Index("entryDate"), Index("moodCheckInId")]
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val entryDate: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val body: String,
    val promptType: String?,
    val moodCheckInId: String?,
    val isPrimary: Boolean
)
