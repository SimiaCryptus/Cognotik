package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.tools.CommandAutoFixTask.CommandAutoFixTaskConfigData
import com.simiacryptus.cognotik.plan.tools.RunShellCommandTask.RunShellCommandTaskConfigData
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskConfigData
import com.simiacryptus.cognotik.plan.tools.file.InsightTask.InsightTaskConfigData
import com.simiacryptus.cognotik.plan.tools.plan.ForeachTask.ForeachTaskConfigData
import com.simiacryptus.cognotik.plan.tools.plan.PlanningTask.PlanningTaskConfigData
import com.simiacryptus.util.DynamicEnum
import com.simiacryptus.util.DynamicEnumDeserializer
import com.simiacryptus.util.DynamicEnumSerializer

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
        val SoftwareGraphPlanningTask = TaskType(
            "SoftwareGraphPlanningTask",
            com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphPlanningTask.GraphBasedPlanningTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Generate and execute task plans based on software graph structure",
            """
      Creates task plans using software graph context.
      <ul>
        <li>Analyzes software graph structure</li>
        <li>Generates dependency-aware task plans</li>
        <li>Considers node relationships</li>
        <li>Supports immediate execution</li>
        <li>Provides planning rationale</li>
      </ul>
      """
        )
        val DataTableCompilationTask = TaskType(
            "DataTableCompilationTask",
            com.simiacryptus.cognotik.plan.tools.knowledge.DataTableCompilationTask.DataTableCompilationTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Compile structured data tables from multiple files",
            """
          Extracts and compiles structured data from multiple files into a unified table.
          <ul>
            <li>Identifies rows and columns based on custom instructions</li>
            <li>Extracts cell data according to specified criteria</li>
            <li>Supports multiple file formats via glob patterns</li>
            <li>Generates both JSON and markdown table outputs</li>
            <li>Provides detailed extraction statistics</li>
            <li>Handles large datasets with progress tracking</li>
          </ul>
          """
        )
        val SoftwareGraphModificationTask = TaskType(
            "SoftwareGraphModificationTask",
            com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphModificationTask.SoftwareGraphModificationTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Modify an existing software graph representation",
            """
           Loads, modifies and saves software graph representations.
           <ul>
             <li>Loads existing graph from JSON file</li>
             <li>Generates targeted modifications</li>
             <li>Preserves existing relationships</li>
             <li>Validates node references</li>
             <li>Saves modified graph</li>
           </ul>
           """
        )
        val SoftwareGraphGenerationTask = TaskType(
            "SoftwareGraphGenerationTask",
            com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphGenerationTask.SoftwareGraphGenerationTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Generate a SoftwareGraph representation of the codebase",
            """
                  Generates a comprehensive SoftwareGraph representation of the codebase.
                  <ul>
                    <li>Analyzes code structure and relationships</li>
                    <li>Maps dependencies between components</li>
                    <li>Captures project organization</li>
                    <li>Identifies test relationships</li>
                    <li>Tracks external dependencies</li>
                    <li>Saves graph in JSON format</li>
                  </ul>
                """
        )

        private val taskConstructors =
            mutableMapOf<TaskType<*, *>, (PlanSettings, TaskConfigBase?) -> AbstractTask<out TaskConfigBase>>()

        val TaskPlanningTask = TaskType(
            "TaskPlanningTask",
            PlanningTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Break down and coordinate complex development tasks with dependency management",
            """
                      Orchestrates complex development tasks by breaking them down into manageable subtasks.
                      <ul>
                        <li>Analyzes project requirements and constraints to create optimal task sequences</li>
                        <li>Establishes clear task dependencies and relationships between components</li>
                        <li>Optimizes task ordering for maximum parallel execution efficiency</li>
                        <li>Provides interactive visual dependency graphs for progress tracking</li>
                        <li>Supports both fully automated and interactive planning modes</li>
                        <li>Estimates task complexity and resource requirements</li>
                        <li>Identifies critical paths and potential bottlenecks</li>
                      </ul>
                    """
        )
        val InsightTask = TaskType(
            "InsightTask",
            InsightTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Directly answer questions or provide insights using the LLM, optionally referencing files, with optional user feedback and iteration.",
            """
            Provides direct answers and insights using the LLM, optionally referencing project files.
            <ul>
              <li>Primarily processes and responds to user inquiries using the language model, without producing side effects or modifying files</li>
              <li>Reading files is optional; the task can operate with or without file input</li>
              <li>User feedback and iterative refinement are supported but not required</li>
              <li>Generates comprehensive markdown reports, explanations, and recommendations</li>
              <li>Can answer detailed questions about code, design, or project context</li>
              <li>Supports both one-shot and interactive discussion modes</li>
              <li>Ideal for technical Q&A, code reviews, and architectural analysis without making changes</li>
            </ul>
            """
        )
        val FileSearchTask = TaskType(
            "FileSearchTask",
            com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.SearchTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Search project files using patterns with contextual results",
            """
                      Performs pattern-based searches across project files with context.
                      <ul>
                        <li>Supports both substring and regex search patterns</li>
                        <li>Shows configurable context lines around matches</li>
                        <li>Groups results by file with line numbers</li>
                        <li>Filters for text-based files automatically</li>
                        <li>Provides organized, readable output format</li>
                      </ul>
                    """
        )
        val EmbeddingSearchTask = TaskType(
            "EmbeddingSearchTask",
            com.simiacryptus.cognotik.plan.tools.knowledge.EmbeddingSearchTask.EmbeddingSearchTaskConfigData::class.java,
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
        val FileModificationTask = TaskType(
            "FileModificationTask",
            FileModificationTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Create new files or modify existing code with AI-powered assistance",
            """
                      Creates or modifies source files with AI assistance while maintaining code quality.
                      <ul>
                        <li>Shows proposed changes in diff format for easy review</li>
                        <li>Supports both automated application and manual approval modes</li>
                        <li>Maintains project coding standards and style consistency</li>
                        <li>Handles complex multi-file operations and refactoring</li>
                        <li>Provides clear documentation of all changes with rationale</li>
                        <li>Implements proper error handling and edge cases</li>
                        <li>Updates imports and dependencies automatically</li>
                        <li>Preserves existing code formatting and structure</li>
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
        val CommandAutoFixTask = TaskType(
            "CommandAutoFixTask",
            CommandAutoFixTaskConfigData::class.java,
            com.simiacryptus.cognotik.plan.tools.CommandAutoFixTask.CommandAutoFixTaskSettings::class.java,
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

        val ForeachTask = TaskType(
            "ForeachTask",
            ForeachTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Execute subtasks for each item in a list",
            """
          Executes a set of subtasks for each item in a given list.
          <ul>
            <li>Handles sequential item processing</li>
            <li>Maintains subtask dependencies</li>
            <li>Supports parallel execution within items</li>
            <li>Provides progress tracking</li>
            <li>Configurable subtask definitions</li>
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
        val KnowledgeIndexingTask = TaskType(
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
            com.simiacryptus.cognotik.plan.tools.SeleniumSessionTask.SeleniumSessionTaskConfigData::class.java,
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
            com.simiacryptus.cognotik.plan.tools.CommandSessionTask.CommandSessionTaskConfigData::class.java,
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
        val WebSearchTask = TaskType(
            "CrawlerAgentTask",
            com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.SearchAndAnalyzeTaskConfigData::class.java,
            TaskSettingsBase::class.java,
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
            registerConstructor(SoftwareGraphPlanningTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphPlanningTask(
                    settings,
                    task
                )
            }
            registerConstructor(SoftwareGraphModificationTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphModificationTask(
                    settings,
                    task
                )
            }
            registerConstructor(SoftwareGraphGenerationTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.graph.SoftwareGraphGenerationTask(
                    settings,
                    task
                )
            }
            registerConstructor(DataTableCompilationTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.knowledge.DataTableCompilationTask(
                    settings,
                    task
                )
            }
            registerConstructor(CommandAutoFixTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.CommandAutoFixTask(
                    settings,
                    task
                )
            }
            registerConstructor(InsightTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.file.InsightTask(
                    settings,
                    task
                )
            }
            registerConstructor(FileSearchTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.file.FileSearchTask(
                    settings,
                    task
                )
            }
            registerConstructor(WebSearchTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask(
                    settings,
                    task
                )
            }
            registerConstructor(EmbeddingSearchTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.knowledge.EmbeddingSearchTask(
                    settings,
                    task
                )
            }
            registerConstructor(FileModificationTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.file.FileModificationTask(
                    settings,
                    task
                )
            }
            registerConstructor(RunShellCommandTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.RunShellCommandTask(
                    settings,
                    task
                )
            }
            registerConstructor(ForeachTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.plan.ForeachTask(
                    settings,
                    task
                )
            }
            registerConstructor(TaskPlanningTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.plan.PlanningTask(
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
                com.simiacryptus.cognotik.plan.tools.SeleniumSessionTask(
                    settings,
                    task
                )
            }
            registerConstructor(CommandSessionTask) { settings, task ->
                com.simiacryptus.cognotik.plan.tools.CommandSessionTask(
                    settings,
                    task
                )
            }
        }

        fun <T : TaskConfigBase, U : TaskSettingsBase> registerConstructor(
            taskType: TaskType<T, U>, constructor: (PlanSettings, T?) -> AbstractTask<T>
        ) {
            taskConstructors[taskType] = { settings: PlanSettings, task: TaskConfigBase? ->
                constructor(settings, task as T?)
            }
            register(taskType)
        }

        fun values() = values(TaskType::class.java)
        fun getImpl(
            planSettings: PlanSettings, planTask: TaskConfigBase?, strict: Boolean = true
        ) = getImpl(
            planSettings = planSettings,
            taskType = planTask?.task_type?.let { valueOf(it) } ?: throw RuntimeException("Task type not specified"),
            planTask = planTask,
            strict = strict)

        fun getImpl(
            planSettings: PlanSettings,
            taskType: TaskType<*, *>,
            planTask: TaskConfigBase? = null,
            strict: Boolean = true
        ): AbstractTask<out TaskConfigBase> {
            if (strict && !planSettings.getTaskSettings(taskType).enabled) {
                throw DisabledTaskException(taskType)
            }
            val constructor =
                taskConstructors[taskType] ?: throw RuntimeException("Unknown task type: ${taskType.name}")
            return constructor(planSettings, planTask)
        }

        fun getAvailableTaskTypes(planSettings: PlanSettings) = values().filter {
            planSettings.getTaskSettings(it).enabled
        }

        fun valueOf(name: String): TaskType<*, *> = valueOf(TaskType::class.java, name)
        private fun register(taskType: TaskType<*, *>) = register(TaskType::class.java, taskType)
    }
}

class TaskTypeSerializer : DynamicEnumSerializer<TaskType<*, *>>(TaskType::class.java)
class TaskTypeDeserializer : DynamicEnumDeserializer<TaskType<*, *>>(TaskType::class.java)