package com.simiacryptus.aicoder.demotest.action.editor

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.EditorFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import com.simiacryptus.aicoder.demotest.DemoTestBase
import com.simiacryptus.aicoder.demotest.SplashScreenConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Duration
import kotlin.io.path.name

/**
 * Tests the Smart Paste functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open with Main.kt file
 * - The IDE should be in its default layout
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmartPasteActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Smart Paste Demo",
    )
) {
    companion object {
        private val log = LoggerFactory.getLogger(SmartPasteActionTest::class.java)
    }

    private fun setClipboardContent(text: String) {
        log.debug("Setting clipboard content: {}", text)
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    @Test
    fun testSmartPaste() = with(remoteRobot) {
        tts("Welcome to the AI Coder Smart Paste demo.")?.play(2000)

        step("Open project view and file") {
            log.info("Opening project view and navigating to test file")
            openProjectView()
            val path = arrayOf(testProjectDir.name, "src", "main", "kotlin", "Main.kt")
            log.debug("Navigating to file path: {}", path.joinToString("/"))
            val tree = find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) { tree.doubleClickPath(*path, fullMatch = false); true }
            Thread.sleep(2000)
        }

        step("Test Smart Paste") {
            log.info("Starting Smart Paste operation")
            tts("Demonstrating the Smart Paste feature for converting JavaScript to Kotlin.")?.play()

            setClipboardContent(
                """
                function calculateSum(a, b) {
                    return a + b;
                }
            """.trimIndent()
            )
            log.debug("Sample JavaScript code set to clipboard")

            val editor = find(EditorFixture::class.java, byXpath("//div[@class='EditorComponentImpl']"))
            editor.rightClick(editor.findAllText().firstOrNull()?.point?.location!!)
            Thread.sleep(1000)

            selectAICoderMenu()
            log.debug("Attempting to find and click Smart Paste menu item")
            findAll(
                CommonContainerFixture::class.java,
                byXpath("//div[contains(@class, 'ActionMenuItem') and contains(@text, 'Smart Paste')]")
            ).firstOrNull()?.click()

            log.debug("Smart Paste operation triggered")
            tts("Smart Paste will now convert the JavaScript function to Kotlin syntax.")?.play(3000)
        }

        tts("Smart Paste demonstration completed.")?.play(2000)
        return@with
    }
}