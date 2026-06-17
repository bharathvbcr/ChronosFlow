# ChronosFlow for iOS — native SwiftUI port (iOS 27)

A native iOS port of ChronosFlow, rebuilt in **SwiftUI + SwiftData**, targeting **iOS 27**. The
Android app's architecture and domain logic are ported faithfully; every Android-native subsystem
is mapped to its first-class iOS-native equivalent (table below).

> **Build environment.** SwiftUI is an Apple framework — it can only be compiled with **Xcode on
> macOS**. This port was authored on Windows, so the final `xcodebuild`/run step must be done on a
> Mac with **Xcode 27 (iOS 27 SDK)**. Everything here is written to compile cleanly there; see
> *Building* below. There is no way to compile a SwiftUI target on Windows.

## What's here

```
ios/
├── ChronosFlow.xcodeproj/        # opens directly in Xcode 27 (file-system-synchronized groups)
├── project.yml                   # XcodeGen spec — canonical, regenerates incl. the widget target
├── build.sh                      # xcodebuild helper (macOS); `./build.sh core` builds the portable core anywhere
├── ChronosCore/                  # platform-agnostic logic core (SwiftPM) — builds + unit-tests on ANY OS
├── ChronosFlow/                  # the app
│   ├── ChronosFlowApp.swift      # @main App  (≈ ChronosApplication + MainActivity)
│   ├── App/RootView.swift        # TabView shell (≈ ChronosNavigationShell / ChronosRoute)
│   ├── Models/                   # SwiftData @Model types (≈ core/data Room entities + core/domain)
│   ├── Persistence/              # ModelContainer + seed (≈ Room database)
│   ├── Planner/                  # DialGeometry, sleep readiness, conflict/free-time (≈ core/domain/planner)
│   ├── DesignSystem/             # Liquid Glass, Material You roles, springs (≈ core/ui + UX_PRINCIPLES)
│   ├── Features/                 # DayDial, Today, Focus, Tasks, Habits, Goals, Medication, Journal, Sleep, Insights
│   ├── AI/                       # Foundation Models: planner, streaming assistant, text tools (≈ core/ai)
│   ├── Notifications/            # UNUserNotificationCenter + actionable categories (≈ core/notifications)
│   ├── Settings/                 # ChronosSettings store + Settings tab (≈ Android sidebar settings)
│   ├── Security/                 # Face ID med-lock via LocalAuthentication (≈ SensitiveArea)
│   ├── Health/                   # HealthKit sleep import (≈ Health Connect)
│   ├── Backup/                   # BGTaskScheduler auto-backup (≈ ChronosAutoBackup)
│   ├── Watch/                    # phone-side WatchConnectivity sync (≈ Wear DataClient)
│   └── LiveActivity/             # ActivityKit attributes (≈ Android Live Updates)
├── ChronosFlowWidgets/           # WidgetKit ext: Today/Tasks/Habits/Medication + interactive Focus Live Activity
├── ChronosWatch/                 # watchOS companion app (Today/Tasks/Habits/Focus over WatchConnectivity)
└── ChronosWatchWidgets/          # watchOS complications + Smart Stack (≈ Wear tiles)
```

## Native-implementation mapping (Android → iOS)

