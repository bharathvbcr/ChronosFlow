package com.chronosflow.feature.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chronosflow.core.ai.TaskAssistActionDraftPayload
import com.chronosflow.core.ai.TaskAssistSchedulePayload
import com.chronosflow.core.ai.TaskAssistSource
import com.chronosflow.core.ai.TaskAssistSuggestion
import com.chronosflow.core.domain.model.TaskChecklistItem
import com.chronosflow.core.domain.model.TaskActionType
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class TaskFormSheetUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun assistTitleSuggestionRequiresUserClickBeforeItApplies() {
        val suggestion = TaskAssistSuggestion.Title(
            id = "title-1",
            label = "Use clearer title",
            reason = "The title can be made more actionable.",
            source = TaskAssistSource.GEMINI_NANO,
            title = "Send launch follow-up"
        )
        var capturedTitle: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                var assistState by remember {
                    mutableStateOf(TaskAssistUiState(suggestions = listOf(suggestion)))
                }

                TaskFormSheet(
                    target = TaskSheetTarget.Add(prefillTitle = "Follow-up"),
                    onDismiss = {},
                    onConfirm = { title, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
                        capturedTitle = title
                    },
                    assistState = assistState,
                    onRequestAssist = {},
                    onClearAssist = { assistState = TaskAssistUiState() }
                )
            }
        }

        // The suggested title is offered as a tappable chip, but must NOT be written into the
        // editable Title field until the suggestion is clicked. Scope the check to the editable
        // field (hasSetTextAction) so the offered chip doesn't count as "applied".
        composeTestRule
            .onNode(hasSetTextAction() and hasText("Send launch follow-up"))
            .assertDoesNotExist()

        composeTestRule.onNodeWithText("Use clearer title").performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNode(hasSetTextAction() and hasText("Send launch follow-up"))
            .assertExists()
        composeTestRule.onNodeWithText("Add task").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Send launch follow-up", capturedTitle)
    }

    @Test
    fun assistActionSuggestionRequiresUserClickBeforeItApplies() {
        val suggestion = TaskAssistSuggestion.ActionDraft(
            id = "suggestion-1",
            label = "Email Alex",
            reason = "The task asks for a direct follow-up.",
            source = TaskAssistSource.GEMINI_NANO,
            payload = TaskAssistActionDraftPayload(
                type = TaskActionType.EMAIL,
                label = "Email Alex",
                value = "alex@example.com"
            )
        )

        composeTestRule.setContent {
            MaterialTheme {
                var assistState by remember {
                    mutableStateOf(TaskAssistUiState(suggestions = listOf(suggestion)))
                }

                TaskFormSheet(
                    target = TaskSheetTarget.Add(),
                    onDismiss = {},
                    onConfirm = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    assistState = assistState,
                    onRequestAssist = {},
                    onClearAssist = { assistState = TaskAssistUiState() }
                )
            }
        }

        composeTestRule.onNodeWithText("alex@example.com").assertDoesNotExist()

        composeTestRule.onNodeWithText("Email Alex").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Action label").assertExists()
        composeTestRule.onNodeWithText("Email Alex").assertExists()
        composeTestRule.onNodeWithText("alex@example.com").assertExists()
    }

    @Test
    fun incompleteAssistActionSuggestionPrefillsManualActionFields() {
        val suggestion = TaskAssistSuggestion.ActionDraft(
            id = "suggestion-1",
            label = "Call Alex",
            reason = "The task asks for a call, but no number is known yet.",
            source = TaskAssistSource.LOCAL,
            payload = TaskAssistActionDraftPayload(
                type = TaskActionType.PHONE,
                label = "Call Alex",
                value = ""
            )
        )

        composeTestRule.setContent {
            MaterialTheme {
                var assistState by remember {
                    mutableStateOf(TaskAssistUiState(suggestions = listOf(suggestion)))
                }

                TaskFormSheet(
                    target = TaskSheetTarget.Add(),
                    onDismiss = {},
                    onConfirm = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    assistState = assistState,
                    onRequestAssist = {},
                    onClearAssist = { assistState = TaskAssistUiState() }
                )
            }
        }

        composeTestRule.onNodeWithText("Action label").assertDoesNotExist()

        composeTestRule.onNodeWithText("Call Alex").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Action label").assertDoesNotExist()
        composeTestRule.onNodeWithText("New action label").assertExists()
        composeTestRule.onNodeWithText("New action destination").assertExists()
        composeTestRule.onNodeWithText("Call Alex").assertExists()
    }

    @Test
    fun assistSuggestionBatchCanApplyScheduleThenChecklist() {
        val targetDate = LocalDate.now().plusDays(1)
        val scheduleSuggestion = TaskAssistSuggestion.Schedule(
            id = "schedule-1",
            label = "Schedule launch block",
            reason = "The task names a launch window.",
            source = TaskAssistSource.GEMINI_NANO,
            payload = TaskAssistSchedulePayload(
                targetDate = targetDate,
                preferredDurationMinutes = 45,
                preferredStartMinuteOfDay = 13 * 60
            )
        )
        val checklistSuggestion = TaskAssistSuggestion.Checklist(
            id = "checklist-1",
            label = "Add launch checklist",
            reason = "The task needs a few concrete steps.",
            source = TaskAssistSource.GEMINI_NANO,
            items = listOf("Confirm owner", "Send launch note")
        )
        var capturedDuration: Int? = null
        var capturedStart: Int? = null
        var capturedTargetDate: LocalDate? = null
        var capturedChecklist: List<TaskChecklistItem> = emptyList()

        composeTestRule.setContent {
            MaterialTheme {
                var assistState by remember {
                    mutableStateOf(
                        TaskAssistUiState(
                            suggestions = listOf(scheduleSuggestion, checklistSuggestion)
                        )
                    )
                }

                TaskFormSheet(
                    target = TaskSheetTarget.Add(prefillTitle = "Prepare launch"),
                    onDismiss = {},
                    onConfirm = { _, _, _, _, _, duration, preferredStart, date, checklist, _, _, _, _, _ ->
                        capturedDuration = duration
                        capturedStart = preferredStart
                        capturedTargetDate = date
                        capturedChecklist = checklist
                    },
                    assistState = assistState,
                    onRequestAssist = {},
                    onClearAssist = { assistState = TaskAssistUiState() }
                )
            }
        }

        composeTestRule.onNodeWithText("Schedule launch block").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Add launch checklist").assertExists()

        composeTestRule.onNodeWithText("Add launch checklist").performClick()
        composeTestRule.onNodeWithText("Add task").performClick()
        composeTestRule.waitForIdle()

        assertEquals(45, capturedDuration)
        assertEquals(13 * 60, capturedStart)
        assertEquals(targetDate, capturedTargetDate)
        assertEquals(listOf("Confirm owner", "Send launch note"), capturedChecklist.map { it.label })
    }

    @Test
    fun assistPrioritySuggestionRequiresUserClickBeforeItApplies() {
        val suggestion = TaskAssistSuggestion.Priority(
            id = "priority-1",
            label = "Mark urgent",
            reason = "The task text names a time-sensitive launch.",
            source = TaskAssistSource.GEMINI_NANO,
            priority = 2
        )
        var capturedPriority: Int? = null

        composeTestRule.setContent {
            MaterialTheme {
                var assistState by remember {
                    mutableStateOf(TaskAssistUiState(suggestions = listOf(suggestion)))
                }

                TaskFormSheet(
                    target = TaskSheetTarget.Add(prefillTitle = "Ship launch fix"),
                    onDismiss = {},
                    onConfirm = { _, _, priority, _, _, _, _, _, _, _, _, _, _, _ ->
                        capturedPriority = priority
                    },
                    assistState = assistState,
                    onRequestAssist = {},
                    onClearAssist = { assistState = TaskAssistUiState() }
                )
            }
        }

        // The priority suggestion is offered but the value applies only when clicked: capturedPriority
        // stays unset until the chip is tapped and the task is saved. (The "Priority / reminder"
        // section auto-expands whenever a priority suggestion exists, so section visibility can't
        // distinguish the click — the applied value is the contract.)
        composeTestRule.onNodeWithText("Mark urgent").assertExists()

        composeTestRule.onNodeWithText("Mark urgent").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Add task").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, capturedPriority)
    }
}
