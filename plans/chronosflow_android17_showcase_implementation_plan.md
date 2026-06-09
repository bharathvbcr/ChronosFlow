# ChronosFlow Android 17 Showcase Implementation Plan

This is a planning implementation artifact; for current product status and contract use `README.md` and `docs/product-contract.md`.

## Executive Summary

ChronosFlow should move from Android 16 readiness to an Android 17 showcase through a focused evolution, not a rewrite. The app already has the right foundation: a modular Jetpack Compose architecture, Android 17 SDK configuration, Room-backed local data, focus-session foreground notifications, Glance widget scaffolding, and a Day/Plan/Focus-centered product direction built around the 24-hour Chronos Dial.

The implementation should preserve the current planning model while improving the product surface in four areas:

- Make the app feel fully native to Android 17 and Material 3 / Material You.
- Turn the Chronos Dial into a smoother, more adaptive, and more explainable interaction surface.
- Promote Focus, medication, habits, and review insights into Android system surfaces without leaking sensitive data.
- Add local-first intelligence through mood, energy, semantic search, and planned-vs-actual correlation.

This plan assumes Android 17 maps to API 37 for this codebase, matching the current build configuration. Android 17-only behavior must be gated behind SDK and permission checks, with graceful fallback paths for API 35 and API 36 devices.

Before implementing code changes, follow the repo's GitNexus guidance: run impact analysis before editing symbols, warn before HIGH or CRITICAL risk changes, and run change detection before committing. This workspace may not have `.git`, so use GitNexus, focused tests, generated artifacts, and screenshots as the source of verification.

## Current Architecture Baseline

ChronosFlow is already structured around separable Android feature and core modules. The Android 17 showcase work should build on these boundaries instead of collapsing logic into the app module.

- `app`: owns the app shell, `MainActivity`, command palette wiring, top-level navigation, Android manifest declarations, app widget receiver, and the current Glance widget entry point.
- `feature:daydial`: owns the Chronos Dial screen, Day/Plan/Focus tabs, dial interaction surface, drag/resize callbacks, compact dial behavior, and primary daily planning UX.
- `feature:focus`: owns the foreground focus session service, focus timer surface, and focus session lifecycle interactions.
- `feature:medication`, `feature:habits`, and `feature:tasks`: provide user inputs that feed the daily planning model and should become first-class command palette and widget actions.
- `core:data`: owns Room entities, DAOs, repositories, migrations, and the persisted local state for tasks, time blocks, habits, medication plans, reviews, alarms, and focus sessions.
- `core:domain`: owns planner contracts, conflict detection, dial geometry, schedule validation, and use cases that must remain authoritative for schedule mutations.
- `core:ai`: owns AI-assisted planning, privacy mode decisions, deep-work window detection, and review-backed planning logic.
- `core:notifications`: owns alarm scheduling, focus notification rendering, Live Update compatibility logic, and notification fallback behavior.
- `benchmark`: should become the home for 120Hz dial, startup, and focus transition Macrobenchmark coverage.
- `wear`: should remain out of scope for the first Android 17 showcase pass unless a shared notification or widget contract requires a compatibility update.

Existing product direction remains valid:

- Day/Plan/Focus stay as the primary workflow.
- Tasks, habits, medication, review, templates, diagnostics, and settings remain reachable through the drawer, command palette, widgets, or contextual entry points.
- Planner semantics should not change during visual or performance work.
- Medication and mood data are sensitive local-first data by default.

## Phase 1: Material 3 and Edge-to-Edge Foundation

### Goal

Make ChronosFlow fully consistent with Android 17 edge-to-edge expectations and Material 3 theming while keeping the app's radial planning identity.

### Implementation Steps

1. Remove the remaining edge-to-edge opt-out styling and legacy system bar color assumptions.
   - Remove any `android:windowOptOutEdgeToEdgeEnforcement` usage from app styles.
   - Remove hard-coded white status/navigation bar assumptions from themes.
   - Keep transparent or platform-managed system bars as the default.

2. Keep `enableEdgeToEdge()` in `MainActivity`.
   - Treat `MainActivity` as the single app-level edge-to-edge entry point.
   - Avoid per-screen system bar overrides unless a specific surface needs temporary contrast management.

