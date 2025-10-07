package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

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
    )

    override fun promptSegment(): String {
        return """
ConstraintSatisfaction - Solve problems with multiple competing constraints
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
        val problemDescription = executionConfig?.problem_description
        if (problemDescription.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No problem description provided")
            return
        }

        val hardConstraints = executionConfig.hard_constraints ?: emptyList()
        val softConstraints = executionConfig.soft_constraints ?: emptyMap()
        val searchStrategy = executionConfig.search_strategy
        val maxIterations = executionConfig.max_iterations

        val newTask = task.ui.newTask(false)
        val toInput = { it: String -> listOf(it) }
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |## Constraint Satisfaction Problem
                |
                |**Problem**: $problemDescription
                |
                |**Hard Constraints** (${hardConstraints.size}):
                |${hardConstraints.joinToString("\n") { "- $it" }}
                |
                |**Soft Constraints** (${softConstraints.size}):
                |${softConstraints.entries.joinToString("\n") { "- ${it.key} (weight: ${it.value})" }}
                |
                |**Strategy**: $searchStrategy (max iterations: $maxIterations)
                """.trimMargin(),
                ui = ui
            )
        )

        val priorCode = getPriorCode(agent.executionState!!)

        val prompt = buildPrompt(
            problemDescription,
            hardConstraints,
            softConstraints,
            searchStrategy,
            maxIterations,
            priorCode
        )

        val chatAgent = ChatAgent(
            prompt = promptSegment(),
            model = api,
        )

        var answer: String? = null
        Retryable(newTask, newTask.ui) { sb ->
            answer = chatAgent.answer(toInput(prompt))
            sb.append(answer ?: "")
            sb.toString()
        }
        val solution = answer

        newTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |## Solution
                |
                |$solution
                """.trimMargin(),
                ui = ui
            )
        )

        if (orchestrationConfig.autoFix) {
            newTask.complete("Constraint satisfaction solution generated")
            resultFn(answer ?: "No solution generated")
        } else {
            newTask.add(
                MarkdownUtil.renderMarkdown(
                    acceptButtonFooter(ui) {
                        try {
                            newTask.complete("Constraint satisfaction solution accepted")
                            resultFn(answer ?: "No solution generated")
                        } catch (e: Exception) {
                            log.error("Error accepting solution", e)
                            newTask.error(e)
                            resultFn("ERROR: ${e.message}")
                        }
                    },
                    ui = ui
                )
            )
        }
    }

    private fun buildPrompt(
        problemDescription: String,
        hardConstraints: List<String>,
        softConstraints: Map<String, Double>,
        searchStrategy: String,
        maxIterations: Int,
        priorCode: String
    ): String {
        return """
You are an expert problem solver specializing in constraint satisfaction problems (CSP).

## Problem Description:
$problemDescription

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