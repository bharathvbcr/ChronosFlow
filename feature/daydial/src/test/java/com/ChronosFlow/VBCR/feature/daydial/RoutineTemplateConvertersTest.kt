package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.ui.graphics.Color
import com.ChronosFlow.VBCR.core.domain.model.Routine
import com.ChronosFlow.VBCR.core.domain.model.RoutineStep
import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlockBlueprint
import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlueprint
import com.ChronosFlow.VBCR.feature.daydial.model.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTemplateConvertersTest {

    private fun template(vararg blocks: TemplateBlockBlueprint) = TemplateBlueprint(
        id = "tpl-1",
        name = "Morning Plan",
        blocks = blocks.toList()
    )

    private fun uiBlock(
        id: String,
        routineId: String?,
        actualEndMinuteOfDay: Int? = null
    ) = TimeBlockUiModel(
        id = id,
        title = "Block $id",
        startMinuteOfDay = 9 * 60,
        durationMinutes = 30,
        color = Color.Unspecified,
        routineId = routineId,
        actualEndMinuteOfDay = actualEndMinuteOfDay
    )

    @Test
    fun `toRoutine stores absolute start minutes as step offsets sorted by start`() {
        val routine = template(
            TemplateBlockBlueprint("Deep Work", 9 * 60, 90, "WORK"),
            TemplateBlockBlueprint("Stretch", 8 * 60, 15, "PERSONAL")
        ).toRoutine()

        assertEquals("tpl-1", routine.id)
        assertEquals("Morning Plan", routine.title)
        assertTrue(routine.isActive)
        assertNull(routine.lastCompletedDate)
        assertEquals(listOf("Stretch", "Deep Work"), routine.steps.map { it.title })
        assertEquals(listOf(8 * 60, 9 * 60), routine.steps.map { it.offsetMinute })
        assertEquals(listOf(15, 90), routine.steps.map { it.durationMinutes })
        assertEquals(listOf("PERSONAL", "WORK"), routine.steps.map { it.category })
    }

    @Test
    fun `toRoutine falls back to default category for blank block category`() {
        val routine = template(
            TemplateBlockBlueprint("Untagged", 10 * 60, 25, "")
        ).toRoutine()

        assertEquals(RoutineStep.DEFAULT_CATEGORY, routine.steps.single().category)
        assertEquals(RoutineStep.DEFAULT_ENERGY_LEVEL, routine.steps.single().energyLevel)
    }

    @Test
    fun `template to routine to template round trip preserves blocks`() {
        val original = template(
            TemplateBlockBlueprint("Morning Routine", 8 * 60, 45, "PERSONAL"),
            TemplateBlockBlueprint("Deep Work", 9 * 60, 90, "WORK"),
            TemplateBlockBlueprint("Lunch", 12 * 60 + 30, 45, "PERSONAL")
        )

        val roundTripped = original.toRoutine().toTemplateBlueprint()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.blocks, roundTripped.blocks)
    }

    @Test
    fun `toTemplateBlueprint sorts steps by offset and normalizes out-of-range offsets`() {
        val routine = Routine(
            id = "r-1",
            title = "Wrap",
            isActive = true,
            lastCompletedDate = null,
            steps = listOf(
                RoutineStep(id = "s2", title = "Late", offsetMinute = 25 * 60, durationMinutes = 30),
                RoutineStep(id = "s1", title = "Early", offsetMinute = 7 * 60, durationMinutes = 30)
            )
        )

        val blueprint = routine.toTemplateBlueprint()

        assertEquals(listOf("Early", "Late"), blueprint.blocks.map { it.title })
        assertEquals(listOf(7 * 60, 1 * 60), blueprint.blocks.map { it.startMinute })
    }

    @Test
    fun `deriveRoutineCompletions counts done and total blocks per routine`() {
        val completions = deriveRoutineCompletions(
            listOf(
                uiBlock("a", routineId = "r-1", actualEndMinuteOfDay = 10 * 60),
                uiBlock("b", routineId = "r-1", actualEndMinuteOfDay = null),
                uiBlock("c", routineId = "r-2", actualEndMinuteOfDay = 11 * 60),
                uiBlock("d", routineId = null, actualEndMinuteOfDay = 12 * 60)
            )
        )

        assertEquals(2, completions.size)
        assertEquals(RoutineCompletionSummary(doneCount = 1, totalCount = 2), completions["r-1"])
        assertEquals(RoutineCompletionSummary(doneCount = 1, totalCount = 1), completions["r-2"])
    }

    @Test
    fun `deriveRoutineCompletions returns empty map when no routine blocks exist`() {
        assertTrue(deriveRoutineCompletions(listOf(uiBlock("a", routineId = null))).isEmpty())
    }
}
