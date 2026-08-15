# Cognotik CLI

A small set of foreground, one-shot command line tools built on the Cognotik platform, plus a single
launcher script (`bin/cognotik`) that finds or fetches the right jar and dispatches to them.

```
cognotik <action> [options] [--] [args...]
```

| Action              | Main class                                        | What it does                                   |
|---------------------|----------------------------------------------------|-------------------------------------------------|
| `fileserver`, `fs`  | `com.simiacryptus.cognotik.cli.FileServerCli`      | Serves a directory over HTTP (browse/edit/exec) |
| `keys`              | `com.simiacryptus.cognotik.cli.ApiKeysCli`         | Configure provider API keys                     |
| `docops`            | `com.simiacryptus.cognotik.cli.DocOpsCli`          | Render markdown frontmatter specs into files    |
| `autofix`           | `com.simiacryptus.cognotik.cli.AutoFixCli`         | Wrap a command and iteratively fix its errors   |

All four tools run in the foreground, print human-readable progress, and exit with a meaningful
code — none of them installs a daemon or leaves anything running in the background.

## Installing / running

You do **not** need a source checkout to use this. `bin/cognotik` works two ways:

* **Source checkout** — if it finds a Gradle project root above itself (a `cli/` directory next to
  `settings.gradle[.kts]`), it builds/reuses `cli/build/libs/*-all.jar` via `:cli:shadowJar`.
* **Standalone** — copied anywhere else on disk, it downloads the latest (or a pinned) `*-all.jar`
  release asset from GitHub into a local cache and runs that instead.

```shell
# From a checkout:
./cli/bin/cognotik docops plan

# Standalone (drop the script anywhere on your PATH):
curl -sSL -o cognotik https://raw.githubusercontent.com/SimiaCryptus/Cognotik/refs/heads/main/cli/bin/cognotik
chmod +x cognotik
./cognotik fileserver .
```

### Launcher options

| Option        | Meaning                                                              |
|---------------|------------------------------------------------------------------------|
| `--build`     | Force a rebuild (`:cli:shadowJar`). Source checkout only.               |
| `--no-build`  | Never build or download; fail if no jar is available.                   |
| `--update`    | Re-download the release jar (standalone mode).                          |
| `--where`     | Print the jar/mode that would be used, then exit.                       |
| `-h`, `--help`| Show usage.                                                             |

### Launcher environment variables

| Variable            | Meaning                                                                 |
|---------------------|--------------------------------------------------------------------------|
| `JAVA_HOME`         | If set, `$JAVA_HOME/bin/java` is used instead of `PATH`'s `java`.        |
| `JAVA_OPTS`         | Extra JVM options (word-split, e.g. `"-Xmx2g -Dfoo=bar"`).               |
| `COGNOTIK_JAR`      | Path to an existing `*-all.jar`; skips discovery/build entirely.        |
| `COGNOTIK_HOME`     | Cache dir for downloads (default `${XDG_CACHE_HOME:-~/.cache}/cognotik`).|
| `COGNOTIK_REPO`     | GitHub repo for releases (default `SimiaCryptus/Cognotik`).             |
| `COGNOTIK_VERSION`  | Release tag to download (default `latest`).                             |

Everything after the action and the launcher's own flags is passed straight through to the selected
CLI, so `cognotik docops run --var FOO=bar` and `cognotik autofix -- ./gradlew build` behave exactly
as documented below.

---

## `fileserver` / `fs` — serve a directory

Starts a foreground HTTP server over a directory: a classic file listing, an IDE-like single-page
view, Git integration, an interactive terminal, arbitrary command execution, and (by default) the
`docops` / `autofix` / "modify" agentic actions exposed as a small FS-API.

```shell
cognotik fileserver .                       # serve the current directory (127.0.0.1:8081)
cognotik fileserver --port 0 --host 0.0.0.0 /srv/project
cognotik fileserver --secure /srv/project   # read-only, no terminal/exec/tasks/modify
```

Key options (see `--help` for the full list):

| Option              | Effect                                                                     |
|---------------------|-----------------------------------------------------------------------------|
| `-p, --port <n>`    | Port to listen on (`0` = random free port). Default `8081`.                 |
| `-h, --host <addr>` | Interface to bind. Default `127.0.0.1`; use `0.0.0.0` for all interfaces.    |
| `--read-only`       | Disable uploads, edits, deletes (and `docops run` / `autofix` / `modify`).   |
| `--no-terminal`     | Disable interactive terminal sessions.                                      |
| `--no-exec`         | Restrict `/.fsapi/v1/exec` to a read-mostly Git allowlist.                  |
| `--secure`          | Shorthand for `--read-only --no-terminal --no-exec --no-tasks --no-modify`. |
| `--no-git`          | Disable Git UI/API features.                                               |
| `--no-ui`           | Do not serve the IDE-style single-page view.                               |
| `--files` / `--ui` / `--home` | Change the landing page `/` redirects to.                        |
| `--email <addr>`    | Login email for the local CLI user (default: anonymous).                   |
| `--task-root <dir>` | Project root handed to `docops`/`autofix`/`modify` (default: served dir).   |
| `--smart-model <id>`, `--fast-model <id>` | Model selection (or `COGNOTIK_SMART_MODEL`/`COGNOTIK_FAST_MODEL`). |
| `--no-tasks`        | Disable the `docops`/`autofix`/`tasks` FS-API operations.                   |
| `--no-modify`       | Disable the "modify files" patch-chat FS-API operation.                    |
| `--no-extract-utils`| Disable the bundled-tooling extraction operation.                          |

