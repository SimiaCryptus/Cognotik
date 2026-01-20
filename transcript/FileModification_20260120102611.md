# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/util/HtmlSimplifier.kt

```
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
            select("*:not(img)").forEach { element ->
                if (element.text().isBlank() &&
                    element.attributes().isEmpty &&
                    !element.select("img, br, hr, iframe[src], svg, source[src], track[src]")
                        .any()

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
                if (element.tagName() !in PRESERVED_ELEMENTS && element.childNodes().size == 1
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
            while (simplifyStructure) select("*").filter { element -> (element.attributes().isEmpty && element.children().size == 1) }
                .filter { element ->
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/util/ImagePatchLocalization.kt

```
package com.simiacryptus.cognotik.util

import java.awt.image.BufferedImage
import kotlin.math.*

object ImagePatchLocalization {
    data class SubImageBounds(val x: Int, val y: Int, val width: Int, val height: Int, val score: Double)

    fun findBounds(parentImage: BufferedImage, subImage: BufferedImage, estimate: SubImageBounds? = null): SubImageBounds {
        val maxDim = 200
        val globalScale = min(1.0, maxDim.toDouble() / max(parentImage.width, parentImage.height))

        val smallParent = normalize(resize(parentImage, globalScale))
        val smallSub = normalize(resize(subImage, globalScale))

        fun score(x: Double, y: Double, s: Double): Double {
            val w = (smallSub.width * s).toInt()
            val h = (smallSub.height * s).toInt()
            if (w <= 5 || h <= 5 || w > smallParent.width || h > smallParent.height) return Double.MAX_VALUE

            val ix = x.toInt()
            val iy = y.toInt()
            if (ix < 0 || iy < 0 || ix + w > smallParent.width || iy + h > smallParent.height) return Double.MAX_VALUE

            var penalty = 0.0
            if (estimate != null) {
                val estX = estimate.x * globalScale
                val estY = estimate.y * globalScale
                val estW = estimate.width * globalScale
                val estH = estimate.height * globalScale
                val currW = smallSub.width * s
                val currH = smallSub.height * s
                penalty = (x - estX).pow(2) + (y - estY).pow(2) + (currW - estW).pow(2) + (currH - estH).pow(2)
            }

            val scaledSub = resize(smallSub, w, h)
            return diff(smallParent, ix, iy, scaledSub, Double.MAX_VALUE) + penalty
        }

        var bestScore = Double.MAX_VALUE
        var bestX = 0.0
        var bestY = 0.0
        var bestS = 1.0

        if (estimate != null) {
            bestX = estimate.x * globalScale
            bestY = estimate.y * globalScale
            bestS = estimate.width.toDouble() / subImage.width
            bestScore = score(bestX, bestY, bestS)
        } else {
            val scales = listOf(0.5, 0.75, 1.0, 1.25, 1.5)
            for (s in scales) {
                val w = (smallSub.width * s).toInt()
                val h = (smallSub.height * s).toInt()
                if (w > smallParent.width || h > smallParent.height) continue
                val step = max(1, min(smallParent.width, smallParent.height) / 10)
                for (y in 0 until (smallParent.height - h) step step) {
                    for (x in 0 until (smallParent.width - w) step step) {
                        val sc = score(x.toDouble(), y.toDouble(), s)
                        if (sc < bestScore) {
                            bestScore = sc
                            bestX = x.toDouble()
                            bestY = y.toDouble()
                            bestS = s
                        }
                    }
                }
            }
        }

        var stepX = if (estimate != null) 5.0 else max(1.0, smallParent.width / 4.0)
        var stepY = if (estimate != null) 5.0 else max(1.0, smallParent.height / 4.0)
        var stepS = if (estimate != null) 0.1 else 0.2

        while (stepX > 0.5 || stepY > 0.5 || stepS > 0.01) {
            var improved = false
            fun tryMove(nx: Double, ny: Double, ns: Double): Boolean {
                val s = score(nx, ny, ns)
                if (s < bestScore) {
                    bestScore = s
                    bestX = nx
                    bestY = ny
                    bestS = ns
                    return true
                }
                return false
            }

            if (stepX > 0.5) {
                val s0 = bestScore
                val sLeft = score(bestX - stepX, bestY, bestS)
                val sRight = score(bestX + stepX, bestY, bestS)
                if (sLeft < s0 && sLeft < sRight) {
                    bestX -= stepX; bestScore = sLeft; improved = true
                } else if (sRight < s0) {
                    bestX += stepX; bestScore = sRight; improved = true
                } else if (sLeft != Double.MAX_VALUE && sRight != Double.MAX_VALUE) {
                    val denom = sLeft - 2 * s0 + sRight
                    if (denom > 1e-5) {
                        val delta = stepX * (sLeft - sRight) / (2 * denom)
                        if (abs(delta) < stepX) tryMove(bestX + delta, bestY, bestS)
                    }
                }
            }
            if (stepY > 0.5) {
                val s0 = bestScore
                val sUp = score(bestX, bestY - stepY, bestS)
                val sDown = score(bestX, bestY + stepY, bestS)
                if (sUp < s0 && sUp < sDown) {
                    bestY -= stepY; bestScore = sUp; improved = true
                } else if (sDown < s0) {
                    bestY += stepY; bestScore = sDown; improved = true
                } else if (sUp != Double.MAX_VALUE && sDown != Double.MAX_VALUE) {
                    val denom = sUp - 2 * s0 + sDown
                    if (denom > 1e-5) {
                        val delta = stepY * (sUp - sDown) / (2 * denom)
                        if (abs(delta) < stepY) tryMove(bestX, bestY + delta, bestS)
                    }
                }
            }
            if (stepS > 0.01) {
                val s0 = bestScore
                val sSmall = score(bestX, bestY, bestS - stepS)
                val sLarge = score(bestX, bestY, bestS + stepS)
                if (sSmall < s0 && sSmall < sLarge) {
                    bestS -= stepS; bestScore = sSmall; improved = true
                } else if (sLarge < s0) {
                    bestS += stepS; bestScore = sLarge; improved = true
                } else if (sSmall != Double.MAX_VALUE && sLarge != Double.MAX_VALUE) {
                    val denom = sSmall - 2 * s0 + sLarge
                    if (denom > 1e-5) {
                        val delta = stepS * (sSmall - sLarge) / (2 * denom)
                        if (abs(delta) < stepS) tryMove(bestX, bestY, bestS + delta)
                    }
                }
            }
            if (!improved) {
                stepX /= 2
                stepY /= 2
                stepS /= 2
            }
        }

        // Clamp to parent bounds
        val finalW = (smallSub.width * bestS).toInt()
        val finalH = (smallSub.height * bestS).toInt()
        val finalX = (bestX / globalScale).toInt()
        val finalY = (bestY / globalScale).toInt()
        val finalWOrig = (finalW / globalScale).toInt()
        val finalHOrig = (finalH / globalScale).toInt()

        val cx = max(0, min(parentImage.width - 1, finalX))
        val cy = max(0, min(parentImage.height - 1, finalY))
        val cw = min(parentImage.width - cx, finalWOrig)
        val ch = min(parentImage.height - cy, finalHOrig)

        return SubImageBounds(cx, cy, cw, ch, bestScore)
    }

