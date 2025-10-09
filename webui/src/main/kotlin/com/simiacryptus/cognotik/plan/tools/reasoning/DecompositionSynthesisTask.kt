package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

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
        val problem = executionConfig?.complex_problem
        if (problem.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No problem specified")
            return
        }

        val newTask = task.ui.newTask(false)
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(
            MarkdownUtil.renderMarkdown(
                "## Decomposing Problem: ${problem.take(100)}${if (problem.length > 100) "..." else ""}",
                ui = ui
            )
        )

        try {
            // Get context from related files and dependencies
            val context = buildContext(agent)

            // Decompose the problem
            val decomposition = decomposeProblem(
                problem = problem,
                strategy = executionConfig.decomposition_strategy,
                maxDepth = executionConfig.max_depth,
                currentDepth = 0,
                context = context,
                task = newTask,
                api = api
            )

            newTask.add(
                MarkdownUtil.renderMarkdown(
                    """
                    |### Problem Decomposition
                    |
                    |**Strategy**: ${executionConfig.decomposition_strategy}
                    |
                    |**Rationale**: ${decomposition.decomposition_rationale}
                    |
                    |**Subproblems** (${decomposition.subproblems.size}):
                    |${decomposition.subproblems.joinToString("\n") { "- **${it.id}**: ${it.description} (complexity: ${it.complexity})" }}
                    |
                    |**Dependencies**:
                    |${
                        decomposition.dependencies.entries.joinToString("\n") { (id, deps) ->
                            "- $id depends on: ${
                                deps.joinToString(
                                    ", "
                                )
                            }"
                        }
                    }
                    """.trimMargin(),
                    ui = ui
                )
            )

            // Solve subproblems
            val solutions = solveSubproblems(
                decomposition = decomposition,
                context = context,
                task = newTask,
                api = api
            )

            newTask.add(
                MarkdownUtil.renderMarkdown(
                    """
                    |### Subproblem Solutions
                    |
                    |${
                        solutions.joinToString("\n\n") { sol ->
                            """
                        |#### ${sol.subproblem_id}
                        |**Confidence**: ${(sol.confidence * 100).toInt()}%
                        |
                        |${sol.solution}
                        """.trimMargin()
                        }
                    }
                    """.trimMargin(),
                    ui = ui
                )
            )

            // Synthesize solution if requested
            val finalResult = if (executionConfig.synthesize_solution) {
                val synthesized = synthesizeSolution(
                    problem = problem,
                    decomposition = decomposition,
                    solutions = solutions,
                    context = context,
                    task = newTask,
                    api = api
                )

                newTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
                        |### Synthesized Solution
                        |
                        |**Synthesis Approach**: ${synthesized.synthesis_approach}
                        |
                        |**Overall Confidence**: ${(synthesized.confidence * 100).toInt()}%
                        |
                        |${synthesized.solution}
                        """.trimMargin(),
                        ui = ui
                    )
                )

                // Validate coherence if requested
                if (executionConfig.validate_coherence) {
                    val validation = validateCoherence(
                        problem = problem,
                        synthesized = synthesized,
                        solutions = solutions,
                        task = newTask,
                        api = api
                    )

                    newTask.add(
                        MarkdownUtil.renderMarkdown(
                            """
                            |### Coherence Validation
                            |
                            |**Is Coherent**: ${if (validation.is_coherent) "✓ Yes" else "✗ No"}
                            |
                            |${if (validation.issues.isNotEmpty()) "**Issues**:\n${validation.issues.joinToString("\n") { "- $it" }}\n" else ""}
                            |${
                                if (validation.suggestions.isNotEmpty()) "**Suggestions**:\n${
                                    validation.suggestions.joinToString(
                                        "\n"
                                    ) { "- $it" }
                                }" else ""
                            }
                            """.trimMargin(),
                            ui = ui
                        )
                    )
                }

                synthesized.solution
            } else {
                // Just return the subproblem solutions
                solutions.joinToString("\n\n") { "${it.subproblem_id}:\n${it.solution}" }
            }

            newTask.complete()
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error in decomposition synthesis", e)
            newTask.error(e)
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun buildContext(agent: TaskOrchestrator): String {
        val priorCode = getPriorCode(agent.executionState!!)
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
        api: com.simiacryptus.cognotik.chat.model.ChatInterface
    ): List<SubproblemSolution> {
        val solutions = mutableListOf<SubproblemSolution>()
        val solvedIds = mutableSetOf<String>()

        // Solve in dependency order
        val sortedSubproblems = topologicalSort(decomposition)

        for (subproblem in sortedSubproblems) {
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

            solutions.add(solution!!.copy(subproblem_id = subproblem.id))
            solvedIds.add(subproblem.id)
        }

        return solutions
    }

    private fun topologicalSort(decomposition: ProblemDecomposition): List<Subproblem> {
        val sorted = mutableListOf<Subproblem>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()

        fun visit(id: String) {
            if (id in visited) return
            if (id in visiting) throw IllegalStateException("Circular dependency detected: $id")

            visiting.add(id)
            decomposition.dependencies[id]?.forEach { visit(it) }
            visiting.remove(id)
            visited.add(id)

            decomposition.subproblems.find { it.id == id }?.let { sorted.add(it) }
        }

        decomposition.subproblems.forEach { visit(it.id) }
        return sorted
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