# DataIngest

**Iteratively parse unstructured logs/text into structured data using LLM-discovered regex patterns.**

`Side-Effect Safe` · `File` · `Model Required`

DataIngest samples raw log/text files, asks an LLM to propose Java-compatible regex patterns for residual
(unmatched) lines, validates and registers those patterns, then streams the full file set through the pattern
registry to emit structured artifacts — JSONL, CSV, a search index, a pattern registry, and a standalone Node.js
reprocessing script.

---

## Reality Check

**Input configuration**

```json
{
  "task_type": "DataIngest",
  "task_description": "Parse Apache access logs and application error logs",
  "input_files": ["logs/**/*.log", "var/log/app-*.txt"],
  "sample_size": 1000,
  "max_iterations": 10,
  "coverage_threshold": 0.95
}
```

**Rendered output (UI)**

The task renders as a tabbed panel (`TabbedDisplay`) with three tabs:

- **Log** — a running markdown log: file discovery counts, sampling progress, phase headers ("Phase 1: Sampling",
  "Phase 2: Pattern Discovery", "Phase 3: Bulk Extraction"), per-iteration discovery messages
  (`✅ Discovered Pattern (N matches in sample): \`regex\``, or `⚠️`/`❌` on failure/invalid regex).
- **Discovery** — a live-updating status line showing `Iteration N | Coverage: X% | Residuals: M`, replaced with
  "Discovery Complete" once the loop exits.
- **Summary** — a final card with **Total Extracted Records** and **Patterns Discovered** counts.

At completion, the task posts a markdown report with download links: `data.jsonl`, `data.csv`, `index.csv`,
`patterns.json`, and `process.js`, plus a per-pattern match-count breakdown showing each field list and its regex.

---

## Documentation

### Configuration Table

| Field Name             | Required/Optional | Type           | Description                                                                 |
|-------------------------|--------------------|----------------|-------------------------------------------------------------------------------|
| `input_files`           | Required           | `List<String>` | File patterns to ingest (glob, e.g. `**/*.log`).                             |
| `sample_size`           | Optional (default `1000`) | `Int`   | Number of lines to sample for pattern discovery.                            |
| `max_iterations`        | Optional (default `10`)   | `Int`   | Maximum number of discovery iterations.                                     |
| `coverage_threshold`    | Optional (default `0.95`) | `Double`| Stop discovery when this fraction of the sample is covered (0.0–1.0).       |
| `task_description`      | Optional           | `String?`      | Freeform context injected into the LLM prompt to steer regex generation.    |
| `task_dependencies`     | Optional           | `List<String>?`| Standard orchestration dependency wiring (inherited from `TaskExecutionConfig`). |

### Dependencies

DataIngest has no hard dependency on other task types, but it consumes upstream context via
`getPriorCode(agent.executionState)` (prior task outputs are logged into the transcript for reference). It uses:

- `ParsedAgent` (structured LLM response parsing) to obtain `RegexSuggestion` objects.
- Two model clients: `defaultFast` (`parsingChatter`, used for structured parsing) and `defaultSmart`
  (`defaultChatter`, used for regex generation reasoning).
- `FileSelectionUtils.filteredWalk` for glob-based file resolution.

### Token Usage Estimate

**Medium** — Each discovery iteration sends up to 20 residual log lines plus a fixed instructional prompt to the
smart model, repeated up to `max_iterations` times (default 10). Extraction itself (Phase 3) is done via regex
matching, not LLM calls, so cost scales with discovery loop count/iteration size, not with total file volume.

---

## Config & Process

### Type Configuration
- `input_files`, `sample_size`, `max_iterations`, `coverage_threshold` — fixed at task-definition time; these drive
  how many lines are sampled and how aggressively the discovery loop runs.

### Runtime Configuration
- `task_description` — optional freeform text passed into every discovery prompt as additional context.
- `task_dependencies` / `state` — standard orchestration lifecycle fields inherited from `TaskExecutionConfig`.

### Lifecycle Walkthrough

1. **Initialization** — Validates that `input_files` is non-empty (`validate()`); resolves glob patterns against the
   project root via `FileSelectionUtils.filteredWalk`, filtering to existing regular files. Throws if no files
   match.
2. **Execution**
   - **Phase 1 — Sampling:** Reads lines from resolved files (skipping blanks) until `sample_size` lines are
     collected.
   - **Phase 2 — Discovery:** Loops up to `max_iterations` times. Each iteration computes current coverage
     (`1 - residual/total`), stops early if `coverage_threshold` is reached or residuals are empty. Otherwise takes
     the first 20 residual lines, builds a prompt (including known field names to encourage reuse), and calls
     `ParsedAgent` to get a `RegexSuggestion`. The suggested regex is compiled and tested against all residuals;
     matched lines are removed from the residual pool and a `PatternRegistryItem` is registered. Invalid regex or
     zero-match suggestions are logged and skipped (loop continues, consuming an iteration).
   - **Phase 3 — Bulk Extraction:** Writes `patterns.json` (registry dump), generates `process.js` (a Node.js
     port of the same regex/field logic), then streams every resolved file line-by-line, testing each line against
     registered patterns in order and writing the first match to `data.jsonl`, `data.csv`, and `index.csv`
     (with byte-offset bookkeeping for traceability back to source).
3. **Error Handling**
   - Per-pattern: regex compilation failures are caught individually (`❌ Invalid Regex generated`) and do not abort
     the loop.
   - Zero-match suggestions are logged as warnings (`⚠️`) without registering the pattern.
   - Top-level: the entire run body is wrapped in try/catch; any unhandled exception calls `task.error(e)`, logs via
     SLF4J, writes a stack trace into the transcript file, and reports failure through `resultFn`. The transcript
     stream is always closed in a `finally` block.

---

## Integration

### Registering the task

```kotlin
val config = OrchestrationConfig(
  // ... other setup
)

val dataIngestConfig = DataIngestTask.DataIngestTaskExecutionConfigData(
  input_files = listOf("logs/**/*.log", "var/log/app-*.txt"),
  sample_size = 1000,
  max_iterations = 10,
  coverage_threshold = 0.95,
  task_description = "Parse Apache access logs and application error logs"
)

val dataIngestTask = DataIngestTask(config, dataIngestConfig)
```

### Prompt Segment (injected into orchestrator planning)

```
DataIngest - Iteratively parse unstructured logs/text into structured data
  ** Specify input_files patterns (glob) to process
  ** Iteratively discovers Regex patterns using LLM for residual data
  ** Generates structured artifacts: data.jsonl, data.csv, patterns.json, index.csv
  ** Also emits a standalone Node.js script (process.js) to apply the same rules to new inputs
  ** Efficiently handles large files via streaming extraction
```

### Discovery Prompt Template (per iteration)

```
You are a Data Engineering expert. Your goal is to write a Java/Kotlin compatible Regular Expression (Regex) to parse the following log lines.

**Context:** <task_description, if provided>

**Requirements:**
1. Use named capture groups `(?<name>...)` for all extractable fields. Note: Java regex group names must be alphanumeric only (no underscores).
2. Prefer specific character classes (e.g., `[^\]]+`) over greedy `.*`.
3. Anchor the regex with `^` and `$` if the pattern covers the whole line.
4. The regex must match the provided lines.
5. Reuse existing field names where appropriate: <known fields>

**Residual Log Lines:**
<up to 20 residual lines>
```