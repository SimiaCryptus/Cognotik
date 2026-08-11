---
  transforms: ../plan/architecture.json -> ../plan/phases.json
  folder: ../../..
  related:
    - plan_schema.ts
    - ../plan/feature.json
    - ../plan/stack.json
    - ../idea.md
  ---

  Break the work into an ordered sequence of delivery phases.

  Produce a single JSON document conforming **strictly** to the `PhasePlan`
  interface in `plan_schema.ts` (see related file).

  Rules:

  - **Phase 0 is always the walking skeleton**: the build runs, one test
    passes, and one end-to-end path works — however trivially. Give it the id
    `p0-skeleton`. Nothing else can be verified until this exists.
  - 3–7 phases total. Each phase must be independently demonstrable; a phase
    that produces no runnable behaviour should be merged into its neighbour.
  - `id` — filename-safe, ordered prefix (`p0-…`, `p1-…`). It becomes the name
    of `tasks/<phase-id>.json`.
  - `deliverables` — concrete artifacts (files, commands, endpoints).
  - `exit_criteria` — checkable statements, traceable to the
    `acceptance_criteria` in `plan/feature.json`.
  - `components` — the `Component.id`s from `plan/architecture.json` touched by
    the phase. Every component must appear in at least one phase.
  - `depends_on` — advisory ordering hints only.
  - Non-goals from the feature spec must not reappear as phases.

  Output valid, parseable JSON only. No commentary, no markdown fences.