---
documents:
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/platform/PluginManager.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/platform/model/PluginManagerInterface.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/plugins/AuthorizationChain.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/plugins/AuthorizationStep.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/plugins/CallbackResult.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/plugins/PendingAuthorization.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/PluginManagerServlet.kt
---

# Plugin Platform

The plugin platform provides a runtime extension mechanism for Cognotik. Plugins are packaged as JAR files, discovered
via the Java `ServiceLoader` mechanism, and managed through a web-based administration interface. The platform also
includes a multi-step authorization framework that plugins can use to gate sensitive operations behind interactive
approval flows.

## Architecture Overview

The plugin platform consists of three major subsystems:

1. **Plugin Management** — Loading, unloading, persisting, and discovering plugin JARs at runtime.
2. **Authorization Framework** — A composable chain of authorization steps supporting both programmatic and
   web-interactive flows.
3. **Web Administration** — A servlet-based UI for managing plugins and triggering authorization flows.

---

## Plugin Management

### PluginManagerInterface

`com.simiacryptus.cognotik.platform.model.PluginManagerInterface` defines the contract for plugin lifecycle management:

| Method                                                                       | Description                                                                                                                                                                                                               |
|------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loadPlugin(jarFile: File): List<CognotikPlugin>`                            | Load a JAR and initialize all `CognotikPlugin` implementations discovered via `ServiceLoader`. Throws `IllegalArgumentException` if the file doesn't exist or isn't a JAR, and `IllegalStateException` if already loaded. |
| `loadPlugin(jarFile: File, entryPointClass: String): CognotikPlugin`         | Load a JAR and initialize a specific plugin class by fully-qualified name. The class must implement `CognotikPlugin`.                                                                                                     |
| `loadPluginsFromDirectory(directory: File): Map<File, List<CognotikPlugin>>` | Scan a directory for JAR files and load all plugins from each. Already-loaded JARs are skipped.                                                                                                                           |
| `unloadPlugin(jarFile: File)`                                                | Unload a previously loaded JAR, calling `unload()` on each plugin and closing the classloader. Classes already loaded remain in memory until garbage collected.                                                           |
| `deletePlugin(jarFile: File)`                                                | Delete a JAR from disk. If currently loaded, it is unloaded first. Throws `IllegalArgumentException` if the file doesn't exist.                                                                                           |
| `getLoadedPlugins(): Map<String, List<CognotikPlugin>>`                      | Return a snapshot of all loaded plugins, keyed by canonical JAR path.                                                                                                                                                     |
| `isLoaded(jarFile: File): Boolean`                                           | Check whether a JAR is currently loaded.                                                                                                                                                                                  |
| `subscribeToChanges(subscriber: () -> Unit)`                                 | Register a callback invoked whenever the plugin set changes.                                                                                                                                                              |
| `triggerChangeNotification()`                                                | Manually fire all registered change subscribers.                                                                                                                                                                          |

### PluginManager

`com.simiacryptus.cognotik.util.PluginManager` is the concrete implementation. Key implementation details:

- **Plugin directory**: Defaults to `./plugins`. Created on initialization if it doesn't exist.
- **Classloader isolation**: Each JAR gets its own `URLClassLoader` with the application classloader as parent. The
  classloader is named `"Plugin: <jarName>"` for debugging.
- **Thread safety**: `loadedJars`, `loadedPlugins`, and `loadedPluginEntries` are `ConcurrentHashMap` instances.
  Mutation operations (`loadPlugin`, `unloadPlugin`) are `synchronized` on the `PluginManager` instance.
- **ServiceLoader discovery**: When loading without an explicit entry point,
  `ServiceLoader.load(CognotikPlugin::class.java, classLoader)` discovers implementations listed in
  `META-INF/services/com.simiacryptus.cognotik.CognotikPlugin` within the JAR.
- **Empty JAR cleanup**: If no `CognotikPlugin` implementations are found via ServiceLoader, the classloader is closed
  and removed — the JAR is not considered loaded.

#### Persistence

Plugin state is persisted to a JSON manifest file (`plugins/plugins-manifest.json`) so that plugins survive application
restarts:

- **PluginEntry**: A data class storing `jarPath` and an optional `entryPointClass`.
- **saveManifest()**: Serializes all `loadedPluginEntries` to the manifest file after every load/unload operation.
- **loadManifest()**: Reads the manifest file on startup.
- **restorePlugins()**: Called during `init`. Iterates over manifest entries and reloads each JAR. Missing JARs are
  logged and skipped.

#### Plugin Lifecycle

1. **Discovery**: JARs are found by scanning the plugin directory or specified explicitly by path.
2. **Loading**: A `URLClassLoader` is created for the JAR. Plugins are discovered via `ServiceLoader` or instantiated by
   class name.
3. **Initialization**: `plugin.init()` is called on each discovered plugin. Failures are logged but don't prevent other
   plugins in the same JAR from loading.
4. **Runtime**: Plugins are active and accessible via `getLoadedPlugins()`.
5. **Unloading**: `plugin.unload()` is called on each plugin, then the classloader is closed.
6. **Deletion**: The JAR file is removed from disk (unloading first if necessary).

#### Change Notification

Subscribers registered via `subscribeToChanges()` are notified after:

- A plugin JAR is successfully loaded (with at least one plugin)
- A plugin JAR is unloaded
- A plugin JAR is deleted
- Plugins are loaded from a directory

---

## Authorization Framework

The authorization framework provides a composable, multi-step authorization mechanism that supports both headless (
programmatic) and web-interactive flows.

### AuthorizationStep

`com.simiacryptus.cognotik.plugins.AuthorizationStep` is a functional interface representing a single authorization
gate:

```kotlin
fun interface AuthorizationStep {
  fun authorize(onSuccess: () -> Unit, onFailure: (reason: String) -> Unit)
}
```

**Dual-mode operation:**

| Mode            | Methods Used                                                        | Description                                                                                                              |
|-----------------|---------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| Programmatic    | `authorize(onSuccess, onFailure)`                                   | Callbacks are invoked directly. Non-interactive steps must invoke callbacks synchronously when used in web flow context. |
| Web-interactive | `renderHtml(callbackUrl, sessionId)` + `handleCallback(parameters)` | HTML is rendered for the user; form submissions are processed via `handleCallback`.                                      |

**Key methods:**

- `requiresWebInteraction(): Boolean` — Returns `false` by default. Override to `true` for steps that need user
  interaction via the web UI.
- `renderHtml(callbackUrl, sessionId): String` — Returns HTML with a form that POSTs back to the callback URL. The
  default implementation renders Approve/Deny buttons. The `sessionId` is HTML-escaped to prevent XSS.
- `handleCallback(parameters: Map<String, String>): CallbackResult` — Processes callback parameters. Internal
  parameters (`action`, `sessionId`, `chain`) are filtered out before being passed to this method. Default
  implementation checks for `approve=true` or `deny=true`.

### CallbackResult

`com.simiacryptus.cognotik.plugins.CallbackResult` is a sealed class representing the outcome of a web callback:

| Variant                    | Description                                                                    |
|----------------------------|--------------------------------------------------------------------------------|
| `Success`                  | Step succeeded; proceed to the next step.                                      |
| `Failure(reason: String)`  | Step failed with a human-readable reason.                                      |
| `Redirect(url: String)`    | Redirect the user to a URL (e.g., for OAuth flows). Does not advance the step. |
| `RenderHtml(html: String)` | Render additional HTML (e.g., multi-page forms). Does not advance the step.    |

### AuthorizationChain

`com.simiacryptus.cognotik.plugins.AuthorizationChain` chains multiple `AuthorizationStep` instances together. All steps
must succeed in order for the chain to succeed.

#### Construction

Use the builder DSL:

```kotlin
val chain = AuthorizationChain.build {
  step(MyFirstStep())
  step(MySecondStep())
  step(MyThirdStep())
}
```

At least one step is required.

#### Programmatic Execution

```kotlin
chain.execute(
  onSuccess = { /* all steps passed */ },
  onFailure = { reason -> /* a step failed */ }
)
```

Steps are executed sequentially. If any step fails, no further steps are attempted and `onFailure` is called. Exceptions
thrown by steps are caught and converted to failure reasons.

#### Web-Interactive Execution

The web flow uses session-based state management:

1. **Start**: `chain.startWebFlow()` creates an `AuthorizationSession` with a unique UUID, skips any leading
   non-interactive steps, and registers the session.
2. **Render**: The current step's `renderHtml()` is called to present UI to the user.
3. **Callback**: User responses are processed via `chain.handleWebCallback(sessionId, parameters)`.
4. **Advance**: On `CallbackResult.Success`, the chain advances to the next step. On `Failure`, the session is marked
   failed. `Redirect` and `RenderHtml` results do not advance the step.
5. **Completion**: When all steps pass, the session status becomes `COMPLETED`.

**AuthorizationSession** tracks:

- `sessionId: String` — Unique identifier
- `currentStepIndex: Int` — Current position in the chain (volatile)
- `status: SessionStatus` — `IN_PROGRESS`, `COMPLETED`, or `FAILED` (volatile)
- `failureReason: String?` — Set on failure (volatile)
- `metadata: ConcurrentHashMap<String, Any>` — Arbitrary metadata storage
- `createdAt: Long` — Creation timestamp

**Session management:**

- Sessions are stored in a static `ConcurrentHashMap` keyed by session ID.
- Sessions expire after 30 minutes (`SESSION_TIMEOUT_MS`). Expired sessions are cleaned up lazily when `getSession()` is
  called.
- `removeSession(sessionId)` explicitly removes a session.

**Non-interactive step handling in web flows:**
When `startWebFlow()` is called or after a step succeeds, `advancePastNonInteractiveSteps()` automatically executes any
consecutive non-interactive steps (those where `requiresWebInteraction()` returns `false`). These steps must invoke
their callbacks synchronously. If a non-interactive step doesn't invoke either callback before returning, it is treated
as interactive and the chain pauses.

### PendingAuthorization

`com.simiacryptus.cognotik.plugins.PendingAuthorization` represents a deferred authorization flow that can be registered
and later triggered from the web UI.

**Fields:**

- `id: String` — Unique identifier (UUID by default)
- `pluginName: String` — Name of the plugin requesting authorization
- `chain: AuthorizationChain` — The authorization chain to execute
- `onSuccess: () -> Unit` — Callback on successful authorization
- `onFailure: (reason: String) -> Unit` — Callback on failure
- `status: Status` — `PENDING`, `IN_PROGRESS`, `COMPLETED`, or `FAILED` (volatile)

**Static registry methods:**

| Method              | Description                                                                                                                                            |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `register(pending)` | Register a pending authorization, returns its ID.                                                                                                      |
| `getAll()`          | Get all pending authorizations (snapshot).                                                                                                             |
| `getPending()`      | Get only those in `PENDING` state.                                                                                                                     |
| `get(id)`           | Get a specific authorization by ID.                                                                                                                    |
| `remove(id)`        | Remove an authorization.                                                                                                                               |
| `removeCompleted()` | Remove all `COMPLETED` or `FAILED` authorizations.                                                                                                     |
| `execute(id)`       | Execute a pending authorization. Transitions through `IN_PROGRESS` → `COMPLETED`/`FAILED`. Exceptions in the chain or callbacks are caught and logged. |

---

## Web Administration — PluginManagerServlet

`com.simiacryptus.cognotik.webui.servlet.PluginManagerServlet` is mounted at `/pluginManager` and provides both a JSON
API and an HTML management interface.

### Access Control

All requests require authentication (via `authenticate(request, response)`) and `OperationType.Admin` authorization.
Unauthorized requests receive HTTP 403.

### Multipart Configuration

The servlet supports file uploads with:

- Max file size: 50 MB
- Max request size: 100 MB

### GET Endpoints

| Parameter (`action`)                  | Accept Header      | Description                                                                                                                                                                                                |
|---------------------------------------|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `list` (or `application/json` Accept) | `application/json` | Returns JSON array of loaded plugins with JAR paths, plugin names, and class names.                                                                                                                        |
| `scan`                                | `application/json` | Lists all JAR files in the plugin directory with name, path, size, and loaded status.                                                                                                                      |
| `authChains`                          | `application/json` | Lists registered authorization chain names.                                                                                                                                                                |
| `authStatus`                          | `application/json` | Returns status of an authorization session. Requires `sessionId` parameter. Returns session ID, status, current step, total steps, completion flag, and failure reason.                                    |
| `authStep`                            | `text/html`        | Renders the current authorization step's HTML with a progress bar. Requires `sessionId` parameter. Shows completion/failure status if the session is done. Completed sessions are removed after rendering. |
| `authCallback`                        | varies             | Handles GET-based authorization callbacks (e.g., OAuth redirects). Requires `sessionId` parameter.                                                                                                         |
| *(none/default)*                      | `text/html`        | Serves the full Plugin Manager HTML page.                                                                                                                                                                  |

### POST Endpoints

All POST responses use `application/json` content type (except auth callbacks which may redirect or render HTML).

| Action          | Parameters                                                                         | Description                                                                                                                                                                                                                                     |
|-----------------|------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `load`          | `jar` (required), `entryPoint` (optional)                                          | Load a plugin JAR. Path can be relative to plugin directory or absolute. Returns 409 if already loaded.                                                                                                                                         |
| `unload`        | `jar` (required)                                                                   | Unload a loaded plugin JAR.                                                                                                                                                                                                                     |
| `upload`        | `jarFile` (multipart file, required), `autoLoad` (optional, `"true"` to auto-load) | Upload a JAR to the plugin directory. Optionally loads it immediately.                                                                                                                                                                          |
| `loadDirectory` | `directory` (optional, defaults to plugin directory)                               | Load all JARs from a directory.                                                                                                                                                                                                                 |
| `delete`        | `jar` (required)                                                                   | Delete a JAR from disk (unloads first if loaded). Returns 404 if file doesn't exist.                                                                                                                                                            |
| `startAuth`     | `chain` (required)                                                                 | Start a web authorization flow for a named chain. Returns session ID and status. If no steps are required, returns immediate completion.                                                                                                        |
| `authCallback`  | `sessionId` (required), plus step-specific parameters                              | Process an authorization callback. Internal parameters (`action`, `sessionId`, `chain`) are filtered before forwarding to the step. Redirects to the step page on success/failure, follows `Redirect` results, or renders `RenderHtml` results. |

### Authorization Chain Registry

The servlet maintains a `ConcurrentHashMap<String, AuthorizationChain>` of named authorization chains:

- `registerAuthorizationChain(name, chain)` — Register a chain that can be triggered via the web UI.
- `unregisterAuthorizationChain(name)` — Remove a registered chain.

### HTML Interface

The Plugin Manager page provides:

1. **Authorization Chains** — Table of registered chains with "Start Authorization" buttons. Starting a chain redirects
   to the interactive step page.
2. **Loaded Plugins** — Table showing loaded JARs, their plugins (name and class), with Unload and Delete buttons.
3. **Available JARs** — Scannable directory listing with file sizes, loaded/unloaded badges, and Load/Delete buttons.
   Includes a "Load All from Directory" button.
4. **Load by Path** — Manual JAR path input with optional entry point class.
5. **Upload Plugin JAR** — File upload form with auto-load checkbox.

A message banner displays operation results (success/error/info) with a 6-second auto-dismiss.

### Security Considerations

- All user-supplied strings in JSON error responses are escaped via `jsonEscape()` to prevent injection.
- Session IDs in HTML are escaped to prevent XSS (`&`, `"`, `<`, `>` are encoded).
- Internal request parameters (`action`, `sessionId`, `chain`) are filtered out before being forwarded to authorization
  step callbacks via the `INTERNAL_PARAMS` set.
