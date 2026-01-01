# User Settings Subsystem Developer Guide

## 1. Overview
The User Settings subsystem is responsible for managing user-specific configurations, primarily focusing on **API credentials** (for LLM providers like OpenAI, Anthropic, etc.) and **local tool configurations** (paths to executables like Git, Python, Docker).

It provides a persistent, file-based storage mechanism, a JSON serialization layer that handles backward compatibility, and a web servlet for UI interaction with security masking for sensitive keys.

## 2. Architecture

The subsystem is built around a central interface and a file-based implementation, integrated into the web server via a Servlet.

### Core Components
*   **`UserSettingsInterface`**: Defines the contract for retrieving and updating settings.
*   **`UserSettingsManager`**: The concrete implementation that manages file I/O and in-memory caching.
*   **`UserSettings`**: The data transfer object (DTO) representing the configuration state.
*   **`UserSettingsServlet`**: Handles HTTP GET/POST requests to view and modify settings via the Web UI.

## 3. Data Model

The `UserSettings` class is the root container. It has evolved from a map-based structure to a list-based structure to support multiple configurations per provider.

### Structure (`UserSettings`)
| Property | Type | Description |
| :--- | :--- | :--- |
| **`apis`** | `MutableList<ApiData>` | **Primary.** Contains configurations for API providers (Key, Base URL, Provider Type). |
| **`tools`** | `MutableList<ToolData>` | **Primary.** Contains paths to local executables (e.g., path to `python.exe`). |
| **`etc`** | `MutableMap<String, Any>` | A generic map for miscellaneous configuration flags. |
| *`apiBase`* | `Map` | *Deprecated.* Read-only property for backward compatibility. |

### Sub-Models
*   **`ApiData`**: Represents a connection to an AI provider.
    *   `provider`: Enum (`APIProvider`) e.g., OpenAI, Google.
    *   `key`: The API Key (String).
    *   `baseUrl`: Optional override for the API endpoint.
    *   `name`: Optional display name.
*   **`ToolData`**: Represents a local tool.
    *   `provider`: Enum (`ToolProvider`) e.g., Git, Python.
    *   `path`: Absolute path to the executable.

## 4. Persistence & Storage

### File System
Settings are stored as JSON files on the local disk.
*   **Location**: Defined by the `root` parameter passed to `UserSettingsManager`.
*   **Naming Convention**: `<User.toString()>.json` (e.g., `user@localhost.json`).

### Caching
The `UserSettingsManager` maintains an in-memory `HashMap<User, UserSettings>` cache.
*   **Read**: Checks cache first. If missing, reads from disk. If file is missing, creates a new default instance.
*   **Write**: Updates cache immediately and writes to disk synchronously.

## 5. Serialization & Migration

The subsystem uses custom Jackson serializers/deserializers to handle schema evolution seamlessly.

### Deserialization (`UserSettingsDeserializer`)
This class contains logic to migrate legacy configuration files to the new format automatically.

1.  **New Format Detection**: Checks if the JSON contains `apis` or `tools`. If so, it loads them directly.
2.  **Legacy Fallback**: If new fields are missing, it looks for `apiKeys` and `apiBase` (old maps). It converts these maps into the `List<ApiData>` format via the `toApiList` helper function.
3.  **Tool Discovery**: If the `tools` list is empty after loading, it calls `discoverAllToolsFromPath()`. This scans the system `PATH` environment variable to automatically populate available tools (Git, Java, Python, etc.).

### Serialization (`UserSettingsSerializer`)
Always writes the data in the **new** format (`apis`, `tools`, `etc`), effectively migrating old files to the new structure upon the first save.

## 6. Web API & Security

The `UserSettingsServlet` exposes settings at `/userSettings`. It implements specific security logic to prevent API keys from being exposed to the client browser.

### GET Request (Retrieval)
When the UI requests settings:
1.  Loads the actual `UserSettings` object.
2.  Creates a **Visible Copy**:
    *   Iterates through `apis`.
    *   **Masking**: Replaces the actual API key with the string `"********"`.
    *   Preserves `baseUrl` and `provider`.
3.  Returns the masked JSON to the client.

### POST Request (Update)
When the UI saves settings:
1.  Receives the JSON (containing masked keys).
2.  Loads the **Previous Settings** from the manager.
3.  **Reconstruction Logic**:
    *   Iterates through the submitted `apis`.
    *   Checks the `key` field.
    *   **If `key == "********"`**: It assumes the user did not change the key. It retrieves the *actual* key from the `Previous Settings`.
    *   **If `key != "********"`**: It assumes the user entered a new key. It uses the new value.
4.  Merges new tools with existing tools.
5.  Saves the reconstructed object to disk.

## 7. Integration Guide

### Retrieving Settings in Code
To access the current user's settings from anywhere in the platform:

```kotlin
val user = ApplicationServices.authenticationManager.getUser(requestCookie)
val settings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(user)

// Access an API Key
val openAiKey = settings.apis.find { it.provider == APIProvider.OpenAI }?.key

// Access a Tool Path
val pythonPath = settings.tools.find { it.provider == ToolProvider.Python }?.path
```

### Adding a New API Provider
1.  Modify `com.simiacryptus.cognotik.models.APIProvider`.
2.  Add a new object extending `APIProvider`.
3.  Register it in the `init` block of the companion object.
4.  The `UserSettings` system will automatically handle serialization for the new enum value due to `DynamicEnum`.

### Adding a New Tool
1.  Modify `com.simiacryptus.cognotik.models.ToolProvider`.
2.  Add a new object extending `ToolProvider`.
3.  Implement `getExecutables()` (list of binary names, e.g., `["node", "npm"]`).
4.  Implement `getVersion(path)` for validation.
5.  Register it in the `init` block.
6.  The auto-discovery logic in `UserSettingsDeserializer` will now automatically find this tool on user systems.

## 8. Common Pitfalls

1.  **Masking Collision**: If a user actually sets their API key to the literal string `"********"`, the system will overwrite it with the previous key on the next save.
2.  **Manual File Editing**: If editing the JSON file manually while the server is running, changes might be overwritten because the `UserSettingsManager` caches the object in memory. Restart the server after manual edits.
3.  **Validation**: `ApiData.validate()` throws an `IllegalStateException` if a provider is set but the key is missing. This validation is enforced during the reconstruction phase in the Servlet.
