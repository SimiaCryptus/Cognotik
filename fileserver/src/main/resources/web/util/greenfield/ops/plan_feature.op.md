---
  transforms: ../idea.md -> ../plan/feature.json
  folder: ../../..
  related:
    - plan_schema.ts
  ---

  Read the seed idea and turn it into a rigorous feature specification.

  Produce a single JSON document conforming **strictly** to the `FeaturePlan`
  interface in `plan_schema.ts` (see related file):

  - `name` / `slug` — a real product name and a kebab-case slug safe to use as
    a directory and package name.
  - `problem_statement` — one paragraph: the problem, for whom, and why the
    obvious workaround is not good enough.
  - `users` — the distinct actors.
  - `user_stories` — 5–12 stories, each with an `id`, `as_a` / `i_want` /
    `so_that`, and 2–5 *checkable* `acceptance_criteria`. Criteria are the
    contract the generated tests will be written against, so they must be
    observable ("returns 400 with an error body", not "handles errors well").
  - `non_goals` — be aggressive. Anything the idea does not explicitly require
    is a non-goal. This list is what keeps every later stage small.
  - `constraints` — hard limits implied by the idea (offline, single binary,
    no external services, licence, target platform).
  - `open_questions` — decisions a human should make. Do not block on them;
    pick a sensible default in later stages and record it here.

  Output valid, parseable JSON only. No commentary, no markdown fences.