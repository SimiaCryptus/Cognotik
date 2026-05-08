package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

class PersuasiveEssayTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: PersuasiveEssayTaskExecutionConfigData?
) :
  AbstractTask<PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData, PersuasiveEssayTask.PersuasiveEssayTaskTypeConfig>(
    orchestrationConfig,
    planTask
  ) {
  class PersuasiveEssayTaskTypeConfig(
    @Description("Whether to generate images for the essay")
    var generate_images: Boolean = true,
    @Description("Whether to generate a cover image for the essay")
    var generate_cover_image: Boolean = true,
  ) : TaskTypeConfig(
    task_type = PersuasiveEssay.name,
    name = "Persuasive Essay Task"
  )

  class PersuasiveEssayTaskExecutionConfigData(
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    var related_files: List<String>? = null,

    @Description("The thesis statement or position to argue for")
    var thesis: String? = null,

    @Description("The target audience (e.g., 'general public', 'academics', 'policymakers', 'business leaders')")
    var target_audience: String = "general public",

    @Description("The tone of the essay (e.g., 'formal', 'conversational', 'passionate', 'analytical')")
    var tone: String = "formal",

    @Description("Target word count for the complete essay")
    var target_word_count: Int = 1500,

    @Description("Number of main arguments to develop")
    var num_arguments: Int = 3,

    @Description("Whether to include counterarguments and rebuttals")
    var include_counterarguments: Boolean = true,

    @Description("Whether to use rhetorical devices (ethos, pathos, logos)")
    var use_rhetorical_devices: Boolean = true,

    @Description("Whether to include statistical evidence and citations")
    var include_evidence: Boolean = true,

    @Description("Whether to use analogies and examples")
    var use_analogies: Boolean = true,

    @Description("Call to action type (MUST BE one of: 'strong', 'moderate', 'reflective', 'none')")
    var call_to_action: String = "strong",

    @Description("Number of revision passes for quality improvement")
    var revision_passes: Int = 1,

    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = PersuasiveEssay.name,
    task_description = task_description ?: "Generate persuasive essay for thesis: '$thesis'",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (thesis.isNullOrBlank()) {
        return "thesis must not be null or blank"
      }
      if (target_word_count <= 0) {
        return "target_word_count must be positive, got: $target_word_count"
      }
      if (num_arguments < 1 || num_arguments > 10) {
        return "num_arguments must be between 1 and 10, got: $num_arguments"
      }
      if (revision_passes < 0 || revision_passes > 5) {
        return "revision_passes must be between 0 and 5, got: $revision_passes"
      }
      if (target_audience.isBlank()) {
        return "target_audience must not be blank"
      }
      if (tone.isBlank()) {
        return "tone must not be blank"
      }
      val validCallToActions = setOf("strong", "moderate", "reflective", "none")
      if (call_to_action.lowercase() !in validCallToActions) {
        return "call_to_action must be one of: ${validCallToActions.joinToString(", ")}, got: $call_to_action"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class EssayOutline(
    @Description("The essay title")
    val title: String = "",
    @Description("Hook or opening statement")
    val hook: String = "",
    @Description("Background context")
    val background: String = "",
    @Description("Clear thesis statement")
    val thesis_statement: String = "",
    @Description("Main arguments to develop")
    val arguments: List<ArgumentOutline> = emptyList(),
    @Description("Counterarguments to address")
    val counterarguments: List<CounterargumentOutline> = emptyList(),
    @Description("Conclusion strategy")
    val conclusion_strategy: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) return "title must not be blank"
      if (thesis_statement.isBlank()) return "thesis_statement must not be blank"
      if (arguments.isEmpty()) return "arguments must not be empty"
      return ValidatedObject.validateFields(this)
    }
  }

  data class ArgumentOutline(
    @Description("Argument number")
    val number: Int = 1,
    @Description("Main claim of this argument")
    val claim: String = "",
    @Description("Supporting points")
    val supporting_points: List<String> = emptyList(),
    @Description("Evidence types to use")
    val evidence_types: List<String> = emptyList(),
    @Description("Rhetorical approach")
    val rhetorical_approach: String = "",
    @Description("Estimated word count")
    val estimated_word_count: Int = 0
  ) : ValidatedObject

  data class CounterargumentOutline(
    @Description("The opposing viewpoint")
    val opposing_view: String = "",
    @Description("Rebuttal strategy")
    val rebuttal_strategy: String = "",
    @Description("Estimated word count")
    val estimated_word_count: Int = 0
  ) : ValidatedObject

  data class EssaySection(
    @Description("Section type")
    val section_type: String = "",
    @Description("Section content")
    val content: String = "",
    @Description("Word count")
    val word_count: Int = 0,
    @Description("Rhetorical devices used")
    val rhetorical_devices: List<String> = emptyList(),
    @Description("Key persuasive elements")
    val persuasive_elements: List<String> = emptyList()
  ) : ValidatedObject

  override fun promptSegment(): String {
    return """
 PersuasiveEssay - Generate compelling persuasive essays with structured arguments
  ** Specify the thesis statement or position to argue
  ** Optionally provide input files (supports glob patterns) to incorporate as research
  ** Define target audience and tone
  ** Set target word count and number of main arguments
  ** Enable counterarguments and rebuttals for balanced perspective
  ** Use rhetorical devices (ethos, pathos, logos) for persuasive impact
  ** Include statistical evidence and citations
  ** Incorporate analogies and examples for clarity
  ** Configure call to action strength
  ** Performs outline creation, argument development, and iterative writing
  ** Produces complete, well-structured persuasive essay
  ** Detailed output saved to files with links in summary
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
      val startTime = System.currentTimeMillis()
      log.info("Starting PersuasiveEssayTask for thesis: '${executionConfig?.thesis}'")
      val transcript = task.newUserFileStream(transcriptFile())
      try {
        transcript?.write("# Persuasive Essay Generation Transcript\n\n".toByteArray())
        transcript?.write(
          "**Started:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }\n\n".toByteArray()
        )
        transcript?.write("**Thesis:** ${executionConfig?.thesis}\n\n---\n\n".toByteArray())

        // Validate configuration
        val executionConfig = this.executionConfig ?: return@submit
        executionConfig?.validate()?.let { validationError ->
          log.error("Configuration validation failed: $validationError")
          task.safeComplete("CONFIGURATION ERROR: $validationError", log)
          task.error(ValidatedObject.ValidationError(validationError, executionConfig))
          resultFn("CONFIGURATION ERROR: $validationError")
          return@submit
        }

        val thesis = executionConfig?.thesis
        if (thesis.isNullOrBlank()) {
          log.error("No thesis specified for persuasive essay")
          task.safeComplete("CONFIGURATION ERROR: No thesis specified", log)
          resultFn("CONFIGURATION ERROR: No thesis specified")
          return@submit
        }

        val api = defaultSmart ?: return@submit

        val tabs = TabbedDisplay(task)
        // Generate cover image if enabled
        if (typeConfig!!.generate_cover_image) {
          generateCoverImage(
            task = task,
            tabs = tabs,
            title = thesis,
            audience = executionConfig.target_audience,
            tone = executionConfig.tone,
            transcript = transcript,
            orchestrationConfig = orchestrationConfig
          )
        }

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
          appendLine("# Persuasive Essay Generation")
          appendLine()
          appendLine("**Thesis:** $thesis")
          appendLine()
          appendLine("## Configuration")
          appendLine("- Target Audience: ${executionConfig.target_audience}")
          appendLine("- Tone: ${executionConfig.tone}")
          appendLine("- Target Word Count: ${executionConfig.target_word_count}")
          appendLine("- Number of Arguments: ${executionConfig.num_arguments}")
          appendLine("- Include Counterarguments: ${if (executionConfig.include_counterarguments) "✓" else "✗"}")
          appendLine("- Use Rhetorical Devices: ${if (executionConfig.use_rhetorical_devices) "✓" else "✗"}")
          appendLine("- Include Evidence: ${if (executionConfig.include_evidence) "✓" else "✗"}")
          appendLine("- Use Analogies: ${if (executionConfig.use_analogies) "✓" else "✗"}")
          appendLine("- Call to Action: ${executionConfig.call_to_action}")
          appendLine()
          appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## Progress")
          appendLine()
          appendLine("### Phase 1: Research & Outline")
          appendLine("*Analyzing thesis and creating essay structure...*")
        }
        overviewTask.add(overviewContent.renderMarkdown(true))
        overviewTask.update()
        transcript?.write("## Configuration\n\n".toByteArray())
        transcript?.write(overviewContent.toByteArray())
        transcript?.write("\n\n".toByteArray())

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Persuasive Essay: $thesis\n\n")

        try {
          // Gather context
          val priorContext = getPriorCode(agent.executionState)
          val inputFileContent =
            super.getInputFileContent(executionConfig.related_files, root, treatDocumentsAsText = true)
          val contextFiles = getContextFiles()

          if (priorContext.isNotBlank() || inputFileContent.isNotBlank() || contextFiles.isNotBlank()) {
            log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
            val contextTask = tabs.newTask("Research Context")
            contextTask.add(
              buildString {
                appendLine("# Research Context")
                appendLine()
                if (priorContext.isNotBlank()) {
                  appendLine("## Prior Context")
                  appendLine(priorContext.truncateForDisplay(2000))
                  appendLine()
                }
                if (inputFileContent.isNotBlank()) {
                  appendLine("## Input Files")
                  appendLine(inputFileContent.truncateForDisplay(3000))
                  appendLine()
                }
                if (contextFiles.isNotBlank()) {
                  appendLine("## Related Files")
                  appendLine(contextFiles.truncateForDisplay(2000))
                }
              }.renderMarkdown(true)
            )
            contextTask.complete()
            transcript?.write(
              """
                            <details>
                            <summary>Research Context</summary>
                            $priorContext
                            $inputFileContent
                            $contextFiles
                            </details>
                        """.trimIndent().toByteArray()
            )
          }

          // Phase 1: Create outline
          log.info("Phase 1: Creating essay outline")
          val outlineTask = tabs.newTask("Outline")

          outlineTask.add(
            buildString {
              appendLine("# Essay Outline")
              appendLine()
              appendLine("**Status:** Creating structured outline...")
              appendLine()
            }.renderMarkdown(true)
          )
          outlineTask.update()

          val wordsPerArgument = (executionConfig.target_word_count * 0.6).toInt() / executionConfig.num_arguments
          val counterargumentWords = if (executionConfig.include_counterarguments) {
            (executionConfig.target_word_count * 0.15).toInt()
          } else 0

          val outlineAgent = ParsedAgent(
            resultClass = EssayOutline::class.java,
            prompt = """
You are an expert in persuasive writing and rhetoric. Create a detailed outline for a persuasive essay.

Thesis: $thesis

Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}
Target Word Count: ${executionConfig.target_word_count}
Number of Arguments: ${executionConfig.num_arguments}

${if (inputFileContent.isNotBlank()) "Input Files:\n${inputFileContent.truncateForDisplay(3000)}\n" else ""}
${if (priorContext.isNotBlank()) "Research Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Additional Research:\n${contextFiles.truncateForDisplay(3000)}\n" else ""}

Create an outline with:
1. A compelling hook that grabs attention
2. Background context (100-150 words)
3. Clear, specific thesis statement
4. ${executionConfig.num_arguments} main arguments (~$wordsPerArgument words each)
${if (executionConfig.include_counterarguments) "5. 2-3 counterarguments with rebuttal strategies (~$counterargumentWords words total)" else ""}
6. Conclusion strategy

For each argument, specify:
- The main claim
- 3-4 supporting points
- Types of evidence to use (statistics, expert testimony, examples, analogies)
${if (executionConfig.use_rhetorical_devices) "- Rhetorical approach (ethos/pathos/logos emphasis)" else ""}

Ensure the outline:
- Builds a logical progression of ideas
- Addresses the ${executionConfig.target_audience} effectively
- Maintains a ${executionConfig.tone} tone
- Includes diverse types of support
- Anticipates and addresses objections
          """.trimIndent(),
            model = api,
            temperature = 0.7,
            parsingChatter = defaultFast
          )

          val outline = outlineAgent.answer(listOf("Generate outline")).obj
          log.info("Generated outline: ${outline.arguments.size} arguments, ${outline.counterarguments.size} counterarguments")

          val outlineContent = buildString {
            appendLine("## ${outline.title}")
            appendLine()
            appendLine("### Hook")
            appendLine(outline.hook)
            appendLine()
            appendLine("### Background")
            appendLine(outline.background)
            appendLine()
            appendLine("### Thesis Statement")
            appendLine("> ${outline.thesis_statement}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("### Main Arguments")
            outline.arguments.forEach { arg ->
              appendLine("#### Argument ${arg.number}: ${arg.claim}")
              appendLine()
              appendLine("**Supporting Points:**")
              arg.supporting_points.forEach { point ->
                appendLine("- $point")
              }
              appendLine()
              appendLine("**Evidence Types:** ${arg.evidence_types.joinToString(", ")}")
              appendLine()
              if (arg.rhetorical_approach.isNotBlank()) {
                appendLine("**Rhetorical Approach:** ${arg.rhetorical_approach}")
                appendLine()
              }
              appendLine("**Est. Words:** ${arg.estimated_word_count}")
              appendLine()
              appendLine("---")
              appendLine()
            }
            if (outline.counterarguments.isNotEmpty()) {
              appendLine("### Counterarguments & Rebuttals")
              outline.counterarguments.forEach { counter ->
                appendLine("**Opposing View:** ${counter.opposing_view}")
                appendLine()
                appendLine("**Rebuttal Strategy:** ${counter.rebuttal_strategy}")
                appendLine()
                appendLine("**Est. Words:** ${counter.estimated_word_count}")
                appendLine()
              }
              appendLine("---")
              appendLine()
            }
            appendLine("### Conclusion Strategy")
            appendLine(outline.conclusion_strategy)
            appendLine()
            appendLine("**Status:** ✅ Complete")
          }
          outlineTask.add(outlineContent.renderMarkdown(true))
          outlineTask.complete()
          transcript?.write("## Essay Outline\n\n".toByteArray())
          transcript?.write(outlineContent.toByteArray())
          transcript?.write("\n\n".toByteArray())

          overviewTask.add("✅ Phase 1 Complete: Outline created\n".renderMarkdown(true))
          overviewTask.add("\n### Phase 2: Introduction\n*Writing compelling introduction...*\n".renderMarkdown(true))
          overviewTask.update()
          // Generate outline visualization image if enabled
          if (typeConfig!!.generate_images) {
            generateOutlineImage(
              task = task,
              tabs = tabs,
              title = outline.title,
              outline = outline,
              transcript = transcript,
              orchestrationConfig = orchestrationConfig
            )
          }

          // Phase 2: Write Introduction
          log.info("Phase 2: Writing introduction")
          val introTask = tabs.newTask("Introduction")

          introTask.add(
            buildString {
              appendLine("# Introduction")
              appendLine()
              appendLine("**Status:** Writing introduction...")
              appendLine()
            }.renderMarkdown(true)
          )
          introTask.update()

          val introAgent = ParsedAgent(
            resultClass = EssaySection::class.java,
            prompt = """
You are an expert persuasive writer. Write a compelling introduction for this essay.

Thesis: $thesis
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}

