package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicInteger

class DecompositionSynthesisTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DecompositionSynthesisTaskExecutionConfigData?
) : AbstractTask<DecompositionSynthesisTask.DecompositionSynthesisTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class DecompositionSynthesisTaskExecutionConfigData(
        @Description("The complex problem to decompose")
        val complex_problem: String? = null,
        @Description("Decomposition strategy: 'functional', 'temporal', 'spatial', 'hierarchical'")
        val decomposition_strategy: String = "functional",
        @Description("Maximum decomposition depth")
        val max_depth: Int = 3,
        @Description("Whether to synthesize solutions from subproblems")
        val synthesize_solution: Boolean = true,
        @Description("Whether to validate synthesis coherence")
        val validate_coherence: Boolean = true,
        @Description("Additional files for context")
        val related_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = DecompositionSynthesis.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class ProblemDecomposition(
        @Description("List of subproblems identified")
        val subproblems: List<Subproblem> = emptyList(),
        @Description("Rationale for this decomposition")
        val decomposition_rationale: String = "",
        @Description("Dependencies between subproblems")
        val dependencies: Map<String, List<String>> = emptyMap()
    )

    data class Subproblem(
        @Description("Unique identifier for this subproblem")
        val id: String = "",
        @Description("Description of the subproblem")
        val description: String = "",
        @Description("Estimated complexity (1-10)")
        val complexity: Int = 5,
        @Description("Whether this can be further decomposed")
        val can_decompose: Boolean = false
    )

    data class SubproblemSolution(
        @Description("The subproblem ID being solved")
        val subproblem_id: String = "",
        @Description("The solution to this subproblem")
        val solution: String = "",
        @Description("Confidence in this solution (0-1)")
        val confidence: Double = 0.0
    )

    data class SynthesizedSolution(
        @Description("The complete synthesized solution")
        val solution: String = "",
        @Description("How subproblem solutions were integrated")
        val synthesis_approach: String = "",
        @Description("Overall confidence in the solution (0-1)")
        val confidence: Double = 0.0
    )

    data class CoherenceValidation(
        @Description("Whether the solution is coherent")
        val is_coherent: Boolean = false,
        @Description("Issues found in the synthesis")
        val issues: List<String> = emptyList(),
        @Description("Suggestions for improvement")
        val suggestions: List<String> = emptyList()
    )

    override fun promptSegment(): String {
        return """
DecompositionSynthesis - Decompose complex problems and synthesize solutions
  ** Specify the complex problem to decompose
  ** Choose decomposition strategy:
     - functional: Break down by function/capability
     - temporal: Break down by time/sequence
     - spatial: Break down by location/component
     - hierarchical: Break down by abstraction level
  ** Set maximum decomposition depth (default: 3)
  ** Enable solution synthesis to combine subproblem solutions
  ** Enable coherence validation to check solution consistency
  ** Related files can provide context for the problem
  ** Implements divide-and-conquer reasoning approach
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
        log.info("Starting DecompositionSynthesisTask with problem: ${executionConfig?.complex_problem?.take(100)}")

        val problem = executionConfig?.complex_problem
        if (problem.isNullOrBlank()) {
            log.error("No problem specified in execution config")
            resultFn("CONFIGURATION ERROR: No problem specified")
            return
        }

        // Create tabbed display for organized output
        val tabs = TabbedDisplay(task)
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter


        // Overview tab
        val overviewTask = ui.newTask(false)
        tabs["Overview"] = overviewTask.placeholder

        val overviewContent = buildString {
            appendLine("# Decomposition & Synthesis Analysis")
            appendLine()
            appendLine("**Problem:** ${problem.take(200)}${if (problem.length > 200) "..." else ""}")
            appendLine()
            appendLine("**Strategy:** ${executionConfig.decomposition_strategy}")
            appendLine("**Max Depth:** ${executionConfig.max_depth}")
            appendLine("**Synthesize Solution:** ${executionConfig.synthesize_solution}")
            appendLine("**Validate Coherence:** ${executionConfig.validate_coherence}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("⏳ Starting decomposition analysis...")
        }
        overviewTask.add(overviewContent.renderMarkdown)
        task.update()

        try {
            log.debug("Building context from related files and dependencies")
            // Get context from related files and dependencies
            val context = buildContext(agent)
            // Update overview with context info
            overviewTask.add(buildString {
                appendLine()
                appendLine("✅ Context built successfully")
                appendLine()
            }.renderMarkdown)
            task.update()
            // Decomposition tab
            val decompositionTask = ui.newTask(false)
            tabs["Decomposition"] = decompositionTask.placeholder
            decompositionTask.add(buildString {
                appendLine("# Problem Decomposition")
                appendLine()
                appendLine("⏳ Analyzing problem structure...")
                appendLine()
                appendLine("**Strategy:** ${executionConfig.decomposition_strategy}")
                appendLine("**Max Depth:** ${executionConfig.max_depth}")
            }.renderMarkdown)
            task.update()
            log.info("Starting problem decomposition with strategy: ${executionConfig.decomposition_strategy}")

            // Decompose the problem
            val decomposition = decomposeProblem(
                problem = problem,
                strategy = executionConfig.decomposition_strategy,
                maxDepth = executionConfig.max_depth,
                currentDepth = 0,
                context = context,
                task = decompositionTask,
                api = api
            )
            log.info("Decomposition completed: ${decomposition.subproblems.size} subproblems identified")

            decompositionTask.add(buildString {
                appendLine()
                appendLine("✅ Decomposition complete!")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Results")
                appendLine()
                appendLine("**Rationale:** ${decomposition.decomposition_rationale}")
                appendLine()
                appendLine("### Subproblems Identified (${decomposition.subproblems.size})")
                appendLine()
                decomposition.subproblems.forEachIndexed { index, subproblem ->
                    appendLine("${index + 1}. **${subproblem.id}**: ${subproblem.description}")
                    appendLine("   - Complexity: ${subproblem.complexity}/10")
                    appendLine("   - Can Decompose Further: ${if (subproblem.can_decompose) "Yes" else "No"}")
                    appendLine()
                }
                appendLine("### Dependencies")
                appendLine()
                if (decomposition.dependencies.isEmpty()) {
                    appendLine("*No dependencies identified - subproblems can be solved independently*")
                } else {
                    decomposition.dependencies.entries.forEach { (id, deps) ->
                        appendLine("- **$id** depends on: ${deps.joinToString(", ")}")
                    }
                }
                appendLine()
            }.renderMarkdown)
            task.update()

            // Update overview
            overviewTask.add(buildString {
                appendLine("✅ Decomposition complete: ${decomposition.subproblems.size} subproblems identified")
                appendLine()
            }.renderMarkdown)
            task.update()

            // Subproblem Solutions tab
            val solutionsTask = ui.newTask(false)
            tabs["Subproblem Solutions"] = solutionsTask.placeholder
            solutionsTask.add(buildString {
                appendLine("# Subproblem Solutions")
                appendLine()
                appendLine("⏳ Solving ${decomposition.subproblems.size} subproblems...")
                appendLine()
            }.renderMarkdown)
            task.update()

            val solvedCount = AtomicInteger(0)
            log.info("Starting to solve ${decomposition.subproblems.size} subproblems")
            // Solve subproblems
            val solutions = solveSubproblems(
                decomposition = decomposition,
                context = context,
                task = solutionsTask,
                api = api,
                progressCallback = { subproblemId, solution ->
                    val count = solvedCount.incrementAndGet()
                    log.debug("Solved subproblem $count/${decomposition.subproblems.size}: $subproblemId")
                    solutionsTask.add(buildString {
                        appendLine()
                        appendLine("### ${count}. ${subproblemId}")
                        appendLine()
                        appendLine("**Confidence:** ${(solution.confidence * 100).toInt()}%")
                        appendLine()
                        appendLine(solution.solution)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Progress:** ${count}/${decomposition.subproblems.size} subproblems solved")
                        appendLine()
                    }.renderMarkdown)
                    task.update()

                    // Update overview
                    overviewTask.add(buildString {
                        appendLine("⏳ Solving subproblems: ${count}/${decomposition.subproblems.size}")
                        appendLine()
                    }.renderMarkdown)
                    task.update()
                }
            )

            solutionsTask.add(buildString {
                appendLine()
                appendLine("✅ All subproblems solved!")
                appendLine()
                appendLine("**Average Confidence:** ${(solutions.map { it.confidence }.average() * 100).toInt()}%")
                appendLine()
            }.renderMarkdown)
            task.update()

            // Update overview
            overviewTask.add(buildString {
                appendLine("✅ All ${solutions.size} subproblems solved")
                appendLine()
            }.renderMarkdown)
            task.update()

            // Synthesize solution if requested
            val finalResult = if (executionConfig.synthesize_solution) {
                // Synthesis tab
                val synthesisTask = ui.newTask(false)
                tabs["Synthesis"] = synthesisTask.placeholder
                synthesisTask.add(buildString {
                    appendLine("# Solution Synthesis")
                    appendLine()
                    appendLine("⏳ Integrating ${solutions.size} subproblem solutions...")
                    appendLine()
                }.renderMarkdown)
                task.update()
                log.info("Starting solution synthesis from ${solutions.size} subproblem solutions")
                val synthesized = synthesizeSolution(
                    problem = problem,
                    decomposition = decomposition,
                    solutions = solutions,
                    context = context,
                    task = synthesisTask,
                    api = api
                )
                log.info("Solution synthesis completed with confidence: ${synthesized.confidence}")

                synthesisTask.add(buildString {
                    appendLine()
                    appendLine("✅ Synthesis complete!")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## Synthesized Solution")
                    appendLine()
                    appendLine("**Synthesis Approach:** ${synthesized.synthesis_approach}")
                    appendLine()
                    appendLine("**Overall Confidence:** ${(synthesized.confidence * 100).toInt()}%")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine(synthesized.solution)
                    appendLine()
                }.renderMarkdown)
                task.update()

                // Update overview
                overviewTask.add(buildString {
                    appendLine("✅ Solution synthesized (confidence: ${(synthesized.confidence * 100).toInt()}%)")
                    appendLine()
                }.renderMarkdown)
                task.update()

                // Validate coherence if requested
                if (executionConfig.validate_coherence) {
                    // Validation tab
                    val validationTask = ui.newTask(false)
                    tabs["Validation"] = validationTask.placeholder
                    validationTask.add(buildString {
                        appendLine("# Coherence Validation")
                        appendLine()
                        appendLine("⏳ Validating solution coherence...")
                        appendLine()
                    }.renderMarkdown)
                    task.update()
                    log.info("Starting coherence validation")
                    val validation = validateCoherence(
                        problem = problem,
                        synthesized = synthesized,
                        solutions = solutions,
                        task = validationTask,
                        api = api
                    )
                    log.info("Validation completed: coherent=${validation.is_coherent}, issues=${validation.issues.size}")

                    validationTask.add(buildString {
                        appendLine()
                        appendLine("✅ Validation complete!")
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("## Results")
                        appendLine()
                        appendLine("**Is Coherent:** ${if (validation.is_coherent) "✅ Yes" else "❌ No"}")
                        appendLine()
                        if (validation.issues.isNotEmpty()) {
                            appendLine("### Issues Found (${validation.issues.size})")
                            appendLine()
                            validation.issues.forEach { issue ->
                                appendLine("- ⚠️ $issue")
                            }
                            appendLine()
                        }
                        if (validation.suggestions.isNotEmpty()) {
                            appendLine("### Suggestions for Improvement (${validation.suggestions.size})")
                            appendLine()
                            validation.suggestions.forEach { suggestion ->
                                appendLine("- 💡 $suggestion")
                            }
                            appendLine()
                        }
                        if (validation.issues.isEmpty() && validation.suggestions.isEmpty()) {
                            appendLine("*No issues or suggestions - solution is coherent and complete*")
                            appendLine()
                        }
                    }.renderMarkdown)
                    task.update()

                    // Update overview
                    overviewTask.add(buildString {
                        appendLine("✅ Validation complete: ${if (validation.is_coherent) "coherent" else "issues found"}")
                        appendLine()
                    }.renderMarkdown)
                    task.update()
                }

                synthesized.solution
            } else {
                log.info("Skipping synthesis, returning individual subproblem solutions")
                // Just return the subproblem solutions
                solutions.joinToString("\n\n") { "${it.subproblem_id}:\n${it.solution}" }
            }

            // Final summary in overview
            val totalTime = System.currentTimeMillis() - startTime
            log.info("DecompositionSynthesisTask completed successfully in ${totalTime}ms")

            overviewTask.add(buildString {
                appendLine("---")
                appendLine()
                appendLine("## ✅ Analysis Complete!")
                appendLine()
                appendLine("**Total Time:** ${totalTime / 1000} seconds")
                appendLine("**Subproblems Identified:** ${decomposition.subproblems.size}")
                appendLine("**Solutions Generated:** ${solutions.size}")
                appendLine("**Average Confidence:** ${(solutions.map { it.confidence }.average() * 100).toInt()}%")
                if (executionConfig.synthesize_solution) {
                    appendLine("**Synthesis:** ✅ Complete")
                }
                if (executionConfig.validate_coherence) {
                    appendLine("**Validation:** ✅ Complete")
                }
                appendLine()
            }.renderMarkdown)
            task.update()

            task.complete("Completed in ${totalTime / 1000} seconds")
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error in decomposition synthesis", e)

            // Update overview with error
            overviewTask.add(buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ❌ Error")
                appendLine()
                appendLine("**Error Type:** ${e.javaClass.simpleName}")
                appendLine("**Message:** ${e.message ?: "Unknown error"}")
                appendLine()
            }.renderMarkdown)
            task.update()

            task.error(e)
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun buildContext(agent: TaskOrchestrator): String {
        val priorCode = getPriorCode(agent.executionState)
        val relatedFiles = executionConfig?.related_files?.joinToString("\n") { "- $it" } ?: ""

        return """
            |## Context
            |
            |### Related Files
            |$relatedFiles
            |
            |### Previous Task Results
            |$priorCode
        """.trimMargin()
    }

    private fun decomposeProblem(
        problem: String,
        strategy: String,
        maxDepth: Int,
        currentDepth: Int,
        context: String,
        task: SessionTask,
        api: com.simiacryptus.cognotik.chat.model.ChatInterface
    ): ProblemDecomposition {
        val prompt = """
            |Decompose the following complex problem using a $strategy decomposition strategy.
            |
            |**Problem**: $problem
            |
            |**Context**:
            |$context
            |
            |**Decomposition Strategy**: $strategy
            |- functional: Break down by function/capability
            |- temporal: Break down by time/sequence
            |- spatial: Break down by location/component
            |- hierarchical: Break down by abstraction level
            |
            |**Current Depth**: $currentDepth / $maxDepth
            |
            |Identify 3-7 subproblems that together solve the original problem.
            |For each subproblem:
            |- Assign a unique ID (e.g., "SP1", "SP2")
            |- Provide a clear description
            |- Estimate complexity (1-10, where 10 is most complex)
            |- Indicate if it can be further decomposed (complexity > 7 and depth < maxDepth)
            |
            |Also identify dependencies between subproblems (which must be solved first).
        """.trimMargin()

        val decompositionAgent = ParsedAgent(
            resultClass = ProblemDecomposition::class.java,
            prompt = prompt,
            model = api,
            parsingModel = orchestrationConfig.parsingChatter,
        )

        var decomposition: ProblemDecomposition? = null
        Retryable(task, task.ui) { sb ->
            decomposition = decompositionAgent.answer(listOf(problem)).obj
            sb.append("Decomposition completed")
            sb.toString()
        }
        return decomposition!!
    }

    private fun solveSubproblems(
        decomposition: ProblemDecomposition,
        context: String,
        task: SessionTask,
        api: com.simiacryptus.cognotik.chat.model.ChatInterface,
        progressCallback: (String, SubproblemSolution) -> Unit = { _, _ -> }
    ): List<SubproblemSolution> {
        val solutions = mutableListOf<SubproblemSolution>()
        val solvedIds = mutableSetOf<String>()

        // Solve in dependency order, handling circular dependencies
        val (sortedSubproblems, circularDeps) = topologicalSortWithCycleDetection(decomposition)

        if (circularDeps.isNotEmpty()) {
            log.warn("Circular dependencies detected and resolved: ${circularDeps.joinToString(", ")}")
            task.add(buildString {
                appendLine()
                appendLine("⚠️ **Warning**: Circular dependencies detected and automatically resolved:")
                appendLine()
                circularDeps.forEach { cycle ->
                    appendLine("- $cycle")
                }
                appendLine()
                appendLine("Dependencies have been adjusted to allow execution to proceed.")
                appendLine()
            }.renderMarkdown)
            task.update()
        }

        log.info("Solving ${sortedSubproblems.size} subproblems in dependency order")

        for (subproblem in sortedSubproblems) {
            log.debug("Solving subproblem: ${subproblem.id} - ${subproblem.description}")
            val dependencySolutions = decomposition.dependencies[subproblem.id]
                ?.mapNotNull { depId -> solutions.find { it.subproblem_id == depId } }
                ?: emptyList()

            val prompt = """
                |Solve the following subproblem:
                |
                |**Subproblem ID**: ${subproblem.id}
                |**Description**: ${subproblem.description}
                |**Complexity**: ${subproblem.complexity}/10
                |
                |**Context**:
                |$context
                |
                |${
                if (dependencySolutions.isNotEmpty()) {
                    """
                    |**Dependency Solutions**:
                    |${
                        dependencySolutions.joinToString("\n\n") {
                            "- ${it.subproblem_id}: ${it.solution}"
                        }
                    }
                    """.trimMargin()
                } else ""
            }
                |
                |Provide a detailed solution to this subproblem.
                |Include your confidence level (0-1) in the solution.
            """.trimMargin()

            val solutionAgent = ParsedAgent(
                resultClass = SubproblemSolution::class.java,
                prompt = prompt,
                model = api!!,
                parsingModel = orchestrationConfig.parsingChatter,
            )

            var solution: SubproblemSolution? = null
            Retryable(task, task.ui) { sb ->
                solution = solutionAgent.answer(listOf(subproblem.description)).obj
                sb.append("Subproblem ${subproblem.id} solved")
                sb.toString()
            }

            val finalSolution = solution!!.copy(subproblem_id = subproblem.id)
            solutions.add(finalSolution)
            solvedIds.add(subproblem.id)
            // Call progress callback
            progressCallback(subproblem.id, finalSolution)
        }

        return solutions
    }

    private fun topologicalSortWithCycleDetection(decomposition: ProblemDecomposition): Pair<List<Subproblem>, List<String>> {
        val sorted = mutableListOf<Subproblem>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val circularDependencies = mutableListOf<String>()
        val brokenEdges = mutableSetOf<Pair<String, String>>()
        // Create a mutable copy of dependencies that we can modify
        val adjustedDependencies = decomposition.dependencies.mapValues { it.value.toMutableList() }.toMutableMap()

        fun visit(id: String, path: List<String> = emptyList()) {
            if (id in visited) return

            if (id in visiting) {
                // Circular dependency detected - find the cycle
                val cycleStart = path.indexOf(id)
                val cycle = path.subList(cycleStart, path.size) + id
                val cycleDesc = cycle.joinToString(" → ")
                circularDependencies.add(cycleDesc)
                log.warn("Circular dependency detected: $cycleDesc")

                // Break the cycle by removing the edge that closes the loop
                val lastInCycle = path.last()
                adjustedDependencies[lastInCycle]?.remove(id)
                brokenEdges.add(lastInCycle to id)
                log.info("Breaking dependency: $lastInCycle → $id")
                return
            }

            visiting.add(id)
            val newPath = path + id
            adjustedDependencies[id]?.forEach { depId ->
                if (depId to id !in brokenEdges) {
                    visit(depId, newPath)
                }
            }
            visiting.remove(id)
            visited.add(id)

            decomposition.subproblems.find { it.id == id }?.let { sorted.add(it) }
        }

        decomposition.subproblems.forEach { visit(it.id) }
        return sorted to circularDependencies
    }

    private fun synthesizeSolution(
        problem: String,
        decomposition: ProblemDecomposition,
        solutions: List<SubproblemSolution>,
        context: String,
        task: SessionTask,
        api: com.simiacryptus.cognotik.chat.model.ChatInterface
    ): SynthesizedSolution {
        val prompt = """
            |Synthesize a complete solution to the original problem by integrating the subproblem solutions.
            |
            |**Original Problem**: $problem
            |
            |**Decomposition Strategy**: ${executionConfig?.decomposition_strategy}
            |
            |**Subproblem Solutions**:
            |${
            solutions.joinToString("\n\n") { sol ->
                """
                |### ${sol.subproblem_id}
                |${decomposition.subproblems.find { it.id == sol.subproblem_id }?.description}
                |
                |**Solution**: ${sol.solution}
                |**Confidence**: ${sol.confidence}
                """.trimMargin()
            }
        }
            |
            |**Context**:
            |$context
            |
            |Create a coherent, integrated solution that:
            |1. Addresses the original problem completely
            |2. Properly integrates all subproblem solutions
            |3. Resolves any conflicts or overlaps
            |4. Provides a clear synthesis approach
            |5. Includes an overall confidence assessment
        """.trimMargin()

        val synthesisAgent = ParsedAgent(
            resultClass = SynthesizedSolution::class.java,
            prompt = prompt,
            model = api,
            parsingModel = orchestrationConfig.parsingChatter,
        )

        var synthesized: SynthesizedSolution? = null
        Retryable(task, task.ui) { sb ->
            synthesized = synthesisAgent.answer(listOf(problem)).obj
            sb.append("Solution synthesized")
            sb.toString()
        }
        return synthesized!!
    }

    private fun validateCoherence(
        problem: String,
        synthesized: SynthesizedSolution,
        solutions: List<SubproblemSolution>,
        task: SessionTask,
        api: com.simiacryptus.cognotik.chat.model.ChatInterface
    ): CoherenceValidation {
        val prompt = """
            |Validate the coherence of the synthesized solution.
            |
            |**Original Problem**: $problem
            |
            |**Synthesized Solution**: ${synthesized.solution}
            |
            |**Synthesis Approach**: ${synthesized.synthesis_approach}
            |
            |**Subproblem Solutions**:
            |${solutions.joinToString("\n") { "- ${it.subproblem_id}: confidence ${it.confidence}" }}
            |
            |Check for:
            |1. Logical consistency across the solution
            |2. Completeness (all aspects of the problem addressed)
            |3. Integration quality (subproblems properly combined)
            |4. Contradictions or conflicts
            |5. Missing elements or gaps
            |
            |Provide:
            |- Whether the solution is coherent (true/false)
            |- List of any issues found
            |- Suggestions for improvement
        """.trimMargin()

        val validationAgent = ParsedAgent(
            resultClass = CoherenceValidation::class.java,
            prompt = prompt,
            model = api!!,
            parsingModel = orchestrationConfig.parsingChatter,
        )

        var validation: CoherenceValidation? = null
        Retryable(task, task.ui) { sb ->
            validation = validationAgent.answer(listOf(synthesized.solution)).obj
            sb.append("Validation completed")
            sb.toString()
        }
        return validation!!
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DecompositionSynthesisTask::class.java)
        val DecompositionSynthesis = TaskType(
            "DecompositionSynthesis",
            DecompositionSynthesisTaskExecutionConfigData::class.java,
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
    }
}