---
name: gitnexus-exploring
description: "Use when the user asks how code works, wants to understand architecture, trace execution flows, or explore unfamiliar parts of the codebase. Examples: \"How does X work?\", \"What calls this function?\", \"Show me the auth flow\""
---

# Exploring Codebases with GitNexus — ChronosFlow

## ChronosFlow Quick Reference

**Index:** 21,610 symbols · 73,807 edges · 911 clusters · 300 flows · repo name = `ChronosFlow`

> **MCP server note:** `gitnexus_*` MCP tools require a Claude Code restart after `npx gitnexus analyze`. Until restarted, use the CLI fallbacks shown in each step below — they hit the same index.

### Module Map
```
:app              MainActivity, ChronosApplication, navigation graph, interop
:core:ai          Gemini cloud (Firebase AI) + ML Kit Nano (GenerationProfile)
:core:data        ChronosDatabase (Room v25 + SQLCipher), DAOs, sync workers
:core:domain      PlannerService, UseCases (Create/Move/Resize/CompleteBlock, ScheduleTaskIntoDay)
:core:notifications  Live notification unification, alarm management
:core:ui          Design system: Material You + glass, ChronosMotionDefaults
:feature:daydial  Day view + dial (DayDialViewModel 1,372 lines, 100 files — largest feature)
:feature:focus    FocusService (Pomodoro, 589 lines; comm_273 Focus, cohesion 0.97)
:feature:habits   Habit tracking + streaks (comm_293 Habits, 76 symbols, cohesion 0.89)
:feature:goals    Goals lifecycle + analytics
:feature:tasks    TaskFormSheet, smart-fill, bulk import
:feature:medication  Auth-gated reminders (SensitiveRouteGate)
:wear             Wear OS 4+ companion (shared packageId + signing with :app)
```

### Key Symbols by Area
| Area | Go-to symbol | File |
|------|-------------|------|
| Navigation | `ChronosNavigationShell` | `app/navigation/ChronosNavigationShell.kt` |
| Day view | `DayDialViewModel` | `feature/daydial/.../DayDialViewModel.kt:148` |
| Scheduling | `PlannerService` | `core/domain/.../planner/PlannerService.kt:14` |
| Focus | `FocusService` | `feature/focus/.../FocusService.kt:51` |
| Database | `ChronosDatabase` | `core/data/.../ChronosDatabase.kt:66` |
| AI | `GenAiGateway` | `core/ai/...` |
| Interop | `InteropProvider` | `app/interop/InteropProvider.kt:25` |
| Wear sync | `OnDataChanged` | `wear/...` |

### Top Clusters (by size)
`Habits` 76 · `Ai` 75 · `Usecase` 66 · `Tasks` 66 · `Delegate` 59/52/51/50/48 · `Health` 48 · `Focus` 36 (cohesion 0.97)

---

## When to Use

- "How does authentication work?"
- "What's the project structure?"
- "Show me the main components"
- "Where is the database logic?"
- Understanding code you haven't seen before

## Workflow

```
1. READ gitnexus://repo/ChronosFlow/context             → Check freshness
   CLI fallback: npx gitnexus status

2. gitnexus_query({query: "<concept>", repo: "ChronosFlow"})
   CLI fallback: npx gitnexus query --repo ChronosFlow "<concept>"

3. gitnexus_context({name: "<symbol>", repo: "ChronosFlow"})
   CLI fallback: npx gitnexus context --repo ChronosFlow <symbol>

4. READ gitnexus://repo/ChronosFlow/process/{name}
   (no CLI fallback — read source files directly instead)

5. Read source files for implementation details
```

> If index is stale → run `npx gitnexus analyze` in terminal, then restart Claude Code for MCP tools.

## Checklist

```
- [ ] Check index freshness (status or context resource)
- [ ] gitnexus_query / CLI query for the concept
- [ ] Review returned processes (execution flows)
- [ ] gitnexus_context / CLI context on key symbols for callers/callees
- [ ] Read source files for implementation details
```

