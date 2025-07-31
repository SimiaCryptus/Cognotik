# Android Module for Cognotik

This module provides Android app packaging for the Cognotik desktop application, adapting the core functionality for mobile devices.

## Overview

The Android module creates a native Android application that:
- Runs the Cognotik web server locally on the Android device
- Provides a WebView-based interface to access all Cognotik features
- Removes desktop-specific dependencies (JavaFX, System Tray, etc.)
- Adapts file system access for Android's sandboxed environment

## Key Components

### 1. AndroidCognotikApps.kt
Adapted version of the desktop `CognotikApps` class that:
- Removes system tray and daemon functionality
- Uses Android's file system directories
- Provides the same web applications (chat, task planning, etc.)
- Handles port management for mobile environment

### 2. MainActivity.kt
Main Android activity that:
- Starts the Cognotik service
- Displays a WebView to access the web interface
- Shows loading progress while server starts
- Handles navigation within the web application

### 3. CognotikService.kt
Background service that:
- Manages the Jetty web server lifecycle
- Runs in the background to keep server available
- Handles server startup/shutdown
- Provides status callbacks to the UI

### 4. AndroidManifest.xml
Defines:
- Required permissions (Internet, file access, etc.)
- Service and activity declarations
- App metadata and launch configuration

## Architecture

```
MainActivity (UI)
    ├── WebView (displays Cognotik web interface)
    └── CognotikService (background server)
        └── AndroidCognotikApps (adapted server logic)
            ├── Core modules (shared with desktop)
            ├── WebUI modules (shared with desktop)
            └── Chat/Planning apps (shared functionality)
```

## Key Adaptations from Desktop

### Removed Components
- **System Tray Manager**: Not available on Android
- **Daemon Client**: Not needed in mobile context
- **JavaFX Dependencies**: Android uses WebView instead
- **Desktop-specific file associations**: Android handles differently

### Modified Components
- **File System Access**: Uses Android's app-specific directories
- **Port Management**: Uses dynamic port allocation for mobile environment
- **Authentication**: Simplified for single-user mobile context
- **Server Lifecycle**: Managed by Android Service instead of system daemon

### Preserved Components
- **Core Logic**: All planning and AI functionality preserved
- **Web Applications**: Chat, task planning, goal-oriented modes
- **API Integrations**: Same AI model integrations
- **WebUI**: Same web interface, accessed via WebView

## Dependencies

The Android module reuses most dependencies from the desktop module but excludes:
- JavaFX components (`openjfx-*`)
- System-specific libraries (`batik-*` for desktop icon handling)

## Build Configuration

### Prerequisites
- Android SDK 26+ (Android 8.0+)
- Kotlin Android plugin
- Android Gradle Plugin 8.1.4+

### Build Steps
1. Ensure Android SDK is installed
2. Add Google repository to Gradle configuration
3. Enable the android module in `settings.gradle.kts`
4. Run: `./gradlew :android:assembleDebug`

### Installation
The build produces an APK that can be:
- Installed directly on Android devices
- Distributed through app stores
- Side-loaded for testing

## Usage

1. Install the APK on an Android device
2. Launch the Cognotik app
3. Wait for the server to start (shown in loading screen)
4. Use the web interface through the embedded WebView
5. Access all desktop features: chat, planning, goal-oriented AI assistance

## Technical Notes

### Memory Management
- The Jetty server runs in-process within the Android app
- Memory usage is optimized for mobile devices
- Server automatically manages resources

### Networking
- Server binds to localhost only for security
- Uses dynamic port allocation to avoid conflicts
- All communication stays local to the device

### Data Storage
- Uses Android's app-specific file directories
- Data is sandboxed and secure
- Follows Android storage best practices

### Performance
- Optimized for mobile CPU and memory constraints
- WebView provides efficient rendering
- Background service ensures responsive UI

## Future Enhancements

- Push notifications for long-running tasks
- Integration with Android sharing system
- Voice input integration
- Offline mode capabilities
- Cloud synchronization options

## Development Notes

This adaptation demonstrates how to:
1. Port JVM-based server applications to Android
2. Maintain feature parity while adapting to mobile constraints
3. Reuse existing business logic across platforms
4. Handle platform-specific concerns (UI, file system, networking)

The pattern used here can be applied to other desktop applications that need mobile versions while preserving core functionality.