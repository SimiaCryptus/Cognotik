package com.simiacryptus.cognotik.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory.getLogger
import kotlin.collections.iterator
import kotlin.text.iterator

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
        summarizeLinks: Boolean = false,
        collapseInlineLeaves: Boolean = true,
        treeIndexLinks: Boolean = true
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
            log.warn(
                "Failed to simplify HTML in step='${stepName}' (${e.javaClass.simpleName}): ${e.message}",
                e
            )
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
                    && element.childNodes().first().nodeName() == "#text" && element.attributes().isEmpty()
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
                val nodesToRemove = mutableListOf<TextNode>()
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
        if (collapseInlineLeaves) {
            simplifyDocument(stepName = "CollapseInlineLeafElements") {
                collapseInlineLeafElements(this)
            }
        }


        simplifyDocument(stepName = "SimplifyNestedStructure") {
            if (simplifyStructure) {
                // Iterate: each pass may expose newly-empty or newly-redundant wrappers.
                var outerChanged = true
                var passes = 0
                var totalUnwraps = 0
                while (outerChanged && passes < 5) {
                    outerChanged = false
                    passes++

                    // Pass A: Unwrap parent elements that have no attributes and contain a single child element.
                    var changed = true
                    while (changed) {
                        changed = false
                        val rawCandidates = select(STRUCTURAL_WRAPPER_ELEMENTS.joinToString(", "))
                        // DIAGNOSTIC: explain why candidates are/aren't accepted. Prior fixes
                        // failed because rejection reasons were invisible.
                        fun rejectionReason(element: Element): String? = when {
                            element.tagName() in setOf("html", "head", "body") -> "tag-is-document-root(${element.tagName()})"
                            element.tagName() !in STRUCTURAL_WRAPPER_ELEMENTS -> "tag-not-wrapper(${element.tagName()})"
                            element.meaningfulAttributeCount() != 0 -> "has-attributes(${element.attributes().size()}:${element.attributes().asList().joinToString { it.key }})"
                            element.children().size != 1 -> "child-element-count=${element.children().size}"
                            element.parent() == null -> "no-parent"
                            !element.textNodes().all { it.text().isBlank() } -> "has-nonblank-text-nodes(${element.textNodes().filter { it.text().isNotBlank() }.joinToString("|") { "'${it.text().take(20)}'" }})"
                            else -> null
                        }
                        val candidate = rawCandidates.firstOrNull { rejectionReason(it) == null }
                        if (candidate == null && rawCandidates.isNotEmpty()) {
                            // Log per-clause rejection for the first few candidates so the
                            // exact failing predicate is visible in test output.
                            rawCandidates.take(3).forEachIndexed { i, el ->
                                log.debug(
                                    "SimplifyNestedStructure pass=$passes rejected candidate[$i] <${el.tagName()}>: " +
                                            "reason=${rejectionReason(el)} " +
                                            "(attrs=${el.attributes().size()}, childEls=${el.children().size}, " +
                                            "childNodes=${el.childNodes().size}, parent=${el.parent()?.tagName()}, " +
                                            "textNodeCount=${el.textNodes().size})"
                                )
                            }
                            log.debug("SimplifyNestedStructure pass=$passes: ${rawCandidates.size} raw wrapper candidates, none accepted")
                        }
                        if (candidate != null) {
                            // Replace the wrapper with its single child element directly.
                            // unwrap() can leave behind whitespace text nodes that cause the
                            // same wrapper to be re-selected (it then has a text node + element
                            // child and no longer matches), stalling progress. Explicitly
                            // promoting the single child element is more deterministic.
                            val tag = candidate.tagName()
                            candidate.children().first()?.apply {
                                remove();
                                candidate.replaceWith(this)
                                totalUnwraps++
                                log.debug("SimplifyNestedStructure pass=$passes unwrapped <$tag> (total=$totalUnwraps)")
                                changed = true
                                outerChanged = true
                            }
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
                log.debug("SimplifyNestedStructure completed: passes=$passes totalUnwraps=$totalUnwraps")
            }
        }
        val linkIndex: List<String> = if (summarizeLinks) {
            summarizeLinksInDocument(document)
        } else {
            emptyList()
        }
        val treeIndexJson: String? = if (treeIndexLinks) {
            buildLinkTreeIndex(document)
        } else {
            null
        }
        val bodyHtml = if (simplifyStructure) {
            document.body().html()
        } else {
            document.html()
        }
        return when {
            treeIndexLinks && treeIndexJson != null -> buildString {
                append(treeIndexJson)
                append('\n')
                append('\n')
                append(bodyHtml)
            }

            summarizeLinks && linkIndex.isNotEmpty() -> buildString {
                append("Links:\n")
                linkIndex.forEachIndexed { idx, url ->
                    append("[").append(idx + 1).append("] ").append(url).append('\n')
                }
                append('\n')
                append(bodyHtml)
            }

            else -> bodyHtml
        }
    }

    private fun shouldPreserveElement(tagName: String): Boolean =
        (tagName in PRESERVED_ELEMENTS || tagName in setOf("html", "head", "body"))
    /**
     * Counts attributes that carry a non-blank key. JSoup can surface phantom
     * attributes with empty keys (e.g. from stray whitespace produced during
     * earlier mutation/pretty-printing passes); these have no semantic value and
     * must be ignored when deciding whether an element is "attribute-free".
     */
    private fun Element.meaningfulAttributeCount(): Int =
        attributes().asList().count { it.key.isNotBlank() }

    /**
     * Builds a tree-based, substring-deduplicated index of all links in the document.
     *
     * URLs are decomposed into hierarchical components: the origin (scheme + authority)
     * forms the top-level key, and each path segment becomes a nested key. Each unique
     * URL is assigned a short integer id at its leaf position. The resulting tree is
     * emitted as single-line JSON and prepended to the output, while each anchor in the
     * body is replaced by a compact `text {id}` marker.
     *
     * Tree shape (single-line JSON):
     *   {"https://example.com":{"/":{"$":0},"a":{"$":1},"b":{"x":{"$":2}}}}
     * where the `$` key holds the id assigned to the URL terminating at that node.
     *
     * Reconstruction: walk from the origin root down the path keys, concatenating the
     * origin with each segment (joined by '/') to recover the full URL for a given id.
     */
    private fun buildLinkTreeIndex(document: Document): String? {
        val anchors = document.select("a[href]")
        if (anchors.isEmpty()) return null
        // Mutable tree node: child segment -> node; plus an optional terminal id.
        class Node {
            val children = LinkedHashMap<String, Node>()
            var id: Int? = null
        }

        val root = Node()
        val urlToId = LinkedHashMap<String, Int>()
        var nextId = 0
        fun originAndSegments(href: String): Pair<String, List<String>>? {
            val trimmed = href.trim()
            if (trimmed.isBlank()) return null
            // Split off the origin (scheme://authority) from the remainder.
            val schemeIdx = trimmed.indexOf("://")
            return if (schemeIdx >= 0) {
                val afterScheme = trimmed.indexOf('/', schemeIdx + 3)
                if (afterScheme < 0) {
                    // No path component.
                    Pair(trimmed, emptyList())
                } else {
                    val origin = trimmed.substring(0, afterScheme)
                    val rest = trimmed.substring(afterScheme)
                    Pair(origin, splitPath(rest))
                }
            } else {
                // Relative or scheme-less URL: treat first path char as origin boundary.
                Pair("", splitPath(trimmed))
            }
        }
        // Assign ids and build the tree.
        anchors.forEach { anchor ->
            val href = anchor.attr("href").trim()
            if (href.isBlank()) return@forEach
            val id = urlToId.getOrPut(href) {
                val assigned = nextId++
                val (origin, segments) = originAndSegments(href) ?: Pair(href, emptyList())
                var node = root.children.getOrPut(origin) { Node() }
                if (segments.isEmpty()) {
                    node.id = assigned
                } else {
                    for (seg in segments) {
                        node = node.children.getOrPut(seg) { Node() }
                    }
                    node.id = assigned
                }
                assigned
            }
            val text = anchor.text().trim()
            val replacement = if (text.isNotEmpty()) "$text {$id}" else "{$id}"
            anchor.replaceWith(TextNode(replacement))
        }
        if (urlToId.isEmpty()) return null
        // Serialize the tree to compact single-line JSON.
        fun jsonEscape(s: String): String = buildString {
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }

        fun serialize(node: Node): String = buildString {
            append('{')
            var first = true
            node.id?.let {
                append("\"\$\":").append(it)
                first = false
            }
            for ((key, child) in node.children) {
                if (!first) append(',')
                append('"').append(jsonEscape(key)).append("\":")
                append(serialize(child))
                first = false
            }
            append('}')
        }
        return serialize(root)
    }

    /** Splits a URL path (which may include a leading '/') into non-empty segments. */
    private fun splitPath(path: String): List<String> =
        path.split('/').filter { it.isNotBlank() }

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
            val textNode = TextNode(replacement)
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

    /** Inline elements commonly used purely for styling/wrapping individual tokens. */
    private val INLINE_LEAF_ELEMENTS = setOf(
        "span", "b", "i", "em", "strong", "u", "small", "mark", "sub", "sup",
        "font", "tt", "code", "ins", "del", "abbr", "cite", "q", "var", "kbd", "samp"
    )

    /**
     * Collapses "inflated" markup where a parent contains many sibling inline leaf
     * elements (e.g. `<span>1</span><span>2</span>...`) that each wrap only a short
     * text node and carry no meaningful attributes. Such patterns are produced by
     * syntax highlighters, line-number renderers, and per-character animation libraries,
     * and contribute massive token bloat with no semantic value.
     *
     * Each qualifying leaf is unwrapped (replaced by its text). A parent qualifies when
     * a strong majority of its element children are short, attribute-free inline leaves.
     */
    private fun collapseInlineLeafElements(
        document: Document,
        minLeaves: Int = 5,
        leafRatio: Double = 0.8,
        shortLeafMaxChars: Int = 32
    ) {
        fun isInlineLeaf(element: Element): Boolean {
            if (element.tagName() !in INLINE_LEAF_ELEMENTS) return false
            if (element.meaningfulAttributeCount() != 0) return false
            // Leaf = no child elements, only a (short) text node or empty.
            if (element.children().isNotEmpty()) return false
            return element.text().trim().length <= shortLeafMaxChars
        }
        // DIAGNOSTIC helper: explain why a given element is not an inline leaf.
        fun leafRejectionReason(element: Element): String? = when {
            element.tagName() !in INLINE_LEAF_ELEMENTS -> "tag-not-inline-leaf(${element.tagName()})"
            element.meaningfulAttributeCount() != 0 -> "has-attributes(${element.attributes().asList().joinToString { it.key }})"
            element.children().isNotEmpty() -> "has-child-elements(${element.children().size})"
            element.text().trim().length > shortLeafMaxChars -> "text-too-long(${element.text().trim().length})"
            else -> null
        }
        // Process deepest parents first so nested inflation collapses inside-out.
        val parents = document.select("*")
            .sortedByDescending { it.parents().size }
            .toList()
        var collapsedParents = 0
        var collapsedLeaves = 0
        var inspectedParents = 0
        var diagnosedParents = 0
        for (parent in parents) {
            // The captured parent may have been detached/altered by an earlier
            // collapse in this same pass; skip anything no longer in the document.
            if (parent.ownerDocument() == null) {
                log.debug("collapseInlineLeafElements: skipping <${parent.tagName()}> (detached: ownerDocument==null)")
                continue
            }
            val childEls = parent.children()
            if (childEls.size < minLeaves) continue
            inspectedParents++
            val leaves = childEls.toList().filter { isInlineLeaf(it) }
            if (leaves.size < minLeaves) {
                // This parent had enough children but not enough qualifying leaves.
                // Surface the rejection reasons for the first few non-leaf children so
                // the failing predicate is visible (previous fixes were blind here).
                if (diagnosedParents < 5) {
                    diagnosedParents++
                    val reasons = childEls.toList().take(5).map { it.tagName() to leafRejectionReason(it) }
                    log.debug(
                        "collapseInlineLeafElements: <${parent.tagName()}> has ${childEls.size} child els " +
                                "but only ${leaves.size} qualify as inline leaves (need $minLeaves). " +
                                "Sample child evaluations: ${reasons.joinToString { "${it.first}=${it.second ?: "OK"}" }}"
                    )
                }
                continue
            }
            val ratio = leaves.size.toDouble() / childEls.size.coerceAtLeast(1)
            if (ratio < leafRatio) {
                log.debug(
                    "collapseInlineLeafElements: <${parent.tagName()}> leaf ratio $ratio " +
                            "(${leaves.size}/${childEls.size}) below threshold $leafRatio"
                )
                continue
            }


            // Rebuild the parent's child nodes from scratch rather than mutating in
            // place. In-place replaceWith on jsoup nodes is fragile when many siblings
            // are replaced in one pass (sibling indices drift, producing
            // IndexOutOfBoundsException). Snapshot the current child nodes, convert
            // qualifying inline leaves to text, then re-attach everything.
            try {
                val originalNodes = parent.childNodes().toList()
                var localCollapsed = 0
                val replacements = originalNodes.map { node ->
                    if (node is Element && isInlineLeaf(node)) {
                        localCollapsed++
                        TextNode(node.text()) as Node
                    } else {
                        node
                    }
                }
                // Detach all existing children, then re-append the (possibly rewritten) set.
                originalNodes.forEach { it.remove() }
                replacements.forEach { parent.appendChild(it) }
                collapsedLeaves += localCollapsed
                collapsedParents++
            } catch (e: Exception) {
                log.warn(
                    "Skipped inline-leaf collapse for <${parent.tagName()}> " +
                            "(${e.javaClass.simpleName}): ${e.message}"
                )
            }
        }
        log.debug(
            "collapseInlineLeafElements summary: totalParents=${parents.size}, " +
                    "inspected(childEls>=$minLeaves)=$inspectedParents, " +
                    "collapsedParents=$collapsedParents, collapsedLeaves=$collapsedLeaves"
        )
        if (collapsedParents > 0) {
            log.debug("collapseInlineLeafElements: collapsed $collapsedLeaves leaves across $collapsedParents parents")
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