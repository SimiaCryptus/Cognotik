package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.*
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SystemsThinkingTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: SystemsThinkingTaskExecutionConfigData?
) : AbstractTask<SystemsThinkingTask.SystemsThinkingTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  val maxDescriptionLength = 1500

  class SystemsThinkingTaskExecutionConfigData(
    @Description("Description of the system to analyze")
    var system_description: String? = null,
    @Description("Whether to identify feedback loops (reinforcing and balancing)")
    var identify_feedback_loops: Boolean = true,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    var related_files: List<String>? = null,
    @Description("Whether to map delays and accumulations in the system")
    var map_delays: Boolean = true,
    @Description("Whether to find leverage points for intervention")
    var find_leverage_points: Boolean = true,
    @Description("List of potential interventions to simulate (e.g., 'Implement caching layer', 'Add rate limiting'). Leave empty or null to skip intervention simulation.")
    var simulate_interventions: List<String>? = null,
    @Description("Time horizon for analysis (e.g., '6 months', '1 year')")
    var time_horizon: String? = "6 months",
    @Description("Whether to identify system archetypes")
    var identify_archetypes: Boolean = true,
    @Description("Whether to analyze emergent behavior")
    var analyze_emergent_behavior: Boolean = true,
    @Description("Focus areas or subsystems to prioritize in the analysis")
    var focus_areas: List<String>? = null,
    @Description("Specific questions to answer about the system")
    var analysis_questions: List<String>? = null,
    @Description("Description of the task")
    task_description: String? = null,
    @Description("List of task IDs this task depends on")
    task_dependencies: MutableList<String>? = null,
    @Description("The current state of the task")
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = SystemsThinking.name,
    task_description = task_description
      ?: "Analyze system dynamics for: ${system_description?.take(50)}${if ((system_description?.length ?: 0) > 50) "..." else ""}",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment() = """
SystemsThinking - Analyze complex systems through feedback loops and dynamics
* Specify the system to analyze (e.g., "CI/CD pipeline", "team workflow", "market dynamics")
* Identify feedback loops (reinforcing and balancing)
* Map delays and accumulations
* Find leverage points for intervention
* Simulate potential interventions (provide a list of specific interventions to simulate)
* Identify system archetypes (e.g., "Limits to Growth", "Shifting the Burden")
* Analyze emergent behavior and unintended consequences
* Optionally specify focus_areas to prioritize certain subsystems
* Optionally provide analysis_questions for specific insights
* Useful for:
  - Understanding system behavior
  - Performance optimization
  - Identifying unintended consequences
  - Organizational dynamics
  - Technical debt dynamics
  - Strategic planning and scenario analysis
""".trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val startTime = System.currentTimeMillis()
    log.info("Starting SystemsThinkingTask for system: '${executionConfig?.system_description?.take(100)}'")
    val transcript = task.newUserFileStream(transcriptFile())

    val systemDescription = executionConfig?.system_description
    if (systemDescription.isNullOrBlank()) {
      val errorMsg = "CONFIGURATION ERROR: No system description specified"
      log.error(errorMsg)
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }

    val api = defaultSmart ?: return

    val ui = task.ui
    val tabs = TabbedDisplay(task)

    // Overview tab
    val overviewTask = tabs.newTask("Overview")
    try {

      val executionConfig = this.executionConfig ?: throw IllegalStateException("Execution config is null")
      val timeHorizon = executionConfig.time_horizon ?: "6 months"
      val interventions = executionConfig.simulate_interventions ?: emptyList()
      val focusAreas = executionConfig.focus_areas ?: emptyList()
      val analysisQuestions = executionConfig.analysis_questions ?: emptyList()
      overviewTask.header("Systems Thinking Analysis", level = 1)

      overviewTask.add(buildString {
        this.appendLine("**System:** $systemDescription")
        this.appendLine()
        this.appendLine("**Time Horizon:** $timeHorizon")
        this.appendLine()
        if (focusAreas.isNotEmpty()) {
          this.appendLine("**Focus Areas:**")
          focusAreas.forEach { this.appendLine("- $it") }
          this.appendLine()
        }
        if (analysisQuestions.isNotEmpty()) {
          this.appendLine("**Analysis Questions:**")
          analysisQuestions.forEach { this.appendLine("- $it") }
          this.appendLine()
        }
        this.appendLine("**Analysis Components:**")
        if (executionConfig.identify_feedback_loops) this.appendLine("- ✅ Feedback Loops")
        if (executionConfig.map_delays) this.appendLine("- ✅ Delays & Accumulations")
        if (executionConfig.find_leverage_points) this.appendLine("- ✅ Leverage Points")
        if (executionConfig.identify_archetypes) this.appendLine("- ✅ System Archetypes")
        if (executionConfig.analyze_emergent_behavior) this.appendLine("- ✅ Emergent Behavior")
        if (interventions.isNotEmpty()) this.appendLine("- ✅ Intervention Simulation (${interventions.size} scenarios)")
        this.appendLine()
        this.appendLine(
          "**Started:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }"
        )
        this.appendLine()
        this.appendLine("---")
        this.appendLine()
        this.appendLine("**Status:** 🔄 Gathering context...")
      }.renderMarkdown())

      transcript?.write(
        "# Systems Thinking Analysis\n\n**System:** $systemDescription\n\n**Time Horizon:** $timeHorizon\n\n**Started:** ${
          LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }\n\n---\n\n".toByteArray()
      )

      // Gather context
      log.debug("Gathering context from prior tasks and related files")
      val priorContext = getPriorCode(agent.executionState)
      val inputFileContext = getInputFileCode()
      val relatedContext = gatherRelatedFiles()

      if (priorContext.isNotBlank() || inputFileContext.isNotBlank() || relatedContext.isNotBlank()) {

        val contextTask = tabs.newTask("Context")
        contextTask.header("Context", level = 1)

        if (priorContext.isNotBlank()) {
          contextTask.expandable("Prior Task Results", priorContext.truncateForDisplay().renderMarkdown())
        }
        if (inputFileContext.isNotBlank()) {
          contextTask.expandable("Input Files", inputFileContext.truncateForDisplay().renderMarkdown())
        }
        if (relatedContext.isNotBlank()) {

          contextTask.expandable("Related Files", relatedContext.truncateForDisplay().renderMarkdown())
        }
      }

      // Initialize analysis agent
      log.info("Initializing systems thinking analysis agent")
      val analysisAgent = ChatAgent(
        prompt = buildSystemsThinkingPrompt(
          systemDescription,
          timeHorizon,
          priorContext,
          relatedContext,
          focusAreas,
          analysisQuestions
        ),
        model = api,
        temperature = 0.6
      )

      overviewTask.add(buildString {
        appendLine()
        appendLine("✅ Context gathered")
        appendLine()
        appendLine("**Status:** 🔄 Analyzing system structure...")
      }.renderMarkdown())

      // Step 1: System Structure Analysis
      log.debug("Analyzing system structure and components")
      val structureTask = tabs.newTask("System Structure")

      structureTask.header("System Structure", level = 2)
      structureTask.add("🔄 Analyzing components and relationships...".renderMarkdown())

      val structureAnalysis = analysisAgent.answer(
        listOf(
          """
Analyze the system structure:
1. Identify key components and variables
2. Map relationships between components
3. Identify stocks (accumulations) and flows
4. Note information flows and decision points

Provide a clear, structured analysis.
          """.trimIndent()
        )
      )

      structureTask.add(buildString {
        appendLine("✅ Analysis complete")
        appendLine()
        appendLine(structureAnalysis)
      }.renderMarkdown())
      transcript?.write("## System Structure\n\n$structureAnalysis\n\n---\n\n".toByteArray())

      // Step 2: Feedback Loops
      if (executionConfig.identify_feedback_loops) {
        log.debug("Identifying feedback loops")
        val loopsTask = tabs.newTask("Feedback Loops")

        loopsTask.header("Feedback Loops", level = 2)
        loopsTask.add("🔄 Identifying reinforcing and balancing loops...".renderMarkdown())

        val loopsAnalysis = analysisAgent.answer(
          listOf(
            """
Identify feedback loops in the system:
1. **Reinforcing Loops (R)**: Loops that amplify change (virtuous or vicious cycles)
2. **Balancing Loops (B)**: Loops that resist change and seek equilibrium

For each loop:
- Name and describe the loop
- List the causal chain (A → B → C → A)
- Classify as reinforcing (R) or balancing (B)
- Explain the behavior it produces
- Rate its strength/impact (High/Medium/Low)

Then create a Mermaid diagram showing the loops using:
- Solid arrows (-->) for positive causality
- Dashed arrows (-.->) for negative causality
- Label loops as R1, R2, B1, B2, etc.

Provide the analysis and diagram.
          """.trimIndent()
          )
        )

        transcript?.write("## Feedback Loops\n\n$loopsAnalysis\n\n---\n\n".toByteArray())
        loopsTask.add(buildString {
          appendLine("✅ Analysis complete")
          appendLine()
          appendLine(loopsAnalysis)
        }.renderMarkdown())
      }

      // Step 3: Delays and Accumulations
      if (executionConfig.map_delays) {
        log.debug("Mapping delays and accumulations")
        val delaysTask = tabs.newTask("Delays & Accumulations")

        delaysTask.header("Delays & Accumulations", level = 2)
        delaysTask.add("🔄 Analyzing time lags and stocks...".renderMarkdown())

        val delaysAnalysis = analysisAgent.answer(
          listOf(
            """
Analyze delays and accumulations:
1. **Delays**: Identify time lags between cause and effect
   - Information delays
   - Physical delays
   - Decision delays
2. **Accumulations (Stocks)**: Identify what accumulates over time
   - What builds up?
   - What depletes?
   - What are the inflows and outflows?
3. **Impact**: How do these delays and accumulations affect system behavior?

Provide specific examples with estimated time scales.
          """.trimIndent()
          )
        )

        delaysTask.add(buildString {
          appendLine("✅ Analysis complete")
          appendLine()
          appendLine(delaysAnalysis)
        }.renderMarkdown())
        transcript?.write("## Delays & Accumulations\n\n$delaysAnalysis\n\n---\n\n".toByteArray())
      }

      // Step 4: System Archetypes
      if (executionConfig.identify_archetypes) {
        log.debug("Identifying system archetypes")
        val archetypesTask = tabs.newTask("System Archetypes")

        archetypesTask.header("System Archetypes", level = 2)
        archetypesTask.add("🔄 Identifying common patterns...".renderMarkdown())

        val archetypesAnalysis = analysisAgent.answer(
          listOf(
            """
Identify system archetypes present in this system. Common archetypes include:
- **Limits to Growth**: Growth slows as limits are approached
- **Shifting the Burden**: Short-term fixes undermine long-term solutions
- **Eroding Goals**: Performance standards decline over time
- **Escalation**: Competing parties escalate actions
- **Success to the Successful**: Winners get more resources
- **Tragedy of the Commons**: Individual actions deplete shared resources
- **Fixes that Fail**: Solutions create new problems
- **Growth and Underinvestment**: Growth is limited by underinvestment

For each archetype found:
1. Name the archetype
2. Explain how it manifests in this system
3. Describe the typical behavior pattern
4. Suggest intervention strategies

Focus on the most relevant archetypes.
          """.trimIndent()
          )
        )

        archetypesTask.add(buildString {
          appendLine("✅ Analysis complete")
          appendLine()
          appendLine(archetypesAnalysis)
        }.renderMarkdown())
        transcript?.write("## System Archetypes\n\n$archetypesAnalysis\n\n---\n\n".toByteArray())
      }

      // Step 5: Emergent Behavior
      if (executionConfig.analyze_emergent_behavior) {
        log.debug("Analyzing emergent behavior")
        val emergentTask = tabs.newTask("Emergent Behavior")

        emergentTask.header("Emergent Behavior", level = 2)
        emergentTask.add("🔄 Predicting system-level patterns...".renderMarkdown())

        val emergentAnalysis = analysisAgent.answer(
          listOf(
            """
Analyze emergent behavior in the system:
1. **Current Emergent Patterns**: What system-level behaviors emerge from component interactions?
2. **Unintended Consequences**: What side effects or unexpected outcomes occur?
3. **Future Predictions**: Over the $timeHorizon time horizon, what behaviors are likely to emerge?
4. **Tipping Points**: Are there thresholds where behavior changes dramatically?
5. **Resilience**: How does the system respond to disruptions?

Consider both positive and negative emergent behaviors.
          """.trimIndent()
          )
        )

        emergentTask.add(buildString {
          appendLine("✅ Analysis complete")
          appendLine()
          appendLine(emergentAnalysis)
        }.renderMarkdown())
        transcript?.write("## Emergent Behavior\n\n$emergentAnalysis\n\n---\n\n".toByteArray())
      }

      // Step 6: Leverage Points
      if (executionConfig.find_leverage_points) {
        log.debug("Finding leverage points")
        val leverageTask = tabs.newTask("Leverage Points")

        leverageTask.header("Leverage Points", level = 2)
        leverageTask.add("🔄 Identifying high-impact intervention points...".renderMarkdown())

        val leverageAnalysis = analysisAgent.answer(
          listOf(
            """
Identify leverage points for intervention, ranked by effectiveness (Meadows' hierarchy):
1. **Paradigms**: Mindsets and goals that shape the system
2. **Goals**: System objectives and metrics
3. **Self-Organization**: System structure and evolution
4. **Rules**: Incentives, constraints, and feedback
5. **Information Flows**: Who has access to what information
6. **Reinforcing Loops**: Amplifying feedback mechanisms
7. **Balancing Loops**: Stabilizing feedback mechanisms
8. **Delays**: Time lags in the system
9. **Stocks and Flows**: Material and information accumulations
10. **Parameters**: Constants and rates

For each leverage point:
- Describe the intervention
- Explain why it's high-leverage
- Estimate impact (High/Medium/Low)
- Note potential risks or side effects
- Suggest implementation approach

Focus on the most impactful leverage points.
          """.trimIndent()
          )
        )

        leverageTask.add(buildString {
          appendLine("✅ Analysis complete")
          appendLine()
          appendLine(leverageAnalysis)
        }.renderMarkdown())
        transcript?.write("## Leverage Points\n\n$leverageAnalysis\n\n---\n\n".toByteArray())
      }

      // Step 7: Intervention Simulation
      if (interventions.isNotEmpty()) {
        log.debug("Simulating ${interventions.size} interventions")
        val simulationTask = tabs.newTask("Intervention Simulation")
        simulationTask.header("Intervention Simulation", level = 2)

        simulationTask.add(buildString {
          appendLine("🔄 Simulating ${interventions.size} intervention scenarios...")
          appendLine()
          appendLine("**Interventions:**")
          interventions.forEach { appendLine("- $it") }
        }.renderMarkdown())

        val simulationResults = mutableListOf<String>()
        interventions.forEachIndexed { index, intervention ->
          log.debug("Simulating intervention ${index + 1}/${interventions.size}: $intervention")

          val simulationAnalysis = analysisAgent.answer(
            listOf(
              """
Simulate the intervention: "$intervention"

Analyze over the $timeHorizon time horizon:
1. **Immediate Effects** (0-1 month): What happens right away?
2. **Short-term Effects** (1-3 months): How does the system respond?
3. **Medium-term Effects** (3-6 months): What patterns emerge?
4. **Long-term Effects** (6+ months): What is the steady-state outcome?
5. **Feedback Loop Impacts**: Which loops are strengthened/weakened?
6. **Unintended Consequences**: What side effects might occur?
7. **Overall Assessment**: Rate effectiveness (High/Medium/Low) and explain

Be specific about mechanisms and timelines.
              """.trimIndent()
            )
          )

          val resultMarkdown = """
### Intervention ${index + 1}: $intervention

$simulationAnalysis

---
          """.trimIndent()
          simulationResults.add(resultMarkdown)

          // Stream result to UI


          simulationTask.add(resultMarkdown.renderMarkdown())

          transcript?.write(resultMarkdown.toByteArray())
        }

        simulationTask.add(buildString {
          appendLine("✅ Simulation complete")
        }.renderMarkdown())
      }

      // Step 8: Synthesis and Recommendations
      log.debug("Generating synthesis and recommendations")
      val synthesisTask = tabs.newTask("Synthesis")

      synthesisTask.header("Synthesis & Recommendations", level = 2)
      synthesisTask.add("🔄 Generating comprehensive synthesis...".renderMarkdown())

      val synthesisPrompt = buildString {
        appendLine("Provide a comprehensive synthesis of the systems thinking analysis:")
        appendLine()
        appendLine("1. **Key Insights**: What are the most important findings?")
        appendLine("2. **System Behavior Summary**: How does this system behave and why?")
        appendLine("3. **Critical Feedback Loops**: Which loops drive system behavior?")
        appendLine("4. **Highest-Impact Leverage Points**: Where should interventions focus?")
        if (interventions.isNotEmpty()) {
          appendLine("5. **Recommended Interventions**: Which simulated interventions are most promising?")
        }
        appendLine("6. **Implementation Roadmap**: Suggested sequence of actions")
        appendLine("7. **Monitoring Metrics**: What should be tracked to assess progress?")
        appendLine("8. **Risks and Mitigation**: What could go wrong and how to prevent it?")
        appendLine()
        appendLine("Provide actionable, prioritized recommendations.")
      }

      val synthesis = analysisAgent.answer(listOf(synthesisPrompt.toString()))

      synthesisTask.add(buildString {
        appendLine("✅ Analysis complete")
        appendLine()
        appendLine(synthesis)
      }.renderMarkdown())
      transcript?.write("## Synthesis & Recommendations\n\n$synthesis\n\n---\n\n".toByteArray())

      // Build concise final result
      val finalResult = buildString {
        appendLine("# Systems Thinking Analysis: $systemDescription")
        appendLine()
        appendLine("**Time Horizon:** $timeHorizon")
        appendLine()
        appendLine("## Key Findings")
        appendLine()
        appendLine(synthesis.truncateForDisplay(maxDescriptionLength))
        appendLine()
        appendLine("---")
        appendLine()
        appendLine(
          "**Analysis Components:** ${
            listOfNotNull(
              if (executionConfig.identify_feedback_loops) "Feedback Loops" else null,
              if (executionConfig.map_delays) "Delays" else null,
              if (executionConfig.find_leverage_points) "Leverage Points" else null,
              if (executionConfig.identify_archetypes) "Archetypes" else null,
              if (executionConfig.analyze_emergent_behavior) "Emergent Behavior" else null,
              if (interventions.isNotEmpty()) "Intervention Simulation (${interventions.size})" else null
            ).joinToString(", ")
          }"
        )
      }

      val duration = System.currentTimeMillis() - startTime
      log.info(
        "SystemsThinkingTask completed: system='$systemDescription', " +
            "duration=${duration}ms, interventions=${interventions.size}, " +
            "output_size=${finalResult.length} chars"
      )

      overviewTask.add(buildString {
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## ✅ Analysis Complete")
        appendLine()
        appendLine("**Total Time:** ${duration / 1000.0}s")
        appendLine()
        appendLine(
          "**Components Analyzed:** ${
            listOfNotNull(
              if (executionConfig.identify_feedback_loops) "Feedback Loops" else null,
              if (executionConfig.map_delays) "Delays & Accumulations" else null,
              if (executionConfig.find_leverage_points) "Leverage Points" else null,
              if (executionConfig.identify_archetypes) "System Archetypes" else null,
              if (executionConfig.analyze_emergent_behavior) "Emergent Behavior" else null,
              if (interventions.isNotEmpty()) "Intervention Simulation" else null
            ).size
          }"
        )
        appendLine()
        if (interventions.isNotEmpty()) {
          appendLine("**Interventions Simulated:** ${interventions.size}")
          appendLine()
        }
        appendLine(
          "**Completed:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }"
        )
      }.renderMarkdown())
      transcript?.write(
        "\n\n## Analysis Complete\n\n**Total Time:** ${duration / 1000.0}s\n\n**Completed:** ${
          LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }\n".toByteArray()
      )


      task.complete("Systems thinking analysis completed in ${duration / 1000}s.".renderMarkdown())
      resultFn(finalResult.toString())

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - startTime
      transcript?.write(
        """
                <details>
                <summary>Error Occurred: ${e.message}</summary>
                
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent().toByteArray()
      )

      log.error("SystemsThinkingTask failed after ${duration}ms for system: $systemDescription", e)
      task.error(e)

      overviewTask.add(buildString {
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## ❌ Error Occurred")
        appendLine()
        appendLine("**Error:** ${e.message?.take(200)}")
        appendLine()
        appendLine("**Type:** ${e.javaClass.simpleName}")
      }.renderMarkdown())

      val errorOutput = buildString {
        appendLine("## Analysis Failed")
        appendLine()
        appendLine("The systems thinking analysis encountered an error.")
        appendLine("# Error in Systems Thinking Analysis")
        appendLine()
        appendLine("**System:** $systemDescription")
        appendLine()
        appendLine("**Error:** ${e.message}")
      }
      resultFn(errorOutput.toString())
    } finally {
      transcript?.close()
    }
  }

  private fun buildSystemsThinkingPrompt(
    systemDescription: String,
    timeHorizon: String,
    priorContext: String,
    relatedContext: String,
    focusAreas: List<String>,
    analysisQuestions: List<String>
  ): String {
    return """
 You are an expert in systems thinking, system dynamics, and complex adaptive systems. Your role is to analyze systems through the lens of feedback loops, delays, accumulations, and emergent behavior.

 ## System to Analyze:
 $systemDescription

 ## Time Horizon:
 $timeHorizon

${if (focusAreas.isNotEmpty()) "## Focus Areas:\n${focusAreas.joinToString("\n") { "- $it" }}\n\n" else ""}
${if (analysisQuestions.isNotEmpty()) "## Specific Questions to Address:\n${analysisQuestions.joinToString("\n") { "- $it" }}\n\n" else ""}
 ## Context:
 ${if (priorContext.isNotBlank()) "### Prior Task Results:\n$priorContext\n\n" else ""}
 ${if (relatedContext.isNotBlank()) "### Related Files:\n$relatedContext\n\n" else ""}

 ## Systems Thinking Principles:
 1. **Feedback Loops**: Systems are governed by reinforcing and balancing feedback loops
 2. **Delays**: Time lags between cause and effect create oscillations and instability
 3. **Stocks and Flows**: Accumulations (stocks) change through inflows and outflows
 4. **Non-linearity**: Small changes can have large effects; large changes can have small effects
 5. **Emergence**: System behavior arises from interactions, not individual components
 6. **Boundaries**: System definition affects what we see and can influence
 7. **Leverage Points**: Some interventions are far more effective than others

 ## Analysis Approach:
- Think in terms of circular causality, not linear cause-and-effect
- Look for feedback loops that drive system behavior
- Identify delays that create problems
- Find leverage points where small changes have big impacts
- Consider unintended consequences and side effects
- Use system archetypes to recognize common patterns
- Think long-term about how interventions play out over time
${if (focusAreas.isNotEmpty()) "- Prioritize analysis of the specified focus areas" else ""}
${if (analysisQuestions.isNotEmpty()) "- Ensure the specific analysis questions are addressed" else ""}

Provide clear, actionable insights grounded in systems thinking principles.
    """.trimIndent()
  }

  private fun gatherRelatedFiles(): String {
    val relatedFiles = executionConfig?.related_files ?: return ""
    if (relatedFiles.isEmpty()) return ""

    val maxFileSize = 2000
    val maxTotalSize = 8000
    var totalSize = 0

    return relatedFiles.mapNotNull { pattern ->
      val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
      root.toFile().walkTopDown()
        .filter { file ->
          file.isFile && matcher.matches(root.relativize(file.toPath()))
        }
        .take(5) // Limit files per pattern
        .mapNotNull { file ->
          if (totalSize >= maxTotalSize) return@mapNotNull null
          val relativePath = root.relativize(file.toPath())
          try {
            val content = file.readText()
            totalSize += content.length.coerceAtMost(maxFileSize)
            "### $relativePath\n```\n${content.truncateForDisplay(maxFileSize)}\n```"
          } catch (e: Exception) {
            log.warn("Error reading file: $relativePath", e)
            null
          }
        }
        .joinToString("\n\n")
    }.joinToString("\n\n")
  }

  private fun getInputFileCode() = (executionConfig?.related_files ?: listOf())
    .flatMap { pattern: String ->
      val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
      (FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
          matcher.matches(root.relativize(it.toPath())) -> true
          it.isDirectory -> true
          else -> false
        }
      })
    }.filter { file ->
      file.isFile && file.exists()
    }
    .distinct()
    .sortedBy { it }
    .joinToString("\n\n") { relativePath ->
      val file = root.toFile().resolve(relativePath)
      try {
        val content = file.readText()
        "# $relativePath\n\n```\n$content\n```"
      } catch (e: Throwable) {
        log.warn("Error reading file: $relativePath", e)
        ""
      }
    }


  companion object {
    private val log: Logger = LoggerFactory.getLogger(SystemsThinkingTask::class.java)

    @JvmStatic
    val SystemsThinking = TaskType(
      name = "SystemsThinking",
      category = "Reasoning",
      taskClass = SystemsThinkingTask::class.java,
      executionConfigClass = SystemsThinkingTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Analyze complex systems through feedback loops and dynamics",
      tooltipHtml = """
                        Performs systems thinking analysis to understand complex system behavior.
                        <ul>
                          <li>Identifies feedback loops (reinforcing and balancing)</li>
                          <li>Maps system archetypes (e.g., "Limits to Growth", "Shifting the Burden")</li>
                          <li>Analyzes delays and accumulations</li>
                          <li>Predicts emergent behavior and unintended consequences</li>
                          <li>Finds high-leverage intervention points</li>
                          <li>Simulates potential interventions over time</li>
                          <li>Useful for understanding system dynamics, optimization, and organizational change</li>
                        </ul>
                      """,
    )
  }
}