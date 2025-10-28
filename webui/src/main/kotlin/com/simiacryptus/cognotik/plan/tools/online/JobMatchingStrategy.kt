package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.parserCast
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class JobMatchingStrategy : DefaultSummarizerStrategy() {

  data class JobMatchingConfig(
    @Description("User's resume/experience summary")
    val user_experience: String,
    @Description("Desired job titles or keywords")
    val target_roles: List<String>,
    @Description("Required skills to match")
    val required_skills: List<String>? = null,
    @Description("Preferred locations")
    val preferred_locations: List<String>? = null,
    @Description("Minimum match score (0.0-1.0)")
    val min_match_score: Double = 0.6,
    @Description("Stop after finding N good matches")
    val target_matches: Int = 5
  )


  override val description: String
    get() = "Analyzes job postings against user experience to find strong matches, generates application materials, and saves detailed reports."

  data class JobAnalysis(
    val job_title: String,
    val company: String,
    val location: String?,
    val application_url: String,
    val job_description: String,
    val required_skills: List<String>,
    val preferred_skills: List<String>,
    val match_score: Double,
    val match_analysis: String,
    val skill_gaps: List<String>,
    val skill_matches: List<String>,
    val cover_letter: String,
    val application_notes: String
  )

  private val goodMatches = ConcurrentHashMap<String, JobAnalysis>()

  companion object {
    private val log = LoggerFactory.getLogger(JobMatchingStrategy::class.java)
  }

  override fun processPage(
    url: String,
    content: String,
    context: PageProcessingStrategy.ProcessingContext
  ): PageProcessingStrategy.PageProcessingResult {

    return try {
      val config = context.executionConfig.content_queries?.parserCast<JobMatchingConfig>(context.orchestrationConfig.parsingChatter)
        ?: throw IllegalArgumentException("Missing JobMatchingConfig")

      if (!detectJobPosting(content, config, context)) {
        super.processPage(url, content, context)
      } else {
        processJD(url, content, config, context)
      }
    } catch (e: Exception) {
      log.error("Error processing page for URL: $url", e)
      super.processPage(url, content, context)
    }
  }

  private fun processJD(
    url: String,
    content: String,
    config: JobMatchingConfig,
    context: PageProcessingStrategy.ProcessingContext
  ): PageProcessingStrategy.PageProcessingResult {
    // Extract and analyze job details
    val jobAnalysis = analyzeJobMatch(url, content, config, context)

    // If it's a good match, save detailed report
    val shouldTerminate = if (jobAnalysis.match_score >= config.min_match_score) {
      goodMatches[url] = jobAnalysis
      saveJobReport(jobAnalysis, context)

      // Check if we've found enough good matches
      goodMatches.size >= config.target_matches
    } else {
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

  private fun detectJobPosting(
    content: String,
    config: JobMatchingConfig,
    context: PageProcessingStrategy.ProcessingContext
  ): Boolean {
    val prompt = """
            Analyze if this content is a job posting/description.

            Look for:
            - Job title
            - Company name
            - Job responsibilities
            - Required qualifications
            - Application instructions

            Target roles: ${config.target_roles.joinToString(", ")}
        """.trimIndent()

    data class JobDetection(
      val is_job_posting: Boolean,
      val confidence: Double,
      val detected_title: String?
    )

    val detection = ParsedAgent(
      prompt = prompt,
      resultClass = JobDetection::class.java,
      model = context.orchestrationConfig.parsingChatter.getChildClient(context.task),
      parsingChatter = context.orchestrationConfig.parsingChatter.getChildClient(context.task)
    ).getParser().apply(content.take(5000)) // Only analyze first 5K chars for detection, using 1 pass (faster)

    return detection.is_job_posting && detection.confidence > 0.7
  }

  private fun analyzeJobMatch(
    url: String,
    content: String,
    config: JobMatchingConfig,
    context: PageProcessingStrategy.ProcessingContext
  ): JobAnalysis {
    val prompt = """
            Analyze this job posting and compare it to the candidate's experience.

            CANDIDATE EXPERIENCE:
            ${config.user_experience}

            TARGET ROLES: ${config.target_roles.joinToString(", ")}
            REQUIRED SKILLS: ${config.required_skills?.joinToString(", ") ?: "Not specified"}

            Extract:
            1. Job title, company, location
            2. Application URL
            3. Required and preferred skills
            4. Match score (0.0-1.0) based on experience alignment
            5. Detailed match analysis
            6. Skill gaps and matches
            7. Draft a compelling cover letter (200-300 words)
            8. Application strategy notes
        """.trimIndent()

    val analysis = ParsedAgent(
      prompt = prompt,
      resultClass = JobAnalysis::class.java,
      model = context.orchestrationConfig.parsingChatter.getChildClient(context.task),
      parsingChatter = context.orchestrationConfig.parsingChatter.getChildClient(context.task)
    ).answer(listOf(content))

    return analysis.obj.copy(application_url = url)
  }

  private fun saveJobReport(
    jobAnalysis: JobAnalysis,
    context: PageProcessingStrategy.ProcessingContext
  ) {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val companySafe = jobAnalysis.company.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)
    val titleSafe = jobAnalysis.job_title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)

    val reportDir = File(context.webSearchDir, "job_matches")
    reportDir.mkdirs()

    val reportFile = File(reportDir, "${companySafe}_${titleSafe}_${timestamp}.md")

    val report = buildString {
      appendLine("# Job Application Report")
      appendLine()
      appendLine("**Generated:** ${LocalDateTime.now()}")
      appendLine("**Match Score:** ${jobAnalysis.match_score}")
      appendLine()

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

    reportFile.writeText(report)

    log.info("Saved job report: ${reportFile.absolutePath}")
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
    // Extract links that are likely to lead to more job postings
    val jobBoardPatterns = listOf(
      "careers", "jobs", "apply", "positions", "openings", "opportunities"
    )

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
    return links
  }

  override fun shouldContinueCrawling(
    currentResults: List<PageProcessingStrategy.PageProcessingResult>,
    context: PageProcessingStrategy.ProcessingContext
  ): PageProcessingStrategy.ContinuationDecision {
    val anyTermination = currentResults.any { it.shouldTerminate }

    return PageProcessingStrategy.ContinuationDecision(
      shouldContinue = !anyTermination && context.processedCount.get() < context.maxPages,
      reason = if (anyTermination) {
        currentResults.first { it.shouldTerminate }.terminationReason ?: "Target matches found"
      } else {
        "Continue searching for job matches (${goodMatches.size} found so far)"
      }
    )
  }

  override fun generateFinalOutput(
    results: List<PageProcessingStrategy.PageProcessingResult>,
    context: PageProcessingStrategy.ProcessingContext
  ): String {
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
    }
  }

  override fun validateConfig(config: Any?): String? {
    return null
  }
}
