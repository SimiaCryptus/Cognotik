package com.simiacryptus.cognotik.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.actors.ParsedResponse
import com.simiacryptus.cognotik.actors.SimpleActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.FileOutputStream
import java.net.URI
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
    val max_pages_per_task: Int = 50,
    val max_final_output_size: Int = 10000,
    val concurrent_page_processing: Int = 3,
    val allow_revisit_pages: Boolean = false,
    val create_final_summary: Boolean? = true,
    val min_content_length: Int = 100,
) : AbstractTask<CrawlerAgentTask.SearchAndAnalyzeTaskConfigData>(planSettings, planTask) {

    class SearchAndAnalyzeTaskSettings(
        @Description("Method to seed the crawler (optional)") val seed_method: SeedMethod? = SeedMethod.GoogleSearch,
        @Description("Method used to fetch content from  URLs (optional)") val fetch_method: FetchMethod? = FetchMethod.HttpClient,
        task_type: String = "CrawlerAgentTask",
        enabled: Boolean = false,
        model: ApiChatModel? = null,
    ) : TaskSettingsBase(task_type, enabled, model)

    override val taskSettings: SearchAndAnalyzeTaskSettings
        get() = super.taskSettings.jsonCast<SearchAndAnalyzeTaskSettings>()

    class SearchAndAnalyzeTaskConfigData(
        @Description("The search query to use for Google search") val search_query: String? = null,
        @Description("Direct URLs to analyze (comma-separated)") val direct_urls: String? = null,
        @Description("The question(s) considered when processing the content") val content_queries: Any? = null,
        @Description("Maximum number of pages to process in a single task") val max_pages_per_task: Int? = 30,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskConfigBase(
        task_type = TaskType.CrawlerAgentTask.name,
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
                log.info("Cleaning up Selenium WebDriver instance")
                it.quit()
                selenium = null
                log.debug("Selenium WebDriver cleanup completed")
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
        var error: String? = null
        var processingTimeMs: Long = 0
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
        resultFn: (String) -> Unit,
        planSettings: PlanSettings
    ) {
        try {
            val startTime = System.currentTimeMillis()
            log.info("Starting CrawlerAgentTask with config: search_query='${taskConfig?.search_query}', direct_urls='${taskConfig?.direct_urls}', max_pages=${taskConfig?.max_pages_per_task ?: max_pages_per_task}")
            val webSearchDir = File(agent.root.toFile(), ".websearch")
            if (!webSearchDir.exists()) webSearchDir.mkdirs()

            val seedMethod = taskSettings?.seed_method ?: SeedMethod.GoogleSearch
            log.info("Using seed method: $seedMethod")
            val seedItems = try {
                seedMethod.createStrategy(this, agent.user).getSeedItems(taskConfig, planSettings)
            } catch (e: Exception) {
                log.error("Failed to get seed items using method: $seedMethod", e)
                task.error(e)
                resultFn("Error: Failed to get seed items - ${e.message}")
                return
            }

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
            log.info("Initialized page queue with ${pageQueue.size} seed items")
            if (pageQueue.isEmpty()) {
                log.warn("No seed items found, cannot proceed with crawling")
                resultFn("Warning: No seed items found to start crawling")
                return
            }

            val analysisResultsMap = ConcurrentHashMap<Int, String>()
            val maxPages = taskConfig?.max_pages_per_task ?: max_pages_per_task
            val concurrentProcessing = /*taskConfig?.concurrent_page_processing ?:*/ concurrent_page_processing
            log.info("Processing configuration: maxPages=$maxPages, concurrentProcessing=$concurrentProcessing")

            val tabs = TabbedDisplay(task)
            val exeManager = FixedConcurrencyProcessor(agent.pool, concurrentProcessing)
            val futureMap = mutableMapOf<String, Future<*>>()
            val processedCount = AtomicInteger(0)
            val errorCount = AtomicInteger(0)
            val maxErrors = maxPages / 2 // Stop if too many errors
            log.info("Starting crawling loop with maxErrors threshold: $maxErrors")
            val fetchStrategy = (this@CrawlerAgentTask.taskSettings.fetch_method
                ?: FetchMethod.HttpClient).createStrategy(
                this@CrawlerAgentTask
            )

            try {
                while (
                    pageQueue.count { !it.completed } > 0 &&
                    pageQueue.count { it.completed } < maxPages &&
                    errorCount.get() < maxErrors
                ) {
                    val queueStats =
                        "total=${pageQueue.size} , completed=${pageQueue.count { it.completed }}, started=${pageQueue.count { it.started }}"
                    while (
                        pageQueue.count { it.started } < maxPages && // Do not start more than maxPages
                        pageQueue.count { !it.started } > 0 && // There are still unstarted pages
                        errorCount.get() < maxErrors // Not too many errors
                    ) {
                        log.info("Status before queuing next page: $queueStats, active_tasks=${futureMap.size}, errors=${errorCount.get()}/$maxErrors")
                        val page = synchronized(pageQueue) {
                            pageQueue.filter { !it.started }
                                .maxByOrNull { it.relevance_score }
                                ?.apply { started = true }
                        } ?: break
                        log.info("Queuing page for processing: url='${page.link}', title='${page.title}', depth=${page.depth}, relevance=${page.relevance_score}")
                        val task = task.manager.newTask(false).apply { tabs[page.link] = placeholder; task.update() }
                        futureMap[page.link] = exeManager.submit {
                            val pageStartTime = System.currentTimeMillis()
                            log.info("Starting to process page ${processedCount.get() + 1}: url='${page.link}', title='${page.title}'")
                            val currentIndex = processedCount.incrementAndGet()
                            if (currentIndex > maxPages) {
                                log.warn("Max pages limit ($maxPages) reached, stopping processing for page: ${page.link}")
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
                                            val content = fetchAndProcessUrl(
                                                url,
                                                webSearchDir,
                                                currentIndex,
                                                agent.pool,
                                                fetchStrategy
                                            )
                                            log.debug("Fetched content for '$url': ${content.length} characters")
                                            if (content.length < min_content_length) {
                                                log.info("Content too short for '$url': ${content.length} < $min_content_length chars, skipping")
                                                appendLine("*Content too short (${content.length} chars), skipping this result*")
                                                appendLine()
                                                return@buildString
                                            }

                                            val analysisGoal = when {
                                                taskConfig?.content_queries != null -> taskConfig.content_queries.toJson()
                                                taskConfig?.task_description?.isNotBlank() == true -> taskConfig.task_description!!
                                                else -> "Analyze the content and provide insights."
                                            }
                                            log.debug("Analyzing content for '$url' with goal: $analysisGoal")
                                            val analysis: ParsedResponse<ParsedPage> =
                                                transformContent(content, analysisGoal, planSettings, agent.describer)

                                            if (analysis.obj.page_type == PageType.Error) {
                                                log.warn("Analysis returned error for '$url': ${analysis.obj.page_information}")
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
                                                log.info("Content marked as irrelevant for '$url', skipping")
                                                appendLine("*Irrelevant content, skipping this result*")
                                                appendLine()
                                                saveAnalysis(webSearchDir.resolve("irrelevant").apply {
                                                    mkdirs()
                                                }, url, analysis, currentIndex)
                                                return@buildString
                                            }
                                            log.debug("Successfully analyzed content for '$url', saving results")

                                            saveAnalysis(webSearchDir, url, analysis, currentIndex)

                                            appendLine(analysis.text)
                                            appendLine()

                                            if (/*taskConfig?.follow_links ?:*/ follow_links) {

                                                var linkData = analysis.obj.link_data
                                                val allowRevisit = /*taskConfig?.allow_revisit_pages ?:*/
                                                    allow_revisit_pages
                                                if (linkData.isNullOrEmpty()) {
                                                    linkData = extractLinksFromMarkdown(analysis.text)
                                                    log.debug("Extracted ${linkData.size} links from markdown for '$url'")
                                                } else {
                                                    log.debug("Using ${linkData.size} structured links from analysis for '$url'")
                                                }
                                                val filteredLinks = linkData
                                                    .take(10) // Limit links per page to prevent explosion
                                                    .filter { link ->
                                                        VALID_URL_PATTERN.matcher(link.link).matches() &&
                                                                !isBlacklistedDomain(link.link) &&
                                                                (allowRevisit || pageQueue.none { it.link == link.link })
                                                    }
                                                log.info("Adding ${filteredLinks.size} new links to queue from '$url' (filtered from ${linkData.size} total)")
                                                linkData
                                                    .take(10) // Limit links per page to prevent explosion
                                                    .filter { link ->
                                                        VALID_URL_PATTERN.matcher(link.link).matches() &&
                                                                !isBlacklistedDomain(link.link) &&
                                                                (allowRevisit || pageQueue.none { it.link == link.link })
                                                    }
                                                    .forEach { link ->
                                                        log.debug("Adding link to queue: url='${link.link}', title='${link.title}', relevance=${link.relevance_score}")
                                                        pageQueue.add(
                                                            link.apply { depth = page.depth + 1 }
                                                        )
                                                    }
                                            }
                                        } catch (e: Exception) {
                                            log.error("Error processing URL: $url", e)
                                            errorCount.incrementAndGet()
                                            page.error = e.message
                                            appendLine("*Error processing this result: ${e.message}*")
                                            appendLine()
                                        }
                                    }
                                task.add(processPageResult.renderMarkdown)
                                analysisResultsMap[currentIndex] = processPageResult
                                log.info("Successfully processed page ${currentIndex}: url='${page.link}', processing_time=${System.currentTimeMillis() - pageStartTime}ms")
                            } catch (e: Exception) {
                                task.error(e)
                                log.error("Error processing page: ${page.link}", e)
                                errorCount.incrementAndGet()
                                page.error = e.message
                                analysisResultsMap[currentIndex] =
                                    "## ${currentIndex}. [${page.title}](${page.link})\n\n*Error processing this result: ${e.message}*\n\n"
                            } finally {
                                page.processingTimeMs = System.currentTimeMillis() - pageStartTime
                                page.completed = true
                                log.debug("Page processing completed: url='${page.link}', time=${page.processingTimeMs}ms, error='${page.error ?: "none"}'")
                            }
                        }
                    }
                    val completedCount = pageQueue.count { it.completed }
                    val queuedCount = pageQueue.count { !it.completed }
                    while (futureMap.values.any { !it.isDone }) {
                        Thread.sleep(5000)
                        futureMap.filter { it.value.isDone }.forEach {
                            log.debug("Cleaning up completed task for URL: ${it.key}")
                            futureMap.remove(it.key)
                        }
                        log.info("Crawling progress: completed=$completedCount/${pageQueue.size}, queued=$queuedCount, active_tasks=${futureMap.size}, errors=${errorCount.get()}/$maxErrors")
                    }
                }
            } catch (e: Exception) {
                log.error("Error during processing", e)
                task.error(e)
            } finally {
                log.info("Crawling phase completed, cleaning up resources")
            }
            val totalTime = System.currentTimeMillis() - startTime
            log.info("CrawlerAgentTask completed: total_time=${totalTime}ms, pages_processed=${processedCount.get()}, errors=${errorCount.get()}, success_rate=${if (processedCount.get() > 0) ((processedCount.get() - errorCount.get()) * 100 / processedCount.get()) else 0}%")

            val analysisResults = (1..processedCount.get()).asSequence().mapNotNull {
                analysisResultsMap[it]
            }.joinToString("\n")
            if (analysisResults.isBlank()) {
                val errorMessage = "No content was successfully processed. Check logs for errors."
                log.error(errorMessage)
                resultFn(errorMessage)
                return
            }

            val finalOutput =
                if (create_final_summary != false && analysisResults.length > max_final_output_size) {
                    log.info("Creating final summary: original_size=${analysisResults.length}, max_size=$max_final_output_size")
                    createFinalSummary(analysisResults)
                } else {
                    log.info("Using analysis results directly: size=${analysisResults.length}")
                    analysisResults
                }
            task.manager.newTask(false).apply {
                tabs["Final Summary"] = placeholder
                add(finalOutput.renderMarkdown())
                task.update()
            }
            log.info("CrawlerAgentTask finished successfully, final output size: ${finalOutput.length}")
            resultFn(finalOutput)
        } catch (e: Exception) {
            log.error("Unhandled exception in CrawlerAgentTask", e)
            task.error(e)
            resultFn("Error: ${e.message}")
        } finally {
            cleanup()
        }
    }

    private fun isBlacklistedDomain(url: String): Boolean {
        val blacklistedDomains = setOf(
            "facebook.com", "twitter.com", "instagram.com", "linkedin.com",
            "youtube.com", "tiktok.com", "pinterest.com", "reddit.com",
            "amazon.com", "ebay.com", "aliexpress.com"
        )
        return try {
            val domain = URI.create(url).host?.lowercase()
            blacklistedDomains.any { domain?.contains(it) == true }
        } catch (e: Exception) {
            log.warn("Invalid URL format: $url")
            true // Blacklist invalid URLs
        }
    }

    private fun createFinalSummary(analysisResults: String): String {
        log.info("Creating final summary of analysis results (original size: ${analysisResults.length})")
        val maxSize = /*taskConfig?.max_final_output_size ?:*/ max_final_output_size

        if (analysisResults.length < maxSize * 1.2) {
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
            model = (taskSettings.model?.let { planSettings.instance(it) } ?: planSettings.parsingChatter),
        ).answer(
            listOf(
                "Here are summaries of each analyzed page:\n${urlSections.joinToString("\n\n")}"
            ),
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

    private fun fetchAndProcessUrl(
        url: String, webSearchDir: File, index: Int, pool: ExecutorService, fetchStrategy: FetchStrategy
    ): String {

        if (!allow_revisit_pages && urlContentCache.containsKey(url)) {
            log.debug("Using cached content for URL: $url (cache size: ${urlContentCache.size})")
            return urlContentCache[url]!!
        }
        log.debug("Fetching content for URL: $url using method: ${taskSettings?.fetch_method ?: FetchMethod.HttpClient}")

        return try {
            val content = fetchStrategy.fetch(url, webSearchDir, index, pool, planSettings)
            // Cache successful fetches
            if (content.isNotBlank()) {
                urlContentCache[url] = content
                log.debug("Cached content for URL: $url (content length: ${content.length}, cache size: ${urlContentCache.size})")
            } else {
                log.warn("Fetched empty content for URL: $url")
            }
            content
        } catch (e: Exception) {
            log.error("Failed to fetch URL: $url", e)
            throw e
        }
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
            val extension = when {
                webSearchDir.name.contains("document") -> ".txt"
                webSearchDir.name.contains("text") -> ".txt"
                webSearchDir.name.contains("extracted_text") -> ".txt"
                else -> ".html"
            }
            val rawFile = File(webSearchDir, urlSafe + extension)
            // Ensure content is saved with proper encoding
            rawFile.writeText(content, java.nio.charset.StandardCharsets.UTF_8)
            log.debug("Saved raw content to: ${rawFile.absolutePath} (size: ${content.length} chars)")
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
            log.debug("Saved analysis to file: ${analysisFile.absolutePath} (size: ${contentWithHeader.length} chars)")
        } catch (e: Exception) {
            log.error("Failed to save analysis for URL: $url", e)
        }
    }

    private fun transformContent(
        content: String,
        analysisGoal: String,
        planSettings: PlanSettings,
        describer: TypeDescriber
    ): ParsedResponse<ParsedPage> {

        val maxChunkSize = 50000
        if (content.length <= maxChunkSize) {
            log.debug("Content size (${content.length}) within limit, processing as single chunk")
            return pageParsedResponse(planSettings, analysisGoal, content, describer)
        }

        log.debug("Content size (${content.length}) exceeds limit, splitting into chunks")
        val chunks = splitContentIntoChunks(content, maxChunkSize)
        log.debug("Split content into ${chunks.size} chunks")
        val chunkResults = chunks.mapIndexed { index, chunk ->
            log.debug("Processing chunk ${index + 1}/${chunks.size} (size: ${chunk.length})")
            val chunkGoal = "$analysisGoal (Part ${index + 1}/${chunks.size})"
            pageParsedResponse(planSettings, chunkGoal, chunk, describer)
        }
        if (chunkResults.size == 1) {
            log.debug("Only one chunk result, returning directly")
            return chunkResults[0]
        }
        log.debug("Combining ${chunkResults.size} chunk results into final analysis")
        val combinedAnalysis = chunkResults.joinToString("\n\n---\n\n") { it.text }
        return pageParsedResponse(planSettings, analysisGoal, combinedAnalysis, describer)
    }

    private fun pageParsedResponse(
        planSettings: PlanSettings,
        analysisGoal: String,
        content: String,
        describer: TypeDescriber
    ): ParsedResponse<ParsedPage> {
        val summaryPrompt = listOf(
            "Below are analyses of different parts of a web page related to this goal: $analysisGoal",
            "Content: \n${content.indent("  ")}",
            "Create a unified summary that combines the key insights from all parts.",
            "Use markdown formatting for your response, with * characters for bullets.",
            "Identify the most important links that should be followed up on according to the goal."
        ).joinToString("\n\n")

        return try {
            ParsedActor(
                prompt = summaryPrompt,
                resultClass = ParsedPage::class.java,
                model = (taskSettings.model?.let { planSettings.instance(it) } ?: planSettings.parsingChatter),
                describer = describer,
                parsingModel = planSettings.parsingChatter,
            ).answer(listOf(summaryPrompt))
        } catch (e: Exception) {
            log.error("Error during content transformation", e)
            object : ParsedResponse<ParsedPage>(
                clazz = ParsedPage::class.java
            ) {
                override val obj: ParsedPage
                    get() = ParsedPage(
                        page_type = PageType.Error,
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

    fun saveBinaryContent(webSearchDir: File, url: String, content: ByteArray, extension: String, index: Int) {
        try {
            val urlSafe = url.replace(Regex("[^a-zA-Z0-9]"), "_").take(50)
            webSearchDir.mkdirs()
            val binaryFile = File(webSearchDir, "${urlSafe}_${index}.$extension")
            FileOutputStream(binaryFile).use { it.write(content) }
            log.debug("Saved binary content to: ${binaryFile.absolutePath}")
        } catch (e: Exception) {
            log.error("Failed to save binary content for URL: $url", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CrawlerAgentTask::class.java)
        private val LINK_PATTERN = Pattern.compile("""\[([^]]+)]\(([^)]+)\)""")
        private val VALID_URL_PATTERN = Pattern.compile("^(http|https)://.*")
    }
}