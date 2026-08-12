# SeleniumFetch

**Headless-Chrome page capture: screenshot, DOM, console, and network log in one shot.**

`Side-Effect Safe` · `Online` · `No Vision Required`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "SeleniumFetch",
  "target": "https://example.com/dashboard",
  "diagnostic_delay_seconds": 2,
  "page_load_timeout_seconds": 30,
  "viewport_width": 1920,
  "viewport_height": 1080,
  "task_description": "Capture rendered state of the dashboard after async widgets load"
}
```

**Rendered Output (UI)**

The task produces a tabbed display (`TabbedDisplay`) with three tabs:

- **Overview** — a bullet-list summary (target, resolved URL, delay, timeout, viewport) rendered as markdown.
- **Load** — status messages ("Navigating to `<url>`...", "✅ Page loaded", settle-delay notice, or a "⚠️ timed out" warning).
- **Artifacts** — inline links and an embedded `<img>` thumbnail for the screenshot, plus links to the saved `.html`, `.console.log`, and `.network.log` files, each annotated with size/entry counts (e.g. "📸 Screenshot saved", "🖥️ Console log saved (12 entries)").

A final Markdown transcript file (`.md`) is written alongside the artifacts, summarizing the target, resolved URL, elapsed time, and a bulleted artifact manifest. On failure, an "## Error" section with exception type/message/stack trace is appended to the transcript instead.

---

## Documentation

### Configuration

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `target` | Required | `String` | URL or local file path to load. Local paths resolve against the project root if not absolute; supports `file://` URIs for explicit file access. |
| `diagnostic_delay_seconds` | Optional (default `1`) | `Long` | Seconds to wait after page load completes before capturing diagnostics, allowing async content to render. |
| `page_load_timeout_seconds` | Optional (default `30`) | `Long` | Maximum seconds to wait for page load before proceeding with partial-state capture. |
| `viewport_width` | Optional (default `1920`) | `Int` | Browser viewport width in pixels. |
| `viewport_height` | Optional (default `1080`) | `Int` | Browser viewport height in pixels. |

Validation rules (enforced in `validate()`):
- `target` must be non-blank after trimming.
- `diagnostic_delay_seconds` must be `>= 0`.
- `page_load_timeout_seconds` must be `> 0`.
- `viewport_width` and `viewport_height` must both be `> 0`.

### Dependencies

No hard dependencies on other Task types are declared in code — `SeleniumFetchTask` is a standalone leaf task. It relies on the standard `AbstractTask`/`TaskOrchestrator` framework plumbing (`SessionTask`, `TabbedDisplay`, `resultFn`) shared across all tasks, and on the Selenium Java bindings (`ChromeDriver`) as an external dependency.

### Token Usage

**Low** — This task does not construct or send any LLM prompts itself; its `promptSegment()` is a short static description used only when the orchestrator plans task usage. All heavy work (browser automation, screenshotting, log capture) happens outside the LLM, so token cost is limited to the fixed prompt-segment text plus whatever summary/result text is echoed back into the conversation.

---

## Config & Process

### Type Configuration (fixed at task-type registration)

- `name = "SeleniumFetch"`, `category = "Online"`
- `taskClass = SeleniumFetchTask::class.java`
- `executionConfigClass = SeleniumFetchTaskExecutionConfigData::class.java`
- `taskSettingsClass = TaskTypeConfig::class.java` (no custom task-level settings beyond the shared defaults)

### Runtime Configuration (per-invocation, from `SeleniumFetchTaskExecutionConfigData`)

- `target`, `diagnostic_delay_seconds`, `page_load_timeout_seconds`, `viewport_width`, `viewport_height` — see table above.
- Inherited: `task_description`, `task_dependencies`, `state`.

### Lifecycle

