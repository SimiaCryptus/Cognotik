# CommandSession

**Category:** Session &nbsp;·&nbsp; **Badges:** `Stateful` `Destructive` `Shell Access`

Spin up a persistent, stateful interactive terminal (bash, python, or any interactive process) and drive it with
a sequence of stdin inputs — across multiple task calls, if given a `sessionId`.

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "CommandSession",
  "command": ["python3", "-i"],
  "inputs": [
    "import sys; print(sys.version)",
    "x = 21 * 2",
    "print(f'answer={x}')"
  ],
  "sessionId": "analysis-session-1",
  "timeout": 30000,
  "idle_timeout": 2000,
  "tty": false,
  "task_description": "Start a Python REPL and compute a quick value.",
  "task_dependencies": []
}
```

**Rendered Output**

The UI renders a "Command Session Results" header, followed by:

- An initial info block: the resolved command, session ID, and per-command timeout.
- For each input in `inputs`, an `### Input N` block containing the literal input in a fenced code block.
- Immediately following, an `Output:` block containing up to 10,000 characters of captured stdout/stderr in a
  fenced code block.
- A separate transcript file (per-task) is written alongside the UI stream, wrapping each output in a collapsible
  `<details>`/`<summary>` block titled "Output for Input N".
- On success (in auto-fix mode) the task completes with "Command session finished successfully."; otherwise a
  "Run Commands" button and an accept/complete footer are shown for manual confirmation.

---

## Documentation

### Configuration

| Field Name       | Required/Optional | Type            | Description                                                                                   |
|------------------|--------------------|------------------|-----------------------------------------------------------------------------------------------|
| `command`        | Optional (default `["bash", "-i"]`) | `List<String>`  | The command to start the interactive session (e.g. `["bash", "-i"]` or `["python3", "-i"]`). |
| `inputs`         | Optional (default `[]`) | `List<String>`  | List of strings to send to the session's standard input, executed sequentially.               |
| `sessionId`      | Optional            | `String?`        | ID to reuse an existing session across multiple task calls, preserving state (vars, cwd).      |
| `timeout`        | Optional (default `30000`) | `Long`          | Maximum time in milliseconds to wait for all commands to complete.                            |
| `idle_timeout`   | Optional (default `2000`) | `Long`          | Maximum time in milliseconds to wait for output after a command is sent before considering it idle. |
| `tty`            | Optional (default `false`) | `Boolean`       | Whether to allocate a pseudo-terminal (via pty4j) — useful for interactive tools or colored/curses output. |
| `task_description` | Optional         | `String?`        | A description of what this specific task instance is intended to achieve.                      |
| `task_dependencies` | Optional        | `List<String>?`  | IDs of tasks that must complete before this one.                                              |

### Dependencies

No direct compile-time dependency on other `Task` implementations. This task is self-contained but relies on:

- `com.pty4j.PtyProcessBuilder` for optional TTY-mode process spawning.
- Shared static session registry (`_activeSessions`), keyed by `orchestrationConfig.sessionId`, allowing multiple
  `CommandSessionTask` invocations within the same orchestration session to share live processes via `sessionId`.

### Token Usage

**Low.** The prompt segment is a short static system-information block plus a live session listing; there is no
LLM-generated content beyond whatever surrounding orchestration prompt includes this task's description. Output
volume to the UI/transcript can be large (process stdout), but this does not consume LLM tokens.

---

## Config & Process

### Type Configuration

- `CommandSession` `TaskType`: name `"CommandSession"`, category `"Session"`, execution config class
  `CommandSessionTaskExecutionConfigData`, settings class `TaskTypeConfig` (no custom per-type settings).

### Runtime Configuration

- `CommandSessionTaskExecutionConfigData`: command, inputs, sessionId, timeout, idle_timeout, tty (see table above),
  plus inherited `task_description`, `task_dependencies`, and `state` (`TaskState`) for internal tracking.
- Global constants: `MAX_SESSIONS = 10` concurrent sessions per orchestration session; `TIMEOUT_MS = 30000` default.

### Lifecycle

**Initialization**
- On `run()`, cleans up inactive sessions (`cleanupInactiveSessions()`), removing entries whose process has died or
  whose liveness check throws.
- Enforces `MAX_SESSIONS` cap when starting a brand-new (non-reused) session.
- Resolves an existing `SessionState` by `sessionId` if provided, otherwise builds a new process:
  - If `tty = true`, attempts `PtyProcessBuilder`; on failure, falls back to a standard `ProcessBuilder` with
    merged stderr/stdout.
  - Otherwise directly uses `ProcessBuilder` with `redirectErrorStream(true)`.
  - Registers the new `SessionState` under `sessionId` if one was supplied (unkeyed sessions are ephemeral and not
    reusable).
- Each `SessionState` spins up a daemon monitor thread that continuously drains the process's stdout into an
  internal buffer.

**Execution**
- If `orchestrationConfig.autoFix` is true, the task executes immediately; otherwise it renders a plan (command,
  session ID, list of inputs) and waits for the user to click "Run Commands" or accept/complete without running.
- For each input string: writes it to the process's stdin via `PrintWriter.println`, then polls the session's
  output buffer until either `timeout` elapses or output goes idle for `idle_timeout` ms.
- Streams both the input and captured output (truncated to 10,000 chars) to the UI task and a transcript file.

**Error Handling**
- Per-input failures (exceptions during write/read) are caught individually, captured as an `"Error: ${message}"`
  string in place of output, and execution continues to subsequent inputs.
- A top-level `try/catch` wraps the whole action; any uncaught exception is logged, reported via `task.error(e)`,
  appended to the transcript as a stack trace block, and (if `shouldComplete`) surfaces the error through
  `resultFn`.
- The transcript stream is always closed in a `finally` block.
- Dead or errantly-checked sessions are forcibly destroyed (`process.destroyForcibly()`), and their monitor threads
  are interrupted during cleanup.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings
    autoFix = true,
    sessionId = "my-orchestration-session"
)

val task = CommandSessionTask(
    orchestrationConfig,
    CommandSessionTask.CommandSessionTaskExecutionConfigData(
        command = listOf("python3", "-i"),
        inputs = listOf(
            "import sys; print(sys.version)",
            "print(21 * 2)"
        ),
        sessionId = "analysis-session-1",
        timeout = 30000,
        idle_timeout = 2000,
        tty = false,
        task_description = "Start a Python REPL and compute a quick value."
    )
)

task.run(agent, messages, sessionTask, resultFn, orchestrationConfig)
```

### Prompt Segment (injected into LLM context)

```
CommandSession - Create and manage a stateful interactive terminal session.
- Use this for running shell commands, interactive scripts (Python, Node), or managing long-running processes.
- Specify 'command' to start a new session (e.g., ["python3", "-i"]).
- Provide 'inputs' as a list of strings to send to the session's stdin.
- Use 'sessionId' to maintain state (variables, directory) across multiple task calls.

System Information:
- OS: <os.name> <os.version> (<os.arch>)
- Working Directory: <user.dir>

Active Sessions:
  ** Session <id> (<pendingBytes> bytes pending output, alive=<true|false>)
```