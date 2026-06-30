package com.simiacryptus.cognotik.crawl.fetch

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.crawl.CrawlerAgentTask
import com.simiacryptus.cognotik.util.HtmlSimplifier
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * A [FetchStrategy] that uses a headless Selenium/ChromeDriver session to render
 * JavaScript-heavy pages before extracting their text content.
 *
 * Driver instances are pooled per-thread via a [ThreadLocal] so that concurrent
 * crawl tasks each get their own isolated browser session without paying the
 * startup cost on every request.
 */
open class SeleniumFetchStrategy(private val task: CrawlerAgentTask) : FetchStrategy {

    open val pageLoadTimeoutSeconds: Long = 30L

    open val scriptSettleMillis: Long = 1_500L

    open val elementWaitSeconds: Long = 10L

    open val contentReadySelector: String = "body"

    private val driverLocal: ThreadLocal<WebDriver> = ThreadLocal.withInitial {
        log.info("Initialising ChromeDriver for thread: ${Thread.currentThread().name}")
        createDriver()
    }

    open fun simplifyHtml(body: String, url: String): String {
        var simplified = HtmlSimplifier.scrubHtml(
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

        // Check for reasonable content length
        if (simplified.length > 5_000_000) { // 5MB limit
            FetchMethod.log.info("Content too large (${simplified.length} chars) for URL: $url, truncating")
            simplified = simplified.substring(0, 1_000_000)
        }
        return simplified
    }

    open fun createDriver(): WebDriver {
        val options = buildChromeOptions()
        return ChromeDriver(options)
    }

    open fun buildChromeOptions(): ChromeOptions = ChromeOptions().apply {
        addArguments(
            "--headless=new",          // new headless mode (Chrome 112+)
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--disable-extensions",
            "--disable-infobars",
            "--disable-notifications",
            "--disable-popup-blocking",
            "--blink-settings=imagesEnabled=false",   // skip image downloads
            "--window-size=1920,1080",
            "--user-agent=Mozilla/5.0 (compatible; CognotikBot/1.0; +https://github.com/SimiaCryptus/cognotik)"
        )
        // Accept insecure (self-signed) certificates – mirrors BasicHttpClientStrategy behaviour
        setAcceptInsecureCerts(true)
    }

    protected val driver: WebDriver get() = driverLocal.get()

    /**
     * Cleanly quits the driver bound to the calling thread and removes it from
     * the [ThreadLocal].  Call this from a thread-pool shutdown hook or test
     * tear-down.
     */
    fun quitDriver() {
        try {
            driverLocal.get()?.quit()
        } catch (e: Exception) {
            log.warn("Error quitting WebDriver", e)
        } finally {
            driverLocal.remove()
        }
    }

    override fun fetch(
        url: String,
        webSearchDir: File,
        index: Int,
        pool: ExecutorService,
        orchestrationConfig: OrchestrationConfig
    ): String {
        log.info("Selenium fetching URL: $url (index: $index)")
        val wd = driver
        configureTimeouts(wd)
        return try {
            navigateTo(wd, url)
            waitForContent(wd, url)
            val pageSource = wd.pageSource ?: ""
           val renderedText = simplifyHtml(pageSource, url)
            task.urlContentCache[url] = renderedText
            log.info("Selenium successfully processed URL: $url, content length: ${renderedText.length}")
            renderedText
        } catch (e: Exception) {
            log.error("Selenium failed to fetch URL: $url", e)
            captureErrorScreenshot(wd, webSearchDir, url, index)
            throw RuntimeException("Selenium failed to fetch URL: $url – ${e.message}", e)
        }
    }

    open fun configureTimeouts(wd: WebDriver) {
        wd.manage().timeouts().apply {
            pageLoadTimeout(Duration.ofSeconds(pageLoadTimeoutSeconds))
            scriptTimeout(Duration.ofSeconds(pageLoadTimeoutSeconds))
            implicitlyWait(Duration.ofSeconds(0))   // we use explicit waits
        }
    }

    open fun navigateTo(wd: WebDriver, url: String) {
        log.debug("Navigating to: $url")
        wd.get(url)
    }

    open fun waitForContent(wd: WebDriver, url: String) {
        val wait = WebDriverWait(wd, Duration.ofSeconds(pageLoadTimeoutSeconds))

        // 1. Wait for document.readyState
        log.debug("Waiting for document.readyState == complete for: $url")
        wait.until { js(wd, "return document.readyState") == "complete" }

        // 2. Wait for the content selector
        log.debug("Waiting for content selector '$contentReadySelector' on: $url")
        wait.withTimeout(Duration.ofSeconds(elementWaitSeconds))
            .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(contentReadySelector)))

        // 3. Settle delay – lets React/Vue/Angular finish rendering
        if (scriptSettleMillis > 0) {
            log.debug("Settling for ${scriptSettleMillis}ms on: $url")
            TimeUnit.MILLISECONDS.sleep(scriptSettleMillis)
        }
    }

    protected fun js(wd: WebDriver, script: String, vararg args: Any?): Any? =
        (wd as? JavascriptExecutor)?.executeScript(script, *args)

    private fun captureErrorScreenshot(wd: WebDriver, webSearchDir: File, url: String, index: Int) {
        try {
            val screenshotsDir = webSearchDir.resolve("screenshots").also { it.mkdirs() }
            val urlSafe = url.replace(Regex("[^a-zA-Z0-9]"), "_").take(50)
            val dest = screenshotsDir.resolve("error_${urlSafe}_$index.png")
            val src = (wd as? TakesScreenshot)?.getScreenshotAs(OutputType.FILE) ?: return
            src.copyTo(dest, overwrite = true)
            log.info("Error screenshot saved to: ${dest.absolutePath}")
        } catch (e: Exception) {
            log.warn("Could not capture error screenshot for URL: $url", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SeleniumFetchStrategy::class.java)
    }
}