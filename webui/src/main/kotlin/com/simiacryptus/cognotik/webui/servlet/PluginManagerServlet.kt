package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.PluginEvents
import com.simiacryptus.cognotik.auth.AuthorizationChain
import com.simiacryptus.cognotik.auth.PendingAuthorization
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*
*  /pluginManager
* */
@MultipartConfig(
  maxFileSize = 50 * 1024 * 1024L,       // 50 MB per file
  maxRequestSize = 100 * 1024 * 1024L     // 100 MB per request
)
class PluginManagerServlet(
) : HttpServlet() {
  private val pluginDirectory: File = File("./plugins")

  /**
   * Registry of named authorization chains that plugins can register.
   * Key is a chain name/ID, value is the chain.
   */
  private val authorizationChains = ConcurrentHashMap<String, AuthorizationChain>()

  /** Subscription IDs for event router cleanup */
  private val eventSubscriptionIds = mutableListOf<String>()

  /**
   * Maps handler IDs (used in callback URLs) to their corresponding session IDs.
   * This allows the callback endpoint to resolve which session a callback belongs to.
   */
  private val handlerToSessionMap = ConcurrentHashMap<String, ConcurrentHashMap<String, (AuthorizationChain.AuthorizationSession)->String>>()

  init {
    pluginDirectory.mkdirs()
    log.info("PluginManagerServlet initialized with plugin directory: {}", pluginDirectory.canonicalPath)
    // Ensure temp directory for multipart uploads exists
    File(System.getProperty("java.io.tmpdir")).mkdirs()
    log.debug("Temp directory for multipart uploads: {}", System.getProperty("java.io.tmpdir"))
    // Subscribe to auth chain registration events from plugins via the event router
    subscribeToPluginEvents()
  }

  /**
   * Subscribe to well-known plugin events so plugins can register auth chains
   * without depending on this servlet.
   */
  private fun subscribeToPluginEvents() {
    val pm = ApplicationServices.pluginManager
    eventSubscriptionIds += pm.subscribe(PluginEvents.REGISTER_AUTH_CHAIN) { data ->
      if (data is PluginEvents.AuthChainRegistration) {
        val chain = data.chain
        if (chain is AuthorizationChain) {
          registerAuthorizationChain(data.name, chain)
        } else {
          log.warn("Received auth chain registration for '{}' but payload is not an AuthorizationChain: {}", data.name, chain.javaClass.name)
        }
      } else {
        log.warn("Received unexpected payload on {}: {}", PluginEvents.REGISTER_AUTH_CHAIN, data)
      }
    }
    eventSubscriptionIds += pm.subscribe(PluginEvents.UNREGISTER_AUTH_CHAIN) { data ->
      if (data is String) {
        unregisterAuthorizationChain(data)
      } else {
        log.warn("Received unexpected payload on {}: {}", PluginEvents.UNREGISTER_AUTH_CHAIN, data)
      }
    }
    log.info("Subscribed to plugin event router for auth chain management")
  }


  /**
   * Register an authorization chain that can be triggered via the web UI.
   *
   * @param name A unique name for this chain
   * @param chain The authorization chain
   */
  fun registerAuthorizationChain(name: String, chain: AuthorizationChain) {
    authorizationChains[name] = chain
    log.info("Registered authorization chain: {}", name)
  }

  /**
   * Remove a registered authorization chain.
   */
  fun unregisterAuthorizationChain(name: String) {
    authorizationChains.remove(name)
    log.info("Unregistered authorization chain: {}", name)
  }

  override fun destroy() {
    // Clean up event subscriptions when servlet is destroyed
    val pm = ApplicationServices.pluginManager
    eventSubscriptionIds.forEach { pm.unsubscribe(it) }
    eventSubscriptionIds.clear()
    handlerToSessionMap.clear()
    log.info("PluginManagerServlet destroyed, event subscriptions cleaned up")
    super.destroy()
  }


  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    log.info(
      "Received GET request from {} - URI: {}, QueryString: {}",
      request.remoteAddr,
      request.requestURI,
      request.queryString
    )
    val user = authenticate(request, response) ?: return
    log.debug("Authenticated user: {}", user)
    if (!ApplicationServices.authorizationManager.isAuthorized(
        PluginManagerServlet::class.java, user, OperationType.Admin
      )
    ) {
      log.warn("Unauthorized access attempt by user: {} from IP: {}", user, request.remoteAddr)
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required")
      return
    }

    val acceptHeader = request.getHeader("Accept") ?: ""
    val action = request.getParameter("action")
    log.debug("GET action: {}, Accept header: {}", action, acceptHeader)

    when {
      action == "scan" -> {
        log.info("Scanning plugin directory: {}", pluginDirectory.canonicalPath)
        response.contentType = "application/json"
        response.status = HttpServletResponse.SC_OK
        val jarFiles = pluginDirectory.listFiles { f -> f.name.endsWith(".jar") } ?: emptyArray()
        log.debug("Found {} JAR files in plugin directory", jarFiles.size)
        val available = jarFiles.map { f ->
          val isLoaded = ApplicationServices.pluginManager.isLoaded(f)
          log.trace("JAR file: {} (size: {} bytes, loaded: {})", f.name, f.length(), isLoaded)
          mapOf(
            "name" to f.name, "path" to f.canonicalPath, "size" to f.length(), "loaded" to isLoaded
          )
        }
        response.writer.write(JsonUtil.toJson(available))
        log.info("Successfully scanned directory, found {} JAR files", jarFiles.size)
      }

      action == "authStatus" -> {
        handleAuthStatus(request, response)
      }

      action == "authStep" -> {
        handleAuthStepGet(request, response)
      }

      action == "authChains" -> {
        handleListAuthChains(response)
      }

      action == "authCallback" -> {
        // Support GET-based callbacks (e.g., OAuth redirects)
        handleAuthCallback(request, response)
      }

      action == "list" || acceptHeader.contains("application/json") -> {
        log.info("Listing loaded plugins")
        response.contentType = "application/json"
        response.status = HttpServletResponse.SC_OK
        val loadedPlugins = ApplicationServices.pluginManager.getLoadedPlugins()
        log.debug("Found {} loaded plugin JARs", loadedPlugins.size)
        val pluginData = loadedPlugins.map { (jarPath, plugins) ->
          log.trace("Loaded JAR: {} with {} plugins", jarPath, plugins.size)
          mapOf(
            "jar" to jarPath, "plugins" to plugins.map { plugin ->
              mapOf(
                "name" to plugin.pluginName, "class" to plugin.javaClass.name
              )
            })
        }
        response.writer.write(JsonUtil.toJson(pluginData))
        log.info("Successfully returned list of {} loaded plugin JARs", loadedPlugins.size)
      }

      else -> {
        log.info("Serving Plugin Manager HTML page")
        response.contentType = "text/html"
        response.status = HttpServletResponse.SC_OK
        response.writer.write(renderHtml())
      }
    }
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    log.info(
      "Received POST request from {} - URI: {}, ContentType: {}",
      request.remoteAddr,
      request.requestURI,
      request.contentType
    )
    val user = authenticate(request, response) ?: return
    log.debug("Authenticated user for POST: {}", user)
    if (!ApplicationServices.authorizationManager.isAuthorized(
        PluginManagerServlet::class.java, user, OperationType.Admin
      )
    ) {
      log.warn("Unauthorized POST access attempt by user: {} from IP: {}", user, request.remoteAddr)
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required")
      return
    }

    response.contentType = "application/json"

    // For multipart requests, we need to handle content type detection
    val contentType = request.contentType ?: ""
    log.debug("Request content type: {}", contentType)
    val action = try {
      if (contentType.contains("multipart/form-data", ignoreCase = true)) {
        log.debug("Processing multipart/form-data request")
        // For multipart, try getPart first for the action field
        try {
          val actionPart = request.getPart("action")
          val resolvedAction =
            actionPart?.inputStream?.bufferedReader()?.readText()?.trim() ?: request.getParameter("action") ?: ""
          log.debug("Resolved action from multipart request: '{}'", resolvedAction)
          resolvedAction
        } catch (e: Exception) {
          log.debug("Failed to get action from multipart part, falling back to parameter: {}", e.message)
          val fallbackAction = request.getParameter("action") ?: ""
          log.debug("Fallback action: '{}'", fallbackAction)
          fallbackAction
        }
      } else {
        val paramAction = request.getParameter("action") ?: ""
        log.debug("Action from request parameter: '{}'", paramAction)
        paramAction
      }
    } catch (e: Exception) {
      log.error("Failed to parse request action", e)
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Failed to parse request: ${jsonEscape(e.message)}"}""")
      return
    }
    log.info("Processing POST action: '{}' from user: {}", action, user)

    when (action) {
      "load" -> handleLoad(request, response)
      "unload" -> handleUnload(request, response)
      "upload" -> handleUpload(request, response)
      "loadDirectory" -> handleLoadDirectory(request, response)
      "delete" -> handleDelete(request, response)
      "startAuth" -> handleStartAuth(request, response)
      "authCallback" -> handleAuthCallback(request, response)
      "executePendingAuth" -> handleExecutePendingAuth(request, response)
      else -> {
        log.warn("Unknown POST action received: '{}'", action)
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.writer.write("""{"error":"Unknown action: $action"}""")
      }
    }
  }

  private fun handleListAuthChains(response: HttpServletResponse) {
    log.info("Listing registered authorization chains")
    response.contentType = "application/json"
    response.status = HttpServletResponse.SC_OK
    val chains = authorizationChains.keys.map { name ->
      val hasPending = PendingAuthorization.getAll().values.any {
        it.pluginName == name || it.pluginName.contains(name, ignoreCase = true)
      }
      mapOf("name" to name, "hasPendingAuth" to hasPending)
    }
    response.writer.write(JsonUtil.toJson(chains))
  }

  private fun handleExecutePendingAuth(request: HttpServletRequest, response: HttpServletResponse) {
    val authId = request.getParameter("id")
    log.info("handleExecutePendingAuth called - id: {}", authId)
    if (authId.isNullOrBlank()) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.contentType = "application/json"
      response.writer.write("""{"error":"Missing 'id' parameter"}""")
      return
    }
    val pending = PendingAuthorization.get(authId)
    if (pending == null) {
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.contentType = "application/json"
      response.writer.write("""{"error":"Pending authorization not found: $authId"}""")
      return
    }
    // Start a web flow for this pending authorization's chain
    val session = pending.chain.startWebFlow()
    if (session == null) {
      // No interactive steps needed - execute directly
      PendingAuthorization.execute(authId)
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to true, "status" to "completed", "message" to "Authorization completed (no interactive steps)"
          )
        )
      )
      return
    }
    // Store the pending auth ID in the session metadata so we can trigger it on completion
    session.metadata["pendingAuthId"] = authId
    pending.status = PendingAuthorization.Status.IN_PROGRESS
    response.contentType = "application/json"
    response.status = HttpServletResponse.SC_OK
    response.writer.write(
      JsonUtil.toJson(
        mapOf(
          "success" to true,
          "sessionId" to session.sessionId,
          "status" to "IN_PROGRESS",
          "currentStep" to (session.currentStepIndex + 1),
          "totalSteps" to session.totalSteps
        )
      )
    )
  }

  private fun handleStartAuth(request: HttpServletRequest, response: HttpServletResponse) {
    val chainName = request.getParameter("chain")
    log.info("handleStartAuth called - chain: {}", chainName)
    if (chainName.isNullOrBlank()) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Missing 'chain' parameter"}""")
      return
    }
    val chain = authorizationChains[chainName]
    if (chain == null) {
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.writer.write("""{"error":"Authorization chain not found: $chainName"}""")
      return
    }
    val session = chain.startWebFlow()
    if (session == null) {
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to true, "status" to "completed", "message" to "No authorization steps required"
          )
        )
      )
      return
    }
    response.contentType = "application/json"
    response.status = HttpServletResponse.SC_OK
    if (session.isComplete) {
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to (session.status == AuthorizationChain.SessionStatus.COMPLETED),
            "sessionId" to session.sessionId,
            "status" to session.status.name,
            "failureReason" to session.failureReason
          )
        )
      )
    } else {
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to true,
            "sessionId" to session.sessionId,
            "status" to "IN_PROGRESS",
            "currentStep" to (session.currentStepIndex + 1),
            "totalSteps" to session.totalSteps
          )
        )
      )
    }
  }

  private fun handleAuthStepGet(request: HttpServletRequest, response: HttpServletResponse) {
    val sessionId = request.getParameter("sessionId")
    log.info("handleAuthStepGet called - sessionId: {}", sessionId)
    if (sessionId.isNullOrBlank()) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.contentType = "application/json"
      response.writer.write("""{"error":"Missing 'sessionId' parameter"}""")
      return
    }
    val session = AuthorizationChain.getSession(sessionId)
    if (session == null) {
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.contentType = "application/json"
      response.writer.write("""{"error":"Session not found: $sessionId"}""")
      return
    }
    if (session.isComplete) {
      response.contentType = "text/html"
      response.status = HttpServletResponse.SC_OK
      val statusClass = if (session.status == AuthorizationChain.SessionStatus.COMPLETED) "msg-success" else "msg-error"
      val statusMsg = if (session.status == AuthorizationChain.SessionStatus.COMPLETED) {
        // If this session is linked to a pending authorization, execute it
        val pendingAuthId = session.metadata["pendingAuthId"] as? String
        if (pendingAuthId != null) {
          val pending = PendingAuthorization.get(pendingAuthId)
          if (pending != null && pending.status == PendingAuthorization.Status.IN_PROGRESS) {
            log.info("Web auth flow completed successfully, triggering pending authorization: {}", pendingAuthId)
            pending.status = PendingAuthorization.Status.COMPLETED
            try {
              pending.onSuccess()
            } catch (e: Exception) {
              log.error("Error in pending authorization onSuccess callback: {}", e.message, e)
            }
          }
        }
        "✅ Authorization completed successfully!"
      } else {
        // If this session is linked to a pending authorization, mark it as failed
        val pendingAuthId = session.metadata["pendingAuthId"] as? String
        if (pendingAuthId != null) {
          val pending = PendingAuthorization.get(pendingAuthId)
          if (pending != null && pending.status == PendingAuthorization.Status.IN_PROGRESS) {
            log.info("Web auth flow failed, marking pending authorization as failed: {}", pendingAuthId)
            pending.status = PendingAuthorization.Status.FAILED
            try {
              pending.onFailure(session.failureReason ?: "Authorization failed")
            } catch (e: Exception) {
              log.error("Error in pending authorization onFailure callback: {}", e.message, e)
            }
          }
        }
        "❌ Authorization failed: ${session.failureReason ?: "Unknown reason"}"
      }
      response.writer.write(
        renderAuthPageWrapper(
          """
         <div class="$statusClass" style="padding:1em;border-radius:4px;margin:1em 0;">
           <h3>$statusMsg</h3>
           <p><a href="/pluginManager">Return to Plugin Manager</a></p>
         </div>
       """.trimIndent()
        )
      )
      AuthorizationChain.removeSession(sessionId)
      // Clean up any remaining handler mappings for this session
      handlerToSessionMap.remove(sessionId)
      return
    }
    val step = session.currentStep
    if (step == null) {
      response.contentType = "text/html"
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        renderAuthPageWrapper(
          """
         <div class="msg-success" style="padding:1em;border-radius:4px;margin:1em 0;">
           <h3>✅ All authorization steps completed!</h3>
           <p><a href="/pluginManager">Return to Plugin Manager</a></p>
         </div>
       """.trimIndent()
        )
      )
      return
    }
    val stepHtml = step.renderHtml(sessionId) { handler ->
      val handlerId = UUID.randomUUID().toString()
      handlerToSessionMap.computeIfAbsent(sessionId) { ConcurrentHashMap() }[handlerId] = handler
      log.debug("Registered callback handler {} for session {}", handlerId, sessionId)
      "/pluginManager?action=authCallback&sessionId=$sessionId&handlerId=$handlerId"
    }
    val progressHtml = """
       <div style="margin-bottom:1em;color:#666;font-size:0.9em;">
         Step ${session.currentStepIndex + 1} of ${session.totalSteps}
         <div style="background:#e9ecef;border-radius:4px;height:8px;margin-top:0.3em;">
           <div style="background:#007bff;border-radius:4px;height:8px;width:${((session.currentStepIndex + 1) * 100) / session.totalSteps}%;"></div>
         </div>
       </div>
     """.trimIndent()
    response.contentType = "text/html"
    response.status = HttpServletResponse.SC_OK
    response.writer.write(renderAuthPageWrapper(progressHtml + stepHtml))
  }

  private fun handleAuthStatus(request: HttpServletRequest, response: HttpServletResponse) {
    val sessionId = request.getParameter("sessionId")
    log.info("handleAuthStatus called - sessionId: {}", sessionId)
    if (sessionId.isNullOrBlank()) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.contentType = "application/json"
      response.writer.write("""{"error":"Missing 'sessionId' parameter"}""")
      return
    }
    val session = AuthorizationChain.getSession(sessionId)
    if (session == null) {
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.contentType = "application/json"
      response.writer.write("""{"error":"Session not found or expired: $sessionId"}""")
      return
    }
    response.contentType = "application/json"
    response.status = HttpServletResponse.SC_OK
    response.writer.write(
      JsonUtil.toJson(
        mapOf(
          "sessionId" to session.sessionId,
          "status" to session.status.name,
          "currentStep" to (session.currentStepIndex + 1),
          "totalSteps" to session.totalSteps,
          "isComplete" to session.isComplete,
          "failureReason" to session.failureReason
        )
      )
    )
  }

  private fun handleAuthCallback(request: HttpServletRequest, response: HttpServletResponse) {
    val sessionId = request.getParameter("sessionId")
    val handlerId = request.getParameter("handlerId")
    if (handlerId.isNullOrBlank()) {
      log.warn("handleAuthCallback: Missing 'handlerId' parameter")
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.contentType = "application/json"
      response.writer.write("""{"error":"Missing 'handlerId' parameter. Callbacks must use a registered handler."}""")
      return
    }
    if (sessionId == null) {
      log.warn("handleAuthCallback: No session found for handlerId {}", handlerId)
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.contentType = "application/json"
      response.writer.write("""{"error":"Unknown or expired callback handler: $handlerId"}""")
      return
    }
    log.info("handleAuthCallback called - handlerId: {}, resolved sessionId: {}", handlerId, sessionId)
    val session: AuthorizationChain.AuthorizationSession = AuthorizationChain.getSession(sessionId) ?: run {
      log.warn("handleAuthCallback: No session found for sessionId {} (handlerId {})", sessionId, handlerId)
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.contentType = "application/json"
      response.writer.write("""{"error":"Session not found or expired for handler: $handlerId"}""")
      return
    }
    // Clean up the handler mapping now that it's been used
    handlerToSessionMap.remove(handlerId)
    log.debug("Removed callback handler mapping for handlerId {}", handlerId)
    // Collect all parameters into a map
    val parameters = mutableMapOf<String, String>()
    request.parameterMap.forEach { (key, values) ->
      if (values.isNotEmpty() && key !in INTERNAL_PARAMS && key != "handlerId") parameters[key] = values[0]
    }
    val fn = handlerToSessionMap[sessionId]?.get(handlerId)
    val result = fn?.let { it(session) }
    response.contentType = "text/html"
    response.status = HttpServletResponse.SC_OK
    response.writer.write(renderAuthPageWrapper(result ?: "OK"))
  }

  private fun renderAuthPageWrapper(bodyContent: String): String = """
     <!DOCTYPE html>
     <html lang="en">
     <head>
         <meta charset="UTF-8"/>
         <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
         <title>Plugin Authorization</title>
         <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
         <style>
             body { font-family: Arial, sans-serif; margin: 2em; background: #f5f5f5; color: #333; }
             h1 { color: #444; }
             h3 { color: #555; }
             .card { background: #fff; border-radius: 6px; padding: 1.5em; margin-bottom: 1.5em;
                     box-shadow: 0 1px 4px rgba(0,0,0,0.1); max-width: 700px; margin-left: auto; margin-right: auto; }
             .btn-primary { padding: 0.4em 1em; border: none; border-radius: 4px; cursor: pointer;
                 font-size: 0.9em; background: #007bff; color: #fff; }
             .btn-primary:hover { background: #0056b3; }
             .btn-danger { padding: 0.4em 1em; border: none; border-radius: 4px; cursor: pointer;
                 font-size: 0.9em; background: #dc3545; color: #fff; }
             .btn-danger:hover { background: #a71d2a; }
             .btn-success { padding: 0.4em 1em; border: none; border-radius: 4px; cursor: pointer;
                 font-size: 0.9em; background: #28a745; color: #fff; }
             .btn-success:hover { background: #1e7e34; }
             .btn-secondary { padding: 0.4em 1em; border: none; border-radius: 4px; cursor: pointer;
                 font-size: 0.9em; background: #6c757d; color: #fff; }
             .btn-secondary:hover { background: #545b62; }
             .msg-success { background: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
             .msg-error { background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
             .msg-info { background: #d1ecf1; color: #0c5460; border: 1px solid #bee5eb; }
             a { color: #007bff; }
             a:hover { color: #0056b3; }
         </style>
     </head>
     <body>
         <div class="card">
             <h1>🔐 Plugin Authorization</h1>
             $bodyContent
         </div>
     </body>
     </html>
   """.trimIndent()


  private fun handleLoad(request: HttpServletRequest, response: HttpServletResponse) {
    val jarPath = request.getParameter("jar")
    val entryPoint = request.getParameter("entryPoint")
    log.info("handleLoad called - jarPath: {}, entryPoint: {}", jarPath, entryPoint)

    if (jarPath.isNullOrBlank()) {
      log.warn("handleLoad: Missing 'jar' parameter")
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Missing 'jar' parameter"}""")
      return
    }

    val jarFile = File(jarPath).let {
      if (it.isAbsolute) it else File(pluginDirectory, jarPath)
    }
    log.debug(
      "Resolved JAR file path: {}, exists: {}, size: {} bytes",
      jarFile.canonicalPath,
      jarFile.exists(),
      if (jarFile.exists()) jarFile.length() else "N/A"
    )

    response.contentType = "application/json"
    try {
      val plugins = if (!entryPoint.isNullOrBlank()) {
        log.info("Loading plugin from JAR: {} with entry point: {}", jarFile.canonicalPath, entryPoint)
        listOf(ApplicationServices.pluginManager.loadPlugin(jarFile, entryPoint))
      } else {
        log.info("Loading all plugins from JAR: {}", jarFile.canonicalPath)
        ApplicationServices.pluginManager.loadPlugin(jarFile)
      }
      log.info(
        "Successfully loaded {} plugin(s) from JAR: {} - plugins: {}",
        plugins.size,
        jarFile.canonicalPath,
        plugins.map { it.pluginName })
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        JsonUtil.toJson(
        mapOf(
        "success" to true,
        "jar" to jarFile.canonicalPath,
        "pluginsLoaded" to plugins.size,
        "plugins" to plugins.map { it.pluginName })))
    } catch (e: IllegalStateException) {
      log.warn("Plugin already loaded: {}", jarPath)
      response.status = HttpServletResponse.SC_CONFLICT
      response.writer.write("""{"error":"${jsonEscape(e.message)}"}""")
    } catch (e: Exception) {
      log.error("Failed to load plugin JAR: {}", jarPath, e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("""{"error":"${jsonEscape(e.message)}"}""")
    }
  }

  private fun handleUnload(request: HttpServletRequest, response: HttpServletResponse) {
    val jarPath = request.getParameter("jar")
    log.info("handleUnload called - jarPath: {}", jarPath)

    if (jarPath.isNullOrBlank()) {
      log.warn("handleUnload: Missing 'jar' parameter")
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Missing 'jar' parameter"}""")
      return
    }

    val jarFile = File(jarPath).let {
      if (it.isAbsolute) it else File(pluginDirectory, jarPath)
    }
    log.debug("Resolved JAR file path for unload: {}", jarFile.canonicalPath)

    response.contentType = "application/json"
    try {
      log.info("Unloading plugin JAR: {}", jarFile.canonicalPath)
      ApplicationServices.pluginManager.unloadPlugin(jarFile)
      log.info("Successfully unloaded plugin JAR: {}", jarFile.canonicalPath)
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to true, "jar" to jarFile.canonicalPath
          )
        )
      )
    } catch (e: Exception) {
      log.error("Failed to unload plugin JAR: {}", jarPath, e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("""{"error":"${jsonEscape(e.message)}"}""")
    }
  }

  private fun handleUpload(request: HttpServletRequest, response: HttpServletResponse) {
    log.info("handleUpload called")
    val part = try {
      request.getPart("jarFile")
    } catch (e: Exception) {
      log.error("Failed to get uploaded file part", e)
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Failed to read uploaded file: ${jsonEscape(e.message)}"}""")
      return
    }

    if (part == null) {
      log.warn("handleUpload: No file part found in request")
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"No file uploaded (expected part named 'jarFile')"}""")
      return
    }

    val submittedFileName = part.submittedFileName ?: "plugin.jar"
    log.debug(
      "Uploaded file name: {}, size: {} bytes, content type: {}", submittedFileName, part.size, part.contentType
    )
    if (!submittedFileName.endsWith(".jar")) {
      log.warn("handleUpload: Uploaded file is not a JAR: {}", submittedFileName)
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Uploaded file must be a JAR"}""")
      return
    }

    val destFile = File(pluginDirectory, submittedFileName)
    log.debug("Destination file for upload: {}", destFile.canonicalPath)
    response.contentType = "application/json"
    try {
      part.inputStream.use { input ->
        Files.copy(input, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
      log.info("Plugin JAR uploaded successfully: {} ({} bytes)", destFile.canonicalPath, destFile.length())
      val autoLoad = request.getParameter("autoLoad")?.equals("true", ignoreCase = true) ?: false
      log.debug("Auto-load after upload: {}", autoLoad)
      if (autoLoad) {
        log.info("Auto-loading uploaded plugin JAR: {}", destFile.canonicalPath)
        val plugins = ApplicationServices.pluginManager.loadPlugin(destFile)
        log.info(
          "Auto-loaded {} plugin(s) from uploaded JAR: {} - plugins: {}",
          plugins.size,
          destFile.canonicalPath,
          plugins.map { it.pluginName })
        response.status = HttpServletResponse.SC_OK
        response.writer.write(
          JsonUtil.toJson(
            mapOf(
          "success" to true,
          "file" to destFile.name,
          "path" to destFile.canonicalPath,
          "autoLoaded" to true,
          "pluginsLoaded" to plugins.size,
          "plugins" to plugins.map { it.pluginName })))
      } else {
        log.info("Plugin JAR uploaded without auto-load: {}", destFile.canonicalPath)
        response.status = HttpServletResponse.SC_OK
        response.writer.write(
          JsonUtil.toJson(
            mapOf(
              "success" to true, "file" to destFile.name, "path" to destFile.canonicalPath, "autoLoaded" to false
            )
          )
        )
      }
    } catch (e: Exception) {
      log.error("Failed to save or load uploaded plugin JAR: {}", submittedFileName, e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("""{"error":"${jsonEscape(e.message)}"}""")
    }
  }

  private fun handleLoadDirectory(request: HttpServletRequest, response: HttpServletResponse) {
    val dirPath = request.getParameter("directory")
    val directory = if (!dirPath.isNullOrBlank()) File(dirPath) else pluginDirectory
    log.info(
      "handleLoadDirectory called - directory: {}, exists: {}, isDirectory: {}",
      directory.canonicalPath,
      directory.exists(),
      directory.isDirectory
    )

    response.contentType = "application/json"
    try {
      log.info("Loading all plugins from directory: {}", directory.canonicalPath)
      val results = ApplicationServices.pluginManager.loadPluginsFromDirectory(directory)
      log.info("Loaded plugins from {} JAR(s) in directory: {}", results.size, directory.canonicalPath)
      val summary = results.map { (file, plugins) ->
        log.debug(
          "Directory load result - JAR: {}, plugins loaded: {}, plugin names: {}",
          file.canonicalPath,
          plugins.size,
          plugins.map { it.pluginName })
        mapOf(
          "jar" to file.canonicalPath, "pluginsLoaded" to plugins.size, "plugins" to plugins.map { it.pluginName })
      }
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to true,
            "directory" to directory.canonicalPath,
            "jarsProcessed" to results.size,
            "results" to summary
          )
        )
      )
      log.info("Successfully processed {} JAR(s) from directory: {}", results.size, directory.canonicalPath)
    } catch (e: Exception) {
      log.error("Failed to load plugins from directory: {}", directory, e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("""{"error":"${jsonEscape(e.message)}"}""")
    }
  }

  private fun handleDelete(request: HttpServletRequest, response: HttpServletResponse) {
    val jarPath = request.getParameter("jar")
    log.info("handleDelete called - jarPath: {}", jarPath)
    if (jarPath.isNullOrBlank()) {
      log.warn("handleDelete: Missing 'jar' parameter")
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Missing 'jar' parameter"}""")
      return
    }
    val jarFile = File(jarPath).let {
      if (it.isAbsolute) it else File(pluginDirectory, jarPath)
    }
    log.debug("Resolved JAR file path for delete: {}", jarFile.canonicalPath)
    response.contentType = "application/json"
    try {
      log.info("Deleting plugin JAR: {}", jarFile.canonicalPath)
      ApplicationServices.pluginManager.deletePlugin(jarFile)
      log.info("Successfully deleted plugin JAR: {}", jarFile.canonicalPath)
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to true, "jar" to jarFile.canonicalPath
          )
        )
      )
    } catch (e: IllegalArgumentException) {
      log.warn("Plugin JAR not found for delete: {}", jarPath)
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.writer.write("""{"error":"${jsonEscape(e.message)}"}""")
    } catch (e: Exception) {
      log.error("Failed to delete plugin JAR: {}", jarPath, e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("""{"error":"${jsonEscape(e.message)}"}""")
    }
  }


  private fun renderHtml(): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Plugin Manager</title>
            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
            <style>
                body { font-family: Arial, sans-serif; margin: 2em; background: #f5f5f5; color: #333; }
                h1 { color: #444; }
                h2 { color: #555; border-bottom: 1px solid #ccc; padding-bottom: 0.3em; }
                .card { background: #fff; border-radius: 6px; padding: 1.5em; margin-bottom: 1.5em;
                        box-shadow: 0 1px 4px rgba(0,0,0,0.1); }
                table { width: 100%; border-collapse: collapse; }
                th, td { text-align: left; padding: 0.5em 0.75em; border-bottom: 1px solid #eee; }
                th { background: #f0f0f0; font-weight: bold; }
                tr:hover td { background: #fafafa; }
                .badge { display: inline-block; padding: 0.2em 0.6em; border-radius: 3px;
                         font-size: 0.8em; font-weight: bold; }
                .badge-loaded { background: #d4edda; color: #155724; }
                .badge-unloaded { background: #f8d7da; color: #721c24; }
                button, input[type=submit] {
                    padding: 0.4em 1em; border: none; border-radius: 4px; cursor: pointer;
                    font-size: 0.9em; margin: 0.2em;
                }
                .btn-primary { background: #007bff; color: #fff; }
                .btn-primary:hover { background: #0056b3; }
                .btn-danger { background: #dc3545; color: #fff; }
                .btn-danger:hover { background: #a71d2a; }
                .btn-success { background: #28a745; color: #fff; }
                .btn-success:hover { background: #1e7e34; }
                .btn-secondary { background: #6c757d; color: #fff; }
                .btn-secondary:hover { background: #545b62; }
                input[type=text], input[type=file] {
                    padding: 0.4em; border: 1px solid #ccc; border-radius: 4px;
                    font-size: 0.9em; width: 300px;
                }
                #message { padding: 0.75em 1em; border-radius: 4px; margin-bottom: 1em; display: none; }
                .msg-success { background: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
                .msg-error { background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
                .msg-info { background: #d1ecf1; color: #0c5460; border: 1px solid #bee5eb; }
                pre { background: #f8f9fa; padding: 0.75em; border-radius: 4px;
                      font-size: 0.85em; overflow-x: auto; }
            </style>
        </head>
        <body>
            <h1>🔌 Plugin Manager</h1>

            <div id="message"></div>
             <!-- Authorization Chains -->
             <div class="card">
                 <h2>Authorization Chains
                     <button class="btn-secondary" style="float:right;font-size:0.8em" onclick="refreshAuthChains()">↻ Refresh</button>
                 </h2>
                 <div id="authChains"><em>Loading…</em></div>
             </div>

            <!-- Loaded Plugins -->
            <div class="card">
                <h2>Loaded Plugins
                    <button class="btn-secondary" style="float:right;font-size:0.8em" onclick="refreshLoaded()">↻ Refresh</button>
                </h2>
                <div id="loadedPlugins"><em>Loading…</em></div>
            </div>

            <!-- Available JARs -->
            <div class="card">
                <h2>Available JARs in Plugin Directory
                    <button class="btn-secondary" style="float:right;font-size:0.8em" onclick="scanDirectory()">↻ Scan</button>
                </h2>
                <div id="availableJars"><em>Click Scan to list JARs…</em></div>
                <br/>
                <button class="btn-success" onclick="loadDirectory()">Load All from Directory</button>
            </div>

            <!-- Load by Path -->
            <div class="card">
                <h2>Load Plugin by Path</h2>
                <label>JAR path (relative to plugin directory or absolute):<br/>
                    <input type="text" id="jarPath" placeholder="myplugin.jar"/>
                </label><br/><br/>
                <label>Entry point class (optional):<br/>
                    <input type="text" id="entryPoint" placeholder="com.example.MyPlugin"/>
                </label><br/><br/>
                <button class="btn-primary" onclick="loadPlugin()">Load Plugin</button>
            </div>

            <!-- Upload JAR -->
            <div class="card">
                <h2>Upload Plugin JAR</h2>
                <form id="uploadForm" enctype="multipart/form-data">
                    <label>Select JAR file:<br/>
                        <input type="file" id="jarFile" name="jarFile" accept=".jar"/>
                    </label><br/><br/>
                    <label>
                        <input type="checkbox" id="autoLoad" name="autoLoad" value="true"/>
                        Auto-load after upload
                    </label><br/><br/>
                    <button type="button" class="btn-primary" onclick="uploadPlugin()">Upload</button>
                </form>
            </div>

            <script>
                function showMessage(text, type) {
                    const el = document.getElementById('message');
                    el.textContent = text;
                    el.className = 'msg-' + type;
                    el.style.display = 'block';
                    setTimeout(() => { el.style.display = 'none'; }, 6000);
                }

                function refreshLoaded() {
                    fetch('/pluginManager?action=list', { headers: { 'Accept': 'application/json' } })
                        .then(r => r.json())
                        .then(data => {
                            const container = document.getElementById('loadedPlugins');
                            if (!data || data.length === 0) {
                                container.innerHTML = '<em>No plugins currently loaded.</em>';
                                return;
                            }
                            let html = '<table><thead><tr><th>JAR</th><th>Plugins</th><th>Actions</th></tr></thead><tbody>';
                            data.forEach(entry => {
                                const pluginNames = entry.plugins.map(p => p.name + ' <small>(' + p.class + ')</small>').join('<br/>');
                                html += '<tr>'
                                    + '<td><code>' + escHtml(entry.jar) + '</code></td>'
                                    + '<td>' + (pluginNames || '<em>none</em>') + '</td>'
                                    + '<td><button class="btn-danger" onclick="unloadPlugin(' + JSON.stringify(entry.jar) + ')">Unload</button>'
                                    + ' <button class="btn-danger" onclick="deletePlugin(' + JSON.stringify(entry.jar) + ')">Delete</button></td>'
                                    + '</tr>';
                            });
                            html += '</tbody></table>';
                            container.innerHTML = html;
                        })
                        .catch(e => {
                            document.getElementById('loadedPlugins').innerHTML = '<em>Error loading data.</em>';
                            showMessage('Error fetching loaded plugins: ' + e, 'error');
                        });
                }

                function scanDirectory() {
                    fetch('/pluginManager?action=scan', { headers: { 'Accept': 'application/json' } })
                        .then(r => r.json())
                        .then(data => {
                            const container = document.getElementById('availableJars');
                            if (!data || data.length === 0) {
                                container.innerHTML = '<em>No JAR files found in plugin directory.</em>';
                                return;
                            }
                            let html = '<table><thead><tr><th>File</th><th>Size</th><th>Status</th><th>Actions</th></tr></thead><tbody>';
                            data.forEach(entry => {
                                const sizeKb = (entry.size / 1024).toFixed(1);
                                const badge = entry.loaded
                                    ? '<span class="badge badge-loaded">Loaded</span>'
                                    : '<span class="badge badge-unloaded">Not Loaded</span>';
                                const loadBtn = entry.loaded ? '' :
                                    '<button class="btn-primary" onclick="loadPlugin(' + JSON.stringify(entry.name) + ')">Load</button>';
                                const unloadBtn = entry.loaded ?
                                    '<button class="btn-danger" onclick="unloadPlugin(' + JSON.stringify(entry.path) + ')">Unload</button>' : '';
                                const deleteBtn = '<button class="btn-danger" onclick="deletePlugin(' + JSON.stringify(entry.path) + ')">Delete</button>';
                                html += '<tr>'
                                    + '<td><code>' + escHtml(entry.name) + '</code></td>'
                                    + '<td>' + sizeKb + ' KB</td>'
                                    + '<td>' + badge + '</td>'
                                    + '<td>' + loadBtn + unloadBtn + ' ' + deleteBtn + '</td>'
                                    + '</tr>';
                            });
                            html += '</tbody></table>';
                            container.innerHTML = html;
                        })
                        .catch(e => {
                            document.getElementById('availableJars').innerHTML = '<em>Error scanning directory.</em>';
                            showMessage('Error scanning directory: ' + e, 'error');
                        });
                }

                function loadPlugin(jarName) {
                    const jar = jarName || document.getElementById('jarPath').value.trim();
                    const entryPoint = document.getElementById('entryPoint') ? document.getElementById('entryPoint').value.trim() : '';
                    if (!jar) { showMessage('Please enter a JAR path.', 'error'); return; }
                    const body = new URLSearchParams({ action: 'load', jar });
                    if (entryPoint) body.append('entryPoint', entryPoint);
                    fetch('/pluginManager', { method: 'POST', body })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success) {
                                showMessage('Loaded ' + data.pluginsLoaded + ' plugin(s): ' + (data.plugins || []).join(', '), 'success');
                                refreshLoaded();
                                scanDirectory();
                            } else {
                                showMessage('Error: ' + data.error, 'error');
                            }
                        })
                        .catch(e => showMessage('Request failed: ' + e, 'error'));
                }

                function unloadPlugin(jarPath) {
                    if (!confirm('Unload plugin JAR: ' + jarPath + '?')) return;
                    fetch('/pluginManager', {
                        method: 'POST',
                        body: new URLSearchParams({ action: 'unload', jar: jarPath })
                    })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success) {
                                showMessage('Plugin unloaded: ' + jarPath, 'success');
                                refreshLoaded();
                                scanDirectory();
                            } else {
                                showMessage('Error: ' + data.error, 'error');
                            }
                        })
                        .catch(e => showMessage('Request failed: ' + e, 'error'));
                }
                function deletePlugin(jarPath) {
                    if (!confirm('Delete plugin JAR: ' + jarPath + '?\nThis will permanently remove the file from disk. If loaded, it will be unloaded first.')) return;
                    fetch('/pluginManager', {
                        method: 'POST',
                        body: new URLSearchParams({ action: 'delete', jar: jarPath })
                    })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success) {
                                showMessage('Plugin deleted: ' + jarPath, 'success');
                                refreshLoaded();
                                scanDirectory();
                            } else {
                                showMessage('Error: ' + data.error, 'error');
                            }
                        })
                        .catch(e => showMessage('Request failed: ' + e, 'error'));
                }


                function loadDirectory() {
                    fetch('/pluginManager', {
                        method: 'POST',
                        body: new URLSearchParams({ action: 'loadDirectory' })
                    })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success) {
                                showMessage('Processed ' + data.jarsProcessed + ' JAR(s) from ' + data.directory, 'success');
                                refreshLoaded();
                                scanDirectory();
                            } else {
                                showMessage('Error: ' + data.error, 'error');
                            }
                        })
                        .catch(e => showMessage('Request failed: ' + e, 'error'));
                }

                function uploadPlugin() {
                    const fileInput = document.getElementById('jarFile');
                    const autoLoad = document.getElementById('autoLoad').checked;
                    if (!fileInput.files || fileInput.files.length === 0) {
                        showMessage('Please select a JAR file to upload.', 'error');
                        return;
                    }
                    const formData = new FormData();
                    formData.append('action', 'upload');
                    formData.append('jarFile', fileInput.files[0]);
                    formData.append('autoLoad', autoLoad ? 'true' : 'false');
                    fetch('/pluginManager', { method: 'POST', body: formData })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success) {
                                let msg = 'Uploaded: ' + data.file;
                                if (data.autoLoaded) msg += ' — loaded ' + data.pluginsLoaded + ' plugin(s): ' + (data.plugins || []).join(', ');
                                showMessage(msg, 'success');
                                fileInput.value = '';
                                refreshLoaded();
                                scanDirectory();
                            } else {
                                showMessage('Error: ' + data.error, 'error');
                            }
                        })
                        .catch(e => showMessage('Upload failed: ' + e, 'error'));
                }

                function escHtml(str) {
                    return String(str)
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;');
                }
                 function refreshAuthChains() {
                     fetch('/pluginManager?action=authChains', { headers: { 'Accept': 'application/json' } })
                         .then(r => r.json())
                         .then(data => {
                             const container = document.getElementById('authChains');
                             if (!data || data.length === 0) {
                                 container.innerHTML = '<em>No authorization chains registered.</em>';
                                 return;
                             }
                             let html = '<table><thead><tr><th>Chain Name</th><th>Actions</th></tr></thead><tbody>';
                             data.forEach(entry => {
                                 html += '<tr>'
                                     + '<td><code>' + escHtml(entry.name) + '</code></td>'
                                     + '<td><button class="btn-primary" onclick="startAuthChain(' + JSON.stringify(entry.name) + ')">Start Authorization</button></td>'
                                     + '</tr>';
                             });
                             html += '</tbody></table>';
                             container.innerHTML = html;
                         })
                         .catch(e => {
                             document.getElementById('authChains').innerHTML = '<em>Error loading authorization chains.</em>';
                             showMessage('Error fetching authorization chains: ' + e, 'error');
                         });
                 }
                 function startAuthChain(chainName) {
                     fetch('/pluginManager', {
                         method: 'POST',
                         body: new URLSearchParams({ action: 'startAuth', chain: chainName })
                     })
                         .then(r => r.json())
                         .then(data => {
                             if (data.success && data.sessionId && data.status === 'IN_PROGRESS') {
                                 // Redirect to the interactive authorization step page
                                 window.location.href = '/pluginManager?action=authStep&sessionId=' + encodeURIComponent(data.sessionId);
                             } else if (data.success && data.status === 'completed') {
                                 showMessage('Authorization completed: ' + (data.message || 'No steps required'), 'success');
                             } else if (data.success && data.status === 'COMPLETED') {
                                 showMessage('Authorization completed successfully!', 'success');
                             } else if (data.status === 'FAILED') {
                                 showMessage('Authorization failed: ' + (data.failureReason || 'Unknown reason'), 'error');
                             } else {
                                 showMessage('Error: ' + (data.error || 'Unknown error'), 'error');
                             }
                         })
                         .catch(e => showMessage('Request failed: ' + e, 'error'));
                 }
                
                function executePendingAuth(authId) {
                    fetch('/pluginManager', {
                        method: 'POST',
                        body: new URLSearchParams({ action: 'executePendingAuth', id: authId })
                    })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success && data.sessionId && data.status === 'IN_PROGRESS') {
                                window.location.href = '/pluginManager?action=authStep&sessionId=' + encodeURIComponent(data.sessionId);
                            } else if (data.success && data.status === 'completed') {
                                showMessage('Authorization completed: ' + (data.message || 'No steps required'), 'success');
                                refreshAuthChains();
                                refreshLoaded();
                            } else {
                                showMessage('Error: ' + (data.error || 'Unknown error'), 'error');
                            }
                        })
                        .catch(e => showMessage('Request failed: ' + e, 'error'));
                }
                
                // Initial load
                refreshLoaded();
                 refreshAuthChains();
            </script>
        </body>
        </html>
    """.trimIndent()

  companion object {
    private val log = LoggerFactory.getLogger(PluginManagerServlet::class.java)

    /** Internal parameter names that should not be forwarded to authorization step callbacks */
    private val INTERNAL_PARAMS = setOf("action", "sessionId", "chain")

    /**
     * Safely encode a string for JSON value context.
     */
    private fun jsonEscape(value: String?): String {
      if (value == null) return ""
      return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        .replace("\t", "\\t")
    }
  }
}