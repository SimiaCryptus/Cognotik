# UnrunnableProtocolDialog

  **Analyze any concept through dense, pseudo-code "Unrunnable Protocol" frameworks — paired with a built-in skeptic that audits the framework for pattern worship before you believe it.**

  `Reasoning` · `Side-Effect Safe` · `Multi-Agent Dialog` · `Iterative Refinement`

  ---

  ## Reality Check

  ### Input Configuration

  ```json
  {
    "task_type": "UnrunnableProtocolDialog",
    "concept": "Why do AI models exhibit sudden shifts toward user-flattering belief systems during long conversations?",
    "domain": "cognitive architecture",
    "related_files": ["docs/research/*.md"],
    "iterations": 3,
    "epistemic_audit": true
  }
  ```

  ### Rendered Output (UI)

  The task renders as a **tabbed display** (`TabbedDisplay`) with one tab per phase:

  - **Overview** — live-updating status log: initialization, per-iteration completion timestamps and durations, final summary with total time and character count.
  - **Context** (only if prior task output or `related_files` matched) — the raw combined context injected into the dialog.
  - **Iteration 1…N** — each tab shows:
    - `## Protocol Draft` — a fenced pseudo-code block with struct/function/state-machine artifacts (e.g. `ConvertPsychology { ... }`, `def euphoria_misinterpretation(): ...`).
    - `## Epistemic Audit` — prose critique flagging "pattern imposition" vs "genuine insight," unfalsifiable structures, and concrete revision suggestions.
  - **Synthesis** — a final "sobered" markdown report separating expressive/heuristic value from verifiable claims, with an explicit epistemic-confidence statement.

  A full transcript is also written to a downloadable file (`transcriptFile()`) containing the entire draft/audit history in Markdown, and the task's `resultFn` returns a **concise** version (first + last iteration only, truncated per-field to 1500 chars) for downstream task chaining.

  ---

  ## Documentation

  ### Configuration Fields

  | Field Name        | Required/Optional | Type           | Description                                                                                       |
  |--------------------|--------------------|----------------|-----------------------------------------------------------------------------------------------------|
  | `concept`          | Required           | `String`       | The concept, system, or phenomenon to analyze through the Unrunnable Protocol format.              |
  | `domain`           | Optional           | `String`       | Optional domain or framing for the analysis (e.g. `"cognitive architecture"`, `"game theory"`).    |
  | `related_files`    | Optional           | `List<String>` | Optional input files (supports glob patterns) providing additional context for the analysis.       |
  | `iterations`        | Optional (default `3`) | `Int`      | Number of draft/audit iterations to perform. Validated range: 1–10.                                |
  | `epistemic_audit`   | Optional (default `true`) | `Boolean` | Whether to include an epistemic audit pass critiquing pattern-worship / framework fundamentalism.  |
  | `task_dependencies` | Optional           | `List<String>` | Standard cross-task dependency wiring (inherited from `TaskExecutionConfig`).                       |

  ### Dependencies

  This task has **no hard dependency on other Task types** — it is self-contained, using two internally-instantiated `ChatAgent` instances (drafter + auditor) rather than delegating to sibling tasks. It does consume prior task output via `getPriorCode(agent.executionState)` for context chaining, following the standard `AbstractTask` pattern.

  ### Token Usage Estimate

  **High** — Each iteration invokes two LLM calls (draft + audit) using `defaultSmart`, with prompts that include the full prior draft and audit text plus growing context. Default `iterations = 3` with audit enabled means up to 7 LLM calls per run (3 drafts + 3 audits + 1 synthesis), each potentially producing dense multi-paragraph pseudo-code and critique text.

  ---

  ## Config & Process

  ### Type Configuration
  - `task_type`: fixed to `"UnrunnableProtocolDialog"`
  - `taskSettingsClass`: `TaskTypeConfig` (no task-type-specific settings beyond the base)
  - `executionConfigClass`: `UnrunnableProtocolDialogTaskExecutionConfigData`

  ### Runtime Configuration
  - `concept`, `domain`, `related_files`, `iterations`, `epistemic_audit` — resolved once at task start via `executionConfig`.
  - `defaultSmart` model is used for both the drafter (`temperature = 0.7`) and auditor (`temperature = 0.5`) agents.

  ### Lifecycle

  1. **Initialization**
     - `executionConfig?.validate()` checks `concept` is non-blank and `iterations` is in `[1, 10]`; failures short-circuit with a `CONFIGURATION ERROR` result and `task.safeComplete`.
     - Resolves input file context via `FileSelectionUtils.filteredWalk` against `related_files` glob patterns.
     - Instantiates the `TabbedDisplay`, writes an initial "Overview" tab, and opens a transcript file stream.
     - Pulls prior task context via `getPriorCode` and merges with file context into `combinedContext`.
     - Constructs the **Protocol Drafter** `ChatAgent` (expressive, high-temperature) and **Epistemic Auditor** `ChatAgent` (skeptical, lower-temperature).

  2. **Execution**
     - Loops `1..iterations`:
       - Builds a draft prompt (first iteration includes the concept + context; later iterations include the previous draft and prior audit feedback).
       - Calls `drafterAgent.answer(...)`; falls back to `"No draft generated"` on empty response.
       - If `epistemic_audit` is enabled, calls `auditorAgent.answer(...)` on the fresh draft; falls back to `"No audit generated"`.
       - Appends both to the full transcript, per-iteration tab, and (for first/last iteration only) the concise result builder.
       - Tracks per-iteration timing for the final summary.
     - After the loop, generates a **synthesis** pass by calling the auditor agent once more with the final draft (+ final audit) to produce a "sobered" summary separating heuristic value from verified claims.

  3. **Error Handling**
     - The entire iteration/synthesis block is wrapped in a `try/catch (e: Exception)`.
     - On failure: logs the error, calls `task.error(e)`, appends an "❌ Error Occurred" block to both the transcript and Overview tab, and returns a partial-results `errorOutput` string (including however many iterations completed) via `resultFn` rather than throwing.
     - No rollback is needed since this task performs no destructive/side-effecting operations — it only writes to its own transcript file and UI tabs.

  ---

  ## Integration

  ### Registering in an OrchestrationConfig

  ```kotlin
  import com.simiacryptus.cognotik.plan.tools.reasoning.UnrunnableProtocolDialogTask

  val orchestrationConfig = OrchestrationConfig(
      // ... other config ...
      taskTypes = listOf(
          UnrunnableProtocolDialogTask.UnrunnableProtocolDialog,
          // ... other task types ...
      )
  )
  ```

  Example execution-config wiring for a plan step:

  ```kotlin
  UnrunnableProtocolDialogTask.UnrunnableProtocolDialogTaskExecutionConfigData(
      concept = "Emergent goal misgeneralization in RL agents",
      domain = "reinforcement learning",
      related_files = listOf("docs/rl-notes/*.md"),
      iterations = 4,
      epistemic_audit = true
  )
  ```

  ### Prompt Segments (as injected into the LLM)

  **Protocol Drafter system prompt:**

  ```text
  You are a Protocol Drafter working in the "Unrunnable Protocol" expressive format.

  The Unrunnable Protocol is a dense, pseudo-code style notation used purely as an EXPRESSIVE LENS,
  not as executable code. You express conceptual structure using:
  - Named struct/object blocks with descriptive snake_case fields (e.g. ConvertPsychology { ... })
  - Function-like blocks that describe processes (e.g. def euphoria_misinterpretation(): ...)
  - State machines, cascades, and weighted parameter listings
  - IF/THEN logic blocks for conditional dynamics

  Wrap protocol artifacts in fenced code blocks. Be vivid, systematic, and structurally elegant.

  CRITICAL DISCIPLINE: The protocol is a TOOL FOR THINKING, not a claim of TRUTH. Structural
  elegance is not evidence of accuracy. Express boldly, but do not conflate expressiveness with
  correctness.

  Domain: {domain}

  Produce one or more focused protocol artifacts that illuminate the concept.
  ```

  **Epistemic Auditor system prompt:**

  ```text
  You are an Epistemic Auditor. Your role is to scrutinize "Unrunnable Protocol" artifacts for
  cognitive failure modes documented in framework-fundamentalism analysis:
  1. Pattern worship: imposing the framework on data that does not support it
  2. Elegance/truth conflation: mistaking aesthetic appeal for empirical accuracy
  3. Apologetics reflex: incorporating disconfirming evidence rather than questioning the framework
  4. Speculation-as-fact: treating speculative pattern projections as established
  5. Loss of epistemic humility under expressive euphoria

  For the given artifact, identify which claims are GENUINE INSIGHT vs PATTERN IMPOSITION,
  flag any unfalsifiable or overfit structures, and propose concrete revisions that preserve
  expressive value while restoring epistemic grounding.

  Domain: {domain}

  Be specific and constructive. Separate "the tool is useful" from "the framework is true."
  ```

  These correspond directly to `promptSegment()` and the two `ChatAgent` constructions in
  `UnrunnableProtocolDialogTask.kt`.