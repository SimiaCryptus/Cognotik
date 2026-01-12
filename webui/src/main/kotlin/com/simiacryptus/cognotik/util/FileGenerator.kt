package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import java.io.File

abstract class FileGenerator {
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
    generationPrompt: (File, File) -> String
  ) {
    withHarness(root, javaClass.simpleName) { harness ->
      (listFiles)(root, folder)
        .forEach { source ->
          val target = (targetFile)(source)
          overwriteMode.prepare(source, target)?.let { patchProcessor ->
            harness.runTask(
              taskType = FileModificationTask.Companion.FileModification,
              typeConfig = TaskTypeConfig(task_type = FileModificationTask.Companion.FileModification.name),
              executionConfig = FileModificationTask.FileModificationTaskExecutionConfigData(
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
                  taskType = FileModificationTask.Companion.FileModification,
                  typeConfig = TaskTypeConfig(task_type = FileModificationTask.Companion.FileModification.name)
                ).apply {
                  processor = patchProcessor
                }
              }
            )
          }
        }
    }
  }

  interface OverwriteMode {
    fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File> = emptyList(),
    ): PatchProcessors?
  }

  enum class OverwriteModes : OverwriteMode {
    SkipExisting,
    OverwriteExisting,
    OverwriteToUpdate,
    PatchExisting,
    PatchToUpdate;

    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? = when {
      target.exists() -> when (this) {
        SkipExisting -> null
        PatchExisting -> PatchProcessors.Fuzzy

        OverwriteExisting -> {
          target.delete()
          PatchProcessors.FullReplacement
        }

        OverwriteToUpdate -> when {
          source.lastModified(relatedFiles) > target.lastModified() -> {
            target.delete()
            PatchProcessors.FullReplacement
          }

          else -> null
        }

        PatchToUpdate -> when {
          source.lastModified(relatedFiles) > target.lastModified() -> PatchProcessors.Fuzzy
          else -> null
        }
      }

      else -> PatchProcessors.FullReplacement
    }

  }

  fun withHarness(
    root: File,
    testName: String,
    fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    function: (UnifiedHarness) -> Unit
  ) {
    val harness = object : UnifiedHarness(fastModel = fastModel, smartModel = smartModel) {
      override fun createTempDirectory(prefix: String) =
        root.resolve("workspaces/${testName}/test-${PlanHarness.Companion.now()}").apply { mkdirs() }
    }
    harness.start()
    try {
      function(harness)
    } finally {
      harness.stop()
    }
  }

  companion object {
    fun File.lastModified(
      relatedFiles: List<File>,
    ): Long = maxOf(this.lastModified(), relatedFiles.maxOfOrNull { it.lastModified() } ?: 0L)
  }
}