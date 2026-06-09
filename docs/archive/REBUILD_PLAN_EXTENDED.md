# ChronosFlow Rebuild Blueprint (Online IDE Edition)

This is an implementation blueprint for rebuild/porting work and should be treated as historical or planning context, not current release state. For current status, use `README.md` and `docs/product-contract.md`.

## Objective

Rebuild ChronosFlow from a clean codebase into a **focus-first, dial-first time operating system** with a minimal visible product surface and strict feature gates for advanced capabilities.

Primary visible flow in Phase 1:

- `Today` (Core dial + active blocks)
- `Plan` (Inbox + scheduling)
- `Focus` (Timer + session lifecycle)
- `Launcher` (global command palette)

Everything else (`Habits`, `Medication`, `Review`, deeper `AI`) starts as parked but architecture-ready features.

---

## How to use this plan

Treat each phase as a **single PR-sized chunk** with a done gate before moving forward:

1. Implement only current phase scope.
2. Run the phase compile/test checklist.
3. Fix and stabilize.
4. Tag a clean checkpoint (`git commit` if your setup supports it).
5. Move to next phase.

You can copy this file into the root of a brand-new repo. Each phase explicitly lists:

- Files/folders to create
- Core implementation steps
- Verification commands
- Exit criteria

---

## Environment assumptions for online IDE

- Android Studio Flamingo+ or equivalent IDE with Kotlin + Compose plugins
- JDK 17
- Kotlin 2.0+ (or matching your Compose plugin)
- Android SDK with at least API 34 installed

Recommended baseline:

- `minSdk = 26`
- `compileSdk = 36` (or latest stable in your IDE)
- `targetSdk = 36`
- Java/Kotlin toolchain set to 17

---

## Phase 0 — Scope lock + product contract (no code)

Create a concise contract doc (`docs/product-contract.md`) before writing code:

- **Value proposition:** Time becomes the organizing primitive.
- **Primary workflows:**
  1. Create tasks in Plan/Inboxes.
  2. Map tasks to time blocks.
  3. Focus within scheduled blocks.
  4. Review outcome at end of day.
- **Visible surfaces (Phase 1):**
  - Today, Plan, Focus, Launcher.
- **Parked surfaces (Phase 2+):**
  - Habits, Medication, Review, AI auto actions.
- **Hard constraints:**
  - Single app shell, no duplicated nav/header.
  - No automatic AI writes without explicit user approval.
  - All flows must preserve data integrity after app process death.

### Exit criteria

- [x] `README_MINIMAL.md` with scope + out-of-scope list created.
- [x] Feature matrix marked as `MVP`, `Phase 2`, `Phase 3`.
- [x] Acceptance criteria written for each major flow.

---

## Phase 1 — Project bootstrap and module topology

### 1.1 Create empty modules

Create modules:

- `:app`
- `:core:design`
- `:core:domain`
- `:core:data`
- `:core:ui`
- `:feature:daydial`
- `:feature:tasks`
- `:feature:focus`
- Optional but preplanned: `:feature:habits`, `:feature:medication`, `:feature:review`, `:feature:ai`

### 1.2 Configure Gradle

At repo root add/verify:

- `settings.gradle.kts`
- `gradle/libs.versions.toml`
- `build.gradle.kts` (or pluginManagement setup)
- `gradle.properties`
- Version catalogs for:
  - Kotlin
  - Compose BOM
  - Material3
  - Room
  - Coroutines / Flow
  - Hilt or Koin (choose one DI stack, do not mix)
  - WorkManager
  - Navigation
  - Lifecycle / ViewModel
  - Testing libs

### 1.3 Baseline compile check

Run:

```bash
./gradlew :app:assembleDebug
```

### Exit criteria

- [x] Project syncs through the Android/Gradle project model
- [x] `:app:assembleDebug` succeeds for the current Today/Plan/Focus shell
- [x] No extra modules are imported from old code

---

## Phase 2 — Theme and visual language (detailed design system)

This is the required phase before building major screens so visual consistency is not retrofitted later.

Create in `:core:design/src/main/java/com/chronosflow/core/design/`:

### Colors (`Color.kt`)

- `ChronosBrand` fallback palette (light and dark)
- Semantic aliases:
  - `surface`, `surfaceVariant`, `container`, `onContainer`, `onSurface`
  - `success`, `warning`, `error`, `outline`, `focus`
  - `dialRingCalendar`, `dialRingPlan`, `dialRingInner`, `nowHand`
- Theme dynamic-color adapter with static fallback

### Type (`Type.kt`)

Define consistent set:

- `displayLarge`, `headlineSmall`
- `titleLarge`, `titleMedium`
- `bodyLarge`, `bodyMedium`, `bodySmall`
- `labelLarge`, `labelSmall`

### Shapes (`Shape.kt`)

- Standard radii: 8dp
- Emphasis elements: up to 18dp
- Card and panel tokens separated

### Spacing (`Spacing.kt`)

- `0,4,8,12,16,20,24,28,32` dp set
- Dedicated horizontal/vertical row/section spacing constants

### Theme wrapper (`ChronosTheme.kt`)

Implement:

- `ChronosTheme(useDynamicColor: Boolean = true, darkTheme: Boolean = isSystemInDarkTheme())`
- `CompositionLocal` providers for spacing / tonal system
- Dynamic color path:
  - if device/API supports, use `dynamicDarkColorScheme`/`dynamicLightColorScheme`
  - else use local fallback palette

### Shared components (`components/`)

Implement reusable atoms in `core/design`:

- `ChronosCard`
- `ChronosGlassPanel` or `ChronosSurface`
- `ChronosPrimaryButton`, `ChronosGhostButton`
- `ChronosPillButton`
- `ChronosSegmentedPill`
- `ChronosTopBar`
- `ChronosFloatingNav`
- `ChronosStatChip`
- `ChronosEmptyState`

### Theme validation screen (`:app` or `:core:design` preview)

Create a simple composable screen that renders:

- color chips for all semantic roles
- type scale samples
- spacing/reference examples
- button/field states
- dark + light + dynamic toggle

### Exit criteria

