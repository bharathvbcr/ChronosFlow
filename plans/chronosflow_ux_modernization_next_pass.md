# ChronosFlow UX Modernization Next Pass

## Objective

Make ChronosFlow feel more modern, focused, and pleasant to use while preserving the current product direction: a dial-first daily planning app centered on Today, Plan, Focus, and the global command palette.

This pass should improve hierarchy, navigation clarity, Material 3 polish, accessibility, large-screen behavior, and empty-state quality without expanding scope into a broad feature build-out.

## Current State

- The project is already configured for Android SDK 37 with `compileSdk = 37` and `targetSdk = 37`.
- The active product shell is DayDial-centered. `Today`, `Plan`, and `Focus` are the primary user loop.
- Tasks, Habits, Medication, Review, Templates, settings, and diagnostics remain reachable through the drawer and command palette, but they should not become top-level navigation until they clearly feed the day plan.
- Material 3 and dynamic color are already present. The next improvement is consistency: fewer competing surfaces, fewer heavy custom/glass treatments, stronger state-based actions, and better adaptive behavior.
- Android 17 large-screen behavior matters because orientation, resizability, and aspect-ratio restrictions are ignored on large screens for apps targeting API 37 and higher.

## UX Principles

- DayDial remains the product spine.
- Material 3 native surfaces win over heavy custom/glass styling.
- One obvious primary action should be visible for each user state.
- Secondary features support the day plan rather than creating top-level sprawl.
- Large-screen, foldable, keyboard, and Android 17 resizable behavior must be first-class.
- Dynamic color should feel integrated, not like a theme pasted over custom chrome.
- High contrast and reduced motion settings must visibly simplify the UI.
- Empty states should offer the next useful action, not just describe the absence of content.

## Implementation Plan

### 1. Bottom Navigation And Quick Create

Goal: make the primary mobile shell feel stable and native.

- Replace the current bottom navigation plus visually competing quick-create placement with a cleaner Material 3 bottom app surface.
- Keep `Today`, `Plan`, and `Focus` as the only primary mobile destinations.
- Preserve quick-create behavior:
  - Tap creates a normal block.
  - Long press opens quick-add.
- Move the quick-create affordance into a clear FAB slot or visibly separated action area so it does not overlap or compete with the navigation labels.
- Ensure navigation and quick-create keep stable dimensions across gesture navigation, three-button navigation, large font, and dynamic color.

### 2. Today Next-Best-Action Strip

Goal: make Today answer "what should I do now?" immediately.

- Add a compact action strip above or near the dial, derived only from existing DayDial state.
- Introduce a UI-only model, such as `DailyActionUiModel`, with:
  - `title`
  - `subtitle`
  - `primaryLabel`
  - `secondaryLabel`
  - `kind`
- Suggested state mapping:
  - Empty day: `Plan day` as primary, `Add block` as secondary.
  - Active block: `Start focus` as primary, `Complete` as secondary.
  - Upcoming block but no active block: `Start next` or `Prepare` as primary.
  - Missed blocks: `Review missed` as primary.
  - No upcoming work: `Add block` as primary, `Fill gaps` as secondary.
- Do not add persistence or scheduling rules in this pass. The strip is a view-level decision layer over existing state.

### 3. Today Layout Tightening

Goal: keep the dial as the hero while reducing visual noise.

- Keep the Chronos Dial large and central.
- Reduce repeated explanatory copy in the Now and Next cards.
- Use denser Material 3 cards for:
  - Day summary
  - Now
  - Next
  - Quick actions
- Avoid low-alpha text for essential labels and times.
- Use consistent action labels:
  - `Add block`
  - `Fill gap`
  - `Start focus`
  - `Complete`
  - `Review missed`
- Keep quick actions visible but subordinate to the immediate Now/Next action.

### 4. Plan Layout Tightening

Goal: make planning feel like one coherent workflow instead of separate panels.

- Group planning actions into one Material 3 surface:
  - Generate
  - Rebalance
  - Fill gaps
  - Add manually
- Convert templates into compact chips or small cards that do not dominate the page.
- Keep the timeline section visually primary after the planning controls.
- Improve empty timeline state:
  - Clear title: `No blocks planned`
  - Primary action: `Generate with AI`
  - Secondary action: `Add manually`
- Keep warning and overlap banners, but make them concise and action-oriented.
- Preserve existing swipe-to-delete and copy behavior for timeline blocks.

### 5. Focus Layout Tightening

Goal: make Focus calm, actionable, and free of dead controls.

- When idle and no block is selected, show a clean empty state with one primary action.
- Do not show disabled timer controls when there is no active or selected focus session.
- When a next block exists, offer `Start next: {title}`.
- When a session is active, use one dominant timer surface with:
  - Remaining time
  - Pause/resume
  - Finish
  - Skip
  - Small time adjustment actions
- Reduce decorative glow and low-contrast labels, especially in high contrast or reduced motion.
- Keep status chips for privacy, notifications, and focus protection, but make them secondary to the timer/action surface.

### 6. Supporting Screen Refresh

Goal: make parked features feel polished and useful while keeping them subordinate to DayDial.

- Tasks:
  - Keep `Schedule` as the primary row action.
  - Move edit/delete into compact icon actions.
  - Make open/done filters stable and compact.
  - Keep `DayDial-ready` language only where it helps explain scheduling.
- Habits:
  - Make streak and repair panels easier to scan.
  - Reduce stacked text buttons.
  - Use one primary action per habit row, such as `Complete`.
- Medication:
  - Reduce nested action rows.
  - Make `Taken`, `Missed`, reminder time, and exact-alarm status clear.
  - Keep details expandable, but improve the default collapsed row.
