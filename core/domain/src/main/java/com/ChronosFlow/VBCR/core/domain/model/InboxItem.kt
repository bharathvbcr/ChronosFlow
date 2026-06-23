package com.ChronosFlow.VBCR.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * A quick-capture inbox item: any thought or link dumped in for later triage. The user later
 * triages it into a Task, Block, Reading item, or Journal entry (or discards it).
 */
@Immutable
data class InboxItem(
    val id: String,
    val text: String,
    val url: String? = null,
    val source: CaptureSource = CaptureSource.MANUAL,
    val createdAt: Instant,
    val triaged: Boolean = false,
    val triagedTo: TriageOutcome? = null,
    val triagedRefId: String? = null,
    val sortOrder: Int = 0
)

/** Where a captured item came from. */
enum class CaptureSource { SHARE, MANUAL, SHORTCUT }

/** What an inbox item was triaged into. */
enum class TriageOutcome { TASK, BLOCK, READING, JOURNAL, DISCARDED }
