# Scala

*First-class Scala language support for Cognotik's JVM-based AI coding platform.*

## Overview

The Scala module extends Cognotik's core capabilities to Scala codebases, letting you use Cognotik's AI-powered
coding tools on projects written in Scala. It's built directly on the Scala compiler and standard library, so
Cognotik understands Scala syntax and semantics natively — no external interpreters, no bridging layers, just JVM
bytecode talking to JVM bytecode.

## Key Features

* **Native Scala compiler integration** — built on `scala-compiler` and `scala-reflect` for accurate parsing and
  analysis of Scala source.
* **Full standard library support** — leverages `scala-library` so language idioms (case classes, pattern matching,
  collections) are handled correctly.
* **Java 8 interop** — includes `scala-java8-compat` for smooth interoperability between Scala and Java-based
  components of Cognotik.
* **Zero-friction setup** — added as a standard Gradle module dependency; no separate toolchain installation
  required beyond the JVM.
* **Logging via SLF4J** — integrates with Cognotik's standard logging pipeline for consistent diagnostics across
  languages.

## Example

Add the Scala module as a dependency in your Gradle project to enable Scala-aware tooling:

```kotlin
dependencies {
    implementation(project(":Cognotik:scala"))
}
```

Once included, Cognotik's core engine gains the ability to parse, analyze, and reason about `.scala` source files
alongside your other supported languages.

## Integration

The Scala module plugs into Cognotik's `core` module and follows the same dependency conventions as other language
modules in the platform:

* Depends on `core` for shared platform services.
* Uses `slf4j-api` for logging, consistent with the rest of Cognotik.
* Tested with JUnit 5 (via the shared `junit-bom`) and `kotlin-test-junit5`, matching the testing conventions used
  across all Cognotik modules.

This makes it straightforward to combine Scala support with other Cognotik language and tooling modules in a single
build.