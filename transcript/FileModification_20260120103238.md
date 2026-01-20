# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/AdaptivePlanningMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
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
            task.echo("User: $userMessage".renderMarkdown(true))
            currentUserMessage.set(userMessage)
        }
    }

    private fun startAutoPlanChat(task : SessionTask, userMessage: String) {
        log.debug("Starting auto plan chat with initial message: $userMessage")
        task.echo(userMessage.renderMarkdown())
        transcriptStream = task.transcript()

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
                                complete(it.renderMarkdown())
                            }
                            complete(it.renderMarkdown())
                        }
                        inputTabs.newTask("Thinking Status").apply {
                            complete(config.cognitiveStrategy.formatState(currentThinkingStatus).renderMarkdown())
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
                        iterationTabbedDisplay["Errors"]?.append("Error choosing next task: ${e.message}".renderMarkdown())
                        break
                    }

                    if (nextTask?.isEmpty() != false) {
                        log.debug("No more tasks to execute")
                        task.add("No more tasks to execute. Finishing Auto Plan Chat.".renderMarkdown())
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
                        taskExecutionTask.add("\n```json\n${taskConfig?.toJson()}\n```\n".renderMarkdown(true))
                        writeToTranscript("**Description:** $taskDescription\n\n```json\n${JsonUtil.toJson(taskConfig)}\n```\n\n")
                        taskExecutionTask.expandable(
                            "Task Configuration",

                          """
                           Executing task: `$currentTaskId` - $taskDescription
                          Full TaskData JSON:
                          ```json
                          ${JsonUtil.toJson(taskConfig)}
                          ```
                          """.trimIndent().renderMarkdown(true)
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
                          "Updated Thinking Status:\n${
                            config.cognitiveStrategy.formatState(updatedStatus)
                          }".renderMarkdown()
                        )
                        writeToTranscript("```json\n${JsonUtil.toJson(updatedStatus)}\n```\n\n")
                    } catch (e: Exception) {
                        log.error("Error updating thinking status", e)
                        thinkingStatusTask.error(e)
                        iterationTabbedDisplay["Errors"]?.append("Error updating thinking status: ${e.message}".renderMarkdown())
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
                  "Auto Plan Chat completed. Final thinking status:\n${
                    reasoningState.get()?.let {
                      config.cognitiveStrategy.formatState(it)
                    } ?: "null"
                  }".renderMarkdown()
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CodingMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.MethodTypeDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.jsonCast
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

