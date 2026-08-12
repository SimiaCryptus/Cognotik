# 🌱 Greenfield

*Turn a one-page idea into a planned, scaffolded, implemented, tested and documented project.*

`Build` · `beta`

## Overview

Greenfield is a chain of doc-ops that takes a single hand-written seed document (`idea.md`) and drives it through
planning, scaffolding, implementation, testing and documentation. Each stage emits a schema-validated JSON artifact
(a feature plan, a stack plan, an architecture plan, a phase plan, and per-phase build plans) rather than prose, so
the output of every stage is renderable, diffable, and directly executable by the next one. Anyone starting a new
project from scratch — and wanting a reviewable paper trail from "what is this" to "here is working, tested,
documented code" — is the intended user.

## Pipeline

1. `idea.md` — human-written seed describing the problem, users, and constraints
2. `plan/feature.json` — structured feature plan (user stories, acceptance criteria, non-goals)
3. `plan/stack.json` — chosen language, runtime, build tool, test framework, and libraries
4. `plan/architecture.json` — components, data model, boundaries, directory layout, risks
5. `plan/phases.json` — ordered delivery phases starting with a walking skeleton
6. `tasks/*.json` — one build plan per phase, fanned out into individual file-edit tasks
7. scaffolded build files, CI, and a passing (near-empty) test target
8. feature source, implemented task by task
9. a full test suite
10. `README.md`, `docs/architecture.md`, and ADRs describing what was actually built

## Getting Started

1. Write `idea.md`: a short, honest statement of the problem, the users, and any hard constraints or non-goals.
2. Run the planning stages (`plan_feature` → `plan_stack` → `plan_architecture` → `plan_phases` → `plan_tasks`) in
   order, reviewing each generated JSON file before moving to the next — a bad architecture plan propagates into
   every later task.
3. Check `tasks/*.json`: one file per phase, with phase 0 always being a minimal walking skeleton.
4. Run `impl_scaffold` first and alone, and confirm the build's `dev` and `test` targets actually work before writing
   any feature logic.
5. Work through `impl_task` phase by phase (via the tool's task list or unattended), running the build after each
   task, then run `impl_tests` and finally `impl_docs` to produce the test suite and documentation.
6. If you change an upstream artifact (the idea, the stack, or the architecture), re-run only the stages downstream
   of that change — each stage reads only committed artifacts, so partial re-runs are safe.

## Artifacts

| Path            | What it holds                                                              |
|------------------|-----------------------------------------------------------------------------|
| `idea.md`        | The hand-written seed document that starts the pipeline                   |
| `plan/*.json`     | Feature, stack, architecture, and phase plans — the durable, reviewable planning output |
| `tasks/*.json`    | One `BuildPlan` per phase, listing the individual file-edit tasks to run   |
| `tmp/`            | Disposable run summaries and synthesized per-task doc-ops                 |

## Requirements

- A doc-op runner capable of executing `FileModification` and `SubPlan` task types
- Schema validation against the pipeline's code-defined JSON schemas, to catch drifted or invalid stage output early
- A build tool and test runner for the target stack, so the scaffold and later phases can be verified as they run

## Local State

The task browser keeps track of which build-plan tasks you've viewed and run in `localStorage`, so your progress
through a phase's task list persists between sessions in the browser.