**Initialization**
1. `validate()` trims `target` and checks required constraints (non-blank target, non-negative delay, positive timeout/viewport).
2. On `run()`, the execution config is fetched; if absent, a configuration-error message is logged and completed immediately.
3. A Markdown transcript file is opened for writing (`<task>.md`), and a `TabbedDisplay` (`Overview` / `Load` / `Artifacts`) is created.

**Execution**
1. `resolveTarget()` normalizes the target: `http(s)://` URLs pass through unchanged; `file:` URIs (all common variants — `file:///abs`, `file://host/abs`, `file:/abs`) are unwrapped to a filesystem path; anything else is treated as a path resolved against the project root. Non-existent or unreadable local paths raise `IllegalArgumentException`.
2. `createDriver()` builds a headless `ChromeDriver` with a fixed set of hardening flags (`--headless=new`, `--no-sandbox`, `--disable-gpu`, etc.), a custom user agent, insecure-cert acceptance, and enabled `BROWSER`/`PERFORMANCE`/`DRIVER` logging preferences.
3. Page-load/script timeouts are set from config; `driver.get(targetUrl)` is invoked. A `TimeoutException` here is caught and logged as a warning (diagnostics are still attempted); other `WebDriverException`s are re-thrown.
4. The task polls `document.readyState` every 100ms until `"complete"` or the timeout deadline is reached.
5. If `diagnostic_delay_seconds > 0`, the thread sleeps for that duration to let async content settle.
6. Four artifacts are captured independently, each wrapped in its own try/catch so one failure doesn't abort the others:
   - **Screenshot** (`.png`) via `TakesScreenshot`, copied into the resolved user file and embedded as a clickable thumbnail.
   - **HTML** (`.html`) via `driver.pageSource`.
   - **Console log** (`.console.log`) via `driver.manage().logs().get(LogType.BROWSER)`.
   - **Network log** (`.network.log`) via `driver.manage().logs().get(LogType.PERFORMANCE)`, formatted as raw JSON performance events (or a placeholder message if unavailable).
7. A final summary block (target, resolved URL, elapsed time, artifact manifest) is written to the transcript and returned via `resultFn`.

**Error Handling**
- `IllegalArgumentException` (bad/missing target), `WebDriverException` (browser/navigation failures), and generic `Exception` are each caught at the top level and routed to a shared `handleError()`:
  - Reports the error to the UI (`task.error(e)`).
  - Appends a `## Error` section (exception type, message, stack trace) to the transcript.
  - Calls `task.safeComplete(...)` with a failure message.
  - Returns a formatted `# SeleniumFetch Error` string via `resultFn`.
- A `finally` block always attempts `driver?.quit()` (logging any quit failure) and closes the transcript stream, regardless of success or failure — no retry/rollback logic is implemented; each run is a single best-effort attempt.

---

## Integration

### Registering the task

```kotlin
import com.simiacryptus.cognotik.plan.tools.online.SeleniumFetchTask

val orchestrationConfig = OrchestrationConfig(
    // ... other configuration ...
    taskTypes = listOf(
        // ... other task types ...
        SeleniumFetchTask.SeleniumFetch
    )
)
```

### Example execution config

```kotlin
val fetchConfig = SeleniumFetchTask.SeleniumFetchTaskExecutionConfigData(
    target = "https://example.com/dashboard",
    diagnostic_delay_seconds = 2,
    page_load_timeout_seconds = 30,
    viewport_width = 1920,
    viewport_height = 1080,
    task_description = "Capture rendered state of the dashboard after async widgets load"
)
```

### Prompt segment injected into the LLM

```text
SeleniumFetch - Load a URL or local file in headless Chrome and capture diagnostics
  ** Specify 'target' as a URL (http/https) or a local file path / file:// URI
  ** Configure diagnostic_delay_seconds (default: 1) for post-load settle time
  ** Captures screenshot (.png), console log, network log, and rendered HTML
  ** Use this when you need to diagnose a page, capture a visual snapshot,
     inspect console errors, or examine the post-render DOM
```