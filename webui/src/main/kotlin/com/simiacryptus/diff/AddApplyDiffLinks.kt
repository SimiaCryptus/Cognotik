package com.simiacryptus.diff

import com.simiacryptus.skyenet.AgentPatterns.displayMapInTabs
import com.simiacryptus.skyenet.set
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.session.SocketManagerBase

class AddApplyDiffLinks {
  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(AddApplyDiffLinks::class.java)

    private const val MAX_DIFF_SIZE_CHARS = 100000

    private val DIFF_PATTERN = """(?s)(?<![^\n])```diff\n(.*?)\n```""".toRegex()

    fun addApplyDiffLinks(
      self: SocketManagerBase,
      code: () -> String,
      response: String,
      handle: (String) -> Unit,
      task: SessionTask,
      ui: ApplicationInterface,
      shouldAutoApply: Boolean = false,
     ) = AddApplyDiffLinks().apply(self, code, response, handle, task, ui, shouldAutoApply)

    val patchEditorPrompt = """
          Response should use one or more code patches in diff format within ```diff code blocks.
          The diff format should use + for line additions, - for line deletions.
          The diff should include 2 lines of context before and after every change.
          
          Example:
          
          Here are the changes:
          
          ```diff
           // Utility functions for example feature
           const b = 2;
           function exampleFunction() {
          -   return b + 1;
          +   return b + 2;
           }
          ```
          
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
          """.trimIndent()

  }

  private fun validateDiffSize(diff: String): Boolean {
    return diff.length <= MAX_DIFF_SIZE_CHARS
  }

  fun apply(
    socketManagerBase: SocketManagerBase,
    code: () -> String,
    response: String,
    handle: (String) -> Unit,
    task: SessionTask,
    ui: ApplicationInterface,
    shouldAutoApply: Boolean = false,
  ): String {
    val matches = DIFF_PATTERN.findAll(response).distinct()

    val patch = { code: String, diff: String ->
      if (!validateDiffSize(diff)) {
        throw IllegalArgumentException("Diff size exceeds maximum limit")
      }

      val isParenthesisBalanced = FileValidationUtils.isParenthesisBalanced(code)
      val isQuoteBalanced = FileValidationUtils.isQuoteBalanced(code)
      val isSingleQuoteBalanced = FileValidationUtils.isSingleQuoteBalanced(code)
      val isCurlyBalanced = FileValidationUtils.isCurlyBalanced(code)
      val isSquareBalanced = FileValidationUtils.isSquareBalanced(code)
      val newCode = IterativePatchUtil.applyPatch(code, diff).replace("\r", "")
      val isParenthesisBalancedNew = FileValidationUtils.isParenthesisBalanced(newCode)
      val isQuoteBalancedNew = FileValidationUtils.isQuoteBalanced(newCode)
      val isSingleQuoteBalancedNew = FileValidationUtils.isSingleQuoteBalanced(newCode)
      val isCurlyBalancedNew = FileValidationUtils.isCurlyBalanced(newCode)
      val isSquareBalancedNew = FileValidationUtils.isSquareBalanced(newCode)
      val isError = (isCurlyBalanced && !isCurlyBalancedNew) ||
          (isSquareBalanced && !isSquareBalancedNew) ||
          (isParenthesisBalanced && !isParenthesisBalancedNew) ||
          (isQuoteBalanced && !isQuoteBalancedNew) ||
          (isSingleQuoteBalanced && !isSingleQuoteBalancedNew)
      PatchResult(
        newCode, !isError, if (!isError) null else {
          val error = StringBuilder()
          if (!isCurlyBalancedNew) error.append("Curly braces are not balanced\n")
          if (!isSquareBalancedNew) error.append("Square braces are not balanced\n")
          if (!isParenthesisBalancedNew) error.append("Parenthesis are not balanced\n")
          if (!isQuoteBalancedNew) error.append("Quotes are not balanced\n")
          if (!isSingleQuoteBalancedNew) error.append("Single quotes are not balanced\n")
          error.toString()
        }
      )
    }

    val withLinks = matches.fold(response) { markdown, diffBlock ->
      val diffVal: String = diffBlock.groupValues[1]
      // Try to auto-apply if enabled
      if (shouldAutoApply) {
        try {
          val newCode = patch(code(), diffVal)
          if (newCode.isValid) {
            handle(newCode.newCode)
            return@fold markdown.replace(diffBlock.value, 
              """```diff
              $diffVal
              ```
              <div class="cmd-button">Diff Automatically Applied</div>""")
          }
        } catch (e: Throwable) {
          log.error("Error auto-applying diff", e)
          return@fold markdown.replace(diffBlock.value,
            """```diff
            $diffVal
            ```
            <div class="cmd-button">Error Auto-Applying Diff: ${e.message}</div>""")
        }
      }

      val buttons = ui.newTask(false)
      lateinit var hrefLink: StringBuilder
      var reverseHrefLink: StringBuilder? = null
      hrefLink = buttons.complete(socketManagerBase.hrefLink("Apply Diff", classname = "href-link cmd-button") {
        try {
          val newCode = patch(code(), diffVal)
          handle(newCode.newCode)
          hrefLink.set("""<div class="cmd-button">Diff Applied</div>""")
          buttons.complete()
          reverseHrefLink?.clear()
        } catch (e: Throwable) {
          hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
          buttons.complete()
          task.error(ui, e)
        }
      })!!
      val patch = patch(code(), diffVal).newCode
      val patchRev = patch(
        code().lines().reversed().joinToString("\n"),
        diffVal.lines().reversed().joinToString("\n")
      ).newCode
      val newValue = if (patchRev == patch) {
        val test1 = IterativePatchUtil.generatePatch(code().replace("\r", ""), patch)
        displayMapInTabs(
          mapOf(
            "Diff" to renderMarkdown("```diff\n$diffVal\n```", ui = ui, tabs = true),
            "Verify" to renderMarkdown("```diff\n$test1\n```", ui = ui, tabs = true),
          ), ui = ui, split = true
        ) + "\n" + buttons.placeholder
      } else {
        reverseHrefLink = buttons.complete(socketManagerBase.hrefLink("(Bottom to Top)", classname = "href-link cmd-button") {
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
            task.error(ui, e)
          }
        })!!
        val test1 = IterativePatchUtil.generatePatch(code().replace("\r", ""), patch)
        val test2 = DiffUtil.formatDiff(
          DiffUtil.generateDiff(
            code().lines(),
            patchRev.lines().reversed()
          )
        )
        displayMapInTabs(
          mapOf(
            "Diff" to renderMarkdown("```diff\n$diffVal\n```", ui = ui, tabs = true),
            "Verify" to renderMarkdown("```diff\n$test1\n```", ui = ui, tabs = true),
            "Reverse" to renderMarkdown("```diff\n$test2\n```", ui = ui, tabs = true),
          ), ui = ui, split = true
        ) + "\n" + buttons.placeholder
      }
      markdown.replace(diffBlock.value, newValue)
    }
    return withLinks
  }
}