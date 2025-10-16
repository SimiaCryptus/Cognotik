package com.simiacryptus.cognotik.plan.tools.online

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.actors.ParsedResponse
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.lang.Thread.sleep
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.math.min

class CrawlerAgentTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: CrawlerTaskExecutionConfigData?,
) : AbstractTask<CrawlerAgentTask.CrawlerTaskExecutionConfigData, CrawlerAgentTask.CrawlerTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class CrawlerTaskTypeConfig(
        @Description("Method to seed the crawler (optional)") val seed_method: SeedMethod? = SeedMethod.GoogleSearch,
        @Description("Method used to fetch content from  URLs (optional)") val fetch_method: FetchMethod? = FetchMethod.HttpClient,
        @Description("Maximum number of pages to process in a single task") val max_pages_per_task: Int? = null,
        @Description("Maximum depth to crawl from seed pages") val max_depth: Int? = null,
        @Description("Maximum queue size to prevent memory issues") val max_queue_size: Int? = null,
        @Description("Number of pages to process concurrently") val concurrent_page_processing: Int? = null,
        @Description("Maximum characters in final summary") val max_final_output_size: Int? = null,
        @Description("Minimum content length to process") val min_content_length: Int? = null,
        @Description("Automatically follow links found in analyzed pages") val follow_links: Boolean? = null,
        @Description("Allow crawling the same page multiple times") val allow_revisit_pages: Boolean? = null,
        @Description("Generate a comprehensive summary of all results") val create_final_summary: Boolean? = null,
        task_type: String = "CrawlerAgent",
        model: ApiChatModel? = null,
        name: String? = task_type,
    ) : TaskTypeConfig(task_type = task_type, name = name, model = model)

    override val typeConfig: CrawlerTaskTypeConfig
        get() = super.typeConfig.jsonCast<CrawlerTaskTypeConfig>()

    class CrawlerTaskExecutionConfigData(
        @Description("The search query to use for Google search") val search_query: String? = null,
        @Description("Direct URLs to analyze (comma-separated)") val direct_urls: List<String>? = null,
        @Description("The question(s) considered when processing the content") val content_queries: Any? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = TaskType.CrawlerAgent.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    var selenium: Selenium2S3? = null

    val urlContentCache = ConcurrentHashMap<String, String>()
    private val pageQueueLock = Any()

    override fun promptSegment() = """
        CrawlerAgent - Search Google, fetch top results, and analyze content
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
                try {
                    it.quit()
                } catch (e: Exception) {
                    log.warn("Failed to quit Selenium WebDriver gracefully: ${e.message}")
                }
                selenium = null
                log.debug("Selenium WebDriver cleanup completed")
            }
        } catch (e: Exception) {
            log.error("Error cleaning up Selenium resources", e)
        }
    }

    data class LinkData(
        val link: String? = null,
        val title: String? = null,
        val tags: List<String>? = null,
        @Description("1-100") val relevance_score: Double = 100.0
    ) {
        var started: Boolean = false
        var completed: Boolean = false
        var depth: Int = 0
        var error: String? = null
        var processingTimeMs: Long = 0
        var retryCount: Int = 0
        var lastRetryTime: Long = 0
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
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        log.info("Starting CrawlerAgentTask.run() with messages count: ${messages.size}")
        try {
            resultFn(innerRun(agent, task, orchestrationConfig))
        } catch (e: Throwable) {
            log.error("Unhandled exception in CrawlerAgentTask", e)
            val errorMessage = "Error: ${e.message ?: "Unknown error occurred"}"
            resultFn(errorMessage)
            task.error(e)
        } finally {
            cleanup()
        }
    }

    private fun innerRun(
        agent: TaskOrchestrator,
        task: SessionTask,
        orchestrationConfig: OrchestrationConfig
    ): String {
        try {
            val startTime = System.currentTimeMillis()
            log.info(
                "Starting CrawlerAgentTask with config: search_query='${executionConfig?.search_query}', direct_urls='${
                    executionConfig?.direct_urls?.joinToString(
                        ", "
                    ) ?: ""
                }', max_pages=${typeConfig.max_pages_per_task ?: (typeConfig.max_pages_per_task ?: 30)}"
            )
            val webSearchDir = File(agent.root.toFile(), ".websearch")
            if (!webSearchDir.exists()) {
                if (!webSearchDir.mkdirs()) {
                    log.error("Failed to create websearch directory: ${webSearchDir.absolutePath}")
                    return "Error: Failed to create output directory"
                }
                log.debug("Created websearch directory: ${webSearchDir.absolutePath}")
            }

            val seedMethod = typeConfig.seed_method ?: SeedMethod.GoogleSearch
            log.info("Using seed method: $seedMethod")
            val seedItems = try {
                seedMethod.createStrategy(this, agent.user).getSeedItems(executionConfig, orchestrationConfig)
            } catch (e: Exception) {
                log.error("Failed to get seed items using method: $seedMethod", e)
                task.error(e)
                return "Error: Failed to get seed items - ${e.message}"
            }
            if (seedItems == null || seedItems.isEmpty()) {
                log.warn("No seed items returned from seed method: $seedMethod")
                return "Warning: No seed items found to start crawling"
            }

            val pageQueue = mutableListOf<LinkData>().apply {
                seedItems.forEach { item ->
                    LinkData(
                        link = item.link,
                        title = item.title,
                        tags = item.tags,
                        relevance_score = item.relevance_score
                    ).let { linkData ->
                        log.debug("Adding seed item to page queue: {}", linkData)
                        addToQueue(
                            this,
                            linkData,
                            typeConfig.max_depth ?: (typeConfig.max_depth ?: 3),
                            typeConfig.max_queue_size ?: (typeConfig.max_queue_size ?: 100)
                        )
                        if (this.isEmpty()) {
                            log.warn("No valid seed items found after processing")
                        }
                    }
                }
            }
            log.info("Initialized page queue with ${pageQueue.size} seed items")
            if (pageQueue.isEmpty()) {
                log.warn("No seed items found, cannot proceed with crawling")
                return "Warning: No seed items found to start crawling"
            }

            val analysisResultsMap = ConcurrentHashMap<Int, String>()
            val maxPages = typeConfig.max_pages_per_task ?: (typeConfig.max_pages_per_task ?: 30)
            val concurrentProcessing = /*taskConfig?.concurrent_page_processing ?:*/
                typeConfig.concurrent_page_processing ?: 3
            log.info("Processing configuration: maxPages=$maxPages, concurrentProcessing=$concurrentProcessing")

            val tabs = TabbedDisplay(task)
            val completionService: CompletionService<Unit> = ExecutorCompletionService(agent.pool)
            val activeTasks = ConcurrentHashMap.newKeySet<String>()
            val processedCount = AtomicInteger(0)
            val errorCount = AtomicInteger(0)
            val maxErrors = maxPages / 2 // Stop if too many errors
            log.info("Starting crawling loop with maxErrors threshold: $maxErrors")
            val fetchStrategy = (this@CrawlerAgentTask.typeConfig.fetch_method
                ?: FetchMethod.HttpClient).createStrategy(
                this@CrawlerAgentTask
            )

            try {
                val loopIterations = AtomicInteger(0)
                val maxDepthConfig = typeConfig.max_depth ?: (typeConfig.max_depth ?: 3)
                val maxQueueSizeConfig = typeConfig.max_queue_size ?: (typeConfig.max_queue_size ?: 100)
                log.debug("Starting crawling loop: maxPages=$maxPages, maxErrors=$maxErrors, maxIterations=${1000}")
                while (shouldContinue(pageQueue, maxPages, errorCount, maxErrors, loopIterations, activeTasks)) {
                    if (loopIterations.get() % 10 == 0) {
                        synchronized(pageQueueLock) {
                            log.info("Loop iteration ${loopIterations.get()}: completed=${pageQueue.count { it.completed }}, active=${activeTasks.size}, errors=${errorCount.get()}")
                        }
                    }
                    val queueStats = synchronized(pageQueueLock) {
                        "total=${pageQueue.size}, completed=${pageQueue.count { it.completed }}, started=${pageQueue.count { it.started }}, active=${activeTasks.size}"
                    }
                    // Queue new tasks while we have capacity and unstarted pages
                    while (
                        activeTasks.size < concurrentProcessing && // Limit concurrent tasks
                        synchronized(pageQueueLock) { pageQueue.count { !it.started } > 0 } && // There are still unstarted pages
                        errorCount.get() < maxErrors && // Not too many errors
                        pageQueue.count { it.completed } < maxPages // Haven't hit max pages yet
                    ) {
                        addCrawlTask(
                            queueStats = queueStats,
                            completionService = completionService,
                            activeTasks = activeTasks,
                            errorCount = errorCount,
                            maxErrors = maxErrors,
                            pageQueue = pageQueue,
                            task = task,
                            tabs = tabs,
                            processedCount = processedCount,
                            maxPages = maxPages,
                            maxDepth = maxDepthConfig,
                            maxQueueSize = maxQueueSizeConfig,
                            webSearchDir = webSearchDir,
                            agent = agent,
                            fetchStrategy = fetchStrategy,
                            orchestrationConfig = orchestrationConfig,
                            analysisResultsMap = analysisResultsMap
                        )
                    }

                    // Wait for at least one task to complete before checking the queue again
                    // This allows in-progress tasks to add new links to the queue
                    if (activeTasks.isNotEmpty()) {
                        try {
                            val future = completionService.poll(1, java.util.concurrent.TimeUnit.SECONDS)
                            if (future != null) {
                                future.get() // This will throw if the task failed
                            } else {
                                while (activeTasks.isNotEmpty()) sleep(1000)
                            }
                        } catch (e: Exception) {
                            log.error("Task execution failed", e)
                        }
                    } else {
                        // No active tasks, check if there are unstarted pages we missed
                        val unstartedCount = synchronized(pageQueueLock) { pageQueue.count { !it.started } }
                        if (unstartedCount > 0) {
                            log.warn("No active tasks but $unstartedCount unstarted pages remain - continuing")
                            continue
                        }
                    }

                    val completedCount = synchronized(pageQueueLock) { pageQueue.count { it.completed } }
                    log.info("Crawling progress: completed=$completedCount/${pageQueue.size}, active_tasks=${activeTasks.size}, errors=${errorCount.get()}/$maxErrors")
                    //while (activeTasks.isNotEmpty()) sleep(1000)
                }
                if (loopIterations.get() >= 1000) {
                    log.warn("Reached maximum iteration limit: ${1000}")
                }
            } catch (e: Exception) {
                log.error("Error during processing", e)
                task.error(e)
            } finally {
                log.info("Crawling phase completed, cleaning up resources")
            }
            val totalTime = System.currentTimeMillis() - startTime
            log.info("CrawlerAgentTask completed: total_time=${totalTime}ms, pages_processed=${processedCount.get()}, errors=${errorCount.get()}, success_rate=${if (processedCount.get() > 0) ((processedCount.get() - errorCount.get()) * 100 / processedCount.get()) else 0}%")
            task.complete("Completed in ${totalTime / 1000} seconds, processed ${processedCount.get()} pages with ${errorCount.get()} errors.")

            val analysisResults = (1..processedCount.get()).asSequence().mapNotNull {
                analysisResultsMap[it]
            }.joinToString("\n")
            if (analysisResults.isBlank()) {
                val errorMessage = "No content was successfully processed. Check logs for errors."
                log.error(errorMessage)
                log.error("Processing stats: total_attempted=${processedCount.get()}, errors=${errorCount.get()}, queue_size=${pageQueue.size}")
                return errorMessage
            }

            val summaryTask = task.ui.newTask(false)
            tabs["Final Summary"] = summaryTask.placeholder
            val finalOutput =
                if (typeConfig.create_final_summary != false && analysisResults.length > typeConfig.max_final_output_size ?: 15000) {
                    log.info("Creating final summary: original_size=${analysisResults.length}, max_size=${typeConfig.max_final_output_size ?: 15000}")
                    try {
                        createFinalSummary(analysisResults, summaryTask)
                    } catch (e: Exception) {
                        log.error("Failed to create final summary, using truncated results", e)
                        analysisResults.substring(
                            0, minOf(
                                analysisResults.length,
                                typeConfig.max_final_output_size ?: 15000
                            )
                        ) +
                                "\n\n---\n\n*Note: Summary generation failed, showing truncated results*"
                    }
                } else {
                    log.info("Using analysis results directly: size=${analysisResults.length}")
                    analysisResults
                }
            try {
                summaryTask.add(finalOutput.renderMarkdown)
                task.update()
            } catch (e: Exception) {
                log.error("Failed to update task with final summary", e)
            }
            log.info("CrawlerAgentTask finished successfully, final output size: ${finalOutput.length}")
            return finalOutput
        } catch (e: Throwable) {
            log.error("Unhandled exception in CrawlerAgentTask", e)
            task.error(e)
            return "Error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}"
        }
    }

    fun addToQueue(
        pageQueue: MutableList<LinkData>,
        newLink: LinkData,
        maxDepth: Int,
        maxQueueSize: Int
    ): Boolean = synchronized(pageQueueLock) {
        if (newLink.link.isNullOrBlank()) {
            log.warn("Attempted to add invalid or empty URL to queue: $newLink")
            return false
        }
        if (pageQueue.size >= maxQueueSize) {
            log.warn("Page queue has reached maximum size of $maxQueueSize, cannot add more links")
            return false
        }
        if (newLink.depth > maxDepth) {
            log.debug("Skipping link due to depth limit (depth=${newLink.depth} > maxDepth=$maxDepth): ${newLink.link}")
            return false
        }
        if (pageQueue.any { it.link == newLink.link }) {
            log.debug("Skipping duplicate link already in queue: ${newLink.link}")
            return false
        }
        pageQueue.add(newLink)
        log.debug("Added new link to queue: ${newLink.link} (depth=${newLink.depth})")
        true
    }

    fun getNextPage(pageQueue: MutableList<LinkData>): LinkData? = synchronized(pageQueueLock) {
        val nextPage = pageQueue.firstOrNull { !it.started && !it.completed }
        nextPage?.let {
            it.started = true
        }
        nextPage
    }

    private fun shouldContinue(
        pageQueue: MutableList<LinkData>,
        maxPages: Int,
        errorCount: AtomicInteger,
        maxErrors: Int,
        loopIterations: AtomicInteger,
        activeTasks: MutableSet<String>
    ): Boolean = synchronized(pageQueueLock) {
        val completed = pageQueue.count { it.completed }
        val unstarted = pageQueue.count { !it.started }
        val hasActiveTasks = activeTasks.isNotEmpty()

        // Continue if:
        // 1. We have active tasks (they might add more links), OR
        // 2. We have unstarted pages in the queue
        // AND we haven't hit our limits
        val shouldContinue = (hasActiveTasks || unstarted > 0) &&
                completed < maxPages &&
                errorCount.get() < maxErrors &&
                loopIterations.getAndIncrement() < 1000

        if (!shouldContinue) {
            log.info("Stopping crawl: completed=$completed/$maxPages, unstarted=$unstarted, active=$hasActiveTasks, errors=${errorCount.get()}/$maxErrors")
        }

        shouldContinue
    }

    private fun addCrawlTask(
        queueStats: String,
        completionService: CompletionService<Unit>,
        activeTasks: MutableSet<String>,
        errorCount: AtomicInteger,
        maxErrors: Int,
        pageQueue: MutableList<LinkData>,
        task: SessionTask,
        tabs: TabbedDisplay,
        processedCount: AtomicInteger,
        maxPages: Int,
        maxDepth: Int,
        maxQueueSize: Int,
        webSearchDir: File,
        agent: TaskOrchestrator,
        fetchStrategy: FetchStrategy,
        orchestrationConfig: OrchestrationConfig,
        analysisResultsMap: ConcurrentHashMap<Int, String>
    ): Boolean {
        log.info("Status before queuing next page: $queueStats, active_tasks=${activeTasks.size}, errors=${errorCount.get()}/$maxErrors")
        val page = getNextPage(pageQueue) ?: return true
        if (page.link.isNullOrBlank()) {
            log.error("Invalid page link encountered: $page")
            errorCount.incrementAndGet()
            synchronized(pageQueueLock) {
                page.completed = true
                page.error = "Invalid or empty URL"
            }
            return false
        }
        activeTasks.add(page.link)

        log.info("Queuing page for processing: url='${page.link}', title='${page.title}', depth=${page.depth}, relevance=${page.relevance_score}")

        val subTask = try {
            task.ui.newTask(false).apply {
                tabs[page.link] = placeholder
                task.update()
            }
        } catch (e: Exception) {
            log.error("Failed to create subtask for URL: ${page.link}", e)
            errorCount.incrementAndGet()
            synchronized(pageQueueLock) {
                page.completed = true
                page.error = "Failed to create subtask: ${e.message}"
            }
            return false
        }

        completionService.submit({
            try {
                crawlPage(
                    processedCount,
                    page.link,
                    page,
                    maxPages,
                    maxDepth,
                    maxQueueSize,
                    webSearchDir,
                    agent,
                    fetchStrategy,
                    orchestrationConfig,
                    pageQueue,
                    errorCount,
                    subTask,
                    analysisResultsMap
                )
            } catch (e: Exception) {
                log.error("Uncaught exception in page processing task for: ${page.link}", e)
                errorCount.incrementAndGet()
                synchronized(pageQueueLock) {
                    page.completed = true
                    page.error = "Uncaught exception: ${e.message}"
                }
            } finally {
                activeTasks.remove(page.link)
            }
        })
        return false
    }

    private fun crawlPage(
        processedCount: AtomicInteger,
        link: String,
        page: LinkData,
        maxPages: Int,
        maxDepth: Int,
        maxQueueSize: Int,
        webSearchDir: File,
        agent: TaskOrchestrator,
        fetchStrategy: FetchStrategy,
        orchestrationConfig: OrchestrationConfig,
        pageQueue: MutableList<LinkData>,
        errorCount: AtomicInteger,
        task: SessionTask,
        analysisResultsMap: ConcurrentHashMap<Int, String>
    ) {
        val pageStartTime = System.currentTimeMillis()
        log.info("Starting to process page ${processedCount.get() + 1}: url='${link}', title='${page.title}'")
        val currentIndex = processedCount.incrementAndGet()
        if (currentIndex > maxPages) {
            log.warn("Max pages limit ($maxPages) reached, stopping processing for page: ${link}")
        } else {
            try {
                val url = link
                val title = page.title
                val processPageResult =
                    buildString {
                        this.appendLine("## ${currentIndex}. [${title}]($url)")
                        this.appendLine()
                        try {
                            val content = fetchAndProcessUrl(
                                url,
                                webSearchDir = webSearchDir,
                                index = currentIndex,
                                pool = agent.pool,
                                fetchStrategy = fetchStrategy
                            )
                            log.debug("Fetched content for '$url': ${content.length} characters")
                            if (content.length < typeConfig.min_content_length ?: 500) {
                                log.info("Content too short for '$url': ${content.length} < ${typeConfig.min_content_length ?: 500} chars, skipping")
                                this.appendLine("*Content too short (${content.length} chars), skipping this result*")
                                this.appendLine()
                                return@buildString
                            }

                            val analysisGoal = when {
                                this@CrawlerAgentTask.executionConfig?.content_queries != null -> executionConfig.toJson()
                                this@CrawlerAgentTask.executionConfig?.task_description?.isNotBlank() == true -> executionConfig.toString()
                                else -> "Analyze the content and provide insights."
                            }
                            log.debug("Analyzing content for '$url' with goal: $analysisGoal")
                            val analysis: ParsedResponse<ParsedPage> =
                                transformContent(
                                    content,
                                    analysisGoal,
                                    orchestrationConfig,
                                    task
                                )

                            val parsedPage = analysis.obj
                            if (parsedPage.page_type == PageType.Error) {
                                log.warn("Analysis returned error for '$url': ${parsedPage.page_information}")
                                this.appendLine(
                                    "*Error processing this result: ${
                                        parsedPage.page_information?.let {
                                            JsonUtil.toJson(
                                                it
                                            )
                                        }
                                    }*"
                                )
                                this.appendLine()
                                saveAnalysis(webSearchDir.resolve("error").apply {
                                    mkdirs()
                                }, url, analysis, currentIndex)
                                return@buildString
                            }

                            if (parsedPage.page_type == PageType.Irrelevant) {
                                log.info("Content marked as irrelevant for '$url', skipping")
                                this.appendLine("*Irrelevant content, skipping this result*")
                                this.appendLine()
                                saveAnalysis(webSearchDir.resolve("irrelevant").apply {
                                    mkdirs()
                                }, url, analysis, currentIndex)
                                return@buildString
                            }
                            log.debug("Successfully analyzed content for '$url', saving results")

                            saveAnalysis(
                                webSearchDir = webSearchDir,
                                url = url,
                                analysis = analysis,
                                index = currentIndex
                            )

                            this.appendLine(analysis.text)
                            this.appendLine()

                            if (/*taskConfig?.follow_links ?:*/ typeConfig.follow_links == true) {

                                var linkData = parsedPage.link_data
                                val allowRevisit = /*taskConfig?.allow_revisit_pages ?:*/
                                    typeConfig.allow_revisit_pages == true
                                if (linkData.isNullOrEmpty()) {
                                    linkData = extractLinksFromMarkdown(analysis.text)
                                    log.debug("Extracted ${linkData.size} links from markdown for '$url'")
                                } else {
                                    log.debug("Using ${linkData.size} structured links from analysis for '$url'")
                                }
                                val filteredLinks = linkData
                                    .take(10) // Limit links per page to prevent explosion
                                    .filter { link ->
                                        VALID_URL_PATTERN.matcher(link.link!!).matches() &&
                                                !isBlacklistedDomain(link.link) &&
                                                (allowRevisit || pageQueue.none { it.link == link.link })
                                    }

                                var addedCount = 0
                                linkData
                                    .take(10)
                                    .filter { link ->
                                        VALID_URL_PATTERN.matcher(link.link ?: "").matches() &&
                                                !isBlacklistedDomain(link.link!!)
                                    }
                                    .forEach { link ->
                                        val newLink = link.apply { depth = page.depth + 1 }
                                        if (addToQueue(pageQueue, newLink, maxDepth, maxQueueSize)) {
                                            addedCount++
                                        }
                                    }
                                log.info("Added $addedCount new links to queue from '$url' (filtered from ${linkData.size} total)")
                            }
                        } catch (e: Exception) {
                            log.error("Error processing URL: $url", e)
                            errorCount.incrementAndGet()
                            synchronized(pageQueueLock) {
                                page.error = e.message
                            }
                            this.appendLine("*Error processing this result: ${e.message}*")
                            this.appendLine()
                        }
                    }
                task.add(processPageResult.renderMarkdown)
                analysisResultsMap[currentIndex] = processPageResult
                log.info("Successfully processed page ${currentIndex}: url='${link}', processing_time=${System.currentTimeMillis() - pageStartTime}ms")
            } catch (e: Exception) {
                task.error(e)
                log.error("Error processing page: ${link}", e)
                errorCount.incrementAndGet()
                synchronized(pageQueueLock) {
                    page.error = e.message
                }
                analysisResultsMap[currentIndex] =
                    "## ${currentIndex}. [${page.title}](${link})\n\n*Error processing this result: ${e.message}*\n\n"
            } finally {
                synchronized(pageQueueLock) {
                    page.processingTimeMs = System.currentTimeMillis() - pageStartTime
                    page.completed = true
                }
                log.debug("Page processing completed: url='${link}', time=${page.processingTimeMs}ms, error='${page.error ?: "none"}'")
            }
        }
    }

    private fun isBlacklistedDomain(url: String): Boolean {
        val blacklistedDomains = setOf(
            "facebook.com", "twitter.com", "instagram.com", "linkedin.com",
            "youtube.com", "tiktok.com", "pinterest.com", "reddit.com",
            "amazon.com", "ebay.com", "aliexpress.com"
        )
        return try {
            val uri = URI.create(url)
            val domain = uri.host?.lowercase()
            if (domain == null) {
                log.warn("Could not extract domain from URL: $url")
                return true
            }
            blacklistedDomains.any { domain?.contains(it) == true }
        } catch (e: Exception) {
            log.warn("Invalid URL format: $url", e)
            true // Blacklist invalid URLs
        }
    }

    private fun createFinalSummary(analysisResults: String, task: SessionTask): String {
        log.info("Creating final summary of analysis results (original size: ${analysisResults.length})")

        if (analysisResults.length < (typeConfig.max_final_output_size ?: 15000) * 1.2) {
            log.info("Analysis results only slightly exceed max size, truncating instead of summarizing")
            return analysisResults.substring(
                0,
                min(analysisResults.length, typeConfig.max_final_output_size ?: 15000)
            ) + "\n\n---\n\n*Note: Some content has been truncated due to length limitations.*"
        }

        val headerEndIndex = analysisResults.indexOf("## 1. [")
        val header = if (headerEndIndex > 0) {
            analysisResults.substring(0, headerEndIndex)
        } else {
            "# Web Search: ${executionConfig?.search_query ?: executionConfig?.direct_urls?.joinToString(", ") ?: ""}\n\n"
        }

        val urlSections = extractUrlSections(analysisResults)
        log.info("Extracted ${urlSections.size} URL sections for summarization")
        val summary = ChatAgent(
            prompt = listOf(
                "Create a comprehensive summary of the following web search results and analyses.",
                "Original analysis contained ${urlSections.size} web pages related to: ${executionConfig?.search_query ?: ""}",
                "Analysis goal: ${executionConfig?.content_queries ?: executionConfig?.task_description ?: "Provide key insights"}",
                "For each source, extract the most important insights, facts, and conclusions.",
                "Organize information by themes rather than by source when possible.",
                "Use markdown formatting with headers, bullet points, and emphasis where appropriate.",
                "Include the most important links that should be followed up on.",
                "Keep your response under ${(typeConfig.max_final_output_size ?: 15000) / 1000}K characters."
            ).joinToString("\n\n"),
            model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
                ?: orchestrationConfig.parsingChatter).getChildClient(task),
        ).answer(
            listOf(
                "Here are summaries of each analyzed page:\n${analysisResults}"
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
        if (url.isBlank()) {
            throw IllegalArgumentException("URL cannot be blank")
        }


        if (!(typeConfig.allow_revisit_pages == true) && urlContentCache.containsKey(url)) {
            log.debug("Using cached content for URL: $url (cache size: ${urlContentCache.size})")
            return urlContentCache[url]!!
        }
        log.debug(
            "Fetching content for URL: {} using method: {}",
            url,
            typeConfig.fetch_method ?: FetchMethod.HttpClient
        )

        return try {
            val content = fetchStrategy.fetch(url, webSearchDir, index, pool, orchestrationConfig)
            // Cache successful fetches
            if (content.isNotBlank()) {
                urlContentCache[url] = content
                log.debug("Cached content for URL: $url (content length: ${content.length}, cache size: ${urlContentCache.size})")
            } else {
                log.warn("Fetched empty content for URL: $url")
            }
            content
        } catch (e: Exception) {
            log.error("Failed to fetch URL: $url - ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }

    private fun extractLinksFromMarkdown(markdown: String): List<LinkData> {
        val links = mutableListOf<Pair<String, String>>()
        val matcher = LINK_PATTERN.matcher(markdown)
        var matchCount = 0
        while (matcher.find()) {
            matchCount++
            if (matchCount > 100) {
                log.warn("Too many links found in markdown (>100), stopping extraction")
                break
            }
            val linkText = matcher.group(1)
            val linkUrl = matcher.group(2)
            try {
                if (VALID_URL_PATTERN.matcher(linkUrl).matches()) {
                    links.add(Pair(linkText, linkUrl))
                } else {
                    log.debug("Skipping invalid URL in markdown: $linkUrl")
                }
            } catch (e: Exception) {
                log.warn("Invalid URL found in markdown: $linkUrl", e)
            }
        }
        log.debug("Extracted ${links.size} valid links from markdown")
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
            if (!webSearchDir.exists() && !webSearchDir.mkdirs()) {
                log.error("Failed to create directory: ${webSearchDir.absolutePath}")
                return
            }
            val extension = when {
                webSearchDir.name.contains("document") -> ".txt"
                webSearchDir.name.contains("text") -> ".txt"
                webSearchDir.name.contains("extracted_text") -> ".txt"
                else -> ".html"
            }
            val rawFile = File(webSearchDir, urlSafe + extension)
            // Ensure content is saved with proper encoding
            try {
                rawFile.writeText(content, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                log.error("Failed to write content to file: ${rawFile.absolutePath}", e)
                return
            }
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
                "query" to (executionConfig?.search_query ?: ""),
                "content_query" to (executionConfig?.content_queries ?: "")
            )
            val metadataJson = try {
                ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata)
            } catch (e: JsonProcessingException) {
                log.error("Failed to serialize metadata for URL: $url", e)
                "{}"
            }

            val objJson = try {
                analysis.obj.let { JsonUtil.toJson(it) }
            } catch (e: Exception) {
                log.error("Failed to serialize analysis object for URL: $url", e)
                ""
            }

            val contentWithHeader = "<!-- ${metadataJson}${objJson} -->\n\n${analysis.text}"
            analysisFile.writeText(contentWithHeader)
            log.debug("Saved analysis to file: ${analysisFile.absolutePath} (size: ${contentWithHeader.length} chars)")
        } catch (e: Exception) {
            log.error("Failed to save analysis for URL: $url", e)
        }
    }

    private fun transformContent(
        content: String,
        analysisGoal: String,
        orchestrationConfig: OrchestrationConfig,
        task: SessionTask
    ): ParsedResponse<ParsedPage> {
        val describer = TaskContextYamlDescriber(orchestrationConfig)
        val maxChunkSize = 50000
        if (content.length <= maxChunkSize) {
            log.debug("Content size (${content.length}) within limit, processing as single chunk")
            return pageParsedResponse(orchestrationConfig, analysisGoal, content, describer, task)
        }

        log.debug("Content size (${content.length}) exceeds limit, splitting into chunks")
        val chunks = splitContentIntoChunks(content, maxChunkSize)
        log.debug("Split content into ${chunks.size} chunks")
        val chunkResults = chunks.mapIndexed { index, chunk ->
            log.debug("Processing chunk ${index + 1}/${chunks.size} (size: ${chunk.length})")
            val chunkGoal = "$analysisGoal (Part ${index + 1}/${chunks.size})"
            pageParsedResponse(orchestrationConfig, chunkGoal, chunk, describer, task)
        }
        if (chunkResults.size == 1) {
            log.debug("Only one chunk result, returning directly")
            return chunkResults[0]
        }
        log.debug("Combining ${chunkResults.size} chunk results into final analysis")
        val combinedAnalysis = chunkResults.joinToString("\n\n---\n\n") { it.text }
        return pageParsedResponse(orchestrationConfig, analysisGoal, combinedAnalysis, describer, task)
    }

    private fun pageParsedResponse(
        orchestrationConfig: OrchestrationConfig,
        analysisGoal: String,
        content: String,
        describer: TypeDescriber,
        task: SessionTask
    ) = try {
        val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
            ?: orchestrationConfig.parsingChatter).getChildClient(task)
        ParsedAgent(
            prompt = listOf(
                "Below are analyses of different parts of a web page related to this goal: $analysisGoal",
                "Create a unified summary that combines the key insights from all parts.",
                "Use markdown formatting for your response, with * characters for bullets.",
                "Identify the most important links that should be followed up on according to the goal."
            ).joinToString("\n\n"),
            resultClass = ParsedPage::class.java,
            model = model,
            describer = describer,
            parsingModel = model,
        ).answer(listOf(content))
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