    private fun normalize(image: BufferedImage): BufferedImage {
        val width = image.width
        val height = image.height
        val pixels = image.getRGB(0, 0, width, height, null, 0, width)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumSqR = 0.0
        var sumSqG = 0.0
        var sumSqB = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            sumR += r
            sumG += g
            sumB += b
            sumSqR += r * r
            sumSqG += g * g
            sumSqB += b * b
        }
        val count = pixels.size.toDouble()
        val meanR = sumR / count
        val meanG = sumG / count
        val meanB = sumB / count
        val stdR = sqrt(max(0.0, sumSqR / count - meanR * meanR))
        val stdG = sqrt(max(0.0, sumSqG / count - meanG * meanG))
        val stdB = sqrt(max(0.0, sumSqB / count - meanB * meanB))
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val newR = ((r - meanR) / (stdR + 1.0) * 64.0 + 128.0).coerceIn(0.0, 255.0).toInt()
            val newG = ((g - meanG) / (stdG + 1.0) * 64.0 + 128.0).coerceIn(0.0, 255.0).toInt()
            val newB = ((b - meanB) / (stdB + 1.0) * 64.0 + 128.0).coerceIn(0.0, 255.0).toInt()
            pixels[i] = (newR shl 16) or (newG shl 8) or newB
        }
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        result.setRGB(0, 0, width, height, pixels, 0, width)
        return result
    }


    private fun resize(img: BufferedImage, scale: Double): BufferedImage {
        val w = max(1, (img.width * scale).toInt())
        val h = max(1, (img.height * scale).toInt())
        return resize(img, w, h)
    }

    private fun resize(img: BufferedImage, w: Int, h: Int): BufferedImage {
        val resized = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()
        return resized
    }

    private fun diff(img1: BufferedImage, x1: Int, y1: Int, img2: BufferedImage, maxScore: Double): Double {
        var sum = 0.0
        val w = img2.width
        val h = img2.height
        val limit = maxScore * w * h
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb1 = img1.getRGB(x1 + x, y1 + y)
                val rgb2 = img2.getRGB(x, y)
                val dr = ((rgb1 shr 16) and 0xFF) - ((rgb2 shr 16) and 0xFF)
                val dg = ((rgb1 shr 8) and 0xFF) - ((rgb2 shr 8) and 0xFF)
                val db = (rgb1 and 0xFF) - (rgb2 and 0xFF)
                sum += (dr * dr + dg * dg + db * db)
                if (sum > limit) return Double.MAX_VALUE
            }
        }
        return sum / (w * h)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/util/MarkdownUtil.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.util.AgentPatterns.displayMapInTabs
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import org.apache.commons.text.StringEscapeUtils
import java.nio.file.Files
import java.util.*

