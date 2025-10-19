package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FiniteStateMachineTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: FiniteStateMachineTaskExecutionConfigData?
) : AbstractTask<FiniteStateMachineTask.FiniteStateMachineTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class FiniteStateMachineTaskExecutionConfigData(
    @Description("The concept, system, or process to model as a finite state machine")
    val concept_to_model: String? = null,
    @Description("Initial state(s) to consider")
    val initial_states: List<String>? = null,
    @Description("Known events or triggers that cause state transitions")
    val known_events: List<String>? = null,
    @Description("Whether to identify edge cases and error states")
    val identify_edge_cases: Boolean = true,
    @Description("Whether to validate state machine properties (determinism, completeness, reachability)")
    val validate_properties: Boolean = true,
    @Description("Whether to generate test scenarios for state transitions")
    val generate_test_scenarios: Boolean = true,
    @Description("Domain or context for the FSM (e.g., 'authentication system', 'order processing')")
    val domain_context: String? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = FiniteStateMachine.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment(): String {
    return """
FiniteStateMachine - Model concepts using finite state machine analysis
  ** Specify the concept, system, or process to model
  ** Optionally provide initial states and known events
  ** Identify all possible states and transitions
  ** Detect edge cases and error states
  ** Validate FSM properties (determinism, completeness, reachability)
  ** Generate test scenarios for state transitions
  ** Produces state diagram and transition table
  ** Useful for:
     - System design and validation
     - Understanding complex workflows
     - Identifying missing requirements
     - Test case generation
     - Protocol analysis
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val startTime = System.currentTimeMillis()
    log.info("Starting FiniteStateMachineTask for concept: '${executionConfig?.concept_to_model}'")

    val conceptToModel = executionConfig?.concept_to_model
    if (conceptToModel.isNullOrBlank()) {
      val errorMsg = "CONFIGURATION ERROR: No concept to model specified"
      log.error(errorMsg)
      task.complete(errorMsg)
      resultFn(errorMsg)
      return
    }

    val ui = task.ui
    val api = orchestrationConfig.defaultChatter ?: run {
      log.error("No default chatter available")
      task.complete("ERROR: No API available")
      resultFn("ERROR: No API available")
      return
    }

    try {
      // Create tabbed display for organized output
      val tabs = TabbedDisplay(task)

      // Overview tab
      val overviewTask = task.ui.newTask(false)
      tabs["Overview"] = overviewTask.placeholder

      val domainContext = executionConfig.domain_context ?: "general domain"
      val initialStates = executionConfig.initial_states ?: emptyList()
      val knownEvents = executionConfig.known_events ?: emptyList()

      var overviewContent = overviewTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## Finite State Machine Analysis
            |
            |**Concept:** $conceptToModel
            |
            |**Domain:** $domainContext
            |
            |**Initial States:** ${if (initialStates.isNotEmpty()) initialStates.joinToString(", ") else "To be identified"}
            |
            |**Known Events:** ${if (knownEvents.isNotEmpty()) knownEvents.joinToString(", ") else "To be identified"}
            |
            |**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
            |
            |---
            |
            |**Status:** 🔄 Analyzing concept and identifying states...
          """.trimMargin(), ui = ui
        )
      )
      task.update()

      log.debug("Gathering prior context from execution state")
      val priorContext = getPriorCode(agent.executionState)

      // Step 1: Identify States
      log.info("Step 1: Identifying all possible states")
      val statesTask = task.ui.newTask(false)
      tabs["States"] = statesTask.placeholder

      val statesLoading = statesTask.add(
        MarkdownUtil.renderMarkdown("## State Identification\n\n🔄 Analyzing concept to identify all possible states...", ui = ui)
      )
      task.update()

      val stateIdentificationPrompt = buildStateIdentificationPrompt(
        conceptToModel,
        domainContext,
        initialStates,
        priorContext
      )

      val stateAgent = ChatAgent(
        prompt = stateIdentificationPrompt,
        model = api,
        temperature = 0.3
      )

      log.debug("Requesting state identification from LLM")
      val statesAnalysis = stateAgent.answer(listOf("Identify all possible states for this concept."))

      statesLoading?.clear()
      statesTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## Identified States
            |
            |✅ State analysis complete
            |
            |$statesAnalysis
          """.trimMargin(), ui = ui
        )
      )
      task.update()

      // Update overview
      overviewContent?.clear()
      overviewContent = overviewTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## Finite State Machine Analysis
            |
            |**Concept:** $conceptToModel
            |
            |**Domain:** $domainContext
            |
            |**Status:** 🔄 Identifying transitions and events...
          """.trimMargin(), ui = ui
        )
      )
      task.update()

      // Step 2: Identify Transitions
      log.info("Step 2: Identifying state transitions and events")
      val transitionsTask = task.ui.newTask(false)
      tabs["Transitions"] = transitionsTask.placeholder

      val transitionsLoading = transitionsTask.add(
        MarkdownUtil.renderMarkdown("## Transition Analysis\n\n🔄 Identifying events and state transitions...", ui = ui)
      )
      task.update()

      val transitionPrompt = buildTransitionPrompt(
        statesAnalysis,
        knownEvents
      )

      log.debug("Requesting transition analysis from LLM")
      val transitionsAnalysis = stateAgent.answer(listOf(transitionPrompt))

      transitionsLoading?.clear()
      transitionsTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## State Transitions
            |
            |✅ Transition analysis complete
            |
            |$transitionsAnalysis
          """.trimMargin(), ui = ui
        )
      )
      task.update()

      // Step 3: Generate State Diagram
      log.info("Step 3: Generating state diagram")
      val diagramTask = task.ui.newTask(false)
      tabs["State Diagram"] = diagramTask.placeholder

      val diagramLoading = diagramTask.add(
        MarkdownUtil.renderMarkdown("## State Diagram\n\n🔄 Generating visual representation...", ui = ui)
      )
      task.update()

      val diagramPrompt = """
