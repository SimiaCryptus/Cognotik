package com.simiacryptus.skyenet.apps.graph

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.apps.general.PlanAheadApp
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.tools.graph.GraphBasedPlanningTask.GraphBasedPlanningTaskConfigData
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File

class GraphPlanApp(
  applicationName: String = "Graph-Based Planning",
  path: String = "/graphPlan",
  planSettings: PlanSettings,
  model: ChatModel,
  parsingModel: ChatModel,
  domainName: String = "localhost",
  showMenubar: Boolean = true,
  api: API? = null,
  api2: OpenAIClient,
  private val graphFile: String
) : PlanAheadApp(
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
  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      val planSettings = getSettings(session, user, PlanSettings::class.java)
      if (api is ChatClient) api.budget = planSettings?.budget ?: 2.0
      val task = ui.newTask()
      val api = (api as ChatClient).getChildClient(task)
      PlanCoordinator(
        user = user,
        session = session,
        dataStorage = dataStorage,
        ui = ui,
        root = planSettings?.workingDir?.let { File(it).toPath() } ?: dataStorage.getDataDir(user, session).toPath(),
        planSettings = planSettings!!
      ).executeTask(
        task = GraphBasedPlanningTaskConfigData(
          input_graph_file = graphFile,
          instruction = userMessage,
          task_description = "Execute graph-based planning using input graph and instruction"
        ),
        messages = listOf(userMessage),
        sessionTask = task,
        api = api,
        api2 = api2,
      )
    } catch (e: Throwable) {
      ui.newTask().error(ui, e)
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(GraphPlanApp::class.java)
  }
}