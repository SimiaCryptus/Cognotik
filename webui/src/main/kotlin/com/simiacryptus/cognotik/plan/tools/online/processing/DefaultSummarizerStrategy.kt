package com.simiacryptus.cognotik.plan.tools.online.processing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import kotlin.math.min

open class DefaultSummarizerStrategy : PageProcessingStrategy {
    companion object {
        private val log = LoggerFactory.getLogger(DefaultSummarizerStrategy::class.java)
    }

    override val description: String // Describe both the strategy and its configuration
        get() = ""

    open override fun processPage(
        url: String,
        content: String,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.PageProcessingResult {
        val analysisGoal = analysisGoal(context)

        val analysis = try {
            transformContent(content, analysisGoal, context)
        } catch (e: Exception) {
            log.error("Error transforming content for URL: $url", e)
            return PageProcessingStrategy.PageProcessingResult(
                url = url,
                pageType = CrawlerAgentTask.PageType.Error,
                content = "Error analyzing content: ${e.message}",
                extractedLinks = null,
                metadata = mapOf("error" to (e.message ?: "Unknown error"))
            )
        }

        return PageProcessingStrategy.PageProcessingResult(
            url = url,
            pageType = analysis.obj.page_type,
            content = analysis.text,
            extractedLinks = analysis.obj.link_data,
            metadata = mapOf(
                "tags" to (analysis.obj.tags ?: emptyList<String>())
            ),
            shouldTerminate = false
        )
    }

    open fun analysisGoal(context: PageProcessingStrategy.ProcessingContext): String = when {
        context.executionConfig.content_queries != null -> context.executionConfig.content_queries.toJson()
        context.executionConfig.task_description?.isNotBlank() == true -> context.executionConfig.task_description!!
        else -> "Analyze the content and provide insights."
    }

    override fun shouldContinueCrawling(
        currentResults: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.ContinuationDecision {
        // Never early-terminate, use existing page limit logic
        return PageProcessingStrategy.ContinuationDecision(
            shouldContinue = context.processedCount.get() < context.maxPages,
            reason = "Processing up to max_pages limit"
        )
    }

    override fun generateFinalOutput(
        results: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): String {
        val analysisResults = results.joinToString("\n") { it.content }
        return createFinalSummary(analysisResults, context)
    }

    private fun createFinalSummary(analysisResults: String, context: PageProcessingStrategy.ProcessingContext): String {
        val maxFinalOutputSize = context.typeConfig.max_final_output_size ?: 15000

        if (analysisResults.length < maxFinalOutputSize * 1.2) {
            return analysisResults.substring(0, min(analysisResults.length, maxFinalOutputSize)) +
                    "\n\n---\n\n*Note: Some content has been truncated due to length limitations.*"
        }

        val summary = ChatAgent(
            prompt = listOf(
                "Create a comprehensive summary of the following web search results and analyses.",
                "Analysis goal: ${context.executionConfig.content_queries ?: context.executionConfig.task_description ?: "Provide key insights"}",
                "For each source, extract the most important insights, facts, and conclusions.",
                "Organize information by themes rather than by source when possible.",
                "Use markdown formatting with headers, bullet points, and emphasis where appropriate.",
                "Include the most important links that should be followed up on.",
                "Keep your response under ${maxFinalOutputSize / 1000}K characters."
            ).joinToString("\n\n"),
            model = (context.typeConfig.model?.let { context.orchestrationConfig.instance(it) }
                ?: context.orchestrationConfig.defaultFast).getChildClient(context.task),
        ).answer(
            listOf("Here are summaries of each analyzed page:\n${analysisResults}"),
        )

        return summary
    }

    override fun validateConfig(config: Any?): String? {
        // Default strategy doesn't require specific config validation
        return null
    }

    private fun transformContent(
        content: String,
        analysisGoal: String,
        context: PageProcessingStrategy.ProcessingContext
    ): ParsedResponse<CrawlerAgentTask.ParsedPage> {
        val describer = TaskContextYamlDescriber(context.orchestrationConfig)
        val maxChunkSize = 50000
        if (content.length <= maxChunkSize) {
            return pageParsedResponse(context, analysisGoal, content, describer)
        }
        val chunks = splitContentIntoChunks(content, maxChunkSize)
        val chunkResults = chunks.mapIndexed { index, chunk ->
            val chunkGoal = "$analysisGoal (Part ${index + 1}/${chunks.size})"
            pageParsedResponse(context, chunkGoal, chunk, describer)
        }
        if (chunkResults.size == 1) {
            return chunkResults[0]
        }
        val combinedAnalysis = chunkResults.joinToString("\n\n---\n\n") { it.text }
        return pageParsedResponse(context, analysisGoal, combinedAnalysis, describer)
    }

    private fun pageParsedResponse(
        context: PageProcessingStrategy.ProcessingContext,
        analysisGoal: String,
        content: String,
        describer: TypeDescriber
    ): ParsedResponse<CrawlerAgentTask.ParsedPage> {
        return try {
            val model = (context.typeConfig.model?.let { context.orchestrationConfig.instance(it) }
                ?: context.orchestrationConfig.defaultFast).getChildClient(context.task)
            ParsedAgent(
                prompt = listOf(
                    "Below are analyses of different parts of a web page related to this goal: $analysisGoal",
                    "Create a unified summary that combines the key insights from all parts.",
                    "Use markdown formatting for your response, with * characters for bullets.",
                    "Identify the most important links that should be followed up on according to the goal."
                ).joinToString("\n\n"),
                resultClass = CrawlerAgentTask.ParsedPage::class.java,
                model = model,
                describer = describer,
                parsingChatter = model,
            ).answer(listOf(content))
        } catch (e: Exception) {
            log.error("Error during content transformation", e)
            object : ParsedResponse<CrawlerAgentTask.ParsedPage>(
                clazz = CrawlerAgentTask.ParsedPage::class.java
            ) {
                override val obj: CrawlerAgentTask.ParsedPage
                    get() = CrawlerAgentTask.ParsedPage(
                        page_type = CrawlerAgentTask.PageType.Error,
                        page_information = "Error during analysis: ${e.message}"
                    )
                override val text: String
                    get() = "Error during analysis: ${e.message}"
            }
        }
    }

    private fun splitContentIntoChunks(content: String, maxChunkSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        var remainingContent = content
        while (remainingContent.isNotEmpty()) {
            val chunkSize = if (remainingContent.length <= maxChunkSize) {
                remainingContent.length
            } else {
                findBreakPoint(remainingContent, maxChunkSize)
            }
            chunks.add(remainingContent.substring(0, chunkSize))
            remainingContent = remainingContent.substring(chunkSize)
        }
        return chunks
    }

    private fun findBreakPoint(text: String, maxSize: Int): Int {
        val paragraphBreakSearch = text.substring(0, minOf(maxSize, text.length)).lastIndexOf("\n\n")
        if (paragraphBreakSearch > maxSize * 0.7) {
            return paragraphBreakSearch + 2
        }
        val newlineSearch = text.substring(0, minOf(maxSize, text.length)).lastIndexOf("\n")
        if (newlineSearch > maxSize * 0.7) {
            return newlineSearch + 1
        }
        val sentenceSearch = text.substring(0, minOf(maxSize, text.length)).lastIndexOf(". ")
        if (sentenceSearch > maxSize * 0.7) {
            return sentenceSearch + 2
        }
        return minOf(maxSize, text.length)
    }

}