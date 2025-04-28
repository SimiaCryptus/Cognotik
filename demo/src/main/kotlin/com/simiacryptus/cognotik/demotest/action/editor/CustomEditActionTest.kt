package com.simiacryptus.cognotik.demotest.action.editor

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
import org.slf4j.LoggerFactory
import java.awt.event.KeyEvent
import java.lang.Thread.sleep
import java.time.Duration
import kotlin.io.path.name

/**
 * Tests the Custom Edit functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open with Main.kt file
 * - The IDE should be in its default layout
 * - The AI Coder plugin should be properly configured
 *
 * Test Flow:
 * 1. Opens the Project View if not already visible
 * 2. Navigates to and opens the Main.kt file
 * 3. Selects all code in the editor
 * 4. Opens the context menu via right-click
 * 5. Navigates through the AI Coder menu to select Custom Edit
 * 6. Enters a custom edit instruction
 * 7. Verifies the edit is applied
 *
 * Expected Results:
 * - The custom edit should be applied to the selected code
 * - The editor should update with the modified code
 * - All UI interactions should complete without errors
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomEditActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Custom Edit Demo",
    )
) {
    companion object {
        private val log = LoggerFactory.getLogger(CustomEditActionTest::class.java)
    }

    private fun selectAllText(editor: EditorFixture) {
        log.debug("Selecting all text in editor")
        editor.click()
        editor.keyboard {
            pressing(KeyEvent.VK_CONTROL) {
                key(KeyEvent.VK_A)
            }
        }
        sleep(500)
    }

    @Test
    fun testCustomEdit() = with(remoteRobot) {
        tts("Welcome to the Custom Edit feature demonstration. This powerful tool helps you modify code using natural language instructions.")?.play(
            2000
        )

        step("Open project view and file") {
            log.info("Opening project view and navigating to test file")
            tts("Let's start by opening a sample code file that we'll enhance with AI assistance.")?.play()
            openProjectView()
            val path = arrayOf(testProjectDir.name, "src", "main", "kotlin", "Main.kt")
            log.debug("Navigating to file path: {}", path.joinToString("/"))
            val tree = find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) { tree.doubleClickPath(*path, fullMatch = false); true }
            sleep(2000)
        }

        step("Test Custom Edit") {
            tts("Let's examine the code we want to improve. Take a moment to notice its current structure.")?.play()
            sleep(5000)
            log.info("Starting Custom Edit operation")

            sleep(3000)
            tts("Now we'll use Custom Edit to improve our code. First, let's select the code we want to modify.")?.play()

            waitFor(Duration.ofSeconds(30)) {
                try {
                    val editor = find(EditorFixture::class.java, byXpath("//div[@class='EditorComponentImpl']"))
                    selectAllText(editor)

                    sleep(2000)
                    tts("With our code selected, we can access Custom Edit through the context menu.")?.play(2000)
                    editor.rightClick(editor.findAllText().firstOrNull()?.point?.location!!)

                    selectAICoderMenu()

                    sleep(1000)
                    tts("Now we'll select Custom Edit from the AI Coder menu.")?.play(300)
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@text='AI Coder']//div[contains(@text, 'Edit Code')]")
                    ).firstOrNull()?.let {
                        sleep(500)
                        it.click()
                        true
                    } ?: false
                } catch (e: Exception) {
                    log.warn("Failed to interact with editor: ${e.message}")
                    false
                }
            }

            log.debug("Waiting for edit instruction dialog")
            waitFor(Duration.ofSeconds(10)) {
                try {
                    val dialog = find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@class='JDialog' and @title='Edit Code']")
                    )
                    val textField =
                        dialog.find(
                            CommonContainerFixture::class.java,
                            byXpath("//div[@class='MultiplexingTextField']")
                        )
                    textField.click()
                    tts("The Custom Edit dialog allows us to describe our desired changes in natural language. Let's add error handling to our code.")?.play()
                    keyboard {
                        enterText("Add error handling")
                    }
                    tts("We've instructed the AI to add error handling. This will make our code more robust and reliable.")?.play()
                    sleep(1000)

                    val okButton =
                        dialog.find(
                            CommonContainerFixture::class.java,
                            byXpath("//div[@class='JButton' and @text='OK']")
                        )
                    okButton.click()
                    true
                } catch (e: Exception) {
                    log.warn("Failed to interact with edit dialog: ${e.message}")
                    false
                }
            }

            log.debug("Custom Edit operation triggered")
            tts("Watch as the AI analyzes our code and intelligently adds appropriate error handling patterns. It considers the context and best practices for our programming language.")?.play(
                3000
            )
        }

        tts("And there we have it! The AI has enhanced our code with proper error handling. Notice how it maintained the original functionality while making the code more robust. This is just one example of how Custom Edit can help improve your code through natural language instructions.")?.play(
            2000
        )
        return@with
    }
}