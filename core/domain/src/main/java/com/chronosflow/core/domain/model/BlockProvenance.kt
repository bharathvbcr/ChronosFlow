package com.chronosflow.core.domain.model

enum class BlockProvenance {
    USER_CREATED,
    AI_SUGGESTED,
    CALENDAR_IMPORTED,
    TASK_CONVERTED,
    SYSTEM_GENERATED;

    companion object {
        fun fromSource(source: String?): BlockProvenance {
            return when (source?.uppercase()) {
                "AI", "AI_SUGGESTED", "SYSTEM" -> AI_SUGGESTED
                "CALENDAR", "CALENDAR_IMPORTED" -> CALENDAR_IMPORTED
                "TASK", "TASK_CONVERTED" -> TASK_CONVERTED
                "SYSTEM_GENERATED" -> SYSTEM_GENERATED
                else -> USER_CREATED
            }
        }
    }
}

