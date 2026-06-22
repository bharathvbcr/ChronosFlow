I'll synthesize this into a prioritized parity plan. The reports are comprehensive, so let me produce the plan directly without re-reading the codebase (this is a synthesis task over provided data).

# ChronosFlow iOS-27 Parity Implementation Plan

## 1. Executive Summary

**Overall state:** The iOS port is broadly functional for *core* surfaces (Day Dial, Tasks, Focus, Sleep, Goals basics) but has drifted **6+ days behind** the Android tree. Of 20 audited areas, **15 are `major-drift`, 1 `missing` (Meridian interop), 2 `minor-drift`, and the rest mixed**. The shared `ChronosCore` (Swift, Windows-testable, ~223 tests) is the right vehicle for deterministic logic, but it lags Android's recent declutter/recommendations/command-history work.

**Biggest gaps (in rough impact order):**
1. **Wired but dead control paths** — `FocusCommandBridge.drain()` is never called by the app, so **every Live Activity / focus widget button is a silent no-op** (CRITICAL). This is the single highest-leverage bug.
2. **Entire feature subsystems missing on iOS:** Meridian/DevTime interop (consent gate + sync worker), Goals detail/linked-work + full form, Habits repair panel + lifecycle (pause/skip/defer) + analytics, Journal multi-entry/calendar/HealthKit-import, calendar **export** + background sync + provenance guards.
3. **AppIntents have no feature-flag gating** — a user who disabled Habits can still add habits by voice (parity + correctness).
4. **Routines day-rollover bug** — modulo wrapping places midnight-crossing steps on the wrong day (latent correctness bug, test passes for the wrong reason).
5. **Undo/redo command history** absent from Day Dial; **smart duplicate placement** absent.
6. **Insights/Review architecture mismatch** — iOS filters data-domains; Android filters page-sections; no AI recommendations card.
7. **Onboarding feature-graduation markers** missing — legacy `false` flags never promote to on-by-default.

**Total scope:** ~95 discrete work items. Estimate **~12 L, ~30 M, ~50 S**. The bulk of L items are net-new subsystems (interop, habits repair, journal overhaul, calendar export). A large fraction of value comes from **porting deterministic helpers into ChronosCore once** (used by app + widgets + watch + intents) and from **wiring already-built but disconnected paths** (focus bridge, watch redaction, intent gating).

---

## 2. Prioritized Work Items

Effort: **S** ≤ ~½ day, **M** ~1-2 days, **L** ~3+ days / new subsystem. `CC` = ChronosCore shared logic.

