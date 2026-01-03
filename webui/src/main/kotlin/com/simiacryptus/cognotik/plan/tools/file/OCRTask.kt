package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.input.PaginatedDocumentReader
import com.simiacryptus.cognotik.input.RenderableDocumentReader
import com.simiacryptus.cognotik.input.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Semaphore

class OCRTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: OCRTaskExecutionConfigData?
) : AbstractFileTask<OCRTask.OCRTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class OCRTaskExecutionConfigData(
        @Description("The files to process (PDF or images)") files: List<String>? = null,
        @Description("DPI for rendering pages (default: 150)") val dpi: Float = 150f,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = OCR.name,
        task_description = task_description,
        files = files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            if (files.isNullOrEmpty()) return "OCRTask requires at least one file"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
OCR - Convert documents (PDF, Images) to Markdown text.
* Extracts text from images and PDFs using Vision models.
* Preserves formatting as Markdown.
* Saves output to a .md file with the same name.
""".trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val files = executionConfig?.files ?: emptyList()

        if (files.isEmpty()) {
            val msg = "No files specified"
            task.safeComplete(msg, log)
            resultFn(msg)
            return
        }

        val transcript = task.transcript()
        val results = mutableMapOf<File, String>()

        try {
            files.forEach { filePath ->
                val file = root.resolve(filePath).toFile()
                if (!file.exists()) {
                    log.warn("File not found: $filePath")
                    task.add("❌ File not found: $filePath", additionalClasses = "text-danger")
                    return@forEach
                }

                task.header("Processing ${file.name}", level = 3)
                val sb = StringBuilder()
                val progressTask = task.newTask()

                try {
                    file.getDocumentReader().use { reader ->
                        if (reader is PaginatedDocumentReader && reader is RenderableDocumentReader) {
                            val pageCount = reader.getPageCount()
                            val progressBuffer = progressTask.add("Initializing...")

                            for (page in 0 until pageCount) {
                                progressBuffer?.setLength(0)
                                progressBuffer?.append("Processing page ${page + 1}/$pageCount...")
                                progressTask.update()

                                val image = reader.renderImage(page, executionConfig!!.dpi)

                                val response = orchestrationConfig.defaultSmart.chat(
                                    listOfNotNull(
                                        ModelSchema.ChatMessage(
                                            ModelSchema.Role.system,
                                            listOf(ModelSchema.ContentPart("You are an OCR engine. Convert the image to Markdown. Output only the markdown content."))
                                        ),
                                        ModelSchema.ChatMessage(
                                            ModelSchema.Role.user,
                                            listOf(
                                                ModelSchema.ContentPart("Convert this page")
                                                    .apply { this.image = image })
                                        )
                                    )
                                ).choices.first().message?.content ?: ""

                                sb.append(response).append("\n\n")
                            }
                            progressBuffer?.setLength(0)
                            progressBuffer?.append("✅ Processed $pageCount pages")
                            progressTask.update()
                            progressTask.complete()
                        } else {
                            task.add("❌ Cannot read document: ${file.name}", additionalClasses = "text-danger")
                            return@forEach
                        }
                    }

                    val outputFileName = file.nameWithoutExtension + ".md"
                    val outputFile = File(file.parentFile, outputFileName)
                    results[outputFile] = sb.toString()

                } catch (e: Exception) {
                    log.error("Error processing ${file.name}", e)
                    task.error(e)
                }
            }

            if (results.isEmpty()) {
                task.safeComplete("No documents processed successfully", log)
                resultFn("Failed to process documents")
                return
            }

            if (orchestrationConfig.autoFix) {
                results.forEach { (file, content) ->
                    file.writeText(content)
                    task.add("✅ Saved <code>${file.name}</code>")
                }
                val summary = "OCR Completed for ${results.size} files."
                task.safeComplete(summary, log)
                resultFn(summary)
            } else {
                val semaphore = Semaphore(0)
                task.header("Review OCR Results", level = 3)
                results.forEach { (file, content) ->
                    task.expandable("Preview: ${file.name}", "<pre>${content.take(500)}...</pre>")
                }

                task.add(task.ui.hrefLink("💾 Save All", "btn btn-success") {
                    results.forEach { (file, content) ->
                        file.writeText(content)
                        task.add("✅ Saved <code>${file.name}</code>")
                    }
                    semaphore.release()
                })

                semaphore.acquire()
                val summary = "OCR Completed for ${results.size} files."
                task.safeComplete(summary, log)
                resultFn(summary)
            }

        } catch (e: Exception) {
            log.error("OCR Task failed", e)
            task.error(e)
            resultFn("Error: ${e.message}")
        } finally {
            transcript?.close()
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OCRTask::class.java)
        val OCR = TaskType(
            "OCRTask",
            "File Operations",
            OCRTask::class.java,
            OCRTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Convert documents (PDF, Images) to Markdown text",
            """
Uses Vision models to extract text and formatting from documents.
<ul>
<li>Supports PDF and Image files</li>
<li>Converts to Markdown format</li>
<li>Preserves layout and structure where possible</li>
</ul>
"""
        )
    }
}