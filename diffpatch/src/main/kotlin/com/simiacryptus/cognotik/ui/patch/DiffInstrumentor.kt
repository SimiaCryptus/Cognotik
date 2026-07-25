package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchParser.Companion.TRIPLE_TILDE
import com.simiacryptus.cognotik.diff.PatchParser.ResponseSegment
import com.simiacryptus.cognotik.diff.PatchProcessor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class DiffInstrumentor(
  private val processor: PatchProcessor,
  private val renderer: DiffUIRenderer,
  private val fs: FileSystem = RealFileSystem(),
  val allowInvalid: Boolean = true,
  val forceChildPaths: Boolean = false
) {
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(DiffInstrumentor::class.java)
  }

  fun instrument(
    root: Path,
    response: String,
    handle: (Map<Path, String>) -> Unit = {},
    shouldAutoApply: (Path) -> Boolean = { false },
    defaultFile: String? = null,
    resolver: (Path, String) -> String?,
    prefilterFilename: (String) -> String?,
  ): String {
    /** Shadows the class logger: captures this call's messages for the Patch Data dump. */
    val log = PatchTrace("instrument(root=$root)", log)
    log.debug("Instrumenting response: {} chars, root={}, defaultFile={}", response.length, root, defaultFile)
    if (response.isBlank()) {
      log.warn("Empty response provided to instrument()")
      return response
    }
    val segments = processor.parse(response, defaultFile)
    if (segments.isEmpty()) {
      log.debug("No segments parsed from response")
      return response
    }
    log.debug("Parsed {} segments from response", segments.size)

    val result = StringBuilder()
    val changes = mutableListOf<PendingChange>()
    for (segment in segments) {
      when (segment) {
        is ResponseSegment.Markdown -> result.append(segment.removeCodeFences().trimEnd()).append("\n\n")
        is ResponseSegment.NewFileBlock -> {
          val rawFilename = segment.filename ?: ""
          val filename = prefilterFilename(rawFilename) ?: rawFilename
          log.debug(
            "Processing new file block: filename={}, language={}, code length={}",
            filename,
            segment.language,
            segment.removeCodeFences().length
          )
          if (filename.isBlank()) {
            log.warn("Blank filename after prefiltering for new file block, rendering as code block")
            result.append("```${segment.language}\n${segment.removeCodeFences()}\n```").append("\n\n")
            result.append(renderer.renderWarning("The new file block could not be associated with a valid filename. Please ensure the filename is included and has an extension."))
              .append("\n\n")
            continue
          }
          val resolved = resolveNewFilePath(root, filename, resolver, log)
          if (resolved == null) {
            result.append("```${segment.language}\n${segment.removeCodeFences()}\n```").append("\n\n")
            result.append(renderer.renderWarning("The new file block's filename '${filename}' could not be resolved to a valid path. Please ensure the filename is correct and has an extension."))
              .append("\n\n")
            continue
          }
          val filepath = fs.resolve(root, resolved)
          log.debug("Resolved new file path: {}", filepath)
          result.append(
            renderNewFile(
              filepath,
              segment.removeCodeFences(),
              segment.language,
              handle,
              shouldAutoApply,
              changes = changes,
              trace = log
            ).trimEnd()
          ).append("\n\n")
        }

        is ResponseSegment.DiffBlock -> {
          var filename = segment.calcFilename(root)?.toString() ?: defaultFile!!
          log.debug("Processing diff block: filename={}, diff length={}", filename, segment.removeCodeFences().length)
          if (filename.isBlank() || !filename.contains('.')) {
            if (defaultFile != null) filename = defaultFile
            else {
              log.warn(
                "Blank or extensionless filename '{}' with no default file, rendering as diff code block",
                segment.filename
              )
              result.append("```diff\n${segment.removeCodeFences()}\n```").append("\n\n")
              result.append(renderer.renderWarning("The diff block could not be associated with a valid filename. Please ensure the filename is included and has an extension, or provide a default file."))
                .append("\n\n")
              continue
            }
          }
          val resolved = resolveWithBestEffort(root, filename, resolver, log)
          if (resolved == null) {
            // Treat as "create file" by applying the diff to blank content
            log.info("File '{}' not found, treating diff as new file creation", filename)
            val diffContent = segment.removeCodeFences()
            val stdDiffContent = "```diff\n${diffContent}\n```"
            val applyResult = try {
              processor.apply("", stdDiffContent, filename)
            } catch (e: Throwable) {
              log.warn("Failed to apply diff to blank content for new file '{}': {}", filename, e.message, e)
              result.appendLine(renderer.renderWarning("Error applying diff to create new file '${filename}': ${e.message}. Rendering diff as code block."))
              null
            }
            if (applyResult != null && applyResult.newCode.isNotBlank() && (applyResult.isValid || allowInvalid)) {
              // Resolve the path for the new file
              val newFileResolved = resolveNewFilePath(root, filename, resolver, log)
              if (newFileResolved != null) {
                val filepath = fs.resolve(root, newFileResolved)
                log.debug("Creating new file from diff: {}", filepath)
                val lang = filepath.name.substringAfterLast('.', "")
                result.append(
                  renderNewFile(
                    filepath, applyResult.newCode, lang, handle, shouldAutoApply,
                    changes = changes, trace = log
                  ).trimEnd()
                )
                  .append("\n\n")
              } else {
                log.warn("Could not resolve new file path for '{}', rendering as diff code block", filename)
                result.append(stdDiffContent).append("\n\n")
                result.append(renderer.renderWarning("The diff appears to be for creating a new file '${filename}', but the filename could not be resolved to a valid path. The content of the diff is included above for reference."))
                  .append("\n\n")
              }
            } else {
              log.warn(
                "Could not apply diff to blank content for '{}' (isValid={}, errors={}), rendering as diff code block",
                filename,
                applyResult?.isValid,
                applyResult?.errors?.joinToString("; ") { it.message }
              )
              result.append(stdDiffContent).append("\n\n")
              // Add a note to the output about the failure to apply the diff, which may help the user understand why the file wasn't created
              result.append(renderer.renderWarning("The diff could not be applied to create the file '${filename}'. This may be because the diff format is invalid or not compatible with creating a new file. The content of the diff is included above for reference."))
                .append("\n\n")
            }
            continue
          }
          val filepath = fs.resolve(root, resolved)
          val relativize = try {
            root.relativize(filepath)
          } catch (e: Throwable) {
            log.warn("Could not relativize path {} against root {}: {}", filepath, root, e.message)
            filepath
          }
          result.append(
            renderDiffBlock(
              filepath,
              relativize,
              segment.removeCodeFences(),
              handle,
              shouldAutoApply,
              changes = changes,
              trace = log
            ).trimEnd()
          ).append("\n\n")
        }
      }
    }
    appendChangeSummary(result, changes, log)
    return result.toString()
  }

  /**
   * Appends a summary of every file the response touches, plus an "Apply All" control which
   * applies all still-pending changes. Individual failures are collected so one bad patch does
   * not prevent the rest from being applied.
   */
  private fun appendChangeSummary(
    result: StringBuilder,
    changes: List<PendingChange>,
    log: PatchTrace
  ) {
    if (changes.isEmpty()) return
    val pending = changes.filter { it.apply != null }
    log.debug("Rendering change summary: {} change(s), {} pending", changes.size, pending.size)
    val onApplyAll: (() -> Unit)? = if (pending.isEmpty()) null else ({
      var failures = 0
      pending.forEach { change ->
        try {
          change.apply?.invoke()
        } catch (e: Throwable) {
          failures++
          log.error("Apply-all failed for {}: {}", change.summary.path, e.message, e)
        }
      }
      if (failures > 0) throw RuntimeException(
        "$failures of ${pending.size} change(s) could not be applied; use the per-file buttons for details"
      )
    })
    result.append(renderer.renderChangeSummary(changes.map { it.summary }, onApplyAll)).append("\n\n")
  }

  private fun newFileSummary(filepath: Path, code: String, applied: Boolean) = FileChangeSummary(
    path = filepath,
    relativePath = filepath.fileName ?: filepath,
    changeType = ChangeType.NEW_FILE,
    linesAdded = if (code.isBlank()) 0 else code.lines().size,
    linesRemoved = 0,
    isValid = true,
    applied = applied
  )

  /**
   * Resolves a filename for a new file block. This is intentionally less aggressive
   * than diff resolution — we only try direct resolution and, if the file doesn't
   * exist yet, treat it as a new file path relative to root. We do NOT search the
   * filesystem for similarly-named files, since that could cause a new file's content
   * to overwrite an unrelated existing file.
   */
  private fun resolveNewFilePath(
    root: Path,
    filename: String,
    resolver: (Path, String) -> String?,
    log: PatchTrace
  ): String? {
    val updirCount = filename.split("/").takeWhile { it == ".." }.size
    val trimmedFilename = filename.split("/").dropWhile { it == ".." }.joinToString("/")
    val currentDirRelPath =
      root.toFile().absolutePath.split(File.separator).takeLast(updirCount).joinToString("/") + File.separator
    if (trimmedFilename.startsWith(currentDirRelPath)) {
      val stripped = trimmedFilename.removePrefix(currentDirRelPath)
      log.debug(
        "Stripping leading '../{}' from filename '{}' to resolve new file path: '{}'",
        currentDirRelPath,
        filename,
        stripped
      )
      return resolveNewFilePath(root, stripped, resolver, log)
    }

    // First, check for overlap between filename components and root's trailing components.
    // This must happen before direct resolution to avoid duplicating path components
    // e.g., filename="generated_app/ops/file.md", root ends with "generated_app/ops"
    val filenameParts = filename.replace("\\", "/").split("/")
    val rootParts = root.toString().replace("\\", "/").split("/")
    var bestOverlap = 0
    for (overlapLen in minOf(filenameParts.size - 1, rootParts.size) downTo 1) {
      val rootSuffix = rootParts.subList(rootParts.size - overlapLen, rootParts.size)
      val filenamePrefix = filenameParts.subList(0, overlapLen)
      if (rootSuffix == filenamePrefix) {
        bestOverlap = overlapLen
        break
      }
    }

    if (bestOverlap > 0) {
      val effectiveRelativePath = filenameParts.subList(bestOverlap, filenameParts.size).joinToString("/")
      val directPath = root.resolve(effectiveRelativePath).normalize()
      if (directPath.startsWith(root)) {
        val relativePath = root.relativize(directPath).toString()
        log.info(
          "Treating '{}' as new file path with {}-component overlap with root: '{}'",
          filename, bestOverlap, relativePath
        )
        return relativePath
      }
    }

    // Strategy 1: Direct resolution (only succeeds if file already exists)
    val direct = resolver(root, filename)
    if (direct != null) {
      log.debug("Resolved new file '{}' directly against root '{}'", filename, root)
      return direct
    }
    // Strategy 2: Strip common diff prefixes and try direct resolution
    val strippedPrefixes = listOf("a/", "b/", "src/", "./")
    for (prefix in strippedPrefixes) {
      if (filename.startsWith(prefix)) {
        val stripped = filename.removePrefix(prefix)
        val resolved = resolver(root, stripped)
        if (resolved != null) {
          log.info("Resolved new file '{}' by stripping prefix '{}' -> '{}'", filename, prefix, stripped)
          return resolved
        }
      }
    }
    // Strategy 3: Treat as a new file path relative to root (no filesystem search)
    val effectiveRelativePath = if (bestOverlap > 0) {
      filenameParts.subList(bestOverlap, filenameParts.size).joinToString("/")
    } else {
      filename
    }
    val directPath = root.resolve(effectiveRelativePath).normalize()
    if (forceChildPaths && !directPath.startsWith(root)) {
      log.warn("New file path '{}' resolves outside root '{}', rejecting", filename, root)
      return null
    }
    val relativePath = root.relativize(directPath).toString()
    log.info("Treating '{}' as new file path: '{}'", filename, relativePath)
    return relativePath
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
    resolver: (Path, String) -> String?,
    log: PatchTrace
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
            val candidate = candidates[0]
            val match = root.relativize(candidate).toString()
            // Only accept if at least the filename and parent directory match
            val filenameParts = filename.replace("\\", "/").split("/")
            val candidateParts = match.replace("\\", "/").split("/")
            val matchingComponents = filenameParts.reversed().zip(candidateParts.reversed())
              .takeWhile { (a, b) -> a == b }.count()
            if (matchingComponents >= minOf(2, filenameParts.size)) {
              log.info(
                "Resolved '{}' by filesystem search: found unique match '{}' under root '{}' ({} path components match)",
                filename, match, root, matchingComponents
              )
              return match
            } else {
              log.debug(
                "Filesystem search found '{}' for '{}' but only {} path components match, skipping",
                match, filename, matchingComponents
              )
            }
          }

          candidates.size > 1 -> {
            // Try to disambiguate by matching the most path components from the end
            val filenameParts = filename.replace("\\", "/").split("/")
            val bestMatch = candidates.maxByOrNull { candidate ->
              val candidateParts = root.relativize(candidate).toString().replace("\\", "/").split("/")
              filenameParts.reversed().zip(candidateParts.reversed()).takeWhile { (a, b) -> a == b }.count()
            }
            if (bestMatch != null) {
              val candidateParts = root.relativize(bestMatch).toString().replace("\\", "/").split("/")
              val matchingComponents = filenameParts.reversed().zip(candidateParts.reversed())
                .takeWhile { (a, b) -> a == b }.count()
              if (matchingComponents >= minOf(2, filenameParts.size)) {
                val match = root.relativize(bestMatch).toString()
                log.info(
                  "Resolved '{}' by filesystem search: best match '{}' among {} candidates under root '{}' ({} path components match). All candidates: {}",
                  filename, match, candidates.size, root, matchingComponents,
                  candidates.map { root.relativize(it).toString() }
                )
                return match
              } else {
                log.debug(
                  "Filesystem search found {} candidates for '{}' but best match only has {} path components matching, skipping. Candidates: {}",
                  candidates.size, filename, matchingComponents,
                  candidates.map { root.relativize(it).toString() }
                )
              }
            }
          }
        }
      } catch (e: Throwable) {
        log.debug("Filesystem search failed for '{}' under root '{}': {}", filename, root, e.message)
      }
    }
    // All strategies exhausted
    log.error(
      "Failed to resolve filename '{}' against root '{}'. " +
          "Strategies attempted: direct resolution, prefix stripping ({}), path suffix matching, " +
          "filesystem search (target='{}'). " +
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
    shouldAutoApply: (Path) -> Boolean,
    changes: MutableList<PendingChange>? = null,
    trace: PatchTrace? = null
  ): String {
    /** Shadows the class logger: captures this call's messages for the Patch Data dump. */
    val log = PatchTrace("renderNewFile($filepath)", log, trace)
    var code = code.trim().trimIndent()
    if (code.startsWith("```") && code.endsWith("```")) {
      code = code.lines().drop(1).dropLast(1).joinToString("\n").trim().trimIndent()
    }
    log.debug("Rendering new file: path={}, lang={}, code length={}", filepath, lang, code.length)
    val codeBlock = "\n```${lang}\n${code.indent("  ")}\n```\n"
    val saveAction: () -> Unit = {
      log.info("Saving new file: {}", filepath)
      fs.writeText(filepath, code)
      handle(mapOf(filepath to code))
    }
    if (code.isBlank()) {
      log.warn("Empty code content for new file: {}", filepath)
    }
    if (shouldAutoApply(filepath)) {
      if (fs.exists(filepath)) {
        log.warn("File already exists at {}! Overwriting existing file...", filepath)
      } else {
        log.info("Auto-applying new file: {}", filepath)
      }
      return try {
        fs.writeText(filepath, code)
        handle(mapOf(filepath to code))
        log.info("Successfully auto-created file: {}", filepath)
        changes?.add(PendingChange(newFileSummary(filepath, code, applied = true), apply = null))
        codeBlock + "\n" + renderer.renderAutoApplied(
          filepath,
          ""
        ) + renderer.recordPatch(
          mapOf(
            "filename" to filepath.toString(),
            "code" to code,
            "action" to "save_auto",
            "trace" to log.linesWithParents()
          )
        )
      } catch (e: Throwable) {
        log.error("Error auto-saving file {}: {}", filepath, e.message, e)
        changes?.add(PendingChange(newFileSummary(filepath, code, applied = false), apply = saveAction))
        codeBlock + "\n<div class=\"cmd-button\">Error Auto-Saving ${filepath}: ${e.message}</div>" +
            renderer.recordPatch(
              mapOf(
                "filename" to filepath.toString(),
                "code" to code,
                "action" to "save_auto_failed",
                "errors" to e.message,
                "trace" to log.linesWithParents()
              )
            )
      }
    }
    changes?.add(PendingChange(newFileSummary(filepath, code, applied = false), apply = saveAction))
    val saveButton = renderer.renderSaveButton(filepath, code, lang, saveAction)
    return codeBlock + "\n" + saveButton + renderer.recordPatch(
      mapOf(
        "filename" to filepath.toString(),
        "code" to code,
        "action" to "save",
        "trace" to log.linesWithParents()
      )
    )
  }

  private fun renderDiffBlock(
    filepath: Path,
    relativePath: Path,
    diffVal: String,
    handle: (Map<Path, String>) -> Unit,
    shouldAutoApply: (Path) -> Boolean,
    changes: MutableList<PendingChange>? = null,
    trace: PatchTrace? = null
  ): String {
    /** Shadows the class logger: captures this call's messages for the Patch Data dump. */
    val log = PatchTrace("renderDiffBlock($filepath)", log, trace)
    var diffVal = diffVal.trim().trimIndent()
    if (diffVal.startsWith("```") && diffVal.endsWith("```")) {
      diffVal = diffVal.lines().drop(1).dropLast(1).joinToString("\n").trim().trimIndent()
    }
    log.debug(
      "Rendering diff block: filepath={}, relativePath={}, diff length={}",
      filepath,
      relativePath,
      diffVal.length
    )
    val escaped = "\n```diff\n$diffVal\n```\n"
    if (diffVal.isBlank()) {
      log.warn("Empty diff content for file: {}", filepath)
      return escaped + renderer.renderWarning("Empty diff content") + renderer.recordPatch(
        mapOf(
          "filename" to filepath.toString(),
          "action" to "diff_empty",
          "trace" to log.linesWithParents()
        )
      )
    }
    val controller = DiffApplyController(filepath, diffVal, processor, fs, log)
    val prevCode = fs.readText(filepath)
    log.debug("Read previous code from {}: {} chars", filepath, prevCode.length)

    // Validate the patch
    val testResult = try {
      processor.apply(prevCode, escaped, filepath.fileName?.toString())
    } catch (e: Throwable) {
      log.warn("Exception during patch validation for {}: {}", filepath, e.message, e)
      null
    }
    val isValid = testResult?.isValid ?: false
    val errorMsg = testResult?.errors?.joinToString("; ") { it.message }
    log.debug("Patch validation for {}: isValid={}, errors={}", filepath, isValid, errorMsg)
    val summaryOf: (Boolean) -> FileChangeSummary = { applied ->
      FileChangeSummary(
        path = filepath,
        relativePath = relativePath,
        changeType = ChangeType.MODIFIED,
        linesAdded = DiffStats.linesAdded(diffVal),
        linesRemoved = DiffStats.linesRemoved(diffVal),
        isValid = isValid,
        applied = applied
      )
    }

    /** Applies (or re-applies, after a revert) the diff; [force] ignores validation failures. */
    val applyDiff: (Boolean) -> Unit = { force ->
      log.info("Applying diff for {} (force={})", filepath, force)
      when (val state = controller.apply(force = force)) {
        is ApplyState.Applied -> {
          handle(mapOf(relativePath to state.newCode))
          log.info("Successfully applied diff to {}", filepath)
        }

        is ApplyState.Failed -> {
          log.error("Apply diff failed for {}: {}", filepath, state.error.message)
          throw state.error
        }

        else -> log.warn("Unexpected state after apply for {}: {}", filepath, state::class.simpleName)
      }
    }
    val revertDiff: () -> Unit = {
      log.info("Reverting diff for {}", filepath)
      val currentState = controller.currentState()
      if (currentState is ApplyState.Applied) {
        controller.revert()
        handle(mapOf(relativePath to currentState.originalCode))
        log.info("Successfully reverted diff for {}", filepath)
      } else {
        log.warn("Cannot revert, current state for {} is {}", filepath, currentState::class.simpleName)
      }
    }


    // Auto-apply path
    val shouldAutoApplyResult = shouldAutoApply(filepath)
    log.debug("shouldAutoApply({})={}, isValid={}", filepath, shouldAutoApplyResult, isValid)
    return "\n" + if (isValid && shouldAutoApplyResult) {
      log.debug("Auto-applying diff to {}", filepath)
      when (val state = controller.apply()) {
        is ApplyState.Applied -> {
          handle(mapOf(relativePath to state.newCode))
          log.debug("Successfully auto-applied diff to {}", filepath)
          changes?.add(PendingChange(summaryOf(true), apply = null))
          /* onApply used to be a no-op, which made re-applying after a revert impossible. */
          val revertButton = renderer.renderApplyDiffButton(
            filepath, diffVal,
            onApply = { applyDiff(false) },
            onRevert = { revertDiff() },
            onForceApply = { applyDiff(true) }
          )
          escaped + renderer.renderAutoApplied(filepath, revertButton) + renderer.recordPatch(
            mapOf(
              "filename" to filepath.toString(),
              "originalCode" to prevCode,
              "diff" to diffVal,
              "newCode" to state.newCode,
              "isValid" to true,
              "action" to "auto_apply",
              "trace" to log.linesWithParents()
            )
          )
        }

        is ApplyState.Failed -> {
          log.error("Failed to auto-apply diff to {}: {}", filepath, state.error.message)
          changes?.add(PendingChange(summaryOf(false), apply = { applyDiff(true) }))
          escaped + "<div class=\"cmd-button\">Error Auto-Applying Diff to ${filepath}: ${state.error.message}</div>" +
              renderer.renderApplyDiffButton(
                filepath, diffVal,
                onApply = { applyDiff(false) },
                onRevert = { revertDiff() },
                onForceApply = { applyDiff(true) }
              ) +
              renderer.recordPatch(
                mapOf(
                  "filename" to filepath.toString(),
                  "originalCode" to prevCode,
                  "diff" to diffVal,
                  "isValid" to isValid,
                  "errors" to state.error.message,
                  "action" to "auto_apply_failed",
                  "trace" to log.linesWithParents()
                )
              )
        }

        else -> {
          log.warn("Unexpected state after auto-apply attempt for {}: {}", filepath, state::class.simpleName)
          escaped
        }
      }
    } else {
      changes?.add(PendingChange(summaryOf(false), apply = { applyDiff(false) }))
      escaped + (if (!isValid && errorMsg != null) {
        log.debug("Rendering validation warning for {}: {}", filepath, errorMsg)
        renderer.renderWarning("The patch is not valid: $errorMsg")
      } else "") + renderer.renderApplyDiffButton(
        filepath, diffVal,
        onApply = { applyDiff(false) },
        onRevert = { revertDiff() },
        onForceApply = { applyDiff(true) }
      ) + renderer.recordPatch(
        mapOf(
          "filename" to filepath.toString(),
          "originalCode" to prevCode,
          "diff" to diffVal,
          "newCode" to (testResult?.newCode ?: ""),
          "isValid" to isValid,
          "errors" to errorMsg,
          "action" to "diff",
          "trace" to log.linesWithParents()
        )
      )
    } + "\n\n"
  }
}

private fun String.indent(indent: String): String {
  return this.lines().joinToString("\n") { line -> indent + line }
}

fun String.removeCodeFences(): String {
  return when {
    this.trim().startsWith(TRIPLE_TILDE) && this.endsWith(TRIPLE_TILDE) ->
      this.trim().lines()
        .drop(1)
        .dropLast(1)
        .joinToString("\n")

    else -> this
  }
}