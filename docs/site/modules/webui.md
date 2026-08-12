# Webui

  *Build interactive, AI-powered web applications with real-time chat, task orchestration, and rich diff tooling — in pure JVM.*

  ## Overview

  Webui is the interactive front-end runtime for Cognotik. It provides everything you need to expose an AI-assisted
  development workflow as a live, browser-based application: a real-time chat/session layer, a servlet-based
  application framework, and a set of interactive UI components (links, text inputs, task panels, file uploads) that
  render directly from server-side Kotlin/Java code. It ships with a pre-built React/TypeScript webapp (`webapp-v2`)
  that Gradle compiles and bundles automatically, so you get a polished UI without owning a separate frontend build
  pipeline.

  On top of the UI layer, Webui bundles the Cognotik **planning** and **diff** engines directly into the same
  artifact, so AI-generated code changes can be previewed, validated, and applied through the browser with full
  session state and dependency-aware task execution.

  ## Key Features

  - **Real-time Chat System** — bidirectional client/server communication for streaming AI responses and task updates.
  - **Session Management** — persistent user sessions, state storage, and task lifecycle handling.
  - **Application Framework** — a servlet-based structure (Jetty) for building and serving full web applications.
  - **Interactive UI Components** — server-driven links, text inputs, task widgets, and file handling with no
    hand-written JavaScript required.
  - **AI Task Planning built-in** — pulls in Cognotik's planning module for dependency-aware task decomposition and
    execution, visualized live in the browser.
  - **Diff & Patch Tooling** — generate, preview, and apply code diffs interactively, with validation to ensure
    patches produce compilable results.
  - **Integrated Frontend Build** — Gradle tasks (`pnpmInstall`, `buildWebapp`, `copyWebappBuild`) automatically build
    and package the `webapp-v2` React app into the JVM artifact's resources; a `-PskipWebapp` flag lets you build the
    backend alone when a pre-built frontend is already present.
  - **Rich Document & Media Support** — PDF parsing (PDFBox), image codecs (JAI ImageIO), Office documents (Apache
    POI), QR codes (ZXing), and Markdown rendering (Flexmark) out of the box.
  - **Pluggable Storage** — embedded H2 database and Exposed DSL support for lightweight session/state persistence.

  ## Example

  Start a Jetty-backed Cognotik web application (conceptual usage once wired into your app class):

  ```kotlin
  import com.simiacryptus.cognotik.webui.application.ApplicationServer

  fun main() {
    ApplicationServer(
      applicationName = "My AI Assistant",
      path = "/assistant"
    ).run(port = 8080)
  }
  ```

  Build the module, including the bundled web UI:

  ```bash
  ./gradlew :webui:build
  ```

  Build just the JVM backend and reuse an existing pre-built frontend:

  ```bash
  ./gradlew :webui:build -PskipWebapp
  ```

  ## Integration

  Webui sits at the top of the Cognotik dependency stack, composing several other modules into a single deployable
  artifact:

  - **core / lwcore** — shared runtime primitives.
  - **text** — text processing utilities used across chat and task rendering.
  - **fileserver** — file handling and static asset serving.
  - **docops** — diff, patch, and document manipulation used by the interactive diff UI.
  - **groovy / kotlin** (optional, compile-time) — scripting support for dynamic task definitions.

  Because Webui packages the planning and diff engines directly, most applications only need to depend on `webui` to
  get a complete AI-assisted development UI — no need to wire up the lower-level modules individually.