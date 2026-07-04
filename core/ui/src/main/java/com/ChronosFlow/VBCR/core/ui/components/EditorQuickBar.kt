package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiBooleanSetting
import com.ChronosFlow.VBCR.core.ui.settings.rememberPersistentUiBooleanSetting
import com.ChronosFlow.VBCR.core.ui.settings.rememberPersistentUiIntSetting
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing

// MARK: - Editor quick-bar
//
// The attribute quick-bar shown at the top of a card editor. Used directly or via [CardEditorScaffold].
// one per common attribute (Due, Priority, Repeat, Checklist …). A chip is *muted* when the
// attribute is unset (it shows the attribute name) and *tinted* when set (it shows the value) with a
// trailing "✕" to clear it. Tapping a chip reveals the matching section; long-pressing offers
// Pin / Hide so the user tailors which advanced sections open by default.
//
// This complements the forms' existing content-adaptive auto-expand (taskModalAdaptiveHints, etc.):
// the quick-bar is the fast set/clear surface plus the manual customization the hints can't provide.

/** DataStore key for "this editor section is pinned open by default" (per editor kind + section). */
fun editorSectionPinnedKey(kind: String, id: String): String = "editor.$kind.pinned.$id"

/** DataStore key for "this editor section is hidden unless explicitly revealed". */
fun editorSectionHiddenKey(kind: String, id: String): String = "editor.$kind.hidden.$id"

/** DataStore key for how often the user expanded this section (adaptive promotion signal). */
fun editorSectionUsageKey(kind: String, id: String): String = "editor.$kind.usage.$id"

/** Opens before a section auto-surfaces above "More options". Matches iOS `EditorLayoutStore`. */
const val EDITOR_SECTION_AUTO_SURFACE_THRESHOLD = 3

/** Whether compact editors (quick-bar + progressive disclosure) are enabled. On by default. */
@Composable
fun rememberAdaptiveEditorEnabled(): Boolean =
    rememberChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_ADAPTIVE_EDITOR_ENABLED, true)

/** Two-way pin state for a section, persisted across launches. */
@Composable
fun rememberEditorSectionPinned(kind: String, id: String): MutableState<Boolean> =
    rememberPersistentUiBooleanSetting(editorSectionPinnedKey(kind, id), false)

/** Two-way hide state for a section, persisted across launches. */
@Composable
fun rememberEditorSectionHidden(kind: String, id: String): MutableState<Boolean> =
    rememberPersistentUiBooleanSetting(editorSectionHiddenKey(kind, id), false)

/** Persisted open count for adaptive section promotion. */
@Composable
fun rememberEditorSectionOpenCount(kind: String, id: String): MutableState<Int> =
    rememberPersistentUiIntSetting(editorSectionUsageKey(kind, id), 0)

/** Whether frequent use should promote a section above "More options". */
fun editorSectionIsAutoSurfaced(openCount: Int, hidden: Boolean): Boolean =
    !hidden && openCount >= EDITOR_SECTION_AUTO_SURFACE_THRESHOLD

/**
 * The default-expanded decision for a section, combining the compact-editor switch, the caller's
 * content-adaptive hint, whether the section already holds data, and the user's pin/hide choice.
 * When compact editors are off, everything is expanded (the old flat layout).
 */
@Composable
fun rememberEditorSectionDefaultExpanded(
    kind: String,
    id: String,
    hasValue: Boolean,
    adaptiveHint: Boolean,
): Boolean {
    val adaptiveEnabled = rememberAdaptiveEditorEnabled()
    val pinned by rememberEditorSectionPinned(kind, id)
    val hidden by rememberEditorSectionHidden(kind, id)
    val openCount by rememberEditorSectionOpenCount(kind, id)
    if (!adaptiveEnabled) return true
    if (hidden) return false
    return pinned || hasValue || adaptiveHint || editorSectionIsAutoSurfaced(openCount, hidden)
}

/** One attribute chip in the quick-bar. `id` must match the section id it reveals (for pin/hide). */
data class EditorQuickAttribute(
    val id: String,
    val title: String,
    val value: String?,
    val onReveal: () -> Unit,
    val icon: ImageVector? = null,
    val onClear: (() -> Unit)? = null,
)

@Composable
fun EditorQuickBar(
    kind: String,
    attributes: List<EditorQuickAttribute>,
    modifier: Modifier = Modifier,
) {
    if (attributes.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attributes.forEach { attr ->
            QuickAttributeChip(kind = kind, attr = attr)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAttributeChip(kind: String, attr: EditorQuickAttribute) {
    var pinned by rememberEditorSectionPinned(kind, attr.id)
    var hidden by rememberEditorSectionHidden(kind, attr.id)
    var menuOpen by remember { mutableStateOf(false) }
    val isSet = attr.value != null

    val container = if (isSet) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (isSet) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box {
        Surface(
            shape = RoundedCornerShape(50),
            color = container,
            contentColor = content,
            modifier = Modifier.combinedClickable(
                onClick = attr.onReveal,
                onLongClick = { menuOpen = true },
                onClickLabel = if (isSet) "Edit ${attr.title}" else "Set ${attr.title}",
                onLongClickLabel = "Pin or hide ${attr.title}",
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = ChronosSpacing.Compact, vertical = ChronosSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                attr.icon?.let {
                    Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                if (pinned && !isSet) {
                    // A small dot marks a pinned-but-empty attribute so the user sees their choice stuck.
                    Text("•", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = if (isSet) attr.value!! else attr.title,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (isSet && attr.onClear != null) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear ${attr.title}",
                        modifier = Modifier
                            .size(16.dp)
                            .combinedClickable(onClick = attr.onClear),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ChronosDropdownMenuItem(
                text = { Text(if (pinned) "Unpin" else "Pin open") },
                onClick = {
                    pinned = !pinned
                    if (pinned) hidden = false
                    menuOpen = false
                },
            )
            ChronosDropdownMenuItem(
                text = { Text(if (hidden) "Show by default" else "Hide unless needed") },
                onClick = {
                    hidden = !hidden
                    if (hidden) pinned = false
                    menuOpen = false
                },
            )
        }
    }
}