- [x] App launches with theme wrapper and no crash
- [x] Dynamic color toggle is wire-tested
- [x] No hard-coded colors in core UI

## UI DETAIL ADDENDUM — Screen + component contracts

This addendum is mandatory for every screen build. Keep it as the UI acceptance source, not the vague “make it look good” notes.

### Rule: one shell, one flow owner

- Only `app` owns global shell chrome.
- `feature` screens only receive `PaddingValues`, top content slots, and callbacks.
- Duplicate scaffolds are rejected if they define their own bottom navigation or top app bar.
- If the current screen already handles insets and safe areas, child modules must use content slots only.

---

## Screen blueprint: Today (Primary)

### Layout

1. **Header strip (72dp max):**
   - Left: date chip (`Today`, `Tomorrow`, or selected date)
   - Center: section title and live now time
   - Right: command launcher icon + optional notifications dot
2. **Summary strip (72-108dp):**
   - active block title
   - occupancy metric (`used/free`)
   - focus status chip
3. **Dial canvas area (~62% of visible height on phone portrait):**
   - middle alignment
   - `Now hand`
   - outer/middle/inner rings
4. **Bottom strip:**
   - floating pill with Today/Plan/Focus
   - each destination 48dp min hit target

### States

- `EMPTY_DAY`  
  show `ChronosEmptyState("No schedule yet")` + primary CTA: `Add task`
- `LOADING`
  skeleton dial blocks + progress shimmer
- `HAS_DATA`
  render blocks, focus marker, active summary
- `CONFLICT_MODE`
  conflicting blocks ringed in warning color with inline legend

### Interaction requirements

- Tap block => open block detail sheet.
- Drag => move block, snap by 5 minutes, block cannot cross midnight.
- Drag preview color:
  - green: valid
  - yellow: partial overlap (requires resolve)
  - red: blocked by hard conflict
- Long-press => quick action menu (Edit / Split / Delete / Copy to Next Day)
- Drag cancel gesture => restore previous committed block

### Component list to build first

- `ChronosDialCanvas`
- `DialArcPainter`
- `NowHandRenderer`
- `RingHitTester`
- `DialSummaryCard`
- `BlockSelectionBadge`
- `DialConflictOverlay`

---

## Screen blueprint: Plan (Primary)

### Layout

1. **Plan top action bar (64dp):**
   - global search
   - filter chips
   - sort button
2. **Inbox list:**
   - each row includes task title, due label, priority dot
   - row action set: complete, schedule
3. **Sticky quick-add input:**
   - single input + micro action (`+`)
4. **Bottom action strip:**
   - floating pill nav only (no extra bars)

### States

- `EMPTY_INBOX`: helper text + quick add CTA
- `SEARCH`: result highlight + keyboard aware list shrink
- `SCHEDULING`: block sheet overlay from bottom
- `VALIDATING`: optimistic disabled state before conflict check complete
- `ERROR`: inline retry bar (non-blocking toast + action)

### Schedule sheet contract

- Inputs:
  - title (required)
  - duration slider (15, 30, 60 presets + custom)
  - start time / end time chips
  - conflict banner area
- Buttons:
  - `Cancel`
  - `Preview`
  - `Apply schedule` (disabled on conflict unless overridden)
- On save:
  - create/update block transaction
  - link task atomically
  - emit success snackbar + close sheet

### Component list to build first

- `TaskInboxList`
- `TaskRow`
- `TaskFilterChips`
- `TaskSearchBar`
- `ScheduleBlockSheet`
- `ConflictPreviewList`

---

## Screen blueprint: Focus (Primary)

### Layout

1. **Top status row**
   - active block title
   - target duration
   - auto-stop flag
2. **Timer surface**
   - large circular progress ring
   - minute/second display
3. **Control row**
   - Start/Pause (primary)
   - Stop (secondary)
4. **Progress metadata**
   - started at
   - interrupted count
   - projected completion

### Focus state machine

- `IDLE`: no block selected → action requires block choose
- `ARMED`: countdown configured, waiting for start
- `RUNNING`: service active + foreground notification
- `PAUSED`: persisted remaining time
- `DONE`: session written, summary sheet opens
- `ERROR`: notification permission / service failure

### Interaction requirements

- Resume from background must recover remaining duration from persisted timestamps.
- Completing while app in background must still write a single `FocusSession`.
- Session lifecycle events should emit one user-facing snackbar at state changes.

### Component list to build first

- `FocusTimerScreen`
- `FocusTimerDial`
- `FocusControlRow`
- `FocusStateNotice`
- `FocusServiceConnection`

---

## Command palette (Global, mandatory)

### Visual behavior

- Opens on icon tap or `Ctrl+K`
- Max width: 760dp; centered on large screens
- Search debounce: 150ms
- Empty state message: "No matches"

### Command model

- Section tags:
  - `Navigation`
  - `Actions`
  - `Tasks`
  - `Settings`
- Sorting:
  - destination exact match first
  - recent commands boosted
  - contextual actions boosted (Today/Plan/Focus)

### Component list

- `LauncherDialog`
- `LauncherSearchField`
- `LauncherResultList`
- `LauncherShortcutRow`
- `LauncherKeyboardHint`

---

## Drawer / settings screen (Secondary)

### Drawer sections

1. Core
2. Tools
3. Developer (debug only)

### Interaction rules

- grouped menus with one level max in phase 1
- destructive actions always confirm
- switches and actions aligned to 16dp baseline
- no long explanatory paragraphs in side menu rows

### Settings must include at least

- Theme
- Focus default duration
- Default reminder window
- Permission status flags
- parked feature toggles

---

## Responsive and accessibility constraints

- Phone portrait: full-width dial with card spacing; list fills remaining area.
- Phone landscape: dial and summary arranged as top row; list or details in bottom row if available.
- Tablet: preserve center alignment; avoid oversized panels over 420dp width.
- Touch target minimum: 48dp.
- Every interactive icon has label.
- Contrast checks for text over all semantic background tokens.
- Focus order must follow reading order.

### Accessibility acceptance

- At least one `Semantics` test for selected-state announcement.
- `TalkBack`/screen reader can navigate all command and schedule controls.

---

## Phase gates updated with UI deliverables

### Phase 2 must include

