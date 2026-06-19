package com.ChronosFlow.VBCR.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import com.ChronosFlow.VBCR.core.notifications.SECTION_DAY

/**
 * Hybrid Navigation 3 state for ChronosFlow (state holder + navigator combined, following the
 * androidx nav3 `TopLevelBackStack` recipe).
 *
 * The flattened [backStack] only models *cross-section* navigation: Day is always the root, and at
 * most one sub-section (Tasks / Habits / Goals / Medication) sits on top of it. The Day tab
 * (Plan / Today / Focus / Review) is **not** part of the back stack — those are in-place tabs inside
 * DayDialScreen, so the requested tab travels as UI state ([requestedDayTarget] +
 * [dayTargetGeneration]) while the Day [androidx.navigation3.runtime.NavEntry] stays stable
 * (preserving its composition and internal AnimatedContent animation).
 */
@Composable
fun rememberChronosNavigationState(startDayTarget: String? = null): ChronosNavigationState {
    // One back stack per top-level section. Day always anchors the bottom of the stack. The hybrid
    // stacks are depth ≤ 2 and deterministically rebuildable from topLevelSection, so the meaningful
    // state (topLevelSection / requestedDayTarget) is what we persist via rememberSaveable below.
    val sectionStacks = remember { newSectionStacks() }

    val topLevelSection = rememberSaveable { mutableStateOf(SECTION_DAY) }
    val requestedDayTarget = rememberSaveable { mutableStateOf(startDayTarget) }
    val requestedDayCapture = rememberSaveable { mutableStateOf<String?>(null) }
    // Saveable (unlike dayTargetGeneration) on purpose: a sub-section's one-shot add-sheet effect
    // is gated on this generation, so resetting it on config change / process death would re-fire
    // that effect and reopen the sheet on rotation. See [ChronosNavigationState.navigate].
    val sectionTargetGeneration = rememberSaveable { mutableIntStateOf(0) }

    return remember {
        ChronosNavigationState(
            sectionStacks = sectionStacks,
            topLevelSection = topLevelSection,
            requestedDayTarget = requestedDayTarget,
            requestedDayCapture = requestedDayCapture,
            dayTargetGeneration = mutableIntStateOf(0),
            sectionTargetGeneration = sectionTargetGeneration
        )
    }
}

/** Builds the per-section back stacks, each seeded with its base route. */
private fun newSectionStacks(): Map<String, SnapshotStateList<NavKey>> = linkedMapOf(
    SECTION_DAY to mutableStateListOf<NavKey>(ChronosRoute.Day()),
    ChronosRoute.Tasks.section to mutableStateListOf<NavKey>(ChronosRoute.Tasks()),
    ChronosRoute.Habits.section to mutableStateListOf<NavKey>(ChronosRoute.Habits()),
    ChronosRoute.Goals.section to mutableStateListOf<NavKey>(ChronosRoute.Goals()),
    ChronosRoute.Medication.section to mutableStateListOf<NavKey>(ChronosRoute.Medication())
)

/** Test-only factory: builds a fully-functional state holder without a Compose composition. */
@androidx.annotation.VisibleForTesting
internal fun createChronosNavigationStateForTest(startDayTarget: String? = null): ChronosNavigationState =
    ChronosNavigationState(
        sectionStacks = newSectionStacks(),
        topLevelSection = mutableStateOf(SECTION_DAY),
        requestedDayTarget = mutableStateOf(startDayTarget),
        requestedDayCapture = mutableStateOf(null),
        dayTargetGeneration = mutableIntStateOf(0),
        sectionTargetGeneration = mutableIntStateOf(0)
    )

