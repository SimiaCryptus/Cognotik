package com.simiacryptus.cognotik.demotest.action.agent

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import com.simiacryptus.cognotik.demotest.DemoTestBase
import com.simiacryptus.cognotik.demotest.SplashScreenConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.time.Duration

/**
 * Tests the Simple Command functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open with a standard structure
 * - The IDE should be in its default layout with no dialogs open
 *
 * Test Flow:
 * 1. Opens the Project View if not already visible
 * 2. Selects a directory in the project structure
 * 3. Opens the AI Coder context menu
 * 4. Launches the Simple Command feature
 * 5. Interacts with the web interface to:
 *    - Submit a coding task
 *    - Monitor execution progress
 *    - Review and apply generated changes
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleCommandActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Simple Command Demo"
    )
) {

    companion object {
        val log = LoggerFactory.getLogger(SimpleCommandActionTest::class.java)
    }

    @Test
    fun testSimpleCommand() = with(remoteRobot) {
        tts("Welcome to the Simple Command feature demonstration. This powerful tool allows you to modify code using natural language instructions, making complex code changes as simple as having a conversation.")?.play(
            2000
        )

        step("Open project view") {
            tts("Let's start by opening the project view, where we can select the code files we want to modify.")?.play(
                2000
            )
            openProjectView()
        }

        step("Select directory") {
            tts("We'll select the main source directory containing our Kotlin files. The AI will analyze these files to understand the context before making any changes.")?.play(
                2000
            )
            val path = arrayOf(testProjectDir.toFile().name, "src", "main", "kotlin")
            val tree = find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
        }

        step("Select 'AI Coder' menu") {
            tts("Now we'll access the AI Coder menu, which contains various AI-powered development tools. The Simple Command feature is found under the Agents submenu.")?.play(
                2000
            )
            selectAICoderMenu()
        }

        step("Click 'Do Something' action") {
            tts("We'll select the 'Do Something' action, which opens our natural language command interface. This is where we can describe the changes we want to make to our code.")?.play()
            waitFor(Duration.ofSeconds(10)) {
                try {
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@text='AI Coder']//div[@text='\uD83E\uDD16 Agents']")
                    )
                        .firstOrNull()?.click()
                    sleep(1000)
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@text='AI Coder']//div[@text='✨ Do Something']")
                    )
                        .firstOrNull()?.click()
                    log.info("'Do Something' action clicked")
                    true
                } catch (e: Exception) {
                    log.warn("Failed to find 'Do Something' action: ${e.message}")
                    false
                }
            }
            sleep(2000)
        }

        step("Interact with web interface") {
            var url: String? = null
            log.debug("Starting web interface interaction")
            waitFor(Duration.ofSeconds(90)) {
                val messages = getReceivedMessages()
                url = messages.firstOrNull { it.startsWith("http") } ?: ""
                log.debug("Searching for URL in messages. Found: ${url?.take(50) ?: "none"}")
                url?.isNotEmpty() ?: false
            }

            try {
                if (url != null) {
                    log.info("Retrieved URL: $url")
                    tts("The AI Coder interface opens in your default browser, providing a chat-like experience for code modifications.")?.play()
                    driver.get(url)
                    val wait = WebDriverWait(driver, Duration.ofSeconds(90))

                    val chatInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    log.info("Chat interface loaded successfully")
                    tts("The interface is now ready. Let's demonstrate how to request a common code improvement - adding input validation to our methods.")?.play(
                        1000
                    )
                    chatInput.click()

                    tts("We'll type our request in natural language. Notice how you don't need to know specific code patterns or syntax - just describe what you want to achieve.")?.play(
                        1000
                    )
                    chatInput.sendKeys("Add input validation to all public methods")

                    wait.until(ExpectedConditions.elementToBeClickable(By.id("send-message-button"))).click()
                    log.info("Request submitted successfully")
                    tts("After submitting our request, the AI analyzes the codebase and generates appropriate validation code for each public method.")?.play(
                        2000
                    )

                    try {
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".tabs-container")))
                        tts("The AI has completed its analysis and generated suggested changes. Each tab represents a different file that needs modifications. Let's review some of these changes.")?.play(
                            3000
                        )

                        val tabs = driver.findElements(By.cssSelector(".tabs-container .tab-button"))
                        tabs.take(3).forEach { tab ->
                            tab.click()
                            sleep(2000)
                        }
                    } catch (e: Exception) {
                        log.warn("Response tabs not found: ${e.message}")
                        tts("The AI is still processing our request. For complex codebases, thorough analysis may take a few moments to ensure accurate modifications.")?.play()
                    }

                    tts("And that concludes our demonstration of the Simple Command feature. As you've seen, it provides an intuitive way to make systematic code changes across your project using natural language instructions.")?.play()
                    log.info("Web interface interaction completed successfully")
                } else {
                    log.error("No URL found in UDP messages")
                    tts("Error retrieving web interface URL.")?.play()
                }
            } finally {
                log.debug("Cleaning up web driver resources")
                driver.quit()
                log.info("Web driver cleanup completed")
                clearMessageBuffer()
            }
        }

        tts("Thank you for watching this demonstration. The Simple Command feature streamlines code modifications by combining AI understanding with your natural language instructions, making complex refactoring tasks simple and efficient.")?.play(
            5000
        )
        return@with
    }
}