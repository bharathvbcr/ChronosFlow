package com.chronosflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronosflow.core.ai.CommandAssistCandidate
import com.chronosflow.core.ai.CommandAssistPlanner
import com.chronosflow.core.ai.CaptureIntentClassifier
import com.chronosflow.core.ai.CaptureIntentSuggestion
import com.chronosflow.core.ai.CaptureIntentType
import com.chronosflow.core.ai.SemanticAppSearchBridge
import com.chronosflow.core.ai.SemanticDocumentType
import com.chronosflow.core.ai.SemanticPlanningCorpusRefresher
import com.chronosflow.core.ai.SemanticPlanningIndex
import com.chronosflow.core.ai.SemanticSearchHit
import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.core.ui.components.CommandPaletteItem
import com.chronosflow.core.ui.components.filterCommandPaletteItems
import com.chronosflow.core.ui.components.mergeCommandPaletteResults
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantCommandActions(
    val onOpenDay: () -> Unit = {},
    val onOpenTasks: () -> Unit = {},
    val onOpenReview: () -> Unit = {},
    val onOpenFocus: () -> Unit = {},
    val onOpenHabits: () -> Unit = {},
    val onOpenMedication: () -> Unit = {},
    val onCaptureTask: (String) -> Unit = {},
    val onCaptureHabit: (String) -> Unit = {},
    val onCaptureMedication: (String) -> Unit = {},
    val onCaptureFocus: (String) -> Unit = {},
    val habitsEnabled: Boolean = true,
    val medicationEnabled: Boolean = true
)

data class CommandUiState(
    val query: String = "",
    val isOpen: Boolean = false,
    val selectedSection: String? = null,
    val maxResults: Int = 12,
    val results: List<CommandPaletteItem> = emptyList(),
    val isExecuting: Boolean = false
)

data class QuickCaptureCommandPreview(
    val label: String,
    val title: String,
    val subtitle: String
)

sealed interface LauncherAction {
    data class OnQueryChanged(val text: String) : LauncherAction
    data class OnExecute(val commandId: String) : LauncherAction
    data class OnSectionSelected(val section: String?) : LauncherAction
    data object OnOpen : LauncherAction
    data object OnClose : LauncherAction
    data object OnClearHistory : LauncherAction
}

