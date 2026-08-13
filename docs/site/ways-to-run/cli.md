# Running Cognotik from the CLI

The `docops` CLI is a minimal, dependency-light front end for driving Cognotik's document-processing engine
straight from your terminal — great for scripting, CI, and anyone who'd rather not open a full app.

## Getting Started

1. Download the launcher script and make it executable:
   ```shell
   curl -sSL -o fileserver https://raw.githubusercontent.com/SimiaCryptus/Cognotik/refs/heads/main/cli/bin/fileserver
   chmod +x fileserver
   ```
2. Run it:
   ```shell
   ./fileserver
   ```
3. Use the `docops` subcommands to drive the engine, for example:
   ```sh
   cognotik docops plan                                   # what would happen?
   cognotik docops vars docs/api.md                       # which {{ VARS }} exist?
   cognotik docops run docs/api.md --var MODULE=billing   # execute one document
   cognotik docops run --mode ForceUpdate -c 8 --open     # rebuild everything, watch in browser
   cognotik docops status                                 # last recorded run
   ```

Exit codes are straightforward: `0` on success, `1` if any task failed, and `2` for bad usage.

## Packaging & Launch

The `fileserver` script is a self-contained launcher — no installer needed. It figures out on its own whether
you're running from a Cognotik source checkout or as a standalone download:

* In a source checkout, it builds the CLI jar automatically the first time (or on demand with `--build`).
* Standalone, it downloads the latest released jar and caches it locally, so later runs start instantly.
* You can point it at your own jar via the `COGNOTIK_JAR` environment variable, or check where it *would* run
  from with `--where`.

A web-based monitor is started only when you actually run a task (not for `plan`, `status`, `vars`, or `models`),
and it shuts down automatically when the run finishes — so the CLI stays lightweight and headless by default.
Use `--serverless` if you want to skip the monitor entirely.

---

Cognotik can also be run as a desktop app or as an IntelliJ plugin, if a GUI suits your workflow better.