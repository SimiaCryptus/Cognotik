package com.simiacryptus.cognotik.config

import com.intellij.util.xmlb.XmlSerializerUtil
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.util.EncryptionUtil
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.toJson
import java.awt.*
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableModel

class StaticAppSettingsConfigurable : AppSettingsConfigurable() {
    override fun apply() {
        super.apply()
        AppSettingsState.auxiliaryLog = null
        log.debug("Applied settings, cleared auxiliary log")
    }

    private val password = JPasswordField()

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
                        add(JLabel("Image Model:"))
                        add(component.mainImageModel)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Embedding Model:"))
                        add(component.embeddingModel)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Temperature:"))
                        add(component.temperature)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Executables:"))
                        add(component.executablesPanel)
                    })
                    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JLabel("Password:"))
                        add(password)
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
                })
            })
        } catch (e: Exception) {
            log.warn("Error building Basic Settings", e)
        }

        try {
            tabbedPane.addTab("Keys", JPanel(BorderLayout()).apply {
                add(JPanel(BorderLayout()).apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JPanel(BorderLayout()).apply {
                        add(JLabel("API Configurations:"), BorderLayout.NORTH)
                        add(component.apiManagementPanel, BorderLayout.CENTER)
                    })
                    add(JPanel(BorderLayout()).apply {
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
                    })
                })
            })
        } catch (e: Exception) {
            log.warn("Error building Configuration", e)
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
            } catch (e: Exception) {
                log.warn("Error building Developer Tools", e)
            }
        })

        return tabbedPane
    }

    private fun showExportConfigDialog() {
        log.debug("Opening export configuration dialog")
        val dialog = JDialog(null as Frame?, "Export Configuration", true)
        dialog.layout = BorderLayout()

        val userSettings = ApplicationServices.fileApplicationServices(AppSettingsState.Companion.pluginHome).userSettingsManager.getUserSettings()
        val fullConfig = try {
            val encryptedSettings = AppSettingsState.instance.copy()
            // Export UserSettings with encrypted keys
            log.debug("Encrypting ${userSettings.apis.size} API configurations")

            val encryptedUserSettings = userSettings.copy(
                apis = userSettings.apis.map { api ->
                    try {
                        api.copy(key = api.key?.let { EncryptionUtil.encrypt(it, password.text) } ?: api.key)
                    } catch (e: Exception) {
                        log.error("Failed to encrypt API key for provider: ${api.provider}", e)
                        api // Return original if encryption fails
                    }
                }.toMutableList()
            )
            val configJson = JsonUtil.toJson(encryptedSettings)
            val userSettingsJson = JsonUtil.toJson(encryptedUserSettings)
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
                val decryptedUserSettings = importedUserSettings.copy(
                    apis = importedUserSettings.apis.map { api ->
                        try {
                            api.copy(key = api.key?.let { EncryptionUtil.decrypt(it, password.text) } ?: api.key)
                        } catch (e: Exception) {
                            log.error("Failed to decrypt API key for provider: ${api.provider}", e)
                            throw IllegalStateException(
                                "Failed to decrypt API key for ${api.provider}. Please check your password.", e
                            )
                        }
                    }.toMutableList()
                )
                ApplicationServices.fileApplicationServices(AppSettingsState.Companion.pluginHome).userSettingsManager.updateUserSettings(
                    UserSettingsManager.defaultUser, decryptedUserSettings
                )
                log.info("Successfully imported configuration with ${decryptedUserSettings.apis.size} API configurations")
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
            component.devActions.isSelected = settings.devActions
            component.mainImageModel.selectedItem = settings.mainImageModel
            component.temperature.text = settings.temperature.toString()
            component.embeddingModel.selectedItem = settings.embeddingModel
            component.shellCommand.text = settings.shellCommand
            component.showWelcomeScreen.isSelected = settings.showWelcomeScreen
            component.setExecutables(settings.executables ?: emptySet())
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
                AppSettingsState.Companion.pluginHome
            ).userSettingsManager.getUserSettings()
            log.debug("Current user has ${userSettings.apis.size} API configurations")

            val fastModelName = component.fastModel.selectedItem as String?
            val smartModelName = component.smartModel.selectedItem as String?
            log.debug("Selected models - fast: $fastModelName, smart: $smartModelName")

            val fastChatModel = userSettings.apis.filter { it.key.isNotBlank() }.firstOrNull()
                ?.let { apiData -> apiData.provider?.getChatModels(apiData.key, apiData.baseUrl)?.find { model -> model.modelName == fastModelName } }
            val fastApiData = userSettings.apis.find { it.provider == fastChatModel?.provider }
            val smartChatModel = userSettings.apis.filter { it.key.isNotBlank() }.firstOrNull()
                ?.let { apiData -> apiData.provider?.getChatModels(apiData.key, apiData.baseUrl)?.find { model -> model.modelName == smartModelName } }
            val smartApiData = userSettings.apis.find { it.provider == smartChatModel?.provider }

            settings.fastModel = ApiChatModel(fastChatModel, fastApiData)
            settings.diffLoggingEnabled = component.diffLoggingEnabled.isSelected
            settings.awsProfile = component.awsProfile.text.takeIf { it.isNotBlank() }
            settings.awsRegion = component.awsRegion.text.takeIf { it.isNotBlank() }
            settings.awsBucket = component.awsBucket.text.takeIf { it.isNotBlank() }
            settings.executables?.clear()
            settings.executables?.plusAssign(component.getExecutables().toMutableSet())
            settings.listeningPort = component.listeningPort.text.safeInt()
            settings.listeningEndpoint = component.listeningEndpoint.text
            settings.suppressErrors = component.suppressErrors.isSelected
            settings.smartModel = ApiChatModel(smartChatModel, smartApiData)
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
            settings.mainImageModel = component.mainImageModel.selectedItem.let {
                when (it) {
                    is String -> it
                    else -> ""
                }
            }
            settings.shellCommand = component.shellCommand.text
            settings.showWelcomeScreen = component.showWelcomeScreen.isSelected

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
                                key = key.takeIf { it.isNotBlank() } ?: "",
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
            ApplicationServices.fileApplicationServices(AppSettingsState.Companion.pluginHome).userSettingsManager.updateUserSettings(UserSettingsManager.defaultUser, userSettings)
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