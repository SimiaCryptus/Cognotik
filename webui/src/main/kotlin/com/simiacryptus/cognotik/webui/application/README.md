# Web UI Application Framework

This package provides the core infrastructure for hosting and managing AI-powered web applications within the Cognotik platform. It leverages the Jetty web server to provide a robust, session-aware environment for chat-based and interactive AI tools.

## Key Components

### Server Infrastructure

*   **`ApplicationDirectory`**: An abstract base class designed to manage multiple child web applications. It handles:
    *   Jetty server initialization and lifecycle.
    *   OAuth2 authentication configuration (specifically Google OAuth).
    *   Registration of common servlets (User Info, Settings, Usage, API Keys, etc.).
    *   Automatic browser launching for local development.
    *   Android compatibility checks for class loading.
*   **`CognotikAppServer`**: A concrete server implementation used to run the Cognotik application environment. It includes built-in support for CORS filters and session proxying.
*   **`ApplicationServer`**: The primary base class for individual AI applications. It extends `ChatServer` and provides:
    *   Session-specific data storage and settings management.
    *   Security filters for authorization (Read/Write/etc.).
    *   Standardized API endpoints for application metadata (`/appInfo`), file management (`/fileIndex`, `/fileZip`), and session control.

### Session & Communication

*   **`ApplicationSocketManager`**: An abstract handler for WebSocket-based communication. It bridges the gap between the real-time web interface and the application's backend logic, routing user messages to the appropriate processing methods.
*   **`AppInfoData`**: A data transfer object (DTO) that carries application configuration to the frontend, including UI preferences like menu bar visibility and image loading settings.

### Specialized Applications

*   **`SymbolGraphApp`**: (In development) Integrates with `SymbolGraphService` to provide specialized graph-based analysis or visualization.

## Features

*   **Multi-Tenancy & Sessions**: Built-in support for user-isolated sessions with persistent storage for settings and chat history.
*   **Security**: Integrated OAuth2 flow and granular operation-based authorization checks.
*   **Extensibility**: Developers can create new AI applications by extending `ApplicationServer` and implementing the `userMessage` logic.
*   **Resource Management**: Efficient handling of static resources, multipart file uploads, and dynamic servlet registration.

## Usage Pattern

To create a new application in this framework:

1.  Extend `ApplicationServer` to define your application's metadata and session logic.
2.  Implement `userMessage` to handle incoming AI prompts.
3.  Register the application within an `ApplicationDirectory` instance to expose it via a specific URL path.
4.  Use `ApplicationSocketManager` to customize real-time interaction behaviors if necessary.