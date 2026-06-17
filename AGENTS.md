<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **ChronosFlow** (20268 symbols, 63705 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

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

## Karpathy Coding Guardrails

Adapted from https://github.com/multica-ai/andrej-karpathy-skills/blob/main/CLAUDE.md and merged with the ChronosFlow-specific rules above.

- Think before coding: state assumptions, call out uncertainty, and clarify ambiguous requests before implementation.
- Prefer the simplest working change: avoid speculative features, unused abstractions, and configurability that was not requested.
- Keep changes surgical: touch only files required by the task, match local style, and clean up only unused code introduced by the change.
- Work from verifiable goals: define the expected outcome, pair code changes with focused checks, and keep looping until current evidence proves the result.
- For code edits, the GitNexus impact-analysis requirements in this file still apply before modifying any function, class, method, or symbol.

## Android Runtime Safety

- Prefer `.\scripts\install-debug-preserve.ps1 [-Device <serial>]` for ChronosFlow phone-app deploy/run checks. It builds the debug APK, installs with `adb install -r`, and launches `.MainActivity` without uninstalling the existing package.
- Use `android run` only when explicitly testing Android CLI behavior itself; verify `pm path com.chronosflow` before and after so a failed deploy does not masquerade as an app deletion.

## Canonical Documentation References

- Current status and behavior are sourced from:
  - `README.md`
  - `README_MINIMAL.md`
  - `docs/product-contract.md`
- Treat roadmap and planning docs as implementation targets or historical context, not as current completion state.
