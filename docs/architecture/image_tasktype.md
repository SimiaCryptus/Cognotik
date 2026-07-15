# Illustration Spec: TaskType Componentization Architecture

## Topic
Illustrate the architecture supporting the componentization of a Cognotik
Task Type — showing how a self-describing TaskType is defined, advertised to
the Planner, instantiated, executed, and how it reports to its four audiences.

## Format
- Rendered as a component/flow diagram with three horizontal bands:
(1) Definition, (2) Selection & Instantiation, (3) Execution & Reporting.
- Aspect ratio: 16:9 (wide, to show the horizontal pipeline).
- Color-coded per Cognotik palette (planning = #F5A623, agent = #F5A623,
platform = #50E3C2, cognitive = #7ED321).

## Layout & Content

### Band 1 — TaskType Definition (the "component")
Central box labeled **`TaskType`** (fill: #F5A623) containing sub-parts:
- `@Description`-annotated `Config` class
- `promptSegment()`  → capability advertisement
- `run()`            → execution logic
- `resultFn()`       → concise summary output
Side connector: `TypeDescriber` node (converts Config class → JSON/YAML/TS schema).
Annotate: "Self-describing: advertises its own schema & capabilities."

### Band 2 — Selection & Instantiation
Flow (left → right):
- `CognitiveMode` (fill: #7ED321)
→ provides context: Goal + Task Descriptions + History
- `LLM (Planner)` node
→ returns Plan (JSON: TaskType + Config)
- `Orchestrator`
→ validates plan against schema
→ `Instantiate(TaskExecutionConfig)`
Annotate the schema-driven edge: "Planner reads generated schema to configure
tasks it has never seen before."

### Band 3 — Execution & The Four Audiences
- Instantiated `Task Instance` box with `run()` executing.
- From the running task, four labeled output arrows fan out (the "Four Audiences"):
1. **User** → channel `task.ui` → HTML (Markdown) — real-time feedback
 (target: #7ED321)
2. **Auditor** → channel `task.transcript()` → Markdown + `<details>` — audit trail
 (target: #F5A623)
3. **Developer** → SLF4J log → single-line text — system health
 (target: #BD10E0)
4. **LLM** → `resultFn()` / context → concise Markdown — inter-agent context
 (target: #50E3C2)
- A return arrow from the Task back to `CognitiveMode` labeled
`resultFn("Summary of work done")`, feeding the next cognitive cycle.

## Cross-Cutting Elements
- `DynamicEnum<TaskType>` registry box on the side, with an arrow into Band 1,
annotated: "New task types registered at runtime (no core changes)."
- Human-in-the-loop gate icon on `run()` output, annotated:
"Side effects guarded by approval unless auto-applied."

## Emphasis / Callouts
- Central theme label at top: "A TaskType is an atomic, self-describing,
runtime-registerable unit of work."
- Highlight the multiplicative scaling note: "TaskType × Provider × Model ×
Processing = thousands of configurations."

## Reference
Corresponds to Section 5 ("The Cognitive Planning Layer" / Planning Cycle),
Section 8 ("IO Discipline: The Four Audiences"), and Section 9 ("Extensibility
Model") of architecture_overview.md, and diagrams.md Sections 4, 9, and 12.