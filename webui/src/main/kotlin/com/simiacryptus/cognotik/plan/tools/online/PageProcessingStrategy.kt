package com.simiacryptus.cognotik.plan.tools.online

 import com.simiacryptus.cognotik.plan.OrchestrationConfig
 import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskExecutionConfigData
 import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskTypeConfig
 import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.LinkData
 import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.PageType
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
 import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

interface PageProcessingStrategy {
  val description: String

  /**
   * Process a single page and return results
   * 
   * @param url The URL of the page being processed
   * @param content The content of the page
   * @param context The processing context containing configuration and state
   * @return PageProcessingResult containing the processing outcome
   * @throws Exception if processing fails critically
   */
  fun processPage(
    url: String,
    content: String,
    context: ProcessingContext
  ): PageProcessingResult

  /**
   * Determine if crawling should continue
   * 
   * @param currentResults The results from pages processed so far
   * @param context The processing context
   * @return ContinuationDecision indicating whether to continue and why
   */
  fun shouldContinueCrawling(
    currentResults: List<PageProcessingResult>,
    context: ProcessingContext
  ): ContinuationDecision

  /**
   * Generate final output from all processed pages
   * 
   * @param results All page processing results
   * @param context The processing context
   * @return String containing the final formatted output
   */
  fun generateFinalOutput(
    results: List<PageProcessingResult>,
    context: ProcessingContext
  ): String

  /**
   * Strategy-specific configuration validation
   * 
   * @param config The configuration to validate
   * @return Error message if validation fails, null if valid
   */
  fun validateConfig(config: Any?): String?


  data class ProcessingContext(
    val executionConfig: CrawlerTaskExecutionConfigData,
    val typeConfig: CrawlerTaskTypeConfig,
    val orchestrationConfig: OrchestrationConfig,
    val messages: List<String> = emptyList(),
    val task: SessionTask,
    val webSearchDir: File = File("websearch"),
    val processedCount: AtomicInteger = AtomicInteger(0),
    val maxPages: Int = Int.MAX_VALUE,
    val transcriptStream: FileOutputStream? = null
  )

  data class PageProcessingResult(
    val url: String = "",
    val pageType: PageType = PageType.Error,
    val content: String = "",
    val extractedLinks: List<LinkData>? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val shouldTerminate: Boolean = false,
    val terminationReason: String? = null,
    val error: Throwable? = null
  )

  data class ContinuationDecision(
    val shouldContinue: Boolean = true,
    val reason: String = "No specific reason",
  )
}