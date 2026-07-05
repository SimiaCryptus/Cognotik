package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.util.ErbTemplateEngine
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.util.getSelectedFiles
import org.slf4j.LoggerFactory
import java.io.File
import javax.swing.JComponent

class RenderErbTemplateAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isEnabled(event: AnActionEvent): Boolean {
        val files = event.getSelectedFiles()
        if (files.isEmpty()) return false
        
        // Check if we have at least one template file and one JSON file
        val hasTemplate = files.any { isTemplateFile(it.toFile) }
        val hasJson = files.any { it.extension?.lowercase() == "json" }
        
        // Enable if we have both, or if we have JSON files (template can be selected via dialog)
        return (hasTemplate && hasJson) || hasJson
    }

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getSelectedFiles().map { it.toFile }
        
        // Separate template files and JSON files
        val templateFiles = selectedFiles.filter { isTemplateFile(it) }
        val jsonFiles = selectedFiles.filter { it.extension.lowercase() == "json" }
        
        if (jsonFiles.isEmpty()) {
            Messages.showErrorDialog(project, "Please select at least one JSON file to render.", "No JSON Files Selected")
            return
        }
        
        // If no template selected, prompt user to select one
        val templateFile = if (templateFiles.isEmpty()) {
            selectTemplateFile(project) ?: return
        } else if (templateFiles.size > 1) {
            // If multiple templates, show dialog to choose
            val dialog = TemplateSelectionDialog(project, templateFiles)
            if (!dialog.showAndGet()) return
            dialog.selectedTemplate ?: return
        } else {
            templateFiles.first()
        }
        
        // Show configuration dialog
        val configDialog = RenderConfigDialog(project, templateFile, jsonFiles)
        if (!configDialog.showAndGet()) return
        
        val outputDir = configDialog.outputDirectory
        val overwrite = configDialog.overwriteExisting
        
        UITools.runAsync(project, "Rendering ERB Templates", true) { progress ->
            renderTemplates(progress, templateFile, jsonFiles, outputDir, overwrite, project)
        }
    }
    
    private fun renderTemplates(
        progress: ProgressIndicator,
        templateFile: File,
        jsonFiles: List<File>,
        outputDir: File,
        overwrite: Boolean,
        project: Project
    ) {
        val engine = ErbTemplateEngine()
        val gson = Gson()
        val templateContent = templateFile.readText()
        val outputExtension = determineOutputExtension(templateFile)
        
        val results = mutableListOf<RenderResult>()
        
        jsonFiles.forEachIndexed { index, jsonFile ->
            progress.text = "Rendering ${jsonFile.name}..."
            progress.fraction = index.toDouble() / jsonFiles.size
            
            try {
                val jsonContent = jsonFile.readText()
                val jsonData = gson.fromJson(jsonContent, JsonObject::class.java)
                
                val renderedContent = engine.render(templateContent, jsonData)
                
                val outputFileName = jsonFile.nameWithoutExtension + "." + outputExtension
                val outputFile = File(outputDir, outputFileName)
                
                if (outputFile.exists() && !overwrite) {
                    results.add(RenderResult(jsonFile.name, outputFile.name, false, "File already exists"))
                } else {
                    outputDir.mkdirs()
                    outputFile.writeText(renderedContent)
                    results.add(RenderResult(jsonFile.name, outputFile.name, true, null))
                }
            } catch (e: Exception) {
                log.error("Error rendering ${jsonFile.name}", e)
                results.add(RenderResult(jsonFile.name, null, false, e.message ?: "Unknown error"))
            }
        }
        
        // Show results summary
        val successCount = results.count { it.success }
        val failCount = results.size - successCount
        
        val message = buildString {
            appendLine("Rendering complete!")
            appendLine()
            appendLine("✅ Success: $successCount")
            if (failCount > 0) {
                appendLine("❌ Failed: $failCount")
                appendLine()
                appendLine("Failures:")
                results.filter { !it.success }.forEach {
                    appendLine("  - ${it.inputFile}: ${it.error}")
                }
            }
            if (successCount > 0) {
                appendLine()
                appendLine("Output files written to: ${outputDir.absolutePath}")
            }
        }

        invokeLater {
            if (failCount > 0) {
                Messages.showWarningDialog(project, message, "Rendering Results")
            } else {
                Messages.showInfoMessage(project, message, "Rendering Complete")
            }
        }
    }
    
    private fun isTemplateFile(file: File) = file.name.lowercase().endsWith(".erb")
    
    private fun determineOutputExtension(templateFile: File): String {
        val name = templateFile.name
        
        // For patterns like foo.html.erb -> html
        val parts = name.split(".")
        
        return when {
            parts.size >= 3 && parts.last().lowercase() == "erb" -> parts[parts.size - 2]
            parts.size >= 2 && parts.last().lowercase() == "erb" -> "txt"
            else -> parts.last()
        }
    }
    
    private fun selectTemplateFile(project: Project): File? {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Select ERB Template")
            .withDescription("Select an ERB template file (*.erb or *.*.erb)")
        
        val selectedFile = FileChooser.chooseFile(descriptor, project, null)
        return selectedFile?.toFile
    }
    
    private data class RenderResult(
        val inputFile: String,
        val outputFile: String?,
        val success: Boolean,
        val error: String?
    )
    
    class TemplateSelectionDialog(
        project: Project,
        private val templates: List<File>
    ) : DialogWrapper(project) {
        
        var selectedTemplate: File? = null
        private val templateCombo = com.intellij.openapi.ui.ComboBox(templates.map { it.name }.toTypedArray())
        
        init {
            init()
            title = "Select Template"
        }
        
        override fun createCenterPanel(): JComponent = panel {
            row("Template:") {
                cell(templateCombo).align(Align.FILL)
            }
        }
        
        override fun doOKAction() {
            selectedTemplate = templates[templateCombo.selectedIndex]
            super.doOKAction()
        }
    }
    
    class RenderConfigDialog(
        project: Project,
        private val templateFile: File,
        private val jsonFiles: List<File>
    ) : DialogWrapper(project) {
        
        private val outputDirField = JBTextField(jsonFiles.first().parentFile.absolutePath)
        private val overwriteCheckbox = JBCheckBox("Overwrite existing files", true)
        private val outputExtension = determineOutputExtensionStatic(templateFile)
        
        val outputDirectory: File get() = File(outputDirField.text)
        val overwriteExisting: Boolean get() = overwriteCheckbox.isSelected
        
        init {
            init()
            title = "Configure ERB Template Rendering"
        }
        
        override fun createCenterPanel(): JComponent = panel {
            row("Template:") {
                cell(JBLabel(templateFile.name))
            }
            row("JSON Files:") {
                cell(JBLabel("${jsonFiles.size} file(s) selected"))
            }
            row("Output Extension:") {
                cell(JBLabel(".$outputExtension"))
            }
            row("Output Directory:") {
                cell(outputDirField).align(Align.FILL)
                button("Browse...") {
                    val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    val selected = FileChooser.chooseFile(descriptor, null, null)
                    selected?.let { outputDirField.text = it.path }
                }
            }
            row {
                cell(overwriteCheckbox)
            }
            
            if (jsonFiles.size <= 5) {
                group("Preview Output Files") {
                    jsonFiles.forEach { jsonFile ->
                        row {
                            cell(JBLabel("${jsonFile.name} → ${jsonFile.nameWithoutExtension}.$outputExtension"))
                        }
                    }
                }
            }
        }
        
        companion object {
            private fun determineOutputExtensionStatic(templateFile: File): String {
                val name = templateFile.name
                val parts = name.split(".")
                
                return when {
                    parts.size >= 3 && parts.last().lowercase() == "erb" -> parts[parts.size - 2]
                    parts.size >= 2 && parts.last().lowercase() == "erb" -> "txt"
                    else -> parts.last()
                }
            }
        }
    }
    
    companion object {
        private val log = LoggerFactory.getLogger(RenderErbTemplateAction::class.java)
    }
}