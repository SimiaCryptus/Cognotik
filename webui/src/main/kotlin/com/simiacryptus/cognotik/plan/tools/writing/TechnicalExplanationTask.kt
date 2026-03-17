package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems

class TechnicalExplanationTask(
  orchestrationConfig: OrchestrationConfig, planTask: TechnicalExplanationTaskExecutionConfigData?
) : AbstractTask<TechnicalExplanationTask.TechnicalExplanationTaskExecutionConfigData, TechnicalExplanationTask.TechnicalExplanationTypeConfig>(
  orchestrationConfig, planTask
) {
  class TechnicalExplanationTypeConfig(
    @Description("Prompt template for outline generation. Use {topic}, {audience}, {audience_guidance}, {detail_level}, {detail_guidance}, {format}, {context}, {docs}, {terminology_instruction}, {analogy_instruction}, {code_instruction}, {visual_instruction} placeholders.")
    var outline_prompt: String = buildString {
      appendLine("You are an expert technical educator and communicator. Create a detailed outline for explaining this topic.")
      appendLine()
      appendLine("Topic: {topic}")
      appendLine()
      appendLine("Target Audience: {audience}")
      appendLine("Audience Guidance: {audience_guidance}")
      appendLine()
      appendLine("Level of Detail: {detail_level}")
      appendLine("Detail Guidance: {detail_guidance}")
      appendLine()
      appendLine("Format: {format}")
      appendLine()
      appendLine("{context}")
      appendLine("{docs}")
      appendLine()
      appendLine("Create an outline that includes:")
      appendLine("1. A clear, engaging title")
      appendLine("2. Brief overview (2-3 sentences) of what will be explained")
      appendLine("3. 3-6 key concepts to cover, ordered logically (simple to complex or general to specific)")
      appendLine("4. {terminology_instruction}")
      appendLine("5. {analogy_instruction}")
      appendLine("6. {code_instruction}")
      appendLine("7. {visual_instruction}")
      appendLine()
      appendLine("For each key concept, specify:")
      appendLine("- The concept name")
      appendLine("- Why it's important to understand")
      appendLine("- Sub-topics to cover")
      appendLine("- Complexity level (basic, intermediate, advanced)")
      appendLine("- Estimated paragraphs needed")
      appendLine()
      appendLine("Ensure the outline:")
      appendLine("- Builds understanding progressively")
      appendLine("- Matches the {audience} audience level")
      appendLine("- Provides {detail_level} level of detail")
      appendLine("- Follows {format} format conventions")
    },
    @Description("Prompt template for section generation. Use {topic}, {audience}, {audience_guidance}, {concept}, {importance}, {subtopics}, {complexity}, {previous_context}, {analogies_section}, {code_examples_section}, {analogy_instruction}, {subtopics_instruction}, {examples_instruction}, {code_instruction}, {visual_instruction}, {target_audience}, {estimated_paragraphs}, {format}, {code_language_instruction} placeholders.")
    var section_prompt: String = buildString {
      appendLine("You are an expert technical educator. Write a clear, engaging explanation of this concept.")
      appendLine()
      appendLine("Overall Topic: {topic}")
      appendLine("Target Audience: {audience}")
      appendLine("Audience Guidance: {audience_guidance}")
      appendLine()
      appendLine("Concept to Explain: {concept}")
      appendLine("Importance: {importance}")
      appendLine("Subtopics: {subtopics}")
      appendLine("Complexity: {complexity}")
      appendLine()
      appendLine("{previous_context}")
      appendLine()
      appendLine("{analogies_section}")
      appendLine()
      appendLine("{code_examples_section}")
      appendLine()
      appendLine("Write a section that:")
      appendLine("1. Opens with a clear introduction to the concept")
      appendLine("2. {analogy_instruction}")
      appendLine("3. Covers all subtopics: {subtopics_instruction}")
      appendLine("4. {examples_instruction}")
      appendLine("5. {code_instruction}")
      appendLine("6. {visual_instruction}")
      appendLine("7. Provides 2-4 key takeaways at the end")
      appendLine("8. Transitions smoothly to the next concept")
      appendLine()
      appendLine("Make it:")
      appendLine("- Clear and accessible to {target_audience}")
      appendLine("- Engaging and well-structured")
      appendLine("- Approximately {estimated_paragraphs} paragraphs")
      appendLine("- Following {format} format")
      appendLine()
      appendLine("{code_language_instruction}")
    },
    @Description("Prompt template for comparison generation. Use {topic}, {audience}, {sections_list} placeholders.")
    var comparison_prompt: String = buildString {
      appendLine("You are an expert technical educator. Compare and contrast this topic with related concepts.")
      appendLine()
      appendLine("Topic: {topic}")
      appendLine("Target Audience: {audience}")
      appendLine()
      appendLine("Content Covered:")
      appendLine("{sections_list}")
      appendLine()
      appendLine("Provide comparisons that:")
      appendLine("1. Identify 2-3 related or commonly confused concepts")
      appendLine("2. Explain key similarities")
      appendLine("3. Highlight important differences")
      appendLine("4. Clarify when to use each")
      appendLine("5. Help readers understand the boundaries and relationships")
      appendLine()
      appendLine("Make comparisons clear and helpful for {audience}.")
    },
    @Description("Prompt template for revision. Use {explanation}, {audience}, {detail_level}, {format} placeholders.")
    var revision_prompt: String = buildString {
      appendLine("You are an expert technical editor. Review and improve this explanation for clarity and effectiveness.")
      appendLine()
      appendLine("Current Explanation:")
      appendLine("{explanation}")
      appendLine()
      appendLine("Target Audience: {audience}")
      appendLine("Level of Detail: {detail_level}")
      appendLine()
      appendLine("Focus on:")
      appendLine("1. Clarity and simplicity of language")
      appendLine("2. Logical flow and transitions")
      appendLine("3. Effectiveness of analogies and examples")
      appendLine("4. Accuracy of technical content")
      appendLine("5. Appropriateness for {audience}")
      appendLine("6. Completeness of coverage")
      appendLine("7. Engagement and readability")
      appendLine()
      appendLine("Maintain:")
      appendLine("- All key concepts and information")
      appendLine("- Code examples and their explanations")
      appendLine("- Technical accuracy")
      appendLine("- Approximate length")
      appendLine("- {format} format")
      appendLine()
      appendLine("Provide the complete revised explanation.")
    },
    @Description("Temperature for outline generation (0.0-1.0)")
    var outline_temperature: Double = 0.6,
    @Description("Temperature for section generation (0.0-1.0)")
    var section_temperature: Double = 0.7,
    @Description("Temperature for comparison generation (0.0-1.0)")
    var comparison_temperature: Double = 0.6,
    @Description("Temperature for revision (0.0-1.0)")
    var revision_temperature: Double = 0.5,
  ) : TaskTypeConfig()


  class TechnicalExplanationTaskExecutionConfigData @JvmOverloads constructor(
    @Description("The complex technical subject to explain")
    var topic: String? = null,

    @Description("Target audience expertise level. One of: 'layperson', 'beginner', 'intermediate', 'expert', 'manager', 'software_engineer', 'data_scientist', 'student'")
    var target_audience: String = "intermediate",

    @Description("Level of detail for the explanation. One of: 'high_level_overview', 'moderate_detail', 'detailed_walkthrough', 'comprehensive'")
    var level_of_detail: String = "moderate_detail",

    @Description("Whether to include code examples and snippets")
    var include_code_examples: Boolean = true,

    @Description("Explanation format. One of: 'markdown', 'q_and_a', 'step_by_step', 'narrative', 'tutorial'")
    var explanation_format: String = "markdown",

    @Description("Whether to generate analogies and metaphors")
    var use_analogies: Boolean = true,

    @Description("Whether to include visual descriptions or diagrams")
    var include_visual_descriptions: Boolean = true,

    @Description("Whether to define key terminology")
    var define_terminology: Boolean = true,

    @Description("Whether to include practical examples and use cases")
    var include_examples: Boolean = true,

    @Description("Whether to provide comparison with related concepts")
    var include_comparisons: Boolean = true,

    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    var input_files: List<String>? = null,

    @Description("Programming language for code examples (if applicable)")
    var code_language: String? = null,

    @Description("Number of revision passes for clarity improvement. Must be between 0 and 5.")
    var revision_passes: Int = 1,

    @Description("Related files or documentation to reference")
    var related_files: List<String>? = null,

    task_description: String? = null,
    task_dependencies: MutableList<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = "TechnicalExplanation",
    task_description = task_description ?: "Generate technical explanation for: '$topic'",
    task_dependencies = task_dependencies,
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (topic.isNullOrBlank()) {
        return "topic must not be null or blank"
      }
      val validAudiences = setOf(
        "layperson", "beginner", "intermediate", "expert", "manager", "software_engineer", "data_scientist", "student"
      )
      if (target_audience.lowercase() !in validAudiences) {
        target_audience = "intermediate"
      }
      val validDetailLevels = setOf(
        "high_level_overview", "moderate_detail", "detailed_walkthrough", "comprehensive"
      )
      if (level_of_detail.lowercase() !in validDetailLevels) {
        level_of_detail = "moderate_detail"
      }
      val validFormats = setOf("markdown", "q_and_a", "step_by_step", "narrative", "tutorial")
      if (explanation_format.lowercase() !in validFormats) {
        explanation_format = "markdown"
      }
      revision_passes = revision_passes.coerceIn(0, 5)
      if (!input_files.isNullOrEmpty()) {
        input_files = input_files?.filter { it.isNotBlank() }
      }
      return ValidatedObject.validateFields(this)
    }
  }

  class ExplanationOutline(
    @Description("The main topic title")
    var title: String = "",
    @Description("Brief overview of what will be explained")
    var overview: String = "",
    @Description("Key concepts to cover in order, from simple to complex")
    var key_concepts: List<ConceptOutline> = emptyList(),
    @Description("Core terminology that needs definition")
    var terminology: List<TermDefinition> = emptyList(),
    @Description("Analogies to use for complex concepts")
    var analogies: List<AnalogyMapping> = emptyList(),
    @Description("Code examples to include")
    var code_examples: List<CodeExampleOutline> = emptyList(),
    @Description("Visual descriptions or diagrams needed")
    var visual_descriptions: List<Any> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      title = title.trim()
      overview = overview.trim()
      if (title.isBlank()) title = "Technical Explanation"
      if (overview.isBlank()) overview = "An overview of the topic."
      return ValidatedObject.validateFields(this)
    }
  }

  class ConceptOutline(
    @Description("Concept name")
    var concept: String = "",
    @Description("Why this concept matters")
    var importance: String = "",
    @Description("Sub-topics to cover under this concept")
    var subtopics: List<String> = emptyList(),
    @Description("Complexity level. One of: 'basic', 'intermediate', 'advanced'")
    var complexity: String = "",
    @Description("Estimated number of paragraphs needed for this concept")
    var estimated_paragraphs: Int = 0
  ) : ValidatedObject

  class TermDefinition(
    @Description("The technical term to define")
    var term: String = "",
    @Description("Simple, accessible definition of the term")
    var definition: String = "",
    @Description("Context where this term is typically used")
    var context: String = ""
  ) : ValidatedObject

  class AnalogyMapping(
    @Description("The technical concept being explained")
    var technical_concept: String = "",
    @Description("The relatable real-world analogy")
    var analogy: String = "",
    @Description("How the analogy maps to the technical concept")
    var mapping_explanation: String = ""
  ) : ValidatedObject

  class CodeExampleOutline(
    @Description("What the code example demonstrates")
    var purpose: String = "",
    @Description("Programming language for the example")
    var language: String = "",
    @Description("Complexity level of the example. One of: 'basic', 'intermediate', 'advanced'")
    var complexity: String = "",
    @Description("Key points to highlight in the code")
    var key_points: List<String> = emptyList()
  ) : ValidatedObject

  class ExplanationSection(
    @Description("Section title summarizing the concept covered")
    var title: String = "",
    @Description("Full section content in markdown format")
    var content: String = "",
    @Description("Code snippets included in this section")
    var code_snippets: List<CodeSnippet> = emptyList(),
    @Description("Key takeaways the reader should remember from this section")
    var key_takeaways: List<String> = emptyList()
  ) : ValidatedObject

  class CodeSnippet(
    @Description("Programming language for syntax highlighting")
    var language: String = "",
    @Description("The code content")
    var code: String = "",
    @Description("Plain-language explanation of what the code does")
    var explanation: String = "",
    @Description("Key points to highlight about this code")
    var highlights: List<String> = emptyList()
  ) : ValidatedObject

  override fun promptSegment(): String = buildString {
    appendLine("TechnicalExplanation - Break down complex technical subjects into clear, digestible explanations")
    appendLine("  ** Specify the technical topic to explain")
    appendLine("  ** Define target audience expertise level")
    appendLine("  ** Set level of detail (overview to comprehensive)")
    appendLine("  ** Configure explanation format (markdown, Q&A, step-by-step, etc.)")
    appendLine("  ** Enable analogies and metaphors for clarity")
    appendLine("  ** Include code examples with explanations")
    appendLine("  ** Define key terminology")
    appendLine("  ** Provide visual descriptions")
    appendLine("  ** Include practical examples and use cases")
    appendLine("  ** Compare with related concepts")
    appendLine("  ** Performs outline creation, content generation, and iterative refinement")
    appendLine("  ** Produces clear, audience-appropriate technical explanations")
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val tabs = TabbedDisplay(task)
    val overviewTask = tabs.newTask("Overview")
    val transcript = task.newUserFileStream(transcriptFile())
    val resultBuilder = StringBuilder()

    task.ui.pool.submit {
      try {
        val startTime = System.currentTimeMillis()
        val config = executionConfig ?: throw RuntimeException("No configuration provided")

        config.validate()?.let { validationError ->
          val msg = "Configuration validation failed: $validationError"
          log.error(msg)
          task.error(ValidatedObject.ValidationError(validationError, config))
          transcript?.write(buildString {
            appendLine("## Configuration Error")
            appendLine(msg)
          }.toByteArray(StandardCharsets.UTF_8))
          task.complete(msg.renderMarkdown(true))
          resultFn("CONFIGURATION ERROR: $validationError")
          return@submit
        }

        val topic = config.topic
        if (topic.isNullOrBlank()) {
          val msg = "No topic specified for technical explanation"
          log.error(msg)
          task.complete(msg.renderMarkdown(true))
          resultFn("CONFIGURATION ERROR: No topic specified")
          return@submit
        }

        val api = defaultSmart.getChildClient(task)
        val fastApi = defaultFast.getChildClient(task)
        log.info("Starting TechnicalExplanationTask for topic: '$topic'")
        val userMessages = messages.filter { it.isNotBlank() }

        if (userMessages.isNotEmpty()) {
          log.info("Including ${userMessages.size} user message(s) in context")
        }

        val overviewContent = buildString {
          appendLine("# Technical Explanation Generation")
          appendLine()
          appendLine("**Topic:** $topic")
          appendLine()
        }
        overviewTask.add(overviewContent.renderMarkdown(true))
        transcript?.write(overviewContent.toByteArray(StandardCharsets.UTF_8))

        val configContent = buildString {
          appendLine("- Target Audience: ${config.target_audience}")
          appendLine("- Level of Detail: ${config.level_of_detail}")
          appendLine("- Format: ${config.explanation_format}")
          appendLine("- Include Code Examples: ${if (config.include_code_examples) "✓" else "✗"}")
          appendLine("- Use Analogies: ${if (config.use_analogies) "✓" else "✗"}")
          appendLine("- Define Terminology: ${if (config.define_terminology) "✓" else "✗"}")
          appendLine("- Include Visual Descriptions: ${if (config.include_visual_descriptions) "✓" else "✗"}")
          appendLine("- Include Examples: ${if (config.include_examples) "✓" else "✗"}")
          appendLine("- Include Comparisons: ${if (config.include_comparisons) "✓" else "✗"}")
          if (config.code_language != null) {
            appendLine("- Code Language: ${config.code_language}")
          }
          appendLine()
          appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
          appendLine()
          appendLine("---")
          appendLine()
        }
        overviewTask.add(configContent.renderMarkdown(true))
        transcript?.write(configContent.toByteArray(StandardCharsets.UTF_8))

        val phase1Content = buildString {
          appendLine("### Phase 1: Analysis & Outline")
          appendLine("*Analyzing topic and creating explanation structure...*")
        }
        overviewTask.add(phase1Content.renderMarkdown(true))
        transcript?.write(phase1Content.toByteArray(StandardCharsets.UTF_8))
        overviewTask.update()
        resultBuilder.append("# Technical Explanation: $topic\n\n")

        val priorContext = getPriorCode(agent.executionState)
        val contextFiles = getContextFiles()

        if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
          log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
          val contextTask = tabs.newTask("Reference Context")
          contextTask.add(
            buildString {
              appendLine("# Reference Context")
              appendLine()
              if (priorContext.isNotBlank()) {
                appendLine("## Prior Context")
                appendLine(priorContext.truncateForDisplay(2000))
                appendLine()
              }
              if (contextFiles.isNotBlank()) {
                appendLine("## Related Files")
                appendLine(contextFiles.truncateForDisplay(2000))
              }
            }.renderMarkdown(true)
          )
          contextTask.update()
          transcript?.write(
            buildString {
              appendLine("## Reference Context")
              appendLine("<details>")
              appendLine("<summary>Prior Context and Related Files</summary>")
              appendLine()
              appendLine("### Prior Context")
              appendLine(priorContext)
              appendLine()
              appendLine("### Related Files")
              appendLine(contextFiles)
              appendLine("</details>")
              appendLine()
            }.toByteArray(StandardCharsets.UTF_8)
          )
        }

        log.info("Phase 1: Creating explanation outline")
        val outlineTask = tabs.newTask("Outline")

        outlineTask.add(
          buildString {
            appendLine("# Explanation Outline")
            appendLine()
            appendLine("**Status:** Creating structured outline...")
            appendLine()
          }.renderMarkdown(true)
        )
        transcript?.write("\n# Explanation Outline\n\n".toByteArray(StandardCharsets.UTF_8))
        transcript?.write("**Status:** Creating structured outline...\n\n".toByteArray(StandardCharsets.UTF_8))
        outlineTask.update()

        val audienceGuidance = when (config.target_audience.lowercase()) {
          "layperson" -> "Assume no technical background. Use everyday language and avoid jargon."
          "beginner" -> "Assume basic familiarity with technology but limited domain knowledge."
          "intermediate" -> "Assume solid foundation in the field with some practical experience."
          "expert" -> "Assume deep technical knowledge. Focus on nuances and advanced concepts."
          "manager" -> "Focus on high-level concepts, business value, and practical implications."
          "software_engineer" -> "Assume programming knowledge. Include implementation details."
          "data_scientist" -> "Assume statistical and algorithmic knowledge. Include mathematical concepts."
          else -> "Adjust language to match the audience's technical level."
        }

        val detailGuidance = when (config.level_of_detail.lowercase()) {
          "high_level_overview" -> "Provide a bird's-eye view. Focus on the 'what' and 'why'."
          "moderate_detail" -> "Balance overview with key details. Cover 'what', 'why', and 'how' at a moderate depth."
          "detailed_walkthrough" -> "Provide comprehensive coverage with step-by-step explanations."
          "comprehensive" -> "Cover all aspects thoroughly, including edge cases and advanced topics."
          else -> "Provide moderate detail with clear explanations."
        }
        val terminologyInstruction = if (config.define_terminology) "5-10 essential terms that need definition" else "Key terminology (minimal)"
        val analogyInstruction = if (config.use_analogies) "2-4 analogies to make complex concepts relatable" else "Analogies (if absolutely necessary)"
        val codeInstruction = if (config.include_code_examples) "3-5 code examples to illustrate concepts" else "Code examples (minimal or none)"
        val visualInstruction = if (config.include_visual_descriptions) "Descriptions of diagrams or visualizations that would help" else "Visual aids (if critical)"
        val contextSection = if (priorContext.isNotBlank()) "Reference Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""
        val docsSection = if (contextFiles.isNotBlank()) "Additional Documentation:\n${contextFiles.truncateForDisplay(3000)}\n" else ""
        val outlinePrompt = (typeConfig?.outline_prompt ?: TechnicalExplanationTypeConfig().outline_prompt)
          .replace("{topic}", topic)
          .replace("{audience}", config.target_audience)
          .replace("{audience_guidance}", audienceGuidance)
          .replace("{detail_level}", config.level_of_detail)
          .replace("{detail_guidance}", detailGuidance)
          .replace("{format}", config.explanation_format)
          .replace("{context}", contextSection)
          .replace("{docs}", docsSection)
          .replace("{terminology_instruction}", terminologyInstruction)
          .replace("{analogy_instruction}", analogyInstruction)
          .replace("{code_instruction}", codeInstruction)
          .replace("{visual_instruction}", visualInstruction)


        val outlineAgent = ParsedAgent(
          resultClass = ExplanationOutline::class.java,
          prompt = outlinePrompt,
          model = api,
          temperature = typeConfig?.outline_temperature ?: 0.6,
          parsingChatter = fastApi
        )

        val outline = outlineAgent.answer(listOf("Generate outline")).obj

        outline.validate()?.let { validationError ->
          log.error("Outline validation failed: $validationError")
          outlineTask.error(ValidatedObject.ValidationError(validationError, outline))
          task.complete("Outline validation failed: $validationError".renderMarkdown(true))
          resultFn("CONFIGURATION ERROR: Outline validation failed: $validationError")
          return@submit
        }

        log.info("Generated outline: ${outline.key_concepts.size} concepts, ${outline.terminology.size} terms, ${outline.analogies.size} analogies")

        val outlineContent = buildString {
          appendLine("## ${outline.title}")
          appendLine()
          appendLine("### Overview")
          appendLine(outline.overview)
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("### Key Concepts")
          outline.key_concepts.forEachIndexed { index, concept ->
            appendLine("#### ${index + 1}. ${concept.concept}")
            appendLine()
            appendLine("**Importance:** ${concept.importance}")
            appendLine()
            appendLine("**Complexity:** ${concept.complexity}")
            appendLine()
            if (concept.subtopics.isNotEmpty()) {
              appendLine("**Subtopics:**")
              concept.subtopics.forEach { subtopic ->
                appendLine("- $subtopic")
              }
              appendLine()
            }
            appendLine("**Est. Paragraphs:** ${concept.estimated_paragraphs}")
            appendLine()
            appendLine("---")
            appendLine()
          }
          if (outline.terminology.isNotEmpty()) {
            appendLine("### Key Terminology")
            outline.terminology.forEach { term ->
              appendLine("**${term.term}:** ${term.definition}")
              if (term.context.isNotBlank()) {
                appendLine("  - *Context: ${term.context}*")
              }
              appendLine()
            }
            appendLine("---")
            appendLine()
          }
          if (outline.analogies.isNotEmpty()) {
            appendLine("### Analogies")
            outline.analogies.forEach { analogy ->
              appendLine("**${analogy.technical_concept}** ≈ ${analogy.analogy}")
              appendLine("  - ${analogy.mapping_explanation}")
              appendLine()
            }
            appendLine("---")
            appendLine()
          }
          if (outline.code_examples.isNotEmpty()) {
            appendLine("### Code Examples")
            outline.code_examples.forEachIndexed { index, example ->
              appendLine("${index + 1}. **${example.purpose}** (${example.language})")
              appendLine("   - Complexity: ${example.complexity}")
              if (example.key_points.isNotEmpty()) {
                appendLine("   - Key points: ${example.key_points.joinToString(", ")}")
              }
              appendLine()
            }
            appendLine("---")
            appendLine()
          }
          if (outline.visual_descriptions.isNotEmpty()) {
            appendLine("### Visual Aids")
            outline.visual_descriptions.forEach { visual ->
              appendLine("- ${visual.toJson()}")
            }
            appendLine()
          }
          appendLine("**Status:** ✅ Complete")
        }
        outlineTask.add(outlineContent.renderMarkdown(true))
        outlineTask.update()
        transcript?.write(outlineContent.toByteArray(StandardCharsets.UTF_8))

        overviewTask.add("✅ Phase 1 Complete: Outline created\n".renderMarkdown(true))
        overviewTask.add("\n### Phase 2: Content Generation\n*Writing explanation sections...*\n".renderMarkdown(true))
        overviewTask.update()

        log.info("Phase 2: Generating explanation content")
        val sections = mutableListOf<ExplanationSection>()

        outline.key_concepts.forEachIndexed { index, conceptOutline ->
          log.info("Writing section ${index + 1}/${outline.key_concepts.size}: ${conceptOutline.concept}")

          overviewTask.add(
            "- Section ${index + 1}: ${conceptOutline.concept.truncateForDisplay(50)} ".renderMarkdown(
              true
            )
          )
          overviewTask.update()

          val sectionTask = tabs.newTask("Section ${index + 1}")

          sectionTask.add(
            buildString {
              appendLine("# ${conceptOutline.concept}")
              appendLine()
              appendLine("**Status:** Writing section...")
              appendLine()
            }.renderMarkdown(true)
          )
          transcript?.write("\n# ${conceptOutline.concept}\n\n".toByteArray(StandardCharsets.UTF_8))
          transcript?.write("**Status:** Writing section...\n\n".toByteArray(StandardCharsets.UTF_8))
          sectionTask.update()

          val previousContext = if (sections.isNotEmpty()) {
            buildString {
              appendLine("## Previously Covered")
              sections.takeLast(2).forEach { prevSection ->
                appendLine("**${prevSection.title}:** ${prevSection.content.take(200)}...")
                appendLine()
              }
            }
          } else {
            "This is the first section."
          }

          val relevantAnalogies = outline.analogies.filter {
            it.technical_concept.contains(conceptOutline.concept, ignoreCase = true) || conceptOutline.concept.contains(
              it.technical_concept,
              ignoreCase = true
            )
          }

          val relevantCodeExamples = outline.code_examples.filter {
            it.purpose.contains(
              conceptOutline.concept,
              ignoreCase = true
            ) || conceptOutline.concept.contains(it.purpose, ignoreCase = true)
          }
          val analogiesSection = if (relevantAnalogies.isNotEmpty()) {
            buildString {
              appendLine("Analogies to Use:")
              relevantAnalogies.forEach { appendLine("- ${it.analogy}: ${it.mapping_explanation}") }
            }
          } else ""
          val codeExamplesSection = if (config.include_code_examples && relevantCodeExamples.isNotEmpty()) {
            buildString {
              appendLine("Code Examples to Include:")
              relevantCodeExamples.forEach { appendLine("- ${it.purpose} (${it.language})") }
            }
          } else ""
          val sectionAnalogyInstruction = if (config.use_analogies && relevantAnalogies.isNotEmpty()) {
            "Uses the provided analogy to make it relatable"
          } else {
            "Explains clearly without jargon"
          }
          val sectionExamplesInstruction = if (config.include_examples) "Includes practical examples or use cases" else "Focuses on conceptual understanding"
          val sectionCodeInstruction = if (config.include_code_examples) "Includes code snippets with clear explanations" else "Avoids code unless absolutely necessary"
          val sectionVisualInstruction = if (config.include_visual_descriptions) "Describes visual representations that would help" else "Uses text-based explanations"
          val codeLangInstruction = if (config.include_code_examples) {
            buildString {
              appendLine("For code snippets, provide:")
              appendLine("- The code in ${config.code_language ?: "appropriate language"}")
              appendLine("- Line-by-line or block explanation")
              appendLine("- Key points to highlight")
            }
          } else ""
          val sectionPrompt = (typeConfig?.section_prompt ?: TechnicalExplanationTypeConfig().section_prompt)
            .replace("{topic}", topic)
            .replace("{audience}", config.target_audience)
            .replace("{audience_guidance}", audienceGuidance)
            .replace("{concept}", conceptOutline.concept)
            .replace("{importance}", conceptOutline.importance)
            .replace("{subtopics}", conceptOutline.subtopics.joinToString(", "))
            .replace("{complexity}", conceptOutline.complexity)
            .replace("{previous_context}", previousContext)
            .replace("{analogies_section}", analogiesSection)
            .replace("{code_examples_section}", codeExamplesSection)
            .replace("{analogy_instruction}", sectionAnalogyInstruction)
            .replace("{subtopics_instruction}", conceptOutline.subtopics.joinToString(", "))
            .replace("{examples_instruction}", sectionExamplesInstruction)
            .replace("{code_instruction}", sectionCodeInstruction)
            .replace("{visual_instruction}", sectionVisualInstruction)
            .replace("{target_audience}", config.target_audience)
            .replace("{estimated_paragraphs}", conceptOutline.estimated_paragraphs.toString())
            .replace("{format}", config.explanation_format)
            .replace("{code_language_instruction}", codeLangInstruction)


          val sectionAgent = ParsedAgent(
            resultClass = ExplanationSection::class.java,
            prompt = sectionPrompt,
            model = api,
            temperature = typeConfig?.section_temperature ?: 0.7,
            parsingChatter = fastApi
          )

          val section = sectionAgent.answer(listOf("Write section")).obj
          sections.add(section)

          val sectionContent = buildString {
            appendLine("## ${section.title}")
            appendLine()
            appendLine(section.content)
            appendLine()
            if (section.code_snippets.isNotEmpty()) {
              appendLine("---")
              appendLine()
              appendLine("### Code Examples")
              appendLine()
              section.code_snippets.forEach { snippet ->
                appendLine("**${snippet.explanation}**")
                appendLine()
                appendLine("```${snippet.language}")
                appendLine(snippet.code)
                appendLine("```")
                appendLine()
                if (snippet.highlights.isNotEmpty()) {
                  appendLine("**Key Points:**")
                  snippet.highlights.forEach { highlight ->
                    appendLine("- $highlight")
                  }
                  appendLine()
                }
              }
            }
            if (section.key_takeaways.isNotEmpty()) {
              appendLine("---")
              appendLine()
              appendLine("### Key Takeaways")
              section.key_takeaways.forEach { takeaway ->
                appendLine("- $takeaway")
              }
              appendLine()
            }
            appendLine("**Status:** ✅ Complete")
          }
          sectionTask.add(sectionContent.renderMarkdown(true))
          sectionTask.update()
          transcript?.write(sectionContent.toByteArray(StandardCharsets.UTF_8))

          resultBuilder.append("## ${section.title}\n\n")
          resultBuilder.append(section.content)
          resultBuilder.append("\n\n")

          if (section.code_snippets.isNotEmpty()) {
            section.code_snippets.forEach { snippet ->
              resultBuilder.append("```${snippet.language}\n")
              resultBuilder.append(snippet.code)
              resultBuilder.append("\n```\n\n")
              resultBuilder.append("*${snippet.explanation}*\n\n")
            }
          }

          overviewTask.add("✅\n".renderMarkdown(true))
          overviewTask.update()
        }

        overviewTask.add("✅ Phase 2 Complete: All sections written\n".renderMarkdown(true))

        if (config.include_comparisons) {
          overviewTask.add(
            "\n### Phase 3: Comparisons\n*Adding comparisons with related concepts...*\n".renderMarkdown(
              true
            )
          )
          overviewTask.update()

          log.info("Phase 3: Generating comparisons")
          val comparisonTask = tabs.newTask("Comparisons")

          comparisonTask.add(
            buildString {
              appendLine("# Comparisons")
              appendLine()
              appendLine("**Status:** Comparing with related concepts...")
              appendLine()
            }.renderMarkdown(true)
          )
          transcript?.write("\n# Comparisons\n\n".toByteArray(StandardCharsets.UTF_8))
          transcript?.write("**Status:** Comparing with related concepts...\n\n".toByteArray(StandardCharsets.UTF_8))
          comparisonTask.update()
          val comparisonPromptText = (typeConfig?.comparison_prompt ?: TechnicalExplanationTypeConfig().comparison_prompt)
            .replace("{topic}", topic)
            .replace("{audience}", config.target_audience)
            .replace("{sections_list}", sections.joinToString("\n") { "- ${it.title}" })


          val comparisonAgent = ChatAgent(
            prompt = comparisonPromptText,
            model = api,
            temperature = typeConfig?.comparison_temperature ?: 0.6
          )

          val comparisons = comparisonAgent.answer(listOf("Generate comparisons"))

          comparisonTask.add(
            buildString {
              appendLine("## Related Concepts")
              appendLine()
              appendLine(comparisons)
              appendLine()
              appendLine("**Status:** ✅ Complete")
            }.renderMarkdown(true)
          )
          comparisonTask.update()
          transcript?.write("\n## Related Concepts\n\n${comparisons}\n\n".toByteArray(StandardCharsets.UTF_8))

          resultBuilder.append("## Comparisons with Related Concepts\n\n")
          resultBuilder.append(comparisons)
          resultBuilder.append("\n\n")

          overviewTask.add("✅ Phase 3 Complete: Comparisons added\n".renderMarkdown(true))
        }

        if (config.revision_passes > 0) {
          overviewTask.add("\n### Phase 4: Revision\n*Refining for clarity...*\n".renderMarkdown(true))
          overviewTask.update()

          log.info("Phase 4: Performing ${config.revision_passes} revision pass(es)")
          val revisionTask = tabs.newTask("Revision")

          revisionTask.add(
            buildString {
              appendLine("# Revision Process")
              appendLine()
              appendLine("**Status:** Performing ${config.revision_passes} revision pass(es)...")
              appendLine()
            }.renderMarkdown(true)
          )
          transcript?.write("\n# Revision Process\n\n".toByteArray(StandardCharsets.UTF_8))
          transcript?.write("**Status:** Performing ${config.revision_passes} revision pass(es)...\n\n".toByteArray(StandardCharsets.UTF_8))
          revisionTask.update()

          repeat(config.revision_passes) { passNum ->
            log.debug("Revision pass ${passNum + 1}/${config.revision_passes}")
            val revisionPromptText = (typeConfig?.revision_prompt ?: TechnicalExplanationTypeConfig().revision_prompt)
              .replace("{explanation}", resultBuilder.toString())
              .replace("{audience}", config.target_audience)
              .replace("{detail_level}", config.level_of_detail)
              .replace("{format}", config.explanation_format)


            val revisionAgent = ChatAgent(
              prompt = revisionPromptText,
              model = api,
              temperature = typeConfig?.revision_temperature ?: 0.5
            )

            val revisedExplanation = revisionAgent.answer(listOf("Revise the explanation"))
            resultBuilder.clear()
            resultBuilder.append(revisedExplanation)

            revisionTask.add(
              buildString {
                appendLine("## Revision Pass ${passNum + 1}")
                appendLine()
                appendLine("✅ Complete")
                appendLine()
              }.renderMarkdown(true)
            )
            revisionTask.update()
            transcript?.write("\n## Revision Pass ${passNum + 1}\n\n✅ Complete\n\n".toByteArray(StandardCharsets.UTF_8))
          }

          overviewTask.add("✅ Phase 4 Complete: ${config.revision_passes} revision pass(es) completed\n".renderMarkdown(true))
        }

        overviewTask.add("\n### Phase 5: Final Assembly\n*Compiling complete explanation...*\n".renderMarkdown(true))
        overviewTask.update()

        log.info("Phase 5: Assembling final explanation")
        val finalTask = tabs.newTask("Complete Explanation")

        val finalExplanation = buildString {
          appendLine("# ${outline.title}")
          appendLine()
          appendLine("> *Explanation for: ${config.target_audience}*")
          appendLine()
          appendLine("## Overview")
          appendLine()
          appendLine(outline.overview)
          appendLine()
          if (outline.terminology.isNotEmpty() && config.define_terminology) {
            appendLine("---")
            appendLine()
            appendLine("## Key Terminology")
            appendLine()
            outline.terminology.forEach { term ->
              appendLine("**${term.term}:** ${term.definition}")
              appendLine()
            }
          }
          appendLine("---")
          appendLine()
          appendLine(resultBuilder.toString())
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## Summary")
          appendLine()
          appendLine("This explanation covered:")
          sections.forEach { section ->
            appendLine("- **${section.title}**")
            if (section.key_takeaways.isNotEmpty()) {
              section.key_takeaways.forEach { takeaway ->
                appendLine("  - ${takeaway.truncateForDisplay(100)}")
              }
            }
          }
        }

        finalTask.add(finalExplanation.renderMarkdown(true))
        finalTask.update()

        transcript?.write(
          buildString {
            appendLine("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">")
            appendLine()
            appendLine("## Final Explanation")
            appendLine()
            appendLine(finalExplanation)
            appendLine()
            appendLine("</div>")
            appendLine()
          }.toByteArray(StandardCharsets.UTF_8)
        )

        val totalTime = System.currentTimeMillis() - startTime
        val wordCount = finalExplanation.split("\\s+".toRegex()).size
        val codeExampleCount = sections.sumOf { it.code_snippets.size }

        val statsContent = buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ✅ Generation Complete")
          appendLine()
          appendLine("**Statistics:**")
          appendLine("- Sections: ${sections.size}")
          appendLine("- Word Count: $wordCount")
          appendLine("- Code Examples: $codeExampleCount")
          appendLine("- Analogies Used: ${outline.analogies.size}")
          appendLine("- Terms Defined: ${outline.terminology.size}")
          appendLine("- Revision Passes: ${config.revision_passes}")
          appendLine("- Total Time: ${totalTime / 1000.0}s")
          appendLine()
          appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        }
        overviewTask.add(statsContent.renderMarkdown(true))
        transcript?.write(statsContent.toByteArray(StandardCharsets.UTF_8))
        overviewTask.update()

        val finalResult = buildString {
          appendLine("# Technical Explanation: ${outline.title}")
          appendLine()
          appendLine("Generated a $wordCount-word explanation for a **${config.target_audience}** audience.")
          appendLine("The explanation covers ${sections.size} key concepts with $codeExampleCount code examples and ${outline.analogies.size} analogies.")
          appendLine()
          appendLine("*Full content is available in the task UI.*")
        }

        log.info("TechnicalExplanationTask completed: sections=${sections.size}, words=$wordCount, time=${totalTime}ms")

        task.complete("Technical explanation generation complete: $wordCount words in ${totalTime / 1000}s".renderMarkdown(true))
        resultFn(finalResult)

      } catch (e: Exception) {
        log.error("Error during technical explanation generation", e)
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
          }.renderMarkdown(true)
        )
        overviewTask.update()
        transcript?.write(buildString {
          appendLine("## Error")
          appendLine("<details>")
          appendLine("<summary>Stack Trace</summary>")
          appendLine()
          appendLine("```")
          appendLine(e.stackTraceToString())
          appendLine("```")
          appendLine("</details>")
        }.toByteArray(StandardCharsets.UTF_8))
        val errorOutput = buildString {
          appendLine("# Error in Technical Explanation Generation")
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
      } finally {
        transcript?.close()
      }
    }
  }

  private fun getInputFileCode() = (executionConfig?.input_files ?: listOf()).flatMap { pattern: String ->
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
  }.distinct().sortedBy { it }.joinToString("\n\n") { relativePath ->
    val file = root.toFile().resolve(relativePath)
    try {
      val content = file.readText()
      "# $relativePath\n\n```\n$content\n```"
    } catch (e: Throwable) {
      log.warn("Error reading file: $relativePath", e)
      ""
    }
  }


  private fun getContextFiles(): String {
    val relatedFiles = executionConfig?.related_files ?: return ""
    if (relatedFiles.isEmpty()) return ""
    log.debug("Loading ${relatedFiles.size} related context files")

    return buildString {
      appendLine("## Related Documentation Files")
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
    private val log: Logger = LoggerFactory.getLogger(TechnicalExplanationTask::class.java)
    @JvmStatic
    val TechnicalExplanation = TaskType(
        name = "TechnicalExplanation",
        category = "Writing",
        taskClass = TechnicalExplanationTask::class.java,
        executionConfigClass = TechnicalExplanationTaskExecutionConfigData::class.java,
        taskSettingsClass = TechnicalExplanationTypeConfig::class.java,
        description = "Break down complex technical subjects into clear, digestible explanations",
        tooltipHtml = buildString {
          appendLine("Generates clear, audience-appropriate explanations of complex technical topics.")
          appendLine("<ul>")
          appendLine("  <li>Creates structured outline with key concepts and terminology</li>")
          appendLine("  <li>Adjusts language and depth for target audience (layperson to expert)</li>")
          appendLine("  <li>Generates relatable analogies and metaphors</li>")
          appendLine("  <li>Includes code examples with detailed explanations</li>")
          appendLine("  <li>Defines essential terminology in context</li>")
          appendLine("  <li>Provides visual descriptions and diagrams</li>")
          appendLine("  <li>Includes practical examples and use cases</li>")
          appendLine("  <li>Compares with related concepts for clarity</li>")
          appendLine("  <li>Supports multiple formats (markdown, Q&amp;A, step-by-step, tutorial)</li>")
          appendLine("  <li>Optional revision passes for clarity improvement</li>")
          appendLine("  <li>Ideal for documentation, onboarding, education, and knowledge sharing</li>")
          appendLine("</ul>")
        },
    )
  }
}