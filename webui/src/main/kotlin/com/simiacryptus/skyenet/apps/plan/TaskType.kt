package com.simiacryptus.skyenet.apps.plan

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.skyenet.apps.plan.tools.CommandAutoFixTask
import com.simiacryptus.skyenet.apps.plan.tools.CommandAutoFixTask.CommandAutoFixTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.CommandSessionTask
import com.simiacryptus.skyenet.apps.plan.tools.RunShellCommandTask
import com.simiacryptus.skyenet.apps.plan.tools.RunShellCommandTask.RunShellCommandTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.SeleniumSessionTask
import com.simiacryptus.skyenet.apps.plan.tools.file.*
import com.simiacryptus.skyenet.apps.plan.tools.file.CodeOptimizationTask.CodeOptimizationTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.file.CodeReviewTask.CodeReviewTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.file.DocumentationTask.DocumentationTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.file.FileModificationTask.FileModificationTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.file.InquiryTask.InquiryTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.file.PerformanceAnalysisTask.PerformanceAnalysisTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.file.RefactorTask.RefactorTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.file.SecurityAuditTask.SecurityAuditTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.file.TestGenerationTask.TestGenerationTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.knowledge.EmbeddingSearchTask
import com.simiacryptus.skyenet.apps.plan.tools.knowledge.KnowledgeIndexingTask
import com.simiacryptus.skyenet.apps.plan.tools.knowledge.WebSearchAndIndexTask
import com.simiacryptus.skyenet.apps.plan.tools.online.GitHubSearchTask
import com.simiacryptus.skyenet.apps.plan.tools.online.SearchAndAnalyzeTask
import com.simiacryptus.skyenet.apps.plan.tools.online.SimpleGoogleSearchTask
import com.simiacryptus.skyenet.apps.plan.tools.online.SimpleGoogleSearchTask.GoogleSearchTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.online.WebFetchAndTransformTask
import com.simiacryptus.skyenet.apps.plan.tools.plan.ForeachTask
import com.simiacryptus.skyenet.apps.plan.tools.plan.ForeachTask.ForeachTaskConfigData
import com.simiacryptus.skyenet.apps.plan.tools.plan.PlanningTask
import com.simiacryptus.skyenet.apps.plan.tools.plan.PlanningTask.PlanningTaskConfigData
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

    private val taskConstructors =
      mutableMapOf<TaskType<*, *>, (PlanSettings, TaskConfigBase?) -> AbstractTask<out TaskConfigBase>>()

    val TaskPlanning = TaskType(
      name = "TaskPlanning",
      taskDataClass = PlanningTaskConfigData::class.java,
      taskSettingsClass = TaskSettingsBase::class.java,
      description = "Break down and coordinate complex development tasks with dependency management",
      tooltipHtml = """
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
    val Inquiry = TaskType(
      name = "Inquiry",
      taskDataClass = InquiryTaskConfigData::class.java,
      taskSettingsClass = TaskSettingsBase::class.java,
      description = "Analyze code and provide detailed explanations of implementation patterns",
      tooltipHtml = """
                Provides detailed answers and insights about code implementation by analyzing specified files.
                <ul>
                  <li>Answers detailed questions about code functionality and implementation</li>
                  <li>Analyzes code patterns, relationships and architectural decisions</li>
                  <li>Supports interactive discussions and follow-up questions in blocking mode</li>
                  <li>Generates comprehensive markdown reports with code examples</li>
                  <li>Handles multiple files and complex cross-reference queries</li>
                  <li>Provides context-aware technical recommendations</li>
                  <li>Explains trade-offs and rationale behind implementation choices</li>
                </ul>
              """
    )
    val Search = TaskType(
      name = "Search",
      taskDataClass = FileSearchTask.SearchTaskConfigData::class.java,
      taskSettingsClass = TaskSettingsBase::class.java,
      description = "Search project files using patterns with contextual results",
      tooltipHtml = """
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
    val EmbeddingSearch = TaskType(
      name = "EmbeddingSearch",
      taskDataClass = EmbeddingSearchTask.EmbeddingSearchTaskConfigData::class.java,
      taskSettingsClass = TaskSettingsBase::class.java,
      description = "Perform semantic search using AI embeddings",
      tooltipHtml = """
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
    val FileModification = TaskType(
      name = "FileModification",
      taskDataClass = FileModificationTaskConfigData::class.java,
      taskSettingsClass = TaskSettingsBase::class.java,
      description = "Create new files or modify existing code with AI-powered assistance",
      tooltipHtml = """
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
    val Documentation = TaskType(
      name = "Documentation",
      taskDataClass = DocumentationTaskConfigData::class.java,
      taskSettingsClass = TaskSettingsBase::class.java,
      description = "Generate comprehensive documentation for code, APIs, and architecture",
      tooltipHtml = """
                Generates comprehensive documentation for code files and APIs.
                <ul>
                  <li>Handles both inline comments and markdown files</li>
                  <li>Generates detailed API documentation</li>
                  <li>Documents design decisions and rationale</li>
                  <li>Supports interactive approval workflow</li>
                  <li>Maintains documentation consistency</li>
                </ul>
              """
    )
    val CodeReview = TaskType(
      name = "CodeReview",
      taskDataClass = CodeReviewTaskConfigData::class.java,
      taskSettingsClass = TaskSettingsBase::class.java,
      description = "Perform thorough code review with quality and best practice analysis",
      tooltipHtml = """
                Performs automated code reviews focusing on quality and best practices.
                <ul>
                  <li>Analyzes code quality and potential issues</li>
                  <li>Identifies bugs and performance problems</li>
                  <li>Reviews security vulnerabilities</li>
                  <li>Suggests specific improvements</li>
                  <li>Provides actionable recommendations</li>
                </ul>
              """
    )
    val TestGeneration = TaskType(
      "TestGeneration",
      TestGenerationTaskConfigData::class.java,
      TaskSettingsBase::class.java,
      "Generate comprehensive test suites with full coverage analysis",
      """
          Creates comprehensive test suites for code reliability and correctness.
          <ul>
            <li>Generates unit and integration tests</li>
            <li>Creates positive and negative test cases</li>
            <li>Tests edge cases and boundary conditions</li>
            <li>Follows language-specific testing practices</li>
            <li>Organizes tests in appropriate directories</li>
          </ul>
        """
    )
    val Optimization = TaskType(
      "Optimization",
      CodeOptimizationTaskConfigData::class.java,
      TaskSettingsBase::class.java,
      "Analyze performance bottlenecks and implement optimizations",
      """
          Analyzes and optimizes code performance while maintaining readability.
          <ul>
            <li>Identifies performance bottlenecks</li>
            <li>Suggests algorithmic improvements</li>
            <li>Analyzes memory usage patterns</li>
            <li>Recommends caching strategies</li>
            <li>Provides impact estimates</li>
          </ul>
        """
    )
    val SecurityAudit = TaskType(
      "SecurityAudit",
      SecurityAuditTaskConfigData::class.java,
      TaskSettingsBase::class.java,
      "Identify security vulnerabilities and provide mitigation strategies",
      """
          Performs security analysis to identify and fix vulnerabilities.
          <ul>
            <li>Analyzes security vulnerabilities</li>
            <li>Reviews authentication/authorization</li>
            <li>Checks data handling practices</li>
            <li>Provides security recommendations</li>
            <li>Generates detailed audit reports</li>
          </ul>
        """
    )
    val PerformanceAnalysis = TaskType(
      "PerformanceAnalysis",
      PerformanceAnalysisTaskConfigData::class.java,
      TaskSettingsBase::class.java,
      "Analyze and optimize code performance with detailed metrics",
      """
          Analyzes code performance and provides optimization recommendations.
          <ul>
            <li>Identifies performance bottlenecks and hotspots</li>
            <li>Measures execution time and resource usage</li>
            <li>Suggests algorithmic and structural improvements</li>
            <li>Provides quantitative performance metrics</li>
            <li>Recommends caching and optimization strategies</li>
          </ul>
        """
    )
    val RefactorTask = TaskType(
      "RefactorTask",
      RefactorTaskConfigData::class.java,
      TaskSettingsBase::class.java,
      "Improve code structure, readability and maintainability",
      """
          Analyzes and improves code structure while maintaining functionality.
          <ul>
            <li>Suggests structural improvements</li>
            <li>Reduces code complexity</li>
            <li>Improves naming conventions</li>
            <li>Enhances code organization</li>
            <li>Shows changes in diff format</li>
          </ul>
        """
    )
    val RunShellCommand = TaskType(
      "RunShellCommand",
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
    val CommandAutoFix = TaskType(
      "CommandAutoFix",
      CommandAutoFixTaskConfigData::class.java,
      CommandAutoFixTask.CommandAutoFixTaskSettings::class.java
    )
    val ForeachTask = TaskType("ForeachTask", ForeachTaskConfigData::class.java, TaskSettingsBase::class.java,
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
    val GitHubSearch = TaskType(
      "GitHubSearch",
      GitHubSearchTask.GitHubSearchTaskConfigData::class.java,
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
    val GoogleSearch = TaskType(
      "GoogleSearch",
      GoogleSearchTaskConfigData::class.java,
      TaskSettingsBase::class.java,
      "Perform Google web searches with custom filtering",
      """
          Executes Google web searches with customizable parameters.
          <ul>
            <li>Uses Google Custom Search API</li>
            <li>Supports result count configuration</li>
            <li>Includes metadata and snippets</li>
            <li>Formats results in markdown</li>
            <li>Handles URL encoding and safety</li>
          </ul>
        """
    )
    val WebFetchAndTransform =
      TaskType(
        "WebFetchAndTransform",
        WebFetchAndTransformTask.WebFetchAndTransformTaskConfigData::class.java,
        TaskSettingsBase::class.java,
        "Fetch and transform web content into desired formats",
        """
          Fetches content from web URLs and transforms it into desired formats.
          <ul>
            <li>Downloads and cleans HTML content</li>
            <li>Converts content to specified formats</li>
            <li>Handles content size limitations</li>
            <li>Supports custom transformation goals</li>
            <li>Integrates with markdown rendering</li>
          </ul>
        """
      )
    val KnowledgeIndexing = TaskType(
      "KnowledgeIndexing",
      KnowledgeIndexingTask.KnowledgeIndexingTaskConfigData::class.java,
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
    val WebSearchAndIndex = TaskType(
      "WebSearchAndIndex",
      WebSearchAndIndexTask.WebSearchAndIndexTaskConfigData::class.java,
      TaskSettingsBase::class.java,
      "Search web content and create searchable indexes",
      """
          Performs web searches and indexes results for future reference.
          <ul>
            <li>Integrates with Google Custom Search</li>
            <li>Downloads and processes search results</li>
            <li>Creates searchable indexes</li>
            <li>Handles content download and storage</li>
            <li>Supports batch processing</li>
          </ul>
        """
    )
    val SeleniumSession = TaskType(
      "SeleniumSession",
      SeleniumSessionTask.SeleniumSessionTaskConfigData::class.java,
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
    val CommandSession = TaskType(
      "CommandSession",
      CommandSessionTask.CommandSessionTaskConfigData::class.java,
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
    val SearchAndAnalyze = TaskType(
      "SearchAndAnalyze",
      SearchAndAnalyzeTask.SearchAndAnalyzeTaskConfigData::class.java,
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
      registerConstructor(CommandAutoFix) { settings, task -> CommandAutoFixTask(settings, task) }
      registerConstructor(Inquiry) { settings, task -> InquiryTask(settings, task) }
      registerConstructor(Search) { settings, task -> FileSearchTask(settings, task) }
      registerConstructor(SearchAndAnalyze) { settings, task -> SearchAndAnalyzeTask(settings, task) }
      registerConstructor(EmbeddingSearch) { settings, task -> EmbeddingSearchTask(settings, task) }
      registerConstructor(FileModification) { settings, task -> FileModificationTask(settings, task) }
      registerConstructor(Documentation) { settings, task -> DocumentationTask(settings, task) }
      registerConstructor(RunShellCommand) { settings, task -> RunShellCommandTask(settings, task) }
      registerConstructor(CodeReview) { settings, task -> CodeReviewTask(settings, task) }
      registerConstructor(TestGeneration) { settings, task -> TestGenerationTask(settings, task) }
      registerConstructor(Optimization) { settings, task -> CodeOptimizationTask(settings, task) }
      registerConstructor(SecurityAudit) { settings, task -> SecurityAuditTask(settings, task) }
      registerConstructor(PerformanceAnalysis) { settings, task -> PerformanceAnalysisTask(settings, task) }
      registerConstructor(RefactorTask) { settings, task -> RefactorTask(settings, task) }
      registerConstructor(ForeachTask) { settings, task -> ForeachTask(settings, task) }
      registerConstructor(TaskPlanning) { settings, task -> PlanningTask(settings, task) }
      registerConstructor(GitHubSearch) { settings, task -> GitHubSearchTask(settings, task) }
      registerConstructor(GoogleSearch) { settings, task -> SimpleGoogleSearchTask(settings, task) }
      registerConstructor(WebFetchAndTransform) { settings, task -> WebFetchAndTransformTask(settings, task) }
      registerConstructor(KnowledgeIndexing) { settings, task -> KnowledgeIndexingTask(settings, task) }
      registerConstructor(WebSearchAndIndex) { settings, task -> WebSearchAndIndexTask(settings, task) }
      registerConstructor(SeleniumSession) { settings, task -> SeleniumSessionTask(settings, task) }
      registerConstructor(CommandSession) { settings, task -> CommandSessionTask(settings, task) }
    }

    private fun <T : TaskConfigBase, U : TaskSettingsBase> registerConstructor(
      taskType: TaskType<T, U>,
      constructor: (PlanSettings, T?) -> AbstractTask<T>
    ) {
      taskConstructors[taskType] = { settings: PlanSettings, task: TaskConfigBase? ->
        constructor(settings, task as T?)
      }
      register(taskType)
    }

    fun values() = values(TaskType::class.java)
    fun getImpl(
      planSettings: PlanSettings,
      planTask: TaskConfigBase?
    ) = getImpl(planSettings, planTask?.task_type?.let { valueOf(it) } ?: throw RuntimeException("Task type not specified"), planTask)

    fun getImpl(
      planSettings: PlanSettings,
      taskType: TaskType<*, *>,
      planTask: TaskConfigBase? = null
    ): AbstractTask<out TaskConfigBase> {
      if (!planSettings.getTaskSettings(taskType).enabled) {
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