@HiltViewModel
class CommandSearchViewModel @Inject constructor(
    private val semanticIndex: SemanticPlanningIndex,
    private val appSearchBridge: SemanticAppSearchBridge,
    private val commandAssistPlanner: CommandAssistPlanner,
    private val corpusRefresher: SemanticPlanningCorpusRefresher
) : ViewModel() {
    private val _indexReady = MutableStateFlow(false)
    val indexReady = _indexReady.asStateFlow()
    private val _appSearchEnabled = MutableStateFlow(false)
    val appSearchEnabled = _appSearchEnabled.asStateFlow()
    private val _uiState = MutableStateFlow(CommandUiState())
    val uiState = _uiState.asStateFlow()
    private var indexRefreshRequested = false

    suspend fun rebuildIndex(redactMedicationNames: Boolean? = null) {
        _appSearchEnabled.value = corpusRefresher.refresh(
            redactMedicationNames = redactMedicationNames,
            clock = Clock.systemDefaultZone()
        )
        _indexReady.value = true
    }

    fun searchCommands(
        query: String,
        actions: AssistantCommandActions = AssistantCommandActions(),
        paletteCommands: List<CommandPaletteItem> = emptyList()
    ): List<CommandPaletteItem> {
        if (query.length < 3) return emptyList()
        val semantic = semanticIndex.query(query, semanticAvailable = _appSearchEnabled.value)
            .map { it.toCommandItem(actions) }
        val boosted = boostedPaletteCommands(
            query = query,
            paletteCommands = paletteCommands,
            boostPriority = 180
        )
        return mergeCommandPaletteResults(boosted, semantic)
    }

    fun previewBestCaptureCommand(
        query: String,
        actions: AssistantCommandActions = AssistantCommandActions()
    ): QuickCaptureCommandPreview? = previewQuickCaptureCommand(query, actions)

    fun runBestCaptureCommand(
        query: String,
        actions: AssistantCommandActions = AssistantCommandActions()
    ): Boolean {
        val commandRan = runQuickCaptureCommand(query, actions)
        if (commandRan) {
            _uiState.value = CommandUiState()
        }
        return commandRan
    }

    fun dispatch(
        action: LauncherAction,
        actions: AssistantCommandActions = AssistantCommandActions(),
        paletteCommands: List<CommandPaletteItem> = emptyList()
    ) {
        when (action) {
            LauncherAction.OnOpen -> {
                requestIndexRefresh()
                _uiState.update {
                    it.copy(
                        isOpen = true,
                        isExecuting = false,
                        results = commandResultsForState(
                            query = it.query,
                            actions = actions,
                            paletteCommands = paletteCommands,
                            selectedSection = it.selectedSection,
                            maxResults = it.maxResults
                        )
                    )
                }
            }
            LauncherAction.OnClose -> {
                _uiState.value = CommandUiState()
            }
            LauncherAction.OnClearHistory -> {
                _uiState.update {
                    it.copy(
                        query = "",
                        results = commandResultsForState(
                            query = "",
                            actions = actions,
                            paletteCommands = paletteCommands,
                            selectedSection = it.selectedSection,
                            maxResults = it.maxResults
                        )
                    )
                }
            }
            is LauncherAction.OnSectionSelected -> {
                _uiState.update {
                    it.copy(
                        selectedSection = action.section,
                        results = commandResultsForState(
                            query = it.query,
                            actions = actions,
                            paletteCommands = paletteCommands,
                            selectedSection = action.section,
                            maxResults = it.maxResults
                        )
                    )
                }
            }
            is LauncherAction.OnQueryChanged -> {
                requestIndexRefresh()
                _uiState.update {
                    it.copy(
                        query = action.text,
                        results = commandResultsForState(
                            query = action.text,
                            actions = actions,
                            paletteCommands = paletteCommands,
                            selectedSection = it.selectedSection,
                            maxResults = it.maxResults
                        )
                    )
                }
                refreshAsyncCommandResults(action.text, actions, paletteCommands)
            }
            is LauncherAction.OnExecute -> {
                val command = _uiState.value.results.firstOrNull { it.id == action.commandId }
                    ?: paletteCommands.firstOrNull { it.id == action.commandId }
                    ?: return
                _uiState.update { it.copy(isExecuting = true) }
                command.onRun()
                _uiState.value = CommandUiState()
            }
        }
    }

    private fun requestIndexRefresh() {
        if (indexRefreshRequested || _indexReady.value) return
        indexRefreshRequested = true
        viewModelScope.launch {
            rebuildIndex()
        }
    }

    fun searchCommandsAsync(
        query: String,
        actions: AssistantCommandActions = AssistantCommandActions(),
        paletteCommands: List<CommandPaletteItem> = emptyList(),
        onResults: (List<CommandPaletteItem>) -> Unit
    ) {
        if (query.length < 3) {
            onResults(emptyList())
            return
        }
        viewModelScope.launch {
            val hits = if (_appSearchEnabled.value) {
                appSearchBridge.query(query, semanticAvailable = true)
            } else {
                semanticIndex.query(query)
            }
            val semantic = hits.map { it.toCommandItem(actions) }
            val assistIds = commandAssistPlanner.rankCommandIdsWithAssist(
                query = query,
                candidates = paletteCommands.map { it.toAssistCandidate() }
            )
            val boosted = paletteCommands
                .filter { it.id in assistIds }
                .map { command ->
                    val rank = assistIds.indexOf(command.id)
                    command.withBoostedPriority(220 - rank.coerceAtLeast(0))
                }
            val localBoosted = boostedPaletteCommands(
                query = query,
                paletteCommands = paletteCommands,
                boostPriority = 160,
                excludeIds = assistIds.toSet()
            )
            onResults(mergeCommandPaletteResults(boosted + localBoosted, semantic))
        }
    }

    private fun refreshAsyncCommandResults(
        query: String,
        actions: AssistantCommandActions,
        paletteCommands: List<CommandPaletteItem>
    ) {
        if (query.length < 3) return
        searchCommandsAsync(query, actions, paletteCommands) { asyncResults ->
            _uiState.update { current ->
                if (current.query != query || !current.isOpen) {
                    current
                } else {
                    current.copy(
                        results = commandResultsForState(
                            query = query,
                            actions = actions,
                            paletteCommands = paletteCommands,
                            selectedSection = current.selectedSection,
                            maxResults = current.maxResults,
                            asyncResults = asyncResults
                        )
                    )
                }
            }
        }
    }

    private fun commandResultsForState(
        query: String,
        actions: AssistantCommandActions,
        paletteCommands: List<CommandPaletteItem>,
        selectedSection: String?,
        maxResults: Int,
        asyncResults: List<CommandPaletteItem> = emptyList()
    ): List<CommandPaletteItem> {
        val local = filterCommandPaletteItems(paletteCommands, query)
        val capture = quickCaptureCreateCommands(query, actions)
        val semantic = searchCommands(
            query = query,
            actions = actions,
            paletteCommands = paletteCommands
        )
        return mergeCommandPaletteResults(capture, local, semantic, asyncResults)
            .filter { selectedSection == null || it.group == selectedSection }
            .take(maxResults)
    }

    private fun boostedPaletteCommands(
        query: String,
        paletteCommands: List<CommandPaletteItem>,
        boostPriority: Int,
        excludeIds: Set<String> = emptySet()
    ): List<CommandPaletteItem> {
        val ids = commandAssistPlanner.localRankCommandIds(
            query = query,
            candidates = paletteCommands.map { it.toAssistCandidate() }
        ).filterNot { it in excludeIds }
        return paletteCommands
            .filter { it.id in ids }
            .map { command ->
                val rank = ids.indexOf(command.id)
                command.withBoostedPriority(boostPriority - rank.coerceAtLeast(0))
            }
    }
}

fun previewQuickCaptureCommand(
    query: String,
    actions: AssistantCommandActions = AssistantCommandActions()
): QuickCaptureCommandPreview? {
    val command = bestQuickCaptureCommand(query, actions) ?: return null
    return QuickCaptureCommandPreview(
        label = captureCommandPreviewLabel(command.id),
        title = command.title,
        subtitle = command.subtitle
    )
}

fun runQuickCaptureCommand(
    query: String,
    actions: AssistantCommandActions = AssistantCommandActions()
): Boolean {
    val command = bestQuickCaptureCommand(query, actions) ?: return false
    command.onRun()
    return true
}

