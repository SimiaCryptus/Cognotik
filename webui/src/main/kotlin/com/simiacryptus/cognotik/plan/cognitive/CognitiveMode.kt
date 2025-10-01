package com.simiacryptus.cognotik.plan.cognitive

// Register the new mode in the package
import com.simiacryptus.cognotik.describe.TypeDescriber
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
    val ui: SocketManager
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

    fun contextData(): List<String>
}

interface CognitiveModeStrategy {
    val inputCnt: Int

    fun getCognitiveMode(
        ui: SocketManager,
        orchestrationConfig: OrchestrationConfig,
        session: Session,
        user: User?,
        describer: TypeDescriber
    ): CognitiveMode
}

enum class CognitiveModeStrategies : CognitiveModeStrategy {
    Chat {
        override val inputCnt: Int get() = ConversationalMode.inputCnt

        override fun getCognitiveMode(
            ui: SocketManager,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?,
            describer: TypeDescriber
        ): CognitiveMode {
            return ConversationalMode(ui, orchestrationConfig, session, user, describer)
        }
    },
    Adaptive {
        override val inputCnt: Int get() = AdaptivePlanningMode.inputCnt

        override fun getCognitiveMode(
            ui: SocketManager,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?,
            describer: TypeDescriber
        ): CognitiveMode {
            return AdaptivePlanningMode(ui, orchestrationConfig, session, user, describer = describer)
        }
    },
    Waterfall {
        override val inputCnt: Int get() = WaterfallMode.inputCnt

        override fun getCognitiveMode(
            ui: SocketManager,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?,
            describer: TypeDescriber
        ): CognitiveMode {
            return WaterfallMode(ui, orchestrationConfig, session, user, describer)
        }
    },
    Hierarchical {
        override val inputCnt: Int get() = HierarchicalPlanningMode.inputCnt

        override fun getCognitiveMode(
            ui: SocketManager,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?,
            describer: TypeDescriber
        ): CognitiveMode {
            return HierarchicalPlanningMode(ui, orchestrationConfig, session, user, describer)
        }
    },
    ;
}