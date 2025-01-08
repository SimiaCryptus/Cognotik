package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.apps.plan.tools.file.FileModificationTask.FileModificationTaskConfigData
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.indent
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.set
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.util.JsonUtil
import java.io.File
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

open class AutoPlanChatApp(
  applicationName: String = "Auto Plan Chat App",
  path: String = "/autoPlanChat",
  planSettings: PlanSettings,
  model: ChatModel,
  parsingModel: ChatModel,
  domainName: String = "localhost",
  showMenubar: Boolean = true,
  api: API? = null,
  api2: OpenAIClient,
  val maxTaskHistoryChars: Int = 20000,
  val maxTasksPerIteration: Int = 3,
  val maxIterations: Int = 100
) : PlanChatApp(
  applicationName = applicationName,
  path = path,
  planSettings = planSettings,
  model = model,
  parsingModel = parsingModel,
  domainName = domainName,
  showMenubar = showMenubar,
  api = api,
  api2 = api2
) {
  override val stickyInput = true
  override val singleInput = false

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(AutoPlanChatApp::class.java)
    private val currentUserMessages = mutableMapOf<String, AtomicReference<String?>>()
    private val runningStates = mutableMapOf<String, Boolean>()
    private val executionRecordMap = mutableMapOf<String, MutableList<ExecutionRecord>>()
    private val thinkingStatuses = mutableMapOf<String, AtomicReference<ThinkingStatus?>>()

    @Synchronized
    private fun getState(sessionId: String): Triple<AtomicReference<String?>, Boolean, MutableList<ExecutionRecord>> {
      return Triple(
        currentUserMessages.getOrPut(sessionId) { AtomicReference(null) },
        runningStates.getOrPut(sessionId) { false },
        executionRecordMap.getOrPut(sessionId) { mutableListOf() }
      )
    }

    @Synchronized
    private fun setState(sessionId: String, running: Boolean) {
      runningStates[sessionId] = running
    }
  }

  private fun logDebug(message: String, data: Any? = null) {
    if (data != null) {
      log.debug("$message: ${JsonUtil.toJson(data)}")
    } else {
      log.debug(message)
    }
  }

  @Description("The current thinking status of the AI assistant.")
  data class ThinkingStatus(
    @Description("The original user prompt or request that initiated the conversation.")
    var initialPrompt: String? = null,
    @Description("The hierarchical goals structure defining both immediate and long-term objectives.")
    val goals: Goals? = null,
    @Description("The accumulated knowledge, facts, and uncertainties gathered during the conversation.")
    val knowledge: Knowledge? = null,
    @Description("The operational context including task history, current state, and planned actions.")
    val executionContext: ExecutionContext? = null
  )

  @Description("The goals of the AI assistant.")
  data class Goals(
    @Description("Immediate objectives that need to be accomplished in the current iteration.")
    val shortTerm: MutableList<Any>? = null,
    @Description("Overall objectives that span multiple iterations or the entire conversation.")
    val longTerm: MutableList<Any>? = null
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

  data class ExecutionRecord(
    val time: Date? = Date(),
    val task: TaskConfigBase? = null,
    val result: String? = null
  )

  data class Tasks(
    val tasks: MutableList<TaskConfigBase>? = null
  )

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      logDebug("Received user message", userMessage)
      val (currentUserMessage, isRunning, _) = getState(session.sessionId)
      if (!isRunning) {
        setState(session.sessionId, true)
        logDebug("Starting new auto plan chat session")
        startAutoPlanChat(session, user, userMessage, ui, api)
      } else {
        logDebug("Injecting user message into ongoing chat", userMessage)
        val userMessageTask = ui.newTask()
        userMessageTask.echo(renderMarkdown("User: $userMessage", ui = ui))
        currentUserMessage.set(userMessage)
      }
    } catch (e: Exception) {
      log.error("Error processing user message", e)
      ui.newTask().add(renderMarkdown("An error occurred while processing your message: ${e.message}", ui = ui))
    }
  }

  protected open fun startAutoPlanChat(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    logDebug("Starting auto plan chat with initial message", userMessage)
    val thinkingStatus = thinkingStatuses.computeIfAbsent(session.sessionId) { AtomicReference<ThinkingStatus?>(null) }
    val task = ui.newTask(true)
    val (currentUserMessage, _, executionRecords) = getState(session.sessionId)
    val api = (api as ChatClient).getChildClient().apply {
      val createFile = task.createFile(".logs/api-${UUID.randomUUID()}.log")
      createFile.second?.apply {
        logStreams += this.outputStream().buffered()
        logDebug("Created API log file", this)
        task.verbose("API log: <a href=\"file:///$this\">$this</a>")
      }
    }

    val tabbedDisplay = TabbedDisplay(task)
    val executor = ui.socketManager!!.pool
    var continueLoop = true
    executor.execute {
      try {
        logDebug("Starting main execution loop")
        ui.newTask(false).let { task ->
          tabbedDisplay["Controls"] = task.placeholder
          lateinit var stopLink: StringBuilder
          stopLink = task.add(ui.hrefLink("Stop") {
            logDebug("Stop button clicked - terminating execution")
            continueLoop = false
            executor.shutdown()
            stopLink.set("Stopped")
            task.complete()
          })!!
        }
        tabbedDisplay.update()
        task.complete()
        val initialPromptTask = ui.newTask(false)
        initialPromptTask.add(renderMarkdown("Starting Auto Plan Chat for prompt: $userMessage"))
        tabbedDisplay["Initial Prompt"] = initialPromptTask.placeholder
        val planSettings = getSettings(session, user, PlanSettings::class.java) ?: planSettings.copy(allowBlocking = false)
        logDebug("Initialized plan settings", planSettings)
        api.budget = planSettings.budget
        val coordinator = PlanCoordinator(
          user = user,
          session = session,
          dataStorage = dataStorage,
          ui = ui,
          root = planSettings.workingDir?.let { File(it).toPath() } ?: dataStorage.getDataDir(user, session).toPath(),
          planSettings = planSettings
        )
        logDebug("Created plan coordinator")
        val initialStatus = initThinking(planSettings, userMessage, api)
        logDebug("Initialized thinking status", initialStatus)
        initialStatus.initialPrompt = userMessage
        thinkingStatus.set(initialStatus)
        initialPromptTask.complete(renderMarkdown("Initial Thinking Status:\n${formatThinkingStatus(thinkingStatus.get()!!)}"))

        var iteration = 0
        while (iteration++ < maxIterations && continueLoop) {
          logDebug("Starting iteration $iteration")
          task.complete()
          val task = ui.newTask(false).apply { tabbedDisplay["Iteration $iteration"] = placeholder }
          val api = api.getChildClient().apply {
            val createFile = task.createFile(".logs/api-${UUID.randomUUID()}.log")
            createFile.second?.apply {
              logStreams += this.outputStream().buffered()
              logDebug("Created iteration API log file", this)
              task.verbose("API log: <a href=\"file:///$this\">$this</a>")
            }
          }
          val tabbedDisplay = TabbedDisplay(task, additionalClasses = "iteration")
          ui.newTask(false).apply {
            tabbedDisplay["Inputs"] = placeholder
            header("Project Info")
            contextData().forEach { add(renderMarkdown(it)) }
            header("Evaluation Records")
            formatEvalRecords(session = session).forEach { add(renderMarkdown(it)) }
            header("Current Thinking Status")
            formatThinkingStatus(thinkingStatus.get()!!).let { add(renderMarkdown(it)) }
          }
          val nextTask = try {
            logDebug("Getting next task")
            getNextTask(api, planSettings, coordinator, userMessage, thinkingStatus.get(), session)
          } catch (e: Exception) {
            log.error("Error choosing next task", e)
            tabbedDisplay["Errors"]?.append(renderMarkdown("Error choosing next task: ${e.message}"))
            break
          }
          if (nextTask?.isEmpty() != false) {
            logDebug("No more tasks to execute")
            task.add(renderMarkdown("No more tasks to execute. Finishing Auto Plan Chat."))
            break
          }
          logDebug("Retrieved next tasks", nextTask)

          val taskResults = mutableListOf<Pair<TaskConfigBase, Future<String>>>()
          for ((index, currentTask) in nextTask.withIndex()) {
            val currentTaskId = "task_${index + 1}"
            logDebug("Executing task $currentTaskId", currentTask)
            val taskExecutionTask = ui.newTask(false)
            taskExecutionTask.add(
              renderMarkdown(
                "Executing task: `$currentTaskId` - ${currentTask.task_description}\n```json\n${
                  JsonUtil.toJson(currentTask)
                }\n```"
              )
            )
            tabbedDisplay["Task Execution $currentTaskId"] = taskExecutionTask.placeholder
            val future = executor.submit<String> {
              try {
                runTask(api, api2, task, coordinator, currentTask, currentTaskId, userMessage, taskExecutionTask, thinkingStatus.get(), session)
              } catch (e: Exception) {
                taskExecutionTask.error(ui, e)
                log.error("Error executing task", e)
                "Error executing task: ${e.message}"
              }
            }
            taskResults.add(Pair(currentTask, future))
          }
          val completedTasks = taskResults.map { (task, future) ->
            val result = future.get()
            logDebug("Task completed", mapOf("task" to task, "result" to result))
            ExecutionRecord(
              time = Date(),
              task = task,
              result = result
            )
          }
          executionRecords.addAll(completedTasks)

          val thinkingStatusTask = ui.newTask(false).apply { tabbedDisplay["Thinking Status"] = placeholder }
          logDebug("Updating thinking status")
          thinkingStatus.set(
            updateThinking(api, planSettings, thinkingStatus.get(), completedTasks, session)
          )
          logDebug("Updated thinking status", thinkingStatus.get())
          thinkingStatusTask.complete(renderMarkdown("Updated Thinking Status:\n${formatThinkingStatus(thinkingStatus.get()!!)}"))
        }
        logDebug("Main execution loop completed")
        task.complete("Auto Plan Chat completed.")
      } catch (e: Throwable) {
        task.error(ui, e)
        log.error("Error in startAutoPlanChat", e)
      } finally {
        logDebug("Finalizing auto plan chat")
        setState(session.sessionId, false)
        val summaryTask = ui.newTask(false).apply { tabbedDisplay["Summary"] = placeholder }
        summaryTask.add(
          renderMarkdown(
            "Auto Plan Chat completed. Final thinking status:\n${
              thinkingStatus.get()?.let {
                formatThinkingStatus(it)
              } ?: "null"
            }"))
        task.complete()
      }
    }

  }

  protected open fun runTask(
    api: ChatClient,
    api2: OpenAIClient,
    task: SessionTask,
    coordinator: PlanCoordinator,
    currentTask: TaskConfigBase,
    currentTaskId: String,
    userMessage: String,
    taskExecutionTask: SessionTask,
    thinkingStatus: ThinkingStatus?,
    session: Session,
  ): String {
    val api = api.getChildClient().apply {
      val createFile = task.createFile(".logs/api-${UUID.randomUUID()}.log")
      createFile.second?.apply {
        logStreams += this.outputStream().buffered()
        task.verbose("API log: <a href=\"file:///$this\">$this</a>")
      }
    }
    val taskImpl = TaskType.getImpl(coordinator.planSettings, currentTask)
    val result = StringBuilder()
    taskImpl.run(
      agent = coordinator.copy(
        planSettings = coordinator.planSettings.copy(
          taskSettings = coordinator.planSettings.taskSettings.toList().toTypedArray().toMap().toMutableMap().apply {
            this["TaskPlanning"] = TaskSettingsBase(task_type = TaskType.TaskPlanning.name, enabled = false, model = null)
          }
        )
      ),
      messages = listOf(
        userMessage,
        "Current thinking status:\n${formatThinkingStatus(thinkingStatus!!)}"
      ) + formatEvalRecords(session = session),
      task = taskExecutionTask,
      api = api,
      resultFn = { result.append(it) },
      api2 = api2,
      planSettings = planSettings,
    )
    return result.toString()
  }

  protected open fun getNextTask(
    api: ChatClient,
    planSettings: PlanSettings,
    coordinator: PlanCoordinator,
    userMessage: String,
    thinkingStatus: ThinkingStatus?,
    session: Session,
  ): List<TaskConfigBase>? {
    val describer1 = planSettings.describer()
    val tasks = ParsedActor(
      name = "SingleTaskChooser",
      resultClass = Tasks::class.java,
      exampleInstance = Tasks(
        listOf(
          FileModificationTaskConfigData(
            task_description = "Modify the file 'example.txt' to include the given input."
          )
        ).toMutableList()
      ),
      prompt = buildString {
        append("Given the following input, choose up to ")
        append(maxTasksPerIteration)
        append("tasks to execute. Do not create a full plan, just select the most appropriate task types for the given input and note any required/important details.\n")
        append("Available task types:\n")
        append(TaskType.getAvailableTaskTypes(coordinator.planSettings).joinToString("\n\n") { taskType ->
            "* ${TaskType.getImpl(coordinator.planSettings, taskType).promptSegment().trim().trimIndent().indent("  ")}"
          })
        append("\nChoose the most suitable task types and provide details of how they should be executed.")
      },
      model = coordinator.planSettings.defaultModel,
      parsingModel = coordinator.planSettings.parsingModel,
      temperature = coordinator.planSettings.temperature,
      describer = describer1,
      parserPrompt = ("Task Subtype Schema:\n" + TaskType.getAvailableTaskTypes(coordinator.planSettings).joinToString("\n\n") { taskType ->
        "${taskType.name}:\n  ${describer1.describe(taskType.taskDataClass).trim().trimIndent().indent("  ")}".trim()
      })
    ).answer(
      listOf(userMessage) + contextData() +
          listOf(
            """
                Current thinking status: ${formatThinkingStatus(thinkingStatus!!)}
                Please choose the next single task to execute based on the current status.
                If there are no tasks to execute, return {}.
            """.trimIndent()
          )
          + formatEvalRecords(session = session), api
    ).obj.tasks?.map { task ->
      task to (if (task.task_type == null) {
        null
      } else {
        TaskType.getImpl(coordinator.planSettings, task)
      })?.taskConfig
    }
    if (tasks.isNullOrEmpty()) {
      log.info("No tasks selected from: ${tasks?.map { it.first }}")
      return null
    } else if (tasks.mapNotNull { it.second }.isEmpty()) {
      log.warn("No tasks selected from: ${tasks.map { it.first }}")
      return null
    } else {
      return tasks.mapNotNull { it.second }.take(maxTasksPerIteration)
    }
  }

  protected open fun updateThinking(
    api: ChatClient,
    planSettings: PlanSettings,
    thinkingStatus: ThinkingStatus?,
    completedTasks: List<ExecutionRecord>,
    session: Session,
  ): ThinkingStatus = ParsedActor(
    name = "UpdateQuestionsActor",
    resultClass = ThinkingStatus::class.java,
    exampleInstance = ThinkingStatus(
      initialPrompt = "Create a Python script to analyze log files and generate a summary report",
      goals = Goals(
        shortTerm = mutableListOf(
          "Understand log file format requirements",
          "Define report structure",
          "Plan implementation approach"
        ),
        longTerm = mutableListOf(
          "Deliver working Python script",
          "Ensure robust error handling",
          "Provide documentation"
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
          "Add error handling"
        )
      )
    ),
    prompt = """
        Given the current thinking status, the last completed task, and its result,
        update the open questions to guide the next steps of the planning process.
        Consider what information is still needed and what new questions arise from the task result.
        Update the current goal if necessary, adjust the progress, suggest next steps, and add any new insights.
        Update the knowledge base with new facts and hypotheses.
        Update the estimated time remaining and adjust the confidence level based on progress.
        Reassess challenges, available resources, and alternative approaches.
    """.trimIndent(),
    model = planSettings.defaultModel,
    parsingModel = planSettings.parsingModel,
    temperature = planSettings.temperature,
    describer = planSettings.describer()
  ).answer(
    listOf("Current thinking status: ${formatThinkingStatus(thinkingStatus!!)}") + contextData() +
        completedTasks.flatMap { record ->
          listOf(
            "Completed task: ${record.task?.task_description}",
            "Task result: ${record.result}"
          )
        } + (currentUserMessages.get(session.sessionId)?.let { listOf("User message: $it") } ?: listOf()),
    api
  ).obj.apply {
    currentUserMessages.get(session.sessionId)?.set(null)
    knowledge?.facts?.apply {
      this.addAll(completedTasks.mapIndexed { index, (task, result) ->
        "Task ${(executionContext?.completedTasks?.size ?: 0) + index + 1} Result: $result"
      })
    }
  }

  protected open fun initThinking(
    planSettings: PlanSettings,
    userMessage: String,
    api: ChatClient
  ): ThinkingStatus {
    val initialStatus = ParsedActor(
      name = "ThinkingStatusInitializer",
      resultClass = ThinkingStatus::class.java,
      exampleInstance = ThinkingStatus(
        initialPrompt = "Example prompt",
        goals = Goals(
          shortTerm = mutableListOf("Understand the user's request"),
          longTerm = mutableListOf("Complete the user's task")
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
      describer = planSettings.describer()
    ).answer(listOf(userMessage) + contextData(), api).obj
    return initialStatus
  }

  protected open fun formatEvalRecords(maxTotalLength: Int = maxTaskHistoryChars, session: Session): List<String> {
    var currentLength = 0
    val formattedRecords = mutableListOf<String>()
    val sessionExecutionRecords = executionRecordMap[session.sessionId] ?: mutableListOf()
    for (record in sessionExecutionRecords.reversed()) {
      val formattedRecord = """
# Task ${sessionExecutionRecords.indexOf(record) + 1}

## Task:
```json
${JsonUtil.toJson(record.task!!)}
```

## Result: 
${
        record.result?.let {
          // Add 2 levels of header level to each header
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

  protected open fun formatThinkingStatus(thinkingStatus: ThinkingStatus) = """
```json
${JsonUtil.toJson(thinkingStatus)}
```
"""


  protected open fun contextData(): List<String> = emptyList()
}