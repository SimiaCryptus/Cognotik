package com.simiacryptus.cognotik.diff

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PatchParserTest {
  private lateinit var parser: PatchParser

  @BeforeEach
  fun setUp() {
    parser = object : PatchParser {}
  }

  @Nested
  inner class BasicParsing {
    @Test
    fun `parse blank response returns empty list`() {
      val result = parser.parse("")
      assertTrue(result.isEmpty())
    }

    @Test
    fun `parse whitespace-only response returns empty list`() {
      val result = parser.parse("   \n  \n  ")
      assertTrue(result.isEmpty())
    }

    @Test
    fun `parse plain text returns single markdown segment`() {
      val response = "This is just some plain text without any code blocks."
      val result = parser.parse(response)
      assertEquals(1, result.size)
      assertTrue(result[0] is PatchParser.ResponseSegment.Markdown)
      assertEquals(response, (result[0] as PatchParser.ResponseSegment.Markdown).content)
    }

    @Test
    fun `parse response with no filename treats code block as markdown`() {
      val response = """
Some text
```kotlin
fun hello() = println("Hello")
```
""".trimIndent()
      val result = parser.parse(response)
// Without a header or default file, code block should be treated as markdown
      assertTrue(result.any { it is PatchParser.ResponseSegment.Markdown })
    }
  }

  @Nested
  inner class DiffBlockParsing {
    @Test
    fun `parse diff block with markdown header`() {
      val response = """
### src/main/Example.kt
```diff
fun hello() {
-    println("Hello")
+    println("Hello World")
}
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/Example.kt", diffBlocks[0].filename)
      assertTrue(diffBlocks[0].diff.contains("-    println(\"Hello\")"))
      assertTrue(diffBlocks[0].diff.contains("+    println(\"Hello World\")"))
    }

    @Test
    fun `parse multiple diff blocks`() {
      val response = """
### src/main/Foo.kt
```diff
class Foo {
-    val x = 1
+    val x = 2
}
```
### src/main/Bar.kt
```diff
class Bar {
-    val y = 3
+    val y = 4
}
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(2, diffBlocks.size)
      assertEquals("src/main/Foo.kt", diffBlocks[0].filename)
      assertEquals("src/main/Bar.kt", diffBlocks[1].filename)
    }

    @Test
    fun `parse diff block with default file`() {
      val response = """
```diff
fun hello() {
-    return 1
+    return 2
}
```
""".trimIndent()
      val result = parser.parse(response, defaultFile = "src/Default.kt")
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/Default.kt", diffBlocks[0].filename)
    }

    @Test
    fun `isDiffContent detected by line prefixes`() {
      val response = """
### src/main/Example.kt
```kotlin
val a = 1
-val b = 2
+val b = 3
-val c = 4
+val c = 5
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/Example.kt", diffBlocks[0].filename)
    }
  }

  @Nested
  inner class NewFileBlockParsing {
    @Test
    fun `parse new file block with markdown header`() {
      val response = """
### src/main/NewFile.kt
```kotlin
package com.example
fun newFunction() {
println("New file")
}
```
""".trimIndent()
      val result = parser.parse(response)
      val newFileBlocks = result.filterIsInstance<PatchParser.ResponseSegment.NewFileBlock>()
      assertEquals(1, newFileBlocks.size)
      assertEquals("src/main/NewFile.kt", newFileBlocks[0].filename)
      assertEquals("kotlin", newFileBlocks[0].language)
      assertTrue(newFileBlocks[0].code.contains("fun newFunction()"))
    }

    @Test
    fun `parse new file block with default file`() {
      val response = """
```javascript
function hello() {
return "world";
}
```
""".trimIndent()
      val result = parser.parse(response, defaultFile = "src/hello.js")
      val newFileBlocks = result.filterIsInstance<PatchParser.ResponseSegment.NewFileBlock>()
      assertEquals(1, newFileBlocks.size)
      assertEquals("src/hello.js", newFileBlocks[0].filename)
      assertEquals("javascript", newFileBlocks[0].language)
    }
  }

  @Nested
  inner class ExplicitMarkerParsing {
    @Test
    fun `parse explicit DIFF markers`() {
      val response = """
Some intro text
<<<DIFF src/main/Example.kt>>>
```diff
fun hello() {
-    return 1
+    return 2
}
```
<<<END>>>
Some trailing text
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/Example.kt", diffBlocks[0].filename)
    }

    @Test
    fun `parse explicit PATCH markers`() {
      val response = """
<<<PATCH src/main/NewFile.kt>>>
package com.example
fun newFunction() {
println("Hello")
}
<<<END>>>
""".trimIndent()
      val result = parser.parse(response)
// PATCH markers with non-diff content should produce a NewFileBlock
      val newFileBlocks = result.filterIsInstance<PatchParser.ResponseSegment.NewFileBlock>()
      assertEquals(1, newFileBlocks.size)
      assertEquals("src/main/NewFile.kt", newFileBlocks[0].filename)
    }

    @Test
    fun `parse multiple explicit markers`() {
      val response = """
<<<DIFF src/main/Foo.kt>>>
class Foo {
-    val x = 1
+    val x = 2
}
<<<END>>>
<<<DIFF src/main/Bar.kt>>>
class Bar {
-    val y = 3
+    val y = 4
}
<<<END>>>
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(2, diffBlocks.size)
      assertEquals("src/main/Foo.kt", diffBlocks[0].filename)
      assertEquals("src/main/Bar.kt", diffBlocks[1].filename)
    }

    @Test
    fun `explicit markers preserve preceding and trailing markdown`() {
      val response = """
Here is the change:
<<<DIFF src/main/Example.kt>>>
val x = 1
-val y = 2
+val y = 3
<<<END>>>
That should fix the issue.
""".trimIndent()
      val result = parser.parse(response)
      val markdowns = result.filterIsInstance<PatchParser.ResponseSegment.Markdown>()
      assertTrue(markdowns.any { it.content.contains("Here is the change") })
      assertTrue(markdowns.any { it.content.contains("That should fix the issue") })
    }

    @Test
    fun `explicit markers are case insensitive`() {
      val response = """
<<<diff src/main/Example.kt>>>
val x = 1
-val y = 2
+val y = 3
<<<end>>>
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/Example.kt", diffBlocks[0].filename)
    }
  }

  @Nested
  inner class FilenameNormalization {
    @Test
    fun `normalize filename removes common prefixes`() {
      val testCases = listOf(
        "File: src/main/Example.kt" to "src/main/Example.kt",
        "file: src/main/Example.kt" to "src/main/Example.kt",
        "Code: src/main/Example.kt" to "src/main/Example.kt",
        "Path: src/main/Example.kt" to "src/main/Example.kt",
        "Modified: src/main/Example.kt" to "src/main/Example.kt",
        "Updated: src/main/Example.kt" to "src/main/Example.kt",
        "Changed: src/main/Example.kt" to "src/main/Example.kt",
        "Edit: src/main/Example.kt" to "src/main/Example.kt",
        "Patch: src/main/Example.kt" to "src/main/Example.kt",
      )
      for ((input, expected) in testCases) {
        val response = """
### $input
```diff
val x = 1
-val y = 2
+val y = 3
```
""".trimIndent()
        val result = parser.parse(response)
        val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
        assertEquals(1, diffBlocks.size, "Failed for input: $input")
        assertEquals(expected, diffBlocks[0].filename, "Failed for input: $input")
      }
    }

    @Test
    fun `normalize filename removes quotes and backticks`() {
      val response = """
### `src/main/Example.kt`
```diff
val x = 1
-val y = 2
+val y = 3
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/Example.kt", diffBlocks[0].filename)
    }

    @Test
    fun `normalize filename removes markdown bold formatting`() {
      val response = """
### **src/main/Example.kt**
```diff
val x = 1
-val y = 2
+val y = 3
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/Example.kt", diffBlocks[0].filename)
    }

    @Test
    fun `normalize filename removes numbered list prefix`() {
      val response = """
### 1. src/main/Example.kt
```diff
val x = 1
-val y = 2
+val y = 3
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/Example.kt", diffBlocks[0].filename)
    }

    @Test
    fun `language-only header is not treated as filename`() {
      val response = """
### kotlin
```kotlin
fun hello() = println("Hello")
```
""".trimIndent()
      val result = parser.parse(response)
// "kotlin" alone should be normalized to empty, so no DiffBlock or NewFileBlock
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      val newFileBlocks = result.filterIsInstance<PatchParser.ResponseSegment.NewFileBlock>()
      assertEquals(0, diffBlocks.size)
      assertEquals(0, newFileBlocks.size)
    }
  }

  @Nested
  inner class FileHeaderPattern {
    @Test
    fun `parse file header with dashes`() {
      val response = """
--------------------
File: src/main/Example.kt
--------------------
```diff
val x = 1
-val y = 2
+val y = 3
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/Example.kt", diffBlocks[0].filename)
    }
  }

  @Nested
  inner class AutoCloseCodeBlocks {
    @Test
    fun `auto-close unclosed code block`() {
      val response = """
### src/main/Example.kt
```diff
fun hello() {
-    return 1
+    return 2
}
""".trimIndent()
// The code block is not closed, parser should auto-close it
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/Example.kt", diffBlocks[0].filename)
    }
  }

  @Nested
  inner class MixedContent {
    @Test
    fun `parse response with markdown and diff blocks interleaved`() {
      val response = """
Here is the first change:
### src/main/Foo.kt
```diff
class Foo {
-    val x = 1
+    val x = 2
}
```
And here is the second change:
### src/main/Bar.kt
```diff
class Bar {
-    val y = 3
+    val y = 4
}
```
That's all the changes needed.
""".trimIndent()
      val result = parser.parse(response)
      val markdowns = result.filterIsInstance<PatchParser.ResponseSegment.Markdown>()
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(2, diffBlocks.size)
      assertTrue(markdowns.any { it.content.contains("first change") })
      assertTrue(markdowns.any { it.content.contains("second change") })
      assertTrue(markdowns.any { it.content.contains("That's all") })
    }

    @Test
    fun `parse response with new file and diff blocks`() {
      val response = """
### src/main/NewFile.kt
```kotlin
package com.example
class NewFile {
fun hello() = "Hello"
}
```
### src/main/ExistingFile.kt
```diff
class ExistingFile {
-    fun greet() = "Hi"
+    fun greet() = "Hello"
}
```
""".trimIndent()
      val result = parser.parse(response)
      val newFileBlocks = result.filterIsInstance<PatchParser.ResponseSegment.NewFileBlock>()
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, newFileBlocks.size)
      assertEquals("src/main/NewFile.kt", newFileBlocks[0].filename)
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/ExistingFile.kt", diffBlocks[0].filename)
    }
  }

  @Nested
  inner class EdgeCases {
    @Test
    fun `parse response with nested code fences in explicit markers`() {
      val response = """
<<<DIFF README.md>>>
```md
This file contains utility functions.
Example usage:
```js
print("Something")
```
```
<<<END>>>
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("README.md", diffBlocks[0].filename)
    }

    @Test
    fun `parse response with filename containing spaces`() {
      val response = """
### src/main/My File.kt
```diff
val x = 1
-val y = 2
+val y = 3
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/My File.kt", diffBlocks[0].filename)
    }

    @Test
    fun `parse response with only markdown headers and no code blocks`() {
      val response = """
### Introduction
This is some text.
### Conclusion
This is more text.
""".trimIndent()
      val result = parser.parse(response)
      assertEquals(1, result.size)
      assertTrue(result[0] is PatchParser.ResponseSegment.Markdown)
    }

    @Test
    fun `parse response with empty code block`() {
      val response = """
### src/main/Empty.kt
```kotlin
```
""".trimIndent()
      val result = parser.parse(response)
// Empty code block with a filename header
      val newFileBlocks = result.filterIsInstance<PatchParser.ResponseSegment.NewFileBlock>()
      if (newFileBlocks.isNotEmpty()) {
        assertEquals("src/main/Empty.kt", newFileBlocks[0].filename)
        assertTrue(newFileBlocks[0].code.isBlank())
      }
    }

    @Test
    fun `resolveFilename prefers header over default`() {
      val response = """
### src/main/FromHeader.kt
```diff
val x = 1
-val y = 2
+val y = 3
```
""".trimIndent()
      val result = parser.parse(response, defaultFile = "src/main/Default.kt")
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/FromHeader.kt", diffBlocks[0].filename)
    }

    @Test
    fun `diff marker takes precedence over markdown header`() {
      val response = """
### src/main/HeaderFile.kt
<<<DIFF src/main/MarkerFile.kt>>>
```diff
val x = 1
-val y = 2
+val y = 3
```
<<<END>>>
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals("src/main/MarkerFile.kt", diffBlocks[0].filename)
    }
  }

  @Nested
  inner class ResponseSegmentTypes {
    @Test
    fun `Markdown segment data class equality`() {
      val seg1 = PatchParser.ResponseSegment.Markdown("hello")
      val seg2 = PatchParser.ResponseSegment.Markdown("hello")
      val seg3 = PatchParser.ResponseSegment.Markdown("world")
      assertEquals(seg1, seg2)
      assertNotEquals(seg1, seg3)
    }

    @Test
    fun `DiffBlock segment data class equality`() {
      val seg1 = PatchParser.ResponseSegment.DiffBlock("file.kt", "+line", 0..10)
      val seg2 = PatchParser.ResponseSegment.DiffBlock("file.kt", "+line", 0..10)
      val seg3 = PatchParser.ResponseSegment.DiffBlock("other.kt", "+line", 0..10)
      assertEquals(seg1, seg2)
      assertNotEquals(seg1, seg3)
    }

    @Test
    fun `NewFileBlock segment data class equality`() {
      val seg1 = PatchParser.ResponseSegment.NewFileBlock("file.kt", "kotlin", "code", 0..10)
      val seg2 = PatchParser.ResponseSegment.NewFileBlock("file.kt", "kotlin", "code", 0..10)
      val seg3 = PatchParser.ResponseSegment.NewFileBlock("file.kt", "java", "code", 0..10)
      assertEquals(seg1, seg2)
      assertNotEquals(seg1, seg3)
    }
  }

  @Nested
  inner class PatchFormatPrompt {
    @Test
    fun `patchFormatPrompt is not blank`() {
      assertTrue(parser.patchFormatPrompt.isNotBlank())
    }

    @Test
    fun `patchFormatPrompt contains expected keywords`() {
      val prompt = parser.patchFormatPrompt
      assertTrue(prompt.contains("diff"))
      assertTrue(prompt.contains("DIFF"))
      assertTrue(prompt.contains("END"))
      assertTrue(prompt.contains("```"))
    }
  }

  @Nested
  inner class StripDiffMarkerLines {
    @Test
    fun `markdown segments do not contain diff marker artifacts`() {
      val response = """
Here is the change:
<<<DIFF src/main/Example.kt>>>
```diff
val x = 1
-val y = 2
+val y = 3
```
<<<END>>>
Done.
""".trimIndent()
      val result = parser.parse(response)
      val markdowns = result.filterIsInstance<PatchParser.ResponseSegment.Markdown>()
      for (md in markdowns) {
        assertFalse(md.content.contains("<<<DIFF"), "Markdown should not contain <<<DIFF marker: ${md.content}")
        assertFalse(md.content.contains("<<<END"), "Markdown should not contain <<<END marker: ${md.content}")
      }
    }
  }

  @Nested
  inner class ComplexScenarios {
    @Test
    fun `parse real-world-like response with explanation and multiple patches`() {
      val response = """
I've analyzed the code and found two issues. Here are the fixes:
### src/main/kotlin/com/example/Service.kt
The `processData` method has a bug in the null check:
```diff
fun processData(input: String?): Result {
-        if (input != null) {
+        if (!input.isNullOrBlank()) {
return Result.success(input)
}
```
### src/main/kotlin/com/example/Repository.kt
The query needs to be updated to use the new table name:
```diff
fun findAll(): List<Entity> {
-        return jdbc.query("SELECT * FROM old_table")
+        return jdbc.query("SELECT * FROM new_table")
}
```
These changes should resolve the reported issues. Please run the test suite to verify.
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      val markdowns = result.filterIsInstance<PatchParser.ResponseSegment.Markdown>()
      assertEquals(2, diffBlocks.size)
      assertEquals("src/main/kotlin/com/example/Service.kt", diffBlocks[0].filename)
      assertEquals("src/main/kotlin/com/example/Repository.kt", diffBlocks[1].filename)
      assertTrue(markdowns.any { it.content.contains("analyzed the code") })
      assertTrue(markdowns.any { it.content.contains("resolve the reported issues") })
    }

    @Test
    fun `parse response with deeply nested path`() {
      val response = """
### src/main/kotlin/com/example/deep/nested/package/MyClass.kt
```diff
class MyClass {
-    val version = "1.0"
+    val version = "2.0"
}
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
      assertEquals(
        "src/main/kotlin/com/example/deep/nested/package/MyClass.kt",
        diffBlocks[0].filename
      )
    }

    @Test
    fun `parse response with various file extensions`() {
      val extensions = listOf("kt", "java", "py", "js", "ts", "go", "rs", "cpp", "rb")
      for (ext in extensions) {
        val response = """
### src/main/Example.$ext
```diff
line1
-line2
+line3
```
""".trimIndent()
        val result = parser.parse(response)
        val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
        assertEquals(1, diffBlocks.size, "Failed for extension: $ext")
        assertEquals("src/main/Example.$ext", diffBlocks[0].filename, "Failed for extension: $ext")
      }
    }

    @Test
    fun `parse explicit markers with embedded code fences in content`() {
      val response = """
<<<PATCH docs/README.md>>>
# Project README
Example usage:
```kotlin
fun main() {
println("Hello")
}
```
More documentation here.
<<<END>>>
""".trimIndent()
      val result = parser.parse(response)
// Should have a NewFileBlock since PATCH with non-diff content
      val newFileBlocks = result.filterIsInstance<PatchParser.ResponseSegment.NewFileBlock>()
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      val totalCodeBlocks = newFileBlocks.size + diffBlocks.size
      assertEquals(1, totalCodeBlocks)
      val filename = if (newFileBlocks.isNotEmpty()) newFileBlocks[0].filename else diffBlocks[0].filename
      assertEquals("docs/README.md", filename)
    }
  }

  @Nested
  inner class DiffContentDetection {
    @Test
    fun `code block with diff language is always treated as diff`() {
      val response = """
### src/main/Example.kt
```diff
this is not really a diff
but the language says diff
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
    }

    @Test
    fun `code block with mostly plus and minus lines is treated as diff`() {
      val response = """
### src/main/Example.kt
```kotlin
-old line 1
+new line 1
-old line 2
+new line 2
-old line 3
+new line 3
```
""".trimIndent()
      val result = parser.parse(response)
      val diffBlocks = result.filterIsInstance<PatchParser.ResponseSegment.DiffBlock>()
      assertEquals(1, diffBlocks.size)
    }

    @Test
    fun `code block without diff indicators is treated as new file`() {
      val response = """
### src/main/Example.kt
```kotlin
package com.example
class Example {
fun hello() = "Hello"
fun world() = "World"
}
```
""".trimIndent()
      val result = parser.parse(response)
      val newFileBlocks = result.filterIsInstance<PatchParser.ResponseSegment.NewFileBlock>()
      assertEquals(1, newFileBlocks.size)
      assertEquals("src/main/Example.kt", newFileBlocks[0].filename)
    }
  }
}