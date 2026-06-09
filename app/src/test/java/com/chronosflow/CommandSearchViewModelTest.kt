package com.chronosflow

import com.chronosflow.core.ai.CommandAssistPlanner
import com.chronosflow.core.ai.SemanticAppSearchBridge
import com.chronosflow.core.ai.SemanticPlanningCorpusRefresher
import com.chronosflow.core.ai.SemanticPlanningIndex
import com.chronosflow.core.domain.model.FocusSession
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.core.ui.components.CommandPaletteItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandSearchViewModelTest {
    private val semanticIndex = SemanticPlanningIndex()
    private val commandAssistPlanner = CommandAssistPlanner(mockk(relaxed = true), mockk(relaxed = true))
    private val appSearchBridge: SemanticAppSearchBridge = mockk(relaxed = true)
    private val corpusRefresher: SemanticPlanningCorpusRefresher = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { corpusRefresher.refresh(null, any<Clock>()) } returns false
        coEvery { corpusRefresher.refresh(false, any<Clock>()) } returns false
        coEvery { appSearchBridge.syncDocuments() } returns false
        semanticIndex.rebuild(
            reviews = emptyList(),
            blocks = emptyList(),
            tasks = listOf(
                Task(
                    id = "task-1",
                    title = "Prepare launch brief",
                    description = "Write the assistant-first rollout summary",
                    isCompleted = false,
                    priority = 2,
                    dueDate = null,
                    createdAt = Instant.parse("2026-05-20T09:00:00Z"),
                    updatedAt = Instant.parse("2026-05-20T09:00:00Z")
                )
            ),
            habits = emptyList(),
            medications = emptyList(),
            focusSessions = emptyList<FocusSession>(),
            redactMedicationNames = false
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `semantic corpus refresh waits until palette opens`() = runTest {
        val viewModel = createViewModel()

        coVerify(exactly = 0) { corpusRefresher.refresh(null, any<Clock>()) }

        viewModel.dispatch(LauncherAction.OnOpen)

        coVerify(exactly = 1) { corpusRefresher.refresh(null, any<Clock>()) }
    }

    @Test
    fun `task semantic hits become runnable assistant commands`() = runTest {
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            corpusRefresher = corpusRefresher
        )
        viewModel.rebuildIndex(redactMedicationNames = false)

        var openedTasks = false
        val results = viewModel.searchCommands(
            query = "launch brief",
            actions = AssistantCommandActions(
                onOpenTasks = { openedTasks = true }
            )
        )

        assertFalse(results.isEmpty())
        assertEquals(CommandPaletteGroups.ASSISTANT, results.first().group)

        results.first().onRun()

        assertTrue(openedTasks)
    }

    @Test
    fun `launcher open and query actions update command ui state`() = runTest {
        val viewModel = createViewModel()
        val commands = listOf(
            command(id = "daydial.open", title = "Open today", group = CommandPaletteGroups.DAY),
            command(id = "tasks.open", title = "Search tasks", group = CommandPaletteGroups.SUPPORTING),
            command(id = "daydial.ai-settings", title = "Open settings", group = CommandPaletteGroups.SETTINGS)
        )

        viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)

        assertTrue(viewModel.uiState.value.isOpen)
        assertEquals(commands.map { it.id }, viewModel.uiState.value.results.map { it.id })

        viewModel.dispatch(LauncherAction.OnQueryChanged("task"), paletteCommands = commands)

        assertEquals("task", viewModel.uiState.value.query)
        assertEquals(listOf("tasks.open"), viewModel.uiState.value.results.map { it.id })
    }

    @Test
    fun `launcher query does not turn generic navigation search into capture command`() = runTest {
        val viewModel = createViewModel()
        val commands = listOf(command(id = "daydial.ai-settings", title = "Open settings"))

        viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)
        viewModel.dispatch(LauncherAction.OnQueryChanged("settings"), paletteCommands = commands)

        assertEquals(listOf("daydial.ai-settings"), viewModel.uiState.value.results.map { it.id })
    }

    @Test
    fun `launcher query offers create command from medication capture`() = runTest {
        val viewModel = createViewModel()
        var capturedMedication = ""
        val actions = AssistantCommandActions(
            onCaptureMedication = { capturedMedication = it }
        )

        viewModel.dispatch(LauncherAction.OnOpen, actions = actions)
        viewModel.dispatch(
            LauncherAction.OnQueryChanged("vitamin d 1000 iu morning"),
            actions = actions
        )

        assertEquals("capture.create.medication", viewModel.uiState.value.results.first().id)
        assertTrue(viewModel.uiState.value.results.first().subtitle.contains("med"))
        assertTrue(viewModel.uiState.value.results.first().subtitle.contains("supplement name, amount, timing"))

        viewModel.dispatch(
            LauncherAction.OnExecute("capture.create.medication"),
            actions = actions
        )

        assertEquals("vitamin d 1000 iu morning", capturedMedication)
    }

    @Test
    fun `launcher query offers create command from task capture`() = runTest {
        val viewModel = createViewModel()
        var capturedTask = ""
        val actions = AssistantCommandActions(
            onCaptureTask = { capturedTask = it }
        )

        viewModel.dispatch(LauncherAction.OnOpen, actions = actions)
        viewModel.dispatch(LauncherAction.OnQueryChanged("call mom tomorrow"), actions = actions)

        assertEquals("capture.create.task", viewModel.uiState.value.results.first().id)
        assertTrue(viewModel.uiState.value.results.first().subtitle.contains("task"))
        assertTrue(viewModel.uiState.value.results.first().subtitle.contains("phone action, due-time hint"))

        viewModel.dispatch(LauncherAction.OnExecute("capture.create.task"), actions = actions)

        assertEquals("call mom tomorrow", capturedTask)
    }

    @Test
    fun `launcher capture command title previews long input but executes full text`() = runTest {
        val viewModel = createViewModel()
        val longCapture = "call mom tomorrow about the full medication list and ask her to send the appointment details"
        var capturedTask = ""
        val actions = AssistantCommandActions(
            onCaptureTask = { capturedTask = it }
        )

        viewModel.dispatch(LauncherAction.OnOpen, actions = actions)
        viewModel.dispatch(LauncherAction.OnQueryChanged(longCapture), actions = actions)

        val createCommand = viewModel.uiState.value.results.first { it.id == "capture.create.task" }
        assertTrue(createCommand.title.endsWith("...\""))
        assertTrue(createCommand.title.length < longCapture.length)

        viewModel.dispatch(LauncherAction.OnExecute("capture.create.task"), actions = actions)

        assertEquals(longCapture, capturedTask)
    }

    @Test
    fun `launcher query offers create command from habit capture`() = runTest {
        val viewModel = createViewModel()
        var capturedHabit = ""
        val actions = AssistantCommandActions(
            onCaptureHabit = { capturedHabit = it }
        )

        viewModel.dispatch(LauncherAction.OnOpen, actions = actions)
        viewModel.dispatch(LauncherAction.OnQueryChanged("gym 3x week evening"), actions = actions)

        assertEquals("capture.create.habit", viewModel.uiState.value.results.first().id)
        assertTrue(viewModel.uiState.value.results.first().subtitle.contains("habit"))
        assertTrue(viewModel.uiState.value.results.first().subtitle.contains("workout cadence, window, effort"))

        viewModel.dispatch(LauncherAction.OnExecute("capture.create.habit"), actions = actions)

        assertEquals("gym 3x week evening", capturedHabit)
    }

    @Test
    fun `launcher query offers create command from focus capture`() = runTest {
        val viewModel = createViewModel()
        var capturedFocus = ""
        val actions = AssistantCommandActions(
            onCaptureFocus = { capturedFocus = it }
        )

        viewModel.dispatch(LauncherAction.OnOpen, actions = actions)
        viewModel.dispatch(LauncherAction.OnQueryChanged("focus 45 minutes on launch brief"), actions = actions)

        val createCommand = viewModel.uiState.value.results.first()
        assertEquals("capture.create.focus", createCommand.id)
        assertTrue(createCommand.subtitle.contains("focus session"))
        assertTrue(createCommand.subtitle.contains("protected block"))

        viewModel.dispatch(LauncherAction.OnExecute("capture.create.focus"), actions = actions)

        assertEquals("focus 45 minutes on launch brief", capturedFocus)
    }

    @Test
    fun `quick capture runs the same create commands without opening palette`() = runTest {
        val viewModel = createViewModel()
        val captures = mutableListOf<String>()
        val actions = AssistantCommandActions(
            onCaptureTask = { captures += "task:$it" },
            onCaptureMedication = { captures += "medication:$it" },
            onCaptureHabit = { captures += "habit:$it" },
            onCaptureFocus = { captures += "focus:$it" }
        )

        assertTrue(viewModel.runBestCaptureCommand("call mom tomorrow", actions))
        assertTrue(viewModel.runBestCaptureCommand("vitamin d 1000 iu morning", actions))
        assertTrue(viewModel.runBestCaptureCommand("gym 3x week evening", actions))
        assertTrue(viewModel.runBestCaptureCommand("focus 45 minutes on launch brief", actions))

        assertEquals(
            listOf(
                "task:call mom tomorrow",
                "medication:vitamin d 1000 iu morning",
                "habit:gym 3x week evening",
                "focus:focus 45 minutes on launch brief"
            ),
            captures
        )
        assertEquals(CommandUiState(), viewModel.uiState.value)
    }

    @Test
    fun `quick capture preview exposes the inferred create kind`() = runTest {
        val viewModel = createViewModel()

        assertEquals("task", viewModel.previewBestCaptureCommand("call mom tomorrow")?.label)
        assertEquals("med", viewModel.previewBestCaptureCommand("vitamin d 1000 iu morning")?.label)
        assertEquals("habit", viewModel.previewBestCaptureCommand("gym 3x week evening")?.label)
        assertEquals("focus", viewModel.previewBestCaptureCommand("focus 45 minutes on launch brief")?.label)
        assertEquals(null, viewModel.previewBestCaptureCommand("task"))
    }

    @Test
    fun `quick capture preview and submit respect disabled habit and medication features`() = runTest {
        val viewModel = createViewModel()
        val actions = AssistantCommandActions(
            habitsEnabled = false,
            medicationEnabled = false
        )

        assertEquals(null, viewModel.previewBestCaptureCommand("vitamin d 1000 iu morning", actions))
        assertEquals(null, viewModel.previewBestCaptureCommand("gym 3x week evening", actions))
        assertEquals("task", viewModel.previewBestCaptureCommand("call mom tomorrow", actions)?.label)

        assertFalse(viewModel.runBestCaptureCommand("vitamin d 1000 iu morning", actions))
        assertFalse(viewModel.runBestCaptureCommand("gym 3x week evening", actions))
        assertTrue(viewModel.runBestCaptureCommand("call mom tomorrow", actions))
    }

    @Test
    fun `quick capture ignores generic command category text`() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.runBestCaptureCommand("task"))
        assertEquals(CommandUiState(), viewModel.uiState.value)
    }

    @Test
    fun `launcher capture commands respect disabled habit and medication features`() = runTest {
        val viewModel = createViewModel()
        val actions = AssistantCommandActions(
            habitsEnabled = false,
            medicationEnabled = false
        )

        viewModel.dispatch(LauncherAction.OnOpen, actions = actions)
        viewModel.dispatch(LauncherAction.OnQueryChanged("vitamin d 1000 iu morning"), actions = actions)

        assertTrue(viewModel.uiState.value.results.none { it.id == "capture.create.medication" })

        viewModel.dispatch(LauncherAction.OnQueryChanged("gym 3x week evening"), actions = actions)

        assertTrue(viewModel.uiState.value.results.none { it.id == "capture.create.habit" })
    }

    @Test
    fun `launcher section action filters visible results`() = runTest {
        val viewModel = createViewModel()
        val commands = listOf(
            command(id = "daydial.open", title = "Open today", group = CommandPaletteGroups.DAY),
            command(id = "daydial.plan", title = "Open plan", group = CommandPaletteGroups.PLAN),
            command(id = "daydial.ai-settings", title = "Open settings", group = CommandPaletteGroups.SETTINGS)
        )

        viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)
        viewModel.dispatch(
            LauncherAction.OnSectionSelected(CommandPaletteGroups.SETTINGS),
            paletteCommands = commands
        )

        assertEquals(CommandPaletteGroups.SETTINGS, viewModel.uiState.value.selectedSection)
        assertEquals(listOf("daydial.ai-settings"), viewModel.uiState.value.results.map { it.id })
    }

    @Test
    fun `launcher execute action runs command and closes palette`() = runTest {
        val viewModel = createViewModel()
        var executed = false
        val commands = listOf(
            command(id = "daydial.open", title = "Open today") { executed = true }
        )

        viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)
        viewModel.dispatch(LauncherAction.OnExecute("daydial.open"), paletteCommands = commands)

        assertTrue(executed)
        assertEquals(CommandUiState(), viewModel.uiState.value)
    }

    @Test
    fun `prebuilt command ids execute their configured callbacks`() = runTest {
        val viewModel = createViewModel()
        val executed = mutableListOf<String>()
        val commands = listOf(
            command(id = "daydial.open", title = "Open today") { executed += "day" },
            command(id = "daydial.plan", title = "Open plan") { executed += "plan" },
            command(id = "focus.open", title = "Open focus") { executed += "focus" },
            command(id = "tasks.open", title = "Search tasks") { executed += "tasks" },
            command(id = "daydial.ai-settings", title = "Open settings") { executed += "settings" }
        )

        commands.forEach { command ->
            viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)
            viewModel.dispatch(LauncherAction.OnExecute(command.id), paletteCommands = commands)
        }

        assertEquals(listOf("day", "plan", "focus", "tasks", "settings"), executed)
    }

    @Test
    fun `launcher close and clear actions have explicit transitions`() = runTest {
        val viewModel = createViewModel()
        val commands = listOf(command(id = "tasks.open", title = "Search tasks"))

        viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)
        viewModel.dispatch(LauncherAction.OnQueryChanged("task"), paletteCommands = commands)
        viewModel.dispatch(LauncherAction.OnClearHistory, paletteCommands = commands)

        assertEquals("", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.isOpen)
        assertEquals(listOf("tasks.open"), viewModel.uiState.value.results.map { it.id })

        viewModel.dispatch(LauncherAction.OnClose, paletteCommands = commands)

        assertEquals(CommandUiState(), viewModel.uiState.value)
    }

    @Test
    fun `launcher action interface covers palette interactions`() {
        val actions: List<LauncherAction> = listOf(
            LauncherAction.OnOpen,
            LauncherAction.OnQueryChanged("plan"),
            LauncherAction.OnSectionSelected(CommandPaletteGroups.PLAN),
            LauncherAction.OnExecute("daydial.plan"),
            LauncherAction.OnClearHistory,
            LauncherAction.OnClose
        )

        assertEquals(6, actions.size)
    }

    private fun createViewModel(): CommandSearchViewModel = CommandSearchViewModel(
        semanticIndex = semanticIndex,
        appSearchBridge = appSearchBridge,
        commandAssistPlanner = commandAssistPlanner,
        corpusRefresher = corpusRefresher
    )

    private fun command(
        id: String,
        title: String,
        group: String = CommandPaletteGroups.OTHER,
        onRun: () -> Unit = {}
    ): CommandPaletteItem = CommandPaletteItem(
        id = id,
        title = title,
        subtitle = title,
        keywords = setOf(title),
        group = group,
        onRun = onRun
    )
}