3. Apply insets at the correct UI layers.
   - Apply safe drawing and gesture insets to command surfaces, bottom controls, FABs, toolbars, and tappable dial affordances.
   - Let visual backgrounds, dial rings, and large decorative surfaces draw behind system bars where appropriate.
   - Do not apply broad padding at the root if it causes the dial to shrink unnecessarily.

4. Extend `ChronosTheme` with a focus-aware accent layer.
   - Add a `FocusAwareColorState` model that derives from active focus block, recent mood/energy check-in, theme mode, and accessibility settings.
   - Preserve wallpaper dynamic color as the base palette.
   - Use mood, energy, and active focus state only for dial accents, progress indicators, selected block outlines, and tertiary tones.
   - Avoid replacing the entire dynamic color scheme with mood colors.

5. Respect accessibility and user preferences.
   - High contrast mode should favor stronger outlines, tonal elevation, and text contrast over more saturated mood color.
   - Reduced motion should disable nonessential shared element, dial pulse, and backdrop animation.
   - Dynamic color opt-out should fall back to the existing stable Chronos palette.
   - Dark theme should keep the same semantic color mapping as light theme.

### Deliverables

- Updated theme/style definitions with no edge-to-edge opt-out.
- `FocusAwareColorState` or equivalent theme input.
- Theme tests or screenshot evidence for light, dark, dynamic color, high contrast, and reduced motion.
- A short migration note in the relevant implementation PR describing edge-to-edge behavior and fallback decisions.

### Acceptance Gate

The app launches into the Day Dial with transparent system bars, no clipped controls, no hidden command palette input, and no tappable UI behind gesture exclusion areas on compact and expanded layouts.

## Phase 2: Chronos Dial Interaction and Performance

### Goal

Make the 24-hour radial planner feel smooth at 120Hz while preserving planner correctness and conflict semantics.

### Implementation Steps

1. Introduce a `ChronosDialInteractionEngine` or equivalent domain/UI state boundary.
   - Keep raw pointer handling and transient drag state close to UI.
   - Move snap logic, preview generation, resize handles, conflict state mapping, and haptic cue selection into a testable boundary.
   - Keep `PlannerService` as the authoritative layer for conflict detection and schedule validation.

2. Add a stable `ChronosDialRenderModel`.
   - Precompute block arcs, free-time arcs, tick geometry, hit targets, labels, selection handles, and conflict overlays outside the Canvas draw loop.
   - Keep render model instances immutable or effectively stable.
   - Use stable IDs for arcs so transitions and test selectors can refer to blocks reliably.

3. Optimize Canvas rendering.
   - Use `drawWithCache` for static rings, hour ticks, labels, and reusable paths.
   - Avoid allocation inside the draw lambda.
   - Move color, brush, text measurement, and geometry calculations out of per-frame paths.
   - Use `derivedStateOf` or ViewModel-provided state for expensive transformations.
   - Keep pointer input handlers small and avoid reading large mutable state directly during every pointer event.

4. Preserve commit semantics.
   - Persist drag and resize changes only on commit, not during every pointer movement.
   - Continue using preview state for drag/resize feedback.
   - Ensure cancelled gestures restore the prior committed schedule.
   - Ensure locked/fixed blocks remain non-editable.

5. Improve haptics.
   - Add haptic cues for snap, warning conflict, locked conflict, and successful commit.
   - Use platform haptic feedback first for standard actions.
   - Use `VibrationEffect` only for custom effects that improve clarity beyond standard feedback.
   - Respect system haptics settings and reduced-motion/accessibility preferences.
   - Keep haptics quiet enough for frequent drag interactions.

6. Add conflict clarity.
   - Warning conflicts should show a preview state and allow the user to understand the overlap.
   - Blocking conflicts should visibly reject the placement and announce the reason through accessibility semantics.
   - Locked calendar/protected focus blocks should use distinct visual language from ordinary soft conflicts.

7. Add performance coverage.
   - Add Macrobenchmark coverage for startup, dial drag, dial resize, and transition from dial block to Focus Session.
   - Capture frame timing and jank evidence on a 120Hz-capable device or emulator where available.
   - Generate or update baseline profiles for launch, Day Dial, command palette, and Focus Session.

### Deliverables

- `ChronosDialInteractionEngine` or equivalent testable interaction layer.
- `ChronosDialRenderModel` or equivalent stable render model.
- Updated dial Canvas implementation using cached draw resources.
- Unit tests for hit testing, snapping, conflicts, resize handles, and haptic cue mapping.
- Macrobenchmark tests for drag/resize smoothness.

