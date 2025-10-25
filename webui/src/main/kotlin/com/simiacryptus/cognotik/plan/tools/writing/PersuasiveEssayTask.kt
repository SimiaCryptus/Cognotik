package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.reasoning.safeComplete
import com.simiacryptus.cognotik.plan.tools.reasoning.truncateForDisplay
import com.simiacryptus.cognotik.plan.tools.reasoning.validateAndGetApi
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PersuasiveEssayTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: PersuasiveEssayTaskExecutionConfigData?
) : AbstractTask<PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class PersuasiveEssayTaskExecutionConfigData(
    @Description("The thesis statement or position to argue for")
    val thesis: String? = null,

    @Description("The target audience (e.g., 'general public', 'academics', 'policymakers', 'business leaders')")
    val target_audience: String = "general public",

    @Description("The tone of the essay (e.g., 'formal', 'conversational', 'passionate', 'analytical')")
    val tone: String = "formal",

    @Description("Target word count for the complete essay")
    val target_word_count: Int = 1500,

    @Description("Number of main arguments to develop")
    val num_arguments: Int = 3,

    @Description("Whether to include counterarguments and rebuttals")
    val include_counterarguments: Boolean = true,

    @Description("Whether to use rhetorical devices (ethos, pathos, logos)")
    val use_rhetorical_devices: Boolean = true,

    @Description("Whether to include statistical evidence and citations")
    val include_evidence: Boolean = true,

    @Description("Whether to use analogies and examples")
    val use_analogies: Boolean = true,

    @Description("Call to action type (MUST BE one of: 'strong', 'moderate', 'reflective', 'none')")
    val call_to_action: String = "strong",

    @Description("Number of revision passes for quality improvement")
    val revision_passes: Int = 1,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    val input_files: List<String>? = null,


    @Description("Related files or research to incorporate")
    val related_files: List<String>? = null,

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
    val startTime = System.currentTimeMillis()
    log.info("Starting PersuasiveEssayTask for thesis: '${executionConfig?.thesis}', input_files: ${executionConfig?.input_files?.size ?: 0}")
    // Create transcript file
    val transcript = transcript(task)
    transcript?.let { stream ->
      stream.write("# Persuasive Essay Generation Transcript\n\n".toByteArray())
      stream.write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n".toByteArray())
      stream.write("**Thesis:** ${executionConfig?.thesis}\n\n".toByteArray())
      stream.write("---\n\n".toByteArray())
      stream.flush()
    }


    // Validate configuration
    executionConfig?.validate()?.let { validationError ->
      log.error("Configuration validation failed: $validationError")
      task.safeComplete("CONFIGURATION ERROR: $validationError", log)
      task.error(ValidatedObject.ValidationError(validationError, executionConfig))
      resultFn("CONFIGURATION ERROR: $validationError")
      return
    }

    val thesis = executionConfig?.thesis
    if (thesis.isNullOrBlank()) {
      log.error("No thesis specified for persuasive essay")
      task.safeComplete("CONFIGURATION ERROR: No thesis specified", log)
      resultFn("CONFIGURATION ERROR: No thesis specified")
      return
    }

    val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return

    val tabs = TabbedDisplay(task)

    // Overview tab
    val overviewTask = task.ui.newTask(false)
    tabs["Overview"] = overviewTask.placeholder

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
    overviewTask.add(overviewContent.renderMarkdown)
    task.update()
    transcript?.let { stream ->
      stream.write("## Configuration\n\n".toByteArray())
      stream.write(overviewContent.toByteArray())
      stream.write("\n\n".toByteArray())
      stream.flush()
    }

    val resultBuilder = StringBuilder()
    resultBuilder.append("# Persuasive Essay: $thesis\n\n")

    try {
      // Gather context
      val priorContext = getPriorCode(agent.executionState)
      val inputFileContent = getInputFileContent()
      val contextFiles = getContextFiles()

      if (priorContext.isNotBlank() || inputFileContent.isNotBlank() || contextFiles.isNotBlank()) {
        log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
        val contextTask = task.ui.newTask(false)
        tabs["Research Context"] = contextTask.placeholder
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
          }.renderMarkdown
        )
        task.update()
      }

      // Phase 1: Create outline
      log.info("Phase 1: Creating essay outline")
      val outlineTask = task.ui.newTask(false)
      tabs["Outline"] = outlineTask.placeholder

      outlineTask.add(
        buildString {
          appendLine("# Essay Outline")
          appendLine()
          appendLine("**Status:** Creating structured outline...")
          appendLine()
        }.renderMarkdown
      )
      task.update()

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
        parsingChatter = orchestrationConfig.parsingChatter
      )

      var outline = outlineAgent.answer(listOf("Generate outline")).obj

      // Validate outline
      outline.validate()?.let { validationError ->
        log.error("Outline validation failed: $validationError")
        outlineTask.error(ValidatedObject.ValidationError(validationError, outline))
        task.safeComplete("Outline validation failed: $validationError", log)
        resultFn("ERROR: Outline validation failed: $validationError")
        return
      }

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
      outlineTask.add(outlineContent.renderMarkdown)
      task.update()
      transcript?.let { stream ->
        stream.write("## Essay Outline\n\n".toByteArray())
        stream.write(outlineContent.toByteArray())
        stream.write("\n\n".toByteArray())
        stream.flush()
      }

      overviewTask.add("✅ Phase 1 Complete: Outline created\n".renderMarkdown)
      overviewTask.add("\n### Phase 2: Introduction\n*Writing compelling introduction...*\n".renderMarkdown)
      task.update()

      // Phase 2: Write Introduction
      log.info("Phase 2: Writing introduction")
      val introTask = task.ui.newTask(false)
      tabs["Introduction"] = introTask.placeholder

      introTask.add(
        buildString {
          appendLine("# Introduction")
          appendLine()
          appendLine("**Status:** Writing introduction...")
          appendLine()
        }.renderMarkdown
      )
      task.update()

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
        parsingChatter = orchestrationConfig.parsingChatter
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
        }.renderMarkdown
      )
      task.update()
      transcript?.let { stream ->
        stream.write("## Introduction\n\n".toByteArray())
        stream.write(introduction.content.toByteArray())
        stream.write("\n\n**Word Count:** ${introduction.word_count}\n\n".toByteArray())
        stream.flush()
      }


      resultBuilder.append(introduction.content)
      resultBuilder.append("\n\n")

      overviewTask.add("✅ Phase 2 Complete: Introduction written (${introduction.word_count} words)\n".renderMarkdown)
      overviewTask.add("\n### Phase 3: Body Arguments\n*Developing main arguments...*\n".renderMarkdown)
      task.update()

      // Phase 3: Write each argument
      log.info("Phase 3: Writing body arguments")
      val argumentSections = mutableListOf<EssaySection>()
      var cumulativeWordCount = introduction.word_count

      outline.arguments.forEachIndexed { index, argOutline ->
        log.info("Writing argument ${index + 1}/${outline.arguments.size}: ${argOutline.claim}")

        overviewTask.add("- Argument ${index + 1}: ${argOutline.claim.truncateForDisplay(50)} ".renderMarkdown)
        task.update()

        val argTask = task.ui.newTask(false)
        tabs["Argument ${index + 1}"] = argTask.placeholder

        argTask.add(
          buildString {
            appendLine("# Argument ${index + 1}")
            appendLine()
            appendLine("**Status:** Writing argument...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

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
          parsingChatter = orchestrationConfig.parsingChatter
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
          }.renderMarkdown
        )
        task.update()
        transcript?.let { stream ->
          stream.write("## Argument ${index + 1}: ${argOutline.claim}\n\n".toByteArray())
          stream.write(argumentSection.content.toByteArray())
          stream.write("\n\n**Word Count:** ${argumentSection.word_count}\n\n".toByteArray())
          stream.flush()
        }


        resultBuilder.append(argumentSection.content)
        resultBuilder.append("\n\n")

        overviewTask.add("✅ (${argumentSection.word_count} words)\n".renderMarkdown)
        task.update()
      }

      overviewTask.add("✅ Phase 3 Complete: All arguments written\n".renderMarkdown)

      // Phase 4: Counterarguments (if enabled)
      if (executionConfig.include_counterarguments && outline.counterarguments.isNotEmpty()) {
        overviewTask.add("\n### Phase 4: Counterarguments\n*Addressing opposing views...*\n".renderMarkdown)
        task.update()

        log.info("Phase 4: Writing counterarguments and rebuttals")
        val counterTask = task.ui.newTask(false)
        tabs["Counterarguments"] = counterTask.placeholder

        counterTask.add(
          buildString {
            appendLine("# Counterarguments & Rebuttals")
            appendLine()
            appendLine("**Status:** Writing counterarguments...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

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
          parsingChatter = orchestrationConfig.parsingChatter
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
          }.renderMarkdown
        )
        task.update()
        transcript?.let { stream ->
          stream.write("## Counterarguments & Rebuttals\n\n".toByteArray())
          stream.write(counterSection.content.toByteArray())
          stream.write("\n\n**Word Count:** ${counterSection.word_count}\n\n".toByteArray())
          stream.flush()
        }


        resultBuilder.append(counterSection.content)
        resultBuilder.append("\n\n")

        overviewTask.add("✅ Phase 4 Complete: Counterarguments addressed (${counterSection.word_count} words)\n".renderMarkdown)
      }

      // Phase 5: Conclusion
      overviewTask.add("\n### Phase 5: Conclusion\n*Writing powerful conclusion...*\n".renderMarkdown)
      task.update()

      log.info("Phase 5: Writing conclusion")
      val conclusionTask = task.ui.newTask(false)
      tabs["Conclusion"] = conclusionTask.placeholder

      conclusionTask.add(
        buildString {
          appendLine("# Conclusion")
          appendLine()
          appendLine("**Status:** Writing conclusion...")
          appendLine()
        }.renderMarkdown
      )
      task.update()

      val conclusionAgent = ParsedAgent(
        resultClass = EssaySection::class.java,
        prompt = """
You are an expert persuasive writer. Write a powerful conclusion for this essay.

Overall Thesis: $thesis
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}
Call to Action Type: ${executionConfig.call_to_action}

Main Arguments Presented:
${argumentSections.mapIndexed { i, arg -> "${i + 1}. ${arg.persuasive_elements.firstOrNull() ?: arg.content.take(100)}" }.joinToString("\n")}

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
        parsingChatter = orchestrationConfig.parsingChatter
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
        }.renderMarkdown
      )
      task.update()
      transcript?.let { stream ->
        stream.write("## Conclusion\n\n".toByteArray())
        stream.write(conclusion.content.toByteArray())
        stream.write("\n\n**Word Count:** ${conclusion.word_count}\n\n".toByteArray())
        stream.flush()
      }


      resultBuilder.append(conclusion.content)
      resultBuilder.append("\n\n")

      overviewTask.add("✅ Phase 5 Complete: Conclusion written (${conclusion.word_count} words)\n".renderMarkdown)

      // Phase 6: Revision (if enabled)
      if (executionConfig.revision_passes > 0) {
        overviewTask.add("\n### Phase 6: Revision\n*Refining and polishing...*\n".renderMarkdown)
        task.update()

        log.info("Phase 6: Performing ${executionConfig.revision_passes} revision pass(es)")
        val revisionTask = task.ui.newTask(false)
        tabs["Revision"] = revisionTask.placeholder

        revisionTask.add(
          buildString {
            appendLine("# Revision Process")
            appendLine()
            appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

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
            }.renderMarkdown
          )
          task.update()
          transcript?.let { stream ->
            stream.write("### Revision Pass ${passNum + 1}\n\n".toByteArray())
            stream.write("Completed revision pass ${passNum + 1} of ${executionConfig.revision_passes}\n\n".toByteArray())
            stream.flush()
          }
        }

        overviewTask.add("✅ Phase 6 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown)
      }

      // Phase 7: Final Assembly
      overviewTask.add("\n### Phase 7: Final Assembly\n*Compiling complete essay...*\n".renderMarkdown)
      task.update()

      log.info("Phase 7: Assembling final essay")
      val finalTask = task.ui.newTask(false)
      tabs["Complete Essay"] = finalTask.placeholder

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
      val (essayLink, essayFile) = task.createFile("persuasive_essay.md")
      essayFile?.writeText(finalEssay, StandardCharsets.UTF_8)
      log.info("Saved complete essay to: $essayLink")


      finalTask.add(finalEssay.renderMarkdown)
      task.update()
      // Update transcript with final essay
      transcript?.let { stream ->
        stream.write("## Complete Essay\n\n".toByteArray())
        stream.write(finalEssay.toByteArray())
        stream.write("\n\n".toByteArray())
        stream.flush()
      }


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
          appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        }.renderMarkdown
      )
      task.update()
      transcript?.let { stream ->
        stream.write("---\n\n".toByteArray())
        stream.write("## Generation Complete\n\n".toByteArray())
        stream.write("**Total Word Count:** $cumulativeWordCount\n\n".toByteArray())
        stream.write("**Total Time:** ${totalTime / 1000.0}s\n\n".toByteArray())
        stream.write("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n".toByteArray())
        stream.flush()
      }


      // Close transcript and get link
      transcript?.close()
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

      task.safeComplete("Persuasive essay generation complete: $cumulativeWordCount words in ${totalTime / 1000}s", log)
      resultFn(finalResult)

    } catch (e: Exception) {
      log.error("Error during persuasive essay generation", e)
      task.error(e)

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
        }.renderMarkdown
      )
      task.update()
      transcript?.let { stream ->
        stream.write("---\n\n".toByteArray())
        stream.write("## Error Occurred\n\n".toByteArray())
        stream.write("**Error:** ${e.message}\n\n".toByteArray())
        stream.flush()
      }
      transcript?.close()


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
  }

  private fun getInputFileContent(): String {
    val inputFiles = executionConfig?.input_files ?: return ""
    if (inputFiles.isEmpty()) return ""
    log.debug("Loading ${inputFiles.size} input files")
    return buildString {
      appendLine("## Input Files Content")
      appendLine()
      inputFiles.forEach { pattern: String ->
        try {
          val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
          val matchedFiles = FileSelectionUtils.filteredWalk(root.toFile()) {
            when {
              FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
              matcher.matches(root.relativize(it.toPath())) -> true
              it.isDirectory -> true
              else -> false
            }
          }.filter { it.isFile && it.exists() }.distinct().sortedBy { it }
          matchedFiles.forEach { file ->
            log.debug("Loading input file: ${file.path}")
            appendLine("### ${root.relativize(file.toPath())}")
            appendLine("```")
            appendLine(file.readText().truncateForDisplay(1000))
            appendLine("```")
            appendLine()
          }
        } catch (e: Exception) {
          log.warn("Error reading input files matching pattern: $pattern", e)
        }
      }
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

  private fun transcript(task: SessionTask): FileOutputStream? {
    val (link, file) = task.createFile("transcript.md")
    val markdownTranscript = file?.outputStream()
    task.complete(
      "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
        link.removeSuffix(
          ".md"
        )
      }.pdf' target='_blank'>pdf</a>"
    )
    return markdownTranscript
  }


  companion object {
    private val log: Logger = LoggerFactory.getLogger(PersuasiveEssayTask::class.java)
    val PersuasiveEssay = TaskType(
      "PersuasiveEssay",
      PersuasiveEssayTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Generate compelling persuasive essays with structured arguments",
      """
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
            """
    )
  }
}