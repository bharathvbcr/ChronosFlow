package com.ChronosFlow.VBCR.feature.daydial.model

enum class DailyActionKind {
    EMPTY_DAY,
    ACTIVE_BLOCK,
    UPCOMING_BLOCK,
    MISSED_BLOCKS,
    OPEN_TIME
}

data class DailyActionUiModel(
    val title: String,
    val subtitle: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val kind: DailyActionKind
)
