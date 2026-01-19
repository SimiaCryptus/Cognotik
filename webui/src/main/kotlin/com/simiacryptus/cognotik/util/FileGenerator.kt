package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
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
    withHarness(root, javaClass.simpleName) { harness ->
      (listFiles)(root, folder).shuffled()
        .map { source ->
          val target = (targetFile)(source)
          overwriteMode.prepare(source, target)?.let { patchProcessor ->
            concurrencyProcessor.submit {
              try {
                harness.runTask(
                  taskType = FileModification,
                  typeConfig = TaskTypeConfig(task_type = FileModification.name),
                  executionConfig = FileModificationTaskExecutionConfigData(
                    files = listOf(target.toString()),
                    related_files = (relatedFiles)(source),
                    task_description = (generationPrompt)(source, target),
                  ),
                  timeoutMinutes = 5,
                  workspace = root.absoluteFile,
                  initSettings = { session ->
                    harness.initSettings(
                      session = session,
                      workspace = root.absoluteFile,
                      autoFix = true,
                      taskType = FileModification,
                      typeConfig = TaskTypeConfig(task_type = FileModification.name)
                    ).apply {
                      processor = patchProcessor
                    }
                  }
                )
              } catch (e: Exception) {
                log.error("Error running task", e)
              }
            }
          }
        }.toTypedArray().forEach { it?.get() }
    }
  }

  interface OverwriteMode {
    fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File> = emptyList(),
    ): PatchProcessors?
  }


  companion object {
    private val log = LoggerFactory.getLogger(FileGenerator::class.java)
    fun File.lastModified(
      relatedFiles: List<File>,
    ): Long = maxOf(this.lastModified(), relatedFiles.maxOfOrNull { it.lastModified() } ?: 0L)
  }
}

fun withHarness(
  root: File,
  testName: String,
  fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
  smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
  function: (UnifiedHarness) -> Unit
) {
  val workingDir = root.resolve("workspaces/${testName}/test-${PlanHarness.Companion.now()}")
  val harness = object : UnifiedHarness(fastModel = fastModel, smartModel = smartModel) {
    override fun createTempDirectory(prefix: String): File {
      return workingDir.apply { mkdirs() }
    }
  }
  harness.start()
  try {
    function(harness)
  } finally {
    harness.stop()
  }
}

