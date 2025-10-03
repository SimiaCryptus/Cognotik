package cognotik.actions.knowledge

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.embedding.DistanceType
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.getRoot
import com.simiacryptus.cognotik.util.getSelectedFiles
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import javax.swing.*
class EmbeddingSearchAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    data class SearchSettings(
        var positiveQueries: List<String> = emptyList(),
        var negativeQueries: List<String> = emptyList(),
        var distanceType: DistanceType = DistanceType.Cosine,
        var count: Int = 5,
        var minLength: Int = 0,
        var requiredRegexes: List<String> = emptyList()
    )

    class SettingsUI {
        val positiveQueriesArea = JBTextArea(4, 40).apply {
            lineWrap = true
            wrapStyleWord = true
        }
        val negativeQueriesArea = JBTextArea(3, 40).apply {
            lineWrap = true
            wrapStyleWord = true
        }
        val distanceTypeCombo = JComboBox(DistanceType.entries.toTypedArray()).apply {
            selectedItem = DistanceType.Cosine
        }
        val countSpinner = JSpinner(SpinnerNumberModel(5, 1, 100, 1))
        val minLengthSpinner = JSpinner(SpinnerNumberModel(0, 0, 10000, 10))
        val regexArea = JBTextArea(2, 40).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        fun getSettings(): SearchSettings {
            val positiveQueries = positiveQueriesArea.text.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val negativeQueries = negativeQueriesArea.text.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val regexes = regexArea.text.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            return SearchSettings(
                positiveQueries = positiveQueries,
                negativeQueries = negativeQueries,
                distanceType = distanceTypeCombo.selectedItem as DistanceType,
                count = countSpinner.value as Int,
                minLength = minLengthSpinner.value as Int,
                requiredRegexes = regexes
            )
        }
    }

    override fun handle(event: AnActionEvent) {
        val project = event.project
        val settingsUI = SettingsUI()

        val dialog = object : DialogWrapper(project, false) {
            init {
                title = "Embedding Search"
                isModal = false
                init()
            }

            override fun createCenterPanel(): JComponent {
                return JPanel(BorderLayout()).apply {
                    preferredSize = Dimension(600, 500)
                    border = JBUI.Borders.empty(10)

                    val mainPanel = JPanel(BorderLayout()).apply {
                        val topPanel = JPanel(BorderLayout()).apply {
                            val positivePanel = JPanel(BorderLayout()).apply {
                                border = JBUI.Borders.empty(0, 0, 10, 0)
                                add(JLabel("Positive search queries (one per line):"), BorderLayout.NORTH)
                                add(JBScrollPane(settingsUI.positiveQueriesArea), BorderLayout.CENTER)
                            }

                            val negativePanel = JPanel(BorderLayout()).apply {
                                border = JBUI.Borders.empty(10, 0, 10, 0)
                                add(JLabel("Negative search queries (one per line):"), BorderLayout.NORTH)
                                add(JBScrollPane(settingsUI.negativeQueriesArea), BorderLayout.CENTER)
                            }

                            add(positivePanel, BorderLayout.NORTH)
                            add(negativePanel, BorderLayout.CENTER)
                        }

                        val optionsPanel = JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.Y_AXIS)
                            border = JBUI.Borders.empty(10, 0, 0, 0)

                            val distancePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                                add(JLabel("Distance type:"))
                                add(settingsUI.distanceTypeCombo)
                            }

                            val countPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                                add(JLabel("Number of results:"))
                                add(settingsUI.countSpinner)
                            }

                            val minLengthPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                                add(JLabel("Minimum content length:"))
                                add(settingsUI.minLengthSpinner)
                            }

                            val regexPanel = JPanel(BorderLayout()).apply {
                                border = JBUI.Borders.empty(10, 0, 0, 0)
                                add(JLabel("Required regex patterns (one per line):"), BorderLayout.NORTH)
                                add(JBScrollPane(settingsUI.regexArea), BorderLayout.CENTER)
                            }

                            add(distancePanel)
                            add(countPanel)
                            add(minLengthPanel)
                            add(regexPanel)
                        }

                        add(topPanel, BorderLayout.CENTER)
                        add(optionsPanel, BorderLayout.SOUTH)
                    }

                    add(mainPanel, BorderLayout.CENTER)
                }
            }

            override fun doOKAction() {
                val settings = settingsUI.getSettings()

                if (settings.positiveQueries.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        this.contentPane,
                        "Please specify at least one positive search query",
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                    return
                }

                super.doOKAction()
                executeSearch(settings)
            }

            private fun executeSearch(settings: SearchSettings) {
                try {
                    val session = Session.newGlobalID()
                    SessionProxyServer.metadataStorage.setSessionName(
                        null,
                        session,
                        "Embedding Search @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                    )

                    fun expandFiles(vararg virtualFiles: VirtualFile): List<VirtualFile?> = virtualFiles.flatMap { virtualFile ->
                        if (virtualFile.isDirectory) {
                            expandFiles(*virtualFile.children)
                        } else if(virtualFile.name.endsWith(".index.data")) {
                            listOf(virtualFile)
                        } else {
                            emptyList()
                        }
                    }
                    SessionProxyServer.chats[session] = EmbeddingSearchServer(
                        settings = settings,
                    model = AppSettingsState.instance.embeddingModel ?: throw IllegalStateException("No embedding model configured"),
                        files = expandFiles(*event.getSelectedFiles().toTypedArray()),
                        root = File(event.getRoot())
                    )

                    ApplicationServer.appInfoMap[session] = AppInfoData(
                        applicationName = "Embedding Search",
                        inputCnt = 0,
                        stickyInput = false,
                        loadImages = false,
                        showMenubar = false
                    )

                    val server = CognotikAppServer.getServer()
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
                    log.error("Failed to execute search", e)
                    JOptionPane.showMessageDialog(
                        null,
                        "Failed to start search: ${e.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        dialog.show()
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        return super.isEnabled(event) && event.project != null
    }
}