private fun quickCaptureCreateCommands(
    query: String,
    actions: AssistantCommandActions
): List<CommandPaletteItem> {
    val capture = query.trim()
    if (capture.length < 3 && !capture.isShortMedicationCaptureCandidate()) return emptyList()

    return CaptureIntentClassifier.classify(capture)
        .filter { suggestion -> suggestion.shouldOfferCaptureCommand(capture) }
        .filter { suggestion ->
            when (suggestion.type) {
                CaptureIntentType.TASK -> true
                CaptureIntentType.HABIT -> actions.habitsEnabled
                CaptureIntentType.MEDICATION -> actions.medicationEnabled
                CaptureIntentType.FOCUS -> true
            }
        }
        .mapIndexed { index, suggestion ->
            val (title, subtitle, shortcut, onRun) = when (suggestion.type) {
                CaptureIntentType.TASK -> {
                    val typeLabel = captureCreateTypeLabel(CaptureIntentType.TASK, capture)
                    CaptureCommandCopy(
                        title = captureCreateTitle(typeLabel, capture),
                        subtitle = captureCreateSubtitle(
                            typeLabel = typeLabel,
                            suggestion = suggestion,
                            action = captureCreateAction(CaptureIntentType.TASK, capture)
                        ),
                        shortcut = captureCreateShortcut(CaptureIntentType.TASK, capture),
                        onRun = { actions.onCaptureTask(capture) }
                    )
                }
                CaptureIntentType.MEDICATION -> {
                    val typeLabel = captureCreateTypeLabel(CaptureIntentType.MEDICATION, capture)
                    CaptureCommandCopy(
                        title = captureCreateTitle(typeLabel, capture),
                        subtitle = captureCreateSubtitle(
                            typeLabel = typeLabel,
                            suggestion = suggestion,
                            action = captureCreateAction(CaptureIntentType.MEDICATION, capture)
                        ),
                        shortcut = captureCreateShortcut(CaptureIntentType.MEDICATION, capture),
                        onRun = { actions.onCaptureMedication(capture) }
                    )
                }
                CaptureIntentType.HABIT -> {
                    val typeLabel = captureCreateTypeLabel(CaptureIntentType.HABIT, capture)
                    CaptureCommandCopy(
                        title = captureCreateTitle(typeLabel, capture),
                        subtitle = captureCreateSubtitle(
                            typeLabel = typeLabel,
                            suggestion = suggestion,
                            action = captureCreateAction(CaptureIntentType.HABIT, capture)
                        ),
                        shortcut = captureCreateShortcut(CaptureIntentType.HABIT, capture),
                        onRun = { actions.onCaptureHabit(capture) }
                    )
                }
                CaptureIntentType.FOCUS -> {
                    val typeLabel = captureCreateTypeLabel(CaptureIntentType.FOCUS, capture)
                    CaptureCommandCopy(
                        title = captureCreateTitle(typeLabel, capture),
                        subtitle = captureCreateSubtitle(
                            typeLabel = typeLabel,
                            suggestion = suggestion,
                            action = captureCreateAction(CaptureIntentType.FOCUS, capture)
                        ),
                        shortcut = captureCreateShortcut(CaptureIntentType.FOCUS, capture),
                        onRun = { actions.onCaptureFocus(capture) }
                    )
                }
            }
            CommandPaletteItem(
                id = "capture.create.${suggestion.type.name.lowercase()}",
                title = title,
                subtitle = subtitle,
                keywords = setOf("add", "create", "capture", "ai", suggestion.type.name.lowercase()),
                group = CommandPaletteGroups.QUICK_CREATE,
                shortcutLabel = shortcut,
                priority = 260 + suggestion.score * 4 - index,
                onRun = onRun
            )
        }
}

private fun bestQuickCaptureCommand(
    query: String,
    actions: AssistantCommandActions
): CommandPaletteItem? = quickCaptureCreateCommands(query, actions).maxByOrNull { it.priority }

private fun captureCommandPreviewLabel(commandId: String): String = when {
    commandId.endsWith(".task") -> "task"
    commandId.endsWith(".medication") -> "med"
    commandId.endsWith(".habit") -> "habit"
    commandId.endsWith(".focus") -> "focus"
    else -> "item"
}

private data class CaptureCommandCopy(
    val title: String,
    val subtitle: String,
    val shortcut: String,
    val onRun: () -> Unit
)

private fun captureCreateTitle(typeLabel: String, capture: String): String =
    "Create $typeLabel from \"${capture.capturePreview()}\""

private fun captureCreateSubtitle(
    typeLabel: String,
    suggestion: CaptureIntentSuggestion,
    action: String
): String {
    val confidence = when {
        suggestion.score >= 8 -> "Strong"
        suggestion.score >= 4 -> "Likely"
        else -> "Capture"
    }
    return "$confidence $typeLabel: ${suggestion.reason} $action"
}

