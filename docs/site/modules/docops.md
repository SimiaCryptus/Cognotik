# DocOps

*Document generation, parsing, and transformation for JVM applications — YAML, XML, TOML, and more, all through one unified Jackson-powered pipeline.*

## Overview

DocOps is Cognotik's core library for structured document handling. It provides a consistent, type-safe way to
parse, generate, and transform documents across a wide range of formats — from configuration files to structured
data interchange formats — using Kotlin and Jackson under the hood. Whether you're building configuration tooling,
data pipelines, or content-generation features, DocOps gives you a single dependency surface instead of juggling
multiple format-specific libraries.

## Key Features

* **Multi-format support** — read and write YAML, XML, TOML, Java Properties, and JSON with the same underlying
  Jackson-based approach.
* **Kotlin-first ergonomics** — built with `jackson-module-kotlin` for idiomatic data classes, nullable types, and
  coroutine-friendly APIs.
* **Rich type coverage** — native support for Java 8 date/time (`jsr310`), `Optional`/`jdk8` types, and nullable
  Bean Validation-style properties out of the box.
* **ANTLR-powered parsing** — integrates with Cognotik's `antlr` module for grammar-based text parsing when
  structured formats aren't enough.
* **Embedded database support** — ships with HSQLDB for lightweight, embedded persistence scenarios (caching,
  local indexing, test fixtures).
* **Battle-tested foundations** — built on Guava and Apache Commons (`commons-text`, `commons-io`) for reliable
  string and I/O utilities.

## Example

Reading a YAML configuration file and mapping it into a Kotlin data class:

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

data class AppConfig(
  val name: String,
  val enabled: Boolean,
  val retries: Int = 3
)

val mapper = ObjectMapper(YAMLFactory()).apply {
  registerKotlinModule()
}

val config: AppConfig = mapper.readValue(File("app-config.yaml"))
println("Loaded config: $config")
```

The same `ObjectMapper` pattern extends directly to XML, TOML, or Properties by swapping the factory —
no additional parsing logic required.

## Integration

DocOps builds on several Cognotik foundation modules and is designed to slot cleanly into larger projects:

* **`antlr`** — for grammar-driven text and language parsing.
* **`text`** — shared text-processing utilities used across Cognotik modules.
* **`lwcore`** — lightweight core abstractions shared across the platform.
* **Jackson ecosystem** — `jackson-databind`, `jackson-kotlin`, and dataformat modules for YAML, XML, TOML, and
  Properties.

Add it to your Gradle build like any other Cognotik module:

```kotlin
dependencies {
  implementation(project(":Cognotik:docops"))
}
```

DocOps is JVM-native with no external process or Python dependency — everything runs in-process, making it a
natural fit for applications that need reliable, embeddable document handling without extra runtime overhead.