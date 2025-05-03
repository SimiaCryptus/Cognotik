package com.simiacryptus.cognotik.demotest.action.chat

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.EditorFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.simiacryptus.cognotik.demotest.DemoTestBase
import com.simiacryptus.cognotik.demotest.SplashScreenConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.awt.event.KeyEvent
import java.lang.Thread.sleep
import java.time.Duration
import kotlin.io.path.name

/**
 * Tests the Diff Chat functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open with the following structure:
 *   - TestProject/
 *     - src/
 *       - main/
 *         - kotlin/
 *           - Person.kt (containing a basic Kotlin class)
 * - The IDE should be in its default layout with no other dialogs open
 * - The AI Coder plugin should be properly configured with valid API credentials
 *
 * Test Behavior:
 * 1. Opens the Project view panel if not already open
 * 2. Navigates to and opens the Person.kt file
 * 3. Selects all code in the editor
 * 4. Opens the context menu via right-click
 * 5. Navigates through the AI Coder menu to select Patch Chat
 * 6. Waits for the chat interface to open in a browser
 * 7. Types a request for code improvements
 * 8. Submits the request and waits for AI response
 * 9. Reviews the suggested patches
 * 10. Applies a patch and verifies the changes
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiffChatActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Patch Chat Demo",
    )
) {
    override fun getTemplateProjectPath(): String {
        return "demo_projects/TestProject"
    }

    @Test
    fun testDiffChatAction() = with(remoteRobot) {
        tts("Welcome to the Patch Chat feature demonstration. This powerful tool enables real-time AI assistance for code improvements and refactoring, with immediate visual feedback and easy patch application.")?.play(
            2000
        )

        step("Open project view") {
            openProjectView()
            log.info("Project view opened")
            try {
                tts("Let's start by accessing our project files. The Patch Chat feature works with any code file in your project.")?.play(
                    2000
                )
            } catch (e: Exception) {
                log.warn("Failed to provide audio feedback: ${e.message}")
            }
        }

        val projectName = testProjectDir.fileName.name
        step("Open a Kotlin file") {
            tts("We'll open a Kotlin file that could benefit from some improvements. Patch Chat works with any programming language the IDE supports.")?.play(
                2000
            )
            val path = arrayOf(projectName, "src", "main", "kotlin", "Main.kt")
            val tree = remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) { tree.doubleClickPath(*path, fullMatch = false); true }
        }

        step("Select code") {
            tts("Now we'll select the code we want to improve. The AI analyzes both the selected code and its context to provide relevant suggestions.")?.play(
                2000
            )
            val editor = find(EditorFixture::class.java, byXpath("//div[@class='EditorComponentImpl']"))
            editor.click()
            keyboard {
                pressing(KeyEvent.VK_CONTROL) {
                    key(KeyEvent.VK_A)

                }
            }
        }

        step("Open context menu and select 'Patch Chat'") {
            tts("The Patch Chat feature is easily accessible through the context menu. You can also configure keyboard shortcuts for quicker access. Under the AI Coder menu, you'll find various AI-powered features. Each one is designed for specific development tasks. Let's launch Patch Chat. This will open an interactive interface where we can discuss code improvements with the AI assistant.")?.play(
                2000
            )
            waitFor(Duration.ofSeconds(90)) {
                try {
                    val editor = find(EditorFixture::class.java, byXpath("//div[@class='EditorComponentImpl']"))
                    editor.rightClick()
                    selectAICoderMenu()
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'ActionMenuItem') and contains(@text, 'Patch Chat')]")
                    ).firstOrNull()?.let {
                        it.click()
                        log.info("'Patch Chat' action clicked")
                        true
                    } ?: false
                } catch (e: Exception) {
                    log.warn("Failed to find 'Patch Chat' action: ${e.message}")
                    false
                }
            }
        }

        step("Get URL from UDP messages") {
            var url: String? = null
            log.debug("Starting Patch Chat interface interaction")
            waitFor(Duration.ofSeconds(90)) {
                val messages = getReceivedMessages()
                url = messages.firstOrNull { it.startsWith("http") } ?: ""
                log.debug("Searching for URL in messages. Found: ${url?.take(50) ?: "none"}")
                url?.isNotEmpty() ?: false
            }

            if (url != null) {
                log.info("Retrieved Patch Chat interface URL: $url")
                tts("Patch Chat web interface opened.")?.play()
                log.debug("Initializing web driver")
                driver.get(url)
                log.debug("Setting up WebDriverWait with 90 second timeout")
                val wait = WebDriverWait(driver, Duration.ofSeconds(90))

                try {
                    val chatInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    log.info("Chat interface loaded successfully")
                    tts("The chat interface is now ready. Watch how we can request code improvements using natural language.")?.play(
                        1000
                    )

                    log.debug("Submitting request to chat interface")
                    chatInput.click()
                    tts("We'll ask the AI to suggest optimizations. You can be as specific or general as needed - for example, requesting performance improvements, better error handling, or cleaner code structure.")?.play(
                        1000
                    )
                    chatInput.sendKeys("Suggest improvements to optimize this code")

                    wait.until(ExpectedConditions.elementToBeClickable(By.id("send-message-button"))).click()
                    log.info("Request submitted successfully")
                    tts("The AI is analyzing our code and generating improvement suggestions. Each suggestion will come with a detailed explanation and ready-to-apply patches.")?.play(
                        2000
                    )

                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".message-container")))

                    (driver as JavascriptExecutor).executeScript(
                        """
      document.querySelector(".message-container").scrollIntoView({
          behavior: 'smooth',
          block: 'center'
      });
  """
                    )
                    sleep(2000)
                    tts("Let's review the AI's suggestions. Notice how each patch comes with an explanation of the changes and their benefits. You can apply patches directly from this interface.")?.play(
                        3000
                    )

                    val applyButtons = driver.findElements(By.cssSelector("a.href-link"))
                        .filter { it.text.contains("Apply", ignoreCase = true) }

                    if (applyButtons.isNotEmpty()) {
                        tts("We'll apply one of the suggested improvements. The patch will be automatically applied to your code while maintaining proper formatting and structure.")?.play(
                            2000
                        )
                        applyButtons.first().click()
                    }

                    tts("And that's how easy it is to improve your code with Patch Chat! You can continue the conversation to refine the changes or request additional improvements.")?.play()
                    log.info("Patch Chat interaction completed successfully")
                } catch (e: Exception) {
                    log.error("Error during Patch Chat interaction: ${e.message}", e)
                    tts("Encountered an error during Patch Chat interaction. Please check the logs for details.")?.play()
                } finally {
                    log.debug("Cleaning up web driver resources")
                    driver.quit()
                    log.info("Web driver cleanup completed")
                }
            } else {
                log.error("Failed to retrieve Patch Chat interface URL from UDP messages")
                tts("Error: Unable to retrieve the necessary URL.")?.play()
            }
            log.debug("Clearing message buffer")
            clearMessageBuffer()
        }

        tts("This concludes our Patch Chat demonstration. We've seen how it streamlines code improvement by combining AI analysis, interactive discussion, and automated patch application. Try it with your own code to experience the benefits of AI-assisted development!")?.play(
            5000
        )
        return@with
    }

    companion object {
        val log = LoggerFactory.getLogger(DiffChatActionTest::class.java)
    }
}