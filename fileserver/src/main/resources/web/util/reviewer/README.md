# Reviewer

A single-page, dependency-light web UI that drives a **schema-driven code-review
pipeline** built on top of Cognotik *doc-ops*.

Instead of asking a model for free-form prose, every stage of this pipeline emits
JSON that conforms to a fixed, code-defined schema (`ops/analysis_schema.ts`,
`ops/followup_schema.ts`). That makes the output renderable, diffable and — most
importantly — *executable*: findings become follow-up tasks, and follow-up tasks
become generated doc-op files that patch the code.

---

## Contents

```
reviewer/
├── index.html                  # the entire UI (vanilla JS module, no build step)
├── focus.md                    # the current focus query — read by every stage
├── README.md
├── ops/                        # doc-op definitions (front-matter + instructions)
│   ├── process_files.op.md         # source file  -> review/<path>.json
│   ├── process_packages.op.md      # folder       -> review/<pkg>.json
│   ├── plan_followup_multi.op.md   # review/**    -> tasks/**.json
│   ├── plan_followup_single.op.md  # review/**    -> tasks.json
│   ├── process_followup.op.md      # tasks        -> SubPlan execution
│   ├── followup.task.json          # SubPlan task settings
│   ├── analysis_schema.ts          # FileAnalysis / PackageAnalysis schemas
│   └── followup_schema.ts          # FollowupPlan / FollowupTask schemas
├── review/                     # generated: per-file and per-package analyses
├── tasks/                      # generated: one follow-up plan per review doc
├── tasks.json                  # generated: aggregate follow-up plan
└── tmp/                        # generated: temporary per-task doc-op files
```

`review/`, `tasks/`, `tasks.json` and `tmp/` are all produced by the pipeline and
are safe to delete at any time (the UI has delete buttons for each stage).

---

## Requirements

