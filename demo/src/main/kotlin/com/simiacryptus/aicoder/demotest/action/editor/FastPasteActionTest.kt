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
 * Tests the Fast Paste functionality of the AI Coder plugin.
 *
 * Prerequisites:
 * - IntelliJ IDEA must be running with the AI Coder plugin installed
 * - A test project must be open with Main.kt file
 * - The IDE should be in its default layout
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FastPasteActionTest : DemoTestBase(
    splashScreenConfig = SplashScreenConfig(
        titleText = "Fast Paste Demo",
    )
) {
    companion object {
        private val log = LoggerFactory.getLogger(FastPasteActionTest::class.java)
    }

    private fun setClipboardContent(text: String) {
        log.debug("Setting clipboard content: {}", text)
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    @Test
    fun testFastPaste() = with(remoteRobot) {
        tts("Welcome to Fast Paste, a powerful feature that instantly converts code between different programming languages while preserving functionality.")?.play(
            2000
        )

        step("Open project view and file") {
            log.info("Opening project view and navigating to test file")
            tts("Let's start by opening a Kotlin file where we'll demonstrate the conversion capabilities.")?.play()
            openProjectView()
            val path = arrayOf(testProjectDir.name, "src", "main", "kotlin", "Main.kt")
            log.debug("Navigating to file path: {}", path.joinToString("/"))
            val tree = find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH)).apply { expandAll(path) }
            waitFor(Duration.ofSeconds(10)) { tree.doubleClickPath(*path, fullMatch = false); true }
            Thread.sleep(2000)
        }

        step("Test Fast Paste") {
            log.info("Starting Fast Paste operation")
            tts("I have some Java code in HTML format on the clipboard. Watch how Fast Paste intelligently handles both the HTML cleanup and Java to Kotlin conversion in one step.")?.play()

            setClipboardContent(
                """
                <pre><code>
                public class Example {
                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                </code></pre>
            """.trimIndent()
            )
            log.debug("HTML-wrapped Java code set to clipboard")

            val editor = find(EditorFixture::class.java, byXpath("//div[@class='EditorComponentImpl']"))
            editor.rightClick(editor.findAllText().firstOrNull()?.point?.location!!)
            Thread.sleep(1000)

            selectAICoderMenu()
            log.debug("Attempting to find and click Fast Paste menu item")
            findAll(
                CommonContainerFixture::class.java,
                byXpath("//div[contains(@class, 'ActionMenuItem') and contains(@text, 'Fast Paste')]")
            ).firstOrNull()?.click()

            log.debug("Fast Paste operation triggered")
            tts("Notice how Fast Paste automatically removes the HTML tags and converts the Java code to idiomatic Kotlin, using features like the main function shorthand and println extension function.")?.play(
                3000
            )
        }

        tts("With Fast Paste, you can quickly convert code snippets from documentation, web pages, or other sources into your target language, saving time and maintaining code quality.")?.play(
            2000
        )
        return@with
    }
}