- component library composables with preview screenshots per token
- theme preview route
- no hardcoded dimensions for global spacing

### Phase 5 must include

- shell-only chrome implementation
- launcher available on all primary tabs
- bottom nav single instance (global ID check)

### Phase 6 must include

- dial ring rendering and ring-hit test cases
- block selection + summary render update in same compose frame

### Phase 7 must include

- Plan list and filter visual states
- schedule sheet UI states

### Phase 8 must include

- focus timer visual state transitions
- background resume behavior in UI indicator

---

## Phase 5–10 UI PROMPT PACK (use these exact checklists)

Use the following per-phase text in your implementation assistant prompts when generating code.

### Prompt pack for Phase 5 (Shell + navigation UI)

> "Build shell UI only. Create one top strip and one bottom floating pill with routes Today, Plan, Focus. No nested Scaffold or duplicate bar in child features. Use adaptive padding and insets. Add command palette host on the top strip and make it keyboard accessible with `Ctrl+K` and `Cmd+K`. Add shell state tests for route restore after process death."

### Prompt pack for Phase 6 (Today/Day dial UI)

> "Build today UI using fixed hierarchy: header, summary strip, dial canvas, bottom nav. Implement these states: EMPTY_DAY, LOADING, HAS_DATA, CONFLICT_MODE. Add dial rings with explicit radii and hit-testing by ring. Add block drag with 5-minute snap + conflict overlay color states. Add center summary card + selected block sheet entry animation. Keep composable names exactly: `ChronosDialCanvas`, `RingHitTester`, `DialSummaryCard`, `DialConflictOverlay`."

### Prompt pack for Phase 7 (Plan UI)

> "Build plan UI only. Provide top action bar, search, filter chips, sticky quick add, inbox list, and schedule bottom sheet. Cover state set: EMPTY_INBOX, SEARCH, SCHEDULING, VALIDATING, ERROR. Use rows with task title, due label, priority dot, and action buttons complete/schedule. Include conflict preview in scheduling sheet before commit."

### Prompt pack for Phase 8 (Focus UI)

> "Build focus UI only. Create top status strip, circular timer, control row, metadata row, and session summary path. Implement focus states `IDLE`, `ARMED`, `RUNNING`, `PAUSED`, `DONE`, `ERROR`. Add restore state path for background-to-foreground and a non-dismissible summary modal on DONE."

### Prompt pack for Phase 10+ (Secondary UI, hidden by flag)

> "Implement each secondary module behind a feature flag only. Keep route registration but hide from primary bottom nav. Add module screens in non-primary group only when enabled. Ensure style and spacing reuse existing `core:design` tokens and confirm no shell duplication."

---

## UI Implementation Matrix (exact files + components)

Use this matrix to generate the actual screens in order. Build each folder fully before moving to next phase.

### Phase 2 UI files (`:core:design`)

- `core/design/src/main/java/com/chronosflow/core/design/theme/Color.kt`
- `core/design/src/main/java/com/chronosflow/core/design/theme/Type.kt`
- `core/design/src/main/java/com/chronosflow/core/design/theme/Shape.kt`
- `core/design/src/main/java/com/chronosflow/core/design/theme/Spacing.kt`
- `core/design/src/main/java/com/chronosflow/core/design/theme/ChronosTheme.kt`
- `core/design/src/main/java/com/chronosflow/core/design/components/ChronosCard.kt`
- `core/design/src/main/java/com/chronosflow/core/design/components/ChronosSurface.kt`
- `core/design/src/main/java/com/chronosflow/core/design/components/ChronosTopBar.kt`
- `core/design/src/main/java/com/chronosflow/core/design/components/ChronosFloatingNav.kt`
- `core/design/src/main/java/com/chronosflow/core/design/components/ChronosPill.kt`
- `core/design/src/main/java/com/chronosflow/core/design/components/ChronosStateChip.kt`
- `core/design/src/main/java/com/chronosflow/core/design/components/ChronosButton.kt`
- `core/design/src/main/java/com/chronosflow/core/design/components/ChronosTag.kt`
- `core/design/src/main/java/com/chronosflow/core/design/components/ChronosEmptyState.kt`

### Phase 5 UI files (`:app`)

- `app/src/main/java/com/chronosflow/app/navigation/ChronosRoute.kt`
- `app/src/main/java/com/chronosflow/app/navigation/ChronosNavGraph.kt`
- `app/src/main/java/com/chronosflow/app/navigation/ChronosBottomBarState.kt`
- `app/src/main/java/com/chronosflow/app/ui/shell/ChronosAppShell.kt`
- `app/src/main/java/com/chronosflow/app/ui/shell/ChronosTopStrip.kt`
- `app/src/main/java/com/chronosflow/app/ui/shell/ChronosCommandHost.kt`
- `app/src/main/java/com/chronosflow/app/ui/shell/BottomNavState.kt`
- `app/src/main/java/com/chronosflow/app/ui/shell/ChronosDrawer.kt`
- `app/src/main/java/com/chronosflow/app/ui/shell/PaletteShortcut.kt`
- `app/src/main/java/com/chronosflow/app/ui/shell/ChronosShellState.kt`
- `app/src/main/java/com/chronosflow/app/ui/shell/ChronosShellViewModel.kt`
- `app/src/main/res/values/strings.xml` (route/label constants for visible nav only)
- `app/src/main/java/com/chronosflow/app/ui/previews/AppThemePreview.kt`

### Phase 6 UI files (`:feature:daydial`)

- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialRoute.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialScreen.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/DayDialTopStrip.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/TodaySummaryStrip.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/ChronosDialCanvas.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/ChronosDialGeometry.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/BlockSelectionBadge.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/DialSummaryCard.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/DialConflictOverlay.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/sheets/BlockActionSheet.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/sheets/ScheduleFromDialSheet.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/model/DayDialUiModels.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialState.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialViewModel.kt`
- `feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialViewModelFactory.kt`

### Phase 7 UI files (`:feature:tasks`)

- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TasksRoute.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/PlanScreen.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/ui/PlanTopBar.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/ui/TaskSearchBar.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/ui/TaskFilterChips.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/ui/TaskInboxList.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/ui/TaskRow.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/ui/QuickAddTaskBar.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/ui/TaskQuickActionSheet.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/ui/ConflictPreviewList.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TaskState.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TasksViewModel.kt`
- `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TasksCommands.kt`

