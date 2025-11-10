package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sqrt

class LLMPollSimulationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: LLMPollSimulationTaskExecutionConfigData?
) : AbstractTask<LLMPollSimulationTask.LLMPollSimulationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class LLMPollSimulationTaskExecutionConfigData(
        @Description("List of survey questions to ask respondents")
        val questions: List<SurveyQuestion>? = null,
        @Description("Respondent profile templates defining demographics and characteristics")
        val respondent_profiles: List<RespondentProfile>? = null,
        @Description("Number of simulated respondents to generate per profile")
        val respondents_per_profile: Int = 10,
        @Description("Whether to include demographic information in responses")
        val include_demographics: Boolean = true,
        @Description("Demographic dimensions to track (e.g., age, gender, location)")
        val demographic_dimensions: List<String>? = listOf("age", "gender", "location", "education"),
        @Description("Whether to generate cross-tabulation analysis")
        val cross_tabulation: Boolean = true,
        @Description("Whether to perform sentiment analysis on open-ended responses")
        val sentiment_analysis: Boolean = true,
        @Description("Whether to detect response biases and patterns")
        val bias_detection: Boolean = true,
        @Description("Temperature for LLM responses (0.0-1.0, higher = more varied)")
        val temperature: Double = 0.7,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = LLMPollSimulation.name,
        task_description = "Simulate poll with ${respondent_profiles?.size ?: 0} profiles",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (questions.isNullOrEmpty()) {
                return "questions cannot be null or empty"
            }
            if (questions.any { it.text.isBlank() }) {
                return "questions cannot contain blank text"
            }
            if (respondent_profiles.isNullOrEmpty()) {
                return "respondent_profiles cannot be null or empty"
            }
            if (respondents_per_profile < 1 || respondents_per_profile > 1000) {
                return "respondents_per_profile must be between 1 and 1000, got: $respondents_per_profile"
            }
            if (temperature < 0.0 || temperature > 1.0) {
                return "temperature must be between 0.0 and 1.0, got: $temperature"
            }
            // Validate question types and options
            questions.forEach { question ->
                when (question.type) {
                    QuestionType.MULTIPLE_CHOICE, QuestionType.SINGLE_CHOICE, QuestionType.RANKING -> {
                        if (question.options.isNullOrEmpty()) {
                            return "Question '${question.id}' of type ${question.type} must have options"
                        }
                    }
                    QuestionType.LIKERT_SCALE, QuestionType.RATING -> {
                        if (question.min == null || question.max == null) {
                            return "Question '${question.id}' of type ${question.type} must have min and max validation"
                        }
                    }
                    else -> {}
                }
            }
            return null
        }
    }

    data class SurveyQuestion(
        @Description("Unique identifier for the question")
        val id: String = UUID.randomUUID().toString(),
        @Description("The question text to present to respondents")
        val text: String = "",
        @Description("Type of question (MULTIPLE_CHOICE, SINGLE_CHOICE, LIKERT_SCALE, etc.)")
        val type: QuestionType = QuestionType.OPEN_ENDED,
        @Description("Whether this question is required")
        val required: Boolean = true,
        @Description("ID of question this depends on (for conditional logic)")
        val conditional_on: String? = null,
        @Description("Available options for choice-based questions (required for MULTIPLE_CHOICE, SINGLE_CHOICE, RANKING)")
        val options: List<String>? = null,
        @Description("Validation rule: min (required for LIKERT_SCALE, RATING)")
        val min: Int? = null,
        @Description("Validation rule: max (required for LIKERT_SCALE, RATING)")
        val max: Int? = null
    )

    enum class QuestionType {
        MULTIPLE_CHOICE,
        SINGLE_CHOICE,
        LIKERT_SCALE,
        RATING,
        OPEN_ENDED,
        YES_NO,
        RANKING,
        MATRIX,
        DEMOGRAPHIC
    }

    data class RespondentProfile(
        @Description("Unique identifier for the profile")
        val id: String = UUID.randomUUID().toString(),
        @Description("Description of this respondent type")
        val description: String = "",
        @Description("Demographic attributes (age, gender, location, etc.)")
        val demographics: Map<String, String>? = null,
        @Description("Personality traits and characteristics")
        val characteristics: List<String>? = null,
        @Description("Background context and life experiences")
        val background_context: String? = null
    )

    data class SimulatedRespondent(
        val id: String = "",
        val profile: RespondentProfile = RespondentProfile(),
        val demographics: Map<String, String> = mapOf(),
        val persona_prompt: String = ""
    )

    data class SurveyResponse(
        val respondent_id: String = "",
        val answers: Map<String, Any> = mapOf(),
        val demographics: Map<String, String> = mapOf(),
        val response_time: Long = 0L,
        val reasoning: Map<String, String>? = null
    )

