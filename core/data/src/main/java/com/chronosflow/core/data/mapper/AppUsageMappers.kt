package com.chronosflow.core.data.mapper

import com.chronosflow.core.data.model.AppUsageDayEntity
import com.chronosflow.core.domain.model.AppUsageDay

fun AppUsageDayEntity.toDomain(): AppUsageDay = AppUsageDay(
    date = date,
    productiveMinutes = productiveMinutes,
    distractingMinutes = distractingMinutes,
    neutralMinutes = neutralMinutes
)

fun AppUsageDay.toEntity(): AppUsageDayEntity = AppUsageDayEntity(
    date = date,
    productiveMinutes = productiveMinutes,
    distractingMinutes = distractingMinutes,
    neutralMinutes = neutralMinutes
)
