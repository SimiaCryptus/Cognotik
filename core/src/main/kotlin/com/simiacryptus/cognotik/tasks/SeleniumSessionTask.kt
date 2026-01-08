package com.simiacryptus.cognotik.tasks

import com.simiacryptus.cognotik.util.Selenium
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

/**
 * A task wrapper for managing a Selenium browser session.
 * Follows agentic best practices by providing robust error handling,
 * resource cleanup, and structured tool outputs for LLM agents.
 */
class SeleniumSessionTask(
    private val selenium: Selenium,
    private val config: Configuration = Configuration()
) : AutoCloseable {

    data class Configuration(
        val scriptTimeoutMs: Long = 30000,
        val screenshotOnFailure: Boolean = true
    )

    private val logger = LoggerFactory.getLogger(SeleniumSessionTask::class.java)

    init {
        selenium.setScriptTimeout(config.scriptTimeoutMs)
    }

    /**
     * Navigates to the specified URL.
     * @return A summary of the navigation result.
     */
    fun navigate(url: String): String {
        return try {
            logger.info("Navigating to: $url")
            selenium.navigate(url)
            val currentUrl = selenium.getCurrentUrl()
            "Successfully navigated to $currentUrl"
        } catch (e: Exception) {
            handleError("Navigation failed", e)
        }
    }

    /**
     * Executes a JavaScript snippet in the browser.
     * @return The result of the script execution or an error message.
     */
    fun executeScript(script: String): String {
        return try {
            logger.debug("Executing script: $script")
            val result = selenium.executeScript(script)
            "Script executed successfully. Result: $result"
        } catch (e: Exception) {
            handleError("Script execution failed", e)
        }
    }

    /**
     * Retrieves the current page source.
     * Note: For large pages, consider using a script to extract specific content
     * rather than dumping the entire source.
     */
    fun getSource(): String {
        return try {
            selenium.getPageSource()
        } catch (e: Exception) {
            handleError("Failed to get page source", e)
        }
    }

    /**
     * Queries the DOM using a CSS selector and returns the text content.
     * This is a helper wrapper around executeScript for common read operations.
     */
    fun querySelector(selector: String): String {
        val script = """
            var el = document.querySelector('$selector');
            return el ? el.innerText : 'ELEMENT_NOT_FOUND';
        """.trimIndent()
        return executeScript(script)
    }

    /**
     * Clicks an element matching the CSS selector.
     */
    fun click(selector: String): String {
        val script = """
            var el = document.querySelector('$selector');
            if (el) {
                el.click();
                return 'CLICKED';
            } else {
                return 'ELEMENT_NOT_FOUND';
            }
        """.trimIndent()
        return executeScript(script)
    }

    /**
     * Captures a screenshot of the current browser state.
     */
    fun captureScreenshot(): File? {
        return try {
//            selenium.takeScreenshot()
            null
        } catch (e: Exception) {
            logger.error("Failed to take screenshot", e)
            null
        }
    }

    /**
     * Returns browser logs to help diagnose issues.
     */
    fun getBrowserLogs(): String {
        return try {
            selenium.getLogs()
        } catch (e: Exception) {
            "Failed to retrieve logs: ${e.message}"
        }
    }

    /**
     * Closes the selenium session.
     */
    override fun close() {
        try {
            if (selenium.isAlive()) {
                selenium.quit()
            }
        } catch (e: Exception) {
            logger.warn("Error closing Selenium session", e)
            try {
                selenium.forceQuit()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun handleError(context: String, e: Exception): String {
        logger.error("$context: ${e.message}", e)
        if (config.screenshotOnFailure) {
            captureScreenshot()?.let {
                logger.info("Screenshot saved to: ${it.absolutePath}")
            }
        }
        return "Error: $context - ${e.message}"
    }
}