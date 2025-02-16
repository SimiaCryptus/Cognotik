package com.simiacryptus.skyenet.apps.plan.tools.knowledge

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.parse.DocumentRecord
import com.simiacryptus.skyenet.apps.plan.AbstractTask
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskConfigBase
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.util.JsonUtil
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors

class WebSearchAndIndexTask(
  planSettings: PlanSettings,
  planTask: WebSearchAndIndexTaskConfigData?
) : AbstractTask<WebSearchAndIndexTask.WebSearchAndIndexTaskConfigData>(planSettings, planTask) {

  class WebSearchAndIndexTaskConfigData(
    @Description("The search query to use for web search")
    val search_query: String,
    @Description("The number of search results to process (max 10)")
    val num_results: Int = 5,
    @Description("The directory to store downloaded and indexed content")
    val output_directory: String,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
  ) : TaskConfigBase(
    task_type = "WebSearchAndIndex",
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment() = """
    WebSearchAndIndex - Search web, download content, parse and index for future embedding search
    ** Specify the search query
    ** Specify number of results to process (max 10)
    ** Specify output directory for indexed content
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
    val searchResults = performGoogleSearch(planSettings)
    val downloadedFiles = downloadAndSaveContent(searchResults)
    val indexedFiles = indexContent(downloadedFiles, api2)

    val summary = buildString {
      appendLine("# Web Search and Index Results")
      appendLine("## Search Query: ${taskConfig?.search_query}")
      appendLine("## Downloaded and Indexed Files:")
      indexedFiles.forEachIndexed { index, file ->
        appendLine("${index + 1}. ${file.name}")
      }
    }

    resultFn(summary)
  }

  private fun performGoogleSearch(planSettings: PlanSettings): List<Map<String, Any>> {
    val client = HttpClient.newBuilder().build()
    val encodedQuery = URLEncoder.encode(taskConfig?.search_query, "UTF-8")
    val uriBuilder =
      "https://www.googleapis.com/customsearch/v1?key=${planSettings.googleApiKey}&cx=${planSettings.googleSearchEngineId}&q=$encodedQuery&num=${taskConfig?.num_results}"

    val request = HttpRequest.newBuilder()
      .uri(URI.create(uriBuilder))
      .GET()
      .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() != 200) {
      throw RuntimeException("Google API request failed with status ${response.statusCode()}: ${response.body()}")
    }

    val searchResults: Map<String, Any> = JsonUtil.fromJson(response.body(), Map::class.java)
    return (searchResults["items"] as List<Map<String, Any>>?) ?: emptyList()
  }

  private fun downloadAndSaveContent(searchResults: List<Map<String, Any>>): List<File> {
    val outputDir = File(taskConfig?.output_directory ?: "web_content")
    outputDir.mkdirs()

    val client = HttpClient.newBuilder().build()
    return searchResults.mapNotNull { result ->
      try {
        val url = result["link"] as String
        val title = result["title"] as String
        val fileName = "${sanitizeFileName(title)}.html"
        val outputFile = File(outputDir, fileName)

        val request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .GET()
          .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) {
          FileUtils.writeStringToFile(outputFile, response.body(), "UTF-8")
          outputFile
        } else null
      } catch (e: Exception) {
        log.error("Error downloading content", e)
        null
      }
    }
  }

  private fun indexContent(files: List<File>, api: OpenAIClient): List<File> {
    val threadPool = Executors.newFixedThreadPool(8)
    try {
      return DocumentRecord.saveAsBinary(
        openAIClient = api,
        pool = threadPool,
        progressState = null,
        inputPaths = files.map { it.absolutePath }.toTypedArray()
      ).map { File(it) }.toList()
    } finally {
      threadPool.shutdown()
    }
  }

  private fun sanitizeFileName(fileName: String): String {
    return fileName.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50)
  }

  companion object {
    private val log = LoggerFactory.getLogger(WebSearchAndIndexTask::class.java)
  }
}