```kotlin
package com.simiacryptus.aicoder.demotest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import io.github.bonigarcia.wdm.WebDriverManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseActionTest : ScreenRec() {
    protected lateinit var remoteRobot: RemoteRobot

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this.javaClass)
        protected const val PROJECT_TREE_XPATH = "//div[@class='ProjectViewTree']"
        protected const val AI_CODER_MENU_XPATH = "//div[contains(@class, 'ActionMenu') and contains(@text, 'AI Coder')]"
    }

    protected lateinit var driver: WebDriver
    protected fun initializeWebDriver() {
        try {
            WebDriverManager.chromedriver().setup()
            val options = ChromeOptions().apply {
                addArguments(
                    "--start-maximized",
                    "--remote-allow-origins=*",
                    "--disable-dev-shm-usage",
                    "--no-sandbox"
                )
            }
            driver = ChromeDriver(options)
            (driver as JavascriptExecutor).executeScript("document.body.style.zoom='150%'")
        } catch (e: Exception) {
            log.error("Failed to initialize WebDriver", e)
            throw RuntimeException("WebDriver initialization failed", e)
        }
    }

    protected fun navigateProjectTree(path: Array<String>, maxAttempts: Int = 3) {
        var attempts = 0
        while (attempts < maxAttempts) {
            try {
                val projectTree = remoteRobot.find(JTreeFixture::class.java, byXpath(PROJECT_TREE_XPATH))
                projectTree.clickPath(*path, fullMatch = false)
                log.info("Successfully navigated to ${path.last()}")
                return
            } catch (e: Exception) {
                attempts++
                log.warn("Navigation attempt $attempts failed", e)
                Thread.sleep(1000)
            }
        }
        throw RuntimeException("Failed to navigate after $maxAttempts attempts")
    }

    protected fun openProjectView() {
        try {
            remoteRobot.find(CommonContainerFixture::class.java, byXpath(PROJECT_TREE_XPATH)).click()
            log.info("Project view opened")
        } catch (e: Exception) {
            log.error("Failed to open project view", e)
            throw e
        }
    }

    protected fun selectAICoderMenu() {
        waitFor(Duration.ofSeconds(10)) {
            try {
                val aiCoderMenu = remoteRobot.find(CommonContainerFixture::class.java, byXpath(AI_CODER_MENU_XPATH))
                aiCoderMenu.click()
                log.info("'AI Coder' menu clicked")
                true
            } catch (e: Exception) {
                log.warn("Failed to find or click 'AI Coder' menu: ${e.message}")
                false
            }
        }
    }


    @BeforeAll
    fun setup() {
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
        TestUtil.startUdpServer()
        System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver")
        try {
            startScreenRecording()
        } catch (e: Exception) {
            log.error("Failed to start screen recording", e)
            throw e;
        }
    }

    @AfterAll
    fun tearDown() {
        TestUtil.stopUdpServer()
        if (::driver.isInitialized) {
            driver.quit()
        }
        stopScreenRecording()
        TestUtil.clearMessageBuffer()
    }

}
```