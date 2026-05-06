package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.util.DocProcessor
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.UpdateModes
import com.simiacryptus.cognotik.webui.application.authenticate
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.models
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.userSettings
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
  * - audioModel: (Optional) Model ID to use as the audio model
 *
  * Template variable overrides:
  * Any query parameter prefixed with "var." will be treated as a template
  * variable override. For example, "var.PROJECT_NAME=Foo" supplies the value
  * "Foo" for the {{PROJECT_NAME}} placeholder. Overrides take precedence over
  * frontmatter-declared defaults (template_vars / template_variables / vars /
  * variables) and are also applied for variables not declared in frontmatter.
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
    val user = authenticate(request, response) ?: return
    val models = user.userSettings().models()
    val smartModel = resolveModel(request.getParameter("smartModel"), models)
    val effectiveSmartModel = smartModel ?: throw IllegalArgumentException("Invalid or missing smartModel parameter. Provide a valid model ID or omit the parameter to use the default.")
    val effectiveFastModel = resolveModel(request.getParameter("fastModel"), models) ?: effectiveSmartModel
    val effectiveImageModel = resolveModel(request.getParameter("imageModel"), models) ?: effectiveFastModel
     val effectiveAudioModel = resolveModel(request.getParameter("audioModel"), models) ?: effectiveFastModel
     val templateVarOverrides = extractTemplateVarOverrides(request)
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
      // If the caller only wants to inspect available template variables, return them and exit early.
      val listTemplateVarsParam = request.getParameter("listTemplateVars")
      if (listTemplateVarsParam != null && listTemplateVarsParam.equals("true", ignoreCase = true)) {
        val vars = DocProcessor.listTemplateVarKeys(docFile)
        response.status = HttpServletResponse.SC_OK
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(buildString {
          append("""{"doc": "${docPath.replace("\\", "\\\\").replace("\"", "\\\"")}"""")
          append(""", "templateVars": {""")
          append(vars.entries.joinToString(", ") { (k, v) ->
            val ek = k.replace("\\", "\\\\").replace("\"", "\\\"")
            val ev = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            "\"$ek\": \"$ev\""
          })
          append("}}")
        })
        return
      }
      val targetPath = request.getParameter("target")
       log.info("DocOps request: session=$sessionId, doc=$docPath, target=$targetPath, mode=$modeName, smartModel=${effectiveSmartModel.modelId}, fastModel=${effectiveFastModel.modelId}, imageModel=${effectiveImageModel.modelId}, audioModel=${effectiveAudioModel.modelId}, templateVars=${templateVarOverrides.keys}")
      val docProcessor = DocProcessor(
        root = sessionDir,
        docsFolder = sessionDir,
        updateMode = updateMode,
        fastModel = effectiveFastModel,
        smartModel = effectiveSmartModel,
        imageModel = effectiveImageModel,
         audioModel = effectiveAudioModel,
        autoFix = true,
        user = user,
        parentSession = Session(sessionId),
         templateVarOverrides = templateVarOverrides,
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
      * Prefix used on query parameters to identify template variable overrides.
      * Example: "var.PROJECT_NAME=Foo" -> overrides {{PROJECT_NAME}} with "Foo".
      */
     private const val TEMPLATE_VAR_PARAM_PREFIX = "var."
     /**
      * Extract template variable overrides from request query parameters.
      * Any parameter whose name begins with [TEMPLATE_VAR_PARAM_PREFIX] is
      * treated as an override. The portion of the parameter name after the
      * prefix is the template variable name. Empty names are ignored. If a
      * parameter has multiple values, the first non-null value is used.
      */
     private fun extractTemplateVarOverrides(request: HttpServletRequest): Map<String, String> {
       val result = linkedMapOf<String, String>()
       val paramNames = request.parameterNames ?: return result
       while (paramNames.hasMoreElements()) {
         val name = paramNames.nextElement() ?: continue
         if (!name.startsWith(TEMPLATE_VAR_PARAM_PREFIX)) continue
         val varName = name.substring(TEMPLATE_VAR_PARAM_PREFIX.length).trim()
         if (varName.isEmpty()) continue
         val value = request.getParameterValues(name)?.firstOrNull { it != null } ?: continue
         result[varName] = value
       }
       return result
     }

    /**
     * Resolves a model ID string to a ChatModel instance.
     * Looks up the model in the registered ChatModel values first,
     * then falls back to creating a generic LLMModel if not found.
     * Returns null if the input is null or blank.
     */
    private fun resolveModel(
      modelId: String?, models: Map<String, ChatModel>
                             ): ChatModel? {
      if (modelId.isNullOrBlank()) return null
      models.values.find { it.modelId == modelId }?.let { return it }
      log.warn("Model ID '{}' not found in registered models; creating unregistered model reference", modelId)
      return ChatModel(modelId = modelId, provider = null)
    }

  }
}