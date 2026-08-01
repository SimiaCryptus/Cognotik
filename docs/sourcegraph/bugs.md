Now I have a thorough picture of the new code. Here is a summary of the bugs I found in the `2.1.19` branch:

---

## Bugs Found in `2.1.19`

### 1. `DocOpsCli` duplicates `CliSupport` logic — and uses a different `defaultUser()` that ignores `CliSupport.email`

**File:** [`cli/src/main/kotlin/com/simiacryptus/cognotik/cli/DocOpsCli.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/DocOpsCli.kt?L250-L272)

`DocOpsCli` has its own private `defaultUser()` and `bootstrapPlatform()` that do **not** use `CliSupport.email`:

```kotlin
private fun defaultUser(): User = User(
    id = "1",
    email = System.getenv("EMAIL") ?: System.getProperty("user.email") ?: "user@localhost"
)
```

`CliSupport.email` is a `var` mutated by `--email` flag parsing (in both `DocOpsCli.parse()` at line 541 and `AutoFixCli.parse()` at line 311). However, `DocOpsCli.execute()` calls its own private `defaultUser()` instead of `CliSupport.defaultUser()`, so `--email` is **stored in `CliSupport.email` but never used** when `DocOpsCli` creates its `User`. The user object gets the wrong email.

The fix: replace the private `defaultUser()` / `bootstrapPlatform()` / `availableModels()` / `resolveModels()` in `DocOpsCli` with calls to `CliSupport.*`. These are already fully duplicated there.

---

### 2. `applyTargetFilter` uses `File.endsWith` (path suffix) instead of `File.canonicalPath` equality

**File:** [`cli/src/main/kotlin/com/simiacryptus/cognotik/cli/DocOpsCli.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/DocOpsCli.kt?L379-L386)

```kotlin
planned.task.data.main_file?.canonicalFile?.endsWith(targetFile) == true
```

`File.endsWith(File)` checks whether the **abstract path** ends with the given path — it is a path-component suffix match, not a canonical equality check. For example, `File("/a/b/c").endsWith(File("b/c"))` is `true`. This means `--target foo.md` would also match `bar/foo.md`, `baz/foo.md`, etc., running more tasks than intended. The fix is `== targetFile` (canonical equality).

---

### 3. `EphemeralMonitorServer.close()` casts `jetty` to `Server` but `start()` assigns `server.start()` return value (not `server` itself)

**File:** [`cli/src/main/kotlin/com/simiacryptus/cognotik/cli/EphemeralMonitorServer.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/EphemeralMonitorServer.kt?L41-L62)

```kotlin
fun start(): String {
    if (jetty == null) {
        val server = CognotikAppServer(localName = host, port = port)
        jetty = server.start()   // <-- assigned the return value of start(), not `server`
        appServer = server
    }
    return baseUrl
}

override fun close() {
    val current = jetty
    ...
    if (current is Server) {    // <-- may never be true if start() returns something else
        current.stop()
    }
}
```

`CognotikAppServer.start()` likely returns a `org.eclipse.jetty.server.Server` directly (Jetty's `start()` returns `this`), so in practice this may work. But `jetty` is typed `Any?` and the cast guard `is Server` is fragile — if `CognotikAppServer.start()` ever returns something other than a `Server` (e.g. wraps it), `close()` silently does nothing and the server leaks. The fix is to store `server` in `jetty`, not `server.start()`.

---

### 4. `FileServerCli.start()` registers `docProcessorServlet` **after** `start()` is called, so it is always `null` inside `start()`

**File:** [`cli/src/main/kotlin/com/simiacryptus/cognotik/cli/FileServerCli.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/FileServerCli.kt?L278-L330)

In `main()`:
1. `ServerTaskActions.install(...)` is called at line 279 — this creates `CliDocProcessorServlet` which sets `FileServerCli.docProcessorServlet` (line 81 of `ServerTaskActions.kt`).
2. `start(...)` is called at line 326 — inside `start()` at line 456, `docProcessorServlet?.let { ... }` mounts it.

This ordering works **only** if `ServerTaskActions.install()` is guaranteed to complete synchronously before `start()` is called. Currently it does, so this is not a crash, but the `start()` function takes `docProcessorServlet` from the `object` field (side-effectful shared mutable state), which breaks the `start()` API contract when called programmatically without going through `main()` first (e.g. in tests or embedding). The `start()` KDoc says "the caller owns stopping the returned Server" which implies it is self-contained, but it depends on global mutable state.

---

### 5. `--smart-model` / `--fast-model` in `FileServerCli` silently ignore the value on a missing argument

**File:** [`cli/src/main/kotlin/com/simiacryptus/cognotik/cli/FileServerCli.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/FileServerCli.kt?L215-L216)

```kotlin
"--smart-model" -> smartModel = args.getOrNull(++i) ?: smartModel ?: fail("Missing value for $arg")
"--fast-model"  -> fastModel  = args.getOrNull(++i) ?: fastModel  ?: fail("Missing value for $arg")
```

If the user passes `--smart-model` as the last argument (no following value), `args.getOrNull(++i)` returns `null`. Then `?: smartModel` falls through to the existing (possibly `null`) value from `COGNOTIK_SMART_MODEL`. If the env var is also unset, `fail()` is called. But if the env var **is** set, the missing argument is **silently ignored** — no error is reported even though the user clearly made a mistake. The other CLIs correctly throw `IllegalArgumentException("missing value for $name")` in this case.

---

### 6. `cognotikModify` JavaScript function is referenced in `getToolbarActions` but not defined in `getAdditionalScripts`

**File:** [`cli/src/main/kotlin/com/simiacryptus/cognotik/cli/SimpleFileServlet.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik@48dbede/-/blob/cli/src/main/kotlin/com/simiacryptus/cognotik/cli/SimpleFileServlet.kt?L68-L70)

```kotlin
val modify = if (!modifyEnabled || readOnly) "" else
    """<a ... onclick="return cognotikModify(event,null)">✏️ Modify files…</a>"""
```

`cognotikModify` is **never defined** in `getAdditionalScripts()` (lines 126-256). The scripts block defines `cognotikDocOps`, `cognotikAutoFix`, `cognotikTasks`, `cognotikModels` — but not `cognotikModify`. Clicking "Modify files…" in the toolbar will throw a `ReferenceError` in the browser. The same function name is also used in `getFileActions` at line 93. This is a missing implementation.