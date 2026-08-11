# Builder

A doc-op pipeline that answers one question for a repository you did not set up:
**how do I build and test this thing?** — and then makes the answer executable.

It researches the project, writes a single standardized `build.sh` at the repo root, and runs that script under an
**AutoFix** task until `setup`, `compile`
and `test` all succeed.

Where [`coder/`](../coder/) researches an existing codebase to answer questions and [`reviewer/`](../reviewer/)
critiques one, `builder/` targets the build itself: the deliverable is not a document, it is a working entry point.
See [`idea.md`](./idea.md) for the three-line design sketch this grew from.

> **No UI.** Unlike the other tools in `code-utils/`, builder has no
> `index.html`. It is three op files you run directly with a doc-op runner, so
> it also works fine unattended (CI, a shell loop, another pipeline).

---

## Layout

```
builder/
├── README.md
├── idea.md                       # the seed idea (research -> draft -> run)
├── ops/
│   ├── research_project.op.md    # repo        -> ../research.md   (SubPlan)
│   ├── research.task.json        # SubPlan settings for the research stage
│   ├── draft_build.op.md         # research.md -> <root>/build.sh
│   └── run_build.op.md           # build.sh    -> ../build.log.md  (AutoFix)
├── research.md                   # generated
└── build.log.md                  # generated
```

`research.md` and `build.log.md` are pipeline output and safe to delete; the generated `build.sh` lives at the
**analysis root**, not in this folder, and is meant to be committed.

## Path model

Every op declares:

```yaml
folder: ../../..
```

which walks `ops/` → `builder/` → `code-utils/` → repo root. That repo root is the **analysis root**: the folder the
research stage explores and the folder paths in `build.sh` are relative to.

Documents are addressed relative to the op file itself, so:

| Front matter                   | Resolves to                       |
|--------------------------------|-----------------------------------|
| `specifies: ../research.md`    | `code-utils/builder/research.md`  |
| `specifies: ../build.log.md`   | `code-utils/builder/build.log.md` |
| `specifies: ../../../build.sh` | `build.sh` at the repo root       |

If you relocate `builder/` to a different depth, change `folder:` in all three ops **and** the `../../../build.sh`
target in `draft_build.op.md` together.

## Pipeline

| # | Stage       | Op                       | In → Out                          |
|---|-------------|--------------------------|-----------------------------------|
| 1 | Research    | `research_project.op.md` | the repo → `research.md`          |
| 2 | Draft       | `draft_build.op.md`      | `research.md` → `<root>/build.sh` |
| 3 | Run and fix | `run_build.op.md`        | `build.sh` → `build.log.md`       |

Run them in order: stage 2 lists `research.md` as a `related:` file, and stage 3 is only useful once a script exists to
execute.

### 1. Research the project

`research_project.op.md` is a `SubPlan` task configured by
[`research.task.json`](./ops/research.task.json):

* **Adaptive** cognitive mode, `Project Manager` strategy
* up to **10 iterations**, **5 tasks per iteration**, 10 000 chars of history
* sub-tasks: `FileSearch` and `FileReview`

It explores the analysis root and writes `research.md` — the requirements document for the build system. The interesting
output is everything a fresh machine would need to know: modules and their layout, build tools and their versions,
language runtimes, system packages, how tests are invoked, what artifacts are produced and where they land.

Because it is a SubPlan, the stage also emits a rolled-up summary using the
`summaryPrompt` in the task config, so a long exploration collapses into something reviewable.

### 2. Draft the build script

`draft_build.op.md` specifies `../../../build.sh` and reads `research.md` as context. The instructions ask for a
*unified, environment-agnostic* script with three sub-commands:

| Sub-command | Contract                                                                |
|-------------|-------------------------------------------------------------------------|
| `setup`     | Install every dependency — system packages and language runtimes alike. |
| `compile`   | Compile the source and produce the project's artifacts.                 |
| `test`      | Run the test suite and fail loudly if anything regresses.               |

Ubuntu 22 is the only assumed platform for now, but the script is expected to be written *for extensibility* — detect
the environment, keep the platform-specific bits behind a seam, so a second distro (or macOS) is an addition rather than
a rewrite.

This three-verb CLI is the closest thing builder has to a schema: the other tools in this repo pin their stages to a
JSON shape, builder pins its stage to a command-line contract. Downstream automation can rely on `./build.sh setup`,
`./build.sh compile` and `./build.sh test` existing and returning a meaningful exit code.

### 3. Run it, and fix what breaks

`run_build.op.md` is an `AutoFix` task that maintains `../build.log.md`. It executes the build, feeds failures back in,
patches the offending files (the script itself, or the project) and re-runs until the command succeeds or the iteration
budget is exhausted. `build.log.md` is the transcript of that loop:
what was run, what failed, what was changed.

Run it once per mode — `setup`, then `compile`, then `test` — so a failure is attributed to one phase instead of a
tangle of three.

## Conventions

* **The artifact is executable.** Research and the run log are markdown, but the thing the pipeline exists to produce is
  a script with a stable CLI.
* **One document per stage.** `research.md` and `build.log.md` are separate documents so each op keeps a small, focused
  context.
* **Nothing implicit.** The analysis root is `folder: ../../..` in every op, and
  `build.sh` is always written at that root — never next to the ops.
* **Idempotent by intent.** `setup` should be safe to re-run; AutoFix will call it repeatedly and a script that only
  works once will fight the loop.
* **Fail loudly.** `set -euo pipefail` and non-zero exits are what make stage 3 able to tell success from silent
  breakage.

## Extending

**Add a platform.** Extend the environment detection in `build.sh` and note the new target in `draft_build.op.md`'s
instruction body, so a regenerated script keeps the support.

**Add a sub-command.** `lint`, `package`, `run` and `clean` are the obvious next verbs. Add them to the bullet list in
`draft_build.op.md`; the AutoFix stage needs no changes to start exercising them.

**Tune the research depth.** `research.task.json` controls how hard stage 1 digs. Raise `maxIterations` /
`maxTasksPerIteration` for a large monorepo, lower them for a single-module project.

**Add a stage.** Drop a new `ops/<name>.op.md` with `folder: ../../..`, a
`specifies:` target under `builder/`, and any `related:` documents it should read.

## Not implemented yet

* **No UI.** There is no `index.html`, so no progress view, no per-stage buttons and no status polling — run the ops
  directly. This is why the catalog entry in
  [`../apps.js`](../apps.js) carries `entry: null`.
* **One platform.** Ubuntu 22 only, in practice; extensibility is a design goal, not yet a tested one.
* **No CI wiring.** Nothing generates a workflow file that calls `build.sh`.
* **No staleness tracking.** Re-running stage 1 does not invalidate an existing
  `build.sh`; re-run stage 2 yourself.