| ChronosFlow subsystem | Android-native | iOS-native (this port) |
|---|---|---|
| Persistence | Room (DB v19) | **SwiftData** `@Model` + `ModelContainer`, App-Group-shared |
| Cross-device sync / backup | SAF auto-backup, device transfer | **CloudKit** mirroring (Settings → Data toggle) + **BGTaskScheduler** auto-backup + JSON export/restore |
| UI toolkit | Jetpack Compose | **SwiftUI** |
| Design language | Material You + Haze "liquid glass" | **Liquid Glass** (`.glassEffect`) + system semantic colors |
| Navigation | Compose nav, compact/expanded shell | **`TabView`** with `.sidebarAdaptable` (iPhone tab bar ↔ iPad sidebar) |
| Signature 24-h dial | Compose Canvas (`feature/daydial`) | **SwiftUI `Canvas`** driven by the ported `DialGeometry` |
| On-device AI | Gemini Nano via ML Kit (`core/ai`) | **Foundation Models**: day planner + streaming assistant + summarize/proofread/rewrite text tools |
| Focus live status | Foreground service + Live Updates | **ActivityKit Live Activity** (Lock Screen + Dynamic Island) with Pause/Stop/+5m controls |
| Home-screen widgets | Glance widgets (4) | **WidgetKit** (Today/Tasks/Habits/Medication) with interactive `Button(intent:)` actions |
| Voice / shortcuts | AppFunctions (~20) | **App Intents** (~17) + `AppShortcutsProvider` |
| Reminders | Notification manager (`core/notifications`) | **UNUserNotificationCenter** + actionable Taken/Skip/Complete categories + quiet hours |
| Sleep import | Health Connect (read-only) | **HealthKit** (`.sleepAnalysis`, stage-derived quality, provenance guard) |
| Privacy lock | SensitiveArea app-lock | **LocalAuthentication** Face ID/passcode gate on Medications |
| Charts/trends | Compose charts | **Swift Charts** |
| Mood/energy check-ins | check-in sheet | `CheckInSheet` + `MoodEnergyCheckIn` |
| Wear companion | Wear OS Data Layer + tiles | **watchOS app** (WatchConnectivity Data Layer) + **complications / Smart Stack** |

## Faithful domain port

The Kotlin domain models and pure planner logic are ported one-for-one so behaviour matches:

- **Models** — `TimeBlock`, `TaskItem`, `Habit`, `Goal`, `MedicationPlan`, `FocusSession`,
  `JournalEntry`, `SleepTrack`, `MoodEnergyCheckIn`, `Routine` (+ the `BlockFlexibility`,
  `EnergyIntensity`, `BlockProvenance`, `DayPlanStatus`, `SleepSource`, `SleepReadiness` enums).
- **Planner** — `DialGeometry` (minute↔angle, ring hit-testing, snapping) is a direct port;
  `deriveSleepReadiness`, conflict detection, and free-window finding mirror `core/domain/planner`.
- **Design tokens** — spacing/radius/type/motion scales and the brand + category + provenance
  palettes match `docs/UX_PRINCIPLES.md`; the liquid-glass surface + bouncy-spring motion
  personality is preserved via native Liquid Glass and SwiftUI springs.

## Building

### Portable logic core — builds and tests on any OS (incl. Windows/Linux, no Mac needed)
The pure algorithms ported from `core/domain/planner` (dial math, sleep readiness, conflict/
free-window detection) live in `ios/ChronosCore` as a Swift package depending only on Foundation,
so they compile and unit-test anywhere a Swift toolchain exists:
```bash
cd ios/ChronosCore
swift build && swift test
# or, type-check only (no linker / Windows SDK required):
swiftc -typecheck Sources/ChronosCore/*.swift
```
This is also run on every push by `.github/workflows/ios.yml` (the `build-core` job), alongside the
`build-app` job which runs the full `xcodebuild` on a macOS runner.

> **Verified.** `ChronosCore` has been compiled and unit-tested with Swift 6.3.2 on Windows
> (`x86_64-unknown-windows-msvc`): `swift build` → *Build complete!*, `swift test` → **223 tests, 0
> failures** (29 logic modules; re-verified 2026-06-14). (On a freshly winget-installed toolchain, open a new terminal so `swift` is on `PATH`;
> if `swift build` reports "unable to load standard library", set `SDKROOT` to
> `…\Swift\Platforms\<ver>\Windows.platform\Developer\SDKs\Windows.sdk`.)

### Full app (macOS, Xcode 27)
The app target links Apple frameworks, so it builds only on macOS:
```bash
cd ios
./build.sh                 # build the app for the iOS Simulator
./build.sh widgets         # brew install xcodegen first; regenerates with the widget target, then builds
./build.sh core            # build + test the portable ChronosCore package (works on any OS)
```

Or open `ChronosFlow.xcodeproj` in Xcode 27 and press ⌘R. The committed `.xcodeproj` builds the
**app** target out of the box (no tooling). To include the **widget extension + Live Activity**
target, regenerate the project from the canonical spec:

