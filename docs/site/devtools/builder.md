# 🔨 Builder

*A doc-op pipeline that researches an unfamiliar repo and turns the answer into an executable `build.sh`.*

**Category:** Build &nbsp;·&nbsp; **Status:** Experimental &nbsp;·&nbsp; **Op-Only**

## Overview

Builder answers one question for a repository you did not set up: how do you build and test it? Rather than
producing a document, it researches the project, writes a single standardized `build.sh` at the repository root,
and runs that script under an AutoFix task until `setup`, `compile`, and `test` all succeed. Where the Coder tool
answers questions about an existing codebase and the Reviewer tool critiques one, Builder targets the build itself
— the deliverable is a working entry point, not a write-up.

Builder ships no UI page. It is three op files run directly with a doc-op runner, which also means it works fine
unattended — in CI, a shell loop, or another pipeline.

## Pipeline

1. **Research** — explore the repository and write `research.md`, a requirements document covering modules,
   build tools, language runtimes, system packages, test invocation, and build artifacts.
2. **Draft** — read `research.md` and write a unified, environment-agnostic `build.sh` at the repository root, with
   `setup`, `compile`, and `test` sub-commands.
3. **Run and fix** — execute `build.sh` under an AutoFix loop, patching the script (or the project) until the
   command succeeds, logging the transcript to `build.log.md`.

## Getting Started

1. Locate the three op files in `builder/ops/`: `research_project.op.md`, `draft_build.op.md`, and
   `run_build.op.md`.
2. Run `research_project.op.md` first. It is a `SubPlan` task (configured by `research.task.json`) that explores
   the repository and produces `research.md`, along with a rolled-up summary of what it found.
3. Run `draft_build.op.md`, which reads `research.md` and writes `build.sh` at the repository root, implementing
   `setup`, `compile`, and `test` sub-commands.
4. Run `run_build.op.md` once per mode — `setup`, then `compile`, then `test` — so a failure is attributed to a
   single phase. This `AutoFix` task executes the build, patches whatever is broken, and re-runs until it passes
   or the iteration budget runs out.
5. Review `build.log.md` for a transcript of what was run, what failed, and what was changed.
6. Commit the resulting `build.sh`; it's meant to live at the repo root as a stable, three-verb CLI for any
   downstream automation.

## Artifacts

| Path                 | What it holds                                                          |
|----------------------|-------------------------------------------------------------------------|
| `research.md`        | Generated requirements doc: modules, build tools, runtimes, test setup. |
| `build.sh`           | The generated build script (`setup`, `compile`, `test`), written at the repository root. |
| `build.log.md`       | Transcript of the AutoFix run loop — what was executed, what failed, what was patched. |

## Requirements

- A doc-op runner capable of executing `SubPlan` and `AutoFix` task types, since Builder has no UI to drive them.
- Shell access to actually execute the generated `build.sh` during the run/fix stage.

## Not Implemented Yet

Builder currently assumes Ubuntu 22 as its only tested platform, has no UI or CI wiring, and does not track
staleness — re-running the research stage does not automatically invalidate an existing `build.sh`.