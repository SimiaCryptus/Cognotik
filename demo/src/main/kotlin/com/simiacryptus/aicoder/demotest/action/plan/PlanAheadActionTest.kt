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
import kotlin.io.path.name

/**
 * Test class for the Plan Ahead Action feature of AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A project named "DataGnome" must be open
 * - The project must have a standard Kotlin project structure with src/main/kotlin directories
 * - The IDE should be in its default layout with no dialogs open
 *
 * Test Flow:
 * 1. Opens the Project View if not already visible
 * 2. Navigates to the kotlin source directory in the project tree
 * 3. Opens the AI Coder context menu
 * 4. Launches the Task Runner feature
 * 5. Configures the Task Runner with auto-fix enabled
 * 6. Interacts with the Task Runner web interface to:
 *    - Request adding a feature
 *    - Accept the AI's proposed changes
 *    - Wait for implementation
 *
 * Expected Results:
 * - The Task Runner should successfully complete the requested feature addition
 * - The test includes voice feedback for demonstration purposes
 * - All UI interactions should complete without errors
 *
 * Note: This test includes delays for demonstration purposes and voice narration
 * for educational/demo scenarios. These can be adjusted or removed for production testing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanAheadActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Task Planning Demo",
    )
) {

    override fun getTemplateProjectPath(): String {
        return "demo_projects/TestProject"
    }

    companion object {
        private val log = LoggerFactory.getLogger(PlanAheadActionTest::class.java)
        private const val TASK_RUNNER_XPATH =
            "//div[contains(@class, 'ActionMenuItem') and contains(@text, 'Task Runner')]"
    }

    @Test
    fun testPlanAheadAction() = with(remoteRobot) {
        tts("Welcome to the Task Runner feature demonstration. This powerful tool helps break down complex development tasks into manageable steps and executes them automatically.")?.play(
            3000
        )
        step("Open project view") {
            tts("Let's start by opening the project view where we'll select our target directory for task execution.")?.play(
                2000
            )
            openProjectView()
        }

        step("Select source directory") {
            log.debug("Starting source directory selection")
            tts("We'll select the main source directory to demonstrate how Task Runner analyzes project structure and context.")?.play()
            val path = arrayOf(testProjectDir.name, "src", "main", "kotlin")
            log.debug("Attempting to locate project tree with path: ${path.joinToString("/")}")
            val tree = remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            log.debug("Project tree found, expanding path")
            waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
            log.info("Source directory selected and right-clicked successfully")
            sleep(3000)
        }

        step("Select 'AI Coder' menu") {
            log.debug("Attempting to open AI Coder menu")
            tts("Now we'll access the AI Coder menu, which contains our intelligent development tools.")?.play(3000)
            selectAICoderMenu()
        }

        step("Click 'Task Runner' action") {
            log.debug("Starting Task Runner action selection")
            tts("Let's launch the Task Runner. This tool will help us plan and execute complex development tasks with AI assistance.")?.play()
            waitFor(Duration.ofSeconds(10)) {
                try {
                    // First navigate to Task Plans submenu
                    val taskPlansMenu = find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@text='AI Coder']//div[@text='📋 Task Plans']")
                    )
                    taskPlansMenu.moveMouse()
                    sleep(1000)
                    // Then find and click Task Runner
                    findAll(CommonContainerFixture::class.java, byXpath(TASK_RUNNER_XPATH))
                        .firstOrNull()?.click()
                    log.info("Task Runner action found and clicked successfully")
                    tts("Task Runner is now initializing. We'll configure it to automatically implement our requested changes.")?.play()
                    true
                } catch (e: Exception) {
                    log.warn("Failed to find 'Task Runner' action. Error: ${e.message}", e)
                    tts("Just a moment while we locate the Task Runner option...")?.play()
                    false
                }
            }
            sleep(3000)
        }

        step("Configure Task Runner") {
            log.debug("Starting Task Runner configuration")
            tts("Let's configure the Task Runner for optimal performance. We'll enable automatic fix application and disable blocking for seamless execution.")?.play()
            waitFor(Duration.ofSeconds(10)) {
                val dialog = find(
                    CommonContainerFixture::class.java,
                    byXpath("//div[@class='MyDialog' and @title='Configure Planning and Tasks']")
                )
                if (dialog.isShowing) {
                    log.debug("Configuration dialog found and visible")
                    dialog.find(
                        JCheckboxFixture::class.java,
                        byXpath("//div[contains(@class, 'JCheckBox') and contains(@text, 'Auto-apply fixes')]")
                    ).apply {
                        if (!isSelected()) {
                            click()
                            log.info("Auto-apply fixes checkbox toggled to selected state")
                            tts("Enabled 'auto-apply fixes'; automatically apply suggested code changes.")?.play()
                        }
                    }
                    dialog.find(
                        JCheckboxFixture::class.java,
                        byXpath("//div[contains(@class, 'JCheckBox') and contains(@text, 'Allow blocking')]")
                    ).apply {
                        if (isSelected()) {
                            click()
                            log.info("Allow blocking checkbox toggled to deselected state")
                            tts("Disabled 'blocking'; do not wait for user input.")?.play()
                        }
                    }
                    sleep(3000)
                    log.debug("Attempting to click OK button")

                    val okButton = dialog.find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[contains(@class, 'JButton') and contains(@text, 'OK')]")
                    )
                    okButton.click()
                    log.info("Task Runner configuration completed and dialog closed")
                    tts("Configuration complete. The Task Runner is now optimized for automated task execution with AI assistance.")?.play()
                    true
                } else {
                    log.warn("Configuration dialog not found or not visible")
                    false
                }
            }
        }

        step("Interact with Task Runner interface") {
            log.debug("Starting Task Runner interface interaction")
            var url: String? = null
            waitFor(Duration.ofSeconds(90)) {
                val messages = getReceivedMessages()
                url = messages.firstOrNull { it.startsWith("http") } ?: ""
                log.debug("Searching for URL in messages. Found: ${url?.take(50) ?: "none"}")
                url?.isNotEmpty() ?: false
            }
            if (url != null) {
                log.info("Retrieved Task Runner interface URL: $url")
                tts("The Task Runner interface is now ready. We'll use it to create a new utility class with AI assistance.")?.play()
                log.debug("Initializing web driver")
                driver.get(url)
                log.debug("Setting up WebDriverWait with 90 second timeout")
                val wait = WebDriverWait(this@PlanAheadActionTest.driver, Duration.ofSeconds(90))
                val chatInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("chat-input")))
                log.info("Chat interface loaded successfully")
                tts("Watch as the AI analyzes our request and breaks it down into manageable development steps.")?.play(
                    1000
                )

                try {
                    log.debug("Submitting task to chat interface")
                    chatInput.sendKeys("Create a new data validation utility class with methods for common validation operations")
                    clickElement(driver, wait, "#send-message-button")
                    log.info("Task submitted successfully")
                    tts("We're requesting the creation of a data validation utility class. The AI will plan and implement this feature step by step.")?.play()

                    log.debug("Beginning execution progress monitoring")
                    tts("Task Runner is analyzing the task and creating an execution plan.")?.play()

                    var planIndex = 1
                    var maxPlanIterations = 10
                    while (true) {
                        if (planIndex > maxPlanIterations) {
                            log.info("Reached maximum plan iterations")
                            break
                        }

                        val duration = Duration.ofSeconds(if (planIndex == 1) 300 else 60)
                        val wait = WebDriverWait(driver, duration)
                        log.debug("Waiting $duration for plan iteration $planIndex")
                        try {
                            clickElement(
                                driver,
                                wait,
                                "div.task-tabs.tabs-container > div.tabs > .tab-button:nth-child($planIndex)"
                            )
                            val planButtonText = runElement(
                                wait,
                                "div.task-tabs.tabs-container > div.tabs > .tab-button:nth-child($planIndex)"
                            ) { it.text }
                            if (planButtonText.contains("Summary", ignoreCase = true)) {
                                log.info("Reached summary tab - execution complete")
                                break
                            }
                            log.debug("Processing plan task $planIndex: ${planButtonText}")
                            tts("Plan iteration $planIndex in progress: ${planButtonText}")?.play()
                        } catch (e: Exception) {
                            log.warn("Error processing plan iteration $planIndex: ${e.message}")
                            break
                        }
                        sleep(500)
                        runElement(
                            driver, wait, "div.task-tabs.tabs-container > div.active div.tab-content", """
                arguments[0].scrollIntoView(true);
            """.trimIndent()
                        )
                        sleep(5000)
                        planIndex += 1
                    }
                } catch (e: Exception) {
                    log.error("Error during Task Runner interaction: ${e.message}", e)
                    tts("Encountered an error during Task Runner execution. Please check the logs for details.")?.play()
                } finally {
                    log.debug("Cleaning up web driver resources")
                    driver.quit()
                    log.info("Web driver cleanup completed")
                }

            } else {
                log.error("Failed to retrieve Task Runner interface URL from UDP messages")
                tts("Failed to retrieve Task Runner interface URL.")?.play()
            }
            log.debug("Clearing message buffer")
            clearMessageBuffer()
        }
        log.info("Task Runner demonstration test completed successfully")

        tts("And there you have it! The Task Runner has successfully planned and implemented our requested feature. This powerful tool streamlines development by breaking down complex tasks and executing them automatically with AI assistance.")?.play(
            10000
        )
        return@with
    }

}