Based on the states and transitions identified above, create a Mermaid state diagram.

Use the following format:
- Use `stateDiagram-v2` for the diagram type
- Show all states clearly
- Use `-->` for transitions with event labels
- Mark initial state with `[*]`
- Mark final/terminal states with `[*]` as destination
- Use descriptive labels for transitions
- Group related states if applicable

Generate the Mermaid diagram now:
      """.trimIndent()

      log.debug("Requesting state diagram from LLM")
      val diagramResult = stateAgent.answer(listOf(diagramPrompt))
      val mermaidCode = extractMermaidCode(diagramResult)

      diagramLoading?.clear()
      if (mermaidCode.isNotEmpty()) {
        diagramTask.add(
          MarkdownUtil.renderMarkdown(
            """
              |## State Diagram
              |
              |✅ Diagram generated successfully
              |
              |```mermaid
              |$mermaidCode
              |```
            """.trimMargin(), ui = ui
          )
        )
      } else {
        diagramTask.add(
          MarkdownUtil.renderMarkdown(
            """
              |## State Diagram
              |
              |⚠️ Failed to generate diagram
              |
              |Raw output:
              |```
              |$diagramResult
              |```
            """.trimMargin(), ui = ui
          )
        )
      }
      task.update()

      // Step 4: Edge Cases (if enabled)
      var edgeCasesAnalysis: String
      if (executionConfig.identify_edge_cases) {
        log.info("Step 4: Identifying edge cases and error states")
        val edgeCasesTask = task.ui.newTask(false)
        tabs["Edge Cases"] = edgeCasesTask.placeholder

        val edgeCasesLoading = edgeCasesTask.add(
          MarkdownUtil.renderMarkdown("## Edge Cases Analysis\n\n🔄 Identifying edge cases and error states...", ui = ui)
        )
        task.update()

        val edgeCasesPrompt = """
Analyze the finite state machine for edge cases and error conditions:

1. **Invalid Transitions:** Identify events that could occur in states where they're not valid
2. **Missing Transitions:** Find states that might be missing important transitions
3. **Error States:** Identify states that represent error conditions
4. **Recovery Paths:** Suggest how to recover from error states
5. **Boundary Conditions:** Identify unusual or extreme scenarios
6. **Race Conditions:** Identify potential concurrent event issues

Provide a structured analysis of edge cases and recommendations.
        """.trimIndent()

        log.debug("Requesting edge case analysis from LLM")
        edgeCasesAnalysis = stateAgent.answer(listOf(edgeCasesPrompt))

        edgeCasesLoading?.clear()
        edgeCasesTask.add(
          MarkdownUtil.renderMarkdown(
            """
              |## Edge Cases and Error States
              |
              |✅ Edge case analysis complete
              |
              |$edgeCasesAnalysis
            """.trimMargin(), ui = ui
          )
        )
        task.update()
      }

      // Step 5: Validation (if enabled)
      var validationAnalysis = ""
      if (executionConfig.validate_properties) {
        log.info("Step 5: Validating FSM properties")
        val validationTask = task.ui.newTask(false)
        tabs["Validation"] = validationTask.placeholder

        val validationLoading = validationTask.add(
          MarkdownUtil.renderMarkdown("## FSM Validation\n\n🔄 Validating state machine properties...", ui = ui)
        )
        task.update()

        val validationPrompt = """
Validate the following properties of this finite state machine:

1. **Determinism:** Is the FSM deterministic? (Each state + event combination leads to exactly one next state)
2. **Completeness:** Are all possible events handled in every state?
3. **Reachability:** Can all states be reached from the initial state(s)?
4. **Liveness:** Are there any deadlock states (states with no outgoing transitions)?
5. **Safety:** Are there any unsafe state transitions?
6. **Minimality:** Are there redundant states that could be merged?

