# FiniteStateMachine

**Model concepts, systems, and processes using rigorous finite state machine analysis.**

`Reasoning` · `Side-Effect Safe` · `Standard Model`

FiniteStateMachine takes a concept description and drives an LLM through a seven-stage pipeline — state
identification, transition mapping, Mermaid diagram generation, edge-case analysis, property validation, test
scenario generation, and summarization — producing a structured FSM report, a downloadable Mermaid diagram file,
and a full analysis markdown file.

---

## Reality Check

### Input Configuration

```json
{
  "task_type": "FiniteStateMachine",
  "concept_to_model": "User authentication session lifecycle",
  "initial_states": ["LoggedOut"],
  "known_events": ["submit_credentials", "token_expired", "logout"],
  "identify_edge_cases": true,
  "validate_properties": true,
  "generate_test_scenarios": true,
  "domain_context": "web application authentication",
  "related_files": ["src/auth/**/*.kt"],
  "task_description": "Model the auth session FSM",
  "task_dependencies": []
}
```

### Output (rendered in UI)

A `TabbedDisplay` with tabs: **Overview**, **States**, **Transitions**, **State Diagram**, **Edge Cases**,
**Validation**, **Test Scenarios**, **Summary**.

- **Overview** tab shows a live-updating status card (concept, domain, duration, checklist of completed
  components) that starts as "🔄 Analyzing..." and ends as "✅ Analysis complete."
- **States** / **Transitions** / **Edge Cases** / **Validation** / **Test Scenarios** / **Summary** tabs each render
  the raw markdown response from the corresponding LLM call, prefixed with a loading placeholder that is cleared once
  the answer arrives.