### Phase 8 UI files (`:feature:focus`)

- `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusRoute.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusScreen.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/ui/FocusTopStatusBar.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/ui/FocusTimerDial.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/ui/FocusControlRow.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/ui/FocusMetaRow.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/ui/FocusCompletionSheet.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/ui/FocusErrorPanel.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusState.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusViewModel.kt`
- `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusServiceBinding.kt`

### Cross-cutting UI tests (by phase)

- `app/src/androidTest/.../ChronosShellStateRestoreTest.kt`
- `app/src/androidTest/.../CommandPaletteKeyboardTest.kt`
- `feature/daydial/src/androidTest/.../DialInteractionUiTest.kt`
- `feature/tasks/src/androidTest/.../PlanScheduleFlowUiTest.kt`
- `feature/focus/src/androidTest/.../FocusLifecycleUiTest.kt`
- `app/src/androidTest/.../ThemeContrastAccessibilityTest.kt`

### Per-phase UI test names

- Phase 2: `ColorSemanticsPreviewSnapshotTest`, `ThemeTokenSmokeTest`
- Phase 5: `ShellChromeSingleInstanceTest`, `RouteRestoreAfterRecreateTest`
- Phase 6: `DialRenderStateTest`, `DialDragConflictTest`, `RingHitTest`
- Phase 7: `TaskListStatesTest`, `ScheduleSheetValidationTest`, `TaskToBlockCommitTest`
- Phase 8: `FocusStateMachineTest`, `FocusBackgroundResumeTest`, `FocusServiceStopStartTest`

### Motion and transition contract

Use the following transition rules to avoid jarring interaction:

- Enter/exit drawer or launcher: `200ms`, `easeOutCubic`
- Sheet show/hide: `180ms`, `easeOutQuart` with `alpha + vertical translate`
- Route change fade: `120ms` crossfade for core tabs
- Dial selection change: `80ms` spring (low damping) for summary card + selection badge
- Focus state change from running to paused/stopped: `150ms` animated color/label transition

### Input and validation feedback contract

- If a block move is invalid, keep block at original position and display:
  - inline `Text` warning (`Unable to place here due to conflict`)
  - haptic soft tick (if enabled)
- If sheet save fails:
  - keep sheet open
  - show inline retry action
  - do not lose draft form state
- On service start failure:
  - show modal non-blocking error with direct CTA `Try again` + `Open permissions`
- Any network/IO failure in local app must show explicit human message, no silent catch.

### Empty-state quality bar

- Empty-state copy must be actionable, not apologetic.
- Every empty card must include:
  - primary CTA
  - one secondary helpful hint
  - route back link (if user might be stranded)
- Example format:
  - `Title`: `No plan for today`
  - `Body`: `Add your first task to begin`
  - `Primary`: `Add task`
  - `Secondary`: `View templates`

### Component naming and composition rules

- Name every composable by function (`TodayHeaderStrip`, `TaskFilterChips`, `FocusStateNotice`).
- Keep screen-specific composables under feature module’s `ui` package.
- Reuse `ChronosStateChip`, `ChronosCard`, `ChronosTag`, and `ChronosSurface`.
- Do not put business logic in composables above `ViewModel`/`UseCase` boundaries.

---

## UI State + Event Contracts (must be implemented before UI files in Phase 5–8)

To avoid drift, write the state layer first, then view-model wiring, then UI rendering.

### Shared envelope used by all UI view models

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Empty(val message: String, val cta: UiCta?) : UiState<Nothing>
    data class Data<T>(val value: T) : UiState<T>
    data class Error(val message: String, val recoverable: Boolean, val retry: Boolean) : UiState<Nothing>
}

data class UiCta(val label: String, val action: UiAction)
```

`UiAction` should be module-local commands shared only as callbacks, never a sealed supertype in domain.

### Today (DayDial) screen contract

Create:

```kotlin
data class TodayUiState(
    val date: LocalDate,
    val nowMinute: Int,
    val blocks: List<UiTimeBlock>,
    val activeBlockId: String?,
    val conflictIds: Set<String>,
    val selection: SelectedBlock?,
    val routeState: UiRouteState,
    val focus: FocusSummary?,
    val isLauncherOpen: Boolean,
    val activeSheet: TodaySheetTarget,
    val isLoading: Boolean,
    val userMessage: String?
)

sealed interface TodayAction {
    data class OnDateChanged(val date: LocalDate) : TodayAction
    data class OnBlockTap(val blockId: String) : TodayAction
    data class OnBlockDrag(val blockId: String, val startMinute: Int, val endMinute: Int) : TodayAction
    data class OnBlockDragPreview(val blockId: String, val startMinute: Int, val endMinute: Int) : TodayAction
    data class OnBlockDragCommit(val blockId: String, val startMinute: Int, val endMinute: Int) : TodayAction
    data class OnBlockDragCancel(val blockId: String) : TodayAction
    data class OnOpenSheet(val target: TodaySheetTarget) : TodayAction
    data class OnSheetAction(val command: TodaySheetCommand) : TodayAction
    data object OnOpenPalette : TodayAction
    data object OnClearMessage : TodayAction
}
```

Render rules:

- If `isLoading == true`, show `Loading`.
- Else if `blocks.isEmpty()`, show `Empty`.
- Else show `Data`.
- `conflictIds` must drive warning colors for `DialConflictOverlay`.

### Today sheet/action enums

- `TodaySheetTarget`: `None`, `BlockMenu`, `ScheduleFromDial`, `BlockRename`, `ConflictResolve`.
- `TodaySheetCommand`: `Edit`, `Split`, `DuplicateNextDay`, `Delete`, `ConfirmConflictOverride`, `Cancel`.

### Plan screen contract

```kotlin
data class PlanUiState(
    val date: LocalDate,
    val query: String,
    val activeFilter: PlanFilter,
    val tasks: List<UiTask>,
    val selectedTaskId: String?,
    val sortDescending: Boolean,
    val isSearching: Boolean,
    val sheetState: PlanSheetState,
    val isSubmittingSchedule: Boolean,
    val conflicts: List<TimeConflict>,
    val canApplySchedule: Boolean
)

