# Running Cognotik on Desktop

Cognotik Desktop is a system-tray-resident background service that hosts the Cognotik web apps on your machine and keeps them running, updated, and easy to reach — ideal for anyone who wants Cognotik available locally without managing a server by hand.

## Getting Started

1. Launch Cognotik as a background daemon (this will auto-start the server if it isn't already running):
   ```bash
   cognotik daemon
   ```
2. Alternatively, start the server directly on the default port (12891):
   ```bash
   cognotik server
   ```
   You can customize the port, host, or public name if needed:
   ```bash
   cognotik server --port 12000 --host 0.0.0.0 --public-name my.host.com
   ```
3. Once running, use the system tray icon to open Cognotik in your browser, check for and apply updates, or exit the app.
4. To stop a running daemon, use:
   ```bash
   cognotik --stop
   ```

Logs are stored under `~/.cognotik/logs`, and session data lives under `~/.cognotik/<session-id>`.

## Packaging & Launch

Cognotik Desktop is distributed as a native, double-clickable installer for each platform — a `.dmg`/`.pkg` on macOS, an `.msi` on Windows, and a `.deb` package on Linux — so you install and launch it like any other desktop application, no manual Java setup required. Once installed, it runs quietly in the system tray, checks for new releases in the background, and can download and apply updates for you, relaunching automatically when an update finishes.

## See Also

Cognotik can also be run other ways, such as from the command line or as an IDE plugin.