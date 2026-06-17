# ChronosFlow iOS — macOS build & verification handoff

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
