---
transforms:
  - (.*)Task.kt -> $1Task.md
---

# Task Type Documenter: Source-to-Markdown Generation Standard

## 1. Purpose

This document defines the standard for generating per-task Markdown documentation from the Kotlin source files of
each `TaskType` implementation. The `transforms` header above instructs the documentation pipeline to produce a
sibling `.md` file for every `*Task.kt` source file (e.g., `FileModificationTask.kt` → `FileModificationTask.md`).

These generated Markdown files serve as the **canonical data source** for:

* The product pages defined in `task_product_page.md` (which further transform per-task Markdown into HTML).
* Internal reference documentation for developers and reviewers.
* Context injection into the Cognotik Planner when users browse available task types.

## 2. Scope

The documenter runs against every file matching `(.*)Task.kt`. 
Each generated `.md` file describes exactly one `TaskType` and its associated configuration classes.

## 3. Required Sections

Every generated per-task Markdown file **must** contain the following sections, in order. Sections are populated by
extracting structured information from the Kotlin source (class declarations, `@Description` annotations,
`promptSegment()` bodies, and `TaskType` registration metadata in `TaskType.kt`).

### 3.1 Front Matter

YAML front matter identifying the task and its category:

```yaml
---
task_name: FileModificationTask
category: File Operations
documents: webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/FileModificationTask.kt
---
```

### 3.2 Title and Summary

* **H1 title:** The task class name (e.g., `# FileModificationTask`).
* **One-line pitch:** Extracted from the `TaskType.description` field in `TaskType.kt`.
* **Badges:** Category, side-effect classification (`Side-Effect Safe` vs. `Destructive`), and model requirements.

### 3.3 Overview

A short narrative paragraph explaining what the task does, derived from:

* The `TaskType.description` string.
* The `tooltipHtml` (stripped of HTML tags).
* The opening lines of `promptSegment()`.

### 3.4 Execution Configuration

A dense table listing every field in the task's `TaskExecutionConfig` subclass:

| Column           | Source                                                      |
|:-----------------|:------------------------------------------------------------|
| **Field Name**   | Kotlin property name (preserve `snake_case` as written)     |
| **Type**         | Kotlin type signature (including nullability)               |
| **Default**      | Default value from the property declaration                 |
| **Required**     | `Yes` if no default / non-nullable; `No` otherwise          |
| **Description**  | Text from the `@Description` annotation                     |

### 3.5 Type Configuration

A parallel table for the task's `TaskTypeConfig` subclass (global/static settings). If the task uses the base
`TaskTypeConfig` directly (no custom subclass), state: *"This task uses the default `TaskTypeConfig` and has no
type-level settings."*

### 3.6 Prompt Segment

The verbatim string returned by `promptSegment()`, rendered in a fenced code block. This is the exact text the
Planner sees when deciding whether to select this task.

```text
[contents of promptSegment() here]
```

### 3.7 Example Configuration

A JSON example showing how the Planner would instantiate this task, constructed by:

* Setting `task_type` to the task's registered name.
* Populating each field with a plausible example value (inferred from the `@Description` text).
* Including a representative `task_description` and empty `task_dependencies` array.

```json
{
  "task_type": "FileModificationTask",
  "task_description": "Add a calculateTax method to Service.kt",
  "target_file": "src/Service.kt",
  "files": ["src/Service.kt"],
  "task_dependencies": []
}
```

### 3.8 Output Behavior

A subsection describing:

* Whether the task produces a single main output file (via `transcriptFile()`).
* Whether it creates auxiliary artifacts (themed directories, companion data files).
* Whether it has side effects (file writes, shell commands, network calls).
* Whether it requires user approval in interactive mode (`autoFix == false`).

### 3.9 Dependencies and Integration

* **Upstream:** What kinds of task outputs does this task typically consume via `getPriorCode()`?
* **Downstream:** What kinds of task outputs does this task produce that other tasks consume?
* **Registration:** The exact entry in `TaskType.kt` that registers this task.

### 3.10 Source Location

A link to the Kotlin source file, relative to the repository root:

```
[Source](../webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/FileModificationTask.kt)
```

## 4. Extraction Rules

The documenter is expected to follow these rules when extracting data from source:

1. **`@Description` is authoritative.** If a field lacks `@Description`, emit a warning in the generated file under
   a `## Warnings` section. Per `task_type_best_practices.md` (check R1), this is a compliance failure.
2. **Preserve field names verbatim.** Do not convert `snake_case` to `camelCase` or vice versa. The JSON field name
   the Planner sees must match exactly.
3. **Defaults must be literal.** Extract default values from the Kotlin property initializer as written. Do not
   evaluate expressions; render them as source text.
4. **`promptSegment()` is verbatim.** Include the exact output (or the string-building logic rendered as its result)
   without reformatting or summarizing. The Planner sees this text literally.
5. **Follow the `files` convention.** When generating the example configuration, populate `files` per the "Single
   Main File" rule in `task_type_best_practices.md` §4.3.1. If the task has a natural single-file output, declare
   it there.
6. **Nested config types.** If an execution config field is itself a structured type (not a primitive), recursively
   document the nested type in a subsection under the field's row.

## 5. Cross-References

Per-task Markdown files generated by this documenter are consumed by:

* **`task_product_page.md`** — transforms each `*Task.md` into the bento-grid HTML product page rendered at
  `site/cognotik.com/{TaskName}.html`. The "Reality Check" Left Panel uses §3.7, the Configuration Table uses §3.4,
  and the Integration Tab uses §3.6.
* **`task_type_best_practices.md`** — the review rubric (checks R1–R16) is applied against the source, and
  violations surface in the generated `## Warnings` section.
* **`taskplanning.md`** — the high-level user documentation links back to individual task pages for detailed
  reference.

## 6. Failure Modes

If the documenter cannot extract a required section, it must:

* Emit the section header with a placeholder body (`*Not available — see source.*`).
* Add an entry to a top-level `## Warnings` section describing what was missing and why.
* Never silently omit a required section; downstream consumers (the product page generator) depend on stable
  section anchors.

## 7. Example Output Skeleton

Below is a minimal skeleton of what a generated `FileModificationTask.md` should look like:

```md
---
task_name: FileModificationTask
category: File Operations
documents: webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/FileModificationTask.kt
---

# FileModificationTask

> Applies patch-based edits to source code with syntax validation and auto-rollback.

**Category:** File Operations &nbsp;|&nbsp; **Side Effects:** Destructive &nbsp;|&nbsp; **Model:** GPT-4 Preferred

## Overview
...

## Execution Configuration

| Field | Type | Default | Required | Description |
|:------|:-----|:--------|:---------|:------------|
| `target_file` | `String?` | `null` | Yes | The relative path of the file to modify. |
...

## Type Configuration
...

## Prompt Segment
```text
FileModificationTask - Modify existing files...
```

## Example Configuration
```json
{ "task_type": "FileModificationTask", ... }
```

## Output Behavior
...

## Dependencies and Integration
...

## Source Location
[Source](../webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/FileModificationTask.kt)
```