* A server that exposes the Cognotik doc-op and file-IO endpoints used by the page:
* `/app/docops.js` — `runDocOp`, `waitForTask`, `createStatusPoller`
* `/app/fileIO.js` — `readFile`, `writeFile`, `listFiles`, `deleteFile`
* `/lib/marked.min.js` — local copy of [marked](https://marked.js.org) for
markdown rendering (loaded **before** the module script)
* `index.html` served from the `reviewer/` folder of the repository, so that the
page can locate `focus.md`, `ops/`, `review/` and `tasks/` next to itself.

There is no build step, bundler, or npm install. Open the page and go.

---

## Path model

Two roots matter, and the UI derives both from the page URL — nothing is
configured by hand:

| Root              | Meaning                                                                                                                                                                                                                 |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **doc root**      | The folder this page is served from (`.../reviewer`). All project artifacts (`focus.md`, `ops/`, `review/`, `tasks/`) are relative to it.                                                                               |
| **analysis root** | Two levels above the doc root (`reviewer/` → `cognotik-tools/` → repo root). This matches the `folder: ../../..` declared by every op, and is the root that **target file paths** in reviews and tasks are relative to. |

The hop count lives in one constant:

```js
const ROOT_HOPS = 2;
```

If you relocate `reviewer/` to a different depth, change `ROOT_HOPS` and the
`folder:` front-matter in `ops/*.op.md` together.

Doc-op run status is polled from `<served-root>/docops.status.json` — the runner
writes it to the served root, never next to the page, so the UI strips the
document sub-directory when computing the status base. The resolved *base*,
*doc root*, *analysis root* and *status* paths are all shown in the **Session**
panel in the sidebar; check there first if reads or writes behave unexpectedly.

---

## The pipeline

### 1. Focus query → `focus.md`

Type what the review should be about — *"error-handling consistency"*, *"unused
exports"*, *"security of the auth flow"* — and press **Save Focus**. The text is
written to `focus.md`, which is listed as a `related:` file by every downstream
op, so it is the single source of truth for review intent.

Every stage calls `ensureFocus()` first, so an unsaved edit in the textarea is
persisted before the op runs.

### 2. Analyze files → `review/**.json`

Op: `ops/process_files.op.md` — `(.*) -> ../review/$1.json`

For each path in the **Files to review** box, one review document is produced:

```
src/main/kotlin/com/example/Foo.kt
  -> review/src/main/kotlin/com/example/Foo.kt.json
```

Each document is a `FileAnalysis`: a `file`, a `summary`, and a list of
`findings` with `category`, `severity`, `message`, optional `location`,
`suggested_fix`, `confidence` and `tags`.

### 3. Summarize packages → `review/<pkg>.json`

Op: `ops/process_packages.op.md` — `(.*)/[^/]+.(?:kt|js) -> ../review/$1.json`

Rolls the per-file findings of a folder up into a `PackageAnalysis`: which files
contributed, what patterns recur across them, and an `overall_severity`.

If the target box is empty, the folders are inferred from the per-file reviews
that already exist on disk. Because a package rollup is named after a *folder*,
its basename has no source extension — that is exactly how the UI tells the two
kinds of review document apart:

```js
const isPackageReview = (p) => !/\.[a-z0-9]+\.json$/i.test(basename(p));
```

### 4. Plan follow-ups

Two flavours, both emitting a `FollowupPlan`:

* **per review** — `ops/plan_followup_multi.op.md`,
`../review/(.*).json -> ../tasks/$1.json`. One plan per review document,
keeping tasks scoped and parallelizable.
* **aggregate** — `ops/plan_followup_single.op.md`, `specifies: ../tasks.json`.
One plan over everything, better for cross-cutting refactors.

A `FollowupTask` carries an `id`, `title`, `description`, `priority`,
`task_type` (default `FileModification`), `target_files`, `related_files`,
advisory `depends_on` links, `tags`, `notes` and back-references to the
`source_findings` that motivated it.

### 5. Execute tasks → `tmp/*.op.md`

This is the part that makes the plan real. For each task the UI **synthesizes a
doc-op file on the fly** and runs it:

```md
---
specifies: ../../../src/main/kotlin/com/example/Foo.kt
task_type: FileModification
related:
  - ../../../src/main/kotlin/com/example/Bar.kt
folder: ../../..
---

Guard against a null config

<task description…>
```

* `target_files[0]` becomes `specifies:` — it must be the primary file.
* Remaining targets plus `related_files` become `related:` context.
* The `../` prefix is computed from the temp directory depth plus `ROOT_HOPS`.

Use **Copy op file** on any task card to inspect exactly what would be written
before running it.

Alternatively, `ops/process_followup.op.md` executes an entire plan in one shot
as a `SubPlan` task, configured by `ops/followup.task.json`.

---

## Using the UI

### Running stages

* **Run All Steps** — focus → files → packages → tasks-multi → tasks-single, with
the last three individually toggleable via the checkboxes. Task *execution* is
deliberately excluded: nothing modifies your code until you ask it to.
* **▶ run** on any step line — runs that one stage in isolation.
* **🗑 delete** on any step line — deletes that stage's artifacts (with a
confirmation listing the first 12 paths) and resets the badge so it can be
re-run.
* **Ctrl/Cmd+Enter** in the focus textarea — runs the whole pipeline.

Each step shows live progress (`3/12 · RUNNING`) driven by both the per-target
`waitForTask` callback and a 3-second background status poller.

### Sidebar

* **Review Documents** — every `review/**.json`; click to render, 🗑 to delete.
**→ targets** refills the target list from the per-file reviews that exist,
which is the fastest way to re-review the same set under a new focus query.
* **Task Plans** — `tasks.json` plus every `tasks/**.json`. Click one to load it,
or **Load all** to pull in every plan at once.

### Review tab

Renders `FileAnalysis`, `PackageAnalysis`, and the aggregate `*Plan` wrappers as
severity-coded cards with badges for category, location, confidence and tags.
Markdown in `summary` fields is rendered. Toggle **View raw** for the JSON
source, or **Copy source** to take it elsewhere. Unparseable JSON falls back to
raw display and logs an error rather than blanking the panel.

