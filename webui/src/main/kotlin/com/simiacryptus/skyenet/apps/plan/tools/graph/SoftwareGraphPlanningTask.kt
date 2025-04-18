package com.simiacryptus.skyenet.apps.plan.tools.graph

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File

class SoftwareGraphPlanningTask(
    planSettings: PlanSettings, planTask: GraphBasedPlanningTaskConfigData?
) : AbstractTask<SoftwareGraphPlanningTask.GraphBasedPlanningTaskConfigData>(planSettings, planTask) {

    class GraphBasedPlanningTaskConfigData(
        @Description("REQUIRED: The path to the input software graph JSON file") val input_graph_file: String? = null,
        @Description("The instruction or goal to be achieved") val instruction: String = "",
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskConfigBase(
        task_type = TaskType.SoftwareGraphPlanningTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = """
     GraphBasedPlanningTask - Use a software graph to generate an actionable sub-plan.
       ** Include the file path to the input graph file and the instruction.
    """.trimIndent()

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        api: ChatClient,
        resultFn: (String) -> Unit,
        api2: OpenAIClient,
        planSettings: PlanSettings
    ) {
        val inputFile = (planSettings.workingDir?.let { File(it) } ?: File(".")).resolve(
            when {
                !taskConfig?.input_graph_file.isNullOrBlank() -> taskConfig?.input_graph_file!!
                else -> throw IllegalArgumentException("Input graph file not specified")
            }
        )
        if (!inputFile.exists()) throw IllegalArgumentException("Input graph file does not exist: ${inputFile.absolutePath}")
        val response = planSettings.planningActor(agent.describer).answer(
            (messages + listOf(
                "Software Graph `${taskConfig.input_graph_file}`:\n```json\n${inputFile.readText()}\n```",
                "Instruction: ${taskConfig.instruction}"
            )).filter { it.isNotBlank() }, api = api
        )
        val plan = PlanUtil.filterPlan { response.obj.tasksByID } ?: emptyMap()
        val planSummary = buildString {
            appendLine("# Graph-Based Planning Result")
            appendLine()
            appendLine("## Generated Plan (DAG)")
            appendLine("```json")
            appendLine(JsonUtil.toJson(plan))
            appendLine("```")
        }
        val planProcessingState = agent.executePlan(
            plan = plan, task = task, userMessage = taskConfig.instruction, api = api, api2 = api2
        )
        val executionSummary = buildString {
            appendLine("## Plan Execution Summary")
            appendLine("- Completed Tasks: ${planProcessingState.completedTasks.size}")
            appendLine("- Failed Tasks: ${plan.size - planProcessingState.completedTasks.size}")
            appendLine()
            appendLine("### Task Results:")
            planProcessingState.taskResult.forEach { (taskId, result) ->
                appendLine("#### $taskId")
                appendLine("```")
                appendLine(result.take(500)) // Limit output size
                appendLine("```")
            }
        }
        resultFn(planSummary + "\n\n" + executionSummary)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SoftwareGraphPlanningTask::class.java)
    }
}