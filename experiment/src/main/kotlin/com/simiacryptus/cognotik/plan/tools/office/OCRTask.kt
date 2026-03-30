package com.simiacryptus.cognotik.plan.tools.office

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.RenderableDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

class OCRTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: OCRTaskExecutionConfigData?
) : AbstractFileTask<OCRTask.OCRTaskExecutionConfigData>(orchestrationConfig, planTask) {

  class OCRTaskExecutionConfigData(
    @Description("DPI for rendering pages (default: 150)") val dpi: Float = 150f,
    @Description("Extract figures as images") val extract_figures: Boolean = false,
    @Description("Extract form fields and metadata") val extract_metadata: Boolean = false,
    @Description("Extract existing text content") val extract_text: Boolean = false,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = OCR.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    state = state
  ) {
    override fun validate(): String? {
      if (listOf<String>(main_file).isEmpty()) return "OCRTask requires at least one file"
      return ValidatedObject.validateFields(this)
    }
  }

  data class FigureLocation(
    @Description("Description/Name of the figure") val name: String = "",
    @Description("X coordinate (0-1000)") val x: Int = 0,
    @Description("Y coordinate (0-1000)") val y: Int = 0,
    @Description("Width (0-1000)") val width: Int = 0,
    @Description("Height (0-1000)") val height: Int = 0
  )

  data class PageAnalysis(
    @Description("List of figures/images/charts found on the page") val figures: List<FigureLocation> = emptyList(),
    @Description("Form fields, key-value pairs, and other metadata") val metadata: Map<String, String> = emptyMap()
  )


  override fun promptSegment(): String {
    return """
OCR - Convert documents (PDF, Images) to Markdown text.
* Extracts text from images and PDFs using Vision models.
* Preserves formatting as Markdown.
* Optionally extracts figures as images and metadata/form fields.
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
    val files = executionConfig?.let { listOf(it.main_file) } ?: emptyList()

    if (files.isEmpty()) {
      val msg = "No files specified"
      task.safeComplete(msg, log)
      resultFn(msg)
      return
    }

    val transcript = task.newUserFileStream(transcriptFile())
















    task.ui.pool.submit {
      val tabs = TabbedDisplay(task)
      val summaryTask = tabs.newTask("Summary")
      val results = mutableMapOf<File, String>()
      try {
        log.info("Starting OCR task for ${files.size} files")
        transcript?.write("## OCR Task Started\nProcessing files: ${files.joinToString(", ")}\n".toByteArray())
        files.forEachIndexed { index, filePath ->
          val file = root.resolve(filePath).toFile()
          if (!file.exists()) {
            val errorMsg = "❌ File not found: $filePath"
            log.warn("File not found: $filePath")
            task.add(errorMsg.renderMarkdown(), additionalClasses = "text-danger")
            transcript?.write("- [ERROR] File not found: $filePath\n".toByteArray())
            return@forEachIndexed
          }
          val fileTask = tabs.newTask(file.name)
          fileTask.header("Processing ${file.name}", level = 3)
          transcript?.write("### Processing ${file.name}\n".toByteArray())
          val sb = StringBuilder()
          if (executionConfig?.extract_text == true && file.extension.equals("pdf", ignoreCase = true)) {
            try {
              PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(doc)
                val textFile = File(file.parentFile, file.nameWithoutExtension + "_text.txt")
                textFile.writeText(text)
                fileTask.add("✅ Extracted text to `${textFile.name}`".renderMarkdown())
                transcript?.write("- Extracted raw text to `${textFile.name}`\n".toByteArray())
              }
            } catch (e: Exception) {
              log.warn("Failed to extract text from PDF: ${file.name}", e)
              fileTask.add(
                "❌ Failed to extract text: ${e.message}".renderMarkdown(),
                additionalClasses = "text-danger"
              )
              transcript?.write("- [ERROR] Text extraction failed: ${e.message}\n".toByteArray())
            }
          }
          try {
            file.getDocumentReader().use { reader ->
              if (reader is PaginatedDocumentReader && reader is RenderableDocumentReader) {
                val pageCount = reader.getPageCount()
                val progressBuffer = fileTask.add("Initializing...")
                val allMetadata = mutableListOf<PageAnalysis>()
                for (page in 0 until pageCount) {
                  progressBuffer?.setLength(0)
                  progressBuffer?.append("Processing page ${page + 1}/$pageCount...")
                  fileTask.update()
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
                  transcript?.write("<details><summary>Page ${page + 1} OCR Result</summary>\n\n$response\n\n</details>\n".toByteArray())
                  if (executionConfig?.extract_figures == true || executionConfig?.extract_metadata == true) {
                    val config = executionConfig!!
                    try {
                      val analysisAgent = ParsedImageAgent(
                        resultClass = PageAnalysis::class.java,
                        model = orchestrationConfig.defaultSmart.getChildClient(task),
                        prompt = """
                                                    Analyze this page.
                                                    ${if (config.extract_figures) "Identify any figures, charts, or diagrams. Provide bounding boxes (0-1000 scale)." else ""}
                                                    ${if (config.extract_metadata) "Extract any form fields or key-value metadata." else ""}
                                                """.trimIndent()
                      )
                      val analysis = analysisAgent.answer(
                        listOf(ImageAndText(image = image, text = "Analyze page"))
                      ).obj
                      if (config.extract_metadata) {
                        allMetadata.add(analysis)
                      }
                      if (config.extract_figures) {
                        val figuresDir =
                          File(file.parentFile, "${file.nameWithoutExtension}_figures")
                        figuresDir.mkdirs()
                        analysis.figures.forEachIndexed { i, fig ->
                          val scaledX = (fig.x * image.width / 1000.0).toInt()
                            .coerceIn(0, image.width - 1)
                          val scaledY = (fig.y * image.height / 1000.0).toInt()
                            .coerceIn(0, image.height - 1)
                          val scaledW = (fig.width * image.width / 1000.0).toInt()
                            .coerceAtMost(image.width - scaledX)
                          val scaledH = (fig.height * image.height / 1000.0).toInt()
                            .coerceAtMost(image.height - scaledY)
                          if (scaledW > 10 && scaledH > 10) {
                            val subImage =
                              image.getSubimage(scaledX, scaledY, scaledW, scaledH)
                            val safeName =
                              fig.name.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50)
                            val figFile =
                              File(figuresDir, "p${page + 1}_${i + 1}_$safeName.png")
                            ImageIO.write(subImage, "png", figFile)
                          }
                        }
                        if (analysis.figures.isNotEmpty()) {
                          fileTask.add("  - Saved ${analysis.figures.size} figures from page ${page + 1}".renderMarkdown())
                        }
                      }
                    } catch (e: Exception) {
                      log.warn("Analysis failed for page $page of ${file.name}", e)
                    }
                  }
                }
                if (executionConfig?.extract_metadata == true && allMetadata.isNotEmpty()) {
                  val metaFile = File(file.parentFile, "${file.nameWithoutExtension}_metadata.json")
                  val mapper = jacksonObjectMapper().writerWithDefaultPrettyPrinter()
                  metaFile.writeText(mapper.writeValueAsString(allMetadata))
                  fileTask.add("✅ Saved metadata to `${metaFile.name}`".renderMarkdown())
                  transcript?.write("- Saved metadata to `${metaFile.name}`\n".toByteArray())
                }
                progressBuffer?.setLength(0)
                progressBuffer?.append("✅ Processed $pageCount pages")
                fileTask.update()
                fileTask.add(sb.toString().renderMarkdown())
                fileTask.complete()
              } else {
                fileTask.add(
                  "❌ Cannot read document: `${file.name}`".renderMarkdown(),
                  additionalClasses = "text-danger"
                )
                return@forEachIndexed
              }
            }
            val outputFileName = file.nameWithoutExtension + ".md"
            val outputFile = File(file.parentFile, outputFileName)
            results[outputFile] = sb.toString()
          } catch (e: Exception) {
            log.error("Error processing ${file.name}", e)
            task.error(e)
            transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
          }
        }
        if (results.isEmpty()) {
          summaryTask.safeComplete("No documents processed successfully", log)
          resultFn("Failed to process documents")
          return@submit
        }
        if (orchestrationConfig.autoFix) {
          results.forEach { (file, content) ->
            file.writeText(content)
            summaryTask.add("✅ Saved `${file.name}`".renderMarkdown())
            transcript?.write("- Auto-saved `${file.name}`\n".toByteArray())
          }
          val summary = "OCR Completed for ${results.size} files."
          summaryTask.safeComplete(summary, log)
          resultFn(summary)
        } else {
          val semaphore = Semaphore(0)
          summaryTask.header("Review OCR Results", level = 3)
          results.forEach { (file, content) ->
            summaryTask.expandable("Preview: ${file.name}", "<pre>${content.take(500)}...</pre>")
          }
          val footer = acceptButtonFooter(task.ui) {
            results.forEach { (file, content) ->
              file.writeText(content)
              summaryTask.add("✅ Saved `${file.name}`".renderMarkdown())
              transcript?.write("- User approved and saved `${file.name}`\n".toByteArray())
            }
            semaphore.release()
          }
          summaryTask.add(footer)
          summaryTask.update()
          semaphore.acquire()
          val summary = "OCR Completed for ${results.size} files."
          task.safeComplete(summary, log)
          resultFn(summary)
        }
      } catch (e: Exception) {
        log.error("OCR Task failed", e)
        task.error(e)
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        resultFn("Error: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(OCRTask::class.java)

    @JvmStatic
    val OCR = TaskType(
      name = "OCRTask",
      category = "File",
      taskClass = OCRTask::class.java,
      executionConfigClass = OCRTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Convert documents (PDF, Images) to Markdown text",
      tooltipHtml = """
          Uses Vision models to extract text and formatting from documents.
          <ul>
          <li>Supports PDF and Image files</li>
          <li>Converts to Markdown format</li>
          <li>Preserves layout and structure where possible</li>
          <li>Optionally extracts figures and metadata</li>
          </ul>
          """
    )
  }
}