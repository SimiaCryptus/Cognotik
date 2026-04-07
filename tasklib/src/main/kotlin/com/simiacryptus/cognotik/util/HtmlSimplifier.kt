package com.simiacryptus.cognotik.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities

object HtmlSimplifier {
  private val log = LoggerFactory.getLogger(HtmlSimplifier::class.java)

  /** Elements that can execute scripts or load external content */
  private val SCRIPT_ELEMENTS = setOf(
    "script", "noscript", "iframe"
  )

  /** Elements that handle user input */
  private val INTERACTIVE_ELEMENTS = setOf(
    "form", "input", "textarea", "button", "select", "option"
  )

  /** Elements that load or display media content */
  private val MEDIA_ELEMENTS = setOf(
    "canvas", "audio", "video", "source", "track", "picture"
  )
  private val PRESERVED_ELEMENTS = setOf(
    "p",
    "div",
    "span",
    "table",
    "tr",
    "td",
    "th",
    "thead",
    "tbody",
    "tfoot",
    "ul",
    "ol",
    "li",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "br",
    "hr",
    "img"
  )
  private val DEFAULT_IMPORTANT_ATTRIBUTES = setOf(
    "href",
    "src",
    "alt",
    "title",
    "style",
    "class",
    "name",
    "rel",
    "type",
    "content",
    "colspan",
    "rowspan",
    "scope",
    "id",
    "lang",
    "action",
    "method",
    "value",
    "placeholder",
    "aria-label",
    "aria-describedby",
    "role"
  )
  private val SCRIPT_ATTRIBUTES = setOf(
    "onclick", "onload", "onsubmit", "oninput", "onchange"
  )

  private val STRUCTURAL_WRAPPER_ELEMENTS = setOf(
    "div", "span", "section", "article", "aside", "main", "header", "footer", "nav", "figure", "figcaption"
  )