Outline:
Hook: ${outline.hook}
Background: ${outline.background}
Thesis Statement: ${outline.thesis_statement}

Write an introduction (200-300 words) that:
1. Opens with the compelling hook
2. Provides necessary background context
3. Establishes credibility and relevance
4. Builds toward the thesis statement
5. Clearly states the thesis
${if (executionConfig.use_rhetorical_devices) "6. Uses appropriate rhetorical devices (ethos to establish credibility)" else ""}

Make it engaging and set the tone for the entire essay.
Speak directly to the ${executionConfig.target_audience}.
          """.trimIndent(),
            model = api,
            temperature = 0.8,
            parsingChatter = defaultFast
          )

          var introduction = introAgent.answer(listOf("Write introduction")).obj

          introTask.add(
            buildString {
              appendLine("## Introduction")
              appendLine()
              appendLine(introduction.content)
              appendLine()
              appendLine("---")
              appendLine()
              appendLine("**Word Count:** ${introduction.word_count}")
              if (introduction.rhetorical_devices.isNotEmpty()) {
                appendLine()
                appendLine("**Rhetorical Devices:** ${introduction.rhetorical_devices.joinToString(", ")}")
              }
              appendLine()
              appendLine("**Status:** ✅ Complete")
            }.renderMarkdown(true)
          )
          introTask.complete()
          transcript?.write("## Introduction\n\n".toByteArray())
          transcript?.write(introduction.content.toByteArray())
          transcript?.write("\n\n**Word Count:** ${introduction.word_count}\n\n".toByteArray())


          resultBuilder.append(introduction.content)
          resultBuilder.append("\n\n")

          overviewTask.add(
            "✅ Phase 2 Complete: Introduction written (${introduction.word_count} words)\n".renderMarkdown(
              true
            )
          )
          overviewTask.add("\n### Phase 3: Body Arguments\n*Developing main arguments...*\n".renderMarkdown(true))
          overviewTask.update()

          // Phase 3: Write each argument
          log.info("Phase 3: Writing body arguments")
          val argumentSections = mutableListOf<EssaySection>()
          var cumulativeWordCount = introduction.word_count

          outline.arguments.forEachIndexed { index, argOutline ->
            log.info("Writing argument ${index + 1}/${outline.arguments.size}: ${argOutline.claim}")

            overviewTask.add(
              "- Argument ${index + 1}: ${argOutline.claim.truncateForDisplay(50)} ".renderMarkdown(
                true
              )
            )
            overviewTask.update()

            val argTask = tabs.newTask("Argument ${index + 1}")

            argTask.add(
              buildString {
                appendLine("# Argument ${index + 1}")
                appendLine()
                appendLine("**Status:** Writing argument...")
                appendLine()
              }.renderMarkdown(true)
            )
            argTask.update()

            // Build context from previous arguments
            val previousContext = if (argumentSections.isNotEmpty()) {
              buildString {
                appendLine("## Previous Arguments Summary")
                argumentSections.takeLast(1).forEach { prevArg ->
                  appendLine("**Previous Claim:** ${prevArg.persuasive_elements.firstOrNull() ?: ""}")
                  appendLine("**Key Points:** ${prevArg.content.take(200)}...")
                  appendLine()
                }
              }
            } else {
              "This is the first argument."
            }

            val argumentAgent = ParsedAgent(
              resultClass = EssaySection::class.java,
              prompt = """
