# CommitAnalyzerTaskCli

A command-line harness for running `CommitAnalyzerTask` outside the IDE plugin or web UI.
It takes a single commit, or a range of commits treated as one combined change, and splits ("explodes") it into a series
of smaller, ordered operations. It can optionally:

- verify that every intermediate state still builds,
- emit patch files, and
- write the result as a branch with one commit per item.

The recommended entry point is the `cognotik` launcher script with the `commit` action:

~~~bash
cognotik commit [launcher options] [--] [CommitAnalyzerTaskCli options]
~~~

---

## Quick start

~~~bash
# Analyze HEAD of the current directory using the hosted backend (default)
cognotik commit

# Analyze a specific commit in another repo, with locally configured API keys
cognotik commit --local --repo /path/to/repo --commit <rev>


# Explode the combined change of a range of commits
cognotik commit --commit main..feature
cognotik commit --commit main...feature      # diff from merge-base(main, feature)
cognotik commit --base v1.0 --commit HEAD    # same as v1.0..HEAD


# Fully deterministic run (no LLM calls), no patch files
cognotik commit --no-llm --no-patches --commit HEAD~1

# Verify each intermediate state builds, and write the series to a named branch
cognotik commit --verify-build --build-command "./gradlew compileKotlin" \
    --build-timeout 900 --branch split/my-feature

# Show CommitAnalyzerTaskCli's own usage text (note the `--`, see below)
cognotik commit -- --help
~~~

---

## Running with the `cognotik` launcher

`cognotik` (located at `../../../../../../../../bin/cognotik`) is a single launcher for all Cognotik CLI tools.
The `commit` action runs `com.simiacryptus.cognotik.cli.task.CommitAnalyzerTaskCli` from the
`cli` fat jar (`*-all.jar`). The launcher locates or obtains that jar, picks a JVM, and then
`exec`s Java with everything after the launcher options passed straight through to this CLI.

### Installing the launcher

The script resolves symlinks and does not depend on your working directory, so you can put it on
your `PATH` with a symlink:

~~~bash
ln -s /path/to/Cognotik/cli/bin/cognotik ~/bin/cognotik
~~~

It can also be copied anywhere as a standalone file. In that case it downloads a released fat jar.

### Where the jar comes from

| Situation                      | Behavior                                                                                                                                        |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `COGNOTIK_JAR` is set          | Uses that jar. No discovery, build or download.                                                                                                 |
| Inside a source checkout       | Uses the newest `cli/build/libs/*-all.jar`. If none exists, runs `./gradlew :cli:shadowJar` first.                                              |
| Standalone (no checkout found) | Uses the newest cached jar in `${COGNOTIK_HOME:-${XDG_CACHE_HOME:-~/.cache}/cognotik}/jars`. If none exists, downloads it from GitHub releases. |

A source checkout is detected by walking up from the script's location until a directory contains
`../../../../../../../..` and a `settings.gradle(.kts)` file.

> **Tip:** Run `cognotik commit --where` to see which mode, jar and Java binary would be used,
> without running anything.

### Launcher options

Launcher options go **before** any `CommitAnalyzerTaskCli` options. The launcher stops parsing at
the first argument it does not recognize, and everything from there on is passed to this CLI.

| Option       | Description                                                                              |
|--------------|------------------------------------------------------------------------------------------|
| `--build`    | Force a rebuild of `:cli:shadowJar` (source checkout only; an error in standalone mode). |
| `--no-build` | Never build or download. Fail if no jar is available.                                    |
| `--update`   | Re-download the release jar (standalone mode).                                           |
| `--where`    | Print the jar, mode and Java binary that would be used, then exit.                       |
| `-h, --help` | Show the **launcher's** help, then exit.                                                 |
| `--`         | End of launcher options. Everything after it goes to `CommitAnalyzerTaskCli`.            |

Examples:

~~~bash
# Rebuild the jar from source, then run
cognotik commit --build --commit HEAD~3

# Offline / CI: fail rather than build or download
cognotik commit --no-build --no-llm --commit HEAD

# Refresh the downloaded release jar (standalone mode), then run
cognotik commit --update --local --commit HEAD
~~~

> **Important:** `--help` (and `-h`) is intercepted by the launcher if it appears before `--`.
> To see this CLI's usage text, use `cognotik commit -- --help`.
>
> Likewise, launcher options placed *after* a CLI option (for example
> `cognotik commit --repo . --build`) are not seen by the launcher. They are passed to
> `CommitAnalyzerTaskCli`, which rejects them as unknown options.
> (`--build-command`, `--build-timeout` and `--verify-build` are CLI options and are unaffected.)

### Environment variables

| Variable           | Description                                                                           |
|--------------------|---------------------------------------------------------------------------------------|
| `JAVA_HOME`        | If set and usable, `$JAVA_HOME/bin/java` is used. Otherwise `java` from `PATH`.       |
| `JAVA_OPTS`        | Extra JVM options, word-split (e.g. `JAVA_OPTS="-Xmx2g -Dfoo=bar"`).                  |
| `COGNOTIK_JAR`     | Path to an existing `*-all.jar`. Skips discovery, build and download.                 |
| `COGNOTIK_HOME`    | Cache directory for downloaded jars (default `${XDG_CACHE_HOME:-~/.cache}/cognotik`). |
| `COGNOTIK_REPO`    | GitHub repo used for release downloads (default `SimiaCryptus/Cognotik`).             |
| `COGNOTIK_VERSION` | Release tag to download (default `latest`).                                           |

