I now have a comprehensive picture of the entire plugability architecture. Let me compose the document.

---

# Plugability of the `fileserver` Micro-IDE — Cognotik 2.1.19

> **Branch:** [`2.1.19`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/tree/) — last commit 2026-07-31
> **Module root:** [`fileserver/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/tree/fileserver/)
> **CLI module:** [`cli/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/tree/cli/)

---

## 1. Overview

The `fileserver` module is a self-contained, embeddable file-browser / editor / Git-UI built on the Jakarta Servlet API. It is explicitly designed for extension: the module's own [README](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/README.md?L6-L8) describes the design principle as subclass-to-specialize — you implement `getDir()`, and the framework handles the rest.

The plugability surface is organized into four distinct tiers, each offering progressively deeper extension:

| Tier | Mechanism | Scope |
|------|-----------|-------|
| 1 | `FileServlet` subclass overrides | HTML rendering hooks (toolbar, per-file actions, scripts, styles) |
| 2 | `FilesystemServlet` overrides + `FsApiConfig` | FS API v1 capability gating and routing |
| 3 | `FsAction` DynamicEnum registration | Adding/replacing first-class API operations at runtime |
| 4 | `CognotikPlugin` / `ServiceLoader` | System-wide cross-cutting extension (model providers, task types, auth chains) |

---

## 2. Tier 1 — HTML Rendering Hooks (`FileServlet`)

[`FileServlet`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FileServlet.kt) is the abstract base class. It handles all five HTTP verbs (`GET`, `HEAD`, `POST`, `PUT`, `DELETE`) and assembles the directory listing via [`DirectoryListingRenderer`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/render/DirectoryListingRenderer.kt). The assembly point at [lines 379–391](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FileServlet.kt?L379-L391) calls every hook before rendering:

```kotlin
val model = DirectoryPageModel(
  toolbarActions = getToolbarActions(request, currentPathString) + gitToolbar,
  additionalSections = gitSection + getAdditionalSections(file, request, currentPathString),
  additionalStyles = getAdditionalStyles() + gitStyles,
  additionalScripts = getAdditionalScripts() + gitScripts,
  ...
)
```

### Extension Hook Table

All hooks are declared `open` with empty-string defaults at [lines 444–496](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FileServlet.kt?L444-L496):

| Hook | Return | Injected into |
|------|--------|---------------|
| `getFileActions(file, req)` | HTML string | After each file's link+edit in the listing |
| `getFolderActions(folder, req)` | HTML string | After each folder link |
| `getToolbarActions(req, currentPath)` | HTML string | Navbar, alongside ZIP and theme controls |
| `getAdditionalSections(dir, req, currentPath)` | HTML string | Between upload widget and folder/file lists |
| `getAdditionalStyles()` | CSS string | Appended to `<style>` block |
| `getAdditionalScripts()` | JS string | Appended to `<script>` block |
| `isGitEnabled(req)` | Boolean | Toggles entire Git panel |
| `getGitRoot(req, resp)` | `File?` | Root for Git repo discovery |
| `getZipLink(req, filePath)` | URL string | "Download as ZIP" button href |
| `listContents(file, req, resp)` | `Pair<String,String>` | Override entire file/folder HTML generation |
| `newUiRedirectUrl(req, currentPath)` | `String?` | Redirect directory GETs to an SPA |

There is no registration step; simply subclass `FileServlet`, implement `getDir()`, and override whichever hooks apply.

#### Real-World Implementation: `SimpleFileServlet`

[`SimpleFileServlet`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/SimpleFileServlet.kt) (the default mount in `FileServerCli`) is a complete example of all hooks in action:

- `getToolbarActions` injects IDE-view, Modify, Models, DocOps Plan, AutoFix, and Tasks buttons ([lines 62–80](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/SimpleFileServlet.kt?L62-L80))
- `getFileActions` adds per-markdown-file `📘 Plan` / `🚀 Run` links and a per-file `✏️ Modify` link ([lines 83–96](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/SimpleFileServlet.kt?L83-L96))
- `getAdditionalSections` injects the `COGNOTIK_FSAPI` configuration and the live task-output panel ([lines 98–113](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/SimpleFileServlet.kt?L98-L113))
- `getAdditionalScripts` delivers the entire polling/task/models client-side runtime (plain ES5, no bundler required) ([lines 126–255](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/SimpleFileServlet.kt?L126-L255))

---

## 3. Tier 2 — FS API v1 Capability Gating (`FilesystemServlet` + `FsApiConfig`)

[`FilesystemServlet`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FilesystemServlet.kt) extends `FileServlet` with the remote-filesystem HTTP API (`/.fsapi/v1/*`). It intercepts calls in `service()` before normal path parsing, so the `.fsapi` segment is reserved and never collides with real files.

