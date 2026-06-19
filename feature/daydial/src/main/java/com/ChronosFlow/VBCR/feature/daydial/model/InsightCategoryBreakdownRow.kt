package com.ChronosFlow.VBCR.feature.daydial.model

/** One category row in the Insights "Category breakdown" section. */
data class InsightCategoryBreakdownRow(
    val category: String,
    val minutes: Int,
    val share: Float,
    val progress: Float
)
