---
transforms: ../plan/phases.json -> ../tmp/plan_tasks.md
task_type: SubPlan
task_config_json: plan.task.json
folder: ../../..
related:
  - task_schema.ts
  - plan_schema.ts
  - ../plan/feature.json
  - ../plan/stack.json
  - ../plan/architecture.json
---

Fan the phase plan out into one build-task plan per phase.

For **every** phase in `code-utils/greenfield/plan/phases.json`, create the file
`code-utils/greenfield/tasks/<phase-id>.json` containing a single JSON document conforming **strictly** to the
`BuildPlan` interface in
`task_schema.ts`.

For each phase:

- `phase_id` / `phase_title` — copied from the phase.
- `summary` — what this phase builds, in two sentences.
- `tasks` — 3–15 `BuildTask` entries. Each task must:

* have a stable kebab-case `id`, unique within the plan;
* name its `target_files` relative to the analysis root (the `folder:`
  above), with the **primary** file first — that entry becomes the generated doc-op's `specifies:` target;
* list `related_files` for read-only context (interfaces it must satisfy, config it must match);
* carry a `description` detailed enough to execute with no other context:
  what to create, the exact signatures/exports expected, the behaviour, and the error cases;
* reference the architecture via `source_components` and the feature spec via `source_stories`;
* use `depends_on` for intra-plan ordering only (advisory).

- Prefer one file per task. Split a task that would touch more than two files.
- Do **not** emit tasks for test files or documentation here: stages 8 and 9 own those.

The output document (`tmp/plan_tasks.md`) is just the run summary; the real payload is the `tasks/*.json` files.