You are an expert persuasive writer. Write a compelling body paragraph for this argument.

Overall Thesis: $thesis
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}

Argument to Develop:
Claim: ${argOutline.claim}
Supporting Points: ${argOutline.supporting_points.joinToString("; ")}
Evidence Types: ${argOutline.evidence_types.joinToString(", ")}
${if (argOutline.rhetorical_approach.isNotBlank()) "Rhetorical Approach: ${argOutline.rhetorical_approach}" else ""}
Target Words: ${argOutline.estimated_word_count}

$previousContext

Write a well-developed argument paragraph that:
1. Opens with a clear topic sentence stating the claim
2. Provides detailed supporting points
${if (executionConfig.include_evidence) "3. Includes specific evidence (statistics, expert quotes, examples)" else ""}
${if (executionConfig.use_analogies) "4. Uses analogies or concrete examples for clarity" else ""}
${if (executionConfig.use_rhetorical_devices) "5. Employs appropriate rhetorical devices (${argOutline.rhetorical_approach})" else ""}
6. Connects back to the thesis
7. Transitions smoothly to the next point

Make it persuasive, logical, and engaging.
Aim for approximately ${argOutline.estimated_word_count} words.
          """.trimIndent(),
              model = api,
              temperature = 0.8,
              parsingChatter = defaultFast
            )

            var argumentSection = argumentAgent.answer(listOf("Write argument")).obj
            argumentSections.add(argumentSection)
            cumulativeWordCount += argumentSection.word_count

            argTask.add(
              buildString {
                appendLine("## ${argOutline.claim}")
                appendLine()
                appendLine(argumentSection.content)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Word Count:** ${argumentSection.word_count}")
                if (argumentSection.rhetorical_devices.isNotEmpty()) {
                  appendLine()
                  appendLine("**Rhetorical Devices:** ${argumentSection.rhetorical_devices.joinToString(", ")}")
                }
                if (argumentSection.persuasive_elements.isNotEmpty()) {
                  appendLine()
                  appendLine("**Persuasive Elements:** ${argumentSection.persuasive_elements.joinToString(", ")}")
                }
                appendLine()
                appendLine("**Status:** ✅ Complete")
              }.renderMarkdown(true)
            )
            argTask.complete()
            transcript?.write("## Argument ${index + 1}: ${argOutline.claim}\n\n".toByteArray())
            transcript?.write(argumentSection.content.toByteArray())
            transcript?.write("\n\n**Word Count:** ${argumentSection.word_count}\n\n".toByteArray())


            resultBuilder.append(argumentSection.content)
            resultBuilder.append("\n\n")
            // Generate argument visualization image if enabled
            if (typeConfig!!.generate_images) {
              generateArgumentImage(
                task = task,
                tabs = tabs,
                argumentNumber = index + 1,
                claim = argOutline.claim,
                content = argumentSection.content,
                transcript = transcript,
                orchestrationConfig = orchestrationConfig
              )
            }

            overviewTask.add("✅ (${argumentSection.word_count} words)\n".renderMarkdown(true))
            overviewTask.update()
          }

          overviewTask.add("✅ Phase 3 Complete: All arguments written\n".renderMarkdown(true))

          // Phase 4: Counterarguments (if enabled)
          if (executionConfig.include_counterarguments && outline.counterarguments.isNotEmpty()) {
            overviewTask.add("\n### Phase 4: Counterarguments\n*Addressing opposing views...*\n".renderMarkdown(true))
            overviewTask.update()

            log.info("Phase 4: Writing counterarguments and rebuttals")
            val counterTask = tabs.newTask("Counterarguments")

            counterTask.add(
              buildString {
                appendLine("# Counterarguments & Rebuttals")
                appendLine()
                appendLine("**Status:** Writing counterarguments...")
                appendLine()
              }.renderMarkdown(true)
            )
            counterTask.update()

            val counterAgent = ParsedAgent(
              resultClass = EssaySection::class.java,
              prompt = """
