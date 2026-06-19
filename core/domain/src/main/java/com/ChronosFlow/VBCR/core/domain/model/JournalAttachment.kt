package com.ChronosFlow.VBCR.core.domain.model

/** A photo attached to a [JournalEntry], referenced by a persisted content URI. */
data class JournalAttachment(
    val id: String,
    val journalEntryId: String,
    val uri: String,
    val mimeType: String? = null
)
