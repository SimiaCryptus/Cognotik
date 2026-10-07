package com.simiacryptus.cognotik.config


import cognotik.actions.plan.PlanConfigDialog.Companion.isVisible
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.ServiceKey

import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.platform.model.EmbeddingModel
import com.simiacryptus.cognotik.platform.model.ImageModel
import com.simiacryptus.cognotik.text.patch.PatchProcessors
import com.simiacryptus.cognotik.util.BrowseUtil
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class AppSettingsComponent : Disposable {
    @Name("Enable Diff Logging")
    val diffLoggingEnabled = JBCheckBox()
    @Suppress("unused")
    @Name("Store Metadata")
    val storeMetadata = JTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
    }

    @Name("Listening Port")
    val listeningPort = JBTextField()

    @Name("Listening Endpoint")
    val listeningEndpoint = JBTextField()

    @Name("Suppress Errors")
    val suppressErrors = JBCheckBox()

    @Name("Use Scratches System Path")
    val useScratchesSystemPath = JBCheckBox()

    @Suppress("unused")
    @Name("Enable API Log")
    val apiLog = JBCheckBox()

    @Suppress("unused")
    val openApiLog = JButton(object : AbstractAction("Open API Log") {
        override fun actionPerformed(e: ActionEvent) {
            AppSettingsState.auxiliaryLog?.let {
                if (it.exists()) {
                    val project = ApplicationManager.getApplication().runReadAction<Project> {
                        ProjectManager.getInstance().openProjects.firstOrNull()
                    }
                    ApplicationManager.getApplication().invokeLater {
                        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(it)
                        val openFileDescriptor = OpenFileDescriptor(project, virtualFile!!, virtualFile.length.toInt())
                        FileEditorManager.getInstance(project!!)
                            .openTextEditor(openFileDescriptor, true)?.document?.setReadOnly(
                                true
                            )
                    }
                }
            }
        }
    })

    @Name("Developer Tools")
    val devActions = JBCheckBox()

    @Suppress("unused")
    @Name("Edit API Requests")
    val editRequests = JBCheckBox()

    @Name("Disable Auto-Open URLs")
    val disableAutoOpenUrls = JBCheckBox()
    @Name("Preferred Browser")
    val preferredBrowser = ComboBox<String>().apply {
        BrowseUtil.getAvailableBrowsers().forEach { addItem(it) }
        isEditable = false
        selectedItem = AppSettingsState.instance.preferredBrowser
    }


    @Name("Shell Command")
    val shellCommand = JBTextField()

    @Name("Show Welcome Screen")
    val showWelcomeScreen = JBCheckBox()

    @Name("Temperature")
    val temperature = JBTextField()

    @Name("APIs")
    val apis = JBTable(DefaultTableModel(arrayOf("Provider", "Name", "Key", "Base URL"), 0)).apply {
        columnModel.getColumn(0).preferredWidth = 100
        columnModel.getColumn(1).preferredWidth = 150
        columnModel.getColumn(2).preferredWidth = 200
        columnModel.getColumn(3).preferredWidth = 200
        val keyColumnIndex = 2
        columnModel.getColumn(keyColumnIndex).cellRenderer = object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text =
                    if (value is String && value.isNotEmpty()) value.map { '*' }.joinToString("") else value?.toString()
                        ?: ""
            }
        }
    }

    @Name("API Management")
    val apiManagementPanel = JPanel(BorderLayout()).apply {
        val scrollPane = JScrollPane(apis)
        scrollPane.preferredSize = Dimension(600, 300)
        add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val addButton = JButton("Add API")
        val removeButton = JButton("Remove")
        val editButton = JButton("Edit")

        removeButton.isEnabled = false
        editButton.isEnabled = false

        addButton.addActionListener {
            val model = apis.model as DefaultTableModel

            // Create add dialog with all fields
            val dialog = JDialog(null as Frame?, "Add API Configuration", true)
            dialog.layout = GridBagLayout()
            val gbc = GridBagConstraints()

            gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
            dialog.add(JLabel("Provider Type:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            val providerCombo = ComboBox(APIProvider.values().map { it.name }.toTypedArray())
            dialog.add(providerCombo, gbc)

            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            dialog.add(JLabel("Name:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            val nameField = JBTextField(30)
            dialog.add(nameField, gbc)

            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            dialog.add(JLabel("API Key:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            val keyField = JBTextField(30)
            dialog.add(keyField, gbc)

            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            dialog.add(JLabel("Base URL:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            val urlField = JBTextField(30)
            dialog.add(urlField, gbc)

            // Auto-populate name and base URL when provider changes
            providerCombo.addActionListener {
                val selectedProvider = APIProvider.valueOf(providerCombo.selectedItem as String)
                urlField.text = selectedProvider.base
                nameField.text = selectedProvider.name
            }

            // Initialize with first provider's defaults
            val initialProvider = APIProvider.values().first()
            nameField.text = initialProvider.name
            urlField.text = initialProvider.base

            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE
            val buttonPanel = JPanel(FlowLayout())
            val okButton = JButton("OK")
            val cancelButton = JButton("Cancel")

            okButton.addActionListener {
                val provider = providerCombo.selectedItem as? String
                val name = nameField.text

                if (provider.isNullOrBlank()) {
                    log.warn("Provider type is required")
                    JOptionPane.showMessageDialog(
                        dialog, "Provider type is required", "Validation Error", JOptionPane.WARNING_MESSAGE
                    )
                    return@addActionListener
                }
                if (name.isBlank()) {
                    log.warn("API name is required")
                    JOptionPane.showMessageDialog(
                        dialog, "API name is required", "Validation Error", JOptionPane.WARNING_MESSAGE
                    )
                    return@addActionListener
                }

                model.addRow(
                    arrayOf(
                        providerCombo.selectedItem, nameField.text, keyField.text, urlField.text
                    )
                )
                dialog.dispose()
            }
            cancelButton.addActionListener { dialog.dispose() }

            buttonPanel.add(okButton)
            buttonPanel.add(cancelButton)
            dialog.add(buttonPanel, gbc)

            dialog.pack()
            dialog.setLocationRelativeTo(this)
            dialog.isVisible = true
        }


        removeButton.addActionListener {
            try {
                val selectedRows = apis.selectedRows
                if (selectedRows.isEmpty()) {
                    log.warn("No API configurations selected for removal")
                    return@addActionListener
                }
                val model = apis.model as DefaultTableModel
                for (i in selectedRows.reversed()) {
                    val provider = model.getValueAt(i, 0) as? String
                    val name = model.getValueAt(i, 1) as? String
                    model.removeRow(i)
                    log.debug("Successfully removed API configuration: $provider - $name")
                }
            } catch (e: Exception) {
                log.error("Unexpected error removing API configuration: ${e.message}", e)
                JOptionPane.showMessageDialog(
                    this, "Failed to remove API configuration: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE
                )
            }
        }

        editButton.addActionListener {
            val selectedRow = apis.selectedRow
            if (selectedRow != -1) {
                val model = apis.model as DefaultTableModel
                val currentProvider = model.getValueAt(selectedRow, 0) as String
                val currentName = model.getValueAt(selectedRow, 1) as String
                val currentKey = model.getValueAt(selectedRow, 2) as String
                val currentUrl = model.getValueAt(selectedRow, 3) as String

                // Create edit dialog
                val dialog = JDialog(null as Frame?, "Edit API Configuration", true)
                dialog.layout = GridBagLayout()
                val gbc = GridBagConstraints()

                gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
                dialog.add(JLabel("Provider Type:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                val providerCombo = ComboBox(APIProvider.values().map { it.name }.toTypedArray())
                providerCombo.selectedItem = currentProvider
                dialog.add(providerCombo, gbc)

                gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                dialog.add(JLabel("Name:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                val nameField = JBTextField(currentName, 30)
                dialog.add(nameField, gbc)
                gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                dialog.add(JLabel("API Key:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                val keyField = JBTextField(currentKey, 30)
                dialog.add(keyField, gbc)

                gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                dialog.add(JLabel("Base URL:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                val urlField = JBTextField(currentUrl, 30)
                dialog.add(urlField, gbc)
                // Auto-populate base URL when provider changes
                providerCombo.addActionListener {
                    val selectedProvider = APIProvider.valueOf(providerCombo.selectedItem as String)
                    if (urlField.text == currentUrl || urlField.text.isBlank()) {
                        urlField.text = selectedProvider.base
                    }
                }

                gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE
                val buttonPanel = JPanel(FlowLayout())
                val okButton = JButton("OK")
                val cancelButton = JButton("Cancel")

                okButton.addActionListener {
                    val provider = providerCombo.selectedItem as? String
                    val name = nameField.text
                    val key = keyField.text
                    val url = urlField.text

                    if (provider.isNullOrBlank()) {
                        log.warn("Provider type is required for editing")
                        JOptionPane.showMessageDialog(
                            dialog, "Provider type is required", "Validation Error", JOptionPane.WARNING_MESSAGE
                        )
                        return@addActionListener
                    }
                    if (name.isBlank()) {
                        log.warn("API name is required for editing")
                        JOptionPane.showMessageDialog(
                            dialog, "API name is required", "Validation Error", JOptionPane.WARNING_MESSAGE
                        )
                        return@addActionListener
                    }

                    model.setValueAt(provider, selectedRow, 0)
                    model.setValueAt(name, selectedRow, 1)
                    model.setValueAt(key, selectedRow, 2)
                    model.setValueAt(url, selectedRow, 3)
                    log.debug("Updated API configuration: $provider - $name")
                    dialog.dispose()
                }
                cancelButton.addActionListener { dialog.dispose() }

                buttonPanel.add(okButton)
                buttonPanel.add(cancelButton)
                dialog.add(buttonPanel, gbc)

                dialog.pack()
                dialog.setLocationRelativeTo(this)
                dialog.isVisible = true
            }
        }

        apis.selectionModel.addListSelectionListener {
            val hasSelection = apis.selectedRow != -1
            removeButton.isEnabled = hasSelection
            editButton.isEnabled = hasSelection
        }

        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(editButton)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    @Name("Editor Actions")
    var usage =
        UsageTable(ServiceKey.USAGE_DB.get())

    init {
        log.debug("Initializing AppSettingsComponent")
        try {
            diffLoggingEnabled.isSelected = AppSettingsState.instance.diffLoggingEnabled
            disableAutoOpenUrls.isSelected = AppSettingsState.instance.disableAutoOpenUrls
        } catch (e: Exception) {
            log.error("Error initializing basic settings: ${e.message}", e)
        }
        try {
            // Populate API table first
            populateApiTable()
        } catch (e: Exception) {
            log.error("Error populating API table: ${e.message}", e)
        }
        log.debug("AppSettingsComponent initialization completed")
    }

    override fun dispose() {
        log.debug("Disposing AppSettingsComponent")
    }

    private fun populateApiTable() {
        try {
            log.debug("Populating API table")
            val model = apis.model as DefaultTableModel
            model.rowCount = 0
          val userSettings = ServiceKey.USER_SETTINGS.get().getUserSettings(CognotikConfig.localUser)
            userSettings.apis.forEach { api ->
                val providerName = api.provider?.name ?: ""
                val name = api.name ?: api.provider?.name ?: ""
                val key = api.key?.decrypt ?: ""
                val url = api.apiBase
                model.addRow(arrayOf(providerName, name, key, url))
            }
            log.debug("Successfully populated API table with ${userSettings.apis.size} entries")
        } catch (e: Exception) {
            log.error("Failed to populate API table: ${e.message}", e)
            JOptionPane.showMessageDialog(
                null, "Failed to load API configurations: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE
            )
        }
    }


    companion object {
        private val log = LoggerFactory.getLogger(AppSettingsComponent::class.java)
    }
}