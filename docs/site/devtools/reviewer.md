# 🔍 Reviewer

*Schema-driven code review pipeline that turns AI findings into runnable follow-up tasks*

`Review` · `stable`

## Overview

Reviewer is a single-page, dependency-light web UI that drives a schema-driven code-review
pipeline built on Cognotik doc-ops. Instead of asking a model for free-form prose, every stage
emits JSON that conforms to a fixed schema, which makes the output renderable, diffable, and
executable: findings become follow-up tasks, and follow-up tasks become generated doc-op files
that patch the code. It's for anyone who wants a structured, repeatable review of a focused set
of files or packages — and a way to act on what the review finds without hand-writing patches.

## Pipeline

1. Focus query → `focus.md`
2. Analyze files → `.review/**.json`
3. Summarize packages → `.review/<pkg>.json`
4. Plan follow-ups → `.tasks/**.json` or `tasks.json`
5. Execute tasks → generated doc-op files that patch the code

## Getting Started

1. Open the Reviewer page (served from the `reviewer/` folder) and type what the review should
   focus on — e.g. "error-handling consistency" or "security of the auth flow" — then press
   **Save Focus**.
2. List the files you want reviewed in the **Files to review** box, or click **Run All Steps** to
   run the whole pipeline in one go.
3. Watch each stage's live progress; per-file and per-package reviews appear in the **Review
   Documents** sidebar as they finish.
4. Click any review document to render its findings as severity-coded cards, or toggle **View
   raw** to see the underlying JSON.
5. Open the **Tasks** tab to see the generated follow-up plan, and use **▶ Run task** (or **▶ Run
   ready tasks**) to execute individual tasks — nothing modifies your code until you do this.
6. Use **Mark done** / **Reset status** to track progress; completed states persist across
   reloads.

## Requirements

- A server exposing Cognotik's doc-op endpoints (for running review and follow-up stages)
- A server exposing Cognotik's file-IO endpoints (for reading/writing/listing files)
- A local copy of the `marked` markdown library for rendering summaries

## Local State

Your task run statuses and the current target file list stay in the browser's `localStorage`
between sessions, so progress isn't lost on reload. Reviews and plans themselves are always read
fresh from disk.