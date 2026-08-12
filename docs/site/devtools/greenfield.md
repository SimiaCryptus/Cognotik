# 🌱 Greenfield Implementer

*Take a one-paragraph product idea and walk it all the way to a working, tested, documented project skeleton.*

`Plan` · `experimental`

## Overview

Greenfield Implementer is a doc-op driven pipeline that turns a short product idea into a planned, scaffolded, and
documented codebase. Where the `coder` tool researches an existing codebase and `reviewer` critiques one, Greenfield
starts from an empty directory: it plans the feature, chooses a stack, sketches an architecture, breaks the work into
phases and tasks, and then (on request) writes the scaffold, source, tests, and docs — with an inspectable JSON
artifact at every step.

## Pipeline

1. Seed idea — saved from the UI into `idea.md`
2. Feature spec — `plan/feature.json`
3. Tech stack — `plan/stack.json`
4. Architecture — `plan/architecture.json`
5. Phases / WBS — `plan/phases.json`
6. Task breakdown — `tasks/<phase>.json`
7. Scaffold — build files, config, env setup, CI
8. Code — source files per task
9. Tests — test files per component
10. Docs — `README.md`, usage docs, ADR notes

## Getting Started

1. Open the Greenfield page and enter your one-paragraph product idea, then click **Save Idea** to write it to `idea.md`.
2. Click **Run All Steps** to run stages 1–5: feature spec, tech stack, architecture, phases, and per-phase task
   breakdowns.
3. Review the generated JSON under `plan/` and `tasks/` — each stage's output feeds directly into the next.
4. When you're ready to generate code, run the implementation stages individually: **Scaffold**, then **Code** (one
   task at a time), then **Tests**, then **Docs**. These are never run automatically.
5. Use **📋 Copy op file** on any task card to inspect exactly what doc-op will be written and run before executing it.
6. Poll progress from the Session panel, which reads `docops.status.json` every few seconds.

## Artifacts

| Path              | What it holds                                                        |
|--------------------|----------------------------------------------------------------------|
| `idea.md`          | The seed product idea, read by every planning stage                  |
| `plan/feature.json`| The generated feature specification                                  |
| `plan/stack.json`  | The chosen technology stack                                          |
| `plan/architecture.json` | The proposed system architecture                                |
| `plan/phases.json` | The work breakdown structure, split into phases                      |
| `tasks/<phase>.json` | Per-phase task lists (`BuildTask`/`BuildPlan` records)              |
| `tmp/`             | Per-task doc-op files and run summaries generated during implementation |

## Requirements

- A write-capable API at the doc root for saving idea text and plan/task JSON (`api/write`, `api/delete`)
- A run endpoint capable of executing doc-ops and reporting progress (`api/run`, `docops.status.json`)
- Optional: a directory listing endpoint (`api/list`) to discover generated task-plan files; without it the UI
  derives filenames from `plan/phases.json`

## Local State

Your in-progress idea draft and task run bookkeeping (which tasks have been started or completed) stay in the
browser's `localStorage` between sessions.