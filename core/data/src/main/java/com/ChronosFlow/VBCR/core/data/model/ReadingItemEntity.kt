package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** Persisted "read later" item. Enum-typed domain fields are stored as their `name` strings. */
@Entity(
    tableName = "reading_items",
    indices = [Index("status"), Index("addedAt")]
)
data class ReadingItemEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val domain: String,
    val faviconPath: String?,
    val estimatedReadMinutes: Int?,
    val wordCount: Int?,
    val status: String,
    val metadataState: String,
    val notes: String?,
    val reminderAt: Instant?,
    val addedAt: Instant,
    val updatedAt: Instant,
    val lastOpenedAt: Instant?,
    val sortOrder: Int
)