Because this is a *permissive local mount* by default (interactive terminals and unrestricted child
processes are enabled, binding only to loopback), pass `--secure` (or the individual flags) before
exposing it beyond `127.0.0.1`.

The server's own `--help` output documents every mounted endpoint, including the FS-API
(`/.fsapi/v1/docops`, `/.fsapi/v1/autofix`, `/.fsapi/v1/modify`, `/.fsapi/v1/tasks`,
`/.fsapi/v1/models`, `/.fsapi/v1/extract-utils`) and the model-selection endpoints.

---

## `keys` — configure provider API keys

Interactive (or scriptable) management of the provider credentials the other tools use to talk to
models.

```shell
cognotik keys                                   # interactive: pick a provider, paste a key
cognotik keys --list                            # show what is configured (keys masked)
cognotik keys --provider OpenAI --key sk-... --verify
cognotik keys --remove Anthropic
```

* Interactive mode never echoes the key to the terminal when a real console is attached
  (`Console.readPassword`); stored keys are only ever shown masked (`sk-a****...1234`).
* `--verify` contacts the provider to list its models after saving, purely as a sanity check.
* All of this goes through the same settings store the rest of the platform reads, so keys
  configured here are immediately visible to `docops`, `autofix`, and `fileserver`.

Exit codes: `0` success, `1` error, `2` bad usage.

---

## `docops` — render markdown frontmatter specs

A minimal, dependency-light front end for the `DocOps` engine: it turns declarative frontmatter in
markdown files (`specifies`, `transforms`, `documents`, `generates`, `folder`) into generated
artifacts.

```shell
cognotik docops plan                                   # what would happen? (pure, no writes)
cognotik docops vars docs/api.md                       # which {{ VARS }} does it declare?
cognotik docops run docs/api.md --var MODULE=billing   # execute one document
cognotik docops run --mode ForceUpdate -c 8 --open     # rebuild everything, watch in browser
cognotik docops status                                 # last recorded run
cognotik docops keys                                   # jump straight to API key setup
```

### Lifecycle

```
parse args
  └─ bootstrap platform (dynamic enums, local auth, OrchestrationConfig.instanceFn)
     └─ DocProcessor(root, docsFolder, updateMode, models, user, templateVarOverrides)
        ├─ getAll(...)            -> WorkPlan          (pure: no writes, no server)
        ├─ [plan]  print + exit
        └─ [run]
           ├─ initializeStatus(plan)                   (docops.status.json = PENDING)
           ├─ EphemeralMonitorServer.start()           -> prints "Monitor: http://host:port/"
           ├─ runAll(plan, pool, cancelFlag) { println monitor url per session }
           ├─ monitor.close()                          (finally + shutdown hook)
           └─ print status, exitProcess(0|1)
```

### Why the server is ephemeral

Task execution registers each session in the process-wide `SessionProxyServer` maps, so a web UI is
only needed for *observation*. The CLI therefore:

* starts Jetty **lazily** — never for `plan`, `status`, `vars`, `models`, `keys`, or an empty plan;
* binds an unused port by default so concurrent invocations do not collide;
* stops it in `finally` **and** from a shutdown hook (Ctrl-C also sets the cancel flag);
* writes no PID file and spawns no detached process.

Use `--serverless` for fully headless execution (implies `--no-monitor`).

### Commands

| Command  | Effect                                                                          |
|----------|-----------------------------------------------------------------------------------|
| `run`    | Plan and execute (default). Starts an ephemeral monitor server if work exists.     |
| `plan`   | Plan only. Pure: writes nothing, starts nothing.                                  |
| `status` | Print `docops.status.json` for `--root`.                                          |
| `vars`   | List declared `{{ TEMPLATE_VARS }}` and their defaults.                            |
| `models` | List model ids usable by the current user.                                        |
| `keys`   | Interactively configure provider API keys.                                        |

### Key options

