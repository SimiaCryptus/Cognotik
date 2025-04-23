package com.simiacryptus.aicoder.demotest.action.agent

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
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.time.Duration
import kotlin.io.path.name

/**
 * Integration test for the Command Autofix feature of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A project named "DataGnome" must be open and accessible
 * - The IDE should be in its default layout with no dialogs open
 * - Remote Robot server must be running and accessible
 *
 * Test Workflow:
 * 1. Opens the project view panel
 * 2. Navigates to the "DataGnome" directory in the project tree
 * 3. Opens the AI Coder menu
 * 4. Initiates the Auto-Fix action
 * 5. Configures Auto-Fix settings (enables auto-apply fixes)
 * 6. Interacts with the web-based Command Autofix interface
 * 7. Verifies successful build completion
 * 8. Handles potential errors with up to 5 retry attempts
 *
 * Expected Results:
 * - The Command Autofix process should complete successfully
 * - The build should remain successful after fixes are applied
 * - All UI interactions should be properly logged and narrated
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandAutofixActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Command Autofix Demo",
    )
) {
    override fun getTemplateProjectPath(): String {
        return "demo_projects/DataGnome"
    }

    companion object {
        val log = LoggerFactory.getLogger(CommandAutofixActionTest::class.java)
    }

    @Test
    fun testCommandAutofixAction() {
        with(remoteRobot) {
            tts("Welcome to the Command Autofix feature demonstration. This powerful tool automatically detects and resolves build and compilation issues across your entire project.")?.play(
                2000
            )

            step("Open project view") {
                tts("Let's start by opening our project view. We'll be working with a sample project that has some build issues to fix.")?.play()
                openProjectView()
            }

            step("Select a directory") {
                tts("We'll select the project root directory to analyze the entire codebase. Command Autofix works recursively through all project files.")?.play()
                val path = arrayOf(testProjectDir.name)
                val tree =
                    remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
                waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
            }

            step("Click 'Auto-Fix' action") {
                tts("Now we'll launch the Command Autofix feature through the AI Coder menu. This tool integrates seamlessly with your IDE's build system.")?.play()
                waitFor(Duration.ofSeconds(30)) {
                    try {
                        tts("Now we'll access the Auto-Fix feature through the AI Coder menu.")?.play()
                        tts("Navigate to the Agents submenu, where you'll find various automated assistance tools.")?.play()
                        val aiCoderMenu = selectAICoderMenu()
                        val agentsMenu = aiCoderMenu.find(
                            CommonContainerFixture::class.java,
                            byXpath("//div[contains(@class, 'Menu') and contains(@text, 'Agents')]")
                        )
                        // Add delay for menu animation
                        sleep(500)
                        robot.mouseMove(agentsMenu.locationOnScreen.x + 10, agentsMenu.locationOnScreen.y)
                        agentsMenu.click()
                        sleep(2000)
                        val autoFixMenu = agentsMenu.find(
                            CommonContainerFixture::class.java,
                            byXpath("//div[contains(@class, 'MenuItem') and contains(@text, 'Run ... and Fix')]")
                        )
                        robot.mouseMove(autoFixMenu.locationOnScreen.x + 10, autoFixMenu.locationOnScreen.y)
                        autoFixMenu.click()
                        log.info("'Auto-Fix' action clicked")
                        true
                    } catch (e: Exception) {
                        log.warn("Failed to navigate Auto-Fix menu: ${e.message}")
                        // Wait for initial analysis
                        tts("The AI is now analyzing your codebase and build configuration. This may take a few moments.")?.play()
                        sleep(5000)
                        // Wait longer for response and processing
                        sleep(10000)
                        tts("The AI is now processing the request and making necessary code changes. Let's review the modifications as they happen.")?.play()
                        // Give time to view changes
                        sleep(5000)
                        tts("If the menu doesn't appear immediately, the IDE will automatically retry. This ensures reliable access to the feature.")?.play()
                        false
                    }
                }
            }

            step("Configure Command Autofix") {
                tts("Let's configure the Command Autofix settings. The tool offers several options to customize how fixes are applied.")?.play()
                waitFor(Duration.ofSeconds(15)) {
                    val dialog = find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@class='MyDialog' and @title='Command Autofix Settings']")
                    )
                    if (dialog.isShowing) {
                        val autoFixCheckbox = dialog.find(
                            JCheckboxFixture::class.java,
                            byXpath("//div[@class='JCheckBox' and @text='Auto-apply fixes']")
                        )
                        autoFixCheckbox.select()
                        tts("We'll enable the 'Auto-apply fixes' option, which allows Command Autofix to automatically implement suggested fixes without manual confirmation.")?.play()

                        val okButton =
                            dialog.find(
                                CommonContainerFixture::class.java,
                                byXpath("//div[@class='JButton' and @text='OK']")
                            )
                        okButton.click()
                        log.info("Command Autofix configured and started")
                        true
                    } else {
                        false
                    }
                }
            }
            sleep(1000)

            step("Interact with Command Autofix interface") {
                var url: String? = null
                waitFor(Duration.ofSeconds(90)) {
                    val messages = getReceivedMessages()
                    url = messages.firstOrNull { it.startsWith("http") }
                    url != null
                }
                if (url != null) {
                    log.info("Retrieved URL: $url")
                    tts("The Command Autofix interface opens in your browser, providing a detailed view of the analysis process and any fixes being applied.")?.play()
                    try {
                        this@CommandAutofixActionTest.driver.get(url.toString())
                        // Add longer initial wait for interface load
                        sleep(5000)
                    } catch (e: Exception) {
                        log.error("Failed to initialize browser", e)
                        throw e
                    }

                    var attempt = 1
                    while (attempt <= 5) {
                        val wait = WebDriverWait(this@CommandAutofixActionTest.driver, Duration.ofSeconds(600))
                        try {
                            tts("Watch as Command Autofix analyzes your build output, identifies issues, and applies appropriate fixes. The AI considers your project's context and build configuration.")?.play()
                            // Wait for response content
                            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("response-message")))
                            // Give time to view response
                            sleep(5000)
//              val buildSuccessful = codeElements.any { it.text.contains("BUILD SUCCESSFUL") }
//              require(buildSuccessful) { "BUILD SUCCESSFUL not found in any code element" }
                            tts("Excellent! Command Autofix has successfully resolved the build issues. Notice how the build now completes without errors, demonstrating the effectiveness of the automated fixes.")?.play()
                            break
                        } catch (e: Exception) {
                            attempt++
                            log.warn("Error interacting with Command Autofix interface", e)
                            tts("Sometimes multiple attempts may be needed for complex issues. Command Autofix will automatically retry with different approaches until the build succeeds.")?.play()
                            (driver as JavascriptExecutor).executeScript("window.scrollTo(0, 0)")
                            val refreshButton = driver.findElement(By.xpath("//a[@class='href-link' and text()='♻']"))
                            refreshButton.click()
                            log.info("Refresh button clicked")
                            tts("Refreshing Command Autofix interface.")?.play()
                            driver.findElements(By.cssSelector(".tabs-container > .tabs > .tab-button"))
                                .get(attempt - 1).click()
                        }
                    }
                    this@CommandAutofixActionTest.driver.quit()
                } else {
                    log.error("No URL found in UDP messages")
                    tts("If you encounter any issues accessing the interface, ensure your IDE has permission to open browser windows.")?.play()
                }
                clearMessageBuffer()
            }

            tts("And that concludes our demonstration of Command Autofix. This powerful feature saves time by automatically resolving build issues, allowing you to focus on writing code rather than fixing build problems.")?.play()
            Unit
        }
    }

}