object MarkdownUtil {
    private const val RENDERED_MARKER = "<!-- markdown-rendered -->"
    
    fun renderMarkdown(
        rawMarkdown: String,
        options: MutableDataSet = defaultOptions(),
        tabs: Boolean = true,
        ui: SocketManager? = null,
    ) = renderMarkdown(rawMarkdown, options, tabs, ui) { it }

    fun renderMarkdown(
        rawMarkdown: String,
        options: MutableDataSet = defaultOptions(),
        tabs: Boolean = true,
        ui: SocketManager? = null,
        markdownEditor: (String) -> String,
    ): String {
        if (rawMarkdown.isBlank()) return ""
        // Check if already rendered
        if (rawMarkdown.contains(RENDERED_MARKER)) {
            return rawMarkdown
        }
        
        val markdown = markdownEditor(rawMarkdown)
        val asHtml = renderMermaid(markdown.markdownToHtml(options), ui, tabs)
        return when {
            markdown.isBlank() -> ""
            asHtml == rawMarkdown -> asHtml
            tabs -> {
                displayMapInTabs(
                    mapOf(
                        "HTML" to "$RENDERED_MARKER\n${stackTrace()}\n$asHtml",
                        "Markdown" to """<pre><code class="language-markdown">${
                            rawMarkdown.replace("&", "&amp;")
                                .replace("<", "&lt;")
                                .replace(">", "&gt;")
                        }</code></pre>""",
                        "Hide" to "",
                    ), ui = ui
                )
            }

            else -> "$RENDERED_MARKER\n${stackTrace()}\n$asHtml"
        }
    }

    var includeStacks = false

    private fun stackTrace(): String {
        @Suppress("KotlinConstantConditions") if(!includeStacks) return "" // Disable stack trace inclusion
        val stackTrace = Thread.currentThread().stackTrace
        val filteredStack = stackTrace.filter {
            !it.className.startsWith("com.simiacryptus.cognotik.util.MarkdownUtil") &&
                    !it.className.startsWith("java.lang.Thread")
        }
        val formattedStack = filteredStack.joinToString(separator = "\n") { element ->
            "at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})"
        }
        return "<!--\n$formattedStack\n-->"
    }

    fun String.markdownToHtml(options: MutableDataSet = defaultOptions()): String =
        HtmlRenderer.builder(options).build().render(Parser.builder(options).build().parse(this))

    fun renderMermaid(html: String, ui: SocketManager?, tabs: Boolean): String {
        val mermaidRegex =
            Regex("<pre[^>]*><code class=\"language-mermaid\">(.*?)</code></pre>", RegexOption.DOT_MATCHES_ALL)
        val matches = mermaidRegex.findAll(html)
        var htmlContent = html
        matches.forEach { match ->
            val mermaidCode = match.groups[1]!!.value

            val fixedMermaidCode = fixupMermaidCode(mermaidCode)
            var mermaidDiagramHTML = """<pre class="mermaid">$fixedMermaidCode</pre>"""
            try {
                val svg = renderMermaidToSVG(fixedMermaidCode)
                if (null != ui) {
                    val graphTask = ui.newTask(false)
                    mermaidDiagramHTML = graphTask.placeholder
                    graphTask.complete(svg)
                } else {
                    mermaidDiagramHTML = svg
                }
            } catch (e: Exception) {
                log.warn("Failed to render Mermaid diagram: " + e.message)
            }
            val replacement = if (tabs) """
        <div class="tabs-container" id="""".trimIndent() + UUID.randomUUID() + """">
          <div class="tabs">
            <button class="tab-button active" data-for-tab="1">Diagram</button>
            <button class="tab-button" data-for-tab="2">Source</button>
          </div>
          <div class="tab-content active" data-tab="1">""".trimIndent() + mermaidDiagramHTML + """</div>
          <div class="tab-content" data-tab="2"><pre><code class="language-mermaid">""".trimIndent() + fixedMermaidCode + """</code></pre></div>
        </div>
        """.trimIndent() else mermaidDiagramHTML
            htmlContent = htmlContent.replace(match.value, replacement)
        }
        return htmlContent
    }

