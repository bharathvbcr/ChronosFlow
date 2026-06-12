package com.chronosflow.feature.daydial.model

import java.time.LocalDate

enum class ReviewDetailSection {
    PLANNED,
    ACTUAL,
    MISSED
}

sealed class SheetTarget {
    data class NewBlock(
        val startMinute: Int? = null,
        val title: String = "",
        val category: String = "WORK",
        val durationMinutes: Int? = null
    ) : SheetTarget()
    object QuickAdd : SheetTarget()
    object AiPlan : SheetTarget()
    object MissedBlocks : SheetTarget()
    object EndOfDayReview : SheetTarget()
    object FocusSettings : SheetTarget()
    object ExportData : SheetTarget()
    object ImportBackup : SheetTarget()
    object WeeklySummary : SheetTarget()
    object Diagnostics : SheetTarget()
    object Logs : SheetTarget()
    data class ReviewDetails(val section: ReviewDetailSection) : SheetTarget()
    data class BlockEditor(val blockId: String) : SheetTarget()
    data class Journal(val date: LocalDate) : SheetTarget()
    data class SleepLog(val date: LocalDate) : SheetTarget()
}
