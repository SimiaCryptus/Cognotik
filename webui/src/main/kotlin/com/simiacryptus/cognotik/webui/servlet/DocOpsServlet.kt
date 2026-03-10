package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.chat.model.AnthropicModels
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.util.DocProcessor
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.UpdateModes
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servlet that processes DocOps rendering requests.
 *
 * Accepts requests with the following query parameters:
 * - sessionId: The session ID to resolve the working directory
 * - doc: The path to the markdown documentation file (relative to session root)
 * - target: (Optional) The target output file path (relative to session root)
 * - mode: (Optional) The update mode to use (default: PatchExisting)
 *
 * Example:
 *   GET /docops?sessionId=U-20260310-i2oc2f&doc=ops/foo.md&target=output.md
 *
 * The servlet parses the specified markdown file for frontmatter specifications
 * and executes the resulting documentation processing tasks.
 */
class DocOpsServlet(
  private val dataStorage: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
  private val smartModel: ChatModel = AnthropicModels.Claude45Haiku,
  private val fastModel: ChatModel = smartModel,
) : HttpServlet() {
  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    doPost(req, resp)
  }

  override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    val sessionId = req.getParameter("sessionId")
    val docPath = req.getParameter("doc")
    if (sessionId.isNullOrBlank()) {
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.contentType = "application/json"
      resp.writer.write("""{"error": "Missing required parameter: sessionId"}""")
      return
    }
    if (docPath.isNullOrBlank()) {
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.contentType = "application/json"
      resp.writer.write("""{"error": "Missing required parameter: doc"}""")
      return
    }
    val modeName = req.getParameter("mode") ?: "PatchExisting"
    val updateMode = UpdateModes.fromName(modeName) ?: UpdateModes.PatchExisting
    try {
      val session = Session(sessionId)
      val user = UserSettingsManager.defaultUser
      val sessionDir = dataStorage.getSessionDir(user, session)
      if (!sessionDir.exists() || !sessionDir.isDirectory) {
        resp.status = HttpServletResponse.SC_NOT_FOUND
        resp.contentType = "application/json"
        resp.writer.write("""{"error": "Session directory not found: $sessionId"}""")
        return
      }
      val docFile = sessionDir.resolve(docPath)
      if (!docFile.exists() || !docFile.isFile) {
        resp.status = HttpServletResponse.SC_NOT_FOUND
        resp.contentType = "application/json"
        resp.writer.write("""{"error": "Document file not found: $docPath"}""")
        return
      }
// Validate the doc file is within the session directory
      if (!docFile.canonicalPath.startsWith(sessionDir.canonicalPath)) {
        resp.status = HttpServletResponse.SC_FORBIDDEN
        resp.contentType = "application/json"
        resp.writer.write("""{"error": "Access denied: document path is outside session directory"}""")
        return
      }
      val targetPath = req.getParameter("target")
      log.info("DocOps request: session=$sessionId, doc=$docPath, target=$targetPath, mode=$modeName")
      val docProcessor = DocProcessor(
        root = sessionDir,
        docsFolder = sessionDir,
        updateMode = updateMode,
        fastModel = fastModel,
        smartModel = smartModel,
        autoFix = true,
      )
      val docSpec = docProcessor.parseMarkdownWithFrontmatter(docFile)
      if (docSpec == null) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"error": "No valid frontmatter found in document: $docPath. Ensure the file has 'specifies', 'documents', 'transforms', or 'generates' frontmatter."}""")
        return
      }
      val allTasks = docProcessor.getAll(docFile)
// If a specific target is requested, filter tasks to only that target
      val tasksToRun = if (!targetPath.isNullOrBlank()) {
        val targetFile = sessionDir.resolve(targetPath).canonicalFile
        allTasks.filter { task ->
          task.data.files?.any { filePath ->
            try {
              File(filePath).canonicalFile == targetFile
            } catch (e: Exception) {
              false
            }
          } ?: false
        }
      } else {
        allTasks
      }
      if (tasksToRun.isEmpty()) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        val msg = if (!targetPath.isNullOrBlank()) {
          "No tasks found for target '$targetPath' in document '$docPath'"
        } else {
          "No tasks found in document '$docPath'"
        }
        resp.writer.write("""{"error": "$msg"}""")
        return
      }
      log.info("Executing ${tasksToRun.size} DocOps task(s) for session $sessionId")
      val cancelFlag = AtomicBoolean(false)
      val sessions = mutableListOf<com.simiacryptus.cognotik.platform.Session>()
      val resultSessions = docProcessor.runAll(
        fileMods = tasksToRun,
        cancelFlag = cancelFlag,
        onNewSession = { s -> sessions += s }
      )
// Collect results
      val processedFiles = tasksToRun.flatMap { task ->
        task.data.relative_files ?: emptyList()
      }.distinct()
      resp.status = HttpServletResponse.SC_OK
      resp.contentType = "application/json"
      resp.characterEncoding = "UTF-8"
      resp.writer.write(buildString {
        append("""{"success": true""")
        append(""", "tasksExecuted": ${tasksToRun.size}""")
        append(""", "sessions": ${resultSessions.size}""")
        append(""", "processedFiles": [""")
        append(processedFiles.joinToString(", ") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" })
        append("]")
// If a single target was requested, include its content if it now exists
        if (!targetPath.isNullOrBlank()) {
          val targetFile = sessionDir.resolve(targetPath)
          if (targetFile.exists() && targetFile.isFile) {
            val content = targetFile.readText()
            val escapedContent = content
              .replace("\\", "\\\\")
              .replace("\"", "\\\"")
              .replace("\n", "\\n")
              .replace("\r", "\\r")
              .replace("\t", "\\t")
            append(""", "content": "$escapedContent"""")
          }
        }
        append("}")
      })
      log.info("DocOps processing complete: ${tasksToRun.size} tasks, ${resultSessions.size} sessions")
    } catch (e: Exception) {
      log.error("Error processing DocOps request", e)
      resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.contentType = "application/json"
      val errorMsg = (e.message ?: "Unknown error").replace("\"", "\\\"").replace("\n", " ")
      resp.writer.write("""{"error": "Processing failed: $errorMsg"}""")
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(DocOpsServlet::class.java)
    fun getCookie(req: HttpServletRequest): String? {
      return req.cookies?.find { it.name == "sessionId" }?.value
    }
  }
}