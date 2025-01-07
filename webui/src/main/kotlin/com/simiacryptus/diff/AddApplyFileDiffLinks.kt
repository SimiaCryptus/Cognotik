package com.simiacryptus.diff

import com.simiacryptus.skyenet.core.util.FileValidationUtils.Companion.isGitignore
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.AgentPatterns.displayMapInTabs
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.util.FileValidationUtils
import com.simiacryptus.skyenet.core.util.IterativePatchUtil
import com.simiacryptus.skyenet.set
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SocketManagerBase
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.readText

open class AddApplyFileDiffLinks {
  // Add constants for commonly used strings
  companion object {

    private val log = org.slf4j.LoggerFactory.getLogger(AddApplyFileDiffLinks::class.java)

    /** Validation message mapping */
    private val VALIDATION_MESSAGES = mapOf(
      "curly" to "Curly braces are not balanced",
      "square" to "Square braces are not balanced",
      "parenthesis" to "Parenthesis are not balanced",
      "quote" to "Quotes are not balanced",
      "singleQuote" to "Single quotes are not balanced"
    )

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
    ) = AddApplyFileDiffLinks().instrument(
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

    val patchEditorPrompt = """
          Response should use one or more code patches in diff format within ```diff code blocks.
          Each diff should be preceded by a header that identifies the file being modified.
          The diff format should use + for line additions, - for line deletions.
          The diff should include 2 lines of context before and after every change.
          
          Example:
          
          Here are the patches:
          
          ### src/utils/exampleUtils.js
          ```diff
           // Utility functions for example feature
           const b = 2;
           function exampleFunction() {
          -   return b + 1;
          +   return b + 2;
           }
          ```
          
          ### tests/exampleUtils.test.js
          ```diff
           // Unit tests for exampleUtils
           const assert = require('assert');
           const { exampleFunction } = require('../src/utils/exampleUtils');
           
           describe('exampleFunction', () => {
          -   it('should return 3', () => {
          +   it('should return 4', () => {
               assert.equal(exampleFunction(), 3);
             });
           });
          ```
          
          If needed, new files can be created by using code blocks labeled with the filename in the same manner.
          """.trimIndent()

  }

  protected open fun getInitiatorPattern(): Regex {
    return "(?s)```\\w*\n".toRegex()
  }

  protected open fun createPatchResult(
    newCode: String, isError: Boolean, validationErrors: String?
  ): PatchResult {
    return PatchResult(newCode, !isError, validationErrors)
  }

  protected open fun validateCode(code: String): Map<String, Boolean> {
    return VALIDATION_MESSAGES.keys.associateWith { key ->
      when (key) {
        "curly" -> FileValidationUtils.isCurlyBalanced(code)
        "square" -> FileValidationUtils.isSquareBalanced(code)
        "parenthesis" -> FileValidationUtils.isParenthesisBalanced(code)
        "quote" -> FileValidationUtils.isQuoteBalanced(code)
        "singleQuote" -> FileValidationUtils.isSingleQuoteBalanced(code)
        else -> true
      }
    }
  }

  protected open fun formatValidationErrors(validationResults: Map<String, Boolean>): String = buildString {
    validationResults.forEach { (key, isValid) ->
      if (!isValid) {
        append(VALIDATION_MESSAGES[key])
        append('\n')
      }
    }
  }

  protected open fun loadFile(filepath: Path?): String = try {
    when {
      filepath == null -> ""
      !filepath.toFile().exists() -> {
        log.warn("File not found: $filepath")
        ""
      }

      else -> filepath.readText(Charsets.UTF_8)
    }
  } catch (e: Throwable) {
    log.error("Error reading file: $filepath", e)
    ""
  }


