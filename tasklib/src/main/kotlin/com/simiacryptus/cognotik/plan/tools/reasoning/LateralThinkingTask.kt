package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.*
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LateralThinkingTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: LateralThinkingTaskExecutionConfigData?
) :
  AbstractTask<LateralThinkingTask.LateralThinkingTaskExecutionConfigData, LateralThinkingTask.LateralThinkingTaskTypeConfig>(
    orchestrationConfig,
    planTask
  ) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(LateralThinkingTask::class.java)

    @JvmStatic
    val LateralThinking = TaskType(
      name = "LateralThinking",
      category = "Reasoning",
      taskClass = LateralThinkingTask::class.java,
      executionConfigClass = LateralThinkingTaskExecutionConfigData::class.java,
      taskSettingsClass = LateralThinkingTaskTypeConfig::class.java,
      description = "Break conventional thinking patterns to find innovative solutions",
      tooltipHtml = """
                        Applies lateral thinking techniques to generate unconventional solutions.
                        <ul>
                          <li>Supports multiple techniques: reversal, random stimulus, challenge assumptions, exaggeration, escape, metaphor, provocation</li>
                          <li>Generates multiple alternatives per technique</li>
                          <li>Identifies breakthrough aspects and novel perspectives</li>
                          <li>Evaluates novelty and feasibility of ideas</li>
                          <li>Synthesizes insights across techniques</li>
                          <li>Optionally performs detailed feasibility evaluation</li>
                          <li>Suggests hybrid approaches combining multiple ideas</li>
                          <li>Ideal for innovation, breaking design impasses, and creative problem-solving</li>
                        </ul>
                      """,
    )
  }

  val maxDescriptionLength = 1500

  class LateralThinkingTaskExecutionConfigData(
    @Description("The problem or challenge to approach with lateral thinking")
    var problem: String? = null,
    @Description("Lateral thinking techniques to apply: reversal, random_stimulus, challenge_assumptions, exaggeration, escape, metaphor, provocation")
    var techniques: List<String>? = listOf(
      "reversal",
      "random_stimulus",
      "challenge_assumptions",
      "exaggeration",
      "escape"
    ),
    @Description("Number of alternative solutions to generate per technique")
    var num_alternatives: Int = 5,
    @Description("Whether to evaluate the feasibility of generated ideas")
    var evaluate_feasibility: Boolean = true,
    @Description("Domain or context to constrain the thinking (optional)")
    var domain_context: String? = null,
    @Description("Additional constraints or requirements to consider")
    var constraints: List<String>? = null,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input context for the task")
    var related_files: List<String>? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = LateralThinking.name,
    task_description = task_description
      ?: "Apply lateral thinking to: ${problem?.take(100)}${if (problem?.length ?: 0 > 100) "..." else ""}",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {

    override fun validate(): String? {
      if (problem.isNullOrBlank()) {
        return "Problem must be specified and cannot be blank"
      }
      if (num_alternatives < 1 || num_alternatives > 10) {
        return "Number of alternatives must be between 1 and 10, got: $num_alternatives"
      }
      techniques?.forEach { technique ->
        val validTechniques = listOf(
          "reversal", "random_stimulus", "challenge_assumptions",
          "exaggeration", "escape", "metaphor", "provocation"
        )
        if (technique !in validTechniques) {
          return "Invalid technique '$technique'. Valid techniques are: ${validTechniques.joinToString(", ")}"
        }
      }
      return ValidatedObject.validateFields(this)
    }
  }

  class LateralThinkingTaskTypeConfig(
    task_type: String? = LateralThinking.name,
    name: String? = null,
    model: ApiChatModel? = null
  ) : TaskTypeConfig(
    task_type = task_type,
    name = name,
    model = model
  ), ValidatedObject

  data class LateralIdea(
    @Description("Title of the idea")
    val title: String = "",
    @Description("The lateral thinking technique used")
    val technique: String = "",
    @Description("Detailed description of the idea")
    val description: String = "",
    @Description("The provocation or stimulus that led to this idea")
    val provocation: String = "",
    @Description("How this breaks conventional thinking")
    val breakthrough_aspect: String = "",
    @Description("Potential benefits of this approach")
    val benefits: List<String> = emptyList(),
    @Description("Potential challenges or risks")
    val challenges: List<String> = emptyList(),
    @Description("Concrete steps to implement this idea")
    val implementation_steps: List<String> = emptyList(),
    @Description("Novelty score (0-1)")
    val novelty_score: Double = 0.0,
    @Description("Feasibility score (0-1)")
    val feasibility_score: Double = 0.0
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) {
        return "LateralIdea title cannot be blank"
      }
      if (technique.isBlank()) {
        return "LateralIdea technique cannot be blank"
      }
      if (description.isBlank()) {
        return "LateralIdea description cannot be blank"
      }
      if (novelty_score < 0.0 || novelty_score > 1.0) {
        return "LateralIdea novelty_score must be between 0.0 and 1.0, got: $novelty_score"
      }
      if (feasibility_score < 0.0 || feasibility_score > 1.0) {
        return "LateralIdea feasibility_score must be between 0.0 and 1.0, got: $feasibility_score"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class TechniqueApplication(
    @Description("The technique name")
    val technique: String = "",
    @Description("Description of how the technique was applied")
    val application_description: String = "",
    @Description("The provocation or reframing used")
    val provocation: String = "",
    @Description("Ideas generated from this technique")
    val ideas: List<LateralIdea> = emptyList(),
    @Description("Key insights from applying this technique")
    val insights: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (technique.isBlank()) {
        return "TechniqueApplication technique cannot be blank"
      }
      if (application_description.isBlank()) {
        return "TechniqueApplication application_description cannot be blank"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class FeasibilityEvaluation(
    @Description("Overall feasibility assessment")
    val overall_assessment: String = "",
    @Description("Most promising ideas ranked by feasibility")
    val top_ideas: List<String> = emptyList(),
    @Description("Ideas requiring further exploration")
    val ideas_for_exploration: List<String> = emptyList(),
    @Description("Hybrid approaches combining multiple ideas")
    val hybrid_approaches: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (overall_assessment.isBlank()) {
        return "FeasibilityEvaluation overall_assessment cannot be blank"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class LateralThinkingResult(
    @Description("Applications of each technique")
    val technique_applications: List<TechniqueApplication> = emptyList(),
    @Description("All generated ideas across techniques")
    val all_ideas: List<LateralIdea> = emptyList(),
    @Description("Synthesized insights across all techniques")
    val synthesized_insights: List<String> = emptyList(),
    @Description("Recommended unconventional approaches")
    val recommended_approaches: List<String> = emptyList(),
    @Description("Feasibility evaluation if requested")
    val feasibility_evaluation: FeasibilityEvaluation? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (technique_applications.isEmpty()) {
        return "LateralThinkingResult must have at least one technique application"
      }
      if (all_ideas.isEmpty()) {
        return "LateralThinkingResult must have at least one idea"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return """
LateralThinking - Break conventional thinking patterns to find innovative solutions
  ** Specify the problem or challenge to approach creatively
  ** Select lateral thinking techniques to apply:
     - reversal: Reverse the problem or goal
     - random_stimulus: Apply unrelated concepts
     - challenge_assumptions: Question fundamental assumptions
     - exaggeration: Amplify aspects to extremes
     - escape: Temporarily ignore key constraints
     - metaphor: Use metaphorical thinking
     - provocation: Use deliberate provocations
  ** Configure number of alternatives per technique (default: 5)
  ** Optionally evaluate feasibility of generated ideas
  ** The task will:
     - Apply each selected technique systematically
     - Generate unconventional alternatives
     - Identify breakthrough aspects
     - Synthesize insights across techniques
     - Evaluate feasibility if requested
  ** Useful for innovation, breaking design impasses, and creative problem-solving
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    task.ui.pool.submit {
      val transcript = task.newUserFileStream(transcriptFile())
      try {
        val startTime = System.currentTimeMillis()
        log.info("Starting LateralThinkingTask for problem='${executionConfig?.problem?.take(50)}...', techniques=${executionConfig?.techniques}")

        val problem = executionConfig?.problem
        if (problem.isNullOrBlank()) {
          log.error("Configuration error: problem is blank")
          task.safeComplete("CONFIGURATION ERROR: Problem must be specified", log)
          task.error(RuntimeException("Configuration error: problem is blank"))
          resultFn("CONFIGURATION ERROR: Problem must be specified")
          return@submit
        }

        val executionConfig = this.executionConfig ?: LateralThinkingTaskExecutionConfigData(problem = problem)
        val techniques = executionConfig.techniques ?: listOf(
          "reversal",
          "random_stimulus",
          "challenge_assumptions",
          "exaggeration",
          "escape"
        )
        val numAlternatives = (executionConfig.num_alternatives ?: 5).coerceIn(1, 10)
        val evaluateFeasibility = executionConfig.evaluate_feasibility ?: true
        val domainContext = executionConfig.domain_context
        val constraints = executionConfig.constraints

        log.info("Configuration: techniques=${techniques.size}, numAlternatives=$numAlternatives, evaluateFeasibility=$evaluateFeasibility")

        val tabs = TabbedDisplay(task)

        // Create overview tab
        val overviewTask = tabs.newTask("Overview")
        val overviewContent = buildString {
          appendLine("# Lateral Thinking Task")
          appendLine()
          appendLine(
            "**Started:** ${
              LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }"
          )
          appendLine()
          appendLine("## Problem Statement")
          appendLine()
          appendLine("> $problem")
          appendLine()
          if (domainContext != null) {
            appendLine("**Domain Context:** $domainContext")
            appendLine()
          }
          if (!constraints.isNullOrEmpty()) {
            appendLine("**Constraints:**")
            constraints.forEach { appendLine("- $it") }
            appendLine()
          }
          appendLine("## Configuration")
          appendLine()
          appendLine("| Parameter | Value |")
          appendLine("|-----------|-------|")
          appendLine("| Techniques | ${techniques.joinToString(", ")} |")
          appendLine("| Alternatives per Technique | $numAlternatives |")
          appendLine("| Feasibility Evaluation | ${if (evaluateFeasibility) "✓ Enabled" else "✗ Disabled"} |")
          appendLine()
          transcript?.write(this.toString().toByteArray())
          appendLine("## Progress")
          appendLine()
          appendLine("- ⏳ Gathering context...")
        }
        overviewTask.add(overviewContent.renderMarkdown(true))

        log.debug("Gathering prior context")
        val priorContext = getPriorCode(agent.executionState)
        val fileContext = getInputFileContent(executionConfig.related_files, agent.root)
        val combinedContext = priorContext + "\n\n" + fileContext
        log.debug("Context gathered: priorContext length=${priorContext.length}, fileContext length=${fileContext.length}")
        transcript?.write("## Context\n<details>\n<summary>Input Context</summary>\n\n$combinedContext\n</details>\n".toByteArray())

        overviewTask.add(buildString {
          appendLine()
          transcript?.write("\n- ✓ Context gathered\n- ⏳ Applying lateral thinking techniques...\n".toByteArray())
          appendLine("- ✓ Context gathered")
          appendLine("- ⏳ Applying lateral thinking techniques...")
        }.renderMarkdown(true))

        // Step 1: Apply each technique
        log.info("Starting technique application phase")
        val techniqueApplications = mutableListOf<TechniqueApplication>()
        val allIdeas = mutableListOf<LateralIdea>()

        techniques.forEachIndexed { index, technique ->
          log.info("Applying technique ${index + 1}/${techniques.size}: $technique")

          val techniqueTask = tabs.newTask("${index + 1}. ${technique.capitalize()}")

          techniqueTask.add(buildString {
            appendLine("# ${technique.capitalize()} Technique")
            appendLine()
            transcript?.write("# ${technique.capitalize()} Technique\n\n**Status:** ⏳ Generating ideas...\n\n".toByteArray())
            appendLine("**Status:** ⏳ Generating ideas...")
            appendLine()
            appendLine(getTechniqueDescription(technique))
          }.renderMarkdown(true))

          val techniquePrompt = buildTechniquePrompt(
            technique,
            problem,
            numAlternatives,
            domainContext,
            constraints,
            combinedContext
          )

          val techniqueParser = ParsedAgent(
            resultClass = TechniqueApplication::class.java,
            prompt = techniquePrompt,
            model = defaultSmart.getChildClient(task),
            temperature = 0.8,
            name = "LateralThinking_${technique}",
            parsingChatter = defaultFast,
          )

          val application = techniqueParser.answer(listOf(techniquePrompt)).obj

          if (application != null) {
            techniqueApplications.add(application)
            allIdeas.addAll(application.ideas)
            log.info("Technique $technique generated ${application.ideas.size} ideas")

            // Display technique results
            techniqueTask.add(buildString {
              transcript?.write("\n---\n\n## Results\n\n**Status:** ✓ Complete\n\n".toByteArray())
              appendLine()
              appendLine("---")
              appendLine()
              appendLine("## Results")
              appendLine()
              appendLine("**Status:** ✓ Complete")
              appendLine()
              appendLine("### Provocation")
              appendLine()
              appendLine("> ${application.provocation}")
              appendLine()
              appendLine("### Application")
              appendLine()
              appendLine(application.application_description)
              appendLine()
              appendLine("### Generated Ideas (${application.ideas.size})")
              appendLine()
              application.ideas.forEachIndexed { ideaIndex, idea ->
                appendLine("#### ${ideaIndex + 1}. ${idea.title}")
                appendLine()
                appendLine(
                  "**Novelty:** ${String.format("%.1f%%", idea.novelty_score * 100)} | **Feasibility:** ${
                    String.format(
                      "%.1f%%",
                      idea.feasibility_score * 100
                    )
                  }"
                )
                appendLine()
                appendLine(idea.description)
                appendLine()
                appendLine("**Breakthrough Aspect:** ${idea.breakthrough_aspect}")
                appendLine()
                if (idea.benefits.isNotEmpty()) {
                  appendLine("**Benefits:**")
                  idea.benefits.take(3).forEach { appendLine("- $it") }
                  if (idea.benefits.size > 3) appendLine("- *...and ${idea.benefits.size - 3} more*")
                  appendLine()
                }
                if (idea.challenges.isNotEmpty()) {
                  appendLine("**Challenges:**")
                  idea.challenges.take(3).forEach { appendLine("- $it") }
                  if (idea.challenges.size > 3) appendLine("- *...and ${idea.challenges.size - 3} more*")
                  appendLine()
                }
                appendLine("---")
                appendLine()
              }
              transcript?.write(this.toString().toByteArray())
              if (application.insights.isNotEmpty()) {
                appendLine("### Key Insights")
                appendLine()
                application.insights.forEach { appendLine("- $it") }
              }
            }.renderMarkdown(true))
          } else {
            log.warn("Failed to generate ideas for technique: $technique")
            transcript?.write("\n**Status:** ⚠️ Failed to generate ideas\n".toByteArray())
            techniqueTask.add(buildString {
              appendLine()
              appendLine("**Status:** ⚠️ Failed to generate ideas")
            }.renderMarkdown(true))
          }
          techniqueTask.complete()

          overviewTask.add(buildString {
            appendLine()
            transcript?.write("\n- ✓ ${technique.capitalize()} complete (${application?.ideas?.size ?: 0} ideas)\n".toByteArray())
            appendLine("- ✓ ${technique.capitalize()} complete (${application?.ideas?.size ?: 0} ideas)")
          }.renderMarkdown(true))
        }

        log.info("All techniques applied. Total ideas generated: ${allIdeas.size}")

        overviewTask.add(buildString {
          appendLine()
          transcript?.write("\n- ✓ All techniques applied (${allIdeas.size} total ideas)\n- ⏳ Synthesizing insights...\n".toByteArray())
          appendLine("- ✓ All techniques applied (${allIdeas.size} total ideas)")
          appendLine("- ⏳ Synthesizing insights...")
        }.renderMarkdown(true))

        // Step 2: Synthesize insights
        log.info("Starting synthesis phase")
        val synthesisTask = tabs.newTask("Synthesis")

        synthesisTask.add(buildString {
          appendLine("# Cross-Technique Synthesis")
          transcript?.write("\n# Cross-Technique Synthesis\n\n**Status:** ⏳ Analyzing patterns and insights...\n".toByteArray())
          appendLine()
          appendLine("**Status:** ⏳ Analyzing patterns and insights...")
        }.renderMarkdown(true))

        val synthesisPrompt = """
You are an expert in creative problem-solving and innovation.

## Task
Synthesize insights across all the lateral thinking techniques applied to this problem.

## Original Problem
$problem

## Techniques Applied and Their Results
${
          techniqueApplications.joinToString("\n\n") { app ->
            """
### ${app.technique.capitalize()}
**Provocation:** ${app.provocation}
**Ideas Generated:** ${app.ideas.size}
**Key Insights:**
${app.insights.joinToString("\n") { "- $it" }}

**Top Ideas:**
${
              app.ideas.sortedByDescending { it.novelty_score * it.feasibility_score }.take(2)
                .joinToString("\n") { "- ${it.title}: ${it.description.take(100)}..." }
            }
""".trim()
          }
        }

## Instructions
1. Identify common themes and patterns across techniques
2. Highlight the most breakthrough insights
3. Identify complementary ideas that could be combined
4. Suggest 3-5 recommended unconventional approaches
5. Note any paradigm shifts or reframings that emerged

Provide a comprehensive synthesis.
            """.trimIndent()

        val synthesisAgent = ChatAgent(
          prompt = "You are an expert in creative synthesis and innovation strategy.",
          model = defaultSmart.getChildClient(task),
          temperature = 0.6
        )

        val synthesisText = synthesisAgent.answer(listOf(synthesisPrompt))

        synthesisTask.add(buildString {
          appendLine()
          transcript?.write("\n---\n\n## Synthesis Results\n\n**Status:** ✓ Complete\n\n${synthesisText}\n".toByteArray())
          appendLine("---")
          appendLine()
          appendLine("## Synthesis Results")
          appendLine()
          appendLine("**Status:** ✓ Complete")
          appendLine()
          appendLine(synthesisText)
        }.renderMarkdown(true))
        synthesisTask.complete()

        // Extract recommended approaches from synthesis
        val recommendedApproaches = extractRecommendedApproaches(synthesisText)

        overviewTask.add(buildString {
          appendLine()
          transcript?.write("\n- ✓ Synthesis complete\n".toByteArray())
          appendLine("- ✓ Synthesis complete")
          if (evaluateFeasibility) {
            appendLine("- ⏳ Evaluating feasibility...")
          }
        }.renderMarkdown(true))

        // Step 3: Feasibility evaluation (if requested)
        var feasibilityEvaluation: FeasibilityEvaluation? = null
        if (evaluateFeasibility) {
          log.info("Starting feasibility evaluation phase")
          val feasibilityTask = tabs.newTask("Feasibility")

          feasibilityTask.add(buildString {
            appendLine("# Feasibility Evaluation")
            transcript?.write("\n# Feasibility Evaluation\n\n**Status:** ⏳ Evaluating ${allIdeas.size} ideas...\n".toByteArray())
            appendLine()
            appendLine("**Status:** ⏳ Evaluating ${allIdeas.size} ideas...")
          }.renderMarkdown(true))

          val feasibilityPrompt = """
You are an expert in evaluating the practical feasibility of innovative ideas.

## Original Problem
$problem

${if (domainContext != null) "## Domain Context\n$domainContext\n" else ""}
${if (!constraints.isNullOrEmpty()) "## Constraints\n${constraints.joinToString("\n") { "- $it" }}\n" else ""}

## Ideas to Evaluate
${
            allIdeas.sortedByDescending { it.novelty_score }.take(15).joinToString("\n\n") { idea ->
              """
### ${idea.title}
**Technique:** ${idea.technique}
**Description:** ${idea.description}
**Novelty:** ${String.format("%.1f%%", idea.novelty_score * 100)}
**Initial Feasibility:** ${String.format("%.1f%%", idea.feasibility_score * 100)}
**Benefits:** ${idea.benefits.joinToString(", ")}
**Challenges:** ${idea.challenges.joinToString(", ")}
""".trim()
            }
          }

## Instructions
1. Provide an overall feasibility assessment
2. Rank the top 5 most promising ideas by practical feasibility
3. Identify 3-5 ideas that need further exploration or prototyping
4. Suggest 2-3 hybrid approaches that combine elements from multiple ideas
5. Consider implementation complexity, resource requirements, and risk

Provide a structured evaluation.
            """.trimIndent()

          val feasibilityParser = ParsedAgent(
            resultClass = FeasibilityEvaluation::class.java,
            prompt = feasibilityPrompt,
            model = defaultSmart.getChildClient(task),
            temperature = 0.4,
            name = "FeasibilityEvaluation",
            parsingChatter = defaultFast,
          )

          feasibilityEvaluation = feasibilityParser.answer(listOf(feasibilityPrompt)).obj

          if (feasibilityEvaluation != null) {
            feasibilityTask.add(buildString {
              transcript?.write("\n---\n\n## Evaluation Results\n\n**Status:** ✓ Complete\n\n".toByteArray())
              appendLine()
              appendLine("---")
              appendLine()
              appendLine("## Evaluation Results")
              appendLine()
              appendLine("**Status:** ✓ Complete")
              appendLine()
              appendLine("### Overall Assessment")
              appendLine()
              appendLine(feasibilityEvaluation.overall_assessment)
              appendLine()
              appendLine("### Top Ideas by Feasibility")
              appendLine()
              feasibilityEvaluation.top_ideas.forEachIndexed { idx, idea ->
                appendLine("${idx + 1}. $idea")
              }
              appendLine()
              appendLine("### Ideas for Further Exploration")
              appendLine()
              feasibilityEvaluation.ideas_for_exploration.forEach { appendLine("- $it") }
              appendLine()
              if (feasibilityEvaluation.hybrid_approaches.isNotEmpty()) {
                appendLine("### Hybrid Approaches")
                appendLine()
                feasibilityEvaluation.hybrid_approaches.forEach { appendLine("- $it") }
              }
            }.renderMarkdown(true))
            transcript?.write(this.toString().toByteArray())
          }

          overviewTask.add(buildString {
            transcript?.write("\n- ✓ Feasibility evaluation complete\n".toByteArray())
            appendLine()
            appendLine("- ✓ Feasibility evaluation complete")
          }.renderMarkdown(true))
          feasibilityTask.complete()
        }

        // Step 4: Create final result
        val result = LateralThinkingResult(
          technique_applications = techniqueApplications,
          all_ideas = allIdeas,
          synthesized_insights = extractInsights(synthesisText),
          recommended_approaches = recommendedApproaches,
          feasibility_evaluation = feasibilityEvaluation
        )

        // Step 5: Format final output
        log.info("Formatting final results")
        val summaryTask = tabs.newTask("Summary")

        val summaryContent = formatSummary(result, problem, techniques)
        summaryTask.add(summaryContent.renderMarkdown(true))
        transcript?.write("\n${summaryContent}\n".toByteArray())
        summaryTask.complete()

        // Create concise result text
        val resultText = buildString {
          appendLine("# Lateral Thinking Results")
          appendLine()
          appendLine("**Problem:** $problem")
          appendLine()
          appendLine("## Techniques Applied")
          techniques.forEach { appendLine("- ${it.capitalize()}") }
          appendLine()
          appendLine("## Key Statistics")
          appendLine("- **Total Ideas Generated:** ${allIdeas.size}")
          appendLine(
            "- **Average Novelty:** ${
              String.format(
                "%.1f%%",
                allIdeas.map { it.novelty_score }.average() * 100
              )
            }"
          )
          appendLine(
            "- **Average Feasibility:** ${
              String.format(
                "%.1f%%",
                allIdeas.map { it.feasibility_score }.average() * 100
              )
            }"
          )
          appendLine()
          appendLine("## Top Breakthrough Ideas")
          allIdeas.sortedByDescending { it.novelty_score * it.feasibility_score }
            .take(5)
            .forEachIndexed { idx, idea ->
              appendLine("${idx + 1}. **${idea.title}** (${idea.technique})")
              appendLine("   ${idea.description.truncateForDisplay(maxDescriptionLength)}")
              appendLine()
            }
          appendLine("## Recommended Approaches")
          recommendedApproaches.forEach { appendLine("- $it") }
          appendLine()
          if (feasibilityEvaluation != null) {
            appendLine("## Feasibility Assessment")
            appendLine(feasibilityEvaluation.overall_assessment.truncateForDisplay(maxDescriptionLength))
            appendLine()
          }
          appendLine("*See the Summary tab for complete analysis and all generated ideas*")
        }

        // Final overview update
        val totalTime = System.currentTimeMillis() - startTime
        overviewTask.add(buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ✓ Task Complete")
          appendLine()
          appendLine("| Metric | Value |")
          appendLine("|--------|-------|")
          appendLine("| Techniques Applied | ${techniques.size} |")
          appendLine("| Total Ideas | ${allIdeas.size} |")
          appendLine(
            "| Avg Novelty | ${
              String.format(
                "%.1f%%",
                allIdeas.map { it.novelty_score }.average() * 100
              )
            } |"
          )
          appendLine(
            "| Avg Feasibility | ${
              String.format(
                "%.1f%%",
                allIdeas.map { it.feasibility_score }.average() * 100
              )
            } |"
          )
          appendLine("| Total Time | ${totalTime / 1000}s |")
          appendLine()
          appendLine("**Status:** ✓ Complete")
          transcript?.write(this.toString().toByteArray())
        }.renderMarkdown(true))
        overviewTask.complete()

        log.info(
          "LateralThinkingTask completed: total_time=${totalTime}ms, techniques=${techniques.size}, ideas=${allIdeas.size}, avg_novelty=${
            allIdeas.map { it.novelty_score }.average()
          }"
        )
        task.safeComplete(
          "Completed in ${totalTime / 1000}s with ${allIdeas.size} ideas across ${techniques.size} techniques.",
          log
        )
        // Create summary message with transcript link
        val transcriptLink = task.saveFile("lateral_thinking_summary.md", resultText.toByteArray())
        val summaryMessage = buildString {
          appendLine(resultText)
          appendLine()
          appendLine("---")
          appendLine()
          appendLine(
            "📄 **Full Analysis:** [View Transcript]($transcriptLink) | [HTML](${transcriptLink.removeSuffix(".md")}.html) | [PDF](${
              transcriptLink.removeSuffix(
                ".md"
              )
            }.pdf)"
          )
        }
        resultFn(summaryMessage)

      } catch (e: Exception) {
        log.error("Error during LateralThinkingTask execution", e)
        task.error(e)
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        task.safeComplete("Failed with error: ${e.message}", log)
        resultFn("ERROR: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }


  private fun getTechniqueDescription(technique: String): String {
    return when (technique.lowercase()) {
      "reversal" -> """
## Reversal Technique
Instead of solving the problem, reverse it. Ask "How could we make this worse?" or "What if we did the opposite?"
This helps identify hidden assumptions and can lead to breakthrough insights.
            """.trimIndent()

      "random_stimulus" -> """
## Random Stimulus Technique
Introduce a completely unrelated concept, object, or idea and force connections to the problem.
This breaks mental patterns and can spark unexpected creative solutions.
            """.trimIndent()

      "challenge_assumptions" -> """
## Challenge Assumptions Technique
Identify and question the fundamental assumptions underlying the problem.
Ask "What if this assumption wasn't true?" to open new solution spaces.
            """.trimIndent()

      "exaggeration" -> """
## Exaggeration Technique
Amplify aspects of the problem to extremes. Make it 10x, 100x, or 1000x bigger or smaller.
Extreme scenarios often reveal insights applicable to the original problem.
            """.trimIndent()

      "escape" -> """
## Escape Technique
Temporarily ignore a key constraint or requirement that seems immovable.
This mental freedom can reveal solutions that work around the constraint in unexpected ways.
            """.trimIndent()

      "metaphor" -> """
## Metaphor Technique
Describe the problem using metaphors from completely different domains.
Metaphorical thinking can reveal structural similarities and novel approaches.
            """.trimIndent()

      "provocation" -> """
## Provocation Technique
Make deliberately absurd or provocative statements about the problem.
Use "Po" (provocative operation) statements to jar thinking out of established patterns.
            """.trimIndent()

      else -> "## $technique Technique\nApplying lateral thinking technique: $technique"
    }
  }

  private fun buildTechniquePrompt(
    technique: String,
    problem: String,
    numAlternatives: Int,
    domainContext: String?,
    constraints: List<String>?,
    priorContext: String
  ): String {
    val baseContext = buildString {
      appendLine("## Problem")
      appendLine(problem)
      appendLine()
      if (domainContext != null) {
        appendLine("## Domain Context")
        appendLine(domainContext)
        appendLine()
      }
      if (!constraints.isNullOrEmpty()) {
        appendLine("## Constraints")
        constraints.forEach { appendLine("- $it") }
        appendLine()
      }
      if (priorContext.isNotBlank()) {
        appendLine("## Additional Context")
        appendLine(priorContext.take(2000))
        appendLine()
      }
    }

    return when (technique.lowercase()) {
      "reversal" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Reversal
Apply the reversal technique to generate $numAlternatives unconventional ideas.

### Instructions
1. Reverse the problem: Instead of solving it, how would you make it worse or achieve the opposite?
2. For each reversal, identify what it reveals about hidden assumptions
3. Transform the reversal back into a positive, unconventional solution
4. Explain how each idea breaks conventional thinking
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using reversal thinking.
            """.trimIndent()

      "random_stimulus" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Random Stimulus
Apply the random stimulus technique to generate $numAlternatives unconventional ideas.

### Instructions
1. Introduce $numAlternatives completely unrelated concepts (e.g., "ocean waves", "jazz music", "ant colonies", "origami", "thunderstorms")
2. For each random stimulus, force connections to the problem
3. Develop each connection into a concrete, unconventional solution
4. Explain how the random stimulus sparked the breakthrough
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using random stimuli.
            """.trimIndent()

      "challenge_assumptions" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Challenge Assumptions
Apply assumption-challenging to generate $numAlternatives unconventional ideas.

### Instructions
1. Identify $numAlternatives fundamental assumptions underlying the problem
2. For each assumption, ask "What if this wasn't true?"
3. Develop solutions that work in the assumption-free space
4. Explain how challenging the assumption opens new possibilities
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas by challenging assumptions.
            """.trimIndent()

      "exaggeration" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Exaggeration
Apply exaggeration to generate $numAlternatives unconventional ideas.

### Instructions
1. Create $numAlternatives extreme scenarios (10x, 100x, 1000x scale changes, or reduce to zero)
2. For each exaggeration, explore what solutions would work in that extreme
3. Scale the solution back to find insights applicable to the original problem
4. Explain how the exaggeration revealed the breakthrough
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using exaggeration.
            """.trimIndent()

      "escape" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Escape
Apply the escape technique to generate $numAlternatives unconventional ideas.

### Instructions
1. Identify $numAlternatives key constraints that seem immovable
2. For each constraint, temporarily ignore it completely
3. Develop solutions in this constraint-free space
4. Find ways to work around or reframe the original constraint
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas by escaping constraints.
            """.trimIndent()

      "metaphor" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Metaphor
Apply metaphorical thinking to generate $numAlternatives unconventional ideas.

### Instructions
1. Create $numAlternatives metaphors from different domains (nature, music, sports, cooking, etc.)
2. For each metaphor, identify structural similarities to the problem
3. Develop solutions by applying the metaphor's logic
4. Explain how the metaphor reveals new perspectives
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using metaphorical thinking.
            """.trimIndent()

      "provocation" -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: Provocation
Apply provocative statements to generate $numAlternatives unconventional ideas.

### Instructions
1. Create $numAlternatives deliberately absurd or provocative "Po" statements about the problem
2. For each provocation, explore what it suggests (even if impossible)
3. Extract practical insights from the provocative thinking
4. Develop concrete solutions inspired by the provocation
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using provocations.
            """.trimIndent()

      else -> """
You are an expert in lateral thinking and creative problem-solving.

$baseContext

## Technique: $technique
Apply the $technique technique to generate $numAlternatives unconventional ideas.

### Instructions
1. Apply the $technique technique systematically
2. Generate $numAlternatives unconventional ideas
3. Explain how each idea breaks conventional thinking
4. Identify benefits and challenges for each
5. Assess novelty (0-1) and feasibility (0-1) for each idea

Generate $numAlternatives ideas using $technique.
            """.trimIndent()
    }
  }

  private fun extractRecommendedApproaches(synthesisText: String): List<String> {
    // Simple extraction - look for numbered or bulleted lists in sections about recommendations
    val lines = synthesisText.lines()
    val recommendations = mutableListOf<String>()

    var inRecommendationSection = false
    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.contains("recommend", ignoreCase = true) ||
        trimmed.contains("approach", ignoreCase = true) ||
        trimmed.contains("suggestion", ignoreCase = true)
      ) {
        inRecommendationSection = true
        continue
      }
      if (inRecommendationSection) {
        if (trimmed.startsWith("-") || trimmed.matches(Regex("^\\d+\\..*"))) {
          val cleaned = trimmed.removePrefix("-").removePrefix(Regex("^\\d+\\.")).trim()
          if (cleaned.isNotEmpty() && cleaned.length > 10) {
            recommendations.add(cleaned)
          }
        } else if (trimmed.isEmpty() && recommendations.isNotEmpty()) {
          break
        }
      }
    }

    return recommendations.take(5)
  }

  private fun extractInsights(synthesisText: String): List<String> {
    // Simple extraction - look for bulleted or numbered insights
    val lines = synthesisText.lines()
    val insights = mutableListOf<String>()

    var inInsightSection = false
    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.contains("insight", ignoreCase = true) ||
        trimmed.contains("theme", ignoreCase = true) ||
        trimmed.contains("pattern", ignoreCase = true)
      ) {
        inInsightSection = true
        continue
      }
      if (inInsightSection) {
        if (trimmed.startsWith("-") || trimmed.matches(Regex("^\\d+\\..*"))) {
          //val cleaned = trimmed.removePrefix("-").removePrefix(Regex("^\\d+\\.")).trim()
          val cleaned = trimmed.removePrefix("-").removePrefix(Regex("^\\d+\\.")).trim()
          if (cleaned.isNotEmpty() && cleaned.length > 10) {
            insights.add(cleaned)
          }
        } else if (trimmed.isEmpty() && insights.isNotEmpty()) {
          break
        }
      }
    }

    return insights.take(10)
  }

  private fun formatSummary(
    result: LateralThinkingResult,
    problem: String,
    techniques: List<String>
  ): String {
    return buildString {
      appendLine("# Lateral Thinking Summary")
      appendLine()
      appendLine("## Problem Statement")
      appendLine()
      appendLine("> $problem")
      appendLine()
      appendLine("---")
      appendLine()
      appendLine("## Executive Summary")
      appendLine()
      appendLine("Applied ${techniques.size} lateral thinking techniques to generate ${result.all_ideas.size} unconventional ideas.")
      appendLine()
      val avgNovelty = result.all_ideas.map { it.novelty_score }.average()
      val avgFeasibility = result.all_ideas.map { it.feasibility_score }.average()
      appendLine("**Average Novelty:** ${String.format("%.1f%%", avgNovelty * 100)}")
      appendLine()
      appendLine("**Average Feasibility:** ${String.format("%.1f%%", avgFeasibility * 100)}")
      appendLine()
      appendLine("---")
      appendLine()
      appendLine("## Top Breakthrough Ideas")
      appendLine()
      result.all_ideas
        .sortedByDescending { it.novelty_score * it.feasibility_score }
        .take(10)
        .forEachIndexed { idx, idea ->
          appendLine("### ${idx + 1}. ${idea.title}")
          appendLine()
          appendLine(
            "**Technique:** ${idea.technique.capitalize()} | **Novelty:** ${
              String.format(
                "%.1f%%",
                idea.novelty_score * 100
              )
            } | **Feasibility:** ${String.format("%.1f%%", idea.feasibility_score * 100)}"
          )
          appendLine()
          appendLine("#### Description")
          appendLine(idea.description)
          appendLine()
          appendLine("#### Breakthrough Aspect")
          appendLine(idea.breakthrough_aspect)
          appendLine()
          if (idea.benefits.isNotEmpty()) {
            appendLine("#### Benefits")
            idea.benefits.forEach { appendLine("- $it") }
            appendLine()
          }
          if (idea.challenges.isNotEmpty()) {
            appendLine("#### Challenges")
            idea.challenges.forEach { appendLine("- $it") }
            appendLine()
          }
          if (idea.implementation_steps.isNotEmpty()) {
            appendLine("#### Implementation Steps")
            idea.implementation_steps.forEach { appendLine("${it.split(".").firstOrNull() ?: ""}. ${it}") }
            appendLine()
          }
          appendLine("---")
          appendLine()
        }

      appendLine("## Synthesized Insights")
      appendLine()
      result.synthesized_insights.forEach { appendLine("- $it") }
      appendLine()

      appendLine("## Recommended Unconventional Approaches")
      appendLine()
      result.recommended_approaches.forEachIndexed { idx, approach ->
        appendLine("${idx + 1}. $approach")
      }
      appendLine()

      if (result.feasibility_evaluation != null) {
        appendLine("---")
        appendLine()
        appendLine("## Feasibility Evaluation")
        appendLine()
        appendLine("### Overall Assessment")
        appendLine()
        appendLine(result.feasibility_evaluation.overall_assessment)
        appendLine()
        appendLine("### Most Promising Ideas")
        appendLine()
        result.feasibility_evaluation.top_ideas.forEachIndexed { idx, idea ->
          appendLine("${idx + 1}. $idea")
        }
        appendLine()
        if (result.feasibility_evaluation.ideas_for_exploration.isNotEmpty()) {
          appendLine("### Ideas Requiring Further Exploration")
          appendLine()
          result.feasibility_evaluation.ideas_for_exploration.forEach { appendLine("- $it") }
          appendLine()
        }
        if (result.feasibility_evaluation.hybrid_approaches.isNotEmpty()) {
          appendLine("### Hybrid Approaches")
          appendLine()
          result.feasibility_evaluation.hybrid_approaches.forEach { appendLine("- $it") }
          appendLine()
        }
      }

      appendLine("---")
      appendLine()
      appendLine("## Ideas by Technique")
      appendLine()
      result.technique_applications.forEach { app ->
        appendLine("### ${app.technique.capitalize()} (${app.ideas.size} ideas)")
        appendLine()
        appendLine("**Provocation:** ${app.provocation}")
        appendLine()
        app.ideas.sortedByDescending { it.novelty_score }.take(3).forEach { idea ->
          appendLine("- **${idea.title}** (Novelty: ${String.format("%.0f%%", idea.novelty_score * 100)})")
          appendLine("  ${idea.description.truncateForDisplay(maxDescriptionLength)}")
        }
        if (app.ideas.size > 3) {
          appendLine("  *...and ${app.ideas.size - 3} more ideas*")
        }
        appendLine()
      }
    }
  }


  private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
  }

}

private fun String.removePrefix(prefix: Regex): String {
  return this.replace(prefix, "")
}