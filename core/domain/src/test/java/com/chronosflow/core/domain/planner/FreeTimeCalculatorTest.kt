package com.chronosflow.core.domain.planner

import com.chronosflow.core.domain.planner.PlannerTestFixtures.timeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTimeCalculatorTest {
    private val calculator = FreeTimeCalculator()

    @Test
    fun `empty list returns full day as free`() {
        val result = calculator.calculate(emptyList())
        assertEquals(1, result.size)
        assertEquals(FreeTimeSegment(0, 1440), result[0])
    }

    @Test
    fun `single block splits day into two segments`() {
        val block = timeBlock(startMinute = 600, durationMinutes = 60) // 10:00 to 11:00
        val result = calculator.calculate(listOf(block))
        assertEquals(2, result.size)
        assertEquals(FreeTimeSegment(0, 600), result[0])
        assertEquals(FreeTimeSegment(660, 1440), result[1])
    }

    @Test
    fun `multiple non-overlapping blocks split day correctly`() {
        val blocks = listOf(
            timeBlock(id = "1", startMinute = 100, durationMinutes = 50),
            timeBlock(id = "2", startMinute = 300, durationMinutes = 100)
        )
        val result = calculator.calculate(blocks)
        assertEquals(3, result.size)
        assertEquals(FreeTimeSegment(0, 100), result[0])
        assertEquals(FreeTimeSegment(150, 300), result[1])
        assertEquals(FreeTimeSegment(400, 1440), result[2])
    }

    @Test
    fun `overlapping blocks are handled correctly`() {
        val blocks = listOf(
            timeBlock(id = "1", startMinute = 100, durationMinutes = 100),
            timeBlock(id = "2", startMinute = 150, durationMinutes = 100)
        )
        val result = calculator.calculate(blocks)
        assertEquals(2, result.size)
        assertEquals(FreeTimeSegment(0, 100), result[0])
        assertEquals(FreeTimeSegment(250, 1440), result[1])
    }

    @Test
    fun `block at start of day`() {
        val block = timeBlock(startMinute = 0, durationMinutes = 60)
        val result = calculator.calculate(listOf(block))
        assertEquals(1, result.size)
        assertEquals(FreeTimeSegment(60, 1440), result[0])
    }

    @Test
    fun `block at end of day`() {
        val block = timeBlock(startMinute = 1380, durationMinutes = 60)
        val result = calculator.calculate(listOf(block))
        assertEquals(1, result.size)
        assertEquals(FreeTimeSegment(0, 1380), result[0])
    }

    @Test
    fun `full day occupied returns empty list`() {
        val block = timeBlock(startMinute = 0, durationMinutes = 1440)
        val result = calculator.calculate(listOf(block))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `block wrapping around midnight occupies both ends`() {
        val block = timeBlock(startMinute = 1400, durationMinutes = 100) // 1400 to 1440, then 0 to 60
        val result = calculator.calculate(listOf(block))
        assertEquals(1, result.size)
        assertEquals(FreeTimeSegment(60, 1400), result[0])
    }
}
