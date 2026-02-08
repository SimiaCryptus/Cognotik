package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ConstraintSatisfactionTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ConstraintSatisfactionTaskExecutionConfigData?
) : AbstractTask<ConstraintSatisfactionTask.ConstraintSatisfactionTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class ConstraintSatisfactionTaskExecutionConfigData(
      @Description("The problem requiring constraint satisfaction. Be specific about the goals and constraints.")
      var problem_description: String? = null,
      @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task.")
      var input_files: List<String>? = null,
      @Description("Hard constraints that must be satisfied (cannot be violated).")
      var hard_constraints: List<String>? = emptyList(),
      @Description("Soft constraints to optimize with their relative weights (0.0-1.0).")
      var soft_constraints: Map<String, Double>? = emptyMap(),
      @Description("Search strategy: 'backtracking' (systematic), 'forward' (greedy), 'local' (hill-climbing).")
      var search_strategy: String = "backtracking",
      @Description("Maximum search iterations before returning best solution found.")
      var max_iterations: Int = 100,
      @Description("Additional files for context.")
      var related_files: List<String>? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ConstraintSatisfaction.name,
        task_description = problem_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {

        override fun validate(): String? {
            // Validate problem description
            if (problem_description.isNullOrBlank()) {
                return "problem_description cannot be null or blank"
            }

            // Validate search strategy
            val validStrategies = setOf("backtracking", "forward", "local")
            if (search_strategy !in validStrategies) {
                return "search_strategy must be one of: ${validStrategies.joinToString(", ")}"
            }

            // Validate max iterations
            if (max_iterations <= 0) {
                return "max_iterations must be greater than 0"
            }

            // Validate soft constraint weights
            soft_constraints?.forEach { (constraint, weight) ->
                if (weight < 0.0 || weight > 1.0) {
                    return "soft constraint '$constraint' has invalid weight $weight (must be between 0.0 and 1.0)"
                }
            }
            // Validate input files if provided
            input_files?.forEach { pattern ->
                if (pattern.isBlank()) {
                    return "input_files patterns cannot be blank"
                }
            }

            // Call parent validation
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
        ### ConstraintSatisfaction
        Solves complex problems with multiple competing constraints using various search strategies.
        - **Use when**: You need to balance hard requirements with weighted soft preferences (e.g., architecture, scheduling).
        - **Inputs**: Problem description, hard/soft constraints, and optional file context.
        - **Strategies**: 
            - `backtracking`: Systematic search for guaranteed satisfaction.
            - `forward`: Greedy approach for speed.
            - `local`: Hill-climbing for optimization in large search spaces.
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {

      val transcript = task.newFileOutputStream(transcriptFile())
      try {
        // Validate configuration before execution
        executionConfig?.validate()?.let { error ->
          val msg = "Configuration validation failed: $error"
          log.error(msg)
          task.error(Exception(msg))
          resultFn("CONFIGURATION ERROR: $error")
          return
        }


        val startTime = System.currentTimeMillis()
        val problemDescription = executionConfig?.problem_description ?: ""
        val hardConstraints = executionConfig?.hard_constraints ?: emptyList()
        val softConstraints = executionConfig?.soft_constraints ?: emptyMap()
        val searchStrategy = executionConfig?.search_strategy ?: "backtracking"
        val maxIterations = executionConfig?.max_iterations ?: 100

        log.info("Starting Constraint Satisfaction Task for problem: ${problemDescription.take(50)}...")

        transcript?.let { stream ->
          writeTranscriptHeader(
            stream,
            problemDescription,
            hardConstraints,
            softConstraints,
            searchStrategy,
            maxIterations
          )
        }

        task.ui.pool.submit {
          try {
            val tabbedDisplay = TabbedDisplay(task)
            tabbedDisplay.newTask("Problem Overview").add(
              """
                        |## Constraint Satisfaction Problem
                        |
                        |**Problem**: ${problemDescription.truncateForDisplay()}
                        |
                        |**Hard Constraints** (${hardConstraints.size}):
                        |${hardConstraints.joinToString("\n") { "- $it" }}
                        |
                        |**Soft Constraints** (${softConstraints.size}):
                        |${softConstraints.entries.joinToString("\n") { "- ${it.key} (weight: ${it.value})" }}
                        |
                        |**Strategy**: $searchStrategy (max iterations: $maxIterations)
                        """.trimMargin().renderMarkdown()
            )

            // Step 2: Gather Context
            val contextTask = tabbedDisplay.newTask("Context")
            contextTask.add("### Gathering context from previous tasks...".renderMarkdown())

            val priorCode = getPriorCode(agent.executionState)
            val inputFileContent =
              super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)

            val prompt = buildPrompt(
              problemDescription, hardConstraints, softConstraints,
              searchStrategy, maxIterations, priorCode, inputFileContent
            )

            transcript?.write(
              """
                        |<details>
                        |<summary>Generated Prompt</summary>
                        |
                        |```markdown
                        |$prompt
                        |```
                        |</details>
                        |
                    """.trimMargin().toByteArray()
            )

            contextTask.add(
              """
                        |### Context Gathered
                        |✅ Previous task results collected
                        |✅ Prompt constructed
                        """.trimMargin().renderMarkdown()
            )

            // Step 3: Generate Solution
            val solutionGenerationTask = tabbedDisplay.newTask("Solution Generation")
            solutionGenerationTask.add("### Generating solution...".renderMarkdown())

            val api = defaultSmart ?: throw IllegalStateException("No smart model configured")
            val chatAgent = ChatAgent(prompt = prompt, model = api)
            val answer = chatAgent.answer(listOf(""))

            transcript?.write(
              """
                        |<details>
                        |<summary>Raw LLM Response</summary>
                        |
                        |```markdown
                        |$answer
                        |```
                        |</details>
                        |
                    """.trimMargin().toByteArray()
            )

            solutionGenerationTask.add("### Solution Generated ✅".renderMarkdown())

            // Step 4: Display Solution
            tabbedDisplay.newTask("Final Solution").add(
              """
                        |## Solution
                        |
                        |${answer.truncateForDisplay()}
                        """.trimMargin().renderMarkdown()
            )

            val duration = System.currentTimeMillis() - startTime
            log.info("Constraint Satisfaction Task completed in ${duration}ms")
            transcript?.write("\n\n---\n**Completed in ${duration}ms**\n".toByteArray())

            if (orchestrationConfig.autoFix) {
              finalizeTask(task, answer, resultFn)
            } else {
              val footer = acceptButtonFooter(task.ui) {
                finalizeTask(task, answer, resultFn)
              }
              task.add(footer.renderMarkdown())
            }
          } catch (e: Exception) {
            handleError(e, task, transcript, resultFn)
          } finally {
            transcript?.close()
          }
        }
      } catch (e: Exception) {
        handleError(e, task, transcript, resultFn)
        transcript?.close()
      }
    }

  private fun finalizeTask(task: SessionTask, answer: String, resultFn: (String) -> Unit) {
    try {
      val (link, _) = task.createFile("constraint_solution_transcript.md")
      val summaryMessage = """
                |Constraint satisfaction solution finalized.
                |View detailed transcript: [markdown]($link) | [html](${link.removeSuffix(".md")}.html) | [pdf](${
        link.removeSuffix(
          ".md"
        )
      }.pdf)
            """.trimMargin().renderMarkdown()
      task.complete(summaryMessage)
      resultFn(answer)
    } catch (e: Exception) {
      log.error("Error finalizing task", e)
      task.error(e)
      resultFn("ERROR: Finalization failed - ${e.message}")
    }
  }

  private fun handleError(e: Exception, task: SessionTask, transcript: FileOutputStream?, resultFn: (String) -> Unit) {
    log.error("Error in Constraint Satisfaction Task: ${e.message}", e)
    task.error(e)
    transcript?.write(
      """
            |
            |## ❌ Error
            |<details>
            |<summary>Stack Trace</summary>
            |
            |```
            |${e.stackTraceToString()}
            |```
            |</details>
        """.trimMargin().toByteArray()
    )
    resultFn("ERROR: Failed to generate constraint satisfaction solution - ${e.message}")
    }

    private fun writeTranscriptHeader(
        stream: FileOutputStream,
        problemDescription: String,
        hardConstraints: List<String>,
        softConstraints: Map<String, Double>,
        searchStrategy: String,
        maxIterations: Int
    ) {
        try {
            val header = buildString {
                appendLine("# Constraint Satisfaction Task Transcript")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("**Problem:** $problemDescription")
                appendLine("**Hard Constraints:** ${hardConstraints.size}")
                appendLine("**Soft Constraints:** ${softConstraints.size}")
                appendLine("**Search Strategy:** $searchStrategy")
                appendLine("**Max Iterations:** $maxIterations")
                appendLine()
                appendLine("---")
                appendLine()
            }
            stream.write(header.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
        } catch (e: Exception) {
            log.error("Failed to write transcript header", e)
        }
    }


    private fun buildPrompt(
        problemDescription: String,
        hardConstraints: List<String>,
        softConstraints: Map<String, Double>,
        searchStrategy: String,
        maxIterations: Int,
        priorCode: String,
        inputFileContent: String
    ): String {
        return """
 You are an expert problem solver specializing in constraint satisfaction problems (CSP).

 ## Problem Description:
 $problemDescription
## Input Files Context:
${if (inputFileContent.isNotBlank()) inputFileContent else "No input files provided"}


 ## Hard Constraints (MUST be satisfied):
 ${hardConstraints.mapIndexed { i, c -> "${i + 1}. $c" }.joinToString("\n")}

## Soft Constraints (optimize with given weights):
${
            softConstraints.entries.mapIndexed { i, (constraint, weight) ->
                "${i + 1}. $constraint (weight: $weight)"
            }.joinToString("\n")
        }

## Search Strategy:
$searchStrategy

## Maximum Iterations:
$maxIterations

## Previous Task Results:
$priorCode

## Instructions:
1. Analyze the problem and identify the decision variables
2. Formulate the constraint satisfaction problem clearly
3. Apply the specified search strategy ($searchStrategy):
   - For 'backtracking': Use systematic search with intelligent backtracking
   - For 'forward': Use greedy forward search with constraint propagation
   - For 'local': Use local search/hill-climbing with random restarts
4. Ensure ALL hard constraints are satisfied (non-negotiable)
5. Optimize soft constraints according to their weights
6. If no perfect solution exists, find the best compromise
7. Provide reasoning for the solution and trade-offs made
8. Include a satisfaction score for each soft constraint
9. Suggest alternatives if multiple good solutions exist

## Output Format:
Provide your solution in the following structure:

### Solution Overview
[Brief description of the proposed solution]

### Decision Variables
[List the key decisions made]

### Hard Constraint Satisfaction
[Verify each hard constraint is satisfied]

### Soft Constraint Optimization
[Score each soft constraint and explain trade-offs]

### Overall Score
[Weighted sum of soft constraint satisfaction]

### Reasoning
[Explain why this solution is optimal or near-optimal]

### Alternative Solutions
[If applicable, mention other viable options]

Generate the constraint satisfaction solution now:
        """.trimIndent()
    }
    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept and Save Solution") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(ConstraintSatisfactionTask::class.java)
        @JvmStatic val ConstraintSatisfaction = TaskType(
          name = "ConstraintSatisfaction",
          category = "Reasoning",
          taskClass = ConstraintSatisfactionTask::class.java,
          executionConfigClass = ConstraintSatisfactionTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Solve problems with multiple competing constraints",
          tooltipHtml = """
                        Solves constraint satisfaction problems with hard and soft constraints.
                        <ul>
                          <li>Handles hard constraints that must be satisfied</li>
                          <li>Optimizes soft constraints with configurable weights</li>
                          <li>Supports multiple search strategies (backtracking, forward, local)</li>
                          <li>Provides detailed reasoning and trade-off analysis</li>
                          <li>Suggests alternative solutions when applicable</li>
                          <li>Useful for architectural decisions, resource allocation, and optimization</li>
                        </ul>
                      """,
        )
    }
}