    var MMDC_CMD: List<String> = listOf(System.getProperty("mmdc", "mmdc"))
    private fun renderMermaidToSVG(mermaidCode: String): String {

        val tempInputFile = Files.createTempFile("mermaid", ".mmd").toFile()
        val tempOutputFile = Files.createTempFile("mermaid", ".svg").toFile()
        tempInputFile.writeText(StringEscapeUtils.unescapeHtml4(mermaidCode))
        val strings = MMDC_CMD + listOf("-i", tempInputFile.absolutePath, "-o", tempOutputFile.absolutePath)
        val processBuilder =
            ProcessBuilder(*strings.toTypedArray())
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        val output = StringBuilder()
        val errorOutput = StringBuilder()
        process.inputStream.bufferedReader().use {
            it.lines().forEach { line -> output.append(line) }
        }
        process.errorStream.bufferedReader().use {
            it.lines().forEach { line -> errorOutput.append(line) }
        }
        process.waitFor()
        val svgContent = tempOutputFile.readText()
        tempInputFile.delete()
        tempOutputFile.delete()
        if (output.isNotEmpty()) {
            log.error("Mermaid CLI Output: $output")
        }
        if (errorOutput.isNotEmpty()) {
            log.error("Mermaid CLI Error: $errorOutput")
        }
        if (svgContent.isBlank()) {
            throw RuntimeException("Mermaid CLI failed to generate SVG")
        }
        return svgContent
    }

    enum class State {
        DEFAULT, IN_NODE, IN_EDGE, IN_LABEL, IN_KEYWORD
    }

    fun fixupMermaidCode(code: String): String {
        val stringBuilder = StringBuilder()
        var index = 0
        var currentState = State.DEFAULT
        var labelStart = -1
        val keywords = listOf("graph", "subgraph", "end", "classDef", "class", "click", "style")

        while (index < code.length) {
            when (currentState) {
                State.DEFAULT -> {
                    if (code.startsWith(keywords.find { code.startsWith(it, index) } ?: "", index)) {

                        currentState = State.IN_KEYWORD
                        stringBuilder.append(code[index])
                    } else
                        if (code[index] == '[' || code[index] == '(' || code[index] == '{') {

                            currentState = State.IN_LABEL
                            labelStart = index
                        } else if (code[index].isWhitespace() || code[index] == '-') {

                            stringBuilder.append(code[index])
                        } else {

                            currentState = State.IN_NODE
                            stringBuilder.append(code[index])
                        }
                }

                State.IN_KEYWORD -> {
                    if (code[index].isWhitespace()) {

                        currentState = State.DEFAULT
                    }
                    stringBuilder.append(code[index])
                }

                State.IN_NODE -> {
                    if (code[index] == '-' || code[index] == '>' || code[index].isWhitespace()) {

                        currentState = if (code[index].isWhitespace()) State.DEFAULT else State.IN_EDGE
                        stringBuilder.append(code[index])
                    } else {

                        stringBuilder.append(code[index])
                    }
                }

                State.IN_EDGE -> {
                    if (!code[index].isWhitespace() && code[index] != '-' && code[index] != '>') {

                        currentState = State.IN_NODE
                        stringBuilder.append(code[index])
                    } else {

                        stringBuilder.append(code[index])
                    }
                }

                State.IN_LABEL -> {
                    if (code[index] == ']' || code[index] == ')' || code[index] == '}') {

                        val label = code.substring(labelStart + 1, index)
                        val escapedLabel = "\"${label.replace("\"", "'")}\""
                        stringBuilder.append(escapedLabel)
                        stringBuilder.append(code[index])
                        currentState = State.DEFAULT
                    }
                }
            }
            index++
        }

        return stringBuilder.toString()
    }

    private fun defaultOptions(): MutableDataSet {
        val options = MutableDataSet()
        options.set(Parser.EXTENSIONS, listOf(TablesExtension.create()))
        return options
    }

    private val log = LoggerFactory.getLogger(MarkdownUtil::class.java)
}


fun String.renderMarkdown(tabs: Boolean = false): String = MarkdownUtil.renderMarkdown(this, tabs = tabs)
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/util/RecordingThreadFactory.kt

