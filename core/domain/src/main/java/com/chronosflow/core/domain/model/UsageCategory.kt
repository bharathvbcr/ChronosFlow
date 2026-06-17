package com.chronosflow.core.domain.model

/**
 * How a foreground app counts toward the day's "focused vs. distracted" screen-time split.
 * Apps are classified by their declared Android app category (productivity/maps/news → productive,
 * games/social/video → distracting, everything else neutral) and may later be reclassified by the user.
 */
enum class UsageCategory {
    PRODUCTIVE,
    DISTRACTING,
    NEUTRAL
}