| ID | Area | Title | Sev | Eff | Target files | iOS-27 approach |
|----|------|-------|-----|-----|--------------|-----------------|
| W01 | Widgets/Focus | Wire `FocusCommandBridge.drain()` on launch + Darwin observer | **crit** | M | `ChronosFlowApp.swift`, `RootView`, `WidgetIntents.swift`, `FocusTimerModel.swift` | `.onAppear`/scenePhase drain + `CFNotificationCenter` observer → `FocusTimerModel` mutations |
| I01 | Interop | Interop consent gate (`interop.consent.granted`) | **crit** | S | `ChronosSettings.swift`, `SettingsView.swift` (CC: `InteropContract`) | `@Observable`+App-Group `UserDefaults`, SwiftUI `Toggle` |
| I02 | Interop | Background DevTime sync (BGTaskScheduler, 6h) + import pipeline | **crit** | L | new `Interop/InteropSync.swift`, `ChronosFlowApp.swift` (CC: dedup, cap, reminder-lead) | `BGTaskScheduler` (mirror `ChronosAutoBackup`), App-Group file/`UserDefaults` handoff, `UNUserNotificationCenter` |
| G01 | Goals | GoalDetailView + linked-work visualization | **crit** | M | new `GoalDetailView.swift`, `GoalsView.swift` (CC: `goalDueLabel`, `cadenceLabel`, `goalLinkedWorkLabel`) | SwiftUI sheet/`NavigationLink`, `@Query` filtered on goalID |
| O01 | Onboarding | Feature-graduation marker system | **crit** | M | `ChronosSettings.swift` (CC: `FeatureGraduation`) | Two promotion-marker keys + `graduationForFlag` before applying stored bool; port Android tests |
| R01 | Routines | Day-rollover in `instantiateRoutine` (floorDiv/floorMod) | high | M | CC `Routines.swift`, `RoutinesView.swift`, `ChronosAppIntents.swift` | Return `(block, daysOffset)`; `Calendar` date arithmetic; CC midnight-rollover test |
| R02 | Routines | `ApplyRoutineIntent` must not set `lastCompletedDate` | high | S | `ChronosAppIntents.swift` | Remove side-effect; optional separate `MarkRoutineCompleteIntent` |
| AI01 | AppIntents | Feature-flag gating on all gated intents | high | M | `ChronosAppIntents.swift` | `guard ChronosSettings.shared.XEnabled else { .result(dialog:) }` |
| AI02 | AppIntents | `StartFocusSessionIntent` create session in background | high | M | `ChronosAppIntents.swift`, `FocusSession` model | Insert `FocusSession`, ActivityKit live activity, `openAppWhenRun=false` |
| AI03 | AppIntents | `SkipHabitIntent` + `SkipMedicationDoseIntent` + `ListMedicationsIntent` | high | M | `ChronosAppIntents.swift`, `Habit`/`MedicationPlan` models | New AppIntents; add skip-date arrays (depends H07/M02) |
| AI04 | AppIntents | `logMoodEnergyCheckIn` add stress+focus params | high | S | `ChronosAppIntents.swift` | Two `@Parameter` Int, clamp 1-5 |
| DD01 | DayDial | Undo/redo command history | high | L | CC new `PlannerCommand.swift`+`PlannerCommandHistory.swift`, `DayDialScreen.swift` | Sendable value-typed command protocol; `@State canUndo/canRedo`; toolbar (`visibilityPriority(.high)`) |
| DD02 | DayDial | Smart duplicate placement (free-gap heuristics) | high | M | CC `Planner.swift` (`nextDuplicatePlacement`), `DayDialScreen.swift` | Reuse `FreeTimeCalculator`; `.swipeActions`/`.contextMenu` |
| H01 | Habits | Missed-habit AI repair panel | high | L | `HabitsView.swift` (CC: `HabitRepairAssistPlanner` local fallback) | `@State` suggestions, FoundationModels w/ local fallback, `GenAiAssistBanner` |
| H02 | Habits | Metric tiles (Active/Best streak/Done today) | high | S | `HabitsView.swift` | Derived from `@Query`, HStack of tiles |
| H03 | Habits | Consistency 14-day chart + streak top-5 chart | high | M | `HabitsView.swift` (CC: `habitConsistencyHeadline`) | Swift `Charts` |
| H04 | Habits | Status pills (due/paused/skipped/streak-milestone/adherence) | high | M | `HabitsView.swift` (CC: `deriveHabitAnalytics`, `habitStreakMilestoneLabel`) | FlowRow of `Capsule` pills |
| H05 | Habits | Pause/resume/skip/defer UI + context sheet | high | M | `HabitsView.swift`, new context sheet | `.sheet`/`.contextMenu`; depends H07 |
| H06 | Habits | `Habit.isActive` filter on list query | med | S | `HabitsView.swift` | `@Query(filter: #Predicate { $0.isActive })` |
| H07 | Habits | Habit schedule fields + `HabitEvent` model | med | M | `Wellbeing.swift`/`Habit.swift` | `pausedUntil/skipDate/deferUntilMinuteOfDay`; `@Model HabitEvent`; SwiftData migration |
| J01 | Journal | Multi-entry/day + sub-notes body parse | **crit** | L | `JournalView.swift` (CC: `journalParseBody/Serialize`) | Inline point adder; serialize `• ` bullets |
| J02 | Journal | Calendar overview (mood emoji + workout badges) | high | L | `JournalView.swift` (CC: `journalMoodByDate`) | Custom 7-col grid layout |
| J03 | Journal | HealthKit workout import as journal points | high | M | `JournalView.swift` | `HKWorkoutQuery`, `isWorkoutEntry` entries |
| J04 | Journal | AI insights card + offline fallback | high | M | `JournalView.swift` (CC: `buildJournalInsightPrompt`) | FoundationModels + heuristic fallback |
| J05 | Journal | Optional time-of-day per entry | high | M | `JournalView.swift`, model (CC: `parseFlexibleMinute`) | Time picker, `entryMinuteOfDay` |
| C01 | Calendar | Calendar export to device (EventKit save/update/delete) | high | L | CC `CalendarExport.swift`, `TimeBlockEditorSheet.swift`, `DayDialViewModel` | EventKit `save(event:)`, `requestFullAccessToEvents` |
| C02 | Calendar | Provenance guards on edit/delete of imported blocks | high | M | `TimeBlockEditorSheet.swift` (CC: `isImportedCalendarBlock`) | `block.provenance == .calendar` guard at UI |
| C03 | Calendar | Background calendar sync (BGTaskScheduler 7-day) | high | M | new `CalendarBackgroundSync.swift` (CC: `syncWindow`) | `BGTaskScheduler` + `EKEventStore.predicateForEvents` |
| C04 | Calendar | Instance-unique IDs + recurrence expansion + midnight split | high | M | CC `CalendarImport.swift` | `EKRecurrenceRule` manual enumeration; `calendar-import-<id>-<date>-<min>` |
| F01 | Focus | Break injection chips (5/10/15) | high | S | `FocusView.swift` | `ForEach` chips, guard on `.work && !paused` |
| F02 | Focus | Segmented progress bar in Live Activity | high | M | `FocusLiveActivityView.swift`, `FocusActivityAttributes.swift` (CC: segment struct) | `Canvas`/stacked capsules; extend `ContentState` |
| F03 | Focus | Persist split session on app kill | high | M | new `FocusSessionSnapshot` `@Model`, `FocusTimerModel.swift` | SwiftData snapshot save/load |
| N01 | AI | Cloud fallback PrivacyMode structure (on-device/cloud/disabled) | high | M | `Enums.swift`, `ChronosSettings.swift`, planners | Enum + gate; cloud path = TODO (FoundationModels provider layer future) |
| N02 | AI | Text tools: Emojify + multi-bullet summarize | high | M | `ChronosTextTools.swift` | Per-op instruction strings |
| N03 | AI | Port LocalPlanningHeuristics + DayPlanResponseParser + PromptBuilder | high | L | CC new files | Foundation-only, tested |
| WT01 | Watch | Medication page + per-dose controls | high | M | `WatchSyncDTO.swift`, `WatchRootView.swift`, `PhoneWatchSync.swift` | `WatchMed` DTO, conditional TabView page |
| WT02 | Watch | Day-dial ring on watch today page | high | M | `WatchRootView.swift` | `Canvas` arc rendering (port `DayDialRing`) |
| WT03 | Watch | Habits + Meds complications | high | M | new watch widget files | `StaticConfiguration`+`TimelineProvider`, gauge views |
| WT04 | Watch | Wire `sensitiveTitlesRedacted` into watch sync | med | S | `PhoneWatchSync.swift` | Conditional title redaction in `buildSnapshot` |
| WG01 | Widgets | Agenda/Schedule widget | high | M | new `AgendaWidget.swift` | `TimelineProvider`, responsive families |
| WG02 | Widgets | Home-screen Focus widget (active+idle) | high | M | new `FocusWidget.swift` | `StaticConfiguration`, intents via bridge (dep W01) |
| T01 | Tasks | Bulk import review sheet (.txt/.ics/SEND_MULTIPLE) | high | L | `ShareViewController.swift`, new bulk sheet (CC: `splitSharedTaskLines`+ICS) | Share extension attachments + SwiftUI checklist modal |
| S01 | Sleep | In-sheet one-time HealthKit sync button | high | S | `SleepLogSheet.swift` | One-shot `importRecent()`, no toggle flip |
| PS01 | Settings | Meridian companion status card + medication-sharing toggle | high | M | `SettingsView.swift`, `ChronosSettings.swift` (CC: `InteropClient`) | `DisclosureGroup`, App-Group peer detection |
| PS02 | Settings | Privacy & Sync collapsible sections | high | M | `SettingsView.swift`, `ChronosSettings.swift` | `DisclosureGroup` w/ persisted expand state |
| BK01 | Backup | Schema-aware import + format alignment (`formatKind`/`schema`) | high | L | CC import validator, `DataExport.swift` | Column-intersection matching |
| BK02 | Backup | Device-to-device transfer snapshot (App-Group) | high | M | new `ChronosDeviceTransfer.swift` | App-Group file, restore if DB empty |
| BK03 | Backup | Task attachments in backup | high | M | `TaskItem.swift`, `DataExport.swift` (CC: `AttachmentDTO`) | Codable attachment array |
| BK04 | Backup | Non-destructive restore mode (`mergeIfEmpty`) | med | S | `DataExport.swift` | `enum RestoreMode`, per-entity empty guard |
| IN01 | Insights | InsightsSection enum realign (EXECUTION/CATEGORIES/INSIGHTS/SCREEN_TIME/TRENDS) | high | M | CC `InsightsAnalytics.swift`, `Enums.swift`, `InsightsView.swift` | Move enum to CC, `insightsSectionVisible` |
| IN02 | Insights | AI recommendations card (`InsightsRecommendationsPlanner`) | high | L | CC new planner, `InsightsView.swift` | Foundation-only local heuristics + GenAI |
| IN03 | Insights | TRENDS section grouping + `CompanionTrendSections` | med | M | CC, new `InsightsTrendSections.swift` | Aggregation struct + range picker |
| M01 | Medication | Dose event history (TAKEN/MISSED/SKIPPED/...) | high | M | new `DoseEvent` `@Model`, `MedicationPlan` | `@Relationship(.cascade)`, migrate `takenAt` |
| M02 | Medication | Pause/resume + safety profile + skip/snooze actions | high | L | `MedicationPlan`, editor, notif delegate | Multiple model fields + `UNUserNotificationCenterDelegate` |
| DD03 | DayDial | Block-edit latency: drag-session cache + debounce (benchmark first) | high | M | `DayDialScreen.swift` | Benchmark; `@State` cache; `Task.sleep` debounce |
| ... | (lower-sev polish) | conflict arcs, preview-validation, severity levels, recently-used titles, duplicate-warning, prompt-shuffle determinism, mood labels, sync-status labels, refill warnings, notification deep-links | low | S each | per-area | per-report |

