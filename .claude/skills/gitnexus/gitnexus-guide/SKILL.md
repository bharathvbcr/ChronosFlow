---
name: gitnexus-guide
description: "Use when the user asks about GitNexus itself — available tools, how to query the knowledge graph, MCP resources, graph schema, or workflow reference. Examples: \"What GitNexus tools are available?\", \"How do I use GitNexus?\""
---

# GitNexus Guide — ChronosFlow

**Repo:** `ChronosFlow` · **Index:** 21,610 symbols · 73,807 edges · 911 clusters · 300 flows

## MCP vs CLI — Important

The `gitnexus_*` MCP tools are the preferred interface but **require a Claude Code restart** after running `npx gitnexus analyze`. Until restarted, all operations must use the CLI equivalents:

| MCP Tool | CLI Equivalent |
|----------|---------------|
| `gitnexus_query` | `npx gitnexus query --repo ChronosFlow "<query>"` |
| `gitnexus_context` | `npx gitnexus context --repo ChronosFlow <symbol>` |
| `gitnexus_impact` | `npx gitnexus impact --repo ChronosFlow <symbol>` |
| `gitnexus_detect_changes` | `git diff --stat` + `npx gitnexus cypher --repo ChronosFlow "MATCH ..."` |
| `gitnexus_rename` | LSP `findReferences` + manual `Edit` (no CLI rename command) |
| `gitnexus_cypher` | `npx gitnexus cypher --repo ChronosFlow "<cypher>"` |
| Resource reads | `npx gitnexus status` for freshness; cypher for data |

> **Always pass `--repo ChronosFlow`** — there are 12 repos indexed globally and the CLI will error without it.

---

## Always Start Here

1. Check freshness: `npx gitnexus status` or `READ gitnexus://repo/ChronosFlow/context`
2. Match your task to a skill below and read that skill file
3. Follow the skill's workflow — use CLI fallbacks if MCP unavailable

> If stale → `npx gitnexus analyze` (35 seconds), then restart Claude Code.

## Skills

| Task | Skill | Key CLI |
|------|-------|---------|
| Architecture / "How does X work?" | `gitnexus-exploring` | `npx gitnexus context --repo ChronosFlow <sym>` |
| Blast radius / "What breaks?" | `gitnexus-impact-analysis` | `npx gitnexus impact --repo ChronosFlow <sym>` |
| "Why is X failing?" | `gitnexus-debugging` | `npx gitnexus cypher --repo ChronosFlow "MATCH..."` |
| Rename / extract / refactor | `gitnexus-refactoring` | LSP findReferences + manual Edit |
| Index/status/wiki CLI | `gitnexus-cli` | `npx gitnexus analyze / status / wiki` |

## Tools Reference

| Tool | What it gives you |
|------|------------------|
| `query` | Process-grouped flows related to a concept |
| `context` | 360-degree symbol view — callers, callees, processes |
| `impact` | Symbol blast radius at depth 1/2/3 with confidence |
| `detect_changes` | Git-diff impact — what your current changes affect |
| `rename` | Multi-file rename with confidence-tagged edits |
| `cypher` | Raw graph queries against the knowledge graph |

## Resources Reference (ChronosFlow)

| Resource | Content |
|----------|---------|
| `gitnexus://repo/ChronosFlow/context` | Stats, staleness check |
| `gitnexus://repo/ChronosFlow/clusters` | 911 functional areas with cohesion scores |
| `gitnexus://repo/ChronosFlow/cluster/{name}` | Area members (e.g. cluster/Habits, cluster/Focus) |
| `gitnexus://repo/ChronosFlow/processes` | All 300 execution flows |
| `gitnexus://repo/ChronosFlow/process/{name}` | Step-by-step trace |
| `gitnexus://repo/ChronosFlow/schema` | Graph schema for Cypher |

## Graph Schema

**Nodes:** File, Function, Class, Interface, Method, Property, Enum, Variable, TypeAlias, Community, Process  
**Edges (via CodeRelation.type):** CALLS, IMPORTS, EXTENDS, IMPLEMENTS, DEFINES, MEMBER_OF, STEP_IN_PROCESS, HAS_METHOD, HAS_PROPERTY

**Node counts in ChronosFlow:**
Property 10,946 · Method 4,335 · Function 1,683 · Class 1,251 · File 1,217 · Community 790 · Enum 369 · Process 300

**Key Cypher patterns:**
```cypher
-- Who calls PlannerService?
MATCH (caller)-[:CodeRelation {type:'CALLS'}]->(t {name:'PlannerService'})
RETURN caller.name, caller.filePath

-- All processes that pass through a symbol
MATCH (p:Process)-[:STEP_IN_PROCESS]->(s {name:'resolveConflicts'})
RETURN p.id, p.label

-- Symbols in the Focus cluster (comm_273)
MATCH (s)-[:MEMBER_OF]->(c:Community {id:'comm_273'})
RETURN s.name, labels(s), s.filePath

-- Top 20 processes by label
MATCH (p:Process) RETURN p.id, p.label, p.stepCount ORDER BY p.label LIMIT 20
```
