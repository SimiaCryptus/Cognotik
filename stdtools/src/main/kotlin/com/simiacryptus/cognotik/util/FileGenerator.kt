package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.docops.UpdateMode
import com.simiacryptus.cognotik.docops.UpdateModes
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors

open class FileGenerator {
  fun run(
    root: File,
    folder: File,
    fastModel: ChatModel,
    smartModel: ChatModel,
    imageModel: ChatModel,
    listFiles: (File, File) -> List<File> = { root, folder ->
      folder.listFilesRecursively()
        .filter { it.isFile && it.extension in setOf("kt") }
        .map { it.relativeTo(root.absoluteFile) }
    },
    targetFile: (File) -> File = { it },
    updateMode: UpdateMode = UpdateModes.SkipExisting,
    concurrencyLimit: Int = 4,
    user: User = ApplicationServicesConfig.defaultUser
  ) {
    val concurrencyProcessor = FixedConcurrencyProcessor(
      pool = Executors.newCachedThreadPool(),
      concurrencyLimit = concurrencyLimit
    )
    object : UnifiedHarness(
      showMenubar = true,
      user = user,
      fastModel = fastModel,
      smartModel = smartModel,
      imageModel = imageModel,
    ) {
      override fun createTempDirectory(prefix: String) = root
        .resolve("workspaces/${javaClass.simpleName}/test-${PlanHarness.now()}")
        .apply { mkdirs() }
    }.use { harness: UnifiedHarness ->
      (listFiles)(root, folder).shuffled()
        .map { source ->
          val target = (targetFile)(source)
          updateMode.prepare(source, target)?.let { patchProcessor ->
            concurrencyProcessor.submit {
              try {
                harness.runTask(
                  taskType = FileModification,
                  timeoutMinutes = 5,
                  executionConfig = TaskExecutionConfig(task_type = FileModification.name)
                ) { session ->
                  harness.createSettings(
                    session = session,
                    autoFix = true,
                    typeConfig = TaskTypeConfig(task_type = FileModification.name),
                    workingDir = harness.getRoot(root, session, FileModification.name).absolutePath
                  ).apply {
                    processor = patchProcessor.patchProcessor
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
  }
}