### Acceptance Gate

Dragging and resizing blocks should remain responsive without visible stutter, should not write to Room until commit, should preserve existing planner behavior, and should produce clear feedback for snap, soft conflict, locked conflict, and success.

## Phase 3: Focus Session Live Updates

### Goal

Make active focus sessions visible as Android 17 system-level ongoing activity while retaining notification compatibility on older devices.

### Implementation Steps

1. Add `LiveUpdateGateway` in `core:notifications`.
   - Centralize SDK checks, permission checks, notification eligibility, style selection, and fallback behavior.
   - Keep feature modules from calling Android 17 notification APIs directly.
   - Make the gateway injectable so tests can simulate API 35, API 36, and API 37 behavior.

2. Gate Android 17 behavior properly.
   - Check SDK level before using Android 17-only APIs.
   - Request and explain `POST_PROMOTED_NOTIFICATIONS` only when the app can provide a meaningful promoted ongoing notification.
   - Check whether promoted notifications can be posted before assuming chip/status behavior.
   - Continue to support ordinary foreground service notifications when promotion is unavailable.

3. Use promoted ongoing notifications only for valid user-initiated sessions.
   - Active Focus Session qualifies.
   - A user-started bounded medication dose window can qualify if it has a clear active duration.
   - Generic reminders, passive tips, and shortcuts should not use Live Updates.

4. Use Android 17 presentation where available.
   - Use `MetricStyle` for timer/countdown presentation where available and appropriate.
   - Use `ProgressStyle` on supported earlier APIs.
   - Retain NotificationCompat progress fallback for lower APIs.
   - Preserve pause, stop, and extend actions.

5. Add privacy controls.
   - Redact sensitive block titles on the lock screen when privacy mode requires it.
   - Use generic titles such as `Focus session active` when the underlying task name is sensitive.
   - Avoid medication names in promoted or lock-screen-visible surfaces unless the user opts in.

6. Keep foreground service behavior correct.
   - The existing Focus service remains responsible for lifecycle and recovery.
   - Notification style changes must not weaken foreground service requirements.
   - Task removal and recoverable session archival should continue to work.

### Deliverables

- `LiveUpdateGateway` with SDK/permission/style abstraction.
- Updated focus notification renderer using the gateway.
- Permission education UI or settings entry for promoted notifications.
- Tests for API 35, API 36, and API 37 notification behavior.
- Manual verification notes for promoted, non-promoted, and fallback notifications.

### Acceptance Gate

An active focus session appears as the richest eligible notification on Android 17, falls back cleanly on older APIs or denied permissions, keeps pause/stop/extend actions, and never exposes sensitive titles when privacy mode redaction is enabled.

## Phase 4: Medication, Habit, and Glance Widget Actions

### Goal

Make simple health and routine actions available from the home screen without forcing users into the full app.

### Implementation Steps

1. Expand the current Glance widget family.
   - Today Dial widget: shows current/next block, focus progress, and a compact status summary.
   - Focus Now widget: starts, pauses, resumes, or extends the current focus session.
   - Health Routine widget: marks medication dose acknowledgement and habit completion.

2. Add Glance `ActionCallback` implementations.
   - `DoseAcknowledgementAction`: marks a scheduled dose taken or snoozed.
   - `HabitMarkAction`: marks a habit occurrence complete or skipped.
   - `FocusWidgetAction`: starts, pauses, resumes, extends, or stops focus where safe.

3. Route actions through existing data boundaries.
   - Use repositories or WorkManager for work that may outlive the widget process.
   - Avoid direct database writes in UI-only widget code.
   - Reuse medication/habit/focus contracts so widget actions match in-app behavior.

4. Keep simple acknowledgement actions out of the full app.
   - Tapping `Taken`, `Done`, `Pause`, or `Resume` should complete the action in place.
   - Tapping a larger detail area can still open the app to the relevant route.
   - Use toast/snackbar-equivalent widget state updates only where supported by Glance behavior.

5. Treat medication carefully.
   - Generic medication reminders stay ordinary reminders.
   - Live Updates for medication apply only during a bounded, user-visible active dose window.
   - Lock screen, widget, and recents surfaces should redact medication names unless the user opts in.

6. Refresh widget state reliably.
   - Update widgets after repository writes.
   - Schedule refresh after alarms, focus state changes, habit completion, and day rollover.
   - Keep widget state lightweight and resilient to process death.