private fun captureCreateTypeLabel(type: CaptureIntentType, capture: String): String {
    val normalized = capture.lowercase()
    return when (type) {
        CaptureIntentType.TASK -> when {
            normalized.hasCaptureTerm("urgent", "high priority", "important", "asap", "critical") -> "priority task"
            normalized.hasCaptureTerm("call", "phone") -> "call task"
            normalized.hasCaptureTerm("email", "mail", "respond by email", "reply to email", "respond to email", "email reply") -> "email task"
            normalized.hasCaptureTerm("text", "sms", "message", "dm", "reply", "ask", "tell", "ping") -> "message task"
            normalized.hasCaptureTerm("document", "doc", "pdf", "file", "attachment", "brief", "proposal") -> "document task"
            normalized.hasCaptureTerm("link", "url", "website", "webpage", "http", "https", "www") -> "link task"
            normalized.hasTaskAppActionTerm() -> "app task"
            normalized.hasCaptureTerm("pay", "bill", "invoice", "rent", "utility bill", "utilities", "subscription", "credit card") -> "payment task"
            normalized.hasCaptureTerm("ship", "shipping", "mail", "post office", "ups", "fedex", "usps", "return label") -> "shipping task"
            normalized.hasCaptureTerm("clean", "laundry", "wash clothes", "dishes", "dishwasher", "trash", "plants", "water plants", "fix", "repair") -> "home task"
            normalized.hasCaptureTerm("birthday", "anniversary", "gift", "present", "birthday gift", "birthday card", "party", "celebration") -> "event task"
            normalized.hasCaptureTerm("buy", "grocery", "groceries", "shop", "order", "return", "package", "ship") -> "errand task"
            normalized.hasCaptureTerm("appointment", "appt", "doctor", "dentist", "clinic", "checkup", "physical", "exam", "therapy", "therapist", "pediatrician", "optometrist", "lab", "blood test", "vaccination", "vaccine") -> "appointment task"
            normalized.hasCaptureTerm("invite", "meeting invite", "calendar invite", "save the date", "send invite", "send calendar invite", "send meeting invite", "event invite", "rsvp", "accept invite", "decline invite") -> "invite task"
            normalized.hasCaptureTerm("meeting", "meet") -> "meeting task"
            normalized.hasCaptureTerm("drive", "go to", "navigate", "visit", "pickup", "pick up", "pick up from", "drop off", "drop off at", "take to", "bring to", "deliver to") -> "travel task"
            else -> "task"
        }
        CaptureIntentType.MEDICATION -> when {
            normalized.hasCaptureTerm("allergy", "zyrtec", "claritin", "cetirizine", "loratadine", "benadryl", "antihistamine") -> "allergy med"
            normalized.hasCaptureTerm("pain", "headache", "migraine", "ibuprofen", "advil", "tylenol", "acetaminophen", "naproxen", "aleve") -> "pain relief med"
            normalized.hasCaptureTerm("refill", "prescription", "rx", "pharmacy", "pharmacy pickup", "pick up prescription", "pickup prescription", "renew prescription", "refill prescription") -> "refill med"
            normalized.hasCaptureTerm("insulin", "injection", "inject", "shot", "syringe", "pen needle", "epipen", "epi pen", "epinephrine") -> "injection med"
            normalized.hasCaptureTerm("vitamin", "vit d", "vit c", "supplement", "omega", "b12", "d3", "iron", "magnesium", "calcium", "zinc", "probiotic", "cbd", "melatonin") -> "supplement med"
            normalized.hasCaptureTerm("as needed", "prn", "rescue") -> "as-needed med"
            normalized.hasCaptureTerm("inhaler", "puff", "spray") -> "inhaler med"
            normalized.hasCaptureTerm("drop", "drops", "eye drops", "ear drops") -> "drops med"
            normalized.hasCaptureTerm("cream", "ointment", "gel", "topical", "patch") -> "topical med"
            normalized.hasCaptureTerm("liquid", "syrup", "solution", "ml", "tsp", "teaspoon", "tbsp") -> "liquid med"
            else -> "medication"
        }
        CaptureIntentType.HABIT -> when {
            normalized.hasHabitAppAssistTerm() -> "app-assisted habit"
            normalized.hasCaptureTerm("strength", "weights", "lift", "lifting") -> "strength habit"
            normalized.hasCaptureTerm("run", "running", "jog", "jogging") -> "running habit"
            normalized.hasCaptureTerm("yoga", "pilates") -> "mobility habit"
            normalized.hasCaptureTerm("cardio", "bike", "cycling", "swim", "swimming") -> "cardio habit"
            normalized.hasCaptureTerm("gym", "workout", "workouts", "work out", "working out", "exercise", "fitness") -> "workout habit"
            normalized.hasCaptureTerm("water", "hydrate", "hydration", "drink", "glass of water", "glasses of water", "cups", "oz", "liter", "liters", "ml") -> "hydration habit"
            normalized.hasCaptureTerm("meal prep", "meal planning", "prep meals", "nutrition", "healthy eating", "eat breakfast", "eat lunch", "eat dinner", "eat vegetables", "eat fruit", "protein", "reduce caffeine", "cut caffeine", "limit caffeine", "cut sugar", "reduce sugar", "limit sugar", "breakfast habit", "lunch habit", "dinner habit") -> "nutrition habit"
            normalized.hasCaptureTerm("floss", "flossing", "brush teeth", "brush my teeth", "brushing teeth", "brushing my teeth", "dental") -> "dental habit"
            normalized.hasCaptureTerm("meditate", "meditation", "mindful", "mindfulness", "breath", "breathing", "journal") -> "mindfulness habit"
            normalized.hasCaptureTerm("study", "learn", "learning", "language practice", "spanish", "guitar", "piano", "coding practice") -> "learning habit"
            normalized.hasCaptureTerm("read", "reading", "pages", "book") -> "reading habit"
            normalized.hasCaptureTerm("sleep", "sleep routine", "bed", "bedtime", "wake", "wake up", "wind down", "screen free", "screen-free") -> "sleep habit"
            normalized.hasCaptureTerm("walk", "steps", "mobility", "stretch") -> "walking habit"
            else -> "habit"
        }
        CaptureIntentType.FOCUS -> when {
            normalized.hasCaptureTerm("pomodoro") -> "pomodoro focus session"
            normalized.hasCaptureTerm("deep work", "deep-work", "protected work", "protected focus") -> "protected focus session"
            else -> "focus session"
        }
    }
}

