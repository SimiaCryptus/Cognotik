package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.input.PaginatedDocumentReader
import com.simiacryptus.cognotik.input.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FiniteStateMachineTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: FiniteStateMachineTaskExecutionConfigData?
) : AbstractTask<FiniteStateMachineTask.FiniteStateMachineTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    protected val codeFiles = mutableMapOf<java.nio.file.Path, String>()
    val maxDescriptionLength = 2000

    class FiniteStateMachineTaskExecutionConfigData(
        @Description("The concept, system, or process to model as a finite state machine")
        var concept_to_model: String? = null,
        @Description("Initial state(s) to consider")
        var initial_states: List<String>? = null,
        @Description("Known events or triggers that cause state transitions")
        var known_events: List<String>? = null,
        @Description("Whether to identify edge cases and error states")
        var identify_edge_cases: Boolean = true,
        @Description("Whether to validate state machine properties (determinism, completeness, reachability)")
        var validate_properties: Boolean = true,
        @Description("Whether to generate test scenarios for state transitions")
        var generate_test_scenarios: Boolean = true,
        @Description("Domain or context for the FSM (e.g., 'authentication system', 'order processing')")
        var domain_context: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
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
        val conceptToModel = executionConfig?.concept_to_model
      val startTime = System.currentTimeMillis()




      task.ui.pool.submit {
        val transcript = task.transcript()
        try {
          log.info("Starting FiniteStateMachineTask for concept: '$conceptToModel'")
          transcript?.write("# Finite State Machine Analysis\n\n".toByteArray())
          transcript?.write("**Started:** ${LocalDateTime.now()}\n\n".toByteArray())

          if (conceptToModel.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No concept to model specified"
            log.error(errorMsg)
            task.error(Exception(errorMsg))
            resultFn(errorMsg)
            return@submit
          }

          task.ui
          val api = defaultSmart ?: throw IllegalStateException("No default chatter available")

            val domainContext = executionConfig.domain_context ?: "general domain"
            val initialStates = executionConfig.initial_states ?: emptyList()
            val knownEvents = executionConfig.known_events ?: emptyList()

            // Create tabbed display for organized output
            val tabs = TabbedDisplay(task)
            // Initialize full report builder
            val fullReport = StringBuilder()
            fullReport.append("# Finite State Machine Analysis: $conceptToModel\n\n")
            fullReport.append("**Domain:** $domainContext\n")
            fullReport.append("**Date:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")

            // Overview tab
            val overviewTask = tabs.newTask("Overview")

          transcript?.write("## Configuration\n".toByteArray())
          transcript?.write("<details><summary>Task Parameters</summary>\n\n".toByteArray())
          transcript?.write("**Concept:** $conceptToModel\n".toByteArray())
          transcript?.write("**Domain:** $domainContext\n".toByteArray())
          transcript?.write("**Initial States:** ${initialStates.joinToString(", ")}\n".toByteArray())
          transcript?.write("**Known Events:** ${knownEvents.joinToString(", ")}\n".toByteArray())
          transcript?.write("</details>\n\n".toByteArray())

            var overviewContent = overviewTask.add(
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
          """.trimMargin().renderMarkdown()
            )
            task.update()

            log.debug("Gathering prior context from execution state")
            val priorContext = getPriorCode(agent.executionState)
            val inputFileContent = getInputFileCode()

            // Step 1: Identify States
            log.info("Step 1: Identifying all possible states")
            val statesTask = tabs.newTask("States")

            val statesLoading = statesTask.add(
              "## State Identification\n\n🔄 Analyzing concept to identify all possible states...".renderMarkdown()
            )
            task.update()

            val stateIdentificationPrompt = buildStateIdentificationPrompt(
                conceptToModel,
                domainContext,
                initialStates,
                priorContext,
                inputFileContent
            )

            val stateAgent = ChatAgent(
                prompt = stateIdentificationPrompt,
                model = api,
                temperature = 0.3
            )

            log.debug("Requesting state identification from LLM")
            val statesAnalysis = stateAgent.answer(listOf("Identify all possible states for this concept."))
          transcript?.write("## Step 1: State Identification\n".toByteArray())
          transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$stateIdentificationPrompt\n```\n\n### Response\n\n$statesAnalysis\n</details>\n\n".toByteArray())
            fullReport.append("## 1. State Identification\n\n$statesAnalysis\n\n")

            statesLoading?.clear()
            statesTask.add(
                    """
            |## Identified States
            |
            |✅ State analysis complete
            |
            |$statesAnalysis
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Update overview
            overviewContent?.clear()
            overviewContent = overviewTask.add(
                    """
            |## Finite State Machine Analysis
            |
            |**Concept:** $conceptToModel
            |
            |**Domain:** $domainContext
            |
            |**Status:** 🔄 Identifying transitions and events...
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Step 2: Identify Transitions
            log.info("Step 2: Identifying state transitions and events")
            val transitionsTask = tabs.newTask("Transitions")

            val transitionsLoading = transitionsTask.add(
              "## Transition Analysis\n\n🔄 Identifying events and state transitions...".renderMarkdown()
            )
            task.update()

            val transitionPrompt = buildTransitionPrompt(
                statesAnalysis,
                knownEvents
            )

            log.debug("Requesting transition analysis from LLM")
            val transitionsAnalysis = stateAgent.answer(listOf(transitionPrompt))
          transcript?.write("## Step 2: Transition Analysis\n".toByteArray())
          transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$transitionPrompt\n```\n\n### Response\n\n$transitionsAnalysis\n</details>\n\n".toByteArray())
            fullReport.append("## 2. Transition Analysis\n\n$transitionsAnalysis\n\n")

            transitionsLoading?.clear()
            transitionsTask.add(
                    """
            |## State Transitions
            |
            |✅ Transition analysis complete
            |
            |$transitionsAnalysis
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Step 3: Generate State Diagram
            log.info("Step 3: Generating state diagram")
            val diagramTask = tabs.newTask("State Diagram")

            val diagramLoading = diagramTask.add(
              "## State Diagram\n\n🔄 Generating visual representation...".renderMarkdown()
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
          transcript?.write("## Step 3: State Diagram\n".toByteArray())
          transcript?.write("<details><summary>Mermaid Source</summary>\n\n".toByteArray())
            if (mermaidCode.isNotEmpty()) {
              transcript?.write("```mermaid\n$mermaidCode\n```\n".toByteArray())
            } else {
              transcript?.write("⚠️ Failed to generate diagram\n".toByteArray())
            }
          transcript?.write("</details>\n\n".toByteArray())
            if (mermaidCode.isNotEmpty()) {
                fullReport.append("## 3. State Diagram\n\n```mermaid\n$mermaidCode\n```\n\n")
            } else {
                fullReport.append("## 3. State Diagram\n\nFailed to generate diagram.\n\n")
            }

            diagramLoading?.clear()
            if (mermaidCode.isNotEmpty()) {
                diagramTask.add(
                        """
              |## State Diagram
              |
              |✅ Diagram generated successfully
              |
              |```mermaid
              |$mermaidCode
              |```
            """.trimMargin().renderMarkdown()
                )
            } else {
                diagramTask.add(
                        """
              |## State Diagram
              |
              |⚠️ Failed to generate diagram
              |
              |Raw output:
              |```
              |$diagramResult
              |```
            """.trimMargin().renderMarkdown()
                )
            }
            task.update()

            // Step 4: Edge Cases (if enabled)
            var edgeCasesAnalysis: String
            if (executionConfig.identify_edge_cases) {
                log.info("Step 4: Identifying edge cases and error states")
                val edgeCasesTask = tabs.newTask("Edge Cases")

                val edgeCasesLoading = edgeCasesTask.add(
                  "## Edge Cases Analysis\n\n🔄 Identifying edge cases and error states...".renderMarkdown()
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
              transcript?.write("## Step 4: Edge Cases\n".toByteArray())
              transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$edgeCasesPrompt\n```\n\n### Response\n\n$edgeCasesAnalysis\n</details>\n\n".toByteArray())
                fullReport.append("## 4. Edge Cases Analysis\n\n$edgeCasesAnalysis\n\n")

                edgeCasesLoading?.clear()
                edgeCasesTask.add(
                        """
              |## Edge Cases and Error States
              |
              |✅ Edge case analysis complete
              |
              |$edgeCasesAnalysis
            """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 5: Validation (if enabled)
            var validationAnalysis = ""
            if (executionConfig.validate_properties) {
                log.info("Step 5: Validating FSM properties")
                val validationTask = tabs.newTask("Validation")

                val validationLoading = validationTask.add(
                  "## FSM Validation\n\n🔄 Validating state machine properties...".renderMarkdown()
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
              transcript?.write("## Step 5: Validation\n".toByteArray())
              transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$validationPrompt\n```\n\n### Response\n\n$validationAnalysis\n</details>\n\n".toByteArray())
                fullReport.append("## 5. Property Validation\n\n$validationAnalysis\n\n")

                validationLoading?.clear()
                validationTask.add(
                        """
              |## FSM Property Validation
              |
              |✅ Validation complete
              |
              |$validationAnalysis
            """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 6: Test Scenarios (if enabled)
            var testScenariosAnalysis: String
            if (executionConfig.generate_test_scenarios) {
                log.info("Step 6: Generating test scenarios")
                val testScenariosTask = tabs.newTask("Test Scenarios")

                val testScenariosLoading = testScenariosTask.add(
                  "## Test Scenario Generation\n\n🔄 Creating test scenarios...".renderMarkdown()
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
              transcript?.write("## Step 6: Test Scenarios\n".toByteArray())
              transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$testScenariosPrompt\n```\n\n### Response\n\n$testScenariosAnalysis\n</details>\n\n".toByteArray())
                fullReport.append("## 6. Test Scenarios\n\n$testScenariosAnalysis\n\n")

                testScenariosLoading?.clear()
                testScenariosTask.add(
                        """
              |## Test Scenarios
              |
              |✅ Test scenarios generated
              |
              |$testScenariosAnalysis
            """.trimMargin().renderMarkdown()
                )
                task.update()
            }

            // Step 7: Generate Summary
            log.info("Step 7: Generating comprehensive summary")
            val summaryTask = tabs.newTask("Summary")

            val summaryLoading = summaryTask.add(
              "## Summary\n\n🔄 Generating comprehensive summary...".renderMarkdown()
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
          transcript?.write("## Step 7: Summary\n".toByteArray())
          transcript?.write("<details><summary>Prompt & Response</summary>\n\n### Prompt\n\n```\n$summaryPrompt\n```\n\n### Response\n\n$summaryAnalysis\n</details>\n\n".toByteArray())
            fullReport.append("## 7. Summary\n\n$summaryAnalysis\n\n")

            summaryLoading?.clear()
            summaryTask.add(
                    """
            |## Analysis Summary
            |
            |✅ Summary complete
            |
            |$summaryAnalysis
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Final overview update
            overviewContent?.clear()
            val totalTime = System.currentTimeMillis() - startTime
            overviewTask.add(
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
          """.trimMargin().renderMarkdown()
            )
            task.update()

            // Build concise result for task dependencies
            val conciseResult = buildString {
                appendLine("# FSM Analysis: $conceptToModel")
                appendLine()
                if (summaryAnalysis.isNotBlank()) {
                    appendLine("## Summary")
                    appendLine(summaryAnalysis.smartTruncate(maxDescriptionLength))
                    appendLine()
                } else {
                    appendLine("## Summary")
                    appendLine("Analysis completed successfully.")
                    appendLine()
                }
                appendLine("## Key Components")
                appendLine("- States identified and analyzed")
                appendLine("- Transitions mapped")
                appendLine("- State diagram generated")
                if (executionConfig.identify_edge_cases) appendLine("- Edge cases identified")
                if (executionConfig.validate_properties) appendLine("- Properties validated")
                if (executionConfig.generate_test_scenarios) appendLine("- Test scenarios generated")
            }

            log.info("FiniteStateMachineTask completed: concept='$conceptToModel', duration=${totalTime}ms, output_size=${conciseResult.length} chars")

          transcript?.write("## Completion\n\n**Status:** ✅ Analysis complete\n".toByteArray())
          transcript?.write("**Duration:** ${totalTime / 1000.0}s\n".toByteArray())

          val (link, file) = task.createFile("fsm_analysis.md")
            file?.writeText(fullReport.toString())

            var mmdLink = ""
            if (mermaidCode.isNotEmpty()) {
                val (l, f) = task.createFile("fsm_diagram.mmd")
                f?.writeText(mermaidCode)
                mmdLink = l
            }

          task.complete(
                "FSM analysis completed for: $conceptToModel. " +
                        "Full analysis written to <a href='$link' target='_blank'>$link</a> " +
                    (if (mmdLink.isNotEmpty()) " <a href='$mmdLink' target='_blank'>Mermaid Diagram</a>" else "")
            )
            resultFn(conciseResult)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("FiniteStateMachineTask failed after ${duration}ms for concept: $conceptToModel", e)
          transcript?.write("## Error\n\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
            task.error(e)
            resultFn("ERROR: FSM analysis failed - ${e.message}")
        } finally {
          transcript?.close()
        }
        }
    }

    private fun buildStateIdentificationPrompt(
        concept: String,
        domain: String,
        initialStates: List<String>,
        priorContext: String,
        inputFileContent: String
    ): String {
        val fileContentSection = if (inputFileContent.isNotBlank()) {
            """
        |
        |## Reference Files:
        |$inputFileContent
      """.trimMargin()
        } else {
            ""
        }
        val initialStatesSection = if (initialStates.isNotEmpty()) {
            """
$fileContentSection
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

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
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
                val content = if (!isTextFile(file)) {
                    extractDocumentContent(file)
                } else {
                    codeFiles[file.toPath()] ?: file.readText()
                }
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    private fun isTextFile(file: File): Boolean {
        val textExtensions = setOf(
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp",
            "h", "hpp", "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }

    private fun extractDocumentContent(file: File) = try {
        file.getDocumentReader().use { reader ->
            when (reader) {
                is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                else -> reader.getText()
            }
        }
    } catch (e: Exception) {
        log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
        try {
            file.readText()
        } catch (e2: Exception) {
            "Error reading file: ${e2.message}"
        }
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
    private fun String.smartTruncate(maxLength: Int): String {
        if (length <= maxLength) return this
        val truncated = take(maxLength)
        val lastNewline = truncated.lastIndexOf('\n')
        return if (lastNewline > maxLength / 2) {
            truncated.substring(0, lastNewline) + "\n...(truncated)"
        } else {
            truncated + "..."
        }
    }




    companion object {
        private val log: Logger = LoggerFactory.getLogger(FiniteStateMachineTask::class.java)

        val FiniteStateMachine = TaskType(
          name = "FiniteStateMachine",
          category = "Reasoning",
          taskClass = FiniteStateMachineTask::class.java,
          executionConfigClass = FiniteStateMachineTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Model concepts using finite state machine analysis",
          tooltipHtml = """
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
                """,
        )
    }
}