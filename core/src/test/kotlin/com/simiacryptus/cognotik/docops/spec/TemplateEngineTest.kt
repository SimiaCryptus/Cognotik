package com.simiacryptus.cognotik.docops.spec

    import com.simiacryptus.cognotik.docops.child
    import org.junit.jupiter.api.Assertions.*
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.io.TempDir
    import java.io.File

    class TemplateEngineTest {

      @TempDir
      lateinit var tempDir: File

      private val root: File get() = tempDir.canonicalFile

      @Test
      fun `parseVars reads maps`() {
        val vars = TemplateEngine.parseVars(mapOf("vars" to mapOf("A" to "1", "B" to 2)))
        assertEquals(mapOf("A" to "1", "B" to "2"), vars)
      }

      @Test
      fun `parseVars reads colon and equals separated list entries`() {
        val vars = TemplateEngine.parseVars(mapOf("vars" to listOf("PROJECT: cognotik", "LANG=kotlin")))
        assertEquals(mapOf("PROJECT" to "cognotik", "LANG" to "kotlin"), vars)
      }

      @Test
      fun `parseVars treats a bare list entry as an empty default`() {
        assertEquals(mapOf("NEEDED" to ""), TemplateEngine.parseVars(mapOf("vars" to listOf("NEEDED"))))
      }

      @Test
      fun `parseVars ignores a bare string value`() {
        assertTrue(TemplateEngine.parseVars(mapOf("vars" to "NEEDED")).isEmpty())
        assertEquals(mapOf("A" to "1"), TemplateEngine.parseVars(mapOf("vars" to "A: 1")))
      }

      @Test
      fun `parseVars merges every recognized key`() {
        val vars = TemplateEngine.parseVars(
          mapOf(
            "template_vars" to listOf("A: 1"),
            "variables" to listOf("B: 2"),
          )
        )
        assertEquals(mapOf("A" to "1", "B" to "2"), vars)
      }

      @Test
      fun `parseVars ignores unsupported value types`() {
        assertTrue(TemplateEngine.parseVars(mapOf("vars" to 42)).isEmpty())
      }

      @Test
      fun `substitute replaces known placeholders and leaves unknown ones`() {
        val out = TemplateEngine.substitute("a {{ A }} b {{B}} c {{ MISSING }}", mapOf("A" to "1", "B" to "2"))
        assertEquals("a 1 b 2 c {{ MISSING }}", out)
      }

      @Test
      fun `substitute escapes dollar signs in replacements`() {
        assertEquals("cost: \\\$1", TemplateEngine.substitute("cost: {{ P }}", mapOf("P" to "\$1")))
      }

      @Test
      fun `substitute is a no-op with no variables`() {
        assertEquals("{{ A }}", TemplateEngine.substitute("{{ A }}", emptyMap()))
      }

      @Test
      fun `listKeys reads declared defaults from a file`() {
        val doc = root.child("doc.md", "---\nvars:\n  - A: 1\n  - B\nspecifies:\n  - x.kt\n---\nbody")
        assertEquals(mapOf("A" to "1", "B" to ""), TemplateEngine.listKeys(doc))
      }

      @Test
      fun `listKeys returns empty for missing files and files without frontmatter`() {
        assertTrue(TemplateEngine.listKeys(File(root, "nope.md")).isEmpty())
        assertTrue(TemplateEngine.listKeys(root.child("plain.md", "# hi")).isEmpty())
      }

      @Test
      fun `listKeys over several files keeps the first declaration`() {
        val a = root.child("a.md", "---\nvars:\n  - A: first\nspecifies:\n  - x.kt\n---\n")
        val b = root.child("b.md", "---\nvars:\n  - A: second\n  - B: 2\nspecifies:\n  - y.kt\n---\n")
        assertEquals(mapOf("A" to "first", "B" to "2"), TemplateEngine.listKeys(listOf(a, b)))
      }
    }