### Deliverables

- Three widget layouts or size-aware widget modes.
- `DoseAcknowledgementAction`, `HabitMarkAction`, and `FocusWidgetAction`.
- Repository or WorkManager integration for widget-side writes.
- Widget refresh wiring after data changes.
- Tests or manual evidence for widget actions without opening the app.

### Acceptance Gate

Users can acknowledge a medication dose, mark a habit, and pause/resume focus from a widget. The app should update persisted state and widget state without launching the full app for these simple actions.

## Phase 5: Semantic Search and AI-Assisted Planning

### Goal

Make daily review data, historical schedule patterns, and health/routine context searchable through the command palette and voice, while keeping sensitive data local by default.

### Implementation Steps

1. Add `SemanticPlanningIndex`.
   - Use AndroidX AppSearch as the local index.
   - Index daily reviews, time blocks, habits, medication events, and focus sessions.
   - Keep index documents small, scoped, and redaction-aware.

2. Define indexed document types.
   - `ReviewDocument`: date, summary, insights, mood/energy references, actual-vs-planned notes.
   - `TimeBlockDocument`: title, category, date, start/end, actual completion signals, focus metadata.
   - `MedicationEventDocument`: redacted medication label, status, dose time, adherence metadata.
   - `HabitEventDocument`: habit label, completion status, time window, streak metadata.
   - `FocusSessionDocument`: duration, block link, completion state, interruption notes.

3. Add semantic search with fallback.
   - Use `SemanticSearchNode` and embeddings when available.
   - Fall back to lexical AppSearch plus existing AI planner logic when semantic support is unavailable.
   - Do not require cloud AI for medication or mood queries.

4. Integrate with command palette.
   - Route search-like command palette input to `SemanticPlanningIndex`.
   - Show local results with clear provenance: review, time block, medication, habit, or focus.
   - For scheduling commands, generate a preview before applying planner mutations.

5. Support voice and stylus input.
   - Voice transcript should enter the same command parser as text.
   - Stylus handwriting should resolve to the same input pipeline when platform support is available.
   - Keep all modalities converging on one command preview and confirmation model.

6. Support target queries.
   - `When do I usually have the most energy?`
   - `Find days where medication was missed.`
   - `Schedule deep work when I perform best.`
   - `Show reviews where focus was interrupted.`
   - `What habits correlate with better afternoon energy?`

### Deliverables

- `SemanticPlanningIndex` service.
- AppSearch schema definitions and index refresh logic.
- Command palette semantic query route.
- Voice/stylus input consolidation plan or implementation.
- Tests for indexing, query fallback, redaction, and command preview generation.

### Acceptance Gate

The command palette can answer local planning-history questions and generate schedule previews without requiring cloud AI. Sensitive medication and mood data stay local unless the user explicitly enables cloud processing for a supported feature.

## Phase 6: Mood, Energy, and Insight Engine

### Goal

Turn mood and energy check-ins into useful local insight without making the app feel clinical, noisy, or invasive.

### Implementation Steps

1. Persist the existing mood/energy concept.
   - Add `MoodEnergyCheckInEntity`.
   - Add `MoodEnergyCheckInDao`.
   - Add `MoodEnergyRepository`.
   - Add Room migration coverage.
   - Link check-ins to date and optional time block ID.

2. Add `EnergyCorrelationEngine`.
   - Compare planned time blocks with actual focus/session data.
   - Include mood score, stress score, energy score, focus score, habit completion, and medication adherence.
   - Compute per-hour and per-category patterns.
   - Prefer explainable heuristics first; add ML-assisted ranking later only if enough local data exists.

3. Generate explainable insights.
   - Best deep-work window.
   - Energy drift.
   - Planning optimism.
   - Medication adherence pattern.
   - Habit timing correlation.
   - Focus underrun or interruption pattern.

4. Integrate with daily review.
   - Surface insights in Review, Plan, and command palette results.
   - Keep suggestions concise and tied to evidence.
   - Show confidence or data sufficiency when the pattern is weak.

5. Improve deep-work recommendations.
   - Feed `EnergyCorrelationEngine` output into the existing deep-work window detector.
   - Prefer windows with strong actual completion and energy evidence.
   - Avoid suggesting deep work near medication, protected rest, or historically low-energy periods unless the user requests it.

