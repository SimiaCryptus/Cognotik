package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class ExceptionFixer(
  val projectRoot: File = File(".").gitRoot() ?: throw IllegalStateException("Could not find .git folder in any parent directory"),
  val related_files: List<String> = emptyList()
) {
  fun fix(throwable: Throwable) {
    val codeFiles = throwable.getCodeFiles(projectRoot)
    val eAsString = throwable.toFullString() ?: return
    withHarness(
      root = projectRoot,
      testName = "SmartFixingExceptions",
    ) { harness ->
      try {
        harness.runTask<FileModificationTask.FileModificationTaskExecutionConfigData, FileModificationTask.FileModificationTypeConfig>(
          taskType = FileModificationTask.Companion.FileModification,
          typeConfig = TaskTypeConfig(task_type = FileModificationTask.Companion.FileModification.name),
          executionConfig = FileModificationTask.FileModificationTaskExecutionConfigData(
            files = codeFiles.map { it.relativeTo(projectRoot).toString() },
            related_files = related_files,
            task_description = eAsString,
          ),
          timeoutMinutes = 5,
          workspace = projectRoot.absoluteFile,
          initSettings = { session ->
            harness.initSettings<FileModificationTask.FileModificationTaskExecutionConfigData, FileModificationTask.FileModificationTypeConfig>(
              session = session,
              workspace = projectRoot.absoluteFile,
              autoFix = true,
              taskType = FileModificationTask.Companion.FileModification,
              typeConfig = TaskTypeConfig(task_type = FileModificationTask.Companion.FileModification.name)
            ).apply {
              processor = PatchProcessors.Fuzzy
            }
          }
        )
      } catch (e: Exception) {
        FileGenerator.Companion.log.error("Error running task", e)
      }
    }
  }
}

fun Throwable.toFullString(): String? {
  val outputStream = ByteArrayOutputStream()
  this.printStackTrace(PrintStream(outputStream))
  return outputStream.toString("UTF-8") ?: this.toString()
}

fun Throwable.getCodeFiles(projectRoot: File): List<File> {
  val visited = mutableSetOf<Throwable>()
  fun helper(t: Throwable): List<File> {
    if (visited.contains(t)) return emptyList()
    visited.add(t)
    val files = t.stackTrace?.mapNotNull { element ->
      val classPath = element.className.replace('.', '/') + ".kt"
      projectRoot.walkTopDown().filter { file ->
        file.isDirectory && file.name == "kotlin" &&
            file.parentFile?.name == "main" &&
            file.parentFile?.parentFile?.name == "src"
      }.asSequence().mapNotNull { root ->
        val potentialFile = File(root, classPath)
        if (potentialFile.exists()) potentialFile else null
      }.firstOrNull()
    }?.distinct() ?: emptyList()
    t.cause?.let { helper(it) }
    t.suppressed.forEach { helper(it) }
    return files
  }
  return helper(this)
}

fun File.gitRoot(): File? {
  var current: File? = this.absoluteFile
  while (current != null) {
    if (File(current, ".git").exists()) {
      return current
    }
    current = current.parentFile
  }
  return null
}
