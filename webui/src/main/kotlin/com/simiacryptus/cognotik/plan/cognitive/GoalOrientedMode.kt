package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.plan.PlanCoordinator
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.TaskType
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
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Goal-Oriented Cognitive Mode:
 * Maintains a dynamic set of goals and subgoals, decomposes them, and executes tasks in parallel.
 */
open class GoalOrientedMode(
  override val ui: ApplicationInterface,
  override val api: API,
  override val planSettings: PlanSettings,
  override val session: Session,
  override val user: User?,
  private val api2: OpenAIClient,
  val describer: TypeDescriber,
  private val maxConcurrency: Int = 4,
  private val maxIterations: Int = 20 // Added maxIterations
) : CognitiveMode {
  private val log = LoggerFactory.getLogger(GoalOrientedMode::class.java)
  private val goalIdCounter = AtomicInteger(1)
  private val taskIdCounter = AtomicInteger(1)
  private val isRunning = AtomicBoolean(false)
  private val stopRequested = AtomicBoolean(false) // Added stopRequested

  // In-memory state for the session
  private val goalTree = ConcurrentHashMap<String, Goal>() // Goal.id -> Goal
  private val taskMap = ConcurrentHashMap<String, Task>()   // Task.id -> Task

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
      task.add(renderMarkdown("Goal-Oriented Mode is already running. Please wait for the current session to complete or stop it.", ui = ui))
      return
    }
    stopRequested.set(false) // Reset stop flag for new session
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
    val mainTab = TabbedDisplay(task)
    task.echo(renderMarkdown("User: $userMessage", ui = ui)) // Echo user message
    val stopLinkRef = AtomicReference<StringBuilder>()
    val goalTreeTask = ui.newTask(false).apply { mainTab["Goal Tree"] = placeholder }
    val stopLink = task.add(ui.hrefLink("Stop Goal-Oriented Processing") {
      log.info("Stop requested by user.")
      stopRequested.set(true)
      stopLinkRef.get()?.set("Stop signal sent. Waiting for current iteration to finish...")
    })
    stopLinkRef.set(stopLink!!)

    val executor = ui.socketManager?.pool ?: throw IllegalStateException("SocketManager or its pool is null")
    val processor = FixedConcurrencyProcessor(executor, maxConcurrency)
    val apiClient = (api as? ChatClient)?.getChildClient(task) ?: throw IllegalStateException("API must be a ChatClient")
    apiClient.budget = planSettings.budget
    val sessionLog = StringBuilder()
    val sessionLogTask = ui.newTask(false).apply { mainTab["Session Log"] = placeholder }
    fun logToSession(message: String) {
      log.info(message)
      sessionLog.append(message).append("\n")
      sessionLogTask.complete(renderMarkdown(sessionLog.toString(), ui = ui))
    }
    logToSession("Starting Goal-Oriented session for: $userMessage")


    // 1. Initialization: Parse initial goal(s)
    try {
      val initialGoals = parseInitialGoals(userMessage, apiClient)
      if (initialGoals.isEmpty()) {
        logToSession("No initial goals parsed. Aborting.")
        task.complete(renderMarkdown("Could not determine initial goals from your request.", ui = ui))
        return
      }
      initialGoals.forEach { goal ->
        goalTree[goal.id] = goal // Status will be ACTIVE by default if no deps
      }
      logToSession("Parsed ${initialGoals.size} initial goal(s).")
    } catch (e: Exception) {
      log.error("Failed to parse initial goals", e)
      logToSession("Error parsing initial goals: ${e.message}")
      task.error(ui, e)
      return
    }


    goalTreeTask.add(renderMarkdown(renderGoalTreeText(goalTree.values.toList()), ui = ui))
    // Initial render

    // 2. Goal Processing Loop
    var iteration = 0
    while (iteration < maxIterations && !stopRequested.get()) {
      if (stopRequested.get()) break
      iteration++
      logToSession("\n## Iteration $iteration / $maxIterations")
      goalTreeTask.add(renderMarkdown(renderGoalTreeText(goalTree.values.toList()), ui = ui))
      updateAllStatuses()
      val decomposableGoals = goalTree.values.filter {
        it.status == GoalStatus.ACTIVE && !it.decompositionAttempted && it.subgoals.isEmpty() && it.tasks.isEmpty()
      }

      if (decomposableGoals.isNotEmpty()) {
        logToSession("Found ${decomposableGoals.size} goal(s) to decompose:")
        decomposableGoals.forEach { logToSession("- Goal ID ${it.id}: ${it.description}") }
      }

      for (goal in decomposableGoals) {
        if (stopRequested.get()) break
        logToSession("Decomposing goal: ${goal.description} (ID: ${goal.id})")
        try {
          val (subgoals, tasksForGoal) = decomposeGoal(goal, apiClient)
          goal.decompositionAttempted = true
          if (subgoals.isEmpty() && tasksForGoal.isEmpty()) {
            logToSession("Goal ID ${goal.id} (${goal.description}) decomposed into no subgoals or tasks.")
            // Status will be updated by updateAllStatuses if it truly becomes blocked
          } else {
            subgoals.forEach { subgoal ->
              // Check for duplicate ID before adding
              if (!goalTree.containsKey(subgoal.id)) {
                goalTree[subgoal.id] = subgoal
                logToSession("  New subgoal: ${subgoal.description} (ID: ${subgoal.id}) for Goal ${goal.id}")
              } else {
                logToSession("  Subgoal ID ${subgoal.id} already exists. Skipping addition.")
              }
              goal.subgoals.add(subgoal.id)
            }
            tasksForGoal.forEach { t ->
              if (!taskMap.containsKey(t.id)) {
                taskMap[t.id] = t
                logToSession("  New task: ${t.description} (ID: ${t.id}) for Goal ${goal.id}")
              } else {
                logToSession("  Task ID ${t.id} already exists. Skipping addition.")
              }
              goal.tasks.add(t.id)
            }
          }
        } catch (e: Exception) {
          log.error("Error decomposing goal ${goal.id}", e)
          logToSession("Error decomposing goal ${goal.id}: ${e.message}. Marking as BLOCKED.")
          goal.status = GoalStatus.BLOCKED // Mark problematic goal as blocked
          goal.result = "Failed to decompose: ${e.message}"
        }
        updateAllStatuses() // Update statuses after decomposition
        goalTreeTask.add(renderMarkdown(renderGoalTreeText(goalTree.values.toList()), ui = ui))
      }

      if (stopRequested.get()) break

      updateAllStatuses() // Ensure statuses are fresh before task execution phase

      val executableTasks = taskMap.values.filter { it.status == TaskStatus.PENDING } // updateAllStatuses should have set PENDING if dependencies met

      if (executableTasks.isNotEmpty()) {
        logToSession("Found ${executableTasks.size} task(s) to execute:")

        executableTasks.forEach { logToSession("- Task ID ${it.id}: ${it.description}") }

        val taskExecutionJobs = mutableListOf<Pair<Task, Future<String?>>>()
        executableTasks.forEach { t ->
          if (stopRequested.get()) return@forEach
          t.status = TaskStatus.RUNNING
          logToSession("Executing Task ID ${t.id} (${t.description})")
          goalTreeTask.add(renderMarkdown(renderGoalTreeText(goalTree.values.toList()), ui = ui))
          // Update UI to show task as RUNNING
          val future = processor.submit<String?> {
            try {
              val executionUiTask = ui.newTask(false)
              val taskResult = executeTask(t, apiClient.getChildClient(executionUiTask), executionUiTask)
              taskResult // Return only the result string
            } catch (e: Exception) {
              log.error("Task ID ${t.id} (${t.description}) execution failed in processor.submit lambda", e)
              // Update the original task object in the map
              taskMap[t.id]?.apply {
                status = TaskStatus.FAILED
                result = "Execution Error: ${e.message}"
              }
              throw e // Re-throw so future.get() catches it as ExecutionException
            }

          }
          taskExecutionJobs.add(Pair(t, future))
        }

        for ((taskInstance, future) in taskExecutionJobs) {
          if (stopRequested.get()) {
            logToSession("Stop requested, not waiting for all tasks to complete this iteration.")
            // Optionally, attempt to cancel remaining futures if the processor supports it
            // For now, just break the loop of waiting for results.
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
            // If status is FAILED, it was set by the lambda's catch block. Log message handled there or below.
          } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("Task ID ${taskInstance.id} (${taskInstance.description}) interrupted.", e)
            taskInstance.status = TaskStatus.FAILED // Or a new status like INTERRUPTED
            taskInstance.result = "Task execution was interrupted."
            logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) INTERRUPTED.")
          } catch (e: Exception) {


            val cause = if (e is java.util.concurrent.ExecutionException) e.cause ?: e else e
            log.error("Task ID ${taskInstance.id} (${taskInstance.description}) failed or error retrieving result.", cause)
            if (taskInstance.status != TaskStatus.FAILED) { // Ensure status is FAILED
              taskInstance.status = TaskStatus.FAILED
              taskInstance.result = "Execution Error: ${cause.message}"
            }
            logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) FAILED. Reason: ${taskInstance.result}")
          }
        }
      } else {
        logToSession("No executable tasks in this iteration.")
      }

      updateAllStatuses() // Final status update for the iteration
      goalTreeTask.add(renderMarkdown(renderGoalTreeText(goalTree.values.toList()), ui = ui))

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

    goalTreeTask.add(renderMarkdown(renderGoalTreeText(goalTree.values.toList()), ui = ui))
    // Final render
    if (stopRequested.get()) {
      logToSession("Goal-Oriented session stopped by user request at iteration $iteration.")
      task.complete(renderMarkdown("Session stopped by user.", ui = ui))
      stopLink.set("Stopped")
    } else if (iteration >= maxIterations) {
      logToSession("Goal-Oriented session reached max iterations ($maxIterations).")
      task.complete(renderMarkdown("Session reached max iterations.", ui = ui))
      stopLink.set("Max Iterations Reached")
    } else {
      val finalStatusSummary = goalTree.values.groupBy { it.status }.mapValues { it.value.size }.toString()
      logToSession("Goal-Oriented session completed. Final status: $finalStatusSummary")
      task.complete(renderMarkdown("Session completed. Final Status: $finalStatusSummary", ui = ui))
      stopLink.set("Completed")
    }
    sessionLogTask.complete(renderMarkdown(sessionLog.toString(), ui = ui)) // Ensure final log is displayed
  }

  private fun parseInitialGoals(userMessage: String, api: ChatClient): List<Goal> {
    // Use LLM to parse initial goals from user message
    val parsedActor = ParsedActor(
      name = "GoalParser",
      resultClass = GoalList::class.java,
      exampleInstance = GoalList(
        listOf(
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
    val goals = answer.obj.goals
    if (goals.isNullOrEmpty()) {
      // Fallback: create a single goal from the user message
      return listOf(
        Goal(
          id = "G${goalIdCounter.getAndIncrement()}",
          description = userMessage,
          status = GoalStatus.ACTIVE,
          parentGoalId = null,
          subgoals = mutableListOf(),
          tasks = mutableListOf(),
          dependencies = mutableListOf()
        )
      )
    }
    // Assign unique IDs if missing
    return goals.map { g ->
      g.copy(
        id = g.id.ifBlank { "G${goalIdCounter.getAndIncrement()}" },
        status = if (g.dependencies.isEmpty()) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT,
        subgoals = g.subgoals,
        tasks = g.tasks,
        dependencies = g.dependencies
      )
    }
  }

  /**
   * Decompose a goal into subgoals and/or tasks using LLM.
   * Returns Pair(subgoals, tasks)
   */
  private fun decomposeGoal(goal: Goal, api: ChatClient): Pair<List<Goal>, List<Task>> {
    val parsedActor = ParsedActor(
      name = "GoalDecomposer",
      resultClass = GoalDecomposition::class.java,
      exampleInstance = GoalDecomposition(
        subgoals = listOf(
          Goal(
            id = "G2",
            description = "Design API endpoint",
            parentGoalId = goal.id,
            subgoals = mutableListOf(),
            tasks = mutableListOf(),
            dependencies = mutableListOf() // Example: If this subgoal depends on an external Goal "G_Ext", add "G_Ext" here.
          )
        ),
        tasks = listOf(
          Task(
            id = "T1",
            description = "Draft OpenAPI spec for upload endpoint",
            parentGoalId = goal.id,
            dependencies = mutableListOf() // Example: If this task depends on an external Task "T_Ext", add "T_Ext" here.
          )
        )
      ),
      prompt = """
                Given the following goal, decide whether it can be directly addressed by a task, or if it should be broken down into subgoals.
                If the goal is sufficiently concrete, identify the next executable task(s) for this goal.
                If the goal is still abstract or complex, identify subgoals that, when completed, will achieve the parent goal.
                For each subgoal and task, list any *external* prerequisite goal or task IDs in their 'dependencies' list. Do not list the parent goal ID as a dependency.
                Return a list of subgoals and/or tasks.
                Goal:
                ${goal.description}
            """.trimIndent(),
      model = planSettings.defaultModel,
      parsingModel = planSettings.parsingModel,
      temperature = planSettings.temperature,
      describer = describer
    )
    val answer = parsedActor.answer(listOf(goal.description), api)
    val subgoals = answer.obj.subgoals?.map { sg ->
      sg.copy(
        id = sg.id.ifBlank { "G${goalIdCounter.getAndIncrement()}" },
        status = if (sg.dependencies.isEmpty()) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT,
        parentGoalId = goal.id,
        subgoals = sg.subgoals,
        tasks = sg.tasks,
        dependencies = sg.dependencies
      )
    } ?: emptyList()
    val tasks = answer.obj.tasks?.map { t ->
      t.copy(
        id = t.id.ifBlank { "T${taskIdCounter.getAndIncrement()}" },
        status = if (t.dependencies.isEmpty()) TaskStatus.PENDING else TaskStatus.ACTIVE_DEPENDENCY_WAIT,
        parentGoalId = goal.id,
        dependencies = t.dependencies
      )
    } ?: emptyList()
    return Pair(subgoals, tasks)
  }

  /**
   * Executes a single task and returns the result string.
   */
  private fun executeTask(taskDefinition: Task, api: ChatClient, uiTask: SessionTask): String {
    val coordinator = PlanCoordinator(
      user = user,
      session = session,
      dataStorage = ui.socketManager?.dataStorage!!,
      ui = ui,
      root = planSettings.absoluteWorkingDir?.let { File(it).toPath() }
        ?: ui.socketManager!!.dataStorage?.getDataDir(user, session)?.toPath() ?: File(".").toPath(),
      planSettings = planSettings
    )
    // Use LLM to select the most appropriate TaskType and config for this task
    val availableTaskTypes = TaskType.getAvailableTaskTypes(planSettings)
    val parsedActor = ParsedActor(
      name = "TaskTypeChooser",
      resultClass = AutoPlanMode.Tasks::class.java,
      exampleInstance = AutoPlanMode.Tasks(
        mutableListOf(
          TaskType.getAvailableTaskTypes(planSettings).firstOrNull()?.let {
            TaskType.getImpl(planSettings, it).taskConfig
          }
        ).filterNotNull().toMutableList()
      ),
      prompt = """
                Given the following task description, choose the most appropriate task type and provide all required details.
                Task Description: ${taskDefinition.description}
                Available task types:
                ${availableTaskTypes.joinToString("\n") { it.name }}
            """.trimIndent(),
      model = planSettings.defaultModel,
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
    val answer = parsedActor.answer(listOf(taskDefinition.description) + contextData(), api)
    val chosenTaskConfig = answer.obj.tasks?.firstOrNull()
      ?: throw IllegalStateException("No task config selected for: ${taskDefinition.description}")

    val result = StringBuilder()
    // Actually run the task
    TaskType.getImpl(planSettings, chosenTaskConfig).run(
      agent = coordinator,
      messages = listOf(taskDefinition.description) + contextData(), // Add context
      task = uiTask, // Use the specific uiTask for this execution
      api = api,
      resultFn = { result.append(it) },
      api2 = api2,
      planSettings = planSettings,
    )
    taskDefinition.result = result.toString() // Store result in the task object
    return result.toString()
  }

  /**
   * Iteratively updates statuses of all goals and tasks based on dependencies and completions.
   */
  private fun updateAllStatuses() {
    var changed: Boolean
    do {
      // Capture initial statuses to detect changes accurately within one pass for a type (goal/task)
      val initialTaskStatuses = taskMap.mapValues { it.value.status }
      val initialGoalStatuses = goalTree.mapValues { it.value.status }
      changed = false

      // Update Task Statuses first
      taskMap.values.forEach { task ->
        if (task.status != TaskStatus.COMPLETED && task.status != TaskStatus.FAILED && task.status != TaskStatus.RUNNING) {
          val newStatus = if (areDependenciesMet(task)) {
            TaskStatus.PENDING
          } else {
            // If any dependency is terminally failed/blocked, this task might also be considered blocked.
            // For now, just mark as waiting if dependencies are not met.
            TaskStatus.ACTIVE_DEPENDENCY_WAIT
          }
          if (task.status != newStatus) {
            task.status = newStatus
            // changed will be set outside this loop based on initialTaskStatuses
          }
        }
      }
      if (taskMap.any { initialTaskStatuses[it.key] != it.value.status }) changed = true

      // Update Goal Statuses
      goalTree.values.forEach { goal ->
        var newStatus = goal.status // Start with current status

        if (goal.status == GoalStatus.COMPLETED || goal.status == GoalStatus.BLOCKED) {
          // Already in a terminal state, skip
        } else {
          val dependenciesMet = areDependenciesMet(goal)
          val subGoals = goal.subgoals.mapNotNull { goalTree[it] }
          val tasksOfGoal = goal.tasks.mapNotNull { taskMap[it] }

          // Check for conditions that lead to BLOCKED
          val blockingDependency = goal.dependencies.firstOrNull { depId -> goalTree[depId]?.status == GoalStatus.BLOCKED }
          if (blockingDependency != null) {
            newStatus = GoalStatus.BLOCKED
            goal.result = goal.result ?: "Blocked by dependency Goal ID: $blockingDependency (${goalTree[blockingDependency]?.description?.take(50)}...)"
          } else {
            val blockingSubGoal = subGoals.firstOrNull { it.status == GoalStatus.BLOCKED }
            val failedTask = tasksOfGoal.firstOrNull { it.status == TaskStatus.FAILED }
            if (dependenciesMet && (blockingSubGoal != null || failedTask != null)) {
              newStatus = GoalStatus.BLOCKED
              val reason = if (blockingSubGoal != null) {
                "sub-goal ID: ${blockingSubGoal.id} (${blockingSubGoal.description.take(50)}...) is BLOCKED"
              } else { // failedTask must be non-null
                "task ID: ${failedTask!!.id} (${failedTask.description.take(50)}...) is FAILED"
              }
              goal.result = goal.result ?: "Blocked because $reason."
            } else if (dependenciesMet && goal.decompositionAttempted && subGoals.isEmpty() && tasksOfGoal.isEmpty()) {
              // Decomposed into nothing, and no tasks are running/pending for it
              newStatus = GoalStatus.BLOCKED
              goal.result = goal.result ?: "Decomposition yielded no actionable sub-goals or tasks, and dependencies are met."
            }
          }
          // Check for conditions that lead to COMPLETED (only if not already decided to be BLOCKED)
          // newStatus check ensures we don't override a BLOCKED status decided above in this pass
          if (newStatus != GoalStatus.BLOCKED && dependenciesMet &&
            (goal.decompositionAttempted || subGoals.isNotEmpty() || tasksOfGoal.isNotEmpty()) && // Must have content or tried
            subGoals.all { it.status == GoalStatus.COMPLETED } &&
            tasksOfGoal.all { it.status == TaskStatus.COMPLETED }
          ) {
            newStatus = GoalStatus.COMPLETED
            goal.result = goal.result ?: "All sub-goals and tasks completed."
          }
          // Check for ACTIVE_DEPENDENCY_WAIT
          else if (newStatus != GoalStatus.BLOCKED && newStatus != GoalStatus.COMPLETED && !dependenciesMet) {
            newStatus = GoalStatus.ACTIVE_DEPENDENCY_WAIT
          }
          // Otherwise, if dependencies are met and not completed/blocked, it's ACTIVE
          else if (newStatus != GoalStatus.BLOCKED && newStatus != GoalStatus.COMPLETED) {
            newStatus = GoalStatus.ACTIVE
          }

          if (goal.status != newStatus) {
            goal.status = newStatus
            // changed will be set outside this loop
          }
        }
      }
      if (!changed && goalTree.any { initialGoalStatuses[it.key] != it.value.status }) changed = true

    } while (changed) // Loop until no more status changes propagate
  }

  private fun areDependenciesMet(item: Goal): Boolean {
    if (item.dependencies.isEmpty()) return true
    return item.dependencies.all { depId ->
      goalTree[depId]?.status == GoalStatus.COMPLETED
    }
  }

  private fun areDependenciesMet(item: Task): Boolean {
    if (item.dependencies.isEmpty()) return true
    return item.dependencies.all { depId ->
      (goalTree[depId]?.status == GoalStatus.COMPLETED) || (taskMap[depId]?.status == TaskStatus.COMPLETED)
    }
  }

  /**
   * Renders the goal tree as markdown for display in the UI.
   */
  private fun renderGoalTreeText(goals: List<Goal>): String { // Renamed from renderGoalTree
    val sb = StringBuilder("### Goal Tree Status\n")
    fun renderNode(goal: Goal, indentStr: String) {
      val statusEmoji = when (goal.status) {
        GoalStatus.ACTIVE -> "🟢 Active"
        GoalStatus.BLOCKED -> "🧱 Blocked"
        GoalStatus.COMPLETED -> "✅ Completed"
        GoalStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳ Waiting (Deps)"
      }
      sb.append("$indentStr- $statusEmoji **${goal.description}** (ID: ${goal.id}, Deps: ${goal.dependencies.joinToString(",") { it.take(4) }})\n")
      if (goal.result != null) {
        sb.append("$indentStr  Result: ${goal.result?.replace("\n", "\n$indentStr  ")?.take(200)}...\n")
      }
      goal.tasks.mapNotNull { taskMap[it] }.forEach { t ->
        val taskStatusEmoji = when (t.status) {
          TaskStatus.PENDING -> "📝 Pending"
          TaskStatus.RUNNING -> "🏃 Running"
          TaskStatus.COMPLETED -> "✔️ Completed"
          TaskStatus.FAILED -> "❌ Failed"
          TaskStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳ Waiting (Deps)"
        }
        sb.append("$indentStr  - Task $taskStatusEmoji ${t.description} (ID: ${t.id}, Deps: ${t.dependencies.joinToString(",") { it.take(4) }})\n")
        if (t.result != null) {
          sb.append("$indentStr    Result: ${t.result?.replace("\n", "\n$indentStr    ")?.take(200)}...\n")
        }
      }
      goal.subgoals.mapNotNull { goalTree[it] }.forEach { subGoal ->
        renderNode(subGoal, indentStr + "  ")
      }
    }
    // Filter for root goals (those without a parent or whose parent isn't in the current tree - robust for partial trees)
    val rootGoalIds = goals.map { it.id }.toSet()
    val roots = goals.filter { it.parentGoalId == null || !rootGoalIds.contains(it.parentGoalId) }.sortedBy { it.id }
    if (roots.isEmpty() && goals.isNotEmpty()) { // Handle cases where all goals might have parent IDs but form separate trees
      goals.sortedBy { it.id }.forEach { renderNode(it, "") }
    } else {
      roots.forEach { renderNode(it, "") }
    }
    return sb.toString()
  }

  override fun contextData(): List<String> {
    // Provide a summary of the current state for LLM context
    val contextLines = mutableListOf<String>()
    contextLines.add("Current Goal-Oriented Plan State:")
    // Use a simpler text representation for LLM context to save tokens
    val llmContextSb = StringBuilder()
    fun renderNodeForLlm(goal: Goal, indent: Int) {
      llmContextSb.append("${"  ".repeat(indent)}- G(${goal.id}): ${goal.description} [${goal.status}] (Deps: ${goal.dependencies.joinToString(",")})\n")
      goal.tasks.mapNotNull { taskMap[it] }.forEach { t ->
        llmContextSb.append("${"  ".repeat(indent + 1)}- T(${t.id}): ${t.description} [${t.status}] (Deps: ${t.dependencies.joinToString(",")})\n")
      }
      goal.subgoals.mapNotNull { goalTree[it] }.forEach { subGoal ->
        renderNodeForLlm(subGoal, indent + 1)
      }
    }

    val rootsForLlm = goalTree.values.filter { it.parentGoalId == null }.sortedBy { it.id }
    rootsForLlm.forEach { renderNodeForLlm(it, 0) }
    contextLines.add(llmContextSb.toString())
    return contextLines
  }

  // --- Data Classes ---
  @Description("A goal in the goal-oriented planning system.")
  data class Goal(
    val id: String,
    val description: String,
    var status: GoalStatus = GoalStatus.ACTIVE_DEPENDENCY_WAIT,
    val parentGoalId: String? = null,
    val subgoals: MutableList<String> = mutableListOf(), // IDs of subgoals
    val tasks: MutableList<String> = mutableListOf(),    // IDs of tasks
    val dependencies: MutableList<String> = mutableListOf(), // IDs of prerequisite goals/tasks
    var decompositionAttempted: Boolean = false,
    var result: String? = null
  )

  @Description("A task that can be executed to achieve a goal.")
  data class Task(
    val id: String,
    val description: String,
    var status: TaskStatus = TaskStatus.ACTIVE_DEPENDENCY_WAIT,
    val parentGoalId: String? = null, // ID of the goal this task directly contributes to
    var result: String? = null,
    val dependencies: MutableList<String> = mutableListOf() // IDs of prerequisite goals/tasks
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