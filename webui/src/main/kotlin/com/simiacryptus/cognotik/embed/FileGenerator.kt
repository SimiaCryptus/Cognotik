package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors

open class FileGenerator {
  fun run(
    root: File,
    folder: File,
    listFiles: (File, File) -> List<File> = { root, folder ->
      folder.listFilesRecursively()
        .filter { it.isFile && it.extension in setOf("kt") }
        .map { it.relativeTo(root.absoluteFile) }
    },
    targetFile: (File) -> File = { it },
    overwriteMode: OverwriteMode = OverwriteModes.SkipExisting,
    relatedFiles: (File) -> List<String>,
    generationPrompt: (File, File) -> String,
    concurrencyLimit: Int = 4
  ) {
    val concurrencyProcessor = FixedConcurrencyProcessor(
      pool = Executors.newCachedThreadPool(),
      concurrencyLimit = concurrencyLimit
    )
    object : UnifiedHarness(
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      smartModel = GeminiModels.GeminiFlash_30_Preview
    ) {
      override fun createTempDirectory(prefix: String) = root
        .resolve("workspaces/${javaClass.simpleName}/test-${PlanHarness.now()}")
        .apply { mkdirs() }
    }.use { harness: UnifiedHarness ->
      (listFiles)(root, folder).shuffled()
        .map { source ->
          val target = (targetFile)(source)
          overwriteMode.prepare(source, target)?.let { patchProcessor ->
            concurrencyProcessor.submit {
              try {
                harness.runTask(
                  taskType = FileModification,
                  timeoutMinutes = 5,
                  executionConfig = TaskExecutionConfig(task_type = FileModification.name)
                ) { session ->
                  harness.initSettings(
                    session = session,
                    autoFix = true,
                    typeConfig = TaskTypeConfig(task_type = FileModification.name),
                    workingDir = harness.getRoot(root, session, FileModification.name).absolutePath
                  ).apply {
                    processor = patchProcessor
                  }
                }
              } catch (e: Exception) {
                log.error("Error running task", e)
              }
            }
          }
        }.toTypedArray().forEach { it?.get() }
    }
  }


  companion object {
    private val log = LoggerFactory.getLogger(FileGenerator::class.java)
    fun File.lastModified(
      relatedFiles: List<File>,
    ): Long = maxOf(this.lastModified(), relatedFiles.maxOfOrNull { it.lastModified() } ?: 0L)
  }
}

