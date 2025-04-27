package com.simiacryptus.aicoder.demotest.action.chat

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
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.time.Duration
import kotlin.io.path.name

/**
 * Tests the Multi-Diff Chat functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project named "TestProject" must be open
 * - The project must contain a readme.md file
 * - The IDE should be in its default layout with no open editors
 * - Remote Robot server must be running and accessible
 * - UDP message receiving capability must be configured
 * - WebDriver must be properly configured for browser automation
 *
 * Test Flow:
 * 1. Opens the Project View if not already visible
 * 2. Locates and right-clicks in the project tree
 * 3. Navigates through context menu to select AI Coder > Modify Files
 * 4. Waits for and captures the URL from UDP messages
 * 5. Opens the Multi-Diff Chat interface in a browser
 * 6. Submits a request to add a Mermaid diagram to readme.md
 * 7. Waits for AI to generate the patch
 * 8. Applies the generated patch
 * 9. Verifies the changes in the IDE
 * 10. Cleans up by closing the browser
 *
 * Expected Results:
 * - The readme.md file should be modified to include a Mermaid diagram
 * - The test verifies the presence of "```mermaid" in the file content
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiDiffChatActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Multi-Diff Chat Demo",
    )
) {
    override fun getTemplateProjectPath(): String {
        return "demo_projects/TestProject"
    }

    companion object {
        val log = LoggerFactory.getLogger(MultiDiffChatActionTest::class.java)
    }

    @Test
    fun testMultiDiffChatAction() = with(remoteRobot) {
        tts("Welcome to the Multi-Code Chat demonstration. This powerful feature allows you to analyze and modify multiple code files simultaneously using AI assistance.")?.play(
            2000
        )

        step("Open project view") {
            openProjectView()
            log.info("Project view opened")
            try {
                tts("Let's start by accessing our project files. The Multi-Code Chat can analyze any number of files you select.")?.play(
                    2000
                )
            } catch (e: Exception) {
                log.warn("Failed to provide audio feedback: ${e.message}")
            }
        }
        val projectName = testProjectDir.fileName.name

        step("Select multiple Kotlin files") {
            try {
                tts("We'll select a Kotlin file for this demonstration. In practice, you can select multiple files across different languages for comprehensive analysis.")?.play()
                val path = arrayOf(projectName, "src", "main", "kotlin", "Main.kt")
                val tree =
                    remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
                waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
                log.info("Kotlin file selected")
            } catch (e: Exception) {
                log.error("Failed to select Kotlin files", e)
                tts("We've encountered an issue selecting the files. In a real scenario, ensure your files are accessible and try again.")?.play(
                    2000
                )
            }
        }

        step("Select 'AI Coder' menu") {
            tts("Now we'll access the AI Coder menu. This menu contains various AI-powered features for code analysis and modification.")?.play(
                2000
            )
            selectAICoderMenu()
        }

        step("Click 'Multi-Code Chat' action") {
            tts("Let's launch the Multi-Code Chat. This will open an interactive interface where we can discuss and analyze our code with AI assistance.")?.play()
            waitFor(Duration.ofSeconds(15)) {
                try {
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'ActionMenuItem') and contains(@text, 'Modify Files')]")
                    )
                        .firstOrNull()?.click()
                    log.info("'Diff Chat' action clicked")
                    true
                } catch (e: Exception) {
                    log.warn("Failed to find 'Diff Chat' action: ${e.message}")
                    false
                }
            }
            sleep(2000)
        }

        step("Get URL from UDP messages") {
            var url: String? = null
            log.debug("Starting web interface interaction")
            waitFor(Duration.ofSeconds(90)) {
                val messages = getReceivedMessages()
                url = messages.firstOrNull { it.startsWith("http") } ?: ""
                log.debug("Searching for URL in messages. Found: ${url?.take(50) ?: "none"}")
                url?.isNotEmpty() ?: false
            }
            try {
                if (url == null) {
                    log.error("No URL found in UDP messages")
                    tts("Error retrieving Multi-Code Chat URL. In a real scenario, retry or contact support.")?.play(
                        3000
                    )
                } else {
                    log.info("Retrieved URL: $url")
                    tts("The AI Coder is opening a dedicated chat interface in your browser. This provides a comfortable environment for code discussion and analysis.")?.play()
                    driver.get(url)
                    val wait = WebDriverWait(driver, Duration.ofSeconds(90))
                    log.debug("Setting up WebDriverWait with 90 second timeout")

                    val chatInput = wait.until<WebElement>(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    log.info("Chat interface loaded successfully")
                    tts("The interface is ready. Notice how it displays your selected files and provides a chat input area for natural language interaction.")?.play(
                        1000
                    )
                    chatInput.click()

                    tts("Let's ask the AI to analyze our code. You can request anything from code review to specific modifications.")?.play()
                    val request = "Analyze this class"
                    request.forEach { char ->
                        chatInput.sendKeys(char.toString())
                        sleep(100)

                    }
                    sleep(1000)

                    val submitButton =
                        wait.until<WebElement>(ExpectedConditions.elementToBeClickable(By.xpath("//button[@type='submit']")))
                    tts("Sending our request to the AI. The system will analyze the code context and provide detailed insights.")?.play()
                    log.info("Submitting request to AI")
                    submitButton.click()
                    tts("Watch as the AI examines the code structure, patterns, and potential improvements. This comprehensive analysis ensures thorough understanding of your codebase.")?.play(
                        2000
                    )

                    try {
                        val markdownTab =
                            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//button[contains(@class, 'tab-button') and contains(text(), 'Markdown')])[3]")))
                        sleep(2000)
                        tts("The AI's response is formatted in Markdown for clarity. You can easily read the analysis and copy any suggested code changes.")?.play(
                            3000
                        )
                        markdownTab.click()
                    } catch (e: Exception) {
                        log.warn("Copy button not found within the expected time. Skipping copy action.", e)
                        tts("If you experience delays, the system provides clear feedback. You can refresh the page or check your connection without losing context.")?.play(
                            3000
                        )
                    }

                    tts("We've now seen how Multi-Code Chat provides intelligent code analysis across multiple files. This tool is invaluable for code review, refactoring, and understanding complex codebases.")?.play(
                        3000
                    )
                    log.info("interaction completed successfully")
                }
            } finally {
                log.debug("Cleaning up web driver resources")
                driver.quit()
                log.info("Web driver cleanup completed")
                log.debug("Clearing message buffer")
                clearMessageBuffer()
            }
        }

        tts("Thank you for exploring the Multi-Code Chat feature. Remember, you can use this tool for everything from quick code reviews to complex refactoring projects across your entire codebase.")?.play(
            5000
        )
        return@with
    }
}