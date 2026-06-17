package com.chronosflow.core.data.health

import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.chronosflow.core.domain.model.SleepSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SleepSessionMapperTest {

    private val utc = ZoneOffset.UTC

    private fun session(
        start: String,
        end: String,
        stages: List<SleepSessionRecord.Stage> = emptyList()
    ) = SleepSessionRecord(
        startTime = Instant.parse(start),
        startZoneOffset = utc,
        endTime = Instant.parse(end),
        endZoneOffset = utc,
        stages = stages,
        metadata = Metadata.manualEntry()
    )

    private fun awakeStage(start: String, end: String) = SleepSessionRecord.Stage(
        startTime = Instant.parse(start),
        endTime = Instant.parse(end),
        stage = SleepSessionRecord.STAGE_TYPE_AWAKE
    )

    private fun stage(start: String, end: String, type: Int) = SleepSessionRecord.Stage(
        startTime = Instant.parse(start),
        endTime = Instant.parse(end),
        stage = type
    )

    @Test
    fun `session is keyed by local wake-day and converts start and end to minute-of-day`() {
        val tracks = listOf(
            session(start = "2026-06-11T23:00:00Z", end = "2026-06-12T07:00:00Z")
        ).toSleepTracks(utc)

        assertEquals(1, tracks.size)
        val track = tracks.single()
        assertEquals(java.time.LocalDate.of(2026, 6, 12), track.date)
        assertEquals(23 * 60, track.actualStartMinute)
        assertEquals(7 * 60, track.actualEndMinute)
        assertEquals(SleepSource.HEALTH_CONNECT, track.source)
        assertEquals("hc-2026-06-12", track.id)
    }

    @Test
    fun `multiple sessions on one wake-day merge to earliest bed, latest wake, summed awake stages`() {
        val tracks = listOf(
            session(
                start = "2026-06-11T22:30:00Z",
                end = "2026-06-12T02:00:00Z",
                stages = listOf(awakeStage("2026-06-11T23:30:00Z", "2026-06-11T23:45:00Z"))
            ),
            session(
                start = "2026-06-12T02:10:00Z",
                end = "2026-06-12T07:15:00Z",
                stages = listOf(
                    awakeStage("2026-06-12T03:00:00Z", "2026-06-12T03:05:00Z"),
                    awakeStage("2026-06-12T05:00:00Z", "2026-06-12T05:10:00Z")
                )
            )
        ).toSleepTracks(utc)

        val track = tracks.single()
        assertEquals(22 * 60 + 30, track.actualStartMinute)
        assertEquals(7 * 60 + 15, track.actualEndMinute)
        assertEquals(3, track.interruptedCount)
    }

    @Test
    fun `quality defaults when the tracker gives no stage breakdown`() {
        val track = listOf(
            session(start = "2026-06-11T23:00:00Z", end = "2026-06-12T07:00:00Z")
        ).toSleepTracks(utc).single()

        assertEquals(3, track.sleepQuality)
    }

    @Test
    fun `quality rises with a high deep and REM share of the night`() {
        // 8h asleep, ~4h restorative (deep + REM) → ~50% share → top score.
        val track = listOf(
            session(
                start = "2026-06-11T23:00:00Z",
                end = "2026-06-12T07:00:00Z",
                stages = listOf(
                    stage("2026-06-11T23:00:00Z", "2026-06-12T01:00:00Z", SleepSessionRecord.STAGE_TYPE_LIGHT),
                    stage("2026-06-12T01:00:00Z", "2026-06-12T03:30:00Z", SleepSessionRecord.STAGE_TYPE_DEEP),
                    stage("2026-06-12T03:30:00Z", "2026-06-12T05:00:00Z", SleepSessionRecord.STAGE_TYPE_REM),
                    stage("2026-06-12T05:00:00Z", "2026-06-12T07:00:00Z", SleepSessionRecord.STAGE_TYPE_LIGHT)
                )
            )
        ).toSleepTracks(utc).single()

        assertEquals(5, track.sleepQuality)
    }

    @Test
    fun `quality is low when little of the night is deep or REM`() {
        // 8h asleep, only 30min restorative → ~6% share → bottom score.
        val track = listOf(
            session(
                start = "2026-06-11T23:00:00Z",
                end = "2026-06-12T07:00:00Z",
                stages = listOf(
                    stage("2026-06-11T23:00:00Z", "2026-06-12T06:30:00Z", SleepSessionRecord.STAGE_TYPE_LIGHT),
                    stage("2026-06-12T06:30:00Z", "2026-06-12T07:00:00Z", SleepSessionRecord.STAGE_TYPE_DEEP)
                )
            )
        ).toSleepTracks(utc).single()

        assertEquals(1, track.sleepQuality)
    }

    @Test
    fun `daytime nap sharing the wake-day does not inflate the overnight sleep`() {
        val tracks = listOf(
            // Overnight sleep ending the morning of the 12th.
            session(start = "2026-06-11T23:00:00Z", end = "2026-06-12T07:00:00Z"),
            // Afternoon nap the same calendar day — same wake-day, but a separate, shorter cluster.
            session(start = "2026-06-12T14:00:00Z", end = "2026-06-12T15:00:00Z")
        ).toSleepTracks(utc)

        val track = tracks.single()
        assertEquals(23 * 60, track.actualStartMinute)
        assertEquals(7 * 60, track.actualEndMinute)
    }
}