open class CodingMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User
) : CognitiveMode<CodingMode.CodingModeConfig>(orchestrationConfig, session, user) {

    class CodingModeConfig(
        var codeRuntime: CodeRuntimes = CodeRuntimes.GroovyRuntime
    ) : CognitiveModeConfig(type = CognitiveModeType.Coding)

    protected val history = mutableListOf<Pair<String, ModelSchema.Role>>()

    inner class TaskFunctionImpl<T : TaskExecutionConfig, U : TaskTypeConfig>(
      private val taskType: TaskType<*, *>?,
      private val task: SessionTask
    ) : TaskFunction<T>(
        executionConfigClass = taskType?.executionConfigClass as Class<out T>
    ) {
        override fun call(executionConfig: Any, message: String): String {
            var result = ""
            val onComplete = Semaphore(0)
            val resultFn: (String) -> Unit = {
                result = it
                onComplete.release()
            }
            try {
            try {
                orchestrationConfig.getImpl(taskType as TaskType<T, U>, (executionConfig.jsonCast<Map<String,Any>>()+mapOf(
                    "task_type" to taskType.name
                )).jsonCast()).run(
                    agent = TaskOrchestrator(
                      user = user,
                      session = session,
                      dataStorage = task.ui.dataStorage,
                      root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                          ?: task.ui.dataStorage.getSessionDir(user, session).toPath()
                          ?: File(".").toPath()
                    ),
                    messages = listOf(message),
                    task = task,
                    resultFn = resultFn,
                    orchestrationConfig = orchestrationConfig
                )
            } catch (e: Throwable) {
                result = "Error initiating task: ${e.message}"
                onComplete.release()
            }
                if (!onComplete.tryAcquire(1, TimeUnit.HOURS)) {
                    throw RuntimeException("Task execution timed out")
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to execute task", e)
            }
            return result
        }
    }

    abstract class TaskFunction<T : TaskExecutionConfig>(
        val executionConfigClass: Class<out T>,
    ) : MethodTypeDescriber {
        override fun getMethodTypes(methodName: String): List<Type> {
            return if (methodName == "call") listOf(executionConfigClass) else emptyList()
        }
        abstract fun call(executionConfig: Any, messages: String): String
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        val transcript = task.transcript()
        try {
            transcript?.write("User: $userMessage\n".toByteArray())
            history.add(userMessage to ModelSchema.Role.user)
            val response = if (orchestrationConfig.autoFix) {
                plan(task)
            } else {
                val baseHistory = history.dropLast(1)
                Discussable(
                    task = task,
                    heading = "Code Plan",
                    userMessage = { userMessage },
                    initialResponse = { _ -> plan(task) },
                    outputFn = { result ->
                        ("```" + config.codeRuntime.name.lowercase()
                            .replace("runtime", "") + "\n" + result.code + "\n```").renderMarkdown()
                    },
                    reviseResponse = { discussionHistory ->
                        generateCode(task, baseHistory + discussionHistory)
                    }
                ).call() ?: throw IllegalStateException("Discussion failed to produce a result")
            }
            val tabs = TabbedDisplay(task)
            tabs["Code"] = ("```" + config.codeRuntime.name.lowercase().replace("runtime", "") + "\n" + response.code + "\n```").renderMarkdown()
            transcript?.write("Code:\n${response.code}\n".toByteArray())
            task.resolveUserFile(".logs/code_${now()}.${config.codeRuntime.extension}")?.writeBytes(response.code.toByteArray())
            val executionResult = response.result // execute code
            output(executionResult, tabs, transcript, response)
        } catch (e: Throwable) {
            log.error("Error during code execution", e)
            task.error(e)
            history.add("Error: ${e.message}" to ModelSchema.Role.system)
            transcript?.write("Error: ${e.message}\n".toByteArray())
        }
    }

    private fun now(): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS")
        return fmt.format(java.util.Date())
    }

    open fun output(
        executionResult: CodeAgent.ExecutionResult,
        tabs: TabbedDisplay,
        transcript: FileOutputStream?,
        response: CodeAgent.CodeResult
    ) {
        val output = executionResult.resultOutput
        val value = executionResult.resultValue
        if (output.isNotBlank()) {
            tabs["Output"] = "```text\n$output\n```".renderMarkdown()
        }
        if (value.isNotBlank() && value != "null") {
            tabs["Result"] = "```text\n$value\n```".renderMarkdown()
        }
        transcript?.write("Output:\n$output\nValue:\n$value\n".toByteArray())

        history.add(response.code to ModelSchema.Role.assistant)
        val resultMsg = listOfNotNull(
            if (output.isNotBlank()) "Output:\n$output" else null,
            if (value.isNotBlank() && value != "null") "Result:\n$value" else null
        ).joinToString("\n")
        if (resultMsg.isNotBlank()) {
            history.add(resultMsg to ModelSchema.Role.system)
        }
    }

    open fun plan(task: SessionTask) = generateCode(task, history)

    private fun generateCode(
        task: SessionTask,
        messages: List<Pair<String, ModelSchema.Role>>
    ): CodeAgent.CodeResult {
        return symbols(task).let { symbols ->
            CodeAgent(
                codeRuntime = CodeRuntimes.getRuntime(config.codeRuntime, symbols),
                model = orchestrationConfig.defaultSmart.getChildClient(task),
                details = "You are in an interactive coding session. Execute code to answer the user.",
                temperature = orchestrationConfig.temperature,
                symbols = symbols,
                describer = describer,
            ).respond(
                CodeAgent.CodeRequest(
                    messages = messages
                )
            )
        }

    }

    open val describer: TypeDescriber = AbbrevWhitelistYamlDescriber("com.simiacryptus")

    open fun symbols(task: SessionTask): Map<String, Any> =
        orchestrationConfig.taskSettings.map { (name, taskTypeConfig) ->
            Pair(
                name.replace("[^a-zA-Z01-9_]".toRegex(), "_"),
                TaskFunctionImpl<com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig, TaskTypeConfig>(taskTypeConfig.task_type?.let {
                    TaskType.valueOf(
                        it
                    )
                }, task)
            )
        }.toMap() + mapOf(
            "workingDir" to (orchestrationConfig.absoluteWorkingDir?.let { File(it).absoluteFile } ?: "."),
            "smartModel" to orchestrationConfig.defaultSmart.getChildClient(task),
            "fastModel" to orchestrationConfig.defaultFast.getChildClient(task),
            "task" to task,
        )

    override fun contextData(): List<String> = emptyList()

    companion object {
        private val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(CodingMode::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CognitiveMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * The CognitiveMode interface defines the “cognitive” strategy
 * which handles user input, initial planning, execution and iterative
 * thought updates.
 */
abstract class CognitiveMode<U : CognitiveModeConfig>(
    val orchestrationConfig: OrchestrationConfig,
    val session: Session,
    val user: User,
) {
    val config: U
        get() = orchestrationConfig.cognitiveSettings as? U
            ?: throw IllegalStateException("Cognitive settings not defined")

    val enabledTasks get() = TaskType.getAvailableTaskTypes(orchestrationConfig)

    /**
     * Initialize the internal cognitive state.
     */
    open fun initialize(task : SessionTask) {}

    /**
     * Handle a user message and trigger the appropriate planning or execution.
     */
    abstract fun handleUserMessage(userMessage: String, task: SessionTask)

    /**
     * Get the context data accumulated during execution.
     * This is useful for sub-planning tasks to collect results.
     */
    abstract fun contextData(): List<String>

    val name: String? = (this@CognitiveMode.config.type?.name ?: this.javaClass.simpleName)

    fun SessionTask.transcript(name: String? = this@CognitiveMode.name): FileOutputStream? {
        val transcriptFile = "transcript/${name}_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(linkTo(transcriptFile), resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        add("[Transcript](${link.removeSuffix(".md")}.html)".renderMarkdown())
        return markdownTranscript
    }
}

class CognitiveModeTypeSerializer : DynamicEnumSerializer<CognitiveModeType<*>>(CognitiveModeType::class.java)
class CognitiveModeTypeDeserializer : DynamicEnumDeserializer<CognitiveModeType<*>>(CognitiveModeType::class.java)
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CognitiveModeConfig.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase

@JsonTypeIdResolver(CognitiveModeConfig.TypeIdResolver::class)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CUSTOM,
    property = "type",
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    visible = true
)
open class CognitiveModeConfig(
    var type: CognitiveModeType<*>? = null
) {
    class TypeIdResolver : TypeIdResolverBase() {
        override fun idFromValue(value: Any): String? {
            return (value as? CognitiveModeConfig)?.type?.name
        }

        override fun idFromValueAndType(value: Any, suggestedType: Class<*>): String? {
            return idFromValue(value)
        }

        override fun typeFromId(context: DatabindContext, id: String): JavaType {
            val type = CognitiveModeType.valueOf(id)
            return context.constructType(type.configClass)
        }

        override fun getMechanism(): JsonTypeInfo.Id {
            return JsonTypeInfo.Id.CUSTOM
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CognitiveModeType.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.CodingMode.CodingModeConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.DynamicEnum

@JsonDeserialize(using = CognitiveModeTypeDeserializer::class)
@JsonSerialize(using = CognitiveModeTypeSerializer::class)
class CognitiveModeType<out U : CognitiveModeConfig>(
    name: String,
    val configClass: Class<out U>,
    val description: String? = null,
    val inputCnt: Int = 1
) : DynamicEnum<CognitiveModeType<*>>(name) {
    companion object {
        @JvmStatic
        val entries: List<CognitiveModeType<*>> get() = values()
        @JvmStatic
        val Chat = CognitiveModeType("Chat", ConversationalModeConfig::class.java, inputCnt = ConversationalMode.inputCnt)
        @JvmStatic
        val Adaptive = CognitiveModeType("Adaptive", AdaptivePlanningConfig::class.java, inputCnt = AdaptivePlanningMode.inputCnt)
        @JvmStatic
        val Waterfall = CognitiveModeType("Waterfall", WaterfallMode.WaterfallModeConfig::class.java, inputCnt = WaterfallMode.inputCnt)
        @JvmStatic
        val Hierarchical = CognitiveModeType("Hierarchical", CognitiveModeConfig::class.java, inputCnt = HierarchicalPlanningMode.inputCnt)
        @JvmStatic
        val Parallel = CognitiveModeType("Parallel", ParallelModeConfig::class.java, inputCnt = ParallelMode.inputCnt)
        @JvmStatic
        val Protocol = CognitiveModeType("Protocol", ProtocolModeConfig::class.java, inputCnt = ProtocolMode.inputCnt)
        @JvmStatic
        val Council = CognitiveModeType("Council", CouncilModeConfig::class.java, inputCnt = CouncilMode.inputCnt)
        @JvmStatic
        val PersonaChat = CognitiveModeType("PersonaChat", PersonaChatConfig::class.java, inputCnt = PersonaChatMode.inputCnt)
        @JvmStatic
        val Coding = CognitiveModeType("Coding", CodingModeConfig::class.java)

        private val constructors by lazy {
            val map =
                mutableMapOf<CognitiveModeType<*>, (OrchestrationConfig, Session, User) -> CognitiveMode<*>>()

            fun <U : CognitiveModeConfig> register(
                type: CognitiveModeType<U>,
                constructor: (OrchestrationConfig, Session, User) -> CognitiveMode<U>
            ) {
                map[type] = { config, session, user ->
                    constructor(config, session, user)
                }
                register(CognitiveModeType::class.java, type)
            }

            register(Chat) {  config, session, user -> ConversationalMode(config, session, user) }
            register(Adaptive) {  config, session, user -> AdaptivePlanningMode(config, session, user) }
            register(Waterfall) {  config, session, user -> WaterfallMode(config, session, user) }
            register(Hierarchical) {  config, session, user -> HierarchicalPlanningMode(config, session, user) }
            register(Parallel) {  config, session, user -> ParallelMode(config, session, user) }
            register(Protocol) {  config, session, user -> ProtocolMode(config, session, user) }
            register(Council) {  config, session, user -> CouncilMode(config, session, user) }
            register(PersonaChat) {  config, session, user -> PersonaChatMode(config, session, user) }
            register(Coding) {  config, session, user -> CodingMode(config, session, user) }
            map
        }

        fun values(): List<CognitiveModeType<*>> {
            @Suppress("SENSELESS_COMPARISON") require(constructors != null) // Trigger lazy init
            return values(CognitiveModeType::class.java)
        }

        fun valueOf(name: String): CognitiveModeType<*> {
            @Suppress("SENSELESS_COMPARISON") require(constructors != null) // Trigger lazy init
            return valueOf(CognitiveModeType::class.java, name)
        }
    }

    fun getImpl(
        orchestrationConfig: OrchestrationConfig,
        session: Session,
        user: User
    ) = (constructors[this]?.invoke(orchestrationConfig, session, user)
        ?: throw IllegalStateException("No constructor for cognitive mode ${name}"))

    fun newSettings(): CognitiveModeConfig {
        val instance = configClass.getDeclaredConstructor().newInstance()
        instance.type = this
        return instance
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CognitiveSchemaStrategy.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient

@JsonDeserialize(using = CognitiveSchemaStrategyDeserializer::class)
@JsonSerialize(using = CognitiveSchemaStrategySerializer::class)
abstract class CognitiveSchemaStrategy(
    name: String,
    val description: String
) : DynamicEnum<CognitiveSchemaStrategy>(name) {
    abstract fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any

    abstract fun update(
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any

    abstract fun formatState(state: Any): String
    abstract fun getTaskSelectionGuidance(state: Any): String

    companion object {
        val ProjectManager = ProjectManagerStrategy()
        val ScientificMethod = ScientificMethodStrategy()
        val AgileDeveloper = AgileDeveloperStrategy()
        val CriticalAuditor = CriticalAuditorStrategy()
        val CreativeWriter = CreativeWriterStrategy()

        init {
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, ProjectManager)
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, ScientificMethod)
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, AgileDeveloper)
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, CriticalAuditor)
            DynamicEnum.register(CognitiveSchemaStrategy::class.java, CreativeWriter)
        }

        fun values() = DynamicEnum.values(CognitiveSchemaStrategy::class.java)
        fun valueOf(name: String) = DynamicEnum.valueOf(CognitiveSchemaStrategy::class.java, name)
    }

}

class CognitiveSchemaStrategySerializer : JsonSerializer<CognitiveSchemaStrategy>() {
    override fun serialize(value: CognitiveSchemaStrategy, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.name)
    }
}

class CognitiveSchemaStrategyDeserializer : JsonDeserializer<CognitiveSchemaStrategy>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CognitiveSchemaStrategy {
        return CognitiveSchemaStrategy.valueOf(p.text)
    }
}


open class ProjectManagerStrategy(
    name: String = "Project Manager",
    description: String = "Standard goal-oriented planning.",
    val initPrompt: String? = null,
    val updatePrompt: String? = null
) : CognitiveSchemaStrategy(name, description) {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
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
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as AdaptivePlanningMode.ReasoningState
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

    override fun formatState(state: Any): String {
        return "```json\n${JsonUtil.toJson(state)}\n```"
    }

    override fun getTaskSelectionGuidance(state: Any): String {
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

class ScientificMethodStrategy : CognitiveSchemaStrategy("Scientific Researcher", "Hypothesis-driven investigation.") {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
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
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as ScientificState
        return ParsedAgent(
            name = "ScientificUpdater",
            resultClass = ScientificState::class.java,
            exampleInstance = state,
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

    override fun formatState(state: Any) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: Any): String {
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

class AgileDeveloperStrategy : CognitiveSchemaStrategy("Agile Developer", "Iterative Test-Driven Development.") {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
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
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as AgileState
        return ParsedAgent(
            name = "AgileUpdater",
            resultClass = AgileState::class.java,
            exampleInstance = state,
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

    override fun formatState(state: Any) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: Any): String {
        val s = state as AgileState
        return when (s.currentPhase) {
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

class CriticalAuditorStrategy : CognitiveSchemaStrategy("Critical Auditor", "Security and logic validation.") {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
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
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as AuditState
        return ParsedAgent(
            name = "AuditUpdater",
            resultClass = AuditState::class.java,
            exampleInstance = state,
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

    override fun formatState(state: Any) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: Any): String {
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

class CreativeWriterStrategy : CognitiveSchemaStrategy("Creative Writer", "Narrative and content generation.") {
    override fun initialize(
        userMessage: String,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
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
        currentState: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        val state = currentState as NarrativeState
        return ParsedAgent(
            name = "WriterUpdater",
            resultClass = NarrativeState::class.java,
            exampleInstance = state,
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

    override fun formatState(state: Any) = JsonUtil.toJson(state)
    override fun getTaskSelectionGuidance(state: Any): String {
        return "Focus on generating content. If the tone is off, select a task to rewrite or edit. Do not execute code unless it is to generate text."
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/ConversationalMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path

open class ConversationalModeConfig(
    var useExpansionSyntax: Boolean = true
) : CognitiveModeConfig(type = CognitiveModeType.Chat)


/**
 * A cognitive mode that executes tasks based on user input while maintaining conversation history.
 */
open class ConversationalMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser
) : CognitiveMode<ConversationalModeConfig>(
    orchestrationConfig,
    session,
    user
) {

    init {
        require(orchestrationConfig.defaultSmartModel != null) { "Default model must be specified in orchestration config" }
        require(orchestrationConfig.defaultFastModel != null) { "Parsing model must be specified in orchestration config" }
    }

    private val messagesLock = Any()
    private val messages get() = messageMaps.computeIfAbsent(session) { ConcurrentLinkedQueue() }
    private val messageBuffer = ConcurrentLinkedQueue<String>()
    private var transcriptStream: FileOutputStream? = null
    private var isProcessing = false
    private val systemPrompt =
        "Given the following input, choose ONE task to execute and describe it in detail. Be sure to include all necessary parameters for execution, and all data required to complete the task."
    private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
    private val idSubPattern =
        """[^|\n,/\\;}\]\[><()@]+""" // Matches any valid identifier character except for special characters used in the expansion syntax
    private val expansionExpressionPattern =
        Regex("""@\[($idSubPattern(?:[|,]$idSubPattern)+)]""") // Matches @[option1|option2|option3]
    private val sequenceExpansionPattern =
        Regex("""@\{([^}]+(?:\s*->\s*[^}]+)+)\}""") // Matches @{item1 -> item2 -> item3}
    private val rangeExpansionPattern =
        Regex("""@\((-?\d+)(?:\.{2,3}| to )(-?\d+)(?:(?::| by )(\d+))?\)""") // Matches @(start..end:step) or @(start to end by step)

    override fun initialize(task : SessionTask) {
        log.debug(
            "ConversationalMode initialized with task types: ${enabledTasks.joinToString(", ") { it.name }}",
            RuntimeException()
        )
        transcriptStream = task.transcript()
        log.debug(
            "Task configurations: ${
                orchestrationConfig.taskSettings.values.joinToString(", ") {
                    "${it.task_type}${it.name?.let { name -> ":$name" } ?: ""}"
                }
            }")
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        log.debug("Handling user message: ${JsonUtil.toJson(userMessage)}")
        val parserChatter = orchestrationConfig.defaultFast.getChildClient(task)
        val defaultChat = this@ConversationalMode.orchestrationConfig.defaultSmart.getChildClient(task)

        synchronized(messagesLock) {
            messageBuffer.add(userMessage)
            if (isProcessing) {
                log.debug("Already processing a task, adding message to buffer: ${userMessage}")
                return
            }
            isProcessing = true
        }

        task.echo(userMessage.renderMarkdown(true))
        writeToTranscript("## User\n\n$userMessage\n\n")
        task.ui.pool.submit {
            try {
                while (!Thread.interrupted()) {
                    sleep(100) // Brief pause to allow batching of messages
                    val userMessage = messageBuffer.poll() ?: continue
                    val task = task.newTask()
                    execute(task, userMessage, parserChatter, defaultChat)
                }
            } finally {
                synchronized(messagesLock) {
                    isProcessing = false
                }
            }
        }
    }

    private fun execute(
        task: SessionTask,
        userMessage: String,
        parsingChatter: ChatInterface,
        defaultChat: ChatInterface
    ) {
        try {
            val expandedUserMessage = expandTopics(userMessage)
            val expansionFunctions = processMsgRecursive(
                expandedUserMessage, task, parsingChatter, defaultChat
            )
            val aggregateResponse = StringBuilder()
            runAll(task, expansionFunctions, aggregateResponse)
            // Now add the original user message and aggregate response to history
            synchronized(messagesLock) {
                messages.add(ModelSchema.ChatMessage(ModelSchema.Role.user, expandedUserMessage.toContentList()))
                if (aggregateResponse.isNotEmpty()) {
                    messages.add(
                        ModelSchema.ChatMessage(
                            ModelSchema.Role.assistant, aggregateResponse.toString().toContentList()
                        )
                    )
                }
            }

            // Extract topics from the aggregated response
            if (config.useExpansionSyntax && aggregateResponse.isNotEmpty()) {
                try {
                    writeToTranscript("## Assistant\n\n${aggregateResponse}\n\n")
                    val model = defaultChat
                    val topics = extractTopics(aggregateResponse.toString(), model, parsingChatter)
                    topics.topics?.forEach { (topicType, entities) ->
                        val topicList = aggregateTopics.computeIfAbsent(topicType) { mutableListOf() }
                        synchronized(topicList) {
                            topicList.addAll(entities)
                        }
                    }
                    if (topics.topics?.isNotEmpty() == true) {
                        val topicsText = topics.topics.entries.joinToString("\n") {
                            "* `{${it.key}}` - ${it.value.joinToString(", ") { "`$it`" }}"
                        }
                        task.complete(topicsText.renderMarkdown(), additionalClasses = "topics")
                        writeToTranscript("### Topics\n\n$topicsText\n\n")
                    }
                } catch (e: Exception) {
                    log.error("Error in topic extraction", e)
                }
            }
            task.complete()
        } catch (e: Exception) {
            log.error("Error executing task", e)
            task.error(e)
        }
    }

    private fun processMsgRecursive(
        currentMessage: String, task: SessionTask, parsingChatter: ChatInterface, defaultChatter: ChatInterface
    ): List<(StringBuilder) -> Unit> {
        if (config.useExpansionSyntax) {
            val rangeMatch = rangeExpansionPattern.find(currentMessage)
            if (rangeMatch != null) {
                return expandRange(
                    currentMessage, task, rangeMatch, parsingChatter, defaultChatter
                )
            }
            val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
            if (sequenceMatch != null) {
                return listOf { finalAggregate: StringBuilder ->
                    expandSequence(
                        task,
                        sequenceMatch.groupValues[1].split(Regex("""\s*->\s*""")),
                        currentMessage,
                        sequenceMatch.value,
                        defaultChatter,
                        parsingChatter
                    )
                }
            }
            val match = expansionExpressionPattern.find(currentMessage)
            if (match != null && match.groupValues[1].split('|', ',').size > 1) {
                return expandAlternatives(
                    currentMessage, task, match
                ) { msg, tsk ->
                    processMsgRecursive(
                        msg, tsk, parsingChatter, defaultChatter = defaultChatter
                    )
                }
            }
        }
        return listOf { aggregateResponse: StringBuilder ->
            executeTask(
                currentMessage, task, aggregateResponse, defaultChatter, parsingChatter
            )
        }
    }

    private fun executeTask(
        userMessage: String,
        task: SessionTask,
        aggregateResponse: StringBuilder,
        defaultModel: ChatInterface,
        parserChatter: ChatInterface
    ) {
        val tabs = TabbedDisplay(task)
        val planTask = tabs.newTask("Plan")

        val chosenTask: Pair<ParsedResponse<Tasks>, TaskExecutionConfig>? = if (orchestrationConfig.autoFix) {
            val result = requestToTask(
                defaultModel, parserChatter,
                userMessage,
                this@ConversationalMode.orchestrationConfig,
                this@ConversationalMode.systemPrompt,
                this.getConversationContext()
            )
            planTask.add(result.first.text.renderMarkdown())
            planTask.complete("Executing task:\n```json\n${JsonUtil.toJson(result.second)}\n```".renderMarkdown())
            result
        } else {
            Discussable(
                task = planTask,
                heading = "Plan Task",
                userMessage = { userMessage },
                initialResponse = {
                    requestToTask(
                        defaultModel, parserChatter,
                        userMessage,
                        this@ConversationalMode.orchestrationConfig,
                        this@ConversationalMode.systemPrompt,
                        this.getConversationContext()
                    )
                },
                outputFn = { (reasoning, taskConfig) ->
                    reasoning.text.renderMarkdown() +
                            "\n\nProposed Task:\n```json\n${JsonUtil.toJson(taskConfig)}\n```".renderMarkdown()
                },
                reviseResponse = { history ->
                    val discussionContext = history.map { (msg, role) ->
                        "${role.name.uppercase()}: $msg"
                    }
                    requestToTask(
                        defaultModel, parserChatter,
                        userMessage,
                        this@ConversationalMode.orchestrationConfig,
                        this@ConversationalMode.systemPrompt,
                        this.getConversationContext() + discussionContext
                    )
                }
            ).call()
        }

        // Add task configuration to conversation history
        val taskConfigJson = JsonUtil.toJson(chosenTask)
        synchronized(messagesLock) {
            messages.add(
                ModelSchema.ChatMessage(
                    ModelSchema.Role.assistant,
                    "Executing task:\n```json\n$taskConfigJson\n```".toContentList()
                )
            )
        }

        val resultRef = AtomicReference<String>()
        tabs.newTask("Run").apply {
            orchestrationConfig.getImpl(chosenTask?.component2()).run(
                agent = TaskOrchestrator(
                  user = user,
                  session = session,
                  dataStorage = ui.dataStorage!!,
                  root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                      ?: ui.dataStorage.getSessionDir(user, session).toPath()
                      ?: File(".").toPath()),
                messages = getConversationContext().takeLast(10) + listOf("USER: $userMessage"),
                task = this,
                resultFn = { result ->
                    tabs.newTask("Output").complete(result.renderMarkdown())
                    // Don't add to messages here - it will be added in execute() after all expansions complete
                    resultRef.set(result)
                    aggregateResponse.append(resultRef.get() ?: "").append("\n\n")
                    task.complete()
                },
                orchestrationConfig = orchestrationConfig,
            )
            this.complete()
        }
    }

    /**
     * Executes a list of functions, each appending to the target StringBuilder, potentially in parallel.
     */
    private fun runAll(task : SessionTask, function1s: List<(StringBuilder) -> Unit>, target: StringBuilder) {
        val fixedConcurrencyProcessor = FixedConcurrencyProcessor(task.ui.pool, 4)
        function1s.map { function1 ->
            fixedConcurrencyProcessor.submit {
                function1(target)
            }
        }.forEach { it.get() }
    }

    /**
     * Expands range expressions in the format [start...end:step]
     * Creates a sequence of numbers from start to end with the given step (default 1)
     */
    private fun expandRange(
        currentMessage: String,
        task: SessionTask,
        rangeMatch: MatchResult,
        parsingChatter: ChatInterface,
        defaultChatter: ChatInterface
    ): List<(StringBuilder) -> Unit> = listOf { finalAggregate: StringBuilder ->
        val start = rangeMatch.groupValues[1].toInt()
        val end = rangeMatch.groupValues[2].toInt()
        val step = rangeMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 1
        expandSequence(
            task,
            generateSequence(start) { it + step }.takeWhile { if (step > 0) it <= end else it >= end }.toList()
                .map { it.toString() },
            currentMessage,
            rangeMatch.value,
            defaultChatter,
            parsingChatter
        )
    }

    /**
     * Expands alternative expressions in the format {option1|option2|option3}
     * Each option is processed in parallel
     */
    private fun expandAlternatives(
        currentMessage: String,
        task: SessionTask,
        match: MatchResult,
        recursiveFn: (String, SessionTask) -> List<(StringBuilder) -> Unit>
    ): List<(StringBuilder) -> Unit> {
        val tabs = TabbedDisplay(task, closable = config.useExpansionSyntax)
        return match.groupValues[1].split('|', ',').flatMap { option ->
            recursiveFn(
                currentMessage.replaceFirst(match.value, option),
                tabs.newTask(option))
        }.apply {
            tabs.update()
        }
    }

    private fun expandSequence(
        task: SessionTask,
        items: List<String>,
        currentMessage: String,
        expression: String,
        defaultChatter: ChatInterface,
        parsingChatter: ChatInterface
    ) {
        val aggregatedResponse = StringBuilder()
        val tabs = TabbedDisplay(task, closable = config.useExpansionSyntax)
        for (item in items) {
            val newMessage = currentMessage.replaceFirst(expression, item)
            val subTaskFunctions = processMsgRecursive(
                currentMessage = newMessage,
                task = tabs.newTask(item),
                defaultChatter = defaultChatter,
                parsingChatter = parsingChatter
            )
            val subAggregate = StringBuilder()
            runAll(task, subTaskFunctions, subAggregate)
            aggregatedResponse.append("[").append(item).append("]\n").append(subAggregate.toString()).append("\n")
        }
        tabs.update()
    }

    protected open fun expandTopics(userMessage: String): String {
        if (!config.useExpansionSyntax) return userMessage
        // Matches both @TopicType and @{Topic Type With Spaces}
        val topicReferencePattern = Regex("""@\{([A-Z][a-zA-Z0-9_ ]+)\}|@([A-Z][a-zA-Z0-9_]*)""")
        return topicReferencePattern.replace(userMessage) { matchResult ->
            // Group 1 is for delimited format @{Topic Type}, Group 2 is for simple format @TopicType
            val topicType = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }
            val topicList = aggregateTopics[topicType]
            val entities = synchronized(topicList ?: Any()) {
                topicList?.toList()
            }
            if (!entities.isNullOrEmpty()) {
                "@[${entities.joinToString("|")}]"
            } else {
                matchResult.value
            }
        }
    }

    private fun extractTopics(response: String, model: ChatInterface, chatInterface: ChatInterface): Topics {
        val topicsParsedActor = ParsedAgent(
            resultClass = Topics::class.java,
            prompt = "Identify topics (i.e. all named entities grouped by type) in the following text:",
            model = model,
            temperature = orchestrationConfig.temperature,
            name = "Topics",
            parsingChatter = chatInterface,
        )
        return topicsParsedActor.getParser().apply(response)
    }

    data class Topics(
        val topics: Map<String, List<String>>? = emptyMap()
    )


    /**
     * Writes content to the transcript file if available
     */
    private fun writeToTranscript(content: String) {
        transcriptStream?.write(content.toByteArray())
        transcriptStream?.flush()
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

    companion object {

        val inputCnt = 1

        private val messageMaps = ConcurrentHashMap<Session, ConcurrentLinkedQueue<ModelSchema.ChatMessage>>()
        private val log = LoggerFactory.getLogger(ConversationalMode::class.java)
        fun requestToTask(
            defaultModel: ChatInterface,
            fastModel: ChatInterface,
            userMessage: String,
            orchestrationConfig: OrchestrationConfig,
            prompt: String = "",
            history: List<String> = emptyList(),
            singleStage: Boolean = false,
        ): Pair<ParsedResponse<Tasks>, TaskExecutionConfig> {
            val describer = TaskContextYamlDescriber(orchestrationConfig)
            val availableTaskTypes = TaskType.getAvailableTaskTypes(orchestrationConfig)
            Tasks.initDescriber(orchestrationConfig, describer)
            val parsedActor = ParsedAgent(
                name = "TaskChooser",
                resultClass = Tasks::class.java,
                exampleInstance = Tasks(
                    listOfNotNull(availableTaskTypes.firstOrNull()?.let {
                        orchestrationConfig.getImpl(it).executionConfig
                    }).toMutableList()
                ),
                prompt = buildString {
                    append(prompt)
                    append("Available task types:\n")
                    append(orchestrationConfig.taskSettings.values.joinToString("\n\n") { config ->
                        val taskType = TaskType.valueOf(config.task_type ?: return@joinToString "")
                        val configName = config.name?.let { " ($it)" } ?: ""
                        "* ${taskType.name}$configName:\n  ${
                            orchestrationConfig.getImpl(taskType).promptSegment().trim().trimIndent()
                                .indent("  ")
                        }" + (orchestrationConfig.workingDir?.let { root ->
                            "\nAvailable files:\n\n" + getAvailableFiles(Path(root)).joinToString("\n") { "      - $it" } + "\n"
                        } ?: "")
                    })
                    append("\nChoose the most suitable task type and provide details of how it should be executed.")
                    if (orchestrationConfig.taskSettings.values.any { it.name != null }) {
                        append("\nNote: Some task types have multiple configurations available. You can specify which configuration to use by setting the task_config_name field.")
                    }
                },
                model = defaultModel,
                parsingChatter = fastModel,
                temperature = orchestrationConfig.temperature,
                describer = describer,
                parserPrompt = ("Task Subtype Schema:\n" + availableTaskTypes.joinToString("\n\n") { taskType ->
                    "${taskType.name}:\n  ${
                        describer.describe(taskType.executionConfigClass).trim().trimIndent().indent("  ")
                    }".trim()
                }),
                singleStage = singleStage
            )
            // Use the expanded userMessage with full conversation context for task selection
            val answer = parsedActor.answer(
                history + listOf(
                    "USER: $userMessage",
                    "Please choose a single task to execute based on the current conversation context above."
                )
            )
            val chosenTask = answer.obj.tasks?.firstOrNull() ?: throw IllegalStateException("No task was selected")
            return Pair(answer, chosenTask)
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CouncilMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.OutputStream
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

class CouncilModeConfig(
    var council: List<CognitiveSchemaStrategy> = listOf(
        CognitiveSchemaStrategy.ProjectManager,
        CognitiveSchemaStrategy.AgileDeveloper,
        CognitiveSchemaStrategy.CreativeWriter,
    ),
    var maxTaskHistoryChars: Int = 20000,
    var maxTasksPerIteration: Int = 3,
    var maxIterations: Int = 10
) : CognitiveModeConfig(type = CognitiveModeType.Council)


open class CouncilMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser
) : CognitiveMode<CouncilModeConfig>(
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(CouncilMode::class.java)
    private val currentUserMessage = AtomicReference<String?>(null)
    private val executionRecords = mutableListOf<AdaptivePlanningMode.ExecutionRecord>()
    private val reasoningStates = mutableMapOf<String, Any>()
    private var isRunning = false
    private var transcriptStream: OutputStream? = null
    private val expansionExpressionPattern = Regex("""\{([^|}{]+(?:\|[^|}{\n<>()\[\]]+))}""")
    private val maxTaskHistoryChars: Int get() = config.maxTaskHistoryChars
    private val maxTasksPerIteration: Int get() = config.maxTasksPerIteration
    private val maxIterations: Int get() = config.maxIterations
    val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)


    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        if (!isRunning) {
            isRunning = true
            startCouncilChat(task, userMessage)
        } else {
            task.echo(renderMarkdown("User: $userMessage", ui = task.ui))
            currentUserMessage.set(userMessage)
        }
    }

    override fun contextData(): List<String> = emptyList()

    private fun startCouncilChat(task : SessionTask, userMessage: String) {
        task.echo(renderMarkdown(userMessage, ui = task.ui))
        transcriptStream = task.transcript()

        val continueLoop = true
        val tabbedDisplay = TabbedDisplay(task)
        task.ui.pool.execute {
            try {
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
                    writeToTranscript("## Iteration $iteration\n\n")
                    val iterationTask = tabbedDisplay.newTask("Iteration $iteration")
                    val ui = iterationTask.ui
                    val iterationTabbedDisplay = TabbedDisplay(iterationTask, additionalClasses = "iteration")

                    // Display Inputs
                    iterationTabbedDisplay.newTask("Inputs").apply {
                        val inputTabs = TabbedDisplay(this)
                        inputTabs.newTask("Project Info").apply {
                            contextData().forEach { complete(renderMarkdown(it, tabs = false, ui = ui)) }
                            complete()
                        }
                        formatEvalRecords().forEachIndexed { index, it ->
                            inputTabs.newTask("Task ${index + 1}").apply {
                                complete(renderMarkdown(it, ui = ui))
                            }
                        }
                        // Display Council States
                        config.council.forEach { strategy ->
                            inputTabs.newTask("${strategy.name} State").apply {
                                val state = reasoningStates[strategy.name]!!
                                complete(renderMarkdown(formatState(strategy, state), ui = ui))
                            }
                        }
                    }

                    // Nominations
                    val nominations = mutableListOf<Pair<String, AdaptivePlanningMode.TaskData>>()
                    val nominationFutures = config.council.map { strategy ->
                        ui.pool.submit<List<Pair<String, AdaptivePlanningMode.TaskData>>> {
                            try {
                                val state = reasoningStates[strategy.name]!!
                                val tasks = getNominations(userMessage, strategy, state, iterationTask)
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
                        iterationTask.add(renderMarkdown("No tasks nominated. Finishing Council Chat.", ui = ui))
                        iterationTask.complete()
                        break
                    }

                    // Voting
                    val selectedTasks = if (nominations.size > 1) {
                        voteOnTasks(nominations, userMessage, iterationTask)
                    } else {
                        nominations.map { it.second }
                    }

                    if (selectedTasks.isEmpty()) {
                        iterationTask.add(renderMarkdown("No tasks selected by vote. Finishing Council Chat.", ui = ui))
                        iterationTask.complete()
                        break
                    }
                    if (!orchestrationConfig.autoFix) {
                        val semaphore = java.util.concurrent.Semaphore(0)
                        var approved = false
                        iterationTask.header("Proposed Plan", 3)
                        val planHtml = StringBuilder()
                        selectedTasks.forEachIndexed { index, taskData ->
                            planHtml.append("${index + 1}. **${taskData.task.tasks?.firstOrNull()?.task_type ?: "Task"}**: ${taskData.task.tasks?.firstOrNull()?.task_description}\n")
                        }
                        iterationTask.add(renderMarkdown(planHtml.toString(), ui = ui))
                        val buttons = StringBuilder()
                        buttons.append(ui.hrefLink("Execute Plan", "btn btn-success mr-2") {
                            approved = true
                            semaphore.release()
                        })
                        buttons.append(" ")
                        buttons.append(ui.hrefLink("Stop Council", "btn btn-danger") {
                            approved = false
                            semaphore.release()
                        })
                        iterationTask.add(buttons.toString())
                        semaphore.acquire()
                        if (!approved) {
                            iterationTask.add(renderMarkdown("Council stopped by user.", ui = ui))
                            iterationTask.complete()
                            break
                        }
                    }


                    // Execution
                    val taskResults = mutableListOf<Pair<TaskExecutionConfig, Future<String>>>()
                    for ((index, currentTask) in selectedTasks.withIndex()) {
                        val currentTaskId = "task_${index + 1}"
                        writeToTranscript("### Task $currentTaskId\n\n")
                        val taskExecutionTask = task.newTask()
                        val taskConfig = currentTask.task.tasks?.firstOrNull()
                        val taskDescription = taskConfig?.task_description ?: "No description provided."
                        taskExecutionTask.add(renderMarkdown("\n```json\n${taskConfig?.toJson()}\n```\n", ui = ui))
                        writeToTranscript("**Description:** $taskDescription\n\n```json\n${JsonUtil.toJson(taskConfig)}\n```\n\n")
                        taskExecutionTask.verbose(
                            renderMarkdown(
                                """
Executing task: `$currentTaskId` - $taskDescription
Full TaskData JSON:
```json 
${JsonUtil.toJson(taskConfig)}
```
""".trimIndent(), ui = ui
                            )
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
                        val newState = updateState(
                            strategy,
                            oldState,
                            completedTasks,
                            currentUserMessage.get(),
                            contextData(),
                            orchestrationConfig,
                            iterationTask,
                            describer
                        )
                        reasoningStates[strategy.name] = newState
                    }
                    currentUserMessage.set(null)
                    iterationTask.complete()
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

    private fun updateState(
        strategy: CognitiveSchemaStrategy,
        state: Any,
        completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
        userMessage: String?,
        contextData: List<String>,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask,
        describer: TaskContextYamlDescriber
    ): Any {
        @Suppress("UNCHECKED_CAST")
        return strategy.update(state, completedTasks, userMessage, contextData, orchestrationConfig, task, describer)
    }

    private fun formatState(strategy: CognitiveSchemaStrategy, state: Any): String {
        @Suppress("UNCHECKED_CAST")
        return strategy.formatState(state)
    }

    private fun getNominations(
        userMessage: String,
        strategy: CognitiveSchemaStrategy,
        state: Any,
        task: SessionTask
    ): List<AdaptivePlanningMode.TaskData>? {
        @Suppress("UNCHECKED_CAST")
        val typedState = state
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
                                    orchestrationConfig.getImpl(taskType).promptSegment().trim()
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

        val executor = task.ui.pool ?: return null
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
        val taskImpl = orchestrationConfig.getImpl(currentTask)
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
                    val subTask = tabs.newTask(option)
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/HierarchicalPlanningMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.OutputStream
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

open class HierarchicalPlanningMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser,
    val maxConcurrency: Int = 4,
    private val maxIterations: Int = 200,
    val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)
) : CognitiveMode<CognitiveModeConfig>(
    orchestrationConfig,
    session,
    user
) {
    private val goalIdCounter = AtomicInteger(1)
    private val taskIdCounter = AtomicInteger(1)
    private val isRunning = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val goalTree = ConcurrentHashMap<String, Goal>()
    private val taskMap = ConcurrentHashMap<String, Task>()
    private val goalTasks = ConcurrentHashMap<String, SessionTask>()
    private val taskTasks = ConcurrentHashMap<String, SessionTask>()
    private var updateGoalTreeUI: () -> Unit = {}
    private var updateLogUI: () -> Unit = {}
    private var debouncedUpdateGoalTreeUI: () -> Unit = {}
    private var periodicUpdateFuture: ScheduledFuture<*>? = null
    private val sessionLog = StringBuilder()
    private var transcriptStream: OutputStream? = null

    fun logToSession(message: String) {
        log.info(message)
        sessionLog.append(message).append("\n")
        updateLogUI()
        transcriptStream?.write("$message\n".toByteArray())
        transcriptStream?.flush()
    }

    lateinit var processor: FixedConcurrencyProcessor

    override fun initialize(task : SessionTask) {
        log.debug("Initializing GoalOrientedMode")
        goalTree.clear()
        taskMap.clear()
        goalTasks.clear()
        taskTasks.clear()
        goalIdCounter.set(1)
        taskIdCounter.set(1)
        stopRequested.set(false)
        transcriptStream?.close()
        transcriptStream = null
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        processor = FixedConcurrencyProcessor(task.ui.pool, maxConcurrency)
        log.debug("Handling user message: $userMessage")
        if (isRunning.getAndSet(true)) {
            task.add("Goal-Oriented Mode is already running. Please wait for the current session to complete or stop it.".renderMarkdown())
            return
        }
        stopRequested.set(false)
        try {
            startGoalOrientedSession(userMessage, task)
        } catch (e: Throwable) {
            log.error("Error in Goal-Oriented session", e)
            task.error(e)
        } finally {
            isRunning.set(false)
        }
    }

    private fun startGoalOrientedSession(userMessage: String, task: SessionTask) {
        task.echo("User: $userMessage".renderMarkdown())
        // Initialize transcript
        transcriptStream = task.transcript()
        logToSession("# Goal-Oriented Planning Session Transcript\n")
        logToSession("**User Request:** $userMessage\n")
        logToSession("**Started:** ${java.time.LocalDateTime.now()}\n\n")


        val stopLinkRef = AtomicReference<StringBuilder>()
        val stopLink = task.add(task.ui.hrefLink("Stop Goal-Oriented Processing") {
            log.info("Stop requested by user.")
            stopRequested.set(true)
            stopLinkRef.get()?.set("Stop signal sent. Waiting for current iteration to finish...")
        })
        stopLinkRef.set(stopLink)

        val tabs = TabbedDisplay(task)
        tabs["Goal Tree"] = "Loading...".renderMarkdown()
        tabs["Goals"] = "No goals yet.".renderMarkdown()
        tabs["Tasks"] = "No tasks yet.".renderMarkdown()
        tabs["Session Log"] = "Session started...".renderMarkdown()

        updateGoalTreeUI = {
            tabs["Goal Tree"] = renderGoalTreeText(goalTree.values.toList()).renderMarkdown()
            tabs["Goals"] = goalSummary().renderMarkdown()
            tabs["Tasks"] = taskSummary().renderMarkdown()
        }
        updateLogUI = {
            tabs["Session Log"] = sessionLog.toString().renderMarkdown()
        }

        val scheduledExecutorService = ApplicationServices.threadPoolManager.getScheduledPool(
            session = session,
            user = user
        )
        debouncedUpdateGoalTreeUI = createDebouncedUpdate(scheduledExecutorService, updateGoalTreeUI, 500)
        periodicUpdateFuture = scheduledExecutorService.scheduleWithFixedDelay({
            if (!stopRequested.get() && isRunning.get()) {
                debouncedUpdateGoalTreeUI()
            }
        }, 15, 15, TimeUnit.SECONDS)


        logToSession("Starting Goal-Oriented session for: $userMessage")
        val coordinator = TaskOrchestrator(
          user = user,
          session = session,
          dataStorage = task.ui.dataStorage
              ?: throw IllegalStateException("SocketManager or its dataStorage is null"),
          root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
              ?: task.ui.dataStorage?.getSessionDir(
                  user,
                  session
              )?.toPath() ?: File(".").toPath())
        val planningChatter = orchestrationConfig.defaultSmart.getChildClient(task)

        try {
            var loaded = false
            if (userMessage.trim().equals("resume", ignoreCase = true)) {
                loaded = loadState(task)
                if (loaded) {
                    logToSession("Resumed previous session state.")
                } else {
                    logToSession("No saved state found to resume. Starting new session.")
                }
            }

            if (!loaded) {
                val initialGoals = parseInitialGoals(userMessage, planningChatter)
                if (initialGoals.isEmpty()) {
                    logToSession("No initial goals parsed. Aborting.")
                    task.complete("Could not determine initial goals from your request.".renderMarkdown())
                    throw IllegalStateException("No initial goals parsed")
                }
                initialGoals.forEach { goal -> goalTree[goal.id] = goal }
                logToSession("Parsed ${initialGoals.size} initial goal(s).")
            }
        } catch (e: Exception) {
            log.error("Failed to parse initial goals", e)
            logToSession("Error parsing initial goals: ${e.message}")
            task.error(e)
            throw e
        }
        updateGoalTreeUI()

        var iteration = 0
        while (iteration < maxIterations && !stopRequested.get()) {
            if (stopRequested.get()) break
            iteration++
            if (nextIteration(task, iteration, coordinator, planningChatter)) break
            saveState(task)
        }

        // Cancel periodic updates and do final update
        periodicUpdateFuture?.cancel(false)
        periodicUpdateFuture = null
        updateGoalTreeUI() // Final update without debouncing

        handleStop(iteration, task, stopLink)
        updateLogUI()
        // Finalize transcript
        logToSession("\n---\n")
        logToSession("**Completed:** ${java.time.LocalDateTime.now()}")
        logToSession("\n## Final Statistics")
        logToSession("- Total Goals: ${goalTree.size}")
        logToSession("- Total Tasks: ${taskMap.size}")
        logToSession("- Iterations: $iteration")
        transcriptStream?.close()
        transcriptStream = null
    }

    private fun nextIteration(
        task : SessionTask,
        iteration: Int,
        coordinator: TaskOrchestrator,
        planningChatInterface: ChatInterface
    ): Boolean {
        logToSession("\n## Iteration $iteration / $maxIterations")
        updateGoalTreeUI()
        updateAllStatuses()
        val decomposableGoals = goalTree.values.filter {
            it.status == GoalStatus.ACTIVE && it.decompositionAttempted != true
        }

        if (decomposableGoals.isNotEmpty()) {
            logToSession("Found ${decomposableGoals.size} goal(s) to decompose:")
            decomposableGoals.forEach { logToSession("- Goal ID ${it.id}: ${it.description}") }
        }

        for (goal in decomposableGoals) {
            if (stopRequested.get()) break
            expandGoal(task, goal, coordinator, planningChatInterface)
            updateGoalTreeUI()
        }

        if (stopRequested.get()) return true

        updateAllStatuses()

        val executableTasks = taskMap.values.filter { it.status == TaskStatus.PENDING }

        if (executableTasks.isNotEmpty()) {
            logToSession("Found ${executableTasks.size} task(s) to execute:")

            executableTasks.forEach { logToSession("- Task ID ${it.id}: ${it.description}") }

            val taskExecutionJobs = mutableListOf<Pair<Task, Future<String?>>>()
            executableTasks.forEach { t ->
                if (stopRequested.get()) return@forEach
                t.status = TaskStatus.RUNNING
                logToSession("Executing Task ID ${t.id} (${t.description})")
                debouncedUpdateGoalTreeUI() // Update UI when task starts running

                log.info("Submitting Task ID ${t.id} (${t.description}) to processor.")
                val executionUiTask = task.newTask()
                taskTasks[t.id] = executionUiTask
                val future = processor.submit {
                    executeTask(
                        t.id, t, executionUiTask, coordinator, this@HierarchicalPlanningMode.getParsedActor(
                            t, planningChatInterface
                        )
                    )
                }
                taskExecutionJobs.add(Pair(t, future))
            }
            awaitAll(taskExecutionJobs)
        } else {
            logToSession("No executable tasks in this iteration.")
        }

        updateAllStatuses()
        debouncedUpdateGoalTreeUI()

        val activeGoalsCount = goalTree.values.count { it.status == GoalStatus.ACTIVE }
        val pendingOrRunningTasksCount =
            taskMap.values.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING }

        if (activeGoalsCount == 0 && pendingOrRunningTasksCount == 0) {
            val allDoneOrBlocked =
                goalTree.values.all { it.status == GoalStatus.COMPLETED || it.status == GoalStatus.BLOCKED } && taskMap.values.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
            if (allDoneOrBlocked) {
                logToSession("All goals are completed or blocked. No pending/running tasks.")
                return true
            }
        }
        if (decomposableGoals.isEmpty() && executableTasks.isEmpty() && (activeGoalsCount > 0 || pendingOrRunningTasksCount > 0)) {
            logToSession("Stalled: No goals decomposed and no tasks executed, but active goals or pending/running tasks remain. Check for dependency cycles or unresolvable goals.")
        }
        return false
    }

    private fun expandGoal(
        task : SessionTask,
        goal: Goal,
        coordinator: TaskOrchestrator,
        planningChatInterface: ChatInterface
    ) {
        logToSession("Decomposing goal: ${goal.description} (ID: ${goal.id})")
        logToSession("\n### Goal Decomposition: ${goal.id}\n")
        // Create a goal tab for this goal
        val task = task.newTask()
        goalTasks[goal.id] = task
        task.add("# Goal: ${goal.description}\n\nID: ${goal.id}".renderMarkdown())

        try {
            val inputMessages = mutableListOf(goal.description ?: "")
            inputMessages.addAll(contextData(goal.id, null))
            val goalDecomposition = getGoalParser(
                goal, coordinator, planningChatInterface
            ).answer(inputMessages).obj
            val subgoals = goalDecomposition.subgoals?.map { sg ->
                sg.copy(id = sg.id.takeIf { it.isNotBlank() } ?: "G${goalIdCounter.getAndIncrement()}",
                    description = sg.description,
                    status = sg.status
                        ?: (if (sg.dependencies?.isEmpty() != false) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT),
                    parentGoalId = goal.id,
                    subgoals = sg.subgoals ?: mutableListOf(),
                    tasks = sg.tasks ?: mutableListOf(),
                    dependencies = sg.dependencies ?: mutableListOf(),
                    decompositionAttempted = sg.decompositionAttempted ?: false,
                    result = sg.result)
            } ?: emptyList()
            val tasksForGoal = goalDecomposition.tasks?.map { t ->
                val actualParentGoalId = t.parentGoalId ?: goal.id
                t.copy(id = t.id.takeIf { it.isNotBlank() } ?: "T${taskIdCounter.getAndIncrement()}",
                    description = t.description,
                    status = t.status
                        ?: (if (t.dependencies?.isEmpty() != false) TaskStatus.PENDING else TaskStatus.ACTIVE_DEPENDENCY_WAIT),
                    parentGoalId = actualParentGoalId,
                    dependencies = t.dependencies ?: mutableListOf(),
                    result = t.result)
            } ?: emptyList()
            goal.decompositionAttempted = true
            if (subgoals.isEmpty() && tasksForGoal.isEmpty()) {
                logToSession("Goal ID ${goal.id} (${goal.description}) decomposed into no subgoals or tasks.")
                task.add("No subgoals or tasks were generated for this goal.".renderMarkdown())
                // Mark the goal as complete if it was decomposed but produced no new work
                goal.status = GoalStatus.COMPLETED
                goal.result = "Goal decomposition complete - no further actions needed."
                updateGoalTreeUI()

            } else {
                val subgoalsList = StringBuilder("## Subgoals:\n")
                val tasksList = StringBuilder("## Tasks:\n")
                logToSession("\n#### Generated Subgoals and Tasks for Goal ${goal.id}:")

                subgoals.forEach { subgoal ->
                    if (!goalTree.containsKey(subgoal.id)) {
                        goalTree[subgoal.id] = subgoal
                        logToSession("  New subgoal: ${subgoal.description} (ID: ${subgoal.id}) for Goal ${goal.id}")
                        subgoalsList.append(
                            "- ${subgoal.description} (ID: ${
                                subgoal.id.let {
                                    goalTasks[subgoal.id]?.ui?.linkToSession(
                                        it
                                    ) ?: it
                                }
                            }})\n")
                        debouncedUpdateGoalTreeUI()
                    } else {
                        logToSession("  Subgoal ID ${subgoal.id} already exists. Skipping addition.")
                        // Still add the existing subgoal to the parent's subgoal list
                        subgoalsList.append(
                            "- ${subgoal.description} (ID: ${
                                subgoal.id.let {
                                    goalTasks[subgoal.id]?.ui?.linkToSession(
                                        it
                                    ) ?: it
                                }
                            }}) [Already exists]\n")
                    }
                    if (goal.subgoals?.any { subgoal.id == it.id } != true) {
                        goal.subgoals?.add(subgoal)
                    }
                }
                tasksForGoal.forEach { t ->
                    if (!taskMap.containsKey(t.id)) {
                        taskMap[t.id] = t
                        logToSession("  New task: ${t.description} (ID: ${t.id}) for Goal ${goal.id}")
                        tasksList.append(
                            "- ${t.description} (ID: ${
                                t.id.let {
                                    goalTasks[t.id]?.ui?.linkToSession(
                                        it
                                    ) ?: it
                                }
                            })\n")
                        debouncedUpdateGoalTreeUI()
                    } else {
                        logToSession("  Task ID ${t.id} already exists. Skipping addition.")
                    }
                }
                // Add tasks to appropriate goals based on parentGoalId
                tasksForGoal.forEach { t ->
                    val targetGoalId = t.parentGoalId ?: goal.id
                    val targetGoal = if (targetGoalId == goal.id) goal else goalTree[targetGoalId]
                    if (targetGoal != null && targetGoal.tasks?.any { t.id == it.id } != true) {
                        targetGoal.tasks?.add(t)
                    }
                }

                if (subgoals.isNotEmpty()) {
                    task.add(subgoalsList.toString().renderMarkdown())
                }
                if (tasksForGoal.isNotEmpty()) {
                    task.add(tasksList.toString().renderMarkdown())
                }
            }
        } catch (e: Exception) {
            log.error("Error decomposing goal ${goal.id}", e)
            logToSession("Error decomposing goal ${goal.id}: ${e.message}. Marking as BLOCKED.")
            task.add("**ERROR:** Failed to decompose goal: ${e.message}".renderMarkdown())
            goal.status = GoalStatus.BLOCKED
            goal.result = "Failed to decompose: ${e.message}"
            debouncedUpdateGoalTreeUI()
        }
    }

    private fun goalSummary(
    ): String {
        val goalsSummary = StringBuilder()
        goalTree.values.sortedBy { it.id }.forEach { goal ->
            val statusEmoji = when (goal.status) {
                GoalStatus.ACTIVE -> "🟢"
                GoalStatus.BLOCKED -> "🧱"
                GoalStatus.COMPLETED -> "✅"
                GoalStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳"
                GoalStatus.SKIPPED -> "⏭️"
                null -> "❓"
            }
            val goalLink = goalTasks[goal.id]?.ui?.linkToSession(goal.id) ?: goal.id
            goalsSummary.append("$statusEmoji **$goalLink**: ${goal.description}\n")
            if (goal.parentGoalId != null) {
                val parentGoal = goalTree[goal.parentGoalId]
                val parentLink = goalTasks[goal.parentGoalId]?.ui?.linkToSession(goal.parentGoalId) ?: goal.parentGoalId
                goalsSummary.append("  - Parent: $parentLink - ${parentGoal?.description ?: "Unknown"}\n")
            }
            if (!goal.subgoals.isNullOrEmpty()) {
                val subgoalLinks = goal.subgoals.joinToString(", ") { subgoalId ->
                    goalTasks[subgoalId.id]?.ui?.linkToSession(subgoalId.id) ?: subgoalId.id
                }
                goalsSummary.append("  - Subgoals: $subgoalLinks\n")
            }
            if (!goal.tasks.isNullOrEmpty()) {
                val taskLinks = goal.tasks.joinToString(", ") { taskId ->
                    taskTasks[taskId.id]?.ui?.linkToSession(taskId.id) ?: taskId.id
                }
                goalsSummary.append("  - Tasks: $taskLinks\n")
            }
            if (!goal.dependencies.isNullOrEmpty()) {
                val depLinks = goal.dependencies.joinToString(", ") { depId ->
                    goalTasks[depId]?.ui?.linkToSession(depId) ?: depId
                }
                goalsSummary.append("  - Dependencies: $depLinks\n")
            }
            if (goal.result != null) {
                goalsSummary.append("  - Result: ${goal.result?.take(100)?.replace("\n", " ")}...\n")
            }
        }
        val goalSummary = goalsSummary.toString()
        return goalSummary
    }

    private fun taskSummary(
    ): String {
        val tasksSummary = StringBuilder()
        taskMap.values.sortedBy { it.id }.forEach { task ->
            val statusEmoji = when (task.status) {
                TaskStatus.PENDING -> "📝"
                TaskStatus.RUNNING -> "🏃"
                TaskStatus.COMPLETED -> "✔️"
                TaskStatus.FAILED -> "❌"
                TaskStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳"
                TaskStatus.SKIPPED -> "⏭️"
                null -> "❓"
            }
            val taskLink = taskTasks[task.id]?.ui?.linkToSession(task.id) ?: task.id
            tasksSummary.append("$statusEmoji **$taskLink**: ${task.description}\n")
            if (task.parentGoalId != null) {
                val parentGoal = goalTree[task.parentGoalId]
                val parentLink = goalTasks[task.parentGoalId]?.ui?.linkToSession(task.parentGoalId)
                tasksSummary.append("  - Parent Goal: $parentLink - ${parentGoal?.description ?: "Unknown"}\n")
            }
            if (!task.dependencies.isNullOrEmpty()) {
                val depLinks = task.dependencies.joinToString(", ") { depId ->
                    val depGoal = goalTree[depId]
                    val depTask = taskMap[depId]
                    when {
                        depGoal != null -> goalTasks[task.parentGoalId]?.ui?.linkToSession(depId)
                            ?: "Unknown ${depId}"

                        depTask != null -> taskTasks[depId]?.ui?.linkToSession(depId) ?: depId
                        else -> "Unknown ${depId}"
                    }
                }
                tasksSummary.append("  - Dependencies: $depLinks\n")
            }
            if (task.result != null) {
                tasksSummary.append("  - Result: ${task.result?.take(100)?.replace("\n", " ")}...\n")
            }
        }
        return tasksSummary.toString()
    }

    private fun handleStop(
        iteration: Int,
        task: SessionTask,
        stopLink: StringBuilder?
    ) {
        if (stopRequested.get()) {
            logToSession("Goal-Oriented session stopped by user request at iteration $iteration.")
            task.complete("Session stopped by user.".renderMarkdown())
            stopLink?.set("Stopped")
        } else if (iteration >= maxIterations) {
            logToSession("Goal-Oriented session reached max iterations ($maxIterations).")
            task.complete("Session reached max iterations.".renderMarkdown())
            stopLink?.set("Max Iterations Reached")
        } else {
            val finalStatusSummary = goalTree.values.groupBy { it.status }.mapValues { it.value.size }.toString()
            logToSession("Goal-Oriented session completed. Final status: $finalStatusSummary")
            task.complete("Session completed. Final Status: $finalStatusSummary".renderMarkdown())
            stopLink?.set("Completed")
        }
    }

    private fun executeTask(
        id: String, t: Task, task: SessionTask, coordinator: TaskOrchestrator, actor: ParsedAgent<Tasks>
    ): String? {
        return try {
            log.info("Started execution of Task ID ${id} (${t.description}) in processor.")
            logToSession("\n### Task Execution: ${t.id}\n")
            logToSession("**Description:** ${t.description}")
            logToSession("**Status:** ${t.status}")
            task.add("Starting execution of task: ${t.description}".renderMarkdown())
            task.verbose("Task Details:\n```json\n${t.toJson()}\n```\n".renderMarkdown())
            val answer = actor.answer(
                listOf(t.description ?: "") + contextData(
                    t.parentGoalId, t.id
                ), // Pass focused context
            ).obj
            val planTask = answer.tasks?.firstOrNull()
            logToSession("Resolved task for Task ID ${t.id}\n```json\n${planTask?.toJson() ?: "None"}\n```\n")
            if (planTask == null) {
                logToSession("No task implementation generated for Task ID ${t.id}")
                t.status = TaskStatus.FAILED
                t.result = "Failed to generate task implementation"
                task.add("Failed to generate task implementation".renderMarkdown())
                return t.result
            }
            val semaphore = Semaphore(0)
            val taskImpl = orchestrationConfig.getImpl(planTask = planTask)
            taskImpl.run(
                agent = coordinator,
                messages = listOf(t.description ?: "") + contextData(),
                task = task,
                resultFn = {
                    logToSession("Completed task for Task ID ${t.id}")
                    t.result = it
                    t.status = TaskStatus.COMPLETED
                    semaphore.release()
                }, // Capture task output
                orchestrationConfig = orchestrationConfig,
            )
            logToSession("Waiting for task completion for Task ID ${t.id}...")
            val acquired = semaphore.tryAcquire(20, TimeUnit.MINUTES)
            if (!acquired) {
                logToSession("Task ID ${t.id} timed out after 20 minutes")
                logToSession("**Result:** TIMEOUT")
                t.status = TaskStatus.FAILED
                t.result = "Task execution timed out"
                task.add("Task execution timed out after 20 minutes".renderMarkdown())
            }
            logToSession("Task ID ${t.id} complete")
            val result = t.result
            logToSession(
                "**Result:** ${
                    result?.take(200)?.replace("\n", " ")
                }${if ((result?.length ?: 0) > 200) "..." else ""}"
            )
            log.info("Completed execution of Task ID ${id} (${t.description}) in processor.")
            result
        } catch (e: Exception) {
            log.error(
                "Task ID ${id} (${t.description}) execution failed in processor.submit lambda", e
            )
            logToSession("**Result:** FAILED - ${e.message}")
            taskMap[id]?.apply {
                status = TaskStatus.FAILED
                result = "Execution Error: ${e.message}"
            }
            task.add("Task execution failed: ${e.message}".renderMarkdown())
            "Task execution failed: ${e.message}"
        }
    }


    private fun getParsedActor(
        task: Task, chatInterface: ChatInterface
    ): ParsedAgent<Tasks> {
        val availableTaskTypes = TaskType.getAvailableTaskTypes(orchestrationConfig)
        Tasks.initDescriber(orchestrationConfig, describer)
        return ParsedAgent(
            name = "TaskTypeChooser",
            resultClass = Tasks::class.java, // Parse directly into TaskConfigBase
            exampleInstance = Tasks(
                mutableListOf(TaskType.getAvailableTaskTypes(orchestrationConfig).firstOrNull()?.let {
                    orchestrationConfig.getImpl(it).executionConfig
                }).filterNotNull().toMutableList()
            ),
            prompt = """
                        Given the following task description and context, choose the single most appropriate task type and provide all required details.
                        Task Description: ${task.description}
                        Available task types (and their schemas):
                        ${availableTaskTypes.joinToString("\n") { it.name }}
                    """.trimIndent(),
            model = chatInterface,
            parsingChatter = orchestrationConfig.defaultFast,
            temperature = orchestrationConfig.temperature,
            describer = describer,
            parserPrompt = ("Task Subtype Schema:\n" + availableTaskTypes.joinToString("\n\n") { taskType ->
                "${taskType.name}:\n  ${
                    describer.describe(taskType.executionConfigClass).trim().trimIndent()
                        .indent("  ")
                }".trim()
            })
        )
    }

    private fun awaitAll(taskExecutionJobs: MutableList<Pair<Task, Future<String?>>>) {
        for ((taskInstance, future) in taskExecutionJobs) {
            if (stopRequested.get()) {
                logToSession("Stop requested, not waiting for all tasks to complete this iteration.")
                break
            }
            try {
                if (taskInstance.status != TaskStatus.FAILED) {
                    var waitCount = 0
                    while (!future.isDone) {
                        if (stopRequested.get()) break
                        if (future.isCancelled) {
                            logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) was cancelled.")
                            taskInstance.status = TaskStatus.FAILED
                            taskInstance.result = "Task was cancelled."
                            debouncedUpdateGoalTreeUI()
                            break
                        }
                        if (processor.getActiveTaskCount() == 0 && waitCount > 0) {
                            log.warn("No active tasks in processor but future not done for Task ID ${taskInstance.id}. Possible deadlock.")
                            // Give it one more chance with a shorter timeout
                            try {
                                taskInstance.result = future.get(30, TimeUnit.SECONDS)
                                taskInstance.status = TaskStatus.COMPLETED
                                logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) COMPLETED after wait.")
                            } catch (te: TimeoutException) {
                                log.error("Task ID ${taskInstance.id} appears to be stuck. Marking as failed.")
                                taskInstance.status = TaskStatus.FAILED
                                taskInstance.result = "Task execution appears stuck - no progress detected"
                                future.cancel(true)
                            }
                            break
                        }
                        waitCount++
                        log.info("Waiting for Task ID ${taskInstance.id} (${taskInstance.description}) to complete. Currently ${processor.getActiveTaskCount()} active tasks. Wait cycle: $waitCount")
                        // Wait for 5 seconds, but check stopRequested every 100ms
                        for (i in 0 until 50) {
                            if (stopRequested.get()) break
                            Thread.sleep(100)
                        }
                    }
                    if (future.isDone && taskInstance.status != TaskStatus.FAILED) {
                        taskInstance.status = TaskStatus.COMPLETED
                        taskInstance.result = future.get()
                        logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) COMPLETED.")
                        debouncedUpdateGoalTreeUI()
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("Task ID ${taskInstance.id} (${taskInstance.description}) interrupted.", e)
                taskInstance.status = TaskStatus.FAILED
                taskInstance.result = "Task execution was interrupted."
                logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) INTERRUPTED.")
                debouncedUpdateGoalTreeUI()
            } catch (e: Exception) {
                val cause = if (e is ExecutionException) e.cause ?: e else e
                log.error(
                    "Task ID ${taskInstance.id} (${taskInstance.description}) failed or error retrieving result.", cause
                )
                if (taskInstance.status != TaskStatus.FAILED) {
                    taskInstance.status = TaskStatus.FAILED
                    taskInstance.result = "Execution Error: ${cause.message}"
                }
                logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) FAILED. Reason: ${taskInstance.result}")
                debouncedUpdateGoalTreeUI()
            }
            // Ensure UI is updated after each task completion
            debouncedUpdateGoalTreeUI()
        }
    }

    private fun parseInitialGoals(
        userMessage: String, chatInterface: ChatInterface
    ): List<Goal> {
        val parsedActor = ParsedAgent(
            name = "InitialGoalParser",
            resultClass = GoalList::class.java,
            exampleInstance = GoalList(
                goals = listOf(
                    Goal(
                        id = "G1",
                        description = "Implement a file upload feature",
                        parentGoalId = null,
                        subgoals = mutableListOf(),
                        tasks = mutableListOf(),
                        dependencies = mutableListOf()
                    )
                )
            ),
            prompt = """
                Given the following user objective, extract one or more high-level goals.
                Each goal should be a clear, actionable objective.
                Return a list of goal objects with unique IDs and descriptions.
            """.trimIndent(),
            model = chatInterface,
            parsingChatter = orchestrationConfig.defaultFast,
            temperature = orchestrationConfig.temperature,
            describer = describer
        )
        val answer = parsedActor.answer(listOf(userMessage))
        val goals = answer.obj.goals ?: emptyList()
        if (goals.isEmpty()) {
            return listOf(
                Goal(
                    id = "G${goalIdCounter.getAndIncrement()}",
                    description = userMessage,
                    status = GoalStatus.ACTIVE,
                    parentGoalId = null,
                    subgoals = mutableListOf(),
                    tasks = mutableListOf(),
                    dependencies = mutableListOf(),
                    decompositionAttempted = false,
                    result = null
                )
            )
        }

        return goals.map { g ->
            g.copy(id = g.id?.takeIf { it.isNotBlank() } ?: "G${goalIdCounter.getAndIncrement()}",
                description = g.description,
                status = g.status
                    ?: (if (g.dependencies?.isEmpty() != false) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT),
                parentGoalId = g.parentGoalId,
                subgoals = g.subgoals ?: mutableListOf(),
                tasks = g.tasks ?: mutableListOf(),
                dependencies = g.dependencies ?: mutableListOf(),
                decompositionAttempted = g.decompositionAttempted ?: false,
                result = g.result)
        }
    }


    private fun getGoalParser(
        goal: Goal, coordinator: TaskOrchestrator, chatInterface: ChatInterface
    ): ParsedAgent<GoalDecomposition> = ParsedAgent(
        name = "GoalDecomposer",
        resultClass = GoalDecomposition::class.java,
        exampleInstance = GoalDecomposition( // Example should match the structure and intent
            subgoals = listOf(
                Goal(
                    id = "G2",
                    description = "Design API endpoint",
                    parentGoalId = goal.id,
                    subgoals = mutableListOf(),
                    tasks = mutableListOf(),
                    dependencies = mutableListOf()
                )
            ), tasks = listOf(
                Task(
                    id = "T1",
                    description = "Draft OpenAPI spec for upload endpoint",
                    parentGoalId = goal.id,
                    dependencies = mutableListOf()
                )
            )
        ),
        prompt = run {
            val availableTaskTypes = TaskType.getAvailableTaskTypes(orchestrationConfig)
                .joinToString("\n                ") { "- ${it.name}" }
            val relatedTasksContext = goal.tasks?.mapNotNull { taskMap[it.id] }
                ?.filter { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
                ?.takeIf { it.isNotEmpty() }?.joinToString("\n                ") {
                    "  - Task ${it.id} (${it.description?.take(50)}...): ${it.status}"
                }?.indent("  ") // Indent the context block
            var promptStr = """
                    Given the following goal, decide whether it can be directly addressed by a task, or if it should be broken down into subgoals.
                    If the goal is sufficiently concrete, identify the next executable task(s) for this goal.
                    If the goal is still abstract or complex, identify subgoals that, when completed, will achieve the parent goal.
                    For each subgoal and task, list any *external* prerequisite goal or task IDs in their 'dependencies' list. Do not list the parent goal ID as a dependency.
                    Important: Tasks should be assigned to exactly one goal - either the parent goal OR one of its subgoals, but not both.
                    If you create subgoals, assign tasks to the most specific relevant subgoal rather than the parent goal.
                    Return a list of subgoals and/or tasks.
                    Goal: ${goal.description ?: "N/A"}
                    (ID: ${goal.id})
                    Available task types for direct execution:
                    $availableTaskTypes
                """.trimIndent()
            if (!relatedTasksContext.isNullOrBlank()) {
                promptStr += "\nConsider the following results from previously attempted tasks for this goal:\n$relatedTasksContext"
            }
            promptStr
        },
        model = chatInterface,
        parsingChatter = orchestrationConfig.defaultFast,
        temperature = orchestrationConfig.temperature,
        describer = describer
    )


    private fun updateAllStatuses() {
        var changed: Boolean = false
        val circularDependencies = detectCircularDependencies()
        if (circularDependencies.isNotEmpty()) {
            logToSession("Circular dependencies detected. Marking affected items as SKIPPED to break deadlock.")
            circularDependencies.forEach { id ->
                when {
                    goalTree.containsKey(id) -> {
                        val goal = goalTree[id]!!
                        if (goal.status != GoalStatus.COMPLETED && goal.status != GoalStatus.BLOCKED && goal.status != GoalStatus.SKIPPED) {
                            goal.status = GoalStatus.SKIPPED
                            goal.result = "Skipped due to circular dependency deadlock"
                            logToSession("Goal ${goal.id} (${goal.description}) marked as SKIPPED due to circular dependency")
                            changed = true
                        }
                    }

                    taskMap.containsKey(id) -> {
                        val task = taskMap[id]!!
                        if (task.status != TaskStatus.COMPLETED && task.status != TaskStatus.FAILED && task.status != TaskStatus.SKIPPED) {
                            task.status = TaskStatus.SKIPPED
                            task.result = "Skipped due to circular dependency deadlock"
                            logToSession("Task ${task.id} (${task.description}) marked as SKIPPED due to circular dependency")
                            changed = true
                        }
                    }
                }
            }
            if (changed) {
                debouncedUpdateGoalTreeUI()
            }
        }

        do {
            val initialTaskStatuses = taskMap.mapValues { it.value.status }
            val initialGoalStatuses = goalTree.mapValues { it.value.status }
            changed = false
            taskMap.values.forEach { task ->
                val status = task.status
                if (status != TaskStatus.COMPLETED && status != TaskStatus.FAILED && status != TaskStatus.RUNNING && status != TaskStatus.SKIPPED) {
                    val newStatus = if (areDependenciesMet(task)) {
                        TaskStatus.PENDING // Dependencies met, ready to run
                    } else {
                        TaskStatus.ACTIVE_DEPENDENCY_WAIT // Waiting for dependencies
                    }
                    if (status != newStatus) {
                        task.status = newStatus
                        debouncedUpdateGoalTreeUI()
                        changed = true
                    }
                }
            }

            goalTree.values.forEach { goal ->
                var newStatus = goal.status

                if (goal.status != GoalStatus.COMPLETED && goal.status != GoalStatus.BLOCKED && goal.status != GoalStatus.SKIPPED) {
                    val dependenciesMet = areDependenciesMet(goal)
                    val subGoals = goal.subgoals?.mapNotNull { goalTree[it.id] }
                    val directTasks = goal.tasks?.mapNotNull { taskMap[it.id] }

                    val blockingDependency =
                        goal.dependencies?.firstOrNull { depId -> goalTree[depId]?.status == GoalStatus.BLOCKED }
                    val blockingSubGoal = subGoals?.firstOrNull { it.status == GoalStatus.BLOCKED }
                    if (blockingDependency != null || blockingSubGoal != null) {
                        newStatus = GoalStatus.BLOCKED
                    } else {
                        val failedTask = directTasks?.firstOrNull { it.status == TaskStatus.FAILED }
                        if (dependenciesMet && failedTask != null) {
                            newStatus = GoalStatus.BLOCKED // Blocked by a failed/blocked child
                        } else {
                            goal.result = goal.result
                                ?: "Blocked because task ID: ${failedTask?.id} (${failedTask?.description?.take(50) ?: "N/A"}...) is FAILED."
                        }
                    }

                    if (newStatus != GoalStatus.BLOCKED && dependenciesMet && (goal.decompositionAttempted == true || subGoals?.isNotEmpty() == true || directTasks?.isNotEmpty() == true) && (subGoals?.isEmpty() != false || subGoals.all { it.status == GoalStatus.COMPLETED || it.status == GoalStatus.SKIPPED }) && (directTasks?.isEmpty() != false || directTasks.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.SKIPPED })) {
                        newStatus = GoalStatus.COMPLETED
                        goal.result = goal.result ?: when {
                            subGoals?.isNotEmpty() == true && directTasks?.isNotEmpty() == true -> "All sub-goals and tasks completed."
                            subGoals?.isNotEmpty() == true -> "All sub-goals completed."
                            directTasks?.isNotEmpty() == true -> "All tasks completed."
                            else -> "Goal achieved."
                        }
                    } else if (newStatus != GoalStatus.BLOCKED && newStatus != GoalStatus.COMPLETED && newStatus != GoalStatus.SKIPPED && !dependenciesMet) { // Still waiting for external dependencies
                        newStatus = GoalStatus.ACTIVE_DEPENDENCY_WAIT
                    } else if (newStatus != GoalStatus.BLOCKED && newStatus != GoalStatus.COMPLETED && newStatus != GoalStatus.SKIPPED) { // Dependencies met, not blocked, not completed
                        newStatus = GoalStatus.ACTIVE
                    }

                    if (goal.status != newStatus) {
                        goal.status = newStatus
                        debouncedUpdateGoalTreeUI()
                        changed = true

                    }
                }
            }
            // Update UI after all status changes are complete
            if (goalTree.any { initialGoalStatuses[it.key] != it.value.status } || taskMap.any { initialTaskStatuses[it.key] != it.value.status }) {
                debouncedUpdateGoalTreeUI()
            }

        } while (changed)
    }

    private fun areDependenciesMet(item: Goal): Boolean {
        if (item.dependencies?.isEmpty() != false) return true
        return item.dependencies.all { depId ->
            val s = goalTree[depId]?.status
            s == GoalStatus.COMPLETED || s == GoalStatus.SKIPPED
        }
    }

    private fun areDependenciesMet(item: Task): Boolean {
        if (item.dependencies?.isEmpty() != false) return true
        return item.dependencies.all { depId ->
            val gs = goalTree[depId]?.status
            val ts = taskMap[depId]?.status
            (gs == GoalStatus.COMPLETED || gs == GoalStatus.SKIPPED) || (ts == TaskStatus.COMPLETED || ts == TaskStatus.SKIPPED)
        }
    }

    private fun detectCircularDependencies(): Set<String> {
        val circularItems = mutableSetOf<String>()
        val allItems = goalTree.keys + taskMap.keys
        fun hasCycle(id: String, visited: MutableSet<String>, recursionStack: MutableSet<String>): Boolean {
            if (recursionStack.contains(id)) {
                // Found a cycle - add all items in the recursion stack
                circularItems.addAll(recursionStack)
                return true
            }
            if (visited.contains(id)) {
                return false
            }
            visited.add(id)
            recursionStack.add(id)
            val dependencies = when {
                goalTree.containsKey(id) -> goalTree[id]?.dependencies ?: emptyList()
                taskMap.containsKey(id) -> taskMap[id]?.dependencies ?: emptyList()
                else -> emptyList()
            }
            for (depId in dependencies) {
                if (hasCycle(depId, visited, recursionStack)) {
                    return true
                }
            }
            recursionStack.remove(id)
            return false
        }

        val visited = mutableSetOf<String>()
        for (id in allItems) {
            if (!visited.contains(id)) {
                hasCycle(id, visited, mutableSetOf())
            }
        }
        return circularItems
    }


    private fun renderNode(goal: Goal, visited: MutableSet<String>): String {
        val threadVisited = renderingInProgress.get()
        if (goal.id in threadVisited) {
            // Already rendering this goal in the current call stack, return a reference to avoid infinite recursion
            return "- ⚠️ **Circular reference detected: ${goal.description ?: "N/A"} (ID: ${goal.id})**\n"
        }
        threadVisited.add(goal.id)
        try {
            val nodeSb = StringBuilder()
            val statusEmoji = when (goal.status) {
                GoalStatus.ACTIVE -> "🟢 Active"
                GoalStatus.BLOCKED -> "🧱 Blocked"
                GoalStatus.COMPLETED -> "✅ Completed"
                GoalStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳ Waiting (Deps)"
                GoalStatus.SKIPPED -> "⏭️ Skipped"
                null -> "❓ Unknown"
            }
            val depsString = if (goal.dependencies.isNullOrEmpty()) "" else "Deps: " + goal.dependencies.joinToString(", ") { "Goal $it" }

            nodeSb.append("- " + ("""$statusEmoji **${goal.description ?: "N/A"} (ID: ${goal.id})**""").let {
                goalTasks[goal.id]?.ui?.linkToSession(
                    it
                ) ?: it
            } + (if (depsString.isNotEmpty()) "   $depsString" else ""))
            nodeSb.append("\n")
            goal.tasks?.mapNotNull { taskMap[it.id] }?.forEach { t ->
                val taskStatusEmoji = when (t.status) {
                    TaskStatus.PENDING -> "📝 Pending"
                    TaskStatus.RUNNING -> "🏃 Running"
                    TaskStatus.COMPLETED -> "✔️ Completed"
                    TaskStatus.FAILED -> "❌ Failed"
                    TaskStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳ Waiting (Deps)"
                    TaskStatus.SKIPPED -> "⏭️ Skipped"
                    null -> "❓ Unknown"
                }
                val string =
                    if (t.dependencies?.isEmpty() == true) "none" else t.dependencies?.joinToString(", ") { dep ->
                        idToString(dep)
                    }
                val text = "Task $taskStatusEmoji ${t.description ?: "N/A"} (ID: ${t.id})"
                nodeSb.append(
                    "  - ${taskTasks[t.id]?.ui?.linkToSession(text) ?: text}" + "    " + when (string) {
                        "" -> ""
                        null -> ""
                        else -> "Deps: $string"
                    }
                )
                nodeSb.append("\n")
            }
            goal.subgoals?.mapNotNull { goalTree[it.id] }?.joinToString("\n") { subGoal ->
                renderNode(subGoal, visited).trim().indent("  ")
            }.apply { nodeSb.append(this + "\n") }
            return nodeSb.toString()
        } finally {
            // Remove from thread-local set when done rendering this node to allow it to be rendered in other branches
            threadVisited.remove(goal.id)
        }
    }

    private fun idToString(dep: String): CharSequence =
        if (goalTree.containsKey(dep)) "Goal ${goalTasks.get(dep)?.ui?.linkToSession(dep) ?: dep}"
        else "Task ${taskTasks.get(dep)?.ui?.linkToSession(dep) ?: dep}"

    private fun renderGoalTreeText(goals: List<Goal>): String {

        val sb = StringBuilder("### Goal Tree Status\n")
        val rootGoalIds = goals.map { it.id }.toSet()
        val roots =
            goals.filter { it.parentGoalId == null || !rootGoalIds.contains(it.parentGoalId) }.sortedBy { it.id }
        if (roots.isEmpty() && goals.isNotEmpty()) {
            goals.sortedBy { it.id }.forEach {
                sb.append(
                    renderNode(
                        it, mutableSetOf()
                    )
                )
            }
        } else {
            roots.sortedBy { it.id }
                .forEach { sb.append(renderNode(it, mutableSetOf())) }
        }
        return sb.toString()
    }

    override fun contextData(): List<String> {
        val contextLines = mutableListOf<String>()
        contextLines.add("Current Goal-Oriented Plan State:")
        val llmContextSb = StringBuilder()
        fun renderNodeForLlm(goal: Goal, indent: Int, visited: MutableSet<String>) {
            val goalDeps = goal.dependencies?.joinToString(",").let {
                when (it) {
                    "" -> ""
                    else -> "(Deps: $it)"
                }
            }
            llmContextSb.append("${"  ".repeat(indent)}- G(${goal.id}): ${goal.description ?: "N/A"} [${goal.status}] $goalDeps\n")
            goal.tasks?.mapNotNull { taskMap[it.id] }?.forEach { t ->
                val taskDeps = t.dependencies?.joinToString(",").let {
                    when (it) {
                        "" -> ""
                        else -> "(Deps: $it)"
                    }
                }
                llmContextSb.append("${"  ".repeat(indent + 1)}- T(${t.id}): ${t.description ?: "N/A"} [${t.status}] $taskDeps\n")
            }
            goal.subgoals?.mapNotNull { goalTree[it.id] }?.forEach { subGoal ->
                if (visited.add(subGoal.id)) { // Prevent infinite loops in case of cycles (though cycles aren't explicitly handled)
                    renderNodeForLlm(subGoal, indent + 1, visited)
                } else {
                    llmContextSb.append("${"  ".repeat(indent + 1)}- G(${subGoal.id}): ... (cycle detected or already rendered)\n")
                }
            }
        }

        val rootsForLlm = goalTree.values.filter { it.parentGoalId == null || !goalTree.containsKey(it.parentGoalId) }
            .sortedBy { it.id } // Consider nodes without known parents as roots
        rootsForLlm.forEach { renderNodeForLlm(it, 0, mutableSetOf()) }
        contextLines.add(llmContextSb.toString())
        return contextLines
    }

    fun contextData(focusGoalId: String?, focusTaskId: String?): List<String> {
        val contextLines = mutableListOf<String>()
        contextLines.add("Current Goal-Oriented Plan State:")
        if (focusGoalId != null || focusTaskId != null) {
            val focusMsg = mutableListOf<String>()
            if (focusGoalId != null) focusMsg.add("Goal $focusGoalId")
            if (focusTaskId != null) focusMsg.add("Task $focusTaskId")
            contextLines.add("Current operational focus: ${focusMsg.joinToString(" / ")}")
        }

        val llmContextSb = StringBuilder()
        fun renderNodeForLlm(goal: Goal, indent: Int, visited: MutableSet<String>) {
            if (!visited.add(goal.id)) {
                // Already visited this goal, prevent infinite recursion
                llmContextSb.append("${"  ".repeat(indent)}- G(${goal.id}): ... (cycle detected or already rendered)\n")
                return
            }
            val goalDeps = goal.dependencies?.joinToString(",")?.let {
                when (it) {
                    "" -> ""
                    else -> "(Deps: $it)"
                }
            }
            llmContextSb.append("${"  ".repeat(indent)}- G(${goal.id}): ${goal.description ?: "N/A"} [${goal.status}] $goalDeps\n")
            goal.tasks?.mapNotNull { taskMap[it.id] }?.forEach { t ->
                val taskDeps = t.dependencies?.joinToString(",")
                // Add task result if available and relevant (e.g., for completed/failed tasks)
                val taskResultSnippet = t.result?.take(50)?.replace("\n", " ").let {
                    when (it) {
                        "" -> ""
                        else -> "(Deps: $it)"
                    }
                }
                if (taskResultSnippet.isNotBlank()) llmContextSb.append("${"  ".repeat(indent + 1)}  Result: $taskResultSnippet...\n")
                llmContextSb.append("${"  ".repeat(indent + 1)}- T(${t.id}): ${t.description ?: "N/A"} [${t.status}] $taskDeps\n")
            }
            goal.subgoals?.mapNotNull { goalTree[it.id] }?.forEach { subGoal ->
                renderNodeForLlm(subGoal, indent + 1, visited)
            }
        }

        val rootsForLlm = goalTree.values.filter { it.parentGoalId == null || !goalTree.containsKey(it.parentGoalId) }
            .sortedBy { it.id } // Consider nodes without known parents as roots
        rootsForLlm.forEach { renderNodeForLlm(it, 0, mutableSetOf()) }
        contextLines.add(llmContextSb.toString())
        return contextLines
    }

    private fun createDebouncedUpdate(
        scheduler: ScheduledExecutorService, updateFunction: () -> Unit, delayMs: Long
    ): () -> Unit {
        var debounceTask: ScheduledFuture<*>? = null
        return {
            synchronized(this) {
                // Cancel any pending update
                debounceTask?.cancel(false)
                // Schedule new update
                debounceTask = scheduler.schedule({
                    try {
                        if (!stopRequested.get() && isRunning.get()) {
                            updateFunction()
                        }
                    } catch (e: Exception) {
                        log.warn("Error in debounced UI update", e)
                    }
                }, delayMs, TimeUnit.MILLISECONDS)
            }
        }
    }


    @Description("A goal in the goal-oriented planning system.")
    data class Goal(
        val id: String = "",
        val description: String? = null,
        var status: GoalStatus? = GoalStatus.ACTIVE_DEPENDENCY_WAIT,
        val parentGoalId: String? = null,
        val subgoals: MutableList<Goal>? = mutableListOf(),
        val tasks: MutableList<Task>? = mutableListOf(),
        val dependencies: MutableList<String>? = mutableListOf(),
        var decompositionAttempted: Boolean? = false,
        var result: String? = null
    )

    @Description("A task that can be executed to achieve a goal.")
    data class Task(
        val id: String = "",
        val description: String? = null,
        var status: TaskStatus? = TaskStatus.ACTIVE_DEPENDENCY_WAIT,
        val parentGoalId: String? = null,
        var result: String? = null,
        val dependencies: MutableList<String>? = mutableListOf()
    )

    @Description("Status of a goal.")
    enum class GoalStatus {
        @Description("Goal is active and its dependencies are met. It's either being decomposed, or its sub-goals/tasks are in progress.")
        ACTIVE,

        @Description("Goal is blocked, either by a failed/blocked dependency, a failed/blocked sub-goal/task, or because decomposition yielded no actions.")
        BLOCKED,

        @Description("Goal has been successfully completed (all sub-goals and tasks are complete).")
        COMPLETED,

        @Description("Goal is waiting for its declared dependencies (other goals) to be completed.")
        ACTIVE_DEPENDENCY_WAIT,

        @Description("Goal was skipped, possibly to resolve a deadlock.")
        SKIPPED
    }

    @Description("Status of a task.")
    enum class TaskStatus {
        @Description("Task is ready to be executed, all dependencies are met.")
        PENDING,

        @Description("Task is currently being executed.")
        RUNNING,

        @Description("Task has been successfully completed.")
        COMPLETED,

        @Description("Task execution failed.")
        FAILED,

        @Description("Task is waiting for its declared dependencies (other goals or tasks) to be completed.")
        ACTIVE_DEPENDENCY_WAIT,

        @Description("Task was skipped, possibly to resolve a deadlock.")
        SKIPPED
    }

    @Description("A list of goals (for LLM parsing).")
    data class GoalList(
        val goals: List<Goal>? = null
    )

    @Description("Result of decomposing a goal into subgoals and tasks.")
    data class GoalDecomposition(
        val subgoals: List<Goal>? = null, val tasks: List<Task>? = null
    )

    data class PlanningState(
        val goalIdCounter: Int = 1,
        val taskIdCounter: Int = 1,
        val goals: List<Goal> = emptyList(),
        val tasks: List<Task> = emptyList()
    )

    private fun getStateFile(task : SessionTask) = File(task.ui.dataStorage.getSessionDir(user, session), "planning_state.json")

    private fun saveState(task : SessionTask) {
        try {
            val state = PlanningState(
                goalIdCounter = goalIdCounter.get(),
                taskIdCounter = taskIdCounter.get(),
                goals = goalTree.values.toList(),
                tasks = taskMap.values.toList()
            )
            getStateFile(task).writeText(state.toJson())
        } catch (e: Exception) {
            log.error("Failed to save state", e)
        }
    }

    private fun loadState(task : SessionTask): Boolean {
        val file = getStateFile(task)
        if (!file.exists()) return false
        try {
            val state = JsonUtil.fromJson<PlanningState>(file.readText(), PlanningState::class.java)
            goalIdCounter.set(state.goalIdCounter)
            taskIdCounter.set(state.taskIdCounter)
            goalTree.clear()
            taskMap.clear()
            state.goals.forEach { goalTree[it.id] = it }
            state.tasks.forEach { taskMap[it.id] = it }
            // Reconstruct UI mappings
            goalTree.values.forEach { goal ->
                val t = task.newTask()
                goalTasks[goal.id] = t
                t.add("# Goal: ${goal.description}\n\nID: ${goal.id}".renderMarkdown())
            }
            taskMap.values.forEach { v ->
                val t = task.newTask()
                taskTasks[v.id] = t
                t.add("# Task: ${v.description}\n\nID: ${v.id}".renderMarkdown())
            }
            updateGoalTreeUI()
            return true
        } catch (e: Exception) {
            log.error("Failed to load state", e)
            return false
        }
    }


    companion object {
        val inputCnt = 1

        private val log = LoggerFactory.getLogger(HierarchicalPlanningMode::class.java)

        // ThreadLocal to track visited nodes during rendering to prevent infinite recursion
        private val renderingInProgress = ThreadLocal.withInitial { mutableSetOf<String>() }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/ParallelMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.cognitive.ConversationalMode.Companion.requestToTask
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

class ParallelModeConfig(
    var defaultConcurrency: Int = 4,
    var defaultMode: CombinationMode = CombinationMode.CrossJoin
) : CognitiveModeConfig(type = CognitiveModeType.Parallel) {
    enum class CombinationMode {
        CrossJoin,
        Zip
    }
}

open class ParallelMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser
) : CognitiveMode<ParallelModeConfig>(
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(ParallelMode::class.java)


    override fun contextData(): List<String> = emptyList()

    data class ParallelPlan(
        val variables: Map<String, Any> = emptyMap(),
        val template: String = "",
        val concurrency: Int = 4,
        val mode: ParallelModeConfig.CombinationMode = ParallelModeConfig.CombinationMode.CrossJoin
    )

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        val transcript = task.transcript()
        try {
            task.echo(userMessage.renderMarkdown(true))

            transcript?.write("User Message: $userMessage\n".toByteArray())

            val root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                ?: task.ui.dataStorage?.getSessionDir(user, session)?.toPath()
                ?: File(".").toPath()
            val parser = createParserAgent(task)
            val plan = if (orchestrationConfig.autoFix) {
                parser.answer(listOf(userMessage)).obj
            } else {
                Discussable(
                    task = task,
                    heading = "Parallel Execution Plan",
                    userMessage = { userMessage },
                    initialResponse = { parser.answer(listOf(it)).obj },
                    outputFn = { plan ->
                        val expandedVariables = plan.variables.mapValues { (_, value) -> expandVariable(value, root) }
                        val combinations = generateCombinations(expandedVariables, plan.mode)
                        buildString {
                            append("<div>")
                            append("<b>Template:</b> <pre>${plan.template}</pre>")
                            append("<b>Concurrency:</b> ${plan.concurrency}<br/>")
                            append("<b>Mode:</b> ${plan.mode}<br/>")
                            append("<b>Tasks to run:</b> ${combinations.size}<br/>")
                            append("<details><summary>Variables</summary><pre>${JsonUtil.toJson(plan.variables)}</pre></details>")
                            append("<details><summary>Combinations (First 10)</summary><ul>")
                            combinations.take(10).forEach { append("<li>${JsonUtil.toJson(it)}</li>") }
                            append("</ul></details>")
                            append("</div>")
                        }
                    },
                    reviseResponse = { history ->
                        parser.answer(history.map { it.first }).obj
                    }
                ).call()!!
            }
            transcript?.write("Plan: ${JsonUtil.toJson(plan)}\n".toByteArray())


            val expandedVariables = plan.variables.mapValues { (_, value) -> expandVariable(value, root) }
            val combinations = generateCombinations(expandedVariables, plan.mode)

            task.header("Running ${combinations.size} tasks (Concurrency: ${plan.concurrency})", level = 3)

            val tabs = TabbedDisplay(task)
            val processor = FixedConcurrencyProcessor(task.ui.pool, plan.concurrency)

            val futures = combinations.map { combination ->
                val label = combination.values.joinToString(",") { it.toString() }
                val task = tabs.newTask(label)
                processor.submit {
                    try {
                        val renderedMessage = renderTemplate(plan.template, combination)
                        task.expandable("Parameters", "```json\n${JsonUtil.toJson(combination)}\n```".renderMarkdown())
                        task.expandable("Rendered Message", "```text\n${renderedMessage}\n```".renderMarkdown())
                        val (_, chosenTask) = requestToTask(
                            defaultModel = orchestrationConfig.defaultSmart.getChildClient(task),
                            fastModel = orchestrationConfig.defaultFast.getChildClient(task),
                            userMessage = renderedMessage,
                            orchestrationConfig = orchestrationConfig,
                            singleStage = true
                        )
                        task.expandable("Config", "```json\n${JsonUtil.toJson(chosenTask)}\n```".renderMarkdown())
                        val coordinator = TaskOrchestrator(
                          user = user,
                          session = session,
                          dataStorage = task.ui.dataStorage!!,
                          root = root
                        )
                        val impl = orchestrationConfig.getImpl(chosenTask)
                        var resultString = ""
                        impl.run(
                            agent = coordinator,
                            messages = listOf(userMessage),
                            task = task,
                            resultFn = { result ->
                                resultString = result
                                task.complete(result.renderMarkdown())
                            },
                            orchestrationConfig = orchestrationConfig
                        )
                        Result.success(resultString)
                    } catch (e: Throwable) {
                        task.error(e)
                        log.error("Error in parallel task $label", e)
                        Result.failure(e)
                    }
                }
            }

            val results = futures.map {
                try {
                    it.get() as Result<String>
                } catch (e: Exception) {
                    log.warn("Task failed", e)
                    Result.failure(e)
                }
            }
            val succeeded = results.count { it.isSuccess }
            val failed = results.count { it.isFailure }
            task.complete("All parallel tasks completed. $succeeded Succeeded, $failed Failed.")

        } catch (e: Throwable) {
            task.error(e)
            log.error("Error in ParallelMode", e)
        } finally {
            transcript?.close()
        }
    }

    private fun createParserAgent(task: SessionTask): ParsedAgent<ParallelPlan> {
        val availableTaskTypes = TaskType.getAvailableTaskTypes(orchestrationConfig)
        val taskDescriptions = availableTaskTypes.joinToString("\n") { taskType ->
            val impl = orchestrationConfig.getImpl(taskType)
            "* ${taskType.name}: ${impl.promptSegment()}"
        }

        val describer = TaskContextYamlDescriber(orchestrationConfig)
        Tasks.initDescriber(orchestrationConfig, describer)
        return ParsedAgent(
            name = "ParallelConfigParser",
            resultClass = ParallelPlan::class.java,
            exampleInstance = ParallelPlan(
                variables = mapOf("file" to listOf("src/main.kt", "src/utils.kt")),
                template = """{"task_type": "CodingTask", "prompt": "Review the code in {{file}}"}""",
                concurrency = config.defaultConcurrency,
                mode = config.defaultMode
            ),
            prompt = """
Analyze the user request to identify parallel execution parameters.
Extract variables that represent lists of items to process (e.g., files, inputs).
Construct a template string that uses these variables (e.g., "{{variableName}}") to formulate a request describing the task to be performed.

Available task types that the downstream agent can perform:
$taskDescriptions

If the user mentions specific files or globs, include them in the variables map.
If the user specifies concurrency, set it; otherwise default to ${config.defaultConcurrency}.
If the user implies pairing items (e.g. "zip", "pair", "corresponding"), set mode to Zip. Default is ${config.defaultMode}.
            """ + (orchestrationConfig.workingDir?.let { root ->
                "\nAvailable files:\n\n" + getAvailableFiles(Path(root)).joinToString("\n") { "      - $it" } + "\n"
            } ?: ""),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = 0.1,
            describer = describer
        )
    }

    private fun expandVariable(value: Any, root: Path): List<Any> {
        return when (value) {
            is String -> {
                if (value.contains("*") || value.contains("?") || (value.contains("[") && value.contains("]"))) {
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:$value")
                    val files = mutableListOf<String>()
                    if (Files.exists(root)) {
                        Files.walk(root).use { stream ->
                            stream.filter { !it.isDirectory() }
                                .forEach { path ->
                                    val relative = try {
                                        root.relativize(path)
                                    } catch (e: IllegalArgumentException) {
                                        path
                                    }
                                    if (matcher.matches(relative)) {
                                        files.add(relative.toString())
                                    }
                                }
                        }
                    }
                    files.sorted()
                } else {
                    listOf(value)
                }
            }

            is List<*> -> value.filterNotNull()
            else -> listOf(value)
        }
    }

    private fun generateCombinations(
        variables: Map<String, List<Any>>,
        mode: ParallelModeConfig.CombinationMode
    ): List<Map<String, Any>> {
        if (variables.isEmpty()) return listOf(emptyMap())

        val keys = variables.keys.toList()

        return when (mode) {
            ParallelModeConfig.CombinationMode.CrossJoin -> {
                var combinations = variables[keys[0]]!!.map { mapOf(keys[0] to it) }
                for (i in 1 until keys.size) {
                    val key = keys[i]
                    val values = variables[key]!!
                    combinations = combinations.flatMap { map ->
                        values.map { value ->
                            map + (key to value)
                        }
                    }
                }
                combinations
            }

            ParallelModeConfig.CombinationMode.Zip -> {
                val size = variables.values.minOf { it.size }
                (0 until size).map { i ->
                    keys.associateWith { key -> variables[key]!![i] }
                }
            }
        }
    }

    private fun renderTemplate(template: String, variables: Map<String, Any>): String {
        var result = template
        variables.forEach { (k, v) ->
            result = result.replace("{{$k}}", v.toString())
        }
        return result
    }

    companion object {
        val inputCnt = 1
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/PersonaChatMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path

open class PersonaChatConfig(
    type: CognitiveModeType<*> = CognitiveModeType.Chat,
    var cognitiveStrategy: CognitiveSchemaStrategy = CognitiveSchemaStrategy.ProjectManager,
    var useExpansionSyntax: Boolean = true
) : CognitiveModeConfig(type)

open class PersonaChatMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser,
    val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)
) : CognitiveMode<PersonaChatConfig>(
    orchestrationConfig,
    session,
    user
) {

    init {
        require(orchestrationConfig.defaultSmartModel != null) { "Default model must be specified in orchestration config" }
        require(orchestrationConfig.defaultFastModel != null) { "Parsing model must be specified in orchestration config" }
    }

    private val messagesLock = Any()
    private val transcriptLock = Any()
    private val messages get() = messageMaps.computeIfAbsent(session) { ConcurrentLinkedQueue() }
    private val messageBuffer = ConcurrentLinkedQueue<String>()
    private var transcriptStream: FileOutputStream? = null
    private var isProcessing = false
    private val reasoningState = AtomicReference<Any?>(null)
    private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()

    private val idSubPattern = """[^|\n,/\\;}\]\[><()@]+"""
    private val expansionExpressionPattern = Regex("""@\[($idSubPattern(?:[|,]$idSubPattern)+)]""")
    private val sequenceExpansionPattern = Regex("""@\{([^}]+(?:\s*->\s*[^}]+)+)\}""")
    private val rangeExpansionPattern = Regex("""@\((-?\d+)(?:\.{2,3}| to )(-?\d+)(?:(?::| by )(\d+))?\)""")

    override fun initialize(task : SessionTask) {
        log.debug("PersonaChatMode initialized with task types: ${enabledTasks.joinToString(", ") { it.name }}")
        transcriptStream = task.transcript()
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        log.debug("Handling user message: ${JsonUtil.toJson(userMessage)}")
        val parserChatter = orchestrationConfig.defaultFast.getChildClient(task)
        val defaultChat = orchestrationConfig.defaultSmart.getChildClient(task)

        synchronized(messagesLock) {
            messageBuffer.add(userMessage)
            if (isProcessing) {
                return
            }
            isProcessing = true
        }

        task.echo(userMessage.renderMarkdown(true))
        writeToTranscript("## User\n\n$userMessage\n\n")
        task.ui.pool.submit {
            try {
                while (!Thread.interrupted()) {
                    sleep(100)
                    val msg = messageBuffer.poll() ?: continue
                    val t = task.newTask()
                    task.add(t.placeholder)
                    execute(t, msg, parserChatter, defaultChat)
                }
            } finally {
                synchronized(messagesLock) {
                    isProcessing = false
                }
            }
        }
    }

    private fun execute(
        task: SessionTask,
        userMessage: String,
        parsingChatter: ChatInterface,
        defaultChat: ChatInterface
    ) {
        try {
            val expandedUserMessage = if (config.useExpansionSyntax) expandTopics(userMessage) else userMessage

            val expansionFunctions = processMsgRecursive(
                expandedUserMessage, task, parsingChatter, defaultChat
            )
            val aggregateResponse = StringBuilder()
            runAll(task, expansionFunctions, aggregateResponse)

            synchronized(messagesLock) {
                messages.add(ModelSchema.ChatMessage(ModelSchema.Role.user, expandedUserMessage.toContentList()))
                if (aggregateResponse.isNotEmpty()) {
                    messages.add(
                        ModelSchema.ChatMessage(
                            ModelSchema.Role.assistant, aggregateResponse.toString().toContentList()
                        )
                    )
                }
            }

            if (aggregateResponse.isNotEmpty()) {
                writeToTranscript("## Assistant\n\n${aggregateResponse}\n\n")
            }
        } catch (e: Exception) {
            log.error("Error executing task", e)
            writeToTranscript("## Error\n\n${e.message}\n```\n${e.stackTraceToString()}\n```\n\n")
            task.error(e)
        }
    }

    private fun processMsgRecursive(
        currentMessage: String, task: SessionTask, parsingChatter: ChatInterface, defaultChatter: ChatInterface
    ): List<(StringBuilder) -> Unit> {
        if (config.useExpansionSyntax) {
            val rangeMatch = rangeExpansionPattern.find(currentMessage)
            if (rangeMatch != null) {
                return expandRange(currentMessage, task, rangeMatch, parsingChatter, defaultChatter)
            }
            val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
            if (sequenceMatch != null) {
                return listOf { finalAggregate: StringBuilder ->
                    expandSequence(
                        task,
                        sequenceMatch.groupValues[1].split(Regex("""\s*->\s*""")),
                        currentMessage,
                        sequenceMatch.value,
                        defaultChatter,
                        parsingChatter
                    )
                }
            }
            val match = expansionExpressionPattern.find(currentMessage)
            if (match != null && match.groupValues[1].split('|', ',').size > 1) {
                return expandAlternatives(currentMessage, task, match) { msg, tsk ->
                    processMsgRecursive(msg, tsk, parsingChatter, defaultChatter)
                }
            }
        }
        return listOf { aggregateResponse: StringBuilder ->
            executeTask(currentMessage, task, aggregateResponse, defaultChatter, parsingChatter)
        }
    }

    private fun executeTask(
        userMessage: String,
        task: SessionTask,
        aggregateResponse: StringBuilder,
        defaultModel: ChatInterface,
        parserChatter: ChatInterface
    ) {
        val currentState = reasoningState.updateAndGet { state ->
            if (state == null) {
                val s = config.cognitiveStrategy.initialize(
                    userMessage,
                    getConversationContext(),
                    orchestrationConfig,
                    task,
                    describer
                )
                val stateTask = task.newTask()
                task.add(stateTask.placeholder)
                stateTask.complete(
                  "### Initial Persona State\n" + config.cognitiveStrategy.formatState(s).renderMarkdown())
                s
            } else {
                state
            }
        }!!


        val tabs = TabbedDisplay(task)

        val planTask = tabs.newTask("Plan")
        val chosenTask = if (orchestrationConfig.autoFix) {
            val result = requestToTaskWithPersona(
                defaultModel, parserChatter,
                userMessage,
                orchestrationConfig,
                currentState,
                config.cognitiveStrategy,
                getConversationContext(),
                describer
            )
            planTask.add(result.first.text.renderMarkdown())
            planTask.complete("Executing task:\n```json\n${JsonUtil.toJson(result.second)}\n```".renderMarkdown())
            result
        } else {
            Discussable(
                task = planTask,
                heading = "Plan Review",
                userMessage = { userMessage },
                initialResponse = { prompt ->
                    requestToTaskWithPersona(
                        defaultModel, parserChatter,
                        prompt,
                        orchestrationConfig,
                        currentState,
                        config.cognitiveStrategy,
                        getConversationContext(),
                        describer
                    )
                },
                outputFn = { (reasoning, task) ->
                    reasoning.text.renderMarkdown() + "\n```json\n${JsonUtil.toJson(task)}\n```".renderMarkdown()
                },
                reviseResponse = { history ->
                    val feedbackHistory = history.map { (msg, role) ->
                        "${if (role == ModelSchema.Role.user) "USER" else "ASSISTANT"}: $msg"
                    }
                    val lastUserMsg = history.lastOrNull { it.second == ModelSchema.Role.user }?.first ?: userMessage
                    requestToTaskWithPersona(
                        defaultModel, parserChatter,
                        lastUserMsg,
                        orchestrationConfig,
                        currentState,
                        config.cognitiveStrategy,
                        getConversationContext() + feedbackHistory.dropLast(1),
                        describer
                    )
                }
            ).call()
        }
        val taskConfigJson = JsonUtil.toJson(chosenTask)
        writeToTranscript("### Plan\n\nExecuting task:\n```json\n$taskConfigJson\n```\n\n")
        synchronized(messagesLock) {
            messages.add(
                ModelSchema.ChatMessage(
                    ModelSchema.Role.assistant,
                    "Executing task:\n```json\n$taskConfigJson\n```".toContentList()
                )
            )
        }
        val resultSemaphore = Semaphore(0)
        val resultRef = AtomicReference<String>()

        tabs.newTask("Run").apply {
            orchestrationConfig.getImpl(chosenTask?.component2()).run(
                agent = TaskOrchestrator(
                  user = user,
                  session = session,
                  dataStorage = ui.dataStorage!!,
                  root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                      ?: ui.dataStorage.getSessionDir(user, session).toPath()
                      ?: File(".").toPath()),
                messages = getConversationContext().takeLast(10) + listOf("USER: $userMessage"),
                task = this,
                resultFn = { result ->
                    task.newTask().apply {
                        tabs["Output"] = placeholder
                        complete(result.renderMarkdown())
                    }
                    resultRef.set(result)
                    resultSemaphore.release()
                },
                orchestrationConfig = orchestrationConfig,
            )
            this.complete()
        }
        resultSemaphore.acquire()
        val resultString = resultRef.get() ?: ""
        aggregateResponse.append(resultString).append("\n\n")

        val executionRecord = AdaptivePlanningMode.ExecutionRecord(
            task = chosenTask?.component2(),
            result = resultString
        )
        val newState = config.cognitiveStrategy.update(
            currentState,
            listOf(executionRecord),
            userMessage,
            getConversationContext(),
            orchestrationConfig,
            task,
            describer
        )
        reasoningState.set(newState)

        task.newTask().apply {
            tabs["State"] = placeholder
            complete("### Updated Persona State\n" + config.cognitiveStrategy.formatState(newState).renderMarkdown())
        }

        task.complete()
    }

    private fun runAll(task : SessionTask, function1s: List<(StringBuilder) -> Unit>, target: StringBuilder) {
        val fixedConcurrencyProcessor = FixedConcurrencyProcessor(task.ui.pool, 4)
        function1s.map { function1 ->
            fixedConcurrencyProcessor.submit {
                function1(target)
            }
        }.forEach { it.get() }
    }

    private fun expandRange(
        currentMessage: String,
        task: SessionTask,
        rangeMatch: MatchResult,
        parsingChatter: ChatInterface,
        defaultChatter: ChatInterface
    ): List<(StringBuilder) -> Unit> = listOf { finalAggregate: StringBuilder ->
        val start = rangeMatch.groupValues[1].toInt()
        val end = rangeMatch.groupValues[2].toInt()
        val step = rangeMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 1
        expandSequence(
            task,
            generateSequence(start) { it + step }.takeWhile { if (step > 0) it <= end else it >= end }.toList()
                .map { it.toString() },
            currentMessage,
            rangeMatch.value,
            defaultChatter,
            parsingChatter
        )
    }

    private fun expandAlternatives(
        currentMessage: String,
        task: SessionTask,
        match: MatchResult,
        recursiveFn: (String, SessionTask) -> List<(StringBuilder) -> Unit>
    ): List<(StringBuilder) -> Unit> {
        val tabs = TabbedDisplay(task, closable = config.useExpansionSyntax)
        return match.groupValues[1].split('|', ',').flatMap { option ->
            recursiveFn(
                currentMessage.replaceFirst(match.value, option),
                task.newTask().apply { tabs[option] = placeholder })
        }.apply {
            tabs.update()
        }
    }

    private fun expandSequence(
        task: SessionTask,
        items: List<String>,
        currentMessage: String,
        expression: String,
        defaultChatter: ChatInterface,
        parsingChatter: ChatInterface
    ) {
        val aggregatedResponse = StringBuilder()
        val tabs = TabbedDisplay(task, closable = config.useExpansionSyntax)
        for (item in items) {
            val newMessage = currentMessage.replaceFirst(expression, item)
            val subTaskFunctions = processMsgRecursive(
                currentMessage = newMessage,
                task = task.newTask().apply { tabs[item] = placeholder },
                defaultChatter = defaultChatter,
                parsingChatter = parsingChatter
            )
            val subAggregate = StringBuilder()
            runAll(task, subTaskFunctions, subAggregate)
            aggregatedResponse.append("[").append(item).append("]\n").append(subAggregate.toString()).append("\n")
        }
        tabs.update()
    }

    protected open fun expandTopics(userMessage: String): String {
        if (!config.useExpansionSyntax) return userMessage
        val topicReferencePattern = Regex("""@\{([A-Z][a-zA-Z0-9_ ]+)\}|@([A-Z][a-zA-Z0-9_]*)""")
        return topicReferencePattern.replace(userMessage) { matchResult ->
            val topicType = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }
            val topicList = aggregateTopics[topicType]
            val entities = synchronized(topicList ?: Any()) {
                topicList?.toList()
            }
            if (!entities.isNullOrEmpty()) {
                "@[${entities.joinToString("|")}]"
            } else {
                matchResult.value
            }
        }
    }

    private fun writeToTranscript(content: String) {
        synchronized(transcriptLock) {
            transcriptStream?.write(content.toByteArray())
            transcriptStream?.flush()
        }
    }

    private fun getConversationContext(): List<String> {
        val contextMessages = synchronized(messagesLock) {
            messages.toList()
        }
        return contextMessages.map { message ->
            "${message.role?.name?.uppercase()}: ${message.content?.joinToString("") { it.text ?: "" } ?: ""}"
        }
    }

    override fun contextData(): List<String> {
        return getConversationContext()
    }

    companion object {
        val inputCnt: Int = 1
        private val messageMaps = ConcurrentHashMap<Session, ConcurrentLinkedQueue<ModelSchema.ChatMessage>>()
        private val log = LoggerFactory.getLogger(PersonaChatMode::class.java)

        fun requestToTaskWithPersona(
            defaultModel: ChatInterface,
            fastModel: ChatInterface,
            userMessage: String,
            orchestrationConfig: OrchestrationConfig,
            state: Any,
            strategy: CognitiveSchemaStrategy,
            history: List<String>,
            describer: TaskContextYamlDescriber
        ): Pair<ParsedResponse<Tasks>, TaskExecutionConfig> {
            Tasks.initDescriber(orchestrationConfig, describer)
            val availableTaskTypes = TaskType.getAvailableTaskTypes(orchestrationConfig)
            val parsedActor = ParsedAgent(
                name = "TaskChooser",
                resultClass = Tasks::class.java,
                exampleInstance = Tasks(
                    listOfNotNull(availableTaskTypes.firstOrNull()?.let {
                        orchestrationConfig.getImpl(it).executionConfig
                    }).toMutableList()
                ),
                prompt = buildString {
                    append("You are an AI assistant with the following internal state/persona:\n")
                    append(strategy.formatState(state))
                    append("\n\n")
                    append(strategy.getTaskSelectionGuidance(state))
                    append("\n\n")
                    append("Given the conversation history and the user's latest input, choose ONE task to execute.\n")
                    append("Available task types:\n")
                    append(orchestrationConfig.taskSettings.values.joinToString("\n\n") { config ->
                        val taskType = TaskType.valueOf(config.task_type ?: return@joinToString "")
                        val configName = config.name?.let { " ($it)" } ?: ""
                        "* ${taskType.name}$configName:\n  ${
                            orchestrationConfig.getImpl(taskType).promptSegment().trim().trimIndent()
                                .indent("  ")
                        }" + (orchestrationConfig.workingDir?.let { root ->
                            "\nAvailable files:\n\n" + getAvailableFiles(Path(root)).joinToString("\n") { "      - $it" } + "\n"
                        } ?: "")
                    })
                    append("\nChoose the most suitable task type and provide details of how it should be executed.")
                },
                model = defaultModel,
                parsingChatter = fastModel,
                temperature = orchestrationConfig.temperature,
                describer = describer,
                parserPrompt = ("Task Subtype Schema:\n" + availableTaskTypes.joinToString("\n\n") { taskType ->
                    "${taskType.name}:\n  ${
                        describer.describe(taskType.executionConfigClass).trim().trimIndent().indent("  ")
                    }".trim()
                })
            )
            val answer = parsedActor.answer(
                history + listOf(
                    "USER: $userMessage",
                    "Please choose a single task to execute based on the current conversation context and your internal persona."
                )
            )
            val chosenTask = answer.obj.tasks?.firstOrNull() ?: throw IllegalStateException("No task was selected")
            return Pair(answer, chosenTask)
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/ProtocolMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

open class ProtocolMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser,
    val describer: TaskContextYamlDescriber = TaskContextYamlDescriber(orchestrationConfig)
) : CognitiveMode<ProtocolModeConfig>(
    orchestrationConfig,
    session,
    user
) {

    private val log = LoggerFactory.getLogger(ProtocolMode::class.java)
    private var isRunning = false
    private val history = mutableListOf<String>()

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        if (!isRunning) {
            isRunning = true
            startProtocolSession(task, userMessage)
        } else {
            task.echo("User: $userMessage".renderMarkdown(true))
            history.add("User Message: $userMessage")
        }
    }

    private fun startProtocolSession(task : SessionTask, userMessage: String) {
        task.echo(userMessage.renderMarkdown(true))
        val transcript = task.transcript()
        fun writeToTranscript(content: String) {
            transcript?.write(content.toByteArray())
            transcript?.flush()
        }

        task.ui.pool.execute {
            try {
                task.complete()
                val coordinator = task.ui.dataStorage?.let {
                    TaskOrchestrator(
                      user = user,
                      session = session,
                      dataStorage = it,
                      root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                          ?: task.ui.dataStorage!!.getSessionDir(user, session).toPath() ?: File(".").toPath()
                    )
                } ?: throw IllegalStateException("Coordinator could not be initialized")

                val protocol = if (config.protocolFile != null) {
                    loadPrePlanned(userMessage, coordinator.root, task)
                } else {
                    val definer = { msgs: List<String> -> defineProtocol(task, msgs) }
                    val p = if (orchestrationConfig.autoFix) {
                        definer(listOf(userMessage))
                    } else {
                        Discussable(
                            task = task,
                            heading = "Protocol Definition",
                            userMessage = { userMessage },
                            initialResponse = { definer(listOf(it)) },
                            outputFn = { "```json\n${JsonUtil.toJson(it)}\n```".renderMarkdown() },
                            reviseResponse = { history ->
                                definer(history.map { "${it.second}: ${it.first}" })
                            }
                        ).call()
                    }
                    try {
                        val protocolFile = coordinator.root.resolve("protocol.json").toFile()
                        JsonUtil.toJson(p).let { json ->
                            protocolFile.writeText(json)
                            task.add("Protocol saved to [${protocolFile.name}](${task.linkTo("protocol.json")})")
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to save protocol json", e)
                    }
                    p
                }!!
                writeToTranscript("# Protocol Definition\n\n```json\n${JsonUtil.toJson(protocol)}\n```\n\n")

                val protocolDisplay = TabbedDisplay(task)
              protocolDisplay["Protocol"] = "```json\n${JsonUtil.toJson(protocol)}\n```".renderMarkdown()

                var currentStateName: String? = protocol.initialState
                var iteration = 0
                val maxIterations = config.maxIterations

                while (currentStateName != null && iteration++ < maxIterations) {
                    val currentState = protocol.states.find { it.name == currentStateName }
                        ?: throw IllegalStateException("State $currentStateName not found")

                    writeToTranscript("## State: ${currentState.name}\n\n")
                    val stateTask = task.newTask()
                    protocolDisplay["${iteration}. ${currentState.name}"] = stateTask.placeholder
                    stateTask.header("State: ${currentState.name}", level = 3)
                    stateTask.add("**Instructions:** ${currentState.instructions}".renderMarkdown())

                    var statePassed = false
                    var retryCount = 0
                    val maxRetries = config.maxRetries
                    var nextState: String? = null

                    while (!statePassed && retryCount++ < maxRetries) {
                        if (retryCount > 1) {
                            stateTask.header("Retry $retryCount", level = 4)
                            writeToTranscript("### Retry $retryCount\n\n")
                        }

                        // 1. Execute Action
                        val taskConfig = if (orchestrationConfig.autoFix) {
                            selectTask(task, currentState, userMessage, history)
                        } else {
                            Discussable(
                                task = stateTask,
                                heading = "Task Selection",
                                userMessage = { "Select task for state: ${currentState.name}" },
                                initialResponse = { selectTask(task, currentState, userMessage, history) },
                                outputFn = { "Selected Task: **${it.task_description}**".renderMarkdown() },
                                reviseResponse = { h ->
                                    selectTask(
                                        task, currentState,
                                        userMessage,
                                        history + h.map { "${it.second}: ${it.first}" })
                                }
                            ).call()
                        }

                        val result = StringBuilder()
                        val taskImpl = orchestrationConfig.getImpl(taskConfig)
                        val executionTask = stateTask.newTask()
                        stateTask.add(executionTask.placeholder)
                        executionTask.add("Executing: ${taskConfig?.task_description}".renderMarkdown())

                        taskImpl.run(
                            agent = coordinator,
                            messages = listOf(userMessage),
                            task = executionTask,
                            resultFn = { result.append(it) },
                            orchestrationConfig = orchestrationConfig
                        )
                        val actionResult = result.toString()

                        writeToTranscript("### Action\n\n**Task:** ${taskConfig!!.task_description}\n\n**Result:**\n$actionResult\n\n")

                        // 2. Validate
                        val validation = if (orchestrationConfig.autoFix) {
                            validateState(task, currentState, taskConfig!!, actionResult)
                        } else {
                            Discussable(
                                task = stateTask,
                                heading = "Validation",
                                userMessage = { "Validate result for state: ${currentState.name}" },
                                initialResponse = { validateState(task, currentState, taskConfig!!, actionResult) },
                                outputFn = { "Passed: ${it.passed}\nFeedback: ${it.feedback}".renderMarkdown() },
                                reviseResponse = { h ->
                                    validateState(
                                        task, currentState,
                                        taskConfig!!, actionResult, h.map { "${it.second}: ${it.first}" })
                                }
                            ).call()
                        }!!
                        writeToTranscript("### Validation\n\n**Passed:** ${validation.passed}\n\n**Feedback:** ${validation.feedback}\n\n")

                        val statusClass = if (validation.passed) "text-success" else "text-danger"
                        stateTask.add(
                            "<b>Validation:</b> ${if (validation.passed) "PASSED" else "FAILED"}",
                            additionalClasses = statusClass
                        )
                        stateTask.add(validation.feedback.renderMarkdown())

                        if (validation.passed) {
                            statePassed = true
                            nextState = validation.nextState
                            history.add("State ${currentState.name} completed successfully. Action: ${taskConfig!!.task_description}. Result: $actionResult")
                        } else {
                            history.add("State ${currentState.name} failed. Feedback: ${validation.feedback}")
                        }
                    }

                    if (statePassed) {
                        currentStateName = nextState
                    } else {
                        stateTask.add(
                            "State ${currentState.name} failed after $maxRetries retries.",
                            additionalClasses = "text-danger"
                        )
                        writeToTranscript("State ${currentState.name} failed after max retries.\n")
                        break
                    }
                }

                task.add("Protocol session completed.")
                task.complete()
            } catch (e: Throwable) {
                log.error("Error in ProtocolMode", e)
                task.error(e)
            } finally {
                isRunning = false
                transcript?.close()
            }
        }
    }

    private fun defineProtocol(task : SessionTask, messages: List<String>): ProtocolDefinition {
        val prompt = """
            Define a strict protocol (state machine) to achieve the user's request.
            The protocol can have branching, loops, or be linear.
            Each state must have clear instructions and validation criteria.
            The validation criteria will be used by a referee agent to determine success and the next state.
            Explicitly mention in validation criteria which state to transition to under what conditions.
        """.trimIndent()

        return ParsedAgent(
            name = "ProtocolDefiner",
            resultClass = ProtocolDefinition::class.java,
            prompt = prompt,
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(messages).obj
    }

    private fun selectTask(
        task : SessionTask,
        state: ProtocolState,
        userMessage: String,
        history: List<String>
    ): TaskExecutionConfig {
        Tasks.initDescriber(orchestrationConfig, describer)
        val prompt = """
            You are executing the state '${state.name}' of a protocol.
            Instructions: ${state.instructions}
            
            Choose the appropriate task to perform this step.
            Available task types:
            ${TaskType.getAvailableTaskTypes(orchestrationConfig).joinToString(", ") { it.name }}
        """.trimIndent()

        val tasks = ParsedAgent(
            name = "StateExecutor",
            resultClass = Tasks::class.java,
            prompt = prompt,
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


        ).answer(listOf(userMessage) + history).obj

        return tasks.tasks?.firstOrNull() ?: throw IllegalStateException("No task generated for state ${state.name}")
    }

    private fun validateState(
      task : SessionTask,
      state: ProtocolState,
      taskConfig: TaskExecutionConfig,
      result: String,
      messages: List<String> = emptyList()
    ): ValidationResult {
        val prompt = """
            You are the Referee.
            Current State: ${state.name}
            Validation Criteria: ${state.validationCriteria}
            
            Task Executed: ${taskConfig.task_description}
            Task Result:
            $result
            
            Did the task result satisfy the validation criteria?
            If yes, determine the next state based on the criteria.
            If the protocol is finished, the next state should be null.
            Provide feedback explaining your decision.
        """.trimIndent()

        return ParsedAgent(
            name = "Referee",
            resultClass = ValidationResult::class.java,
            prompt = prompt,
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        ).answer(messages).obj
    }

    override fun contextData(): List<String> = history
    private fun loadPrePlanned(userMessage: String, root: Path, task: SessionTask): ProtocolDefinition {
        val parsedConfig = parseConfig(userMessage, root.toString(), task)
        task.add("Loading protocol from `${parsedConfig.protocolFile}` with variables: ${parsedConfig.variables}")
        val protocolFile = root.resolve(parsedConfig.protocolFile!!).toFile()
        if (!protocolFile.exists()) {
            throw IllegalArgumentException("Protocol file not found: ${protocolFile.absolutePath}")
        }
        val rawJson = protocolFile.readText()
        val genericProtocol: MutableMap<String, Any> = JsonUtil.fromJson(rawJson, MutableMap::class.java)
        val processedProtocol = replaceVariables(genericProtocol, parsedConfig.variables)
        return JsonUtil.fromJson(JsonUtil.toJson(processedProtocol), ProtocolDefinition::class.java)
    }

    private fun parseConfig(message: String, root: String, task: SessionTask): ProtocolModeConfig {
        val availableFiles = getAvailableFiles(Path(root))
            .filter { it.endsWith(".json") }
            .joinToString("\n") { "      - $it" }
        val agent = ParsedAgent(
            name = "ProtocolConfigParser",
            resultClass = ProtocolModeConfig::class.java,
            exampleInstance = ProtocolModeConfig(
                protocolFile = config.protocolFile,
                variables = config.variables
            ),
            prompt = """
                Analyze the user request to identify the protocol file to use and the variables to substitute.
                The user wants to execute a pre-defined protocol stored in a JSON file.
                1. Identify the JSON file mentioned. If not explicitly mentioned, look for '${config.protocolFile}' or the most relevant file in the list below.
                2. Extract any other parameters or instructions as variables. The keys should match placeholders likely found in the protocol (e.g., {{key}}).
                Available JSON files:
                $availableFiles
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = 0.1,
            describer = describer
        )
        return agent.answer(listOf(message)).obj
    }

    private fun replaceVariables(node: Any?, variables: Map<String, String>): Any? {
        return when (node) {
            is String -> {
                var result: String = node
                variables.forEach { (k, v) ->
                    result = result.replace("{{$k}}", v)
                }
                result
            }

            is Map<*, *> -> node.entries.associate { (k, v) -> k to replaceVariables(v, variables) }
            is List<*> -> node.map { replaceVariables(it, variables) }
            else -> node
        }
    }


    data class ProtocolDefinition(
        @Description("The list of states in the protocol")
        val states: List<ProtocolState>,
        @Description("The name of the initial state")
        val initialState: String
    )

    data class ProtocolState(
        @Description("Unique name of the state")
        val name: String,
        @Description("Instructions for the agent in this state")
        val instructions: String,
        @Description("Criteria for the referee to validate success and decide the next state")
        val validationCriteria: String
    )

    data class ValidationResult(
        @Description("Whether the criteria were met")
        val passed: Boolean,
        @Description("Feedback or reason for failure/success")
        val feedback: String,
        @Description("The name of the next state to transition to, or null if terminal")
        val nextState: String?
    )

    companion object {
        val inputCnt = 1
    }
}

class ProtocolModeConfig(
    var maxIterations: Int = 20,
    var maxRetries: Int = 3,
    var protocolFile: String? = null,
    var variables: Map<String, String> = emptyMap()
) : CognitiveModeConfig()
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/Tasks.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.util.ValidatedObject

data class Tasks(
    val tasks: MutableList<TaskExecutionConfig>? = null
) : ValidatedObject {
    override fun validate(): String? {
        val errors = mutableListOf<String>()
        if (tasks == null || tasks.isEmpty()) {
            errors.add("Tasks list cannot be null or empty.")
        } else {
            tasks.forEachIndexed { index, task ->
                if (task is ValidatedObject) task.validate()?.let { errors.add(it) }
            }
        }
        return errors.ifEmpty { null }?.joinToString("; ")
    }
    companion object {
        fun initDescriber(orchestrationConfig: OrchestrationConfig, describer: TaskContextYamlDescriber) {
            describer.clearSubTypes(TaskExecutionConfig::class.java)
            TaskType.getAvailableTaskTypes(orchestrationConfig).forEach { taskType ->
                describer.registerSubType(TaskExecutionConfig::class.java, taskType.executionConfigClass)
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/WaterfallMode.kt

```
package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.PlanUtil.buildMermaidGraph
import com.simiacryptus.cognotik.plan.PlanUtil.filterPlan
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.AgentPatterns
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.io.path.Path

/**
 * A cognitive mode that implements the traditional plan-ahead strategy.
 */
open class WaterfallMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User = defaultUser
) : CognitiveMode<WaterfallMode.WaterfallModeConfig>(
    orchestrationConfig,
    session,
    user
) {
    class WaterfallModeConfig(
        var planFile: String? = null,
        var variables: Map<String, String> = emptyMap()
    ) : CognitiveModeConfig(type = CognitiveModeType.Waterfall)


    private val log = LoggerFactory.getLogger(WaterfallMode::class.java)
    private var transcriptStream: FileOutputStream? = null

    override fun initialize(task : SessionTask) {
        log.debug("Initializing PlanAheadMode")
        transcriptStream = task.transcript()
    }

    override fun contextData(): List<String> = emptyList()

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        try {
            log.debug("Handling user message: $userMessage")
            transcriptStream?.let { stream ->
                stream.write("\n## User Message\n\n$userMessage\n\n".toByteArray())
                stream.flush()
            }
            execute(userMessage, task)
        } catch (e: Throwable) {
            log.error("Error in handleUserMessage", e)
            task.error(e)
        }
    }

    private fun execute(userMessage: String, task: SessionTask) {
        try {
            val coordinator = TaskOrchestrator(
                user = user,
                session = session,
                dataStorage = task.ui.dataStorage!!,
                root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                    ?: task.ui.dataStorage?.getSessionDir(
                        user,
                        session
                    )?.toPath() ?: File(".").toPath(),
                transcriptStream = transcriptStream
            )


            val plan = if (config.planFile != null) {
                loadPrePlanned(userMessage, coordinator.root, task)
            } else {
                val describer = TaskContextYamlDescriber(orchestrationConfig)
                Tasks.initDescriber(orchestrationConfig, describer)
                val plan = initialPlan(
                    codeFiles = coordinator.codeFiles,
                    files = coordinator.files,
                    root = coordinator.root,
                    task = task,
                    userMessage = userMessage,
                    orchestrationConfig = orchestrationConfig,
                    contextFn = { contextData() },
                    describer = describer
                )
                transcriptStream?.let { stream ->
                    stream.write("\n## Generated Plan\n\n${plan.planText}\n\n".toByteArray())
                    stream.write("\n### Plan Diagram\n\n```mermaid\n${buildMermaidGraph((filterPlan { plan.plan } ?: emptyMap()).toMap(), false)}\n```\n\n".toByteArray())
                    stream.flush()
                }
                // Save plan to file for PrePlanned mode
                try {
                    val planFile = coordinator.root.resolve(".logs/plan_${now()}.json").toFile()
                    planFile.writeText(JsonUtil.toJson(plan))
                    task.add("Plan saved to [${planFile.name}](${task.linkTo("plan.json")})".renderMarkdown())
                } catch (e: Exception) {
                    log.warn("Failed to save plan json", e)
                }
                plan
            }
            task.header("Executing Plan")


            coordinator.executePlan(
                plan = plan.plan,
                task = task,
                userMessage = userMessage,
                orchestrationConfig = orchestrationConfig,
                // Use the budgeted and task-specific client
            )
            task.complete()
        } catch (e: Throwable) {
            task.error(e) // Report error on the current task
            log.error("Error in execute", e)
            transcriptStream?.let { stream ->
                stream.write("\n## Error\n\n```\n${e.message}\n${e.stackTraceToString()}\n```\n\n".toByteArray())
                stream.flush()
            }
        } finally {
            transcriptStream?.close()
        }
    }

    open fun initialPlan(
        codeFiles: Map<Path, String>,
        files: Array<File>,
        root: Path,
        task: SessionTask,
        userMessage: String,
        orchestrationConfig: OrchestrationConfig,
        contextFn: () -> List<String> = { emptyList() },
        describer: TypeDescriber
    ): TaskBreakdownWithPrompt {
        val toInput = inputFn(codeFiles, files, root)
        task.echo(userMessage.renderMarkdown())
        return if (!orchestrationConfig.autoFix)
            Discussable(
                task = task,
                heading = "Plan Generation",
                userMessage = { userMessage },
                initialResponse = {
                    newPlan(
                        orchestrationConfig,
                        toInput(userMessage) + contextFn(),
                        describer,
                        task
                    )
                },
                outputFn = {
                    try {
                        render(
                            withPrompt = TaskBreakdownWithPrompt(
                                prompt = userMessage,
                                plan = it.obj,
                                planText = it.text
                            )
                        )
                    } catch (e: Throwable) {
                        log.warn("Error rendering task breakdown", e)
                        task.error(e)
                        e.message ?: e.javaClass.simpleName
                    }
                },
                reviseResponse = { userMessages: List<Pair<String, ModelSchema.Role>> ->
                    newPlan(
                        orchestrationConfig,
                        userMessages.map { it.first },
                        describer,
                        task
                    )
                },
            ).call().let {
                TaskBreakdownWithPrompt(
                    prompt = userMessage,
                    plan = filterPlan { it?.obj } ?: emptyMap(),
                    planText = it?.text ?: "(no plan generated)"
                )
            }
        else {
            newPlan(
                orchestrationConfig,
                toInput(userMessage) + contextFn(),
                describer,
                task
            ).let {
                TaskBreakdownWithPrompt(
                    prompt = userMessage,
                    plan = filterPlan { it.obj } ?: emptyMap(),
                    planText = it.text
                )
            }
        }
    }

    data class TaskBreakdownWithPrompt(
      val prompt: String,
      val plan: Map<String, TaskExecutionConfig>,
      val planText: String
    )

    fun render(
        withPrompt: TaskBreakdownWithPrompt
    ) = AgentPatterns.displayMapInTabs(
        mapOf(
            "Text" to withPrompt.planText.renderMarkdown(),
            "JSON" to "${TRIPLE_TILDE}json\n${JsonUtil.toJson(withPrompt)}\n${TRIPLE_TILDE}".renderMarkdown(),
            "Diagram" to (("```mermaid\n" + buildMermaidGraph(
                (filterPlan {
                    withPrompt.plan
                } ?: emptyMap()).toMutableMap()
            ) + "\n```\n").renderMarkdown())
        )
    )

    open fun newPlan(
        orchestrationConfig: OrchestrationConfig,
        inStrings: List<String>,
        describer: TypeDescriber,
        task: SessionTask
    ): ParsedResponse<Map<String, TaskExecutionConfig>> {
        orchestrationConfig.absoluteWorkingDir?.apply { File(this).mkdirs() }
        val planningActor = orchestrationConfig.planningActor(describer, task)
        return planningActor.respond(
            messages = planningActor.chatMessages(inStrings),
            input = inStrings,
        ).map(Map::class.java) {
            it.tasksByID ?: emptyMap<String, TaskExecutionConfig>()
        } as ParsedResponse<Map<String, TaskExecutionConfig>>
    }

    open fun inputFn(
        codeFiles: Map<Path, String>,
        files: Array<File>,
        root: Path
    ) = { str: String ->
        listOf(
            if (!codeFiles.all { it.key.toFile().isFile } || codeFiles.size > 2) {
                "Files:\n${codeFiles.keys.joinToString("\n") { "* $it" }}"
            } else {
                files.joinToString("\n\n") {
                    val path = root.relativize(it.toPath())
                    "\n## $path\n\n${(codeFiles[path] ?: "").let { "$TRIPLE_TILDE\n${it}\n$TRIPLE_TILDE" }}"
                }
            },
            str
        )
    }

    private fun loadPrePlanned(userMessage: String, root: Path, task: SessionTask): TaskBreakdownWithPrompt {
        val parsedConfig = parseConfig(userMessage, root.toString(), task)
        task.add("Loading plan from `${parsedConfig.planFile}` with variables: ${parsedConfig.variables}".renderMarkdown())
        val planFile = root.resolve(parsedConfig.planFile!!).toFile()
        if (!planFile.exists()) {
            throw IllegalArgumentException("Plan file not found: ${planFile.absolutePath}")
        }
        // Load and substitute variables
        val rawJson = planFile.readText()
        val genericPlan: MutableMap<String, Any> = JsonUtil.fromJson(rawJson, MutableMap::class.java)
        val processedPlan = replaceVariables(genericPlan, parsedConfig.variables)
        // Deserialize
        val planWrapper: TaskBreakdownWithPrompt = JsonUtil.fromJson(
            JsonUtil.toJson(processedPlan),
            TaskBreakdownWithPrompt::class.java
        )
        task.add("Plan loaded with ${planWrapper.plan.size} steps.")
        return planWrapper
    }

    private fun parseConfig(message: String, root: String, task: SessionTask): WaterfallModeConfig {
        val describer = TaskContextYamlDescriber(orchestrationConfig)
        Tasks.initDescriber(orchestrationConfig, describer)
        val availableFiles = getAvailableFiles(Path(root))
            .filter { it.endsWith(".json") }
            .joinToString("\n") { "      - $it" }
        val agent = ParsedAgent(
            name = "PrePlannedConfigParser",
            resultClass = WaterfallModeConfig::class.java,
            exampleInstance = WaterfallModeConfig(
                planFile = config.planFile,
                variables = config.variables
            ),
            prompt = """
Analyze the user request to identify the plan file to use and the variables to substitute.
The user wants to execute a pre-defined plan stored in a JSON file.
1. Identify the JSON file mentioned. If not explicitly mentioned, look for '${config.planFile}' or the most relevant file in the list below.
2. Extract any other parameters or instructions as variables. The keys should match placeholders likely found in the plan (e.g., {{key}}).
Available JSON files:
$availableFiles
            """,
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = 0.1,
            describer = describer
        )
        return agent.answer(listOf(message)).obj
    }

    private fun replaceVariables(node: Any?, variables: Map<String, String>): Any? {
        return when (node) {
            is String -> {
                var result: String = node
                variables.forEach { (k, v) ->
                    result = result.replace("{{$k}}", v)
                }
                result
            }

            is Map<*, *> -> node.entries.associate { (k, v) -> k to replaceVariables(v, variables) }
            is List<*> -> node.map { replaceVariables(it, variables) }
            else -> node
        }
    }

    companion object {
        val inputCnt = 1
        fun now(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date())
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the `README.md` for the `com.simiacryptus.cognotik.plan.cognitive` package, summarizing the various AI planning and execution strategies implemented.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/README.md
```markdown
# Cognitive Planning Modes

This package implements the "cognitive" layer of the AI orchestration system. It defines various strategies for how an AI agent processes user input, maintains internal state, plans actions, and executes tasks.

## Core Architecture

The system is built around a flexible, extensible architecture:

*   **`CognitiveMode`**: The abstract base class for all planning strategies. It provides the interface for handling user messages, managing session state, and generating transcripts.
*   **`CognitiveModeType`**: A dynamic registry of available modes, linking configuration classes to their implementations.
*   **`CognitiveSchemaStrategy`**: Defines specific "thinking" patterns used by iterative modes. These strategies manage how the AI's internal "Reasoning State" is initialized and updated.
    *   **Project Manager**: Standard goal-oriented planning.
    *   **Scientific Researcher**: Hypothesis-driven investigation.
    *   **Agile Developer**: Iterative Test-Driven Development (TDD).
    *   **Critical Auditor**: Security and logic validation.
    *   **Creative Writer**: Narrative and content generation.

## Available Planning Modes

### 1. Conversational Mode (`Chat`)
A task-oriented chat interface that maintains conversation history. It can trigger specific tasks based on user input and supports advanced expansion syntax for generating multiple tasks from a single prompt.

### 2. Adaptive Planning Mode (`Adaptive`)
An iterative mode that maintains a complex `ReasoningState` (goals, knowledge base, and execution context). It uses a `CognitiveSchemaStrategy` to reflect on task results and update its plan in each iteration.

### 3. Hierarchical Planning Mode (`Hierarchical`)
A goal-oriented mode that decomposes high-level objectives into a tree of subgoals and tasks. It manages complex dependency graphs, executing tasks only when their prerequisites are satisfied.

### 4. Council Mode (`Council`)
A multi-agent consensus mode. Different cognitive strategies (e.g., Project Manager, Developer, Auditor) nominate tasks and vote on the best course of action for each iteration.

### 5. Coding Mode (`Coding`)
An interactive environment where the AI solves problems by writing and executing code (e.g., Groovy). It provides a REPL-like experience where the AI can use code to call other system tasks.

### 6. Parallel Mode (`Parallel`)
Optimized for batch processing. It uses variable expansion (e.g., file globs or lists) to generate and execute multiple tasks in parallel using cross-join or zip logic.

### 7. Protocol Mode (`Protocol`)
Executes a strict state machine. The AI defines a "protocol" consisting of states, instructions, and validation criteria, then transitions through these states based on execution outcomes.

### 8. Waterfall Mode (`Waterfall`)
A traditional "plan-ahead" strategy. It generates a full task breakdown upfront, often visualized as a Mermaid diagram, and then executes the plan.

### 9. Persona Chat Mode (`PersonaChat`)
Combines the conversational flow of Chat mode with the structured internal state of a specific cognitive persona (e.g., an Auditor or a Scientist).

## Key Features

*   **Expansion Syntax**: Supports powerful syntax for task generation:
    *   `@[opt1|opt2]`: Parallel alternatives.
    *   `@{a -> b}`: Sequential execution steps.
    *   `@(1..10)`: Numeric ranges for batch operations.
*   **Transcript Logging**: Automatically generates detailed Markdown transcripts of every session, capturing reasoning, plans, and execution results.
*   **Interactive Review**: Many modes support a "Plan Review" phase where the user can discuss and refine the AI's proposed plan before execution begins.
*   **Dynamic Task Mapping**: Uses a `TaskChooser` agent to map natural language requirements to specific `TaskExecutionConfig` objects based on the available tools in the `OrchestrationConfig`.

## Implementation Details

- **`Tasks.kt`**: Provides the data structure for lists of task configurations and utilities for describing available task types to the AI.
- **`CognitiveModeConfig.kt`**: Base configuration class using Jackson type info for polymorphic deserialization of mode settings.
```

### Summary of Changes
- Created a new `README.md` in the `com.simiacryptus.cognotik.plan.cognitive` package directory.
- Documented the core architecture (`CognitiveMode`, `CognitiveModeType`, `CognitiveSchemaStrategy`).
- Provided detailed descriptions for all 9 implemented cognitive modes found in the source files.
- Highlighted key features like expansion syntax, transcripts, and interactive review.
- Included implementation notes on configuration and task mapping.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/README.md</a> Updated
