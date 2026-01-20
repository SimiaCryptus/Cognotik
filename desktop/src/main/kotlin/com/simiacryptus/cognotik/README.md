# Cognotik Desktop Core

This package contains the core logic for the Cognotik desktop application, including the main server entry point, daemon management, system tray integration, and the automated update system.

## Key Components

### [CognotikApps.kt](./CognotikApps.kt)
The primary application class that extends `ApplicationDirectory`. It serves as the central hub for:
- **Web Server**: Initializes a Jetty-based server hosting the Cognotik web interface.
- **Application Registry**: Configures child applications like `/chat` (Basic Chat) and `/taskChat` (Unified Plan/Task Runner).
- **Socket Server**: Listens for commands from the `DaemonClient` (typically on `port + 1`).
- **Platform Setup**: Configures default authentication and authorization providers for local desktop use.
- **System Tray**: Initializes the `SystemTrayManager` for desktop integration.

### [DaemonClient.kt](./DaemonClient.kt)
A lightweight client used to manage the Cognotik background process. It handles:
- **Process Management**: Launches the server as a background daemon if it isn't already running.
- **Command Dispatch**: Sends commands (like opening specific session directories) to the running server via a socket.
- **Lifecycle Control**: Supports stopping the server via the `--stop` flag.
- **Session Management**: Generates unique session directories in `~/.cognotik`.

### [SystemTrayManager.kt](./SystemTrayManager.kt)
Provides OS-level integration via the system tray icon. Features include:
- **Quick Access**: "Open in Browser" menu item and double-click support.
- **Update Notifications**: Displays a menu item when a new version is available via the `UpdateManager`.
- **SVG Icon Support**: Uses Apache Batik to render the application icon from SVG.
- **Notifications**: Displays error and status messages using native tray notifications.

### [UpdateManager.kt](./UpdateManager.kt)
A comprehensive update system that interacts with GitHub Releases. It includes:
- **Version Checking**: Compares the current `jpackage` version against the latest tag on GitHub.
- **OS Detection**: Identifies Windows, macOS, or Linux to select the appropriate installer (`.msi`, `.dmg`, `.deb`, etc.).
- **Download Management**: Downloads updates with a Swing-based progress bar and support for cancellation.
- **Automated Installation**: Generates and executes OS-specific scripts (PowerShell for Windows, Shell for Unix) to uninstall the old version and launch the new installer.

## Usage

### Command Line Arguments

The application can be invoked with several commands:

- **Server Mode**: `cognotik server [--port <port>] [--host <host>] [--public-name <name>]`
  - Starts the web server directly in the foreground.
- **Daemon Mode**: (Default behavior when no command is provided)
  - Checks if a server is running; if not, launches it in the background.
  - Dispatches the current directory or a new session to the running server.
- **Stop Server**: `cognotik --stop`
  - Sends a shutdown command to the running daemon.
- **Help**: `cognotik help`
  - Displays usage information.

### Configuration

- **Default Port**: `12891`
- **Socket Port**: `12892` (Default Port + 1)
- **Home Directory**: `~/.cognotik` (Stores PID files and session data)

## Architecture

1. **Communication**: The `DaemonClient` and `CognotikApps` communicate via a simple socket protocol. This allows multiple CLI invocations to interact with a single persistent server instance.
2. **Update Flow**:
   - `UpdateManager` fetches JSON from the GitHub API.
   - If a newer version is found, the user is prompted via the System Tray.
   - Upon confirmation, the installer is downloaded to a temporary file.
   - A platform-specific script is generated to handle the transition (closing the app and running the installer).
3. **Model Integration**: Uses `AnthropicModels.Claude35Haiku` as the default model for orchestration tasks.

## Dependencies

- **Jetty**: Web server engine.
- **Apache Batik**: SVG transcoding for the system tray icon.
- **Gson**: JSON parsing for GitHub API responses.
- **Kotlin Coroutines/Executors**: Background task management.