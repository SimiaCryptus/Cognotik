package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.actors.SimpleActor
import com.simiacryptus.cognotik.plan.PlanCoordinator
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskConfigData
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.util.AddApplyFileDiffLinks
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class FileModificationTask(
    planSettings: PlanSettings,
    planTask: FileModificationTaskConfigData?
) : AbstractFileTask<FileModificationTaskConfigData>(planSettings, planTask) {
    class FileModificationTaskConfigData(
        files: List<String>? = null,
        related_files: List<String>? = null,
        @Description("Specific modifications to be made to the files")
        val modifications: Any? = null,
        @Description("Whether to include git diff with HEAD")
        val includeGitDiff: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : FileTaskConfigBase(
        task_type = TaskType.FileModificationTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies,
        related_files = related_files,
        files = files,
        state = state
    )

    private fun getGitDiff(filePath: String): String? {
        return try {
            val process = ProcessBuilder("git", "diff", "HEAD", "--", File(filePath).name)
                .directory(File(filePath).parentFile)
                .start()
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                process.inputStream.bufferedReader().readText()
            } else {
                process.destroy()
                log.warn("Git diff command timed out for file: $filePath")
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to get git diff for file: $filePath", e)
            null
        }
    }

    private fun getInputFileWithDiff(): String {
        if (!taskConfig?.includeGitDiff!!) return getInputFileCode()
        val fileContent = getInputFileCode()
        val gitDiffs = (taskConfig?.related_files ?: listOf())
            .mapNotNull { file ->
                getGitDiff(file)?.let { diff ->
                    "Git diff for $file:\n$diff"
                }
            }
            .joinToString("\n\n")
        return if (gitDiffs.isNotBlank()) {
            """
      Current file content:
      $fileContent
      Git changes:
      $gitDiffs
      """.trimIndent()
        } else {
            fileContent
        }
    }

    val fileModificationActor by lazy {
        SimpleActor(
            name = "FileModification",
            prompt = """
Generate precise code modifications and new files based on requirements:
For modifying existing files:
- Write efficient, readable, and maintainable code changes
- Ensure modifications integrate smoothly with existing code
- Follow project coding standards and patterns
- Consider dependencies and potential side effects
- Provide clear context and rationale for changes

For creating new files:
- Choose appropriate file locations and names
- Structure code according to project conventions
- Include necessary imports and dependencies
- Add comprehensive documentation
- Ensure no duplication of existing functionality

Provide a clear summary explaining:
- What changes were made and why
- Any important implementation details
- Potential impacts on other code
- Required follow-up actions

Response format:
For existing files: Use ${TRIPLE_TILDE}diff code blocks with a header specifying the file path.
For new files: Use $TRIPLE_TILDE code blocks with a header specifying the new file path.
The diff format should use + for line additions, - for line deletions.
Include 2 lines of context before and after every change in diffs.
Separate code blocks with a single blank line.
For new files, specify the language for syntax highlighting after the opening triple backticks.

Example:

Here are the modifications:

### src/utils/existingFile.js
${TRIPLE_TILDE}diff

function existingFunction() {
return 'old result';
return 'new result';
}
$TRIPLE_TILDE

### src/utils/newFile.js
${TRIPLE_TILDE}js

function newFunction() {
 return 'new functionality';
}
$TRIPLE_TILDE
""".trimIndent(),
            model = taskSettings.model ?: planSettings.defaultModel,
            temperature = planSettings.temperature,
        )
    }

    override fun promptSegment() = """
FileModificationTask - Modify existing files or create new files
  * For each file, specify the relative file path and the goal of the modification or creation
  * List input files/tasks to be examined when designing the modifications or new files
Available files:
${getAvailableFiles(root).joinToString("\n") { "  - $it" }}
""".trimIndent()

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        api: ChatClient,
        resultFn: (String) -> Unit,
        api2: OpenAIClient,
        planSettings: PlanSettings
    ) {
        val defaultFile = if (((taskConfig?.related_files ?: listOf()) + (taskConfig?.files ?: listOf())).isEmpty()) {
            task.complete("CONFIGURATION ERROR: No input files specified")
            resultFn("CONFIGURATION ERROR: No input files specified")
            return
        } else if (((taskConfig?.related_files ?: listOf()) + (taskConfig?.files ?: listOf())).distinct().size == 1) {
            ((taskConfig?.related_files ?: listOf()) + (taskConfig?.files ?: listOf())).first()
        } else {
            null
        }

        val semaphore = Semaphore(0)
        val onComplete = { semaphore.release() }
        val completionNotes = mutableListOf<String>()
        Retryable(agent.ui, task = task) {
            val task = agent.ui.newTask(false)
            agent.ui.socketManager?.pool?.submit {
                val codeResult = fileModificationActor.answer(
                    (messages + listOf(
                        agent.planProcessingState?.tasksByDescription?.filter {
                            this.taskConfig?.task_dependencies?.contains(it.key) == true && it.value is FileModificationTaskConfigData
                        }?.entries?.joinToString("\n\n") {
                            (it.value as FileModificationTaskConfigData).files?.joinToString("\n") {
                                val file = root.resolve(it).toFile()
                                if (file.exists()) {
                                    val relativePath = root.relativize(file.toPath())
                                    "## $relativePath\n\n${(codeFiles[file.toPath()] ?: file.readText()).let { "$TRIPLE_TILDE\n${it}\n$TRIPLE_TILDE" }}"
                                } else {
                                    "File not found: $it"
                                }
                            } ?: ""
                        } ?: "",
                        getInputFileWithDiff(),
                        this.taskConfig?.task_description ?: "",
                    )).filter { it.isNotBlank() }, api
                )
                if (agent.planSettings.autoFix) {
                    val markdown = renderMarkdown(codeResult, ui = agent.ui) {
                        AddApplyFileDiffLinks.instrumentFileDiffs(
                            agent.ui.socketManager,
                            root = agent.root,
                            response = it,
                            handle = { newCodeMap ->
                                newCodeMap.forEach { (path, newCode) ->
                                    completionNotes += ("<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated")
                                }
                            },
                            ui = agent.ui,
                            api = api,
                            shouldAutoApply = { agent.planSettings.autoFix },
                            model = taskSettings.model ?: planSettings.defaultModel,
                            defaultFile = defaultFile
                        ) + "\n\n## Auto-applied changes"
                    }
                    onComplete()
                    task.complete(markdown)
                } else {
                    task.complete(renderMarkdown(codeResult, ui = agent.ui) {
                        AddApplyFileDiffLinks.instrumentFileDiffs(
                            agent.ui.socketManager,
                            root = agent.root,
                            response = it,
                            handle = { newCodeMap ->
                                newCodeMap.forEach { (path, newCode) ->
                                    completionNotes += ("<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated")
                                }
                            },
                            ui = agent.ui,
                            api = api,
                            model = taskSettings.model ?: planSettings.defaultModel,
                            defaultFile = defaultFile,
                        ) + acceptButtonFooter(agent.ui) {
                            task.complete()
                            onComplete()
                        }
                    })
                }
            }
            task.placeholder
        }
        try {
            semaphore.acquire()
            resultFn(completionNotes.joinToString("\n"))
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileModificationTask::class.java)
    }
}