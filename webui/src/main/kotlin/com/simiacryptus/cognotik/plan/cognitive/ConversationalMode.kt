package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.actors.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.toContentList
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore

/**
 * A cognitive mode that executes tasks based on user input while maintaining conversation history.
 */
open class ConversationalMode(
    override val ui: SocketManager,
    override val orchestrationConfig: OrchestrationConfig,
    override val session: Session,
    override val user: User?
) : CognitiveMode {

    init {
        require(orchestrationConfig.defaultModel != null) { "Default model must be specified in orchestration config" }
        require(orchestrationConfig.parsingModel != null) { "Parsing model must be specified in orchestration config" }
    }

    private val messagesLock = Any()
    private val messages get() = messageMaps.computeIfAbsent(session) { ConcurrentLinkedQueue() }
    private val messageBuffer = ConcurrentLinkedQueue<String>()
    private var isProcessing = false
    private val systemPrompt = "Given the following input, choose ONE task to execute and describe it in detail."

    override fun initialize() {
        val enabledTasks = TaskType.getAvailableTaskTypes(orchestrationConfig)
        log.debug(
            "ConversationalMode initialized with task types: ${enabledTasks.joinToString(", ") { it.name }}",
            RuntimeException()
        )
        log.debug(
            "Task configurations: ${
                orchestrationConfig.taskSettings.values.joinToString(", ") {
                    "${it.task_type}${it.name?.let { name -> ":$name" } ?: ""}"
                }
            }")
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        log.debug("Handling user message: ${JsonUtil.toJson(userMessage)}")

        synchronized(messagesLock) {
            messageBuffer.add(userMessage)
            if (isProcessing) {
                log.debug("Already processing a task, adding message to buffer: ${userMessage}")
                return
            }
            isProcessing = true
        }

        task.echo(userMessage.renderMarkdown())
        ui.pool.submit {
            try {
                while (!Thread.interrupted()) {
                    sleep(100) // Brief pause to allow batching of messages
                    val userMessage = messageBuffer.poll() ?: continue
                    val task = ui.newTask(true)
                    execute(task, userMessage)
                }
            } finally {
                synchronized(messagesLock) {
                    isProcessing = false
                }
            }
        }
    }

    private fun execute(task: SessionTask, userMessage: String) {
        try {
            synchronized(messagesLock) {
                messages.add(ModelSchema.ChatMessage(ModelSchema.Role.user, userMessage.toContentList()))
            }
            val describer = TaskContextYamlDescriber(orchestrationConfig)
            val availableTaskTypes = TaskType.getAvailableTaskTypes(orchestrationConfig)
            val parsedActor = ParsedAgent(
                name = "TaskChooser",
                resultClass = AdaptivePlanningMode.Tasks::class.java,
                exampleInstance = AdaptivePlanningMode.Tasks(
                    listOfNotNull(
                        availableTaskTypes.firstOrNull()?.let {
                            TaskType.getImpl(orchestrationConfig, it).executionConfig
                        }
                    ).toMutableList()
                ),
                prompt = buildString {
                    append(systemPrompt)
                    append("Available task types:\n")
                    append(orchestrationConfig.taskSettings.values.joinToString("\n\n") { config ->
                        val taskType = TaskType.valueOf(config.task_type ?: return@joinToString "")
                        val configName = config.name?.let { " ($it)" } ?: ""
                        "* ${taskType.name}$configName:\n  ${
                            TaskType.getImpl(orchestrationConfig, taskType).promptSegment().trim()
                                .trimIndent()
                                .indent("  ")
                        }"
                    })
                    append("\nChoose the most suitable task type and provide details of how it should be executed.")
                    if (orchestrationConfig.taskSettings.values.any { it.name != null }) {
                        append("\nNote: Some task types have multiple configurations available. You can specify which configuration to use by setting the task_config_name field.")
                    }
                },
                model = orchestrationConfig.defaultChatter.getChildClient(task),
                parsingModel = orchestrationConfig.parsingChatter.getChildClient(task),
                temperature = orchestrationConfig.temperature,
                describer = describer,
                parserPrompt = ("Task Subtype Schema:\n" + availableTaskTypes
                    .joinToString("\n\n") { taskType ->
                        "${taskType.name}:\n  ${
                            describer.describe(taskType.executionConfigClass).trim().trimIndent().indent("  ")
                        }".trim()
                    })
            )

            val input = getConversationContext() +
                    listOf(
                        "Please choose a single task to execute based on the current conversation."
                    )

            val answer = parsedActor.answer(input)
            val chosenTasks = answer.obj.tasks?.firstOrNull()
                ?: throw IllegalStateException("No task was selected")

            val resultSemaphore = Semaphore(0)

            val tabs = TabbedDisplay(task)
            ui.newTask(false).apply {
                tabs["Plan"] = placeholder
                add(answer.text.renderMarkdown())
                complete("Executing task:\n```json\n${JsonUtil.toJson(chosenTasks)}\n```".renderMarkdown())
            }
            ui.newTask(false).apply {
                tabs["Run"] = placeholder
                TaskType.getImpl(orchestrationConfig, chosenTasks).run(
                    agent = TaskOrchestrator(
                        user = user,
                        session = session,
                        dataStorage = ui.dataStorage!!,
                        root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                            ?: ui.dataStorage.getSessionDir(user, session).toPath() ?: File(".").toPath()
                    ),
                    messages = listOf(userMessage),
                    task = this,
                    resultFn = { result ->
                        ui.newTask(false).apply {
                            tabs["Output"] = placeholder
                            complete(result.renderMarkdown())
                        }
                        val assistantResponse = "Task executed: ${chosenTasks.task_type}\n${result}"
                        synchronized(messagesLock) {
                            messages.add(
                                ModelSchema.ChatMessage(
                                    ModelSchema.Role.assistant,
                                    assistantResponse.toContentList()
                                )
                            )
                        }
                        resultSemaphore.release()
                    },
                    orchestrationConfig = orchestrationConfig,
                )
              this.complete()
            }
            resultSemaphore.acquire()
            task.complete()
        } catch (e: Exception) {
            log.error("Error executing task", e)
            task.error(e)
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

        override val inputCnt = 1
        override fun getCognitiveMode(
            ui: SocketManager,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User?
        ) = ConversationalMode(ui, orchestrationConfig, session, user)

        private val messageMaps = ConcurrentHashMap<Session, ConcurrentLinkedQueue<ModelSchema.ChatMessage>>()
        private val log = LoggerFactory.getLogger(ConversationalMode::class.java)
    }
}