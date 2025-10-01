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
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.image.ImageModels
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
import com.simiacryptus.cognotik.util.LoggerFactory
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
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

    val executablesModel = DefaultListModel<String>().apply {
        AppSettingsState.instance.executables?.forEach { addElement(it) }
    }
    val executablesList = JBList(executablesModel)

    @Name("Executables")
    val executablesPanel = JPanel(BorderLayout()).apply {
        val scrollPane = JScrollPane(executablesList)
        scrollPane.preferredSize = Dimension(300, 200)
        add(scrollPane, BorderLayout.CENTER)
        val buttonPanel = JPanel()
        val addButton = JButton("Add")
        val removeButton = JButton("Remove")
        val editButton = JButton("Edit")
        removeButton.isEnabled = false
        editButton.isEnabled = false

        addButton.addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            descriptor.title = "Select Executable"
            try {
                FileChooser.chooseFile(descriptor, null, null) { file ->
                    val executablePath = file.path
                    if (executablePath.isNotBlank() && !executablesModel.contains(executablePath)) {
                        executablesModel.addElement(executablePath)
                        AppSettingsState.instance.executables?.add(executablePath)
                        log.debug("Successfully added executable: $executablePath")
                    } else {
                        if (executablePath.isBlank()) {
                            log.warn("Attempted to add blank executable path")
                        } else {
                            log.warn("Executable already exists in list: $executablePath")
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to add executable: ${e.message}", e)
                JOptionPane.showMessageDialog(
                    this, "Failed to add executable: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE
                )
            }
        }
        removeButton.addActionListener {
            try {
                val selectedIndices = executablesList.selectedIndices
                if (selectedIndices.isEmpty()) {
                    log.warn("No executables selected for removal")
                    return@addActionListener
                }
                for (i in selectedIndices.reversed()) {
                    val removed = executablesModel.remove(i)
                    AppSettingsState.instance.executables?.remove(removed)
                    log.debug("Successfully removed executable: $removed")
                }
            } catch (e: Exception) {
                log.error("Unexpected error removing executable: ${e.message}", e)
                JOptionPane.showMessageDialog(
                    this, "Failed to remove executable: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE
                )
            }
        }
        editButton.addActionListener {
            try {
                val selectedIndex = executablesList.selectedIndex
                if (selectedIndex == -1) {
                    log.warn("No executable selected for editing")
                    return@addActionListener
                }
                val currentValue = executablesModel.get(selectedIndex)
                val newValue = JOptionPane.showInputDialog(this, "Edit executable path:", currentValue)
                if (newValue != null && newValue.isNotBlank()) {
                    executablesModel.set(selectedIndex, newValue)
                    AppSettingsState.instance.executables?.remove(currentValue)
                    AppSettingsState.instance.executables?.add(newValue)
                    log.debug("Successfully updated executable from '$currentValue' to '$newValue'")
                } else {
                    log.warn("Invalid new executable path provided: ${newValue ?: "null"}")
                }
            } catch (e: Exception) {
                log.error("Unexpected error editing executable: ${e.message}", e)
                JOptionPane.showMessageDialog(
                    this, "Failed to edit executable: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE
                )
            }
        }
        executablesList.addListSelectionListener(object : ListSelectionListener {
            override fun valueChanged(e: ListSelectionEvent?) {
                val hasSelection = executablesList.selectedIndex != -1
                removeButton.isEnabled = hasSelection
                editButton.isEnabled = hasSelection
            }
        })
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(editButton)
        add(buttonPanel, BorderLayout.SOUTH)

        executablesList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }

    @Name("Listening Port")
    val listeningPort = JBTextField()

    @Name("Listening Endpoint")
    val listeningEndpoint = JBTextField()

    @Name("Suppress Errors")
    val suppressErrors = JBCheckBox()

    @Name("Model")
    val smartModel = ComboBox<String>()

    @Name("Model")
    val fastModel = ComboBox<String>()

    @Name("Main Image Model")
    val mainImageModel = ComboBox<String>()

    @Name("Embedding Model")
    val embeddingModel = ComboBox<String>()


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
                urlField.text = selectedProvider.base ?: ""
                nameField.text = selectedProvider.name
                urlField.text = selectedProvider.base ?: ""
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
                val key = keyField.text
                val url = urlField.text

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
    var usage = UsageTable(fileApplicationServices(AppSettingsState.Companion.pluginHome).usageManager)

    init {
        log.debug("Initializing AppSettingsComponent")
        try {

            diffLoggingEnabled.isSelected = AppSettingsState.instance.diffLoggingEnabled
            awsProfile.text = AppSettingsState.instance.awsProfile ?: ""
            awsRegion.text = AppSettingsState.instance.awsRegion ?: ""
            awsBucket.text = AppSettingsState.instance.awsBucket ?: ""
            disableAutoOpenUrls.isSelected = AppSettingsState.instance.disableAutoOpenUrls

            setExecutables(AppSettingsState.instance.executables ?: emptySet())
        } catch (e: Exception) {
            log.error("Error initializing basic settings: ${e.message}", e)
        }
        try {
            // Populate API table first
            populateApiTable()
        } catch (e: Exception) {
            log.error("Error populating API table: ${e.message}", e)
        }
        val apis = fileApplicationServices(AppSettingsState.Companion.pluginHome).userSettingsManager.getUserSettings().apis
        try {

            // Get all available models from APIs with valid keys
            val availableModels = try {
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

            availableModels.forEach {
                this.smartModel.addItem(it.value.modelName)
                this.fastModel.addItem(it.value.modelName)
            }
            log.debug("Loaded ${availableModels.size} available models")
        } catch (e: Exception) {
            log.error("Error loading models: ${e.message}", e)
        }
        try {

            ImageModels.values.values.forEach {
                this.mainImageModel.addItem(it.name)
            }
            EmbeddingModel.values().keys.forEach {
                this.embeddingModel.addItem(it)
            }
        } catch (e: Exception) {
            log.error("Error loading image and embedding models: ${e.message}", e)
        }


        val smartModelItems = (0 until smartModel.itemCount).map { smartModel.getItemAt(it) }.filter { modelItem ->
            val chatModel = apis.filter { it.key != null }
                    .mapNotNull { apiData -> apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.find { it.modelName == modelItem } }.firstOrNull()
            if (chatModel == null) {
                false
            } else {
                val visible = isVisible(chatModel)
                visible
            }
        }.sortedBy { modelItem ->
            val model =
                apis.filter { it.key != null }
                    .find { apiData -> apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.any { it.modelName == modelItem } == true }
                    ?.let { apiData -> apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.find { it.modelName == modelItem } }!!
            "${model.provider?.name} - ${model.modelName}"
        }.toList()
        val fastModelItems = (0 until fastModel.itemCount).map { fastModel.getItemAt(it) }.filter { modelItem ->
            val chatModel = apis.filter { it.key != null }
                .mapNotNull { apiData -> apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.find { it.modelName == modelItem } }.firstOrNull()
            if (chatModel == null) {
                false
            } else {
                val visible = isVisible(chatModel)
                visible
            }
        }.sortedBy { modelItem ->
            val model =
                //ChatModel.values().entries.find { it.value.modelName == modelItem }?.value ?: return@sortedBy ""
                apis.filter { it.key != null }
                    .find { apiData -> apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.any { it.modelName == modelItem } == true }
                    ?.let { apiData -> apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.find { it.modelName == modelItem } }
            "${model?.provider?.name} - ${model?.modelName}"
        }.toList()
        smartModel.removeAllItems()
        fastModel.removeAllItems()
        smartModelItems.forEach { smartModel.addItem(it) }
        fastModelItems.forEach { fastModel.addItem(it) }
        this.smartModel.isEditable = true
        this.fastModel.isEditable = true
        this.smartModel.renderer = getModelRenderer()
        this.fastModel.renderer = getModelRenderer()
        this.mainImageModel.isEditable = true
        this.mainImageModel.renderer = getImageModelRenderer()
        this.embeddingModel.isEditable = true
        this.embeddingModel.renderer = getEmbeddingModelRenderer()
        // Set current selections
        AppSettingsState.instance.smartModel?.model?.let { model ->
            this.smartModel.selectedItem = model.modelName
        }
        AppSettingsState.instance.fastModel?.model?.let { model ->
            this.fastModel.selectedItem = model.modelName
        }
        AppSettingsState.instance.embeddingModel?.let { model ->
            this.embeddingModel.selectedItem = model
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
                val key = api.key
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
                    .find { apiData -> apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.any { it.modelName == value } == true }
                    ?.let { apiData -> apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.find { it.modelName == value } }
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


    fun getExecutables(): Set<String> {
        return try {
            val model =
                ((executablesPanel.getComponent(0) as? JScrollPane)?.viewport?.view as? JList<*>)?.model as? DefaultListModel<String>
            model?.elements()?.toList()?.toSet() ?: emptySet()
        } catch (e: Exception) {
            log.error("Failed to get executables list: ${e.message}", e)
            emptySet()
        }
    }

    fun setExecutables(executables: Set<String>) {
        try {
            val model =
                ((executablesPanel.getComponent(0) as? JScrollPane)?.viewport?.view as? JList<*>)?.model as? DefaultListModel<String>
            model?.clear()
            executables.forEach { model?.addElement(it) }
            log.debug("Set ${executables.size} executables")
        } catch (e: Exception) {
            log.error("Failed to set executables: ${e.message}", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AppSettingsComponent::class.java)
    }
}