class ChronosNavigationState internal constructor(
    private val sectionStacks: Map<String, SnapshotStateList<NavKey>>,
    topLevelSection: androidx.compose.runtime.MutableState<String>,
    requestedDayTarget: androidx.compose.runtime.MutableState<String?>,
    requestedDayCapture: androidx.compose.runtime.MutableState<String?>,
    dayTargetGeneration: androidx.compose.runtime.MutableState<Int>,
    sectionTargetGeneration: androidx.compose.runtime.MutableState<Int>
) {
    var topLevelSection: String by topLevelSection
        private set
    var requestedDayTarget: String? by requestedDayTarget
        private set
    var requestedDayCapture: String? by requestedDayCapture
        private set
    var dayTargetGeneration: Int by dayTargetGeneration
        private set

    /**
     * Bumped on every sub-section [navigate]. A sub-section's transient target/capture (e.g. the
     * "add" sheet request) rides in its `NavKey`, so re-navigating to the same target yields an
     * equal key that `NavDisplay` treats as no change — the screen would never re-consume it.
     * Screens key their one-shot add-sheet effect on this generation to re-fire it anyway, the
     * same way `DayDialScreen` uses [dayTargetGeneration] for in-place Day tabs.
     */
    var sectionTargetGeneration: Int by sectionTargetGeneration
        private set

    /** The single flattened back stack rendered by [androidx.navigation3.ui.NavDisplay]. */
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf<NavKey>().also {
        rebuild(it)
    }

    /** The route currently on top of the active stack. */
    val currentRoute: ChronosRoute?
        get() = sectionStacks[topLevelSection]?.lastOrNull() as? ChronosRoute

    /**
     * Navigate to [route]. Day targets switch to the Day section and surface the tab in-place;
     * every other route replaces its section's stack head and brings that section to the top.
     */
    fun navigate(route: ChronosRoute) {
        when (route) {
            is ChronosRoute.Day -> {
                topLevelSection = SECTION_DAY
                requestedDayTarget = route.target
                requestedDayCapture = route.capture
                dayTargetGeneration += 1
            }
            else -> {
                val stack = sectionStacks.getValue(route.section)
                stack.clear()
                stack.add(route)
                topLevelSection = route.section
                // Re-navigating to the same sub-section target (e.g. tapping "New task" again while
                // already on Tasks) yields a NavKey equal to the one already on the stack, which
                // NavDisplay sees as no change. Bump a generation so the destination can re-fire its
                // one-shot target (open-add-sheet) even when the key is unchanged.
                sectionTargetGeneration += 1
            }
        }
        rebuild(backStack)
    }

    /** Surface a Day tab/target without leaving (or re-entering) the back stack. */
    fun selectDayTarget(target: String?, capture: String? = null) {
        topLevelSection = SECTION_DAY
        requestedDayTarget = target
        requestedDayCapture = capture
        dayTargetGeneration += 1
        rebuild(backStack)
    }

    /** Handle a system back event. Returns to Day from any sub-section; Day is the exit root. */
    fun goBack() {
        if (topLevelSection != SECTION_DAY) {
            val stack = sectionStacks.getValue(topLevelSection)
            stack.clear()
            stack.add(baseRouteFor(topLevelSection))
            topLevelSection = SECTION_DAY
            rebuild(backStack)
        }
    }

    /** True when a back event has somewhere to go (i.e. we are not at the Day root). */
    val canGoBack: Boolean
        get() = topLevelSection != SECTION_DAY

    private fun rebuild(target: SnapshotStateList<NavKey>) {
        target.clear()
        sectionStacks.getValue(SECTION_DAY).lastOrNull()?.let(target::add)
        if (topLevelSection != SECTION_DAY) {
            sectionStacks[topLevelSection]?.lastOrNull()?.let(target::add)
        }
    }

    private fun baseRouteFor(section: String): NavKey =
        when (section) {
            ChronosRoute.Tasks.section -> ChronosRoute.Tasks()
            ChronosRoute.Habits.section -> ChronosRoute.Habits()
            ChronosRoute.Goals.section -> ChronosRoute.Goals()
            ChronosRoute.Medication.section -> ChronosRoute.Medication()
            else -> ChronosRoute.Day()
        }
}
