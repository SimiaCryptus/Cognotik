package com.simiacryptus.cognotik.plan.tools.graph

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.graph.SoftwareNodeType
import com.simiacryptus.cognotik.plan.AbstractTask
import com.simiacryptus.cognotik.plan.PlanCoordinator
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File

class SoftwareGraphGenerationTask(
  planSettings: PlanSettings,
  planTask: SoftwareGraphGenerationTaskConfigData?
 ) : AbstractTask<SoftwareGraphGenerationTask.SoftwareGraphGenerationTaskConfigData>(planSettings, planTask) {

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
    task_type = TaskType.SoftwareGraphGenerationTask.name,
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
  private val graphGenerationActor by lazy {
    ParsedActor(
      name = "SoftwareGraphGenerator",
      resultClass = SoftwareNodeType.SoftwareGraph::class.java,
      prompt = "Analyze the provided code files and generate a SoftwareGraph representation.\nThe graph should accurately represent the software architecture including:\n\nAvailable Node Types:\n" +
              SoftwareNodeType.values().joinToString("\n") {
                "* ${it.name}: ${it.description?.replace("\n", "\n  ")}\n    ${
                  describer.describe(rawType = it.nodeClass).lineSequence()
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
                }"
          } + "\n\nGenerate appropriate NodeId values for each node.\nEnsure all relationships between nodes are properly established.\nFormat the response as a valid SoftwareGraph JSON structure.",
      model = taskSettings.model ?: planSettings.defaultModel,
      parsingModel = planSettings.parsingModel,
      temperature = planSettings.temperature,
      describer = describer,
    )
  }

  override fun promptSegment() = """
    SoftwareGraphGenerationTask - Generate a SoftwareGraph representation of the codebase
      ** Specify the output file path for the generated graph
      ** Optionally specify node types to focus on
      ** List input files to analyze for graph generation
  """.trimIndent()

  fun getInputFileCode(): String {
    val inputFiles = taskConfig?.related_files ?: return ""
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

    // Save the graph to file
    val outputFile = File(planSettings.workingDir ?: ".").resolve(taskConfig?.output_file.let { when {
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