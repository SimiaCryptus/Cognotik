package com.simiacryptus.skyenet.apps.plan.tools.file

import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.plan.AbstractTask
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskConfigBase
import com.simiacryptus.skyenet.apps.plan.tools.file.AbstractFileTask.FileTaskConfigBase
import com.simiacryptus.skyenet.core.util.FileSelectionUtils
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.streams.asSequence

abstract class AbstractFileTask<T : FileTaskConfigBase>(
  planSettings: PlanSettings,
  planTask: T?
) : AbstractTask<T>(planSettings, planTask) {

  open class FileTaskConfigBase(
    task_type: String,
    task_description: String? = null,
    @Description("REQUIRED: The files to be generated as output for the task (relative paths)") val files: List<String>? = null,
    @Description("Additional files used to inform the change, including relevant files created by previous tasks") val related_files: List<String>? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskConfigBase(
    task_type = task_type,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  protected fun getInputFileCode(): String =
    ((taskConfig?.related_files ?: listOf()) + (taskConfig?.files ?: listOf()))
      .flatMap { pattern: String ->
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        Files.walk(root).asSequence()
          .filter { path ->
            matcher.matches(root.relativize(path)) &&
                FileSelectionUtils.isLLMIncludableFile(path.toFile())
          }
          .map { path ->
            root.relativize(path).toString()
          }
          .toList()
      }
      .distinct()
      .sortedBy { it }
      .joinToString("\n\n") { relativePath ->
        val file = root.resolve(relativePath).toFile()
        try {
          "# $relativePath\n\n$TRIPLE_TILDE\n${codeFiles[file.toPath()] ?: file.readText()}\n$TRIPLE_TILDE"
        } catch (e: Throwable) {
          log.warn("Error reading file: $relativePath", e)
          ""
        }
      }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(AbstractFileTask::class.java)
    const val TRIPLE_TILDE = "```"

  }
}