### Tasks tab

Task cards show priority, task type, id, run status, dependency chips and the
path of the temp op that would be generated. Per card:

| Button                                      | Effect                                              |
|---------------------------------------------|-----------------------------------------------------|
| ▶ Run task                                  | Write the temp doc-op and execute it                |
| Mark done / Reset status                    | Adjust bookkeeping without running anything         |
| Copy description / Copy JSON / Copy op file | Clipboard helpers                                   |
| Review this                                 | Load the task as the next focus query + target list |

Toolbar actions: **▶ Run ready tasks** (skips tasks with unmet dependencies),
**▶▶ Run all** (dependency-ordered, ignores readiness), **Reset statuses**.
A batch stops at the first failure so a broken task does not cascade.

#### Ordering and dependencies

`orderTasks()` sorts by priority (`critical → high → medium → low`) and then
stabilises the list so a task never precedes a dependency that exists in the same
plan; unresolvable cycles are appended rather than dropped.

**Dependencies are advisory only.** An unmet `depends_on` marks the card
`deps pending` and logs a warning, but never blocks a manual run — the review is
a suggestion engine, not a gatekeeper.

#### Persistence

Run statuses live in `localStorage` under `codeReview_tasks`, keyed as
`<plan-path>#<task-id>`, so completion survives reloads. The target list is
stored under `codeReview_targets`. Nothing else is cached — reviews and plans are
always read from disk.

---

## Extending

**Add a stage.** Drop a new `ops/<name>.op.md`, register it in the `OPS` map, add
a `stage*()` function, an entry in `STAGES`, a `<li data-step="…">` in the steps
list, and (if it produces deletable files) an entry in `STEP_ARTIFACTS`.

**Change the schema.** Edit `ops/analysis_schema.ts` or
`ops/followup_schema.ts` — they are listed as `related:` files, so the ops pick
up changes immediately. Both files also export draft-07 JSON Schemas
(`FILE_ANALYSIS_PLAN_JSON_SCHEMA`, `PACKAGE_ANALYSIS_PLAN_JSON_SCHEMA`,
`FOLLOWUP_PLAN_JSON_SCHEMA`) if you want to validate generated documents with
ajv in CI.

**Change the rendering.** `renderReviewDoc()` dispatches on document shape
(`files[]`, `packages[]`, single `package`, single `file`/`findings`) and falls
back to pretty-printed JSON, so new shapes degrade gracefully.

---

## Troubleshooting

| Symptom                                           | Likely cause                                                                                                                                         |
|---------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| *"Could not determine a base path from this URL"* | The page is not served from a path the file-IO layer can resolve; file access is disabled.                                                           |
| *"No task id returned … cannot link to monitor"*  | The op started but the runner gave no id; the target is still polled to completion.                                                                  |
| Step stuck on `running`                           | Status file not where expected — compare the **status** path in the Session panel against the actual `docops.status.json`.                           |
| *"is not parseable JSON"*                         | The model wrapped output in fences or emitted prose. `stripFences()` handles ```` ```json ```` blocks; anything else needs a schema-tightening pass. |
| *"declares no target_files"*                      | The plan produced an unexecutable task — fix the plan or the op instructions.                                                                        |
| Task writes to the wrong path                     | `ROOT_HOPS` and the ops' `folder:` are out of sync with the actual folder depth.                                                                     |

The in-page log (colour-coded, timestamped) records every op invocation, target
path and failure — it is the first place to look.

---

## Design notes

* **Schema-first.** Fixed schemas beat query-derived ones: the UI can render,
aggregate and execute results without guessing at their shape.
* **One document per unit.** Per-file and per-folder documents keep each op's
context small and let stages run incrementally and in parallel.
* **Advisory, not authoritative.** Dependencies, readiness and priority are
hints. Every task remains individually runnable, re-runnable and skippable.
* **Nothing implicit.** Analysis root, status location and generated op contents
are all derived deterministically and displayed, so the plumbing is inspectable
rather than magic.