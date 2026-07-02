# ChronosFlow iOS — deferred parity backlog

Five Android→iOS parity items remain unbuilt. Each is deferred because of a **real constraint**
(platform capability, restricted entitlement, or an accepted product/shell redesign), not because
it's large. This doc is the build brief for each: the constraint, the approach that fits iOS, the
files it touches, and the guardrails. Follow the repo's normal loop — port deterministic logic into
`ChronosCore` with tests first, then wire the UI; build the app + watch and run `swift test` before
committing; run `impact` before editing a symbol and `detect_changes` before committing.

Priority order (highest value / lowest risk first): 1 → 5 → 2 → 4 → 3.

---

## 1. Unified cross-tab undo/redo

**Constraint (soft).** Today the undo stack is `@State private var history = PlannerCommandHistory()`
local to `DayDialScreen` (`Features/DayDial/DayDialScreen.swift:31`), so it only covers Plan-tab
block edits and is discarded on tab switch. Android shares one `PlannerCommandHistory` across all
tabs, so completing a task on Today or a habit toggle is undoable from anywhere.

**Approach.** Promote the history to shared shell state and route every mutating action through it.
- Move ownership to `ShellState` (`App/ShellModel.swift`) — e.g. `var history = PlannerCommandHistory()`
  (it's `@Observable`, so views observe `canUndo`/`canRedo` automatically). Keep `PlannerCommand`
  value-typed and `Sendable`.
- Add command cases for the cross-tab mutations that today bypass it: task complete/reopen
  (`TodayView`/`TasksView`), habit toggle (`TodayView`/`HabitsView`), medication mark-taken. Each
  command needs an `apply`/`revert` pair operating on a `ModelContext` (fetch by id + flip the field);
  don't capture `@Model` objects in the command (they're not `Sendable`) — capture ids + prior values.
- Surface a single undo/redo affordance in the shell chrome (a toolbar pair or a transient snackbar
  with "Undo") so it's reachable regardless of the active tab, mirroring Android's global snackbar.

**Files.** `App/ShellModel.swift`, `Features/DayDial/DayDialScreen.swift` (consume shared history
instead of local), `Features/Today/TodayView.swift`, `Features/Tasks/TasksView.swift`,
`Features/Habits/HabitsView.swift`, `Features/Medication/MedicationView.swift`, and
`ChronosCore` `PlannerCommand`/`PlannerCommandHistory` (add the new command cases + tests).

