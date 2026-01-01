package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.input.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ResearchPaperGenerationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ResearchPaperGenerationTaskExecutionConfigData?
) : AbstractTask<ResearchPaperGenerationTask.ResearchPaperGenerationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class ResearchPaperGenerationTaskExecutionConfigData(
        @Description("The main research question or topic")
        val research_topic: String? = null,

        @Description("Type of research paper (e.g., 'empirical', 'theoretical', 'review', 'meta-analysis')")
        val paper_type: String = "empirical",

        @Description("Academic level (e.g., 'undergraduate', 'masters', 'phd', 'postdoc')")
        val academic_level: String = "masters",

        @Description("Target word count for the complete paper")
        val target_word_count: Int = 8000,

        @Description("Citation style (e.g., 'apa', 'mla', 'chicago', 'ieee')")
        val citation_style: String = "apa",

        @Description("Whether to include a literature review section")
        val include_literature_review: Boolean = true,

        @Description("Whether to include methodology section")
        val include_methodology: Boolean = true,

        @Description("Whether to include statistical analysis descriptions")
        val include_statistical_analysis: Boolean = true,

        @Description("Whether to include peer review simulation")
        val include_peer_review: Boolean = true,

        @Description("Number of main sections (excluding abstract/conclusion)")
        val number_of_sections: Int = 6,

        @Description("Number of revision passes for quality improvement")
        val revision_passes: Int = 1,

        @Description("Research source files or data to incorporate")
        val research_files: List<String>? = null,

        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ResearchPaperGeneration.name,
        task_description = task_description ?: "Generate research paper on: '$research_topic'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (research_topic.isNullOrBlank()) {
                return "research_topic must not be null or blank"
            }
            if (target_word_count <= 0) {
                return "target_word_count must be positive, got: $target_word_count"
            }
            if (revision_passes < 0 || revision_passes > 5) {
                return "revision_passes must be between 0 and 5, got: $revision_passes"
            }
            val validPaperTypes = setOf("empirical", "theoretical", "review", "meta-analysis", "systematic-review")
            if (paper_type.lowercase() !in validPaperTypes) {
                return "paper_type must be one of: ${validPaperTypes.joinToString(", ")}, got: $paper_type"
            }
            val validAcademicLevels = setOf("undergraduate", "masters", "phd", "postdoc")
            if (academic_level.lowercase() !in validAcademicLevels) {
                return "academic_level must be one of: ${validAcademicLevels.joinToString(", ")}, got: $academic_level"
            }
            val validCitationStyles = setOf("apa", "mla", "chicago", "ieee")
            if (citation_style.lowercase() !in validCitationStyles) {
                return "citation_style must be one of: ${validCitationStyles.joinToString(", ")}, got: $citation_style"
            }
            if (number_of_sections < 3 || number_of_sections > 15) {
                return "number_of_sections must be between 3 and 15, got: $number_of_sections"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class ResearchOutline(
        @Description("Paper title")
        val title: String = "",
        @Description("Research question or thesis statement")
        val thesis_statement: String = "",
        @Description("Abstract summary")
        val abstract_summary: String = "",
        @Description("Main sections of the paper")
        val sections: List<PaperSection> = emptyList(),
        @Description("Key research gaps identified")
        val research_gaps: List<String> = emptyList(),
        @Description("Estimated total word count")
        val estimated_word_count: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (sections.isEmpty()) return "sections must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class PaperSection(
        @Description("Section number")
        val section_number: Int = 1,
        @Description("Section title")
        val title: String = "",
        @Description("Section purpose")
        val purpose: String = "",
        @Description("Key points to cover")
        val key_points: List<String> = emptyList(),
        @Description("Estimated word count")
        val estimated_word_count: Int = 0,
        @Description("Whether this section requires citations")
        val requires_citations: Boolean = true
    ) : ValidatedObject {
        override fun validate(): String? {
            if (section_number < 1) return "section_number must be positive"
            if (title.isBlank()) return "title must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Citation(
        @Description("Citation key/identifier")
        val key: String = "",
        @Description("Author(s)")
        val authors: String = "",
        @Description("Publication year")
        val year: String = "",
        @Description("Title")
        val title: String = "",
        @Description("Source/Journal/Publisher")
        val source: String = "",
        @Description("URL or DOI if available")
        val url: String = ""
    ) : ValidatedObject

    data class Bibliography(
        @Description("List of citations")
        val citations: List<Citation> = emptyList()
    ) : ValidatedObject

    data class GeneratedSection(
        @Description("Section number")
        val section_number: Int = 1,
        @Description("Section title")
        val title: String = "",
        @Description("Section content with citations")
        val content: String = "",
        @Description("Word count")
        val word_count: Int = 0,
        @Description("Citations used in this section")
        val citations_used: List<String> = emptyList(),
        @Description("Key findings or arguments")
        val key_arguments: List<String> = emptyList()
    ) : ValidatedObject

    data class PeerReview(
        @Description("Overall assessment")
        val overall_assessment: String = "",
        @Description("Strengths of the paper")
        val strengths: List<String> = emptyList(),
        @Description("Weaknesses identified")
        val weaknesses: List<String> = emptyList(),
        @Description("Suggestions for improvement")
        val suggestions: List<String> = emptyList(),
        @Description("Recommendation (accept, minor revisions, major revisions, reject)")
        val recommendation: String = ""
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
ResearchPaperGeneration - Generate comprehensive academic research papers with citations
  ** Specify the research topic and paper type (empirical, theoretical, review, meta-analysis)
  ** Define academic level (undergraduate, masters, PhD, postdoc)
  ** Configure citation style (APA, MLA, Chicago, IEEE)
  ** Provide research source files for literature synthesis
  ** Enable literature review, methodology, and statistical analysis sections
  ** Optional peer review simulation to identify weaknesses
  ** Produces complete academic paper with bibliography
        """.trimIndent()
    }

    protected val codeFiles = mutableMapOf<java.nio.file.Path, String>()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val startTime = System.currentTimeMillis()
        log.info("Starting ResearchPaperGenerationTask for topic: '${executionConfig?.research_topic}'")
        val markdownTranscript = task.transcript()

        // Read input from messages parameter
        val messageContext = messages.filter { it.isNotBlank() }.joinToString("\n\n")
        log.debug("Received ${messages.size} messages with total length: ${messageContext.length}")

        // Load input files if specified
        val inputFileContent = getInputFileCode()
        log.debug("Loaded input files: ${inputFileContent.length} characters")

        val fullContext =
            listOfNotNull(messageContext, inputFileContent).filter { it.isNotBlank() }.joinToString("\n\n---\n\n")

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            resultFn("CONFIGURATION ERROR: $validationError")
            return
        }

        val researchTopic = executionConfig?.research_topic
        if (researchTopic.isNullOrBlank()) {
            log.error("No research topic specified")
            task.safeComplete("CONFIGURATION ERROR: No research topic specified", log)
            resultFn("CONFIGURATION ERROR: No research topic specified")
            return
        }

        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Research Paper Generation")
            appendLine()
            appendLine("**Topic:** $researchTopic")
            appendLine("**Type:** ${executionConfig.paper_type}")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Paper Type: ${executionConfig.paper_type}")
            appendLine("- Academic Level: ${executionConfig.academic_level}")
            appendLine("- Citation Style: ${executionConfig.citation_style}")
            appendLine("- Target Word Count: ${executionConfig.target_word_count}")
            appendLine("- Number of Sections: ${executionConfig.number_of_sections}")
            appendLine()
            appendLine("## Features")
            appendLine("- Literature Review: ${if (executionConfig.include_literature_review) "✓" else "✗"}")
            appendLine("- Methodology: ${if (executionConfig.include_methodology) "✓" else "✗"}")
            appendLine("- Statistical Analysis: ${if (executionConfig.include_statistical_analysis) "✓" else "✗"}")
            appendLine("- Peer Review: ${if (executionConfig.include_peer_review) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Research Analysis")
            appendLine("*Analyzing sources and identifying research gaps...*")
        }
        markdownTranscript?.write(overviewContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        overviewTask.add(overviewContent.renderMarkdown)
        task.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Research Paper: $researchTopic\n\n")

        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState)
            val contextFiles = getRelatedContextFiles()

            if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                val contextTask = tabs.newTask("Sources")
                contextTask.header("Research Sources & Context")
                if (fullContext.isNotBlank()) {
                    contextTask.expandable("Input Context", fullContext.truncateForDisplay(3000).renderMarkdown)
                }
                if (priorContext.isNotBlank()) {
                    contextTask.expandable("Prior Context", priorContext.truncateForDisplay(2000).renderMarkdown)
                }
                if (contextFiles.isNotBlank()) {
                    contextTask.expandable("Related Files", contextFiles.truncateForDisplay(2000).renderMarkdown)
                }
                val contextContent = buildString {
                    appendLine("# Research Sources & Context")
                    if (fullContext.isNotBlank()) appendLine("\n## Input Context\n${fullContext.truncateForDisplay(3000)}")
                    if (priorContext.isNotBlank()) appendLine(
                        "\n## Prior Context\n${
                            priorContext.truncateForDisplay(
                                2000
                            )
                        }"
                    )
                    if (contextFiles.isNotBlank()) appendLine(
                        "\n## Related Files\n${
                            contextFiles.truncateForDisplay(
                                2000
                            )
                        }"
                    )
                }
                markdownTranscript?.write(contextContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                task.update()
            }

            // Phase 1: Research Analysis
            log.info("Phase 1: Analyzing research sources")
            val analysisTask = tabs.newTask("Analysis")

            val analysisBuffer = analysisTask.add(
                buildString {
                    appendLine("# Research Analysis")
                    appendLine()
                    appendLine("**Status:** Analyzing sources and identifying gaps...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val analysisAgent = ChatAgent(
                prompt = """
You are a research analyst. Analyze the provided sources and research context.

Research Topic: $researchTopic
Paper Type: ${executionConfig.paper_type}
Academic Level: ${executionConfig.academic_level}

${if (fullContext.isNotBlank()) "Sources and Context:\n${fullContext.truncateForDisplay(5000)}\n" else ""}
${if (priorContext.isNotBlank()) "Additional Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}

Provide:
1. Summary of existing research on this topic
2. Key findings and themes from sources
3. Research gaps and unanswered questions
4. Potential research directions
5. Methodological considerations

Be thorough and academic in tone.
        """.trimIndent(),
                model = api,
                temperature = 0.6
            )

            val analysisResult = analysisAgent.answer(listOf("Analyze the research"))
            log.info("Research analysis complete")

            val analysisContent = buildString {
                appendLine("# Research Analysis")
                appendLine()
                appendLine(analysisResult)
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            markdownTranscript?.write(analysisContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            analysisBuffer?.setLength(0)
            analysisBuffer?.append(analysisContent.renderMarkdown)
            task.update()

            overviewTask.add("✅ Phase 1 Complete: Research analyzed\n".renderMarkdown)
            overviewTask.add("\n### Phase 2: Outline Generation\n*Creating paper structure...*\n".renderMarkdown)
            task.update()

            // Phase 2: Create Paper Outline
            log.info("Phase 2: Creating paper outline")
            val outlineTask = tabs.newTask("Outline")

            val outlineBuffer = outlineTask.add(
                buildString {
                    appendLine("# Paper Outline")
                    appendLine()
                    appendLine("**Status:** Structuring paper sections...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val outlineAgent = ParsedAgent(
                resultClass = ResearchOutline::class.java,
                prompt = """
You are an academic paper structure expert. Create a detailed outline for this research paper.

Research Topic: $researchTopic
Paper Type: ${executionConfig.paper_type}
Academic Level: ${executionConfig.academic_level}
Target Word Count: ${executionConfig.target_word_count}
Number of Sections: ${executionConfig.number_of_sections}

Research Analysis:
${analysisResult.truncateForDisplay(3000)}

Create an outline with:
1. A compelling title
2. A clear thesis statement
3. Abstract summary (150-250 words)
4. ${executionConfig.number_of_sections} main sections including:
   - Introduction
   ${if (executionConfig.include_literature_review) "- Literature Review" else ""}
   ${if (executionConfig.include_methodology) "- Methodology" else ""}
   - Results/Findings
   - Discussion
   ${if (executionConfig.include_statistical_analysis) "- Statistical Analysis" else ""}
   - Conclusion
5. Key research gaps addressed
6. Estimated word count per section

For each section, specify:
- Section title and purpose
- Key points to cover
- Whether citations are required
- Estimated word count

Ensure academic rigor appropriate for ${executionConfig.academic_level} level.
        """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = defaultFast
            )

            val outline = outlineAgent.answer(listOf("Create outline")).obj
            log.info("Created outline with ${outline.sections.size} sections")

            val outlineContent = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine("**Thesis:** ${outline.thesis_statement}")
                appendLine()
                appendLine("**Abstract Summary:** ${outline.abstract_summary}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Main Sections")
                outline.sections.forEach { section ->
                    appendLine("#### ${section.section_number}. ${section.title}")
                    appendLine()
                    appendLine("**Purpose:** ${section.purpose}")
                    appendLine()
                    appendLine("**Key Points:**")
                    section.key_points.forEach { point ->
                        appendLine("- $point")
                    }
                    appendLine()
                    appendLine("**Est. Words:** ${section.estimated_word_count}")
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("### Research Gaps Addressed")
                outline.research_gaps.forEach { gap ->
                    appendLine("- $gap")
                }
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            markdownTranscript?.write(outlineContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            outlineBuffer?.setLength(0)
            outlineBuffer?.append(outlineContent.renderMarkdown)
            task.update()

            resultBuilder.append("## ${outline.title}\n\n")
            resultBuilder.append("**Thesis:** ${outline.thesis_statement}\n\n")

            overviewTask.add("✅ Phase 2 Complete: Outline created (${outline.sections.size} sections)\n".renderMarkdown)
            overviewTask.add("\n### Phase 3: Content Generation\n*Writing paper sections...*\n".renderMarkdown)
            task.update()

            // Phase 3: Generate Each Section
            log.info("Phase 3: Generating paper sections")
            val generatedSections = mutableListOf<GeneratedSection>()
            var cumulativeWordCount = 0
            val allCitations = mutableListOf<Citation>()

            outline.sections.forEachIndexed { index, sectionOutline ->
                log.info("Generating section ${index + 1}/${outline.sections.size}: ${sectionOutline.title}")

                overviewTask.add("- Section ${sectionOutline.section_number}: ${sectionOutline.title} ".renderMarkdown)
                task.update()

                val sectionTask = tabs.newTask("Section ${sectionOutline.section_number}")

                val sectionBuffer = sectionTask.add(
                    buildString {
                        appendLine("# Section ${sectionOutline.section_number}: ${sectionOutline.title}")
                        appendLine()
                        appendLine("**Status:** Writing section...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                // Build context from previous sections
                val previousContext = if (generatedSections.isNotEmpty()) {
                    buildString {
                        appendLine("## Previous Sections Summary")
                        generatedSections.takeLast(1).forEach { prevSection ->
                            appendLine("### ${prevSection.title}")
                            appendLine("Key arguments: ${prevSection.key_arguments.joinToString("; ")}")
                            appendLine()
                        }
                    }
                } else {
                    "This is the opening section."
                }

                val sectionAgent = ParsedAgent(
                    resultClass = GeneratedSection::class.java,
                    prompt = """
You are an academic writer. Write Section ${sectionOutline.section_number}: ${sectionOutline.title}

Paper Title: ${outline.title}
Thesis: ${outline.thesis_statement}
Paper Type: ${executionConfig.paper_type}
Academic Level: ${executionConfig.academic_level}

Section Details:
- Purpose: ${sectionOutline.purpose}
- Target Word Count: ${sectionOutline.estimated_word_count}

Key Points to Cover:
${sectionOutline.key_points.joinToString("\n") { "- $it" }}

$previousContext

Research Context:
${analysisResult.truncateForDisplay(2000)}

Write a complete, academically rigorous section that:
1. Opens with a clear topic sentence
2. Develops arguments with evidence
${if (sectionOutline.requires_citations) "3. Includes citations in [Author, Year] format" else ""}
4. Maintains logical flow
5. Uses appropriate academic terminology
6. Concludes with transition to next section
7. Approximately ${sectionOutline.estimated_word_count} words

After writing, provide:
- The section content
- Actual word count
- Citations used (in [Author, Year] format)
- 3-5 key arguments or findings

Write in a ${executionConfig.academic_level} level academic style.
          """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = defaultFast
                )

                var generatedSection = sectionAgent.answer(listOf("Write section")).obj
                generatedSections.add(generatedSection)
                cumulativeWordCount += generatedSection.word_count

                val sectionContent =
                    buildString {
                        appendLine("# ${sectionOutline.title}")
                        appendLine()
                        appendLine(generatedSection.content)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Word Count:** ${generatedSection.word_count}")
                        appendLine()
                        if (generatedSection.citations_used.isNotEmpty()) {
                            appendLine("**Citations Used:** ${generatedSection.citations_used.joinToString(", ")}")
                            appendLine()
                        }
                        appendLine("**Key Arguments:**")
                        generatedSection.key_arguments.forEach { arg ->
                            appendLine("- $arg")
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }
                sectionBuffer?.setLength(0)
                sectionBuffer?.append(sectionContent.renderMarkdown)
                markdownTranscript?.write(sectionContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                task.update()

                resultBuilder.append("## ${sectionOutline.title}\n\n")
                resultBuilder.append(generatedSection.content)
                resultBuilder.append("\n\n")

                overviewTask.add("✅ (${generatedSection.word_count} words)\n".renderMarkdown)
                task.update()
            }

            overviewTask.add("✅ Phase 3 Complete: All sections written\n".renderMarkdown)

            // Phase 4: Generate Bibliography
            log.info("Phase 4: Generating bibliography")
            overviewTask.add("\n### Phase 4: Bibliography Generation\n*Compiling citations...*\n".renderMarkdown)
            task.update()

            val bibliographyTask = tabs.newTask("Bibliography")

            val bibBuffer = bibliographyTask.add(
                buildString {
                    appendLine("# Bibliography")
                    appendLine()
                    appendLine("**Status:** Generating bibliography...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val bibliographyAgent = ParsedAgent(
                resultClass = Bibliography::class.java,
                prompt = """
You are a citation expert. Generate a bibliography for this research paper.

Paper Topic: $researchTopic
Citation Style: ${executionConfig.citation_style}

Citations Used in Paper:
${generatedSections.flatMap { it.citations_used }.distinct().joinToString("\n") { "- $it" }}

Research Sources:
${analysisResult.truncateForDisplay(3000)}

Create a comprehensive bibliography with:
- All citations mentioned in the paper
- Additional relevant sources from the research analysis
- Proper formatting in ${executionConfig.citation_style} style

For each citation, provide:
- Key (identifier used in paper)
- Authors
- Year
- Title
- Source/Journal/Publisher
- URL/DOI if available

Ensure all citations are properly formatted and complete.
        """.trimIndent(),
                model = api,
                temperature = 0.6,
                parsingChatter = defaultFast
            )

            val bibliography = bibliographyAgent.answer(listOf("Generate bibliography")).obj.citations
            log.info("Generated ${bibliography.size} citations")

            val bibliographyContent = buildString {
                appendLine("# Bibliography")
                appendLine()
                appendLine("**Citation Style:** ${executionConfig.citation_style.uppercase()}")
                appendLine()
                bibliography.forEach { citation ->
                    appendLine("### ${citation.key}")
                    appendLine()
                    appendLine("**Authors:** ${citation.authors}")
                    appendLine()
                    appendLine("**Year:** ${citation.year}")
                    appendLine()
                    appendLine("**Title:** ${citation.title}")
                    appendLine()
                    appendLine("**Source:** ${citation.source}")
                    appendLine()
                    if (citation.url.isNotBlank()) {
                        appendLine("**URL/DOI:** ${citation.url}")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }
            bibBuffer?.setLength(0)
            bibBuffer?.append(bibliographyContent.renderMarkdown)
            markdownTranscript?.write(bibliographyContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            task.update()

            resultBuilder.append("## Bibliography\n\n")
            bibliography.forEach { citation ->
                resultBuilder.append("- **${citation.key}:** ${citation.authors} (${citation.year}). ${citation.title}\n")
            }
            resultBuilder.append("\n")

            overviewTask.add("✅ Phase 4 Complete: Bibliography generated (${bibliography.size} citations)\n".renderMarkdown)

            // Phase 5: Peer Review (if enabled)
            if (executionConfig.include_peer_review) {
                overviewTask.add("\n### Phase 5: Peer Review\n*Simulating peer review process...*\n".renderMarkdown)
                task.update()

                log.info("Phase 5: Generating peer review")
                val reviewTask = tabs.newTask("Peer Review")

                val reviewBuffer = reviewTask.add(
                    buildString {
                        appendLine("# Peer Review")
                        appendLine()
                        appendLine("**Status:** Simulating peer review...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val reviewAgent = ParsedAgent(
                    resultClass = PeerReview::class.java,
                    prompt = """
You are an academic peer reviewer. Provide a critical review of this research paper.

Paper Title: ${outline.title}
Paper Type: ${executionConfig.paper_type}
Academic Level: ${executionConfig.academic_level}

Paper Summary:
${resultBuilder.toString().truncateForDisplay(5000)}

Provide a comprehensive peer review including:

1. Overall Assessment: Brief summary of the paper's contribution
2. Strengths: 3-5 positive aspects (methodology, clarity, novelty, etc.)
3. Weaknesses: 3-5 areas for improvement (gaps, limitations, unclear sections, etc.)
4. Suggestions: 3-5 specific recommendations for improvement
5. Recommendation: Accept / Minor Revisions / Major Revisions / Reject

Be constructive but rigorous. Consider:
- Novelty and contribution to the field
- Methodological soundness
- Clarity of presentation
- Completeness of literature review
- Validity of conclusions
- Appropriate for ${executionConfig.academic_level} level

Format as a professional peer review.
          """.trimIndent(),
                    model = api,
                    temperature = 0.6,
                    parsingChatter = defaultFast
                )

                val review = reviewAgent.answer(listOf("Review the paper")).obj
                log.info("Peer review generated")

                val reviewContent = buildString {
                    appendLine("# Peer Review Report")
                    appendLine()
                    appendLine("### Overall Assessment")
                    appendLine(review.overall_assessment)
                    appendLine()
                    appendLine("### Strengths")
                    review.strengths.forEach { strength ->
                        appendLine("- $strength")
                    }
                    appendLine()
                    appendLine("### Weaknesses")
                    review.weaknesses.forEach { weakness ->
                        appendLine("- $weakness")
                    }
                    appendLine()
                    appendLine("### Suggestions for Improvement")
                    review.suggestions.forEach { suggestion ->
                        appendLine("- $suggestion")
                    }
                    appendLine()
                    appendLine("### Recommendation")
                    appendLine("**${review.recommendation.uppercase()}**")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                reviewBuffer?.setLength(0)
                reviewBuffer?.append(reviewContent.renderMarkdown)
                markdownTranscript?.write(reviewContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                task.update()

                resultBuilder.append("## Peer Review\n\n")
                resultBuilder.append("**Recommendation:** ${review.recommendation}\n\n")
                resultBuilder.append("**Strengths:**\n")
                review.strengths.forEach { resultBuilder.append("- $it\n") }
                resultBuilder.append("\n**Weaknesses:**\n")
                review.weaknesses.forEach { resultBuilder.append("- $it\n") }
                resultBuilder.append("\n")

                overviewTask.add("✅ Phase 5 Complete: Peer review completed\n".renderMarkdown)
            }

            // Phase 6: Final Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                overviewTask.add("\n### Phase 6: Revision\n*Refining paper...*\n".renderMarkdown)
                task.update()

                log.info("Phase 6: Performing ${executionConfig.revision_passes} revision pass(es)")
                val revisionTask = tabs.newTask("Revision")

                revisionTask.add(
                    buildString {
                        appendLine("# Revision Process")
                        appendLine()
                        appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val fullPaper = resultBuilder.toString()

                repeat(executionConfig.revision_passes) { passNum ->
                    log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")

                    val revisionAgent = ChatAgent(
                        prompt = """
You are an expert academic editor. Review and improve this research paper.

Current Paper:
$fullPaper

Focus on:
1. Academic rigor and clarity
2. Logical flow and organization
3. Consistency of terminology
4. Proper citation integration
5. Appropriate tone for ${executionConfig.academic_level} level
6. Completeness of arguments
7. Clarity of conclusions

Maintain:
- All key content and arguments
- Citation format (${executionConfig.citation_style})
- Approximate word count ($cumulativeWordCount words)
- Academic structure

Provide the complete revised paper.
            """.trimIndent(),
                        model = api,
                        temperature = 0.6
                    )

                    val revisedPaper = revisionAgent.answer(listOf("Revise the paper"))
                    resultBuilder.clear()
                    resultBuilder.append(revisedPaper)

                    revisionTask.add(
                        buildString {
                            appendLine("## Revision Pass ${passNum + 1}")
                            appendLine()
                            appendLine("✅ Complete")
                            appendLine()
                        }.renderMarkdown
                    )
                    markdownTranscript?.write("## Revision Pass ${passNum + 1}\n\n✅ Complete\n\n".toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                    task.update()
                }

                overviewTask.add("✅ Phase 6 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown)
            }

            // Phase 7: Final Assembly
            overviewTask.add("\n### Phase 7: Final Assembly\n*Compiling complete paper...*\n".renderMarkdown)
            task.update()

            log.info("Phase 7: Assembling final paper")
            val finalTask = tabs.newTask("Complete Paper")

            val finalPaper = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine("**Research Topic:** $researchTopic")
                appendLine()
                appendLine("**Paper Type:** ${executionConfig.paper_type}")
                appendLine()
                appendLine("**Academic Level:** ${executionConfig.academic_level}")
                appendLine()
                appendLine("**Date:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Abstract")
                appendLine()
                appendLine(outline.abstract_summary)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(resultBuilder.toString())
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Total Word Count:** $cumulativeWordCount")
                appendLine()
                appendLine(
                    "**Paper Generated:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
            }

            finalTask.add(finalPaper.renderMarkdown)
            val filename = "Research_Paper_${System.currentTimeMillis()}.md"
            val fileUrl = task.saveFile(filename, finalPaper.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            finalTask.add("<div class='mt-3'><a href='$fileUrl' class='btn btn-primary' target='_blank'>Download Markdown</a></div>")

            markdownTranscript?.write(finalPaper.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            task.update()

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
                    appendLine("- Number of Sections: ${generatedSections.size}")
                    appendLine("- Citations: ${bibliography.size}")
                    appendLine("- Revision Passes: ${executionConfig.revision_passes}")
                    appendLine("- Total Time: ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown
            )
            markdownTranscript?.write(overviewTask.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            task.update()

            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# Research Paper Generation Summary")
                appendLine()
                appendLine("## ${outline.title}")
                appendLine()
                appendLine("A complete ${executionConfig.academic_level} level ${executionConfig.paper_type} research paper of **$cumulativeWordCount words** was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Key Highlights:**")
                appendLine("- ${generatedSections.size} sections written")
                appendLine("- ${bibliography.size} citations compiled")
                appendLine("- Citation style: ${executionConfig.citation_style.uppercase()}")
                if (executionConfig.include_peer_review) {
                    appendLine("- Peer review completed")
                }
                appendLine()
                appendLine("> The complete paper is available in the Complete Paper tab for detailed review.")
            }

            log.info("ResearchPaperGenerationTask completed: words=$cumulativeWordCount, sections=${generatedSections.size}, citations=${bibliography.size}, time=${totalTime}ms")
            markdownTranscript?.write("\n\n---\n\n# Final Result\n\n${finalResult}".toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            markdownTranscript?.close()

            task.safeComplete(
                "Research paper generation complete: $cumulativeWordCount words in ${totalTime / 1000}s",
                log
            )
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during research paper generation", e)
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
            markdownTranscript?.write(
                "\n\n---\n\n# Error\n\n**Error:** ${e.message}\n\n**Type:** ${e.javaClass.simpleName}\n".toByteArray(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            )
            task.update()

            val errorOutput = buildString {
                appendLine("# Error in Research Paper Generation")
                appendLine()
                appendLine("**Topic:** $researchTopic")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                if (resultBuilder.isNotEmpty()) {
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(resultBuilder.toString())
                }
            }
            markdownTranscript?.close()
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
                val content = if (!isTextFile(file)) {
                    extractDocumentContent(file)
                } else {
                    codeFiles[file.toPath()] ?: file.readText()
                }
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    private fun getRelatedContextFiles(): String {
        val relatedFiles = executionConfig?.research_files ?: return ""
        if (relatedFiles.isEmpty()) return ""
        log.debug("Loading ${relatedFiles.size} related research files")

        return buildString {
            appendLine("## Research Files")
            appendLine()
            relatedFiles.forEach { file ->
                try {
                    val filePath = root.resolve(file)
                    if (filePath.toFile().exists()) {
                        log.debug("Successfully loaded research file: $file")
                        appendLine("### $file")
                        appendLine("```")
                        appendLine(filePath.toFile().readText().truncateForDisplay(1500))
                        appendLine("```")
                        appendLine()
                    } else {
                        log.warn("Research file not found: $file")
                    }
                } catch (e: Exception) {
                    log.warn("Error reading file: $file", e)
                }
            }
        }
    }

    private fun isTextFile(file: java.io.File): Boolean {
        val textExtensions = setOf(
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
            "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven", "pdf", "doc", "docx"
        )
        return textExtensions.contains(file.extension.lowercase())
    }

    private fun extractDocumentContent(file: java.io.File) = try {
        file.getDocumentReader().use { reader ->
            when (reader) {
                is com.simiacryptus.cognotik.input.PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                else -> reader.getText()
            }
        }
    } catch (e: Exception) {
        log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
        try {
            file.readText()
        } catch (e2: Exception) {
            "Error reading file: ${e2.message}"
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ResearchPaperGenerationTask::class.java)
        val ResearchPaperGeneration = TaskType(
            "ResearchPaperGeneration",
            "Writing",
            ResearchPaperGenerationTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate comprehensive academic research papers with citations",
            """
              Generates complete, publication-ready academic research papers.
              <ul>
                <li>Analyzes research sources and identifies gaps</li>
                <li>Creates structured academic outline</li>
                <li>Generates multi-section papers with proper citations</li>
                <li>Supports multiple paper types (empirical, theoretical, review, meta-analysis)</li>
                <li>Configurable academic levels (undergraduate to postdoc)</li>
                <li>Multiple citation styles (APA, MLA, Chicago, IEEE)</li>
                <li>Automatic bibliography generation</li>
                <li>Optional peer review simulation</li>
                <li>Revision passes for quality improvement</li>
                <li>Ideal for academic research, literature reviews, thesis chapters</li>
              </ul>
            """
        )
    }
}