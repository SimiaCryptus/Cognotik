package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.FileTaskExecutionConfig
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.exists

abstract class AbstractFileTask<T : FileTaskExecutionConfig>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, TaskTypeConfig>(orchestrationConfig, planTask) {

    protected val codeFiles = mutableMapOf<Path, String>()

    abstract class FileTaskExecutionConfig(
      task_type: String? = null,
      task_description: String? = null,
      @Description("REQUIRED: The files to be generated as output for the task (relative paths)") override var files: List<String> = emptyList(),
      @Description("Additional files used to inform the change, including relevant files created by previous tasks") var related_files: List<String>? = null,
      task_dependencies: List<String>? = null,
      state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = task_type,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    protected fun getInputFileCode(fn: (File) -> (CharSequence) = ::formatFileForLLM) = getInputFiles().joinToString("\n\n") { fn(it) }

    protected fun getInputFiles(): List<File> =
        ((executionConfig?.related_files ?: listOf()) + (executionConfig?.files ?: listOf()))
            .flatMap { pattern: String ->
                if (root.resolve(pattern).exists()) {
                    return@flatMap listOf(root.resolve(pattern).toFile())
                }
                val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
                (FileSelectionUtils.filteredWalk(root.toFile()) {
                    //path -> matcher.matches(root.relativize(path.toPath())) && !FileSelectionUtils.isLLMIgnored(path.toPath())
                    when {
                        FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                        it.isDirectory -> true
                        !matcher.matches(root.relativize(it.toPath())) -> false
                        else -> true
                    }
                })
            }.filter { file ->
                file.isFile && file.exists() && !isIgnored(file)
            }
            .distinct()
            .filterNotNull()
            .sortedBy { it }


    protected open fun isIgnored(file: File): Boolean = when (file.extension) {
        /* Common Binary Files */
        "class", "jar", "exe", "dll", "bin", "img", "iso", "zip", "tar", "gz", "7z" -> true
        /* Common Image and Media */
        "png", "jpg", "jpeg", "gif", "bmp", "tiff", "mp4", "mp3", "avi", "mov", "wmv", "flv", "mkv" -> true
        /* Binary Documents */
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> false // Not ignored, but may be extracted
        else -> false
    }

    /**
     * Formats the content of a file for inclusion in the LLM context.
     * Uses Markdown headers and code blocks.
     */
    protected open fun formatFileForLLM(relativePath: File): CharSequence = try {
        val file = root.toFile().resolve(relativePath)
        val content = if (!isTextFile(file)) {
            extractDocumentContent(file)
        } else {
            codeFiles[file.toPath()] ?: file.readText()
        }
        "# $relativePath\n\n$TRIPLE_TILDE\n$content\n$TRIPLE_TILDE"
    } catch (e: Throwable) {
        log.warn("Error reading file: $relativePath", e)
        ""
    }

    private fun isTextFile(file: File): Boolean {
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
            "sh",
            "bat",
            "ps1",
            "sql",
            "properties",
            "gradle",
            "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }


    companion object {
        private val log = LoggerFactory.getLogger(AbstractFileTask::class.java)
        const val TRIPLE_TILDE = "```"

        fun extractDocumentContent(file: File): String {
            return try {
                file.getDocumentReader().use { reader ->
                    when (reader) {
                        is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                        else -> {
                            val text = reader.getText()
                            when {
                                text.isAsciiPrintable() -> text
                                text.length > (1024 * 512) -> ""
                                else -> text
                            }
                        }
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

fun String.isAsciiPrintable(): Boolean {
    return this.all { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }
}