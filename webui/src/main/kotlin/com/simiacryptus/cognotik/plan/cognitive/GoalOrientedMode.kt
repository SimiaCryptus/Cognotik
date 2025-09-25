package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.PlanCoordinator
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.cognitive.AutoPlanMode.Tasks
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


open class GoalOrientedMode(
    override val ui: SocketManager,
    override val planSettings: PlanSettings,
    override val session: Session,
    override val user: User?,
    val describer: TypeDescriber,
    private val maxConcurrency: Int = 4,
    private val maxIterations: Int = 20
) : CognitiveMode {
    private val goalIdCounter = AtomicInteger(1)
    private val taskIdCounter = AtomicInteger(1)
    private val isRunning = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val goalTree = ConcurrentHashMap<String, Goal>()
    private val taskMap = ConcurrentHashMap<String, Task>()
    private val goalTasks = ConcurrentHashMap<String, SessionTask>()
    private val taskTasks = ConcurrentHashMap<String, SessionTask>()
    private var updateGoalTreeUI: () -> Unit = {}
    private var debouncedUpdateGoalTreeUI: () -> Unit = {}
    private var periodicUpdateFuture: ScheduledFuture<*>? = null
    private val sessionLog = StringBuilder()
    private var sessionLogTask: SessionTask? = null
    fun logToSession(message: String) {
        log.info(message)
        sessionLog.append(message).append("\n")
        sessionLogTask?.complete(message.renderMarkdown())
    }
    val executor: ImmediateExecutorService = ui.pool ?: throw IllegalStateException("SocketManager or its pool is null")
    val processor: FixedConcurrencyProcessor = FixedConcurrencyProcessor(executor, maxConcurrency)

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
            task.error(e)
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

        val goalTreeTask = task.linkedTask("Goal Tree")
        val goalTreeElement = goalTreeTask.add("Loading...".renderMarkdown())
        updateGoalTreeUI = {
            goalTreeElement?.set(renderGoalTreeText(goalTree.values.toList()).renderMarkdown())
            goalTreeTask.update()
        }
        // Create debounced wrapper for UI updates
        debouncedUpdateGoalTreeUI = createDebouncedUpdate(scheduledExecutorService, updateGoalTreeUI, 500) // 500ms debounce
        periodicUpdateFuture = scheduledExecutorService.scheduleWithFixedDelay({
            if (!stopRequested.get() && isRunning.get()) {
                debouncedUpdateGoalTreeUI()
            }
        }, 15, 15, TimeUnit.SECONDS)

        val goalsTask = task.linkedTask("Goals")
        val tasksTask = task.linkedTask("Tasks")

        sessionLogTask = task.linkedTask("Session Log")
        logToSession("Starting Goal-Oriented session for: $userMessage")
        val coordinator = PlanCoordinator(
            user = user,
            session = session,
            dataStorage = ui.dataStorage!!,
            root = planSettings.absoluteWorkingDir?.let { File(it).toPath() }
                ?: ui.dataStorage?.getSessionDir(user, session)?.toPath() ?: File(".").toPath(),
            planSettings = planSettings
        )

        try {
            val initialGoals = parseInitialGoals(userMessage, task)
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
            task.error(e)
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
                val goalTask = goalsTask.linkedTask("Goal ID ${goal.id}")
                goalTasks[goal.id!!] = goalTask
                goalTask.add("# Goal: ${goal.description}\n\nID: ${goal.id}".renderMarkdown())

                try {
                    val (subgoals, tasksForGoal) = decomposeGoal(goal, coordinator, task)
                    goal.decompositionAttempted = true
                    if (subgoals.isEmpty() && tasksForGoal.isEmpty()) {
                        logToSession("Goal ID ${goal.id} (${goal.description}) decomposed into no subgoals or tasks.")
                        goalTask.add("No subgoals or tasks were generated for this goal.".renderMarkdown())
                        updateGoalTreeUI()

                    } else {
                        val subgoalsList = StringBuilder("## Subgoals:\n")
                        val tasksList = StringBuilder("## Tasks:\n")

                        subgoals.forEach { subgoal ->
                            if (!goalTree.containsKey(subgoal.id)) {
                                goalTree[subgoal.id!!] = subgoal
                                logToSession("  New subgoal: ${subgoal.description} (ID: ${subgoal.id}) for Goal ${goal.id}")
                                subgoalsList.append(
                                    "- ${subgoal.description} (ID: ${
                                        subgoal.id.let {
                                            goalTasks[subgoal.id]?.manager?.linkToSession(
                                                it
                                            ) ?: it
                                        }
                                    }})\n"
                                )
                                debouncedUpdateGoalTreeUI()
                            } else {
                                logToSession("  Subgoal ID ${subgoal.id} already exists. Skipping addition.")
                            }
                            goal.subgoals?.add(subgoal.id!!)
                        }
                        tasksForGoal.forEach { t ->
                            if (!taskMap.containsKey(t.id)) {
                                taskMap[t.id!!] = t
                                logToSession("  New task: ${t.description} (ID: ${t.id}) for Goal ${goal.id}")
                                tasksList.append(
                                    "- ${t.description} (ID: ${
                                        t.id.let {
                                            goalTasks[t.id]?.manager?.linkToSession(
                                                it
                                            ) ?: it
                                        }
                                    })\n"
                                )
                                debouncedUpdateGoalTreeUI()
                            } else {
                                logToSession("  Task ID ${t.id} already exists. Skipping addition.")
                            }
                            goal.tasks?.add(t.id!!)
                        }
                        if (subgoals.isNotEmpty()) {
                            goalTask.add(subgoalsList.toString().renderMarkdown())
                        }
                        if (tasksForGoal.isNotEmpty()) {
                            goalTask.add(tasksList.toString().renderMarkdown())
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error decomposing goal ${goal.id}", e)
                    logToSession("Error decomposing goal ${goal.id}: ${e.message}. Marking as BLOCKED.")
                    goalTask.add("**ERROR:** Failed to decompose goal: ${e.message}".renderMarkdown())
                    goal.status = GoalStatus.BLOCKED
                    goal.result = "Failed to decompose: ${e.message}"
                    debouncedUpdateGoalTreeUI()
                }
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
                    debouncedUpdateGoalTreeUI() // Update UI when task starts running

                    log.info("Submitting Task ID ${t.id} (${t.description}) to processor.")
                    val executionUiTask = tasksTask.linkedTask("Task ID ${t.id}")
                    taskTasks[t.id!!] = executionUiTask
                    val future = processor.submit<String?> {
                        executeTask(t.id, t, executionUiTask, coordinator)
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
                    goalTree.values.all { it.status == GoalStatus.COMPLETED || it.status == GoalStatus.BLOCKED } &&
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

        // Cancel periodic updates and do final update
        periodicUpdateFuture?.cancel(false)
        periodicUpdateFuture = null
        updateGoalTreeUI() // Final update without debouncing

        // Update the Goals tab with final status of all goals
        val goalsSummaryTask = goalsTask.linkedTask("Summary")
        goalsSummaryTask.add("# Goals Summary\n\n".renderMarkdown())
        val goalsSummary = StringBuilder()
        goalTree.values.sortedBy { it.id }.forEach { goal ->
            val statusEmoji = when (goal.status!!) {
                GoalStatus.ACTIVE -> "🟢"
                GoalStatus.BLOCKED -> "🧱"
                GoalStatus.COMPLETED -> "✅"
                GoalStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳"
            }
            val goalLink = goalsTask.manager.linkToSession(goal.id!!)
            goalsSummary.append("$statusEmoji **$goalLink**: ${goal.description}\n")
            if (goal.parentGoalId != null) {
                val parentGoal = goalTree[goal.parentGoalId]
                val parentLink = goalsTask.manager.linkToSession(goal.parentGoalId)
                goalsSummary.append("  - Parent: $parentLink - ${parentGoal?.description ?: "Unknown"}\n")
            }
            if (!goal.subgoals.isNullOrEmpty()) {
                val subgoalLinks = goal.subgoals.joinToString(", ") { subgoalId ->
                    goalsTask.manager.linkToSession(subgoalId)
                }
                goalsSummary.append("  - Subgoals: $subgoalLinks\n")
            }
            if (!goal.tasks.isNullOrEmpty()) {
                val taskLinks = goal.tasks.joinToString(", ") { taskId ->
                    tasksTask.manager.linkToSession(taskId)
                }
                goalsSummary.append("  - Tasks: $taskLinks\n")
            }
            if (!goal.dependencies.isNullOrEmpty()) {
                val depLinks = goal.dependencies.joinToString(", ") { depId ->
                    goalsTask.manager.linkToSession(depId)
                }
                goalsSummary.append("  - Dependencies: $depLinks\n")
            }
            if (goal.result != null) {
                goalsSummary.append("  - Result: ${goal.result?.take(100)?.replace("\n", " ")}...\n")
            }
        }
        goalsSummaryTask.add(goalsSummary.toString().renderMarkdown())
        // Update the Tasks tab with final status of all tasks
        val tasksSummaryTask = tasksTask.linkedTask("Summary")
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
            val taskLink = tasksTask.manager.linkToSession(task.id!!)
            tasksSummary.append("$statusEmoji **$taskLink**: ${task.description}\n")
            if (task.parentGoalId != null) {
                val parentGoal = goalTree[task.parentGoalId]
                val parentLink = goalsTask.manager.linkToSession(task.parentGoalId)
                tasksSummary.append("  - Parent Goal: $parentLink - ${parentGoal?.description ?: "Unknown"}\n")
            }
            if (!task.dependencies.isNullOrEmpty()) {
                val depLinks = task.dependencies.joinToString(", ") { depId ->
                    val depGoal = goalTree[depId]
                    val depTask = taskMap[depId]
                    when {
                        depGoal != null -> goalsTask.manager.linkToSession(depId)
                        depTask != null -> tasksTask.manager.linkToSession(depId)
                        else -> "Unknown ${depId}"
                    }
                }
                tasksSummary.append("  - Dependencies: $depLinks\n")
            }
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
        sessionLogTask?.complete(sessionLog.toString().renderMarkdown())
    }

    private fun executeTask(
        id: String,
        t: Task,
        task: SessionTask,
        coordinator: PlanCoordinator
    ): String = try {
        log.info("Started execution of Task ID ${id} (${t.description}) in processor.")
        val availableTaskTypes = TaskType.getAvailableTaskTypes(coordinator.planSettings)
        val parsedActor = ParsedActor(
            name = "TaskTypeChooser",
            resultClass = Tasks::class.java, // Parse directly into TaskConfigBase
            exampleInstance = Tasks(
                mutableListOf(
                    TaskType.getAvailableTaskTypes(planSettings).firstOrNull()?.let {
                        TaskType.getImpl(planSettings, it).taskConfig
                    }
                ).filterNotNull().toMutableList()
            ),
            prompt = """
                Given the following task description and context, choose the single most appropriate task type and provide all required details.
                Task Description: ${t.description}
                Available task types (and their schemas):
                ${availableTaskTypes.joinToString("\n") { it.name }}
            """.trimIndent(),
            model = coordinator.planSettings.defaultChatter.getChildClient(task),
            parsingModel = planSettings.parsingChatter,
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
            listOf(t.description ?: "") + contextData(
                t.parentGoalId,
                t.id
            ), // Pass focused context
        ).obj
        val result1 = StringBuilder()
        val planTask = answer.tasks?.firstOrNull()
        logToSession("Resolved task for Task ID ${t.id}\n```json\n${planTask?.toJson() ?: "None"}\n```\n")
        val semaphore = java.util.concurrent.Semaphore(0)
        val taskImpl = TaskType.getImpl(planSettings, planTask = planTask)
        taskImpl.run(
            agent = coordinator,
            messages = listOf(t.description ?: "") + contextData(),
            task = task,
            resultFn = {
                logToSession("Completed task for Task ID ${t.id}")
                result1.append(it)
                t.result = result1.toString()
                t.status = TaskStatus.COMPLETED
                semaphore.release()
            }, // Capture task output
            planSettings = planSettings,
        )
        logToSession("Waiting for task completion for Task ID ${t.id}...")
        semaphore.acquire()
        logToSession("Task ID ${t.id} complete")
        val result = t.result!!
        log.info("Completed execution of Task ID ${id} (${t.description}) in processor.")
        result
    } catch (e: Exception) {
        log.error(
            "Task ID ${id} (${t.description}) execution failed in processor.submit lambda",
            e
        )
        taskMap[id]?.apply {
            status = TaskStatus.FAILED
            result = "Execution Error: ${e.message}"
        }
        "Task execution failed: ${e.message}"
    }

    private fun awaitAll(taskExecutionJobs: MutableList<Pair<Task, Future<String?>>>) {
        for ((taskInstance, future) in taskExecutionJobs) {
            if (stopRequested.get()) {
                logToSession("Stop requested, not waiting for all tasks to complete this iteration.")
                break
            }
            try {
                if (taskInstance.status != TaskStatus.FAILED) {
                    while (!future.isDone) {
                        if (stopRequested.get()) break
                        if (future.isCancelled) {
                            logToSession("Task ID ${taskInstance.id} (${taskInstance.description}) was cancelled.")
                            taskInstance.status = TaskStatus.FAILED
                            taskInstance.result = "Task was cancelled."
                            debouncedUpdateGoalTreeUI()
                            break
                        }
                        if (processor.getActiveTaskCount() == 0) {
                            log.warn("No active tasks in processor but future not done for Task ID ${taskInstance.id}. Possible deadlock.")
                            break;
                        }
                        log.info("Waiting for Task ID ${taskInstance.id} (${taskInstance.description}) to complete. Currently ${processor.getActiveTaskCount()} active tasks.")
                        Thread.sleep(60000)
                    }
                    if (taskInstance.status != TaskStatus.FAILED) {
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
                    "Task ID ${taskInstance.id} (${taskInstance.description}) failed or error retrieving result.",
                    cause
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

    private fun parseInitialGoals(userMessage: String, task: SessionTask): List<Goal> {
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
            model = planSettings.defaultChatter.getChildClient(task),
            parsingModel = planSettings.parsingChatter,
            temperature = planSettings.temperature,
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
            g.copy(
                id = g.id?.takeIf { it.isNotBlank() } ?: "G${goalIdCounter.getAndIncrement()}",
                description = g.description,
                status = g.status
                    ?: (if (g.dependencies?.isEmpty() != false) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT),
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
        coordinator: PlanCoordinator,
        task: SessionTask
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
                        "  - Task ${it.id} (${it.description?.take(50)}...): ${it.status}"
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
            model = coordinator.planSettings.defaultChatter.getChildClient(task),
            parsingModel = coordinator.planSettings.parsingChatter,
            temperature = coordinator.planSettings.temperature,
            describer = describer
        )
        val inputMessages = mutableListOf(goal.description ?: "")
        inputMessages.addAll(contextData(goal.id, null))

        val answer = parsedActor.answer(inputMessages)
        val subgoals = answer.obj.subgoals?.map { sg ->
            sg.copy(
                id = sg.id?.takeIf { it.isNotBlank() } ?: "G${goalIdCounter.getAndIncrement()}",
                description = sg.description,
                status = sg.status
                    ?: (if (sg.dependencies?.isEmpty() != false) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT),
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
                status = t.status
                    ?: (if (t.dependencies?.isEmpty() != false) TaskStatus.PENDING else TaskStatus.ACTIVE_DEPENDENCY_WAIT),
                parentGoalId = goal.id,
                dependencies = t.dependencies ?: mutableListOf(),
                result = t.result
            )
        } ?: emptyList()
        return Pair(subgoals, tasks)
    }


    private fun updateAllStatuses() {
        var changed: Boolean
        do {
            val initialTaskStatuses = taskMap.mapValues { it.value.status }
            val initialGoalStatuses = goalTree.mapValues { it.value.status }
            changed = false
            taskMap.values.forEach { task ->
                val status = task.status!!
                if (status != TaskStatus.COMPLETED && status != TaskStatus.FAILED && status != TaskStatus.RUNNING) {
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

                if (goal.status!! != GoalStatus.COMPLETED && goal.status!! != GoalStatus.BLOCKED) {
                    val dependenciesMet = areDependenciesMet(goal)
                    val subGoals = goal.subgoals!!.mapNotNull { goalTree[it] }
                    val directTasks = goal.tasks!!.mapNotNull { taskMap[it] }

                    val blockingDependency =
                        goal.dependencies!!.firstOrNull { depId -> goalTree[depId]?.status == GoalStatus.BLOCKED }
                    val blockingSubGoal = subGoals.firstOrNull { it.status!! == GoalStatus.BLOCKED }
                    if (blockingDependency != null || blockingSubGoal != null) {
                        newStatus = GoalStatus.BLOCKED
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
                            goal.result = goal.result
                                ?: "Decomposition yielded no actionable sub-goals or tasks, and dependencies are met."
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
                        debouncedUpdateGoalTreeUI()
                        changed = true

                    }
                }
            }
            // Update UI after all status changes are complete
            if (goalTree.any { initialGoalStatuses[it.key] != it.value.status } ||
                taskMap.any { initialTaskStatuses[it.key] != it.value.status }) {
                debouncedUpdateGoalTreeUI()
            }

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

    private fun renderNode(goal: Goal, visited: MutableSet<String>): String {
        val nodeSb = StringBuilder()
        val statusEmoji = when (goal.status!!) {
            GoalStatus.ACTIVE -> "🟢 Active"
            GoalStatus.BLOCKED -> "🧱 Blocked"
            GoalStatus.COMPLETED -> "✅ Completed"
            GoalStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳ Waiting (Deps)"
        }
        val depsString =
            (if (goal.dependencies!!.isEmpty()) "none" else goal.dependencies.joinToString(", ") { "Goal ${it}" }).let {
                when (it) {
                    "" -> ""
                    else -> "Deps: $it"
                }
            }
        nodeSb.append("- " + ("""$statusEmoji **${goal.description ?: "N/A"} (ID: ${goal.id})**""").let { it ->
            goalTasks[goal.id]?.manager?.linkToSession(
                it
            ) ?: it
        } + "   " + depsString)
        nodeSb.append("\n")
        goal.tasks!!.mapNotNull { taskMap[it] }.forEach { t ->
            val taskStatusEmoji = when (t.status!!) {
                TaskStatus.PENDING -> "📝 Pending"
                TaskStatus.RUNNING -> "🏃 Running"
                TaskStatus.COMPLETED -> "✔️ Completed"
                TaskStatus.FAILED -> "❌ Failed"
                TaskStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳ Waiting (Deps)"
            }
            val taskDepsString =
                (if (t.dependencies!!.isEmpty()) "none" else t.dependencies.joinToString(", ") { dep ->
                    if (goalTree.containsKey(dep)) "Goal ${goalTasks.get(dep)?.manager?.linkToSession(dep) ?: dep}"
                    else "Task ${taskTasks.get(dep)?.manager?.linkToSession(dep) ?: dep}"
                }
                        ).let {
                        when (it) {
                            "" -> ""
                            else -> "Deps: $it"
                        }
                    }
            nodeSb.append("  - " + ("Task $taskStatusEmoji ${t.description ?: "N/A"} (ID: ${t.id!!})").let { it ->
                taskTasks[t.id]?.manager?.linkToSession(
                    it
                ) ?: it
            } + "    " + taskDepsString)
            nodeSb.append("\n")
        }
        goal.subgoals!!.mapNotNull { goalTree[it] }.joinToString("\n") { subGoal ->
            renderNode(subGoal, visited).trim().indent("  ")
        }.apply { nodeSb.append(this + "\n") }
        return nodeSb.toString()
    }

    private fun renderGoalTreeText(goals: List<Goal>): String {
        val sb = StringBuilder("### Goal Tree Status\n")
        val rootGoalIds = goals.mapNotNull { it.id }.toSet()
        val roots =
            goals.filter { it.parentGoalId == null || !rootGoalIds.contains(it.parentGoalId) }.sortedBy { it.id }
        if (roots.isEmpty() && goals.isNotEmpty()) {
            goals.sortedBy { it.id!! }.forEach {
                sb.append(
                    renderNode(
                        it,
                        mutableSetOf()
                    )
                )
            } // Start new traversal for each potential root in fallback
        } else {
            roots.sortedBy { it.id!! }
                .forEach { sb.append(renderNode(it, mutableSetOf())) } // Start new traversal for each root
        }
        return sb.toString()
    }

    override fun contextData(): List<String> {
        val contextLines = mutableListOf<String>()
        contextLines.add("Current Goal-Oriented Plan State:")
        val llmContextSb = StringBuilder()
        fun renderNodeForLlm(goal: Goal, indent: Int, visited: MutableSet<String>) {
            val goalDeps = goal.dependencies!!.joinToString(",").let {
                when (it) {
                    "" -> ""
                    else -> "(Deps: $it)"
                }
            }
            llmContextSb.append("${"  ".repeat(indent)}- G(${goal.id!!}): ${goal.description ?: "N/A"} [${goal.status!!}] $goalDeps\n")
            goal.tasks!!.mapNotNull { taskMap[it] }.forEach { t ->
                val taskDeps = t.dependencies!!.joinToString(",").let {
                    when (it) {
                        "" -> ""
                        else -> "(Deps: $it)"
                    }
                }
                llmContextSb.append("${"  ".repeat(indent + 1)}- T(${t.id!!}): ${t.description ?: "N/A"} [${t.status!!}] $taskDeps\n")
            }
            goal.subgoals!!.mapNotNull { goalTree[it] }.forEach { subGoal ->
                if (visited.add(subGoal.id!!)) { // Prevent infinite loops in case of cycles (though cycles aren't explicitly handled)
                    renderNodeForLlm(subGoal, indent + 1, visited)
                } else {
                    llmContextSb.append("${"  ".repeat(indent + 1)}- G(${subGoal.id}): ... (cycle detected or already rendered)\n")
                }
            }
        }

        val rootsForLlm = goalTree.values.filter { it.parentGoalId == null || !goalTree.containsKey(it.parentGoalId) }
            .sortedBy { it.id!! } // Consider nodes without known parents as roots
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
            val goalDeps = goal.dependencies!!.joinToString(",").let {
                when (it) {
                    "" -> ""
                    else -> "(Deps: $it)"
                }
            }
            llmContextSb.append("${"  ".repeat(indent)}- G(${goal.id!!}): ${goal.description ?: "N/A"} [${goal.status!!}] $goalDeps\n")
            goal.tasks!!.mapNotNull { taskMap[it] }.forEach { t ->
                val taskDeps = t.dependencies!!.joinToString(",")
                // Add task result if available and relevant (e.g., for completed/failed tasks)
                val taskResultSnippet = t.result?.take(50)?.replace("\n", " ").let {
                    when (it) {
                        "" -> ""
                        else -> "(Deps: $it)"
                    }
                }
                if (taskResultSnippet.isNotBlank()) llmContextSb.append("${"  ".repeat(indent + 1)}  Result: $taskResultSnippet...\n")
                llmContextSb.append("${"  ".repeat(indent + 1)}- T(${t.id!!}): ${t.description ?: "N/A"} [${t.status!!}] $taskDeps\n")
            }
            goal.subgoals!!.mapNotNull { goalTree[it] }.forEach { subGoal ->
                renderNodeForLlm(subGoal, indent + 1)
            }
        }

        val rootsForLlm = goalTree.values.filter { it.parentGoalId == null || !goalTree.containsKey(it.parentGoalId) }
            .sortedBy { it.id!! } // Consider nodes without known parents as roots
        rootsForLlm.forEach { renderNodeForLlm(it, 0) }
        contextLines.add(llmContextSb.toString())
        return contextLines
    }

    private fun createDebouncedUpdate(
        scheduler: ScheduledExecutorService,
        updateFunction: () -> Unit,
        delayMs: Long
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
        override val inputCnt = 1
        override fun getCognitiveMode(
            ui: SocketManager,
            planSettings: PlanSettings,
            session: Session,
            user: User?,
            describer: TypeDescriber
        ) = GoalOrientedMode(ui, planSettings, session, user, describer)

        private val log = LoggerFactory.getLogger(GoalOrientedMode::class.java)
        val scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    }
}