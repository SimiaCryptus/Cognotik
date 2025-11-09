package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TechnicalExplanationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: TechnicalExplanationTaskExecutionConfigData?
) : AbstractTask<TechnicalExplanationTask.TechnicalExplanationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class TechnicalExplanationTaskExecutionConfigData(
        @Description("The complex technical subject to explain")
        val topic: String? = null,

        @Description("Target audience expertise level (e.g., 'layperson', 'beginner', 'intermediate', 'expert', 'manager', 'software_engineer', 'data_scientist')")
        val target_audience: String = "intermediate",

        @Description("Level of detail for the explanation (e.g., 'high_level_overview', 'moderate_detail', 'detailed_walkthrough', 'comprehensive')")
        val level_of_detail: String = "moderate_detail",

        @Description("Whether to include code examples and snippets")
        val include_code_examples: Boolean = true,

        @Description("Explanation format (e.g., 'markdown', 'q_and_a', 'step_by_step', 'narrative', 'tutorial')")
        val explanation_format: String = "markdown",

        @Description("Whether to generate analogies and metaphors")
        val use_analogies: Boolean = true,

        @Description("Whether to include visual descriptions or diagrams")
        val include_visual_descriptions: Boolean = true,

        @Description("Whether to define key terminology")
        val define_terminology: Boolean = true,

        @Description("Whether to include practical examples and use cases")
        val include_examples: Boolean = true,

        @Description("Whether to provide comparison with related concepts")
        val include_comparisons: Boolean = true,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,


        @Description("Programming language for code examples (if applicable)")
        val code_language: String? = null,

        @Description("Number of revision passes for clarity improvement")
        val revision_passes: Int = 1,

        @Description("Related files or documentation to reference")
        val related_files: List<String>? = null,

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = TechnicalExplanation.name,
        task_description = task_description ?: "Generate technical explanation for: '$topic'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (topic.isNullOrBlank()) {
                return "topic must not be null or blank"
            }
            val validAudiences = setOf(
                "layperson", "beginner", "intermediate", "expert",
                "manager", "software_engineer", "data_scientist", "student"
            )
            if (target_audience.lowercase() !in validAudiences) {
                return "target_audience must be one of: ${validAudiences.joinToString(", ")}, got: $target_audience"
            }
            val validDetailLevels = setOf(
                "high_level_overview", "moderate_detail", "detailed_walkthrough", "comprehensive"
            )
            if (level_of_detail.lowercase() !in validDetailLevels) {
                return "level_of_detail must be one of: ${validDetailLevels.joinToString(", ")}, got: $level_of_detail"
            }
            val validFormats = setOf("markdown", "q_and_a", "step_by_step", "narrative", "tutorial")
            if (explanation_format.lowercase() !in validFormats) {
                return "explanation_format must be one of: ${validFormats.joinToString(", ")}, got: $explanation_format"
            }
            if (revision_passes < 0 || revision_passes > 5) {
                return "revision_passes must be between 0 and 5, got: $revision_passes"
            }
            if (!input_files.isNullOrEmpty()) {
                input_files.forEach { file ->
                    if (file.isBlank()) {
                        return "input_files must not contain blank entries"
                    }
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class ExplanationOutline(
        @Description("The main topic title")
        val title: String = "",
        @Description("Brief overview of what will be explained")
        val overview: String = "",
        @Description("Key concepts to cover in order")
        val key_concepts: List<ConceptOutline> = emptyList(),
        @Description("Core terminology that needs definition")
        val terminology: List<TermDefinition> = emptyList(),
        @Description("Analogies to use for complex concepts")
        val analogies: List<AnalogyMapping> = emptyList(),
        @Description("Code examples to include")
        val code_examples: List<CodeExampleOutline> = emptyList(),
        @Description("Visual descriptions or diagrams needed")
        val visual_descriptions: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (overview.isBlank()) return "overview must not be blank"
            if (key_concepts.isEmpty()) return "key_concepts must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ConceptOutline(
        @Description("Concept name")
        val concept: String = "",
        @Description("Why this concept matters")
        val importance: String = "",
        @Description("Sub-topics to cover")
        val subtopics: List<String> = emptyList(),
        @Description("Complexity level")
        val complexity: String = "",
        @Description("Estimated explanation length")
        val estimated_paragraphs: Int = 0
    ) : ValidatedObject

    data class TermDefinition(
        @Description("The technical term")
        val term: String = "",
        @Description("Simple definition")
        val definition: String = "",
        @Description("Context where it's used")
        val context: String = ""
    ) : ValidatedObject

    data class AnalogyMapping(
        @Description("The technical concept")
        val technical_concept: String = "",
        @Description("The relatable analogy")
        val analogy: String = "",
        @Description("How they map to each other")
        val mapping_explanation: String = ""
    ) : ValidatedObject

    data class CodeExampleOutline(
        @Description("What the code demonstrates")
        val purpose: String = "",
        @Description("Programming language")
        val language: String = "",
        @Description("Complexity level")
        val complexity: String = "",
        @Description("Key points to highlight")
        val key_points: List<String> = emptyList()
    ) : ValidatedObject

    data class ExplanationSection(
        @Description("Section title")
        val title: String = "",
        @Description("Section content")
        val content: String = "",
        @Description("Code snippets in this section")
        val code_snippets: List<CodeSnippet> = emptyList(),
        @Description("Key takeaways")
        val key_takeaways: List<String> = emptyList()
    ) : ValidatedObject

    data class CodeSnippet(
        @Description("Programming language")
        val language: String = "",
        @Description("The code")
        val code: String = "",
        @Description("Explanation of the code")
        val explanation: String = "",
        @Description("Key points highlighted")
        val highlights: List<String> = emptyList()
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
TechnicalExplanation - Break down complex technical subjects into clear, digestible explanations
  ** Specify the technical topic to explain
  ** Define target audience expertise level
  ** Set level of detail (overview to comprehensive)
  ** Configure explanation format (markdown, Q&A, step-by-step, etc.)
  ** Enable analogies and metaphors for clarity
  ** Include code examples with explanations
  ** Define key terminology
  ** Provide visual descriptions
  ** Include practical examples and use cases
  ** Compare with related concepts
  ** Performs outline creation, content generation, and iterative refinement
  ** Produces clear, audience-appropriate technical explanations
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
        log.info("Starting TechnicalExplanationTask for topic: '${executionConfig?.topic}'")
        val markdownTranscript = task.transcript()
        val userMessages = messages.filter { it.isNotBlank() }

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            resultFn("CONFIGURATION ERROR: $validationError")
            return
        }

        val topic = executionConfig?.topic
        if (topic.isNullOrBlank()) {
            log.error("No topic specified for technical explanation")
            task.safeComplete("CONFIGURATION ERROR: No topic specified", log)
            resultFn("CONFIGURATION ERROR: No topic specified")
            return
        }

        val api = orchestrationConfig.defaultChatter ?: return

        val tabs = TabbedDisplay(task)
        // Load input files if specified
        val inputFileContent = getInputFileCode()
        if (inputFileContent.isNotBlank()) {
            log.info("Loaded input files for context")
            val inputFilesTask = task.ui.newTask(false)
            tabs["Input Files"] = inputFilesTask.placeholder
            inputFilesTask.add(
                buildString {
                    appendLine("# Input Files")
                    appendLine()
                    appendLine(inputFileContent.truncateForDisplay(3000))
                    appendLine()
                }.renderMarkdown
            )
            markdownTranscript?.write("# Input Files\n\n".toByteArray(StandardCharsets.UTF_8))
            markdownTranscript?.write(inputFileContent.truncateForDisplay(3000).toByteArray(StandardCharsets.UTF_8))
            markdownTranscript?.write("\n\n".toByteArray(StandardCharsets.UTF_8))
            task.update()
        }
        // Include user messages in context
        if (userMessages.isNotEmpty()) {
            log.info("Including ${userMessages.size} user message(s) in context")
        }


        // Overview tab
        val overviewTask = task.ui.newTask(false)
        tabs["Overview"] = overviewTask.placeholder

        val overviewContent = buildString {
            appendLine("# Technical Explanation Generation")
            appendLine()
            appendLine("**Topic:** $topic")
            appendLine()
            appendLine("## Configuration")
            appendLine()
            if (userMessages.isNotEmpty()) {
                appendLine("### User Input")
                appendLine()
                userMessages.forEach { message ->
                    appendLine(message)
                    appendLine()
                }
                appendLine("---")
                appendLine()
            }
        }
        overviewTask.add(overviewContent.renderMarkdown)
        markdownTranscript?.write(overviewContent.toByteArray(StandardCharsets.UTF_8))
        buildString {
            appendLine("- Target Audience: ${executionConfig.target_audience}")
            appendLine("- Level of Detail: ${executionConfig.level_of_detail}")
            appendLine("- Format: ${executionConfig.explanation_format}")
            appendLine("- Include Code Examples: ${if (executionConfig.include_code_examples) "✓" else "✗"}")
            appendLine("- Use Analogies: ${if (executionConfig.use_analogies) "✓" else "✗"}")
            appendLine("- Define Terminology: ${if (executionConfig.define_terminology) "✓" else "✗"}")
            appendLine("- Include Visual Descriptions: ${if (executionConfig.include_visual_descriptions) "✓" else "✗"}")
            appendLine("- Include Examples: ${if (executionConfig.include_examples) "✓" else "✗"}")
            appendLine("- Include Comparisons: ${if (executionConfig.include_comparisons) "✓" else "✗"}")
            if (executionConfig.code_language != null) {
                appendLine("- Code Language: ${executionConfig.code_language}")
            }
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
        }
        buildString {
            appendLine("### Phase 1: Analysis & Outline")
            appendLine("*Analyzing topic and creating explanation structure...*")
        }
        overviewTask.add(overviewContent.renderMarkdown)
        task.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Technical Explanation: $topic\n\n")

        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState)
            val contextFiles = getContextFiles()

            if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                val contextTask = task.ui.newTask(false)
                tabs["Reference Context"] = contextTask.placeholder
                contextTask.add(
                    buildString {
                        appendLine("# Reference Context")
                        appendLine()
                        if (priorContext.isNotBlank()) {
                            appendLine("## Prior Context")
                            appendLine(priorContext.truncateForDisplay(2000))
                            markdownTranscript?.write("\n## Prior Context\n".toByteArray(StandardCharsets.UTF_8))
                            markdownTranscript?.write(
                                priorContext.truncateForDisplay(2000).toByteArray(StandardCharsets.UTF_8)
                            )
                            appendLine()
                        }
                        if (contextFiles.isNotBlank()) {
                            appendLine("## Related Files")
                            appendLine(contextFiles.truncateForDisplay(2000))
                        }
                    }.renderMarkdown
                )
                task.update()
                markdownTranscript?.write("\n## Related Files\n".toByteArray(StandardCharsets.UTF_8))
                markdownTranscript?.write(contextFiles.truncateForDisplay(2000).toByteArray(StandardCharsets.UTF_8))
            }

            // Phase 1: Create outline
            log.info("Phase 1: Creating explanation outline")
            val outlineTask = task.ui.newTask(false)
            tabs["Outline"] = outlineTask.placeholder

            outlineTask.add(
                buildString {
                    appendLine("# Explanation Outline")
                    appendLine()
                    appendLine("**Status:** Creating structured outline...")
                    appendLine()
                }.renderMarkdown
            )
            markdownTranscript?.write("\n# Explanation Outline\n\n".toByteArray(StandardCharsets.UTF_8))
            markdownTranscript?.write("**Status:** Creating structured outline...\n\n".toByteArray(StandardCharsets.UTF_8))
            task.update()

            val audienceGuidance = when (executionConfig.target_audience.lowercase()) {
                "layperson" -> "Assume no technical background. Use everyday language and avoid jargon."
                "beginner" -> "Assume basic familiarity with technology but limited domain knowledge."
                "intermediate" -> "Assume solid foundation in the field with some practical experience."
                "expert" -> "Assume deep technical knowledge. Focus on nuances and advanced concepts."
                "manager" -> "Focus on high-level concepts, business value, and practical implications."
                "software_engineer" -> "Assume programming knowledge. Include implementation details."
                "data_scientist" -> "Assume statistical and algorithmic knowledge. Include mathematical concepts."
                else -> "Adjust language to match the audience's technical level."
            }

            val detailGuidance = when (executionConfig.level_of_detail.lowercase()) {
                "high_level_overview" -> "Provide a bird's-eye view. Focus on the 'what' and 'why'."
                "moderate_detail" -> "Balance overview with key details. Cover 'what', 'why', and 'how' at a moderate depth."
                "detailed_walkthrough" -> "Provide comprehensive coverage with step-by-step explanations."
                "comprehensive" -> "Cover all aspects thoroughly, including edge cases and advanced topics."
                else -> "Provide moderate detail with clear explanations."
            }

            val outlineAgent = ParsedAgent(
                resultClass = ExplanationOutline::class.java,
                prompt = """
You are an expert technical educator and communicator. Create a detailed outline for explaining this topic.

Topic: $topic

Target Audience: ${executionConfig.target_audience}
Audience Guidance: $audienceGuidance

Level of Detail: ${executionConfig.level_of_detail}
Detail Guidance: $detailGuidance

Format: ${executionConfig.explanation_format}

${if (priorContext.isNotBlank()) "Reference Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Additional Documentation:\n${contextFiles.truncateForDisplay(3000)}\n" else ""}

Create an outline that includes:
1. A clear, engaging title
2. Brief overview (2-3 sentences) of what will be explained
3. 3-6 key concepts to cover, ordered logically (simple to complex or general to specific)
4. ${if (executionConfig.define_terminology) "5-10 essential terms that need definition" else "Key terminology (minimal)"}
5. ${if (executionConfig.use_analogies) "2-4 analogies to make complex concepts relatable" else "Analogies (if absolutely necessary)"}
6. ${if (executionConfig.include_code_examples) "3-5 code examples to illustrate concepts" else "Code examples (minimal or none)"}
7. ${if (executionConfig.include_visual_descriptions) "Descriptions of diagrams or visualizations that would help" else "Visual aids (if critical)"}

For each key concept, specify:
- The concept name
- Why it's important to understand
- Sub-topics to cover
- Complexity level (basic, intermediate, advanced)
- Estimated paragraphs needed

Ensure the outline:
- Builds understanding progressively
- Matches the ${executionConfig.target_audience} audience level
- Provides ${executionConfig.level_of_detail} level of detail
- Follows ${executionConfig.explanation_format} format conventions
          """.trimIndent(),
                model = api,
                temperature = 0.6,
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
                        appendLine("- $visual")
                    }
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }
            outlineTask.add(outlineContent.renderMarkdown)
            task.update()
            markdownTranscript?.write(outlineContent.toByteArray(StandardCharsets.UTF_8))

            overviewTask.add("✅ Phase 1 Complete: Outline created\n".renderMarkdown)
            overviewTask.add("\n### Phase 2: Content Generation\n*Writing explanation sections...*\n".renderMarkdown)
            task.update()

            // Phase 2: Generate content for each concept
            log.info("Phase 2: Generating explanation content")
            val sections = mutableListOf<ExplanationSection>()

            outline.key_concepts.forEachIndexed { index, conceptOutline ->
                log.info("Writing section ${index + 1}/${outline.key_concepts.size}: ${conceptOutline.concept}")

                overviewTask.add("- Section ${index + 1}: ${conceptOutline.concept.truncateForDisplay(50)} ".renderMarkdown)
                task.update()

                val sectionTask = task.ui.newTask(false)
                tabs["Section ${index + 1}"] = sectionTask.placeholder

                sectionTask.add(
                    buildString {
                        appendLine("# ${conceptOutline.concept}")
                        appendLine()
                        appendLine("**Status:** Writing section...")
                        appendLine()
                    }.renderMarkdown
                )
                markdownTranscript?.write("\n# ${conceptOutline.concept}\n\n".toByteArray(StandardCharsets.UTF_8))
                markdownTranscript?.write("**Status:** Writing section...\n\n".toByteArray(StandardCharsets.UTF_8))
                task.update()

                // Build context from previous sections
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

                // Find relevant analogies for this concept
                val relevantAnalogies = outline.analogies.filter {
                    it.technical_concept.contains(conceptOutline.concept, ignoreCase = true) ||
                            conceptOutline.concept.contains(it.technical_concept, ignoreCase = true)
                }

                // Find relevant code examples
                val relevantCodeExamples = outline.code_examples.filter {
                    it.purpose.contains(conceptOutline.concept, ignoreCase = true) ||
                            conceptOutline.concept.contains(it.purpose, ignoreCase = true)
                }

                val sectionAgent = ParsedAgent(
                    resultClass = ExplanationSection::class.java,
                    prompt = """
You are an expert technical educator. Write a clear, engaging explanation of this concept.

Overall Topic: $topic
Target Audience: ${executionConfig.target_audience}
Audience Guidance: $audienceGuidance

Concept to Explain: ${conceptOutline.concept}
Importance: ${conceptOutline.importance}
Subtopics: ${conceptOutline.subtopics.joinToString(", ")}
Complexity: ${conceptOutline.complexity}

$previousContext

${
                        if (relevantAnalogies.isNotEmpty()) {
                            "Analogies to Use:\n${relevantAnalogies.joinToString("\n") { "- ${it.analogy}: ${it.mapping_explanation}" }}\n"
                        } else ""
                    }

${
                        if (executionConfig.include_code_examples && relevantCodeExamples.isNotEmpty()) {
                            "Code Examples to Include:\n${relevantCodeExamples.joinToString("\n") { "- ${it.purpose} (${it.language})" }}\n"
                        } else ""
                    }

Write a section that:
1. Opens with a clear introduction to the concept
2. ${if (executionConfig.use_analogies && relevantAnalogies.isNotEmpty()) "Uses the provided analogy to make it relatable" else "Explains clearly without jargon"}
3. Covers all subtopics: ${conceptOutline.subtopics.joinToString(", ")}
4. ${if (executionConfig.include_examples) "Includes practical examples or use cases" else "Focuses on conceptual understanding"}
5. ${if (executionConfig.include_code_examples) "Includes code snippets with clear explanations" else "Avoids code unless absolutely necessary"}
6. ${if (executionConfig.include_visual_descriptions) "Describes visual representations that would help" else "Uses text-based explanations"}
7. Provides 2-4 key takeaways at the end
8. Transitions smoothly to the next concept

Make it:
- Clear and accessible to ${executionConfig.target_audience}
- Engaging and well-structured
- Approximately ${conceptOutline.estimated_paragraphs} paragraphs
- Following ${executionConfig.explanation_format} format

${
                        if (executionConfig.include_code_examples) {
                            "For code snippets, provide:\n- The code in ${executionConfig.code_language ?: "appropriate language"}\n- Line-by-line or block explanation\n- Key points to highlight\n"
                        } else ""
                    }
          """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = orchestrationConfig.parsingChatter
                )

                var section = sectionAgent.answer(listOf("Write section")).obj
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
                sectionTask.add(sectionContent.renderMarkdown)
                task.update()
                markdownTranscript?.write(sectionContent.toByteArray(StandardCharsets.UTF_8))

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

                overviewTask.add("✅\n".renderMarkdown)
                task.update()
            }

            overviewTask.add("✅ Phase 2 Complete: All sections written\n".renderMarkdown)

            // Phase 3: Add comparisons if enabled
            if (executionConfig.include_comparisons) {
                overviewTask.add("\n### Phase 3: Comparisons\n*Adding comparisons with related concepts...*\n".renderMarkdown)
                task.update()

                log.info("Phase 3: Generating comparisons")
                val comparisonTask = task.ui.newTask(false)
                tabs["Comparisons"] = comparisonTask.placeholder

                comparisonTask.add(
                    buildString {
                        appendLine("# Comparisons")
                        appendLine()
                        appendLine("**Status:** Comparing with related concepts...")
                        appendLine()
                    }.renderMarkdown
                )
                markdownTranscript?.write("\n# Comparisons\n\n".toByteArray(StandardCharsets.UTF_8))
                markdownTranscript?.write(
                    "**Status:** Comparing with related concepts...\n\n".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                task.update()

                val comparisonAgent = ChatAgent(
                    prompt = """
You are an expert technical educator. Compare and contrast this topic with related concepts.

Topic: $topic
Target Audience: ${executionConfig.target_audience}

Content Covered:
${sections.joinToString("\n") { "- ${it.title}" }}

Provide comparisons that:
1. Identify 2-3 related or commonly confused concepts
2. Explain key similarities
3. Highlight important differences
4. Clarify when to use each
5. Help readers understand the boundaries and relationships

Make comparisons clear and helpful for ${executionConfig.target_audience}.
          """.trimIndent(),
                    model = api,
                    temperature = 0.6
                )

                val comparisons = comparisonAgent.answer(listOf("Generate comparisons"))

                comparisonTask.add(
                    buildString {
                        appendLine("## Related Concepts")
                        appendLine()
                        appendLine(comparisons)
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }.renderMarkdown
                )
                task.update()
                markdownTranscript?.write("\n## Related Concepts\n\n${comparisons}\n\n".toByteArray(StandardCharsets.UTF_8))

                resultBuilder.append("## Comparisons with Related Concepts\n\n")
                resultBuilder.append(comparisons)
                resultBuilder.append("\n\n")

                overviewTask.add("✅ Phase 3 Complete: Comparisons added\n".renderMarkdown)
            }

            // Phase 4: Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                overviewTask.add("\n### Phase 4: Revision\n*Refining for clarity...*\n".renderMarkdown)
                task.update()

                log.info("Phase 4: Performing ${executionConfig.revision_passes} revision pass(es)")
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
                markdownTranscript?.write("\n# Revision Process\n\n".toByteArray(StandardCharsets.UTF_8))
                markdownTranscript?.write(
                    "**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...\n\n".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                task.update()

                val fullExplanation = resultBuilder.toString()

                repeat(executionConfig.revision_passes) { passNum ->
                    log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")

                    val revisionAgent = ChatAgent(
                        prompt = """
You are an expert technical editor. Review and improve this explanation for clarity and effectiveness.

Current Explanation:
$fullExplanation

Target Audience: ${executionConfig.target_audience}
Level of Detail: ${executionConfig.level_of_detail}

Focus on:
1. Clarity and simplicity of language
2. Logical flow and transitions
3. Effectiveness of analogies and examples
4. Accuracy of technical content
5. Appropriateness for ${executionConfig.target_audience}
6. Completeness of coverage
7. Engagement and readability

Maintain:
- All key concepts and information
- Code examples and their explanations
- Technical accuracy
- Approximate length
- ${executionConfig.explanation_format} format

Provide the complete revised explanation.
            """.trimIndent(),
                        model = api,
                        temperature = 0.5
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
                        }.renderMarkdown
                    )
                    task.update()
                    markdownTranscript?.write(
                        "\n## Revision Pass ${passNum + 1}\n\n✅ Complete\n\n".toByteArray(
                            StandardCharsets.UTF_8
                        )
                    )
                }

                overviewTask.add("✅ Phase 4 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown)
            }

            // Phase 5: Final Assembly
            overviewTask.add("\n### Phase 5: Final Assembly\n*Compiling complete explanation...*\n".renderMarkdown)
            task.update()

            log.info("Phase 5: Assembling final explanation")
            val finalTask = task.ui.newTask(false)
            tabs["Complete Explanation"] = finalTask.placeholder

            val finalExplanation = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine("> *Explanation for: ${executionConfig.target_audience}*")
                appendLine()
                appendLine("## Overview")
                appendLine()
                appendLine(outline.overview)
                appendLine()
                if (outline.terminology.isNotEmpty() && executionConfig.define_terminology) {
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

            finalTask.add(finalExplanation.renderMarkdown)
            task.update()
            markdownTranscript?.write("\n---\n\n${finalExplanation}\n".toByteArray(StandardCharsets.UTF_8))

            // Final statistics
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
                appendLine("- Revision Passes: ${executionConfig.revision_passes}")
                appendLine("- Total Time: ${totalTime / 1000.0}s")
                appendLine()
                appendLine(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
            }
            overviewTask.add(
                statsContent.renderMarkdown
            )
            markdownTranscript?.write(statsContent.toByteArray(StandardCharsets.UTF_8))
            task.update()

            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# Technical Explanation Summary: ${outline.title}")
                appendLine()
                appendLine("A complete technical explanation of **$topic** was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Target Audience:** ${executionConfig.target_audience}")
                appendLine()
                appendLine("**Coverage:**")
                appendLine("- ${sections.size} main sections")
                appendLine("- $wordCount words")
                appendLine("- $codeExampleCount code examples")
                appendLine("- ${outline.analogies.size} analogies")
                appendLine()
                appendLine("> The full explanation is available in the Complete Explanation tab for detailed review.")
            }

            log.info("TechnicalExplanationTask completed: sections=${sections.size}, words=$wordCount, time=${totalTime}ms")
            markdownTranscript?.close()

            task.safeComplete(
                "Technical explanation generation complete: $wordCount words in ${totalTime / 1000}s",
                log
            )
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
                }.renderMarkdown
            )
            task.update()
            markdownTranscript?.close()

            val errorOutput = buildString {
                appendLine("# Error in Technical Explanation Generation")
                appendLine()
                appendLine("**Topic:** $topic")
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
        val TechnicalExplanation = TaskType(
            "TechnicalExplanation",
            TechnicalExplanationTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Break down complex technical subjects into clear, digestible explanations",
            """
              Generates clear, audience-appropriate explanations of complex technical topics.
              <ul>
                <li>Creates structured outline with key concepts and terminology</li>
                <li>Adjusts language and depth for target audience (layperson to expert)</li>
                <li>Generates relatable analogies and metaphors</li>
                <li>Includes code examples with detailed explanations</li>
                <li>Defines essential terminology in context</li>
                <li>Provides visual descriptions and diagrams</li>
                <li>Includes practical examples and use cases</li>
                <li>Compares with related concepts for clarity</li>
                <li>Supports multiple formats (markdown, Q&A, step-by-step, tutorial)</li>
                <li>Optional revision passes for clarity improvement</li>
                <li>Ideal for documentation, onboarding, education, and knowledge sharing</li>
              </ul>
            """
        )
    }
}