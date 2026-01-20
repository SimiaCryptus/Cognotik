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

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/ui/AddApplyDiffLinks.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.diff.DiffUtil
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchResult
import com.simiacryptus.cognotik.diff.SimpleDiffApplier
import com.simiacryptus.cognotik.util.AgentPatterns.displayMapInTabs
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager

class AddApplyDiffLinks {
    companion object {
        val log = LoggerFactory.getLogger(AddApplyDiffLinks::class.java)

        private val diffApplier = SimpleDiffApplier()

        fun addApplyDiffLinks(
            self: SocketManager,
            code: () -> String,
            response: String,
            handle: (String) -> Unit,
            task: SessionTask,
            shouldAutoApply: Boolean = false,
            processor: PatchProcessor,
        ) = AddApplyDiffLinks().apply(self, code, response, handle, task, shouldAutoApply, processor)

    }

    fun apply(
        socketManager: SocketManager,
        code: () -> String,
        response: String,
        handle: (String) -> Unit,
        task: SessionTask,
        shouldAutoApply: Boolean = false,
        processor: PatchProcessor,
    ): String {
        val matches = SimpleDiffApplier.DIFF_PATTERN.findAll(response).distinct()

        val patch = { code: String, diff: String ->
            val result = diffApplier.apply(code, "```diff\n$diff\n```", processor = processor)
            PatchResult(result.newCode, result.isValid, null)
        }

        val withLinks = matches.fold(response) { markdown, diffBlock ->
            val diffVal: String = diffBlock.groupValues[1]

            if (shouldAutoApply) {
                try {
                    val newCode = patch(code(), diffVal)
                    if (newCode.isValid) {
                        handle(newCode.newCode)
                        return@fold markdown.replace(
                            diffBlock.value,
                            """```diff
              $diffVal
              ```
              <div class="cmd-button">Diff Automatically Applied</div>"""
                        )
                    }

                    return@fold markdown.replace(
                        diffBlock.value,
                        """```diff
            $diffVal
            ```
            <div class="cmd-button">Error: ${newCode.error ?: "Invalid patch"}</div>"""
                    )
                } catch (e: Throwable) {
                    log.error("Error auto-applying diff", e)
                    return@fold markdown.replace(
                        diffBlock.value,
                        """```diff
            $diffVal
            ```
            <div class="cmd-button">Error Auto-Applying Diff: ${e.message}</div>"""
                    )
                }
            }

            val buttons = task.newTask()
            lateinit var hrefLink: StringBuilder
            var reverseHrefLink: StringBuilder? = null
            hrefLink = buttons.complete(socketManager.hrefLink("Apply Diff", classname = "href-link cmd-button") {
                try {
                    val newCode = patch(code(), diffVal)
                    handle(newCode.newCode)
                    hrefLink.set("""<div class="cmd-button">Diff Applied</div>""")
                    buttons.complete()
                    reverseHrefLink?.clear()
                } catch (e: Throwable) {
                    hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
                    buttons.complete()
                    task.error(e)
                }
            })!!
            val patch = patch(code(), diffVal).newCode
            val patchRev = patch(
                code().lines().reversed().joinToString("\n"),
                diffVal.lines().reversed().joinToString("\n")
            ).newCode
            val newValue = if (patchRev == patch) {
                val test1 = processor.generatePatch(code().replace("\r", ""), patch)
                displayMapInTabs(
                    mapOf(
                        "Diff" to renderMarkdown("```diff\n$diffVal\n```", ui = task.ui, tabs = true),
                        "Verify" to renderMarkdown("```diff\n$test1\n```", ui = task.ui, tabs = true),
                    ), ui = task.ui, split = true
                ) + "\n" + buttons.placeholder
            } else {
                @Suppress("AssignedValueIsNeverRead")
                reverseHrefLink =
                    buttons.complete(socketManager.hrefLink("(Bottom to Top)", classname = "href-link cmd-button") {
                        try {
                            val reversedCode = code().lines().reversed().joinToString("\n")
                            val reversedDiff = diffVal.lines().reversed().joinToString("\n")
                            val newReversedCode = patch(reversedCode, reversedDiff).newCode
                            val newCode = newReversedCode.lines().reversed().joinToString("\n")
                            handle(newCode)
                            reverseHrefLink!!.set("""<div class="cmd-button">Diff Applied (Bottom to Top)</div>""")
                            buttons.complete()
                            hrefLink.clear()
                        } catch (e: Throwable) {
                            task.error(e)
                        }
                    })!!
                val test1 = processor.generatePatch(code().replace("\r", ""), patch)
                val test2 = DiffUtil.formatDiff(
                    DiffUtil.generateDiff(
                        code().lines(),
                        patchRev.lines().reversed()
                    )
                )
                displayMapInTabs(
                    mapOf(
                        "Diff" to renderMarkdown("```diff\n$diffVal\n```", ui = task.ui, tabs = true),
                        "Verify" to renderMarkdown("```diff\n$test1\n```", ui = task.ui, tabs = true),
                        "Reverse" to renderMarkdown("```diff\n$test2\n```", ui = task.ui, tabs = true),
                    ), ui = task.ui, split = true
                ) + "\n" + buttons.placeholder
            }
            markdown.replace(diffBlock.value, newValue)
        }
        return withLinks
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/ui/AddApplyFileDiffLinks.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.diff.DiffApplicationResult
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchResult
import com.simiacryptus.cognotik.diff.SimpleDiffApplier
import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.io.path.readText

open class AddApplyFileDiffLinks(val processor: PatchProcessor) {

    companion object {
        var loggingEnabled = { false }
        private val diffApplier = SimpleDiffApplier()
        private val log = LoggerFactory.getLogger(AddApplyFileDiffLinks::class.java).apply {
            debug("Initializing AddApplyFileDiffLinks")
        }

        private fun logFileOperation(
            filepath: Path,
            originalCode: String,
            patch: String?,
            newCode: String,
            operationType: String,
            startTime: Instant,
            validator: GrammarValidator? = null
        ) {
            if (loggingEnabled()) try {
                val logFile = filepath.resolveSibling(filepath.fileName.toString() + ".log").toFile()
                val duration = Duration.between(startTime, Instant.now())
                val originalSize = filepath.toFile().length()
                val stackTrace = Thread.currentThread().stackTrace
                    .drop(2)
                    .take(10)
                    .joinToString("\n    ")
                val logEntry = buildString {
                    appendLine("─".repeat(80))
                    appendLine("Timestamp: ${Instant.now()}")
                    appendLine("Operation: $operationType")
                    appendLine("Duration: ${duration.toMillis()}ms")
                    appendLine("File: ${filepath.fileName}")
                    appendLine("Original Size: $originalSize bytes")
                    appendLine("New Size: ${filepath.toFile().length()} bytes")
                    appendLine("Validator: ${validator?.javaClass?.simpleName ?: "None"}")
                    appendLine("Stack Trace:")
                    appendLine("    $stackTrace")
                    appendLine("Original Code:")
                    originalCode.lines().forEach { appendLine("    $it") }
                    if (patch != null) {
                        appendLine("Patch:")
                        patch.lines().forEach { appendLine("    $it") }
                    }
                    appendLine("New Code:")
                    newCode.lines().forEach { appendLine("    $it") }
                    appendLine()
                }
                logFile.appendText(logEntry)
            } catch (e: Throwable) {
                log.error("Failed to write operation log", e)
            }
        }

        fun instrumentFileDiffs(
            self: SocketManager,
            root: Path,
            response: String,
            handle: (Map<Path, String>) -> Unit = {},
            shouldAutoApply: (Path) -> Boolean = { false },
            model: ChatInterface? = null,
            defaultFile: String? = null,
            processor: PatchProcessor,
        ) = AddApplyFileDiffLinks(processor).instrument(
            socketManager = self,
            root = root,
            response = response,
            handle = handle,
            shouldAutoApply = shouldAutoApply,
            model = model,
            defaultFile = defaultFile
        )

        fun newFile(
            filepath: Path,
            content: String,
            filename: String,
            handle: (Map<Path, String>) -> Unit
        ) {
            val startTime = Instant.now()
            filepath.toFile().parentFile?.mkdirs()
            filepath.toFile().writeText(content, Charsets.UTF_8)
            logFileOperation(filepath, "", null, content, "NEW_FILE", startTime)
            handle(mapOf(File(filename).toPath() to content))
        }
    }

    protected open fun getInitiatorPattern(): Regex {
        return processor.getInitiatorPattern()
    }

    protected open fun loadFile(filepath: Path?): String = try {
        when {
            filepath == null -> ""
            !filepath.toFile().exists() -> {
                log.warn("File not found: {}", filepath)
                ""
            }

            else -> filepath.readText(Charsets.UTF_8)
        }
    } catch (e: Throwable) {
        log.error("Error reading file {}: {}", filepath, e.message, e)
        ""
    }

    private fun String.reverseLines(): String = lines().reversed().joinToString("\n")

    private fun record(socketManager: SocketManager, data: Any): String {
        val relativePath = "patch/${UUID.randomUUID()}.json"
        require(relativePath.isNotBlank()) { "File path cannot be blank" }
        socketManager.resolveSystemFile(relativePath)?.writeText(data.toJson())
        return "<a href='fileIndex/${socketManager.sessionId}/$relativePath' target='_blank' class='verbose'>Patch Data</a>"
    }

    fun instrument(
        socketManager: SocketManager,
        root: Path,
        response: String,
        handle: (Map<Path, String>) -> Unit = {},
        shouldAutoApply: (Path) -> Boolean = { false },
        model: ChatInterface? = null,
        defaultFile: String? = null,
        resolver: ((Path, String) -> String?) = ::resolveToRelativePath,
    ): String {
        log.debug("Instrumenting file diffs for root: {}", root)
        val initiator = getInitiatorPattern()
        if (response.contains(initiator) && !response.split(initiator, 2)[1].contains("\n```(?![^\n])".toRegex())) {
            return instrument(
                socketManager = socketManager,
                root = root,
                response = response + "\n```\n",
                handle = handle,
                model = model,
                defaultFile = defaultFile,
            )
        }

        val codeBlocks = processor.extractCodeBlocks(response)
        val codeBlocksWithHeaders = codeBlocks.mapIndexed { index, (lang, code) ->
            val headerForBlock = findHeaderForBlock(response, code)
            Triple(
                if (headerForBlock != null) {
                    headerForBlock
                } else {
                    defaultFile
                }, lang, code
            )
        }
        if (codeBlocksWithHeaders.isNotEmpty()) {
            log.debug("Found ${codeBlocksWithHeaders.size} code blocks")
            codeBlocksWithHeaders.forEach { (header, lang, _) ->
                log.debug("Block: header='$header', lang='$lang'")
            }
        }

        fun isFileResolvable(header: String?): Boolean {
            return try {
                val resolvedPath = resolver(root, prefilterFilename(normalizeFilename(header ?: "")) ?: "")
                if (resolvedPath == null) {
                    return (true != header?.contains('.') && null != defaultFile)
                }
                when {
                    root.resolve(resolvedPath).toFile().exists() -> {
                        true
                    }
                    !resolvedPath.contains('.') && null != defaultFile -> {
                        log.debug("File not found for header '$header': resolved to '$resolvedPath', but default file is set")
                        true // Allow default file for extensionless paths (likely to be a mis-parse)
                    }

                    else -> {
                        log.debug("File not found for header '$header': resolved to '$resolvedPath'")
                        false
                    }
                }
            } catch (e: Throwable) {
                log.info("Error processing code block", e)
                false
            }
        }

        val (newFileBlocks, patchBlocks) = codeBlocksWithHeaders
            .partition { (header, lang, code) -> !isFileResolvable(header) }

        log.debug("Categorized blocks: ${newFileBlocks.size} new files, ${patchBlocks.size} patches")

        val withPatchLinks: String = patchBlocks.reversed().fold(response) { markdown, (header, lang, diffValue) ->
            var normalizeFilename = normalizeFilename(header ?: "")
            if (normalizeFilename.isBlank() || !normalizeFilename.contains('.')) {
                if (defaultFile == null) {
                    return@fold markdown
                }
                normalizeFilename = defaultFile
            }
            val filename = resolver(root, normalizeFilename) ?: return@fold markdown
            val newValue =
                try { socketManager.renderDiffBlock(root, filename, diffValue, handle, socketManager, shouldAutoApply) }
                catch (e: Throwable) {
                    log.error("Error rendering diff block for file: $filename", e)
                    "\n```diff\n$diffValue\n```\n<div class=\"warning\">Error rendering diff block for file $filename: ${e.message?.renderMarkdown()
                        ?: "Unknown error"}</div>\n"
                }
            val startOfMatch = markdown.indexOf(diffValue)
            if (startOfMatch < 0) {
                return@fold markdown
            }
            val endOfMatch = startOfMatch + diffValue.length
            val precedingText = markdown.substring(0, startOfMatch)
            val followingText = markdown.substring(endOfMatch)
            val prependLength = precedingText.lastIndexOf("```")
                .let { if (it >= 0) precedingText.length - it else 0 }
            val appendLength = followingText.indexOf("```").let { if (it >= 0) it + 3 else 0 }
            markdown.replaceRange(
                startIndex = startOfMatch - prependLength,
                endIndex = endOfMatch + appendLength,
                replacement = newValue
            )
        }

        val withSaveLinks =
            newFileBlocks.foldIndexed(withPatchLinks) { index, markdown, (header, lang, codeValue) ->
                var processedCode = codeValue.trimIndent()
                if (codeValue.lines().all { it.startsWith('+') || it.startsWith('-') }) {
                    processedCode = codeValue.lines().joinToString("\n") { it.drop(1) }
                }
                if (header.isNullOrBlank()) return markdown
                val filename = prefilterFilename(normalizeFilename(header))
                if (filename.isNullOrBlank()) return markdown
                val newMarkdown =
                    socketManager.renderNewFile(root, filename, processedCode, handle, socketManager, lang, shouldAutoApply) + record(
                        socketManager, mapOf(
                            "filename" to filename,
                            "code" to processedCode,
                            "header" to header,
                            "language" to lang,
                        )
                    )
                markdown.replace("```$lang\n$codeValue\n```", newMarkdown)
            }
        return withSaveLinks
    }

    private fun findHeaderForBlock(response: String, code: String): String? {
        val blockPosition = Regex.escape(code).toRegex().find(response)?.range?.first
        if (blockPosition == null) return null
        val markdownHeaderPattern = """(?<![^\n])#+\s*([^\n]+)""".toRegex()
        val fileHeaderPattern = """(?m)^(?:─+|-+)\s*\nFile:\s*(.+?)\s*\n(?:─+|-+)\s*""".toRegex()
        val headers = mutableListOf<Pair<IntRange, String>>()
        val markdownMatches = markdownHeaderPattern.findAll(response).toList().toTypedArray()
        markdownMatches.forEach { match ->
            headers.add(match.range to normalizeFilename(match.groupValues[1]))
        }
        val fileHeaderMatches = fileHeaderPattern.findAll(response).toList().toTypedArray()
        fileHeaderMatches.forEach { match ->
            headers.add(match.range to normalizeFilename(match.groupValues[1]))
        }
        val maxByOrNull = headers.filter { it.first.last <= blockPosition }.maxByOrNull { it.first.last }
        val str = maxByOrNull?.second
        if (null != str) return str
        return null
    }

    protected open fun normalizeFilename(filename: String): String {
        val newValue = filename.trim()
            // Remove common prefixes
            .removePrefix("Code:")
            .removePrefix("code:")
            .removePrefix("File:")
            .removePrefix("file:")
            .removePrefix("Path:")
            .removePrefix("path:")
            .removePrefix("Filename:")
            .removePrefix("filename:")
            .removePrefix("Modified:")
            .removePrefix("modified:")
            .removePrefix("Updated:")
            .removePrefix("updated:")
            .removePrefix("Changed:")
            .removePrefix("changed:")
            .removePrefix("Edit:")
            .removePrefix("edit:")
            .removePrefix("Patch:")
            .removePrefix("patch:")
            // Remove common suffixes
            .removeSuffix(":")
            .removeSuffix(".")
            // Remove quotes and backticks
            .removePrefix("\"").removeSuffix("\"")
            .removePrefix("'").removeSuffix("'")
            .removePrefix("`").removeSuffix("`")
            // Remove any number with a period
            .replace(Regex("^\\d+\\.\\s*"), "")
            // Remove markdown formatting
            .replace("**", "")
            .replace("*", "")
            // Clean up whitespace
            .trim()
            // Remove code block language indicators that might be mistaken for filenames
            .let { name ->
                if (name.matches(
                        Regex(
                            "^(java|kotlin|kt|js|javascript|python|py|cpp|c|cs|go|rust|rs|php|rb|ruby|swift|scala|clj|clojure|sh|bash|sql|html|css|xml|json|yaml|yml|toml|ini|cfg|conf|config|properties|gradle|maven|pom|dockerfile|docker|makefile|make|cmake|bazel|build)$",
                            RegexOption.IGNORE_CASE
                        )
                    )
                ) {
                    ""
                } else {
                    name
                }
            }
            .trim()
        return if (newValue != filename) normalizeFilename(newValue) else newValue
    }

    private fun SocketManager.renderNewFile(
        root: Path,
        filename: String,
        codeValue: String,
        handle: (Map<Path, String>) -> Unit,
        ui: SocketManager,
        codeLang: String,
        shouldAutoApply: (Path) -> Boolean
    ): String {
        val filepath = root.resolve(filename)
        if (shouldAutoApply(filepath) && !filepath.toFile().exists()) {
            try {
                newFile(filepath, codeValue, filename, handle)
                return "\n```${codeLang}\n${codeValue}\n```\n\n<div class=\"cmd-button\">Automatically Saved ${filepath}</div>"
            } catch (e: Throwable) {
                return "\n```${codeLang}\n${codeValue}\n```\n\n<div class=\"cmd-button\">Error Auto-Saving ${filename}: ${e.message}</div>"
            }
        } else {
            val commandTask = ui.newTask(false)
            lateinit var hrefLink: StringBuilder
            @Suppress("AssignedValueIsNeverRead")
            hrefLink = commandTask.complete(hrefLink("Save File", classname = "href-link cmd-button") {
                try {
                    newFile(filepath, codeValue, filename, handle)
                    hrefLink.set("""<div class="cmd-button">Saved ${filepath}</div>""")
                    commandTask.complete()
                } catch (e: Throwable) {
                    hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
                    commandTask.error(e)
                }
            })!!
            return "\n```${codeLang}\n${codeValue}\n```\n\n${commandTask.placeholder}\n"
        }
    }

    private fun SocketManager.renderDiffBlock(
        root: Path,
        filename: String,
        diffVal: String,
        handle: (Map<Path, String>) -> Unit,
        ui: SocketManager,
        shouldAutoApply: (Path) -> Boolean,
    ): String {

        val filepath = root.resolve(filename)
        val prevCode = load(filepath)
        val relativize = try {
            root.relativize(filepath)
        } catch (e: Throwable) {
            filepath
        }
        val applydiffTask = ui.newTask(false)
        lateinit var hrefLink: StringBuilder

        val apply = diffApplier.apply(prevCode, "```diff\n$diffVal\n```", filename, processor)
        var newCode = apply.patchResult
        val echoDiff = try {
            processor.generatePatch(prevCode, newCode.newCode)
        } catch (e: Throwable) {
            "\n```\n${e.stackTraceToString()}\n```\n".renderMarkdown()
        }

        fun createRevertButton(filepath: Path, originalCode: String, handle: (Map<Path, String>) -> Unit): String {
            val relativize = try {
                root.relativize(filepath)
            } catch (e: Throwable) {
                filepath
            }
            val revertTask = ui.newTask(false)
            lateinit var revertButton: StringBuilder
            @Suppress("AssignedValueIsNeverRead")
            revertButton = revertTask.complete(hrefLink("Revert", classname = "href-link cmd-button") {
                try {
                    filepath.toFile().writeText(originalCode, Charsets.UTF_8)
                    handle(mapOf(relativize to originalCode))
                    revertButton.set("""<div class="cmd-button">Reverted</div>""")
                    revertTask.complete()
                } catch (e: Throwable) {
                    revertButton.append("""<div class="cmd-button">Error: ${e.message}</div>""")
                    revertTask.error(e)
                }
            })!!
            return revertTask.placeholder
        }

        if (echoDiff.isNotBlank()) {
            if (newCode.isValid) {
                if (shouldAutoApply(filepath ?: root.resolve(filename))) {
                    try {
                        val startTime = Instant.now()
                        filepath.toFile().writeText(newCode.newCode, Charsets.UTF_8)
                        handle(mapOf(relativize to newCode.newCode))
                        logFileOperation(
                            filepath,
                            prevCode,
                            diffVal,
                            newCode.newCode,
                            "AUTO_PATCH",
                            startTime,
                            apply.validator
                        )
                        val revertButton = createRevertButton(filepath, prevCode, handle)
                        return "\n```diff\n$diffVal\n```\n" + """<div class="cmd-button">Diff Automatically Applied to ${filepath}</div>""" + revertButton
                    } catch (e: Throwable) {
                        log.error("Error auto-applying diff", e)
                        return "\n```diff\n$diffVal\n```\n" + """<div class="cmd-button">Error Auto-Applying Diff to ${filepath}: ${e.message}</div>"""
                    }
                }
            }
        }

        val diffTask = ui.newTask(root = false)
        diffTask.complete("\n```diff\n$diffVal\n```\n".renderMarkdown())

        lateinit var revert: String
        lateinit var applyButton: String
        var originalCode = prevCode
        var isApplied = false

        applyButton = hrefLink("Apply Diff", classname = "href-link cmd-button") {
            if (isApplied) return@hrefLink // Prevent re-triggering
            try {
                isApplied = true
                val startTime = Instant.now()
                originalCode = load(filepath)
                newCode = diffApplier.apply(originalCode, "```diff\n$diffVal\n```", processor = processor).patchResult
                filepath.toFile().writeText(newCode.newCode, Charsets.UTF_8)
                handle(mapOf(relativize to newCode.newCode))
                logFileOperation(
                    filepath,
                    originalCode,
                    diffVal,
                    newCode.newCode,
                    "MANUAL_PATCH",
                    startTime,
                    apply.validator
                )
                hrefLink.set("<div class=\"cmd-button\">Diff Applied</div>$revert")
                applydiffTask.complete()
            } catch (e: Throwable) {
                isApplied = false
                hrefLink.set("""<div class="cmd-button">Error: ${e.message}</div>""")
                applydiffTask.error(e)
            }
        }

        val applyDiff = applydiffTask.complete(applyButton)!!
        hrefLink = applyDiff
        @Suppress("AssignedValueIsNeverRead")
        revert = hrefLink("Revert", classname = "href-link cmd-button") {
            try {
                isApplied = false
                filepath.toFile().writeText(originalCode, Charsets.UTF_8)
                handle(mapOf(relativize to originalCode))
                hrefLink.set("""<div class="cmd-button">Reverted</div>""" + applyButton)
                applydiffTask.complete()
            } catch (e: Throwable) {
                hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
                applydiffTask.error(e)
            }
        }

        load(filepath).reverseLines()
        diffVal.reverseLines()
        return if (newCode.isValid) {
            diffTask.placeholder + "\n" + applydiffTask.placeholder
        } else {
            log.warn("Invalid patch for $filename: ${newCode.error}")
            diffTask.placeholder + """<div class="warning">Warning: The patch is not valid: ${newCode.error?.renderMarkdown() ?: "???"}</div>""" + applydiffTask.placeholder
        } + record(
            ui, mapOf(
                "filename" to filename,
                "originalCode" to prevCode,
                "diff" to diffVal,
                "newCode" to newCode.newCode,
                "isValid" to newCode.isValid,
                "errors" to newCode.error,
            )
        )
    }

    private val DiffApplicationResult.patchResult
        get() = PatchResult(
            newCode,
            isValid,
            errors.joinToString("\n") { "* ${it.message} (line ${it.line})" }
        )

    private fun load(filepath: Path?) = loadFile(filepath)
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/ui/AgentPatterns.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.webui.session.SocketManager
import java.util.*

object AgentPatterns {

    fun displayMapInTabs(
        map: Map<String, String>,
        ui: SocketManager? = null,
        split: Boolean = map.entries.map { it.value.length + it.key.length }.sum() > 10000
    ): String = if (split && ui != null) {
        val tasks = map.entries.associate { (key, value) ->
          key to ui.newTask(root = false)
        }
      ui.scheduledThreadPoolExecutor.schedule({
        tasks.forEach { (key, task) ->
          task.complete(map[key]!!)
        }
      }, 200, java.util.concurrent.TimeUnit.MILLISECONDS)
        displayMapInTabs(tasks.mapValues { it.value.placeholder }, ui = ui, split = false)
    } else {
        """
<div class="tabs-container" id="${UUID.randomUUID()}">
<div class="tabs">
${
            map.keys.joinToString("\n") { key ->
                """<button class="tab-button${
                    when {
                        key == map.keys.first() -> " active"
                        else -> ""
                    }
                }" data-for-tab="$key">$key</button>"""
            }
        }
</div>
${
            map.entries.withIndex().joinToString("\n") { (idx, t) ->
                val (key, value) = t
                """
<div class="tab-content${
                    when {
                        idx == 0 -> " active"
                        else -> ""
                    }
                }" data-tab="$key">
$value
</div>
"""
            }
        }
</div>
"""
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/ui/Discussable.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.util.concurrent.Callable
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class Discussable<T : Any>(
    private val task: SessionTask,
    private val userMessage: () -> String,
    private val initialResponse: (String) -> T,
    private val outputFn: (T) -> String,
    private val reviseResponse: (List<Pair<String, Role>>) -> T,
    private val atomicRef: AtomicReference<T?> = AtomicReference(),
    private val semaphore: Semaphore = Semaphore(0),
    private val heading: String,
    private val blocking: Boolean = true,
) : Callable<T> {

    val tabs = object : TabbedDisplay(task) {
        override fun renderTabButtons() = """
<div class="tabs">
${
            tabs.withIndex().joinToString("\n")
            { (index: Int, t: Pair<String, StringBuilder>) ->
                renderButton(index, t.first)
            }
        }
${
            task.ui.hrefLink("♻") {
                val newTask = task.ui.newTask(blocking)
                val header = newTask.header("Retrying...", 4)
                val idx: Int = size
                this.set(label(idx), newTask.placeholder)
                main(idx, newTask)

                header?.clear()
                newTask.complete()
            }
        }

</div>
"""
    }
    private val acceptGuard = AtomicBoolean(blocking)

    private fun main(tabIndex: Int, task: SessionTask) {
        log.info("Starting main function for tabIndex: $tabIndex")
        try {
            val history = mutableListOf<Pair<String, Role>>()
            val userMessage = userMessage()

            history.add(userMessage to Role.user)
            val design = initialResponse(userMessage)

            val rendered = outputFn(design)

            history.add(rendered to Role.assistant)
            val tabContent = task.add(rendered)!!
            val feedbackForm = feedbackForm(tabIndex, tabContent, design, history, task)
            tabContent.append("\n" + feedbackForm.placeholder)
            task.complete()
        } catch (e: Throwable) {
            log.error("Error in discussable", e)
            task.error(e)
            task.complete(task.ui.hrefLink("🔄 Retry") {
                main(tabIndex = tabIndex, task = task)
            })
        }
    }

    private fun feedbackForm(
        tabIndex: Int?,
        tabContent: StringBuilder,
        design: T,
        history: List<Pair<String, Role>>,
        task: SessionTask,
    ) = task.ui.newTask(blocking).apply {
        log.info("Creating feedback form for tabIndex: $tabIndex")
        val feedbackSB = add("<div />")!!
        feedbackSB.clear()
        feedbackSB.append(
            """
<div style="display: flex; flex-direction: column;">
${acceptLink(tabIndex, tabContent, design, feedbackSB, feedbackTask = this)}
</div>
${textInput(tabContent, history, task, feedbackSB, feedbackTask = this)}
"""
        )
        complete()
    }

    private fun acceptLink(
        tabIndex: Int?,
        tabContent: StringBuilder,
        design: T,
        feedbackSB: StringBuilder,
        feedbackTask: SessionTask,
    ) = task.hrefLink("Accept", classname = "href-link cmd-button") {
        log.info("Accept link clicked for tabIndex: $tabIndex")
        feedbackSB.clear()
        feedbackTask.complete()
        accept(tabIndex, tabContent, design)
    }

    private fun textInput(
        tabContent: StringBuilder,
        history: List<Pair<String, Role>>,
        task: SessionTask,
        feedbackSB: StringBuilder,
        feedbackTask: SessionTask,
    ): String {
        val feedbackGuard = AtomicBoolean(blocking)
        return task.ui.textInput { userResponse ->
            log.info("User response received: $userResponse")
            if (feedbackGuard.getAndSet(true)) return@textInput
            val prev = feedbackSB.toString()
            try {
                feedbackSB.clear()
                feedbackTask.complete()
                feedback(tabContent, userResponse, history, task)
            } catch (e: Exception) {
                log.error("Error processing user feedback", e)
                task.error(e)
                feedbackSB.set(prev)
                feedbackTask.complete()
                throw e
            } finally {
                feedbackGuard.set(blocking)
            }
        }
    }

    private fun feedback(
        tabContent: StringBuilder,
        userResponse: String,
        history: List<Pair<String, Role>>,
        task: SessionTask,
    ) {
        log.info("Processing feedback for user response: $userResponse")
        var history = history
        history = history + (userResponse to Role.user)
        val newValue = (tabContent.toString()
            + "<div class=\"user-message\">"
            + userResponse.renderMarkdown(true)
                + "</div>")
        tabContent.set(newValue)
        val stringBuilder = task.add("Processing...")
        tabs.update()
        val newDesign = reviseResponse(history)
        log.info("Revised design: $newDesign")
        val newTask = task.ui.newTask(root = blocking)
        tabContent.set(newValue + "\n" + newTask.placeholder)
        tabs.update()
        stringBuilder?.clear()
        task.complete()
        Retryable(newTask) {
            outputFn(newDesign) + "\n" + feedbackForm(
                tabIndex = null,
                tabContent = it,
                design = newDesign,
                history = history,
                task = newTask
            ).placeholder
        }
    }

    private fun accept(tabIndex: Int?, tabContent: StringBuilder, design: T) {
        log.info("Accepting design for tabIndex: $tabIndex")
        if (acceptGuard.getAndSet(true)) {
            return
        }
        try {

            tabContent.apply {
                val prevTab = toString()
                set(prevTab)
                tabs.update()
            }
        } catch (e: Exception) {
            log.error("Error accepting design", e)
            task.error(e)
            acceptGuard.set(blocking)
            throw e
        }
        atomicRef.set(design)
        semaphore.release()
    }

    override fun call(): T? {
        try {

            if (heading.isNotBlank()) task.echo(heading)
            val idx = tabs.size
            val newTask = task.ui.newTask(blocking)
            val header = newTask.header("Processing...", 4)
            tabs[tabs.label(idx)] = newTask.placeholder
            try {
                main(idx, newTask)

                if (blocking) semaphore.acquire()
            } catch (e: Throwable) {
                log.error("Error in main function", e)
                task.error(e)
            } finally {
                header?.clear()
                newTask.complete()
            }
            log.info("Returning result from Discussable")
            return atomicRef.get()
        } catch (e: Exception) {
            log.warn(
                """
Error in Discussable
${e.message}
""", e
            )
            task.error(e)
            return null
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Discussable::class.java)
    }
}

fun java.lang.StringBuilder.set(newValue: String) {
    clear()
    append(newValue)
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/ui/Retryable.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager

open class Retryable(
    task: SessionTask,
    val process: (StringBuilder) -> String
) : TabbedDisplay(task) {

//    constructor(
//        task: SessionTask,
//        process: (SessionTask) -> Unit?,
//    ) : this(task, process.async(task.ui))

    init {
        init()
    }

    open fun init() {
        val tabLabel = label(size)
        set(tabLabel, SessionTask.spinner)
        set(tabLabel, process(container))
    }

    fun retry() {
        val idx = tabs.size
        val label = label(idx)
        val content = StringBuilder("Retrying..." + SessionTask.spinner)
        tabs.add(label to content)
        update()
        val newResult = process(content)
        content.clear()
        set(label, newResult)
    }

    override fun renderTabButtons(): String = """
<div class="tabs">${
        tabs.withIndex().joinToString("\n") { (index, pair) ->
            renderButton(index, pair.first)
        }
    }${
        task.ui.hrefLink(
            "♻",
            """href-link""",
            null,
            oneAtATime { it: Unit -> retry() })
    }
</div>
"""

    companion object {
        fun ((SessionTask) -> Unit?).async(
            socketManager: SocketManager,
            pool: ImmediateExecutorService = socketManager.pool
        ): (StringBuilder) -> String = {
            val task = socketManager.newTask(false)
            pool.submit {
                this(task)
            }
            task.placeholder
        }
    }
}

```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/ui/TabbedDisplay.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.webui.session.SessionTask
import java.util.*

open class TabbedDisplay(
    val task: SessionTask,
    val tabs: MutableList<Pair<String, StringBuilder>> = mutableListOf(),
    val additionalClasses: String = "",
    val closable: Boolean = true,
    val id: UUID = UUID.randomUUID(),
) {

    fun newTask(label: String = label(tabs.size)): SessionTask {
        log.debug("Creating new tab with label: $label")
        val task = task.ui.newTask(false)
        this[label] = task.placeholder
        return task
    }

    var selectedTab: Int = 0

    companion object {
        val log = LoggerFactory.getLogger(TabbedDisplay::class.java)
    }

    val size: Int get() = tabs.size
    private fun render() = if (tabs.isEmpty()) "<div/>" else {
        """
  <div class="${
            (additionalClasses.split(" ").toSet() + setOf("tabs-container")).filter { it.isNotEmpty() }
                .joinToString(" ")
        }" id="$id">
  ${renderTabButtons()}
  ${
            tabs.toTypedArray().withIndex().joinToString("\n")
            { (idx, t) -> renderContentTab(t, idx) }
        }
  </div>
  """
    }

    val container: StringBuilder by lazy {
        log.debug("Initializing container with rendered content")
        task.add(render())!!
    }

    protected open fun renderTabButtons() = """<div class="tabs">${
        tabs.toTypedArray().withIndex().joinToString("\n") { (idx, pair) ->
            renderButton(idx, pair.first)
        }
    }</div>"""

    protected open fun renderButton(idx: Int, label: String): String {
        val buttonHtml = if (idx == selectedTab) {
            """<button class="tab-button active" data-for-tab="$idx">$label</button>"""
        } else {
            """<button class="tab-button" data-for-tab="$idx">$label</button>"""
        }
        val closeButton = if (idx <= 1 || !closable) "" else task.hrefLink("✖️") {
            tabs.removeAt(idx)
            update()
        }
        return buttonHtml + closeButton
    }

    protected open fun renderContentTab(t: Pair<String, StringBuilder>, idx: Int) = """<div class="${
        (additionalClasses.split(" ") + setOf("tab-content") + when {
            idx == selectedTab -> setOf("active")
            else -> emptySet()
        }).filter { it.isNotEmpty() }.joinToString(" ")
    }" data-tab="$idx">${t.second}</div>"""

    operator fun get(i: String) = tabs.toMap()[i]
    operator fun set(name: String, content: String) =
        when (val index = find(name)) {
            null -> {
                log.debug("Adding new tab: $name")
                val stringBuilder = StringBuilder(content)
                tabs.add(name to stringBuilder)
                update()
                stringBuilder
            }

            else -> {
                log.debug("Updating existing tab: $name")
                val stringBuilder = tabs[index].second
                stringBuilder.clear()
                stringBuilder.append(content)
                update()
                stringBuilder
            }
        }

    fun find(name: String) = tabs.withIndex().firstOrNull { it.value.first == name }?.index

    open fun label(i: Int): String {
        return "${tabs.size + 1}"
    }

    open fun delete(name: String): Boolean {
        log.debug("Deleting tab: $name")
        val index = find(name)
        return if (index != null) {
            tabs.removeAt(index)
            update()
            true
        } else {
            false
        }
    }

    open fun clear() {
        log.debug("Clearing all tabs")
        tabs.clear()
        update()
    }

    open fun update() {
        log.debug("Updating container content")
        synchronized(container) {
            if (tabs.isNotEmpty() && (selectedTab < 0 || selectedTab >= tabs.size)) {
                selectedTab = 0
            }
            container.clear()
            container.append(render())
        }
        task.update()
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

Here is the new README.md file for the UI components package, based on the provided source files and the transformation rules.

### webui/src/main/kotlin/com/simiacryptus/cognotik/ui/README.md
```markdown
# UI Components and Utilities

This package provides the core interactive UI components and utilities for the Cognotik web interface. These classes facilitate complex interactions between the AI agent and the user, including real-time diff application, multi-turn discussions, and dynamic tabbed layouts.

## Core Components

### Diff Application
*   **AddApplyDiffLinks**: Scans markdown responses for diff blocks and injects interactive "Apply Diff" links. It supports:
    *   Automatic application of valid patches.
    *   Reverse patching (bottom-to-top) for complex diffs.
    *   Validation and verification tabs to preview changes.
*   **AddApplyFileDiffLinks**: Extends diff application to the file system. It handles:
    *   Resolving markdown headers to physical file paths.
    *   Creating new files from code blocks.
    *   Applying patches to existing files with logging and revert capabilities.
    *   Instrumentation of AI responses to add "Save" and "Apply" buttons.

### Interactive Interaction Patterns
*   **Discussable**: A high-level component for multi-turn refinement loops. It allows the user to:
    *   View an initial AI response.
    *   Provide feedback for revision.
    *   "Accept" a design or "Retry" the generation.
    *   Navigate through the history of the discussion via tabs.
*   **Retryable**: A specialized display for asynchronous tasks that provides a "Retry" (♻) button. Each retry attempt is rendered in a new tab, allowing users to compare different outputs.

### Layout and Display Utilities
*   **TabbedDisplay**: The foundational class for all multi-tab UI elements. It manages:
    *   Dynamic addition and deletion of tabs.
    *   Real-time UI updates via `SocketManager`.
    *   Rendering of tab buttons and content containers with unique IDs.
*   **AgentPatterns**: Contains utility methods like `displayMapInTabs`, which can render a map of strings into a tabbed interface, optionally splitting large content into separate background tasks for better performance.

## Implementation Details

### UI Synchronization
Most components in this package interact with `SocketManager` and `SessionTask`. They use placeholders and real-time updates to ensure the web interface reflects the current state of background processing without requiring manual page refreshes.

### File Operation Logging
`AddApplyFileDiffLinks` includes a robust logging mechanism for file operations. When enabled, it records:
*   Operation type (NEW_FILE, AUTO_PATCH, MANUAL_PATCH).
*   Original and new code content.
*   Execution duration and stack traces.
*   Validation results.

### Filename Normalization
The `AddApplyFileDiffLinks` class includes sophisticated logic to extract and normalize filenames from various markdown header patterns (e.g., "File: path/to/file.kt", "Modified: filename.py"), ensuring reliable file system mapping even with varied AI output formats.

## Usage
These components are typically used within AI agent loops to wrap generated content before it is sent to the user. For example, `AddApplyFileDiffLinks.instrumentFileDiffs` is called on the final markdown response to make any suggested code changes interactive.
```

### Summary of Changes
- Created a new `README.md` in `webui/src/main/kotlin/com/simiacryptus/cognotik/ui/` as specified by the transformation rule in `index_docs.md`.
- Documented the primary responsibilities of the six Kotlin files provided:
    - **Diff Application**: `AddApplyDiffLinks`, `AddApplyFileDiffLinks`.
    - **Interactive Patterns**: `Discussable`, `Retryable`.
    - **Layout**: `TabbedDisplay`, `AgentPatterns`.
- Highlighted key technical features such as real-time UI synchronization, file operation logging, and filename normalization logic found in the source code.
- Organized the documentation into logical sections (Core Components, Implementation Details, Usage) for better maintainability.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/ui/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/ui/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/ui/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/ui/README.md</a> Updated
