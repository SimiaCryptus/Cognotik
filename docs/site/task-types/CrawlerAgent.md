# CrawlerAgent

**Search, fetch, and analyze the web — with a persistent, resumable link database.**

`Side-Effect Safe` `Online & Search` `Optional Model` `File I/O`

CrawlerAgent seeds a crawl frontier from Google search or direct URLs, fetches pages via HTTP or Selenium,
scores and re-prioritizes a priority queue of discovered links, applies a pluggable processing strategy
(summarization, fact-checking, job matching) to each page, and persists the entire link graph to a JSON
state file so crawls can be paused, inspected, edited, and resumed across runs.

---

## Reality Check

**Input (execution config):**

```json
{
  "task_type": "CrawlerAgent",
  "search_query": ["kotlin coroutine structured concurrency best practices"],
  "content_queries": "Extract concrete recommendations, pitfalls, and code patterns for structured concurrency in Kotlin. Prioritize official docs and high-signal blog posts.",
  "task_description": "Research current best practices for structured concurrency in Kotlin"
}
```

**Type Configuration used for this run:**

```json
{
  "seed_method": "GoogleProxy",
  "fetch_method": "HttpClient",
  "processing_strategy": "DefaultSummarizer",
  "max_pages_per_task": 15,
  "max_depth": 2,
  "concurrent_page_processing": 3,
  "respect_robots_txt": true,
  "follow_links": true,
  "use_state_file": true
}
```

**Output (what the user sees):**

The task renders a **tabbed UI** (`TabbedDisplay`) with:

* **Seed Links** tab — a markdown-rendered numbered list of the initial search results, each with URL,
  relevance score, and tags.
