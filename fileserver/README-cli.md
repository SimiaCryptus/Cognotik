# Standalone File Server CLI

`com.simiacryptus.cognotik.webui.servlet.FileServerCli` starts an embedded Jetty server that exposes a local directory through a web interface, supporting single-page app IDE (`/ui/`), directory browsing (`/files/root/`), Monaco code editing (`?edit=1`), interactive web terminal, background process execution, ZIP downloading, and Git UI/API operations.

## Quickstart & Launchers

### Shell Launcher (`curl`)

Execute the standalone fileserver IDE directly in any directory without cloning the repository:

```bash
# Launch in current directory with default settings (http://localhost:8081)
curl -sSL https://raw.githubusercontent.com/SimiaCryptus/Cognotik/refs/heads/main/cli/bin/fileserver | bash

# Pass options and workspace directory to shell launcher
curl -sSL https://raw.githubusercontent.com/SimiaCryptus/Cognotik/refs/heads/main/cli/bin/fileserver | bash -s -- --port 9090 /path/to/workspace
```

### Gradle Runner

Launch directly from the repository using Gradle:

```bash
# Serve current directory on http://localhost:8081/
./gradlew :fileserver:fileserver

# Pass custom arguments via serverArgs property
./gradlew :fileserver:fileserver -PserverArgs="--port 9000 /path/to/workspace"
```

### Direct Java Classpath

Launch via standard Java runtime:

```bash
java -cp <classpath> com.simiacryptus.cognotik.webui.servlet.FileServerCli [options] [directory]
```

## CLI Reference & Options

```
Usage: FileServerCli [options] [directory]
```

| Option | Description | Default / Behavior |
| :--- | :--- | :--- |
| `-p, --port <n>` | Port to listen on | `8081` (`0` allocates dynamic free port) |
| `-h, --host <addr>` | Network interface binding | `127.0.0.1` (`0.0.0.0` binds to all interfaces) |
| `--shell <cmd>` | Shell binary for interactive web terminals | Auto-detected host shell |
| `--no-git` | Disable Git UI panel and backend Git API operations | Sets `gitEnabled = false` |
| `--read-only` | Disable file write operations (uploads, saves, deletes) | Enforces `ReadOnlyFileServlet` (HTTP `403` on write operations) |
| `--no-terminal` | Disable interactive terminal endpoint (`/.fsapi/v1/terminal`) | Sets `terminalEnabled = false` |
| `--no-exec` | Restrict process execution (`/.fsapi/v1/exec`) to read-only Git subcommands | Sets `execPermissive = false` |
| `--secure` | Shorthand lockdown mode | Enables `--read-only --no-terminal --no-exec` |
| `--ui` | Set modern SPA IDE (`/ui/`) as default root landing page | Sets `uiDefault = true` |
| `--no-ui` | Disable SPA interface completely | Sets `uiEnabled = false` |
| `--help` | Display usage summary and exit | Prints CLI options table |

## Runtime Execution Profiles

### Permissive Local Profile (Default)

When executed without lockdown flags, the CLI defaults to local loopback binding (`127.0.0.1:8081`) with full feature access:
- Interactive web terminal sessions (`/.fsapi/v1/terminal`)
- Arbitrary command execution (`/.fsapi/v1/exec`)
- Read/write access for code editing (Monaco editor), uploads, and file management
- Git integration for commits, branches, and repository operations

This profile is designed for local loopback developer convenience.

### Secure / Hardened Profile (`--secure`)

When hosting outside local loopback or serving untrusted environments, use the `--secure` flag or individual hardening options:

```bash
# Full lockdown mode on public network binding
curl -sSL https://raw.githubusercontent.com/SimiaCryptus/Cognotik/refs/heads/main/cli/bin/fileserver | bash -s -- --host 0.0.0.0 --secure /path/to/workspace
```

The secure profile enforces:
- **Read-only file access** (`--read-only`): Rejects all file modifications, saves, uploads, and deletions with HTTP `403 Forbidden`.
- **Terminal disabled** (`--no-terminal`): Disables interactive terminal endpoints.
- **Exec restricted** (`--no-exec`): Blocks execution of arbitrary process commands, allowing only safe, read-only Git subcommands.

## URL Layout & Architecture

`FileServerCli` configures an embedded Jetty server hosting servlets under context path `/`:

```
/ (Root Landing Redirect -> /ui/ or /files/root/)
├── /ui/*     -> WebUiServlet (Single Page App IDE)
├── /files/*  -> SimpleFileServlet / ReadOnlyFileServlet (Files, Monaco Editor ?edit=1, REST API)
├── /zip      -> StaticZipServlet (Directory/File ZIP downloads)
├── /lib/*    -> WebUiServlet("web/lib") (Classpath static libraries)
└── /app/*    -> WebUiServlet("web/app") (Classpath application assets)
```

`FileServlet` treats the first path segment as a session id, so the served workspace tree lives under `/files/root/`. Requests to `/` (and `/files`) are redirected automatically based on `--ui` configuration.

## Build Requirements

The CLI requires Jetty server and servlet artifacts on the runtime classpath:

```kotlin
// fileserver/build.gradle.kts
dependencies {
    implementation("org.eclipse.jetty:jetty-server:11.0.20")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.20")
}

// Convenience JavaExec task
tasks.register<JavaExec>("fileserver") {
    mainClass.set("com.simiacryptus.cognotik.webui.servlet.FileServerCli")
    classpath = sourceSets["main"].runtimeClasspath
    args = (project.findProperty("serverArgs") as String? ?: ".").split(" ")
}
```

> **Note on Jetty Versioning**: If updating to Jetty 12+, update imports in `FileServerCli.kt` from `org.eclipse.jetty.servlet.*` to `org.eclipse.jetty.ee10.servlet.*` and use `setBaseResourceAsString(...)` instead of `resourceBase = ...`.