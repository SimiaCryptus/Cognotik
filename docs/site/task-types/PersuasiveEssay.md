# PersuasiveEssay

**Generate compelling, structured persuasive essays with rhetorical arguments, counterarguments, and optional AI-generated illustrations.**

`Category: Writing` · `Non-Destructive` · `Vision Optional (Image Generation)` · `Multi-Phase Agent Pipeline`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "PersuasiveEssay",
  "thesis": "Remote work should be the default option for knowledge workers",
  "related_files": ["research/**/*.md"],
  "target_audience": "business leaders",
  "tone": "analytical",
  "target_word_count": 1500,
  "num_arguments": 3,
  "include_counterarguments": true,
  "use_rhetorical_devices": true,
  "include_evidence": true,
  "use_analogies": true,
  "call_to_action": "strong",
  "revision_passes": 1
}
```

**Rendered Output (UI)**

A `TabbedDisplay` with one tab per generation phase: `Overview`, `Cover Image` (optional), `Research Context`
(if input files/prior context exist), `Outline`, `Outline Visualization` (optional), `Introduction`, one tab per
`Argument N` (each with an optional inline illustration), `Counterarguments` (optional, with image), `Conclusion`,
`Revision`, and a final `Complete Essay` tab rendering the full assembled Markdown essay with word-count stats.

The `Overview` tab renders as a live-updating checklist (Phase 1 → Phase 7) with ✅ markers and word counts appended
as each phase completes. The final chat response is a concise Markdown summary with clickable links to the saved
essay (`.md` / `.html` / `.pdf`) and a full transcript file, gated behind an accept button unless `autoFix` is
enabled.

---

## Documentation

### Configuration Table

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `thesis` | **Required** | `String?` | The thesis statement or position to argue for |
| `related_files` | Optional | `List<String>?` | Specific files or glob patterns used as research input |
| `target_audience` | Optional | `String` (default `"general public"`) | Target audience, e.g. `academics`, `policymakers`, `business leaders` |
| `tone` | Optional | `String` (default `"formal"`) | Essay tone, e.g. `conversational`, `passionate`, `analytical` |
| `target_word_count` | Optional | `Int` (default `1500`) | Target total word count; must be `> 0` |
| `num_arguments` | Optional | `Int` (default `3`) | Number of main arguments; must be between `1` and `10` |
| `include_counterarguments` | Optional | `Boolean` (default `true`) | Whether to include counterarguments and rebuttals |
| `use_rhetorical_devices` | Optional | `Boolean` (default `true`) | Whether to use ethos/pathos/logos rhetorical devices |
| `include_evidence` | Optional | `Boolean` (default `true`) | Whether to include statistics/citations as evidence |
| `use_analogies` | Optional | `Boolean` (default `true`) | Whether to use analogies and concrete examples |
| `call_to_action` | Optional | `String` (default `"strong"`) | Must be one of `strong`, `moderate`, `reflective`, `none` |
| `revision_passes` | Optional | `Int` (default `1`) | Number of editorial revision passes; must be between `0` and `5` |
| `task_description` | Optional | `String?` | Free-text description (auto-generated from thesis if omitted) |
| `task_dependencies` | Optional | `List<String>?` | IDs of prerequisite tasks in the orchestration plan |

**Type Configuration** (`PersuasiveEssayTaskTypeConfig`, plan-wide settings, not per-execution):

| Field Name | Type | Description |
|---|---|---|
| `generate_images` | `Boolean` (default `true`) | Whether to generate illustrative images per argument/section |
| `generate_cover_image` | `Boolean` (default `true`) | Whether to generate a cover image for the essay |

### Dependencies

No hard dependency on other `TaskType`s in the orchestration graph, but this task composes multiple internal agents:

- `ParsedAgent<EssayOutline>` / `ParsedAgent<EssaySection>` — structured outline and section generation
- `ChatAgent` — free-form revision passes
- `ImageProcessingAgent` — cover, outline, argument, and counterargument illustrations (uses `orchestrationConfig.defaultImage`)

It also consumes `getPriorCode(agent.executionState)`, `getInputFileContent(...)`, and manually-resolved
`related_files` content as research context.

### Token Usage

**High** — the pipeline issues a distinct LLM call per phase (outline, introduction, each argument, optional
counterargument section, conclusion, and each revision pass), plus separate image-generation calls. For a 3-argument
essay with counterarguments and one revision pass, expect on the order of 7–9 text completions and up to 5 image
generations per run.

---

## Config & Process

### Type Configuration vs. Runtime Configuration

- **Type Configuration** (`PersuasiveEssayTaskTypeConfig`) — set once at the task-type level: whether image
  generation is enabled at all (`generate_images`, `generate_cover_image`). These act as global feature toggles
  checked via `typeConfig!!.generate_images` throughout execution.
- **Runtime Configuration** (`PersuasiveEssayTaskExecutionConfigData`) — set per invocation: the thesis, audience,
  tone, structural parameters (word count, argument count), and rhetorical feature flags (evidence, analogies,
  rhetorical devices, counterarguments, call-to-action strength, revision passes).

### Lifecycle Walkthrough

**Initialization**
- Opens a transcript file (`transcriptFile()`) and writes a Markdown header with thesis and timestamp.
- Runs `executionConfig.validate()`, checking non-blank `thesis`/`target_audience`/`tone`, `target_word_count > 0`,
  `num_arguments` in `[1,10]`, `revision_passes` in `[0,5]`, and `call_to_action` against a whitelist. On failure,
  writes a `CONFIGURATION ERROR` to the task, logs it, and returns early via `resultFn`.
- Bails out early (without full validation messaging) if `thesis` is null/blank at the top level.

**Execution** (sequential phases inside `task.pool.submit`)
1. **Cover image** (optional, `ImageProcessingAgent`) rendered into its own tab and saved as `00_cover_image.png`.
2. **Overview** tab initialized as a running checklist.
3. **Research context** gathered from `getPriorCode`, `getInputFileContent` (glob-resolved `related_files`), and
   `getContextFiles()` (raw file reads truncated for display).
4. **Outline** generation via `ParsedAgent<EssayOutline>` — hook, background, thesis statement, N arguments,
   counterarguments (if enabled), conclusion strategy. Optional outline visualization image.
5. **Introduction** via `ParsedAgent<EssaySection>`.
6. **Body arguments** — one `ParsedAgent<EssaySection>` call per outlined argument, each seeded with a summary of the
   previous argument for continuity; optional per-argument illustration.
7. **Counterarguments** (if `include_counterarguments` and outline has any) via another `ParsedAgent<EssaySection>`
   call, plus optional illustration.
8. **Conclusion** via `ParsedAgent<EssaySection>`, incorporating the `call_to_action` mode.
9. **Revision** — `revision_passes` repetitions of a `ChatAgent` full-essay rewrite pass, replacing the accumulated
   `resultBuilder` content each time.
10. **Final assembly** — concatenates all sections into a titled Markdown document, appends word-count/target stats,
    and saves it via `task.saveFile("persuasive_essay.md", ...)`.

Each phase writes progress to both the `TabbedDisplay` (live UI) and the transcript `OutputStream`, and appends
completion markers to the `Overview` tab.

**Error Handling**
- Any exception thrown during the core phases (outline through final assembly) is caught in a single surrounding
  `try/catch`: logs the error, appends an "❌ Error Occurred" block to the `Overview` tab, calls `task.error(e)`,
  writes a collapsible `<details>` stack trace to the transcript, and calls `resultFn` with an error report that
  includes any partial essay text accumulated so far in `resultBuilder`.
- Image-generation helpers (`generateCoverImage`, `generateOutlineImage`, `generateArgumentImage`,
  `generateCounterargumentImage`) each wrap their logic in their own `try/catch`, logging failures and writing a
  collapsible error note to the transcript **without aborting the overall essay pipeline** — image generation is
  best-effort and non-fatal.
- The transcript stream is always closed in a `finally` block regardless of success or failure.
- On successful completion, if `orchestrationConfig.autoFix` is `false`, the result is gated behind an
  accept-button footer rather than being passed directly to `resultFn`.

---

## Integration

### Registering in an `OrchestrationConfig`

```kotlin
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.social.PersuasiveEssayTask