  protected open fun createPatchFixerActor(): SimpleActor {
    return SimpleActor(
      prompt = """
        You are a helpful AI that helps people with coding.
        Response should use one or more code patches in diff format within ```diff code blocks.
        Each diff should be preceded by a header that identifies the file being modified.
        The diff format should use + for line additions, - for line deletions.
        The diff should include 2 lines of context before and after every change.
        Example:
        Here are the patches:
        ### src/utils/exampleUtils.js
        ```diff
         // Utility functions for example feature
         const b = 2;
         function exampleFunction() {
        -   return b + 1;
        +   return b + 2;
         }
        ```
        ### tests/exampleUtils.test.js
        ```diff
         // Unit tests for exampleUtils
         const assert = require('assert');
         const { exampleFunction } = require('../src/utils/exampleUtils');
         describe('exampleFunction', () => {
        -   it('should return 3', () => {
        +   it('should return 4', () => {
             assert.equal(exampleFunction(), 3);
           });
         });
        ```
        """.trimIndent(), model = OpenAIModels.GPT4o, temperature = 0.3
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
          self,
          root,
          response + "\n```\n",
          handle,
          ui,
          api,
          model = model,
          defaultFile = defaultFile,
        )
      }
      val headerPattern = """(?<![^\n])#+\s*([^\n]+)""".toRegex() // capture filename
      val codeblockPattern = """(?s)(?<![^\n])```([^\n]*)(\n.*?\n)```""".toRegex() // capture filename
      val headers = headerPattern.findAll(response).map { it.range to it.groupValues[1] }.toList()
      val findAll = codeblockPattern.findAll(response).toList()
      val codeblocks = findAll.filter { block ->
        try {
          val header = headers.lastOrNull { it.first.last <= block.range.first }?.second ?: defaultFile
          if (header == null) {
            return@filter false
          }
          val filename = resolve(root, header)
          !root.resolve(filename).toFile().exists()
        } catch (e: Throwable) {
          log.info("Error processing code block", e)
          false
        }
      }.map { it.range to it }.toList()
      val patchBlocks = findAll.filter { block ->
        try {
          val header = headers.lastOrNull { it.first.last <= block.range.first }?.second ?: defaultFile
          if (header == null) {
            return@filter false
          }
          val filename = resolve(root, header)
          root.resolve(filename).toFile().exists()
        } catch (e: Throwable) {
          log.info("Error processing code block", e)
          false
        }
      }.map { it.range to it }.toList()

