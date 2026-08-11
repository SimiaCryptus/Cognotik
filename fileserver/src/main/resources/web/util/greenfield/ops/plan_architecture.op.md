---
transforms: ../plan/stack.json -> ../plan/architecture.json
folder: ../../..
related:
  - plan_schema.ts
  - ../plan/feature.json
  - ../idea.md
---

Design the architecture for the chosen stack and feature set.

Produce a single JSON document conforming **strictly** to the
`ArchitecturePlan` interface in `plan_schema.ts` (see related file).

Guidance:

- `style` — pick one (layered, hexagonal, pipeline, mvc, ...) and justify it in `style_rationale` against the
  constraints, not against fashion.
- `components` — 4–12 components. Each needs a kebab-case `id`, a *single*
  `responsibility` sentence, `depends_on` ids, a `public_interface` sketch (key types/functions/routes) and the `files`
  expected to implement it. Component ids are referenced later by `BuildTask.source_components`, so they must be stable.
- `depends_on` must be acyclic. If two components need each other, extract a third.
- `data_model` — the entities that cross component boundaries, with fields.
- `boundaries` — every place the system touches the outside world (io, persistence, network, process, ui) and which
  component owns it.
- `directory_layout` — concrete paths relative to `StackPlan.target_root`, idiomatic for the chosen language and build
  tool, each with a `purpose`. This is the tree the scaffold and code stages will actually create.
- `cross_cutting` — logging, configuration, error handling, validation.
- `risks` — what is most likely to go wrong, with a mitigation.

Output valid, parseable JSON only. No commentary, no markdown fences.