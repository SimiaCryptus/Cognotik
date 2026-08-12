# TutorialGeneration

Generate complete, step-by-step tutorials for processes and projects — outline, prerequisites, per-step instructions, troubleshooting, and next-steps, assembled into a publication-ready Markdown document.

`Side-Effect Safe` · `Writing` · `Multi-Phase Agent Pipeline`

---

## Reality Check

**Input configuration**

```json
{
  "task_type": "TutorialGeneration",
  "goal": "Deploy a Node.js app to a Docker container",
  "target_platform": "Linux",
  "include_screenshots_placeholders": true,
  "verbosity": "detailed",
  "include_troubleshooting": true,
  "skill_level": "beginner",
  "estimated_duration": 30,
  "include_code_examples": true,
  "include_validation_steps": true,
  "include_learning_objectives": true,
  "include_next_steps": true,
  "target_step_count": 7,
  "related_files": ["**/Dockerfile", "**/package.json"]
}
```

**Rendered output (UI)**

The task drives a `TabbedDisplay` with one tab per phase, plus a running "Overview" tab acting as a progress log:

- **Overview** — live checklist of phases (`Planning & Outline` → `Writing Steps` → `Troubleshooting` → `Next Steps` → `Final Assembly`), each step ticked off with ✅ as it completes, ending in a stats block (step count, word count, code block count, generation time) and links to output files.
- **Context** (conditional) — collapsible `<details>` block showing raw input file content and prior-task context.
- **Outline** — rendered outline: title, description, learning objectives, required/optional prerequisites, and a numbered list of planned steps with purpose/actions/time estimates.
- **Step 1..N** — one tab per tutorial step with explanation prose, fenced code blocks (with working directory notes), 📸 screenshot placeholders, expected outcome, numbered validation checklist, and ⚠️ common issues.
- **Troubleshooting** (conditional) — numbered problem/symptom/cause/solution sections.
- **Next Steps** (conditional) — "Try These Next", "Related Resources", "Advanced Topics" bulleted lists.
- **Complete Tutorial** — the fully assembled Markdown document, also saved to `tutorial.md`; a parallel `transcript.md` file logs every phase transition with timestamps.

The final `resultFn` payload is a concise Markdown summary (goal, platform, skill level, feature counts, links to `tutorial.md` and `transcript.md`).

---

## Documentation Tab

### Configuration Table

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `goal` | Required | `String?` | The final outcome the user should achieve (e.g., "deploy a web app to the cloud") |
| `target_platform` | Optional | `String` (default `"cross-platform"`) | Environment the tutorial targets (e.g., Windows, Linux, macOS, VS Code, Docker) |
| `include_screenshots_placeholders` | Optional | `Boolean` (default `true`) | Add placeholders like "[Screenshot of the successful output]" |
| `verbosity` | Optional | `String` (default `"detailed"`) | Amount of explanatory text per step: `concise`, `detailed`, `verbose` |
| `include_troubleshooting` | Optional | `Boolean` (default `true`) | Add a common-errors/troubleshooting section |
| `skill_level` | Optional | `String` (default `"beginner"`) | Target audience skill level |
| `estimated_duration` | Optional | `Int` (default `30`) | Estimated total completion time in minutes; must be positive |
| `include_code_examples` | Optional | `Boolean` (default `true`) | Include code examples and commands |
| `include_validation_steps` | Optional | `Boolean` (default `true`) | Include validation steps to verify success |
| `include_learning_objectives` | Optional | `Boolean` (default `true`) | Include a "What You'll Learn" section |
| `include_next_steps` | Optional | `Boolean` (default `true`) | Include a "Next Steps" section |
| `target_step_count` | Optional | `Int` (default `7`) | Number of main steps; must be between 3 and 20 |
| `related_files` | Optional | `List<String>?` | Glob patterns for input files used as context |
| `task_description` | Optional | `String?` | Inherited task metadata (defaults to `"Generate tutorial for: '$goal'"`) |
| `task_dependencies` | Optional | `List<String>?` | Inherited task dependency list |
| `state` | Optional | `TaskState?` (default `Pending`) | Inherited orchestration state |

### Dependencies

No hard dependencies on other `TaskType`s are wired in the code. The task reads generic pipeline context via `getPriorCode(agent.executionState)` and `getInputFileContent(related_files, root)`, so it composes well downstream of any task that populates prior-code context or produces related files, but it can also run standalone.

### Token Usage Estimate

