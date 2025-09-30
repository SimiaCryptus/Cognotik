package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.diff.DiffUtil
import com.simiacryptus.cognotik.diff.PatchProccessors
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
        ) = AddApplyDiffLinks().apply(self, code, response, handle, task, shouldAutoApply)

    }

    fun apply(
        socketManager: SocketManager,
        code: () -> String,
        response: String,
        handle: (String) -> Unit,
        task: SessionTask,
        shouldAutoApply: Boolean = false,
    ): String {
        val matches = SimpleDiffApplier.DIFF_PATTERN.findAll(response).distinct()

        val patch = { code: String, diff: String ->
            val result = diffApplier.apply(code, "```diff\n$diff\n```")
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

            val buttons = socketManager.newTask(cancelable = false, root = false)
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
                val test1 = PatchProccessors.Iterative.generatePatch(code().replace("\r", ""), patch)
                displayMapInTabs(
                    mapOf(
                        "Diff" to renderMarkdown("```diff\n$diffVal\n```", ui = task.manager, tabs = true),
                        "Verify" to renderMarkdown("```diff\n$test1\n```", ui = task.manager, tabs = true),
                    ), ui = task.manager, split = true
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
                val test1 = PatchProccessors.Iterative.generatePatch(code().replace("\r", ""), patch)
                val test2 = DiffUtil.formatDiff(
                    DiffUtil.generateDiff(
                        code().lines(),
                        patchRev.lines().reversed()
                    )
                )
                displayMapInTabs(
                    mapOf(
                        "Diff" to renderMarkdown("```diff\n$diffVal\n```", ui = task.manager, tabs = true),
                        "Verify" to renderMarkdown("```diff\n$test1\n```", ui = task.manager, tabs = true),
                        "Reverse" to renderMarkdown("```diff\n$test2\n```", ui = task.manager, tabs = true),
                    ), ui = task.manager, split = true
                ) + "\n" + buttons.placeholder
            }
            markdown.replace(diffBlock.value, newValue)
        }
        return withLinks
    }
}