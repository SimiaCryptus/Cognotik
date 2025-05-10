package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.*
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
        
        mainTab["Goal Tree"] = renderGoalTreeUI(task).placeholder

        // 2. Goal Processing Loop


        var iteration = 0
        while (iteration < maxIterations && !stopRequested.get()) {
            iteration++
            logToSession("\n## Iteration $iteration / $maxIterations")
            mainTab["Goal Tree"] = renderGoalTreeUI(task).placeholder

            updateAllStatuses() // Crucial: update statuses based on dependencies and completions

            val decomposableGoals = goalTree.values.filter {
                it.status == GoalStatus.ACTIVE && !it.decompositionAttempted && it.subgoals.isEmpty() && it.tasks.isEmpty()
            }

            if (decomposableGoals.isNotEmpty()) {
                logToSession("Found ${decomposableGoals.size} goal(s) to decompose:")
                decomposableGoals.forEach { logToSession("- ${it.description} (ID: ${it.id})") }
            }

            for (goal in decomposableGoals) {
                if (stopRequested.get()) break
                logToSession("Decomposing goal: ${goal.description} (ID: ${goal.id})")
                try {
                    val (subgoals, tasksForGoal) = decomposeGoal(goal, apiClient, task)
                    goal.decompositionAttempted = true
                    if (subgoals.isEmpty() && tasksForGoal.isEmpty()) {
                        logToSession("Goal ${goal.id} decomposed into no subgoals or tasks. Marking as potentially blocked if no other path.")
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
                mainTab["Goal Tree"] = renderGoalTreeUI(task).placeholder
            }

            if (stopRequested.get()) break

            updateAllStatuses() // Ensure statuses are fresh before task execution phase

            val executableTasks = taskMap.values.filter { it.status == TaskStatus.PENDING } // updateAllStatuses should have set PENDING if dependencies met

            if (executableTasks.isNotEmpty()) {
                logToSession("Found ${executableTasks.size} task(s) to execute:")
                executableTasks.forEach { logToSession("- ${it.description} (ID: ${it.id})") }

                val taskFutures = executableTasks.map { t ->
                    t.status = TaskStatus.RUNNING
                    logToSession("Executing task: ${t.description} (ID: ${t.id})")
                    mainTab["Goal Tree"] = renderGoalTreeUI(task).placeholder
                    processor.submit<Pair<Task, String?>> { // Task object, Result string
                        try {
                            val executionUiTask = ui.newTask(false)
                            val taskResult = executeTask(t, apiClient.getChildClient(executionUiTask), executionUiTask)
                            Pair(t, taskResult)
                        } catch (e: Exception) {
                            log.error("Task ${t.id} execution failed in processor.submit", e)
                            t.status = TaskStatus.FAILED // Ensure status is updated
                            t.result = "Error during task execution: ${e.message}"
                            Pair(t, t.result) // Return the task and its error result
                        }
                    } 
                }
                
                for (future in taskFutures) {
                    if (stopRequested.get()) { 
                        logToSession("Stop requested, not waiting for all tasks to complete this iteration.")
                        break 
                    }
                    try {
                        val (taskInstance, taskOutputResult) = future.get() 
                        
                        if (taskInstance.status == TaskStatus.FAILED) { 
                            logToSession("Task ${taskInstance.id} FAILED (from execution). Result: ${taskInstance.result}")
                        } else { 
                            taskInstance.status = TaskStatus.COMPLETED
                            taskInstance.result = taskOutputResult
                            logToSession("Task ${taskInstance.id} COMPLETED. Result: ${taskOutputResult?.take(100)}...")
                        }
                    } catch (e: Exception) {


                        log.error("Critical error retrieving task result from future", e)
                        logToSession("Critical error processing a task result: ${e.message}. This may leave a task in RUNNING state.")
                    }
                }
            } else {
                logToSession("No executable tasks in this iteration.")
            }
            
            updateAllStatuses() // Final status update for the iteration
            mainTab["Goal Tree"] = renderGoalTreeUI(task).placeholder

            val activeGoalsCount = goalTree.values.count { it.status == GoalStatus.ACTIVE }
            val pendingOrRunningTasksCount = taskMap.values.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING }

            if (activeGoalsCount == 0 && pendingOrRunningTasksCount == 0) {
                val allDoneOrBlocked = goalTree.values.all { it.status == GoalStatus.COMPLETED || it.status == GoalStatus.BLOCKED } &&
                                   taskMap.values.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
                if(allDoneOrBlocked) {
                    logToSession("All goals are completed or blocked. No pending/running tasks.")
                    break
                }
            }
            if (decomposableGoals.isEmpty() && executableTasks.isEmpty() && (activeGoalsCount > 0 || pendingOrRunningTasksCount > 0)) {
                logToSession("Stalled: No goals decomposed and no tasks executed, but active goals or pending/running tasks remain. Check for dependency cycles or unresolvable goals.")
            }
        }

        mainTab["Goal Tree"] = renderGoalTreeUI(task).placeholder
        if (stopRequested.get()) {
            logToSession("Goal-Oriented session stopped by user request at iteration $iteration.")
            task.complete(renderMarkdown("Session stopped by user.", ui = ui))
            stopLink?.set("Stopped")
        } else if (iteration >= maxIterations) {
            logToSession("Goal-Oriented session reached max iterations ($maxIterations).")
            task.complete(renderMarkdown("Session reached max iterations.", ui = ui))
            stopLink?.set("Max Iterations Reached")
        } else {
            val finalStatusSummary = goalTree.values.groupBy { it.status }.mapValues { it.value.size }.toString()
            logToSession("Goal-Oriented session completed. Final status: $finalStatusSummary")
            task.complete(renderMarkdown("Session completed. Final Status: $finalStatusSummary", ui = ui))
            stopLink?.set("Completed")
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
                status = if (g.dependencies.isNullOrEmpty()) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT,
                subgoals = g.subgoals ?: mutableListOf(),
                tasks = g.tasks ?: mutableListOf(),
                dependencies = g.dependencies ?: mutableListOf()
            )
        }
    }

    /**
     * Decompose a goal into subgoals and/or tasks using LLM.
     * Returns Pair(subgoals, tasks)
     */
    private fun decomposeGoal(goal: Goal, api: ChatClient, sessionTask: SessionTask): Pair<List<Goal>, List<Task>> {
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
                        dependencies = mutableListOf(goal.id)
                    )
                ),
                tasks = listOf(
                    Task(
                        id = "T1",
                        description = "Draft OpenAPI spec for upload endpoint",
                        parentGoalId = goal.id,
                        dependencies = mutableListOf(goal.id)
                    )
                )
            ),
            prompt = """
                Given the following goal, decide whether it can be directly addressed by a task, or if it should be broken down into subgoals.
                If the goal is sufficiently concrete, identify the next executable task(s) for this goal.
                If the goal is still abstract or complex, identify subgoals that, when completed, will achieve the parent goal.
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
                status = if (sg.dependencies.isNullOrEmpty()) GoalStatus.ACTIVE else GoalStatus.ACTIVE_DEPENDENCY_WAIT,
                parentGoalId = goal.id,
                subgoals = sg.subgoals ?: mutableListOf(),
                tasks = sg.tasks ?: mutableListOf(),
                dependencies = sg.dependencies ?: mutableListOf(goal.id)
            )
        } ?: emptyList()
        val tasks = answer.obj.tasks?.map { t ->
            t.copy(
                id = t.id.ifBlank { "T${taskIdCounter.getAndIncrement()}" },
                status = if (t.dependencies.isNullOrEmpty()) TaskStatus.PENDING else TaskStatus.ACTIVE_DEPENDENCY_WAIT,
                parentGoalId = goal.id,
                dependencies = t.dependencies ?: mutableListOf(goal.id)
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
                ${TaskType.getAvailableTaskTypes(planSettings).joinToString("\n") { it.name }}
            """.trimIndent(),
            model = planSettings.defaultModel,
            parsingModel = planSettings.parsingModel,
            temperature = planSettings.temperature,
            describer = describer,
            parserPrompt = ("Task Subtype Schema:\n" + TaskType.getAvailableTaskTypes(planSettings)
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
            changed = false
            // Update Task Statuses first
            taskMap.values.forEach { task ->
                val oldStatus = task.status
                if (task.status == TaskStatus.ACTIVE_DEPENDENCY_WAIT || task.status == TaskStatus.PENDING) { // Check if it can move to PENDING or stay waiting
                    if (areDependenciesMet(task)) {
                        if (task.status != TaskStatus.RUNNING && task.status != TaskStatus.COMPLETED && task.status != TaskStatus.FAILED) {
                           task.status = TaskStatus.PENDING // Ready to be picked up
                        }
                    } else {
                         if (task.status != TaskStatus.RUNNING && task.status != TaskStatus.COMPLETED && task.status != TaskStatus.FAILED) {
                            task.status = TaskStatus.ACTIVE_DEPENDENCY_WAIT
                         }
                    }
                }
                if (oldStatus != task.status) changed = true
            }

            // Update Goal Statuses
            goalTree.values.forEach { goal ->
                val oldStatus = goal.status
                if (goal.status != GoalStatus.COMPLETED && goal.status != GoalStatus.BLOCKED) {
                    if (!areDependenciesMet(goal)) {
                        goal.status = GoalStatus.ACTIVE_DEPENDENCY_WAIT
                    } else {
                        // Dependencies met, now check sub-items
                        val subGoals = goal.subgoals.mapNotNull { goalTree[it] }
                        val tasksOfGoal = goal.tasks.mapNotNull { taskMap[it] }

                        if (subGoals.all { it.status == GoalStatus.COMPLETED } && tasksOfGoal.all { it.status == TaskStatus.COMPLETED }) {
                            goal.status = GoalStatus.COMPLETED
                            goal.result = goal.result ?: "All sub-goals and tasks completed."
                        } else if (subGoals.any { it.status == GoalStatus.BLOCKED } || tasksOfGoal.any { it.status == TaskStatus.FAILED }) {
                            goal.status = GoalStatus.BLOCKED
                            goal.result = goal.result ?: "Blocked by a sub-goal or failed task."
                        } else if (goal.decompositionAttempted && subGoals.isEmpty() && tasksOfGoal.isEmpty() && !tasksOfGoal.any { it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING }) {
                            goal.status = GoalStatus.BLOCKED
                            goal.result = goal.result ?: "Decomposition yielded no actions, and no pending tasks exist for this goal."
                        } else {
                            goal.status = GoalStatus.ACTIVE // Dependencies met, has work or needs decomposition
                        }
                    }
                }
                if (oldStatus != goal.status) changed = true
            }
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

    // Refined renderGoalTree to create a new task for the display
    private fun renderGoalTreeUI(parentTask: SessionTask): SessionTask {
        val task = ui.newTask(false) // Create a new task for this rendering
        task.add(renderMarkdown(renderGoalTreeText(goalTree.values.toList()), ui=ui))
        task.complete()
        return task
    }

    /**
     * Renders the goal tree as markdown for display in the UI.
     */
    private fun renderGoalTreeText(goals: List<Goal>): String { // Renamed from renderGoalTree
        val sb = StringBuilder("### Goal Tree\n")
        fun renderNode(goal: Goal, indent: String) {
            val statusEmoji = when (goal.status) {
                GoalStatus.ACTIVE -> "🟢"
                GoalStatus.BLOCKED -> "⏸️"
                GoalStatus.COMPLETED -> "✅"

                GoalStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳"
            }
            sb.append("$indent- $statusEmoji **${goal.description}** (ID: ${goal.id}, Deps: ${goal.dependencies.joinToString(",")})\n")
            if (goal.result != null) {
                sb.append("$indent  Result: ${goal.result?.replace("\n", "\n$indent  ")}\n")
            }
            goal.tasks.mapNotNull { taskMap[it] }.forEach { t ->
                val taskStatusEmoji = when (t.status) {
                    TaskStatus.PENDING -> "📝"
                    TaskStatus.RUNNING -> "🏃"
                    TaskStatus.COMPLETED -> "✔️"
                    TaskStatus.FAILED -> "❌"
                    TaskStatus.ACTIVE_DEPENDENCY_WAIT -> "⏳"
                }
                sb.append("$indent  - Task $taskStatusEmoji ${t.description} (ID: ${t.id}, Deps: ${t.dependencies.joinToString(",")})\n")
                 if (t.result != null) {
                    sb.append("$indent    Result: ${t.result?.replace("\n", "\n$indent    ")}\n")
                }
            }
            goal.subgoals.mapNotNull { goalTree[it] }.forEach { subGoal ->
                renderNode(subGoal, indent + "  ")
            }
        }
        val roots = goals.filter { it.parentGoalId == null }.sortedBy { it.id }
        roots.forEach { renderNode(it, "") }
        return sb.toString()
    }

    override fun contextData(): List<String> {
        // Provide a summary of the current state for LLM context
        val contextLines = mutableListOf<String>()
        contextLines.add("Current Goal-Oriented Plan State:")
        contextLines.add(renderGoalTreeText(goalTree.values.toList()).renderMarkdown())
        return contextLines
    }

    // --- Data Classes ---
    @Description("A goal in the goal-oriented planning system.")
    data class Goal(
        val id: String,
        val description: String,
        var status: GoalStatus = GoalStatus.ACTIVE_DEPENDENCY_WAIT, // Default, will be updated by updateAllStatuses
        val parentGoalId: String? = null,
        val subgoals: MutableList<String> = mutableListOf(),
        val tasks: MutableList<String> = mutableListOf(),
        val dependencies: MutableList<String> = mutableListOf(),
        var decompositionAttempted: Boolean = false,
        var result: String? = null
    )

    @Description("A task that can be executed to achieve a goal.")
    data class Task(
        val id: String,
        val description: String,
        var status: TaskStatus = TaskStatus.ACTIVE_DEPENDENCY_WAIT, // Default, will be updated
        val parentGoalId: String? = null,
        var result: String? = null,
        val dependencies: MutableList<String> = mutableListOf()
    )

    @Description("Status of a goal.")
    enum class GoalStatus { ACTIVE, BLOCKED, COMPLETED, ACTIVE_DEPENDENCY_WAIT }
    @Description("Status of a task.")
    enum class TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, ACTIVE_DEPENDENCY_WAIT }

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