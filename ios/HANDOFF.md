# ChronosFlow iOS — macOS build & verification handoff

## 0. Parity status (2026-07-01)

A full verification pass re-audited every divergence in `PARITY_GAP_REPORTS.json` against the
current tree, then closed the remaining actionable gaps. Landed in this pass: Today-tab dial +
shared browsed date + meds quick items + running-focus card + missed-block badge; start-focus from
Plan blocks; quick-capture intent classification (task/med/habit, ported CaptureIntentClassifier +
tests) with routing + question→assistant; palette recents + digest row + ⌘K; journal composer from
Quick-Add + per-field AI polish; task permission-alert accuracy, per-chip smart-fill apply with
reasons, contextual schedule section, recurring reminders, auto-apply assist setting; habit editor
recurrence presets/templates/history hints; medication form AI-assist (MedicationAssist planner +
tests); Insights journal-history/evening-reflection/sleep cards; calendar timeline schedule-side
sources with quick actions; save-today-as-routine; planning toggles wired into plan regeneration
(PlanRegeneration policy + tests).

A follow-up pass then closed four of the deferred items: the **iPad adaptive navigation rail**
(regular-width leading rail + FAB, shared badge derivations); **task attachments / linked contact /
external actions** (persisted model fields, editor Connect sections, row + context-sheet action
buttons, backup round-trip, ChronosCore DTOs + tests); **command-palette entity search** ("Your
data" across tasks/habits/goals/meds/routines/journal via `rankCommands`); and **cadence-aware
habit scheduling** (every-N-days / weekly-interval / quota due-derivation in ChronosCore, parsed
from the cadence string so no schema change, honored by the Habits pill + Today quick-list).

**Consciously deferred (known, accepted divergences)** — build briefs (constraint, approach, files,
guardrails) for all five are in [`DEFERRED_PARITY.md`](DEFERRED_PARITY.md):
- Unified cross-tab undo/redo (iOS history is Plan-scoped; Android shares one stack).
- Review as a 4th in-dial tab (iOS presents Review as a sheet route — accepted shell redesign).
- Command-palette on-device LLM re-rank (`rankCommandIdsWithAssist`) + AppSearch-backed
  `SemanticPlanningIndex` (iOS has deterministic name-ranked entity search; no per-keystroke model
  call, no AppSearch analogue).
- DeviceActivity-backed screen-time insights (restricted entitlement; card remains a placeholder).
- Android manual-missed registry (iOS derives missed blocks from scheduled end + no actual end).

This port was authored on Windows; the portable logic core (`ChronosCore`) is compiled and
unit-tested there (**223 tests, 0 failures**), but the app/watch/widget targets link Apple
frameworks and can only be built on **macOS + Xcode 27 (iOS 27 SDK)**. This doc is the ordered
checklist to take it from here to a running app, plus the handful of API points that need a real
SDK to confirm.

---

## 1. Build

```bash
cd ios
brew install xcodegen xcbeautify          # one-time
xcodegen generate                          # regenerate the .xcodeproj (REQUIRED — see §2)
xcodebuild -resolvePackageDependencies -project ChronosFlow.xcodeproj
./build.sh                                 # iOS app → Simulator
./build.sh watch                           # watchOS app (+ watch widget) → Simulator
./build.sh core                            # portable ChronosCore tests (also runs on Windows/Linux)
```

CI does all of this on every push (`.github/workflows/ios.yml`): `build-app` (iOS app + `ChronosWatch`
scheme on a `macos-15` runner) and `build-core` (Linux). Start a manual run with **Actions → iOS →
Run workflow** to shake out SDK-signature drift before touching a device.

## 2. Why you MUST regenerate the project

The committed `ChronosFlow.xcodeproj` predates several structural additions that only live in the
canonical `project.yml`. Building the stale `.xcodeproj` will fail to link. `xcodegen generate`
picks up:

- the **`ChronosCore`** local SwiftPM package dependency (the app/widgets/watch now `import ChronosCore`);
- the shared **`ChronosFlowWidgets/WidgetIntents.swift`** compiled into the app target;
- the **`ChronosWatch`** watchOS app and **`ChronosWatchWidgets`** complication targets;
- explicit **schemes** (`ChronosFlow`, `ChronosWatch`) so `xcodebuild -scheme` is deterministic.

## 3. Capabilities to provision (Signing & Capabilities, per target)

Simulator builds run with no signing. For **device** builds set `DEVELOPMENT_TEAM` in `project.yml`
(or Xcode) and enable, on each target:

| Capability | Targets | Notes |
|---|---|---|
| **App Groups** `group.com.chronosflow.shared` | app, `ChronosFlowWidgets`, `ChronosWatch`, `ChronosWatchWidgets` | Shared store (phone side) + on-watch widget cache. Already in the `.entitlements` files. |
| **HealthKit** | app | Read-only sleep. `NSHealthShareUsageDescription` already in Info.plist. |
| **Live Activities** | app | `NSSupportsLiveActivities` already set. |
| **Background Modes → Background fetch** | app | For `BGTaskScheduler` auto-backup (`BGTaskSchedulerPermittedIdentifiers` already set). |
| **Face ID** | app | `NSFaceIDUsageDescription` already set (Medications lock). |
| **Speech + Microphone** | app | Journal dictation usage strings already set. |
| **iCloud → CloudKit** + container `iCloud.com.chronosflow` | app (+ `ChronosWatch` if syncing the watch) | ONLY if you turn on sync — see §5. Snippet is commented in `ChronosFlow.entitlements`. |
| Watch pairing | `ChronosWatch` | `WKCompanionAppBundleIdentifier` already set; no extra entitlement for WatchConnectivity. |

## 4. API points to confirm against the live SDK

Everything statically traceable was reviewed clean. Two iOS-26/27 surfaces could only be verified on
a Mac; both are isolated to a single fix-point with a safe fallback:

1. **Foundation Models text streaming** — `ios/ChronosFlow/AI/ChronosAssistant.swift`, `send(_:)`.
   The loop reads `snapshot.content` from `session.streamResponse(to:)`. If the shipping
   `ResponseStream<String>` yields the partial value directly instead of a `Snapshot`, change the one
   line to `currentReply = snapshot`. Either way, `send` already **falls back at runtime** to the
   confirmed non-streaming `respond(to:)` (`respondOnce`) if streaming throws, so chat still works.
2. **`toolbarMinimizeBehavior(.onScrollDown, for:)`** — funneled through the single helper
   `ios/ChronosFlow/DesignSystem/ChronosToolbar.swift` (`chronosScrollMinimizedBar()`), used by every
   screen. If the signature differs, fix it once there.

The rest of the Foundation Models surface (`respond(to:options:)` → `.content`,
`respond(to:generating:options:)`, `SystemLanguageModel.default.availability` cases, `Tool`/
`ToolOutput`, `GenerationOptions(temperature:)`, `@Generable`/`@Guide`) is corroborated by Apple docs.
The on-device planner/assistant require an Apple-Intelligence-capable device/sim with the feature on;
when unavailable they surface a friendly reason and the rest of the app is unaffected.

## 5. Turning on iCloud sync

1. Add the iCloud capability + `iCloud.com.chronosflow` container to `ChronosFlow.entitlements`
   (uncomment the snippet) and, for watch sync, the same to `ChronosWatch.entitlements`.
2. In-app: **Settings → Data → iCloud sync**. The flag persists to the App Group; `ChronosStore`
   reads it at container-build time, so it applies on the **next launch**. If CloudKit isn't
   provisioned the store falls back to the local container (no data loss).

## 6. Manual test plan (device or simulator)

- **Dial / Plan** — 3 rings render (calendar=outer, plan=middle, tasks/habits/meds=inner); night band,
  free-time dashed arcs, conflict arcs; drag a block (live snapped preview + handles); tap empty → create.
- **Focus** — pick a block + split preset (No breaks / 25·5 / 50·10 / 30·5); it holds at each phase
  boundary (phase dots); Live Activity shows countdown + Pause/Stop/+5m; controls drive the live timer.
- **Tasks** — smart-fill ("Email mom tomorrow at 9am urgent every week" → detection chips); templates;
  Add & New; checklist paste-split + reorder; AI rewrite/proofread on the notes field.
- **Habits / Goals** — streak + 7/30-day completion-rate cards; missed-habit "Schedule at…"; goal +1.
- **Medication** — Face ID gate (toggle in Settings → Privacy); adherence + refill cards; multi-time reminders.
- **Sleep** — Apple Health import card (grant → import; stage-derived quality; manual entries never
  overwritten); manual log validation hints; readiness banner; verify a depleted night shifts demanding
  tasks ≥ 11:00 in gap-fill / AI plan.
- **Journal** — guided prompts + dictation; history expand/collapse + "showing X of Y".
- **Insights** — Day/Week/Month rollups; category breakdown + concentration; severity-ranked findings.
- **Routines** — apply (instantiates blocks) vs Mark complete (no re-instantiate); isActive toggle.
- **Widgets** — Today/Tasks/Habits/Medication; tap actions (complete task, toggle habit, mark dose,
  start focus) mutate the store and reload.
- **App Intents / Siri** — "Add task…", "What's next", "Start focus", log habit/med/sleep/mood, etc.
- **Watch** — Today/Tasks/Habits/Focus mirror the phone over WatchConnectivity; tapping sends a command
  back; complication / Smart Stack shows next block or live focus countdown.
- **Notifications** — medication Taken/Snooze/Skip, task/habit Complete/Snooze; refill-soon line;
  quiet-hours shifting.
- **Backup** — export/restore JSON; automatic daily backup toggle + stored snapshots list.

## 7. Known limitations (deferred)

- Weekly recurrence with **interval > 1 AND an explicit weekday set** re-anchors its week math to the
  completion date on each spawn (intended for the advance-on-complete model; not a fixed-calendar series).
- Watch complication timeline / cross-device focus mirror / streaming-with-tools are correct by
  construction but only runtime-verifiable on a paired device with Apple Intelligence.

## 8. Android→iOS parity update (2026-06-21) — macOS finalization checklist

A large parity pass brought iOS up to the current Android app. The plan + per-area gap reports are in
`ios/PARITY_PLAN.md` and `ios/PARITY_REPORTS.json`. **ChronosCore is fully built & tested on Windows
(680 tests green)**; the app/widget/watch targets were authored correct-by-construction but **could not
be compiled here (no iOS SDK)**. Do this on macOS + Xcode 27 first:

1. `cd ios && xcodegen generate` (project.yml gained watch-widget targets) → `./build.sh`, fix residual
   type/signature drift (the only class of issue that can't be caught on Windows).

**Known wiring TODOs the authors flagged (each is a small, local change):**
- **Register `FocusSessionSnapshot` @Model** in `ChronosStore.schema` (Persistence/ChronosStore) — added
  in Phase 1 but not yet in the container schema, so split-session restore + widget fetch won't work until it is.
- **Publish the focus widget snapshot:** call `FocusWidgetBridge.publish(...)` from `FocusTimerModel` on
  every session change (start/pause/advance/extend/stop; `publish(nil)` on stop) + `WidgetCenter.shared
  .reloadTimelines(ofKind:"ChronosFocusWidget")`. Until then the home `FocusWidget` shows its idle state
  (still functional). Mirrors the existing `FocusCommandBridge` app-side TODO.
- **Activate interop sync:** `ChronosFlowApp`'s `ChronosBackgroundSync` stub handler for
  `com.chronosflow.interop.sync` should call `InteropSync.handleRefresh()` (matching task id already wired).
- **Share extension + ChronosCore:** `ChronosShareExtension` is not linked against ChronosCore, so
  `ShareViewController` mirrors `splitSharedTaskLines` inline. Either add the dependency in `project.yml` or
  keep the (functionally identical) inline copy.
- **Name disambiguation to confirm under the compiler:** `import ChronosCore` shadows app types in DayDial
  (`PlannerMath`/`FreeWindow`/`BlockSpan`/`BlockProvenance`/`BlockFlexibility`/`EnergyIntensity`) and the
  `JournalMood` (CC struct vs app enum) / `SleepReadiness` (CC vs app enum, bridged by rawValue) pairs —
  references were qualified, but verify no ambiguity.
- **Minor/deferred:** `DoseStatus` has no `.paused` (pause currently logged as `.snoozed`); the +15m snooze
  reschedule belongs in the `UNUserNotificationCenterDelegate`; `aiEnabled` could join the feature-graduation
  companion wave in `ChronosSettings`; medication SCREEN_TIME insights have no public iOS API (out of scope).
- **Entitlements** (App Groups / HealthKit / EventKit / Live Activities / Face ID / Background fetch) must be
  provisioned per §3 — interop, calendar export, sleep import, and widgets fail silently without them.
- **Foundation Models** call sites (planner JSON parse, journal/insights/text-tools, assistant streaming)
  fall back to the ChronosCore offline heuristics when the model is unavailable; confirm the streaming
  `snapshot.content` shape against the shipping SDK (see §4).
