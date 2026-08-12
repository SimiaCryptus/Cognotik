# MCPTool

**Execute tools from Model Context Protocol (MCP) servers directly inside an orchestration plan.**

`Side-Effect Variable` · `Network Required` · `Retryable`

MCPTool bridges the orchestrator to any MCP-compatible server: it discovers a named tool, validates it exists,
invokes it with structured arguments, and folds the JSON result back into the task graph — with optional automatic
retry and full transcript logging.

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "MCPTool",
  "task_description": "Fetch current weather for Seattle via weather-mcp server",
  "server_name": "weather-mcp",
  "tool_name": "get_forecast",
  "tool_arguments": {
    "location": "Seattle, WA",
    "units": "metric"
  },
  "timeout_seconds": 30,
  "task_dependencies": []
}
```

**Rendered Output**

The UI panel shows:

* `## Executing MCP tool: get_forecast` header
* `Server: weather-mcp` line
* An expandable **Arguments** block with pretty-printed JSON
* Progress line: *"Establishing connection to MCP server..."* → updates in place to *"Connection established to weather-mcp"*
* `Executing tool with timeout of 30 seconds...` followed by `Tool executed in 842ms`
* A green success line: *"Tool execution completed successfully"*
* An expandable **Result** block containing the full JSON envelope (`status`, `server`, `tool`, `arguments`, `result`, `execution_time_ms`, `timestamp`)
* A downloadable transcript file (`.md`) capturing the entire connection/execution/result narrative, if transcript generation is enabled

On failure without retry: a red inline error block plus exception rethrow, terminating the task branch.

---

## Documentation Tab

### Configuration Table

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `server_name` | Optional* | `String?` | The name of the MCP server to connect to. Falls back to the type config's `default_server`; if neither is set, execution throws. |
| `tool_name` | Required | `String?` | The name of the tool to execute on the MCP server. |
| `tool_arguments` | Optional | `Map<String, Any>?` | Arguments to pass to the MCP tool as a JSON object. Defaults to an empty map. |
| `timeout_seconds` | Optional | `Int?` | Timeout in seconds for the tool execution. Defaults to `30`, or the type config's `default_timeout`. |
| `task_description` | Optional | `String?` | Inherited from `TaskExecutionConfig`; human-readable task description. |
| `task_dependencies` | Optional | `MutableList<String>?` | Inherited from `TaskExecutionConfig`; IDs of prerequisite tasks whose results are injected via `getPriorCode`. |

\* Effectively required unless `default_server` is set on the task type configuration.

### Dependencies

MCPTool has no compile-time dependency on other Task types. At runtime it can consume outputs from prior tasks via
`task_dependencies`, which are surfaced through `getPriorCode()` as a "Prior Task Results" preamble. It relies on
`MCPServerRegistry` (from `com.simiacryptus.cognotik.util.mcp`) to resolve and connect to a registered MCP client —
this is an external infrastructure dependency, not another Task.

### Token Usage

**Low** — MCPTool itself does not construct LLM prompts for its core execution; the prompt segment only advertises
its existence to the planning LLM. Token cost is driven entirely by how much of the JSON arguments/result is echoed
back into the orchestrator's context (typically small, tool-call-sized payloads).

---

## Config & Process Tab

### Type Configuration (`MCPToolTaskTypeConfig`)

| Field | Type | Default | Purpose |
|---|---|---|---|
| `default_server` | `String?` | `null` | Fallback server name when execution config omits `server_name`. |
| `default_timeout` | `Int` | `30` | Fallback timeout in seconds. |
| `auto_retry` | `Boolean` | `false` | Enables automatic retry on retryable errors. |
| `generate_transcript` | `Boolean` | `true` | Writes a Markdown transcript file of the connection/execution flow. |
| `max_retries` | `Int` | `3` | Cap on retry attempts. |
| `retry_delay_ms` | `Long` | `1000` | Base delay between retries. |
| `exponential_backoff` | `Boolean` | `true` | If true, delay doubles each attempt (`retry_delay_ms * 2^(n-1)`). |

### Runtime Configuration (`MCPToolTaskExecutionConfigData`)

See the Configuration Table above (`server_name`, `tool_name`, `tool_arguments`, `timeout_seconds`, plus inherited
`task_description`/`task_dependencies`).

### Lifecycle Walkthrough

**Initialization**
- Resolves `serverName` (execution config → type config `default_server`) and `toolName`; throws
  `IllegalStateException` immediately if either is missing.
- Opens a transcript `FileOutputStream` if `generate_transcript` is enabled, and renders the header/server info plus
  an expandable arguments block in the UI.

**Execution**
- Fetches the MCP client from `MCPServerRegistry`; throws if the server isn't registered.
- Connects the client if not already connected, updating the UI progress buffer in place.
- Lists available tools and matches by name; throws `IllegalArgumentException` (listing available tool names) if not
  found.
- Invokes `client.executeTool(toolName, arguments, timeout, TimeUnit.SECONDS)`, timing the call.
- Wraps the raw result into a structured envelope (`status`, `server`, `tool`, `arguments`, `result`,
  `execution_time_ms`, `timestamp`) and passes it to `resultFn` as JSON.
- Marks task state `Completed` and calls `task.complete()`.

**Error Handling**
- All exceptions from connection, listing, or tool invocation are caught, logged, and written to the transcript with
  a full stack trace.
- `TimeoutException` from the executor is rewrapped with a clearer timeout message.
- `shouldRetry(e)` classifies errors as retryable if they are `SocketTimeoutException`, `TimeoutException`,
  `IOException`, or contain "connection"/"timeout"/"unavailable" in the message.
- If `auto_retry` is true and the error is retryable, `handleRetry` runs up to `max_retries` attempts with
  (optionally exponential) backoff delay, re-invoking `run(...)` each time; it records each failed attempt in the UI.
- If retries are disabled, not applicable, or exhausted, the transcript stream is closed, state is set to
  `Completed`, `task.error(e)` renders the failure, and the exception is rethrown to the orchestrator.

---

## Integration Tab

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings ...
    taskSettings = mapOf(
        MCPToolTask.MCPTool.name to MCPToolTask.MCPToolTaskTypeConfig(
            default_server = "weather-mcp",
            default_timeout = 45,
            auto_retry = true,
            max_retries = 3,
            retry_delay_ms = 1000,
            exponential_backoff = true,
            generate_transcript = true
        )
    )
)

// Example execution config produced/consumed by the planner:
val execConfig = MCPToolTask.MCPToolTaskExecutionConfigData(
    server_name = "weather-mcp",
    tool_name = "get_forecast",
    tool_arguments = mapOf("location" to "Seattle, WA", "units" to "metric"),
    timeout_seconds = 30,
    task_description = "Fetch current weather for Seattle"
)
```

### Prompt Segment (as injected into planning LLM)

```text
MCPTool - Execute tools from Model Context Protocol (MCP) servers
** Specify the MCP server name and tool to execute
** Provide tool arguments as a JSON object
** Configure timeout and retry behavior
** Supports integration with external MCP-compatible services
```