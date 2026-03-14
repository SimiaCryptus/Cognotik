package com.simiacryptus.cognotik.ui.patch

/**
 * The "Fake" Pattern for LLM Responses.
 * Provides raw string constants of typical LLM outputs to avoid relying on actual LLMs during testing.
 */
object TestFixtures {
    val STANDARD_DIFF_RESPONSE = """
        Here is the fix for your issue:
        # File: src/main/Main.kt
        ```diff
        - val x = 1
        + val x = 2
        ```
    """.trimIndent()

    val MALFORMED_RESPONSE = """
        I will create the file.
        ```kotlin
        fun main() { println("Hello") }
        // Oops, forgot to close the block
    """.trimIndent()

    val NEW_FILE_RESPONSE = """
        # File: src/main/NewFile.kt
        ```kotlin
        class NewFile {
            val isNew = true
        }
        ```
    """.trimIndent()
    val EMBEDDED_FENCE_WITH_LANG = """
        # File: src/main/Readme.md
        ```markdown
        # Title
        ```javascript
        console.log("hello");
        ```
        ```
    """.trimIndent()
    val EMBEDDED_FENCE_INDENTED = """
        # File: src/main/Readme.md
        ```markdown
        # Title
            ```
            console.log("hello");
            ```
        ```
    """.trimIndent()
}