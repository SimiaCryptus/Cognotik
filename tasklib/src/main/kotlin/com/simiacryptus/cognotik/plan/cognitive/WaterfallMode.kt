package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.CoreTasks
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.PlanUtil.buildMermaidGraph
import com.simiacryptus.cognotik.plan.PlanUtil.filterPlan
import com.simiacryptus.cognotik.plan.TRIPLE_TILDE
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path

/**
 * A cognitive mode that implements the traditional plan-ahead strategy.
 */
open class WaterfallMode(
  orchestrationConfig: OrchestrationConfig,
  session: Session,
  user: User
) : CognitiveMode<WaterfallMode.WaterfallModeConfig>(
  orchestrationConfig,
  session,
  user
) {
  class WaterfallModeConfig(
    var planFile: String? = null,
    var variables: Map<String, String> = emptyMap()
  ) : CognitiveModeConfig(type = CoreTasks.Waterfall)


  private val log = LoggerFactory.getLogger(WaterfallMode::class.java)
  private var transcriptStream: FileOutputStream? = null

  override fun initialize(task: SessionTask) {
    log.debug("Initializing PlanAheadMode")
    transcriptStream = task.transcript()
  }

  override fun contextData(): List<String> = emptyList()

  override fun handleUserMessage(userMessage: String, task: SessionTask) {
    try {
      log.debug("Handling user message: $userMessage")
      transcriptStream?.let { stream ->
        stream.write("\n## User Message\n\n$userMessage\n\n".toByteArray())
        stream.flush()
      }
      execute(userMessage, task)
    } catch (e: Throwable) {
      log.error("Error in handleUserMessage", e)
      task.error(e)
    }
  }

  private fun execute(userMessage: String, task: SessionTask) {
    try {
      val coordinator = TaskOrchestrator(
        user = user,
        session = session,
        dataStorage = task.ui.dataStorage,
        root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
          ?: task.ui.dataStorage.getUserDir(user, session).toPath()
          ?: File(".").toPath(),
        transcriptStream = transcriptStream
      )


      val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
      val plan = if (config.planFile != null) {
        loadPrePlanned(userMessage, coordinator.root, task)
      } else {
        val describer = TaskContextYamlDescriber(orchestrationConfig)
        Tasks.initDescriber(orchestrationConfig, describer)
        val codeFiles = coordinator.codeFiles
        transcriptStream?.let { stream ->
          val fileList = codeFiles.entries.joinToString("\n") { (path, content) ->
            "* `$path` (${content.length} chars)"
          }
          stream.write("\n## Code Files\n\n${codeFiles.size} files:\n$fileList\n\n".toByteArray())
          stream.flush()
        }
        val plan = initialPlan(
          codeFiles = codeFiles,
          files = coordinator.files,
          root = coordinator.root,
          task = task,
          userMessage = userMessage,
          orchestrationConfig = orchestrationConfig,
          contextFn = { contextData() },
          describer = describer
        )
        transcriptStream?.let { stream ->
          stream.write("\n## Generated Plan\n\n${plan.planText}\n\n".toByteArray())
          stream.write(
            "\n### Plan Diagram\n\n```mermaid\n${
              buildMermaidGraph((filterPlan { plan.plan } ?: emptyMap()).toMap(),
                false)
            }\n```\n\n".toByteArray())
          stream.flush()
        }
        // Save plan to file for PrePlanned mode
        try {
          val planFile = coordinator.root.resolve(".logs/plan_${now()}.json").toFile()
          planFile.writeText(JsonUtil.toJson(plan))
          task.add("Plan saved to [${planFile.name}](${task.linkTo("plan.json")})".renderMarkdown())
        } catch (e: Exception) {
          log.warn("Failed to save plan json", e)
        }
        plan
      }
      task.header("Executing Plan")


      coordinator.executePlan(
        plan = plan.plan,
        task = task,
        userMessage = userMessage,
        orchestrationConfig = orchestrationConfig,
        // Use the budgeted and task-specific client
      )
      task.complete()
    } catch (e: Throwable) {
      task.error(e) // Report error on the current task
      log.error("Error in execute", e)
      transcriptStream?.let { stream ->
        stream.write("\n## Error\n\n```\n${e.message}\n${e.stackTraceToString()}\n```\n\n".toByteArray())
        stream.flush()
      }
    } finally {
      transcriptStream?.close()
    }
  }

  open fun initialPlan(
    codeFiles: Map<Path, String>,
    files: Array<File>,
    root: Path,
    task: SessionTask,
    userMessage: String,
    orchestrationConfig: OrchestrationConfig,
    contextFn: () -> List<String> = { emptyList() },
    describer: TypeDescriber
  ): TaskBreakdownWithPrompt {
    val toInput = inputFn(codeFiles, files, root)
    //task.echo(userMessage.renderMarkdown())
    return if (!orchestrationConfig.autoFix)
      Discussable(
        task = task,
        heading = "Plan Generation",
        userMessage = { userMessage },
        initialResponse = {
          newPlan(
            orchestrationConfig,
            toInput(userMessage) + contextFn(),
            describer,
            task
          )
        },
        outputFn = {
          try {
            render(
              withPrompt = TaskBreakdownWithPrompt(
                prompt = userMessage,
                plan = it.obj,
                planText = it.text
              )
            )
          } catch (e: Throwable) {
            log.warn("Error rendering task breakdown", e)
            task.error(e)
            e.message ?: e.javaClass.simpleName
          }
        },
        reviseResponse = { userMessages: List<Pair<String, ModelSchema.Role>> ->
          newPlan(
            orchestrationConfig,
            userMessages.map { it.first },
            describer,
            task
          )
        },
      ).call().let {
        TaskBreakdownWithPrompt(
          prompt = userMessage,
          plan = filterPlan { it?.obj } ?: emptyMap(),
          planText = it?.text ?: "(no plan generated)"
        )
      }
    else {
      newPlan(
        orchestrationConfig,
        toInput(userMessage) + contextFn(),
        describer,
        task
      ).let {
        TaskBreakdownWithPrompt(
          prompt = userMessage,
          plan = filterPlan { it.obj } ?: emptyMap(),
          planText = it.text
        )
      }
    }
  }

  data class TaskBreakdownWithPrompt(
    val prompt: String,
    val plan: Map<String, TaskExecutionConfig>,
    val planText: String
  )

  fun render(
    withPrompt: TaskBreakdownWithPrompt
  ): String {
    val map = mapOf(
      "Text" to withPrompt.planText.renderMarkdown(),
      "JSON" to "${TRIPLE_TILDE}json\n${JsonUtil.toJson(withPrompt)}\n${TRIPLE_TILDE}".renderMarkdown(),
      "Diagram" to (("```mermaid\n" + buildMermaidGraph(
        (filterPlan {
          withPrompt.plan
        } ?: emptyMap()).toMutableMap()
      ) + "\n```\n").renderMarkdown())
    )
    return TabbedDisplay.displayMapInTabs(map)
  }

  open fun newPlan(
    orchestrationConfig: OrchestrationConfig,
    inStrings: List<String>,
    describer: TypeDescriber,
    task: SessionTask
  ): ParsedResponse<Map<String, TaskExecutionConfig>> {
    orchestrationConfig.absoluteWorkingDir?.apply { File(this).mkdirs() }
    val planningActor = orchestrationConfig.planningActor(describer, task)
    return planningActor.respond(
      messages = planningActor.chatMessages(inStrings),
      input = inStrings,
    ).map(Map::class.java) {
      it.tasksByID ?: emptyMap<String, TaskExecutionConfig>()
    } as ParsedResponse<Map<String, TaskExecutionConfig>>
  }

  open fun inputFn(
    codeFiles: Map<Path, String>,
    files: Array<File>,
    root: Path
  ) = { str: String ->
    listOf(
      if (!codeFiles.all { it.key.toFile().isFile } || codeFiles.size > 2) {
        "Files:\n${codeFiles.keys.joinToString("\n") { "* $it" }}"
      } else {
        files.joinToString("\n\n") {
          val path = root.relativize(it.toPath())
          "\n## $path\n\n${(codeFiles[path] ?: "").let { "$TRIPLE_TILDE\n${it}\n$TRIPLE_TILDE" }}"
        }
      },
      str
    )
  }

  private fun loadPrePlanned(userMessage: String, root: Path, task: SessionTask): TaskBreakdownWithPrompt {
    val parsedConfig = parseConfig(userMessage, root.toString(), task)
    task.add("Loading plan from `${parsedConfig.planFile}` with variables: ${parsedConfig.variables}".renderMarkdown())
    val planFile = root.resolve(parsedConfig.planFile!!).toFile()
    if (!planFile.exists()) {
      throw IllegalArgumentException("Plan file not found: ${planFile.absolutePath}")
    }
    // Load and substitute variables
    val rawJson = planFile.readText()
    val genericPlan: MutableMap<String, Any> = JsonUtil.fromJson(rawJson, MutableMap::class.java)
    val processedPlan = replaceVariables(genericPlan, parsedConfig.variables)
    // Deserialize
    val planWrapper: TaskBreakdownWithPrompt = JsonUtil.fromJson(
      JsonUtil.toJson(processedPlan),
      TaskBreakdownWithPrompt::class.java
    )
    task.add("Plan loaded with ${planWrapper.plan.size} steps.")
    return planWrapper
  }

  private fun parseConfig(message: String, root: String, task: SessionTask): WaterfallModeConfig {
    val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
    val describer = TaskContextYamlDescriber(orchestrationConfig)
    Tasks.initDescriber(orchestrationConfig, describer)
    val availableFiles = FileSelectionUtils.getAvailableFiles(Path(root))
      .filter { it.endsWith(".json") }
      .joinToString("\n") { "      - $it" }
    val agent = ParsedAgent(
      name = "PrePlannedConfigParser",
      resultClass = WaterfallModeConfig::class.java,
      exampleInstance = WaterfallModeConfig(
        planFile = config.planFile,
        variables = config.variables
      ),
      prompt = """
Analyze the user request to identify the plan file to use and the variables to substitute.
The user wants to execute a pre-defined plan stored in a JSON file.
1. Identify the JSON file mentioned. If not explicitly mentioned, look for '${config.planFile}' or the most relevant file in the list below.
2. Extract any other parameters or instructions as variables. The keys should match placeholders likely found in the plan (e.g., {{key}}).
Available JSON files:
$availableFiles
            """,
      model = orchestrationConfig.defaultSmart.getChildClient(task),
      parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
      temperature = 0.1,
      describer = describer
    )
    return agent.answer(listOf(message)).obj
  }

  private fun replaceVariables(node: Any?, variables: Map<String, String>): Any? {
    return when (node) {
      is String -> {
        var result: String = node
        variables.forEach { (k, v) ->
          result = result.replace("{{$k}}", v)
        }
        result
      }

      is Map<*, *> -> node.entries.associate { (k, v) -> k to replaceVariables(v, variables) }
      is List<*> -> node.map { replaceVariables(it, variables) }
      else -> node
    }
  }

  companion object {
    val inputCnt = 1
    fun now(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date())
  }
}