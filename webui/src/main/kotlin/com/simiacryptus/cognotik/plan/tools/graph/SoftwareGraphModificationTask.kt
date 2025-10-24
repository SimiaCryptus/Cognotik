package com.simiacryptus.cognotik.plan.tools.graph

import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.apps.graph.SoftwareNodeType
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream

class SoftwareGraphModificationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: SoftwareGraphModificationTaskExecutionConfigData?
) : AbstractTask<SoftwareGraphModificationTask.SoftwareGraphModificationTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

  class SoftwareGraphModificationTaskExecutionConfigData(
    @Description("The path to the input software graph JSON file")
    val input_graph_file: String? = null,
    @Description("The path where the modified graph will be saved")
    val output_graph_file: String? = null,
    @Description("The modification goal or instructions")
    val modification_goal: String? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : TaskExecutionConfig(
    task_type = "SoftwareGraphModification",
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment() = """
     SoftwareGraphModification - Load, modify and save software graph representations
       ** Specify the input graph file path
       ** Specify the output graph file path (optional, defaults to input file)
       ** Describe the desired modifications to the graph
   """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val typeConfig = typeConfig ?: throw RuntimeException()
    val transcript = transcript(task)
    val graphModificationActor = ParsedAgent(
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
            "\n    " + TaskContextYamlDescriber(orchestrationConfig).describe(rawType = it.nodeClass).lineSequence()
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
      model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
        ?: orchestrationConfig.defaultChatter).getChildClient(task),
      parsingChatter = orchestrationConfig.parsingChatter,
      temperature = orchestrationConfig.temperature,
      describer = TaskContextYamlDescriber(orchestrationConfig),
    )

    val inputFile = (orchestrationConfig.absoluteWorkingDir?.let { File(it) } ?: File("."))
      .resolve(executionConfig?.input_graph_file ?: throw IllegalArgumentException("Input graph file not specified"))
    if (!inputFile.exists()) throw IllegalArgumentException("Input graph file does not exist: ${inputFile.absolutePath}")

    transcript?.write("# Software Graph Modification Task\n\n".toByteArray())
    transcript?.write("## Input\n\n".toByteArray())
    transcript?.write("- Input file: ${inputFile.absolutePath}\n".toByteArray())
    transcript?.write("- Modification goal: ${executionConfig.modification_goal}\n\n".toByteArray())
    val originalGraph = JsonUtil.fromJson<SoftwareNodeType.SoftwareGraph>(
      inputFile.readText(),
      SoftwareNodeType.SoftwareGraph::class.java
    )

    transcript?.write("## Original Graph Statistics\n\n".toByteArray())
    transcript?.write("- Total nodes: ${originalGraph.nodes.size}\n".toByteArray())
    transcript?.write(
      "- Node types: ${
        originalGraph.nodes.groupBy { it.javaClass.simpleName }.map { "${it.key}: ${it.value.size}" }.joinToString(", ")
      }\n\n".toByteArray()
    )
    val response = graphModificationActor.answer(
      messages + listOf(
        "Current graph:\n```json\n${JsonUtil.toJson(originalGraph)}\n```",
        "Modification goal: ${executionConfig.modification_goal}"
      ),
    )

    val deltaGraph = response.obj
    transcript?.write("## Delta Changes\n\n".toByteArray())
    transcript?.write("```json\n${JsonUtil.toJson(deltaGraph)}\n```\n\n".toByteArray())

    val newGraph = originalGraph + deltaGraph

    val outputFile = (orchestrationConfig.absoluteWorkingDir?.let { File(it) } ?: File("."))
      .resolve(
        when {
          !executionConfig.output_graph_file.isNullOrBlank() -> executionConfig.output_graph_file
          executionConfig.input_graph_file.isNotBlank() -> executionConfig.input_graph_file
          else -> "modified_graph.json"
        }
      )
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(JsonUtil.toJson(newGraph))

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

    task.add((summary.renderMarkdown))
    transcript?.write("## Summary\n\n".toByteArray())
    transcript?.write(summary.toByteArray())
    transcript?.flush()
    transcript?.close()

    resultFn(summary)
  }

  companion object {
    private fun transcript(task: SessionTask): FileOutputStream? {
      val (link, file) = task.createFile("transcript.md")
      val markdownTranscript = file?.outputStream()
      task.complete(
        "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
          link.removeSuffix(
            ".md"
          )
        }.pdf' target='_blank'>pdf</a>"
      )
      return markdownTranscript
    }

  }
}