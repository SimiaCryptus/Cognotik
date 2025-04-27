package com.simiacryptus.aicoder.demotest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.AudioModels
import io.github.bonigarcia.wdm.WebDriverManager
import org.junit.jupiter.api.*
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.edge.EdgeDriver
import org.openqa.selenium.edge.EdgeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxDriverLogLevel
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.Thread.sleep
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DemoTestBase(
    recordingConfig: RecordingConfig = RecordingConfig(),
    splashScreenConfig: SplashScreenConfig = SplashScreenConfig()
) : ScreenRec(
    recordingConfig = recordingConfig,
    splashScreenConfig = splashScreenConfig
) {
    protected lateinit var remoteRobot: RemoteRobot
    protected val robot: java.awt.Robot = java.awt.Robot()
    private var testStartTime: LocalDateTime? = null
    protected lateinit var testProjectDir: Path
    private var driverInitialized = false
    protected val driver: WebDriver by lazy { initializeWebDriver() }
    private fun initializeWebDriver(): RemoteWebDriver {
        try {
            val driver = getChrome()
            log.info("Setting browser zoom level to 150%")
            (driver as JavascriptExecutor).executeScript("document.body.style.zoom='150%'")
            driverInitialized = true
            log.info("WebDriver successfully initialized")
            return driver
        } catch (e: Exception) {
            log.error("Failed to initialize WebDriver: ${e.message}", e)
            throw RuntimeException("WebDriver initialization failed", e)
        }
    }

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        testStartTime = LocalDateTime.now()
        log.info("Starting test at ${testStartTime}")
    }

    @BeforeAll
    fun setup() {
        log.info("Starting test setup")
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
        log.info("RemoteRobot initialized with endpoint http://127.0.0.1:8082")
        UDPClient.startUdpServer()
        log.info("Setting Chrome driver system property")
        System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver")
        try {
            log.info("Initializing test project")
            initializeTestProject()
            log.info("Starting screen recording")
            startScreenRecording()
        } catch (e: Exception) {
            log.error("Failed to start screen recording", e)
            throw e
        }
        log.info("Test setup completed successfully")
    }

    override fun sleepForSplash() {
        val startTime = System.currentTimeMillis()
        if (super.recordingConfig.splashNarration.isNotBlank()) {
            tts(super.recordingConfig.splashNarration)?.play()
        }
        val sleep = super.recordingConfig.splashScreenDelay - (System.currentTimeMillis() - startTime)
        if (sleep > 0) sleep(sleep)
    }

    @AfterAll
    fun tearDown() {

        if (driverInitialized) {
            driver.quit()
        }
        stopScreenRecording()
        UDPClient.clearMessageBuffer()
        cleanupTestProject()
    }

    fun getReceivedMessages() = UDPClient.getReceivedMessages()
    fun clearMessageBuffer() = UDPClient.clearMessageBuffer()

    protected open fun getTemplateProjectPath(): String {
        return "demo_projects/TestProject"
    }

    private fun initializeTestProject() {
        log.info("Creating temporary directory for test project")

        testProjectDir = Files.createTempDirectory("test-project-")

        val templatePath = Paths.get(getTemplateProjectPath())
        log.debug("Template project path: {}", templatePath)
        if (!Files.exists(templatePath)) {
            log.error("Template project not found at path: $templatePath")
            throw IllegalStateException("Template project directory not found: $templatePath")
        }
        log.info("Copying template project to temporary directory")

        templatePath.toFile().copyRecursively(testProjectDir.toFile(), true)
        log.info("Initialized test project in: $testProjectDir")

        val txt = testProjectDir.toString()
        log.debug("Opening project in IDE with path: $txt")
        waitFor(Duration.ofSeconds(20)) {
            try {
                remoteRobot.findAll(CommonContainerFixture::class.java, byXpath("//div[@class='JDialog']"))
                    .firstOrNull()?.apply {
                        click()
                        keyboard {
                            escape()
                            sleep(500)
                        }
                    }

                remoteRobot.findAll(CommonContainerFixture::class.java, byXpath("//div[@class='JDialog']"))
                    .firstOrNull()?.apply {
                        click()
                        keyboard {
                            enter()
                            sleep(500)
                        }
                    }

                remoteRobot.findAll(CommonContainerFixture::class.java, byXpath("//div[@text='Cancel']")).firstOrNull()
                    ?.click()
                remoteRobot.findAll(CommonContainerFixture::class.java, byXpath("//div[@class='JButton']")).apply {
                    if (size == 1) {
                        first().click()
                    }
                }
                log.debug("Attempting to find and click main menu")
                val menu =
                    remoteRobot.find(CommonContainerFixture::class.java, byXpath("//div[@tooltiptext='Main Menu']"))
                menu.click()
                log.debug("Finding 'Open...' menu item")
                val open = remoteRobot.find(
                    CommonContainerFixture::class.java,
                    byXpath("//div[@text='File']//div[@text='Open...']")
                )
                robot.mouseMove(menu.locationOnScreen.x, open.locationOnScreen.y)
                open.click()
                sleep(3000)
                log.debug("Typing project path and pressing enter")
                remoteRobot.keyboard {
                    this.enterText(txt.replace("\\", "\\\\"))
                    sleep(1000)
                    this.enter()
                    sleep(1000)
                    this.enter()
                }
                sleep(1000)
                remoteRobot.findAll(CommonContainerFixture::class.java, byXpath("//div[@text='Trust Project']"))
                    .firstOrNull()?.click()
                log.info("Project opened in IntelliJ IDEA")
                waitAfterProjectOpen()
                true
            } catch (e: Exception) {
                log.error("Failed to open project: ${e.message}", e)
                log.debug("Stack trace: ", e)
                false
            }
        }
        sleep(TimeUnit.SECONDS.toMillis(30))
    }

    protected open fun waitAfterProjectOpen() {
        sleep(15000)
    }

    private fun cleanupTestProject() {
        if (::testProjectDir.isInitialized) {

            log.info("Cleaned up test project directory")
        }
    }

    protected fun JTreeFixture.expandAll(path: Array<String>) {
        (0 until path.size).forEach { i ->
            waitFor(Duration.ofSeconds(10)) {
                try {
                    this.expand(*path.sliceArray(0..i))
                    log.info("Navigated to ${path[i]}")
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    protected fun openProjectView() {
        waitFor(LONG_TIMEOUT) {
            try {
                remoteRobot.find(CommonContainerFixture::class.java, byXpath(PROJECT_TREE_XPATH)).click()
                log.info("Project view opened")
                true
            } catch (e: Exception) {
                log.info("Failed to open project view: ${e.message}")
                false
            }
        }
    }

    protected fun selectAICoderMenu(): CommonContainerFixture {
        lateinit var aiCoderMenu: CommonContainerFixture
        log.debug("Attempting to find and select AI Coder menu")
        waitFor(LONG_TIMEOUT) {
            try {
                aiCoderMenu = remoteRobot.find(CommonContainerFixture::class.java, byXpath(AI_CODER_MENU_XPATH)).apply {
                    log.debug("Found AI Coder menu, waiting for visibility")

                    waitFor(Duration.ofSeconds(2)) { isShowing }
                    click()
                }
                log.info("'AI Coder' menu clicked")
                true
            } catch (e: Exception) {
                log.warn("Failed to find or click 'AI Coder' menu: ${e.message}", e)
                log.debug("Full exception details: ", e)
                false
            }
        }
        return aiCoderMenu
    }

    val voices = arrayOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")
    open val voice: String =
        this::class.java.simpleName.lowercase().let { voices[it.hashCode().absoluteValue % voices.size] }

    fun tts(
        text: String,
        voice: String? = null,
        speed: Double = 1.0
    ): SpokenText? {
        if (!recordingConfig.enableAudio) return null
        val cacheDir = File("./.tts_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        @Suppress("NAME_SHADOWING") val voice = voice ?: this.voice
        val cacheKey = "${text}_${voice}_${speed}".toByteArray()
        val cacheFileName = MessageDigest.getInstance("SHA-256").digest(cacheKey).joinToString("") { "%02x".format(it) }
        val cacheFile = File(cacheDir, "$cacheFileName.wav")
        if (cacheFile.exists()) {
            log.info("Using cached TTS for text: $text")
            return SpokenText(text, cacheFile.readBytes().inputStream(), 0)
        }
        val startTime = System.currentTimeMillis()
        val speechWavBytes = OpenAIClient().createSpeech(
            ApiModel.SpeechRequest(
                input = text,
                model = AudioModels.TTS.modelName,
                voice = voice,
                speed = speed,
                response_format = "wav"
            )
        ) ?: throw RuntimeException("No response")

        cacheFile.writeBytes(speechWavBytes)

        val renderTime = System.currentTimeMillis() - startTime
        log.info("Received speech response in $renderTime ms")
        return SpokenText(text, speechWavBytes.inputStream(), renderTime)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this.javaClass)

        const val PROJECT_TREE_XPATH: String = "//div[@class='ProjectViewTree']"
        const val AI_CODER_MENU_XPATH: String = "//div[contains(@class, 'ActionMenu') and contains(@text, 'AI Coder')]"
        val SHORT_TIMEOUT = Duration.ofSeconds(10)
        val LONG_TIMEOUT = Duration.ofSeconds(300)

        fun clickElement(driver: WebDriver, wait: WebDriverWait, selector: String) = runElement(
            driver, wait, selector, """
                arguments[0].scrollIntoView(true);
                arguments[0].click();
            """.trimIndent()
        )

        fun runElement(
            driver: WebDriver, wait: WebDriverWait, selector: String, js: String
        ): WebElement {
            val startTime = System.currentTimeMillis()
            while (true) {
                try {
                    return wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector))).apply {
                        (driver as JavascriptExecutor).executeScript(js, this)
                    }
                } catch (e: WebDriverException) {
                    if (e is TimeoutException) throw e
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > 30000) throw RuntimeException(
                        "Failed to click $selector: ${e.message} - timed out",
                        e
                    )
                    log.info("Retry failure to click $selector: ${e.message}")
                    sleep(100)
                }
            }
        }

        fun getElement(
            wait: WebDriverWait, selector: String
        ): WebElement {
            val startTime = System.currentTimeMillis()
            while ((System.currentTimeMillis() - startTime) < 30000) {
                try {
                    return wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector))
                    )
                } catch (e: WebDriverException) {
                    if (e is TimeoutException) throw e
                    if ((System.currentTimeMillis() - startTime) > 30000)
                        throw RuntimeException("Failed to click $selector: ${e.message} - timed out", e)
                    log.info("Retry failure to click $selector: ${e.message}")
                    sleep(100)
                }
            }
            throw RuntimeException("Failed to click $selector: timed out")
        }

        fun <T> runElement(
            wait: WebDriverWait, selector: String, fn: (WebElement) -> T
        ): T {
            val startTime = System.currentTimeMillis()
            while (true) {
                try {
                    return wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)))
                        .let { fn(it) }
                } catch (e: WebDriverException) {
                    if (e is TimeoutException) throw e
                    if (System.currentTimeMillis() - startTime > 30000) throw e
                    log.info("Failed to click $selector: ${e.message}")
                    sleep(100)
                }
            }
        }

        const val UDP_PORT = 41390
        fun getChrome(): ChromeDriver {
            log.info("Setting up ChromeDriver using WebDriverManager")
            WebDriverManager.chromedriver().setup()
            log.info("Configuring Chrome options")
            val options = ChromeOptions().apply {
                addArguments(
                    "--start-maximized",
                    "--remote-allow-origins=*",
                    "--disable-dev-shm-usage",
                    "--no-sandbox",
                    "--disable-application-cache",
                    "--kiosk",
                )
            }
            val driver = ChromeDriver(options)
            log.info("Initializing ChromeDriver with configured options")
            return driver
        }

        fun getChromium(): ChromeDriver {
            log.info("Setting up ChromeDriver using WebDriverManager")
            WebDriverManager.chromiumdriver().setup()
            log.info("Configuring Chrome options")
            val options = ChromeOptions().apply {
                addArguments(
                    "--start-maximized",
                    "--remote-allow-origins=*",
                    "--disable-dev-shm-usage",
                    "--no-sandbox",
                    "--disable-application-cache",
                    "--kiosk",
                )
            }
            val driver = ChromeDriver(options)
            log.info("Initializing ChromeDriver with configured options")
            return driver
        }

        fun getEdge(): EdgeDriver {
            log.info("Setting up EdgeDriver using WebDriverManager")
            WebDriverManager.edgedriver().setup()
            log.info("Configuring Edge options")
            val options = EdgeOptions().apply {
                addArguments(
                    "--start-maximized",
                    "--disable-dev-shm-usage",
                    "--no-sandbox",
                    "--disable-application-cache",
                    "--kiosk",
                )
            }
            val driver = EdgeDriver(options)
            log.info("Initializing EdgeDriver with configured options")
            return driver
        }

        fun getFirefox(): RemoteWebDriver {
            log.info("Setting up FirefoxDriver using WebDriverManager")
            WebDriverManager.firefoxdriver().apply {
                setup()
            }
            log.info("Configuring Firefox options")
            val options = FirefoxOptions().apply {
                setLogLevel(FirefoxDriverLogLevel.TRACE)
            }
            val driver = FirefoxDriver(options)
            log.info("Initializing FirefoxDriver with configured options")
            return driver
        }
    }

}