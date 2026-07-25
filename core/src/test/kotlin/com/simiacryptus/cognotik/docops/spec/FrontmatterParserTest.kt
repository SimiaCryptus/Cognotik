package com.simiacryptus.cognotik.docops.spec

    import org.junit.jupiter.api.Assertions.*
    import org.junit.jupiter.api.Test

    class FrontmatterParserTest {

      @Test
      fun `split returns null without leading marker`() {
        assertNull(FrontmatterParser.split("# Just a doc\n"))
      }

      @Test
      fun `split returns null without closing marker`() {
        assertNull(FrontmatterParser.split("---\nspecifies: a.kt\n"))
      }

      @Test
      fun `split separates frontmatter and body`() {
        val (fm, body) = FrontmatterParser.split("---\nspecifies: a.kt\n---\n\n# Title\ntext\n")!!
        assertEquals("specifies: a.kt", fm)
        assertEquals("# Title\ntext", body)
      }

      @Test
      fun `split tolerates empty body`() {
        val (fm, body) = FrontmatterParser.split("---\nfolder: sub\n---")!!
        assertEquals("folder: sub", fm)
        assertEquals("", body)
      }

      @Test
      fun `parse reads scalars`() {
        val fm = FrontmatterParser.parse("task_type: CodeEdit\nupdate_mode: ForceUpdate")
        assertEquals("CodeEdit", fm["task_type"])
        assertEquals("ForceUpdate", fm["update_mode"])
      }

      @Test
      fun `parse keeps everything after the first colon`() {
        val fm = FrontmatterParser.parse("related: https://example.com/spec.html")
        assertEquals("https://example.com/spec.html", fm["related"])
      }

      @Test
      fun `parse reads lists`() {
        val fm = FrontmatterParser.parse("specifies:\n  - a.kt\n  - b.kt\ntask_type: CodeEdit")
        assertEquals(listOf("a.kt", "b.kt"), fm["specifies"])
        assertEquals("CodeEdit", fm["task_type"])
      }

      @Test
      fun `parse drops keys with an empty list`() {
        val fm = FrontmatterParser.parse("specifies:\ntask_type: CodeEdit")
        assertFalse(fm.containsKey("specifies"))
        assertEquals("CodeEdit", fm["task_type"])
      }

      @Test
      fun `parse ignores lines without a colon`() {
        val fm = FrontmatterParser.parse("garbage\nfolder: sub")
        assertEquals(mapOf<String, Any>("folder" to "sub"), fm)
      }

      @Test
      fun `parse only captures the first line of a block scalar (documented limitation)`() {
        val fm = FrontmatterParser.parse("prompt: |\n  keep the api stable\nfolder: sub")
        assertEquals("|", fm["prompt"])
      }

      @Test
      fun `render round trips through parse`() {
        val original = linkedMapOf<String, Any>(
          "specifies" to listOf("a.kt", "b.kt"),
          "task_type" to "CodeEdit",
        )
        val rendered = FrontmatterParser.render(original)
        assertEquals("specifies:\n  - a.kt\n  - b.kt\ntask_type: CodeEdit\n", rendered)
        assertEquals(original, FrontmatterParser.parse(rendered))
      }

      @Test
      fun `render emits nested maps as indented pairs`() {
        val rendered = FrontmatterParser.render(mapOf("vars" to mapOf("A" to "1")))
        assertEquals("vars:\n  A: 1\n", rendered)
      }
    }