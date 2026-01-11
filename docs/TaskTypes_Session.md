# Session

## CommandSession

Execute commands in a stateful, interactive session

Creates and manages a persistent command-line session (e.g., bash, python).
This allows for stateful interactions where commands can build on the results of previous ones.
<ul>
    <li><b>Start any interactive process:</b> Specify the command to run</li>
    <li><b>Send inputs:</b> Provide a list of commands to be executed sequentially in the session.</li>
    <li><b>Stateful Sessions:</b> Reuse sessions by providing a `sessionId`. The environment (variables, current directory) persists between tasks using the same ID.</li>
    <li><b>Manage Session Lifecycle:</b> Sessions can be explicitly closed or will be cleaned up automatically.</li>
    <li><b>TTY Support:</b> Set `tty` to true to allocate a pseudo-terminal (requires pty4j), enabling UI applications and TTY-dependent tools.</li>
</ul>

#### Planner Prompt Segment

```text
CommandSession - Create and manage a stateful interactive terminal session
** Specify the command to start an interactive session, or sessionId to reuse an existing one
** Provide inputs to send to the session
** Session persists between commands for stateful interactions

System Information:
- OS: Linux 6.14.0-37-generic (amd64)
- Working Directory: /home/andrew/code/Cognotik
- Available Tools: 

Active Sessions:

```

#### Default Execution Configuration

```json
{
  "task_type" : "CommandSession",
  "command" : [ "bash", "-i" ],
  "inputs" : [ ],
  "sessionId" : null,
  "timeout" : 30000,
  "idle_timeout" : 2000,
  "tty" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "CommandSession"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "CommandSession",
  "name" : "CommandSession",
  "model" : null
}
```

---

## JdbcSession

Execute SQL queries via JDBC

Executes SQL statements against a database using JDBC.
<ul>
    <li><b>Connection:</b> Requires `url`. Optional `user`, `password`, and `driver`.</li>
    <li><b>Stateful:</b> Use `sessionId` to keep connections open across multiple tasks (useful for transactions or temp tables).</li>
    <li><b>Output:</b> Returns results as Markdown tables.</li>
</ul>

#### Planner Prompt Segment

```text
JdbcSession - Execute SQL queries via JDBC in a stateful session.
** Specify the `url`, `user`, and `password` to start a new session.
** Use `sessionId` to reuse an existing connection for transactions or subsequent queries.
** Provide a list of `sql` statements to execute.

Active Sessions:
None
```

#### Default Execution Configuration

```json
{
  "task_type" : "JdbcSession",
  "url" : null,
  "user" : null,
  "password" : null,
  "driver" : null,
  "sql" : [ ],
  "sessionId" : null,
  "closeSession" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "JdbcSession"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "JdbcSession",
  "name" : "JdbcSession",
  "model" : null
}
```

---

