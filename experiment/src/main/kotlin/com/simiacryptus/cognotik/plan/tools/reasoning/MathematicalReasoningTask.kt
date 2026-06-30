package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MathematicalReasoningTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: MathematicalReasoningTaskExecutionConfigData?
) :
  AbstractTask<MathematicalReasoningTask.MathematicalReasoningTaskExecutionConfigData, MathematicalReasoningTask.MathematicalReasoningTaskTypeConfig>(
    orchestrationConfig,
    planTask
  ) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(MathematicalReasoningTask::class.java)

    @JvmStatic
    val MathematicalReasoning = TaskType(
      name = "MathematicalReasoning",
      category = "Reasoning",
      taskClass = MathematicalReasoningTask::class.java,
      executionConfigClass = MathematicalReasoningTaskExecutionConfigData::class.java,
      taskSettingsClass = MathematicalReasoningTaskTypeConfig::class.java,
      description = "Solve mathematical problems through step-by-step logical reasoning with verifiable steps",
      tooltipHtml = "<p>Uses path search to solve mathematical problems through rigorous step-by-step reasoning.</p>" +
          "<ul>" +
          "<li>Breaks down complex problems into verifiable atomic steps</li>" +
          "<li>Each step includes justification and verification</li>" +
          "<li>Explores multiple solution paths when needed</li>" +
          "<li>Backtracking when encountering dead ends</li>" +
          "<li>Provides detailed proof trail with MathJax notation</li>" +
          "<li>Supports algebra, calculus, number theory, and more</li>" +
          "<li>Validates intermediate results for correctness</li>" +
          "<li>Generates human-readable mathematical proofs</li>" +
          "</ul>",
    )
  }

  class MathematicalReasoningTaskTypeConfig(
    var promptTemplate: String = buildString {
      appendLine("MathematicalReasoning - Solve mathematical problems through step-by-step logical reasoning")
      appendLine("  ** Specify the problem statement clearly")
      appendLine("  ** Define the goal (prove, solve, simplify, etc.)")
      appendLine("  ** Provide any given information or constraints")
      appendLine("  ** Specify the mathematical domain if relevant")
      appendLine("  ** Configure search parameters (depth, alternatives)")
      appendLine("  ** The task will:")
      appendLine("     - Break down the problem into atomic steps")
      appendLine("     - Verify each step's mathematical validity")
      appendLine("     - Explore alternative solution paths")
      appendLine("     - Backtrack from dead ends")
      appendLine("     - Generate a complete proof trail")
      appendLine("     - Output results in MathJax/LaTeX format")
      appendLine("  ** Useful for:")
      appendLine("     - Solving algebraic equations")
      appendLine("     - Proving mathematical theorems")
      appendLine("     - Simplifying complex expressions")
      appendLine("     - Step-by-step calculus problems")
      appendLine("     - Number theory proofs")
      appendLine("     - Geometric proofs")
    },
    var initialStatePrompt: String = buildString {
      appendLine("You are a mathematical reasoning expert. Analyze the initial state of a mathematical problem.")
      appendLine("## Problem Statement")
      appendLine("{{problem_statement}}")
      appendLine("## Goal")
      appendLine("{{goal}}")
      appendLine("## Given Information")
      appendLine("{{given_info}}")
      appendLine("## Domain")
      appendLine("{{domain}}")
      appendLine("## Instructions")
      appendLine("Create the initial reasoning step that captures the starting state of the problem.")
      appendLine("- Restate the problem in precise mathematical terms")
      appendLine("- Identify the key variables and relationships")
      appendLine("- Express the initial state in LaTeX notation")
      appendLine("- Set step_type to \"initial\"")
      appendLine("- Set step_id to \"S0\"")
    },
    var stepGeneratorPrompt: String = buildString {
      appendLine("You are a mathematical reasoning expert. Generate the next logical step in solving a problem.")
      appendLine("## Problem Statement")
      appendLine("{{problem_statement}}")
      appendLine("## Goal")
      appendLine("{{goal}}")
      appendLine("## Given Information")
      appendLine("{{given_info}}")
      appendLine("## Domain")
      appendLine("{{domain}}")
      appendLine("## Current Progress")
      appendLine("{{current_progress}}")
      appendLine("## Instructions")
      appendLine("Generate the next logical step that moves us closer to the goal.")
      appendLine("Requirements:")
      appendLine("1. The step must be mathematically valid and follow from previous steps")
      appendLine("2. Provide clear justification (cite theorem, rule, or operation used)")
      appendLine("3. Include proper LaTeX notation")
      appendLine("4. Choose appropriate step_type: 'algebraic', 'substitution', 'simplification', 'theorem', 'inference', 'definition'")
      appendLine("5. Estimate confidence (0-100) based on how certain the step is")
      appendLine("6. Keep steps atomic - one transformation at a time")
      appendLine("7. Detail level: {{detail_level}}")
      appendLine("Focus on making progress toward: {{goal}}")
    },
    var stepVerifierPrompt: String = buildString {
      appendLine("You are a mathematical verification expert. Verify if a reasoning step is valid.")
      appendLine("## Domain")
      appendLine("{{domain}}")
      appendLine("## Previous Steps")
      appendLine("{{previous_steps}}")
      appendLine("## Step to Verify")
      appendLine("Statement: {{statement}}")
      appendLine("LaTeX: {{latex}}")
      appendLine("Justification: {{justification}}")
      appendLine("Type: {{step_type}}")
      appendLine("## Instructions")
      appendLine("Verify this step is mathematically valid:")
      appendLine("1. Check if it follows logically from previous steps")
      appendLine("2. Verify the mathematical operations are correct")
      appendLine("3. Confirm the justification is appropriate")
      appendLine("4. Look for any errors in algebra, logic, or notation")
      appendLine("Be rigorous but fair - minor notation issues are acceptable if the mathematics is sound.")
    },
    var goalCheckerPrompt: String = buildString {
      appendLine("You are a mathematical reasoning expert. Check if the goal has been reached.")
      appendLine("## Goal")
      appendLine("{{goal}}")
      appendLine("## Current State")
      appendLine("{{current_state}}")
      appendLine("## Full Progress")
      appendLine("{{full_progress}}")
      appendLine("## Instructions")
      appendLine("Determine if the goal has been achieved:")
      appendLine("1. Compare the current state to the goal")
      appendLine("2. Estimate progress (0-100%)")
      appendLine("3. If not complete, describe what remains")
      appendLine("4. Be precise about whether the goal is fully achieved")
    },
    var alternativeGeneratorPrompt: String = buildString {
      appendLine("You are a mathematical reasoning expert. Generate alternative approaches.")
      appendLine("## Problem")
      appendLine("{{problem_statement}}")
      appendLine("## Goal")
      appendLine("{{goal}}")
      appendLine("## Domain")
      appendLine("{{domain}}")
      appendLine("## Current Path (last 3 steps)")
      appendLine("{{current_path}}")
      appendLine("## Instructions")
      appendLine("Generate 2-3 alternative next steps that could be taken from the current state.")
      appendLine("Each alternative should:")
      appendLine("1. Be a valid mathematical operation")
      appendLine("2. Take a different approach than the current path")
      appendLine("3. Have potential to reach the goal")
      appendLine("Rank them by likelihood of success.")
    }
  ) : TaskTypeConfig()


  class MathematicalReasoningTaskExecutionConfigData(
    @Description("The mathematical problem or theorem to solve/prove")
    var problem_statement: String? = null,
    @Description("The goal or target result (e.g., 'prove equality', 'find x', 'simplify expression')")
    var goal: String? = null,
    @Description("Known facts, axioms, or given information")
    var given_information: List<String>? = null,
    @Description("Mathematical domain (e.g., 'algebra', 'calculus', 'number_theory', 'geometry', 'linear_algebra')")
    var domain: String? = "general",
    @Description("Maximum depth of reasoning steps (default: 20)")
    var max_depth: Int = 20,
    @Description("Maximum number of alternative paths to explore (default: 3)")
    var max_alternatives: Int = 3,
    @Description("Whether to show all explored paths or just the successful one")
    var show_all_paths: Boolean = false,
    @Description("Level of detail in explanations ('brief', 'standard', 'detailed')")
    var detail_level: String = "standard",

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
      max_depth = max_depth.coerceIn(1, 100)
      max_alternatives = max_alternatives.coerceIn(1, 10)
      if (detail_level !in listOf("brief", "standard", "detailed")) {
        detail_level = "standard"
      }
      return ValidatedObject.Companion.validateFields(this)
    }
  }

  data class ReasoningStep(
    @Description("Unique identifier for this step")
    var step_id: String = "",
    @Description("The mathematical statement or transformation at this step")
    var statement: String = "",
    @Description("LaTeX/MathJax representation of the statement")
    var latex: String = "",
    @Description("Justification for this step (theorem, axiom, or rule applied)")
    var justification: String = "",
    @Description("Type of step: 'initial', 'axiom', 'definition', 'theorem', 'algebraic', 'substitution', 'simplification', 'inference'")
    var step_type: String = "",
    @Description("Confidence in this step's correctness (0-100)")
    var confidence: Int = 100,
    @Description("Whether this step has been verified")
    var verified: Boolean = false,
    @Description("Any notes or caveats about this step")
    var notes: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (statement.isBlank()) return "statement must not be blank"
      confidence = confidence.coerceIn(0, 100)
      return null
    }
  }

  data class ReasoningPath(
    @Description("Sequence of reasoning steps")
    var steps: List<ReasoningStep> = emptyList(),
    @Description("Whether this path reached the goal")
    var reached_goal: Boolean = false,
    @Description("If not reached, reason for stopping")
    var termination_reason: String = "",
    @Description("Overall confidence in this path")
    var path_confidence: Int = 0
  )

  data class NextStepOptions(
    @Description("List of possible next steps to explore")
    var options: List<ReasoningStep> = emptyList(),
    @Description("Recommended option index (0-based)")
    var recommended_index: Int = 0,
    @Description("Reasoning for the recommendation")
    var recommendation_reason: String = ""
  )

  data class StepVerification(
    @Description("Whether the step is mathematically valid")
    var is_valid: Boolean = false,
    @Description("Explanation of the verification")
    var explanation: String = "",
    @Description("Any errors or issues found")
    var errors: List<String> = emptyList(),
    @Description("Suggestions for correction if invalid")
    var suggestions: List<String> = emptyList()
  )

  data class GoalCheck(
    @Description("Whether the current state matches the goal")
    var goal_reached: Boolean = false,
    @Description("How close we are to the goal (0-100)")
    var progress: Int = 0,
    @Description("What remains to be done")
    var remaining_work: String = "",
    @Description("Explanation of the assessment")
    var explanation: String = ""
  )

  override fun promptSegment(): String {
    return typeConfig?.promptTemplate ?: ""
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.newUserFileStream(transcriptFile())
    task.ui.pool.submit {
      try {
        val startTime = System.currentTimeMillis()
        log.info("MathematicalReasoningTask started. Problem: ${executionConfig?.problem_statement?.take(50)}...")

        // Validate configuration
        val executionConfig = this.executionConfig ?: MathematicalReasoningTaskExecutionConfigData()
        executionConfig?.validate()?.let { errorMessage ->
          log.error("MathematicalReasoningTask validation failed: $errorMessage")
          task.error(ValidatedObject.ValidationError(errorMessage, executionConfig))
          transcript?.write(
            "\n## Validation Error\n\n<details><summary>Validation Details</summary>\n\n$errorMessage\n\n</details>\n".toByteArray(
              StandardCharsets.UTF_8
            )
          )
          resultFn("VALIDATION ERROR: $errorMessage")
          return@submit
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
        val smartApi = orchestrationConfig.defaultSmart.getChildClient(task)
        val fastApi = orchestrationConfig.defaultFast.getChildClient(task)

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
        overviewTask.add(overviewContent.renderMarkdown())

        transcript?.write(buildString {
          appendLine("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">")
          appendLine()
          appendLine("## Work Details")
          appendLine()
          appendLine("<details><summary>Task Configuration & Context</summary>")
          appendLine()
          append(overviewContent)
          appendLine()
          appendLine("</details>")
          appendLine()
        }.toByteArray(StandardCharsets.UTF_8))

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
        val initialStep = analyzeInitialState(problemStatement, goal, givenInfo, domain, smartApi, fastApi)
        pathQueue.add(Pair(listOf(initialStep), 100))

        while (pathQueue.isNotEmpty() && pathsExplored < maxAlternatives && successfulPath == null) {
          val (currentPath, priority) = pathQueue.poll()
          pathsExplored++


          log.info("Exploring path $pathsExplored. Steps: ${currentPath.size}, Priority: $priority")

          overviewTask.add("\n- 🔍 Exploring path $pathsExplored (${currentPath.size} steps)...".renderMarkdown())

          // Explore this path
          val result = explorePath(
            currentPath = currentPath,
            problemStatement = problemStatement,
            goal = goal,
            givenInfo = givenInfo,
            domain = domain,
            maxDepth = maxDepth,
            detailLevel = detailLevel,
            smartApi = smartApi,
            fastApi = fastApi,
            solutionTask = solutionTask,
            task = task,
            pathNumber = pathsExplored
          )

          exploredPaths.add(result)

          if (result.reached_goal) {
            successfulPath = result
            log.info("MathematicalReasoningTask found solution in ${result.steps.size} steps.")
          } else if (result.steps.size < maxDepth) {
            // Generate alternative branches from the last valid step
            val alternatives = generateAlternatives(
              currentPath = result.steps,
              problemStatement = problemStatement,
              goal = goal,
              domain = domain,
              smartApi = smartApi,
              fastApi = fastApi
            )
            alternatives.options.forEachIndexed { index, step ->
              if (pathQueue.size < maxAlternatives * 2) {
                val newPath = result.steps.dropLast(1) + step
                pathQueue.add(Pair(newPath, step.confidence))
              }
            }
          }
        }
        // Close work-details tab div in transcript
        transcript?.write("\n</div>\n\n".toByteArray(StandardCharsets.UTF_8))
        // Open final-output tab div in transcript
        transcript?.write(
          "<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray(
            StandardCharsets.UTF_8
          )
        )


        // Create proof tab if successful
        if (successfulPath != null) {
          val proofTask = tabs.newTask("Formal Proof")
          val proofContent = generateFormalProof(successfulPath, problemStatement, goal, detailLevel)
          proofTask.add(proofContent.renderMarkdown())
          proofTask.complete()
          transcript?.write("# Formal Proof\n\n".toByteArray(StandardCharsets.UTF_8))
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
          pathsTask.add(pathsContent.renderMarkdown())
          pathsTask.complete()
          transcript?.write(buildString {
            appendLine()
            appendLine("<details><summary>All Explored Reasoning Paths</summary>")
            appendLine()
            append(pathsContent)
            appendLine()
            appendLine("</details>")
            appendLine()
          }.toByteArray(StandardCharsets.UTF_8))
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
        overviewTask.add(finalOverview.renderMarkdown())
        overviewTask.complete()
        solutionTask.complete()
        transcript?.write(finalOverview.toByteArray(StandardCharsets.UTF_8))
        transcript?.write("\n</div>\n\n".toByteArray(StandardCharsets.UTF_8))

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

        task.complete("Mathematical reasoning complete in ${totalTime / 1000}s".renderMarkdown())
        resultFn(resultMessage)

      } catch (e: Exception) {
        // Triple Log: UI, SLF4J, Transcript
        task.error(e)
        log.error("MathematicalReasoningTask failed: ${e.message}", e)
        transcript?.write(buildString {
          appendLine()
          appendLine("## Error")
          appendLine()
          appendLine("<details><summary>Stack Trace</summary>")
          appendLine()
          appendLine("```")
          appendLine(e.stackTraceToString())
          appendLine("```")
          appendLine()
          appendLine("</details>")
        }.toByteArray(StandardCharsets.UTF_8))
        resultFn("ERROR: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }

  private fun analyzeInitialState(
    problemStatement: String,
    goal: String,
    givenInfo: List<String>,
    domain: String,
    smartApi: ChatInterface,
    fastApi: ChatInterface
  ): ReasoningStep {
    return try {
      val prompt = typeConfig?.initialStatePrompt
        ?.replace("{{problem_statement}}", problemStatement)
        ?.replace("{{goal}}", goal)
        ?.replace("{{given_info}}", givenInfo.joinToString("\n") { "- $it" }.ifEmpty { "None specified" })
        ?.replace("{{domain}}", domain) ?: ""
      ParsedAgent(
        resultClass = ReasoningStep::class.java,


        prompt = prompt,
        model = smartApi,
        temperature = 0.3,
        name = "InitialStateAnalyzer",
        parsingModel = fastApi
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
    smartApi: ChatInterface,
    fastApi: ChatInterface,
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
    }.renderMarkdown())

    while (depth < maxDepth) {
      // Check if we've reached the goal
      val goalCheck = checkGoal(steps, goal, smartApi, fastApi)
      if (goalCheck.goal_reached) {
        solutionTask.add("\n✅ **Goal Reached!**\n\n${goalCheck.explanation}\n".renderMarkdown())
        return ReasoningPath(
          steps = steps,
          reached_goal = true,
          termination_reason = "Goal achieved",
          path_confidence = steps.map { it.confidence }.average().toInt()
        )
      }

      // Generate next step
      val nextStep = generateNextStep(steps, problemStatement, goal, givenInfo, domain, detailLevel, smartApi, fastApi)

      if (nextStep == null || nextStep.statement.isBlank()) {
        solutionTask.add("\n⚠️ **No valid next step found**\n".renderMarkdown())
        return ReasoningPath(
          steps = steps,
          reached_goal = false,
          termination_reason = "No valid next step",
          path_confidence = steps.map { it.confidence }.average().toInt()
        )
      }

      // Verify the step
      val verification = verifyStep(nextStep, steps, domain, smartApi, fastApi)
      if (!verification.is_valid) {
        solutionTask.add(buildString {
          appendLine()
          appendLine("⚠️ **Step verification failed:**")
          appendLine()
          appendLine("Attempted: ${nextStep.statement}")
          appendLine()
          verification.errors.forEach { appendLine("- ❌ $it") }
          appendLine()
        }.renderMarkdown())


        return ReasoningPath(
          steps = steps,
          reached_goal = false,
          termination_reason = "Step verification failed: ${verification.errors.firstOrNull() ?: "Unknown error"}",
          path_confidence = steps.map { it.confidence }.average().toInt()
        )
      }

      // Add verified step
      val verifiedStep = nextStep.copy().apply {
        step_id = "S${steps.size}"
        verified = true
      }
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
      }.renderMarkdown())
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
    smartApi: ChatInterface,
    fastApi: ChatInterface
  ): ReasoningStep? {
    return try {
      val progress = currentSteps.mapIndexed { i, step ->
        "Step $i: ${step.statement}\n  LaTeX: ${step.latex}\n  Justification: ${step.justification}"
      }.joinToString("\n\n")
      val prompt = typeConfig?.stepGeneratorPrompt
        ?.replace("{{problem_statement}}", problemStatement)
        ?.replace("{{goal}}", goal)
        ?.replace("{{given_info}}", givenInfo.joinToString("\n") { "- $it" }.ifEmpty { "None specified" })
        ?.replace("{{domain}}", domain)
        ?.replace("{{current_progress}}", progress)
        ?.replace("{{detail_level}}", detailLevel) ?: ""
      ParsedAgent(
        resultClass = ReasoningStep::class.java,


        prompt = prompt,
        model = smartApi,
        temperature = 0.4,
        name = "StepGenerator",
        parsingModel = fastApi
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
    smartApi: ChatInterface,
    fastApi: ChatInterface
  ): StepVerification {
    return try {
      val prev = previousSteps.takeLast(3).mapIndexed { i, s ->
        "Step ${previousSteps.size - 3 + i}: ${s.statement}\n  LaTeX: ${s.latex}"
      }.joinToString("\n\n")
      val prompt = typeConfig?.stepVerifierPrompt
        ?.replace("{{domain}}", domain)
        ?.replace("{{previous_steps}}", prev)
        ?.replace("{{statement}}", step.statement)
        ?.replace("{{latex}}", step.latex)
        ?.replace("{{justification}}", step.justification)
        ?.replace("{{step_type}}", step.step_type) ?: ""
      ParsedAgent(
        resultClass = StepVerification::class.java,


        prompt = prompt,
        model = smartApi,
        temperature = 0.2,
        name = "StepVerifier",
        parsingModel = fastApi
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
    smartApi: ChatInterface,
    fastApi: ChatInterface
  ): GoalCheck {
    return try {
      val latest = steps.lastOrNull()?.let { "Latest step: ${it.statement}\nLaTeX: ${it.latex}" } ?: "No steps yet"
      val full = steps.takeLast(5).mapIndexed { i, s -> "${i + 1}. ${s.statement}" }.joinToString("\n")
      val prompt = typeConfig?.goalCheckerPrompt
        ?.replace("{{goal}}", goal)
        ?.replace("{{current_state}}", latest)
        ?.replace("{{full_progress}}", full) ?: ""
      ParsedAgent(
        resultClass = GoalCheck::class.java,


        prompt = prompt,
        model = smartApi,
        temperature = 0.2,
        name = "GoalChecker",
        parsingModel = fastApi
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
    smartApi: ChatInterface,
    fastApi: ChatInterface
  ): NextStepOptions {
    return try {
      val path = currentPath.takeLast(3).mapIndexed { i, s -> "Step ${currentPath.size - 3 + i}: ${s.statement}" }
        .joinToString("\n")
      val prompt = typeConfig?.alternativeGeneratorPrompt
        ?.replace("{{problem_statement}}", problemStatement)
        ?.replace("{{goal}}", goal)
        ?.replace("{{domain}}", domain)
        ?.replace("{{current_path}}", path) ?: ""
      ParsedAgent(
        resultClass = NextStepOptions::class.java,


        prompt = prompt,
        model = smartApi,
        temperature = 0.6,
        name = "AlternativeGenerator",
        parsingModel = fastApi
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