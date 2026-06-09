# ChronosFlow Material 3 Primary UX Redesign Implementation Plan

This is an implementation plan for UX work; for current implementation status, use `README.md` and `docs/product-contract.md`.

## Summary

Redesign ChronosFlow toward a native Material 3 / Material You experience while preserving the app's DayDial-centered product identity. The primary workflow remains `Today -> Plan -> Focus`; Tasks, Habits, Medication, Review, Templates, Settings, and diagnostics stay reachable through the drawer and command palette.

This plan intentionally avoids planner/domain behavior changes. The implementation should improve UI consistency, adaptive navigation, accessibility, command-palette usability, and visible polish.

## Implementation Changes

### 1. Shared Material 3 UI Foundation

- Add shared `core:ui` composables for `ChronosScreenScaffold`, `ChronosTopBar`, `ChronosActionRow`, `ChronosListCard`, and `ChronosSettingsRow`.
- Keep `ChronosTheme`, dynamic color, high contrast, and reduced motion support.
- Make Material 3 surfaces the default; use glass/backdrop only as an optional accent.
- Add tooltip support for icon-only buttons in top bars, command palette actions, undo/redo, date navigation, and quick-create.
- Remove visual drift such as hard-coded violet surfaces where `MaterialTheme.colorScheme` should be used.

### 2. App Shell And Navigation

- Replace the custom floating bottom navigation in `BottomFloatingNav` with a Material 3 native primary navigation surface for `Today`, `Plan`, and `Focus`.
- Keep `Insights` out of primary navigation unless explicitly reintroduced later.
- Remove hard-coded drawer badges in `DayDialSidebar`.
- Replace stale drawer footer `v1.0.0` with `0.1.0` or a build-derived value.
- Keep drawer grouping:
  - Your Day: Day Tools, Tasks, Focus Timer, Habits, Meds, Review
  - Tools: Templates, Calendars, Data & Export, Developer, About
  - Settings: AI Settings, Privacy & Sync, Notifications, Appearance
- Preserve `navigateSingleTop` behavior for query routes so deep links do not restore stale state.

### 3. Command Palette

- Update `CommandPaletteDialog` to render as a native Material 3 search/action surface.
- Visible title must be `Command palette` to match connected tests and user expectations.
- Keep `Ctrl+K`, the top-right search icon, and command-provider contracts unchanged.
- Preserve search behavior across daily dial, plan, focus planner, planning tools, templates, AI settings, privacy/sync, notifications, appearance, focus, tasks, habits, medication, and review.
- Add empty, filtered, and dense-result states using Material 3 list rows, not custom keyword pills that crowd the row.

### 4. Primary Screens

- `TodayTab`
  - Keep `ChronosDial` interaction and callbacks unchanged.
  - Rework hierarchy to: daily summary, dial, Now card, Next card, contextual action strip.
  - Simplify labels and avoid all-caps section headers where Material typography already establishes hierarchy.
  - Keep quick actions: add block, fill gaps, rebalance, protect focus.
- `PlanTab`
  - Group content into: AI planning, templates, warnings, timeline.
  - Make timeline cards denser and easier to scan.
  - Preserve swipe-to-delete/copy behavior.
  - Use Material 3 warning and suggestion patterns instead of heavy custom gradients.
- `FocusTab`
  - Keep active timer behavior and session callbacks unchanged.
  - Use one dominant timer/control surface.
  - Replace decorative glow-heavy idle state with a clearer Material 3 empty state.
  - Remove negative letter spacing and non-ASCII decorative status text.

### 5. Secondary Screens

- Update Tasks, Habits, Medication, Review, and standalone Focus screens to use the shared scaffold and top-bar pattern.
- Keep existing view models and repository calls unchanged.
- Standardize FAB placement, empty states, metric tiles, warning banners, list row actions, and dialog button order.
- Improve Medication row readability by reducing nested button rows and making details expansion more scannable.
- Improve Habit and Task rows with consistent primary/secondary actions.

### 6. Accessibility And Adaptivity

- Add semantic headings to screen titles and major sections.
- Ensure icon-only actions have content descriptions and tooltips.
- Ensure custom clickable rows use roles where appropriate.
- Respect reduced motion by disabling nonessential animated backdrop/motion effects.
- Respect high contrast by avoiding low-alpha text and glass overlays.
- Use adaptive layout checks where practical; on expanded width, prefer rail/supporting-pane behavior over simply stretching phone layouts.

## GitNexus Requirements

Before editing any symbol, run impact analysis and record risk:

- `CommandPaletteDialog`: expected LOW risk.
- `ChronosTheme`: expected LOW risk.
- `DayDialTopBar`: expected LOW risk.
- `BottomFloatingNav`: expected LOW risk.
- `DayDialSidebar`: expected LOW risk.
- `TodayTab`: HIGH risk, because it feeds the core DayDial flow.
- `PlanTab`: HIGH risk, because it feeds the core DayDial flow.
- `FocusTab`: run impact before editing.
- Secondary screens: run impact per screen before editing.

If GitNexus reports a stale index, run:

```powershell
npx gitnexus analyze --skip-git
```

This checkout may not have `.git`, so use `--skip-git` and source/test/screenshot evidence instead of normal git diff assumptions.

## Test Plan

Run focused unit and UI checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:ui:testDebugUnitTest :feature:daydial:testDebugUnitTest --no-daemon --max-workers=1
```

Run connected UI tests when an emulator is available:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --max-workers=1
```

Verify these scenarios:

- App launches to DayDial Today.
- Primary navigation shows `Today`, `Plan`, and `Focus`.
- `Insights` is not top-level navigation.
- Command palette opens from top bar and `Ctrl+K`.
- Command palette title is `Command palette`.
- Palette search finds tasks, habits, medication, review, templates, settings, and focus.
- Drawer pages remain reachable.
- Query-route deep links open the intended section without stale restored state.
- Today dial interactions still select, drag, resize, and create blocks.
- Plan timeline still supports copy/delete gestures.
- Focus start/pause/resume/finish behavior still works.
- High contrast and reduced motion settings visibly affect the UI.
- Text does not overlap at normal and large font scales.

Capture evidence for Today, Plan, Focus, Command palette, Drawer, Tasks, Habits, Medication, Review, and Appearance settings.

## Acceptance Criteria

- The app feels like a coherent Material 3 productivity app, not a mix of custom chrome and unrelated feature screens.
- Day/Plan/Focus remain the obvious core workflow.
- Secondary features are easy to reach but do not clutter top-level navigation.
- No planner, persistence, notification, or command-provider contracts regress.
- All focused tests pass, or any environment-only failure is clearly separated from code failure.
- Emulator screenshots show no obvious overlap, clipped text, stale labels, or inconsistent chrome.

## Assumptions

- Primary redesign is allowed for layout and presentation, but not for domain behavior.
- Material 3 native direction wins over heavier Liquid Glass styling.
- Existing Chronos Dial mechanics remain intact.
- Planning docs belong under `plans/`.
- This implementation should favor focused, verifiable changes over a broad rewrite.
