# Brainstorming

**Generate and independently analyze multiple solution options, then converge on a ranked recommendation.**

`Side-Effect Safe` `Reasoning` `No Vision Required` `Multi-Pass (Generate → Analyze × N → Summarize)`

---

## Reality Check

**Input (Execution Config)**

```json
{
  "task_type": "Brainstorming",
  "problem_statement": "How should we reduce p99 latency on the checkout API without a full rewrite?",
  "related_files": ["src/main/kotlin/checkout/**/*.kt"],
  "target_option_count": 6,
  "categories": ["Caching", "Infrastructure", "Algorithmic"],
  "constraints": ["No new infra spend this quarter", "Must ship within 2 sprints"],
  "include_creative_options": true,
  "analysis_depth": "moderate",
  "task_description": "Brainstorm latency mitigation strategies for checkout",
  "task_dependencies": []
}
```

**Output (rendered in UI)**

A tabbed session view (`TabbedDisplay`) with one tab per stage:

- **Overview** — problem statement, config summary, live progress line (`✅ Generated 6 options` → `🔄 Analyzing next option...` → `✅ Brainstorming Complete`) with the winning option and time-to-completion.
- **Input Files** — rendered code blocks of any `related_files` matched via glob.
- **Context** — any prior-task output injected as context (truncated for display).
- **Generated Options** — numbered list of options with title, category, and description.
- **Option N Analysis** (one tab per option) — Pros / Cons / Feasibility / Impact / Risks / Requirements, each rendered as markdown headers with emoji markers (✅ ❌ 📊 💥 ⚠️ 📋).
- **Summary & Recommendations** — 🏆 top pick, selection reasoning, overview, and next steps.

A downloadable **detailed results file** (`<task>_results.md`) linked from the final message, plus a full session transcript file (`*.details.md`) containing every option/analysis in raw markdown. The task's final chat message is a concise digest: winner, reasoning, summary, and a link to the full results file.

---

## Documentation

### Configuration

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `problem_statement` | **Required** | `String?` | The problem or question to brainstorm solutions for. |
| `related_files` | Optional | `List<String>?` | Specific files or glob patterns (e.g. `**/*.kt`) to use as input context. |
| `target_option_count` | Optional (default `7`) | `Int` | Number of options to generate; coerced into range `3–20` during validation. |
| `categories` | Optional | `List<String>?` | Categories/domains to explore (e.g. "Caching", "UX"). |
| `constraints` | Optional | `List<String>?` | Constraints or requirements the options must respect. |
| `include_creative_options` | Optional (default `true`) | `Boolean` | Whether to encourage unconventional/creative options (raises generation temperature to 0.8 vs 0.6). |
| `analysis_depth` | Optional (default `"moderate"`) | `String` | `"brief"`, `"moderate"`, or `"detailed"` — controls how many items are requested per analysis category. |
| `task_description` | Optional | `String?` | Inherited from `TaskExecutionConfig`; free-text task label. |
| `task_dependencies` | Optional | `List<String>?` | Inherited from `TaskExecutionConfig`; IDs of tasks this one depends on. |
| `state` | Optional (default `Pending`) | `TaskState?` | Inherited task lifecycle state. |

Validation: fails if `problem_statement` is null/blank; `target_option_count` is silently clamped to `[3, 20]`.

### Dependencies

No hard compile-time dependency on other `TaskType`s. It integrates generically via `TaskOrchestrator.executionState` to pull **prior task context** (`getPriorCode`) and can read files produced by earlier file-writing tasks via `related_files` globs. It uses `ParsedAgent` (structured-output agent wrapper) for all three LLM calls, and `PaginatedDocumentReader`/`FileSelectionUtils` for non-text input file ingestion.

### Token Usage

**Medium–High.** Cost scales with `target_option_count`: one generation call, then **one LLM call per option** for analysis (each with a moderate/detailed prompt and structured JSON output), plus one final summary call over all options+analyses. With the default of 7 options this is 9 total model calls; at `analysis_depth = "detailed"` and 20 options this becomes the largest-cost configuration (22 calls).

---

## Config & Process

### Type Configuration
- `TaskType.name = "Brainstorming"`, `category = "Reasoning"`
- `taskClass = BrainstormingTask::class.java`
- `executionConfigClass = BrainstormingTaskExecutionConfigData::class.java`
- `taskSettingsClass = TaskTypeConfig::class.java` (no task-specific settings beyond the shared defaults)