The key extension points at [lines 51–59](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FilesystemServlet.kt?L51-L59):

```kotlin
open fun getFsApiRoot(req: HttpServletRequest, resp: HttpServletResponse): File? = getDir(req, resp)
open fun getFsApiConfig(req: HttpServletRequest): FsApiConfig = FsApiConfig(...)
open fun isFsApiEnabled(req: HttpServletRequest): Boolean = true
```

### `FsApiConfig` — Capability Declaration

[`FsApiConfig`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiConfig.kt) is a data class (all fields have defaults) that drives both server-side enforcement and client-side self-discovery via `GET /.fsapi/v1/meta`. Any capability absent from `meta` causes the client to generate a clean `ENOSYS` rather than silently failing.

Key toggles:

| Field | Default | Effect |
|-------|---------|--------|
| `readOnly` | `false` | All mutations answer `EROFS` |
| `execAllowlist` | `{}` | `cmd -> Set<subcommand>`; empty set = any subcommand allowed |
| `execAllowAny` | `false` | True = any bare command may be spawned (trusted loopback) |
| `execRestrictArguments` | `true` | Argument-injection hardening for `/exec` |
| `terminalEnabled` | `true` | Enables `/.fsapi/v1/terminal` sessions |
| `watchMode` | `"sse"` | `"sse"` / `"poll"` / `"none"` |
| `snapshotEnabled` | `true` | Enables `/.fsapi/v1/snapshot` |
| `resolveEnabled` | `true` | Enables CommonJS/ESM resolution |
| `maxTerminals` | `8` | Terminal session cap |
| `maxBatchOps` | `256` | Batch op limit |
| `maxFileSize` | `50MB` | Upload/read limit |

`SimpleFileServlet` demonstrates profile-based configuration: it builds a permissive profile (any exec, terminal on) for trusted loopback deployments and a hardened profile when `--secure` or `--read-only` flags are set ([lines 42–53](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/SimpleFileServlet.kt?L42-L53)).

---

## 4. Tier 3 — Runtime Operation Registry (`FsAction` DynamicEnum)

This is the deepest server-side extension tier. [`FsAction`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/action/FsAction.kt) is a `DynamicEnum` — operations are registered at runtime and dispatched by [`FsApiHandler`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiHandler.kt).

### Registering a Custom Operation

```kotlin
FsAction.register(
  FsAction(
    op = "hash",
    method = "GET",
    description = "SHA-256 of a file",
    parameters = listOf(ActionParam("path", required = true)),
    ui = ActionUi(
      title = "Hash File",
      icon = "🔒",
      menus = listOf(ActionMenu("file/context"))
    )
  ) { ctx ->
    val path = ctx.req.getParameter("path")
    // ... compute and write JSON response
  }
)
```

That single call makes the action:
- Dispatched by `FsApiHandler.handle()` ([line 293](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiHandler.kt?L293-L308))
- Listed in `GET /.fsapi/v1/actions` as part of the live registry ([lines 473–480](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiHandler.kt?L473-L480))
- Surfaced by the web UI as a first-class action (menus, command palette, Alt+Enter) via `uiDescriptor()` ([lines 202–230](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/action/FsAction.kt?L202-L230))

Operations can be replaced with `FsAction.register(action, replace = true)` and removed with `FsAction.unregister(method, op)`.

### `ActionUi` — Client-Side Integration Without JavaScript

The optional `ActionUi` data class (defined at [lines 109–137](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/action/FsAction.kt?L109-L137)) controls how the action appears in the web UI with no client-side code required:

| Field | Purpose |
|-------|---------|
| `title` | Display name in menus and palette |
| `icon` | Emoji/icon prefix |
| `category` | Command palette grouping |
| `menus` | List of `ActionMenu(anchor, group, order)` placement anchors |
| `selection` | `ActionSelection(min, max, kinds)` — governs when the action is enabled |
| `sendSelection` | `"none"` / `"paths"` / `"first"` / `"folder"` — how the file selection is sent |
| `hiddenParams` | Params filled by context, hidden in generated dialogs |
| `hideWhenDisabled` | Hide vs. grey-out when selection does not satisfy requirements |

### `ActionParam` — Self-Describing Parameters

[`ActionParam`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/action/FsAction.kt?L12-L75) describes each parameter for both the REST contract and the auto-generated dialog:

- `type`: `"string"` / `"boolean"` / `"int"` / `"array"` / `"object"`
- `location`: `"query"` / `"body"` / `"header"` / `"path"`
- `options`: static enum values → renders as a `<select>`
- `dynamic: true` + `paramResolvers` → options fetched live via `?resolveParam=<name>` when the dialog opens
- `multi: true` → checkbox list instead of single-value select

