package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.model.ISessionTask
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.chrome.ChromeOptions.LOGGING_PREFS
import org.openqa.selenium.logging.LogType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * A task that loads a URL or local file in a Selenium-controlled headless Chrome
 * browser and produces various diagnostic artifacts:
 *  - A screenshot of the rendered page (png)
 *  - The browser's console output (console.log)
 *  - A network access log (network.log)
 *  - The page's rendered HTML (html)
 *
 * All diagnostics are captured a configurable delay (default 1s) after the
 * page-load event completes so that asynchronous content has a chance to render.
 */
class SeleniumFetchTask(
  orchestrationConfig: OrchestrationConfig, planTask: SeleniumFetchTaskExecutionConfigData?
) :
  AbstractTask<SeleniumFetchTask.SeleniumFetchTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

  class SeleniumFetchTaskExecutionConfigData(
    @Description("URL or local file path to load. Local paths are resolved against the project root if not absolute. Use 'file://' URIs for explicit file access.") var target: String = "",
    @Description("Number of seconds to wait after page load before capturing diagnostics (default: 1)") var diagnostic_delay_seconds: Long = 1L,
    @Description("Maximum seconds to wait for page load (default: 30)") var page_load_timeout_seconds: Long = 30L,
    @Description("Browser viewport width in pixels (default: 1920)") var viewport_width: Int = 1920,
    @Description("Browser viewport height in pixels (default: 1080)") var viewport_height: Int = 1080,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: AbstractTask.TaskState? = AbstractTask.TaskState.Pending,
  ) : ValidatedObject, TaskExecutionConfig(
    task_type = SeleniumFetch.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ) {
    override fun validate(): String? {
      target = target.trim()
      if (target.isBlank()) return "SeleniumFetchTask requires a non-blank 'target'"
      if (diagnostic_delay_seconds < 0) return "diagnostic_delay_seconds must be >= 0"
      if (page_load_timeout_seconds <= 0) return "page_load_timeout_seconds must be > 0"
      if (viewport_width <= 0 || viewport_height <= 0) return "viewport dimensions must be positive"
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String = buildString {
    appendLine("SeleniumFetch - Load a URL or local file in headless Chrome and capture diagnostics")
    appendLine("  ** Specify 'target' as a URL (http/https) or a local file path / file:// URI")
    appendLine("  ** Configure diagnostic_delay_seconds (default: 1) for post-load settle time")
    appendLine("  ** Captures screenshot (.png), console log, network log, and rendered HTML")
    appendLine("  ** Use this when you need to diagnose a page, capture a visual snapshot,")
    appendLine("     inspect console errors, or examine the post-render DOM")
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    task.pool.submit {
      val executionConfig = this@SeleniumFetchTask.executionConfig ?: run {
        val msg = "CONFIGURATION ERROR: SeleniumFetchTask has no execution config"
        log.error(msg)
        task.safeComplete(msg, log)
        resultFn(msg)
        return@submit
      }

      val startTime = System.currentTimeMillis()
      val transcriptFile = getOutputFile(".md")!!
      val transcript = task.newUserFileStream(transcriptFile)
      fun getOutputFile(extension: String) = transcriptFile.removeSuffix(".md") + extension
      val tabs = TabbedDisplay(task)

      var driver: WebDriver? = null
      var targetUrl: String? = null
      try {
        log.info("SeleniumFetchTask starting for target='${executionConfig.target}'")
        targetUrl = try {
          resolveTarget(executionConfig.target)
        } catch (e: IllegalArgumentException) {
          log.error("Failed to resolve target '${executionConfig.target}': ${e.message}")
          throw e
        } catch (e: Exception) {
          log.error("Unexpected error resolving target '${executionConfig.target}'", e)
          throw e
        }
        transcript?.write("# SeleniumFetch: ${executionConfig.target}\n\n".toByteArray())
        transcript?.write("Resolved target: `$targetUrl`\n\n".toByteArray())

        val overviewTask = tabs.newTask("Overview")
        overviewTask.header("SeleniumFetch", level = 2)
        overviewTask.add(
          buildString {
            appendLine("* **Target:** ${executionConfig.target}")
            appendLine("* **Resolved URL:** $targetUrl")
            appendLine("* **Diagnostic Delay:** ${executionConfig.diagnostic_delay_seconds}s")
            appendLine("* **Page Load Timeout:** ${executionConfig.page_load_timeout_seconds}s")
            appendLine("* **Viewport:** ${executionConfig.viewport_width}x${executionConfig.viewport_height}")
          }.renderMarkdown()
        )

        // Build the driver with logging enabled
        driver = try {
          createDriver(executionConfig)
        } catch (e: Exception) {
          log.error("Failed to create ChromeDriver for target='$targetUrl'", e)
          throw IllegalStateException("Unable to initialize headless Chrome: ${e.message}", e)
        }
        log.info("ChromeDriver initialized successfully")

        val loadTask = tabs.newTask("Load")
        loadTask.header("Loading Page", level = 3)
        loadTask.add("Navigating to `$targetUrl`...".renderMarkdown())

        try {
          driver.manage().timeouts().apply {
            pageLoadTimeout(Duration.ofSeconds(executionConfig.page_load_timeout_seconds))
            scriptTimeout(Duration.ofSeconds(executionConfig.page_load_timeout_seconds))
            implicitlyWait(Duration.ofSeconds(0))
          }
        } catch (e: Exception) {
          log.warn("Failed to set driver timeouts (continuing): ${e.message}", e)
        }

        try {
          driver.get(targetUrl)
          log.info("Navigation completed for target='$targetUrl'")
        } catch (e: org.openqa.selenium.TimeoutException) {
          log.warn(
            "Page load timeout for '$targetUrl' after ${executionConfig.page_load_timeout_seconds}s; will attempt to capture diagnostics anyway",
            e
          )
          loadTask.add("⚠️ Page load timed out, capturing partial state...".renderMarkdown())
        } catch (e: org.openqa.selenium.WebDriverException) {
          log.error("WebDriver error navigating to '$targetUrl': ${e.message}", e)
          throw e
        }

        // Wait for document.readyState == complete
        val deadline = System.currentTimeMillis() + executionConfig.page_load_timeout_seconds * 1000
        while (System.currentTimeMillis() < deadline) {
          val ready = try {
            (driver as? JavascriptExecutor)?.executeScript("return document.readyState") as? String
          } catch (e: Exception) {
            log.debug("readyState poll failed: ${e.message}")
            null
          }
          if (ready == "complete") break
          try {
            Thread.sleep(100)
          } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("Interrupted while waiting for page readyState")
            break
          }
        }
        loadTask.add("✅ Page loaded".renderMarkdown())

        // Settle delay
        if (executionConfig.diagnostic_delay_seconds > 0) {
          loadTask.add("Settling for ${executionConfig.diagnostic_delay_seconds}s before capturing diagnostics...".renderMarkdown())
          try {
            TimeUnit.SECONDS.sleep(executionConfig.diagnostic_delay_seconds)
          } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("Interrupted during settle delay")
          }
        }

        val artifactsTask = tabs.newTask("Artifacts")
        artifactsTask.header("Capturing Diagnostics", level = 3)

        // 1. Screenshot
        val pngOut = getOutputFile(".png")
        if (pngOut != null) {
          try {
            val pngFile = task.resolveUserFile(pngOut)
            if (pngFile != null) {
              pngFile.parentFile?.mkdirs()
              val src = (driver as? TakesScreenshot)?.getScreenshotAs(OutputType.FILE)
              if (src != null) {
                src.copyTo(pngFile, overwrite = true)
                val link = task.linkTo(pngOut)
                artifactsTask.add(
                  """📸 Screenshot saved: <a href="$link" target="_blank">$pngOut</a>""".renderMarkdown()
                )
                artifactsTask.add(
                  """<a href="$link" target="_blank"><img src="$link" style="max-width:600px; border:1px solid #ccc; border-radius:4px;" /></a>"""
                )
                transcript?.write("- Screenshot: $pngOut\n".toByteArray())
                log.info("Screenshot captured: $pngOut (${pngFile.length()} bytes)")
              } else {
                log.warn("Driver does not support screenshots (TakesScreenshot not implemented)")
                artifactsTask.add("⚠️ Driver does not support screenshots".renderMarkdown())
              }
            } else {
              log.warn("Could not resolve user file for screenshot output: $pngOut")
            }
          } catch (e: org.openqa.selenium.WebDriverException) {
            log.warn("WebDriver error capturing screenshot for '$targetUrl'", e)
            artifactsTask.add("❌ Screenshot capture failed: ${e.message}".renderMarkdown())
          } catch (e: java.io.IOException) {
            log.warn("I/O error writing screenshot to '$pngOut'", e)
            artifactsTask.add("❌ Screenshot write failed: ${e.message}".renderMarkdown())
          } catch (e: Exception) {
            log.warn("Unexpected error capturing screenshot for '$targetUrl'", e)
            artifactsTask.add("❌ Screenshot capture failed: ${e.message}".renderMarkdown())
          }
        }

        // 2. HTML dump
        val htmlOut = getOutputFile(".html")
        if (htmlOut != null) {
          try {
            val htmlFile = task.resolveUserFile(htmlOut)
            if (htmlFile != null) {
              htmlFile.parentFile?.mkdirs()
              val pageSource = try {
                driver.pageSource ?: ""
              } catch (e: Exception) {
                log.warn("Failed to read pageSource: ${e.message}", e)
                ""
              }
              htmlFile.writeText(pageSource)
              val link = task.linkTo(htmlOut)
              artifactsTask.add(
                """📄 HTML saved (${pageSource.length} chars): <a href="$link" target="_blank">$htmlOut</a>""".renderMarkdown()
              )
              transcript?.write("- HTML: $htmlOut (${pageSource.length} chars)\n".toByteArray())
              log.info("HTML captured: $htmlOut (${pageSource.length} chars)")
            } else {
              log.warn("Could not resolve user file for HTML output: $htmlOut")
            }
          } catch (e: java.io.IOException) {
            log.warn("I/O error writing HTML to '$htmlOut'", e)
            artifactsTask.add("❌ HTML write failed: ${e.message}".renderMarkdown())
          } catch (e: Exception) {
            log.warn("Unexpected error capturing HTML for '$targetUrl'", e)
            artifactsTask.add("❌ HTML capture failed: ${e.message}".renderMarkdown())
          }
        }

        // 3. Console log
        val consoleOut = getOutputFile(".console.log")
        if (consoleOut != null) {
          try {
            val consoleFile = task.resolveUserFile(consoleOut)
            if (consoleFile != null) {
              consoleFile.parentFile?.mkdirs()
              val consoleEntries = try {
                driver.manage().logs().get(LogType.BROWSER).all
              } catch (e: Exception) {
                log.debug("Browser log not available via logs API: ${e.message}", e)
                emptyList()
              }
              val text = buildString {
                if (consoleEntries.isEmpty()) {
                  appendLine("# No console entries captured")
                } else {
                  consoleEntries.forEach { entry ->
                    appendLine("[${entry.level}] ${entry.timestamp} ${entry.message}")
                  }
                }
              }
              consoleFile.writeText(text)
              val link = task.linkTo(consoleOut)
              artifactsTask.add(
                """🖥️ Console log saved (${consoleEntries.size} entries): <a href="$link" target="_blank">$consoleOut</a>""".renderMarkdown()
              )
              transcript?.write("- Console log: $consoleOut (${consoleEntries.size} entries)\n".toByteArray())
              log.info("Console log captured: $consoleOut (${consoleEntries.size} entries)")
            } else {
              log.warn("Could not resolve user file for console log output: $consoleOut")
            }
          } catch (e: java.io.IOException) {
            log.warn("I/O error writing console log to '$consoleOut'", e)
            artifactsTask.add("❌ Console log write failed: ${e.message}".renderMarkdown())
          } catch (e: Exception) {
            log.warn("Unexpected error capturing console log for '$targetUrl'", e)
            artifactsTask.add("❌ Console log capture failed: ${e.message}".renderMarkdown())
          }
        }

        // 4. Network log
        val networkOut = getOutputFile(".network.log")
        if (networkOut != null) {
          try {
            val networkFile = task.resolveUserFile(networkOut)
            if (networkFile != null) {
              networkFile.parentFile?.mkdirs()
              val networkText = captureNetworkLog(driver)
              networkFile.writeText(networkText)
              val link = task.linkTo(networkOut)
              artifactsTask.add(
                """🌐 Network log saved: <a href="$link" target="_blank">$networkOut</a>""".renderMarkdown()
              )
              transcript?.write("- Network log: $networkOut\n".toByteArray())
              log.info("Network log captured: $networkOut (${networkText.length} chars)")
            } else {
              log.warn("Could not resolve user file for network log output: $networkOut")
            }
          } catch (e: java.io.IOException) {
            log.warn("I/O error writing network log to '$networkOut'", e)
            artifactsTask.add("❌ Network log write failed: ${e.message}".renderMarkdown())
          } catch (e: Exception) {
            log.warn("Unexpected error capturing network log for '$targetUrl'", e)
            artifactsTask.add("❌ Network log capture failed: ${e.message}".renderMarkdown())
          }
        }

        val totalTime = System.currentTimeMillis() - startTime
        val summary = buildString {
          appendLine("# SeleniumFetch Complete")
          appendLine()
          appendLine("**Target:** ${executionConfig.target}")
          appendLine("**Resolved URL:** $targetUrl")
          appendLine("**Time:** ${totalTime / 1000.0}s")
          appendLine()
          appendLine("## Artifacts")
          listOfNotNull(
            getOutputFile(".png")?.let { "- Screenshot: $it" },
            getOutputFile(".html")?.let { "- HTML: $it" },
            getOutputFile(".console.log")?.let { "- Console log: $it" },
            getOutputFile(".network.log")?.let { "- Network log: $it" },
          ).forEach { appendLine(it) }
        }
        transcript?.write("\n$summary\n".toByteArray())
        task.safeComplete("SeleniumFetch completed in ${totalTime / 1000}s", log)
        log.info("SeleniumFetchTask completed for target='$targetUrl' in ${totalTime}ms")
        resultFn(summary)
      } catch (e: IllegalArgumentException) {
        val duration = System.currentTimeMillis() - startTime
        log.error("SeleniumFetchTask invalid argument for '${executionConfig.target}' after ${duration}ms: ${e.message}")
        handleError(e, executionConfig, task, transcript, resultFn)
      } catch (e: org.openqa.selenium.WebDriverException) {
        val duration = System.currentTimeMillis() - startTime
        log.error(
          "SeleniumFetchTask WebDriver failure for '${executionConfig.target}' after ${duration}ms", e
        )
        handleError(e, executionConfig, task, transcript, resultFn)

      } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        log.error("SeleniumFetchTask failed for '${executionConfig.target}' after ${duration}ms", e)
        handleError(e, executionConfig, task, transcript, resultFn)
      } finally {
        try {
          driver?.quit()
          if (driver != null) log.debug("WebDriver quit successfully")
        } catch (e: Exception) {
          log.warn("Error quitting WebDriver for target='$targetUrl': ${e.message}", e)
        }
        transcript?.close()
      }
    }
  }

  private fun handleError(
    e: Throwable,
    executionConfig: SeleniumFetchTaskExecutionConfigData,
    task: ISessionTask,
    transcript: java.io.OutputStream?,
    resultFn: (String) -> Unit
  ) {
    try {
      task.error(e)
    } catch (inner: Exception) {
      log.warn("Failed to report task error to UI", inner)
    }
    try {
      transcript?.write(
        buildString {
          appendLine("## Error")
          appendLine()
          appendLine("**Type:** ${e.javaClass.name}")
          appendLine("**Message:** ${e.message}")
          appendLine()
          appendLine("```")
          appendLine(e.stackTraceToString())
          appendLine("```")
        }.toByteArray()
      )
    } catch (inner: Exception) {
      log.warn("Failed to write error to transcript", inner)
    }
    val errorOutput = buildString {
      appendLine("# SeleniumFetch Error")
      appendLine()
      appendLine("**Target:** ${executionConfig.target}")
      appendLine("**Error:** ${e.message}")
      appendLine("**Type:** ${e.javaClass.simpleName}")
    }
    try {
      task.safeComplete("SeleniumFetch failed: ${e.message}", log)
    } catch (inner: Exception) {
      log.warn("Failed to safeComplete after error", inner)
    }
    resultFn(errorOutput)
  }

  /**
   * Resolves the user-provided target into a navigable URL string.
   *  - http(s):// URLs are returned as-is
   *  - file:// URIs are returned as-is
   *  - Everything else is treated as a local path resolved against [root]
   */
  private fun resolveTarget(target: String): String {
    if (target.isBlank()) {
      throw IllegalArgumentException("Target cannot be blank")
    }
    val lower = target.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
      return target
    }
    // Strip a leading file:// scheme so we can resolve the inner path against [root]
    // when it is not absolute. Handles forms like:
    //   file://index.html        -> index.html (treated as relative)
    //   file:///abs/path.html    -> /abs/path.html (absolute)
    //   file:/abs/path.html      -> /abs/path.html (absolute, single slash form)
    //   file://localhost/abs.html-> /abs.html (host stripped)
    val pathPart: String = if (lower.startsWith("file:")) {
      val afterScheme = target.substring("file:".length)
      when {
        // file:///path or file://host/path -> take everything from the third slash on
        afterScheme.startsWith("//") -> {
          val rest = afterScheme.substring(2)
          val slashIdx = rest.indexOf('/')
          if (slashIdx >= 0) {
            // file://host/path or file:///path (host portion empty)
            rest.substring(slashIdx)
          } else {
            // file://something with no further slash -> treat 'something' as a relative path
            rest
          }
        }
        // file:/path -> /path
        else -> afterScheme
      }
    } else {
      target
    }
    val resolved = try {
      val asFile = File(pathPart)
      if (asFile.isAbsolute) asFile else root.resolve(pathPart).toFile()
    } catch (e: Exception) {
      throw IllegalArgumentException("Could not resolve target path '$target': ${e.message}", e)
    }
    if (!resolved.exists()) {
      throw IllegalArgumentException("Local target does not exist: ${resolved.absolutePath}")
    }
    if (!resolved.canRead()) {
      throw IllegalArgumentException("Local target is not readable: ${resolved.absolutePath}")
    }
    return resolved.toURI().toString()
  }

  private fun createDriver(cfg: SeleniumFetchTaskExecutionConfigData): WebDriver {
    val options = ChromeOptions().apply {
      addArguments(
        "--headless=new",
        "--no-sandbox",
        "--disable-dev-shm-usage",
        "--disable-gpu",
        "--disable-extensions",
        "--disable-infobars",
        "--disable-notifications",
        "--disable-popup-blocking",
        "--window-size=${cfg.viewport_width},${cfg.viewport_height}",
        "--allow-file-access-from-files",
        "--user-agent=Mozilla/5.0 (compatible; CognotikBot/1.0; +https://github.com/SimiaCryptus/cognotik)"
      )
      setAcceptInsecureCerts(true)

      // Enable verbose browser logging so we can capture the console
      val logPrefs = org.openqa.selenium.logging.LoggingPreferences().apply {
        enable(LogType.BROWSER, Level.ALL)
        enable(LogType.PERFORMANCE, Level.ALL)
        enable(LogType.DRIVER, Level.WARNING)
      }
      setCapability(LOGGING_PREFS, logPrefs)
      setCapability("goog:loggingPrefs", logPrefs)

      // Ask Chrome to enable the Performance log so we get network events
      setCapability(
        "goog:chromeOptions", mapOf(
          "perfLoggingPrefs" to mapOf(
            "enableNetwork" to true, "enablePage" to false
          )
        )
      )
    }
    return ChromeDriver(options)
  }

  /**
   * Captures network requests/responses from Chrome's performance log.
   * Falls back to a minimal placeholder if performance logs are not available.
   */
  private fun captureNetworkLog(driver: WebDriver): String {
    return try {
      val entries = driver.manage().logs().get(LogType.PERFORMANCE).all
      if (entries.isEmpty()) {
        log.debug("Performance log returned 0 entries")
        "# No network entries captured\n"
      } else {
        buildString {
          appendLine("# Network log (${entries.size} performance events)")
          appendLine()
          entries.forEach { entry ->
            // Each performance entry message is a JSON blob; preserve raw form
            appendLine("[${entry.timestamp}] ${entry.message}")
          }
        }
      }
    } catch (e: org.openqa.selenium.WebDriverException) {
      log.debug("Performance log WebDriver error: ${e.message}", e)
      "# Network log unavailable (WebDriver error): ${e.message}\n"
    } catch (e: Exception) {
      log.debug("Performance log not available: ${e.message}", e)
      "# Network log unavailable: ${e.message}\n"
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(SeleniumFetchTask::class.java)

    @JvmStatic
    val SeleniumFetch = TaskType(
      name = "SeleniumFetch",
      category = "Online",
      taskClass = SeleniumFetchTask::class.java,
      executionConfigClass = SeleniumFetchTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Load a URL or local file in headless Chrome and capture screenshot, console log, network log, and HTML.",
      tooltipHtml = buildString {
        appendLine("Loads a target page in a headless Selenium-controlled Chrome browser and captures multiple diagnostic artifacts.")
        appendLine("<ul>")
        appendLine("<li>Supports http(s) URLs and local file targets</li>")
        appendLine("<li>Captures a rendered-page screenshot (.png)</li>")
        appendLine("<li>Captures the browser console output (console.log)</li>")
        appendLine("<li>Captures network activity from Chrome's performance log (network.log)</li>")
        appendLine("<li>Dumps the post-render DOM (.html)</li>")
        appendLine("<li>Configurable post-load settle delay (default 1s) so async content can render</li>")
        appendLine("<li>Configurable viewport size and page-load timeout</li>")
        appendLine("</ul>")
      },
    )
  }
}