package com.simiacryptus.cognotik.demotest.action.chat

/**
 * Tests the Multi-Code Chat functionality in the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open with the following structure:
 *   TestProject/
 *     src/
 *       main/
 *         kotlin/
 *           Person.kt
 * - The IDE should be in its default layout with no dialogs open
 *
 * Test Flow:
 * 1. Opens the Project View if not already visible
 * 2. Navigates to and selects the Person.kt file in the project structure
 * 3. Opens the context menu and selects AI Coder > Multi-Code Chat
 * 4. Launches a browser window with the chat interface
 * 5. Submits a code analysis request and waits for response
 * 6. Demonstrates UI interactions with the response
 * 7. Closes the browser and cleans up
 *
 * Expected Results:
 * - The Multi-Code Chat interface should launch successfully
 * - The AI should provide an analysis of the selected code
 * - All UI interactions should be smooth and functional
 */
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
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.time.Duration
import kotlin.io.path.name

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiCodeChatActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Multi-Code Chat Demo",
    )
) {

    companion object {
        val log = LoggerFactory.getLogger(MultiCodeChatActionTest::class.java)
    }

    @Test
    fun testMultiCodeChatAction() = with(remoteRobot) {
        tts("Welcome to the Multi-Code Chat feature demonstration. This powerful tool allows you to analyze multiple code files simultaneously using AI assistance.")?.play(
            2000
        )
        log.info("Starting testMultiCodeChatAction")

        step("Open project view") {
            openProjectView()
            log.info("Project view opened")
            try {
                tts("Let's start by accessing our project files. The Multi-Code Chat feature works best when analyzing related code files together.")?.play(
                    2000
                )
            } catch (e: Exception) {
                log.warn("Failed to provide audio feedback: ${e.message}")
            }
        }
        val projectName = testProjectDir.fileName.name

        step("Select multiple Kotlin files") {
            try {
                tts("We'll select our main Kotlin file. In practice, you can select multiple files to analyze their relationships and interactions.")?.play()
                val path = arrayOf(projectName, "src", "main", "kotlin", "Main.kt")
                val tree =
                    remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
                waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
                log.info("Kotlin file selected")
            } catch (e: Exception) {
                log.error("Failed to select Kotlin files", e)
                tts("If you encounter issues selecting files, ensure they are accessible and you have proper permissions.")?.play()
            }
            sleep(2000)
        }

        step("Select 'AI Coder' menu") {
            tts("Now we'll access the AI Coder menu. This contains all our AI-powered development tools.")?.play(2000)
            selectAICoderMenu()
        }

        step("Click 'Multi-Code Chat' action") {
            tts("Let's launch the Multi-Code Chat interface. This will open a dedicated chat window for code analysis and discussion.")?.play()
            waitFor(Duration.ofSeconds(15)) {
                try {
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'ActionMenuItem') and contains(@text, 'Code Chat')]")
                    )
                        .firstOrNull()?.click()
                    log.info("'Multi-Code Chat' action clicked")
                    true
                } catch (e: Exception) {
                    log.warn("Failed to find 'Multi-Code Chat' action: ${e.message}")
                    false
                }
            }
            sleep(2000)
        }

        step("Get URL from UDP messages") {
            var url: String? = null
            log.debug("Starting Multi-Code Chat interface interaction")
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
                    tts("The chat interface is opening in your default browser. This provides a familiar environment for interacting with the AI assistant.")?.play()
                    driver.get(url)
                    val wait = WebDriverWait(driver, Duration.ofSeconds(90))
                    log.debug("Setting up WebDriverWait with 90 second timeout")

                    val chatInput = wait.until<WebElement>(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    log.info("Chat interface loaded successfully")
                    tts("The interface has loaded successfully. Notice the clean, intuitive design that makes code discussions natural and efficient.")?.play(
                        1000
                    )
                    chatInput.click()

                    tts("Let's ask the AI to analyze our code. You can request anything from basic code review to complex architectural analysis.")?.play()
                    val request = "Analyze this class"
                    request.forEach { char ->
                        chatInput.sendKeys(char.toString())
                        sleep(100)

                    }
                    sleep(1000)

                    val submitButton =
                        wait.until<WebElement>(ExpectedConditions.elementToBeClickable(By.xpath("//button[@type='submit']")))
                    tts("Sending our request to the AI. The assistant will analyze the code context and provide detailed insights.")?.play()
                    log.info("Submitting request to AI")
                    submitButton.click()
                    tts("Watch as the AI processes our request. It's examining the code structure, patterns, and potential improvements.")?.play(
                        2000
                    )

                    try {
                        val markdownTab =
                            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//button[contains(@class, 'tab-button') and contains(text(), 'Markdown')])[3]")))
                        sleep(2000)
                        tts("The AI's response is formatted in Markdown, making it easy to read and understand. You can copy code snippets, follow links, and navigate through the analysis.")?.play(
                            3000
                        )
                        markdownTab.click()
                    } catch (e: Exception) {
                        log.warn("Copy button not found within the expected time. Skipping copy action.", e)
                        tts("AI response is delayed. In a real scenario, consider refreshing or checking network connection.")?.play(
                            3000
                        )
                    }

                    sleep(3000)
                    tts("We've now seen how Multi-Code Chat streamlines code review and analysis. This tool is particularly valuable for understanding complex codebases and ensuring consistent code quality.")?.play()
                    log.info("Multi-Code Chat interaction completed successfully")
                }
            } finally {
                log.debug("Cleaning up web driver resources")
                driver.quit()
                log.info("Web driver cleanup completed")
                log.debug("Clearing message buffer")
                clearMessageBuffer()
            }
        }

        tts("That concludes our demonstration of Multi-Code Chat. Remember, you can use this tool for everything from quick code reviews to deep architectural discussions. The AI assistant helps you understand and improve your code more efficiently than ever.")?.play(
            5000
        )
        return@with
    }
}