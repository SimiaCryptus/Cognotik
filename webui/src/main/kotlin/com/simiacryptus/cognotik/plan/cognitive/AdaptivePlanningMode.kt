package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path

/**
 * A cognitive mode that implements the auto-planning strategy with iterative thinking.
 */
open class AdaptivePlanningMode<T>(
    override val task: SessionTask,
    override val orchestrationConfig: OrchestrationConfig,
    override val session: Session,
    override val user: User = defaultUser,
    private val maxTaskHistoryChars: Int = orchestrationConfig.maxTaskHistoryChars,
    private val maxTasksPerIteration: Int = orchestrationConfig.maxTasksPerIteration,
    private val maxIterations: Int = orchestrationConfig.maxIterations,
    val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig),
    val cognitiveStrategy: CognitiveSchemaStrategy<T>
) : CognitiveMode {

    private val log = LoggerFactory.getLogger(AdaptivePlanningMode::class.java)
    private val currentUserMessage = AtomicReference<String?>(null)
    private val executionRecords = mutableListOf<ExecutionRecord>()
    private val reasoningState = AtomicReference<T?>(null)
    private var isRunning = false
    private var transcriptStream: FileOutputStream? = null
    private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:\|[^|}{\n<>()\[\]]+))}""")

    override fun initialize() {
        log.debug("Initializing AutoPlanMode")
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        log.debug("Handling user message: $userMessage")
        if (!isRunning) {
            isRunning = true
            log.debug("Starting new auto plan chat session")
            startAutoPlanChat(userMessage)
        } else {
            log.debug("Injecting user message into ongoing chat")
            task.echo("User: $userMessage".renderMarkdown)
            currentUserMessage.set(userMessage)
        }
    }

    private fun startAutoPlanChat(userMessage: String) {
        log.debug("Starting auto plan chat with initial message: $userMessage")
        task.echo(renderMarkdown(userMessage))
        transcriptStream = transcript(task)

        val continueLoop = true
        val tabbedDisplay = TabbedDisplay(task)
        this.task.ui.pool.execute {
            try {
                log.debug("Starting main execution loop")
                task.complete()

                val coordinator = this.task.ui.dataStorage?.let {
                    TaskOrchestrator(
                        user = user,
                        session = session,
                        dataStorage = it,
                        root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                            ?: this.task.ui.dataStorage!!.getSessionDir(user, session).toPath() ?: File(".").toPath()
                    )
                }
                log.debug("Created plan coordinator")

                val initialStatus = cognitiveStrategy.initialize(
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
                while (iteration++ < maxIterations && continueLoop) {
                    log.debug("Starting iteration $iteration")
                    task.complete()
                    val currentThinkingStatus = reasoningState.get()
                        ?: throw IllegalStateException("ThinkingStatus is null at iteration $iteration")
                    writeToTranscript("## Iteration $iteration\n\n")

                    val task = task.linkedTask("Iteration $iteration")
                    val ui = task.ui
                    val iterationTabbedDisplay = TabbedDisplay(task, additionalClasses = "iteration")

                    ui.newTask(false).apply {
                        iterationTabbedDisplay["Inputs"] = placeholder
                        val inputTabs = TabbedDisplay(this)
                        ui.newTask(false).apply {
                            inputTabs["Project Info"] = placeholder
                            contextData().forEach {
                                complete(renderMarkdown(it, tabs = false))
                            }
                            complete()
                        }
                        formatEvalRecords().forEachIndexed { index, it ->
                            ui.newTask(false).apply {
                                inputTabs["Task ${index + 1}"] = placeholder
                                complete(renderMarkdown(it))
                            }
                            complete(renderMarkdown(it))
                        }
                        ui.newTask(false).apply {
                            inputTabs["Thinking Status"] = placeholder
                            complete(renderMarkdown(cognitiveStrategy.formatState(currentThinkingStatus)))
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
                        val taskExecutionTask = ui.newTask(false)
                        val taskConfig = currentTask.task.tasks?.get(index)
                        val taskDescription =
                            taskConfig?.task_description ?: "No description provided for this task item."
                        taskExecutionTask.add("\n```json\n${taskConfig?.toJson()}\n```\n".renderMarkdown)
                        writeToTranscript("**Description:** $taskDescription\n\n```json\n${JsonUtil.toJson(taskConfig)}\n```\n\n")
                        taskExecutionTask.verbose(

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
                        ui.newTask(false).apply { iterationTabbedDisplay["Thinking Status"] = placeholder }
                    try {
                        log.debug("Updating thinking status")
                        writeToTranscript("### Updated Thinking Status\n\n")
                        val updatedStatus = cognitiveStrategy.update(
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
                                    cognitiveStrategy.formatState(updatedStatus)
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
                val summaryTask = this.task.ui.newTask(false).apply { tabbedDisplay["Summary"] = placeholder }
                summaryTask.add(
                    renderMarkdown(
                        "Auto Plan Chat completed. Final thinking status:\n${
                            reasoningState.get()?.let {
                                cognitiveStrategy.formatState(it)
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
        val taskImpl = TaskType.getImpl(orchestrationConfig, currentTask)
        val result = StringBuilder()

        taskImpl.run(
            agent = coordinator,
            messages = listOf(
                userMessage,
                "Current thinking status:\n${cognitiveStrategy.formatState(currentThinkingStatus)}"
            ) + formatEvalRecords(),
            task = task,
            resultFn = { result.append(it) },
            orchestrationConfig = orchestrationConfig,
        )

        return result.toString()
    }

    private fun getNextTask(
        userMessage: String,
        reasoningState: T,
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
                append(maxTasksPerIteration)
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
                                    TaskType.getImpl(orchestrationConfig, taskType).promptSegment().trim()
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
        val answer = parsedActor.answer(
            listOf(userMessage) + contextData() + listOf(
                """
        Current thinking status: ${cognitiveStrategy.formatState(reasoningState)}
        ${cognitiveStrategy.getTaskSelectionGuidance(reasoningState)}
        """.trimIndent()
            ) + formatEvalRecords(),
        )


        val executor = this.task.ui.pool
            ?: throw IllegalStateException("SocketManager or its pool is null for expansion processing")
        val processor = FixedConcurrencyProcessor(executor, 4)

        val expandedTasks = processTaskExpansionRecursive(
            currentText = answer.text,
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
                    TaskType.getImpl(orchestrationConfig, taskConfigBase)
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
            return tasks.take(maxTasksPerIteration).map {
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
                    val subTask = this.task.ui.newTask(false).apply { tabs[option] = placeholder }
                    val nextText = currentText.replaceFirst(match.value, option)
                    processTaskExpansionRecursive(nextText, subTask, parsedActor, processor)
                }
            }
            return futures.flatMap { it.get() }
        }
    }



    private fun formatEvalRecords(maxTotalLength: Int = maxTaskHistoryChars): List<String> {
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

    private fun transcript(task: SessionTask): FileOutputStream? {
        val transcriptFile = "adaptive_planning_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.complete(
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(
                    ".md"
                )
            }.pdf' target='_blank'>pdf</a>"
        )
        return markdownTranscript
    }

    private fun writeToTranscript(content: String) {
        transcriptStream?.write(content.toByteArray())
    }


    companion object : CognitiveModeStrategy {
        override val inputCnt = 1
        override fun getCognitiveMode(
            task: SessionTask,
            orchestrationConfig: OrchestrationConfig,
            session: Session,
            user: User
        ) = AdaptivePlanningMode(
            task, orchestrationConfig, session, user,
            cognitiveStrategy = ProjectManagerStrategy()
        )

    }
}

interface CognitiveSchemaStrategy<T> {
    val name: String
    val description: String
    val stateClass: Class<T>
    fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): T

    fun update(
        currentState: T,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): T

    fun formatState(state: T): String
    fun getTaskSelectionGuidance(state: T): String
}

open class ProjectManagerStrategy(
    override val name: String = "Project Manager",
    override val description: String = "Standard goal-oriented planning.",
    val initPrompt: String? = null,
    val updatePrompt: String? = null
) : CognitiveSchemaStrategy<AdaptivePlanningMode.ReasoningState> {
    override val stateClass = AdaptivePlanningMode.ReasoningState::class.java
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): AdaptivePlanningMode.ReasoningState {
        return ParsedAgent(
            name = "ThinkingStatusInitializer",
            resultClass = AdaptivePlanningMode.ReasoningState::class.java,
            exampleInstance = AdaptivePlanningMode.ReasoningState(
                initialPrompt = "Example prompt",
                goals = AdaptivePlanningMode.Goals(
                    shortTerm = mutableListOf(AdaptivePlanningMode.Goal("Understand the user's request")),
                    longTerm = mutableListOf(AdaptivePlanningMode.Goal("Complete the user's task"))
                ),
                knowledge = AdaptivePlanningMode.Knowledge(
                    facts = mutableListOf("Initial Context: User's request received"),
                    openQuestions = mutableListOf("What is the first task?")
                ),
                executionContext = AdaptivePlanningMode.ExecutionContext(
                    nextSteps = mutableListOf("Analyze the initial prompt", "Identify key objectives"),
                )
            ),
            prompt = initPrompt ?: """
        Initialize a comprehensive thinking status for an AI assistant based on the user's prompt.
        Goals:
        1. Short-term goals: Define immediate objectives that can be accomplished in 1-2 iterations
        2. Long-term goals: Outline the overall project objectives and desired end state
        Knowledge Base:
        1. Facts: Extract concrete information and requirements from the prompt
        2. Hypotheses: Form initial assumptions that need validation
        3. Open Questions: List critical uncertainties and information gaps
        Execution Context:
        1. Next Steps: Plan initial 2-3 concrete actions
        2. Potential Challenges: Identify possible obstacles and constraints
        3. Available Resources: List tools and capabilities at disposal
        Analysis Guidelines:
        * Break down complex requirements into manageable components
        * Consider both technical and non-technical aspects
        * Identify dependencies and prerequisites
        * Maintain alignment between short-term actions and long-term goals
        * Ensure scalability and maintainability of the approach
      """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj.apply {
            initialPrompt = userMessage
        }
    }

    override fun update(
        currentState: AdaptivePlanningMode.ReasoningState,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): AdaptivePlanningMode.ReasoningState {
        return ParsedAgent(
            name = "UpdateQuestionsActor",
            resultClass = AdaptivePlanningMode.ReasoningState::class.java,
            exampleInstance = AdaptivePlanningMode.ReasoningState(
                initialPrompt = "Create a Python script to analyze log files and generate a summary report",
                confidence = 0.8,
                iteration = 1,
                goals = AdaptivePlanningMode.Goals(
                    shortTerm = mutableListOf(
                        AdaptivePlanningMode.Goal(
                            "Understand log file format requirements",
                            isRigid = true,
                            priority = 1
                        ),
                        AdaptivePlanningMode.Goal("Define report structure", priority = 2),
                        AdaptivePlanningMode.Goal("Plan implementation approach", priority = 3)
                    ),
                    longTerm = mutableListOf(
                        AdaptivePlanningMode.Goal("Deliver working Python script", isRigid = true, priority = 1),
                        AdaptivePlanningMode.Goal("Ensure robust error handling", priority = 2),
                        AdaptivePlanningMode.Goal("Provide documentation", priority = 3)
                    )
                ),
                knowledge = AdaptivePlanningMode.Knowledge(
                    facts = mutableListOf(
                        "Project requires Python programming",
                        "Output format needs to be a summary report",
                        "Input consists of log files"
                    ),
                    hypotheses = mutableListOf(
                        "Log files might be in different formats",
                        "Performance optimization may be needed for large files"
                    ),
                    openQuestions = mutableListOf(
                        "What is the specific log file format?",
                        "Are there any performance requirements?",
                        "What specific metrics should be included in the report?"
                    )
                ),
                executionContext = AdaptivePlanningMode.ExecutionContext(
                    completedTasks = mutableListOf(
                        "Initial requirements analysis",
                        "Project scope definition"
                    ),
                    currentTask = AdaptivePlanningMode.CurrentTask(
                        taskId = "TASK_003",
                        description = "Design log parsing algorithm"
                    ),
                    nextSteps = mutableListOf(
                        "Implement log file reader",
                        "Create report generator",
                        "Add error handling",
                        "Invoke reflect task if needed"
                    )
                )
            ),
            prompt = updatePrompt ?: """
      Given the current thinking status, the last completed task, its result, and any repeating error signals,
      update the open questions and next steps to guide the planning process.
      Reflect on what went well and what could be improved.
      Reassess the goals (paying attention to priorities and rigidity) and adjust the confidence level.
      If error patterns are recurring or progress slows, trigger a reflection loop by adding a 'reflect' task.
    """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast,
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current thinking status: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.flatMap { record ->
                        val t: TaskExecutionConfig? = record.task
                        listOf(
                            "Completed task: ${t?.task_description}",
                            "Task result: ${record.result}",
                            record.reflections?.let { "Reflection: Positive: ${it.positiveNotes}, Improvements: ${it.improvementSuggestions}" }
                                ?: "")
                    } +
                    (userMessage?.let { listOf("User message: $it") } ?: listOf()),
        ).obj.apply {
            knowledge?.facts?.apply {
                this.addAll(completedTasks.mapIndexed { index, record ->
                    "Task ${(executionContext?.completedTasks?.size ?: 0) + index + 1} Result: ${record.result}"
                })
            }
        }
    }

    override fun formatState(state: AdaptivePlanningMode.ReasoningState): String {
        return "```json\n${JsonUtil.toJson(state)}\n```"
    }

    override fun getTaskSelectionGuidance(state: AdaptivePlanningMode.ReasoningState): String {
        return "Please choose the next single task to execute based on the current status.\nIf there are no tasks to execute, return {}."
    }
}
data class ScientificState(
    @Description("The core question or problem being investigated.")
    val researchQuestion: String? = null,
    @Description("List of hypotheses with confidence levels and evidence requirements.")
    val currentHypotheses: MutableList<Hypothesis>? = null,
    @Description("Facts that have been verified through evidence.")
    val establishedFacts: MutableList<String>? = null,
    @Description("Theories that have been proven false.")
    val refutedTheories: MutableList<String>? = null,
    @Description("Log of experiments or investigations performed.")
    val experimentLog: MutableList<String>? = null
)
data class Hypothesis(
    val statement: String = "",
    val confidence: Double = 0.0,
    val evidenceNeeded: String = ""
)
class ScientificMethodStrategy : CognitiveSchemaStrategy<ScientificState> {
    override val name = "Scientific Researcher"
    override val description = "Hypothesis-driven investigation."
    override val stateClass = ScientificState::class.java
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): ScientificState {
        return ParsedAgent(
            name = "ScientificInitializer",
            resultClass = ScientificState::class.java,
            exampleInstance = ScientificState(
                researchQuestion = "Why is the system crashing?",
                currentHypotheses = mutableListOf(
                    Hypothesis("Memory leak in loop", 0.4, "Heap dump analysis"),
                    Hypothesis("Database timeout", 0.3, "Log timestamp correlation")
                ),
                establishedFacts = mutableListOf("Crashes occur every 24 hours"),
                refutedTheories = mutableListOf("Disk space full"),
                experimentLog = mutableListOf("Checked disk space")
            ),
            prompt = """
                Formulate a scientific research plan based on the user request.
                1. Define the core research question.
                2. Propose initial hypotheses with confidence levels.
                3. Identify what evidence is needed to prove/disprove them.
                4. List any known facts provided in the prompt.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj
    }
    override fun update(
        currentState: ScientificState,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): ScientificState {
        return ParsedAgent(
            name = "ScientificUpdater",
            resultClass = ScientificState::class.java,
            exampleInstance = currentState,
            prompt = """
                Analyze the results of the recent tasks.
                1. Did the results confirm or refute any hypothesis?
                2. Move proven hypotheses to established facts.
                3. Move disproven hypotheses to refuted theories.
                4. Update confidence levels based on new evidence.
                5. Add new hypotheses if new questions arise.
                6. Update the experiment log.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current State: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.map { "Task: ${it.task?.task_description}\nResult: ${it.result}" } +
                    (userMessage?.let { listOf("User: $it") } ?: emptyList())
        ).obj
    }
    override fun formatState(state: ScientificState) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: ScientificState): String {
        return "Select tasks specifically designed to falsify or validate the top hypothesis. Prioritize information gathering over content generation."
    }
}
data class AgileState(
    val userStory: String? = null,
    val acceptanceCriteria: MutableList<String>? = null,
    val currentPhase: String? = null, // "TEST_FAILING", "IMPLEMENTING", "REFACTORING"
    val knownBugs: MutableList<String>? = null,
    val todoList: MutableList<String>? = null
)
class AgileDeveloperStrategy : CognitiveSchemaStrategy<AgileState> {
    override val name = "Agile Developer"
    override val description = "Iterative Test-Driven Development."
    override val stateClass = AgileState::class.java
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): AgileState {
        return ParsedAgent(
            name = "AgileInitializer",
            resultClass = AgileState::class.java,
            exampleInstance = AgileState(
                userStory = "As a user, I want to login so that I can access my data",
                acceptanceCriteria = mutableListOf("Valid credentials logs in", "Invalid credentials shows error"),
                currentPhase = "TEST_FAILING",
                knownBugs = mutableListOf(),
                todoList = mutableListOf("Create login test", "Implement login function")
            ),
            prompt = """
                Break the request into a User Story and Acceptance Criteria.
                Initialize the process in the 'TEST_FAILING' phase (TDD).
                Create a TODO list of small, incremental steps.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj
    }
    override fun update(
        currentState: AgileState,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): AgileState {
        return ParsedAgent(
            name = "AgileUpdater",
            resultClass = AgileState::class.java,
            exampleInstance = currentState,
            prompt = """
                Update the Agile state based on task results.
                - If in TEST_FAILING and tests passed, move to REFACTORING.
                - If in TEST_FAILING and tests failed (as expected), move to IMPLEMENTING.
                - If in IMPLEMENTING and tests pass, move to REFACTORING.
                - If in REFACTORING and code is clean, pick next TODO and move to TEST_FAILING.
                - Update known bugs and TODO list.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current State: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.map { "Task: ${it.task?.task_description}\nResult: ${it.result}" } +
                    (userMessage?.let { listOf("User: $it") } ?: emptyList())
        ).obj
    }
    override fun formatState(state: AgileState) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: AgileState): String {
        return when (state.currentPhase) {
            "TEST_FAILING" -> "Create a test file or run existing tests to confirm failure."
            "IMPLEMENTING" -> "Write code to satisfy the failing test."
            "REFACTORING" -> "Optimize the code without changing behavior."
            else -> "Check acceptance criteria and pick the next item."
        }
    }
}
data class AuditState(
    val targetScope: String? = null,
    val riskAssessment: MutableList<Risk>? = null,
    val complianceChecklist: MutableMap<String, Boolean>? = null,
    val vulnerabilitiesFound: MutableList<String>? = null,
    val finalVerdict: String? = null
)
data class Risk(
    val description: String = "",
    val severity: String = "LOW",
    val status: String = "OPEN"
)
class CriticalAuditorStrategy : CognitiveSchemaStrategy<AuditState> {
    override val name = "Critical Auditor"
    override val description = "Security and logic validation."
    override val stateClass = AuditState::class.java
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): AuditState {
        return ParsedAgent(
            name = "AuditInitializer",
            resultClass = AuditState::class.java,
            exampleInstance = AuditState(
                targetScope = "Login Module",
                riskAssessment = mutableListOf(Risk("SQL Injection", "HIGH", "OPEN")),
                complianceChecklist = mutableMapOf("GDPR" to false),
                vulnerabilitiesFound = mutableListOf(),
                finalVerdict = "PENDING"
            ),
            prompt = """
                Identify potential risks, attack vectors, and compliance requirements in the user request.
                Define the scope of the audit.
                Initialize the risk assessment.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj
    }
    override fun update(
        currentState: AuditState,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): AuditState {
        return ParsedAgent(
            name = "AuditUpdater",
            resultClass = AuditState::class.java,
            exampleInstance = currentState,
            prompt = """
                Review the output. Be extremely critical.
                - If any error or weakness is found, log it in vulnerabilities.
                - Update risk status (MITIGATED, CONFIRMED, OPEN).
                - Update compliance checklist.
                - If serious issues found, escalate severity.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current State: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.map { "Task: ${it.task?.task_description}\nResult: ${it.result}" } +
                    (userMessage?.let { listOf("User: $it") } ?: emptyList())
        ).obj
    }
    override fun formatState(state: AuditState) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: AuditState): String {
        return "Choose tasks that stress-test the system. Try to break the implementation. Do not fix issues, only report them."
    }
}
data class NarrativeState(
    val theme: String? = null,
    val targetAudience: String? = null,
    val outline: MutableList<Chapter>? = null,
    val currentSection: String? = null,
    val toneCheck: String? = null // e.g., "Too formal", "Just right"
)
data class Chapter(
    val title: String = "",
    val summary: String = "",
    val status: String = "DRAFT"
)
class CreativeWriterStrategy : CognitiveSchemaStrategy<NarrativeState> {
    override val name = "Creative Writer"
    override val description = "Narrative and content generation."
    override val stateClass = NarrativeState::class.java
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): NarrativeState {
        return ParsedAgent(
            name = "WriterInitializer",
            resultClass = NarrativeState::class.java,
            exampleInstance = NarrativeState(
                theme = "Cyberpunk Noir",
                targetAudience = "Young Adults",
                outline = mutableListOf(Chapter("The Setup", "Hero meets villain", "TODO")),
                currentSection = "The Setup",
                toneCheck = "Pending"
            ),
            prompt = """
                Develop a narrative structure based on the user request.
                Define the theme and target audience.
                Create a high-level outline of chapters or sections.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData).obj
    }
    override fun update(
        currentState: NarrativeState,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): NarrativeState {
        return ParsedAgent(
            name = "WriterUpdater",
            resultClass = NarrativeState::class.java,
            exampleInstance = currentState,
            prompt = """
                Review the generated content.
                - Check if the tone matches the theme.
                - Update the status of chapters (DRAFT, REVIEWED, DONE).
                - Move to the next section if the current one is satisfactory.
                - Adjust the outline if the story evolves differently.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(
            listOf("Current State: ${formatState(currentState)}") +
                    contextData +
                    completedTasks.map { "Task: ${it.task?.task_description}\nResult: ${it.result}" } +
                    (userMessage?.let { listOf("User: $it") } ?: emptyList())
        ).obj
    }
    override fun formatState(state: NarrativeState) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: NarrativeState): String {
        return "Focus on generating content. If the tone is off, select a task to rewrite or edit. Do not execute code unless it is to generate text."
    }
}