data class ParsedResponse(
        val answer: Any? = null,
        val reasoning: String? = null
    )
    data class QuestionResponse(
        @Description("The answer to the question")
        val answer: Any? = null,
        @Description("Brief explanation of the reasoning behind the answer")
        val reasoning: String? = null
    )


    override fun promptSegment(): String {
        return """
LLMPollSimulation - Simulate polls and surveys with diverse AI personas
  ** Define survey questions with various types (multiple choice, Likert, open-ended)
  ** Create respondent profiles with demographics and characteristics
  ** Generate realistic survey responses from simulated personas
  ** Analyze results with cross-tabulations and statistical summaries
  ** Detect response patterns, biases, and sentiment
  ** Test survey instruments before real-world deployment
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
        log.info("Starting LLMPollSimulationTask")

        // Validate configuration
        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }

        val questions = executionConfig?.questions ?: listOf()
        val profiles = executionConfig?.respondent_profiles ?: listOf()
        val respondentsPerProfile = executionConfig?.respondents_per_profile ?: 10
        val temperature = executionConfig?.temperature ?: 0.7
        val api = orchestrationConfig.defaultChatter.getChildClient(task)

        val (transcriptLink, transcriptStream) = createTranscriptFile(task)
        val transcriptWriter = transcriptStream?.bufferedWriter()
        transcriptWriter?.apply {
            write("# Poll Simulation Report\n\n")
            write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
            write("## Survey Design\n\n")
            write("- **Total Questions:** ${questions.size}\n")
            write("- **Respondent Profiles:** ${profiles.size}\n")
            write("- **Respondents per Profile:** $respondentsPerProfile\n")
            write("- **Total Respondents:** ${profiles.size * respondentsPerProfile}\n")
            write("- **Temperature:** $temperature\n\n")
            write("### Questions\n\n")
            questions.forEachIndexed { idx, q ->
                write("${idx + 1}. **${q.id}** (${q.type}): ${q.text}\n")
                if (!q.options.isNullOrEmpty()) {
                    write("   - Options: ${q.options.joinToString(", ")}\n")
                }
            }
            write("\n---\n\n")
            flush()
        }

        // Create tabbed display
        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = task.ui.newTask(false)
        tabs["Overview"] = overviewTask.placeholder

        overviewTask.add(
            buildString {
                appendLine("# Poll Simulation Overview")
                appendLine()
                appendLine("**Survey Questions:** ${questions.size}")
                appendLine()
                appendLine("**Respondent Profiles:** ${profiles.size}")
                appendLine()
                appendLine("**Total Respondents:** ${profiles.size * respondentsPerProfile}")
                appendLine()
                appendLine("**Temperature:** $temperature")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("*Generating respondent personas...*")
            }.renderMarkdown()
        )
        task.update()

        try {
            // Generate respondents
            log.info("Generating ${profiles.size * respondentsPerProfile} simulated respondents")
            val respondents = generateRespondents(profiles, respondentsPerProfile)

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("✅ Generated ${respondents.size} respondent personas")
                    appendLine()
                    appendLine("*Conducting survey...*")
                }.renderMarkdown()
            )
            task.update()

            transcriptWriter?.apply {
                write("## Respondent Profiles\n\n")
                profiles.forEach { profile ->
                    write("### ${profile.description}\n\n")
                    write("**Demographics:** ${profile.demographics}\n\n")
                    write("**Characteristics:** ${profile.characteristics?.joinToString(", ")}\n\n")
                    if (profile.background_context != null) {
                        write("**Background:** ${profile.background_context}\n\n")
                    }
                }
                write("\n---\n\n")
                flush()
            }

            // Progress tab
            val progressTask = task.ui.newTask(false)
            tabs["Progress"] = progressTask.placeholder

            // Conduct survey
            val responses = ConcurrentHashMap<String, SurveyResponse>()
            val completedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val totalRespondents = respondents.size

            progressTask.add(
                buildString {
                    appendLine("# Survey Progress")
                    appendLine()
                    appendLine("**Total Respondents:** $totalRespondents")
                    appendLine()
                    appendLine("*Collecting responses...*")
                }.renderMarkdown()
            )
            task.update()

            // Submit all surveys to thread pool
            val futures = respondents.map { respondent ->
                task.ui.pool.submit {
                    try {
                        val response = conductSurvey(respondent, questions, api, temperature)
                        responses[respondent.id] = response
                        val completed = completedCount.incrementAndGet()

                        if (completed % 10 == 0 || completed == totalRespondents) {
                            progressTask.add(
                                buildString {
                                    appendLine()
                                    appendLine("**Progress:** $completed / $totalRespondents (${(completed * 100 / totalRespondents)}%)")
                                }.renderMarkdown()
                            )
                            task.update()
                        }

                        log.debug("Survey completed for respondent ${respondent.id}")
                    } catch (e: Exception) {
                        failedCount.incrementAndGet()
                        log.error("Error conducting survey for respondent ${respondent.id}", e)
                    }
                }
            }

            // Wait for all surveys to complete
            futures.forEach { it.get() }

            val successfulResponses = responses.values.toList()
            log.info("Survey complete: ${successfulResponses.size}/${totalRespondents} successful responses")

            progressTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("✅ **Survey Complete**")
                    appendLine()
                    appendLine("**Successful Responses:** ${successfulResponses.size}")
                    appendLine("**Failed Responses:** ${failedCount.get()}")
                }.renderMarkdown()
            )
            task.update()

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("✅ Survey complete: ${successfulResponses.size}/${totalRespondents} responses")
                    appendLine()
                    appendLine("*Analyzing results...*")
                }.renderMarkdown()
            )
            task.update()

            // Write responses to transcript
            transcriptWriter?.apply {
                write("## Survey Responses\n\n")
                write("**Successful Responses:** ${successfulResponses.size}\n")
                write("**Failed Responses:** ${failedCount.get()}\n\n")
                successfulResponses.take(5).forEach { response ->
                    write("### Sample Response: ${response.respondent_id}\n\n")
                    write("**Demographics:** ${response.demographics}\n\n")
                    write("**Answers:**\n")
                    response.answers.forEach { (qId, answer) ->
                        write("- $qId: $answer\n")
                    }
                    write("\n")
                }
                write("\n---\n\n")
                flush()
            }

            // Generate descriptive statistics
            val statsTask = task.ui.newTask(false)
            tabs["Statistics"] = statsTask.placeholder

            statsTask.add(
                buildString {
                    appendLine("# Descriptive Statistics")
                    appendLine()
                    appendLine("*Computing statistics...*")
                }.renderMarkdown()
            )
            task.update()

            val statistics = generateDescriptiveStatistics(successfulResponses, questions)

            transcriptWriter?.apply {
                write("## Descriptive Statistics\n\n")
                write(statistics)
                write("\n\n")
                flush()
            }

            statsTask.add(
                buildString {
                    appendLine()
                    appendLine(statistics)
                }.renderMarkdown()
            )
            task.update()

            // Cross-tabulation analysis
            if (executionConfig?.cross_tabulation == true && executionConfig.include_demographics) {
                val crossTabTask = task.ui.newTask(false)
                tabs["Cross-Tabulation"] = crossTabTask.placeholder

                crossTabTask.add(
                    buildString {
                        appendLine("# Cross-Tabulation Analysis")
                        appendLine()
                        appendLine("*Generating cross-tabs...*")
                    }.renderMarkdown()
                )
                task.update()

                val crossTabs = generateCrossTabs(
                    successfulResponses,
                    questions,
                    executionConfig.demographic_dimensions ?: listOf()
                )

                transcriptWriter?.apply {
                    write("## Cross-Tabulation Analysis\n\n")
                    write(crossTabs)
                    write("\n\n")
                    flush()
                }

                crossTabTask.add(
                    buildString {
                        appendLine()
                        appendLine(crossTabs)
                    }.renderMarkdown()
                )
                task.update()
            }

            // Sentiment analysis
            if (executionConfig?.sentiment_analysis == true) {
                val sentimentTask = task.ui.newTask(false)
                tabs["Sentiment"] = sentimentTask.placeholder

                sentimentTask.add(
                    buildString {
                        appendLine("# Sentiment Analysis")
                        appendLine()
                        appendLine("*Analyzing sentiment...*")
                    }.renderMarkdown()
                )
                task.update()

                val sentiment = performSentimentAnalysis(successfulResponses, questions, api)

                transcriptWriter?.apply {
                    write("## Sentiment Analysis\n\n")
                    write(sentiment)
                    write("\n\n")
                    flush()
                }

                sentimentTask.add(
                    buildString {
                        appendLine()
                        appendLine(sentiment)
                    }.renderMarkdown()
                )
                task.update()
            }

            // Bias detection
            if (executionConfig?.bias_detection == true) {
                val biasTask = task.ui.newTask(false)
                tabs["Bias Detection"] = biasTask.placeholder

                biasTask.add(
                    buildString {
                        appendLine("# Bias Detection")
                        appendLine()
                        appendLine("*Detecting biases...*")
                    }.renderMarkdown()
                )
                task.update()

                val biases = detectBiases(successfulResponses, questions, api)

                transcriptWriter?.apply {
                    write("## Bias Detection\n\n")
                    write(biases)
                    write("\n\n")
                    flush()
                }

                biasTask.add(
                    buildString {
                        appendLine()
                        appendLine(biases)
                    }.renderMarkdown()
                )
                task.update()
            }

            // Generate insights
            log.info("Generating insights from poll results")
            val insightsTask = task.ui.newTask(false)
            tabs["Insights"] = insightsTask.placeholder

            insightsTask.add(
                buildString {
                    appendLine("# Poll Insights")
                    appendLine()
                    appendLine("*Generating insights...*")
                }.renderMarkdown()
            )
            task.update()

            val insightsAgent = ChatAgent(
                prompt = """
You are an expert survey researcher and data analyst.
Analyze the following poll results and provide insights about:
1. Key findings and trends
2. Demographic patterns and differences
3. Response consistency and quality
4. Potential biases or limitations
5. Recommendations for survey improvement
6. Implications for real-world polling

Be specific and reference the data provided.
                """.trimIndent(),
                model = api,
                temperature = 0.3
            )

            val insightsPrompt = buildString {
                appendLine("Poll Design:")
                appendLine("- Questions: ${questions.size}")
                appendLine("- Respondents: ${successfulResponses.size}")
                appendLine("- Profiles: ${profiles.size}")
                appendLine()
                appendLine("Results Summary:")
                appendLine(statistics.take(2000))
                appendLine()
                appendLine("Sample Responses:")
                successfulResponses.take(3).forEach { response ->
                    appendLine("- Demographics: ${response.demographics}")
                    appendLine("  Answers: ${response.answers.entries.take(3).joinToString(", ") { "${it.key}: ${it.value}" }}")
                }
            }

            val insights = insightsAgent.answer(listOf(insightsPrompt))

            transcriptWriter?.apply {
                write("## Insights and Recommendations\n\n")
                write(insights)
                write("\n\n")
                flush()
            }

            insightsTask.add(
                buildString {
                    appendLine()
                    appendLine(insights)
                }.renderMarkdown()
            )
            task.update()

            // Final summary
            val totalTime = System.currentTimeMillis() - startTime
            val avgResponseTime = if (successfulResponses.isNotEmpty()) {
                successfulResponses.map { it.response_time }.average()
            } else 0.0

            val summary = buildString {
                appendLine("## Summary")
                appendLine()
                appendLine("- **Total Respondents:** $totalRespondents")
                appendLine("- **Successful Responses:** ${successfulResponses.size}")
                appendLine("- **Response Rate:** ${String.format("%.1f", successfulResponses.size * 100.0 / totalRespondents)}%")
                appendLine("- **Total Time:** ${totalTime / 1000.0}s")
                appendLine("- **Avg Response Time:** ${avgResponseTime / 1000.0}s")
                appendLine()
                appendLine("## Key Findings")
                appendLine()
                appendLine(statistics.take(1000))
                appendLine()
                appendLine("## Insights")
                appendLine()
                appendLine(insights.take(2000))
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("Full report: [View Transcript]($transcriptLink)")
            }

            transcriptWriter?.apply {
                write("---\n\n")
                write("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
                write("**Total Time:** ${totalTime / 1000.0}s | **Responses:** ${successfulResponses.size}/$totalRespondents\n")
                flush()
                close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Poll Simulation Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine("**Responses:** ${successfulResponses.size}/$totalRespondents")
                    appendLine()
                    appendLine("**Response Rate:** ${String.format("%.1f", successfulResponses.size * 100.0 / totalRespondents)}%")
                }.renderMarkdown()
            )
            task.update()

            log.info("LLMPollSimulationTask completed: responses=${successfulResponses.size}/$totalRespondents, time=${totalTime}ms")

            task.complete("Completed poll simulation with ${successfulResponses.size} responses in ${totalTime / 1000}s")

            val finalMessage = buildString {
                appendLine(summary)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("Full poll report: <a href='$transcriptLink' target='_blank'>$transcriptLink</a> <a href='${transcriptLink.removeSuffix(".md")}.html' target='_blank'>html</a>")
            }
            resultFn(finalMessage)

        } catch (e: Exception) {
            log.error("Error during poll simulation", e)
            task.error(e)

            transcriptWriter?.apply {
                write("\n\n---\n\n## ❌ Error Occurred\n\n")
                write("**Error:** ${e.message}\n\n")
                flush()
                close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                }.renderMarkdown()
            )
            task.update()

            resultFn("Error in poll simulation: ${e.message}")
        }
    }

    private fun generateRespondents(
        profiles: List<RespondentProfile>,
        respondentsPerProfile: Int
    ): List<SimulatedRespondent> {
        val respondents = mutableListOf<SimulatedRespondent>()

        profiles.forEach { profile ->
            repeat(respondentsPerProfile) { index ->
                val respondentId = "${profile.id}_${index + 1}"

                // Generate or use demographics
                val demographics = if (profile.demographics != null) {
                    profile.demographics.toMutableMap()
                } else {
                    generateRealisticDemographics()
                }

                // Build persona prompt
                val personaPrompt = buildPersonaPrompt(profile, demographics)

                respondents.add(
                    SimulatedRespondent(
                        id = respondentId,
                        profile = profile,
                        demographics = demographics,
                        persona_prompt = personaPrompt
                    )
                )
            }
        }

        return respondents
    }

    private fun generateRealisticDemographics(): MutableMap<String, String> {
        val random = Random()
        val demographics = mutableMapOf<String, String>()

        // Age
        val ageRanges = listOf("18-24", "25-34", "35-44", "45-54", "55-64", "65+")
        demographics["age"] = ageRanges[random.nextInt(ageRanges.size)]

        // Gender
        val genders = listOf("Male", "Female", "Non-binary", "Prefer not to say")
        demographics["gender"] = genders[random.nextInt(genders.size)]

        // Location
        val locations = listOf("Urban", "Suburban", "Rural")
        demographics["location"] = locations[random.nextInt(locations.size)]

        // Education
        val educationLevels = listOf("High School", "Some College", "Bachelor's", "Master's", "Doctorate")
        demographics["education"] = educationLevels[random.nextInt(educationLevels.size)]

        return demographics
    }

    private fun buildPersonaPrompt(profile: RespondentProfile, demographics: Map<String, String>): String {
        return """
You are participating in a survey. Please respond authentically based on your profile:

Profile: ${profile.description}

Demographics:
${demographics.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }}

