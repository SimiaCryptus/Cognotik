package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.input.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ResearchPaperGenerationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ResearchPaperGenerationTaskExecutionConfigData?
) : AbstractTask<ResearchPaperGenerationTask.ResearchPaperGenerationTaskExecutionConfigData, ResearchPaperGenerationTask.ResearchPaperGenerationTypeConfig>(
    orchestrationConfig,
    planTask
) {
    class ResearchPaperGenerationTypeConfig(
        var analysisPrompt: String = """
            You are a research analyst. Analyze the provided sources and research context.
            Research Topic: {research_topic}
            Paper Type: {paper_type}
            Academic Level: {academic_level}
            {context}
            Provide:
            1. Summary of existing research on this topic
            2. Key findings and themes from sources
            3. Research gaps and unanswered questions
            4. Potential research directions
            5. Methodological considerations
            Be thorough and academic in tone.
        """.trimIndent(),
        var outlinePrompt: String = """
            You are an academic paper structure expert. Create a detailed outline for this research paper.
            Research Topic: {research_topic}
            Paper Type: {paper_type}
            Academic Level: {academic_level}
            Target Word Count: {target_word_count}
            Number of Sections: {number_of_sections}
            Research Analysis:
            {analysis_result}
            Create an outline with:
            1. A compelling title
            2. A clear thesis statement
            3. Abstract summary (150-250 words)
            4. {number_of_sections} main sections including:
               - Introduction
               {literature_review}
               {methodology}
               - Results/Findings
               - Discussion
               {statistical_analysis}
               - Conclusion
            5. Key research gaps addressed
            6. Estimated word count per section
            For each section, specify:
            - Section title and purpose
            - Key points to cover
            - Whether citations are required
            - Estimated word count
            Ensure academic rigor appropriate for {academic_level} level.
        """.trimIndent(),
        var sectionPrompt: String = """
            You are an academic writer. Write Section {section_number}: {section_title}
            Paper Title: {paper_title}
            Thesis: {thesis_statement}
            Paper Type: {paper_type}
            Academic Level: {academic_level}
            Section Details:
            - Purpose: {section_purpose}
            - Target Word Count: {target_word_count}
            Key Points to Cover:
            {key_points}
            {previous_context}
            Research Context:
            {analysis_result}
            Write a complete, academically rigorous section that:
            1. Opens with a clear topic sentence
            2. Develops arguments with evidence
            {citation_instruction}
            4. Maintains logical flow
            5. Uses appropriate academic terminology
            6. Concludes with transition to next section
            7. Approximately {target_word_count} words
            After writing, provide:
            - The section content
            - Actual word count
            - Citations used (in [Author, Year] format)
            - 3-5 key arguments or findings
            Write in a {academic_level} level academic style.
        """.trimIndent(),
        var bibliographyPrompt: String = """
            You are a citation expert. Generate a bibliography for this research paper.
            Paper Topic: {research_topic}
            Citation Style: {citation_style}
            Citations Used in Paper:
            {citations_used}
            Research Sources:
            {analysis_result}
            Create a comprehensive bibliography with:
            - All citations mentioned in the paper
            - Additional relevant sources from the research analysis
            - Proper formatting in {citation_style} style
            For each citation, provide:
            - Key (identifier used in paper)
            - Authors
            - Year
            - Title
            - Source/Journal/Publisher
            - URL/DOI if available
            Ensure all citations are properly formatted and complete.
        """.trimIndent(),
        var reviewPrompt: String = """
            You are an academic peer reviewer. Provide a critical review of this research paper.
            Paper Title: {paper_title}
            Paper Type: {paper_type}
            Academic Level: {academic_level}
            Paper Summary:
            {paper_content}
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
            - Appropriate for {academic_level} level
            Format as a professional peer review.
        """.trimIndent(),
        var revisionPrompt: String = """
            You are an expert academic editor. Review and improve this research paper.
            Current Paper:
            {paper_content}
            Focus on:
            1. Academic rigor and clarity
            2. Logical flow and organization
            3. Consistency of terminology
            4. Proper citation integration
            5. Appropriate tone for {academic_level} level
            6. Completeness of arguments
            7. Clarity of conclusions
            Maintain:
            - All key content and arguments
            - Citation format ({citation_style})
            - Approximate word count ({word_count} words)
            - Academic structure
            Provide the complete revised paper.
        """.trimIndent()
    ) : TaskTypeConfig()


    class ResearchPaperGenerationTaskExecutionConfigData(
        @Description("The main research question or topic")
        var research_topic: String? = null,

        @Description("Type of research paper (e.g., 'empirical', 'theoretical', 'review', 'meta-analysis')")
        var paper_type: String = "empirical",

        @Description("Academic level (e.g., 'undergraduate', 'masters', 'phd', 'postdoc')")
        var academic_level: String = "masters",

        @Description("Target word count for the complete paper")
        var target_word_count: Int = 8000,

        @Description("Citation style (e.g., 'apa', 'mla', 'chicago', 'ieee')")
        var citation_style: String = "apa",

        @Description("Whether to include a literature review section")
        var include_literature_review: Boolean = true,

        @Description("Whether to include methodology section")
        var include_methodology: Boolean = true,

        @Description("Whether to include statistical analysis descriptions")
        var include_statistical_analysis: Boolean = true,

        @Description("Whether to include peer review simulation")
        var include_peer_review: Boolean = true,

        @Description("Number of main sections (excluding abstract/conclusion)")
        var number_of_sections: Int = 6,

        @Description("Number of revision passes for quality improvement")
        var revision_passes: Int = 1,

        @Description("Research source files or data to incorporate")
        var research_files: List<String>? = null,

        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,

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
  ** research_topic: The main research question or topic
  ** paper_type: 'empirical', 'theoretical', 'review', or 'meta-analysis'
  ** academic_level: 'undergraduate', 'masters', 'phd', or 'postdoc'
  ** target_word_count: Target word count for the complete paper
  ** citation_style: 'apa', 'mla', 'chicago', or 'ieee'
  ** include_literature_review: Whether to include a literature review section
  ** include_methodology: Whether to include methodology section
  ** include_statistical_analysis: Whether to include statistical analysis descriptions
  ** include_peer_review: Whether to include peer review simulation
  ** number_of_sections: Number of main sections
  ** revision_passes: Number of revision passes
  ** research_files: Research source files or data to incorporate
  ** input_files: Specific files or patterns to use as input
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
        log.info("Starting ResearchPaperGenerationTask for topic: '{}'", executionConfig?.research_topic)
        val markdownTranscript = task.transcript()

        // Read input from messages parameter
        val messageContext = messages.filter { it.isNotBlank() }.joinToString("\n\n").trim()
        log.debug("Received ${messages.size} messages with total length: ${messageContext.length}")

        // Load input files if specified
        val inputFileContent = getInputFileCode()
        log.debug("Loaded input files: ${inputFileContent.length} characters")

        val fullContext =
            listOfNotNull(messageContext, inputFileContent).filter { it.isNotBlank() }.joinToString("\n\n---\n\n")

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: {}", validationError)
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            markdownTranscript?.write(
                """
                ## Configuration Error
                $validationError
                <details><summary>Config Dump</summary>```json${
                    JsonUtil.toJson(
                        executionConfig
                    )
                }```</details>
            """.trimIndent().toByteArray()
            )
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
        markdownTranscript?.write(
            """
            # Research Paper Generation Started
            <details><summary>Initial Configuration</summary>$overviewContent</details>
        """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        )
        overviewTask.add(overviewContent.renderMarkdown(true))
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
                    contextTask.expandable("Input Context", fullContext.truncateForDisplay(3000).renderMarkdown(true))
                }
                if (priorContext.isNotBlank()) {
                    contextTask.expandable("Prior Context", priorContext.truncateForDisplay(2000).renderMarkdown(true))
                }
                if (contextFiles.isNotBlank()) {
                    contextTask.expandable("Related Files", contextFiles.truncateForDisplay(2000).renderMarkdown(true))
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
                markdownTranscript?.write(
                    """
                    ## Research Sources & Context
                    <details><summary>Full Context Data</summary>$contextContent</details>
                """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                )
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
              }.renderMarkdown(true)
            )
            task.update()
            val analysisContextStr = listOfNotNull(
                if (fullContext.isNotBlank()) "Sources and Context:\n${fullContext.truncateForDisplay(5000)}\n" else null,
                if (priorContext.isNotBlank()) "Additional Context:\n${priorContext.truncateForDisplay(3000)}\n" else null
            ).joinToString("\n")


            val typeConfig = typeConfig!!
            val analysisAgent = ChatAgent(


                prompt = typeConfig!!.analysisPrompt.replace("{research_topic}", researchTopic)
                    .replace("{paper_type}", executionConfig.paper_type)
                    .replace("{academic_level}", executionConfig.academic_level)
                    .replace("{context}", analysisContextStr),
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
            }.trimIndent()
            markdownTranscript?.write(
                """
                ## Phase 1: Research Analysis Complete
                <details><summary>Analysis Results</summary>$analysisContent</details>
            """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            )
            analysisBuffer?.setLength(0)
            analysisBuffer?.append(analysisContent.renderMarkdown(true))
            task.update()

            overviewTask.add("✅ Phase 1 Complete: Research analyzed\n".renderMarkdown(true))
            overviewTask.add("\n### Phase 2: Outline Generation\n*Creating paper structure...*\n".renderMarkdown(true))
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
              }.renderMarkdown(true)
            )
            task.update()

            val outlineAgent = ParsedAgent(
                resultClass = ResearchOutline::class.java,


                prompt = typeConfig.outlinePrompt.replace("{research_topic}", researchTopic)
                    .replace("{paper_type}", executionConfig.paper_type)
                    .replace("{academic_level}", executionConfig.academic_level)
                    .replace("{target_word_count}", executionConfig.target_word_count.toString())
                    .replace("{number_of_sections}", executionConfig.number_of_sections.toString())
                    .replace("{analysis_result}", analysisResult.truncateForDisplay(3000))
                    .replace(
                        "{literature_review}",
                        if (executionConfig.include_literature_review) "- Literature Review" else ""
                    )
                    .replace("{methodology}", if (executionConfig.include_methodology) "- Methodology" else "")
                    .replace(
                        "{statistical_analysis}",
                        if (executionConfig.include_statistical_analysis) "- Statistical Analysis" else ""
                    ),
                model = api,
                temperature = 0.7,
                parsingChatter = defaultFast
            )

            val outline = outlineAgent.answer(listOf("Create outline")).obj
            log.info("Created outline with {} sections", outline.sections.size)

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
            markdownTranscript?.write(
                """
                ## Phase 2: Outline Generation Complete
                <details><summary>Paper Outline</summary>$outlineContent</details>
            """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            )
            outlineBuffer?.append(outlineContent.renderMarkdown(true))
            task.update()

            resultBuilder.append("## ${outline.title}\n\n")
            resultBuilder.append("**Thesis:** ${outline.thesis_statement}\n\n")

            overviewTask.add(
              "✅ Phase 2 Complete: Outline created (${outline.sections.size} sections)\n".renderMarkdown(
                true
              )
            )
            overviewTask.add("\n### Phase 3: Content Generation\n*Writing paper sections...*\n".renderMarkdown(true))
            task.update()

            // Phase 3: Generate Each Section
            log.info("Phase 3: Generating paper sections")
            val generatedSections = mutableListOf<GeneratedSection>()
            var cumulativeWordCount = 0
            val allCitations = mutableListOf<Citation>()

            outline.sections.forEachIndexed { index, sectionOutline ->
                log.info("Generating section ${index + 1}/${outline.sections.size}: ${sectionOutline.title}")

                overviewTask.add(
                  "- Section ${sectionOutline.section_number}: ${sectionOutline.title} ".renderMarkdown(
                    true
                  )
                )
                task.update()

                val sectionTask = tabs.newTask("Section ${sectionOutline.section_number}")

                val sectionBuffer = sectionTask.add(
                  buildString {
                    appendLine("# Section ${sectionOutline.section_number}: ${sectionOutline.title}")
                    appendLine()
                    appendLine("**Status:** Writing section...")
                    appendLine()
                  }.renderMarkdown(true)
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


                    prompt = typeConfig.sectionPrompt.replace(
                        "{section_number}",
                        sectionOutline.section_number.toString()
                    )
                        .replace("{section_title}", sectionOutline.title).replace("{paper_title}", outline.title)
                        .replace("{thesis_statement}", outline.thesis_statement)
                        .replace("{paper_type}", executionConfig.paper_type)
                        .replace("{academic_level}", executionConfig.academic_level)
                        .replace("{section_purpose}", sectionOutline.purpose)
                        .replace("{target_word_count}", sectionOutline.estimated_word_count.toString())
                        .replace("{key_points}", sectionOutline.key_points.joinToString("\n") { "- $it" })
                        .replace("{previous_context}", previousContext)
                        .replace("{analysis_result}", analysisResult.truncateForDisplay(2000))
                        .replace(
                            "{citation_instruction}",
                            if (sectionOutline.requires_citations) "3. Includes citations in [Author, Year] format" else ""
                        ),
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
                sectionBuffer?.append(sectionContent.renderMarkdown(true))
                markdownTranscript?.write(
                    """
                    ### Section ${sectionOutline.section_number}: ${sectionOutline.title}
                    <details><summary>Section Content</summary>$sectionContent</details>
                """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                )
                task.update()

                resultBuilder.append("## ${sectionOutline.title}\n\n")
                resultBuilder.append(generatedSection.content)
                resultBuilder.append("\n\n")

                overviewTask.add("✅ (${generatedSection.word_count} words)\n".renderMarkdown(true))
                task.update()
            }

            overviewTask.add("✅ Phase 3 Complete: All sections written\n".renderMarkdown(true))

            // Phase 4: Generate Bibliography
            log.info("Phase 4: Generating bibliography")
            overviewTask.add("\n### Phase 4: Bibliography Generation\n*Compiling citations...*\n".renderMarkdown(true))
            task.update()

            val bibliographyTask = tabs.newTask("Bibliography")

            val bibBuffer = bibliographyTask.add(
              buildString {
                appendLine("# Bibliography")
                appendLine()
                appendLine("**Status:** Generating bibliography...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()

            val bibliographyAgent = ParsedAgent(
                resultClass = Bibliography::class.java,


                prompt = typeConfig.bibliographyPrompt.replace("{research_topic}", researchTopic)
                    .replace("{citation_style}", executionConfig.citation_style)
                    .replace(
                        "{citations_used}",
                        generatedSections.flatMap { it.citations_used }.distinct().joinToString("\n") { "- $it" })
                    .replace("{analysis_result}", analysisResult.truncateForDisplay(3000)),
                model = api,
                temperature = 0.6,
                parsingChatter = defaultFast
            )

            val bibliography = bibliographyAgent.answer(listOf("Generate bibliography")).obj.citations
            log.info("Generated {} citations", bibliography.size)

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
            bibBuffer?.append(bibliographyContent.renderMarkdown(true))
            markdownTranscript?.write(
                """
                ## Phase 4: Bibliography Generation Complete
                <details><summary>Bibliography Details</summary>$bibliographyContent</details>
            """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            )
            task.update()

            resultBuilder.append("## Bibliography\n\n")
            bibliography.forEach { citation ->
                resultBuilder.append("- **${citation.key}:** ${citation.authors} (${citation.year}). ${citation.title}\n")
            }
            resultBuilder.append("\n")

            overviewTask.add(
              "✅ Phase 4 Complete: Bibliography generated (${bibliography.size} citations)\n".renderMarkdown(
                true
              )
            )

            // Phase 5: Peer Review (if enabled)
            if (executionConfig.include_peer_review) {
                overviewTask.add("\n### Phase 5: Peer Review\n*Simulating peer review process...*\n".renderMarkdown(true))
                task.update()

                log.info("Phase 5: Generating peer review")
                val reviewTask = tabs.newTask("Peer Review")

                val reviewBuffer = reviewTask.add(
                  buildString {
                    appendLine("# Peer Review")
                    appendLine()
                    appendLine("**Status:** Simulating peer review...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                task.update()

                val reviewAgent = ParsedAgent(
                    resultClass = PeerReview::class.java,


                    prompt = typeConfig.reviewPrompt.replace("{paper_title}", outline.title)
                        .replace("{paper_type}", executionConfig.paper_type)
                        .replace("{academic_level}", executionConfig.academic_level)
                        .replace("{paper_content}", resultBuilder.toString().truncateForDisplay(5000)),
                    model = api,
                    temperature = 0.6,
                    parsingChatter = defaultFast
                )

                val review = reviewAgent.answer(listOf("Review the paper")).obj
                log.info("Peer review generated for topic: {}", researchTopic)

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
                reviewBuffer?.append(reviewContent.renderMarkdown(true))
                markdownTranscript?.write(
                    """
                    ## Phase 5: Peer Review Complete
                    <details><summary>Peer Review Report</summary>$reviewContent</details>
                """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                )
                task.update()

                resultBuilder.append("## Peer Review\n\n")
                resultBuilder.append("**Recommendation:** ${review.recommendation}\n\n")
                resultBuilder.append("**Strengths:**\n")
                review.strengths.forEach { resultBuilder.append("- $it\n") }
                resultBuilder.append("\n**Weaknesses:**\n")
                review.weaknesses.forEach { resultBuilder.append("- $it\n") }
                resultBuilder.append("\n")

                overviewTask.add("✅ Phase 5 Complete: Peer review completed\n".renderMarkdown(true))
            }

            // Phase 6: Final Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                overviewTask.add("\n### Phase 6: Revision\n*Refining paper...*\n".renderMarkdown(true))
                task.update()

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
                task.update()

                val fullPaper = resultBuilder.toString()

                repeat(executionConfig.revision_passes) { passNum ->
                    log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")

                    val revisionAgent = ChatAgent(


                        prompt = typeConfig.revisionPrompt.replace("{paper_content}", fullPaper)
                            .replace("{academic_level}", executionConfig.academic_level)
                            .replace("{citation_style}", executionConfig.citation_style)
                            .replace("{word_count}", cumulativeWordCount.toString()),
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
                      }.renderMarkdown(true)
                    )
                    markdownTranscript?.write(
                        """
                        ### Revision Pass ${passNum + 1}
                        <details><summary>Revision Details</summary>Revision pass ${passNum + 1} completed successfully.</details>
                    """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                    )
                    task.update()
                }

                overviewTask.add(
                  "✅ Phase 6 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown(
                    true
                  )
                )
            }

            // Phase 7: Final Assembly
            overviewTask.add("\n### Phase 7: Final Assembly\n*Compiling complete paper...*\n".renderMarkdown(true))
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

            finalTask.add(finalPaper.renderMarkdown(true))
            val filename = "Research_Paper_${System.currentTimeMillis()}.md"
            val fileUrl = task.saveFile(filename, finalPaper.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            finalTask.add("<div class='mt-3'><a href='$fileUrl' class='btn btn-primary' target='_blank'>Download Markdown</a></div>")

            markdownTranscript?.write(
                """
                ## Phase 7: Final Assembly Complete
                <details><summary>Final Paper Content</summary>$finalPaper</details>
            """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            )
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
              }.renderMarkdown(true)
            )
            markdownTranscript?.write(
                """
                ## Generation Statistics
                <details><summary>Final Stats</summary>$overviewTask</details>
            """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            )
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

            log.info(
                "ResearchPaperGenerationTask completed: words={}, sections={}, citations={}, time={}ms",
                cumulativeWordCount,
                generatedSections.size,
                bibliography.size,
                totalTime
            )
            markdownTranscript?.write("\n\n---\n\n# Final Result\n\n${finalResult}".toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            markdownTranscript?.close()

            if (orchestrationConfig.autoFix) {
                task.safeComplete(
                    "Research paper generation complete: $cumulativeWordCount words in ${totalTime / 1000}s",
                    log
                )
                resultFn(finalResult)
            } else {
                finalTask.add(
                    MarkdownUtil.renderMarkdown(
                        acceptButtonFooter(task.ui) {
                            try {
                                task.safeComplete(
                                    "Research paper generation accepted: $cumulativeWordCount words",
                                    log
                                )
                                resultFn(finalResult)
                            } catch (e: Exception) {
                                log.error("Error accepting research paper", e)
                                task.error(e)
                                resultFn("ERROR: ${e.message}")
                            }
                        }, ui = task.ui
                    )
                )
            }

        } catch (e: Exception) {
            log.error("Error during research paper generation for topic: {}", researchTopic, e)
            task.error(e)
            markdownTranscript?.write(
                """
                ## Error Occurred
                **Error:** ${e.message}
                **Type:** ${e.javaClass.simpleName}
                <details><summary>Stack Trace</summary>```${e.stackTraceToString()}```</details>
            """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            )

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
    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept and Save Research Paper") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(ResearchPaperGenerationTask::class.java)
        @JvmStatic val ResearchPaperGeneration = TaskType(
            name = "ResearchPaperGeneration",
            category = "Writing",
            taskClass = ResearchPaperGenerationTask::class.java,
            executionConfigClass = ResearchPaperGenerationTaskExecutionConfigData::class.java,
            taskSettingsClass = ResearchPaperGenerationTypeConfig::class.java,
            description = "Generate comprehensive academic research papers with citations",
            tooltipHtml = """
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
                      """,
        )
    }
}