package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import io.github.bonigarcia.wdm.WebDriverManager
import jakarta.servlet.http.Cookie
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.devtools.v136.log.Log
import org.openqa.selenium.devtools.v136.network.Network
import org.openqa.selenium.remote.RemoteWebDriver
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class SeleniumSessionTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SeleniumSessionTaskExecutionConfigData?
) : AbstractTask<SeleniumSessionTask.SeleniumSessionTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {
    companion object {
        private val log = LoggerFactory.getLogger(SeleniumSessionTask::class.java)
        private val activeSessions = ConcurrentHashMap<String, Selenium>()
        private const val TIMEOUT_MS = 30000L

        private const val MAX_SESSIONS = 10

        val SeleniumSession = TaskType(
            "SeleniumSession",
            SeleniumSessionTask.SeleniumSessionTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Automate browser interactions with Selenium",
            """
          Automates browser interactions using Selenium WebDriver.
          <ul>
            <li>Headless Chrome browser automation</li>
            <li>JavaScript command execution</li>
            <li>Session management capabilities</li>
            <li>Configurable timeouts</li>
            <li>Detailed execution results</li>
          </ul>
        """
        )


    }

    private fun cleanupInactiveSessions() {
        activeSessions.entries.removeIf { (id, session) ->
            try {
                if (!session.isAlive()) {
                    log.info("Removing inactive session $id")
                    session.quit()
                    true
                } else false
            } catch (e: Exception) {
                log.warn("Error checking session $id, removing", e)
                try {
                    session.forceQuit()
                } catch (e2: Exception) {
                    log.error("Failed to force quit session $id", e2)
                }
                true
            }
        }
    }

    class SeleniumSessionTaskExecutionConfigData(
        @Description("The URL to navigate to (optional if reusing existing session)")
        val url: String = "",
        @Description("JavaScript commands to execute")
        val commands: List<String> = listOf(),
        @Description("Session ID for reusing existing sessions")
        val sessionId: String? = null,
        @Description("Timeout in milliseconds for commands")
        val timeout: Long = TIMEOUT_MS,
        @Description("Whether to close the session after execution")
        val closeSession: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
        @Description("Include CSS data in page source: styles, classes, etc.")
        val includeCssData: Boolean? = null,
        @Description("Whether to simplify the HTML structure by combining nested elements")
        val simplifyStructure: Boolean = true,
        @Description("Whether to keep object IDs in the HTML output")
        val keepObjectIds: Boolean = false,
        @Description("Whether to preserve whitespace in text nodes")
        val preserveWhitespace: Boolean = false,
    ) : TaskExecutionConfig(
        task_type = SeleniumSession.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = """
      SeleniumSession - Create and manage a stateful Selenium browser session
        * Specify the URL to navigate to
        * Provide JavaScript commands to execute in sequence through Selenium's executeScript method
        * Can be used for web scraping, testing, or automation
        * Session persists between commands for stateful interactions
        * Optionally specify sessionId to reuse an existing session
        * Set closeSession=true to close the session after execution
      Example JavaScript Commands:
        * "return document.title;" - Get page title
        * "return document.querySelector('.my-class').textContent;" - Get element text
        * "return Array.from(document.querySelectorAll('a')).map(a => a.href);" - Get all links
        * "document.querySelector('#my-button').click();" - Click an element
        * "window.scrollTo(0, document.body.scrollHeight);" - Scroll to bottom
        * "return document.documentElement.outerHTML;" - Get entire page HTML
        * "return new Promise(r => setTimeout(() => r(document.title), 1000));" - Async operation
      Note: Commands are executed in the browser context and must be valid JavaScript.
            Use proper error handling and waits for dynamic content.

      Active Sessions:
      """.trimIndent() + activeSessions.entries.joinToString("\n") { (id, session: Selenium) ->
        buildString {
            append("  ** Session $id:\n")
            append("     URL: ${session.getCurrentUrl()}\n")
            try {
                append("     Title: ${session.executeScript("return document.title;")}\n")
                val logs = session.getLogs()
                if (logs.isNotEmpty()) {
                    append("     Recent Logs:\n")
                    logs.takeLast(3).forEach { log ->
                        append("       - $log\n")
                    }
                }
            } catch (e: Exception) {
                append("     Error getting session details: ${e.message}\n")
            }
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val seleniumFactory: (pool: ExecutorService, cookies: Array<out Cookie>?) -> Selenium =
            { pool, cookies ->
                try {
                    Selenium2S3(
                        pool = pool,
                        cookies = cookies,
                        driver = driver()
                    )
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to initialize Selenium", e)
                }
            }
        requireNotNull(executionConfig) { "SeleniumSessionTaskData is required" }
        var selenium: Selenium? = null
        try {

            cleanupInactiveSessions()

            if (activeSessions.size >= MAX_SESSIONS && executionConfig.sessionId == null) {
                throw IllegalStateException("Maximum number of concurrent sessions ($MAX_SESSIONS) reached")
            }
            selenium = executionConfig.sessionId?.let { id -> activeSessions[id] }
                ?: seleniumFactory(agent.pool, null).also { newSession ->
                    executionConfig.sessionId?.let { id -> activeSessions[id] = newSession }
                }
            log.info("Starting Selenium session ${executionConfig.sessionId ?: "temporary"} for URL: ${executionConfig.url} with timeout ${executionConfig.timeout}ms")
            selenium.setScriptTimeout(executionConfig.timeout)


            if (executionConfig.url.isNotBlank()) {
                selenium.navigate(executionConfig.url)
            }

            val results = executionConfig.commands.map { command ->
                try {
                    log.debug("Executing command: $command")
                    val startTime = System.currentTimeMillis()
                    val result = selenium.executeScript(command)?.toString() ?: "null"
                    val duration = System.currentTimeMillis() - startTime
                    log.debug("Command completed in ${duration}ms")
                    result
                } catch (e: Exception) {
                    task.error(e)
                    log.error("Error executing command: $command", e)
                    e.message ?: "Error executing command"
                }
            }
            val result = formatResults(executionConfig, selenium, results)
            task.add(MarkdownUtil.renderMarkdown(result))
            resultFn(result)
        } finally {

            if ((executionConfig.sessionId == null || executionConfig.closeSession) && selenium != null) {
                log.info("Closing temporary session")
                try {
                    selenium.quit()
                    if (executionConfig.sessionId != null) {
                        activeSessions.remove(executionConfig.sessionId)
                    }
                } catch (e: Exception) {
                    log.error("Error closing temporary session", e)
                    selenium.forceQuit()
                    if (executionConfig.sessionId != null) {
                        activeSessions.remove(executionConfig.sessionId)
                    }
                }
            }
        }
    }

    val chromeDriver: WebDriverManager by lazy { WebDriverManager.chromedriver().apply { setup() } }
    fun driver(): RemoteWebDriver {
        requireNotNull(chromeDriver)
        val driver = ChromeDriver(ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
        })

        val devTools = driver.devTools
        devTools.createSession()

        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()))
        devTools.addListener(Network.requestWillBeSent()) { request ->
            println("Request URL: " + request.request.url)
        }

        devTools.send(Log.enable())
        devTools.addListener(Log.entryAdded()) { logEntry ->
            println("Console: " + logEntry.text)
        }
        return driver
    }

    private fun formatResults(
        planTask: SeleniumSessionTaskExecutionConfigData,
        selenium: Selenium,
        results: List<String>
    ): String = buildString(capacity = 163840) {

        appendLine("## Selenium Session Results")
        if (planTask.url.isNotBlank()) {
            appendLine("Initial URL: ${planTask.url}")
        }
        appendLine("Session ID: ${planTask.sessionId ?: "temporary"}")
        appendLine("Final URL: ${selenium.getCurrentUrl()}")
        appendLine("Timeout: ${planTask.timeout}ms")
        appendLine("Browser Info: ${selenium.getBrowserInfo()}")
        appendLine("\nCommand Results:")
        results.forEachIndexed { index, result ->
            appendLine("### Command ${index + 1}")
            appendLine("```javascript")
            appendLine(planTask.commands[index])
            appendLine("```")
            if (result != "null") {
                appendLine("Result:")
                appendLine("```")
                appendLine(result.take(5000))

                appendLine("```")
            }
        }
        try {
            appendLine("\nFinal Page Source:")
            appendLine("```html")
            appendLine(
                HtmlSimplifier.scrubHtml(
                    str = selenium.getPageSource(),
                    baseUrl = selenium.getCurrentUrl(),
                    includeCssData = executionConfig?.includeCssData ?: false,
                    simplifyStructure = executionConfig?.simplifyStructure ?: true,
                    keepObjectIds = executionConfig?.keepObjectIds ?: false,
                    preserveWhitespace = executionConfig?.preserveWhitespace ?: false
                )
            )

            appendLine("```")
        } catch (e: Exception) {
            appendLine("\nError getting page source: ${e.message}")
        }
    }
}