package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.tools.RunCodeTask
import com.simiacryptus.cognotik.plan.tools.RunCodeTask.RunCodeTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.RunCodeTask.RunCodeTaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.SelfHealingTask
import com.simiacryptus.cognotik.plan.tools.file.AnalysisTask
import com.simiacryptus.cognotik.plan.tools.file.AnalysisTask.Companion.Analysis
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask
 import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.Companion.FileSearch
 import com.simiacryptus.cognotik.plan.tools.knowledge.KnowledgeIndexingTask
 import com.simiacryptus.cognotik.plan.tools.knowledge.VectorSearchTask
import com.simiacryptus.cognotik.plan.tools.mcp.MCPToolTask
import com.simiacryptus.cognotik.plan.tools.mcp.MCPToolTask.Companion.MCPTool
 import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
 import com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask
 import com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask
import com.simiacryptus.cognotik.plan.tools.session.RunShellCommandTask
import com.simiacryptus.cognotik.plan.tools.session.RunShellCommandTask.RunShellCommandTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.session.SeleniumSessionTask
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer

@JsonDeserialize(using = TaskTypeDeserializer::class)
@JsonSerialize(using = TaskTypeSerializer::class)
class TaskType<out T : TaskExecutionConfig, out U : TaskTypeConfig>(
    name: String,
    val executionConfigClass: Class<out T>,
    val taskSettingsClass: Class<out U>,
    val description: String? = null,
    val tooltipHtml: String? = null,
) : DynamicEnum<TaskType<*, *>>(name) {

    companion object {
        val VectorSearch = TaskType(
            "VectorSearch",
            VectorSearchTask.VectorSearchTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Perform semantic search using AI embeddings",
            """
                      Performs semantic search using AI embeddings across indexed content.
                      <ul>
                        <li>Uses OpenAI embeddings for semantic matching</li>
                        <li>Supports positive and negative search queries</li>
                        <li>Configurable similarity metrics and thresholds</li>
                        <li>Regular expression filtering capabilities</li>
                        <li>Returns ranked results with context</li>
                      </ul>
                    """
        )

        val RunShellCommand = TaskType(
            "RunShellCommand",
            RunShellCommandTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Execute shell commands safely",
            """
          Executes shell commands in a controlled environment.
          <ul>
            <li>Safe command execution handling</li>
            <li>Working directory configuration</li>
            <li>Output capture and formatting</li>
            <li>Error handling and reporting</li>
            <li>Interactive result review</li>
          </ul>
        """
        )
        val RunCode = TaskType(
            "RunCode",
            RunCodeTaskExecutionConfigData::class.java,
            RunCodeTaskTypeConfig::class.java,
            "Execute code snippets safely",
            """
          Executes code snippets in a controlled environment.
          <ul>
            <li>Safe code execution handling</li>
            <li>Working directory configuration</li>
            <li>Output capture and formatting</li>
            <li>Error handling and reporting</li>
            <li>Interactive result review</li>
          </ul>
        """
        )
        val SelfHealing = TaskType(
            "SelfHealing",
            SelfHealingTask.SelfHealingTaskExecutionConfigData::class.java,
            SelfHealingTask.SelfHealingTaskTypeConfig::class.java,
            "Run a command and automatically fix any issues that arise",
            """
          Executes a command and automatically fixes any issues that arise.
          <ul>
            <li>Specify commands and working directories</li>
            <li>Supports multiple commands and directories</li>
            <li>Interactive approval mode</li>
            <li>Output diff formatting</li>
          </ul>
        """
        )
        val GitHubSearch = TaskType(
            "GitHubSearch",
            GitHubSearchTask.GitHubSearchTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Search GitHub repositories, code, issues and users",
            """
          Performs comprehensive searches across GitHub's content.
          <ul>
            <li>Searches repositories, code, and issues</li>
            <li>Supports advanced search queries</li>
            <li>Filters results by various criteria</li>
            <li>Formats results with relevant details</li>
            <li>Handles API rate limiting</li>
          </ul>
        """
        )
        val KnowledgeIndexing = TaskType( // TODO: This should be automatically done as needed during embedding search
            "KnowledgeIndexing",
            KnowledgeIndexingTask.KnowledgeIndexingTaskExecutionConfigData::class.java,
                TaskTypeConfig::class.java,
                "Index content for semantic search capabilities",
                """
          Indexes documents and code for semantic search capabilities.
          <ul>
            <li>Processes both documentation and source code</li>
            <li>Creates searchable content chunks</li>
            <li>Supports parallel processing</li>
            <li>Configurable chunking strategies</li>
            <li>Progress tracking and reporting</li>
          </ul>
        """
            )
        val SeleniumSession = TaskType(
            "SeleniumSession",
            SeleniumSessionTask.SeleniumSessionTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Automate browser interactions with Selenium",
            """
          Automates browser interactions using Selenium WebDriver.
          <ul>
            <li>Headless Chrome browser automation</li>
            <li>JavaScript command execution</li>
            <li>Session management capabilities</li>
            <li>Configurable timeouts</li>
            <li>Detailed execution results</li>
          </ul>
        """
        )
        val CommandSession = TaskType(
            "CommandSession",
            CommandSessionTask.CommandSessionTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Manage interactive command-line sessions",
            """
          Manages interactive command-line sessions with state persistence.
          <ul>
            <li>Creates and maintains command sessions</li>
            <li>Supports multiple concurrent sessions</li>
            <li>Configurable timeouts and cleanup</li>
            <li>Session state preservation</li>
            <li>Comprehensive output capture</li>
          </ul>
        """
        )
        val CrawlerAgent = TaskType(
            "CrawlerAgent",
            CrawlerAgentTask.CrawlerTaskExecutionConfigData::class.java,
            CrawlerAgentTask.CrawlerTaskTypeConfig::class.java,
            "Search Google, fetch top results, and analyze content",
            """
          Searches Google for specified queries and analyzes the top results.
          <ul>
            <li>Performs Google searches</li>
            <li>Fetches top search results</li>
            <li>Analyzes content for specific goals</li>
            <li>Generates detailed analysis reports</li>
</ul>
        """
        )
        val MCPTool = TaskType(
            "MCPTool",
            MCPToolTask.MCPToolTaskExecutionConfigData::class.java,
            MCPToolTask.MCPToolTaskTypeConfig::class.java,
            "Execute tools from Model Context Protocol servers",
            """
              Executes tools from MCP (Model Context Protocol) servers.
              <ul>
                <li>Connect to MCP servers via various transports</li>
                <li>Execute tools with custom arguments</li>
                <li>Configurable timeouts and retry logic</li>
                <li>Support for multiple MCP server integrations</li>
                <li>Structured result handling</li>
              </ul>
            """
        )

        init {
            registerConstructor(Analysis) { settings, task ->
                AnalysisTask(
                    settings,
                    task
                )
            }
            registerConstructor(CommandSession) { settings, task ->
                CommandSessionTask(
                    settings,
                    task
                )
            }
            registerConstructor(CrawlerAgent) { settings, task ->
                CrawlerAgentTask(
                    settings,
                    task
                )
            }
            registerConstructor(FileModification) { settings, task ->
                FileModificationTask(
                    settings,
                    task
                )
            }
            registerConstructor(FileSearch) { settings, task ->
                FileSearchTask(
                    settings,
                    task
                )
            }
            registerConstructor(KnowledgeIndexing) { settings, task ->
                KnowledgeIndexingTask(
                    settings,
                    task
                )
            }
            registerConstructor(GitHubSearch) { settings, task ->
                GitHubSearchTask(
                    settings,
                    task
                )
            }
            registerConstructor(RunShellCommand) { settings, task ->
                RunShellCommandTask(
                    settings,
                    task
                )
            }
            registerConstructor(RunCode) { settings, task ->
                RunCodeTask(
                    settings,
                    task
                )
            }
            registerConstructor(SeleniumSession) { settings, task ->
                SeleniumSessionTask(
                    settings,
                    task
                )
            }
            registerConstructor(SelfHealing) { settings, task ->
                SelfHealingTask(
                    settings,
                    task
                )
            }
registerConstructor(VectorSearch) { settings, task ->
                VectorSearchTask(
                    settings,
                    task
                )
            }
            registerConstructor(MCPTool) { settings, task ->
                MCPToolTask(
                    settings,
                    task
                )
            }
        }

        fun <T : TaskExecutionConfig, U : TaskTypeConfig> registerConstructor(
            taskType: TaskType<T, U>, constructor: (OrchestrationConfig, T?) -> AbstractTask<T,U>
        ) {
            taskConstructors[taskType] = { settings: OrchestrationConfig, task: TaskExecutionConfig? ->
                constructor(settings, task as T?) as AbstractTask<TaskExecutionConfig, TaskTypeConfig>
            }
            register(taskType)
        }

        fun values() = values(TaskType::class.java)

        fun getImpl(
            orchestrationConfig: OrchestrationConfig, planTask: TaskExecutionConfig?, strict: Boolean = true
        ) = getImpl(
            orchestrationConfig = orchestrationConfig,
            taskType = planTask?.task_type?.let { valueOf(it) } ?: throw RuntimeException("Task type not specified"),
            planTask = planTask
        )

        fun getImpl(
            orchestrationConfig: OrchestrationConfig,
            taskType: TaskType<*, *>,
            planTask: TaskExecutionConfig? = null
        ): AbstractTask<out TaskExecutionConfig, TaskTypeConfig> {
            val constructor = taskConstructors[taskType]
            if (constructor == null) {
                throw RuntimeException("Unknown task type: ${taskType.name}")
            }
            return constructor(orchestrationConfig, planTask)
        }

        fun getAvailableTaskTypes(orchestrationConfig: OrchestrationConfig) = orchestrationConfig.taskSettings
            .mapNotNull { x -> valueOf(x.value.task_type ?: return@mapNotNull null) }

        fun valueOf(name: String): TaskType<*, *> = valueOf(TaskType::class.java, name)

        private fun register(taskType: TaskType<*, *>) = register(TaskType::class.java, taskType)
    }

}

class TaskTypeSerializer : DynamicEnumSerializer<TaskType<*, *>>(TaskType::class.java)

class TaskTypeDeserializer : DynamicEnumDeserializer<TaskType<*, *>>(TaskType::class.java)
private val taskConstructors =
    mutableMapOf<TaskType<*, *>, (OrchestrationConfig, TaskExecutionConfig?) -> AbstractTask<out TaskExecutionConfig, TaskTypeConfig>>()