package com.simiacryptus.cognotik.plan.cognitive

// Register the new mode in the package
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager

/**
 * The CognitiveMode interface defines the “cognitive” strategy
 * which handles user input, initial planning, execution and iterative
 * thought updates.
 */
interface CognitiveMode {
    val task: SessionTask
    val orchestrationConfig: OrchestrationConfig
    val session: Session
    val user: User?

    /**
     * Initialize the internal cognitive state.
     */
    fun initialize()

    /**
     * Handle a user message and trigger the appropriate planning or execution.
     */
    fun handleUserMessage(userMessage: String, task: SessionTask)

    /**
     * Get the context data accumulated during execution.
     * This is useful for sub-planning tasks to collect results.
     */
    fun contextData(): List<String>
}

interface CognitiveModeStrategy {
    val inputCnt: Int

    fun getCognitiveMode(
        task: SessionTask,
        orchestrationConfig: OrchestrationConfig,
        session: Session,
        user: User?
    ): CognitiveMode
}

enum class CognitiveModeStrategies : CognitiveModeStrategy {
    Chat {
        override val inputCnt: Int get() = ConversationalMode.inputCnt

        override fun getCognitiveMode(
            task: SessionTask,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?
        ): CognitiveMode {
            return ConversationalMode(task, orchestrationConfig, session, user)
        }
    },
    Adaptive {
        override val inputCnt: Int get() = AdaptivePlanningMode.inputCnt

        override fun getCognitiveMode(
            task: SessionTask,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?
        ): CognitiveMode {
            return AdaptivePlanningMode(task, orchestrationConfig, session, user)
        }
    },
    Waterfall {
        override val inputCnt: Int get() = WaterfallMode.inputCnt

        override fun getCognitiveMode(
            task: SessionTask,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?
        ): CognitiveMode {
            return WaterfallMode(task, orchestrationConfig, session, user)
        }
    },
    Hierarchical {
        override val inputCnt: Int get() = HierarchicalPlanningMode.inputCnt

        override fun getCognitiveMode(
            task: SessionTask,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?
        ): CognitiveMode {
            return HierarchicalPlanningMode(task, orchestrationConfig, session, user)
        }
    },
    ;
}