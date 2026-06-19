package com.ChronosFlow.VBCR

import com.ChronosFlow.VBCR.core.ai.CaptureIntentType
import com.ChronosFlow.VBCR.core.ai.CommandAssistPlanner
import com.ChronosFlow.VBCR.core.ai.ConversationalAssistant
import com.ChronosFlow.VBCR.core.ai.ProactiveAssistContent
import com.ChronosFlow.VBCR.core.ai.ProactiveAssistGenerator
import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.ai.SemanticAppSearchBridge
import com.ChronosFlow.VBCR.core.ai.SemanticPlanningCorpusRefresher
import com.ChronosFlow.VBCR.core.ai.SemanticPlanningIndex
import com.ChronosFlow.VBCR.core.domain.model.FocusSession
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteGroups
import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
    private val proactiveAssistGenerator: ProactiveAssistGenerator = mockk {
        every { cachedCopy(any(), any(), any()) } returns null
    }
    private val preferenceValues = mutableMapOf<String, String>()
    private val preferences: ChronosPreferencesDataSource = mockk {
        every { getString(any(), any()) } answers { preferenceValues[firstArg()] ?: secondArg() }
        every { putString(any(), any()) } answers { preferenceValues[firstArg()] = secondArg() }
    }

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
    fun `semantic corpus refresh waits until palette opens`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        coVerify(exactly = 0) { corpusRefresher.refresh(null, any<Clock>()) }

        viewModel.dispatch(LauncherAction.OnOpen)

        coVerify(exactly = 1) { corpusRefresher.refresh(null, any<Clock>()) }
    }

    @Test
    fun `task semantic hits become runnable assistant commands`() = runTest(testDispatcher) {
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            conversationalAssistant = mockk(relaxed = true),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
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
    fun `launcher open and query actions update command ui state`() = runTest(testDispatcher) {
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
    fun `launcher query does not turn generic navigation search into capture command`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val commands = listOf(command(id = "daydial.ai-settings", title = "Open settings"))

        viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)
        viewModel.dispatch(LauncherAction.OnQueryChanged("settings"), paletteCommands = commands)

        assertEquals(listOf("daydial.ai-settings"), viewModel.uiState.value.results.map { it.id })
    }

    @Test
    fun `launcher query offers create command from medication capture`() = runTest(testDispatcher) {
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
    fun `launcher query offers create command from task capture`() = runTest(testDispatcher) {
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
    fun `launcher capture command title previews long input but executes full text`() = runTest(testDispatcher) {
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
    fun `launcher query offers create command from habit capture`() = runTest(testDispatcher) {
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
    fun `launcher query offers create command from focus capture`() = runTest(testDispatcher) {
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
    fun `quick capture runs the same create commands without opening palette`() = runTest(testDispatcher) {
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
    fun `palette open surfaces the cached daily digest`() = runTest(testDispatcher) {
        every { proactiveAssistGenerator.cachedCopy(any(), any(), any()) } returns ProactiveAssistContent(
            text = "2 block(s) done; 3 task(s) still open for today.",
            source = AssistGenAiSource.GEMINI_NANO,
            generatedAtEpochMs = 0L,
            forDateIso = "2026-06-10"
        )
        val viewModel = createViewModel()
        var openedReview = false
        val actions = AssistantCommandActions(onOpenReview = { openedReview = true })

        viewModel.dispatch(LauncherAction.OnOpen, actions = actions)

        val digest = viewModel.uiState.value.results.first { it.id == ASSISTANT_DIGEST_COMMAND_ID }
        assertEquals("Today at a glance", digest.title)
        assertEquals("2 block(s) done; 3 task(s) still open for today.", digest.subtitle)

        viewModel.dispatch(LauncherAction.OnExecute(digest.id), actions = actions)

        assertTrue(openedReview)
    }

    @Test
    fun `palette open shows no digest row without a fresh cached copy`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(LauncherAction.OnOpen)

        assertTrue(viewModel.uiState.value.results.none { it.id == ASSISTANT_DIGEST_COMMAND_ID })
    }

    @Test
    fun `question queries surface an ask row that converses in place`() = runTest(testDispatcher) {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        every { coordinator.generateAssistTextStream(any(), any(), any()) } returns flowOf("You have room after lunch.")
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            conversationalAssistant = ConversationalAssistant(coordinator, commandAssistPlanner),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
        )
        val commands = listOf(command(id = "tasks.open", title = "Open tasks"))

        viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)
        viewModel.dispatch(LauncherAction.OnQueryChanged("what should i do next?"), paletteCommands = commands)

        val ask = viewModel.uiState.value.results.first { it.id == ASSISTANT_ASK_COMMAND_ID }

        viewModel.dispatch(LauncherAction.OnExecute(ask.id), paletteCommands = commands)

        assertTrue(viewModel.uiState.value.isOpen)
        assertEquals("what should i do next?", viewModel.assistantPanel.value.reply?.question)
        assertEquals("You have room after lunch.", viewModel.assistantPanel.value.reply?.reply)
    }

    @Test
    fun `question detection matches questions but not captures`() {
        assertTrue(isAssistantQuestionQuery("what should I do next"))
        assertTrue(isAssistantQuestionQuery("plan my morning"))
        assertTrue(isAssistantQuestionQuery("is my afternoon free?"))
        assertFalse(isAssistantQuestionQuery("call mom tomorrow"))
        assertFalse(isAssistantQuestionQuery("vitamin d 1000 iu morning"))
        assertFalse(isAssistantQuestionQuery("gym?"))
    }

    @Test
    fun `capture subtitle leads with confidence phrasing and prefill markers`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val actions = AssistantCommandActions(onCaptureMedication = {})

        viewModel.dispatch(LauncherAction.OnOpen, actions = actions)
        viewModel.dispatch(
            LauncherAction.OnQueryChanged("vitamin d 1000 iu morning"),
            actions = actions
        )

        val subtitle = viewModel.uiState.value.results.first { it.id == "capture.create.medication" }.subtitle
        assertTrue(subtitle.startsWith("Looks like") || subtitle.startsWith("Could be"))
        assertTrue(subtitle.contains("Prefills 1000 iu · morning."))
    }

    @Test
    fun `capture prefill preview extracts concrete markers per type`() {
        assertEquals(
            "Prefills tomorrow · 2pm · 45m.",
            capturePrefillPreview(CaptureIntentType.TASK, "call mom tomorrow at 2pm for 45 minutes")
        )
        assertEquals(
            "Prefills 1000 iu · morning.",
            capturePrefillPreview(CaptureIntentType.MEDICATION, "vitamin d 1000 iu morning")
        )
        assertEquals(
            "Prefills 3x week · evening.",
            capturePrefillPreview(CaptureIntentType.HABIT, "gym 3x week evening")
        )
        assertEquals(
            "Prefills 45m.",
            capturePrefillPreview(CaptureIntentType.FOCUS, "focus 45 minutes on launch brief")
        )
        assertEquals(null, capturePrefillPreview(CaptureIntentType.TASK, "review launch brief"))
    }

    @Test
    fun `searchCommandsAsync boosts the AI-confirmed capture command`() = runTest(testDispatcher) {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = "capture.create.medication",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = CommandAssistPlanner(coordinator, mockk(relaxed = true))
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = planner,
            conversationalAssistant = mockk(relaxed = true),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
        )
        val actions = AssistantCommandActions(onCaptureMedication = {})
        var results: List<CommandPaletteItem> = emptyList()

        viewModel.searchCommandsAsync(
            query = "vitamin d 1000 iu morning",
            actions = actions,
            paletteCommands = listOf(command(id = "medication.open", title = "Open medication"))
        ) { results = it }

        val capture = results.first { it.id == "capture.create.medication" }
        assertEquals(320, capture.priority)
    }

    @Test
    fun `quick capture preview exposes the inferred create kind`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        assertEquals("task", viewModel.previewBestCaptureCommand("call mom tomorrow")?.label)
        assertEquals("med", viewModel.previewBestCaptureCommand("vitamin d 1000 iu morning")?.label)
        assertEquals("habit", viewModel.previewBestCaptureCommand("gym 3x week evening")?.label)
        assertEquals("focus", viewModel.previewBestCaptureCommand("focus 45 minutes on launch brief")?.label)
        assertEquals(null, viewModel.previewBestCaptureCommand("task"))
    }

    @Test
    fun `quick capture preview and submit respect disabled habit and medication features`() = runTest(testDispatcher) {
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
    fun `quick capture ignores generic command category text`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        assertFalse(viewModel.runBestCaptureCommand("task"))
        assertEquals(CommandUiState(), viewModel.uiState.value)
    }

    @Test
    fun `launcher capture commands respect disabled habit and medication features`() = runTest(testDispatcher) {
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
    fun `launcher section action filters visible results`() = runTest(testDispatcher) {
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
    fun `launcher execute action runs command and closes palette`() = runTest(testDispatcher) {
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
    fun `prebuilt command ids execute their configured callbacks`() = runTest(testDispatcher) {
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
    fun `launcher close and clear actions have explicit transitions`() = runTest(testDispatcher) {
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

    @Test
    fun `askAssistant resolves a proposed action to a runnable command`() = runTest(testDispatcher) {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        every { coordinator.generateAssistTextStream(any(), any(), any()) } returns flowOf(
            "Starting",
            "Starting a focus session.\nACTION: focus.start"
        )
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            conversationalAssistant = ConversationalAssistant(coordinator, commandAssistPlanner),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
        )
        val commands = listOf(
            command(id = "focus.start", title = "Start focus session"),
            command(id = "task.add", title = "Add task")
        )

        viewModel.askAssistant("help me focus", commands)

        val panel = viewModel.assistantPanel.value
        assertEquals("focus.start", panel.reply?.proposedCommand?.id)
        assertEquals("help me focus", panel.reply?.question)
        assertFalse(panel.reply?.reply?.contains("ACTION:") ?: true)
        assertFalse(panel.isAsking)
        assertEquals(null, panel.streamingReply)
    }

    @Test
    fun `askAssistant falls back to the non-streaming path when the stream is empty`() = runTest(testDispatcher) {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        every { coordinator.generateAssistTextStream(any(), any(), any()) } returns emptyFlow()
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = "Starting a focus session.\nACTION: focus.start",
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            conversationalAssistant = ConversationalAssistant(coordinator, commandAssistPlanner),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
        )
        val commands = listOf(command(id = "focus.start", title = "Start focus session"))

        viewModel.askAssistant("help me focus", commands)

        val panel = viewModel.assistantPanel.value
        assertEquals("focus.start", panel.reply?.proposedCommand?.id)
        assertEquals(AssistGenAiSource.CLOUD_GEMINI, panel.reply?.source)
    }

    @Test
    fun `askAssistant keeps conversation history across follow-up questions`() = runTest(testDispatcher) {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        val prompts = mutableListOf<String>()
        every { coordinator.generateAssistTextStream(capture(prompts), any(), any()) } answers {
            flowOf("Sure — a focus session helps.\nACTION: focus.start")
        }
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            conversationalAssistant = ConversationalAssistant(coordinator, commandAssistPlanner),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
        )
        val commands = listOf(command(id = "focus.start", title = "Start focus session"))

        viewModel.askAssistant("help me focus", commands)
        assertEquals(2, viewModel.assistantPanel.value.history.size)

        viewModel.askAssistant("make it 25 minutes", commands)

        assertEquals(4, viewModel.assistantPanel.value.history.size)
        assertTrue(prompts.last().contains("help me focus"))
        assertTrue(prompts.last().contains("Sure — a focus session helps."))
    }

    @Test
    fun `askAssistant proposes a quick-capture create command for capture-shaped queries`() = runTest(testDispatcher) {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        every { coordinator.generateAssistTextStream(any(), any(), any()) } returns flowOf(
            "I can draft that task for you.\nACTION: capture.create.task"
        )
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            conversationalAssistant = ConversationalAssistant(coordinator, commandAssistPlanner),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
        )
        var capturedTask: String? = null
        val actions = AssistantCommandActions(onCaptureTask = { capturedTask = it })

        viewModel.askAssistant(
            query = "add task call mom tomorrow",
            paletteCommands = listOf(command(id = "tasks.open", title = "Open tasks")),
            actions = actions
        )

        val proposed = viewModel.assistantPanel.value.reply?.proposedCommand
        assertEquals("capture.create.task", proposed?.id)

        viewModel.runAssistantProposal(proposed!!)

        assertEquals("add task call mom tomorrow", capturedTask)
        assertEquals(AssistantPanelState(), viewModel.assistantPanel.value)
        assertEquals(CommandUiState(), viewModel.uiState.value)
    }

    @Test
    fun `askAssistant rebuilds capture proposals from the conversation payload`() = runTest(testDispatcher) {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        every { coordinator.generateAssistTextStream(any(), any(), any()) } returns flowOf(
            "I'll fold that in.\nACTION: capture.create.task | call mom tomorrow at 2pm"
        )
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            conversationalAssistant = ConversationalAssistant(coordinator, commandAssistPlanner),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
        )
        var capturedTask: String? = null
        val actions = AssistantCommandActions(onCaptureTask = { capturedTask = it })

        // The latest query alone ("make it tomorrow at 2pm") would capture the wrong text; the
        // payload composed from the whole conversation should win.
        viewModel.askAssistant(
            query = "make it tomorrow at 2pm",
            paletteCommands = listOf(command(id = "tasks.open", title = "Open tasks")),
            actions = actions
        )

        val proposed = viewModel.assistantPanel.value.reply?.proposedCommand
        assertEquals("capture.create.task", proposed?.id)
        assertTrue(proposed?.title.orEmpty().contains("call mom tomorrow at 2pm"))

        viewModel.runAssistantProposal(proposed!!)

        assertEquals("call mom tomorrow at 2pm", capturedTask)
    }

    @Test
    fun `askAssistant resolves multiple proposals into runnable commands`() = runTest(testDispatcher) {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        every { coordinator.generateAssistTextStream(any(), any(), any()) } returns flowOf(
            "Two good options.\nACTION: focus.start\nACTION: tasks.open"
        )
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            conversationalAssistant = ConversationalAssistant(coordinator, commandAssistPlanner),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
        )
        var openedTasks = false
        val commands = listOf(
            command(id = "focus.start", title = "Start focus session"),
            command(id = "tasks.open", title = "Open tasks", onRun = { openedTasks = true })
        )

        viewModel.askAssistant("what should i do next?", commands)

        val proposed = viewModel.assistantPanel.value.reply?.proposedCommands.orEmpty()
        assertEquals(listOf("focus.start", "tasks.open"), proposed.map { it.id })

        viewModel.runAssistantProposal(proposed[1])

        assertTrue(openedTasks)
    }

    @Test
    fun `recently executed commands are boosted when the palette reopens`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val commands = listOf(
            command(id = "daydial.open", title = "Open today", group = CommandPaletteGroups.DAY),
            command(id = "tasks.open", title = "Search tasks", group = CommandPaletteGroups.DAY)
        )

        viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)
        viewModel.dispatch(LauncherAction.OnExecute("tasks.open"), paletteCommands = commands)
        viewModel.dispatch(LauncherAction.OnOpen, paletteCommands = commands)

        val results = viewModel.uiState.value.results
        val boosted = results.first { it.id == "tasks.open" }
        val plain = results.first { it.id == "daydial.open" }
        assertTrue(boosted.priority > plain.priority)
        assertEquals(200, boosted.priority)
    }

    @Test
    fun `per-query rows are never recorded as recent commands`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        var captured = ""
        val actions = AssistantCommandActions(onCaptureTask = { captured = it })

        viewModel.dispatch(LauncherAction.OnOpen, actions = actions)
        viewModel.dispatch(LauncherAction.OnQueryChanged("call mom tomorrow"), actions = actions)
        viewModel.dispatch(LauncherAction.OnExecute("capture.create.task"), actions = actions)

        assertEquals("call mom tomorrow", captured)
        assertEquals(null, preferenceValues["command_palette_recent_ids"])
    }

    @Test
    fun `clearAssistant resets the conversation`() = runTest(testDispatcher) {
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        every { coordinator.generateAssistTextStream(any(), any(), any()) } returns flowOf("Hello there.")
        val viewModel = CommandSearchViewModel(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            commandAssistPlanner = commandAssistPlanner,
            conversationalAssistant = ConversationalAssistant(coordinator, commandAssistPlanner),
            corpusRefresher = corpusRefresher,
            proactiveAssistGenerator = proactiveAssistGenerator,
            preferences = preferences
        )

        viewModel.askAssistant("hi", listOf(command(id = "focus.start", title = "Start focus session")))
        viewModel.clearAssistant()

        assertEquals(AssistantPanelState(), viewModel.assistantPanel.value)
    }

    private fun createViewModel(): CommandSearchViewModel = CommandSearchViewModel(
        semanticIndex = semanticIndex,
        appSearchBridge = appSearchBridge,
        commandAssistPlanner = commandAssistPlanner,
        conversationalAssistant = mockk(relaxed = true),
        corpusRefresher = corpusRefresher,
        proactiveAssistGenerator = proactiveAssistGenerator,
        preferences = preferences
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
