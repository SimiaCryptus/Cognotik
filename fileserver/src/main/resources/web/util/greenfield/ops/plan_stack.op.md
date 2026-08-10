---
  transforms: ../plan/feature.json -> ../plan/stack.json
  folder: ../../..
  related:
    - plan_schema.ts
    - ../idea.md
  ---

  Choose the technology stack for the feature specification.

  Produce a single JSON document conforming **strictly** to the `StackPlan`
  interface in `plan_schema.ts` (see related file).

  Rules:

  - **One stack.** No monorepos, no polyglot splits. If the feature seems to
    need two languages, pick one and record the other under
    `alternatives_considered`.
  - **Boring wins.** Prefer the smallest toolchain that satisfies the
    `constraints` and `non_goals` in `plan/feature.json`.
  - **Every choice carries a rationale.** `libraries[]` entries must state both
    `purpose` (what it is used for here) and `rationale` (why this one). A
    re-plan should produce a readable diff.
  - Fill in `test_framework`, `lint_format`, `ci` and `packaging`. These drive
    the scaffold stage; leaving them empty means the project cannot be verified.
  - `target_root` — where generated source is written, relative to the analysis
    root (`folder:` above). Default to `"."` unless the feature clearly wants a
    subdirectory, in which case use the feature `slug`.

  Output valid, parseable JSON only. No commentary, no markdown fences.