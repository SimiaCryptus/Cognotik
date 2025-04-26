package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.plan.PlanCoordinator
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A cognitive mode that executes tasks based on user input while maintaining conversation history.
 */
open class TaskChatMode(
  override val ui: ApplicationInterface,
  override val api: API,
  override val planSettings: PlanSettings,
  override val session: Session,
  override val user: User?,
  private val api2: OpenAIClient,
  val describer: TypeDescriber
) : CognitiveMode {
  private val log = LoggerFactory.getLogger(TaskChatMode::class.java)
  // Message history for the conversation
  private val messagesLock = Any()
  private val messages = ConcurrentLinkedQueue<ApiModel.ChatMessage>()
  // Buffer for queued messages while processing
  private val messageBuffer = ConcurrentLinkedQueue<String>()
  // Flag to track if we're currently processing a task
  private var isProcessing = false
  // System prompt for task selection
  private val systemPrompt = "Given the following input, choose ONE task to execute. " +
      "Do not create a full plan, just select the most appropriate task types for the given input."

  override fun initialize() {
    // Validate that only one task type is enabled
    val enabledTasks = TaskType.getAvailableTaskTypes(planSettings)
    require(enabledTasks.size == 1) {
      "TaskChatMode requires exactly one enabled task type. Found ${enabledTasks.size}: ${enabledTasks.map { it.name }}"
    }
    log.debug("TaskChatMode initialized with task type: ${enabledTasks.first().name}")
    
    // Initialize conversation with system message
    synchronized(messagesLock) {
      messages.add(ApiModel.ChatMessage(ApiModel.Role.system, systemPrompt.toContentList()))
    }
  }

  override fun handleUserMessage(userMessage: String, task: SessionTask) {
    log.debug("Handling user message: ${JsonUtil.toJson(userMessage)}")
    // If already processing a message, add to buffer and return
    synchronized(messagesLock) {
      if (isProcessing) {
        log.debug("Already processing a task, adding message to buffer: ${userMessage}")
        messageBuffer.add(userMessage)
        return
      }
      isProcessing = true
    }
    
    // Add user message to conversation history
    synchronized(messagesLock) {
      messages.add(ApiModel.ChatMessage(ApiModel.Role.user, userMessage.toContentList()))
    }
    
    Retryable(ui, task) {
      val subtask = ui.newTask(false)
      ui.socketManager?.pool?.submit {
        execute(subtask, userMessage)
      }
      subtask.placeholder
    }
  }

  private fun execute(task: SessionTask, userMessage: String) {
    task.echo(renderMarkdown(userMessage))
    val apiClient = (api as ChatClient).getChildClient(task)
    apiClient.budget = planSettings.budget

    
    val coordinator = PlanCoordinator(
      user = user,
      session = session,
      dataStorage = ui.socketManager?.dataStorage!!,
      ui = ui,
      root = planSettings.workingDir?.let { File(it).toPath() } ?: ui.socketManager?.dataStorage?.getDataDir(user, session)?.toPath() ?: File(".").toPath(),
      planSettings = planSettings
    )

    
    try {
      val taskType = TaskType.getAvailableTaskTypes(planSettings).first()

      
      // Build the task selection prompt
      val taskSelectionPrompt =
        "Available task types:\n" +
            TaskType.getAvailableTaskTypes(coordinator.planSettings).joinToString("\n") { taskType ->
              "* ${TaskType.getImpl(coordinator.planSettings, taskType).promptSegment()}"
            } + "\nChoose the most suitable task types and provide details of how they should be executed."

      val actor = ParsedActor(
        name = "SingleTaskChooser",
        resultClass = taskType.taskDataClass,
        prompt = taskSelectionPrompt,
        model = coordinator.planSettings.defaultModel,
        parsingModel = coordinator.planSettings.parsingModel,
        temperature = coordinator.planSettings.temperature,
        describer = describer,
        parserPrompt = "Task Subtype Schema:\n" + TaskType.getAvailableTaskTypes(coordinator.planSettings)
          .joinToString("\n\n") {
            "\n    ${it.name}:\n      ${
              describer.describe(it.taskDataClass).lineSequence()
                .map {
                  when {
                    it.isBlank() -> {
                      when {
                        it.length < "  ".length -> "  "
                        else -> it
                      }
                    }
                    else -> "  " + it
                  }
                }
                .joinToString("\n")
            }\n    ".trim()
          }
      )

      
      // Prepare input with conversation context
      val input = getConversationContext() +
          listOf(
            "Please choose the next single task to execute based on the current status.\nIf there are no tasks to execute, return {}."
          )

      
      val taskConfig = actor.answer(input, apiClient).obj
      task.add(renderMarkdown("Executing ${taskType.name}:\n```json\n${JsonUtil.toJson(taskConfig)}\n```"))

      val taskImpl = TaskType.getImpl(planSettings, taskConfig)
      val result = StringBuilder()

      val tabs = TabbedDisplay(task)
      taskImpl.run(
        agent = coordinator,
        messages = listOf(userMessage),
        task = ui.newTask(false).apply {
          tabs["Task"] = placeholder
        },
        api = apiClient,
        resultFn = { result.append(it) },
        api2 = api2,
        planSettings = planSettings,
      )
      ui.newTask(false).apply {
        tabs["Output"] = placeholder
        complete(renderMarkdown("Task completed. Result:\n${result}"))
      }
      // Add assistant response to conversation history
      val assistantResponse = "Task executed: ${taskType.name}\n${result}"
      synchronized(messagesLock) {
        messages.add(ApiModel.ChatMessage(ApiModel.Role.assistant, assistantResponse.toContentList()))
      }
      // Complete the task
      task.complete()
      // Process any buffered messages
      processBufferedMessages()
    } catch (e: Exception) {
      log.error("Error executing task", e)
      task.error(null, e)
      // Reset processing flag even if there was an error
      synchronized(messagesLock) {
        isProcessing = false
      }
    }
  }
  /**
   * Process any messages that were buffered while a task was running
   */
  private fun processBufferedMessages() {
    synchronized(messagesLock) {
      if (messageBuffer.isNotEmpty()) {
        // Concatenate all buffered messages
        val combinedMessage = messageBuffer.joinToString("\n\n")
        log.debug("Processing buffered messages: ${messageBuffer.size} messages")
        // Clear the buffer
        messageBuffer.clear()
        // Reset processing flag to allow the new task to start
        isProcessing = false
        // Process the combined message
        val newTask = ui.newTask(false)
        ui.socketManager?.pool?.submit {
          handleUserMessage(combinedMessage, newTask)
        }
      } else {
        // No buffered messages, just reset the flag
        isProcessing = false
      }
    }
  }
  
  
  /**
   * Gets the current conversation context as a list of messages
   */
  private fun getConversationContext(): List<String> {
    val contextMessages = synchronized(messagesLock) {
      messages.toList()
    }
    
    return contextMessages.map { message ->
      "${message.role?.name?.uppercase()}: ${message.content?.joinToString("") { it.text ?: "" } ?: ""}"
    }
  }
  
  /**
   * Provides context data for the conversation
   */
  override fun contextData(): List<String> {
    return getConversationContext()
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
    ) = TaskChatMode(ui, api, planSettings, session, user, api2, describer)
  }
}