package com.simiacryptus.cognotik.util

import com.intellij.ide.BrowserUtil
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Url
import com.simiacryptus.cognotik.SettingsWidgetFactory.SettingsWidget
import com.simiacryptus.cognotik.config.AppSettingsState
import org.slf4j.LoggerFactory.getLogger
import java.awt.Desktop
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import kotlin.collections.mutableListOf

object BrowseUtil {
    const val BROWSER_SYSTEM_DEFAULT = "System Default"
    const val BROWSER_INTELLIJ_BUILTIN = "Built-in Preview"
    val log = getLogger(BrowseUtil::class.java)
    /**
     * Returns a list of available browser options including system default,
     * built-in preview, and all browsers configured in IntelliJ.
     */
    fun getAvailableBrowsers(): List<String> {
        val browsers = mutableListOf(
            BROWSER_SYSTEM_DEFAULT,
            BROWSER_INTELLIJ_BUILTIN,
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
            val selectedBrowser = AppSettingsState.instance.preferredBrowser
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
                    BROWSER_INTELLIJ_BUILTIN -> {
                        openInBuiltInPreview(uri)
                    }
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

    private fun openInBuiltInPreview(uri: URI) {
        try {
            // Try to use IntelliJ's built-in JCEF browser via a virtual file
            val project = ProjectManager.getInstance().openProjects.firstOrNull()
            if (project != null) {
                ApplicationManager.getApplication().invokeLater {
                    try {
                        val previewFile = com.intellij.ide.browsers.actions.WebPreviewVirtualFile(
                            LightVirtualFile(uri.toString()), createUrl(uri)
                        )
                        FileEditorManager.getInstance(project).openFile(previewFile, true)
                    } catch (e: Exception) {
                        log.warn("Failed to open built-in preview via WebPreviewVirtualFile, trying BrowserUtil", e)
                        try {
                            BrowserUtil.browse(uri)
                        } catch (e2: Exception) {
                            log.warn("BrowserUtil.browse also failed, falling back to Desktop", e2)
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().browse(uri)
                            }
                        }
                    }
                }
            } else {
                log.warn("No open project found for built-in preview, falling back to BrowserUtil")
                BrowserUtil.browse(uri)
            }
        } catch (e: Exception) {
            log.warn("Failed to open built-in preview, falling back to system default", e)
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(uri)
            }
        }
    }

    private fun createUrl(uri: URI): Url {
        return object : Url {
            override fun getPath(): String = uri.path ?: ""
            override fun isInLocalFileSystem(): Boolean = "file" == uri.scheme
            override fun toDecodedForm(): @NlsSafe String = uri.toString()
            override fun toExternalForm(): String = uri.toString()
            override fun getScheme(): String? = uri.scheme
            override fun getAuthority(): String? = uri.authority
            override fun getParameters(): String? = uri.query
            override fun equalsIgnoreParameters(p0: Url?): Boolean {
                if (p0 == null) return false
                return scheme == p0.scheme && authority == p0.authority && path == p0.path
            }

            override fun equalsIgnoreCase(p0: Url?): Boolean {
                if (p0 == null) return false
                return toExternalForm().equals(p0.toExternalForm(), ignoreCase = true)
            }

            override fun trimParameters(): Url {
                val trimmed = URI(uri.scheme, uri.authority, uri.path, null, null)
                return createUrl(trimmed)
            }

            override fun hashCodeCaseInsensitive(): Int = toExternalForm().lowercase().hashCode()
            override fun resolve(p0: String): Url = createUrl(uri.resolve(p0))
            override fun addParameters(p0: Map<String?, String?>): Url {
                val existingQuery = uri.query
                val newParams = p0.entries.joinToString("&") { (k, v) ->
                    "${k ?: ""}=${v ?: ""}"
                }
                val combinedQuery = if (existingQuery.isNullOrEmpty()) newParams else "$existingQuery&$newParams"
                val newUri = URI(uri.scheme, uri.authority, uri.path, combinedQuery, uri.fragment)
                return createUrl(newUri)
            }

            override fun equals(other: Any?): Boolean {
                if (other is Url) return toExternalForm() == other.toExternalForm()
                return false
            }

            override fun hashCode(): Int = toExternalForm().hashCode()
            override fun toString(): String = toExternalForm()
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


}