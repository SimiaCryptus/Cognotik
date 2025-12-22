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
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class EmailCampaignTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: EmailCampaignTaskExecutionConfigData?
) : AbstractTask<EmailCampaignTask.EmailCampaignTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class EmailCampaignTaskExecutionConfigData(
        @Description("The goal or purpose of the email campaign")
        val campaign_goal: String? = null,

        @Description("The product, service, or topic being promoted")
        val subject_matter: String? = null,

        @Description("Target audience description (demographics, role, pain points)")
        val target_audience: String = "general audience",

        @Description("Campaign type (e.g., 'welcome_series', 'nurture', 'sales', 're_engagement', 'newsletter', 'event_promotion')")
        val campaign_type: String = "nurture",

        @Description("Number of emails in the sequence")
        val num_emails: Int = 3,

        @Description("Recommended days between emails (e.g., [1, 3, 7] for day 1, day 4, day 11)")
        val send_intervals: List<Int>? = null,

        @Description("Brand voice and tone (e.g., 'professional', 'friendly', 'casual', 'authoritative', 'playful')")
        val brand_voice: String = "professional",

        @Description("Primary call-to-action (e.g., 'schedule_demo', 'download_resource', 'make_purchase', 'register_event')")
        val primary_cta: String = "learn_more",

        @Description("Whether to generate A/B test variants for subject lines")
        val generate_subject_variants: Boolean = true,

        @Description("Number of subject line variants per email (if enabled)")
        val subject_variants_count: Int = 3,

        @Description("Whether to include personalization tokens (e.g., {{first_name}}, {{company}})")
        val include_personalization: Boolean = true,

        @Description("Whether to include preview text (the snippet shown in inbox)")
        val include_preview_text: Boolean = true,

        @Description("Whether to include emoji in subject lines")
        val use_emoji: Boolean = false,

        @Description("Maximum subject line length in characters")
        val max_subject_length: Int = 60,

        @Description("Target email body length (MUST BE on of: 'short' <150 words, 'medium' 150-300, 'long' >300)")
        val body_length: String = "medium",

        @Description("Whether to include PS (postscript) sections")
        val include_ps: Boolean = true,

        @Description("Number of revision passes for quality improvement")
        val revision_passes: Int = 1,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as brand context for the task")
        val input_files: List<String>? = null,


        @Description("Related files or brand guidelines to incorporate")
        val related_files: List<String>? = null,

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = EmailCampaign.name,
        task_description = task_description ?: "Generate email campaign for: '$campaign_goal'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (campaign_goal.isNullOrBlank()) {
                return "campaign_goal must not be null or blank"
            }
            if (subject_matter.isNullOrBlank()) {
                return "subject_matter must not be null or blank"
            }
            if (num_emails < 1 || num_emails > 10) {
                return "num_emails must be between 1 and 10, got: $num_emails"
            }
            if (subject_variants_count < 1 || subject_variants_count > 5) {
                return "subject_variants_count must be between 1 and 5, got: $subject_variants_count"
            }
            if (max_subject_length < 20 || max_subject_length > 100) {
                return "max_subject_length must be between 20 and 100, got: $max_subject_length"
            }
            if (revision_passes < 0 || revision_passes > 5) {
                return "revision_passes must be between 0 and 5, got: $revision_passes"
            }
            if (campaign_type.isBlank()) {
                return "campaign_type must not be blank"
            }
            val validBodyLengths = setOf("short", "medium", "long")
            if (body_length.lowercase() !in validBodyLengths) {
                return "body_length must be one of: ${validBodyLengths.joinToString(", ")}, got: $body_length"
            }
            send_intervals?.let { intervals ->
                if (intervals.size != num_emails - 1) {
                    return "send_intervals must have ${num_emails - 1} values (one less than num_emails), got: ${intervals.size}"
                }
                if (intervals.any { it < 0 }) {
                    return "send_intervals must all be non-negative"
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class CampaignStrategy(
        @Description("Overall campaign strategy and approach")
        val strategy: String = "",
        @Description("Key messages to communicate across the sequence")
        val key_messages: List<String> = emptyList(),
        @Description("Progression logic (how emails build on each other)")
        val progression_logic: String = "",
        @Description("Audience pain points to address")
        val pain_points: List<String> = emptyList(),
        @Description("Value propositions to emphasize")
        val value_propositions: List<String> = emptyList(),
        @Description("Recommended send timing")
        val timing_recommendations: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (strategy.isBlank()) return "strategy must not be blank"
            if (key_messages.isEmpty()) return "key_messages must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class EmailOutline(
        @Description("Email number in sequence")
        val email_number: Int = 1,
        @Description("Email purpose and goal")
        val purpose: String = "",
        @Description("Main message or theme")
        val main_message: String = "",
        @Description("Key points to cover")
        val key_points: List<String> = emptyList(),
        @Description("Call-to-action for this email")
        val cta: String = "",
        @Description("Emotional tone for this email")
        val emotional_tone: String = "",
        @Description("Connection to previous email (if applicable)")
        val connection_to_previous: String = "",
        @Description("Estimated word count")
        val estimated_word_count: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (email_number < 1) return "email_number must be positive"
            if (purpose.isBlank()) return "purpose must not be blank"
            if (main_message.isBlank()) return "main_message must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class SubjectLineVariants(
        @Description("List of subject line options")
        val variants: List<SubjectLine> = emptyList()
    ) : ValidatedObject

    data class SubjectLine(
        @Description("The subject line text")
        val text: String = "",
        @Description("Approach or technique used (e.g., 'curiosity', 'urgency', 'benefit-focused')")
        val approach: String = "",
        @Description("Character count")
        val character_count: Int = 0,
        @Description("Whether it includes personalization tokens")
        val has_personalization: Boolean = false
    ) : ValidatedObject {
        override fun validate(): String? {
            if (text.isBlank()) return "text must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class EmailContent(
        @Description("Email number in sequence")
        val email_number: Int = 1,
        @Description("Selected subject line")
        val subject_line: String = "",
        @Description("Preview text (inbox snippet)")
        val preview_text: String = "",
        @Description("Email body content")
        val body: String = "",
        @Description("Call-to-action text")
        val cta_text: String = "",
        @Description("CTA button/link text")
        val cta_button: String = "",
        @Description("PS section (if applicable)")
        val ps_section: String = "",
        @Description("Word count")
        val word_count: Int = 0,
        @Description("Personalization tokens used")
        val personalization_tokens: List<String> = emptyList(),
        @Description("Key persuasive elements")
        val persuasive_elements: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (email_number < 1) return "email_number must be positive"
            if (subject_line.isBlank()) return "subject_line must not be blank"
            if (body.isBlank()) return "body must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
EmailCampaign - Generate complete email sequences for marketing, sales, or outreach
  ** Specify the campaign goal and subject matter
  ** Define target audience and campaign type
  ** Set number of emails and send intervals
  ** Configure brand voice and primary CTA
  ** Enable A/B test variants for subject lines
  ** Include personalization tokens and preview text
  ** Control body length and formatting options
  ** Performs strategy planning, outlining, and email generation
  ** Produces complete, ready-to-use email sequence
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
        log.info("Starting EmailCampaignTask for goal: '${executionConfig?.campaign_goal}'")
        val transcript = transcript(task)


        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            resultFn("CONFIGURATION ERROR: $validationError")
            transcript?.close()
            return
        }

        val campaignGoal = executionConfig?.campaign_goal
        if (campaignGoal.isNullOrBlank()) {
            log.error("No campaign goal specified")
            task.safeComplete("CONFIGURATION ERROR: No campaign goal specified", log)
            resultFn("CONFIGURATION ERROR: No campaign goal specified")
            transcript?.close()
            return
        }

        val api = defaultChatter ?: run {
            transcript?.close()
            return
        }

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = task.ui.newTask(false)
        tabs["Overview"] = overviewTask.placeholder

        val overviewContent = buildString {
            appendLine("# Email Campaign Generation")
            appendLine()
            appendLine(
                "**Generated:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }"
            )
            appendLine()
            appendLine("**Campaign Goal:** $campaignGoal")
            appendLine()
            appendLine("**Subject Matter:** ${executionConfig.subject_matter}")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Campaign Type: ${executionConfig.campaign_type}")
            appendLine("- Target Audience: ${executionConfig.target_audience}")
            appendLine("- Number of Emails: ${executionConfig.num_emails}")
            appendLine("- Brand Voice: ${executionConfig.brand_voice}")
            appendLine("- Primary CTA: ${executionConfig.primary_cta}")
            appendLine("- Body Length: ${executionConfig.body_length}")
            appendLine("- Subject Variants: ${if (executionConfig.generate_subject_variants) "${executionConfig.subject_variants_count} per email" else "Single per email"}")
            appendLine("- Personalization: ${if (executionConfig.include_personalization) "✓" else "✗"}")
            appendLine("- Preview Text: ${if (executionConfig.include_preview_text) "✓" else "✗"}")
            appendLine("- Emoji: ${if (executionConfig.use_emoji) "✓" else "✗"}")
            appendLine()
            if (executionConfig.send_intervals != null) {
                appendLine("**Send Schedule:**")
                appendLine("- Email 1: Day 0 (immediate)")
                executionConfig.send_intervals.forEachIndexed { index, interval ->
                    val cumulativeDays = executionConfig.send_intervals.take(index + 1).sum()
                    appendLine("- Email ${index + 2}: Day $cumulativeDays (+$interval days)")
                }
                appendLine()
            }
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Campaign Strategy")
            appendLine("*Developing overall campaign approach...*")
        }
        overviewTask.add(overviewContent.renderMarkdown)
        transcript?.write(overviewContent.toByteArray(Charsets.UTF_8))
        task.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Email Campaign: $campaignGoal\n\n")

        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState) ?: ""
            val contextFiles = getContextFiles()

            if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                val contextTask = task.ui.newTask(false)
                tabs["Brand Context"] = contextTask.placeholder
                contextTask.add(
                    buildString {
                        appendLine("# Brand & Campaign Context")
                        appendLine()
                        if (priorContext.isNotBlank()) {
                            appendLine("## Prior Context")
                            appendLine(priorContext.truncateForDisplay(2000))
                            appendLine()
                        }
                        if (contextFiles.isNotBlank()) {
                            appendLine("## Brand Guidelines")
                            appendLine(contextFiles.truncateForDisplay(2000))
                        }
                    }.renderMarkdown
                )
                transcript?.write(contextTask.placeholder.toString().toByteArray(Charsets.UTF_8))
                task.update()
            }

            // Phase 1: Develop campaign strategy
            log.info("Phase 1: Developing campaign strategy")
            val strategyTask = task.ui.newTask(false)
            tabs["Strategy"] = strategyTask.placeholder

            strategyTask.add(
                buildString {
                    appendLine("# Campaign Strategy")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** Analyzing audience and developing approach...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val targetWordCount = when (executionConfig.body_length.lowercase()) {
                "short" -> 125
                "medium" -> 225
                "long" -> 400
                else -> 225
            }

            val strategyAgent = ParsedAgent(
                resultClass = CampaignStrategy::class.java,
                prompt = """
You are an expert email marketing strategist. Develop a comprehensive strategy for this email campaign.

Campaign Goal: $campaignGoal
Subject Matter: ${executionConfig.subject_matter}
Campaign Type: ${executionConfig.campaign_type}
Target Audience: ${executionConfig.target_audience}
Number of Emails: ${executionConfig.num_emails}
Brand Voice: ${executionConfig.brand_voice}
Primary CTA: ${executionConfig.primary_cta}

${if (priorContext.isNotBlank()) "Brand Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Brand Guidelines:\n${contextFiles.truncateForDisplay(2000)}\n" else ""}

Create a strategy that includes:
1. Overall approach and positioning
2. 3-5 key messages to communicate across the sequence
3. How emails will build on each other (progression logic)
4. Audience pain points to address
5. Value propositions to emphasize
6. Timing recommendations for maximum engagement

Consider:
- The ${executionConfig.campaign_type} campaign type requires specific pacing and messaging
- The ${executionConfig.target_audience} has specific needs and preferences
- Each email should move the recipient closer to the ${executionConfig.primary_cta}
- Maintain ${executionConfig.brand_voice} voice throughout
- Build trust and value before asking for action
          """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = parsingChatter
            )

            val strategy = strategyAgent.answer(listOf("Develop strategy")).obj
            log.info("Campaign strategy developed: ${strategy.key_messages.size} key messages")

            val strategyContent = buildString {
                appendLine("## Campaign Approach")
                appendLine()
                appendLine(strategy.strategy)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Key Messages")
                strategy.key_messages.forEachIndexed { index, message ->
                    appendLine("${index + 1}. $message")
                }
                appendLine()
                appendLine("### Progression Logic")
                appendLine(strategy.progression_logic)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Audience Pain Points")
                strategy.pain_points.forEach { pain ->
                    appendLine("- $pain")
                }
                appendLine()
                appendLine("### Value Propositions")
                strategy.value_propositions.forEach { value ->
                    appendLine("- $value")
                }
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Timing Recommendations")
                appendLine(strategy.timing_recommendations)
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            strategyTask.add(strategyContent.renderMarkdown)
            transcript?.write(("\n\n" + strategyContent).toByteArray(Charsets.UTF_8))
            task.update()

            overviewTask.add("✅ Phase 1 Complete: Strategy developed\n".renderMarkdown)
            overviewTask.add("\n### Phase 2: Email Sequence Outline\n*Creating detailed outline for each email...*\n".renderMarkdown)
            task.update()

            // Phase 2: Create email outlines
            log.info("Phase 2: Creating email sequence outline")
            val outlineTask = task.ui.newTask(false)
            tabs["Sequence Outline"] = outlineTask.placeholder

            outlineTask.add(
                buildString {
                    appendLine("# Email Sequence Outline")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** Planning ${executionConfig.num_emails} emails...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val outlines = mutableListOf<EmailOutline>()
            for (emailNum in 1..executionConfig.num_emails) {
                log.debug("Creating outline for email $emailNum")

                val previousOutlines = if (outlines.isNotEmpty()) {
                    buildString {
                        appendLine("Previous Emails:")
                        outlines.forEach { prev ->
                            appendLine("Email ${prev.email_number}: ${prev.main_message}")
                        }
                    }
                } else ""

                val outlineAgent = ParsedAgent(
                    resultClass = EmailOutline::class.java,
                    prompt = """
You are an email marketing expert. Create a detailed outline for Email $emailNum of ${executionConfig.num_emails}.

Campaign Strategy:
${strategy.strategy}

Key Messages: ${strategy.key_messages.joinToString("; ")}
Progression Logic: ${strategy.progression_logic}

$previousOutlines

For Email $emailNum, specify:
- Purpose and goal of this specific email
- Main message or theme
- 3-5 key points to cover
- Specific call-to-action
- Emotional tone (e.g., 'welcoming', 'educational', 'urgent', 'supportive')
- How it connects to the previous email (if applicable)
- Estimated word count (~$targetWordCount words)

Email $emailNum should:
${
                        when (emailNum) {
                            1 -> "- Establish connection and set expectations\n- Introduce the value proposition\n- Build initial trust"
                            executionConfig.num_emails -> "- Reinforce key benefits\n- Create urgency or final push\n- Make the primary CTA compelling"
                            else -> "- Build on previous email's message\n- Deepen engagement\n- Move closer to conversion"
                        }
                    }

Maintain ${executionConfig.brand_voice} voice and address ${executionConfig.target_audience}.
          """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = parsingChatter
                )

                val outline = outlineAgent.answer(listOf("Create outline")).obj
                outlines.add(outline)
            }

            val outlineContent = buildString {
                appendLine("## Email Sequence Plan")
                appendLine()
                outlines.forEach { outline ->
                    appendLine("### Email ${outline.email_number}: ${outline.main_message}")
                    appendLine()
                    appendLine("**Purpose:** ${outline.purpose}")
                    appendLine()
                    appendLine("**Emotional Tone:** ${outline.emotional_tone}")
                    appendLine()
                    appendLine("**Key Points:**")
                    outline.key_points.forEach { point ->
                        appendLine("- $point")
                    }
                    appendLine()
                    appendLine("**Call-to-Action:** ${outline.cta}")
                    appendLine()
                    if (outline.connection_to_previous.isNotBlank()) {
                        appendLine("**Connection to Previous:** ${outline.connection_to_previous}")
                        appendLine()
                    }
                    appendLine("**Est. Words:** ${outline.estimated_word_count}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }
            outlineTask.add(outlineContent.renderMarkdown)
            transcript?.write(("\n\n" + outlineContent).toByteArray(Charsets.UTF_8))
            task.update()

            overviewTask.add("✅ Phase 2 Complete: ${outlines.size} emails outlined\n".renderMarkdown)
            overviewTask.add("\n### Phase 3: Email Generation\n*Writing emails with subject lines...*\n".renderMarkdown)
            task.update()

            // Phase 3: Generate each email
            log.info("Phase 3: Generating emails")
            val generatedEmails = mutableListOf<EmailContent>()
            val allSubjectVariants = mutableMapOf<Int, List<SubjectLine>>()

            outlines.forEach { outline ->
                log.info("Generating email ${outline.email_number}/${executionConfig.num_emails}")

                overviewTask.add("- Email ${outline.email_number}: ${outline.main_message.truncateForDisplay(50)} ".renderMarkdown)
                task.update()

                val emailTask = task.ui.newTask(false)
                tabs["Email ${outline.email_number}"] = emailTask.placeholder

                emailTask.add(
                    buildString {
                        appendLine("# Email ${outline.email_number}")
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** Generating content...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                // Generate subject line variants
                val subjectVariants = if (executionConfig.generate_subject_variants) {
                    log.debug("Generating ${executionConfig.subject_variants_count} subject line variants")

                    val subjectAgent = ParsedAgent(
                        resultClass = SubjectLineVariants::class.java,
                        prompt = """
You are an expert at writing compelling email subject lines. Generate ${executionConfig.subject_variants_count} different subject line options.

Email Purpose: ${outline.purpose}
Main Message: ${outline.main_message}
Target Audience: ${executionConfig.target_audience}
Brand Voice: ${executionConfig.brand_voice}
Max Length: ${executionConfig.max_subject_length} characters

Create ${executionConfig.subject_variants_count} variants using different approaches:
- Curiosity-driven (make them want to know more)
- Benefit-focused (highlight the value)
- Urgency/scarcity (create FOMO)
- Question-based (engage their thinking)
- Direct/clear (straightforward value)

Requirements:
- Each must be under ${executionConfig.max_subject_length} characters
- Match ${executionConfig.brand_voice} voice
- Be specific and relevant to ${outline.main_message}
${if (executionConfig.include_personalization) "- Include personalization tokens like {{first_name}} where appropriate" else ""}
${if (executionConfig.use_emoji) "- Consider using relevant emoji (but don't overdo it)" else "- Do NOT use emoji"}
- Avoid spam trigger words (FREE, !!!, ALL CAPS)
- Make each variant distinctly different in approach

For each variant, specify the approach used and character count.
            """.trimIndent(),
                        model = api,
                        temperature = 0.8,
                        parsingChatter = parsingChatter
                    )

                    subjectAgent.answer(listOf("Generate subject lines")).obj.variants
                } else {
                    // Generate single subject line
                    val subjectAgent = ParsedAgent(
                        resultClass = SubjectLineVariants::class.java,
                        prompt = """
You are an expert at writing compelling email subject lines. Generate 1 subject line.

Email Purpose: ${outline.purpose}
Main Message: ${outline.main_message}
Target Audience: ${executionConfig.target_audience}
Brand Voice: ${executionConfig.brand_voice}
Max Length: ${executionConfig.max_subject_length} characters

Create a subject line that:
- Is under ${executionConfig.max_subject_length} characters
- Matches ${executionConfig.brand_voice} voice
- Is specific and relevant to ${outline.main_message}
${if (executionConfig.include_personalization) "- Includes personalization tokens like {{first_name}} where appropriate" else ""}
${if (executionConfig.use_emoji) "- Uses relevant emoji if appropriate" else "- Does NOT use emoji"}
- Avoids spam trigger words
            """.trimIndent(),
                        model = api,
                        temperature = 0.7,
                        parsingChatter = parsingChatter
                    )

                    subjectAgent.answer(listOf("Generate subject line")).obj.variants
                }

                allSubjectVariants[outline.email_number] = subjectVariants
                log.debug("Generated ${subjectVariants.size} subject line variants")

                // Generate email body
                val previousContext = if (generatedEmails.isNotEmpty()) {
                    buildString {
                        appendLine("Previous Email Context:")
                        val lastEmail = generatedEmails.last()
                        appendLine("Email ${lastEmail.email_number} Subject: ${lastEmail.subject_line}")
                        appendLine("Key CTA: ${lastEmail.cta_text}")
                        appendLine("Ending: ${lastEmail.body.takeLast(200)}")
                    }
                } else {
                    "This is the first email in the sequence."
                }

                val emailAgent = ParsedAgent(
                    resultClass = EmailContent::class.java,
                    prompt = """
You are an expert email copywriter. Write Email ${outline.email_number} of the campaign.

Campaign Goal: $campaignGoal
Subject Matter: ${executionConfig.subject_matter}
Target Audience: ${executionConfig.target_audience}
Brand Voice: ${executionConfig.brand_voice}

Email Outline:
Purpose: ${outline.purpose}
Main Message: ${outline.main_message}
Key Points: ${outline.key_points.joinToString("; ")}
CTA: ${outline.cta}
Emotional Tone: ${outline.emotional_tone}
Target Words: ${outline.estimated_word_count}

Selected Subject Line: ${subjectVariants.first().text}

$previousContext

Write the complete email including:
1. ${if (executionConfig.include_preview_text) "Preview text (40-90 characters that appear in inbox)" else ""}
2. Email body (~${outline.estimated_word_count} words)
3. Clear call-to-action section
4. CTA button text
${if (executionConfig.include_ps) "5. PS section (optional but recommended for key point or urgency)" else ""}

Email Body Guidelines:
- Open with a ${if (outline.email_number == 1) "warm greeting" else "reference to previous email"}
- Use short paragraphs (2-3 sentences max)
- Include white space for readability
- Write in ${executionConfig.brand_voice} voice
- Address ${executionConfig.target_audience} directly
- Focus on benefits, not features
- Use "you" language (not "we")
${if (executionConfig.include_personalization) "- Include personalization tokens: {{first_name}}, {{company}}, etc." else ""}
- Build to the CTA naturally
- Make the CTA specific and action-oriented

Length: ${executionConfig.body_length} (~${outline.estimated_word_count} words)

Provide:
- The complete email body
- CTA text and button text
- Preview text
${if (executionConfig.include_ps) "- PS section" else ""}
- List of personalization tokens used
- Key persuasive elements employed
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = parsingChatter
                )

                var emailContent = emailAgent.answer(listOf("Write email")).obj.copy(
                    email_number = outline.email_number,
                    subject_line = subjectVariants.first().text
                )

                generatedEmails.add(emailContent)

                // Display email
                val emailDisplay = buildString {
                    appendLine("## Email ${outline.email_number}: ${outline.main_message}")
                    appendLine()
                    appendLine("### Subject Line Options")
                    subjectVariants.forEachIndexed { index, variant ->
                        val badge = if (index == 0) "**[SELECTED]** " else ""
                        appendLine("${index + 1}. $badge**${variant.text}** (${variant.character_count} chars)")
                        appendLine("   - *Approach: ${variant.approach}*")
                        if (variant.has_personalization) {
                            appendLine("   - *Includes personalization*")
                        }
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                    if (executionConfig.include_preview_text && emailContent.preview_text.isNotBlank()) {
                        appendLine("### Preview Text")
                        appendLine("> ${emailContent.preview_text}")
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("### Email Body")
                    appendLine()
                    appendLine(emailContent.body)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("### Call-to-Action")
                    appendLine()
                    appendLine("**CTA Text:** ${emailContent.cta_text}")
                    appendLine()
                    appendLine("**Button:** `${emailContent.cta_button}`")
                    appendLine()
                    if (executionConfig.include_ps && emailContent.ps_section.isNotBlank()) {
                        appendLine("---")
                        appendLine()
                        appendLine("### P.S.")
                        appendLine(emailContent.ps_section)
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                    appendLine("**Word Count:** ${emailContent.word_count}")
                    if (emailContent.personalization_tokens.isNotEmpty()) {
                        appendLine()
                        appendLine("**Personalization Tokens:** ${emailContent.personalization_tokens.joinToString(", ")}")
                    }
                    if (emailContent.persuasive_elements.isNotEmpty()) {
                        appendLine()
                        appendLine("**Persuasive Elements:** ${emailContent.persuasive_elements.joinToString(", ")}")
                    }
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                emailTask.add(emailDisplay.renderMarkdown)
                transcript?.write(("\n\n" + emailDisplay).toByteArray(Charsets.UTF_8))
                task.update()

                overviewTask.add("✅ (${emailContent.word_count} words)\n".renderMarkdown)
                task.update()
            }

            overviewTask.add("✅ Phase 3 Complete: All emails generated\n".renderMarkdown)

            // Phase 4: Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                overviewTask.add("\n### Phase 4: Revision\n*Refining email sequence...*\n".renderMarkdown)
                task.update()

                log.info("Phase 4: Performing ${executionConfig.revision_passes} revision pass(es)")
                val revisionTask = task.ui.newTask(false)
                tabs["Revision"] = revisionTask.placeholder

                revisionTask.add(
                    buildString {
                        appendLine("# Revision Process")
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                repeat(executionConfig.revision_passes) { passNum ->
                    log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")
                    transcript?.write(
                        ("\n\n## Revision Pass ${passNum + 1}\n✅ All ${generatedEmails.size} emails revised\n").toByteArray(
                            Charsets.UTF_8
                        )
                    )

                    generatedEmails.forEachIndexed { index, email ->
                        val revisionAgent = ChatAgent(
                            prompt = """
You are an expert email editor. Review and improve this email while maintaining its core message and structure.

Email ${email.email_number} of ${executionConfig.num_emails}
Subject: ${email.subject_line}

Current Body:
${email.body}

CTA: ${email.cta_text}
${if (email.ps_section.isNotBlank()) "PS: ${email.ps_section}" else ""}

Improve:
1. Clarity and conciseness
2. Persuasive impact
3. Flow and transitions
4. Call-to-action strength
5. Emotional resonance with ${executionConfig.target_audience}
6. ${executionConfig.brand_voice} voice consistency

Maintain:
- All key points and messages
- Word count (~${email.word_count} words)
- Personalization tokens
- Overall structure

Provide the complete revised email body only.
              """.trimIndent(),
                            model = api,
                            temperature = 0.6
                        )

                        val revisedBody = revisionAgent.answer(listOf("Revise email"))
                        generatedEmails[index] = email.copy(
                            body = revisedBody,
                            word_count = revisedBody.split("\\s+".toRegex()).size
                        )
                    }

                    revisionTask.add(
                        buildString {
                            appendLine("## Revision Pass ${passNum + 1}")
                            appendLine()
                            appendLine("✅ All ${generatedEmails.size} emails revised")
                            appendLine()
                        }.renderMarkdown
                    )
                    task.update()
                }

                overviewTask.add("✅ Phase 4 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown)
            }

            // Phase 5: Final Assembly
            overviewTask.add("\n### Phase 5: Final Assembly\n*Compiling complete campaign...*\n".renderMarkdown)
            task.update()

            log.info("Phase 5: Assembling final campaign")
            val finalTask = task.ui.newTask(false)
            tabs["Complete Campaign"] = finalTask.placeholder

            val finalCampaign = buildString {
                appendLine("# Email Campaign: $campaignGoal")
                appendLine()
                appendLine("## Campaign Overview")
                appendLine()
                appendLine("**Subject Matter:** ${executionConfig.subject_matter}")
                appendLine("**Target Audience:** ${executionConfig.target_audience}")
                appendLine("**Campaign Type:** ${executionConfig.campaign_type}")
                appendLine("**Number of Emails:** ${executionConfig.num_emails}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Campaign Strategy")
                appendLine()
                appendLine(strategy.strategy)
                appendLine()
                appendLine("**Key Messages:**")
                strategy.key_messages.forEach { message ->
                    appendLine("- $message")
                }
                appendLine()
                appendLine("---")
                appendLine()

                generatedEmails.forEachIndexed { index, email ->
                    val daysSinceStart = if (index == 0) 0 else executionConfig.send_intervals?.take(index)?.sum() ?: 0

                    appendLine("## Email ${email.email_number} - Day $daysSinceStart")
                    appendLine()

                    // Show all subject line variants
                    val variants = allSubjectVariants[email.email_number] ?: emptyList()
                    if (variants.size > 1) {
                        appendLine("### Subject Line Options (A/B Test)")
                        variants.forEachIndexed { variantIndex, variant ->
                            val badge =
                                if (variantIndex == 0) "**[A]** " else "**[B${if (variants.size > 2) "${variantIndex}" else ""}]** "
                            appendLine("$badge${variant.text}")
                            appendLine()
                        }
                    } else {
                        appendLine("**Subject:** ${email.subject_line}")
                        appendLine()
                    }

                    if (email.preview_text.isNotBlank()) {
                        appendLine("**Preview:** ${email.preview_text}")
                        appendLine()
                    }

                    appendLine("---")
                    appendLine()
                    appendLine(email.body)
                    appendLine()
                    appendLine("**${email.cta_button}**")
                    appendLine()
                    if (email.ps_section.isNotBlank()) {
                        appendLine("*P.S. ${email.ps_section}*")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }

                appendLine()
                appendLine("## Campaign Metrics")
                appendLine()
                val totalWords = generatedEmails.sumOf { it.word_count }
                val avgWords = totalWords / generatedEmails.size
                appendLine("- Total Emails: ${generatedEmails.size}")
                appendLine("- Total Word Count: $totalWords")
                appendLine("- Average Words per Email: $avgWords")
                if (executionConfig.send_intervals != null) {
                    val totalDays = executionConfig.send_intervals.sum()
                    appendLine("- Campaign Duration: $totalDays days")
                }
                appendLine()
                appendLine("## Implementation Notes")
                appendLine()
                appendLine("1. **Personalization Tokens:** Ensure your email platform supports the tokens used")
                appendLine("2. **A/B Testing:** Test subject line variants to optimize open rates")
                appendLine("3. **Timing:** Send emails at optimal times for your audience (typically 10am-2pm)")
                appendLine("4. **Mobile Optimization:** Preview on mobile devices before sending")
                appendLine("5. **Unsubscribe Link:** Always include an easy unsubscribe option")
                appendLine("6. **Tracking:** Set up UTM parameters for link tracking")
                appendLine("7. **Compliance:** Ensure compliance with CAN-SPAM, GDPR, or relevant regulations")
            }

            finalTask.add(finalCampaign.renderMarkdown)
            transcript?.write(("\n\n" + finalCampaign).toByteArray(Charsets.UTF_8))
            task.update()

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime
            val totalWords = generatedEmails.sumOf { it.word_count }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Campaign Complete")
                    appendLine()
                    appendLine("**Statistics:**")
                    appendLine("- Emails Generated: ${generatedEmails.size}")
                    appendLine("- Total Word Count: $totalWords")
                    appendLine("- Average Words per Email: ${totalWords / generatedEmails.size}")
                    appendLine("- Subject Line Variants: ${allSubjectVariants.values.sumOf { it.size }}")
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
            transcript?.write(
                ("\n\n---\n\n## Campaign Complete\n\n**Statistics:**\n- Emails: ${generatedEmails.size}\n- Words: $totalWords\n- Time: ${totalTime / 1000.0}s\n").toByteArray(
                    Charsets.UTF_8
                )
            )
            task.update()

            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# Email Campaign Summary: $campaignGoal")
                appendLine()
                appendLine("A complete email campaign of **${generatedEmails.size} emails** with **$totalWords total words** was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Campaign Type:** ${executionConfig.campaign_type}")
                appendLine("**Target Audience:** ${executionConfig.target_audience}")
                appendLine()
                appendLine("**Email Sequence:**")
                generatedEmails.forEach { email ->
                    appendLine("${email.email_number}. ${email.subject_line} (${email.word_count} words)")
                }
                appendLine()
                appendLine("> The complete campaign with all subject line variants and implementation notes is available in the Complete Campaign tab.")
            }

            log.info("EmailCampaignTask completed: emails=${generatedEmails.size}, words=$totalWords, time=${totalTime}ms")
            transcript?.close()

            val (transcriptLink, _) = Pair(
                task.linkTo("campaign_summary.md"),
                task.resolveUserFile("campaign_summary.md")
            )
            task.safeComplete(
                "Email campaign generation complete: ${generatedEmails.size} emails, $totalWords words in ${totalTime / 1000}s. Full details: <a href='$transcriptLink' target='_blank'>transcript</a>",
                log
            )
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during email campaign generation", e)
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
            transcript?.close()

            val errorOutput = buildString {
                appendLine("# Error in Email Campaign Generation")
                appendLine()
                appendLine("**Campaign Goal:** $campaignGoal")
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

    private fun getInputFileCode(): String = (executionConfig?.input_files ?: listOf())
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
                "# $relativePath\n\n```\n${file.readText()}\n```"
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
            appendLine("## Related Brand Files")
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
        val transcriptFile = this.javaClass.simpleName + "_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.complete(
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(".md")
            }.pdf' target='_blank'>pdf</a>"
        )
        return markdownTranscript
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(EmailCampaignTask::class.java)
        val EmailCampaign = TaskType(
            "EmailCampaign",
            EmailCampaignTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate complete email sequences for marketing, sales, or outreach",
            """
              Generates complete, ready-to-use email campaigns with strategic planning.
              <ul>
                <li>Develops comprehensive campaign strategy and messaging</li>
                <li>Creates detailed outline for each email in the sequence</li>
                <li>Generates A/B test variants for subject lines</li>
                <li>Writes complete email bodies with CTAs</li>
                <li>Includes personalization tokens and preview text</li>
                <li>Supports multiple campaign types (welcome, nurture, sales, etc.)</li>
                <li>Configurable brand voice, tone, and length</li>
                <li>Optional revision passes for quality improvement</li>
                <li>Provides implementation notes and best practices</li>
                <li>Ideal for marketing automation, sales outreach, and customer engagement</li>
              </ul>
            """
        )
    }
}

