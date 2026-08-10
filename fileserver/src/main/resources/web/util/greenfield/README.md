# Greenfield Implementer

A doc-op driven pipeline that takes a one-paragraph product idea and walks it
all the way to a working, tested, documented project skeleton — planning first,
code second, with an inspectable JSON artifact at every step.

Where [`coder/`](../coder/) researches an *existing* codebase and
[`reviewer/`](../reviewer/) critiques one, `greenfield/` starts from an empty
directory. See [`idea.md`](./idea.md) for the design rationale.

---

## Layout

```
greenfield/
├── index.html                 # the entire UI (vanilla JS module, no build step)
├── idea.md                    # the seed idea — read by every stage
├── ops/
│   ├── plan_feature.op.md         # idea.md      -> plan/feature.json
│   ├── plan_stack.op.md           # plan/feature -> plan/stack.json
│   ├── plan_architecture.op.md    # plan/stack   -> plan/architecture.json
│   ├── plan_phases.op.md          # plan/**      -> plan/phases.json
│   ├── plan_tasks.op.md           # plan/phases  -> tasks/<phase>.json
│   ├── plan.task.json             # SubPlan settings for the fan-out
│   ├── impl_scaffold.op.md        # plan/stack   -> build scripts, env setup
│   ├── impl_task.op.md            # tasks/**     -> SubPlan execution
│   ├── impl_tests.op.md           # source       -> test files
│   ├── impl_docs.op.md            # plan/** + src -> README / docs
│   ├── impl.task.json             # SubPlan task settings
│   ├── plan_schema.ts             # Feature/Stack/Architecture/PhasePlan
│   └── task_schema.ts             # BuildTask / BuildPlan
├── plan/                      # generated: the planning artifacts
├── tasks/                     # generated: one task plan per phase
└── tmp/                       # generated: per-task doc-op files + run summaries
```

`plan/`, `tasks/` and `tmp/` are pipeline output and safe to delete.

## Path model

```js
const ROOT_HOPS = 2;   // greenfield/ -> code-utils/ -> repo root
```

* **doc root** — the folder the page is served from (`.../greenfield`);
`idea.md`, `ops/`, `plan/`, `tasks/`, `tmp/` live here.
* **analysis root** — `ROOT_HOPS` levels up; matches `folder: ../../..` in
every op and is what generated *source* paths are relative to.
* Temp ops live in `tmp/`, one level below the doc root, so their prefix is
`../` × (1 + `ROOT_HOPS`) = `../../../` — the same `folder:` value the
checked-in ops use.
* Generated source is written under `StackPlan.target_root`, relative to the
analysis root. It defaults to `.`.

Status is polled from `<doc-root>/docops.status.json` every 3 seconds.

## Pipeline

### Plan (stages 1–5, run by **Run All Steps**)

| # | Stage          | Op                        | In → Out                                     |
|---|----------------|---------------------------|----------------------------------------------|
| 0 | Seed idea      | UI "Save Idea"            | textarea → `idea.md`                          |
| 1 | Feature spec   | `plan_feature.op.md`      | `idea.md` → `plan/feature.json`               |
| 2 | Tech stack     | `plan_stack.op.md`        | `plan/feature.json` → `plan/stack.json`       |
| 3 | Architecture   | `plan_architecture.op.md` | `plan/stack.json` → `plan/architecture.json`  |
| 4 | Phases / WBS   | `plan_phases.op.md`       | `plan/*.json` → `plan/phases.json`            |
| 5 | Task breakdown | `plan_tasks.op.md`        | `plan/phases.json` → `tasks/<phase>.json`     |

Stage 5 in the UI synthesizes one temp op per phase (`tmp/plan-<phase>.op.md`)
so each phase plan is small and individually re-runnable; `plan_tasks.op.md`
is the whole-plan fallback for unattended runs.

### Implement (stages 6–9, **never run implicitly**)

| # | Stage    | Op                    | Produces                             |
|---|----------|-----------------------|--------------------------------------|
| 6 | Scaffold | `impl_scaffold.op.md` | build files, config, env setup, CI    |
| 7 | Code     | `impl_task.op.md`     | source files, per `BuildTask`         |
| 8 | Tests    | `impl_tests.op.md`    | test files per component              |
| 9 | Docs     | `impl_docs.op.md`     | `README.md`, usage docs, ADR notes    |

Stage 7 mirrors `reviewer/`'s stage 5: for each `BuildTask` the UI writes a
temp doc-op and runs it. `target_files[0]` → `specifies:`; remaining targets
plus `related_files` → `related:`:

```
---
specifies: ../../../src/main/kotlin/com/example/Foo.kt
task_type: FileModification
related:
- ../../../src/main/kotlin/com/example/Bar.kt
folder: ../../..
---

Implement the Foo component

<task description…>
```

**📋 Copy op file** on every task card shows exactly what would be written.

## Server contract

`index.html` is a static page; everything else goes through a small JSON API
rooted at the doc root (identical to the one `reviewer/index.html` uses):

| Method | Path                    | Body / Query      | Returns                                 |
|--------|-------------------------|-------------------|-----------------------------------------|
| GET    | `<path>`                | —                 | file contents (static serve)            |
| POST   | `api/write`             | `{path, content}` | 200                                     |
| POST   | `api/delete`            | `{path}`          | 200                                     |
| POST   | `api/run`               | `{op}`            | `{taskId}`                              |
| GET    | `api/list?prefix=tasks` | —                 | `{files:[…]}` *(optional)*              |
| GET    | `docops.status.json`    | —                 | `{tasks:[{id,state,progress,message}]}` |

`api/list` is optional: without it the UI derives the task-plan filenames from
the phase ids in `plan/phases.json`. Reading works against a plain static file
server; only running and writing need the API.

## Persistence

`localStorage`:

* `greenfield_idea` — the draft in the textarea (cleared on save).
* `greenfield_tasks` — run bookkeeping keyed `<plan-path>#<task-id>`.

## Conventions

* **Schema-first.** Every planning stage emits JSON conforming to
`ops/plan_schema.ts` / `ops/task_schema.ts`, never prose.
* **One document per unit.** One doc per phase, per component, per file group.
* **Advisory, not authoritative.** `depends_on` orders the UI list
(`orderTasks()` sorts by priority, then stabilises against dependencies;
cycles are appended, never dropped) but never blocks a run.
* **Nothing implicit.** Roots, status paths and generated op contents are
derived deterministically and shown in the Session panel / op dialog.
* **Self-contained.** The schemas are deliberate *copies* of the reviewer's
shapes, not imports — these are wire formats and coupling two apps' schemas
would hurt later.

## Not implemented yet

* **Verification loop** — shelling out to `build` / `test` and feeding failures
back as new tasks. Deliberately out of scope for the first cut.
* **Staleness tracking** — re-running stage 2 does not invalidate stages 3–5.
* **Multi-module projects** — one `StackPlan` means one stack.