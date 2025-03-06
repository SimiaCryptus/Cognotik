package com.simiacryptus.skyenet.apps.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.indent
import com.simiacryptus.skyenet.core.util.Selenium
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.util.HtmlSimplifier
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import com.simiacryptus.skyenet.util.Selenium2S3
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue
import java.util.regex.Pattern
import kotlin.math.min

class SearchAndAnalyzeTask(
  planSettings: PlanSettings,
  planTask: SearchAndAnalyzeTaskConfigData?,
  val follow_links: Boolean = true,
  val max_link_depth: Int = 2,
  val max_pages_per_task: Int = 30,
  val max_final_output_size: Int = 10000,
) : AbstractTask<SearchAndAnalyzeTask.SearchAndAnalyzeTaskConfigData>(planSettings, planTask) {
  enum class FetchMethod {
    @JsonProperty("httpClient") HttpClient,
    @JsonProperty("selenium") Selenium
  }
  

  class SearchAndAnalyzeTaskConfigData(
    @Description("The search query to use for Google search") val search_query: String = "",
    @Description("The question(s) considered when processing the content") val content_query: String = "",
    @Description("Whether to follow links found in the analysis") val follow_links: Boolean = true,
    @Description("Maximum depth for following links") val max_link_depth: Int = 1,
    @Description("Method used to fetch content from URLs (httpClient or selenium)") val fetch_method: FetchMethod = FetchMethod.HttpClient,
    @Description("Maximum number of pages to process in a single task") val max_pages_per_task: Int = 30,
    @Description("Whether to create a final summary of all results") val create_final_summary: Boolean = true,
    @Description("Maximum size of the final output in characters") val max_final_output_size: Int = 10000,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
  ) : TaskConfigBase(
    task_type = TaskType.SearchAndAnalyze.name, task_description = task_description, task_dependencies = task_dependencies?.toMutableList(), state = state
  )
  private var selenium: Selenium? = null
  

  val urlContentCache = ConcurrentHashMap<String, String>()
  
  override fun promptSegment() = """
    SearchAndAnalyze - Search Google, fetch top results, and analyze content
    ** Specify the search query
    ** Specify the analysis goal or focus
    ** Results will be saved to .websearch directory for future reference
    ** Links found in analysis can be automatically followed for deeper research
  """.trimIndent()
  data class PageToProcess(
    val url: String,
    val title: String,
    val depth: Int
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
    val searchResults = performGoogleSearch(planSettings)
    val searchData: Map<String, Any> = ObjectMapper().readValue(searchResults)
    val webSearchDir = File(agent.root.toFile(), ".websearch")
    if (!webSearchDir.exists()) webSearchDir.mkdirs()
    val visitedUrls = mutableSetOf<String>()
    visitedUrls.addAll(urlContentCache.keys)
    
    val items = (searchData["items"] as List<Map<String, Any>>?)?.take(20)
    // Create processing queue
    val pageQueue = LinkedBlockingQueue<PageToProcess>()
    items?.forEach { item ->
      pageQueue.add(PageToProcess(
        url = item["link"] as String,
        title = (item["title"] as? String) ?: "Untitled",
        depth = 0
      ))
    }
    
    val analysisResults = buildString {
      appendLine("# Analysis of Search Results")
      appendLine()
      // Display search query and URLs
      appendLine("**Search Query:** ${taskConfig?.search_query}")
      appendLine()
      appendLine("**URLs Analyzed:**")
      items?.forEach { item ->
        appendLine("- [${item["title"]}](${item["link"]})")
      }
      appendLine()
      appendLine("---")
      appendLine()
      
      var processedCount = 0
      val maxPages = taskConfig?.max_pages_per_task ?: max_pages_per_task
      while (pageQueue.isNotEmpty() && processedCount < maxPages) {
        val page = pageQueue.poll()
        val url = page.url
        val title = page.title
        val depth = page.depth
        
        if (url in visitedUrls) {
          log.info("Skipping already visited URL: $url")
          continue
        }
        visitedUrls.add(url)
        processedCount++
        
        appendLine("## ${processedCount}. [${title}]($url)")
        if (depth > 0) {
          appendLine("*(Follow-up link, depth: $depth)*")
        }
        appendLine()
        
        try {
          // Fetch and transform content for each result
          
          val content = fetchAndProcessUrl(url, webSearchDir, processedCount, agent.pool)
          if (content.isBlank()) {
            appendLine("*Empty content, skipping this result*")
            appendLine()
            continue
          }
          
          val analysisGoal = when {
            taskConfig?.content_query?.isNotBlank() == true -> taskConfig.content_query
            taskConfig?.task_description?.isNotBlank() == true -> taskConfig.task_description!!
            else -> "Analyze the content and provide insights."
          }
          val analysis = transformContent(content, analysisGoal, api, planSettings)
          saveAnalysis(webSearchDir, url, analysis, processedCount)
          
          appendLine(analysis)
          appendLine()
          // Extract and follow links if enabled and not at max depth
          val shouldFollowLinks = taskConfig?.follow_links ?: follow_links
          val maxDepth = taskConfig?.max_link_depth ?: max_link_depth
          if (shouldFollowLinks && depth < maxDepth) {
            extractLinksFromMarkdown(analysis).forEach { (linkText, linkUrl) ->
              if (linkUrl !in visitedUrls && !pageQueue.any { it.url == linkUrl }) {
                pageQueue.add(PageToProcess(
                  url = linkUrl,
                  title = linkText,
                  depth = depth + 1
                ))
              }
            }
          }
        } catch (e: Exception) {
          log.error("Error processing URL: $url", e)
          appendLine("*Error processing this result: ${e.message}*")
          appendLine()
        }
      }
    }
    
    // Check if we need to create a final summary
    val finalOutput = if (taskConfig?.create_final_summary != false && analysisResults.length > (taskConfig?.max_final_output_size ?: max_final_output_size)) {
      createFinalSummary(analysisResults, api, planSettings)
    } else {
      analysisResults
    }
    task.add(MarkdownUtil.renderMarkdown(finalOutput, ui = agent.ui))
    resultFn(finalOutput)
  }
  private fun createFinalSummary(analysisResults: String, api: API, planSettings: PlanSettings): String {
    log.info("Creating final summary of analysis results (original size: ${analysisResults.length})")
    val maxSize = taskConfig?.max_final_output_size ?: max_final_output_size
    // If the analysis is not too much larger than our target, just truncate it
    if (analysisResults.length < maxSize * 1.5) {
      log.info("Analysis results only slightly exceed max size, truncating instead of summarizing")
      return analysisResults.substring(0, min(analysisResults.length, maxSize)) + 
        "\n\n---\n\n*Note: Some content has been truncated due to length limitations.*"
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
      "Analysis goal: ${taskConfig?.content_query ?: taskConfig?.task_description ?: "Provide key insights"}",
      "For each source, extract the most important insights, facts, and conclusions.",
      "Organize information by themes rather than by source when possible.",
      "Use markdown formatting with headers, bullet points, and emphasis where appropriate.",
      "Include the most important links that should be followed up on.",
      "Keep your response under ${maxSize / 1000}K characters.",
      "Here are summaries of each analyzed page:\n${urlSections.joinToString("\n\n")}"
    ).joinToString("\n\n")
    // Generate the summary
    val summary = SimpleActor(
      prompt = summaryPrompt,
      model = planSettings.defaultModel,
    ).answer(listOf(summaryPrompt), api)
    // Combine the header with the summary
    return header + summary
  }
  private fun extractUrlSections(analysisResults: String): List<String> {
    val sections = mutableListOf<String>()
    val sectionPattern = Pattern.compile("## \\d+\\. \\[([^\\]]+)\\]\\(([^)]+)\\)(.*?)(?=## \\d+\\. \\[|$)", Pattern.DOTALL)
    val matcher = sectionPattern.matcher(analysisResults)
    while (matcher.find()) {
      val title = matcher.group(1)
      val url = matcher.group(2)
      val content = matcher.group(3).trim()
      // Create a condensed version of each section
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
  
  
  protected fun fetchAndProcessUrl(url: String, webSearchDir: File, index: Int, pool: ThreadPoolExecutor): String {
    // Check if URL is already in cache
    if (urlContentCache.containsKey(url)) {
      log.info("Using cached content for URL: $url")
      return urlContentCache[url]!!
    }
    fun fetchWithHttpClient(): String { val client = HttpClient.newBuilder().build()
    val request = HttpRequest.newBuilder().uri(URI.create(url))
      .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36").GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    val contentType = response.headers().firstValue("Content-Type").orElse("")
    if (contentType.isNotEmpty() && !contentType.startsWith("text/")) {
      return ""
    }
    val body = response.body()
    if (body.isBlank()) {
      return ""
    }
    saveRawContent(webSearchDir.resolve(".raw"), url, body, index)
    val content = HtmlSimplifier.scrubHtml(
      str = body,
      baseUrl = url,
      includeCssData = false,
      simplifyStructure = true,
      keepObjectIds = false,
      preserveWhitespace = false,
      keepScriptElements = false,
      keepInteractiveElements = false,
      keepMediaElements = false,
      keepEventHandlers = false
    )
    // Cache the processed content
    urlContentCache[url] = content
    return content
  }
  fun fetchWithSelenium(pool: ThreadPoolExecutor): String {
    if (selenium == null) {
      selenium = Selenium2S3(
        pool = pool,
        cookies = null,
        driver = planSettings.driver()
      )
    }
    selenium?.navigate(url)
    return selenium?.getPageSource() ?: ""
  }
    return when (taskConfig?.fetch_method ?: FetchMethod.HttpClient) {
      FetchMethod.HttpClient -> fetchWithHttpClient()
      FetchMethod.Selenium -> {
        try {
          fetchWithSelenium(pool)
        } finally {
          selenium?.let {
            it.quit()
            selenium = null
          }
        }
      }
    }
  }
  
  private fun extractLinksFromMarkdown(markdown: String): List<Pair<String, String>> {
    val links = mutableListOf<Pair<String, String>>()
    val linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)")
    val matcher = linkPattern.matcher(markdown)
    while (matcher.find()) {
      val linkText = matcher.group(1)
      val linkUrl = matcher.group(2)
      // Validate URL
      try {
        val uri = URI.create(linkUrl)
        if (uri.scheme == "http" || uri.scheme == "https") {
          links.add(Pair(linkText, linkUrl))
        }
      } catch (e: Exception) {
        log.warn("Invalid URL found in markdown: $linkUrl")
      }
    }
    return links
  }
  
  
  private fun saveRawContent(webSearchDir: File, url: String, content: String, index: Int) {
    try {
      val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
      val urlSafe = url.replace(Regex("[^a-zA-Z0-9]"), "_").take(50)
      webSearchDir.mkdirs()
      val rawFile = File(webSearchDir, "${urlSafe}_${timestamp}_${index}.html")
      rawFile.writeText(content)
    } catch (e: Exception) {
      log.error("Failed to save raw content for URL: $url", e)
    }
  }
  
  private fun saveAnalysis(webSearchDir: File, url: String, analysis: String, index: Int) {
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
        "content_query" to (taskConfig?.content_query ?: "")
      )
      val metadataJson = ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata)
      // Write file with commented JSON header followed by the analysis
      val contentWithHeader = "<!-- ${metadataJson} -->\n\n$analysis"
      analysisFile.writeText(contentWithHeader)
    } catch (e: Exception) {
      log.error("Failed to save analysis for URL: $url", e)
    }
  }
  
  
  private fun performGoogleSearch(planSettings: PlanSettings): String {
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
  
  private fun transformContent(content: String, analysisGoal: String, api: API, planSettings: PlanSettings): String {
    // Check if content is too large and needs to be split
    val maxChunkSize = 50000
    if (content.length <= maxChunkSize) {
      return processContentChunk(content, analysisGoal, api, planSettings)
    }
    // Split content into manageable chunks
    log.info("Content size (${content.length}) exceeds limit, splitting into chunks")
    val chunks = splitContentIntoChunks(content, maxChunkSize)
    log.info("Split content into ${chunks.size} chunks")
    // Process each chunk
    val chunkResults = chunks.mapIndexed { index, chunk ->
      log.info("Processing chunk ${index + 1}/${chunks.size} (size: ${chunk.length})")
      val chunkGoal = "$analysisGoal (Part ${index + 1}/${chunks.size})"
      processContentChunk(chunk, chunkGoal, api, planSettings)
    }
    // Combine and summarize results
    if (chunkResults.size == 1) {
      return chunkResults[0]
    }
    // Create a summary of all chunks
    val combinedAnalysis = chunkResults.joinToString("\n\n---\n\n")
    val summaryPrompt = listOf(
      "Below are analyses of different parts of a web page related to this goal: $analysisGoal",
      "Individual analyses: \n${combinedAnalysis.indent("  ")}",
      "Create a unified summary that combines the key insights from all parts.",
      "Use markdown formatting for your response, with * characters for bullets.",
      "Identify the most important links that should be followed up on according to the goal."
    ).joinToString("\n\n")
    return SimpleActor(
      prompt = summaryPrompt,
      model = planSettings.defaultModel,
    ).answer(listOf(summaryPrompt), api)
  }
  
  private fun splitContentIntoChunks(content: String, maxChunkSize: Int): List<String> {
    val chunks = mutableListOf<String>()
    var remainingContent = content
    while (remainingContent.isNotEmpty()) {
      val chunkSize = if (remainingContent.length <= maxChunkSize) {
        remainingContent.length
      } else {
        // Try to find a good breaking point (paragraph or sentence)
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
  
  private fun processContentChunk(content: String, analysisGoal: String, api: API, planSettings: PlanSettings): String {
    val promptList = listOf(
      "Analyze the following web content according to this goal: $analysisGoal",
      "Content: \n${content.indent("  ")}",
      "Use markdown formatting for your response, with * characters for bullets.",
      "Identify links that should be followed up on according to the goal.",
    )
    val prompt = promptList.joinToString("\n\n")
    return SimpleActor(
      prompt = prompt,
      model = planSettings.defaultModel,
    ).answer(listOf(prompt), api)
  }
  
  companion object {
    private val log = LoggerFactory.getLogger(SearchAndAnalyzeTask::class.java)
  }
}