package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.tools.run.SymbolsDbCodeTask.SymbolsDbCodeTaskExecutionConfigData

class SymbolsDbCodeTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SymbolsDbCodeTaskExecutionConfigData?,
) : RunCodeTask<SymbolsDbCodeTaskExecutionConfigData, SymbolsDbCodeTask.SymbolsDbCodeTaskTypeConfig>(
    orchestrationConfig,
    planTask,
) {
    companion object {
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
                        <li>Access to `symbols_db` (SymbolGraphService)</li>
                        <li>Query code symbols and relationships</li>
                        <li>User-approved code execution</li>
                        <li>Interactive result review</li>
                      </ul>
                    """,
        )
    }

    override fun promptSegment() = super.promptSegment() + """
        
        You have access to a `symbols_db` object (SymbolGraphService) to assist with code execution.
    """.trimIndent()

    override fun symbols(): Map<String, Any> = typeConfig?.let { typeConfig ->
        mapOf(
            "symbols_db" to SymbolGraphService().apply {
                load(root.toFile().resolve(typeConfig.symbolFile))
            }
        )
    } ?: emptyMap()

    override fun describer() = AbbrevWhitelistYamlDescriber("com.simiacryptus")

    open class SymbolsDbCodeTaskTypeConfig(
        codeRuntime: CodeRuntimes? = CodeRuntimes.GroovyRuntime,
        val symbolFile: String = "symbol_graph.json",
    ) : RunCodeTaskTypeConfig(
        task_type = SymbolsDbCode.name,
        codeRuntime = codeRuntime,
        model = null,
        name = SymbolsDbCode.name,
    )

    open class SymbolsDbCodeTaskExecutionConfigData(
        goal: String? = null,
        workingDir: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
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