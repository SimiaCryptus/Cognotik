# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/ArticleGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.extractDocumentContent
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
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


        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)
        // Create transcript file
        val transcript = task.transcript("transcript")
        transcript?.let { out ->
            out.write("# Article Generation Transcript\n\n".toByteArray())
            out.write("**Story Topic:** $storyTopic\n\n".toByteArray())
            out.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n".toByteArray()
            )
            out.write("---\n\n".toByteArray())
        }


        // Overview tab
        val overviewTask = tabs.newTask("Overview")

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
        overviewTask.add(overviewContent.renderMarkdown(true))
        transcript?.write(overviewContent.toByteArray())


        val resultBuilder = StringBuilder()
        resultBuilder.append("# Generated Article: $storyTopic\n\n")

        try {
            // Phase 1: Run the base journalism investigation
            log.info("Phase 1: Running journalism investigation")
            val investigationResult = StringBuilder()

            val investigationTask = tabs.newTask("Investigation")

            super.run(agent, messages, investigationTask, { result ->
                investigationResult.append(result)
                transcript?.write("\n## Investigation Results\n\n".toByteArray())
                transcript?.write(result.toByteArray())
                transcript?.write("\n\n".toByteArray())
            }, orchestrationConfig)

            overviewTask.add("\n✅ Phase 1 Complete: Investigation finished\n".renderMarkdown(true))
            overviewTask.add(
              "\n### Phase 2: Article Structure\n*Creating article outline and structure...*\n".renderMarkdown(
                true
              )
            )

            // Phase 2: Generate article structure
            log.info("Phase 2: Generating article structure")
            val structureTask = tabs.newTask("Article Structure")

            structureTask.add(
              buildString {
                appendLine("# Article Structure")
                appendLine()
                appendLine("**Status:** Planning article organization...")
                appendLine()
              }.renderMarkdown(true)
            )

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
                parsingChatter = defaultFast
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
            structureTask.add(structureContent.renderMarkdown(true))
            structureTask.complete()
            transcript?.write("\n## Article Structure\n\n".toByteArray())
            transcript?.write(structureContent.toByteArray())
            transcript?.write("\n\n".toByteArray())


            overviewTask.add(
              "✅ Phase 2 Complete: Structure created (${structure.sections.size} sections)\n".renderMarkdown(
                true
              )
            )
            overviewTask.add("\n### Phase 3: Article Writing\n*Writing full article...*\n".renderMarkdown(true))

            // Phase 3: Write the article
            log.info("Phase 3: Writing article")
            val writingTask = tabs.newTask("Article Draft")

            writingTask.add(
              buildString {
                appendLine("# Article Draft")
                appendLine()
                appendLine("**Status:** Writing article...")
                appendLine()
              }.renderMarkdown(true)
            )

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
                parsingChatter = defaultFast
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
            writingTask.add(articleContent.renderMarkdown(true))
            writingTask.complete()
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

            overviewTask.add("✅ Phase 3 Complete: Article written (${article.word_count} words)\n".renderMarkdown(true))

            // Phase 4: Generate social snippets if requested
            if (genConfig.generate_social_snippets) {
                log.info("Phase 4: Generating social media snippets")
                overviewTask.add("\n### Phase 4: Social Media\n*Creating social snippets...*\n".renderMarkdown(true))


                val socialTask = tabs.newTask("Social Media")

                socialTask.add(
                  buildString {
                    appendLine("# Social Media Snippets")
                    appendLine()
                    appendLine("**Status:** Creating platform-specific content...")
                    appendLine()
                  }.renderMarkdown(true)
                )

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
                    parsingChatter = defaultFast
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
                socialTask.add(socialContent.renderMarkdown(true))
                socialTask.complete()
                transcript?.write("\n## Social Media Snippets\n\n".toByteArray())
                transcript?.write(socialContent.toByteArray())
                transcript?.write("\n\n".toByteArray())


                overviewTask.add("✅ Phase 4 Complete: Social snippets created\n".renderMarkdown(true))
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
                appendLine(
                  "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                  }"
                )
              }.renderMarkdown(true)
            )
            overviewTask.complete()
            transcript?.write("\n## Final Statistics\n\n".toByteArray())
            transcript?.write("- Article Format: ${genConfig.article_format}\n".toByteArray())
            transcript?.write("- Word Count: ${article.word_count}\n".toByteArray())
            transcript?.write("- Target Word Count: ${genConfig.target_word_count}\n".toByteArray())
            transcript?.write("- Target Accuracy: $targetAccuracy%\n".toByteArray())
            transcript?.write("- Sources Cited: ${article.sources_cited.size}\n".toByteArray())
            transcript?.write("- Key Facts: ${article.key_facts.size}\n".toByteArray())
            transcript?.write("- Total Time: ${totalTime / 1000.0}s\n".toByteArray())
            transcript?.write(
                "\n**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )


            // Per best practices, the final result passed to resultFn should be a concise summary
            val finalResult = buildString {
                appendLine("# Article Generation Summary")
                appendLine()
                appendLine("A complete **${genConfig.article_format}** article of **${article.word_count} words** was generated.")
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
              }.renderMarkdown(true)
            )
            overviewTask.complete()
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

        @JvmStatic val ArticleGeneration = TaskType(
          name = "ArticleGeneration",
          category = "Writing",
          taskClass = ArticleGenerationTask::class.java,
          executionConfigClass = ArticleGenerationTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate complete journalistic articles from investigation and analysis",
          tooltipHtml = """
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
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/BusinessProposalTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
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
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class BusinessProposalTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: BusinessProposalTaskExecutionConfigData?
) : AbstractTask<BusinessProposalTask.BusinessProposalTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class BusinessProposalTaskExecutionConfigData(
        @Description("The title or name of the proposal")
        val proposal_title: String? = null,

        @Description("The type of proposal (e.g., 'project', 'investment', 'grant', 'partnership', 'rfp_response')")
        val proposal_type: String = "project",

        @Description("The primary objective or goal of the proposal")
        val objective: String? = null,

        @Description("The organization or individual submitting the proposal")
        val proposing_organization: String? = null,

        @Description("The target audience or decision-makers who will evaluate the proposal")
        val decision_makers: List<String>? = null,

        @Description("Budget range or financial scope (e.g., '$50,000-$100,000', 'under $1M')")
        val budget_range: String? = null,

        @Description("Project timeline or duration (e.g., '6 months', '2024-2025', 'Q1-Q3')")
        val timeline: String? = null,

        @Description("Key stakeholders and their interests")
        val stakeholders: Map<String, String>? = null,

        @Description("Whether to include detailed ROI calculations and financial projections")
        val include_roi_analysis: Boolean = true,

        @Description("Whether to include risk assessment and mitigation strategies")
        val include_risk_assessment: Boolean = true,

        @Description("Whether to include competitive analysis or alternatives comparison")
        val include_competitive_analysis: Boolean = true,

        @Description("Whether to include detailed timeline with milestones")
        val include_timeline_milestones: Boolean = true,

        @Description("Whether to include team/resource requirements")
        val include_resource_requirements: Boolean = true,

        @Description("Whether to include appendices and supporting documents")
        val include_appendices: Boolean = true,

        @Description("Urgency level of the opportunity (e.g., 'critical', 'high', 'moderate', 'low')")
        val urgency_level: String = "moderate",

        @Description("Tone of the proposal (e.g., 'formal', 'professional', 'persuasive', 'collaborative')")
        val tone: String = "professional",

        @Description("Target word count for the complete proposal")
        val target_word_count: Int = 3000,

        @Description("Number of revision passes for quality improvement")
        val revision_passes: Int = 1,

        @Description("Related files or research to incorporate")
        val related_files: List<String>? = null,

        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = BusinessProposal.name,
        task_description = task_description ?: "Generate business proposal: '$proposal_title'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (proposal_title.isNullOrBlank()) {
                return "proposal_title must not be null or blank"
            }
            if (objective.isNullOrBlank()) {
                return "objective must not be null or blank"
            }
            if (target_word_count <= 0) {
                return "target_word_count must be positive, got: $target_word_count"
            }
            if (revision_passes < 0 || revision_passes > 5) {
                return "revision_passes must be between 0 and 5, got: $revision_passes"
            }
            if (proposal_type.isBlank()) {
                return "proposal_type must not be blank"
            }
            if (urgency_level.isBlank()) {
                return "urgency_level must not be blank"
            }
            if (tone.isBlank()) {
                return "tone must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class ProposalOutline(
        @Description("The proposal title")
        val title: String = "",
        @Description("Executive summary overview")
        val executive_summary: String = "",
        @Description("Problem statement or opportunity")
        val problem_statement: String = "",
        @Description("Proposed solution overview")
        val solution_overview: String = "",
        @Description("Main sections of the proposal")
        val sections: List<ProposalSection> = emptyList(),
        @Description("Key success metrics")
        val success_metrics: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (executive_summary.isBlank()) return "executive_summary must not be blank"
            if (problem_statement.isBlank()) return "problem_statement must not be blank"
            if (sections.isEmpty()) return "sections must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ProposalSection(
        @Description("Section title")
        val title: String = "",
        @Description("Section purpose")
        val purpose: String = "",
        @Description("Key points to cover")
        val key_points: List<String> = emptyList(),
        @Description("Estimated word count")
        val estimated_word_count: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (purpose.isBlank()) return "purpose must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class StakeholderAnalysis(
        @Description("Stakeholder analyses")
        val stakeholders: List<StakeholderProfile> = emptyList()
    ) : ValidatedObject

    data class StakeholderProfile(
        @Description("Stakeholder name or role")
        val name: String = "",
        @Description("Their primary interests")
        val interests: List<String> = emptyList(),
        @Description("Their concerns or objections")
        val concerns: List<String> = emptyList(),
        @Description("How to address their needs")
        val addressing_strategy: String = "",
        @Description("Their influence level")
        val influence_level: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (name.isBlank()) return "name must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ROIAnalysis(
        @Description("Financial projections")
        val financial_projections: FinancialProjections = FinancialProjections(),
        @Description("Cost breakdown")
        val cost_breakdown: List<CostItem> = emptyList(),
        @Description("Expected benefits")
        val expected_benefits: List<Benefit> = emptyList(),
        @Description("ROI calculation summary")
        val roi_summary: String = "",
        @Description("Payback period")
        val payback_period: String = ""
    ) : ValidatedObject

    data class FinancialProjections(
        @Description("Total investment required")
        val total_investment: String = "",
        @Description("Year 1 projected return")
        val year_1_return: String = "",
        @Description("Year 2 projected return")
        val year_2_return: String = "",
        @Description("Year 3 projected return")
        val year_3_return: String = "",
        @Description("Break-even point")
        val break_even_point: String = ""
    ) : ValidatedObject

    data class CostItem(
        @Description("Cost category")
        val category: String = "",
        @Description("Amount")
        val amount: String = "",
        @Description("Justification")
        val justification: String = ""
    ) : ValidatedObject

    data class Benefit(
        @Description("Benefit type")
        val type: String = "",
        @Description("Description")
        val description: String = "",
        @Description("Quantifiable value")
        val quantifiable_value: String = "",
        @Description("Timeline to realize")
        val timeline: String = ""
    ) : ValidatedObject

    data class RiskAssessment(
        @Description("Identified risks")
        val risks: List<Risk> = emptyList(),
        @Description("Overall risk level")
        val overall_risk_level: String = ""
    ) : ValidatedObject

    data class Risk(
        @Description("Risk category")
        val category: String = "",
        @Description("Risk description")
        val description: String = "",
        @Description("Probability")
        val probability: String = "",
        @Description("Impact level")
        val impact: String = "",
        @Description("Mitigation strategy")
        val mitigation_strategy: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (description.isBlank()) return "description must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class CompetitiveAnalysis(
        @Description("Alternative approaches")
        val alternatives: List<Alternative> = emptyList(),
        @Description("Competitive advantages")
        val competitive_advantages: List<String> = emptyList(),
        @Description("Why this proposal is superior")
        val superiority_statement: String = ""
    ) : ValidatedObject

    data class Alternative(
        @Description("Alternative name")
        val name: String = "",
        @Description("Description")
        val description: String = "",
        @Description("Pros")
        val pros: List<String> = emptyList(),
        @Description("Cons")
        val cons: List<String> = emptyList(),
        @Description("Why our proposal is better")
        val comparison: String = ""
    ) : ValidatedObject

    data class TimelineMilestones(
        @Description("Project phases")
        val phases: List<ProjectPhase> = emptyList(),
        @Description("Critical path items")
        val critical_path: List<String> = emptyList()
    ) : ValidatedObject

    data class ProjectPhase(
        @Description("Phase name")
        val name: String = "",
        @Description("Duration")
        val duration: String = "",
        @Description("Key deliverables")
        val deliverables: List<String> = emptyList(),
        @Description("Dependencies")
        val dependencies: List<String> = emptyList()
    ) : ValidatedObject

    data class ProposalContent(
        @Description("Section title")
        val section_title: String = "",
        @Description("Section content")
        val content: String = "",
        @Description("Word count")
        val word_count: Int = 0,
        @Description("Key messages")
        val key_messages: List<String> = emptyList()
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
BusinessProposal - Generate comprehensive business proposals with ROI analysis and risk assessment
  ** Specify the proposal title and objective
  ** Define proposal type (project, investment, grant, partnership, RFP response)
  ** Identify decision-makers and stakeholders
  ** Set budget range and timeline
  ** Enable ROI calculations and financial projections
  ** Include risk assessment and mitigation strategies
  ** Add competitive analysis and alternatives comparison
  ** Generate timeline with milestones
  ** Specify resource requirements
  ** Produces complete, persuasive business proposal
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        // Create transcript file
        val transcriptStream = task.transcript("transcript")
        val proposalStream = task.transcript("proposal")
        transcriptStream?.let { stream ->
            stream.write("# Business Proposal Generation Transcript\n\n".toByteArray())
            stream.write("**Proposal:** ${executionConfig?.proposal_title}\n".toByteArray())
            stream.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n".toByteArray()
            )
            stream.write("---\n\n".toByteArray())
        }
        fun logToTranscript(message: String) {
            transcriptStream?.write("$message\n".toByteArray())
        }

        fun writeToProposal(message: String) {
            proposalStream?.write("$message\n".toByteArray())
        }

        val startTime = System.currentTimeMillis()
        log.info("Starting BusinessProposalTask for: '${executionConfig?.proposal_title}'")

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            logToTranscript("## Configuration Validation Failed\n\n$validationError\n")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            resultFn("CONFIGURATION ERROR: $validationError")
            return
        }

        val proposalTitle = executionConfig?.proposal_title
        if (proposalTitle.isNullOrBlank()) {
            log.error("No proposal title specified")
            logToTranscript("## Error: No Proposal Title\n")
            task.safeComplete("CONFIGURATION ERROR: No proposal title specified", log)
            resultFn("CONFIGURATION ERROR: No proposal title specified")
            return
        }

        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)
        fun <T> runPhase(tabName: String, block: (SessionTask) -> T): T {
            val subTask = tabs.newTask(tabName)
            try {
                return block(subTask)
            } finally {
                subTask.complete()
            }
        }


        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Business Proposal Generation")
            appendLine()
            appendLine("**Proposal:** $proposalTitle")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Type: ${executionConfig.proposal_type}")
            appendLine("- Objective: ${executionConfig.objective}")
            appendLine("- Proposing Organization: ${executionConfig.proposing_organization ?: "Not specified"}")
            appendLine("- Budget Range: ${executionConfig.budget_range ?: "Not specified"}")
            appendLine("- Timeline: ${executionConfig.timeline ?: "Not specified"}")
            appendLine("- Urgency: ${executionConfig.urgency_level}")
            appendLine("- Tone: ${executionConfig.tone}")
            appendLine("- Target Word Count: ${executionConfig.target_word_count}")
            appendLine()
            appendLine("## Analysis Components")
            appendLine("- ROI Analysis: ${if (executionConfig.include_roi_analysis) "✓" else "✗"}")
            appendLine("- Risk Assessment: ${if (executionConfig.include_risk_assessment) "✓" else "✗"}")
            appendLine("- Competitive Analysis: ${if (executionConfig.include_competitive_analysis) "✓" else "✗"}")
            appendLine("- Timeline & Milestones: ${if (executionConfig.include_timeline_milestones) "✓" else "✗"}")
            appendLine("- Resource Requirements: ${if (executionConfig.include_resource_requirements) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Strategic Analysis")
            appendLine("*Analyzing stakeholders and strategic positioning...*")
        }
        overviewTask.add(overviewContent.renderMarkdown(true))

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Business Proposal: $proposalTitle\n\n")
        // Load input files if specified
        val inputFileContent =
            super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)
        val messagesWithContext = if (inputFileContent.isNotBlank()) {
            messages + listOf(
                "## Input Files Context\n\n$inputFileContent"
            )
        } else {
            messages
        }
        // Include messages in context
        val messagesContext = if (messagesWithContext.isNotEmpty()) {
            buildString {
                appendLine("## User Input")
                appendLine()
                messagesWithContext.forEach { msg ->
                    appendLine(msg)
                    appendLine()
                }
            }
        } else {
            ""
        }


        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState)
            val contextFiles = getContextFiles()

            if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                runPhase("Research Context") { contextTask ->
                    contextTask.add("# Research Context\n\n".renderMarkdown(true))
                    if (priorContext.isNotBlank()) {
                        contextTask.expandable("Prior Context",
                          priorContext.truncateForDisplay(2000).renderMarkdown(true)
                        )
                    }
                    if (contextFiles.isNotBlank()) {
                        contextTask.expandable("Related Files",
                          contextFiles.truncateForDisplay(2000).renderMarkdown(true)
                        )
                    }
                    if (messagesContext.isNotBlank()) {
                        contextTask.expandable("User Input",
                          messagesContext.truncateForDisplay(2000).renderMarkdown(true)
                        )
                    }
                }
            }

            // Phase 1: Stakeholder Analysis
            log.info("Phase 1: Analyzing stakeholders")
            logToTranscript("## Phase 1: Stakeholder Analysis\n\n")


            val stakeholderAnalysis = runPhase("Stakeholder Analysis") { stakeholderTask ->
                stakeholderTask.add(
                  buildString {
                    appendLine("# Stakeholder Analysis")
                    appendLine()
                    appendLine("**Status:** Analyzing decision-makers and stakeholders...")
                    appendLine()
                  }.renderMarkdown(true)
                )

                val stakeholderAgent = ParsedAgent(
                    resultClass = StakeholderAnalysis::class.java,
                    prompt = """
You are a strategic business analyst. Analyze the stakeholders for this business proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}

Decision Makers: ${executionConfig.decision_makers?.joinToString(", ") ?: "Not specified"}
Known Stakeholders: ${executionConfig.stakeholders?.entries?.joinToString("\n") { (name, interest) -> "- $name: $interest" } ?: "Not specified"}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

For each key stakeholder (decision-makers and influencers), provide:
- Name or role
- Their primary interests and priorities
- Potential concerns or objections they might have
- Strategy for addressing their needs in the proposal
- Their influence level (High/Medium/Low)

Consider:
- What motivates each stakeholder?
- What are their success criteria?
- What risks or concerns might they have?
- How can the proposal align with their goals?

Identify 3-5 key stakeholders who will influence the decision.
          """.trimIndent(),
                    model = api,
                    temperature = 0.6,
                    parsingChatter = defaultFast
                )


                val stakeholderAnalysis = stakeholderAgent.answer(listOf("Analyze stakeholders")).obj
                log.debug("Analyzed ${stakeholderAnalysis.stakeholders.size} stakeholders")
                logToTranscript("Identified ${stakeholderAnalysis.stakeholders.size} key stakeholders\n\n")
                writeToProposal("## Key Stakeholders\n\n")

                val stakeholderContent = buildString {
                    appendLine("## Key Stakeholders")
                    appendLine()
                    stakeholderAnalysis.stakeholders.forEach { stakeholder ->
                        val influenceIcon = when (stakeholder.influence_level.lowercase()) {
                            "high" -> "🔴"
                            "medium" -> "🟡"
                            else -> "🟢"
                        }
                        appendLine("### $influenceIcon ${stakeholder.name}")
                        appendLine()
                        appendLine("**Influence Level:** ${stakeholder.influence_level}")
                        appendLine()
                        appendLine("**Interests:**")
                        stakeholder.interests.forEach { interest ->
                            appendLine("- $interest")
                        }
                        appendLine()
                        if (stakeholder.concerns.isNotEmpty()) {
                            appendLine("**Concerns:**")
                            stakeholder.concerns.forEach { concern ->
                                appendLine("- $concern")
                            }
                            appendLine()
                        }
                        appendLine("**Addressing Strategy:** ${stakeholder.addressing_strategy}")
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("**Status:** ✅ Complete")
                }
                stakeholderTask.add(stakeholderContent.renderMarkdown(true))
                writeToProposal(stakeholderContent)
                stakeholderAnalysis
            }

            overviewTask.add("✅ Phase 1 Complete: Stakeholder analysis finished\n".renderMarkdown(true))

            // Phase 2: ROI Analysis (if enabled)
            val roiAnalysis = if (executionConfig.include_roi_analysis) {
                logToTranscript("## Phase 2: ROI Analysis\n\n")
                overviewTask.add(
                  "\n### Phase 2: ROI Analysis\n*Calculating financial projections and ROI...*\n".renderMarkdown(
                    true
                  )
                )

                log.info("Phase 2: Performing ROI analysis")


                runPhase("ROI Analysis") { roiTask ->
                    roiTask.add(
                      buildString {
                        appendLine("# ROI Analysis")
                        appendLine()
                        appendLine("**Status:** Calculating financial projections...")
                        appendLine()
                      }.renderMarkdown(true)
                    )

                    val roiAgent = ParsedAgent(
                        resultClass = ROIAnalysis::class.java,
                        prompt = """
You are a financial analyst. Create a comprehensive ROI analysis for this business proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Budget Range: ${executionConfig.budget_range ?: "Not specified"}
Timeline: ${executionConfig.timeline ?: "Not specified"}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

Provide:
1. Financial Projections:
   - Total investment required
   - Year 1, 2, and 3 projected returns
   - Break-even point

2. Cost Breakdown:
   - Major cost categories (personnel, technology, operations, etc.)
   - Amount for each category
   - Justification for each cost

3. Expected Benefits:
   - Quantifiable benefits (revenue increase, cost savings, efficiency gains)
   - Timeline to realize each benefit
   - Both tangible and intangible benefits

4. ROI Summary:
   - Overall ROI calculation
   - Payback period
   - Key financial metrics

Be realistic and conservative in projections. Include assumptions.
If specific numbers aren't provided, use reasonable estimates based on the proposal type and industry standards.
          """.trimIndent(),
                        model = api,
                        temperature = 0.5,
                        parsingChatter = defaultFast
                    )


                    val roiAnalysis = roiAgent.answer(listOf("Perform ROI analysis")).obj
                    log.debug("ROI analysis complete")
                    logToTranscript("ROI Analysis complete: ${roiAnalysis.roi_summary.take(200)}\n\n")

                    val roiContent = buildString {
                        appendLine("## Financial Projections")
                        appendLine()
                        appendLine("| Metric | Value |")
                        appendLine("|--------|-------|")
                        appendLine("| Total Investment | ${roiAnalysis.financial_projections.total_investment} |")
                        appendLine("| Year 1 Return | ${roiAnalysis.financial_projections.year_1_return} |")
                        appendLine("| Year 2 Return | ${roiAnalysis.financial_projections.year_2_return} |")
                        appendLine("| Year 3 Return | ${roiAnalysis.financial_projections.year_3_return} |")
                        appendLine("| Break-Even Point | ${roiAnalysis.financial_projections.break_even_point} |")
                        appendLine()
                        appendLine("### Cost Breakdown")
                        appendLine()
                        roiAnalysis.cost_breakdown.forEach { cost ->
                            appendLine("**${cost.category}:** ${cost.amount}")
                            appendLine("- ${cost.justification}")
                            appendLine()
                        }
                        appendLine("### Expected Benefits")
                        appendLine()
                        roiAnalysis.expected_benefits.forEach { benefit ->
                            appendLine("**${benefit.type}**")
                            appendLine("- Description: ${benefit.description}")
                            appendLine("- Value: ${benefit.quantifiable_value}")
                            appendLine("- Timeline: ${benefit.timeline}")
                            appendLine()
                        }
                        appendLine("### ROI Summary")
                        appendLine()
                        appendLine(roiAnalysis.roi_summary)
                        appendLine()
                        appendLine("**Payback Period:** ${roiAnalysis.payback_period}")
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }
                    roiTask.add(roiContent.renderMarkdown(true))
                    writeToProposal(roiContent)

                    overviewTask.add("✅ Phase 2 Complete: ROI analysis finished\n".renderMarkdown(true))
                    roiAnalysis
                }
            } else null

            // Phase 3: Risk Assessment (if enabled)
            val riskAssessment = if (executionConfig.include_risk_assessment) {
                logToTranscript("## Phase 3: Risk Assessment\n\n")
                overviewTask.add(
                  "\n### Phase 3: Risk Assessment\n*Identifying and mitigating risks...*\n".renderMarkdown(
                    true
                  )
                )

                log.info("Phase 3: Performing risk assessment")


                runPhase("Risk Assessment") { riskTask ->
                    riskTask.add(
                      buildString {
                        appendLine("# Risk Assessment")
                        appendLine()
                        appendLine("**Status:** Identifying risks and mitigation strategies...")
                        appendLine()
                      }.renderMarkdown(true)
                    )

                    val riskAgent = ParsedAgent(
                        resultClass = RiskAssessment::class.java,
                        prompt = """
You are a risk management expert. Identify and assess risks for this business proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Timeline: ${executionConfig.timeline ?: "Not specified"}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

Identify 5-7 key risks across categories:
- Technical risks
- Financial risks
- Operational risks
- Market/competitive risks
- Organizational/people risks
- Timeline/schedule risks

For each risk, provide:
- Category
- Clear description of the risk
- Probability (High/Medium/Low)
- Impact level (High/Medium/Low)
- Specific mitigation strategy

Also provide an overall risk level assessment (Low/Moderate/High/Critical).

Be realistic but not alarmist. Focus on actionable mitigation strategies.
          """.trimIndent(),
                        model = api,
                        temperature = 0.6,
                        parsingChatter = defaultFast
                    )


                    val riskAssessment = riskAgent.answer(listOf("Assess risks")).obj
                    log.debug("Identified ${riskAssessment.risks.size} risks")
                    logToTranscript("Identified ${riskAssessment.risks.size} risks. Overall risk level: ${riskAssessment.overall_risk_level}\n\n")

                    val riskContent = buildString {
                        appendLine("## Overall Risk Level: ${riskAssessment.overall_risk_level}")
                        appendLine()
                        appendLine("## Identified Risks")
                        appendLine()
                        riskAssessment.risks.forEach { risk ->
                            val riskIcon = when {
                                risk.probability.lowercase() == "high" && risk.impact.lowercase() == "high" -> "🔴"
                                risk.probability.lowercase() == "high" || risk.impact.lowercase() == "high" -> "🟡"
                                else -> "🟢"
                            }
                            appendLine("### $riskIcon ${risk.category}")
                            appendLine()
                            appendLine("**Description:** ${risk.description}")
                            appendLine()
                            appendLine("**Probability:** ${risk.probability} | **Impact:** ${risk.impact}")
                            appendLine()
                            appendLine("**Mitigation Strategy:**")
                            appendLine(risk.mitigation_strategy)
                            appendLine()
                            appendLine("---")
                            appendLine()
                        }
                        appendLine("**Status:** ✅ Complete")
                    }
                    riskTask.add(riskContent.renderMarkdown(true))
                    writeToProposal(riskContent)

                    overviewTask.add("✅ Phase 3 Complete: Risk assessment finished\n".renderMarkdown(true))
                    riskAssessment
                }
            } else null

            // Phase 4: Competitive Analysis (if enabled)
            val competitiveAnalysis = if (executionConfig.include_competitive_analysis) {
                logToTranscript("## Phase 4: Competitive Analysis\n\n")
                overviewTask.add(
                  "\n### Phase 4: Competitive Analysis\n*Analyzing alternatives and competitive advantages...*\n".renderMarkdown(
                    true
                  )
                )

                log.info("Phase 4: Performing competitive analysis")


                runPhase("Competitive Analysis") { competitiveTask ->
                    competitiveTask.add(
                      buildString {
                        appendLine("# Competitive Analysis")
                        appendLine()
                        appendLine("**Status:** Analyzing alternatives and positioning...")
                        appendLine()
                      }.renderMarkdown(true)
                    )

                    val competitiveAgent = ParsedAgent(
                        resultClass = CompetitiveAnalysis::class.java,
                        prompt = """
You are a competitive strategy analyst. Analyze alternatives and competitive positioning for this proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

Identify 3-4 alternative approaches or competing solutions, including:
- Status quo (doing nothing)
- Alternative vendors or approaches
- In-house vs. outsourced options
- Different implementation strategies

For each alternative:
- Name and brief description
- Pros (advantages)
- Cons (disadvantages)
- Why our proposal is better (specific comparison)

Also provide:
- List of competitive advantages of this proposal
- A clear superiority statement explaining why this proposal is the best choice

Be fair to alternatives but make a compelling case for this proposal.
          """.trimIndent(),
                        model = api,
                        temperature = 0.6,
                        parsingChatter = defaultFast
                    )


                    val competitiveAnalysis = competitiveAgent.answer(listOf("Analyze competition")).obj
                    log.debug("Analyzed ${competitiveAnalysis.alternatives.size} alternatives")
                    logToTranscript("Analyzed ${competitiveAnalysis.alternatives.size} alternative approaches\n\n")

                    val competitiveContent = buildString {
                        appendLine("## Competitive Advantages")
                        appendLine()
                        competitiveAnalysis.competitive_advantages.forEach { advantage ->
                            appendLine("- $advantage")
                        }
                        appendLine()
                        appendLine("## Alternative Approaches")
                        appendLine()
                        competitiveAnalysis.alternatives.forEach { alt ->
                            appendLine("### ${alt.name}")
                            appendLine()
                            appendLine(alt.description)
                            appendLine()
                            appendLine("**Pros:**")
                            alt.pros.forEach { pro ->
                                appendLine("- $pro")
                            }
                            appendLine()
                            appendLine("**Cons:**")
                            alt.cons.forEach { con ->
                                appendLine("- $con")
                            }
                            appendLine()
                            appendLine("**Why Our Proposal is Better:**")
                            appendLine(alt.comparison)
                            appendLine()
                            appendLine("---")
                            appendLine()
                        }
                        appendLine("## Why This Proposal is Superior")
                        appendLine()
                        appendLine(competitiveAnalysis.superiority_statement)
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }
                    competitiveTask.add(competitiveContent.renderMarkdown(true))
                    writeToProposal(competitiveContent)

                    overviewTask.add("✅ Phase 4 Complete: Competitive analysis finished\n".renderMarkdown(true))
                    competitiveAnalysis
                }
            } else null

            // Phase 5: Timeline & Milestones (if enabled)
            val timelineMilestones = if (executionConfig.include_timeline_milestones) {
                logToTranscript("## Phase 5: Timeline & Milestones\n\n")
                overviewTask.add(
                  "\n### Phase 5: Timeline & Milestones\n*Creating project timeline...*\n".renderMarkdown(
                    true
                  )
                )

                log.info("Phase 5: Creating timeline and milestones")


                runPhase("Timeline & Milestones") { timelineTask ->
                    timelineTask.add(
                      buildString {
                        appendLine("# Timeline & Milestones")
                        appendLine()
                        appendLine("**Status:** Creating project timeline...")
                        appendLine()
                      }.renderMarkdown(true)
                    )

                    val timelineAgent = ParsedAgent(
                        resultClass = TimelineMilestones::class.java,
                        prompt = """
You are a project management expert. Create a detailed timeline with milestones for this proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Timeline: ${executionConfig.timeline ?: "Not specified"}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

Create a project timeline with:
1. 4-6 major phases (e.g., Planning, Design, Implementation, Testing, Launch, Optimization)
2. For each phase:
   - Name
   - Duration
   - Key deliverables
   - Dependencies on other phases

3. Critical path items (tasks that must be completed on time to avoid delays)

Be realistic about timelines. Include buffer time for unexpected issues.
Ensure phases flow logically and dependencies are clear.
          """.trimIndent(),
                        model = api,
                        temperature = 0.5,
                        parsingChatter = defaultFast
                    )


                    val timelineMilestones = timelineAgent.answer(listOf("Create timeline")).obj
                    log.debug("Created timeline with ${timelineMilestones.phases.size} phases")
                    logToTranscript("Created project timeline with ${timelineMilestones.phases.size} phases\n\n")

                    val timelineContent = buildString {
                        appendLine("## Project Phases")
                        appendLine()
                        timelineMilestones.phases.forEachIndexed { index, phase ->
                            appendLine("### Phase ${index + 1}: ${phase.name}")
                            appendLine()
                            appendLine("**Duration:** ${phase.duration}")
                            appendLine()
                            appendLine("**Key Deliverables:**")
                            phase.deliverables.forEach { deliverable ->
                                appendLine("- $deliverable")
                            }
                            appendLine()
                            if (phase.dependencies.isNotEmpty()) {
                                appendLine("**Dependencies:**")
                                phase.dependencies.forEach { dep ->
                                    appendLine("- $dep")
                                }
                                appendLine()
                            }
                            appendLine("---")
                            appendLine()
                        }
                        appendLine("## Critical Path")
                        appendLine()
                        timelineMilestones.critical_path.forEach { item ->
                            appendLine("- $item")
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }
                    timelineTask.add(timelineContent.renderMarkdown(true))
                    writeToProposal(timelineContent)

                    overviewTask.add("✅ Phase 5 Complete: Timeline created\n".renderMarkdown(true))
                    timelineMilestones
                }
            } else null

            // Phase 6: Create Proposal Outline
            logToTranscript("## Phase 6: Proposal Structure\n\n")
            overviewTask.add("\n### Phase 6: Proposal Structure\n*Creating detailed outline...*\n".renderMarkdown(true))

            log.info("Phase 6: Creating proposal outline")

            val outline = runPhase("Proposal Outline") { outlineTask ->
                outlineTask.add(
                  buildString {
                    appendLine("# Proposal Outline")
                    appendLine()
                    appendLine("**Status:** Creating detailed structure...")
                    appendLine()
                  }.renderMarkdown(true)
                )

                val wordsPerSection = executionConfig.target_word_count / 8 // Rough estimate for 8 main sections

                val outlineAgent = ParsedAgent(
                    resultClass = ProposalOutline::class.java,
                    prompt = """
You are a business proposal expert. Create a detailed outline for this proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Target Word Count: ${executionConfig.target_word_count}

Stakeholder Analysis Summary:
${stakeholderAnalysis.stakeholders.take(3).joinToString("\n") { "- ${it.name}: ${it.interests.firstOrNull() ?: ""}" }}

${if (roiAnalysis != null) "ROI Summary: ${roiAnalysis.roi_summary.take(200)}" else ""}
${if (riskAssessment != null) "Risk Level: ${riskAssessment.overall_risk_level}" else ""}

Create a comprehensive outline with:
1. Title
2. Executive Summary (compelling 1-paragraph overview)
3. Problem Statement (what opportunity or challenge this addresses)
4. Solution Overview (high-level description of the proposal)
5. Main sections (6-8 sections covering):
   - Background/Context
   - Proposed Solution (detailed)
   - Implementation Approach
   ${if (roiAnalysis != null) "- Financial Analysis" else ""}
   ${if (riskAssessment != null) "- Risk Management" else ""}
   ${if (competitiveAnalysis != null) "- Competitive Positioning" else ""}
   ${if (timelineMilestones != null) "- Timeline & Milestones" else ""}
   ${if (executionConfig.include_resource_requirements) "- Resource Requirements" else ""}
   - Conclusion & Next Steps

For each section:
- Clear title
- Purpose (what this section accomplishes)
- 3-5 key points to cover
- Estimated word count (~$wordsPerSection words per section)

6. Success metrics (how success will be measured)

Tailor the outline to the ${executionConfig.proposal_type} proposal type and ${executionConfig.tone} tone.
        """.trimIndent(),
                    model = api,
                    temperature = 0.6,
                    parsingChatter = defaultFast
                )


                val outline = outlineAgent.answer(listOf("Create outline")).obj
                log.debug("Outline created with ${outline.sections.size} sections")
                logToTranscript("Created outline with ${outline.sections.size} main sections\n\n")

                val outlineContent = buildString {
                    appendLine("## ${outline.title}")
                    appendLine()
                    appendLine("### Executive Summary")
                    appendLine(outline.executive_summary)
                    appendLine()
                    appendLine("### Problem Statement")
                    appendLine(outline.problem_statement)
                    appendLine()
                    appendLine("### Solution Overview")
                    appendLine(outline.solution_overview)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("### Main Sections")
                    appendLine()
                    outline.sections.forEach { section ->
                        appendLine("#### ${section.title}")
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
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("### Success Metrics")
                    appendLine()
                    outline.success_metrics.forEach { metric ->
                        appendLine("- $metric")
                    }
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                outlineTask.add(outlineContent.renderMarkdown(true))
                writeToProposal(outlineContent)
                outline
            }

            overviewTask.add("✅ Phase 6 Complete: Outline created\n".renderMarkdown(true))

            // Phase 7: Write Proposal Sections
            logToTranscript("## Phase 7: Content Generation\n\n")
            overviewTask.add("\n### Phase 7: Content Generation\n*Writing proposal sections...*\n".renderMarkdown(true))

            log.info("Phase 7: Writing proposal sections")
            val proposalSections = mutableListOf<ProposalContent>()
            var cumulativeWordCount = 0

            // Write Executive Summary


            runPhase("Executive Summary") { execSummaryTask ->
                execSummaryTask.add(
                  buildString {
                    appendLine("# Executive Summary")
                    appendLine()
                    appendLine("**Status:** Writing executive summary...")
                    appendLine()
                  }.renderMarkdown(true)
                )

                val execSummaryAgent = ParsedAgent(
                    resultClass = ProposalContent::class.java,
                    prompt = """
You are a business proposal writer. Write a compelling executive summary.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Tone: ${executionConfig.tone}

Outline Summary: ${outline.executive_summary}

Write an executive summary (300-400 words) that:
1. Opens with a strong hook that captures attention
2. Clearly states the problem or opportunity
3. Presents the proposed solution at a high level
4. Highlights key benefits and ROI
5. Mentions critical success factors
6. Ends with a clear call to action or next steps

Make it compelling and persuasive. Decision-makers should understand the value immediately.
Target audience: ${executionConfig.decision_makers?.joinToString(", ") ?: "Senior executives"}
        """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = defaultFast
                )


                var execSummary = execSummaryAgent.answer(listOf("Write executive summary")).obj
                proposalSections.add(execSummary)
                cumulativeWordCount += execSummary.word_count
                logToTranscript("Executive Summary written: ${execSummary.word_count} words\n")

                val execSummaryContent = buildString {
                    appendLine("## Executive Summary")
                    appendLine()
                    appendLine(execSummary.content)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Word Count:** ${execSummary.word_count}")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }

                execSummaryTask.add(
                  execSummaryContent.renderMarkdown(true)
                )
                writeToProposal(execSummaryContent)

                resultBuilder.append("## Executive Summary\n\n")
                resultBuilder.append(execSummary.content)
                resultBuilder.append("\n\n")
            }

            overviewTask.add("- Executive Summary ✅ (${proposalSections.last().word_count} words)\n".renderMarkdown(true))

            // Write each main section
            outline.sections.forEachIndexed { index, sectionOutline ->
                log.info("Writing section ${index + 1}/${outline.sections.size}: ${sectionOutline.title}")
                logToTranscript("Writing section: ${sectionOutline.title}\n")

                overviewTask.add("- ${sectionOutline.title} ".renderMarkdown(true))



                runPhase(sectionOutline.title) { sectionTask ->
                    sectionTask.add(
                      buildString {
                        appendLine("# ${sectionOutline.title}")
                        appendLine()
                        appendLine("**Status:** Writing section...")
                        appendLine()
                      }.renderMarkdown(true)
                    )

                    // Build context from previous sections
                    val previousContext = if (proposalSections.isNotEmpty()) {
                        buildString {
                            appendLine("## Previous Sections Summary")
                            proposalSections.takeLast(2).forEach { prevSection ->
                                appendLine("**${prevSection.section_title}:** ${prevSection.key_messages.firstOrNull() ?: ""}")
                                appendLine()
                            }
                        }
                    } else {
                        "This is the first main section after the executive summary."
                    }

                    // Determine if this section should incorporate analysis results
                    val analysisContext = buildString {
                        when {
                            sectionOutline.title.contains("Financial", ignoreCase = true) && roiAnalysis != null -> {
                                appendLine("## ROI Analysis to Incorporate")
                                appendLine("ROI Summary: ${roiAnalysis.roi_summary}")
                                appendLine("Payback Period: ${roiAnalysis.payback_period}")
                                appendLine()
                            }

                            sectionOutline.title.contains("Risk", ignoreCase = true) && riskAssessment != null -> {
                                appendLine("## Risk Assessment to Incorporate")
                                appendLine("Overall Risk Level: ${riskAssessment.overall_risk_level}")
                                riskAssessment.risks.take(3).forEach { risk ->
                                    appendLine("- ${risk.category}: ${risk.description.take(100)}")
                                }
                                appendLine()
                            }

                            sectionOutline.title.contains(
                                "Competitive",
                                ignoreCase = true
                            ) && competitiveAnalysis != null -> {
                                appendLine("## Competitive Analysis to Incorporate")
                                appendLine(competitiveAnalysis.superiority_statement.take(200))
                                appendLine()
                            }

                            sectionOutline.title.contains(
                                "Timeline",
                                ignoreCase = true
                            ) && timelineMilestones != null -> {
                                appendLine("## Timeline to Incorporate")
                                timelineMilestones.phases.take(3).forEach { phase ->
                                    appendLine("- ${phase.name}: ${phase.duration}")
                                }
                                appendLine()
                            }
                        }
                    }

                    val sectionAgent = ParsedAgent(
                        resultClass = ProposalContent::class.java,
                        prompt = """
You are a business proposal writer. Write the "${sectionOutline.title}" section.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Tone: ${executionConfig.tone}

Section Purpose: ${sectionOutline.purpose}

Key Points to Cover:
${sectionOutline.key_points.joinToString("\n") { "- $it" }}

Target Word Count: ${sectionOutline.estimated_word_count}

$previousContext

$analysisContext

${if (contextFiles.isNotBlank()) "Additional Context:\n${contextFiles.truncateForDisplay(1000)}\n" else ""}

Write a well-structured section that:
1. Opens with a clear topic statement
2. Develops each key point with supporting details
3. Uses concrete examples and data where appropriate
4. Maintains a ${executionConfig.tone} tone
5. Connects to the overall proposal objective
6. Transitions smoothly to the next section

Make it persuasive and professional. Use clear, concise language.
Aim for approximately ${sectionOutline.estimated_word_count} words.
          """.trimIndent(),
                        model = api,
                        temperature = 0.7,
                        parsingChatter = defaultFast
                    )

                    var sectionContent = sectionAgent.answer(listOf("Write section")).obj
                    proposalSections.add(sectionContent)
                    cumulativeWordCount += sectionContent.word_count
                    logToTranscript("Section '${sectionOutline.title}' completed: ${sectionContent.word_count} words\n")

                    sectionTask.add(
                      buildString {
                        appendLine("## ${sectionOutline.title}")
                        appendLine()
                        appendLine(sectionContent.content)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Word Count:** ${sectionContent.word_count}")
                        if (sectionContent.key_messages.isNotEmpty()) {
                          appendLine()
                          appendLine("**Key Messages:**")
                          sectionContent.key_messages.forEach { msg ->
                            appendLine("- $msg")
                          }
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                      }.renderMarkdown(true)
                    )

                    writeToProposal(sectionContent.content)

                    resultBuilder.append("## ${sectionOutline.title}\n\n")
                    resultBuilder.append(sectionContent.content)
                    resultBuilder.append("\n\n")
                }

                overviewTask.add("✅ (${proposalSections.last().word_count} words)\n".renderMarkdown(true))
            }

            overviewTask.add("✅ Phase 7 Complete: All sections written\n".renderMarkdown(true))

            // Phase 8: Conclusion & Next Steps
            logToTranscript("\n## Phase 8: Conclusion & Next Steps\n\n")
            overviewTask.add("\n### Phase 8: Conclusion\n*Writing conclusion and next steps...*\n".renderMarkdown(true))

            log.info("Phase 8: Writing conclusion")

            val conclusion = runPhase("Conclusion") { conclusionTask ->
                conclusionTask.add(
                  buildString {
                    appendLine("# Conclusion & Next Steps")
                    appendLine()
                    appendLine("**Status:** Writing conclusion...")
                    appendLine()
                  }.renderMarkdown(true)
                )

                val conclusionAgent = ParsedAgent(
                    resultClass = ProposalContent::class.java,
                    prompt = """
You are a business proposal writer. Write a compelling conclusion and next steps section.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Urgency Level: ${executionConfig.urgency_level}

Success Metrics:
${outline.success_metrics.joinToString("\n") { "- $it" }}

Write a conclusion (200-300 words) that:
1. Summarizes the key value proposition
2. Reinforces why this proposal is the best choice
3. Reiterates the urgency (${executionConfig.urgency_level} urgency)
4. Provides clear, specific next steps
5. Includes a call to action
6. Expresses confidence and readiness to proceed

Make it action-oriented and compelling. The reader should feel motivated to move forward.
        """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = defaultFast
                )


                var conclusion = conclusionAgent.answer(listOf("Write conclusion")).obj
                cumulativeWordCount += conclusion.word_count
                logToTranscript("Conclusion written: ${conclusion.word_count} words\n\n")

                val conclusionContent = buildString {
                    appendLine("## Conclusion & Next Steps")
                    appendLine()
                    appendLine(conclusion.content)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Word Count:** ${conclusion.word_count}")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }

                conclusionTask.add(
                  conclusionContent.renderMarkdown(true)
                )
                writeToProposal(conclusionContent)

                resultBuilder.append("## Conclusion & Next Steps\n\n")
                resultBuilder.append(conclusion.content)
                resultBuilder.append("\n\n")
                conclusion
            }

            overviewTask.add(
              "✅ Phase 8 Complete: Conclusion written (${conclusion.word_count} words)\n".renderMarkdown(
                true
              )
            )

            // Phase 9: Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                logToTranscript("## Phase 9: Revision Process\n\n")
                overviewTask.add("\n### Phase 9: Revision\n*Refining and polishing...*\n".renderMarkdown(true))

                log.info("Phase 9: Performing ${executionConfig.revision_passes} revision pass(es)")

                runPhase("Revision") { revisionTask ->
                    revisionTask.add(
                      buildString {
                        appendLine("# Revision Process")
                        appendLine()
                        appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
                        appendLine()
                      }.renderMarkdown(true)
                    )

                    val fullProposal = resultBuilder.toString()

                    repeat(executionConfig.revision_passes) { passNum ->
                        log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")
                        logToTranscript("Performing revision pass ${passNum + 1}/${executionConfig.revision_passes}\n")

                        val revisionAgent = ChatAgent(
                            prompt = """
You are an expert business proposal editor. Review and improve this proposal.

Current Proposal:
$fullProposal

Focus on:
1. Strengthening persuasive language and value proposition
2. Ensuring logical flow and coherence
3. Improving clarity and conciseness
4. Verifying alignment with ${executionConfig.tone} tone
5. Enhancing professional presentation
6. Ensuring all stakeholder concerns are addressed
7. Maximizing impact on decision-makers

Maintain:
- All key points and data
- The proposal structure
- Approximate word count ($cumulativeWordCount words)
- The ${executionConfig.tone} tone

Provide the complete revised proposal.
            """.trimIndent(),
                            model = api,
                            temperature = 0.6
                        )

                        val revisedProposal = revisionAgent.answer(listOf("Revise the proposal"))
                        resultBuilder.clear()
                        resultBuilder.append(revisedProposal)

                        revisionTask.add(
                          buildString {
                            appendLine("## Revision Pass ${passNum + 1}")
                            appendLine()
                            appendLine("✅ Complete")
                            appendLine()
                          }.renderMarkdown(true)
                        )
                    }
                }

                overviewTask.add(
                  "✅ Phase 9 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown(
                    true
                  )
                )
            }

            // Phase 10: Final Assembly
            logToTranscript("\n## Phase 10: Final Assembly\n\n")
            overviewTask.add("\n### Phase 10: Final Assembly\n*Compiling complete proposal...*\n".renderMarkdown(true))

            log.info("Phase 10: Assembling final proposal")

            val finalProposal = runPhase("Complete Proposal") { finalTask ->
                val proposal = buildString {
                    appendLine("# ${outline.title}")
                    appendLine()
                    appendLine("**Prepared by:** ${executionConfig.proposing_organization ?: "Your Organization"}")
                    appendLine()
                    appendLine("**Date:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine(resultBuilder.toString())
                    appendLine()
                    if (executionConfig.include_appendices) {
                        appendLine("---")
                        appendLine()
                        appendLine("## Appendices")
                        appendLine()
                        appendLine("### Appendix A: Detailed Financial Projections")
                        appendLine("*[Include detailed spreadsheets and financial models]*")
                        appendLine()
                        appendLine("### Appendix B: Technical Specifications")
                        appendLine("*[Include technical documentation and specifications]*")
                        appendLine()
                        appendLine("### Appendix C: Team Biographies")
                        appendLine("*[Include key team member profiles and qualifications]*")
                        appendLine()
                        appendLine("### Appendix D: References and Case Studies")
                        appendLine("*[Include relevant case studies and client references]*")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                    appendLine("**Total Word Count:** $cumulativeWordCount")
                    appendLine()
                    appendLine("**Target Word Count:** ${executionConfig.target_word_count}")
                    appendLine()
                    appendLine("**Completion:** ${(cumulativeWordCount.toFloat() / executionConfig.target_word_count * 100).toInt()}%")
                }

                finalTask.add(proposal.renderMarkdown(true))
                writeToProposal(proposal)
                proposal
            }

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime
            logToTranscript("\n## Generation Complete\n\nTotal time: ${totalTime / 1000.0}s\nTotal words: $cumulativeWordCount\n")

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
                appendLine("- Number of Sections: ${proposalSections.size}")
                appendLine("- Stakeholders Analyzed: ${stakeholderAnalysis.stakeholders.size}")
                if (roiAnalysis != null) appendLine("- ROI Analysis: ✓ Included")
                if (riskAssessment != null) appendLine("- Risk Assessment: ✓ Included (${riskAssessment.risks.size} risks)")
                if (competitiveAnalysis != null) appendLine("- Competitive Analysis: ✓ Included")
                if (timelineMilestones != null) appendLine("- Timeline: ✓ Included (${timelineMilestones.phases.size} phases)")
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

            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# Business Proposal Summary: ${outline.title}")
                appendLine()
                appendLine("A complete business proposal of **$cumulativeWordCount words** was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Objective:** ${executionConfig.objective}")
                appendLine()
                appendLine("## Output Files")
                appendLine()
                val proposalFile = "proposal_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
                val (proposalLink, _) = Pair(task.linkTo(proposalFile), task.resolveUserFile(proposalFile))
                val transcriptFile = "proposal_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
                val (transcriptLink, _) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
                appendLine(
                    "- **Complete Proposal:** [View](${proposalLink}) | [HTML](${proposalLink.removeSuffix(".md")}.html) | [PDF](${
                        proposalLink.removeSuffix(
                            ".md"
                        )
                    }.pdf)"
                )
                appendLine(
                    "- **Transcript:** [View](${transcriptLink}) | [HTML](${transcriptLink.removeSuffix(".md")}.html) | [PDF](${
                        transcriptLink.removeSuffix(
                            ".md"
                        )
                    }.pdf)"
                )
                appendLine()
                appendLine("**Key Components:**")
                appendLine("- Executive Summary")
                appendLine("- ${outline.sections.size} main sections")
                if (roiAnalysis != null) appendLine("- ROI Analysis with financial projections")
                if (riskAssessment != null) appendLine("- Risk Assessment (${riskAssessment.overall_risk_level} risk level)")
                if (competitiveAnalysis != null) appendLine("- Competitive Analysis")
                if (timelineMilestones != null) appendLine("- Timeline with ${timelineMilestones.phases.size} phases")
                appendLine("- Conclusion with next steps")
                appendLine()
                appendLine("**Statistics:**")
                appendLine("- Total Word Count: $cumulativeWordCount / ${executionConfig.target_word_count}")
                appendLine("- Sections: ${proposalSections.size}")
                appendLine("- Generation Time: ${totalTime / 1000.0}s")
            }

            log.info("BusinessProposalTask completed: words=$cumulativeWordCount, sections=${proposalSections.size}, time=${totalTime}ms")

            task.safeComplete(
                "Business proposal generation complete: $cumulativeWordCount words in ${totalTime / 1000}s",
                log
            )
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during business proposal generation", e)
            logToTranscript("\n## Error Occurred\n\n${e.message}\n")
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

            val errorOutput = buildString {
                appendLine("# Error in Business Proposal Generation")
                appendLine()
                appendLine("**Proposal:** $proposalTitle")
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
        } finally {
            overviewTask.complete()
        }
        proposalStream?.close()
    }

    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""
        if (relatedFiles.isEmpty()) return ""
        log.debug("Loading ${relatedFiles.size} related context files")

        return buildString {
            appendLine("## Related Research Files")
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


    companion object {
        private val log: Logger = LoggerFactory.getLogger(BusinessProposalTask::class.java)
        @JvmStatic val BusinessProposal = TaskType(
          name = "BusinessProposal",
          category = "Writing",
          taskClass = BusinessProposalTask::class.java,
          executionConfigClass = BusinessProposalTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate comprehensive business proposals with ROI analysis and risk assessment",
          tooltipHtml = """
                        Generates complete, professional business proposals for various purposes.
                        <ul>
                          <li>Performs stakeholder analysis to understand decision-makers</li>
                          <li>Creates detailed ROI analysis with financial projections</li>
                          <li>Conducts risk assessment with mitigation strategies</li>
                          <li>Analyzes competitive alternatives and positioning</li>
                          <li>Develops timeline with milestones and dependencies</li>
                          <li>Writes compelling executive summary and sections</li>
                          <li>Includes optional revision passes for quality</li>
                          <li>Supports multiple proposal types (project, investment, grant, partnership, RFP)</li>
                          <li>Ideal for project proposals, funding requests, vendor responses, and business plans</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/ComicBookGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

open class ComicBookGenerationTask<T : ComicBookGenerationTask.ComicBookGenerationTaskExecutionConfigData>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    open class ComicBookGenerationTaskExecutionConfigData(
        @Description("The subject or scenario to develop into a comic book")
        var subject: String? = null,

        @Description("Target number of pages")
        var target_pages: Int = 5,

        @Description("Art style (e.g., 'manga', 'western superhero', 'noir', 'cartoon')")
        var art_style: String = "western superhero",
        @Description("Additional style details or visual guidelines")
        var style_details: String = "",


        @Description("Whether to generate images for each row")
        var generate_images: Boolean = true,

        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ComicBookGeneration.name,
        task_description = "Generate comic book for '$subject'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (target_pages <= 0) return "target_pages must be positive"
            return null
        }
    }

    data class ComicScript(
        val title: String = "",
        val premise: String = "",
        val characters: List<CharacterProfile> = emptyList(),
        val pages: List<PageScript> = emptyList()
    )

    data class CharacterProfile(
        val name: String = "",
        val description: String = "",
        val visual_traits: String = ""
    )

    data class PageScript(
        val page_number: Int = 1,
        val rows: List<RowScript> = emptyList()
    )

    data class RowScript(
        val row_number: Int = 1,
        val frames: List<FrameScript> = emptyList(),
        val visual_description: String = ""
    )

    data class FrameScript(
        val frame_number: Int = 1,
        val description: String = "",
        val dialog: List<DialogLine> = emptyList(),
        val caption: String? = null
    )

    data class DialogLine(
        val character: String = "",
        val text: String = ""
    )

    override fun promptSegment(): String {
        return """
ComicBookGeneration - Generate comic book scripts and visuals
  - **Use this tool** to create professional comic book scripts with a structured page/row/frame layout.
  - **Inputs:** Requires a 'subject', 'target_pages', and 'art_style'.
  - **Capabilities:** Generates character profiles, detailed visual descriptions, and can optionally generate AI visuals for each row (strip).
  - **Output:** Returns a summary of the generated script and links to saved image artifacts.
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
        try {
          log.info("Task 'ComicBookGeneration' started for subject: ${executionConfig?.subject}")
          transcript?.write("# Comic Book Generation Task\n\n".toByteArray())

          val genConfig = executionConfig
          if (genConfig == null || genConfig.subject.isNullOrBlank()) {
            val err = "CONFIGURATION ERROR: Invalid or missing subject."
            task.error(Exception(err))
            log.error(err)
            resultFn(err)
            return@submit
          }

          val api = defaultSmart.getChildClient(task)
          val tabs = TabbedDisplay(task)
          val overviewTask = tabs.newTask("Overview")
          overviewTask.header("Comic Book Generation: ${genConfig.subject}", 1)
          val statusBuffer = overviewTask.add("Generating script...".renderMarkdown())
          task.update()

            val parsingChatter = defaultFast.getChildClient(task)
            val scriptAgent = ParsedAgent(
                resultClass = ComicScript::class.java,
                prompt = """
                    You are a professional comic book writer. Create a detailed script for a comic book.
                    **Subject:** ${genConfig.subject}
                    **Target Pages:** ${genConfig.target_pages}
                    **Style:** ${genConfig.art_style}
                    ${if (genConfig.style_details.isNotBlank()) "Style Details: ${genConfig.style_details}" else ""}
                    
                    Structure the output with:
                    - Title and Premise
                    - Character Profiles (Name, Description, Visual Traits)
                    - Pages (numbered)
                    - Rows per page (usually 3-4 rows per page)
                    - Frames per row (usually 1-3 frames per row)
                    
                    For each frame, provide:
                    - Visual description
                    - Dialog (Character: Text)
                    - Captions (if any)
                    
                    For each row, provide a 'visual_description' that summarizes the row for an artist to draw as a strip. Include lighting, mood, and composition details. Ensure visual consistency across panels.
                """.trimIndent(),
                model = api,
                parsingChatter = parsingChatter,
                temperature = 0.7
            )

            val script = scriptAgent.answer(listOf("Generate comic script")).obj

            val scriptContent = buildString {
                appendLine("# ${script.title}")
                appendLine("*${script.premise}*")
                appendLine("## Characters")
                script.characters.forEach { appendLine("- **${it.name}**: ${it.description} (${it.visual_traits})") }
                appendLine("## Script")
                script.pages.forEach { page ->
                    appendLine("### Page ${page.page_number}")
                    page.rows.forEach { row ->
                        appendLine("**Row ${row.row_number}**")
                        row.frames.forEach { frame ->
                            appendLine("- Panel ${frame.frame_number}: ${frame.description}")
                            frame.dialog.forEach { d -> appendLine("  - **${d.character}**: \"${d.text}\"") }
                            if (frame.caption != null) appendLine("  - *Caption*: ${frame.caption}")
                        }
                    }
                }
            }

            val scriptTask = tabs.newTask("Script")
            scriptTask.add(scriptContent.renderMarkdown(true))
          transcript?.write("## Generated Script\n<details><summary>Expand Script Content</summary>\n\n$scriptContent\n\n</details>\n".toByteArray())
            task.update()

            statusBuffer?.setLength(0)
          statusBuffer?.append("✅ Script Generated".renderMarkdown())
            overviewTask.update()

            val characterImages = mutableMapOf<String, String>()
            val rowImages = mutableMapOf<String, String>()

            if (genConfig.generate_images) {
              if (!orchestrationConfig.autoFix) {
                val semaphore = Semaphore(0)
                val footer = acceptButtonFooter(task.ui) {
                  statusBuffer?.setLength(0)
                  statusBuffer?.append("✅ Script Approved. Generating visuals...".renderMarkdown())
                  overviewTask.update()
                  semaphore.release()
                }
                overviewTask.add("### Approval Required\nReview the script in the 'Script' tab and click below to generate visuals.".renderMarkdown())
                overviewTask.add(footer)
                log.info("Task paused. Waiting for user approval to generate visuals.")
                semaphore.acquire()
              }

              var lastImage: java.awt.image.BufferedImage? = null
                val charRefTask = tabs.newTask("Characters")
                charRefTask.header("Character References", 1)

                val charAgent = ImageProcessingAgent(
                    prompt = "Create a character sheet for a comic book. Style: ${genConfig.art_style} ${genConfig.style_details}",
                    model = orchestrationConfig.defaultImage.getChildClient(task),
                    temperature = 0.7
                )

                script.characters.forEach { char ->
                    try {
                        val charPrompt =
                            "Character: ${char.name}\nDescription: ${char.description}\nVisual Traits: ${char.visual_traits}\nStyle: ${genConfig.art_style} ${genConfig.style_details}"
                        val inputs = mutableListOf<ImageAndText>()
                        if (lastImage != null) {
                            inputs.add(ImageAndText(text = "Style Reference", image = lastImage))
                        }
                        inputs.add(ImageAndText(charPrompt))
                        val result = charAgent.answer(inputs)
                        val image = result.image
                        lastImage = image
                        val relativePath = "char_${char.name.replace(Regex("[^a-zA-Z0-9]"), "_")}.png"
                        val baos = ByteArrayOutputStream()
                        ImageIO.write(image, "png", baos)
                        val link = task.saveFile(relativePath, baos.toByteArray())
                        characterImages[char.name] = relativePath

                        charRefTask.header(char.name, 2)
                        charRefTask.append("<img src='$link' alt='${char.name}' style='max-width: 100%'/>")
                        charRefTask.add("*${char.description}*")

                      transcript?.write(
                        "## Character: ${char.name}\n\n![${char.name}]($link)".transcriptFilter()
                          .toByteArray() + "\n\n*${char.description}*\n\n".toByteArray()
                      )
                        task.update()
                    } catch (e: Exception) {
                        log.error("Failed to generate character image for ${char.name}", e)
                    }
                }

                statusBuffer?.setLength(0)
              statusBuffer?.append("✅ Script Generated<br/>Generating pages...".renderMarkdown())
                overviewTask.update()
                task.update()

                val imageAgent = ImageProcessingAgent(
                    prompt = "Create a comic book strip based on the description. Style: ${genConfig.art_style} ${genConfig.style_details}",
                    model = orchestrationConfig.defaultImage.getChildClient(task),
                    temperature = 0.7
                )

                val finalOutput = StringBuilder()
                finalOutput.append("# ${script.title}\n\n")

                script.pages.forEach { page ->
                    val pageTask = tabs.newTask("Page ${page.page_number}")
                    pageTask.header("Page ${page.page_number}: Visuals & Script", 1)
                    finalOutput.append("## Page ${page.page_number}\n\n")

                    page.rows.forEach { row ->
                        val rowPrompt = buildString {
                            appendLine("Comic strip row, style: ${genConfig.art_style} ${genConfig.style_details}")
                            appendLine("Description: ${row.visual_description}")
                            appendLine("Panels:")
                            row.frames.forEach { frame ->
                                appendLine("- Panel ${frame.frame_number}: ${frame.description}")
                                frame.dialog.forEach { d -> appendLine("  (Speech bubble for ${d.character}: \"${d.text}\")") }
                            }
                        }

                        try {
                            val imageInputs = mutableListOf<ImageAndText>()

                            val rowContent = row.visual_description + " " + row.frames.joinToString(" ") {
                                it.description + " " + it.dialog.joinToString(" ") { d -> d.character + " " + d.text }
                            }
                            script.characters.forEach { char ->
                                if (rowContent.contains(char.name, ignoreCase = true) || char.name.split(" ")
                                        .any { it.length > 3 && rowContent.contains(it, ignoreCase = true) }
                                ) {
                                    val path = characterImages[char.name]
                                    if (path != null) {
                                        try {
                                            val img = ImageIO.read(task.resolveUserFile(path))
                                            if (img != null) {
                                                imageInputs.add(
                                                    ImageAndText(
                                                        text = "Reference for ${char.name}: ${char.visual_traits}",
                                                        image = img
                                                    )
                                                )
                                            }
                                        } catch (e: Exception) {
                                            log.warn("Failed to load character reference", e)
                                        }
                                    }
                                }
                            }
                            if (lastImage != null) {
                                imageInputs.add(
                                    ImageAndText(
                                        text = "Previous Scene / Style Reference",
                                        image = lastImage
                                    )
                                )
                            }
                            imageInputs.add(ImageAndText(rowPrompt))

                            val result = imageAgent.answer(imageInputs)
                            val image = result.image
                            lastImage = image
                            val relativePath = "page_${page.page_number}_row_${row.row_number}.png"
                            val baos = ByteArrayOutputStream()
                            ImageIO.write(image, "png", baos)
                            val link = task.saveFile(relativePath, baos.toByteArray())
                            rowImages["${page.page_number}_${row.row_number}"] = relativePath

                            val rowHtml = """
                                <div class='comic-row'>
                                  <img src='$link' alt='Page ${page.page_number} Row ${row.row_number}' title='${row.visual_description}' style='width: 100%; max-width: 800px; border: 1px solid #ccc;' />
                                </div>
                            """.trimIndent()

                            pageTask.add(rowHtml.renderMarkdown(true))
                            finalOutput.append("![Row ${row.row_number}]($link)\n\n")
                          transcript?.write(
                            "![Page ${page.page_number} Row ${row.row_number}]($link)".transcriptFilter()
                              .toByteArray() + "\n\n".toByteArray()
                          )

                            val textContent = buildString {
                                row.frames.forEach { frame ->
                                    frame.dialog.forEach { d -> appendLine("**${d.character}**: ${d.text}") }
                                    if (frame.caption != null) appendLine("*${frame.caption}*")
                                }
                            }
                            pageTask.add(textContent.renderMarkdown(true))
                            finalOutput.append(textContent + "\n\n")

                        } catch (e: Exception) {
                            log.error("Failed to generate image for Page ${page.page_number} Row ${row.row_number}", e)
                            pageTask.add("**Failed to generate image**\n".renderMarkdown(true))
                        }
                        task.update()
                    }
                }

                statusBuffer?.setLength(0)
              statusBuffer?.append("✅ Images Generated".renderMarkdown())
                overviewTask.update()

              resultFn(
                """
                    ## Comic Book Generation Complete
                    * **Title:** ${script.title}
                    * **Pages:** ${script.pages.size}
                    * **Visuals:** Generated for all rows.
                    * **Artifacts:**
                      * Full Metadata: `comic_book.json`
                      * Character Images: ${characterImages.size} files
                      * Page Strips: ${rowImages.size} files
                """.trimIndent()
              )
            } else {
              resultFn("## Comic Book Script Generated\nTitle: ${script.title}\nPages: ${script.pages.size}\nVisual generation was disabled.")
            }
            task.saveFile(
                "comic_book.json",
                JsonUtil.toJson(
                    mapOf(
                        "config" to genConfig,
                        "script" to script,
                        "characterImages" to characterImages,
                        "rowImages" to rowImages
                    )
                ).toByteArray()
            )


            task.safeComplete("Comic Book Generation Complete", log)

        } catch (e: Exception) {
          task.error(e)
          log.error("Error in ComicBookGenerationTask: ${e.message}")
          transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
          resultFn("Error: ${e.message}")
        } finally {
          transcript?.close()
        }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ComicBookGenerationTask::class.java)
        @JvmStatic val ComicBookGeneration = TaskType(
          name = "ComicBookGeneration",
          category = "Writing",
          taskClass = ComicBookGenerationTask::class.java,
          executionConfigClass = ComicBookGenerationTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate comic book scripts and visuals",
          tooltipHtml = "Creates a comic book with page/row/frame structure and optional visual generation.",
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/DataTableCompilationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.BufferedWriter
import java.io.StringWriter
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class DataTableCompilationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DataTableCompilationTaskExecutionConfigData?
) : AbstractTask<DataTableCompilationTask.DataTableCompilationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "task_type")
    @JsonTypeIdResolver(TaskExecutionConfig.PlanTaskTypeIdResolver::class)
    class DataTableCompilationTaskExecutionConfigData(
        @Description("List of file glob patterns to include in the data compilation")
        var file_patterns: List<String> = listOf(),
        @Description("REQUIRED: Output file path where the compiled data table will be saved (CSV or JSON)")
        var output_file: String = "compiled_data.json",
        @Description("Instructions for identifying rows in the data")
        var row_identification_instructions: String = "",
        @Description("Instructions for identifying columns in the data")
        var column_identification_instructions: String = "",
        @Description("Instructions for extracting cell data")
        var cell_extraction_instructions: String = "",
        @Description("Description of the task")
        task_description: String? = null,
        @Description("List of task IDs this task depends on")
        task_dependencies: List<String>? = null,
        @Description("The current state of the task")
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = "DataTableCompilation",
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class Rows(val rows: List<Row> = listOf())
    data class Row(val id: String = "", val sourceFiles: List<String> = listOf())
    data class Columns(val columns: List<Column> = listOf())
    data class Column(val id: String = "", val name: String = "", val description: String = "")
    data class RowData(val rowId: String, val data: Map<String, String>)
    data class TableData(val rows: List<Map<String, Any>>, val columns: List<Column>)

    override fun promptSegment() = """
        DataTableCompilation - Compile structured data tables from multiple files.
        - Use file glob patterns to select source files.
        - Provide instructions for row and column identification.
        - Define cell extraction logic.
        - Specify output file (JSON/CSV/MD).
    """.trimIndent()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
        try {
            log.info("Starting DataTableCompilationTask. Output: ${executionConfig?.output_file}")
            renderTaskHeader(task)
            transcript?.write("# Data Table Compilation Task\n\n".toByteArray())
            transcript?.write("## Configuration\n\n".toByteArray())
            transcript?.write("- File Patterns: ${executionConfig?.file_patterns?.joinToString(", ")}\n".toByteArray())
            transcript?.write("- Output File: ${executionConfig?.output_file}\n\n".toByteArray())

        task.header("Step 1: Collecting Files", level = 3)
        val result = mutableListOf<Path>()
        val basePath = Paths.get(orchestrationConfig.absoluteWorkingDir ?: ".")
        executionConfig?.file_patterns?.forEach { pattern ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            Files.walk(basePath).use { paths ->
                paths
                    .filter { it.isRegularFile() }
                    .filter { matcher.matches(basePath.relativize(it)) }
                    .forEach { result.add(it) }
            }
        }
        val matchedFiles = result.distinct()
        if (matchedFiles.isEmpty()) {
            val errorMsg =
                "No files matched the provided patterns: ${executionConfig?.file_patterns?.joinToString(", ")}"
            transcript?.write("### Error\n\n$errorMsg\n\n".toByteArray())
            task.error(Exception(errorMsg))
            resultFn(errorMsg)
            return
        }
            task.add("Found ${matchedFiles.size} files matching the patterns".renderMarkdown())
            transcript?.write(
                "## Step 1: File Collection\n<details><summary>Matched Files</summary>\n\n${
                    matchedFiles.joinToString(
                        "\n"
                    ) { "- ${it.name}" }
                }\n</details>\n\n".toByteArray()
            )

        val fileContentString = matchedFiles.joinToString("\n\n") { file ->
            val content = readFileContent(file)
            "### ${file.name}\n```\n${content.take(1000)}${if (content.length > 1000) "..." else ""}\n```"
        }
            transcript?.write("### Source Content Preview\n<details><summary>Raw Content</summary>\n\n$fileContentString\n</details>\n\n".toByteArray())

        val typeConfig = typeConfig ?: throw RuntimeException()
        val chatter =
            (typeConfig.model?.let { orchestrationConfig.instance(it) }
                ?: defaultSmart).getChildClient(task)
        task.header("Step 2: Identifying Columns", level = 3)
        val columnsResponse = ParsedAgent(
            name = "ColumnIdentifier",
            resultClass = Columns::class.java,
            exampleInstance = Columns(
                listOf(
                    Column(
                        id = "Name",
                        name = "Name of the fruit",
                        description = "The name of the fruit in the row"
                    ),
                    Column(
                        id = "Color",
                        name = "Color of the fruit",
                        description = "The color of the fruit in the row"
                    ),
                    Column(
                        id = "Taste",
                        name = "Taste of the fruit",
                        description = "The taste of the fruit in the row"
                    )
                )
            ),
            prompt = """
                Analyze the provided files and identify distinct columns for a data table based on the following instructions:
                ${executionConfig?.column_identification_instructions}

                For each column you identify:
                1. Assign a unique column ID - should be a short, descriptive string
                2. Provide a detailed description of what the column represents
            """.trimIndent(),
            model = chatter,
            parsingChatter = defaultFast,
            temperature = orchestrationConfig.temperature,
            describer = TaskContextYamlDescriber(orchestrationConfig),
        ).answer(
            listOf(
                fileContentString
            ),
        )
        val columns = columnsResponse.obj
        val columnsList = columns.columns.map {
            Column(
                id = it.id,
                name = it.name,
                description = it.description,
            )
        }
            transcript?.write(
                "## Step 2: Column Identification\n<details><summary>Identified Columns</summary>\n\n${
                    columnsList.joinToString(
                        "\n"
                    ) { "- **${it.name}** (${it.id}): ${it.description}" }
                }\n</details>\n\n".toByteArray()
            )

            task.header("Step 3: Identifying Rows", level = 3)

        val rowsList = ParsedAgent(
            name = "RowIdentifier",
            resultClass = Rows::class.java,
            exampleInstance = Rows(
                listOf(
                    Row(
                        id = "Apple",
                        sourceFiles = listOf("apples.md", "apple_recipes.md")
                    ),
                    Row(
                        id = "Banana",
                        sourceFiles = listOf("bananas.md", "banana_recipes.md")
                    )
                )
            ),
            prompt = """
                You are a data extraction agent that is building a data table.
                Analyze the provided files and identify ALL distinct rows found in the data:

                Special Instructions:
                ${executionConfig?.row_identification_instructions}

                For each row you identify:
                1. Assign a unique row ID - should be a short, descriptive string
                2. List the source files that contain data for this row
            """.trimIndent(),
            model = chatter,
            parsingChatter = defaultFast,
            temperature = orchestrationConfig.temperature,
            describer = TaskContextYamlDescriber(orchestrationConfig),
        ).answer(
            listOf(
                fileContentString,
                "Columns:\n" + columnsList.joinToString("\n") { "- ${it.id}: ${it.name} (${it.description})" }
            ),
        )

            task.add("Identified ${rowsList.obj.rows.size} rows".renderMarkdown())
            task.add("Identified ${columnsList.size} columns".renderMarkdown())
            transcript?.write(
                "## Step 3: Row Identification\n<details><summary>Identified Rows</summary>\n\n${
                    rowsList.obj.rows.joinToString(
                        "\n"
                    ) { "- **${it.id}** (Sources: ${it.sourceFiles.joinToString(", ")})" }
                }\n</details>\n\n".toByteArray()
            )

        task.header("Step 4: Extracting Cell Data", level = 3)
        val tableData = mutableListOf<Map<String, Any>>()
        val progressTotal = rowsList.obj.rows.size
        var progressCurrent = 0
        val statusBuffer = task.add("Initializing extraction...")
        val tabs = TabbedDisplay(task)

        rowsList.obj.rows.forEach { row ->
            progressCurrent++
            statusBuffer?.setLength(0)
            statusBuffer?.append("Processing row $progressCurrent/$progressTotal: ${row.id}")
            val rowTask = tabs.newTask(row.id)
            task.update()
            val rowDataResponse = ParsedAgent(
                name = "CellExtractor",
                resultClass = RowData::class.java,
                exampleInstance = RowData(
                    rowId = "Apple",
                    data = mapOf(
                        "Name" to "Apple",
                        "Color" to "Red",
                        "Taste" to "Sweet"
                    )
                ),
                prompt = "Extract data for a data row for `${row.id}` from the provided source files.\n\n" +
                        "Expected Columns:\n${columnsList.joinToString("\n") { "- ${it.id}: ${it.name} (${it.description})" }}\n\n" +
                        "Special Instructions:\n${executionConfig?.cell_extraction_instructions}\n\n" +
                        "IMPORTANT: Respond with ONLY the single JSON object for the row `${row.id}`. Do NOT return a JSON array.",
                model = chatter.getChildClient(rowTask),
                parsingChatter = defaultFast,
                temperature = orchestrationConfig.temperature,
                describer = TaskContextYamlDescriber(orchestrationConfig),
            ).answer(
                listOf(
                    "Source Files:\n" + row.sourceFiles.mapNotNull { fileName ->
                        matchedFiles.find { it.name == fileName || it.toString().endsWith(fileName) }
                    }.joinToString("\n\n") { file ->
                        "### ${file.name}\n```\n${readFileContent(file).indent("  ")}\n```"
                    }
                ),
            )

            val rowData = rowDataResponse.obj
            val rowMap = mutableMapOf<String, Any>()
            rowMap["rowId"] = row.id
            rowMap.putAll(rowData.data)

            tableData.add(rowMap)
            transcript?.write(
                "### Row: ${row.id}\n<details><summary>Data</summary>\n\n${
                    rowData.data.entries.joinToString(
                        "\n"
                    ) { "- ${it.key}: ${it.value}" }
                }\n</details>\n\n".toByteArray()
            )
            rowTask.complete()
        }

        task.header("Step 5: Finalizing Table", level = 3)

        val outputPath = executionConfig?.output_file ?: "compiled_data.json"


            val markdownTable = StringWriter().use {
                BufferedWriter(it).use { bw ->
                    writeMarkdown(columnsList, bw, tableData)
                }
                it.toString()
            }

            val finalizeAction = {
                val data = when {
                    outputPath.endsWith(".json", ignoreCase = true) -> {
                        val finalData = TableData(tableData, columnsList)
                        jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(finalData)
                    }


                    outputPath.endsWith(".csv", ignoreCase = true) -> {
                        val sw = StringWriter()
                        BufferedWriter(sw).use { writer ->
                            val header = columnsList.joinToString(",") { "\"${it.name.replace("\"", "\"\"")}\"" }
                            writer.write(header)
                            writer.newLine()
                            tableData.forEach { row ->
                                val rowValues = columnsList.map { column ->
                                    val value = row[column.id]?.toString() ?: "N/A"
                                    "\"${value.replace("\"", "\"\"")}\""
                                }
                                writer.write(rowValues.joinToString(","))
                                writer.newLine()
                            }
                        }
                        sw.toString().toByteArray()
                    }

                    outputPath.endsWith(".md", ignoreCase = true) -> {
                        markdownTable.toByteArray()
                    }

                    else -> {
                        val finalData = TableData(tableData, columnsList)
                        jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(finalData)
                    }
                }

                val fileUrl = task.saveFile(outputPath, data)
                val summary = """
                ### Compilation Summary
                - **Source Files:** ${matchedFiles.size}
                - **Rows:** ${tableData.size}
                - **Columns:** ${columnsList.size}
                - **Output:** [$outputPath]($fileUrl)
                
                ### Compiled Data
                
                $markdownTable
            """.trimIndent()

                val finalTabs = TabbedDisplay(task)
                finalTabs["Summary"] = summary.renderMarkdown()
                finalTabs["Markdown"] = "```markdown\n$markdownTable\n```".renderMarkdown()

                transcript?.write("## Step 5: Final Results\n\n".toByteArray())
                transcript?.write(summary.toByteArray())

                resultFn("Compiled table with ${tableData.size} rows to $outputPath. Download: $fileUrl")
                task.complete()
            }

            if (orchestrationConfig.autoFix) {
                finalizeAction()
            } else {
                task.add("### Preview of Compiled Data".renderMarkdown())
                task.add(markdownTable.renderMarkdown())
                task.add(task.ui.hrefLink("Save Table & Complete", "btn btn-primary") {
                    finalizeAction()
                })
            }

        } catch (e: Exception) {
            task.error(e)
            log.error("Error in DataTableCompilationTask: ${e.message}", e)
            transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
            throw e
        } finally {
            transcript?.close()
        }
    }

    private fun writeMarkdown(
        columnsList: List<Column>,
        writer: BufferedWriter,
        tableData: MutableList<Map<String, Any>>
    ) {

        val header = columnsList.joinToString(" | ") { it.name }
        writer.write("| $header |")
        writer.newLine()

        val separator = columnsList.joinToString(" | ") { "---" }
        writer.write("| $separator |")
        writer.newLine()

        tableData.forEach { row ->
            val rowValues = columnsList.joinToString(" | ") { column ->
                val value = row[column.id]?.toString() ?: "N/A"
                value
            }
            writer.write("| $rowValues |")
            writer.newLine()
        }
    }

    private fun readFileContent(path: Path): String {
        return try {
            getInputFileContent(listOf(path.toString()), root) ?: "ERROR: Could not read file content"
        } catch (e: Exception) {
            log.warn("Failed to read file: $path", e)
            "ERROR: Could not read file content"
        }
    }

    companion object {
        @JvmStatic val DataTableCompilation = TaskType(
            name = "DataTableCompilation",
            category = "Writing",
            taskClass = DataTableCompilationTask::class.java,
            executionConfigClass = DataTableCompilationTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Compile structured data tables from multiple files",
            tooltipHtml = "Compile structured data tables from multiple files"
        )
        private val log = LoggerFactory.getLogger(DataTableCompilationTask::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/EmailCampaignTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EmailCampaignTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: EmailCampaignTaskExecutionConfigData?
) : AbstractTask<EmailCampaignTask.EmailCampaignTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class EmailCampaignTaskExecutionConfigData(
        @Description("The goal or purpose of the email campaign")
        var campaign_goal: String? = null,

        @Description("The product, service, or topic being promoted")
        var subject_matter: String? = null,

        @Description("Target audience description (demographics, role, pain points)")
        var target_audience: String = "general audience",

        @Description("Campaign type (e.g., 'welcome_series', 'nurture', 'sales', 're_engagement', 'newsletter', 'event_promotion')")
        var campaign_type: String = "nurture",

        @Description("Number of emails in the sequence")
        var num_emails: Int = 3,

        @Description("Recommended days between emails (e.g., [1, 3, 7] for day 1, day 4, day 11)")
        var send_intervals: List<Int>? = null,

        @Description("Brand voice and tone (e.g., 'professional', 'friendly', 'casual', 'authoritative', 'playful')")
        var brand_voice: String = "professional",

        @Description("Primary call-to-action (e.g., 'schedule_demo', 'download_resource', 'make_purchase', 'register_event')")
        var primary_cta: String = "learn_more",

        @Description("Whether to generate A/B test variants for subject lines")
        var generate_subject_variants: Boolean = true,

        @Description("Number of subject line variants per email (if enabled)")
        var subject_variants_count: Int = 3,

        @Description("Whether to include personalization tokens (e.g., {{first_name}}, {{company}})")
        var include_personalization: Boolean = true,

        @Description("Whether to include preview text (the snippet shown in inbox)")
        var include_preview_text: Boolean = true,

        @Description("Whether to include emoji in subject lines")
        var use_emoji: Boolean = false,

        @Description("Maximum subject line length in characters")
        var max_subject_length: Int = 60,

        @Description("Target email body length (MUST BE on of: 'short' <150 words, 'medium' 150-300, 'long' >300)")
        var body_length: String = "medium",

        @Description("Whether to include PS (postscript) sections")
        var include_ps: Boolean = true,

        @Description("Number of revision passes for quality improvement")
        var revision_passes: Int = 1,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as brand context for the task")
        var input_files: List<String>? = null,


        @Description("Related files or brand guidelines to incorporate")
        var related_files: List<String>? = null,

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
EmailCampaign - Generate multi-email marketing or outreach sequences.
- campaign_goal: Primary objective.
- subject_matter: Product or topic.
- target_audience: Who is receiving the emails.
- campaign_type: welcome_series, nurture, sales, etc.
- num_emails: Length of sequence (1-10).
- brand_voice: professional, friendly, etc.
- primary_cta: Main action desired.
- related_files: Brand guidelines or context.
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









        try {
            task.ui.pool.submit {
                try {
                    val startTime = System.currentTimeMillis()
                    log.info("Starting EmailCampaignTask for goal: '${executionConfig?.campaign_goal}'")
                    // Validate configuration
                    executionConfig?.validate()?.let { validationError ->
                        val err = "Configuration validation failed: $validationError"
                        log.error(err)
                        task.error(ValidatedObject.ValidationError(validationError, executionConfig))
                        resultFn("CONFIGURATION ERROR: $validationError")
                        return@submit
                    }
                    val campaignGoal = executionConfig?.campaign_goal!!
                    val api = defaultSmart ?: return@submit
                    val tabs = TabbedDisplay(task)
                    val overviewTask = tabs.newTask("Overview")
                    val overviewContent = buildString {
                        appendLine("# Email Campaign Generation")
                        appendLine("\n**Campaign Goal:** $campaignGoal")
                        appendLine("**Subject Matter:** ${executionConfig?.subject_matter}")
                        appendLine("\n## Configuration")
                        appendLine("- Campaign Type: ${executionConfig?.campaign_type}")
                        appendLine("- Target Audience: ${executionConfig?.target_audience}")
                        appendLine("- Number of Emails: ${executionConfig?.num_emails}")
                        appendLine("- Brand Voice: ${executionConfig?.brand_voice}")
                        appendLine("- Primary CTA: ${executionConfig?.primary_cta}")
                        appendLine("\n---")
                        appendLine("\n## Progress")
                        appendLine("\n### Phase 1: Campaign Strategy")
                        appendLine("*Developing overall campaign approach...*")
                    }
                    overviewTask.add(overviewContent.renderMarkdown())
                    transcript?.write("## Campaign Initialization\n$overviewContent\n".toByteArray())

            // Gather context
            val priorContext = getPriorCode(agent.executionState) ?: ""
            val contextFiles = getContextFiles()

            if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                val contextTask = tabs.newTask("Brand Context")

                contextTask.add("# Brand & Campaign Context\n".renderMarkdown())
                if (priorContext.isNotBlank()) {
                    contextTask.expandable("Prior Context", priorContext.renderMarkdown())
                }
                if (contextFiles.isNotBlank()) {
                    contextTask.expandable("Brand Guidelines", contextFiles.renderMarkdown())
                }

                transcript?.write(buildString {
                    appendLine("## Brand & Campaign Context")
                    if (priorContext.isNotBlank()) appendLine("<details><summary>Prior Context</summary>\n\n$priorContext\n</details>")
                    if (contextFiles.isNotBlank()) appendLine("<details><summary>Brand Guidelines</summary>\n\n$contextFiles\n</details>")
                }.toByteArray(Charsets.UTF_8))
            }

            // Phase 1: Develop campaign strategy
            log.info("Phase 1: Developing campaign strategy")
            val strategyTask = tabs.newTask("Strategy")

            strategyTask.add(
                buildString {
                    appendLine("# Campaign Strategy")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** Analyzing audience and developing approach...")
                    appendLine()
                }.renderMarkdown
                    ()
            )

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
                parsingChatter = defaultFast
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

                    strategyTask.add(strategyContent.renderMarkdown())
                    transcript?.write("\n### Phase 1: Strategy\n<details><summary>Strategy Details</summary>\n\n$strategyContent\n</details>\n".toByteArray())

                    overviewTask.add("✅ Phase 1 Complete: Strategy developed\n".renderMarkdown())
                    overviewTask.add("\n### Phase 2: Email Sequence Outline\n*Creating detailed outline for each email...*\n".renderMarkdown())

            // Phase 2: Create email outlines
            log.info("Phase 2: Creating email sequence outline")
            val outlineTask = tabs.newTask("Sequence Outline")

            outlineTask.add(
                buildString {
                    appendLine("# Email Sequence Outline")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** Planning ${executionConfig.num_emails} emails...")
                    appendLine()
                }.renderMarkdown
                    ()
            )

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
                    parsingChatter = defaultFast
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

                    outlineTask.add(outlineContent.renderMarkdown())
                    transcript?.write("\n### Phase 2: Outlines\n<details><summary>Sequence Outlines</summary>\n\n$outlineContent\n</details>\n".toByteArray())

                    overviewTask.add("✅ Phase 2 Complete: ${outlines.size} emails outlined\n".renderMarkdown())
                    overviewTask.add("\n### Phase 3: Email Generation\n*Writing emails with subject lines...*\n".renderMarkdown())

            // Phase 3: Generate each email
            log.info("Phase 3: Generating emails")
            val generatedEmails = mutableListOf<EmailContent>()
            val allSubjectVariants = mutableMapOf<Int, List<SubjectLine>>()

            outlines.forEach { outline ->
                log.info("Generating email ${outline.email_number}/${executionConfig.num_emails}")

                overviewTask.add("- Email ${outline.email_number}: ${outline.main_message.truncateForDisplay(50)} ".renderMarkdown())

                val emailTask = tabs.newTask("Email ${outline.email_number}")

                emailTask.add(
                    buildString {
                        appendLine("# Email ${outline.email_number}")
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** Generating content...")
                        appendLine()
                    }.renderMarkdown
                        ()
                )

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
                        parsingChatter = defaultFast
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
                        parsingChatter = defaultFast
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
                    parsingChatter = defaultFast
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


                emailTask.add(emailDisplay.renderMarkdown())
                transcript?.write("\n#### Email ${outline.email_number}\n<details><summary>Content</summary>\n\n$emailDisplay\n</details>\n".toByteArray())

                overviewTask.add("✅ (${emailContent.word_count} words)\n".renderMarkdown())
            }

                    overviewTask.add("✅ Phase 3 Complete: All emails generated\n".renderMarkdown())

            // Phase 4: Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                overviewTask.add("\n### Phase 4: Revision\n*Refining email sequence...*\n".renderMarkdown())

                log.info("Phase 4: Performing ${executionConfig.revision_passes} revision pass(es)")
                val revisionTask = tabs.newTask("Revision")

                revisionTask.add(
                    buildString {
                        appendLine("# Revision Process")
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
                        appendLine()
                    }.renderMarkdown
                        ()
                )

                repeat(executionConfig.revision_passes) { passNum ->
                    log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")
                    transcript?.write(
                        "\n### Revision Pass ${passNum + 1}\n".toByteArray()
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
                        }.renderMarkdown()
                    )
                }

                overviewTask.add("✅ Phase 4 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown())
            }

            // Phase 5: Final Assembly
                    overviewTask.add("\n### Phase 5: Final Assembly\n*Compiling complete campaign...*\n".renderMarkdown())

            log.info("Phase 5: Assembling final campaign")
            val finalTask = tabs.newTask("Complete Campaign")

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
                    val totalDays = executionConfig.send_intervals?.sum()
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

                    finalTask.add(finalCampaign.renderMarkdown())
                    val campaignFile = task.saveFile(
                        "campaigns/email_campaign_${System.currentTimeMillis()}.md",
                        finalCampaign.toByteArray()
                    )
                    finalTask.add("\n\n[Download Campaign Markdown]($campaignFile)".renderMarkdown())
                    transcript?.write("\n### Final Campaign\n<details><summary>Full Markdown</summary>\n\n$finalCampaign\n</details>\n".toByteArray())

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
                }.renderMarkdown()
            )


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

                    task.complete()
            resultFn(finalResult)


                } catch (e: Exception) {
                    log.error("Error during email campaign generation: ${e.message}", e)
                    task.error(e)
                    transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                    resultFn("Error during email campaign generation: ${e.message}")
                }
            }
        } finally {
            transcript?.close()

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

    companion object {
        private val log: Logger = LoggerFactory.getLogger(EmailCampaignTask::class.java)
        @JvmStatic val EmailCampaign = TaskType(
            name = "EmailCampaign",
            category = "Writing",
            taskClass = EmailCampaignTask::class.java,
            executionConfigClass = EmailCampaignTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Generate complete email sequences for marketing, sales, or outreach",
            tooltipHtml = """
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
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/InteractiveStoryTask.kt

```
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
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class InteractiveStoryTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: InteractiveStoryTaskExecutionConfigData?
) : AbstractTask<InteractiveStoryTask.InteractiveStoryTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    protected val codeFiles = mutableMapOf<Path, String>()

    class InteractiveStoryTaskExecutionConfigData(
        @Description("The premise or starting scenario for the interactive story")
        val premise: String? = null,

        @Description("The genre of the story (e.g., 'fantasy', 'sci-fi', 'mystery', 'horror', 'romance')")
        val genre: String = "fantasy",

        @Description("The target audience (e.g., 'children', 'young_adult', 'adult')")
        val target_audience: String = "young_adult",

        @Description("The tone of the story (e.g., 'lighthearted', 'serious', 'dark', 'humorous')")
        val tone: String = "serious",

        @Description("Number of major decision points in the story")
        val num_decision_points: Int = 5,

        @Description("Number of choices at each decision point")
        val choices_per_decision: Int = 3,

        @Description("Whether to track state variables (inventory, relationships, stats)")
        val track_state_variables: Boolean = true,

        @Description("State variables to track (e.g., 'health', 'reputation', 'gold', 'ally_trust')")
        val state_variables: List<String>? = null,

        @Description("Whether to ensure all paths lead to meaningful endings")
        val prevent_dead_ends: Boolean = true,

        @Description("Number of distinct endings to create")
        val num_endings: Int = 3,

        @Description("Whether to optimize for replay value with distinct experiences")
        val optimize_replay_value: Boolean = true,

        @Description("Average word count per story segment")
        val segment_word_count: Int = 300,
        @Description("Writing style (e.g., 'descriptive', 'action-packed', 'dialogue-heavy', 'introspective')")
        val writing_style: String = "descriptive",

        @Description("Point of view (e.g., 'second_person', 'first_person', 'third_person')")
        val point_of_view: String = "second_person",
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input context for the story")
        val input_files: List<String>? = null,


        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = InteractiveStory.name,
        task_description = task_description ?: "Generate interactive story: '$premise'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (premise.isNullOrBlank()) {
                return "premise must not be null or blank"
            }
            if (num_decision_points < 1 || num_decision_points > 20) {
                return "num_decision_points must be between 1 and 20, got: $num_decision_points"
            }
            if (choices_per_decision < 2 || choices_per_decision > 5) {
                return "choices_per_decision must be between 2 and 5, got: $choices_per_decision"
            }
            if (num_endings < 1 || num_endings > 10) {
                return "num_endings must be between 1 and 10, got: $num_endings"
            }
            if (segment_word_count < 100 || segment_word_count > 1000) {
                return "segment_word_count must be between 100 and 1000, got: $segment_word_count"
            }
            if (genre.isNullOrBlank()) {
                return "genre must not be null or blank"
            }
            if (point_of_view.isBlank()) {
                return "point_of_view must not be blank"
            }
            if (!input_files.isNullOrEmpty()) {
                input_files.forEach { pattern ->
                    if (pattern.isBlank()) {
                        return "input_files patterns must not be blank"
                    }
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class StoryStructure(
        @Description("The story title")
        val title: String = "",
        @Description("Opening segment that sets the scene")
        val opening: String = "",
        @Description("Decision points in the story")
        val decision_points: List<DecisionPoint> = emptyList(),
        @Description("Possible endings")
        val endings: List<Ending> = emptyList(),
        @Description("State variables being tracked")
        val tracked_variables: Map<String, String> = emptyMap()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (opening.isBlank()) return "opening must not be blank"
            if (decision_points.isEmpty()) return "decision_points must not be empty"
            if (endings.isEmpty()) return "endings must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class DecisionPoint(
        @Description("Unique identifier for this decision point")
        val id: String = "",
        @Description("The narrative segment leading to this decision")
        val narrative: String = "",
        @Description("The question or situation requiring a choice")
        val decision_prompt: String = "",
        @Description("Available choices")
        val choices: List<Choice> = emptyList(),
        @Description("Current state variable values at this point")
        val state_snapshot: Map<String, Int> = emptyMap()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (id.isBlank()) return "id must not be blank"
            if (narrative.isBlank()) return "narrative must not be blank"
            if (decision_prompt.isBlank()) return "decision_prompt must not be blank"
            if (choices.isEmpty()) return "choices must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Choice(
        @Description("The choice text presented to the reader")
        val text: String = "",
        @Description("ID of the next decision point or ending this leads to")
        val leads_to: String = "",
        @Description("State variable changes from this choice")
        val state_changes: Map<String, Int> = emptyMap(),
        @Description("Immediate consequence description")
        val immediate_consequence: String = "",
        @Description("Long-term impact on the story")
        val long_term_impact: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (text.isBlank()) return "text must not be blank"
            if (leads_to.isBlank()) return "leads_to must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Ending(
        @Description("Unique identifier for this ending")
        val id: String = "",
        @Description("Type of ending (e.g., 'triumph', 'tragedy', 'bittersweet', 'twist')")
        val ending_type: String = "",
        @Description("The final narrative segment")
        val narrative: String = "",
        @Description("Required state conditions to reach this ending")
        val required_conditions: Map<String, String> = emptyMap(),
        @Description("Choices that led to this ending")
        val path_summary: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (id.isBlank()) return "id must not be blank"
            if (ending_type.isBlank()) return "ending_type must not be blank"
            if (narrative.isBlank()) return "narrative must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class StorySegment(
        @Description("The segment ID")
        val id: String = "",
        @Description("The narrative content")
        val content: String = "",
        @Description("Word count")
        val word_count: Int = 0,
        @Description("State changes in this segment")
        val state_changes: Map<String, Int> = emptyMap()
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
 InteractiveStory - Create choose-your-own-adventure narratives with branching paths
  ** Optionally, list input files (supports glob patterns) to be examined for context
  ** Specify the premise or starting scenario
  ** Define genre, tone, and target audience
  ** Set number of decision points and choices per decision
  ** Enable state variable tracking (health, reputation, inventory, etc.)
  ** Prevent dead ends to ensure all paths lead somewhere meaningful
  ** Create multiple distinct endings based on player choices
  ** Optimize for replay value with different experiences
  ** Track consequences across choices for coherent storytelling
  ** Produces complete interactive narrative with decision tree
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
        // Initialize transcript
        val transcriptStream = task.newLogStream("Transcript")
        val transcriptWriter = transcriptStream.bufferedWriter()
        // Gather input context from files and messages
        val inputContext = getInputFileCode() +
                if (messages.isNotEmpty()) "\n\n## User Input\n\n${messages.joinToString("\n\n")}" else ""


        log.info("Starting InteractiveStoryTask for premise: '${executionConfig?.premise}'")

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            transcriptWriter.close()
            resultFn("CONFIGURATION ERROR: $validationError")
            return
        }

        val premise = executionConfig?.premise
        if (premise.isNullOrBlank()) {
            log.error("No premise specified for interactive story")
            task.safeComplete("CONFIGURATION ERROR: No premise specified", log)
            transcriptWriter.close()
            resultFn("CONFIGURATION ERROR: No premise specified")
            return
        }

        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Interactive Story Generation")
            appendLine()
            appendLine("**Premise:** $premise")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Genre: ${executionConfig.genre}")
            appendLine("- Target Audience: ${executionConfig.target_audience}")
            appendLine("- Tone: ${executionConfig.tone}")
            appendLine("- Point of View: ${executionConfig.point_of_view}")
            appendLine("- Writing Style: ${executionConfig.writing_style}")
            appendLine("- Decision Points: ${executionConfig.num_decision_points}")
            appendLine("- Choices per Decision: ${executionConfig.choices_per_decision}")
            appendLine("- Number of Endings: ${executionConfig.num_endings}")
            appendLine("- Track State Variables: ${if (executionConfig.track_state_variables) "✓" else "✗"}")
            if (executionConfig.track_state_variables && !executionConfig.state_variables.isNullOrEmpty()) {
                appendLine("- State Variables: ${executionConfig.state_variables.joinToString(", ")}")
            }
            appendLine("- Prevent Dead Ends: ${if (executionConfig.prevent_dead_ends) "✓" else "✗"}")
            appendLine("- Optimize Replay Value: ${if (executionConfig.optimize_replay_value) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Story Structure Planning")
            appendLine("*Creating decision tree and story architecture...*")
        }
        // Write to transcript
        transcriptWriter.apply {
            write("# Interactive Story Generation Transcript\n\n")
            write("**Premise:** $premise\n\n")
            write("## Configuration\n\n")
            write("- Genre: ${executionConfig.genre}\n")
            write("- Target Audience: ${executionConfig.target_audience}\n")
            write("- Tone: ${executionConfig.tone}\n")
            write("- Point of View: ${executionConfig.point_of_view}\n")
            write("- Writing Style: ${executionConfig.writing_style}\n")
            write("- Decision Points: ${executionConfig.num_decision_points}\n")
            write("- Choices per Decision: ${executionConfig.choices_per_decision}\n")
            write("- Number of Endings: ${executionConfig.num_endings}\n")
            write("- Track State Variables: ${if (executionConfig.track_state_variables) "✓" else "✗"}\n")
            if (executionConfig.track_state_variables && !executionConfig.state_variables.isNullOrEmpty()) {
                write("- State Variables: ${executionConfig.state_variables.joinToString(", ")}\n")
            }
            write(
                "\n**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n"
            )
            write("---\n\n")
            flush()
        }
        overviewTask.add(overviewContent.renderMarkdown())
        task.update()

            val storyBuilder = StringBuilder()
            storyBuilder.append("# Interactive Story: $premise\n\n")

        try {
            // Gather context from input files and messages
            val priorContext = getPriorCode(agent.executionState)
            val combinedContext = (if (inputContext.isNotBlank()) inputContext else "") +
                    (if (priorContext.isNotBlank()) "\n\n## Prior Context\n\n$priorContext" else "")

            // Gather context
            if (priorContext.isNotBlank()) {
                transcriptWriter.apply {
                    write("## Context from Previous Tasks\n\n")
                    write(priorContext.truncateForDisplay(2000))
                    write("\n\n---\n\n")
                    flush()
                }
                log.debug("Found prior context: ${priorContext.length} chars")
                val contextTask = tabs.newTask("Context")
                contextTask.add(
                    buildString {
                        appendLine("# Context from Previous Tasks")
                        appendLine()
                        appendLine(priorContext.truncateForDisplay(2000))
                    }.renderMarkdown()
                )
                task.update()
            }

            // Phase 1: Create story structure and decision tree
            transcriptWriter.apply {
                write("## Phase 1: Story Structure Planning\n\n")
                write("Creating decision tree and story architecture...\n\n")
                flush()
            }
            log.info("Phase 1: Creating story structure")
            val structureTask = tabs.newTask("Story Structure")

            structureTask.add(
                buildString {
                    appendLine("# Story Structure & Decision Tree")
                    appendLine()
                    appendLine("**Status:** Planning narrative branches and decision points...")
                    appendLine()
                }.renderMarkdown()
            )
            task.update()

            val stateVars = if (executionConfig.track_state_variables) {
                executionConfig.state_variables ?: listOf("health", "reputation", "resources")
            } else {
                emptyList()
            }
            // First, create a high-level outline
            val outlineAgent = ChatAgent(
                prompt = """
 You are an expert interactive fiction designer. Create a high-level outline for a branching story.
 Premise: $premise
 Story Parameters:
- Genre: ${executionConfig.genre}
- Target Audience: ${executionConfig.target_audience}
- Tone: ${executionConfig.tone}
- Decision Points: ${executionConfig.num_decision_points}
- Choices per Decision: ${executionConfig.choices_per_decision}
- Number of Endings: ${executionConfig.num_endings}
${if (combinedContext.isNotBlank()) "Additional Context:\n${combinedContext.truncateForDisplay(1000)}\n" else ""}
Create a brief outline with:
1. A compelling title
2. A one-paragraph opening concept
3. List of ${executionConfig.num_decision_points} decision point IDs and brief descriptions (e.g., "decision_1: Choose path in forest")
4. List of ${executionConfig.num_endings} ending IDs and types (e.g., "ending_triumph: Hero succeeds")
5. A simple flow showing how decisions connect (decision_1 -> decision_2 or ending_1)
Keep it concise - just the structure, not full narratives.
          """.trimIndent(),
                model = api,
                temperature = 0.7
            )
            val outline = outlineAgent.answer(listOf("Create outline"))
            log.debug("Generated outline: ${outline.length} chars")
            transcriptWriter.apply {
                write("### Story Outline\n\n")
                write(outline)
                write("\n\n")
                flush()
            }
            structureTask.add(
                buildString {
                    appendLine("## Story Outline")
                    appendLine()
                    appendLine(outline)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** Building detailed structure...")
                    appendLine()
                }.renderMarkdown()
            )
            task.update()
            // Now create the detailed structure in smaller pieces

            val structureAgent = ParsedAgent(
                resultClass = StoryStructure::class.java,
                prompt = """
You are an expert interactive fiction designer. Create a detailed story structure based on this outline.

OUTLINE:
$outline

Story Parameters:
- Genre: ${executionConfig.genre}
- Target Audience: ${executionConfig.target_audience}
- Tone: ${executionConfig.tone}
${if (executionConfig.track_state_variables) "- State Variables to Track: ${stateVars.joinToString(", ")}" else ""}


Expand the outline into a complete structure with:
1. The title from the outline
2. A brief opening description (2-3 sentences, NOT the full narrative)
3. Decision points with:
   - A unique ID (e.g., "decision_1", "decision_2" - use snake_case for compatibility)
   - A brief narrative description (1-2 sentences)
   - A clear decision prompt
   - ${executionConfig.choices_per_decision} meaningful choices
   - Each choice should lead to another decision point or an ending
4. Endings with unique IDs and types
${if (executionConfig.track_state_variables) "5. State variable definitions and how they're affected by choices" else ""}


IMPORTANT: Keep descriptions brief. Full narratives will be written later.
Focus on structure and connections, not detailed prose.
          """.trimIndent(),
                model = api,
                temperature = 0.5,
                parsingChatter = defaultFast
            )

            val structure = structureAgent.answer(listOf("Create detailed structure from outline")).obj

            // Validate structure
            structure.validate()?.let { validationError ->
                log.error("Structure validation failed: $validationError")
                structureTask.error(ValidatedObject.ValidationError(validationError, structure))
                transcriptWriter.apply {
                    write("**ERROR:** Structure validation failed: $validationError\n\n")
                    flush()
                    close()
                }
                task.safeComplete("Structure validation failed: $validationError", log)
                resultFn("ERROR: Structure validation failed: $validationError")
                return
            }

            log.info("Generated structure: ${structure.decision_points.size} decision points, ${structure.endings.size} endings")
            transcriptWriter.apply {
                write("### Generated Story Structure\n\n")
                write("**Title:** ${structure.title}\n\n")
                write("**Opening:** ${structure.opening}\n\n")
                write("**Decision Points:** ${structure.decision_points.size}\n\n")
                structure.decision_points.forEach { dp ->
                    write("- ${dp.id}: ${dp.decision_prompt}\n")
                    dp.choices.forEach { choice ->
                        write("  - ${choice.text} → ${choice.leads_to}\n")
                    }
                }
                write("\n**Endings:** ${structure.endings.size}\n\n")
                structure.endings.forEach { ending ->
                    write("- ${ending.id}: ${ending.ending_type}\n")
                }
                if (structure.tracked_variables.isNotEmpty()) {
                    write("\n**Tracked Variables:**\n\n")
                    structure.tracked_variables.forEach { (name, description) ->
                        write("- $name: $description\n")
                    }
                }
                write("\n---\n\n")
                flush()
            }


            val structureContent = buildString {
                appendLine("## ${structure.title}")
                appendLine()
                appendLine("### Opening")
                appendLine(structure.opening.truncateForDisplay(500))
                appendLine()
                if (structure.tracked_variables.isNotEmpty()) {
                    appendLine("### Tracked Variables")
                    structure.tracked_variables.forEach { (name, description) ->
                        appendLine("- **$name:** $description")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("### Decision Tree")
                appendLine()
                appendLine("```")
                appendLine("START")
                appendLine("  ↓")
                structure.decision_points.forEachIndexed { index, dp ->
                    appendLine("${dp.id}: ${dp.decision_prompt.truncateForDisplay(60)}")
                    dp.choices.forEach { choice ->
                        appendLine("  → ${choice.text.truncateForDisplay(50)} → ${choice.leads_to}")
                    }
                    if (index < structure.decision_points.size - 1) {
                        appendLine("  ↓")
                    }
                }
                appendLine()
                appendLine("ENDINGS:")
                structure.endings.forEach { ending ->
                    appendLine("  • ${ending.id}: ${ending.ending_type}")
                }
                appendLine("```")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Decision Points Summary")
                structure.decision_points.forEach { dp ->
                    appendLine("#### ${dp.id}")
                    appendLine("**Prompt:** ${dp.decision_prompt}")
                    appendLine()
                    appendLine("**Choices:**")
                    dp.choices.forEach { choice ->
                        appendLine("- ${choice.text}")
                        if (choice.state_changes.isNotEmpty()) {
                            appendLine("  - State changes: ${choice.state_changes.entries.joinToString(", ") { "${it.key} ${if (it.value >= 0) "+" else ""}${it.value}" }}")
                        }
                        appendLine("  - Leads to: ${choice.leads_to}")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("### Endings Summary")
                structure.endings.forEach { ending ->
                    appendLine("#### ${ending.id}: ${ending.ending_type}")
                    if (ending.required_conditions.isNotEmpty()) {
                        appendLine("**Conditions:** ${ending.required_conditions.entries.joinToString(", ") { "${it.key} ${it.value}" }}")
                    }
                    appendLine()
                }
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            structureTask.add(structureContent.renderMarkdown())
            task.update()

            overviewTask.add("✅ Phase 1 Complete: Story structure created\n".renderMarkdown())
            overviewTask.add("\n### Phase 2: Opening Segment\n*Writing the story opening...*\n".renderMarkdown())
            task.update()

            // Phase 2: Write opening segment
            transcriptWriter.apply {
                write("## Phase 2: Opening Segment\n\n")
                write("Writing the story opening...\n\n")
                flush()
            }
            log.info("Phase 2: Writing opening segment")
            val openingTask = tabs.newTask("Opening")

            openingTask.add(
                buildString {
                    appendLine("# Opening Segment")
                    appendLine()
                    appendLine("**Status:** Writing opening narrative...")
                    appendLine()
                }.renderMarkdown()
            )
            task.update()

            val openingAgent = ParsedAgent(
                resultClass = StorySegment::class.java,
                prompt = """
You are a skilled ${executionConfig.genre} writer. Write the opening segment of this interactive story.

Title: ${structure.title}
Premise: $premise

Story Parameters:
- Genre: ${executionConfig.genre}
- Tone: ${executionConfig.tone}
- Point of View: ${executionConfig.point_of_view}
- Writing Style: ${executionConfig.writing_style}
- Target Audience: ${executionConfig.target_audience}

Opening Outline: ${structure.opening}

Write an engaging opening segment (~${executionConfig.segment_word_count} words) that:
1. Immediately hooks the reader
2. Establishes the setting and atmosphere
3. Introduces the protagonist (the reader in ${executionConfig.point_of_view} POV)
4. Sets up the initial situation
5. Creates anticipation for the first decision
6. Matches the ${executionConfig.tone} tone and ${executionConfig.writing_style} style

${if (executionConfig.track_state_variables) "Initialize state variables: ${stateVars.joinToString(", ")}" else ""}

Make it immersive and compelling. The reader should feel invested immediately.
          """.trimIndent(),
                model = api,
                temperature = 0.8,
                parsingChatter = defaultFast
            )

            var openingSegment = openingAgent.answer(listOf("Write opening")).obj
            transcriptWriter.apply {
                write("### Opening Segment\n\n")
                write(openingSegment.content)
                write("\n\n**Word Count:** ${openingSegment.word_count}\n\n")
                write("---\n\n")
                flush()
            }

            openingTask.add(
                buildString {
                    appendLine("## ${structure.title}")
                    appendLine()
                    appendLine(openingSegment.content)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Word Count:** ${openingSegment.word_count}")
                    if (openingSegment.state_changes.isNotEmpty()) {
                        appendLine()
                        appendLine("**Initial State:**")
                        openingSegment.state_changes.forEach { (variable, value) ->
                            appendLine("- $variable: $value")
                        }
                    }
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }.renderMarkdown()
            )
            task.update()

            storyBuilder.append("## ${structure.title}\n\n")
            storyBuilder.append(openingSegment.content)
            storyBuilder.append("\n\n---\n\n")

            overviewTask.add("✅ Phase 2 Complete: Opening written (${openingSegment.word_count} words)\n".renderMarkdown())
            overviewTask.add("\n### Phase 3: Decision Points\n*Writing branching narrative segments...*\n".renderMarkdown())

            // Phase 3: Write each decision point
            transcriptWriter.apply {
                write("## Phase 3: Decision Points\n\n")
                write("Writing branching narrative segments...\n\n")
                flush()
            }
            log.info("Phase 3: Writing decision points")
            val decisionSegments = mutableMapOf<String, StorySegment>()
            var cumulativeWordCount = openingSegment.word_count

            structure.decision_points.forEachIndexed { index, decisionPoint ->
                log.info("Writing decision point ${index + 1}/${structure.decision_points.size}: ${decisionPoint.id}")

                overviewTask.add(
                    "- ${decisionPoint.id}: ${
                        decisionPoint.decision_prompt.truncateForDisplay(
                            50
                        )
                    } ".renderMarkdown()
                )
                task.update()

                val dpTask = tabs.newTask("${decisionPoint.id}")

                dpTask.add(
                    buildString {
                        appendLine("# ${decisionPoint.id}")
                        appendLine()
                        appendLine("**Status:** Writing decision point narrative...")
                        appendLine()
                    }.renderMarkdown()
                )
                task.update()

                // Build context from previous segments
                val previousContext = if (decisionSegments.isNotEmpty()) {
                    buildString {
                        appendLine("## Previous Story Context")
                        val recentSegments = decisionSegments.values.toList().takeLast(2)
                        recentSegments.forEach { seg ->
                            appendLine("### ${seg.id}")
                            appendLine(seg.content.takeLast(300))
                            appendLine()
                        }
                    }
                } else {
                    buildString {
                        appendLine("## Opening Context")
                        appendLine(openingSegment.content.takeLast(300))
                    }
                }

                val decisionAgent = ParsedAgent(
                    resultClass = StorySegment::class.java,
                    prompt = """
You are a skilled ${executionConfig.genre} writer. Write the narrative segment leading to this decision point.

Title: ${structure.title}
Decision Point: ${decisionPoint.id}

Decision Outline:
- Narrative: ${decisionPoint.narrative}
- Decision Prompt: ${decisionPoint.decision_prompt}

Available Choices:
${decisionPoint.choices.joinToString("\n") { "- ${it.text}" }}

$previousContext

Story Parameters:
- Genre: ${executionConfig.genre}
- Tone: ${executionConfig.tone}
- Point of View: ${executionConfig.point_of_view}
- Writing Style: ${executionConfig.writing_style}

Write a narrative segment (~${executionConfig.segment_word_count} words) that:
1. Flows naturally from the previous segment
2. Develops the story and builds tension
3. Presents the situation requiring a decision
4. Makes all choices feel meaningful and distinct
5. Maintains the ${executionConfig.tone} tone
6. Ends with the decision prompt clearly presented

${
                        if (executionConfig.track_state_variables && decisionPoint.state_snapshot.isNotEmpty()) {
                            "Current State: ${decisionPoint.state_snapshot.entries.joinToString(", ") { "${it.key}: ${it.value}" }}"
                        } else ""
                    }

Make the reader feel the weight of their choice. Each option should feel viable but lead to different outcomes.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = defaultFast
                )

                var segment = decisionAgent.answer(listOf("Write decision point")).obj.copy(id = decisionPoint.id)
                decisionSegments[decisionPoint.id] = segment
                cumulativeWordCount += segment.word_count
                transcriptWriter.apply {
                    write("### ${decisionPoint.id}\n\n")
                    write(segment.content)
                    write("\n\n**Decision:** ${decisionPoint.decision_prompt}\n\n")
                    decisionPoint.choices.forEach { choice ->
                        write("- ${choice.text} → ${choice.leads_to}\n")
                    }
                    write("\n**Word Count:** ${segment.word_count}\n\n---\n\n")
                    flush()
                }

                dpTask.add(
                    buildString {
                        appendLine("## ${decisionPoint.id}")
                        appendLine()
                        appendLine(segment.content)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("### ${decisionPoint.decision_prompt}")
                        appendLine()
                        decisionPoint.choices.forEachIndexed { choiceIndex, choice ->
                            appendLine("**${choiceIndex + 1}. ${choice.text}**")
                            if (choice.immediate_consequence.isNotBlank()) {
                                appendLine("   - *${choice.immediate_consequence}*")
                            }
                            if (choice.state_changes.isNotEmpty()) {
                                appendLine("   - State changes: ${choice.state_changes.entries.joinToString(", ") { "${it.key} ${if (it.value >= 0) "+" else ""}${it.value}" }}")
                            }
                            appendLine()
                        }
                        appendLine("---")
                        appendLine()
                        appendLine("**Word Count:** ${segment.word_count}")
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }.renderMarkdown()
                )
                task.update()

                storyBuilder.append("## ${decisionPoint.id}\n\n")
                storyBuilder.append(segment.content)
                storyBuilder.append("\n\n")
                storyBuilder.append("### ${decisionPoint.decision_prompt}\n\n")
                decisionPoint.choices.forEachIndexed { choiceIndex, choice ->
                    storyBuilder.append("${choiceIndex + 1}. ${choice.text}\n")
                }
                storyBuilder.append("\n---\n\n")

                overviewTask.add("✅ (${segment.word_count} words)\n".renderMarkdown())
                task.update()
            }

            overviewTask.add("✅ Phase 3 Complete: All decision points written\n".renderMarkdown())
            overviewTask.add("\n### Phase 4: Endings\n*Writing story conclusions...*\n".renderMarkdown())
            task.update()

            // Phase 4: Write endings
            transcriptWriter.apply {
                write("## Phase 4: Endings\n\n")
                write("Writing story conclusions...\n\n")
                flush()
            }
            log.info("Phase 4: Writing endings")
            val endingSegments = mutableMapOf<String, StorySegment>()

            structure.endings.forEachIndexed { index, ending ->
                log.info("Writing ending ${index + 1}/${structure.endings.size}: ${ending.id}")

                overviewTask.add("- ${ending.id}: ${ending.ending_type} ".renderMarkdown())
                task.update()

                val endingTask = tabs.newTask("${ending.id}")

                endingTask.add(
                    buildString {
                        appendLine("# ${ending.id}")
                        appendLine()
                        appendLine("**Status:** Writing ending narrative...")
                        appendLine()
                    }.renderMarkdown()
                )
                task.update()

                val endingAgent = ParsedAgent(
                    resultClass = StorySegment::class.java,
                    prompt = """
You are a skilled ${executionConfig.genre} writer. Write a satisfying ending for this interactive story.

Title: ${structure.title}
Ending: ${ending.id}
Ending Type: ${ending.ending_type}

Ending Outline: ${ending.narrative}

${
                        if (ending.required_conditions.isNotEmpty()) {
                            "This ending is reached when: ${ending.required_conditions.entries.joinToString(", ") { "${it.key} ${it.value}" }}"
                        } else ""
                    }

${
                        if (ending.path_summary.isNotEmpty()) {
                            "Key choices that led here:\n${ending.path_summary.joinToString("\n") { "- $it" }}"
                        } else ""
                    }

Story Parameters:
- Genre: ${executionConfig.genre}
- Tone: ${executionConfig.tone}
- Point of View: ${executionConfig.point_of_view}
- Writing Style: ${executionConfig.writing_style}

Write an ending segment (~${executionConfig.segment_word_count} words) that:
1. Provides a satisfying conclusion to the story
2. Reflects the consequences of the reader's choices
3. Matches the ${ending.ending_type} ending type
4. Maintains the ${executionConfig.tone} tone
5. Gives a sense of closure while honoring the journey
6. Makes the reader feel their choices mattered

Make this ending feel earned and meaningful. It should resonate with the path taken.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = defaultFast
                )

                var endingSegment = endingAgent.answer(listOf("Write ending")).obj.copy(id = ending.id)
                endingSegments[ending.id] = endingSegment
                cumulativeWordCount += endingSegment.word_count
                transcriptWriter.apply {
                    write("### ${ending.id}: ${ending.ending_type}\n\n")
                    write(endingSegment.content)
                    write("\n\n**Word Count:** ${endingSegment.word_count}\n\n---\n\n")
                    flush()
                }

                endingTask.add(
                    buildString {
                        appendLine("## ${ending.id}: ${ending.ending_type}")
                        appendLine()
                        appendLine(endingSegment.content)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**THE END**")
                        appendLine()
                        appendLine("**Word Count:** ${endingSegment.word_count}")
                        if (ending.required_conditions.isNotEmpty()) {
                            appendLine()
                            appendLine("**Conditions to Reach:**")
                            ending.required_conditions.forEach { (condition, value) ->
                                appendLine("- $condition: $value")
                            }
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }.renderMarkdown()
                )
                task.update()

                storyBuilder.append("## ${ending.id}: ${ending.ending_type}\n\n")
                storyBuilder.append(endingSegment.content)
                storyBuilder.append("\n\n**THE END**\n\n---\n\n")

                overviewTask.add("✅ (${endingSegment.word_count} words)\n".renderMarkdown())
                task.update()
            }

            overviewTask.add("✅ Phase 4 Complete: All endings written\n".renderMarkdown())
            overviewTask.add("\n### Phase 5: Interactive Map\n*Generating playable story map...*\n".renderMarkdown())
            task.update()

            // Phase 5: Create interactive story map
            log.info("Phase 5: Creating interactive story map")
            val mapTask = tabs.newTask("Story Map")

            val storyMap = buildString {
                appendLine("# ${structure.title} - Interactive Story Map")
                appendLine()
                appendLine("## How to Play")
                appendLine("1. Start with the Opening segment")
                appendLine("2. At each decision point, choose one of the available options")
                appendLine("3. Follow the path indicated by your choice")
                appendLine("4. Continue until you reach an ending")
                appendLine("5. Try different choices to discover all ${structure.endings.size} endings!")
                appendLine()
                if (structure.tracked_variables.isNotEmpty()) {
                    appendLine("## Tracked Variables")
                    structure.tracked_variables.forEach { (name, description) ->
                        appendLine("- **$name:** $description")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("## START: Opening")
                appendLine()
                appendLine(openingSegment.content)
                appendLine()
                val firstNodeId = structure.decision_points.firstOrNull()?.id ?: "ending"
                appendLine("**→ [Continue to: $firstNodeId](#$firstNodeId)**")
                appendLine()
                appendLine("---")
                appendLine()

                structure.decision_points.forEach { dp ->
                    val segment = decisionSegments[dp.id]
                    appendLine("## ${dp.id}")
                    appendLine()
                    if (segment != null) {
                        appendLine(segment.content)
                        appendLine()
                    }
                    appendLine("### ${dp.decision_prompt}")
                    appendLine()
                    dp.choices.forEachIndexed { index, choice ->
                        appendLine("**Choice ${index + 1}: ${choice.text}**")
                        if (choice.immediate_consequence.isNotBlank()) {
                            appendLine()
                            appendLine("*${choice.immediate_consequence}*")
                        }
                        if (choice.state_changes.isNotEmpty()) {
                            appendLine()
                            appendLine("State changes: ${choice.state_changes.entries.joinToString(", ") { "${it.key} ${if (it.value >= 0) "+" else ""}${it.value}" }}")
                        }
                        appendLine()
                        appendLine("**→ [Continue to: ${choice.leads_to}](#${choice.leads_to})**")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }

                structure.endings.forEach { ending ->
                    val segment = endingSegments[ending.id]
                    appendLine("## ${ending.id}: ${ending.ending_type}")
                    appendLine()
                    if (segment != null) {
                        appendLine(segment.content)
                        appendLine()
                    }
                    appendLine("**THE END**")
                    appendLine()
                    if (ending.required_conditions.isNotEmpty()) {
                        appendLine("*This ending is reached when: ${ending.required_conditions.entries.joinToString(", ") { "${it.key} ${it.value}" }}*")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }

                appendLine("## Story Statistics")
                appendLine()
                appendLine("- Total Word Count: $cumulativeWordCount")
                appendLine("- Decision Points: ${structure.decision_points.size}")
                appendLine("- Total Choices: ${structure.decision_points.sumOf { it.choices.size }}")
                appendLine("- Possible Endings: ${structure.endings.size}")
                appendLine("- Unique Paths: ~${calculateUniquePaths(structure)}")
            }

            mapTask.add(storyMap.renderMarkdown())
            task.update()

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime
            transcriptWriter.apply {
                write("## Generation Complete\n\n")
                write("**Statistics:**\n\n")
                write("- Total Word Count: $cumulativeWordCount\n")
                write("- Decision Points: ${structure.decision_points.size}\n")
                write("- Total Choices: ${structure.decision_points.sumOf { it.choices.size }}\n")
                write("- Endings: ${structure.endings.size}\n")
                write("- Estimated Unique Paths: ~${calculateUniquePaths(structure)}\n")
                write("- Total Time: ${totalTime / 1000.0}s\n\n")
                write(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n"
                )
                flush()
                close()
            }


            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Generation Complete")
                    appendLine()
                    appendLine("**Statistics:**")
                    appendLine("- Total Word Count: $cumulativeWordCount")
                    appendLine("- Decision Points: ${structure.decision_points.size}")
                    appendLine("- Total Choices: ${structure.decision_points.sumOf { it.choices.size }}")
                    appendLine("- Endings: ${structure.endings.size}")
                    appendLine("- Estimated Unique Paths: ~${calculateUniquePaths(structure)}")
                    appendLine("- Total Time: ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown()
            )
            task.update()

            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# Interactive Story Summary: ${structure.title}")
                appendLine()
                appendLine("A complete interactive story of **$cumulativeWordCount words** with **${structure.decision_points.size} decision points** and **${structure.endings.size} endings** was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Structure:**")
                appendLine("- Opening segment")
                appendLine("- ${structure.decision_points.size} branching decision points")
                appendLine("- ${structure.decision_points.sumOf { it.choices.size }} total choices")
                appendLine("- ${structure.endings.size} distinct endings")
                appendLine("- Estimated ${calculateUniquePaths(structure)} unique story paths")
                appendLine()
                appendLine("> The complete interactive story map is available in the Story Map tab for play-through.")
            }

            log.info("InteractiveStoryTask completed: words=$cumulativeWordCount, decisions=${structure.decision_points.size}, endings=${structure.endings.size}, time=${totalTime}ms")

            task.safeComplete(
                "Interactive story generation complete: $cumulativeWordCount words, ${structure.decision_points.size} decisions, ${structure.endings.size} endings in ${totalTime / 1000}s",
                log
            )
            resultFn(buildFinalResultWithLinks(task, finalResult, storyBuilder.toString(), cumulativeWordCount, structure, totalTime))

        } catch (e: Exception) {
            log.error("Error during interactive story generation", e)
            transcriptWriter.close()
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
                }.renderMarkdown()
            )
            task.update()

            val errorOutput = buildString {
                appendLine("# Error in Interactive Story Generation")
                appendLine()
                appendLine("**Premise:** $premise")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                if (storyBuilder.isNotEmpty()) {
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(storyBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
    }

    private fun buildFinalResultWithLinks(
        task: SessionTask,
        summary: String,
        storyMap: String,
        wordCount: Int,
        structure: StoryStructure,
        totalTime: Long
    ): String {
        return try {
            // Save story map to file
            val mapLink = task.saveFile("story_map.md", storyMap.toByteArray())
            // Save summary to file
            val summaryLink = task.saveFile("story_summary.md", summary.toByteArray())
            buildString {
                appendLine("# Interactive Story Generation Complete")
                appendLine()
                appendLine("**Story:** ${structure.title}")
                appendLine("**Word Count:** $wordCount")
                appendLine("**Decision Points:** ${structure.decision_points.size}")
                appendLine("**Endings:** ${structure.endings.size}")
                appendLine("**Generation Time:** ${totalTime / 1000.0}s")
                appendLine()
                appendLine("## Output Files")
                appendLine()
                appendLine("- [Story Map (Interactive)]($mapLink) - Complete playable story with all paths")
                appendLine()
                appendLine("- [Story Summary]($summaryLink) - Generation summary and statistics")
                appendLine()
                appendLine("## Quick Stats")
                appendLine()
                appendLine("- Total Choices: ${structure.decision_points.sumOf { it.choices.size }}")
                appendLine("- Unique Paths: ~${calculateUniquePaths(structure)}")
                appendLine("- Tracked Variables: ${structure.tracked_variables.size}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(summary)
            }
        } catch (e: Exception) {
            log.error("Failed to create output files", e)
            buildString {
                appendLine("# Interactive Story Generation Complete")
                appendLine()
                appendLine("**Note:** Could not save detailed output files, but story was generated successfully.")
                appendLine()
                appendLine(summary)
            }
        }
    }

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    matcher.matches(root.relativize(it.toPath())) -> true
                    it.isDirectory -> true
                    else -> false
                }
            }
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                val content = codeFiles[file.toPath()] ?: file.readText()
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }


    private fun calculateUniquePaths(structure: StoryStructure): Int {
        // Simple estimation: multiply choices at each decision point
        // This is an upper bound; actual paths may converge
        val choicesPerDecision = structure.decision_points.map { it.choices.size }
        return if (choicesPerDecision.isEmpty()) {
            1
        } else {
            choicesPerDecision.fold(1) { acc, choices ->
                (acc * choices).coerceAtMost(1000) // Cap at 1000 to avoid overflow
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InteractiveStoryTask::class.java)
        @JvmStatic val InteractiveStory = TaskType(
          name = "InteractiveStory",
          category = "Writing",
          taskClass = InteractiveStoryTask::class.java,
          executionConfigClass = InteractiveStoryTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Create choose-your-own-adventure narratives with branching paths",
          tooltipHtml = """
                        Generates complete interactive stories with meaningful choices and multiple endings.
                        <ul>
                          <li>Creates detailed story structure with decision tree</li>
                          <li>Writes opening segment that hooks the reader</li>
                          <li>Develops branching narrative segments for each decision point</li>
                          <li>Generates multiple distinct endings based on player choices</li>
                          <li>Tracks state variables (health, reputation, inventory, etc.)</li>
                          <li>Ensures all paths lead to meaningful endings (no dead ends)</li>
                          <li>Optimizes for replay value with significantly different experiences</li>
                          <li>Tracks consequences across choices for coherent storytelling</li>
                          <li>Produces complete playable interactive story map</li>
                          <li>Ideal for interactive fiction, training scenarios, educational content, and games</li>
                        </ul>
                      """,
        )

        private val textExtensions = setOf(
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
            "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
        )

        fun isTextFile(file: File): Boolean {
            return textExtensions.contains(file.extension.lowercase())
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/IterativeGraphGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONReader
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONWriter
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import java.io.ByteArrayOutputStream


open class IterativeGraphGenerationTask<T : IterativeGraphGenerationTask.IterativeGraphGenerationTaskExecutionConfigData>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, IterativeGraphGenerationTask.IterativeGraphGenerationTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    open class IterativeGraphGenerationTaskExecutionConfigData(
        @Description("The goal or question the graph should answer/represent")
        var goal_prompt: String? = null,

        @Description("Input text to analyze")
        var context_data: String? = null,

        @Description("Input files to analyze")
        var input_files: List<String>? = null,

        @Description("Optional JSON file to initialize graph from")
        var initial_graph_file: String? = null,

        @Description("Maximum number of iterations")
        var max_iterations: Int = 20,

        @Description("Maximum number of nodes")
        var max_nodes: Int = 50,

        @Description("Maximum number of edges")
        var max_edges: Int = 100,

        @Description("Allowed node types (labels)")
        var node_types: List<String> = listOf("Concept", "Entity"),

        @Description("Allowed edge types (labels)")
        var edge_types: List<String> = listOf("RELATES_TO"),

        @Description("Tasks that must complete before this one")
        task_dependencies: List<String>? = null,

        @Description("The current state of the task")
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = IterativeGraphGeneration.name,
        task_description = "Generate knowledge graph for '${goal_prompt ?: "unknown"}'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (goal_prompt.isNullOrBlank()) return "goal_prompt is required"
            if (max_iterations <= 0) return "max_iterations must be positive"
            return null
        }
    }

    class IterativeGraphGenerationTaskTypeConfig(
        @Description("The prompt template used for each iteration of graph expansion")
        var iterationPromptTemplate: String = """
            Goal: {goal_prompt}
            
            Schema:
            Node Types: {node_types}
            Edge Types: {edge_types}
            
            Current Graph State:
            {graph_view}
            
            Context Data ({chunk_index}/{total_chunks}):
            {current_text}
            
            Provide a list of actions to expand the graph based on the context and goal.
            If the graph is complete, set is_finished to true.
            
            For ADD_EDGE, 'from' and 'to' must be maps of properties that uniquely identify existing nodes.
            For ADD_NODE, provide properties that uniquely identify the node (e.g. name).
            For MERGE_NODES, provide 'target' (keep) and 'source' (remove) properties to identify nodes.
        """.trimIndent()
    ) : TaskTypeConfig()

    data class GraphActionList(
        @Description("The reasoning behind the proposed actions")
        var reasoning: String = "",
        @Description("The list of graph operations to perform")
        var actions: List<GraphAction> = emptyList(),
        @Description("Whether the graph generation is complete")
        var is_finished: Boolean = false
    ) : ValidatedObject

    data class GraphAction(
        @Description("Type of action to perform: ADD_NODE, ADD_EDGE, or MERGE_NODES")
        var type: String = "ADD_NODE",
        @Description("Label of the node or edge (REQUIRED for ADD_*)")
        var label: String = "",
        @Description("Properties of the node or edge")
        var properties: Map<String, Any> = emptyMap(),
        @Description("For ADD_EDGE: properties to identify the 'from' node")
        var from: Map<String, Any>? = null,
        @Description("For ADD_EDGE: properties to identify the 'to' node")
        var to: Map<String, Any>? = null,
        @Description("For MERGE_NODES: The properties of the node to keep")
        var target: Map<String, Any>? = null,
        @Description("For MERGE_NODES: The properties of the node to remove (merging into target)")
        var source: Map<String, Any>? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (type != "ADD_NODE" && type != "ADD_EDGE" && type != "MERGE_NODES") return "Invalid action type: $type"
            if ((type == "ADD_NODE" || type == "ADD_EDGE") && label.isBlank()) return "Label is required"
            if (type == "ADD_EDGE") {
                if (from.isNullOrEmpty()) return "From properties are required for ADD_EDGE"
                if (to.isNullOrEmpty()) return "To properties are required for ADD_EDGE"
            }
            if (type == "MERGE_NODES") {
                if (target.isNullOrEmpty()) return "Target properties are required for MERGE_NODES"
                if (source.isNullOrEmpty()) return "Source properties are required for MERGE_NODES"
            }
            return null
        }
    }

    override fun promptSegment(): String {
        return """
IterativeGraphGeneration - Build knowledge graphs incrementally
  * goal_prompt: The goal or question the graph should answer/represent.
  * context_data: Input text to analyze.
  * input_files: Input files to analyze.
  * node_types/edge_types: Allowed labels for nodes and edges.
  * Use this to extract entities and relationships for complex knowledge management and visualization.
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
            try {
                val config = executionConfig!!
                log.info("Starting IterativeGraphGenerationTask for goal: ${config.goal_prompt}")
                transcript?.write("## Iterative Graph Generation\n\n".toByteArray())
                transcript?.write("Goal: **${config.goal_prompt}**\n\n".toByteArray())

                val graph: Graph = TinkerGraph.open()
                if (!config.initial_graph_file.isNullOrBlank()) {
                    val file = task.resolveUserFile(config.initial_graph_file!!)
                    if (file?.exists() == true) {
                        try {
                            GraphSONReader.build().create().readGraph(file.inputStream(), graph)
                            transcript?.write("Loaded initial graph from `${config.initial_graph_file}`\n".toByteArray())
                        } catch (e: Exception) {
                            transcript?.write("Error loading initial graph: ${e.message}\n".toByteArray())
                        }
                    }
                }
                val g: GraphTraversalSource = graph.traversal()

                val tabs = TabbedDisplay(task)
                val mainTask = tabs.newTask("Progress")

                // Load context
                val fileContext = try {
                    super.getInputFileContent(config.input_files, root, treatDocumentsAsText = true)
                } catch (e: Exception) {
                    log.warn("Failed to load input files: ${e.message}")
                    ""
                }

                val fullContext = """
                    ${config.context_data ?: ""}
                    
                    $fileContext
                """.trimIndent()

                val chunkSize = 4000
                val contextChunks = fullContext.chunked(chunkSize).ifEmpty { listOf("") }
                var currentChunkIndex = 0

                var iteration = 0
                val api = defaultSmart.getChildClient(task)

                while (iteration < config.max_iterations && currentChunkIndex < contextChunks.size) {
                    iteration++
                    val currentText = contextChunks[currentChunkIndex]

                    // 1. Serialize State
                    val nodeCount = g.V().count().next()
                    val edgeCount = g.E().count().next()

                    if (nodeCount >= config.max_nodes) {
                        transcript?.write("Max nodes reached ($nodeCount). Stopping.\n".toByteArray())
                        break
                    }
                    if (edgeCount >= config.max_edges) {
                        transcript?.write("Max edges reached ($edgeCount). Stopping.\n".toByteArray())
                        break
                    }

                    val graphView = if (nodeCount < 50) {
                        serializeGraphSimple(g)
                    } else {
                        "Graph Summary: $nodeCount nodes, $edgeCount edges. (Too large to display full state)"
                    }

                    mainTask.header("Iteration $iteration", 3)
                    mainTask.add("Starting Nodes: $nodeCount, Edges: $edgeCount".renderMarkdown())

                    // 2. Agent Decision
                    val prompt = (typeConfig?.iterationPromptTemplate ?: "")
                        .replace("{goal_prompt}", config.goal_prompt ?: "")
                        .replace("{node_types}", config.node_types.toString())
                        .replace("{edge_types}", config.edge_types.toString())
                        .replace("{graph_view}", graphView)
                        .replace("{chunk_index}", (currentChunkIndex + 1).toString())
                        .replace("{total_chunks}", contextChunks.size.toString())
                        .replace("{current_text}", currentText)

                    val agentResponse = ParsedAgent(
                        resultClass = GraphActionList::class.java,
                        prompt = prompt,
                        model = api,
                        temperature = 0.2,
                        parsingChatter = api,
                        singleStage = true
                    ).answer(listOf("Analyze and update graph"))

                    val response = agentResponse.obj
                    transcript?.write("### Iteration $iteration\n".toByteArray())
                    transcript?.write("<details><summary>Reasoning</summary>\n\n${response.reasoning}\n\n</details>\n".toByteArray())
                    mainTask.expandable("Reasoning", response.reasoning.renderMarkdown())

                    // 3. Apply Actions
                    var actionsApplied = 0
                    response.actions.forEach { action ->
                        try {
                            when (action.type) {
                                "ADD_NODE" -> {
                                    var traversal = g.V().hasLabel(action.label)
                                    action.properties.forEach { (k, v) ->
                                        if (k == "name" || k == "id") traversal = traversal.has(k, v)
                                    }
                                    if (!traversal.hasNext()) {
                                        val v = g.addV(action.label).next()
                                        action.properties.forEach { (k, valObj) ->
                                            v.property(k, valObj)
                                        }
                                        actionsApplied++
                                    }
                                }

                                "ADD_EDGE" -> {
                                    val fromV = findVertex(g, action.from)
                                    val toV = findVertex(g, action.to)
                                    if (fromV != null && toV != null) {
                                        if (!g.V(fromV.id()).out(action.label).where(`__`.hasId<Vertex>(toV.id()))
                                                .hasNext()
                                        ) {
                                            val e = g.addE(action.label).from(fromV).to(toV).next()
                                            action.properties.forEach { (k, valObj) ->
                                                e.property(k, valObj)
                                            }
                                            actionsApplied++
                                        }
                                    }
                                }

                                "MERGE_NODES" -> {
                                    val keepV = findVertex(g, action.target)
                                    val removeV = findVertex(g, action.source)
                                    if (keepV != null && removeV != null && keepV.id() != removeV.id()) {
                                        g.V(removeV.id()).outE().forEachRemaining { e ->
                                            g.addE(e.label()).from(keepV).to(e.inVertex()).next()
                                        }
                                        g.V(removeV.id()).inE().forEachRemaining { e ->
                                            g.addE(e.label()).from(e.outVertex()).to(keepV).next()
                                        }
                                        g.V(removeV.id()).drop().iterate()
                                        transcript?.write("Merged node `${removeV.id()}` into `${keepV.id()}`\n".toByteArray())
                                        actionsApplied++
                                    }
                                }

                                else -> transcript?.write("Unknown action type: ${action.type}\n".toByteArray())
                            }
                        } catch (e: Exception) {
                            log.warn("Error applying action: $action", e)
                            transcript?.write("Error applying action: $action - ${e.message}\n".toByteArray())
                        }
                    }
                    transcript?.write("Applied $actionsApplied actions.\n".toByteArray())

                    if (nodeCount > 0) {
                        val mermaid = toMermaid(g)
                        mainTask.add("```mermaid\n$mermaid\n```".renderMarkdown())
                        transcript?.write("<details><summary>Graph Visualization</summary>\n\n```mermaid\n$mermaid\n```\n\n</details>\n".toByteArray())
                    }

                    if (response.is_finished) {
                        transcript?.write("Agent signaled completion.\n".toByteArray())
                        break
                    }

                    if (actionsApplied < 2 || iteration % 3 == 0) {
                        currentChunkIndex++
                        transcript?.write("Advancing to context chunk ${currentChunkIndex + 1}\n".toByteArray())
                    }
                }

                // Export
                val os = ByteArrayOutputStream()
                GraphSONWriter.build().create().writeGraph(os, graph)
                val graphJSON = os.toByteArray()
                val fileUrl = task.saveFile("graph.json", graphJSON)

                val summary = """
                    ## Graph Generation Complete
                    * **Nodes:** ${g.V().count().next()}
                    * **Edges:** ${g.E().count().next()}
                    * **Artifact:** [Download Graph JSON]($fileUrl)
                """.trimIndent()

                mainTask.add(summary.renderMarkdown())
                task.update()
                task.safeComplete(summary, log)
                resultFn(summary)
            } catch (e: Exception) {
                task.error(e)
                log.error("Error in IterativeGraphGenerationTask", e)
                transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                throw e
            } finally {
                transcript?.close()
            }
        }
    }

    private fun findVertex(g: GraphTraversalSource, props: Map<String, Any>?): Vertex? {
        if (props == null || props.isEmpty()) return null
        // Try exact match first
        var t = g.V()
        props.forEach { (k, v) -> t = t.has(k, v) }
        if (t.hasNext()) return t.next()

        // Fallback: Case insensitive search for 'name' property
        if (props.containsKey("name")) {
            val nameVal = props["name"].toString().lowercase()
            val candidates = g.V().filter {
                it.get().property<String>("name").orElse("").lowercase() == nameVal
            }
            if (candidates.hasNext()) return candidates.next()
        }

        return null
    }

    private fun toMermaid(g: GraphTraversalSource): String {
        val sb = StringBuilder()
        sb.append("graph TD\n")
        g.V().forEachRemaining { v ->
            val label = v.label()
            val name =
                if (v.property<Any>("name").isPresent) v.property<Any>("name").value().toString() else v.id().toString()
            sb.append("  v${v.id()}[\"$label: ${name.replace("\"", "'")}\"]\n")
        }
        g.E().forEachRemaining { e ->
            sb.append("  v${e.outVertex().id()} -->|\"${e.label()}\"| v${e.inVertex().id()}\n")
        }
        return sb.toString()
    }


    private fun serializeGraphSimple(g: GraphTraversalSource): String {
        val sb = StringBuilder()
        sb.append("Nodes:\n")
        g.V().forEachRemaining { v ->
            sb.append(" - [${v.label()}] ")
            v.properties<Any>().forEachRemaining { p -> sb.append("${p.key()}=${p.value()}, ") }
            sb.append("\n")
        }
        sb.append("Edges:\n")
        g.E().forEachRemaining { e ->
            val from = e.outVertex()
            val to = e.inVertex()
            // Try to get a name property for display
            val fromName = if (from.property<Any>("name").isPresent) from.property<Any>("name").value() else from.id()
            val toName = if (to.property<Any>("name").isPresent) to.property<Any>("name").value() else to.id()
            sb.append(" - $fromName --[${e.label()}]--> $toName\n")
        }
        return sb.toString()
    }

    companion object {
        private val log = LoggerFactory.getLogger(IterativeGraphGenerationTask::class.java)
        @JvmStatic val IterativeGraphGeneration = TaskType(
            name = "IterativeGraphGeneration",
            category = "Writing",
            taskClass = IterativeGraphGenerationTask::class.java,
            executionConfigClass = IterativeGraphGenerationTaskExecutionConfigData::class.java,
            taskSettingsClass = IterativeGraphGenerationTaskTypeConfig::class.java,
            description = "Extract structured knowledge from unstructured data by iteratively building an entity-relationship graph.",
            tooltipHtml = """
                      Constructs a knowledge graph by iteratively analyzing context and adding nodes/edges.
                      <ul>
                        <li>Processes large contexts by chunking and iterative refinement</li>
                        <li>Supports custom schemas for nodes and edges</li>
                        <li>Visualizes progress using Mermaid diagrams</li>
                        <li>Allows merging nodes to resolve entities</li>
                        <li>Exports the final graph as GraphSON JSON</li>
                        <li>Ideal for mapping complex domains, research analysis, and knowledge extraction</li>
                      </ul>
                      """.trimIndent(),
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/JournalismReasoningTask.kt

```
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/NarrativeGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
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
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

open class NarrativeGenerationTask<T : NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    open class NarrativeGenerationTaskExecutionConfigData(

        @Description("The subject or scenario to develop into a full narrative")
        val subject: String? = null,

        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input context for the narrative")
        val input_files: List<String>? = null,

        @Description("Narrative elements to consider (characters, setting, conflict, timeline, etc.)")
        val narrative_elements: Map<String, Any>? = null,

        @Description("Target word count for the complete narrative")
        val target_word_count: Int = 5000,

        @Description("Number of acts in the narrative structure (typically 3 or 5)")
        val number_of_acts: Int = 3,

        @Description("Average number of scenes per act")
        val scenes_per_act: Int = 3,

        @Description("Writing style (e.g., 'literary', 'thriller', 'technical', 'conversational')")
        val writing_style: String = "literary",

        @Description("Point of view (e.g., 'first person', 'third person limited', 'third person omniscient')")
        val point_of_view: String = "third person limited",

        @Description("Tone (e.g., 'dramatic', 'humorous', 'suspenseful', 'reflective')")
        val tone: String = "dramatic",

        @Description("Whether to include detailed scene descriptions")
        val detailed_descriptions: Boolean = true,

        @Description("Whether to include character dialogue")
        val include_dialogue: Boolean = true,

        @Description("Whether to show internal character thoughts")
        val show_internal_thoughts: Boolean = true,

        @Description("Number of revision passes for each scene")
        val revision_passes: Int = 2,

        @Description("Whether to generate images for each scene")
        val generate_scene_images: Boolean = true,

        @Description("Whether to generate a cover image for the narrative")
        val generate_cover_image: Boolean = true,

        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = NarrativeGeneration.name,
        task_description = "Generate full narrative for '$subject'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            // Validate target_word_count
            if (target_word_count <= 0) {
                return "target_word_count must be positive, got: $target_word_count"
            }
            // Validate number_of_acts
            if (number_of_acts <= 0) {
                return "number_of_acts must be positive, got: $number_of_acts"
            }
            // Validate scenes_per_act
            if (scenes_per_act <= 0) {
                return "scenes_per_act must be positive, got: $scenes_per_act"
            }
            // Validate revision_passes
            if (revision_passes < 0) {
                return "revision_passes cannot be negative, got: $revision_passes"
            }
            return null
        }
    }

    data class NarrativeOutline(
        val title: String = "",
        val premise: String = "",
        val characters: List<CharacterProfile> = emptyList(),
        val settings: List<SettingProfile> = emptyList(),
        val acts: List<ActOutline> = emptyList(),
        val estimated_word_count: Int = 0
    )

    data class CharacterProfile(
        val name: String = "",
        val description: String = "",
        val role: String = "",
        val traits: List<String> = emptyList()
    )

    data class SettingProfile(
        val setting_id: String = "",
        val description: String = "",
        val atmosphere: String = "",
        val significance: String = ""
    )

    data class HighLevelOutline(
        val title: String = "",
        val premise: String = "",
        val characters: List<CharacterProfile> = emptyList(),
        val settings: List<SettingProfile> = emptyList(),
        val acts: List<ActSummary> = emptyList(),
        val estimated_word_count: Int = 0
    )

    data class ActSummary(
        val act_number: Int = 1,
        val title: String = "",
        val purpose: String = "",
        val key_developments: List<String> = emptyList(),
        val estimated_scenes: Int = 3
    )


    data class ActOutline(
        val act_number: Int = 1,
        val title: String? = "",
        val purpose: String? = "",
        val scenes: List<SceneOutline>? = emptyList()
    )

    data class SceneOutline(
        val act_number: Int = 1,
        val scene_number: Int = 1,
        val title: String = "",
        val setting_id: String = "",
        val characters: List<String> = emptyList(),
        val purpose: String = "",
        val key_events: List<String> = emptyList(),
        val emotional_arc: String = "",
        val estimated_word_count: Int = 0
    )

    data class GeneratedScene(
        val scene_number: Int = 1,
        val act_number: Int = 1,
        val title: String = "",
        val content: String = "",
        val word_count: Int = 0,
        val key_moments: List<String> = emptyList(),
        val character_states: Map<String, String> = emptyMap()
    )

    override fun promptSegment(): String {
        return """
NarrativeGeneration - Generate complete narratives from analysis and outlines
  ** Extends NarrativeReasoning with full story generation
  ** Specify the subject or scenario to develop
  ** Define narrative elements: characters, setting, conflict, timeline
  ** Set target word count and structural parameters (acts, scenes)
  ** Configure writing style, POV, and tone
  ** Enable detailed descriptions, dialogue, and internal thoughts
  ** Performs analysis, creates outline, then writes each scene iteratively
  ** Each scene receives context from previous scenes
  ** Produces complete, coherent narrative with consistent style
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
        log.info("Starting NarrativeGenerationTask - Subject: '${executionConfig?.subject}', Target words: ${executionConfig?.target_word_count}")
        val transcript = task.transcript("NarrativeGeneration")?.let { OutputStreamWriter(it) }
        val genConfig = executionConfig
        transcript?.write("# Narrative Generation Task\n\n")

        if (genConfig == null) {
            log.error("Invalid configuration type for NarrativeGenerationTask")
            transcript?.write("ERROR: Invalid configuration type\n")
            transcript?.close()
            task.safeComplete("CONFIGURATION ERROR: Invalid configuration type", log)
            resultFn("CONFIGURATION ERROR: Invalid configuration type")
            return
        }

        val subject = genConfig.subject
        if (subject.isNullOrBlank()) {
            log.error("No subject specified for narrative generation")
            transcript?.write("ERROR: No subject specified\n")
            transcript?.close()
            task.safeComplete("CONFIGURATION ERROR: No subject specified", log)
            resultFn("CONFIGURATION ERROR: No subject specified")
            return
        }
        log.debug("Configuration validated - Acts: ${genConfig.number_of_acts}, Scenes/Act: ${genConfig.scenes_per_act}, Style: ${genConfig.writing_style}")

        val smartApi = defaultSmart.getChildClient(task)
        val fastApi = defaultFast.getChildClient(task)

        val tabs = TabbedDisplay(task)
        // Get input file context
        val inputFileContext = try {
            log.debug("Loading input files: ${executionConfig.input_files?.joinToString(", ") ?: "none"}")
            super.getInputFileContent(executionConfig.input_files, root, treatDocumentsAsText = true)
        } catch (e: Exception) {
            log.error("Failed to load input files", e)
            transcript?.write("WARNING: Failed to load input files: ${e.message}\n\n")
            transcript?.flush()
            ""
        }

        if (inputFileContext.isNotBlank()) {
            log.debug("Loaded input file context: ${inputFileContext.length} characters")
            transcript?.write("## Input Files Context\n\n$inputFileContext\n\n")
            transcript?.flush()
            task.expandable("Input File Context", "<pre>${inputFileContext.truncateForDisplay(5000)}</pre>")
        }
        // Combine messages with input files
        val combinedMessages = messages + listOf(inputFileContext).filter { it.isNotBlank() }
        transcript?.write("## Input Messages\n\n${combinedMessages.joinToString("\n\n")}\n\n")
        transcript?.flush()


        // Overview tab
        val overviewTask = tabs.newTask("Overview")
        val overviewContent = buildString {
            appendLine("# Narrative Generation")
            appendLine()
            appendLine("**Subject:** $subject")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Target Word Count: ${genConfig.target_word_count}")
            appendLine("- Structure: ${genConfig.number_of_acts} acts, ~${genConfig.scenes_per_act} scenes per act")
            appendLine("- Writing Style: ${genConfig.writing_style}")
            appendLine("- Point of View: ${genConfig.point_of_view}")
            appendLine("- Tone: ${genConfig.tone}")
            appendLine("- Detailed Descriptions: ${if (genConfig.detailed_descriptions) "✓" else "✗"}")
            appendLine("- Include Dialogue: ${if (genConfig.include_dialogue) "✓" else "✗"}")
            appendLine("- Internal Thoughts: ${if (genConfig.show_internal_thoughts) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Narrative Analysis")
            appendLine("*Running base narrative reasoning analysis...*")
        }
        overviewTask.add(overviewContent.renderMarkdown(true))
        transcript?.write("\n## Overview\n\n$overviewContent\n\n")
        transcript?.flush()
        overviewTask.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Generated Narrative: $subject\n\n")

        try {
            // Phase 1: Run the base narrative reasoning analysis
            log.info("Phase 1: Running narrative analysis")
            val analysisResult = StringBuilder()

            overviewTask.add("\n✅ Phase 1 Complete: Narrative analysis finished\n".renderMarkdown(true))
            overviewTask.add(
              "\n### Phase 2: Outline Generation\n*Creating detailed scene-by-scene outline...*\n".renderMarkdown(
                true
              )
            )
            overviewTask.update()

// Phase 2: Generate detailed outline
            log.info("Phase 2: Generating narrative outline")
            val outlineTask = tabs.newTask("Outline")

            outlineTask.add(
              buildString {
                appendLine("# Narrative Outline")
                appendLine()
                appendLine("**Status:** Pass 1 - Generating high-level structure...")
                appendLine()
              }.renderMarkdown(true)
            )
            outlineTask.update()

            val totalScenes = genConfig.number_of_acts * genConfig.scenes_per_act
            val wordsPerScene = genConfig.target_word_count / totalScenes

            // Generate cover image first if enabled (to use as seed for other images)
            var coverImagePath: String? = null
            if (genConfig.generate_cover_image || genConfig.generate_scene_images) {
                log.info("Phase 2.0: Generating cover image to use as visual seed")
                overviewTask.add(
                  "\n### Phase 2.0: Generating Cover Image\n*Creating visual foundation for the narrative...*\n".renderMarkdown(
                    true
                  )
                )
                overviewTask.update()
                coverImagePath = generateCoverImage(
                    task = task,
                    tabs = tabs,
                    title = subject,
                    premise = analysisResult.toString().take(500),
                    transcriptWriter = transcript,
                    orchestrationConfig = orchestrationConfig
                )
                if (coverImagePath != null) {
                    overviewTask.add(
                      "✅ Phase 2.0 Complete: Cover image generated and will be used as visual seed\n".renderMarkdown(
                        true
                      )
                    )
                } else {
                    overviewTask.add(
                      "⚠️ Phase 2.0: Cover image generation failed, proceeding without visual seed\n".renderMarkdown(
                        true
                      )
                    )
                }
                overviewTask.update()
            }

            // Pass 1: High-level outline with characters and settings
            log.info("Phase 2.1: Generating high-level outline")

            val highLevelAgent = ParsedAgent(
                resultClass = HighLevelOutline::class.java,
                prompt = """
You are a master story architect. Based on the narrative analysis, create a high-level narrative structure.

Subject: $subject

Narrative Analysis:
${analysisResult.toString().truncateForDisplay(8000)}

Narrative Elements:
${genConfig.narrative_elements?.entries?.joinToString("\n") { (key, value) -> "- $key: $value" } ?: ""}

- ${genConfig.number_of_acts} acts
- Approximately ${genConfig.scenes_per_act} scenes per act (total ~$totalScenes scenes)
- Target: ${genConfig.target_word_count} total words (~$wordsPerScene words per scene)

For each scene, specify:
- Scene number and title
- Purpose (what this scene accomplishes)
- Key events (what happens)
- Emotional arc (how characters feel/change)
- Setting (choose from available settings or describe a new one)
- Characters present (from the character list)
- Estimated word count (~$wordsPerScene words)

Ensure the outline:
- Has clear cause-and-effect between scenes
- Matches the ${genConfig.tone} tone and ${genConfig.writing_style} style


Create a high-level outline with:
1. **Characters**: Define all major characters with:
   - Name
   - Detailed description (appearance, personality, background)
   - Role in the story (protagonist, antagonist, supporting, etc.)
   - Key traits and motivations
2. **Settings**: Define all major locations/settings with:
   - Name
   - Detailed description (visual details, atmosphere)
   - Atmosphere/mood
   - Significance to the story
3. **Act Structure**: Create ${genConfig.number_of_acts} acts with:
   - Act number and title
   - Purpose (what this act accomplishes in the story)
   - Key developments (major plot points and character changes)
   - Estimated number of scenes (approximately ${genConfig.scenes_per_act} per act)
Target: ${genConfig.target_word_count} total words
Style: ${genConfig.writing_style}
Tone: ${genConfig.tone}
POV: ${genConfig.point_of_view}
Ensure the structure:
- Has well-defined, memorable characters
- Uses vivid, atmospheric settings
- Follows classic story structure (setup, rising action, climax, falling action, resolution)
- Builds tension and stakes progressively
- Matches the ${genConfig.tone} tone and ${genConfig.writing_style} style
          """.trimIndent(),
                model = smartApi,
                temperature = 0.7,
                parsingChatter = fastApi
            )

            val highLevelOutline = try {
                highLevelAgent.answer(listOf("Generate high-level outline")).obj
            } catch (e: Exception) {
                log.error("Failed to generate high-level outline", e)
                transcript?.write("ERROR: Failed to generate high-level outline: ${e.message}\n\n")
                transcript?.flush()
                throw RuntimeException("Failed to generate high-level outline", e)
            }

            log.info("Generated high-level outline: ${highLevelOutline.acts.size} acts, ${highLevelOutline.characters.size} characters, ${highLevelOutline.settings.size} settings")

            // Display high-level outline
            val highLevelContent = buildString {
                appendLine("## ${highLevelOutline.title}")
                appendLine()
                appendLine("**Premise:** ${highLevelOutline.premise}")
                appendLine()
                appendLine("**Estimated Word Count:** ${highLevelOutline.estimated_word_count}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Characters")
                appendLine()
                highLevelOutline.characters.forEach { char ->
                    appendLine("#### ${char.name}")
                    appendLine()
                    appendLine("**Role:** ${char.role}")
                    appendLine()
                    appendLine("**Description:** ${char.description}")
                    appendLine()
                    appendLine("**Traits:** ${char.traits.joinToString(", ")}")
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("### Settings")
                appendLine()
                highLevelOutline.settings.forEach { setting ->
                    appendLine("#### ${setting.setting_id}")
                    appendLine()
                    appendLine("**Description:** ${setting.description}")
                    appendLine()
                    appendLine("**Atmosphere:** ${setting.atmosphere}")
                    appendLine()
                    appendLine("**Significance:** ${setting.significance}")
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("### Act Structure")
                appendLine()
                highLevelOutline.acts.forEach { act ->
                    appendLine("#### Act ${act.act_number}: ${act.title}")
                    appendLine()
                    appendLine("**Purpose:** ${act.purpose}")
                    appendLine()
                    appendLine("**Estimated Scenes:** ${act.estimated_scenes}")
                    appendLine()
                    appendLine("**Key Developments:**")
                    act.key_developments.forEach { dev ->
                        appendLine("- $dev")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("**Status:** ✅ Pass 1 Complete")
            }
            outlineTask.add(highLevelContent.renderMarkdown(true))
            transcript?.write("\n## High-Level Outline\n\n$highLevelContent\n\n")
            transcript?.flush()
            outlineTask.update()
// Pass 2: Expand acts into detailed scenes
            log.info("Phase 2.2: Expanding acts into scenes")
            outlineTask.add("\n**Status:** Pass 2 - Expanding acts into detailed scenes...\n".renderMarkdown(true))
            outlineTask.update()

            val detailedActs = mutableListOf<ActOutline>()
            highLevelOutline.acts.forEach { actSummary ->
                log.info("Expanding Act ${actSummary.act_number}: ${actSummary.title}")

                val sceneExpansionAgent = ParsedAgent(
                    resultClass = ActOutline::class.java,
                    prompt = """
You are a master story architect. Expand this act into detailed scenes.

**High-Level Narrative Context:**
  ${highLevelOutline.toJson().indent("  ")}

**Act:**
  ${actSummary.toJson().indent("  ")}

**Previous Acts Context:**
  ${
                        detailedActs.joinToString("\n") { act -> "Act ${act.act_number}: ${act.title} - ${act.scenes?.size ?: 0} scenes" }
                            .indent("  ")
                    }

Create approximately ${actSummary.estimated_scenes} scenes for this act. For each scene specify:
- Fulfills the act's purpose and key developments
- Appropriate setting_id from defined settings
- Characters present from defined characters
          """.trimIndent(),
                    model = smartApi,
                    temperature = 0.7,
                    parsingChatter = fastApi
                )
                try {
                    val expandedAct = sceneExpansionAgent.answer(listOf("Expand act into scenes")).obj
                    // Add act number to each scene
                    val actWithNumberedScenes = expandedAct.copy(
                        scenes = expandedAct.scenes?.map { scene ->
                            scene.copy(act_number = actSummary.act_number)
                        }
                    )
                    detailedActs.add(actWithNumberedScenes)
                    log.debug("Expanded Act ${actSummary.act_number} into ${expandedAct.scenes?.size ?: 0} scenes")
                } catch (e: Exception) {
                    log.error("Failed to expand Act ${actSummary.act_number}", e)
                    transcript?.write("ERROR: Failed to expand Act ${actSummary.act_number}: ${e.message}\n\n")
                    transcript?.flush()
                    throw RuntimeException("Failed to expand Act ${actSummary.act_number}", e)
                }
            }

            // Combine into final outline
            val outline = NarrativeOutline(
                title = highLevelOutline.title,
                premise = highLevelOutline.premise,
                characters = highLevelOutline.characters,
                settings = highLevelOutline.settings,
                acts = detailedActs,
                estimated_word_count = highLevelOutline.estimated_word_count
            )
            log.info("Complete outline: ${outline.acts.size} acts, ${outline.acts.sumOf { it.scenes?.size ?: 0 }} scenes")

            // Display complete detailed outline
            val outlineContent = buildString {
                appendLine("## ${outline.title}")
                appendLine()
                appendLine("**Premise:** ${outline.premise}")
                appendLine()
                appendLine("**Estimated Word Count:** ${outline.estimated_word_count}")
                appendLine()
                appendLine("**Total Scenes:** ${outline.acts.sumOf { it.scenes?.size ?: 0 }}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Detailed Scene Breakdown")
                appendLine()
                outline.acts.forEach { act ->
                    appendLine("### Act ${act.act_number}: ${act.title}")
                    appendLine()
                    appendLine("**Purpose:** ${act.purpose}")
                    appendLine()
                    act.scenes?.forEach { scene ->
                        appendLine("#### Scene ${scene.scene_number}: ${scene.title}")
                        appendLine()
                        appendLine("- **Setting:** ${scene.setting_id}")
                        appendLine("- **Characters:** ${scene.characters.joinToString(", ")}")
                        appendLine("- **Purpose:** ${scene.purpose}")
                        appendLine("- **Emotional Arc:** ${scene.emotional_arc}")
                        appendLine("- **Est. Words:** ${scene.estimated_word_count}")
                        appendLine()
                        appendLine("**Key Events:**")
                        scene.key_events.forEach { event ->
                            appendLine("- $event")
                        }
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }
            outlineTask.add(outlineContent.renderMarkdown(true))
            transcript?.write("\n## Outline\n\n$outlineContent\n\n")
            transcript?.flush()
            outlineTask.update()
            outlineTask.complete()

            resultBuilder.append("## ${outline.title}\n\n")
            resultBuilder.append("${outline.premise}\n\n")
            resultBuilder.append("---\n\n")

            overviewTask.add(
              "✅ Phase 2 Complete: Outline created (${outline.acts.sumOf { it.scenes?.size ?: 0 }} scenes)\n".renderMarkdown(
                true
              )
            )
            overviewTask.add(
              "\n### Phase 3: Scene Generation\n*Writing scenes iteratively with context...*\n".renderMarkdown(
                true
              )
            )
            overviewTask.update()

            // Phase 2.5: Generate setting and character images if enabled
            val allScenes = outline.acts.flatMap { it.scenes ?: emptyList() } ?: emptyList()
            val settingImages = mutableMapOf<String, String>()
            val characterImages = mutableMapOf<String, String>()

            if (genConfig.generate_scene_images) {
                log.info("Phase 2.5: Generating setting and character reference images")
                overviewTask.add(
                  "\n### Phase 2.5: Generating Reference Images\n*Creating setting and character visualizations...*\n".renderMarkdown(
                    true
                  )
                )
                overviewTask.update()

                // Generate images for defined settings
                log.info("Generating images for ${outline.settings.size} settings")
                outline.settings.forEach { setting ->
                    val settingImagePath = generateSettingImage(
                        task = task,
                        tabs = tabs,
                        transcriptWriter = transcript,
                        orchestrationConfig = orchestrationConfig,
                        settingProfile = setting,
                        coverImagePath = coverImagePath
                    )
                    if (settingImagePath != null) {
                        settingImages[setting.setting_id] = settingImagePath
                    }
                }

                // Generate images for defined characters
                log.info("Generating images for ${outline.characters.size} characters")
                outline.characters.forEach { character ->
                    val characterImagePath = generateCharacterImage(
                        task = task,
                        tabs = tabs,
                        characterProfile = character,
                        transcriptWriter = transcript,
                        orchestrationConfig = orchestrationConfig,
                        coverImagePath = coverImagePath
                    )
                    if (characterImagePath != null) {
                        characterImages[character.name] = characterImagePath
                    }
                }
                overviewTask.add(
                  "✅ Phase 2.5 Complete: Generated ${settingImages.size} setting images and ${characterImages.size} character images\n".renderMarkdown(
                    true
                  )
                )
                overviewTask.update()
            }


            // Phase 3: Generate each scene iteratively
            log.info("Phase 3: Generating scenes")
            val generatedScenes = mutableListOf<GeneratedScene>()
            var cumulativeWordCount = 0

            allScenes.forEachIndexed { index, sceneOutline ->
                log.info("Generating Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}/${allScenes.size}: ${sceneOutline?.title}")

                overviewTask.add(
                  "- Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline?.title} ".renderMarkdown(
                    true
                  )
                )
                overviewTask.update()

                val sceneTask = tabs.newTask("Act ${sceneOutline.act_number} Scene ${sceneOutline.scene_number}")

                sceneTask.add(
                  buildString {
                    appendLine("# Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title}")
                    appendLine()
                    appendLine("**Status:** Writing scene...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                sceneTask.update()

                // Build context from previous scenes
                val recentContext = if (generatedScenes.size > 0) {
                    val lastScenes = generatedScenes.takeLast(2)
                    buildString {
                        appendLine("## Previous Scene Context")
                        lastScenes.forEach { prevScene ->
                            appendLine("### Scene ${prevScene.scene_number}: ${prevScene.title}")
                            appendLine("**Key Moments:**")
                            prevScene.key_moments.forEach { moment ->
                                appendLine("- $moment")
                            }
                            appendLine()
                            appendLine("**Character States:**")
                            prevScene.character_states.forEach { (char, state) ->
                                appendLine("- $char: $state")
                            }
                            appendLine()
                            if (prevScene == lastScenes.last()) {
                                appendLine("**Last Scene Ending:**")
                                appendLine(prevScene.content.takeLast(500))
                                appendLine()
                            }
                        }
                    }
                } else {
                    "This is the opening scene."
                }

                val sceneAgent = ParsedAgent(
                    resultClass = GeneratedScene::class.java,
                    prompt = """
 You are a skilled ${genConfig.writing_style} writer. Write Scene ${sceneOutline.scene_number} of the narrative.
This is Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}.

 Overall Story: ${outline.title}
Premise: ${outline.premise}

Scene Outline:
  ${sceneOutline.toJson().indent("  ")}

$recentContext

Writing Guidelines:
- Point of View: ${genConfig.point_of_view}
- Tone: ${genConfig.tone}
- Style: ${genConfig.writing_style}
${if (genConfig.detailed_descriptions) "- Include vivid, sensory descriptions of setting and action" else "- Keep descriptions concise"}
${if (genConfig.include_dialogue) "- Include natural, character-appropriate dialogue" else "- Minimize dialogue, focus on narration"}
${if (genConfig.show_internal_thoughts) "- Show character internal thoughts and feelings" else "- Show emotions through action and dialogue only"}

Write the complete scene with:
- A strong opening that connects to the previous scene (if any)
- Clear progression through the key events
- Character development and emotional depth
- Sensory details and atmosphere
- A compelling ending that sets up the next scene
- Approximately ${sceneOutline.estimated_word_count} words

After writing, provide:
- The scene content
- Actual word count
- Key moments (3-5 bullet points of what happened)
- Character states (how each character ends the scene emotionally/physically)

Make the writing engaging, immersive, and true to the characters and story.
          """.trimIndent(),
                    model = smartApi,
                    temperature = 0.8,
                    parsingChatter = fastApi
                )

                var generatedScene = sceneAgent.answer(listOf("Write the scene")).obj
                // Ensure act number is preserved
                generatedScene = generatedScene.copy(act_number = sceneOutline.act_number)

                // Optional revision pass
                if (genConfig.revision_passes > 0) {
                    repeat(genConfig.revision_passes) { revisionNum ->
                        log.debug("Revision pass ${revisionNum + 1} for Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}")

                        val revisionAgent = ChatAgent(
                            prompt = """
 You are an expert editor. Review and improve this scene while maintaining its core events and purpose.

Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title}

Current Version:
${generatedScene.content}

Improve:
- Prose quality and flow
- Character voice consistency
- Sensory details and atmosphere
- Pacing and tension
- Dialogue naturalness
- Emotional impact

Maintain:
- All key events and plot points
- Character states and development
- Word count (currently ${generatedScene.word_count}, target ${sceneOutline.estimated_word_count})
- Tone and style

Provide the revised scene content only.
              """.trimIndent(),
                            model = smartApi,
                            temperature = 0.7
                        )

                        val revisedContent = revisionAgent.answer(listOf("Revise the scene"))
                        generatedScene = generatedScene.copy(
                            content = revisedContent,
                            word_count = revisedContent.split("\\s+".toRegex()).size
                        )
                    }
                }

                generatedScenes.add(generatedScene)
                cumulativeWordCount += generatedScene.word_count

                val sceneContent = buildString {
                    appendLine("## ${sceneOutline.title}")
                    appendLine()
                    appendLine("**Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}**")
                    appendLine()
                    appendLine("**Setting:** ${sceneOutline.setting_id}")
                    appendLine()
                    appendLine("**Characters:** ${sceneOutline.characters.joinToString(", ")}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine(generatedScene.content)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Word Count:** ${generatedScene.word_count}")
                    appendLine()
                    appendLine("**Key Moments:**")
                    generatedScene.key_moments.forEach { moment ->
                        appendLine("- $moment")
                    }
                    appendLine()
                    appendLine("**Character States:**")
                    generatedScene.character_states.forEach { (char, state) ->
                        appendLine("- **$char:** $state")
                    }
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                sceneTask.add(sceneContent.renderMarkdown(true))
                transcript?.write("\n## $sceneContent\n\n")
                transcript?.flush()
                sceneTask.update()
                sceneTask.complete()

                // Add to result
                resultBuilder.append("## Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title}\n\n")
                resultBuilder.append(generatedScene.content)
                resultBuilder.append("\n\n---\n\n")

                overviewTask.add("✅ (${generatedScene.word_count} words)\n".renderMarkdown(true))
                overviewTask.update()
// Generate scene image if enabled
                if (genConfig.generate_scene_images) {
                    generateSceneImage(
                        task = task,
                        tabs = tabs,
                        actNumber = sceneOutline.act_number,
                        sceneNumber = sceneOutline.scene_number,
                        sceneTitle = sceneOutline.title,
                        sceneContent = generatedScene.content,
                        setting = sceneOutline.setting_id,
                        settingImagePath = settingImages.entries.find {
                            sceneOutline.setting_id.lowercase().contains(it.key.lowercase())
                        }?.value,
                        characterImagePaths = sceneOutline.characters.mapNotNull { char ->
                            char to (characterImages[char] ?: return@mapNotNull null)
                        }.toMap(),
                        transcriptWriter = transcript,
                        orchestrationConfig = orchestrationConfig
                    )
                }
            }

            overviewTask.add("\n✅ Phase 3 Complete: All scenes generated\n".renderMarkdown(true))
            overviewTask.add("\n### Phase 4: Final Assembly\n*Compiling complete narrative...*\n".renderMarkdown(true))
            overviewTask.update()

            // Phase 4: Create final compiled version
            log.info("Phase 4: Assembling final narrative")
            val finalTask = tabs.newTask("Complete Narrative")

            val finalNarrative = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine("*${outline.premise}*")
                appendLine()
                appendLine("---")
                appendLine()

                outline.acts.forEach { act ->
                    appendLine("# Act ${act.act_number}: ${act.title}")
                    appendLine()

                    val actScenes = generatedScenes.filter { scene ->
                        act.scenes?.any { it.scene_number == scene.scene_number } == true
                    }

                    actScenes.forEach { scene ->
                        appendLine("## ${scene.title}")
                        appendLine()
                        appendLine(scene.content)
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                }

                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**THE END**")
                appendLine()
                appendLine("*Total Word Count: $cumulativeWordCount*")
            }

            finalTask.add(finalNarrative.renderMarkdown(true))
            finalTask.update()
            finalTask.complete()

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime
            val avgWordsPerScene = if (generatedScenes.isNotEmpty()) cumulativeWordCount / generatedScenes.size else 0

            overviewTask.add(
              buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Generation Complete")
                appendLine()
                appendLine("**Statistics:**")
                appendLine("- Total Scenes: ${generatedScenes.size}")
                appendLine("- Total Word Count: $cumulativeWordCount")
                appendLine("- Average Words/Scene: $avgWordsPerScene")
                appendLine("- Target Word Count: ${genConfig.target_word_count}")
                appendLine("- Completion: ${(cumulativeWordCount.toFloat() / genConfig.target_word_count * 100).toInt()}%")
                appendLine("- Total Time: ${totalTime / 1000.0}s")
                appendLine()
                appendLine(
                  "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                  }"
                )
              }.renderMarkdown(true)
            )
            overviewTask.update()
            overviewTask.complete()
            transcript?.write("\n## Final Statistics\n\n- Total Scenes: ${generatedScenes.size}\n- Total Word Count: $cumulativeWordCount\n- Time: ${totalTime / 1000.0}s\n\n")
            transcript?.close()

            val finalResult = buildString {
                appendLine("# Narrative Generation Summary: ${outline.title}")
                appendLine()
                appendLine("A complete narrative of **$cumulativeWordCount words** across **${generatedScenes.size} scenes** was generated in **${totalTime / 1000.0}s**.")
                appendLine("> The full narrative and detailed transcript are available in the UI tabs for review.")
                appendLine()
                appendLine(outlineContent.substringBeforeLast("\n**Status:**").trim())
            }
            log.info("NarrativeGenerationTask completed: scenes=${generatedScenes.size}, words=$cumulativeWordCount, time=${totalTime}ms")
            val narrativeData = mapOf(
                "config" to genConfig,
                "highLevelOutline" to highLevelOutline,
                "outline" to outline,
                "scenes" to generatedScenes,
                "assets" to mapOf(
                    "coverImage" to coverImagePath,
                    "settingImages" to settingImages,
                    "characterImages" to characterImages
                ),
                "statistics" to mapOf(
                    "wordCount" to cumulativeWordCount,
                    "sceneCount" to generatedScenes.size,
                    "durationMs" to totalTime
                )
            )
            task.resolveUserFile("narrative_data.json")?.let { jsonFile ->
                jsonFile.writeText(narrativeData.toJson())
                log.info("Saved narrative data to ${jsonFile.absolutePath}")
                overviewTask.add("\n**Data:** Saved full narrative data to `narrative_data.json`\n".renderMarkdown(true))
                overviewTask.update()
            }

            task.safeComplete(
                "Narrative generation complete: ${generatedScenes.size} scenes, $cumulativeWordCount words in ${totalTime / 1000}s",
                log
            )
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during narrative generation", e)
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
            overviewTask.update()

            val errorOutput = buildString {
                appendLine("# Error in Narrative Generation")
                appendLine()
                appendLine("**Subject:** $subject")
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

    private fun generateCoverImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        title: String,
        premise: String,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig
    ): String? {
        try {
            log.info("Generating cover image for: $title")
            val task = tabs.newTask("Cover Image")
            task.add(
              buildString {
                appendLine("# Cover Image")
                appendLine()
                appendLine("**Status:** Generating cover image...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()
            val imageAgent = ImageProcessingAgent(
                prompt = "Create a compelling book cover image that captures the essence of this narrative",
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.8,
            )
            val coverPrompt = "$title: $premise"
            val result = imageAgent.answer(listOf(ImageAndText(coverPrompt)))
            val image = result.image
            // Save image
            val relativePath = "00_cover_image.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved cover image to: ${imageFile.absolutePath}")
            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='cover-image'>
          <h3>$title</h3>
          <p><em>$premise</em></p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Cover' style='max-width: 600px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.2);' />
          </a>
        </div>
      """.trimIndent()
            task.add(imageHtml.renderMarkdown(true))
            task.update()
            // Write to transcript
            transcriptWriter?.appendLine("## Cover Image")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Cover Image]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            task.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
            task.update()
            task.complete()
            return relativePath
        } catch (e: Exception) {
            log.error("Failed to generate cover image", e)
            transcriptWriter?.appendLine("**Cover Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
            return null
        }
    }

    private fun generateSettingImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        settingProfile: SettingProfile,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig,
        coverImagePath: String?
    ): String? {
        return try {
            log.info("Generating reference image for setting: ${settingProfile.setting_id}")
            val task = tabs.newTask("Setting: ${settingProfile.setting_id}")
            task.add(
              buildString {
                appendLine("# Setting Reference: ${settingProfile.setting_id}")
                appendLine()
                appendLine("**Status:** Generating setting visualization...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create a detailed, atmospheric image of this setting that captures its essence and mood. Use the cover image as visual inspiration for style and atmosphere.",
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.7,
            )

            val settingPrompt = buildString {
                appendLine("${settingProfile.setting_id}")
                appendLine("Description: ${settingProfile.description}")
                appendLine("Atmosphere: ${settingProfile.atmosphere}")
            }.toString()

            // Build input with cover image as reference if available
            val imageInputs = mutableListOf<ImageAndText>()

            if (coverImagePath != null) {
                try {
                    val coverImage = ImageIO.read(task.resolveUserFile(coverImagePath))
                    if (coverImage != null) {
                        imageInputs.add(
                            ImageAndText(
                                text = "Cover image - use as visual style reference",
                                image = coverImage
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load cover image for setting reference: $coverImagePath", e)
                }
            }

            imageInputs.add(ImageAndText(settingPrompt))

            val result = imageAgent.answer(imageInputs)
            val image = result.image
            // Save image with sanitized filename
            val sanitizedName = settingProfile.setting_id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
            val relativePath = "setting_${sanitizedName}_ref.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved setting reference image to: ${imageFile.absolutePath}")

            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='setting-reference'>
          <h4>${settingProfile.setting_id}</h4>
          <p><strong>Description:</strong> ${settingProfile.description}</p>
          <p><strong>Atmosphere:</strong> ${settingProfile.atmosphere}</p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='${settingProfile.setting_id}' style='max-width: 400px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            task.add(imageHtml.renderMarkdown(true))
            task.update()
            // Write to transcript
            transcriptWriter?.appendLine("#### Setting: ${settingProfile.setting_id}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Setting: ${settingProfile.setting_id}]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            task.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
            task.update()
            task.complete()
            relativePath
        } catch (e: Exception) {
            log.error("Failed to generate setting reference image for: ${settingProfile.setting_id}", e)
            transcriptWriter?.appendLine("**Setting Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
            null
        }
    }

    private fun generateCharacterImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        characterProfile: CharacterProfile,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig,
        coverImagePath: String?
    ): String? {
        return try {
            log.info("Generating reference image for character: ${characterProfile.name}")
            val task = tabs.newTask("Character: ${characterProfile.name}")
            task.add(
              buildString {
                appendLine("# Character Reference: ${characterProfile.name}")
                appendLine()
                appendLine("**Status:** Generating character visualization...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create a detailed character portrait that captures their appearance, personality, and essence. Use the cover image as visual inspiration for style and atmosphere.",
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.7,
            )

            val characterPrompt = buildString {
                appendLine("${characterProfile.name}")
                appendLine("Role: ${characterProfile.role}")
                appendLine("Description: ${characterProfile.description}")
                appendLine("Traits: ${characterProfile.traits.joinToString(", ")}")
            }.toString()

            // Build input with cover image as reference if available
            val imageInputs = mutableListOf<ImageAndText>()

            if (coverImagePath != null) {
                try {
                    val coverImage = ImageIO.read(task.resolveUserFile(coverImagePath))
                    if (coverImage != null) {
                        imageInputs.add(
                            ImageAndText(
                                text = "Cover image - use as visual style reference",
                                image = coverImage
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load cover image for character reference: $coverImagePath", e)
                }
            }

            imageInputs.add(ImageAndText(characterPrompt))

            val result = imageAgent.answer(imageInputs)
            val image = result.image
            // Save image with sanitized filename
            val sanitizedName = characterProfile.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
            val relativePath = "character_${sanitizedName}_ref.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved character reference image to: ${imageFile.absolutePath}")

            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='character-reference'>
          <h4>${characterProfile.name}</h4>
          <p><strong>Role:</strong> ${characterProfile.role}</p>
          <p><strong>Description:</strong> ${characterProfile.description}</p>
          <p><strong>Traits:</strong> ${characterProfile.traits.joinToString(", ")}</p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='${characterProfile.name}' style='max-width: 400px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            task.add(imageHtml.renderMarkdown(true))
            task.update()
            // Write to transcript
            transcriptWriter?.appendLine("#### Character: ${characterProfile.name}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Character: ${characterProfile.name}]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            task.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
            task.update()
            task.complete()
            relativePath
        } catch (e: Exception) {
            log.error("Failed to generate character reference image for: ${characterProfile.name}", e)
            transcriptWriter?.appendLine("**Character Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
            null
        }
    }

    private fun generateSceneImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        actNumber: Int,
        sceneNumber: Int,
        sceneTitle: String,
        sceneContent: String,
        setting: String,
        settingImagePath: String?,
        characterImagePaths: Map<String, String>,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig
    ) {
        try {
            log.info("Generating image for Act $actNumber, Scene $sceneNumber: $sceneTitle")
            val sceneImageTask = tabs.newTask("Act $actNumber Scene $sceneNumber Image")
            sceneImageTask.add(
              buildString {
                appendLine("# Act $actNumber, Scene $sceneNumber Image")
                appendLine()
                appendLine("**Status:** Generating scene visualization...")
                appendLine()
              }.renderMarkdown(true)
            )
            sceneImageTask.update()
            val imageAgent = ImageProcessingAgent(
                prompt = "Create a cinematic scene illustration that captures the key moment and atmosphere",
                model = orchestrationConfig.defaultImage,
                temperature = 0.7,
            )
            // Extract key visual elements from scene
            val scenePrompt = buildString {
                append("Scene: $sceneTitle. Act $actNumber, Scene $sceneNumber. ")
                append("Setting: $setting. ")
                // Take first 500 chars of scene content for context
                append(sceneContent)
            }
            // Build input with reference images
            val imageInputs = mutableListOf<ImageAndText>()

            // Add setting reference image if available
            if (settingImagePath != null) {
                try {
                    val settingImage = ImageIO.read(task.resolveUserFile(settingImagePath))
                    if (settingImage != null) {
                        imageInputs.add(
                            ImageAndText(
                                text = "Reference setting image for: $setting",
                                image = settingImage
                            )
                        )
                    } else {
                        log.debug("Setting reference image is null for path: $settingImagePath")
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load setting reference image: $settingImagePath", e)
                }
            } else {
                log.debug("No setting reference image available for setting: $setting")
            }

            // Add character reference images if available
            characterImagePaths.forEach { (character, imagePath) ->
                try {
                    val charImage = ImageIO.read(task.resolveUserFile(imagePath))
                    if (charImage != null) {
                        imageInputs.add(
                            ImageAndText(
                                text = "Reference character image for: $character",
                                image = charImage
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load character reference image: $imagePath", e)
                }
            }

            // Add the scene prompt
            imageInputs.add(ImageAndText(scenePrompt))

            val result = imageAgent.answer(imageInputs)
            val image = result.image
            // Save image
            val relativePath = "act_${actNumber}_scene_${sceneNumber}_image.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved scene image to: ${imageFile.absolutePath}")
            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='scene-image'>
          <h4>Act $actNumber, Scene $sceneNumber: $sceneTitle</h4>
          <p><strong>Setting:</strong> $setting</p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Act $actNumber Scene $sceneNumber' style='max-width: 600px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            sceneImageTask.add(imageHtml.renderMarkdown(true))
            sceneImageTask.update()
            // Write to transcript
            transcriptWriter?.appendLine("#### Act $actNumber, Scene $sceneNumber Image")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Act $actNumber Scene $sceneNumber]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            sceneImageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
            sceneImageTask.update()
            sceneImageTask.complete()
        } catch (e: Exception) {
            log.error("Failed to generate scene image for Act $actNumber, Scene $sceneNumber", e)
            transcriptWriter?.appendLine("**Scene Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
        }
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(NarrativeGenerationTask::class.java)
        @JvmStatic val NarrativeGeneration = TaskType(
          name = "NarrativeGeneration",
          category = "Writing",
          taskClass = NarrativeGenerationTask::class.java,
          executionConfigClass = NarrativeGenerationTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate complete narratives from analysis and outlines",
          tooltipHtml = """
                        Extends NarrativeReasoning to generate complete, publication-ready narratives.
                        <ul>
                          <li>Performs comprehensive narrative analysis (inherited from NarrativeReasoning)</li>
                          <li>Creates detailed scene-by-scene outline based on analysis</li>
                          <li>Generates each scene iteratively with full context</li>
                          <li>Maintains consistency by feeding previous scenes into each generation</li>
                          <li>Supports configurable structure (acts, scenes, word count)</li>
                          <li>Customizable writing style, POV, tone, and narrative elements</li>
                          <li>Optional revision passes for quality improvement</li>
                          <li>Produces complete, coherent narrative with consistent style and voice</li>
                          <li>Ideal for story generation, scenario planning, user journey narratives</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/ReportGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
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

        @Description("Related files or data sources to incorporate")
        var related_files: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,


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

        val transcript = task.transcript()
        task.ui.pool.submit {
            try {
        // Read input from messages parameter
        val messageContext = messages.filter { it.isNotBlank() }.joinToString("\n\n")
        // Load input files if specified
        val inputFileContent = getInputFileCode()
                val fullContext = listOfNotNull(messageContext, inputFileContent).filter { it.isNotBlank() }
                    .joinToString("\n\n---\n\n")

        // Validate configuration
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
        @JvmStatic val ReportGeneration = TaskType(
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/ResearchPaperGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/ScriptwritingTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
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
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Semaphore
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ScriptwritingTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ScriptwritingTaskExecutionConfigData?
) : AbstractTask<ScriptwritingTask.ScriptwritingTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    protected val codeFiles = mutableMapOf<Path, String>()

    class ScriptwritingTaskExecutionConfigData(
        @Description("The topic or subject of the script")
        val topic: String? = null,

        @Description("The type of script to generate")
        val script_type: String = "video",

        @Description("Target duration in minutes")
        val target_duration_minutes: Int = 5,

        @Description("The intended audience for the script")
        val target_audience: String = "general public",

        @Description("The tone of the script")
        val tone: String = "professional",

        @Description("Whether to include visual/scene directions")
        val include_directions: Boolean = true,

        @Description("Whether to include timing markers")
        val include_timing: Boolean = true,

        @Description("Whether to suggest B-roll or supporting visuals")
        val suggest_b_roll: Boolean = true,

        @Description("Whether to include speaker notes or production notes")
        val include_notes: Boolean = true,

        @Description("Whether to mark key points for emphasis or graphics")
        val mark_key_points: Boolean = true,

        @Description("The pacing style")
        val pacing: String = "moderate",

        @Description("Whether to include an opening hook")
        val include_hook: Boolean = true,

        @Description("Whether to include a call-to-action")
        val include_cta: Boolean = true,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,


        @Description("Number of revision passes")
        val revision_passes: Int = 1,

        @Description("Related files or research to incorporate")
        val related_files: List<String>? = null,

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = Scriptwriting.name,
        task_description = task_description ?: "Generate script for: '$topic'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (topic.isNullOrBlank()) {
                return "topic must not be null or blank"
            }
            if (target_duration_minutes <= 0 || target_duration_minutes > 180) {
                return "target_duration_minutes must be between 1 and 180, got: $target_duration_minutes"
            }
            if (script_type.isBlank()) {
                return "script_type must not be blank"
            }
            if (tone.isBlank()) {
                return "tone must not be blank"
            }
            val validPacing = setOf("slow", "moderate", "fast", "dynamic")
            if (pacing.lowercase() !in validPacing) {
                return "pacing must be one of: ${validPacing.joinToString(", ")}, got: $pacing"
            }
            if (revision_passes < 0 || revision_passes > 5) {
                return "revision_passes must be between 0 and 5, got: $revision_passes"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class ScriptOutline(
        @Description("The script title")
        val title: String = "",
        @Description("Opening hook or attention grabber")
        val hook: String = "",
        @Description("Main sections of the script")
        val sections: List<ScriptSection> = emptyList(),
        @Description("Closing and call-to-action")
        val closing: String = "",
        @Description("Estimated total duration in seconds")
        val estimated_duration_seconds: Int = 0,
        @Description("Key messages to convey")
        val key_messages: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (sections.isEmpty()) return "sections must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ScriptSection(
        @Description("Section number")
        val section_number: Int = 1,
        @Description("Section title or purpose")
        val title: String = "",
        @Description("Key points to cover in this section")
        val key_points: List<String> = emptyList(),
        @Description("Visual elements or B-roll suggestions")
        val visual_suggestions: List<String> = emptyList(),
        @Description("Estimated duration in seconds")
        val estimated_duration_seconds: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (section_number < 1) return "section_number must be positive"
            if (title.isBlank()) return "title must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ScriptSegment(
        @Description("Segment type")
        val segment_type: String = "",
        @Description("The spoken dialogue or narration")
        val dialogue: String = "",
        @Description("Visual directions or scene description")
        val visual_direction: String = "",
        @Description("B-roll or supporting visual suggestions")
        val b_roll_suggestions: List<String> = emptyList(),
        @Description("Production notes or speaker notes")
        val notes: String = "",
        @Description("Timing marker in MM:SS format")
        val timing: String = "",
        @Description("Key points marked for emphasis or graphics")
        val key_points_marked: List<String> = emptyList(),
        @Description("Estimated duration in seconds")
        val duration_seconds: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (dialogue.isBlank()) return "dialogue must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
 Scriptwriting - Generate complete scripts for videos, podcasts, and presentations
  ** Optionally, list input files (supports glob patterns) to be examined when generating the script
  ** Specify the topic and script type (video, podcast, presentation, etc.)
  ** Set target duration and audience
  ** Configure tone and pacing
  ** Specify the topic and script type (video, podcast, presentation, etc.)
  ** Set target duration and audience
  ** Configure tone and pacing
  ** Include visual directions, timing markers, and B-roll suggestions
  ** Mark key points for emphasis or graphics
  ** Add speaker notes and production notes
  ** Performs outline creation, segment writing, and timing calculation
  ** Produces complete, production-ready script with all necessary elements
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
        log.info("Starting ScriptwritingTask for topic: '${executionConfig?.topic}'")
        val markdownTranscript = task.transcript()


        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            markdownTranscript?.close()
            resultFn("CONFIGURATION ERROR: $validationError")
            markdownTranscript?.close()
            return
        }

        val topic = executionConfig?.topic
        if (topic.isNullOrBlank()) {
            log.error("No topic specified for scriptwriting")
            task.safeComplete("CONFIGURATION ERROR: No topic specified", log)
            markdownTranscript?.close()
            resultFn("CONFIGURATION ERROR: No topic specified")
            markdownTranscript?.close()
            return
        }

        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)
        val semaphore = Semaphore(0)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Script Generation")
            appendLine()
            appendLine("**Topic:** $topic")
            appendLine()
        }
        markdownTranscript?.write(overviewContent.toByteArray())
        markdownTranscript?.write("\n".toByteArray())
        overviewTask.add(overviewContent.renderMarkdown(true))
        task.update()
        val overviewContent2 = buildString {
            appendLine()
            appendLine("## Configuration")
            appendLine("- Script Type: ${executionConfig.script_type}")
            appendLine("- Target Duration: ${executionConfig.target_duration_minutes} minutes")
            appendLine("- Target Audience: ${executionConfig.target_audience}")
            appendLine("- Tone: ${executionConfig.tone}")
            appendLine("- Pacing: ${executionConfig.pacing}")
            appendLine("- Include Directions: ${if (executionConfig.include_directions) "✓" else "✗"}")
            appendLine("- Include Timing: ${if (executionConfig.include_timing) "✓" else "✗"}")
            appendLine("- Suggest B-Roll: ${if (executionConfig.suggest_b_roll) "✓" else "✗"}")
            appendLine("- Include Notes: ${if (executionConfig.include_notes) "✓" else "✗"}")
            appendLine("- Mark Key Points: ${if (executionConfig.mark_key_points) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Research & Outline")
            appendLine("*Analyzing topic and creating script structure...*")
        }
        markdownTranscript?.write(overviewContent2.toByteArray())
        overviewTask.add(overviewContent2.renderMarkdown(true))
        task.update()

        val resultBuilder = StringBuilder()
        markdownTranscript?.write("# Research Context\n<details>\n<summary>Context Details</summary>\n\n".toByteArray())
        markdownTranscript?.write("Context loaded from prior tasks and related files.\n\n".toByteArray())
        resultBuilder.append("# Script: $topic\n\n")

        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState)
            val contextFiles = getContextFiles()

            if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                val contextTask = tabs.newTask("Research Context")
                contextTask.add(
                  buildString {
                    appendLine("# Research Context")
                    appendLine()
                    if (priorContext.isNotBlank()) {
                      appendLine("## Prior Context")
                      appendLine(priorContext.truncateForDisplay(2000))
                      appendLine()
                    }
                    if (contextFiles.isNotBlank()) {
                      appendLine("## Related Files")
                      appendLine(contextFiles.truncateForDisplay(2000))
                    }
                  }.renderMarkdown(true)
                )
                task.update()
            }
            markdownTranscript?.write("\n</details>\n".toByteArray())

            // Phase 1: Create outline
            log.info("Phase 1: Creating script outline")
            val outlineTask = tabs.newTask("Outline")

            outlineTask.add(
              buildString {
                appendLine("# Script Outline")
                appendLine()
                appendLine("**Status:** Creating structured outline...")
                appendLine()
              }.renderMarkdown(true)
            )
            markdownTranscript?.write("# Script Outline\n<details>\n<summary>Outline Details</summary>\n\n".toByteArray())
            task.update()

            val targetDurationSeconds = executionConfig.target_duration_minutes * 60
            val wordsPerMinute = when (executionConfig.pacing.lowercase()) {
                "slow" -> 120
                "moderate" -> 150
                "fast" -> 180
                "dynamic" -> 160
                else -> 150
            }
            val targetWordCount = executionConfig.target_duration_minutes * wordsPerMinute

            val outlineAgent = ParsedAgent(
                resultClass = ScriptOutline::class.java,
                prompt = """
You are an expert scriptwriter specializing in ${executionConfig.script_type} scripts. Create a detailed outline for this script.

Topic: $topic

Script Type: ${executionConfig.script_type}
Target Duration: ${executionConfig.target_duration_minutes} minutes (~$targetDurationSeconds seconds)
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}
Pacing: ${executionConfig.pacing} (~$wordsPerMinute words per minute)
Target Word Count: ~$targetWordCount words

${if (priorContext.isNotBlank()) "Research Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Additional Research:\n${contextFiles.truncateForDisplay(3000)}\n" else ""}

Create an outline with:
1. A compelling title
${if (executionConfig.include_hook) "2. An attention-grabbing opening hook (10-15 seconds)" else ""}
3. 3-5 main sections that logically progress through the topic
4. Key points to cover in each section
${if (executionConfig.suggest_b_roll) "5. Visual suggestions for each section" else ""}
6. Estimated duration for each section
${if (executionConfig.include_cta) "7. A strong closing with call-to-action" else "7. A memorable closing"}

Ensure the outline:
- Flows logically from introduction to conclusion
- Maintains the ${executionConfig.tone} tone throughout
- Fits within the ${executionConfig.target_duration_minutes}-minute timeframe
- Engages the ${executionConfig.target_audience}
- Balances information delivery with entertainment/engagement
          """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = defaultFast
            )

            var outline = outlineAgent.answer(listOf("Generate outline")).obj

            // Validate outline
            outline.validate()?.let { validationError ->
                log.error("Outline validation failed: $validationError")
                outlineTask.error(ValidatedObject.ValidationError(validationError, outline))
                task.safeComplete("Outline validation failed: $validationError", log)
                resultFn("ERROR: Outline validation failed: $validationError")
                return
            }

            log.info("Generated outline: ${outline.sections.size} sections, ${outline.estimated_duration_seconds}s estimated")

            val outlineContent = buildString {
                appendLine("## ${outline.title}")
                appendLine()
                if (executionConfig.include_hook && outline.hook.isNotBlank()) {
                    appendLine("### Opening Hook")
                    appendLine(outline.hook)
                    appendLine()
                }
                appendLine("### Key Messages")
                outline.key_messages.forEach { message ->
                    appendLine("- $message")
                }
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Script Sections")
                outline.sections.forEach { section ->
                    appendLine("#### Section ${section.section_number}: ${section.title}")
                    appendLine()
                    appendLine("**Duration:** ~${section.estimated_duration_seconds}s")
                    appendLine()
                    appendLine("**Key Points:**")
                    section.key_points.forEach { point ->
                        appendLine("- $point")
                    }
                    appendLine()
                    if (section.visual_suggestions.isNotEmpty()) {
                        appendLine("**Visual Suggestions:**")
                        section.visual_suggestions.forEach { visual ->
                            appendLine("- $visual")
                        }
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }
                appendLine("### Closing")
                appendLine(outline.closing)
                appendLine()
                appendLine("**Total Estimated Duration:** ${outline.estimated_duration_seconds}s (${outline.estimated_duration_seconds / 60}m ${outline.estimated_duration_seconds % 60}s)")
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            outlineTask.add(outlineContent.renderMarkdown(true))
            markdownTranscript?.write(outlineContent.toByteArray())
            markdownTranscript?.write("\n</details>\n".toByteArray())
            task.update()

            overviewTask.add(
              "✅ Phase 1 Complete: Outline created (${outline.sections.size} sections)\n".renderMarkdown(
                true
              )
            )
            overviewTask.add(
              "\n### Phase 2: Script Writing\n*Writing detailed script segments...*\n".renderMarkdown(
                true
              )
            )
            task.update()

            // Phase 2: Write script segments
            log.info("Phase 2: Writing script segments")
            val scriptSegments = mutableListOf<ScriptSegment>()
            var cumulativeDuration = 0
            var cumulativeWordCount = 0

            // Write opening if hook is included
            if (executionConfig.include_hook && outline.hook.isNotBlank()) {
                log.info("Writing opening hook")
                overviewTask.add("- Opening Hook ".renderMarkdown(true))
                task.update()

                val hookTask = tabs.newTask("Opening")

                hookTask.add(
                  buildString {
                    appendLine("# Opening Hook")
                    appendLine()
                    appendLine("**Status:** Writing opening...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                task.update()

                val hookAgent = ParsedAgent(
                    resultClass = ScriptSegment::class.java,
                    prompt = """
You are an expert scriptwriter. Write the opening hook for this ${executionConfig.script_type} script.

Topic: $topic
Hook Concept: ${outline.hook}
Tone: ${executionConfig.tone}
Target Audience: ${executionConfig.target_audience}
Target Duration: 10-15 seconds

Write an opening that:
1. Immediately grabs attention
2. Sets the tone for the entire script
3. Hints at what's coming
4. Is conversational and natural for spoken delivery
${if (executionConfig.include_directions) "5. Includes visual direction for what the viewer sees" else ""}
${if (executionConfig.suggest_b_roll) "6. Suggests B-roll or supporting visuals" else ""}

Make it punchy, engaging, and memorable.
Ensure the dialogue sounds natural when spoken aloud.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = defaultFast
                )

                var hookSegment = hookAgent.answer(listOf("Write opening")).obj
                scriptSegments.add(hookSegment)
                cumulativeDuration += hookSegment.duration_seconds
                cumulativeWordCount += hookSegment.dialogue.split("\\s+".toRegex()).size

                hookTask.add(
                  buildString {
                    appendLine("## Opening Hook")
                    appendLine()
                    if (executionConfig.include_timing) {
                      appendLine("**[${formatTiming(0)}]**")
                      appendLine()
                    }
                    if (executionConfig.include_directions && hookSegment.visual_direction.isNotBlank()) {
                      appendLine("*${hookSegment.visual_direction}*")
                      appendLine()
                    }
                    appendLine(hookSegment.dialogue)
                    appendLine()
                    if (executionConfig.suggest_b_roll && hookSegment.b_roll_suggestions.isNotEmpty()) {
                      appendLine("**B-Roll:**")
                      hookSegment.b_roll_suggestions.forEach { broll ->
                        appendLine("- $broll")
                      }
                      appendLine()
                    }
                    if (executionConfig.include_notes && hookSegment.notes.isNotBlank()) {
                      appendLine("**Notes:** ${hookSegment.notes}")
                      appendLine()
                    }
                    appendLine("---")
                    appendLine()
                    appendLine(
                      "**Duration:** ${hookSegment.duration_seconds}s | **Words:** ${
                        hookSegment.dialogue.split(
                          "\\s+".toRegex()
                        ).size
                      }"
                    )
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                  }.renderMarkdown(true)
                )
                markdownTranscript?.write("# Opening Hook\n\n".toByteArray())
                markdownTranscript?.write(hookSegment.dialogue.toByteArray())
                markdownTranscript?.write("\n\n".toByteArray())
                task.update()

                overviewTask.add("✅\n".renderMarkdown(true))
                task.update()
            }

            // Write each section
            outline.sections.forEachIndexed { index, sectionOutline ->
                log.info("Writing section ${index + 1}/${outline.sections.size}: ${sectionOutline.title}")

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

                // Build context from previous segments
                val previousContext = if (scriptSegments.isNotEmpty()) {
                    buildString {
                        appendLine("## Previous Script Context")
                        val lastSegments = scriptSegments.takeLast(2)
                        lastSegments.forEach { prevSegment ->
                            appendLine("**Previous Dialogue:**")
                            appendLine(prevSegment.dialogue.takeLast(200))
                            appendLine()
                        }
                        appendLine("**Current Duration:** ${cumulativeDuration}s")
                    }
                } else {
                    "This is the first main section."
                }

                val sectionAgent = ParsedAgent(
                    resultClass = ScriptSegment::class.java,
                    prompt = """
You are an expert scriptwriter. Write Section ${sectionOutline.section_number} of this ${executionConfig.script_type} script.

Overall Topic: $topic
Section Title: ${sectionOutline.title}
Section Purpose: Cover these key points: ${sectionOutline.key_points.joinToString("; ")}
Target Duration: ${sectionOutline.estimated_duration_seconds} seconds
Tone: ${executionConfig.tone}
Pacing: ${executionConfig.pacing}

$previousContext

${
                        if (sectionOutline.visual_suggestions.isNotEmpty()) "Visual Suggestions: ${
                            sectionOutline.visual_suggestions.joinToString(
                                "; "
                            )
                        }" else ""
                    }

Write this section with:
1. Natural, conversational dialogue that sounds good when spoken
2. Clear transitions from the previous section
3. Logical flow through the key points
4. Appropriate pacing for ${executionConfig.pacing} delivery
${if (executionConfig.include_directions) "5. Visual directions for what appears on screen" else ""}
${if (executionConfig.suggest_b_roll) "6. B-roll suggestions to support the narration" else ""}
${if (executionConfig.include_notes) "7. Production notes for the speaker/director" else ""}
${if (executionConfig.mark_key_points) "8. Mark key points that should be emphasized or shown as graphics" else ""}

Ensure the dialogue:
- Sounds natural when read aloud
- Uses contractions and conversational language
- Varies sentence length for rhythm
- Includes pauses where appropriate
- Maintains the ${executionConfig.tone} tone
- Engages the ${executionConfig.target_audience}

Aim for approximately ${sectionOutline.estimated_duration_seconds} seconds of content.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = defaultFast
                )

                var sectionSegment = sectionAgent.answer(listOf("Write section")).obj
                scriptSegments.add(sectionSegment)
                cumulativeDuration += sectionSegment.duration_seconds
                cumulativeWordCount += sectionSegment.dialogue.split("\\s+".toRegex()).size

                sectionTask.add(
                  buildString {
                    appendLine("## ${sectionOutline.title}")
                    appendLine()
                    if (executionConfig.include_timing) {
                      appendLine("**[${formatTiming(cumulativeDuration - sectionSegment.duration_seconds)}]**")
                      appendLine()
                    }
                    if (executionConfig.include_directions && sectionSegment.visual_direction.isNotBlank()) {
                      appendLine("*${sectionSegment.visual_direction}*")
                      appendLine()
                    }
                    appendLine(sectionSegment.dialogue)
                    appendLine()
                    if (executionConfig.suggest_b_roll && sectionSegment.b_roll_suggestions.isNotEmpty()) {
                      appendLine("**B-Roll:**")
                      sectionSegment.b_roll_suggestions.forEach { broll ->
                        appendLine("- $broll")
                      }
                      appendLine()
                    }
                    if (executionConfig.mark_key_points && sectionSegment.key_points_marked.isNotEmpty()) {
                      appendLine("**Key Points for Graphics:**")
                      sectionSegment.key_points_marked.forEach { point ->
                        appendLine("- $point")
                      }
                      appendLine()
                    }
                    if (executionConfig.include_notes && sectionSegment.notes.isNotBlank()) {
                      appendLine("**Notes:** ${sectionSegment.notes}")
                      appendLine()
                    }
                    appendLine("---")
                    appendLine()
                    appendLine(
                      "**Duration:** ${sectionSegment.duration_seconds}s | **Words:** ${
                        sectionSegment.dialogue.split(
                          "\\s+".toRegex()
                        ).size
                      }"
                    )
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                  }.renderMarkdown(true)
                )
                markdownTranscript?.write("## Section ${sectionOutline.section_number}: ${sectionOutline.title}\n\n".toByteArray())
                markdownTranscript?.write(sectionSegment.dialogue.toByteArray())
                markdownTranscript?.write("\n\n".toByteArray())
                task.update()

                overviewTask.add("✅ (${sectionSegment.duration_seconds}s)\n".renderMarkdown(true))
                task.update()
            }

            // Write closing
            log.info("Writing closing")
            overviewTask.add("- Closing ".renderMarkdown(true))
            task.update()

            val closingTask = tabs.newTask("Closing")

            closingTask.add(
              buildString {
                appendLine("# Closing")
                appendLine()
                appendLine("**Status:** Writing closing...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()

            val closingAgent = ParsedAgent(
                resultClass = ScriptSegment::class.java,
                prompt = """
You are an expert scriptwriter. Write the closing for this ${executionConfig.script_type} script.

Topic: $topic
Closing Concept: ${outline.closing}
Tone: ${executionConfig.tone}
Target Audience: ${executionConfig.target_audience}
${if (executionConfig.include_cta) "Include Call-to-Action: Yes" else ""}

Key Messages Covered:
${outline.key_messages.joinToString("\n") { "- $it" }}

Previous Script Context:
${scriptSegments.takeLast(1).firstOrNull()?.dialogue?.takeLast(200) ?: ""}

Write a closing that:
1. Summarizes the key takeaways
2. Reinforces the main message
3. Leaves a lasting impression
${if (executionConfig.include_cta) "4. Includes a clear, compelling call-to-action" else "4. Ends on a strong note"}
5. Sounds natural and conversational
${if (executionConfig.include_directions) "6. Includes visual direction for the final shot" else ""}

Make it memorable and motivating.
Target duration: 15-20 seconds.
          """.trimIndent(),
                model = api,
                temperature = 0.8,
                parsingChatter = defaultFast
            )

            var closingSegment = closingAgent.answer(listOf("Write closing")).obj
            scriptSegments.add(closingSegment)
            cumulativeDuration += closingSegment.duration_seconds
            cumulativeWordCount += closingSegment.dialogue.split("\\s+".toRegex()).size

            closingTask.add(
              buildString {
                appendLine("## Closing")
                appendLine()
                if (executionConfig.include_timing) {
                  appendLine("**[${formatTiming(cumulativeDuration - closingSegment.duration_seconds)}]**")
                  appendLine()
                }
                if (executionConfig.include_directions && closingSegment.visual_direction.isNotBlank()) {
                  appendLine("*${closingSegment.visual_direction}*")
                  appendLine()
                }
                appendLine(closingSegment.dialogue)
                appendLine()
                if (executionConfig.suggest_b_roll && closingSegment.b_roll_suggestions.isNotEmpty()) {
                  appendLine("**B-Roll:**")
                  closingSegment.b_roll_suggestions.forEach { broll ->
                    appendLine("- $broll")
                  }
                  appendLine()
                }
                if (executionConfig.include_notes && closingSegment.notes.isNotBlank()) {
                  appendLine("**Notes:** ${closingSegment.notes}")
                  appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine(
                  "**Duration:** ${closingSegment.duration_seconds}s | **Words:** ${
                    closingSegment.dialogue.split(
                      "\\s+".toRegex()
                    ).size
                  }"
                )
                appendLine()
                appendLine("**Status:** ✅ Complete")
              }.renderMarkdown(true)
            )
            markdownTranscript?.write("# Closing\n\n".toByteArray())
            markdownTranscript?.write(closingSegment.dialogue.toByteArray())
            markdownTranscript?.write("\n\n".toByteArray())
            task.update()

            overviewTask.add("✅\n".renderMarkdown(true))
            overviewTask.add("✅ Phase 2 Complete: All segments written\n".renderMarkdown(true))
            task.update()

            // Phase 3: Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                overviewTask.add(
                  "\n### Phase 3: Revision\n*Refining script for flow and timing...*\n".renderMarkdown(
                    true
                  )
                )
                task.update()

                log.info("Phase 3: Performing ${executionConfig.revision_passes} revision pass(es)")
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

                val fullScript = buildFullScript(outline, scriptSegments)

                repeat(executionConfig.revision_passes) { passNum ->
                    log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")

                    val revisionAgent = ChatAgent(
                        prompt = """
You are an expert script editor. Review and improve this ${executionConfig.script_type} script.

Current Script:
$fullScript

Focus on:
1. Natural dialogue flow and conversational tone
2. Pacing and rhythm (target: ${executionConfig.pacing})
3. Clarity and conciseness
4. Smooth transitions between sections
5. Timing accuracy (target: ${executionConfig.target_duration_minutes} minutes)
6. Engagement and audience connection
7. Consistency in tone (${executionConfig.tone})

Maintain:
- All key messages and content
- The overall structure
- Approximate duration ($cumulativeDuration seconds)
- The ${executionConfig.tone} tone

Provide the complete revised script with all formatting intact.
            """.trimIndent(),
                        model = api,
                        temperature = 0.6
                    )

                    revisionAgent.answer(listOf("Revise the script"))

                    revisionTask.add(
                      buildString {
                        appendLine("## Revision Pass ${passNum + 1}")
                        appendLine()
                        appendLine("✅ Complete")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    task.update()
                }

                overviewTask.add(
                  "✅ Phase 3 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown(
                    true
                  )
                )
            }

            // Phase 4: Final Assembly
            overviewTask.add("\n### Phase 4: Final Assembly\n*Compiling complete script...*\n".renderMarkdown(true))
            task.update()

            log.info("Phase 4: Assembling final script")
            val finalTask = tabs.newTask("Complete Script")

            val finalScript = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine("**Script Type:** ${executionConfig.script_type.capitalize()}")
                appendLine("**Duration:** ${formatTiming(cumulativeDuration)} (${cumulativeDuration}s)")
                appendLine("**Word Count:** $cumulativeWordCount")
                appendLine("**Tone:** ${executionConfig.tone.capitalize()}")
                appendLine("**Target Audience:** ${executionConfig.target_audience}")
                appendLine()
                appendLine("---")
                appendLine()

                var currentTime = 0
                scriptSegments.forEachIndexed { index, segment ->
                    if (executionConfig.include_timing) {
                        appendLine("**[${formatTiming(currentTime)}]**")
                        appendLine()
                    }

                    if (executionConfig.include_directions && segment.visual_direction.isNotBlank()) {
                        appendLine("*${segment.visual_direction}*")
                        appendLine()
                    }

                    appendLine(segment.dialogue)
                    appendLine()

                    if (executionConfig.suggest_b_roll && segment.b_roll_suggestions.isNotEmpty()) {
                        appendLine("**B-Roll:**")
                        segment.b_roll_suggestions.forEach { broll ->
                            appendLine("- $broll")
                        }
                        appendLine()
                    }

                    if (executionConfig.mark_key_points && segment.key_points_marked.isNotEmpty()) {
                        appendLine("**Key Points for Graphics:**")
                        segment.key_points_marked.forEach { point ->
                            appendLine("- $point")
                        }
                        appendLine()
                    }

                    if (executionConfig.include_notes && segment.notes.isNotBlank()) {
                        appendLine("**Notes:** ${segment.notes}")
                        appendLine()
                    }

                    currentTime += segment.duration_seconds

                    if (index < scriptSegments.size - 1) {
                        appendLine("---")
                        appendLine()
                    }
                }

                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**END OF SCRIPT**")
                appendLine()
                appendLine("**Total Duration:** ${formatTiming(cumulativeDuration)}")
                appendLine("**Total Word Count:** $cumulativeWordCount")
                appendLine("**Average Words Per Minute:** ${(cumulativeWordCount.toFloat() / (cumulativeDuration / 60f)).toInt()}")
            }

            finalTask.add(finalScript.renderMarkdown(true))
            markdownTranscript?.write("\n---\n\n# Complete Script\n\n".toByteArray())
            markdownTranscript?.write(finalScript.toByteArray())
            markdownTranscript?.write("\n".toByteArray())
            task.update()

            // Production notes tab
            if (executionConfig.include_notes) {
                val productionNotesTask = tabs.newTask("Production Notes")

                val productionNotes = buildString {
                    appendLine("# Production Notes")
                    appendLine()
                    appendLine("## Script Overview")
                    appendLine("- **Total Duration:** ${formatTiming(cumulativeDuration)}")
                    appendLine("- **Total Segments:** ${scriptSegments.size}")
                    appendLine("- **Word Count:** $cumulativeWordCount")
                    appendLine("- **Average WPM:** ${(cumulativeWordCount.toFloat() / (cumulativeDuration / 60f)).toInt()}")
                    appendLine()
                    appendLine("## Timing Breakdown")
                    var segmentTime = 0
                    scriptSegments.forEachIndexed { index, segment ->
                        val segmentType = when {
                            index == 0 && executionConfig.include_hook -> "Opening Hook"
                            index == scriptSegments.size - 1 -> "Closing"
                            else -> "Section ${index}"
                        }
                        appendLine("- **$segmentType:** ${formatTiming(segmentTime)} - ${formatTiming(segmentTime + segment.duration_seconds)} (${segment.duration_seconds}s)")
                        segmentTime += segment.duration_seconds
                    }
                    appendLine()
                    if (executionConfig.suggest_b_roll) {
                        appendLine("## B-Roll Requirements")
                        val allBRoll = scriptSegments.flatMap { it.b_roll_suggestions }.distinct()
                        if (allBRoll.isNotEmpty()) {
                            allBRoll.forEach { broll ->
                                appendLine("- $broll")
                            }
                        } else {
                            appendLine("*No specific B-roll requirements*")
                        }
                        appendLine()
                    }
                    if (executionConfig.mark_key_points) {
                        appendLine("## Graphics/Text Overlays")
                        val allKeyPoints = scriptSegments.flatMap { it.key_points_marked }
                        if (allKeyPoints.isNotEmpty()) {
                            allKeyPoints.forEach { point ->
                                appendLine("- $point")
                            }
                        } else {
                            appendLine("*No specific graphics requirements*")
                        }
                        appendLine()
                    }
                    appendLine("## Key Messages")
                    outline.key_messages.forEach { message ->
                        appendLine("- $message")
                    }
                }

                productionNotesTask.add(productionNotes.renderMarkdown(true))
                markdownTranscript?.write("\n---\n\n".toByteArray())
                markdownTranscript?.write(productionNotes.toByteArray())
                markdownTranscript?.write("\n".toByteArray())
                task.update()
            }

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime
            val targetDurationDiff = cumulativeDuration - targetDurationSeconds
            val durationAccuracy = 100 - (Math.abs(targetDurationDiff).toFloat() / targetDurationSeconds * 100)

            overviewTask.add(
              buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Generation Complete")
                appendLine()
                appendLine("**Statistics:**")
                appendLine("- Total Duration: ${formatTiming(cumulativeDuration)} (${cumulativeDuration}s)")
                appendLine("- Target Duration: ${formatTiming(targetDurationSeconds)} (${targetDurationSeconds}s)")
                appendLine("- Duration Accuracy: ${durationAccuracy.toInt()}%")
                appendLine("- Total Word Count: $cumulativeWordCount")
                appendLine("- Average WPM: ${(cumulativeWordCount.toFloat() / (cumulativeDuration / 60f)).toInt()}")
                appendLine("- Number of Segments: ${scriptSegments.size}")
                appendLine("- Total Time: ${totalTime / 1000.0}s")
                appendLine()
                appendLine(
                  "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                  }"
                )
              }.renderMarkdown(true)
            )
            markdownTranscript?.write("\n---\n\n## Generation Complete\n\n".toByteArray())
            markdownTranscript?.write("Script generation completed successfully.\n".toByteArray())
            task.update()

            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# Script Generation Summary: ${outline.title}")
                appendLine()
                appendLine("A complete ${executionConfig.script_type} script of **${formatTiming(cumulativeDuration)}** ($cumulativeWordCount words) was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Topic:** $topic")
                appendLine()
                appendLine("**Structure:**")
                if (executionConfig.include_hook) {
                    appendLine("- Opening hook")
                }
                appendLine("- ${outline.sections.size} main sections")
                appendLine("- Closing${if (executionConfig.include_cta) " with call-to-action" else ""}")
                appendLine()
                appendLine("**Duration Accuracy:** ${durationAccuracy.toInt()}% (target: ${executionConfig.target_duration_minutes}m)")
                appendLine()
                appendLine("> The complete script with all formatting, timing, and production notes is available in the Complete Script tab.")
            }

            log.info("ScriptwritingTask completed: duration=${cumulativeDuration}s, words=$cumulativeWordCount, segments=${scriptSegments.size}, time=${totalTime}ms")


            // Best Practice: Use acceptButtonFooter for manual review
            task.add(acceptButtonFooter(task.ui) {
                semaphore.release()
            })
            semaphore.acquire()

            task.complete()
            log.info("Script generation complete: ${formatTiming(cumulativeDuration)} in ${totalTime / 1000}s")
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during script generation", e)
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
            task.update()
            markdownTranscript?.close()

            val errorOutput = buildString {
                appendLine("# Error in Script Generation")
                appendLine()
                appendLine("**Topic:** $topic")
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
        markdownTranscript?.close()
    }

    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""
        if (relatedFiles.isEmpty()) return ""
        log.debug("Loading ${relatedFiles.size} related context files")

        return buildString {
            appendLine("## Related Research Files")
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

    private fun formatTiming(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun buildFullScript(outline: ScriptOutline, segments: List<ScriptSegment>): String {
        return buildString {
            appendLine("# ${outline.title}")
            appendLine()
            var currentTime = 0
            segments.forEach { segment ->
                appendLine("[${formatTiming(currentTime)}]")
                if (segment.visual_direction.isNotBlank()) {
                    appendLine("*${segment.visual_direction}*")
                }
                appendLine(segment.dialogue)
                appendLine()
                currentTime += segment.duration_seconds
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ScriptwritingTask::class.java)

        fun extractDocumentContent(file: File) = try {
            file.readText()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }

        @JvmStatic val Scriptwriting = TaskType(
          name = "Scriptwriting",
          category = "Writing",
          taskClass = ScriptwritingTask::class.java,
          executionConfigClass = ScriptwritingTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate complete scripts for videos, podcasts, and presentations",
          tooltipHtml = """
                        Generates production-ready scripts with dialogue, timing, and production notes.
                        <ul>
                          <li>Creates detailed script outline with sections and timing</li>
                          <li>Writes natural, conversational dialogue for spoken delivery</li>
                          <li>Includes visual directions and scene descriptions</li>
                          <li>Suggests B-roll and supporting visuals</li>
                          <li>Marks key points for emphasis or graphics</li>
                          <li>Provides timing markers and duration estimates</li>
                          <li>Includes production notes and speaker guidance</li>
                          <li>Supports multiple script types (video, podcast, presentation, commercial)</li>
                          <li>Configurable tone, pacing, and audience targeting</li>
                          <li>Optional revision passes for quality improvement</li>
                          <li>Ideal for video production, podcasts, presentations, training videos</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/SoftwareDesignDocumentTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
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
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class SoftwareDesignDocumentTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: SoftwareDesignDocumentTaskExecutionConfigData?
) : AbstractTask<SoftwareDesignDocumentTask.SoftwareDesignDocumentTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  val maxDescriptionLength = 2000

  class SoftwareDesignDocumentTaskExecutionConfigData(
    @Description("The name/title of the software project")
    var project_name: String? = null,
    @Description("High-level description of the software system to design")
    var system_description: String? = null,
    @Description("Target audience for the software (e.g., 'enterprise users', 'mobile consumers')")
    var target_audience: String? = null,
    @Description("Key stakeholders and their roles")
    var stakeholders: List<String>? = null,
    @Description("Whether to generate use case diagrams and documentation")
    var generate_use_cases: Boolean = true,
    @Description("Whether to generate functional and non-functional requirements")
    var generate_requirements: Boolean = true,
    @Description("Whether to generate architectural diagrams (C4, component, deployment)")
    var generate_architecture: Boolean = true,
    @Description("Whether to generate data model and ERD diagrams")
    var generate_data_model: Boolean = true,
    @Description("Whether to generate sequence and activity diagrams for key flows")
    var generate_flow_diagrams: Boolean = true,
    @Description("Whether to generate test plan and test case documentation")
    var generate_test_plan: Boolean = true,
    @Description("Whether to generate phase planning with milestones")
    var generate_phase_plan: Boolean = true,
    @Description("Whether to generate the project data JSON file with tasks, epics, sprints, etc.")
    var generate_project_data: Boolean = true,
    @Description("Number of sprints to plan (default: 6)")
    var sprint_count: Int = 6,
    @Description("Sprint duration in weeks (default: 2)")
    var sprint_duration_weeks: Int = 2,
    @Description("Technology stack constraints or preferences")
    var technology_stack: List<String>? = null,
    @Description("Known constraints or limitations")
    var constraints: List<String>? = null,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for context")
    var input_files: List<String>? = null,
    @Description("Description of the task")
    task_description: String? = null,
    @Description("List of task IDs this task depends on")
    task_dependencies: List<String>? = null,
    @Description("The current state of the task")
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = SoftwareDesignDocument.name,
    task_description = task_description
      ?: "Generate software design document for: ${project_name ?: system_description?.take(50)}",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  // Data classes for project planning JSON output
  data class ProjectData(
    val project_name: String = "",
    val description: String = "",
    val created_date: String = "",
    val epics: List<Epic> = emptyList(),
    val releases: List<Release> = emptyList(),
    val sprints: List<Sprint> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val dependencies: List<Dependency> = emptyList()
  )

  data class Epic(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val priority: String = "Medium",
    val status: String = "Planned",
    val story_points: Int? = null
  )

  data class Release(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val target_date: String = "",
    val description: String = "",
    val epic_ids: List<String> = emptyList(),
    val status: String = "Planned"
  )

  data class Sprint(
    val id: String = "",
    val name: String = "",
    val number: Int = 0,
    val start_date: String = "",
    val end_date: String = "",
    val goals: List<String> = emptyList(),
    val capacity_points: Int = 0,
    val task_ids: List<String> = emptyList(),
    val status: String = "Planned"
  )

  data class Task(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val type: String = "Feature", // "Feature", "Bug", "Chore", "Spike"
    val epic_id: String? = null,
    var sprint_id: String? = null,
    val priority: String = "Medium", // "Low", "Medium", "High", "Critical"
    val story_points: Int? = null,
    val status: String = "Backlog",
    val acceptance_criteria: List<String>? = null,
    val labels: List<String>? = null
  )

  data class Milestone(
    val id: String = "",
    val name: String = "",
    val target_date: String = "",
    val description: String = "",
    val deliverables: List<String> = emptyList(),
    val status: String = "Planned"
  )

  data class Dependency(
    val id: String = "",
    val source_id: String = "",
    val source_type: String = "", // "task", "epic", "milestone"
    val target_id: String = "",
    val target_type: String = "", // "task", "epic", "milestone"
    val dependency_type: String = "" // "blocks", "depends_on", "relates_to"
  )

  override fun promptSegment(): String {
    return """
SoftwareDesignDocument - Generate comprehensive software design documentation
  ** Specify the project name and system description
  ** Generate use case diagrams and actor documentation
  ** Create functional and non-functional requirements
  ** Produce architectural diagrams (C4, component, deployment)
  ** Design data models with ERD diagrams
  ** Create sequence and activity diagrams for key flows
  ** Generate test plans and test case documentation
  ** Plan development phases with milestones
  ** Output project data JSON with tasks, epics, sprints, releases
  ** All diagrams use Mermaid syntax for easy rendering
  ** Useful for:
     - Project kickoff documentation
     - Technical specification creation
     - Sprint and release planning
     - Stakeholder communication
     - Development team onboarding
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {

    val projectName = executionConfig?.project_name ?: "Unnamed Project"
    log.info("Task 'SoftwareDesignDocument' started for project: $projectName")
    val startTime = System.currentTimeMillis()

    val systemDescription = executionConfig?.system_description
    if (systemDescription.isNullOrBlank()) {
      val errorMsg = "CONFIGURATION ERROR: No system description specified"
      log.error(errorMsg)
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }

    val tabs = TabbedDisplay(task)
    val transcriptStream = task.transcript()
    val overviewTask = tabs.newTask("Overview")

    task.ui.pool.submit {
      try {
        val api = defaultSmart.getChildClient(task)
        overviewTask.header("Software Design Document: $projectName")
        val checklist = mutableMapOf<String, String>()
        fun updateChecklist() {
          val content = buildString {
            appendLine("### Generation Progress")
            checklist.forEach { (name, status) -> appendLine("- $status $name") }
          }
          overviewTask.add(content.renderMarkdown(true))
        }

        if (executionConfig.generate_use_cases) checklist["Use Cases & Actors"] = "⏳"
        if (executionConfig.generate_requirements) checklist["Requirements Specification"] = "⏳"
        if (executionConfig.generate_architecture) checklist["Architecture Diagrams"] = "⏳"
        if (executionConfig.generate_data_model) checklist["Data Model & ERD"] = "⏳"
        if (executionConfig.generate_flow_diagrams) checklist["Flow Diagrams"] = "⏳"
        if (executionConfig.generate_test_plan) checklist["Test Plan"] = "⏳"
        if (executionConfig.generate_phase_plan) checklist["Phase Planning"] = "⏳"
        if (executionConfig.generate_project_data) checklist["Project Data JSON"] = "⏳"


        val targetAudience = executionConfig.target_audience ?: "general users"

        overviewTask.add(
          buildString {
                      appendLine("**System:** ${systemDescription.take(200)}${if (systemDescription.length > 200) "..." else ""}")
                      appendLine()
                      appendLine("**Target Audience:** $targetAudience")
                    }.renderMarkdown(true)
        )
        val statusBuffer = overviewTask.add("**Status:** 🔄 Gathering context...".renderMarkdown(true))
        updateChecklist()

        transcriptStream?.write(
          "# Software Design Document: $projectName\n\n**System:** $systemDescription\n\n**Generated:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }\n\n---\n\n".toByteArray()
        )
        task.update()

        // Gather context
        log.debug("Gathering context from prior tasks and input files")
        val priorContext = getPriorCode(agent.executionState)
        val inputFileContext = getInputFileCode()
        transcriptStream?.write(
          """
                    <details>
                    <summary>Input Context Data</summary>
                    ### Prior Task Context
                    $priorContext
                    ### Input File Context
                    $inputFileContext
                    </details>
                """.trimIndent().toByteArray()
        )

        // Initialize design agent
        log.info("Initializing software design agent")
        val designAgent = ChatAgent(
          prompt = buildDesignPrompt(
            projectName = projectName,
            systemDescription = systemDescription,
            targetAudience = targetAudience,
//                  stakeholders = stakeholders,
//                  techStack = techStack,
//                  constraints = constraints,
            priorContext = priorContext,
            inputFileContext = inputFileContext
          ),
          model = api,
          temperature = 0.5
        )

        // Collected data for project JSON
        val collectedEpics = mutableListOf<Epic>()
        val collectedTasks = mutableListOf<Task>()
        val collectedMilestones = mutableListOf<Milestone>()
        val collectedDependencies = mutableListOf<Dependency>()

        // Section 1: Use Cases
        if (executionConfig.generate_use_cases) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Generating Use Cases...".renderMarkdown(true))
          log.debug("Generating use cases and actor documentation")
          val useCaseTask = tabs.newTask("Use Cases")

          val useCaseBuffer = useCaseTask.add("## Use Cases\n\n🔄 Analyzing actors and use cases...".renderMarkdown(true))
          task.update()

          val useCaseAnalysis = designAgent.answer(
            listOf(
              """
Generate comprehensive use case documentation:

1. **Actor Identification**
   - List all actors (users, systems, external services)
   - Describe each actor's role and goals
   - Identify actor relationships

2. **Use Case Catalog**
   For each major use case:
   - UC-ID and Name
   - Primary Actor
   - Preconditions
   - Main Success Scenario (numbered steps)
   - Alternative Flows
   - Postconditions
   - Business Rules

3. **Use Case Diagram** (Mermaid)
   Create a use case diagram showing actors and their interactions with the system.
   Use this format:
   ```mermaid
   graph LR
       subgraph Actors
           A1[Actor 1]
           A2[Actor 2]
       end
       subgraph System
           UC1((Use Case 1))
           UC2((Use Case 2))
       end
       A1 --> UC1
       A1 --> UC2
       A2 --> UC2
   ```

4. **Actor-Use Case Matrix**
   Show which actors participate in which use cases.

Provide detailed, actionable use case documentation.
                        """.trimIndent()
            )
          )

          useCaseBuffer?.setLength(0)
          useCaseBuffer?.append(
            buildString {
                          appendLine("## Use Cases & Actors")
                          appendLine()
                          appendLine("✅ Analysis complete")
                          appendLine()
                          appendLine(useCaseAnalysis)
                        }.renderMarkdown(true)
          )
          useCaseTask.update()
          transcriptStream?.write("## Use Cases & Actors\n\n$useCaseAnalysis\n\n---\n\n".toByteArray())

          // Extract epics from use cases
          extractEpicsFromUseCases(useCaseAnalysis, collectedEpics)
          checklist["Use Cases & Actors"] = "✅"
          updateChecklist()
        }

        // Section 2: Requirements
        if (executionConfig.generate_requirements) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Defining Requirements...".renderMarkdown(true))
          log.debug("Generating requirements specification")
          val requirementsTask = tabs.newTask("Requirements")

          val requirementsBuffer =
            requirementsTask.add(
              "## Requirements\n\n🔄 Defining functional and non-functional requirements...".renderMarkdown(
                true
              )
            )
          task.update()

          val requirementsAnalysis = designAgent.answer(
            listOf(
              """
Generate comprehensive requirements documentation:

1. **Functional Requirements**
   For each requirement:
   - FR-ID: Unique identifier
   - Description: Clear, testable statement
   - Priority: Must Have / Should Have / Could Have / Won't Have (MoSCoW)
   - Source: Which use case or stakeholder
   - Acceptance Criteria: Specific, measurable criteria

2. **Non-Functional Requirements**
   Categories to cover:
   - Performance (response times, throughput)
   - Scalability (users, data volume)
   - Security (authentication, authorization, data protection)
   - Reliability (uptime, recovery)
   - Usability (accessibility, UX standards)
   - Maintainability (code quality, documentation)
   - Compatibility (browsers, devices, integrations)

3. **Requirements Traceability Matrix**
   Show relationships between:
   - Use Cases → Requirements
   - Requirements → Test Cases (placeholder IDs)

4. **Requirements Dependency Diagram** (Mermaid)
   ```mermaid
   graph TD
       FR1[FR-001: User Login] --> FR2[FR-002: Session Management]
       FR2 --> FR3[FR-003: Access Control]
       NFR1[NFR-001: Response Time] -.-> FR1
   ```

Provide detailed, prioritized requirements.
                        """.trimIndent()
            )
          )

          requirementsBuffer?.setLength(0)
          requirementsBuffer?.append(
            buildString {
                          appendLine("## Requirements Specification")
                          appendLine()
                          appendLine("✅ Requirements defined")
                          appendLine()
                          appendLine(requirementsAnalysis)
                        }.renderMarkdown(true)
          )
          requirementsTask.update()
          transcriptStream?.write("## Requirements Specification\n\n$requirementsAnalysis\n\n---\n\n".toByteArray())

          // Extract tasks from requirements
//               extractTasksFrom requirements(requirementsAnalysis, collectedTasks, collectedEpics)
          checklist["Requirements Specification"] = "✅"
          updateChecklist()
        }

        // Section 3: Architecture
        if (executionConfig.generate_architecture) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Designing Architecture...".renderMarkdown(true))
          log.debug("Generating architectural diagrams")
          val architectureTask = tabs.newTask("Architecture")

          val architectureBuffer =
            architectureTask.add("## Architecture\n\n🔄 Designing system architecture...".renderMarkdown(true))
          task.update()

          val architectureAnalysis = designAgent.answer(
            listOf(
              """
Generate comprehensive architecture documentation:

1. **System Context Diagram (C4 Level 1)**
   Show the system and its relationships with users and external systems.
   ```mermaid
   graph TB
       subgraph External
           U[Users]
           ES1[External System 1]
       end
       S[System Name]
       U --> S
       S --> ES1
   ```

2. **Container Diagram (C4 Level 2)**
   Show high-level technology choices and container responsibilities.
   ```mermaid
   graph TB
       subgraph System
           WA[Web Application<br/>React]
           API[API Server<br/>Node.js]
           DB[(Database<br/>PostgreSQL)]
           CACHE[(Cache<br/>Redis)]
       end
       WA --> API
       API --> DB
       API --> CACHE
   ```

3. **Component Diagram (C4 Level 3)**
   For key containers, show internal components.

4. **Deployment Diagram**
   Show infrastructure and deployment topology.
   ```mermaid
   graph TB
       subgraph Cloud Provider
           subgraph Production
               LB[Load Balancer]
               subgraph App Tier
                   APP1[App Server 1]
                   APP2[App Server 2]
               end
               subgraph Data Tier
                   DB1[(Primary DB)]
                   DB2[(Replica DB)]
               end
           end
       end
       LB --> APP1
       LB --> APP2
       APP1 --> DB1
       APP2 --> DB1
       DB1 --> DB2
   ```

5. **Technology Stack Summary**
   - Frontend technologies
   - Backend technologies
   - Data storage
   - Infrastructure
   - DevOps tools

6. **Architecture Decision Records (ADRs)**
   Document key architectural decisions with:
   - Context
   - Decision
   - Consequences

Provide detailed architecture documentation with all diagrams.
                        """.trimIndent()
            )
          )

          architectureBuffer?.setLength(0)
          architectureBuffer?.append(
            buildString {
                          appendLine("## System Architecture")
                          appendLine()
                          appendLine("✅ Architecture designed")
                          appendLine()
                          appendLine(architectureAnalysis)
                        }.renderMarkdown(true)
          )
          architectureTask.update()
          transcriptStream?.write("## System Architecture\n\n$architectureAnalysis\n\n---\n\n".toByteArray())

          // Add architecture epic
          collectedEpics.add(
            Epic(
              id = "EPIC-ARCH",
              name = "Architecture & Infrastructure",
              description = "Set up system architecture and infrastructure",
              priority = "High",
              story_points = 21
            )
          )
          checklist["Architecture Diagrams"] = "✅"
          updateChecklist()
        }

        // Section 4: Data Model
        if (executionConfig.generate_data_model) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Designing Data Model...".renderMarkdown(true))
          log.debug("Generating data model and ERD")
          val dataModelTask = tabs.newTask("Data Model")

          val dataModelBuffer = dataModelTask.add("## Data Model\n\n🔄 Designing data structures...".renderMarkdown(true))
          task.update()

          val dataModelAnalysis = designAgent.answer(
            listOf(
              """
Generate comprehensive data model documentation:

1. **Entity-Relationship Diagram**
   ```mermaid
   erDiagram
       USER ||--o{ ORDER : places
       USER {
           int id PK
           string email UK
           string name
           datetime created_at
       }
       ORDER ||--|{ ORDER_ITEM : contains
       ORDER {
           int id PK
           int user_id FK
           decimal total
           string status
           datetime created_at
       }
       ORDER_ITEM {
           int id PK
           int order_id FK
           int product_id FK
           int quantity
           decimal price
       }
       PRODUCT ||--o{ ORDER_ITEM : "ordered in"
       PRODUCT {
           int id PK
           string name
           string description
           decimal price
           int stock
       }
   ```

2. **Entity Descriptions**
   For each entity:
   - Purpose and business meaning
   - Attributes with types and constraints
   - Relationships and cardinality
   - Indexes and performance considerations

3. **Data Dictionary**
   | Entity | Attribute | Type | Constraints | Description |
   |--------|-----------|------|-------------|-------------|
   | User | id | INT | PK, AUTO | Unique identifier |

4. **Data Flow Diagram**
   Show how data moves through the system.

5. **Data Validation Rules**
   Business rules for data integrity.

6. **Data Migration Considerations**
   If migrating from existing systems.

Provide complete data model documentation.
                        """.trimIndent()
            )
          )

          dataModelBuffer?.setLength(0)
          dataModelBuffer?.append(
            buildString {
                          appendLine("## Data Model & ERD")
                          appendLine()
                          appendLine("✅ Data model designed")
                          appendLine()
                          appendLine(dataModelAnalysis)
                        }.renderMarkdown(true)
          )
          dataModelTask.update()
          transcriptStream?.write("## Data Model & ERD\n\n$dataModelAnalysis\n\n---\n\n".toByteArray())
          checklist["Data Model & ERD"] = "✅"
          updateChecklist()
        }

        // Section 5: Flow Diagrams
        if (executionConfig.generate_flow_diagrams) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Mapping Flow Diagrams...".renderMarkdown(true))
          log.debug("Generating sequence and activity diagrams")
          val flowTask = tabs.newTask("Flow Diagrams")

          val flowBuffer = flowTask.add("## Flow Diagrams\n\n🔄 Mapping system flows...".renderMarkdown(true))
          task.update()

          val flowAnalysis = designAgent.answer(
            listOf(
              """
Generate flow diagrams for key system interactions:

1. **Sequence Diagrams**
   For 3-5 critical user journeys, create sequence diagrams:
   ```mermaid
   sequenceDiagram
       participant U as User
       participant W as Web App
       participant A as API Server
       participant D as Database
       
       U->>W: Login Request
       W->>A: POST /auth/login
       A->>D: Query User
       D-->>A: User Data
       A->>A: Validate Credentials
       A->>A: Generate JWT
       A-->>W: JWT Token
       W-->>U: Login Success
   ```

2. **Activity Diagrams**
   For complex business processes:
   ```mermaid
   graph TD
       A[Start] --> B{User Authenticated?}
       B -->|Yes| C[Load Dashboard]
       B -->|No| D[Show Login]
       D --> E[Enter Credentials]
       E --> F{Valid?}
       F -->|Yes| C
       F -->|No| G[Show Error]
       G --> D
       C --> H[End]
   ```

3. **State Diagrams**
   For entities with complex state transitions:
   ```mermaid
   stateDiagram-v2
       [*] --> Draft
       Draft --> Submitted: Submit
       Submitted --> UnderReview: Assign Reviewer
       UnderReview --> Approved: Approve
       UnderReview --> Rejected: Reject
       Rejected --> Draft: Revise
       Approved --> [*]
   ```

4. **Integration Flow Diagrams**
   Show data flow between systems.

5. **Error Handling Flows**
   Document how errors propagate and are handled.

Provide detailed flow documentation for all critical paths.
                        """.trimIndent()
            )
          )

          flowBuffer?.setLength(0)
          flowBuffer?.append(
            buildString {
                          appendLine("## Flow Diagrams")
                          appendLine()
                          appendLine("✅ Flows documented")
                          appendLine()
                          appendLine(flowAnalysis)
                        }.renderMarkdown(true)
          )
          flowTask.update()
          transcriptStream?.write("## Flow Diagrams\n\n$flowAnalysis\n\n---\n\n".toByteArray())
          checklist["Flow Diagrams"] = "✅"
          updateChecklist()
        }

        // Section 6: Test Plan
        if (executionConfig.generate_test_plan) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Creating Test Plan...".renderMarkdown(true))
          log.debug("Generating test plan")
          val testPlanTask = tabs.newTask("Test Plan")

          val testPlanBuffer = testPlanTask.add("## Test Plan\n\n🔄 Creating test strategy...".renderMarkdown(true))
          task.update()

          val testPlanAnalysis = designAgent.answer(
            listOf(
              """
Generate comprehensive test plan documentation:

1. **Test Strategy Overview**
   - Testing objectives
   - Testing scope (in-scope/out-of-scope)
   - Testing approach
   - Entry/Exit criteria

2. **Test Levels**
   - Unit Testing: Coverage targets, frameworks
   - Integration Testing: API testing, component integration
   - System Testing: End-to-end scenarios
   - Acceptance Testing: UAT criteria

3. **Test Case Catalog**
   | TC-ID | Requirement | Description | Steps | Expected Result | Priority |
   |-------|-------------|-------------|-------|-----------------|----------|
   | TC-001 | FR-001 | User login with valid credentials | 1. Navigate to login... | User is authenticated | High |

4. **Test Coverage Matrix**
   ```mermaid
   graph LR
       subgraph Requirements
           FR1[FR-001]
           FR2[FR-002]
           FR3[FR-003]
       end
       subgraph Test Cases
           TC1[TC-001]
           TC2[TC-002]
           TC3[TC-003]
           TC4[TC-004]
       end
       FR1 --> TC1
       FR1 --> TC2
       FR2 --> TC3
       FR3 --> TC4
   ```

5. **Non-Functional Test Cases**
   - Performance test scenarios
   - Security test scenarios
   - Usability test scenarios

6. **Test Environment Requirements**
   - Hardware/software requirements
   - Test data requirements
   - Tool requirements

7. **Test Schedule**
   Timeline for test phases.

8. **Risk Assessment**
   Testing risks and mitigation strategies.

Provide actionable test documentation.
                        """.trimIndent()
            )
          )

          testPlanBuffer?.setLength(0)
          testPlanBuffer?.append(
            buildString {
                          appendLine("## Test Plan")
                          appendLine()
                          appendLine("✅ Test plan created")
                          appendLine()
                          appendLine(testPlanAnalysis)
                        }.renderMarkdown(true)
          )
          testPlanTask.update()
          transcriptStream?.write("## Test Plan\n\n$testPlanAnalysis\n\n---\n\n".toByteArray())

          // Add testing epic
          collectedEpics.add(
            Epic(
              id = "EPIC-TEST",
              name = "Quality Assurance",
              description = "Testing and quality assurance activities",
              priority = "High",
              story_points = 13
            )
          )
          checklist["Test Plan"] = "✅"
          updateChecklist()
        }

        // Section 7: Phase Planning
        if (executionConfig.generate_phase_plan) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Planning Phases...".renderMarkdown(true))
          log.debug("Generating phase plan")
          val phasePlanTask = tabs.newTask("Phase Plan")

          val phasePlanBuffer =
            phasePlanTask.add("## Phase Plan\n\n🔄 Planning development phases...".renderMarkdown(true))
          task.update()
          val sprintCount = executionConfig.sprint_count
          val sprintDuration = executionConfig.sprint_duration_weeks

          val phasePlanAnalysis = designAgent.answer(
            listOf(
              """
Generate development phase planning:

1. **Project Timeline Overview**
   ```mermaid
   gantt
       title Project Timeline
       dateFormat  YYYY-MM-DD
       section Phase 1: Foundation
       Architecture Setup    :a1, 2024-01-01, 2w
       Core Infrastructure   :a2, after a1, 2w
       section Phase 2: Core Features
       User Management       :b1, after a2, 3w
       Core Business Logic   :b2, after b1, 4w
       section Phase 3: Integration
       External Integrations :c1, after b2, 3w
       API Development       :c2, after b2, 3w
       section Phase 4: Polish
       UI/UX Refinement      :d1, after c1, 2w
       Performance Tuning    :d2, after c2, 2w
       section Phase 5: Launch
       UAT                   :e1, after d1, 2w
       Production Deployment :e2, after e1, 1w
   ```

2. **Phase Descriptions**
   For each phase:
   - Phase name and duration
   - Objectives and deliverables
   - Key activities
   - Dependencies
   - Success criteria
   - Risks and mitigations

3. **Milestone Schedule**
   | Milestone | Target Date | Deliverables | Success Criteria |
   |-----------|-------------|--------------|------------------|
   | M1: Architecture Complete | Week 4 | Architecture docs, infra setup | All diagrams approved |

4. **Resource Allocation**
   Team structure and responsibilities per phase.

5. **Sprint Planning Overview**
   For $sprintCount sprints of $sprintDuration weeks each:
   - Sprint goals
   - Capacity planning
   - Key deliverables

6. **Release Plan**
   - Release versions and dates
   - Features per release
   - Release criteria

7. **Risk Timeline**
   When risks are highest and mitigation windows.

Provide detailed phase planning with realistic timelines.
                        """.trimIndent()
            )
          )

          phasePlanBuffer?.setLength(0)
          phasePlanBuffer?.append(
            buildString {
                          appendLine("## Phase Plan")
                          appendLine()
                          appendLine("✅ Phases planned")
                          appendLine()
                          appendLine(phasePlanAnalysis)
                        }.renderMarkdown(true)
          )
          phasePlanTask.update()
          transcriptStream?.write("## Phase Plan\n\n$phasePlanAnalysis\n\n---\n\n".toByteArray())

          // Extract milestones from phase plan
          extractMilestonesFromPhasePlan(phasePlanAnalysis, collectedMilestones)
          checklist["Phase Planning"] = "✅"
          updateChecklist()
        }

        // Section 8: Project Data JSON
        if (executionConfig.generate_project_data) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Generating Project Data...".renderMarkdown(true))
          log.debug("Generating project data JSON")
          val projectDataTask = tabs.newTask("Project Data")

          val projectDataBuffer =
            projectDataTask.add("## Project Data\n\n🔄 Generating structured project data...".renderMarkdown(true))
          task.update()
          val sprintCount = executionConfig.sprint_count
          val sprintDuration = executionConfig.sprint_duration_weeks

          // Generate detailed tasks and sprints
          val projectDataAnalysis = designAgent.answer(
            listOf(
              """
Generate a detailed breakdown of all project work items. For each item provide:

1. **Epics** (high-level features/initiatives)
   - ID (EPIC-XXX format)
   - Name
   - Description
   - Priority (Critical/High/Medium/Low)
   - Estimated story points

2. **User Stories/Tasks** (for each epic)
   - ID (TASK-XXX format)
   - Title
   - Description
   - Type (story/task/spike/bug)
   - Parent Epic ID
   - Priority
   - Story points (1, 2, 3, 5, 8, 13)
   - Acceptance criteria (list)
   - Labels/tags

3. **Sprint Assignments**
   Distribute tasks across $sprintCount sprints, each $sprintDuration weeks.
   Consider:
   - Dependencies between tasks
   - Team velocity (assume ~40 points per sprint)
   - Risk distribution

4. **Releases**
   - ID (REL-XXX format)
   - Version number
   - Target date
   - Included epics
   - Release notes summary

5. **Dependencies**
   List all dependencies between tasks, epics, and milestones.
   Format: SOURCE_ID blocks/depends_on/relates_to TARGET_ID

Provide comprehensive, realistic project breakdown.
                        """.trimIndent()
            )
          )

          // Parse the analysis and build structured data
          parseProjectDataFromAnalysis(
            projectDataAnalysis,
            collectedEpics,
            collectedTasks,
            collectedMilestones,
            collectedDependencies,
            sprintCount,
            sprintDuration
          )

          // Build sprints
          val sprints = buildSprints(collectedTasks, sprintCount, sprintDuration)

          // Build releases
          val releases = buildReleases(collectedEpics, sprints)

          // Create project data object
          val projectData = ProjectData(
            project_name = projectName,
            description = systemDescription,
            created_date = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
            epics = collectedEpics,
            releases = releases,
            sprints = sprints,
            tasks = collectedTasks,
            milestones = collectedMilestones,
            dependencies = collectedDependencies
          )

          // Write JSON file
          val jsonContent = JsonUtil.toJson(projectData)
          val jsonFileName = "${projectName.replace(" ", "_").lowercase()}_project_data.json"

          val jsonLink = task.saveFile(jsonFileName, jsonContent.toByteArray())
          projectDataBuffer?.setLength(0)
          projectDataBuffer?.append(
            buildString {
                          appendLine("## Project Data")
                          appendLine()
                          appendLine("✅ Project data generated")
                          appendLine()
                          appendLine("### Summary")
                          appendLine()
                          appendLine("| Category | Count |")
                          appendLine("|----------|-------|")
                          appendLine("| Epics | ${collectedEpics.size} |")
                          appendLine("| Tasks | ${collectedTasks.size} |")
                          appendLine("| Sprints | ${sprints.size} |")
                          appendLine("| Releases | ${releases.size} |")
                          appendLine("| Milestones | ${collectedMilestones.size} |")
                          appendLine("| Dependencies | ${collectedDependencies.size} |")
                          appendLine()
                          appendLine("### Download")
                          appendLine()
                          appendLine("📥 [Download Project Data JSON]($jsonLink)")
                          appendLine()
                          appendLine("### Preview")
                          appendLine()
                          appendLine("```json")
                          appendLine(jsonContent.take(2000))
                          if (jsonContent.length > 2000) appendLine("... (truncated)")
                          appendLine("```")
                        }.renderMarkdown(true)
          )
          projectDataTask.update()
          transcriptStream?.write(
            """
                    ## Project Data
                    Generated JSON file: $jsonFileName
                    <details>
                    <summary>Raw JSON Content</summary>
                    ```json
                    $jsonContent
                    ```
                    </details>
                """.trimIndent().toByteArray()
          )
          checklist["Project Data JSON"] = "✅"
          updateChecklist()
        }

        // Final summary
        val duration = System.currentTimeMillis() - startTime
        log.info("SoftwareDesignDocumentTask completed: project='$projectName', duration=${duration}ms")

        statusBuffer?.setLength(0)
        statusBuffer?.append(
          buildString {
                      appendLine("---")
                      appendLine("### ✅ Document Generation Complete")
                      appendLine("**Total Time:** ${duration / 1000.0}s | **Tasks:** ${collectedTasks.size} | **Epics:** ${collectedEpics.size}")
                    }.renderMarkdown(true)
        )
        overviewTask.complete()
        task.update()

        val relativePath = "${
          projectName.replace(" ", "_").lowercase()
        }_design_document_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (transcriptLink, _) = Pair(task.linkTo(relativePath), task.resolveUserFile(relativePath))
        task.safeComplete(
          "Software design document generated in ${duration / 1000}s. " +
              "View document: <a href='$transcriptLink' target='_blank'>markdown</a> " +
              "<a href='${transcriptLink.removeSuffix(".md")}.html' target='_blank'>html</a> " +
              "<a href='${transcriptLink.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>",
          log
        )

        val finalResult = buildString {
          appendLine("# Software Design Document: $projectName")
          appendLine()
          appendLine("**System:** ${systemDescription.truncateForDisplay(500)}")
          appendLine()
          appendLine("## Generated Sections")
          if (executionConfig.generate_use_cases) appendLine("- ✅ Use Cases & Actors")
          if (executionConfig.generate_requirements) appendLine("- ✅ Requirements Specification")
          if (executionConfig.generate_architecture) appendLine("- ✅ Architecture Diagrams")
          if (executionConfig.generate_data_model) appendLine("- ✅ Data Model & ERD")
          if (executionConfig.generate_flow_diagrams) appendLine("- ✅ Flow Diagrams")
          if (executionConfig.generate_test_plan) appendLine("- ✅ Test Plan")
          if (executionConfig.generate_phase_plan) appendLine("- ✅ Phase Planning")
          if (executionConfig.generate_project_data) appendLine("- ✅ Project Data JSON (${collectedTasks.size} tasks, ${collectedEpics.size} epics)")
        }
        resultFn(finalResult)

      } catch (e: Exception) {
        task.error(e)
        log.error("SoftwareDesignDocumentTask failed for project: $projectName", e)
        transcriptStream?.write(
          """
                ## Error Occurred
                **Error:** ${e.message}
                **Type:** ${e.javaClass.simpleName}
                <details>
                <summary>Stack Trace</summary>
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent().toByteArray()
        )

        overviewTask.add(
          buildString {
                      appendLine("## ❌ Error Occurred")
                      appendLine()
                      appendLine("**Error:** ${e.message}")
                      appendLine()
                      appendLine("**Type:** ${e.javaClass.simpleName}")
                    }.renderMarkdown(true)
        )
        overviewTask.update()
        task.update()

        resultFn("Error generating software design document: ${e.message}")
      } finally {
        transcriptStream?.flush()
        transcriptStream?.close()
      }
    }
  }

  private fun buildDesignPrompt(
    projectName: String,
    systemDescription: String,
    targetAudience: String,
    stakeholders: List<String> = emptyList(),
    techStack: List<String> = emptyList(),
    constraints: List<String> = emptyList(),
    priorContext: String,
    inputFileContext: String
  ): String {
    return """
You are an expert software architect and technical writer. Your role is to create comprehensive software design documentation with detailed Mermaid diagrams.

## Project: $projectName

## System Description:
$systemDescription

## Target Audience:
$targetAudience

${if (stakeholders.isNotEmpty()) "## Stakeholders:\n${stakeholders.joinToString("\n") { "- $it" }}\n" else ""}

${if (techStack.isNotEmpty()) "## Technology Stack:\n${techStack.joinToString("\n") { "- $it" }}\n" else ""}

${if (constraints.isNotEmpty()) "## Constraints:\n${constraints.joinToString("\n") { "- $it" }}\n" else ""}

${if (priorContext.isNotBlank()) "## Context from Prior Tasks:\n$priorContext\n" else ""}

${if (inputFileContext.isNotBlank()) "## Input Files:\n$inputFileContext\n" else ""}

## Documentation Standards:
1. Use clear, professional technical writing
2. Include Mermaid diagrams for all visual representations
3. Use consistent ID formats (FR-XXX, UC-XXX, TC-XXX, etc.)
4. Provide actionable, specific details
5. Consider scalability, security, and maintainability
6. Include traceability between artifacts
7. Make all acceptance criteria testable

## Mermaid Diagram Types to Use:
- `graph TD/LR` for flowcharts and architecture
- `sequenceDiagram` for interactions
- `erDiagram` for data models
- `stateDiagram-v2` for state machines
- `gantt` for timelines
- `classDiagram` for class structures

Provide comprehensive, production-ready documentation.
        """.trimIndent()
  }

  private fun extractEpicsFromUseCases(analysis: String, epics: MutableList<Epic>) {
    // Extract use case groups as epics
    val ucPattern = "UC-\\d+".toRegex()
    val matches = ucPattern.findAll(analysis)
    val ucCount = matches.count()

    if (ucCount > 0) {
      epics.add(
        Epic(
          id = "EPIC-UC",
          name = "User Features",
          description = "Core user-facing functionality based on use cases",
          priority = "High",
          story_points = ucCount * 5
        )
      )
    }
  }

  private fun extractMilestonesFromPhasePlan(analysis: String, milestones: MutableList<Milestone>) {
    val milestonePattern = "M\\d+:?\\s*([^|\\n]+)".toRegex()
    val matches = milestonePattern.findAll(analysis)

    matches.forEachIndexed { index, match ->
      milestones.add(
        Milestone(
          id = "MS-${index + 1}",
          name = match.groupValues[1].trim().take(50),
          target_date = LocalDateTime.now().plusWeeks((index + 1) * 4L).format(DateTimeFormatter.ISO_DATE),
          description = "Project milestone ${index + 1}",
          deliverables = listOf("Phase ${index + 1} deliverables complete")
        )
      )
    }
  }

  private fun parseProjectDataFromAnalysis(
    analysis: String,
    epics: MutableList<Epic>,
    tasks: MutableList<Task>,
    milestones: MutableList<Milestone>,
    dependencies: MutableList<Dependency>,
    sprintCount: Int,
    sprintDuration: Int
  ) {
    // Parse EPIC patterns
    val epicPattern = "EPIC-(\\w+)".toRegex()
    epicPattern.findAll(analysis).forEach { match ->
      val epicId = match.value
      if (epics.none { it.id == epicId }) {
        epics.add(
          Epic(
            id = epicId,
            name = "Epic $epicId",
            description = "Auto-extracted epic from analysis",
            priority = "Medium",
            story_points = 13
          )
        )
      }
    }

    // Parse TASK patterns
    val taskPattern = "TASK-(\\d+)".toRegex()
    taskPattern.findAll(analysis).forEach { match ->
      val taskId = match.value
      if (tasks.none { it.id == taskId }) {
        tasks.add(
          Task(
            id = taskId,
            title = "Task $taskId",
            description = "Auto-extracted task from analysis",
            type = "task",
            epic_id = epics.firstOrNull()?.id,
            sprint_id = null,
            priority = "Medium",
            story_points = 3,
            acceptance_criteria = listOf("Task completed successfully"),
            labels = listOf("auto-generated")
          )
        )
      }
    }

    // Parse dependency patterns
    val depPattern = "(TASK-\\d+|EPIC-\\w+)\\s+(blocks|depends_on|relates_to)\\s+(TASK-\\d+|EPIC-\\w+)".toRegex()
    depPattern.findAll(analysis).forEachIndexed { index, match ->
      dependencies.add(
        Dependency(
          id = "DEP-${index + 1}",
          source_id = match.groupValues[1],
          source_type = if (match.groupValues[1].startsWith("EPIC")) "epic" else "task",
          target_id = match.groupValues[3],
          target_type = if (match.groupValues[3].startsWith("EPIC")) "epic" else "task",
          dependency_type = match.groupValues[2]
        )
      )
    }

    // Ensure minimum tasks if none extracted
    if (tasks.isEmpty()) {
      val defaultTasks = listOf(
        "Project Setup", "Architecture Design", "Database Schema",
        "API Development", "Frontend Development", "Integration Testing",
        "Documentation", "Deployment Setup"
      )
      defaultTasks.forEachIndexed { index, title ->
        tasks.add(
          Task(
            id = "TASK-${index + 1}",
            title = title,
            description = "Default task: $title",
            type = "task",
            epic_id = epics.firstOrNull()?.id,
            sprint_id = null,
            priority = if (index < 3) "High" else "Medium",
            story_points = listOf(2, 3, 5, 8).random(),
            acceptance_criteria = listOf("$title completed"),
            labels = listOf("default")
          )
        )
      }
    }
  }

  private fun buildSprints(tasks: MutableList<Task>, sprintCount: Int, sprintDuration: Int): List<Sprint> {
    val sprints = mutableListOf<Sprint>()
    val tasksPerSprint = (tasks.size / sprintCount).coerceAtLeast(1)
    var taskIndex = 0
    val startDate = LocalDateTime.now()

    for (i in 1..sprintCount) {
      val sprintTasks = mutableListOf<String>()
      var sprintPoints = 0

      while (taskIndex < tasks.size && sprintPoints < 40 && sprintTasks.size < tasksPerSprint + 2) {
        val task = tasks[taskIndex]
        task.sprint_id = "SPRINT-$i"
        sprintTasks.add(task.id)
        sprintPoints += task.story_points ?: 3
        taskIndex++
      }

      val sprintStart = startDate.plusWeeks(((i - 1) * sprintDuration).toLong())
      val sprintEnd = sprintStart.plusWeeks(sprintDuration.toLong())

      sprints.add(
        Sprint(
          id = "SPRINT-$i",
          name = "Sprint $i",
          number = i,
          start_date = sprintStart.format(DateTimeFormatter.ISO_DATE),
          end_date = sprintEnd.format(DateTimeFormatter.ISO_DATE),
          goals = listOf("Complete sprint $i deliverables"),
          capacity_points = 40,
          task_ids = sprintTasks
        )
      )
    }

    return sprints
  }

  private fun buildReleases(epics: List<Epic>, sprints: List<Sprint>): List<Release> {
    val releases = mutableListOf<Release>()
    val sprintsPerRelease = (sprints.size / 2).coerceAtLeast(1)

    releases.add(
      Release(
        id = "REL-1",
        name = "MVP Release",
        version = "1.0.0",
        target_date = sprints.getOrNull(sprintsPerRelease - 1)?.end_date
          ?: LocalDateTime.now().plusMonths(2).format(DateTimeFormatter.ISO_DATE),
        description = "Minimum Viable Product release with core functionality",
        epic_ids = epics.take(epics.size / 2).map { it.id }
      )
    )

    if (sprints.size > sprintsPerRelease) {
      releases.add(
        Release(
          id = "REL-2",
          name = "Feature Complete Release",
          version = "1.1.0",
          target_date = sprints.lastOrNull()?.end_date
            ?: LocalDateTime.now().plusMonths(4).format(DateTimeFormatter.ISO_DATE),
          description = "Full feature release with all planned functionality",
          epic_ids = epics.map { it.id }
        )
      )
    }

    return releases
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
    .take(10) // Limit files
    .joinToString("\n\n") { relativePath ->
      val file = root.toFile().resolve(relativePath)
      try {
        val content = file.readText()
        "# $relativePath\n\n```\n${content.truncateForDisplay(1000)}\n```"
      } catch (e: Throwable) {
        log.warn("Error reading file: $relativePath", e)
        ""
      }
    }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(SoftwareDesignDocumentTask::class.java)
    @JvmStatic val SoftwareDesignDocument = TaskType(
        name = "SoftwareDesignDocument",
        category = "Writing",
        taskClass = SoftwareDesignDocumentTask::class.java,
        executionConfigClass = SoftwareDesignDocumentTaskExecutionConfigData::class.java,
        taskSettingsClass = TaskTypeConfig::class.java,
        description = "Generate comprehensive software design documentation",
        tooltipHtml = """
                        Creates complete software design documentation with Mermaid diagrams.
                        <ul>
                          <li>Use case diagrams and actor documentation</li>
                          <li>Functional and non-functional requirements</li>
                          <li>Architecture diagrams (C4, component, deployment)</li>
                          <li>Data model and ERD diagrams</li>
                          <li>Sequence and activity flow diagrams</li>
                          <li>Test plan and test case documentation</li>
                          <li>Phase planning with Gantt charts</li>
                          <li>Project data JSON with tasks, epics, sprints, releases</li>
                          <li>All diagrams use Mermaid syntax</li>
                        </ul>
                      """,
    )
  }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/TechnicalExplanationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TechnicalExplanationTask(
  orchestrationConfig: OrchestrationConfig, planTask: TechnicalExplanationTaskExecutionConfigData?
) : AbstractTask<TechnicalExplanationTask.TechnicalExplanationTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig, planTask
) {

  class TechnicalExplanationTaskExecutionConfigData @JvmOverloads constructor(
    @Description("The complex technical subject to explain") var topic: String? = null,

    @Description("Target audience expertise level (e.g., 'layperson', 'beginner', 'intermediate', 'expert', 'manager', 'software_engineer', 'data_scientist')") var target_audience: String = "intermediate",

    @Description("Level of detail for the explanation (e.g., 'high_level_overview', 'moderate_detail', 'detailed_walkthrough', 'comprehensive')") var level_of_detail: String = "moderate_detail",

    @Description("Whether to include code examples and snippets") var include_code_examples: Boolean = true,

    @Description("Explanation format (e.g., 'markdown', 'q_and_a', 'step_by_step', 'narrative', 'tutorial')") var explanation_format: String = "markdown",

    @Description("Whether to generate analogies and metaphors") var use_analogies: Boolean = true,

    @Description("Whether to include visual descriptions or diagrams") var include_visual_descriptions: Boolean = true,

    @Description("Whether to define key terminology") var define_terminology: Boolean = true,

    @Description("Whether to include practical examples and use cases") var include_examples: Boolean = true,

    @Description("Whether to provide comparison with related concepts") var include_comparisons: Boolean = true,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task") var input_files: List<String>? = null,


    @Description("Programming language for code examples (if applicable)") var code_language: String? = null,

    @Description("Number of revision passes for clarity improvement") var revision_passes: Int = 1,

    @Description("Related files or documentation to reference") var related_files: List<String>? = null,

    task_description: String? = null,
    task_dependencies: MutableList<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = "TechnicalExplanation",
    task_description = task_description ?: "Generate technical explanation for: '$topic'",
    task_dependencies = task_dependencies,
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (topic.isNullOrBlank()) {
        return "topic must not be null or blank"
      }
      val validAudiences = setOf(
        "layperson", "beginner", "intermediate", "expert", "manager", "software_engineer", "data_scientist", "student"
      )
      if (target_audience.lowercase() !in validAudiences) {
        return "target_audience must be one of: ${validAudiences.joinToString(", ")}, got: $target_audience"
      }
      val validDetailLevels = setOf(
        "high_level_overview", "moderate_detail", "detailed_walkthrough", "comprehensive"
      )
      if (level_of_detail.lowercase() !in validDetailLevels) {
        return "level_of_detail must be one of: ${validDetailLevels.joinToString(", ")}, got: $level_of_detail"
      }
      val validFormats = setOf("markdown", "q_and_a", "step_by_step", "narrative", "tutorial")
      if (explanation_format.lowercase() !in validFormats) {
        return "explanation_format must be one of: ${validFormats.joinToString(", ")}, got: $explanation_format"
      }
      if (revision_passes < 0 || revision_passes > 5) {
        return "revision_passes must be between 0 and 5, got: $revision_passes"
      }
      if (!input_files.isNullOrEmpty()) {
        input_files?.forEach { file ->
          if (file.isBlank()) {
            return "input_files must not contain blank entries"
          }
        }
      }
      return ValidatedObject.validateFields(this)
    }
  }

  class ExplanationOutline(
    @Description("The main topic title") var title: String = "",
    @Description("Brief overview of what will be explained") var overview: String = "",
    @Description("Key concepts to cover in order") var key_concepts: List<ConceptOutline> = emptyList(),
    @Description("Core terminology that needs definition") var terminology: List<TermDefinition> = emptyList(),
    @Description("Analogies to use for complex concepts") var analogies: List<AnalogyMapping> = emptyList(),
    @Description("Code examples to include") var code_examples: List<CodeExampleOutline> = emptyList(),
    @Description("Visual descriptions or diagrams needed") var visual_descriptions: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) return "title must not be blank"
      if (overview.isBlank()) return "overview must not be blank"
      if (key_concepts.isEmpty()) return "key_concepts must not be empty"
      return ValidatedObject.validateFields(this)
    }
  }

  class ConceptOutline(
    @Description("Concept name") var concept: String = "",
    @Description("Why this concept matters") var importance: String = "",
    @Description("Sub-topics to cover") var subtopics: List<String> = emptyList(),
    @Description("Complexity level") var complexity: String = "",
    @Description("Estimated explanation length") var estimated_paragraphs: Int = 0
  ) : ValidatedObject

  class TermDefinition(
    @Description("The technical term") var term: String = "",
    @Description("Simple definition") var definition: String = "",
    @Description("Context where it's used") var context: String = ""
  ) : ValidatedObject

  class AnalogyMapping(
    @Description("The technical concept") var technical_concept: String = "",
    @Description("The relatable analogy") var analogy: String = "",
    @Description("How they map to each other") var mapping_explanation: String = ""
  ) : ValidatedObject

  class CodeExampleOutline(
    @Description("What the code demonstrates") var purpose: String = "",
    @Description("Programming language") var language: String = "",
    @Description("Complexity level") var complexity: String = "",
    @Description("Key points to highlight") var key_points: List<String> = emptyList()
  ) : ValidatedObject

  class ExplanationSection(
    @Description("Section title") var title: String = "",
    @Description("Section content") var content: String = "",
    @Description("Code snippets in this section") var code_snippets: List<CodeSnippet> = emptyList(),
    @Description("Key takeaways") var key_takeaways: List<String> = emptyList()
  ) : ValidatedObject

  class CodeSnippet(
    @Description("Programming language") var language: String = "",
    @Description("The code") var code: String = "",
    @Description("Explanation of the code") var explanation: String = "",
    @Description("Key points highlighted") var highlights: List<String> = emptyList()
  ) : ValidatedObject

  override fun promptSegment(): String {
    return """
TechnicalExplanation - Break down complex technical subjects into clear, digestible explanations
  ** Specify the technical topic to explain
  ** Define target audience expertise level
  ** Set level of detail (overview to comprehensive)
  ** Configure explanation format (markdown, Q&A, step-by-step, etc.)
  ** Enable analogies and metaphors for clarity
  ** Include code examples with explanations
  ** Define key terminology
  ** Provide visual descriptions
  ** Include practical examples and use cases
  ** Compare with related concepts
  ** Performs outline creation, content generation, and iterative refinement
  ** Produces clear, audience-appropriate technical explanations
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {


    val tabs = TabbedDisplay(task)
    // Overview tab
    val overviewTask = tabs.newTask("Overview")

    val transcript = task.transcript()
      val resultBuilder = StringBuilder()
    task.ui.pool.submit {
      try {
        val startTime = System.currentTimeMillis()
        val config = executionConfig ?: throw RuntimeException("No configuration provided")

        // Validate configuration
        config.validate()?.let { validationError ->
          val msg = "Configuration validation failed: $validationError"
          log.error(msg)
          task.error(ValidatedObject.ValidationError(validationError, config))
          transcript?.write("## Configuration Error\n$msg\n".toByteArray(StandardCharsets.UTF_8))
          task.complete(msg.renderMarkdown(true))
          resultFn("CONFIGURATION ERROR: $validationError")
          return@submit
        }

        val topic = config.topic
        if (topic.isNullOrBlank()) {
          val msg = "No topic specified for technical explanation"
          log.error(msg)
          task.complete(msg.renderMarkdown(true))
          resultFn("CONFIGURATION ERROR: No topic specified")
          return@submit
        }

        val api = defaultSmart ?: throw RuntimeException("No smart model configured")
        log.info("Starting TechnicalExplanationTask for topic: '$topic'")
        val userMessages = messages.filter { it.isNotBlank() }

        // Load input files if specified
        val inputFileContent = getInputFileCode()
        if (inputFileContent.isNotBlank()) {
          log.info("Loaded input files for context")
          val inputFilesTask = tabs.newTask("Input Files")
          inputFilesTask.add(
            buildString {
                          appendLine("# Input Files")
                          appendLine()
                          appendLine(inputFileContent.truncateForDisplay(3000))
                          appendLine()
                        }.renderMarkdown(true)
          )
          transcript?.write(
            """
                |# Input Files
                |<details>
                |<summary>Raw Input Content</summary>
                |
                |$inputFileContent
                |</details>
            """.trimMargin().toByteArray(StandardCharsets.UTF_8)
          )
          inputFilesTask.update()
        }
        // Include user messages in context
        if (userMessages.isNotEmpty()) {
          log.info("Including ${userMessages.size} user message(s) in context")
        }

        val overviewContent = buildString {
          appendLine("# Technical Explanation Generation")
          appendLine()
          appendLine("**Topic:** $topic")
          appendLine()
          appendLine("## Configuration")
          appendLine()
          if (userMessages.isNotEmpty()) {
            appendLine("### User Input")
            appendLine()
            userMessages.forEach { message ->
              appendLine(message)
              appendLine()
            }
            appendLine("---")
            appendLine()
          }
        }
        overviewTask.add(overviewContent.renderMarkdown(true))
        transcript?.write(overviewContent.toByteArray(StandardCharsets.UTF_8))

        val configContent = buildString {
          appendLine("- Target Audience: ${config.target_audience}")
          appendLine("- Level of Detail: ${config.level_of_detail}")
          appendLine("- Format: ${config.explanation_format}")
          appendLine("- Include Code Examples: ${if (config.include_code_examples) "✓" else "✗"}")
          appendLine("- Use Analogies: ${if (config.use_analogies) "✓" else "✗"}")
          appendLine("- Define Terminology: ${if (config.define_terminology) "✓" else "✗"}")
          appendLine("- Include Visual Descriptions: ${if (config.include_visual_descriptions) "✓" else "✗"}")
          appendLine("- Include Examples: ${if (config.include_examples) "✓" else "✗"}")
          appendLine("- Include Comparisons: ${if (config.include_comparisons) "✓" else "✗"}")
          if (config.code_language != null) {
            appendLine("- Code Language: ${config.code_language}")
          }
          appendLine()
          appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
          appendLine()
          appendLine("---")
          appendLine()
        }
        overviewTask.add(configContent.renderMarkdown(true))
        transcript?.write(configContent.toByteArray(StandardCharsets.UTF_8))

        val phase1Content = buildString {
          appendLine("### Phase 1: Analysis & Outline")
          appendLine("*Analyzing topic and creating explanation structure...*")
        }
        overviewTask.add(phase1Content.renderMarkdown(true))
        transcript?.write(phase1Content.toByteArray(StandardCharsets.UTF_8))
        overviewTask.update()
        resultBuilder.append("# Technical Explanation: $topic\n\n")

        // Gather context
        val priorContext = getPriorCode(agent.executionState)
        val contextFiles = getContextFiles()

        if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
          log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
          val contextTask = tabs.newTask("Reference Context")
          contextTask.add(
            buildString {
                          appendLine("# Reference Context")
                          appendLine()
                          if (priorContext.isNotBlank()) {
                            appendLine("## Prior Context")
                            appendLine(priorContext.truncateForDisplay(2000))
                            appendLine()
                          }
                          if (contextFiles.isNotBlank()) {
                            appendLine("## Related Files")
                            appendLine(contextFiles.truncateForDisplay(2000))
                          }
                        }.renderMarkdown(true)
          )
          contextTask.update()
          transcript?.write(
            """
                    |## Reference Context
                    |<details>
                    |<summary>Prior Context and Related Files</summary>
                    |
                    |### Prior Context
                    |$priorContext
                    |
                    |### Related Files
                    |$contextFiles
                    |</details>
                """.trimMargin().toByteArray(StandardCharsets.UTF_8)
          )
        }

        // Phase 1: Create outline
        log.info("Phase 1: Creating explanation outline")
        val outlineTask = tabs.newTask("Outline")

        outlineTask.add(
          buildString {
                      appendLine("# Explanation Outline")
                      appendLine()
                      appendLine("**Status:** Creating structured outline...")
                      appendLine()
                    }.renderMarkdown(true)
        )
        transcript?.write("\n# Explanation Outline\n\n".toByteArray(StandardCharsets.UTF_8))
        transcript?.write("**Status:** Creating structured outline...\n\n".toByteArray(StandardCharsets.UTF_8))
        outlineTask.update()

        val audienceGuidance = when (config.target_audience.lowercase()) {
          "layperson" -> "Assume no technical background. Use everyday language and avoid jargon."
          "beginner" -> "Assume basic familiarity with technology but limited domain knowledge."
          "intermediate" -> "Assume solid foundation in the field with some practical experience."
          "expert" -> "Assume deep technical knowledge. Focus on nuances and advanced concepts."
          "manager" -> "Focus on high-level concepts, business value, and practical implications."
          "software_engineer" -> "Assume programming knowledge. Include implementation details."
          "data_scientist" -> "Assume statistical and algorithmic knowledge. Include mathematical concepts."
          else -> "Adjust language to match the audience's technical level."
        }

        val detailGuidance = when (config.level_of_detail.lowercase()) {
          "high_level_overview" -> "Provide a bird's-eye view. Focus on the 'what' and 'why'."
          "moderate_detail" -> "Balance overview with key details. Cover 'what', 'why', and 'how' at a moderate depth."
          "detailed_walkthrough" -> "Provide comprehensive coverage with step-by-step explanations."
          "comprehensive" -> "Cover all aspects thoroughly, including edge cases and advanced topics."
          else -> "Provide moderate detail with clear explanations."
        }

        val outlineAgent = ParsedAgent(
          resultClass = ExplanationOutline::class.java, prompt = """
You are an expert technical educator and communicator. Create a detailed outline for explaining this topic.

Topic: $topic

Target Audience: ${config.target_audience}
Audience Guidance: $audienceGuidance

Level of Detail: ${config.level_of_detail}
Detail Guidance: $detailGuidance

Format: ${config.explanation_format}

${if (priorContext.isNotBlank()) "Reference Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Additional Documentation:\n${contextFiles.truncateForDisplay(3000)}\n" else ""}

Create an outline that includes:
1. A clear, engaging title
2. Brief overview (2-3 sentences) of what will be explained
3. 3-6 key concepts to cover, ordered logically (simple to complex or general to specific)
4. ${if (config.define_terminology) "5-10 essential terms that need definition" else "Key terminology (minimal)"}
5. ${if (config.use_analogies) "2-4 analogies to make complex concepts relatable" else "Analogies (if absolutely necessary)"}
6. ${if (config.include_code_examples) "3-5 code examples to illustrate concepts" else "Code examples (minimal or none)"}
7. ${if (config.include_visual_descriptions) "Descriptions of diagrams or visualizations that would help" else "Visual aids (if critical)"}

For each key concept, specify:
- The concept name
- Why it's important to understand
- Sub-topics to cover
- Complexity level (basic, intermediate, advanced)
- Estimated paragraphs needed

Ensure the outline:
- Builds understanding progressively
- Matches the ${config.target_audience} audience level
- Provides ${config.level_of_detail} level of detail
- Follows ${config.explanation_format} format conventions
          """.trimIndent(), model = api, temperature = 0.6, parsingChatter = defaultFast
        )

        var outline = outlineAgent.answer(listOf("Generate outline")).obj

        // Validate outline
        outline.validate()?.let { validationError ->
          log.error("Outline validation failed: $validationError")
          outlineTask.error(ValidatedObject.ValidationError(validationError, outline))
          task.complete("Outline validation failed: $validationError".renderMarkdown(true))
          resultFn("CONFIGURATION ERROR: Outline validation failed: $validationError")
          return@submit
        }

        log.info("Generated outline: ${outline.key_concepts.size} concepts, ${outline.terminology.size} terms, ${outline.analogies.size} analogies")

        val outlineContent = buildString {
          appendLine("## ${outline.title}")
          appendLine()
          appendLine("### Overview")
          appendLine(outline.overview)
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("### Key Concepts")
          outline.key_concepts.forEachIndexed { index, concept ->
            appendLine("#### ${index + 1}. ${concept.concept}")
            appendLine()
            appendLine("**Importance:** ${concept.importance}")
            appendLine()
            appendLine("**Complexity:** ${concept.complexity}")
            appendLine()
            if (concept.subtopics.isNotEmpty()) {
              appendLine("**Subtopics:**")
              concept.subtopics.forEach { subtopic ->
                appendLine("- $subtopic")
              }
              appendLine()
            }
            appendLine("**Est. Paragraphs:** ${concept.estimated_paragraphs}")
            appendLine()
            appendLine("---")
            appendLine()
          }
          if (outline.terminology.isNotEmpty()) {
            appendLine("### Key Terminology")
            outline.terminology.forEach { term ->
              appendLine("**${term.term}:** ${term.definition}")
              if (term.context.isNotBlank()) {
                appendLine("  - *Context: ${term.context}*")
              }
              appendLine()
            }
            appendLine("---")
            appendLine()
          }
          if (outline.analogies.isNotEmpty()) {
            appendLine("### Analogies")
            outline.analogies.forEach { analogy ->
              appendLine("**${analogy.technical_concept}** ≈ ${analogy.analogy}")
              appendLine("  - ${analogy.mapping_explanation}")
              appendLine()
            }
            appendLine("---")
            appendLine()
          }
          if (outline.code_examples.isNotEmpty()) {
            appendLine("### Code Examples")
            outline.code_examples.forEachIndexed { index, example ->
              appendLine("${index + 1}. **${example.purpose}** (${example.language})")
              appendLine("   - Complexity: ${example.complexity}")
              if (example.key_points.isNotEmpty()) {
                appendLine("   - Key points: ${example.key_points.joinToString(", ")}")
              }
              appendLine()
            }
            appendLine("---")
            appendLine()
          }
          if (outline.visual_descriptions.isNotEmpty()) {
            appendLine("### Visual Aids")
            outline.visual_descriptions.forEach { visual ->
              appendLine("- $visual")
            }
            appendLine()
          }
          appendLine("**Status:** ✅ Complete")
        }
        outlineTask.add(outlineContent.renderMarkdown(true))
        outlineTask.update()
        transcript?.write(outlineContent.toByteArray(StandardCharsets.UTF_8))

        overviewTask.add("✅ Phase 1 Complete: Outline created\n".renderMarkdown(true))
        overviewTask.add("\n### Phase 2: Content Generation\n*Writing explanation sections...*\n".renderMarkdown(true))
        overviewTask.update()

        // Phase 2: Generate content for each concept
        log.info("Phase 2: Generating explanation content")
        val sections = mutableListOf<ExplanationSection>()

        outline.key_concepts.forEachIndexed { index, conceptOutline ->
          log.info("Writing section ${index + 1}/${outline.key_concepts.size}: ${conceptOutline.concept}")

          overviewTask.add(
            "- Section ${index + 1}: ${conceptOutline.concept.truncateForDisplay(50)} ".renderMarkdown(
              true
            )
          )
          overviewTask.update()

          val sectionTask = tabs.newTask("Section ${index + 1}")

          sectionTask.add(
            buildString {
                          appendLine("# ${conceptOutline.concept}")
                          appendLine()
                          appendLine("**Status:** Writing section...")
                          appendLine()
                        }.renderMarkdown(true)
          )
          transcript?.write("\n# ${conceptOutline.concept}\n\n".toByteArray(StandardCharsets.UTF_8))
          transcript?.write("**Status:** Writing section...\n\n".toByteArray(StandardCharsets.UTF_8))
          sectionTask.update()

          // Build context from previous sections
          val previousContext = if (sections.isNotEmpty()) {
            buildString {
              appendLine("## Previously Covered")
              sections.takeLast(2).forEach { prevSection ->
                appendLine("**${prevSection.title}:** ${prevSection.content.take(200)}...")
                appendLine()
              }
            }
          } else {
            "This is the first section."
          }

          // Find relevant analogies for this concept
          val relevantAnalogies = outline.analogies.filter {
            it.technical_concept.contains(conceptOutline.concept, ignoreCase = true) || conceptOutline.concept.contains(
              it.technical_concept,
              ignoreCase = true
            )
          }

          // Find relevant code examples
          val relevantCodeExamples = outline.code_examples.filter {
            it.purpose.contains(
              conceptOutline.concept,
              ignoreCase = true
            ) || conceptOutline.concept.contains(it.purpose, ignoreCase = true)
          }

          val sectionAgent = ParsedAgent(
            resultClass = ExplanationSection::class.java, prompt = """
You are an expert technical educator. Write a clear, engaging explanation of this concept.

Overall Topic: $topic
Target Audience: ${config.target_audience}
Audience Guidance: $audienceGuidance

Concept to Explain: ${conceptOutline.concept}
Importance: ${conceptOutline.importance}
Subtopics: ${conceptOutline.subtopics.joinToString(", ")}
Complexity: ${conceptOutline.complexity}

$previousContext

${
              if (relevantAnalogies.isNotEmpty()) {
                "Analogies to Use:\n${relevantAnalogies.joinToString("\n") { "- ${it.analogy}: ${it.mapping_explanation}" }}\n"
              } else ""
            }

${
              if (config.include_code_examples && relevantCodeExamples.isNotEmpty()) {
                "Code Examples to Include:\n${relevantCodeExamples.joinToString("\n") { "- ${it.purpose} (${it.language})" }}\n"
              } else ""
            }

Write a section that:
1. Opens with a clear introduction to the concept
2. ${if (config.use_analogies && relevantAnalogies.isNotEmpty()) "Uses the provided analogy to make it relatable" else "Explains clearly without jargon"}
3. Covers all subtopics: ${conceptOutline.subtopics.joinToString(", ")}
4. ${if (config.include_examples) "Includes practical examples or use cases" else "Focuses on conceptual understanding"}
5. ${if (config.include_code_examples) "Includes code snippets with clear explanations" else "Avoids code unless absolutely necessary"}
6. ${if (config.include_visual_descriptions) "Describes visual representations that would help" else "Uses text-based explanations"}
7. Provides 2-4 key takeaways at the end
8. Transitions smoothly to the next concept

Make it:
- Clear and accessible to ${executionConfig.target_audience}
- Engaging and well-structured
- Approximately ${conceptOutline.estimated_paragraphs} paragraphs
- Following ${config.explanation_format} format

${
              if (config.include_code_examples) {
                "For code snippets, provide:\n- The code in ${config.code_language ?: "appropriate language"}\n- Line-by-line or block explanation\n- Key points to highlight\n"
              } else ""
            }
          """.trimIndent(), model = api, temperature = 0.7, parsingChatter = defaultFast
          )

          var section = sectionAgent.answer(listOf("Write section")).obj
          sections.add(section)

          val sectionContent = buildString {
            appendLine("## ${section.title}")
            appendLine()
            appendLine(section.content)
            appendLine()
            if (section.code_snippets.isNotEmpty()) {
              appendLine("---")
              appendLine()
              appendLine("### Code Examples")
              appendLine()
              section.code_snippets.forEach { snippet ->
                appendLine("**${snippet.explanation}**")
                appendLine()
                appendLine("```${snippet.language}")
                appendLine(snippet.code)
                appendLine("```")
                appendLine()
                if (snippet.highlights.isNotEmpty()) {
                  appendLine("**Key Points:**")
                  snippet.highlights.forEach { highlight ->
                    appendLine("- $highlight")
                  }
                  appendLine()
                }
              }
            }
            if (section.key_takeaways.isNotEmpty()) {
              appendLine("---")
              appendLine()
              appendLine("### Key Takeaways")
              section.key_takeaways.forEach { takeaway ->
                appendLine("- $takeaway")
              }
              appendLine()
            }
            appendLine("**Status:** ✅ Complete")
          }
          sectionTask.add(sectionContent.renderMarkdown(true))
          sectionTask.update()
          transcript?.write(sectionContent.toByteArray(StandardCharsets.UTF_8))

          resultBuilder.append("## ${section.title}\n\n")
          resultBuilder.append(section.content)
          resultBuilder.append("\n\n")

          if (section.code_snippets.isNotEmpty()) {
            section.code_snippets.forEach { snippet ->
              resultBuilder.append("```${snippet.language}\n")
              resultBuilder.append(snippet.code)
              resultBuilder.append("\n```\n\n")
              resultBuilder.append("*${snippet.explanation}*\n\n")
            }
          }

          overviewTask.add("✅\n".renderMarkdown(true))
          overviewTask.update()
        }

        overviewTask.add("✅ Phase 2 Complete: All sections written\n".renderMarkdown(true))

        // Phase 3: Add comparisons if enabled
        if (config.include_comparisons) {
          overviewTask.add(
            "\n### Phase 3: Comparisons\n*Adding comparisons with related concepts...*\n".renderMarkdown(
              true
            )
          )
          overviewTask.update()

          log.info("Phase 3: Generating comparisons")
          val comparisonTask = tabs.newTask("Comparisons")

          comparisonTask.add(
            buildString {
                          appendLine("# Comparisons")
                          appendLine()
                          appendLine("**Status:** Comparing with related concepts...")
                          appendLine()
                        }.renderMarkdown(true)
          )
          transcript?.write("\n# Comparisons\n\n".toByteArray(StandardCharsets.UTF_8))
          transcript?.write(
            "**Status:** Comparing with related concepts...\n\n".toByteArray(
              StandardCharsets.UTF_8
            )
          )
          comparisonTask.update()

          val comparisonAgent = ChatAgent(
            prompt = """
You are an expert technical educator. Compare and contrast this topic with related concepts.

Topic: $topic
Target Audience: ${config.target_audience}

Content Covered:
${sections.joinToString("\n") { "- ${it.title}" }}

Provide comparisons that:
1. Identify 2-3 related or commonly confused concepts
2. Explain key similarities
3. Highlight important differences
4. Clarify when to use each
5. Help readers understand the boundaries and relationships

Make comparisons clear and helpful for ${config.target_audience}.
          """.trimIndent(), model = api, temperature = 0.6
          )

          val comparisons = comparisonAgent.answer(listOf("Generate comparisons"))

          comparisonTask.add(
            buildString {
                          appendLine("## Related Concepts")
                          appendLine()
                          appendLine(comparisons)
                          appendLine()
                          appendLine("**Status:** ✅ Complete")
                        }.renderMarkdown(true)
          )
          comparisonTask.update()
          transcript?.write("\n## Related Concepts\n\n${comparisons}\n\n".toByteArray(StandardCharsets.UTF_8))

          resultBuilder.append("## Comparisons with Related Concepts\n\n")
          resultBuilder.append(comparisons)
          resultBuilder.append("\n\n")

          overviewTask.add("✅ Phase 3 Complete: Comparisons added\n".renderMarkdown(true))
        }

        // Phase 4: Revision (if enabled)
        if (config.revision_passes > 0) {
          overviewTask.add("\n### Phase 4: Revision\n*Refining for clarity...*\n".renderMarkdown(true))
          overviewTask.update()

          log.info("Phase 4: Performing ${config.revision_passes} revision pass(es)")
          val revisionTask = tabs.newTask("Revision")

          revisionTask.add(
            buildString {
                          appendLine("# Revision Process")
                          appendLine()
                          appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
                          appendLine()
                        }.renderMarkdown(true)
          )
          transcript?.write("\n# Revision Process\n\n".toByteArray(StandardCharsets.UTF_8))
          transcript?.write(
            "**Status:** Performing ${config.revision_passes} revision pass(es)...\n\n".toByteArray(
              StandardCharsets.UTF_8
            )
          )
          revisionTask.update()

          val fullExplanation = resultBuilder.toString()

          repeat(config.revision_passes) { passNum ->
            log.debug("Revision pass ${passNum + 1}/${config.revision_passes}")

            val revisionAgent = ChatAgent(
              prompt = """
You are an expert technical editor. Review and improve this explanation for clarity and effectiveness.

Current Explanation:
$fullExplanation

Target Audience: ${config.target_audience}
Level of Detail: ${config.level_of_detail}

Focus on:
1. Clarity and simplicity of language
2. Logical flow and transitions
3. Effectiveness of analogies and examples
4. Accuracy of technical content
5. Appropriateness for ${config.target_audience}
6. Completeness of coverage
7. Engagement and readability

Maintain:
- All key concepts and information
- Code examples and their explanations
- Technical accuracy
- Approximate length
- ${config.explanation_format} format

Provide the complete revised explanation.
            """.trimIndent(), model = api, temperature = 0.5
            )

            val revisedExplanation = revisionAgent.answer(listOf("Revise the explanation"))
            resultBuilder.clear()
            resultBuilder.append(revisedExplanation)

            revisionTask.add(
              buildString {
                              appendLine("## Revision Pass ${passNum + 1}")
                              appendLine()
                              appendLine("✅ Complete")
                              appendLine()
                            }.renderMarkdown(true)
            )
            revisionTask.update()
            transcript?.write(
              "\n## Revision Pass ${passNum + 1}\n\n✅ Complete\n\n".toByteArray(
                StandardCharsets.UTF_8
              )
            )
          }

          overviewTask.add(
            "✅ Phase 4 Complete: ${config.revision_passes} revision pass(es) completed\n".renderMarkdown(
              true
            )
          )
        }

        // Phase 5: Final Assembly
        overviewTask.add("\n### Phase 5: Final Assembly\n*Compiling complete explanation...*\n".renderMarkdown(true))
        overviewTask.update()

        log.info("Phase 5: Assembling final explanation")
        val finalTask = tabs.newTask("Complete Explanation")

        val finalExplanation = buildString {
          appendLine("# ${outline.title}")
          appendLine()
          appendLine("> *Explanation for: ${config.target_audience}*")
          appendLine()
          appendLine("## Overview")
          appendLine()
          appendLine(outline.overview)
          appendLine()
          if (outline.terminology.isNotEmpty() && config.define_terminology) {
            appendLine("---")
            appendLine()
            appendLine("## Key Terminology")
            appendLine()
            outline.terminology.forEach { term ->
              appendLine("**${term.term}:** ${term.definition}")
              appendLine()
            }
          }
          appendLine("---")
          appendLine()
          appendLine(resultBuilder.toString())
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## Summary")
          appendLine()
          appendLine("This explanation covered:")
          sections.forEach { section ->
            appendLine("- **${section.title}**")
            if (section.key_takeaways.isNotEmpty()) {
              section.key_takeaways.forEach { takeaway ->
                appendLine("  - ${takeaway.truncateForDisplay(100)}")
              }
            }
          }
        }

        finalTask.add(finalExplanation.renderMarkdown(true))
        finalTask.update()
        transcript?.write(
          """
                |## Final Explanation
                |<details>
                |<summary>Full Content</summary>
                |
                |$finalExplanation
                |</details>
            """.trimMargin().toByteArray(StandardCharsets.UTF_8)
        )

        // Final statistics
        val totalTime = System.currentTimeMillis() - startTime
        val wordCount = finalExplanation.split("\\s+".toRegex()).size
        val codeExampleCount = sections.sumOf { it.code_snippets.size }

        val statsContent = buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ✅ Generation Complete")
          appendLine()
          appendLine("**Statistics:**")
          appendLine("- Sections: ${sections.size}")
          appendLine("- Word Count: $wordCount")
          appendLine("- Code Examples: $codeExampleCount")
          appendLine("- Analogies Used: ${outline.analogies.size}")
          appendLine("- Terms Defined: ${outline.terminology.size}")
          appendLine("- Revision Passes: ${config.revision_passes}")
          appendLine("- Total Time: ${totalTime / 1000.0}s")
          appendLine()
          appendLine(
            "**Completed:** ${
              LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }"
          )
        }
        overviewTask.add(
          statsContent.renderMarkdown(true)
        )
        transcript?.write(statsContent.toByteArray(StandardCharsets.UTF_8))
        overviewTask.update()

        // Concise summary for resultFn
        val finalResult = """
                |# Technical Explanation: ${outline.title}
                |
                |Generated a $wordCount-word explanation for a **${config.target_audience}** audience.
                |The explanation covers ${sections.size} key concepts with $codeExampleCount code examples and ${outline.analogies.size} analogies.
                |
                |*Full content is available in the task UI.*""".trimMargin()

        log.info("TechnicalExplanationTask completed: sections=${sections.size}, words=$wordCount, time=${totalTime}ms")


        task.complete(
          "Technical explanation generation complete: $wordCount words in ${totalTime / 1000}s".renderMarkdown(
            true
          )
        )
        resultFn(finalResult)

      } catch (e: Exception) {
        log.error("Error during technical explanation generation", e)
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
        overviewTask.update()
        transcript?.write(
          """
                |## Error
                |<details>
                |<summary>Stack Trace</summary>
                |
                |```
                |${e.stackTraceToString()}
                |```
                |</details>
                """.trimMargin().toByteArray(StandardCharsets.UTF_8)
        )

        val errorOutput = buildString {
          appendLine("# Error in Technical Explanation Generation")
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
      } finally {
        transcript?.close()
      }
    }
  }

  private fun getInputFileCode() = (executionConfig?.input_files ?: listOf()).flatMap { pattern: String ->
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
  }.distinct().sortedBy { it }.joinToString("\n\n") { relativePath ->
    val file = root.toFile().resolve(relativePath)
    try {
      val content = file.readText()
      "# $relativePath\n\n```\n$content\n```"
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
      appendLine("## Related Documentation Files")
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


  companion object {
    private val log: Logger = LoggerFactory.getLogger(TechnicalExplanationTask::class.java)
    @JvmStatic val TechnicalExplanation = TaskType(
        name = "TechnicalExplanation",
        category = "Writing",
        taskClass = TechnicalExplanationTask::class.java,
        executionConfigClass = TechnicalExplanationTaskExecutionConfigData::class.java,
        taskSettingsClass = TaskTypeConfig::class.java,
        description = "Break down complex technical subjects into clear, digestible explanations",
        tooltipHtml = """
                        Generates clear, audience-appropriate explanations of complex technical topics.
                        <ul>
                          <li>Creates structured outline with key concepts and terminology</li>
                          <li>Adjusts language and depth for target audience (layperson to expert)</li>
                          <li>Generates relatable analogies and metaphors</li>
                          <li>Includes code examples with detailed explanations</li>
                          <li>Defines essential terminology in context</li>
                          <li>Provides visual descriptions and diagrams</li>
                          <li>Includes practical examples and use cases</li>
                          <li>Compares with related concepts for clarity</li>
                          <li>Supports multiple formats (markdown, Q&A, step-by-step, tutorial)</li>
                          <li>Optional revision passes for clarity improvement</li>
                          <li>Ideal for documentation, onboarding, education, and knowledge sharing</li>
                        </ul>
                      """,
    )
  }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/TutorialGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.writing

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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class TutorialGenerationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: TutorialGenerationTaskExecutionConfigData?
) : AbstractTask<TutorialGenerationTask.TutorialGenerationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class TutorialGenerationTaskExecutionConfigData(
        @Description("The final outcome the user should achieve (e.g., 'deploy a web app to the cloud', 'train a simple machine learning model')")
        var goal: String? = null,

        @Description("The environment the tutorial is for (e.g., 'Windows', 'Linux', 'macOS', 'VS Code', 'Docker')")
        var target_platform: String = "cross-platform",

        @Description("Whether to add placeholders like '[Screenshot of the successful output]' where visuals would be needed")
        var include_screenshots_placeholders: Boolean = true,

        @Description("Controls how much explanatory text is included with each step ('concise', 'detailed', 'verbose')")
        var verbosity: String = "detailed",

        @Description("Whether to add a common errors and troubleshooting section")
        var include_troubleshooting: Boolean = true,

        @Description("Target audience skill level (e.g., 'beginner', 'intermediate', 'advanced')")
        var skill_level: String = "beginner",

        @Description("Estimated time to complete the tutorial in minutes")
        var estimated_duration: Int = 30,

        @Description("Whether to include code examples and commands")
        var include_code_examples: Boolean = true,

        @Description("Whether to include validation steps to verify success")
        var include_validation_steps: Boolean = true,

        @Description("Whether to include a 'What You'll Learn' section")
        var include_learning_objectives: Boolean = true,

        @Description("Whether to include a 'Next Steps' section for further learning")
        var include_next_steps: Boolean = true,

        @Description("Number of main steps to break the tutorial into")
        var target_step_count: Int = 7,

        @Description("Related files or documentation to reference")
        var related_files: List<String>? = null,
        @Description("Optional input files to use as context (supports glob patterns, e.g. **/*.kt)")
        var input_files: List<String>? = null,


        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = TutorialGeneration.name,
        task_description = task_description ?: "Generate tutorial for: '$goal'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (goal.isNullOrBlank()) {
                return "goal must not be null or blank"
            }
            if (estimated_duration <= 0) {
                return "estimated_duration must be positive, got: $estimated_duration"
            }
            if (target_step_count < 3 || target_step_count > 20) {
                return "target_step_count must be between 3 and 20, got: $target_step_count"
            }
            if (verbosity.isBlank()) {
                return "verbosity must not be blank"
            }
            if (skill_level.isBlank()) {
                return "skill_level must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class TutorialOutline(
        @Description("Tutorial title")
        val title: String = "",
        @Description("Brief description of what the tutorial covers")
        val description: String = "",
        @Description("Learning objectives")
        val learning_objectives: List<String> = emptyList(),
        @Description("Prerequisites (tools, software, prior knowledge)")
        val prerequisites: List<Prerequisite> = emptyList(),
        @Description("Main tutorial steps")
        val steps: List<TutorialStepOutline> = emptyList(),
        @Description("Estimated completion time in minutes")
        val estimated_time: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (description.isBlank()) return "description must not be blank"
            if (steps.isEmpty()) return "steps must not be empty"
            if (estimated_time <= 0) return "estimated_time must be positive"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Prerequisite(
        @Description("Type of prerequisite (e.g., 'software', 'knowledge', 'account', 'hardware')")
        val type: String = "",
        @Description("Name of the prerequisite")
        val name: String = "",
        @Description("Description or installation instructions")
        val description: String = "",
        @Description("Whether this is required or optional")
        val required: Boolean = true,
        @Description("Link to download or learn more")
        val link: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (name.isBlank()) return "prerequisite name must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class TutorialStepOutline(
        @Description("Step number")
        val step_number: Int = 1,
        @Description("Step title")
        val title: String = "",
        @Description("What this step accomplishes")
        val purpose: String = "",
        @Description("Key actions to perform")
        val actions: List<String> = emptyList(),
        @Description("Whether this step includes code or commands")
        val has_code: Boolean = false,
        @Description("Whether this step needs a screenshot placeholder")
        val needs_screenshot: Boolean = false,
        @Description("Expected outcome or result")
        val expected_outcome: String = "",
        @Description("Estimated time for this step in minutes")
        val estimated_time: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "step title must not be blank"
            if (step_number <= 0) return "step_number must be positive"
            return ValidatedObject.validateFields(this)
        }
    }

    data class TutorialStep(
        @Description("Step number")
        val step_number: Int = 1,
        @Description("Step title")
        val title: String = "",
        @Description("Detailed explanation")
        val explanation: String = "",
        @Description("Commands or code to execute")
        val code_blocks: List<CodeBlock> = emptyList(),
        @Description("Expected outcome description")
        val expected_outcome: String = "",
        @Description("Validation steps to verify success")
        val validation_steps: List<String> = emptyList(),
        @Description("Screenshot placeholder locations")
        val screenshot_placeholders: List<String> = emptyList(),
        @Description("Common issues for this step")
        val common_issues: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "step title must not be blank"
            if (explanation.isBlank()) return "step explanation must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class CodeBlock(
        @Description("Programming language or shell type")
        val language: String = "",
        @Description("The code or command")
        val code: String = "",
        @Description("Brief description of what this code does")
        val description: String = "",
        @Description("Whether this should be run in a specific directory")
        val working_directory: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (code.isBlank()) return "code must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class TroubleshootingSection(
        @Description("Common problems and solutions")
        val issues: List<TroubleshootingIssue> = emptyList()
    ) : ValidatedObject

    data class TroubleshootingIssue(
        @Description("The problem or error")
        val problem: String = "",
        @Description("Symptoms or error messages")
        val symptoms: List<String> = emptyList(),
        @Description("Possible causes")
        val causes: List<String> = emptyList(),
        @Description("Solutions to try")
        val solutions: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (problem.isBlank()) return "problem must not be blank"
            if (solutions.isEmpty()) return "solutions must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class NextSteps(
        @Description("Suggestions for further learning")
        val suggestions: List<String> = emptyList(),
        @Description("Related tutorials or resources")
        val related_resources: List<String> = emptyList(),
        @Description("Advanced topics to explore")
        val advanced_topics: List<String> = emptyList()
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
TutorialGeneration - Create complete, step-by-step tutorials for processes and projects
  ** Specify the goal or final outcome to achieve
  ** Define target platform and environment
  ** Set skill level and estimated duration
  ** Enable screenshot placeholders for visual guidance
  ** Configure verbosity level (concise, detailed, verbose)
  ** Include code examples and commands
  ** Add validation steps to verify success
  ** Include troubleshooting section for common errors
  ** Add learning objectives and next steps
  ** Produces publication-ready tutorial with clear, actionable steps
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        task.ui.pool.submit {
            val startTime = System.currentTimeMillis()
            log.info("Starting TutorialGenerationTask for goal: '${executionConfig?.goal}'")
            val transcript = task.transcript()

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            resultFn("CONFIGURATION ERROR: $validationError")
            return@submit
        }

        val goal = executionConfig?.goal
        if (goal.isNullOrBlank()) {
            log.error("No goal specified for tutorial generation")
            task.safeComplete("CONFIGURATION ERROR: No goal specified", log)
            resultFn("CONFIGURATION ERROR: No goal specified")
            return@submit
        }

            val api = defaultSmart ?: return@submit
            try {

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")
        transcript?.write("# Tutorial Generation Transcript\n\n".toByteArray())
        transcript?.write("**Goal:** $goal\n\n".toByteArray())
        transcript?.write(
            "**Started:** ${
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }\n\n".toByteArray()
        )
        transcript?.write("---\n\n".toByteArray())


        val overviewContent = buildString {
            appendLine("# Tutorial Generation")
            appendLine()
            appendLine("**Goal:** $goal")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Target Platform: ${executionConfig.target_platform}")
            appendLine("- Skill Level: ${executionConfig.skill_level}")
            appendLine("- Estimated Duration: ${executionConfig.estimated_duration} minutes")
            appendLine("- Verbosity: ${executionConfig.verbosity}")
            appendLine("- Target Steps: ${executionConfig.target_step_count}")
            appendLine("- Include Code Examples: ${if (executionConfig.include_code_examples) "✓" else "✗"}")
            appendLine("- Include Screenshots: ${if (executionConfig.include_screenshots_placeholders) "✓" else "✗"}")
            appendLine("- Include Validation: ${if (executionConfig.include_validation_steps) "✓" else "✗"}")
            appendLine("- Include Troubleshooting: ${if (executionConfig.include_troubleshooting) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine("**Input Messages:** ${messages.size}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Planning & Outline")
            appendLine("*Creating tutorial structure...*")
        }
        overviewTask.add(overviewContent.renderMarkdown(true))
        overviewTask.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Tutorial: $goal\n\n")

        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState)
            val contextFiles = getInputFileContent(executionConfig.related_files, root)
            val inputFileContent = getInputFileContent(executionConfig.input_files, root)
            // Combine all context
            buildString {
                if (contextFiles.isNotBlank()) appendLine(contextFiles)
            }

            if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                val contextTask = tabs.newTask("Context")
                contextTask.add(
                  buildString {
                    appendLine("# Context & Resources")
                    appendLine()
                    appendLine("<details><summary>Raw Context Data</summary>")
                    if (inputFileContent.isNotBlank()) {
                      appendLine("## Input Files")
                      appendLine(inputFileContent)
                      appendLine()
                    }
                    if (priorContext.isNotBlank()) {
                      appendLine("## Prior Context")
                      appendLine(priorContext)
                      appendLine()
                    }
                    appendLine("</details>")
                  }.renderMarkdown(true)
                )
                contextTask.update()
                contextTask.complete()
            }

            // Phase 1: Create outline
            log.info("Phase 1: Creating tutorial outline")
            val outlineTask = tabs.newTask("Outline")
            transcript?.write("## Phase 1: Planning & Outline\n\n".toByteArray())
            transcript?.write("Creating tutorial structure...\n\n".toByteArray())
            transcript?.write("**Configuration:**\n".toByteArray())
            transcript?.write("- Target Steps: ${executionConfig.target_step_count}\n".toByteArray())
            transcript?.write("- Skill Level: ${executionConfig.skill_level}\n\n".toByteArray())

            outlineTask.add(
              buildString {
                appendLine("# Tutorial Outline")
                appendLine()
                appendLine("**Status:** Creating structured outline...")
                appendLine()
              }.renderMarkdown(true)
            )
            outlineTask.update()

            val outlineAgent = ParsedAgent(
                resultClass = TutorialOutline::class.java,
                prompt = """
You are an expert technical writer and educator. Create a detailed outline for a tutorial.

Goal: $goal

Target Platform: ${executionConfig.target_platform}
Skill Level: ${executionConfig.skill_level}
Estimated Duration: ${executionConfig.estimated_duration} minutes
Target Step Count: ${executionConfig.target_step_count}

${if (inputFileContent.isNotBlank()) "Input Files:\n${inputFileContent.truncateForDisplay(3000)}\n" else ""}
${if (messages.isNotEmpty()) "User Messages:\n${messages.joinToString("\n").truncateForDisplay(2000)}\n" else ""}
${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Related Files:\n${contextFiles.truncateForDisplay(3000)}\n" else ""}

Create an outline with:
1. A clear, descriptive title
2. Brief description of what the tutorial covers
3. ${if (executionConfig.include_learning_objectives) "3-5 specific learning objectives" else ""}
4. Complete list of prerequisites:
   - Required software and tools (with versions if relevant)
   - Prior knowledge or skills needed
   - Accounts or services required
   - Hardware requirements if applicable
5. ${executionConfig.target_step_count} main steps that:
   - Follow a logical progression
   - Are appropriately sized (not too large or small)
   - Build on previous steps
   - Lead to the stated goal
   - Include time estimates

For each step, specify:
- Clear, action-oriented title
- Purpose (what this step accomplishes)
- Key actions to perform
- Whether it includes code/commands
- Whether it needs a screenshot
- Expected outcome
- Estimated time (total should be ~${executionConfig.estimated_duration} minutes)

Ensure the outline:
- Is appropriate for ${executionConfig.skill_level} level
- Works on ${executionConfig.target_platform}
- Follows best practices for technical tutorials
- Has a clear beginning, middle, and end
          """.trimIndent(),
                model = api,
                temperature = 0.6,
                parsingChatter = defaultFast
            )

            var outline = outlineAgent.answer(listOf("Generate outline")).obj

            // Validate outline
            outline.validate()?.let { validationError ->
                log.error("Outline validation failed: $validationError")
                outlineTask.error(ValidatedObject.ValidationError(validationError, outline))
                task.safeComplete("Outline validation failed: $validationError", log)
                resultFn("ERROR: Outline validation failed: $validationError")
                return@submit
            }

            log.info("Generated outline: ${outline.steps.size} steps, ${outline.prerequisites.size} prerequisites")
            transcript?.write("### Outline Generated\n\n".toByteArray())
            transcript?.write("**Title:** ${outline.title}\n\n".toByteArray())
            transcript?.write("**Steps:** ${outline.steps.size}\n\n".toByteArray())
            transcript?.write("**Prerequisites:** ${outline.prerequisites.size}\n\n".toByteArray())
            transcript?.write("**Estimated Time:** ${outline.estimated_time} minutes\n\n".toByteArray())
            transcript?.write("---\n\n".toByteArray())


            val outlineContent = buildString {
                appendLine("## ${outline.title}")
                appendLine()
                appendLine(outline.description)
                appendLine()
                appendLine("**Estimated Time:** ${outline.estimated_time} minutes")
                appendLine()
                appendLine("---")
                appendLine()
                if (outline.learning_objectives.isNotEmpty()) {
                    appendLine("### What You'll Learn")
                    outline.learning_objectives.forEach { objective ->
                        appendLine("- $objective")
                    }
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
                appendLine("### Prerequisites")
                appendLine()
                val requiredPrereqs = outline.prerequisites.filter { it.required }
                val optionalPrereqs = outline.prerequisites.filter { !it.required }

                if (requiredPrereqs.isNotEmpty()) {
                    appendLine("#### Required")
                    requiredPrereqs.forEach { prereq ->
                        appendLine("**${prereq.name}** (${prereq.type})")
                        appendLine()
                        appendLine(prereq.description)
                        if (prereq.link != null) {
                            appendLine()
                            appendLine("Download: ${prereq.link}")
                        }
                        appendLine()
                    }
                }

                if (optionalPrereqs.isNotEmpty()) {
                    appendLine("#### Optional")
                    optionalPrereqs.forEach { prereq ->
                        appendLine("**${prereq.name}** (${prereq.type})")
                        appendLine()
                        appendLine(prereq.description)
                        if (prereq.link != null) {
                            appendLine()
                            appendLine("Download: ${prereq.link}")
                        }
                        appendLine()
                    }
                }
                appendLine("---")
                appendLine()
                appendLine("### Tutorial Steps")
                outline.steps.forEach { step ->
                    appendLine("#### Step ${step.step_number}: ${step.title}")
                    appendLine()
                    appendLine("**Purpose:** ${step.purpose}")
                    appendLine()
                    appendLine("**Actions:**")
                    step.actions.forEach { action ->
                        appendLine("- $action")
                    }
                    appendLine()
                    if (step.has_code) {
                        appendLine("*Includes code/commands*")
                        appendLine()
                    }
                    if (step.needs_screenshot) {
                        appendLine("*Screenshot needed*")
                        appendLine()
                    }
                    appendLine("**Expected Outcome:** ${step.expected_outcome}")
                    appendLine()
                    appendLine("**Time:** ~${step.estimated_time} min")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }
            outlineTask.add(outlineContent.renderMarkdown(true))
            outlineTask.update()
            outlineTask.complete()

            overviewTask.add("✅ Phase 1 Complete: Outline created (${outline.steps.size} steps)\n".renderMarkdown(true))
            overviewTask.add(
              "\n### Phase 2: Writing Steps\n*Developing detailed step-by-step instructions...*\n".renderMarkdown(
                true
              )
            )
            overviewTask.update()
            transcript?.write("## Phase 2: Writing Steps\n\n".toByteArray())
            transcript?.write("Input Context:\n".toByteArray())
            if (messages.isNotEmpty()) {
                transcript?.write("**Messages:** ${messages.size} items\n".toByteArray())
                messages.forEach { msg ->
                    transcript?.write("- ${msg.truncateForDisplay(100)}\n".toByteArray())
                }
                transcript?.write("\n".toByteArray())
            }
            if (inputFileContent.isNotBlank()) {
                transcript?.write("**Input Files Loaded:** ${inputFileContent.length} characters\n\n".toByteArray())
            }
            transcript?.write("Developing detailed step-by-step instructions...\n\n".toByteArray())


            // Phase 2: Write each step
            log.info("Phase 2: Writing detailed steps")
            val tutorialSteps = mutableListOf<TutorialStep>()

            outline.steps.forEachIndexed { index, stepOutline ->
                log.info("Writing step ${index + 1}/${outline.steps.size}: ${stepOutline.title}")

                overviewTask.add("- Step ${index + 1}: ${stepOutline.title.truncateForDisplay(50)} ".renderMarkdown(true))
                overviewTask.update()
                transcript?.write("### Step ${index + 1}: ${stepOutline.title}\n\n".toByteArray())
                transcript?.write("Writing detailed instructions...\n\n".toByteArray())


                val stepTask = tabs.newTask("Step ${index + 1}")

                stepTask.add(
                  buildString {
                    appendLine("# Step ${index + 1}: ${stepOutline.title}")
                    appendLine()
                    appendLine("**Status:** Writing detailed instructions...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                stepTask.update()

                // Build context from previous steps
                val previousStepsContext = if (tutorialSteps.isNotEmpty()) {
                    buildString {
                        appendLine("## Previous Steps Summary")
                        tutorialSteps.takeLast(2).forEach { prevStep ->
                            appendLine("**Step ${prevStep.step_number}:** ${prevStep.title}")
                            appendLine("Outcome: ${prevStep.expected_outcome}")
                            appendLine()
                        }
                    }
                } else {
                    "This is the first step."
                }

                val stepAgent = ParsedAgent(
                    resultClass = TutorialStep::class.java,
                    prompt = """
You are an expert technical writer. Write detailed instructions for this tutorial step.

Overall Goal: $goal
Target Platform: ${executionConfig.target_platform}
Skill Level: ${executionConfig.skill_level}
Verbosity: ${executionConfig.verbosity}

Step to Write:
Number: ${stepOutline.step_number}
Title: ${stepOutline.title}
Purpose: ${stepOutline.purpose}
Actions: ${stepOutline.actions.joinToString("; ")}
Expected Outcome: ${stepOutline.expected_outcome}

$previousStepsContext

Write a complete step with:
1. Clear, ${executionConfig.verbosity} explanation of what to do and why
2. ${if (executionConfig.include_code_examples && stepOutline.has_code) "Exact commands or code to execute (with language/shell specified)" else ""}
3. ${if (executionConfig.include_screenshots_placeholders && stepOutline.needs_screenshot) "Screenshot placeholders where visual confirmation is helpful" else ""}
4. Description of expected outcome (what the user should see)
5. ${if (executionConfig.include_validation_steps) "Validation steps to verify success" else ""}
6. ${if (executionConfig.include_troubleshooting) "Common issues that might occur in this step" else ""}

Guidelines:
- Use ${executionConfig.verbosity} level of detail
- Write for ${executionConfig.skill_level} skill level
- Be specific about ${executionConfig.target_platform} requirements
- Use clear, imperative language ("Click...", "Run...", "Open...")
- Include exact file paths, commands, and values
- Explain technical terms if needed for skill level
- Number sub-steps if there are multiple actions
          """.trimIndent(),
                    model = api,
                    temperature = 0.5,
                    parsingChatter = defaultFast
                )

                var tutorialStep = stepAgent.answer(listOf("Write step")).obj
                tutorialSteps.add(tutorialStep)
                transcript?.write("**Completed:** ${tutorialStep.title}\n".toByteArray())
                transcript?.write("- Code blocks: ${tutorialStep.code_blocks.size}\n".toByteArray())
                transcript?.write("- Validation steps: ${tutorialStep.validation_steps.size}\n\n".toByteArray())


                val stepContent = buildString {
                    appendLine("## Step ${tutorialStep.step_number}: ${tutorialStep.title}")
                    appendLine()
                    appendLine(tutorialStep.explanation)
                    appendLine()

                    if (tutorialStep.code_blocks.isNotEmpty()) {
                        appendLine("### Commands/Code")
                        appendLine()
                        tutorialStep.code_blocks.forEach { codeBlock ->
                            if (codeBlock.description.isNotBlank()) {
                                appendLine(codeBlock.description)
                                appendLine()
                            }
                            if (codeBlock.working_directory != null) {
                                appendLine("*Run in directory: `${codeBlock.working_directory}`*")
                                appendLine()
                            }
                            appendLine("```${codeBlock.language}")
                            appendLine(codeBlock.code)
                            appendLine("```")
                            appendLine()
                        }
                    }

                    if (tutorialStep.screenshot_placeholders.isNotEmpty()) {
                        appendLine("### Visual Checkpoints")
                        tutorialStep.screenshot_placeholders.forEach { placeholder ->
                            appendLine("📸 $placeholder")
                            appendLine()
                        }
                    }

                    appendLine("### Expected Outcome")
                    appendLine(tutorialStep.expected_outcome)
                    appendLine()

                    if (tutorialStep.validation_steps.isNotEmpty()) {
                        appendLine("### Verify Success")
                        tutorialStep.validation_steps.forEachIndexed { idx, validation ->
                            appendLine("${idx + 1}. $validation")
                        }
                        appendLine()
                    }

                    if (tutorialStep.common_issues.isNotEmpty()) {
                        appendLine("### Common Issues")
                        tutorialStep.common_issues.forEach { issue ->
                            appendLine("⚠️ $issue")
                            appendLine()
                        }
                    }

                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                stepTask.add(stepContent.renderMarkdown(true))
                stepTask.update()
                stepTask.complete()

                overviewTask.add("✅\n".renderMarkdown(true))
                overviewTask.update()
            }

            overviewTask.add("✅ Phase 2 Complete: All steps written\n".renderMarkdown(true))

            // Phase 3: Troubleshooting section (if enabled)
            var troubleshootingSection: TroubleshootingSection? = null
            if (executionConfig.include_troubleshooting) {
                overviewTask.add(
                  "\n### Phase 3: Troubleshooting\n*Compiling common issues and solutions...*\n".renderMarkdown(
                    true
                  )
                )
                overviewTask.update()
                transcript?.write("## Phase 3: Troubleshooting\n\n".toByteArray())
                transcript?.write("Compiling common issues and solutions...\n\n".toByteArray())


                log.info("Phase 3: Creating troubleshooting section")
                val troubleshootingTask = tabs.newTask("Troubleshooting")

                troubleshootingTask.add(
                  buildString {
                    appendLine("# Troubleshooting")
                    appendLine()
                    appendLine("**Status:** Identifying common problems...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                troubleshootingTask.update()

                val troubleshootingAgent = ParsedAgent(
                    resultClass = TroubleshootingSection::class.java,
                    prompt = """
You are an expert technical support specialist. Create a troubleshooting section for this tutorial.

Goal: $goal
Target Platform: ${executionConfig.target_platform}
Skill Level: ${executionConfig.skill_level}

Tutorial Steps Summary:
${tutorialSteps.joinToString("\n") { "Step ${it.step_number}: ${it.title}" }}

Identify 5-8 common problems users might encounter, including:
- Platform-specific issues
- Configuration errors
- Permission problems
- Version compatibility issues
- Common mistakes or misunderstandings
- Environment setup problems

For each issue, provide:
- Clear description of the problem
- Symptoms or error messages users might see
- Possible causes
- Step-by-step solutions (multiple if applicable)

Focus on issues that:
- Are likely to occur for ${executionConfig.skill_level} users
- Are specific to ${executionConfig.target_platform}
- Have clear, actionable solutions
- Aren't already covered in step-specific troubleshooting
          """.trimIndent(),
                    model = api,
                    temperature = 0.5,
                    parsingChatter = defaultFast
                )

                troubleshootingSection = troubleshootingAgent.answer(listOf("Create troubleshooting")).obj
                transcript?.write("**Troubleshooting Issues Identified:** ${troubleshootingSection.issues.size}\n\n".toByteArray())
                transcript?.write("---\n\n".toByteArray())


                val troubleshootingContent = buildString {
                    appendLine("## Common Issues and Solutions")
                    appendLine()
                    if (troubleshootingSection.issues.isEmpty()) {
                        appendLine("No common issues identified. If you encounter problems, check:")
                        appendLine("- Prerequisites are correctly installed")
                        appendLine("- Commands are run in the correct directory")
                        appendLine("- Platform-specific requirements are met")
                    } else {
                        troubleshootingSection.issues.forEachIndexed { index, issue ->
                            appendLine("### ${index + 1}. ${issue.problem}")
                            appendLine()
                            if (issue.symptoms.isNotEmpty()) {
                                appendLine("**Symptoms:**")
                                issue.symptoms.forEach { symptom ->
                                    appendLine("- $symptom")
                                }
                                appendLine()
                            }
                            if (issue.causes.isNotEmpty()) {
                                appendLine("**Possible Causes:**")
                                issue.causes.forEach { cause ->
                                    appendLine("- $cause")
                                }
                                appendLine()
                            }
                            appendLine("**Solutions:**")
                            issue.solutions.forEachIndexed { solIdx, solution ->
                                appendLine("${solIdx + 1}. $solution")
                            }
                            appendLine()
                            if (index < troubleshootingSection.issues.size - 1) {
                                appendLine("---")
                                appendLine()
                            }
                        }
                    }
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                troubleshootingTask.add(troubleshootingContent.renderMarkdown(true))
                troubleshootingTask.update()
                troubleshootingTask.complete()

                overviewTask.add("✅ Phase 3 Complete: Troubleshooting section added\n".renderMarkdown(true))
            }

            // Phase 4: Next Steps (if enabled)
            var nextSteps: NextSteps? = null
            if (executionConfig.include_next_steps) {
                overviewTask.add(
                  "\n### Phase 4: Next Steps\n*Suggesting further learning paths...*\n".renderMarkdown(
                    true
                  )
                )
                overviewTask.update()
                transcript?.write("## Phase 4: Next Steps\n\n".toByteArray())
                transcript?.write("Suggesting further learning paths...\n\n".toByteArray())


                log.info("Phase 4: Creating next steps section")
                val nextStepsTask = tabs.newTask("Next Steps")

                nextStepsTask.add(
                  buildString {
                    appendLine("# Next Steps")
                    appendLine()
                    appendLine("**Status:** Generating recommendations...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                nextStepsTask.update()

                val nextStepsAgent = ParsedAgent(
                    resultClass = NextSteps::class.java,
                    prompt = """
You are an expert educator. Suggest next steps for learners who completed this tutorial.

Goal Achieved: $goal
Skill Level: ${executionConfig.skill_level}

Provide:
1. 3-5 suggestions for what to try next or how to extend what they learned
2. 3-5 related tutorials, documentation, or resources
3. 3-5 advanced topics to explore

Make suggestions:
- Progressive (build on what was learned)
- Appropriate for ${executionConfig.skill_level} moving to the next level
- Specific and actionable
- Include mix of practice, learning, and exploration
          """.trimIndent(),
                    model = api,
                    temperature = 0.6,
                    parsingChatter = defaultFast
                )

                nextSteps = nextStepsAgent.answer(listOf("Generate next steps")).obj
                transcript?.write("**Next Steps Generated:**\n".toByteArray())
                transcript?.write("- Suggestions: ${nextSteps.suggestions.size}\n".toByteArray())
                transcript?.write("- Resources: ${nextSteps.related_resources.size}\n\n".toByteArray())
                transcript?.write("---\n\n".toByteArray())


                val nextStepsContent = buildString {
                    appendLine("## What's Next?")
                    appendLine()
                    appendLine("Congratulations on completing this tutorial! Here are some ways to continue your learning:")
                    appendLine()
                    if (nextSteps.suggestions.isNotEmpty()) {
                        appendLine("### Try These Next")
                        nextSteps.suggestions.forEach { suggestion ->
                            appendLine("- $suggestion")
                        }
                        appendLine()
                    }
                    if (nextSteps.related_resources.isNotEmpty()) {
                        appendLine("### Related Resources")
                        nextSteps.related_resources.forEach { resource ->
                            appendLine("- $resource")
                        }
                        appendLine()
                    }
                    if (nextSteps.advanced_topics.isNotEmpty()) {
                        appendLine("### Advanced Topics")
                        nextSteps.advanced_topics.forEach { topic ->
                            appendLine("- $topic")
                        }
                        appendLine()
                    }
                    appendLine("**Status:** ✅ Complete")
                }
                nextStepsTask.add(nextStepsContent.renderMarkdown(true))
                nextStepsTask.update()
                nextStepsTask.complete()

                overviewTask.add("✅ Phase 4 Complete: Next steps added\n".renderMarkdown(true))
            }

            // Phase 5: Final Assembly
            overviewTask.add("\n### Phase 5: Final Assembly\n*Compiling complete tutorial...*\n".renderMarkdown(true))
            overviewTask.update()
            transcript?.write("## Phase 5: Final Assembly\n\n".toByteArray())
            transcript?.write("Compiling complete tutorial...\n\n".toByteArray())


            log.info("Phase 5: Assembling final tutorial")
            val finalTask = tabs.newTask("Complete Tutorial")

            val finalTutorial = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine(outline.description)
                appendLine()
                appendLine("**⏱️ Estimated Time:** ${outline.estimated_time} minutes")
                appendLine()
                appendLine("**🎯 Skill Level:** ${executionConfig.skill_level.capitalize()}")
                appendLine()
                appendLine("**💻 Platform:** ${executionConfig.target_platform}")
                appendLine()
                appendLine("---")
                appendLine()

                if (outline.learning_objectives.isNotEmpty()) {
                    appendLine("## What You'll Learn")
                    appendLine()
                    outline.learning_objectives.forEach { objective ->
                        appendLine("✓ $objective")
                    }
                    appendLine()
                    appendLine("---")
                    appendLine()
                }

                appendLine("## Prerequisites")
                appendLine()
                val requiredPrereqs = outline.prerequisites.filter { it.required }
                val optionalPrereqs = outline.prerequisites.filter { !it.required }

                if (requiredPrereqs.isNotEmpty()) {
                    appendLine("### Required")
                    appendLine()
                    requiredPrereqs.forEach { prereq ->
                        appendLine("- **${prereq.name}** (${prereq.type}): ${prereq.description}")
                        if (prereq.link != null) {
                            appendLine("  - Download: ${prereq.link}")
                        }
                    }
                    appendLine()
                }

                if (optionalPrereqs.isNotEmpty()) {
                    appendLine("### Optional")
                    appendLine()
                    optionalPrereqs.forEach { prereq ->
                        appendLine("- **${prereq.name}** (${prereq.type}): ${prereq.description}")
                        if (prereq.link != null) {
                            appendLine("  - Download: ${prereq.link}")
                        }
                    }
                    appendLine()
                }

                appendLine("---")
                appendLine()
                appendLine("## Tutorial Steps")
                appendLine()

                tutorialSteps.forEach { step ->
                    appendLine("### Step ${step.step_number}: ${step.title}")
                    appendLine()
                    appendLine(step.explanation)
                    appendLine()

                    if (step.code_blocks.isNotEmpty()) {
                        step.code_blocks.forEach { codeBlock ->
                            if (codeBlock.description.isNotBlank()) {
                                appendLine(codeBlock.description)
                                appendLine()
                            }
                            if (codeBlock.working_directory != null) {
                                appendLine("*Run in: `${codeBlock.working_directory}`*")
                                appendLine()
                            }
                            appendLine("```${codeBlock.language}")
                            appendLine(codeBlock.code)
                            appendLine("```")
                            appendLine()
                        }
                    }

                    if (step.screenshot_placeholders.isNotEmpty()) {
                        step.screenshot_placeholders.forEach { placeholder ->
                            appendLine("📸 $placeholder")
                            appendLine()
                        }
                    }

                    appendLine("**Expected Outcome:** ${step.expected_outcome}")
                    appendLine()

                    if (step.validation_steps.isNotEmpty()) {
                        appendLine("**Verify Success:**")
                        step.validation_steps.forEachIndexed { idx, validation ->
                            appendLine("${idx + 1}. $validation")
                        }
                        appendLine()
                    }

                    if (step.common_issues.isNotEmpty()) {
                        appendLine("**⚠️ Common Issues:**")
                        step.common_issues.forEach { issue ->
                            appendLine("- $issue")
                        }
                        appendLine()
                    }

                    appendLine("---")
                    appendLine()
                }

                if (troubleshootingSection != null && troubleshootingSection.issues.isNotEmpty()) {
                    appendLine("## Troubleshooting")
                    appendLine()
                    troubleshootingSection.issues.forEachIndexed { index, issue ->
                        appendLine("### ${index + 1}. ${issue.problem}")
                        appendLine()
                        if (issue.symptoms.isNotEmpty()) {
                            appendLine("**Symptoms:**")
                            issue.symptoms.forEach { symptom ->
                                appendLine("- $symptom")
                            }
                            appendLine()
                        }
                        if (issue.causes.isNotEmpty()) {
                            appendLine("**Possible Causes:**")
                            issue.causes.forEach { cause ->
                                appendLine("- $cause")
                            }
                            appendLine()
                        }
                        appendLine("**Solutions:**")
                        issue.solutions.forEachIndexed { solIdx, solution ->
                            appendLine("${solIdx + 1}. $solution")
                        }
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }

                if (nextSteps != null) {
                    appendLine("## Next Steps")
                    appendLine()
                    appendLine("🎉 Congratulations on completing this tutorial!")
                    appendLine()
                    if (nextSteps.suggestions.isNotEmpty()) {
                        appendLine("### Try These Next")
                        nextSteps.suggestions.forEach { suggestion ->
                            appendLine("- $suggestion")
                        }
                        appendLine()
                    }
                    if (nextSteps.related_resources.isNotEmpty()) {
                        appendLine("### Related Resources")
                        nextSteps.related_resources.forEach { resource ->
                            appendLine("- $resource")
                        }
                        appendLine()
                    }
                    if (nextSteps.advanced_topics.isNotEmpty()) {
                        appendLine("### Advanced Topics")
                        nextSteps.advanced_topics.forEach { topic ->
                            appendLine("- $topic")
                        }
                        appendLine()
                    }
                }
            }

            finalTask.add(finalTutorial.renderMarkdown(true))
            val tutorialUrl = task.saveFile("tutorial.md", finalTutorial.toByteArray(Charsets.UTF_8))
            finalTask.update()
            finalTask.complete()

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime
            val totalWords = finalTutorial.split("\\s+".toRegex()).size
            transcript?.write("## Generation Complete\n\n".toByteArray())
            transcript?.write("**Statistics:**\n".toByteArray())
            transcript?.write("- Total Steps: ${tutorialSteps.size}\n".toByteArray())
            transcript?.write("- Prerequisites: ${outline.prerequisites.size}\n".toByteArray())
            transcript?.write("- Word Count: $totalWords\n".toByteArray())
            transcript?.write("- Code Blocks: ${tutorialSteps.sumOf { it.code_blocks.size }}\n".toByteArray())
            transcript?.write("- Total Time: ${totalTime / 1000.0}s\n\n".toByteArray())
            transcript?.write(
                "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )
            transcript?.flush()


            overviewTask.add(
              buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Generation Complete")
                appendLine()
                appendLine("**Output Files:**")
                appendLine("- [Complete Tutorial]($tutorialUrl)")
                appendLine("- [Transcript](transcript.md)")
                appendLine()
                appendLine("**Statistics:**")
                appendLine("- Total Steps: ${tutorialSteps.size}")
                appendLine("- Prerequisites: ${outline.prerequisites.size}")
                appendLine("- Estimated Duration: ${outline.estimated_time} minutes")
                appendLine("- Word Count: $totalWords")
                appendLine("- Code Blocks: ${tutorialSteps.sumOf { it.code_blocks.size }}")
                if (troubleshootingSection != null) {
                  appendLine("- Troubleshooting Issues: ${troubleshootingSection.issues.size}")
                }
                appendLine("- Total Time: ${totalTime / 1000.0}s")
                appendLine()
                appendLine(
                  "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                  }"
                )
              }.renderMarkdown(true)
            )
            overviewTask.update()
            overviewTask.complete()


            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# ✅ Tutorial Generated: ${outline.title}")
                appendLine()
                appendLine("A comprehensive tutorial with **${tutorialSteps.size} steps** was successfully generated.")
                appendLine()
                appendLine("## Summary")
                appendLine()
                appendLine("**Goal:** $goal")
                appendLine("**Platform:** ${executionConfig.target_platform}")
                appendLine("**Skill Level:** ${executionConfig.skill_level}")
                appendLine("**Estimated Duration:** ${outline.estimated_time} minutes")
                appendLine("**Key Features:**")
                appendLine("- ${outline.prerequisites.size} prerequisites identified")
                appendLine("- ${tutorialSteps.size} detailed steps with explanations")
                appendLine("- ${tutorialSteps.sumOf { it.code_blocks.size }} code examples")
                appendLine("- ${tutorialSteps.sumOf { it.validation_steps.size }} validation steps")
                appendLine("- Estimated completion time: ${outline.estimated_time} minutes")
                if (troubleshootingSection != null) {
                    appendLine("- ${troubleshootingSection.issues.size} troubleshooting scenarios")
                }
                appendLine()
                appendLine("## Output Files")
                appendLine()
                appendLine("- **Complete Tutorial:** [tutorial.md]($tutorialUrl)")
                appendLine("- **Transcript:** [transcript.md](transcript.md)")
                appendLine()
                appendLine("**Generation Time:** ${totalTime / 1000.0}s")
            }

            log.info("TutorialGenerationTask completed: steps=${tutorialSteps.size}, time=${totalTime}ms")

            task.safeComplete("Tutorial generation complete: ${tutorialSteps.size} steps in ${totalTime / 1000}s", log)
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during tutorial generation: ${e.message}")
            task.error(e)
            transcript?.write("\n## Error Occurred\n\n".toByteArray())
            transcript?.write("<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())

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
            overviewTask.update()
            task.update()

            val errorOutput = buildString {
                appendLine("# Error in Tutorial Generation")
                appendLine()
                appendLine("**Goal:** $goal")
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

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TutorialGenerationTask::class.java)
        @JvmStatic val TutorialGeneration = TaskType(
            name = "TutorialGeneration",
            category = "Writing",
            taskClass = TutorialGenerationTask::class.java,
            executionConfigClass = TutorialGenerationTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Create complete, step-by-step tutorials for processes and projects",
            tooltipHtml = """
                        Generates comprehensive tutorials with clear, actionable steps.
                        <ul>
                          <li>Creates detailed outline with prerequisites and learning objectives</li>
                          <li>Breaks process into logical, numbered steps</li>
                          <li>Generates exact commands and code examples</li>
                          <li>Includes expected outcomes and validation steps</li>
                          <li>Adds screenshot placeholders for visual guidance</li>
                          <li>Provides troubleshooting section for common issues</li>
                          <li>Suggests next steps for continued learning</li>
                          <li>Configurable verbosity and skill level</li>
                          <li>Platform-specific instructions and requirements</li>
                          <li>Ideal for how-to guides, educational content, and project-based learning</li>
                        </ul>
                      """,
        )
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here are the modifications for the README.md file in the writing tools directory.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/README.md
```markdown
# Writing Tools

This package contains a comprehensive suite of AI-powered tools designed for professional content creation, technical documentation, and creative storytelling. These tools leverage structured reasoning and iterative generation to produce high-quality, publication-ready outputs.

## Tools

### Article Generation
Generates complete journalistic articles by performing deep investigation and analysis.
- **Investigation**: Inherits from Journalism Reasoning to verify facts and identify perspectives.
- **Structure**: Creates detailed outlines including headlines, subheadlines, and ledes.
- **Formats**: Supports news, features, investigative reports, opinions, and profiles.
- **Social Media**: Can automatically generate snippets for Twitter, Facebook, and LinkedIn.

### Business Proposal
Creates professional business proposals with strategic depth.
- **Analysis**: Includes stakeholder analysis, ROI calculations, and risk assessments.
- **Competitive Edge**: Performs competitive analysis and identifies superiority statements.
- **Planning**: Generates detailed project timelines with milestones and resource requirements.
- **Customization**: Supports various types including investment, grant, and RFP responses.

### Comic Book Generation
Develops structured comic book scripts and optional AI-generated visuals.
- **Scripting**: Organizes content into pages, rows, and frames with dialogue and captions.
- **Visuals**: Generates character reference sheets and scene-by-scene illustrations.
- **Consistency**: Uses visual seeds and character profiles to maintain artistic continuity.

### Data Table Compilation
Compiles structured data tables from multiple unstructured source files.
- **Extraction**: Uses AI to identify rows and columns based on natural language instructions.
- **Formats**: Outputs data in JSON, CSV, or Markdown table formats.
- **Context**: Handles complex cross-referencing across multiple documents.

### Email Campaign
Generates multi-email marketing or outreach sequences with strategic pacing.
- **Strategy**: Develops progression logic and identifies audience pain points.
- **Optimization**: Creates A/B test variants for subject lines.
- **Personalization**: Includes placeholders for tokens like name and company.
- **Best Practices**: Provides implementation notes and recommended send intervals.

### Interactive Story
Creates branching "choose-your-own-adventure" narratives.
- **Logic**: Manages complex decision trees and prevents narrative dead ends.
- **State Tracking**: Can track variables like health, reputation, or inventory.
- **Replayability**: Optimizes for distinct experiences across multiple endings.
- **Visualization**: Produces an interactive story map for play-through.

### Iterative Graph Generation
Builds knowledge graphs incrementally from large volumes of text.
- **Schema**: Supports custom node and edge types (e.g., Concept, Entity, RELATES_TO).
- **Scalability**: Processes context in chunks to build complex relationship maps.
- **Visualization**: Generates Mermaid diagrams to visualize the evolving graph.
- **Export**: Saves the final graph in GraphSON JSON format.

### Journalism Reasoning
An investigative tool that applies professional journalistic standards to topics.
- **Fact-Checking**: Verifies claims against evidence with confidence levels.
- **Perspectives**: Identifies diverse stakeholder voices and potential biases.
- **Context**: Analyzes historical background and broader implications.
- **Synthesis**: Provides an editorial assessment of newsworthiness and gaps.

### Narrative Generation
Generates full-length creative narratives using an iterative scene-by-scene approach.
- **Structure**: Organizes stories into acts and scenes with consistent emotional arcs.
- **Contextual Memory**: Feeds previous scene data into subsequent generations for continuity.
- **Visuals**: Generates cover art, setting references, and character portraits.
- **Style**: Configurable for literary, thriller, technical, or conversational styles.

### Report Generation
Produces comprehensive business reports with data analysis and recommendations.
- **Metrics**: Analyzes KPIs and data points with trend and comparative analysis.
- **Insights**: Generates executive summaries and identifies critical findings.
- **Actionable**: Provides prioritized recommendations and risk assessments.
- **Visual Aids**: Suggests appropriate chart types and placements.

### Research Paper Generation
Generates academically rigorous research papers with proper citations.
- **Rigor**: Supports levels from undergraduate to postdoc.
- **Citations**: Handles APA, MLA, Chicago, and IEEE styles.
- **Components**: Includes abstract, literature review, methodology, and bibliography.
- **Peer Review**: Simulates a peer review process to identify strengths and weaknesses.

### Scriptwriting
Generates production-ready scripts for videos, podcasts, and presentations.
- **Production**: Includes visual directions, B-roll suggestions, and speaker notes.
- **Timing**: Provides timing markers and calculates duration based on pacing.
- **Engagement**: Focuses on hooks, key messages, and calls-to-action.

### Software Design Document
Creates technical specifications and architectural documentation.
- **Diagrams**: Generates C4, ERD, Sequence, and Activity diagrams using Mermaid.
- **Planning**: Produces project data JSON including epics, sprints, and tasks.
- **Requirements**: Documents functional and non-functional requirements with traceability.

### Technical Explanation
Breaks down complex technical subjects for specific target audiences.
- **Education**: Uses analogies and metaphors to explain difficult concepts.
- **Code**: Includes annotated code examples in various programming languages.
- **Clarity**: Defines essential terminology and provides comparisons to related concepts.

### Tutorial Generation
Creates step-by-step instructional guides for processes and projects.
- **Action-Oriented**: Focuses on clear, imperative steps with expected outcomes.
- **Validation**: Includes success checkpoints and troubleshooting sections.
- **Visuals**: Uses screenshot placeholders to guide the user.
```

### Summary of Changes
- Created a comprehensive `README.md` for the `writing` tools package.
- Included all 15 tools found in the directory: `ArticleGeneration`, `BusinessProposal`, `ComicBookGeneration`, `DataTableCompilation`, `EmailCampaign`, `InteractiveStory`, `IterativeGraphGeneration`, `JournalismReasoning`, `NarrativeGeneration`, `ReportGeneration`, `ResearchPaperGeneration`, `Scriptwriting`, `SoftwareDesignDocument`, `TechnicalExplanation`, and `TutorialGeneration`.
- Extracted key features and capabilities from the `TaskType` definitions and `ExecutionConfigData` classes in the Kotlin source files.
- Organized the documentation to highlight the specific value proposition of each tool (e.g., Mermaid diagrams for software design, citation styles for research papers, state tracking for interactive stories).
- Followed the transform pattern specified in `index_docs.md`.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/writing/README.md</a> Updated
