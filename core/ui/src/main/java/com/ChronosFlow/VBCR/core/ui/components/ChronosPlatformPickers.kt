package com.ChronosFlow.VBCR.core.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.shell.ChronosModalBottomSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

typealias ShowNativeTimePicker = (Context, Int, Boolean, (Int) -> Unit) -> Unit
typealias ShowNativeDatePicker = (Context, LocalDate, (LocalDate) -> Unit) -> Unit

@Composable
fun ChronosTimePickerField(
    label: String,
    value: String,
    selectedMinute: Int?,
    onTimeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showPicker: ShowNativeTimePicker? = null,
    sheetTitle: String? = null,
    sheetConfirmButtonLabel: String = "Confirm",
    sheetCancelButtonLabel: String = "Cancel"
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }

    ChronosOutlinedButton(
        onClick = {
            if (showPicker != null) {
                // If overridden (e.g. by unit tests), call the mock picker callback
                showPicker(
                    context,
                    selectedMinute ?: 8 * 60,
                    DateFormat.is24HourFormat(context),
                    onTimeSelected
                )
            } else {
                showSheet = true
            }
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
    ) {
        ChronosPickerFieldContent(label = label, value = value)
    }

    if (showSheet) {
        ChronosTimePickerBottomSheet(
            initialMinute = selectedMinute ?: 8 * 60,
            is24Hour = DateFormat.is24HourFormat(context),
            title = sheetTitle ?: pickerTitleForField("Select time", label),
            confirmButtonLabel = sheetConfirmButtonLabel,
            cancelButtonLabel = sheetCancelButtonLabel,
            onTimeSelected = onTimeSelected,
            onDismiss = { showSheet = false }
        )
    }
}

@Composable
fun ChronosDatePickerField(
    label: String,
    value: String,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showPicker: ShowNativeDatePicker? = null,
    sheetTitle: String? = null,
    sheetConfirmButtonLabel: String = "Select",
    sheetCancelButtonLabel: String = "Cancel"
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }

    ChronosOutlinedButton(
        onClick = {
            if (showPicker != null) {
                // If overridden (e.g. by unit tests), call the mock picker callback
                showPicker(
                    context,
                    selectedDate ?: LocalDate.now(),
                    onDateSelected
                )
            } else {
                showSheet = true
            }
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
    ) {
        ChronosPickerFieldContent(label = label, value = value)
    }

    if (showSheet) {
        ChronosDatePickerBottomSheet(
            initialDate = selectedDate ?: LocalDate.now(),
            title = sheetTitle ?: pickerTitleForField("Select date", label),
            confirmButtonLabel = sheetConfirmButtonLabel,
            cancelButtonLabel = sheetCancelButtonLabel,
            onDateSelected = onDateSelected,
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronosDatePickerBottomSheet(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Select date",
    confirmButtonLabel: String = "Select",
    cancelButtonLabel: String = "Cancel"
) {
    val zoneId = java.time.ZoneOffset.UTC
    val initialMs = initialDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)

        ChronosModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = BottomSheetDefaults.ExpandedShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
                DatePicker(
                    state = datePickerState,
                    showModeToggle = true
                )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChronosOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(cancelButtonLabel, fontWeight = FontWeight.SemiBold)
                }
                ChronosButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            val date = java.time.Instant.ofEpochMilli(ms)
                                .atZone(zoneId)
                                .toLocalDate()
                            onDateSelected(date)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(confirmButtonLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronosTimePickerBottomSheet(
    initialMinute: Int,
    is24Hour: Boolean,
    onTimeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Select time",
    confirmButtonLabel: String = "Confirm",
    cancelButtonLabel: String = "Cancel"
) {
    val initialHour = initialMinute / 60
    val initialMin = initialMinute % 60
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMin,
        is24Hour = is24Hour
    )
    var isInputMode by remember { mutableStateOf(false) }

    ChronosModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = BottomSheetDefaults.ExpandedShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChronosTextButton(onClick = { isInputMode = !isInputMode }) {
                    Text(
                        text = if (isInputMode) "Show clock" else "Use keyboard",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isInputMode) {
                    TimeInput(state = timePickerState)
                } else {
                    TimePicker(state = timePickerState)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChronosOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(cancelButtonLabel, fontWeight = FontWeight.SemiBold)
                }
                ChronosButton(
                    onClick = {
                        onTimeSelected(timePickerState.hour * 60 + timePickerState.minute)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(confirmButtonLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

fun formatChronosPickerDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))

private fun pickerTitleForField(baseTitle: String, fieldLabel: String): String {
    val normalizedLabel = fieldLabel.trim()
    return if (normalizedLabel.isBlank()) {
        baseTitle
    } else {
        "$baseTitle for \"$normalizedLabel\""
    }
}

@Composable
private fun ChronosPickerFieldContent(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
