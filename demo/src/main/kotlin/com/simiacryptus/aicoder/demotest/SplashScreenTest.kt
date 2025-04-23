package com.simiacryptus.aicoder.demotest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SplashScreenTest {
    @Test
    fun `test splash screen configuration and rendering`() {
        val config = SplashScreenConfig(
            fontFamily = "Arial",
            titleText = "Test Splash",
        )
        val splashHtml = config.splashPage()
        var splashFrame: JFrame? = null
        try {
            splashFrame = config.toSplashDialog(splashHtml)
            assertNotNull(splashFrame, "Splash frame should not be null")
            val latch = CountDownLatch(1)
            splashFrame.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    latch.countDown()
                }
            })
            splashFrame.isVisible = true
            latch.await(5, TimeUnit.SECONDS)
        } finally {
            splashFrame?.dispose()
        }
    }
}