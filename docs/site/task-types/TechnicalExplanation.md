# TechnicalExplanation

  Turns a complex technical topic into an audience-calibrated, multi-section explanation — outline, sections,
  analogies, code examples, comparisons, and optional revision passes — assembled into one markdown document.

  `Side-Effect Safe` · `Writing` · `Multi-Phase Pipeline` · `No File Mutation`

  ---

  ## Reality Check

  **Input configuration**

  ```json
  {
    "task_type": "TechnicalExplanation",
    "topic": "How does a Bloom filter work?",
    "target_audience": "software_engineer",
    "level_of_detail": "detailed_walkthrough",
    "include_code_examples": true,
    "explanation_format": "markdown",
    "use_analogies": true,
    "include_visual_descriptions": true,
    "define_terminology": true,
    "include_examples": true,
    "include_comparisons": true,
    "related_files": ["docs/data-structures/**/*.md"],
    "code_language": "kotlin",
    "revision_passes": 1
  }
  ```

  **Rendered output**

  The task builds a tabbed UI (`TabbedDisplay`) with one tab per phase:

  - **Overview** — running progress log ("Phase 1: Analysis & Outline" → "Phase 5: Final Assembly") with a live
    checklist and final statistics block (word count, section count, code example count, analogies used, terms
    defined, revision passes, elapsed time).
  - **Outline** — rendered markdown outline: title, overview, numbered key concepts (importance, complexity,
    subtopics, estimated paragraphs), terminology list, analogy mappings, code example plan, visual aid descriptions.
  - **Section N** (one tab per concept) — full markdown section: intro, body text, fenced code blocks per language
    with explanation + bullet highlights, and a "Key Takeaways" bullet list.
  - **Comparisons** (if `include_comparisons`) — freeform markdown contrasting the topic with 2–3 related concepts.
  - **Revision** (if `revision_passes > 0`) — one completion marker per pass; underlying content is fully rewritten
    each pass but only the pass count is displayed in the UI.
  - **Complete Explanation** — the final assembled markdown document (title, overview, terminology, all sections,
    summary bullet list of takeaways), also streamed to a downloadable transcript file (`transcriptFile()`).

  ---

  ## Documentation

  ### Configuration

  | Field Name                    | Required/Optional | Type           | Description |
  |--------------------------------|--------------------|----------------|--------------|
  | `topic`                        | Required           | `String?`      | The complex technical subject to explain. |
  | `target_audience`              | Optional            | `String`       | One of `layperson`, `beginner`, `intermediate`, `expert`, `manager`, `software_engineer`, `data_scientist`, `student`. Defaults to `intermediate`; invalid values are coerced back to `intermediate`. |
  | `level_of_detail`               | Optional            | `String`       | One of `high_level_overview`, `moderate_detail`, `detailed_walkthrough`, `comprehensive`. Defaults to `moderate_detail`; invalid values coerced. |
  | `include_code_examples`        | Optional            | `Boolean`      | Whether to include code examples and snippets. Default `true`. |
  | `explanation_format`           | Optional            | `String`       | One of `markdown`, `q_and_a`, `step_by_step`, `narrative`, `tutorial`. Defaults to `markdown`; invalid values coerced. |
  | `use_analogies`                | Optional            | `Boolean`      | Whether to generate analogies/metaphors. Default `true`. |
  | `include_visual_descriptions`  | Optional            | `Boolean`      | Whether to include described diagrams/visuals. Default `true`. |
  | `define_terminology`           | Optional            | `Boolean`      | Whether to define key terminology. Default `true`. |
  | `include_examples`              | Optional            | `Boolean`      | Whether to include practical examples/use cases. Default `true`. |
  | `include_comparisons`          | Optional            | `Boolean`      | Whether to add a comparison-with-related-concepts phase. Default `true`. |
  | `related_files`                | Optional            | `List<String>?`| Specific files or glob patterns (e.g. `**/*.kt`) used as reference input. |
  | `code_language`                | Optional            | `String?`      | Programming language for code examples, if applicable. |
  | `revision_passes`              | Optional            | `Int`          | Number of clarity revision passes; coerced into `[0, 5]`. Default `1`. |

  ### Dependencies

  None on other task types. It orchestrates internal sub-agents directly:
  - `ParsedAgent<ExplanationOutline>` for the outline phase.
  - `ParsedAgent<ExplanationSection>` for each section.
  - `ChatAgent` for the comparisons phase and each revision pass.

  ### Token Usage

  **High.** The pipeline issues one outline call, one call per key concept (typically 3–6), an optional comparison
  call, and one full-document revision call per `revision_passes` (each revision re-submits the entire assembled
  explanation). Detailed/comprehensive settings with multiple revision passes multiply cost significantly.

  ---

  ## Config & Process

  ### Type Configuration (`TechnicalExplanationTypeConfig`)

  Templated prompts and per-phase temperatures, all overridable:

  - `outline_prompt` (temperature `outline_temperature`, default `0.6`)
  - `section_prompt` (temperature `section_temperature`, default `0.7`)
  - `comparison_prompt` (temperature `comparison_temperature`, default `0.6`)
  - `revision_prompt` (temperature `revision_temperature`, default `0.5`)

  Each template uses `{placeholder}` tokens substituted at runtime with derived guidance strings (audience guidance,
  detail guidance, instruction toggles for terminology/analogies/code/visuals, etc.).

  ### Runtime Configuration (`TechnicalExplanationTaskExecutionConfigData`)

  See the configuration table above — `topic`, audience/detail/format settings, feature toggles, `related_files`,
  `code_language`, and `revision_passes`.

  ### Lifecycle

  **Initialization**
  - Runs `config.validate()`; on failure, emits a `ValidationError`, writes it to the transcript, calls
    `task.complete(...)` with the error, and returns via `resultFn` with a `CONFIGURATION ERROR` string — no LLM
    calls are made.
  - Guards against a blank/null `topic` with an early return and error message.
  - Loads prior orchestration context (`getPriorCode`) and related files (`getContextFiles`) and renders them into a
    "Reference Context" tab when non-empty.

  **Execution** (all inside a submitted background task on `task.pool`)
  1. **Phase 1 — Outline:** builds the outline prompt from templates + guidance strings, invokes `ParsedAgent` for a
     structured `ExplanationOutline`, validates it (separate `ValidationError` path on failure), renders it to the
     Outline tab and transcript.
  2. **Phase 2 — Sections:** iterates `outline.key_concepts`, building each section prompt with rolling
     "previously covered" context (last 2 sections), relevant analogies/code examples filtered by concept-name
     matching, and per-toggle instructions. Each section is generated via `ParsedAgent<ExplanationSection>` and
     rendered to its own tab, transcript, and the accumulating `resultBuilder`.
  3. **Phase 3 — Comparisons** (optional): a single `ChatAgent` call summarizing similarities/differences with
     related concepts, appended to the Comparisons tab and `resultBuilder`.
  4. **Phase 4 — Revision** (optional, repeated `revision_passes` times): each pass sends the *entire* current
     `resultBuilder` content through a `ChatAgent` revision prompt and replaces `resultBuilder` wholesale with the
     rewritten text.
  5. **Phase 5 — Final Assembly:** concatenates title, overview, terminology, the (possibly revised) body, and a
     takeaways summary into `finalExplanation`; renders to the "Complete Explanation" tab, writes a wrapped
     `<div id="final-output">` block to the transcript, computes stats (word count, code example count, elapsed
     time), and calls `task.complete(...)` plus `resultFn(finalResult)` with a short summary (not the full text).

  **Error Handling**
  - Any exception during execution is caught in a top-level `try/catch`: logged, surfaced via `task.error(e)`, an
    "❌ Error Occurred" block appended to the Overview tab, a stack-trace `<details>` block written to the transcript,
    and `resultFn` invoked with an error message that includes any partial `resultBuilder` content collected so far.
  - The transcript stream is always closed in a `finally` block regardless of success/failure.
  - No file-system mutations occur, so there is no rollback logic — failures are purely reported, not reverted.

  ---

  ## Integration

  ### Registering the task

  ```kotlin
  import com.simiacryptus.cognotik.plan.OrchestrationConfig
  import com.simiacryptus.cognotik.plan.tools.writing.TechnicalExplanationTask

  val orchestrationConfig = OrchestrationConfig(
      // ... other settings
  ).apply {
      taskTypes += TechnicalExplanationTask.TechnicalExplanation
  }

  val executionConfig = TechnicalExplanationTask.TechnicalExplanationTaskExecutionConfigData(
      topic = "How does a Bloom filter work?",
      target_audience = "software_engineer",
      level_of_detail = "detailed_walkthrough",
      include_code_examples = true,
      explanation_format = "markdown",
      code_language = "kotlin",
      revision_passes = 1
  )
  ```

  ### Prompt segment (planner-facing description)

  This is the text injected into the orchestrator's planning prompt so the LLM knows when/how to select this task:

  ```text
  TechnicalExplanation - Break down complex technical subjects into clear, digestible explanations
    ** Specify the technical topic to explain
    ** Define target audience expertise level
    ** Set level of detail (overview to comprehensive)
    ** Configure explanation format (markdown, Q&A, step-by-step, etc.)
    ** Enable analogies and metaphors for clarity
    ** Include code examples with explanations
    ** Define key terminology
    ** Provide visual descriptions
    ** Include practical examples and use cases
    ** Compare with related concepts
    ** Performs outline creation, content generation, and iterative refinement
    ** Produces clear, audience-appropriate technical explanations
  ```

  ### Outline generation prompt (template excerpt)

  ```text
  You are an expert technical educator and communicator. Create a detailed outline for explaining this topic.

  Topic: {topic}

  Target Audience: {audience}
  Audience Guidance: {audience_guidance}

  Level of Detail: {detail_level}
  Detail Guidance: {detail_guidance}

  Format: {format}

  {context}
  {docs}

  Create an outline that includes:
  1. A clear, engaging title
  2. Brief overview (2-3 sentences) of what will be explained
  3. 3-6 key concepts to cover, ordered logically (simple to complex or general to specific)
  4. {terminology_instruction}
  5. {analogy_instruction}
  6. {code_instruction}
  7. {visual_instruction}
  ...
  ```