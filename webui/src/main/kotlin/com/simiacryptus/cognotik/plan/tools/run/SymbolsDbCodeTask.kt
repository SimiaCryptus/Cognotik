package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.describe.AbbrevWhitelistTSDescriber
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.tools.run.SymbolsDbCodeTask.SymbolsDbCodeTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApiChatModel

class SymbolsDbCodeTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SymbolsDbCodeTaskExecutionConfigData?,
) : RunCodeTask<SymbolsDbCodeTaskExecutionConfigData, SymbolsDbCodeTask.SymbolsDbCodeTaskTypeConfig>(
    orchestrationConfig,
    planTask,
) {
    companion object {
        val SymbolsDbCode = TaskType(
            "SymbolsDbCodeTask",
            "Execution & Automation",
            SymbolsDbCodeTask::class.java,
            SymbolsDbCodeTaskExecutionConfigData::class.java,
            SymbolsDbCodeTaskTypeConfig::class.java,
            "Execute code snippets with predefined symbols",
            """
          Executes code snippets in an interactive environment with access to predefined symbols.
          <ul>
            <li>User-approved code execution</li>
            <li>Working directory configuration</li>
            <li>Output capture and formatting</li>
            <li>Error handling and reporting</li>
            <li>Interactive result review</li>
            <li>Access to predefined symbols for enhanced functionality</li>
          </ul>
        """,
        )
    }

    override fun symbols(): Map<String, Any> = typeConfig?.let {  typeConfig ->
        mapOf(
            "symbols_db" to SymbolGraphService().apply {
                load(root.toFile().resolve(typeConfig.symbolFile))
            }
        )
    } ?: emptyMap()

    override fun describer() = AbbrevWhitelistTSDescriber("com.simiacryptus")

    open class SymbolsDbCodeTaskTypeConfig(
        codeRuntime: CodeRuntimes? = null,
        model: ApiChatModel? = null,
        name: String? = SymbolsDbCode.name,
        val symbolFile: String = "symbol_graph.json",
    ) : RunCodeTaskTypeConfig(
        task_type = SymbolsDbCode.name,
        codeRuntime = codeRuntime,
        model = model,
        name = name,
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