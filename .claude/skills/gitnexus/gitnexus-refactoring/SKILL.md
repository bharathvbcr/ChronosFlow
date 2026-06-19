---
name: gitnexus-refactoring
description: "Use when the user wants to rename, extract, split, move, or restructure code safely. Examples: \"Rename this function\", \"Extract this into a module\", \"Refactor this class\", \"Move this to a separate file\""
---

# Refactoring with GitNexus — ChronosFlow

## ChronosFlow Refactoring Hazards

> **MCP server note:** `gitnexus_rename` / `gitnexus_impact` require a Claude Code restart after `npx gitnexus analyze`. Use CLI fallbacks below.

### Never rename these without the call graph

| Symbol | Why it's dangerous |
|--------|-------------------|
| `com.ChronosFlow.VBCR` package | Just renamed from `com.chronosflow` in Jun 2026 — 33 dirs, 955 files; must also match wear `applicationId` and Meridian peer `com.Meridian.VBCR` |
| `ChronosDatabase` DAOs | DataModule DI binds them by type; InteropProvider queries them by name |
| `InteropContract` URI paths | Byte-mirrored with DevTime/Meridian repo — change here = change there or interop breaks |
| `AlarmRequestType.*` | Used across workers, receivers, DataStore keys — string representation matters |
| `SidebarPage.*` | Nav route enum; changing values breaks deep-link parsing in `NotificationNavigation` |
| `ChronosPreferencesDataSource` key strings | DataStore keys are persisted — renaming = silent data loss for existing installs |

### Safe to rename (low cohesion, few callers)
- Individual `InsightsSection` enum values (affects only InsightsTab filter pills)
- `WearFormat` helper functions (only used within `:wear`)
- Private methods in individual feature screens

---

## When to Use

- "Rename this function safely"
- "Extract this into a module"
- "Split this service"
- "Move this to a new file"

## Workflow

```
1. gitnexus_impact({target: "X", direction: "upstream", repo: "ChronosFlow"})
   CLI fallback: npx gitnexus impact --repo ChronosFlow <symbol>

2. gitnexus_context({name: "X", repo: "ChronosFlow"})
   CLI fallback: npx gitnexus context --repo ChronosFlow <symbol>

3. gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true, repo: "ChronosFlow"})
   CLI fallback: use LSP goToDefinition + findReferences; manual multi-file edit

4. Plan update order: interfaces → implementations → callers → tests
5. gitnexus_detect_changes() after to verify scope
   CLI fallback: git diff --stat
```

> If index is stale → `npx gitnexus analyze`, restart Claude Code for MCP tools.

## Checklists

### Rename Symbol
```
- [ ] npx gitnexus context --repo ChronosFlow <symbol> — count callers first
- [ ] Check ChronosFlow hazard table above — is it in there?
- [ ] gitnexus_rename dry_run=true OR LSP findReferences for full list
- [ ] Update interfaces before implementations
- [ ] Update DataStore key strings if renaming preference keys (warn: data loss risk)
- [ ] gitnexus_detect_changes / git diff --stat after — verify scope
- [ ] Run tests for affected module
```

### Extract to New Module
```
- [ ] npx gitnexus context --repo ChronosFlow <symbol> — see all cross-module imports
- [ ] Check circular dep constraints: core:data ↛ core:ai (circular — never add this edge)
- [ ] Add new module to settings.gradle.kts
- [ ] Wire Hilt module in new module's di/ package
- [ ] Update all import sites
- [ ] gitnexus_detect_changes to verify nothing unexpected changed
```

### Split DayDialViewModel (if ever needed)
```
- [ ] Already uses delegate pattern: DayDialBlockDelegate, DayDialFocusDelegate, DayDialReviewDelegate
- [ ] DayDialViewModel is 1,372 lines — if splitting, extract a 4th delegate
- [ ] All delegates must keep PlannerService reference (it's the scheduling source of truth)
- [ ] DayDialFocusNotificationBridge must stay wired to whichever class owns focus state
```

## Tools + CLI Fallbacks

**gitnexus_rename** — automated multi-file rename:
```
MCP:  gitnexus_rename({symbol_name: "oldName", new_name: "newName", dry_run: true, repo: "ChronosFlow"})
CLI:  (no CLI equivalent — use LSP findReferences + manual Edit)
      LSP: findReferences on the symbol, then Edit each call site
```

**gitnexus_impact** — map all dependents:
```
MCP:  gitnexus_impact({target: "oldName", direction: "upstream", repo: "ChronosFlow"})
CLI:  npx gitnexus impact --repo ChronosFlow <symbol>
```

**gitnexus_context** — see all incoming/outgoing refs:
```
MCP:  gitnexus_context({name: "oldName", repo: "ChronosFlow"})
CLI:  npx gitnexus context --repo ChronosFlow <symbol>
```

**gitnexus_cypher** — find string references (DataStore keys, deep-link routes):
```
CLI:  npx gitnexus cypher --repo ChronosFlow \
        "MATCH (s) WHERE s.uid CONTAINS 'MySymbol' RETURN s.name, s.filePath, labels(s)"
      # Also: Grep for the string literal in DataStore calls
```

## ChronosFlow Example: "Extract conflict resolution logic from PlannerService"

```
1. npx gitnexus context --repo ChronosFlow PlannerService
   → Called by: DayDialBlockDelegate, DayDialFocusDelegate, DayDialReviewDelegate,
                FocusService, ChronosAppFunctions, 5 UseCases
   → Risk: HIGH — 10+ callers across 4 modules

2. resolveConflicts is already wrapped by ResolveConflictsCommand (undo pattern)
   → Extract to ConflictResolver class inside core:domain/planner/
   → Keep PlannerService as the facade (don't change its public API)
   → Update: just the one PlannerService.resolveConflicts body

3. gitnexus_detect_changes / git diff --stat after
   → Should only show PlannerService.kt + new ConflictResolver.kt
   → If DayDialBlockDelegate shows in diff, something leaked out of the intended scope
```
