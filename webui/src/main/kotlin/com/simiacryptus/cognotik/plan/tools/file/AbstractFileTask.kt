package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.input.PaginatedDocumentReader
import com.simiacryptus.cognotik.input.getReader
import com.simiacryptus.cognotik.plan.AbstractTask
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.FileTaskExecutionConfig
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystems

abstract class AbstractFileTask<T : FileTaskExecutionConfig>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, TaskTypeConfig>(orchestrationConfig, planTask) {

abstract class FileTaskExecutionConfig(
        task_type: String? = null,
        task_description: String? = null,
        @Description("REQUIRED: The files to be generated as output for the task (relative paths)") val files: List<String>? = null,
        @Description("Additional files used to inform the change, including relevant files created by previous tasks") val related_files: List<String>? = null,
        @Description("Whether to extract text content from non-text files (PDF, HTML, etc.)") val extractContent: Boolean = false,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = task_type,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    protected fun getInputFileCode(): String {
        val strings = (executionConfig?.related_files ?: listOf()) + (executionConfig?.files ?: listOf())
        val flatMap = strings
            .flatMap { pattern: String ->
                val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
                (FileSelectionUtils.filteredWalk(root.toFile()) {
                    //path -> matcher.matches(root.relativize(path.toPath())) && !FileSelectionUtils.isLLMIgnored(path.toPath())
                    when {
                        FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                        matcher.matches(root.relativize(it.toPath())) -> true
                        it.isDirectory -> true
                        else -> false
                    }
                })
            }
        val filter = flatMap.filter { file ->
            file.isFile && file.exists()
        }
        return filter
            .distinct()
            .sortedBy { it }
            .joinToString("\n\n") { relativePath ->
                val file = root.toFile().resolve(relativePath)
                try {
                    val content = if (executionConfig?.extractContent == true && !isTextFile(file)) {
                        extractDocumentContent(file)
                    } else {
                        codeFiles[file.toPath()] ?: file.readText()
                    }
                    "# $relativePath\n\n$TRIPLE_TILDE\n$content\n$TRIPLE_TILDE"
                } catch (e: Throwable) {
                    log.warn("Error reading file: $relativePath", e)
                    ""
                }
            }
    }

    private fun isTextFile(file: java.io.File): Boolean {
        val textExtensions = setOf(
            "txt",
            "md",
            "kt",
            "java",
            "js",
            "ts",
            "py",
            "rb",
            "go",
            "rs",
            "c",
            "cpp",
            "h",
            "hpp",
            "css",
            "html",
            "xml",
            "json",
            "yaml",
            "yml",
            "properties",
            "gradle",
            "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }
  protected fun transcript(task: SessionTask): FileOutputStream? {
    val (link, file) = task.createFile("transcript.md")
    val markdownTranscript = file?.outputStream()
    task.complete(
      "Writing transcript to <a href='$link' target='_blank'>$link</a> " +
          "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
          "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
    )
    return markdownTranscript
  }


  companion object {
        private val log = LoggerFactory.getLogger(AbstractFileTask::class.java)
        const val TRIPLE_TILDE = "```"

        fun extractDocumentContent(file: File): String {
            return try {
                file.getReader().use { reader ->
                    when (reader) {
                        is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                        else -> reader.getText()
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
                try {
                    file.readText()
                } catch (e2: Exception) {
                    "Error reading file: ${e2.message}"
                }
            }
        }
    }
}