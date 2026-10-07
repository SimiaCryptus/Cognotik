package com.simiacryptus.cognotik.util

import com.intellij.ide.BrowserUtil
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.ide.browsers.actions.WebPreviewVirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Urls
import com.simiacryptus.cognotik.config.AppSettingsState
import org.slf4j.LoggerFactory.getLogger
import java.awt.Desktop
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI

object BrowseUtil {
    const val BROWSER_SYSTEM_DEFAULT = "System Default"
    const val BROWSER_INTELLIJ_BUILTIN = "Built-in Preview"
    val log = getLogger(BrowseUtil::class.java)

    /** UDP port notified with every opened URL (for session synchronization). Null disables. */
    var NOTIFICATION_PORT: Int? = 41390

    /** System default, built-in preview, and all browsers configured in IntelliJ. */
    fun getAvailableBrowsers(): List<String> {
        val browsers = mutableListOf(BROWSER_SYSTEM_DEFAULT, BROWSER_INTELLIJ_BUILTIN)
        try {
            WebBrowserManager.getInstance().browsers.forEach { browsers.add(it.name) }
        } catch (e: Exception) {
            log.warn("Failed to enumerate IntelliJ browsers", e)
        }
        return browsers
    }

    fun browse(uri: URI) {
        log.info("Opening browser to $uri")
        sendUdpMessage(uri.toString())
        if (AppSettingsState.instance.disableAutoOpenUrls) return
        val selectedBrowser = AppSettingsState.instance.preferredBrowser
        try {
            when (selectedBrowser) {
                BROWSER_SYSTEM_DEFAULT -> browseWithDesktop(uri)
                BROWSER_INTELLIJ_BUILTIN -> openInBuiltInPreview(uri)
                else -> {
                    val browser = WebBrowserManager.getInstance().browsers.find { it.name == selectedBrowser }
                    if (browser != null) {
                        BrowserLauncher.instance.browse(uri.toString(), browser)
                    } else {
                        log.warn("Configured browser '$selectedBrowser' not found, falling back to system default")
                        browseWithDesktop(uri)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to open browser '$selectedBrowser', falling back to system default", e)
            browseWithDesktop(uri)
        }
    }

    private fun browseWithDesktop(uri: URI) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
            } else {
                BrowserUtil.browse(uri)
            }
        } catch (e: Exception) {
            log.error("Unable to open $uri", e)
        }
    }

    private fun openInBuiltInPreview(uri: URI) {
        val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
        if (project == null) {
            log.warn("No open project for built-in preview; using system browser")
            browseWithDesktop(uri)
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            try {
                val previewFile = WebPreviewVirtualFile(LightVirtualFile(uri.toString()), Urls.newFromEncoded(uri.toString()))
                FileEditorManager.getInstance(project).openFile(previewFile, true)
            } catch (e: Exception) {
                log.warn("Failed to open built-in preview; using system browser", e)
                ApplicationManager.getApplication().executeOnPooledThread { browseWithDesktop(uri) }
            }
        }
    }

    private fun sendUdpMessage(message: String) {
        val port = NOTIFICATION_PORT ?: return
        try {
            val buf = message.toByteArray(Charsets.UTF_8)
            DatagramSocket().use { it.send(DatagramPacket(buf, buf.size, InetAddress.getLoopbackAddress(), port)) }
        } catch (e: Exception) {
            log.warn("Error sending UDP message", e)
        }
    }
}