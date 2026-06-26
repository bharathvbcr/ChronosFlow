The reports are confirmed against the actual code: flat 12-tab `TabView`, `DayDialScreen.selectedDate` hardcoded to today, no `CommandPalette`/`SidebarPage` on iOS, separate `TodayView`/`FocusView`/`InsightsView`, and `QuickAddEditor` routing to editor sheets rather than Day targets. I have what I need to synthesize the plan.

---

# iOS → Android Parity: Prioritized Restructuring Plan

## 1. Executive Summary — Root Structural Divergences

iOS "feels like a different app" not because individual screens are wrong, but because the **information architecture is inverted**. Five root divergences cascade into nearly every per-area gap report:

1. **The navigation shell IA is flat, not dial-centric (#1 root cause).** Android has **3 primary in-place tabs** (Plan/Today/Focus) that swap targets inside one `DayDialScreen` over a *shared* date/dial/focus/undo state, plus a 4th in-dial Review tab — with all secondary destinations (Tasks/Habits/Goals/Meds/Routines/Journal/Sleep/Calendar/Settings-pages) reached via the Quick-Add FAB, command palette, or a sidebar. iOS uses a **flat `TabView(.sidebarAdaptable)` with 12 equally-weighted sibling tabs**, no shared state, no in-place concept. This single divergence is the parent of the "critical" findings in Navigation Shell, DayDial, Insights, Routines, Journal, and Sleep.

2. **No unified Day state model.** Android's `DayDialViewModel` owns `selectedDate`, `sortedBlocks`, `focusSession`, and `PlannerCommandHistory` and shares them across all 4 primary tabs. On iOS each view holds its own `@State`: `DayDialScreen.selectedDate` is **hardcoded to today**, `FocusTimerModel` is a disconnected singleton, undo/redo lives only in the Plan tab, and the date picker doesn't propagate. Tabs cannot see each other's state.

3. **No command palette and an under-powered Quick-Add.** Android has two complementary creation surfaces (FAB menu with live NL quick-capture preview + a global Cmd-K `CommandPaletteDialog` with semantic search, AI ranking, recent-command boosting, and an "Ask the assistant" row). iOS has **only** a static Quick-Add sheet (block/task/med/habit/goal/journal/sleep) and **no command palette at all**; the assistant is buried in a tab.

4. **Secondary destinations are modeled as top-level tabs, not in-app sidebar pages.** Android distinguishes dismissible in-day *sidebar pages* (Tasks/Habits/Routines/Calendars/Settings-sub-pages) from a hierarchical back stack anchored at Day. iOS makes them peer tabs with no hierarchy, no back-to-Day fallback, no badges, and no calendar-management or settings-multipage structure.

5. **Per-screen feature density and visual system drift.** Within screens, iOS rows/cards are thinner than Android (Tasks rows lack metadata badges/action footers/context sheets; Medication lacks the suggestion panel/info pills; Habits/Goals use `ChronosGlassCard` where Android uses opaque `ChronosListCard`; Insights hides trends behind collapsibles). These are real but **downstream** — they should be fixed per-area *after* the shell is corrected, since several screens (Today, Focus, Insights, Journal, Sleep) will be restructured or relocated by the shell work anyway.

**Implication for sequencing:** Do **not** start with per-screen polish. Phase 1 must rebuild the shell + unified Day state, because it relocates 4 screens (Today/Focus/Insights into the dial), changes how 6 others are reached (sidebar pages), and is a prerequisite for badges, command palette, in-place sheets, and the date scrubber. Everything else is unblocked by it.

---

## 2. Phased Plan

### Phase 0 — Foundations (state model + feature-flag destination registry)
*Unblocks every later phase; no user-visible change on its own.*

**What changes**
- Introduce a `@Observable DayViewModel` (Swift mirror of Android `DayDialViewModel`) owning: `selectedDate`, derived `sortedBlocks`/free-windows/conflicts, `focusSession` (+ remaining/elapsed seconds), and a `PlannerCommandHistory` (`canUndo`/`canRedo`). This is the spine of Phase 1.
- Introduce a `ShellDestination`/`SidebarPage` registry — a computed list filtered by feature flags — replacing the inline `if settings.habitsEnabled { Tab… }` scattering in `RootView`. Mirror `ChronosRoute.compactShellDestinations()` / `expandedShellDestinations()`.

**iOS files**
- Create `ios/ChronosFlow/App/DayViewModel.swift`
- Create `ios/ChronosFlow/App/ShellDestination.swift` (registry + `SidebarPage` enum)

**Android reference**
- `ChronosShellViewModel.kt`, `DayDialViewModel` (focus/undo/date state), `ChronosRoute.kt` (`compactShellDestinations`/`expandedShellDestinations`/`isAvailable`), `feature/daydial/model/SidebarPage.kt`

---

### Phase 1 — Shell & IA restructure (THE unblocker) — *critical*
*Collapses the flat 12-tab `TabView` into Android's 3-primary / in-dial-Review / FAB+sidebar model.*

**What changes**
1. **Three primary tabs only.** Reduce the `TabView` to Plan, Today, Focus. Move **Review** to be a 4th *in-dial* tab (not a `TabView` member). Remove Tasks/Habits/Goals/Meds/Routines/Sleep/Journal as top-level tabs — they become FAB-menu + sidebar-page destinations.
2. **Unified dial screen.** Create `DayDialUnifiedScreen.swift` hosting Plan/Today/Focus/Review as in-place tabs over the shared `DayViewModel`, with `AnimatedContent`-style horizontal transition and **back-falls-to-Today** behavior. `DayDialScreen`, `TodayView`, `FocusView`, `InsightsView` become tab *content* composited inside it (not standalone `NavigationStack`s).
3. **Shared date scrubber + undo/redo in one top bar**, visible across all 4 tabs, bound to `DayViewModel.selectedDate` (removes the hardcoded `Calendar.current.startOfDay` and per-view pickers). Clear history on date change.
4. **iPad sidebar** preserving the 3-primary / secondary split: primary items (Plan/Today/Focus/Review) on top, a "Destinations" section listing secondary pages — not a flat flatten.
5. **Tab badges** (Today: missed-block count / off-today date; Review: unread insights; Focus: active indicator), driven by shell state — requires a custom compact bar / sidebar item since native `TabView` won't badge here.
6. **Double-tap Today → reset to today** (`TARGET_TODAY_RESET`).
7. **Deep-link feature-flag gating** in `handleDeepLink` (disabled feature → Today, not a blank tab).

**iOS files**
- Modify `ios/ChronosFlow/App/RootView.swift` (collapse tabs, custom compact bar + badges, sidebar, deep-link gating)
- Create `ios/ChronosFlow/Features/DayDial/DayDialUnifiedScreen.swift`
- Modify `Features/Today/TodayView.swift`, `Features/Focus/FocusView.swift`, `Features/Insights/InsightsView.swift`, `Features/DayDial/DayDialScreen.swift` (de-`NavigationStack`, read shared `DayViewModel`)
- Modify `ios/ChronosFlow/ChronosFlowApp.swift` (inject `DayViewModel`)

**Android reference**
- `navigation/ChronosNavigationShell.kt`, `ChronosCompactFloatingBottomBar`, `ChronosAdaptiveNavigationRail`, `navigation/ChronosNavigationState.kt` (back stack), `feature/daydial/DayDialScreen.kt`, `DayDialScreenChrome.kt`, `DayDialMainContent.kt`, `DayDialTopBar.kt`, badge logic `badgeValueFor()`

---

### Phase 2 — Quick-Add FAB + Command Palette + sidebar pages — *critical*
*The two creation/discovery surfaces and the in-app page mechanism Android relies on.*

**What changes**
1. **FAB expand-in-place** (not a full modal): animated scale/fade menu with feature-gated actions (block/task/med/habit/goal/journal/sleep) + rotating `+`.
2. **Live NL quick-capture preview** ("Add task '…' · tomorrow") mirroring `previewQuickCaptureCommand`, plus an **"Ask the assistant"** row for question-shaped queries.
3. **Command palette** (`CommandPaletteView`) triggered by Cmd-K / toolbar button: command providers (quick-create, day-dial, focus, task/habit/goal/med), keyword filter, recent-command boosting, semantic search + AI ranking, proactive digest row, assistant integration. Consolidate the buried `AssistantSheet` into it.
4. **Sidebar-page mechanism + in-place Day sheets.** Secondary destinations selected from FAB/palette open as dismissible in-day pages (back → Day), and block/journal/sleep creation maps to **Day targets** (`TARGET_ADD_BLOCK/JOURNAL/SLEEP`) so editors open in-place rather than as detached sheets.

**iOS files**
- Modify `ios/ChronosFlow/App/QuickAdd.swift`, `ios/ChronosFlow/App/RootView.swift`
- Create `ios/ChronosFlow/App/CommandPaletteView.swift`
- Create `ios/ChronosFlow/Features/AI/CommandSearchViewModel.swift`
- Create `ios/ChronosCore/Sources/ChronosCore/CommandSearch.swift` (if semantic search/ranking not yet ported)

**Android reference**
- `ChronosNavigationShell.kt` (`ChronosQuickAddButton`/`ChronosQuickAddMenu`/`ChronosQuickCaptureInput`), `MainActivity.kt` (`CommandPaletteDialog`), `CommandSearchViewModel.kt`, `ChronosQuickCreateViewModel`, `feature/daydial/DayDialCommandProvider.kt`, `feature/daydial/ui/SidebarPageContent.kt`

---

### Phase 3 — Relocate Sleep / Journal / Routines into the shell model — *critical/high*
*These three are mis-placed as Track tabs; the shell now exists to host them correctly.*

**What changes**
- **Routines:** remove `ShellTab.routines`; surface as a sidebar page (`SidebarPage.ROUTINES`/reuse `TEMPLATES`). Add **"Seed day"** (apply at minute 0) and **"Save current day as routine"** (callback from the unified dial). Show **X/Y blocks done** completion (query blocks by `routineId`). Add an "Apply Routine" App Intent for Siri/Shortcuts parity.
- **Journal:** remove from Track; extract composer into `JournalEditorSheet` opened as an in-place Day target from Quick-Add; keep root Journal page (history + calendar + inline insight card) as a sidebar page. Move AI insight inline (drop the separate full-screen sheet); add multi-day range creation and per-day inline point-adder.
- **Sleep:** remove top-level tab; primary entry is Quick-Add → Log sleep (Day target) + sidebar page. Add the **"Refreshed" 1–5 emoji rating** (and `refreshedRating` on `SleepTrack`), emoji quality label, and a **readiness banner on TodayView**.

**iOS files**
- Modify `App/RootView.swift`, `App/QuickAdd.swift`
- `Features/Routines/RoutinesView.swift`, `RoutineEditorSheet.swift`, `Features/DayDial/DayDialScreen.swift`
- `Features/Journal/JournalView.swift` (+ new `JournalEditorSheet`)
- `Features/Sleep/SleepView.swift`, `Models/Wellbeing.swift`, `Features/Today/TodayView.swift`
- `ios/ChronosFlow/AppIntents/ChronosAppIntents.swift` (routine intent)

**Android reference**
- `feature/daydial/ui/SidebarPageContent.kt` (templates/journal/sleep pages, seed/save actions), `JournalComposerSheet`/`JournalEntryComposer`, `SleepLogSheet`/`RefreshedEmojiSelector`, `ChronosAppFunctions.kt` (`applyRoutine`)

---

### Phase 4 — Insights/Review parity — *critical*
*Largely structural (Phase 1 moves Review into the dial); this is the content cleanup.*

**What changes**
- Render trends **inline, gated by data availability** — remove the default-collapsed `collapsibleCard` wrappers (habit streaks, sleep chart, best-window, mood/energy, sleep→mood, adherence, findings).
- Move "Open daily review" button **into** the execution card (off the toolbar).
- Add **"Apply"** action to recommendation rows; add the **focus-concentration tip** to the category card; consolidate into one cohesive Trends section.
- Screen Time: keep placeholder unless `DeviceActivity` authorization is wired.

**iOS files** — `Features/Insights/InsightsView.swift`, `Features/DayDial/DayDialUnifiedScreen.swift`

**Android reference** — `feature/daydial/DayDialMainContent.kt` (InsightsTab), `InsightsTrendSections.kt`, `ScreenTimeCard.kt`

---

### Phase 5 — Tasks deep parity — *critical (content-level)*
*Biggest single-screen gap; independent of shell, so parallelizable after Phase 1.*

**What changes** — Add page header + Open/Urgent/Done metric tiles; AI triage `TaskStatsCard`; permission alert cards; Open/All/Done filter chips + DayDial button; rich `TaskRow` (description preview, colored metadata badges for schedule/connections/alarm/dial-gap, action cue, Schedule + external-action + Duplicate/Edit/Delete footer); `TaskContextCommandSheet`; editor: AI "Suggest details" + suggestion chips, duplicate-task warning, goal detection/linking, full recurrence (ordinal weekday, start/end, reminders), attachments/contact/actions, dictation, auto-assist.

**iOS files** — `Features/Tasks/TasksView.swift`, `TaskEditorSheet.swift`, `TaskBulkImportSheet.swift`, `TaskTemplate.swift`, `Models/TaskItem.swift`

**Android reference** — `feature/tasks/TaskScreen.kt` (`TaskItem`, `TaskFormSheet`, `TaskContextCommandSheet`, `taskContextCue`/`taskScheduleSummary`/`taskConnectionSummary`/`detectGoalIdFromText`)

---

### Phase 6 — Focus session parity — *high*
**What changes** — Add "Start Focus" entry from Plan blocks (bridge to Focus tab w/ prefilled blockID); surface `boundaryPrompt` next-phase copy; expose **Skip phase** + **−5/−15m** controls; explicit `restoreActiveSplitFromStore` on appear + background-boundary recovery; phase-count on lock-screen Live Activity; "Paused · tap play to resume" copy; active-phase pulse + 10dp/0.8 sizing.

**iOS files** — `Features/Focus/FocusView.swift`, `FocusTimerModel.swift`, `Features/DayDial/DayDialScreen.swift`, `LiveActivity/FocusLiveActivityView.swift`, `ChronosCore/.../FocusPhasePlan.swift`

**Android reference** — `DayDialFocusDelegate`, `FocusPhasePlanner`, `FocusSplitSessionStore`, Android Live Activity segments

---

### Phase 7 — Medication & Habits density — *high*
**What changes** — Medication: unified layout (header, metric tiles, add button, **Next-Dose card**, refill, **adherence chart**, **adherence suggestion panel**), `MedicationInfoPill`s, expand/collapse rows, empty-state quick-add chips, custom context action sheet w/ discrete snooze (15/30/60), editor AI assist. Habits: restructure card to title→pills→weekstrip→actions, condense repair-suggestion copy, swap `ChronosGlassCard`→`ChronosListCard`, 16dp square week-strip dots.

**iOS files** — `Features/Medication/MedicationView.swift` (+ `Models/MedicationPlan.swift`), `Features/Habits/HabitsView.swift`

**Android reference** — `feature/medication/MedicationScreen.kt`/`MedicationViewModel.kt`/`MedicationAdherencePanel`, `feature/habits/HabitScreen.kt`

---

### Phase 8 — Goals, Settings multi-page, Calendar/Interop — *high*
**What changes**
- **Goals:** promote to a primary/sidebar destination w/ prefill capture; fix filter-chip toggle → exclusive selection; default `showCompleted = false`; align metric/empty-state components.
- **Settings:** split monolithic `SettingsView` into 4 `NavigationStack` sub-pages (Appearance / AI & Planning / Notifications / Privacy & Sync); add App-Permissions disclosure; add missing planning toggles (protect focus / auto-breaks / preserve manual). Optionally trim onboarding to 3 pages (cosmetic).
- **Calendar/Interop:** new `CalendarManagementView` (month picker, sync-status, permission flow w/ rationale, source-filtered timeline w/ All/Schedule/Calendar presets); `ConnectedAppsView` surfacing interop last-sync/result/Sync-Now/consent toggle; block-level export/refresh/remove in `TimeBlockEditorSheet`.

**iOS files** — `Features/Goals/GoalsView.swift`, `GoalDetailView.swift`; `Settings/SettingsView.swift` (+4 new sub-views), `Settings/ChronosSettings.swift`, `Features/Onboarding/OnboardingView.swift`; `Features/Calendar/CalendarOverlay.swift` (+ new `CalendarManagementView`, `ConnectedAppsView`), `Interop/InteropSync.swift`, `Models/TimeBlock.swift`, `Features/Editor/TimeBlockEditorSheet.swift`

**Android reference** — `feature/goals/`, `feature/daydial/ui/SidebarPageContent.kt` (settings sub-pages, calendars page, companion-app card), `DayDialCalendarPermissions.kt`, `InteropSyncManager.kt`/`InteropContract.kt`

---

## 3. Per-Area Gap Table (ordered by impact)

| Area | Severity | Top divergences | Key iOS files |
|---|---|---|---|
| Navigation shell & IA | **critical** | Flat 12 tabs vs 3 in-place primaries; no hierarchical back/sidebar; no badges/double-tap-reset | `App/RootView.swift`, `App/QuickAdd.swift`, `ChronosFlowApp.swift` |
| DayDial central screen | **critical** | 4 primary tabs not unified in one screen; no shared date/focus/undo; date hardcoded to today | `Features/DayDial/DayDialScreen.swift`, `Today/TodayView.swift`, `Focus/FocusView.swift`, `Insights/InsightsView.swift` |
| Quick-Add / Command palette / capture | **critical** | No command palette; FAB is static modal w/o live NL preview; no "Ask assistant" | `App/QuickAdd.swift`, `App/CommandPaletteView.swift` (new), `Features/AI/CommandSearchViewModel.swift` (new) |
| Insights / Review | **critical** | Review is a separate tab not in-dial; trends default-collapsed; recs lack Apply; no concentration tip | `Features/Insights/InsightsView.swift`, `DayDial/DayDialScreen.swift` |
| Routines | **critical** | Standalone tab vs in-day sidebar page; no seed-day/save-as-routine; no AppIntent; binary completion only | `App/RootView.swift`, `Features/Routines/RoutinesView.swift`, `RoutineEditorSheet.swift` |
| Calendar import / sync / interop | **critical** | No calendar-management page; permission flow silent; interop has zero UI; no source filters/export | `Settings/SettingsView.swift`, `Features/Calendar/CalendarOverlay.swift`, `Interop/InteropSync.swift`, `Models/TimeBlock.swift` |
| Tasks | **critical** | Minimal rows (no badges/footers/cues/context sheet); no metrics/triage/permission cards/filter chips; thin editor | `Features/Tasks/TasksView.swift`, `TaskEditorSheet.swift`, `Models/TaskItem.swift` |
| Focus timer & sessions | **high** | No Plan-tab entry; no Skip/−5m UI; no next-phase prompt copy; no explicit split restore | `Features/Focus/FocusView.swift`, `FocusTimerModel.swift`, `LiveActivity/FocusLiveActivityView.swift` |
| Medication | **high** | No adherence suggestion panel/next-dose card; no info pills/expand rows; native context menu | `Features/Medication/MedicationView.swift`, `Models/MedicationPlan.swift` |
| Sleep | **high** | Top-level tab vs dial sheet; missing Refreshed rating; no readiness on Today | `App/RootView.swift`, `Features/Sleep/SleepView.swift`, `Models/Wellbeing.swift`, `Today/TodayView.swift` |
| Journal | **high** | Tab vs modal-sheet composer; insight in separate sheet not inline; no multi-day/inline point-adder | `App/RootView.swift`, `Features/Journal/JournalView.swift` |
| Settings & Onboarding | **high** | One monolithic Form vs 4 sidebar pages; missing planning toggles & App-Permissions group | `Settings/SettingsView.swift`, `Settings/ChronosSettings.swift`, `Features/Onboarding/OnboardingView.swift` |
| Habits | **high** | Card hierarchy differs; GlassCard vs ListCard; circular vs square week-strip dots | `Features/Habits/HabitsView.swift` |
| Goals | **high** | TabSection vs primary destination; filter chip toggles instead of exclusive; `showCompleted` defaults true | `App/RootView.swift`, `Features/Goals/GoalsView.swift`, `GoalDetailView.swift` |

---

## 4. Risks / Requires a Real Xcode Build to Verify

- **Custom shell vs native `TabView` regressions (highest risk).** Replacing `TabView(.sidebarAdaptable)` with a custom 3-tab bar + sidebar forfeits free behaviors: iPad sidebar collapse, state restoration, accessibility/VoiceOver tab semantics, and Stage Manager/size-class adaptation. Must be verified on iPhone (compact), iPad (regular, both orientations), and Slide Over. Native `Tab` badges don't work in this layout — the custom bar is mandatory, so badge rendering/animation needs device verification.
- **`@Observable DayViewModel` + SwiftData threading.** Centralizing `selectedDate`/blocks/focus/undo and re-deriving on every date change risks main-thread `@Query` churn and retain cycles between the VM and four tab views. Profile for recomposition storms and confirm `history.clear()` on date change doesn't drop in-flight edits. Compile-only checks won't catch this.
- **Live Activity / `FocusTimerModel` migration.** Moving the singleton into the VM while keeping the Live Activity widget in sync requires App-Group `UserDefaults` round-trips and background-boundary recovery — only verifiable by backgrounding/killing the app mid-phase on a physical device (Live Activities don't run in Simulator reliably).
- **iOS-version-gated APIs.** `TabSection`, `.sidebarAdaptable`, `Tab(value:)` are iOS 18+; the project targets Xcode 27 / newer SwiftUI but builds on Xcode 26.6 locally (per memory) — confirm the deployment target and that `DeviceActivity`/Screen Time and any new symbol effects (`.symbolEffect(.pulse)`) compile against the actual SDK.
- **EventKit / DeviceActivity / HealthKit entitlements.** Calendar write-export, interop consent, Screen Time data, and sleep import all need Info.plist usage strings + capabilities; missing entitlements fail only at runtime on device, not at build.
- **Cmd-K keyboard shortcut + hardware keyboard** behavior on iPhone vs iPad needs a build to confirm the palette triggers and doesn't conflict with text-field focus.
- **`ChronosCore` semantic search/AI ranking port** (`CommandSearch.swift`) — if not already in Core, the command palette's ranking is a net-new dependency; verify it builds for both app and widget targets and doesn't bloat extension binary size.

**Sequencing note for the implementing engineer:** Phases 0→1→2 are strictly ordered (each is a hard prerequisite for the next). Phases 3 and 4 depend on Phase 1. Phases 5–8 are content-level and can proceed in parallel once Phase 1 lands, but Sleep/Journal/Routines (Phase 3) must precede final removal of their tabs in `RootView`. Do not merge the `RootView` tab-removal until the replacement entry points (FAB/sidebar/Day-targets) are functional, to avoid stranding destinations.