package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AnalysisTask.Companion.extractDocumentContent
import com.simiacryptus.cognotik.plan.tools.reasoning.safeComplete
import com.simiacryptus.cognotik.plan.tools.reasoning.truncateForDisplay
import com.simiacryptus.cognotik.plan.tools.reasoning.validateAndGetApi
import com.simiacryptus.cognotik.plan.transcript
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ArticleGenerationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: ArticleGenerationTaskExecutionConfigData?
) : JournalismReasoningTask<ArticleGenerationTask.ArticleGenerationTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class ArticleGenerationTaskExecutionConfigData(
    @Description("The story topic or event to write about")
    story_topic: String? = null,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    input_files: List<String>? = null,

    @Description("Journalism elements to consider (who, what, when, where, why, how)")
    journalism_elements: Map<String, Any>? = null,

    @Description("Target word count for the article")
    val target_word_count: Int = 1000,

    @Description("Article format (e.g., 'news', 'feature', 'investigative', 'opinion', 'profile')")
    val article_format: String = "news",

    @Description("Writing style (e.g., 'AP style', 'narrative', 'analytical', 'conversational')")
    val writing_style: String = "AP style",

    @Description("Target publication or audience (affects tone and depth)")
    val target_publication: String = "general news",

    @Description("Whether to include quotes from sources")
    val include_quotes: Boolean = true,

    @Description("Whether to include data and statistics")
    val include_data: Boolean = true,

    @Description("Whether to include expert analysis")
    val include_expert_analysis: Boolean = true,

    @Description("Whether to include related context and background")
    val include_context: Boolean = true,

    @Description("Number of revision passes for quality improvement")
    val revision_passes: Int = 1,

    @Description("Whether to generate headline and subheadline")
    val generate_headlines: Boolean = true,

    @Description("Whether to generate social media snippets")
    val generate_social_snippets: Boolean = false,

    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : JournalismReasoningTaskExecutionConfigData(
    story_topic = story_topic,
    journalism_elements = journalism_elements,
    verify_facts = true,
    identify_perspectives = true,
    analyze_context = true,
    identify_biases = true,
    find_gaps = true,
    alternative_angles = 1,
    assess_newsworthiness = true,
    task_dependencies = task_dependencies,
    state = state,
    input_files = input_files
  ) {
    override val task_type: String = ArticleGeneration.name
    override var task_description: String? = "Generate $article_format article about '$story_topic'"
    override fun validate(): String? {
      // First validate parent class
      super.validate()?.let { return it }
      // Validate target_word_count
      if (target_word_count <= 0) {
        return "target_word_count must be positive, got: $target_word_count"
      }
      // Validate revision_passes
      if (revision_passes < 0) {
        return "revision_passes cannot be negative, got: $revision_passes"
      }
      return null
    }
  }

  data class ArticleStructure(
    val headline: String = "",
    val subheadline: String = "",
    val lede: String = "",
    val sections: List<ArticleSection> = emptyList(),
    val conclusion: String = "",
    val estimated_word_count: Int = 0
  )

  data class ArticleSection(
    val section_title: String? = null,
    val purpose: String = "",
    val key_points: List<String> = emptyList(),
    val sources_to_include: List<String> = emptyList(),
    val estimated_word_count: Int = 0
  )

  data class GeneratedArticle(
    val headline: String = "",
    val subheadline: String = "",
    val byline: String = "",
    val dateline: String = "",
    val content: String = "",
    val word_count: Int = 0,
    val key_facts: List<String> = emptyList(),
    val sources_cited: List<String> = emptyList()
  )

  data class SocialSnippets(
    val twitter: String = "",
    val facebook: String = "",
    val linkedin: String = ""
  )

  override fun promptSegment(): String {
    return """
ArticleGeneration - Generate complete journalistic articles from investigation and analysis
  ** Extends JournalismReasoning with full article writing
  ** Specify the story topic to write about
  ** Define journalism elements: who, what, when, where, why, how
  ** Set target word count and article format (news, feature, investigative, etc.)
  ** Configure writing style and target publication
  ** Enable quotes, data, expert analysis, and context
  ** Performs investigation, creates structure, then writes article
  ** Optional revision passes for quality improvement
  ** Can generate headlines and social media snippets
  ** Produces publication-ready articles with proper journalistic structure
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
    val genConfig = executionConfig as? ArticleGenerationTaskExecutionConfigData
    log.info("Starting ArticleGenerationTask for story: '${genConfig?.story_topic}'")

    if (genConfig == null) {
      log.error("Invalid configuration type for ArticleGenerationTask")
      task.safeComplete("CONFIGURATION ERROR: Invalid configuration type", log)
      resultFn("CONFIGURATION ERROR: Invalid configuration type")
      return
    }

    val storyTopic = genConfig.story_topic
    if (storyTopic.isNullOrBlank()) {
      log.error("No story topic specified for article generation")
      task.safeComplete("CONFIGURATION ERROR: No story topic specified", log)
      resultFn("CONFIGURATION ERROR: No story topic specified")
      return
    }
    val inputFileContent = getInputFileCode()
    val messageContent = messages.filter { it.isNotBlank() }.joinToString("\n\n")
    val combinedInput = listOfNotNull(
      messageContent.takeIf { it.isNotBlank() },
      inputFileContent.takeIf { it.isNotBlank() }
    ).joinToString("\n\n---\n\n")


    val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return

    val tabs = TabbedDisplay(task)
    // Create transcript file
    val transcript = task.transcript("transcript")
    transcript?.let { out ->
      out.write("# Article Generation Transcript\n\n".toByteArray())
      out.write("**Story Topic:** $storyTopic\n\n".toByteArray())
      out.write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n".toByteArray())
      out.write("---\n\n".toByteArray())
    }


    // Overview tab
    val overviewTask = task.ui.newTask(false)
    tabs["Overview"] = overviewTask.placeholder

    val overviewContent = buildString {
      appendLine("# Article Generation")
      appendLine()
      if (combinedInput.isNotBlank()) {
        appendLine("**Input Context:** ${combinedInput.take(200)}...")
        appendLine()
      }
      appendLine("**Story Topic:** $storyTopic")
      appendLine()
      appendLine("## Configuration")
      appendLine("- Target Word Count: ${genConfig.target_word_count}")
      appendLine("- Article Format: ${genConfig.article_format}")
      appendLine("- Writing Style: ${genConfig.writing_style}")
      appendLine("- Target Publication: ${genConfig.target_publication}")
      appendLine("- Include Quotes: ${if (genConfig.include_quotes) "✓" else "✗"}")
      appendLine("- Include Data: ${if (genConfig.include_data) "✓" else "✗"}")
      appendLine("- Include Expert Analysis: ${if (genConfig.include_expert_analysis) "✓" else "✗"}")
      appendLine("- Include Context: ${if (genConfig.include_context) "✓" else "✗"}")
      appendLine()
      appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
      appendLine()
      appendLine("---")
      appendLine()
      appendLine("## Progress")
      appendLine()
      appendLine("### Phase 1: Journalism Investigation")
      appendLine("*Running comprehensive journalism analysis...*")
    }
    overviewTask.add(overviewContent.renderMarkdown)
    task.update()
    transcript?.write(overviewContent.toByteArray())


    val resultBuilder = StringBuilder()
    resultBuilder.append("# Generated Article: $storyTopic\n\n")

    try {
      // Phase 1: Run the base journalism investigation
      log.info("Phase 1: Running journalism investigation")
      val investigationResult = StringBuilder()

      super.run(agent, messages, task, { result ->
        investigationResult.append(result)
        transcript?.write("\n## Investigation Results\n\n".toByteArray())
        transcript?.write(result.toByteArray())
        transcript?.write("\n\n".toByteArray())
      }, orchestrationConfig)

      overviewTask.add("\n✅ Phase 1 Complete: Investigation finished\n".renderMarkdown)
      overviewTask.add("\n### Phase 2: Article Structure\n*Creating article outline and structure...*\n".renderMarkdown)
      task.update()

      // Phase 2: Generate article structure
      log.info("Phase 2: Generating article structure")
      val structureTask = task.ui.newTask(false)
      tabs["Article Structure"] = structureTask.placeholder

      structureTask.add(
        buildString {
          appendLine("# Article Structure")
          appendLine()
          appendLine("**Status:** Planning article organization...")
          appendLine()
        }.renderMarkdown
      )
      task.update()

      val structureAgent = ParsedAgent(
        resultClass = ArticleStructure::class.java,
        prompt = """
You are an experienced news editor. Create a detailed structure for this article.

${if (combinedInput.isNotBlank()) "Reference Material:\n$combinedInput\n\n" else ""}

Story Topic: $storyTopic

Investigation Results:
${investigationResult.toString().truncateForDisplay(8000)}

Journalism Elements:
${genConfig.journalism_elements?.entries?.joinToString("\n") { (key, value) -> "- $key: $value" } ?: ""}

Article Specifications:
- Format: ${genConfig.article_format}
- Style: ${genConfig.writing_style}
- Target: ${genConfig.target_publication}
- Word Count: ${genConfig.target_word_count}

Create a structure with:
- Compelling headline and subheadline
- Strong lede (opening paragraph) that hooks readers
- 3-5 main sections with clear purposes
- Logical flow from most to least important (inverted pyramid for news)
- Conclusion that provides context or forward-looking perspective

For each section, specify:
- Section title (if applicable)
- Purpose (what this section accomplishes)
- Key points to cover
- Sources or quotes to include
- Estimated word count

Ensure the structure:
- Follows ${genConfig.article_format} format conventions
- Matches ${genConfig.writing_style} style guidelines
- Serves the ${genConfig.target_publication} audience
- Totals approximately ${genConfig.target_word_count} words
          """.trimIndent(),
        model = api,
        temperature = 0.6,
        parsingChatter = orchestrationConfig.parsingChatter
      )

      val structure = structureAgent.answer(listOf("Generate structure")).obj
      log.info("Generated structure: ${structure.sections.size} sections, ${structure.estimated_word_count} words")

      val structureContent = buildString {
        appendLine("## ${structure.headline}")
        appendLine()
        appendLine("**Subheadline:** ${structure.subheadline}")
        appendLine()
        appendLine("**Estimated Word Count:** ${structure.estimated_word_count}")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("### Lede")
        appendLine(structure.lede)
        appendLine()
        appendLine("---")
        appendLine()
        structure.sections.forEachIndexed { index, section ->
          appendLine("### Section ${index + 1}${section.section_title?.let { ": $it" } ?: ""}")
          appendLine()
          appendLine("**Purpose:** ${section.purpose}")
          appendLine()
          appendLine("**Key Points:**")
          section.key_points.forEach { point ->
            appendLine("- $point")
          }
          appendLine()
          if (section.sources_to_include.isNotEmpty()) {
            appendLine("**Sources to Include:**")
            section.sources_to_include.forEach { source ->
              appendLine("- $source")
            }
            appendLine()
          }
          appendLine("**Est. Words:** ${section.estimated_word_count}")
          appendLine()
          appendLine("---")
          appendLine()
        }
        appendLine("### Conclusion")
        appendLine(structure.conclusion)
        appendLine()
        appendLine("**Status:** ✅ Complete")
      }
      structureTask.add(structureContent.renderMarkdown)
      task.update()
      transcript?.write("\n## Article Structure\n\n".toByteArray())
      transcript?.write(structureContent.toByteArray())
      transcript?.write("\n\n".toByteArray())


      overviewTask.add("✅ Phase 2 Complete: Structure created (${structure.sections.size} sections)\n".renderMarkdown)
      overviewTask.add("\n### Phase 3: Article Writing\n*Writing full article...*\n".renderMarkdown)
      task.update()

      // Phase 3: Write the article
      log.info("Phase 3: Writing article")
      val writingTask = task.ui.newTask(false)
      tabs["Article Draft"] = writingTask.placeholder

      writingTask.add(
        buildString {
          appendLine("# Article Draft")
          appendLine()
          appendLine("**Status:** Writing article...")
          appendLine()
        }.renderMarkdown
      )
      task.update()

      val writingPrompt = """
You are a professional journalist writing for ${genConfig.target_publication}. Write the complete article.

${if (combinedInput.isNotBlank()) "Reference Material:\n$combinedInput\n\n" else ""}

Story Topic: $storyTopic

Article Structure:
${structureContent}

Investigation Findings:
${investigationResult.toString().truncateForDisplay(6000)}

Writing Guidelines:
- Format: ${genConfig.article_format}
- Style: ${genConfig.writing_style}
- Target Word Count: ${genConfig.target_word_count}
${if (genConfig.include_quotes) "- Include direct quotes from sources" else "- Minimize direct quotes"}
${if (genConfig.include_data) "- Include relevant data and statistics" else "- Focus on narrative over data"}
${if (genConfig.include_expert_analysis) "- Include expert analysis and interpretation" else "- Stick to factual reporting"}
${if (genConfig.include_context) "- Provide historical context and background" else "- Focus on current events"}

Write the complete article with:
- Compelling headline and subheadline
- Byline and dateline
- Strong lede that captures the essence
- Clear, concise body following the structure
- Proper attribution for all claims and quotes
- Smooth transitions between sections
- Engaging conclusion
- Approximately ${genConfig.target_word_count} words

Follow journalistic best practices:
- Active voice and strong verbs
- Short paragraphs (2-3 sentences)
- Concrete details and specific examples
- Proper AP style (or specified style)
- Objective tone (unless opinion piece)
- Fact-based reporting

After writing, provide:
- The complete article content
- Actual word count
- Key facts covered
- Sources cited
      """.trimIndent()

      val articleAgent = ParsedAgent(
        resultClass = GeneratedArticle::class.java,
        prompt = writingPrompt,
        model = api,
        temperature = 0.7,
        parsingChatter = orchestrationConfig.parsingChatter
      )

      var article = articleAgent.answer(listOf("Write the article")).obj

      // Optional revision passes
      if (genConfig.revision_passes > 0) {
        repeat(genConfig.revision_passes) { revisionNum ->
          log.debug("Revision pass ${revisionNum + 1}")

          val revisionAgent = ChatAgent(
            prompt = """
You are a senior editor. Review and improve this article while maintaining its factual accuracy.

Article:
${article.content}

Improve:
- Clarity and readability
- Flow and transitions
- Lead strength and hook
- Quote integration
- Fact presentation
- Conclusion impact

Maintain:
- All verified facts
- Source attributions
- Word count (currently ${article.word_count}, target ${genConfig.target_word_count})
- Journalistic standards
- ${genConfig.writing_style} style

Provide the revised article content only.
            """.trimIndent(),
            model = api,
            temperature = 0.6
          )

          val revisedContent = revisionAgent.answer(listOf("Revise the article"))
          article = article.copy(
            content = revisedContent,
            word_count = revisedContent.split("\\s+".toRegex()).size
          )
        }
      }

      val articleContent = buildString {
        appendLine("# ${article.headline}")
        appendLine()
        appendLine("## ${article.subheadline}")
        appendLine()
        appendLine("**${article.byline}**")
        appendLine()
        appendLine("*${article.dateline}*")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine(article.content)
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("**Word Count:** ${article.word_count}")
        appendLine()
        appendLine("**Key Facts:**")
        article.key_facts.forEach { fact ->
          appendLine("- $fact")
        }
        appendLine()
        appendLine("**Sources Cited:**")
        article.sources_cited.forEach { source ->
          appendLine("- $source")
        }
        appendLine()
        appendLine("**Status:** ✅ Complete")
      }
      writingTask.add(articleContent.renderMarkdown)
      task.update()
      transcript?.write("\n## Generated Article\n\n".toByteArray())
      transcript?.write(articleContent.toByteArray())
      transcript?.write("\n\n".toByteArray())


      resultBuilder.append("# ${article.headline}\n\n")
      resultBuilder.append("## ${article.subheadline}\n\n")
      resultBuilder.append("${article.byline}\n\n")
      resultBuilder.append("${article.dateline}\n\n")
      resultBuilder.append("---\n\n")
      resultBuilder.append(article.content)
      resultBuilder.append("\n\n")

      overviewTask.add("✅ Phase 3 Complete: Article written (${article.word_count} words)\n".renderMarkdown)
      task.update()

      // Phase 4: Generate social snippets if requested
      if (genConfig.generate_social_snippets) {
        log.info("Phase 4: Generating social media snippets")
        overviewTask.add("\n### Phase 4: Social Media\n*Creating social snippets...*\n".renderMarkdown)
        task.update()

        val socialTask = task.ui.newTask(false)
        tabs["Social Media"] = socialTask.placeholder

        socialTask.add(
          buildString {
            appendLine("# Social Media Snippets")
            appendLine()
            appendLine("**Status:** Creating platform-specific content...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

        val socialAgent = ParsedAgent(
          resultClass = SocialSnippets::class.java,
          prompt = """
You are a social media editor. Create engaging snippets for different platforms.

Article Headline: ${article.headline}
Article Summary: ${article.content.take(500)}

Create platform-specific snippets:
- Twitter: 280 characters max, engaging hook, relevant hashtags
- Facebook: 2-3 sentences, conversational tone, question or call-to-action
- LinkedIn: Professional tone, business angle, 2-3 sentences

Make each snippet:
- Platform-appropriate in tone and length
- Compelling and clickworthy
- Accurate to the article content
- Include relevant hashtags where appropriate
          """.trimIndent(),
          model = api,
          temperature = 0.8,
          parsingChatter = orchestrationConfig.parsingChatter
        )

        val socialSnippets = socialAgent.answer(listOf("Generate snippets")).obj

        val socialContent = buildString {
          appendLine("## Platform Snippets")
          appendLine()
          appendLine("### Twitter")
          appendLine("```")
          appendLine(socialSnippets.twitter)
          appendLine("```")
          appendLine()
          appendLine("### Facebook")
          appendLine("```")
          appendLine(socialSnippets.facebook)
          appendLine("```")
          appendLine()
          appendLine("### LinkedIn")
          appendLine("```")
          appendLine(socialSnippets.linkedin)
          appendLine("```")
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }
        socialTask.add(socialContent.renderMarkdown)
        task.update()
        transcript?.write("\n## Social Media Snippets\n\n".toByteArray())
        transcript?.write(socialContent.toByteArray())
        transcript?.write("\n\n".toByteArray())


        overviewTask.add("✅ Phase 4 Complete: Social snippets created\n".renderMarkdown)
        task.update()
      }

      // Final statistics
      val totalTime = System.currentTimeMillis() - startTime
      val targetAccuracy = (article.word_count.toFloat() / genConfig.target_word_count * 100).toInt()

      overviewTask.add(
        buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ✅ Generation Complete")
          appendLine()
          appendLine("**Statistics:**")
          appendLine("- Article Format: ${genConfig.article_format}")
          appendLine("- Word Count: ${article.word_count}")
          appendLine("- Target Word Count: ${genConfig.target_word_count}")
          appendLine("- Target Accuracy: $targetAccuracy%")
          appendLine("- Sources Cited: ${article.sources_cited.size}")
          appendLine("- Key Facts: ${article.key_facts.size}")
          appendLine("- Total Time: ${totalTime / 1000.0}s")
          appendLine()
          appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        }.renderMarkdown
      )
      task.update()
      transcript?.write("\n## Final Statistics\n\n".toByteArray())
      transcript?.write("- Article Format: ${genConfig.article_format}\n".toByteArray())
      transcript?.write("- Word Count: ${article.word_count}\n".toByteArray())
      transcript?.write("- Target Word Count: ${genConfig.target_word_count}\n".toByteArray())
      transcript?.write("- Target Accuracy: $targetAccuracy%\n".toByteArray())
      transcript?.write("- Sources Cited: ${article.sources_cited.size}\n".toByteArray())
      transcript?.write("- Key Facts: ${article.key_facts.size}\n".toByteArray())
      transcript?.write("- Total Time: ${totalTime / 1000.0}s\n".toByteArray())
      transcript?.write("\n**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n".toByteArray())


      // Per best practices, the final result passed to resultFn should be a concise summary
      val finalResult = buildString {
        appendLine("# Article Generation Summary")
        appendLine()
        appendLine("A complete **${genConfig.article_format}** article of **${article.word_count} words** was generated in **${totalTime / 1000.0}s**.")
        appendLine()
        appendLine("## ${article.headline}")
        appendLine()
        appendLine("### ${article.subheadline}")
        appendLine()
        appendLine("> The full article is available in the Article Draft tab for review.")
        appendLine()
        appendLine("**Key Metrics:**")
        appendLine("- Sources: ${article.sources_cited.size}")
        appendLine("- Key Facts: ${article.key_facts.size}")
        appendLine("- Target Accuracy: $targetAccuracy%")
      }

      log.info("ArticleGenerationTask completed: words=${article.word_count}, sources=${article.sources_cited.size}, time=${totalTime}ms")
      transcript?.close()

      task.safeComplete("Article generation complete: ${article.word_count} words in ${totalTime / 1000}s", log)
      resultFn(finalResult)

    } catch (e: Exception) {
      log.error("Error during article generation", e)
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
      transcript?.write("\n## Error\n\n".toByteArray())
      transcript?.write("**Error:** ${e.message}\n".toByteArray())
      transcript?.write("**Type:** ${e.javaClass.simpleName}\n".toByteArray())
      transcript?.close()


      val errorOutput = buildString {
        appendLine("# Error in Article Generation")
        appendLine()
        appendLine("**Story:** $storyTopic")
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
    .filterNotNull()
    .sortedBy { it }
    .joinToString("\n\n") { relativePath ->
      val file = root.toFile().resolve(relativePath)
      try {
        val content = if (!isTextFile(file)) {
          extractDocumentContent(file)
        } else {
          file.readText()
        }
        "# $relativePath\n\n```\n$content\n```"
      } catch (e: Throwable) {
        log.warn("Error reading file: $relativePath", e)
        ""
      }
    }

  private fun isTextFile(file: File): Boolean {
    return textExtensions.contains(file.extension.lowercase())
  }


  companion object {
    private val log: Logger = LoggerFactory.getLogger(ArticleGenerationTask::class.java)
    private val textExtensions = setOf(
      "txt",
      "md",
      "kt",
      "java",
      "js",
      "ts",
      "py",
      "rb",
      "go",
      "rs",
      "c",
      "cpp",
      "h",
      "hpp",
      "css",
      "html",
      "xml",
      "json",
      "yaml",
      "yml",
      "properties",
      "gradle",
      "maven"
    )

    val ArticleGeneration = TaskType(
      "ArticleGeneration",
      ArticleGenerationTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Generate complete journalistic articles from investigation and analysis",
      """
              Extends JournalismReasoning to generate publication-ready articles.
              <ul>
                <li>Performs comprehensive journalism investigation (inherited from JournalismReasoning)</li>
                <li>Creates detailed article structure and outline</li>
                <li>Writes complete article following journalistic standards</li>
                <li>Supports multiple formats (news, feature, investigative, opinion, profile)</li>
                <li>Configurable style, tone, and target publication</li>
                <li>Includes quotes, data, expert analysis, and context as configured</li>
                <li>Optional revision passes for quality improvement</li>
                <li>Can generate headlines and social media snippets</li>
                <li>Produces publication-ready articles with proper structure and attribution</li>
                <li>Ideal for news writing, content creation, journalism training</li>
              </ul>
            """
    )
  }
}