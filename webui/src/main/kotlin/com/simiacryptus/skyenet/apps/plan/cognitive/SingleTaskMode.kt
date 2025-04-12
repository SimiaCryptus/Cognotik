package com.simiacryptus.skyenet.apps.plan.cognitive

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskType
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.session.getChildClient
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File

/**
 * A cognitive mode that executes a single task based on user input.
 */
class SingleTaskMode(
  override val ui: ApplicationInterface,
  override val api: API,
  override val planSettings: PlanSettings,
  override val session: Session,
  override val user: User?,
  private val api2: com.simiacryptus.jopenai.OpenAIClient
) : CognitiveMode {
  private val log = LoggerFactory.getLogger(SingleTaskMode::class.java)

  override fun initialize() {
    // Validate that only one task type is enabled
    val enabledTasks = TaskType.getAvailableTaskTypes(planSettings)
    require(enabledTasks.size == 1) {
      "SingleTaskMode requires exactly one enabled task type. Found ${enabledTasks.size}: ${enabledTasks.map { it.name }}"
    }
    log.debug("SingleTaskMode initialized with task type: ${enabledTasks.first().name}")
  }

  override fun handleUserMessage(userMessage: String, task: SessionTask) {
    log.debug("Handling user message: ${JsonUtil.toJson(userMessage)}")
    Retryable(ui, task) {
      val subtask = ui.newTask(false)
      ui.socketManager?.pool?.submit {
        execute(subtask, userMessage)
      }
      subtask.placeholder
    }
  }

  private fun execute(task: SessionTask, userMessage: String) {
    task.echo(renderMarkdown(userMessage))
    val apiClient = (api as ChatClient).getChildClient(task)
    apiClient.budget = planSettings.budget

    val coordinator = PlanCoordinator(
      user = user,
      session = session,
      dataStorage = ui.socketManager?.dataStorage!!,
      ui = ui,
      root = planSettings.workingDir?.let { File(it).toPath() } ?: ui.socketManager?.dataStorage?.getDataDir(user, session)?.toPath() ?: File(".").toPath(),
      planSettings = planSettings
    )

    try {
      val taskType = TaskType.getAvailableTaskTypes(planSettings).first()
      val describer = planSettings.describer()

      val prompt =
        "Given the following input, choose ONE task to execute. Do not create a full plan, just select the most appropriate task types for the given input.\nAvailable task types:\n" +
            TaskType.getAvailableTaskTypes(coordinator.planSettings).joinToString("\n") {
              "* ${TaskType.getImpl(coordinator.planSettings, it).promptSegment()}"
            } + "\nChoose the most suitable task types and provide details of how they should be executed."

      val actor = ParsedActor(
        name = "SingleTaskChooser",
        resultClass = taskType.taskDataClass,
        prompt = prompt,
        model = coordinator.planSettings.defaultModel,
        parsingModel = coordinator.planSettings.parsingModel,
        temperature = coordinator.planSettings.temperature,
        describer = describer,
        parserPrompt = "Task Subtype Schema:\n" + TaskType.getAvailableTaskTypes(coordinator.planSettings)
          .joinToString("\n\n") {
            "\n    ${it.name}:\n      ${
              describer.describe(it.taskDataClass).lineSequence()
                .map {
                  when {
                    it.isBlank() -> {
                      when {
                        it.length < "  ".length -> "  "
                        else -> it
                      }
                    }
                    else -> "  " + it
                  }
                }
                .joinToString("\n")
            }\n    ".trim()
          }
      )

      val input = listOf(userMessage) + contextData() +
          listOf(
            "Please choose the next single task to execute based on the current status.\nIf there are no tasks to execute, return {}."
          )

      val taskConfig = actor.answer(input, apiClient).obj
      task.add(renderMarkdown("Executing ${taskType.name}:\n```json\n${JsonUtil.toJson(taskConfig)}\n```"))

      val taskImpl = TaskType.getImpl(planSettings, taskConfig)
      val result = StringBuilder()

      taskImpl.run(
        agent = coordinator,
        messages = listOf(userMessage),
        task = task,
        api = apiClient,
        resultFn = { result.append(it) },
        api2 = api2,
        planSettings = planSettings,
      )

      task.add(renderMarkdown("Task completed. Result:\n${result}"))
      task.complete()
    } catch (e: Exception) {
      log.error("Error executing task", e)
      task.error(null, e)
    }
  }

  protected open fun contextData() = emptyList<String>()

  companion object : CognitiveModeStrategy {
    override fun getCognitiveMode(
      ui: ApplicationInterface,
      api: API,
      api2: OpenAIClient,
      planSettings: PlanSettings,
      session: Session,
      user: User?
    ) = SingleTaskMode(ui, api, planSettings, session, user, api2)
  }
}