# Cognotik Application Server Documentation

## Overview

The Cognotik Application Server is a modular platform for hosting AI-powered applications. It provides a daemon-based
architecture that allows applications to run in the background and be accessed via a web interface. The server includes
system tray integration, socket-based communication for remote control, and various AI-powered applications.

## Core Components

### AppServer

The main server component that hosts web applications and provides the core functionality.

**Key Features:**

- Web server hosting multiple AI applications
- System tray integration for easy access
- Socket-based communication for remote control
- Authentication and authorization management

**Usage:**

```
java -cp <classpath> com.simiacryptus.cognotik.AppServer server [options]
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

- System tray icon with context menu
- Quick access to hosted applications
- Server shutdown option

## Included Applications

The server hosts several AI-powered applications:

1. **Basic Chat** (`/chat`): A simple chat interface for interacting with AI models.

2. **Task Runner** (`/singleTask`): A single-task execution environment using the `SingleTaskMode` cognitive strategy.

3. **Auto Plan** (`/autoPlan`): An application that automatically plans and executes tasks using the `AutoPlanMode`
   cognitive strategy.

4. **Plan Ahead** (`/planAhead`): An application that plans ahead before executing tasks using the `PlanAheadMode`
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
   ```
   ./gradlew shadowJar
   ```

2. Run the application:
   ```
   java -jar build/libs/cognotik-<version>-all.jar
   ```

## Development

### Building from Source

1. Clone the repository
2. Build using Gradle:
   ```
   ./gradlew build
   ```

### Running in Development Mode

```
./gradlew runServer
```

### Creating Platform Packages

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
3. **SystemTrayManager**: UI integration via system tray
4. **Web Applications**: Individual applications hosted by the server

Communication between components:

- Socket-based communication between DaemonClient and AppServer
- HTTP/WebSocket for web applications
- System tray for user interaction

## Configuration

The server uses sensible defaults but can be configured via command-line options. It automatically finds available ports
if the default ports are in use.

## Troubleshooting

- **Server not starting**: Check if another instance is already running
- **Port conflicts**: The server will automatically find an available port
- **System tray not showing**: Ensure your system supports system tray icons

To stop a running server:

```
java -cp <classpath> com.simiacryptus.cognotik.DaemonClient --stop
```

Or use the system tray menu's "Exit" option.
