package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import com.simiacryptus.cognotik.webui.session.SessionTask

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

class CognitiveModeTypeSerializer : DynamicEnumSerializer<CognitiveModeType<*>>(CognitiveModeType::class.java)
class CognitiveModeTypeDeserializer : DynamicEnumDeserializer<CognitiveModeType<*>>(CognitiveModeType::class.java)