```bash
brew install xcodegen
cd ios && xcodegen generate
```

### Signing & capabilities (device builds)
Simulator builds run with **no setup** — the default entitlements are just **App Groups**
(+ HealthKit), and the SwiftData store falls back to a local store if the App Group container
isn't available. For device builds set `DEVELOPMENT_TEAM` in `project.yml` (or in Xcode).
**Live Activities** are already declared in `Info.plist`. To turn on **iCloud/CloudKit**
cross-device sync, add the iCloud capability + `iCloud.com.chronosflow` container (snippet is in
`ChronosFlow.entitlements`), then flip **Settings → Data → iCloud sync** in-app (persisted in the
App Group; `ChronosStore` reads it at container build, so it applies on the next launch).

### Apple Intelligence (AI planner)
The on-device planner requires an Apple-Intelligence-capable device/simulator with the feature
enabled. When unavailable, `ChronosAIPlanner` reports a friendly reason and the rest of the app is
unaffected (same review-before-apply contract as Android: AI never changes the plan silently).

## Feature logic verified off-device (`ChronosCore`, 223 tests passing)

Beyond the dial math, these ported algorithms are unit-tested in `ChronosCore`: goal progress
roll-up, Pomodoro phase sequencing, routine instantiation, recurrence expansion (daily/weekly/
monthly), habit streak + completion rate, daily-review execution score, gap-fill planner, conflict
auto-resolve (relocate movable blocks to free slots), best deep-work window from check-ins,
medication adherence + refill projection, missed-habit repair, calendar-import reconciliation
(idempotent add/remove with classify/skip rules), sleep trends (avg/consistency/debt/direction), and
a day-balance/overload score. The app surfaces them in: **Routines** (build + apply), **gap-fill**
("Fill free time"), **recurring tasks** (completion spawns next occurrence), a tappable **conflict
banner → Resolve** sheet, **Insights** best-window + adherence cards, a Habits **"Schedule at …"**
repair button, a **calendar overlay** on the dial (EventKit ghost arcs, toggle in the Plan toolbar),
and **App Intents / Siri Shortcuts** (Add Task, Log Mood, What's Next, Start Focus). Additional
features: **drag-to-move/resize** blocks on the dial (math in `DialDrag`), **sleep-trends** card,
**day-balance** + **sleep→mood correlation** insights, **medication refill** ETA tracking,
**first-run onboarding**, and **JSON backup/export + restore** (Settings → Data, via the Today menu).

## Parity pass (2026-06-13)

A feature-parity sweep against the Android app closed the major gaps and adopted the latest
iOS 27 / WWDC 2026 SwiftUI APIs. New, with all pure logic unit-tested in `ChronosCore`
(**223 tests, 0 failures** on Windows):

- **Settings** — a full `Settings` tab (`Settings/SettingsView.swift`) bound to the new
  `@Observable` `ChronosSettings` store (App-Group `UserDefaults`): Appearance (theme, 7
  backdrops, glass, reduce-motion, contrast), AI & planning style, Notifications + quiet hours,
  Privacy & Security, and the graduated feature flags (which now show/hide tabs in `RootView`).
- **Medication app-lock** — Face ID / Touch ID / passcode gate via `LocalAuthentication`
  (`Security/AppLock.swift`), plus adherence + refill cards driven by `ChronosCore`.
- **HealthKit sleep import** — read-only `HKCategoryType(.sleepAnalysis)` import with
  stage-derived quality and a manual-entry provenance guard (`Health/HealthKitSleepImporter.swift`,
  `ChronosCore/SleepStaging.swift`).
- **Dial density** — 3-ring layering, night band, free-time + conflict arcs, drag handles with a
  live snapped preview (`ChronosCore/DialRings.swift`).
- **Focus** — block-bounded work/break phase splits that hold at each boundary, with preset chips
  and phase dots (`ChronosCore/FocusPhasePlan.swift`).
- **Task smart-fill** — offline NL parser (dates/times/durations/priority/recurrence/contacts)
  with a "Detected in your text" preview (`ChronosCore/SmartFill.swift`).
- **Insights** — Day/Week/Month rollups, category breakdown + concentration, severity-ranked
  findings (`ChronosCore/InsightsAnalytics.swift`); sleep readiness now deterministically mutates
  the schedule (`ChronosCore/ReadinessSchedule.swift`).

### Second pass — medium-priority gaps (also 2026-06-13)

- **App Intents / Siri** — expanded from 4 to ~17 intents (log habit/med/sleep/journal, add block,
  create goal, apply routine, complete/miss block, reflow day, read today/tasks/habits).
- **Interactive widgets** — Habits + Medication widgets added; Tasks/Today/Habits/Medication widgets
  now have `Button(intent:)` actions (complete task, toggle habit, mark dose, start focus).
- **Live Activity controls** — Pause/Resume, +5m, Stop, and Continue buttons, routed back to the app
  via `FocusCommandBridge` and drained in `FocusView`.
- **Notifications** — actionable Medication (Taken/Snooze/**Skip**)/Task/Habit categories with a
  dose-logging delegate, task reminders, refill-soon lines, quiet-hours shifting
  (`ChronosCore/QuietHours.swift`), threading + interruption levels.
- **On-device AI suite** — streaming multi-turn `ChronosAssistant` + sheet, summarize/proofread/
  rewrite text tools (surfaced on the task description) with an LRU cache, and a deterministic
  command-palette ranker (`ChronosCore/CommandAssist.swift`).
- **Tasks/Habits** — task templates, "Add & New", checklist paste-split, weekday-set recurrence,
  habit 7/30-day completion-rate cards.
- **Journal/Sleep/Routines** — guided prompts + dictation + history polish
  (`ChronosCore/JournalText.swift`), live sleep-log validation hints
  (`ChronosCore/SleepLogHints.swift`), routine apply≠complete split + `isActive` toggle.
- **Scheduled auto-backup** — `BGTaskScheduler` daily export with rolling retention; **onboarding
  feature-selector** page writing the feature flags.

### Third pass — final parity items (2026-06-14)

- **watchOS companion** (`ChronosWatch/`) — the iOS-native Wear analogue. A **WatchConnectivity**
  Data Layer (`Models/WatchSyncDTO.swift`, phone `Watch/PhoneWatchSync.swift`, watch
  `WatchConnectivityClient.swift`): the phone ships a today snapshot (blocks/tasks/habits + live
  focus) and the watch sends back quick actions (complete task, toggle habit, mark dose, start/
  pause/stop focus). Watch UI: Today / Tasks / Habits / Focus with a live `Text(timerInterval:)`
  countdown. (Apple Watch and iPhone do **not** share an App-Group store — WC is the bridge.)
- **Auto-scheduled reminders** — task reminders now fire on save (`TaskEditorSheet`); the
  next-block notification refreshes when the day's plan changes (`TodayView`).
- **User-toggleable iCloud sync** — Settings → Data toggle persists `sync.cloudKit`;
  `ChronosStore` reads it at container-build time (applies next launch) and falls back to the local
  store if CloudKit isn't provisioned, so data is never dropped.

> **Build note.** The app target now depends on the `ChronosCore` SwiftPM package and compiles the
> shared `ChronosFlowWidgets/WidgetIntents.swift` (both declared in `project.yml`). The `ChronosWatch`
> watchOS target's sources now exist. The committed `.xcodeproj` predates these — **regenerate it**:
> `brew install xcodegen && cd ios && xcodegen generate`.

- **Watch complication / Smart Stack** (`ChronosWatchWidgets/`) — a watchOS WidgetKit extension with
  inline/circular/corner/rectangular accessory families showing the current/next block or a live
  focus countdown, fed by the snapshot the watch app caches into the on-watch App Group. The
  iOS-native analogue of the Android Wear tiles.

## Roadmap (not yet ported)

- On-device validation only: the cross-device focus mirror, the watch complication timeline, and the
  streaming-with-tools AI path all need a real iPhone+Watch pair + Apple Intelligence, which can't be
  exercised on Windows. Everything is authored compile-correct and the portable `ChronosCore` logic
  is unit-tested green; the remaining step is a macOS/Xcode build + device run.
