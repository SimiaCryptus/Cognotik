package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class ExceptionFixer(
  val projectRoot: File = File(".").gitRoot()
    ?: throw IllegalStateException("Could not find .git folder in any parent directory"),
  val model: ChatModel = GeminiModels.GeminiFlash_30_Preview,
  val user: User = com.simiacryptus.cognotik.platform.model.defaultUser
) {
  fun fix(throwable: Throwable) {
    val codeFiles = throwable.getCodeFiles(projectRoot)
    val eAsString = throwable.toFullString() ?: return
    object : UnifiedHarness(
      showMenubar = true,
      fastModel = model,
      smartModel = model,
      imageModel = model,
      user = user,
    ) {
      override fun createTempDirectory(prefix: String) = projectRoot
        .resolve("workspaces/${javaClass.simpleName}/test-${PlanHarness.now()}")
        .apply { mkdirs() }
    }.use { harness: UnifiedHarness ->
      try {
        harness.runTask(
          taskType = FileModification,
          timeoutMinutes = 5,
          executionConfig = TaskExecutionConfig(
            task_type = FileModification.name,
            task_description = """$codeFiles\n\n$eAsString"""
          )
        ) { session ->
          harness.createSettings(
            session = session,
            autoFix = true,
            typeConfig = TaskTypeConfig(task_type = FileModification.name),
            workingDir = harness.getRoot(projectRoot, session, FileModification.name).absolutePath
          ).apply {
            processor = PatchProcessors.Fuzzy
          }
        }
      } catch (e: Exception) {
        log.error("Error running task", e)
      }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(ExceptionFixer::class.java)
  }
}

fun Throwable.toFullString(): String {
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
      }.firstNotNullOfOrNull { root ->
        val potentialFile = File(root, classPath)
        if (potentialFile.exists()) potentialFile else null
      }
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