private fun captureCreateShortcut(type: CaptureIntentType, capture: String): String {
    val normalized = capture.lowercase()
    return when (type) {
        CaptureIntentType.TASK -> when {
            normalized.hasCaptureTerm("urgent", "high priority", "important", "asap", "critical") -> "Priority"
            normalized.hasCaptureTerm("call", "phone") -> "Call"
            normalized.hasCaptureTerm("email", "mail", "respond by email", "reply to email", "respond to email", "email reply") -> "Email"
            normalized.hasCaptureTerm("text", "sms", "message", "dm", "reply", "ask", "tell", "ping") -> "Msg"
            normalized.hasCaptureTerm("document", "doc", "pdf", "file", "attachment", "brief", "proposal") -> "Doc"
            normalized.hasCaptureTerm("link", "url", "website", "webpage", "http", "https", "www") -> "Link"
            normalized.hasTaskAppActionTerm() -> "App"
            normalized.hasCaptureTerm("pay", "bill", "invoice", "rent", "utility bill", "utilities", "subscription", "credit card") -> "Bill"
            normalized.hasCaptureTerm("ship", "shipping", "mail", "post office", "ups", "fedex", "usps", "return label") -> "Ship"
            normalized.hasCaptureTerm("clean", "laundry", "wash clothes", "dishes", "dishwasher", "trash", "plants", "water plants", "fix", "repair") -> "Home"
            normalized.hasCaptureTerm("birthday", "anniversary", "gift", "present", "birthday gift", "birthday card", "party", "celebration") -> "Event"
            normalized.hasCaptureTerm("buy", "grocery", "groceries", "shop", "order", "return", "package", "ship") -> "Errand"
            normalized.hasCaptureTerm("appointment", "appt", "doctor", "dentist", "clinic", "checkup", "physical", "exam", "therapy", "therapist", "pediatrician", "optometrist", "lab", "blood test", "vaccination", "vaccine") -> "Appt"
            normalized.hasCaptureTerm("invite", "meeting invite", "calendar invite", "save the date", "send invite", "send calendar invite", "send meeting invite", "event invite", "rsvp", "accept invite", "decline invite") -> "Invite"
            normalized.hasCaptureTerm("meeting", "meet") -> "Meet"
            normalized.hasCaptureTerm("drive", "go to", "navigate", "visit", "pickup", "pick up", "pick up from", "drop off", "drop off at", "take to", "bring to", "deliver to") -> "Travel"
            else -> "Task"
        }
        CaptureIntentType.MEDICATION -> when {
            normalized.hasCaptureTerm("allergy", "zyrtec", "claritin", "cetirizine", "loratadine", "benadryl", "antihistamine") -> "Allergy"
            normalized.hasCaptureTerm("pain", "headache", "migraine", "ibuprofen", "advil", "tylenol", "acetaminophen", "naproxen", "aleve") -> "Pain"
            normalized.hasCaptureTerm("refill", "prescription", "rx", "pharmacy", "pharmacy pickup", "pick up prescription", "pickup prescription", "renew prescription", "refill prescription") -> "Refill"
            normalized.hasCaptureTerm("insulin", "injection", "inject", "shot", "syringe", "pen needle", "epipen", "epi pen", "epinephrine") -> "Shot"
            normalized.hasCaptureTerm("vitamin", "vit d", "vit c", "supplement", "omega", "b12", "d3", "iron", "magnesium", "calcium", "zinc", "probiotic", "cbd", "melatonin") -> "Suppl"
            normalized.hasCaptureTerm("as needed", "prn", "rescue") -> "PRN"
            normalized.hasCaptureTerm("inhaler", "puff", "spray") -> "Inhaler"
            normalized.hasCaptureTerm("drop", "drops", "eye drops", "ear drops") -> "Drops"
            normalized.hasCaptureTerm("cream", "ointment", "gel", "topical", "patch") -> "Topical"
            normalized.hasCaptureTerm("liquid", "syrup", "solution", "ml", "tsp", "teaspoon", "tbsp") -> "Liquid"
            else -> "Meds"
        }
        CaptureIntentType.HABIT -> when {
            normalized.hasHabitAppAssistTerm() -> "App"
            normalized.hasCaptureTerm("strength", "weights", "lift", "lifting") -> "Strength"
            normalized.hasCaptureTerm("run", "running", "jog", "jogging") -> "Run"
            normalized.hasCaptureTerm("yoga", "pilates") -> "Mobility"
            normalized.hasCaptureTerm("cardio", "bike", "cycling", "swim", "swimming") -> "Cardio"
            normalized.hasCaptureTerm("gym", "workout", "workouts", "work out", "working out", "exercise", "fitness") -> "Workout"
            normalized.hasCaptureTerm("water", "hydrate", "hydration", "drink", "glass of water", "glasses of water", "cups", "oz", "liter", "liters", "ml") -> "Water"
            normalized.hasCaptureTerm("meal prep", "meal planning", "prep meals", "nutrition", "healthy eating", "eat breakfast", "eat lunch", "eat dinner", "eat vegetables", "eat fruit", "protein", "reduce caffeine", "cut caffeine", "limit caffeine", "cut sugar", "reduce sugar", "limit sugar", "breakfast habit", "lunch habit", "dinner habit") -> "Nutrition"
            normalized.hasCaptureTerm("floss", "flossing", "brush teeth", "brush my teeth", "brushing teeth", "brushing my teeth", "dental") -> "Dental"
            normalized.hasCaptureTerm("meditate", "meditation", "mindful", "mindfulness", "breath", "breathing", "journal") -> "Mindful"
            normalized.hasCaptureTerm("study", "learn", "learning", "language practice", "spanish", "guitar", "piano", "coding practice") -> "Learning"
            normalized.hasCaptureTerm("read", "reading", "pages", "book") -> "Read"
            normalized.hasCaptureTerm("sleep", "sleep routine", "bed", "bedtime", "wake", "wake up", "wind down", "screen free", "screen-free") -> "Sleep"
            normalized.hasCaptureTerm("walk", "steps", "mobility", "stretch") -> "Walk"
            else -> "Habit"
        }
        CaptureIntentType.FOCUS -> when {
            normalized.hasCaptureTerm("pomodoro") -> "Pomodoro"
            normalized.hasCaptureTerm("deep work", "deep-work") -> "Deep"
            else -> "Focus"
        }
    }
}

