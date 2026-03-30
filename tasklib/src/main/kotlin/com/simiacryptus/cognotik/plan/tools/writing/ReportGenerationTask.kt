package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.*
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ReportGenerationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: ReportGenerationTaskExecutionConfigData?
) : AbstractTask<ReportGenerationTask.ReportGenerationTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class ReportGenerationTaskExecutionConfigData(
    @Description("The subject or topic of the report")
    var report_topic: String? = null,

    @Description("Type of report (e.g., 'status_update', 'quarterly_review', 'incident_report', 'performance_analysis', 'market_research')")
    var report_type: String = "status_update",

    @Description("Target audience for the report (e.g., 'executives', 'team_members', 'stakeholders', 'board_of_directors')")
    var target_audience: String = "executives",

    @Description("Time period covered by the report (e.g., 'Q1 2024', 'January 2024', 'Last 30 days')")
    var time_period: String? = null,

    @Description("Key metrics or KPIs to include in the report")
    var key_metrics: List<String>? = null,

    @Description("Data points or statistics to analyze")
    var data_points: Map<String, Any>? = null,

    @Description("Whether to include trend analysis comparing to previous periods")
    var include_trend_analysis: Boolean = true,

    @Description("Whether to include data visualization descriptions")
    var include_visualizations: Boolean = true,

    @Description("Whether to include executive summary/dashboard")
    var include_executive_summary: Boolean = true,

    @Description("Whether to include actionable recommendations")
    var include_recommendations: Boolean = true,

    @Description("Whether to include comparative analysis (benchmarks, competitors, previous periods)")
    var include_comparative_analysis: Boolean = true,

    @Description("Whether to include risk assessment or challenges section")
    var include_risk_assessment: Boolean = true,

    @Description("Tone of the report (e.g., 'formal', 'professional', 'analytical', 'conversational')")
    var tone: String = "professional",

    @Description("Target word count for the complete report")
    var target_word_count: Int = 2000,

    @Description("Number of revision passes for quality improvement")
    var revision_passes: Int = 1,

    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    var related_files: List<String>? = null,


    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = ReportGeneration.name,
    task_description = task_description ?: "Generate report on: '$report_topic'",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (report_topic.isNullOrBlank()) {
        return "report_topic must not be null or blank"
      }
      if (target_word_count <= 0) {
        return "target_word_count must be positive, got: $target_word_count"
      }
      if (revision_passes < 0 || revision_passes > 5) {
        return "revision_passes must be between 0 and 5, got: $revision_passes"
      }
      val validReportTypes = setOf(
        "status_update", "quarterly_review", "incident_report",
        "performance_analysis", "market_research", "post_mortem",
        "financial_report", "project_summary"
      )
      if (report_type.lowercase() !in validReportTypes) {
        return "report_type must be one of: ${validReportTypes.joinToString(", ")}, got: $report_type"
      }
      val validTones = setOf("formal", "professional", "analytical", "conversational", "technical")
      if (tone.lowercase() !in validTones) {
        return "tone must be one of: ${validTones.joinToString(", ")}, got: $tone"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class ReportOutline(
    @Description("Report title")
    var title: String = "",
    @Description("Executive summary or key highlights")
    var executive_summary: String = "",
    @Description("Main sections of the report")
    var sections: List<ReportSection> = emptyList(),
    @Description("Key findings or takeaways")
    var key_findings: List<String> = emptyList(),
    @Description("Recommended visualizations")
    var visualization_suggestions: List<VisualizationSuggestion> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) return "title must not be blank"
      if (sections.isEmpty()) return "sections must not be empty"
      return ValidatedObject.validateFields(this)
    }
  }

  data class ReportSection(
    @Description("Section number")
    var section_number: Int = 1,
    @Description("Section title")
    var title: String = "",
    @Description("Section purpose or focus")
    var purpose: String = "",
    @Description("Key points to cover")
    var key_points: List<String> = emptyList(),
    @Description("Metrics or data to include")
    var metrics: List<String> = emptyList(),
    @Description("Estimated word count")
    var estimated_word_count: Int = 0
  ) : ValidatedObject {
    override fun validate(): String? {
      if (section_number < 1) return "section_number must be positive"
      if (title.isBlank()) return "title must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class VisualizationSuggestion(
    @Description("Type of visualization (e.g., 'line_chart', 'bar_chart', 'pie_chart', 'table', 'heatmap')")
    var type: String = "",
    @Description("What data to visualize")
    var data_description: String = "",
    @Description("Purpose of this visualization")
    var purpose: String = "",
    @Description("Suggested placement in report")
    var placement: String = ""
  ) : ValidatedObject

  data class DataAnalysis(
    @Description("Metric or data point being analyzed")
    var metric_name: String = "",
    @Description("Current value or status")
    var current_value: String = "",
    @Description("Comparison to previous period")
    var comparison: String = "",
    @Description("Trend direction (e.g., 'increasing', 'decreasing', 'stable')")
    var trend: String = "",
    @Description("Interpretation of the data")
    var interpretation: String = "",
    @Description("Significance level (e.g., 'critical', 'important', 'notable', 'minor')")
    var significance: String = ""
  ) : ValidatedObject

  data class DataAnalyses(
    var analyses: List<DataAnalysis> = emptyList()
  ) : ValidatedObject

  data class RecommendationSet(
    @Description("Actionable recommendations")
    var recommendations: List<Recommendation> = emptyList()
  ) : ValidatedObject

  data class Recommendation(
    @Description("Priority level (e.g., 'high', 'medium', 'low')")
    var priority: String = "",
    @Description("The recommended action")
    var action: String = "",
    @Description("Rationale for this recommendation")
    var rationale: String = "",
    @Description("Expected impact or benefit")
    var expected_impact: String = "",
    @Description("Implementation timeline")
    var timeline: String = "",
    @Description("Resources required")
    var resources_required: List<String> = emptyList()
  ) : ValidatedObject

  data class RiskAssessment(
    @Description("Identified risks or challenges")
    var risks: List<Risk> = emptyList()
  ) : ValidatedObject

  data class Risk(
    @Description("Risk category (e.g., 'operational', 'financial', 'strategic', 'technical')")
    var category: String = "",
    @Description("Description of the risk")
    var description: String = "",
    @Description("Likelihood (e.g., 'high', 'medium', 'low')")
    var likelihood: String = "",
    @Description("Potential impact (e.g., 'high', 'medium', 'low')")
    var impact: String = "",
    @Description("Mitigation strategies")
    var mitigation: String = ""
  ) : ValidatedObject

  data class GeneratedSection(
    @Description("Section number")
    var section_number: Int = 1,
    @Description("Section title")
    var title: String = "",
    @Description("Section content")
    var content: String = "",
    @Description("Word count")
    var word_count: Int = 0,
    @Description("Key insights from this section")
    var key_insights: List<String> = emptyList()
  ) : ValidatedObject

  override fun promptSegment(): String {
    return """
ReportGeneration - Generate comprehensive business reports with data analysis and recommendations
  ** Specify the report topic and type (status update, quarterly review, incident report, etc.)
  ** Define target audience and time period
  ** Provide key metrics, KPIs, and data points to analyze
  ** Enable trend analysis, visualizations, and comparative analysis
  ** Include executive summary/dashboard for quick insights
  ** Generate actionable recommendations based on findings
  ** Assess risks and challenges
  ** Produces complete, professional report with clear structure
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
    val reportTopic = executionConfig?.report_topic
    log.info("Starting ReportGenerationTask. Topic: '$reportTopic'")

    val transcript = task.newUserFileStream(transcriptFile())
    task.ui.pool.submit {
      try {
        // Read input from messages parameter
        val messageContext = messages.filter { it.isNotBlank() }.joinToString("\n\n")
        // Load input files if specified
        val inputFileContent = getInputFileCode()
        val fullContext = listOfNotNull(messageContext, inputFileContent).filter { it.isNotBlank() }
          .joinToString("\n\n---\n\n")

        // Validate configuration
        val executionConfig = executionConfig ?: return@submit
        executionConfig?.validate()?.let { validationError ->
          log.error("Configuration validation failed: $validationError")
          task.safeComplete("CONFIGURATION ERROR: $validationError", log)
          task.error(ValidatedObject.ValidationError(validationError, executionConfig))
          resultFn("CONFIGURATION ERROR: $validationError")
          return@submit
        }

        if (reportTopic.isNullOrBlank()) {
          log.error("No report topic specified")
          task.safeComplete("CONFIGURATION ERROR: No report topic specified", log)
          resultFn("CONFIGURATION ERROR: No report topic specified")
          return@submit
        }

        val api = defaultSmart ?: return@submit

        val tabs = createTabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
          appendLine("# Report Generation")
          appendLine()
          appendLine("**Topic:** $reportTopic")
          appendLine("**Type:** ${executionConfig.report_type}")
          appendLine()
          appendLine("## Configuration")
          appendLine("- Report Type: ${executionConfig.report_type}")
          appendLine("- Target Audience: ${executionConfig.target_audience}")
          appendLine("- Time Period: ${executionConfig.time_period ?: "Not specified"}")
          appendLine("- Target Word Count: ${executionConfig.target_word_count}")
          appendLine("- Tone: ${executionConfig.tone}")
          appendLine()
          appendLine("## Features")
          appendLine("- Executive Summary: ${if (executionConfig.include_executive_summary) "✓" else "✗"}")
          appendLine("- Trend Analysis: ${if (executionConfig.include_trend_analysis) "✓" else "✗"}")
          appendLine("- Visualizations: ${if (executionConfig.include_visualizations) "✓" else "✗"}")
          appendLine("- Recommendations: ${if (executionConfig.include_recommendations) "✓" else "✗"}")
          appendLine("- Comparative Analysis: ${if (executionConfig.include_comparative_analysis) "✓" else "✗"}")
          appendLine("- Risk Assessment: ${if (executionConfig.include_risk_assessment) "✓" else "✗"}")
          appendLine()
          appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## Progress")
          appendLine()
          appendLine("### Phase 1: Data Analysis")
          appendLine("*Analyzing metrics and data points...*")
        }
        transcript?.write(overviewContent.toByteArray())
        overviewTask.add(overviewContent.renderMarkdown(true))
        task.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# ${executionConfig.report_type.replace("_", " ").capitalize()} Report: $reportTopic\n\n")

        try {
          val priorContext = getPriorCode(agent.executionState)
          val contextFiles = getRelatedContextFiles()

          if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
            log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
            val contextTask = tabs.newTask("Data Sources")

            contextTask.add("# Data Sources & Context".renderMarkdown(true))
            if (fullContext.isNotBlank()) {
              contextTask.expandable("Input Context", fullContext.truncateForDisplay(3000).renderMarkdown(true))
            }
            if (priorContext.isNotBlank()) {
              contextTask.expandable("Prior Context", priorContext.truncateForDisplay(2000).renderMarkdown(true))
            }
            if (contextFiles.isNotBlank()) {
              contextTask.expandable("Related Files", contextFiles.truncateForDisplay(2000).renderMarkdown(true))
            }
            contextTask.complete()

            val contextContent = buildString {
              appendLine("# Data Sources & Context")
              if (fullContext.isNotBlank()) appendLine("## Input Context\n${fullContext.truncateForDisplay(3000)}\n")
              if (priorContext.isNotBlank()) appendLine("## Prior Context\n${priorContext.truncateForDisplay(2000)}\n")
              if (contextFiles.isNotBlank()) appendLine("## Related Files\n${contextFiles.truncateForDisplay(2000)}")
            }
            transcript?.write(contextContent.toByteArray())

            task.update()
          }

          // Phase 1: Data Analysis
          log.info("Phase 1: Analyzing data and metrics")
          val dataAnalysisTask = tabs.newTask("Data Analysis")

          dataAnalysisTask.add(
            buildString {
              appendLine("# Data Analysis")
              appendLine()
              appendLine()
              appendLine("**Status:** Analyzing metrics and trends...")
              appendLine()
            }.renderMarkdown(true)
          )
          task.update()

          val metricsContext = buildString {
            if (!executionConfig.key_metrics.isNullOrEmpty()) {
              appendLine("Key Metrics to Analyze:")
              executionConfig.key_metrics?.forEach { metric ->
                appendLine("- $metric")
              }
              appendLine()
            }
            if (!executionConfig.data_points.isNullOrEmpty()) {
              appendLine("Data Points:")
              executionConfig.data_points?.forEach { (key, value) ->
                appendLine("- $key: $value")
              }
              appendLine()
            }
          }

          val dataAnalysisAgent = ParsedAgent(
            resultClass = DataAnalyses::class.java,
            prompt = """
${if (fullContext.isNotBlank()) "Input Context:\n${fullContext.truncateForDisplay(3000)}\n" else ""}
You are a data analyst expert. Analyze the provided metrics and data points for this report.

Report Topic: $reportTopic
Report Type: ${executionConfig.report_type}
Time Period: ${executionConfig.time_period ?: "Current period"}

$metricsContext

${if (priorContext.isNotBlank()) "Additional Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Data Sources:\n${contextFiles.truncateForDisplay(3000)}\n" else ""}

For each key metric or data point, provide:
- Current value or status
- Comparison to previous period (if applicable)
- Trend direction (increasing, decreasing, stable)
- Interpretation of what the data means
- Significance level (critical, important, notable, minor)

${if (executionConfig.include_trend_analysis) "Include trend analysis comparing to historical data where possible." else ""}
${if (executionConfig.include_comparative_analysis) "Include comparative analysis against benchmarks or competitors where relevant." else ""}

Focus on insights that matter to ${executionConfig.target_audience}.
Be specific with numbers and percentages where available.
          """.trimIndent(),
            model = api,
            temperature = 0.6,
            parsingChatter = defaultFast
          )

          val dataAnalyses = dataAnalysisAgent.answer(listOf("Analyze data")).obj.analyses
          log.info("Analyzed ${dataAnalyses.size} metrics")

          val dataAnalysisContent = buildString {
            appendLine()
            appendLine("## Key Metrics Analysis")
            appendLine()
            dataAnalyses.forEach { analysis ->
              val significanceIcon = when (analysis.significance.lowercase()) {
                "critical" -> "🔴"
                "important" -> "🟡"
                "notable" -> "🔵"
                else -> "⚪"
              }
              appendLine("### $significanceIcon ${analysis.metric_name}")
              appendLine()
              appendLine("**Current Value:** ${analysis.current_value}")
              appendLine()
              if (analysis.comparison.isNotBlank()) {
                appendLine("**Comparison:** ${analysis.comparison}")
                appendLine()
              }
              appendLine("**Trend:** ${analysis.trend}")
              appendLine()
              appendLine("**Analysis:** ${analysis.interpretation}")
              appendLine()
              appendLine("---")
              appendLine()
            }
            appendLine("**Status:** ✅ Complete")
          }
          transcript?.write(dataAnalysisContent.toByteArray())
          dataAnalysisTask.add(dataAnalysisContent.renderMarkdown(true))
          dataAnalysisTask.complete()
          task.update()

          overviewTask.add("✅ Phase 1 Complete: ${dataAnalyses.size} metrics analyzed\n".renderMarkdown(true))
          overviewTask.add("\n### Phase 2: Report Structure\n*Creating report outline...*\n".renderMarkdown(true))
          task.update()

          // Phase 2: Create Report Outline
          log.info("Phase 2: Creating report outline")
          val outlineTask = tabs.newTask("Outline")

          outlineTask.add(
            buildString {
              appendLine("# Report Outline")
              appendLine()
              appendLine()
              appendLine("**Status:** Structuring report sections...")
              appendLine()
            }.renderMarkdown(true)
          )
          task.update()

          val outlineAgent = ParsedAgent(
            resultClass = ReportOutline::class.java,
            prompt = """
You are a business report writing expert. Create a detailed outline for this report.

Report Topic: $reportTopic
Report Type: ${executionConfig.report_type}
Target Audience: ${executionConfig.target_audience}
Time Period: ${executionConfig.time_period ?: "Current period"}
Target Word Count: ${executionConfig.target_word_count}

Data Analysis Summary:
${dataAnalyses.take(5).joinToString("\n") { "- ${it.metric_name}: ${it.interpretation.take(100)}" }}

Create an outline with:
1. A compelling title
${if (executionConfig.include_executive_summary) "2. Executive summary highlighting key findings (150-200 words)" else ""}
3. 4-6 main sections covering:
   - Current status/performance
   - Key findings from data analysis
   ${if (executionConfig.include_trend_analysis) "- Trend analysis and patterns" else ""}
   ${if (executionConfig.include_comparative_analysis) "- Comparative analysis" else ""}
   ${if (executionConfig.include_risk_assessment) "- Challenges and risks" else ""}
   ${if (executionConfig.include_recommendations) "- Recommendations and next steps" else ""}

For each section, specify:
- Section title and purpose
- Key points to cover
- Relevant metrics to include
- Estimated word count

${
              if (executionConfig.include_visualizations) {
                """Also suggest 3-5 data visualizations:
- Type of chart/graph (line chart, bar chart, pie chart, table, etc.)
- What data to visualize
- Purpose of the visualization
- Where to place it in the report"""
              } else ""
            }

Structure should be appropriate for ${executionConfig.target_audience} with a ${executionConfig.tone} tone.
          """.trimIndent(),
            model = api,
            temperature = 0.7,
            parsingChatter = defaultFast
          )

          val outline = outlineAgent.answer(listOf("Create outline")).obj
          log.info("Created outline with ${outline.sections.size} sections")

          val outlineContent = buildString {
            appendLine("## ${outline.title}")
            appendLine()
            if (outline.executive_summary.isNotBlank()) {
              appendLine("### Executive Summary")
              appendLine(outline.executive_summary)
              appendLine()
              appendLine("---")
              appendLine()
            }
            appendLine("### Report Sections")
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
              if (section.metrics.isNotEmpty()) {
                appendLine("**Metrics:** ${section.metrics.joinToString(", ")}")
                appendLine()
              }
              appendLine("**Est. Words:** ${section.estimated_word_count}")
              appendLine()
              appendLine("---")
              appendLine()
            }
            if (outline.visualization_suggestions.isNotEmpty()) {
              appendLine("### Suggested Visualizations")
              outline.visualization_suggestions.forEach { viz ->
                appendLine("- **${viz.type.replace("_", " ").capitalize()}:** ${viz.data_description}")
                appendLine("  - Purpose: ${viz.purpose}")
                appendLine("  - Placement: ${viz.placement}")
                appendLine()
              }
              appendLine("---")
              appendLine()
            }
            appendLine("**Status:** ✅ Complete")
          }
          transcript?.write(outlineContent.toByteArray())
          outlineTask.add(outlineContent.renderMarkdown(true))
          outlineTask.complete()
          task.update()

          overviewTask.add(
            "✅ Phase 2 Complete: Outline created (${outline.sections.size} sections)\n".renderMarkdown(
              true
            )
          )
          overviewTask.add("\n### Phase 3: Content Generation\n*Writing report sections...*\n".renderMarkdown(true))
          task.update()

          // Phase 3: Generate Each Section
          log.info("Phase 3: Generating report sections")
          val generatedSections = mutableListOf<GeneratedSection>()
          var cumulativeWordCount = 0

          outline.sections.forEachIndexed { index, sectionOutline ->
            log.info("Generating section ${index + 1}/${outline.sections.size}: ${sectionOutline.title}")

            overviewTask.add(
              "- Section ${sectionOutline.section_number}: ${sectionOutline.title} ".renderMarkdown(
                true
              )
            )
            task.update()

            val sectionTask = tabs.newTask("Section ${sectionOutline.section_number}")

            sectionTask.add(
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
                  appendLine("Key insights: ${prevSection.key_insights.joinToString("; ")}")
                  appendLine()
                }
              }
            } else {
              "This is the first section."
            }

            // Find relevant data analyses for this section
            val relevantAnalyses = dataAnalyses.filter { analysis ->
              sectionOutline.metrics.any { metric ->
                analysis.metric_name.contains(metric, ignoreCase = true) ||
                    metric.contains(analysis.metric_name, ignoreCase = true)
              }
            }

            val sectionAgent = ParsedAgent(
              resultClass = GeneratedSection::class.java,
              prompt = """
You are a professional business report writer. Write Section ${sectionOutline.section_number} of the report.

Report Title: ${outline.title}
Report Type: ${executionConfig.report_type}
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}

Section Details:
- Title: ${sectionOutline.title}
- Purpose: ${sectionOutline.purpose}
- Target Word Count: ${sectionOutline.estimated_word_count}

Key Points to Cover:
${sectionOutline.key_points.joinToString("\n") { "- $it" }}

Relevant Data Analysis:
${relevantAnalyses.joinToString("\n") { "- ${it.metric_name}: ${it.interpretation}" }}

$previousContext

Write a complete section that:
1. Opens with a clear topic statement
2. Presents data and findings clearly
3. Uses specific numbers and metrics
${
                if (executionConfig.include_visualizations && outline.visualization_suggestions.any {
                    it.placement.contains(
                      sectionOutline.title,
                      ignoreCase = true
                    )
                  }) {
                  "4. References suggested visualizations with [Chart: description] placeholders"
                } else ""
              }
5. Provides interpretation and context
6. Connects to the overall report narrative
7. Maintains a ${executionConfig.tone} tone appropriate for ${executionConfig.target_audience}

After writing, provide:
- The section content
- Actual word count
- 3-5 key insights from this section

Be specific, data-driven, and actionable.
          """.trimIndent(),
              model = api,
              temperature = 0.7,
              parsingChatter = defaultFast
            )

            var generatedSection = sectionAgent.answer(listOf("Write section")).obj
            generatedSections.add(generatedSection)
            cumulativeWordCount += generatedSection.word_count

            sectionTask.add(
              buildString {
                appendLine("## ${sectionOutline.title}")
                appendLine()
                appendLine(generatedSection.content)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Word Count:** ${generatedSection.word_count}")
                appendLine()
                appendLine("**Key Insights:**")
                generatedSection.key_insights.forEach { insight ->
                  appendLine("- $insight")
                }
                appendLine()
                appendLine("**Status:** ✅ Complete")
              }.renderMarkdown(true)
            )
            transcript?.write(sectionTask.toString().toByteArray())
            sectionTask.complete()
            task.update()

            resultBuilder.append("## ${sectionOutline.title}\n\n")
            resultBuilder.append(generatedSection.content)
            resultBuilder.append("\n\n")

            overviewTask.add("✅ (${generatedSection.word_count} words)\n".renderMarkdown(true))
            task.update()
          }

          overviewTask.add("✅ Phase 3 Complete: All sections written\n".renderMarkdown(true))

          // Phase 4: Recommendations (if enabled)
          if (executionConfig.include_recommendations) {
            overviewTask.add(
              "\n### Phase 4: Recommendations\n*Generating actionable recommendations...*\n".renderMarkdown(
                true
              )
            )
            task.update()

            log.info("Phase 4: Generating recommendations")
            val recommendationsTask = tabs.newTask("Recommendations")

            recommendationsTask.add(
              buildString {
                appendLine("# Recommendations")
                appendLine()
                appendLine("**Status:** Generating actionable recommendations...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()

            val recommendationAgent = ParsedAgent(
              resultClass = RecommendationSet::class.java,
              prompt = """
You are a strategic business advisor. Based on the report findings, provide actionable recommendations.

Report Topic: $reportTopic
Report Type: ${executionConfig.report_type}
Target Audience: ${executionConfig.target_audience}

Key Findings:
${outline.key_findings.joinToString("\n") { "- $it" }}

Data Analysis Summary:
${dataAnalyses.take(5).joinToString("\n") { "- ${it.metric_name}: ${it.interpretation}" }}

Section Insights:
${generatedSections.flatMap { it.key_insights }.take(10).joinToString("\n") { "- $it" }}

Provide 3-5 prioritized recommendations that:
- Are specific and actionable
- Address the key findings and challenges
- Are realistic and achievable
- Have clear expected impact
- Include implementation timeline
- Specify required resources

For each recommendation, provide:
- Priority level (high, medium, low)
- The specific action to take
- Rationale based on the data
- Expected impact or benefit
- Suggested timeline
- Resources needed

Tailor recommendations to ${executionConfig.target_audience}.
          """.trimIndent(),
              model = api,
              temperature = 0.7,
              parsingChatter = defaultFast
            )

            val recommendations = recommendationAgent.answer(listOf("Generate recommendations")).obj.recommendations
            log.info("Generated ${recommendations.size} recommendations")

            val recommendationsContent = buildString {
              appendLine("## Actionable Recommendations")
              appendLine()
              recommendations.sortedByDescending {
                when (it.priority.lowercase()) {
                  "high" -> 3
                  "medium" -> 2
                  else -> 1
                }
              }.forEach { rec ->
                val priorityIcon = when (rec.priority.lowercase()) {
                  "high" -> "🔴"
                  "medium" -> "🟡"
                  else -> "🟢"
                }
                appendLine("### $priorityIcon ${rec.action}")
                appendLine()
                appendLine("**Priority:** ${rec.priority}")
                appendLine()
                appendLine("**Rationale:** ${rec.rationale}")
                appendLine()
                appendLine("**Expected Impact:** ${rec.expected_impact}")
                appendLine()
                appendLine("**Timeline:** ${rec.timeline}")
                appendLine()
                if (rec.resources_required.isNotEmpty()) {
                  appendLine("**Resources Required:**")
                  rec.resources_required.forEach { resource ->
                    appendLine("- $resource")
                  }
                  appendLine()
                }
                appendLine("---")
                appendLine()
              }
              appendLine("**Status:** ✅ Complete")
            }
            recommendationsTask.add(recommendationsContent.renderMarkdown(true))
            transcript?.write(recommendationsContent.toByteArray())
            recommendationsTask.complete()
            task.update()

            resultBuilder.append("## Recommendations\n\n")
            recommendations.forEach { rec ->
              resultBuilder.append("### ${rec.action}\n")
              resultBuilder.append("**Priority:** ${rec.priority} | **Timeline:** ${rec.timeline}\n\n")
              resultBuilder.append("${rec.rationale}\n\n")
              resultBuilder.append("**Expected Impact:** ${rec.expected_impact}\n\n")
            }

            overviewTask.add(
              "✅ Phase 4 Complete: ${recommendations.size} recommendations generated\n".renderMarkdown(
                true
              )
            )
          }

          // Phase 5: Risk Assessment (if enabled)
          if (executionConfig.include_risk_assessment) {
            overviewTask.add(
              "\n### Phase 5: Risk Assessment\n*Identifying risks and challenges...*\n".renderMarkdown(
                true
              )
            )
            task.update()

            log.info("Phase 5: Generating risk assessment")
            val riskTask = tabs.newTask("Risk Assessment")

            riskTask.add(
              buildString {
                appendLine("# Risk Assessment")
                appendLine()
                appendLine("**Status:** Analyzing risks and challenges...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()

            val riskAgent = ParsedAgent(
              resultClass = RiskAssessment::class.java,
              prompt = """
You are a risk management expert. Identify and assess risks based on the report findings.

Report Topic: $reportTopic
Report Type: ${executionConfig.report_type}

Key Findings:
${outline.key_findings.joinToString("\n") { "- $it" }}

Data Analysis:
${
                dataAnalyses.filter { it.significance.lowercase() in setOf("critical", "important") }
                  .joinToString("\n") { "- ${it.metric_name}: ${it.interpretation}" }
              }

Identify 3-5 key risks or challenges, including:
- Operational risks
- Financial risks
- Strategic risks
- Technical risks (if applicable)

For each risk, provide:
- Category (operational, financial, strategic, technical)
- Clear description of the risk
- Likelihood (high, medium, low)
- Potential impact (high, medium, low)
- Mitigation strategies

Be realistic and specific. Focus on risks that ${executionConfig.target_audience} should be aware of.
          """.trimIndent(),
              model = api,
              temperature = 0.6,
              parsingChatter = defaultFast
            )

            val riskAssessment = riskAgent.answer(listOf("Assess risks")).obj.risks
            log.info("Identified ${riskAssessment.size} risks")

            val riskContent = buildString {
              appendLine("## Identified Risks & Challenges")
              appendLine()
              riskAssessment.sortedByDescending {
                val likelihoodScore = when (it.likelihood.lowercase()) {
                  "high" -> 3
                  "medium" -> 2
                  else -> 1
                }
                val impactScore = when (it.impact.lowercase()) {
                  "high" -> 3
                  "medium" -> 2
                  else -> 1
                }
                likelihoodScore * impactScore
              }.forEach { risk ->
                val riskLevel = when {
                  risk.likelihood.lowercase() == "high" && risk.impact.lowercase() == "high" -> "🔴 Critical"
                  risk.likelihood.lowercase() == "high" || risk.impact.lowercase() == "high" -> "🟡 Significant"
                  else -> "🟢 Moderate"
                }
                appendLine("### $riskLevel - ${risk.category.capitalize()} Risk")
                appendLine()
                appendLine("**Description:** ${risk.description}")
                appendLine()
                appendLine("**Likelihood:** ${risk.likelihood} | **Impact:** ${risk.impact}")
                appendLine()
                appendLine("**Mitigation:** ${risk.mitigation}")
                appendLine()
                appendLine("---")
                appendLine()
              }
              appendLine("**Status:** ✅ Complete")
            }
            riskTask.add(riskContent.renderMarkdown(true))
            transcript?.write(riskContent.toByteArray())
            riskTask.complete()
            task.update()

            resultBuilder.append("## Risk Assessment\n\n")
            riskAssessment.forEach { risk ->
              resultBuilder.append("### ${risk.category.capitalize()} Risk: ${risk.description.take(100)}\n")
              resultBuilder.append("**Likelihood:** ${risk.likelihood} | **Impact:** ${risk.impact}\n\n")
              resultBuilder.append("**Mitigation:** ${risk.mitigation}\n\n")
            }

            overviewTask.add("✅ Phase 5 Complete: ${riskAssessment.size} risks identified\n".renderMarkdown(true))
          }

          // Phase 6: Revision (if enabled)
          if (executionConfig.revision_passes > 0) {
            overviewTask.add("\n### Phase 6: Revision\n*Refining and polishing report...*\n".renderMarkdown(true))
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

            val fullReport = resultBuilder.toString()

            repeat(executionConfig.revision_passes) { passNum ->
              log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")

              val revisionAgent = ChatAgent(
                prompt = """
You are an expert business report editor. Review and improve this report.

Current Report:
$fullReport

Focus on:
1. Clarity and conciseness
2. Data presentation and interpretation
3. Logical flow between sections
4. Consistency in tone (${executionConfig.tone})
5. Actionability of recommendations
6. Professional formatting and structure
7. Appropriateness for ${executionConfig.target_audience}

Maintain:
- All key data points and metrics
- The core findings and recommendations
- Approximate word count ($cumulativeWordCount words)
- The ${executionConfig.tone} tone

Provide the complete revised report.
            """.trimIndent(),
                model = api,
                temperature = 0.6
              )

              val revisedReport = revisionAgent.answer(listOf("Revise the report"))
              resultBuilder.clear()
              resultBuilder.append(revisedReport)

              revisionTask.add(
                buildString {
                  appendLine("## Revision Pass ${passNum + 1}")
                  appendLine()
                  appendLine("✅ Complete")
                  appendLine()
                }.renderMarkdown(true)
              )
              transcript?.write("## Revision Pass ${passNum + 1}\n\n✅ Complete\n\n".toByteArray())
              task.update()
            }
            revisionTask.complete()

            overviewTask.add(
              "✅ Phase 6 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown(
                true
              )
            )
          }

          // Phase 7: Final Assembly
          overviewTask.add("\n### Phase 7: Final Assembly\n*Compiling complete report...*\n".renderMarkdown(true))
          task.update()

          log.info("Phase 7: Assembling final report")
          val finalTask = tabs.newTask("Complete Report")

          val finalReport = buildString {
            appendLine("# ${outline.title}")
            appendLine()
            appendLine("**Report Type:** ${executionConfig.report_type.replace("_", " ").capitalize()}")
            appendLine()
            appendLine("**Period:** ${executionConfig.time_period ?: "Current"}")
            appendLine()
            appendLine("**Prepared for:** ${executionConfig.target_audience.capitalize()}")
            appendLine()
            appendLine("**Date:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}")
            appendLine()
            appendLine("---")
            appendLine()
            if (executionConfig.include_executive_summary && outline.executive_summary.isNotBlank()) {
              appendLine("## Executive Summary")
              appendLine()
              appendLine(outline.executive_summary)
              appendLine()
              appendLine("### Key Findings")
              outline.key_findings.forEach { finding ->
                appendLine("- $finding")
              }
              appendLine()
              appendLine("---")
              appendLine()
            }
            appendLine(resultBuilder.toString())
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("**Total Word Count:** $cumulativeWordCount")
            appendLine()
            appendLine(
              "**Report Generated:** ${
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
              }"
            )
          }

          finalTask.add(finalReport.renderMarkdown(true))
          // Save report to file and provide download link
          val reportFileName = "report_${System.currentTimeMillis()}.md"
          val reportUrl = task.saveFile("reports/$reportFileName", finalReport.toByteArray())
          finalTask.add("<div class='mt-3'><a href='$reportUrl' class='btn btn-primary' target='_blank'>Download Report (Markdown)</a></div>")
          finalTask.complete()
          transcript?.write(finalReport.toByteArray())
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
              appendLine("- Metrics Analyzed: ${dataAnalyses.size}")
              if (executionConfig.include_visualizations) {
                appendLine("- Visualizations Suggested: ${outline.visualization_suggestions.size}")
              }
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
          transcript?.write(overviewTask.toString().toByteArray())
          overviewTask.complete()
          task.update()

          // Concise summary for resultFn
          val finalResult = buildString {
            appendLine("# Report Generation Summary: ${outline.title}")
            appendLine()
            appendLine(
              "A complete ${
                executionConfig.report_type.replace(
                  "_",
                  " "
                )
              } report of **$cumulativeWordCount words** was generated in **${totalTime / 1000.0}s**."
            )
            appendLine()
            appendLine("**Key Highlights:**")
            appendLine("- ${dataAnalyses.size} metrics analyzed")
            appendLine("- ${generatedSections.size} sections written")
            if (executionConfig.include_recommendations) {
              appendLine("- Actionable recommendations provided")
            }
            if (executionConfig.include_risk_assessment) {
              appendLine("- Risk assessment completed")
            }
            appendLine()
            appendLine("> The full report is available in the Complete Report tab for detailed review.")
          }

          log.info("ReportGenerationTask completed: words=$cumulativeWordCount, sections=${generatedSections.size}, time=${totalTime}ms")

          transcript?.write("\n\n---\n\n# Final Result\n\n${finalResult}".toByteArray())

          task.complete("Report generation complete: $cumulativeWordCount words in ${totalTime / 1000}s")
          resultFn(finalResult)

        } catch (e: Exception) {
          log.error("Error during report generation", e)
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
          transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
          task.update()

          val errorOutput = buildString {
            appendLine("# Error in Report Generation")
            appendLine()
            appendLine("**Topic:** $reportTopic")
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
      } finally {
        transcript?.close()
      }
    }
  }

  private fun getInputFileCode() = (executionConfig?.related_files ?: listOf())
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
    val relatedFiles = executionConfig?.related_files ?: return ""
    if (relatedFiles.isEmpty()) return ""
    log.debug("Loading ${relatedFiles.size} related context files")

    return buildString {
      appendLine("## Related Data Files")
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

  private fun isTextFile(file: java.io.File): Boolean {
    val textExtensions = setOf(
      "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
      "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
    )
    return textExtensions.contains(file.extension.lowercase())
  }

  private fun extractDocumentContent(file: java.io.File) = try {
    file.getDocumentReader().use { reader ->
      when (reader) {
        is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
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
    private val log: Logger = LoggerFactory.getLogger(ReportGenerationTask::class.java)

    @JvmStatic
    val ReportGeneration = TaskType(
      name = "ReportGeneration",
      category = "Writing",
      taskClass = ReportGenerationTask::class.java,
      executionConfigClass = ReportGenerationTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generate comprehensive business reports with data analysis and recommendations",
      tooltipHtml = """
                        Generates complete, professional business reports with structured analysis.
                        <ul>
                          <li>Analyzes metrics and data points with trend analysis</li>
                          <li>Creates structured report outline with multiple sections</li>
                          <li>Generates executive summary/dashboard for quick insights</li>
                          <li>Writes detailed sections with data-driven content</li>
                          <li>Provides actionable recommendations based on findings</li>
                          <li>Includes risk assessment and mitigation strategies</li>
                          <li>Suggests data visualizations (charts, graphs, tables)</li>
                          <li>Supports multiple report types (status updates, quarterly reviews, incident reports)</li>
                          <li>Tailors content to target audience (executives, team members, stakeholders)</li>
                          <li>Optional revision passes for quality improvement</li>
                          <li>Ideal for business reporting, performance analysis, project summaries</li>
                        </ul>
                      """,
    )
  }
}