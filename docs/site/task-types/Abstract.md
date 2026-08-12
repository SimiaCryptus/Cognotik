# AbstractTask

  **Base infrastructure class for all orchestrated task types — not directly instantiable or user-selectable.**

  `Badges: Base Class` `Side-Effect Safe` `No Model Requirement`

  > **Note:** `AbstractTask` is not a leaf task type. It provides shared plumbing (config resolution, file I/O,
  > transcript streaming, UI header rendering) that every concrete `*Task` subclass (e.g. `CodingTask`,
  > `SearchTask`) extends. This page documents the shared contract rather than a runnable product feature.

  ## Reality Check

  Because `AbstractTask` is generic (`<T : TaskExecutionConfig, U : TaskTypeConfig>`), there is no single
  concrete "input configuration." Concrete subclasses supply their own `executionConfig` shape. The example below
  shows the **minimum shape** every subclass's execution config must satisfy, since `AbstractTask` reads these
  fields directly:

  ```json
  {
    "task_type": "CodingTask",
    "task_description": "Refactor the payment module for clarity",
    "task_dependencies": ["AnalysisTask_1"],
    "main_file": "src/payments/PaymentService.kt"
  }
  ```

  **Rendered Output (UI):** `AbstractTask` itself only guarantees a rendered task header:

  ```
  ### CodingTask
  **Description:** Refactor the payment module for clarity
  ```

  followed by whatever markdown/HTML the concrete subclass appends beneath it (diffs, transcripts, tabbed panels,
  or an "Accept Result" footer button rendered via `acceptButtonFooter`). Any transcript files written via
  `newUserFileStream`/`newSystemFileStream` appear as clickable `[Transcript](...)` links in the task pane.

  ## Documentation Tab

  ### Configuration Table (fields consumed directly by `AbstractTask`)

  | Field Name | Type | Description |
  |---|---|---|
  | `task_type` (Optional) | `String?` | Identifies the task type; falls back to the Kotlin class simple name if unset. Used to resolve matching `TaskTypeConfig` in `orchestrationConfig.taskSettings`. |
  | `task_description` (Optional) | `String?` | Rendered as a markdown "**Description:**" line beneath the task header via `renderTaskHeader`. |
  | `task_dependencies` (Optional) | `List<String>?` | Names of prior tasks whose results are concatenated (via `getPriorCode`) into a `# <dependency>` blocked context for use by the subclass. |
  | `main_file` (Optional) | `String?` | Base filename used by `getOutputFile(extension)` to derive transcript/output file paths; if it already ends with the requested extension it is used verbatim. |

  Subclasses add their own fields (e.g. file globs, target languages, diff strategies) on top of this shared
  contract — see each concrete task's own documentation page for those.

  ### Dependencies

  * **`TaskOrchestrator`** — every `run(...)` implementation receives the orchestrator instance driving task
    execution and dependency resolution.
  * **`OrchestrationConfig`** — supplies working directory (`absoluteWorkingDir`), model defaults
    (`defaultSmart`/`defaultFast`), and per-type settings (`taskSettings`).
  * **`FileSelectionUtils`** — used by `getInputFileContent` to glob-match and filter files (respecting
    `.gitignore`-style exclusion via `isIgnored`).
  * **`docs` package (`isDocumentFile` / `getDocumentReader`)** — used to extract text from non-plain-text
    document formats when reading input files.
  * **`SessionTask` / `SocketManager`** — UI rendering primitives (headers, markdown blocks, hyperlink buttons).

  ### Token Usage Estimate

  **Low** at the base-class level — `AbstractTask` itself issues no LLM calls; it only exposes `defaultSmart` /
  `defaultFast` `ChatInterface` accessors for subclasses to use. Actual token usage is entirely determined by
  the concrete subclass's prompt construction.

  ## Config & Process Tab

  ### Type Configuration (`U : TaskTypeConfig`, shared across all instances of a task type)

  | Property | Description |
  |---|---|
  | `model` | Optional model override resolved via `typeConfig?.model?.instance(user)`; falls back to `orchestrationConfig.defaultSmart` if absent. |
  | `verbose` | Controls verbose logging/output for the task type, exposed as `val verbose: Boolean`. |

  ### Runtime Configuration (`T : TaskExecutionConfig`, per task instance)

  See the Configuration Table above (`task_type`, `task_description`, `task_dependencies`, `main_file`) —
  these are the only fields `AbstractTask` itself reads; subclasses extend `T` with their own fields.

  ### Lifecycle Walkthrough

  * **Initialization:** `state` begins as `TaskState.Pending`. `typeConfig` is lazily resolved by scanning
    `orchestrationConfig.taskSettings` for an entry whose `task_type` matches this instance's `taskType`. `root`
    resolves the working directory from `orchestrationConfig.absoluteWorkingDir`, throwing
    `IllegalStateException` if unset — this is the primary validation gate before any file-based work begins.
  * **Execution:** Concrete subclasses implement the abstract `run(agent, messages, task, resultFn, orchestrationConfig)`
    method. `AbstractTask` supplies helpers used during execution: `getPriorCode` (assembles dependency context),
    `getInputFileContent` (glob-resolves and reads/renders input files, catching and logging per-file read errors
    rather than failing the whole task), `renderTaskHeader` (UI header), and `acceptButtonFooter` (optional
    human-in-the-loop confirmation button that invokes a callback and mutates the button UI to "Accepted").
  * **Error Handling:** File-read failures inside `getInputFileContent` are caught per-file and logged via
    `log.warn`, returning an empty string for that file rather than aborting the whole content assembly. The
    `acceptButtonFooter` click handler wraps its callback in a try/catch, logging `Throwable`s via `log.warn`
    without propagating. There is no built-in retry/rollback — subclasses are responsible for their own recovery
    logic around the abstract `run` method.

  ## Integration Tab

  Concrete subclasses are registered by including their execution config in the plan/orchestration payload;
  `AbstractTask` does the type-matching internally via `taskType`:

  ```kotlin
  val orchestrationConfig = OrchestrationConfig(
      absoluteWorkingDir = "/workspace/project",
      taskSettings = mapOf(
          "CodingTask" to CodingTaskTypeConfig(
              task_type = "CodingTask",
              model = ChatModels.GPT4o,
              verbose = true
          )
      )
      // defaultSmart / defaultFast configured elsewhere
  )

  val task = CodingTask(
      orchestrationConfig = orchestrationConfig,
      executionConfig = CodingTaskExecutionConfig(
          task_type = "CodingTask",
          task_description = "Refactor the payment module for clarity",
          task_dependencies = listOf("AnalysisTask_1"),
          main_file = "src/payments/PaymentService.kt"
      )
  )
  ```

  ### Prompt Segment

  `AbstractTask` defines the abstract contract but injects no prompt text itself:

  ```kotlin
  abstract fun promptSegment(): String
  ```

  Each concrete subclass must implement `promptSegment()` to contribute its own instructions into the overall
  orchestration prompt; there is no shared/default prompt text at this base-class level.