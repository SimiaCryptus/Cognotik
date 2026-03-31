package com.simiacryptus.cognotik.util.crawl.fetch

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Selenium2S3
import java.io.File
import java.util.concurrent.ExecutorService

class Selenium : FetchMethodFactory {
  override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = object : FetchStrategy {

//  fun cleanup() {
//    try {
//      selenium?.let {
//        log.info("Cleaning up Selenium WebDriver instance")
//        try {
//          it.quit()
//        } catch (e: Exception) {
//          log.warn("Failed to quit Selenium WebDriver gracefully: ${e.message}")
//        }
//        selenium = null
//        log.debug("Selenium WebDriver cleanup completed")
//      }
//    } catch (e: Exception) {
//      log.error("Error cleaning up Selenium resources", e)
//    }
//  }

    var selenium : Selenium2S3? = null
    override fun fetch(
      url: String,
      webSearchDir: File,
      index: Int,
      pool: ExecutorService,
      orchestrationConfig: OrchestrationConfig
    ): String {
      log.info("Selenium fetching URL: $url (index: $index)")
      return try {
        if (selenium == null) {
          log.debug("Initializing Selenium driver")
          selenium = Selenium2S3(
            pool = pool, cookies = null, driver = Selenium2S3.chromeDriver()
          )
        }
        try {
          log.debug("Navigating to URL with Selenium: $url")
          selenium?.navigate(url)
          val pageSource = selenium?.getPageSource() ?: ""
          log.debug("Retrieved page source with Selenium, length: ${pageSource.length}")
          pageSource
        } finally {
          selenium?.let {
            log.debug("Quitting Selenium driver")
            it.quit()
            selenium = null
          }
        }
      } catch (e: Exception) {
        log.warn("Selenium fetch failed for URL: $url, falling back to HttpClient. Error: ${e.message}", e)
        FetchConfig.isSeleniumEnabled = false
        createStrategy(task).fetch(url, webSearchDir, index, pool, orchestrationConfig)
      }
    }

    override fun isEnabled(): Boolean {
      return FetchConfig.isSeleniumEnabled
    }
  }

  companion object {
    val log = LoggerFactory.getLogger(Selenium::class.java)
  }
}