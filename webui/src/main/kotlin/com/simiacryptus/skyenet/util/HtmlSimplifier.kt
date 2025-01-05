package com.simiacryptus.skyenet.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities

object HtmlSimplifier {
  private val log = org.slf4j.LoggerFactory.getLogger(HtmlSimplifier::class.java)

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
    "p", "div", "span", "table", "tr", "td", "th", "thead", "tbody", "tfoot", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6", "br", "hr", "img"
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
    "aria-label",
    "aria-describedby",
    "role"
  )
  private val SCRIPT_ATTRIBUTES = setOf(
    "onclick", "onload", "onsubmit", "oninput", "onchange"
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
    // Add input validation for baseUrl
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
      log.info("Simplified HTML in ${stepName} from ${prevDocSize} to $newLength in ${endTime - startTime}ms")
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
      select("[data-*]").forEach { it.attributes().removeAll { attr -> attr.key.startsWith("data-") } }
    }

    simplifyDocument(stepName = "RemoveEventHandlers") {
      if (!keepEventHandlers) {
        select("*").forEach { element ->
          element.attributes().removeAll { attr ->
            // Simplified condition and fixed logic error
            attr.key.lowercase().startsWith("on") && attr.key !in SCRIPT_ATTRIBUTES
          }
        }
      }
    }

    simplifyDocument(stepName = "RemoveUnsafeAttributes") {
      select("*").forEach { element ->
        element.attributes().forEach { attr ->
          if (!keepScriptElements && (attr.value.contains("javascript:") || attr.value.contains("data:") || attr.value.contains("vbscript:") || attr.value.contains(
              "file:"
            ))
          ) {
            element.removeAttr(attr.key)
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
        element.attributes().removeAll { attr -> attr.key !in importantAttributes }
      }
    }

    simplifyDocument(stepName = "RemoveEmptyElements") {
      select("*:not(img)").forEach { element ->
        if (element.text().isBlank() && 
            element.attributes().isEmpty && 
            !element.select("img, br, hr, iframe[src], svg, source[src], track[src]").any() // Added more media elements
        ) {
          element.remove()
        }
      }
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
        if (element.tagName() !in PRESERVED_ELEMENTS && element.childNodes().size == 1
          && element.childNodes().first()?.nodeName() == "#text" && element.attributes().isEmpty()
        ) {
          element.unwrap()
        }
      }
    }

    simplifyDocument(stepName = "ConvertRelativeUrls") {
      if (baseUrl != null) {
        // Add more elements that might have URLs
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
        element.attributes().removeAll { attr ->
          attr.value.isBlank() || attr.value == "null" || attr.value.contains("javascript:") || attr.value.contains("data:")
        }
      }
    }

    simplifyDocument(stepName = "CleanupTextNodes") {
      select("*").forEach { element ->
        element.textNodes().forEach { node ->
          val trimmed = if (preserveWhitespace) node.text() else node.text().trim()
          if (trimmed.isBlank()) node.remove()
          else node.text(trimmed)
        }
      }
    }

    simplifyDocument(stepName = "SimplifyNestedStructure") {
      while (simplifyStructure) select("*").filter { element -> (element.attributes().isEmpty && element.children().size == 1) }.filter { element ->
          val child = element.children().first() ?: return@filter false
          when {
            !child.attributes().isEmpty -> false
            child.tagName() != element.tagName() -> false
            child.children().size > 1 -> false
            else -> true
          }
        }.firstOrNull()?.unwrap() ?: break
    }

    return document.body().html() ?: ""
  }
}