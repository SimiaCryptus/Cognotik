---
transforms:
  - ../../../(tasklib/.*)/(.*?)Task.kt -> $2.md
  - ../../../(webui/.*)/(.*?)Task.kt -> $2.md
---

# Task: Task-Type Product Page

You are transforming a **Task** implementation (a `*Task.kt` class) into a public product page following the
"Industrial" design system defined in [`docs/tasks/task_product_page.md`](../../tasks/task_product_page.md). The
output is Markdown that will be rendered into the site's Bento-grid layout — write content, not final HTML, but
structure it so each section maps cleanly onto that layout.

## Goal

Produce Markdown content covering every section of the product page spec:

1. **Header:** Task name, a one-line technical pitch, and badges (e.g. category such as `Side-Effect Safe` /
   `Destructive`, and any model requirements inferred from the code, e.g. `Vision Required`).
2. **Reality Check:** A realistic example **input configuration** (JSON, matching the task's actual execution config
   fields/types) paired with a realistic **output description** (what the user sees in the UI — markdown, diff,
   gallery, etc.), inferred from how the task renders its results.
3. **Documentation Tab:**
    * A configuration table: `Field Name` (with `Required`/`Optional`), `Type`, `Description` — sourced directly from
      the task's execution-config class and its `@Description`/KDoc annotations.
    * Dependencies on other tasks, if evident from code (imports, task-type references, orchestration wiring).
    * A rough Token Usage estimate (`Low`/`Medium`/`High`) based on the task's typical prompt size and expected
      output volume.
4. **Config & Process Tab:**
    * Type Configuration vs. Runtime Configuration, clearly separated.
    * A lifecycle walkthrough: Initialization → Execution → Error Handling, written as short prose/bullets describing
      the task's actual control flow (validation, main logic, rollback/retry behavior).
5. **Integration Tab:**
    * Copy-pasteable Kotlin snippet showing how to register/use this task in an `OrchestrationConfig`.
    * The actual (or a faithful paraphrase of the) prompt segment injected into the LLM, if discoverable in code,
      presented transparently as a fenced code block.

## Style Guide

* **Tone:** "IDE-Native" — dense, concrete, developer-facing. No abstract marketing fluff; every claim should be
  traceable to something in the code.
* **Format:** Valid Markdown using `##`/`###` headings matching the sections above, Markdown tables for the config
  table, and fenced code blocks (`json`, `kotlin`) for all examples.
* **Screenshots/UI mockups:** Since this is text-only output, describe the expected rendered UI in enough detail that
  a designer or later automation step could construct the actual "Reality Check" mockup faithfully.

## Source-to-Output Mapping

| Source Signal (from `*Task.kt`)                                  | Output Section                       |
|----------------------------------------------------------------------|---------------------------------------|
| Class name, KDoc summary                                             | Header title + one-line pitch        |
| Execution config data class + `@Description` annotations             | Reality Check (left) + Config table  |
| Rendered output calls (markdown/HTML emission, file writes)          | Reality Check (right)                |
| Validation / try-catch / rollback logic                              | Error Handling                        |
| References to other Task classes                                     | Dependencies                          |
| Prompt string construction / template literals                       | Integration → Prompt Segment         |

## Constraints

* Never fabricate config fields — every row in the configuration table must correspond to an actual field in the
  task's execution/runtime config classes.
* If token usage cannot be estimated confidently, default to `Medium` rather than omitting the field.
* Follow the exact terminology used in `docs/tasks/task_product_page.md` (e.g. "Reality Check", "Bento Grid") so the
  content integrates seamlessly with the site's templates.