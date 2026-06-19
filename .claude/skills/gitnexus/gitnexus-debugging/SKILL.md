---
name: gitnexus-debugging
description: "Use when the user is debugging a bug, tracing an error, or asking why something fails. Examples: \"Why is X failing?\", \"Where does this error come from?\", \"Trace this bug\""
---

# Debugging with GitNexus — ChronosFlow

## ChronosFlow Debugging Cheat Sheet

> **MCP server note:** `gitnexus_query` / `gitnexus_context` require a Claude Code restart after `npx gitnexus analyze`. Use CLI fallbacks shown below.

### Where bugs live in ChronosFlow

| Symptom area | Suspect first | How to trace |
|-------------|--------------|-------------|
| Block edit slow / not showing | `DayDialBlockDelegate`, `PlannerService`, `SwipeToDismissBox` | WAL mode? optimistic dragPreview hiding? stale dismiss state? |
| Focus timer broken | `FocusService.startNewSession` / `handleStartCommand` | Phase split wiring? `DayDialFocusNotificationBridge` bridge? |
| Notification wrong / multiple | `FocusNotificationManager`, live notification (id 4201) | Focus↔block handoff; `chronos_now_live` channel |
| Schedule conflict repair broken | `PlannerService.resolveConflicts`, `ResolveConflictsCommand` | immovable filter (locked||protected||FIXED||underway)? |
| Wear "Not synced yet" | `OnDataChanged` Wear listener, `WearDataPublisher` | ON_START publish? pull-on-open TYPE_SYNC msg? |
| Nav sheet not opening | `sectionTargetGeneration` counter, `isOneShotSheetRoute` | generation counter re-fires needed for repeats |
| Medication inaccessible | `SensitiveRouteGate` | `canAuthenticate==false` → should fail OPEN |
| AI not responding | `GenAiGateway`, `ProactiveAssistCache`, `GenerationProfile` | download failure cooldown? single-flight coalescing? |
| Calendar sync not importing | `CalendarSyncWorker.doWork`, `Sha256Hex` dedup | hash collision? provenance guard? |
| DataStore value stale | `ChronosPreferencesDataSource.getString/putString` | 150+ flows end here — read the right key |
| Widget tap dead | `AgendaGlanceWidget`, `openSectionAction` | filterEquals collision? per-section data URI? |

### Key delegate architecture (DayDial)
`DayDialViewModel` splits work across delegates injected at construction:
- `DayDialBlockDelegate` — block CRUD, drag, conflict repair
- `DayDialFocusDelegate` — focus session lifecycle, phase splits
- `DayDialReviewDelegate` — insights, missed-block tracking
- All delegates call `PlannerService` for scheduling decisions

---

## When to Use

- "Why is this function failing?"
- "Trace where this error comes from"
- "Who calls this method?"
- Investigating bugs, errors, or unexpected behavior

## Workflow

```
1. gitnexus_query({query: "<error or symptom>", repo: "ChronosFlow"})
   CLI fallback: npx gitnexus query --repo ChronosFlow "<symptom>"

2. gitnexus_context({name: "<suspect>", repo: "ChronosFlow"})
   CLI fallback: npx gitnexus context --repo ChronosFlow <suspect>

3. npx gitnexus cypher --repo ChronosFlow \
     "MATCH path=(a)-[:CodeRelation {type:'CALLS'}*1..3]->(b {name:'<suspect>'}) \
      RETURN [n IN nodes(path) | n.name] AS chain LIMIT 20"

4. Read source files to confirm root cause
```

> If index is stale → `npx gitnexus analyze`, restart Claude Code for MCP tools.

## Checklist

```
- [ ] Match symptom to ChronosFlow cheat sheet above
- [ ] gitnexus_context / CLI context on the primary suspect
- [ ] Check incoming calls at d=1 (direct callers are the blast zone)
- [ ] Verify architecture pattern (delegate? use case? worker?)
- [ ] Read source file for the suspect method
- [ ] Check relevant test file for expected behavior
```

## Debugging Patterns in ChronosFlow

| Pattern | What to check |
|---------|--------------|
| Delegate not wired | Check DayDialViewModel constructor — delegate might not have `onInjectBreak` or similar called at the right site |
| DataStore key mismatch | `ChronosPreferencesDataSource` — confirm key string matches between read and write sites |
| Room WAL not enabled | SQLCipher requires explicit `setJournalMode(WAL)` — without it, writes block reads |
| Nav sheet repeats silently drop | `sectionTargetGeneration` must increment each time to re-fire a one-shot sheet route |
| Wear tile stale | `requestUpdate()` in `ChronosHabitsTileProvider` only fires on data change; check `OnDataChanged` bridge |
| Optimistic drag hiding edits | `dragPreview` in DayDialBlockDelegate — move/resize operations hide blocks visually until committed |

## Tools + CLI Fallbacks

**gitnexus_context** — see all callers and callees:
```
MCP:  gitnexus_context({name: "resolveConflicts", repo: "ChronosFlow"})
CLI:  npx gitnexus context --repo ChronosFlow resolveConflicts
```

**gitnexus_cypher** — custom call chain trace:
```
CLI:  npx gitnexus cypher --repo ChronosFlow \
        "MATCH (a)-[:CodeRelation {type:'CALLS'}]->(b {name:'startNewSession'}) \
         RETURN a.name, a.filePath"
```

## ChronosFlow Example: "Focus session completes but notification doesn't clear"

```
1. Suspect: FocusService.completeAndLogCurrentSession
   npx gitnexus context --repo ChronosFlow FocusService
   → outgoing methods: completeAndLogCurrentSession, updateForegroundNotification, stopTicker

2. Check the notification flow:
   FocusService → updateForegroundNotification → FocusNotificationManager
   → Live notification id 4201; completion should fold into same id (not post new one)
   → Check: is stopForeground() called? Does chronos_now_live channel cancel?

3. Check if focus↔block handoff is broken:
   proc_29: OnDataChanged → FocusState (Wear sync)
   DayDialFocusNotificationBridge bridges service → ViewModel
   → If bridge isn't called, ViewModel state stays "active" and notification persists

4. Read feature/focus/.../FocusService.kt:completeAndLogCurrentSession
   + core/notifications/.../FocusNotificationManager for id management
```
