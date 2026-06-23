package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** Persisted quick-capture inbox item. Enum-typed domain fields are stored as their `name` strings. */
@Entity(
    tableName = "inbox_items",
    indices = [Index("triaged"), Index("createdAt")]
)
data class InboxItemEntity(
    @PrimaryKey val id: String,
    val text: String,
    val url: String?,
    val source: String,
    val createdAt: Instant,
    val triaged: Boolean,
    val triagedTo: String?,
    val triagedRefId: String?,
    val sortOrder: Int
)
