package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.ai.genai.RewriteStyle
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import org.junit.Assert.assertNull
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAssistPlannerTest {
    @Test
    fun `task prompt includes concrete capture examples`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        val prompt = slot<String>()
        coEvery { coordinator.generateAssistText(capture(prompt)) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        planner.suggest(TaskAssistRequest(title = "Call mom tomorrow"))

        assertTrue(prompt.captured.contains("Input: call mom tomorrow"))
        assertTrue(prompt.captured.contains("action_phone|Call mom||"))
        assertTrue(prompt.captured.contains("schedule_duration|Quick call|15m"))
        assertTrue(prompt.captured.contains("review launch brief https://docs.example.com/brief"))
    }

    @Test
    fun `refineTitle returns proofread title when materially different`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.proofread("emial Alx the reprot") } returns AssistTextGeneration(
            text = "Email Alex the report",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestion = planner.refineTitle("emial Alx the reprot")

        assertEquals("Email Alex the report", suggestion?.title)
        assertEquals(TaskAssistSource.GEMINI_NANO, suggestion?.source)
    }

    @Test
    fun `refineTitle returns null when proofread matches input`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.proofread(any()) } returns AssistTextGeneration(
            text = "Email Alex",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = TaskAssistPlanner(coordinator)

        assertNull(planner.refineTitle("Email Alex"))
    }

    @Test
    fun `rewriteText returns rewritten text when available`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.rewrite("write the launch recap", RewriteStyle.SHORTEN) } returns AssistTextGeneration(
            text = "Recap the launch",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = TaskAssistPlanner(coordinator)

        assertEquals("Recap the launch", planner.rewriteText("write the launch recap", RewriteStyle.SHORTEN))
    }

    @Test
    fun `suggest parses Gemini Nano suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "checklist|Add launch steps|Review notes;Email team|Task needs explicit steps",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Email launch team"))

        val checklist = suggestions.single() as TaskAssistSuggestion.Checklist
        assertEquals(listOf("Review notes", "Email team"), checklist.items)
        assertEquals(TaskAssistSource.GEMINI_NANO, checklist.source)
    }

    @Test
    fun `suggest keeps generated action suggestions that need a manual destination`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "action_phone|Call Alex||The task asks for a call but no number is known",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Call Alex"))

        val action = suggestions.single() as TaskAssistSuggestion.ActionDraft
        assertEquals(TaskAssistSource.GEMINI_NANO, action.source)
        assertEquals(TaskActionType.PHONE, action.payload.type)
        assertEquals("Call Alex", action.payload.label)
        assertEquals("", action.payload.value)
    }

    @Test
    fun `suggest parses generated map and deep link action suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                action_map|Open venue|geo:0,0?q=Launch+venue|The task mentions a venue
                action_deep_link|Open project|chronos://task/launch|The task links to a project workspace
            """.trimIndent(),
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Launch venue"))

        val actions = suggestions.filterIsInstance<TaskAssistSuggestion.ActionDraft>()
        assertEquals(2, actions.size)
        assertEquals(TaskAssistSource.CLOUD_GEMINI, actions.first().source)
        assertEquals(TaskActionType.MAP, actions[0].payload.type)
        assertEquals("Open venue", actions[0].payload.label)
        assertEquals("geo:0,0?q=Launch+venue", actions[0].payload.value)
        assertEquals(TaskActionType.CUSTOM_DEEP_LINK, actions[1].payload.type)
        assertEquals("Open project", actions[1].payload.label)
        assertEquals("chronos://task/launch", actions[1].payload.value)
    }

    @Test
    fun `suggest parses generated document and app action suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                action_document|Open brief|https://docs.example.com/brief|The task references a source document
                action_app|Open journal|com.example.journal|The task should start in the journal app
            """.trimIndent(),
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch brief in journal"))

        val actions = suggestions.filterIsInstance<TaskAssistSuggestion.ActionDraft>()
        assertEquals(2, actions.size)
        assertEquals(TaskAssistSource.GEMINI_NANO, actions.first().source)
        assertEquals(TaskActionType.DOCUMENT, actions[0].payload.type)
        assertEquals("Open brief", actions[0].payload.label)
        assertEquals("https://docs.example.com/brief", actions[0].payload.value)
        assertEquals(TaskActionType.APP, actions[1].payload.type)
        assertEquals("Open journal", actions[1].payload.label)
        assertEquals("com.example.journal", actions[1].payload.value)
    }

    @Test
    fun `suggest normalizes generated app action values`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                action_app|Open journal|package:com.example.journal|The task should start in the journal app
                action_app|Open editor|component:com.example.editor/.MainActivity|The task references an editor activity
            """.trimIndent(),
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch brief in journal"))

        val values = suggestions
            .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
            .map { it.payload.value }
        assertEquals(
            listOf(
                "com.example.journal",
                "component:com.example.editor/com.example.editor.MainActivity"
            ),
            values
        )
    }

    @Test
    fun `suggest trims generated action destinations`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                action_email|Email Alex|alex@example.com.|The task includes an email address
                action_link|Open brief|https://example.com/brief).|The task references a URL
            """.trimIndent(),
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Email Alex and open brief"))

        val actions = suggestions.filterIsInstance<TaskAssistSuggestion.ActionDraft>()
        assertEquals("alex@example.com", actions[0].payload.value)
        assertEquals("https://example.com/brief", actions[1].payload.value)
    }

    @Test
    fun `suggest falls back locally when generation is empty`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Call Alex today"))

        assertEquals(TaskAssistSource.LOCAL, suggestions.first().source)
        assertEquals(TaskActionType.PHONE, (suggestions.last() as TaskAssistSuggestion.ActionDraft).payload.type)
    }

    @Test
    fun `local fallback extracts email destinations from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Email launch team at launch@example.com"))

        val action = suggestions
            .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
            .single { it.payload.type == TaskActionType.EMAIL }
        assertEquals("launch@example.com", action.payload.value)
    }

    @Test
    fun `local fallback extracts phone destinations from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Call Alex at +1 555 123 4567"))

        val action = suggestions
            .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
            .single { it.payload.type == TaskActionType.PHONE }
        assertEquals("+1 555 123 4567", action.payload.value)
    }

    @Test
    fun `local fallback extracts link destinations from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch brief https://example.com/brief"))

        val action = suggestions
            .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
            .single { it.payload.type == TaskActionType.WEBSITE }
        assertEquals("https://example.com/brief", action.payload.value)
    }

    @Test
    fun `local fallback extracts document destinations from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review document https://docs.example.com/brief"))

        val action = suggestions
            .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
            .single { it.payload.type == TaskActionType.DOCUMENT }
        assertEquals("https://docs.example.com/brief", action.payload.value)
    }

    @Test
    fun `local fallback keeps separate website and document links`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            TaskAssistRequest(
                title = "Review document https://example.com/landing and https://docs.example.com/brief"
            )
        )

        val actions = suggestions.filterIsInstance<TaskAssistSuggestion.ActionDraft>()
        assertEquals(
            "https://example.com/landing",
            actions.single { it.payload.type == TaskActionType.WEBSITE }.payload.value
        )
        assertEquals(
            "https://docs.example.com/brief",
            actions.single { it.payload.type == TaskActionType.DOCUMENT }.payload.value
        )
    }

    @Test
    fun `local fallback keeps later website links when document link appears first`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            TaskAssistRequest(
                title = "Review document https://docs.example.com/brief and publish https://example.com/landing"
            )
        )

        val actions = suggestions.filterIsInstance<TaskAssistSuggestion.ActionDraft>()
        assertEquals(
            "https://example.com/landing",
            actions.single { it.payload.type == TaskActionType.WEBSITE }.payload.value
        )
        assertEquals(
            "https://docs.example.com/brief",
            actions.single { it.payload.type == TaskActionType.DOCUMENT }.payload.value
        )
    }

    @Test
    fun `local fallback extracts app launch targets from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Open app com.example.journal"))

        val action = suggestions
            .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
            .single { it.payload.type == TaskActionType.APP }
        assertEquals("com.example.journal", action.payload.value)
    }

    @Test
    fun `local fallback treats app uri launch targets as app actions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Open app journal://new"))

        val actions = suggestions.filterIsInstance<TaskAssistSuggestion.ActionDraft>()
        val appAction = actions.single { it.payload.type == TaskActionType.APP }
        assertEquals("journal://new", appAction.payload.value)
        assertEquals(
            emptyList<TaskAssistSuggestion.ActionDraft>(),
            actions.filter { it.payload.type == TaskActionType.CUSTOM_DEEP_LINK }
        )
    }

    @Test
    fun `local fallback extracts map destinations from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Open venue map geo:0,0?q=Launch+venue"))

        val action = suggestions
            .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
            .single { it.payload.type == TaskActionType.MAP }
        assertEquals("geo:0,0?q=Launch+venue", action.payload.value)
    }

    @Test
    fun `local fallback extracts custom deep link destinations from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Open project chronos://task/launch"))

        val action = suggestions
            .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
            .single { it.payload.type == TaskActionType.CUSTOM_DEEP_LINK }
        assertEquals("chronos://task/launch", action.payload.value)
    }

    @Test
    fun `local fallback keeps explicit action destinations when suggestions hit the cap`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            TaskAssistRequest(
                title = "Call Alex today urgent at +1 555 123 4567 email launch@example.com https://example.com/brief"
            )
        )

        val actionTypes = suggestions
            .filterIsInstance<TaskAssistSuggestion.ActionDraft>()
            .map { it.payload.type }
            .toSet()
        assertEquals(6, suggestions.size)
        assertEquals(
            setOf(TaskActionType.PHONE, TaskActionType.EMAIL, TaskActionType.WEBSITE),
            actionTypes
        )
    }

    @Test
    fun `local fallback extracts explicit target dates from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        // Anchor to a fixed offset from today so the date is always in the future. An explicit
        // calendar date that happens to land on today/tomorrow renders as "Target today"/
        // "Target tomorrow" (see localTargetDateLabel), which would make this assertion fail
        // whenever the suite runs on that calendar day.
        val target = java.time.LocalDate.now().plusMonths(3)
        val suggestions = planner.suggest(TaskAssistRequest(title = "Submit launch notes by $target"))

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.targetDate != null }
        assertEquals(target, schedule.payload.targetDate)
        assertEquals("Target $target", schedule.label)
    }

    @Test
    fun `local fallback extracts common formatted target dates from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val slashSuggestions = planner.suggest(TaskAssistRequest(title = "Submit launch notes by 6/15/2026"))
        val monthSuggestions = planner.suggest(TaskAssistRequest(title = "Follow up on June 16, 2026"))

        assertEquals(
            java.time.LocalDate.of(2026, 6, 15),
            slashSuggestions
                .filterIsInstance<TaskAssistSuggestion.Schedule>()
                .single { it.payload.targetDate != null }
                .payload.targetDate
        )
        assertEquals(
            java.time.LocalDate.of(2026, 6, 16),
            monthSuggestions
                .filterIsInstance<TaskAssistSuggestion.Schedule>()
                .single { it.payload.targetDate != null }
                .payload.targetDate
        )
    }

    @Test
    fun `local fallback extracts month target dates without commas from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val fullMonthSuggestions = planner.suggest(TaskAssistRequest(title = "Follow up on June 16 2026"))
        val abbreviatedMonthSuggestions = planner.suggest(TaskAssistRequest(title = "Archive notes on Jun 17 2026"))

        assertEquals(
            java.time.LocalDate.of(2026, 6, 16),
            fullMonthSuggestions
                .filterIsInstance<TaskAssistSuggestion.Schedule>()
                .single { it.payload.targetDate != null }
                .payload.targetDate
        )
        assertEquals(
            java.time.LocalDate.of(2026, 6, 17),
            abbreviatedMonthSuggestions
                .filterIsInstance<TaskAssistSuggestion.Schedule>()
                .single { it.payload.targetDate != null }
                .payload.targetDate
        )
    }

    @Test
    fun `local fallback extracts ordinal month target dates from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val fullMonthSuggestions = planner.suggest(TaskAssistRequest(title = "Follow up on June 16th, 2026"))
        val abbreviatedMonthSuggestions = planner.suggest(TaskAssistRequest(title = "Archive notes on Jun 17th 2026"))

        assertEquals(
            java.time.LocalDate.of(2026, 6, 16),
            fullMonthSuggestions
                .filterIsInstance<TaskAssistSuggestion.Schedule>()
                .single { it.payload.targetDate != null }
                .payload.targetDate
        )
        assertEquals(
            java.time.LocalDate.of(2026, 6, 17),
            abbreviatedMonthSuggestions
                .filterIsInstance<TaskAssistSuggestion.Schedule>()
                .single { it.payload.targetDate != null }
                .payload.targetDate
        )
    }

    @Test
    fun `local fallback keeps first explicit target date in task text order`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            TaskAssistRequest(title = "Follow up on June 16, 2026 and archive reference 2026-06-20")
        )

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.targetDate != null }
        assertEquals(java.time.LocalDate.of(2026, 6, 16), schedule.payload.targetDate)
    }

    @Test
    fun `local fallback extracts relative weekday target dates from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)
        val expectedDate = java.time.LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))

        val suggestions = planner.suggest(TaskAssistRequest(title = "Prepare notes for next Monday"))

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.targetDate != null }
        assertEquals(expectedDate, schedule.payload.targetDate)
    }

    @Test
    fun `local fallback extracts relative day offset target dates from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)
        val expectedDate = java.time.LocalDate.now().plusDays(3)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Send the summary in 3 days"))

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.targetDate != null }
        assertEquals(expectedDate, schedule.payload.targetDate)
    }

    @Test
    fun `local fallback extracts relative week offset target dates from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)
        val expectedDate = java.time.LocalDate.now().plusWeeks(2)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Send the summary in 2 weeks"))

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.targetDate != null }
        assertEquals(expectedDate, schedule.payload.targetDate)
    }

    @Test
    fun `local fallback extracts explicit start times from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review notes at 9:30 AM"))

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.preferredStartMinuteOfDay != null }
        assertEquals(9 * 60 + 30, schedule.payload.preferredStartMinuteOfDay)
        assertEquals("Start around 9:30", schedule.label)
        assertEquals(TaskAssistSource.LOCAL, schedule.source)
    }

    @Test
    fun `local fallback extracts explicit twenty four hour start times from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch notes at 18:30"))

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.preferredStartMinuteOfDay != null }
        assertEquals(18 * 60 + 30, schedule.payload.preferredStartMinuteOfDay)
        assertEquals("Start around 18:30", schedule.label)
        assertEquals(TaskAssistSource.LOCAL, schedule.source)
    }

    @Test
    fun `local fallback extracts natural language start times from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val noonSuggestions = planner.suggest(TaskAssistRequest(title = "Review launch notes at noon"))
        val midnightSuggestions = planner.suggest(TaskAssistRequest(title = "Archive logs by midnight"))

        assertEquals(
            12 * 60,
            noonSuggestions
                .filterIsInstance<TaskAssistSuggestion.Schedule>()
                .single { it.payload.preferredStartMinuteOfDay != null }
                .payload.preferredStartMinuteOfDay
        )
        assertEquals(
            0,
            midnightSuggestions
                .filterIsInstance<TaskAssistSuggestion.Schedule>()
                .single { it.payload.preferredStartMinuteOfDay != null }
                .payload.preferredStartMinuteOfDay
        )
    }

    @Test
    fun `local fallback extracts explicit durations from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Draft launch notes for 45 minutes"))

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.preferredDurationMinutes != null }
        assertEquals(45, schedule.payload.preferredDurationMinutes)
        assertEquals("45m block", schedule.label)
        assertEquals(TaskAssistSource.LOCAL, schedule.source)
    }

    @Test
    fun `local fallback adapts call capture into action schedule and checklist suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "call mom tomorrow"))

        val duration = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.preferredDurationMinutes != null }
        val checklist = suggestions.filterIsInstance<TaskAssistSuggestion.Checklist>().single()
        val action = suggestions.filterIsInstance<TaskAssistSuggestion.ActionDraft>().single()
        assertEquals(15, duration.payload.preferredDurationMinutes)
        assertEquals(listOf("Confirm the right contact", "Make the call", "Capture follow-up from Call mom"), checklist.items)
        assertEquals(TaskActionType.PHONE, action.payload.type)
        assertEquals("Call mom", action.payload.label)
    }

    @Test
    fun `local fallback adapts quick chore capture without marking today as urgent`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "water plants today"))

        val duration = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.preferredDurationMinutes != null }
        val checklist = suggestions.filterIsInstance<TaskAssistSuggestion.Checklist>().single()
        assertEquals(15, duration.payload.preferredDurationMinutes)
        assertEquals(listOf("Do the quick chore", "Reset anything needed afterward", "Mark complete"), checklist.items)
        assertEquals(emptyList<TaskAssistSuggestion.Priority>(), suggestions.filterIsInstance<TaskAssistSuggestion.Priority>())
    }

    @Test
    fun `local fallback extracts decimal hour durations from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Draft launch notes for 1.5 hours"))

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.preferredDurationMinutes != null }
        assertEquals(90, schedule.payload.preferredDurationMinutes)
        assertEquals("90m block", schedule.label)
        assertEquals("The task text names a duration.", schedule.reason)
    }

    @Test
    fun `local fallback keeps explicit duration reason when duration matches default`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Draft launch notes for 30 minutes"))

        val schedule = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .single { it.payload.preferredDurationMinutes != null }
        assertEquals(30, schedule.payload.preferredDurationMinutes)
        assertEquals("30m block", schedule.label)
        assertEquals("The task text names a duration.", schedule.reason)
    }

    @Test
    fun `local fallback extracts critical priority cues from task text`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Fix critical sync issue"))

        val priority = suggestions.filterIsInstance<TaskAssistSuggestion.Priority>().single()
        assertEquals(2, priority.priority)
        assertEquals("Mark urgent", priority.label)
        assertEquals(TaskAssistSource.LOCAL, priority.source)
    }

    @Test
    fun `suggest parses cloud Gemini suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "priority|Mark urgent|2|Deadline today",
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Ship release"))

        assertEquals(TaskAssistSource.CLOUD_GEMINI, suggestions.single().source)
    }

    @Test
    fun `suggest parses generated priority labels`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                priority|Mark urgent|high|Deadline today
                priority|Keep visible|low|Useful but not blocking
            """.trimIndent(),
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Ship release"))

        val priorities = suggestions
            .filterIsInstance<TaskAssistSuggestion.Priority>()
            .map { it.priority }
        assertEquals(listOf(2, 0), priorities)
    }

    @Test
    fun `suggest parses generated scalar values with trailing punctuation`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                schedule_date|Target launch day|2026-05-27.|The title names a launch window
                priority|Mark urgent|urgent.|Deadline today
            """.trimIndent(),
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Launch follow-up"))

        val schedule = suggestions.filterIsInstance<TaskAssistSuggestion.Schedule>().single()
        val priority = suggestions.filterIsInstance<TaskAssistSuggestion.Priority>().single()
        assertEquals(java.time.LocalDate.of(2026, 5, 27), schedule.payload.targetDate)
        assertEquals(2, priority.priority)
    }

    @Test
    fun `suggest parses target date suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "schedule_date|Target launch day|2026-05-27|The title names a launch window",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Launch follow-up"))

        val schedule = suggestions.single() as TaskAssistSuggestion.Schedule
        assertEquals(java.time.LocalDate.of(2026, 5, 27), schedule.payload.targetDate)
        assertEquals(TaskAssistSource.GEMINI_NANO, schedule.source)
    }

    @Test
    fun `suggest parses generated date suggestions with common formats`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                schedule_date|Target launch day|2026/05/27|The title names a launch window
                schedule_date|Follow up|May 28, 2026|Follow-up should happen the next day
            """.trimIndent(),
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Launch follow-up"))

        val dates = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .map { it.payload.targetDate }
        assertEquals(
            listOf(java.time.LocalDate.of(2026, 5, 27), java.time.LocalDate.of(2026, 5, 28)),
            dates
        )
    }

    @Test
    fun `suggest parses generated relative weekday date suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "schedule_date|Target review|next Monday|Review should happen after the weekend",
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)
        val expectedDate = java.time.LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch follow-up"))

        val schedule = suggestions.single() as TaskAssistSuggestion.Schedule
        assertEquals(expectedDate, schedule.payload.targetDate)
    }

    @Test
    fun `suggest parses generated relative day offset date suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "schedule_date|Target review|in 2 days|Review should happen soon",
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)
        val expectedDate = java.time.LocalDate.now().plusDays(2)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch follow-up"))

        val schedule = suggestions.single() as TaskAssistSuggestion.Schedule
        assertEquals(expectedDate, schedule.payload.targetDate)
    }

    @Test
    fun `suggest parses generated relative week offset date suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "schedule_date|Target review|in 1 week|Review should happen soon",
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)
        val expectedDate = java.time.LocalDate.now().plusWeeks(1)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch follow-up"))

        val schedule = suggestions.single() as TaskAssistSuggestion.Schedule
        assertEquals(expectedDate, schedule.payload.targetDate)
    }

    @Test
    fun `suggest parses generated clock time suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                schedule_time|Start after standup|18:30|The evening block is open
                schedule_time|Start after dinner|6:45 PM|The task works after dinner
            """.trimIndent(),
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch follow-up"))

        val startMinutes = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .map { it.payload.preferredStartMinuteOfDay }
        assertEquals(listOf(18 * 60 + 30, 18 * 60 + 45), startMinutes)
    }

    @Test
    fun `suggest parses generated natural language time suggestions`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                schedule_time|Start at lunch|noon|The task fits a midday block
                schedule_time|Run overnight|midnight|The task should start overnight
            """.trimIndent(),
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch follow-up"))

        val startMinutes = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .map { it.payload.preferredStartMinuteOfDay }
        assertEquals(listOf(12 * 60, 0), startMinutes)
    }

    @Test
    fun `suggest parses generated duration suggestions with units`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = """
                schedule_duration|Short review|45m|A short focused pass is enough
                schedule_duration|Deep pass|1h 30m|The task needs a longer block
            """.trimIndent(),
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = TaskAssistPlanner(coordinator)

        val suggestions = planner.suggest(TaskAssistRequest(title = "Review launch"))

        val durations = suggestions
            .filterIsInstance<TaskAssistSuggestion.Schedule>()
            .map { it.payload.preferredDurationMinutes }
        assertEquals(listOf(45, 90), durations)
    }
}
