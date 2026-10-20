# CommitAnalyzerTaskCli

A command-line harness for running `CommitAnalyzerTask` outside the IDE plugin or web UI.
It takes a single commit, or a range of commits treated as one combined change, and splits ("explodes") it into a series
of smaller, ordered operations. It can optionally:

- verify that every intermediate state still builds,
- emit patch files, and
- write the result as a branch with one commit per item.

---

## Quick start

~~~bash
# Analyze HEAD of the current directory using the hosted backend (default)
CommitAnalyzerTaskCli

# Analyze a specific commit in another repo, with locally configured API keys
CommitAnalyzerTaskCli --local --repo /path/to/repo --commit <rev>


# Explode the combined change of a range of commits
CommitAnalyzerTaskCli --commit main..feature
CommitAnalyzerTaskCli --commit main...feature      # diff from merge-base(main, feature)
CommitAnalyzerTaskCli --base v1.0 --commit HEAD    # same as v1.0..HEAD


# Fully deterministic run (no LLM calls), no patch files
CommitAnalyzerTaskCli --no-llm --no-patches --commit HEAD~1

# Verify each intermediate state builds, and write the series to a named branch
CommitAnalyzerTaskCli --verify-build --build-command "./gradlew compileKotlin" \
    --build-timeout 900 --branch split/my-feature
~~~

Run the `main` method of `com.simiacryptus.cognotik.plan.tools.file.CommitAnalyzerTaskCli` on the
test runtime classpath. Your IDE run configuration or any Gradle `JavaExec` setup that
includes test classes will work. Program arguments are passed straight through.

---

## Execution modes

| Mode             | Flag                  | Behavior                                                                                                                                                                  |
|------------------|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Hosted (default) | `--hosted` or nothing | Performs a device login through `FileServer`'s hosted bootstrap. API calls are proxied through the hosted server, and the session is cached in the global `session.json`. |
| Local            | `--local`             | Skips hosted login entirely and uses the API keys already configured in your local user settings.                                                                         |

`--local` takes precedence. If both flags are given, the CLI runs in local mode.

If hosted login fails, the CLI exits with code `2` and suggests two fixes: check your hosted
account balance, or re-run with `--local`.

---

## Options

Options are accepted in two forms: `--key value` and `--key=value`.

Boolean flags may be given bare (`--no-llm`) or with an explicit value (`--no-llm=true`).
Explicit values are parsed strictly. Only `true` and `false` are recognized, and anything
else is treated as `false`.

### Input