6. Add review insight types only where needed.
   - Add new `ReviewInsightType` values only for surfaced behaviors that cannot be represented by existing types.
   - Candidate additions: `ENERGY_PEAK`, `DEEP_WORK_WINDOW`, `PLANNING_OPTIMISM`, `MEDICATION_PATTERN`, and `HABIT_CORRELATION`.

### Deliverables

- Mood/energy Room entity, DAO, repository, and migration.
- `EnergyCorrelationEngine` with unit tests.
- Review insight integration.
- Deep-work recommendation integration.
- UI surfaces for insight explanation and data sufficiency.

### Acceptance Gate

After several check-ins and focus sessions, ChronosFlow can suggest plausible deep-work windows and explain why, while making it clear when there is not enough data to make a strong recommendation.

## Phase 7: Privacy, Encryption, and Sensitive Data Handling

### Goal

Protect medication, mood, review, and routine data as sensitive local-first data while still enabling useful Android system integrations.

### Implementation Steps

1. Add SQLCipher-backed Room support.
   - Add SQLCipher and required SQLite dependencies.
   - Introduce `ChronosSecureDatabaseProvider`.
   - Use a Room open helper factory backed by an encrypted key.
   - Keep database creation injectable for tests.

2. Protect database keys with Android Keystore.
   - Generate or wrap the database passphrase using Android Keystore.
   - Avoid storing raw encryption keys in SharedPreferences.
   - Define failure behavior for keystore reset, device restore, and corrupted key material.

3. Add a one-time migration from unencrypted to encrypted storage.
   - Detect existing unencrypted database.
   - Create encrypted destination.
   - Copy tables transactionally.
   - Verify row counts or migration markers.
   - Keep rollback or backup strategy for failed migrations.

4. Enable safer schema management.
   - Enable Room schema export.
   - Check in generated schemas if the project convention allows it.
   - Add migration tests for current and future versions.

5. Review backup and restore behavior.
   - Exclude encrypted database backup if restore cannot recover keys safely.
   - If backup is supported, document restore behavior and key recovery limits.
   - Make failure states user-readable and non-destructive.

6. Redact sensitive surfaces.
   - Notifications: hide medication names and mood details unless opted in.
   - Widgets: use generic labels when privacy mode is strict.
   - Recents: prevent sensitive screen snapshots where needed.
   - Lock screen: use public versions of notifications.
   - Command palette: do not show sensitive history unless the app is unlocked and privacy settings allow it.

7. Clarify Privacy Sandbox scope.
   - Treat Android Privacy Sandbox as SDK/ad isolation and measurement containment.
   - Do not describe Privacy Sandbox as the primary storage protection for medication or mood data.
   - If analytics or third-party SDKs are added later, keep them away from medication, mood, and review payloads.

### Deliverables

- `ChronosSecureDatabaseProvider`.
- SQLCipher Room integration.
- Keystore-backed key handling.
- Unencrypted-to-encrypted database migration.
- Migration and recovery tests.
- Privacy mode redaction policy for notifications, widgets, command palette, and recents.

### Acceptance Gate

Sensitive data persists in encrypted local storage, existing users can migrate without data loss, and Android system surfaces reveal only redacted information unless the user explicitly opts in.

## Phase 8: Adaptive Layouts for Tablets and Foldables

### Goal

Make ChronosFlow feel designed for phones, tablets, foldables, bubbles, and large-screen modes rather than stretched from a phone layout.

### Implementation Steps

1. Replace custom-only width class decisions with Material 3 adaptive APIs.
   - Use `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)`.
   - Keep existing compact behavior where it works, but move breakpoint decisions to official adaptive window classes.
   - Use Material 3 adaptive navigation and pane patterns.

2. Define compact phone behavior.
   - Centered dial.
   - Bottom navigation for Day/Plan/Focus.
   - Modal details or bottom sheets for block editing.
   - Compact command palette.
   - Preserve one-handed reach for primary actions.

3. Define medium screen behavior.
   - Dial plus supporting pane.
   - Navigation rail when width allows.
   - Supporting pane can show Now/Next, selected block details, focus state, or review insight preview.
   - Avoid stretching the dial to consume the full width.

4. Define expanded/tablet behavior.
   - Persistent dial pane.
   - Review/detail/editor pane.
   - Navigation rail or navigation suite scaffold.
   - Command palette centered with readable maximum width.
   - Timeline and review details use denser but still touch-friendly rows.

