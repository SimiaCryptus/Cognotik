Now I have a complete picture. Here is the guide:

---

# Developer Guide: Extending the Cognotik File Server (branch `2.1.19`)

The fileserver module lives at [
`fileserver/`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet)
and offers three orthogonal extension surfaces:

1. **Custom FS API actions** (`FsAction` registry)
2. **Custom Git actions** (`GitAction` registry)
3. **HTML UI extension hooks** (overridable methods on `FileServlet`)

---

## Architecture Overview

The class hierarchy:

```
HttpServlet
  └── FileServlet          (HTML browser, upload, delete, editor — overridable hooks)
        └── FilesystemServlet  (adds FS API v1 routing — override getFsApiConfig/getFsApiRoot)
              └── SimpleFilesystemServlet  (concrete ready-to-use impl)
```

All FS API traffic arrives at [
`FilesystemServlet.service()`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FilesystemServlet.kt?L61-91).
It intercepts any URL containing the reserved `.fsapi` segment, parses the route with [
`FsApiRoute`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiRoute.kt),
then hands off to [
`FsApiHandler.handle()`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiHandler.kt?L193-228).
Non-API requests fall through to `FileServlet` for the HTML browser experience.

---

## 1. Registering Custom FS API Actions

### The `FsAction` Dynamic Registry

[
`FsAction`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/action/FsAction.kt)
extends `DynamicEnum<FsAction>`. The registry is keyed by `"<METHOD> <op>"`. Every operation that `FsApiHandler`
dispatches is a registered `FsAction` — including all the built-in ones — so the set is fully open.

**Registering a new action:**

```kotlin
FsAction.register(
  FsAction(
    op = "hash",
    method = "GET",
    description = "SHA-256 hash of a file",
    parameters = listOf(
      ActionParam("path", required = true, description = "virtual path"),
      ActionParam("algorithm", default = "SHA-256")
    ),
    requiresCapability = null,   // optional: gate behind a capability flag
    mutating = false             // inferred from method if omitted (GET/HEAD = read-only)
  ) { ctx ->
    val path = ctx.req.getParameter("path") ?: "/"
    // resolve, hash, respond...
    ctx.resp.contentType = "application/json"
    ctx.resp.writer.write("""{"path":"$path","hash":"..."}""")
  }
)
```

To **replace** a built-in action:

```kotlin
FsAction.register(myAction, replace = true)
```

To **unregister** one:

```kotlin
FsAction.unregister("GET", "file")
```

### `FsActionContext`

Every handler receives an [
`FsActionContext`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/action/FsAction.kt?L29-37)
containing:

| Field    | Type                  | Description                              |
|----------|-----------------------|------------------------------------------|
| `method` | `String`              | Normalised HTTP method                   |
| `op`     | `String`              | Op name from the URL                     |
| `req`    | `HttpServletRequest`  | Raw request                              |
| `resp`   | `HttpServletResponse` | Raw response                             |
| `root`   | `File`                | Validated filesystem root for this mount |
| `config` | `FsApiConfig`         | Capability/limit configuration           |

### Security: CSRF Mitigation

Actions whose `mutating` flag is `true` (which defaults to any non-GET/HEAD/OPTIONS method) will be rejected by [
`FsApiHandler`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiHandler.kt?L211-213)
unless the request carries the `X-Fs-Api` header, when `FsApiConfig.requireApiHeader` is true. Custom actions
automatically participate in this check.

### Self-Documentation

Registered actions are automatically surfaced by `GET /.fsapi/v1/actions`. The [
`describeActions()`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiHandler.kt?L273-278)
method calls `FsAction.values()` at call time — the live registry, not a compiled list. Your custom action's name,
method, parameters, and description will appear there automatically.

---

## 2. Registering Custom Git Actions

### The `GitAction` Dynamic Registry

[
`GitAction`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/action/GitAction.kt)
uses the same `DynamicEnum` pattern. Built-in actions (status, add, commit, log, diff, reset, branches, create-branch,
etc.) are registered in [
`GitActions.init {}`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/GitActions.kt?L21-108).

**Registering a new git action:**