**Guardrails.** Commands must be id-based and reversible without holding model references. Clearing
rules: Android clears the stack on date change for block commands — decide whether cross-tab commands
survive a date change (task/habit completions aren't date-scoped the same way; likely keep them).
Add ChronosCore tests for each new command's apply→revert round-trip before wiring UI.

---

## 2. Review as a 4th in-dial tab

**Constraint (accepted redesign).** Android renders INSIGHTS as the 4th swipeable tab *inside*
`DayDialScreen`, sharing the dial + browsed date. The iOS shell was deliberately rebuilt to present
Review as a dismissible sheet route (`ShellRoute.review`), and the compact bar carries only
Plan/Today/Focus (`PrimaryTab`, `App/ShellModel.swift:16-17`). Making Review a primary tab is a
shell-model change, not a view change — hence "accepted redesign," do it only if you want strict IA
parity over the current iOS idiom.

**Approach.** Add `.review` to `PrimaryTab` and render it in `RootView.primaryContent`
(`App/RootView.swift`), then remove `.review` from the secondary `ShellRoute` set / palette-create
list so it isn't reachable two ways. The compact pill and the iPad rail both iterate `PrimaryTab`,
so a 4-tab bar needs a spacing/width check on the narrowest iPhone (the pill already tightened
spacing once — verify it doesn't overflow with four items + the FAB).

**Files.** `App/ShellModel.swift` (PrimaryTab + ShellRoute), `App/RootView.swift` (primaryContent +
routeView), `App/ShellBottomBar.swift` (4-item layout + the rail's primary list), deep-link routing
(`handleDeepLink`) so `chronosflow://review` selects the tab instead of opening a sheet.

**Guardrails.** This changes the back-stack mental model (Review stops being swipe-to-dismiss).
Confirm the double-tap-Today reset and the shared `selectedDate` still read correctly when Review is
a peer tab. Keep it behind a single decision — don't half-migrate (both a tab and a route).

---

## 3. Palette LLM re-rank + semantic index

**Constraint (hard, platform).** Android's `CommandSearchViewModel` runs an AppSearch-backed
`SemanticPlanningIndex` and an optional on-device LLM re-rank (`rankCommandIdsWithAssist`). iOS has
**no AppSearch analogue**, and a FoundationModels call per keystroke is inappropriate (latency +
battery). The palette already does deterministic name-ranked entity search via
`ChronosCore.rankCommands` ("Your data" section) — this item is the *semantic* layer on top.

**Approach (only if pursued).** Two independent, optional layers:
- **Semantic index:** build a lightweight on-device embedding/keyword index of entities (Core
  Spotlight `CSSearchableItem` is the closest iOS platform primitive, or a hand-rolled token index in
  `ChronosCore`). Prefer Core Spotlight if you also want system-wide search; otherwise keep it a
  deterministic ranker and don't claim "semantic."
- **LLM re-rank:** gate behind a setting, run **only on submit** (not per keystroke), over the
  top-N deterministic candidates, via the existing FoundationModels availability/fallback pattern
  (`AI/ChronosAssistant.swift`). Time-box it and fall back to the deterministic order on timeout.

**Files.** `App/CommandPalette.swift`, `ChronosCore/CommandAssist.swift` (a `rankCommandIdsWithAssist`
analogue that takes an injected async re-ranker so it stays testable), plus a Spotlight indexer if
that route is chosen.

**Guardrails.** Never call the model per keystroke. Keep the deterministic ranker as the always-on
baseline and the re-rank as a pure reordering of its output so a model failure degrades to today's
behavior. If you don't add a genuine semantic index, don't rename the deterministic path "semantic."

---

## 4. DeviceActivity screen-time insights

**Constraint (hard, entitlement).** Real screen-time data requires the **Family Controls**
entitlement (`com.apple.developer.family-controls`) + `DeviceActivity`/`FamilyControls`
authorization, which is restricted and must be requested from Apple. Until provisioned, the Insights
"Screen time" card is an informational placeholder (`Features/Insights/InsightsView.swift:728`,
gated on `settings.screenTimeEnabled`, `Settings/ChronosSettings.swift:226`).

**Approach (once the entitlement is granted).**
- Add the Family Controls capability to `project.yml` + entitlements, then `xcodegen generate`.
- Request authorization via `AuthorizationCenter.shared.requestAuthorization(for: .individual)` behind
  the existing `screenTimeEnabled` toggle; no-op silently when denied (mirror the HealthKit/EventKit
  gating pattern already in the app).
- Schedule a `DeviceActivitySchedule` + `DeviceActivityMonitor` extension to collect focused-vs-
  distracting usage; surface the aggregates in `screenTimeCard` (replace the placeholder branch).
- Keep all derivation (focus %, best-day, distraction rollups) in `ChronosCore` as pure functions
  over the collected minutes, with tests — the extension just feeds it numbers.

**Files.** `project.yml` + `*.entitlements`, a new `DeviceActivityMonitor` extension target,
`Features/Insights/InsightsView.swift` (`screenTimeCard`), `ChronosCore` (aggregation + tests).

**Guardrails.** Do not attempt without the provisioned entitlement — the APIs throw/deny and the
extension won't load. Everything must degrade to today's placeholder when unauthorized. Screen-time
data is sensitive: keep it on-device, never in a backup export or the watch snapshot.

---

## 5. Android manual-missed registry

**Constraint (soft, model).** Android persists an explicit "missed" registry (`HabitEvent`/block
events of type MISSED) so a user can mark something missed and drive analytics/streak-window logic
from it. iOS currently *derives* missed blocks heuristically (scheduled end passed with no actual
end — `ShellBadges.missedTodayCount`, `App/ShellBottomBar.swift`) and has no user-driven "mark
missed" for blocks. Habits already model skips (`Habit.skipDates`); this is mainly about blocks and
an explicit missed state.

**Approach.**
- Decide scope: the highest-value slice is **block-level explicit missed** (a "Mark missed" action on
  a past TimeBlock) feeding the Today badge + Insights execution/drift stats instead of the
  end-time heuristic. Habit MISSED events are largely covered by skip + the new cadence due-derivation.
- If persisting: add an optional `missedAt: Date?` (or a small `@Model MissedEvent`) — optional /
  defaulted so SwiftData lightweight-migrates (same rule the attachments + recurrence-reminders
  changes followed). Keep the heuristic as the fallback when no explicit state exists.
- Put the "is this block missed" resolution (explicit state OR heuristic) in `ChronosCore` so the
  badge, Insights, and any widget agree, with tests for both paths.

**Files.** `Models/TimeBlock.swift` (optional missed field), a "Mark missed" affordance in
`Features/DayDial/DayDialScreen.swift` (block context menu) and/or `Features/Today/TodayView.swift`,
`ChronosCore` (missed resolution + tests), and the consumers (`ShellBadges`, `InsightsAnalytics`).

**Guardrails.** New persisted fields must be optional/defaulted (no destructive migration). Don't
remove the heuristic — layer explicit state on top of it. Verify the missed count still matches
across the Today badge and the Insights "Missed"/drift figures (they must read the same resolver).
