package cognotik.actions.agent

import cognotik.actions.BaseAction
import cognotik.actions.generate.items
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.Name
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMTextFile
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.getSelectedFiles
import com.simiacryptus.cognotik.util.getSelectedFolder
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import javax.swing.*

class DocumentedMassPatchAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    class SettingsUI {
        @Name("Documentation Files")
        val documentationFiles = CheckBoxList<Path>().apply {
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        }

        @Name("Code Files")
        val codeFiles = CheckBoxList<Path>().apply {
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        }

        @Name("AI Instruction")
        val transformationMessage = JBTextArea(4, 40)

        @Name("Auto Apply")
        val autoApply = JCheckBox("Auto Apply Changes")
    }

    class UserSettings(
        var transformationMessage: String = "Review and update code according to documentation standards",
        var documentationFiles: List<Path> = listOf(),
        var codeFilePaths: List<Path> = listOf(),
        var autoApply: Boolean = false,
    )

    class Settings(
        val settings: UserSettings? = null,
        val project: Project? = null,
    )

    override fun handle(e: AnActionEvent) {
        val project = e.project
        val config = getConfig(project, e)
        if (config == null) return

        val session = Session.newGlobalID()
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
        SessionProxyServer.chats[session] = DocumentedMassPatchServer(
            config = config,
            autoApply = config.settings?.autoApply ?: false,
            processor = AppSettingsState.instance.processor
        )
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Documented Code Patch",
            inputCnt = 1,
            stickyInput = false,
            loadImages = false,
            showMenubar = false
        )

        Thread {
            Thread.sleep(500)
            try {
                val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.uri.resolve("/#$session")
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    private fun getConfig(project: Project?, e: AnActionEvent): Settings? {
        var root = e.getSelectedFolder()?.toNioPath()
        val allFiles: List<Path> = root?.let { Files.walk(it).toList() }
            ?: e.getSelectedFiles().map { it.toNioPath() }
        if (root == null) {
            root = e.project?.basePath?.let { File(it).toPath() }
        }
        val docFiles: Array<Path> = allFiles.filter { it.toString().endsWith(".md") }.toTypedArray()
        val sourceFiles: Array<Path> = allFiles.filter {
            isLLMTextFile(it.toFile()) && !it.toString().endsWith(".md")
        }.toTypedArray()

        val settingsUI = SettingsUI().apply {
            documentationFiles.setItems(docFiles.toMutableList()) { path ->
                root?.relativize(path)?.toString() ?: path.toString()
            }
            codeFiles.setItems(sourceFiles.toMutableList()) { path ->
                root?.relativize(path)?.toString() ?: path.toString()
            }

            docFiles.forEach { path ->
                documentationFiles.setItemSelected(path, true)
            }
            sourceFiles.forEach { path ->
                codeFiles.setItemSelected(path, true)
            }
            autoApply.isSelected = false
        }

        val dialog = ConfigDialog(project, settingsUI, "Documented Mass Patch")
        dialog.show()
        if (!dialog.isOK) return null

        return Settings(dialog.userSettings, project)
    }

    class ConfigDialog(project: Project?, private val settingsUI: SettingsUI, title: String) : DialogWrapper(project) {
        val userSettings = UserSettings()

        init {
            this.title = title
            settingsUI.transformationMessage.text = userSettings.transformationMessage
            settingsUI.autoApply.isSelected = userSettings.autoApply
            init()
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(BorderLayout()).apply {
                val mainPanel = JPanel(BorderLayout()).apply {
                    val docPanel = JPanel(BorderLayout()).apply {
                        add(JLabel("Documentation Files"), BorderLayout.NORTH)
                        add(JBScrollPane(settingsUI.documentationFiles), BorderLayout.CENTER)
                    }

                    val codePanel = JPanel(BorderLayout()).apply {
                        add(JLabel("Code Files"), BorderLayout.NORTH)
                        add(JBScrollPane(settingsUI.codeFiles), BorderLayout.CENTER)
                    }

                    val buttonPanel = JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.X_AXIS)
                        border = JBUI.Borders.empty(10)

                        add(Box.createHorizontalGlue())

                        val moveDownButton = JButton("↓").apply {
                            toolTipText = "Move selected documentation file to code files"
                            addActionListener {
                                moveSelectedItems(settingsUI.documentationFiles, settingsUI.codeFiles)
                            }
                        }
                        add(moveDownButton)

                        add(Box.createHorizontalStrut(10))

                        val moveUpButton = JButton("↑").apply {
                            toolTipText = "Move selected code file to documentation files"
                            addActionListener {
                                moveSelectedItems(settingsUI.codeFiles, settingsUI.documentationFiles)
                            }
                        }
                        add(moveUpButton)

                        add(Box.createHorizontalGlue())
                    }

                    val centerPanel = JPanel(GridBagLayout()).apply {
                        val c = GridBagConstraints()
                        c.gridx = 0
                        c.weightx = 1.0

                        c.gridy = 0
                        c.weighty = 1.0
                        c.fill = GridBagConstraints.BOTH
                        add(docPanel, c)

                        c.gridy = 1
                        c.weighty = 0.0
                        c.fill = GridBagConstraints.HORIZONTAL
                        add(buttonPanel, c)

                        c.gridy = 2
                        c.weighty = 1.0
                        c.fill = GridBagConstraints.BOTH
                        add(codePanel, c)
                    }

                    add(centerPanel, BorderLayout.CENTER)
                    preferredSize = Dimension(500, 600)
                }

                add(mainPanel, BorderLayout.CENTER)
                add(JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JLabel("AI Instruction"))
                    add(settingsUI.transformationMessage)
                    add(Box.createVerticalStrut(10))
                    add(settingsUI.autoApply)
                }, BorderLayout.SOUTH)
            }
        }

        private fun moveSelectedItems(sourceList: CheckBoxList<Path>, targetList: CheckBoxList<Path>) {
            val selectedIndices = sourceList.selectedIndices
            if (selectedIndices.isEmpty()) return

            val selectedItems = selectedIndices.map { sourceList.items[it] }

            // Remove from source list
            val sourceItems = sourceList.items.toMutableList()
            selectedItems.forEach { sourceItems.remove(it) }

            // Add to target list
            val targetItems = targetList.items.toMutableList()
            targetItems.addAll(selectedItems)

            // Update both lists
            val root = userSettings.documentationFiles.firstOrNull()?.parent
                ?: userSettings.codeFilePaths.firstOrNull()?.parent
            sourceList.setItems(sourceItems) { path ->
                root?.relativize(path)?.toString() ?: path.toString()
            }
            targetList.setItems(targetItems) { path ->
                root?.relativize(path)?.toString() ?: path.toString()
            }

            // Select the moved items in target list
            val newIndices = selectedItems.mapNotNull { item ->
                val index = targetItems.indexOf(item)
                if (index >= 0) index else null
            }.toIntArray()
            if (newIndices.isNotEmpty()) {
                targetList.selectedIndices = newIndices
            }
        }

        override fun doOKAction() {
            super.doOKAction()
            userSettings.apply {
                transformationMessage = settingsUI.transformationMessage.text
                documentationFiles = settingsUI.documentationFiles.items
                    .filter { settingsUI.documentationFiles.isItemSelected(it) }
                codeFilePaths = settingsUI.codeFiles.items
                    .filter { settingsUI.codeFiles.isItemSelected(it) }
                autoApply = settingsUI.autoApply.isSelected
            }
        }
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        event.getSelectedFolder() ?: event.getSelectedFiles().let {
            when (it.size) {
                0 -> null
                1 -> null
                else -> it
            }
        } ?: return false
        return true
    }

}