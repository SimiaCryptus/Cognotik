package com.simiacryptus.cognotik.plan.tools.graph

import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.apps.graph.SoftwareNodeType
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.AbstractTask
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File

class SoftwareGraphGenerationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SoftwareGraphGenerationTaskExecutionConfigData?
) : AbstractTask<SoftwareGraphGenerationTask.SoftwareGraphGenerationTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

    class SoftwareGraphGenerationTaskExecutionConfigData(
        @Description("The output file path where the software graph will be saved")
        val output_file: String = "software_graph.json",
        @Description("The type of nodes to focus on generating (e.g., CodeFile, CodePackage, etc.)")
        val node_types: List<String> = listOf(),
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        input_files: List<String>? = null,
        state: TaskState? = null
    ) : AbstractFileTask.FileTaskExecutionConfig(
        task_type = "SoftwareGraphGenerationTask",
        task_description = task_description,
        task_dependencies = task_dependencies,
        related_files = input_files,
        state = state
    )

    val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "aicoder.actions"
    ) {
        override val includeMethods: Boolean get() = false
    }

    override fun promptSegment() = """
    SoftwareGraphGenerationTask - Generate a SoftwareGraph representation of the codebase
      ** Specify the output file path for the generated graph
      ** Optionally specify node types to focus on
      ** List input files to analyze for graph generation
  """.trimIndent()

    fun getInputFileCode(): String {
        val inputFiles = executionConfig?.related_files ?: return ""
        return inputFiles.joinToString("\n\n") { filePath ->
            val file = File(filePath)
            if (file.exists()) {
                "### ${file.name}\n" + file.readText()
            } else {
                "### $filePath\nFile not found."
            }
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val graphGenerationActor = ParsedAgent<SoftwareNodeType.SoftwareGraph>(
            name = "SoftwareGraphGenerator",
            resultClass = SoftwareNodeType.SoftwareGraph::class.java,
            prompt = "Analyze the provided code files and generate a SoftwareGraph representation.\nThe graph should accurately represent the software architecture including:\n\nAvailable Node Types:\n" +
                    SoftwareNodeType.values().joinToString<SoftwareNodeType<out SoftwareNodeType.NodeBase<*>>>("\n") {
                        "* ${it.name}: ${it.description?.replace("\n", "\n  ")}\n    ${
                            describer.describe(rawType = it.nodeClass).lineSequence()
                                .map<String, String> {
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
                                .joinToString<String>("\n")
                        }"
                    } + "\n\nGenerate appropriate NodeId values for each node.\nEnsure all relationships between nodes are properly established.\nFormat the response as a valid SoftwareGraph JSON structure.",
            model = (typeConfig.model?.let<ApiChatModel, ChatInterface> { this.orchestrationConfig.instance(it) }
                ?: this.orchestrationConfig.defaultChatter).getChildClient(task),
            parsingModel = this.orchestrationConfig.parsingChatter,
            temperature = this.orchestrationConfig.temperature,
            describer = describer,
        )
        val chatMessages = graphGenerationActor.chatMessages(
            messages + listOf(
                getInputFileCode(),
                "Generate a SoftwareGraph for the above code focusing on these node types: ${
                    executionConfig?.node_types?.joinToString(
                        ", "
                    )
                }"
            ).filter { it.isNotBlank() },
        )
        val response = graphGenerationActor.respond(
            messages = chatMessages,
            input = messages,
        )

        val outputFile = File(orchestrationConfig.absoluteWorkingDir ?: ".").resolve(executionConfig?.output_file.let {
            when {
                it.isNullOrBlank() -> "software_graph.json"
                else -> it
            }
        })
        try {
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(JsonUtil.toJson(response.obj))

            val summary = buildString {
                appendLine("# Software Graph Generation Complete")
                appendLine()
                appendLine("Generated graph saved to: ${outputFile.absolutePath}")
                appendLine()
                appendLine("## Graph Statistics")
                appendLine("- Total nodes: ${response.obj.nodes.size}")
                appendLine("- Node types:")
                response.obj.nodes.groupBy { it.javaClass.simpleName }.forEach { (type, nodes) ->
                    appendLine("  - $type: ${nodes.size} nodes")
                }
            }

            task.add(MarkdownUtil.renderMarkdown(summary, ui = task.manager))
            resultFn(summary)
        } catch (e: Exception) {
            task.error(e)
            resultFn("Failed to save graph to ${outputFile.absolutePath}: ${e.message}")
        }
    }

    companion object {
    }
}