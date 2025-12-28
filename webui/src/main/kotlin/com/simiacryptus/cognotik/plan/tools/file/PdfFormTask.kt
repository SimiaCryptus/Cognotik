package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ParsedAgent

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.apache.pdfbox.Loader
import kotlin.io.path.exists

class PdfFormTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: PdfFormExecutionConfig?
) : AbstractTask<PdfFormTask.PdfFormExecutionConfig, PdfFormTask.PdfFormTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class PdfFormTypeConfig(
        @Description("The path to the PDF template file to be used for all executions of this task type")
        val template_file: String? = null,
        task_type: String? = PdfForm.name,
        name: String? = null
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name
    )

    class PdfFormExecutionConfig(
        @Description("The output filename for the filled PDF")
        val output_file: String? = null,
        @Description("Map of field names (as defined in the template schema) to the values to fill")
        val fields: Map<String, String>? = null,
        @Description("Whether to flatten the form fields after filling (making them read-only)")
        val flatten: Boolean = true,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = PdfForm.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class FormData(
        @Description("Map of field names to values extracted from context")
        val fields: Map<String, String> = emptyMap()
    ) : ValidatedObject {
        override fun validate(): String? = null
    }


    override fun promptSegment(): String {
        val templatePath = typeConfig?.template_file
        if (templatePath.isNullOrBlank()) {
            return "PdfForm - Configuration Error: No template_file specified in TypeConfig."
        }

        val templateFile = root.resolve(templatePath)
        if (!templateFile.exists()) {
            return "PdfForm - Configuration Error: Template file '$templatePath' not found in working directory."
        }

        return try {
            Loader.loadPDF(templateFile.toFile()).use { doc ->
                val acroForm = doc.documentCatalog.acroForm
                val fieldList = acroForm?.fields?.joinToString("\n") { field ->
                    "  - \"${field.fullyQualifiedName}\" (${field.javaClass.simpleName.replace("PD", "")})"
                } ?: "  (No fields found)"

                """
PdfForm - Fill out a PDF form based on the template: $templatePath

The following fields are available in the PDF template:
$fieldList

* Specify the 'output_file' path.
* Provide the 'fields' map with values for the fields listed above.
                """.trimIndent()
            }
        } catch (e: Exception) {
            log.warn("Error reading PDF template for prompt generation", e)
            "PdfForm - Error reading template '$templatePath': ${e.message}"
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript("pdf_fill_log")
        try {
            val templatePath = typeConfig?.template_file
                ?: throw IllegalStateException("Template file not configured in TaskTypeConfig")

            val templateFile = root.resolve(templatePath).toFile()
            if (!templateFile.exists()) throw IllegalStateException("Template file not found: $templatePath")

            val outputPath = executionConfig?.output_file ?: "filled_form.pdf"
            val outputFile = root.resolve(outputPath).toFile()

            val availableFields = Loader.loadPDF(templateFile).use { doc ->
                doc.documentCatalog.acroForm?.fields?.map { it.fullyQualifiedName } ?: emptyList()
            }

            val api = defaultSmart
            val extractedFields = if (api != null && messages.isNotEmpty()) {
                task.add("Analyzing context to extract form data...")
                val parsingChatter = defaultFast.getChildClient(task)
                val defaultChatter = api.getChildClient(task)

                val prompt = """
Analyze the provided context and extract values for the following PDF form fields.

Available Fields:
${availableFields.joinToString("\n") { "* $it" }}

Context:
${messages.joinToString("\n\n")}

Return a JSON object with a 'fields' map containing the extracted values.
Only include fields where a value can be confidently determined from the context.
                """.trimIndent()

                val parsedAgent = ParsedAgent(
                    resultClass = FormData::class.java,
                    prompt = prompt,
                    model = defaultChatter,
                    parsingChatter = parsingChatter,
                    temperature = 0.1
                )

                parsedAgent.answer(listOf(prompt)).obj.fields
            } else {
                emptyMap()
            }

            val configFields = executionConfig?.fields ?: emptyMap()
            val fieldData = extractedFields + configFields

            transcript?.write("# PDF Form Fill Execution\n".toByteArray())
            transcript?.write("Template: $templatePath\n".toByteArray())
            transcript?.write("Output: $outputPath\n".toByteArray())
            transcript?.write("## Field Data\n```json\n${fieldData}\n```\n".toByteArray())

            task.add("Preparing to fill ${fieldData.size} fields into $outputPath...")

            acceptButtonFooter(task.ui) {
                try {
                    Loader.loadPDF(templateFile).use { doc ->
                        val acroForm = doc.documentCatalog.acroForm
                        if (acroForm == null) {
                            throw IllegalStateException("No AcroForm found in template PDF")
                        }

                        val missingFields = mutableListOf<String>()
                        fieldData.forEach { (key, value) ->
                            val field = acroForm.getField(key)
                            if (field != null) {
                                field.setValue(value)
                            } else {
                                missingFields.add(key)
                            }
                        }

                        if (missingFields.isNotEmpty()) {
                            val msg = "Warning: The following fields were not found in the PDF: $missingFields"
                            log.warn(msg)
                            transcript?.write("\n$msg\n".toByteArray())
                        }

                        if (executionConfig?.flatten == true) {
                            acroForm.flatten()
                        }

                        doc.save(outputFile)
                    }

                    val successMsg = "Successfully created $outputPath with ${fieldData.size} fields filled."
                    transcript?.write("\n## Success\n$successMsg\n".toByteArray())
                    resultFn(successMsg)

                } catch (e: Exception) {
                    log.error("Failed to fill PDF", e)
                    transcript?.write("\n## Error\n${e.message}\n".toByteArray())
                    throw e
                }
            }
        } catch (e: Exception) {
            resultFn("Error executing PDF task: ${e.message}")
        } finally {
            transcript?.close()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PdfFormTask::class.java)

        val PdfForm = TaskType(
            "PdfForm",
            "File",
            PdfFormExecutionConfig::class.java,
            PdfFormTypeConfig::class.java,
            "Fills out a specific PDF form template with provided data.",
            """
            Fills fields in a pre-configured PDF template.
            <ul>
              <li><b>Requires:</b> A template PDF file defined in the global Type Config.</li>
              <li><b>Output:</b> A new PDF file with the fields populated.</li>
              <li>Automatically lists available fields from the template to the Planner.</li>
            </ul>
            """
        )
    }
}