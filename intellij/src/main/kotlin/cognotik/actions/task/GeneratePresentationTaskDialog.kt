package cognotik.actions.task

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.file.GeneratePresentationTask
import com.simiacryptus.cognotik.platform.ApplicationServices
import cognotik.actions.plan.PlanConfigDialog
import cognotik.actions.plan.toApiChatModel
import com.simiacryptus.cognotik.plan.AbstractTask.TaskState
import java.awt.Dimension
import java.io.File
import javax.swing.JComponent
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class GeneratePresentationTaskDialog(
  project: Project?,
  private val root: File,
  val relatedFiles: List<File>
) : DialogWrapper(project) {

    private val taskDescriptionArea = JBTextArea(8, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "Describe the presentation including topic, key points, target audience, and desired style"
    }

    private val htmlFileField = JBTextField().apply {
        toolTipText = "Path for the HTML presentation file to create (must end with .html)"
        text = "${relatedFiles.firstOrNull()?.nameWithoutExtension?.let { "${it}_presentation" } ?: "presentation"}.html"
    }

    private val relatedFilesField = JBTextField().apply {
        toolTipText = "Comma-separated list of related files to consider for context (e.g., reference materials)"
        text = relatedFiles.joinToString(", ") { it.relativeTo(root).path }
    }

    private val generateImagesCheckbox = JBCheckBox("Generate images for key slides", false).apply {
        toolTipText = "Use AI to generate images for important slides in the presentation"
        addActionListener {
            imageCountSpinner.isEnabled = isSelected
            imageModelCombo.isEnabled = isSelected
        }
    }

    private val imageCountSpinner = JSpinner(SpinnerNumberModel(5, 1, 10, 1)).apply {
        toolTipText = "Maximum number of images to generate (1-10)"
        isEnabled = false
    }

    private val visibleModelsCache by lazy { getVisibleModels() }

    private val modelCombo = ComboBox(
        visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
    ).apply {
        maximumSize = Dimension(200, 30)
        selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
        toolTipText = "AI model to use for generating presentation content"
    }

    private val imageModelCombo = ComboBox(
        visibleModelsCache
            .distinctBy { it.modelName }
            .map { it.modelName }
            .toTypedArray()
    ).apply {
        maximumSize = Dimension(200, 30)
        selectedItem = AppSettingsState.instance.imageChatModel?.model?.modelName
        toolTipText = "AI model to use for generating images"
        isEnabled = false
    }

    private val temperatureSlider = JSlider(0, 100, 70).apply {
        addChangeListener {
            temperatureLabel.text = "%.2f".format(value / 100.0)
        }
    }

    private val temperatureLabel = javax.swing.JLabel("0.70")

    private val autoFixCheckbox = JBCheckBox("Auto-apply generated presentation", false).apply {
        toolTipText = "Automatically write the generated presentation files without manual confirmation"
    }

    init {
        init()
        title = "Configure Presentation Generation Task"
    }

    override fun createCenterPanel(): JComponent = panel {
        group("Presentation Configuration") {
            row("HTML File:") {
                cell(htmlFileField)
                    .align(Align.FILL)
                    .comment("Output path for the presentation file (e.g., presentation.html, slides/demo.html)")
            }

            row("Presentation Description:") {
                scrollCell(taskDescriptionArea)
                    .align(Align.FILL)
                    .comment("Describe the presentation topic, key points, target audience, number of slides, and style preferences")
                    .resizableColumn()
            }.resizableRow()

            row("Related Files:") {
                cell(relatedFilesField)
                    .align(Align.FILL)
                    .comment("Additional files for context (optional)")
            }
        }

        group("Image Generation") {
            row {
                cell(generateImagesCheckbox)
            }

            row("Maximum Images:") {
                cell(imageCountSpinner)
                    .comment("Maximum number of images to generate for key slides (1-10)")
            }

            row("Image Model:") {
                cell(imageModelCombo)
                    .align(Align.FILL)
                    .comment("AI model for image generation")
            }
        }

        group("Model Settings") {
            row("Text Model:") {
                cell(modelCombo)
                    .align(Align.FILL)
                    .comment("AI model for generating presentation content")
            }

            row("Temperature:") {
                cell(temperatureSlider)
                    .align(Align.FILL)
                    .comment("Higher values = more creative, lower = more focused")
                cell(temperatureLabel)
            }

            row {
                cell(autoFixCheckbox)
            }
        }
    }

    override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
        if (htmlFileField.text.isBlank()) {
            return com.intellij.openapi.ui.ValidationInfo("HTML file path is required", htmlFileField)
        }

        if (!htmlFileField.text.endsWith(".html", ignoreCase = true)) {
            return com.intellij.openapi.ui.ValidationInfo("File must have .html extension", htmlFileField)
        } else {
          if (htmlFileField.text.let { root.resolve(it) }.exists()) {
            return com.intellij.openapi.ui.ValidationInfo("HTML file path must not exist", htmlFileField)
          }
        }

        return null
    }

    fun getTaskConfig(): GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData {
        val relatedFiles = relatedFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }

        return GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData(
            files = listOf(htmlFileField.text),
            related_files = relatedFiles,
            task_description = taskDescriptionArea.text,
            generate_images = generateImagesCheckbox.isSelected,
            image_model = imageModelCombo.selectedItem as? String ?: "DallE3",
            max_images = imageCountSpinner.value as Int,
            state = TaskState.Pending
        )
    }

    fun getOrchestrationConfig(): OrchestrationConfig {
        val selectedModel = modelCombo.selectedItem as? String
        val model = selectedModel?.let { modelName ->
            visibleModelsCache.find { it.modelName == modelName }?.toApiChatModel()
        }

        val selectedImageModel = imageModelCombo.selectedItem as? String
        val imageModel = selectedImageModel?.let { modelName ->
            visibleModelsCache.find { it.modelName == modelName }?.toApiChatModel()
        }

        return OrchestrationConfig(
            defaultModel = model ?: AppSettingsState.instance.smartModel
                ?: throw IllegalStateException("No model configured"),
            parsingModel = AppSettingsState.instance.fastModel
                ?: throw IllegalStateException("Fast model not configured"),
            imageChatModel = imageModel ?: AppSettingsState.instance.imageChatModel
                ?: throw IllegalStateException("No image model configured"),
            temperature = temperatureSlider.value / 100.0,
            autoFix = autoFixCheckbox.isSelected,
            workingDir = root.absolutePath,
            shellCmd = listOf(
                if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
            )
        )
    }

    private fun getVisibleModels() =
        ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis.flatMap { apiData ->
            apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.filter { model ->
                model.provider == apiData.provider && 
                model.modelName?.isNotBlank() == true && 
                PlanConfigDialog.isVisible(model)
            } ?: listOf()
        }.distinctBy { it.modelName }.sortedBy { "${it.provider?.name} - ${it.modelName}" }
}