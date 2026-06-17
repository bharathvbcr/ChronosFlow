package com.chronosflow.core.data.health

import androidx.health.connect.client.records.SleepSessionRecord
import com.chronosflow.core.domain.model.SleepSource
import com.chronosflow.core.domain.model.SleepTrack
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Quality used when Health Connect gives no stage breakdown to reason about (1..5 scale). */
private const val DEFAULT_QUALITY = 3

/** Gap below which adjacent sessions count as one night (a tracker splitting at a brief wake). */
private val MERGE_GAP: Duration = Duration.ofHours(1)

/** Stages that represent wakefulness during the night; their count stands in for interruptions. */
private val AWAKE_STAGES = setOf(
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
)

/** Restorative stages — their share of asleep time drives the derived quality score. */
private val RESTORATIVE_STAGES = setOf(
    SleepSessionRecord.STAGE_TYPE_DEEP,
    SleepSessionRecord.STAGE_TYPE_REM
)

/** Every stage that counts as actually asleep (the denominator for the restorative share). */
private val ASLEEP_STAGES = setOf(
    SleepSessionRecord.STAGE_TYPE_LIGHT,
    SleepSessionRecord.STAGE_TYPE_DEEP,
    SleepSessionRecord.STAGE_TYPE_REM,
    SleepSessionRecord.STAGE_TYPE_SLEEPING
)

/** Interruption count at or above which the derived quality is docked a point for fragmentation. */
private const val FRAGMENTED_INTERRUPTIONS = 4

/**
 * Folds Health Connect sleep sessions into [SleepTrack] rows keyed by wake-day — the local date a
 * session ends — matching how the day-dial keys sleep to "the night ending that morning".
 *
 * Within a wake-day the sessions are clustered by adjacency (a gap under [MERGE_GAP] joins them, so
 * a tracker that splits the night into back-to-back records still reads as one night), and the
 * longest-spanning cluster wins. That keeps a daytime nap sharing the same wake-day from inflating
 * the night into a bed-at-23:00, wake-at-15:00 artifact. The winning cluster contributes its
 * earliest bedtime, latest wake time, and summed awake-stage count as interruptions. Quality is
 * unknown to Health Connect, so rows default to [DEFAULT_QUALITY]; every row is stamped
 * [SleepSource.HEALTH_CONNECT].
 */
fun List<SleepSessionRecord>.toSleepTracks(zoneId: ZoneId): List<SleepTrack> =
    groupBy { it.wakeDay(zoneId) }
        .map { (date, sessions) -> sessions.primaryNightCluster().toSleepTrack(date, zoneId) }

/** Sessions in the same wake-day, sorted and split into adjacency clusters; the widest span wins. */
private fun List<SleepSessionRecord>.primaryNightCluster(): List<SleepSessionRecord> {
    val sorted = sortedBy { it.startTime }
    val clusters = mutableListOf<MutableList<SleepSessionRecord>>()
    for (session in sorted) {
        val current = clusters.lastOrNull()
        val clusterEnd = current?.maxOf { it.endTime }
        if (current == null || clusterEnd == null ||
            Duration.between(clusterEnd, session.startTime) > MERGE_GAP
        ) {
            clusters.add(mutableListOf(session))
        } else {
            current.add(session)
        }
    }
    return clusters.maxByOrNull { cluster ->
        Duration.between(cluster.minOf { it.startTime }, cluster.maxOf { it.endTime })
    }!!
}

private fun List<SleepSessionRecord>.toSleepTrack(date: LocalDate, zoneId: ZoneId): SleepTrack {
    val earliest = minByOrNull { it.startTime }!!
    val latest = maxByOrNull { it.endTime }!!
    val interruptions = sumOf { session -> session.stages.count { it.stage in AWAKE_STAGES } }
    return SleepTrack(
        id = "hc-$date",
        date = date,
        plannedStartMinute = null,
        plannedEndMinute = null,
        actualStartMinute = minuteOfDay(earliest.startTime, earliest.startZone(zoneId)),
        actualEndMinute = minuteOfDay(latest.endTime, latest.endZone(zoneId)),
        sleepQuality = deriveQuality(interruptions),
        windDownNotes = null,
        interruptedCount = interruptions,
        source = SleepSource.HEALTH_CONNECT
    )
}

/**
 * Estimates a 1..5 quality from the restorative (deep + REM) share of asleep time, docking a point
 * when the night was heavily fragmented. Trackers that don't break the night into stages give no
 * signal to reason about, so those nights fall back to [DEFAULT_QUALITY] rather than scoring poorly.
 */
private fun List<SleepSessionRecord>.deriveQuality(interruptions: Int): Int {
    val stages = flatMap { it.stages }
    val hasStageDetail = stages.any {
        it.stage == SleepSessionRecord.STAGE_TYPE_LIGHT ||
            it.stage == SleepSessionRecord.STAGE_TYPE_DEEP ||
            it.stage == SleepSessionRecord.STAGE_TYPE_REM
    }
    if (!hasStageDetail) return DEFAULT_QUALITY

    val asleepMillis = stages.filter { it.stage in ASLEEP_STAGES }.sumOfMillis()
    if (asleepMillis <= 0) return DEFAULT_QUALITY
    val restorativeMillis = stages.filter { it.stage in RESTORATIVE_STAGES }.sumOfMillis()

    val restorativeShare = restorativeMillis.toDouble() / asleepMillis
    val base = when {
        restorativeShare >= 0.45 -> 5
        restorativeShare >= 0.35 -> 4
        restorativeShare >= 0.22 -> 3
        restorativeShare >= 0.12 -> 2
        else -> 1
    }
    val docked = if (interruptions >= FRAGMENTED_INTERRUPTIONS) base - 1 else base
    return docked.coerceIn(1, 5)
}

private fun List<SleepSessionRecord.Stage>.sumOfMillis(): Long =
    sumOf { Duration.between(it.startTime, it.endTime).toMillis() }

private fun SleepSessionRecord.wakeDay(zoneId: ZoneId): LocalDate =
    endTime.atZone(endZone(zoneId)).toLocalDate()

private fun SleepSessionRecord.startZone(zoneId: ZoneId): ZoneId = startZoneOffset ?: zoneId

private fun SleepSessionRecord.endZone(zoneId: ZoneId): ZoneId = endZoneOffset ?: zoneId

private fun minuteOfDay(instant: Instant, zone: ZoneId): Int {
    val time = instant.atZone(zone).toLocalTime()
    return time.hour * 60 + time.minute
}
