package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// MARK: - Card editor scaffold
//
// Android counterpart to iOS `CardEditorScaffold`: essentials at the top, an optional attribute
// quick-bar, primary sections inline, and everything else grouped under "More options" when
// compact editors are enabled.

/** One collapsible field group in a card editor. `id` must match any [EditorQuickAttribute.id]. */
data class CardEditorSection(
    val id: String,
    val title: String,
    val summary: String,
    val alwaysPrimary: Boolean = false,
    val hasValue: Boolean = false,
    val adaptiveHint: Boolean = false,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * Whether a section renders above "More options" when compact editors are on. Mirrors iOS
 * `CardEditorScaffold.isPrimary`.
 */
internal fun cardEditorSectionIsPrimary(
    adaptiveEnabled: Boolean,
    section: CardEditorSection,
    sessionRevealed: Set<String>,
    pinned: Boolean,
    hidden: Boolean,
    autoSurfaced: Boolean = false,
): Boolean {
    if (!adaptiveEnabled) return true
    if (hidden) return false
    if (section.alwaysPrimary) return true
    if (sessionRevealed.contains(section.id)) return true
    if (section.hasValue) return true
    if (pinned) return true
    if (section.adaptiveHint) return true
    if (autoSurfaced) return true
    return false
}

internal fun cardEditorMoreOptionsSummary(count: Int, expanded: Boolean): String =
    when {
        expanded -> "Showing $count additional section${if (count == 1) "" else "s"}"
        count == 1 -> "1 more option"
        else -> "$count more options"
    }

/**
 * Count of sections that render under "More options". Uses the same [cardEditorSectionIsPrimary]
 * rules as [CardEditorSectionGate] so the label matches actual placement (including pinned,
 * auto-surfaced, and session-revealed sections).
 */
internal fun cardEditorEstimatedMoreSectionCount(
    compactEditors: Boolean,
    sections: List<CardEditorSection>,
    sessionRevealed: Set<String>,
    pinnedIds: Set<String> = emptySet(),
    hiddenIds: Set<String> = emptySet(),
    autoSurfacedIds: Set<String> = emptySet(),
): Int {
    if (!compactEditors) return 0
    return sections.count { section ->
        !cardEditorSectionIsPrimary(
            adaptiveEnabled = true,
            section = section,
            sessionRevealed = sessionRevealed,
            pinned = section.id in pinnedIds,
            hidden = section.id in hiddenIds,
            autoSurfaced = section.id in autoSurfacedIds,
        )
    }
}

/** Live more-section count that reads persisted pin/hide/usage for each section. */
@Composable
private fun cardEditorLiveMoreSectionCount(
    kind: String,
    compactEditors: Boolean,
    sections: List<CardEditorSection>,
    sessionRevealed: Set<String>,
): Int {
    if (!compactEditors) return 0
    return sections.sumOf { section ->
        CardEditorMoreSectionSlot(
            kind = kind,
            section = section,
            sessionRevealed = sessionRevealed,
        )
    }
}

@Composable
private fun rememberCardEditorSectionIsPrimary(
    kind: String,
    section: CardEditorSection,
    compactEditors: Boolean,
    sessionRevealed: Set<String>,
): Boolean {
    val pinned by rememberEditorSectionPinned(kind, section.id)
    val hidden by rememberEditorSectionHidden(kind, section.id)
    val openCount by rememberEditorSectionOpenCount(kind, section.id)
    val autoSurfaced = editorSectionIsAutoSurfaced(openCount, hidden)
    return cardEditorSectionIsPrimary(
        adaptiveEnabled = compactEditors,
        section = section,
        sessionRevealed = sessionRevealed,
        pinned = pinned,
        hidden = hidden,
        autoSurfaced = autoSurfaced,
    )
}

@Composable
private fun CardEditorMoreSectionSlot(
    kind: String,
    section: CardEditorSection,
    sessionRevealed: Set<String>,
): Int = if (
    rememberCardEditorSectionIsPrimary(
        kind = kind,
        section = section,
        compactEditors = true,
        sessionRevealed = sessionRevealed,
    )
) {
    0
} else {
    1
}

/**
 * Programmatically promotes and expands scaffold sections (assist apply, speech capture, etc.).
 * Pass the same instance to [CardEditorScaffold] via `revealController`.
 */
@Stable
class CardEditorRevealController {
    var pendingReveals by mutableStateOf(setOf<String>())
        private set

    fun reveal(id: String) {
        pendingReveals = pendingReveals + id
    }

    fun revealAll(ids: Collection<String>) {
        pendingReveals = pendingReveals + ids
    }

    /** Clears [pendingReveals] after the scaffold has applied them to session state. */
    fun consumePendingReveals() {
        if (pendingReveals.isNotEmpty()) {
            pendingReveals = emptySet()
        }
    }
}

@Composable
fun rememberCardEditorRevealController(stateKey: String): CardEditorRevealController =
    remember(stateKey) { CardEditorRevealController() }

@Composable
fun CardEditorScaffold(
    kind: String,
    stateKey: String,
    attributes: List<EditorQuickAttribute>,
    sections: List<CardEditorSection>,
    modifier: Modifier = Modifier,
    revealController: CardEditorRevealController? = null,
    essentials: @Composable () -> Unit,
) {
    val compactEditors = rememberAdaptiveEditorEnabled()
    var sessionRevealed by rememberSaveable(stateKey) { mutableStateOf(setOf<String>()) }
    var moreExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }

    val pendingReveals = revealController?.pendingReveals
    LaunchedEffect(pendingReveals) {
        pendingReveals?.takeIf { it.isNotEmpty() }?.let { ids ->
            sessionRevealed = sessionRevealed + ids
            revealController?.consumePendingReveals()
        }
    }

    fun revealSection(id: String) {
        sessionRevealed = sessionRevealed + id
    }

    val wrappedAttributes = attributes.map { attribute ->
        attribute.copy(
            onReveal = {
                revealSection(attribute.id)
                attribute.onReveal()
            }
        )
    }

    val estimatedMoreCount = cardEditorLiveMoreSectionCount(
        kind = kind,
        compactEditors = compactEditors,
        sections = sections,
        sessionRevealed = sessionRevealed,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        essentials()
        if (compactEditors && wrappedAttributes.isNotEmpty()) {
            EditorQuickBar(kind = kind, attributes = wrappedAttributes)
        }
        sections.forEach { section ->
            CardEditorSectionGate(
                kind = kind,
                stateKey = stateKey,
                section = section,
                compactEditors = compactEditors,
                sessionRevealed = sessionRevealed,
                expectPrimary = true,
            )
        }
        if (estimatedMoreCount > 0) {
            ChronosCollapsibleSection(
                title = "More options",
                summary = cardEditorMoreOptionsSummary(estimatedMoreCount, moreExpanded),
                expanded = moreExpanded,
                onExpandedChange = { moreExpanded = it },
            ) {
                sections.forEach { section ->
                    CardEditorSectionGate(
                        kind = kind,
                        stateKey = stateKey,
                        section = section,
                        compactEditors = compactEditors,
                        sessionRevealed = sessionRevealed,
                        expectPrimary = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardEditorSectionGate(
    kind: String,
    stateKey: String,
    section: CardEditorSection,
    compactEditors: Boolean,
    sessionRevealed: Set<String>,
    expectPrimary: Boolean,
) {
    val isPrimary = rememberCardEditorSectionIsPrimary(
        kind = kind,
        section = section,
        compactEditors = compactEditors,
        sessionRevealed = sessionRevealed,
    )
    if (isPrimary != expectPrimary) return

    CardEditorSectionBlock(
        kind = kind,
        stateKey = stateKey,
        section = section,
        sessionRevealed = sessionRevealed,
        forceExpanded = !compactEditors,
    )
}

@Composable
private fun CardEditorSectionBlock(
    kind: String,
    stateKey: String,
    section: CardEditorSection,
    sessionRevealed: Set<String>,
    forceExpanded: Boolean,
) {
    val defaultExpanded = rememberEditorSectionDefaultExpanded(
        kind = kind,
        id = section.id,
        hasValue = section.hasValue,
        adaptiveHint = section.adaptiveHint,
    )
    var expanded by rememberSaveable(stateKey, section.id) { mutableStateOf(defaultExpanded) }
    var openCount by rememberEditorSectionOpenCount(kind, section.id)

    LaunchedEffect(stateKey, section.id, defaultExpanded) {
        expanded = defaultExpanded
    }
    LaunchedEffect(sessionRevealed, section.id) {
        if (section.id in sessionRevealed) {
            expanded = true
        }
    }

    ChronosCollapsibleSection(
        title = section.title,
        summary = section.summary,
        expanded = forceExpanded || expanded,
        onExpandedChange = { next ->
            expanded = next
            if (next) {
                openCount = openCount + 1
            }
        },
        content = section.content,
    )
}