Examples:

~~~bash
JAVA_OPTS="-Xmx4g" cognotik commit --commit main..feature
COGNOTIK_JAR=/opt/cognotik/cli-all.jar cognotik commit --no-build --where
~~~

### Working directory

The launcher does not change directory, so the CLI inherits your current working directory.
This matters because the CLI points its database root at the working directory (see
[Startup sequence](#startup-sequence)). Run `cognotik commit` from the directory where you want
local data and session state to live. `--repo` is independent of this and can point anywhere.

### Running without the launcher

If you prefer not to use the launcher, run the `main` method of
`com.simiacryptus.cognotik.cli.task.CommitAnalyzerTaskCli` directly, either from an IDE run
configuration, from a Gradle `JavaExec` setup that includes the needed classes on the classpath,
or with the fat jar:

~~~bash
java -cp cli/build/libs/<name>-all.jar com.simiacryptus.cognotik.cli.CommitAnalyzerTaskCli --commit HEAD
~~~

Program arguments are passed straight through.

---

## Execution modes

| Mode             | Flag                  | Behavior                                                                                                                                                                  |
|------------------|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Hosted (default) | `--hosted` or nothing | Performs a device login through `FileServer`'s hosted bootstrap. API calls are proxied through the hosted server, and the session is cached in the global `session.json`. |
| Local            | `--local`             | Skips hosted login entirely and uses the API keys already configured in your local user settings (see `cognotik keys`).                                                   |

`--local` takes precedence. If both flags are given, the CLI runs in local mode.

If hosted login fails, the CLI exits with code `2` and suggests two fixes: check your hosted
account balance, or re-run with `--local`.

---

## Options

Options are accepted in two forms: `--key value` and `--key=value`.

Boolean flags may be given bare (`--no-llm`) or with an explicit value (`--no-llm=true`).
Explicit values are parsed strictly. Only `true` and `false` are recognized, and anything
else is treated as `false`.

All options in this section are passed after the launcher options (and after `--` if you use it).

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

| Option     | Description                                                                                                               |
|------------|---------------------------------------------------------------------------------------------------------------------------|
| `--hosted` | Use the hosted backend (default).                                                                                         |
| `--local`  | Use locally configured API keys.                                                                                          |
| `--help`   | Print usage and exit. This is checked before any other parsing. When using `cognotik`, write `cognotik commit -- --help`. |

---

## Argument parsing rules

- **Positional arguments are rejected.** Every argument must start with `--`.
- **Unknown options are rejected** with `Unknown option: --<name>`. This includes launcher
  options such as `--build` or `--where` when they appear after the first CLI option.
- **Values that begin with `--` must use the `=` form.** For example, write
  `--build-command=--some-flag`. The space-separated form would treat the value as a
  missing argument.
- **Integer options** must parse as integers. Otherwise you get `--<key> must be an integer`.
- **`--mechanical`** must be `first` or `last`. Matching is case-insensitive.
- **`--threshold`** must parse as a number. Otherwise you get `--threshold must be a number`.
- **`--repo`** must point to an existing directory, otherwise you get
  `--repo does not exist or is not a directory: <path>`.
- **`--timeout-minutes`** must be a positive integer.
- After parsing, the CLI calls `CommitAnalyzerTaskExecutionConfigData.validate()`. Any
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
otherwise keep the JVM alive. The launcher `exec`s Java, so the CLI's exit code is the exit
code of `cognotik commit`.

> **Note:** The launcher itself also exits with `1` (after printing a `cognotik: ...` message
> to stderr) for launcher-level problems, such as a missing action, no usable `java`, a failed
> build or download, or no jar being available with `--no-build`. These messages are prefixed
> with `cognotik:`, which distinguishes them from task failures.

---

## Logging

The CLI logs through SLF4J under the logger `com.simiacryptus.cognotik.plan.tools.file.CommitAnalyzerTaskCli`.

| Level   | What it shows                                           |
|---------|---------------------------------------------------------|
| `INFO`  | Mode, the resolved config summary, and timings.         |
| `DEBUG` | Per-argument parsing, full config, and model discovery. |
| `TRACE` | Model accessors that were skipped during reflection.    |

Raise the level in your logging configuration when diagnosing issues. When using the launcher,
you can pass JVM system properties through `JAVA_OPTS`, if your logging backend supports them.
For example: `JAVA_OPTS="-Dlogback.configurationFile=/path/to/logback.xml" cognotik commit`.

---

## Troubleshooting

| Symptom                                                            | Likely cause / fix                                                                         |
|--------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `cognotik: missing required action`                                | You ran `cognotik` with no action. Use `cognotik commit ...`.                              |
| `cognotik: no *-all.jar in ... (and --no-build was given)`         | No jar is available. Drop `--no-build`, or set `COGNOTIK_JAR`.                             |
| `--build requires a Cognotik source checkout`                      | `--build` only works from a checkout. In standalone mode use `--update`.                   |
| `cognotik commit --help` prints the launcher help, not the CLI's   | Use `cognotik commit -- --help`.                                                           |
| `Unknown option: --build` (or `--where`, `--update`, `--no-build`) | Launcher options must come before CLI options. Move them directly after `commit`.          |
| Exit code `2` with a hosted login error                            | Check your hosted account balance, or re-run with `--local`.                               |
| Unexpected files appear in the directory you ran from              | The database root is the working directory. Run from a directory where this is acceptable. |