package com.chronosflow.core.domain.planner

import com.chronosflow.core.domain.model.DayPlanStatus
import com.chronosflow.core.domain.planner.PlannerTestFixtures.timeBlock
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DayPlanAssemblerTest {
    private val conflictDetectionEngine: ConflictDetectionEngine = mockk()
    private val assembler = DayPlanAssembler(conflictDetectionEngine)
    private val date = PlannerTestFixtures.date
    private val zone = ZoneId.of("UTC")

    @Test
    fun `assembles draft status when no blocks`() {
        every { conflictDetectionEngine.detect(emptyList()) } returns emptyList()

        val plan = assembler.assemble(date, zone, emptyList())

        assertEquals(DayPlanStatus.DRAFT, plan.status)
        assertTrue(plan.blocks.isEmpty())
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun `assembles planned status when blocks exist`() {
        val block = timeBlock(id = "1")
        every { conflictDetectionEngine.detect(listOf(block)) } returns emptyList()

        val plan = assembler.assemble(date, zone, listOf(block))

        assertEquals(DayPlanStatus.PLANNED, plan.status)
        assertEquals(1, plan.blocks.size)
        assertEquals("1", plan.blocks[0].id)
    }

    @Test
    fun `assembles completed status when review exists`() {
        val review = PlannerTestFixtures.dailyReviewSummary()
        every { conflictDetectionEngine.detect(emptyList()) } returns emptyList()

        val plan = assembler.assemble(date, zone, emptyList(), review = review)

        assertEquals(DayPlanStatus.COMPLETED, plan.status)
        assertEquals(review, plan.review)
    }

    @Test
    fun `filters blocks by date and sorts by time`() {
        val block1 = timeBlock(id = "1", startMinute = 600)
        val block2 = timeBlock(id = "2", startMinute = 400)
        val blockOtherDay = timeBlock(id = "3", date = date.plusDays(1))
        
        every { conflictDetectionEngine.detect(any()) } returns emptyList()

        val plan = assembler.assemble(date, zone, listOf(block1, block2, blockOtherDay))

        assertEquals(2, plan.blocks.size)
        assertEquals("2", plan.blocks[0].id) // 400 comes before 600
        assertEquals("1", plan.blocks[1].id)
    }
}