  fun scrubHtml(
    str: String,
    baseUrl: String? = null,
    includeCssData: Boolean = false,
    simplifyStructure: Boolean = true,
    keepObjectIds: Boolean = false,
    preserveWhitespace: Boolean = false,
    keepScriptElements: Boolean = false,
    keepInteractiveElements: Boolean = false,
    keepMediaElements: Boolean = false,
    keepEventHandlers: Boolean = false
  ): String {

    baseUrl?.let {
      require(!it.startsWith("javascript:") && !it.startsWith("data:")) { "Invalid base URL scheme" }
    }
    require(str.isNotBlank()) { "Input HTML cannot be blank" }
    require(!str.startsWith("data:")) { "Data URLs are not supported" }
    require(!str.startsWith("javascript:")) { "JavaScript URLs are not supported" }

    val document: Document = try {
      if (null != baseUrl) Jsoup.parse(str, baseUrl) else Jsoup.parse(str)
    } catch (e: Exception) {
      throw IllegalArgumentException("Failed to parse HTML: ${e.message}", e)
    }

    fun simplifyDocument(stepName: String = "", fn: Document.() -> Unit) = try {
      val prevDocSize = document.html().length
      val startTime = System.currentTimeMillis()
      document.fn()
      val endTime = System.currentTimeMillis()
      val newLength = document.html().length
      log.debug("Simplified HTML in ${stepName} from ${prevDocSize} to $newLength in ${endTime - startTime}ms")
    } catch (e: Exception) {
      log.warn("Failed to simplify HTML in ${stepName}: ${e.message}", e)
    }

    simplifyDocument(stepName = "Setup") {
      outputSettings().prettyPrint(true)
      outputSettings().charset("UTF-8")
      outputSettings().escapeMode(Entities.EscapeMode.xhtml)
      outputSettings().syntax(Document.OutputSettings.Syntax.html)
    }

    simplifyDocument(stepName = "RemoveUnsafeElements") {
      val elementsToRemove = mutableListOf<String>()
      elementsToRemove.addAll(
        listOf(
          "link", "meta", "object", "embed", "applet", "base", "frame", "frameset", "marquee", "blink"
        )
      )
      if (!keepScriptElements) elementsToRemove.addAll(SCRIPT_ELEMENTS)
      if (!keepInteractiveElements) elementsToRemove.addAll(INTERACTIVE_ELEMENTS)
      if (!keepMediaElements) elementsToRemove.addAll(MEDIA_ELEMENTS)
      if (!includeCssData) elementsToRemove.add("style")
      select(
        elementsToRemove.joinToString(", ")
      ).remove()
    }

    simplifyDocument(stepName = "RemoveDataAttributes") {
      select("[data-*]").forEach { element ->
        val iterator = element.attributes().iterator()
        while (iterator.hasNext()) {
          val attr = iterator.next()
          if (attr.key.startsWith("data-")) {
            iterator.remove()
          }
        }
      }
    }

    simplifyDocument(stepName = "RemoveEventHandlers") {
      if (!keepEventHandlers) {
        select("*").forEach { element ->

          val iterator = element.attributes().iterator()
          while (iterator.hasNext()) {
            val attr = iterator.next()
            if (attr.key.lowercase().startsWith("on") && attr.key !in SCRIPT_ATTRIBUTES) {
              iterator.remove()
            }
          }
        }
      }
    }

    simplifyDocument(stepName = "RemoveUnsafeAttributes") {
      select("*").forEach { element ->
        val iterator = element.attributes().iterator()
        while (iterator.hasNext()) {
          val attr = iterator.next()
          if (!keepScriptElements && (attr.value.contains("javascript:") ||
                attr.value.contains("data:") ||
                attr.value.contains("vbscript:") ||
                attr.value.contains("file:"))
          ) {
            iterator.remove()
          }
        }
      }
    }

    simplifyDocument(stepName = "FilterAttributes") {
      val importantAttributes = DEFAULT_IMPORTANT_ATTRIBUTES.let { baseSet ->
        when {
          includeCssData -> baseSet
          keepObjectIds -> baseSet - setOf("style", "class", "width", "height", "target")
          else -> baseSet - setOf("style", "class", "id", "width", "height", "target")
        }
      }.toSet()
      select("*").forEach { element ->
        val iterator = element.attributes().iterator()
        while (iterator.hasNext()) {
          val attr = iterator.next()
          if (attr.key !in importantAttributes) {
            iterator.remove()
          }
        }
      }
    }

    simplifyDocument(stepName = "RemoveEmptyElements") {
      val elementsToRemove = mutableListOf<org.jsoup.nodes.Element>()
      val excludeSelector = buildList {
        add("img")
        add("br")
        add("hr")
        add("html")
        add("head")
        add("body")
        if (keepScriptElements) addAll(SCRIPT_ELEMENTS)
        if (keepMediaElements) addAll(MEDIA_ELEMENTS)
        if (keepInteractiveElements) addAll(INTERACTIVE_ELEMENTS)
      }.joinToString(", ") { ":not($it)" }
      select("*$excludeSelector").forEach { element ->
        if (element.text().isBlank() &&
          element.attributes().isEmpty &&
          !element.select(
            buildList {
              addAll(listOf("img", "br", "hr", "iframe[src]", "svg", "source[src]", "track[src]"))
              if (keepInteractiveElements) addAll(INTERACTIVE_ELEMENTS)
              if (keepMediaElements) addAll(MEDIA_ELEMENTS)
            }.joinToString(", ")
          ).any() &&
          !(keepScriptElements && element.tagName() in SCRIPT_ELEMENTS && element.data().isNotBlank())
          && !(keepInteractiveElements && element.tagName() in INTERACTIVE_ELEMENTS)

        ) {
          elementsToRemove.add(element)
        }
      }
      elementsToRemove.forEach { it.remove() }
    }

    simplifyDocument(stepName = "CleanupHrefAttributes") {
      select("a[href]").forEach { element ->
        val href = element.attr("href")
        if (href.startsWith("javascript:") || href.startsWith("data:")) {
          element.removeAttr("href")
        }
      }
    }

    simplifyDocument(stepName = "UnwrapSimpleTextElements") {
      select("*").forEach { element ->
        if (element.tagName() !in PRESERVED_ELEMENTS
          && element.tagName() !in setOf("html", "head", "body")
          && element.childNodes().size == 1
          && element.childNodes().first()?.nodeName() == "#text" && element.attributes().isEmpty()
        ) {
          element.unwrap()
        }
      }
    }

    simplifyDocument(stepName = "ConvertRelativeUrls") {
      if (baseUrl != null) {

        select("a[href]").forEach {
          it.attr("href", it.absUrl("href"))
        }
        select("img[src]").forEach {
          it.attr("src", it.absUrl("src"))
        }
        select("source[src]").forEach {
          it.attr("src", it.absUrl("src"))
        }
        select("track[src]").forEach {
          it.attr("src", it.absUrl("src"))
        }
      }
    }

    simplifyDocument(stepName = "RemoveInvalidAttributes") {
      select("*").forEach { element ->
        val iterator = element.attributes().iterator()
        while (iterator.hasNext()) {
          val attr = iterator.next()
          if (attr.value.isBlank() || attr.value == "null" ||
            attr.value.contains("javascript:") || attr.value.contains("data:")
          ) {
            iterator.remove()
          }
        }
      }
    }

    simplifyDocument(stepName = "CleanupTextNodes") {
      select("*").forEach { element ->
        val nodesToRemove = mutableListOf<org.jsoup.nodes.TextNode>()
        element.textNodes().forEach { node ->
          val trimmed = if (preserveWhitespace) node.text() else node.text().trim()
          if (trimmed.isBlank()) {
            nodesToRemove.add(node)
          } else {
            node.text(trimmed)
          }
        }
      }
    }

    simplifyDocument(stepName = "SimplifyNestedStructure") {
      if (simplifyStructure) {
        // Pass 1: Unwrap parent elements that have no attributes and contain a single child element.
        // This collapses unnecessary wrapper divs like <div><div><p>text</p></div></div>
        var changed = true
        while (changed) {
          changed = false
          val candidates = select("*").filter { element ->
            element.tagName() !in setOf("html", "head", "body") &&
              element.tagName() in STRUCTURAL_WRAPPER_ELEMENTS &&
            element.attributes().isEmpty &&
            element.children().size == 1 &&
            element.textNodes().all { it.text().isBlank() }
          }
          for (element in candidates) {
            // Safety check: element must still be in the document
            if (element.parent() == null) continue
            element.unwrap()
            changed = true
          }
        }
      }
    }

    return document.body().html() ?: ""
  }
}