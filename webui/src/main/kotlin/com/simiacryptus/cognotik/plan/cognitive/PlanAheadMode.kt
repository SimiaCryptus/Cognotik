package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.PlanCoordinator
import com.simiacryptus.cognotik.plan.PlanCoordinator.Companion.initialPlan
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File

/**
 * A cognitive mode that implements the traditional plan-ahead strategy.
 */
open class PlanAheadMode(
    override val ui: ApplicationInterface,
    override val planSettings: PlanSettings,
    override val session: Session,
    override val user: User?,
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
            val coordinator = PlanCoordinator(
                user = user,
                session = session,
                dataStorage = ui.socketManager?.dataStorage!!,
                ui = ui,
                root = planSettings.absoluteWorkingDir?.let { File(it).toPath() }
                    ?: ui.socketManager!!.dataStorage?.getSessionDir(
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
                contextFn = { contextData() },
                describer = describer
            )

            coordinator.executePlan(
                plan = plan.plan,
                task = task,
                userMessage = userMessage
                // Use the budgeted and task-specific client
            )
        } catch (e: Throwable) {
            task.error(e) // Report error on the current task
            log.error("Error in execute", e)
        }
    }

    companion object : CognitiveModeStrategy {
        override val inputCnt = 1
        override fun getCognitiveMode(
            ui: ApplicationInterface,
            planSettings: PlanSettings,
            session: Session,
            user: User?,
            describer: TypeDescriber
        ) = PlanAheadMode(ui, planSettings, session, user, describer)
    }
}