You are an expert persuasive writer. Write a section addressing counterarguments.

Overall Thesis: $thesis
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}

Counterarguments to Address:
${outline.counterarguments.joinToString("\n") { "- ${it.opposing_view}\n  Rebuttal: ${it.rebuttal_strategy}" }}

Write a counterargument section that:
1. Acknowledges opposing viewpoints fairly and respectfully
2. Demonstrates understanding of the other side
3. Provides strong, logical rebuttals
4. Strengthens your original thesis
5. Maintains credibility through balanced treatment

Use phrases like "While some argue...", "Critics may claim...", "However..."
Show why your position is stronger despite valid concerns.
Aim for approximately $counterargumentWords words.
          """.trimIndent(),
              model = api,
              temperature = 0.7,
              parsingChatter = defaultFast
            )

            var counterSection = counterAgent.answer(listOf("Write counterarguments")).obj
            cumulativeWordCount += counterSection.word_count

            counterTask.add(
              buildString {
                appendLine("## Addressing Opposing Views")
                appendLine()
                appendLine(counterSection.content)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Word Count:** ${counterSection.word_count}")
                appendLine()
                appendLine("**Status:** ✅ Complete")
              }.renderMarkdown(true)
            )
            counterTask.complete()
            transcript?.write("## Counterarguments & Rebuttals\n\n".toByteArray())
            transcript?.write(counterSection.content.toByteArray())
            transcript?.write("\n\n**Word Count:** ${counterSection.word_count}\n\n".toByteArray())

            resultBuilder.append(counterSection.content)
            resultBuilder.append("\n\n")
            // Generate counterargument visualization image if enabled
            if (typeConfig!!.generate_images) {
              generateCounterargumentImage(
                task = task,
                tabs = tabs,
                content = counterSection.content,
                transcript = transcript,
                orchestrationConfig = orchestrationConfig
              )
            }

            overviewTask.add(
              "✅ Phase 4 Complete: Counterarguments addressed (${counterSection.word_count} words)\n".renderMarkdown(
                true
              )
            )
          }

          // Phase 5: Conclusion
          overviewTask.add("\n### Phase 5: Conclusion\n*Writing powerful conclusion...*\n".renderMarkdown(true))
          overviewTask.update()

          log.info("Phase 5: Writing conclusion")
          val conclusionTask = tabs.newTask("Conclusion")

          conclusionTask.add(
            buildString {
              appendLine("# Conclusion")
              appendLine()
              appendLine("**Status:** Writing conclusion...")
              appendLine()
            }.renderMarkdown(true)
          )
          conclusionTask.update()

          val conclusionAgent = ParsedAgent(
            resultClass = EssaySection::class.java,
            prompt = """