*(Remaining ~30 low-severity polish items from the reports — conflict arc rendering, live drag validation, conflict severity, recent-title chips, duplicate warnings, redaction-text wording, watch stale-sync warning, sync-status labels, iCloud sync card, app-permissions breakdown, notification deep-links, refill-soon warning, etc. — are deferred to a final Polish wave; each is S.)*

---

## 3. Implementation Waves (parallelizable)

Items in the same wave touch **disjoint files** and can be done concurrently. **ChronosCore ports land first within each wave** so app/widget/watch/intent consumers build on tested helpers.

### Wave 0 — ChronosCore deterministic foundations (port once, test on Windows)
*No app target needed; unblocks everything; all `Sendable`/Foundation-only with unit tests.*
- **CC-A** `FeatureGraduation` (O01) + tests
- **CC-B** `PlannerCommand` + `PlannerCommandHistory` (DD01) + `nextDuplicatePlacement` (DD02) + tests
- **CC-C** Routines `instantiateRoutine` rollover (R01) + tests (fix the latent wrong-reason test)
- **CC-D** Journal `journalParseBody/Serialize`, `journalMoodByDate`, `journalPromptOfTheDay`, `parseFlexibleMinute`, `buildJournalInsightPrompt` (J01/J02/J04/J05) + tests
- **CC-E** Habits `deriveHabitAnalytics`, `habitConsistencyHeadline`, `habitStreakMilestoneLabel`, `missedForRepair` (H01/H03/H04) + tests
- **CC-F** Goals `goalDueLabel`, `cadenceLabel`, `goalLinkedWorkLabel`, `suggestGoalCategory`, `goalTargetFromTitle`, `goalDeadlinePresets`, warnings (G01/G02) + tests
- **CC-G** Calendar `instanceUniqueId`, midnight-split, recurrence expansion, `syncWindow`, `formatLastSyncedLabel`, all-day context marker, `CalendarExport` contract (C01/C03/C04) + tests
- **CC-H** Insights `InsightsSection`+`insightsSectionVisible`, `CompanionTrendSections`, `InsightsRecommendationsPlanner` local heuristics (IN01/IN02/IN03) + tests
- **CC-I** AI `LocalPlanningHeuristics`, `DayPlanResponseParser`, `PlanningPromptBuilder` (N03) + tests
- **CC-J** Backup schema-aware import validator + `AttachmentDTO` + `instanceUniqueId` reuse (BK01/BK03) + tests
- **CC-K** Sleep `deriveSleepReadiness` (move from app), `excludeSleepMinutesFromWindows`, `calculateSleepTrends`, `applyReadinessFloor` + tests
- **CC-L** Interop `InteropContract`, dedup-collapse, `MAX_INTEROP_TASKS` cap, reminder-lead (I02) + tests
- **CC-M** Watch `WearLinkStatus`/provider, `syncAgeLabel`, `WearFormat` helpers (WT04/PS-watch) + tests
- **CC-N** Focus `FocusPhaseSegment` render struct (F02) + tests; `FocusCommandBridge.Command` already Codable (consider moving to CC)

