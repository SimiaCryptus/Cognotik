package com.simiacryptus.skyenet.apps.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.general.renderMarkdown
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.apps.plan.TaskConfigBase
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.indent
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.ParsedResponse
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.util.FixedConcurrencyProcessor
import com.simiacryptus.skyenet.core.util.Selenium
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.toJson
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ExecutorService
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
  val allow_revisit_pages: Boolean = false
) : AbstractTask<CrawlerAgentTask.SearchAndAnalyzeTaskConfigData>(planSettings, planTask) {

  class SearchAndAnalyzeTaskConfigData(
    @Description("The search query to use for Google search") val search_query: String? = null,
    @Description("Direct URLs to analyze (comma-separated)") val direct_urls: String? = null,
    @Description("The question(s) considered when processing the content") val content_queries: Any? = null,
    @Description("Whether to follow links found in the analysis") val follow_links: Boolean? = true,
    @Description("Method to seed the crawler (GoogleSearch or DirectUrls)") val seed_method: SeedMethod = SeedMethod.GoogleSearch,
    @Description("Method used to fetch content from  URLs (HttpClient or Selenium)") val fetch_method: FetchMethod = FetchMethod.HttpClient,
    @Description("Maximum number of pages to process in a single task") val max_pages_per_task: Int? = 30,
    @Description("Whether to create a final summary of all results") val create_final_summary: Boolean? = true,
    @Description("Maximum size of the final output in characters") val max_final_output_size: Int? = 10000,
    @Description("Number of pages to process concurrently") val concurrent_page_processing: Int? = 5,
    @Description("Whether to allow analyzing the same URL multiple times") val allow_revisit_pages: Boolean? = false,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
  ) : TaskConfigBase(
    task_type = TaskType.SearchAndAnalyze.name, task_description = task_description, task_dependencies = task_dependencies?.toMutableList(), state = state
  )
  
  var selenium: Selenium? = null
  
  val urlContentCache = ConcurrentHashMap<String, String>()
  
  override fun promptSegment() = """
    SearchAndAnalyze - Search Google, fetch top results, and analyze content
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
    val visitedUrls = mutableSetOf<String>()
    
    // Only add cached URLs to visited list if we don't allow revisiting pages
    if (!(taskConfig?.allow_revisit_pages ?: allow_revisit_pages)) {
      visitedUrls.addAll(urlContentCache.keys)
    }
    
    // Get the seed strategy based on the selected method
    val seedMethod = taskConfig?.seed_method ?: SeedMethod.GoogleSearch
    val seedItems = seedMethod.createStrategy(this).getSeedItems(taskConfig, planSettings)
    
    // Create processing queue with initial items
    val pageQueue = LinkedBlockingQueue<LinkData>().apply {
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
    
    // Create a thread-safe map to store analysis results by index
    val analysisResultsMap = ConcurrentHashMap<Int, String>()
    val processedCount = AtomicInteger(0)
    val maxPages = taskConfig?.max_pages_per_task ?: max_pages_per_task
    val concurrentProcessing = taskConfig?.concurrent_page_processing ?: concurrent_page_processing
    
    // Header for the analysis results
    val header = buildString {
      appendLine("# Analysis of Search Results")
      appendLine()
      // Display seed method, search query or direct URLs
      when (seedMethod) {
        SeedMethod.GoogleSearch -> {
          appendLine("**Search Query:** ${taskConfig?.search_query}")
        }
        SeedMethod.DirectUrls -> {
          appendLine("**Direct URLs:**")
          taskConfig?.direct_urls?.split(",")?.forEach { url ->
            appendLine("- $url")
          }
        }
      }
      appendLine()
      appendLine("**URLs Analyzed:**")
      seedItems?.forEach { item ->
        appendLine("- [${item["title"]}](${item["link"]})")
      }
      appendLine()
      appendLine("---")
      appendLine()
    }
    task.add(header.renderMarkdown())
    
    val tabs = TabbedDisplay(task)
    val exeManager = FixedConcurrencyProcessor(agent.pool, concurrentProcessing)
    pageQueue.take(maxPages).map { page ->
      exeManager.submit {
        val currentIndex = processedCount.incrementAndGet()
        if (currentIndex > maxPages) return@submit
        try {
          val url = page.link
          val title = page.title
          val allowRevisit = taskConfig?.allow_revisit_pages ?: allow_revisit_pages
          if (!allowRevisit && url in visitedUrls) {
            log.info("Skipping already visited URL: $url")
            return@submit
          }
          if (!allowRevisit) visitedUrls.add(url)
          val processPageResult = processPage(currentIndex, title, url, webSearchDir, agent.pool, taskConfig, api, planSettings, visitedUrls, pageQueue, page.depth)
          agent.ui.newTask(false).apply {
            tabs[page.link] = placeholder
            add(processPageResult.renderMarkdown)
          }
          analysisResultsMap[currentIndex] = processPageResult
        } catch (e: Exception) {
          task.error(agent.ui, e)
          log.error("Error processing page: ${page?.link}", e)
          analysisResultsMap[currentIndex] = "## ${currentIndex}. [${page?.title}](${page?.link})\n\n*Error processing this result: ${e.message}*\n\n"
        }
      }
    }.toTypedArray().forEach { it.get() }
    
    val analysisResults = header + (1..processedCount.get()).asSequence().mapNotNull {
      analysisResultsMap[it]
    }.joinToString("\n")
    val finalOutput = if (taskConfig?.create_final_summary != false && analysisResults.length > (taskConfig?.max_final_output_size ?: max_final_output_size)) {
      createFinalSummary(analysisResults, api, planSettings)
    } else {
      analysisResults
    }
    agent.ui.newTask(false).apply {
      tabs["Final Summary"] = placeholder
      add(MarkdownUtil.renderMarkdown(finalOutput, ui = agent.ui))
    }
    resultFn(finalOutput)
  }
  
  private fun processPage(
    index: Int,
    title: String,
    url: String,
    webSearchDir: File,
    pool: ExecutorService,
    taskConfig: SearchAndAnalyzeTaskConfigData?,
    api: API,
    planSettings: PlanSettings,
    visitedUrls: MutableSet<String>,
    pageQueue: LinkedBlockingQueue<LinkData>,
    depth: Int = 0
  ) = buildString {
    appendLine("## ${index}. [${title}]($url)")
    appendLine()
    
    try {
      // Fetch and transform content
      val content = fetchAndProcessUrl(url, webSearchDir, index, pool)
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
      val analysis: ParsedResponse<ParsedPage> = transformContent(content, analysisGoal, api, planSettings)
      
      if (analysis.obj.page_type == PageType.Error) {
        appendLine("*Error processing this result: ${analysis.obj.page_information?.let { JsonUtil.toJson(it) }}*")
        appendLine()
        saveAnalysis(webSearchDir.resolve("error").apply {
          mkdirs()
        }, url, analysis, index)
        return@buildString
      }
      
      if (analysis.obj.page_type == PageType.Irrelevant) {
        appendLine("*Irrelevant content, skipping this result*")
        appendLine()
        saveAnalysis(webSearchDir.resolve("irrelevant").apply {
          mkdirs()
        }, url, analysis, index)
        return@buildString
      }
      
      saveAnalysis(webSearchDir, url, analysis, index)
      
      appendLine(analysis.text)
      appendLine()
      
      if (taskConfig?.follow_links ?: follow_links) {
        // First try to use the structured link data if available
        val linkData = analysis.obj.link_data
        val allowRevisit = taskConfig?.allow_revisit_pages ?: allow_revisit_pages
        
        if (!linkData.isNullOrEmpty()) {
          synchronized(visitedUrls) {
            linkData
              .filter { link -> 
                (allowRevisit || link.link !in visitedUrls) && 
                !pageQueue.any { it.link == link.link } 
              }
              .forEach { link ->
                pageQueue.add(
                  link.apply { this.depth = depth + 1 }
                )
                if (!allowRevisit) visitedUrls.add(link.link)
              }
          }
        } else {
          // Fallback to extracting links from markdown
          val extractedLinks = extractLinksFromMarkdown(analysis.text)
          synchronized(visitedUrls) {
            extractedLinks
              .filter { (_, linkUrl) -> 
                (allowRevisit || linkUrl !in visitedUrls) && 
                !pageQueue.any { it.link == linkUrl } 
              }
              .forEach { (linkText, linkUrl) ->
                pageQueue.add(
                  LinkData(
                    link = linkUrl, 
                    title = linkText,
                    relevance_score = 50.0
                  ).apply { this.depth = depth + 1 }
                )
                if (!allowRevisit) visitedUrls.add(linkUrl)
              }
            }
        }
      }
    } catch (e: Exception) {
      log.error("Error processing URL: $url", e)
      appendLine("*Error processing this result: ${e.message}*")
      appendLine()
    }
  }
  
  private fun createFinalSummary(analysisResults: String, api: API, planSettings: PlanSettings): String {
    log.info("Creating final summary of analysis results (original size: ${analysisResults.length})")
    val maxSize = taskConfig?.max_final_output_size ?: max_final_output_size
    // If the analysis is not too much larger than our target, just truncate it
    if (analysisResults.length < maxSize * 1.5) {
      log.info("Analysis results only slightly exceed max size, truncating instead of summarizing")
      return analysisResults.substring(
        0,
        min(analysisResults.length, maxSize)
      ) + "\n\n---\n\n*Note: Some content has been truncated due to length limitations.*"
    }
    // Extract the header section (everything before the first URL analysis)
    val headerEndIndex = analysisResults.indexOf("## 1. [")
    val header = if (headerEndIndex > 0) {
      analysisResults.substring(0, headerEndIndex)
    } else {
      "# Analysis of Search Results\n\n"
    }
    // Extract all the URL sections
    val urlSections = extractUrlSections(analysisResults)
    log.info("Extracted ${urlSections.size} URL sections for summarization")
    // Create a summary prompt
    val summaryPrompt = listOf(
      "Create a comprehensive summary of the following web search results and analyses.",
      "Original analysis contained ${urlSections.size} web pages related to: ${taskConfig?.search_query ?: ""}",
      "Analysis goal: ${taskConfig?.content_queries ?: taskConfig?.task_description ?: "Provide key insights"}",
      "For each source, extract the most important insights, facts, and conclusions.",
      "Organize information by themes rather than by source when possible.",
      "Use markdown formatting with headers, bullet points, and emphasis where appropriate.",
      "Include the most important links that should be followed up on.",
      "Keep your response under ${maxSize / 1000}K characters.",
      "Here are summaries of each analyzed page:\n${urlSections.joinToString("\n\n")}"
    ).joinToString("\n\n")
    val summary = SimpleActor(
      prompt = summaryPrompt,
      model = taskSettings.model!!,
    ).answer(listOf(summaryPrompt), api)
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
    // Extract the first paragraph or first few sentences
    val firstParagraph = content.split("\n\n").firstOrNull()?.trim() ?: ""
    if (firstParagraph.length < 300) return firstParagraph
    // If first paragraph is too long, get first few sentences
    val sentences = content.split(". ").take(3)
    return sentences.joinToString(". ") + (if (sentences.size >= 3) "..." else "")
  }

  fun fetchAndProcessUrl(url: String, webSearchDir: File, index: Int, pool: ExecutorService): String {
    // Check if URL is already in cache
    val allowRevisit = taskConfig?.allow_revisit_pages ?: allow_revisit_pages
    if (!allowRevisit && urlContentCache.containsKey(url)) {
      log.info("Using cached content for URL: $url")
      return urlContentCache[url]!!
    }
    return (taskConfig?.fetch_method ?: FetchMethod.HttpClient).createStrategy(this).fetch(url, webSearchDir, index, pool, planSettings)
  }

  private fun extractLinksFromMarkdown(markdown: String): List<Pair<String, String>> {
    val links = mutableListOf<Pair<String, String>>()
    val linkPattern = Pattern.compile("""\[([^]]+)]\(([^)]+)\)""")
    val matcher = linkPattern.matcher(markdown)
    val validUrlPattern = Pattern.compile("^(http|https)://.*")
    
    while (matcher.find()) {
      val linkText = matcher.group(1)
      val linkUrl = matcher.group(2)
      try {
        if (validUrlPattern.matcher(linkUrl).matches()) {
          links.add(Pair(linkText, linkUrl))
        }
      } catch (e: Exception) {
        log.warn("Invalid URL found in markdown: $linkUrl")
      }
    }
    return links
  }
  
  
  fun saveRawContent(webSearchDir: File, url: String, content: String, index: Int) {
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
      // Create metadata JSON header
      val metadata = mapOf(
        "url" to url,
        "timestamp" to LocalDateTime.now().toString(),
        "index" to index,
        "query" to (taskConfig?.search_query ?: ""),
        "content_query" to (taskConfig?.content_queries ?: "")
      )
      val metadataJson = ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata)
      // Write file with commented JSON header followed by the analysis
      val contentWithHeader = "<!-- ${metadataJson}${analysis.obj.let { JsonUtil.toJson(it) }} -->\n\n${analysis.text}"
      analysisFile.writeText(contentWithHeader)
    } catch (e: Exception) {
      log.error("Failed to save analysis for URL: $url", e)
    }
  }
  
  
  fun performGoogleSearch(planSettings: PlanSettings): String {
    val client = HttpClient.newBuilder().build()
    val encodedQuery = URLEncoder.encode(taskConfig?.search_query?.trim(), "UTF-8")
    val uriBuilder =
      "https://www.googleapis.com/customsearch/v1?key=${planSettings.googleApiKey}" + "&cx=${planSettings.googleSearchEngineId}&q=$encodedQuery&num=${10}"
    val request = HttpRequest.newBuilder().uri(URI.create(uriBuilder)).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    val statusCode = response.statusCode()
    when {
      statusCode == 400 -> {
        throw RuntimeException("Google API request failed with status $statusCode: ${response.body()}")
      }
      
      statusCode != 200 -> {
        throw RuntimeException("Google API request failed with status $statusCode: ${response.body()}")
      }
      
      else -> {
        return response.body()
      }
    }
  }
  
  private fun transformContent(content: String, analysisGoal: String, api: API, planSettings: PlanSettings): ParsedResponse<ParsedPage> {
    // Check if content is too large and needs to be split
    val maxChunkSize = 50000
    if (content.length <= maxChunkSize) {
      return processContentChunk(content, analysisGoal, api, planSettings)
    }
    // Split content into manageable chunks
    log.info("Content size (${content.length}) exceeds limit, splitting into chunks")
    val chunks = splitContentIntoChunks(content, maxChunkSize)
    log.info("Split content into ${chunks.size} chunks")
    val chunkResults = chunks.mapIndexed { index, chunk ->
      log.info("Processing chunk ${index + 1}/${chunks.size} (size: ${chunk.length})")
      val chunkGoal = "$analysisGoal (Part ${index + 1}/${chunks.size})"
      processContentChunk(chunk, chunkGoal, api, planSettings)
    }
    if (chunkResults.size == 1) {
      return chunkResults[0]
    }
    val combinedAnalysis = chunkResults.joinToString("\n\n---\n\n") { it.text }
    return pageParsedResponse(planSettings, analysisGoal, combinedAnalysis, api)
  }
  
  private fun pageParsedResponse(
    planSettings: PlanSettings,
    analysisGoal: String,
    content: String,
    api: API
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
      model = taskSettings.model!!,
      describer = planSettings.describer(),
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
    // Look for paragraph breaks near the max size
    val paragraphBreakSearch = text.substring(0, minOf(maxSize, text.length)).lastIndexOf("\n\n")
    if (paragraphBreakSearch > maxSize * 0.7) {
      return paragraphBreakSearch + 2 // Include the newlines
    }
    // Look for single newlines
    val newlineSearch = text.substring(0, minOf(maxSize, text.length)).lastIndexOf("\n")
    if (newlineSearch > maxSize * 0.7) {
      return newlineSearch + 1
    }
    // Look for sentence breaks (period followed by space)
    val sentenceSearch = text.substring(0, minOf(maxSize, text.length)).lastIndexOf(". ")
    if (sentenceSearch > maxSize * 0.7) {
      return sentenceSearch + 2 // Include the period and space
    }
    // If no good break point, just use the max size
    return minOf(maxSize, text.length)
  }
  
  private fun processContentChunk(content: String, analysisGoal: String, api: API, planSettings: PlanSettings): ParsedResponse<ParsedPage> {
    return pageParsedResponse(planSettings, analysisGoal, content, api)
  }
  
  companion object {
    private val log = LoggerFactory.getLogger(CrawlerAgentTask::class.java)
  }
}

