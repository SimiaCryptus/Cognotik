package com.simiacryptus.skyenet.apps.plan.cognitive
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask

/**
 * The CognitiveMode interface defines the “cognitive” strategy
 * which handles user input, initial planning, execution and iterative
 * thought updates.
 */
interface CognitiveMode {
  val ui: ApplicationInterface
  val api: API
  val planSettings: PlanSettings
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
  fun getCognitiveMode(
    ui: ApplicationInterface,
    api: API,
    api2: OpenAIClient,
    planSettings: PlanSettings,
    session: Session,
    user: User?,
    describer: TypeDescriber
  ): CognitiveMode
  
}