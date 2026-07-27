# Standalone file server CLI

`com.simiacryptus.cognotik.webui.servlet.FileServerCli` starts an embedded Jetty
server that exposes a local directory through `FileServlet` (browse, view, edit,
upload, delete, ZIP download and the optional Git UI).

## Build requirements

The CLI needs the Jetty server + servlet artifacts (Jetty 11.x, i.e. the
`jakarta.servlet` generation) on the runtime classpath:

```kotlin
  // fileserver/build.gradle.kts
  dependencies {
    implementation("org.eclipse.jetty:jetty-server:11.0.20")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.20")
  }

  // optional convenience task
  tasks.register<JavaExec>("fileserver") {
    mainClass.set("com.simiacryptus.cognotik.webui.servlet.FileServerCli")
    classpath = sourceSets["main"].runtimeClasspath
    args = (project.findProperty("serverArgs") as String? ?: ".").split(" ")
  }
```

> If the project is on Jetty 12, swap the imports in `FileServerCli.kt` from
> `org.eclipse.jetty.servlet.*` to `org.eclipse.jetty.ee10.servlet.*` and use
> `setBaseResourceAsString(...)` instead of `resourceBase = ...`.

## Usage

```
  java -cp <classpath> com.simiacryptus.cognotik.webui.servlet.FileServerCli [options] [directory]

    -p, --port <n>     Port to listen on (default 8081, 0 = random free port)
    -h, --host <addr>  Interface to bind (default 127.0.0.1, 0.0.0.0 for all)
        --no-git       Disable Git UI/API features
        --read-only    Disable uploads, edits and deletes
        --help         Show help
```

Examples:

```
  # serve the current directory on http://localhost:8081/
  ./gradlew :fileserver:fileserver

  # serve /tmp/work on all interfaces, port 9000, read-only
  java -cp ... FileServerCli --host 0.0.0.0 -p 9000 --read-only /tmp/work
```

The process stays in the foreground and shuts the server down cleanly on
`Ctrl-C` (SIGINT) via a JVM shutdown hook.

## URL layout

`FileServlet` treats the first path segment as a session id, so the served tree
lives under `/files/root/`. Requests to `/` (and `/files`) are redirected there
automatically. ZIP links are handled by `StaticZipServlet` at `/zip`.