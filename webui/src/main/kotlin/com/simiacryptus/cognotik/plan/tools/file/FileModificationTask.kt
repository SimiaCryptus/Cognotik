package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.AddApplyFileDiffLinks
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class FileModificationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: FileModificationTaskExecutionConfigData?
) : AbstractFileTask<FileModificationTaskExecutionConfigData>(orchestrationConfig, planTask) {
    class FileModificationTaskExecutionConfigData(
        files: List<String>? = null,
        related_files: List<String>? = null,
        extractContent: Boolean = false,
        @Description("Specific modifications to be made to the files")
        val modifications: Any? = null,
        @Description("Whether to include git diff with HEAD")
        val includeGitDiff: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : FileTaskExecutionConfig(
        task_type = FileModification.name,
        task_description = task_description,
        task_dependencies = task_dependencies,
        related_files = related_files,
        files = files,
        extractContent = extractContent,
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (files.isNullOrEmpty() && related_files.isNullOrEmpty()) {
                return "At least one file must be specified in either 'files' or 'related_files'"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    private fun getInputFileWithDiff() = if (!executionConfig?.includeGitDiff!!) getInputFileCode()
    else getInputFileCode { file ->
        toString(file).toString() + (file.toString().getGitDiff()?.let { diff ->
            "\n\nGit diff for $file:\n$diff"
        } ?: "")
    }

    override fun promptSegment() = """
FileModification - Modify existing files or create new files
  * For each file, specify the relative file path and the goal of the modification or creation
  * List input files/tasks to be examined when designing the modifications or new files
""".trimIndent()


    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val defaultFile = getDefaultFile()
        val semaphore = Semaphore(0)
        val completionNotes = mutableListOf<String>()
        // Initialize transcript for this task
        val transcript = task.transcript()
        transcript?.let { stream ->
            stream.write("# File Modification Task Transcript\n\n".toByteArray())
            Retryable(task = task) {
                val task = task.ui.newTask(false)
                val typeConfig = typeConfig ?: throw RuntimeException()
                task.ui.pool.submit {
                    val chatInterface =
                        (typeConfig.model?.let<ApiChatModel, ChatInterface> { this.orchestrationConfig.instance(it) }
                            ?: this.defaultSmart).getChildClient(task)
                    val chatAgent = ChatAgent(
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
        For new files: Use ${TRIPLE_TILDE} code blocks with a header specifying the new file path.
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
        ${TRIPLE_TILDE}
        
        ### src/utils/newFile.js
        ${TRIPLE_TILDE}js
        
        function newFunction() {
         return 'new functionality';
        }
        ${TRIPLE_TILDE}
        """.trimIndent(),
                        model = chatInterface,
                        temperature = this.orchestrationConfig.temperature,
                    )
                    val codeResult = chatAgent.answer(
                        (messages + listOf(
                            agent.executionState?.tasksByDescription?.filter {
                                executionConfig?.task_dependencies?.contains(it.key) == true && it.value is FileModificationTaskExecutionConfigData
                            }?.entries?.joinToString("\n\n") {
                                (it.value as FileModificationTaskExecutionConfigData).files?.joinToString("\n") {
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
                            executionConfig?.task_description ?: "",
                        )).filter { it.isNotBlank() }
                    )
                    // Write to transcript
                    transcript?.write("\n## AI Response\n\n".toByteArray())
                    transcript?.write(codeResult.toByteArray())
                    transcript?.write("\n\n".toByteArray())

                    if (orchestrationConfig.autoFix) {
                        val markdown = renderMarkdown(codeResult, ui = task.ui) {
                            AddApplyFileDiffLinks.instrumentFileDiffs(
                                task.ui,
                                root = agent.root,
                                response = it,
                                handle = { newCodeMap ->
                                    newCodeMap.forEach { (path, _) ->
                                        completionNotes += ("<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated")
                                    }
                                },
                                shouldAutoApply = { orchestrationConfig.autoFix },
                                model = chatInterface,
                                defaultFile = defaultFile,
                                orchestrationConfig.processor
                            ) + "\n\n## Auto-applied changes"
                        }
                        // Log auto-applied changes to transcript
                        transcript?.write("## Auto-Applied Changes\n\n".toByteArray())
                        transcript?.write(completionNotes.joinToString("\n").toByteArray())
                        task.complete(markdown)
                        semaphore.release()
                    } else {
                        task.complete(renderMarkdown(codeResult, ui = task.ui) {
                            AddApplyFileDiffLinks.instrumentFileDiffs(
                                task.ui,
                                root = agent.root,
                                response = it,
                                handle = { newCodeMap ->
                                    newCodeMap.forEach { (path, _) ->
                                        completionNotes += ("<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated")
                                    }
                                },
                                model = chatInterface,
                                defaultFile = defaultFile,
                                processor = orchestrationConfig.processor,
                            ) + acceptButtonFooter(task.ui) {
                                task.complete()
                                semaphore.release()
                            }
                        })
                    }
                    transcript?.flush()
                }
                task.placeholder
            }
        }

        try {
            semaphore.acquire()
            // Write final completion notes to transcript
            transcript?.write("\n## Completion Notes\n\n".toByteArray())
            transcript?.write(completionNotes.joinToString("\n").toByteArray())
            transcript?.close()
            resultFn(completionNotes.joinToString("\n"))
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    fun getDefaultFile() =
        if (((executionConfig?.related_files ?: listOf()) + (executionConfig?.files ?: listOf())).isEmpty()) {
            null
        } else if (((executionConfig?.related_files ?: listOf()) + (executionConfig?.files
                ?: listOf())).distinct().size == 1
        ) {
            ((executionConfig?.related_files ?: listOf()) + (executionConfig?.files ?: listOf())).last()
        } else if ((executionConfig?.files ?: listOf()).distinct().size == 1) {
            (executionConfig?.files ?: listOf()).first()
        } else {
            null
        }

    companion object {
        private val log = LoggerFactory.getLogger(FileModificationTask::class.java)

        val FileModification = TaskType(
            "FileModification",
            "File",
            FileModificationTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Create new files or modify existing code with AI-powered assistance",
            """
                      Creates or modifies source files with AI assistance while maintaining code quality.
                      <ul>
                        <li>Shows proposed changes in diff format for easy review</li>
                        <li>Supports both automated application and manual approval modes</li>
                        <li>Maintains project coding standards and style consistency</li>
                        <li>Handles complex multi-file operations and refactoring</li>
                        <li>Provides clear documentation of all changes with rationale</li>
                        <li>Implements proper error handling and edge cases</li>
                        <li>Updates imports and dependencies automatically</li>
                        <li>Preserves existing code formatting and structure</li>
                      </ul>
                    """
        )
        fun String.getGitDiff(): String? {
            return try {
                val process = ProcessBuilder("git", "diff", "HEAD", "--", File(this).name)
                    .directory(File(this).parentFile)
                    .start()
                if (process.waitFor(10, TimeUnit.SECONDS)) {
                    process.inputStream.bufferedReader().readText()
                } else {
                    process.destroy()
                    log.warn("Git diff command timed out for file: ${this}")
                    null
                }
            } catch (e: Exception) {
                log.warn("Failed to get git diff for file: ${this}", e)
                null
            }
        }
    }
}