5. Define foldable behavior.
   - Use hinge-aware split where available.
   - Place the dial on one pane and editor/focus details on the other.
   - Avoid placing draggable dial controls under a hinge or fold.
   - In tabletop posture, prioritize focus timer, current block, and minimal controls.

6. Cap dial size.
   - Keep the dial capped to a readable maximum diameter.
   - Use added space for details, controls, context, and insight rather than endlessly scaling the radial view.
   - Ensure labels remain readable at large font sizes.

7. Preserve bubble and embedded behavior.
   - Compact bubble surfaces should keep dial read-only or limited-interaction if there is not enough space for safe drag/resize.
   - Embedded/bubble modes should not expose sensitive medication or mood data by default.

### Deliverables

- Adaptive layout policy for compact, medium, expanded, and foldable postures.
- Material 3 adaptive API integration.
- Updated DayDial layout with supporting panes.
- Tablet/foldable screenshots.
- UI tests for layout mode selection and no-overlap behavior.

### Acceptance Gate

On phone, tablet, and foldable layouts, the dial remains readable and usable, details are available without clutter, and the app no longer looks like a stretched compact phone screen.

## Phase 9: Testing, Benchmarks, and Rollout Gates

### Goal

Make the Android 17 showcase upgrade verifiable through targeted tests, screenshots, and runtime evidence.

### Unit Tests

- Dial geometry.
- Planner conflict handling.
- Drag/resize preview behavior.
- Haptic cue mapping.
- Energy correlation scoring.
- AppSearch indexing and semantic fallback.
- SQLCipher database open and migration.
- Live Update eligibility and style selection.
- Glance action callbacks.
- Privacy redaction policy.
- Command parser preview generation.

### Compose and UI Tests

- Edge-to-edge insets.
- Command palette predictive back dismissal.
- Command palette search and semantic result rendering.
- Shared element transition fallback when reduced motion is enabled.
- Tablet and foldable layout modes.
- Large font and high contrast behavior.
- Widget entry points opening the correct route when required.
- Medication, habit, and focus actions staying accessible by screen reader.

### Android Integration Tests

- API 35 fallback behavior.
- API 36 progress notification behavior.
- API 37 promoted ongoing and MetricStyle behavior.
- Notification permission flows.
- Promoted notification permission flow.
- Widget action execution.
- Exact alarm fallback.
- Encrypted database migration from an existing unencrypted database.
- App process death and focus session recovery.

### Performance Benchmarks

- Macrobenchmark startup.
- Macrobenchmark Day Dial initial render.
- Macrobenchmark dial drag.
- Macrobenchmark dial resize.
- Macrobenchmark Focus Session transition.
- Baseline profile generation for launch, Day Dial, command palette, and Focus Session.
- Frame timing evidence for 120Hz-capable devices where available.

### Rollout Gates

1. Phase branch or checkpoint builds without compile errors.
2. Unit tests pass for modified modules.
3. UI tests pass for impacted screens.
4. API 35/36/37 smoke tests pass.
5. No sensitive medication or mood text appears in public notification/widget surfaces unless opted in.
6. Macrobenchmark results show no dial interaction regression.
7. GitNexus change detection confirms affected symbols and flows match the intended scope before commit.

### Suggested Focused Commands

Use focused commands first, then broaden only when needed:

```powershell
.\gradlew.bat :core:domain:testDebugUnitTest :feature:daydial:testDebugUnitTest --no-daemon --max-workers=1
```

```powershell
.\gradlew.bat :core:data:testDebugUnitTest :core:notifications:testDebugUnitTest --no-daemon --max-workers=1
```

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon --max-workers=1
```

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest --no-daemon --max-workers=1
```

If GitNexus reports a stale index, refresh it before impact or change detection:

```powershell
npx gitnexus analyze --skip-git
```

## API and Data Model Additions

The following names are proposed interfaces, classes, entities, or equivalent concepts. Exact package placement should follow existing module ownership.

### `FocusAwareColorState`

Purpose: Provide theme and dial accent inputs derived from focus, mood, energy, accessibility, and dynamic color state.

Suggested ownership: `core:ui` or `feature:daydial` if it remains dial-specific.

Suggested fields:

- `activeBlockId: String?`
- `activeBlockCategory: String?`
- `energyScore: Int?`
- `moodScore: Int?`
- `focusScore: Int?`
- `isHighContrast: Boolean`
- `isReducedMotion: Boolean`
- `dynamicColorEnabled: Boolean`

### `ChronosDialRenderModel`

Purpose: Stable precomputed representation of all dial draw data.

Suggested ownership: `feature:daydial` for UI rendering, with geometry primitives kept in `core:domain`.

Suggested fields:

- `blockArcs`
- `freeTimeArcs`
- `hourTicks`
- `labels`
- `selectedHandles`
- `currentTimeNeedle`
- `conflictOverlays`
- `accessibilityDescriptions`

### `ChronosDialInteractionEngine`

Purpose: Convert pointer and stylus input into preview, commit, haptic, and accessibility outcomes.

Suggested ownership: `feature:daydial` with planner calls delegated to `core:domain`.

Required behavior:

- Uses `PlannerService` for validation.
- Emits preview state without persistence.
- Emits commit command only on drag/resize completion.
- Emits haptic cue and conflict reason.
- Handles cancellation and rollback.

### `ChronosHaptics`

Purpose: Centralize haptic cue mapping for dial interactions and command confirmations.

Suggested cues:

- `Snap`
- `WarningConflict`
- `LockedConflict`
- `SuccessfulCommit`
- `CommandAccepted`
- `CommandRejected`

### `LiveUpdateGateway`

Purpose: Encapsulate Android 17 promoted ongoing notification, MetricStyle, ProgressStyle, and fallback selection.

Suggested ownership: `core:notifications`.

Required behavior:

- Checks SDK level.
- Checks notification permission state.
- Checks promoted notification eligibility.
- Selects MetricStyle, ProgressStyle, or NotificationCompat fallback.
- Applies privacy redaction.
- Exposes testable decision output.

### `MoodEnergyCheckInEntity`

Purpose: Persist mood, stress, energy, and focus scores.

Suggested ownership: `core:data`.

Suggested fields:

- `id`
- `checkInDate`
- `recordedAt`
- `blockId`
- `moodScore`
- `stressScore`
- `energyScore`
- `focusScore`
- `notes`

### `MoodEnergyCheckInDao`

Purpose: Query check-ins by date, block, range, and recent history.

Required queries:

- Get check-ins for date.
- Get check-ins for date range.
- Get latest check-in.
- Get check-ins linked to a block.
- Insert/update/delete check-ins.

### `MoodEnergyRepository`

Purpose: Provide domain-facing mood/energy access without exposing Room details.

Required behavior:

- Save check-in.
- Observe current day check-ins.
- Query historical ranges.
- Provide redacted or aggregate data to AI/search layers.

### `EnergyCorrelationEngine`

Purpose: Generate local insights from planned-vs-actual behavior and check-ins.

Required inputs:

- Time blocks.
- Actual time segments.
- Focus sessions.
- Mood/energy check-ins.
- Habit completions.
- Medication adherence events.

Required outputs:

- Deep-work window candidates.
- Energy peak windows.
- Drift and underrun insights.
- Habit and medication timing correlations.
- Confidence/data sufficiency metadata.

### `SemanticPlanningIndex`

Purpose: App-local searchable index for reviews, blocks, habits, medication, and focus sessions.

Suggested ownership: `core:ai` or a new search-oriented core package if it grows.

Required behavior:

- Index local planning documents.
- Query with semantic search when available.
- Fall back to lexical AppSearch.
- Redact sensitive data based on privacy settings.
- Return result provenance and confidence.

### `ChronosSecureDatabaseProvider`

Purpose: Create and migrate encrypted Room database instances.

Suggested ownership: `core:data`.

Required behavior:

- Creates SQLCipher-backed Room database.
- Retrieves/wraps encryption key through Android Keystore.
- Handles migration from unencrypted DB.
- Supports test database creation.
- Defines recovery behavior for key or migration failure.

### `DoseAcknowledgementAction`

Purpose: Glance action callback for medication acknowledgement.

Required behavior:

- Marks dose taken, skipped, or snoozed.
- Updates widget state.
- Respects privacy redaction.
- Avoids launching app for simple acknowledgement.

### `HabitMarkAction`

Purpose: Glance action callback for habit completion.

Required behavior:

- Marks habit complete or skipped for the current date/window.
- Updates streak or completion data through repository logic.
- Updates widget state.

### `FocusWidgetAction`