## Resources (ChronosFlow)

| Resource | What you get |
|----------|-------------|
| `gitnexus://repo/ChronosFlow/context` | Stats, staleness warning |
| `gitnexus://repo/ChronosFlow/clusters` | All 911 functional areas |
| `gitnexus://repo/ChronosFlow/cluster/{name}` | Area members with file paths |
| `gitnexus://repo/ChronosFlow/processes` | All 300 execution flows |
| `gitnexus://repo/ChronosFlow/process/{name}` | Step-by-step trace |

## Tools + CLI Fallbacks

**gitnexus_query** — find execution flows:
```
MCP:  gitnexus_query({query: "focus pomodoro", repo: "ChronosFlow"})
CLI:  npx gitnexus query --repo ChronosFlow "focus pomodoro"
→ Processes: OnTileRequest → FocusState, ChronosDial → DurationToSweep, ...
```

**gitnexus_context** — 360-degree view of a symbol:
```
MCP:  gitnexus_context({name: "FocusService", repo: "ChronosFlow"})
CLI:  npx gitnexus context --repo ChronosFlow FocusService
→ Incoming imports: MainActivity, ChronosAppFunctions, DayDialFocusNotificationBridge
→ Methods: onCreate, startNewSession, buildNotification, startTicker, completeAndLogCurrentSession
```

**gitnexus_cypher** — raw graph queries:
```
MCP:  gitnexus_cypher({query: "MATCH ...", repo: "ChronosFlow"})
CLI:  npx gitnexus cypher --repo ChronosFlow "MATCH (p:Process) RETURN p.id, p.label LIMIT 20"
```

## ChronosFlow Example: "How does the day schedule work?"

```
1. npx gitnexus context --repo ChronosFlow DayDialViewModel
   → Imports: MainActivity, TodayTab, PlanTab, FocusTab, SidebarPageContent
   → 1,372-line class: main orchestrator for the day view

2. npx gitnexus context --repo ChronosFlow PlannerService
   → Called by: DayDialViewModel, DayDialBlockDelegate, FocusService, ChronosAppFunctions
   → Owns: conflict detection, block move/resize validation, resolveConflicts (undo-able)

3. npx gitnexus context --repo ChronosFlow ChronosDatabase
   → DAOs: taskDao, timeBlockDao, dayPlanDao, habitDao, habitScheduleDao, medicationDao...
   → Used by: DataModule (DI), InteropProvider, backup repositories

4. Read feature/daydial/.../DayDialViewModel.kt for implementation details
```

## ChronosFlow Execution Flow Groups (300 total — key ones)

| Entry Point | Terminals | Notes |
|-------------|-----------|-------|
| `DayDialDataScreen` | Current, GetString, PutString, LegacyChronosUiPreferences | 6-step flows |
| `OnBlockMoved` | GetTimeBlockById, NormalizeMinute, SaveTimeBlock, SnapToGrid | Block edit path |
| `DuplicateBlock` | FreeTimeSegment, GetSleepSchedule, MinuteSegment | Duplicate logic |
| `DoWork` | ChronosDatabase, Sha256Hex, DeleteEventsBetween, DistractionNudge | Background sync |
| `EndDayReview` | Cancel, GetPersistedIds, ObserveRequestsByType, SaveAlarmRequest | Alarm scheduling |
| `OnDataChanged` | FocusState, PackBlock, PackTask, WearDaySummary | Wear sync |
| `OnTileRequest` | WearBlock, WearDaySummary, Prefs, FocusState | Wear tiles |
| `ChronosNavigationShell` | ChronosUiPreferencesFlow, GetString, LegacyChronosUiPreferences | Nav shell |
| `HabitScreen` | GenAiAssistUiSnapshot, NanoModelStatus, PrivacyMode | Habit + AI |
| `MedicationScreen` | BannerMessage, ToRoutineAssistSource | Medication |