For each property, provide:
- ✅ Pass or ❌ Fail
- Explanation
- Specific examples if failing
- Recommendations for fixes

Provide a structured validation report.
        """.trimIndent()

        log.debug("Requesting FSM validation from LLM")
        validationAnalysis = stateAgent.answer(listOf(validationPrompt))

        validationLoading?.clear()
        validationTask.add(
          MarkdownUtil.renderMarkdown(
            """
              |## FSM Property Validation
              |
              |✅ Validation complete
              |
              |$validationAnalysis
            """.trimMargin(), ui = ui
          )
        )
        task.update()
      }

      // Step 6: Test Scenarios (if enabled)
      var testScenariosAnalysis: String
      if (executionConfig.generate_test_scenarios) {
        log.info("Step 6: Generating test scenarios")
        val testScenariosTask = task.ui.newTask(false)
        tabs["Test Scenarios"] = testScenariosTask.placeholder

        val testScenariosLoading = testScenariosTask.add(
          MarkdownUtil.renderMarkdown("## Test Scenario Generation\n\n🔄 Creating test scenarios...", ui = ui)
        )
        task.update()

        val testScenariosPrompt = """
Generate comprehensive test scenarios for this finite state machine:

1. **Happy Path Tests:** Normal sequences of events leading to successful outcomes
2. **Error Path Tests:** Sequences that should trigger error states
3. **Boundary Tests:** Edge cases and unusual sequences
4. **State Coverage Tests:** Scenarios that exercise all states
5. **Transition Coverage Tests:** Scenarios that exercise all transitions

For each test scenario, provide:
- **Scenario Name:** Descriptive name
- **Initial State:** Starting state
- **Event Sequence:** List of events in order
- **Expected States:** State after each event
- **Expected Outcome:** Final state and result
- **Purpose:** What this test validates

Generate at least 5-10 diverse test scenarios.
        """.trimIndent()

        log.debug("Requesting test scenario generation from LLM")
        testScenariosAnalysis = stateAgent.answer(listOf(testScenariosPrompt))

        testScenariosLoading?.clear()
        testScenariosTask.add(
          MarkdownUtil.renderMarkdown(
            """
              |## Test Scenarios
              |
              |✅ Test scenarios generated
              |
              |$testScenariosAnalysis
            """.trimMargin(), ui = ui
          )
        )
        task.update()
      }

      // Step 7: Generate Summary
      log.info("Step 7: Generating comprehensive summary")
      val summaryTask = task.ui.newTask(false)
      tabs["Summary"] = summaryTask.placeholder

      val summaryLoading = summaryTask.add(
        MarkdownUtil.renderMarkdown("## Summary\n\n🔄 Generating comprehensive summary...", ui = ui)
      )
      task.update()

      val summaryPrompt = """
Provide a comprehensive summary of the finite state machine analysis:

1. **Overview:** Brief description of the FSM and its purpose
2. **Key States:** List the most important states (3-5)
3. **Critical Transitions:** Highlight the most important state transitions
4. **Key Findings:** Main insights from the analysis
5. **Recommendations:** Top 3-5 actionable recommendations for improvement
6. **Complexity Assessment:** Evaluate the complexity of this FSM

