package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyCheckIn

internal fun latestFocusMoodAccent(
    checkIns: List<MoodEnergyCheckIn>,
    blockId: String?,
    cachedMoodScore: Int? = null,
    cachedEnergyScore: Int? = null
): Pair<Int?, Int?> {
    val blockSpecific = blockId?.let { id ->
        checkIns.filter { it.blockId == id }.maxByOrNull { it.recordedAt }
    }
    val latest = blockSpecific
        ?: checkIns.filter { it.blockId == null }.maxByOrNull { it.recordedAt }
        ?: checkIns.maxByOrNull { it.recordedAt }
    if (latest != null) {
        return latest.moodScore to latest.energyScore
    }
    return cachedMoodScore to cachedEnergyScore
}
