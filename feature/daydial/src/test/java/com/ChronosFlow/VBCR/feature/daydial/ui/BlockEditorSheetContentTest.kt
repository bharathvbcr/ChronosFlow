package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.ai.genai.GenAiRuntimeStatus
import com.ChronosFlow.VBCR.feature.daydial.model.AppearanceMode
import com.ChronosFlow.VBCR.feature.daydial.model.DailyReview
import com.ChronosFlow.VBCR.feature.daydial.model.SheetTarget
import com.ChronosFlow.VBCR.feature.daydial.model.TimeBlockUiModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Guards against the block-editor clipping regression: on a short sheet the
 * field list must scroll while the action rows stay pinned, visible, and
 * tappable without any scrolling. Runs on Robolectric so it has internal
 * access to SheetContent and executes with the JVM unit-test suite.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class BlockEditorSheetContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun blockEditorActionButtonsStayVisibleAndTappableOnShortSheets() {
        val events = mutableListOf<String>()
        val block = editorBlock()

        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) {
                    BlockEditorSheetUnderTest(
                        block = block,
                        onSave = { events += "save" },
                        onDelete = { events += "delete" }
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithContentDescription(sheetBlockSaveActionLabel(block))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(sheetBlockDeleteActionLabel(block))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(sheetBlockDuplicateActionLabel(block))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(sheetBlockStartFocusActionLabel(block))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(sheetBlockSaveActionLabel(block))
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(sheetBlockDeleteActionLabel(block))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("save", "delete"), events)
    }

    private fun editorBlock(): TimeBlockUiModel = TimeBlockUiModel(
        id = "block-1",
        title = "Deep work",
        startMinuteOfDay = 9 * 60,
        durationMinutes = 90,
        color = Color(0xFF64B5F6),
        provenance = "USER",
        flexibility = "MOVABLE",
        isProtected = false
    )
}

@androidx.compose.runtime.Composable
private fun BlockEditorSheetUnderTest(
    block: TimeBlockUiModel,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    SheetContent(
        target = SheetTarget.BlockEditor(blockId = block.id),
        selectedBlock = block,
        selectedDate = LocalDate.now(),
        timeBlocks = listOf(block),
        missedBlocks = emptyList(),
        suggestedBlocks = emptyList(),
        review = DailyReview(
            plannedMinutes = 0,
            actualMinutes = 0,
            missedMinutes = 0,
            completedBlocks = 0
        ),
        privacyMode = PrivacyMode.ON_DEVICE_ONLY,
        isGenerating = false,
        genAiRuntimeStatus = GenAiRuntimeStatus(),
        aiPlanResult = null,
        explainPlan = null,
        explainPlanSource = null,
        focusElapsedSeconds = 0L,
        syncStatus = "",
        blockStartReminders = false,
        breakReminders = false,
        missedAlerts = false,
        endDayReviewReminder = false,
        reminderScheduleStatus = "",
        medicationReliabilityStatus = "",
        dynamicColorEnabled = false,
        glassSurfacesEnabled = false,
        appearanceMode = AppearanceMode.SYSTEM,
        reduceMotionEnabled = false,
        highContrastEnabled = false,
        calendarPermissionStatus = CalendarPermissionStatus(
            readGranted = false,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = false
        ),
        showCalendarPermissionRationale = false,
        calendarConnectionState = CalendarConnectionState(),
        onDismiss = {},
        onDismissCalendarPermissionRationale = {},
        onDeleteBlock = onDelete,
        onStartFocus = {},
        onCreateBlock = { _, _, _, _ -> },
        onGeneratePlan = {},
        onApplyAiSuggestions = {},
        onDismissAiSuggestions = {},
        onAcceptAiSuggestion = {},
        onRejectAiSuggestion = {},
        onModifyAiSuggestion = { _, _, _, _ -> },
        onAdjustFocus = {},
        onMarkComplete = {},
        onMarkMissed = {},
        onDuplicateBlock = {},
        onUpdateBlockDetails = { _, _, _, _, _, _, _ -> onSave() },
        onExportBlockToCalendar = {},
        onRefreshCalendarExport = {},
        onRemoveCalendarExport = {},
        onOpenCalendarSettings = {},
        dataExportState = DataExportState(),
        onCreateDataExport = {},
        onImportBackup = {},
        onFinishFocus = {},
        onEndDay = {},
        showMessage = {}
    )
}
