package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.parserCast
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class JobMatchingStrategy : DefaultSummarizerStrategy() {

  data class JobMatchingConfig(
    @Description("User's resume/experience summary")
    val user_experience: String = "",
    @Description("Desired job titles or keywords")
    val target_roles: List<String> = listOf(),
    @Description("Required skills to match")
    val required_skills: List<String>? = null,
    @Description("Preferred locations")
    val preferred_locations: List<String>? = null,
    @Description("Minimum match score (0.0-1.0)")
    val min_match_score: Double = 0.6,
    @Description("Stop after finding N good matches")
    val target_matches: Int = 5,
    @Description("Automatically adjust match threshold based on results")
    val adaptive_threshold: Boolean = false,
    @Description("Preferred industries")
    val preferred_industries: List<String>? = null,
    @Description("Excluded companies")
    val excluded_companies: List<String>? = null,
    @Description("Salary range (min-max)")
    val salary_range: Pair<Int, Int>? = null,
    @Description("Remote work preference")
    val remote_preference: String? = null
  )


  override val description: String
    get() = "Analyzes job postings against user experience to find strong matches, generates application materials, and saves detailed reports."

  data class JobAnalysis(
    val job_title: String = "",
    val company: String = "",
    val location: String? = null,
    val application_url: String = "",
    val job_description: String = "",
    val required_skills: List<String> = listOf(),
    val preferred_skills: List<String> = listOf(),
    val match_score: Double = 0.0,
    val match_analysis: String = "",
    val skill_gaps: List<String> = listOf(),
    val skill_matches: List<String> = listOf(),
    val cover_letter: String = "",
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
      processJD(url, content, config, context, chatInterface)
    } else {
      log.debug("Page is not a job posting, using default processing: $url")
      super.processPage(url, content, context)
    }
  } catch (e: Exception) {
    val errorMsg = "Error processing page for URL: $url - ${e.message}"
    log.error(errorMsg, e)
    context.task.error(e)
    writeToTranscript(context, "\n**ERROR:** $errorMsg\n```\n${e.stackTraceToString()}\n```\n\n")
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
    writeToTranscript(context, "\n#### Job Posting Detected at $timestamp\n**URL:** $url\n\n")

    // Extract and analyze job details
    val jobAnalysis = try {
      analyzeJobMatch(url, content, config, context, chatInterface)
    } catch (e: Exception) {
      val errorMsg = "Failed to analyze job match for URL: $url - ${e.message}"
      log.error(errorMsg, e)
      context.task.error(e)
      writeToTranscript(context, "\n**ERROR:** $errorMsg\n```\n${e.stackTraceToString()}\n```\n\n")
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
        writeToTranscript(context, "\n**ERROR:** $errorMsg\n")
        // Don't throw - we still want to continue processing
      }

      // Log good match to transcript
      writeToTranscript(context, buildString {
        appendLine("\n**✅ GOOD MATCH FOUND** (Score: ${(jobAnalysis.match_score * 100).toInt()}%)")
        appendLine("- **Position:** ${jobAnalysis.job_title}")
        appendLine("- **Company:** ${jobAnalysis.company}")
        appendLine("- **Location:** ${jobAnalysis.location ?: "Not specified"}")
        appendLine("- **Total Matches Found:** ${goodMatches.size}/${config.target_matches}\n")
      })

      log.info("Good match found: ${jobAnalysis.company} - ${jobAnalysis.job_title} (Score: ${jobAnalysis.match_score})")

      // Check if we've found enough good matches
      goodMatches.size >= config.target_matches
    } else {
      // Log weak match to transcript
      writeToTranscript(context, buildString {
        appendLine("\n**⚠️ Weak Match** (Score: ${(jobAnalysis.match_score * 100).toInt()}%)")
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
      extractedLinks = if (jobAnalysis.match_score >= config.min_match_score) {
        emptyList() // Don't follow links from good matches
      } else {
        extractJobBoardLinks(content)
      },
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

    val prompt = """
            Analyze this job posting and compare it to the candidate's experience.

            CANDIDATE EXPERIENCE:
            ${config.user_experience}

            TARGET ROLES: ${config.target_roles.joinToString(", ")}
            REQUIRED SKILLS: ${config.required_skills?.joinToString(", ") ?: "Not specified"}
            ${additionalContext}

            Extract:
            1. Job title, company, location
            2. Application URL
            3. Required and preferred skills
            4. Match score (0.0-1.0) based on experience alignment
            5. Detailed match analysis
            6. Skill gaps and matches
            7. Draft a compelling cover letter (200-300 words) that incorporates the additional context and highlights relevant experience
            8. Application strategy notes
            When drafting the cover letter, pay special attention to any specific requirements, preferences, or context 
            provided in the additional context section. Tailor the letter to address these points directly.
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
    return analysis.obj.copy(application_url = url)
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
    val titleSafe = jobAnalysis.job_title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)

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
      if (context.messages.isNotEmpty()) {
        appendLine("## User Context")
        context.messages.forEach { message ->
          appendLine(message)
          appendLine()
        }
        appendLine("---")
        appendLine()
      }

      appendLine("## Position Details")
      appendLine("- **Title:** ${jobAnalysis.job_title}")
      appendLine("- **Company:** ${jobAnalysis.company}")
      appendLine("- **Location:** ${jobAnalysis.location ?: "Not specified"}")
      appendLine("- **Application URL:** [Apply Here](${jobAnalysis.application_url})")
      appendLine()

      appendLine("## Match Analysis")
      appendLine(jobAnalysis.match_analysis)
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
      appendLine("**Application:** [Apply Here](${jobAnalysis.application_url})")
      appendLine()

      if (jobAnalysis.match_score >= 0.6) {
        appendLine("✅ **Good Match** - Detailed report saved")
      } else {
        appendLine("⚠️ **Weak Match** - Consider other opportunities")
      }
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

  private fun extractJobBoardLinks(content: String): List<CrawlerAgentTask.LinkData> {
    log.debug("Extracting job board links from content")

    // Extract links that are likely to lead to more job postings
    val jobBoardPatterns = listOf(
      "careers", "jobs", "apply", "positions", "openings", "opportunities"
    )

    return try {
      val linkPattern = Pattern.compile("""\[([^]]+)]\(([^)]+)\)""")
      val matcher = linkPattern.matcher(content)
      val links = mutableListOf<CrawlerAgentTask.LinkData>()
      while (matcher.find()) {
        val linkText = matcher.group(1)
        val linkUrl = matcher.group(2)
        val isJobRelated = jobBoardPatterns.any {
          linkUrl.lowercase().contains(it) || linkText.lowercase().contains(it)
        }
        if (isJobRelated) {
          links.add(
            CrawlerAgentTask.LinkData(
              url = linkUrl,
              title = linkText,
              relevance_score = 70.0
            )
          )
        }
      }
      log.debug("Extracted ${links.size} job board links")
      links
    } catch (e: Exception) {
      log.error("Failed to extract job board links", e)
      emptyList()
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
          appendLine("**Application:** [Apply Here](${job.application_url})")
          appendLine()
          appendLine("**Skills Match:** ${job.skill_matches.size}/${job.skill_matches.size + job.skill_gaps.size}")
          appendLine()

          appendLine("<details>")
          appendLine("<summary>Quick Summary</summary>")
          appendLine()
          appendLine(job.match_analysis.take(300) + "...")
          appendLine()
          appendLine("*Full report saved to job_matches directory*")
          appendLine("</details>")
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