enum class PlanFilter { All, Today, Later, Incomplete, Completed }

data class PlanSheetState(
    val isOpen: Boolean,
    val sourceTaskId: String?,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val durationMinutes: Int,
    val conflictSeverity: ConflictSeverity,
    val isValid: Boolean,
)

sealed interface PlanAction {
    data object OnOpenSearch : PlanAction
    data class OnQueryChanged(val query: String) : PlanAction
    data class OnFilterChanged(val filter: PlanFilter) : PlanAction
    data class OnToggleSort(val descending: Boolean) : PlanAction
    data class OnAddTask(val title: String) : PlanAction
    data class OnCompleteTask(val taskId: String) : PlanAction
    data class OnOpenSheetForTask(val taskId: String) : PlanAction
    data class OnUpdateTime(val startMinute: Int, val endMinute: Int) : PlanAction
    data class OnApplySchedule(val taskId: String, val startMinute: Int, val endMinute: Int) : PlanAction
    data class OnDeleteTask(val taskId: String) : PlanAction
    data class OnResolveConflict(val allowOverride: Boolean) : PlanAction
    data object OnDismissSheet : PlanAction
}
```

Rules:

- Search + filter are mutually applied (`query` first, then `activeFilter`).
- Schedule `OnApplySchedule` cannot fire when `isSubmittingSchedule` true.
- `TaskRow` complete action uses optimistic update and reverses on persistence failure.

### Focus screen contract

```kotlin
data class FocusUiState(
    val selectedBlockId: String?,
    val activeBlockTitle: String,
    val blockStartMinute: Int,
    val blockEndMinute: Int,
    val configuredDurationMinutes: Int,
    val remainingSeconds: Long,
    val state: FocusLifecycleState,
    val elapsedSeconds: Long,
    val interruptionCount: Int,
    val lastEventMessage: String?,
    val showSummary: Boolean,
    val permissionGranted: Boolean,
)

enum class FocusLifecycleState { IDLE, ARMED, RUNNING, PAUSED, DONE, ERROR }

sealed interface FocusAction {
    data object OnLoadBlock : FocusAction
    data class OnPickBlock(val blockId: String) : FocusAction
    data object OnStartTimer : FocusAction
    data object OnPauseTimer : FocusAction
    data object OnResumeTimer : FocusAction
    data object OnStopTimer : FocusAction
    data class OnTimerTick(val remainingSeconds: Long) : FocusAction
    data object OnRestoreFromService : FocusAction
    data class OnSummaryHandled(val accepted: Boolean) : FocusAction
    data class OnOpenPermissions : FocusAction
}
```

Rules:

- `OnStartTimer` only valid when state in `IDLE | ARMED`.
- `OnPauseTimer` only valid when `RUNNING`.
- `RUNNING`/`PAUSED` must persist service timestamp and remaining duration.
- `DONE` must open non-dismissible summary sheet until user confirms.

### Command palette contract

```kotlin
data class CommandUiState(
    val query: String,
    val isOpen: Boolean,
    val selectedSection: String?,
    val maxResults: Int = 12,
    val results: List<LauncherCommand>,
    val isExecuting: Boolean
)

data class LauncherCommand(
    val id: String,
    val label: String,
    val section: LauncherSection,
    val metadata: String? = null,
    val shortcut: String? = null,
    val enabled: Boolean = true
)

enum class LauncherSection { Navigation, Actions, Tasks, Settings }

