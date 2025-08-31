package cognotik.actions.knowledge

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.getSelectedFiles
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.models.EmbeddingModel
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel

class KnowledgeIndexingAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    data class IndexingSettings(
        var filePaths: List<String> = emptyList(),
    )

    class SettingsUI {
        val filePathsArea = JBTextArea(8, 40).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        fun getSettings(): IndexingSettings {
            val paths = filePathsArea.text.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            return IndexingSettings(
                filePaths = paths,
            )
        }
    }

    override fun handle(e: AnActionEvent) {
        val project = e.project
        val selectedFiles = e.getSelectedFiles()
        val settingsUI = SettingsUI()

        if (selectedFiles.isNotEmpty()) {
            settingsUI.filePathsArea.text = selectedFiles.joinToString("\n") { it.toFile.absolutePath }
        }

        val dialog = object : DialogWrapper(project, false) {
            init {
                title = "Knowledge Indexing"
                isModal = false
                init()
            }

            override fun createCenterPanel(): JComponent {
                return JPanel(BorderLayout()).apply {
                    preferredSize = Dimension(600, 400)
                    border = JBUI.Borders.empty(10)

                    val mainPanel = JPanel(BorderLayout()).apply {
                        val pathPanel = JPanel(BorderLayout()).apply {
                            border = JBUI.Borders.empty(0, 0, 10, 0)
                            val headerPanel = JPanel(BorderLayout()).apply {
                                add(JLabel("File paths to index (one per line):"), BorderLayout.WEST)
                                val infoLabel = JLabel("💡 Tip: Drag & drop files or folders here").apply {
                                    font = font.deriveFont(font.size * 0.9f)
                                    foreground = java.awt.Color.GRAY
                                }
                                add(infoLabel, BorderLayout.EAST)
                            }
                            add(headerPanel, BorderLayout.NORTH)
                            add(JBScrollPane(settingsUI.filePathsArea), BorderLayout.CENTER)
                        }

                        val descPanel = JPanel(BorderLayout()).apply {
                            border = JBUI.Borders.empty(10, 0, 0, 0)
                            val descText = JLabel("<html><b>Knowledge Indexing</b><br/>" +
                                "This will create searchable embeddings of your files for semantic search and AI assistance.<br/>" +
                                "Supported formats: text files, code, markdown, PDF, HTML</html>")
                            add(descText, BorderLayout.NORTH)
                        }

                        add(pathPanel, BorderLayout.CENTER)
                        add(descPanel, BorderLayout.SOUTH)
                    }

                    add(mainPanel, BorderLayout.CENTER)
                }
            }

            override fun doOKAction() {
                val settings = settingsUI.getSettings()

                if (settings.filePaths.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        this.contentPane,
                        "Please specify at least one file or directory path to index.\n\n" +
                        "You can:\n" +
                        "• Type file paths manually (one per line)\n" +
                        "• Select files/folders in the project tree first\n" +
                        "• Drag and drop files into the text area",
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                    return
                }
                // Validate file paths and show warning for large files
                val invalidPaths = mutableListOf<String>()
                val largePaths = mutableListOf<String>()
                var totalSizeMB = 0L
                settings.filePaths.forEach { path ->
                    val file = File(path)
                    if (!file.exists()) {
                        invalidPaths.add(path)
                    } else if (file.isFile()) {
                        val sizeMB = file.length() / (1024 * 1024)
                        totalSizeMB += sizeMB
                        if (sizeMB > 50) {
                            largePaths.add("$path (${sizeMB}MB)")
                        }
                    }
                }
                if (invalidPaths.isNotEmpty()) {
                    val result = JOptionPane.showConfirmDialog(
                        this.contentPane,
                        "The following paths do not exist:\n${invalidPaths.joinToString("\n")}\n\n" +
                        "Do you want to continue with the remaining files?",
                        "Invalid Paths Found",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    )
                    if (result != JOptionPane.YES_OPTION) {
                        return
                    }
                }
                if (largePaths.isNotEmpty()) {
                    val result = JOptionPane.showConfirmDialog(
                        this.contentPane,
                        "The following files are quite large:\n${largePaths.joinToString("\n")}\n\n" +
                        "Total size: ${totalSizeMB}MB\n" +
                        "Large files may take significant time to process. Continue?",
                        "Large Files Detected",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    )
                    if (result != JOptionPane.YES_OPTION) {
                        return
                    }
                }

                super.doOKAction()
                executeIndexing(settings)
            }

            private fun executeIndexing(settings: IndexingSettings) {
                try {
                    val session = Session.newGlobalID()
                    SessionProxyServer.metadataStorage.setSessionName(
                        null,
                        session,
                        "Knowledge Indexing @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                    )

                    SessionProxyServer.chats[session] = KnowledgeIndexingServer(
                        settings = settings,
                        api = api,
                        model = EmbeddingModel.OllamaNomadic
                    )

                    ApplicationServer.appInfoMap[session] = AppInfoData(
                        applicationName = "Knowledge Indexing",
                        inputCnt = 0,
                        stickyInput = false,
                        loadImages = false,
                        showMenubar = false
                    )

                    val server = CognotikAppServer.getServer(e.project)
                    CompletableFuture.runAsync({
                        Thread.sleep(500)
                        try {
                            val uri = server.server.uri.resolve("/#$session")
                            log.info("Opening browser to $uri")
                            browse(uri)
                        } catch (e: Throwable) {
                            log.warn("Error opening browser", e)
                        }
                    }, Executors.newSingleThreadExecutor())
                } catch (e: Exception) {
                    log.error("Failed to execute indexing", e)
                    JOptionPane.showMessageDialog(
                        null,
                        "Failed to start indexing: ${e.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        dialog.show()
    }

    private fun collectFilesRecursively(directory: VirtualFile, result: MutableList<String>) {
        directory.children?.forEach { child ->
            if (child.isDirectory) {
                collectFilesRecursively(child, result)
            } else {
                result.add(child.path)
            }
        }
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        return super.isEnabled(event) && event.project != null
    }
}