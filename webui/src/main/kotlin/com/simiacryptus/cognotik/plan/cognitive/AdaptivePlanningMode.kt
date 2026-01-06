package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path

/**
 * Configuration for AdaptivePlanningMode.
 */
open class AdaptivePlanningConfig(
    type: CognitiveModeType<*> = CognitiveModeType.Adaptive,
    var maxTaskHistoryChars: Int = 10000,
    var maxTasksPerIteration: Int = 5,
    var maxIterations: Int = 10,
    val cognitiveStrategy: CognitiveSchemaStrategy = CognitiveSchemaStrategy.ProjectManager
) : CognitiveModeConfig(type)

/**
 * A cognitive mode that implements the auto-planning strategy with iterative thinking.
 */
open class AdaptivePlanningMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser,
    val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)
) : CognitiveMode<AdaptivePlanningConfig>(
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(AdaptivePlanningMode::class.java)
    private val currentUserMessage = AtomicReference<String?>(null)
    private val executionRecords = mutableListOf<ExecutionRecord>()
    private val reasoningState = AtomicReference<Any?>(null)
    private var isRunning = false
    private var transcriptStream: FileOutputStream? = null
    private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:\|[^|}{\n<>()\[\]]+))}""")
    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        log.debug("Handling user message: $userMessage")
        if (!isRunning) {
            isRunning = true
            log.debug("Starting new auto plan chat session")
            startAutoPlanChat(task, userMessage)
        } else {
            log.debug("Injecting user message into ongoing chat")
            task.echo("User: $userMessage".renderMarkdown)
            currentUserMessage.set(userMessage)
        }
    }

    private fun startAutoPlanChat(task : SessionTask, userMessage: String) {
        log.debug("Starting auto plan chat with initial message: $userMessage")
        task.echo(renderMarkdown(userMessage))
        transcriptStream = transcript(task)

        val continueLoop = true
        val tabbedDisplay = TabbedDisplay(task)
        task.ui.pool.execute {
            try {
                log.debug("Starting main execution loop")
                task.complete()

                val coordinator = task.ui.dataStorage?.let {
                    TaskOrchestrator(
                        user = user,
                        session = session,
                        dataStorage = it,
                        root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                            ?: task.ui.dataStorage!!.getSessionDir(user, session).toPath() ?: File(".").toPath()
                    )
                }
                log.debug("Created plan coordinator")

                val initialStatus = config.cognitiveStrategy.initialize(
                    userMessage,
                    contextData(),
                    orchestrationConfig,
                    task,
                    describer
                )
                log.debug("Initialized thinking status")
                reasoningState.set(initialStatus)
                writeToTranscript("# Auto Plan Chat Session\n\n## Initial Prompt\n\n$userMessage\n\n")

                var iteration = 0
                while (iteration++ < config.maxIterations && continueLoop) {
                    log.debug("Starting iteration $iteration")
                    task.complete()
                    val currentThinkingStatus = reasoningState.get()
                        ?: throw IllegalStateException("ThinkingStatus is null at iteration $iteration")
                    writeToTranscript("## Iteration $iteration\n\n")

                    val task = task.linkedTask("Iteration $iteration")
                    val ui = task.ui
                    val iterationTabbedDisplay = TabbedDisplay(task, additionalClasses = "iteration")

                    iterationTabbedDisplay.newTask("Inputs").apply {
                        val inputTabs = TabbedDisplay(this)
                        inputTabs.newTask("Project Info").apply {
                            contextData().forEach {
                                complete(renderMarkdown(it, tabs = false))
                            }
                            complete()
                        }
                        formatEvalRecords().forEachIndexed { index, it ->
                            inputTabs.newTask("Task ${index + 1}").apply {
                                complete(renderMarkdown(it))
                            }
                            complete(renderMarkdown(it))
                        }
                        inputTabs.newTask("Thinking Status").apply {
                            complete(renderMarkdown(config.cognitiveStrategy.formatState(currentThinkingStatus)))
                        }
                    }

                    val nextTask = try {
                        log.debug("Getting next task")
                        if (coordinator != null) {
                            getNextTask(userMessage, currentThinkingStatus, task)
                        } else {
                            log.error("Coordinator is null, cannot get next task")
                            null
                        }
                    } catch (e: Exception) {
                        log.error("Error choosing next task", e)
                        iterationTabbedDisplay["Errors"]?.append(renderMarkdown("Error choosing next task: ${e.message}"))
                        break
                    }

                    if (nextTask?.isEmpty() != false) {
                        log.debug("No more tasks to execute")
                        task.add(renderMarkdown("No more tasks to execute. Finishing Auto Plan Chat."))
                        break
                    }
                    log.debug("Retrieved next tasks: ${nextTask.size}")

                    val taskResults = mutableListOf<Pair<TaskExecutionConfig, Future<String>>>()
                    for ((index, currentTask: TaskData) in nextTask.withIndex()) {
                        val currentTaskId = "task_${index + 1}"
                        writeToTranscript("### Task $currentTaskId\n\n")
                        log.debug("Executing task $currentTaskId")
                        val taskExecutionTask = task.newTask()
                        val taskConfig = currentTask.task.tasks?.get(index)
                        val taskDescription =
                            taskConfig?.task_description ?: "No description provided for this task item."
                        taskExecutionTask.add("\n```json\n${taskConfig?.toJson()}\n```\n".renderMarkdown)
                        writeToTranscript("**Description:** $taskDescription\n\n```json\n${JsonUtil.toJson(taskConfig)}\n```\n\n")
                        taskExecutionTask.expandable(
                            "Task Configuration",

                            """
 Executing task: `$currentTaskId` - $taskDescription
