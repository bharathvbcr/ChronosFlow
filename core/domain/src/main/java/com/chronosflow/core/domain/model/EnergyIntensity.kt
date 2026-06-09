package com.chronosflow.core.domain.model

enum class EnergyIntensity(val level: Int) {
    LOW(1),
    MODERATE(2),
    HIGH(3),
    INTENSE(4),
    MAX(5);

    companion object {
        fun fromLevel(level: Int?): EnergyIntensity {
            return when (level) {
                1 -> LOW
                2 -> MODERATE
                3 -> HIGH
                4 -> INTENSE
                5 -> MAX
                else -> MODERATE
            }
        }
    }
}

