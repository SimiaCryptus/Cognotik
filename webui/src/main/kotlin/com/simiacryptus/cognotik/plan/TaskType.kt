package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.tools.RunCodeTask.RunCodeTaskConfigData
import com.simiacryptus.cognotik.plan.tools.RunCodeTask.RunCodeTaskSettings
import com.simiacryptus.cognotik.plan.tools.session.RunShellCommandTask.RunShellCommandTaskConfigData
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
class TaskType<out T : TaskConfigBase, out U : TaskSettingsBase>(
    name: String,
    val taskDataClass: Class<out T>,
    val taskSettingsClass: Class<out U>,
    val description: String? = null,
    val tooltipHtml: String? = null,
) : DynamicEnum<TaskType<*, *>>(name) {

    companion object {
        private val taskConstructors =
            mutableMapOf<TaskType<*, *>, (OrchestrationConfig, TaskConfigBase?) -> AbstractTask<out TaskConfigBase>>()

//        val SoftwareGraphPlanningTask = TaskType(
//            "SoftwareGraphPlanningTask",
//            com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphPlanningTask.GraphBasedPlanningTaskConfigData::class.java,
//            TaskSettingsBase::class.java,
//            "Generate and execute task plans based on software graph structure",
//            """
//      Creates task plans using software graph context.
//      <ul>
//        <li>Analyzes software graph structure</li>
//        <li>Generates dependency-aware task plans</li>
//        <li>Considers node relationships</li>
//        <li>Supports immediate execution</li>
//        <li>Provides planning rationale</li>
//      </ul>
//      """
//        )
//        val DataTableCompilationTask = TaskType(
//            "DataTableCompilationTask",
//            com.simiacryptus.cognotik.plan.tools.graph.DataTableCompilationTask.DataTableCompilationTaskConfigData::class.java,
//            TaskSettingsBase::class.java,
//            "Compile structured data tables from multiple files",
//            """
//          Extracts and compiles structured data from multiple files into a unified table.
//          <ul>
//            <li>Identifies rows and columns based on custom instructions</li>
//            <li>Extracts cell data according to specified criteria</li>
//            <li>Supports multiple file formats via glob patterns</li>
//            <li>Generates both JSON and markdown table outputs</li>
//            <li>Provides detailed extraction statistics</li>
//            <li>Handles large datasets with progress tracking</li>
//          </ul>
//          """
//        )
//        val SoftwareGraphModificationTask = TaskType(
//            "SoftwareGraphModificationTask",
//            com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphModificationTask.SoftwareGraphModificationTaskConfigData::class.java,
//            TaskSettingsBase::class.java,
//            "Modify an existing software graph representation",
//            """
//           Loads, modifies and saves software graph representations.
//           <ul>
//             <li>Loads existing graph from JSON file</li>
//             <li>Generates targeted modifications</li>
//             <li>Preserves existing relationships</li>
//             <li>Validates node references</li>
//             <li>Saves modified graph</li>
//           </ul>
//           """
//        )
//        val SoftwareGraphGenerationTask = TaskType(
//            "SoftwareGraphGenerationTask",
//            com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphGenerationTask.SoftwareGraphGenerationTaskConfigData::class.java,
//            TaskSettingsBase::class.java,
//            "Generate a SoftwareGraph representation of the codebase",
//            """
//                  Generates a comprehensive SoftwareGraph representation of the codebase.
//                  <ul>
//                    <li>Analyzes code structure and relationships</li>
//                    <li>Maps dependencies between components</li>
//                    <li>Captures project organization</li>
//                    <li>Identifies test relationships</li>
//                    <li>Tracks external dependencies</li>
//                    <li>Saves graph in JSON format</li>
//                  </ul>
//                """
//        )
        val VectorSearchTask = TaskType(
            "VectorSearchTask",
            com.simiacryptus.cognotik.plan.tools.knowledge.VectorSearchTask.VectorSearchTaskConfigData::class.java,
            TaskSettingsBase::class.java,
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
            RunShellCommandTaskConfigData::class.java,
            TaskSettingsBase::class.java,
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
            RunCodeTaskConfigData::class.java,
            RunCodeTaskSettings::class.java,
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
            com.simiacryptus.cognotik.plan.tools.SelfHealingTask.SelfHealingTaskConfigData::class.java,
            com.simiacryptus.cognotik.plan.tools.SelfHealingTask.SelfHealingTaskSettings::class.java,
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
            com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask.GitHubSearchTaskConfigData::class.java,
            TaskSettingsBase::class.java,
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
                com.simiacryptus.cognotik.plan.tools.knowledge.KnowledgeIndexingTask.KnowledgeIndexingTaskConfigData::class.java,
                TaskSettingsBase::class.java,
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
            com.simiacryptus.cognotik.plan.tools.session.SeleniumSessionTask.SeleniumSessionTaskConfigData::class.java,
            TaskSettingsBase::class.java,
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
            com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask.CommandSessionTaskConfigData::class.java,
            TaskSettingsBase::class.java,
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
            com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskConfigData::class.java,
            com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskSettings::class.java,
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
//            registerConstructor(SoftwareGraphPlanningTask) { settings, task ->
//                com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphPlanningTask(
//                    settings,
//                    task
//                )
//            }
//            registerConstructor(SoftwareGraphModificationTask) { settings, task ->
//                com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphModificationTask(
//                    settings,
//                    task
//                )
//            }
//            registerConstructor(SoftwareGraphGenerationTask) { settings, task ->
//                com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphGenerationTask(
//                    settings,
//                    task
//                )
//            }
//            registerConstructor(DataTableCompilationTask) { settings, task ->
//                com.simiacryptus.cognotik.plan.tools.graph.DataTableCompilationTask(
//                    settings,
//                    task
//                )
//            }
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

        fun <T : TaskConfigBase, U : TaskSettingsBase> registerConstructor(
            taskType: TaskType<T, U>, constructor: (OrchestrationConfig, T?) -> AbstractTask<T>
        ) {
            taskConstructors[taskType] = { settings: OrchestrationConfig, task: TaskConfigBase? ->
                constructor(settings, task as T?)
            }
            register(taskType)
        }

        fun values() = values(TaskType::class.java)
        fun getImpl(
            orchestrationConfig: OrchestrationConfig, planTask: TaskConfigBase?, strict: Boolean = true
        ) = getImpl(
            orchestrationConfig = orchestrationConfig,
            taskType = planTask?.task_type?.let { valueOf(it) } ?: throw RuntimeException("Task type not specified"),
            planTask = planTask,
            strict = strict)

        fun getImpl(
            orchestrationConfig: OrchestrationConfig,
            taskType: TaskType<*, *>,
            planTask: TaskConfigBase? = null,
            strict: Boolean = true
        ): AbstractTask<out TaskConfigBase> {
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