| Option                        | Default | Description                                                                                                                                                                                     |
|-------------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `--repo <path>`               | `.`     | Repository path. Always resolved to a canonical absolute path (the task resolves relative paths against the harness workspace, not the CLI's working directory). Must exist and be a directory. |
| `--commit <rev\|A..B\|A...B>` | `HEAD`  | Commit (any git revision) to explode, or a commit range whose combined change is exploded. `A..B` diffs `A` -> `B`; `A...B` diffs `merge-base(A, B)` -> `B`.                                    |
| `--base <rev>`                | none    | Base revision. Explodes the combined change `<base>..<commit>`.                                                                                                                                 |

When `--base` is given, the startup banner shows the target as `<base>..<commit>`.

### Build verification

| Option                  | Default | Description                                                    |
|-------------------------|---------|----------------------------------------------------------------|
| `--verify-build`        | off     | Verify that every intermediate state builds.                   |
| `--build-command <cmd>` | none    | Command used for verification. Quote it if it contains spaces. |
| `--build-timeout <sec>` | `600`   | Per-build timeout in seconds.                                  |
| `--max-retries <n>`     | `10`    | Maximum verification/repair attempts.                          |

### Output

| Option            | Default                 | Description                                             |
|-------------------|-------------------------|---------------------------------------------------------|
| `--branch <name>` | `commit-analysis/<sha>` | Name of the commit-series branch (one commit per item). |
| `--no-patches`    | off                     | Do not emit patch files.                                |

### Analysis tuning

| Option                       | Default | Description                                                                          |
|------------------------------|---------|--------------------------------------------------------------------------------------|
| `--no-llm`                   | off     | Deterministic mode with no LLM calls.                                                |
| `--mechanical <first\|last>` | `first` | Where mechanical operations (renames, bulk replaces, etc.) are placed in the series. |
| `--threshold <double>`       | `0.5`   | Clustering similarity threshold.                                                     |
| `--bulk-min <n>`             | `3`     | Minimum occurrences before a change is treated as a bulk replace.                    |
| `--max-cluster <n>`          | `40`    | Maximum size of a deterministic cluster.                                             |
| `--max-llm-ops <n>`          | `200`   | Maximum number of operations sent to the LLM for dependency reasoning.               |

### Runtime and models

| Option                  | Default          | Description                                    |
|-------------------------|------------------|------------------------------------------------|
| `--timeout-minutes <n>` | `30`             | Overall harness timeout. Must be positive.     |
| `--smart-model <name>`  | `GeminiFlash_38` | Smart model name.                              |
| `--fast-model <name>`   | `GeminiFlash_38` | Fast model name. Also used as the audio model. |

Model names are resolved case-insensitively against the `ChatModel` properties exposed by
`GeminiModels`. An unknown name produces an error that lists every known model. The image
model is fixed to `GeminiModels.GeminiFlash_31_Image_Preview`.

### Misc

| Option     | Description                                                     |
|------------|-----------------------------------------------------------------|
| `--hosted` | Use the hosted backend (default).                               |
| `--local`  | Use locally configured API keys.                                |
| `--help`   | Print usage and exit. This is checked before any other parsing. |

---

## Argument parsing rules

- **Positional arguments are rejected.** Every argument must start with `--`.
- **Unknown options are rejected** with `Unknown option: --<name>`.
- **Values that begin with `--` must use the `=` form.** For example, write
  `--build-command=--some-flag`. The space-separated form would treat the value as a
  missing argument.
- **Integer options** must parse as integers. Otherwise you get `--<key> must be an integer`.
- **`--mechanical`** must be `first` or `last`. Matching is case-insensitive.
- After parsing, the CLI calls `CommitAnalyzerTaskExecutionConfigData.validate()`. Any
- **`--threshold`** must parse as a number. Otherwise you get `--threshold must be a number`.
- **`--repo`** must point to an existing directory, otherwise you get
  `--repo does not exist or is not a directory: <path>`.
- **`--timeout-minutes`** must be a positive integer.
  message it returns is reported as an error, for example if a required build command is
  missing.
- Argument errors are reported as `Error: <message>` followed by the usage text, and the
  CLI exits with code `2`.

---

## Startup sequence

1. Parse and validate the arguments, resolve the models, and determine the mode.
2. `CognotikPlatform.init()`.
3. `CoreProviders.init()`. This registers providers, including the hosted `Cognotik` proxy,
   before login touches any settings.
4. Point `DatabaseFacet.root` at the **current working directory**. The original global
   root is kept and used for the hosted session.
5. Hosted mode only: `CliHostedServer().initHostedEnv(globalRoot)`, which runs device login
   and the hosted service wiring.
6. Pick the user: `CliSupport.defaultUser` if set, otherwise `CognotikConfig.defaultUser`.
7. `CliSupport.bootstrapPlatform(user)`. This is the same headless bootstrap used by
   `FileServer`: tasks, plugins, orchestrator, dynamic enums and permissive local auth.
8. Register the `CommitAnalyzer` task type and run it through `TaskHarness`.
   The parsed config is also stored in `CommitAnalyzerTask.executionConfigOverride` for the
   duration of the run (and cleared afterwards), because the harness may not propagate the
   execution config to the task instance.

Each major step is wrapped in timing logs (`[step] starting` / `[step] completed in N ms`),
so slow phases are easy to spot.

> **Note:** Because the database root moves to the working directory, local data and
> session state for the run are written relative to where you launch the CLI.

---

## Exit codes

| Code | Meaning                                                                                                                             |
|------|-------------------------------------------------------------------------------------------------------------------------------------|
| `0`  | Success, or `--help` was printed.                                                                                                   |
| `1`  | The CommitAnalyzer task itself failed. The stack trace is printed to stderr.                                                        |
| `2`  | CLI error: bad arguments or config, hosted login failure, or platform bootstrap failure. Usage text is printed for argument errors. |

The CLI always calls `exitProcess` at the end, because harness and platform threads may
otherwise keep the JVM alive.

---

## Logging

The CLI logs through SLF4J under the logger `com.simiacryptus.cognotik.plan.tools.file.CommitAnalyzerTaskCli`.

| Level   | What it shows                                           |
|---------|---------------------------------------------------------|
| `INFO`  | Mode, the resolved config summary, and timings.         |
| `DEBUG` | Per-argument parsing, full config, and model discovery. |
| `TRACE` | Model accessors that were skipped during reflection.    |

Raise the level in your test logging configuration when diagnosing issues.