- **State Diagram** tab renders a fenced ` ```mermaid ` block (`stateDiagram-v2` syntax) extracted from the LLM
  response; if extraction fails, it shows a warning plus the raw LLM output.
- On completion, the task writes `fsm_analysis.md` (full report of all 7 sections) and, if a diagram was generated,
  `fsm_diagram.mmd`, both exposed as download links in the final `task.complete(...)` message.
- The value returned to downstream tasks (`resultFn`) is a **truncated** (≤2000 char) summary plus a checklist of
  which analysis components ran.

---

## Documentation

### Configuration

| Field Name                 | Required/Optional | Type            | Description                                                                              |
|-----------------------------|--------------------|------------------|--------------------------------------------------------------------------------------------|
| `concept_to_model`          | Required            | `String?`        | The concept, system, or process to model as a finite state machine.                        |
| `initial_states`            | Optional            | `List<String>?`  | Initial state(s) to consider.                                                              |
| `known_events`              | Optional            | `List<String>?`  | Known events or triggers that cause state transitions.                                     |
| `identify_edge_cases`       | Optional            | `Boolean`        | Whether to identify edge cases and error states. Default `true`.                           |
| `validate_properties`       | Optional            | `Boolean`        | Whether to validate state machine properties (determinism, completeness, reachability). Default `true`. |
| `generate_test_scenarios`   | Optional            | `Boolean`        | Whether to generate test scenarios for state transitions. Default `true`.                  |
| `domain_context`            | Optional            | `String?`        | Domain or context for the FSM (e.g. "authentication system", "order processing").          |
| `related_files`             | Optional            | `List<String>?`  | Specific files or glob patterns (e.g. `**/*.kt`) to use as reference input.                 |
| `task_description`          | Optional            | `String?`        | Inherited from `TaskExecutionConfig` — human-readable description of this task instance.   |
| `task_dependencies`         | Optional            | `List<String>?`  | Inherited from `TaskExecutionConfig` — IDs of tasks this one depends on.                    |

> `concept_to_model` is validated at runtime: if blank/null, the task immediately errors out with
> `"CONFIGURATION ERROR: No concept to model specified"` before any LLM calls are made.

### Dependencies

No hard dependency on other task types is wired into the code. `related_files` content and prior-task context
(`getPriorCode(agent.executionState)`) are pulled in and injected into the first prompt, so this task can consume
output from **any** upstream task in the plan, but does not require a specific one.

### Token Usage: **High**

Up to 7 sequential LLM calls per run (states, transitions, diagram, edge cases, validation, test scenarios, summary),
each with moderately long prompts (up to full file contents from `related_files` plus prior context) and long-form
markdown responses. Disabling `identify_edge_cases`, `validate_properties`, and `generate_test_scenarios` reduces
this to 4 calls.

---

## Config & Process

### Type Configuration

Defined once per `TaskType` registration (`FiniteStateMachine` companion object):
- `name = "FiniteStateMachine"`, `category = "Reasoning"`
- `executionConfigClass = FiniteStateMachineTaskExecutionConfigData::class.java`
- `taskSettingsClass = TaskTypeConfig::class.java` (no task-specific settings beyond the shared defaults)

### Runtime Configuration

Set per task instance via `FiniteStateMachineTaskExecutionConfigData`: `concept_to_model`, `initial_states`,
`known_events`, `identify_edge_cases`, `validate_properties`, `generate_test_scenarios`, `domain_context`,
`related_files`.

### Lifecycle

**Initialization**
- Task is submitted to `task.ui.pool` as an async job; a transcript file (`transcriptFile()`) is opened for a
  full audit log.
- Validates `concept_to_model` is non-blank — fails fast with a logged error and `task.error(...)` if not.
- Resolves `defaultSmart` chat API; throws `IllegalStateException` if unavailable.
- Loads prior task context via `getPriorCode(agent.executionState)` and reference file content via
  `getInputFileCode()` (glob-matched against `related_files`, using `FileSelectionUtils` and document readers for
  non-text files).

**Execution**
1. Build state-identification prompt (concept + domain + initial states + prior context + file content) → LLM call.
2. Build transition prompt from Step 1's output + known events → LLM call.
3. Fixed Mermaid diagram prompt → LLM call → regex-extract `stateDiagram-v2` code via `extractMermaidCode`.
4. (If `identify_edge_cases`) Fixed edge-case prompt → LLM call.
5. (If `validate_properties`) Fixed validation prompt covering determinism/completeness/reachability/liveness/
   safety/minimality → LLM call.
6. (If `generate_test_scenarios`) Fixed test-scenario prompt → LLM call.
7. Fixed summary prompt → LLM call.

Each step updates its own `TabbedDisplay` tab immediately (loading state → cleared → final content) and appends to
the transcript and the in-memory `fullReport` `StringBuilder`.

**Error Handling**
- The entire body after config validation is wrapped in `try/catch/finally`.
- On any `Exception`: logs with elapsed duration, writes the stack trace to the transcript, calls `task.error(e)`,
  and returns an `"ERROR: FSM analysis failed - ${e.message}"` string via `resultFn` (no automatic retry/rollback).
- `finally` always closes the transcript stream.
- On success: writes `fsm_analysis.md` and optionally `fsm_diagram.mmd` to task storage, calls `task.complete(...)`
  with download links, and returns a truncated (`smartTruncate`, 2000 chars) summary via `resultFn`.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val config = OrchestrationConfig(
    // ...other settings...
    availableTaskTypes = listOf(
        FiniteStateMachineTask.FiniteStateMachine,
        // ...other task types...
    )
)

val task = FiniteStateMachineTask.FiniteStateMachineTaskExecutionConfigData(
    concept_to_model = "Order fulfillment workflow",
    domain_context = "e-commerce order processing",
    initial_states = listOf("OrderPlaced"),
    known_events = listOf("payment_confirmed", "shipped", "cancelled"),
    identify_edge_cases = true,
    validate_properties = true,
    generate_test_scenarios = true
)
```

### Prompt Segment (injected into planning LLM)

```text
FiniteStateMachine - Model concepts using finite state machine analysis
  ** Specify the concept, system, or process to model
  ** Optionally provide initial states and known events
  ** Identify all possible states and transitions
  ** Detect edge cases and error states
  ** Validate FSM properties (determinism, completeness, reachability)
  ** Generate test scenarios for state transitions
  ** Produces state diagram and transition table
  ** Useful for:
     - System design and validation
     - Understanding complex workflows
     - Identifying missing requirements
     - Test case generation
     - Protocol analysis
```