- File uploads are restricted to `.jar` extensions.
- JAR paths can be relative (resolved against the plugin directory) or absolute.

---

## Plugin Development Guide

To create a plugin:

1. **Implement `CognotikPlugin`**: Create a class implementing `com.simiacryptus.cognotik.CognotikPlugin` with
   `pluginName`, `init()`, and `unload()`.

2. **Register via ServiceLoader**: Create `META-INF/services/com.simiacryptus.cognotik.CognotikPlugin` in your JAR's
   resources, listing the fully-qualified class name(s) of your plugin implementation(s).

3. **Package as JAR**: Build a JAR containing your plugin classes and the service file.

4. **Deploy**: Either:
    - Place the JAR in the `./plugins` directory (it will be restored on restart if previously loaded)
    - Upload via the web UI
    - Load programmatically via `PluginManager.loadPlugin(jarFile)` or `loadPlugin(jarFile, entryPointClass)`

### Using Authorization in Plugins

Plugins that need user authorization can build chains:

```kotlin
val chain = AuthorizationChain.build {
  step(MyPermissionCheckStep())      // Non-interactive check
  step(MyUserApprovalStep())         // Interactive web step
}

// Option 1: Programmatic execution
chain.execute(
  onSuccess = { /* proceed */ },
  onFailure = { reason -> /* handle denial */ }
)

// Option 2: Register for web-based triggering
PendingAuthorization.register(
  PendingAuthorization(
  pluginName = "My Plugin",
  chain = chain,
  onSuccess = { /* proceed */ },
  onFailure = { reason -> /* handle denial */ }
))

// Option 3: Register as a named chain on the servlet
pluginManagerServlet.registerAuthorizationChain("my-plugin-auth", chain)
```

For custom interactive steps, override `requiresWebInteraction()` to return `true`, implement `renderHtml()` to present
your UI, and implement `handleCallback()` to process responses.