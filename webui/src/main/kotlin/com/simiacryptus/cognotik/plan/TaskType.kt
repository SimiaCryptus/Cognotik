package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.tools.RunCodeTask
import com.simiacryptus.cognotik.plan.tools.RunCodeTask.RunCodeTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.RunCodeTask.RunCodeTaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.SelfHealingTask
import com.simiacryptus.cognotik.plan.tools.file.*
import com.simiacryptus.cognotik.plan.tools.file.AnalysisTask.Companion.Analysis
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.Companion.FileSearch
import com.simiacryptus.cognotik.plan.tools.file.GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.file.WriteHtmlTask.WriteHtmlTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.knowledge.KnowledgeIndexingTask
import com.simiacryptus.cognotik.plan.tools.knowledge.KnowledgeIndexingTask.Companion.KnowledgeIndexing
import com.simiacryptus.cognotik.plan.tools.knowledge.VectorSearchTask
import com.simiacryptus.cognotik.plan.tools.knowledge.VectorSearchTask.Companion.VectorSearch
import com.simiacryptus.cognotik.plan.tools.mcp.MCPToolTask
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask
import com.simiacryptus.cognotik.plan.tools.reasoning.*
import com.simiacryptus.cognotik.plan.tools.reasoning.CausalInferenceTask.CausalInferenceTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.reasoning.ChainOfThoughtTask.Companion.ChainOfThought
import com.simiacryptus.cognotik.plan.tools.reasoning.ConstraintSatisfactionTask.ConstraintSatisfactionTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.reasoning.CounterfactualAnalysisTask.CounterfactualAnalysisTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.reasoning.MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysisTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.reasoning.SocraticDialogueTask.Companion.SocraticDialogue
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
    val WriteHtml = TaskType(
      "WriteHtml",
      WriteHtmlTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Create complete HTML files with embedded CSS and JavaScript",
      """
              Creates standalone HTML files with embedded CSS and JavaScript.
              <ul>
                <li>Generates complete, self-contained HTML documents</li>
                <li>Embeds CSS styles within &lt;style&gt; tags</li>
                <li>Embeds JavaScript within &lt;script&gt; tags</li>
                <li>Supports modern HTML5 features</li>
                <li>Interactive approval or auto-apply mode</li>
                <li>Proper HTML structure and formatting</li>
              </ul>
            """
    )
    val GeneratePresentation = TaskType(
      "GeneratePresentation",
      GeneratePresentationTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Create complete Reveal.js presentations with narration support",
      """
              Creates professional Reveal.js presentations with speaker notes.
              <ul>
                <li>Generates complete, self-contained HTML presentations</li>
                <li>Includes Reveal.js framework integration</li>
                <li>Adds speaker notes for each slide</li>
                <li>Supports custom styling and themes</li>
                <li>Interactive approval or auto-apply mode</li>
                <li>Includes navigation and progress indicators</li>
                <li>Optional audio narration support</li>
              </ul>
            """
    )
    val MetaCognitiveReflection = TaskType(
      "MetaCognitiveReflection",
      MetaCognitiveReflectionTask.MetaCognitiveReflectionTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Reflect on and critique reasoning processes",
      """
              Performs meta-cognitive reflection on task reasoning and solutions.
              <ul>
                <li>Analyzes assumptions and identifies biases</li>
                <li>Evaluates alternative approaches</li>
                <li>Assesses confidence and certainty levels</li>
                <li>Identifies knowledge gaps and uncertainties</li>
                <li>Suggests improvements to reasoning quality</li>
                <li>Checks logical consistency and completeness</li>
              </ul>
            """
    )
    val MultiPerspectiveAnalysis = TaskType(
      "MultiPerspectiveAnalysis",
      MultiPerspectiveAnalysisTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Analyze problems from multiple viewpoints with synthesis",
      """
              Analyzes topics from multiple perspectives and synthesizes findings.
              <ul>
                <li>Examines subject from specified viewpoints</li>
                <li>Generates detailed analysis for each perspective</li>
                <li>Identifies agreements and conflicts</li>
                <li>Synthesizes perspectives into unified conclusion</li>
                <li>Configurable consensus threshold</li>
                <li>Useful for architectural decisions and code reviews</li>
                <li>Supports context from related files</li>
              </ul>
            """
    )
    val AnalogicalReasoning = TaskType(
      "AnalogicalReasoning",
      AnalogicalReasoningTask.AnalogicalReasoningTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Solve problems by finding and applying analogies from different domains",
      """
              Performs creative problem-solving through analogical reasoning.
              <ul>
                <li>Draws analogies from specified source domains</li>
                <li>Maps structural relationships to target problems</li>
                <li>Generates multiple perspectives and insights</li>
                <li>Validates mapping coherence and consistency</li>
                <li>Synthesizes findings across analogies</li>
                <li>Suggests concrete solutions based on analogies</li>
                <li>Useful for design thinking and novel approaches</li>
              </ul>
            """
    )
    val CounterfactualAnalysis = TaskType(
      "CounterfactualAnalysis",
      CounterfactualAnalysisTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Explore what-if scenarios to understand causal relationships and decision impacts",
      """
              Performs counterfactual analysis to explore alternative scenarios and outcomes.
              <ul>
                <li>Analyzes actual scenarios and alternative conditions</li>
                <li>Compares outcomes across different scenarios</li>
                <li>Identifies causal relationships and key factors</li>
                <li>Supports controlled comparison with constant factors</li>
                <li>Provides insights for risk analysis and decision validation</li>
                <li>Useful for retrospective analysis and strategic planning</li>
              </ul>
            """
    )
    val AbstractionLadder = TaskType(
      "AbstractionLadder",
      AbstractionLadderTask.AbstractionLadderTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Traverse abstraction levels to identify patterns and design insights",
      """
              Analyzes concepts by moving up and down abstraction levels.
              <ul>
                <li>Move up to find generalizations and patterns</li>
                <li>Move down to find specific implementations</li>
                <li>Identify design patterns at each level</li>
                <li>Discover refactoring opportunities</li>
                <li>Analyze architectural patterns</li>
                <li>Find code smells and anti-patterns</li>
                <li>Generate actionable recommendations</li>
              </ul>
            """
    )
    val ConstraintSatisfaction = TaskType(
      "ConstraintSatisfaction",
      ConstraintSatisfactionTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Solve problems with multiple competing constraints",
      """
              Solves constraint satisfaction problems with hard and soft constraints.
              <ul>
                <li>Handles hard constraints that must be satisfied</li>
                <li>Optimizes soft constraints with configurable weights</li>
                <li>Supports multiple search strategies (backtracking, forward, local)</li>
                <li>Provides detailed reasoning and trade-off analysis</li>
                <li>Suggests alternative solutions when applicable</li>
                <li>Useful for architectural decisions, resource allocation, and optimization</li>
              </ul>
            """
    )
    val CausalInference = TaskType(
      "CausalInference",
      CausalInferenceTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Identify causal relationships and root causes",
      """
              Performs causal inference analysis to identify true causal relationships.
              <ul>
                <li>Distinguishes causation from correlation</li>
                <li>Identifies root causes vs intermediate factors</li>
                <li>Builds causal graphs showing relationships</li>
                <li>Identifies confounding variables</li>
                <li>Provides evidence-based causal reasoning</li>
                <li>Useful for debugging and root cause analysis</li>
              </ul>
            """
    )
    val DecompositionSynthesis = TaskType(
      "DecompositionSynthesis",
      DecompositionSynthesisTask.DecompositionSynthesisTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Decompose complex problems and synthesize solutions",
      """
              Decomposes complex problems into manageable subproblems, solves them, and synthesizes solutions.
              <ul>
                <li>Multiple decomposition strategies (functional, temporal, spatial, hierarchical)</li>
                <li>Configurable decomposition depth</li>
                <li>Dependency-aware subproblem solving</li>
                <li>Solution synthesis with coherence validation</li>
                <li>Confidence tracking at each level</li>
                <li>Implements divide-and-conquer reasoning</li>
              </ul>
            """
    )


    init {
      registerConstructor(ChainOfThought) { settings, task ->
        ChainOfThoughtTask(
          settings,
          task
        )
      }
      registerConstructor(Analysis) { settings, task ->
        AnalysisTask(
          settings,
          task
        )
      }
//            registerConstructor(CommandSession) { settings, task ->
//                CommandSessionTask(
//                    settings,
//                    task
//                )
//            }
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
      registerConstructor(WriteHtml) { settings, task ->
        WriteHtmlTask(
          settings,
          task
        )
      }
      registerConstructor(GeneratePresentation) { settings, task ->
        GeneratePresentationTask(
          settings,
          task
        )
      }
      registerConstructor(MetaCognitiveReflection) { settings, task ->
        MetaCognitiveReflectionTask(
          settings,
          task
        )
      }
      registerConstructor(CausalInference) { settings, task ->
        CausalInferenceTask(
          settings,
          task
        )
      }
      registerConstructor(AbstractionLadder) { settings, task ->
        AbstractionLadderTask(
          settings,
          task
        )
      }
      registerConstructor(CounterfactualAnalysis) { settings, task ->
        CounterfactualAnalysisTask(
          settings,
          task
        )
      }
      registerConstructor(AnalogicalReasoning) { settings, task ->
        AnalogicalReasoningTask(
          settings,
          task
        )
      }
      registerConstructor(SocraticDialogue) { settings, task ->
        SocraticDialogueTask(
          settings,
          task
        )
      }
      registerConstructor(MultiPerspectiveAnalysis) { settings, task ->
        MultiPerspectiveAnalysisTask(
          settings,
          task
        )
      }
      registerConstructor(ConstraintSatisfaction) { settings, task ->
        ConstraintSatisfactionTask(
          settings,
          task
        )
      }
      registerConstructor(DecompositionSynthesis) { settings, task ->
        DecompositionSynthesisTask(
          settings,
          task
        )
      }
      registerConstructor(BrainstormingTask.Brainstorming) { settings, task ->
        BrainstormingTask(
          settings,
          task
        )
      }
      registerConstructor(FiniteStateMachineTask.FiniteStateMachine) { settings, task ->
        FiniteStateMachineTask(
          settings,
          task
        )
      }
      registerConstructor(GameTheoryTask.GameTheory) { settings, task ->
        GameTheoryTask(
          settings,
          task
        )
      }
    }

    fun <T : TaskExecutionConfig, U : TaskTypeConfig> registerConstructor(
      taskType: TaskType<T, U>, constructor: (OrchestrationConfig, T?) -> AbstractTask<T, U>
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