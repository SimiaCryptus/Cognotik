package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.PlanCoordinator
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
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
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


open class GoalOrientedMode(
  override val ui: ApplicationInterface,
  override val api: API,
  override val planSettings: PlanSettings,
  override val session: Session,
  override val user: User?,
  private val api2: OpenAIClient,
  val describer: TypeDescriber,
  private val maxConcurrency: Int = 4,
  private val maxIterations: Int = 20
) : CognitiveMode {
  private val log = LoggerFactory.getLogger(GoalOrientedMode::class.java)
  private val goalIdCounter = AtomicInteger(1)
  private val taskIdCounter = AtomicInteger(1)
  private val isRunning = AtomicBoolean(false)
  private val stopRequested = AtomicBoolean(false)

  private val goalTree = ConcurrentHashMap<String, Goal>()
  private val taskMap = ConcurrentHashMap<String, Task>()

  override fun initialize() {
    log.debug("Initializing GoalOrientedMode")
    goalTree.clear()
    taskMap.clear()
    goalIdCounter.set(1)
    taskIdCounter.set(1)
    stopRequested.set(false)
  }

  override fun handleUserMessage(userMessage: String, task: SessionTask) {
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
      task.error(ui, e)
    } finally {
      isRunning.set(false)
    }
  }

  private fun startGoalOrientedSession(userMessage: String, task: SessionTask) {
    task.echo("User: $userMessage".renderMarkdown())

    val stopLinkRef = AtomicReference<StringBuilder>()
    val stopLink = task.add(ui.hrefLink("Stop Goal-Oriented Processing") {
      log.info("Stop requested by user.")
      stopRequested.set(true)
      stopLinkRef.get()?.set("Stop signal sent. Waiting for current iteration to finish...")
    })
    stopLinkRef.set(stopLink!!)

    val mainTab = TabbedDisplay(task)

    val goalTreeTask = ui.newTask(false).apply { mainTab["Goal Tree"] = placeholder }
    val goalTreeElement = goalTreeTask.add("Loading...".renderMarkdown())
    fun updateGoalTreeUI() {
      goalTreeElement?.set(renderGoalTreeText(goalTree.values.toList()).renderMarkdown())
      goalTreeTask.update()
    }
    val goalsTab = TabbedDisplay(ui.newTask(false).apply { mainTab["Goals"] = placeholder })
    val tasksTab = TabbedDisplay(ui.newTask(false).apply { mainTab["Tasks"] = placeholder })


    val executor = ui.socketManager?.pool ?: throw IllegalStateException("SocketManager or its pool is null")
    val processor = FixedConcurrencyProcessor(executor, maxConcurrency)
    val apiClient = (api as? ChatClient)?.getChildClient(task) ?: throw IllegalStateException("API must be a ChatClient")
    apiClient.budget = planSettings.budget
    val sessionLog = StringBuilder()
    val sessionLogTask = ui.newTask(false).apply { mainTab["Session Log"] = placeholder }
    fun logToSession(message: String) {
      log.info(message)
      sessionLog.append(message).append("\n")
      sessionLogTask.complete(message.renderMarkdown())
    }
    logToSession("Starting Goal-Oriented session for: $userMessage")
    val coordinator = PlanCoordinator(
      user = user,
      session = session,
      dataStorage = ui.socketManager?.dataStorage!!,
      ui = ui,
      root = planSettings.absoluteWorkingDir?.let { File(it).toPath() }
        ?: ui.socketManager!!.dataStorage?.getDataDir(user, session)?.toPath() ?: File(".").toPath(),
      planSettings = planSettings
    )

    try {
      val initialGoals = parseInitialGoals(userMessage, apiClient)
      if (initialGoals.isEmpty()) {
        logToSession("No initial goals parsed. Aborting.")
        task.complete("Could not determine initial goals from your request.".renderMarkdown())
        return
      }
      initialGoals.forEach { goal ->
        goalTree[goal.id!!] = goal
      }
      logToSession("Parsed ${initialGoals.size} initial goal(s).")
    } catch (e: Exception) {
      log.error("Failed to parse initial goals", e)
      logToSession("Error parsing initial goals: ${e.message}")
      task.error(ui, e)
      return
    }
    updateGoalTreeUI()

    var iteration = 0
    while (iteration < maxIterations && !stopRequested.get()) {
      if (stopRequested.get()) break
      iteration++
      logToSession("\n## Iteration $iteration / $maxIterations")
      updateGoalTreeUI()
      updateAllStatuses()
      val decomposableGoals = goalTree.values.filter {
        it.status == GoalStatus.ACTIVE && false == it.decompositionAttempted && it.subgoals?.isEmpty() == true && it.tasks?.isEmpty() == true
      }

      if (decomposableGoals.isNotEmpty()) {
        logToSession("Found ${decomposableGoals.size} goal(s) to decompose:")
        decomposableGoals.forEach { logToSession("- Goal ID ${it.id}: ${it.description}") }
      }

      for (goal in decomposableGoals) {
        if (stopRequested.get()) break
        logToSession("Decomposing goal: ${goal.description} (ID: ${goal.id})")
        // Create a goal tab for this goal
        val goalTab = ui.newTask(false).apply { goalsTab["Goal ID ${goal.id}"] = placeholder }
        goalTab.add("# Goal: ${goal.description}\n\nID: ${goal.id}".renderMarkdown())
        
        try {
          val (subgoals, tasksForGoal) = decomposeGoal(goal, apiClient, coordinator)
          goal.decompositionAttempted = true
          if (subgoals.isEmpty() && tasksForGoal.isEmpty()) {
            logToSession("Goal ID ${goal.id} (${goal.description}) decomposed into no subgoals or tasks.")
            goalTab.add("No subgoals or tasks were generated for this goal.".renderMarkdown())

          } else {
            val subgoalsList = StringBuilder("## Subgoals:\n")
            val tasksList = StringBuilder("## Tasks:\n")

            subgoals.forEach { subgoal ->

              if (!goalTree.containsKey(subgoal.id)) {
                goalTree[subgoal.id!!] = subgoal
                logToSession("  New subgoal: ${subgoal.description} (ID: ${subgoal.id}) for Goal ${goal.id}")
                subgoalsList.append("- ${subgoal.description} (ID: ${subgoal.id})\n")
              } else {
                logToSession("  Subgoal ID ${subgoal.id} already exists. Skipping addition.")
              }
              goal.subgoals?.add(subgoal.id!!)
            }
            tasksForGoal.forEach { t ->
              if (!taskMap.containsKey(t.id)) {
                taskMap[t.id!!] = t
                logToSession("  New task: ${t.description} (ID: ${t.id}) for Goal ${goal.id}")
                tasksList.append("- ${t.description} (ID: ${t.id})\n")
              } else {
                logToSession("  Task ID ${t.id} already exists. Skipping addition.")
              }
              goal.tasks?.add(t.id!!)
            }
            if (subgoals.isNotEmpty()) {
              goalTab.add(subgoalsList.toString().renderMarkdown())
            }
            if (tasksForGoal.isNotEmpty()) {
              goalTab.add(tasksList.toString().renderMarkdown())
            }
          }
        } catch (e: Exception) {
          log.error("Error decomposing goal ${goal.id}", e)
          logToSession("Error decomposing goal ${goal.id}: ${e.message}. Marking as BLOCKED.")
          goalTab.add("**ERROR:** Failed to decompose goal: ${e.message}".renderMarkdown())
          goal.status = GoalStatus.BLOCKED
          goal.result = "Failed to decompose: ${e.message}"
        }
        updateAllStatuses()
        updateGoalTreeUI()
      }

      if (stopRequested.get()) break

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
          updateGoalTreeUI()

          val future = processor.submit<String?> {
            try {
              val executionUiTask = ui.newTask(false).apply { tasksTab["Task ID ${t.id}"] = placeholder }
              val taskResult = executeTask(t, apiClient.getChildClient(executionUiTask), executionUiTask, coordinator)
              taskResult
            } catch (e: Exception) {
              log.error("Task ID ${t.id} (${t.description}) execution failed in processor.submit lambda", e)

              taskMap[t.id]?.apply {
                status = TaskStatus.FAILED
                result = "Execution Error: ${e.message}"
              }
              throw e
            }
          }
          taskExecutionJobs.add(Pair(t, future))
        }

        for ((taskInstance, future) in taskExecutionJobs) {
          if (stopRequested.get()) {
            logToSession("Stop requested, not waiting for all tasks to complete this iteration.")

            break
          }
          try {

            val taskOutputResult = future.get()
            if (taskInstance.status != TaskStatus.FAILED) {
              taskInstance.status = TaskStatus.COMPLETED
              taskInstance.result = taskOutputResult
              logToSession(
                "Task ID ${taskInstance.id} (${taskInstance.description}) COMPLETED. Result: ${
                  taskOutputResult?.take(100)?.trim()
                }..."
              )
            }

          } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("Task ID ${taskInstance.id} (${taskInstance.description}) interrupted.", e)
            taskInstance.status = TaskStatus.FAILED
            taskInstance.result = "Task execution was interrupted."
            logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) INTERRUPTED.")
          } catch (e: Exception) {

            val cause = if (e is java.util.concurrent.ExecutionException) e.cause ?: e else e
            log.error("Task ID ${taskInstance.id} (${taskInstance.description}) failed or error retrieving result.", cause)
            if (taskInstance.status != TaskStatus.FAILED) {
              taskInstance.status = TaskStatus.FAILED
              taskInstance.result = "Execution Error: ${cause.message}"
            }
            logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) FAILED. Reason: ${taskInstance.result}")
          }
        }
      } else {
        logToSession("No executable tasks in this iteration.")
      }

      updateAllStatuses()
      updateGoalTreeUI()

      val activeGoalsCount = goalTree.values.count { it.status == GoalStatus.ACTIVE }
      val pendingOrRunningTasksCount = taskMap.values.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING }

      if (activeGoalsCount == 0 && pendingOrRunningTasksCount == 0) {
        val allDoneOrBlocked = goalTree.values.all { it.status == GoalStatus.COMPLETED || it.status == GoalStatus.BLOCKED } &&
            taskMap.values.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
        if (allDoneOrBlocked) {
          logToSession("All goals are completed or blocked. No pending/running tasks.")
          break
        }
      }
      if (decomposableGoals.isEmpty() && executableTasks.isEmpty() && (activeGoalsCount > 0 || pendingOrRunningTasksCount > 0)) {
        logToSession("Stalled: No goals decomposed and no tasks executed, but active goals or pending/running tasks remain. Check for dependency cycles or unresolvable goals.")
      }
    }

    updateGoalTreeUI()
    // Update the Goals tab with final status of all goals
    val goalsSummaryTask = ui.newTask(false).apply { goalsTab["Summary"] = placeholder }
    goalsSummaryTask.add("# Goals Summary\n\n".renderMarkdown())
    val goalsSummary = StringBuilder()
    goalTree.values.sortedBy { it.id }.forEach { goal ->
      val statusEmoji = when (goal.status!!) {
        GoalStatus.ACTIVE -> "🟢"
        GoalStatus.BLOCKED -> "🧱"
        GoalStatus.COMPLETED -> "✅"
        GoalStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳"
      }
      goalsSummary.append("$statusEmoji **${goal.id}**: ${goal.description}\n")
      if (goal.result != null) {
        goalsSummary.append("  - Result: ${goal.result?.take(100)?.replace("\n", " ")}...\n")
      }
    }
    goalsSummaryTask.add(goalsSummary.toString().renderMarkdown())
    // Update the Tasks tab with final status of all tasks
    val tasksSummaryTask = ui.newTask(false).apply { tasksTab["Summary"] = placeholder }
    tasksSummaryTask.add("# Tasks Summary\n\n".renderMarkdown())
    val tasksSummary = StringBuilder()
    taskMap.values.sortedBy { it.id }.forEach { task ->
      val statusEmoji = when (task.status!!) {
        TaskStatus.PENDING -> "📝"
        TaskStatus.RUNNING -> "🏃"
        TaskStatus.COMPLETED -> "✔️"
        TaskStatus.FAILED -> "❌"
        TaskStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳"
      }
      tasksSummary.append("$statusEmoji **${task.id}**: ${task.description}\n")
      if (task.result != null) {
        tasksSummary.append("  - Result: ${task.result?.take(100)?.replace("\n", " ")}...\n")
      }
    }
    tasksSummaryTask.add(tasksSummary.toString().renderMarkdown())

    if (stopRequested.get()) {
      logToSession("Goal-Oriented session stopped by user request at iteration $iteration.")
      task.complete("Session stopped by user.".renderMarkdown())
      stopLink.set("Stopped")
    } else if (iteration >= maxIterations) {
      logToSession("Goal-Oriented session reached max iterations ($maxIterations).")
      task.complete("Session reached max iterations.".renderMarkdown())
      stopLink.set("Max Iterations Reached")
    } else {
      val finalStatusSummary = goalTree.values.groupBy { it.status }.mapValues { it.value.size }.toString()
      logToSession("Goal-Oriented session completed. Final status: $finalStatusSummary")
      task.complete("Session completed. Final Status: $finalStatusSummary".renderMarkdown())
      stopLink.set("Completed")
    }
    sessionLogTask.complete(sessionLog.toString().renderMarkdown())
  }

  private fun parseInitialGoals(userMessage: String, api: ChatClient): List<Goal> {
    val parsedActor = ParsedActor(
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
      model = planSettings.defaultModel,
      parsingModel = planSettings.parsingModel,
      temperature = planSettings.temperature,
      describer = describer
    )
    val answer = parsedActor.answer(listOf(userMessage), api)
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
      g.copy(
        id = g.id?.takeIf { it.isNotBlank() } ?: "G${goalIdCounter.getAndIncrement()}",
        description = g.description,
        status = g.status ?: (if (g.dependencies?.isEmpty() != false) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT),
        parentGoalId = g.parentGoalId,
        subgoals = g.subgoals ?: mutableListOf(),
        tasks = g.tasks ?: mutableListOf(),
        dependencies = g.dependencies ?: mutableListOf(),
        decompositionAttempted = g.decompositionAttempted ?: false,
        result = g.result
      )
    }
  }


  private fun decomposeGoal(
    goal: Goal,
    api: ChatClient, // This should be the iteration-specific API client
    coordinator: PlanCoordinator
  ): Pair<List<Goal>, List<Task>> {
    val parsedActor = ParsedActor(
      name = "GoalDecomposer",
      resultClass = GoalDecomposition::class.java,
      exampleInstance = GoalDecomposition( // Example should match the structure and intent
        subgoals = listOf(
          Goal(
            id = "G2",
            description = "Design API endpoint",
            parentGoalId = goal.id!!,
            subgoals = mutableListOf(),
            tasks = mutableListOf(),
            dependencies = mutableListOf()
          )
        ),
        tasks = listOf(
          Task(
            id = "T1",
            description = "Draft OpenAPI spec for upload endpoint",
            parentGoalId = goal.id,
            dependencies = mutableListOf()
          )
        )
      ),
      prompt = run {
        val availableTaskTypes = TaskType.getAvailableTaskTypes(coordinator.planSettings)
          .joinToString("\n                ") { "- ${it.name}" }
        val relatedTasksContext = goal.tasks?.mapNotNull { taskMap[it] }
          ?.filter { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
          ?.takeIf { it.isNotEmpty() }
          ?.joinToString("\n                ") {
            "  - Task ${it.id} (${it.description?.take(50)}...): ${it.status}, Result: ${it.result?.take(100)?.replace("\n", " ") ?: "N/A"}..."
          }?.indent("  ") // Indent the context block
        var promptStr = """
                Given the following goal, decide whether it can be directly addressed by a task, or if it should be broken down into subgoals.
                If the goal is sufficiently concrete, identify the next executable task(s) for this goal.
                If the goal is still abstract or complex, identify subgoals that, when completed, will achieve the parent goal.
                For each subgoal and task, list any *external* prerequisite goal or task IDs in their 'dependencies' list. Do not list the parent goal ID as a dependency.
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
      model = coordinator.planSettings.defaultModel,
      parsingModel = coordinator.planSettings.parsingModel,
      temperature = coordinator.planSettings.temperature,
      describer = describer
    )
    val inputMessages = mutableListOf(goal.description ?: "")
    // Add context data including the focus goal
    inputMessages.addAll(contextData(goal.id, null))

    val answer = parsedActor.answer(inputMessages, api)
    val subgoals = answer.obj.subgoals?.map { sg ->
      sg.copy(
        id = sg.id?.takeIf { it.isNotBlank() } ?: "G${goalIdCounter.getAndIncrement()}",
        description = sg.description,
        status = sg.status ?: (if (sg.dependencies?.isEmpty() != false) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT),
        parentGoalId = goal.id,
        subgoals = sg.subgoals ?: mutableListOf(),
        tasks = sg.tasks ?: mutableListOf(),
        dependencies = sg.dependencies ?: mutableListOf(),
        decompositionAttempted = sg.decompositionAttempted ?: false,
        result = sg.result
      )
    } ?: emptyList()
    val tasks = answer.obj.tasks?.map { t ->
      t.copy(
        id = t.id?.takeIf { it.isNotBlank() } ?: "T${taskIdCounter.getAndIncrement()}",
        description = t.description,
        status = t.status ?: (if (t.dependencies?.isEmpty() != false) TaskStatus.PENDING else TaskStatus.ACTIVE_DEPENDENCY_WAIT),
        parentGoalId = goal.id,
        dependencies = t.dependencies ?: mutableListOf(),
        result = t.result
      )
    } ?: emptyList()

    return Pair(subgoals, tasks)
  }


  private fun executeTask(
    taskDefinition: Task,
    api: ChatClient,
    uiTask: SessionTask,
    coordinator: PlanCoordinator
  ): String {
    val api = api.getChildClient(uiTask)
    val availableTaskTypes = TaskType.getAvailableTaskTypes(coordinator.planSettings)
    val parsedActor = ParsedActor(
      name = "TaskTypeChooser",
      resultClass = AutoPlanMode.Tasks::class.java, // Parse directly into TaskConfigBase
      exampleInstance = AutoPlanMode.Tasks(
        mutableListOf(
          TaskType.getAvailableTaskTypes(planSettings).firstOrNull()?.let {
            TaskType.getImpl(planSettings, it).taskConfig
          }
        ).filterNotNull().toMutableList()
      ),
      prompt = """
                Given the following task description and context, choose the single most appropriate task type and provide all required details.
                Task Description: ${taskDefinition.description}
                Available task types (and their schemas):
                ${availableTaskTypes.joinToString("\n") { it.name }}
            """.trimIndent(),
      model = coordinator.planSettings.defaultModel,
      parsingModel = planSettings.parsingModel,
      temperature = planSettings.temperature,
      describer = describer,
      parserPrompt = ("Task Subtype Schema:\n" + availableTaskTypes
        .joinToString("\n\n") { taskType ->
          "${taskType.name}:\n  ${
            describer.describe(taskType.taskDataClass).trim().trimIndent().indent("  ")
          }".trim()
        })
    )
    val answer = parsedActor.answer(
      listOf(taskDefinition.description ?: "") + contextData(taskDefinition.parentGoalId, taskDefinition.id), // Pass focused context
      api
    )
    val result = StringBuilder()

    TaskType.getImpl(planSettings, planTask= answer.obj.tasks?.firstOrNull()).run(
      agent = coordinator,
      messages = listOf(taskDefinition.description ?: "") + contextData(),
      task = uiTask,
      api = api,
      resultFn = { result.append(it) }, // Capture task output
      api2 = api2,
      planSettings = planSettings,
    )
    taskDefinition.result = result.toString()
    return result.toString()
  }


  private fun updateAllStatuses() {
    var changed: Boolean
    do {

      val initialTaskStatuses = taskMap.mapValues { it.value.status }
      val initialGoalStatuses = goalTree.mapValues { it.value.status }
      changed = false

      taskMap.values.forEach { task ->
        if (task.status!! != TaskStatus.COMPLETED && task.status!! != TaskStatus.FAILED && task.status!! != TaskStatus.RUNNING) {
          val newStatus = if (areDependenciesMet(task)) {
            TaskStatus.PENDING // Dependencies met, ready to run
          } else {

            TaskStatus.ACTIVE_DEPENDENCY_WAIT // Waiting for dependencies
          }
          if (task.status!! != newStatus) {
            task.status = newStatus

          }
        }
      }
      if (taskMap.any { initialTaskStatuses[it.key] != it.value.status }) changed = true

      goalTree.values.forEach { goal ->
        var newStatus = goal.status

        if (goal.status!! != GoalStatus.COMPLETED && goal.status!! != GoalStatus.BLOCKED) {
          val dependenciesMet = areDependenciesMet(goal)
          val subGoals = goal.subgoals!!.mapNotNull { goalTree[it] }
          val directTasks = goal.tasks!!.mapNotNull { taskMap[it] }

          val blockingDependency = goal.dependencies!!.firstOrNull { depId -> goalTree[depId]?.status == GoalStatus.BLOCKED }
          val blockingSubGoal = subGoals.firstOrNull { it.status!! == GoalStatus.BLOCKED }
          if (blockingDependency != null || blockingSubGoal != null) {
            newStatus = GoalStatus.BLOCKED
            goal.result =
              goal.result ?: "Blocked by dependency Goal ID: $blockingDependency (${goalTree[blockingDependency]?.description?.take(50) ?: "N/A"}...)"
          } else {
            val blockingSubGoal = subGoals.firstOrNull { it.status!! == GoalStatus.BLOCKED }
            val failedTask = directTasks.firstOrNull { it.status!! == TaskStatus.FAILED }
            if (dependenciesMet && (blockingSubGoal != null || failedTask != null)) {
              newStatus = GoalStatus.BLOCKED // Blocked by a failed/blocked child
              val reason = if (blockingSubGoal != null) {
                "sub-goal ID: ${blockingSubGoal.id!!} (${blockingSubGoal.description?.take(50) ?: "N/A"}...) is BLOCKED"
              } else {
                "task ID: ${failedTask!!.id!!} (${failedTask.description?.take(50) ?: "N/A"}...) is FAILED"
              }
              goal.result = goal.result ?: "Blocked because $reason."

            } else if (dependenciesMet && goal.decompositionAttempted!! && subGoals.isEmpty() && directTasks.isEmpty()) {
              newStatus = GoalStatus.BLOCKED // Decomposed into nothing actionable
              goal.result = goal.result ?: "Decomposition yielded no actionable sub-goals or tasks, and dependencies are met."
            }
          }

          if (newStatus!! != GoalStatus.BLOCKED && dependenciesMet &&
            (goal.decompositionAttempted!! || subGoals.isNotEmpty() || directTasks.isNotEmpty()) &&
            subGoals.all { it.status!! == GoalStatus.COMPLETED } &&
            directTasks.all { it.status!! == TaskStatus.COMPLETED }
          ) {
            newStatus = GoalStatus.COMPLETED
            goal.result = goal.result ?: "All sub-goals and tasks completed."
          } else if (newStatus != GoalStatus.BLOCKED && newStatus != GoalStatus.COMPLETED && !dependenciesMet) { // Still waiting for external dependencies
           newStatus = GoalStatus.ACTIVE_DEPENDENCY_WAIT
          } else if (newStatus != GoalStatus.BLOCKED && newStatus != GoalStatus.COMPLETED) { // Dependencies met, not blocked, not completed
            newStatus = GoalStatus.ACTIVE
          }

          if (goal.status!! != newStatus) {
            goal.status = newStatus

          }
        }
      }
      if (!changed && goalTree.any { initialGoalStatuses[it.key] != it.value.status }) changed = true
      if (!changed && taskMap.any { initialTaskStatuses[it.key] != it.value.status }) changed = true // Re-check task status changes

    } while (changed)
  }

  private fun areDependenciesMet(item: Goal): Boolean {
    if (item.dependencies!!.isEmpty()) return true
    return item.dependencies.all { depId ->
      goalTree[depId]?.status!! == GoalStatus.COMPLETED
    }
  }

  private fun areDependenciesMet(item: Task): Boolean {
    if (item.dependencies!!.isEmpty()) return true
    return item.dependencies.all { depId ->
      (goalTree[depId]?.status == GoalStatus.COMPLETED) || (taskMap[depId]?.status == TaskStatus.COMPLETED) // Dependency can be a Goal or a Task
    }
  }


  private fun renderGoalTreeText(goals: List<Goal>): String {
    val sb = StringBuilder("### Goal Tree Status\n")

    fun renderNode(goal: Goal, indentStr: String, visited: MutableSet<String>) {
      val statusEmoji = when (goal.status!!) {
        GoalStatus.ACTIVE -> "🟢 Active"
        GoalStatus.BLOCKED -> "🧱 Blocked"
        GoalStatus.COMPLETED -> "✅ Completed"
        GoalStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳ Waiting (Deps)"
      }
      val depsString = goal.dependencies!!.joinToString(",") { it.take(4) }
      sb.append("$indentStr- $statusEmoji **${goal.description ?: "N/A"}** (ID: ${goal.id!!}, Deps: $depsString)\n")
      if (goal.result != null) {
        sb.append("$indentStr  Result: ${goal.result?.replace("\n", "\n$indentStr  ")?.take(200)}...\n")
      }
      goal.tasks!!.mapNotNull { taskMap[it] }.forEach { t ->
        val taskStatusEmoji = when (t.status!!) {
          TaskStatus.PENDING -> "📝 Pending"
          TaskStatus.RUNNING -> "🏃 Running"
          TaskStatus.COMPLETED -> "✔️ Completed"
          TaskStatus.FAILED -> "❌ Failed"
          TaskStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳ Waiting (Deps)"
        }
        val taskDepsString = t.dependencies!!.joinToString(",") { it.take(4) }
        sb.append("$indentStr  - Task $taskStatusEmoji ${t.description ?: "N/A"} (ID: ${t.id!!}, Deps: $taskDepsString)\n")
        if (t.result != null) {
          sb.append("$indentStr    Result: ${t.result?.replace("\n", "\n$indentStr    ")?.take(200)}...\n")
        }
      }
      goal.subgoals!!.mapNotNull { goalTree[it] }.forEach { subGoal ->
        renderNode(subGoal, indentStr + "  ", visited)
      }
    }


    val rootGoalIds = goals.mapNotNull { it.id }.toSet()
    val roots = goals.filter { it.parentGoalId == null || !rootGoalIds.contains(it.parentGoalId) }.sortedBy { it.id }
    if (roots.isEmpty() && goals.isNotEmpty()) {
      goals.sortedBy { it.id!! }.forEach { renderNode(it, "", mutableSetOf()) } // Start new traversal for each potential root in fallback
    } else {
      roots.sortedBy { it.id!! }.forEach { renderNode(it, "", mutableSetOf()) } // Start new traversal for each root
    }
    return sb.toString()
  }

  override fun contextData(): List<String> {
    val contextLines = mutableListOf<String>()
    contextLines.add("Current Goal-Oriented Plan State:")
    val llmContextSb = StringBuilder()
    fun renderNodeForLlm(goal: Goal, indent: Int, visited: MutableSet<String>) {
      val goalDeps = goal.dependencies!!.joinToString(",")
      llmContextSb.append("${"  ".repeat(indent)}- G(${goal.id!!}): ${goal.description ?: "N/A"} [${goal.status!!}] (Deps: $goalDeps)\n")
      goal.tasks!!.mapNotNull { taskMap[it] }.forEach { t ->
        val taskDeps = t.dependencies!!.joinToString(",")
        llmContextSb.append("${"  ".repeat(indent + 1)}- T(${t.id!!}): ${t.description ?: "N/A"} [${t.status!!}] (Deps: $taskDeps)\n")
      }
      goal.subgoals!!.mapNotNull { goalTree[it] }.forEach { subGoal ->

        if (visited.add(subGoal.id!!)) { // Prevent infinite loops in case of cycles (though cycles aren't explicitly handled)
          renderNodeForLlm(subGoal, indent + 1, visited)
        } else {
          llmContextSb.append("${"  ".repeat(indent + 1)}- G(${subGoal.id}): ... (cycle detected or already rendered)\n")
        }
      }
    }
    val rootsForLlm = goalTree.values.filter { it.parentGoalId == null || !goalTree.containsKey(it.parentGoalId) }.sortedBy { it.id!! } // Consider nodes without known parents as roots
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
    fun renderNodeForLlm(goal: Goal, indent: Int) {
      val goalDeps = goal.dependencies!!.joinToString(",")
      llmContextSb.append("${"  ".repeat(indent)}- G(${goal.id!!}): ${goal.description ?: "N/A"} [${goal.status!!}] (Deps: $goalDeps)\n")
      goal.tasks!!.mapNotNull { taskMap[it] }.forEach { t ->
        val taskDeps = t.dependencies!!.joinToString(",")
        // Add task result if available and relevant (e.g., for completed/failed tasks)
        val taskResultSnippet = t.result?.take(50)?.replace("\n", " ")
        if (!taskResultSnippet.isNullOrBlank()) llmContextSb.append("${"  ".repeat(indent + 1)}  Result: $taskResultSnippet...\n")
        llmContextSb.append("${"  ".repeat(indent + 1)}- T(${t.id!!}): ${t.description ?: "N/A"} [${t.status!!}] (Deps: $taskDeps)\n")
      }
      goal.subgoals!!.mapNotNull { goalTree[it] }.forEach { subGoal ->
        renderNodeForLlm(subGoal, indent + 1)
      }
    }

    val rootsForLlm = goalTree.values.filter { it.parentGoalId == null || !goalTree.containsKey(it.parentGoalId) }.sortedBy { it.id!! } // Consider nodes without known parents as roots
    rootsForLlm.forEach { renderNodeForLlm(it, 0) }
    contextLines.add(llmContextSb.toString())
    return contextLines
  }

  @Description("A goal in the goal-oriented planning system.")
  data class Goal(
    val id: String? = null,
    val description: String? = null,
    var status: GoalStatus? = GoalStatus.ACTIVE_DEPENDENCY_WAIT,
    val parentGoalId: String? = null,
    val subgoals: MutableList<String>? = mutableListOf(),
    val tasks: MutableList<String>? = mutableListOf(),
    val dependencies: MutableList<String>? = mutableListOf(),
    var decompositionAttempted: Boolean? = false,
    var result: String? = null
  )

  @Description("A task that can be executed to achieve a goal.")
  data class Task(
    val id: String? = null,
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
    ACTIVE_DEPENDENCY_WAIT
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
    ACTIVE_DEPENDENCY_WAIT
  }

  @Description("A list of goals (for LLM parsing).")
  data class GoalList(
    val goals: List<Goal>? = null
  )

  @Description("Result of decomposing a goal into subgoals and tasks.")
  data class GoalDecomposition(
    val subgoals: List<Goal>? = null,
    val tasks: List<Task>? = null
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
    ) = GoalOrientedMode(ui, api, planSettings, session, user, api2, describer)
  }
}