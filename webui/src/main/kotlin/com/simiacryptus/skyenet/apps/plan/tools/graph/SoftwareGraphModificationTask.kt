package com.simiacryptus.skyenet.apps.plan.tools.graph

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.graph.SoftwareNodeType
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File

class SoftwareGraphModificationTask(
    planSettings: PlanSettings,
    planTask: SoftwareGraphModificationTaskConfigData?
) : AbstractTask<SoftwareGraphModificationTask.SoftwareGraphModificationTaskConfigData>(planSettings, planTask) {

    class SoftwareGraphModificationTaskConfigData(
        @Description("The path to the input software graph JSON file")
        val input_graph_file: String? = null,
        @Description("The path where the modified graph will be saved")
        val output_graph_file: String? = null,
        @Description("The modification goal or instructions")
        val modification_goal: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskConfigBase(
        task_type = TaskType.SoftwareGraphModification.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    private val graphModificationActor by lazy {
        ParsedActor(
            name = "SoftwareGraphModification",
            resultClass = SoftwareNodeType.SoftwareGraph::class.java,
            prompt = """
                Analyze the provided software graph and generate modifications based on the given goal.
                Return only the delta changes that should be applied to the graph.
                
                Consider:
                - Only include nodes that need to be modified
                - Preserve existing relationships where appropriate
                - Ensure all new NodeId values are unique
                - Validate all references between nodes
                
                Format the response as a valid SoftwareGraph JSON structure containing only the delta changes.
                
                Node Types:
                """.trimIndent() + SoftwareNodeType.values().joinToString("\n") {
                "* " + it.name + ": " + it.description?.prependIndent("  ") +
                        "\n    " + planSettings.describer().describe(rawType = it.nodeClass).lineSequence()
                    .map {
                        when {
                            it.isBlank() -> {
                                when {
                                    it.length < "  ".length -> "  "
                                    else -> it
                                }
                            }

                            else -> "  " + it
                        }
                    }
                    .joinToString("\n")
            },
            model = planSettings.getTaskSettings(TaskType.SoftwareGraphModification).model ?: planSettings.defaultModel,
            parsingModel = planSettings.parsingModel,
            temperature = planSettings.temperature,
            describer = planSettings.describer(),
        )
    }

    override fun promptSegment() = """
     SoftwareGraphModification - Load, modify and save software graph representations
       ** Specify the input graph file path
       ** Specify the output graph file path (optional, defaults to input file)
       ** Describe the desired modifications to the graph
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
        // Load the input graph
        val inputFile = (planSettings.workingDir?.let { File(it) } ?: File("."))
            .resolve(taskConfig?.input_graph_file ?: throw IllegalArgumentException("Input graph file not specified"))
        if (!inputFile.exists()) throw IllegalArgumentException("Input graph file does not exist: ${inputFile.absolutePath}")
        val originalGraph = JsonUtil.fromJson<SoftwareNodeType.SoftwareGraph>(inputFile.readText(), SoftwareNodeType.SoftwareGraph::class.java)

        // Generate the modification delta
        val response = graphModificationActor.answer(
            messages + listOf(
                "Current graph:\n```json\n${JsonUtil.toJson(originalGraph)}\n```",
                "Modification goal: ${taskConfig.modification_goal}"
            ),
            api = api
        )

        // Apply the delta to create the new graph
        val deltaGraph = response.obj
        val newGraph = originalGraph + deltaGraph

        // Save the modified graph
        val outputFile = (planSettings.workingDir?.let { File(it) } ?: File("."))
            .resolve(
                when {
                    !taskConfig.output_graph_file.isNullOrBlank() -> taskConfig.output_graph_file
                    taskConfig.input_graph_file.isNotBlank() -> taskConfig.input_graph_file
                    else -> "modified_graph.json"
                }
            )
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(JsonUtil.toJson(newGraph))

        // Generate summary
        val summary = buildString {
            appendLine("# Software Graph Modification Complete")
            appendLine()
            appendLine("Modified graph saved to: ${outputFile.absolutePath}")
            appendLine()
            appendLine("## Modification Summary")
            appendLine("### Changes Applied:")
            deltaGraph.nodes.groupBy { it.javaClass.simpleName }.forEach { (type, nodes) ->
                appendLine("- $type: ${nodes.size} node(s) modified")
            }
            appendLine()
            appendLine("### Final Graph Statistics:")
            appendLine("- Total nodes: ${newGraph.nodes.size}")
            appendLine("- Node types:")
            newGraph.nodes.groupBy { it.javaClass.simpleName }.forEach { (type, nodes) ->
                appendLine("  - $type: ${nodes.size} nodes")
            }
        }

        task.add(MarkdownUtil.renderMarkdown(summary, ui = agent.ui))
        resultFn(summary)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SoftwareGraphModificationTask::class.java)
    }
}