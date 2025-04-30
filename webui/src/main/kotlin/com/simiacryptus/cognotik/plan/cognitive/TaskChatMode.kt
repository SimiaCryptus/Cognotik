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

    private val messagesLock = Any()
    private val messages = ConcurrentLinkedQueue<ApiModel.ChatMessage>()

    private val messageBuffer = ConcurrentLinkedQueue<String>()

    private var isProcessing = false

    private val systemPrompt = "Given the following input, choose ONE task to execute and describe it in detail."

    override fun initialize() {
        val enabledTasks = TaskType.getAvailableTaskTypes(planSettings)
        log.debug("TaskChatMode initialized with task types: ${enabledTasks.joinToString(", ") { it.name }}")
        synchronized(messagesLock) {
            messages.add(ApiModel.ChatMessage(ApiModel.Role.system, systemPrompt.toContentList()))
        }
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        log.debug("Handling user message: ${JsonUtil.toJson(userMessage)}")

        synchronized(messagesLock) {
            if (isProcessing) {
                log.debug("Already processing a task, adding message to buffer: ${userMessage}")
                messageBuffer.add(userMessage)
                return
            }
            isProcessing = true
        }

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
            root = planSettings.workingDir?.let { File(it).toPath() } ?: ui.socketManager?.dataStorage?.getDataDir(
                user,
                session
            )?.toPath() ?: File(".").toPath(),
            planSettings = planSettings
        )

        try {
        // Get all available task types instead of just the first one
        val availableTaskTypes = TaskType.getAvailableTaskTypes(coordinator.planSettings)

            val taskSelectionPrompt =
                "Available task types:\n" +
                    availableTaskTypes.joinToString("\n") { taskType ->
                            "* ${TaskType.getImpl(coordinator.planSettings, taskType).promptSegment()}"
                        } + "\nChoose the most suitable task types and provide details of how they should be executed."


        // First, get the task type from the user's message
        val taskTypeActor = ParsedActor(
            name = "TaskTypeChooser",
            resultClass = String::class.java,
            prompt = taskSelectionPrompt + "\n\nFirst, determine which task type is most appropriate for this request.",
            model = coordinator.planSettings.defaultModel,
            parsingModel = coordinator.planSettings.parsingModel,
            temperature = coordinator.planSettings.temperature,
            describer = describer
        )

            val input = getConversationContext() +
                    listOf(
                        "Please choose the next single task to execute based on the current status.\nIf there are no tasks to execute, return {}."
                    )

        // Get the task type name
        val taskTypeName = taskTypeActor.answer(input, apiClient).text.trim()
        val taskType = availableTaskTypes.find { it.name == taskTypeName } 
            ?: throw IllegalArgumentException("Unknown task type: $taskTypeName")
        
        // Now create an actor with the correct result class for the chosen task type
        val taskConfigActor = ParsedActor(
            name = "TaskConfigGenerator",
            resultClass = taskType.taskDataClass,
            prompt = "Generate a detailed configuration for a ${taskType.name} based on the user's request.",
            model = coordinator.planSettings.defaultModel,
            parsingModel = coordinator.planSettings.parsingModel,
            temperature = coordinator.planSettings.temperature,
            describer = describer,
            parserPrompt = "Task Schema for ${taskType.name}:\n${
                describer.describe(taskType.taskDataClass).lineSequence()
                    .map { if (it.isBlank()) it else "  $it" }
                    .joinToString("\n")
            }"
        )
        
        // Get the task configuration
        val taskConfig = taskConfigActor.answer(input, apiClient).obj
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

            val assistantResponse = "Task executed: ${taskType.name}\n${result}"
            synchronized(messagesLock) {
                messages.add(ApiModel.ChatMessage(ApiModel.Role.assistant, assistantResponse.toContentList()))
            }

            task.complete()

            processBufferedMessages()
        } catch (e: Exception) {
            log.error("Error executing task", e)
            task.error(null, e)

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

                val combinedMessage = messageBuffer.joinToString("\n\n")
                log.debug("Processing buffered messages: ${messageBuffer.size} messages")

                messageBuffer.clear()

                isProcessing = false

                val newTask = ui.newTask(false)
                ui.socketManager?.pool?.submit {
                    handleUserMessage(combinedMessage, newTask)
                }
            } else {

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

        override val singleInput: Boolean = false
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