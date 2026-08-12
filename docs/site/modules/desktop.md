# Desktop

*Ship Cognotik as a native, installable application — no browser tab, no terminal required.*

## Overview

The `desktop` module packages Cognotik's full agent toolkit into a self-contained desktop application. It wraps the
core engine, web UI, and tool providers into a single launchable app, and can be distributed as a native installer
for macOS, Windows, and Linux. For teams and individual developers who want a "double-click and go" experience —
rather than standing up a server or running a CLI — this module is the answer.

Under the hood it's a standard JVM application (Java 21) built with Gradle, using [jpackage](https://openjdk.org/jeps/343)
and a fat JAR to produce platform-native installers.

## Key Features

* **Cross-platform native installers** — `.dmg` for macOS, `.msi` for Windows, and `.deb` for Linux, built directly
  from Gradle tasks (`packageDmg`, `packageMsi`, `buildDebManually`).
* **Single fat JAR distribution** — Shadow-packaged JAR bundles all dependencies (core, web UI, providers, tools)
  for a zero-hassle runtime.
* **App-store-quality packaging** — custom icons, `.desktop` file integration and menu entries on Linux, file
  associations on macOS, and Start Menu/shortcut wiring on Windows.
* **Daemon + client architecture** — a lightweight `DaemonClient` entry point launches and communicates with the
  main `CognotikApps` process, keeping startup fast and resource usage low.
* **Reproducible builds** — packaging tasks are fully scripted (no manual steps), including icon conversion,
  control-file generation for `.deb`, and install-size calculation.
* **Signed & publishable artifacts** — Maven publishing and PGP signing are wired in, so the desktop module can also
  be consumed as a library artifact, not just shipped as an app.

## Example

Build and run the desktop app directly from source:

```bash
# Run the app in-place (development mode)
./gradlew :desktop:runCognotikApps

# Build a fat JAR and run it exactly as an end user would
./gradlew :desktop:runCognotikAppsFromJar
```

Produce a native installer for your current platform:

```bash
# Automatically picks the right packaging task for your OS
./gradlew :desktop:package
```

This generates a ready-to-distribute artifact — e.g. `Cognotik.dmg`, `Cognotik.msi`, or `cognotik_<version>_amd64.deb` —
in `build/jpackage/`.

## Integration

The desktop module is the top-level assembly point for the Cognotik platform. It depends on:

* `core`, `lwcore` — the core agent runtime
* `webui` — the browser-based UI, embedded via a Jetty server
* `providers`, `tasklib`, `stdtools` — model providers and built-in agent tools
* `text`, `docops`, `groovy`, `kotlin` — language and document processing support

Because it simply assembles existing modules into a runnable, packageable application, adding new capabilities to
Cognotik (a new tool, provider, or UI feature) automatically flows through to the desktop build with no extra wiring.