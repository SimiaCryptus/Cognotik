package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import com.simiacryptus.cognotik.util.PlanHarness.Companion.now
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import java.io.File

object ModeProductPageGenerator {
  val testName = javaClass.simpleName
  private val root = File(".")

  @JvmStatic
  fun main(args: Array<String>) {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
    val files =
      File("webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive").listFilesRecursively()
    val filter = files.filter { it.isFile && it.extension in setOf("kt") }
      .map { it.relativeTo(root.absoluteFile) }
    filter
      .forEach { codeFile ->
        val htmlFile = File("site/cognotik.com").resolve(codeFile.nameWithoutExtension + ".html")
        if (htmlFile.exists()) {
//          htmlFile.renameTo(
//              File("site/cognotik.com")
//                  .resolve("old/" + codeFile.nameWithoutExtension + ".html")
//                  .apply { parentFile.mkdirs() })
          return@forEach
        }
        object : TaskHarness<FileModificationTaskExecutionConfigData, TaskTypeConfig>(
          taskType = FileModification,
          typeConfig = TaskTypeConfig(task_type = FileModification.name),
          executionConfig = FileModificationTaskExecutionConfigData(
            files = listOf(htmlFile.toString()),
            related_files = listOf(
              "docs/cognitive_mode_product_page.md",
              "site/cognotik.com/task_product_page_template.html",
              codeFile.toString()
            ),
            task_description = "Update the product page HTML file ($htmlFile) to reflect the latest implementation in ${codeFile.name}",
          ),
          timeoutMinutes = 5,
          workspace = root.absoluteFile,
          fastModel = GeminiModels.GeminiFlash_30_Preview,
          smartModel = GeminiModels.GeminiFlash_30_Preview,
          user = com.simiacryptus.cognotik.platform.model.defaultUser,
          imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
        ) {
          override fun createWorkspace() = root.resolve("workspaces/${testName}/test-${now()}").apply { mkdirs() }
          override fun <T : TaskExecutionConfig, U : TaskTypeConfig> initSettings(
            session: Session, workspace: File?, autoFix: Boolean,
            taskType: TaskType<T, U>, typeConfig: U, harness: UnifiedHarness
          ) = super.initSettings(session, workspace, autoFix, taskType, typeConfig, harness).apply {
            processor = PatchProcessors.FullReplacement
          }
        }.run()
      }
  }
}