* A live-updating **work area** — one linked sub-task per page being crawled, showing a `Fetching
  content...` / `Processing content...` status line, then the rendered strategy output (summary,
  extracted links table split into ✅ *Added to queue* and ⏭️ *Skipped* with reasons like "Already in
  queue" or "Disallowed by robots.txt").
* **Queue Details** tab — a markdown table of processed vs. still-queued pages with status, depth,
  relevance, and priority columns.
* A final **Markdown report** returned as the task result: per-page sections (`## 1. [Title](url)`),
  a "Remaining Queue" listing of unprocessed links, and (if `create_final_summary` triggers) an
  LLM-condensed executive summary when output exceeds `max_final_output_size`.
* A persistent transcript file (`.md`) with full crawl history, timestamps, and error stack traces.

---

## Documentation

### Configuration

**Execution Config** (`CrawlerTaskExecutionConfigData`)

| Field Name        | Required/Optional | Type         | Description                                                                                                                                                     |
|--------------------|--------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `search_query`      | Optional*           | `List<String>` | The search queries to use for Google search. Either this or `direct_urls` must be provided.                                                                    |
| `direct_urls`       | Optional*           | `List<String>` | Direct URLs to analyze. Each must be a valid `http`/`https` URL. Either this or `search_query` must be provided.                                              |
| `content_queries`   | Optional            | `Any`          | The query considered when processing content — a detailed listing of desired data, evaluation criteria, and filtering priorities used to shape each summary. |

\* At least one of `search_query` or `direct_urls` is required (enforced in `validate()`).

**Type Config** (`CrawlerTaskTypeConfig`)

| Field Name                  | Required/Optional | Type                   | Description                                                                                                    |
|-------------------------------|--------------------|-------------------------|------------------------------------------------------------------------------------------------------------------|
| `seed_method`                 | Optional            | `SeedMethod`             | `GoogleProxy` or `DirectUrls`. Default: `GoogleProxy`.                                                          |
| `fetch_method`                 | Optional            | `FetchMethod`            | `HttpClient` or `Selenium`. Default: `HttpClient`.                                                              |
| `processing_strategy`          | Optional            | `ProcessingStrategyType` | `DefaultSummarizer`, `FactChecking`, or `JobMatching`. Default: `DefaultSummarizer`.                            |
| `allowed_domains`              | Optional            | `String?`                | Whitespace-separated domains/URL prefixes restricting crawl scope.                                              |
| `respect_robots_txt`           | Optional            | `Boolean`                | Whether to honor `robots.txt`. Default: `true`.                                                                 |
| `max_pages_per_task`           | Optional            | `Int`                    | Max pages processed per run. Default: `30`.                                                                     |
| `max_depth`                    | Optional            | `Int`                    | Max crawl depth from seeds. Default: `3`.                                                                        |
| `max_queue_size`               | Optional            | `Int`                    | Max frontier queue size. Default: `100`.                                                                         |
| `concurrent_page_processing`   | Optional            | `Int`                    | Concurrent pages processed. Default: `3`.                                                                        |
| `max_final_output_size`        | Optional            | `Int`                    | Max characters in the final summary output. Default: `30000`.                                                   |
| `min_content_length`           | Optional            | `Int`                    | Minimum content length (chars) to process a page. Default: `500`.                                               |
| `follow_links`                 | Optional            | `Boolean`                | Whether to auto-follow links found on analyzed pages. Default: `true`.                                          |
| `allow_revisit_pages`          | Optional            | `Boolean`                | Whether to allow re-crawling already-seen pages. Default: `false`.                                              |
| `create_final_summary`         | Optional            | `Boolean`                | Whether to generate an LLM-condensed final summary. Default: `true`.                                            |
| `crawl_state_file`             | Optional            | `String?`                | Path to JSON persistence file; defaults to `.websearch/crawl_state.json` relative to task root.                |
| `use_state_file`               | Optional            | `Boolean`                | Whether to load/save crawl state at all. Default: `true`.                                                       |

### Dependencies

* Delegates page fetching to `FetchStrategy` implementations (`FetchMethod.HttpClient` / `Selenium`).
* Delegates seeding to `SeedMethod` implementations (`SeedMethod.GoogleProxy` / `DirectUrls`).
* Delegates content transformation to `PageProcessingStrategy` implementations (`ProcessingStrategyType`:
  `DefaultSummarizer`, `FactChecking`, `JobMatching`).
* Uses `ChatAgent` + a resolved `ChatInterface`/`ApiChatModel` to generate the final condensed summary when
  the raw analysis exceeds `max_final_output_size`.
* No hard dependency on other `TaskType`s in the orchestration graph — it is a leaf/standalone research
  task, but its Markdown output is typically consumed as context by downstream planning/reporting tasks.

### Token Usage

**Medium–High.** Each fetched page can trigger one strategy-processing LLM call (summarization / fact
extraction), and with `concurrent_page_processing` × `max_pages_per_task` running in a single task, total
token spend scales linearly with pages processed. An additional condensation pass (`createFinalSummary`)
fires only when accumulated output exceeds `max_final_output_size`.

---

## Config & Process

### Type Configuration vs. Runtime Configuration

* **Type Configuration** (`CrawlerTaskTypeConfig`) is set once per task-type instance and controls crawl
  *mechanics*: fetch/seed/processing strategies, depth/queue/page limits, robots.txt compliance, domain
  allow-listing, and state-file persistence behavior.
* **Runtime/Execution Configuration** (`CrawlerTaskExecutionConfigData`) is supplied per invocation and
  controls crawl *intent*: which queries or URLs to start from, and what content to extract
  (`content_queries`).

### Lifecycle

1. **Initialization**
    * Validates that either `search_query` or `direct_urls` is present, and that `direct_urls` entries are
      well-formed (`validate()`).
    * Clamps `max_depth` and `min_content_length` to non-negative values.
    * Creates `.websearch` output directory.
    * If `use_state_file` is enabled, resolves and loads `CrawlState` from disk (`resolveCrawlStateFile` /
      `loadCrawlState`), increments `run_count`, records new `search_queries`, and **restores** previously
      queued/errored/seen links into the in-memory `pageQueue`/`seenUrls` via `restoreFromCrawlState`.
    * Resolves seed items through the configured `SeedMethod` strategy; filters blacklisted domains and
      robots.txt-disallowed URLs before enqueueing.

2. **Execution**
    * Runs a bounded loop (`shouldContinue`, capped at 1000 iterations) that keeps `concurrent_page_processing`
      sub-tasks active, pulling the highest-priority (`calculatePriority()`) link from a `PriorityQueue` each
      time capacity is available.
    * Each page: applies robots.txt crawl-delay, fetches content via `FetchStrategy`, skips if shorter than
      `min_content_length`, then hands content to the `PageProcessingStrategy` for classification
      (`Error` / `Irrelevant` / `OK`), extraction, and optional queue re-prioritization
      (`reprioritizeQueue`).
    * New links extracted from `OK` pages are deduplicated, filtered (blacklist, robots.txt, depth/queue
      limits, revisit policy), and re-enqueued with slight randomized jitter to break priority ties.
    * The strategy can request early termination (`shouldContinueCrawling`) once it judges the goal
      satisfied.
    * Crawl state is synced and saved every 5 completed pages and again at the end of the crawl phase
      (`syncInMemoryStateToCrawlState` + `saveCrawlState`), when `use_state_file` is enabled.

3. **Error Handling**
    * Per-page fetch/processing exceptions are caught individually — logged, counted (`errorCount`), and
      recorded on the `LinkData`/`CrawlLinkEntry` (`status = "error"`), without aborting the whole crawl.
      Errored URLs are re-queued for retry on a future run via `restoreFromCrawlState`.
    * Crawling stops early if `errorCount` exceeds `maxPages / 2`.
    * A top-level `try/catch` around `innerRun` writes a "Fatal Error" section (with stack trace) to the
      transcript, marks the task as errored, and returns a descriptive error string rather than throwing.
    * Final-output generation failures fall back from strategy-generated output to a truncation/LLM-summary
      fallback (`createFinalSummary`).

---

## Integration

### Registering in an `OrchestrationConfig`

```kotlin
import com.simiacryptus.cognotik.crawl.CrawlerAgentTask
import com.simiacryptus.cognotik.crawl.fetch.FetchMethod
import com.simiacryptus.cognotik.crawl.processing.ProcessingStrategyType
import com.simiacryptus.cognotik.crawl.seed.SeedMethod
import com.simiacryptus.cognotik.plan.tools.TaskType

val crawlerTypeConfig = CrawlerAgentTask.CrawlerTaskTypeConfig(
    seed_method = SeedMethod.GoogleProxy,
    fetch_method = FetchMethod.HttpClient,
    processing_strategy = ProcessingStrategyType.DEFAULT,
    max_pages_per_task = 20,
    max_depth = 2,
    respect_robots_txt = true,
    use_state_file = true,
)

val crawlerExecutionConfig = CrawlerAgentTask.CrawlerTaskExecutionConfigData(
    search_query = listOf("structured concurrency kotlin best practices"),
    content_queries = "Extract actionable recommendations and code patterns.",
    task_description = "Research structured concurrency practices",
)

// Registered task type available to the planner as CrawlerAgentTask.CrawlerAgent
val availableTaskTypes: List<TaskType<*, *>> = listOf(
    CrawlerAgentTask.CrawlerAgent,
    // ...other task types
)
```

### Prompt Segment (injected into the planning LLM)

The task advertises its capabilities to the orchestrating agent via `promptSegment()`:

```text
CrawlerAgent - Search Google, fetch top results, and analyze content
** Specify the search query
** Or provide direct URLs to analyze
** Specify a detailed query/analysis prompt to guide content processing
** Choose a processing strategy: DefaultSummarizer, FactChecking, or JobMatching
** Results will be saved to .websearch directory for future reference
** Links found in analysis can be automatically followed for deeper research
** Crawl state is persisted to a JSON file for resumable/editable ongoing crawls
** Using processing strategy: <StrategyName> - <strategy.description>   [when non-default]
```