private fun captureCreateAction(type: CaptureIntentType, capture: String): String {
    val normalized = capture.lowercase()
    return when (type) {
        CaptureIntentType.TASK -> when {
            normalized.hasCaptureTerm("urgent", "high priority", "important", "asap", "critical") -> "Adds high-priority task setup, timing hints, and focused checklist suggestions."
            normalized.hasCaptureTerm("call", "phone") -> "Adds a phone action, due-time hint, priority, and call checklist."
            normalized.hasCaptureTerm("email", "mail", "respond by email", "reply to email", "respond to email", "email reply") -> "Adds an email action, due-time hint, contact or address notes, and follow-up checklist."
            normalized.hasCaptureTerm("text", "sms", "message", "dm", "reply", "ask", "tell", "ping") -> "Adds a message action, contact hint, due-time hint, and follow-up checklist."
            normalized.hasCaptureTerm("document", "doc", "pdf", "file", "attachment", "brief", "proposal") -> "Adds document action, review timing, priority, and checklist suggestions."
            normalized.hasCaptureTerm("link", "url", "website", "webpage", "http", "https", "www") -> "Adds link action, due-time hints, priority, and follow-up checklist."
            normalized.hasTaskAppActionTerm() -> "Adds app launch action, due-time hints, priority, and follow-up checklist."
            normalized.hasCaptureTerm("pay", "bill", "invoice", "rent", "utility bill", "utilities", "subscription", "credit card") -> "Adds due-date hints, amount or account notes, confirmation tracking, and payment checklist."
            normalized.hasCaptureTerm("ship", "shipping", "mail", "post office", "ups", "fedex", "usps", "return label") -> "Adds shipping/drop-off timing, label or package notes, location hints, and checklist suggestions."
            normalized.hasCaptureTerm("clean", "laundry", "wash clothes", "dishes", "dishwasher", "trash", "plants", "water plants", "fix", "repair") -> "Adds quick chore timing, priority, and concrete home-maintenance checklist suggestions."
            normalized.hasCaptureTerm("birthday", "anniversary", "gift", "present", "birthday gift", "birthday card", "party", "celebration") -> "Adds event timing, gift/card notes, reminder priority, and preparation checklist suggestions."
            normalized.hasCaptureTerm("buy", "grocery", "groceries", "shop", "order", "return", "package", "ship") -> "Adds errand-focused title, due-time, priority, and checklist suggestions."
            normalized.hasCaptureTerm("appointment", "appt", "doctor", "dentist", "clinic", "checkup", "physical", "exam", "therapy", "therapist", "pediatrician", "optometrist", "lab", "blood test", "vaccination", "vaccine") -> "Adds appointment title, date/time hints, location notes, and prep checklist."
            normalized.hasCaptureTerm("invite", "meeting invite", "calendar invite", "save the date", "send invite", "send calendar invite", "send meeting invite", "event invite", "rsvp", "accept invite", "decline invite") -> "Adds invite response, calendar timing, attendee notes, and follow-up checklist."
            normalized.hasCaptureTerm("meeting", "meet") -> "Adds meeting title, start-time or duration hints, and prep checklist."
            normalized.hasCaptureTerm("drive", "go to", "navigate", "visit", "pickup", "pick up", "pick up from", "drop off", "drop off at", "take to", "bring to", "deliver to") -> "Adds location/travel hints, timing, priority, and prep steps."
            else -> "Prefills schedule, actions, priority, and checklist suggestions."
        }
        CaptureIntentType.MEDICATION -> when {
            normalized.hasCaptureTerm("allergy", "zyrtec", "claritin", "cetirizine", "loratadine", "benadryl", "antihistamine") -> "Suggests allergy-med name, dose form, timing, as-needed behavior, and symptom notes."
            normalized.hasCaptureTerm("pain", "headache", "migraine", "ibuprofen", "advil", "tylenol", "acetaminophen", "naproxen", "aleve") -> "Suggests pain-relief med name, amount, timing, as-needed behavior, and safety notes."
            normalized.hasCaptureTerm("refill", "prescription", "rx", "pharmacy", "pharmacy pickup", "pick up prescription", "pickup prescription", "renew prescription", "refill prescription") -> "Suggests refill tracking, pharmacy pickup notes, medication name, and reminder timing."
            normalized.hasCaptureTerm("insulin", "injection", "inject", "shot", "syringe", "pen needle", "epipen", "epi pen", "epinephrine") -> "Suggests injection form, route, stated amount, timing, and tracking notes."
            normalized.hasCaptureTerm("vitamin", "vit d", "vit c", "supplement", "omega", "b12", "d3", "iron", "magnesium", "calcium", "zinc", "probiotic", "cbd", "melatonin") -> "Suggests supplement name, amount, timing, form, and meal notes."
            normalized.hasCaptureTerm("as needed", "prn", "rescue") -> "Suggests as-needed setup, safety notes, form, route, and reminder behavior."
            normalized.hasCaptureTerm("inhaler", "puff", "spray") -> "Suggests inhaler form, puff amount, route, timing, and safety notes."
            normalized.hasCaptureTerm("drop", "drops", "eye drops", "ear drops") -> "Suggests drops form, dose, route, timing, and safety notes."
            normalized.hasCaptureTerm("cream", "ointment", "gel", "topical", "patch") -> "Suggests topical form, route, timing, and usage notes."
            normalized.hasCaptureTerm("liquid", "syrup", "solution", "ml", "tsp", "teaspoon", "tbsp") -> "Suggests liquid dose, unit, timing, route, and refill details."
            else -> "Extracts dose, timing, form, route, and refill details."
        }
        CaptureIntentType.HABIT -> when {
            normalized.hasHabitAppAssistTerm() -> "Suggests habit cadence, completion window, effort, day-plan visibility, and editable app launch context."
            normalized.hasCaptureTerm("strength", "weights", "lift", "lifting") -> "Suggests strength cadence, training window, effort, and day-plan visibility."
            normalized.hasCaptureTerm("run", "running", "jog", "jogging") -> "Suggests running cadence, route/window, effort, and day-plan visibility."
            normalized.hasCaptureTerm("yoga", "pilates") -> "Suggests mobility cadence, gentle window, effort, and day-plan visibility."
            normalized.hasCaptureTerm("cardio", "bike", "cycling", "swim", "swimming") -> "Suggests cardio cadence, workout window, effort, and day-plan visibility."
            normalized.hasCaptureTerm("gym", "workout", "workouts", "work out", "working out", "exercise", "fitness") -> "Suggests workout cadence, window, effort, and day-plan visibility."
            normalized.hasCaptureTerm("water", "hydrate", "hydration", "drink", "glass of water", "glasses of water", "cups", "oz", "liter", "liters", "ml") -> "Suggests hydration target, all-day tracking, cadence, and reminder window."
            normalized.hasCaptureTerm("meal prep", "meal planning", "prep meals", "nutrition", "healthy eating", "eat breakfast", "eat lunch", "eat dinner", "eat vegetables", "eat fruit", "protein", "reduce caffeine", "cut caffeine", "limit caffeine", "cut sugar", "reduce sugar", "limit sugar", "breakfast habit", "lunch habit", "dinner habit") -> "Suggests nutrition cadence, meal window, low effort, and day-plan visibility."
            normalized.hasCaptureTerm("floss", "flossing", "brush teeth", "brush my teeth", "brushing teeth", "brushing my teeth", "dental") -> "Suggests dental-care cadence, evening window, low effort, and reminder visibility."
            normalized.hasCaptureTerm("meditate", "meditation", "mindful", "mindfulness", "breath", "breathing", "journal") -> "Suggests mindfulness cadence, gentle window, effort, and day-plan visibility."
            normalized.hasCaptureTerm("study", "learn", "learning", "language practice", "spanish", "guitar", "piano", "coding practice") -> "Suggests learning cadence, study window, moderate effort, and day-plan visibility."
            normalized.hasCaptureTerm("read", "reading", "pages", "book") -> "Suggests reading target, cadence, window, effort, and day-plan visibility."
            normalized.hasCaptureTerm("sleep", "sleep routine", "bed", "bedtime", "wake", "wake up", "wind down", "screen free", "screen-free") -> "Suggests sleep routine cadence, bedtime or wake window, low effort, and day-plan visibility."
            normalized.hasCaptureTerm("walk", "steps", "mobility", "stretch") -> "Suggests movement cadence, target, window, effort, and day-plan visibility."
            else -> "Infers cadence, completion window, effort, and day-plan visibility."
        }
        CaptureIntentType.FOCUS -> when {
            normalized.hasCaptureTerm("pomodoro") -> "Opens the focus planner with timer length, topic, and protected block context."
            normalized.hasCaptureTerm("deep work", "deep-work", "protected work", "protected focus") -> "Opens the focus planner with title, duration, and protected block context."
            else -> "Opens the focus planner with captured topic, duration, and protected block context."
        }
    }
}

