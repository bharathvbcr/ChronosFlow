---
name: gitnexus-cli
description: "Use when the user needs to run GitNexus CLI commands like analyze/index a repo, check status, clean the index, generate a wiki, or list indexed repos. Examples: \"Index this repo\", \"Reanalyze the codebase\", \"Generate a wiki\""
---

# GitNexus CLI Commands — ChronosFlow

> **ChronosFlow specifics:**  
> - Always pass `--repo ChronosFlow` on query/context/impact/cypher (12 repos indexed globally)  
> - `analyze` takes ~36 seconds for this repo (1,217 files, skips 118 Swift files — no native binding)  
> - After `analyze`, Claude Code **must be restarted** for MCP tools to reload from the new index  
> - CLI tools (`context`, `impact`, `query`, `cypher`) work **immediately** without restart

## ChronosFlow Quick Commands

```bash
# Check if index is fresh
npx gitnexus status

# Re-index after changes
npx gitnexus analyze

# Look up any symbol
npx gitnexus context --repo ChronosFlow PlannerService
npx gitnexus context --repo ChronosFlow DayDialViewModel
npx gitnexus context --repo ChronosFlow ChronosDatabase

# Blast radius
npx gitnexus impact --repo ChronosFlow PlannerService

# Raw graph query
npx gitnexus cypher --repo ChronosFlow "MATCH (p:Process) RETURN p.id, p.label LIMIT 20"
npx gitnexus cypher --repo ChronosFlow \
  "MATCH (c:Community) RETURN c.id, c.label, c.symbolCount ORDER BY c.symbolCount DESC LIMIT 20"
```

---

## Commands

### analyze — Build or refresh the index

```bash
npx gitnexus analyze
```

Run from the project root. Parses all source files, builds the knowledge graph, writes to `.gitnexus/`, updates CLAUDE.md / AGENTS.md.

| Flag | Effect |
|------|--------|
| `--force` | Force full re-index even if up to date |
| `--embeddings` | Enable embedding generation (off by default) |

**ChronosFlow:** After analyze, symbol counts update to the new commit. MCP server needs restart.

### status — Check index freshness

```bash
npx gitnexus status
```

Shows indexed commit vs current commit — if they differ, index is stale.

### context — 360-degree symbol view

```bash
npx gitnexus context --repo ChronosFlow <symbol>
```

Returns incoming callers/importers, outgoing methods/properties, and processes the symbol participates in.

### impact — Blast radius analysis

```bash
npx gitnexus impact --repo ChronosFlow <symbol>
```

Shows what breaks at depth 1 (WILL BREAK), 2 (LIKELY AFFECTED), 3 (MAY NEED TESTING).

### query — Find execution flows

```bash
npx gitnexus query --repo ChronosFlow "<concept>"
```

Returns processes (execution flows) related to the concept.

### cypher — Raw graph query

```bash
npx gitnexus cypher --repo ChronosFlow "<cypher>"
```

Direct Cypher against the knowledge graph. Node labels: File, Function, Class, Method, Property, Enum, Community, Process. Edge type via `CodeRelation.type`.

### clean — Delete the index

```bash
npx gitnexus clean --force
```

Deletes `.gitnexus/` and unregisters from global registry. Use when index is corrupt.

### wiki — Generate documentation

```bash
npx gitnexus wiki
```

Generates repo docs from the graph using an LLM. Requires API key on first use.

### list — Show all indexed repos

```bash
npx gitnexus list
```

ChronosFlow is one of 12 indexed repos — always include `--repo ChronosFlow` in commands.

## After Indexing

1. `npx gitnexus status` to confirm index is current
2. Restart Claude Code if you want MCP tools (`gitnexus_context`, `gitnexus_impact`, etc.)
3. CLI tools work immediately without restart

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Multiple repositories indexed. Specify --repo` | Add `--repo ChronosFlow` |
| `Symbol 'X' not found` | Symbol name is case-sensitive; try the full class name |
| Swift files skipped | Expected — `tree-sitter-swift` native binding not built on Windows |
| Index stale after re-analyzing | Restart Claude Code to reload MCP server |
| MCP tools return no results | Use CLI fallbacks; MCP may need restart after analyze |
| Gradle daemon locks during analyze | Run `gradlew --stop` first |