sealed interface LauncherAction {
    data class OnQueryChanged(val text: String) : LauncherAction
    data class OnExecute(val commandId: String) : LauncherAction
    data object OnOpen : LauncherAction
    data object OnClose : LauncherAction
    data object OnClearHistory : LauncherAction
}
```

### Hard validation gate per phase

Before UI compile in each phase, verify:

- [x] State class compiles independently.
- [x] Action interface includes every user interaction in the wireframe.
- [x] Every sheet state has explicit `open/close/submit` transitions.
- [x] `StateFlow<UiState<...>>` is the only source of truth for screen.

### Build order rule for each phase

For each feature phase:

1. Create state + action types.
2. Create ViewModel and emit flows.
3. Add route + screen.
4. Add child composables.
5. Add preview + tests.

---

## Strict Execution Prompts (for a fresh IDE, one-shot generation)

Use these prompts exactly when you ask an assistant/code model to implement each phase. Keep the order fixed and do not implement a later phase before the previous phase has all exit criteria checked.

### Global starter prompt (before every phase)

> "You are implementing a brand-new Android app in this clean repo. Do not reference or copy old source. Build only the files listed for this phase. Keep architecture: `app` owns shell, `feature` modules own screens, `core:domain` owns business rules, `core:data` owns persistence. Do not add parked features to primary nav. After edits, include exact compile/test commands and the failed command outputs if anything fails."

### Phase 0–2 UI bootstrapping prompt

> "Implement Phases 0, 1, 2 only. Create contract docs and modules first, then design primitives and components. Do not create feature UI interactions yet. Enforce single source of spacing/colors/typography in `core:design`. Add a quick theme preview route only (no app-level nav). Verify with `./gradlew :app:assembleDebug` and return the exit code/output."

### Phase 3–4 domain/data foundation prompt

> "Implement domain contracts and Room-backed data layer only (Phases 3 and 4). Create interfaces in `core:domain`, entities/DAOs/repositories/migrations in `core:data`, and domain/data tests. Do not create UI or ViewModels yet. Ensure domain is platform-agnostic and data is Android-only. Validate with `./gradlew :core:domain:test :core:data:test` and stop on first failing test."

### Phase 5 shell-and-navigation execution prompt (critical)

> "Implement Phase 5 only. Build the app shell, single navigation graph, and launcher command host with one top strip and one floating bottom pill only. No feature-module bars, no duplicate scaffold. Route set visible: Today/Plan/Focus only; park others in hidden routes. Add state restore test and route-recreate test. Validate with `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest --tests *Shell*`."

### Phase 6–8 screens execution prompt

> "Implement Phases 6–8 with strict file-by-file order from matrix: state first, ViewModel second, route+screen third, child components fourth, tests last. For each feature, wire to domain/data contracts only through repositories and use-cases. Do not invent persistence in UI. Verify each phase separately in sequence: `./gradlew :feature:daydial:assembleDebug`, `./gradlew :feature:tasks:assembleDebug`, `./gradlew :feature:focus:assembleDebug` and corresponding UI tests."

### Phase 9–12 finish prompt

> "Implement settings + parked modules behind flags (Phases 9–10), then full hardening matrix and release artifacts (Phases 11–12). Keep parked modules unlinked from visible nav unless flags enabled. Preserve one-shell rule and all contracts in this plan. Validate every changed module tests and finish with `./gradlew test` plus release check docs."

### "No-go" checks embedded in every phase prompt

- Never edit file outside current phase scope.
- Never rename a file without updating imports in all impacted modules in same phase.
- Never add network calls for any feature in Phases 0–10.
- Never bypass validation gates; do not mark a phase done without test evidence.
- Never add feature flags that alter nav without shell wiring.

### Post-phase verification pattern

For each phase, append:

1. Diff summary by file path.
2. Exact pass/fail of required compile command.
3. 3–5 lines of the UI/runtime behavior observed (or emulator log if available).
4. Explicit list of unresolved risks.

---

## Phase 3 — Domain layer and business contracts

Create use-case-level contracts in `:core:domain` before any feature implementation.

### Models

- `TimeOfDay`
- `MinuteOfDay` (0..1439 range helper, clamp + validation)
- `TimeRange(startMinute: Int, endMinute: Int, date: LocalDate)`
- `TimeBlock`
- `TaskItem`
- `FocusSession`
- `TemplateSuggestion`
- `DailyReview`

### Invariants and rules

- `start < end`
- no negative/overflow durations
- conflict detection rules:
  - overlapping blocks of same date and same priority domain
  - allow override only through explicit API and user confirmation
- timezone-safe date handling (`ZoneId`, `LocalDate`, `LocalDateTime`)

### Use-cases

- `CreateTimeBlockUseCase`
- `MoveTimeBlockUseCase`
- `ResizeTimeBlockUseCase`
- `DeleteTimeBlockUseCase`
- `LinkTaskToBlockUseCase`
- `StartFocusSessionUseCase`
- `EndFocusSessionUseCase`
- `GeneratePlanTemplateUseCase` (advisory only)

### Testing in domain

- Unit tests for:
  - overlap / edge adjacency
  - midnight edge behavior
  - conflict priority path
  - DST-like date boundary transitions

### Exit criteria

- [x] All domain use-cases have deterministic tests
- [x] Domain compiles without Android dependencies
- [x] No feature logic in `:app` or UI modules

---

## Phase 4 — Data layer and persistence

### 4.1 Database primitives (`:core:data`)

Define:

- `ChronosDatabase`
- `TimeBlockEntity`
- `TaskEntity`
- `FocusSessionEntity`
- `ReviewEntity` (simple)
- `AppSettingsEntity` (theme/feature flags/preferences)

### 4.2 DAOs

- `TimeBlockDao`:
  - observe by date
  - observe date ranges
  - insert/update/delete transactionally
- `TaskDao`
- `FocusSessionDao`

### 4.3 Repositories

`domain` provides repository interfaces; `data` provides Room implementations and mappers.

### 4.4 Migrations

Ship at least:

- `1->2` schema migration
- `2->3` nullable field migration (safe defaults)

### 4.5 Repository tests

- CRUD stream tests
- migration integrity tests
- transaction safety for task-link operations

### Exit criteria

- [x] All mappers are pure and unit-testable
- [x] Migrations tested locally
- [x] Mock seeding only in `debug` variant

---

## Phase 5 — App shell, navigation, command launcher

### 5.1 Navigation core (`:app`)

Create:

- `navigation/ChronosRoute.kt` (sealed routes)
- `navigation/ChronosNavGraph.kt`
- `navigation/ChronosBottomNav.kt`

Visible routes:

- `Today`
- `Plan`
- `Focus`

Hidden routes:

- `Habits`, `Medication`, `Review`, `AiAdvisor`

### 5.2 Shell rules

- One nav bar at app level.
- One top bar, no duplicate bottom bars from child screens.
- Central state object:
  - selected date
  - active route
  - active sheet target
  - focus session summary

### 5.3 Command palette entry

- Launcher icon + `Ctrl+K`/`Cmd+K` action
- Commands include:
  - add task
  - go to today
  - open focus
  - open day plan
  - search tasks

### 5.4 Exit criteria

- [x] Back stack remains stable after rotate/process death
- [x] Route transitions are predictable and deterministic
- [x] Palette action runs from all visible screens

---

## Phase 6 — DayDial feature (core interaction surface)

### 6.1 Dial primitives (`:feature:daydial`)

Implement:

- `ChronosDialCanvas`
- `DialGrid` helper utilities
- Time-angle conversion functions
- `NowHand`
- Ring hit-testing and selection model

### 6.2 Visual model

- Rings:
  - outer = calendar/imported events (if any)
  - middle = planned blocks
  - inner = indicators (tasks/habit/med)

### 6.3 Interaction

- tap block -> select
- drag block (safe move)
- center summary card updates on selection
- optional long-press create (post-MVP)

### 6.4 Data binding

- `DayDialViewModel` reads today blocks from repo
- emits state:
  - selected date
  - selected block
  - conflict overlays

### Exit criteria

- [x] Block selection + summary updates within 1 frame
- [x] Drag updates persisted in DB with validation
- [x] No crash during repeated gesture tests

---

## Phase 7 — Plan flow and task-to-time integration

### 7.1 Plan tab

- Uncompleted inbox list
- filters (all / today / later)
- task quick-create

### 7.2 Schedule sheet

- start minute
- end minute
- conflict preview before save
- confirm action

### 7.3 Transactional save

- link task to block atomically
- rollback on failure

### 7.4 Undo/redo

- keep one-level history at minimum for move/resize edits

### Exit criteria

- [x] Scheduling updates dial immediately
- [x] No partial writes
- [x] Resume after process kill keeps state

---

## Phase 8 — Focus engine

### 8.1 Feature module

Create `feature:focus` with:

- `FocusSessionViewModel`
- `FocusTimerState`
- UI to show remaining time and session controls

### 8.2 Foreground service

- service with countdown handling
- notification with controls/pulse updates
- Android version fallback handling

### 8.3 Completion flow

- on complete, persist `FocusSession`
- return to Dial/Plan with completion status visible

### Exit criteria

- [x] App can survive background transitions
- [x] Timer stop/resume is consistent
- [x] Focus completion stored exactly once

---

## Phase 9 — Settings, preferences, and feature flags

Create settings surface with:

- theme toggle (dynamic/static/system)
- day start/end defaults
- focus defaults (default block duration)
- notification permissions status
- parked feature flags:
  - `feature.habitsEnabled`
  - `feature.medicationEnabled`
  - `feature.reviewEnabled`
  - `feature.aiAdvisorEnabled`

### Exit criteria

- [x] Settings persist in Room/DataStore
- [x] Flag changes immediately change shell visibility

---

## Phase 10 — Secondary modules (parked but ready)

### 10.1 Habits

- Minimal list + quick complete
- indicator on inner dial
- no complex calendar heatmaps in phase 1 of this module

### 10.2 Medication

- schedule list
- exact-alarm permission flow
- fallback scheduling when exact alarm not permitted

### 10.3 Review

- day-level planned vs actual summary
- missed block and completion metrics

### 10.4 AI advisor

- generate candidate plan only
- show diff and rationale
- user must `Apply plan` explicitly

### Exit criteria

- [x] Modules build and can be toggled on/off independently
- [x] No hidden auto-write in advisor module

---

## Phase 11 — Testing and hardening matrix

### Unit

- Domain logic
- Data repositories
- Planner conflict rules

### Integration

- Room flow to view-model to UI mapping
- navigation transitions across rotate/recreate
- command launcher open/execute path

### UI/Manual

- open app cold start
- create task -> dial block -> focus run -> return
- command palette flow
- back stack sanity

### Performance and quality checks

- single-frame interaction for drag on low-end device
- no memory growth in repeated open/close/re-open loops
- accessibility: contrast and touch target review

### Exit criteria

- [x] `./gradlew test` green for touched modules
- [x] `./gradlew connectedAndroidTest` for at least one device/emulator target (or equivalent coverage)
- [x] No known open crash paths

---

## Phase 12 — Release readiness

### Build lanes

- debug assemble all
- release signing check
- install and smoke test flow

### Documentation set

- `CHANGELOG.md`
- `RELEASE_NOTES.md`
- `KNOWN_LIMITATIONS.md` (include limitations for parked features)

### Final acceptance checklist

- [x] Core flow: Today/Plan/Focus fully wired with clean shell
- [x] Theme and spacing consistent across modules
- [x] No duplicate app chrome
- [x] All parked features behind explicit flags
- [x] AI never auto-applies

---

## Concrete file plan by phase

| Phase | New/updated paths |
|---|---|
| 0 | `docs/product-contract.md`, `README_MINIMAL.md` |
| 1 | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, module `build.gradle.kts` files |
| 2 | `core/design/.../Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `ChronosTheme.kt`, component files |
| 3 | `core/domain/...` models, use-cases, repositories interfaces, tests |
| 4 | `core/data/...` entities, DAOs, Database, migrations, repo impls |
| 5 | `app/navigation/*`, shell composables, launcher entry, route state |
| 6 | `feature/daydial/*` (dial, state, interaction utils) |
| 7 | `feature/tasks/*` + schedule/edit sheet + undo stack |
| 8 | `feature/focus/*` + notification + service |
| 9 | `core/ui/settings/*`, `app/settings/*` |
| 10 | `feature/{habits,medication,review,ai}/*` |
| 11 | tests in each module + CI scripts |
| 12 | `docs/` release artifacts |