### Runtime Configuration
All fields on `BrainstormingTaskExecutionConfigData` (see table above) plus implicit runtime wiring: `defaultSmart`/`defaultFast` model clients (via `getChildClient(task)`), and the session `SessionTask`/`TabbedDisplay` UI context supplied by the orchestrator at execution time.

### Lifecycle

**Initialization**
- Validates `problem_statement` is non-blank; short-circuits with a `CONFIGURATION ERROR` result if missing.
- Clamps `target_option_count` into `[3, 20]`.
- Opens two output streams: a detailed transcript (`*.details.md`) and a summary transcript (matching `transcriptFile()`), both wrapped in `<div class="tab-content">` markers for UI tab rendering.
- Resolves `related_files` globs against the project root (respecting `FileSelectionUtils.isIgnored`), reading text files directly and non-text files (docs) via `PaginatedDocumentReader`.
- Pulls prior-task context via `getPriorCode(agent.executionState)`.

**Execution**
1. **Generate** — builds a brainstorm prompt (problem, target count, categories, constraints, creative flag, prior context, input files) and calls a `ParsedAgent<BrainstormResult>` at temperature 0.8 (creative) or 0.6.
2. **Analyze** — for each generated option, builds an analysis prompt (depth-aware: brief/moderate/detailed) and calls a `ParsedAgent<OptionAnalysis>` at temperature 0.3, updating the UI tab and overview progress line after each.
3. **Summarize** — builds a summary prompt referencing all options + condensed analyses, calls `ParsedAgent<BrainstormingSummary>` at temperature 0.4 to pick `top_option_index`, reasoning, overview, and next steps.
4. Writes a full `detailed_results.md` file via `task.createFile(...)`, links it in the final chat output, and closes out both transcript streams.

**Error Handling**
- The entire generate/analyze/summarize pipeline runs inside a single `try/catch`.
- On exception: logs with elapsed duration, appends an "❌ Error Occurred" section (message, exception type, collapsible stack trace) to the transcript, calls `task.error(e)`, and completes the task with a `"Brainstorming failed: ..."` message rather than throwing further.
- `finally` block always flushes/closes the transcript output stream, logging a warning (not failing the task) if that close fails.
- No retry/rollback logic — failures are terminal for the task instance but reported gracefully.

---

## Integration

### Registering in an `OrchestrationConfig`

```kotlin
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask
import com.simiacryptus.cognotik.plan.OrchestrationConfig

val orchestrationConfig = OrchestrationConfig(
    // ... other task types ...
    taskTypes = listOf(
        BrainstormingTask.Brainstorming,
        // other TaskType entries
    )
)

// Example execution config for the planner to emit:
val config = BrainstormingTask.BrainstormingTaskExecutionConfigData(
    problem_statement = "How should we reduce p99 latency on the checkout API?",
    related_files = listOf("src/main/kotlin/checkout/**/*.kt"),
    target_option_count = 6,
    categories = listOf("Caching", "Infrastructure", "Algorithmic"),
    constraints = listOf("No new infra spend this quarter"),
    include_creative_options = true,
    analysis_depth = "moderate"
)
```

### Prompt Segment (planner-facing task description)

This is the text injected into the planning LLM's tool/task catalog so it knows when and how to select this task:

```text
Brainstorming - Generate and analyze multiple solution options
  ** Specify the problem or question to brainstorm solutions for
  ** Configure target number of options (default: 7)
  ** Optionally specify categories or domains to explore
  ** Define constraints or requirements
  ** Enable/disable creative/unconventional options
  ** Set analysis depth (brief/moderate/detailed)
  ** Generates diverse options, analyzes each independently
  ** Provides comparative summary with recommendations
  ** Useful for:
     - Solution exploration
     - Decision making
     - Strategic planning
     - Problem solving
```

### Core Generation Prompt (paraphrased from `buildBrainstormPrompt`)

```text
You are a creative problem solver and brainstorming expert...

## Problem Statement:
{problem_statement}

## Target:
Generate exactly {target_option_count} distinct options.

## Categories/Domains to Consider:
{categories}

## Constraints to Consider:
- {constraint_1}
- ...

## Brainstorming Guidelines:
1. Diversity — span different approaches and perspectives
2. Clarity — each option clearly described and actionable
3. Relevance — all options must address the core problem
4. Creativity (if enabled) / Practicality (if disabled)
5. Categorization — assign each option to a relevant category

## Output Format:
JSON object with an "options" array: { title, description, category }
```