**High.** The task runs up to `2 + target_step_count` LLM calls: one outline call, one per tutorial step (default 7), plus optional troubleshooting and next-steps calls — each with substantial prompt context (input files truncated to 3000 chars, prior context, messages) and structured JSON output via `ParsedAgent`.

---

## Config & Process Tab

### Type Configuration

Static aspects wired at task-type registration:

- `TaskType.name = "TutorialGeneration"`, `category = "Writing"`
- `executionConfigClass = TutorialGenerationTaskExecutionConfigData::class.java`
- `taskSettingsClass = TaskTypeConfig::class.java` (no task-specific settings subclass)

### Runtime Configuration

Per-invocation fields from `TutorialGenerationTaskExecutionConfigData` (see table above): `goal`, `target_platform`, `verbosity`, `skill_level`, `estimated_duration`, `target_step_count`, and the six boolean feature toggles (`include_*`), plus `related_files` for context loading.

### Lifecycle

**Initialization**
- `run()` submits work to `task.ui.pool` and opens a transcript file stream.
- Guards against a missing `executionConfig`, calling `task.safeComplete` and returning early if absent.
- Calls `executionConfig.validate()` — checks `goal` non-blank, `estimated_duration > 0`, `target_step_count` in `[3,20]`, `verbosity`/`skill_level` non-blank, plus field-level `ValidatedObject.validateFields`. On failure, emits a `ValidationError`, completes the task with an error message, and returns.
- Re-checks `goal` is non-blank as a final guard before proceeding.
- Acquires a model client via `defaultSmart.getChildClient(task)` and renders an initial "Overview" tab.

**Execution**
1. **Context gathering** — loads prior orchestration context and glob-matched related files; renders a collapsible "Context" tab if any content is present.
2. **Phase 1 – Outline** — a `ParsedAgent` targeting `TutorialOutline` produces title, description, learning objectives, prerequisites, and step outlines. Result is validated (`outline.validate()`); on failure the task errors out and returns.
3. **Phase 2 – Steps** — iterates `outline.steps`, running one `ParsedAgent` per step (`TutorialStep`), feeding a rolling window of the last two completed steps as context. Each step tab is rendered incrementally.
4. **Phase 3 – Troubleshooting** (if `include_troubleshooting`) — a `ParsedAgent` targeting `TroubleshootingSection` summarizes step titles into likely problems/causes/solutions.
5. **Phase 4 – Next Steps** (if `include_next_steps`) — a `ParsedAgent` targeting `NextSteps` produces suggestions, resources, and advanced topics.
6. **Phase 5 – Final Assembly** — concatenates outline, all steps, troubleshooting, and next-steps into one Markdown document, saved via `task.saveFile("tutorial.md", ...)`, and a concise summary is built for `resultFn`.

**Error Handling**
- The entire pipeline (post-validation) runs inside a single `try/catch (e: Exception)`.
- On exception: logs the error, calls `task.error(e)`, appends a stack trace to the transcript, renders an "❌ Error Occurred" block in the Overview tab, and calls `resultFn` with an error report that includes any partial `resultBuilder` content accumulated so far.
- There is no automatic retry — failures in any phase (outline, step, troubleshooting, next-steps) abort the remaining phases and surface the exception directly.

---

## Integration Tab

### Registering the task

```kotlin
val orchestrationConfig = OrchestrationConfig(
  // ... other task types ...
  availableTaskTypes = listOf(
    TutorialGenerationTask.TutorialGeneration,
    // ... other TaskType entries ...
  )
)

// Example programmatic invocation via a plan step:
val planStep = TutorialGenerationTask.TutorialGenerationTaskExecutionConfigData(
  goal = "Deploy a Node.js app to a Docker container",
  target_platform = "Linux",
  skill_level = "beginner",
  estimated_duration = 30,
  target_step_count = 7,
  include_troubleshooting = true,
  include_next_steps = true,
  related_files = listOf("**/Dockerfile", "**/package.json")
)
```

### Prompt Segment (injected into planning LLM)

```
TutorialGeneration - Create complete, step-by-step tutorials for processes and projects
  ** Specify the goal or final outcome to achieve
  ** Define target platform and environment
  ** Set skill level and estimated duration
  ** Enable screenshot placeholders for visual guidance
  ** Configure verbosity level (concise, detailed, verbose)
  ** Include code examples and commands
  ** Add validation steps to verify success
  ** Include troubleshooting section for common errors
  ** Add learning objectives and next steps
  ** Produces publication-ready tutorial with clear, actionable steps
```