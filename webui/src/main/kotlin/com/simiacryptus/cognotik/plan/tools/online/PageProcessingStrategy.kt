package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.LinkData
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.PageType
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

interface PageProcessingStrategy {
  val description: String

  /**
   * Process a single page and return results
   */
  fun processPage(
    url: String,
    content: String,
    context: ProcessingContext
  ): PageProcessingResult

  /**
   * Determine if crawling should continue
   */
  fun shouldContinueCrawling(
    currentResults: List<PageProcessingResult>,
    context: ProcessingContext
  ): ContinuationDecision

  /**
   * Generate final output from all processed pages
   */
  fun generateFinalOutput(
    results: List<PageProcessingResult>,
    context: ProcessingContext
  ): String

  /**
   * Strategy-specific configuration validation
   */
  fun validateConfig(config: Any?): String?
  data class ProcessingContext(
    val executionConfig: CrawlerTaskExecutionConfigData,
    val typeConfig: CrawlerTaskTypeConfig,
    val orchestrationConfig: OrchestrationConfig,
    val task: SessionTask,
    val webSearchDir: File,
    val processedCount: AtomicInteger,
    val maxPages: Int
  )

  data class PageProcessingResult(
    val url: String,
    val pageType: PageType,
    val content: String,
    val extractedLinks: List<LinkData>?,
    val metadata: Map<String, Any>,
    val shouldTerminate: Boolean = false,
    val terminationReason: String? = null
  )

  data class ContinuationDecision(
    val shouldContinue: Boolean,
    val reason: String
  )
}

