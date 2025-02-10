package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskBreakdownWithPrompt
import com.simiacryptus.skyenet.apps.plan.TaskConfigBase
import com.simiacryptus.skyenet.apps.plan.tools.file.InquiryTask.InquiryTaskConfigData
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File

open class PlanChatApp(
  applicationName: String = "Task Planning Chat v1.0",
  path: String = "/taskChat",
  planSettings: PlanSettings,
  model: ChatModel,
  parsingModel: ChatModel,
  domainName: String = "localhost",
  showMenubar: Boolean = true,
  initialPlan: TaskBreakdownWithPrompt? = null,
  api: API? = null,
  api2: OpenAIClient,
) : PlanAheadApp(
  applicationName = applicationName,
  path = path,
  planSettings = planSettings,
  model = model,
  parsingModel = parsingModel,
  domainName = domainName,
  showMenubar = showMenubar,
  initialPlan = initialPlan,
  api = api,
  api2 = api2,
) {
  override val stickyInput = true
  override val singleInput = false

  private val sessionHandlers: MutableMap<String, ChatSessionHandler> = mutableMapOf()
  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    ui.socketManager?.pool!!.submit {
      val sessionHandler = sessionHandlers.getOrPut(session.sessionId) {
        ChatSessionHandler(
          ui = ui,
          session = session,
          user = user,
          api = api,
          api2 = api2,
        )
      }
      sessionHandler.handleUserMessage(
        userMessage = userMessage,
      )
    }
  }

  private inner class ChatSessionHandler(
    val ui: ApplicationInterface,
    val session: Session,
    val user: User?,
    val api: API,
    val api2: OpenAIClient,
  ) {
    val messageHistory: MutableList<String> = mutableListOf()

    fun handleUserMessage(userMessage: String) {
      try {
        messageHistory.add(userMessage)
        val planSettings = (getSettings(session, user, PlanSettings::class.java) ?: PlanSettings(
          defaultModel = model,
          parsingModel = parsingModel,
          shellCmd = planSettings.shellCmd,
          temperature = planSettings.temperature,
          workingDir = planSettings.workingDir,
          env = planSettings.env,
          githubToken = planSettings.githubToken,
          googleApiKey = planSettings.googleApiKey,
          googleSearchEngineId = planSettings.googleSearchEngineId,
        )).copy(
          allowBlocking = false,
        )
        if (api is ChatClient) api.budget = planSettings.budget
        val coordinator = PlanCoordinator(
          user = user,
          session = session,
          dataStorage = dataStorage,
          ui = ui,
          root = planSettings.workingDir?.let { File(it).toPath() } ?: dataStorage.getDataDir(user, session).toPath(),
          planSettings = planSettings
        )
        val mainTask = ui.newTask()
        val sessionTask = ui.newTask(false).apply { mainTask.verbose(placeholder) }
        val api = (api as ChatClient).getChildClient(sessionTask)
        val plan = PlanCoordinator.initialPlan(
          codeFiles = coordinator.codeFiles,
          files = coordinator.files,
          root = dataStorage.getDataDir(user, session).toPath(),
          task = sessionTask,
          userMessage = userMessage,
          ui = coordinator.ui,
          planSettings = coordinator.planSettings,
          api = api
        )
        val modifiedPlan = addRespondToChatTask(plan.plan)
        val planProcessingState = coordinator.executePlan(
          plan = modifiedPlan,
          task = sessionTask,
          userMessage = userMessage,
          api = api,
          api2 = api2,
        )
        val response = planProcessingState.taskResult["respond_to_chat"]
        if (response != null) {
          mainTask.add(MarkdownUtil.renderMarkdown(response, ui = ui))
          messageHistory.add(response)
        } else {
          mainTask.add("Sorry, I couldn't generate a response.")
          messageHistory.add("Sorry, I couldn't generate a response.")
        }
        mainTask.complete()
      } catch (e: Throwable) {
        ui.newTask().error(ui, e)
        log.warn("Error", e)
      }
    }

  }

  protected open fun addRespondToChatTask(plan: Map<String, TaskConfigBase>): Map<String, TaskConfigBase> {
    val tasksByID = plan.toMutableMap()
    val respondTaskId = "respond_to_chat"

    tasksByID[respondTaskId] = InquiryTaskConfigData(
      task_description = "Respond to the user's chat message based on the executed plan",
      task_dependencies = tasksByID.keys.toList()
    )

    return tasksByID
  }

  companion object {
    private val log = LoggerFactory.getLogger(PlanChatApp::class.java)
  }
}