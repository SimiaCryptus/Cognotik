package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
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

        task.echo(renderMarkdown(userMessage))
        Retryable(ui, task) {
            val subtask = ui.newTask(false)
            ui.socketManager?.pool?.submit {
                execute(subtask, userMessage)
            }
            subtask.placeholder
        }
    }

    private fun execute(task: SessionTask, userMessage: String) {
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
            val parsedActor = ParsedActor(
                name = "TaskChooser",
                resultClass = AutoPlanMode.Tasks::class.java,
                exampleInstance = AutoPlanMode.Tasks(
                    listOfNotNull(
                        TaskType.getAvailableTaskTypes(coordinator.planSettings).first().let {
                            TaskType.getImpl(coordinator.planSettings, it).taskConfig
                        }
                    ).toMutableList()
                ),
                prompt = buildString {
                    append("Given the following input, choose ONE task to execute. Select the most appropriate task type for the given input and provide all required details.\n")
                    append("Available task types:\n")
                    append(TaskType.getAvailableTaskTypes(coordinator.planSettings).joinToString("\n\n") { taskType ->
                        "* ${TaskType.getImpl(coordinator.planSettings, taskType).promptSegment().trim().trimIndent().indent("  ")}"
                    })
                    append("\nChoose the most suitable task type and provide details of how it should be executed.")
                },
                model = coordinator.planSettings.defaultModel,
                parsingModel = coordinator.planSettings.parsingModel,
                temperature = coordinator.planSettings.temperature,
                describer = describer,
                parserPrompt = ("Task Subtype Schema:\n" + TaskType.getAvailableTaskTypes(coordinator.planSettings)
                    .joinToString("\n\n") { taskType ->
                        "${taskType.name}:\n  ${describer.describe(taskType.taskDataClass).trim().trimIndent().indent("  ")}".trim()
                    })
            )
            
            val input = getConversationContext() +
                    listOf(
                        "Please choose a single task to execute based on the current conversation."
                    )
            
            val answer = parsedActor.answer(input, apiClient)
            val chosenTasks = answer.obj.tasks?.firstOrNull() 
                ?: throw IllegalStateException("No task was selected")
            
            val taskImpl = TaskType.getImpl(planSettings, chosenTasks)
            task.verbose("Executing task:\n```json\n${JsonUtil.toJson(chosenTasks)}\n```".renderMarkdown())

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

            val assistantResponse = "Task executed: ${chosenTasks.task_type}\n${result}"
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