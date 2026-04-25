package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.util.DocProcessor
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.UpdateModes
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
 * Optional model override parameters:
 * - smartModel: (Optional) Model ID to use as the smart/primary model (e.g., "claude-3-5-sonnet-20241022")
 * - fastModel: (Optional) Model ID to use as the fast/secondary model (e.g., "claude-3-5-haiku-20241022")
 * - imageModel: (Optional) Model ID to use as the image model
 *
 * The servlet parses the specified markdown file for frontmatter specifications
 * and executes the resulting documentation processing tasks.
 */
class DocProcessorServlet(
  private val dataStorage: StorageInterface = ApplicationServices.fileApplicationServices().dataStorageFactory,
) : HttpServlet() {
  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    doPost(req, resp)
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    val sessionId = request.getParameter("sessionId")
    val docPath = request.getParameter("doc")
    if (sessionId.isNullOrBlank()) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.contentType = "application/json"
      response.writer.write("""{"error": "Missing required parameter: sessionId"}""")
      return
    }
    if (docPath.isNullOrBlank()) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.contentType = "application/json"
      response.writer.write("""{"error": "Missing required parameter: doc"}""")
      return
    }
    val modeName = request.getParameter("mode") ?: "PatchExisting"
    val updateMode = UpdateModes.Companion.fromName(modeName) ?: UpdateModes.PatchExisting
    val effectiveSmartModel = resolveModel(request.getParameter("smartModel")) ?: throw IllegalArgumentException("Invalid or missing smartModel parameter. Provide a valid model ID or omit the parameter to use the default.")
    val effectiveFastModel = resolveModel(request.getParameter("fastModel")) ?: throw IllegalArgumentException("Invalid or missing fastModel parameter. Provide a valid model ID or omit the parameter to use the default.")
    val effectiveImageModel = resolveModel(request.getParameter("imageModel")) ?: throw IllegalArgumentException("Invalid or missing imageModel parameter. Provide a valid model ID or omit the parameter to use the default.")
    try {
      val session = Session(sessionId)
      val user = authenticate(request, response) ?: return
      val sessionDir = dataStorage.getSessionDir(user, session)
      if (!sessionDir.exists() || !sessionDir.isDirectory) {
        response.status = HttpServletResponse.SC_NOT_FOUND
        response.contentType = "application/json"
        response.writer.write("""{"error": "Session directory not found: $sessionId"}""")
        return
      }
      val docFile = sessionDir.resolve(docPath)
      if (!docFile.exists() || !docFile.isFile) {
        response.status = HttpServletResponse.SC_NOT_FOUND
        response.contentType = "application/json"
        response.writer.write("""{"error": "Document file not found: $docPath"}""")
        return
      }
// Validate the doc file is within the session directory
      if (!docFile.canonicalPath.startsWith(sessionDir.canonicalPath)) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json"
        response.writer.write("""{"error": "Access denied: document path is outside session directory"}""")
        return
      }
      val targetPath = request.getParameter("target")
      log.info("DocOps request: session=$sessionId, doc=$docPath, target=$targetPath, mode=$modeName, smartModel=${effectiveSmartModel.modelId}, fastModel=${effectiveFastModel.modelId}, imageModel=${effectiveImageModel.modelId}")
      val docProcessor = DocProcessor(
        root = sessionDir,
        docsFolder = sessionDir,
        updateMode = updateMode,
        fastModel = effectiveFastModel,
        smartModel = effectiveSmartModel,
        imageModel = effectiveImageModel,
        autoFix = true,
        user = user,
        parentSession = Session(sessionId),
      )
      val docSpec = docProcessor.parseMarkdownWithFrontmatter(docFile)
      if (docSpec == null) {
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.contentType = "application/json"
        response.writer.write("""{"error": "No valid frontmatter found in document: $docPath. Ensure the file has 'specifies', 'documents', 'transforms', or 'generates' frontmatter."}""")
        return
      }
      val allTasks = docProcessor.getAll(docFile)
// If a specific target is requested, filter tasks to only that target
      val tasksToRun = if (!targetPath.isNullOrBlank()) {
        val targetFile = sessionDir.resolve(targetPath).canonicalFile
        allTasks.filter { task ->
          try {
            task.data.main_file?.canonicalFile?.endsWith(targetFile) == true
          } catch (e: Exception) {
            false
          } ?: false
        }
      } else {
        allTasks
      }
      if (tasksToRun.isEmpty()) {
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.contentType = "application/json"
        val msg = if (!targetPath.isNullOrBlank()) {
          "No tasks found for target '$targetPath' in document '$docPath'"
        } else {
          "No tasks found in document '$docPath'"
        }
        response.writer.write("""{"error": "$msg"}""")
        return
      }
      log.info("Executing ${tasksToRun.size} DocOps task(s) for session $sessionId")
      val cancelFlag = AtomicBoolean(false)
      val sessions = mutableListOf<Session>()
      val resultSessions = docProcessor.runAll(
        fileMods = tasksToRun,
        cancelFlag = cancelFlag,
        onNewSession = { s -> sessions += s }
      )
// Collect results
      val processedFiles = tasksToRun.flatMap { task ->
        task.data.relative_files ?: emptyList()
      }.distinct()
      response.status = HttpServletResponse.SC_OK
      response.contentType = "application/json"
      response.characterEncoding = "UTF-8"
      response.writer.write(buildString {
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
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.contentType = "application/json"
      val errorMsg = (e.message ?: "Unknown error").replace("\"", "\\\"").replace("\n", " ")
      response.writer.write("""{"error": "Processing failed: $errorMsg"}""")
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(DocProcessorServlet::class.java)

    /**
     * Resolves a model ID string to a ChatModel instance.
     * Looks up the model in the registered ChatModel values first,
     * then falls back to creating a generic LLMModel if not found.
     * Returns null if the input is null or blank.
     */
    private fun resolveModel(modelId: String?): ChatModel? {
      if (modelId.isNullOrBlank()) return null
      // Look up in registered ChatModel values
      val values = ChatModel.values
      values.values.find { it.modelId == modelId }?.let { return it }
      // Fall back: create a generic ChatModel wrapper
      log.warn("Model ID '{}' not found in registered models; creating unregistered model reference", modelId)
      return object : ChatModel(modelId = modelId, provider = null) {
        override fun toString() = modelId
      }
    }

  }
}