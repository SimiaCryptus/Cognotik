package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
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
class CouncilModeConfig(
    var council: List<CognitiveSchemaStrategy<out Any>> = listOf(
        ProjectManagerStrategy(name = "CEO", description = "Focus on high-level goals and business value."),
        ProjectManagerStrategy(name = "CTO", description = "Focus on technical feasibility and architecture."),
        ProjectManagerStrategy(name = "QA", description = "Focus on testing and quality assurance.")
    ),
    var maxTaskHistoryChars: Int = 20000,
    var maxTasksPerIteration: Int = 3,
    var maxIterations: Int = 10
) : CognitiveModeConfig(type = CognitiveModeType.Council)


open class CouncilMode(
    task: SessionTask,
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser
) : CognitiveMode<CouncilModeConfig>(
    task,
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(CouncilMode::class.java)
    private val currentUserMessage = AtomicReference<String?>(null)
    private val executionRecords = mutableListOf<AdaptivePlanningMode.ExecutionRecord>()
    private val reasoningStates = mutableMapOf<String, Any>()
    private var isRunning = false
    private var transcriptStream: FileOutputStream? = null
    private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:\|[^|}{\n<>()\[\]]+))}""")
    private val maxTaskHistoryChars: Int get() = config.maxTaskHistoryChars
    private val maxTasksPerIteration: Int get() = config.maxTasksPerIteration
    private val maxIterations: Int get() = config.maxIterations
    val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)

    override fun initialize() {
        log.debug("Initializing CouncilMode")
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        if (!isRunning) {
            isRunning = true
            startCouncilChat(userMessage)
        } else {
            task.echo("User: $userMessage".renderMarkdown)
            currentUserMessage.set(userMessage)
        }
    }

    override fun contextData(): List<String> = emptyList()

    private fun startCouncilChat(userMessage: String) {
        task.echo(renderMarkdown(userMessage))
        transcriptStream = transcript(task)

        val continueLoop = true
        val tabbedDisplay = TabbedDisplay(task)
        this.task.ui.pool.execute {
            try {
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

                // Initialize all council members
            config.council.forEach { strategy ->
                    val state = strategy.initialize(
                        userMessage,
                        contextData(),
                        orchestrationConfig,
                        task,
                        describer
                    )
                    reasoningStates[strategy.name] = state
                }
                writeToTranscript("# Council Chat Session\n\n## Initial Prompt\n\n$userMessage\n\n")

                var iteration = 0
                while (iteration++ < maxIterations && continueLoop) {
                    task.complete()
                    writeToTranscript("## Iteration $iteration\n\n")
                    val task = task.linkedTask("Iteration $iteration")
                    val ui = task.ui
                    val iterationTabbedDisplay = TabbedDisplay(task, additionalClasses = "iteration")

                    // Display Inputs
                    ui.newTask(false).apply {
                        iterationTabbedDisplay["Inputs"] = placeholder
                        val inputTabs = TabbedDisplay(this)
                        ui.newTask(false).apply {
                            inputTabs["Project Info"] = placeholder
                            contextData().forEach { complete(renderMarkdown(it, tabs = false)) }
                            complete()
                        }
                        formatEvalRecords().forEachIndexed { index, it ->
                            ui.newTask(false).apply {
                                inputTabs["Task ${index + 1}"] = placeholder
                                complete(renderMarkdown(it))
                            }
                            complete(renderMarkdown(it))
                        }
                        // Display Council States
                    config.council.forEach { strategy ->
                            ui.newTask(false).apply {
                                inputTabs["${strategy.name} State"] = placeholder
                                val state = reasoningStates[strategy.name]!!
                                complete(renderMarkdown(formatState(strategy, state)))
                            }
                        }
                    }

                    // Nominations
                    val nominations = mutableListOf<Pair<String, AdaptivePlanningMode.TaskData>>()
                val nominationFutures = config.council.map { strategy ->
                        ui.pool.submit<List<Pair<String, AdaptivePlanningMode.TaskData>>> {
                            try {
                                val state = reasoningStates[strategy.name]!!
                                val tasks = getNominations(userMessage, strategy, state, task)
                                val pairs =
                                    tasks?.map { strategy.name to it } ?: emptyList()
                                pairs
                            } catch (e: Exception) {
                                log.error("Error getting nominations from ${strategy.name}", e)
                                emptyList()
                            }
                        }
                    }
                    nominations.addAll(nominationFutures.flatMap { it.get() })

                    if (nominations.isEmpty()) {
                        task.add(renderMarkdown("No tasks nominated. Finishing Council Chat."))
                        break
                    }

                    // Voting
                    val selectedTasks = if (nominations.size > 1) {
                        voteOnTasks(nominations, userMessage, task)
                    } else {
                        nominations.map { it.second }
                    }

                    if (selectedTasks.isEmpty()) {
                        task.add(renderMarkdown("No tasks selected by vote. Finishing Council Chat."))
                        break
                    }

                    // Execution
                    val taskResults = mutableListOf<Pair<TaskExecutionConfig, Future<String>>>()
                    for ((index, currentTask) in selectedTasks.withIndex()) {
                        val currentTaskId = "task_${index + 1}"
                        writeToTranscript("### Task $currentTaskId\n\n")
                        val taskExecutionTask = ui.newTask(false)
                        val taskConfig = currentTask.task.tasks?.firstOrNull()
                        val taskDescription = taskConfig?.task_description ?: "No description provided."
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
                        taskResults.add(Pair(taskConfig!!, future))
                    }

                    val completedTasks = taskResults.map { (task, future) ->
                        val result = future.get()
                        writeToTranscript("**Result:**\n\n$result\n\n")
                        AdaptivePlanningMode.ExecutionRecord(
                            time = Date(),
                            iteration = iteration,
                            task = task,
                            result = result
                        )
                    }
                    executionRecords.addAll(completedTasks)

                    // Update States
                config.council.forEach { strategy ->
                        val oldState = reasoningStates[strategy.name]!!
                        val newState = updateState(strategy, oldState, completedTasks, currentUserMessage.get(), contextData(), orchestrationConfig, task, describer)
                        reasoningStates[strategy.name] = newState
                    }
                    currentUserMessage.set(null)
                }
                task.complete("Council Chat completed.")
            } catch (e: Throwable) {
                task.error(e)
                log.error("Error in startCouncilChat", e)
            } finally {
                isRunning = false
                transcriptStream?.flush()
                transcriptStream?.close()
                transcriptStream = null
                task.complete()
            }
        }
    }

    private fun <T> updateState(
        strategy: CognitiveSchemaStrategy<T>,
        state: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): T {
        @Suppress("UNCHECKED_CAST")
        return strategy.update(state as T, completedTasks, userMessage, contextData, orchestrationConfig, task, describer)
    }

    private fun <T> formatState(strategy: CognitiveSchemaStrategy<T>, state: Any): String {
        @Suppress("UNCHECKED_CAST")
        return strategy.formatState(state as T)
    }

    private fun <T> getNominations(
        userMessage: String,
        strategy: CognitiveSchemaStrategy<T>,
        state: Any,
        task: SessionTask
    ): List<AdaptivePlanningMode.TaskData>? {
        @Suppress("UNCHECKED_CAST")
        val typedState = state as T
        Tasks.initDescriber(orchestrationConfig, describer)
        val parsedActor = ParsedAgent(
            name = "TaskChooser",
            resultClass = Tasks::class.java,
            exampleInstance = Tasks(mutableListOf()),
            prompt = buildString {
                append("As ${strategy.name} (${strategy.description}), given the following input, choose up to ")
                append(maxTasksPerIteration)
                append(" tasks to execute.\n")
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
                                }"
                            }
                        }
                        .joinToString("\n\n"))
            },
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        )
        val answer = parsedActor.answer(
            listOf(userMessage) + contextData() + listOf(
                """
        Current thinking status: ${strategy.formatState(typedState)}
        ${strategy.getTaskSelectionGuidance(typedState)}
        """.trimIndent()
            ) + formatEvalRecords(),
        )

        val executor = this.task.ui.pool ?: return null
        val processor = FixedConcurrencyProcessor(executor, 4)
        val expandedTasks = processTaskExpansionRecursive(answer.text, task, parsedActor, processor)

        val tasks = expandedTasks.map { taskData ->
            taskData.task.tasks?.map { taskConfigBase ->
                AdaptivePlanningMode.TaskData(
                    Tasks(mutableListOf(taskConfigBase)),
                    taskData.actorResponse
                )
            } ?: emptyList()
        }.flatten()

        return tasks.take(maxTasksPerIteration)
    }

    private fun voteOnTasks(
        nominations: List<Pair<String, AdaptivePlanningMode.TaskData>>,
        userMessage: String,
        task: SessionTask
    ): List<AdaptivePlanningMode.TaskData> {
        val votes = mutableMapOf<Int, Int>()
        val nominationDescriptions = nominations.mapIndexed { index, (nominator, taskData) ->
            val taskDesc = taskData.task.tasks?.joinToString("\n") { it.task_description ?: "No description" }
            "${index + 1}. [$nominator] $taskDesc"
        }.joinToString("\n\n")

    config.council.forEach { strategy ->
            val state = reasoningStates[strategy.name]!!
            val voter = ParsedAgent(
                name = "Voter",
                resultClass = Voting::class.java,
                exampleInstance = Voting(listOf(1, 3), "Tasks 1 and 3 align with goals."),
                prompt = "Vote for the best tasks.",
                model = orchestrationConfig.defaultSmart.getChildClient(task),
                parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
                temperature = orchestrationConfig.temperature
            )
            val vote = voter.answer(
                listOf(
                    "User Message: $userMessage",
                    "My Role: ${strategy.name} - ${strategy.description}",
                    "My State: ${formatState(strategy, state)}",
                    "Nominations:\n$nominationDescriptions",
                    "Please vote for the best tasks by index (1-based)."
                )
            ).obj
            vote.votes.forEach { index ->
                if (index in 1..nominations.size) {
                    votes[index] = votes.getOrDefault(index, 0) + 1
                }
            }
        }
        
        return votes.entries.sortedByDescending { it.value }
        .take(maxTasksPerIteration)
            .map { nominations[it.key - 1].second }
    }

    private fun runTask(
        coordinator: TaskOrchestrator,
        currentTask: TaskExecutionConfig,
        userMessage: String,
        task: SessionTask
    ): String {
        val taskImpl = TaskType.getImpl(orchestrationConfig, currentTask)
        val result = StringBuilder()
        taskImpl.run(
            agent = coordinator,
            messages = listOf(userMessage) + formatEvalRecords(),
            task = task,
            resultFn = { result.append(it) },
            orchestrationConfig = orchestrationConfig,
        )
        return result.toString()
    }

    private fun processTaskExpansionRecursive(
        currentText: String,
        task: SessionTask,
        parsedActor: ParsedAgent<Tasks>,
        processor: FixedConcurrencyProcessor
    ): List<AdaptivePlanningMode.TaskData> {
        val match = expansionExpressionPattern.find(currentText)
        if (match == null) {
            return try {
                val chosenTasks = parsedActor.getParser().apply(currentText)
                listOf(AdaptivePlanningMode.TaskData(chosenTasks, currentText))
            } catch (e: Exception) {
                log.error("Error parsing task text: $currentText", e)
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

    private fun formatEvalRecords(): List<String> {
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
        ${record.result}
      """
            if (currentLength + formattedRecord.length > maxTaskHistoryChars) {
                formattedRecords.add("... (earlier records truncated)")
                break
            }
            formattedRecords.add(0, formattedRecord)
            currentLength += formattedRecord.length
        }
        return formattedRecords
    }

    private fun transcript(task: SessionTask): FileOutputStream? {
        val transcriptFile = "council_chat_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.complete(
            "Writing transcript to <a href='$link' target='_blank'>$link</a>"
        )
        return markdownTranscript
    }

    private fun writeToTranscript(content: String) {
        transcriptStream?.write(content.toByteArray())
    }

    data class Voting(
        @Description("The indices of the tasks to execute (1-based).")
        val votes: List<Int> = emptyList(),
        @Description("Reasoning for the votes.")
        val reasoning: String = ""
    )

    companion object {
        val inputCnt = 1
    }
}