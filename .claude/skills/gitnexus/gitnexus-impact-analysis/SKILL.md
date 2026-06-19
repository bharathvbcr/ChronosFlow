---
name: gitnexus-impact-analysis
description: "Use when the user wants to know what will break if they change something, or needs safety analysis before editing code. Examples: \"Is it safe to change X?\", \"What depends on this?\", \"What will break?\""
---

# Impact Analysis with GitNexus — ChronosFlow

## ChronosFlow High-Risk Symbols (know before you touch)

> **MCP server note:** `gitnexus_impact` / `gitnexus_detect_changes` require a Claude Code restart after `npx gitnexus analyze`. Use CLI fallbacks until then.

| Symbol | Risk | Why |
|--------|------|-----|
| `PlannerService` | HIGH | Called by DayDialViewModel, DayDialBlockDelegate, DayDialFocusDelegate, DayDialReviewDelegate, FocusService, ChronosAppFunctions + 5 UseCases |
| `DayDialViewModel` | HIGH | 1,372 lines; imported by MainActivity, TodayTab, PlanTab, FocusTab, SidebarPageContent, ChronosDialRenderModel |
| `ChronosDatabase` | CRITICAL | 910 lines; all DAOs flow through it; changing breaks DataModule (DI), InteropProvider, backup repos, tests |
| `FocusService` | HIGH | Foreground service; callers: MainActivity, ChronosAppFunctions, DayDialFocusNotificationBridge |
| `InteropProvider` | MEDIUM | Exported content provider — signature-pinned; changing query shape breaks Meridian (DevTime) |
| `ChronosPreferencesDataSource` | HIGH | 150+ flows in the graph end here (GetString/PutString/GetBoolean); nearly every screen reads from it |
| `PlannerService.resolveConflicts` | MEDIUM | Undoable via ResolveConflictsCommand; changes break DayDialBlockDelegate + 10 domain tests |
| `SensitiveRouteGate` | HIGH | Security boundary for medication + data export — `fails open` when no lock exists by design |
| `ChronosMotionDefaults` | MEDIUM | Referenced by all Chrono* UI components via shared-transition utilities |

### Architecture Risk Tiers (ChronosFlow)
```
CRITICAL  ChronosDatabase, ChronosPreferencesDataSource (all flows terminate here)
HIGH      PlannerService, DayDialViewModel, FocusService, ChronosNavGraph routes
MEDIUM    InteropProvider, SensitiveRouteGate, GenAiGateway, ChronosMotionDefaults
LOW       Individual feature screens, individual DAOs, Wear tile providers
```

---

## When to Use

- "Is it safe to change this function?"
- "What will break if I modify X?"
- "Show me the blast radius"
- "Who uses this code?"
- Before making non-trivial code changes
- Before committing — to understand what your changes affect

## Workflow

```
1. gitnexus_impact({target: "X", direction: "upstream", repo: "ChronosFlow"})
   CLI fallback: npx gitnexus impact --repo ChronosFlow <symbol>

2. Check affected execution flows from the 300-flow graph
   (Cross-reference the flow table in gitnexus-exploring SKILL.md)

3. gitnexus_detect_changes({repo: "ChronosFlow"})
   CLI fallback: npx gitnexus cypher --repo ChronosFlow \
     "MATCH (s) WHERE s.filePath CONTAINS '<changed file>' RETURN s.name, labels(s)"

4. Assess risk and report to user
```

> If "Index is stale" → `npx gitnexus analyze`, then restart Claude Code for MCP tools.

## Checklist

```
- [ ] Run impact / CLI fallback on the target symbol
- [ ] Review d=1 items first (WILL BREAK)
- [ ] Cross-check against ChronosFlow high-risk table above
- [ ] Check if symbol appears in any of the 300 execution flows
- [ ] gitnexus_detect_changes / cypher for pre-commit check
- [ ] Report risk level to user before editing
```

## Understanding Output

| Depth | Risk Level | Meaning |
|-------|-----------|---------|
| d=1 | **WILL BREAK** | Direct callers/importers |
| d=2 | LIKELY AFFECTED | Indirect dependencies |
| d=3 | MAY NEED TESTING | Transitive effects |

## Risk Assessment (ChronosFlow calibrated)

| Affected | Risk |
|----------|------|
| <5 symbols, single module | LOW |
| 5-15 symbols, 2-5 flows | MEDIUM |
| >15 symbols or cross-module callers | HIGH |
| ChronosDatabase / ChronosPreferencesDataSource / PlannerService | CRITICAL |

## Tools + CLI Fallbacks

**gitnexus_impact** — symbol blast radius:
```
MCP:  gitnexus_impact({target: "PlannerService", direction: "upstream", repo: "ChronosFlow"})
CLI:  npx gitnexus impact --repo ChronosFlow PlannerService
```

**gitnexus_detect_changes** — git-diff impact:
```
MCP:  gitnexus_detect_changes({scope: "staged", repo: "ChronosFlow"})
CLI:  npx gitnexus cypher --repo ChronosFlow \
        "MATCH (f:File) WHERE f.filePath CONTAINS 'PlannerService' \
         MATCH (caller)-[:CodeRelation]->(f) RETURN caller.name, caller.filePath"
```

**gitnexus_context** — see ALL callers before touching anything:
```
MCP:  gitnexus_context({name: "PlannerService", repo: "ChronosFlow"})
CLI:  npx gitnexus context --repo ChronosFlow PlannerService
```

## ChronosFlow Example: "Is it safe to change PlannerService.resolveConflicts?"

```
1. npx gitnexus context --repo ChronosFlow PlannerService
   → d=1 imports: DayDialViewModel, DayDialBlockDelegate, DayDialFocusDelegate,
                  DayDialReviewDelegate, FocusService, ChronosAppFunctions,
                  CreateBlockUseCase, MoveBlockUseCase, ResizeBlockUseCase,
                  CompleteTimeBlockUseCase, ScheduleTaskIntoDayUseCase
   → Tests: PlannerServiceConflictResolutionTest (10 tests cover resolveConflicts)

2. resolveConflicts is called from DayDialBlockDelegate (Fix-schedule UI button)
   → Changes to conflict-detection logic affect the Repair-with-AI flow too
   → ResolveConflictsCommand wraps it for undo — keep the command interface stable

3. Risk: HIGH — 3 delegate callers + 5 use cases + 10 tests
   → Run PlannerServiceConflictResolutionTest + DayDialBlockDelegateTest after any change
```