- Review:
  - Add a stronger summary card before insights.
  - Make planned, actual, missed, and drift metrics easier to compare.
  - Keep insights list as supporting detail.

### 7. Command Palette Density And Grouping

Goal: make the palette feel like a modern command surface rather than a long flat list.

- Preserve existing command IDs and callbacks.
- Add optional display metadata without breaking current providers:
  - `group`
  - `shortcutLabel`
  - `priority`
- Group commands by purpose:
  - Day
  - Plan
  - Focus
  - Supporting screens
  - Settings
- Keep the visible title `Command palette`.
- Keep top-bar search and `Ctrl+K`.
- Preserve search coverage for Tasks, Habits, Medication, Review, Templates, AI settings, Privacy & Sync, Notifications, Appearance, and Focus.
- Keep filtered and empty states simple and keyboard-friendly.

### 8. Material You, High Contrast, And Reduced Motion

Goal: make personalization and accessibility feel intentional.

- Keep dynamic color default-on where supported.
- Prefer `MaterialTheme.colorScheme` roles over hand-picked alpha-heavy colors.
- Use branded fallback colors only when dynamic color is unavailable or disabled.
- High contrast mode should:
  - Disable glass/blur.
  - Avoid low-alpha text.
  - Prefer solid `surface`, `surfaceContainer`, and `surfaceContainerHigh` surfaces.
- Reduced motion mode should:
  - Disable nonessential animated backdrops.
  - Reduce decorative progress/glow animations.
  - Keep essential progress updates readable and stable.

### 9. Android 17 And Large-Screen Validation

Goal: make the app behave well under Android 17 resizable and large-screen expectations.

- Validate compact, medium, and expanded widths.
- On expanded widths, prefer navigation rail or supporting-pane behavior over stretching the phone layout.
- Ensure the dial does not become oversized or cramped in landscape, foldable, split-screen, or desktop windowing.
- Confirm text and controls fit with large font settings.
- Confirm keyboard and mouse input can reach:
  - Command palette
  - Navigation
  - Primary actions
  - Dialog confirm/cancel controls
- Keep `ACCESS_LOCAL_NETWORK` out of the manifest unless LAN or companion-device sync actually ships.

## GitNexus Impact Requirements

This plan is a planning artifact only, but the implementation pass must follow the repo's GitNexus requirements before editing source symbols.

Run impact analysis before editing any touched function, class, or method. Minimum targets:

- `BottomFloatingNav`
- `TodayTab`
- `PlanTab`
- `FocusTab`
- `CommandPaletteDialog`
- `ChronosTheme`
- `TaskScreen`
- `HabitScreen`
- `MedicationScreen`
- `ReviewScreen`
- Any shared `core:ui` component edited during the pass

If impact analysis returns HIGH or CRITICAL risk:

- Stop before editing that symbol.
- Report direct callers, affected processes, affected modules, and risk level.
- Narrow the implementation or split the work before proceeding.

Before any commit or final source-change closeout, run GitNexus change detection. If this checkout still lacks normal `.git` metadata, use GitNexus `--skip-git`, focused source inspection, tests, Android CLI layout dumps, and screenshots as the verification record.

## Test Plan

### Focused JVM And Build Checks

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:ui:testDebugUnitTest :feature:daydial:testDebugUnitTest --no-daemon --max-workers=1
```

Run:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1
```

### Connected Tests

When an emulator or device is available, run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --max-workers=1
```

### Visual And Layout Verification

Capture or inspect:

- Compact phone, Today
- Compact phone, Plan
- Compact phone, Focus idle
- Compact phone, active focus session if available
- Drawer and parked feature navigation
- Command palette unfiltered and filtered
- Tasks, Habits, Medication, and Review
- Expanded/tablet or resizable emulator
- Dark theme
- Dynamic color enabled
- High contrast
- Reduced motion
- Large font scale

Use Android CLI where useful:

```powershell
android layout -p
android screen capture -o .\chronosflow-ux-pass.png
```

### Scenario Checks

- App launches to DayDial Today.
- Day/Plan/Focus remain the only primary navigation destinations.
- Quick-create does not overlap labels, system navigation, or content.
- Today next-best-action strip updates correctly for empty day, active block, upcoming block, and missed-block states.
- Focus idle state no longer shows disabled or misleading timer controls.
- Plan empty state offers a clear AI/manual planning path.
- Command palette opens globally from top bar and `Ctrl+K`.
- Palette search still finds parked features and settings.
- Tasks, Habits, Medication, and Review remain reachable through drawer and command palette.
- High contrast removes low-contrast glass effects.
- Reduced motion removes nonessential animation.
- Large-screen layout is clean under Android 17 resizable behavior.

## Acceptance Criteria

- Day/Plan/Focus remain obvious as the core product loop.
- The app feels like a coherent modern Material 3 productivity app.
- No navigation, quick-create, button, or text overlap appears in compact or expanded layouts.
- Focus idle state has a clear action and no dead controls.
- Today surfaces the next useful action without adding new persistence or scheduling rules.
- Plan reads as a coherent planning workflow.
- Supporting screens are cleaner but still secondary to DayDial.
- Command palette remains global, searchable, and backward compatible.
- Parked features remain reachable without becoming top-level sprawl.
- Android 17 large-screen behavior is clean.
- Tests and screenshots/layout dumps provide enough evidence to separate code failures from environment or emulator issues.

## Assumptions

- This file is a planning artifact only.
- Source implementation will happen in a later pass.
- No source code changes are required to create this plan.
- No database migration, route rename, new permission, or top-level navigation expansion is part of this pass.
- The next implementation pass will run GitNexus impact analysis before touching source symbols.
