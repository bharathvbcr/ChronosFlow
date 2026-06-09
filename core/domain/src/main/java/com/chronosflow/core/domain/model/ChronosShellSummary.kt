package com.chronosflow.core.domain.model

data class ChronosShellSummary(
    val missedBlocksCount: Int = 0,
    val focusActive: Boolean = false,
    val unreadInsightsCount: Int = 0
)