### Wave 1 — Critical wiring & high-leverage low-risk fixes (parallel, disjoint files)
- **W01** Focus command bridge wiring (app entry + FocusTimerModel)
- **I01** Interop consent gate (settings)
- **R02** ApplyRoutineIntent side-effect removal
- **AI01/AI04** Intent gating + mood params
- **H06** Habit isActive filter
- **S01** In-sheet sleep sync button
- **BK04** Non-destructive restore mode
- **F01** Break injection chips

### Wave 2 — Model & schema changes (must precede UI that reads them)
*These add SwiftData fields/models; do before dependent UI. Each model file is disjoint.*
- **H07** Habit schedule fields + `HabitEvent` (→ H01/H04/H05/AI03)
- **M01** `DoseEvent` model (→ M02/AI03)
- **F03** `FocusSessionSnapshot` model
- **R01** apply rollover in `RoutinesView`/intent (consumes CC-C)
- **BK03** TaskItem attachments field (consumes CC-J)

### Wave 3 — Feature UI build-out (large, mostly independent feature folders)
- **DD01/DD02/DD03** Day Dial (undo, duplicate, latency) — `DayDial/`
- **H01–H05** Habits panel/charts/pills/lifecycle — `Habits/`
- **J01–J05** Journal overhaul — `Journal/`
- **G01** Goals detail + form — `Goals/`
- **C01–C04** Calendar export/sync/guards — `Calendar/`+`DayDial/`
- **F02** Focus Live Activity segmented bar — `LiveActivity/`
- **AI02/AI03** Focus-start + skip intents — `AppIntents/`
- **T01** Tasks bulk import — `Tasks/`+share ext
- **M02** Medication pause/safety/actions — `Medication/`
- **IN01–IN03** Insights realign + recommendations — `Insights/`
- **I02** Interop sync worker — `Interop/`

