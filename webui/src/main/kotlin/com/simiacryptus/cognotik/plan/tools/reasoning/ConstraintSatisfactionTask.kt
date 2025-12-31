package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
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
        @Description("The problem requiring constraint satisfaction")
        val problem_description: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        @Description("Hard constraints that must be satisfied (cannot be violated)")
        val hard_constraints: List<String>? = null,
        @Description("Soft constraints to optimize with their relative weights (0.0-1.0)")
        val soft_constraints: Map<String, Double>? = null,
        @Description("Search strategy: 'backtracking' (systematic), 'forward' (greedy), 'local' (hill-climbing)")
        val search_strategy: String = "backtracking",
        @Description("Maximum search iterations before returning best solution found")
        val max_iterations: Int = 100,
        @Description("Additional files for context")
        val related_files: List<String>? = null,
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
 ConstraintSatisfaction - Solve problems with multiple competing constraints
  ** Optionally, list input files (supports glob patterns) to be examined when solving the problem
  ** Specify the problem description clearly
  ** Define hard constraints that MUST be satisfied (non-negotiable requirements)
  ** Define soft constraints with weights (0.0-1.0) representing their relative importance
  ** Choose a search strategy:
     - 'backtracking': Systematic search with backtracking (thorough but slower)
     - 'forward': Greedy forward search (faster but may miss optimal solutions)
     - 'local': Local search/hill-climbing (good for optimization problems)
  ** Set max_iterations to control search depth vs. time tradeoff
  ** Use cases include:
     - Architectural decisions balancing performance, maintainability, and cost
     - Resource allocation with competing priorities
     - Configuration optimization with multiple objectives
     - Design trade-off analysis
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        // Validate configuration before execution
        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }

        val startTime = System.currentTimeMillis()
        var transcriptStream: FileOutputStream? = null
        try {
            val problemDescription = executionConfig?.problem_description
            if (problemDescription.isNullOrBlank()) {
                log.error("No problem description provided")
                task.safeComplete("CONFIGURATION ERROR: No problem description provided", log)
                resultFn("CONFIGURATION ERROR: No problem description provided")
                return
            }

            val hardConstraints = executionConfig.hard_constraints ?: emptyList()
            val softConstraints = executionConfig.soft_constraints ?: emptyMap()
            val searchStrategy = executionConfig.search_strategy
            val maxIterations = executionConfig.max_iterations
            // Initialize transcript
            transcriptStream = task.transcript()
            transcriptStream?.let { stream ->
                writeTranscriptHeader(
                    stream,
                    problemDescription,
                    hardConstraints,
                    softConstraints,
                    searchStrategy,
                    maxIterations
                )
            }


            val toInput = { it: String -> listOf(it) }
            val api = defaultSmart ?: return
            log.info(
                """
        |Starting Constraint Satisfaction Task:
        |  Problem: $problemDescription
        |  Hard Constraints: ${hardConstraints.size}
        |  Soft Constraints: ${softConstraints.size}
        |  Strategy: $searchStrategy
        |  Max Iterations: $maxIterations
        """.trimMargin()
            )
            val tabbedDisplay = TabbedDisplay(task)
            tabbedDisplay["Problem Overview"] = MarkdownUtil.renderMarkdown(
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
                """.trimMargin(),
                ui = task.ui
            )
            transcriptStream?.write(
                """
          |## Constraint Satisfaction Problem
          |
          |**Problem**: $problemDescription
          |
          |**Hard Constraints** (${hardConstraints.size}):
          |${hardConstraints.joinToString("\n") { "- $it" }}
          |
        """.trimMargin().toByteArray()
            )
            // Step 2: Gather Context
            tabbedDisplay["Context"] = MarkdownUtil.renderMarkdown(
                "### Gathering context from previous tasks...",
                ui = task.ui
            )
            transcriptStream?.write(
                """
          |
          |### Gathering Context
        """.trimMargin().toByteArray()
            )


            val priorCode = getPriorCode(agent.executionState)
            val inputFileContent =
                super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)

            val prompt = buildPrompt(
                problemDescription,
                hardConstraints,
                softConstraints,
                searchStrategy,
                maxIterations,
                priorCode,
                inputFileContent
            )
            tabbedDisplay["Context"] = MarkdownUtil.renderMarkdown(
                """
            |### Context Gathered
            |✅ Previous task results collected
            |✅ Prompt constructed
            """.trimMargin(),
                ui = task.ui
            )
            transcriptStream?.write(
                """
          |
          |### Context Gathered
        """.trimMargin().toByteArray()
            )
            // Step 3: Generate Solution
            tabbedDisplay["Solution Generation"] = MarkdownUtil.renderMarkdown(
                "### Generating constraint satisfaction solution...\n\nThis may take a moment.",
                ui = task.ui
            )
            transcriptStream?.write(
                """
          |
          |### Generating Solution
        """.trimMargin().toByteArray()
            )


            val chatAgent = ChatAgent(
                prompt = prompt,
                model = api,
            )

            var answer: String? = chatAgent.answer(toInput(""))
            tabbedDisplay["Solution Generation"] = MarkdownUtil.renderMarkdown(
                """
            |### Solution Generated
            |✅ Complete
            """.trimMargin(),
                ui = task.ui
            )
            transcriptStream?.write(
                """
          |
          |### Solution Generated
        """.trimMargin().toByteArray()
            )
            // Step 4: Display Solution

            val solution = answer
            tabbedDisplay["Final Solution"] = MarkdownUtil.renderMarkdown(
                """
                |## Solution
                |
                |${solution?.truncateForDisplay() ?: "No solution generated."}
                """.trimMargin(),
                ui = task.ui
            )
            transcriptStream?.write(
                """
          |
          |## Final Solution
          |
          |${solution ?: "No solution generated."}
        """.trimMargin().toByteArray()
            )
            val duration = System.currentTimeMillis() - startTime
            log.info("Constraint Satisfaction Task completed in ${duration}ms")
            transcriptStream?.write("\n\n---\n**Completed in ${duration}ms**\n".toByteArray())


            if (orchestrationConfig.autoFix) {
                val (link, _) = task.createFile("constraint_solution_transcript.md")
                val summaryMessage = "Constraint satisfaction solution generated. " +
                        "View detailed transcript: <a href='$link' target='_blank'>markdown</a> " +
                        "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                        "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
                task.safeComplete(summaryMessage, log)
                resultFn(answer ?: "No solution generated")
            } else {
                task.add(
                    MarkdownUtil.renderMarkdown(
                        acceptButtonFooter(task.ui) {
                            try {
                                val (link, _) = task.createFile("constraint_solution_transcript.md")
                                val summaryMessage = "Constraint satisfaction solution accepted. " +
                                        "View detailed transcript: <a href='$link' target='_blank'>markdown</a> " +
                                        "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                                        "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
                                task.complete(summaryMessage)
                                resultFn(answer ?: "No solution generated")
                            } catch (e: Exception) {
                                log.error("Error accepting constraint satisfaction solution", e)
                                task.error(e)
                                resultFn("ERROR: ${e.message}")
                            }
                        },
                        ui = task.ui
                    )
                )
            }
        } catch (e: Exception) {
            transcriptStream?.write("\n\n## ❌ Error\n\n${e.message}\n".toByteArray())
            log.error("Error in Constraint Satisfaction Task", e)
            task.error(e)
            task.add(
                MarkdownUtil.renderMarkdown(
                    """
          |## ❌ Error
          |
          |An error occurred while solving the constraint satisfaction problem:
          |```
          |${e.message}
          |```
          """.trimMargin(),
                    ui = task.ui
                )
            )
            resultFn("ERROR: Failed to generate constraint satisfaction solution - ${e.message}")
        } finally {
            transcriptStream?.flush()
            transcriptStream?.close()
        }
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

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ConstraintSatisfactionTask::class.java)
        val ConstraintSatisfaction = TaskType(
            "ConstraintSatisfaction",
            "Reasoning",
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
    }
}