${if (profile.background_context != null) "Background:\n${profile.background_context}\n\n" else ""}
${if (!profile.characteristics.isNullOrEmpty()) "Characteristics:\n${profile.characteristics.joinToString("\n") { "- $it" }}\n\n" else ""}

Instructions:
- Answer each question honestly from your perspective
- Consider your background and values when responding
- If a question doesn't apply to you, indicate that clearly
- Maintain consistency across your responses
- Be thoughtful and realistic in your answers
        """.trimIndent()
    }

private fun conductSurvey(
        respondent: SimulatedRespondent,
        questions: List<SurveyQuestion>,
        api: ChatInterface,
        temperature: Double
    ): SurveyResponse {
        val startTime = System.currentTimeMillis()
        val answers = mutableMapOf<String, Any>()
        val reasoning = mutableMapOf<String, String>()

        val surveyAgent = ChatAgent(
            prompt = respondent.persona_prompt,
            model = api,
            temperature = temperature
        )
        val responseParser = ParsedAgent(
            resultClass = QuestionResponse::class.java,
            prompt = """
Parse the survey response and extract the answer and reasoning.
For the answer field, return the appropriate type based on the question:
- For multiple choice: return a list of selected options
- For single choice: return the selected option as a string
- For numeric ratings/scales: return the number
- For yes/no: return "Yes" or "No"
- For rankings: return a list of options in ranked order
- For open-ended: return the full text response
            """.trimIndent(),
            model = api,
            temperature = 0.1,
            parsingChatter = api
        )


        questions.forEach { question ->
            // Check conditional logic
            if (question.conditional_on != null) {
                val conditionMet = checkCondition(question.conditional_on, answers)
                if (!conditionMet) {
                    return@forEach // Skip this question
                }
            }

            // Format question based on type
            val questionPrompt = formatQuestion(question)

            // Get response
            val response = surveyAgent.answer(listOf(questionPrompt))

            // Parse response based on question type
            val parsedResponse = try {
                val parsed = responseParser.answer(
                    listOf(
                        "Question Type: ${question.type}",
                        "Question: ${question.text}",
                        if (!question.options.isNullOrEmpty()) "Options: ${question.options.joinToString(", ")}" else "",
                        "Response: $response"
                    )
                ).obj
                ParsedResponse(parsed.answer, parsed.reasoning)
            } catch (e: Exception) {
                log.warn("Error parsing response for question ${question.id}", e)
                ParsedResponse(response, null)
            }

            answers[question.id] = parsedResponse.answer ?: ""
            if (parsedResponse.reasoning != null) {
                reasoning[question.id] = parsedResponse.reasoning
            }
        }

        val responseTime = System.currentTimeMillis() - startTime

        return SurveyResponse(
            respondent_id = respondent.id,
            answers = answers,
            demographics = respondent.demographics,
            response_time = responseTime,
            reasoning = reasoning
        )
    }

    private fun checkCondition(conditionalOn: String, answers: Map<String, Any>): Boolean {
        // Simple condition checking - can be extended for complex logic
        return answers.containsKey(conditionalOn)
    }

    private fun formatQuestion(question: SurveyQuestion): String {
        return buildString {
            appendLine(question.text)
            appendLine()

            when (question.type) {
                QuestionType.MULTIPLE_CHOICE -> {
                    appendLine("Select all that apply:")
                    question.options?.forEachIndexed { idx, option ->
                        appendLine("${idx + 1}. $option")
                    }
                    appendLine()
                    appendLine("Provide your answer as a comma-separated list of numbers (e.g., '1, 3, 4')")
                }
                QuestionType.SINGLE_CHOICE -> {
                    appendLine("Select one:")
                    question.options?.forEachIndexed { idx, option ->
                        appendLine("${idx + 1}. $option")
                    }
                    appendLine()
                    appendLine("Provide your answer as a single number (e.g., '2')")
                }
                QuestionType.LIKERT_SCALE -> {
                    val min = question.min as? Int ?: 1
                    val max = question.max as? Int ?: 5
                    appendLine("Rate on a scale from $min to $max:")
                    appendLine("$min = Strongly Disagree, $max = Strongly Agree")
                    appendLine()
                    appendLine("Provide your answer as a single number")
                }
                QuestionType.RATING -> {
                    val min = question.min as? Int ?: 1
                    val max = question.max as? Int ?: 10
                    appendLine("Rate from $min to $max")
                    appendLine()
                    appendLine("Provide your answer as a single number")
                }
                QuestionType.YES_NO -> {
                    appendLine("Answer Yes or No")
                }
                QuestionType.RANKING -> {
                    appendLine("Rank the following options in order of preference (1 = most preferred):")
                    question.options?.forEachIndexed { idx, option ->
                        appendLine("${idx + 1}. $option")
                    }
                    appendLine()
                    appendLine("Provide your ranking as a comma-separated list (e.g., '3, 1, 2, 4')")
                }
                QuestionType.OPEN_ENDED -> {
                    appendLine("Please provide your answer in your own words.")
                }
                else -> {}
            }

            if (question.required) {
                appendLine()
                appendLine("(This question is required)")
            }
        }
    }



    private fun generateDescriptiveStatistics(
        responses: List<SurveyResponse>,
        questions: List<SurveyQuestion>
    ): String {
        val stats = StringBuilder()
        stats.appendLine("### Response Summary\n")
        stats.appendLine("- **Total Responses:** ${responses.size}")
        stats.appendLine("- **Avg Response Time:** ${responses.map { it.response_time }.average().toInt()}ms")
        stats.appendLine()

        questions.forEach { question ->
            stats.appendLine("### ${question.id}: ${question.text}\n")
            val answers = responses.mapNotNull { it.answers[question.id] }
            when (question.type) {
                QuestionType.MULTIPLE_CHOICE, QuestionType.SINGLE_CHOICE -> {
                    val frequency = mutableMapOf<String, Int>()
                    answers.forEach { answer ->
                        when (answer) {
                            is String -> frequency[answer] = frequency.getOrDefault(answer, 0) + 1
                            is List<*> -> answer.forEach { item ->
                                val key = item.toString()
                                frequency[key] = frequency.getOrDefault(key, 0) + 1
                            }
                        }
                    }

                    stats.appendLine("**Response Distribution:**\n")
                    frequency.entries.sortedByDescending { it.value }.forEach { (option, count) ->
                        val percentage = (count * 100.0 / responses.size)
                        stats.appendLine("- $option: $count (${String.format("%.1f", percentage)}%)")
                    }
                    stats.appendLine()
                }
                QuestionType.LIKERT_SCALE, QuestionType.RATING -> {
                    val numericAnswers = answers.mapNotNull { (it as? Number)?.toDouble() }
                    if (numericAnswers.isNotEmpty()) {
                        val mean = numericAnswers.average()
                        val stdDev = calculateStdDev(numericAnswers)
                        val median = numericAnswers.sorted()[numericAnswers.size / 2]

                        stats.appendLine("**Statistics:**\n")
                        stats.appendLine("- Mean: ${String.format("%.2f", mean)}")
                        stats.appendLine("- Median: ${String.format("%.2f", median)}")
                        stats.appendLine("- Std Dev: ${String.format("%.2f", stdDev)}")
                        stats.appendLine("- Min: ${numericAnswers.minOrNull()}")
                        stats.appendLine("- Max: ${numericAnswers.maxOrNull()}")
                        stats.appendLine()
                    }
                }
                QuestionType.YES_NO -> {
                    val yesCount = answers.count { it.toString().equals("Yes", ignoreCase = true) }
                    val noCount = answers.count { it.toString().equals("No", ignoreCase = true) }
                    stats.appendLine("**Results:**\n")
                    stats.appendLine("- Yes: $yesCount (${String.format("%.1f", yesCount * 100.0 / responses.size)}%)")
                    stats.appendLine("- No: $noCount (${String.format("%.1f", noCount * 100.0 / responses.size)}%)")
                    stats.appendLine()
                }
                QuestionType.OPEN_ENDED -> {
                    val avgLength = answers.map { it.toString().length }.average()
                    stats.appendLine("**Text Analysis:**\n")
                    stats.appendLine("- Responses: ${answers.size}")
                    stats.appendLine("- Avg Length: ${avgLength.toInt()} characters")
                    stats.appendLine()
                    stats.appendLine("**Sample Responses:**\n")
                    answers.take(3).forEach { answer ->
                        val preview = answer.toString().take(150)
                        stats.appendLine("- \"${preview}${if (answer.toString().length > 150) "..." else ""}\"")
                    }
                    stats.appendLine()
                }
                else -> {}
            }
        }

        return stats.toString()
    }

    private fun generateCrossTabs(
        responses: List<SurveyResponse>,
        questions: List<SurveyQuestion>,
        demographicDimensions: List<String>
    ): String {
        val crossTabs = StringBuilder()

        crossTabs.appendLine("### Cross-Tabulation by Demographics\n")

        demographicDimensions.forEach { dimension ->
            crossTabs.appendLine("#### By $dimension\n")

            // Get unique values for this dimension
            val dimensionValues = responses.mapNotNull { it.demographics[dimension] }.distinct().sorted()

            questions.forEach { question ->
                if (question.type in listOf(QuestionType.SINGLE_CHOICE, QuestionType.YES_NO, QuestionType.LIKERT_SCALE)) {
                    crossTabs.appendLine("**${question.id}**\n")

                    // Create cross-tab table
                    crossTabs.appendLine("| $dimension | Response | Count | % |")
                    crossTabs.appendLine("|-----------|----------|-------|---|")

                    dimensionValues.forEach { dimValue ->
                        val subset = responses.filter { it.demographics[dimension] == dimValue }
                        val answers = subset.mapNotNull { it.answers[question.id] }

                        when (question.type) {
                            QuestionType.SINGLE_CHOICE, QuestionType.YES_NO -> {
                                val frequency = answers.groupingBy { it.toString() }.eachCount()
                                frequency.forEach { (answer, count) ->
                                    val percentage = if (subset.isNotEmpty()) count * 100.0 / subset.size else 0.0
                                    crossTabs.appendLine("| $dimValue | $answer | $count | ${String.format("%.1f", percentage)}% |")
                                }
                            }
                            QuestionType.LIKERT_SCALE -> {
                                val numericAnswers = answers.mapNotNull { (it as? Number)?.toDouble() }
                                if (numericAnswers.isNotEmpty()) {
                                    val mean = numericAnswers.average()
                                    crossTabs.appendLine("| $dimValue | Mean | ${String.format("%.2f", mean)} | - |")
                                }
                            }
                            else -> {}
                        }
                    }
                    crossTabs.appendLine()
                }
            }
        }

        return crossTabs.toString()
    }

    data class SentimentScore(
        val positive: Double = 0.0,
        val negative: Double = 0.0,
        val neutral: Double = 0.0,
        val overall: String = "",
    )

    private fun performSentimentAnalysis(
        responses: List<SurveyResponse>,
        questions: List<SurveyQuestion>,
        api: ChatInterface
    ): String {
        val sentiment = StringBuilder()

        sentiment.appendLine("### Sentiment Analysis of Open-Ended Responses\n")

        val openEndedQuestions = questions.filter { it.type == QuestionType.OPEN_ENDED }

        if (openEndedQuestions.isEmpty()) {
            sentiment.appendLine("*No open-ended questions to analyze*\n")
            return sentiment.toString()
        }


        val sentimentAgent = ParsedAgent(
            resultClass = SentimentScore::class.java,
            prompt = """
