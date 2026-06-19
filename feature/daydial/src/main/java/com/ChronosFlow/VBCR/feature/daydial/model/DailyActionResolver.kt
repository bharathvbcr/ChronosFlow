package com.ChronosFlow.VBCR.feature.daydial.model

import com.ChronosFlow.VBCR.feature.daydial.TimeBlockUiModel

internal fun resolveDailyAction(
    timeBlocks: List<TimeBlockUiModel>,
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    missedBlocks: List<TimeBlockUiModel>
): DailyActionUiModel? {
    if (timeBlocks.isEmpty()) {
        return DailyActionUiModel(
            title = "Start your day",
            subtitle = "Nothing is on the dial yet.",
            primaryLabel = "Plan day",
            secondaryLabel = "Add block",
            kind = DailyActionKind.EMPTY_DAY
        )
    }

    activeBlock?.let { block ->
        return DailyActionUiModel(
            title = block.title,
            subtitle = "In progress now",
            primaryLabel = "Start focus",
            secondaryLabel = "Complete",
            kind = DailyActionKind.ACTIVE_BLOCK
        )
    }

    if (missedBlocks.isNotEmpty()) {
        val count = missedBlocks.size
        return DailyActionUiModel(
            title = if (count == 1) missedBlocks.first().title else "$count missed blocks",
            subtitle = "Catch up before the day drifts",
            primaryLabel = "Review missed",
            secondaryLabel = "Reflow day",
            kind = DailyActionKind.MISSED_BLOCKS
        )
    }

    if (nextBlock != null) {
        return DailyActionUiModel(
            title = nextBlock.title,
            subtitle = "Up next on your dial",
            primaryLabel = "Start next",
            secondaryLabel = "Prepare",
            kind = DailyActionKind.UPCOMING_BLOCK
        )
    }

    return DailyActionUiModel(
        title = "Open time",
        subtitle = "No upcoming blocks on the dial.",
        primaryLabel = "Add block",
        secondaryLabel = "Fill gaps",
        kind = DailyActionKind.OPEN_TIME
    )
}
