package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** A photo attached to a journal entry, stored as a persisted content-URI reference. */
@Entity(
    tableName = "journal_attachments",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("journalEntryId")]
)
data class JournalAttachmentEntity(
    @PrimaryKey val id: String,
    val journalEntryId: String,
    val uri: String,
    val mimeType: String?,
    val createdAt: Instant,
    val sortOrder: Int
)
