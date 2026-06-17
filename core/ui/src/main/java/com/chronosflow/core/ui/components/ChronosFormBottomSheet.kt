package com.chronosflow.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.motion.ChronosValueAnimationFactory
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.shell.ChronosModalBottomSheet
import com.chronosflow.core.ui.theme.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronosFormBottomSheet(
    visible: Boolean,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancel",
    enabled: Boolean = true,
    subtitle: String? = null,
    validationHint: String? = null,
    onArchive: (() -> Unit)? = null,
    archiveLabel: String = "Archive",
    onDuplicate: (() -> Unit)? = null,
    duplicateLabel: String = "Duplicate",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ChronosModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        chromeTag = "form-sheet",
        containerColor = Color.Transparent,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        val reduceMotionEnabled = rememberChronosUiSettings().reduceMotionEnabled
        Box(
            modifier = modifier
                .fillMaxWidth()
                .liquidGlass(cornerRadius = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                subtitle?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ChronosFormValidationHint(
                    message = if (!enabled) validationHint else null,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Size to content but yield to the footer buttons: the form area
                        // grows with the screen instead of capping at a fixed height.
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .animateContentSize(
                            animationSpec = ChronosValueAnimationFactory.stateChange(reduceMotionEnabled)
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (onDuplicate != null) {
                    ChronosTextButton(
                        onClick = onDuplicate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(duplicateLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (onArchive != null) {
                    ChronosTextButton(
                        onClick = onArchive,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(archiveLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (onDuplicate != null || onArchive != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChronosOutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dismissLabel, fontWeight = FontWeight.SemiBold)
                    }
                    ChronosButton(
                        onClick = onConfirm,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ChronosQuickAddChips(
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    selected: String = ""
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChronosOptionChips(
            label = "",
            options = options,
            selected = selected,
            onSelected = onSelect,
            optionLabel = { it }
        )
    }
}
