package com.simiacryptus.cognotik.plan.tools.online.processing

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.parserCast
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class JobMatchingStrategy : DefaultSummarizerStrategy() {

  data class JobMatchingConfig(
    @Description("User's resume/experience summary")
    val user_experience: String = "",
    @Description("Desired job titles or keywords")
    val target_roles: List<String> = listOf(),
    @Description("Required skills to match")
    val required_skills: List<String>? = null,
    @Description("Preferred locations (cities, states, countries, or 'Remote')")
    val preferred_locations: List<String>? = null,
    @Description("Acceptable locations (if different from preferred)")
    val acceptable_locations: List<String>? = null,
    @Description("Excluded locations")
    val excluded_locations: List<String>? = null,
    @Description("Minimum match score (0.0-1.0)")
    val min_match_score: Double = 0.6,
    @Description("(Optional) Stop after finding N good matches")
    val target_matches: Int? = null,
    @Description("Automatically adjust match threshold based on results")
    val adaptive_threshold: Boolean = false,
    @Description("Preferred industries")
    val preferred_industries: List<String>? = null,
    @Description("Excluded companies")
    val excluded_companies: List<String>? = null,
    @Description("Minimum acceptable salary (annual)")
    val min_salary: Int? = null,
    @Description("Target salary (annual)")
    val target_salary: Int? = null,
    @Description("Maximum salary expectation (annual)")
    val max_salary: Int? = null,
    @Description("Currency for salary (e.g., USD, EUR, GBP)")
    val salary_currency: String = "USD",
    @Description("Work arrangement preference: 'remote', 'hybrid', 'onsite', or 'flexible'")
    val work_arrangement_preference: String? = null,
    @Description("Maximum acceptable days in office per week (for hybrid roles)")
    val max_days_in_office: Int? = null,
    @Description("Willing to travel (percentage or 'none', 'occasional', 'frequent')")
    val travel_willingness: String? = null,
    @Description("Maximum acceptable travel percentage (0-100)")
    val max_travel_percentage: Int? = null,
    @Description("Willing to relocate")
    val willing_to_relocate: Boolean = false,
    @Description("Relocation assistance required")
    val requires_relocation_assistance: Boolean = false
  )


  override val description: String
    get() = "Analyzes job postings against user experience to find strong matches, generates application materials, and saves detailed reports."

  data class JobAnalysis(
    @Description("Job title/position name")
    val job_title: String? = null,
    @Description("Company/organization name")
    val company: String = "",
    @Description("Primary job location (city, state, country)")
    val location: String? = null,
    @Description("Additional locations or service areas")
    val additional_locations: List<String>? = null,
    @Description("Work arrangement: 'remote', 'hybrid', 'onsite'")
    val work_arrangement: String? = null,
    @Description("Days in office per week (for hybrid)")
    val days_in_office: Int? = null,
    @Description("Travel requirements description")
    val travel_requirements: String? = null,
    @Description("Travel percentage (0-100)")
    val travel_percentage: Int? = null,
    @Description("Relocation offered")
    val relocation_offered: Boolean? = null,
    @Description("Relocation assistance details")
    val relocation_assistance: String? = null,
    @Description("URL where the candidate can apply for the position")
    val application_url: String = "",
    @Description("URL of the original job description page")
    val job_description_url: String = "",
    @Description("Full text of the job description")
    val job_description: String = "",
    @Description("Minimum salary offered (if disclosed)")
    val salary_min: Int? = null,
    @Description("Maximum salary offered (if disclosed)")
    val salary_max: Int? = null,
    @Description("Salary currency")
    val salary_currency: String? = null,
    @Description("Salary period: 'annual', 'hourly', 'monthly'")
    val salary_period: String? = null,
    @Description("Additional compensation details (bonus, equity, etc.)")
    val compensation_details: String? = null,
    @Description("List of skills explicitly required for the position")
    val required_skills: List<String> = listOf(),
    @Description("List of skills that are preferred but not required")
    val preferred_skills: List<String> = listOf(),
    @Description("Overall match score between candidate and position (0.0-1.0)")
    val match_score: Double = 0.0,
    @Description("Location compatibility score (0.0-1.0)")
    val location_score: Double = 0.0,
    @Description("Salary compatibility score (0.0-1.0)")
    val salary_score: Double = 0.0,
    @Description("Work arrangement compatibility score (0.0-1.0)")
    val work_arrangement_score: Double = 0.0,
    @Description("Detailed analysis of how well the candidate matches the position")
    val match_analysis: String = "",
    @Description("Analysis of location and work arrangement fit")
    val location_analysis: String = "",
    @Description("Analysis of compensation fit")
    val compensation_analysis: String = "",
    @Description("Skills the candidate lacks that are required or preferred")
    val skill_gaps: List<String> = listOf(),
    @Description("Skills the candidate has that match the job requirements")
    val skill_matches: List<String> = listOf(),
    @Description("Draft cover letter tailored to this specific position")
    val cover_letter: String = "",
    @Description("Strategic notes and recommendations for the application")
    val application_notes: String = ""
  )

  private val goodMatches = ConcurrentHashMap<String, JobAnalysis>()

  companion object {
    private val log = LoggerFactory.getLogger(JobMatchingStrategy::class.java)
  }

  override fun processPage(
    url: String,
    content: String,
    context: PageProcessingStrategy.ProcessingContext
  ) = try {
    log.debug("Processing page: $url")
    val chatInterface = context.orchestrationConfig.parsingChatter.getChildClient(context.task)
    val config = context.executionConfig.content_queries?.parserCast<JobMatchingConfig>(chatInterface)
      ?: run {
        val errorMsg = "Missing JobMatchingConfig for job matching strategy"
        log.error(errorMsg)
        writeToTranscript(context, "**ERROR:** $errorMsg\n")
        throw IllegalArgumentException(errorMsg)
      }

    if (detectJobPosting(content, chatInterface)) {
      log.info("Job posting detected at: $url")
      processJD(url, content, config, context, chatInterface).let { result ->
        val standardProcessing = super.processPage(url, content, context)
        result.copy(
          extractedLinks = standardProcessing.extractedLinks
        )
      }
    } else {
      log.debug("Page is not a job posting, using default processing: $url")
      super.processPage(url, content, context)
    }
  } catch (e: Exception) {
    val errorMsg = "Error processing page for URL: $url - ${e.message}"
    log.error(errorMsg, e)
    context.task.error(e)
    writeToTranscript(context, "\n**ERROR:** $errorMsg\n```text\n${e.stackTraceToString().indent("  ")}\n```\n\n")
    super.processPage(url, content, context)
  }

  private fun writeToTranscript(context: PageProcessingStrategy.ProcessingContext, message: String) {
    context.transcriptStream?.let { stream ->
      try {
        stream.write(message.toByteArray(StandardCharsets.UTF_8))
        stream.flush()
      } catch (e: IOException) {
        log.warn("Failed to write to transcript stream", e)
      }
    }
  }

  private fun processJD(
    url: String,
    content: String,
    config: JobMatchingConfig,
    context: PageProcessingStrategy.ProcessingContext,
    chatInterface: ChatInterface
  ): PageProcessingStrategy.PageProcessingResult {
    log.debug("Processing job description for URL: $url")

    // Log job detection to transcript
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    writeToTranscript(context, "\n#### Job Posting Detected at $timestamp\n**URL:** [$url]($url)\n\n")

    // Extract and analyze job details
    val jobAnalysis = try {
      analyzeJobMatch(url, content, config, context, chatInterface)
    } catch (e: Exception) {
      val errorMsg = "Failed to analyze job match for URL: $url - ${e.message}"
      log.error(errorMsg, e)
      context.task.error(e)
      writeToTranscript(context, "\n**ERROR:** $errorMsg\n```\n${e.stackTraceToString().indent("  ")}\n```\n\n")
      throw e
    }

    // If it's a good match, save detailed report
    val shouldTerminate = if (jobAnalysis.match_score >= config.min_match_score) {
      goodMatches[url] = jobAnalysis
      try {
        saveJobReport(jobAnalysis, context)
      } catch (e: Exception) {
        val errorMsg = "Failed to save job report for ${jobAnalysis.company} - ${jobAnalysis.job_title}: ${e.message}"
        log.error(errorMsg, e)
        context.task.error(e)
        writeToTranscript(context, "\n**ERROR:** \n```text\n${errorMsg.indent("  ")}\n```\n")
        // Don't throw - we still want to continue processing
      }

      // Log good match to transcript
      writeToTranscript(context, buildString {

        appendLine("<details>")
        appendLine("<summary>**✅ GOOD MATCH FOUND** (Score: ${(jobAnalysis.match_score * 100).toInt()}%)</summary>")
        appendLine("\n\n```json\n${jobAnalysis.toJson()}\n```\n\n")
        appendLine("</details>\n")
        appendLine("- **URL:** [$url]($url)\n\n")
        appendLine("- **Position:** ${jobAnalysis.job_title}")
        appendLine("- **Company:** ${jobAnalysis.company}")
        appendLine("- **Location:** ${jobAnalysis.location ?: "Not specified"}")
        appendLine("- **Total Matches Found:** ${goodMatches.size}/${config.target_matches}\n")
      })

      log.info("Good match found: ${jobAnalysis.company} - ${jobAnalysis.job_title} (Score: ${jobAnalysis.match_score})")

      // Check if we've found enough good matches
      config.target_matches != null && config.target_matches > 0 && goodMatches.size >= config.target_matches
    } else {
      // Log weak match to transcript
      writeToTranscript(context, buildString {
        appendLine("<details>")
        appendLine("<summary>**⚠️ Weak Match** (Score: ${(jobAnalysis.match_score * 100).toInt()}%)</summary>")
        appendLine("\n\n```json\n${jobAnalysis.toJson()}\n```\n\n")
        appendLine("</details>\n")
        appendLine("- **URL:** [$url]($url)\n\n")
        appendLine("- **Position:** ${jobAnalysis.job_title}")
        appendLine("- **Company:** ${jobAnalysis.company}\n")
      })

      log.debug("Weak match: ${jobAnalysis.company} - ${jobAnalysis.job_title} (Score: ${jobAnalysis.match_score})")
      false
    }

    return PageProcessingStrategy.PageProcessingResult(
      url = url,
      pageType = CrawlerAgentTask.PageType.OK,
      content = formatJobAnalysis(jobAnalysis),
      metadata = mapOf(
        "job_analysis" to jobAnalysis,
        "match_score" to jobAnalysis.match_score,
        "is_good_match" to (jobAnalysis.match_score >= config.min_match_score)
      ),
      shouldTerminate = shouldTerminate,
      terminationReason = if (shouldTerminate) {
        "Found ${config.target_matches} good job matches"
      } else null
    )
  }

  data class JobDetection(
    val is_job_posting: Boolean = false,
    val confidence: Double = 0.0,
    val detected_title: String? = null,
  )

  private fun detectJobPosting(
    content: String,
    chatInterface: ChatInterface
  ): Boolean {
    log.debug("Detecting if content is a job posting")

    val prompt = """
            Analyze if this content is a job posting/description.

            Look for:
            - Job title
            - Company name
            - Job responsibilities
            - Required qualifications
            - Application instructions
        """.trimIndent()
    val detection = try {
      ParsedAgent(
        prompt = prompt,
        resultClass = JobDetection::class.java,
        model = chatInterface,
        parsingChatter = chatInterface
      ).getParser().apply(content.take(5000)) // Only analyze first 5K chars for detection, using 1 pass (faster)
    } catch (e: Exception) {
      log.error("Failed to detect job posting", e)
      throw e
    }

    log.debug("Job posting detection result: is_job=${detection.is_job_posting}, confidence=${detection.confidence}")
    return detection.is_job_posting && detection.confidence > 0.7
  }

  private fun analyzeJobMatch(
    url: String,
    content: String,
    config: JobMatchingConfig,
    context: PageProcessingStrategy.ProcessingContext,
    chatInterface: ChatInterface
  ): JobAnalysis {
    log.debug("Analyzing job match for URL: $url")

    // Log analysis start to transcript
    writeToTranscript(context, "**Analyzing job match...**\n")

    // Build enriched context from messages
    val additionalContext = if (context.messages.isNotEmpty()) {
      buildString {
        appendLine()
        appendLine("ADDITIONAL CONTEXT FROM USER:")
        context.messages.forEach { message ->
          appendLine(message)
          appendLine()
        }
      }
    } else ""
    val locationContext = buildString {
      appendLine()
      appendLine("LOCATION PREFERENCES:")
      config.preferred_locations?.let { appendLine("Preferred: ${it.joinToString(", ")}") }
      config.acceptable_locations?.let { appendLine("Acceptable: ${it.joinToString(", ")}") }
      config.excluded_locations?.let { appendLine("Excluded: ${it.joinToString(", ")}") }
      appendLine("Willing to relocate: ${config.willing_to_relocate}")
      if (config.requires_relocation_assistance) appendLine("Requires relocation assistance")
    }
    val compensationContext = buildString {
      appendLine()
      appendLine("COMPENSATION EXPECTATIONS:")
      config.min_salary?.let { appendLine("Minimum: ${config.salary_currency} $it/year") }
      config.target_salary?.let { appendLine("Target: ${config.salary_currency} $it/year") }
      config.max_salary?.let { appendLine("Maximum: ${config.salary_currency} $it/year") }
    }
    val workArrangementContext = buildString {
      appendLine()
      appendLine("WORK ARRANGEMENT PREFERENCES:")
      config.work_arrangement_preference?.let { appendLine("Preference: $it") }
      config.max_days_in_office?.let { appendLine("Max days in office: $it/week") }
      config.travel_willingness?.let { appendLine("Travel willingness: $it") }
      config.max_travel_percentage?.let { appendLine("Max travel: $it%") }
    }


    val prompt = """
            Analyze this job posting and compare it to the candidate's experience.

            CANDIDATE EXPERIENCE:
            ${config.user_experience}

            TARGET ROLES: ${config.target_roles.joinToString(", ")}
            REQUIRED SKILLS: ${config.required_skills?.joinToString(", ") ?: "Not specified"}
            ${locationContext}
            ${compensationContext}
            ${workArrangementContext}
            ${additionalContext}

            Extract:
            1. Job title, company, location
            2. Work arrangement (remote/hybrid/onsite), days in office if hybrid
            3. Travel requirements and percentage
            4. Relocation information
            5. Salary range and compensation details (if disclosed)
            6. Application URL
            7. Required and preferred skills
            8. Overall match score (0.0-1.0) based on experience alignment
            9. Location compatibility score (0.0-1.0) considering preferences and work arrangement
            10. Salary compatibility score (0.0-1.0) if salary disclosed
            11. Work arrangement compatibility score (0.0-1.0)
            12. Detailed match analysis
            13. Location and work arrangement analysis
            14. Compensation analysis (if salary disclosed)
            15. Skill gaps and matches
            16. Draft a compelling cover letter (200-300 words) that incorporates the additional context and highlights relevant experience
            17. Application strategy notes
            
            When drafting the cover letter, pay special attention to any specific requirements, preferences, or context 
            provided in the additional context section. Tailor the letter to address these points directly.
            IMPORTANT: When calculating scores, consider:
            - Location score: Match against preferred/acceptable locations, work arrangement fit, relocation needs
            - Salary score: Only calculate if salary is disclosed; compare against min/target/max expectations
            - Work arrangement score: Match remote/hybrid/onsite preference, travel requirements, days in office
            - Overall match score: Weight skills heavily, but factor in location, salary, and work arrangement
        """.trimIndent()

    val analysis = try {
      ParsedAgent(
        prompt = prompt,
        resultClass = JobAnalysis::class.java,
        model = chatInterface,
        parsingChatter = chatInterface
      ).answer(listOf(content))
    } catch (e: Exception) {
      log.error("Failed to analyze job match", e)
      throw e
    }

    log.debug("Job analysis completed with match score: ${analysis.obj.match_score}")
    return analysis.obj.copy(
      job_description_url = url,
    )
  }

  private fun saveJobReport(
    jobAnalysis: JobAnalysis,
    context: PageProcessingStrategy.ProcessingContext
  ) {
    log.debug("Saving job report for: ${jobAnalysis.company} - ${jobAnalysis.job_title}")

    // Log report save to transcript
    writeToTranscript(context, "**Saving detailed job report...**\n")

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val companySafe = jobAnalysis.company.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)
    val titleSafe = jobAnalysis.job_title?.replace(Regex("[^a-zA-Z0-9]"), "_")?.take(30)

    val reportDir = File(context.webSearchDir, "job_matches")

    try {
      if (!reportDir.exists() && !reportDir.mkdirs()) {
        throw IOException("Failed to create report directory: ${reportDir.absolutePath}")
      }
    } catch (e: Exception) {
      log.error("Failed to create report directory", e)
      context.task.error(e)
      throw e
    }

    val reportFile = File(reportDir, "${companySafe}_${titleSafe}_${timestamp}.md")

    val report = buildString {
      appendLine("# Job Application Report")
      appendLine()
      appendLine("**Generated:** ${LocalDateTime.now()}")
      appendLine("**Match Score:** ${jobAnalysis.match_score}")
      appendLine("**Job URL:** ${jobAnalysis.application_url}")
      appendLine()

      appendLine("## Position Details")
      appendLine("- **Title:** ${jobAnalysis.job_title}")
      appendLine("- **Company:** ${jobAnalysis.company}")
      appendLine("- **Location:** ${jobAnalysis.location ?: "Not specified"}")
      jobAnalysis.additional_locations?.let {
        if (it.isNotEmpty()) {
          appendLine("- **Additional Locations:** ${it.joinToString(", ")}")
        }
      }
      appendLine("- **Work Arrangement:** ${jobAnalysis.work_arrangement ?: "Not specified"}")
      jobAnalysis.days_in_office?.let {
        appendLine("- **Days in Office:** $it/week")
      }
      jobAnalysis.travel_percentage?.let {
        appendLine("- **Travel Required:** $it%")
      }
      jobAnalysis.travel_requirements?.let {
        appendLine("- **Travel Details:** $it")
      }
      appendLine("- **Application URL:** [Apply Here](${jobAnalysis.application_url})")
      appendLine()
      appendLine("## Compensation")
      if (jobAnalysis.salary_min != null || jobAnalysis.salary_max != null) {
        val salaryRange = buildString {
          jobAnalysis.salary_min?.let { append("${jobAnalysis.salary_currency ?: "USD"} $it") }
          if (jobAnalysis.salary_min != null && jobAnalysis.salary_max != null) append(" - ")
          jobAnalysis.salary_max?.let { append("${jobAnalysis.salary_currency ?: "USD"} $it") }
          jobAnalysis.salary_period?.let { append(" ($it)") }
        }
        appendLine("- **Salary Range:** $salaryRange")
      } else {
        appendLine("- **Salary Range:** Not disclosed")
      }
      jobAnalysis.compensation_details?.let {
        appendLine("- **Additional Compensation:** $it")
      }
      if (jobAnalysis.relocation_offered == true) {
        appendLine("- **Relocation:** Offered")
        jobAnalysis.relocation_assistance?.let {
          appendLine("  - $it")
        }
      }
      appendLine()

      appendLine("## Match Analysis")
      appendLine("### Overall Match Score: ${(jobAnalysis.match_score * 100).toInt()}%")
      appendLine(jobAnalysis.match_analysis)
      appendLine()
      appendLine("### Location Compatibility: ${(jobAnalysis.location_score * 100).toInt()}%")
      appendLine(jobAnalysis.location_analysis)
      appendLine()
      if (jobAnalysis.salary_min != null || jobAnalysis.salary_max != null) {
        appendLine("### Salary Compatibility: ${(jobAnalysis.salary_score * 100).toInt()}%")
        appendLine(jobAnalysis.compensation_analysis)
        appendLine()
      }
      appendLine("### Work Arrangement Fit: ${(jobAnalysis.work_arrangement_score * 100).toInt()}%")
      appendLine()

      appendLine("## Skills Assessment")
      appendLine("### Matching Skills (${jobAnalysis.skill_matches.size})")
      jobAnalysis.skill_matches.forEach { skill ->
        appendLine("- ✅ $skill")
      }
      appendLine()

      appendLine("### Skill Gaps (${jobAnalysis.skill_gaps.size})")
      jobAnalysis.skill_gaps.forEach { skill ->
        appendLine("- ⚠️ $skill")
      }
      appendLine()

      appendLine("## Cover Letter Draft")
      appendLine()
      appendLine(jobAnalysis.cover_letter)
      appendLine()

      appendLine("## Application Strategy")
      appendLine(jobAnalysis.application_notes)
      appendLine()

      appendLine("---")
      appendLine()
      appendLine("## Job Description")
      appendLine()
      appendLine(jobAnalysis.job_description)
      appendLine("---")
      appendLine()

      appendLine("<details>")
      appendLine("<summary>Job Analysis Data (JSON)</summary>")
      appendLine()
      appendLine("```json")
      appendLine(jobAnalysis.toJson())
      appendLine("```")
      appendLine()
      appendLine("</details>")
    }

    try {
      reportFile.writeText(report)
      log.info("Saved job report: ${reportFile.absolutePath}")
    } catch (e: IOException) {
      val errorMsg = "Failed to write job report to file: ${reportFile.absolutePath}"
      log.error(errorMsg, e)
      context.task.error(e)
      throw IOException(errorMsg, e)
    }

    // Log report location to transcript
  }

  private fun formatJobAnalysis(jobAnalysis: JobAnalysis): String {
    return buildString {
      appendLine("### ${jobAnalysis.job_title} at ${jobAnalysis.company}")
      appendLine()
      appendLine("**Match Score:** ${(jobAnalysis.match_score * 100).toInt()}%")
      appendLine("**Location:** ${jobAnalysis.location ?: "Not specified"}")
      jobAnalysis.work_arrangement?.let {
        appendLine("**Work Arrangement:** $it")
      }
      if (jobAnalysis.salary_min != null || jobAnalysis.salary_max != null) {
        val salaryRange = buildString {
          jobAnalysis.salary_min?.let { append("${jobAnalysis.salary_currency ?: "USD"} $it") }
          if (jobAnalysis.salary_min != null && jobAnalysis.salary_max != null) append(" - ")
          jobAnalysis.salary_max?.let { append("${jobAnalysis.salary_currency ?: "USD"} $it") }
        }
        appendLine("**Salary:** $salaryRange")
      }
      appendLine("**Application:** [Apply Here](${jobAnalysis.application_url})")
      appendLine()

      if (jobAnalysis.match_score >= 0.6) {
        appendLine("✅ **Good Match** - Detailed report saved")
      } else {
        appendLine("⚠️ **Weak Match** - Consider other opportunities")
      }
      appendLine()
      appendLine("**Compatibility Scores:**")
      appendLine("- Skills: ${(jobAnalysis.match_score * 100).toInt()}%")
      appendLine("- Location: ${(jobAnalysis.location_score * 100).toInt()}%")
      if (jobAnalysis.salary_min != null || jobAnalysis.salary_max != null) {
        appendLine("- Salary: ${(jobAnalysis.salary_score * 100).toInt()}%")
      }
      appendLine("- Work Arrangement: ${(jobAnalysis.work_arrangement_score * 100).toInt()}%")
      appendLine()

      appendLine("<details>")
      appendLine("<summary>Match Analysis</summary>")
      appendLine()
      appendLine(jobAnalysis.match_analysis)
      appendLine()
      appendLine("</details>")
      appendLine()
    }
  }

  override fun analysisGoal(context: PageProcessingStrategy.ProcessingContext): String = when {
    context.executionConfig.content_queries != null -> context.executionConfig.content_queries.toJson()
    context.executionConfig.task_description?.isNotBlank() == true -> context.executionConfig.task_description!!
    else -> "Analyze the content and provide insights."
  } + " - Identify pages that contain or are likely to lead to job postings matching the user's experience and target roles."

  override fun shouldContinueCrawling(
    currentResults: List<PageProcessingStrategy.PageProcessingResult>,
    context: PageProcessingStrategy.ProcessingContext
  ): PageProcessingStrategy.ContinuationDecision {
    val anyTermination = currentResults.any { it.shouldTerminate }
    val reason = if (anyTermination) {
      currentResults.first { it.shouldTerminate }.terminationReason ?: "Target matches found"
    } else {
      "Continue searching for job matches (${goodMatches.size} found so far)"
    }
    val shouldContinue = !anyTermination && context.processedCount.get() < context.maxPages
    return PageProcessingStrategy.ContinuationDecision(shouldContinue, reason)
  }

  override fun generateFinalOutput(
    results: List<PageProcessingStrategy.PageProcessingResult>,
    context: PageProcessingStrategy.ProcessingContext
  ): String {
    // Log final summary generation to transcript
    context.transcriptStream?.let { stream ->
      try {
        stream.write("\n\n## Job Search Final Summary\n\n".toByteArray(StandardCharsets.UTF_8))
        stream.write("**Total Pages Analyzed:** ${results.size}\n".toByteArray(StandardCharsets.UTF_8))
        stream.write("**Job Postings Found:** ${results.count { it.metadata["is_job_posting"] == true }}\n".toByteArray(StandardCharsets.UTF_8))
        stream.write("**Good Matches:** ${goodMatches.size}\n\n".toByteArray(StandardCharsets.UTF_8))
        stream.flush()
      } catch (e: Exception) {
        log.debug("Failed to write final summary to transcript", e)
      }
    }

    return buildString {
      appendLine("# Job Search Results")
      appendLine()
      appendLine("**Search Completed:** ${LocalDateTime.now()}")
      appendLine("**Pages Analyzed:** ${results.size}")
      appendLine("**Job Postings Found:** ${results.count { it.metadata["is_job_posting"] == true }}")
      appendLine("**Good Matches:** ${goodMatches.size}")
      appendLine()

      if (goodMatches.isEmpty()) {
        appendLine("⚠️ No strong matches found. Consider:")
        appendLine("- Broadening search criteria")
        appendLine("- Adjusting required skills")
        appendLine("- Expanding target roles")
        appendLine("- Relaxing location or work arrangement preferences")
        appendLine("- Adjusting salary expectations")
        appendLine()
        return@buildString
      }

      appendLine("## Top Matches")
      appendLine()

      goodMatches.values
        .sortedByDescending { it.match_score }
        .forEach { job ->
          appendLine("### ${job.job_title} at ${job.company}")
          appendLine()
          appendLine("**Match Score:** ${(job.match_score * 100).toInt()}%")
          appendLine("**Location:** ${job.location ?: "Not specified"}")
          job.work_arrangement?.let {
            appendLine("**Work Arrangement:** $it")
          }
          if (job.salary_min != null || job.salary_max != null) {
            val salaryRange = buildString {
              job.salary_min?.let { append("${job.salary_currency ?: "USD"} $it") }
              if (job.salary_min != null && job.salary_max != null) append(" - ")
              job.salary_max?.let { append("${job.salary_currency ?: "USD"} $it") }
            }
            appendLine("**Salary:** $salaryRange")
          }
          appendLine("**Application:** [Apply Here](${job.application_url})")
          appendLine()
          appendLine("**Compatibility:**")
          appendLine("- Skills: ${(job.match_score * 100).toInt()}%")
          appendLine("- Location: ${(job.location_score * 100).toInt()}%")
          if (job.salary_min != null || job.salary_max != null) {
            appendLine("- Salary: ${(job.salary_score * 100).toInt()}%")
          }
          appendLine("- Work Arrangement: ${(job.work_arrangement_score * 100).toInt()}%")
          appendLine()
          appendLine("**Skills Match:** ${job.skill_matches.size}/${job.skill_matches.size + job.skill_gaps.size}")
          appendLine()

          appendLine()
          appendLine(job.match_analysis.take(300) + "...")
          appendLine()
          appendLine("---")
          appendLine()
        }

      appendLine("## Next Steps")
      appendLine()
      appendLine("1. Review detailed reports in the `job_matches` directory")
      appendLine("2. Customize cover letters for each application")
      appendLine("3. Prepare for interviews by reviewing skill gaps")
      appendLine("4. Track application status")
      appendLine("5. Verify work arrangement and compensation details during screening")
      appendLine("6. Prepare questions about travel requirements and relocation assistance")
      // Log completion to transcript
      context.transcriptStream?.let { stream ->
        try {
          stream.write("\n**Job search crawling completed successfully**\n".toByteArray(StandardCharsets.UTF_8))
          stream.write("**Good matches found:** ${goodMatches.size}\n\n".toByteArray(StandardCharsets.UTF_8))
          stream.flush()
        } catch (e: Exception) {
          log.debug("Failed to write completion to transcript", e)
        }
      }
    }
  }

  override fun validateConfig(config: Any?): String? {
    return null
  }
}