| Option                | Effect                                                                     |
|------------------------|------------------------------------------------------------------------------|
| `--root DIR`           | Project root / working directory (default `.`).                            |
| `--docs DIR`           | Folder to scan for documents (default `--root`).                           |
| `--doc FILE`           | Single document file to process (repeatable).                              |
| `-m, --mode MODE`      | `SkipExisting`, `OverwriteExisting`, `OverwriteToUpdate`, `PatchExisting`, `PatchToUpdate` (default), `ForceUpdate`, `ForceOverwrite`. |
| `--target PATH`        | Only run the task producing this output file.                              |
| `--var NAME=VALUE`     | Template variable override (repeatable; `-DNAME=VALUE` also works).        |
| `-c, --concurrency N`  | Concurrent task queues (default `4`).                                      |
| `--smart-model ID`     | Primary model (or `COGNOTIK_SMART_MODEL`).                                 |
| `--fast-model ID`      | Secondary model (default: smart model).                                   |
| `--image-model ID`, `--audio-model ID` | Default: fast model.                                      |
| `-p, --port N`         | Monitor server port (default: unused ephemeral port).                     |
| `--host NAME`          | Monitor server bind name (default `localhost`).                           |
| `--no-monitor`         | Do not start the monitor server.                                           |
| `--serverless`         | Run fully in-process; implies `--no-monitor`.                             |
| `--open`               | Open each task session in a browser.                                      |
| `--no-auto-fix`        | Do not auto-apply generated patches.                                      |
| `-n, --dry-run`        | Same as the `plan` command.                                                |

### Exit codes

| Code | Meaning                  |
|------|--------------------------|
| 0    | success                  |
| 1    | one or more tasks failed |
| 2    | bad usage                |

---

## `autofix` — run a command, then patch what it complains about

Wraps an arbitrary command (no shell interpretation — `&&`, `|`, `>` are **not** parsed) and applies
the `AutoFix` task loop to it: run, parse errors from the output, generate patches against the
referenced source files, apply them (unless `--no-auto-fix`), and re-run — repeating until green or
the timeout is reached.

```shell
cognotik autofix -- ./gradlew build              # run, parse errors, patch, re-run
cognotik autofix "npm test" --no-auto-fix         # propose patches, approve in the monitor UI
cognotik autofix --cmd "cmake --build build" --cmd "ctest" --dir build
cognotik autofix --dry-run -- pytest -q           # show the plan, touch nothing
```

### How it works

1. The wrapped command is executed in `--dir` (default `--root`).
2. If it exits `0` the CLI exits `0` immediately — nothing is modified.
3. Otherwise the output is parsed into discrete errors, patches are generated against the referenced
   source files, applied (unless `--no-auto-fix`), and the whole command sequence is re-run. Repeats
   until green or retries run out.

### Key options

| Option                | Effect                                                                     |
|------------------------|------------------------------------------------------------------------------|
| `--root DIR`           | Project root used for file resolution (default `.`).                       |
| `--dir DIR`            | Working directory for the command (default `--root`).                     |
| `--cmd "CMD"`          | Add a command (repeatable; all commands re-run every iteration).           |
| `--smart-model ID`     | Primary model (or `COGNOTIK_SMART_MODEL`) — required.                     |
| `--fast-model ID`      | Secondary model (default: smart model).                                   |
| `--temperature N`      | Sampling temperature (default `0.0`).                                     |
| `-t, --timeout MIN`    | Give up after MIN minutes (default `30`).                                  |
| `-p, --port N`, `--host NAME` | Monitor server binding (as above).                                  |
| `--no-monitor`         | Do not start the monitor server.                                           |
| `--serverless`         | Run fully in-process; implies `--no-monitor` and forces auto-fix on.       |
| `--open`               | Open the session in a browser.                                            |
| `--no-auto-fix`        | Propose patches and wait for approval in the monitor UI.                  |
| `-n, --dry-run`        | Print the plan and exit; writes nothing, starts nothing.                  |
| `--list-models`        | List model ids usable by the current user.                                |

Transcripts and usage data for each run are written under `.cognotik/autofix/run-<timestamp>/`
beside the project, never inside it as clutter.

### Exit codes

| Code | Meaning                                                    |
|------|-------------------------------------------------------------|
| 0    | the wrapped command finally exited 0                        |
| 1    | still failing after the timeout / retries                   |
| 2    | bad usage                                                    |

---

## Shared conventions

* **Foreground, one-shot.** Every tool's `main` ends in `exitProcess(...)`; none of them leaves
  background threads or a daemon running after it returns.
* **Servers are ephemeral.** Whenever a tool starts a Jetty monitor (`docops run`, `autofix`), it is
  started lazily (only when there is something to watch), bound to an unused port by default, and
  stopped both in a `finally` block and from a shutdown hook (so Ctrl-C cleans up too).
* **`--email <addr>`** (docops, autofix, fileserver) selects the local user identity whose settings
  (API keys, model choices) are read; it defaults to `$EMAIL`, `user.email`, then `user@localhost`.
* **`COGNOTIK_DEBUG=1`** prints a full stack trace on unexpected errors instead of just the message.
