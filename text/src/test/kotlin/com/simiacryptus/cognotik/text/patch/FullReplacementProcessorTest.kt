package com.simiacryptus.cognotik.text.patch

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FullReplacementProcessorTest {

  private lateinit var processor: FullReplacementProcessor

  @BeforeEach
  fun setUp() {
    processor = FullReplacementProcessor()
  }

  // ------------------------------------------------------------------
  // Metadata / contract
  // ------------------------------------------------------------------

  @Nested
  @DisplayName("metadata")
  inner class Metadata {

    @Test
    fun `label is FullReplacement`() {
      assertEquals("FullReplacement", processor.label)
    }

    @Test
    fun `label is stable across instances`() {
      assertEquals(FullReplacementProcessor().label, FullReplacementProcessor().label)
    }

    @Test
    fun `patchFormatPrompt is not blank`() {
      assertNotNull(processor.patchFormatPrompt)
      assertFalse(processor.patchFormatPrompt.isBlank(), "patchFormatPrompt must not be blank")
    }

    @Test
    fun `patchFormatPrompt is trimIndent-ed (no leading or trailing newline)`() {
      val prompt = processor.patchFormatPrompt
      assertFalse(prompt.startsWith("\n"), "prompt should not start with a newline")
      assertFalse(prompt.endsWith("\n"), "prompt should not end with a newline")
      assertEquals(prompt, prompt.trimIndent(), "prompt should already be trimIndent-ed")
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "complete updated file content",
        "entire file content should be provided",
        "header that identifies the file being modified",
        "```javascript",
        "### src/utils/exampleUtils.js",
        "### tests/exampleUtils.test.js"
      ]
    )
    fun `patchFormatPrompt contains required guidance`(fragment: String) {
      assertTrue(
        processor.patchFormatPrompt.contains(fragment),
        "Expected patchFormatPrompt to contain: $fragment"
      )
    }

    @Test
    fun `patchFormatPrompt contains balanced code fences`() {
      val fenceCount = processor.patchFormatPrompt.lines().count { it.trim().startsWith("```") }
      assertTrue(fenceCount >= 4, "Expected at least two code blocks (4 fences), found $fenceCount")
      assertEquals(0, fenceCount % 2, "Code fences should be balanced (even count)")
    }

    @Test
    fun `patchFormatPrompt does not advertise diff syntax`() {
      val prompt = processor.patchFormatPrompt
      assertFalse(prompt.contains("```diff"), "Full replacement should not request diff blocks")
    }

    @Test
    fun `patchFormatPrompt is identical across instances`() {
      assertEquals(FullReplacementProcessor().patchFormatPrompt, FullReplacementProcessor().patchFormatPrompt)
    }
  }

  // ------------------------------------------------------------------
  // generatePatch
  // ------------------------------------------------------------------

  @Nested
  @DisplayName("generatePatch")
  inner class GeneratePatch {

    @Test
    fun `returns the new code verbatim`() {
      val oldCode = "val x = 1\n"
      val newCode = "val x = 2\nval y = 3\n"
      assertEquals(newCode, processor.generatePatch(oldCode, newCode))
    }

    @Test
    fun `returns the same instance as newCode (no copying or normalization)`() {
      val newCode = "line1\nline2"
      assertSame(newCode, processor.generatePatch("anything", newCode))
    }

    @Test
    fun `ignores old code entirely`() {
      val newCode = "final content"
      val fromEmpty = processor.generatePatch("", newCode)
      val fromLarge = processor.generatePatch("x".repeat(10_000), newCode)
      val fromSame = processor.generatePatch(newCode, newCode)
      assertEquals(newCode, fromEmpty)
      assertEquals(newCode, fromLarge)
      assertEquals(newCode, fromSame)
    }

    @Test
    fun `preserves leading and trailing whitespace of new code`() {
      val newCode = "\n\n    indented body    \n\n"
      assertEquals(newCode, processor.generatePatch("old", newCode))
    }

    @Test
    fun `supports empty new code`() {
      assertEquals("", processor.generatePatch("old content", ""))
    }

    @Test
    fun `supports whitespace-only new code`() {
      val newCode = "   \t\n  "
      assertEquals(newCode, processor.generatePatch("old", newCode))
    }

    @Test
    fun `preserves CRLF line endings`() {
      val newCode = "a\r\nb\r\nc\r\n"
      val patch = processor.generatePatch("a\nb\n", newCode)
      assertEquals(newCode, patch)
      assertTrue(patch.contains("\r\n"), "CRLF must not be normalized")
    }

    @Test
    fun `preserves unicode and emoji content`() {
      val newCode = "val greeting = \"こんにちは 🌍 Ωμέγα\"\n"
      assertEquals(newCode, processor.generatePatch("val greeting = \"hi\"", newCode))
    }

    @Test
    fun `preserves content that looks like a unified diff`() {
      val newCode = """
                    --- a/file.txt
                    +++ b/file.txt
                    @@ -1,2 +1,2 @@
                    -old
                    +new
                """.trimIndent()
      assertEquals(newCode, processor.generatePatch("old", newCode))
    }

    @Test
    fun `handles large content`() {
      val newCode = (1..20_000).joinToString("\n") { "line $it" }
      val patch = processor.generatePatch("small", newCode)
      assertEquals(newCode, patch)
      assertEquals(20_000, patch.lines().size)
    }

    @Test
    fun `is deterministic across repeated invocations`() {
      val newCode = "deterministic\ncontent"
      val results = (1..25).map { processor.generatePatch("old-$it", newCode) }.distinct()
      assertEquals(1, results.size)
      assertEquals(newCode, results.single())
    }
  }

  // ------------------------------------------------------------------
  // applyPatch
  // ------------------------------------------------------------------

  @Nested
  @DisplayName("applyPatch")
  inner class ApplyPatch {

    @Test
    fun `returns the patch content`() {
      val patch = "val x = 2"
      assertEquals(patch, processor.applyPatch("val x = 1", patch))
    }

    @Test
    fun `ignores the source content entirely`() {
      val patch = "replacement"
      assertEquals(patch, processor.applyPatch("", patch))
      assertEquals(patch, processor.applyPatch("completely\nunrelated\nsource", patch))
      assertEquals(patch, processor.applyPatch("y".repeat(50_000), patch))
    }

    @Test
    fun `trims leading and trailing whitespace`() {
      assertEquals("body", processor.applyPatch("src", "\n\n   body   \n\t\n"))
    }

    @Test
    fun `trims markdown-style trailing newline commonly emitted by LLMs`() {
      assertEquals("fun main() {}", processor.applyPatch("src", "fun main() {}\n"))
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "'','' ",
        "'   ',''",
        "'\n\n',''",
        "'\t \t',''"
      ]
    )
    fun `blank patches collapse to empty string`(patch: String, expected: String) {
      assertEquals(expected, processor.applyPatch("some source", patch))
    }

    @Test
    fun `preserves internal blank lines and indentation`() {
      val patch = """
                    class A {

                        fun f() {
                            return 1
                        }

                    }
                """.trimIndent()
      val result = processor.applyPatch("class A", "\n$patch\n")
      assertEquals(patch, result)
      assertTrue(result.contains("\n\n"), "internal blank lines must be preserved")
      assertTrue(result.contains("        return 1"), "internal indentation must be preserved")
    }

    @Test
    fun `preserves internal CRLF while trimming edges`() {
      val result = processor.applyPatch("src", "  a\r\nb\r\nc  ")
      assertEquals("a\r\nb\r\nc", result)
    }

    @Test
    fun `is idempotent`() {
      val patch = "  content with padding  "
      val once = processor.applyPatch("src", patch)
      val twice = processor.applyPatch("src", once)
      assertEquals(once, twice)
      assertEquals("content with padding", once)
    }

    @Test
    fun `does not interpret patch markers`() {
      val patch = "- removed line\n+ added line\n@@ hunk @@"
      assertEquals(patch, processor.applyPatch("original", patch))
    }

    @Test
    fun `preserves unicode content`() {
      val patch = "  «Ünïcødé» 🚀  "
      assertEquals("«Ünïcødé» 🚀", processor.applyPatch("ascii", patch))
    }

    @Test
    fun `handles large patches`() {
      val body = (1..20_000).joinToString("\n") { "line $it" }
      assertEquals(body, processor.applyPatch("tiny", "\n$body\n"))
    }
  }

  // ------------------------------------------------------------------
  // Round-trip properties
  // ------------------------------------------------------------------

  @Nested
  @DisplayName("round-trip")
  inner class RoundTrip {

    @ParameterizedTest
    @ValueSource(
      strings = [
        "single line",
        "line1\nline2\nline3",
        "class Foo {\n    fun bar() = 42\n}",
        "λ x -> x + 1",
        "{ \"json\": [1, 2, 3] }"
      ]
    )
    fun `applyPatch of generatePatch yields the trimmed new code`(newCode: String) {
      val patch = processor.generatePatch("previous content", newCode)
      assertEquals(newCode.trim(), processor.applyPatch("previous content", patch))
    }

    @Test
    fun `round-trip is exact when new code has no surrounding whitespace`() {
      val newCode = "exact\ncontent"
      assertEquals(newCode, processor.applyPatch("old", processor.generatePatch("old", newCode)))
    }

    @Test
    fun `round-trip normalizes surrounding whitespace only`() {
      val newCode = "\n\n  body  \n\n"
      assertEquals("body", processor.applyPatch("old", processor.generatePatch("old", newCode)))
    }

    @Test
    fun `round-trip of empty new code yields empty result`() {
      assertEquals("", processor.applyPatch("old content", processor.generatePatch("old content", "")))
    }

    @Test
    fun `repeated round-trips converge`() {
      var current = "  start  "
      repeat(5) { current = processor.applyPatch("ignored", processor.generatePatch("ignored", current)) }
      assertEquals("start", current)
    }
  }

  // ------------------------------------------------------------------
  // Interface polymorphism & statelessness
  // ------------------------------------------------------------------

  @Nested
  @DisplayName("PatchProcessor contract")
  inner class InterfaceContract {

    @Test
    fun `is usable through the PatchProcessor interface`() {
      val p: PatchProcessor = processor
      assertEquals("FullReplacement", p.label)
      assertFalse(p.patchFormatPrompt.isBlank())
      assertEquals("new", p.generatePatch("old", "new"))
      assertEquals("new", p.applyPatch("old", " new "))
    }

    @Test
    fun `processor is stateless across mixed invocations`() {
      assertEquals("a", processor.applyPatch("s1", " a "))
      assertEquals("b", processor.generatePatch("s2", "b"))
      assertEquals("a", processor.applyPatch("s3", " a "))
      assertEquals("b", processor.generatePatch("s4", "b"))
    }

    @Test
    fun `is safe for concurrent use`() {
      val threads = 8
      val iterations = 500
      val pool = Executors.newFixedThreadPool(threads)
      try {
        val tasks = (0 until threads).map { t ->
          Callable {
            repeat(iterations) { i ->
              val content = "thread-$t-iteration-$i"
              val patch = processor.generatePatch("old-$t-$i", "  $content  ")
              if (processor.applyPatch("src", patch) != content) return@Callable false
            }
            true
          }
        }
        val results = pool.invokeAll(tasks).map { it.get(30, TimeUnit.SECONDS) }
        assertTrue(results.all { it }, "All concurrent round-trips should succeed")
      } finally {
        pool.shutdownNow()
      }
    }
  }
}