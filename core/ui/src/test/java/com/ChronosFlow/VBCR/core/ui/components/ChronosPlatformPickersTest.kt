package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChronosPlatformPickersTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun timePickerFieldUsesBottomSheetByDefault() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosTimePickerField(
                    label = "Reminder time",
                    value = "9:00 AM",
                    selectedMinute = 9 * 60,
                    onTimeSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("9:00 AM").assertExists().performClick()

        composeTestRule.onNodeWithText("Select time for \"Reminder time\"").assertExists()
        composeTestRule.onNodeWithText("Confirm").assertExists()
    }

    @Test
    fun timePickerFieldInvokesLauncherAndAppliesSelection() {
        val selectedMinute = mutableIntStateOf(9 * 60)
        var launchedMinute: Int? = null

        composeTestRule.setContent {
            MaterialTheme {
                ChronosTimePickerField(
                    label = "Reminder time",
                    value = formatDisplayMinute(selectedMinute.intValue),
                    selectedMinute = selectedMinute.intValue,
                    onTimeSelected = { selectedMinute.intValue = it },
                    showPicker = { _, minute, _, onSelected ->
                        launchedMinute = minute
                        onSelected(13 * 60 + 15)
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("9:00 AM").assertExists().performClick()

        composeTestRule.runOnIdle {
            assertEquals(9 * 60, launchedMinute)
            assertEquals(13 * 60 + 15, selectedMinute.intValue)
        }

        composeTestRule.onNodeWithText("1:15 PM").assertExists()
    }

    @Test
    fun datePickerFieldUsesBottomSheetByDefault() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosDatePickerField(
                    label = "Target day",
                    value = formatChronosPickerDate(LocalDate.of(2026, 5, 28)),
                    selectedDate = LocalDate.of(2026, 5, 28),
                    onDateSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText(formatChronosPickerDate(LocalDate.of(2026, 5, 28)))
            .assertExists()
            .performClick()

        composeTestRule.onNodeWithText("Select date for \"Target day\"").assertExists()
        composeTestRule.onNodeWithText("Select").assertExists()
    }

    @Test
    fun datePickerFieldInvokesLauncherAndAppliesSelection() {
        val selectedDate = mutableStateOf(LocalDate.of(2026, 5, 28))
        var launchedDate: LocalDate? = null

        composeTestRule.setContent {
            MaterialTheme {
                ChronosDatePickerField(
                    label = "Target day",
                    value = formatChronosPickerDate(selectedDate.value),
                    selectedDate = selectedDate.value,
                    onDateSelected = { selectedDate.value = it },
                    showPicker = { _, date, onSelected ->
                        launchedDate = date
                        onSelected(LocalDate.of(2026, 6, 3))
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(formatChronosPickerDate(LocalDate.of(2026, 5, 28)))
            .assertExists()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(LocalDate.of(2026, 5, 28), launchedDate)
            assertEquals(LocalDate.of(2026, 6, 3), selectedDate.value)
        }

        composeTestRule.onNodeWithText(formatChronosPickerDate(LocalDate.of(2026, 6, 3))).assertExists()
    }
}
