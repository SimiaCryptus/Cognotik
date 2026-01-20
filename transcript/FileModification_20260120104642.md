# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/AppSettingsComponent.kt

```
package com.simiacryptus.cognotik.config

import cognotik.actions.plan.PlanConfigDialog.Companion.isVisible
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
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
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ToolProvider
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
import com.simiacryptus.cognotik.util.LoggerFactory
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class AppSettingsComponent : Disposable {
    @Name("Enable Diff Logging")
    val diffLoggingEnabled = JBCheckBox()

    @Name("AWS Profile")
    val awsProfile = JBTextField().apply {
        toolTipText = "AWS Profile"
        columns = 30
    }

    @Name("AWS Region")
    val awsRegion = JBTextField().apply {
        toolTipText = "AWS Region"
        columns = 30
    }

    @Name("AWS Bucket")
    val awsBucket = JBTextField().apply {
        toolTipText = "AWS Bucket"
        columns = 30
    }

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

    @Name("Model")
    val smartModel = ComboBox<String>()

    @Name("Model")
    val fastModel = ComboBox<String>()

    @Name("Model")
    val imageChatModel = ComboBox<String>()


    @Name("Main Image Model")
    val mainImageModel = ComboBox<String>()

    @Name("Embedding Model")
    val embeddingModel = ComboBox<String>()

    @Name("Patch Processor")
    val patchProcessor = ComboBox<String>()


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

    @Name("Tools")
    val tools = JBTable(DefaultTableModel(arrayOf("Tool", "Path"), 0)).apply {
        columnModel.getColumn(0).preferredWidth = 100
        columnModel.getColumn(1).preferredWidth = 400
    }

    @Name("Tool Management")
    val toolManagementPanel = JPanel(BorderLayout()).apply {
        val scrollPane = JScrollPane(tools)
        scrollPane.preferredSize = Dimension(600, 300)
        add(scrollPane, BorderLayout.CENTER)
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val addButton = JButton("Add Tool")
        val removeButton = JButton("Remove")
        val editButton = JButton("Edit")
        val autoDetectButton = JButton("Auto-Detect")
        removeButton.isEnabled = false
        editButton.isEnabled = false
        addButton.addActionListener {
            val model = tools.model as DefaultTableModel
            val dialog = JDialog(null as Frame?, "Add Tool Configuration", true)
            dialog.layout = GridBagLayout()
            val gbc = GridBagConstraints()
            gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
            dialog.add(JLabel("Tool Type:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            val providerCombo = ComboBox(ToolProvider.values().map { it.name }.toTypedArray())
            dialog.add(providerCombo, gbc)
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            dialog.add(JLabel("Path:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            val pathField = JBTextField(30)
            dialog.add(pathField, gbc)
            val browseButton = JButton("Browse")
            browseButton.addActionListener {
                val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                FileChooser.chooseFile(descriptor, null, null) { file ->
                    pathField.text = file.path
                }
            }
            gbc.gridx = 2; gbc.weightx = 0.0
            dialog.add(browseButton, gbc)
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.NONE
            val buttonPanel = JPanel(FlowLayout())
            val okButton = JButton("OK")
            val cancelButton = JButton("Cancel")
            okButton.addActionListener {
                val provider = providerCombo.selectedItem as? String
                val path = pathField.text
                if (!provider.isNullOrBlank() && path.isNotBlank()) {
                    model.addRow(arrayOf(provider, path))
                    dialog.dispose()
                }
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
            val selectedRows = tools.selectedRows
            if (selectedRows.isNotEmpty()) {
                val model = tools.model as DefaultTableModel
                for (i in selectedRows.reversed()) {
                    model.removeRow(i)
                }
            }
        }
        editButton.addActionListener {
            val selectedRow = tools.selectedRow
            if (selectedRow != -1) {
                val model = tools.model as DefaultTableModel
                val currentProvider = model.getValueAt(selectedRow, 0) as String
                val currentPath = model.getValueAt(selectedRow, 1) as String
                val dialog = JDialog(null as Frame?, "Edit Tool Configuration", true)
                dialog.layout = GridBagLayout()
                val gbc = GridBagConstraints()
                gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
                dialog.add(JLabel("Tool Type:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                val providerCombo = ComboBox(ToolProvider.values().map { it.name }.toTypedArray())
                providerCombo.selectedItem = currentProvider
                dialog.add(providerCombo, gbc)
                gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
                dialog.add(JLabel("Path:"), gbc)
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
                val pathField = JBTextField(currentPath, 30)
                dialog.add(pathField, gbc)
                val browseButton = JButton("Browse")
                browseButton.addActionListener {
                    val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                    FileChooser.chooseFile(descriptor, null, null) { file ->
                        pathField.text = file.path
                    }
                }
                gbc.gridx = 2; gbc.weightx = 0.0
                dialog.add(browseButton, gbc)
                gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.NONE
                val buttonPanel = JPanel(FlowLayout())
                val okButton = JButton("OK")
                val cancelButton = JButton("Cancel")
                okButton.addActionListener {
                    val provider = providerCombo.selectedItem as? String
                    val path = pathField.text
                    if (!provider.isNullOrBlank() && path.isNotBlank()) {
                        model.setValueAt(provider, selectedRow, 0)
                        model.setValueAt(path, selectedRow, 1)
                        dialog.dispose()
                    }
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
        autoDetectButton.addActionListener {
            val model = tools.model as DefaultTableModel
            val detected = ToolProvider.discoverAllToolsFromPath()
            var addedCount = 0
            detected.forEach { tool ->
                var exists = false
                for (i in 0 until model.rowCount) {
                    if (model.getValueAt(i, 0) == tool.provider?.name && model.getValueAt(i, 1) == tool.path) {
                        exists = true
                        break
                    }
                }
                if (!exists) {
                    model.addRow(arrayOf(tool.provider?.name, tool.path))
                    addedCount++
                }
            }
            JOptionPane.showMessageDialog(this, "Detected and added $addedCount tools.")
        }
        tools.selectionModel.addListSelectionListener {
            val hasSelection = tools.selectedRow != -1
            removeButton.isEnabled = hasSelection
            editButton.isEnabled = hasSelection
        }
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(editButton)
        buttonPanel.add(autoDetectButton)
        add(buttonPanel, BorderLayout.SOUTH)
    }


    @Name("Editor Actions")
    var usage = UsageTable(fileApplicationServices(AppSettingsState.Companion.pluginHome).usageManager)

    init {
        log.debug("Initializing AppSettingsComponent")
        try {
            diffLoggingEnabled.isSelected = AppSettingsState.instance.diffLoggingEnabled
            awsProfile.text = AppSettingsState.instance.awsProfile ?: ""
            awsRegion.text = AppSettingsState.instance.awsRegion ?: ""
            awsBucket.text = AppSettingsState.instance.awsBucket ?: ""
            disableAutoOpenUrls.isSelected = AppSettingsState.instance.disableAutoOpenUrls
        } catch (e: Exception) {
            log.error("Error initializing basic settings: ${e.message}", e)
        }
        try {
            // Populate API table first
            populateApiTable()
            populateToolsTable()
        } catch (e: Exception) {
            log.error("Error populating API table: ${e.message}", e)
        }
        val apis =
            fileApplicationServices(AppSettingsState.Companion.pluginHome).userSettingsManager.getUserSettings().apis
        try {

            // Get all available models from APIs with valid keys
            val availableChatModels = try {
                apis.filter { api ->
                    api.key != null
                }.flatMap { api ->
                    try {
                        api.provider?.getChatModels(api.key!!, api.baseUrl)?.filter { model ->
                            isVisible(model)
                        }?.map { it.name to it } ?: emptyList()
                    } catch (e: Exception) {
                        log.warn("Failed to get chat models for provider ${api.provider?.name}: ${e.message}")
                        emptyList()
                    }
                }.toMap().toSortedMap(compareBy { it })
            } catch (e: Exception) {
                log.error("Failed to load available models: ${e.message}", e)
                emptyMap()
            }
            availableChatModels.forEach {
                this.smartModel.addItem(it.value.modelName)
                this.fastModel.addItem(it.value.modelName)
                this.imageChatModel.addItem(it.value.modelName)
            }
        } catch (e: Exception) {
            log.error("Error loading models: ${e.message}", e)
        }
        try {
            val availableImageModels = try {
                apis.filter { api ->
                    api.key != null
                }.flatMap { api ->
                    try {
                        val imageModels: List<ImageModel>? =
                            api.provider?.getImageModels(api.key!!, api.baseUrl)
                        imageModels?.filter { model ->
                            isVisible(model)
                        }?.map { it.modelName to it } ?: emptyList()
                    } catch (e: Exception) {
                        log.warn("Failed to get chat models for provider ${api.provider?.name}: ${e.message}")
                        emptyList()
                    }
                }.toMap().toSortedMap(compareBy { it })
            } catch (e: Exception) {
                log.error("Failed to load available models: ${e.message}", e)
                emptyMap()
            }
            availableImageModels.forEach {
                this.mainImageModel.addItem(it.value.modelName)
            }
        } catch (e: Exception) {
            log.error("Error loading models: ${e.message}", e)
        }
        try {
            val availableEmbeddingModels = try {
                apis.filter { api ->
                    api.key != null
                }.flatMap { api ->
                    try {
                        val embeddingModels: List<EmbeddingModel>? =
                            api.provider?.getEmbeddingModels(api.key!!, api.baseUrl)
                        embeddingModels?.filter { model ->
                            isVisible(model)
                        }?.map { it.modelName to it } ?: emptyList()
                    } catch (e: Exception) {
                        log.warn("Failed to get chat models for provider ${api.provider?.name}: ${e.message}")
                        emptyList()
                    }
                }.toMap().toSortedMap(compareBy { it })
            } catch (e: Exception) {
                log.error("Failed to load available models: ${e.message}", e)
                emptyMap()
            }
            availableEmbeddingModels.forEach {
                this.embeddingModel.addItem(it.value.modelName)
            }
        } catch (e: Exception) {
            log.error("Error loading models: ${e.message}", e)
        }
        try {
            PatchProcessors.values().forEach {
                this.patchProcessor.addItem(it.name)
            }
        } catch (e: Exception) {
            log.error("Error loading image and embedding models: ${e.message}", e)
        }


        val smartModelItems = (0 until smartModel.itemCount).map { smartModel.getItemAt(it) }.filter { modelItem ->
            val chatModel = apis.filter { it.key != null }.firstNotNullOfOrNull { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.find { it.modelName == modelItem }
            }
            if (chatModel == null) {
                false
            } else {
                val visible = isVisible(chatModel)
                visible
            }
        }.filterNotNull().sortedBy { modelItem ->
            val model =
                apis.filter { it.key != null }
                    .find { apiData ->
                        apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)
                            ?.any { it.modelName == modelItem } == true
                    }
                    ?.let { apiData ->
                        apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)
                            ?.find { it.modelName == modelItem }
                    }!!
            "${model.provider?.name} - ${model.modelName}"
        }.toList()
        val fastModelItems = (0 until fastModel.itemCount).map { fastModel.getItemAt(it) }.filter { modelItem ->
            val chatModel = apis.filter { it.key != null }.firstNotNullOfOrNull { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.find { it.modelName == modelItem }
            }
            if (chatModel == null) {
                false
            } else {
                val visible = isVisible(chatModel)
                visible
            }
        }.filterNotNull().sortedBy { modelItem ->
            val model =
                //ChatModel.values().entries.find { it.value.modelName == modelItem }?.value ?: return@sortedBy ""
                apis.filter { it.key != null }
                    .find { apiData ->
                        apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)
                            ?.any { it.modelName == modelItem } == true
                    }
                    ?.let { apiData ->
                        apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)
                            ?.find { it.modelName == modelItem }
                    }
            "${model?.provider?.name} - ${model?.modelName}"
        }.toList()
        val imageChatModelItems =
            (0 until imageChatModel.itemCount).map { imageChatModel.getItemAt(it) }.filter { modelItem ->
                val chatModel = apis.filter { it.key != null }.firstNotNullOfOrNull { apiData ->
                    apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.find { it.modelName == modelItem }
                }
                if (chatModel == null) {
                    false
                } else {
                    val visible = isVisible(chatModel)
                    visible
                }
            }.filterNotNull().sortedBy { modelItem ->
                val model =
                    apis.filter { it.key != null }
                        .find { apiData ->
                            apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)
                                ?.any { it.modelName == modelItem } == true
                        }
                        ?.let { apiData ->
                            apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)
                                ?.find { it.modelName == modelItem }
                        }
                "${model?.provider?.name} - ${model?.modelName}"
            }.toList()
        smartModel.removeAllItems()
        fastModel.removeAllItems()
        imageChatModel.removeAllItems()
        smartModelItems.forEach { smartModel.addItem(it) }
        fastModelItems.forEach { fastModel.addItem(it) }
        imageChatModelItems.forEach { imageChatModel.addItem(it) }
        this.smartModel.isEditable = true
        this.fastModel.isEditable = true
        this.imageChatModel.isEditable = true
        this.smartModel.renderer = getModelRenderer()
        this.fastModel.renderer = getModelRenderer()
        this.imageChatModel.renderer = getModelRenderer()
        this.mainImageModel.isEditable = true
        this.mainImageModel.renderer = getImageModelRenderer()
        this.embeddingModel.isEditable = true
        this.embeddingModel.renderer = getEmbeddingModelRenderer()
        this.patchProcessor.isEditable = false
        this.patchProcessor.renderer = getPatchProcessorRenderer()
        // Set current selections
        AppSettingsState.instance.smartModel?.model?.let { model ->
            this.smartModel.selectedItem = model.modelName
        }
        AppSettingsState.instance.fastModel?.model?.let { model ->
            this.fastModel.selectedItem = model.modelName
        }
        AppSettingsState.instance.imageChatModel?.model?.let { model ->
            this.imageChatModel.selectedItem = model.modelName
        }
        AppSettingsState.instance.embeddingModel?.let { model ->
            this.embeddingModel.selectedItem = model
        }
        AppSettingsState.instance.processor.let { processor ->
            this.patchProcessor.selectedItem = processor.label
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
            val userSettings = fileApplicationServices(
                AppSettingsState.Companion.pluginHome
            ).userSettingsManager.getUserSettings()
            userSettings.apis.forEach { api ->
                val providerName = api.provider?.name ?: ""
                val name = api.name ?: api.provider?.name ?: ""
                val key = api.key?.decrypt ?: ""
                val url = api.baseUrl
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

    private fun populateToolsTable() {
        try {
            log.debug("Populating Tools table")
            val model = tools.model as DefaultTableModel
            model.rowCount = 0
            val userSettings = fileApplicationServices(
                AppSettingsState.Companion.pluginHome
            ).userSettingsManager.getUserSettings()
            userSettings.tools.forEach { tool ->
                val providerName = tool.provider?.name ?: ""
                val path = tool.path ?: ""
                model.addRow(arrayOf(providerName, path))
            }
            log.debug("Successfully populated Tools table with ${userSettings.tools.size} entries")
        } catch (e: Exception) {
            log.error("Failed to populate Tools table: ${e.message}", e)
        }
    }


    private fun getModelRenderer(): ListCellRenderer<in String> = object : SimpleListCellRenderer<String>() {
        override fun customize(
            list: JList<out String>, value: String?, index: Int, selected: Boolean, hasFocus: Boolean
        ) {
            text = value
            if (value != null) {
                val fileApplicationServices = fileApplicationServices(AppSettingsState.Companion.pluginHome)
                val userSettings = fileApplicationServices.userSettingsManager.getUserSettings()
                val model = userSettings.apis
                    .filter { it.key != null }
                    .find { apiData ->
                        apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)
                            ?.any { it.modelName == value } == true
                    }
                    ?.let { apiData ->
                        apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.find { it.modelName == value }
                    }
                text = "${model?.provider?.name} - $value"
            }
        }
    }

    private fun getImageModelRenderer(): ListCellRenderer<in String> = object : SimpleListCellRenderer<String>() {
        override fun customize(
            list: JList<out String>, value: String?, index: Int, selected: Boolean, hasFocus: Boolean
        ) {
            text = value

        }
    }

    private fun getEmbeddingModelRenderer(): ListCellRenderer<in String> = object : SimpleListCellRenderer<String>() {
        override fun customize(
            list: JList<out String>, value: String?, index: Int, selected: Boolean, hasFocus: Boolean
        ) {
            if (value != null) {
                val model = EmbeddingModel.values()[value]
                text = "${model?.provider?.name} - $value"
            } else {
                text = "None"
            }
        }
    }

    private fun getPatchProcessorRenderer(): ListCellRenderer<in String> = object : SimpleListCellRenderer<String>() {
        override fun customize(
            list: JList<out String>, value: String?, index: Int, selected: Boolean, hasFocus: Boolean
        ) {
            if (value != null) {
                try {
                    val processor = PatchProcessors.valueOf(value)
                    text = processor.label
                } catch (e: IllegalArgumentException) {
                    text = value
                }
            } else {
                text = "Fuzzy Mode (Balanced)"
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AppSettingsComponent::class.java)
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/AppSettingsConfigurable.kt

```
package com.simiacryptus.cognotik.config