```
package com.simiacryptus.cognotik.util

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User

class RecordingThreadFactory(
    val session: Session,
    val user: User?
) : ImmediateExecutorService.ThreadFactoryTrackerInterface() {
    private val inner = ThreadFactoryBuilder().setNameFormat("Session $session; User $user; #%d").setDaemon(true).build()
    override fun newThread(r: Runnable): Thread {
        log.debug("Creating new thread for session: {}, user: {}", session, user)
        inner.newThread(r).also {
            threads.add(it)
            return it
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RecordingThreadFactory::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/util/Selenium2S3.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.platform.ApplicationServices.cloud
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.cookie.BasicCookieStore
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.Method
import org.jsoup.Jsoup
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.File
import java.net.URI
import java.net.URL
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

open class Selenium2S3(
    val pool: ExecutorService = Executors.newCachedThreadPool() as ExecutorService,
    private val cookies: Array<out jakarta.servlet.http.Cookie>? = null,
    val driver: RemoteWebDriver = chromeDriver()
) : Selenium {
    override fun navigate(url: String) {
        (driver as WebDriver).navigate().to(url)
    }

    override fun getPageSource(): String {
        return (driver as WebDriver).pageSource ?: ""
    }

    override fun getCurrentUrl(): String {
        return (driver as WebDriver).currentUrl ?: ""
    }

    override fun executeScript(script: String): Any? {
        return (driver as JavascriptExecutor).executeScript(script)
    }

    override fun quit() {
        (driver as WebDriver).quit()
    }

    var loadImages: Boolean = false

    private val httpClient by lazy {
        HttpAsyncClientBuilder.create()
            .useSystemProperties()
            .setDefaultCookieStore(BasicCookieStore().apply {
                cookies?.forEach { cookie -> addCookie(BasicClientCookie(cookie.name, cookie.value)) }
            })
            .build()
            .also { it.start() }
    }

    private val linkReplacements = mutableMapOf<String, String>()
    private val htmlPages: MutableMap<String, String> = mutableMapOf()
    private val jsonPages = mutableMapOf<String, String>()
    private val links: MutableList<String> = mutableListOf()

    override fun save(
        url: URL,
        currentFilename: String?,
        saveRoot: String
    ) {
        log.info("Saving URL: $url")
        log.info("Current filename: $currentFilename")
        log.info("Save root: $saveRoot")
        driver.navigate().to(url)
        driver.navigate().refresh()
        Thread.sleep(5000)


        htmlPages += mutableMapOf((currentFilename ?: url.file.split("/").last()) to editPage(driver.pageSource ?: ""))
        val baseUrl = url.toString().split("#").first()
        links += toAbsolute(baseUrl, *currentPageLinks(driver).map { link ->
            val relative = toRelative(baseUrl, link) ?: return@map link
            linkReplacements[link] = "${cloud!!.shareBase}/$saveRoot/${toArchivePath(relative)}"
            linkReplacements[relative] = "${cloud!!.shareBase}/$saveRoot/${toArchivePath(relative)}"
            link
        }.toTypedArray()).toMutableList()
        val completionSemaphores = mutableListOf<Semaphore>()

        log.info("Fetching page source")
        log.info("Base URL: $baseUrl")
        val coveredLinks = mutableSetOf<String>()
        log.info("Processing links")
        while (links.isNotEmpty()) {
            val href = links.removeFirst()
            try {
                if (coveredLinks.contains(href)) continue
                coveredLinks += href
                log.debug("Processing $href")
                process(url, href, completionSemaphores, saveRoot)
            } catch (e: Exception) {
                log.warn("Error processing $href", e)
            }
        }

        log.info("Fetching current page links")
        log.debug("Waiting for completion")
        completionSemaphores.forEach { it.acquire(); it.release() }

        log.debug("Saving")
        saveAll(saveRoot)
        log.debug("Done")
    }

    override fun setScriptTimeout(timeout: Long) {
        (driver as WebDriver).manage().timeouts().scriptTimeout(Duration.ofMillis(timeout))
    }

    override fun getBrowserInfo(): String {
        return driver.capabilities.browserName
    }

    override fun forceQuit() {
        driver.quit()
    }

    override fun isAlive(): Boolean {
        return driver.sessionId != null
    }

    override fun getLogs(): String {
        return driver.manage().logs().get(LogType.BROWSER).all.joinToString("\n")
    }

    protected open fun process(
        url: URL,
        href: String,
        completionSemaphores: MutableList<Semaphore>,
        saveRoot: String
    ): Boolean {
        val base = url.toString().split("/").dropLast(1).joinToString("/")
        val relative = toArchivePath(toRelative(base, href) ?: return true)
        when (val mimeType = mimeType(relative)) {

            "text/html" -> {
                if (htmlPages.containsKey(relative)) return true
                log.info("Fetching $href")
                val semaphore = Semaphore(0)
                completionSemaphores += semaphore
                getHtml(href, htmlPages, relative, links, saveRoot, semaphore)
            }

            "application/json" -> {
                if (jsonPages.containsKey(relative)) return true
                log.info("Fetching $href")
                val semaphore = Semaphore(0)
                completionSemaphores += semaphore
                getJson(href, jsonPages, relative, semaphore)
            }

            else -> {
                val semaphore = Semaphore(0)
                completionSemaphores += semaphore
                getMedia(href, mimeType, saveRoot, relative, semaphore)
            }
        }
        return false
    }

    protected open fun getHtml(
        href: String,
        htmlPages: MutableMap<String, String>,
        relative: String,
        links: MutableList<String>,
        saveRoot: String,
        semaphore: Semaphore
    ) {
        httpClient.execute(get(href), object : FutureCallback<SimpleHttpResponse> {

            override fun completed(p0: SimpleHttpResponse?) {
                log.debug("Fetched $href")
                val html = p0?.body?.bodyText ?: ""
                htmlPages[relative] = html
                links += toAbsolute(href, *currentPageLinks(html).map { link ->
                    val relative = toArchivePath(toRelative(href, link) ?: return@map link)
                    linkReplacements[link] = "${cloud!!.shareBase}/$saveRoot/$relative"
                    link
                }.toTypedArray())
                semaphore.release()
            }

            override fun failed(p0: java.lang.Exception?) {
                log.info("Error fetching $href", p0)
                semaphore.release()
            }

            override fun cancelled() {
                log.info("Cancelled fetching $href")
                semaphore.release()
            }

        })
    }

    protected open fun getJson(
        href: String,
        jsonPages: MutableMap<String, String>,
        relative: String,
        semaphore: Semaphore
    ) {
        httpClient.execute(get(href), object : FutureCallback<SimpleHttpResponse> {

            override fun completed(p0: SimpleHttpResponse?) {
                log.debug("Fetched $href")
                jsonPages[relative] = p0?.body?.bodyText ?: ""
                semaphore.release()
            }

            override fun failed(p0: java.lang.Exception?) {
                log.info("Error fetching $href", p0)
                semaphore.release()
            }

            override fun cancelled() {
                log.info("Cancelled fetching $href")
                semaphore.release()
            }

        })
    }

    protected open fun getMedia(
        href: String,
        mimeType: String,
        saveRoot: String,
        relative: String,
        semaphore: Semaphore
    ) {
        val request = get(href)
        httpClient.execute(request, object : FutureCallback<SimpleHttpResponse> {

            override fun completed(p0: SimpleHttpResponse?) {
                try {
                    log.debug("Fetched {}", request)
                    val bytes = p0?.body?.bodyBytes ?: return
                    if (validate(mimeType, p0.body.contentType.mimeType, bytes))
                        cloud!!.upload(
                            path = "/$saveRoot/$relative",
                            contentType = mimeType,
                            bytes = bytes
                        )
                } finally {
                    semaphore.release()
                }
            }

            override fun failed(p0: java.lang.Exception?) {
                log.info("Error fetching $href", p0)
                semaphore.release()
            }

            override fun cancelled() {
                log.info("Cancelled fetching $href")
                semaphore.release()
            }

        })
    }

    private fun saveAll(
        saveRoot: String
    ) {
        (htmlPages.map { (filename, html) ->
            pool.submit {
                try {
                    saveHTML(html, saveRoot, filename)
                } catch (e: Exception) {
                    log.warn("Error processing $filename", e)
                }
            }
        } + jsonPages.map { (filename, js) ->
            pool.submit {
                try {
                    saveJS(js, saveRoot, filename)
                } catch (e: Exception) {
                    log.warn("Error processing $filename", e)
                }
            }
        }).forEach {
            try {
                it.get()
            } catch (e: Exception) {
                log.warn("Error processing", e)
            }
        }
    }

    protected open fun saveJS(js: String, saveRoot: String, filename: String) {
        val finalJs = linkReplacements.toList().sortedBy { it.first.length }
            .fold(js) { acc, (href, relative) ->

                acc.replace("""(?<![/\w])$href""".toRegex(), relative)
            }
        cloud!!.upload(
            path = "/$saveRoot/$filename",
            contentType = "application/json",
            request = finalJs
        )
    }

    protected open fun saveHTML(html: String, saveRoot: String, filename: String) {
        val finalHtml = linkReplacements.toList().filter { it.first.isNotEmpty() }.fold(html)
        { acc, (href, relative) -> acc.replace("""(?<![/\w#])$href""".toRegex(), relative) }
        cloud!!.upload(
            path = "/$saveRoot/$filename",
            contentType = "text/html",
            request = finalHtml
        )
    }

    protected open fun get(href: String): SimpleHttpRequest {
        val request = SimpleHttpRequest(Method.GET, URI(href))
        cookies?.forEach { cookie ->
            request.addHeader("Cookie", "${cookie.name}=${cookie.value}")
        }
        return request
    }

    protected open fun currentPageLinks(driver: WebDriver) = listOf(
        driver.findElements(By.xpath("//a[@href]")).map<WebElement?, String?> { it?.getAttribute("href") }.toSet(),
        driver.findElements(By.xpath("//img[@src]")).map<WebElement?, String?> { it?.getAttribute("src") }.toSet(),
        driver.findElements(By.xpath("//link[@href]")).map<WebElement?, String?> { it?.getAttribute("href") }.toSet(),
        driver.findElements(By.xpath("//script[@src]")).map<WebElement?, String?> { it?.getAttribute("src") }.toSet(),
        driver.findElements(By.xpath("//source[@src]")).map<WebElement?, String?> { it?.getAttribute("src") }.toSet(),
    ).flatten().filterNotNull()

    private fun currentPageLinks(html: String) = listOf(
        Jsoup.parse(html).select("a[href]").map { it.attr("href") }.toSet(),
        Jsoup.parse(html).select("img[src]").map { it.attr("src") }.toSet(),
        Jsoup.parse(html).select("link[href]").map { it.attr("href") }.toSet(),
        Jsoup.parse(html).select("script[src]").map { it.attr("src") }.toSet(),
        Jsoup.parse(html).select("source[src]").map { it.attr("src") }.toSet(),
    ).flatten()

    protected open fun toAbsolute(base: String, vararg links: String) = links
        .map { it.split("#").first() }.filter { it.isNotBlank() }.distinct()
        .map { link ->
            val newLink = when {
                link.startsWith("http") -> link
                else -> URI.create(base).resolve(link).toString()
            }
            newLink
        }

    protected open fun toRelative(base: String, link: String): String? = when {
        link.startsWith(base) -> toRelative(
            base,
            link.removePrefix(base).replace("/{2,}".toRegex(), "/").removePrefix("/")
        )

        link.startsWith("http") -> null

        else -> link

    }

    protected open fun toArchivePath(link: String): String = when {
        link.startsWith("fileIndex") -> link.split("/").drop(2).joinToString("/")

        else -> link
    }

    protected open fun validate(
        expected: String,
        actual: String,
        bytes: ByteArray
    ): Boolean {
        if (!actual.startsWith(expected)) {
            log.warn("Content type mismatch: $actual != $expected")
            if (actual.startsWith("text/html")) {
                log.warn("Response Error: ${String(bytes)}", Exception())
            }
            return false
        }
        return true
    }

    protected open fun mimeType(relative: String): String {
        val extension = relative.split(".").last().split("?").first()
        val contentType = when (extension) {
            "css" -> "text/css"
            "js" -> "text/javascript"
            "json" -> "application/json"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "bz2" -> "application/bzip2"
            "mp3" -> "audio/mpeg"

            "csv" -> "text/csv"
            "txt" -> "text/plain"
            "xml" -> "text/xml"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg" -> "image/jpeg"
            "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "ico" -> "image/x-icon"
            "html" -> "text/html"
            "htm" -> "text/html"
            else -> "text/plain"
        }
        return contentType
    }

    protected open fun editPage(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("#toolbar").remove()
        doc.select("#namebar").remove()
        doc.select("#main-input").remove()
        doc.select("#footer").remove()
        return doc.toString()
    }

    override fun close() {
        log.debug("Closing", Exception())
        driver.quit()
        httpClient.close()


    }

    companion object {
        private val log = LoggerFactory.getLogger(Selenium2S3::class.java)

        init {
            Runtime.getRuntime().addShutdownHook(Thread {
                try {
                } catch (e: Exception) {
                    log.warn("Error closing com.simiacryptus.cognotik.webui.util.Selenium2S3", e)
                }
            })
        }

        fun chromeDriver(headless: Boolean = true, loadImages: Boolean = !headless): ChromeDriver {
            val osname = System.getProperty("os.name")
            val chromePath = when {

                osname.contains("Windows") -> listOf(
                    "C:\\Program Files\\Google\\Chrome\\Application\\chromedriver.exe",
                    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chromedriver.exe"
                )

                osname.contains("Linux") -> listOf("/usr/bin/chromedriver")
                else -> throw RuntimeException("Not implemented for $osname")
            }
            System.setProperty(
                "webdriver.chrome.driver",
                chromePath.find { File(it).exists() } ?: throw RuntimeException("Chrome not found"))
            val options = ChromeOptions()
            val args = mutableListOf<String>()
            if (headless) args += "--headless"
            if (loadImages) args += "--blink-settings=imagesEnabled=false"
            options.addArguments(*args.toTypedArray())
            options.setPageLoadTimeout(Duration.of(90, ChronoUnit.SECONDS))
            return try {
                ChromeDriver(chromeDriverService, options)
            } catch (e: Exception) {
                log.warn(
                    "Error initializing ChromeDriver with service - Check https://googlechromelabs.github.io/chrome-for-testing/ for ChromeDriver update",
                    e
                )
                throw e
            }
        }

        private val chromeDriverService by lazy { ChromeDriverService.createDefaultService() }
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/util/oneAtATime.kt

```
package com.simiacryptus.cognotik.util

