# Standard Tools

*A batteries-included toolkit of production-ready agent capabilities — document processing, browser automation, code generation, and more.*

## Overview

`stdtools` is Cognotik's standard library of agent-callable tools. Instead of wiring up PDF parsers, spreadsheet
readers, headless browsers, and API clients yourself, `stdtools` provides them pre-integrated and ready for your
agents to invoke. It sits alongside `core`, `webui`, and `tasklib` to give Cognotik agents real-world reach: reading
documents, generating QR codes, scraping the web, and calling out to cloud APIs — all from JVM-native code with no
Python runtime required.

## Key Features

- **Document processing** — read and manipulate PDFs (Apache PDFBox + JBIG2/JPEG2000 support) and Office files
  (Apache POI for Word/Excel/PowerPoint).
- **Web & HTML tooling** — parse and query HTML with Jsoup, drive real browsers via Selenium with automatic
  driver management (WebDriverManager).
- **Barcode/QR generation and decoding** via ZXing.
- **Rich text & Markdown rendering** with Flexmark, including GitHub-flavored tables.
- **Terminal & process control** via Pty4j for interactive shell sessions.
- **Cloud integration** — built-in Google API client support (OAuth2) and AWS SDK compatibility.
- **Embedded servers** — Jetty-based HTTP and WebSocket server support for tools that need to expose local
  endpoints.
- **Graph data structures** via Apache TinkerPop, with an embedded HSQLDB for lightweight persistence.
- **Code generation support** — OpenAPI Generator integration for producing client/server stubs from specs.
- **JVM-native scripting** — optional Kotlin scripting engine support for dynamically evaluated tool logic.

## Example

Add `stdtools` to your project to give an agent access to PDF reading and QR generation out of the box:

```kotlin
dependencies {
    implementation(project(":Cognotik:stdtools"))
}
```

Once included, an agent can invoke tools such as PDF text extraction or QR code generation without any additional
setup — the underlying libraries (PDFBox, ZXing, POI, Selenium, etc.) are already wired in and configured to work
together.

## Integration

`stdtools` builds on top of core Cognotik infrastructure and is typically used alongside:

- **`core`** — the agent execution engine that invokes these tools.
- **`webui`** — for tools that need to surface UI or capture user input.
- **`tasklib`** — for composing tools into multi-step task workflows.
- **`fileserver`**, **`lwcore`**, **`docops`** — supporting file access and document operation primitives.
- **`kotlin` / `groovy`** — optional scripting language support for dynamic tool behavior (compile-time optional).

Because dependencies like the Google API client, AWS SDK, and Kotlin scripting libraries are marked `compileOnly`
where appropriate, consuming projects can opt in only to the pieces they need without bloating their runtime
footprint.