import com.simiacryptus.cognotik.util.UITools

open class AppSettingsConfigurable : UIAdapter<AppSettingsComponent, AppSettingsState>(
    AppSettingsState.instance
) {
    override fun read(component: AppSettingsComponent, settings: AppSettingsState) {
        UITools.readKotlinUIViaReflection(settings = settings, component = component)
    }

    override fun write(settings: AppSettingsState, component: AppSettingsComponent) {
        UITools.writeKotlinUIViaReflection(settings, component, AppSettingsState::class)
    }

    override fun getPreferredFocusedComponent() = component?.temperature

    override fun newComponent() = AppSettingsComponent()

    override fun newSettings() = AppSettingsState()
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/AppSettingsState.kt

```
package com.simiacryptus.cognotik.config

/**
var transcriptionModel: String = AudioModels.Whisper.modelName
 * Stores and manages plugin configuration settings.
 *
 * This class is responsible for persisting and retrieving the plugin's
 * configuration settings. It uses the IntelliJ Platform's persistence
 * framework to save settings across IDE restarts.
 */
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.log
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.File
import kotlin.random.Random


@State(name = "com.simiacryptus.cognotik.config.AppSettingsState", storages = [Storage("SdkSettingsPlugin.xml")])
data class AppSettingsState(

    /* Audio Settings */
    var selectedMicLine: String? = null,
    var talkTime: Double = 1.0,
    var memorySeconds: Double = 10.0,
    var lookbackSeconds: Double = 5.0,
    var minRMS: Double = 0.5,
    var minIEC61672: Double = 0.5,
    var minSpectralEntropy: Double = 0.5,
    var minimumTalkSeconds: Double = 1.0,
    var rmsLevel: Int = 0,
    var iec61672Level: Int = 0,
    var spectralEntropyLevel: Int = 0,
    var sampleRate: Int = 44100,
    var sampleSize: Int = 16,
    var channels: Int = 1,
    var temperature: Double = 0.1,
    var useScratchesSystemPath: Boolean = false,

    /* Model Settings */
    var smartModel: ApiChatModel? = null,
    var fastModel: ApiChatModel? = null,
    var imageChatModel: ApiChatModel? = null,
    var transcriptionModel: String? = null,
    var imageModel: ApiImageModel? = null,
    /* Embedding Model Settings */
    var embeddingModel: EmbeddingModel? = null,
    var processor: PatchProcessors = PatchProcessors.Fuzzy,

    /* AWS Settings */
    var awsProfile: String? = null,
    var awsRegion: String? = null,
    var awsBucket: String? = null,

    /* System Configuration */
    var analyticsEnabled: Boolean = false,
    var diffLoggingEnabled: Boolean = false,
    var listeningPort: Int = Random.nextInt(3000, 9000),
    var listeningEndpoint: String = "localhost",
    var apiThreads: Int = 4,
    var modalTasks: Boolean = false,
    var suppressErrors: Boolean = false,
    var devActions: Boolean = false,
    var disableAutoOpenUrls: Boolean = false,
    var showWelcomeScreen: Boolean = true,
    var greetedVersion: String = "",
    var shellCommand: String = getDefaultShell(),
    var feedbackRequested: Boolean = false,
    var feedbackOptOut: Boolean = false,

    /* Recent Activity Helpers */
    var savedPlanConfigs: MutableMap<String, String>? = mutableMapOf(),
    val savedCommandConfigsJson: MutableMap<String, String>? = mutableMapOf(),
    val recentCommandsJson: MutableMap<String, String>? = mutableMapOf(),
    val recentArguments: MutableList<String>? = mutableListOf(),
    val recentWorkingDirs: MutableList<String>? = mutableListOf(),
) : PersistentStateComponent<SimpleEnvelope> {

    @get:JsonIgnore
    val smartChatClient: ChatInterface
        get() = smartModel?.instance() ?: throw IllegalStateException("Smart model not configured")

    @get:JsonIgnore
    val fastChatClient: ChatInterface
        get() = fastModel?.instance() ?: throw IllegalStateException("Fast model not configured")

    @get:JsonIgnore
    val imageChatClient: ChatInterface
        get() = imageChatModel?.instance() ?: throw IllegalStateException("Image chat model not configured")


    @get:JsonIgnore
    val imageClient: com.simiacryptus.cognotik.image.ImageClientInterface?
        get() = imageModel?.instance()


    @get:JsonIgnore
    val embeddingClient: com.simiacryptus.cognotik.embedding.Embedder? get() = embeddingModel?.instance()

    @JsonIgnore
    override fun getState() = SimpleEnvelope(toJson(this))

    @JsonIgnore
    fun getRecentCommands(id: String) = recentCommandsJson?.get(id)?.let {
        try {
            fromJson(it, MRUItems::class.java)
        } catch (e: Exception) {
            log.warn("Error loading recent commands: $it", e)
            MRUItems()
        }
    } ?: MRUItems()

    @JsonIgnore
    override fun loadState(state: SimpleEnvelope) {
        state.value ?: return
        val fromJson = try {
            val jsonNode = ObjectMapper().readTree(state.value)
            try {
                fromJson(ObjectMapper().writeValueAsString(jsonNode), AppSettingsState::class.java)
            } catch (e: Exception) {
                log.warn("Error parsing settings: $jsonNode", e)
                AppSettingsState()
            }
        } catch (e: Exception) {
            log.warn("Error loading settings: ${state.value}", e)
            AppSettingsState()
        }

        XmlSerializerUtil.copyBean(fromJson, this)

        /* Copy savedCommandConfigsJson */
//        savedCommandConfigsJson?.clear()
        fromJson.savedCommandConfigsJson?.forEach { (key, value) ->
            savedCommandConfigsJson?.set(key, value)
        }
        /* Copy savedPlanConfigs */
//        savedPlanConfigs?.clear()
        fromJson.savedPlanConfigs?.forEach { (key, value) ->
            savedPlanConfigs?.set(key, value)
        }
        /* Copy recentCommandsJson */
//        recentCommandsJson?.clear()
        fromJson.recentCommandsJson?.forEach { (key, value) ->
            recentCommandsJson?.set(key, value)
        }
        /* Copy recentArguments */
//        recentArguments?.clear()
        fromJson.recentArguments?.forEach { argument ->
            recentArguments?.add(argument)
        }
        /* Copy recentWorkingDirs */
//        recentWorkingDirs?.clear()
        fromJson.recentWorkingDirs?.forEach { workingDir ->
            recentWorkingDirs?.add(workingDir)
        }
        notifySettingsLoaded()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AppSettingsState
        if (selectedMicLine != other.selectedMicLine) return false
        if (talkTime != other.talkTime) return false
        if (memorySeconds != other.memorySeconds) return false
        if (lookbackSeconds != other.lookbackSeconds) return false
        if (minRMS != other.minRMS) return false
        if (minIEC61672 != other.minIEC61672) return false
        if (minSpectralEntropy != other.minSpectralEntropy) return false
        if (minimumTalkSeconds != other.minimumTalkSeconds) return false
        if (rmsLevel != other.rmsLevel) return false
        if (iec61672Level != other.iec61672Level) return false
        if (spectralEntropyLevel != other.spectralEntropyLevel) return false
        if (sampleRate != other.sampleRate) return false
        if (sampleSize != other.sampleSize) return false
        if (channels != other.channels) return false
        if (temperature != other.temperature) return false
        if (useScratchesSystemPath != other.useScratchesSystemPath) return false
        if (smartModel != other.smartModel) return false
        if (fastModel != other.fastModel) return false
        if (imageChatModel != other.imageChatModel) return false
        if (transcriptionModel != other.transcriptionModel) return false
        if (imageModel != other.imageModel) return false
        if (embeddingModel != other.embeddingModel) return false
        if (processor != other.processor) return false
        if (awsProfile != other.awsProfile) return false
        if (awsRegion != other.awsRegion) return false
        if (awsBucket != other.awsBucket) return false
        if (analyticsEnabled != other.analyticsEnabled) return false
        if (diffLoggingEnabled != other.diffLoggingEnabled) return false
        if (listeningPort != other.listeningPort) return false
        if (listeningEndpoint != other.listeningEndpoint) return false
        if (apiThreads != other.apiThreads) return false
        if (modalTasks != other.modalTasks) return false
        if (suppressErrors != other.suppressErrors) return false
        if (devActions != other.devActions) return false
        if (disableAutoOpenUrls != other.disableAutoOpenUrls) return false
        if (showWelcomeScreen != other.showWelcomeScreen) return false
        if (greetedVersion != other.greetedVersion) return false
        if (shellCommand != other.shellCommand) return false
        if (savedPlanConfigs != other.savedPlanConfigs) return false
        if (savedCommandConfigsJson != other.savedCommandConfigsJson) return false
        if (recentCommandsJson != other.recentCommandsJson) return false
        if (recentArguments != other.recentArguments) return false
        if (recentWorkingDirs != other.recentWorkingDirs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = selectedMicLine?.hashCode() ?: 0
        result = 31 * result + talkTime.hashCode()
        result = 31 * result + memorySeconds.hashCode()
        result = 31 * result + lookbackSeconds.hashCode()
        result = 31 * result + minRMS.hashCode()
        result = 31 * result + minIEC61672.hashCode()
        result = 31 * result + minSpectralEntropy.hashCode()
        result = 31 * result + minimumTalkSeconds.hashCode()
        result = 31 * result + rmsLevel
        result = 31 * result + iec61672Level
        result = 31 * result + spectralEntropyLevel
        result = 31 * result + sampleRate
        result = 31 * result + sampleSize
        result = 31 * result + channels
        result = 31 * result + temperature.hashCode()
        result = 31 * result + useScratchesSystemPath.hashCode()
        result = 31 * result + smartModel.hashCode()
        result = 31 * result + fastModel.hashCode()
        result = 31 * result + (imageChatModel?.hashCode() ?: 0)
        result = 31 * result + (transcriptionModel?.hashCode() ?: 0)
        result = 31 * result + (imageModel?.hashCode() ?: 0)
        result = 31 * result + (embeddingModel?.hashCode() ?: 0)
        result = 31 * result + processor.hashCode()
        result = 31 * result + (awsProfile?.hashCode() ?: 0)
        result = 31 * result + (awsRegion?.hashCode() ?: 0)
        result = 31 * result + (awsBucket?.hashCode() ?: 0)
        result = 31 * result + analyticsEnabled.hashCode()
        result = 31 * result + diffLoggingEnabled.hashCode()
        result = 31 * result + listeningPort
        result = 31 * result + listeningEndpoint.hashCode()
        result = 31 * result + apiThreads
        result = 31 * result + modalTasks.hashCode()
        result = 31 * result + suppressErrors.hashCode()
        result = 31 * result + devActions.hashCode()
        result = 31 * result + disableAutoOpenUrls.hashCode()
        result = 31 * result + showWelcomeScreen.hashCode()
        result = 31 * result + greetedVersion.hashCode()
        result = 31 * result + shellCommand.hashCode()
        result = 31 * result + (savedPlanConfigs?.hashCode() ?: 0)
        result = 31 * result + (savedCommandConfigsJson?.hashCode() ?: 0)
        result = 31 * result + (recentCommandsJson?.hashCode() ?: 0)
        result = 31 * result + (recentArguments?.hashCode() ?: 0)
        result = 31 * result + (recentWorkingDirs?.hashCode() ?: 0)
        return result
    }

    companion object {
        var lastEvent: AnActionEvent? = null
        val log = LoggerFactory.getLogger(AppSettingsState::class.java)
        var auxiliaryLog: File? = null
        const val WELCOME_VERSION: String = "2.0.8"

        @JvmStatic
        val instance: AppSettingsState by lazy {
            require(APIProvider.values().isNotEmpty()) { "No API providers registered" }
            ApplicationManager.getApplication()?.getService(AppSettingsState::class.java) ?: AppSettingsState()
        }

        fun getDefaultShell() = if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"

        @JsonIgnore
        var onSettingsLoadedListeners = mutableListOf<() -> Unit>()
        fun notifySettingsLoaded() {
            onSettingsLoadedListeners.forEach { it() }
        }

        val currentSession = Session.Companion.newGlobalID()
        val workPool = ApplicationServices.threadPoolManager.getPool(currentSession, UserSettingsManager.defaultUser)
        val pluginHome: File by lazy {
            run {
                var logPath: String? = null
                //if (logPath == null) logPath = System.getProperty("java.io.tmpdir")
                if (logPath == null) logPath = System.getProperty("user.home")
                if (logPath == null) logPath = System.getProperty("idea.plugins.path")
                File(logPath, ".cognotik")
            }
        }
    }

}


fun ApiChatModel.instance(): ChatInterface? {
    val usageManager = ApplicationServices.fileApplicationServices(AppSettingsState.Companion.pluginHome).usageManager
    val model = model
    if (model == null) {
        log.warn("Model not configured for ${provider?.provider?.name}")
        return null
    }
    return model.instance(
        key = provider?.key ?: throw IllegalArgumentException("API key is not set"),
        base = provider?.provider?.base
            ?: throw IllegalArgumentException("API base for ${provider?.provider?.name} is not set"),
        workPool = AppSettingsState.workPool,
        temperature = AppSettingsState.instance.temperature,
        scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(
            AppSettingsState.currentSession,
            UserSettingsManager.defaultUser
        ),
        onUsage = { model, usage ->
            usageManager.incrementUsage(
                AppSettingsState.currentSession,
                UserSettingsManager.defaultUser,
                model,
                usage
            )
        },
    )
}

data class ApiImageModel(
    val model: ImageModel,
    val provider: ApiData?
)

fun ApiImageModel.instance(): com.simiacryptus.cognotik.image.ImageClientInterface? {
    val model = model
    if (model == null) {
        log.warn("Model not configured for ${provider?.provider?.name}")
        return null
    }
    return provider?.provider?.getImageClient(
        key = provider.key ?: throw IllegalArgumentException("API key is not set"),
        base = provider.baseUrl ?: provider.provider?.base
        ?: throw IllegalArgumentException("API base for ${provider.provider?.name} is not set"),
        workPool = AppSettingsState.workPool,
        scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(
            AppSettingsState.currentSession,
            UserSettingsManager.defaultUser
        ),
    )
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/MRUItems.kt

```
package com.simiacryptus.cognotik.config

import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.Serializable
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.min

class MRUItems : Serializable {

    companion object {
        const val DEFAULT_LIMIT = 10
    }

    data class HistoryItem(val instruction: String, var usageCount: Int, var lastUsed: Instant) : Serializable

    val history: MutableList<HistoryItem> = CopyOnWriteArrayList()

    private val lock = ReentrantReadWriteLock()

    var historyLimit = DEFAULT_LIMIT
        set(value) {
            require(value > 0) { "History limit must be positive" }
            lock.write {
                field = value
                trimHistories()
            }
        }

    override fun equals(other: Any?): Boolean {
        return other is MRUItems && history == other.history
    }

    override fun hashCode(): Int {
        return history.hashCode()
    }

    fun addInstructionToHistory(instruction: CharSequence) {
        lock.write {
            val instructionStr = instruction.toString()
            val existingItem = history.find { it.instruction == instructionStr }
            if (existingItem != null) {
                existingItem.usageCount++
                existingItem.lastUsed = Instant.now()
                history.remove(existingItem)
                history.add(0, existingItem)
            } else {
                history.add(0, HistoryItem(instructionStr, 1, Instant.now()))
            }
            trimHistories()
        }
    }

    @JsonIgnore
    fun getMostUsed(limit: Int = DEFAULT_LIMIT): List<String> {
        return lock.read {
            history
                .sortedByDescending { it.usageCount }
                .take(min(limit, historyLimit))
                .map { it.instruction }
        }
    }

    @JsonIgnore
    fun getMostRecent(limit: Int = DEFAULT_LIMIT): List<String> {
        return lock.read {
            history.take(min(limit, historyLimit)).map { it.instruction }
        }
    }

    fun clear() {
        lock.write {
            history.clear()
        }
    }

    fun size(): Int = lock.read { history.size }

    fun isEmpty(): Boolean = lock.read { history.isEmpty() }

    fun remove(item: String) {
        lock.write {
            history.removeIf { it.instruction == item }
        }
    }

    private fun trimHistories() {
        lock.write {
            if (history.size > historyLimit) {
                history.subList(historyLimit, history.size).clear()
            }
        }
    }

    fun contains(item: String): Boolean {
        return lock.read {
            history.any { it.instruction == item }
        }
    }

    override fun toString(): String {
        return lock.read {
            "MRUItems(mostUsed=${getMostUsed(5)}, mostRecent=${getMostRecent(5)}, size=${history.size})"
        }
    }

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/Name.kt

```
﻿package com.simiacryptus.cognotik.config

@Retention(AnnotationRetention.RUNTIME)
annotation class Name(val value: String)


```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/SimpleEnvelope.kt

```
package com.simiacryptus.cognotik.config

class SimpleEnvelope(var value: String? = null)
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/StaticAppSettingsConfigurable.kt

```
package com.simiacryptus.cognotik.config

import com.intellij.util.xmlb.XmlSerializerUtil
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ToolData
import com.simiacryptus.cognotik.models.ToolProvider
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.encrypt
import com.simiacryptus.cognotik.util.toJson
import java.awt.*
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableModel
import javax.swing.table.DefaultTableCellRenderer

class StaticAppSettingsConfigurable : AppSettingsConfigurable() {
    override fun apply() {
        super.apply()
        AppSettingsState.auxiliaryLog = null
        AppSettingsState.notifySettingsLoaded()
    }

    override fun build(component: AppSettingsComponent): JComponent {
        val tabbedPane = com.intellij.ui.components.JBTabbedPane()
        try {
            tabbedPane.addTab("Basic Settings", JPanel(BorderLayout()).apply {
                add(JPanel(BorderLayout()).apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Smart Model:"))
                        add(component.smartModel)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Fast Model:"))
                        add(component.fastModel)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Image Chat Model:"))
                        add(component.imageChatModel)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Image Model:"))
                        add(component.mainImageModel)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Embedding Model:"))
                        add(component.embeddingModel)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Patch Processor:"))
                        add(component.patchProcessor)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Temperature:"))
                        add(component.temperature)
                    })
                })
            })
        } catch (e: Exception) {
            log.warn("Error building Basic Settings", e)
        }

        try {
            tabbedPane.addTab("Keys", JPanel(BorderLayout()).apply {
                add(JPanel(BorderLayout()).apply {
                    add(JPanel(BorderLayout()).apply {
                        add(JLabel("API Configurations:"), BorderLayout.NORTH)
                        add(component.apiManagementPanel, BorderLayout.CENTER)
                    }, BorderLayout.CENTER)
                })
            })
        } catch (e: Exception) {
            log.warn("Error building Configuration", e)
        }
        try {
            tabbedPane.addTab("Tools", JPanel(BorderLayout()).apply {
                add(JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JPanel(BorderLayout()).apply {
                        add(JLabel("Configured Tools:"), BorderLayout.NORTH)
                        add(component.toolManagementPanel, BorderLayout.CENTER)
                    })
                }, BorderLayout.NORTH)
            })
        } catch (e: Exception) {
            log.warn("Error building Tools Settings", e)
        }


        tabbedPane.addTab("Advanced Settings", JPanel(BorderLayout()).apply {
            try {
                add(JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Developer Tools:"))
                        add(component.devActions)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Disable Auto-Open URLs:"))
                        add(component.disableAutoOpenUrls)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Enable Diff Logging:"))
                        add(component.diffLoggingEnabled)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Suppress Errors:"))
                        add(component.suppressErrors)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Use System Path for Scratches (instead of Config Path):"))
                        add(component.useScratchesSystemPath)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Show Welcome Screen:"))
                        add(component.showWelcomeScreen)
                    }, BorderLayout.NORTH)
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Server Port:"))
                        add(component.listeningPort)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Server Endpoint:"))
                        add(component.listeningEndpoint)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Shell Command:"))
                        add(component.shellCommand)
                    })
                }, BorderLayout.NORTH)

                add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                    add(JLabel("Configuration:"))
                    add(JButton("Export Config").apply {
                        addActionListener {
                            try {
                                showExportConfigDialog()
                            } catch (e: Exception) {
                                log.error("Failed to show export config dialog", e)
                                JOptionPane.showMessageDialog(
                                    null,
                                    "Failed to export configuration: ${e.message}",
                                    "Export Error",
                                    JOptionPane.ERROR_MESSAGE
                                )
                            }
                        }
                    })
                    add(JButton("Import Config").apply {
                        addActionListener {
                            try {
                                showImportConfigDialog()
                            } catch (e: Exception) {
                                log.error("Failed to show import config dialog", e)
                                JOptionPane.showMessageDialog(
                                    null,
                                    "Failed to import configuration: ${e.message}",
                                    "Import Error",
                                    JOptionPane.ERROR_MESSAGE
                                )
                            }
                        }
                    })
                })
            } catch (e: Exception) {
                log.warn("Error building Developer Tools", e)
            }

            try {
                tabbedPane.addTab("AWS", JPanel(BorderLayout()).apply {
                    add(JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                            add(JLabel("AWS Profile:"))
                            add(component.awsProfile)
                        })
                        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                            add(JLabel("AWS Region:"))
                            add(component.awsRegion)
                        })
                        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                            add(JLabel("AWS Bucket:"))
                            add(component.awsBucket)
                        })
                    }, BorderLayout.NORTH)
                })
            } catch (e: Exception) {
                log.warn("Error building AWS Settings", e)
            }
        })

        return tabbedPane
    }

    private fun showExportConfigDialog() {
        log.debug("Opening export configuration dialog")
        val dialog = JDialog(null as Frame?, "Export Configuration", true)
        dialog.layout = BorderLayout()

        val userSettings =
            ApplicationServices.fileApplicationServices(AppSettingsState.pluginHome).userSettingsManager.getUserSettings()
        val fullConfig = try {
            val encryptedSettings = AppSettingsState.instance.copy()
            // Export UserSettings with encrypted keys
            log.debug("Encrypting ${userSettings.apis.size} API configurations")

            val configJson = JsonUtil.toJson(encryptedSettings)
            val userSettingsJson = JsonUtil.toJson(userSettings)
            """
                {
                    "appSettings": $configJson,
                    "userSettings": $userSettingsJson
                }
            """.trimIndent()
        } catch (e: Exception) {
            log.error("Failed to prepare configuration for export", e)
            JOptionPane.showMessageDialog(
                dialog, "Failed to prepare configuration: ${e.message}", "Export Error", JOptionPane.ERROR_MESSAGE
            )
            dialog.dispose()
            return
        }

        val textArea = JTextArea(fullConfig).apply {
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
        dialog.add(JScrollPane(textArea), BorderLayout.CENTER)
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val copyButton = JButton("Copy to Clipboard")
        copyButton.addActionListener {
            textArea.selectAll()
            textArea.copy()
            JOptionPane.showMessageDialog(
                dialog, "Configuration copied to clipboard", "Success", JOptionPane.INFORMATION_MESSAGE
            )
        }
        val saveButton = JButton("Save to File")
        saveButton.addActionListener {
            val fileChooser = JFileChooser().apply {
                dialogTitle = "Save Configuration"
                fileFilter = FileNameExtensionFilter("JSON Files", "json")
            }
            if (fileChooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                val file = fileChooser.selectedFile
                val filePath = if (!file.name.lowercase().endsWith(".json")) {
                    File("${file.absolutePath}.json")
                } else {
                    file
                }
                try {
                    FileWriter(filePath).use { writer ->
                        writer.write(textArea.text)
                    }
                    log.info("Configuration exported successfully to: ${filePath.absolutePath}")
                    JOptionPane.showMessageDialog(
                        dialog,
                        "Configuration saved to ${filePath.absolutePath}",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (e: Exception) {
                    log.error("Failed to save configuration to file: ${filePath.absolutePath}", e)
                    JOptionPane.showMessageDialog(
                        dialog, "Error saving configuration: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
        val closeButton = JButton("Close")
        closeButton.addActionListener {
            dialog.dispose()
        }
        buttonPanel.add(copyButton)
        buttonPanel.add(saveButton)
        buttonPanel.add(closeButton)
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        dialog.preferredSize = Dimension(800, 600)
        dialog.pack()
        dialog.setLocationRelativeTo(null)
        dialog.isVisible = true
    }

    private fun showImportConfigDialog() {
        log.debug("Opening import configuration dialog")
        val dialog = JDialog(null as Frame?, "Import Configuration", true)
        dialog.layout = BorderLayout()
        val textArea = JTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
        dialog.add(JScrollPane(textArea), BorderLayout.CENTER)
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val pasteButton = JButton("Paste from Clipboard")
        pasteButton.addActionListener {
            textArea.paste()
        }
        val loadButton = JButton("Load from File")
        loadButton.addActionListener {
            val fileChooser = JFileChooser().apply {
                dialogTitle = "Load Configuration"
                fileFilter = FileNameExtensionFilter("JSON Files", "json")
            }
            if (fileChooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try {
                    val file = fileChooser.selectedFile
                    log.debug("Loading configuration from file: ${file.absolutePath}")
                    FileReader(file).use { reader ->
                        textArea.text = reader.readText()
                    }
                    log.info("Configuration loaded successfully from: ${file.absolutePath}")
                } catch (e: Exception) {
                    log.error("Failed to load configuration from file", e)
                    JOptionPane.showMessageDialog(
                        dialog, "Error loading configuration: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
        val applyButton = JButton("Apply Configuration")
        applyButton.addActionListener {
            try {
                val confirm = JOptionPane.showConfirmDialog(
                    dialog,
                    "Are you sure you want to apply this configuration? This will overwrite your current settings.",
                    "Confirm Import",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                )

                if (confirm == JOptionPane.YES_OPTION) {
                    log.info("User confirmed configuration import")
                    import(textArea.text)
                    write(AppSettingsState.instance, component!!)
                    log.info("Configuration imported and applied successfully")
                    JOptionPane.showMessageDialog(
                        dialog,
                        "Configuration applied successfully. Please restart the IDE for all changes to take effect.",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    dialog.dispose()
                } else {
                    log.debug("User cancelled configuration import")
                }
            } catch (e: Exception) {
                log.error("Failed to apply imported configuration", e)
                JOptionPane.showMessageDialog(
                    dialog, "Error applying configuration: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE
                )
            }
        }
        val closeButton = JButton("Cancel")
        closeButton.addActionListener {
            dialog.dispose()
        }
        buttonPanel.add(pasteButton)
        buttonPanel.add(loadButton)
        buttonPanel.add(applyButton)
        buttonPanel.add(closeButton)
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        dialog.preferredSize = Dimension(800, 600)
        dialog.pack()
        dialog.setLocationRelativeTo(null)
        dialog.isVisible = true
    }

    fun import(text: String) {
        log.debug("Importing configuration, text length: ${text.length}")
        try {
            // Try to parse as new format with both appSettings and userSettings
            val fullConfig: Map<String, Any> = fromJson(text, Map::class.java)
            if (fullConfig.containsKey("appSettings") && fullConfig.containsKey("userSettings")) {
                log.info("Importing new format configuration with appSettings and userSettings")
                val appSettingsJson = JsonUtil.toJson(fullConfig["appSettings"])
                val userSettingsJson = JsonUtil.toJson(fullConfig["userSettings"])
                val importedSettings = fromJson<AppSettingsState>(appSettingsJson, AppSettingsState::class.java)
                XmlSerializerUtil.copyBean(importedSettings, AppSettingsState.instance)

                val importedUserSettings = fromJson<UserSettings>(
                    userSettingsJson, UserSettings::class.java
                )
                log.debug("Decrypting ${importedUserSettings.apis.size} API configurations")
                ApplicationServices.fileApplicationServices(AppSettingsState.pluginHome).userSettingsManager.updateUserSettings(
                    UserSettingsManager.defaultUser, importedUserSettings
                )
                log.info("Successfully imported configuration with ${importedUserSettings.apis.size} API configurations")
            } else {
                // Fall back to old format
                log.info("Importing legacy format configuration")
                val importedSettings = fromJson<AppSettingsState>(text, AppSettingsState::class.java)
                XmlSerializerUtil.copyBean(importedSettings, AppSettingsState.instance)
                log.info("Successfully imported legacy configuration")
            }
        } catch (e: Exception) {
            log.error("Failed to import configuration", e)
            throw e
        }
    }

    override fun write(settings: AppSettingsState, component: AppSettingsComponent) {
        log.debug("Writing settings to UI components")
        try {
            component.diffLoggingEnabled.isSelected = settings.diffLoggingEnabled
            component.awsProfile.text = settings.awsProfile ?: ""
            component.awsRegion.text = settings.awsRegion ?: ""
            component.awsBucket.text = settings.awsBucket ?: ""
            component.listeningPort.text = settings.listeningPort.toString()
            component.listeningEndpoint.text = settings.listeningEndpoint
            component.suppressErrors.isSelected = settings.suppressErrors
            component.disableAutoOpenUrls.isSelected = settings.disableAutoOpenUrls
            settings.fastModel?.model?.let { component.fastModel.selectedItem = it.modelName }
            settings.smartModel?.model?.let { component.smartModel.selectedItem = it.modelName }
            settings.imageChatModel?.model?.let { component.imageChatModel.selectedItem = it.modelName }
            settings.imageModel?.model?.let { component.mainImageModel.selectedItem = it.modelName }
            component.devActions.isSelected = settings.devActions
            component.temperature.text = settings.temperature.toString()
            component.embeddingModel.selectedItem = settings.embeddingModel
            component.shellCommand.text = settings.shellCommand
            component.showWelcomeScreen.isSelected = settings.showWelcomeScreen
            component.patchProcessor.selectedItem = settings.processor.label
            // Refresh API table with current user settings
            val tableModel = component.apis.model as DefaultTableModel
            tableModel.rowCount = 0
            val userSettings = ApplicationServices.fileApplicationServices(
                AppSettingsState.pluginHome
            ).userSettingsManager.getUserSettings()
            userSettings.apis.forEach { api ->
                val providerName = api.provider?.name ?: ""
                val name = api.name ?: api.provider?.name ?: ""
                val key = api.key?.decrypt ?: ""
                val url = api.baseUrl
                tableModel.addRow(arrayOf(providerName, name, key, url))
            }
            
            log.debug("Successfully wrote settings to UI components")
        } catch (e: Exception) {
            log.error("Failed to write settings to UI components", e)
            throw IllegalStateException("Failed to update UI with settings", e)
        }
    }

    override fun read(component: AppSettingsComponent, settings: AppSettingsState) {
        log.debug("Reading settings from UI components")
        try {
            val userSettings = ApplicationServices.fileApplicationServices(
                AppSettingsState.pluginHome
            ).userSettingsManager.getUserSettings()
            log.debug("Current user has ${userSettings.apis.size} API configurations")

            val fastModelName = component.fastModel.selectedItem as String?
            val smartModelName = component.smartModel.selectedItem as String?
            val imageChatModelName = component.imageChatModel.selectedItem as String?
            val imageModelName = component.mainImageModel.selectedItem as String?
            log.debug("Selected models - fast: $fastModelName, smart: $smartModelName, imageChat: $imageChatModelName")

            val chatModels = userSettings.apis.flatMap { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl) ?: emptyList()
            }
            val imageModels = userSettings.apis.flatMap { apiData ->
                apiData.provider?.getImageModels(apiData.key!!, apiData.baseUrl) ?: emptyList()
            }
            val fastChatModel =
                chatModels.find { model -> model.modelName == fastModelName || model.name == fastModelName }
            val fastApiData = userSettings.apis.find { it.provider == fastChatModel?.provider }
            val smartChatModel =
                chatModels.find { model -> model.modelName == smartModelName || model.name == smartModelName }
            val smartApiData = userSettings.apis.find { it.provider == smartChatModel?.provider }
            val imageChatModel =
                chatModels.find { model -> model.modelName == imageChatModelName || model.name == imageChatModelName }
            val imageChatApiData = userSettings.apis.find { it.provider == imageChatModel?.provider }
            val imageModel =
                imageModels.find { model -> model.modelName == imageModelName || model.name == imageModelName }
            val imageApiData = userSettings.apis.find { it.provider == imageModel?.provider }

            settings.fastModel = ApiChatModel(fastChatModel, fastApiData)
            settings.diffLoggingEnabled = component.diffLoggingEnabled.isSelected
            settings.imageChatModel = ApiChatModel(imageChatModel, imageChatApiData)
            settings.awsProfile = component.awsProfile.text.takeIf { it.isNotBlank() }
            settings.awsRegion = component.awsRegion.text.takeIf { it.isNotBlank() }
            settings.awsBucket = component.awsBucket.text.takeIf { it.isNotBlank() }
            settings.listeningPort = component.listeningPort.text.safeInt()
            settings.listeningEndpoint = component.listeningEndpoint.text
            settings.suppressErrors = component.suppressErrors.isSelected
            settings.smartModel = ApiChatModel(smartChatModel, smartApiData)
            settings.imageModel = imageModel?.let { ApiImageModel(it, imageApiData) }
            settings.devActions = component.devActions.isSelected
            settings.disableAutoOpenUrls = component.disableAutoOpenUrls.isSelected
            settings.temperature = component.temperature.text.safeDouble()
            settings.embeddingModel = component.embeddingModel.selectedItem?.let {
                when (it) {
                    is String -> it.embeddingModel()
                    is EmbeddingModel -> it
                    else -> null
                }
            }
            settings.shellCommand = component.shellCommand.text
            settings.showWelcomeScreen = component.showWelcomeScreen.isSelected
            settings.processor = component.patchProcessor.selectedItem?.let {
                when (it) {
                    is String -> try {
                        PatchProcessors.valueOf(it)
                    } catch (e: IllegalArgumentException) {
                        log.warn("Unknown patch processor: $it, defaulting to Fuzzy")
                        PatchProcessors.Fuzzy
                    }

                    is PatchProcessor -> it
                    else -> PatchProcessors.Fuzzy
                } as? PatchProcessors ?: PatchProcessors.Fuzzy
            } ?: PatchProcessors.Fuzzy

            val tableModel = component.apis.model as DefaultTableModel
            log.debug("Reading API keys from table with ${tableModel.rowCount} rows")
            userSettings.apis.clear()
            for (row in 0 until tableModel.rowCount) {
                try {
                    val provider = (tableModel.getValueAt(row, 0) as? String) ?: ""
                    val name = (tableModel.getValueAt(row, 1) as? String) ?: ""
                    val key = (tableModel.getValueAt(row, 2) as? String) ?: ""
                    val base = (tableModel.getValueAt(row, 3) as? String) ?: ""
                    log.debug("Row $row: provider=$provider, name=$name, key=<${if (key.isNotBlank()) "hidden" else "empty"}>, base=$base")

                    if (provider.isNotBlank()) {
                        try {
                            val apiProvider = APIProvider.valueOf(provider)
                            userSettings.apis.add(
                                ApiData(
                                    name = name.takeIf { it.isNotBlank() },
                                    key = (key.takeIf { it.isNotBlank() } ?: "").encrypt,
                                    baseUrl = base,
                                    provider = apiProvider))
                        } catch (e: Exception) {
                            log.debug("Added API configuration for provider: $provider")
                        } catch (e: IllegalArgumentException) {
                            log.warn("Unknown provider at row $row: $provider", e)
                        }
                    }
                } catch (e: Exception) {
                    log.error("Failed to read API configuration from row $row", e)
                }
            }
            val toolsModel = component.tools.model as DefaultTableModel
            log.debug("Reading Tools from table with ${toolsModel.rowCount} rows")
            userSettings.tools.clear()
            for (row in 0 until toolsModel.rowCount) {
                try {
                    val providerName = (toolsModel.getValueAt(row, 0) as? String) ?: ""
                    val path = (toolsModel.getValueAt(row, 1) as? String) ?: ""
                    if (providerName.isNotBlank()) {
                        try {
                            val provider = ToolProvider.valueOf(providerName)
                            userSettings.tools.add(ToolData(provider, path))
                        } catch (e: Exception) {
                            log.warn("Unknown tool provider: $providerName")
                        }
                    }
                } catch (e: Exception) {
                    log.error("Failed to read tool configuration from row $row", e)
                }
            }
            ApplicationServices.fileApplicationServices(AppSettingsState.pluginHome).userSettingsManager.updateUserSettings(
                UserSettingsManager.defaultUser,
                userSettings
            )
            log.info("Successfully read settings with ${userSettings.apis.size} API configurations")
            log.debug("Settings after reading: ${settings.toJson()}")

        } catch (e: Exception) {
            log.error("Failed to read settings from UI components", e)
            throw IllegalStateException("Failed to read settings from UI", e)
        }
    }

    companion object {
        val log = com.intellij.openapi.diagnostic.Logger.getInstance(StaticAppSettingsConfigurable::class.java)
    }
}

fun String.embeddingModel(): EmbeddingModel? = try {
    EmbeddingModel.values()[this]
} catch (e: Exception) {
    StaticAppSettingsConfigurable.log.warn("Failed to parse embedding model: $this", e)
    null
}

fun String?.safeInt() = if (null == this) 0 else when {
    isEmpty() -> 0
    else -> try {
        toInt()
    } catch (e: NumberFormatException) {
        StaticAppSettingsConfigurable.log.debug("Failed to parse integer: $this", e)
        0
    }
}

fun String?.safeDouble() = if (null == this) 0.0 else when {
    isEmpty() -> 0.0
    else -> try {
        toDouble()
    } catch (e: NumberFormatException) {
        StaticAppSettingsConfigurable.log.debug("Failed to parse double: $this", e)
        0.0
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/UIAdapter.kt

```
package com.simiacryptus.cognotik.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.util.buildFormViaReflection
import javax.swing.JComponent

abstract class UIAdapter<C : Any, S : Any>(
    protected val settingsInstance: S,
    var component: C? = null,
) : Configurable {

    companion object {
        private val log = LoggerFactory.getLogger(UIAdapter::class.java)
    }

    @Volatile
    private var mainPanel: JComponent? = null
    override fun getDisplayName(): String {
        return "AICoder Settings"
    }

    override fun getPreferredFocusedComponent(): JComponent? = null

    override fun createComponent(): JComponent? {
        if (null == mainPanel) {
            synchronized(this) {
                if (null == mainPanel) {
                    try {
                        val component = newComponent()
                        this.component = component
                        mainPanel = build(component)
                        write(settingsInstance, component)
                    } catch (e: Exception) {
                        log.error("Error creating component", e)
                    }
                }
            }
        }
        return mainPanel
    }

    abstract fun newComponent(): C
    abstract fun newSettings(): S
    private fun getSettings(component: C? = this.component) = try {
        when (component) {
            null -> settingsInstance
            else -> {
                val buffer = newSettings()
                read(component, buffer)
                buffer
            }
        }
    } catch (e: Exception) {
        log.error("Error reading settings", e)
        settingsInstance
    }

    override fun isModified() = when {
        component == null -> false
        getSettings() != settingsInstance -> true
        else -> false
    }

    override fun apply() {
        if (component != null) read(component!!, settingsInstance)
    }

    override fun reset() {
        if (component != null) write(settingsInstance, component!!)
    }

    override fun disposeUIResources() {
        val component = component
        this.component = null
        if (component != null && component is Disposable) component.dispose()
    }

    open fun build(component: C): JComponent =
        component.buildFormViaReflection(false)!!

    open fun read(component: C, settings: S) {
        UITools.readKotlinUIViaReflection(settings, component, Any::class)
    }

    open fun write(settings: S, component: C) {
        UITools.writeKotlinUIViaReflection(settings, component, Any::class)
    }

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/UsageTable.kt

```
﻿package com.simiacryptus.cognotik.config

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.util.BrowseUtil
import org.jdesktop.swingx.JXTable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.net.URI
import java.util.*
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class UsageTable(
    val usage: UsageInterface
) : JPanel(BorderLayout()) {

    private val buttonPanel = JPanel()
    val columnNames = arrayOf("Model", "Prompt", "Completion", "Cost")

    val rowData by lazy {
        val usageData = usage.getUserUsageSummary(UserSettingsManager.defaultUser).map { entry ->
            listOf(
                entry.key,
                entry.value.prompt_tokens.toString(),
                entry.value.completion_tokens.toString(),
                String.format("%.2f", entry.value.cost)
            ).toMutableList()
        }

        val totalPromptTokens = usageData.sumOf { it[1].toInt() }
        val totalCompletionTokens = usageData.sumOf { it[2].toInt() }
        val totalCost = usageData.sumOf { it[3].toDouble() }

        (usageData + listOf(
            listOf(
                "TOTAL",
                totalPromptTokens.toString(),
                totalCompletionTokens.toString(),
                String.format("%.2f", totalCost)
            ).toMutableList()
        )).toMutableList()
    }

    private val dataModel by lazy {
        object : AbstractTableModel() {
            init {
                checkUsageThreshold()
            }

            override fun getColumnName(column: Int): String {
                return columnNames.get(column)
            }

            override fun getValueAt(row: Int, col: Int): Any {
                return rowData[row][col]
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {

                return row != rowData.size - 1
            }

            override fun getRowCount(): Int {
                return rowData.size
            }

            override fun getColumnCount(): Int {
                return columnNames.size
            }

            override fun setValueAt(value: Any, row: Int, col: Int) {

                if (row == rowData.size - 1) return
                val strings = rowData[row]
                strings[col] = value.toString()
                fireTableCellUpdated(row, col)
                checkUsageThreshold()
            }

        }
    }

    private val jtable by lazy { JBTable(dataModel) }

    private val scrollpane by lazy { JBScrollPane(jtable) }

    private val clearButton by lazy {
        JButton(object : AbstractAction("Clear") {
            override fun actionPerformed(e: ActionEvent?) {
                rowData.clear()
                usage.clear()
                this@UsageTable.parent.invalidate()
            }
        })
    }

    init {

        val totalRowRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                if (row == table?.model?.rowCount?.minus(1)) {
                    font = font.deriveFont(font.style or java.awt.Font.BOLD)
                }
                return c
            }
        }

        jtable.columnModel.getColumn(0).cellRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(1).cellRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(2).cellRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(3).cellRenderer = DefaultTableCellRenderer()

        for (i in 0..3) {
            val column = jtable.columnModel.getColumn(i)
            column.cellRenderer = totalRowRenderer
        }

        val editor = object : JXTable.GenericEditor() {
            override fun isCellEditable(anEvent: EventObject?) = false
        }
        jtable.columnModel.getColumn(0).cellEditor = editor
        jtable.columnModel.getColumn(1).cellEditor = editor
        jtable.columnModel.getColumn(2).cellEditor = editor
        jtable.columnModel.getColumn(3).cellEditor = editor

        jtable.columnModel.getColumn(0).headerRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(1).headerRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(2).headerRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(3).headerRenderer = DefaultTableCellRenderer()

        initCol(0)
        initCol(1)
        initCol(2)
        initCol(3)

        jtable.tableHeader.defaultRenderer = DefaultTableCellRenderer()

        add(scrollpane, BorderLayout.CENTER)
        buttonPanel.add(clearButton)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun checkUsageThreshold() {
        val settings = AppSettingsState.instance
        if (settings.feedbackOptOut || settings.feedbackRequested) {
            return
        }
        val totalTokens = rowData.dropLast(1).sumOf {
            (it[1].toIntOrNull() ?: 0) + (it[2].toIntOrNull() ?: 0)
        }
        if (totalTokens >= 1000000) {
            settings.feedbackRequested = true
            showFeedbackNotification()
        }
    }

    private fun showFeedbackNotification() {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Cognotik Feedback")
            .createNotification(
                "You're Making Great Progress with Cognotik! 🚀",
                "You've processed over 10,000 tokens! Your feedback helps shape the future of this open-source tool. Share your experience?",
                NotificationType.INFORMATION
            )
        notification.addAction(object : AnAction("It's Great! 🎉") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
                showReviewRequest()
            }
        })
        notification.addAction(object : AnAction("I Have Feedback 💭") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
                showFeedbackDialog()
            }
        })
        notification.addAction(object : AnAction("Don't Ask Again") {
            override fun actionPerformed(e: AnActionEvent) {
                AppSettingsState.instance.feedbackOptOut = true
                notification.expire()
            }
        })
        notification.notify(null)
    }

    private fun showReviewRequest() {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Cognotik Feedback")
            .createNotification(
                "Help Other Developers Discover Cognotik! 🌟",
                "Your positive experience can help others find this tool. Would you consider leaving a quick review? It takes less than a minute and makes a huge difference!",
                NotificationType.INFORMATION
            )
        notification.addAction(object : AnAction("Leave a Review ⭐") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowseUtil.browse(URI("https://plugins.jetbrains.com/plugin/27289-cognotik/reviews"))
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Maybe Later") {
            override fun actionPerformed(e: AnActionEvent) {
                AppSettingsState.instance.feedbackRequested = false
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Share Feedback Instead 💬") {
            override fun actionPerformed(e: AnActionEvent) {
                showFeedbackDialog()
                notification.expire()
            }
        })
        notification.notify(null)
    }

    private fun showFeedbackDialog() {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Cognotik Feedback")
            .createNotification(
                "Your Feedback Shapes Cognotik's Future! 💡",
                """
                As an open-source project, your input directly influences our development priorities. Here's how you can contribute:
                
                💬 Share Your Thoughts:
                • Which features are most valuable to you?
                • What workflows could be smoother?
                • What new capabilities would you like to see?
                
                🔧 Troubleshooting Tips:
                • Review our documentation for setup guidance
                • Double-check your API key configuration
                • Experiment with different models for your use case
                • Adjust temperature and other model parameters
                
                Together, we're building better AI-powered development tools!
                """.trimIndent(),
                NotificationType.INFORMATION
            )
        notification.addAction(object : AnAction("Report an Issue 🐛") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowseUtil.browse(URI("https://github.com/SimiaCryptus/Cognotik/issues/new"))
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Share Ideas 💬") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowseUtil.browse(URI("https://github.com/SimiaCryptus/Cognotik/discussions/new?category=feedback"))
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Browse Docs 📚") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowseUtil.browse(URI("https://github.com/SimiaCryptus/Cognotik#readme"))
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Not Now") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
            }
        })
        notification.notify(null)
    }


    private fun initCol(idx: Int) {
        val headerRenderer = jtable.tableHeader.defaultRenderer
        val headerValue = jtable.columnModel.getColumn(idx).headerValue
        val headerComp = headerRenderer.getTableCellRendererComponent(jtable, headerValue, false, false, 0, idx)
        jtable.columnModel.getColumn(idx).preferredWidth = headerComp.preferredSize.width
    }

}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

### intellij/src/main/kotlin/com/simiacryptus/cognotik/config/README.md
```markdown
# Configuration System

The `com.simiacryptus.cognotik.config` package manages the persistence and user interface for all plugin settings. It provides a robust framework for handling API keys, model selections, tool paths, and advanced system configurations.

## Key Components

### [AppSettingsState.kt](./AppSettingsState.kt)
The central data model for the plugin's configuration. It uses IntelliJ's `PersistentStateComponent` to save settings in `SdkSettingsPlugin.xml`.
- **Model Settings**: Configuration for Smart, Fast, Image, and Embedding models.
- **Audio Settings**: Parameters for voice interaction (sample rate, silence detection, etc.).
- **AWS Settings**: Credentials and region info for AWS-based services.
- **System Configuration**: Port settings, shell commands, and developer mode toggles.
- **MRU History**: Stores recently used commands and arguments.

### [StaticAppSettingsConfigurable.kt](./StaticAppSettingsConfigurable.kt)
The primary UI provider for the settings dialog. It organizes settings into several tabs:
- **Basic Settings**: Model selection and temperature control.
- **Keys**: Management of API providers (OpenAI, Anthropic, etc.) and their keys.
- **Tools**: Configuration of external executable paths.
- **Advanced Settings**: Server endpoints, shell configuration, and logging toggles.
- **AWS**: AWS-specific profile and bucket settings.
- **Import/Export**: Allows users to share or backup their entire configuration via JSON.

### [AppSettingsComponent.kt](./AppSettingsComponent.kt)
The Swing-based UI component that defines the layout of the settings forms. It includes specialized editors for API and Tool tables, including auto-detection features for common tools.

### [UsageTable.kt](./UsageTable.kt)
A specialized component that displays a summary of token usage and estimated costs across different models. It also includes a feedback mechanism that triggers after significant usage (1,000,000 tokens).

### [MRUItems.kt](./MRUItems.kt)
A thread-safe helper class for managing "Most Recently Used" lists, used for command history and instruction suggestions.

## Configuration Categories

### API Management
The system supports multiple API providers. Each configuration includes:
- **Provider Type**: (e.g., OpenAI, Anthropic, Google, etc.)
- **Name**: A unique identifier for the specific API instance.
- **Key**: Encrypted storage of API credentials.
- **Base URL**: Customizable endpoint for proxy or private deployments.

### Tool Management
Allows the AI to interact with the local system by defining paths to executables. Includes an "Auto-Detect" feature to find common tools in the system PATH.

### Model Selection
Users can independently configure models for different tasks:
- **Smart Model**: High-reasoning models for complex tasks.
- **Fast Model**: Low-latency models for simple edits or summaries.
- **Image Model**: Models for vision and image generation tasks.
- **Embedding Model**: Used for vector search and context retrieval.

## Implementation Details

- **Persistence**: Settings are serialized to JSON and stored within the IntelliJ platform's XML storage.
- **Security**: API keys are encrypted before being stored on disk using `com.simiacryptus.cognotik.util.encrypt`.
- **Reflection-based UI**: Uses `UITools` to automatically map `AppSettingsState` properties to UI components in `AppSettingsComponent` based on naming conventions and the `@Name` annotation.
```

This new `README.md` provides a comprehensive overview of the configuration package, detailing the roles of various classes and the structure of the settings system. It highlights key features like API management, tool auto-detection, and the usage tracking system.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/README.md'>intellij/src/main/kotlin/com/simiacryptus/cognotik/config/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/com/simiacryptus/cognotik/config/README.md'>intellij/src/main/kotlin/com/simiacryptus/cognotik/config/README.md</a> Updated
