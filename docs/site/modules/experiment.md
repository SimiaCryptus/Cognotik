# Experiment

*A JVM-native sandbox for document processing, browser automation, and scripting experiments.*

## Overview

The `experiment` module is Cognotik's proving ground for capabilities that combine document parsing, image
processing, headless browser automation, and dynamic Kotlin scripting into a single JVM process. It's where
integrations with libraries like Apache PDFBox, POI, Batik, and Selenium are exercised and validated before being
promoted to core tooling — giving you a real, runnable reference for building similar automation on Cognotik.

## Key Features

- **Rich document handling** — parse and generate Office documents (Apache POI), PDFs (PDFBox, JBIG2, JAI-ImageIO),
  and vector/raster graphics (Batik SVG transcoding and codecs).
- **Barcode & QR support** — generate and decode barcodes/QR codes via ZXing.
- **Headless browser automation** — drive real browsers with Selenium and WebDriverManager for scraping, testing,
  or UI-driven workflows.
- **Embedded databases** — HSQLDB and H2 available out of the box for lightweight persistence during experiments.
- **Dynamic Kotlin scripting** — full Kotlin scripting host (`scripting-jsr223`, `scripting-jvm-host`) lets you
  evaluate and run Kotlin code at runtime, useful for plugin systems or REPL-style tooling.
- **Cloud storage integration** — AWS S3 and KMS clients ready for encrypted, cloud-backed artifact storage.
- **Embedded web server** — Jetty (server, webapp, WebSocket) for exposing local endpoints or dashboards during
  experiments.
- **Structured JSON/text processing** — Jackson, Gson, Jsoup, and Flexmark for parsing, transforming, and rendering
  structured or markup content.

## Example

Running the module's test suite, which exercises document parsing, browser automation, and scripting paths together:

```bash
./gradlew :experiment:test
```

A minimal example of using the embedded Kotlin scripting host pattern that this module wires up:

```kotlin
import javax.script.ScriptEngineManager

val engine = ScriptEngineManager().getEngineByExtension("kts")
val result = engine?.eval("1 + 2 + 3")
println(result) // 6
```

## Integration

`experiment` builds on top of Cognotik's core stack and is designed to interoperate with:

- **`core`** and **`lwcore`** — foundational runtime and lightweight utilities.
- **`docops`** — shared document-operation abstractions.
- **`stdtools`** — standard tool implementations available to agents.
- **`webui`** — for surfacing experiment results in the browser-based UI.
- **`tasklib`** — task orchestration primitives.
- **`text`** — text processing utilities used across parsing and rendering features.
- **`providers`** and **`desktop`** — available in the test scope for broader integration testing.

Because most of Cognotik's own module dependencies are declared `compileOnly`, `experiment` is meant to run inside
a full Cognotik deployment rather than as a standalone library — it's the integration layer, not an isolated
utility.