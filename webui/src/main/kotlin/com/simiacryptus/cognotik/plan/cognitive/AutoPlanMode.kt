package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

/**
 * A cognitive mode that implements the auto-planning strategy with iterative thinking.
 */
open class AutoPlanMode(
    override val ui: ApplicationInterface,
    override val api: API,
    override val planSettings: PlanSettings,
    override val session: Session,
    override val user: User?,
    private val api2: OpenAIClient,
    private val maxTaskHistoryChars: Int = planSettings.maxTaskHistoryChars,
    private val maxTasksPerIteration: Int = planSettings.maxTasksPerIteration,
    private val maxIterations: Int = planSettings.maxIterations,
    val describer: TypeDescriber
) : CognitiveMode {
    private val log = LoggerFactory.getLogger(AutoPlanMode::class.java)

    private val currentUserMessage = AtomicReference<String?>(null)
    private val executionRecords = mutableListOf<ExecutionRecord>()
    private val thinkingStatus = AtomicReference<ThinkingStatus?>(null)
    private var isRunning = false
    private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:\|[^|}{]+)+)}""")

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
            task.echo(renderMarkdown("User: $userMessage", ui = ui))
            currentUserMessage.set(userMessage)
        }
    }

    private fun startAutoPlanChat(userMessage: String) {
        log.debug("Starting auto plan chat with initial message: $userMessage")
        val task = ui.newTask(true)
        val apiClient =
            (api as? ChatClient)?.getChildClient(task) ?: throw IllegalStateException("API must be a ChatClient")
        task.echo(renderMarkdown(userMessage))

        var continueLoop = true
        lateinit var stopLink: StringBuilder
        val executor = ui.socketManager?.pool ?: throw IllegalStateException("SocketManager or its pool is null")
        stopLink = task.add(ui.hrefLink("Stop") {
            log.debug("Stop button clicked - terminating execution")
            continueLoop = false

            stopLink.set("Stopped")
            task.complete()
        })!!

        val tabbedDisplay = TabbedDisplay(task)
        executor.execute {
            val socketManager = ui.socketManager ?: run {
                log.error("SocketManager is null, cannot proceed.")
                task.error(ui, IllegalStateException("SocketManager is null"))
                return@execute
            }
            try {
                log.debug("Starting main execution loop")
                tabbedDisplay.update()
                task.complete()

                apiClient.budget = planSettings.budget
                val coordinator = socketManager.dataStorage?.let {
                    PlanCoordinator(
                        user = user,
                        session = session,
                        dataStorage = it,
                        ui = ui,
                        root = planSettings.workingDir?.let { File(it).toPath() }
                            ?: socketManager.dataStorage.getDataDir(user, session).toPath() ?: File(".").toPath(),
                        planSettings = planSettings
                    )
                }
                log.debug("Created plan coordinator")

                val initialStatus = initThinking(planSettings, userMessage, apiClient)
                log.debug("Initialized thinking status")
                initialStatus.initialPrompt = userMessage
                thinkingStatus.set(initialStatus)

                var iteration = 0
                while (iteration++ < maxIterations && continueLoop) {
                    log.debug("Starting iteration $iteration")
                    task.complete()
                    val currentThinkingStatus = thinkingStatus.get()
                        ?: throw IllegalStateException("ThinkingStatus is null at iteration $iteration")
                    val iterationTask = ui.newTask(false).apply { tabbedDisplay["Iteration $iteration"] = placeholder }
                    val iterationApi = apiClient.getChildClient(iterationTask)
                    val iterationTabbedDisplay = TabbedDisplay(iterationTask, additionalClasses = "iteration")

                    ui.newTask(false).apply {
                        iterationTabbedDisplay["Inputs"] = placeholder
                        header("Project Info", 1)
                        contextData().forEach { add(renderMarkdown(it)) }
                        header("Evaluation Records", 1)
                        formatEvalRecords().forEach { add(renderMarkdown(it)) }
                        header("Current Thinking Status", 1)
                        add(renderMarkdown(formatThinkingStatus(currentThinkingStatus)))
                    }

                    val nextTask = try {
                        log.debug("Getting next task")
                        if (coordinator != null) {
                            getNextTask(iterationApi, coordinator, userMessage, currentThinkingStatus, iterationTask)
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
                        iterationTask.add(renderMarkdown("No more tasks to execute. Finishing Auto Plan Chat."))
                        break
                    }
                    log.debug("Retrieved next tasks: ${nextTask.size}")

                    val taskResults = mutableListOf<Pair<TaskConfigBase, Future<String>>>()
                    for ((index, currentTask: TaskData) in nextTask.withIndex()) {
                        val currentTaskId = "task_${index + 1}"
                        log.debug("Executing task $currentTaskId")
                        val taskExecutionTask = ui.newTask(false)
                        val taskConfig = currentTask.task.tasks?.get(index)
                        val taskDescription = taskConfig?.task_description ?: "No description provided for this task item."
                        taskExecutionTask.add(currentTask.actorResponse.renderMarkdown)
                        val fullTaskDataJson = JsonUtil.toJson(currentTask)
                        taskExecutionTask.verbose(
                            renderMarkdown(
                                """
