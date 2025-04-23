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
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.time.Duration
import kotlin.io.path.name

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiStepPatchActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Multi-Step Patch Demo",
    )
) {
    override fun getTemplateProjectPath(): String {
        return "demo_projects/TestProject"
    }

    companion object {
        val log = LoggerFactory.getLogger(MultiStepPatchActionTest::class.java)
    }

    @Test
    fun testMultiStepPatchAction() = with(remoteRobot) {
        tts("Welcome to the Multi-Step Patch demonstration. This feature allows you to apply complex code modifications in a structured manner using AI assistance.")?.play(
            2000
        )

        step("Open project view") {
            openProjectView()
            log.info("Project view opened")
            try {
                tts("Let's start by accessing our project files for the Multi-Step Patch demonstration.")?.play(
                    2000
                )
            } catch (e: Exception) {
                log.warn("Failed to provide audio feedback: ${e.message}")
            }
        }
        val projectName = testProjectDir.fileName.name

        step("Select multiple Kotlin files") {
            try {
                tts("We'll select Kotlin files for this demonstration. You can select multiple files for comprehensive analysis.")?.play()
                val path = arrayOf(projectName, "src", "main", "kotlin", "Main.kt")
                val tree =
                    remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
                waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
                log.info("Kotlin file selected")
            } catch (e: Exception) {
                log.error("Failed to select Kotlin files", e)
                tts("We've encountered an issue selecting the files. Please ensure your files are accessible and try again.")?.play(
                    2000
                )
            }
        }

        step("Select 'AI Coder' menu") {
            tts("Now we'll access the AI Coder menu to find the Multi-Step Patch action.")?.play(
                2000
            )
            selectAICoderMenu()
        }

        step("Click 'Multi-Step Editor' action") {
            tts("Let's launch the Multi-Step Editor. This will open an interface where we can apply structured code modifications with AI assistance.")?.play()
            waitFor(Duration.ofSeconds(15)) {
                try {
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'ActionMenuItem') and contains(@text, 'Multi-Step Editor')]")
                    )
                        .firstOrNull()?.click()
                    log.info("'Multi-Step Editor' action clicked")
                    true
                } catch (e: Exception) {
                    log.warn("Failed to find 'Multi-Step Editor' action: ${e.message}")
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
                    tts("Error retrieving Multi-Step Patch URL. Please retry or contact support.")?.play(3000)
                } else {
                    log.info("Retrieved URL: $url")
                    tts("The AI Coder is opening a dedicated interface in your browser for structured code modifications.")?.play()
                    driver.get(url)
                    val wait = WebDriverWait(driver, Duration.ofSeconds(90))
                    log.debug("Setting up WebDriverWait with 90 second timeout")

                    val chatInput = wait.until<WebElement>(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    log.info("Interface loaded successfully")
                    tts("The interface is ready. You can now interact with the AI to apply structured code modifications.")?.play(
                        1000
                    )
                    chatInput.click()

                    tts("Let's ask the AI to apply a patch to our code. You can request specific modifications.")?.play()
                    val request = "Apply a patch to this class"
                    request.forEach { char ->
                        chatInput.sendKeys(char.toString())
                        sleep(100) // Add a small delay between each character
                    }
                    sleep(1000)

                    val submitButton =
                        wait.until<WebElement>(ExpectedConditions.elementToBeClickable(By.xpath("//button[@type='submit']")))
                    tts("Sending our request to the AI. The system will apply the patch and provide feedback.")?.play()
                    log.info("Submitting request to AI")
                    submitButton.click()
                    tts("Watch as the AI applies the patch, ensuring the modifications are correctly implemented.")?.play(
                        2000
                    )

                    try {
                        val markdownTab =
                            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("(//button[contains(@class, 'tab-button') and contains(text(), 'Markdown')])[3]")))
                        sleep(2000)
                        tts("The AI's response is formatted in Markdown for clarity. You can review the applied changes.")?.play(
                            3000
                        )
                        markdownTab.click()
                    } catch (e: Exception) {
                        log.warn("Markdown tab not found within the expected time. Skipping markdown review.", e)
                        tts("If you experience delays, you can refresh the page or check your connection.")?.play(
                            3000
                        )
                    }

                    tts("We've now seen how the Multi-Step Editor provides structured code modifications. This tool is invaluable for applying complex patches.")?.play(
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

        tts("Thank you for exploring the Multi-Step Patch feature. Use this tool for structured code modifications across your codebase.")?.play(
            5000
        )
        return@with
    }
}