---

## Command-line quick start (copy/paste)

```bash
# from repo root
./gradlew :app:assembleDebug
./gradlew test
./gradlew :core:domain:test :core:data:test :feature:daydial:test :feature:tasks:test :feature:focus:test
./gradlew :app:connectedAndroidTest --no-daemon
```

---

## Suggested sprint schedule

| Sprint | Duration | Focus |
|---|---:|---|
| Sprint 1 | 4–6 days | Phases 0–3 |
| Sprint 2 | 5–7 days | Phases 4–6 |
| Sprint 3 | 4–6 days | Phases 7–8 |
| Sprint 4 | 3–5 days | Phases 9–11 |
| Sprint 5 | 2–3 days | Phase 12 + production polish |

---

## Anti-patterns to avoid

- Don’t add parked features to top-level nav early.
- Don’t store schedule edits as strings; always use minute-of-day ints.
- Don’t auto-route to multiple command/action surfaces simultaneously.
- Don’t duplicate shell layout in each feature screen.
- Don’t persist AI changes without explicit user confirmation.

---

## End state definition

At completion, a brand-new IDE clone should be able to:

1. Build the app from a clean checkout.
2. Run core flow from Today → Plan → Focus.
3. Keep UI coherence under theme changes.
4. Offer feature growth via hidden modules without re-architecting shell.
5. Expand into Habits/Medication/Review/AI with lower risk.

---

## Next Pass: Settings Persistence DataStore Closure

- Added AndroidX DataStore Preferences to `core:ui` and moved shared UI settings reads/writes onto a DataStore-backed store with SharedPreferences migration/fallback mirroring.
- Routed DayDial persistent setting helpers through the shared DataStore-backed UI settings APIs.
- Updated reminder preference reading to use the same persisted store for reminder toggles and sleep schedule settings.
- Added regression coverage for DataStore snapshot reads, flow emission, and reminder settings readback.
- Verified:
  - `.\gradlew.bat --no-daemon --max-workers=1 :core:ui:testDebugUnitTest --tests com.chronosflow.core.ui.settings.ChronosUiSettingsTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :feature:daydial:testDebugUnitTest --tests com.chronosflow.feature.daydial.DayDialReminderPreferencesReaderTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :core:ui:testDebugUnitTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :feature:daydial:testDebugUnitTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug`

