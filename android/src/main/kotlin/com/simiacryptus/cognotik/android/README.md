# Android Cognotik Implementation

This package contains the Android-specific implementation of the Cognotik platform. It adapts the core Cognotik logic—originally designed for desktop environments—to run efficiently within the Android lifecycle, using a background service to host a local Jetty server and a `WebView` for the user interface.

## Core Components

### [AndroidCognotikApps.kt](./AndroidCognotikApps.kt)
The central logic provider for the Android application. It extends `ApplicationDirectory` and is responsible for:
- **Server Configuration**: Setting up the Jetty server and defining the web application routes.
- **App Suite**: Initializing the suite of Cognotik applications including Chat, Task-Runner, Auto-Plan, Plan-Ahead, and Goal-Oriented modes.
- **Platform Adaptation**: Removing desktop-specific features (like system tray and daemon sockets) and providing mock authentication/authorization managers suitable for local device use.
- **Resource Management**: Dynamically generating the welcome page and managing local file paths within the Android `filesDir`.
- **Port Discovery**: Automatically finding available network ports to avoid conflicts.

### [CognotikService.kt](./CognotikService.kt)
A background `Service` that manages the lifecycle of the Cognotik server.
- **Persistence**: Ensures the Jetty server continues running independently of the UI activity state.
- **Concurrency**: Launches the server within a Kotlin Coroutine (`Dispatchers.IO`) to prevent blocking the main thread.
- **Status Monitoring**: Provides a `ServerStatusListener` interface for UI components to track server startup, errors, and port assignments.
- **System Diagnostics**: Logs detailed system information (memory, storage, architecture) to assist in debugging environment-specific issues.

### [CognotikActivity.kt](./CognotikActivity.kt)
The primary user interface component.
- **WebView Integration**: Hosts a fully configured `WebView` (JavaScript enabled, DOM storage, zoom controls) to render the Cognotik web interface.
- **Service Binding**: Manages the connection to `CognotikService` and reacts to server status changes.
- **User Controls**: Implements `SwipeRefreshLayout` and a Floating Action Button (FAB) for easy interface reloading.
- **Lifecycle Handling**: Manages back-button navigation within the WebView history and ensures proper service unbinding on destruction.

### [CognotikApplication.kt](./CognotikApplication.kt)
The custom `Application` class for global initialization.
- **Emoji Support**: Provides thread-safe, bundled `EmojiCompat` initialization to ensure consistent emoji rendering across different Android versions.
- **Logging**: Configures SLF4J (Simple Logging Facade for Java) properties for Android-compatible log output.

## Architecture Overview

The system follows a client-server architecture hosted entirely on the local device:

1.  **Initialization**: `CognotikApplication` sets up logging and emoji support.
2.  **Service Start**: `CognotikActivity` starts and binds to `CognotikService`.
3.  **Server Launch**: The service uses `AndroidCognotikApps` to start a Jetty server on a background thread.
4.  **UI Rendering**: Once the server is ready, the activity loads `http://localhost:[port]` into the `WebView`.
5.  **Interaction**: User interactions in the WebView are handled by the local Jetty server, which invokes the Cognotik planning and chat logic.

## Key Features

- **Local Execution**: All AI orchestration logic runs locally on the device.
- **Resilience**: The background service prevents server interruption during configuration changes (like screen rotation).
- **Dynamic Port Allocation**: Prevents "Address already in use" errors by searching for available ports starting from `12891`.
- **Integrated Debugging**: Comprehensive logging of WebView console messages and server-side events to the Android Logcat.