<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **ChronosFlow** (31710 symbols, 131093 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> Index stale? Run `node .gitnexus/run.cjs analyze` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? `npx gitnexus analyze` (npm 11 crash → `npm i -g gitnexus`; #1939).

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows. For regression review, compare against the default branch: `detect_changes({scope: "compare", base_ref: "main"})`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method without first running `impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit changes without running `detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/ChronosFlow/context` | Codebase overview, check index freshness |
| `gitnexus://repo/ChronosFlow/clusters` | All functional areas |
| `gitnexus://repo/ChronosFlow/processes` | All execution flows |
| `gitnexus://repo/ChronosFlow/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

## DevCouncil Repo Map

Use `.devcouncil/repo_map.json` as the **primary file index** before grepping or guessing paths. Symbol impact still goes through GitNexus above.

- Repo map: `.devcouncil/repo_map.json`
- Code graph: `.devcouncil/graph/code_graph.json` (query with `dev graph`; SQLite at `.devcouncil/codeintel/index.sqlite` is canonical if the JSON is missing/stubbed)
- Regenerate after large refactors: `dev map` (or `dev map --if-stale` / `dev map --watch`)

### Always Do (navigation)

1. Open `.devcouncil/repo_map.json` before hunting for files.
2. Use `files` for module ownership and nearby siblings.
3. Use `subsystems` for area-level navigation (`entry_points`, `critical_files`, `role_files`, `neighbors`, `handoff_paths`).
4. Prefer `dev graph query <name>` / `dev graph trace <a> <b>` / `dev graph dead` for callers, paths, and dead-code tiers; `dev graph html` for the visualizer.
5. Prefer `dev graph dead --confidence extracted` + file greps for dead code. Treat `inferred` as unconfirmed. Prefer `unwired_candidates` / `dead_symbol_candidates` over `unreachable_files`. If `entry_roots` are empty or `liveness_unreachable_unreliable` is true, ignore `unreachable_files` and mass inferred dead.
6. Check `unwired_candidates` / `dead_symbol_candidates` before creating new modules — wire what you create into a real caller.
7. If the map and source disagree, trust the source and regenerate the map.

### Important surfaces

1. `app/` — MainActivityTest, AppLockViewModel
2. `benchmark/` — BaselineProfileGenerator, ChronosMacrobenchmark
3. `core/` — AssistNarrative, CaptureIntentClassifier
4. `feature/` — AiReviewSheetTest, CalendarAutoSync
5. `scripts/` — android-studio-mcp, generate_play_store_graphics
6. `wear/` — ChronosComplications, ChronosHabitsComplicationService

Local Claude skills (gitignored): `.claude/skills/devcouncil/SKILL.md` and related DevCouncil skills via `dev skills scaffold`.

## Karpathy Coding Guardrails

Adapted from https://github.com/multica-ai/andrej-karpathy-skills/blob/main/CLAUDE.md and merged with the ChronosFlow-specific rules above.

- Think before coding: state assumptions, call out uncertainty, and clarify ambiguous requests before implementation.
- Prefer the simplest working change: avoid speculative features, unused abstractions, and configurability that was not requested.
- Keep changes surgical: touch only files required by the task, match local style, and clean up only unused code introduced by the change.
- Work from verifiable goals: define the expected outcome, pair code changes with focused checks, and keep looping until current evidence proves the result.
- For code edits, the GitNexus impact-analysis requirements in this file still apply before modifying any function, class, method, or symbol.

## Android Runtime Safety

- Prefer `.\scripts\install-debug-preserve.ps1 [-Device <serial>]` for ChronosFlow phone-app deploy/run checks. It builds the debug APK, installs with `adb install -r`, and launches `.MainActivity` without uninstalling the existing package.
- Use `android run` only when explicitly testing Android CLI behavior itself; verify `pm path com.ChronosFlow.VBCR` before and after so a failed deploy does not masquerade as an app deletion.

## Canonical Documentation References

- Current status and behavior are sourced from:
  - `README.md`
  - `README_MINIMAL.md`
  - `docs/product-contract.md`
- Treat roadmap and planning docs as implementation targets or historical context, not as current completion state.
