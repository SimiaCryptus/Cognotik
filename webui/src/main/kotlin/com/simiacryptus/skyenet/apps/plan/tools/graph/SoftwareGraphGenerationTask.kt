package com.simiacryptus.skyenet.apps.plan.tools.graph

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.skyenet.apps.graph.SoftwareNodeType
import com.simiacryptus.skyenet.apps.plan.AbstractTask
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskType
import com.simiacryptus.skyenet.apps.plan.tools.file.AbstractFileTask
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File

class SoftwareGraphGenerationTask(
  planSettings: PlanSettings,
  planTask: SoftwareGraphGenerationTaskConfigData?
 ) : AbstractTask<SoftwareGraphGenerationTask.SoftwareGraphGenerationTaskConfigData>(planSettings, planTask) {
  data class GraphGenerationResult(
    @Description("The generated software graph representing the codebase structure")
    val graph: SoftwareNodeType.SoftwareGraph = SoftwareNodeType.SoftwareGraph()
  )

  class SoftwareGraphGenerationTaskConfigData(
    @Description("The output file path where the software graph will be saved")
    val output_file: String = "software_graph.json",
    @Description("The type of nodes to focus on generating (e.g., CodeFile, CodePackage, etc.)")
    val node_types: List<String> = listOf(),
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    input_files: List<String>? = null,
    state: TaskState? = null
  ) : AbstractFileTask.FileTaskConfigBase(
    task_type = TaskType.SoftwareGraphGeneration.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    input_files = input_files,
    state = state
  )

  val exampleInstance by lazy { GraphGenerationResult(SoftwareNodeType.SoftwareGraph().apply {

  }) }

  val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "aicoder.actions"
  ) {
    override val includeMethods: Boolean get() = false
  }
  private val graphGenerationActor by lazy {
    ParsedActor(
      name = "SoftwareGraphGenerator",
      resultClass = GraphGenerationResult::class.java,
      prompt = "Analyze the provided code files and generate a SoftwareGraph representation.\nThe graph should accurately represent the software architecture including:\n\nAvailable Node Types:\n" +
              SoftwareNodeType.values().joinToString("\n") {
            "* ${it.name}: ${it.description?.replace("\n","\n  ")}\n    ${describer.describe(it.nodeClass).replace("\n", "\n    ")}"
          } + "\n\nGenerate appropriate NodeId values for each node.\nEnsure all relationships between nodes are properly established.\nFormat the response as a valid SoftwareGraph JSON structure.",
      model = planSettings.getTaskSettings(TaskType.SoftwareGraphGeneration).model ?: planSettings.defaultModel,
      parsingModel = planSettings.parsingModel,
      temperature = planSettings.temperature,
      describer = planSettings.describer(),
      exampleInstance = exampleInstance
    )
  }

  override fun promptSegment() = """
    SoftwareGraphGeneration - Generate a SoftwareGraph representation of the codebase
      ** Specify the output file path for the generated graph
      ** Optionally specify node types to focus on
      ** List input files to analyze for graph generation
  """.trimIndent()

  fun getInputFileCode(): String {
    val inputFiles = taskConfig?.input_files ?: return ""
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
    agent: PlanCoordinator,
    messages: List<String>,
    task: SessionTask,
    api: ChatClient,
    resultFn: (String) -> Unit,
    api2: OpenAIClient,
    planSettings: PlanSettings
  ) {
    // Generate the graph
    val chatMessages = graphGenerationActor.chatMessages(
      messages + listOf(
        getInputFileCode(),
        "Generate a SoftwareGraph for the above code focusing on these node types: ${
          taskConfig?.node_types?.joinToString(
            ", "
          )
        }"
      ).filter { it.isNotBlank() },
    )
    val response = graphGenerationActor.respond(
      messages = chatMessages,
      input = messages,
      api = api
    )

    // Parse and validate the generated graph
    val graph = response.obj.graph

    // Save the graph to file
    val outputFile = File(planSettings.workingDir ?: ".").resolve(taskConfig?.output_file.let { when {
          it.isNullOrBlank() -> "software_graph.json"
          else -> it
      }
    })
    try {
      outputFile.parentFile?.mkdirs()
      outputFile.writeText(JsonUtil.toJson(graph))
      
      val summary = buildString {
        appendLine("# Software Graph Generation Complete")
        appendLine()
        appendLine("Generated graph saved to: ${outputFile.absolutePath}")
        appendLine()
        appendLine("## Graph Statistics")
        appendLine("- Total nodes: ${graph.nodes.size}")
        appendLine("- Node types:")
        graph.nodes.groupBy { it.javaClass.simpleName }
          .forEach { (type, nodes) ->
            appendLine("  - $type: ${nodes.size} nodes")
          }
      }
      
      task.add(MarkdownUtil.renderMarkdown(summary, ui = agent.ui))
      resultFn(summary)
    } catch (e: Exception) {
      task.error(ui = null, e)
      resultFn("Failed to save graph to ${outputFile.absolutePath}: ${e.message}")
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(SoftwareGraphGenerationTask::class.java)
  }
}