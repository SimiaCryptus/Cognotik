package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.SettingsWidgetFactory.SettingsWidget
import com.simiacryptus.cognotik.config.AppSettingsState
import com.intellij.ide.BrowserUtil
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowserManager
import java.awt.Desktop
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI

object BrowseUtil {
    const val BROWSER_SYSTEM_DEFAULT = "System Default"
//    const val BROWSER_INTELLIJ_BUILTIN = "Built-in Preview"
    /**
     * Returns a list of available browser options including system default,
     * built-in preview, and all browsers configured in IntelliJ.
     */
    fun getAvailableBrowsers(): List<String> {
        val browsers = mutableListOf(
            BROWSER_SYSTEM_DEFAULT,
//            BROWSER_INTELLIJ_BUILTIN,
        )
        try {
            WebBrowserManager.getInstance().browsers.forEach { browser ->
                browsers.add(browser.name)
            }
        } catch (e: Exception) {
            log.warn("Failed to enumerate IntelliJ browsers", e)
        }
        return browsers
    }


    fun browse(uri: URI) {
        log.info("Opening browser to $uri")
        SettingsWidget().updateSessionsList()
        sendUdpMessage(uri.toString())
        if (!AppSettingsState.instance.disableAutoOpenUrls) {
            val selectedBrowser = AppSettingsState.instance.preferredBrowser ?: BROWSER_SYSTEM_DEFAULT
            log.info("Using browser: $selectedBrowser")
            try {
                when (selectedBrowser) {
                    BROWSER_SYSTEM_DEFAULT -> {
                        if (Desktop.isDesktopSupported()) {
                            val desktop = Desktop.getDesktop()
                            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                                desktop.browse(uri)
                            }
                        }
                    }
//                    BROWSER_INTELLIJ_BUILTIN -> {
//                        BrowserLauncher.instance.browse(uri.toString(), WebBrowserManager.getInstance().firstActiveBrowser) // DOES NOT WORK, FALLBACK TO SYSTEM DEFAULT
//                    }
                    else -> {
                        // Find the named browser in IntelliJ's configured browsers
                        val browser = WebBrowserManager.getInstance().browsers.find { it.name == selectedBrowser }
                        if (browser != null) {
                            BrowserLauncher.instance.browse(uri.toString(), browser)
                        } else {
                            log.warn("Configured browser '$selectedBrowser' not found, falling back to system default")
                            if (Desktop.isDesktopSupported()) {
                                val desktop = Desktop.getDesktop()
                                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                                    desktop.browse(uri)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to open browser '$selectedBrowser', falling back to system default", e)
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(uri)
                    }
                } catch (e2: Exception) {
                    log.error("Failed to open system default browser as well", e2)
                }
            }
        }
    }

    var NOTIFICATION_PORT: Int? = 41390
    private fun sendUdpMessage(message: String) {
        try {
            log.info("Sending UDP message: $message")
            val address = InetAddress.getByName("localhost")
            val buf = message.toByteArray()
            val packet = DatagramPacket(buf, buf.size, address, NOTIFICATION_PORT ?: return)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            log.warn("Error sending UDP message", e)
        }
    }

    val log = LoggerFactory.getLogger(BrowseUtil::class.java)

}