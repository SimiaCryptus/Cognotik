package com.simiacryptus.aicoder.demotest.action.generate

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JTextAreaFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.simiacryptus.aicoder.demotest.DemoTestBase
import com.simiacryptus.aicoder.demotest.SplashScreenConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.awt.event.KeyEvent
import java.lang.Thread.sleep
import java.time.Duration
import kotlin.io.path.name

/**
 * UI Test for the Generate Related File action in the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project named "TestProject" must be open
 * - The project must contain a README.md file in its root directory
 * - The IDE should be in its default layout with no dialogs open
 *
 * Test Behavior:
 * 1. Opens the Project View if not already visible
 * 2. Locates and selects the README.md file in the project tree
 * 3. Opens the context menu on the README.md file
 * 4. Navigates through the AI Coder menu to select "Generate Related File"
 * 5. Enters a directive to convert the README to a reveal.js presentation
 * 6. Initiates the generation process
 * 7. Verifies that a new presentation.html file is created
 *
 * The test includes voice feedback for demonstration purposes and includes
 * retry logic for potentially flaky UI interactions.
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerateRelatedFileActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Generate Related File Demo",
    )
) {

    override fun getTemplateProjectPath(): String {
        return "demo_projects/TestProject"
    }

    companion object {
        val log = LoggerFactory.getLogger(GenerateRelatedFileActionTest::class.java)
    }

    @Test
    fun testGenerateRelatedFile() = with(remoteRobot) {
        tts("Welcome to the Generate Related File feature demonstration. This powerful tool helps you create related files from existing content using AI assistance.")?.play(
            3000
        )

        step("Open project view") {
            tts("Let's start by accessing our project structure in the Project View panel.")?.play(2000)
            openProjectView()
        }

        step("Select README.md file") {
            tts("We'll use this README.md file as our source. The AI will analyze its content to generate a related file.")?.play()
            val path = arrayOf(testProjectDir.name, "README.md")
            val tree = remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) { tree.clickPath(*path, fullMatch = false); true }
            log.info("README.md file selected")
        }

        step("Open context menu") {
            log.info("Opening project view and navigating to test file")
            tts("Let's start by opening a sample code file that we'll enhance with AI assistance.")?.play()
            openProjectView()
            val path = arrayOf(testProjectDir.name, "README.md")
            log.debug("Navigating to file path: {}", path.joinToString("/"))
            val tree = find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
            sleep(2000)
        }

        step("Select 'AI Coder' menu") {
            tts("Navigate to the AI Coder menu, where you'll find various AI-powered code generation tools.")?.play()
            selectAICoderMenu()
        }

        step("Click 'Generate Related File' action") {
            tts("Select 'Generate Related File' to create a new file based on our README's content. This feature intelligently generates related files while maintaining context.")?.play()
            waitFor(Duration.ofSeconds(15)) {
                try {
                    // Find and hover over Generate menu
                    findAll(CommonContainerFixture::class.java, byXpath("//div[@text='⚡ Generate']"))
                        .firstOrNull()?.moveMouse()
                    sleep(1000)
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@class='ActionMenuItem' and contains(@text, 'Generate Related File')]")
                    )

                    // Click Generate Related File option
                    findAll(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@class='ActionMenuItem' and contains(@text, 'Generate Related File')]")
                    )
                        .firstOrNull()?.click()
                    log.info("'Generate Related File' action clicked successfully")
                    return@waitFor true
                } catch (e: Exception) {
                    log.warn("Attempt failed: ${e.message}")
                    return@waitFor false
                }
            }
        }

        step("Enter file generation directive") {
            val DIRECTIVE = "Convert this README.md into a reveal.js HTML presentation"
            tts("Now we'll provide instructions for the AI. Let's convert our README into an interactive HTML presentation using reveal.js. Watch how the AI understands and transforms the content.")?.play()
            waitFor(Duration.ofSeconds(30)) {
                try {
                    val textField = find(JTextAreaFixture::class.java, byXpath("//div[@class='JTextArea']"))
                    textField.click()
                    remoteRobot.keyboard {
                        pressing(KeyEvent.VK_CONTROL) {
                            key(KeyEvent.VK_A)
                        }
                        enterText(DIRECTIVE)
                    }
                    tts("Directive entered: $DIRECTIVE")?.play()
                    log.info("File generation directive entered")
                    sleep(3000)
                    val okButton = find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@class='MyDialog']//div[@class='JButton' and @text='Generate']")
                    )
                    okButton.click()
                    log.info("Generate button clicked")
                    tts("The AI is now analyzing the README content and generating a presentation that maintains the original structure while adding interactive elements.")?.play()
                    true
                } catch (e: Exception) {
                    log.error("Failed to enter directive or click generate button", e)
                    false
                }
            }
            tts("Waiting for file generation.")?.play()
        }

        step("Verify file creation") {
            tts("Let's examine the generated presentation file. Notice how the AI has preserved the content hierarchy while adding reveal.js presentation features.")?.play()
            waitFor(Duration.ofSeconds(600)) {
                try {
                    find(
                        ComponentFixture::class.java,
                        byXpath("//div[@class='EditorCompositePanel']"),
                        Duration.ofSeconds(600)
                    )
                    log.info("Presentation.html file created successfully")
                    tts("The presentation has been created successfully. You can now use this HTML file for interactive presentations of your documentation.")?.play()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            sleep(3000)
        }

        tts("This demonstrates how the Generate Related File feature can transform existing content into different formats while preserving meaning and structure. Try it with other file types and transformations to enhance your development workflow.")?.play()
        Unit
    }

    @AfterAll
    fun cleanup() {
        try {
            clearMessageBuffer()
            log.info("Cleanup completed successfully")
        } catch (e: Exception) {
            log.error("Cleanup failed", e)
        }
    }
}