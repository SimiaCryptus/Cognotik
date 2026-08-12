package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HtmlSimplifierTest {

  private fun scrub(html: String, vararg dummy: Unit) = HtmlSimplifier.scrubHtml(
    str = html,
    treeIndexLinks = false
  )

  @Test
  fun `blank input is rejected`() {
    assertThrows(IllegalArgumentException::class.java) { HtmlSimplifier.scrubHtml("   ") }
  }

  @Test
  fun `data urls are rejected`() {
    assertThrows(IllegalArgumentException::class.java) { HtmlSimplifier.scrubHtml("data:text/html,<p>x</p>") }
  }

  @Test
  fun `javascript urls are rejected`() {
    assertThrows(IllegalArgumentException::class.java) { HtmlSimplifier.scrubHtml("javascript:alert(1)") }
  }

  @Test
  fun `javascript base url is rejected`() {
    assertThrows(IllegalArgumentException::class.java) {
      HtmlSimplifier.scrubHtml("<p>x</p>", baseUrl = "javascript:void(0)")
    }
  }

  @Test
  fun `scripts are removed and text preserved`() {
    val out = scrub("<html><body><p>Hello <b>World</b></p><script>alert('x')</script></body></html>")
    assertTrue(out.contains("Hello"), out)
    assertTrue(out.contains("World"), out)
    assertFalse(out.contains("alert"), out)
    assertFalse(out.contains("<script"), out)
  }

  @Test
  fun `style elements are dropped when css is excluded`() {
    val out = scrub("<html><head><style>p{color:red}</style></head><body><p>Text</p></body></html>")
    assertFalse(out.contains("color:red"), out)
    assertTrue(out.contains("Text"), out)
  }

  @Test
  fun `interactive elements are removed by default`() {
    val out = scrub("<body><form action='/s'><input name='q'></form><p>Body</p></body>")
    assertFalse(out.contains("<form"), out)
    assertFalse(out.contains("<input"), out)
    assertTrue(out.contains("Body"), out)
  }

  @Test
  fun `interactive elements can be kept`() {
    val out = HtmlSimplifier.scrubHtml(
      "<body><form action='/s'><input name='q' value='1'></form><p>Body</p></body>",
      keepInteractiveElements = true,
      treeIndexLinks = false
    )
    assertTrue(out.contains("form") || out.contains("input"), out)
  }

  @Test
  fun `navigational chrome is removed by default`() {
    val out = scrub("<body><nav><span>Menu</span></nav><header><span>Head</span></header><p>Body</p></body>")
    assertFalse(out.contains("Menu"), out)
    assertFalse(out.contains("Head"), out)
    assertTrue(out.contains("Body"), out)
  }

  @Test
  fun `navigational chrome can be kept`() {
    val out = HtmlSimplifier.scrubHtml(
      "<body><nav><p>Menu</p></nav><p>Body</p></body>",
      removeNavigationalElements = false,
      treeIndexLinks = false
    )
    assertTrue(out.contains("Menu"), out)
  }

  @Test
  fun `transient ui roles are removed`() {
    val out = scrub("<body><div role='tooltip'><p>Tip</p></div><p>Body</p></body>")
    assertFalse(out.contains("Tip"), out)
    assertTrue(out.contains("Body"), out)
  }

  @Test
  fun `data attributes are removed`() {
    val out = scrub("<body><p data-tracking='abc' title='t'>Text</p></body>")
    assertFalse(out.contains("data-tracking"), out)
    assertTrue(out.contains("title=\"t\""), out)
  }

  @Test
  fun `event handlers are removed`() {
    val out = scrub("<body><p onmouseover=\"track()\" onclick=\"go()\">Text</p></body>")
    assertFalse(out.contains("onmouseover"), out)
    assertFalse(out.contains("onclick"), out)
  }

  @Test
  fun `ids are dropped by default and kept on request`() {
    val html = "<body><div id='main'><p>Text</p></div></body>"
    assertFalse(scrub(html).contains("id=\"main\""))
    val kept = HtmlSimplifier.scrubHtml(html, keepObjectIds = true, treeIndexLinks = false)
    assertTrue(kept.contains("id=\"main\""), kept)
  }

  @Test
  fun `javascript hrefs are stripped`() {
    val out = scrub("<body><a href=\"javascript:evil()\">Click</a></body>")
    assertFalse(out.contains("javascript:"), out)
  }

  @Test
  fun `relative urls are absolutized with a base url`() {
    val out = HtmlSimplifier.scrubHtml(
      "<body><a href='/page'>Link</a></body>",
      baseUrl = "https://example.com",
      treeIndexLinks = false
    )
    assertTrue(out.contains("https://example.com/page"), out)
  }

  @Test
  fun `empty elements are removed`() {
    val out = scrub("<body><div></div><p>Text</p></body>")
    assertFalse(out.contains("<div"), out)
    assertTrue(out.contains("Text"), out)
  }

  @Test
  fun `empty anchors are removed`() {
    val out = scrub("<body><a href='https://example.com'></a><p>Text</p></body>")
    assertFalse(out.contains("<a"), out)
    assertTrue(out.contains("Text"), out)
  }

  @Test
  fun `table structure is preserved`() {
    val out = scrub("<body><table><tr><td>A</td><td>B</td></tr></table></body>")
    assertTrue(out.contains("<table"), out)
    assertTrue(out.contains("<td>"), out)
    assertTrue(out.contains("A"), out)
  }

  @Test
  fun `nested wrappers are collapsed`() {
    val out = HtmlSimplifier.scrubHtml(
      "<body><div><div><div><p>Deep</p></div></div></div></body>",
      treeIndexLinks = false
    )
    assertTrue(out.contains("Deep"), out)
    assertFalse(out.contains("<div"), out)
  }

  @Test
  fun `link summarization emits an index header`() {
    val out = HtmlSimplifier.scrubHtml(
      "<body><p><a href='https://example.com/a'>A</a> and <a href='https://example.com/b'>B</a></p></body>",
      summarizeLinks = true,
      treeIndexLinks = false
    )
    assertTrue(out.startsWith("Links:"), out)
    assertTrue(out.contains("[1] https://example.com/a"), out)
    assertTrue(out.contains("[2] https://example.com/b"), out)
    assertTrue(out.contains("A [1]"), out)
    assertFalse(out.contains("<a "), out)
  }

  @Test
  fun `duplicate links share a single index`() {
    val out = HtmlSimplifier.scrubHtml(
      "<body><p><a href='https://example.com/a'>A</a><a href='https://example.com/a'>A2</a></p></body>",
      summarizeLinks = true,
      treeIndexLinks = false
    )
    assertTrue(out.contains("[1] https://example.com/a"), out)
    assertFalse(out.contains("[2]"), out)
  }

  @Test
  fun `tree index emits single-line json and inline markers`() {
    val out = HtmlSimplifier.scrubHtml(
      "<body><p><a href='https://example.com/a'>A</a></p></body>",
      treeIndexLinks = true
    )
    assertTrue(out.startsWith("{"), out)
    assertTrue(out.contains("\"https://example.com\""), out)
    assertTrue(out.contains("\"\$\":0"), out)
    assertTrue(out.contains("A {0}"), out)
  }

  @Test
  fun `tree index is omitted when there are no links`() {
    val out = HtmlSimplifier.scrubHtml("<body><p>Text</p></body>", treeIndexLinks = true)
    assertFalse(out.startsWith("{"), out)
    assertTrue(out.contains("Text"), out)
  }

  @Test
  fun `boilerplate link lists are collapsed`() {
    val links = (1..12).joinToString("") { "<li><a href='https://example.com/$it'>City $it</a></li>" }
    val out = HtmlSimplifier.scrubHtml("<body><ul>$links</ul><p>Content</p></body>", treeIndexLinks = false)
    assertTrue(out.contains("navigational links omitted"), out)
    assertTrue(out.contains("Content"), out)
  }

  @Test
  fun `link list collapsing can be disabled`() {
    val links = (1..12).joinToString("") { "<li><a href='https://example.com/$it'>City $it</a></li>" }
    val out = HtmlSimplifier.scrubHtml(
      "<body><ul>$links</ul></body>",
      collapseLinkLists = false,
      treeIndexLinks = false
    )
    assertFalse(out.contains("navigational links omitted"), out)
    assertTrue(out.contains("City 1"), out)
  }

  @Test
  fun `inflated inline leaves are collapsed`() {
    val spans = (1..8).joinToString("") { "<span>$it</span>" }
    val out = HtmlSimplifier.scrubHtml("<body><p>$spans</p></body>", treeIndexLinks = false)
    assertFalse(out.contains("<span"), out)
    assertTrue(out.contains("1"), out)
    assertTrue(out.contains("8"), out)
  }

  @Test
  fun `inline leaf collapsing can be disabled`() {
    val spans = (1..8).joinToString("") { "<span>$it</span>" }
    val out = HtmlSimplifier.scrubHtml(
      "<body><p>$spans</p></body>",
      collapseInlineLeaves = false,
      treeIndexLinks = false
    )
    assertTrue(out.contains("<span"), out)
  }

  @Test
  fun `malformed html does not throw`() {
    val out = scrub("<body><p>Unclosed <b>bold</body>")
    assertTrue(out.contains("Unclosed"), out)
  }

  @Test
  fun `full document is returned when structure simplification is disabled`() {
    val out = HtmlSimplifier.scrubHtml(
      "<html><body><p>Text</p></body></html>",
      simplifyStructure = false,
      treeIndexLinks = false
    )
    assertTrue(out.contains("<html>"), out)
    assertTrue(out.contains("Text"), out)
  }
}