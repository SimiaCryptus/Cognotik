package com.simiacryptus.aicoder.demotest.action.agent

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import com.simiacryptus.aicoder.demotest.DemoTestBase
import com.simiacryptus.aicoder.demotest.SplashScreenConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.time.Duration

/**
 * Test class for the Web Development Assistant feature of AI Coder plugin.
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
 * 4. Launches the Web Development Assistant feature
 * 5. Interacts with the web interface to:
 *    - Submit a web development task
 *    - Monitor execution progress
 *    - Review and apply generated changes
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebDevelopmentAssistantActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Web Development Assistant Demo"
    )
) {

    companion object {
        val log = LoggerFactory.getLogger(WebDevelopmentAssistantActionTest::class.java)
    }

    @Test
    fun testWebDevelopmentAssistant() = with(remoteRobot) {
        tts("Welcome to the Web Development Assistant feature demonstration. This tool helps you design and implement web applications using natural language instructions.")?.play(
            2000
        )

        step("Open project view") {
            tts("Let's start by opening the project view, where we can select the code files we want to modify.")?.play(
                2000
            )
            openProjectView()
        }

        testProjectDir.toFile().resolve("src/main/web").mkdirs()
        step("Select directory") {
            tts("We'll select the main source directory containing our web files. The AI will analyze these files to understand the context before making any changes.")?.play(
                2000
            )
            waitFor(Duration.ofSeconds(90)) {
                try {
                    val path = mutableListOf(testProjectDir.toFile().name, "src", "main")
                    find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply {
                        expandAll(path.toTypedArray())
                        rightClickPath(*path.toTypedArray(), fullMatch = false);
                    }
                    remoteRobot.find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'ActionMenu') and contains(@text, 'Reload from Disk')]")
                    ).apply {
                        waitFor(Duration.ofMillis(100)) { isShowing }
                        click()
                    }
                    path += "web"
                    find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply {
                        expandAll(path.toTypedArray())
                        rightClickPath(*path.toTypedArray(), fullMatch = false);
                    }
                    remoteRobot.find(CommonContainerFixture::class.java, byXpath(AI_CODER_MENU_XPATH)).apply {
                        waitFor(Duration.ofMillis(100)) { isShowing }
                        click()
                    }
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@text='AI Coder']//div[@text='\uD83E\uDD16 Agents']")
                    ).firstOrNull()?.click() ?: return@waitFor false
                    sleep(1000)
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@text='AI Coder']//div[@text='\uD83C\uDF10 Web Dev']")
                    ).firstOrNull()?.click() ?: return@waitFor false
                    log.info("'Web Development Assistant' action clicked")
                    true
                } catch (e: Exception) {
                    log.warn("Failed to find 'Web Development Assistant' action: ${e.message}")
                    false
                }
            }
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
                    tts("The Web Development Assistant interface opens in your default browser, providing a chat-like experience for web development tasks.")?.play()
                    driver.get(url)
                    val wait = WebDriverWait(driver, Duration.ofSeconds(90))

                    val chatInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    log.info("Chat interface loaded successfully")
                    tts("The interface is now ready. Let's demonstrate how to request a common web development task - creating a responsive navigation bar.")?.play(
                        1000
                    )
                    chatInput.click()

                    tts("We'll type our request in natural language. Notice how you don't need to know specific code patterns or syntax - just describe what you want to achieve.")?.play(
                        1000
                    )
                    chatInput.sendKeys("Create a responsive navigation bar with dropdown menus")

                    wait.until(ExpectedConditions.elementToBeClickable(By.id("send-message-button"))).click()
                    log.info("Request submitted successfully")
                    tts("After submitting our request, the AI analyzes the requirements and generates the necessary HTML, CSS, and JavaScript code.")?.play(
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
                        tts("The AI is still processing our request. For complex tasks, thorough analysis may take a few moments to ensure accurate modifications.")?.play()
                    }

                    tts("And that concludes our demonstration of the Web Development Assistant feature. As you've seen, it provides an intuitive way to develop web applications using natural language instructions.")?.play()
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

        tts("Thank you for watching this demonstration. The Web Development Assistant feature streamlines web development by combining AI understanding with your natural language instructions, making complex web design tasks simple and efficient.")?.play(
            5000
        )
        return@with
    }
}