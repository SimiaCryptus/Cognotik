package com.simiacryptus.aicoder.demotest.action.editor

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.EditorFixture
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
import java.time.Duration
import kotlin.io.path.name

/**
 * Tests the Describe Code functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open with Main.kt file
 * - The IDE should be in its default layout
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DescribeCodeActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Describe Code Demo",
    )
) {
    companion object {
        private val log = LoggerFactory.getLogger(DescribeCodeActionTest::class.java)
    }

    private fun selectAllText(editor: EditorFixture) {
        log.debug("Selecting all text in editor")
        editor.click()
        editor.keyboard {
            pressing(KeyEvent.VK_CONTROL) {
                key(KeyEvent.VK_A)
            }
        }
        Thread.sleep(500)
    }

    @Test
    fun testDescribeCode() = with(remoteRobot) {
        tts("Welcome to the Describe Code feature demonstration. This powerful tool helps developers automatically generate clear and comprehensive documentation for their code.")?.play(
            2000
        )

        step("Open project view and file") {
            log.info("Opening project view and navigating to test file")
            tts("Let's start by opening a Kotlin source file that needs documentation.")?.play()
            openProjectView()
            val path = arrayOf(testProjectDir.name, "src", "main", "kotlin", "Main.kt")
            log.debug("Navigating to file path: {}", path.joinToString("/"))
            val tree = find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) { tree.doubleClickPath(*path, fullMatch = false); true }
            Thread.sleep(2000)
            tts("We'll use Main.kt as our example file. Notice how it currently lacks proper documentation.")?.play()
        }

        step("Test Describe Code") {
            log.info("Starting Code Description operation")
            tts("To add documentation, we'll first select the code we want to document.")?.play()

            val editor = find(EditorFixture::class.java, byXpath("//div[@class='EditorComponentImpl']"))
            selectAllText(editor)
            tts("With our code selected, we can now access the Describe Code feature through the context menu.")?.play(
                1000
            )
            editor.rightClick(editor.findAllText().firstOrNull()?.point?.location!!)

            selectAICoderMenu()
            log.debug("Attempting to find and click Describe Code menu item")
            findAll(
                CommonContainerFixture::class.java,
                byXpath("//div[contains(@class, 'ActionMenuItem') and contains(@text, 'Describe Code')]")
            ).firstOrNull()?.click()

            log.debug("Code Description operation triggered")
            tts("Watch as the AI analyzes the code structure and generates appropriate documentation comments. It will identify:")?.play(
                1000
            )
            Thread.sleep(1000)
            tts("- Function purposes and behaviors")?.play()
            Thread.sleep(500)
            tts("- Parameter descriptions and types")?.play()
            Thread.sleep(500)
            tts("- Return value details")?.play()
            Thread.sleep(500)
            tts("- Usage examples where appropriate")?.play(3000)
        }

        tts("And there we have it! The code is now properly documented with clear, maintainable comments that follow best practices. This documentation will help other developers understand the code's purpose and usage more easily.")?.play(
            2000
        )
        return@with
    }
}