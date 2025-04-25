package com.simiacryptus.cognotik.apps.parse

import com.google.common.util.concurrent.Futures
import com.simiacryptus.cognotik.apps.parse.ParsingModel.DocumentData
import com.simiacryptus.cognotik.apps.parse.ProgressState.Companion.progressBar
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.util.JsonUtil
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.min

open class DocumentParserApp(
  applicationName: String = "Document Extractor",
  path: String = "/pdfExtractor",
  val parsingModel: ParsingModel<DocumentData>,
  val reader: (File) -> DocumentReader = {
    when {
      it.name.endsWith(".pdf", ignoreCase = true) -> PDFReader(it)
      it.name.endsWith(".html", ignoreCase = true) -> HTMLReader(it)
      it.name.endsWith(".htm", ignoreCase = true) -> HTMLReader(it)
      else -> TextReader(it)
    }
  },
  val fileInputs: List<Path>? = null,
  val fastMode: Boolean = true
) : ApplicationServer(
  applicationName = applicationName,
  path = path,
  showMenubar = true
) {
  override val singleInput: Boolean = true
  override val stickyInput: Boolean = false

  override fun newSession(user: User?, session: Session): SocketManager {
    val socketManager = super.newSession(user, session)
    val ui = (socketManager as ApplicationSocketManager).applicationInterface
    val settings = getSettings(session, user, Settings::class.java) ?: Settings()
    val app = this
    if (null == (fileInputs ?: settings.fileInputs)) {
      log.info("No file input provided")
    } else (fileInputs ?: settings.fileInputs).apply {
      val progressBar = progressBar(ui.newTask())
      socketManager.pool.submit {
        run(
          task = ui.newTask(),
          ui = ui,
          fileInputs = (app.fileInputs ?: settings.fileInputs?.map { File(it).toPath() } ?: error("File input not provided")),
          maxPages = settings.maxPages.coerceAtMost(Int.MAX_VALUE),
          settings = settings,
          pagesPerBatch = settings.pagesPerBatch,
          progressBar = progressBar,
          api = ApplicationServices.clientManager.getChatClient(session, user),
        )
      }
    }
    return socketManager
  }

  override fun userMessage(session: Session, user: User?, userMessage: String, ui: ApplicationInterface, api: API) {
    val settings = getSettings(session, user, Settings::class.java) ?: Settings()
    ui.socketManager!!.pool.submit {
      run(
        task = ui.newTask(),
        ui = ui,
        fileInputs = (this.fileInputs ?: settings.fileInputs?.map<String, Path> { File(it).toPath() } ?: error("File input not provided")),
        maxPages = settings.maxPages.coerceAtMost(Int.MAX_VALUE),
        settings = settings,
        pagesPerBatch = settings.pagesPerBatch,
        api = ApplicationServices.clientManager.getChatClient(session, user),
      )
    }
  }

  private fun run(
    task: SessionTask,
    ui: ApplicationInterface,
    fileInputs: List<Path>,
    maxPages: Int,
    settings: Settings,
    pagesPerBatch: Int,
    api: ChatClient,
    progressBar: ProgressState? = null
  ) {
    try {
      // Validate inputs
      if (fileInputs.isEmpty()) {
        throw IllegalArgumentException("No input files provided")
      }

      task.header("Knowledge Extractor", 2)
      val api = api.getChildClient(task)
      // Create output directory
      val outputDir = root.resolve("output").apply<File> { mkdirs() }
      if (!outputDir.exists()) {
        throw IOException("Failed to create output directory: $outputDir")
      }

      val docTabs = TabbedDisplay(task)
      fileInputs.map { it.toFile() }.forEach { file ->
        if (!file.exists()) {
          task.error(ui, IllegalArgumentException("File not found: $file"))
          return
        }
        ui.socketManager?.pool?.submit {
          val docTask = ui.newTask(false).apply { docTabs[file.toString()] = this.placeholder }
          val pageTabs = TabbedDisplay(docTask)
          val outputDir = root.resolve("output").apply<File> { mkdirs() }
          reader(file).use<DocumentReader, Unit> { reader ->
            if (reader is TextReader) {
              reader.configure(settings)
            }
            var previousPageText = "" // Keep this for context
            val pageCount = minOf(reader.getPageCount(), maxPages)
            val pageSets = 0 until pageCount step pagesPerBatch
            progressBar?.add(0.0, pageCount.toDouble())
            var runningDocument = parsingModel.newDocument()
            val futures = pageSets.toList().mapNotNull { batchStart ->
              val pageTask = ui.newTask(false)
              val api = api.getChildClient(pageTask)
              try {
                val batchEnd = min(batchStart + pagesPerBatch, pageCount)
                val text = reader.getText(batchStart, batchEnd)
                val label = if ((batchStart + 1) != batchEnd) "Pages ${batchStart}-${batchEnd}" else "Page ${batchStart}"
                val pageTabs = TabbedDisplay(pageTask.apply<SessionTask> { pageTabs[label] = placeholder })
                if (settings.showImages) {
                  for (pageIndex in batchStart until batchEnd) {
                    try {
                      val image = reader.renderImage(pageIndex, settings.dpi)
                      ui.newTask(false).apply<SessionTask> {
                        pageTabs["Image ${1 + (pageIndex - batchStart)}"] = placeholder
                        image(image)
                      }
                      if (settings.saveImageFiles) {
                        val imageFile =
                          outputDir.resolve("page_${pageIndex}.${settings.outputFormat.lowercase(Locale.getDefault())}")
                        when (settings.outputFormat.uppercase(Locale.getDefault())) {
                          "PNG" -> ImageIO.write(image, "PNG", imageFile)
                          "JPEG", "JPG" -> ImageIO.write(image, "JPEG", imageFile)
                          "GIF" -> ImageIO.write(image, "GIF", imageFile)
                          "BMP" -> ImageIO.write(image, "BMP", imageFile)
                          else -> throw IllegalArgumentException("Unsupported output format: ${settings.outputFormat}")
                        }
                      }
                    } catch (e: Throwable) {
                      log.info("Error rendering image for page $pageIndex", e)
                    }
                  }
                }
                if (text.isBlank()) {
                  pageTask.error(ui, IllegalArgumentException("No text extracted from pages $batchStart to $batchEnd"))
                  return@mapNotNull null
                }
                if (settings.saveTextFiles) {
                  outputDir.resolve("pages_${batchStart}_to_${batchEnd}_text.txt").writeText(text)
                }
                val promptList = mutableListOf<String>()
                promptList.add(
                  "# Prior Text\n\nFOR INFORMATIVE CONTEXT ONLY. DO NOT COPY TO OUTPUT.\n```text\n$previousPageText\n```"
                )
                promptList.add(
                  "# Current Page\n\n```text\n$text\n```"
                )
                previousPageText = text
                if (fastMode) {
                  ui.socketManager.pool.submit<DocumentData?> {
                    val jsonResult = parsingModel.getFastParser(api)(promptList.toList<String>().joinToString<String>("\n\n"))
                    handleParseResult(settings, outputDir, batchStart, batchEnd, jsonResult, ui, pageTabs, text, pageTask, progressBar)
                  }
                } else {
                  val jsonResult = parsingModel.getSmartParser(api)(runningDocument, promptList.toList<String>().joinToString<String>("\n\n"))
                  runningDocument = handleParseResult(settings, outputDir, batchStart, batchEnd, jsonResult, ui, pageTabs, text, pageTask, progressBar)!!
                  Futures.immediateFuture(runningDocument)
                }
              } catch (e: Throwable) {
                pageTask.error(ui, e)
                null
              }
            }.toTypedArray()
            val finalDocument = futures.mapNotNull {
              try {
                it.get()
              } catch (e: Throwable) {
                task.error(ui, e)
                null
              }
            }.fold(parsingModel.newDocument())
            { runningDocument, it -> parsingModel.merge(runningDocument, it) }
            docTask.add(
              MarkdownUtil.renderMarkdown(
                "## Document JSON\n\n```json\n${JsonUtil.toJson(finalDocument)}\n```\n\nExtracted files are saved in: ${outputDir.absolutePath}", ui = ui
              )
            )
            if (settings.saveFinalJson) {
              val finalJsonFile =
                file.parentFile.resolve(file.name.reversed().split(delimiters = arrayOf("."), false, 2).joinToString("_").reversed() + ".parsed.json")
              finalJsonFile.writeText(JsonUtil.toJson(finalDocument))
              docTask.add(MarkdownUtil.renderMarkdown("Final JSON saved to: ${finalJsonFile.absolutePath}", ui = ui))
            }
          }
        }
      }
    } catch (e: Throwable) {
      task.error(ui, e)
    }
  }

  private fun handleParseResult(
    settings: Settings,
    outputDir: File,
    batchStart: Int,
    batchEnd: Int,
    jsonResult: DocumentData,
    ui: ApplicationInterface,
    pageTabs: TabbedDisplay,
    text: String,
    pageTask: SessionTask,
    progressBar: ProgressState?
  ): DocumentData? {
    // Generate consistent file name pattern
    val fileBaseName = generateFileBaseName(batchStart, batchEnd)

    return try {
      if (settings.saveTextFiles) {
        val jsonFile = outputDir.resolve(generateJsonFileName(fileBaseName))
        jsonFile.writeText(JsonUtil.toJson(jsonResult))
      }
      ui.newTask(false).apply<SessionTask> {
        pageTabs["Text"] = placeholder
        add(
          MarkdownUtil.renderMarkdown(
            generateMarkdownCodeBlock("text", text, settings),
            ui = ui
          )
        )
      }
      ui.newTask(false).apply<SessionTask> {
        pageTabs["JSON"] = placeholder
        add(
          MarkdownUtil.renderMarkdown(
            generateMarkdownCodeBlock("json", JsonUtil.toJson(jsonResult), settings),
            ui = ui
          )
        )
      }
      jsonResult
    } catch (e: Throwable) {
      pageTask.error(ui, e)
      null
    } finally {
      progressBar?.add(1.0, 0.0)
      pageTask.complete()
    }
  }

  private fun generateFileBaseName(batchStart: Int, batchEnd: Int): String =
    "pages_${batchStart}_to_${batchEnd}"

  private fun generateJsonFileName(baseName: String): String =
    "${baseName}_content.json"

  private fun generateMarkdownCodeBlock(language: String, content: String, settings: Settings): String =
    if (settings.addLineNumbers) {
      val lines = content.lines()
      val maxDigits = lines.size.toString().length
      val numberedLines = lines.mapIndexed { index, line ->
        String.format("%${maxDigits}d | %s", index + 1, line)
      }.joinToString("\n")
      "\n```$language\n$numberedLines\n```\n"
    } else {
      "\n```$language\n$content\n```\n"
    }

  data class Settings(
    val dpi: Float = 120f,
    val maxPages: Int = Int.MAX_VALUE,
    val outputFormat: String = "PNG",
    val fileInputs: List<String>? = null,
    val showImages: Boolean = true,
    val pagesPerBatch: Int = 1,
    val saveImageFiles: Boolean = false,
    val saveTextFiles: Boolean = false,
    val saveFinalJson: Boolean = true,
    val fastMode: Boolean = true,
    val addLineNumbers: Boolean = false
  )

  override val settingsClass: Class<*> get() = Settings::class.java

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T = Settings() as T

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(DocumentParserApp::class.java)
  }

  interface DocumentReader : AutoCloseable {
    fun getPageCount(): Int
    fun getText(startPage: Int, endPage: Int): String
    fun renderImage(pageIndex: Int, dpi: Float): BufferedImage
  }

}