private fun String.hasCaptureTerm(vararg terms: String): Boolean =
    terms.any { term ->
        if (term.any(Char::isWhitespace)) {
            contains(term)
        } else {
            Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(this)
        }
    }

private fun String.hasTaskAppActionTerm(): Boolean =
    TASK_CAPTURE_APP_ACTION_PATTERN.containsMatchIn(this)

private fun String.hasHabitAppAssistTerm(): Boolean =
    HABIT_CAPTURE_APP_ASSIST_PATTERN.containsMatchIn(this)

private val TASK_CAPTURE_APP_ACTION_PATTERN = Regex(
    """\b(?:open|launch|start|join|check|review)\s+(?:spotify|youtube|you\s+tube|zoom|teams|microsoft\s+teams|slack|notion|gmail|google\s+calendar|google\s+drive|google\s+docs|google\s+sheets|google\s+slides|google\s+maps|maps)\b"""
)

private val HABIT_CAPTURE_APP_ASSIST_PATTERN = Regex(
    """\b(?:with|using|in|on)\s+(?:duolingo|strava|headspace|calm|spotify|youtube|you\s+tube|google\s+fit|fitbit|myfitnesspal|my\s+fitness\s+pal)\b"""
)

private fun String.capturePreview(maxLength: Int = 56): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength).trimEnd() + "..."
    }
}

private fun CaptureIntentSuggestion.shouldOfferCaptureCommand(capture: String): Boolean {
    val normalized = capture.trim().lowercase()
    if (
        normalized in setOf(
            "task",
            "tasks",
            "habit",
            "habits",
            "med",
            "meds",
            "medication",
            "medications",
            "focus",
            "focus session",
            "deep work"
        )
    ) return false
    val isShortMedicationCapture = normalized in SHORT_MEDICATION_CAPTURE_WORDS
    if (normalized.length < 3 && !isShortMedicationCapture) return false
    val tokenCount = normalized.split(Regex("\\s+")).count { it.isNotBlank() }
    return when (type) {
        CaptureIntentType.MEDICATION -> score >= 4 ||
            isShortMedicationCapture ||
            MEDICATION_CAPTURE_WORDS.any { word -> normalized == word || normalized.contains(word) }
        CaptureIntentType.HABIT -> score >= 4 ||
            HABIT_CAPTURE_WORDS.any { word -> normalized == word || normalized.contains(word) } ||
            (
                HABIT_HYDRATION_UNIT_WORDS.any { word -> normalized == word || normalized.contains(word) } &&
                    HABIT_HYDRATION_CONTEXT_WORDS.any { word -> normalized == word || normalized.contains(word) }
                )
        CaptureIntentType.FOCUS -> score >= 6 ||
            FOCUS_CAPTURE_WORDS.any { word -> normalized == word || normalized.contains(word) }
        CaptureIntentType.TASK -> score >= 4 ||
            tokenCount >= 3 ||
            TASK_CAPTURE_PRIORITY_WORDS.any { word -> word in normalized } ||
            TASK_CAPTURE_TIME_WORDS.any { word -> word in normalized } ||
            TASK_CAPTURE_ACTION_WORDS.any { word -> normalized == word || normalized.startsWith("$word ") } ||
            TASK_CAPTURE_PREFIXES.any { prefix -> normalized.startsWith(prefix) }
    }
}

