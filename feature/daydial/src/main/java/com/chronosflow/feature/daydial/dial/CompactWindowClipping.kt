package com.chronosflow.feature.daydial.dial

private const val DAY_IN_MINUTES = 1440

internal data class VisibleWindowSlice(
    val relativeStart: Int,
    val visibleDuration: Int
)

internal fun visibleWindowSlices(
    startMinute: Int,
    durationMinutes: Int,
    windowStart: Int,
    windowMinutes: Int
): List<VisibleWindowSlice> {
    val normalizedDuration = durationMinutes.coerceIn(0, DAY_IN_MINUTES)
    if (normalizedDuration == 0) return emptyList()

    val relativeStart = ((startMinute - windowStart + DAY_IN_MINUTES) % DAY_IN_MINUTES)
    if (windowMinutes >= DAY_IN_MINUTES) {
        return listOf(VisibleWindowSlice(relativeStart = relativeStart, visibleDuration = normalizedDuration))
    }
    val slices = buildList {
        add(relativeStart to relativeStart + normalizedDuration)
        if (relativeStart + normalizedDuration > DAY_IN_MINUTES) {
            add(0 to (relativeStart + normalizedDuration - DAY_IN_MINUTES))
        }
    }.mapNotNull { (start, end) ->
        val clippedStart = maxOf(start, 0)
        val clippedEnd = minOf(end, windowMinutes)
        if (clippedEnd > clippedStart) {
            VisibleWindowSlice(
                relativeStart = clippedStart,
                visibleDuration = clippedEnd - clippedStart
            )
        } else {
            null
        }
    }.sortedBy { it.relativeStart }

    return slices
}

internal fun visibleWindowSlice(
    startMinute: Int,
    durationMinutes: Int,
    windowStart: Int,
    windowMinutes: Int
): VisibleWindowSlice? = visibleWindowSlices(startMinute, durationMinutes, windowStart, windowMinutes).firstOrNull()
