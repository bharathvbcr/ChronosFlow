package com.ChronosFlow.VBCR.core.domain.model

/**
 * Coarse next-day readiness derived from the most recently logged night, so the day's plan can adapt
 * to how the user actually slept. [UNKNOWN] is the neutral default: when no night is logged the
 * planners behave exactly as before, so nothing changes for users who do not track sleep.
 */
enum class SleepReadiness {
    UNKNOWN,
    DEPLETED,
    NORMAL,
    RESTED
}

/**
 * Derives [SleepReadiness] from [lastNight], the night that ended this morning. A user-entered
 * quality rating (1..5) leads; when it is absent, measured duration stands in. A heavily interrupted
 * night drags either signal down to [SleepReadiness.DEPLETED]. Returns [SleepReadiness.UNKNOWN] when
 * the night is missing or carries neither a rating nor a measurable window, so callers can no-op.
 */
fun deriveSleepReadiness(lastNight: SleepTrack?): SleepReadiness {
    if (lastNight == null) return SleepReadiness.UNKNOWN
    val heavilyInterrupted = lastNight.interruptedCount >= INTERRUPTION_DEPLETED_THRESHOLD
    val quality = lastNight.sleepQuality.takeIf { it > 0 }
    if (quality != null) {
        return when {
            quality <= DEPLETED_QUALITY_MAX || heavilyInterrupted -> SleepReadiness.DEPLETED
            quality >= RESTED_QUALITY_MIN -> SleepReadiness.RESTED
            else -> SleepReadiness.NORMAL
        }
    }
    val durationMinutes = sleepDurationMinutes(lastNight) ?: return SleepReadiness.UNKNOWN
    return when {
        durationMinutes < DEPLETED_DURATION_MINUTES || heavilyInterrupted -> SleepReadiness.DEPLETED
        durationMinutes >= RESTED_DURATION_MINUTES -> SleepReadiness.RESTED
        else -> SleepReadiness.NORMAL
    }
}

// Quality is rated 1..5 (0 means unrated): 1-2 is a poor night, 4-5 a good one, 3 is neutral.
private const val DEPLETED_QUALITY_MAX = 2
private const val RESTED_QUALITY_MIN = 4
private const val DEPLETED_DURATION_MINUTES = 6 * 60
private const val RESTED_DURATION_MINUTES = 7 * 60 + 30
private const val INTERRUPTION_DEPLETED_THRESHOLD = 3
