package com.simiacryptus.cognotik.crawl

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.crawl.fetch.FetchMethod
import com.simiacryptus.cognotik.crawl.fetch.FetchStrategy
import com.simiacryptus.cognotik.crawl.processing.PageProcessingStrategy
import com.simiacryptus.cognotik.crawl.processing.ProcessingStrategyType
import com.simiacryptus.cognotik.crawl.seed.SeedMethod
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApiChatModel
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.ISessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.collections.iterator
import kotlin.math.min
import kotlin.random.Random

class CrawlerAgentTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: CrawlerTaskExecutionConfigData?,
) : AbstractTask<CrawlerAgentTask.CrawlerTaskExecutionConfigData, CrawlerAgentTask.CrawlerTaskTypeConfig>(
  orchestrationConfig, planTask
) {

  @Suppress("PropertyName")
  class CrawlerTaskTypeConfig(
      @Description("Method to seed the crawler. One of: GoogleProxy, DirectUrls (optional, default: GoogleProxy)") var seed_method: SeedMethod = SeedMethod.GoogleProxy,
      @Description("Method used to fetch content from URLs. One of: HttpClient, Selenium (optional, default: HttpClient)") var fetch_method: FetchMethod = FetchMethod.Companion.HttpClient,
      @Description("Strategy for processing pages. One of: DefaultSummarizer, FactChecking, JobMatching (optional, default: DefaultSummarizer)") var processing_strategy: ProcessingStrategyType = ProcessingStrategyType.Companion.DEFAULT,
      @Description("Whitespace-separated list of allowed domains or URL prefixes to restrict crawling scope. If set, only URLs matching these domains/prefixes will be crawled (optional)") var allowed_domains: String? = null,
      @Description("Whether to respect robots.txt rules when crawling (default: true)") var respect_robots_txt: Boolean = true,
      @Description("Maximum number of pages to process in a single task. Must be greater than 0 (optional, default: 30)") var max_pages_per_task: Int = 30,
      @Description("Maximum depth to crawl from seed pages. Must be non-negative (optional, default: 3)") var max_depth: Int = 3,
      @Description("Maximum queue size to prevent memory issues. Must be greater than 0 (optional, default: 100)") var max_queue_size: Int = 100,
      @Description("Number of pages to process concurrently. Must be greater than 0 (optional, default: 3)") var concurrent_page_processing: Int = 3,
      @Description("Maximum characters in final summary output. Must be greater than 0 (optional, default: 15000)") var max_final_output_size: Int = 30000,
      @Description("Minimum content length in characters to process a page. Pages shorter than this are skipped (optional, default: 500)") var min_content_length: Int = 500,
      @Description("Whether to automatically follow links found in analyzed pages (optional)") var follow_links: Boolean = true,
      @Description("Whether to allow crawling the same page multiple times (optional)") var allow_revisit_pages: Boolean = false,
      @Description("Whether to generate a comprehensive summary of all results (optional)") var create_final_summary: Boolean = true,
      @Description("Path to a JSON file for persisting crawl state (link database) across runs. If not specified, defaults to .websearch/crawl_state.json relative to the task root. The file is loaded at start and saved at end, allowing editable persistence for ongoing crawls.") var crawl_state_file: String? = null,
      @Description("Whether to use the state file for persistence. If false, the crawl state will not be loaded or saved, and the crawler will start fresh on each run. This allows you to disable persistence.") var use_state_file: Boolean = true,
      task_type: String = "CrawlerAgent",
      model: ApiChatModel? = null,
      name: String? = task_type,
  ) : TaskTypeConfig(task_type = task_type, name = name, model = model), ValidatedObject {
    override fun validate(): String? {
      if (max_depth < 0) {
        max_depth = 0
      }
      if (min_content_length < 0) {
        min_content_length = 0
      }
      return ValidatedObject.Companion.validateFields(this)
    }
  }
   /**
    * Persistent crawl state representing the link database.
    * This is loaded from and saved to a JSON file, allowing ongoing crawls
    * to be resumed, inspected, and edited between runs.
    */
   data class CrawlState(
       @Description("All links discovered during crawling, keyed by URL") var links: MutableMap<String, CrawlLinkEntry> = mutableMapOf(),
       @Description("Timestamp of last save") var last_saved: String? = null,
       @Description("Total number of runs that have contributed to this state") var run_count: Int = 0,
       @Description("Search queries used across runs") var search_queries: MutableList<String> = mutableListOf()
   )
   data class CrawlLinkEntry(
       @Description("The URL") var url: String,
       @Description("Title of the page") var title: String? = null,
       @Description("Tags associated with the link") var tags: List<String>? = null,
       @Description("Relevance score from 1 to 100") var relevance_score: Double = 50.0,
       @Description("Crawl depth from seed") var depth: Int = 0,
       @Description("Status: queued, completed, error, skipped") var status: String = "queued",
       @Description("Error message if status is error") var error: String? = null,
       @Description("Processing time in milliseconds") var processing_time_ms: Long = 0,
       @Description("Timestamp when this entry was first discovered") var discovered_at: String? = null,
       @Description("Timestamp when this entry was last processed") var processed_at: String? = null,
       @Description("The run number in which this link was completed") var completed_in_run: Int? = null
   )


  class CrawlerTaskExecutionConfigData(
      @Description("The search queries to use for Google search. Either this or direct_urls must be provided.") var search_query: List<String>? = null,
      @Description("Direct URLs to analyze. Each must be a valid http or https URL. Either this or search_query must be provided.") var direct_urls: List<String>? = null,
      @Description("The query considered when processing the content. This should contain a detailed listing of the desired data, evaluation criteria, and filtering priorities used to transform each page into the desired summary.") var content_queries: Any? = null,
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
      val directUrls = direct_urls
      val searchQueries = search_query
      if ((searchQueries.isNullOrEmpty() || searchQueries.all { it.isBlank() }) && directUrls.isNullOrEmpty()) {
        return "Either search_query or direct_urls must be provided"
      }

      if (!directUrls.isNullOrEmpty()) {
        directUrls.forEach { url ->
          if (!url.matches(Regex("^(http|https)://.*"))) {
            return "Invalid URL format in direct_urls: $url"
          }
        }
      }
      return ValidatedObject.Companion.validateFields(this)
    }
  }

  val urlContentCache = ConcurrentHashMap<String, String>()
  private val robotsTxtParser = RobotsTxtParser()
   @Volatile
   private var currentCrawlState: CrawlState = CrawlState()
   @Volatile
   private var crawlStateFile: File? = null
   @Volatile
   private var currentRunNumber: Int = 1

  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  private val pageQueueLock = Object()
  private val transcriptLock = Object()
  private val pageQueue = PriorityQueue<LinkData>(compareByDescending { it.calculatePriority() })
  private val seenUrls = ConcurrentHashMap.newKeySet<String>()
   private fun resolveCrawlStateFile(root: File): File {
     val typeConfig = typeConfig
     val configuredPath = typeConfig?.crawl_state_file
     return if (!configuredPath.isNullOrBlank()) {
       val f = File(configuredPath)
       if (f.isAbsolute) f else File(root, configuredPath)
     } else {
         File(File(root, ".websearch"), "crawl_state.json")
     }
   }
   private fun loadCrawlState(stateFile: File): CrawlState {
     return if (stateFile.exists() && stateFile.length() > 0) {
       try {
         val state = JsonUtil.fromJson<CrawlState>(stateFile.readText(), CrawlState::class.java)
         log.info("Loaded crawl state from ${stateFile.absolutePath}: ${state.links.size} links, run_count=${state.run_count}")
         state
       } catch (e: Exception) {
         log.error("Failed to load crawl state from ${stateFile.absolutePath}, starting fresh", e)
         CrawlState()
       }
     } else {
       log.info("No existing crawl state at ${stateFile.absolutePath}, starting fresh")
       CrawlState()
     }
   }
   private fun saveCrawlState(stateFile: File, state: CrawlState) {
     try {
       stateFile.parentFile?.let { parent ->
         if (!parent.exists() && !parent.mkdirs()) {
           log.error("Failed to create directory for crawl state: ${parent.absolutePath}")
           return
         }
       }
       state.last_saved = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
       stateFile.writeText(state.toJson())
       log.info("Saved crawl state to ${stateFile.absolutePath}: ${state.links.size} links")
     } catch (e: Exception) {
       log.error("Failed to save crawl state to ${stateFile.absolutePath}", e)
     }
   }
   /**
    * Synchronize in-memory queue/seenUrls state into the CrawlState object.
    * Call this before saving.
    */
   private fun syncInMemoryStateToCrawlState() {
     synchronized(pageQueueLock) {
       // Update entries for items still in the queue
       for (linkData in pageQueue) {
         val url = linkData.url ?: continue
         val entry = currentCrawlState.links.getOrPut(url) {
           CrawlLinkEntry(
             url = url,
             discovered_at = LocalDateTime.now().toString()
           )
         }
         entry.title = linkData.title ?: entry.title
         entry.tags = linkData.tags ?: entry.tags
         entry.relevance_score = linkData.relevance_score
         entry.depth = linkData.depth
         if (!linkData.completed && !linkData.started) {
           entry.status = "queued"
         } else if (linkData.started && !linkData.completed) {
           entry.status = "in_progress"
         }
         if (linkData.completed) {
           entry.status = if (linkData.error != null) "error" else "completed"
           entry.error = linkData.error
           entry.processing_time_ms = linkData.processingTimeMs
           entry.processed_at = LocalDateTime.now().toString()
           entry.completed_in_run = currentRunNumber
         }
       }
       // Also update seen URLs that aren't in the queue (already processed)
       for (url in seenUrls) {
         currentCrawlState.links.getOrPut(url) {
           CrawlLinkEntry(
             url = url,
             discovered_at = LocalDateTime.now().toString(),
             status = "seen"
           )
         }
       }
     }
   }
   /**
    * Load crawl state and populate the in-memory queue with any previously
    * queued (not yet completed) links. Already-completed links are added
    * to seenUrls so they won't be re-crawled (unless allow_revisit_pages is true).
    */
   private fun restoreFromCrawlState(state: CrawlState, maxDepth: Int, maxQueueSize: Int) {
     val typeConfig = typeConfig ?: return
     val allowRevisit = typeConfig.allow_revisit_pages == true
     var restoredCount = 0
     var skippedCompleted = 0
     synchronized(pageQueueLock) {
       for ((url, entry) in state.links) {
         when (entry.status) {
           "completed" -> {
             if (!allowRevisit) {
               seenUrls.add(url)
               skippedCompleted++
             } else {
               // Re-queue completed pages if revisit is allowed
               val linkData = LinkData(
                 url = url,
                 title = entry.title,
                 tags = entry.tags,
                 relevance_score = entry.relevance_score
               ).apply { depth = entry.depth }
               if (addToQueue(linkData, maxDepth, maxQueueSize)) {
                 restoredCount++
               }
             }
           }
           "error" -> {
             // Re-queue errored pages for retry
             val linkData = LinkData(
               url = url,
               title = entry.title,
               tags = entry.tags,
               relevance_score = entry.relevance_score
             ).apply { depth = entry.depth }
             if (addToQueue(linkData, maxDepth, maxQueueSize)) {
               restoredCount++
             }
           }
           "queued", "seen", "in_progress" -> {
             // Re-queue items that were queued or in-progress from a previous run
             val linkData = LinkData(
               url = url,
               title = entry.title,
               tags = entry.tags,
               relevance_score = entry.relevance_score
             ).apply { depth = entry.depth }
             if (addToQueue(linkData, maxDepth, maxQueueSize)) {
               restoredCount++
             }
           }
           else -> {
             // Unknown status, treat as queued
             val linkData = LinkData(
               url = url,
               title = entry.title,
               tags = entry.tags,
               relevance_score = entry.relevance_score
             ).apply { depth = entry.depth }
             if (addToQueue(linkData, maxDepth, maxQueueSize)) {
               restoredCount++
             }
           }
         }
       }
     }
     log.info("Restored from crawl state: $restoredCount links re-queued, $skippedCompleted already-completed links marked as seen")
   }


  override fun promptSegment(): String {
    return buildString {
      appendLine("CrawlerAgent - Search Google, fetch top results, and analyze content")
      appendLine("** Specify the search query")
      appendLine("** Or provide direct URLs to analyze")
      appendLine("** Specify a detailed query/analysis prompt to guide content processing")
      appendLine("** Choose a processing strategy: DefaultSummarizer, FactChecking, or JobMatching")
      appendLine("** Results will be saved to .websearch directory for future reference")
      appendLine("** Links found in analysis can be automatically followed for deeper research")
     appendLine("** Crawl state is persisted to a JSON file for resumable/editable ongoing crawls")
      val typeConfig = this@CrawlerAgentTask.typeConfig
      if (null != typeConfig) {
        when (typeConfig.processing_strategy) {
          ProcessingStrategyType.Companion.DEFAULT -> {
            // No additional notes for DefaultSummarizer
          }

          else -> {
            appendLine(
              "** Using processing strategy: ${typeConfig.processing_strategy.name} - ${
                typeConfig.processing_strategy.createStrategy().description.indent("  ")
              }"
            )
          }
        }
      }
    }
  }

  data class LinkData(
      @Description("The URL of the link to crawl. Must be a valid http or https URL.") var url: String? = null,
      @Description("The title of the link (optional)") var title: String? = null,
      @Description("Tags associated with the link for categorization (optional)") var tags: List<String>? = null,
      @Description("Relevance score from 1 to 100 indicating how relevant this link is to the query") var relevance_score: Double = 100.0
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
      relevance_score = relevance_score.coerceIn(1.0, 100.0)
      return ValidatedObject.Companion.validateFields(this)
    }
  }

  // Priority calculation: higher relevance and lower depth = higher priority
  fun LinkData.calculatePriority(): Double = relevance_score // / (depth + 1.0)
  private fun normalizeUrl(url: String): String {
    // Remove fragment/anchor portion (#...) from URL to prevent duplicates
    val hashIndex = url.indexOf('#')
    return if (hashIndex >= 0) url.substring(0, hashIndex) else url
  }


  enum class PageType {
    Error, Irrelevant, OK
  }

  data class ParsedPage(
      @Description("The classification of the page content. One of: Error, Irrelevant, OK") var page_type: PageType = PageType.OK,
      @Description("Extracted information from the page, structured according to the content query") var page_information: Any? = null,
      @Description("Tags categorizing the page content") var tags: List<String>? = null,
      @Description("Links extracted from the page for further crawling") var link_data: List<LinkData>? = null,
  ) : ValidatedObject {
    override fun validate(): String? {
      link_data?.forEach { linkData ->
        linkData.validate()?.let { return it }
      }
      return ValidatedObject.Companion.validateFields(this)
    }
  }

  override fun run(
      agent: TaskOrchestrator,
      messages: List<String>,
      task: ISessionTask,
      resultFn: (String) -> Unit,
      orchestrationConfig: OrchestrationConfig
  ) {
    val transcriptStream = task.newUserFileStream(transcriptFile())
    try {
      log.info("Starting CrawlerAgentTask.run() with messages count: ${messages.size}")
      val chatInterface = (typeConfig?.model?.let { it.instance(orchestrationConfig.user) }
        ?: this@CrawlerAgentTask.defaultFast).getChildClient(task)
      resultFn(innerRun(agent, messages, task, orchestrationConfig, transcriptStream, chatInterface))
    } catch (e: Throwable) {
      log.error("Unhandled exception in CrawlerAgentTask", e)
      transcriptStream?.let { stream ->
        try {
          writeToTranscript(stream, buildString {
            appendLine()
            appendLine("## Fatal Error")
            appendLine()
            appendLine("<details><summary>Stack Trace</summary>")
            appendLine()
            appendLine("```")
            appendLine(e.stackTraceToString())
            appendLine("```")
            appendLine()
            appendLine("</details>")
            appendLine()
          })
        } catch (ex: Exception) {
          log.debug("Failed to write fatal error to transcript", ex)
        }
      }
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
//      cleanup()
    }
  }

  private fun innerRun(
      agent: TaskOrchestrator,
      messages: List<String>,
      task: ISessionTask,
      orchestrationConfig: OrchestrationConfig,
      transcriptStream: FileOutputStream?,
      chatInterface: ChatInterface
  ): String {
    try {
      val typeConfig = typeConfig ?: throw RuntimeException("Missing type config")

      val startTime = System.currentTimeMillis()
      log.info(
        "Starting CrawlerAgentTask with config: search_query='${executionConfig?.search_query?.joinToString(", ") ?: ""}', direct_urls='${
          executionConfig?.direct_urls?.joinToString(
            ", "
          ) ?: ""
        }', max_pages=${typeConfig.max_pages_per_task}"
      )
      val webSearchDir = File(agent.root.toFile(), ".websearch")
      if (!webSearchDir.exists()) {
        if (!webSearchDir.mkdirs()) {
          log.error("Failed to create websearch directory: ${webSearchDir.absolutePath}")
          return "Error: Failed to create output directory"
        }
        log.debug("Created websearch directory: ${webSearchDir.absolutePath}")
      }
       // Load crawl state (only if use_state_file is enabled)
       if (typeConfig.use_state_file) {
         crawlStateFile = resolveCrawlStateFile(agent.root.toFile())
         currentCrawlState = loadCrawlState(crawlStateFile!!)
         currentRunNumber = currentCrawlState.run_count + 1
         currentCrawlState.run_count = currentRunNumber
         executionConfig?.search_query?.forEach { query ->
           if (query.isNotBlank() && !currentCrawlState.search_queries.contains(query)) {
             currentCrawlState.search_queries.add(query)
           }
           }
         // Restore previously queued/errored links from crawl state
         restoreFromCrawlState(currentCrawlState, typeConfig.max_depth, typeConfig.max_queue_size)
         log.info("After restoring crawl state: queue_size=${pageQueue.size}, seen_urls=${seenUrls.size}")
       } else {
         log.info("State file persistence is disabled (use_state_file=false); starting fresh crawl state")
         crawlStateFile = null
         currentCrawlState = CrawlState()
         currentRunNumber = 1
         currentCrawlState.run_count = currentRunNumber
         executionConfig?.search_query?.forEach { query ->
           if (query.isNotBlank()) {
             currentCrawlState.search_queries.add(query)
           }
           }
       }

      val tabs = TabbedDisplay(task)

      // Write transcript header with tabbed structure
      transcriptStream?.let { stream -> writeTranscriptHeader(stream) }

      val seedMethod = when {
        !executionConfig?.direct_urls.isNullOrEmpty() -> SeedMethod.DirectUrls
        else -> typeConfig.seed_method
      }
      log.info("Using seed method: $seedMethod")
      val seedItems = try {
        val strategy = seedMethod.createStrategy(this, agent.user)
        strategy.getSeedItems(executionConfig, orchestrationConfig)
      } catch (e: Exception) {
        log.error("Failed to get seed items using method: $seedMethod", e)
        task.error(e)
        transcriptStream?.let { stream ->
          try {
            writeToTranscript(stream, buildString {
              appendLine("## Error: Failed to get seed items")
              appendLine()
              appendLine("<details><summary>Stack Trace</summary>")
              appendLine()
              appendLine("```")
              appendLine(e.stackTraceToString())
              appendLine("```")
              appendLine()
              appendLine("</details>")
              appendLine()
            })
          } catch (ex: Exception) {
            log.debug("Failed to write seed error to transcript", ex)
          }
        }
        return "Error: Failed to get seed items - ${e.message}"
      }
      if (seedItems == null || seedItems.isEmpty()) {
        log.warn("No seed items returned from seed method: $seedMethod")
        return "Warning: No seed items found to start crawling"
      }
      // Create seed links tab
      val seedLinksTask = task.newTask()
      tabs["Seed Links"] = seedLinksTask.placeholder
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
      seedLinksTask.add(seedLinksContent.renderMarkdown(true))
      seedLinksTask.complete()
      // Log seed links to transcript
      transcriptStream?.let { stream ->
        writeToTranscriptSafe(stream, "## Seed Links\n\n$seedLinksContent\n\n")
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
            if (!addToQueue(linkData, typeConfig.max_depth, typeConfig.max_queue_size)) {
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
      val maxPages = typeConfig.max_pages_per_task
      val concurrentProcessing = typeConfig.concurrent_page_processing
      log.info("Processing configuration: maxPages=$maxPages, concurrentProcessing=$concurrentProcessing")
      val sharedProcessedCount = AtomicInteger(0)
      // Create processing context
      val processingContext = PageProcessingStrategy.ProcessingContext(
          executionConfig = executionConfig ?: throw RuntimeException("Missing execution config"),
          typeConfig = typeConfig,
          orchestrationConfig = orchestrationConfig,
          messages = messages,
          task = task,
          webSearchDir = webSearchDir,
          processedCount = sharedProcessedCount,
          maxPages = maxPages,
          transcriptStream = transcriptStream,
          reprioritizeQueue = { scorer -> this@CrawlerAgentTask.reprioritizeQueue(scorer) }
      )
      // Track all page results for strategy
      val allPageResults = ConcurrentHashMap<Int, PageProcessingStrategy.PageProcessingResult>()


      val activeTasks = ConcurrentHashMap.newKeySet<String>()
      val processedCount = sharedProcessedCount
      val errorCount = AtomicInteger(0)
      val maxErrors = maxPages / 2 // Stop if too many errors
      log.info("Starting crawling loop with maxErrors threshold: $maxErrors")
      val fetchStrategy =
        (this@CrawlerAgentTask.typeConfig?.fetch_method ?: FetchMethod.Companion.HttpClient).createStrategy(
          this@CrawlerAgentTask
        )

      // Initialize processing strategy
      val strategyType = typeConfig.processing_strategy
      log.info("Using processing strategy: ${strategyType.name} - ${strategyType.javaClass.simpleName}")
      val processingStrategy = strategyType.createStrategy()
      try {
        val loopIterations = AtomicInteger(0)
        val maxDepthConfig = typeConfig.max_depth
        val maxQueueSizeConfig = typeConfig.max_queue_size
        val maxIterations = 1000
        log.debug("Starting crawling loop: maxPages=$maxPages, maxErrors=$maxErrors, maxIterations=$maxIterations")
        while (shouldContinue(
            maxPages,
            errorCount,
            maxErrors,
            loopIterations,
            activeTasks,
            maxIterations,
            processedCount
          )
        ) {
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
              task = task,
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
                Thread.sleep(1000)
            } catch (e: Exception) {
              log.error("Interrupted while waiting for tasks", e)
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
            transcriptStream?.let { stream ->
              try {
                writeToTranscript(stream, buildString {
                  appendLine()
                  appendLine("## Strategy Requested Early Termination")
                  appendLine()
                  appendLine("The processing strategy determined that sufficient information has been gathered or further crawling is unlikely to yield valuable results. Reason: ${continuationDecision.reason}")
                  appendLine()
                })
              } catch (e: Exception) {
                log.debug("Failed to write strategy termination to transcript", e)
              }
            }
            break
          }
        }
        if (loopIterations.get() >= maxIterations) {
          log.warn("Reached maximum iteration limit: $maxIterations")
          transcriptStream?.let { stream ->
            try {
              writeToTranscript(stream, buildString {
                appendLine()
                appendLine("## Warning: Reached maximum iteration limit")
                appendLine()
                appendLine("The crawler stopped after reaching the maximum number of iterations ($maxIterations) to prevent infinite loops. Check logs for details.")
                appendLine()
              })
            } catch (e: Exception) {
              log.debug("Failed to write iteration limit warning to transcript", e)
            }
          }
        }
      } catch (e: Exception) {
        log.error("Error during processing", e)
        task.error(e)
        transcriptStream?.let { stream ->
          try {
            writeToTranscript(stream, buildString {
              appendLine()
              appendLine("## Error During Processing")
              appendLine()
              appendLine("<details><summary>Stack Trace</summary>")
              appendLine()
              appendLine("```")
              appendLine(e.stackTraceToString())
              appendLine("```")
              appendLine()
              appendLine("</details>")
              appendLine()
            })
          } catch (ex: Exception) {
            log.debug("Failed to write processing error to transcript", ex)
          }
        }
      } finally {
        log.info("Crawling phase completed, cleaning up resources")
      }
       // Sync and save crawl state after crawling phase (only if use_state_file is enabled)
       if (typeConfig.use_state_file) {
         try {
           syncInMemoryStateToCrawlState()
           crawlStateFile?.let { saveCrawlState(it, currentCrawlState) }
         } catch (e: Exception) {
           log.error("Failed to save crawl state after crawling phase", e)
         }
       } else {
         log.debug("Skipping crawl state save (use_state_file=false)")
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

      val analysisResults = analysisResultsMap.entries
        .sortedBy { it.key }
        .joinToString("\n") { it.value }
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
              allPageResults.values.toList(), processingContext
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
        transcriptStream?.let { stream ->
          try {
            writeToTranscript(stream, buildString {
              appendLine()
              appendLine("## Warning: Strategy output generation failed, using fallback")
              appendLine()
              appendLine("<details><summary>Error Details</summary>")
              appendLine()
              appendLine("```")
              appendLine(e.stackTraceToString())
              appendLine("```")
              appendLine()
              appendLine("</details>")
              appendLine()
            })
          } catch (ex: Exception) {
            log.debug("Failed to write fallback error to transcript", ex)
          }
        }
        if (typeConfig.create_final_summary && analysisResults.length > typeConfig.max_final_output_size
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
            writeToTranscript(stream, buildString {
              appendLine()
              appendLine("</div>") // Close work-details tab
              appendLine()
              appendLine("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">")
              appendLine()
              appendLine("## Final Summary")
              appendLine()
              appendLine(finalOutput)
              appendLine()
              appendLine("</div>") // Close final-output tab
              appendLine()
            })
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

  private fun writeToTranscriptSafe(stream: FileOutputStream, content: String) {
    synchronized(transcriptLock) {
      writeToTranscript(stream, content)
    }
  }

  private fun writeTranscriptHeader(stream: FileOutputStream) {
    synchronized(transcriptLock) {
      synchronized(transcriptLock) {
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
            appendLine("**Search Query:** ${executionConfig?.search_query?.joinToString(", ") ?: "N/A"}")
            appendLine()
            appendLine("**Direct URLs:** ${executionConfig?.direct_urls?.joinToString(", ") ?: "N/A"}")
            appendLine()
           appendLine("**Crawl State File:** ${crawlStateFile?.absolutePath ?: "N/A (persistence disabled)"}")
           appendLine()
           appendLine("**State Persistence Enabled:** ${typeConfig?.use_state_file ?: true}")
           appendLine()
           appendLine("**Run Number:** $currentRunNumber")
           appendLine()
           appendLine("**Links from Previous Runs:** ${currentCrawlState.links.size}")
           appendLine()
            appendLine("<details><summary>Execution Configuration (click to expand)</summary>")
            appendLine()
            appendLine(executionConfig?.content_queries?.toJson()?.let { "\n```json\n${it.indent()}\n```" }
              ?: "N/A")
            appendLine()
            appendLine("</details>")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">")
            appendLine()
            appendLine("## Crawling Work Details")
            appendLine()
          }
          stream.write(header.toByteArray(StandardCharsets.UTF_8))
          stream.flush()
        } catch (e: Exception) {
          if (e !is IOException || e.message?.contains("closed") != true) {
            log.error("Failed to write transcript header", e)
          }
        }
      }
    }
  }

  private fun writeTranscriptFooter(stream: FileOutputStream, totalTime: Long, processedCount: Int, errorCount: Int) {
    synchronized(transcriptLock) {
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
        if (e !is IOException || e.message?.contains("closed") != true) {
          log.error("Failed to write transcript footer", e)
        }
      }
    }
  }

  fun addToQueue(
    newLink: LinkData, maxDepth: Int, maxQueueSize: Int
  ): Boolean = synchronized(pageQueueLock) {
    val typeConfig = typeConfig ?: throw RuntimeException("Missing type config")
   val newUrl = newLink.url?.let { normalizeUrl(it) }?.also { newLink.url = it }
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
    if (typeConfig.allow_revisit_pages != true && seenUrls.contains(newUrl)) {
      log.debug("Skipping duplicate link already in queue: $newUrl")
      return false
    }
    seenUrls.add(newUrl)
    pageQueue.add(
      newLink.copy(
        relevance_score = newLink.relevance_score.coerceIn(1.0, 100.0) + Random.nextInt(
          -500, 500
        ) * 0.001 // Slight randomness to prevent priority ties
      )
    )
    log.debug("Added new link to queue: $newUrl (depth=${newLink.depth}, priority=${newLink.calculatePriority()})")
     // Record in crawl state (only if use_state_file is enabled)
     if (typeConfig.use_state_file) {
       currentCrawlState.links.getOrPut(newUrl) {
         CrawlLinkEntry(
           url = newUrl,
           title = newLink.title,
           tags = newLink.tags,
           relevance_score = newLink.relevance_score,
           depth = newLink.depth,
           status = "queued",
           discovered_at = LocalDateTime.now().toString()
         )
       }
     }
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
  /**
   * Re-prioritize the pending crawl queue using a strategy-supplied scorer function.
   * The scorer receives a snapshot of all currently-queued links and returns a map of
   * URL -> new relevance score for any links whose priority should change.
   *
   * Because the underlying PriorityQueue orders by relevance score, we must remove and
   * re-add modified entries to trigger re-ordering.
   */
  fun reprioritizeQueue(
    scorer: (List<PageProcessingStrategy.QueuedLinkInfo>) -> Map<String, Double>
  ) = synchronized(pageQueueLock) {
    if (pageQueue.isEmpty()) return@synchronized
    val snapshot = pageQueue.map { linkData ->
      PageProcessingStrategy.QueuedLinkInfo(
        url = linkData.url ?: "",
        title = linkData.title,
        tags = linkData.tags,
        relevanceScore = linkData.relevance_score,
        depth = linkData.depth
      )
    }
    val newScores = try {
      scorer(snapshot)
    } catch (e: Exception) {
      log.error("Strategy scorer threw exception during queue re-prioritization", e)
      return@synchronized
    }
    if (newScores.isEmpty()) return@synchronized
    var changed = 0
    // Collect entries to modify, then rebuild the queue to ensure correct ordering
    val current = pageQueue.toList()
    pageQueue.clear()
    current.forEach { linkData ->
      val newScore = linkData.url?.let { newScores[it] }
      if (newScore != null) {
        linkData.relevance_score = newScore.coerceIn(1.0, 100.0)
        changed++
      }
      pageQueue.add(linkData)
    }
    if (changed > 0) {
      log.info("Re-prioritized crawl queue: $changed of ${current.size} links re-scored by strategy")
    }
  }


  private fun shouldContinue(
      maxPages: Int,
      errorCount: AtomicInteger,
      maxErrors: Int,
      loopIterations: AtomicInteger,
      activeTasks: MutableSet<String>,
      maxIterations: Int,
      processedCount: AtomicInteger
  ): Boolean = synchronized(pageQueueLock) {
    val completed = processedCount.get()
    val unstarted = pageQueue.size
    val hasActiveTasks = activeTasks.isNotEmpty()

    // Continue if:
    // 1. We have active tasks (they might add more links), OR
    // 2. We have unstarted pages in the queue
    // AND we haven't hit our limits
    val shouldContinue =
      (hasActiveTasks || unstarted > 0) && completed < maxPages && errorCount.get() < maxErrors && loopIterations.getAndIncrement() < maxIterations

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
      task: ISessionTask,
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
      processingContext: PageProcessingStrategy.ProcessingContext,
      allPageResults: ConcurrentHashMap<Int, PageProcessingStrategy.PageProcessingResult>
  ): Boolean {
    log.info("Status before queuing next page: $queueStats, active_tasks=${activeTasks.size}, errors=${errorCount.get()}/$maxErrors")
    val page = getNextPage() ?: return true
    val pageUrl = page.url
    if (pageUrl.isNullOrBlank()) {
      log.error("Invalid page link encountered: $page")
      errorCount.incrementAndGet()
      page.completed = true
      page.error = "Invalid or empty URL"
      return false
    }
    activeTasks.add(pageUrl)

    log.info("Queuing page for processing: url='$pageUrl', title='${page.title}', depth=${page.depth}, relevance=${page.relevance_score}")

    val subTask = try {
      task.linkedTask("Processing: ${page.title ?: pageUrl}")
    } catch (e: Exception) {
      log.error("Failed to create subtask for URL: $pageUrl", e)
      errorCount.incrementAndGet()
      page.completed = true
      page.error = "Failed to create subtask: ${e.message}"
      activeTasks.remove(pageUrl)
      return false
    }

    subTask.pool.submit({
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
      task: ISessionTask,
      analysisResultsMap: ConcurrentHashMap<Int, String>,
      transcriptStream: FileOutputStream?,
      processingStrategy: PageProcessingStrategy,
      processingContext: PageProcessingStrategy.ProcessingContext,
      allPageResults: ConcurrentHashMap<Int, PageProcessingStrategy.PageProcessingResult>
  ) {
    val typeConfig = typeConfig ?: throw RuntimeException("Missing type config")
    val pageStartTime = System.currentTimeMillis()
    log.info("Starting to process page: url='${link}', title='${page.title}'")
    val currentIndex: Int
    while (true) {
      val current = processedCount.get()
      if (current >= maxPages) {
        log.warn("Max pages limit ($maxPages) reached, stopping processing for page: ${link}")
        page.completed = true
        page.processingTimeMs = System.currentTimeMillis() - pageStartTime
        return
      }
      if (processedCount.compareAndSet(current, current + 1)) {
        currentIndex = current + 1
        break
      }
    }

    // Apply crawl delay if robots.txt specifies one
    if (typeConfig.respect_robots_txt == true) {
      robotsTxtParser.getCrawlDelay(link)?.let { delay ->
        log.debug("Applying robots.txt crawl delay of ${delay}ms for: $link")
          Thread.sleep(delay)
      }
    }

    run {
      try {
        var url = link
        val title = page.title
        task.add("## ${currentIndex}. [${title}]($url)".renderMarkdown(true))
        val statusBuffer = task.add("Fetching content...", additionalClasses = "text-muted")

        val processPageResult = buildString {
          this.appendLine("## ${currentIndex}. [${title}]($url)")
          this.appendLine()
          try {
            // Log page processing start to transcript
            transcriptStream?.let { stream ->
              try {
                writeToTranscriptSafe(
                  stream,
                  "### Processing Page ${currentIndex}: [$title]($url) (priority=${"%0.3f".format(page.calculatePriority())})\n\n"
                )
                writeToTranscriptSafe(
                  stream, "**Started:** ${
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
            if (content.length < typeConfig.min_content_length) {
              log.info("Content too short for '$url': ${content.length} < ${typeConfig.min_content_length} chars, skipping")
              this.appendLine("*Content too short (${content.length} chars), skipping this result*")
              this.appendLine()
              // Record as irrelevant for strategy
              val pageResult = PageProcessingStrategy.PageProcessingResult(
                  url = url,
                  pageType = PageType.Irrelevant,
                  content = "*Content too short*",
                  summary = null,
                  extractedLinks = null,
                  metadata = mapOf("content_length" to content.length)
              )
              allPageResults[currentIndex] = pageResult
              task.add(
                "*Content too short (${content.length} chars), skipping this result*".renderMarkdown(
                  true
                )
              )
              statusBuffer?.setLength(0); task.update()
              return@buildString
            }

            // Use strategy to process the page
            log.debug("Processing page with strategy: ${processingStrategy.javaClass.simpleName}")
            val pageResult = processingStrategy.processPage(url, content, processingContext)
            allPageResults[currentIndex] = pageResult
            // Allow the strategy to re-prioritize the pending crawl queue based on this result
            try {
              processingStrategy.reprioritizeQueue(
                pageResult, allPageResults.values.toList(), processingContext
              )
            } catch (e: Exception) {
              log.error("Error during strategy queue re-prioritization for '$url'", e)
            }


            // Handle different page types
            if (pageResult.pageType == PageType.Error) {
              log.warn("Strategy returned error for '$url': ${pageResult.metadata["error"]}")
              this.appendLine("*Error processing this result: ${pageResult.metadata["error"]}*")
              this.appendLine()
              saveStrategyResult(
                webSearchDir.resolve("error").apply { mkdirs() }, url, pageResult, currentIndex
              )
              task.add(
                "*Error processing this result: ${pageResult.metadata["error"]}*".renderMarkdown(
                  true
                )
              )
              statusBuffer?.setLength(0); task.update()
              return@buildString
            }

            if (pageResult.pageType == PageType.Irrelevant) {
              log.info("Strategy marked content as irrelevant for '$url'")
              this.appendLine("*Irrelevant content, skipping this result*")
              this.appendLine()
              saveStrategyResult(
                webSearchDir.resolve("irrelevant").apply { mkdirs() }, url, pageResult, currentIndex
              )
              task.add("*Irrelevant content, skipping this result*".renderMarkdown(true))
              statusBuffer?.setLength(0); task.update()
              return@buildString
            }

            saveStrategyResult(webSearchDir, url, pageResult, currentIndex)
            statusBuffer?.setLength(0); task.update()
            task.add(pageResult.content.renderMarkdown(true))

            this.appendLine(pageResult.content)
            this.appendLine()
            // Check for early termination
            if (pageResult.shouldTerminate) {
              log.info("Strategy requested termination: ${pageResult.terminationReason}")
              this.appendLine()
              this.appendLine("---")
              this.appendLine()
              this.appendLine("**Crawling terminated:** ${pageResult.terminationReason}")
              task.add("\n\n**Crawling terminated:** ${pageResult.terminationReason}".renderMarkdown(true))
              this.appendLine()
            }

            if (typeConfig.follow_links == true) {

              var linkData = pageResult.extractedLinks
              val allowRevisit = typeConfig.allow_revisit_pages == true
              if (linkData.isNullOrEmpty()) {
                linkData = extractLinksFromMarkdown(pageResult.content)
                log.debug("Extracted ${linkData.size} links from markdown for '$url'")
              } else {
               // Normalize URLs in extracted links to remove fragments
               linkData = linkData.map { link ->
                 link.apply { url = url.let { normalizeUrl(it) } }
               }
                log.debug("Using ${linkData.size} structured links from analysis for '$url'")
              }
              // Add extracted links section to UI
              if (linkData.isNotEmpty()) {
                this.appendLine()
                this.appendLine("### Extracted Links (${linkData.size} found)")
                task.add("### Extracted Links (${linkData.size} found)".renderMarkdown(true))
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
                task.add(skippedBlock.renderMarkdown(true))
                this.appendLine()
              }
              if (addedLinksBuffer.isNotEmpty()) task.add(
                addedLinksBuffer.toString().renderMarkdown(true)
              )

              log.info("Added $addedCount new links to queue from '$url' (filtered from ${linkData.size} total)")
              // Add summary
              if (linkData.isNotEmpty()) {
                this.appendLine()
                this.appendLine("**Link Processing Summary:** ${addedCount} added to queue, ${skippedLinks.size} skipped")
                this.appendLine()
              }
              transcriptStream?.let { stream ->
                writeToTranscriptSafe(
                  stream, buildString {
                    appendLine()
                    appendLine("### Summary for [${title}]($url)")
                    appendLine()
                    pageResult.summary?.apply { appendLine(this) }
                    appendLine("<details>")
                    appendLine("<summary>")
                    appendLine("**Links Found:** ${linkData.size}, **Added to Queue:** $addedCount, **Skipped:** ${skippedLinks.size}")
                    appendLine("</summary>")
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
                  })
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
                writeToTranscriptSafe(stream, buildString {
                  appendLine("**Error:** ${e.message}")
                  appendLine()
                  if (verbose) {
                    appendLine("<details><summary>Stack Trace</summary>")
                    appendLine()
                    appendLine("```")
                    appendLine(e.stackTraceToString())
                    appendLine("```")
                    appendLine()
                    appendLine("</details>")
                    appendLine()
                  }
                })
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
        analysisResultsMap[currentIndex] =
          "## ${currentIndex}. [${page.title}](${link})\n\n*Error processing this result: ${e.message}*\n\n"
        // Log error to transcript (Triple Log Rule)
        transcriptStream?.let { stream ->
          try {
            writeToTranscriptSafe(stream, buildString {
              appendLine("### Error Processing Page ${currentIndex}: [${page.title}](${link})")
              appendLine()
              appendLine("<details><summary>Stack Trace</summary>")
              appendLine()
              appendLine("```")
              appendLine(e.stackTraceToString())
              appendLine("```")
              appendLine()
              appendLine("</details>")
              appendLine()
            })
          } catch (ex: Exception) {
            log.debug("Failed to write page error to transcript", ex)
          }
        }
      } finally {
        // Log page completion to transcript
        transcriptStream?.let { stream ->
          try {
            writeToTranscriptSafe(
              stream,
              "**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}\n"
            )
            writeToTranscriptSafe(
              stream, "**Processing Time:** ${System.currentTimeMillis() - pageStartTime}ms\n\n---\n\n"
            )
          } catch (e: Exception) {
            log.debug("Failed to write page completion to transcript (stream may be closed)", e)
          }
        }

        page.completed = true
        page.processingTimeMs = System.currentTimeMillis() - pageStartTime
        log.debug("Page processing completed: url='${link}', time=${page.processingTimeMs}ms, error='${page.error ?: "none"}'")
       // Update crawl state for this page (only if use_state_file is enabled)
       if (typeConfig.use_state_file) {
         try {
           val entry = currentCrawlState.links.getOrPut(link) {
             CrawlLinkEntry(
               url = link,
               discovered_at = LocalDateTime.now().toString()
             )
           }
           entry.title = page.title ?: entry.title
           entry.tags = page.tags ?: entry.tags
           entry.relevance_score = page.relevance_score
           entry.depth = page.depth
           entry.status = if (page.error != null) "error" else "completed"
           entry.error = page.error
           entry.processing_time_ms = page.processingTimeMs
           entry.processed_at = LocalDateTime.now().toString()
           entry.completed_in_run = currentRunNumber
           // Periodic save every 5 pages
           if (processedCount.get() % 5 == 0) {
             syncInMemoryStateToCrawlState()
             crawlStateFile?.let { saveCrawlState(it, currentCrawlState) }
           }
         } catch (e: Exception) {
           log.debug("Failed to update crawl state for page: $link", e)
         }
       }
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
        (typeConfig.allowed_domains?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: listOf()).toSet()
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
    if (analysisResults.length < typeConfig.max_final_output_size * 1.2) {
      log.info("Analysis results only slightly exceed max size, truncating instead of summarizing")
      return analysisResults.substring(
        0, min(analysisResults.length, typeConfig.max_final_output_size)
      ) + "\n\n---\n\n*Note: Some content has been truncated due to length limitations.*"
    }

    val headerEndIndex = analysisResults.indexOf("## 1. [")
    val header = if (headerEndIndex > 0) {
      analysisResults.substring(0, headerEndIndex)
    } else {
      "# Web Search: ${executionConfig?.search_query?.joinToString(", ") ?: executionConfig?.direct_urls?.joinToString(", ") ?: ""}\n\n"
    }

    val urlSections = extractUrlSections(analysisResults)
    log.info("Extracted ${urlSections.size} URL sections for summarization")
    val summaryPrompt = buildString {
      appendLine("Create a comprehensive summary of the following web search results and analyses.")
      appendLine()
      appendLine("Original analysis contained ${urlSections.size} web pages related to: ${executionConfig?.search_query?.joinToString(", ") ?: ""}")
      appendLine()
      appendLine("Analysis goal: ${executionConfig?.content_queries ?: executionConfig?.task_description ?: "Provide key insights"}")
      appendLine()
      appendLine("For each source, extract the most important insights, facts, and conclusions.")
      appendLine("Organize information by themes rather than by source when possible.")
      appendLine("Use markdown formatting with headers, bullet points, and emphasis where appropriate.")
      appendLine("Include the most important links that should be followed up on.")
      appendLine("Keep your response under ${typeConfig.max_final_output_size / 1000}K characters.")
    }
    val summary = ChatAgent(
        prompt = summaryPrompt,
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
      tabs: TabbedDisplay, processedCount: Int, errorCount: Int
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
              val depth = "N/A"
              val processingTime = "N/A"
              val error = ""
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
      queueDetailsTask.complete(queueDetails.renderMarkdown(true))
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
    log.debug("Fetching content for URL: {} using method: {}", url, typeConfig.fetch_method)

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
     val linkUrl = normalizeUrl(matcher.group(2))
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
      webSearchDir: File, url: String, result: PageProcessingStrategy.PageProcessingResult, index: Int
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
        "query" to (executionConfig?.search_query?.joinToString(", ") ?: ""),
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

    @JvmStatic
    val CrawlerAgent = TaskType(
        "CrawlerAgent",
        "Online & Search",
        CrawlerAgentTask::class.java,
        CrawlerTaskExecutionConfigData::class.java,
        CrawlerTaskTypeConfig::class.java,
        "Search Google, fetch top results, and analyze content",
        buildString {
            append("Searches Google for specified queries and analyzes the top results.")
            append("<ul>")
            append("<li>Performs Google searches</li>")
            append("<li>Fetches top search results</li>")
            append("<li>Analyzes content for specific goals</li>")
            append("<li>Generates detailed analysis reports</li>")
            append("</ul>")
        },
    )

  }

}