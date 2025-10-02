package com.simiacryptus.cognotik.plan.tools.graph

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File

class SoftwareGraphPlanningTask(
    orchestrationConfig: OrchestrationConfig, planTask: GraphBasedPlanningTaskExecutionConfigData?
) : AbstractTask<SoftwareGraphPlanningTask.GraphBasedPlanningTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

    class GraphBasedPlanningTaskExecutionConfigData(
        @Description("REQUIRED: The path to the input software graph JSON file") val input_graph_file: String? = null,
        @Description("The instruction or goal to be achieved") val instruction: String = "",
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = "SoftwareGraphPlanningTask",
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = """
     GraphBasedPlanningTask - Use a software graph to generate an actionable sub-plan.
       ** Include the file path to the input graph file and the instruction.
    """.trimIndent()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val inputFile = (orchestrationConfig.absoluteWorkingDir?.let { File(it) } ?: File(".")).resolve(
            when {
                !executionConfig?.input_graph_file.isNullOrBlank() -> executionConfig?.input_graph_file!!
                else -> throw IllegalArgumentException("Input graph file not specified")
            }
        )
        if (!inputFile.exists()) throw IllegalArgumentException("Input graph file does not exist: ${inputFile.absolutePath}")
        val response = orchestrationConfig.planningActor(TaskContextYamlDescriber(orchestrationConfig)).answer(
            (messages + listOf(
                "Software Graph `${executionConfig.input_graph_file}`:\n```json\n${inputFile.readText()}\n```",
                "Instruction: ${executionConfig.instruction}"
            )).filter { it.isNotBlank() },
        )
        val plan = com.simiacryptus.cognotik.plan.PlanUtil.filterPlan { response.obj.tasksByID } ?: emptyMap()
        val planSummary = buildString {
            appendLine("# Graph-Based Planning Result")
            appendLine()
            appendLine("## Generated Plan (DAG)")
            appendLine("```json")
            appendLine(JsonUtil.toJson(plan))
            appendLine("```")
        }
        val planProcessingState = agent.executePlan(
            plan = plan,
            task = task,
            userMessage = executionConfig.instruction,
            orchestrationConfig = orchestrationConfig,
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
                appendLine(result.take(500))

                appendLine("```")
            }
        }
        resultFn(planSummary + "\n\n" + executionSummary)
    }

    companion object {
    }
}