# HistoricalFigureDebate

**Stage a multi-round, in-character debate between historical figures on any topic, with optional neutral moderator framing and a final impartial synthesis.**

`Side-Effect Safe` `Reasoning` `Multi-Agent`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "HistoricalFigureDebate",
  "debate_topic": "Is centralized government necessary for a just society?",
  "participants": [
    "Thomas Hobbes",
    "Henry David Thoreau",
    "Mary Wollstonecraft"
  ],
  "related_files": [
    "docs/political_theory_notes.md"
  ],
  "rounds": 3,
  "include_moderator": true
}
```

**Rendered Output**

The task builds a `TabbedDisplay` in the session UI with one tab per phase:

- **Overview** — live-updating markdown card showing topic, participants, round count, moderator toggle, and a running progress log (`✅ Round 2 complete (4.1s)` style updates).
- **Context** — (only if input files or prior task output exist) renders the combined context blob as markdown.
- **Moderator Opening** — moderator's framing statement and opening question, rendered as markdown.
- **Round 1 … Round N** — one tab per round, each containing a `## <Figure Name>` markdown block per participant's in-character statement for that round.
- **Synthesis** — final structured summary: per-participant position recap, points of agreement/disagreement, strongest arguments, open questions, and an impartial conclusion.

A downloadable transcript file (`transcript-*.md` style, via `task.newUserFileStream`) is also written incrementally, containing the full unabridged debate. The function result returned to the orchestrator (`resultFn`) is a **truncated/concise** version (first and last round only, quotes capped at 1500 chars) — the full debate remains only in the file stream and UI tabs.

---

## Documentation Tab

### Configuration

| Field Name         | Type            | Required/Optional | Description                                                                 |
|---------------------|------------------|--------------------|-------------------------------------------------------------------------------|
| `debate_topic`      | `String`         | Required           | The topic or question the historical figures will debate.                    |
| `participants`      | `List<String>`   | Required (≥2)      | The list of historical figures who will participate in the debate.           |
| `related_files`     | `List<String>`   | Optional           | Glob patterns for input files providing context for the debate.              |
| `rounds`            | `Int` (default 3)| Optional (1–10)    | Number of debate rounds; each round gives every participant one turn.        |
| `include_moderator` | `Boolean` (default `true`) | Optional | Whether to include a neutral moderator to frame, question, and summarize.    |
| `task_dependencies` | `List<String>`   | Optional           | IDs of tasks whose output should be included as prior context.               |

### Dependencies

- No hard dependency on other `TaskType`s in the plan graph, but consumes **prior task output** via `getPriorCode(agent.executionState)`, so it composes naturally after research/analysis tasks whose output should inform the debate.
- Uses `FileSelectionUtils` for glob-based file resolution (`related_files`), following the same convention as other file-context tasks in the codebase.
- Relies on `ChatAgent` (one instance per participant + one for the moderator/synthesizer) rather than any shared orchestrator agent pool.

### Token Usage Estimate

**High** — Each round issues one LLM call *per participant* (`participants.size × rounds` calls), each carrying the full running conversation log as context (which grows linearly each round). Additional calls for moderator opening and final synthesis. With 3 participants × 3 rounds, expect ~11 total LLM calls, several with growing context windows — cost scales quickly with `rounds` and participant count.

---

## Config & Process Tab

### Type Configuration
Set at plan-authoring time, fixed for the task instance:
- `debate_topic`
- `participants`
- `rounds`
- `include_moderator`
- `related_files`
- `task_dependencies`

### Runtime Configuration
Resolved dynamically during `run()`:
- `defaultSmart` model API (chosen at execution time, not configurable per-task in this class)
- Combined context string (`priorContext` + `inputFileContext`), computed fresh each run
- Per-figure and moderator `ChatAgent` instances, constructed at execution time with fixed temperature (`0.8` for participants, `0.4` for moderator/synthesizer)

