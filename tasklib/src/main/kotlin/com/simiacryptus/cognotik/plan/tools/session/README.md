# Session Tools

This package provides a set of stateful task implementations designed for persistent interactions. Unlike standard
tasks, session-based tasks can maintain state (such as environment variables, database transactions, or browser
contexts) across multiple execution steps by using a `sessionId`.

## Tools Overview

### CommandSession

Creates and manages a persistent command-line session (e.g., `bash`, `python`). This allows for stateful interactions
where commands can build on the results of previous ones.

**Key Features:**

- **Interactive Processes:** Start any interactive process by specifying the base command.
- **Sequential Inputs:** Send a list of commands to be executed sequentially in the session's standard input.
- **State Persistence:** The environment (variables, current directory) persists between tasks using the same
  `sessionId`.
- **TTY Support:** Optional pseudo-terminal allocation (via `pty4j`) for tools requiring a TTY or providing colored
  output.

**Configuration Parameters:**

| Parameter      | Description                                                              | Default          |
|----------------|--------------------------------------------------------------------------|------------------|
| `command`      | The command to start the interactive session (e.g. `["bash", "-i"]`).    | `["bash", "-i"]` |
| `inputs`       | List of strings to send to the session's standard input.                 | `[]`             |
| `sessionId`    | Optional ID to reuse an existing session across multiple tasks.          | `null`           |
| `timeout`      | Maximum time in milliseconds to wait for all commands to complete.       | `30000`          |
| `idle_timeout` | Maximum time in milliseconds to wait for output after a command is sent. | `2000`           |
| `tty`          | Whether to allocate a pseudo-terminal.                                   | `false`          |

---

### JdbcSession

Executes SQL statements against a database using JDBC. It supports maintaining connections for transactions or temporary
state.

**Key Features:**

- **Flexible Connectivity:** Supports various databases via JDBC URL, user, and password.
- **Transaction Support:** Use `sessionId` to keep connections open across multiple tasks, enabling multi-step
  transactions or use of temporary tables.
- **Markdown Output:** Query results are automatically formatted as Markdown tables for easy reading and processing.

**Configuration Parameters:**

| Parameter      | Description                                                          |
|----------------|----------------------------------------------------------------------|
| `url`          | JDBC Connection URL (e.g., `jdbc:postgresql://localhost:5432/mydb`). |
| `user`         | Database User.                                                       |
| `password`     | Database Password.                                                   |
| `driver`       | Optional JDBC Driver Class Name (e.g., `org.postgresql.Driver`).     |
| `sql`          | List of SQL statements to execute.                                   |
| `sessionId`    | Session ID for reusing existing connections.                         |
| `closeSession` | Whether to close the session after execution.                        |

---

### SeleniumSession

Automates browser interactions using Selenium WebDriver. This tool is optimized for web scraping, testing, and
automation tasks that require a real browser environment.

**Key Features:**

- **Headless Automation:** Uses headless Chrome for efficient background execution.
- **JavaScript Execution:** Execute arbitrary JS commands in the browser context.
- **HTML Optimization:** Includes advanced HTML scrubbing and simplification (via `HtmlSimplifier`) to reduce token
  usage when passing page content to LLMs.
- **Transcripts:** Can generate detailed session transcripts for debugging and auditing.

**Configuration Parameters:**

| Parameter           | Description                                                          | Default |
|---------------------|----------------------------------------------------------------------|---------|
| `url`               | The URL to navigate to (optional if reusing session).                | `""`    |
| `commands`          | JavaScript commands to execute in sequence.                          | `[]`    |
| `sessionId`         | Session ID for reusing existing sessions.                            | `null`  |
| `timeout`           | Timeout in milliseconds for commands.                                | `30000` |
| `closeSession`      | Whether to close the session after execution.                        | `false` |
| `simplifyStructure` | Whether to simplify the HTML structure by combining nested elements. | `true`  |
| `createTranscript`  | Whether to create a transcript file of the session.                  | `false` |

## Session Management

- **Concurrency:** Most session types limit the number of concurrent active sessions (typically 10) to manage system
  resources.
- **Cleanup:** Inactive or dead processes/connections are automatically cleaned up during new task initialization.
- **Persistence:** Sessions are stored in-memory and are tied to the lifecycle of the application server.