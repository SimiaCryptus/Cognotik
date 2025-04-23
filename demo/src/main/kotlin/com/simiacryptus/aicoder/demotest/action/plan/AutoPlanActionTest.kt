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
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

/**
 * Test class for the Auto Plan Chat feature of AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A project must be open with a standard structure
 * - The IDE should be in its default layout with no dialogs open
 *
 * Test Flow:
 * 1. Opens the Project View if not already visible
 * 2. Navigates to the source directory in the project tree
 * 3. Opens the AI Coder context menu
 * 4. Launches the Auto-Plan feature
 * 5. Interacts with the Auto-Plan web interface to:
 *    - Submit a task description
 *    - Monitor execution progress
 *    - View results
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoPlanActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Auto-Plan Demo",
    )
) {

    override fun getTemplateProjectPath(): String {
        return "demo_projects/DataGnome"
    }

    companion object {
        private val log = LoggerFactory.getLogger(AutoPlanActionTest::class.java)
    }

    @Test
    fun testAutoPlanAction() = with(remoteRobot) {
        log.debug("Initializing Auto-Plan test execution")
        tts("Welcome to the AI Coder Auto-Plan feature. Let's explore how it can break down complex coding tasks into manageable steps and execute them automatically.")?.play()
        log.info("Starting Auto-Plan demonstration test")
        sleep(3000)

        step("Open project view") {
            log.debug("Attempting to open project view")
            tts("First, we'll open the project view to select our target directory for the new code generation.")?.play()
            openProjectView()
            log.info("Project view opened successfully")
            sleep(2000)
        }

        step("Select source directory") {
            log.debug("Starting source directory selection")
            tts("We'll navigate to the main source directory where our new utility class will be created. Notice how Auto-Plan maintains proper project structure.")?.play()
            val path = arrayOf(testProjectDir.name, "src", "main", "kotlin")
            log.debug("Attempting to locate project tree with path: ${path.joinToString("/")}")
            val tree = find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            log.debug("Project tree found, expanding path")
            waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
            log.info("Source directory selected and right-clicked successfully")
            sleep(3000)
        }

        lateinit var aiCoderMenu: CommonContainerFixture
        step("Open AI Coder menu") {
            log.debug("Attempting to open AI Coder menu")
            tts("From the context menu, we'll access AI Coder's powerful features through its dedicated menu.")?.play()
            aiCoderMenu = selectAICoderMenu()
            log.info("AI Coder menu opened successfully")
            sleep(3000)
        }

        step("Select Auto-Plan action") {
            log.debug("Starting Auto-Plan action selection")
            tts("Let's select the Auto-Plan feature, which will help us create a well-structured utility class with proper documentation and test coverage.")?.play()
            waitFor(Duration.ofSeconds(10)) {
                try {
                    val taskMenu = find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@text='AI Coder']//div[@text='\uD83D\uDCCB Task Plans']")
                    )
                        .apply {
                            robot.mouseMove(this.locationOnScreen.x, aiCoderMenu.locationOnScreen.y)
                            this.moveMouse()
                        }
                    sleep(1000)
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@text='AI Coder']//div[@text='\uD83E\uDD16 Auto-Plan']")
                    )
                        .firstOrNull()?.apply {
                            robot.mouseMove(this.locationOnScreen.x, taskMenu.locationOnScreen.y)
                            this.moveMouse()
                            click()
                        }
                    log.info("Auto-Plan action found and clicked successfully")
                    tts("The Auto-Plan feature is now initializing. It will guide us through the entire development process step by step.")?.play()
                    true
                } catch (e: Exception) {
                    log.warn("Failed to find Auto-Plan action. Error: ${e.message}", e)
                    tts("Failed to find Auto-Plan action. Retrying...")?.play()
                    false
                }
            }
            sleep(3000)
        }

        step("Configure Task Runner") {
            log.debug("Starting Task Runner configuration")
            tts("Before we begin, let's configure the Task Runner for optimal automation. We can customize how Auto-Plan handles code changes and user interactions.")?.play()
            waitFor(Duration.ofSeconds(10)) {
                val dialog = find(CommonContainerFixture::class.java, byXpath("//div[@class='MyDialog']"))
                if (dialog.isShowing) {
                    log.debug("Configuration dialog found and visible")
                    dialog.find(JCheckboxFixture::class.java, byXpath("//div[@text='Auto-apply fixes']")).apply {
                        if (!isSelected()) {
                            click()
                            log.info("Auto-apply fixes checkbox toggled to selected state")
                            tts("We'll enable automatic code changes to streamline the development process, allowing Auto-Plan to implement its suggestions directly.")?.play()
                        }
                    }
                    dialog.find(JCheckboxFixture::class.java, byXpath("//div[@text='Allow blocking']")).apply {
                        if (isSelected()) {
                            click()
                            log.info("Allow blocking checkbox toggled to deselected state")
                            tts("By disabling blocking mode, Auto-Plan will execute tasks continuously without requiring manual confirmation at each step.")?.play()
                        }
                    }
                    sleep(3000)
                    log.debug("Attempting to click OK button")

                    val okButton =
                        dialog.find(
                            CommonContainerFixture::class.java,
                            byXpath("//div[@class='JButton' and @text='OK']")
                        )
                    okButton.click()
                    log.info("Task Runner configuration completed and dialog closed")
                    tts("With these optimal settings configured, Auto-Plan can now work efficiently to complete our development task.")?.play()
                    true
                } else {
                    log.warn("Configuration dialog not found or not visible")
                    false
                }
            }
        }

        step("Interact with Auto-Plan interface") {
            val maxIterations = 3
            log.debug("Starting Auto-Plan interface interaction")
            var url: String? = null
            waitFor(Duration.ofSeconds(90)) {
                val messages = getReceivedMessages()
                url = messages.firstOrNull { it.startsWith("http") } ?: ""
                log.debug("Searching for URL in messages. Found: ${url?.take(50) ?: "none"}")
                url?.isNotEmpty() ?: false
            }

            if (url != null) {
                log.info("Retrieved Auto-Plan interface URL: $url")
                tts("The Auto-Plan interface provides a clear view of the development process, showing each step and decision made by the AI.")?.play()
                log.debug("Initializing web driver")
                driver.get(url)
                log.debug("Setting up WebDriverWait with 90 second timeout")

                val wait = WebDriverWait(driver, Duration.ofSeconds(90))
                log.info("Chat interface loaded successfully")
                tts("Now we can describe our task in natural language. Auto-Plan will understand the requirements and create a detailed implementation plan.")?.play(
                    1000
                )

                try {
                    // Submit task description
                    log.debug("Submitting task description to chat interface")
                    val chatInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                    chatInput.click()
                    chatInput.sendKeys("Create a new utility class for string manipulation with methods for common operations")
                    tts("We're requesting a new utility class for string manipulation. Watch how Auto-Plan breaks this down into specific implementation tasks.")?.play()

                    clickElement(driver, wait, "#send-message-button")
                    log.info("Task description submitted successfully")
                    tts("Task description submitted.")?.play(1000)

                    // Monitor execution progress
                    log.debug("Beginning execution progress monitoring")
                    tts("Auto-Plan is analyzing the task and creating an execution plan.")?.play()

                    var planIndex = 3
                    while (true) {
                        if ((planIndex - 2) > (maxIterations * 2)) {
                            log.info("Reached maximum demonstration iterations, initiating stop sequence")
                            tts("Auto-Plan can continue to iterate over agent plans. However, for demonstration purposes, we will stop here.")?.play()
                            clickElement(
                                driver,
                                wait,
                                "div.message-body > div.tabs-container > div.tabs > button:nth-child(1)"
                            )
                            log.debug("Controls button clicked")
                            sleep(1000)
                            clickElement(driver, wait, "div.message-body > div.tabs-container > div.active a.href-link")
                            log.info("Stop sequence completed")
                            sleep(1000)
                            break
                        }

                        val planButton = clickElement(
                            driver,
                            wait,
                            "div.message-body > div.tabs-container > div.tabs > button:nth-child($planIndex)"
                        ).text
                        if (planButton === "Summary") {
                            log.info("Reached summary stage - execution plan complete")
                            tts("Task analysis completed. Review the summary for the execution plan.")?.play()
                            break
                        } else {
                            val iteration = (planIndex - 2) / 2
                            log.info("Processing agent iteration $iteration: ${planButton}")
                            tts("In iteration $iteration, Auto-Plan is analyzing the code structure and planning the next implementation steps.")?.play()
                        }

                        var taskIndex = 2
                        log.debug("Processing task ${taskIndex - 1}")
                        val startTime = System.currentTimeMillis()
                        while ((System.currentTimeMillis() - startTime) < TimeUnit.MINUTES.toMillis(2)) {
                            val taskButton =
                                try {
                                    getElement(
                                        WebDriverWait(driver, Duration.ofSeconds(1)),
                                        "div.message-body > div.tabs-container > div.active div.iteration.tabs-container > div.tabs > button:nth-child($taskIndex)"
                                    )
                                } catch (e: Exception) {
                                    //log.debug("Failed to find task button: ${e.message}", e)
                                    continue
                                }
                            val text = taskButton.text
                            if (text.isBlank()) {
                                sleep(100)
                                continue
                            }
                            if (text.trim().equals("Thinking Status", ignoreCase = true)) {
                                log.info("Iteration completed after ${taskIndex - 2} tasks")
                                tts("Task execution complete. Updating Thinking State.")?.play()
                                break
                            } else {
                                taskButton.click()
                                log.info("Executing task ${taskIndex - 1}: $text")
                                tts("Task ${taskIndex - 1} in progress.")?.play(3000)
                            }
                            taskIndex += 1
                            log.debug("Processing task ${taskIndex - 1}")
                        }
                        planIndex += 2
                    }

                    sleep(5000)
                    log.info("Auto-Plan execution completed successfully")

                    tts("Auto-Plan has completed the task execution. You can review the results in the interface.")?.play()
                } catch (e: Exception) {
                    log.error("Error during Auto-Plan interaction: ${e.message}", e)
                    tts("Encountered an error during Auto-Plan execution. Please check the logs for details.")?.play()
                    throw e
                } finally {
                    log.debug("Cleaning up web driver resources")
                    driver.quit()
                    log.info("Web driver cleanup completed")
                }
            } else {
                log.error("Failed to retrieve Auto-Plan interface URL from UDP messages")
                tts("Failed to retrieve Auto-Plan interface URL.")?.play()
            }
            log.debug("Clearing message buffer")
            clearMessageBuffer()
        }
        log.info("Auto-Plan demonstration test completed successfully")
        tts("We've seen how Auto-Plan can transform a simple request into a fully implemented feature, complete with proper structure, documentation, and tests. This AI-powered automation significantly accelerates the development process while maintaining high code quality.")?.play()
        Unit
    }

}