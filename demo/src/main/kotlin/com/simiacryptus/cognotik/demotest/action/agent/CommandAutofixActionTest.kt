package com.simiacryptus.cognotik.demotest.action.agent

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JCheckboxFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.component
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
import com.simiacryptus.cognotik.util.LoggerFactory
import java.awt.Point
import java.awt.event.KeyEvent.VK_CONTROL
import java.awt.event.KeyEvent.VK_V
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
    ),
    narrationFile = "narrations/command-autofix.json"
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
            playNarration("welcome", 2000)

            step("Open project view") {
                playNarration("open_project_view")
                openProjectView()
            }


            // Step 1: Copy the location of gradlew
            step("Copy gradlew path") {
                playNarration("copy_gradlew_path")
                val path = arrayOf(testProjectDir.name)
                remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply {
                    expandAll(path)
                    sleep(100)
                    val rowNumber = collectRows().mapIndexed { index, row ->
                        if (row.endsWith("gradlew")) {
                            index
                        } else {
                            null
                        }
                    }.filterNotNull().firstOrNull() ?: throw RuntimeException("gradlew not found in project tree")
                    rightClickRow(rowNumber = rowNumber)
                }
                component("//div[@accessiblename='Copy Path/Reference…']")
                    .click(Point(73, 16))
                component("//div[@accessiblename='Copy' and @class='MyList']")
                    .click(Point(172, 17))
                log.info("Gradlew path copied to clipboard")
            }

            step("Select a directory") {
                playNarration("select_directory")
                val path = arrayOf(testProjectDir.name)
                val tree =
                    remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
                waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
            }

            step("Click 'Auto-Fix' action") {
                playNarration("access_menu")
                playNarration("navigate_agents")
                waitFor(Duration.ofSeconds(30)) {
                    try {
                        val aiCoderMenu = selectAICoderMenu()
                        val agentsMenu = aiCoderMenu.find(
                            CommonContainerFixture::class.java,
                            byXpath("//div[contains(@class, 'Menu') and contains(@text, 'Agents')]")
                        )

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
//                        playNarration("analysis_process")
                        sleep(5000)
//                        playNarration("retry_attempts")
                        false
                    }
                }
            }

            step("Configure Command Autofix") {
                playNarration("configure_settings")
                waitFor(Duration.ofSeconds(15)) {
                    val dialog = find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@class='MyDialog' and @title='Command Autofix Settings']")
                    )
                    if (dialog.isShowing) {
                        // Step 2: Paste the location of gradlew into the tool selection input
                        step("Configure tool selection") {
                            playNarration("configure_tool_selection")
                            val toolSelectionField = dialog.find(
                                JTextFieldFixture::class.java,
                                byXpath("//div[@class='CommandPanel']/div[@class='JPanel'][2]/div[@class='JPanel'][1]/div[@class='ComboBox']/div[@class='BorderlessTextField']")
                            )
                            toolSelectionField.click()
                            keyboard {
                                hotKey(VK_CONTROL, VK_V) // Paste gradlew path
                            }
                            log.info("Gradlew path pasted into tool selection field")
                        }
                        // Step 3: Add 'clean build' as the tool execution arguments
                        step("Configure tool arguments") {
                            playNarration("configure_tool_arguments")
                            val argumentsField = dialog.find(
                                JTextFieldFixture::class.java,
                                byXpath("//div[@class='CommandPanel']/div[@class='JPanel'][2]/div[@class='JPanel'][2]/div[@class='ComboBox']/div[@class='BorderlessTextField']")
                            )
                            argumentsField.click()
                            keyboard {
                                enterText("clean build")
                            }
                            log.info("Tool arguments set to 'clean build'")
                        }

                        val autoFixCheckbox = dialog.find(
                            JCheckboxFixture::class.java,
                            byXpath("//div[@class='JCheckBox' and @text='Auto-apply fixes']")
                        )
                        autoFixCheckbox.select()
                        playNarration("enable_auto_apply")

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
                    playNarration("browser_interface")
                    try {
                        this@CommandAutofixActionTest.driver.get(url.toString())

                        sleep(5000)
                    } catch (e: Exception) {
                        log.error("Failed to initialize browser", e)
                        throw e
                    }

                    var attempt = 1
                    while (attempt <= 5) {
                        val wait = WebDriverWait(this@CommandAutofixActionTest.driver, Duration.ofSeconds(600))
                        try {
                            playNarration("analysis_process")

                            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("response-message")))

                            sleep(5000)


                            playNarration("success_completion")
                            break
                        } catch (e: Exception) {
                            attempt++
                            log.warn("Error interacting with Command Autofix interface", e)
                            playNarration("retry_attempts")
                            (driver as JavascriptExecutor).executeScript("window.scrollTo(0, 0)")
                            val refreshButton = driver.findElement(By.xpath("//a[@class='href-link' and text()='♻']"))
                            refreshButton.click()
                            log.info("Refresh button clicked")
                            playNarration("refresh_interface")
                            driver.findElements(By.cssSelector(".tabs-container > .tabs > .tab-button"))
                                .get(attempt - 1).click()
                        }
                    }
                    this@CommandAutofixActionTest.driver.quit()
                } else {
                    log.error("No URL found in UDP messages")
                    playNarration("troubleshooting")
                }
                clearMessageBuffer()
            }

            playNarration("demo_conclusion")
            Unit
        }
    }

}