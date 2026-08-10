# Code Research

`code_research` is a self-contained docops "app" that automates a
research → summarize → follow-up → execute pipeline over a codebase, driven
either from the bundled web UI (`index.html`) or directly via the `*.op.md`
operation files in this folder.

Each research session is identified by a **slug** (derived from the query
text or an explicit document name) and produces a family of files in the
configured working directory (default: `research/`):

| File                        | Produced by                     | Description                                   |
|-----------------------------|----------------------------------|------------------------------------------------|
| `<slug>.query.md`           | UI "Save query" step             | The raw research question/prompt.              |
| `<slug>.research.md`        | `process_query.op.md`            | Research findings gathered from the codebase.  |
| `<slug>.summary.md`         | `summarize_research.op.md`       | A concise summary of the research document.   |
| `<slug>.followup.json`      | `research_followup.op.md`        | Structured, actionable follow-up task list.    |
| `<slug>.followup.md`        | `process_followup.op.md`         | Generated file patches implementing follow-ups.|

## Pipeline stages

1. **Save query** – the user's research question is written to
   `<slug>.query.md`.
2. **Research** (`process_query.op.md`) – transforms
   `<slug>.query.md` → `<slug>.research.md` using the `SubPlan` task defined
   in [`research.task.json`](./research.task.json). This runs in
   **Adaptive** cognitive mode with a "Project Manager" strategy, using
   `FileSearch` and `FileReview` tasks to explore the codebase.
3. **Summary** (`summarize_research.op.md`) – transforms
   `<slug>.research.md` → `<slug>.summary.md`, producing a short digest of
   the research findings.
4. **Follow-up plan** (`research_followup.op.md`) – transforms
   `<slug>.research.md` → `<slug>.followup.json`. The output must strictly
   conform to the `FollowupPlan` schema defined in
   [`followup_schema.ts`](./followup_schema.ts), producing a list of concrete,
   actionable `FollowupTask` items (primarily `FileModification` tasks) that
   downstream tooling can execute without further clarification.
5. **Process follow-ups** (`process_followup.op.md`) – transforms
   `<slug>.followup.json` → `<slug>.followup.md` using the `SubPlan` task
   defined in [`followup.task.json`](./followup.task.json) (Waterfall
   cognitive mode, `FileModification` task type). The result is a markdown
   document containing diff/file patches implementing the follow-up tasks.

## Files in this folder

- `index.html` – Browser-based UI for driving the pipeline. Supports running
  the full pipeline or individual steps, viewing rendered/raw documents
  (research, summary, follow-ups, patches, raw JSON), managing research
  history, and configuring model selections. Works either inside a hosted
  docops session or standalone (paths are inferred from the page's own
  location).
- `process_query.op.md` – Op definition: `*.query.md` → `*.research.md`.
- `summarize_research.op.md` – Op definition: `*.research.md` → `*.summary.md`.
- `research_followup.op.md` – Op definition: `*.research.md` → `*.followup.json`.
- `process_followup.op.md` – Op definition: `*.followup.json` → `*.followup.md`.
- `research.task.json` – `SubPlan` task configuration used by the research
  stage (Adaptive cognitive mode, `FileSearch`/`FileReview` sub-tasks).
- `followup.task.json` – `SubPlan` task configuration used by the follow-up
  execution stage (Waterfall cognitive mode, `FileModification` sub-task).
- `followup_schema.ts` – TypeScript types and a JSON Schema
  (`FOLLOWUP_PLAN_JSON_SCHEMA`) describing the shape of `*.followup.json`
  files, used both as documentation and for runtime validation.

## Using the UI

Open `index.html` (either through a docops session URL or directly as a
file). Configure:

- **Working directory** – where `<slug>.*` files are read/written
  (default `research`).
- **Ops directory** – where the `*.op.md` files above live (default
  `code_research`).
- **Models** – optional smart/fast/image model overrides, persisted in
  `localStorage`.

Enter a query, then either **Run All Steps** to execute the full pipeline,
or use the individual step buttons / ▶ run controls to execute a single
stage. Completed documents can be inspected in the **Research**,
**Summary**, **Follow-ups**, **Patches**, and **Raw JSON** tabs; follow-up
cards also expose "Research this" to seed a new query from a follow-up item.

## Extending

To add a new pipeline stage, add a new `*.op.md` file with a `transforms`
frontmatter rule (and, if needed, a new task config JSON), then wire it into
`index.html`'s `OPS` map, `paths()` helper, and the `STAGES` table.