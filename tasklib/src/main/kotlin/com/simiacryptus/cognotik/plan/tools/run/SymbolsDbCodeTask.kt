package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.Description
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.run.SymbolsDbCodeTask.SymbolsDbCodeTaskExecutionConfigData
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.model.ISessionTask
import org.slf4j.LoggerFactory

class SymbolsDbCodeTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: SymbolsDbCodeTaskExecutionConfigData?,
) : RunCodeTask<SymbolsDbCodeTaskExecutionConfigData, SymbolsDbCodeTask.SymbolsDbCodeTaskTypeConfig>(
  orchestrationConfig,
  planTask,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    @JvmStatic
    val SymbolsDbCode = TaskType(
      name = "SymbolsDbCodeTask",
      category = "Execution",
      taskClass = SymbolsDbCodeTask::class.java,
      executionConfigClass = SymbolsDbCodeTaskExecutionConfigData::class.java,
      taskSettingsClass = SymbolsDbCodeTaskTypeConfig::class.java,
      description = "Execute code snippets with predefined symbols",
      tooltipHtml = """
                Executes code snippets in an interactive environment with access to a symbol graph.
                <ul>
                  <li>Access to <code>symbols_db</code> (SymbolGraphService)</li>
                  <li>Query code symbols and relationships</li>
                  <li>User-approved code execution</li>
                  <li>Interactive result review</li>
                </ul>
            """.trimIndent(),
    )
  }

  override fun promptSegment(): String {
    val basePrompt = super.promptSegment()
    val customPrompt = typeConfig?.promptTemplate?.replace("{file}", typeConfig?.symbolFile ?: "unknown")
      ?: "You have access to a `symbols_db` object (SymbolGraphService) loaded from the project symbol graph."
    return """
            $basePrompt
            
            ### Symbols Database Access
            * $customPrompt
            * Use `symbols_db.findSymbol("name")` to locate code elements.
            * Use `symbols_db.getDependencies("name")` to analyze relationships.
        """.trimIndent()
  }

  override fun symbols(): Map<String, Any> = typeConfig?.let { typeConfig ->
    val file = root.toFile().resolve(typeConfig.symbolFile)
    log.info("Loading symbols database from ${file.absolutePath}")
    mapOf(
      "symbols_db" to SymbolGraphService().apply {
        if (file.exists()) load(file)
      }
    )
  } ?: emptyMap()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.newUserFileStream(transcriptFile())
    try {
      log.info("Starting SymbolsDbCodeTask - Goal: ${executionConfig?.goal}")
      task.add("### Initializing Symbols Database".renderMarkdown())
      transcript?.write("## Symbols Database Initialization\n".toByteArray())
      val configDetails = """
                <details>
                <summary>Configuration Details</summary>
                * **Symbol File:** `${typeConfig?.symbolFile}`
                * **Runtime:** `${typeConfig?.codeRuntime}`
                * **Working Dir:** `${executionConfig?.workingDir}`
                </details>
            """.trimIndent()
      transcript?.write(configDetails.toByteArray())
      super.run(agent, messages, task, resultFn, orchestrationConfig)
    } catch (e: Exception) {
      // Triple Log Rule
      task.error(e)
      log.error("Error executing SymbolsDbCodeTask", e)
      transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      throw e
    } finally {
      transcript?.close()
    }
  }


  override fun describer() = AbbrevWhitelistYamlDescriber("com.simiacryptus")

  open class SymbolsDbCodeTaskTypeConfig(
    @Description("The code runtime to use for execution (e.g., Groovy, Kotlin).")
    codeRuntime: CodeRuntimes? = CodeRuntimes.GroovyRuntime,
    @Description("The relative path to the symbol graph JSON file.")
    var symbolFile: String = "symbol_graph.json",
    @Description("The prompt template used to describe the symbols database to the LLM.")
    var promptTemplate: String = "You have access to a `symbols_db` object (SymbolGraphService) loaded from '{file}'."
  ) : RunCodeTaskTypeConfig(
    task_type = SymbolsDbCode.name,
    codeRuntime = codeRuntime,
    model = null,
    name = SymbolsDbCode.name,
  )

  open class SymbolsDbCodeTaskExecutionConfigData(
    @Description("The high-level goal or objective for the code execution.")
    goal: String? = null,
    @Description("The working directory where the code will be executed.")
    workingDir: String? = null,
    @Description("A detailed description of the specific task to be performed.")
    task_description: String? = null,
    @Description("A list of task IDs that must be completed before this task starts.")
    task_dependencies: List<String>? = null,
    @Description("The current execution state of the task.")
    state: TaskState? = null,
  ) : RunCodeTaskExecutionConfigData(
    goal = goal,
    workingDir = workingDir,
    task_description = task_description,
    task_dependencies = task_dependencies,
    state = state,
    task_type = SymbolsDbCode.name
  )
}