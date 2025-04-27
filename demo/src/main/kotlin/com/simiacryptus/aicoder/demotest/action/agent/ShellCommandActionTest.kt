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
import java.time.Duration

/**
 * Tests the Shell Command functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open
 * - The IDE should be in its default layout with no dialogs open
 *
 * Test Flow:
 * 1. Opens the Project View if not already visible
 * 2. Selects the project root directory
 * 3. Opens the AI Coder context menu
 * 4. Navigates to Agents > Shell Agent
 * 5. Launches the shell command interface
 * 6. Enters and executes a simple command
 * 7. Verifies the command output
 *
 * Expected Results:
 * - The Shell Command interface should launch successfully
 * - The test command should execute and return output
 * - All UI interactions should be smooth and error-free
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShellCommandActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Shell Command Test",
    )
) {

    companion object {
        private val log = LoggerFactory.getLogger(ShellCommandActionTest::class.java)
    }

    @Test
    fun testShellCommand() = with(remoteRobot) {
        tts("Welcome to the Shell Command feature demonstration. This powerful tool combines AI assistance with shell command execution to help you manage your project more effectively.")?.play(
            2000
        )

        step("Open project view") {
            tts("Let's begin by accessing our project structure. The Shell Command feature works best when we have a clear context of our working directory.")?.play(
                2000
            )
            openProjectView()
        }

        step("Select project root") {
            tts("We'll select our project's source directory, which will become our working directory for command execution. This ensures our commands operate in the right context.")?.play()
            val path = arrayOf(testProjectDir.toFile().name, "src", "main", "kotlin")
            val tree = find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) {
                tree.rightClickPath(*path, fullMatch = false)
                true
            }
            log.info("Project root selected")
            Thread.sleep(2000)
        }

        step("Navigate to Shell Agent") {
            tts("Now we'll access the Shell Agent through the AI Coder menu. This intelligent agent combines shell command execution with AI assistance to help you accomplish tasks more efficiently.")?.play()
            waitFor(Duration.ofSeconds(10)) {
                try {
                    val aiCoderMenu = selectAICoderMenu()
                    Thread.sleep(200)
                    val agentsMenu = aiCoderMenu.findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'Menu') and contains(@text,'Agents')]")
                    )
                        .firstOrNull() ?: throw Exception("Agents menu not found")

                    robot.mouseMove(agentsMenu.locationOnScreen.x + 5, aiCoderMenu.locationOnScreen.y + 5)
                    agentsMenu.click()
                    Thread.sleep(100)

                    val shellAgentMenu = agentsMenu.findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'MenuItem') and contains(@text,'Shell Agent')]")
                    ).firstOrNull() ?: throw Exception("Shell Agent menu item not found")
                    robot.mouseMove(shellAgentMenu.locationOnScreen.x + 5, agentsMenu.locationOnScreen.y + 5)
                    shellAgentMenu.click()
                    log.info("Shell Agent menu item clicked")
                    true
                } catch (e: Exception) {
                    log.warn("Failed to navigate to Shell Agent: ${e.message}")
                    false
                }
            }
            Thread.sleep(2000)
        }
        step("Interact with Shell Command interface") {
            var url: String? = null
            log.debug("Starting Shell Command interface interaction")
            waitFor(Duration.ofSeconds(90)) {
                val messages = getReceivedMessages()
                url = messages.firstOrNull { it.startsWith("http") } ?: ""
                url?.isNotEmpty() ?: false
            }

            if (url != null) {
                log.info("Retrieved Shell Command interface URL: $url")
                tts("The Shell Command interface opens in your browser, providing a chat-like experience where you can interact with both the AI assistant and your system's shell.")?.play()
                driver.get(url)
                val wait = WebDriverWait(driver, Duration.ofSeconds(90))

                try {
                    val chatInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    log.info("Chat interface loaded")
                    tts("Let's try a simple directory listing command. The AI assistant will help interpret the output and suggest relevant follow-up actions.")?.play(
                        1000
                    )

                    val command = if (System.getProperty("os.name").lowercase().contains("windows")) {
                        "dir"
                    } else {
                        "ls -la"
                    }
                    chatInput.sendKeys(command)
                    Thread.sleep(1000)

                    wait.until(ExpectedConditions.elementToBeClickable(By.id("send-message-button"))).click()
                    log.info("Command submitted")
                    tts("Watch as the command executes and the AI analyzes its output in real-time.")?.play(2000)

                    wait.until(ExpectedConditions.presenceOfElementLocated(By.className("response-message")))
                    tts("The command has completed. Notice how the AI provides context and explanations for the output, making it easier to understand your project's structure.")?.play(
                        3000
                    )

                } catch (e: Exception) {
                    log.error("Error during Shell Command interaction: ${e.message}", e)
                    tts("We've encountered an issue. In a real scenario, the AI would help diagnose the problem and suggest potential solutions.")?.play()
                } finally {
                    driver.quit()
                    log.info("Browser session closed")
                }
            } else {
                log.error("Failed to retrieve Shell Command interface URL")
                tts("Error: Unable to launch Shell Command interface.")?.play()
            }
            clearMessageBuffer()
        }

        tts("That concludes our demonstration of the Shell Command feature. You've seen how it combines the power of your system's shell with AI assistance to make command-line operations more intuitive and productive. Try it with more complex commands to see how the AI can help interpret outputs and suggest next steps.")?.play(
            5000
        )
        return@with
    }
}