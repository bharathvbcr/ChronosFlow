package com.ChronosFlow.VBCR.core.domain.model

/**
 * Where a [SleepTrack] row came from. Drives the conflict rule for the Health Connect importer:
 * automatic syncs may refresh [HEALTH_CONNECT] rows but never overwrite a [MANUAL] entry, so a
 * night the user typed (or edited) is always preserved.
 */
enum class SleepSource {
    MANUAL,
    HEALTH_CONNECT
}
