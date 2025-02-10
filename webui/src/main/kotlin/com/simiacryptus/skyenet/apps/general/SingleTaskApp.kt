package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModel
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
import java.io.File

open class SingleTaskApp(
  applicationName: String = "Single Task App",
  path: String = "/singleTask",
  planSettings: PlanSettings,
  model: ChatModel,
  parsingModel: ChatModel,
  domainName: String = "localhost",
  showMenubar: Boolean = true,
  api: API? = null,
  api2: OpenAIClient,
) : PlanChatApp(
  applicationName = applicationName,
  path = path,
  planSettings = planSettings,
  model = model,
  parsingModel = parsingModel,
  domainName = domainName,
  showMenubar = showMenubar,
  api = api,
  api2 = api2
) {
  open fun contextData() = emptyList<String>()
  private val log = org.slf4j.LoggerFactory.getLogger(SingleTaskApp::class.java)
  override val stickyInput = false
  override val singleInput = true

  init {
    val enabledTasks = TaskType.getAvailableTaskTypes(planSettings)
    require(enabledTasks.size == 1) {
      "SingleTaskApp requires exactly one enabled task type. Found ${enabledTasks.size}: ${enabledTasks.map { it.name }}"
    }
  }

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      log.debug("Received user message: ${JsonUtil.toJson(userMessage)}")
      val task = ui.newTask(true)
      Retryable(ui, task) {
        val task = ui.newTask(false)
        ui.socketManager?.pool?.submit {
          run(task, userMessage, api, session, user, ui)
        }
        task.placeholder
      }
    } catch (e: Exception) {
      log.error("Error processing user message", e)
      ui.newTask().add(renderMarkdown("An error occurred while processing your message: ${e.message}"))
    }
  }

  private fun run(
    task: SessionTask,
    userMessage: String,
    api: API,
    session: Session,
    user: User?,
    ui: ApplicationInterface
  ) {
    task.echo(renderMarkdown(userMessage))
    val api = (api as ChatClient).getChildClient(task)
    val settings = getSettings(session, user, PlanSettings::class.java) ?: planSettings
    api.budget = settings.budget
    val coordinator = PlanCoordinator(
      user = user,
      session = session,
      dataStorage = dataStorage,
      ui = ui,
      root = settings.workingDir?.let { File(it).toPath() } ?: dataStorage.getDataDir(user, session).toPath(),
      planSettings = settings
    )
    try {
      val taskType = TaskType.getAvailableTaskTypes(settings).first()
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
            "\n    ${it.name}:\n      ${describer.describe(it.taskDataClass).replace("\n", "\n  ")}\n    ".trim()
          }
      )
      val input = listOf(userMessage) + contextData() +
              listOf(
                "Please choose the next single task to execute based on the current status.\nIf there are no tasks to execute, return {}."
              )
      val taskConfig = actor.answer(input, api).obj
      task.add(renderMarkdown("Executing ${taskType.name}:\n```json\n${JsonUtil.toJson(taskConfig)}\n```"))
      val taskImpl = TaskType.getImpl(settings, taskConfig)
      val result = StringBuilder()
      taskImpl.run(
        agent = coordinator,
        messages = listOf(
          userMessage
        ),
        task = task,
        api = api,
        resultFn = { result.append(it) },
        api2 = api2,
        planSettings = settings,
      )
      task.add(renderMarkdown("Task completed. Result:\n${result}"))
      task.complete()
    } catch (e: Exception) {
      log.error("Error executing task", e)
      task.error(null, e)
    }
  }

  protected open fun logDebug(message: String, data: Any? = null) {
    if (data != null) {
      log.debug("$message: ${JsonUtil.toJson(data)}")
    } else {
      log.debug(message)
    }
  }
}