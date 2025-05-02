package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.DaemonClient.createRandomSessionDir
import com.simiacryptus.cognotik.UpdateManager.currentVersion
import com.simiacryptus.cognotik.UpdateManager.latestVersion
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.net.URI
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class SystemTrayManager(
    private val port: Int,
    private val host: String,
    private val onExit: () -> Unit
) {
    private fun loadSvgImage(): Image? {
        return try {
            val svgStream = javaClass.getResourceAsStream("/toolbarIcon.svg")
            if (svgStream == null) {
                log.warn("Could not find toolbarIcon.svg")
                null
            } else {
                val transcoder = object : ImageTranscoder() {
                    var image: BufferedImage? = null
                    override fun createImage(w: Int, h: Int): BufferedImage {
                        return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                    }

                    override fun writeImage(img: BufferedImage, output: TranscoderOutput?) {
                        this.image = img
                    }
                }
                transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, 32f)
                transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, 32f)
                transcoder.transcode(TranscoderInput(svgStream), TranscoderOutput())
                transcoder.image
            }
        } catch (e: Exception) {
            log.error("Failed to load SVG image: ${e.message}", e)
            null
        }
    }

    fun initialize() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray is not supported")
            return
        }

        SwingUtilities.invokeLater {
            try {
                val tray = SystemTray.getSystemTray()
                val image = loadSvgImage()
                val popup = PopupMenu()

                popup.add(MenuItem("Open in Browser").apply {
                    addActionListener {
                        openInBrowser(host, port)
                    }
                })

                if(latestVersion.greaterThan(currentVersion)) {
                    popup.add(MenuItem("Update to $latestVersion").apply {
                        addActionListener {
                            confirm("Update to ${latestVersion.version}?") {
                                Thread {
                                    try {
                                        UpdateManager.doUpdate()
                                    } catch (e: Exception) {
                                        log.error("Failed to update: ${e.message}", e)
                                        showError("Failed to update")
                                    }
                                }.start()
                            }
                        }
                    })
                }

                popup.add(MenuItem("Exit").apply {
                    addActionListener {
                        confirm("Exit?") {
                            onExit()
                        }
                    }
                })

                trayIcon = TrayIcon(image, "Cognotik ${currentVersion}", popup).apply {
                    isImageAutoSize = true
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1) {
                                openInBrowser(host, port)
                            }
                        }
                    })
                }

                tray.add(trayIcon)
                log.info("System tray icon initialized")
            } catch (e: Exception) {
                log.error("Failed to initialize system tray: ${e.message}", e)
                showError("Failed to initialize system tray")
            }
        }
    }

    fun remove() {
        SwingUtilities.invokeLater {
            try {
                trayIcon?.let { SystemTray.getSystemTray().remove(it) }
                log.info("System tray icon removed")
            } catch (e: Exception) {
                log.error("Failed to remove system tray icon: ${e.message}", e)
                showError("Failed to remove system tray icon")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SystemTrayManager::class.java)
        private var trayIcon: TrayIcon? = null
        private var lastErrorTime: Long = 0
        private val ERROR_COOLDOWN = 5000
        private var lastErrorMessage: String? = null

        fun confirm(message: String, onConfirm: () -> Unit) {
            val result = JOptionPane.showConfirmDialog(
                null,
                message,
                "Cognotik",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            )
            if (result == JOptionPane.YES_OPTION) {
                onConfirm()
            }
        }

        fun openInBrowser(host: String, port: Int) {
            try {
                val sessionDir = createRandomSessionDir()
                val domainName =
                    "http://${if (host == "0.0.0.0") "localhost" else host}:${port}"
                val url = "$domainName/#${sessionDir.urlEncode()}"
                Desktop.getDesktop().browse(URI(url))
                log.info("Opened browser to $url")
            } catch (e: Exception) {
                log.error("Failed to open browser: ${e.message}", e)
                showError("Failed to open browser")
            }
        }

        fun showError(message: String) {
            val now = System.currentTimeMillis()
            if (now - lastErrorTime > ERROR_COOLDOWN && message != lastErrorMessage) {
                trayIcon?.displayMessage(
                    "Error",
                    message,
                    TrayIcon.MessageType.ERROR
                )
                lastErrorTime = now
                lastErrorMessage = message
            } else {
                log.debug("Suppressing error notification due to cooldown: $message")
            }
        }
    }
}