```kotlin
GitAction.register(
  GitAction(
    name = "fetch",
    description = "git fetch --all",
    parameters = listOf(
      ActionParam("remote", default = "origin", description = "remote name")
    ),
    mutating = true
  ) { ctx ->
    val remote = ctx.param("remote", "origin")
    mapOf(
      "message" to "Fetched from $remote",
      "output" to ctx.git("git", "fetch", remote)
    )
  }
)
```

### `GitActionContext`

[
`GitActionContext`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/action/GitAction.kt?L12-24)
provides:

- `ctx.param("name")` — optional parameter
- `ctx.param("name", "default")` — with fallback
- `ctx.required("name")` — throws `IllegalArgumentException` if absent (maps to HTTP 400)
- `ctx.flag("name", default)` — boolean parameter
- `ctx.git("git", "subcommand", ...)` — runs the command in the served root via [
  `GitOperationHandler.executeGitCommand`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/GitOperationHandler.kt)

### Enabling Git / the Exec Allowlist

Git actions are routed through `POST /.fsapi/v1/git` which requires the `git` capability to be enabled. This is
controlled by [
`FsApiConfig.execAllowlist`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiConfig.kt?L12):
if `"git"` is a key, the capability is on. Override `getFsApiConfig()` in your servlet to enable it:

```kotlin
override fun getFsApiConfig(req: HttpServletRequest) = FsApiConfig(
  execAllowlist = mapOf("git" to FilesystemServlet.GIT_SUBCOMMANDS)
)
```

`GIT_SUBCOMMANDS` is
the [built-in safe set](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FilesystemServlet.kt?L97-101)
of git sub-commands. The `POST /.fsapi/v1/exec` endpoint uses this allowlist directly (command + first argument must
both be allowlisted), while the `POST /.fsapi/v1/git` endpoint only checks for the `"git"` key.

---

## 3. Extending the HTML Browser UI

[
`FileServlet`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FileServlet.kt)
provides several open hooks for injecting HTML into the directory listing page. All return empty strings by default.

| Method                                                 | Injected into    | Purpose                                               |
|--------------------------------------------------------|------------------|-------------------------------------------------------|
| `getFileActions(file, req): String`                    | Each file row    | Extra link/button HTML appended after the file link   |
| `getFolderActions(folder, req): String`                | Each folder row  | Extra link/button HTML appended after the folder link |
| `getToolbarActions(req, currentPath): String`          | Navbar (top bar) | HTML placed alongside the ZIP download link           |
| `getAdditionalSections(dir, req, currentPath): String` | Page body        | Full HTML section inserted after files/folders lists  |
| `getAdditionalStyles(): String`                        | `<style>` block  | Raw CSS appended to page styles                       |
| `getAdditionalScripts(): String`                       | `<script>` block | Raw JavaScript appended to page scripts               |

These are called from [
`serveDirectoryListing()`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FileServlet.kt?L345-379)
which assembles the [
`DirectoryPageModel`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/render/DirectoryListingRenderer.kt?L3-14)
and passes it to `DirectoryListingRenderer`.

**Example — add a "Lint" button per file:**

```kotlin
class MyServlet(baseDir: File) : SimpleFilesystemServlet(baseDir) {

  override fun getFileActions(file: File, req: HttpServletRequest): String {
    if (!file.name.endsWith(".kt")) return ""
    val encoded = URLEncoder.encode(file.name, "UTF-8")
    return """<a class="action-link" href="/lint?file=$encoded">🔍 Lint</a>"""
  }

  override fun getToolbarActions(req: HttpServletRequest, currentPath: String): String =
    """<button onclick="alert('Hello from toolbar!')">Custom Action</button>"""

  override fun getAdditionalStyles(): String = """
        .action-link { color: #6f42c1; }
    """
}
```

### Other Overridable Servlet Behaviours

