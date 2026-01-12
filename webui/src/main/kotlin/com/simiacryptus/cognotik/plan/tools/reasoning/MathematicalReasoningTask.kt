package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MathematicalReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: MathematicalReasoningTaskExecutionConfigData?
) : AbstractTask<MathematicalReasoningTask.MathematicalReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(MathematicalReasoningTask::class.java)
        val MathematicalReasoning = TaskType(
          name = "MathematicalReasoning",
          category = "Reasoning",
          taskClass = MathematicalReasoningTask::class.java,
          executionConfigClass = MathematicalReasoningTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Solve mathematical problems through step-by-step logical reasoning with verifiable steps",
          tooltipHtml = """
                          Uses path search to solve mathematical problems through rigorous step-by-step reasoning.
                          <ul>
                              <li>Breaks down complex problems into verifiable atomic steps</li>
                              <li>Each step includes justification and verification</li>
                              <li>Explores multiple solution paths when needed</li>
                              <li>Backtracks when encountering dead ends</li>
                              <li>Provides detailed proof trail with MathJax notation</li>
                              <li>Supports algebra, calculus, number theory, and more</li>
                              <li>Validates intermediate results for correctness</li>
                              <li>Generates human-readable mathematical proofs</li>
                          </ul>
                      """,
        )
    }

    class MathematicalReasoningTaskExecutionConfigData(
        @Description("The mathematical problem or theorem to solve/prove")
        val problem_statement: String? = null,
        @Description("The goal or target result (e.g., 'prove equality', 'find x', 'simplify expression')")
        val goal: String? = null,
        @Description("Known facts, axioms, or given information")
        val given_information: List<String>? = null,
        @Description("Mathematical domain (e.g., 'algebra', 'calculus', 'number_theory', 'geometry', 'linear_algebra')")
        val domain: String? = "general",
        @Description("Maximum depth of reasoning steps (default: 20)")
        val max_depth: Int = 20,
        @Description("Maximum number of alternative paths to explore (default: 3)")
        val max_alternatives: Int = 3,
        @Description("Whether to show all explored paths or just the successful one")
        val show_all_paths: Boolean = false,
        @Description("Level of detail in explanations ('brief', 'standard', 'detailed')")
        val detail_level: String = "standard",

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = MathematicalReasoning.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (problem_statement.isNullOrBlank()) {
                return "problem_statement must not be blank"
            }
            if (max_depth < 1 || max_depth > 100) {
                return "max_depth must be between 1 and 100"
            }
            if (max_alternatives < 1 || max_alternatives > 10) {
                return "max_alternatives must be between 1 and 10"
            }
            if (detail_level !in listOf("brief", "standard", "detailed")) {
                return "detail_level must be 'brief', 'standard', or 'detailed'"
            }
            return ValidatedObject.Companion.validateFields(this)
        }
    }

    data class ReasoningStep(
        @Description("Unique identifier for this step")
        val step_id: String = "",
        @Description("The mathematical statement or transformation at this step")
        val statement: String = "",
        @Description("LaTeX/MathJax representation of the statement")
        val latex: String = "",
        @Description("Justification for this step (theorem, axiom, or rule applied)")
        val justification: String = "",
        @Description("Type of step: 'axiom', 'definition', 'theorem', 'algebraic', 'substitution', 'simplification', 'inference'")
        val step_type: String = "",
        @Description("Confidence in this step's correctness (0-100)")
        val confidence: Int = 100,
        @Description("Whether this step has been verified")
        val verified: Boolean = false,
        @Description("Any notes or caveats about this step")
        val notes: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (statement.isBlank()) return "statement must not be blank"
            if (confidence < 0 || confidence > 100) return "confidence must be between 0 and 100"
            return null
        }
    }

    data class ReasoningPath(
        @Description("Sequence of reasoning steps")
        val steps: List<ReasoningStep> = emptyList(),
        @Description("Whether this path reached the goal")
        val reached_goal: Boolean = false,
        @Description("If not reached, reason for stopping")
        val termination_reason: String = "",
        @Description("Overall confidence in this path")
        val path_confidence: Int = 0
    )

    data class NextStepOptions(
        @Description("List of possible next steps to explore")
        val options: List<ReasoningStep> = emptyList(),
        @Description("Recommended option index (0-based)")
        val recommended_index: Int = 0,
        @Description("Reasoning for the recommendation")
        val recommendation_reason: String = ""
    )

    data class StepVerification(
        @Description("Whether the step is mathematically valid")
        val is_valid: Boolean = false,
        @Description("Explanation of the verification")
        val explanation: String = "",
        @Description("Any errors or issues found")
        val errors: List<String> = emptyList(),
        @Description("Suggestions for correction if invalid")
        val suggestions: List<String> = emptyList()
    )

    data class GoalCheck(
        @Description("Whether the current state matches the goal")
        val goal_reached: Boolean = false,
        @Description("How close we are to the goal (0-100)")
        val progress: Int = 0,
        @Description("What remains to be done")
        val remaining_work: String = "",
        @Description("Explanation of the assessment")
        val explanation: String = ""
    )

    override fun promptSegment(): String {
        return """
MathematicalReasoning - Solve mathematical problems through step-by-step logical reasoning
  ** Specify the problem statement clearly
  ** Define the goal (prove, solve, simplify, etc.)
  ** Provide any given information or constraints
  ** Specify the mathematical domain if relevant
  ** Configure search parameters (depth, alternatives)
  ** The task will:
     - Break down the problem into atomic steps
     - Verify each step's mathematical validity
     - Explore alternative solution paths
     - Backtrack from dead ends
     - Generate a complete proof trail
     - Output results in MathJax/LaTeX format
  ** Useful for:
     - Solving algebraic equations
     - Proving mathematical theorems
     - Simplifying complex expressions
     - Step-by-step calculus problems
     - Number theory proofs
     - Geometric proofs
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
        try {
            val startTime = System.currentTimeMillis()
            log.info("Starting MathematicalReasoningTask with problem: ${executionConfig?.problem_statement?.take(100)}")

            // Validate configuration
            executionConfig?.validate()?.let { errorMessage ->
                log.error("Configuration validation failed: $errorMessage")
                task.safeComplete("VALIDATION ERROR: $errorMessage", log)
                task.error(ValidatedObject.ValidationError(errorMessage, executionConfig))
                transcript?.close()
                resultFn("VALIDATION ERROR: $errorMessage")
                return
            }

            val problemStatement = executionConfig?.problem_statement ?: ""
            val goal = executionConfig?.goal ?: "solve"
            val givenInfo = executionConfig?.given_information ?: emptyList()
            val domain = executionConfig?.domain ?: "general"
            val maxDepth = executionConfig?.max_depth ?: 20
            val maxAlternatives = executionConfig?.max_alternatives ?: 3
            val showAllPaths = executionConfig?.show_all_paths ?: false
            val detailLevel = executionConfig?.detail_level ?: "standard"

            val tabs = TabbedDisplay(task)
            val api = defaultSmart

            // Create overview tab
            val overviewTask = tabs.newTask("Overview")
            val overviewContent = buildString {
                appendLine("# Mathematical Reasoning Task")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("## Problem Statement")
                appendLine()
                appendLine(problemStatement)
                appendLine()
                appendLine("## Goal")
                appendLine()
                appendLine(goal)
                appendLine()
                if (givenInfo.isNotEmpty()) {
                    appendLine("## Given Information")
                    appendLine()
                    givenInfo.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine("## Configuration")
                appendLine()
                appendLine("| Parameter | Value |")
                appendLine("|-----------|-------|")
                appendLine("| Domain | $domain |")
                appendLine("| Max Depth | $maxDepth |")
                appendLine("| Max Alternatives | $maxAlternatives |")
                appendLine("| Detail Level | $detailLevel |")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("- ⏳ Analyzing problem...")
            }
            overviewTask.add(MarkdownUtil.renderMarkdown(overviewContent, ui = task.ui))
            transcript?.write(overviewContent.toByteArray(StandardCharsets.UTF_8))

            // Gather context
            val priorContext = getPriorCode(agent.executionState)

            // Initialize the search
            val exploredPaths = mutableListOf<ReasoningPath>()
            var successfulPath: ReasoningPath? = null

            // Create solution tab
            val solutionTask = tabs.newTask("Solution")

            // Path search loop
            var pathsExplored = 0
            val pathQueue = PriorityQueue<Pair<List<ReasoningStep>, Int>>(compareByDescending { it.second })

            // Initialize with starting state
            val initialStep = analyzeInitialState(problemStatement, goal, givenInfo, domain, api)
            pathQueue.add(Pair(listOf(initialStep), 100))

            while (pathQueue.isNotEmpty() && pathsExplored < maxAlternatives && successfulPath == null) {
                val (currentPath, priority) = pathQueue.poll()
                pathsExplored++

                log.info("Exploring path $pathsExplored with ${currentPath.size} steps, priority=$priority")

                overviewTask.add(
                    MarkdownUtil.renderMarkdown(
                        "\n- 🔍 Exploring path $pathsExplored (${currentPath.size} steps)...",
                        ui = task.ui
                    )
                )

                // Explore this path
                val result = explorePath(
                    currentPath = currentPath,
                    problemStatement = problemStatement,
                    goal = goal,
                    givenInfo = givenInfo,
                    domain = domain,
                    maxDepth = maxDepth,
                    detailLevel = detailLevel,
                    api = api,
                    solutionTask = solutionTask,
                    task = task,
                    pathNumber = pathsExplored
                )

                exploredPaths.add(result)

                if (result.reached_goal) {
                    successfulPath = result
                    log.info("Found successful path with ${result.steps.size} steps")
                } else if (result.steps.size < maxDepth) {
                    // Generate alternative branches from the last valid step
                    val alternatives = generateAlternatives(
                        currentPath = result.steps,
                        problemStatement = problemStatement,
                        goal = goal,
                        domain = domain,
                        api = api
                    )
                    alternatives.options.forEachIndexed { index, step ->
                        if (pathQueue.size < maxAlternatives * 2) {
                            val newPath = result.steps.dropLast(1) + step
                            pathQueue.add(Pair(newPath, step.confidence))
                        }
                    }
                }
            }

            // Create proof tab if successful
            if (successfulPath != null) {
                val proofTask = tabs.newTask("Formal Proof")
                val proofContent = generateFormalProof(successfulPath, problemStatement, goal, detailLevel)
                proofTask.add(MarkdownUtil.renderMarkdown(proofContent, ui = task.ui))
                proofTask.complete()
                transcript?.write("\n\n---\n\n# Formal Proof\n\n".toByteArray(StandardCharsets.UTF_8))
                transcript?.write(proofContent.toByteArray(StandardCharsets.UTF_8))
            }

            // Show all paths if requested
            if (showAllPaths && exploredPaths.size > 1) {
                val pathsTask = tabs.newTask("All Paths")
                val pathsContent = buildString {
                    appendLine("# All Explored Paths")
                    appendLine()
                    exploredPaths.forEachIndexed { index, path ->
                        appendLine("## Path ${index + 1}")
                        appendLine()
                        appendLine("**Status:** ${if (path.reached_goal) "✅ Success" else "❌ ${path.termination_reason}"}")
                        appendLine()
                        appendLine("**Steps:** ${path.steps.size}")
                        appendLine()
                        appendLine("**Confidence:** ${path.path_confidence}%")
                        appendLine()
                        path.steps.forEachIndexed { stepIndex, step ->
                            appendLine("${stepIndex + 1}. ${step.statement}")
                            if (step.latex.isNotBlank()) {
                                appendLine("   $$${step.latex}$$")
                            }
                        }
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                }
                pathsTask.add(MarkdownUtil.renderMarkdown(pathsContent, ui = task.ui))
                pathsTask.complete()
            }

            // Final summary
            val totalTime = System.currentTimeMillis() - startTime
            val finalOverview = buildString {
                appendLine()
                appendLine("---")
                appendLine()
                if (successfulPath != null) {
                    appendLine("## ✅ Solution Found")
                    appendLine()
                    appendLine("| Metric | Value |")
                    appendLine("|--------|-------|")
                    appendLine("| Steps | ${successfulPath.steps.size} |")
                    appendLine("| Paths Explored | $pathsExplored |")
                    appendLine("| Confidence | ${successfulPath.path_confidence}% |")
                    appendLine("| Time | ${totalTime / 1000}s |")
                } else {
                    appendLine("## ❌ No Solution Found")
                    appendLine()
                    appendLine("Explored $pathsExplored paths without finding a complete solution.")
                    appendLine()
                    appendLine(
                        "**Best attempt:** ${
                            exploredPaths.maxByOrNull { it.path_confidence }
                                ?.let { "${it.steps.size} steps, ${it.path_confidence}% confidence" } ?: "None"
                        }")
                }
            }
            overviewTask.add(MarkdownUtil.renderMarkdown(finalOverview, ui = task.ui))
            overviewTask.complete()
            solutionTask.complete()
            transcript?.write(finalOverview.toByteArray(StandardCharsets.UTF_8))
            transcript?.close()

            // Generate result
            val resultMessage = if (successfulPath != null) {
                buildString {
                    appendLine("# Solution")
                    appendLine()
                    appendLine("## Problem")
                    appendLine(problemStatement)
                    appendLine()
                    appendLine("## Answer")
                    appendLine()
                    val finalStep = successfulPath.steps.last()
                    if (finalStep.latex.isNotBlank()) {
                        appendLine("$$${finalStep.latex}$$")
                    }
                    appendLine()
                    appendLine(finalStep.statement)
                    appendLine()
                    appendLine("## Key Steps")
                    appendLine()
                    successfulPath.steps.filter { it.step_type != "initial" }.take(5).forEach { step ->
                        appendLine("- ${step.statement}")
                        if (step.latex.isNotBlank() && step.latex != step.statement) {
                            appendLine("  - $${step.latex}$")
                        }
                    }
                    if (successfulPath.steps.size > 6) {
                        appendLine("- ... (${successfulPath.steps.size - 6} more steps)")
                    }
                }
            } else {
                "Unable to find a complete solution after exploring $pathsExplored paths. See the Solution tab for partial progress."
            }

            task.safeComplete("Mathematical reasoning complete in ${totalTime / 1000}s", log)
            resultFn(resultMessage)

        } catch (e: Exception) {
            log.error("Error during MathematicalReasoningTask execution", e)
            transcript?.close()
            task.error(e)
            task.safeComplete("Failed with error: ${e.message}", log)
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun analyzeInitialState(
        problemStatement: String,
        goal: String,
        givenInfo: List<String>,
        domain: String,
        api: ChatInterface
    ): ReasoningStep {
        return try {
            ParsedAgent(
                resultClass = ReasoningStep::class.java,
                prompt = """
You are a mathematical reasoning expert. Analyze the initial state of a mathematical problem.

## Problem Statement
$problemStatement

## Goal
$goal

## Given Information
${givenInfo.joinToString("\n") { "- $it" }.ifEmpty { "None specified" }}

## Domain
$domain

## Instructions
Create the initial reasoning step that captures the starting state of the problem.
- Restate the problem in precise mathematical terms
- Identify the key variables and relationships
- Express the initial state in LaTeX notation
- Set step_type to "initial"
- Set step_id to "S0"
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "InitialStateAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze the initial state")).obj
        } catch (e: Exception) {
            log.warn("Failed to analyze initial state", e)
            ReasoningStep(
                step_id = "S0",
                statement = problemStatement,
                latex = "",
                justification = "Given problem statement",
                step_type = "initial",
                confidence = 100,
                verified = true
            )
        }
    }

    private fun explorePath(
        currentPath: List<ReasoningStep>,
        problemStatement: String,
        goal: String,
        givenInfo: List<String>,
        domain: String,
        maxDepth: Int,
        detailLevel: String,
        api: ChatInterface,
        solutionTask: SessionTask,
        task: SessionTask,
        pathNumber: Int
    ): ReasoningPath {
        val steps = currentPath.toMutableList()
        var depth = steps.size

        // Display current path progress
        solutionTask.add(buildString {
            appendLine("# Path $pathNumber Exploration")
            appendLine()
            appendLine("## Starting Point")
            appendLine()
            steps.forEach { step ->
                appendLine("**Step ${step.step_id}:** ${step.statement}")
                if (step.latex.isNotBlank()) {
                    appendLine()
                    appendLine("$$${step.latex}$$")
                }
                appendLine()
            }
            appendLine("---")
            appendLine()
        }.let { MarkdownUtil.renderMarkdown(it, ui = task.ui) })

        while (depth < maxDepth) {
            // Check if we've reached the goal
            val goalCheck = checkGoal(steps, goal, api)
            if (goalCheck.goal_reached) {
                solutionTask.add(
                    MarkdownUtil.renderMarkdown(
                        "\n✅ **Goal Reached!**\n\n${goalCheck.explanation}\n",
                        ui = task.ui
                    )
                )
                return ReasoningPath(
                    steps = steps,
                    reached_goal = true,
                    termination_reason = "Goal achieved",
                    path_confidence = steps.map { it.confidence }.average().toInt()
                )
            }

            // Generate next step
            val nextStep = generateNextStep(steps, problemStatement, goal, givenInfo, domain, detailLevel, api)

            if (nextStep == null || nextStep.statement.isBlank()) {
                solutionTask.add(MarkdownUtil.renderMarkdown("\n⚠️ **No valid next step found**\n", ui = task.ui))
                return ReasoningPath(
                    steps = steps,
                    reached_goal = false,
                    termination_reason = "No valid next step",
                    path_confidence = steps.map { it.confidence }.average().toInt()
                )
            }

            // Verify the step
            val verification = verifyStep(nextStep, steps, domain, api)
            if (!verification.is_valid) {
                solutionTask.add(buildString {
                    appendLine()
                    appendLine("⚠️ **Step verification failed:**")
                    appendLine()
                    appendLine("Attempted: ${nextStep.statement}")
                    appendLine()
                    verification.errors.forEach { appendLine("- ❌ $it") }
                    appendLine()
                }.let { MarkdownUtil.renderMarkdown(it, ui = task.ui) })

                // Try to recover with suggestions
                if (verification.suggestions.isNotEmpty()) {
                    log.debug("Attempting recovery with suggestions")
                    // Could implement recovery logic here
                }

                return ReasoningPath(
                    steps = steps,
                    reached_goal = false,
                    termination_reason = "Step verification failed: ${verification.errors.firstOrNull() ?: "Unknown error"}",
                    path_confidence = steps.map { it.confidence }.average().toInt()
                )
            }

            // Add verified step
            val verifiedStep = nextStep.copy(
                step_id = "S${steps.size}",
                verified = true
            )
            steps.add(verifiedStep)
            depth++

            // Display the new step
            solutionTask.add(buildString {
                appendLine()
                appendLine("### Step ${verifiedStep.step_id}: ${verifiedStep.step_type}")
                appendLine()
                appendLine(verifiedStep.statement)
                if (verifiedStep.latex.isNotBlank()) {
                    appendLine()
                    appendLine("$$${verifiedStep.latex}$$")
                }
                appendLine()
                appendLine("*Justification:* ${verifiedStep.justification}")
                if (verifiedStep.notes.isNotBlank()) {
                    appendLine()
                    appendLine("*Note:* ${verifiedStep.notes}")
                }
                appendLine()
            }.let { MarkdownUtil.renderMarkdown(it, ui = task.ui) })
        }

        return ReasoningPath(
            steps = steps,
            reached_goal = false,
            termination_reason = "Maximum depth reached",
            path_confidence = steps.map { it.confidence }.average().toInt()
        )
    }

    private fun generateNextStep(
        currentSteps: List<ReasoningStep>,
        problemStatement: String,
        goal: String,
        givenInfo: List<String>,
        domain: String,
        detailLevel: String,
        api: ChatInterface
    ): ReasoningStep? {
        return try {
            ParsedAgent(
                resultClass = ReasoningStep::class.java,
                prompt = """
You are a mathematical reasoning expert. Generate the next logical step in solving a problem.

## Problem Statement
$problemStatement

## Goal
$goal

## Given Information
${givenInfo.joinToString("\n") { "- $it" }.ifEmpty { "None specified" }}

## Domain
$domain

## Current Progress
${
                    currentSteps.mapIndexed { i, step ->
                        "Step $i: ${step.statement}\n  LaTeX: ${step.latex}\n  Justification: ${step.justification}"
                    }.joinToString("\n\n")
                }

## Instructions
Generate the next logical step that moves us closer to the goal.

Requirements:
1. The step must be mathematically valid and follow from previous steps
2. Provide clear justification (cite theorem, rule, or operation used)
3. Include proper LaTeX notation
4. Choose appropriate step_type: 'algebraic', 'substitution', 'simplification', 'theorem', 'inference', 'definition'
5. Estimate confidence (0-100) based on how certain the step is
6. Keep steps atomic - one transformation at a time
7. Detail level: $detailLevel

Focus on making progress toward: $goal
                """.trimIndent(),
                model = api,
                temperature = 0.4,
                name = "StepGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate the next step")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate next step", e)
            null
        }
    }

    private fun verifyStep(
        step: ReasoningStep,
        previousSteps: List<ReasoningStep>,
        domain: String,
        api: ChatInterface
    ): StepVerification {
        return try {
            ParsedAgent(
                resultClass = StepVerification::class.java,
                prompt = """
You are a mathematical verification expert. Verify if a reasoning step is valid.

## Domain
$domain

## Previous Steps
${
                    previousSteps.takeLast(3).mapIndexed { i, s ->
                        "Step ${previousSteps.size - 3 + i}: ${s.statement}\n  LaTeX: ${s.latex}"
                    }.joinToString("\n\n")
                }

## Step to Verify
Statement: ${step.statement}
LaTeX: ${step.latex}
Justification: ${step.justification}
Type: ${step.step_type}

## Instructions
Verify this step is mathematically valid:
1. Check if it follows logically from previous steps
2. Verify the mathematical operations are correct
3. Confirm the justification is appropriate
4. Look for any errors in algebra, logic, or notation

Be rigorous but fair - minor notation issues are acceptable if the mathematics is sound.
                """.trimIndent(),
                model = api,
                temperature = 0.2,
                name = "StepVerifier",
                parsingChatter = defaultFast
            ).answer(listOf("Verify this step")).obj
        } catch (e: Exception) {
            log.warn("Failed to verify step", e)
            StepVerification(
                is_valid = true,
                explanation = "Verification skipped due to error",
                errors = emptyList(),
                suggestions = emptyList()
            )
        }
    }

    private fun checkGoal(
        steps: List<ReasoningStep>,
        goal: String,
        api: ChatInterface
    ): GoalCheck {
        return try {
            ParsedAgent(
                resultClass = GoalCheck::class.java,
                prompt = """
You are a mathematical reasoning expert. Check if the goal has been reached.

## Goal
$goal

## Current State
${
                    steps.lastOrNull()?.let {
                        "Latest step: ${it.statement}\nLaTeX: ${it.latex}"
                    } ?: "No steps yet"
                }

## Full Progress
${steps.takeLast(5).mapIndexed { i, s -> "${i + 1}. ${s.statement}" }.joinToString("\n")}

## Instructions
Determine if the goal has been achieved:
1. Compare the current state to the goal
2. Estimate progress (0-100%)
3. If not complete, describe what remains
4. Be precise about whether the goal is fully achieved
                """.trimIndent(),
                model = api,
                temperature = 0.2,
                name = "GoalChecker",
                parsingChatter = defaultFast
            ).answer(listOf("Check if goal is reached")).obj
        } catch (e: Exception) {
            log.warn("Failed to check goal", e)
            GoalCheck(
                goal_reached = false,
                progress = 0,
                remaining_work = "Unable to assess",
                explanation = "Goal check failed"
            )
        }
    }

    private fun generateAlternatives(
        currentPath: List<ReasoningStep>,
        problemStatement: String,
        goal: String,
        domain: String,
        api: ChatInterface
    ): NextStepOptions {
        return try {
            ParsedAgent(
                resultClass = NextStepOptions::class.java,
                prompt = """
You are a mathematical reasoning expert. Generate alternative approaches.

## Problem
$problemStatement

## Goal
$goal

## Domain
$domain

## Current Path (last 3 steps)
${
                    currentPath.takeLast(3).mapIndexed { i, s ->
                        "Step ${currentPath.size - 3 + i}: ${s.statement}"
                    }.joinToString("\n")
                }

## Instructions
Generate 2-3 alternative next steps that could be taken from the current state.
Each alternative should:
1. Be a valid mathematical operation
2. Take a different approach than the current path
3. Have potential to reach the goal

Rank them by likelihood of success.
                """.trimIndent(),
                model = api,
                temperature = 0.6,
                name = "AlternativeGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate alternatives")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate alternatives", e)
            NextStepOptions(
                options = emptyList(),
                recommended_index = 0,
                recommendation_reason = "Failed to generate alternatives"
            )
        }
    }

    private fun generateFormalProof(
        path: ReasoningPath,
        problemStatement: String,
        goal: String,
        detailLevel: String
    ): String {
        return buildString {
            appendLine("# Formal Proof")
            appendLine()
            appendLine("## Problem Statement")
            appendLine()
            appendLine(problemStatement)
            appendLine()
            appendLine("## Goal")
            appendLine()
            appendLine(goal)
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Proof")
            appendLine()

            path.steps.forEachIndexed { index, step ->
                if (step.step_type == "initial") {
                    appendLine("**Given:**")
                } else {
                    appendLine("**Step $index** (${step.step_type}):")
                }
                appendLine()

                if (step.latex.isNotBlank()) {
                    appendLine("$$${step.latex}$$")
                    appendLine()
                }

                appendLine(step.statement)
                appendLine()

                if (detailLevel != "brief" && step.justification.isNotBlank()) {
                    appendLine("*Justification:* ${step.justification}")
                    appendLine()
                }

                if (detailLevel == "detailed" && step.notes.isNotBlank()) {
                    appendLine("> ${step.notes}")
                    appendLine()
                }
            }

            appendLine("---")
            appendLine()
            appendLine("## Conclusion")
            appendLine()
            val finalStep = path.steps.last()
            appendLine("$$${finalStep.latex}$$")
            appendLine()
            appendLine("**Q.E.D.** ∎")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("*Proof completed in ${path.steps.size} steps with ${path.path_confidence}% confidence.*")
        }
    }
}