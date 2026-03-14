package com.simiacryptus.cognotik.ui.patch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class NormalizeFilenameTest {

    @ParameterizedTest(name = "Input: ''{0}'' -> Expected: ''{1}''")
    @CsvSource(
        "MyClass.kt, MyClass.kt",
        "**MyClass.kt**, MyClass.kt",
        "`MyClass.kt`, MyClass.kt",
        "File: MyClass.kt, MyClass.kt",
        "modified: src/main.kt, src/main.kt",
        "1. MyClass.kt, MyClass.kt",
        "'MyClass.kt', MyClass.kt",
        "\"MyClass.kt\", MyClass.kt",
        "path: /a/b/c.kt, /a/b/c.kt"
    )
    fun `should normalize valid filenames`(input: String, expected: String) {
        assertEquals(expected, normalizeFilename(input))
    }

    @ParameterizedTest(name = "Language tag or blank: ''{0}''")
    @ValueSource(strings = ["java", "kt", "python", "cpp", "bash", ""])
    fun `should strip language identifiers and handle blanks`(input: String) {
        assertEquals("", normalizeFilename(input))
    }

    @ParameterizedTest(name = "Whitespace: ''{0}''")
    @ValueSource(strings = ["   ", "\t", "\n", " \n \t "])
    fun `should handle whitespace`(input: String) {
        assertEquals("", normalizeFilename(input))
    }

    @Test
    fun `should safeguard against infinite loops with maxIterations`() {
        // A string that might theoretically loop if not for maxIterations
        val maliciousString = "File: File: File: File: File: File: File: File: File: File: File: File: test.kt"
        val result = normalizeFilename(maliciousString, maxIterations = 5)
        // It should strip 5 "File:" prefixes and leave the rest
        assertEquals("File: File: File: File: File: File: File: test.kt", result)
    }
}