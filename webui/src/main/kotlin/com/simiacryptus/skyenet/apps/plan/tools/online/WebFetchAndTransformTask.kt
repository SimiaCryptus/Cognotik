package com.simiacryptus.skyenet.apps.plan.tools.online

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.slf4j.LoggerFactory

open class WebFetchAndTransformTask(
  planSettings: PlanSettings,
  planTask: WebFetchAndTransformTaskConfigData?
) : AbstractTask<WebFetchAndTransformTask.WebFetchAndTransformTaskConfigData>(planSettings, planTask) {
  class WebFetchAndTransformTaskConfigData(
    @Description("The URL to fetch")
    val url: String,
    @Description("The desired format or focus for the transformation")
    val transformationGoal: String,
    @Description("Base URL for resolving relative links")
    val baseUrl: String? = null,
    @Description("Whether to include CSS data in the scrubbed HTML")
    val includeCssData: Boolean = false,
    @Description("Whether to simplify nested HTML structure")
    val simplifyStructure: Boolean = true,
    @Description("Whether to keep object IDs in the HTML")
    val keepObjectIds: Boolean = false,
    @Description("Whether to preserve whitespace in text nodes")
    val preserveWhitespace: Boolean = false,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
  ) : TaskConfigBase(
    task_type = TaskType.WebFetchAndTransform.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    state = state
  )

  override fun promptSegment() = """
        WebFetchAndTransform - Fetch a web page, strip HTML, and transform content
        ** Specify the URL to fetch
        ** Specify the desired format or focus for the transformation
        ** Optionally specify:
           - Base URL for resolving relative links
           - Whether to include CSS data
           - Whether to simplify HTML structure
           - Whether to keep object IDs
           - Whether to preserve whitespace
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
    val fetchedContent = fetchAndStripHtml(taskConfig?.url ?: "")
    val transformedContent = transformContent(fetchedContent, taskConfig?.transformationGoal ?: "", api, planSettings)
    task.add(MarkdownUtil.renderMarkdown(transformedContent, ui = agent.ui))
    resultFn(transformedContent)
  }

  private fun fetchAndStripHtml(url: String): String {
    HttpClients.createDefault().use { httpClient ->
      val httpGet = HttpGet(url)
      httpClient.execute(httpGet).use { response ->
        val entity = response.entity
        val content = EntityUtils.toString(entity)
        return HtmlSimplifier.scrubHtml(
          str = content,
          baseUrl = taskConfig?.baseUrl,
          includeCssData = taskConfig?.includeCssData ?: false,
          simplifyStructure = taskConfig?.simplifyStructure ?: true,
          keepObjectIds = taskConfig?.keepObjectIds ?: false,
          preserveWhitespace = taskConfig?.preserveWhitespace ?: false
        )
      }
    }
  }

  private fun transformContent(content: String, transformationGoal: String, api: API, planSettings: PlanSettings): String {
    val prompt = """
        Transform the following web content according to this goal: $transformationGoal
        
        Content:
        $content
        
        Transformed content:
      """.trimIndent()
    return SimpleActor(
      prompt = prompt,
      model = planSettings.defaultModel,
    ).answer(
      listOf(
        "Transform the following web content according to this goal: $transformationGoal\n\n$content",
      ), api
    )
  }

  companion object {
    private val log = LoggerFactory.getLogger(WebFetchAndTransformTask::class.java)
  }
}