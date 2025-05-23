package com.simiacryptus.cognotik.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.actors.ParsedResponse
import com.simiacryptus.cognotik.actors.SimpleActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Selenium
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.toJson
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.math.min

class CrawlerAgentTask(
    planSettings: PlanSettings,
    planTask: SearchAndAnalyzeTaskConfigData?,
    val follow_links: Boolean = true,
    val max_pages_per_task: Int = 100,
    val max_final_output_size: Int = 10000,
    val concurrent_page_processing: Int = 5,
    val allow_revisit_pages: Boolean = false,
    val create_final_summary: Boolean? = true,
) : AbstractTask<CrawlerAgentTask.SearchAndAnalyzeTaskConfigData>(planSettings, planTask) {

    class SearchAndAnalyzeTaskConfigData(
        @Description("The search query to use for Google search") val search_query: String? = null,
        @Description("Direct URLs to analyze (comma-separated)") val direct_urls: String? = null,
        @Description("The question(s) considered when processing the content") val content_queries: Any? = null,
        @Description("Method to seed the crawler (GoogleSearch or DirectUrls)") val seed_method: SeedMethod = SeedMethod.GoogleSearch,
        @Description("Method used to fetch content from  URLs (HttpClient or Selenium)") val fetch_method: FetchMethod = FetchMethod.HttpClient,
        @Description("Maximum number of pages to process in a single task") val max_pages_per_task: Int? = 30,





        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskConfigBase(
        task_type = TaskType.WebSearchTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    var selenium: Selenium? = null

    val urlContentCache = ConcurrentHashMap<String, String>()

    override fun promptSegment() = """
    CrawlerAgentTask - Search Google, fetch top results, and analyze content
    ** Specify the search query
    ** Or provide direct URLs to analyze
    ** Specify the analysis goal or focus
    ** Results will be saved to .websearch directory for future reference
    ** Links found in analysis can be automatically followed for deeper research
  """.trimIndent()

    fun cleanup() {
        try {
            selenium?.let {
                it.quit()
                selenium = null
            }
        } catch (e: Exception) {
            log.error("Error cleaning up Selenium resources", e)
        }
    }

    data class LinkData(
        val link: String,
        val title: String,
        val tags: List<String>? = null,
        @Description("1-100") val relevance_score: Double = 100.0
    ) {
        var started: Boolean = false
        var completed: Boolean = false
        var depth: Int = 0
    }

    enum class PageType {
        Error,
        Irrelevant,
        OK
    }

    data class ParsedPage(
        val page_type: PageType = PageType.OK,
        val page_information: Any? = null,
        val tags: List<String>? = null,
        val link_data: List<LinkData>? = null,
    )

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        api: ChatClient,
        resultFn: (String) -> Unit,
        api2: OpenAIClient,
        planSettings: PlanSettings
    ) {
        val webSearchDir = File(agent.root.toFile(), ".websearch")
        if (!webSearchDir.exists()) webSearchDir.mkdirs()

        val seedMethod = taskConfig?.seed_method ?: SeedMethod.GoogleSearch
        val seedItems = seedMethod.createStrategy(this, agent.user).getSeedItems(taskConfig, planSettings)

        val pageQueue = mutableListOf<LinkData>().apply {
            seedItems?.forEach { item ->
                add(
                    LinkData(
                        link = item["link"] as String,
                        title = (item["title"] as? String) ?: "Untitled",
                        relevance_score = 100.0
                    )
                )
            }
        }

        val analysisResultsMap = ConcurrentHashMap<Int, String>()
        val maxPages = taskConfig?.max_pages_per_task ?: max_pages_per_task
        val concurrentProcessing = /*taskConfig?.concurrent_page_processing ?:*/ concurrent_page_processing

        val tabs = TabbedDisplay(task)
        val exeManager = FixedConcurrencyProcessor(agent.pool, concurrentProcessing)
        val futureMap = mutableMapOf<String, Future<*>>()
        val processedCount = AtomicInteger(0)
        try {
            while (
                pageQueue.count { !it.completed } > 0 &&
                pageQueue.count { it.completed } < maxPages
            ) {
                while (
                    pageQueue.count { it.started } < maxPages &&

                    pageQueue.count { !it.started } > 0
                ) {
                    val page = synchronized(pageQueue) {
                        pageQueue.filter { !it.started }.maxByOrNull { it.relevance_score }?.apply { started = true }
                    } ?: break
                    log.info("Queued page: ${page.toJson()}")
                    futureMap[page.link] = exeManager.submit {
                        log.info("Processing page: ${page.toJson()}")
                        val task = agent.ui.newTask(false).apply { tabs[page.link] = placeholder }
                        val currentIndex = processedCount.incrementAndGet()
                        if (currentIndex > maxPages) {
                            log.info("Max pages reached, stopping processing")
                            return@submit
                        }
                        try {
                            val url = page.link
                            val title = page.title
                            val processPageResult =
                                buildString {
                                    appendLine("## ${currentIndex}. [${title}]($url)")
                                    appendLine()

                                    try {

                                        val content = fetchAndProcessUrl(url, webSearchDir, currentIndex, agent.pool)
                                        if (content.isBlank()) {
                                            appendLine("*Empty content, skipping this result*")
                                            appendLine()
                                            return@buildString
                                        }

                                        val analysisGoal = when {
                                            taskConfig?.content_queries != null -> taskConfig.content_queries.toJson()
                                            taskConfig?.task_description?.isNotBlank() == true -> taskConfig.task_description!!
                                            else -> "Analyze the content and provide insights."
                                        }
                                        val analysis: ParsedResponse<ParsedPage> =
                                            transformContent(content, analysisGoal, api, planSettings, agent.describer)

                                        if (analysis.obj.page_type == PageType.Error) {
                                            appendLine(
                                                "*Error processing this result: ${
                                                    analysis.obj.page_information?.let {
                                                        JsonUtil.toJson(
                                                            it
                                                        )
                                                    }
                                                }*"
                                            )
                                            appendLine()
                                            saveAnalysis(webSearchDir.resolve("error").apply {
                                                mkdirs()
                                            }, url, analysis, currentIndex)
                                            return@buildString
                                        }

                                        if (analysis.obj.page_type == PageType.Irrelevant) {
                                            appendLine("*Irrelevant content, skipping this result*")
                                            appendLine()
                                            saveAnalysis(webSearchDir.resolve("irrelevant").apply {
                                                mkdirs()
                                            }, url, analysis, currentIndex)
                                            return@buildString
                                        }

                                        saveAnalysis(webSearchDir, url, analysis, currentIndex)

                                        appendLine(analysis.text)
                                        appendLine()

                                        if (/*taskConfig?.follow_links ?:*/ follow_links) {

                                            var linkData = analysis.obj.link_data
                                            val allowRevisit = /*taskConfig?.allow_revisit_pages ?:*/allow_revisit_pages
                                            if (linkData.isNullOrEmpty()) {
                                                linkData = extractLinksFromMarkdown(analysis.text)
                                                log.info("Extracted ${linkData.size} links from markdown")
                                            } else {
                                                log.info("Using ${linkData.size} structured links from analysis")
                                            }
                                            linkData
                                                .filter { link ->
                                                    VALID_URL_PATTERN.matcher(link.link).matches() &&
                                                            (allowRevisit || pageQueue.none { it.link == link.link })
                                                }
                                                .forEach { link ->
                                                    log.info("Adding link to queue: ${link.toJson()}")
                                                    pageQueue.add(
                                                        link.apply { depth = page.depth + 1 }
                                                    )
                                                }
                                        }
                                    } catch (e: Exception) {
                                        log.error("Error processing URL: $url", e)
                                        appendLine("*Error processing this result: ${e.message}*")
                                        appendLine()
                                    }
                                }
                            task.add(processPageResult.renderMarkdown)
                            analysisResultsMap[currentIndex] = processPageResult
                            log.info("Processed page: ${page.toJson()}")
                        } catch (e: Exception) {
                            task.error(agent.ui, e)
                            log.error("Error processing page: ${page.link}", e)
                            analysisResultsMap[currentIndex] =
                                "## ${currentIndex}. [${page.title}](${page.link})\n\n*Error processing this result: ${e.message}*\n\n"
                        } finally {
                            page.completed = true
                            log.info("Status: # completed: ${pageQueue.count { it.completed }} / ${pageQueue.size}; # queued: ${pageQueue.count { !it.completed }}")
                        }
                    }
                }
                log.info("Waiting for ${futureMap.size} tasks to complete")
                log.info("Status: # completed: ${pageQueue.count { it.completed }} / ${pageQueue.size}; # queued: ${pageQueue.count { !it.completed }}")
                Thread.sleep(1000)
                futureMap.values.forEach {
                    try {
                        it.get()
                    } catch (e: Exception) {
                        log.info("Task finished: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Error during processing", e)
        } finally {
            log.info("Processing completed")
        }
        val analysisResults = (1..processedCount.get()).asSequence().mapNotNull {
            analysisResultsMap[it]
        }.joinToString("\n")
        val finalOutput =
            if (create_final_summary != false && analysisResults.length > max_final_output_size) {
                createFinalSummary(analysisResults, api)
            } else {
                analysisResults
            }
        agent.ui.newTask(false).apply {
            tabs["Final Summary"] = placeholder
            add(finalOutput.renderMarkdown())
        }
        resultFn(finalOutput)
    }

    private fun createFinalSummary(analysisResults: String, api: API): String {
        log.info("Creating final summary of analysis results (original size: ${analysisResults.length})")
        val maxSize = /*taskConfig?.max_final_output_size ?:*/ max_final_output_size

        if (analysisResults.length < maxSize * 1.5) {
            log.info("Analysis results only slightly exceed max size, truncating instead of summarizing")
            return analysisResults.substring(
                0,
                min(analysisResults.length, maxSize)
            ) + "\n\n---\n\n*Note: Some content has been truncated due to length limitations.*"
        }

        val headerEndIndex = analysisResults.indexOf("## 1. [")
        val header = if (headerEndIndex > 0) {
            analysisResults.substring(0, headerEndIndex)
        } else {
            "# Web Search: ${taskConfig?.search_query ?: taskConfig?.direct_urls ?: ""}\n\n"
        }

        val urlSections = extractUrlSections(analysisResults)
        log.info("Extracted ${urlSections.size} URL sections for summarization")
        val summary = SimpleActor(
            prompt = listOf(
                "Create a comprehensive summary of the following web search results and analyses.",
                "Original analysis contained ${urlSections.size} web pages related to: ${taskConfig?.search_query ?: ""}",
                "Analysis goal: ${taskConfig?.content_queries ?: taskConfig?.task_description ?: "Provide key insights"}",
                "For each source, extract the most important insights, facts, and conclusions.",
                "Organize information by themes rather than by source when possible.",
                "Use markdown formatting with headers, bullet points, and emphasis where appropriate.",
                "Include the most important links that should be followed up on.",
                "Keep your response under ${maxSize / 1000}K characters."
            ).joinToString("\n\n"),
            model = taskSettings.model ?: planSettings.parsingModel,
        ).answer(
            listOf(
                "Here are summaries of each analyzed page:\n${urlSections.joinToString("\n\n")}"
            ), api
        )
        return header + summary
    }

    private fun extractUrlSections(analysisResults: String): List<String> {
        val sections = mutableListOf<String>()
        val sectionPattern = Pattern.compile("""## \d+\. \[([^]]+)]\(([^)]+)\)(.*?)(?=## \d+\. \[|$)""", Pattern.DOTALL)
        val matcher = sectionPattern.matcher(analysisResults)
        while (matcher.find()) {
            val title = matcher.group(1)
            val url = matcher.group(2)
            val content = matcher.group(3).trim()
            val condensed = "**[${title}](${url})**: ${summarizeSection(content)}"
            sections.add(condensed)
        }
        return sections
    }

    private fun summarizeSection(content: String): String {

        val firstParagraph = content.split("\n\n").firstOrNull()?.trim() ?: ""
        if (firstParagraph.length < 300) return firstParagraph

        val sentences = content.split(". ").take(3)
        return sentences.joinToString(". ") + (if (sentences.size >= 3) "..." else "")
    }

    private fun fetchAndProcessUrl(url: String, webSearchDir: File, index: Int, pool: ExecutorService): String {

        val allowRevisit = /*taskConfig?.allow_revisit_pages ?:*/ allow_revisit_pages
        if (!allowRevisit && urlContentCache.containsKey(url)) {
            log.info("Using cached content for URL: $url")
            return urlContentCache[url]!!
        }
        return (taskConfig?.fetch_method ?: FetchMethod.HttpClient).createStrategy(this)
            .fetch(url, webSearchDir, index, pool, planSettings)
    }

    private fun extractLinksFromMarkdown(markdown: String): List<LinkData> {
        val links = mutableListOf<Pair<String, String>>()
        val matcher = LINK_PATTERN.matcher(markdown)
        while (matcher.find()) {
            val linkText = matcher.group(1)
            val linkUrl = matcher.group(2)
            try {
                if (VALID_URL_PATTERN.matcher(linkUrl).matches()) {
                    links.add(Pair(linkText, linkUrl))
                }
            } catch (e: Exception) {
                log.warn("Invalid URL found in markdown: $linkUrl")
            }
        }
        return links.map { (linkText, linkUrl) ->
            LinkData(
                link = linkUrl,
                title = linkText,
                relevance_score = 50.0
            )
        }
    }

    fun saveRawContent(webSearchDir: File, url: String, content: String) {
        try {
            val urlSafe = url.replace(Regex("[^a-zA-Z0-9]"), "_").take(50)
            webSearchDir.mkdirs()
            val rawFile = File(webSearchDir, urlSafe + ".html")
            rawFile.writeText(content)
        } catch (e: Exception) {
            log.error("Failed to save raw content for URL: $url", e)
        }
    }

    private fun saveAnalysis(webSearchDir: File, url: String, analysis: ParsedResponse<ParsedPage>, index: Int) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val urlSafe = url.replace(Regex("https?://"), "").replace(Regex("[^a-zA-Z0-9]"), "_").take(100)
            val analysisFile = File(webSearchDir, "${urlSafe}_${index}_${timestamp}.md")

            val metadata = mapOf(
                "url" to url,
                "timestamp" to LocalDateTime.now().toString(),
                "index" to index,
                "query" to (taskConfig?.search_query ?: ""),
                "content_query" to (taskConfig?.content_queries ?: "")
            )
            val metadataJson = ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata)

            val contentWithHeader =
                "<!-- ${metadataJson}${analysis.obj.let { JsonUtil.toJson(it) }} -->\n\n${analysis.text}"
            analysisFile.writeText(contentWithHeader)
        } catch (e: Exception) {
            log.error("Failed to save analysis for URL: $url", e)
        }
    }

    private fun transformContent(
        content: String,
        analysisGoal: String,
        api: API,
        planSettings: PlanSettings,
        describer: TypeDescriber
    ): ParsedResponse<ParsedPage> {

        val maxChunkSize = 50000
        if (content.length <= maxChunkSize) {
            return pageParsedResponse(planSettings, analysisGoal, content, api, describer)
        }

        log.info("Content size (${content.length}) exceeds limit, splitting into chunks")
        val chunks = splitContentIntoChunks(content, maxChunkSize)
        log.info("Split content into ${chunks.size} chunks")
        val chunkResults = chunks.mapIndexed { index, chunk ->
            log.info("Processing chunk ${index + 1}/${chunks.size} (size: ${chunk.length})")
            val chunkGoal = "$analysisGoal (Part ${index + 1}/${chunks.size})"
            pageParsedResponse(planSettings, chunkGoal, chunk, api, describer)
        }
        if (chunkResults.size == 1) {
            return chunkResults[0]
        }
        val combinedAnalysis = chunkResults.joinToString("\n\n---\n\n") { it.text }
        return pageParsedResponse(planSettings, analysisGoal, combinedAnalysis, api, describer)
    }

    private fun pageParsedResponse(
        planSettings: PlanSettings,
        analysisGoal: String,
        content: String,
        api: API,
        describer: TypeDescriber
    ): ParsedResponse<ParsedPage> {
        val summaryPrompt = listOf(
            "Below are analyses of different parts of a web page related to this goal: $analysisGoal",
            "Content: \n${content.indent("  ")}",
            "Create a unified summary that combines the key insights from all parts.",
            "Use markdown formatting for your response, with * characters for bullets.",
            "Identify the most important links that should be followed up on according to the goal."
        ).joinToString("\n\n")

        return ParsedActor(
            prompt = summaryPrompt,
            resultClass = ParsedPage::class.java,
            model = taskSettings.model ?: planSettings.parsingModel,
            describer = describer,
            parsingModel = planSettings.parsingModel,
        ).answer(listOf(summaryPrompt), api)
    }

    private fun splitContentIntoChunks(content: String, maxChunkSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        var remainingContent = content
        while (remainingContent.isNotEmpty()) {
            val chunkSize = if (remainingContent.length <= maxChunkSize) {
                remainingContent.length
            } else {
                val breakPoint = findBreakPoint(remainingContent, maxChunkSize)
                breakPoint
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

    companion object {
        private val log = LoggerFactory.getLogger(CrawlerAgentTask::class.java)
        private val LINK_PATTERN = Pattern.compile("""\[([^]]+)]\(([^)]+)\)""")
        private val VALID_URL_PATTERN = Pattern.compile("^(http|https)://.*")
    }
}