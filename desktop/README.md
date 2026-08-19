# Cognotik Desktop

Cognotik Desktop is the desktop launcher and background service manager for the Cognotik application suite. It provides a system tray-resident daemon that hosts the Cognotik web applications, manages plugin lifecycle, and handles automatic updates.

## Components

### `CognotikApps`
The main application server entry point. Responsibilities include:

- **Command-line parsing** — supports `server`, `daemon`, and `help` subcommands, plus `--port`, `--host`, and `--public-name` options.
- **Port management** — automatically finds an available port if the requested one is in use (`findAvailablePort`).
- **Web app hosting** — builds and serves a collection of `ChildWebApp`s (DocOps apps, proxy server, chat app, task-runner app) via Jetty.
- **Dynamic plugin reload** — watches for plugin changes and hot-reloads affected web app contexts without a full server restart (`reloadApps`).
- **System tray integration** — initializes a `SystemTrayManager` for status display and user interaction.
- **Socket-based control channel** — starts a lightweight socket server (on `port + 1`) that accepts simple text commands (e.g. `shutdown`) from `DaemonClient`.
- **Scheduled maintenance** — periodically checks for updates and verifies the server/thread pool is alive.

### `DaemonClient`
A lightweight, logging-free CLI client that:

- Launches the `CognotikApps` server as a background daemon process if one isn't already running (per-OS launch scripts for Windows, macOS, and Linux).
- Waits for the server to become reachable before proceeding.
- Dispatches commands (including session directory paths) to the running server over a TCP socket.
- Supports `--stop` to gracefully shut down the daemon (via socket `shutdown` command, falling back to killing the recorded PID).

### `SystemTrayManager`
Manages the OS system tray icon and menu:

- Renders the app icon from an SVG resource using Apache Batik.
- Provides menu actions: **Open in Browser**, **Update** (when a newer version is available), and **Exit**.
- Reflects live server status (Running/Idle) in the tray icon tooltip.
- Displays throttled error notifications via tray balloon messages.

### `UpdateManager`
Handles checking for and applying application updates:

- Queries the GitHub Releases API for the latest release, with response caching (1 hour TTL).
- Compares semantic versions to determine if an update is available.
- Detects the current OS (Windows/macOS/Linux) and selects a compatible release asset (`.msi`/`.exe`, `.dmg`/`.pkg`, `.deb`/`.rpm`/`.AppImage`).
- Downloads the asset with a Swing progress dialog supporting cancellation.
- Launches OS-specific update scripts (PowerShell on Windows, shell scripts on macOS/Linux) that uninstall the current version, install the new one, and relaunch, after which the current process exits.

## Usage

```bash
# Start the server directly on the default port (12891)
cognotik server

# Start the server on a custom port/host
cognotik server --port 12000 --host 0.0.0.0 --public-name my.host.com

# Run as a background daemon (auto-launches server if not running)
cognotik daemon

# Stop a running daemon
cognotik --stop

# Show help
cognotik help
```

When invoked without a `server`/`daemon` subcommand, `DaemonClient` will automatically launch the server as a background process (if not already running), wait for it to come up, create a random session directory, and dispatch the session as a command to the server's control socket.

## Ports

- **Main port** (default `12891`): Jetty HTTP server hosting web applications.
- **Control port** (`main port + 1`): Plain-text socket for daemon control commands (e.g. `shutdown`).

## Logs & Data

- Logs are stored under `~/.cognotik/logs`.
- Session directories are created under `~/.cognotik/<session-id>`.
- The daemon PID is recorded in `cognotik_server.pid` for use by `--stop`.

## Notes

- `DaemonClient` intentionally avoids using the logging framework to prevent stray log file creation in arbitrary working directories.
- Plugin changes are detected and applied at runtime via `reloadApps()`, which rebuilds and re-registers only the affected Jetty `WebAppContext`s, minimizing downtime.