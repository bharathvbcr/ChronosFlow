package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_contact_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("taskId", unique = true)
    ]
)
data class TaskContactSnapshotEntity(
    @PrimaryKey val taskId: String,
    val displayName: String,
    val lookupKey: String?
)

@Entity(
    tableName = "task_contact_methods",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("taskId")
    ]
)
data class TaskContactMethodEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val kind: String,
    val label: String?,
    val value: String,
    val normalizedValue: String?,
    val isPrimary: Boolean,
    val sortOrder: Int
)

@Entity(
    tableName = "task_actions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("taskId")
    ]
)
data class TaskActionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val type: String,
    val label: String,
    val value: String,
    val isPrimary: Boolean,
    val sortOrder: Int
)

@Entity(
    tableName = "task_attachments",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("taskId")
    ]
)
data class TaskAttachmentEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val kind: String,
    val storageMode: String,
    val reference: String,
    val persistedUriPermission: Boolean,
    val isFeaturedImage: Boolean,
    val sortOrder: Int
)
