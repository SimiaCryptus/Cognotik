package com.simiacryptus.cognotik.plan.tools.online

    import com.simiacryptus.cognotik.describe.Description
    import com.simiacryptus.cognotik.plan.OrchestrationConfig
    import com.simiacryptus.cognotik.plan.TaskOrchestrator
    import com.simiacryptus.cognotik.plan.safeComplete
    import com.simiacryptus.cognotik.plan.tools.AbstractTask
    import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
    import com.simiacryptus.cognotik.plan.tools.TaskType
    import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
    import com.simiacryptus.cognotik.util.LoggerFactory
    import com.simiacryptus.cognotik.util.TabbedDisplay
    import com.simiacryptus.cognotik.util.ValidatedObject
    import com.simiacryptus.cognotik.util.renderMarkdown
    import com.simiacryptus.cognotik.webui.session.SessionTask
    import org.openqa.selenium.JavascriptExecutor
    import org.openqa.selenium.OutputType
    import org.openqa.selenium.TakesScreenshot
    import org.openqa.selenium.WebDriver
    import org.openqa.selenium.chrome.ChromeDriver
    import org.openqa.selenium.chrome.ChromeOptions
    import org.openqa.selenium.chrome.ChromeOptions.LOGGING_PREFS
    import org.openqa.selenium.logging.LogType
    import org.slf4j.Logger
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
        orchestrationConfig: OrchestrationConfig,
        planTask: SeleniumFetchTaskExecutionConfigData?
    ) : AbstractTask<SeleniumFetchTask.SeleniumFetchTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

        class SeleniumFetchTaskExecutionConfigData(
            @Description("URL or local file path to load. Local paths are resolved against the project root if not absolute. Use 'file://' URIs for explicit file access.")
            var target: String = "",
            @Description("Number of seconds to wait after page load before capturing diagnostics (default: 1)")
            var diagnostic_delay_seconds: Long = 1L,
            @Description("Maximum seconds to wait for page load (default: 30)")
            var page_load_timeout_seconds: Long = 30L,
            @Description("Browser viewport width in pixels (default: 1920)")
            var viewport_width: Int = 1920,
            @Description("Browser viewport height in pixels (default: 1080)")
            var viewport_height: Int = 1080,
            @Description("Whether to capture a full-page screenshot when supported (default: true)")
            var full_page_screenshot: Boolean = true,
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
            task: SessionTask,
            resultFn: (String) -> Unit,
            orchestrationConfig: OrchestrationConfig
        ) {
            task.ui.pool.submit {
                val executionConfig = this@SeleniumFetchTask.executionConfig ?: run {
                    val msg = "CONFIGURATION ERROR: SeleniumFetchTask has no execution config"
                    log.error(msg)
                    task.safeComplete(msg, log)
                    resultFn(msg)
                    return@submit
                }

                val startTime = System.currentTimeMillis()
                val transcript = task.newUserFileStream(transcriptFile())
                val tabs = TabbedDisplay(task)

                var driver: WebDriver? = null
                try {
                    val targetUrl = resolveTarget(executionConfig.target)
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
                    driver = createDriver(executionConfig)

                    val loadTask = tabs.newTask("Load")
                    loadTask.header("Loading Page", level = 3)
                    loadTask.add("Navigating to `$targetUrl`...".renderMarkdown())

                    driver.manage().timeouts().apply {
                        pageLoadTimeout(Duration.ofSeconds(executionConfig.page_load_timeout_seconds))
                        scriptTimeout(Duration.ofSeconds(executionConfig.page_load_timeout_seconds))
                        implicitlyWait(Duration.ofSeconds(0))
                    }

                    driver.get(targetUrl)

                    // Wait for document.readyState == complete
                    val deadline = System.currentTimeMillis() + executionConfig.page_load_timeout_seconds * 1000
                    while (System.currentTimeMillis() < deadline) {
                        val ready = (driver as? JavascriptExecutor)?.executeScript("return document.readyState") as? String
                        if (ready == "complete") break
                        Thread.sleep(100)
                    }
                    loadTask.add("✅ Page loaded".renderMarkdown())

                    // Settle delay
                    if (executionConfig.diagnostic_delay_seconds > 0) {
                        loadTask.add("Settling for ${executionConfig.diagnostic_delay_seconds}s before capturing diagnostics...".renderMarkdown())
                        TimeUnit.SECONDS.sleep(executionConfig.diagnostic_delay_seconds)
                    }

                    val artifactsTask = tabs.newTask("Artifacts")
                    artifactsTask.header("Capturing Diagnostics", level = 3)

                    // 1. Screenshot
                    val pngOut = getOutputFile("png")
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
                                } else {
                                    artifactsTask.add("⚠️ Driver does not support screenshots".renderMarkdown())
                                }
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to capture screenshot", e)
                            artifactsTask.add("❌ Screenshot capture failed: ${e.message}".renderMarkdown())
                        }
                    }

                    // 2. HTML dump
                    val htmlOut = getOutputFile("html")
                    if (htmlOut != null) {
                        try {
                            val htmlFile = task.resolveUserFile(htmlOut)
                            if (htmlFile != null) {
                                htmlFile.parentFile?.mkdirs()
                                val pageSource = driver.pageSource ?: ""
                                htmlFile.writeText(pageSource)
                                val link = task.linkTo(htmlOut)
                                artifactsTask.add(
                                    """📄 HTML saved (${pageSource.length} chars): <a href="$link" target="_blank">$htmlOut</a>""".renderMarkdown()
                                )
                                transcript?.write("- HTML: $htmlOut (${pageSource.length} chars)\n".toByteArray())
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to capture HTML", e)
                            artifactsTask.add("❌ HTML capture failed: ${e.message}".renderMarkdown())
                        }
                    }

                    // 3. Console log
                    val consoleOut = getOutputFile("console.log")
                    if (consoleOut != null) {
                        try {
                            val consoleFile = task.resolveUserFile(consoleOut)
                            if (consoleFile != null) {
                                consoleFile.parentFile?.mkdirs()
                                val consoleEntries = try {
                                    driver.manage().logs().get(LogType.BROWSER).all
                                } catch (e: Exception) {
                                    log.debug("Browser log not available via logs API: ${e.message}")
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
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to capture console log", e)
                            artifactsTask.add("❌ Console log capture failed: ${e.message}".renderMarkdown())
                        }
                    }

                    // 4. Network log
                    val networkOut = getOutputFile("network.log")
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
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to capture network log", e)
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
                            getOutputFile("png")?.let { "- Screenshot: $it" },
                            getOutputFile("html")?.let { "- HTML: $it" },
                            getOutputFile("console.log")?.let { "- Console log: $it" },
                            getOutputFile("network.log")?.let { "- Network log: $it" },
                        ).forEach { appendLine(it) }
                    }
                    transcript?.write("\n$summary\n".toByteArray())
                    task.safeComplete("SeleniumFetch completed in ${totalTime / 1000}s", log)
                    resultFn(summary)

                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    log.error("SeleniumFetchTask failed for '${executionConfig.target}' after ${duration}ms", e)
                    task.error(e)
                    transcript?.write(
                        buildString {
                            appendLine("## Error")
                            appendLine()
                            appendLine("```")
                            appendLine(e.stackTraceToString())
                            appendLine("```")
                        }.toByteArray()
                    )
                    val errorOutput = buildString {
                        appendLine("# SeleniumFetch Error")
                        appendLine()
                        appendLine("**Target:** ${executionConfig.target}")
                        appendLine("**Error:** ${e.message}")
                        appendLine("**Type:** ${e.javaClass.simpleName}")
                    }
                    task.safeComplete("SeleniumFetch failed: ${e.message}", log)
                    resultFn(errorOutput)
                } finally {
                    try {
                        driver?.quit()
                    } catch (e: Exception) {
                        log.warn("Error quitting WebDriver", e)
                    }
                    transcript?.close()
                }
            }
        }

        /**
         * Resolves the user-provided target into a navigable URL string.
         *  - http(s):// URLs are returned as-is
         *  - file:// URIs are returned as-is
         *  - Everything else is treated as a local path resolved against [root]
         */
        private fun resolveTarget(target: String): String {
            val lower = target.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://")) {
                return target
            }
            // Treat as local path
            val asFile = File(target)
            val resolved = if (asFile.isAbsolute) asFile else root.resolve(target).toFile()
            if (!resolved.exists()) {
                throw IllegalArgumentException("Local target does not exist: ${resolved.absolutePath}")
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
                    "goog:chromeOptions",
                    mapOf(
                        "perfLoggingPrefs" to mapOf(
                            "enableNetwork" to true,
                            "enablePage" to false
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
            } catch (e: Exception) {
                log.debug("Performance log not available: ${e.message}")
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