package com.ChronosFlow.VBCR.core.data.mapper

import com.ChronosFlow.VBCR.core.data.model.AppUsageDayEntity
import com.ChronosFlow.VBCR.core.domain.model.AppUsageDay

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
