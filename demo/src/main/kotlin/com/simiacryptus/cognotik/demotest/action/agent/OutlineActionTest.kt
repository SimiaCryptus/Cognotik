package com.simiacryptus.cognotik.demotest.action.agent

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JCheckboxFixture
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
 * Tests the Outline Tool functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open
 * - The IDE should be in its default layout
 * - The AI Coder plugin should be properly configured with API keys
 *
 * Test Flow:
 * 1. Opens the Tools menu
 * 2. Selects AI Coder > Outline Tool
 * 3. Configures outline generation settings
 * 4. Launches the outline interface
 * 5. Submits an outline request
 * 6. Verifies outline generation and expansion
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutlineActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Outline Tool Demo",
    )
) {

    companion object {
        private val log = LoggerFactory.getLogger(OutlineActionTest::class.java)
    }

    @Test
    fun testOutlineTool() = with(remoteRobot) {
        tts("Welcome to the AI Coder Outline Tool - a powerful feature that helps you create structured content with AI assistance. Let's explore how it can streamline your documentation process.")?.play(
            2000
        )

        step("Select AI Coder > Outline Tool") {
            tts("First, we'll access the Outline Tool through the IDE's main menu. You can find it under Tools > AI Coder.")?.play()
            tts("The Outline Tool is one of several AI-powered features available in the AI Coder menu. Let's select it to begin our content structuring process.")?.play()

            waitFor(Duration.ofSeconds(60)) {
                try {
                    log.debug("Attempting to find and click main menu")
                    find(CommonContainerFixture::class.java, byXpath("//div[@tooltiptext='Main Menu']")).click()
                    sleep(200)
                    find(CommonContainerFixture::class.java, byXpath("//div[@text='Help']")).moveMouse()

                    val toolsMenu = find(CommonContainerFixture::class.java, byXpath("//div[@text='Tools']"))
                    toolsMenu.moveMouse()

                    val aiCoderMenu = toolsMenu.find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@text='AI Coder']")
                    )
                    robot.mouseMove(toolsMenu.locationOnScreen.x + 10, aiCoderMenu.locationOnScreen.y)
                    sleep(200)
                    aiCoderMenu.click()

                    val outlineMenu = aiCoderMenu.find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'MenuItem') and contains(@text, 'Simple Outline Tool')]")
                    )
                    robot.mouseMove(outlineMenu.locationOnScreen.x + 10, aiCoderMenu.locationOnScreen.y)
                    sleep(200)
                    outlineMenu.click()

                    log.info("Outline Tool menu item clicked")
                    true
                } catch (e: Exception) {
                    log.warn("Failed to click Outline Tool menu item: ${e.message}")
                    false
                }
            }
            sleep(1000)
        }

        step("Configure Outline Tool") {
            tts("Before we start, let's configure the tool for optimal results. We'll enable the Projector visualization for better structure overview, and the Final Essay option to generate complete content.")?.play()
            waitFor(Duration.ofSeconds(10)) {
                val dialog = find(
                    CommonContainerFixture::class.java,
                    byXpath("//div[@class='MyDialog' and @title='Configure Outline Tool']")
                )
                if (dialog.isShowing) {
                    dialog.find(
                        JCheckboxFixture::class.java,
                        byXpath("//div[@class='JBCheckBox' and @text='Show Projector']")
                    ).select()
                    dialog.find(
                        JCheckboxFixture::class.java,
                        byXpath("//div[@class='JBCheckBox' and @text='Write Final Essay']")
                    ).select()
                    tts("These settings will help us create a comprehensive outline with visual relationships between sections.")?.play()
                    val okButton =
                        dialog.find(
                            CommonContainerFixture::class.java,
                            byXpath("//div[@class='JButton' and @text='OK']")
                        )
                    okButton.click()
                    log.info("Outline Tool configured and started")
                    true
                } else {
                    false
                }
            }
        }

        step("Interact with Outline interface") {
            var url: String? = null
            log.debug("Starting Outline interface interaction")
            waitFor(Duration.ofSeconds(90)) {
                val messages = getReceivedMessages()
                url = messages.firstOrNull { it.startsWith("http") } ?: ""
                log.debug("Searching for URL in messages. Found: ${url?.take(50) ?: "none"}")
                url?.isNotEmpty() ?: false
            }

            if (url != null) {
                log.info("Retrieved Outline interface URL: $url")
                tts("The Outline Tool opens in your browser, providing an intuitive interface for content structuring. Notice the clean, modern design optimized for productivity.")?.play()
                driver.get(url)
                val wait = WebDriverWait(driver, Duration.ofSeconds(90))

                try {
                    val chatInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    log.info("Outline interface loaded successfully")
                    tts("Now, let's create an outline for a technical article. Watch how the AI understands our topic and generates a logical structure.")?.play(
                        1000
                    )

                    chatInput.sendKeys("Create an outline for a technical article about AI-assisted coding")
                    wait.until(ExpectedConditions.elementToBeClickable(By.id("send-message-button"))).click()
                    log.info("Outline request submitted")
                    tts("I've requested an outline about AI-assisted coding. The AI will analyze the topic and create a hierarchical structure with main sections and subsections.")?.play(
                        5000
                    )

                    wait.until(ExpectedConditions.presenceOfElementLocated(By.className("outline-container")))
                    tts("Look at how the AI has organized the topic into logical sections. You can click any section to expand it and add more detail. The Projector visualization helps you understand relationships between different parts of your content.")?.play(
                        3000
                    )
                    sleep(3000)

                    log.info("Outline Tool interaction completed successfully")
                } catch (e: Exception) {
                    log.error("Error during Outline Tool interaction: ${e.message}", e)
                    tts("Encountered an error during outline generation. Please check the logs for details.")?.play()
                } finally {
                    driver.quit()
                    log.info("Web driver cleanup completed")
                }
            } else {
                log.error("Failed to retrieve Outline interface URL")
                tts("We've encountered an issue connecting to the Outline Tool. In a real scenario, you would check your internet connection and IDE settings, then try again.")?.play()
            }
            clearMessageBuffer()
        }

        tts("That concludes our demonstration of the Outline Tool. As you've seen, it combines AI intelligence with an intuitive interface to help you create well-structured content quickly and efficiently. Try it with your own documentation needs to experience the productivity boost firsthand.")?.play(
            5000
        )
        return@with
    }
}