Analyze the sentiment of the following text response.
Provide scores for positive, negative, and neutral sentiment (0.0 to 1.0, sum to 1.0).
Also provide an overall sentiment classification: Positive, Negative, or Neutral.
            """.trimIndent(),
            model = api,
            temperature = 0.1,
            parsingChatter = api
        )

        openEndedQuestions.forEach { question ->
            sentiment.appendLine("#### ${question.id}: ${question.text}\n")

            val answers = responses.mapNotNull { it.answers[question.id]?.toString() }
            val sentiments = mutableListOf<SentimentScore>()

            answers.take(20).forEach { answer ->
                try {
                    val score = sentimentAgent.answer(listOf("Text: $answer")).obj
                    sentiments.add(score)
                } catch (e: Exception) {
                    log.warn("Error analyzing sentiment", e)
                }
            }

            if (sentiments.isNotEmpty()) {
                val avgPositive = sentiments.map { it.positive }.average()
                val avgNegative = sentiments.map { it.negative }.average()
                val avgNeutral = sentiments.map { it.neutral }.average()

                sentiment.appendLine("**Average Sentiment Scores:**\n")
                sentiment.appendLine("- Positive: ${String.format("%.2f", avgPositive)}")
                sentiment.appendLine("- Negative: ${String.format("%.2f", avgNegative)}")
                sentiment.appendLine("- Neutral: ${String.format("%.2f", avgNeutral)}")
                sentiment.appendLine()

                val overallCounts = sentiments.groupingBy { it.overall }.eachCount()
                sentiment.appendLine("**Overall Classification:**\n")
                overallCounts.forEach { (classification, count) ->
                    sentiment.appendLine("- $classification: $count (${String.format("%.1f", count * 100.0 / sentiments.size)}%)")
                }
                sentiment.appendLine()
            }
        }

        return sentiment.toString()
    }

    private fun detectBiases(
        responses: List<SurveyResponse>,
        questions: List<SurveyQuestion>,
        api: ChatInterface
    ): String {
        val biases = StringBuilder()

        biases.appendLine("### Bias Detection Analysis\n")

        // Response pattern analysis
        biases.appendLine("#### Response Patterns\n")

        questions.forEach { question ->
            when (question.type) {
                QuestionType.LIKERT_SCALE, QuestionType.RATING -> {
                    val answers = responses.mapNotNull { (it.answers[question.id] as? Number)?.toDouble() }
                    if (answers.isNotEmpty()) {
                        val mean = answers.average()
                        val stdDev = calculateStdDev(answers)

                        // Check for central tendency bias
                        val min = question.min as? Int ?: 1
                        val max = question.max as? Int ?: 5
                        val midpoint = (min + max) / 2.0

                        if (abs(mean - midpoint) < 0.5) {
                            biases.appendLine("⚠️ **${question.id}**: Possible central tendency bias (mean=${String.format("%.2f", mean)}, midpoint=$midpoint)")
                        }

                        // Check for low variance (acquiescence bias)
                        if (stdDev < 0.5) {
                            biases.appendLine("⚠️ **${question.id}**: Low variance detected (sd=${String.format("%.2f", stdDev)}), possible acquiescence bias")
                        }
                    }
                }
                QuestionType.MULTIPLE_CHOICE -> {
                    // Check for primacy/recency effects
                    val selections = mutableMapOf<Int, Int>()
                    responses.forEach { response ->
                        val answer = response.answers[question.id]
                        if (answer is List<*>) {
                            answer.forEach { item ->
                                val index = question.options?.indexOf(item.toString()) ?: -1
                                if (index >= 0) {
                                    selections[index] = selections.getOrDefault(index, 0) + 1
                                }
                            }
                        }
                    }

                    if (selections.isNotEmpty()) {
                        val firstOption = selections[0] ?: 0
                        val lastOption = selections[selections.keys.maxOrNull() ?: 0] ?: 0
                        val avgMiddle = selections.filter { it.key != 0 && it.key != selections.keys.maxOrNull() }
                            .values.average()

                        if (firstOption > avgMiddle * 1.5) {
                            biases.appendLine("⚠️ **${question.id}**: Possible primacy effect (first option selected ${String.format("%.1f", firstOption * 100.0 / responses.size)}% of time)")
                        }
                        if (lastOption > avgMiddle * 1.5) {
                            biases.appendLine("⚠️ **${question.id}**: Possible recency effect (last option selected ${String.format("%.1f", lastOption * 100.0 / responses.size)}% of time)")
                        }
                    }
                }
                else -> {}
            }
        }

        biases.appendLine()

        // Demographic bias analysis
        biases.appendLine("#### Demographic Bias Analysis\n")

        val demographicDimensions = responses.flatMap { it.demographics.keys }.distinct()

        demographicDimensions.forEach { dimension ->
            val dimensionValues = responses.mapNotNull { it.demographics[dimension] }.distinct()

            if (dimensionValues.size > 1) {
                questions.filter { it.type in listOf(QuestionType.LIKERT_SCALE, QuestionType.RATING) }
                    .forEach { question ->
                        val groupMeans = dimensionValues.map { dimValue ->
                            val subset = responses.filter { it.demographics[dimension] == dimValue }
                            val answers = subset.mapNotNull { (it.answers[question.id] as? Number)?.toDouble() }
                            dimValue to if (answers.isNotEmpty()) answers.average() else 0.0
                        }.toMap()

                        val maxDiff = groupMeans.values.maxOrNull()!! - groupMeans.values.minOrNull()!!

                        if (maxDiff > 1.0) {
                            biases.appendLine("⚠️ **${question.id}** by $dimension: Significant difference detected (max diff=${String.format("%.2f", maxDiff)})")
                            groupMeans.forEach { (value, mean) ->
                                biases.appendLine("  - $value: ${String.format("%.2f", mean)}")
                            }
                        }
                    }
            }
        }

        biases.appendLine()

        // Response quality indicators
        biases.appendLine("#### Response Quality\n")

        val avgResponseTime = responses.map { it.response_time }.average()
        val fastResponses = responses.count { it.response_time < avgResponseTime * 0.5 }

        if (fastResponses > responses.size * 0.2) {
            biases.appendLine("⚠️ **Fast Responses**: ${String.format("%.1f", fastResponses * 100.0 / responses.size)}% of responses were unusually fast, possible satisficing behavior")
        }

        biases.appendLine()

        return biases.toString()
    }

    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    private fun createTranscriptFile(task: SessionTask): Pair<String, FileOutputStream?> {
        val transcriptFile = "poll_simulation_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.complete(
            "Writing poll report to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a>"
        )
        return Pair(link, markdownTranscript)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(LLMPollSimulationTask::class.java)
        val LLMPollSimulation = TaskType(
            "LLMPollSimulation",
            LLMPollSimulationTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Simulate polls and surveys with AI personas",
            """
              Simulates polls and surveys using LLMs to model diverse respondent personas.
              <ul>
                <li>Define survey questions with multiple types (choice, Likert, open-ended)</li>
                <li>Create respondent profiles with demographics and characteristics</li>
                <li>Generate realistic survey responses from simulated personas</li>
                <li>Analyze results with descriptive statistics and frequency distributions</li>
                <li>Cross-tabulation analysis by demographic dimensions</li>
                <li>Sentiment analysis for open-ended responses</li>
                <li>Bias detection (central tendency, primacy/recency effects)</li>
                <li>Automated insights and recommendations</li>
                <li>Comprehensive reports with visualizations</li>
              </ul>
              <p><strong>Use cases:</strong> Survey instrument testing, response pattern exploration, demographic analysis, bias detection</p>
            """
        )
    }
}