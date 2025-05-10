package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.plan.PlanCoordinator
import com.simiacryptus.cognotik.plan.PlanCoordinator.Companion.initialPlan
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.TypeDescriber
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
            val chatApi = api as? ChatClient
                ?: throw IllegalStateException("PlanAheadMode requires a ChatClient API implementation.")
            val apiClient = chatApi.getChildClient(task) // Create a task-specific child client
            apiClient.budget = planSettings.budget ?: 2.0 // Set budget on the child client

            val coordinator = PlanCoordinator(
                user = user,
                session = session,
                dataStorage = ui.socketManager?.dataStorage!!,
                ui = ui,
                root = planSettings.absoluteWorkingDir?.let { File(it).toPath() } ?: ui.socketManager!!.dataStorage?.getDataDir(
                    user,
                    session
                )?.toPath() ?: File(".").toPath(),
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
                api = apiClient, // Use the budgeted and task-specific client
                contextFn = { contextData() },
                describer = describer
            )

            coordinator.executePlan(
                plan = plan.plan,
                task = task,
                userMessage = userMessage,
                api = apiClient, // Use the budgeted and task-specific client
                api2 = api2
            )
        } catch (e: Throwable) {
            task.error(ui, e) // Report error on the current task
            log.error("Error in execute", e)
        }
    }

    companion object : CognitiveModeStrategy {
        override val singleInput: Boolean = true
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