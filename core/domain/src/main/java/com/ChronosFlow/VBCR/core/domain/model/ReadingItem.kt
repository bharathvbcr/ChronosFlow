package com.ChronosFlow.VBCR.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * A saved "read later" item: a link the user wants to read, with optionally fetched metadata
 * (title / favicon / estimated read time) and an optional reminder ("remind me to read later").
 */
@Immutable
data class ReadingItem(
    val id: String,
    val url: String,
    val title: String,
    val domain: String,
    val faviconPath: String? = null,
    val estimatedReadMinutes: Int? = null,
    val wordCount: Int? = null,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val metadataState: ReadingMetadataState = ReadingMetadataState.PENDING,
    val notes: String? = null,
    val reminderAt: Instant? = null,
    val addedAt: Instant,
    val updatedAt: Instant,
    val lastOpenedAt: Instant? = null,
    val sortOrder: Int = 0
)

enum class ReadingStatus { UNREAD, READING, DONE, ARCHIVED }

/** Lifecycle of the background metadata fetch for a [ReadingItem]. */
enum class ReadingMetadataState { PENDING, FETCHED, FAILED, SKIPPED }
