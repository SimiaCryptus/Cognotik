package com.simiacryptus.cognotik.docops.spec

    import org.junit.jupiter.api.Assertions.assertEquals
    import org.junit.jupiter.api.Test

    class MarkdownLinksTest {

      @Test
      fun `unwraps a markdown link`() {
        assertEquals("src/Widget.kt", MarkdownLinks.extractPath("[Widget](src/Widget.kt)"))
      }

      @Test
      fun `tolerates surrounding and inner whitespace`() {
        assertEquals("gen/x.kt", MarkdownLinks.extractPath("  [gen]( gen/x.kt ) "))
      }

      @Test
      fun `trims plain values`() {
        assertEquals("src/Widget.kt", MarkdownLinks.extractPath("  src/Widget.kt  "))
      }

      @Test
      fun `leaves malformed links untouched`() {
        assertEquals("[Widget](a b)", MarkdownLinks.extractPath("[Widget](a b)"))
        assertEquals("[Widget]", MarkdownLinks.extractPath("[Widget]"))
      }

      @Test
      fun `maps a list`() {
        assertEquals(
          listOf("a.kt", "b.kt"),
          MarkdownLinks.extractPaths(listOf("[a](a.kt)", " b.kt "))
        )
      }
    }