private val TASK_CAPTURE_PRIORITY_WORDS = setOf(
    "urgent",
    "high priority",
    "important",
    "asap",
    "critical"
)

private fun String.isShortMedicationCaptureCandidate(): Boolean =
    trim().lowercase() in SHORT_MEDICATION_CAPTURE_WORDS

private val SHORT_MEDICATION_CAPTURE_WORDS = setOf("d3", "rx")

private val TASK_CAPTURE_ACTION_WORDS = setOf(
    "call",
    "email",
    "text",
    "message",
    "ask",
    "tell",
    "reply",
    "respond",
    "follow",
    "send",
    "pay",
    "buy",
    "shop",
    "order",
    "pickup",
    "pick",
    "drop",
    "drive",
    "go",
    "navigate",
    "directions",
    "meet",
    "return",
    "bring",
    "deliver",
    "visit",
    "ship",
    "package",
    "review",
    "submit",
    "schedule"
)

private val MEDICATION_CAPTURE_WORDS = setOf(
    "mg",
    "mcg",
    "ml",
    "tsp",
    "teaspoon",
    "tbsp",
    "tablespoon",
    "iu",
    "dose",
    "tablet",
    "capsule",
    "drop",
    "drops",
    "puff",
    "spray",
    "vitamin",
    "vitamin d",
    "vit d",
    "vitamin c",
    "vit c",
    "supplement",
    "magnesium",
    "iron",
    "calcium",
    "zinc",
    "b12",
    "omega",
    "probiotic",
    "melatonin",
    "allergy",
    "allergies",
    "antihistamine",
    "cough",
    "cold",
    "fever",
    "headache",
    "migraine",
    "pain",
    "rash",
    "itching",
    "heartburn",
    "antacid",
    "rescue",
    "inhaler",
    "syrup",
    "liquid",
    "solution",
    "cream",
    "ointment",
    "gel",
    "topical",
    "patch",
    "refill",
    "prn",
    "as needed",
    "with food",
    "after food",
    "with breakfast",
    "with lunch",
    "with dinner",
    "before bed",
    "every 6 hours",
    "every six hours",
    "every 8 hours",
    "every eight hours",
    "every 12 hours",
    "every twelve hours"
)

private val HABIT_CAPTURE_WORDS = setOf(
    "daily",
    "nightly",
    "weekly",
    "weekdays",
    "weekends",
    "every day",
    "every morning",
    "every night",
    "gym",
    "workout",
    "workouts",
    "work out",
    "working out",
    "exercise",
    "fitness",
    "training",
    "strength",
    "cardio",
    "weights",
    "lift",
    "lifting",
    "yoga",
    "pilates",
    "swim",
    "bike",
    "cycle",
    "run",
    "walk",
    "stretch",
    "mobility",
    "drink",
    "drinking",
    "fluid",
    "fluids",
    "hydrate",
    "water",
    "cups",
    "ounces",
    "oz",
    "liters",
    "litres",
    "meditate",
    "mindful",
    "breathing",
    "journal",
    "read",
    "sleep",
    "steps"
)

private val FOCUS_CAPTURE_WORDS = setOf(
    "focus session",
    "deep work",
    "deep-work",
    "protected focus",
    "protected work",
    "pomodoro",
    "flow block"
)

private val HABIT_HYDRATION_UNIT_WORDS = setOf("ml", "milliliter", "milliliters")

private val HABIT_HYDRATION_CONTEXT_WORDS = setOf(
    "drink",
    "drinking",
    "fluid",
    "fluids",
    "hydrate",
    "hydration",
    "water"
)

private val TASK_CAPTURE_TIME_WORDS = setOf(
    " at ",
    " today",
    " tomorrow",
    " tonight",
    " this ",
    " by ",
    " on ",
    " monday",
    " tuesday",
    " wednesday",
    " thursday",
    " friday",
    " saturday",
    " sunday",
    " deadline",
    " due "
)

private val TASK_CAPTURE_PREFIXES = listOf(
    "add task",
    "create task",
    "todo",
    "remind me to",
    "i need to",
    "need to"
)

private fun SemanticSearchHit.toCommandItem(actions: AssistantCommandActions): CommandPaletteItem {
    val onRun = when (type) {
        SemanticDocumentType.REVIEW -> actions.onOpenReview
        SemanticDocumentType.TIME_BLOCK -> actions.onOpenDay
        SemanticDocumentType.TASK -> actions.onOpenTasks
        SemanticDocumentType.MEDICATION -> actions.onOpenMedication
        SemanticDocumentType.HABIT -> actions.onOpenHabits
        SemanticDocumentType.FOCUS -> actions.onOpenFocus
    }
    return CommandPaletteItem(
        id = "semantic.${type.name.lowercase()}.$id",
        title = title,
        subtitle = snippet,
        keywords = setOf(provenance, type.name.lowercase(), "assistant"),
        group = CommandPaletteGroups.ASSISTANT,
        priority = (score * 100).toInt(),
        onRun = onRun
    )
}

private fun CommandPaletteItem.toAssistCandidate(): CommandAssistCandidate = CommandAssistCandidate(
    id = id,
    title = title,
    keywords = keywords
)

private fun CommandPaletteItem.withBoostedPriority(priority: Int): CommandPaletteItem = copy(priority = priority)
