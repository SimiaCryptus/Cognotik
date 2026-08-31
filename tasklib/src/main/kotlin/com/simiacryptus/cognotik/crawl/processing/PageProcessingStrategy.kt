package com.simiacryptus.cognotik.crawl.processing

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.crawl.CrawlerAgentTask.*
import com.simiacryptus.cognotik.webui.session.ISessionTask
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

interface PageProcessingStrategy {
  val name: String get() = this::class.simpleName ?: "UnnamedStrategy"
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
   * Allow the strategy to re-prioritize the crawling queue based on newly-parsed data.
   *
   * This is called after each page is processed, giving the strategy an opportunity to
   * adjust the relevance scores of pending (not-yet-processed) links. For example, a
   * fact-checking strategy may want to boost links related to claims that still need
   * more evidence, and de-prioritize links related to claims that are already resolved.
   *
   * The default implementation is a no-op.
   *
   * @param latestResult The result of the page that was just processed
   * @param allResults All page processing results so far
   * @param context The processing context
   */
  fun reprioritizeQueue(
    latestResult: PageProcessingResult,
    allResults: List<PageProcessingResult>,
    context: ProcessingContext
  ) {
    // Default: no re-prioritization
  }


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
    val task: ISessionTask,
    val webSearchDir: File = File("websearch"),
    val processedCount: AtomicInteger = AtomicInteger(0),
    val maxPages: Int = Int.MAX_VALUE,
    val transcriptStream: FileOutputStream? = null,
    /**
     * Re-score the pending crawl queue. The provided function receives a snapshot of the
     * currently-queued links (URL, title, tags, current relevance score) and returns a
     * map of URL -> new relevance score for any links whose priority should change.
     * Links not present in the returned map keep their existing score.
     */
    val reprioritizeQueue: ((scorer: (List<QueuedLinkInfo>) -> Map<String, Double>) -> Unit)? = null
  )

  data class QueuedLinkInfo(
    val url: String,
    val title: String?,
    val tags: List<String>?,
    val relevanceScore: Double,
    val depth: Int
  )

  data class PageProcessingResult(
    val url: String = "",
    val pageType: PageType = PageType.Error,
    val content: String = "",
    val summary: String? = null,
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