      // Process diff blocks and add patch links
      val withPatchLinks: String = patchBlocks.foldIndexed(response) { index, markdown, diffBlock ->
        val value = diffBlock.second.groupValues[2].trim()
        val header = headers.lastOrNull { it.first.last < diffBlock.first.first }?.second ?: defaultFile ?: "Unknown"
        val filename = resolve(root, header)
        val newValue = renderDiffBlock(root, filename, value, handle, ui, api, shouldAutoApply)
        markdown.replace(diffBlock.second.value, newValue)
      }
      // Process code blocks and add save links
      val withSaveLinks = codeblocks.foldIndexed(withPatchLinks) { index, markdown, codeBlock ->
        val lang = codeBlock.second.groupValues[1]
        val value = codeBlock.second.groupValues[2].trim()
        val header = headers.lastOrNull { it.first.last < codeBlock.first.first }?.second ?: defaultFile ?: "Unknown"
        val newMarkdown = renderNewFile(header, root, ui, shouldAutoApply, value, handle, lang)
        markdown.replace(codeBlock.second.value, newMarkdown)
      }
      return withSaveLinks
    }
  }

  private data class PatchOrCode(
    val id: String? = null,
    val type: String? = null,
    val filename: String? = null,
    val data: String? = null,
  )

  private data class CorrectedPatchOrCode(
    val id: String? = null,
    val filename: String? = null,
  )

  private fun SocketManagerBase.renderNewFile(
    header: String?,
    root: Path,
    ui: ApplicationInterface,
    shouldAutoApply: (Path) -> Boolean,
    codeValue: String,
    handle: (Map<Path, String>) -> Unit,
    codeLang: String
  ): String {
    val filename = resolve(root, header ?: "Unknown")
    val filepath = root.resolve(filename)
    if (shouldAutoApply(filepath) && !filepath.toFile().exists()) {
      try {
        filepath.parent?.toFile()?.mkdirs()
        filepath.toFile().writeText(codeValue, Charsets.UTF_8)
        handle(mapOf(File(filename).toPath() to codeValue))
        return "\n```${codeLang}\n${codeValue}\n```\n\n<div class=\"cmd-button\">Automatically Saved ${filepath}</div>"
      } catch (e: Throwable) {
        return "\n```${codeLang}\n${codeValue}\n```\n\n<div class=\"cmd-button\">Error Auto-Saving ${filename}: ${e.message}</div>"
      }
    } else {
      val commandTask = ui.newTask(false)
      lateinit var hrefLink: StringBuilder
      hrefLink = commandTask.complete(hrefLink("Save File", classname = "href-link cmd-button") {
        try {
          filepath.parent?.toFile()?.mkdirs()
          filepath.toFile().writeText(codeValue, Charsets.UTF_8)
          handle(mapOf(File(filename).toPath() to codeValue))
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

  private fun resolve(root: Path, filename: String): String {
    var filename = filename.trim()
    filename = if (pattern_backticks.containsMatchIn(filename)) {
      pattern_backticks.find(filename)!!.groupValues[1]
    } else {
      filename
    }

    filename = try {
      val path = File(filename).toPath()
      if (root.contains(path)) path.toString().relativizeFrom(root) else filename
    } catch (e: Throwable) {
      filename
    }

    try {
      if (!root.resolve(filename).toFile().exists()) {
        root.toFile().listFilesRecursively().find { it.toString().replace("\\", "/").endsWith(filename.replace("\\", "/")) }?.toString()?.apply {
          filename = relativizeFrom(root)
        }
      }
    } catch (e: Throwable) {
      log.error("Error resolving filename", e)
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
    this.listFiles()?.filterNot { isGitignore(it.toPath()) }?.forEach {
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

    var newCode = patch(prevCode, diffVal)
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
            filepath.toFile().writeText(newCode.newCode, Charsets.UTF_8)
            val originalCode = AtomicReference(prevCode)
            handle(mapOf(relativize to newCode.newCode))
            val revertButton = createRevertButton(filepath, originalCode.get(), handle)
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
        originalCode = load(filepath)
        newCode = patch(originalCode, diffVal)
        filepath.toFile().writeText(newCode.newCode, Charsets.UTF_8)
        handle(mapOf(relativize to newCode.newCode))
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
        val fixPatchLink = hrefLink("Fix Patch", classname = "href-link cmd-button") {
          try {
            val header = fixTask.header("Attempting to fix patch...")
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
        }
        fixTask.complete(fixPatchLink)
      }

      // Create "Apply Diff (Bottom to Top)" button
      val applyReversed = hrefLink("(Bottom to Top)", classname = "href-link cmd-button") {
        try {
          originalCode = load(filepath)
          val originalLines = originalCode.reverseLines()
          val diffLines = diffVal.reverseLines()
          val patch1 = patch(originalLines, diffLines)
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
    val newCode2 = patch(
      load(filepath).reverseLines(), diffVal.reverseLines()
    ).newCode.lines().reversed().joinToString("\n")
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
      mainTabs + """<div class="warning">Warning: The patch is not valid: ${newCode.error ?: "???"}</div>""" + applydiffTask.placeholder
    }
    return newValue
  }

  private val patch = { code: String, diff: String ->
    val originalValidation = validateCode(code)
    var newCode = IterativePatchUtil.applyPatch(code, diff)
    newCode = newCode.replace("\r", "")
    val newValidation = validateCode(newCode)
    val isError = originalValidation.any { (key, wasValid) ->
      wasValid && !newValidation[key]!!
    }
    createPatchResult(
      newCode, isError, if (isError) formatValidationErrors(newValidation) else null
    )
  }

  // Function to load file contents
  private fun load(filepath: Path?) = loadFile(filepath)
}