# Code Research

`ops/` contains the docops operation files for a self-contained "app" that
automates a research → summarize → follow-up → execute pipeline over a
codebase. It is driven either from the web UI at the project root
(`../index.html`) or directly via the `*.op.md` files in this folder.

Layout after the reorganisation:

- the whole project lives in `code-utils/coder`, so the analysis root is
reached with `../..` from this folder (`folder: ../../..` in every op file)
- the docops files live in `ops/` (this folder)
- research documents live in `queries/<slug>/`, one folder per document

Each research session is identified by a **slug** (derived from the query
text or an explicit document name) and produces a family of *sibling* files
inside its own folder under the configured working directory (default:
`queries/`):

| File                                  | Produced by                | Description                                     |
|---------------------------------------|----------------------------|-------------------------------------------------|
| `queries/<slug>/query.md`             | UI "Save query" step       | The raw research question/prompt.                |
| `queries/<slug>/research.md`          | `process_query.op.md`      | Research findings gathered from the codebase.    |
| `queries/<slug>/summary.md`           | `summarize_research.op.md` | A concise summary of the research document.      |
| `queries/<slug>/followup.json`        | `research_followup.op.md`  | Structured, actionable follow-up task list.      |
| `queries/<slug>/followup.md`          | `process_followup.op.md`   | Generated file patches implementing follow-ups.  |
| `queries/<slug>/followup.<id>.md`     | UI "Run task" controls     | Per-task doc-op generated from a follow-up item. |

## Pipeline stages

1. **Save query** – the user's research question is written to
`queries/<slug>/query.md`.
2. **Research** (`process_query.op.md`) – transforms
`query.md` → `research.md` using the `SubPlan` task defined in
[`research.task.json`](./research.task.json). This runs in **Adaptive**
cognitive mode with a "Project Manager" strategy, using `FileSearch` and
`FileReview` tasks to explore the codebase rooted at `../..`.
3. **Summary** (`summarize_research.op.md`) – transforms
`research.md` → `summary.md`, producing a short digest of the research
findings.
4. **Follow-up plan** (`research_followup.op.md`) – transforms
`research.md` → `followup.json`. The output must strictly conform to the
`FollowupPlan` schema defined in
[`followup_schema.ts`](./followup_schema.ts), producing a list of
concrete, actionable `FollowupTask` items (primarily `FileModification`
tasks) that downstream tooling can execute without further clarification.
5. **Process follow-ups** (`process_followup.op.md`) – transforms
`followup.json` → `followup.md` using the `SubPlan` task defined in
[`followup.task.json`](./followup.task.json) (Waterfall cognitive mode,
`FileModification` task type). The result is a markdown document
containing diff/file patches implementing the follow-up tasks.

Because the transforms are written against bare file names
(`(.*)query.md -> $1research.md` and friends), each stage keeps its output in
the same document folder as its input.

## Files in this folder

- `process_query.op.md` – Op definition: `query.md` → `research.md`.
- `summarize_research.op.md` – Op definition: `research.md` → `summary.md`.
- `research_followup.op.md` – Op definition: `research.md` → `followup.json`.
- `process_followup.op.md` – Op definition: `followup.json` → `followup.md`.
- `research.task.json` – `SubPlan` task configuration used by the research
stage (Adaptive cognitive mode, `FileSearch`/`FileReview` sub-tasks).
- `followup.task.json` – `SubPlan` task configuration used by the follow-up
execution stage (Waterfall cognitive mode, `FileModification` sub-task).
- `followup_schema.ts` – TypeScript types and a JSON Schema
(`FOLLOWUP_PLAN_JSON_SCHEMA`) describing the shape of `followup.json`
files, used both as documentation and for runtime validation.

The browser UI (`../index.html`) drives the pipeline: it can run the full
pipeline or individual steps, view rendered/raw documents (research, summary,
follow-ups, patches, raw JSON), and manage research history. It works either
inside a hosted docops session or standalone — in direct-file mode the base
path is the directory the page itself is served from (the project root).

## Using the UI

Open `../index.html` (either through a docops session URL or directly as a

file). The working directory (default `queries`) and ops directory (default
`ops`) are fixed via a hidden settings panel — there is no model-selection
UI. The doc-op root and the `docops.status.json` polling location are never
user-configurable; they are always inferred from the page's own URL (see
`inferContextFromLocation()` in `index.html`).

Enter a query, then either **Run All Steps** to execute the full pipeline,
or use the individual step buttons / ▶ run controls to execute a single
stage. Completed documents can be inspected in the **Research**,
**Summary**, **Follow-ups**, **Patches**, and **Raw JSON** tabs; follow-up
cards also expose "Research this" to seed a new query from a follow-up item.

Per-task doc-ops generated from the follow-up plan are written as
`queries/<slug>/followup.<task-id>.md`. Their `specifies`/`related` paths and
`folder` are emitted relative to the analysis root, i.e. `../../..` from a
document folder one level below `queries/`.

## Extending

To add a new pipeline stage, add a new `*.op.md` file with a `transforms`
frontmatter rule matching bare file names inside a document folder (plus
`folder: ../../..` and, if needed, a new task config JSON), then wire it into
`../index.html`'s `OPS` map, `paths()` helper, and the `STAGES` table.