package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.util.getSelectedFolder
import java.awt.Dimension
import java.io.File
import javax.swing.JComponent

class CreateErbTemplateAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isEnabled(event: AnActionEvent): Boolean {
        return event.project != null
    }

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFolder = e.getSelectedFolder()?.toFile ?: project.basePath?.let { File(it) } ?: return
        
        // Show configuration dialog
        val configDialog = CreateErbTemplateDialog(project, selectedFolder)
        if (!configDialog.showAndGet()) return
        
        val config = configDialog.getConfiguration()
        
        UITools.runAsync(project, "Creating ERB Template", true) { progress ->
            createTemplate(progress, config, project)
        }
    }
    
    private fun createTemplate(
        progress: ProgressIndicator,
        config: TemplateConfig,
        project: Project
    ) {
        progress.text = "Generating template with AI..."
        progress.isIndeterminate = true
        
        try {
            val settings = AppSettingsState.instance
            val chatModel = settings.smartModel
            
            val systemPrompt = buildSystemPrompt(config.outputFormat)
            val userPrompt = buildUserPrompt(config)
            
            val chatAgent = ChatAgent(
                name = "CreateErbTemplate",
                prompt = systemPrompt,
                model = chatModel?.instance() ?: throw IllegalStateException("No chat model configured"),
                temperature = settings.temperature,
            )
            
            progress.text = "Waiting for AI response..."
            val response = chatAgent.answer(listOf(userPrompt))
            
            progress.text = "Extracting template..."
            val template = extractTemplate(response)
            
            progress.text = "Writing template file..."
            val outputFile = File(config.outputDirectory, config.outputFileName)
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(template)
            
            com.intellij.openapi.application.invokeLater {
                Messages.showInfoMessage(
                    project,
                    "Template created successfully!\n\nFile: ${outputFile.absolutePath}",
                    "Template Created"
                )
                
                // Refresh the project view
                com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .refreshAndFindFileByIoFile(outputFile)?.let { vf ->
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                            .openFile(vf, true)
                    }
            }
            
        } catch (e: Exception) {
            log.error("Error creating ERB template", e)
            com.intellij.openapi.application.invokeLater {
                Messages.showErrorDialog(
                    project,
                    "Failed to create template: ${e.message}",
                    "Error"
                )
            }
        }
    }
    
    private fun buildSystemPrompt(outputFormat: String): String {
        val basePrompt = """You are an expert template designer specializing in ERB-style templates.
Your task is to create well-structured, maintainable templates that follow best practices.

When creating templates:
1. Use clear, descriptive variable names
2. Include appropriate schema preambles for data validation
3. Implement proper error handling with default values
4. Use filters appropriately for data transformation
5. Structure control flow (loops, conditionals) for readability
6. Add comments to explain complex logic
7. Follow the target format's conventions and best practices

ERB Template Syntax:
- Variable interpolation: <%= variable %>
- Loops: <% for item in items %> ... <% end %>
- Conditionals: <% if condition %> ... <% else %> ... <% end %>
- Filters: <%= variable | filter %> (available: escape, markdown, upper, lower, join, default)
- Schema preamble: <%# @schema ... %>
"""
        
        return basePrompt + getFormatSpecificGuidelines(outputFormat)
    }
    
    private fun getFormatSpecificGuidelines(format: String): String = when (format.lowercase()) {
        "latex" -> """

## LaTeX-Specific Guidelines
- Use the `escape` filter for user-provided text to handle special LaTeX characters
- Use the `markdown` filter to convert markdown content to LaTeX
- Structure the document with proper LaTeX commands (\documentclass, \begin{document}, etc.)
- Use appropriate LaTeX packages for the content type
"""
        "html" -> """

## HTML-Specific Guidelines
- Use the `escape` filter for user-provided text to prevent XSS
- Structure with proper HTML5 semantic elements
- Include appropriate meta tags and styling hooks
"""
        "markdown" -> """

## Markdown-Specific Guidelines
- Use proper markdown syntax for headers, lists, and formatting
- Consider GitHub-flavored markdown extensions if appropriate
- Use code blocks with language hints for syntax highlighting
"""
        else -> """

## General Guidelines
- Use appropriate escaping for the target format
- Structure content logically with clear sections
"""
    }
    
    private fun buildUserPrompt(config: TemplateConfig): String = buildString {
        appendLine("Create an ERB-style template with the following requirements:")
        appendLine()
        appendLine("## Template Purpose")
        appendLine(config.description)
        appendLine()
        appendLine("## Output Format")
        appendLine(config.outputFormat)
        appendLine()
        if (config.exampleData.isNotBlank()) {
            appendLine("## Example Data Structure")
            appendLine("```json")
            appendLine(config.exampleData)
            appendLine("```")
            appendLine()
        }
        if (config.includeSchema) {
            appendLine("## Schema Requirements")
            appendLine("Include a TypeScript-style schema preamble at the beginning of the template.")
            appendLine("The schema should define all expected fields with their types.")
            appendLine()
        }
        if (config.features.isNotBlank()) {
            appendLine("## Features to Include")
            appendLine(config.features)
        }
    }
    
    private fun extractTemplate(response: String): String {
        val codeBlockPattern = Regex("""```(?:erb|template|latex|html|markdown|text)?\s*\n(.*?)```""", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockPattern.find(response)
        return match?.groupValues?.get(1)?.trim() ?: response.trim()
    }
    
    data class TemplateConfig(
        val description: String,
        val outputFormat: String,
        val outputFileName: String,
        val outputDirectory: File,
        val exampleData: String,
        val includeSchema: Boolean,
        val features: String
    )
    
    class CreateErbTemplateDialog(
        project: Project,
        private val defaultDirectory: File
    ) : DialogWrapper(project) {
        
        private val descriptionArea = JBTextArea(5, 50).apply {
            lineWrap = true
            wrapStyleWord = true
            text = "A template for generating..."
        }
        
        private val outputFormatCombo = com.intellij.openapi.ui.ComboBox(
            arrayOf("latex", "html", "markdown", "text")
        )
        
        private val fileNameField = JBTextField("template.erb")
        
        private val outputDirField = JBTextField(defaultDirectory.absolutePath)
        
        private val exampleDataArea = JBTextArea(5, 50).apply {
            lineWrap = true
            wrapStyleWord = true
            text = """{"title": "Example", "items": []}"""
        }
        
        private val includeSchemaCheckbox = JBCheckBox("Include TypeScript-style schema preamble", true)
        
        private val featuresField = JBTextField("loops, conditionals, filters")
        
        init {
            init()
            title = "Create ERB Template"
        }
        
        override fun createCenterPanel(): JComponent = panel {
            group("Template Description") {
                row {
                    cell(JBScrollPane(descriptionArea).apply {
                        preferredSize = Dimension(500, 100)
                    }).align(Align.FILL)
                }
                row {
                    comment("Describe the purpose of the template and what it should generate")
                }
            }
            
            group("Output Settings") {
                row("Output Format:") {
                    cell(outputFormatCombo)
                }
                row("File Name:") {
                    cell(fileNameField).align(Align.FILL)
                }
                row("Output Directory:") {
                    cell(outputDirField).align(Align.FILL)
                    button("Browse...") {
                        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        val selected = FileChooser.chooseFile(descriptor, null, null)
                        selected?.let { outputDirField.text = it.path }
                    }
                }
            }
            
            group("Data Structure") {
                row {
                    cell(JBLabel("Example JSON data (helps define the schema):"))
                }
                row {
                    cell(JBScrollPane(exampleDataArea).apply {
                        preferredSize = Dimension(500, 80)
                    }).align(Align.FILL)
                }
            }
            
            group("Options") {
                row {
                    cell(includeSchemaCheckbox)
                }
                row("Features:") {
                    cell(featuresField).align(Align.FILL)
                }
                row {
                    comment("Comma-separated list of features (e.g., loops, conditionals, filters)")
                }
            }
        }
        
        fun getConfiguration(): TemplateConfig {
            var fileName = fileNameField.text.trim()
            if (!fileName.endsWith(".erb")) {
                fileName = "$fileName.erb"
            }
            
            return TemplateConfig(
                description = descriptionArea.text.trim(),
                outputFormat = outputFormatCombo.selectedItem as String,
                outputFileName = fileName,
                outputDirectory = File(outputDirField.text),
                exampleData = exampleDataArea.text.trim(),
                includeSchema = includeSchemaCheckbox.isSelected,
                features = featuresField.text.trim()
            )
        }
        
        override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
            if (descriptionArea.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Template description is required", descriptionArea)
            }
            if (fileNameField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("File name is required", fileNameField)
            }
            if (outputDirField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Output directory is required", outputDirField)
            }
            return null
        }
    }
    
    companion object {
        private val log = LoggerFactory.getLogger(CreateErbTemplateAction::class.java)
    }
}