Full TaskData JSON:
```json
${JsonUtil.toJson(taskConfig)}
```
""".trimIndent().renderMarkdown
                        )
                        iterationTabbedDisplay["Task Execution $currentTaskId"] = taskExecutionTask.placeholder

                        val future = ui.pool.submit<String> {
                            try {
                                if (coordinator != null) {
                                    runTask(
                                        coordinator = coordinator,
                                        currentTask = taskConfig!!,
                                        userMessage = userMessage,
                                        task = taskExecutionTask
                                    )
                                } else {
                                    log.error("Coordinator is null, cannot run task")
                                    ""
                                }
                            } catch (e: Exception) {
                                taskExecutionTask.error(e)
                                log.error("Error executing task", e)
                                "Error executing task: ${e.message}"
                            }
                        }
                        taskResults.add(Pair(currentTask.task.tasks?.get(index)!!, future))
                    }

                    val completedTasks = taskResults.map { (task, future) ->
                        val result = future.get()
                        log.debug("Task completed: ${task.task_description}")
                        writeToTranscript("**Result:**\n\n$result\n\n")
                        ExecutionRecord(
                            time = Date(),
                            iteration = iteration,
                            task = task,
                            result = result
                        )
                    }
                    executionRecords.addAll(completedTasks)

                    val thinkingStatusTask =
                        iterationTabbedDisplay.newTask("Thinking Status")
                    try {
                        log.debug("Updating thinking status")
                        writeToTranscript("### Updated Thinking Status\n\n")
                        val updatedStatus = config.cognitiveStrategy.update(
                            currentThinkingStatus,
                            completedTasks,
                            currentUserMessage.get(),
                            contextData(),
                            orchestrationConfig,
                            task,
                            describer
                        )
                        currentUserMessage.set(null)
                        reasoningState.set(updatedStatus)
                        log.debug("Updated thinking status")
                        thinkingStatusTask.complete(
                            renderMarkdown(
                                "Updated Thinking Status:\n${
                                    config.cognitiveStrategy.formatState(updatedStatus)
                                }"
                            )
                        )
                        writeToTranscript("```json\n${JsonUtil.toJson(updatedStatus)}\n```\n\n")
                    } catch (e: Exception) {
                        log.error("Error updating thinking status", e)
                        thinkingStatusTask.error(e)
                        iterationTabbedDisplay["Errors"]?.append(renderMarkdown("Error updating thinking status: ${e.message}"))
                    }
                }

                log.debug("Main execution loop completed")
                task.complete("Auto Plan Chat completed.")
            } catch (e: Throwable) {
                task.error(e)
                log.error("Error in startAutoPlanChat", e)
            } finally {
                log.debug("Finalizing auto plan chat")
                isRunning = false
                val summaryTask = tabbedDisplay.newTask("Summary")
                summaryTask.add(
                    renderMarkdown(
                        "Auto Plan Chat completed. Final thinking status:\n${
                            reasoningState.get()?.let {
                                config.cognitiveStrategy.formatState(it)
                            } ?: "null"
                        }")
                )
                writeToTranscript("\n## Summary\n\nAuto Plan Chat completed.\n\n")
                transcriptStream?.flush()
                transcriptStream?.close()
                transcriptStream = null
                task.complete()
                task.complete()
            }
        }
    }

    private fun runTask(
        coordinator: TaskOrchestrator,
        currentTask: TaskExecutionConfig,
        userMessage: String,
        task: SessionTask
    ): String {
        val currentThinkingStatus =
            reasoningState.get() ?: throw IllegalStateException("ThinkingStatus is null during runTask")
        val taskImpl = orchestrationConfig.getImpl(currentTask)
        val result = StringBuilder()

        taskImpl.run(
            agent = coordinator,
            messages = listOf(
                userMessage,
                "Current thinking status:\n${config.cognitiveStrategy.formatState(currentThinkingStatus)}"
            ) + formatEvalRecords(),
            task = task,
            resultFn = { result.append(it) },
            orchestrationConfig = orchestrationConfig,
        )

        return result.toString()
    }

    private fun getNextTask(
        userMessage: String,
        reasoningState: Any,
        task: SessionTask
    ): List<TaskData>? {
        Tasks.initDescriber(orchestrationConfig, describer)
        val parsedActor = ParsedAgent(
            name = "TaskChooser",
            resultClass = Tasks::class.java,
            exampleInstance = Tasks(
                listOf(
                    FileModificationTask.FileModificationTaskExecutionConfigData(
                        task_description = "Modify the file 'example.txt' to include the given input."
                    )
                ).toMutableList()
            ),
            prompt = buildString {
                append("Given the following input, choose up to ")
                append(config.maxTasksPerIteration)
                append(" tasks to execute. Do not create a full plan, just select the most appropriate task types for the given input and note any required/important details.\n")
                append("Note: These tasks will be run in parallel without knowledge of each other; this is not a sequential plan.\n")
                append("Available task types:\n")
                append(
                    TaskType.getAvailableTaskTypes(orchestrationConfig)
                        .flatMap { taskType ->
                            val configs = orchestrationConfig.getTaskConfigs(taskType)
                            configs.map { config ->
                                val configName = config.name?.let { " - Configuration: '$it'" } ?: ""
                                "* ${taskType.name}$configName:\n  ${
                                    orchestrationConfig.getImpl(taskType).promptSegment().trim()
                                        .trimIndent()
                                        .indent("  ")
                                }" + (orchestrationConfig.workingDir?.let { root ->
                                    "\nAvailable files:\n\n" + getAvailableFiles(Path(root)).joinToString("\n") { "      - $it" } + "\n"
                                } ?: "")
                            }
                        }
                        .joinToString("\n\n"))
                append("\nChoose the most suitable task types and provide details of how they should be executed.")
                val namedConfigs = orchestrationConfig.taskSettings.values.filter { it.name != null }
                if (namedConfigs.isNotEmpty()) {
                    append("\n\nAvailable named configurations:")
                    namedConfigs.groupBy { it.task_type }.forEach { (taskType, configs) ->
                        append("\n* $taskType: ${configs.mapNotNull { it.name }.joinToString(", ")}")
                    }
                    append("\nYou can specify which configuration to use by setting the task_config_name field.")
                }
            },
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer,
            parserPrompt = ("Task Subtype Schema:\n" + TaskType.getAvailableTaskTypes(orchestrationConfig)
                .joinToString("\n\n") { taskType ->
                    "${taskType.name}:\n  ${
                        describer.describe(taskType.executionConfigClass).trim().trimIndent().indent("  ")
                    }".trim()
                })
        )
        val inputMessages = listOf(userMessage) + contextData() + listOf(
            """
        Current thinking status: ${config.cognitiveStrategy.formatState(reasoningState)}
        ${config.cognitiveStrategy.getTaskSelectionGuidance(reasoningState)}
        """.trimIndent()
        ) + formatEvalRecords()

        val responseText = if (orchestrationConfig.autoFix) {
            parsedActor.answer(inputMessages).text
        } else {
            Discussable(
                task = task,
                heading = "Plan Review",
                userMessage = { userMessage },
                initialResponse = { parsedActor.answer(inputMessages) },
                outputFn = { it.text },
                reviseResponse = { history -> parsedActor.respond(history.map { it.component1() }) }
            ).call()?.text
        }

        val executor = task.ui.pool
            ?: throw IllegalStateException("SocketManager or its pool is null for expansion processing")
        val processor = FixedConcurrencyProcessor(executor, 4)

        val expandedTasks = processTaskExpansionRecursive(
            currentText = responseText ?: "",
            task = task,
            parsedActor = parsedActor,
            processor = processor
        )

        val tasks = expandedTasks.map { taskData ->
            taskData.task.tasks?.map { taskConfigBase ->
                TaskData(
                    Tasks(mutableListOf(taskConfigBase)),
                    taskData.actorResponse
                ) to (if (taskConfigBase.task_type == null) {
                    null
                } else {
                    orchestrationConfig.getImpl(taskConfigBase)
                })?.executionConfig
            } ?: emptyList()
        }.flatten()


        if (tasks.isEmpty()) {
            log.info("No tasks selected")
            return null
        } else if (tasks.mapNotNull { it.second }.isEmpty()) {
            log.warn("No valid tasks selected from: ${tasks.map { it.first }}")
            return null
        } else {
            return tasks.take(config.maxTasksPerIteration).map {
                TaskData(
                    task = Tasks(tasks.toList().flatMap { it.first.task.tasks ?: listOf() }.toMutableList()),
                    actorResponse = it.first.actorResponse
                )
            }
        }
    }

    data class TaskData(
        val task: Tasks,
        val actorResponse: String,
    )

    /**
     * Recursively processes task selection text containing expansion expressions {option1|option2}.
     * Creates tabs for each expansion branch and parses the final text at the leaf nodes.
     */
    private fun processTaskExpansionRecursive(
        currentText: String,
        task: SessionTask,
        parsedActor: ParsedAgent<Tasks>,
        processor: FixedConcurrencyProcessor
    ): List<TaskData> {
        val match = expansionExpressionPattern.find(currentText)
        if (match == null) {
            return try {
                val chosenTasks = parsedActor.getParser().apply(currentText)
                listOf(TaskData(chosenTasks, currentText))
            } catch (e: Exception) {
                log.error("Error parsing task text: $currentText", e)
                task.error(e)
                emptyList()
            }
        } else {
            val expression = match.groupValues[1]
            val options = expression.split('|')
            val tabs = TabbedDisplay(task)
            val futures = options.map { option ->
                processor.submit {
                    val subTask = tabs.newTask(option)
                    val nextText = currentText.replaceFirst(match.value, option)
                    processTaskExpansionRecursive(nextText, subTask, parsedActor, processor)
                }
            }
            return futures.flatMap { it.get() }
        }
    }


    private fun formatEvalRecords(maxTotalLength: Int = config.maxTaskHistoryChars): List<String> {
        var currentLength = 0
        val formattedRecords = mutableListOf<String>()

        for (record in executionRecords.reversed()) {
            val formattedRecord = """
        # Task ${executionRecords.indexOf(record) + 1}

        ## Task:
        ```json
        ${JsonUtil.toJson(record.task!!)}
        ```

        ## Result:
        ${
                record.result?.let {

                    it.split("\n").joinToString("\n") { line ->
                        if (line.startsWith("#")) {
                            "##$line"
                        } else {
                            line
                        }
                    }
                }
            }
      """

            if (currentLength + formattedRecord.length > maxTotalLength) {
                formattedRecords.add("... (earlier records truncated)")
                break
            }

            formattedRecords.add(0, formattedRecord)
            currentLength += formattedRecord.length
        }

        return formattedRecords
    }


    override fun contextData(): List<String> = emptyList()

    @Description("The current thinking status of the AI assistant.")
    data class ReasoningState(
        @Description("The original user prompt or request that initiated the conversation.")
        var initialPrompt: String? = null,

        @Description("Confidence level or certainty rating in the current overall thinking state.")
        var confidence: Double? = null,

        @Description("An iteration counter to add temporal context.")
        var iteration: Int = 0,

        @Description("The hierarchical goals structure defining both immediate and long-term objectives.")
        val goals: Goals? = null,

        @Description("The accumulated knowledge, facts, uncertainties and past reflections gathered during the conversation.")
        val knowledge: Knowledge? = null,

        @Description("The operational context including task history, current state, and planned actions.")
        val executionContext: ExecutionContext? = null
    )

    data class ExecutionRecord(
        val time: Date? = Date(),
        val iteration: Int = 0,
        val task: TaskExecutionConfig? = null,
        val result: String? = null,
        @Description("Meta-cognitive reflection about the task execution.")
        val reflections: Reflection? = null
    )

    data class Goals(
        @Description("Immediate objectives that need to be accomplished in the current iteration.")
        val shortTerm: MutableList<Goal>? = null,

        @Description("Overall objectives that span multiple iterations or the entire conversation.")
        val longTerm: MutableList<Goal>? = null
    )

    data class Goal(
        val objective: String = "",
        @Description("Flag indicating if this goal is rigid (non-negotiable) or flexible.")
        val isRigid: Boolean = false,

        @Description("Priority level with lower numbers indicating a higher priority.")
        val priority: Int = 5
    )

    data class Reflection(
        @Description("What went well during this task execution.")
        val positiveNotes: String = "",

        @Description("What could be improved for future iterations.")
        val improvementSuggestions: String = "",

        @Description("Optional error metrics or confidence adjustment from the task.")
        val errorMetric: Double? = null
    )

    @Description("The knowledge base of the AI assistant.")
    data class Knowledge(
        @Description("Verified information and concrete data gathered from task results and user input.")
        val facts: MutableList<Any>? = null,

        @Description("Tentative conclusions and working assumptions that need verification.")
        val hypotheses: MutableList<Any>? = null,

        @Description("Unresolved questions and areas requiring further investigation or clarification.")
        val openQuestions: MutableList<Any>? = null
    )

    @Description("The execution context of the AI assistant.")
    data class ExecutionContext(
        @Description("History of successfully executed tasks and their outcomes.")
        val completedTasks: MutableList<Any>? = null,

        @Description("Details of the task currently in progress, if any.")
        val currentTask: CurrentTask? = null,

        @Description("Planned future actions and their expected outcomes.")
        val nextSteps: MutableList<Any>? = null
    )

    @Description("The current task being executed.")
    data class CurrentTask(
        @Description("Unique identifier for tracking and referencing the task.")
        val taskId: String? = null,

        @Description("Detailed description of the task's objectives and requirements.")
        val description: String? = null
    )

    private fun writeToTranscript(content: String) {
        transcriptStream?.write(content.toByteArray())
    }


    companion object {
        val inputCnt = 1

    }
}