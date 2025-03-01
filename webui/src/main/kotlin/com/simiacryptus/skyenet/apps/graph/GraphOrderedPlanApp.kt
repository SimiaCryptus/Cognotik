package com.simiacryptus.skyenet.apps.graph

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.apps.general.PlanAheadApp
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.getChildClient
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File

/**
 * GraphOrderedPlanApp orders a provided software graph by node type priority,
 * transforms each graph node into an individual patch/subplan and aggregates all patches
 * into an execution DAG, which is then executed accordingly.
 */
class GraphOrderedPlanApp(
    applicationName: String = "Graph Ordered Planning",
    path: String = "/graphOrderedPlan",
    planSettings: PlanSettings,
    model: ChatModel,
    parsingModel: ChatModel,
    domainName: String = "localhost",
    showMenubar: Boolean = true,
    api: API? = null,
    api2: OpenAIClient,
    private val graphFile: String
) : PlanAheadApp(
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
    data class ExtraTaskDependencies(
        val dependencies: Map<String, List<String>> = emptyMap()
    )
    override fun userMessage(
        session: Session, user: User?, userMessage: String, ui: ApplicationInterface, api: API
    ) {
        val task = ui.newTask()
        val api = (api as ChatClient).getChildClient(task)
        try {
            val planSettings = getSettings(session, user, PlanSettings::class.java)
                ?: throw IllegalArgumentException("Plan settings not found")
            api.budget = planSettings.budget
            task.add("Reading graph file: $graphFile")
            val graphFileContent = readGraphFile(planSettings)
            val softwareGraph = JsonUtil.fromJson<SoftwareNodeType.SoftwareGraph>(
                graphFileContent, SoftwareNodeType.SoftwareGraph::class.java
            )
            log.debug("Successfully read graph file. Size: ${graphFileContent.length} characters; ${softwareGraph.nodes.size} nodes.")
            task.add("Successfully loaded graph with ${softwareGraph.nodes.size} nodes")
            val orderedNodes = orderGraphNodes(softwareGraph.nodes)
            task.add("Ordered ${orderedNodes.size} nodes by priority")
            val cumulativeTasks = transformNodesToPlan(orderedNodes, planSettings, userMessage, graphFile, api)
            addDependencies(cumulativeTasks, graphFileContent, userMessage, api)
            val plan = PlanUtil.filterPlan { cumulativeTasks } ?: emptyMap()
            log.info("Ordered plan built successfully. Proceeding to execute DAG.")
            task.add("Plan generated successfully with ${plan.size} tasks")
            task.add("Starting plan execution...")
            task.add(buildPlanSummary(plan).let(::renderMarkdown))
            task.add(
                buildExecutionSummary(
                    PlanCoordinator(
                        user = user,
                        session = session,
                        dataStorage = dataStorage,
                        ui = ui,
                        root = planSettings.workingDir?.let { File(it).toPath() } ?: dataStorage.getDataDir(
                            user,
                            session
                        ).toPath(),
                        planSettings = planSettings
                    ).executePlan(
                        plan = plan,
                        task = task,
                        userMessage = userMessage,
                        api = api,
                        api2 = api2
                    )).let(::renderMarkdown))
            task.add("Plan execution completed")
        } catch (e: Exception) {
            task.error(ui, e)
            task.add("Error during ordered planning: ${e.message}")
            log.error("Error during ordered planning: ${e.message}", e)
        }
    }

    private fun addDependencies(
        cumulativeTasks: MutableMap<String, TaskConfigBase>,
        graphFileContent: String,
        userMessage: String,
        api: ChatClient
    ) {
        log.debug("Starting dependency analysis for ${cumulativeTasks.size} tasks")
        // Validate input parameters
        if (cumulativeTasks.isEmpty()) {
            log.warn("No tasks provided for dependency analysis")
            return
        }
        try {
            val existingDependencies = cumulativeTasks.mapValues {
                it.value.task_dependencies?.toSet() ?: emptySet()
            }

            ParsedActor(
                resultClass = ExtraTaskDependencies::class.java,
                prompt = """
                    Analyze the current plan context and the provided software graph to identify missing task dependencies.
                    Consider:
                    1. Code file dependencies from the graph
                    2. Test dependencies on implementation files
                    3. Package and project hierarchical dependencies
                    4. Build and deployment order requirements
                    Only suggest new dependencies that are not already present.
                    Ensure all suggested task IDs exist in the current plan.
                """.trimIndent(),
                model = model,
                parsingModel = parsingModel,
            ).answer(
                listOf(
                    "You are a software planning assistant. Your goal is to analyze the current plan context and the provided software graph, then focus on generating or refining an instruction (patch/subplan) for the specific node provided.",
                    "Current aggregated plan so far (if any):\n```json\n${JsonUtil.toJson(cumulativeTasks)}\n```",
                    "Complete Software Graph from file `$graphFile` is given below:\n```json\n$graphFileContent\n```",
                    "User Instruction/Query: $userMessage\nPlease evaluate the context and provide your suggested changes or instructions to improve the software plan."
                ), api = api
            ).obj.dependencies.forEach { (taskToEdit, newUpstreams) ->
                // Validate task exists
                val task = cumulativeTasks[taskToEdit]
                if (task == null) {
                    log.warn("Attempted to add dependencies to non-existent task: $taskToEdit")
                    return@forEach
                }
                // Initialize dependencies list if null
                if (task.task_dependencies == null) {
                    task.task_dependencies = mutableListOf()
                }
                // Filter and add only valid new dependencies
                val validNewDependencies = newUpstreams.filter { upstreamId ->
                    if (!cumulativeTasks.containsKey(upstreamId)) {
                        log.warn("Skipping invalid dependency $upstreamId for task $taskToEdit")
                        false
                    } else if (wouldCreateCycle(taskToEdit, upstreamId, cumulativeTasks)) {
                        log.warn("Skipping cyclic dependency $upstreamId for task $taskToEdit")
                        false
                    } else {
                        true
                    }
                }
                task.task_dependencies?.addAll(validNewDependencies)
                if (validNewDependencies.isNotEmpty()) {
                    log.debug("Added ${validNewDependencies.size} dependencies to task $taskToEdit: ${validNewDependencies.joinToString()}")
                }
            }
            // Log summary of changes
            val newDependencies = cumulativeTasks.mapValues {
                (it.value.task_dependencies?.toSet() ?: emptySet()) - (existingDependencies[it.key] ?: emptySet())
            }.filterValues { it.isNotEmpty() }
            if (newDependencies.isNotEmpty()) {
                log.info("Added new dependencies to ${newDependencies.size} tasks")
                newDependencies.forEach { (taskId, deps) ->
                    log.debug("Task $taskId: Added dependencies: ${deps.joinToString()}")
                }
            } else {
                log.debug("No new dependencies were added")
            }
        } catch (e: Exception) {
            log.error("Error during dependency analysis", e)
            throw RuntimeException("Failed to analyze and add dependencies", e)
        }
    }

    /**
     * Check if adding a dependency would create a cycle in the task graph
     */
    private fun wouldCreateCycle(
        taskId: String,
        newDependencyId: String,
        tasks: Map<String, TaskConfigBase>,
        visited: MutableSet<String> = mutableSetOf()
    ): Boolean {
        if (taskId == newDependencyId) return true
        if (!visited.add(newDependencyId)) return false
        return tasks[newDependencyId]?.task_dependencies?.any { dependencyId ->
            wouldCreateCycle(taskId, dependencyId, tasks, visited)
        } ?: false
    }

    /**
     * Read and return the content of the graph file.
     */
    private fun readGraphFile(planSettings: PlanSettings): String {
        val workingDirectory = planSettings.workingDir ?: "."
        val file = File(workingDirectory).resolve(graphFile)
        if (!file.exists()) {
            log.error("Graph file does not exist at: ${file.absolutePath}")
            throw IllegalArgumentException("Graph file does not exist at: ${file.absolutePath}")
        }
        log.debug("Reading graph file from: ${file.absolutePath}")
        return file.readText()
    }

    /**
     * Order nodes first by defined priorities and then by remaining nodes.
     */
    private fun orderGraphNodes(nodes: Collection<SoftwareNodeType.NodeBase<*>>): List<SoftwareNodeType.NodeBase<*>> {
        val priorityOrder = listOf("SpecificationDocument", "CodeFile", "TestCodeFile")
        val ordered = mutableListOf<SoftwareNodeType.NodeBase<*>>()
        for (priority in priorityOrder) {
            val filtered = nodes.filter { it.type == priority }
            log.debug("Found ${filtered.size} nodes for priority '$priority'.")
            ordered.addAll(filtered)
        }
        val remaining = nodes.filter { it.type !in priorityOrder }
        log.debug("Appending ${remaining.size} remaining nodes.")
        ordered.addAll(remaining)
        return ordered
    }

    /**
     * Transform each node into plan patches.
     */
    private fun transformNodesToPlan(
        nodes: List<SoftwareNodeType.NodeBase<*>>,
        planSettings: PlanSettings,
        userMessage: String,
        graphFile: String,
        api: API
    ): MutableMap<String, TaskConfigBase> {
        val tasks = mutableMapOf<String, TaskConfigBase>()
        nodes.forEach {
            tasks.putAll(
                getNodePlan(
                    planSettings = planSettings,
                    tasks = tasks,
                    graphFile = graphFile,
                    graphTxt = readGraphFile(planSettings),
                    node = it,
                    userMessage = userMessage,
                    api = api
                ) ?: emptyMap()
            )
        }
        return tasks
    }

    private fun getNodePlan(
        planSettings: PlanSettings,
        tasks: MutableMap<String, TaskConfigBase>,
        graphFile: String,
        graphTxt: String,
        node: SoftwareNodeType.NodeBase<*>,
        userMessage: String,
        api: API
    ): Map<String, TaskConfigBase>? {
        val maxRetries = 3
        val retryDelayMillis = 1000L
        var attempt = 0
        fun combine(node: SoftwareNodeType.NodeBase<*>, key: String) = when {
            key.startsWith(node.id.toString(), false) -> key
            else -> "${node.id}_$key"
        }
        while (true) {
            try {
                return planSettings.planningActor().answer(
                    listOf(
                        "You are a software planning assistant. Your goal is to analyze the current plan context and the provided software graph, then focus on generating or refining an instruction (patch/subplan) for the specific node provided.",
                        "Current aggregated plan so far (if any):\n```json\n${JsonUtil.toJson(tasks)}\n```",
                        "Complete Software Graph from file `$graphFile` is given below:\n```json\n$graphTxt\n```",
                        "Details of the focused node with ID `${node.id}`:\n```json\n${JsonUtil.toJson(node)}\n```",
                        "User Instruction/Query: $userMessage\nPlease evaluate the context and provide your suggested changes or instructions to improve the software plan."
                    ).filter { it.isNotBlank() }, api = api
                ).obj.tasksByID?.mapKeys { combine(node, it.key) }?.mapValues {
                    it.value.task_dependencies = it.value.task_dependencies?.map { combine(node, it) }?.toMutableList();
                    it.value
                }
            } catch (e: Exception) {
                if (attempt++ >= maxRetries) {
                    throw e
                }
                Thread.sleep(retryDelayMillis)
            }
        }
    }


    /**
     * Build a plan summary string for UI display.
     */
    private fun buildPlanSummary(plan: Map<String, TaskConfigBase>): String = buildString {
        appendLine("# Graph-Based Planning Result")
        appendLine()
        appendLine("## Generated Plan (DAG)")
        appendLine("```json")
        appendLine(JsonUtil.toJson(plan))
        appendLine("```")
    }

    /**
     * Build an execution summary string for UI display.
     */
    private fun buildExecutionSummary(state: PlanProcessingState): String = buildString {
        appendLine("## Plan Execution Summary")
        appendLine("- Completed Tasks: ${state.completedTasks.size}")
        appendLine("- Failed Tasks: ${state.subTasks.size - state.completedTasks.size}")
        appendLine()
        appendLine("### Task Results:")
        state.taskResult.forEach { (taskId, result) ->
            appendLine("#### $taskId")
            appendLine("```")
            appendLine(result.take(500))
            appendLine("```")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GraphOrderedPlanApp::class.java)
    }
}