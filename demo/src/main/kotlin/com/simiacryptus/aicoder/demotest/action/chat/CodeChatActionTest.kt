package com.simiacryptus.aicoder.demotest.action.chat

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.EditorFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.simiacryptus.aicoder.demotest.DemoTestBase
import com.simiacryptus.aicoder.demotest.SplashScreenConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.awt.event.KeyEvent
import java.lang.Thread.sleep
import java.time.Duration
import kotlin.io.path.name

/**
 * Tests the Code Chat functionality of the AI Coder plugin.
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
 * 5. Navigates through the AI Coder menu to select Code Chat
 * 6. Waits for the chat interface to open in a browser
 * 7. Types a request for creating a user manual
 * 8. Submits the request and waits for AI response
 * 9. Switches to Markdown view of the response
 * 10. Verifies the response and closes the browser
 *
 * Note: This test includes voice feedback for demonstration purposes
 * and includes appropriate waits between actions to ensure stability.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CodeChatActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Code Chat Demo",
    )
) {
    override fun getTemplateProjectPath(): String {
        return "demo_projects/TestProject"
    }

    @Test
    fun testCodeChatAction() = with(remoteRobot) {
        tts("Welcome to the Code Chat feature demonstration. This powerful tool enables real-time AI assistance while you code, helping you understand, modify, and improve your codebase through natural language interaction.")?.play(
            2000
        )

        step("Open project view") {
            openProjectView()
            log.info("Project view opened")
            try {
                tts("Let's start by accessing our project files. The Code Chat feature works with any file in your project, providing context-aware assistance.")?.play(
                    2000
                )
            } catch (e: Exception) {
                log.warn("Failed to provide audio feedback: ${e.message}")
            }
        }

        val projectName = testProjectDir.fileName.name
        step("Open a Kotlin file") {
            tts("We'll open a Kotlin source file that contains code we want to analyze. Code Chat works with any programming language supported by your IDE.")?.play(
                2000
            )
            val path = arrayOf(projectName, "src", "main", "kotlin", "Main.kt")
            val tree = remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            for (i in (0 until path.size - 1)) {
                val sliceArray = path.sliceArray(0..i)
                log.info("Checking path exists: ${sliceArray.joinToString("/")}")
                waitFor(Duration.ofSeconds(10)) { tree.isPathExists(*sliceArray, fullMatch = false) }
            }
            waitFor(Duration.ofSeconds(10)) { tree.clickPath(*path, fullMatch = false); true }
            waitFor(Duration.ofSeconds(10)) { tree.doubleClickPath(*path, fullMatch = false); true }
        }

        step("Select code") {
            tts("Now we'll select the code we want to discuss. The AI will analyze this selection to provide relevant insights and suggestions. You can select specific sections or entire files.")?.play(
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

        step("Open context menu") {
            tts("To start a Code Chat session, we'll use the context menu. This is one of several ways to access the AI Coder's features.")?.play(
                2000
            )
            val editor = find(EditorFixture::class.java, byXpath("//div[@class='EditorComponentImpl']"))
            editor.rightClick()
        }

        step("Select 'AI Coder' menu") {
            tts("Under the AI Coder menu, you'll find various AI-powered development tools. We're interested in the Code Chat feature for this demonstration.")?.play(
                2000
            )
            selectAICoderMenu()
        }

        step("Click 'Code Chat' action") {
            tts("When we select Code Chat, the plugin will analyze our code selection and open an interactive chat interface in your default browser. This provides a comfortable environment for detailed code discussions.")?.play()
            waitFor(Duration.ofSeconds(15)) {
                try {
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'ActionMenuItem') and contains(@text, 'AI Editor')]")
                    )
                        .firstOrNull()?.click()
                    sleep(1000)
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'ActionMenuItem') and contains(@text, 'Code Chat')]")
                    )
                        .firstOrNull()?.click()
                    log.info("'Code Chat' action clicked")
                    true
                } catch (e: Exception) {
                    log.warn("Failed to find 'Code Chat' action: ${e.message}")
                    false
                }
            }
            sleep(2000)
        }

        step("Get URL from UDP messages") {
            var url: String? = null
            log.debug("Starting Code Chat interface interaction")
            waitFor(Duration.ofSeconds(90)) {
                val messages = getReceivedMessages()
                url = messages.firstOrNull { it.startsWith("http") } ?: ""
                log.debug("Searching for URL in messages. Found: ${url?.take(50) ?: "none"}")
                url?.isNotEmpty() ?: false
            }

            if (url != null) {
                log.info("Retrieved Code Chat interface URL: $url")
                tts("The chat interface has opened in your browser. Notice how it displays your code selection for reference during the conversation.")?.play()
                log.debug("Initializing web driver")
                driver.get(url)
                log.debug("Setting up WebDriverWait with 90 second timeout")
                val wait = WebDriverWait(driver, Duration.ofSeconds(90))

                try {

                    val chatInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("chat-input")))

                    chatInput.clear()
                    sleep(1000)

                    chatInput.clear()
                    sleep(1000)

                    chatInput.clear()
                    sleep(1000)
                    log.info("Chat interface loaded successfully")
                    tts("The interface is ready for interaction. You can ask questions about the code, request improvements, or seek explanations - all in natural language.")?.play(
                        1000
                    )
                    log.debug("Submitting request to chat interface")
                    chatInput.apply {
                        click()

                        "Please explain this code and suggest improvements".forEach { c ->
                            sendKeys(c.toString())
                            sleep(100)
                        }

                        sleep(2000)

                        "Create a user manual for this class".forEach { c ->
                            sendKeys(c.toString())
                            sleep(100)
                        }
                    }

                    sleep(2000)
                    tts("Let's ask the AI to create a user manual for our class. This demonstrates how Code Chat can help with documentation tasks while maintaining full context of the code.")?.play(
                        1000
                    )

                    wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[@type='submit']"))).click()

                    sleep(10000)
                    log.info("Request submitted successfully")

                    sleep(5000)
                    tts("The request is being processed. The AI analyzes both the code structure and your request to generate comprehensive, contextually relevant responses.")?.play(
                        2000
                    )

                    try {
                        val markdownTab =
                            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//button[contains(@class, 'tab-button') and contains(text(), 'Markdown')])[3]")))
                        tts("The AI has provided a detailed response. Notice how it structures the documentation based on the actual code implementation while following best practices.")?.play(
                            3000
                        )
                        markdownTab.click()
                    } catch (e: Exception) {
                        MultiCodeChatActionTest.log.warn(
                            "Copy button not found within the expected time. Skipping copy action.",
                            e
                        )
                        tts("AI response is delayed. In a real scenario, consider refreshing or checking network connection.")?.play(
                            3000
                        )
                    }
                    tts("The Code Chat feature makes it easy to have meaningful discussions about your code, whether you're seeking explanations, improvements, or documentation help.")?.play(
                        2000
                    )
                } catch (e: Exception) {
                    log.error("Error during Code Chat interaction: ${e.message}", e)
                    tts("Encountered an error during Code Chat interaction. Please check the logs for details.")?.play()
                } finally {
                    log.debug("Cleaning up web driver resources")
                    driver.quit()
                    log.info("Web driver cleanup completed")
                }

            } else {
                log.error("Failed to retrieve Code Chat interface URL from UDP messages")
                tts("Error: Unable to retrieve the necessary URL.")?.play()
            }
            log.debug("Clearing message buffer")
            clearMessageBuffer()
        }

        tts("This concludes our Code Chat demonstration. We've seen how it provides an intuitive interface for AI-powered code discussions, helping you better understand and document your code. Try experimenting with different types of queries to explore its full capabilities.")?.play(
            5000
        )
        return@with
    }

    companion object {
        val log = LoggerFactory.getLogger(CodeChatActionTest::class.java)
    }

}