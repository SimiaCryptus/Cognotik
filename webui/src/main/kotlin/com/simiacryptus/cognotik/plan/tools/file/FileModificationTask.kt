package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.ui.patch.DiffInstrumentor
import com.simiacryptus.cognotik.ui.patch.RealFileSystem
import com.simiacryptus.cognotik.ui.patch.SessionRenderer
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath

import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.Retryable.Companion.async
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class FileModificationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: FileModificationTaskExecutionConfigData?
) : AbstractFileTask<FileModificationTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class FileModificationTaskExecutionConfigData(
        files: List<String> = emptyList(),
        related_files: List<String>? = null,
        @Description("Specific modifications to be made to the files")
        val modifications: Any? = null,
        @Description("Whether to include git diff with HEAD")
        val includeGitDiff: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : FileTaskExecutionConfig(
        task_type = FileModification.name,
        task_description = task_description,
        task_dependencies = task_dependencies,
        related_files = related_files,
        files = files,
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
        formatFileForLLM(file).toString() + (file.toString().getGitDiff()?.let { diff ->
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
        val typeConfig = typeConfig ?: throw RuntimeException("TypeConfig is missing")
        val chatInterface =
            (typeConfig.model?.let<ApiChatModel, ChatInterface> { it.instance() }
                ?: defaultSmart).getChildClient(task)

        val semaphore = Semaphore(0)
        val completionNotes = mutableListOf<String>()
        val transcript = task.newFileOutputStream(transcriptFile())


        try {
            transcript?.write("# File Modification Task Transcript\n\n".toByteArray())

            Retryable(task, process = { task: SessionTask ->
                completionNotes.clear()

                // 1. Prepare Context
                val dependencyContext = agent.executionState?.tasksByDescription?.filter {
                    executionConfig?.task_dependencies?.contains(it.key) == true && it.value is FileModificationTaskExecutionConfigData
                }?.entries?.joinToString("\n\n") {
                    (it.value as FileModificationTaskExecutionConfigData).files?.joinToString("\n") { path ->
                        val file = root.resolve(path).toFile()
                        if (file.exists()) {
                            val relativePath = root.relativize(file.toPath())
                            "## $relativePath\n\n${(codeFiles[file.toPath()] ?: file.readText()).let { content -> "${TRIPLE_TILDE}\n${content}\n${TRIPLE_TILDE}" }}"
                        } else {
                            "File not found: $path"
                        }
                    } ?: ""
                } ?: ""

                val fileContext = getInputFileWithDiff()
                val taskDesc = executionConfig?.task_description ?: ""

                // 2. Log Context to Transcript & UI Tabs
                transcript?.write("""
## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
${dependencyContext.ifBlank { "None" }}

### File Context
${fileContext.ifBlank { "None" }}

### Task Description
$taskDesc
</details>

                """.toByteArray())

                val chatAgent = ChatAgent(
                    name = "FileModification",
                    prompt = getSystemPrompt(), // Extracted for readability
                    model = chatInterface,
                    temperature = this.orchestrationConfig.temperature,
                )

              task.add("Generating modifications...".renderMarkdown())

                // 3. Execute AI
                val codeResult = chatAgent.answer(
                    (messages + listOf(
                        dependencyContext,
                        fileContext,
                        taskDesc,
                    )).filter { it.isNotBlank() }
                )

                // 4. Log Response to Transcript (Best Practice: Use <details>)
                transcript?.write("""
## AI Response
<details>
<summary>Raw Output</summary>

$codeResult
</details>

                """.toByteArray())
                val autoFix = orchestrationConfig.autoFix
                val markdown = renderMarkdown(codeResult, ui = task.ui) {
                  DiffInstrumentor(
                    orchestrationConfig.processor,
                    SessionRenderer(task),
                    RealFileSystem(),
                  ).instrument(
                    root = agent.root,
                    response = it,
                    handle = { newCodeMap: Map<Path, String> ->
                      newCodeMap.forEach { (path, _) ->
                        val note = "<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated"
                        completionNotes += note
                        try {
                          transcript?.write("- $note\n".toByteArray())
                        } catch (e: Exception) {
                          log.warn("Failed to write to transcript for file: $path", e)
                        }
                      }
                    },
                    shouldAutoApply = { autoFix },
                    defaultFile = defaultFile,
                    resolver = ::resolveToRelativePath,
                  )
                }

                if (autoFix) {
                    transcript?.write("\n**Auto-applying changes...**\n".toByteArray())
                  task.complete(markdown)
                    semaphore.release()
                } else {
                  task.add(markdown)
                    // Best Practice: Use acceptButtonFooter for manual review
                  task.complete(acceptButtonFooter(task.ui) {
                    task.complete()
                    semaphore.release()
                  })
                }
                transcript?.flush()
            }.async(task.ui))

            semaphore.acquire()

            // 6. Finalize
            val summary = if (completionNotes.isNotEmpty()) {
                "### Modifications Applied\n" + completionNotes.joinToString("\n") { "* $it" }
            } else {
                "No modifications were applied."
            }

            transcript?.write("\n## Completion\n$summary\n".toByteArray())
            resultFn(summary)

        } catch (e: Throwable) {
            // Best Practice: Triple Log Rule
            task.error(e) // 1. UI
            log.error("Error in FileModificationTask", e) // 2. Log

            // 3. Transcript
            transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
            throw e
        } finally {
            transcript?.flush()
        }
    }

  private fun getSystemPrompt(): String {
      return """
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
    $PROMT_PATCH_FORMAT
        """
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

        @JvmStatic val FileModification = TaskType(
            "FileModification",
            "File",
            FileModificationTask::class.java,
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
                    """,
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

      val PROMT_PATCH_FORMAT = """
        Response format:
        For existing files: Use ${TRIPLE_TILDE}diff code blocks with a header specifying the file path.
        For new files: Use ${TRIPLE_TILDE} code blocks with a header specifying the new file path.
        The content inside the code blocks should be indented - this is CRITICAL for correct parsing.
        The diff format should use + for line additions, - for line deletions.
        Include 2 lines of context before and after every change in diffs.
        Separate code blocks with a single blank line.
        For new files, specify the language for syntax highlighting after the opening triple backticks.
        
        Example:
        
        Here are the modifications:
        
        ### src/utils/existingFile.js
        ${TRIPLE_TILDE}diff
          function existingFunction() {
        -    return 'old result';
        +    return 'new result';
          }
        ${TRIPLE_TILDE}
        
        ### src/utils/newFile.js
        ${TRIPLE_TILDE}js
          function newFunction() {
            return 'new functionality';
          }
        ${TRIPLE_TILDE}
        
        ### src/utils/README.md
        ${TRIPLE_TILDE}md
          # Utility Functions
          This file contains utility functions for the project.
          Example usage:
          ${TRIPLE_TILDE}js
            import { existingFunction, newFunction } from './existingFile.js';
            console.log(existingFunction()); // Outputs: 'new result'
            console.log(newFunction()); // Outputs: 'new functionality'
          ${TRIPLE_TILDE}
        ${TRIPLE_TILDE}"""
    }
}