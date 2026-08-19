# DocOps

*Turn markdown specs into planned, scheduled, executed code changes.*

## Overview

DocOps is a host-agnostic engine that treats markdown documents with YAML frontmatter as
**executable specifications**. Write a doc that says "this file specifies `src/**/*.kt`" or
"every `*.proto` transforms into a `*.kt`," and DocOps figures out which files need to be
created or updated, in what order, with what context, and hands each unit of work off to an
execution backend — typically an LLM coding agent.

Planning is completely pure: `DocOps.plan()` reads your workspace and returns an immutable
plan without touching a single file. Execution — writing files, deleting stale targets,
recording status — only happens when you explicitly call `DocOps.run()`. This separation
means you can inspect, diff, and reason about a plan before anything changes on disk.

## Key Features

* **Frontmatter-driven targeting** — declare targets via `specifies`, `transforms`,
  `documents`, or `generates`; globs, literal paths, and regex-based transform rules are all
  supported.
* **Deterministic planning** — target discovery, queue partitioning, and dependency sorting
  are all stably ordered, so identical inputs always produce identical plans.
* **Smart update modes** — seven built-in modes (`SkipExisting`, `PatchToUpdate`,
  `ForceOverwrite`, etc.) control whether a target is skipped, fully replaced, or fuzzy-patched,
  and whether timestamps gate the decision.
* **Automatic dependency ordering** — a Kahn's-algorithm-based sorter ensures a task's
  related files are produced before tasks that depend on them run.
* **Concurrent, safe execution** — independent queues run in parallel while tasks that touch
  overlapping files run sequentially, with per-task and overall timeouts.
* **Template variables** — `{{ VARS }}` placeholders in frontmatter and body let one doc
  template drive many similar targets.
* **URL context with caching** — `related:` entries can be `http(s)://` URLs; responses are
  cached on disk with conditional requests and HTML simplification.
* **Pluggable everything** — status store, spec loader, resource resolvers, HTTP fetcher,
  planner, partitioner, and sorter are all constructor-injectable, so tests never need the
  network or a real agent.

## Example

A doc-ops markdown file declaring a code target and its context:

```markdown
---
specifies:
    - src/main/kotlin/billing/Invoice.kt
related:
    - schema/billing.sql
update_mode: PatchToUpdate
task_type: Edit
prompt: Keep the public API stable; only change internals.
---

# Invoicing

Invoices are immutable once issued...
```

Planning and running it from Kotlin:

```kotlin
val docOps = DocOps(
  config = DocOpsConfig(
    root = projectRoot,
    docsFolder = File(projectRoot, "docs"),
    updateMode = UpdateModes.PatchToUpdate,
  ),
  host = MyHost(pool),
)

val plan = docOps.plan()                 // pure: no writes, no deletes
println("${plan.tasks.size} task(s) in ${plan.queues.size} queue(s)")

docOps.run(plan) { session -> ui.attach(session) }
```

## Integration

DocOps is a plain JVM library with no Python or external service dependencies. It builds on
other Cognotik modules:

* **antlr** and **text** — for parsing and template rendering support.
* **lwcore** — shared lightweight core utilities.
* Jackson (with YAML/XML/TOML/properties dataformats) for frontmatter and config parsing.

Because `DocOpsHost` is the only platform-specific seam, DocOps drops into any host that can
run a scheduler and execute a task — from a CLI tool to a full agent-driven IDE integration.