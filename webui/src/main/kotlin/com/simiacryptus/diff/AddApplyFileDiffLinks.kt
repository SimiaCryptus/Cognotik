package com.simiacryptus.diff

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.AgentPatterns.displayMapInTabs
import com.simiacryptus.skyenet.apps.general.renderMarkdown
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.util.DiffApplicationResult
import com.simiacryptus.skyenet.core.util.FileValidationUtils.Companion.isGitignore
import com.simiacryptus.skyenet.core.util.GrammarValidator
import com.simiacryptus.skyenet.core.util.IterativePatchUtil
import com.simiacryptus.skyenet.core.util.IterativePatchUtil.patchFormatPrompt
import com.simiacryptus.skyenet.core.util.SimpleDiffApplier
import com.simiacryptus.skyenet.set
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SocketManagerBase
import org.apache.commons.text.similarity.LevenshteinDistance
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.readText

open class AddApplyFileDiffLinks {
  // Add constants for commonly used strings
  companion object {
    var loggingEnabled = true
    private val diffApplier = SimpleDiffApplier()
    private val log = org.slf4j.LoggerFactory.getLogger(AddApplyFileDiffLinks::class.java).apply {
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
      if(loggingEnabled) try {
        val logFile = filepath.resolveSibling(filepath.fileName.toString() + ".log").toFile()
        val duration = Duration.between(startTime, Instant.now())
        val originalSize = filepath.toFile().length()
        val stackTrace = Thread.currentThread().stackTrace
          .drop(2) // Skip getStackTrace
          .take(10) // Get first 5 frames
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
      self: SocketManagerBase,
      root: Path,
      response: String,
      handle: (Map<Path, String>) -> Unit = {},
      ui: ApplicationInterface,
      api: API,
      shouldAutoApply: (Path) -> Boolean = { false },
      model: ChatModel? = null,
      defaultFile: String? = null,
    ): String {
      log.debug("Instrumenting file diffs for root: {}", root)
      return AddApplyFileDiffLinks().instrument(
        self = self,
        root = root,
        response = response,
        handle = handle,
        ui = ui,
        api = api,
        shouldAutoApply = shouldAutoApply,
        model = model,
        defaultFile = defaultFile
      )
    }
    
  }
  
  protected open fun getInitiatorPattern(): Regex {
    return "(?s)```\\w*\n".toRegex()
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
  
  
  protected open fun createPatchFixerActor(): SimpleActor {
    return SimpleActor(
      prompt = """
        You are a helpful AI that helps people with coding.
        
        """.trimIndent() + patchFormatPrompt, model = OpenAIModels.GPT4o, temperature = 0.3
    )
  }
  
  
  // Function to reverse the order of lines in a string
  private fun String.reverseLines(): String = lines().reversed().joinToString("\n")
  
  // Main function to add apply file diff links to the response
  fun instrument(
    self: SocketManagerBase,
    root: Path,
    response: String,
    handle: (Map<Path, String>) -> Unit = {},
    ui: ApplicationInterface,
    api: API,
    shouldAutoApply: (Path) -> Boolean = { false },
    model: ChatModel? = null,
    defaultFile: String? = null,
  ): String {
    self.apply {
      // Check if there's an unclosed code block and close it if necessary
      val initiator = getInitiatorPattern()
      if (response.contains(initiator) && !response.split(initiator, 2)[1].contains("\n```(?![^\n])".toRegex())) {
        // Single diff block without the closing ``` due to LLM limitations... add it back and recurse
        return@instrument instrument(
          self = self,
          root = root,
          response = response + "\n```\n",
          handle = handle,
          ui = ui,
          api = api,
          model = model,
          defaultFile = defaultFile,
        )
      }
      
      val codeblockPattern = """(?s)(?<![^\n])```([^\n]*)\n(.*?)\n```""".toRegex() // capture filename
      val codeblockGreedyPattern = """(?s)(?<![^\n])```([^\n]*)\n(.*)\n```""".toRegex() // capture filename
      val findAll = codeblockPattern.findAll(response).toList()
        .groupBy { block -> findHeader(block, response) ?: defaultFile }
      val findAllGreedy = codeblockGreedyPattern.findAll(response).toList()
        .groupBy { block -> findHeader(block, response) ?: defaultFile }
      val resolvedMatches = mutableListOf<Pair<String?, List<MatchResult>>>()
      if (findAllGreedy.values.flatten().any { it.groupValues[1] == "markdown" }) {
//        resolvedMatches.add(findAllGreedy)
        findAllGreedy.forEach { s, matchResults -> resolvedMatches.add(s to matchResults) }
      } else {
//        resolvedMatches.add(findAll)
        findAll.forEach { s, matchResults -> resolvedMatches.add(s to matchResults) }
      }
      
      val headerPattern = """(?<![^\n])#+\s*([^\n]+)""".toRegex() // capture filename
      val headers = headerPattern.findAll(response).map { it.range to it.groupValues[1] }.toList()
      fun getFile(root: Path, header: String): File = root.resolve(resolve(root, header)).toFile()
      
      val codeblocks = resolvedMatches.filter { (header, block) ->
        try {
          !getFile(root, header ?: return@filter false).exists()
        } catch (e: Throwable) {
          log.info("Error processing code block", e)
          false
        }
      }.flatMap { it.second }.map { it.range to it }.toList()
      val patchBlocks = resolvedMatches.filter { (header, block) ->
        try {
          getFile(root, header ?: return@filter false).exists()
        } catch (e: Throwable) {
          log.info("Error processing code block", e)
          false
        }
      }.flatMap { it.second }.map { it.range to it }.toList()
      
      // Process diff blocks and add patch links
      val withPatchLinks: String = patchBlocks.foldIndexed(response) { index, markdown, diffBlock ->
        val diffValue = diffBlock.second.groupValues[2].trim()
        val header =
          headers.lastOrNull { it.first.last < diffBlock.first.first }?.second ?: defaultFile ?: "Unknown"
        val filename = resolve(root, header)
        val newValue = renderDiffBlock(root, filename, diffValue, handle, ui, api, shouldAutoApply)
        markdown.replace(diffBlock.second.value, newValue)
      }
      // Process code blocks and add save links
      val withSaveLinks = codeblocks.foldIndexed(withPatchLinks) { index, markdown, codeBlock ->
        val lang = codeBlock.second.groupValues[1]
        var codeValue = codeBlock.second.groupValues[2].trim().trimIndent()
        // If all lines start with a '+' or '-', remove them
        if (codeValue.lines().all { it.startsWith('+') || it.startsWith('-') }) {
          codeValue = codeValue.lines().joinToString("\n") { it.drop(1) }
        }
        val header =
          headers.lastOrNull { it.first.last < codeBlock.first.first }?.second ?: defaultFile ?: "Unknown"
        val filename = resolve(root, header ?: "Unknown")
        val newMarkdown = renderNewFile(root, filename, codeValue, handle, ui, lang, shouldAutoApply)
        markdown.replace(codeBlock.second.value, newMarkdown)
      }
      return withSaveLinks
    }
  }
  
  private fun findHeader(block: MatchResult, response: String): String? {
    // Capture markdown headers (e.g., "### filename")...
    val markdownHeaderPattern = """(?<![^\n])#+\s*([^\n]+)""".toRegex()
    // ...and file header banners, e.g.:
    // ─────────────────────────────────────────────
    // File: src/main/kotlin/com/simiacryptus/skyenet/apps/general/PatchApp.kt
    // ─────────────────────────────────────────────
    val fileHeaderPattern = """(?m)^(?:─+|-+)\s*\nFile:\s*(.+?)\s*\n(?:─+|-+)\s*""".toRegex()
    val headers = mutableListOf<Pair<IntRange, String>>()
    markdownHeaderPattern.findAll(response).forEach { match ->
      headers.add(match.range to match.groupValues[1])
    }
    fileHeaderPattern.findAll(response).forEach { match ->
      headers.add(match.range to match.groupValues[1])
    }
    return headers.filter { it.first.last <= block.range.first }
      .maxByOrNull { it.first.last }?.second
  }
  
  private fun SocketManagerBase.renderNewFile(
    root: Path,
    filename: String,
    codeValue: String,
    handle: (Map<Path, String>) -> Unit,
    ui: ApplicationInterface,
    codeLang: String,
    shouldAutoApply: (Path) -> Boolean
  ): String {
    val filepath = root.resolve(filename)
    if (shouldAutoApply(filepath) && !filepath.toFile().exists()) {
      try {
        val startTime = Instant.now()
        filepath.parent?.toFile()?.mkdirs()
        filepath.toFile().writeText(codeValue, Charsets.UTF_8)
        handle(mapOf(File(filename).toPath() to codeValue))
        logFileOperation(filepath, "", null, codeValue, "NEW_FILE", startTime)
        return "\n```${codeLang}\n${codeValue}\n```\n\n<div class=\"cmd-button\">Automatically Saved ${filepath}</div>"
      } catch (e: Throwable) {
        return "\n```${codeLang}\n${codeValue}\n```\n\n<div class=\"cmd-button\">Error Auto-Saving ${filename}: ${e.message}</div>"
      }
    } else {
      val commandTask = ui.newTask(false)
      lateinit var hrefLink: StringBuilder
      hrefLink = commandTask.complete(hrefLink("Save File", classname = "href-link cmd-button") {
        try {
          val startTime = Instant.now()
          filepath.parent?.toFile()?.mkdirs()
          filepath.toFile().writeText(codeValue, Charsets.UTF_8)
          handle(mapOf(File(filename).toPath() to codeValue))
          logFileOperation(filepath, "", null, codeValue, "NEW_FILE", startTime)
          hrefLink.set("""<div class="cmd-button">Saved ${filepath}</div>""")
          commandTask.complete()
        } catch (e: Throwable) {
          hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
          commandTask.error(null, e)
        }
      })!!
      return "\n```${codeLang}\n${codeValue}\n```\n\n${commandTask.placeholder}\n"
    }
  }
  
  private val pattern_backticks = "`(.*)`".toRegex()
  
  /**
   * Resolves a filename relative to a root path, handling various filename formats and path scenarios
   * @param root The base directory path to resolve against
   * @param filename The filename to resolve
   * @return The resolved filename relative to the root path
   */
  private fun resolve(root: Path, filename: String): String {
    log.debug("Resolving filename '{}' relative to root '{}'", filename, root)
    // Trim whitespace from filename
    var filename = filename.trim().split(" ").firstOrNull() ?: ""
    // Return early if filename is empty
    if (filename.isEmpty()) {
      log.warn("Empty filename provided")
      return ""
    }
    
    // Extract filename from backticks if present (e.g., `filename.txt` -> filename.txt)
    if (pattern_backticks.containsMatchIn(filename)) {
      filename = pattern_backticks.find(filename)!!.groupValues[1]
      log.trace("Extracted filename from backticks: {}", filename)
    }
    
    // Try to convert filename to absolute path and make it relative to root if possible
    try {
      val path = File(filename).toPath()
      if (path.startsWith(root)) {
        filename = path.toString().relativizeFrom(root)
        log.debug("Relativized path to: {}", filename)
      }
    } catch (e: Throwable) {
      log.error("Error resolving filename '{}': {}", filename, e.message, e)
    }
    
    // If file doesn't exist directly under root, try to find it recursively
    try {
      val resolvedPath = root.resolve(filename)
      if (!resolvedPath.toFile().exists() || !resolvedPath.toFile().isFile) {
        log.debug("File not found directly under root, searching recursively")
        // Search recursively through root directory for matching file
        // Normalize path separators to handle cross-platform paths
        root.toFile().listFilesRecursively()
          .filter { it.isFile }  // Only consider files, not directories
          .find { it.toString().replace("\\", "/").endsWith(filename.replace("\\", "/")) }
          ?.toString()
          ?.apply {
            filename = relativizeFrom(root)
            log.debug("Found file recursively at: {}", filename)
          }
      }
    } catch (e: Throwable) {
      log.error("Error searching for file '{}' recursively: {}", filename, e.message)
      log.debug("Stack trace:", e)
    }
    
    // If file doesn't exist directly under root, try to find it using string distance
    try {
      if (!root.resolve(filename).toFile().exists()) {
        log.debug("File not found, attempting fuzzy match")
        val levenshtein = LevenshteinDistance()
        val files = root.toFile().listFilesRecursively()
        val closest = files.minByOrNull { levenshtein.apply(it.toString(), filename) }
        if (closest != null && levenshtein.apply(closest.toString(), filename) < 5) {
          filename = closest.toString().relativizeFrom(root)
          log.debug("Found closest match: {}", filename)
        }
      }
    } catch (e: Throwable) {
      log.error("Error finding fuzzy match for '{}': {}", filename, e.message, e)
    }
    
    return filename
  }
  
  private fun String.relativizeFrom(root: Path) = try {
    root.relativize(File(this).toPath()).toString()
  } catch (e: Throwable) {
    this
  }
  
  private fun File.listFilesRecursively(): List<File> {
    val files = mutableListOf<File>()
    this.listFiles()?.filter { 
      !isGitignore(it.toPath()) && 
      !it.name.startsWith(".") &&  // Skip hidden files/directories
      !it.name.equals("node_modules") // Skip node_modules directory
    }?.forEach {
      files.add(it.absoluteFile)
      if (it.isDirectory) {
        files.addAll(it.listFilesRecursively())
      }
    }
    return files
  }
  
  // Function to render a diff block with apply and revert options
  private fun SocketManagerBase.renderDiffBlock(
    root: Path,
    filename: String,
    diffVal: String,
    handle: (Map<Path, String>) -> Unit,
    ui: ApplicationInterface,
    api: API?,
    shouldAutoApply: (Path) -> Boolean,
    model: ChatModel? = null,
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
    
    val apply = diffApplier.apply(prevCode, "```diff\n$diffVal\n```", filename)
    var newCode = apply.patchResult
    val echoDiff = try {
      IterativePatchUtil.generatePatch(prevCode, newCode.newCode)
    } catch (e: Throwable) {
      renderMarkdown("\n```\n${e.stackTraceToString()}\n```\n", ui = ui)
    }
    
    // Function to create a revert button
    fun createRevertButton(filepath: Path, originalCode: String, handle: (Map<Path, String>) -> Unit): String {
      val relativize = try {
        root.relativize(filepath)
      } catch (e: Throwable) {
        filepath
      }
      val revertTask = ui.newTask(false)
      lateinit var revertButton: StringBuilder
      revertButton = revertTask.complete(hrefLink("Revert", classname = "href-link cmd-button") {
        try {
          filepath.toFile().writeText(originalCode, Charsets.UTF_8)
          handle(mapOf(relativize to originalCode))
          revertButton.set("""<div class="cmd-button">Reverted</div>""")
          revertTask.complete()
        } catch (e: Throwable) {
          revertButton.append("""<div class="cmd-button">Error: ${e.message}</div>""")
          revertTask.error(null, e)
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
            logFileOperation(filepath, prevCode, diffVal, newCode.newCode, "AUTO_PATCH", startTime, apply.validator)
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
    diffTask.complete(renderMarkdown("\n```diff\n$diffVal\n```\n", ui = ui))
    
    val prevCodeTask = ui.newTask(root = false)
    val prevCodeTaskSB = prevCodeTask.add("")
    val newCodeTask = ui.newTask(root = false)
    val newCodeTaskSB = newCodeTask.add("")
    val patchTask = ui.newTask(root = false)
    val patchTaskSB = patchTask.add("")
    val fixTask = ui.newTask(root = false)
    val verifyFwdTabs = if (!newCode.isValid) displayMapInTabs(
      mapOf(
        "Echo" to patchTask.placeholder,
        "Fix" to fixTask.placeholder,
        "Code" to prevCodeTask.placeholder,
        "Preview" to newCodeTask.placeholder,
      )
    ) else displayMapInTabs(
      mapOf(
        "Echo" to patchTask.placeholder,
        "Code" to prevCodeTask.placeholder,
        "Preview" to newCodeTask.placeholder,
      )
    )
    
    val prevCode2Task = ui.newTask(root = false)
    val prevCode2TaskSB = prevCode2Task.add("")
    val newCode2Task = ui.newTask(root = false)
    val newCode2TaskSB = newCode2Task.add("")
    val patch2Task = ui.newTask(root = false)
    val patch2TaskSB = patch2Task.add("")
    val verifyRevTabs = displayMapInTabs(
      mapOf(
        "Echo" to patch2Task.placeholder,
        "Code" to prevCode2Task.placeholder,
        "Preview" to newCode2Task.placeholder,
      )
    )
    
    lateinit var revert: String
    
    var originalCode = prevCode
    val apply1 = hrefLink("Apply Diff", classname = "href-link cmd-button") {
      try {
        val startTime = Instant.now()
        originalCode = load(filepath)
        newCode = diffApplier.apply(originalCode, "```diff\n$diffVal\n```", null).patchResult
        filepath.toFile().writeText(newCode.newCode, Charsets.UTF_8)
        handle(mapOf(relativize to newCode.newCode))
        logFileOperation(filepath, originalCode, diffVal, newCode.newCode, "MANUAL_PATCH", startTime, apply.validator)
        hrefLink.set("<div class=\"cmd-button\">Diff Applied</div>$revert")
        applydiffTask.complete()
      } catch (e: Throwable) {
        hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
        applydiffTask.error(null, e)
      }
    }
    
    if (echoDiff.isNotBlank()) {
      // Add "Fix Patch" button if the patch is not valid
      if (!newCode.isValid) {
        fixTask.complete(hrefLink("Fix Patch", classname = "href-link cmd-button") {
          try {
            val header = fixTask.header("Attempting to fix patch...", 4)
            val patchFixer = createPatchFixerActor()
            val echoDiff = try {
              IterativePatchUtil.generatePatch(prevCode, newCode.newCode)
            } catch (e: Throwable) {
              renderMarkdown("\n```\n${e.stackTraceToString()}\n```\n", ui = ui)
            }
            var answer = patchFixer.answer(
              listOf(
                "\nCode:\n```${
                  filename.split('.').lastOrNull() ?: ""
                }\n$prevCode\n```\n\nPatch:\n```diff\n$diffVal\n```\n\nEffective Patch:\n```diff\n$echoDiff\n```\n\nPlease provide a fix for the diff above in the form of a diff patch.\n"
              ), api as OpenAIClient
            )
            answer = instrument(ui.socketManager!!, root, answer, handle, ui, api, model = model)
            header?.clear()
            fixTask.complete(renderMarkdown(answer))
          } catch (e: Throwable) {
            log.error("Error in fix patch", e)
          }
        })
      }
      
      // Create "Apply Diff (Bottom to Top)" button
      val applyReversed = hrefLink("(Bottom to Top)", classname = "href-link cmd-button") {
        try {
          originalCode = load(filepath)
          val originalLines = originalCode.reverseLines()
          val diffLines = diffVal.reverseLines()
          val patch1 = diffApplier.apply(originalLines, "```diff\n$diffLines\n```", filename).patchResult
          val newCode2 = patch1.newCode.reverseLines()
          filepath.toFile().writeText(newCode2, Charsets.UTF_8)
          handle(mapOf(relativize to newCode2))
          hrefLink.set("""<div class="cmd-button">Diff Applied (Bottom to Top)</div>""" + revert)
          applydiffTask.complete()
        } catch (e: Throwable) {
          hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
          applydiffTask.error(null, e)
        }
      }
      // Create "Revert" button
      revert = hrefLink("Revert", classname = "href-link cmd-button") {
        try {
          filepath.toFile().writeText(originalCode, Charsets.UTF_8)
          handle(mapOf(relativize to originalCode))
          hrefLink.set("""<div class="cmd-button">Reverted</div>""" + apply1 + applyReversed)
          applydiffTask.complete()
        } catch (e: Throwable) {
          hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
          applydiffTask.error(null, e)
        }
      }
      hrefLink = applydiffTask.complete(apply1 + "\n" + applyReversed)!!
    }
    
    val lang = filename.split('.').lastOrNull() ?: ""
    newCodeTaskSB?.set(
      renderMarkdown(
        "# $filename\n\n```$lang\n${newCode}\n```\n", ui = ui, tabs = false
      )
    )
    newCodeTask.complete("")
    prevCodeTaskSB?.set(
      renderMarkdown(
        "# $filename\n\n```$lang\n${prevCode}\n```\n", ui = ui, tabs = false
      )
    )
    prevCodeTask.complete("")
    patchTaskSB?.set(
      renderMarkdown(
        "\n# $filename\n\n```diff\n${echoDiff}\n```\n", ui = ui, tabs = false
      )
    )
    patchTask.complete("")
    load(filepath).reverseLines()
    diffVal.reverseLines()
    val newCode2 = diffApplier.apply(
      load(filepath).reverseLines(), "```diff\n${
        diffVal.reverseLines()
      }\n```", filename
    ).patchResult.newCode.lines().reversed().joinToString("\n")
    val echoDiff2 = try {
      IterativePatchUtil.generatePatch(prevCode, newCode2)
    } catch (e: Throwable) {
      renderMarkdown(
        "\n```\n${e.stackTraceToString()}\n```", ui = ui
      )
    }
    newCode2TaskSB?.set(
      renderMarkdown(
        "# $filename\n\n```${filename.split('.').lastOrNull() ?: ""}\n${newCode2}\n```\n", ui = ui, tabs = false
      )
    )
    newCode2Task.complete("")
    prevCode2TaskSB?.set(
      renderMarkdown(
        "# $filename\n\n```${filename.split('.').lastOrNull() ?: ""}\n${prevCode}\n```\n", ui = ui, tabs = false
      )
    )
    prevCode2Task.complete("")
    patch2TaskSB?.set(
      renderMarkdown(
        "# $filename\n\n```diff\n  ${echoDiff2}\n```\n", ui = ui, tabs = false
      )
    )
    patch2Task.complete("")
    
    
    // Create main tabs for displaying diff and verification information
    val mainTabs = displayMapInTabs(
      mapOf(
        "Diff" to diffTask.placeholder,
        "Verify" to displayMapInTabs(
          mapOf(
            "Forward" to verifyFwdTabs,
            "Reverse" to verifyRevTabs,
          )
        ),
      )
    )
    val newValue = if (newCode.isValid) {
      mainTabs + "\n" + applydiffTask.placeholder
    } else {
      mainTabs + """<div class="warning">Warning: The patch is not valid: ${newCode.error?.renderMarkdown() ?: "???"}</div>""" + applydiffTask.placeholder
    }
    return newValue
  }
  
  private val DiffApplicationResult.patchResult get() = PatchResult(
    newCode,
    isValid,
    errors.joinToString("\n") { "* ${it.message} (line ${it.line})" }
  )
  
  // Function to load file contents
  private fun load(filepath: Path?) = loadFile(filepath)
}