### Wave 4 — Widgets, Watch, Settings (consume Waves 1-3 outputs)
- **WG01** Agenda widget; **WG02** Focus home widget (dep W01)
- **WT01–WT04** Watch med page, ring, complications, redaction
- **PS01/PS02** Settings interop card + collapsible sections
- **BK01/BK02** Backup schema alignment + device transfer
- **N01/N02** AI privacy mode + text tools

### Wave 5 — Polish (all S, disjoint)
All low-severity items: conflict arcs, drag preview validation, conflict severity, recent-title chips, duplicate warnings, redaction wording, watch stale-sync/sync-in-flight, sync-status/iCloud labels, app-permissions breakdown, notification deep-links (focus/sleep), refill-soon warning, prompt-shuffle determinism, mood-label alignment, GenerationProfile temp alignment + FACTUAL variant, response-cache TTL.

---

## 4. Sequencing, Dependencies & Risks

**Hard dependencies:**
- Wave 0 (CC) → all consuming UI. Do not start a feature's UI before its CC helper + tests are green.
- **H07 → H01/H04/H05/AI03** (repair/pills/lifecycle/skip-intent all read schedule fields + HabitEvent).
- **M01 → M02/AI03** (dose events back pause/skip/snooze).
- **W01 → WG02** (home Focus widget buttons are dead without the bridge).
- **C04 (instance IDs) → C01 (export)** — export needs collision-free IDs.
- **IN01 (enum realign) → IN02/IN03** (recommendation/trends cards live inside the new sections + their collapse keys).
- **O01 (graduation) → onboarding feature rows** (AI Advisor row, Insights↔Review terminology).

**Risks — only verifiable on macOS/device (cannot confirm on Windows):**
- **FoundationModels streaming** (`snapshot.content` shape) flagged unverified in `ChronosAssistant` — confirm against Xcode 27 SDK; non-streaming fallback exists. Affects J04/N01/IN02.
- **`@State` macro source break** (iOS 27): inline-default + `init`-assignment pattern — recompile-test every view touched; fix by dropping inline defaults.
- **Live Activity interactivity** (`LiveActivityIntent`, landscape `isDynamicIslandLimitedInWidth`, `activityBackgroundTint`) — F02/WG02 need device verification on AOD/StandBy.
- **BGTaskScheduler** scheduling (I02/C03) is opportunistic; can only be exercised on device. Gate all sync on permission and **no-op silently** when ungranted (mirror Android).
- **App-Group / EventKit / HealthKit entitlements** must be configured in Xcode (signing) — interop peer detection, calendar export, sleep import all fail silently without them.
- **Block-edit latency (DD03):** *benchmark before optimizing.* iOS is single-process SwiftData (no SQLCipher WAL concern); the Android fix was driven by Room+SQLCipher. Don't port WAL blindly.
- **SwiftData migrations** (H07/M01/F03/BK): each new field/model needs a `VersionedSchema` stage; CloudKit (if enabled) forbids `.unique` + requires optional/defaulted props — keep new fields optional.
- **Dedup determinism** (I02/BK01): deterministic IDs (`interop:peer:externalId`) + collapse-by-(title,due) must match Android exactly or imports multiply.

