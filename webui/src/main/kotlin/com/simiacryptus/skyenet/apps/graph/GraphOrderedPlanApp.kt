package com.simiacryptus.skyenet.apps.graph

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.apps.general.PlanAheadApp
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
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

    override fun userMessage(
        session: Session, user: User?, userMessage: String, ui: ApplicationInterface, api: API
    ) {
        val task = ui.newTask()
        val api = (api as ChatClient).getChildClient(task)
        try {
            // Retrieve and validate plan settings
            val planSettings = getSettings(session, user, PlanSettings::class.java)
                ?: throw IllegalArgumentException("Plan settings not found")
            api.budget = planSettings.budget
            val graphFileContent = readGraphFile(planSettings)
            val softwareGraph = JsonUtil.fromJson<SoftwareNodeType.SoftwareGraph>(
                graphFileContent, SoftwareNodeType.SoftwareGraph::class.java
            )
            log.debug("Successfully read graph file. Size: ${graphFileContent.length} characters; ${softwareGraph.nodes.size} nodes.")
            val orderedNodes = orderGraphNodes(softwareGraph.nodes)
            val cumulativeTasks = transformNodesToPlan(orderedNodes, planSettings, userMessage, graphFile, api)
            val plan = PlanUtil.filterPlan { cumulativeTasks } ?: emptyMap()
            log.info("Ordered plan built successfully. Proceeding to execute DAG.")
            val agent = PlanCoordinator(
                user = user,
                session = session,
                dataStorage = dataStorage,
                ui = ui,
                root = planSettings.workingDir?.let { File(it).toPath() } ?: dataStorage.getDataDir(user, session)
                    .toPath(),
                planSettings = planSettings)
            task.add(buildPlanSummary(plan))
            task.add(
                buildExecutionSummary(
                    agent.executePlan(
                        plan = plan,
                        task = task,
                        userMessage = userMessage,
                        api = api,
                        api2 = api2
                    )
                ))
        } catch (e: Exception) {
            task.error(ui, e)
            log.error("Error during ordered planning: ${e.message}", e)
        }
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
        val graphTxt = readGraphFile(planSettings)  // reuse the helper for consistency
        nodes.forEach {
            tasks.putAll(
                getNodePlan(
                    planSettings = planSettings,
                    tasks = tasks,
                    graphFile = graphFile,
                    graphTxt = graphTxt,
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
        // Configure retry parameters
        val maxRetries = 3
        val retryDelayMillis = 1000L
        var attempt = 0
        while (attempt < maxRetries) {
            try {
                val answer = planSettings.planningActor().answer(
                    listOf(
                        "You are a software planning assistant. Your goal is to analyze the current plan context and the provided software graph, then focus on generating or refining an instruction (patch/subplan) for the specific node provided.",
                        "Current aggregated plan so far (if any):\n```json\n${JsonUtil.toJson(tasks)}\n```",
                        "Complete Software Graph from file `$graphFile` is given below:\n```json\n$graphTxt\n```",
                        "Details of the focused node with ID `${node.id}`:\n```json\n${JsonUtil.toJson(node)}\n```",
                        "User Instruction/Query: $userMessage\nPlease evaluate the context and provide your suggested changes or instructions to improve the software plan."
                    ).filter { it.isNotBlank() }, api = api
                )
                // Rewrite the keys by prefixing the node id
                val answerTasks = answer.obj.tasksByID
                return answerTasks?.mapKeys { "${node.id}_${it.key}" }
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxRetries) {
                    throw e
                }
                Thread.sleep(retryDelayMillis)
            }
        }
        return null
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