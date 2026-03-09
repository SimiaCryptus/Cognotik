package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import java.nio.file.Path
import java.nio.file.Files
import kotlin.io.path.name
import kotlin.io.path.exists

class DiffInstrumentor(
    private val processor: PatchProcessor,
    private val renderer: DiffUIRenderer,
    private val fs: FileSystem = RealFileSystem()
) {
    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(DiffInstrumentor::class.java)
    }

    private val parser = ResponseParser(processor)

    fun instrument(
        root: Path,
        response: String,
        handle: (Map<Path, String>) -> Unit = {},
        shouldAutoApply: (Path) -> Boolean = { false },
        defaultFile: String? = null,
        resolver: (Path, String) -> String? = ::resolveToRelativePath,
    ): String {
        log.debug("Instrumenting response: {} chars, root={}, defaultFile={}", response.length, root, defaultFile)
        if (response.isBlank()) {
            log.warn("Empty response provided to instrument()")
            return response
        }
        val segments = parser.parse(response, defaultFile)
        if (segments.isEmpty()) {
            log.debug("No segments parsed from response")
            return response
        }
        log.debug("Parsed {} segments from response", segments.size)

        val result = StringBuilder()
        for (segment in segments) {
            when (segment) {
                is ResponseSegment.Markdown -> result.appendLine(segment.content)
                is ResponseSegment.NewFileBlock -> {
                    val filename = prefilterFilename(segment.filename) ?: segment.filename
                    log.debug("Processing new file block: filename={}, language={}, code length={}", filename, segment.language, segment.code.length)
                    if (filename.isBlank()) {
                        log.warn("Blank filename after prefiltering for new file block, rendering as code block")
                        result.appendLine("```${segment.language}\n${segment.code}\n```")
                        continue
                    }
                    val resolved = resolveWithBestEffort(root, filename, resolver)
                    if (resolved == null) {
                        result.appendLine("```${segment.language}\n${segment.code}\n```")
                        continue
                    }
                    val filepath = fs.resolve(root, resolved)
                    log.debug("Resolved new file path: {}", filepath)
                    result.appendLine(renderNewFile(filepath, segment.code, segment.language, handle, shouldAutoApply))
                }

                is ResponseSegment.DiffBlock -> {
                    var filename = segment.filename
                    log.debug("Processing diff block: filename={}, diff length={}", filename, segment.diff.length)
                    if (filename.isBlank() || !filename.contains('.')) {
                        if (defaultFile != null) filename = defaultFile
                        else {
                            log.warn("Blank or extensionless filename '{}' with no default file, rendering as diff code block", segment.filename)
                            result.appendLine("```diff\n${segment.diff}\n```")
                            continue
                        }
                    }
                    val resolved = resolveWithBestEffort(root, filename, resolver)
                    if (resolved == null) {
                        result.appendLine("```diff\n${segment.diff}\n```")
                        continue
                    }
                    val filepath = fs.resolve(root, resolved)
                    val relativize = try {
                        root.relativize(filepath)
                    } catch (e: Throwable) {
                        log.warn("Could not relativize path {} against root {}: {}", filepath, root, e.message)
                        filepath
                    }
                    result.appendLine(renderDiffBlock(filepath, relativize, segment.diff, handle, shouldAutoApply))
                }
            }
        }
        return result.toString()
    }
    /**
     * Attempts to resolve a filename against the root using multiple strategies:
     * 1. Direct resolution via the provided resolver
     * 2. Strip common path prefixes (e.g., "a/", "b/") used by git diffs
     * 3. Search for files matching the filename anywhere under root
     * 4. Try resolving just the last N path components
     *
     * Logs detailed diagnostics when resolution fails.
     */
    private fun resolveWithBestEffort(
        root: Path,
        filename: String,
        resolver: (Path, String) -> String?
    ): String? {
        // Strategy 1: Direct resolution
        val direct = resolver(root, filename)
        if (direct != null) {
            log.debug("Resolved '{}' directly against root '{}'", filename, root)
            return direct
        }
        log.debug("Direct resolution failed for '{}' against root '{}', trying alternative strategies", filename, root)
        // Strategy 2: Strip common diff prefixes (a/, b/) used by git
        val strippedPrefixes = listOf("a/", "b/", "src/", "./")
        for (prefix in strippedPrefixes) {
            if (filename.startsWith(prefix)) {
                val stripped = filename.removePrefix(prefix)
                val resolved = resolver(root, stripped)
                if (resolved != null) {
                    log.info("Resolved '{}' by stripping prefix '{}' -> '{}' against root '{}'", filename, prefix, stripped, root)
                    return resolved
                }
            }
        }
        // Strategy 3: Try resolving progressively shorter suffixes of the path
        val parts = filename.replace("\\", "/").split("/")
        if (parts.size > 1) {
            for (i in 1 until parts.size) {
                val suffix = parts.subList(i, parts.size).joinToString("/")
                val resolved = resolver(root, suffix)
                if (resolved != null) {
                    log.info("Resolved '{}' using path suffix '{}' against root '{}'", filename, suffix, root)
                    return resolved
                }
            }
        }
        // Strategy 4: Walk the file tree to find a matching filename
        val targetName = Path.of(filename.replace("\\", "/")).fileName?.toString()
        if (targetName != null && targetName.contains('.')) {
            try {
                val candidates = mutableListOf<Path>()
                Files.walk(root, 10).use { stream ->
                    stream.filter { it.name == targetName }
                        .forEach { candidates.add(it) }
                }
                when {
                    candidates.size == 1 -> {
                        val match = root.relativize(candidates[0]).toString()
                        log.info(
                            "Resolved '{}' by filesystem search: found unique match '{}' under root '{}'",
                            filename, match, root
                        )
                        return match
                    }
                    candidates.size > 1 -> {
                        // Try to disambiguate by matching the most path components from the end
                        val filenameParts = filename.replace("\\", "/").split("/")
                        val bestMatch = candidates.maxByOrNull { candidate ->
                            val candidateParts = root.relativize(candidate).toString().replace("\\", "/").split("/")
                            filenameParts.reversed().zip(candidateParts.reversed()).takeWhile { (a, b) -> a == b }.count()
                        }
                        if (bestMatch != null) {
                            val match = root.relativize(bestMatch).toString()
                            log.info(
                                "Resolved '{}' by filesystem search: best match '{}' among {} candidates under root '{}'. All candidates: {}",
                                filename, match, candidates.size, root,
                                candidates.map { root.relativize(it).toString() }
                            )
                            return match
                        }
                    }
                }
            } catch (e: Throwable) {
                log.debug("Filesystem search failed for '{}' under root '{}': {}", filename, root, e.message)
            }
        }
        // Strategy 5: If the file doesn't exist yet, treat it as a new file path relative to root
        val directPath = root.resolve(filename).normalize()
        if (directPath.startsWith(root)) {
            // The path is within the root, so it could be a new file
            val relativePath = root.relativize(directPath).toString()
            log.info(
                "Could not find existing file for '{}' under root '{}'. " +
                    "Treating as new file path: '{}'. " +
                    "If this is a diff for an existing file, the file may have been renamed or the path in the response is incorrect.",
                filename, root, relativePath
            )
            return relativePath
        }
        // All strategies exhausted
        log.error(
            "Failed to resolve filename '{}' against root '{}'. " +
                "Strategies attempted: direct resolution, prefix stripping ({}), path suffix matching, " +
                "filesystem search (target='{}'), direct path resolution. " +
                "The file reference in the AI response could not be matched to any file in the project.",
            filename, root, strippedPrefixes.joinToString(", "), targetName
        )
        return null
    }


    private fun renderNewFile(
        filepath: Path,
        code: String,
        lang: String,
        handle: (Map<Path, String>) -> Unit,
        shouldAutoApply: (Path) -> Boolean
    ): String {
        log.debug("Rendering new file: path={}, lang={}, code length={}", filepath, lang, code.length)
        val codeBlock = "\n```${lang}\n${code}\n```\n"
        if (code.isBlank()) {
            log.warn("Empty code content for new file: {}", filepath)
        }
        if (shouldAutoApply(filepath) && !fs.exists(filepath)) {
            log.info("Auto-applying new file: {}", filepath)
            return try {
                fs.writeText(filepath, code)
                handle(mapOf(filepath to code))
                log.info("Successfully auto-created file: {}", filepath)
                codeBlock + "\n" + renderer.renderAutoApplied(
                    filepath,
                    ""
                ) + renderer.recordPatch(
                    mapOf(
                        "filename" to filepath.toString(),
                        "code" to code,
                        "action" to "save_auto"
                    )
                )
            } catch (e: Throwable) {
                log.error("Error auto-saving file {}: {}", filepath, e.message, e)
                codeBlock + "\n<div class=\"cmd-button\">Error Auto-Saving ${filepath}: ${e.message}</div>"
            }
        }
        val saveButton = renderer.renderSaveButton(filepath, code, lang) {
            log.info("User triggered save for file: {}", filepath)
            fs.writeText(filepath, code)
            handle(mapOf(filepath to code))
        }
        return codeBlock + "\n" + saveButton + renderer.recordPatch(
            mapOf(
                "filename" to filepath.toString(),
                "code" to code,
                "action" to "save"
            )
        )
    }

    private fun renderDiffBlock(
        filepath: Path,
        relativePath: Path,
        diffVal: String,
        handle: (Map<Path, String>) -> Unit,
        shouldAutoApply: (Path) -> Boolean
    ): String {
        log.debug("Rendering diff block: filepath={}, relativePath={}, diff length={}", filepath, relativePath, diffVal.length)
        if (diffVal.isBlank()) {
            log.warn("Empty diff content for file: {}", filepath)
            return "\n```diff\n${diffVal}\n```\n" + renderer.renderWarning("Empty diff content")
        }
        val controller = DiffApplyController(filepath, diffVal, processor, fs)
        val prevCode = fs.readText(filepath)
        log.debug("Read previous code from {}: {} chars", filepath, prevCode.length)

        // Validate the patch
        val testResult = try {
            processor.apply(prevCode, "```diff\n$diffVal\n```", filepath.fileName?.toString())
        } catch (e: Throwable) {
            log.warn("Exception during patch validation for {}: {}", filepath, e.message, e)
            null
        }
        val isValid = testResult?.isValid ?: false
        val errorMsg = testResult?.errors?.joinToString("; ") { it.message }
        log.debug("Patch validation for {}: isValid={}, errors={}", filepath, isValid, errorMsg)

        val diffBlock = "\n```diff\n$diffVal\n```\n"

        // Auto-apply path
        val shouldAutoApplyResult = shouldAutoApply(filepath)
        log.debug("shouldAutoApply({})={}, isValid={}", filepath, shouldAutoApplyResult, isValid)
        return if (isValid && shouldAutoApplyResult) {
            log.info("Auto-applying diff to {}", filepath)
            val state = controller.apply()
            when (state) {
                is ApplyState.Applied -> {
                    handle(mapOf(relativePath to state.newCode))
                    log.info("Successfully auto-applied diff to {}", filepath)
                    val revertButton = renderer.renderApplyDiffButton(filepath, diffVal, onApply = {}, onRevert = {
                        log.info("User triggered revert for auto-applied diff: {}", filepath)
                        controller.revert()
                        handle(mapOf(relativePath to state.originalCode))
                    })
                    diffBlock + renderer.renderAutoApplied(filepath, revertButton) + renderer.recordPatch(
                        mapOf(
                            "filename" to filepath.toString(),
                            "originalCode" to prevCode,
                            "diff" to diffVal,
                            "newCode" to state.newCode,
                            "isValid" to true,
                            "action" to "auto_apply"
                        )
                    )
                }

                is ApplyState.Failed -> {
                    log.error("Failed to auto-apply diff to {}: {}", filepath, state.error.message)
                    diffBlock + "<div class=\"cmd-button\">Error Auto-Applying Diff to ${filepath}: ${state.error.message}</div>"
                }

                else -> {
                    log.warn("Unexpected state after auto-apply attempt for {}: {}", filepath, state::class.simpleName)
                    diffBlock
                }
            }
        } else {
            diffBlock + (if (!isValid && errorMsg != null) {
                log.debug("Rendering validation warning for {}: {}", filepath, errorMsg)
                renderer.renderWarning("The patch is not valid: $errorMsg")
            } else "") + renderer.renderApplyDiffButton(filepath, diffVal, onApply = {
                log.info("User triggered apply diff for {}", filepath)
                val state = controller.apply()
                if (state is ApplyState.Applied) {
                    handle(mapOf(relativePath to state.newCode))
                    log.info("User successfully applied diff to {}", filepath)
                } else if (state is ApplyState.Failed) {
                    log.error("User apply diff failed for {}: {}", filepath, state.error.message)
                    throw state.error
                }
            }, onRevert = {
                log.info("User triggered revert for {}", filepath)
                val currentState = controller.currentState()
                if (currentState is ApplyState.Applied) {
                    controller.revert()
                    handle(mapOf(relativePath to currentState.originalCode))
                    log.info("User successfully reverted diff for {}", filepath)
                } else {
                    log.warn("Cannot revert, current state for {} is {}", filepath, currentState::class.simpleName)
                }
            }) + renderer.recordPatch(
                mapOf(
                    "filename" to filepath.toString(),
                    "originalCode" to prevCode,
                    "diff" to diffVal,
                    "newCode" to (testResult?.newCode ?: ""),
                    "isValid" to isValid,
                    "errors" to errorMsg,
                    "action" to "diff"
                )
            )
        }
    }
}