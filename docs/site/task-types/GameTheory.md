# GameTheory

**Strategic-interaction analysis engine — Nash equilibria, dominant strategies, and Pareto optimality on demand.**

`Reasoning` · `Side-Effect Safe` · `Multi-Step LLM Pipeline`

---

## Reality Check

**Input (execution config):**

```json
{
  "task_type": "GameTheory",
  "game_scenario": "Two competing SaaS vendors deciding whether to cut prices by 20% in Q3 to win market share.",
  "players": ["VendorA", "VendorB"],
  "player_strategies": {
    "VendorA": ["Cut Price", "Hold Price"],
    "VendorB": ["Cut Price", "Hold Price"]
  },
  "game_type": "non-cooperative",
  "build_payoff_matrix": true,
  "find_nash_equilibria": true,
  "analyze_dominant_strategies": true,
  "find_pareto_optimal": true,
  "provide_recommendations": true,
  "repeated_game_analysis": false,
  "iterations": 10,
  "additional_context": "Both vendors have similar cost structures and a shared customer base of ~50,000 SMBs.",
  "related_files": ["docs/pricing-strategy.md"]
}
```

**Output (rendered in UI):**

A tabbed transcript panel (`TabbedDisplay`) with sequential tabs:

- **Overview** — scenario, players, game type, live status (`🔄 Initializing...` → `✅ Analysis complete`).
- **Context** — prior task outputs and additional context, if any.
- **Game Structure** — markdown prose describing game classification (cooperative/non-cooperative, information structure, timing).
- **Payoff Matrix** — a markdown table of strategy-combination payoffs per player.
- **Nash Equilibria** — bulleted list of pure/mixed strategy equilibria with stability commentary.
- **Dominant Strategies** — per-player breakdown of strictly/weakly dominant and dominated strategies.
- **Pareto Optimality** — outcomes ranked by efficiency, contrasted against equilibria.
- **Repeated Game** *(only if `repeated_game_analysis: true`)* — folk theorem/trigger-strategy discussion over `iterations` rounds.
- **Recommendations** — per-player strategic guidance, risk notes, coordination opportunities.
- **Summary** — structured recap (game type, players, equilibria, dominant strategies, Pareto outcomes, recommendations) parsed into a `GameAnalysis` object and rendered as a final markdown block.

A downloadable transcript file (`transcriptFile()`) accumulates every step with timestamps, and the final task result is a consolidated markdown report truncated per-section at 10,000 characters.

---

## Documentation Tab

### Configuration

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `game_scenario` | **Required** | `String` | The strategic situation or game to analyze. |
| `players` | **Required** | `List<String>` | List of players/agents in the game. |
| `player_strategies` | Optional | `Map<String, List<String>>` | Available strategies for each player (can be inferred if omitted). |
| `game_type` | Optional (default `"non-cooperative"`) | `String` | Type of game: cooperative, non-cooperative, zero-sum, repeated, sequential. |
| `build_payoff_matrix` | Optional (default `true`) | `Boolean` | Whether to construct a payoff matrix. |
| `find_nash_equilibria` | Optional (default `true`) | `Boolean` | Whether to identify Nash equilibria. |
| `analyze_dominant_strategies` | Optional (default `true`) | `Boolean` | Whether to analyze dominant strategies. |
| `find_pareto_optimal` | Optional (default `true`) | `Boolean` | Whether to identify Pareto optimal outcomes. |
| `provide_recommendations` | Optional (default `true`) | `Boolean` | Whether to provide strategic recommendations for each player. |
| `repeated_game_analysis` | Optional (default `false`) | `Boolean` | Whether to analyze the game as a repeated game. |
| `iterations` | Optional (default `10`, clamped `1–1000`) | `Int` | Number of iterations for repeated game analysis (auto-bumped to ≥2 if repeated analysis is enabled). |
| `additional_context` | Optional | `String` | Additional context or constraints. |
| `related_files` | Optional | `List<String>` | File paths/glob patterns used as supplementary input content. |
| `task_description` | Optional | `String` | Inherited task metadata; defaults to a scenario-derived description. |
| `task_dependencies` | Optional | `List<String>` | Inherited task metadata; IDs of upstream tasks. |

**Validation rules:** `game_scenario` must not be blank; `players` must not be empty; `game_type` defaults to `"non-cooperative"` if blank; `iterations` is coerced into `[1, 1000]` and raised to at least `2` when `repeated_game_analysis` is true.

### Dependencies

No hard dependency on other `Task` classes at the type level. It consumes:
- Prior orchestration context via `getPriorCode(agent.executionState)` (upstream task outputs feed into the "Context" tab).
- Optional file content from `related_files`, resolved via `FileSelectionUtils.filteredWalk` and glob matching, with document extraction (`PaginatedDocumentReader`) for non-text formats and raw read fallback for code/text files.

### Token Usage Estimate

**High.** The task issues up to 8 sequential LLM calls per run (structure → payoff matrix → Nash equilibria → dominant strategies → Pareto optimality → repeated game → recommendations → structured summary via `ParsedAgent`), each potentially carrying the full conversation history plus injected file content. Output per section is capped for display (`maxOutputLengthPerField = 10000`) but the underlying token cost is uncapped.