You are an expert persuasive writer. Write a powerful conclusion for this essay.

Overall Thesis: $thesis
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}
Call to Action Type: ${executionConfig.call_to_action}

Main Arguments Presented:
${
              argumentSections.mapIndexed { i, arg ->
                "${i + 1}. ${
                  arg.persuasive_elements.firstOrNull() ?: arg.content.take(
                    100
                  )
                }"
              }.joinToString("\n")
            }

Conclusion Strategy: ${outline.conclusion_strategy}

Write a conclusion (200-250 words) that:
1. Restates the thesis in fresh language
2. Synthesizes the main arguments
3. Emphasizes the significance and implications
4. Leaves a lasting impression
${
              when (executionConfig.call_to_action.lowercase()) {
                "strong" -> "5. Includes a powerful, specific call to action"
                "moderate" -> "5. Suggests concrete next steps or considerations"
                "reflective" -> "5. Invites thoughtful reflection on the topic"
                else -> ""
              }
            }
${if (executionConfig.use_rhetorical_devices) "6. Uses rhetorical devices for emotional impact (pathos)" else ""}

Make it memorable and motivating.
End on a strong note that reinforces your position.
          """.trimIndent(),
            model = api,
            temperature = 0.8,
            parsingChatter = defaultFast
          )

          var conclusion = conclusionAgent.answer(listOf("Write conclusion")).obj
          cumulativeWordCount += conclusion.word_count

          conclusionTask.add(
            buildString {
              appendLine("## Conclusion")
              appendLine()
              appendLine(conclusion.content)
              appendLine()
              appendLine("---")
              appendLine()
              appendLine("**Word Count:** ${conclusion.word_count}")
              if (conclusion.rhetorical_devices.isNotEmpty()) {
                appendLine()
                appendLine("**Rhetorical Devices:** ${conclusion.rhetorical_devices.joinToString(", ")}")
              }
              appendLine()
              appendLine("**Status:** ✅ Complete")
            }.renderMarkdown(true)
          )
          conclusionTask.complete()
          transcript?.write("## Conclusion\n\n".toByteArray())
          transcript?.write(conclusion.content.toByteArray())
          transcript?.write("\n\n**Word Count:** ${conclusion.word_count}\n\n".toByteArray())


          resultBuilder.append(conclusion.content)
          resultBuilder.append("\n\n")

          overviewTask.add(
            "✅ Phase 5 Complete: Conclusion written (${conclusion.word_count} words)\n".renderMarkdown(
              true
            )
          )

          // Phase 6: Revision (if enabled)
          if (executionConfig.revision_passes > 0) {
            overviewTask.add("\n### Phase 6: Revision\n*Refining and polishing...*\n".renderMarkdown(true))
            overviewTask.update()

            log.info("Phase 6: Performing ${executionConfig.revision_passes} revision pass(es)")
            val revisionTask = tabs.newTask("Revision")

            revisionTask.add(
              buildString {
                appendLine("# Revision Process")
                appendLine()
                appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
                appendLine()
              }.renderMarkdown(true)
            )
            revisionTask.update()

            val fullEssay = resultBuilder.toString()

            repeat(executionConfig.revision_passes) { passNum ->
              log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")

              val revisionAgent = ChatAgent(
                prompt = """
You are an expert editor specializing in persuasive writing. Review and improve this essay.

Current Essay:
$fullEssay

Focus on:
1. Strengthening argument logic and flow
2. Enhancing persuasive language and rhetoric
3. Improving transitions between ideas
4. Ensuring consistent tone (${executionConfig.tone})
5. Polishing sentence structure and word choice
6. Verifying thesis support throughout
7. Maximizing impact on ${executionConfig.target_audience}

