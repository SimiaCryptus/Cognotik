package com.simiacryptus.cognotik.webui.servlet

    import com.simiacryptus.cognotik.chat.model.ChatMessageModality
    import com.simiacryptus.cognotik.chat.model.ChatModel
    import com.simiacryptus.cognotik.docops.DocProcessor
    import com.simiacryptus.cognotik.docops.PlatformTaskKind
    import com.simiacryptus.cognotik.docops.UpdateMode
    import com.simiacryptus.cognotik.docops.UpdateModes
    import com.simiacryptus.cognotik.docops.model.WorkPlan
    import com.simiacryptus.cognotik.platform.ApplicationServices
    import com.simiacryptus.cognotik.platform.model.Session
    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
    import com.simiacryptus.cognotik.webui.application.UserProviderImpl
    import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.models
    import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.userSettings
    import jakarta.servlet.http.HttpServlet
    import jakarta.servlet.http.HttpServletRequest
    import jakarta.servlet.http.HttpServletResponse
    import org.slf4j.LoggerFactory
    import java.io.File
    import java.util.concurrent.Executors
    import java.util.concurrent.atomic.AtomicBoolean

    /**
     * Servlet that processes DocOps rendering requests.
     *
     * This class is **the** DocOps entry point for servers: besides the HTTP surface
     * it exposes the underlying pipeline as a small programmatic API
     * ([newProcessor], [plan], [initializeStatus], [runPlan], plus the model/document
     * resolution helpers in the companion). Embedded drivers - such as the CLI file
     * server's `.fsapi/v1/docops` action - are expected to *invoke this servlet*
     * rather than re-assembling `DocProcessor` themselves, and to customise it by
     * subclassing (see the `resolveXxx` / `defaultXxx` hooks).
     *
     * Accepts requests with the following query parameters:
     * - sessionId: The session ID to resolve the working directory
     * - doc: The path to the markdown documentation file (relative to session root)
     * - target: (Optional, repeatable) The target output file path (relative to session root)
     * - mode: (Optional) The update mode to use (default: [defaultMode])
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
     * and executes the resulting documentation processing tasks asynchronously.
     * The response is returned immediately after tasks are marked PENDING in the
     * docops.status.json file; the actual processing continues in the background.
     */
    open class DocProcessorServlet() : HttpServlet() {
      private val dataStorage by lazy { ApplicationServices.fileApplicationServices().dataStorageFactory }
      private val metadataDB by lazy { ApplicationServices.fileApplicationServices().metadataDB }

      /*
       * ------------------------------------------------------------------
       * Customisation hooks (used by embedded mounts, e.g. the CLI server)
       * ------------------------------------------------------------------
       */

      /** Default `?mode=` when the request names none. */
      protected open val defaultMode: String get() = "PatchExisting"
      protected open val defaultAutoFix: Boolean get() = true
      protected open val defaultConcurrency: Int get() = 4

      /** true = never start a second web server for the generated sessions. */
      protected open val defaultServerless: Boolean get() = false
      protected open val defaultShowMenubar: Boolean get() = true

      /*
       * ------------------------------------------------------------------
       * Programmatic API
       * ------------------------------------------------------------------
       */

      data class Models(
        val smart: ChatModel,
        val fast: ChatModel,
        val image: ChatModel,
        val audio: ChatModel,
      )

      /** Fully resolved, already-validated DocOps invocation. */
      data class DocOpsRequest(
        /** Project root / working directory handed to `DocProcessor`. */
        val root: File,
        val user: User,
        val models: Models,
        /** Explicit documents; empty = scan [docsFolder]. */
        val docFiles: List<File> = emptyList(),
        val docsFolder: File = root,
        /** Session the generated sessions hang off, when there is one. */
        val session: Session? = null,
        /** Only keep the tasks producing these (canonical) output files. */
        val targets: List<File> = emptyList(),
        val updateMode: UpdateMode = UpdateModes.PatchExisting,
        val templateVars: Map<String, String> = emptyMap(),
        val autoFix: Boolean = true,
        val serverless: Boolean = false,
        val openBrowser: Boolean = false,
        val showMenubar: Boolean = true,
        val concurrency: Int = 4,
      )

      /** The single place a `DocProcessor` is configured. */
      open fun newProcessor(req: DocOpsRequest): DocProcessor = if (req.session != null) DocProcessor(
        root = req.root,
        docsFolder = req.docsFolder,
        updateMode = req.updateMode,
        fastModel = req.models.fast,
        smartModel = req.models.smart,
        imageModel = req.models.image,
        audioModel = req.models.audio,
        autoFix = req.autoFix,
        user = req.user,
        parentSession = req.session,
        templateVarOverrides = req.templateVars,
        serverless = req.serverless,
        openBrowser = req.openBrowser,
        showMenubar = req.showMenubar,
      ) else DocProcessor(
        root = req.root,
        docsFolder = req.docsFolder,
        updateMode = req.updateMode,
        fastModel = req.models.fast,
        smartModel = req.models.smart,
        imageModel = req.models.image,
        audioModel = req.models.audio,
        autoFix = req.autoFix,
        user = req.user,
        templateVarOverrides = req.templateVars,
        serverless = req.serverless,
        openBrowser = req.openBrowser,
        showMenubar = req.showMenubar,
      )

      /** Plans [req]; pure (writes nothing, starts nothing). */
      open fun plan(
        req: DocOpsRequest,
        processor: DocProcessor,
        applyTargets: Boolean = true,
      ): WorkPlan<PlatformTaskKind> {
        val plan = if (req.docFiles.isEmpty()) processor.getAll()
        else processor.getAll(*req.docFiles.toTypedArray())
        return if (applyTargets) filterByTargets(plan, req.targets) else plan
      }

      /** Seeds `docops.status.json` with PENDING entries before anything destructive. */
      open fun initializeStatus(processor: DocProcessor, plan: WorkPlan<PlatformTaskKind>) {
        processor.docOps.initializeStatus(plan)
      }

      /** Executes an already-seeded plan. */
      open fun runPlan(
        processor: DocProcessor,
        plan: WorkPlan<PlatformTaskKind>,
        concurrency: Int = defaultConcurrency,
        cancelFlag: AtomicBoolean = AtomicBoolean(false),
        onNewSession: (Session) -> Unit = {},
      ): Collection<Session> = (if (concurrency <= 1) processor.runAll(
        plan = plan,
        cancelFlag = cancelFlag,
        onNewSession = onNewSession,
      ) else processor.runAll(
        plan = plan,
        pool = FixedConcurrencyProcessor(asyncExecutor, concurrency),
        cancelFlag = cancelFlag,
        onNewSession = onNewSession,
      )).toList()

      /*
       * ------------------------------------------------------------------
       * HTTP surface
       * ------------------------------------------------------------------
       */

      override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        doPost(req, resp)
      }

      override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        try {
          val docPath = request.getParameter("doc")
          if (docPath.isNullOrBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: doc")
            return
          }
          val user = resolveUser(request, response) ?: return
          val root = resolveRoot(request, response, user) ?: return
          val docFile = root.resolve(docPath)
          if (!docFile.canonicalPath.startsWith(root.canonicalPath)) {
            log.info("Document path '$docPath' is outside of the served root '${root.absolutePath}'")
            writeError(
              response, HttpServletResponse.SC_FORBIDDEN,
              "Access denied: document path is outside session directory"
            )
            return
          }
          if (!docFile.exists() || !docFile.isFile) {
            log.info("Document file not found for path '$docPath': ${docFile.absolutePath}")
            writeError(response, HttpServletResponse.SC_NOT_FOUND, "Document file not found: $docPath")
            return
          }
          val listTemplateVarsParam = request.getParameter("listTemplateVars")
          if (listTemplateVarsParam != null && listTemplateVarsParam.equals("true", ignoreCase = true)) {
            val vars: Map<String, String> = DocProcessor.listTemplateVarKeys(docFile)
            response.status = HttpServletResponse.SC_OK
            response.contentType = "application/json"
            response.characterEncoding = "UTF-8"
            response.writer.write(buildString {
              append("""{"doc": "${esc(docPath)}"""")
              append(""", "templateVars": {""")
              append(vars.entries.joinToString(", ") { (k: String, v: String) -> "\"${esc(k)}\": \"${esc(v)}\"" })
              append("}}")
            })
            return
          }
          /* Read-only mounts refuse anything past this point. */
          if (!isMutationAllowed(request, response)) return

          val docRequest = buildRequest(request, user, root, docFile)
          val processor = newProcessor(docRequest)
          if (processor.docOps.loader.load(docFile) == null) {
            log.info("No valid frontmatter found in document '$docPath'")
            writeError(
              response, HttpServletResponse.SC_BAD_REQUEST,
              "No valid frontmatter found in document: $docPath. Ensure the file has 'specifies', " +
                  "'documents', 'transforms', or 'generates' frontmatter."
            )
            return
          }
          val models = docRequest.models
          log.info(
            "DocOps request: root=${root.absolutePath}, doc=$docPath, targets=${docRequest.targets}, " +
                "mode=${docRequest.updateMode}, smartModel=${models.smart.modelId}, fastModel=${models.fast.modelId}, " +
                "imageModel=${models.image.modelId}, audioModel=${models.audio.modelId}, " +
                "templateVars=${docRequest.templateVars.keys}"
          )
          val tasksToRun = plan(docRequest, processor)
          if (tasksToRun.isEmpty) {
            log.info("No tasks found for document '$docPath' and targets '${docRequest.targets}'")
            val msg = if (docRequest.targets.isNotEmpty()) {
              "No tasks found for the requested target(s) in document '$docPath'"
            } else {
              "No tasks found in document '$docPath'"
            }
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, msg)
            return
          }
          log.info("Marking ${tasksToRun.tasks.size} DocOps task(s) as PENDING")
          // Write PENDING status entries synchronously BEFORE responding to the client.
          initializeStatus(processor, tasksToRun)
          val processedFiles = processedFiles(tasksToRun)
          // Send response immediately after status is written.
          response.status = HttpServletResponse.SC_ACCEPTED
          response.contentType = "application/json"
          response.characterEncoding = "UTF-8"
          response.writer.write(buildString {
            append("""{"success": true""")
            append(""", "async": true""")
            append(""", "tasksScheduled": ${tasksToRun.tasks.size}""")
            append(""", "processedFiles": [""")
            append(processedFiles.joinToString(", ") { path -> "\"${esc(path)}\"" })
            append("]")
            append("}")
          })
          response.writer.flush()
          log.info("Response sent (${tasksToRun.tasks.size} tasks PENDING); kicking off async execution")
          // Kick off the actual processing on a background thread.
          asyncExecutor.submit {
            try {
              val resultSessions = runPlan(processor, tasksToRun, concurrency = docRequest.concurrency)
              log.info(
                "Async DocOps processing complete: ${tasksToRun.tasks.size} tasks, ${resultSessions.size} sessions"
              )
            } catch (e: Throwable) {
              log.error("Async DocOps processing failed", e)
            }
          }
        } catch (e: Exception) {
          log.error("Error processing DocOps request", e)
          if (!response.isCommitted) {
            val errorMsg = (e.message ?: "Unknown error").replace("\"", "\\\"").replace("\n", " ")
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Processing failed: $errorMsg")
          }
        }
      }

      /** Maps query parameters onto a [DocOpsRequest]. */
      protected open fun buildRequest(
        request: HttpServletRequest,
        user: User,
        root: File,
        docFile: File,
      ): DocOpsRequest {
        val modeName = request.getParameter("mode")?.takeIf { it.isNotBlank() } ?: defaultMode
        val updateMode = UpdateModes.fromName(modeName) ?: UpdateModes.PatchExisting
        return DocOpsRequest(
          root = root,
          user = user,
          models = resolveModels(request, user),
          docFiles = listOf(docFile),
          docsFolder = resolveDocsFolder(request, root),
          session = resolveSession(request),
          targets = request.getParameterValues("target").orEmpty()
            .filter { it.isNotBlank() }
            .map { File(root, it.trim().removePrefix("/")).canonicalFile },
          updateMode = updateMode,
          templateVars = extractTemplateVarOverrides(request),
          autoFix = request.getParameter("autoFix")?.toBoolean() ?: defaultAutoFix,
          serverless = defaultServerless,
          showMenubar = defaultShowMenubar,
          concurrency = defaultConcurrency,
        )
      }

      protected open fun resolveUser(request: HttpServletRequest, response: HttpServletResponse): User? =
        UserProviderImpl().authenticate(request, response) ?: throw IllegalStateException("Authentication failed")

      /** Session the request belongs to, if any (used as the parent of new sessions). */
      protected open fun resolveSession(request: HttpServletRequest): Session? =
        request.getParameter("sessionId")?.takeIf { it.isNotBlank() }?.let { Session(it) }

      protected open fun resolveDocsFolder(request: HttpServletRequest, root: File): File = root

      /**
       * Working directory for the request. The default resolves the caller's session
       * directory and enforces ownership; embedded mounts override it with a fixed root.
       * Returns null after writing an error response.
       */
      protected open fun resolveRoot(
        request: HttpServletRequest,
        response: HttpServletResponse,
        user: User,
      ): File? {
        val sessionId = request.getParameter("sessionId")
        if (sessionId.isNullOrBlank()) {
          writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: sessionId")
          return null
        }
        val session = Session(sessionId)
        metadataDB.getSessionOwner(session)?.let { ownerId ->
          if (ownerId != user.id) {
            log.info("User '${user.id}' attempted to access session '$session' owned by '$ownerId'")
            writeError(
              response, HttpServletResponse.SC_FORBIDDEN,
              "Access denied: session is owned by another user"
            )
            return null
          }
        }
        val sessionDir = dataStorage.getUserDir(user, session)
        if (!sessionDir.exists() || !sessionDir.isDirectory) {
          log.info("Session directory not found for '$session' and user '${user.id}': ${sessionDir.absolutePath}")
          writeError(response, HttpServletResponse.SC_NOT_FOUND, "Session directory not found: $session")
          return null
        }
        return sessionDir
      }

      /** Hook for read-only mounts: write an error and return false to refuse execution. */
      protected open fun isMutationAllowed(request: HttpServletRequest, response: HttpServletResponse): Boolean = true

      /**
       * Models this endpoint may use for [user]. Public (not protected) because programmatic
       * drivers - the `.fsapi/v1/docops` action, the patch chat - must ask *the servlet*, so a
       * swapped proxy implementation stays in control of model/credential selection.
       */
      open fun availableModels(user: User): Map<String, ChatModel> = user.userSettings().models()

      /**
       * Resolves the four model roles for a programmatic (non-HTTP) invocation from [user]'s
       * persisted settings - the only source of a selection. Throws [IllegalArgumentException]
       * when no smart model has been selected (the message includes the available ids).
       */
      open fun modelsFor(
        user: User,
      ): Models = user.userSettings().let { settings ->
        models(
          smartModel = settings.smartModel.takeIf { !it.isNullOrBlank() },
          fastModel = settings.fastModel.takeIf { !it.isNullOrBlank() },
          imageModel = null,
          audioModel = null,
          available = settings.models(),
        )
      }

      /** Query parameters are a per-request override of the caller's stored selection. */
      protected open fun resolveModels(request: HttpServletRequest, user: User): Models {
        val settings = user.userSettings()
        return models(
          smartModel = request.getParameter("smartModel")?.takeIf { it.isNotBlank() }
            ?: settings.smartModel.takeIf { !it.isNullOrBlank() },
          fastModel = request.getParameter("fastModel")?.takeIf { it.isNotBlank() }
            ?: settings.fastModel.takeIf { !it.isNullOrBlank() },
          imageModel = request.getParameter("imageModel")?.takeIf { it.isNotBlank() },
          audioModel = request.getParameter("audioModel")?.takeIf { it.isNotBlank() },
          available = settings.models(),
        )
      }

      protected fun writeError(response: HttpServletResponse, status: Int, message: String) {
        response.status = status
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"error": "${esc(message)}"}""")
      }

      companion object {
        private val log = LoggerFactory.getLogger(DocProcessorServlet::class.java)

        /**
         * Executor used to run DocProcessor.runAll asynchronously after the
         * servlet has already responded to the client. Uses daemon threads so
         * the JVM may exit cleanly.
         */
        private val asyncExecutor = Executors.newCachedThreadPool { r ->
          Thread(r, "DocProcessorServlet-Async").apply { isDaemon = true }
        }

        /**
         * Prefix used on query parameters to identify template variable overrides.
         * Example: "var.PROJECT_NAME=Foo" -> overrides {{PROJECT_NAME}} with "Foo".
         */
        private const val TEMPLATE_VAR_PARAM_PREFIX = "var."

        /** Files the plan will (re)write, relative to the root. */
        fun processedFiles(plan: WorkPlan<PlatformTaskKind>): List<String> = plan.tasks
          .flatMap { planned -> planned.task.data.relative_files ?: emptyList() }
          .distinct()

        /** Keeps only the tasks producing one of [targets] (empty = keep everything). */
        fun filterByTargets(
          plan: WorkPlan<PlatformTaskKind>,
          targets: List<File>,
        ): WorkPlan<PlatformTaskKind> {
          if (targets.isEmpty()) return plan
          return plan.filter { planned ->
            targets.any { targetFile ->
              try {
                planned.task.data.main_file?.canonicalFile?.endsWith(targetFile) == true
                    || planned.target.file.canonicalFile == targetFile
              } catch (e: Exception) {
                false
              }
            }
          }
        }

        /**
         * Canonicalises [paths] (relative to [root]), jails them under it and expands
         * folders. Requested paths are untrusted, so this must be the only resolver.
         */
        fun resolveDocFiles(root: File, docsFolder: File, paths: List<String>): List<File> {
          if (paths.isEmpty()) return markdownFiles(docsFolder)
          val rootPath = root.canonicalFile.toPath()
          return paths.map { path ->
            val file = File(root, path.removePrefix("/")).canonicalFile
            if (!file.toPath().startsWith(rootPath)) {
              throw IllegalArgumentException("document escapes the served root: $path")
            }
            if (!file.exists()) throw IllegalArgumentException("document not found: $path")
            file
          }.flatMap { if (it.isDirectory) markdownFiles(it) else listOf(it) }
        }

        fun markdownFiles(folder: File): List<File> = folder.walkTopDown()
          .filter { it.isFile && it.extension.lowercase() in setOf("md", "markdown") }
          .toList()

        /**
         * Resolves the four model roles, falling back smart -> fast -> image/audio.
         * Throws [IllegalArgumentException] when no smart model can be determined.
         */
        fun models(
          smartModel: String?,
          fastModel: String?,
          imageModel: String?,
          audioModel: String?,
          available: Map<String, ChatModel>,
        ): Models {
          val smart = resolveModel(smartModel, available) ?: throw IllegalArgumentException(
            "Invalid or missing smart model" + (smartModel?.let { " '$it'" } ?: "") + ". " + describeModels(available)
          )
          val fast = resolveModel(fastModel, available) ?: smart
          return Models(
            smart = smart,
            fast = fast,
            image = resolveModel(imageModel, available) ?: fast,
            audio = resolveModel(audioModel, available) ?: fast,
          )
        }

        /**
         * Resolves a model ID string to a ChatModel instance.
         * Looks up the model in the registered ChatModel values first,
         * then falls back to creating a generic model reference if not found.
         * Returns null if the input is null or blank.
         */
        fun resolveModel(modelId: String?, models: Map<String, ChatModel>): ChatModel? {
          if (modelId.isNullOrBlank()) return null
          models.values.find { it.modelId == modelId }?.let { return it }
          models[modelId]?.let { return it }
          models.entries.firstOrNull { it.key.equals(modelId, ignoreCase = true) }?.let { return it.value }
          log.warn("Model ID '{}' not found in registered models; creating unregistered model reference", modelId)
          return ChatModel(
            modelId = modelId,
            inputModalities = setOf(ChatMessageModality.TEXT),
            outputModalities = setOf(ChatMessageModality.TEXT)
          )
        }

        fun describeModels(available: Map<String, ChatModel>): String = if (available.isEmpty()) {
          "No models are configured for this user; add an API key first."
        } else {
          "Available models (${available.size}):\n" +
              available.values.map { it.modelId }.distinct().sorted().joinToString("\n") { "  $it" }
        }

        /**
         * Extract template variable overrides from request query parameters.
         * Any parameter whose name begins with [TEMPLATE_VAR_PARAM_PREFIX] is
         * treated as an override. The portion of the parameter name after the
         * prefix is the template variable name. Empty names are ignored. If a
         * parameter has multiple values, the first non-null value is used.
         */
        fun extractTemplateVarOverrides(request: HttpServletRequest): Map<String, String> {
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

        private fun esc(s: String): String = s
          .replace("\\", "\\\\")
          .replace("\"", "\\\"")
          .replace("\n", "\\n")
          .replace("\r", "\\r")
      }
    }