Purpose: Glance action callback for focus controls.

Required behavior:

- Starts, pauses, resumes, extends, or stops focus where safe.
- Delegates foreground-service work correctly.
- Updates widget and notification state.

## Risk Register

### Promoted Live Updates may not always appear as chips

Risk: Android 17 promoted ongoing notifications depend on API eligibility, user settings, permission state, notification characteristics, and OEM behavior.

Mitigation:

- Treat promotion as best effort.
- Always keep a correct foreground notification fallback.
- Add UI copy that says enabling promoted notifications improves system visibility but does not guarantee placement.
- Test promoted and non-promoted states.

### SQLCipher migration can cause data loss if staged poorly

Risk: Moving from an unencrypted Room database to encrypted storage touches all persisted user data.

Mitigation:

- Build migration as a separate phase.
- Add migration tests using realistic pre-migration databases.
- Verify row counts and migration marker state.
- Keep backup/rollback strategy for failed migration.
- Do not combine encryption migration with unrelated schema changes.

### Mood-based color can harm accessibility or trust

Risk: A strong mood-derived palette could make the app feel unpredictable or expose private state visually.

Mitigation:

- Use mood/energy only as subtle accent input.
- Preserve dynamic color as the base palette.
- Disable or soften mood accents in high contrast, reduced motion, and privacy-sensitive modes.
- Provide a user setting to turn focus-aware accents off.

### Semantic search may expose sensitive data

Risk: Medication, mood, and review content may appear in command results or be sent to cloud AI if boundaries are unclear.

Mitigation:

- Keep semantic indexing local.
- Redact indexed medication labels where privacy mode requires it.
- Never require cloud AI for medication or mood queries.
- Add tests for redaction and cloud-disabled behavior.

### Dial performance work could change planner semantics

Risk: Moving interaction logic for performance may accidentally alter drag, resize, conflict, or locked-block behavior.

Mitigation:

- Keep `PlannerService` authoritative.
- Add tests against existing planner behavior before refactor.
- Use preview state for UI responsiveness and commit through existing planner use cases.
- Compare before/after behavior for create, move, resize, locked, and conflict paths.

### Predictive back and shared transitions may behave differently across APIs

Risk: Back progress and shared transitions may not be available or visually consistent on all supported versions.

Mitigation:

- Gate enhanced behavior by API and Compose support.
- Use built-in Material components where possible.
- Respect reduced motion.
- Provide simple fade/slide fallback for older APIs.

### Widget actions can race with app state

Risk: Glance actions may run while the app process is dead, focus service is restarting, or database migration is in progress.

Mitigation:

- Route durable work through repositories or WorkManager.
- Make widget actions idempotent.
- Refresh widget state after writes.
- Show neutral fallback content when state cannot be loaded.

### Android system surfaces can leak sensitive context

Risk: Notifications, widgets, recents screenshots, lock screen, and command search can reveal medication or mood data.

Mitigation:

- Define a single redaction policy.
- Use public notification versions.
- Redact widget text by default for sensitive categories.
- Add UI tests or screenshots for strict privacy mode.

## Acceptance Criteria

The Android 17 showcase implementation is complete when all of the following are true:

- The Markdown implementation plan exists at `plans/chronosflow_android17_showcase_implementation_plan.md`.
- Implementation phases, API additions, risks, and test gates are documented clearly.
- Android 17-only behavior is separated from backwards-compatible fallback behavior.
- Day/Plan/Focus remains the primary UX.
- The Chronos Dial remains the primary planning surface.
- Planner semantics are preserved while dial rendering and interaction become smoother.
- Medication and mood data are treated as sensitive local-first data.
- Focus Live Updates work on eligible Android 17 devices and fall back cleanly elsewhere.
- Glance widgets support simple dose, habit, and focus actions without always opening the app.
- Semantic search and AI-assisted planning can operate locally for sensitive queries.
- Mood and energy insights are explainable and data-sufficiency aware.
- SQLCipher encryption and Keystore key handling protect local sensitive data.
- Tablet and foldable layouts use adaptive panes rather than stretched phone layouts.
- Macrobenchmark and baseline profile evidence exists for startup, dial interaction, and focus transition.
- API 35, API 36, and API 37 smoke tests pass or any environment-only failure is documented separately.
- GitNexus change detection confirms the affected symbols and execution flows match the intended implementation scope before commit.
