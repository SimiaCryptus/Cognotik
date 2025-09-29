package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskOrchestrator.Companion.initialPlan
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File

/**
 * A cognitive mode that implements the traditional plan-ahead strategy.
 */
open class WaterfallMode(
    override val ui: SocketManager,
    override val orchestrationConfig: OrchestrationConfig,
    override val session: Session,
    override val user: User?,
    val describer: TypeDescriber
) : CognitiveMode {
    private val log = LoggerFactory.getLogger(WaterfallMode::class.java)

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
            val coordinator = TaskOrchestrator(
                user = user,
                session = session,
                dataStorage = ui.dataStorage!!,
                root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                    ?: ui.dataStorage?.getSessionDir(
                        user,
                        session
                    )?.toPath() ?: File(".").toPath(),
                orchestrationConfig = orchestrationConfig
            )

            val plan = initialPlan(
                codeFiles = coordinator.codeFiles,
                files = coordinator.files,
                root = coordinator.root,
                task = task,
                userMessage = userMessage,
                orchestrationConfig = coordinator.orchestrationConfig,
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
            ui: SocketManager,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?,
            describer: TypeDescriber
        ) = WaterfallMode(ui, orchestrationConfig, session, user, describer)
    }
}