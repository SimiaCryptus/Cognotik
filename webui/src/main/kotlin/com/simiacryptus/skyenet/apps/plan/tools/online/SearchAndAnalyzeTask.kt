package com.simiacryptus.skyenet.apps.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.util.HtmlSimplifier
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SearchAndAnalyzeTask(
  planSettings: PlanSettings,
  planTask: SearchAndAnalyzeTaskConfigData?
) : AbstractTask<SearchAndAnalyzeTask.SearchAndAnalyzeTaskConfigData>(planSettings, planTask) {

  class SearchAndAnalyzeTaskConfigData(
    @Description("The search query to use for Google search")
    val search_query: String = "",
    @Description("The question(s) considered when processing the content")
    val content_query: String = "",
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
  ) : TaskConfigBase(
    task_type = TaskType.SearchAndAnalyze.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment() = """
    SearchAndAnalyze - Search Google, fetch top results, and analyze content
    ** Specify the search query
    ** Specify number of results to analyze (max 20)
    ** Specify the analysis goal or focus
    ** Optionally configure HTML processing:
       - keep_script_elements: Keep script elements
       - keep_interactive_elements: Keep forms and buttons
       - keep_media_elements: Keep audio and video elements
       - keep_event_handlers: Keep event handler attributes
  """.trimIndent()

  override fun run(
    agent: PlanCoordinator,
    messages: List<String>,
    task: SessionTask,
    api: ChatClient,
    resultFn: (String) -> Unit,
    api2: OpenAIClient,
    planSettings: PlanSettings
  ) {
    // First perform Google search
    val searchResults = performGoogleSearch(planSettings)
    val searchData: Map<String, Any> = ObjectMapper().readValue(searchResults)

    // Process each result
    val items = (searchData["items"] as List<Map<String, Any>>?)?.take(20)
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


      items?.forEachIndexed { index, item ->
        val url = item["link"] as String
        appendLine("## ${index + 1}. [${item["title"]}]($url)")
        appendLine()
        
        try {
          // Fetch and transform content for each result
          val client = HttpClient.newBuilder().build()
          val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
          val response = client.send(request, HttpResponse.BodyHandlers.ofString())
          val contentType = response.headers().firstValue("Content-Type").orElse("")
          if (contentType.isNotEmpty() && !contentType.startsWith("text/")) {
            appendLine("*Non-text data ($contentType), skipping this result*")
            appendLine()
            return@forEachIndexed
          }
          val body = response.body()
          if (body.isBlank()) {
            appendLine("*Empty response, skipping this result*")
            appendLine()
            return@forEachIndexed
          }
          val content = HtmlSimplifier.scrubHtml(
            str = body,
            baseUrl = url,
            includeCssData =  false,
            simplifyStructure =  true,
            keepObjectIds =  false,
            preserveWhitespace =  false,
            keepScriptElements =  false,
            keepInteractiveElements =  false,
            keepMediaElements =  false,
            keepEventHandlers =  false
          )
          if (content.isBlank()) {
            appendLine("*Empty content, skipping this result*")
            appendLine()
            return@forEachIndexed
          }
          val analysisGoal = when {
            taskConfig?.content_query?.isNotBlank() == true -> taskConfig.content_query
            taskConfig?.task_description?.isNotBlank() == true -> taskConfig.task_description!!
            else -> "Analyze the content and provide insights."
          }
          val analysis = transformContent(content, analysisGoal, api, planSettings)
          appendLine(analysis)
          appendLine()
        } catch (e: Exception) {
          log.error("Error processing URL: $url", e)
          appendLine("*Error processing this result: ${e.message}*")
          appendLine()
        }
      }
    }

    task.add(MarkdownUtil.renderMarkdown(analysisResults, ui = agent.ui))
    resultFn(analysisResults)
  }
  
  private fun performGoogleSearch(planSettings: PlanSettings): String {
    val client = HttpClient.newBuilder().build()
    val encodedQuery = URLEncoder.encode(taskConfig?.search_query?.trim(), "UTF-8")
    val uriBuilder = "https://www.googleapis.com/customsearch/v1?key=${planSettings.googleApiKey}" +
        "&cx=${planSettings.googleSearchEngineId}&q=$encodedQuery&num=${10}"
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
    val prompt = "Analyze the following web content according to this goal: $analysisGoal\n\nContent:\n$content\n\nAnalysis:"
    return SimpleActor(
      prompt = prompt,
      model = planSettings.defaultModel,
    ).answer(listOf(prompt), api)
  }

  companion object {
    private val log = LoggerFactory.getLogger(SearchAndAnalyzeTask::class.java)
  }
}