### Built-in Operations (all replaceable)

[`FsApiHandler`'s `init` block](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiHandler.kt?L68-L249) registers the complete built-in surface:

| Op | Methods | Description |
|----|---------|-------------|
| `meta` | GET/HEAD | API version, platform, limits, capabilities |
| `actions` | GET/HEAD | Self-description of all registered operations |
| `stat` | GET/HEAD/POST | `fs.stat` / `fs.lstat` / batch stat |
| `dir` | GET/HEAD/POST | `fs.readdir` / `fs.mkdir` |
| `file` | GET/HEAD/PUT/DELETE | Read / write / unlink with Range + ETag support |
| `rename` | POST | `fs.rename` (atomic move) |
| `copy` | POST | `fs.copyFile` / `fs.cp` |
| `truncate` | POST | `fs.truncate` |
| `utimes` | POST | `fs.utimes` (when `utimesEnabled`) |
| `realpath` | GET/HEAD | `fs.realpath` |
| `resolve` | GET/HEAD | CommonJS/ESM module resolution (when `resolveEnabled`) |
| `snapshot` | GET/HEAD | ZIP snapshot of a subtree (when `snapshotEnabled`) |
| `watch` | GET | SSE change stream (`fs.watch`) |
| `batch` | POST | Pipeline multiple ops in one round trip |
| `exec` | POST | Run an allowlisted child process |
| `git` | POST | Dispatch a registered `GitAction` |
| `terminal` | GET/POST/DELETE | List / create / close terminal sessions |
| `terminal/stream` | GET | SSE output stream (resumable via `?from=<seq>`) |
| `terminal/input` | POST | Write to stdin |
| `terminal/resize` | POST | Resize (cols/rows) |
| `terminal/signal` | POST | Send a signal (SIGTERM default) |

Git operations are themselves a separate `DynamicEnum` ([`GitAction`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/action/GitAction.kt)), allowing git sub-operations to be added or replaced independently.

---

## 5. Tier 4 — System-Wide Plugin Platform (`CognotikPlugin`)

The broadest extension tier uses [`CognotikPlugin`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/CognotikPlugin.kt) — a `ServiceLoader`-discoverable interface for JAR-packaged plugins. Plugins are managed by [`PluginManager`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/platform/PluginManager.kt) and exposed via [`PluginManagerServlet`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/PluginManagerServlet.kt).

```kotlin
interface CognotikPlugin {
    val pluginName: String get() = javaClass.simpleName
    fun initializePlugin() {}
}
```

### Plugin Lifecycle

1. Drop a JAR into `./plugins/` (or upload via the web admin UI)
2. `PluginManager.loadPlugin(jarFile)` creates an isolated `URLClassLoader` named `"Plugin: <jarName>"`
3. `ServiceLoader.load(CognotikPlugin::class.java, classLoader)` discovers implementations listed in `META-INF/services/com.simiacryptus.cognotik.CognotikPlugin`
4. `initializePlugin()` is called — the plugin registers `DynamicEnum` constants (e.g. `FsAction`, `TaskType`, `APIProvider`)
5. `PluginEvents` pub/sub (via `PluginManager.subscribe`) notifies consumers — including auth chain registration/unregistration

### What a Plugin Can Register

Because `FsAction`, `GitAction`, and `TaskType` are all `DynamicEnum` instances, a plugin's `initializePlugin()` can:

- **Add new FS API operations**: `FsAction.register(FsAction("my-op", "POST", ...) { ctx -> ... })`
- **Replace built-in operations**: `FsAction.register(..., replace = true)`
- **Add git sub-operations**: `GitAction.register(...)`
- **Register new AI task types** for DocOps / AutoFix
- **Add API providers** (new model endpoints)
- **Register authorization chains** via `PluginEvents.REGISTER_AUTH_CHAIN`

Plugins can be hot-loaded and hot-unloaded at runtime without restarting the server.

---

## 6. Agentic Action Extensions (`ServerTaskActions`, `ModifyFilesActions`)

The CLI module demonstrates how to build high-level agentic actions on top of the FS API extension points:

### DocOps + AutoFix (`ServerTaskActions`)

[`ServerTaskActions.install(cfg)`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/ServerTaskActions.kt?L63-L84) registers three `FsAction` operations by delegating to `DocOpsFsActions.install(...)`:

- `POST /.fsapi/v1/docops?command=plan|run|status|vars|models`
- `POST /.fsapi/v1/autofix?cmd=<command>`
- `GET /.fsapi/v1/tasks[?id=<taskId>]`

The pattern is: install is idempotent, actions are (re)configured with `refreshModels()` when the web UI changes the model selection, and the DocOps engine is simultaneously exposed as a standalone servlet at `/docops` — both entry points share one `CliDocProcessorServlet` instance so they can never drift.

### Patch Chat (`ModifyFilesActions`)

[`ModifyFilesActions.install(cfg)`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/ModifyFilesActions.kt?L51-L53) registers:

- `POST /.fsapi/v1/modify?path=src/Foo.kt` → starts a `PatchChatManager` websocket session, returns `{"session": "...", "url": "..."}` (the AI does the work interactively)

The patch processor is exposed as a seam: `ModifyFilesActions.patchProcessor` (`get`/`set`) allows replacing the diff/patch strategy without forking the action.

---

## 7. SPA / Web UI Extension (`WebUiServlet`)

[`WebUiServlet`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/WebUiServlet.kt) serves the IDE-style SPA at `/ui/` from a classpath resource root (default `"webui"`). It is open and can be subclassed to change the resource root, add caching strategies, or rewrite paths. Mounted by `FileServerCli` at [`UI_PREFIX`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/SimpleFileServlet.kt?L268) (`/ui`).

The SPA communicates exclusively through `GET /.fsapi/v1/actions`, making it a zero-JavaScript-configuration consumer: any `FsAction` with an `ActionUi` descriptor automatically appears as a menu item, palette entry, and Alt+Enter action in the editor without any SPA modification.

---

## 8. Access Control as an Extension Point

[`FileAccessControl`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/fileserver/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FileAccessControl.kt) implements `.gitignore`-style marker-file rules. The marker files themselves (`.hidden`, `.readonly`, `.writeable_`) are a declarative, filesystem-resident extension mechanism:

| Marker | Effect |
|--------|--------|
| `.hidden` | Patterns inside are 404 for all verbs |
| `.readonly` | Patterns inside are 403 for mutations |
| `.writeable_` | Whitelist — everything not matched is read-only |

Custom subclasses can override `FileServlet.listContents()` entirely to apply alternative access policies (RBAC, session-based permissions, etc.) without touching the access control files.

---

## 9. Design Invariants for Extension Authors

The code and its documentation establish four invariants all extensions must respect:

1. **A1 — The shim is not a security boundary.** All access control stays in `FileAccessControl` + `FsPath`. Extensions that bypass these will create holes; always call `requireWritable` / `requireExisting` via `FsApiHandler`'s helpers, or check `FileAccessControl` directly.
2. **A2 — Additive and versioned.** The v1 FS API surface is stable. New operations are additions; replacements (`replace = true`) must maintain response-shape compatibility if they replace a built-in.
3. **A3 — One canonical root per mount.** `/` in FS-API space == `getFsApiRoot()`. No operation should resolve paths outside this root; use `FsPath.resolve()` which enforces lexical + canonical containment.
4. **A4 — No magic in the FS API.** The API never performs `.md -> .html/.pdf` substitution (that lives in the HTML browser layer). `stat("foo.html")` is `ENOENT` unless `foo.html` really exists.

---

## 10. Extension Surface Summary

```
FileServlet (abstract)
├── getDir(req, resp): File?            ← REQUIRED: directory strategy
├── getFileActions(file, req)           ← per-file HTML injection
├── getFolderActions(folder, req)       ← per-folder HTML injection
├── getToolbarActions(req, path)        ← navbar HTML injection
├── getAdditionalSections(dir, req, path) ← page section HTML injection
├── getAdditionalStyles()               ← CSS injection
├── getAdditionalScripts()              ← JS injection
├── isGitEnabled(req)                   ← toggle Git panel
├── getGitRoot(req, resp)               ← Git repo root override
├── getZipLink(req, filePath)           ← ZIP button href
├── listContents(file, req, resp)       ← full listing override
└── newUiRedirectUrl(req, path)         ← redirect to SPA

FilesystemServlet extends FileServlet
├── getFsApiRoot(req, resp)             ← FS API root directory
├── getFsApiConfig(req)                 ← capability configuration
└── isFsApiEnabled(req)                 ← toggle entire FS API

FsAction (DynamicEnum)                  ← register any HTTP op
├── FsAction.register(action)           ← add new operation
├── FsAction.register(action, replace=true) ← replace built-in
└── FsAction.unregister(method, op)     ← remove an operation

GitAction (DynamicEnum)                 ← register git sub-ops

CognotikPlugin (ServiceLoader)          ← JAR-packaged plugins
└── initializePlugin()                  ← register FsActions, providers, task types, auth chains
```

---

All source references above are drawn directly from the [`2.1.19` branch](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/tree/) of [`github.com/SimiaCryptus/Cognotik`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/).

