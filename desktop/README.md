# Cognotik Application Server Documentation

## Overview

The Cognotik Application Server is a modular platform for hosting AI-powered applications. It provides a daemon-based
architecture that allows applications to run in the background and be accessed via a web interface or system tray.
The server includes system tray integration, socket-based communication for remote control, automatic updates,
and various AI-powered applications configurable through a setup wizard.

## Open Source & API Key Requirements

The Cognotik Application Server is open source software licensed under Apache 2.0. It uses a "Bring Your Own Key" (BYOK)
model for AI services:

- You must provide your own API keys for AI providers (OpenAI, Anthropic, etc.)
- All API usage is billed directly to your accounts with these providers
- No data is shared with third parties without your explicit configuration
- The application includes tools to help you monitor your API usage

## Core Components

### AppServer

The main server component that hosts web applications, manages the system tray, handles updates, and provides the core functionality.

**Key Features:**

- Welcome wizard for initial setup and session configuration
- User settings management (API Keys, Local Tools) via web UI
- Socket server for communication with DaemonClient

**Usage:**

```
java -cp <classpath> com.simiacryptus.cognotik.AppServer [options]
```

**Options:**

- `--port <port>`: Specify the HTTP port (default: 7681)
- `--host <host>`: Specify the host name (default: localhost)
- `--public-name <name>`: Specify the public domain name (default: apps.simiacrypt.us)

### DaemonClient

A client application that manages the AppServer as a daemon process, allowing it to run in the background.

**Key Features:**

- Launches the AppServer as a background process
- Communicates with the server via socket
- Provides commands to start, stop, and control the server

**Usage:**

```
java -cp <classpath> com.simiacryptus.cognotik.DaemonClient [command] [args]
```

**Commands:**

- `server [options]`: Start the server with options (same as AppServer)
- `--stop`: Stop the running server
- `<command> [args]`: Send a command to the running server

### SystemTrayManager

Manages the system tray icon and menu for easy access to the applications.

**Key Features:**

- Quick access to hosted applications via browser
- Option to check for and install updates

### UpdateManager
Handles checking for new application versions, downloading updates, and launching the appropriate installer for the detected operating system (Windows, macOS, Linux).
**Key Features:**
- Checks GitHub releases for the latest version
- Compares latest version with the current running version
- Downloads platform-specific installers (MSI, DMG, DEB)
- Provides UI prompts for update confirmation and progress
- Manages installer execution (including uninstallation steps where needed)


## Included Applications

The server hosts several AI-powered applications, primarily accessed through a central welcome wizard or the system tray:

1. **Basic Chat** (`/chat`): A simple, standalone chat interface accessible via a button in the welcome page header. Allows configuring model, temperature, and budget separately.

2. **Task Chat** (`/taskChat`): Launched from the welcome wizard ("Chat" mode). An interactive environment for executing individual tasks using the `TaskChatMode` cognitive strategy. Configurable via the wizard.

3. **Autonomous Mode** (`/autoPlan`): Launched from the welcome wizard ("Autonomous" mode). An application that automatically plans and executes tasks using the `AutoPlanMode`
   cognitive strategy.

4. **Plan Ahead Mode** (`/planAhead`): Launched from the welcome wizard ("Plan Ahead" mode). An application that plans ahead before executing tasks using the `PlanAheadMode`
   cognitive strategy.

## Installation

### Package Installation

The application can be packaged for different platforms:

- **Windows**: MSI installer
- **macOS**: DMG package
- **Linux**: DEB package

Each package includes:

- The application executable
- Context menu integration for folders
- Desktop shortcuts

### Manual Installation

1. Build the project using Gradle:
   ```bash
   ./gradlew shadowJar
   ```

2. Run the application (starts the DaemonClient, which manages the AppServer):
   ```
   java -jar build/libs/cognotik-<version>-all.jar
   ```

## Development

### Building from Source

1. Clone the repository
2. Build the project (including the shadow JAR):
   ```
   ./gradlew shadowJar
   ```

### Running in Development Mode

```
./gradlew run
```

### Creating Platform-Specific Packages

```
./gradlew package
```

This will create the appropriate package for your platform:

- Windows: `./gradlew packageMsi`
- macOS: `./gradlew packageDmg`
- Linux: `./gradlew packageDeb`

## Architecture

The application follows a modular architecture:

1. **DaemonClient**: Entry point that manages the server process
2. **AppServer**: Core server that hosts web applications
3. **SystemTrayManager**: UI integration via system tray (part of AppServer)
4. **UpdateManager**: Handles application updates (used by SystemTrayManager/AppServer)
5. **Web Applications**: Individual applications hosted by the server (e.g., BasicChatApp, UnifiedPlanApp)
6. **Welcome Wizard (`welcome.html`)**: Initial configuration and session launch UI.

Communication between components:

- GitHub API for update checks

## Configuration

The server uses sensible defaults but can be configured via command-line options (passed to `AppServer` via `DaemonClient`)
and a web-based welcome wizard. It automatically finds available ports
if the default ports are in use.

### API Key Configuration

You'll need to configure your API keys before using the AI features:

1. Launch the application
2. The welcome wizard (`/`) will load automatically.
3. Click the "User Settings" button in the top-right menu bar.
4. In the modal dialog:
    - Go to the "API Keys" tab.
    - Enter your API keys for the services you want to use (OpenAI, Anthropic, Groq, etc.).
    - Go to the "Local Tools" tab (optional).
    - Add paths to any local command-line tools you want the AI to be able to use.
    - Click "Save Settings".
5. Your API keys and tool paths are stored locally and are only used to authenticate with the respective services or execute the specified tools.

### Session Configuration (via Welcome Wizard)

Before launching an AI session, the welcome wizard guides you through:
- **Choosing a Cognitive Mode:** Chat, Autonomous, or Plan Ahead.
- **Configuring Settings:** Selecting default and parsing AI models, setting the working directory, adjusting temperature, and enabling/disabling auto-fix.
- **Selecting Tasks:** Enabling specific capabilities like file modification, shell execution, web search, etc.

## Troubleshooting

- **Server not starting**: Check if another instance is already running
- **Port conflicts**: The server will automatically find an available port
- **System tray not showing**: Ensure your system supports system tray icons
To check for updates manually, use the "Update to..." option in the system tray menu (if an update is available).

To stop a running server:

```
java -cp <classpath> com.simiacryptus.cognotik.DaemonClient --stop
```

Or use the system tray menu's "Exit" option.