package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.diff.DiffApplicationResult
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchResult
import com.simiacryptus.cognotik.diff.SimpleDiffApplier
import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.resolveSystemFile
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
        ): String {
            log.debug("Instrumenting file diffs for root: {}", root)
            return AddApplyFileDiffLinks(processor).instrument(
                self = self,
                root = root,
                response = response,
                handle = handle,
                shouldAutoApply = shouldAutoApply,
                model = model,
                defaultFile = defaultFile
            )
        }

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
        val relativePath = UUID.randomUUID().toString() + ".json"
        require(relativePath.isNotBlank()) { "File path cannot be blank" }
        socketManager.resolveSystemFile(relativePath)?.writeText(data.toJson())
        return "<a href='fileIndex/${socketManager.sessionId}/$relativePath' target='_blank' class='verbose'>Patch Data</a>"
    }

    fun instrument(
        self: SocketManager,
        root: Path,
        response: String,
        handle: (Map<Path, String>) -> Unit = {},
        shouldAutoApply: (Path) -> Boolean = { false },
        model: ChatInterface? = null,
        defaultFile: String? = null,
        resolver: ((Path, String) -> String?) = ::resolveToRelativePath,
    ): String {

        self.apply {
            val initiator = getInitiatorPattern()
            if (response.contains(initiator) && !response.split(initiator, 2)[1].contains("\n```(?![^\n])".toRegex())) {
                return@instrument instrument(
                    self = self,
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

            fun isFileResolvable(header: String?): Boolean {
                try {
                    val prefiltered = prefilterFilename(normalizeFilename(header ?: "")) ?: ""
                    val resolvedPath = resolver(root, prefiltered) ?: return (true != header?.contains('.') && null != defaultFile)
                    if (root.resolve(resolvedPath).toFile().exists()) return true
                    if(!resolvedPath.contains('.') && null != defaultFile) return true // Allow default file for extensionless paths (likely to be a mis-parse)
                    return false
                } catch (e: Throwable) {
                    log.info("Error processing code block", e)
                    return false
                }
            }

            val newFileBlocks = codeBlocksWithHeaders.filter { (header, lang, code) -> !isFileResolvable(header) }
            val patchBlocks = codeBlocksWithHeaders.filter { (header, lang, code) -> isFileResolvable(header) }

            val withPatchLinks: String = patchBlocks.reversed().fold(response) { markdown, (header, lang, diffValue) ->
                var normalizeFilename = normalizeFilename(header ?: "")
                if (normalizeFilename.isBlank() || !normalizeFilename.contains('.')) {
                    if(defaultFile == null) { return@fold markdown }
                    normalizeFilename = defaultFile
                }
                val filename = resolver(root, normalizeFilename) ?: return@fold markdown
                val newValue = renderDiffBlock(root, filename, diffValue, handle, self, shouldAutoApply)
                val startOfMatch = markdown.indexOf(diffValue)
                if (startOfMatch < 0) {
                    return@fold markdown
                }
                val endOfMatch = startOfMatch + diffValue.length
                val precedingText = markdown.substring(0, startOfMatch)
                val followingText = markdown.substring(endOfMatch)
                val prependLength =
                    precedingText.lastIndexOf("```").let { if (it >= 0) precedingText.length - it else 0 }
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
                        renderNewFile(root, filename, processedCode, handle, self, lang, shouldAutoApply) + record(
                            self, mapOf(
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
        return filename.trim()
            // Remove common prefixes
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
            // Clean up whitespace
            .trim()
            // Remove markdown formatting
            .replace("**", "")
            .replace("*", "")
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