package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.platform.ChatInterface
import com.simiacryptus.cognotik.platform.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.platform.model.ISessionTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class DecompositionSynthesisTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: DecompositionSynthesisTaskExecutionConfigData?
) : AbstractTask<DecompositionSynthesisTask.DecompositionSynthesisTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {
  val maxDescriptionLength = 1000

  companion object {
    private val log: Logger = getLogger(DecompositionSynthesisTask::class.java)

    @JvmStatic
    val DecompositionSynthesis: TaskType<DecompositionSynthesisTaskExecutionConfigData, TaskTypeConfig> = TaskType(
      name = "DecompositionSynthesis",
      category = "Reasoning",
      taskClass = DecompositionSynthesisTask::class.java,
      executionConfigClass = DecompositionSynthesisTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Decompose complex problems and synthesize solutions",
      tooltipHtml = """
                        Decomposes complex problems into manageable subproblems, solves them, and synthesizes solutions.
                        <ul>
                          <li>Multiple decomposition strategies (functional, temporal, spatial, hierarchical)</li>
                          <li>Configurable decomposition depth</li>
                          <li>Dependency-aware subproblem solving</li>
                          <li>Solution synthesis with coherence validation</li>
                          <li>Confidence tracking at each level</li>
                          <li>Implements divide-and-conquer reasoning</li>
                        </ul>
                      """,
    )
  }

  class DecompositionSynthesisTaskExecutionConfigData(
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    var related_files: List<String>? = null,
    @Description("Whether to include file context in the analysis")
    var include_file_context: Boolean = true,
    @Description("The complex problem to decompose")
    var complex_problem: String? = null,
    @Description("Decomposition strategy: 'functional', 'temporal', 'spatial', 'hierarchical'")
    var decomposition_strategy: String = "functional",
    @Description("Maximum decomposition depth")
    var max_depth: Int = 3,
    @Description("Whether to synthesize solutions from subproblems")
    var synthesize_solution: Boolean = true,
    @Description("Whether to validate synthesis coherence")
    var validate_coherence: Boolean = true,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = DecompositionSynthesis.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  class ProblemDecomposition(
    @Description("List of subproblems identified")
    var subproblems: List<Subproblem> = emptyList(),
    @Description("Rationale for this decomposition")
    var decomposition_rationale: String = "",
    @Description("Dependencies between subproblems")
    var dependencies: Map<String, List<String>> = emptyMap()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (subproblems.isEmpty()) return "ProblemDecomposition must have at least one subproblem"
      if (decomposition_rationale.isBlank()) return "ProblemDecomposition must have a decomposition_rationale"
      return ValidatedObject.validateFields(this)
    }
  }

  class Subproblem(
    @Description("Unique identifier for this subproblem")
    var id: String = "",
    @Description("Description of the subproblem")
    var description: String = "",
    @Description("Estimated complexity (1-10)")
    var complexity: Int = 5,
    @Description("Whether this can be further decomposed")
    var can_decompose: Boolean = false
  ) : ValidatedObject {
    override fun validate(): String? {
      if (id.isBlank()) return "Subproblem must have an id"
      if (description.isBlank()) return "Subproblem must have a description"
      if (complexity !in 1..10) return "Subproblem complexity must be between 1 and 10, got $complexity"
      return null
    }
  }

  class SubproblemSolution(
    @Description("The subproblem ID being solved")
    var subproblem_id: String = "",
    @Description("The solution to this subproblem")
    var solution: String = "",
    @Description("Confidence in this solution (0-1)")
    var confidence: Double = 0.0
  ) : ValidatedObject {
    override fun validate(): String? {
      if (subproblem_id.isBlank()) return "SubproblemSolution must have a subproblem_id"
      if (solution.isBlank()) return "SubproblemSolution must have a solution"
      if (confidence !in 0.0..1.0) return "SubproblemSolution confidence must be between 0 and 1, got $confidence"
      return null
    }
  }

  class SynthesizedSolution(
    @Description("The complete synthesized solution")
    var solution: String = "",
    @Description("How subproblem solutions were integrated")
    var synthesis_approach: String = "",
    @Description("Overall confidence in the solution (0-1)")
    var confidence: Double = 0.0
  ) : ValidatedObject {
    override fun validate(): String? {
      if (solution.isBlank()) return "SynthesizedSolution must have a solution"
      if (synthesis_approach.isBlank()) return "SynthesizedSolution must have a synthesis_approach"
      if (confidence !in 0.0..1.0) return "SynthesizedSolution confidence must be between 0 and 1, got $confidence"
      return null
    }
  }

  class CoherenceValidation(
    @Description("Whether the solution is coherent")
    var is_coherent: Boolean = false,
    @Description("Issues found in the synthesis")
    var issues: List<String> = emptyList(),
    @Description("Suggestions for improvement")
    var suggestions: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return """
 DecompositionSynthesis - Break down complex problems into subproblems and synthesize integrated solutions
  ** Optionally, list input files (supports glob patterns) to be examined for context
  ** Problem: ${executionConfig?.complex_problem?.take(100) ?: "Not specified"}
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
  ** Output: Comprehensive solution with decomposition analysis, subproblem solutions, and synthesis
  ** Returns: Final synthesized solution or concatenated subproblem solutions
  ** Implements divide-and-conquer reasoning approach
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    task.pool.submit {
      val startTime = System.currentTimeMillis()
      log.info(
        "Starting DecompositionSynthesisTask with problem: ${
          executionConfig?.complex_problem?.take(
            maxDescriptionLength
          )
        }"
      )

      val problem = executionConfig?.complex_problem
      if (problem.isNullOrBlank()) {
        log.error("No problem specified in execution config")
        task.complete("CONFIGURATION ERROR: No problem specified")
        resultFn("CONFIGURATION ERROR: No problem specified")
        return@submit
      }

      // Create tabbed display for organized output
      val tabs = TabbedDisplay(task)
      val transcriptStream = try {
        task.newUserFileStream(transcriptFile("decomposition_transcript"))
      } catch (e: Exception) {
        log.error("Failed to initialize transcript", e)
        null
      }
      val api = defaultSmart ?: run {
        log.error("No default chatter available")
        task.complete("ERROR: No API available")
        resultFn("ERROR: No API available")
        return@submit
      }


      // Overview tab
      val overviewTask = task.newTask()
      tabs["Overview"] = overviewTask.placeholder

      val overviewContent = buildString {
        appendLine("# Decomposition & Synthesis Analysis")
        appendLine()
        appendLine("**Problem:** ${problem.take(maxDescriptionLength)}${if (problem.length > maxDescriptionLength) "..." else ""}")
        appendLine()
        appendLine("**Strategy:** ${executionConfig?.decomposition_strategy}")
        appendLine("**Max Depth:** ${executionConfig?.max_depth}")
        appendLine("**Synthesize Solution:** ${executionConfig?.synthesize_solution}")
        appendLine("**Validate Coherence:** ${executionConfig?.validate_coherence}")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## Progress")
        appendLine()
        appendLine("⏳ Starting decomposition analysis...")
      }
      overviewTask.add(overviewContent.renderMarkdown())
      transcriptStream?.let { writeToTranscript(it, overviewContent) }

      try {
        // Step 3: Build context from related files and dependencies
        log.debug("Building context from related files and dependencies")
        // Get context from related files and dependencies
        val context = buildContext(agent, root)
        // Context tab
        val contextTask = task.newTask()
        tabs["Context"] = contextTask.placeholder
        contextTask.add(buildString {
          appendLine("# Task Context")
          appendLine()
          appendLine("The following context, derived from previous tasks and related files, will be used to inform the analysis.")
          appendLine()
          appendLine("---")
          appendLine()
          appendLine(context)
        }.renderMarkdown())
        transcriptStream?.let {
          writeToTranscript(it, buildString {
            appendLine("# Task Context")
            appendLine("<details><summary>Raw Context Data</summary>\n")
            appendLine(context)
            appendLine("\n</details>")
          })
        }

        // Update overview with context info
        overviewTask.add(buildString {
          appendLine()
          appendLine("✅ Context built successfully")
          appendLine()
        }.renderMarkdown())
        transcriptStream?.let { writeToTranscript(it, "\n✅ Context built successfully\n\n") }
        // Step 4: Decompose the problem
        // Decomposition tab
        val decompositionTask = task.newTask()
        tabs["Decomposition"] = decompositionTask.placeholder
        decompositionTask.add(buildString {
          appendLine("# Problem Decomposition")
          appendLine()
          appendLine("⏳ Analyzing problem structure...")
          appendLine()
          appendLine("**Strategy:** ${executionConfig?.decomposition_strategy}")
          appendLine("**Max Depth:** ${executionConfig?.max_depth}")
        }.renderMarkdown())
        transcriptStream?.let {
          writeToTranscript(it, buildString {
            appendLine("# Problem Decomposition")
            appendLine()
            appendLine("**Strategy:** ${executionConfig?.decomposition_strategy}")
            appendLine("**Max Depth:** ${executionConfig?.max_depth}")
          })
        }
        log.info("Starting problem decomposition with strategy: ${executionConfig?.decomposition_strategy}")

        val decomposition = decomposeProblem(
          problem = problem,
          strategy = executionConfig?.decomposition_strategy ?: "functional",
          maxDepth = executionConfig?.max_depth ?: 3,
          currentDepth = 0,
          context = context,
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
        }.renderMarkdown())
        transcriptStream?.let {
          writeToTranscript(it, buildString {
            appendLine()
            appendLine("## Decomposition Results")
            appendLine()
            appendLine("**Rationale:** ${decomposition.decomposition_rationale}")
            appendLine()
            appendLine("### Subproblems (${decomposition.subproblems.size})")
            decomposition.subproblems.forEachIndexed { index, subproblem ->
              appendLine("${index + 1}. **${subproblem.id}**: ${subproblem.description}")
              appendLine("   - Complexity: ${subproblem.complexity}/10")
            }
            appendLine()
            appendLine("### Dependencies")
            if (decomposition.dependencies.isEmpty()) {
              appendLine("*No dependencies*")
            } else {
              decomposition.dependencies.entries.forEach { (id, deps) ->
                appendLine(
                  "- **$id** → ${
                    deps.joinToString(
                      ", "
                    )
                  }"
                )
              }
            }
          })
        }
        // Step 5: Solve all subproblems

        // Update overview
        overviewTask.add(buildString {
          appendLine("✅ Decomposition complete: ${decomposition.subproblems.size} subproblems identified")
          appendLine()
        }.renderMarkdown())
        transcriptStream?.let {
          writeToTranscript(
            it,
            "\n✅ Decomposition complete: ${decomposition.subproblems.size} subproblems\n\n"
          )
        }

        // Subproblem Solutions tab
        val solutionsTask = task.newTask()
        tabs["Subproblem Solutions"] = solutionsTask.placeholder
        solutionsTask.add(buildString {
          appendLine("# Subproblem Solutions")
          appendLine()
          appendLine("⏳ Solving ${decomposition.subproblems.size} subproblems...")
          appendLine()
        }.renderMarkdown())
        transcriptStream?.let {
          writeToTranscript(it, buildString {
            appendLine("# Subproblem Solutions")
            appendLine()
            appendLine("Solving ${decomposition.subproblems.size} subproblems...")
          })
        }

        val solvedCount = AtomicInteger(0)
        log.info("Starting to solve ${decomposition.subproblems.size} subproblems")
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
            }.renderMarkdown())
            transcriptStream?.let {
              writeToTranscript(it, buildString {
                appendLine()
                appendLine("## ${count}. ${subproblemId}")
                appendLine()
                appendLine("**Confidence:** ${(solution.confidence * 100).toInt()}%")
                appendLine()
                appendLine(solution.solution)
              })
            }

            // Update overview
            overviewTask.add(buildString {
              appendLine("⏳ Solving subproblems: ${count}/${decomposition.subproblems.size}")
              appendLine()
            }.renderMarkdown())
          }
        )


        solutionsTask.add(buildString {
          appendLine()
          appendLine("✅ All subproblems solved!")
          appendLine()
          appendLine("**Average Confidence:** ${(solutions.map { it.confidence }.average() * 100).toInt()}%")
          appendLine()
        }.renderMarkdown())
        transcriptStream?.let {
          writeToTranscript(it, buildString {
            appendLine()
            appendLine(
              "✅ All subproblems solved! Average confidence: ${
                (solutions.map { it.confidence }.average() * 100).toInt()
              }%"
            )
          })
        }
        // Step 6: Synthesize solution (if requested)

        // Update overview
        overviewTask.add(buildString {
          appendLine("✅ All ${solutions.size} subproblems solved")
          appendLine()
        }.renderMarkdown())
        transcriptStream?.let { writeToTranscript(it, "\n✅ All ${solutions.size} subproblems solved\n\n") }

        val finalResult = if (executionConfig?.synthesize_solution == true) {
          // Synthesis tab
          val synthesisTask = task.newTask()
          tabs["Synthesis"] = synthesisTask.placeholder
          synthesisTask.add(buildString {
            appendLine("# Solution Synthesis")
            appendLine()
            appendLine("⏳ Integrating ${solutions.size} subproblem solutions...")
            appendLine()
          }.renderMarkdown())
          transcriptStream?.let {
            writeToTranscript(it, buildString {
              appendLine("# Solution Synthesis")
              appendLine()
              appendLine("Integrating ${solutions.size} subproblem solutions...")
            })
          }
          log.info("Starting solution synthesis from ${solutions.size} subproblem solutions")
          val synthesized = synthesizeSolution(
            problem = problem,
            decomposition = decomposition,
            solutions = solutions,
            context = context,
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
          }.renderMarkdown())
          transcriptStream?.let {
            writeToTranscript(it, buildString {
              appendLine()
              appendLine("## Synthesized Solution")
              appendLine()
              appendLine("**Synthesis Approach:** ${synthesized.synthesis_approach}")
              appendLine("**Confidence:** ${(synthesized.confidence * 100).toInt()}%")
              appendLine()
              appendLine(synthesized.solution)
            })
          }

          // Update overview
          overviewTask.add(buildString {
            appendLine("✅ Solution synthesized (confidence: ${(synthesized.confidence * 100).toInt()}%)")
            appendLine()
          }.renderMarkdown())
          transcriptStream?.let {
            writeToTranscript(
              it,
              "\n✅ Solution synthesized (confidence: ${(synthesized.confidence * 100).toInt()}%)\n\n"
            )
          }
          // Step 7: Validate coherence (if requested)

          // Validate coherence if requested
          if (executionConfig?.validate_coherence == true) {
            // Validation tab
            val validationTask = task.newTask()
            tabs["Validation"] = validationTask.placeholder
            validationTask.add(buildString {
              appendLine("# Coherence Validation")
              appendLine()
              appendLine("⏳ Validating solution coherence...")
              appendLine()
            }.renderMarkdown())
            transcriptStream?.let {
              writeToTranscript(it, buildString {
                appendLine("# Coherence Validation")
                appendLine()
              })
            }
            log.info("Starting coherence validation")
            val validation = validateCoherence(
              problem = problem,
              synthesized = synthesized,
              solutions = solutions,
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
            }.renderMarkdown())
            transcriptStream?.let {
              writeToTranscript(it, buildString {
                appendLine()
                appendLine("## Validation Results")
                appendLine()
                appendLine("**Is Coherent:** ${if (validation.is_coherent) "Yes" else "No"}")
                if (validation.issues.isNotEmpty()) {
                  appendLine()
                  appendLine("### Issues (${validation.issues.size})")
                  validation.issues.forEach { appendLine("- $it") }
                }
                if (validation.suggestions.isNotEmpty()) {
                  appendLine()
                  appendLine("### Suggestions (${validation.suggestions.size})")
                  validation.suggestions.forEach { appendLine("- $it") }
                }
              })
            }

            // Update overview
            overviewTask.add(buildString {
              appendLine("✅ Validation complete: ${if (validation.is_coherent) "coherent" else "issues found"}")
              appendLine()
            }.renderMarkdown())
            transcriptStream?.let { writeToTranscript(it, "\n✅ Validation complete\n\n") }
          }

          synthesized.solution
        } else {
          log.info("Skipping synthesis, returning individual subproblem solutions")
          overviewTask.add(
            "ℹ️ Synthesis skipped - returning individual solutions\n\n".renderMarkdown()
          )
          // Just return the subproblem solutions
          solutions.joinToString("\n\n") { "${it.subproblem_id}:\n${it.solution}" }
        }
        // Step 8: Finalize and return results

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
          if (executionConfig?.synthesize_solution == true) {
            appendLine("**Synthesis:** ✅ Complete")
          }
          if (executionConfig?.validate_coherence == true) {
            appendLine("**Validation:** ✅ Complete")
          }
          appendLine()
        }.renderMarkdown())
        transcriptStream?.let {
          writeToTranscript(it, buildString {
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Analysis Complete")
            appendLine()
            appendLine("**Total Time:** ${totalTime / 1000}s")
            appendLine("**Subproblems:** ${decomposition.subproblems.size}")
            appendLine("**Solutions:** ${solutions.size}")
            appendLine("**Avg Confidence:** ${(solutions.map { it.confidence }.average() * 100).toInt()}%")
          })
        }

        val summary =
          "Decomposition & Synthesis completed: ${decomposition.subproblems.size} subproblems, ${solutions.size} solutions in ${totalTime / 1000}s"
        task.complete(summary)
        resultFn(finalResult)

      } catch (e: Exception) {
        // Triple Log Rule
        log.error("Error in decomposition synthesis", e)
        task.error(e)
        transcriptStream?.let {
          writeToTranscript(it, buildString {
            appendLine("\n\n## ERROR")
            appendLine("> ${e.message}")
            appendLine("```")
            appendLine(e.stackTraceToString())
            appendLine("```")
          })
        }

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
        }.renderMarkdown())


        resultFn("ERROR: ${e.message}")
      } finally {
        transcriptStream?.flush()
        transcriptStream?.close()
        log.debug("Transcript closed")
      }
    }
  }


  private fun buildContext(agent: TaskOrchestrator, root: Path): String {
    log.debug("Building context from related files and prior code")
    val priorCode = getPriorCode(agent.executionState)
    val relatedFiles = executionConfig?.related_files?.joinToString("\n") { "- $it" } ?: ""
    val fileContext = if (executionConfig?.include_file_context == true) {
      getInputFileCode(root)
    } else {
      ""
    }
    return "\n## Context\n\n### Related Files\n$relatedFiles\n\n### Input Files\n$fileContext\n\n### Previous Task Results\n$priorCode\n        "
  }

  private fun getInputFileCode(root: Path): String = (executionConfig?.related_files ?: listOf())
    .flatMap { pattern: String ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      (FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          FileSelectionUtils.isIgnored(it.toPath()) -> false
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

  private fun decomposeProblem(
    problem: String,
    strategy: String,
    maxDepth: Int,
    currentDepth: Int,
    context: String,
    api: ChatInterface
  ): ProblemDecomposition {
    log.debug("Decomposing problem at depth $currentDepth/$maxDepth using $strategy strategy")
    val prompt = """
            |You are an expert systems analyst. Your task is to decompose the following complex problem using a $strategy decomposition strategy.
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
      parsingModel = defaultFast,
    )

    return decompositionAgent.answer(listOf(problem)).obj
  }

  private fun solveSubproblems(
    decomposition: ProblemDecomposition,
    context: String,
    task: ISessionTask,
    api: ChatInterface,
    progressCallback: (String, SubproblemSolution) -> Unit = { _, _ -> }
  ): List<SubproblemSolution> {
    log.debug("Starting to solve ${decomposition.subproblems.size} subproblems")
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
      }.let { MarkdownUtil.renderMarkdown(it) })
    }

    log.info("Solving ${sortedSubproblems.size} subproblems in dependency order")

    for (subproblem in sortedSubproblems) {
      log.debug("Solving subproblem: ${subproblem.id} - ${subproblem.description}")
      val dependencySolutions = decomposition.dependencies[subproblem.id]
        ?.mapNotNull { depId -> solutions.find { it.subproblem_id == depId } }
        ?: emptyList()

      val prompt = """
                |You are a meticulous and expert problem solver. Your task is to solve the following subproblem, considering all provided context and dependencies.
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
        model = api,
        parsingModel = defaultFast,
      )

      val solution = solutionAgent.answer(listOf(subproblem.description)).obj

      val finalSolution = solution.jsonCopy().apply {
        subproblem_id = subproblem.id
      }
      solutions.add(finalSolution)
      solvedIds.add(subproblem.id)
      // Call progress callback
      progressCallback(subproblem.id, finalSolution)
    }

    return solutions
  }

  private fun topologicalSortWithCycleDetection(decomposition: ProblemDecomposition): Pair<List<Subproblem>, List<String>> {
    log.debug("Performing topological sort with cycle detection")
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
    log.debug("Topological sort complete: ${sorted.size} subproblems, ${circularDependencies.size} cycles detected")
    return sorted to circularDependencies
  }

  private fun synthesizeSolution(
    problem: String,
    decomposition: ProblemDecomposition,
    solutions: List<SubproblemSolution>,
    context: String,
    api: ChatInterface
  ): SynthesizedSolution {
    log.debug("Synthesizing solution from ${solutions.size} subproblem solutions")
    val prompt = """
            |You are a master synthesizer of information. Your task is to create a single, coherent solution to the original problem by integrating the provided subproblem solutions.
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
      parsingModel = defaultFast,
    )
    return synthesisAgent.answer(listOf(problem)).obj
  }

  private fun validateCoherence(
    problem: String,
    synthesized: SynthesizedSolution,
    solutions: List<SubproblemSolution>,
    api: ChatInterface
  ): CoherenceValidation {
    log.debug("Validating coherence of synthesized solution")
    val prompt = """
            |You are a critical reviewer and quality assurance specialist. Your task is to validate the coherence of the synthesized solution.
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
      model = api,
      parsingModel = defaultFast,
    )

    return validationAgent.answer(listOf(synthesized.solution)).obj
  }

  override fun writeToTranscript(it: FileOutputStream, buildString: String) {
    try {
      it.write(buildString.toByteArray())
      it.write("\n\n".toByteArray())
      it.flush()
    } catch (e: Exception) {
      log.error("Failed to write to transcript", e)
    }
  }

}