**Workflow guardrails (per CLAUDE.md):** run `gitnexus_impact` before editing any symbol, `gitnexus_detect_changes` before commit. Note: GitNexus indexes the **Android** tree; iOS Swift edits won't be in its graph — impact analysis applies when touching shared concepts, but Swift files are new/iOS-only.

---

## 5. Android-Only Features — Explicitly Out of Scope

| Feature | Why no iOS analog |
|---------|-------------------|
| **ForegroundService lifecycle / `onTaskRemoved`** | iOS has no background-service model; app backgrounding + ActivityKit Live Activity is the replacement (already in use). |
| **ML Kit Summarizer/Rewriter/Proofreader structured APIs** | iOS uses FoundationModels general LLM with per-op instruction prompts; semantically equivalent, different API shape. |
| **ML Kit prefix caching** (AICore) | FoundationModels exposes no prefix-cache API in iOS 27 beta. Performance gap only, not correctness. Mark future. |
| **Cloud Gemini via Firebase AI Logic** | FoundationModels is on-device; cloud fallback needs a separate app-server. Implement PrivacyMode *structure* only (N01); cloud path = TODO until provider layer/Firebase Apple SDK lands. |
| **MetricStyle big-countdown (API 37)** | ActivityKit `timerInterval` already gives system-driven countdown — native equivalent, no work. |
| **`setRequestPromotedOngoing` / `canPostPromotedNotifications`** | ActivityKit auto-surfaces on lock screen; no promotion request. Add only a user nudge if `areActivitiesEnabled == false`. |
| **WorkManager periodic + battery constraints** | Platform API difference → `BGTaskScheduler` (not a feature gap). |
| **CalendarContract / ContentResolver, ContentProvider interop** | iOS uses EventKit + App Groups + URL schemes; ContentProvider has no 1:1 mapping. Interop is **import-only (read from DevTime)** for MVP. |
| **PeerVerifier SHA-256 cert pinning** | iOS trust boundary = code-signing + App Group / Team-ID entitlements; no public cert-pinning API. Document trust model instead. |
| **`SCHEDULE_EXACT_ALARM` / exact-alarm permission** | `UNNotificationRequest` + `.timeSensitive` handles this transparently. |
| **UsageStatsManager screen-time** | No equivalent public iOS API (ScreenTime entitlement is restricted). iOS Screen Time insights card deferred; could proxy with internal focus-session metrics later. |
| **Glance composition / GlanceTheme Material You / PendingIntent.filterEquals caching** | WidgetKit/SwiftUI is a different abstraction; URL-based routing avoids the `filterEquals` collision natively — no work needed. |
| **Health Connect Changes-API incremental sync + 6h periodic + deletion sweep** | HealthKit has no Changes-API for sleep parity that maps to HC; iOS-native approach is on-demand 14-day re-query (acceptable). (Note: `HKAnchoredObjectQueryDescriptor` *is* the analog if incremental sync is later desired.) |
| **Plaintext→SQLCipher migrator** | SwiftData handles at-rest via Data Protection file-protection class; no SQLCipher migration. |
| **SAF tree-URI folder picker for backups** | iOS uses App-Group container + iCloud; no user folder tree. |
| **Android Backup Service (BackupAgentHelper)** | iCloud/CloudKit sync is the analog (already configurable via `ModelConfiguration.cloudKitDatabase`). |
| **Wear OngoingActivity per-phase mirroring / ambient burn-in mode** | watchOS uses ActivityKit + AOD; conceptual equivalent, separate API. |

**Bidirectional interop (export TO DevTime)** is also out of MVP scope — iOS has no ContentProvider; ship import-only first, add URL-scheme/App-Group export later.

---

**Recommended starting point for the next workflow:** execute **Wave 0 (ChronosCore ports + tests)** in full first — it is Windows-buildable/testable, has no device dependency, and is the prerequisite for nearly every feature wave. Then **Wave 1** (critical wiring, especially W01 focus bridge and I01/AI01 gating) for immediate user-facing correctness wins, then Waves 2→4 by feature folder in parallel.
