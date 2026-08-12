# Code Research

*Research → summarize → follow-up → execute pipeline over a codebase.*

## Overview

Code Research automates the process of investigating a codebase to answer a
question, distilling the findings into a summary, turning that summary into a
concrete list of follow-up tasks, and then generating file patches that
implement those tasks. It's built as a self-contained "app" driven by docops
operation files, usable either through a browser UI or directly via the
underlying `*.op.md` files.

## Pipeline

1. **Save query** — the research question is written to `query.md`.
2. **Research** — `process_query.op.md` explores the codebase and produces
   `research.md`.
3. **Summary** — `summarize_research.op.md` condenses `research.md` into
   `summary.md`.
4. **Follow-up plan** — `research_followup.op.md` turns `research.md` into a
   structured `followup.json` task list.
5. **Process follow-ups** — `process_followup.op.md` turns `followup.json`
   into `followup.md`, a document containing file patches implementing the
   follow-up tasks.

## Getting Started

1. Open `index.html` (via a docops session or directly as a file).
2. Enter a research question or topic in the query field.
3. Click **Run All Steps** to execute the full pipeline, or use the
   individual step buttons to run a single stage at a time.
4. Inspect results in the **Research**, **Summary**, **Follow-ups**,
   **Patches**, and **Raw JSON** tabs.
5. From a follow-up card, use "Research this" to seed a new query based on
   that item.
6. Re-run any stage as the underlying documents evolve.

## Artifacts

| Path                                | What it holds                                    |
|--------------------------------------|---------------------------------------------------|
| `queries/<slug>/query.md`           | The raw research question/prompt.                  |
| `queries/<slug>/research.md`        | Research findings gathered from the codebase.       |
| `queries/<slug>/summary.md`         | A concise summary of the research document.         |
| `queries/<slug>/followup.json`      | Structured, actionable follow-up task list.          |
| `queries/<slug>/followup.md`        | Generated file patches implementing follow-ups.      |
| `queries/<slug>/followup.<id>.md`   | Per-task doc-op generated from a follow-up item.     |