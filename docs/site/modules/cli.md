# DocOps CLI

*A minimal, dependency-light command-line front end for driving documentation generation from your terminal.*

## Overview

The `cli` module is the reference command-line client for `docops`, Cognotik's documentation-generation engine. It
shows the smallest correct way to drive `DocOps`: parse arguments, bootstrap the platform, build a work plan, and
execute it — with an optional live monitor for watching progress in a browser.

Rather than a thin wrapper, the CLI models the full lifecycle of a documentation run: planning, execution, status
tracking, and cancellation — all from a single JVM process with no external runtime dependencies (no Python, no
Node).

## Key Features

- **Plan before you run** — `plan` computes a `WorkPlan` with no writes and no server started, so you can preview
  exactly what would happen.
- **Ephemeral monitoring server** — a Jetty-based monitor starts lazily only when a real execution is happening,
  binds an unused port automatically to avoid collisions between concurrent runs, and shuts down cleanly via
  `finally` and a shutdown hook (Ctrl-C sets a cancel flag instead of killing the process abruptly).
- **Serverless mode** — `--serverless` runs fully headless with no monitor and no Jetty instance, ideal for CI.
- **Status tracking** — every run initializes and updates a `docops.status.json`, queryable later with `status`.
- **Variable introspection** — `vars` lists the `{{ VARIABLES }}` referenced by a document before you run anything.
- **Concurrency control** — `-c <n>` controls the worker pool size for parallel document processing.
- **Clear exit codes** for scripting: `0` success, `1` task failure, `2` bad usage.
- **Packaged as a native app** — Gradle tasks build a fat JAR (`shadowJar`) plus platform-native installers via
  `jpackage`: `.dmg` (macOS), `.msi` (Windows), and a manually-assembled `.deb` (Linux), all wrapping the same CLI
  entry point.

## Example

```sh
# What would happen if I ran this?
cognotik docops plan

# Which {{ VARS }} does this document expect?
cognotik docops vars docs/api.md

# Run one document with a variable override
cognotik docops run docs/api.md --var MODULE=billing

# Force-rebuild everything with 8 workers, open the live monitor in a browser
cognotik docops run --mode ForceUpdate -c 8 --open

# Check the last recorded run
cognotik docops status
```

## Integration

The CLI is a thin orchestration layer over the rest of the Cognotik stack:

- `docops` — the core documentation engine driving `DocProcessor` and `WorkPlan` execution.
- `webui` / `fileserver` — power the ephemeral monitor server used by `--open` and live progress viewing.
- `core`, `lwcore`, `providers`, `tasklib`, `stdtools` — shared platform services (auth, orchestration config,
  model access) that the CLI bootstraps before running any plan.
- `groovy`, `kotlin` scripting support — enables template variable evaluation and scripting hooks within documents.

Because task execution registers sessions in a process-wide session registry, the monitor UI is purely observational
— you can always run with `--serverless` and get identical results without a browser in the loop.