---

## Next Pass: Theme Settings And Core UI Color Closure

- Extended the shared UI settings snapshot with `dynamicColorEnabled` and persisted it through the same DataStore-backed settings path as the other appearance toggles.
- Routed `MainActivity` and `BubbleActivity` root `ChronosTheme` calls through persisted appearance settings so dynamic color, appearance mode, high contrast, and reduced motion reach app-level chrome and non-DayDial screens.
- Centralized `ChronosTheme` fallback palette values behind `ChronosColors` design tokens.
- Added a core-ui color audit test that fails if raw `Color(0x...)` literals appear outside `DesignTokens.kt`.
- Verified:
  - `.\gradlew.bat --no-daemon --max-workers=1 :core:ui:testDebugUnitTest --tests com.chronosflow.core.ui.settings.ChronosUiSettingsTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :core:ui:testDebugUnitTest --tests com.chronosflow.core.ui.theme.ChronosColorTokenAuditTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :core:ui:testDebugUnitTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :feature:daydial:testDebugUnitTest --tests com.chronosflow.feature.daydial.DayDialReminderPreferencesReaderTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug`

---

## Next Pass: Launch No-Crash Closure

- Verified the current debug APK on the running `emulator-5554` target after a clean uninstall/install.
- Confirmed the earlier startup failure was stale installed dex state: the current APK contains `com.chronosflow.ChronosApplication_GeneratedInjector`, and clean install removes the Hilt `NoClassDefFoundError`.
- Verified:
  - `adb uninstall com.chronosflow`
  - `adb install app\build\outputs\apk\debug\app-debug.apk`
  - `adb shell am start -W -n com.chronosflow/.MainActivity` returned `Status: ok`, `LaunchState: COLD`, `Activity: com.chronosflow/.MainActivity`, `TotalTime: 4341`
  - `adb shell pidof com.chronosflow` returned a live pid
  - `adb shell dumpsys window` reported focus on `com.chronosflow/com.chronosflow.MainActivity`
  - `adb logcat -b crash -d -v time` scan found no `com.chronosflow` crash markers

---

## Next Pass: Parked Feature Flag Closure

- Added shared parked-module feature flags for Habits, Medication, Review, and AI advisor with disabled defaults and persisted DataStore/SharedPreferences reads.
- Routed app shell destinations, notification launches, quick add actions, command providers, DayDial sidebar roots, and parked route bodies through explicit feature flags.
- Added user-facing toggles for parked modules in DayDial AI settings and guarded AI plan generation behind the AI advisor flag.
- Verified:
  - `.\gradlew.bat --no-daemon --max-workers=1 :core:ui:testDebugUnitTest --tests com.chronosflow.core.ui.settings.ChronosUiSettingsTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests com.chronosflow.navigation.ChronosRouteShellDestinationTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :feature:daydial:testDebugUnitTest --tests com.chronosflow.feature.daydial.model.SidebarPageFeatureFlagsTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 chronosCiCheck`
  - `.\gradlew.bat --no-daemon --max-workers=1 :core:ui:testDebugUnitTest :feature:daydial:testDebugUnitTest :app:testDebugUnitTest`

---

## Next Pass: Connected Android Test Closure

- Updated the main instrumented command-palette test to treat Habits and Medication as parked-by-default commands instead of active default commands.
- Verified the app connected test lane on the running `emulator-5554` target.
- Verified:
  - `.\gradlew.bat --no-daemon --max-workers=1 :app:connectedDebugAndroidTest`

---

## Next Pass: Runtime Parked Surface And Crash Closure

- Added connected regression coverage that the default primary layout exposes Today, Plan, and Focus while hiding parked Review, Habits, and Meds labels.
- Hid the DayDial Today/Plan quick-item Habits and Meds shortcuts unless their feature flags are enabled.
- Hid the DayDial review action button unless the Review module flag is enabled.
- Verified a clean debug install, cold launch, focused `com.chronosflow/.MainActivity`, and no `com.chronosflow` crash markers in the crash buffer.
- Verified runtime layout still exposes Today, Plan, Focus, Open command palette, and Open quick create while no parked Review/Habits/Meds labels are present.
- Captured `build\reports\chronosflow-current-ui.png` to verify the compact shell has one top app bar, one floating bottom navigation pill, and one quick-create FAB.
- Verified:
  - `.\gradlew.bat --no-daemon --max-workers=1 :feature:daydial:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
  - `.\gradlew.bat --no-daemon --max-workers=1 :app:connectedDebugAndroidTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 :app:installDebug`
  - `android layout --device=emulator-5554 --pretty`
  - `android screen capture "-o=build\reports\chronosflow-current-ui.png"`
  - `.\gradlew.bat --no-daemon --max-workers=1 :feature:daydial:testDebugUnitTest :app:testDebugUnitTest`
  - `.\gradlew.bat --no-daemon --max-workers=1 chronosCiCheck`

---

## Next Pass: Theme Spacing And Build Checklist Closure

- Reserved compact-shell content space above the floating bottom navigation pill and quick-create FAB so routed screens do not render primary controls underneath app chrome.
- Verified Today, Plan, and Focus screenshots after the shell-spacing pass:
  - `build\reports\chronosflow-today-ui-after-shell-padding.png`
  - `build\reports\chronosflow-plan-ui-after-shell-padding.png`
  - `build\reports\chronosflow-focus-ui-after-shell-padding.png`
- Verified the default runtime layout still exposes Focus shell controls without parked Review/Habits/Meds labels leaking into active UI.
- Verified the Gradle project model imports only the current ChronosFlow module set from `settings.gradle.kts`: app, benchmark, core modules, feature modules, and wear.
- Verified:
  - `android describe --project_dir=.`
  - `.\gradlew.bat --no-daemon --max-workers=1 projects`
  - `.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug`
  - `.\gradlew.bat --no-daemon --max-workers=1 :app:connectedDebugAndroidTest`
  - `android layout --device=emulator-5554 --pretty`
  - `.\gradlew.bat --no-daemon --max-workers=1 chronosCiCheck`