---

## Config & Process Tab

### Type Configuration (`GameTheoryTypeConfig`)

Static, reusable across task instances:

- `analysis_temperature` (default `0.3`) — sampling temperature for structural/analytical steps.
- `summary_temperature` (default `0.2`) — sampling temperature for the final structured summary.
- Prompt templates: `structure_prompt_template`, `payoff_matrix_prompt`, `nash_equilibria_prompt`, `dominant_strategies_prompt`, `pareto_optimal_prompt`, `repeated_game_prompt_template`, `recommendations_prompt_template`, `summary_prompt` — all overridable to customize analytical framing without touching code.

### Runtime Configuration (`GameTheoryTaskExecutionConfigData`)

Per-invocation parameters described in the Documentation Tab above — scenario, players, strategies, toggles for each analytical stage, iteration count, and contextual/file inputs.

### Lifecycle

**Initialization**
1. `run()` resolves `game_scenario` and `players` from `executionConfig`; either being null/blank/empty triggers an immediate `CONFIGURATION ERROR` completion via `task.safeComplete(...)` and early return — no LLM calls are made.
2. A background job is submitted to `task.pool`; a transcript file stream and a `TabbedDisplay` are opened for structured, incremental UI updates.

**Execution**
1. **Context assembly** — prior orchestration state and any `additional_context` are merged into a context string injected into subsequent prompts.
2. **Game Structure** — `ChatAgent` (using `defaultSmart`, `analysis_temperature`) analyzes game type, strategy spaces, and payoff characterization from `structure_prompt_template`.
3. **Conditional stages** — payoff matrix, Nash equilibria, dominant strategies, Pareto optimality, repeated-game analysis, and recommendations each run only if their corresponding boolean flag is `true`, reusing the same `ChatAgent` conversation so later steps build on earlier analysis.
4. **Structured summary** — a `ParsedAgent<GameAnalysis>` (using `defaultFast` for parsing, up to 2 deserializer retries) extracts a typed `GameAnalysis` object (game type, players, strategies, payoff summary, Nash equilibria, dominant strategies, Pareto outcomes, recommendations) from the full analysis for reliable downstream consumption.
5. Each stage writes to the transcript, updates its own UI tab (loading indicator → completed content), and logs timing/length metrics.
6. A consolidated final result string is built via `buildFinalResult(...)`, truncating each section to `maxOutputLengthPerField` characters, and passed to `resultFn`.

**Error Handling**
- The entire execution body is wrapped in try/catch; any exception is logged with elapsed duration, reported to the UI via `task.error(e)` (best-effort, itself wrapped in try/catch), appended to the transcript as a stack trace block, and surfaced to `resultFn` as an `"ERROR: Game theory analysis failed - ..."` string rather than throwing.
- The transcript output stream is closed in a `finally` block regardless of success/failure, with close failures logged but not propagated.
- There is no automatic retry/rollback of partially completed analysis stages — a failure mid-pipeline yields whatever transcript/tabs were written up to that point plus the error tab.

---

## Integration Tab

### Registering the Task

```kotlin
import com.simiacryptus.cognotik.plan.tools.social.GameTheoryTask
import com.simiacryptus.cognotik.plan.OrchestrationConfig

val orchestrationConfig = OrchestrationConfig(
    // ... other task type registrations
    taskTypes = listOf(
        // ... existing task types
        GameTheoryTask.GameTheory
    )
)

// Example execution config for a planned task step
val gameTheoryStep = GameTheoryTask.GameTheoryTaskExecutionConfigData(
    game_scenario = "Two competing SaaS vendors deciding whether to cut prices by 20% in Q3.",
    players = listOf("VendorA", "VendorB"),
    game_type = "non-cooperative",
    repeated_game_analysis = false,
    task_description = "Analyze Q3 pricing standoff between VendorA and VendorB"
)
```

### Prompt Segment (injected into planner LLM)

```text
GameTheory - Analyze strategic interactions using game theory
  ** Specify the strategic situation or game scenario
  ** Define players and their available strategies
  ** Choose game type: cooperative, non-cooperative, zero-sum, repeated, sequential
  ** Optionally build payoff matrices
  ** Identify Nash equilibria and dominant strategies
  ** Find Pareto optimal outcomes
  ** Provide strategic recommendations for each player
  ** Analyze repeated games with multiple iterations
  ** Useful for:
     - Strategic decision making
     - Competitive analysis
     - Negotiation planning
     - Market strategy
     - Conflict resolution
```

### Core Analytical Prompt (Game Structure stage)

```text
You are an expert in game theory and strategic analysis. Your task is to analyze a strategic interaction using game theory principles.
## Game Scenario:
{game_scenario}
## Players:
{players}
{strategies_section}
## Game Type:
{game_type}
{context}
## Analysis Instructions:
1. **Identify the Game Structure**: ...
2. **Define Strategy Spaces**: ...
3. **Characterize Payoffs**: ...
4. **Identify Key Features**: ...
Generate the game structure analysis now:
```