### Lifecycle Walkthrough

1. **Initialization**
   - `executionConfig?.validate()` checks: non-blank `debate_topic`, ≥2 non-blank `participants`, `rounds` in `[1,10]`. Any failure short-circuits with a `CONFIGURATION ERROR` string passed to both `task.safeComplete` and `resultFn`.
   - Resolves input files via glob patterns and reads prior task output.
   - Instantiates one `ChatAgent` per participant (in-character system prompt) and, if `include_moderator`, a moderator `ChatAgent`.
   - Renders initial "Overview" tab and opens a transcript file stream.

2. **Execution**
   - Optional moderator opening statement generated and logged to transcript, conversation log, and UI.
   - For each of `rounds`:
     - A new "Round N" tab is created.
     - Each participant is prompted with the topic, combined context, and the full conversation-so-far, and produces an in-character statement appended to the shared `conversationLog`.
     - Statements from round 1 and the final round are captured in the concise `transcriptBuilder`; all rounds go to the full transcript file and UI tabs.
   - After all rounds, a synthesis is generated (by the moderator agent if present, else a dedicated impartial-analyst agent) summarizing positions, agreements/disagreements, and open questions.
   - Final concise result is returned via `resultFn`; full transcript file is finalized and closed.

3. **Error Handling**
   - Any exception during the debate loop is caught: logged via SLF4J, reported to the UI via `task.error(e)`, and an "❌ Error Occurred" block is appended to both the Overview tab and transcript file (with `e.message` included).
   - Partial results (whatever rounds completed) are still returned via `resultFn` as an "Error in Historical Figure Debate" report, so downstream tasks/orchestration can still consume whatever was produced before failure.
   - No automatic retry or rollback — failures are terminal for this task instance but non-fatal to the overall orchestration (execution simply returns partial/error output).

---

## Integration Tab

### Registering in an `OrchestrationConfig`

```kotlin
import com.simiacryptus.cognotik.plan.tools.reasoning.HistoricalFigureDebateTask
import com.simiacryptus.cognotik.plan.tools.reasoning.HistoricalFigureDebateTask.HistoricalFigureDebateTaskExecutionConfigData

val debateConfig = HistoricalFigureDebateTaskExecutionConfigData(
    debate_topic = "Is centralized government necessary for a just society?",
    participants = listOf("Thomas Hobbes", "Henry David Thoreau", "Mary Wollstonecraft"),
    related_files = listOf("docs/political_theory_notes.md"),
    rounds = 3,
    include_moderator = true
)

// Register the task type with the orchestrator's plan
orchestrationConfig.taskTypes += HistoricalFigureDebateTask.HistoricalFigureDebate
```

### Prompt Segment (Plan-Time Description)

This is the guidance injected into the planning/LLM context so the orchestrator knows when and how to use this task:

```text
HistoricalFigureDebate - Stage a debate between historical figures
 ** Specify the debate topic or question to explore
 ** Provide a list of at least two historical figures as participants
 ** Optionally provide input files (supports glob patterns) for context
 ** Configure the number of debate rounds (default: 3)
 ** Enable/disable a neutral moderator
 ** Each participant argues in character based on their known views and rhetoric
 ** Produces a structured debate transcript and a moderator synthesis
```

### Per-Participant Agent Prompt (Runtime)

Each participant `ChatAgent` is constructed with a template like:

```text
You are $figure, the historical figure, participating in a structured debate.
Speak and argue in character, drawing on your documented views, philosophy, rhetorical style, and the
historical context of your life. Stay true to what is known of your positions and personality.

The debate topic is: $debateTopic

Guidelines:
1. Argue persuasively from your authentic perspective
2. Reference your own works, ideas, and experiences where relevant
3. Directly engage with and rebut the arguments of other participants
4. Maintain your characteristic tone and manner of speaking
5. Keep each contribution focused and substantive

Respond only as $figure, without narration or stage directions.
```