package com.simiacryptus.cognotik.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.slf4j.LoggerFactory.getLogger

object HtmlSimplifier {
  private val log = getLogger(HtmlSimplifier::class.java)

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
   /** Navigational / chrome elements that are usually boilerplate for content extraction */
   private val NAVIGATIONAL_ELEMENTS = setOf(
     "nav", "header", "footer", "aside"
   )
   /** Inert UI scaffolding elements whose contents are only activated by JS */
   private val INERT_TEMPLATE_ELEMENTS = setOf(
     "template", "slot"
   )
   /** ARIA roles that indicate transient/decorative UI rather than content */
   private val TRANSIENT_UI_ROLES = setOf(
     "tooltip", "alert", "status", "dialog", "alertdialog", "menu", "menubar", "presentation", "none"
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
     keepEventHandlers: Boolean = false,
     removeNavigationalElements: Boolean = true,
     removeTemplateElements: Boolean = true,
     removeTransientUiElements: Boolean = true,
      collapseLinkLists: Boolean = true,
      summarizeLinks: Boolean = false
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
       if (removeTemplateElements) elementsToRemove.addAll(INERT_TEMPLATE_ELEMENTS)
       if (removeNavigationalElements) elementsToRemove.addAll(NAVIGATIONAL_ELEMENTS)
      select(
        elementsToRemove.joinToString(", ")
      ).remove()
       if (removeTransientUiElements) {
         TRANSIENT_UI_ROLES.forEach { role ->
           select("[role=$role]").remove()
         }
       }
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
      val importantAttributes = getImportantAttributes(keepObjectIds, includeCssData)
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
       removeEmptyElements(
         this,
         keepScriptElements = keepScriptElements,
         keepMediaElements = keepMediaElements,
         keepInteractiveElements = keepInteractiveElements
       )
     }

