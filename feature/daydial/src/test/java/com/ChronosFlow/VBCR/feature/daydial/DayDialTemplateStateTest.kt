package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlockBlueprint
import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayDialTemplateStateTest {

    @Test
    fun `suggestDayDialTemplateName returns base name when unused`() {
        assertEquals("My Day Template", suggestDayDialTemplateName(emptyList()))
    }

    @Test
    fun `suggestDayDialTemplateName appends next number when base name exists`() {
        val existingNames = listOf("My Day Template", "My Day Template 2")

        assertEquals("My Day Template 3", suggestDayDialTemplateName(existingNames))
    }

    @Test
    fun `suggestDayDialTemplateName ignores whitespace and case when matching existing names`() {
        val existingNames = listOf(" my day template ", "MY DAY TEMPLATE 2")

        assertEquals("My Day Template 3", suggestDayDialTemplateName(existingNames))
    }

    @Test
    fun `moveTemplateDraftBlock swaps neighboring blocks`() {
        val drafts = listOf(
            TemplateBlockDraft(
                id = "a",
                title = "One",
                startText = "08:00",
                durationText = "30",
                category = "WORK"
            ),
            TemplateBlockDraft(
                id = "b",
                title = "Two",
                startText = "09:00",
                durationText = "45",
                category = "BREAK"
            )
        )

        val moved = moveTemplateDraftBlock(drafts, index = 1, direction = -1)

        assertEquals(listOf("b", "a"), moved.map { it.id })
    }

    @Test
    fun `template draft block action labels name block and position`() {
        val draft = TemplateBlockDraft(
            id = "draft-1",
            title = "Deep Work",
            startText = "09:00",
            durationText = "90",
            category = "WORK"
        )
        val untitled = draft.copy(title = " ")

        assertEquals("Move Deep Work block 2 up", templateDraftBlockMoveUpActionLabel(draft, index = 1))
        assertEquals("Move Deep Work block 2 down", templateDraftBlockMoveDownActionLabel(draft, index = 1))
        assertEquals("Delete Deep Work block 2", templateDraftBlockDeleteActionLabel(draft, index = 1))
        assertEquals("Delete Untitled block 1", templateDraftBlockDeleteActionLabel(untitled, index = 0))
    }

    @Test
    fun `normalizeTemplateDraftBlocks rejects invalid drafts`() {
        val invalidDrafts = listOf(
            TemplateBlockDraft(
                id = "draft-1",
                title = "",
                startText = "not-a-time",
                durationText = "25",
                category = "WORK"
            )
        )

        assertNull(normalizeTemplateDraftBlocks(invalidDrafts))
    }

    @Test
    fun `saveTemplateEdit updates built in template in place`() {
        val builtIns = listOf(
            TemplateBlueprint(
                id = "tpl_workday",
                name = "Workday",
                blocks = listOf(
                    TemplateBlockBlueprint("Morning Routine", 8 * 60, 45, "PERSONAL")
                )
            )
        )

        val result = saveTemplateEdit(
            isCreatingTemplate = false,
            builtInTemplates = builtIns,
            customTemplates = emptyList(),
            templateToEdit = builtIns.single(),
            editedName = "Workday Prime",
            draftBlocks = listOf(
                TemplateBlockDraft(
                    id = "draft-1",
                    title = "Maker Time",
                    startText = "10:00",
                    durationText = "90",
                    category = "WORK"
                )
            )
        ) ?: error("Expected template save result")

        assertEquals(listOf("tpl_workday"), result.customTemplates.map { it.id })
        assertEquals("Workday Prime", result.templates.single().name)
        assertEquals("Maker Time", result.templates.single().blocks.single().title)
        assertEquals(10 * 60, result.templates.single().blocks.single().startMinute)
    }

    @Test
    fun `saveTemplateEdit creates new template in create mode`() {
        val builtIns = listOf(
            TemplateBlueprint(
                id = "tpl_workday",
                name = "Workday",
                blocks = listOf(
                    TemplateBlockBlueprint("Morning Routine", 8 * 60, 45, "PERSONAL")
                )
            )
        )

        val result = saveTemplateEdit(
            isCreatingTemplate = true,
            builtInTemplates = builtIns,
            customTemplates = emptyList(),
            templateToEdit = null,
            editedName = " Focus Template ",
            draftBlocks = listOf(
                TemplateBlockDraft(
                    id = "draft-1",
                    title = "Deep Work",
                    startText = "09:00",
                    durationText = "3",
                    category = "work"
                )
            ),
            idGenerator = { "custom-id" }
        ) ?: error("Expected template save result")

        assertEquals("custom-id", result.savedTemplate.id)
        assertEquals("Focus Template", result.savedTemplate.name)
        assertEquals(5, result.savedTemplate.blocks.single().durationMinutes)
        assertEquals("WORK", result.savedTemplate.blocks.single().category)
    }

    @Test
    fun `moveTemplateDraftBlock keeps list when moving past boundaries`() {
        val drafts = listOf(
            TemplateBlockDraft(
                id = "a",
                title = "One",
                startText = "08:00",
                durationText = "30",
                category = "WORK"
            )
        )

        val movedFromStart = moveTemplateDraftBlock(drafts, index = 0, direction = -1)
        val movedFromEnd = moveTemplateDraftBlock(drafts, index = 0, direction = 1)

        assertEquals(drafts, movedFromStart)
        assertEquals(drafts, movedFromEnd)
    }

    @Test
    fun `template draft add block action label uses plural form for more than one block`() {
        assertEquals("Add block", templateDraftAddBlockActionLabel(0))
        assertEquals("Add block", templateDraftAddBlockActionLabel(1))
        assertEquals("Add another block", templateDraftAddBlockActionLabel(2))
    }

    @Test
    fun `parseDayDialBackupBlocks preserves escaped separators in exported titles`() {
        val backupText = """
            ChronosFlow Backup v2
            block|Research \| writing|09:15|90|WORK|locked=false|protected=true
        """.trimIndent()

        val blocks = parseDayDialBackupBlocks(backupText)

        assertEquals(1, blocks.size)
        assertEquals("Research | writing", blocks.single().title)
        assertEquals(9 * 60 + 15, blocks.single().startMinute)
        assertEquals(90, blocks.single().durationMinutes)
        assertEquals("WORK", blocks.single().category)
    }

    @Test
    fun `templateDraftBlockMoveUpActionLabel updates with provided index`() {
        val draft = TemplateBlockDraft(
            id = "draft-1",
            title = "Focus",
            startText = "09:00",
            durationText = "90",
            category = "WORK"
        )

        assertEquals(
            "Move Focus block 1 up",
            templateDraftBlockMoveUpActionLabel(draft, index = 0)
        )
        assertEquals(
            "Move Focus block 7 down",
            templateDraftBlockMoveDownActionLabel(draft, index = 6)
        )
        assertEquals(
            "Delete Untitled block 3",
            templateDraftBlockDeleteActionLabel(draft.copy(title = " "), index = 2)
        )
    }

    @Test
    fun `applyTemplateBlueprint creates every block from template`() {
        val createdTitles = mutableListOf<String>()
        val template = TemplateBlueprint(
            id = "tpl_workday",
            name = "Workday",
            blocks = listOf(
                TemplateBlockBlueprint("Deep Work", 9 * 60, 90, "WORK"),
                TemplateBlockBlueprint("Lunch", 12 * 60, 45, "BREAK")
            )
        )

        applyTemplateBlueprint(template) { title, _, _, _ ->
            createdTitles += title
        }

        assertEquals(listOf("Deep Work", "Lunch"), createdTitles)
    }
}