| Method                              | Purpose                                                        |
|-------------------------------------|----------------------------------------------------------------|
| `getDir(req, resp): File?`          | **Required** — the filesystem root to serve                    |
| `getFsApiRoot(req, resp): File?`    | FS API root; defaults to `getDir()`                            |
| `getFsApiConfig(req): FsApiConfig`  | Capability/limit advertisement; defaults to git-enabled config |
| `isFsApiEnabled(req): Boolean`      | Toggle the whole FS API on/off per request                     |
| `isGitEnabled(req): Boolean`        | Toggle the Git UI panel                                        |
| `getGitRoot(req, resp): File?`      | Git repository root; defaults to `getDir()`                    |
| `getZipLink(req, filePath): String` | URL for the ZIP download button                                |

---

## 4. Access Control via Marker Files

[
`FileAccessControl`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FileAccessControl.kt)
enforces access control through gitignore-style files placed anywhere in the tree. These apply uniformly to both the
HTML browser and the FS API.

| File         | Behaviour                                                    |
|--------------|--------------------------------------------------------------|
| `.hidden`    | Matched paths appear non-existent (ENOENT) to all operations |
| `.readonly`  | Matched paths reject all mutations (EACCES)                  |
| `.writeable` | Acts as a **whitelist**: anything _not_ matched is read-only |

Patterns follow `.gitignore` syntax and match relative to the directory containing the marker file. The marker files
themselves are always hidden/read-only so they cannot be tampered with through the API.

---

## 5. Configuring Capabilities via `FsApiConfig`

[
`FsApiConfig`](../src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/handler/FsApiConfig.kt)
is a plain data class. Key fields:

| Field              | Default | Effect                                              |
|--------------------|---------|-----------------------------------------------------|
| `readOnly`         | `false` | All mutations return EROFS                          |
| `execAllowlist`    | `{}`    | `cmd -> allowed-subcommands`; empty = exec disabled |
| `watchMode`        | `"sse"` | `"sse"`, `"poll"`, or `"none"`                      |
| `snapshotEnabled`  | `true`  | Enables `GET /.fsapi/v1/snapshot`                   |
| `resolveEnabled`   | `true`  | Enables `GET /.fsapi/v1/resolve`                    |
| `utimesEnabled`    | `true`  | Enables `POST /.fsapi/v1/utimes`                    |
| `maxFileSize`      | `50 MB` | Reads/writes above this size are rejected           |
| `maxBatchOps`      | `256`   | Max operations in one `/batch` call                 |
| `requireApiHeader` | `true`  | Mutating requests must carry `X-Fs-Api` header      |

The capabilities object from `GET /.fsapi/v1/meta` is derived directly from this config, so clients self-configure from
it.

---

## 6. Quick-Start: Minimal Custom Servlet

```kotlin
class ProjectServlet(root: File) : FilesystemServlet() {

  // Register a custom FS action once at startup
  init {
    GitActions.install()  // force built-in git actions to register
    FsAction.register(
      FsAction(
        "hash", "GET", "SHA-256 of a file",
        listOf(ActionParam("path", required = true))
      ) { ctx ->
        val path = ctx.req.getParameter("path") ?: throw IllegalArgumentException("missing path")
        val file = File(ctx.root, path.trimStart('/'))
        val digest = java.security.MessageDigest.getInstance("SHA-256")
          .digest(file.readBytes())
        val hex = digest.joinToString("") { "%02x".format(it) }
        ctx.resp.contentType = "application/json"
        ctx.resp.writer.write("""{"path":"$path","sha256":"$hex"}""")
      })
  }

  override fun getDir(req: HttpServletRequest, resp: HttpServletResponse) = root

  override fun getFsApiConfig(req: HttpServletRequest) = FsApiConfig(
    readOnly = false,
    execAllowlist = mapOf("git" to FilesystemServlet.GIT_SUBCOMMANDS),
    maxFileSize = 100L * 1024 * 1024
  )

  // Inject a toolbar button in the HTML browser
  override fun getToolbarActions(req: HttpServletRequest, currentPath: String) =
    """<a class="zip-link" href="/reports/$currentPath">📊 Reports</a>"""

  // Add extra buttons to each file entry
  override fun getFileActions(file: File, req: HttpServletRequest): String =
    if (file.name.endsWith(".md"))
      """<a class="action-link" href="${file.name}.pdf">PDF</a>"""
    else ""
}
```

The new `hash` action will appear immediately in `GET /.fsapi/v1/actions` without any restart or recompilation of the
core.
