# Cognotik CLI Module — Code Map

Overview of `com.simiacryptus.cognotik.cli`: reference command-line tools and an
embeddable file server for driving Cognotik's DocOps and AutoFix agentic workflows,
both from a terminal and remotely over HTTP.

## Files

- **`ApiKeysCli.kt`** — Interactive/non-interactive API key configuration
  (`cognotik keys`). Lists providers, prompts for keys (hidden input via
  `Console.readPassword` when available), verifies against the provider, and
  persists through `UserSettingsInterface.updateUserSettings`. Exposes `main`
  (exits process) and `configure(...)` for embedding by other CLIs.

- **`AutoFixCli.kt`** — Wraps an arbitrary command and iteratively patches the
  codebase until it succeeds (`cognotik autofix`). Starts an ephemeral monitor
  server only when work exists, drives `AutoFixTask` via `UnifiedHarness`, and
  always exits based on the wrapped command's final status. Exposes `main` and a
  programmatic `run(args): Int`.

- **`CliSupport.kt`** — Shared headless bootstrap used by multiple CLIs: installs
  root-scoped `FileApplicationServices`, initializes providers/tasks/plugins,
  configures the orchestration model-instance function, and resolves smart/fast/
  image/audio `ChatModel`s from user settings and CLI/env overrides.

- **`DocOpsCli.kt`** — Plans and executes markdown-frontmatter-driven document
  generation/updates (`cognotik docops`). Subcommands: `plan`, `run`, `status`,
  `vars`, `models`, `keys`, `help`. Planning is pure; `run` seeds
  `docops.status.json`, optionally starts an ephemeral monitor server, and
  executes tasks concurrently via `FixedConcurrencyProcessor`. Exposes `main` and
  `run(args): Int`.

- **`DocOpsSupport.kt`** — Headless DocOps runtime plumbing shared by `DocOpsCli`
  and `ServerTaskActions`/`ServerDocOps`: idempotent platform bootstrap, model
  resolution, `DocProcessor` construction, and target/plan filtering — so
  in-process embedders can drive DocOps without shelling out to CLI argv parsing.

- **`ServerDocOps.kt`** — Server-side driver for the DocOps API backing the file
  server's `POST /.fsapi/v1/docops`. Mirrors `DocOpsCli`'s command surface
  (`plan`, `run`, `status`, `vars`, `models`, plus `targets` for live option
  lists) but never touches global `ApplicationServices` state, never calls
  `exitProcess`, and jails all requested paths under the configured root.

- **`ServerTaskActions.kt`** — Registers `docops`, `autofix`, and `tasks` as FS API
  `FsAction`s on the file server. Serializes execution behind a single-task lock,
  tees stdout/stderr into an in-memory task record (`TeeStream`), and answers
  synchronously for pure commands or asynchronously (task id to poll) for mutating
  ones. Refuses mutating operations with `EROFS` on read-only mounts.

- **`ModifyFilesActions.kt`** — Port of the IDE's `ModifyFilesAction` onto the FS
  API (`POST /.fsapi/v1/modify`): selects files/folders (jailed + expanded via
  `FileSelectionUtils`), opens a `ChatSocketManager` (`PatchChatManager`) whose
  system prompt embeds the code and patch-format instructions, and returns a chat
  session URL instead of a task id (the work happens interactively over a
  websocket). Refused with `EROFS` when read-only.

- **`EphemeralMonitorServer.kt`** — Thin wrapper around `CognotikAppServer`/Jetty
  used only to watch task sessions. Lazily started, bound to a single CLI
  invocation's lifetime, always stopped via `close()` (called in `finally` blocks
  and shutdown hooks). Never a daemon.

- **`FileServerCli.kt`** — Foreground static/file-management HTTP server
  (`FileServerCli` / `cognotik-fileserver`). Serves a directory with a classic
  listing, optional IDE-style SPA (`/ui/`), git integration, terminal sessions
  (`FilesystemServlet`/FS API), and wires in `ServerTaskActions` and
  `ModifyFilesActions` by default. Permissive-by-default (loopback bind,
  unrestricted terminal/exec); `--secure` and friends harden it. Also renders
  per-document Plan/Run links, per-file Modify links, an AutoFix toolbar button,
  and a live task-output panel in the classic listing.

- **`README.md`** — Human-facing documentation: quick start, design principles
  (one-shot processes, ephemeral servers, pure planning, single settings writer,
  no secrets on screen, path jailing, permissive-by-default), and per-tool usage
  summaries.

## Design principles (see README.md for full detail)

1. **One-shot CLIs.** Every `main` ends in `exitProcess`; each CLI object also
   exposes `run(args): Int` for in-process embedding (e.g. by `FileServerCli`'s FS
   actions) without killing the JVM.
2. **Ephemeral servers only.** `EphemeralMonitorServer` / chat servers are started
   lazily, only when there is work to watch/host, and always stopped in `finally`
   blocks and shutdown hooks. No daemons, no PID files.
3. **Planning is pure.** `docops plan` / the `plan` FS API command never mutates
   the workspace and never starts a server.
4. **One writer for settings.** All API key persistence goes through
   `UserSettingsInterface.updateUserSettings` (see `ApiKeysCli`).
5. **No secrets on screen or in history.** Interactive key entry uses
   `Console.readPassword`; stored keys are only ever displayed masked.
6. **Untrusted paths are jailed.** Any path from a request/argv (documents, modify
   selections, autofix working directories) is canonicalized and checked against
   the configured root before use.
7. **Permissive by default, lockable on request.** `FileServerCli` binds to
   loopback with terminals/`child_process` enabled by default; `--secure` (or
   individual flags) hardens it.

## Resources

- **`src/main/resources/logback.xml`** — Console-only logging (WARN+), silences
  Jetty/OkHttp/traffic loggers; appropriate for foreground CLI use.
- **`src/main/resources/permissions/*.txt`** — Static permission lists
  (admin/delete/execute/globalkey/public/read/share/write) consumed by the
  platform's file-based authorization store.
- **`src/test/resources/logback-test.xml`** — Verbose console logging for tests.