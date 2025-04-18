package com.simiacryptus.skyenet.apps.plan.cognitive

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator.Companion.initialPlan
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.io.File

/**
 * A cognitive mode that implements the traditional plan-ahead strategy.
 */
open class PlanAheadMode(
  override val ui: ApplicationInterface,
  override val api: API,
  override val planSettings: PlanSettings,
  override val session: Session,
  override val user: User?,
  private val api2: OpenAIClient,
  val describer: TypeDescriber
) : CognitiveMode {
  private val log = LoggerFactory.getLogger(PlanAheadMode::class.java)

  override fun initialize() {
    log.debug("Initializing PlanAheadMode")
  }
  
  override fun contextData(): List<String> = emptyList()
  
  override fun handleUserMessage(userMessage: String, task: SessionTask) {
    log.debug("Handling user message: $userMessage")
    execute(userMessage, task)
  }

  private fun execute(userMessage: String, task: SessionTask) {
    try {
      val apiClient = api as ChatClient
      apiClient.budget = planSettings.budget ?: 2.0

      val coordinator = PlanCoordinator(
        user = user,
        session = session,
        dataStorage = ui.socketManager?.dataStorage!!,
        ui = ui,
        root = planSettings.workingDir?.let { File(it).toPath() } ?: ui.socketManager!!.dataStorage?.getDataDir(user, session)?.toPath() ?: File(".").toPath(),
        planSettings = planSettings
      )

      val plan = initialPlan(
        codeFiles = coordinator.codeFiles,
        files = coordinator.files,
        root = coordinator.root,
        task = task,
        userMessage = userMessage,
        ui = coordinator.ui,
        planSettings = coordinator.planSettings,
        api = api,
        contextFn = { contextData() },
        describer = describer
      )

      coordinator.executePlan(plan.plan, task, userMessage = userMessage, api = api, api2 = api2)
    } catch (e: Throwable) {
      ui.newTask().error(ui, e)
      log.error("Error in execute", e)
    }
  }

  companion object : CognitiveModeStrategy {
    override fun getCognitiveMode(
      ui: ApplicationInterface,
      api: API,
      api2: OpenAIClient,
      planSettings: PlanSettings,
      session: Session,
      user: User?,
      describer: TypeDescriber
    ) = PlanAheadMode(ui, api, planSettings, session, user, api2, describer)
  }
}