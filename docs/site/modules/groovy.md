# Groovy

*Script your AI workflows with dynamic, JVM-native Groovy support.*

## Overview

The Groovy module brings full Apache Groovy language support to Cognotik, letting you write dynamic scripts,
tools, and agent extensions without leaving the JVM ecosystem. Because Groovy compiles to JVM bytecode and
interoperates seamlessly with Java and Kotlin, you get scripting flexibility with none of the overhead of managing
a separate Python runtime or interpreter process.

This module is a thin, focused integration layer — it wires Groovy's runtime into Cognotik's core, text, and
document-processing pipelines so scripts can call directly into the rest of the platform.

## Key Features

- **JVM-native scripting** — no external interpreter, no Python dependency, no subprocess management.
- **Full Groovy language support** via `groovy-all`, including closures, dynamic typing, and metaprogramming.
- **Direct access to Cognotik internals** — scripts can call into `core`, `lwcore`, `text`, and `docops` modules
  without serialization overhead.
- **Kotlin coroutine interop** — designed to coexist with Cognotik's Kotlin-based async infrastructure.
- **Standard build artifacts** — ships with sources and Javadoc jars, published to Maven Central under
  `com.cognotik:groovy`.

## Example

Add the dependency to your Gradle build:

```kotlin
dependencies {
    implementation("com.cognotik:groovy:<version>")
}
```

Then execute a Groovy script against Cognotik's runtime:

```groovy
def result = cognotik.tools.invoke("summarize") {
    input = "Long document text goes here..."
    maxLength = 200
}
println result
```

Because Groovy classes are ordinary JVM classes, this script can be compiled ahead of time, run dynamically at
startup, or hot-reloaded during development — whichever fits your workflow.

## Integration

The Groovy module depends on and extends:

- **core** — Cognotik's foundational APIs and services.
- **lwcore** — lightweight core utilities for minimal-footprint deployments.
- **text** — text processing and NLP utilities available to scripts.
- **docops** — document manipulation operations exposed to Groovy scripts.

It's a natural fit if your team already scripts build logic in Groovy (e.g., via Gradle) and wants that same
flexibility inside Cognotik's AI pipelines, rather than introducing a second scripting language or runtime.