package com.simiacryptus.diff

import com.simiacryptus.skyenet.AgentPatterns.displayMapInTabs
import com.simiacryptus.skyenet.set
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.session.SocketManagerBase

fun SocketManagerBase.addApplyDiffLinks(
  code: () -> String,
  response: String,
  handle: (String) -> Unit,
  task: SessionTask,
  ui: ApplicationInterface,
): String {


  val patch = { code: String, diff: String ->
    val isCurlyBalanced = FileValidationUtils.isCurlyBalanced(code)
    val isSquareBalanced = FileValidationUtils.isSquareBalanced(code)
    val isParenthesisBalanced = FileValidationUtils.isParenthesisBalanced(code)
    val isQuoteBalanced = FileValidationUtils.isQuoteBalanced(code)
    val isSingleQuoteBalanced = FileValidationUtils.isSingleQuoteBalanced(code)
    var newCode = IterativePatchUtil.applyPatch(code, diff)
    newCode = newCode.replace("\r", "")
    val isCurlyBalancedNew = FileValidationUtils.isCurlyBalanced(newCode)
    val isSquareBalancedNew = FileValidationUtils.isSquareBalanced(newCode)
    val isParenthesisBalancedNew = FileValidationUtils.isParenthesisBalanced(newCode)
    val isQuoteBalancedNew = FileValidationUtils.isQuoteBalanced(newCode)
    val isSingleQuoteBalancedNew = FileValidationUtils.isSingleQuoteBalanced(newCode)
    val isError = ((isCurlyBalanced && !isCurlyBalancedNew) ||
        (isSquareBalanced && !isSquareBalancedNew) ||
        (isParenthesisBalanced && !isParenthesisBalancedNew) ||
        (isQuoteBalanced && !isQuoteBalancedNew) ||
        (isSingleQuoteBalanced && !isSingleQuoteBalancedNew))
    PatchResult(newCode, !isError, if(!isError) null else {
      val error = StringBuilder()
      if (!isCurlyBalanced) error.append("Curly braces are not balanced\n")
      if (!isSquareBalanced) error.append("Square braces are not balanced\n")
      if (!isParenthesisBalanced) error.append("Parenthesis are not balanced\n")
      if (!isQuoteBalanced) error.append("Quotes are not balanced\n")
      if (!isSingleQuoteBalanced) error.append("Single quotes are not balanced\n")
      error.toString()
    })
  }

  val diffPattern = """(?s)(?<![^\n])```diff\n(.*?)\n```""".toRegex()
  val matches = diffPattern.findAll(response).distinct()
  val withLinks = matches.fold(response) { markdown, diffBlock ->
    val diffVal: String = diffBlock.groupValues[1]
    val buttons = ui.newTask(false)
    lateinit var hrefLink: StringBuilder
    var reverseHrefLink: StringBuilder? = null
    hrefLink = buttons.complete(hrefLink("Apply Diff", classname = "href-link cmd-button") {
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
    val test1 = IterativePatchUtil.generatePatch(code().replace("\r", ""), patch)
    val patchRev = patch(
      code().lines().reversed().joinToString("\n"),
      diffVal.lines().reversed().joinToString("\n")
    ).newCode
    if (patchRev != patch) {
      reverseHrefLink = buttons.complete(hrefLink("(Bottom to Top)", classname = "href-link cmd-button") {
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
    }
    val test2 = DiffUtil.formatDiff(
      DiffUtil.generateDiff(
        code().lines(),
        patchRev.lines().reversed()
      )
    )
    val newValue = if (patchRev == patch) {
      displayMapInTabs(
        mapOf(
          "Diff" to renderMarkdown("```diff\n$diffVal\n```", ui = ui, tabs = true),
          "Verify" to renderMarkdown("```diff\n$test1\n```", ui = ui, tabs = true),
        ), ui = ui, split = true
      ) + "\n" + buttons.placeholder
    } else {
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