Keep the summary concise but informative.
      """.trimIndent()

      log.debug("Requesting summary from LLM")
      val summaryAnalysis = stateAgent.answer(listOf(summaryPrompt))

      summaryLoading?.clear()
      summaryTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## Analysis Summary
            |
            |✅ Summary complete
            |
            |$summaryAnalysis
          """.trimMargin(), ui = ui
        )
      )
      task.update()

      // Final overview update
      overviewContent?.clear()
      val totalTime = System.currentTimeMillis() - startTime
      overviewTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## Finite State Machine Analysis
            |
            |**Concept:** $conceptToModel
            |
            |**Domain:** $domainContext
            |
            |**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
            |
            |**Duration:** ${totalTime / 1000.0}s
            |
            |---
            |
            |**Status:** ✅ Analysis complete
            |
            |### Analysis Components:
            |- ✅ State identification
            |- ✅ Transition analysis
            |- ✅ State diagram
            |${if (executionConfig.identify_edge_cases) "- ✅ Edge case analysis" else ""}
            |${if (executionConfig.validate_properties) "- ✅ Property validation" else ""}
            |${if (executionConfig.generate_test_scenarios) "- ✅ Test scenario generation" else ""}
            |- ✅ Summary and recommendations
          """.trimMargin(), ui = ui
        )
      )
      task.update()

      // Build concise result for task dependencies
      val conciseResult = buildString {
        appendLine("# FSM Analysis: $conceptToModel")
        appendLine()
        appendLine("## Summary")
        appendLine(summaryAnalysis.take(500))
        if (summaryAnalysis.length > 500) appendLine("...")
        appendLine()
        appendLine("## Key Components")
        appendLine("- States identified and analyzed")
        appendLine("- Transitions mapped")
        appendLine("- State diagram generated")
        if (executionConfig.identify_edge_cases) appendLine("- Edge cases identified")
        if (executionConfig.validate_properties) appendLine("- Properties validated")
        if (executionConfig.generate_test_scenarios) appendLine("- Test scenarios generated")
      }

      log.info("FiniteStateMachineTask completed: concept='$conceptToModel', duration=${totalTime}ms, output_size=${conciseResult.length} chars")

      task.complete("FSM analysis completed for: $conceptToModel")
      resultFn(conciseResult)

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - startTime
      log.error("FiniteStateMachineTask failed after ${duration}ms for concept: $conceptToModel", e)
      task.error(e)
      task.complete("Analysis failed: ${e.message}")
      resultFn("ERROR: FSM analysis failed - ${e.message}")
    }
  }

  private fun buildStateIdentificationPrompt(
    concept: String,
    domain: String,
    initialStates: List<String>,
    priorContext: String
  ): String {
    val initialStatesSection = if (initialStates.isNotEmpty()) {
      """
        |
        |## Known Initial States:
        |${initialStates.joinToString("\n") { "- $it" }}
      """.trimMargin()
    } else {
      ""
    }

    val contextSection = if (priorContext.isNotBlank()) {
      """
        |
        |## Context from Previous Tasks:
        |$priorContext
      """.trimMargin()
    } else {
      ""
    }

    return """
You are an expert in formal methods and finite state machine modeling. Your task is to analyze a concept and identify all possible states.

## Concept to Model:
$concept

## Domain Context:
$domain
$initialStatesSection
$contextSection

## Instructions:
Identify all possible states for this concept. For each state, provide:

1. **State Name:** Clear, descriptive name
2. **Description:** What this state represents
3. **Type:** (Initial, Normal, Error, Final/Terminal)
4. **Invariants:** Conditions that must be true in this state
5. **Entry Conditions:** What must happen to enter this state
6. **Exit Conditions:** What can cause leaving this state

Consider:
- Normal operational states
- Error or exception states
- Transient vs stable states
- Initial and final states

Provide a comprehensive list of states with detailed descriptions.
    """.trimIndent()
  }

  private fun buildTransitionPrompt(
    statesAnalysis: String,
    knownEvents: List<String>
  ): String {
    val eventsSection = if (knownEvents.isNotEmpty()) {
      """
        |
        |## Known Events:
        |${knownEvents.joinToString("\n") { "- $it" }}
      """.trimMargin()
    } else {
      ""
    }

    return """
Based on the states identified above, now identify all possible state transitions.

## States Analysis:
$statesAnalysis
$eventsSection

## Instructions:
For each possible transition, provide:

1. **Source State:** The state before the transition
2. **Event/Trigger:** What causes the transition
3. **Target State:** The state after the transition
4. **Guard Conditions:** Conditions that must be true for the transition to occur
5. **Actions:** Side effects or actions performed during the transition
6. **Priority:** If multiple transitions possible from same state

Create a comprehensive transition table covering:
- All valid transitions between states
- Self-transitions (state to itself)
- Error transitions
- Recovery transitions

Format as a clear table or structured list.
    """.trimIndent()
  }

  private fun extractMermaidCode(response: String): String {
    // Try to extract mermaid code block
    val mermaidBlockRegex = "```mermaid\\s*([\\s\\S]*?)```".toRegex()
    val match = mermaidBlockRegex.find(response)
    if (match != null) {
      return match.groupValues[1].trim()
    }

    // Try to extract stateDiagram-v2 directly
    val stateDiagramRegex = "(stateDiagram-v2[\\s\\S]*?)(?=```|$)".toRegex()
    val stateDiagramMatch = stateDiagramRegex.find(response)
    if (stateDiagramMatch != null) {
      return stateDiagramMatch.groupValues[1].trim()
    }

    return ""
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(FiniteStateMachineTask::class.java)

    val FiniteStateMachine = TaskType(
      "FiniteStateMachine",
      FiniteStateMachineTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Model concepts using finite state machine analysis",
      """
        Analyzes concepts, systems, or processes using finite state machine modeling.
        <ul>
          <li>Identifies all possible states and their properties</li>
          <li>Maps state transitions and triggering events</li>
          <li>Generates visual state diagrams</li>
          <li>Identifies edge cases and error states</li>
          <li>Validates FSM properties (determinism, completeness, reachability)</li>
          <li>Generates comprehensive test scenarios</li>
          <li>Useful for system design, protocol analysis, and workflow validation</li>
        </ul>
      """
    )
  }
}