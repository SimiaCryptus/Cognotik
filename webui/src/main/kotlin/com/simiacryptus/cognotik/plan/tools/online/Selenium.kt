package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.util.Selenium2S3
import java.io.File
import java.util.concurrent.ExecutorService

class Selenium : FetchMethodFactory {
    override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = object : FetchStrategy {
        override fun fetch(
            url: String,
            webSearchDir: File,
            index: Int,
            pool: ExecutorService,
            orchestrationConfig: OrchestrationConfig
        ): String {
            FetchMethod.Companion.log.info("Selenium fetching URL: $url (index: $index)")
            return try {
                if (task.selenium == null) {
                    FetchMethod.Companion.log.debug("Initializing Selenium driver")
                    task.selenium = Selenium2S3(
                        pool = pool, cookies = null, driver = Selenium2S3.Companion.chromeDriver()
                    )
                }
                try {
                    FetchMethod.Companion.log.debug("Navigating to URL with Selenium: $url")
                    task.selenium?.navigate(url)
                    val pageSource = task.selenium?.getPageSource() ?: ""
                    FetchMethod.Companion.log.debug("Retrieved page source with Selenium, length: ${pageSource.length}")
                    pageSource
                } finally {
                    task.selenium?.let {
                        FetchMethod.Companion.log.debug("Quitting Selenium driver")
                        it.quit()
                        task.selenium = null
                    }
                }
            } catch (e: Exception) {
                FetchMethod.Companion.log.warn("Selenium fetch failed for URL: $url, falling back to HttpClient. Error: ${e.message}", e)
                FetchConfig.isSeleniumEnabled = false
                createStrategy(task).fetch(url, webSearchDir, index, pool, orchestrationConfig)
            }
        }

        override fun isEnabled(): Boolean {
            return FetchConfig.isSeleniumEnabled;
        }
    }
}