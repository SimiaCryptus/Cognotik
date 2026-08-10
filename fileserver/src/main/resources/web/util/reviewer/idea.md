# Code Review & Update

A pipeline for performing structured, schema-driven code review across a
codebase (or a subset of it), followed by automated execution of the
resulting follow-up actions. Conceptually mirrors the `coder` pipeline
(query → research → follow-up → execute), but replaces free-form research
with a structured analysis pass driven by a fixed analysis schema (defined
once at implementation time, similar to `followup_schema.ts` in the
`coder` pipeline — not authored per-session by the user).

## Pipeline stages

1. **Focus query** – the user provides a focus query describing what aspect
   of the codebase should be reviewed (e.g. "error handling consistency",
   "unused exports", "API surface for module X", security concerns, etc.).
2. **Summarize files** – a docops op (analogous to `process_query.op.md`)
    operates on each relevant file and produces a schema'd per-file analysis
    document under `queries/<id>/**/*.json`, capturing findings against the
    focus query (e.g. findings, severity, category, suggested fix,
    confidence, references, etc.) per a fixed, code-defined schema — the
    schema itself is not derived from the focus query, it is written once
    during implementation (cf. `followup_schema.ts`).
3. **Summarize packages** – a separate docops op rolls up the per-file
    analyses within a package/folder into a package-level schema'd summary,
    also written to `queries/<id>/**/*.json` alongside the per-file outputs.
    Neither op walks the filesystem itself — each is scoped to a single file
    or package via docops' normal transform/glob mechanics, mirroring how
    `process_query.op.md` operates on a single `query.md` at a time.
4. **Execute follow-up actions** – convert the structured findings (file-
    and package-level) into concrete follow-up tasks (e.g.
    `FileModification` operations) and execute them, similar to the `coder`
    pipeline's follow-up execution stage.

## Open questions / next steps

- Reuse as much of the `coder` pipeline's plumbing as possible (op files,
- Should there be a small set of reusable built-in schemas (e.g. "security
   review", "style consistency", "dead code") to choose between, or a single
   fixed schema covering all review types?
- Since both file- and package-level ops write into
   `queries/<id>/**/*.json`, should package summaries be docops-transformed
   from their constituent file summaries (expressing the roll-up as a
   dependency), and how is that dependency expressed via docops
   transform/glob patterns?
- Should file- and package-level analyses share a single JSON schema (with
   an optional "unit type" field) or use two distinct schemas?
  task configs, follow-up schema/execution) rather than reinventing it.
## Pipeline files
The pipeline described above is implemented under `code-utils/reviewer/ops/`,
mirroring the `coder` pipeline's structure:
- `analysis_schema.ts` – fixed schema for `FileAnalysisPlan` (per-file
  findings) and `PackageAnalysisPlan` (package-level rollups), analogous to
  `coder/ops/followup_schema.ts`.
- `process_focus_query.op.md` + `analysis.task.json` – transforms a
  user-authored `focus.md` into `analysis.json` via a `SubPlan`
  (`FileSearch` + `FileReview`), analogous to `process_query.op.md` /
  `research.task.json`.
- `summarize_packages.op.md` – transforms `analysis.json` into
  `packages.json`, rolling per-file findings up to the package level.
- `summarize_analysis.op.md` – transforms `packages.json` into a
  human-readable `summary.md`, analogous to `summarize_research.op.md`.
- `plan_followup_single.op.md` – transforms `packages.json` into `followup.json`,
  reusing the `FollowupPlan` schema from `coder/ops/followup_schema.ts`.
- `process_followup.op.md` + `followup.task.json` – executes the resulting
  follow-up tasks, identical in structure to the `coder` pipeline's
  equivalents.