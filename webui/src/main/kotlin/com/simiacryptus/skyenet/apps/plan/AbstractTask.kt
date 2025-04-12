package com.simiacryptus.skyenet.apps.plan

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.skyenet.apps.plan.TaskConfigBase
import com.simiacryptus.skyenet.set
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import java.io.File
import java.nio.file.Path


abstract class AbstractTask<T : TaskConfigBase>(
  val planSettings: PlanSettings,
  val taskConfig: T?
) {
  var state: TaskState? = TaskState.Pending
  protected val codeFiles = mutableMapOf<Path, String>()

  protected open val root: Path
    get() = planSettings.workingDir?.let { File(it).toPath() }
      ?: throw IllegalStateException("Working directory not set")
  
  open val taskSettings: TaskSettingsBase
    get() = planSettings.taskSettings.get(taskConfig?.task_type!!)!!

  enum class TaskState {
    Pending,
    InProgress,
    Completed,
  }

  open fun getPriorCode(planProcessingState: PlanProcessingState) =
    taskConfig?.task_dependencies?.joinToString("\n\n\n") { dependency ->
      "# $dependency\n\n${planProcessingState.taskResult[dependency] ?: ""}"
    } ?: ""


  protected fun acceptButtonFooter(ui: ApplicationInterface, fn: () -> Unit): String {
    val footerTask = ui.newTask(false)
    lateinit var textHandle: StringBuilder
    textHandle = footerTask.complete(ui.hrefLink("Accept", classname = "href-link cmd-button") {
      try {
        textHandle.set("""<div class="cmd-button">Accepted</div>""")
        footerTask.complete()
      } catch (e: Throwable) {
        log.warn("Error", e)
      }
      fn()
    })!!
    return footerTask.placeholder
  }

  abstract fun promptSegment(): String

  abstract fun run(
    agent: PlanCoordinator,
    messages: List<String> = listOf(),
    task: SessionTask,
    api: ChatClient,
    resultFn: (String) -> Unit,
    api2: OpenAIClient,
    planSettings: PlanSettings,
  )

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(AbstractTask::class.java)
  }
}