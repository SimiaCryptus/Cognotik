package com.simiacryptus.aicoder.demotest.action.plan

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JCheckboxFixture
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
 * Tests the Single Task functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open with a standard structure
 * - The IDE should be in its default layout with no dialogs open
 *
 * Test Flow:
 * 1. Opens the Project View if not already visible
 * 2. Navigates to the source directory in the project tree
 * 3. Opens the AI Coder context menu
 * 4. Launches the Single Task feature
 * 5. Configures Single Task settings
 * 6. Interacts with the web interface to:
 *    - Submit a simple task request
 *    - Monitor execution
 *    - View results
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SingleTaskActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Single Task Demo",
    )
) {

    override fun getTemplateProjectPath(): String {
        return "demo_projects/TestProject"
    }

    companion object {
        private val log = LoggerFactory.getLogger(SingleTaskActionTest::class.java)
    }

    @Test
    fun testSingleTaskAction() = with(remoteRobot) {
        tts("Welcome to the Single Task feature demonstration. This powerful tool lets you leverage AI assistance for quick, focused coding tasks without switching context.")?.play(
            3000
        )

        step("Open project view") {
            tts("Let's start by accessing our project files. The Single Task feature works best with proper project context.")?.play(
                2000
            )
            openProjectView()
        }

        step("Select source directory") {
            tts("We'll select our source directory where we want to perform our coding task. The AI will analyze this context to provide more relevant assistance.")?.play()
            val path = arrayOf(testProjectDir.toFile().name, "src", "main", "kotlin")
            val tree = find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
        }

        step("Select 'AI Coder' menu") {
            tts("Now we'll access the AI Coder menu. This contains all our AI-powered development tools.")?.play(2000)
            selectAICoderMenu()
        }

        step("Click 'Single Task' action") {
            tts("Under Task Plans, we'll find the Single Task feature. This is perfect for quick, focused coding tasks like implementing a specific function or fixing a bug.")?.play(
                2000
            )
            waitFor(Duration.ofSeconds(10)) {
                try {
                    // Navigate to Task Plans submenu
                    findAll(CommonContainerFixture::class.java, byXpath("//div[@text='📋 Task Plans']"))
                        .firstOrNull()?.moveMouse()
                    sleep(1000)
                    // Click Single Task option
                    findAll(CommonContainerFixture::class.java, byXpath("//div[contains(@text, 'Single Task')]"))
                        .firstOrNull()?.click()
                    log.info("Single Task action clicked")
                    true
                } catch (e: Exception) {
                    log.warn("Failed to find Single Task action: ${e.message}")
                    false
                }
            }
        }

        step("Configure Single Task") {
            tts("Let's configure our task settings. For this demo, we'll enable auto-apply fixes to streamline the process. You can customize these settings based on your preferences.")?.play()
            waitFor(Duration.ofSeconds(10)) {
                val dialog = find(CommonContainerFixture::class.java, byXpath("//div[@class='MyDialog']"))
                if (dialog.isShowing) {
                    dialog.find(JCheckboxFixture::class.java, byXpath("//div[@text='Auto-apply fixes']")).apply {
                        if (!isSelected()) {
                            click()
                            tts("With auto-apply enabled, the AI will automatically implement suggested changes after your approval.")?.play()
                        }
                    }
                    val okButton =
                        dialog.find(
                            CommonContainerFixture::class.java,
                            byXpath("//div[@class='JButton' and @text='OK']")
                        )
                    okButton.click()
                    log.info("Single Task configured")
                    true
                } else {
                    false
                }
            }
        }

        step("Interact with web interface") {
            var url: String? = null
            waitFor(Duration.ofSeconds(90)) {
                val messages = getReceivedMessages()
                url = messages.firstOrNull { it.startsWith("http") } ?: ""
                url?.isNotEmpty() ?: false
            }

            if (url != null) {
                log.info("Retrieved URL: $url")
                tts("The AI Coder opens a dedicated web interface for our task. This provides a clean, focused environment for interacting with the AI assistant.")?.play()
                driver.get(url)
                val wait = WebDriverWait(driver, Duration.ofSeconds(90))

                try {
                    val chatInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    tts("Let's try a common programming task - implementing a factorial function. Watch how the AI understands the requirement and generates appropriate code.")?.play(
                        1000
                    )
                    chatInput.sendKeys("Create a function to calculate the factorial of a number")
                    sleep(1000)

                    val submitButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("send-message-button")))
                    submitButton.click()
                    tts("The AI is now analyzing our request and the project context to generate an optimal implementation.")?.play(
                        5000
                    )

                    // Wait for response
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.className("response-message")))
                    tts("Notice how the AI provides not just the implementation, but also includes documentation and usage examples. You can now review and apply these changes directly to your code.")?.play(
                        3000
                    )
                } catch (e: Exception) {
                    log.error("Error during Single Task interaction: ${e.message}", e)
                    tts("In case of any issues, the AI Coder provides clear error messages and recovery options.")?.play()
                } finally {
                    driver.quit()
                }
            } else {
                log.error("Failed to retrieve interface URL")
                tts("If you encounter connection issues, verify your network settings and API configuration.")?.play()
            }
            clearMessageBuffer()
        }

        tts("That concludes our Single Task demonstration. As you've seen, this feature combines the power of AI with a focused interface to help you quickly implement code changes while maintaining high quality and proper documentation.")?.play(
            5000
        )
        return@with
    }
}