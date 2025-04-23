package com.simiacryptus.aicoder.demotest.action.generate

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JTextAreaFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.simiacryptus.aicoder.demotest.DemoTestBase
import com.simiacryptus.aicoder.demotest.SplashScreenConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.awt.event.KeyEvent
import java.lang.Thread.sleep
import java.time.Duration
import kotlin.io.path.name

/**
 * Integration test for the Generate Documentation action in the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - The DataGnome project must be open and loaded
 * - The project structure must contain the path: src/main/kotlin/com.simiacryptus.util/files
 * - The IDE should be in its default layout with no dialogs open
 *
 * Test Behavior:
 * 1. Opens the Project View if not already visible
 * 2. Navigates to the files utility package in the project structure
 * 3. Right-clicks to open the context menu
 * 4. Selects AI Coder > Generate Documentation
 * 5. Configures documentation generation with specific instructions
 * 6. Waits for and verifies the documentation generation
 * 7. Confirms the result appears in the editor
 *
 * Expected Results:
 * - Documentation should be generated for the files utility package
 * - The generated documentation should appear in a new editor window
 * - The process should complete within 60 seconds
 *
 * Note: This test includes voice feedback for demonstration purposes
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerateDocumentationActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Documentation Generator Demo",
    )
) {

    companion object {
        val log = LoggerFactory.getLogger(GenerateDocumentationActionTest::class.java)
    }

    override fun getTemplateProjectPath(): String {
        return "demo_projects/DataGnome"
    }

    override fun waitAfterProjectOpen() {
        sleep(45000)
    }

    @Test
    fun testGenerateDocumentation() = with(remoteRobot) {
        try {
            tts("Welcome to the AI Coder's Documentation Generator demo. This powerful feature automatically creates comprehensive API documentation by analyzing your code with AI assistance.")?.play(
                3000
            )

            step("Open project view") {
                tts("Let's start by opening the project view where we'll select our target code for documentation.")?.play()
                openProjectView()
            }
            step("Navigate to files utility package") {
                tts("Navigating to the files utility package for documentation generation.")?.play(2000)
            }

            step("Navigate to files utility package") {
                tts("Navigating to the files utility package for documentation generation.")?.play()
                //val path = arrayOf(testProjectDir.name, "src", "main", "kotlin", "com.simiacryptus.util.files")
                val path = arrayOf(testProjectDir.name, "src", "main", "kotlin", "com", "simiacryptus", "util", "files")
                val tree =
                    remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
                waitFor(Duration.ofSeconds(10)) { tree.rightClickPath(*path, fullMatch = false); true }
                tts("We'll navigate to the files utility package - a perfect example of code that would benefit from detailed documentation.")?.play(
                    2000
                )
            }

            step("Select 'AI Coder' menu") {
                tts("Now we'll access the AI Coder menu, which contains our documentation generation tools.")?.play(2000)
                selectAICoderMenu()
            }

            step("Click 'Generate Documentation' action") {
                tts("Let's select 'Generate Documentation' to begin the AI-powered documentation process.")?.play()
                waitFor(Duration.ofSeconds(15)) {
                    try {
                        // Find and hover over Generate menu
                        findAll(CommonContainerFixture::class.java, byXpath("//div[@text='⚡ Generate']"))
                            .firstOrNull()?.moveMouse()
                        sleep(1000)
                        findAll(
                            CommonContainerFixture::class.java,
                            byXpath("//div[@class='ActionMenuItem' and contains(@text, 'Generate Documentation')]")
                        )

                        // Click Generate Documentation option
                        findAll(
                            CommonContainerFixture::class.java,
                            byXpath("//div[@class='ActionMenuItem' and contains(@text, 'Generate Documentation')]")
                        )
                            .firstOrNull()?.click()
                        log.info("'Generate Documentation' action clicked")
                        true
                    } catch (e: Exception) {
                        log.warn("Failed to find 'Generate Documentation' action: ${e.message}")
                        false
                    }
                }
                sleep(2000)
            }

            step("Configure documentation generation") {
                val DIALOG_TITLE = "Compile Documentation"
                tts("Here we can customize how the AI generates our documentation. Let's provide specific instructions to ensure we get exactly what we need.")?.play()
                waitFor(Duration.ofSeconds(10)) {
                    val dialog = find(
                        CommonContainerFixture::class.java,
                        byXpath("//div[@class='MyDialog' and @title='$DIALOG_TITLE']")
                    )
                    dialog.isShowing
                }
                val dialog = find(
                    CommonContainerFixture::class.java,
                    byXpath("//div[@class='MyDialog' and @title='$DIALOG_TITLE']")
                )
                val aiInstructionField =
                    dialog.find(JTextAreaFixture::class.java, byXpath("//div[@class='JBTextArea']"))
                aiInstructionField.click()
                keyboard {
                    pressing(KeyEvent.VK_CONTROL) {
                        key(KeyEvent.VK_A) // Select all
                        key(KeyEvent.VK_BACK_SPACE) // Delete
                    }
                }
                remoteRobot.keyboard { enterText("Create comprehensive API documentation for the files utility package") }

                try {
                    val generateButton =
                        find(CommonContainerFixture::class.java, byXpath("//div[@class='JButton' and @text='OK']"))
                    generateButton.click()
                    log.info("Documentation generation configured and started")
                    tts("With our instructions set, the AI will now analyze the code and generate appropriate documentation following best practices.")?.play()
                    true
                } catch (e: Exception) {
                    log.warn("Failed to configure documentation generation: ${e.message}")
                    false
                }
            }
            tts("Watch as the AI analyzes the code structure, relationships, and patterns to create meaningful documentation. This typically takes a few moments depending on the codebase size.")?.play(
                5000
            )

            step("Verify documentation creation") {
                tts("The AI has completed its analysis. Let's examine the generated documentation.")?.play()
                waitFor(Duration.ofSeconds(600)) {
                    try {
                        val editor =
                            find(CommonContainerFixture::class.java, byXpath("//div[@class='EditorCompositePanel']"))
                        if (editor.isShowing) {
                            tts("Notice how the generated documentation includes detailed descriptions, parameter explanations, and usage examples - all automatically created while maintaining your project's documentation style.")?.play()
                            true
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to find opened editor: ${e.message}")
                        false
                    }
                }
                tts("And that's how easy it is to generate comprehensive documentation with AI Coder! This feature saves hours of manual documentation work while ensuring consistency and completeness across your codebase.")?.play(
                    3000
                )
            }
        } catch (e: Exception) {
            log.error("Error during documentation generation test", e)
            throw e
        } finally {
            clearMessageBuffer()
        }
    }

}