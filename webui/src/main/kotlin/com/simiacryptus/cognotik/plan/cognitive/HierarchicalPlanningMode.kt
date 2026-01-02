package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.cognotik.webui.session.newLogStream
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
            task.add(MarkdownUtil.renderMarkdown("Goal-Oriented Mode is already running. Please wait for the current session to complete or stop it."))
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
        task.echo(MarkdownUtil.renderMarkdown("User: $userMessage"))
        // Initialize transcript
        transcriptStream = task.newLogStream("Transcript")
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
        tabs["Goal Tree"] = MarkdownUtil.renderMarkdown("Loading...")
        tabs["Goals"] = MarkdownUtil.renderMarkdown("No goals yet.")
        tabs["Tasks"] = MarkdownUtil.renderMarkdown("No tasks yet.")
        tabs["Session Log"] = MarkdownUtil.renderMarkdown("Session started...")

        updateGoalTreeUI = {
            tabs["Goal Tree"] = MarkdownUtil.renderMarkdown(renderGoalTreeText(goalTree.values.toList()))
            tabs["Goals"] = MarkdownUtil.renderMarkdown(goalSummary())
            tabs["Tasks"] = MarkdownUtil.renderMarkdown(taskSummary())
        }
        updateLogUI = {
            tabs["Session Log"] = MarkdownUtil.renderMarkdown(sessionLog.toString())
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
                    task.complete(MarkdownUtil.renderMarkdown("Could not determine initial goals from your request."))
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
        task.add(MarkdownUtil.renderMarkdown("# Goal: ${goal.description}\n\nID: ${goal.id}"))

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
                task.add(MarkdownUtil.renderMarkdown("No subgoals or tasks were generated for this goal."))
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
                    task.add(MarkdownUtil.renderMarkdown(subgoalsList.toString()))
                }
                if (tasksForGoal.isNotEmpty()) {
                    task.add(MarkdownUtil.renderMarkdown(tasksList.toString()))
                }
            }
        } catch (e: Exception) {
            log.error("Error decomposing goal ${goal.id}", e)
            logToSession("Error decomposing goal ${goal.id}: ${e.message}. Marking as BLOCKED.")
            task.add(MarkdownUtil.renderMarkdown("**ERROR:** Failed to decompose goal: ${e.message}"))
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
            task.complete(MarkdownUtil.renderMarkdown("Session stopped by user."))
            stopLink?.set("Stopped")
        } else if (iteration >= maxIterations) {
            logToSession("Goal-Oriented session reached max iterations ($maxIterations).")
            task.complete(MarkdownUtil.renderMarkdown("Session reached max iterations."))
            stopLink?.set("Max Iterations Reached")
        } else {
            val finalStatusSummary = goalTree.values.groupBy { it.status }.mapValues { it.value.size }.toString()
            logToSession("Goal-Oriented session completed. Final status: $finalStatusSummary")
            task.complete(MarkdownUtil.renderMarkdown("Session completed. Final Status: $finalStatusSummary"))
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
            task.add(MarkdownUtil.renderMarkdown("Starting execution of task: ${t.description}"))
            task.verbose(MarkdownUtil.renderMarkdown("Task Details:\n```json\n${t.toJson()}\n```\n"))
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
                task.add(MarkdownUtil.renderMarkdown("Failed to generate task implementation"))
                return t.result
            }
            val semaphore = Semaphore(0)
            val taskImpl = TaskType.getImpl(orchestrationConfig, planTask = planTask)
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
            val acquired = semaphore.tryAcquire(5, TimeUnit.MINUTES)
            if (!acquired) {
                logToSession("Task ID ${t.id} timed out after 5 minutes")
                logToSession("**Result:** TIMEOUT")
                t.status = TaskStatus.FAILED
                t.result = "Task execution timed out"
                task.add(MarkdownUtil.renderMarkdown("Task execution timed out after 5 minutes"))
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
            task.add(MarkdownUtil.renderMarkdown("Task execution failed: ${e.message}"))
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
                    TaskType.getImpl(orchestrationConfig, it).executionConfig
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
                        Thread.sleep(5000) // Check more frequently - every 5 seconds instead of 60
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
            val depsString =
                (if (goal.dependencies?.isEmpty() == true) "none" else goal.dependencies?.joinToString(", ") { "Goal ${it}" }).let {
                    when (it) {
                        "" -> ""
                        else -> "Deps: $it"
                    }
                }
            nodeSb.append("- " + ("""$statusEmoji **${goal.description ?: "N/A"} (ID: ${goal.id})**""").let {
                goalTasks[goal.id]?.ui?.linkToSession(
                    it
                ) ?: it
            } + "   " + depsString)
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

    private fun getStateFile(task : SessionTask): File {
        val dir = task.ui.dataStorage?.getSessionDir(user, session) ?: File(".")
        return File(dir, "planning_state.json")
    }

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
                t.add(MarkdownUtil.renderMarkdown("# Goal: ${goal.description}\n\nID: ${goal.id}"))
            }
            taskMap.values.forEach { v ->
                val t = task.newTask()
                taskTasks[v.id] = t
                t.add(MarkdownUtil.renderMarkdown("# Task: ${v.description}\n\nID: ${v.id}"))
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