package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.*
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ConstraintRelaxationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: ConstraintRelaxationTaskExecutionConfigData?
) : AbstractTask<ConstraintRelaxationTask.ConstraintRelaxationTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {
  val maxOutputSize = 500

  class ConstraintRelaxationTaskExecutionConfigData(
    @Description("The problem description to solve")
    val problem: String? = null,
    @Description("Map of constraints to their priority weights (0.0-1.0, where 1.0 is critical)")
    val constraints: Map<String, Double>? = null,
    @Description("Relaxation strategy: 'progressive' (gradual), 'selective' (choose subset), 'hierarchical' (by levels)")
    val relaxation_strategy: String = "progressive",
    @Description("Order for reintroducing constraints: 'by_priority', 'by_difficulty', 'by_dependency'")
    val reintroduction_order: String = "by_priority",
    @Description("Whether to actively seek creative ways to satisfy constraints")
    val find_creative_satisfactions: Boolean = true,
    @Description("Maximum number of relaxation/reintroduction iterations")
    val max_iterations: Int = 5,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    val input_files: List<String>? = null,
    @Description("Additional files for context")
    val related_files: List<String>? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = ConstraintRelaxation.name,
    task_description = "Solve '$problem' through progressive constraint relaxation",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (problem.isNullOrBlank()) {
        return "Problem description cannot be null or blank"
      }
      if (constraints.isNullOrEmpty()) {
        return "Constraints map cannot be null or empty"
      }
      constraints.forEach { (constraint, priority) ->
        if (constraint.isBlank()) {
          return "Constraint name cannot be blank"
        }
        if (priority < 0.0 || priority > 1.0) {
          return "Constraint priority must be between 0.0 and 1.0, got $priority for constraint '$constraint'"
        }
      }
      if (max_iterations < 1 || max_iterations > 10) {
        return "Max iterations must be between 1 and 10, got $max_iterations"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return """
ConstraintRelaxation - Solve over-constrained problems through progressive constraint relaxation
  ** Specify the problem to solve
  ** Define constraints with priority weights (0.0-1.0, where 1.0 is critical)
  ** Choose relaxation strategy:
     - 'progressive': Gradually relax constraints from lowest to highest priority
     - 'selective': Intelligently select which constraints to relax
     - 'hierarchical': Relax constraints in priority-based levels
  ** Choose reintroduction order:
     - 'by_priority': Reintroduce highest priority constraints first
     - 'by_difficulty': Reintroduce easiest constraints first
     - 'by_dependency': Reintroduce based on constraint dependencies
  ** Enable creative satisfaction finding to discover novel solutions
  ** Produces a solution that progressively satisfies constraints
  ** Shows evolution of solution as constraints are reintroduced
  ** Optionally, list input files (supports glob patterns) to be examined for context
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
    log.info("Starting ConstraintRelaxationTask for problem: '${executionConfig?.problem}'")

    val problem = executionConfig?.problem
    if (problem.isNullOrBlank()) {
      log.error("No problem description specified")
      task.safeComplete("CONFIGURATION ERROR: No problem description specified", log)
      resultFn("CONFIGURATION ERROR: No problem description specified")
      return
    }

    val constraints = executionConfig.constraints
    if (constraints.isNullOrEmpty()) {
      log.error("No constraints specified")
      task.safeComplete("CONFIGURATION ERROR: No constraints specified", log)
      resultFn("CONFIGURATION ERROR: No constraints specified")
      return
    }

    val relaxationStrategy = executionConfig.relaxation_strategy
    val reintroductionOrder = executionConfig.reintroduction_order
    val findCreativeSatisfactions = executionConfig.find_creative_satisfactions
    val maxIterations = executionConfig.max_iterations.coerceIn(1, 10)

    log.info(
      """
      |Configuration:
      |  Strategy: $relaxationStrategy
      |  Reintroduction Order: $reintroductionOrder
      |  Creative Satisfactions: $findCreativeSatisfactions
      |  Max Iterations: $maxIterations
      |  Constraints: ${constraints.size}
      """.trimMargin()
    )

    val api = defaultSmart ?: return

    val tabs = TabbedDisplay(task)
    val transcript = task.newUserFileStream(transcriptFile("constraint_relaxation"))
    val overviewTask = task.newTask()
    tabs["Overview"] = overviewTask.placeholder

    val overviewContent = buildString {
      appendLine("# Constraint Relaxation: Progressive Problem Solving")
      appendLine()
      appendLine("**Problem:** $problem")
      appendLine()
      appendLine("**Relaxation Strategy:** $relaxationStrategy")
      appendLine()
      appendLine("**Reintroduction Order:** $reintroductionOrder")
      appendLine()
      appendLine("**Creative Satisfactions:** ${if (findCreativeSatisfactions) "Enabled" else "Disabled"}")
      appendLine()
      appendLine("**Max Iterations:** $maxIterations")
      appendLine()
      appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
      appendLine()
      appendLine("---")
      appendLine()
      appendLine("## Constraints (${constraints.size})")
      appendLine()
      constraints.entries.sortedByDescending { it.value }.forEach { (constraint, priority) ->
        val priorityLabel = when {
          priority >= 0.9 -> "🔴 Critical"
          priority >= 0.7 -> "🟠 High"
          priority >= 0.5 -> "🟡 Medium"
          else -> "🟢 Low"
        }
        appendLine("- **$constraint** - $priorityLabel (${String.format("%.1f", priority)})")
      }
      appendLine()
      appendLine("---")
      appendLine()
      appendLine("## Progress")
      appendLine()
      appendLine("*Initializing constraint relaxation process...*")
    }
    transcript?.write(overviewContent.toByteArray())
    overviewTask.add(overviewContent.renderMarkdown())
    task.update()

    val priorContext = getPriorCode(agent.executionState)
    if (priorContext.isNotBlank()) {
      log.debug("Found prior context: ${priorContext.length} characters")
      val contextTask = task.newTask()
      tabs["Context"] = contextTask.placeholder
      contextTask.add(
        buildString {
          appendLine("# Context from Previous Tasks")
          appendLine()
          appendLine(priorContext.truncateForDisplay())
        }.renderMarkdown()
      )
      transcript?.write("\n\n# Context from Previous Tasks\n\n${priorContext.truncateForDisplay()}\n".toByteArray())
      task.update()
      contextTask.complete()
    }
    val inputFileContent = getInputFileCode()

    overviewTask.add(
      buildString {
        appendLine()
        appendLine("✅ Initialization complete")
        appendLine()
        appendLine("*Analyzing constraint structure...*")
      }.renderMarkdown()
    )
    transcript?.write("\n\n✅ Initialization complete\n\n*Analyzing constraint structure...*\n".toByteArray())
    task.update()

    val solutionBuilder = StringBuilder()
    solutionBuilder.append("# Constraint Relaxation Solution\n\n")
    solutionBuilder.append("**Problem:** $problem\n\n")

    try {
      // Step 1: Analyze and order constraints
      log.info("Analyzing constraint structure")
      val analysisTask = task.newTask()
      tabs["Constraint Analysis"] = analysisTask.placeholder

      analysisTask.add(
        buildString {
          appendLine("# Constraint Analysis")
          appendLine()
          appendLine("**Status:** Analyzing constraint structure and dependencies...")
        }.renderMarkdown()
      )
      task.update()

      val orderedConstraints = orderConstraints(constraints, reintroductionOrder)
      val relaxedConstraints = selectConstraintsToRelax(orderedConstraints, relaxationStrategy)
      buildString {
        appendLine()
        appendLine("## Constraint Ordering")
        appendLine()
        appendLine("Constraints will be reintroduced in the following order:")
        appendLine()
        orderedConstraints.forEachIndexed { index, (constraint, priority) ->
          val status = if (relaxedConstraints.contains(constraint)) "🔓 Initially Relaxed" else "🔒 Active"
          appendLine("${index + 1}. **$constraint** ($status, priority: ${String.format("%.2f", priority)})")
        }
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("**Status:** ✅ Analysis complete")
      }

      task.update()
      analysisTask.complete()

      overviewTask.add(
        buildString {
          appendLine()
          appendLine("✅ Constraint analysis complete")
          appendLine()
          appendLine("*Solving relaxed problem...*")
        }.renderMarkdown()
      )
      task.update()

      // Step 2: Solve the fully relaxed problem
      log.info("Solving relaxed problem with ${relaxedConstraints.size} constraints relaxed")
      val relaxedSolutionTask = task.newTask()
      tabs["Relaxed Solution"] = relaxedSolutionTask.placeholder

      relaxedSolutionTask.add(
        buildString {
          appendLine("# Initial Relaxed Solution")
          appendLine()
          appendLine("**Relaxed Constraints:** ${relaxedConstraints.size}")
          appendLine()
          relaxedConstraints.forEach { constraint ->
            appendLine("- $constraint")
          }
          appendLine()
          appendLine("**Status:** Generating solution without relaxed constraints...")
        }.renderMarkdown()
      )
      transcript?.write("\n\n# Initial Relaxed Solution\n\n**Relaxed Constraints:** ${relaxedConstraints.size}\n\n".toByteArray())
      task.update()

      val activeConstraints = constraints.filterKeys { !relaxedConstraints.contains(it) }
      val relaxedSolution = solveWithConstraints(
        problem,
        activeConstraints,
        priorContext,
        api,
        findCreativeSatisfactions
      )

      val relaxedSolutionContent =
        buildString {
          appendLine()
          appendLine("## Solution")
          appendLine()
          appendLine(relaxedSolution)
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("**Status:** ✅ Relaxed solution generated")
        }

      transcript?.write(relaxedSolutionContent.toByteArray())
      relaxedSolutionTask.add(relaxedSolutionContent.renderMarkdown())
      task.update()
      relaxedSolutionTask.complete()

      solutionBuilder.append("## Initial Relaxed Solution\n\n")
      solutionBuilder.append("**Relaxed:** ${relaxedConstraints.joinToString(", ")}\n\n")
      solutionBuilder.append(relaxedSolution.truncateForDisplay(maxOutputSize))
      solutionBuilder.append("\n\n")

      var currentSolution = relaxedSolution
      val reintroductionSteps = mutableListOf<ReintroductionStep>()

      overviewTask.add(
        buildString {
          appendLine()
          appendLine("✅ Relaxed solution generated")
          appendLine()
          appendLine("*Beginning progressive constraint reintroduction...*")
        }.renderMarkdown()
      )
      task.update()

      // Step 3: Progressively reintroduce constraints
      val constraintsToReintroduce = orderedConstraints.filter { (constraint, _) ->
        relaxedConstraints.contains(constraint)
      }

      constraintsToReintroduce.forEachIndexed { index, (constraint, priority) ->
        if (index >= maxIterations) {
          log.info("Reached max iterations ($maxIterations), stopping reintroduction")
          return@forEachIndexed
        }

        val iterationStartTime = System.currentTimeMillis()
        log.info("Reintroducing constraint ${index + 1}/${constraintsToReintroduce.size}: $constraint")

        val iterationTask = task.newTask()
        tabs["Iteration ${index + 1}"] = iterationTask.placeholder

        iterationTask.add(
          buildString {
            appendLine("# Iteration ${index + 1}: Reintroducing Constraint")
            appendLine()
            appendLine("**Constraint:** $constraint")
            appendLine()
            appendLine("**Priority:** ${String.format("%.2f", priority)}")
            appendLine()
            appendLine("**Status:** Adapting solution to satisfy this constraint...")
          }.renderMarkdown()
        )
        transcript?.write("\n\n# Iteration ${index + 1}: Reintroducing Constraint\n\n**Constraint:** $constraint\n\n".toByteArray())
        task.update()

        val newActiveConstraints = activeConstraints.toMutableMap()
        newActiveConstraints[constraint] = priority

        val adaptedSolution = adaptSolutionForConstraint(
          problem,
          currentSolution,
          constraint,
          priority,
          newActiveConstraints,
          priorContext,
          api,
          findCreativeSatisfactions
        )

        val iterationTime = System.currentTimeMillis() - iterationStartTime

        val iterationContent =
          buildString {
            appendLine()
            appendLine("## Adapted Solution")
            appendLine()
            appendLine(adaptedSolution)
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("**Status:** ✅ Complete (${iterationTime / 1000.0}s)")
          }

        transcript?.write(iterationContent.toByteArray())
        iterationTask.add(iterationContent.renderMarkdown())
        task.update()
        iterationTask.complete()

        reintroductionSteps.add(
          ReintroductionStep(
            constraint = constraint,
            priority = priority,
            solution = adaptedSolution,
            iterationTime = iterationTime
          )
        )

        currentSolution = adaptedSolution

        overviewTask.add(
          buildString {
            appendLine()
            appendLine("✅ Iteration ${index + 1} complete: $constraint (${iterationTime / 1000.0}s)")
          }.renderMarkdown()
        )
        task.update()
      }

      // Step 4: Generate final synthesis
      log.info("Generating final synthesis")
      val synthesisTask = task.newTask()
      tabs["Final Synthesis"] = synthesisTask.placeholder

      synthesisTask.add(
        buildString {
          appendLine("# Final Synthesis")
          appendLine()
          appendLine("**Status:** Generating comprehensive analysis...")
        }.renderMarkdown()
      )
      task.update()

      val synthesis = generateSynthesis(
        problem,
        constraints,
        relaxedConstraints,
        reintroductionSteps,
        currentSolution,
        api
      )

      val synthesisContent =
        buildString {
          appendLine()
          appendLine(synthesis)
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }

      transcript?.write("\n\n# Final Synthesis\n\n${synthesis}\n".toByteArray())
      synthesisTask.add(synthesisContent.renderMarkdown())
      task.update()
      synthesisTask.complete()

      solutionBuilder.append("## Progressive Reintroduction\n\n")
      solutionBuilder.append("**Iterations:** ${reintroductionSteps.size}\n\n")
      reintroductionSteps.forEachIndexed { index, step ->
        solutionBuilder.append("${index + 1}. **${step.constraint}** (${step.iterationTime / 1000.0}s)\n")
      }
      solutionBuilder.append("\n## Final Synthesis\n\n")
      solutionBuilder.append(synthesis)

      val totalTime = System.currentTimeMillis() - startTime
      val avgIterationTime = if (reintroductionSteps.isNotEmpty()) {
        reintroductionSteps.map { it.iterationTime }.average()
      } else 0.0

      log.info(
        """
        |ConstraintRelaxationTask completed:
        |  Total Time: ${totalTime}ms
        |  Iterations: ${reintroductionSteps.size}
        |  Avg Iteration Time: ${avgIterationTime}ms
        |  Output Size: ${solutionBuilder.length} chars
        """.trimMargin()
      )

      val completionContent = buildString {
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## ✅ Constraint Relaxation Complete")
        appendLine()
        appendLine("**Total Time:** ${totalTime / 1000.0}s")
        appendLine()
        appendLine("**Iterations:** ${reintroductionSteps.size}")
        appendLine()
        appendLine("**Average Iteration Time:** ${avgIterationTime / 1000.0}s")
        appendLine()
        appendLine("**Constraints Satisfied:** ${constraints.size - relaxedConstraints.size + reintroductionSteps.size}/${constraints.size}")
        appendLine()
        appendLine(
          "**Completed:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }"
        )
      }
      overviewTask.add(
        completionContent.renderMarkdown()
      )

      transcript?.write(completionContent.toByteArray())
      transcript?.close()
      task.update()
      overviewTask.complete()

      val finalResult = solutionBuilder.toString()
      // Write detailed output to file
      val detailedLink = task.saveFile("constraint_relaxation_detailed.md", finalResult.toByteArray())

      // Generate summary message
      val summaryMessage = buildString {
        appendLine("✅ Constraint Relaxation Complete")
        appendLine()
        appendLine("**Total Time:** ${totalTime / 1000.0}s")
        appendLine("**Iterations:** ${reintroductionSteps.size}")
        appendLine("**Constraints Satisfied:** ${constraints.size - relaxedConstraints.size + reintroductionSteps.size}/${constraints.size}")
        appendLine()
        appendLine("📄 [View Detailed Results]($detailedLink)")
      }

      task.safeComplete(
        summaryMessage,
        log
      )
      resultFn(summaryMessage)

    } catch (e: Exception) {
      log.error("Error during constraint relaxation", e)
      task.error(e)
      transcript?.let {
        it.close()
      }

      overviewTask.add(
        buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ❌ Error Occurred")
          appendLine()
          appendLine("**Error:** ${e.message}")
          appendLine()
          appendLine("**Type:** ${e.javaClass.simpleName}")
        }.renderMarkdown()
      )
      task.update()
      overviewTask.complete()

      val errorOutput = buildString {
        appendLine("# Error in Constraint Relaxation")
        appendLine()
        appendLine("**Problem:** $problem")
        appendLine()
        appendLine("**Error:** ${e.message}")
        appendLine()
        if (solutionBuilder.isNotEmpty()) {
          appendLine("## Partial Results")
          appendLine()
          appendLine(solutionBuilder.toString())
        }
      }
      resultFn(errorOutput)
    }
  }


  private fun orderConstraints(
    constraints: Map<String, Double>,
    order: String
  ): List<Pair<String, Double>> {
    return when (order) {
      "by_priority" -> constraints.entries.sortedByDescending { it.value }.map { it.key to it.value }
      "by_difficulty" -> {
        // For now, treat lower priority as potentially easier to satisfy
        constraints.entries.sortedBy { it.value }.map { it.key to it.value }
      }

      "by_dependency" -> {
        // Simple heuristic: reintroduce in priority order (could be enhanced with dependency analysis)
        constraints.entries.sortedByDescending { it.value }.map { it.key to it.value }
      }

      else -> constraints.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
  }

  private fun selectConstraintsToRelax(
    orderedConstraints: List<Pair<String, Double>>,
    strategy: String
  ): Set<String> {
    return when (strategy) {
      "progressive" -> {
        // Relax bottom 50% of constraints by priority
        val relaxCount = (orderedConstraints.size * 0.5).toInt().coerceAtLeast(1)
        orderedConstraints.takeLast(relaxCount).map { it.first }.toSet()
      }

      "selective" -> {
        // Relax constraints with priority < 0.7
        orderedConstraints.filter { it.second < 0.7 }.map { it.first }.toSet()
      }

      "hierarchical" -> {
        // Relax all but the top priority tier (>= 0.9)
        orderedConstraints.filter { it.second < 0.9 }.map { it.first }.toSet()
      }

      else -> {
        // Default to progressive
        val relaxCount = (orderedConstraints.size * 0.5).toInt().coerceAtLeast(1)
        orderedConstraints.takeLast(relaxCount).map { it.first }.toSet()
      }
    }
  }

  private fun solveWithConstraints(
    problem: String,
    constraints: Map<String, Double>,
    priorContext: String,
    api: ChatInterface,
    findCreative: Boolean
  ): String {
    val prompt = buildString {
      appendLine("You are an expert problem solver specializing in constraint-based design.")
      appendLine()
      appendLine("## Problem:")
      appendLine(problem)
      appendLine()
      appendLine("## Active Constraints:")
      constraints.entries.sortedByDescending { it.value }.forEach { (constraint, priority) ->
        appendLine("- $constraint (priority: ${String.format("%.2f", priority)})")
      }
      appendLine()
      if (priorContext.isNotBlank()) {
        appendLine("## Context from Previous Tasks:")
        appendLine(priorContext.truncateForDisplay(5000))
        appendLine()
      }
      appendLine("## Instructions:")
      appendLine("Generate a solution that satisfies the active constraints listed above.")
      if (findCreative) {
        appendLine("Be creative and consider unconventional approaches that might satisfy constraints in novel ways.")
      }
      appendLine()
      appendLine("Provide:")
      appendLine("1. A clear solution description")
      appendLine("2. How each constraint is satisfied")
      appendLine("3. Any trade-offs or assumptions made")
      appendLine()
      appendLine("Generate the solution now:")
    }

    val agent = ChatAgent(
      prompt = prompt.toString(),
      model = api,
      temperature = if (findCreative) 0.8 else 0.5
    )

    return agent.answer(listOf(""))
  }

  private fun adaptSolutionForConstraint(
    problem: String,
    currentSolution: String,
    newConstraint: String,
    priority: Double,
    allActiveConstraints: Map<String, Double>,
    priorContext: String,
    api: ChatInterface,
    findCreative: Boolean
  ): String {
    val prompt = buildString {
      appendLine("You are an expert problem solver specializing in constraint-based design.")
      appendLine()
      appendLine("## Problem:")
      appendLine(problem)
      appendLine()
      appendLine("## Current Solution:")
      appendLine(currentSolution.truncateForDisplay(8000))
      appendLine()
      appendLine("## New Constraint to Satisfy:")
      appendLine("**$newConstraint** (priority: ${String.format("%.2f", priority)})")
      appendLine()
      appendLine("## All Active Constraints:")
      allActiveConstraints.entries.sortedByDescending { it.value }.forEach { (constraint, p) ->
        val marker = if (constraint == newConstraint) "🆕" else "✓"
        appendLine("$marker $constraint (priority: ${String.format("%.2f", p)})")
      }
      appendLine()
      if (priorContext.isNotBlank()) {
        appendLine("## Context from Previous Tasks:")
        appendLine(priorContext.truncateForDisplay(3000))
        appendLine()
      }
      appendLine("## Instructions:")
      appendLine("Adapt the current solution to satisfy the new constraint: **$newConstraint**")
      appendLine("You must maintain satisfaction of all previously satisfied constraints.")
      if (findCreative) {
        appendLine("Be creative! Consider:")
        appendLine("- Reframing the constraint")
        appendLine("- Finding synergies between constraints")
        appendLine("- Novel architectural approaches")
        appendLine("- Trade-offs that satisfy multiple constraints simultaneously")
      }
      appendLine()
      appendLine("Provide:")
      appendLine("1. The adapted solution")
      appendLine("2. How the new constraint is satisfied")
      appendLine("3. Confirmation that previous constraints remain satisfied")
      appendLine("4. Any creative insights or trade-offs")
      appendLine()
      appendLine("Generate the adapted solution now:")
    }

    val agent = ChatAgent(
      prompt = prompt.toString(),
      model = api,
      temperature = if (findCreative) 0.8 else 0.5
    )

    return agent.answer(listOf(""))
  }

  private fun generateSynthesis(
    problem: String,
    allConstraints: Map<String, Double>,
    initiallyRelaxed: Set<String>,
    reintroductionSteps: List<ReintroductionStep>,
    finalSolution: String,
    api: ChatInterface
  ): String {
    val prompt = buildString {
      appendLine("You are an expert problem solver providing a final synthesis of a constraint relaxation process.")
      appendLine()
      appendLine("## Problem:")
      appendLine(problem)
      appendLine()
      appendLine("## All Constraints:")
      allConstraints.entries.sortedByDescending { it.value }.forEach { (constraint, priority) ->
        appendLine("- $constraint (priority: ${String.format("%.2f", priority)})")
      }
      appendLine()
      appendLine("## Initially Relaxed Constraints:")
      initiallyRelaxed.forEach { constraint ->
        appendLine("- $constraint")
      }
      appendLine()
      appendLine("## Reintroduction Process:")
      reintroductionSteps.forEachIndexed { index, step ->
        appendLine(
          "${index + 1}. ${step.constraint} (priority: ${
            String.format(
              "%.2f",
              step.priority
            )
          }, time: ${step.iterationTime / 1000.0}s)"
        )
      }
      appendLine()
      appendLine("## Final Solution:")
      appendLine(finalSolution.truncateForDisplay(8000))
      appendLine()
      appendLine("## Instructions:")
      appendLine("Provide a comprehensive synthesis that includes:")
      appendLine("1. **Solution Overview**: High-level summary of the final solution")
      appendLine("2. **Constraint Satisfaction Analysis**: How each constraint is satisfied")
      appendLine("3. **Key Insights**: Important discoveries from the progressive relaxation process")
      appendLine("4. **Trade-offs**: Any compromises or trade-offs made")
      appendLine("5. **Creative Elements**: Novel or creative approaches used")
      appendLine("6. **Recommendations**: Suggestions for implementation or further refinement")
      appendLine()
      appendLine("Generate the synthesis now:")
    }

    val agent = ChatAgent(
      prompt = prompt.toString(),
      model = api,
      temperature = 0.6
    )

    return agent.answer(listOf(""))
  }

  private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
    .flatMap { pattern: String ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
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


  private data class ReintroductionStep(
    val constraint: String = "",
    val priority: Double = 0.0,
    val solution: String = "",
    val iterationTime: Long = 0L
  )

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ConstraintRelaxationTask::class.java)

    @JvmStatic
    val ConstraintRelaxation = TaskType(
      name = "ConstraintRelaxation",
      category = "Reasoning",
      taskClass = ConstraintRelaxationTask::class.java,
      executionConfigClass = ConstraintRelaxationTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Solve over-constrained problems through progressive constraint relaxation",
      tooltipHtml = """
                        Solves complex problems by temporarily relaxing constraints and progressively reintroducing them.
                        <ul>
                          <li>Identifies which constraints to initially relax based on priority</li>
                          <li>Solves simplified problem without relaxed constraints</li>
                          <li>Progressively reintroduces constraints in configurable order</li>
                          <li>Adapts solution at each step to satisfy new constraints</li>
                          <li>Finds creative ways to satisfy multiple constraints simultaneously</li>
                          <li>Supports multiple relaxation strategies (progressive, selective, hierarchical)</li>
                          <li>Configurable reintroduction order (by priority, difficulty, or dependency)</li>
                          <li>Useful for over-constrained problems, algorithm design, and architecture under constraints</li>
                        </ul>
                      """,
    )
  }
}