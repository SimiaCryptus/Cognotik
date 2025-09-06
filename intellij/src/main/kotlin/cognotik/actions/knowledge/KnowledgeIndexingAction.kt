package cognotik.actions.knowledge

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.apps.parse.RawTextParsingModel.Companion.SplitPatterns
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.getSelectedFiles
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.models.EmbeddingModel
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class KnowledgeIndexingAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    data class IndexingSettings(
        var filePaths: List<String> = emptyList(),
        var splitRegex: String = SplitPatterns.DEFAULT,
        var embeddingModel: EmbeddingModel = EmbeddingModel.OllamaNomadic
    )

    class SettingsUI {
        val filePathsArea = JBTextArea(8, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Enter file or directory paths, one per line. You can also drag and drop files here."
        }
        
        val splitRegexField = JBTextArea(2, 40).apply {
            text = SplitPatterns.DEFAULT
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Regular expression pattern for splitting text into segments"
        }

        fun getSettings(): IndexingSettings {
            val paths = filePathsArea.text.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            return IndexingSettings(
                filePaths = paths,
                splitRegex = splitRegexField.text.trim().takeIf { it.isNotEmpty() } 
                    ?: SplitPatterns.DEFAULT
            )
        }
    }

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getSelectedFiles()
        val settingsUI = SettingsUI()

        if (selectedFiles.isNotEmpty()) {
            settingsUI.filePathsArea.text = selectedFiles.joinToString("\n") { 
                it.toFile.absolutePath 
            }
        }

        val dialog = object : DialogWrapper(project, false) {
            init {
                title = "Knowledge Indexing"
                isModal = false
                init()
            }

            override fun createCenterPanel(): JComponent {
                return JPanel(BorderLayout()).apply {
                    preferredSize = Dimension(650, 550)
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
                        
                        val regexPanel = JPanel(BorderLayout()).apply {
                            border = JBUI.Borders.empty(10, 0, 10, 0)
                            val regexHeaderPanel = JPanel(BorderLayout()).apply {
                                add(JLabel("Text splitting regex pattern:"), BorderLayout.WEST)
                                val regexInfoLabel = JLabel("💡 Controls how text is split into segments").apply {
                                    font = font.deriveFont(font.size * 0.9f)
                                    foreground = java.awt.Color.GRAY
                                }
                                add(regexInfoLabel, BorderLayout.EAST)
                            }
                            add(regexHeaderPanel, BorderLayout.NORTH)
                            add(JBScrollPane(settingsUI.splitRegexField), BorderLayout.CENTER)
                        }

                        JPanel(BorderLayout()).apply {
                            border = JBUI.Borders.empty(10, 0, 0, 0)
                            val descText = JLabel("<html><b>Knowledge Indexing</b><br/>" +
                                "This will create searchable embeddings of your files for semantic search and AI assistance.<br/>" +
                                "Supported formats: text files, code, markdown, PDF, HTML<br/>" +
                                "<br/><b>Split Regex:</b> Defines how text is divided into searchable segments. " +
                                "Default splits on newlines and sentence endings.</html>").apply {
                                verticalAlignment = SwingConstants.TOP
                            }
                            add(descText, BorderLayout.NORTH)
                        }

                        add(pathPanel, BorderLayout.CENTER)
                        add(regexPanel, BorderLayout.SOUTH)
                    }

                    add(mainPanel, BorderLayout.CENTER)
                    val descPanel = JPanel(BorderLayout()).apply {
                        border = JBUI.Borders.empty(10, 0, 0, 0)
                        val descText = JLabel("<html><b>Knowledge Indexing</b><br/>" +
                            "This will create searchable embeddings of your files for semantic search and AI assistance.<br/>" +
                            "Supported formats: text files, code, markdown, PDF, HTML<br/>" +
                            "<br/><b>Split Regex:</b> Defines how text is divided into searchable segments. " +
                            "Default splits on newlines and sentence endings.</html>").apply {
                            verticalAlignment = SwingConstants.TOP
                        }
                        add(descText, BorderLayout.NORTH)
                    }
                    add(descPanel, BorderLayout.SOUTH)
                }
            }

            override fun doOKAction() {
                val settings = settingsUI.getSettings()

                if (settings.filePaths.isEmpty()) {
                    Messages.showErrorDialog(
                        project,
                        "Please specify at least one file or directory path to index.\n\n" +
                        "You can:\n" +
                        "• Type file paths manually (one per line)\n" +
                        "• Select files/folders in the project tree first\n" +
                        "• Drag and drop files into the text area",
                        "Validation Error"
                    )
                    return
                }
                
                // Validate regex pattern
                try {
                    Regex(settings.splitRegex)
                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        project,
                        "Invalid regex pattern: ${e.message}\n\n" +
                        "Please enter a valid regular expression for text splitting.",
                        "Invalid Regex"
                    )
                    return
                }
                
                // Validate file paths with progress
                ProgressManager.getInstance().run(object : Task.Modal(project, "Validating Paths", true) {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = false
                        val invalidPaths = mutableListOf<String>()
                        val largePaths = mutableListOf<String>()
                        var totalSizeMB = 0L
                        val validPaths = mutableListOf<String>()
                        
                        settings.filePaths.forEachIndexed { index, path ->
                            indicator.fraction = index.toDouble() / settings.filePaths.size
                            indicator.text = "Checking: ${File(path).name}"
                            
                            val file = File(path)
                            if (!file.exists()) {
                                invalidPaths.add(path)
                            } else if (file.isFile) {
                                val sizeMB = file.length() / (1024 * 1024)
                                totalSizeMB += sizeMB
                                if (sizeMB > 50) {
                                    largePaths.add("$path (${sizeMB}MB)")
                                }
                                validPaths.add(path)
                            } else if (file.isDirectory) {
                                // Calculate directory size
                                val dirSize = file.walkTopDown()
                                    .filter { it.isFile }
                                    .map { it.length() }
                                    .sum() / (1024 * 1024)
                                totalSizeMB += dirSize
                                if (dirSize > 100) {
                                    largePaths.add("$path (${dirSize}MB - directory)")
                                }
                                validPaths.add(path)
                            }
                        }
                        
                        if (invalidPaths.isNotEmpty()) {
                            val result = Messages.showYesNoDialog(
                                project,
                                "The following paths do not exist:\n${invalidPaths.joinToString("\n")}\n\n" +
                                "Do you want to continue with the remaining files?",
                                "Invalid Paths Found",
                                Messages.getWarningIcon()
                            )
                            if (result != Messages.YES) {
                                return
                            }
                            settings.filePaths = validPaths
                        }
                        
                        if (validPaths.isEmpty()) {
                            Messages.showErrorDialog(
                                project,
                                "No valid paths found to index.",
                                "No Valid Files"
                            )
                            return
                        }
                        
                        if (largePaths.isNotEmpty()) {
                            val result = Messages.showYesNoDialog(
                                project,
                                "The following files are quite large:\n${largePaths.joinToString("\n")}\n\n" +
                                "Total size: ${totalSizeMB}MB\n" +
                                "Large files may take significant time to process. Continue?",
                                "Large Files Detected",
                                Messages.getWarningIcon()
                            )
                            if (result != Messages.YES) {
                                return
                            }
                        }
                    }
                })

                super.doOKAction()
                executeIndexing(settings, project)
            }

            private fun executeIndexing(settings: IndexingSettings, project: com.intellij.openapi.project.Project) {
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Starting Knowledge Indexing", false) {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.text = "Initializing indexing session..."
                        try {
                            val session = Session.newGlobalID()
                            SessionProxyServer.metadataStorage.setSessionName(
                                null,
                                session,
                                "Knowledge Indexing @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                            )

                            indicator.text = "Creating indexing server..."
                            SessionProxyServer.chats[session] = KnowledgeIndexingServer(
                                settings = settings,
                                api = api,
                                model = settings.embeddingModel
                            )

                            ApplicationServer.appInfoMap[session] = AppInfoData(
                                applicationName = "Knowledge Indexing",
                                inputCnt = 0,
                                stickyInput = false,
                                loadImages = false,
                                showMenubar = false
                            )

                            indicator.text = "Opening browser..."
                            val server = CognotikAppServer.getServer(project)
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
                            Messages.showErrorDialog(
                                project,
                                "Failed to start indexing: ${e.message}",
                                "Error"
                            )
                        }
                    }
                })
            }
        }

        dialog.show()
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        return super.isEnabled(event) && event.project != null
    }
}