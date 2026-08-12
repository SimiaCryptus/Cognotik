# Kotlin Runtime

*Compile, execute, and sandbox Kotlin scripts on the fly — as a first-class scripting engine for Cognotik agents.*

## Overview

The Kotlin Runtime module gives Cognotik agents the ability to generate, compile, and execute Kotlin code dynamically at runtime, using the JetBrains Kotlin scripting stack (`kotlin-scripting-jsr223`, `kotlin-scripting-jvm-host`, and the embeddable compiler). This means agents aren't limited to static tool calls — they can write and run real JVM code to solve problems, transform data, or automate tasks, with full access to the Kotlin standard library and any project dependencies you expose to the script host.

Because it's built entirely on JVM scripting APIs, there's no external process, no Python interpreter, and no subprocess sandboxing hacks — code execution stays in-process and type-safe.

## Key Features

* **JSR-223 scripting engine** — standard `javax.script` integration for embedding Kotlin execution anywhere in the JVM.
* **Full Kotlin compiler access** — uses `kotlin-compiler-embeddable` and `kotlin-scripting-compiler-embeddable` for accurate, up-to-date compilation semantics.
* **JVM-native, zero Python dependency** — runs on the same JVM as the rest of Cognotik, avoiding cross-language IPC overhead.
* **Coroutine-aware** — built with `kotlinx-coroutines` support for async script execution.
* **Structured logging** — SLF4J-backed logging for visibility into script compilation and execution.

## Example

Embedding the Kotlin script engine via JSR-223:

```kotlin
import javax.script.ScriptEngineManager

fun main() {
    val engine = ScriptEngineManager().getEngineByExtension("kts")
    val result = engine.eval(
        """
        val nums = listOf(1, 2, 3, 4, 5)
        nums.sum()
        """.trimIndent()
    )
    println("Script result: $result") // Script result: 15
}
```

Running the module's tests locally:

```bash
./gradlew :Cognotik:kotlin:test
```

## Integration

The Kotlin Runtime module builds on core Cognotik infrastructure and is designed to be used alongside other modules:

* `core` — shared Cognotik framework types and utilities
* `lwcore` — lightweight core abstractions
* `text` — text processing utilities used in script I/O
* `docops` — document operation support for script-driven automation

Add it to your project as a standard Gradle dependency:

```kotlin
dependencies {
    implementation("com.cognotik:kotlin:<version>")
}
```