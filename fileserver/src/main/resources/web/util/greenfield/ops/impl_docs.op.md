---
transforms: ../plan/feature.json -> ../tmp/docs.md
task_type: SubPlan
task_config_json: impl.task.json
folder: ../../..
related:
  - ../plan/stack.json
  - ../plan/architecture.json
  - ../plan/phases.json
---

Write the project documentation for the generated code, under
`StackPlan.target_root`.

Produce:

1. `README.md` — what the project is (from `problem_statement`), what it is explicitly **not** (from `non_goals`),
   install/build/test/run commands taken verbatim from the real build files, and a short usage example that actually
   works against the generated API.
2. `docs/architecture.md` — the component map, their responsibilities and dependencies, the data model and the
   boundaries, rendered as prose plus a directory-tree listing.
3. `docs/adr/NNN-*.md` — one short ADR per entry in
   `StackPlan.alternatives_considered` and per `ArchitecturePlan.style`
   decision: context, decision, consequences.

Every command shown must be one the scaffold stage really created. Prefer omitting a section to inventing one.