Maintain:
- All key arguments and evidence
- The thesis and main claims
- Approximate word count ($cumulativeWordCount words)
- The ${executionConfig.tone} tone

Provide the complete revised essay.
            """.trimIndent(),
                model = api,
                temperature = 0.6
              )

              val revisedEssay = revisionAgent.answer(listOf("Revise the essay"))
              resultBuilder.clear()
              resultBuilder.append(revisedEssay)

              revisionTask.add(
                buildString {
                  appendLine("## Revision Pass ${passNum + 1}")
                  appendLine()
                  appendLine("✅ Complete")
                  appendLine()
                }.renderMarkdown(true)
              )
              revisionTask.update()
              transcript?.write("### Revision Pass ${passNum + 1}\n\n".toByteArray())
              transcript?.write("Completed revision pass ${passNum + 1} of ${executionConfig.revision_passes}\n\n".toByteArray())
            }
            revisionTask.complete()

            overviewTask.add(
              "✅ Phase 6 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown(
                true
              )
            )
          }

          // Phase 7: Final Assembly
          overviewTask.add("\n### Phase 7: Final Assembly\n*Compiling complete essay...*\n".renderMarkdown(true))
          overviewTask.update()

          log.info("Phase 7: Assembling final essay")
          val finalTask = tabs.newTask("Complete Essay")

          val finalEssay = buildString {
            appendLine("# ${outline.title}")
            appendLine()
            appendLine(resultBuilder.toString())
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("**Total Word Count:** $cumulativeWordCount")
            appendLine()
            appendLine("**Target Word Count:** ${executionConfig.target_word_count}")
            appendLine()
            appendLine("**Completion:** ${(cumulativeWordCount.toFloat() / executionConfig.target_word_count * 100).toInt()}%")
          }
          // Save complete essay to file
          val essayLink = task.saveFile("persuasive_essay.md", finalEssay.toByteArray(StandardCharsets.UTF_8))
          log.info("Saved complete essay to: $essayLink")


          finalTask.add(finalEssay.renderMarkdown(true))
          finalTask.complete()
          transcript?.write("## Complete Essay\n\n".toByteArray())
          transcript?.write(finalEssay.toByteArray())
          transcript?.write("\n\n".toByteArray())


          // Final statistics
          val totalTime = System.currentTimeMillis() - startTime

          overviewTask.add(
            buildString {
              appendLine()
              appendLine("---")
              appendLine()
              appendLine("## ✅ Generation Complete")
              appendLine()
              appendLine("**Statistics:**")
              appendLine("- Total Word Count: $cumulativeWordCount")
              appendLine("- Target Word Count: ${executionConfig.target_word_count}")
              appendLine("- Completion: ${(cumulativeWordCount.toFloat() / executionConfig.target_word_count * 100).toInt()}%")
              appendLine("- Number of Arguments: ${argumentSections.size}")
              appendLine("- Counterarguments: ${if (executionConfig.include_counterarguments) "✓ Included" else "✗ Not included"}")
              appendLine("- Revision Passes: ${executionConfig.revision_passes}")
              appendLine("- Total Time: ${totalTime / 1000.0}s")
              appendLine()
              appendLine(
                "**Completed:** ${
                  LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }"
              )
            }.renderMarkdown(true)
          )
          overviewTask.complete()


          transcript?.write("---\n\n".toByteArray())
          transcript?.write("## Generation Complete\n\n".toByteArray())
          transcript?.write("**Total Word Count:** $cumulativeWordCount\n\n".toByteArray())
          transcript?.write("**Total Time:** ${totalTime / 1000.0}s\n\n".toByteArray())
          transcript?.write(
            "**Completed:** ${
              LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }\n\n".toByteArray()
          )


          val (transcriptLink, _) = task.createFile("transcript.md")

          // Concise summary for resultFn with file links
          val finalResult = buildString {
            appendLine("# Persuasive Essay Summary: ${outline.title}")
            appendLine()
            appendLine("A complete persuasive essay of **$cumulativeWordCount words** was generated in **${totalTime / 1000.0}s**.")
            appendLine()
            appendLine("**Thesis:** $thesis")
            appendLine()
            appendLine("**Structure:**")
            appendLine("- Introduction with compelling hook")
            appendLine("- ${argumentSections.size} main arguments with evidence")
            if (executionConfig.include_counterarguments) {
              appendLine("- Counterarguments and rebuttals")
            }
            appendLine("- Conclusion with ${executionConfig.call_to_action} call to action")
            appendLine()
            appendLine("## Output Files")
            appendLine()
            appendLine("- **Complete Essay:** <a href='$essayLink' target='_blank'>$essayLink</a>")
            appendLine("  - <a href='${essayLink.removeSuffix(".md")}.html' target='_blank'>HTML</a>")
            appendLine("  - <a href='${essayLink.removeSuffix(".md")}.pdf' target='_blank'>PDF</a>")
            appendLine()
            appendLine("- **Transcript:** <a href='$transcriptLink' target='_blank'>$transcriptLink</a>")
            appendLine("  - <a href='${transcriptLink.removeSuffix(".md")}.html' target='_blank'>HTML</a>")
            appendLine("  - <a href='${transcriptLink.removeSuffix(".md")}.pdf' target='_blank'>PDF</a>")
          }

          log.info("PersuasiveEssayTask completed: words=$cumulativeWordCount, arguments=${argumentSections.size}, time=${totalTime}ms")

          task.safeComplete(
            "Persuasive essay generation complete: $cumulativeWordCount words in ${totalTime / 1000}s",
            log
          )

          if (orchestrationConfig.autoFix) {
            resultFn(finalResult)
          } else {
            val footer = acceptButtonFooter(task.ui) {
              resultFn(finalResult)
            }
            task.add(footer)
          }

        } catch (e: Exception) {
          log.error("Error during persuasive essay generation: ${e.message}", e)

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
            }.renderMarkdown(true)
          )
          overviewTask.complete()
          task.error(e)
          transcript?.write(
            """
                        <details>
                        <summary>Error: ${e.message}</summary>
                        ```
                        ${e.stackTraceToString()}
                        ```
                        </details>
                    """.trimIndent().toByteArray()
          )


          val errorOutput = buildString {
            appendLine("# Error in Persuasive Essay Generation")
            appendLine()
            appendLine("**Thesis:** $thesis")
            appendLine()
            appendLine("**Error:** ${e.message}")
            appendLine()
            if (resultBuilder.isNotEmpty()) {
              appendLine("## Partial Results")
              appendLine()
              appendLine(resultBuilder.toString())
            }
          }
          resultFn(errorOutput)
        }
      } finally {
        transcript?.close()
      }

    }
  }


  private fun generateCoverImage(
    task: SessionTask,
    tabs: TabbedDisplay,
    title: String,
    audience: String,
    tone: String,
    transcript: OutputStream?,
    orchestrationConfig: OrchestrationConfig
  ) {
    try {
      log.info("Generating cover image for: $title")
      val imageTask = tabs.newTask("Cover Image")
      imageTask.add(
        buildString {
          appendLine("# Cover Image")
          appendLine()
          appendLine("**Status:** Generating cover image...")
          appendLine()
        }.renderMarkdown(true)
      )
      imageTask.update()

      val imageAgent = ImageProcessingAgent(
        prompt = "Create a professional, compelling cover image for a persuasive essay",
        model = orchestrationConfig.defaultImage.getChildClient(task),
        temperature = 0.8,
      )

      val coverPrompt = "Persuasive Essay: $title\nTarget Audience: $audience\nTone: $tone"
      val result = imageAgent.answer(listOf(ImageAndText(coverPrompt)))
      val image = result.image

      // Save image
      val baos = ByteArrayOutputStream()
      ImageIO.write(image, "png", baos)
      val link = task.saveFile("00_cover_image.png", baos.toByteArray())
      log.debug("Saved cover image to: $link")

      // Create display link
      val imageHtml = """
        <div class='cover-image'>
          <h3>$title</h3>
          <p><em>For $audience</em></p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Cover' style='max-width: 600px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.2);' />
          </a>
        </div>
      """.trimIndent()
      imageTask.add(imageHtml.renderMarkdown(true))
      imageTask.update()

      transcript?.write("## Cover Image\n\n".toByteArray())
      transcript?.write("**Prompt:** ${result.text}\n\n".toByteArray())
      transcript?.write("![Cover Image]($link)\n\n".transcriptFilter().toByteArray())

      imageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
      imageTask.complete()
    } catch (e: Exception) {
      log.error("Failed to generate cover image: ${e.message}")
      transcript?.write(
        """
                <details>
                <summary>Cover Image Generation Failed</summary>
                ${e.message}
                </details>
            """.trimIndent().toByteArray()
      )
    }
  }

  private fun generateOutlineImage(
    task: SessionTask,
    tabs: TabbedDisplay,
    title: String,
    outline: EssayOutline,
    transcript: OutputStream?,
    orchestrationConfig: OrchestrationConfig
  ) {
    try {
      log.info("Generating outline visualization image")
      val imageTask = tabs.newTask("Outline Visualization")
      imageTask.add(
        buildString {
          appendLine("# Outline Visualization")
          appendLine()
          appendLine("**Status:** Generating outline visualization...")
          appendLine()
        }.renderMarkdown(true)
      )
      imageTask.update()

      val imageAgent = ImageProcessingAgent(
        prompt = "Create an infographic-style visualization of the essay outline and argument structure",
        model = orchestrationConfig.defaultImage.getChildClient(task),
        temperature = 0.7,
      )

      val outlinePrompt = buildString {
        append("Essay Title: $title\n")
        append("Thesis: ${outline.thesis_statement}\n")
        append("Arguments:\n")
        outline.arguments.forEach { arg ->
          append("${arg.number}. ${arg.claim}\n")
        }
      }

      val result = imageAgent.answer(listOf(ImageAndText(outlinePrompt)))
      val image = result.image

      // Save image
      val baos = ByteArrayOutputStream()
      ImageIO.write(image, "png", baos)
      val link = task.saveFile("01_outline_visualization.png", baos.toByteArray())
      log.debug("Saved outline visualization to: $link")

      // Create display link
      val imageHtml = """
        <div class='outline-image'>
          <h4>Argument Structure</h4>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Outline' style='max-width: 600px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
      imageTask.add(imageHtml.renderMarkdown(true))
      imageTask.update()

      transcript?.write("## Outline Visualization\n\n".toByteArray())
      transcript?.write("**Prompt:** ${result.text}\n\n".toByteArray())
      transcript?.write("![Outline]($link)\n\n".transcriptFilter().toByteArray())

      imageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
      imageTask.complete()
    } catch (e: Exception) {
      log.error("Failed to generate outline visualization: ${e.message}")
      transcript?.write(
        """
                <details>
                <summary>Outline Image Generation Failed</summary>
                ${e.message}
                </details>
            """.trimIndent().toByteArray()
      )
    }
  }

  private fun generateArgumentImage(
    task: SessionTask,
    tabs: TabbedDisplay,
    argumentNumber: Int,
    claim: String,
    content: String,
    transcript: OutputStream?,
    orchestrationConfig: OrchestrationConfig
  ) {
    try {
      log.info("Generating image for argument $argumentNumber")
      val imageTask = tabs.newTask("Argument $argumentNumber Image")
      imageTask.add(
        buildString {
          appendLine("# Argument $argumentNumber Visualization")
          appendLine()
          appendLine("**Status:** Generating argument visualization...")
          appendLine()
        }.renderMarkdown(true)
      )
      imageTask.update()

      val imageAgent = ImageProcessingAgent(
        prompt = "Create a visual representation that illustrates this persuasive argument",
        model = orchestrationConfig.defaultImage.getChildClient(task),
        temperature = 0.7,
      )

      val argumentPrompt = buildString {
        append("Argument $argumentNumber: $claim\n")
        append("Summary: ${content.take(300)}")
      }

      val result = imageAgent.answer(listOf(ImageAndText(argumentPrompt)))
      val image = result.image

      // Save image
      val baos = ByteArrayOutputStream()
      ImageIO.write(image, "png", baos)
      val link = task.saveFile("argument_${argumentNumber}_image.png", baos.toByteArray())
      log.debug("Saved argument $argumentNumber image to: $link")

      // Create display link
      val imageHtml = """
        <div class='argument-image'>
          <h4>Argument $argumentNumber: ${claim.take(60)}</h4>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Argument $argumentNumber' style='max-width: 500px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
      imageTask.add(imageHtml.renderMarkdown(true))
      imageTask.update()

      transcript?.write("#### Argument $argumentNumber Image\n\n".toByteArray())
      transcript?.write("**Prompt:** ${result.text}\n\n".toByteArray())
      transcript?.write("![Argument $argumentNumber]($link)\n\n".transcriptFilter().toByteArray())

      imageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
      imageTask.complete()
    } catch (e: Exception) {
      log.error("Failed to generate argument $argumentNumber image: ${e.message}")
      transcript?.write(
        """
                <details>
                <summary>Argument $argumentNumber Image Generation Failed</summary>
                ${e.message}
                </details>
            """.trimIndent().toByteArray()
      )
    }
  }

  private fun generateCounterargumentImage(
    task: SessionTask,
    tabs: TabbedDisplay,
    content: String,
    transcript: OutputStream?,
    orchestrationConfig: OrchestrationConfig
  ) {
    try {
      log.info("Generating counterargument visualization image")
      val imageTask = tabs.newTask("Counterargument Image")
      imageTask.add(
        buildString {
          appendLine("# Counterargument Visualization")
          appendLine()
          appendLine("**Status:** Generating counterargument visualization...")
          appendLine()
        }.renderMarkdown(true)
      )
      imageTask.update()

      val imageAgent = ImageProcessingAgent(
        prompt = "Create a balanced visual representation showing counterarguments and rebuttals",
        model = orchestrationConfig.defaultImage.getChildClient(task),
        temperature = 0.7,
      )

      val counterPrompt = buildString {
        append("Counterarguments and Rebuttals\n")
        append("Summary: ${content.take(300)}")
      }

      val result = imageAgent.answer(listOf(ImageAndText(counterPrompt)))
      val image = result.image

      // Save image
      val baos = ByteArrayOutputStream()
      ImageIO.write(image, "png", baos)
      val link = task.saveFile("counterargument_image.png", baos.toByteArray())
      log.debug("Saved counterargument image to: $link")

      // Create display link
      val imageHtml = """
        <div class='counterargument-image'>
          <h4>Counterarguments & Rebuttals</h4>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Counterarguments' style='max-width: 500px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
      imageTask.add(imageHtml.renderMarkdown(true))
      imageTask.update()

      transcript?.write("## Counterargument Visualization\n\n".toByteArray())
      transcript?.write("**Prompt:** ${result.text}\n\n".toByteArray())
      transcript?.write("![Counterarguments]($link)\n\n".transcriptFilter().toByteArray())

      imageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
      imageTask.complete()
    } catch (e: Exception) {
      log.error("Failed to generate counterargument image: ${e.message}")
      transcript?.write(
        """
                <details>
                <summary>Counterargument Image Generation Failed</summary>
                ${e.message}
                </details>
            """.trimIndent().toByteArray()
      )
    }
  }


  private fun getContextFiles(): String {
    val relatedFiles = executionConfig?.related_files ?: return ""
    if (relatedFiles.isEmpty()) return ""
    log.debug("Loading ${relatedFiles.size} related context files")

    return buildString {
      appendLine("## Related Research Files")
      appendLine()
      relatedFiles.forEach { file ->
        try {
          val filePath = root.resolve(file)
          if (filePath.toFile().exists()) {
            log.debug("Successfully loaded context file: $file")
            appendLine("### $file")
            appendLine("```")
            appendLine(filePath.toFile().readText().truncateForDisplay(1500))
            appendLine("```")
            appendLine()
          } else {
            log.warn("Context file not found: $file")
          }
        } catch (e: Exception) {
          log.warn("Error reading file: $file", e)
        }
      }
    }
  }


  companion object {
    private val log: Logger = LoggerFactory.getLogger(PersuasiveEssayTask::class.java)

    @JvmStatic
    val PersuasiveEssay = TaskType(
      name = "PersuasiveEssay",
      category = "Writing",
      taskClass = PersuasiveEssayTask::class.java,
      executionConfigClass = PersuasiveEssayTaskExecutionConfigData::class.java,
      taskSettingsClass = PersuasiveEssayTaskTypeConfig::class.java,
      description = "Generate compelling persuasive essays with structured arguments",
      tooltipHtml = """
                        Generates complete, well-structured persuasive essays using rhetorical techniques.
                        <ul>
                          <li>Creates detailed outline with thesis, arguments, and counterarguments</li>
                          <li>Writes compelling introduction with hook and background</li>
                          <li>Develops main arguments with evidence and rhetorical devices</li>
                          <li>Addresses counterarguments with strong rebuttals</li>
                          <li>Crafts powerful conclusion with call to action</li>
                          <li>Supports multiple tones and target audiences</li>
                          <li>Includes optional revision passes for quality</li>
                          <li>Uses ethos, pathos, and logos for persuasive impact</li>
                          <li>Ideal for opinion pieces, proposals, advocacy, and academic arguments</li>
                        </ul>
                      """,
    )
  }
}