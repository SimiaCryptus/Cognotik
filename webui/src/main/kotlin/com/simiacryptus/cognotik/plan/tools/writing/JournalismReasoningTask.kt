package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

open class JournalismReasoningTask<T : JournalismReasoningTask.JournalismReasoningTaskExecutionConfigData, U : TaskTypeConfig>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, U>(
    orchestrationConfig,
    planTask
) {

    val maxDescriptionLength = 100

    companion object {
        private val log: Logger = LoggerFactory.getLogger(JournalismReasoningTask::class.java)
        @JvmStatic val JournalismReasoning = TaskType(
            name = "JournalismReasoning",
            category = "Writing",
            taskClass = JournalismReasoningTask::class.java,
            executionConfigClass = JournalismReasoningTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Investigate stories through journalistic principles and methods",
            tooltipHtml = """
                        Analyzes stories using professional journalism standards and practices.
                        <ul>
                          <li>Verifies facts and checks claims against evidence</li>
                          <li>Identifies multiple perspectives and source credibility</li>
                          <li>Analyzes context, background, and broader implications</li>
                          <li>Detects potential biases and conflicts of interest</li>
                          <li>Finds information gaps and unanswered questions</li>
                          <li>Explores alternative story angles and approaches</li>
                          <li>Assesses newsworthiness and public interest</li>
                          <li>Useful for investigative reporting, fact-checking, editorial planning</li>
                          <li>Generates structured journalistic analysis with verified facts</li>
                        </ul>
                      """,
        )
    }

    open class JournalismReasoningTaskExecutionConfigData(
        @Description("The story topic or event to investigate")
        var story_topic: String? = null,

        @Description("Input files or documents to inform the investigation (glob patterns)")
        var input_files: List<String>? = null,

        @Description("Journalism elements to consider (who, what, when, where, why, how)")
        var journalism_elements: Map<String, Any>? = null,

        @Description("Whether to identify and verify key facts")
        var verify_facts: Boolean = true,

        @Description("Whether to identify multiple perspectives and sources")
        var identify_perspectives: Boolean = true,

        @Description("Whether to analyze context and background")
        var analyze_context: Boolean = true,

        @Description("Whether to identify potential biases and conflicts of interest")
        var identify_biases: Boolean = true,

        @Description("Whether to check for missing information or unanswered questions")
        var find_gaps: Boolean = true,

        @Description("Number of alternative angles to explore")
        var alternative_angles: Int = 3,

        @Description("Whether to assess newsworthiness and public interest")
        var assess_newsworthiness: Boolean = true,
        @Description("List of task IDs that this task depends on")

        task_dependencies: List<String>? = null,
        @Description("The current state of the task")
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = JournalismReasoning.name,
        task_description = "Investigate '$story_topic' through journalistic analysis",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (story_topic.isNullOrBlank()) {
                return "Story topic must not be null or blank"
            }
            if (alternative_angles < 1 || alternative_angles > 10) {
                return "Alternative angles must be between 1 and 10, got: $alternative_angles"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class FactCheck(
        val claim: String = "",
        val source: String = "",
        val verification_status: String = "",
        val supporting_evidence: List<String> = emptyList(),
        val contradicting_evidence: List<String> = emptyList(),
        val confidence_level: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (claim.isBlank()) {
                return "Fact claim must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class FactChecks(
        val facts: List<FactCheck> = emptyList()
    ) : ValidatedObject

    data class SourcePerspective(
        val source_name: String = "",
        val role: String = "",
        val perspective: String = "",
        val key_quotes: List<String> = emptyList(),
        val potential_bias: String = "",
        val credibility_assessment: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (source_name.isBlank()) {
                return "Source name must not be blank"
            }
            if (perspective.isBlank()) {
                return "Source perspective must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class SourcePerspectives(
        val sources: List<SourcePerspective> = emptyList()
    ) : ValidatedObject

    data class ContextAnalysis(
        val historical_background: String = "",
        val relevant_trends: List<String> = emptyList(),
        val related_events: List<String> = emptyList(),
        val broader_implications: List<String> = emptyList(),
        val key_stakeholders: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (historical_background.isBlank()) {
                return "Historical background must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class BiasAnalysis(
        val potential_biases: List<String> = emptyList(),
        val conflicts_of_interest: List<String> = emptyList(),
        val missing_voices: List<String> = emptyList(),
        val framing_issues: List<String> = emptyList(),
        val balance_assessment: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (balance_assessment.isBlank()) {
                return "Balance assessment must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class StoryAngle(
        val angle_title: String = "",
        val focus: String = "",
        val target_audience: String = "",
        val key_questions: List<String> = emptyList(),
        val unique_value: String = "",
        val newsworthiness_score: Double = 0.0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (angle_title.isBlank()) {
                return "Angle title must not be blank"
            }
            if (newsworthiness_score < 0.0 || newsworthiness_score > 1.0) {
                return "Newsworthiness score must be between 0.0 and 1.0"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class StoryAngles(
        val angles: List<StoryAngle> = emptyList()
    ) : ValidatedObject

    data class InformationGap(
        val question: String = "",
        val importance: String = "",
        val potential_sources: List<String> = emptyList(),
        val research_approach: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (question.isBlank()) {
                return "Gap question must not be blank"
            }
            val validImportance = setOf("critical", "important", "minor")
            if (importance.isNotBlank() && importance.lowercase() !in validImportance) {
                return "Importance must be one of: ${validImportance.joinToString(", ")}"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class InformationGaps(
        val gaps: List<InformationGap> = emptyList()
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
JournalismReasoning - Investigate stories through journalistic principles and methods
  ** Specify the story topic or event to investigate
  ** Define journalism elements: who, what, when, where, why, how
  ** Enable fact verification and source checking
  ** Identify multiple perspectives and stakeholder voices
  ** Analyze context, background, and broader implications
  ** Detect potential biases and conflicts of interest
  ** Find information gaps and unanswered questions
  ** Explore alternative story angles
  ** Assess newsworthiness and public interest
  ** Produces structured journalistic analysis with verified facts
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {

        val transcript = task.transcript()
        task.ui.pool.submit {
            val startTime = System.currentTimeMillis()
            val config = executionConfig ?: return@submit
            val storyTopic = config.story_topic
            if (storyTopic.isNullOrBlank()) {
                log.error("No story topic specified for journalism reasoning")
                log.error("No story topic specified for journalism reasoning")
                task.safeComplete("CONFIGURATION ERROR: No story topic specified", log)
                resultFn("CONFIGURATION ERROR: No story topic specified")
                return@submit
            }

            val journalismElements = config.journalism_elements ?: emptyMap()
            val verifyFacts = config.verify_facts
            val identifyPerspectives = config.identify_perspectives
            val analyzeContext = config.analyze_context
            val identifyBiases = config.identify_biases
            val findGaps = config.find_gaps
            val alternativeAngles = config.alternative_angles.coerceIn(1, 10)
            val assessNewsworthiness = config.assess_newsworthiness
            log.info("Starting JournalismReasoningTask for story: '$storyTopic'")

            log.info(
                "Configuration: verifyFacts=$verifyFacts, identifyPerspectives=$identifyPerspectives, " +
                        "analyzeContext=$analyzeContext, identifyBiases=$identifyBiases, findGaps=$findGaps, " +
                        "alternativeAngles=$alternativeAngles, assessNewsworthiness=$assessNewsworthiness"
            )

            val api = defaultSmart ?: return@submit

            val tabs = TabbedDisplay(task)

            // Overview tab
            val overviewTask = tabs.newTask("Overview")

            val overviewContent = buildString {
                appendLine("# Journalism Investigation")
                appendLine()
                appendLine("**Story Topic:** $storyTopic")
                appendLine()
                appendLine("## Journalism Elements")
                journalismElements.forEach { (key, value) ->
                    appendLine("- **${key.capitalize()}:** $value")
                }
                appendLine()
                appendLine("## Investigation Configuration")
                appendLine("- Verify Facts: ${if (verifyFacts) "✓" else "✗"}")
                appendLine("- Identify Perspectives: ${if (identifyPerspectives) "✓" else "✗"}")
                appendLine("- Analyze Context: ${if (analyzeContext) "✓" else "✗"}")
                appendLine("- Identify Biases: ${if (identifyBiases) "✓" else "✗"}")
                appendLine("- Find Information Gaps: ${if (findGaps) "✓" else "✗"}")
                appendLine("- Alternative Angles: $alternativeAngles")
                appendLine("- Assess Newsworthiness: ${if (assessNewsworthiness) "✓" else "✗"}")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("*Initializing investigation...*")
            }

            transcript?.write(
                """
                # Journalism Investigation Transcript
                
                **Story Topic:** $storyTopic
                **Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
                
            """.trimIndent().toByteArray()
            )

            // Include file content if requested
            val fileContent = super.getInputFileContent(config.input_files, root, treatDocumentsAsText = true)
            if (fileContent.isNotBlank()) {
                transcript?.write(
                    """
                    <details>
                    <summary>Input Files Content</summary>
                    
                    $fileContent
                    </details>
                    
                """.trimIndent().toByteArray()
                )
            }

            overviewTask.add(overviewContent.renderMarkdown(true))

            val priorContext = getPriorCode(agent.executionState)
            if (priorContext.isNotBlank()) {
                log.debug("Found prior context: ${priorContext.length} characters")
                val contextTask = tabs.newTask("Context")
                contextTask.add(
                  buildString {
                    appendLine("# Context from Previous Tasks")
                    appendLine()
                    appendLine(priorContext.truncateForDisplay())
                  }.renderMarkdown(true)
                )
            }

            val resultBuilder = StringBuilder()
            resultBuilder.append("# Journalism Investigation: $storyTopic\n\n")

            try {
                // Step 1: Verify facts
                if (verifyFacts) {
                    log.info("Step 1: Verifying facts")
                    overviewTask.add("\n✅ Verifying facts and claims...\n".renderMarkdown(true))

                    val factsTask = tabs.newTask("Fact Verification")

                    factsTask.add(
                      buildString {
                        appendLine("# Fact Verification")
                        appendLine()
                        appendLine("**Status:** Checking claims and evidence...")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    transcript?.write("## Step 1: Fact Verification\n\n".toByteArray())

                    val factAgent = ParsedAgent(
                        resultClass = FactChecks::class.java,
                        prompt = """
You are an expert fact-checker and investigative journalist. Verify the key facts and claims in this story.

Story Topic: $storyTopic

Journalism Elements:
${journalismElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

${if (priorContext.isNotBlank()) "Additional Context:\n$priorContext\n" else ""}

Identify and verify 5-10 key factual claims, including:
- Core facts about the event or topic
- Statistical claims or data points
- Attributions and quotes
- Timeline elements
- Causal relationships

For each fact, provide:
- The specific claim
- The source of the claim
- Verification status (verified, unverified, disputed, false, partially true)
- Supporting evidence
- Any contradicting evidence
- Confidence level in the verification

Apply rigorous journalistic standards. Be skeptical but fair.
          """.trimIndent(),
                        model = api,
                        temperature = 0.3,
                        parsingChatter = defaultFast
                    )

                    val factChecks = factAgent.answer(listOf("Verify facts")).obj.facts
                    log.debug("Verified ${factChecks.size} facts")

                    val factsContent = buildString {
                        appendLine("## Verified Facts")
                        appendLine()
                        factChecks.forEachIndexed { index, fact ->
                            val statusIcon = when (fact.verification_status.lowercase()) {
                                "verified" -> "✅"
                                "partially true" -> "⚠️"
                                "disputed" -> "❓"
                                "false" -> "❌"
                                else -> "⏳"
                            }
                            appendLine("### $statusIcon ${index + 1}. ${fact.claim.truncateForDisplay(80)}")
                            appendLine()
                            appendLine("**Status:** ${fact.verification_status}")
                            appendLine()
                            appendLine("**Source:** ${fact.source}")
                            appendLine()
                            appendLine("**Confidence:** ${fact.confidence_level}")
                            appendLine()
                            if (fact.supporting_evidence.isNotEmpty()) {
                                appendLine("**Supporting Evidence:**")
                                fact.supporting_evidence.forEach { evidence ->
                                    appendLine("- $evidence")
                                }
                                appendLine()
                            }
                            if (fact.contradicting_evidence.isNotEmpty()) {
                                appendLine("**Contradicting Evidence:**")
                                fact.contradicting_evidence.forEach { evidence ->
                                    appendLine("- $evidence")
                                }
                                appendLine()
                            }
                            if (index < factChecks.size - 1) {
                                appendLine("---")
                                appendLine()
                            }
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }

                    transcript?.write(
                        """
                        <details>
                        <summary>Verified Facts (${factChecks.size})</summary>
                        
                        ${factChecks.joinToString("\n") { "- **${it.verification_status}**: ${it.claim} (Source: ${it.source})" }}
                        </details>
                        
                    """.trimIndent().toByteArray()
                    )

                    factsTask.add(factsContent.renderMarkdown(true))

                    resultBuilder.append("## Key Facts\n")
                    factChecks.take(3).forEach { fact ->
                        resultBuilder.append("- ${fact.verification_status.uppercase()}: ${
                            fact.claim.truncateForDisplay(
                                maxDescriptionLength
                            )
                        }\n")
                    }
                    resultBuilder.append("\n")

                    overviewTask.add("✅ Facts verified (${factChecks.size} claims checked)\n".renderMarkdown(true))
                }

                // Step 2: Identify perspectives
                if (identifyPerspectives) {
                    log.info("Step 2: Identifying source perspectives")
                    overviewTask.add("✅ Identifying perspectives and sources...\n".renderMarkdown(true))

                    val perspectivesTask = tabs.newTask("Perspectives")

                    perspectivesTask.add(
                      buildString {
                        appendLine("# Source Perspectives")
                        appendLine()
                        appendLine("**Status:** Analyzing viewpoints and sources...")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    transcript?.write("## Step 2: Source Perspectives\n\n".toByteArray())

                    val perspectiveAgent = ParsedAgent(
                        resultClass = SourcePerspectives::class.java,
                        prompt = """
You are an expert journalist skilled at identifying diverse perspectives. Analyze the different viewpoints on this story.

Story Topic: $storyTopic

Journalism Elements:
${journalismElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

Identify 4-6 key sources or stakeholder perspectives, including:
- Primary sources directly involved
- Expert opinions
- Affected parties
- Opposing viewpoints
- Official statements

For each source, provide:
- Name/identification
- Role or relationship to the story
- Their perspective or position
- Key quotes or statements (if available)
- Potential biases or interests
- Credibility assessment

Ensure balanced representation of different viewpoints.
          """.trimIndent(),
                        model = api,
                        temperature = 0.5,
                        parsingChatter = defaultFast
                    )

                    val perspectives = perspectiveAgent.answer(listOf("Identify perspectives")).obj.sources
                    log.debug("Identified ${perspectives.size} perspectives")

                    val perspectivesContent = buildString {
                        appendLine("## Source Perspectives")
                        appendLine()
                        perspectives.forEach { source ->
                            appendLine("### ${source.source_name}")
                            appendLine()
                            appendLine("**Role:** ${source.role}")
                            appendLine()
                            appendLine("**Perspective:** ${source.perspective}")
                            appendLine()
                            if (source.key_quotes.isNotEmpty()) {
                                appendLine("**Key Quotes:**")
                                source.key_quotes.forEach { quote ->
                                    appendLine("> \"$quote\"")
                                }
                                appendLine()
                            }
                            appendLine("**Potential Bias:** ${source.potential_bias}")
                            appendLine()
                            appendLine("**Credibility:** ${source.credibility_assessment}")
                            appendLine()
                            appendLine("---")
                            appendLine()
                        }
                        appendLine("**Status:** ✅ Complete")
                    }

                    transcript?.write(
                        """
                        <details>
                        <summary>Source Perspectives (${perspectives.size})</summary>
                        
                        ${perspectives.joinToString("\n") { "- **${it.source_name}** (${it.role}): ${it.perspective}" }}
                        </details>
                        
                    """.trimIndent().toByteArray()
                    )

                    perspectivesTask.add(perspectivesContent.renderMarkdown(true))

                    resultBuilder.append("## Key Perspectives\n")
                    perspectives.take(3).forEach { source ->
                        resultBuilder.append("- **${source.source_name}** (${source.role}): ${
                            source.perspective.truncateForDisplay(
                                maxDescriptionLength
                            )
                        }\n")

                    }
                    resultBuilder.append("\n")

                    overviewTask.add("✅ Perspectives identified (${perspectives.size} sources)\n".renderMarkdown(true))
                }

                // Step 3: Analyze context
                if (analyzeContext) {
                    log.info("Step 3: Analyzing context and background")
                    overviewTask.add("✅ Analyzing context and background...\n".renderMarkdown(true))

                    val contextTask = tabs.newTask("Context Analysis")

                    contextTask.add(
                      buildString {
                        appendLine("# Context Analysis")
                        appendLine()
                        appendLine("**Status:** Researching background and implications...")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    transcript?.write("## Step 3: Context Analysis\n\n".toByteArray())

                    val contextAgent = ParsedAgent(
                        resultClass = ContextAnalysis::class.java,
                        prompt = """
You are an expert journalist skilled at providing context. Analyze the broader context of this story.

Story Topic: $storyTopic

Journalism Elements:
${journalismElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

Provide comprehensive context including:
- Historical background (what led to this)
- Relevant trends or patterns
- Related events or precedents
- Broader implications (social, political, economic, etc.)
- Key stakeholders and their interests

Help readers understand why this story matters and how it fits into the bigger picture.
          """.trimIndent(),
                        model = api,
                        temperature = 0.5,
                        parsingChatter = defaultFast
                    )

                    val context = contextAgent.answer(listOf("Analyze context")).obj
                    log.debug("Context analysis complete")

                    val contextContent = buildString {
                        appendLine("## Background and Context")
                        appendLine()
                        appendLine("### Historical Background")
                        appendLine(context.historical_background)
                        appendLine()
                        appendLine("### Relevant Trends")
                        context.relevant_trends.forEach { trend ->
                            appendLine("- $trend")
                        }
                        appendLine()
                        appendLine("### Related Events")
                        context.related_events.forEach { event ->
                            appendLine("- $event")
                        }
                        appendLine()
                        appendLine("### Broader Implications")
                        context.broader_implications.forEach { implication ->
                            appendLine("- $implication")
                        }
                        appendLine()
                        appendLine("### Key Stakeholders")
                        context.key_stakeholders.forEach { stakeholder ->
                            appendLine("- $stakeholder")
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }

                    transcript?.write(
                        """
                        <details>
                        <summary>Context Analysis Summary</summary>
                        
                        ${context.historical_background}
                        </details>
                        
                    """.trimIndent().toByteArray()
                    )

                    contextTask.add(contextContent.renderMarkdown(true))

                    resultBuilder.append("## Context\n")
                    resultBuilder.append("${context.historical_background.truncateForDisplay(200)}\n\n")

                    overviewTask.add("✅ Context analyzed\n".renderMarkdown(true))
                }

                // Step 4: Identify biases
                if (identifyBiases) {
                    log.info("Step 4: Identifying biases and balance issues")
                    overviewTask.add("✅ Checking for biases and balance...\n".renderMarkdown(true))

                    val biasTask = tabs.newTask("Bias Analysis")

                    biasTask.add(
                      buildString {
                        appendLine("# Bias Analysis")
                        appendLine()
                        appendLine("**Status:** Examining potential biases...")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    transcript?.write("## Step 4: Bias Analysis\n\n".toByteArray())

                    val biasAgent = ParsedAgent(
                        resultClass = BiasAnalysis::class.java,
                        prompt = """
You are an expert media critic and journalism ethics specialist. Analyze potential biases in this story coverage.

Story Topic: $storyTopic

Journalism Elements:
${journalismElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

Examine:
- Potential biases in framing or language
- Conflicts of interest (sources, reporters, outlets)
- Missing or underrepresented voices
- Framing issues (what's emphasized vs. downplayed)
- Overall balance assessment

Be thorough but fair. Distinguish between legitimate perspective and problematic bias.
          """.trimIndent(),
                        model = api,
                        temperature = 0.4,
                        parsingChatter = defaultFast
                    )

                    val biasAnalysis = biasAgent.answer(listOf("Analyze biases")).obj
                    log.debug("Bias analysis complete")

                    val biasContent = buildString {
                        appendLine("## Bias and Balance Assessment")
                        appendLine()
                        if (biasAnalysis.potential_biases.isNotEmpty()) {
                            appendLine("### Potential Biases")
                            biasAnalysis.potential_biases.forEach { bias ->
                                appendLine("- $bias")
                            }
                            appendLine()
                        }
                        if (biasAnalysis.conflicts_of_interest.isNotEmpty()) {
                            appendLine("### Conflicts of Interest")
                            biasAnalysis.conflicts_of_interest.forEach { conflict ->
                                appendLine("- $conflict")
                            }
                            appendLine()
                        }
                        if (biasAnalysis.missing_voices.isNotEmpty()) {
                            appendLine("### Missing Voices")
                            biasAnalysis.missing_voices.forEach { voice ->
                                appendLine("- $voice")
                            }
                            appendLine()
                        }
                        if (biasAnalysis.framing_issues.isNotEmpty()) {
                            appendLine("### Framing Issues")
                            biasAnalysis.framing_issues.forEach { issue ->
                                appendLine("- $issue")
                            }
                            appendLine()
                        }
                        appendLine("### Overall Balance Assessment")
                        appendLine(biasAnalysis.balance_assessment)
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }

                    transcript?.write(
                        """
                        <details>
                        <summary>Bias and Balance Assessment</summary>
                        
                        ${biasAnalysis.balance_assessment}
                        </details>
                        
                    """.trimIndent().toByteArray()
                    )

                    biasTask.add(biasContent.renderMarkdown(true))

                    resultBuilder.append("## Balance Assessment\n")
                    resultBuilder.append("${biasAnalysis.balance_assessment.truncateForDisplay(200)}\n\n")

                    overviewTask.add("✅ Bias analysis complete\n".renderMarkdown(true))
                }

                // Step 5: Explore alternative angles
                if (alternativeAngles > 0) {
                    log.info("Step 5: Exploring alternative story angles")
                    overviewTask.add("✅ Exploring alternative angles...\n".renderMarkdown(true))

                    val anglesTask = tabs.newTask("Story Angles")

                    anglesTask.add(
                      buildString {
                        appendLine("# Alternative Story Angles")
                        appendLine()
                        appendLine("**Status:** Identifying different approaches...")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    transcript?.write("## Step 5: Alternative Story Angles\n\n".toByteArray())

                    val anglesAgent = ParsedAgent(
                        resultClass = StoryAngles::class.java,
                        prompt = """
You are a creative news editor. Identify $alternativeAngles different angles for covering this story.

Story Topic: $storyTopic

Journalism Elements:
${journalismElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

For each angle, provide:
- Compelling title/headline
- Focus (what aspect to emphasize)
- Target audience
- Key questions to answer
- Unique value (what makes this angle distinctive)
- Newsworthiness score (0-1)

Consider angles that:
- Appeal to different audiences
- Emphasize different aspects (human interest, policy, impact, etc.)
- Offer fresh perspectives
- Have strong news value
          """.trimIndent(),
                        model = api,
                        temperature = 0.7,
                        parsingChatter = defaultFast
                    )

                    val angles = anglesAgent.answer(listOf("Explore angles")).obj.angles
                    log.debug("Identified ${angles.size} story angles")

                    val anglesContent = buildString {
                        appendLine("## Story Angles")
                        appendLine()
                        angles.sortedByDescending { it.newsworthiness_score }.forEachIndexed { index, angle ->
                            appendLine("### ${index + 1}. ${angle.angle_title}")
                            appendLine()
                            appendLine(
                                "**Newsworthiness:** ${
                                    String.format(
                                        "%.1f%%",
                                        angle.newsworthiness_score * 100
                                    )
                                }"
                            )
                            appendLine()
                            appendLine("**Focus:** ${angle.focus}")
                            appendLine()
                            appendLine("**Target Audience:** ${angle.target_audience}")
                            appendLine()
                            appendLine("**Key Questions:**")
                            angle.key_questions.forEach { question ->
                                appendLine("- $question")
                            }
                            appendLine()
                            appendLine("**Unique Value:** ${angle.unique_value}")
                            appendLine()
                            if (index < angles.size - 1) {
                                appendLine("---")
                                appendLine()
                            }
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }

                    transcript?.write(
                        """
                        <details>
                        <summary>Alternative Story Angles (${angles.size})</summary>
                        
                        ${angles.joinToString("\n") { "- **${it.angle_title}**: ${it.focus}" }}
                        </details>
                        
                    """.trimIndent().toByteArray()
                    )

                    anglesTask.add(anglesContent.renderMarkdown(true))

                    resultBuilder.append("## Story Angles\n")
                    angles.sortedByDescending { it.newsworthiness_score }.take(2).forEach { angle ->
                        resultBuilder.append("- **${angle.angle_title}**: ${
                            angle.focus.truncateForDisplay(
                                maxDescriptionLength
                            )
                        }\n")

                    }
                    resultBuilder.append("\n")

                    overviewTask.add("✅ Story angles explored (${angles.size} angles)\n".renderMarkdown(true))
                }

                // Step 6: Find information gaps
                if (findGaps) {
                    log.info("Step 6: Identifying information gaps")
                    overviewTask.add("✅ Identifying information gaps...\n".renderMarkdown(true))

                    val gapsTask = tabs.newTask("Information Gaps")

                    gapsTask.add(
                      buildString {
                        appendLine("# Information Gaps")
                        appendLine()
                        appendLine("**Status:** Finding unanswered questions...")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    transcript?.write("## Step 6: Information Gaps\n\n".toByteArray())

                    val gapsAgent = ParsedAgent(
                        resultClass = InformationGaps::class.java,
                        prompt = """
You are an investigative journalist. Identify missing information and unanswered questions in this story.

Story Topic: $storyTopic

Journalism Elements:
${journalismElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

Identify 5-8 key information gaps, including:
- Unanswered questions
- Missing data or evidence
- Unclear causation or timeline
- Unverified claims needing follow-up
- Perspectives not yet represented

For each gap, provide:
- The specific question or missing information
- Importance level (critical, important, minor)
- Potential sources to fill the gap
- Suggested research approach

Prioritize gaps that are most important for understanding the full story.
          """.trimIndent(),
                        model = api,
                        temperature = 0.5,
                        parsingChatter = defaultFast
                    )

                    val gaps = gapsAgent.answer(listOf("Find gaps")).obj.gaps
                    log.debug("Found ${gaps.size} information gaps")

                    val gapsContent = buildString {
                        if (gaps.isEmpty()) {
                            appendLine("## ✅ No Significant Information Gaps")
                            appendLine()
                            appendLine("The available information appears comprehensive for the current story scope.")
                        } else {
                            appendLine("## Identified Information Gaps")
                            appendLine()
                            gaps.sortedBy { gap ->
                                when (gap.importance.lowercase()) {
                                    "critical" -> 0
                                    "important" -> 1
                                    else -> 2
                                }
                            }.forEachIndexed { index, gap ->
                                val importanceIcon = when (gap.importance.lowercase()) {
                                    "critical" -> "🔴"
                                    "important" -> "🟡"
                                    else -> "🟢"
                                }
                                appendLine("### $importanceIcon ${index + 1}. ${gap.question}")
                                appendLine()
                                appendLine("**Importance:** ${gap.importance}")
                                appendLine()
                                if (gap.potential_sources.isNotEmpty()) {
                                    appendLine("**Potential Sources:**")
                                    gap.potential_sources.forEach { source ->
                                        appendLine("- $source")
                                    }
                                    appendLine()
                                }
                                appendLine("**Research Approach:** ${gap.research_approach}")
                                appendLine()
                                if (index < gaps.size - 1) {
                                    appendLine("---")
                                    appendLine()
                                }
                            }
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }

                    transcript?.write(
                        """
                        <details>
                        <summary>Information Gaps (${gaps.size})</summary>
                        
                        ${if (gaps.isEmpty()) "No significant gaps identified." else gaps.joinToString("\n") { "- **${it.importance.uppercase()}**: ${it.question}" }}
                        </details>
                        
                    """.trimIndent().toByteArray()
                    )

                    gapsTask.add(gapsContent.renderMarkdown(true))

                    if (gaps.isNotEmpty()) {
                        resultBuilder.append("## Information Gaps\n")
                        gaps.take(3).forEach { gap ->
                            resultBuilder.append(
                                "- ${gap.importance.uppercase()}: ${
                                    gap.question.truncateForDisplay(
                                        maxDescriptionLength
                                    )
                                }\n"
                            )
                        }
                        resultBuilder.append("\n")
                    }

                    overviewTask.add("✅ Information gaps identified (${gaps.size} found)\n".renderMarkdown(true))
                }

                // Step 7: Generate editorial synthesis
                log.info("Step 7: Generating editorial synthesis")
                overviewTask.add("✅ Generating editorial synthesis...\n".renderMarkdown(true))

                val synthesisTask = tabs.newTask("Editorial Synthesis")

                synthesisTask.add(
                  buildString {
                    appendLine("# Editorial Synthesis")
                    appendLine()
                    appendLine("**Status:** Synthesizing findings...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                transcript?.write("## Step 7: Editorial Synthesis\n\n".toByteArray())

                val synthesisAgent = ChatAgent(
                    prompt = """
You are a senior news editor. Provide an editorial synthesis of this journalism investigation.

Story Topic: $storyTopic

Summarize:
1. The core story and its significance
2. Key verified facts and findings
3. Most important perspectives and voices
4. Critical context readers need
5. Remaining questions and next steps
6. Recommended editorial approach
7. Public interest assessment

Be concise, authoritative, and focused on journalistic value.
        """.trimIndent(),
                    model = api,
                    temperature = 0.5
                )

                val synthesis = synthesisAgent.answer(listOf("Generate synthesis"))
                log.debug("Synthesis generated: ${synthesis.length} characters")

                transcript?.write(
                    """
                    <details>
                    <summary>Editorial Synthesis Content</summary>
                    
                    $synthesis
                    </details>
                """.trimIndent().toByteArray()
                )


                synthesisTask.add(
                  buildString {
                    appendLine("## Editorial Assessment")
                    appendLine()
                    appendLine(synthesis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                  }.renderMarkdown(true)
                )

                resultBuilder.append("## Editorial Synthesis\n")
                resultBuilder.append(synthesis)
                resultBuilder.append("\n\n")

                // Final statistics
                val totalTime = System.currentTimeMillis() - startTime
                resultBuilder.append("---\n\n")
                resultBuilder.append("**Investigation Time:** ${totalTime / 1000}s | ")
                resultBuilder.append("**Story:** $storyTopic\n")

                transcript?.write(
                    """
                    ---
                    **Investigation completed in ${totalTime / 1000.0}s**
                    **Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
                    
                """.trimIndent().toByteArray()
                )

                overviewTask.add(
                  buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Investigation Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine(
                      "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                      }"
                    )
                  }.renderMarkdown(true)
                )

                val finalResult = resultBuilder.toString()
                log.info("JournalismReasoningTask completed: total_time=${totalTime}ms, output_size=${finalResult.length} chars")


                val reportPath = "journalism_analysis_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
                val reportUrl = task.saveFile(reportPath, finalResult.toByteArray(StandardCharsets.UTF_8))

                val uiMessage = """
                    ✅ Journalism investigation complete in ${totalTime / 1000}s
                    
                    📄 Full analysis: <a href='$reportUrl' target='_blank'>Download Report</a>
                """.trimIndent()

                val llmSummary = """
                    ## Journalism Investigation Complete: $storyTopic
                    - **Total Time:** ${totalTime / 1000.0}s
                    - **Report Saved to:** `$reportPath`
                    
                    ### Editorial Synthesis
                    ${synthesis.truncateForDisplay(500)}
                """.trimIndent()

                task.safeComplete(uiMessage, log)
                resultFn(llmSummary)

            } catch (e: Exception) {
                // Triple Log Rule
                log.error("Error during journalism reasoning for story: '$storyTopic'", e)
                task.error(e)


                transcript?.write(
                    """
                    <details>
                    <summary>Stack Trace</summary>
                    
                    ```
                    ${e.stackTraceToString()}
                    ```
                    </details>
                """.trimIndent().toByteArray()
                )

                resultFn("Error in Journalism Investigation: ${e.message}")
            } finally {
                transcript?.close()
            }
        }
    }

}