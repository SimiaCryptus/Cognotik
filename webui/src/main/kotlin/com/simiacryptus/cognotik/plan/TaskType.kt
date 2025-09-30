package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.tools.RunCodeTask.RunCodeTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.RunCodeTask.RunCodeTaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.session.RunShellCommandTask.RunShellCommandTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.file.AnalysisTask.Companion.AnalysisTaskType
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModificationTaskType
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.Companion.FileSearchTaskType
import com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask
import com.simiacryptus.cognotik.plan.tools.session.RunShellCommandTask
import com.simiacryptus.cognotik.plan.tools.session.SeleniumSessionTask
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer

@JsonDeserialize(using = TaskTypeDeserializer::class)
@JsonSerialize(using = TaskTypeSerializer::class)
class TaskType<out T : TaskExecutionConfig, out U : TaskTypeConfig>(
    name: String,
    val taskDataClass: Class<out T>,
    val taskSettingsClass: Class<out U>,
    val description: String? = null,
    val tooltipHtml: String? = null,
) : DynamicEnum<TaskType<*, *>>(name) {

    companion object {
        val VectorSearchTask = TaskType(
            "VectorSearchTask",
            com.simiacryptus.cognotik.plan.tools.knowledge.VectorSearchTask.VectorSearchTaskExecutionConfigData::class.java,
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

        val RunShellCommandTask = TaskType(
            "RunShellCommandTask",
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
        val RunCodeTask = TaskType(
            "RunCodeTask",
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
        val SelfHealingTask = TaskType(
            "SelfHealingTask",
            com.simiacryptus.cognotik.plan.tools.SelfHealingTask.SelfHealingTaskExecutionConfigData::class.java,
            com.simiacryptus.cognotik.plan.tools.SelfHealingTask.SelfHealingTaskTypeConfig::class.java,
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
        val GitHubSearchTask = TaskType(
            "GitHubSearchTask",
            com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask.GitHubSearchTaskExecutionConfigData::class.java,
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
        val KnowledgeIndexingTask = TaskType( // TODO: This should be automatically done as needed during embedding search
                "KnowledgeIndexingTask",
                com.simiacryptus.cognotik.plan.tools.knowledge.KnowledgeIndexingTask.KnowledgeIndexingTaskExecutionConfigData::class.java,
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
        val SeleniumSessionTask = TaskType(
            "SeleniumSessionTask",
            com.simiacryptus.cognotik.plan.tools.session.SeleniumSessionTask.SeleniumSessionTaskExecutionConfigData::class.java,
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
        val CommandSessionTask = TaskType(
            "CommandSessionTask",
            com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask.CommandSessionTaskExecutionConfigData::class.java,
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
        val CrawlerAgentTask = TaskType(
            "CrawlerAgentTask",
            com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskExecutionConfigData::class.java,
            com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskTypeConfig::class.java,
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
        init {
            registerConstructor(SelfHealingTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.SelfHealingTask(
                    settings,
                    task
                )
            }
            registerConstructor(AnalysisTaskType) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.file.AnalysisTask(
                    settings,
                    task
                )
            }
            registerConstructor(FileSearchTaskType) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.file.FileSearchTask(
                    settings,
                    task
                )
            }
            registerConstructor(CrawlerAgentTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask(
                    settings,
                    task
                )
            }
            registerConstructor(VectorSearchTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.knowledge.VectorSearchTask(
                    settings,
                    task
                )
            }
            registerConstructor(FileModificationTaskType) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.file.FileModificationTask(
                    settings,
                    task
                )
            }
            registerConstructor(RunShellCommandTask) { settings, task ->
                RunShellCommandTask(
                    settings,
                    task
                )
            }
            registerConstructor(RunCodeTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.RunCodeTask(
                    settings,
                    task
                )
            }
            registerConstructor(GitHubSearchTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask(
                    settings,
                    task
                )
            }
            registerConstructor(KnowledgeIndexingTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.knowledge.KnowledgeIndexingTask(
                    settings,
                    task
                )
            }
            registerConstructor(SeleniumSessionTask) { settings, task ->
                SeleniumSessionTask(
                    settings,
                    task
                )
            }
            registerConstructor(CommandSessionTask) { settings, task ->
                CommandSessionTask(
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
            planTask = planTask,
            strict = strict)
        fun getImpl(
            orchestrationConfig: OrchestrationConfig,
            taskType: TaskType<*, *>,
            planTask: TaskExecutionConfig? = null,
            strict: Boolean = true
        ): AbstractTask<out TaskExecutionConfig, TaskTypeConfig> {
            if (strict && !orchestrationConfig.getTaskSettings(taskType).enabled) {
                throw DisabledTaskException(taskType)
            }
            val constructor = taskConstructors[taskType]
            if (constructor == null) {
                throw RuntimeException("Unknown task type: ${taskType.name}")
            }
            return constructor(orchestrationConfig, planTask)
        }

        fun getAvailableTaskTypes(orchestrationConfig: OrchestrationConfig) = values().filter {
            orchestrationConfig.getTaskSettings(it).enabled
        }

        fun valueOf(name: String): TaskType<*, *> = valueOf(TaskType::class.java, name)

        private fun register(taskType: TaskType<*, *>) = register(TaskType::class.java, taskType)
    }

}

class TaskTypeSerializer : DynamicEnumSerializer<TaskType<*, *>>(TaskType::class.java)

class TaskTypeDeserializer : DynamicEnumDeserializer<TaskType<*, *>>(TaskType::class.java)
private val taskConstructors =
    mutableMapOf<TaskType<*, *>, (OrchestrationConfig, TaskExecutionConfig?) -> AbstractTask<out TaskExecutionConfig, TaskTypeConfig>>()
