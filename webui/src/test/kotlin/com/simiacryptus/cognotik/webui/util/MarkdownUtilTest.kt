package com.simiacryptus.cognotik.webui.util

import com.simiacryptus.cognotik.util.MarkdownUtil
import com.vladsch.flexmark.util.data.MutableDataSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownUtilTest {

  private val markdownUtil = MarkdownUtil

  @Test
  fun testRenderMarkdown_BlankInput() {
    val result = markdownUtil.renderMarkdown("")
    assertEquals("", result, "Rendering blank markdown should return an empty string.")
  }

  @Test
  fun testRenderMarkdown_SimpleMarkdown() {
    val markdown = "# Heading\n\nThis is a test."
    val expectedHtml = "<h1>Heading</h1>\n<p>This is a test.</p>\n"
    val result = markdownUtil.renderMarkdown(markdown, MutableDataSet(), false, null)
    assert(result.contains(expectedHtml)) { "Rendered HTML should contain the expected HTML content." }
  }

  @Test
  fun testFixupMermaidCode() {
    val input = "graph TD; A-->B; A[Square Rect] -- Link text --> C(Round Rect); C --> D{Rhombus};"
    val expected = "graph TD; A-->B; A[Square Rect] -- Link text --> C(Round Rect); C --> D{Rhombus};"
    val result = markdownUtil.fixupMermaidCode(input)
    assertEquals(expected, result, "The Mermaid code should be correctly formatted.")
  }


}