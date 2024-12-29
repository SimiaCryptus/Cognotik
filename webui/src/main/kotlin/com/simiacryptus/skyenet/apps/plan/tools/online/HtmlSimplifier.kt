package com.simiacryptus.skyenet.apps.plan.tools.online

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities

object HtmlSimplifier {

  fun scrubHtml(
    str: String,
    maxLength: Int = 100 * 1024,
    includeCssData: Boolean = false,
    baseUrl: String? = null
  ): String {
    val document: Document = if (null != baseUrl) Jsoup.parse(str, baseUrl) else Jsoup.parse(str)
    document.apply {
      outputSettings().prettyPrint(true) // Disable pretty printing for compact output
      outputSettings().charset("UTF-8")
      outputSettings().escapeMode(Entities.EscapeMode.xhtml)

      select("script, link, meta, iframe, noscript, object, embed, form, input, textarea, button").remove()
      if (!includeCssData) {
        select("style").remove()
      }

      // Remove data-* attributes
      select("*[data-*]").forEach { it.attributes().removeAll { attr -> attr.key.startsWith("data-") } }

      val importantAttributes = setOf("href", "src", "alt", "title", "style", "class", "name", "rel")
        .let { if (includeCssData) it else it - setOf("style", "class", "id", "width", "height", "target") }
      select("*").forEach { it.attributes().removeAll { it.key !in importantAttributes } }
      // Remove empty elements
      select("*:not(img)").forEach { element ->
        if (element.text().isBlank() &&
          element.attributes().isEmpty &&
          !element.children().any { it.tagName() == "img" }
        ) {
          element.remove()
        }
      }
      // Unwrap single-child elements with no attributes
      select("*").forEach { element ->
        if (element.tagName() !in setOf("p", "div", "span") &&
          element.childNodes().size == 1 &&
          element.childNodes()[0].nodeName() == "#text" &&
          element.attributes().isEmpty()
        ) {
          element.unwrap()
        }
      }
      // Convert relative URLs to absolute
      select("a[href]").forEach { element ->
        element.attr("href", element.absUrl("href"))
      }
      select("img[src]").forEach { element ->
        element.attr("src", element.absUrl("src"))
      }
      // Remove empty attributes
      select("*").forEach { element ->
        element.attributes().removeAll { it.value.isBlank() || it.value == "null" }
      }
      // Trim all text nodes
      select("*").forEach { element ->
        element.textNodes().forEach { node ->
          val trimmed = node.text().trim()
          if (trimmed.isBlank()) {
            node.remove()
          } else {
            node.text(trimmed)
          }
        }
      }
    }
    // Truncate if necessary
    val result = document.body().html() ?: ""
    return if (result.length > maxLength) {
      // Try to break at a tag boundary
      val breakPoint = result.lastIndexOf(">", maxLength)
      if (breakPoint > 0) result.substring(0, breakPoint + 1)
      else result.substring(0, maxLength)
    } else {
      result
    }
  }
}