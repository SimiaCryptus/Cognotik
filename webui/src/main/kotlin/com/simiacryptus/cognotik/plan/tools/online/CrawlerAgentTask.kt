package com.simiacryptus.cognotik.plan.tools.online

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.online.fetch.FetchMethod
import com.simiacryptus.cognotik.plan.tools.online.fetch.FetchStrategy
import com.simiacryptus.cognotik.plan.tools.online.processing.PageProcessingStrategy
import com.simiacryptus.cognotik.plan.tools.online.processing.PageProcessingStrategy.PageProcessingResult
import com.simiacryptus.cognotik.plan.tools.online.processing.PageProcessingStrategy.ProcessingContext
import com.simiacryptus.cognotik.plan.tools.online.processing.ProcessingStrategyType
import com.simiacryptus.cognotik.plan.tools.online.seed.SeedMethod
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
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
import kotlin.random.Random

class CrawlerAgentTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: CrawlerTaskExecutionConfigData?,
) : AbstractTask<CrawlerAgentTask.CrawlerTaskExecutionConfigData, CrawlerAgentTask.CrawlerTaskTypeConfig>(
    orchestrationConfig, planTask
) {

    class CrawlerTaskTypeConfig(
        @Description("Method to seed the crawler (optional)") val seed_method: SeedMethod? = SeedMethod.GoogleProxy,
        @Description("Method used to fetch content from  URLs (optional)") val fetch_method: FetchMethod? = FetchMethod.HttpClient,
        @Description("Strategy for processing pages (optional)") val processing_strategy: ProcessingStrategyType? = ProcessingStrategyType.DefaultSummarizer,
        @Description("Whitespace-separated list of allowed domains/URL prefixes to restrict crawling (optional)") val allowed_domains: String? = null,
        @Description("Respect robots.txt rules when crawling (default: true)") val respect_robots_txt: Boolean? = true,
        @Description("Maximum number of pages to process in a single task") val max_pages_per_task: Int? = null,
        @Description("Maximum depth to crawl from seed pages") val max_depth: Int? = null,
        @Description("Maximum queue size to prevent memory issues") val max_queue_size: Int? = null,
        @Description("Number of pages to process concurrently") val concurrent_page_processing: Int? = null,
        @Description("Maximum characters in final summary") val max_final_output_size: Int? = null,
        @Description("Minimum content length to process") val min_content_length: Int? = null,
        @Description("Automatically follow links found in analyzed pages") val follow_links: Boolean? = null,
        @Description("Allow crawling the same page multiple times") val allow_revisit_pages: Boolean? = null,
        @Description("Generate a comprehensive summary of all results") val create_final_summary: Boolean? = null,
        @Description("Generate a detailed transcript of the crawling session") val generate_transcript: Boolean? = true,
        task_type: String = "CrawlerAgent",
        model: ApiChatModel? = null,
        name: String? = task_type,
    ) : TaskTypeConfig(task_type = task_type, name = name, model = model), ValidatedObject {
        override fun validate(): String? {
            if (max_pages_per_task != null && max_pages_per_task <= 0) {
                return "max_pages_per_task must be greater than 0"
            }
            if (max_depth != null && max_depth < 0) {
                return "max_depth must be non-negative"
            }
            if (max_queue_size != null && max_queue_size <= 0) {
                return "max_queue_size must be greater than 0"
            }
            if (concurrent_page_processing != null && concurrent_page_processing <= 0) {
                return "concurrent_page_processing must be greater than 0"
            }
            if (max_final_output_size != null && max_final_output_size <= 0) {
                return "max_final_output_size must be greater than 0"
            }
            if (min_content_length != null && min_content_length < 0) {
                return "min_content_length must be non-negative"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    class CrawlerTaskExecutionConfigData(
        @Description("The search query to use for Google search") val search_query: String? = null,
        @Description("Direct URLs to analyze (comma-separated)") val direct_urls: List<String>? = null,
        @Description("The query considered when processing the content - this should contain a detailed listing of the desired data, evaluation criteria, and filtering priorities used to transform the page into the desired summary") val content_queries: Any? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = CrawlerAgent.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (search_query.isNullOrBlank() && direct_urls.isNullOrEmpty()) {
                return "Either search_query or direct_urls must be provided"
            }

            if (!direct_urls.isNullOrEmpty()) {
                direct_urls.forEach { url ->
                    if (!url.matches(Regex("^(http|https)://.*"))) {
                        return "Invalid URL format in direct_urls: $url"
                    }
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    var selenium: Selenium2S3? = null
    val urlContentCache = ConcurrentHashMap<String, String>()
    private val robotsTxtParser = RobotsTxtParser()
    private val pageQueueLock = Object()
    private val pageQueue = java.util.PriorityQueue<LinkData>(compareByDescending { it.calculatePriority() })
    private val seenUrls = ConcurrentHashMap.newKeySet<String>()

    override fun promptSegment(): String {
        val str = buildString {
            appendLine("CrawlerAgent - Search Google, fetch top results, and analyze content")
            appendLine("** Specify the search query")
            appendLine("** Or provide direct URLs to analyze")
            appendLine("** Specify a detailed query/analysis prompt to guide content processing")
            appendLine("** Choose a processing strategy: DefaultSummarizer, FactChecking, or JobMatching")
            appendLine("** Results will be saved to .websearch directory for future reference")
            appendLine("** Links found in analysis can be automatically followed for deeper research")
            val typeConfig = this@CrawlerAgentTask.typeConfig
            if (null != typeConfig) {
                when (typeConfig.processing_strategy) {
                    ProcessingStrategyType.DefaultSummarizer -> {
                        // No additional notes for DefaultSummarizer
                    }

                    else -> {
                        appendLine(
                            "** Using processing strategy: ${typeConfig.processing_strategy?.name} - ${
                                typeConfig.processing_strategy?.createStrategy()?.description?.indent(
                                    "  "
                                )
                            }"
                        )
                    }
                }
            }
        }
        return str
    }

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
        @Description("The URL of the link to crawl") var url: String? = null,
        @Description("The title of the link (optional)") val title: String? = null,
        @Description("Tags associated with the link (optional)") val tags: List<String>? = null,
        @Description("1-100") val relevance_score: Double = 100.0
    ) : ValidatedObject {
        var started: Boolean = false
        var completed: Boolean = false
        var depth: Int = 0
        var error: String? = null
        var processingTimeMs: Long = 0

        override fun validate(): String? {
            if (url.isNullOrBlank()) {
                return "link cannot be null or blank"
            }
            if (false == url?.matches(Regex("^(http|https)://.*"))) {
                url = "https://$url"
            }
            if (relevance_score < 1.0 || relevance_score > 100.0) {
                return "relevance_score must be between 1 and 100"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    // Priority calculation: higher relevance and lower depth = higher priority
    fun LinkData.calculatePriority(): Double = relevance_score // / (depth + 1.0)

    enum class PageType {
        Error, Irrelevant, OK
    }

    data class ParsedPage(
        val page_type: PageType = PageType.OK,
        val page_information: Any? = null,
        val tags: List<String>? = null,
        val link_data: List<LinkData>? = null,
    ) : ValidatedObject {
        override fun validate(): String? {
            link_data?.forEach { linkData ->
                linkData.validate()?.let { return it }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        log.info("Starting CrawlerAgentTask.run() with messages count: ${messages.size}")
        var transcriptStream: FileOutputStream? = null
        try {
            transcriptStream = if (typeConfig?.generate_transcript != false) {
                try {
                    task.transcript("crawler_transcript")
                } catch (e: Exception) {
                    log.error("Failed to initialize transcript", e)
                    null
                }
            } else null
            val chatInterface = (
                    typeConfig?.model?.let { this@CrawlerAgentTask.orchestrationConfig.instance(it) }
                        ?: this@CrawlerAgentTask.defaultFast
                    ).getChildClient(task)
            resultFn(innerRun(agent, messages, task, orchestrationConfig, transcriptStream, chatInterface))
        } catch (e: Throwable) {
            log.error("Unhandled exception in CrawlerAgentTask", e)
            val errorMessage = "Error: ${e.message ?: "Unknown error occurred"}"
            resultFn(errorMessage)
            task.error(e)
        } finally {
            transcriptStream?.let { stream ->
                try {
                    stream.close()
                    log.debug("Transcript stream closed in run() finally block")
                } catch (e: Exception) {
                    log.error("Failed to close transcript stream", e)
                }
            }
            cleanup()
        }
    }

    private fun innerRun(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        orchestrationConfig: OrchestrationConfig,
        transcriptStream: FileOutputStream?,
        chatInterface: ChatInterface
    ): String {
        try {
            val typeConfig = typeConfig ?: throw RuntimeException("Missing type config")
            // Initialize processing strategy
            val strategyType = typeConfig.processing_strategy ?: ProcessingStrategyType.DefaultSummarizer
            val processingStrategy = strategyType.createStrategy()
            log.info("Using processing strategy: ${strategyType.name} - ${processingStrategy.javaClass.simpleName}")

            val startTime = System.currentTimeMillis()
            log.info(
                "Starting CrawlerAgentTask with config: search_query='${executionConfig?.search_query}', direct_urls='${
                    executionConfig?.direct_urls?.joinToString(
                        ", "
                    ) ?: ""
                }', max_pages=${typeConfig.max_pages_per_task ?: typeConfig.max_pages_per_task ?: 30}"
            )
            val webSearchDir = File(agent.root.toFile(), ".websearch")
            if (!webSearchDir.exists()) {
                if (!webSearchDir.mkdirs()) {
                    log.error("Failed to create websearch directory: ${webSearchDir.absolutePath}")
                    return "Error: Failed to create output directory"
                }
                log.debug("Created websearch directory: ${webSearchDir.absolutePath}")
            }
            val tabs = TabbedDisplay(task)
            val crawlTask = task.linkedTask("Crawl Details")
            val crawlTabs = TabbedDisplay(crawlTask)
            task.update()
            transcriptStream?.let { stream ->
                writeTranscriptHeader(stream)
            }

            val seedMethod = when {
                !executionConfig?.direct_urls.isNullOrEmpty() -> SeedMethod.DirectUrls
                typeConfig.seed_method != null -> typeConfig.seed_method
                !executionConfig?.search_query.isNullOrBlank() -> SeedMethod.GoogleProxy
                else -> {
                    log.error("No seed method specified and no search query or direct URLs provided")
                    return "Error: No seed method specified and no search query or direct URLs provided"
                }
            }
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
            // Create seed links tab
            val seedLinksTask = crawlTask.newTask()
            crawlTabs["Seed Links"] = seedLinksTask.placeholder
            val seedLinksContent = buildString {
                appendLine("# Seed Links")
                appendLine()
                appendLine("**Method:** ${seedMethod.name}")
                appendLine()
                appendLine("**Total Seeds:** ${seedItems.size}")
                appendLine()
                appendLine("---")
                appendLine()
                seedItems.forEachIndexed { index, item ->
                    appendLine("## ${index + 1}. [${item.title}](${item.link})")
                    appendLine()
                    appendLine("- **URL:** ${item.link}")
                    appendLine("- **Relevance Score:** ${item.relevance_score}")
                    if (!item.tags.isNullOrEmpty()) {
                        appendLine("- **Tags:** ${item.tags.joinToString(", ")}")
                    }
                    appendLine()
                }
            }
            seedLinksTask.add(seedLinksContent.renderMarkdown)
            seedLinksTask.complete()
            // Log seed links to transcript
            transcriptStream?.let { stream ->
                writeToTranscript(stream, "## Seed Links\n\n$seedLinksContent\n\n")
            }


            synchronized(pageQueueLock) {
                seedItems.forEach { item ->
                    if (isBlacklistedDomain(item.link)) {
                        log.info("Skipping blacklisted seed URL: ${item.link}")
                        return@forEach
                    }
                    if (typeConfig.respect_robots_txt == true && !robotsTxtParser.isAllowed(item.link)) {
                        log.info("Skipping seed URL disallowed by robots.txt: ${item.link}")
                        return@forEach
                    }
                    LinkData(
                        url = item.link, title = item.title, tags = item.tags, relevance_score = item.relevance_score
                    ).let { linkData ->
                        log.debug("Adding seed item to page queue: {}", linkData)
                        if (!addToQueue(
                                linkData, typeConfig.max_depth ?: 3, typeConfig.max_queue_size ?: 100
                            )
                        ) {
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
            val maxPages = typeConfig.max_pages_per_task ?: typeConfig.max_pages_per_task ?: 30
            val concurrentProcessing = /*taskConfig?.concurrent_page_processing ?:*/
                typeConfig.concurrent_page_processing ?: 3
            log.info("Processing configuration: maxPages=$maxPages, concurrentProcessing=$concurrentProcessing")
// Create processing context
            val processingContext = ProcessingContext(
                executionConfig = executionConfig ?: throw RuntimeException("Missing execution config"),
                typeConfig = typeConfig,
                orchestrationConfig = orchestrationConfig,
                messages = messages,
                task = crawlTask,
                webSearchDir = webSearchDir,
                processedCount = AtomicInteger(0),
                maxPages = maxPages,
                transcriptStream = transcriptStream
            )
            // Track all page results for strategy
            val allPageResults = ConcurrentHashMap<Int, PageProcessingResult>()


            val completionService: CompletionService<Unit> = ExecutorCompletionService(agent.pool)
            val activeTasks = ConcurrentHashMap.newKeySet<String>()
            val processedCount = AtomicInteger(0)
            val errorCount = AtomicInteger(0)
            val maxErrors = maxPages / 2 // Stop if too many errors
            log.info("Starting crawling loop with maxErrors threshold: $maxErrors")
            val fetchStrategy =
                (this@CrawlerAgentTask.typeConfig?.fetch_method ?: FetchMethod.HttpClient).createStrategy(
                    this@CrawlerAgentTask
                )

            try {
                val loopIterations = AtomicInteger(0)
                val maxDepthConfig = typeConfig.max_depth ?: 3
                val maxQueueSizeConfig = typeConfig.max_queue_size ?: 100
                log.debug("Starting crawling loop: maxPages=$maxPages, maxErrors=$maxErrors, maxIterations=${1000}")
                while (shouldContinue(maxPages, errorCount, maxErrors, loopIterations, activeTasks)) {
                    if (loopIterations.get() % 10 == 0) {
                        synchronized(pageQueueLock) {
                            log.info("Loop iteration ${loopIterations.get()}: queue_size=${pageQueue.size}, active=${activeTasks.size}, errors=${errorCount.get()}")
                        }
                    }
                    val queueStats = synchronized(pageQueueLock) {
                        "queue_size=${pageQueue.size}, seen=${seenUrls.size}, active=${activeTasks.size}"
                    }
                    // Queue new tasks while we have capacity and unstarted pages
                    while (activeTasks.size < concurrentProcessing && // Limit concurrent tasks
                        synchronized(pageQueueLock) { pageQueue.isNotEmpty() } && // There are still unstarted pages
                        errorCount.get() < maxErrors && // Not too many errors
                        processedCount.get() < maxPages // Haven't hit max pages yet
                    ) {
                        addCrawlTask(
                            queueStats = queueStats,
                            activeTasks = activeTasks,
                            errorCount = errorCount,
                            maxErrors = maxErrors,
                            tabs = crawlTabs,
                            processedCount = processedCount,
                            maxPages = maxPages,
                            maxDepth = maxDepthConfig,
                            maxQueueSize = maxQueueSizeConfig,
                            webSearchDir = webSearchDir,
                            agent = agent,
                            fetchStrategy = fetchStrategy,
                            analysisResultsMap = analysisResultsMap,
                            transcriptStream = transcriptStream,
                            processingStrategy = processingStrategy,
                            processingContext = processingContext,
                            allPageResults = allPageResults
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
                        val unstartedCount = synchronized(pageQueueLock) { pageQueue.size }
                        if (unstartedCount > 0) {
                            log.warn("No active tasks but $unstartedCount unstarted pages remain - continuing")
                            continue
                        }
                    }

                    log.info("Crawling progress: processed=${processedCount.get()}/$maxPages, queue=${pageQueue.size}, active_tasks=${activeTasks.size}, errors=${errorCount.get()}/$maxErrors")

                    // Check if strategy wants to terminate early
                    val continuationDecision = processingStrategy.shouldContinueCrawling(
                        allPageResults.values.toList(), processingContext
                    )
                    if (!continuationDecision.shouldContinue) {
                        log.info("Strategy requested early termination: ${continuationDecision.reason}")
                        break
                    }
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
            // Add page queue details tab
            addPageQueueDetailsTab(tabs, processedCount.get(), errorCount.get())

            task.complete("Completed in ${totalTime / 1000} seconds, processed ${processedCount.get()} pages with ${errorCount.get()} errors.")
            // Write completion stats to transcript
            transcriptStream?.let { stream ->
                try {
                    writeTranscriptFooter(stream, totalTime, processedCount.get(), errorCount.get())
                } catch (e: Exception) {
                    log.error("Failed to write transcript footer", e)
                }
            }

            val analysisResults = (1..processedCount.get()).asSequence().mapNotNull {
                analysisResultsMap[it]
            }.joinToString("\n")
            if (analysisResults.isBlank()) {
                val errorMessage = "No content was successfully processed. Check logs for errors."
                log.error(errorMessage)
                log.error("Processing stats: total_attempted=${processedCount.get()}, errors=${errorCount.get()}, queue_size=${pageQueue.size}")
                return errorMessage
            }

            // Use strategy to generate final output
            val finalOutput = try {
                log.info("Generating final output using strategy: ${strategyType.name}")
                buildString {
                    appendLine("# Final Output")
                    appendLine(
                        processingStrategy.generateFinalOutput(
                            allPageResults.values.toList(),
                            processingContext
                        )
                    )
                    appendLine("# Remaining Queue")
                    synchronized(pageQueueLock) {
                        if (pageQueue.isEmpty()) {
                            appendLine("No remaining pages in the queue.")
                        } else {
                            appendLine("The following pages were not processed:")
                            var index = 1
                            pageQueue.toList().sortedBy { -it.calculatePriority() }.forEach { linkData ->
                                appendLine("${index++}. [${linkData.title ?: linkData.url}](${linkData.url}), Relevance Score: ${linkData.relevance_score}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to generate final output using strategy, falling back to basic summary", e)
                if (typeConfig.create_final_summary != false && analysisResults.length > (typeConfig.max_final_output_size
                        ?: 15000)
                ) {
                    createFinalSummary(analysisResults, chatInterface)
                } else {
                    analysisResults
                }
            }

            try {
                task.update()
                // Write final summary to transcript
                transcriptStream?.let { stream ->
                    try {
                        writeToTranscript(stream, "\n\n## Final Summary\n\n$finalOutput\n\n")
                    } catch (e: Exception) {
                        log.error("Failed to write final summary to transcript", e)
                    }
                }
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

    private fun writeTranscriptHeader(stream: FileOutputStream) {
        try {
            val header = buildString {
                appendLine("# Crawler Agent Transcript")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("**Search Query:** ${executionConfig?.search_query ?: "N/A"}")
                appendLine()
                appendLine("**Direct URLs:** ${executionConfig?.direct_urls?.joinToString(", ") ?: "N/A"}")
                appendLine()
                appendLine("<details><summary>Execution Configuration (click to expand)</summary>\n")
                appendLine()
                appendLine(executionConfig?.content_queries?.toJson()?.let { "\n```json\n${it.indent()}\n```" }
                    ?: "N/A")
                appendLine()
                appendLine("</details>")
                appendLine()
                appendLine("---")
                appendLine()
            }
            stream.write(header.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
        } catch (e: Exception) {
            if (e !is java.io.IOException || e.message?.contains("closed") != true) {
                log.error("Failed to write transcript header", e)
            }
        }
    }

    private fun writeTranscriptFooter(stream: FileOutputStream, totalTime: Long, processedCount: Int, errorCount: Int) {
        try {
            val footer = buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Crawling Session Summary")
                appendLine()
                appendLine(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine("**Total Time:** ${totalTime / 1000} seconds")
                appendLine("**Pages Processed:** $processedCount")
                appendLine("**Errors:** $errorCount")
                appendLine("**Success Rate:** ${if (processedCount > 0) ((processedCount - errorCount) * 100 / processedCount) else 0}%")
                appendLine()
            }
            stream.write(footer.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
        } catch (e: Exception) {
            if (e !is java.io.IOException || e.message?.contains("closed") != true) {
                log.error("Failed to write transcript footer", e)
            }
        }
    }

    fun addToQueue(
        newLink: LinkData, maxDepth: Int, maxQueueSize: Int
    ): Boolean = synchronized(pageQueueLock) {
        val typeConfig = typeConfig ?: throw RuntimeException("Missing type config")
        val newUrl = newLink.url
        if (newUrl.isNullOrBlank()) {
            log.warn("Attempted to add invalid or empty URL to queue: $newLink")
            return false
        }
        if (typeConfig.respect_robots_txt == true && !robotsTxtParser.isAllowed(newUrl)) {
            log.debug("Skipping URL disallowed by robots.txt: $newUrl")
            return false
        }
        if (pageQueue.size >= maxQueueSize) {
            log.warn("Page queue has reached maximum size of $maxQueueSize, cannot add more links")
            return false
        }
        if (newLink.depth > maxDepth) {
            log.debug("Skipping link due to depth limit (depth=${newLink.depth} > maxDepth=$maxDepth): $newUrl")
            return false
        }
        if (seenUrls.contains(newUrl)) {
            log.debug("Skipping duplicate link already in queue: $newUrl")
            return false
        }
        seenUrls.add(newUrl)
        pageQueue.add(
            newLink.copy(
                relevance_score = newLink.relevance_score.coerceIn(1.0, 100.0) + Random.nextInt(
                    -500,
                    500
                ) * 0.001 // Slight randomness to prevent priority ties
            )
        )
        log.debug("Added new link to queue: $newUrl (depth=${newLink.depth}, priority=${newLink.calculatePriority()})")
        true
    }

    fun getNextPage(): LinkData? = synchronized(pageQueueLock) {
        // Poll removes and returns the highest priority element
        val nextPage = pageQueue.poll()
        nextPage?.let {
            it.started = true
            log.debug("Retrieved next page from queue: ${it.url} (priority=${it.calculatePriority()}, remaining=${pageQueue.size})")
        }
        nextPage
    }

    private fun shouldContinue(
        maxPages: Int,
        errorCount: AtomicInteger,
        maxErrors: Int,
        loopIterations: AtomicInteger,
        activeTasks: MutableSet<String>
    ): Boolean = synchronized(pageQueueLock) {
        val completed = seenUrls.size - pageQueue.size - activeTasks.size
        val unstarted = pageQueue.size
        val hasActiveTasks = activeTasks.isNotEmpty()

        // Continue if:
        // 1. We have active tasks (they might add more links), OR
        // 2. We have unstarted pages in the queue
        // AND we haven't hit our limits
        val shouldContinue =
            (hasActiveTasks || unstarted > 0) && completed < maxPages && errorCount.get() < maxErrors && loopIterations.getAndIncrement() < 1000

        if (!shouldContinue) {
            log.info("Stopping crawl: completed=$completed/$maxPages, unstarted=$unstarted, active=$hasActiveTasks, errors=${errorCount.get()}/$maxErrors")
        }

        shouldContinue
    }

    private fun addCrawlTask(
        queueStats: String,
        activeTasks: MutableSet<String>,
        errorCount: AtomicInteger,
        maxErrors: Int,
        tabs: TabbedDisplay,
        processedCount: AtomicInteger,
        maxPages: Int,
        maxDepth: Int,
        maxQueueSize: Int,
        webSearchDir: File,
        agent: TaskOrchestrator,
        fetchStrategy: FetchStrategy,
        analysisResultsMap: ConcurrentHashMap<Int, String>,
        transcriptStream: FileOutputStream?,
        processingStrategy: PageProcessingStrategy,
        processingContext: ProcessingContext,
        allPageResults: ConcurrentHashMap<Int, PageProcessingResult>
    ): Boolean {
        log.info("Status before queuing next page: $queueStats, active_tasks=${activeTasks.size}, errors=${errorCount.get()}/$maxErrors")
        val page = getNextPage() ?: return true
        val pageUrl = page.url
        if (pageUrl.isNullOrBlank()) {
            log.error("Invalid page link encountered: $page")
            errorCount.incrementAndGet()
            page.completed = true
            page.completed = true
            page.error = "Invalid or empty URL"
            return false
        }
        activeTasks.add(pageUrl)

        log.info("Queuing page for processing: url='$pageUrl', title='${page.title}', depth=${page.depth}, relevance=${page.relevance_score}")

        val subTask = try {
            tabs.task.newTask().apply {
                tabs[pageUrl] = placeholder
            }
        } catch (e: Exception) {
            log.error("Failed to create subtask for URL: $pageUrl", e)
            errorCount.incrementAndGet()
            page.completed = true
            page.completed = true
            page.error = "Failed to create subtask: ${e.message}"
            return false
        }

        subTask.ui.pool.submit({
            try {
                crawlPage(
                    processedCount,
                    pageUrl,
                    page,
                    maxPages,
                    maxDepth,
                    maxQueueSize,
                    webSearchDir,
                    agent,
                    fetchStrategy,
                    errorCount,
                    subTask,
                    analysisResultsMap,
                    transcriptStream,
                    processingStrategy,
                    processingContext.copy(
                        task = subTask
                    ),
                    allPageResults
                )
            } catch (e: Exception) {
                log.error("Uncaught exception in page processing task for: $pageUrl", e)
                errorCount.incrementAndGet()
                page.completed = true
                page.completed = true
                page.error = "Uncaught exception: ${e.message}"
            } finally {
                activeTasks.remove(pageUrl)
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
        errorCount: AtomicInteger,
        task: SessionTask,
        analysisResultsMap: ConcurrentHashMap<Int, String>,
        transcriptStream: FileOutputStream?,
        processingStrategy: PageProcessingStrategy,
        processingContext: ProcessingContext,
        allPageResults: ConcurrentHashMap<Int, PageProcessingResult>
    ) {
        val typeConfig = typeConfig ?: throw RuntimeException("Missing type config")
        val pageStartTime = System.currentTimeMillis()
        log.info("Starting to process page ${processedCount.get() + 1}: url='${link}', title='${page.title}'")
        val currentIndex = processedCount.incrementAndGet()
        // Update processing context with current count
        processingContext.processedCount.set(currentIndex)

        // Apply crawl delay if robots.txt specifies one
        if (typeConfig.respect_robots_txt == true) {
            robotsTxtParser.getCrawlDelay(link)?.let { delay ->
                log.debug("Applying robots.txt crawl delay of ${delay}ms for: $link")
                sleep(delay)
            }
        }

        if (currentIndex > maxPages) {
            log.warn("Max pages limit ($maxPages) reached, stopping processing for page: ${link}")
        } else {
            try {
                val url = link
                val title = page.title
                task.add("## ${currentIndex}. [${title}]($url)".renderMarkdown)
                val statusBuffer = task.add("Fetching content...", additionalClasses = "text-muted")

                val processPageResult = buildString {
                    this.appendLine("## ${currentIndex}. [${title}]($url)")
                    this.appendLine()
                    try {
                        // Log page processing start to transcript
                        transcriptStream?.let { stream ->
                            try {
                                writeToTranscript(
                                    stream,
                                    "### Processing Page ${currentIndex}: [$title]($url) (priority=${"%0.3f".format(page.calculatePriority())})\n\n"
                                )
                                writeToTranscript(
                                    stream,
                                    "**Started:** ${
                                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                                    }\n\n"
                                )
                            } catch (e: Exception) {
                                log.debug("Failed to write page start to transcript (stream may be closed)", e)
                            }
                        }

                        val content = fetchAndProcessUrl(
                            url,
                            webSearchDir = webSearchDir,
                            index = currentIndex,
                            pool = agent.pool,
                            fetchStrategy = fetchStrategy
                        )
                        statusBuffer?.setLength(0)
                        statusBuffer?.append("Processing content...")
                        task.update()
                        log.debug("Fetched content for '$url': ${content.length} characters")
                        if (content.length < (typeConfig.min_content_length ?: 500)) {
                            log.info("Content too short for '$url': ${content.length} < ${typeConfig.min_content_length ?: 500} chars, skipping")
                            this.appendLine("*Content too short (${content.length} chars), skipping this result*")
                            this.appendLine()
                            // Record as irrelevant for strategy
                            val pageResult = PageProcessingResult(
                                url = url,
                                pageType = PageType.Irrelevant,
                                content = "*Content too short*",
                                extractedLinks = null,
                                metadata = mapOf("content_length" to content.length)
                            )
                            allPageResults[currentIndex] = pageResult
                            task.add("*Content too short (${content.length} chars), skipping this result*".renderMarkdown)
                            statusBuffer?.setLength(0); task.update()
                            return@buildString
                        }

                        // Use strategy to process the page
                        log.debug("Processing page with strategy: ${processingStrategy.javaClass.simpleName}")
                        val pageResult = processingStrategy.processPage(url, content, processingContext)
                        allPageResults[currentIndex] = pageResult

                        // Handle different page types
                        if (pageResult.pageType == PageType.Error) {
                            log.warn("Strategy returned error for '$url': ${pageResult.metadata["error"]}")
                            this.appendLine("*Error processing this result: ${pageResult.metadata["error"]}*")
                            this.appendLine()
                            saveStrategyResult(
                                webSearchDir.resolve("error").apply { mkdirs() },
                                url,
                                pageResult,
                                currentIndex
                            )
                            task.add("*Error processing this result: ${pageResult.metadata["error"]}*".renderMarkdown)
                            statusBuffer?.setLength(0); task.update()
                            return@buildString
                        }

                        if (pageResult.pageType == PageType.Irrelevant) {
                            log.info("Strategy marked content as irrelevant for '$url'")
                            this.appendLine("*Irrelevant content, skipping this result*")
                            this.appendLine()
                            saveStrategyResult(
                                webSearchDir.resolve("irrelevant").apply { mkdirs() },
                                url,
                                pageResult,
                                currentIndex
                            )
                            task.add("*Irrelevant content, skipping this result*".renderMarkdown)
                            statusBuffer?.setLength(0); task.update()
                            return@buildString
                        }

                        saveStrategyResult(webSearchDir, url, pageResult, currentIndex)
                        statusBuffer?.setLength(0); task.update()
                        task.add(pageResult.content.renderMarkdown)

                        this.appendLine(pageResult.content)
                        this.appendLine()
                        // Check for early termination
                        if (pageResult.shouldTerminate) {
                            log.info("Strategy requested termination: ${pageResult.terminationReason}")
                            this.appendLine()
                            this.appendLine("---")
                            this.appendLine()
                            this.appendLine("**Crawling terminated:** ${pageResult.terminationReason}")
                            task.add("\n\n**Crawling terminated:** ${pageResult.terminationReason}".renderMarkdown)
                            this.appendLine()
                        }

                        if (typeConfig.follow_links == true) {

                            var linkData = pageResult.extractedLinks
                            val allowRevisit = /*taskConfig?.allow_revisit_pages ?:*/
                                typeConfig.allow_revisit_pages == true
                            if (linkData.isNullOrEmpty()) {
                                linkData = extractLinksFromMarkdown(pageResult.content)
                                log.debug("Extracted ${linkData.size} links from markdown for '$url'")
                            } else {
                                log.debug("Using ${linkData.size} structured links from analysis for '$url'")
                            }
                            // Add extracted links section to UI
                            if (linkData.isNotEmpty()) {
                                this.appendLine()
                                this.appendLine("### Extracted Links (${linkData.size} found)")
                                task.add("### Extracted Links (${linkData.size} found)".renderMarkdown)
                                this.appendLine()
                            }


                            var addedCount = 0
                            val skippedLinks = mutableListOf<Pair<LinkData, String>>()
                            val addedLinksBuffer = StringBuilder()

                            linkData.take(10) // Limit links per page to prevent explosion
                                .filter { link ->
                                    val linkUrl = link.url
                                    val isValid = VALID_URL_PATTERN.matcher(linkUrl!!).matches()
                                    val isNotBlacklisted = !isBlacklistedDomain(linkUrl)
                                    val isNotDuplicate = allowRevisit || !seenUrls.contains(linkUrl)
                                    val isAllowedByRobots =
                                        typeConfig.respect_robots_txt != true || robotsTxtParser.isAllowed(linkUrl)

                                    if (!isValid) {
                                        skippedLinks.add(link to "Invalid URL format")
                                    } else if (!isNotBlacklisted) {
                                        skippedLinks.add(link to "Blacklisted domain")
                                    } else if (!isNotDuplicate) {
                                        skippedLinks.add(link to "Already in queue")
                                    } else if (!isAllowedByRobots) {
                                        skippedLinks.add(link to "Disallowed by robots.txt")
                                    }

                                    isValid && isNotBlacklisted && isNotDuplicate && isAllowedByRobots
                                }.forEach { link ->
                                    val newLink = link.apply { depth = page.depth + 1 }
                                    if (addToQueue(newLink, maxDepth, maxQueueSize)) {
                                        addedCount++
                                        this.appendLine("- ✅ **[${link.title ?: "Untitled"}](${link.url})** (depth: ${newLink.depth}, relevance: ${link.relevance_score})")
                                        addedLinksBuffer.appendLine("- ✅ **[${link.title ?: "Untitled"}](${link.url})** (depth: ${newLink.depth}, relevance: ${link.relevance_score})")
                                    } else {
                                        skippedLinks.add(link to "Queue limit reached or max depth exceeded")
                                    }
                                }
                            // Show skipped links
                            if (skippedLinks.isNotEmpty()) {
                                this.appendLine()
                                val skippedBlock = buildString {
                                    appendLine("<details>")
                                    appendLine("<summary>Skipped Links (${skippedLinks.size})</summary>")
                                    appendLine()
                                    skippedLinks.forEach { (link, reason) ->
                                        appendLine("- ⏭️ **[${link.title ?: "Untitled"}](${link.url})** - *${reason}*")
                                    }
                                    appendLine()
                                    appendLine("</details>")
                                }
                                this.append(skippedBlock)
                                task.add(skippedBlock.renderMarkdown)
                                this.appendLine()
                            }
                            if (addedLinksBuffer.isNotEmpty()) task.add(addedLinksBuffer.toString().renderMarkdown)

                            log.info("Added $addedCount new links to queue from '$url' (filtered from ${linkData.size} total)")
                            // Add summary
                            if (linkData.isNotEmpty()) {
                                this.appendLine()
                                this.appendLine("**Link Processing Summary:** ${addedCount} added to queue, ${skippedLinks.size} skipped")
                                this.appendLine()
                            }
                            transcriptStream?.let { stream ->
                                writeToTranscript(
                                    stream, buildString {
                                        appendLine()
                                        appendLine("### Link Processing Summary for [${title}]($url)")
                                        appendLine("<details>")
                                        appendLine("<summary>**Links Found:** ${linkData.size}, **Added to Queue:** $addedCount, **Skipped:** ${skippedLinks.size}</summary>")
                                        appendLine()
                                        linkData.forEach { link ->
                                            val wasAdded = seenUrls.contains(link.url)
                                            appendLine(
                                                "- ${if (wasAdded) "✅" else "⏭️"} **[${link.title ?: "Untitled"}](${link.url})** - Relevance: ${link.relevance_score} ${
                                                    link.tags?.joinToString(
                                                        ", "
                                                    )?.let { " - Tags: $it" } ?: ""
                                                }")
                                        }
                                        appendLine()
                                        appendLine("</details>")
                                        appendLine()
                                    }
                                )
                            }
                        }
                    } catch (e: Exception) {
                        log.error("Error processing URL: $url", e)
                        task.error(e)
                        errorCount.incrementAndGet()
                        synchronized(pageQueueLock) {
                            page.error = e.message
                        }
                        this.appendLine("*Error processing this result: ${e.message}*")
                        this.appendLine()
                        // Log error to transcript
                        transcriptStream?.let { stream ->
                            try {
                                writeToTranscript(stream, "**Error:** ${e.message}\n\n")
                            } catch (ex: Exception) {
                                log.debug("Failed to write error to transcript (stream may be closed)", ex)
                            }
                        }
                    }
                }
                analysisResultsMap[currentIndex] = processPageResult
                log.info("Successfully processed page ${currentIndex}: url='${link}', processing_time=${System.currentTimeMillis() - pageStartTime}ms")
            } catch (e: Exception) {
                task.error(e)
                log.error("Error processing page: ${link}", e)
                errorCount.incrementAndGet()
                page.error = e.message
                page.error = e.message
                analysisResultsMap[currentIndex] =
                    "## ${currentIndex}. [${page.title}](${link})\n\n*Error processing this result: ${e.message}*\n\n"
            } finally {
                // Log page completion to transcript
                transcriptStream?.let { stream ->
                    try {
                        writeToTranscript(
                            stream,
                            "**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}\n"
                        )
                        writeToTranscript(
                            stream,
                            "**Processing Time:** ${System.currentTimeMillis() - pageStartTime}ms\n\n---\n\n"
                        )
                    } catch (e: Exception) {
                        log.debug("Failed to write page completion to transcript (stream may be closed)", e)
                    }
                }

                page.completed = true
                page.processingTimeMs = System.currentTimeMillis() - pageStartTime
                page.completed = true
                log.debug("Page processing completed: url='${link}', time=${page.processingTimeMs}ms, error='${page.error ?: "none"}'")
                task.complete()
            }
        }
    }

    private fun isBlacklistedDomain(url: String): Boolean {
        val blacklistedDomains = setOf(
            "facebook.com",
            "twitter.com",
            "instagram.com",
            "linkedin.com",
            "youtube.com",
            "tiktok.com",
            "pinterest.com",
            "reddit.com",
            "amazon.com",
            "ebay.com",
            "aliexpress.com"
        )
        return try {
            val uri = URI.create(url)
            val typeConfig = typeConfig ?: throw RuntimeException("Missing type config")

            // Check if URL is restricted by allowed_domains whitelist
            val allowedDomains =
                (
                        (typeConfig.allowed_domains?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: listOf())
                        //+ (executionConfig?.allowed_domains?.split(Regex("\\s+")
                        ).filter { it.isNotBlank() }.toSet()
            if (allowedDomains.isNotEmpty()) {
                val isAllowed = allowedDomains.any { allowedDomainOrPrefix ->
                    val normalizedAllowed = allowedDomainOrPrefix.lowercase().trim()
                    when {
                        // Check if it's a full URL prefix match
                        normalizedAllowed.startsWith("http://") || normalizedAllowed.startsWith("https://") -> {
                            url.lowercase().startsWith(normalizedAllowed)
                        }
                        // Check if it's a domain match (exact or subdomain)
                        else -> {
                            val domain = uri.host?.lowercase()
                            if (domain == null) {
                                log.warn("Could not extract domain from URL: $url")
                                return true
                            }
                            domain == normalizedAllowed || domain.endsWith(".${normalizedAllowed}")
                        }
                    }
                }
                if (!isAllowed) {
                    log.debug("URL not in allowed domains list: $url")
                    return true
                }
            }

            // Check blacklist
            val domain = uri.host?.lowercase()
            if (domain == null) {
                log.warn("Could not extract domain from URL: $url")
                return true
            }
            blacklistedDomains.any { domain.contains(it) }
        } catch (e: Exception) {
            log.warn("Invalid URL format: $url", e)
            true // Blacklist invalid URLs
        }
    }

    private fun createFinalSummary(analysisResults: String, chatInterface: ChatInterface): String {
        log.info("Creating final summary of analysis results (original size: ${analysisResults.length})")

        val typeConfig = typeConfig ?: throw RuntimeException("Missing type config")
        if (analysisResults.length < (typeConfig.max_final_output_size ?: 15000) * 1.2) {
            log.info("Analysis results only slightly exceed max size, truncating instead of summarizing")
            return analysisResults.substring(
                0, min(analysisResults.length, typeConfig.max_final_output_size ?: 15000)
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
            model = chatInterface,
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

    private fun addPageQueueDetailsTab(
        tabs: TabbedDisplay,
        processedCount: Int,
        errorCount: Int
    ) {
        try {
            val queueDetailsTask = tabs.task.newTask()
            tabs["Queue Details"] = queueDetailsTask.placeholder
            val queueDetails = buildString {
                appendLine("# Page Queue Details")
                appendLine()
                appendLine("## Summary Statistics")
                appendLine()
                appendLine("- **Total Pages Processed:** $processedCount")
                appendLine("- **Successful:** ${processedCount - errorCount}")
                appendLine("- **Errors:** $errorCount")
                appendLine("- **Success Rate:** ${if (processedCount > 0) ((processedCount - errorCount) * 100 / processedCount) else 0}%")
                synchronized(pageQueueLock) {
                    val unprocessedPages = pageQueue.toList()
                    val allSeenUrls = seenUrls.toList()
                    appendLine("- **Total URLs Discovered:** ${allSeenUrls.size}")
                    appendLine("- **Pages Not Processed:** ${unprocessedPages.size}")
                    appendLine()
                    // Processed pages section
                    appendLine("## Processed Pages")
                    appendLine()
                    val processedPages = allSeenUrls.filter { url ->
                        unprocessedPages.none { it.url == url }
                    }
                    if (processedPages.isEmpty()) {
                        appendLine("*No pages were processed.*")
                    } else {
                        appendLine("| # | URL | Status | Depth | Processing Time | Error |")
                        appendLine("|---|-----|--------|-------|-----------------|-------|")
                        processedPages.forEachIndexed { index, url ->
                            val status = if (urlContentCache.containsKey(url)) "✅ Success" else "❌ Failed"
                            // Try to find the LinkData for this URL to get more details
                            val depth = "N/A" // We don't track this for completed pages currently
                            val processingTime = "N/A" // We don't track this for completed pages currently
                            val error = "" // We don't track this for completed pages currently
                            appendLine("| ${index + 1} | [${url.take(50)}...](${url}) | $status | $depth | $processingTime | $error |")
                        }
                    }
                    appendLine()
                    // Unprocessed pages section
                    appendLine("## Unprocessed Pages (Still in Queue)")
                    appendLine()
                    if (unprocessedPages.isEmpty()) {
                        appendLine("*All discovered pages were processed.*")
                    } else {
                        appendLine("| # | URL | Title | Relevance | Depth | Priority | Status |")
                        appendLine("|---|-----|-------|-----------|-------|----------|--------|")
                        unprocessedPages.sortedByDescending { it.calculatePriority() }.forEachIndexed { index, page ->
                            val url = page.url ?: "N/A"
                            val title = page.title?.take(30) ?: "Untitled"
                            val relevance = String.format("%.1f", page.relevance_score)
                            val depth = page.depth.toString()
                            val priority = String.format("%.2f", page.calculatePriority())
                            val status = when {
                                page.error != null -> "❌ Error: ${page.error}"
                                page.started && !page.completed -> "⏳ In Progress"
                                page.completed -> "✅ Completed"
                                else -> "⏸️ Queued"
                            }
                            appendLine("| ${index + 1} | [${url.take(50)}...](${url}) | $title | $relevance | $depth | $priority | $status |")
                        }
                    }
                    appendLine()
                }
            }
            queueDetailsTask.complete(queueDetails.renderMarkdown)
            log.info("Added page queue details tab with statistics")
        } catch (e: Exception) {
            log.error("Failed to create page queue details tab", e)
        }
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
        val typeConfig = typeConfig ?: throw RuntimeException("Missing type config")
        if (url.isBlank()) {
            throw IllegalArgumentException("URL cannot be blank")
        }


        if (!(typeConfig.allow_revisit_pages == true) && urlContentCache.containsKey(url)) {
            log.debug("Using cached content for URL: $url (cache size: ${urlContentCache.size})")
            return urlContentCache[url]!!
        }
        log.debug(
            "Fetching content for URL: {} using method: {}", url, typeConfig.fetch_method ?: FetchMethod.HttpClient
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
                url = linkUrl, title = linkText, relevance_score = 50.0
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

    private fun saveStrategyResult(
        webSearchDir: File, url: String, result: PageProcessingResult, index: Int
    ) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val urlSafe = url.replace(Regex("https?://"), "").replace(Regex("[^a-zA-Z0-9]"), "_").take(100)
            val resultFile = File(webSearchDir, "${urlSafe}_${index}_${timestamp}.md")

            val metadata = mapOf(
                "url" to url,
                "timestamp" to LocalDateTime.now().toString(),
                "index" to index,
                "page_type" to result.pageType.name,
                "query" to (executionConfig?.search_query ?: ""),
                "content_query" to (executionConfig?.content_queries ?: ""),
                "metadata" to result.metadata
            )
            val metadataJson = try {
                ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata)
            } catch (e: JsonProcessingException) {
                log.error("Failed to serialize metadata for URL: $url", e)
                "{}"
            }

            val contentWithHeader = "<!-- ${metadataJson} -->\n\n${result.content}"
            resultFile.writeText(contentWithHeader)
            log.debug("Saved strategy result to file: ${resultFile.absolutePath} (size: ${contentWithHeader.length} chars)")
        } catch (e: Exception) {
            log.error("Failed to save strategy result for URL: $url", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CrawlerAgentTask::class.java)
        private val LINK_PATTERN = Pattern.compile("""\[([^]]+)]\(([^)]+)\)""")
        private val VALID_URL_PATTERN = Pattern.compile("^(http|https)://.*")
        val CrawlerAgent = TaskType(
            "CrawlerAgent",
            "Online & Search",
            CrawlerAgentTask::class.java,
            CrawlerTaskExecutionConfigData::class.java,
            CrawlerTaskTypeConfig::class.java,
            "Search Google, fetch top results, and analyze content",
            """
          Searches Google for specified queries and analyzes the top results.
          <ul>
            <li>Performs Google searches</li>
            <li>Fetches top search results</li>
            <li>Analyzes content for specific goals</li>
            <li>Generates detailed analysis reports</li>
 </ul>
        """,
        )

    }

}