Executing task: `$currentTaskId` - $taskDescription
Full TaskData JSON:
```json
$fullTaskDataJson
```
""".trimIndent(), tabs = false
                            )
                        )
                        iterationTabbedDisplay["Task Execution $currentTaskId"] = taskExecutionTask.placeholder

                        val future = executor.submit<String> {
                            try {
                                if (coordinator != null) {
                                    runTask(iterationApi, coordinator, currentTask.task.tasks?.get(index)!!, userMessage, taskExecutionTask)
                                } else {
                                    log.error("Coordinator is null, cannot run task")
                                    ""
                                }
                            } catch (e: Exception) {
                                taskExecutionTask.error(ui, e)
                                log.error("Error executing task", e)
                                "Error executing task: ${e.message}"
                            }
                        }
                        taskResults.add(Pair(currentTask.task.tasks?.get(index)!!, future))
                    }

                    val completedTasks = taskResults.map { (task, future) ->
                        val result = future.get()
                        log.debug("Task completed: ${task.task_description}")
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
                        val updatedStatus = updateThinking(iterationApi, currentThinkingStatus, completedTasks)
                        thinkingStatus.set(updatedStatus)
                        log.debug("Updated thinking status")
                        thinkingStatusTask.complete(
                            renderMarkdown(
                                "Updated Thinking Status:\n${
                                    formatThinkingStatus(
                                        updatedStatus
                                    )
                                }"
                            )
                        )
                    } catch (e: Exception) {
                        log.error("Error updating thinking status", e)
                        thinkingStatusTask.error(ui, e)
                        iterationTabbedDisplay["Errors"]?.append(renderMarkdown("Error updating thinking status: ${e.message}"))
                    }
                }

                log.debug("Main execution loop completed")
                task.complete("Auto Plan Chat completed.")
            } catch (e: Throwable) {
                task.error(ui, e)
                log.error("Error in startAutoPlanChat", e)
            } finally {
                log.debug("Finalizing auto plan chat")
                isRunning = false
                val summaryTask = ui.newTask(false).apply { tabbedDisplay["Summary"] = placeholder }
                summaryTask.add(
                    renderMarkdown(
                        "Auto Plan Chat completed. Final thinking status:\n${
                            thinkingStatus.get()?.let {
                                formatThinkingStatus(it)
                            } ?: "null"
                        }")
                )
                task.complete()
            }
        }
    }

    private fun runTask(
        api: ChatClient,
        coordinator: PlanCoordinator,
        currentTask: TaskConfigBase,
        userMessage: String,
        task: SessionTask
    ): String {
        val taskApi = api.getChildClient(task)
        val currentThinkingStatus =
            thinkingStatus.get() ?: throw IllegalStateException("ThinkingStatus is null during runTask")
        val taskImpl = TaskType.getImpl(coordinator.planSettings, currentTask)
        val result = StringBuilder()

        taskImpl.run(
            agent = coordinator.copy(
                planSettings = coordinator.planSettings.copy(
                    taskSettings = coordinator.planSettings.taskSettings.toList().toTypedArray().toMap().toMutableMap()
                        .apply {
                            this[TaskType.TaskPlanningTask.name] = TaskSettingsBase(
                                task_type = TaskType.TaskPlanningTask.name,
                                enabled = false,
                                model = null
                            )
                        }
                )
            ),
            messages = listOf(
                userMessage,
                "Current thinking status:\n${formatThinkingStatus(currentThinkingStatus)}"
            ) + formatEvalRecords(),
            task = task,
            api = taskApi,
            resultFn = { result.append(it) },
            api2 = api2,
            planSettings = planSettings,
        )

        return result.toString()
    }

    private fun getNextTask(
        api: ChatClient,
        coordinator: PlanCoordinator,
        userMessage: String,
        thinkingStatus: ThinkingStatus,
        task: SessionTask
    ): List<TaskData>? {
        val describer = coordinator.describer

        val parsedActor = ParsedActor(
            name = "TaskChooser",
            resultClass = Tasks::class.java,
            exampleInstance = Tasks(
                listOf(
                    FileModificationTask.FileModificationTaskConfigData(
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
                append(TaskType.getAvailableTaskTypes(coordinator.planSettings).joinToString("\n\n") { taskType ->
                    "* ${
                        TaskType.getImpl(coordinator.planSettings, taskType).promptSegment().trim().trimIndent()
                            .indent("  ")
                    }"
                })
                append("\nChoose the most suitable task types and provide details of how they should be executed.")
            },
            model = coordinator.planSettings.defaultModel,
            parsingModel = coordinator.planSettings.parsingModel,
            temperature = coordinator.planSettings.temperature,
            describer = describer,
            parserPrompt = ("Task Subtype Schema:\n" + TaskType.getAvailableTaskTypes(coordinator.planSettings)
                .joinToString("\n\n") { taskType ->
                    "${taskType.name}:\n  ${
                        describer.describe(taskType.taskDataClass).trim().trimIndent().indent("  ")
                    }".trim()
                })
        )
        val answer = parsedActor.answer(
            listOf(userMessage) + contextData() + listOf(
                """
        Current thinking status: ${formatThinkingStatus(thinkingStatus)}
        Please choose the next single task to execute based on the current status.
        If there are no tasks to execute, return {}.
        """.trimIndent()
            ) + formatEvalRecords(), api

        )


        val executor = ui.socketManager?.pool
            ?: throw IllegalStateException("SocketManager or its pool is null for expansion processing")
        val processor = FixedConcurrencyProcessor(executor, 4)

        val expandedTasks = processTaskExpansionRecursive(
            currentText = answer.text,
            task = task,
            api = api,
            parsedActor = parsedActor,
            processor = processor
        )

        val tasks = expandedTasks.map { taskData ->
            taskData.task.tasks?.map { taskConfigBase ->
                TaskData(Tasks(mutableListOf(taskConfigBase)), taskData.actorResponse) to (if (taskConfigBase.task_type == null) {
                    null
                } else {
                    TaskType.getImpl(coordinator.planSettings, taskConfigBase)
                })?.taskConfig
            } ?: emptyList()
        }.flatten()


        if (tasks.isNullOrEmpty()) {
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
        api: ChatClient,
        parsedActor: ParsedActor<Tasks>,
        processor: FixedConcurrencyProcessor
    ): List<TaskData> {
        val match = expansionExpressionPattern.find(currentText)
        if (match == null) {
            return try {
                val chosenTasks = parsedActor.getParser(api).apply(currentText)
                listOf(TaskData(chosenTasks, currentText))
            } catch (e: Exception) {
                log.error("Error parsing task text: $currentText", e)
                task.error(ui, e)
                emptyList()
            }
        } else {
            val expression = match.groupValues[1]
            val options = expression.split('|')
            val tabs = TabbedDisplay(task)
            val futures = options.map { option ->
                processor.submit {
                    val subTask = ui.newTask(false).apply { tabs[option] = placeholder }
                    val nextText = currentText.replaceFirst(match.value, option)
                    processTaskExpansionRecursive(nextText, subTask, api, parsedActor, processor)
                }
            }
            return futures.flatMap { it.get() }
        }
    }

    private fun initThinking(
        planSettings: PlanSettings,
        userMessage: String,
        api: ChatClient,
    ): ThinkingStatus {
        return ParsedActor(
            name = "ThinkingStatusInitializer",
            resultClass = ThinkingStatus::class.java,
            exampleInstance = ThinkingStatus(
                initialPrompt = "Example prompt",
                goals = Goals(
                    shortTerm = mutableListOf(Goal("Understand the user's request")),
                    longTerm = mutableListOf(Goal("Complete the user's task"))
                ),
                knowledge = Knowledge(
                    facts = mutableListOf("Initial Context: User's request received"),
                    openQuestions = mutableListOf("What is the first task?")
                ),
                executionContext = ExecutionContext(
                    nextSteps = mutableListOf("Analyze the initial prompt", "Identify key objectives"),
                )
            ),
            prompt = """
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
            model = planSettings.defaultModel,
            parsingModel = planSettings.parsingModel,
            temperature = planSettings.temperature,
            describer = describer
        ).answer(listOf(userMessage) + contextData(), api).obj
    }

    private fun updateThinking(
        api: ChatClient,
        thinkingStatus: ThinkingStatus,
        completedTasks: List<ExecutionRecord>,
    ): ThinkingStatus = ParsedActor(
        name = "UpdateQuestionsActor",
        resultClass = ThinkingStatus::class.java,
        exampleInstance = ThinkingStatus(
            initialPrompt = "Create a Python script to analyze log files and generate a summary report",
            confidence = 0.8,
            iteration = 1,
            goals = Goals(
                shortTerm = mutableListOf(
                    Goal("Understand log file format requirements", isRigid = true, priority = 1),
                    Goal("Define report structure", priority = 2),
                    Goal("Plan implementation approach", priority = 3)
                ),
                longTerm = mutableListOf(
                    Goal("Deliver working Python script", isRigid = true, priority = 1),
                    Goal("Ensure robust error handling", priority = 2),
                    Goal("Provide documentation", priority = 3)
                )
            ),
            knowledge = Knowledge(
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
            executionContext = ExecutionContext(
                completedTasks = mutableListOf(
                    "Initial requirements analysis",
                    "Project scope definition"
                ),
                currentTask = CurrentTask(
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
        prompt = """
      Given the current thinking status, the last completed task, its result, and any repeating error signals,
      update the open questions and next steps to guide the planning process.
      Reflect on what went well and what could be improved.
      Reassess the goals (paying attention to priorities and rigidity) and adjust the confidence level.
      If error patterns are recurring or progress slows, trigger a reflection loop by adding a 'reflect' task.
    """.trimIndent(),
        model = planSettings.defaultModel,
        parsingModel = planSettings.parsingModel,
        temperature = planSettings.temperature,
        describer = describer
    ).answer(
        listOf("Current thinking status: ${formatThinkingStatus(thinkingStatus)}") +
                contextData() +
                completedTasks.flatMap { record ->
                    val task: TaskConfigBase? = record.task
                    listOf(
                        "Completed task: ${task?.task_description}",
                        "Task result: ${record.result}",
                        record.reflections?.let { "Reflection: Positive: ${it.positiveNotes}, Improvements: ${it.improvementSuggestions}" }
                            ?: "")
                } +
                (currentUserMessage.get()?.let { listOf("User message: $it") } ?: listOf()),
        api
    ).obj.apply {
        currentUserMessage.set(null)
        knowledge?.facts?.apply {
            this.addAll(completedTasks.mapIndexed { index, record ->
                "Task ${(executionContext?.completedTasks?.size ?: 0) + index + 1} Result: ${record.result}"
            })
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

    private fun formatThinkingStatus(thinkingStatus: ThinkingStatus) =
        "```json\n${JsonUtil.toJson(thinkingStatus)}\n```"

    override fun contextData(): List<String> = emptyList()

    @Description("The current thinking status of the AI assistant.")
    data class ThinkingStatus(
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
        val task: TaskConfigBase? = null,
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
        val objective: String,
        @Description("Flag indicating if this goal is rigid (non-negotiable) or flexible.")
        val isRigid: Boolean = false,

        @Description("Priority level with lower numbers indicating a higher priority.")
        val priority: Int = 5
    )

    data class Reflection(
        @Description("What went well during this task execution.")
        val positiveNotes: String,

        @Description("What could be improved for future iterations.")
        val improvementSuggestions: String,

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

    data class Tasks(
        val tasks: MutableList<TaskConfigBase>? = null
    )

    companion object : CognitiveModeStrategy {
        override val singleInput: Boolean = true
        override fun getCognitiveMode(
            ui: ApplicationInterface,
            api: API,
            api2: OpenAIClient,
            planSettings: PlanSettings,
            session: Session,
            user: User?,
            describer: TypeDescriber
        ) = AutoPlanMode(ui, api, planSettings, session, user, api2, describer = describer)
    }
}