val orchestrationConfig = OrchestrationConfig(
    // ... other config ...
    taskSettings = mapOf(
        PersuasiveEssayTask.PersuasiveEssay.name to PersuasiveEssayTask.PersuasiveEssayTaskTypeConfig(
            generate_images = true,
            generate_cover_image = true
        )
    )
)

// Example execution-config entry within a plan:
val essayStep = PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData(
    thesis = "Remote work should be the default option for knowledge workers",
    related_files = listOf("research/**/*.md"),
    target_audience = "business leaders",
    tone = "analytical",
    target_word_count = 1500,
    num_arguments = 3,
    include_counterarguments = true,
    use_rhetorical_devices = true,
    include_evidence = true,
    use_analogies = true,
    call_to_action = "strong",
    revision_passes = 1
)
```

### Prompt Segment (Injected into LLM / Planner)

```
PersuasiveEssay - Generate compelling persuasive essays with structured arguments
** Specify the thesis statement or position to argue
** Optionally provide input files (supports glob patterns) to incorporate as research
** Define target audience and tone
** Set target word count and number of main arguments
** Enable counterarguments and rebuttals for balanced perspective
** Use rhetorical devices (ethos, pathos, logos) for persuasive impact
** Include statistical evidence and citations
** Incorporate analogies and examples for clarity
** Configure call to action strength
** Performs outline creation, argument development, and iterative writing
** Produces complete, well-structured persuasive essay
** Detailed output saved to files with links in summary
```

**Outline-generation prompt (paraphrased from `outlineAgent`)**

```
You are an expert in persuasive writing and rhetoric. Create a detailed outline for a persuasive essay.

Thesis: {thesis}
Target Audience: {target_audience}
Tone: {tone}
Target Word Count: {target_word_count}
Number of Arguments: {num_arguments}

[Input Files / Research Context / Additional Research, if present]

Create an outline with:
1. A compelling hook
2. Background context (100-150 words)
3. Clear thesis statement
4. {num_arguments} main arguments (~N words each)
[Counterarguments with rebuttal strategies, if enabled]
6. Conclusion strategy