import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

fun <T> oneAtATime(handler: Consumer<T>): Consumer<T> {
    val guard = AtomicBoolean(false)
    return Consumer { t ->
        if (guard.getAndSet(true)) return@Consumer
        handler.accept(t)
        guard.set(false)
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

### webui/src/main/kotlin/com/simiacryptus/cognotik/util/README.md
```markdown
# Utility Package

This package contains a collection of utility classes and functions used across the Cognotik WebUI for HTML processing, image manipulation, markdown rendering, and web automation.

## Components

### [HtmlSimplifier](HtmlSimplifier.kt)
An object providing robust HTML scrubbing and simplification. It is designed to clean up raw HTML by:
- Removing unsafe elements (scripts, iframes, etc.) and event handlers.
- Filtering attributes to keep only essential ones (href, src, alt, etc.).
- Simplifying nested structures and unwrapping simple text elements.
- Converting relative URLs to absolute URLs given a base URL.
- Cleaning up whitespace and empty elements.

### [ImagePatchLocalization](ImagePatchLocalization.kt)
A utility for finding the location and scale of a sub-image within a larger parent image.
- Uses a multi-scale search approach followed by gradient-based refinement.
- Includes image normalization to handle lighting variations.
- Supports providing an initial estimate to speed up and constrain the search.

### [MarkdownUtil](MarkdownUtil.kt)
A comprehensive Markdown rendering utility built on Flexmark.
- Renders Markdown to HTML with support for tables.
- Integrates Mermaid.js diagram rendering via a CLI tool (`mmdc`).
- Supports tabbed displays for showing both rendered HTML and raw Markdown source.
- Includes logic to fix common Mermaid syntax issues generated by LLMs.

### [RecordingThreadFactory](RecordingThreadFactory.kt)
A custom `ThreadFactory` that tracks threads created for specific user sessions.
- Names threads based on Session and User IDs for easier debugging.
- Maintains a list of active threads for lifecycle management.

### [Selenium2S3](Selenium2S3.kt)
An implementation of the `Selenium` interface that automates web browsing and archives content to S3-compatible storage.
- Uses headless Chrome via Selenium WebDriver.
- Recursively fetches and saves linked assets (images, scripts, CSS, JSON).
- Rewrites links in saved HTML/JS files to point to the archived versions in the cloud.
- Handles cookie synchronization for authenticated sessions.

### [oneAtATime](oneAtATime.kt)
A simple concurrency utility that wraps a `Consumer` to ensure that only one execution happens at a time. If the consumer is already busy, subsequent calls are ignored until the current execution completes.

## Usage Examples

### HTML Scrubbing
```kotlin
val cleanHtml = HtmlSimplifier.scrubHtml(
    str = rawHtml,
    baseUrl = "https://example.com",
    simplifyStructure = true
)
```

### Markdown Rendering
```kotlin
val html = MarkdownUtil.renderMarkdown(
    rawMarkdown = "# Hello World",
    tabs = true
)
```

### Concurrency Control
```kotlin
val guardedHandler = oneAtATime<String> { data ->
    // Process data...
}
// Multiple calls will only trigger one active processing
```
```

This `README.md` provides a high-level overview of the utility classes in the `com.simiacryptus.cognotik.util` package, detailing their primary responsibilities and providing basic usage examples. This helps developers understand the available tools for HTML processing, image analysis, and web automation within the project.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/util/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/util/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/util/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/util/README.md</a> Updated