     simplifyDocument(stepName = "RemoveEmptyAnchors") {
       // Anchors with no text and no content children (e.g., decorative overlay links)
       // are common in modern card-style layouts and add noise.
       select("a").forEach { element ->
         val hasText = element.text().isNotBlank()
         val hasMeaningfulChild = element.select("img[src], svg, video, audio, picture").any()
         if (!hasText && !hasMeaningfulChild) {
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
        if (!shouldPreserveElement(element.tagName())
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
     if (collapseLinkLists) {
       simplifyDocument(stepName = "CollapseBoilerplateLinkLists") {
         collapseBoilerplateLinkLists(this)
       }
     }


    simplifyDocument(stepName = "SimplifyNestedStructure") {
      if (simplifyStructure) {
         // Iterate: each pass may expose newly-empty or newly-redundant wrappers.
         var outerChanged = true
         var passes = 0
         while (outerChanged && passes < 5) {
           outerChanged = false
           passes++

           // Pass A: Unwrap parent elements that have no attributes and contain a single child element.
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
               if (element.parent() == null) continue
               element.unwrap()
               changed = true
               outerChanged = true
             }
           }

           // Pass B: Re-remove empty elements that may have been produced by unwrapping.
           val sizeBefore = this.html().length
           removeEmptyElements(
             this,
             keepScriptElements = keepScriptElements,
             keepMediaElements = keepMediaElements,
             keepInteractiveElements = keepInteractiveElements
           )
           if (this.html().length != sizeBefore) outerChanged = true
         }
      }
    }
     val linkIndex: List<String> = if (summarizeLinks) {
       summarizeLinksInDocument(document)
     } else {
       emptyList()
     }
     val bodyHtml = if (simplifyStructure) {
      document.body().html() ?: ""
    } else {
      document.html() ?: ""
    }
     return if (summarizeLinks && linkIndex.isNotEmpty()) {
       buildString {
         append("Links:\n")
         linkIndex.forEachIndexed { idx, url ->
           append("[").append(idx + 1).append("] ").append(url).append('\n')
         }
         append('\n')
         append(bodyHtml)
       }
     } else {
       bodyHtml
     }
  }

  private fun shouldPreserveElement(tagName: String): Boolean =
    (tagName in PRESERVED_ELEMENTS || tagName in setOf("html", "head", "body"))
    /**
     * Walks the document, collects unique href targets in document order, and replaces each
     * anchor's href with a compact `[n]` index reference. Anchors are converted to plain
     * inline markers `text [n]` to maximize compactness for LLM consumption (programmatic
     * link validity is not preserved). Returns the ordered list of URLs for header emission.
     */
    private fun summarizeLinksInDocument(document: Document): List<String> {
      val urls = mutableListOf<String>()
      val urlToIndex = mutableMapOf<String, Int>()
      document.select("a[href]").forEach { anchor ->
        val href = anchor.attr("href").trim()
        if (href.isBlank()) {
          anchor.unwrap()
          return@forEach
        }
        val idx = urlToIndex.getOrPut(href) {
          urls.add(href)
          urls.size
        }
        val text = anchor.text().trim()
        val replacement = if (text.isNotEmpty()) "$text [${idx}]" else "[${idx}]"
        // Replace the entire anchor with a text node containing the marker so downstream
        // consumers see a flat, compact reference rather than nested markup.
        val textNode = org.jsoup.nodes.TextNode(replacement)
        anchor.replaceWith(textNode)
      }
      return urls
    }

   /**
    * Removes elements that are effectively empty: no visible text, no attributes, and
    * no meaningful descendant content (images, line breaks, media, etc.).
    */
   private fun removeEmptyElements(
     document: Document,
     keepScriptElements: Boolean,
     keepMediaElements: Boolean,
     keepInteractiveElements: Boolean
   ) {
     val elementsToRemove = mutableListOf<Element>()
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
     document.select("*$excludeSelector").forEach { element ->
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
   /**
    * Heuristically collapses long boilerplate lists of short links (e.g., city pickers,
    * "Popular Job Searches" footer grids). A container qualifies when:
    *   * It contains at least [minLinks] direct or near-direct anchor descendants.
    *   * Anchors dominate the textual content (>= [linkTextRatio] of non-whitespace text).
    *   * Most anchor texts are short (typical of category/tag clouds).
    *
    * Such blocks are replaced with a single placeholder summary line so the LLM still
    * knows they exist without paying their token cost.
    */
   private fun collapseBoilerplateLinkLists(
     document: Document,
     minLinks: Int = 8,
     linkTextRatio: Double = 0.7,
     shortLinkMaxChars: Int = 40
   ) {
     // Walk from deepest to shallowest to prefer collapsing tight clusters over whole pages.
     val candidates = document.select("ul, ol, div, section")
       .sortedByDescending { it.parents().size }
     val collapsed = mutableSetOf<Element>()
     for (element in candidates) {
       if (collapsed.any { it.isAncestorOf(element) || it == element }) continue
       if (element.parent() == null) continue
       val anchors = element.select("a[href]")
       if (anchors.size < minLinks) continue
       val totalText = element.text().trim()
       if (totalText.isBlank()) continue
       val anchorText = anchors.joinToString(" ") { it.text().trim() }.trim()
       val ratio = anchorText.length.toDouble() / totalText.length.coerceAtLeast(1)
       if (ratio < linkTextRatio) continue
       val shortLinkCount = anchors.count { it.text().trim().length <= shortLinkMaxChars }
       if (shortLinkCount.toDouble() / anchors.size < 0.8) continue
       // Replace with a compact placeholder noting the link count.
       val placeholder = document.createElement("p")
       placeholder.text("[${anchors.size} navigational links omitted]")
       element.replaceWith(placeholder)
       collapsed.add(placeholder)
     }
   }
   private fun Element.isAncestorOf(other: Element): Boolean {
     var p: Element? = other.parent()
     while (p != null) {
       if (p === this) return true
       p = p.parent()
     }
     return false
   }


  private fun getImportantAttributes(
    keepObjectIds: Boolean,
    includeCssData: Boolean
  ): MutableSet<String> {
    val importantAttributes = mutableSetOf(
      "href",
      "src",
      "alt",
      "title",
      "name",
      "rel",
      "type",
      "content",
      "colspan",
      "rowspan",
      "scope",
      "lang",
      "action",
      "method",
      "value",
      "placeholder",
      "aria-label",
      "aria-describedby",
      "role"
    )
    if (keepObjectIds) importantAttributes += setOf("id")
    if (includeCssData) importantAttributes += setOf("style", "class", "id", "width", "height", "target")
    return importantAttributes
  }
}