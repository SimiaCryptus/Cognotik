package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.mcp.McpClientToolProvider
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.model.mcp.MCPException
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolDefinition
import com.simiacryptus.cognotik.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * UI + JSON API for managing and test-connecting per-user MCP server client configurations
 * persisted in [UserSettings] via [ServiceRouter.getUserSettings] and
 * [ServiceRouter.updateUserSettings].
 *
 * Endpoints (all require an authenticated user):
 *
 *  - `GET    /api/mcp`                              -> HTML page, or JSON when `Accept: application/json` / `?format=json`
 *  - `POST   /api/mcp?action=add`                   -> add a new MCP server configuration
 *  - `POST   /api/mcp?action=update`                -> update an existing MCP server configuration
 *  - `POST   /api/mcp?action=remove&id=<id>`        -> remove an MCP server configuration
 *  - `POST   /api/mcp?action=enable&id=<id>`        -> enable a configured MCP server
 *  - `POST   /api/mcp?action=disable&id=<id>`       -> disable a configured MCP server
 *  - `POST   /api/mcp?action=toggle&id=<id>`        -> toggle enabled state
 *  - `POST   /api/mcp?action=test`                  -> test-connect via [McpClientToolProvider.listTools]
 *  - `PUT    /api/mcp`                              -> JSON alias for `action=update`
 *  - `DELETE /api/mcp?id=<id>`                      -> JSON alias for `action=remove`
 *
 * Mutating/action `POST`, `PUT`, and `DELETE` requests must carry the caller's CSRF token
 * (`csrf` parameter/body field or `X-CSRF-Token` header), validated via [User.isSignatureValid].
 */
class McpConfigServlet : HttpServlet() {

  data class McpServerConfig(
    val id: String = "",
    val name: String = "",
    val transport: String = TRANSPORT_STDIO,
    val command: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val workingDir: String? = null,
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    val enabled: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
  )

  data class TestConnectResult(
    val server: McpServerConfig,
    val protocolVersion: String?,
    val serverInfo: JsonNode?,
    val tools: List<MCPToolDefinition>,
  )

  private val mapper = JsonUtil.objectMapper()
  private val userLocks = ConcurrentHashMap<String, Any>()

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val user = authenticate(request, response) ?: return
    val servers = loadServers(user)
    if (wantsJson(request)) {
      writeJson(
        response,
        HttpServletResponse.SC_OK,
        mapOf(
          "user" to userJson(user),
          "csrf" to user.signature,
          "servers" to servers,
        ),
      )
    } else {
      renderPage(response, user, servers)
    }
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    val user = authenticate(request, response) ?: return
    val jsonBody = try {
      readJsonBodyIfPresent(request)
    } catch (e: Exception) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON request body: ${e.message}")
      return
    }
    if (!csrfOk(request, user, jsonBody)) {
      fail(request, response, user, HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token")
      return
    }
    val input = RequestInput(request, jsonBody)
    val action = input.str("action")?.trim()?.lowercase() ?: "add"
    when (action) {
      "add", "create", "new" -> addServer(request, response, user, input)
      "update", "edit" -> updateServer(request, response, user, input)
      "save" -> if (input.str("id").isNullOrBlank()) {
        addServer(request, response, user, input)
      } else {
        updateServer(request, response, user, input)
      }

      "remove", "delete" -> removeServer(request, response, user, input)
      "enable" -> setEnabled(request, response, user, input, enabled = true)
      "disable" -> setEnabled(request, response, user, input, enabled = false)
      "toggle" -> toggleEnabled(request, response, user, input)
      "test", "test-connect", "test_connect", "connect", "list-tools", "listtools" ->
        testConnect(request, response, user, input)

      else -> fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Unknown action: $action")
    }
  }

  override fun doPut(request: HttpServletRequest, response: HttpServletResponse) {
    val user = authenticate(request, response) ?: return
    val jsonBody = try {
      readJsonBodyIfPresent(request)
    } catch (e: Exception) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON request body: ${e.message}")
      return
    }
    if (!csrfOk(request, user, jsonBody)) {
      fail(request, response, user, HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token")
      return
    }
    updateServer(request, response, user, RequestInput(request, jsonBody))
  }

  /** JSON-only alias for `action=remove`. */
  override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) {
    val user = authenticate(request, response) ?: return
    val jsonBody = try {
      readJsonBodyIfPresent(request)
    } catch (e: Exception) {
      writeJson(response, HttpServletResponse.SC_BAD_REQUEST, mapOf("error" to "Invalid JSON request body: ${e.message}"))
      return
    }
    if (!csrfOk(request, user, jsonBody)) {
      writeJson(response, HttpServletResponse.SC_FORBIDDEN, mapOf("error" to "Invalid or missing CSRF token"))
      return
    }
    val input = RequestInput(request, jsonBody)
    val id = input.str("id")?.trim()?.takeIf { it.isNotEmpty() }
    val name = input.str("name")?.trim()?.takeIf { it.isNotEmpty() }
    if (id == null && name == null) {
      writeJson(response, HttpServletResponse.SC_BAD_REQUEST, mapOf("error" to "Missing 'id' or 'name' parameter"))
      return
    }
    val removed = withUserLock(user) {
      val settings = ServiceRouter.getUserSettings(user)
      val current = extractServers(settings).toMutableList()
      val idx = findServerIndex(current, id, name)
      if (idx < 0) null
      else {
        val item = current.removeAt(idx)
        saveServers(user, settings, current)
        item
      }
    }
    if (removed == null) {
      writeJson(response, HttpServletResponse.SC_NOT_FOUND, mapOf("error" to "MCP server configuration not found"))
    } else {
      log.info("Removed MCP server '{}' ({}) for user {}", removed.name, removed.id, user)
      writeJson(response, HttpServletResponse.SC_OK, mapOf("removed" to true, "id" to removed.id, "name" to removed.name))
    }
  }

  private fun addServer(
    request: HttpServletRequest,
    response: HttpServletResponse,
    user: User,
    input: RequestInput,
  ) {
    val parsed = parseServerInput(input, existing = null)
    if (parsed.error != null) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, parsed.error)
      return
    }
    val candidate = parsed.config!!
    val now = Instant.now().toString()
    val toStore = candidate.copy(
      id = candidate.id.ifBlank { UUID.randomUUID().toString() },
      createdAt = now,
      updatedAt = now,
    )

    val conflictError = withUserLock(user) {
      val settings = ServiceRouter.getUserSettings(user)
      val current = extractServers(settings).toMutableList()
      when {
        current.any { it.id == toStore.id } -> "MCP server with id '${toStore.id}' already exists"
        current.any { it.name.equals(toStore.name, ignoreCase = true) } ->
          "MCP server named '${toStore.name}' already exists"

        else -> {
          current.add(toStore)
          saveServers(user, settings, current)
          null
        }
      }
    }
    if (conflictError != null) {
      fail(request, response, user, HttpServletResponse.SC_CONFLICT, conflictError)
      return
    }

    log.info("Added MCP server '{}' ({}) for user {}", toStore.name, toStore.id, user)
    if (wantsJson(request)) {
      writeJson(response, HttpServletResponse.SC_CREATED, mapOf("server" to toStore))
    } else {
      renderPage(response, user, loadServers(user), notice = "Added MCP server '${toStore.name}'.")
    }
  }

  private fun updateServer(
    request: HttpServletRequest,
    response: HttpServletResponse,
    user: User,
    input: RequestInput,
  ) {
    val id = input.str("id")?.trim()?.takeIf { it.isNotEmpty() }
    val lookupName = input.str("originalName")?.trim()?.takeIf { it.isNotEmpty() }
      ?: input.str("name")?.trim()?.takeIf { it.isNotEmpty() }
    if (id == null && lookupName == null) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Missing 'id' or 'name' parameter")
      return
    }

    var errorStatus = HttpServletResponse.SC_BAD_REQUEST
    var errorMessage: String? = null
    var updated: McpServerConfig? = null

    withUserLock(user) {
      val settings = ServiceRouter.getUserSettings(user)
      val current = extractServers(settings).toMutableList()
      val idx = findServerIndex(current, id, lookupName)
      if (idx < 0) {
        errorStatus = HttpServletResponse.SC_NOT_FOUND
        errorMessage = "MCP server configuration not found"
        return@withUserLock
      }
      val existing = current[idx]
      val parsed = parseServerInput(input, existing = existing)
      if (parsed.error != null) {
        errorStatus = HttpServletResponse.SC_BAD_REQUEST
        errorMessage = parsed.error
        return@withUserLock
      }
      val candidate = parsed.config!!.copy(
        id = existing.id,
        createdAt = existing.createdAt,
        updatedAt = Instant.now().toString(),
      )
      if (current.indices.any { it != idx && current[it].name.equals(candidate.name, ignoreCase = true) }) {
        errorStatus = HttpServletResponse.SC_CONFLICT
        errorMessage = "MCP server named '${candidate.name}' already exists"
        return@withUserLock
      }
      current[idx] = candidate
      saveServers(user, settings, current)
      updated = candidate
    }

    if (errorMessage != null) {
      fail(request, response, user, errorStatus, errorMessage)
      return
    }

    val result = updated!!
    log.info("Updated MCP server '{}' ({}) for user {}", result.name, result.id, user)
    if (wantsJson(request)) {
      writeJson(response, HttpServletResponse.SC_OK, mapOf("server" to result))
    } else {
      renderPage(response, user, loadServers(user), notice = "Updated MCP server '${result.name}'.")
    }
  }

  private fun removeServer(
    request: HttpServletRequest,
    response: HttpServletResponse,
    user: User,
    input: RequestInput,
  ) {
    val id = input.str("id")?.trim()?.takeIf { it.isNotEmpty() }
    val name = input.str("name")?.trim()?.takeIf { it.isNotEmpty() }
    if (id == null && name == null) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Missing 'id' or 'name' parameter")
      return
    }

    val removed = withUserLock(user) {
      val settings = ServiceRouter.getUserSettings(user)
      val current = extractServers(settings).toMutableList()
      val idx = findServerIndex(current, id, name)
      if (idx < 0) null
      else {
        val item = current.removeAt(idx)
        saveServers(user, settings, current)
        item
      }
    }

    if (removed == null) {
      fail(request, response, user, HttpServletResponse.SC_NOT_FOUND, "MCP server configuration not found")
      return
    }

    log.info("Removed MCP server '{}' ({}) for user {}", removed.name, removed.id, user)
    if (wantsJson(request)) {
      writeJson(response, HttpServletResponse.SC_OK, mapOf("removed" to true, "id" to removed.id, "name" to removed.name))
    } else {
      renderPage(response, user, loadServers(user), notice = "Removed MCP server '${removed.name}'.")
    }
  }

  private fun setEnabled(
    request: HttpServletRequest,
    response: HttpServletResponse,
    user: User,
    input: RequestInput,
    enabled: Boolean,
  ) {
    val id = input.str("id")?.trim()?.takeIf { it.isNotEmpty() }
    val name = input.str("name")?.trim()?.takeIf { it.isNotEmpty() }
    if (id == null && name == null) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Missing 'id' or 'name' parameter")
      return
    }

    val updated = withUserLock(user) {
      val settings = ServiceRouter.getUserSettings(user)
      val current = extractServers(settings).toMutableList()
      val idx = findServerIndex(current, id, name)
      if (idx < 0) null
      else {
        val next = current[idx].copy(enabled = enabled, updatedAt = Instant.now().toString())
        current[idx] = next
        saveServers(user, settings, current)
        next
      }
    }

    if (updated == null) {
      fail(request, response, user, HttpServletResponse.SC_NOT_FOUND, "MCP server configuration not found")
      return
    }

    val stateLabel = if (enabled) "Enabled" else "Disabled"
    log.info("{} MCP server '{}' ({}) for user {}", stateLabel, updated.name, updated.id, user)
    if (wantsJson(request)) {
      writeJson(response, HttpServletResponse.SC_OK, mapOf("server" to updated))
    } else {
      renderPage(response, user, loadServers(user), notice = "$stateLabel MCP server '${updated.name}'.")
    }
  }

  private fun toggleEnabled(
    request: HttpServletRequest,
    response: HttpServletResponse,
    user: User,
    input: RequestInput,
  ) {
    val id = input.str("id")?.trim()?.takeIf { it.isNotEmpty() }
    val name = input.str("name")?.trim()?.takeIf { it.isNotEmpty() }
    if (id == null && name == null) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Missing 'id' or 'name' parameter")
      return
    }

    val updated = withUserLock(user) {
      val settings = ServiceRouter.getUserSettings(user)
      val current = extractServers(settings).toMutableList()
      val idx = findServerIndex(current, id, name)
      if (idx < 0) null
      else {
        val next = current[idx].copy(enabled = !current[idx].enabled, updatedAt = Instant.now().toString())
        current[idx] = next
        saveServers(user, settings, current)
        next
      }
    }

    if (updated == null) {
      fail(request, response, user, HttpServletResponse.SC_NOT_FOUND, "MCP server configuration not found")
      return
    }

    val stateLabel = if (updated.enabled) "Enabled" else "Disabled"
    log.info("{} MCP server '{}' ({}) for user {}", stateLabel, updated.name, updated.id, user)
    if (wantsJson(request)) {
      writeJson(response, HttpServletResponse.SC_OK, mapOf("server" to updated))
    } else {
      renderPage(response, user, loadServers(user), notice = "$stateLabel MCP server '${updated.name}'.")
    }
  }

  private fun testConnect(
    request: HttpServletRequest,
    response: HttpServletResponse,
    user: User,
    input: RequestInput,
  ) {
    val id = input.str("id")?.trim()?.takeIf { it.isNotEmpty() }
    val name = input.str("name")?.trim()?.takeIf { it.isNotEmpty() }
    val hasInlineCommand = input.hasNonEmpty("command")

    val saved = if (id != null || (!hasInlineCommand && name != null)) {
      val servers = loadServers(user)
      val idx = findServerIndex(servers, id, name)
      if (idx < 0 && !hasInlineCommand) {
        fail(request, response, user, HttpServletResponse.SC_NOT_FOUND, "MCP server configuration not found")
        return
      }
      if (idx >= 0) servers[idx] else null
    } else null

    val targetConfig = if (hasInlineCommand || saved == null) {
      val parsed = parseServerInput(
        input,
        existing = saved ?: McpServerConfig(name = name ?: "test-mcp-server"),
        allowBlankName = true,
      )
      if (parsed.error != null) {
        fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, parsed.error)
        return
      }
      parsed.config!!
    } else {
      saved
    }

    if (targetConfig.transport != TRANSPORT_STDIO) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Unsupported transport: ${targetConfig.transport}")
      return
    }
    if (targetConfig.command.isEmpty()) {
      fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Command must not be empty")
      return
    }
    val workingDirFile = targetConfig.workingDir?.let { dirPath ->
      val file = File(dirPath)
      if (!file.exists() || !file.isDirectory) {
        fail(request, response, user, HttpServletResponse.SC_BAD_REQUEST, "Working directory does not exist: $dirPath")
        return
      }
      file
    }

    try {
      val result = runTestConnect(targetConfig, workingDirFile)
      log.info(
        "MCP test-connect succeeded for '{}' (user={}, tools={})",
        targetConfig.name,
        user,
        result.tools.size,
      )
      if (wantsJson(request)) {
        writeJson(
          response,
          HttpServletResponse.SC_OK,
          mapOf(
            "success" to true,
            "server" to result.server,
            "protocolVersion" to result.protocolVersion,
            "serverInfo" to result.serverInfo,
            "toolCount" to result.tools.size,
            "tools" to result.tools,
          ),
        )
      } else {
        renderPage(
          response,
          user,
          loadServers(user),
          notice = "Connected to '${targetConfig.name}' (${result.tools.size} tool(s) discovered).",
          testResult = result,
        )
      }
    } catch (e: MCPException) {
      log.warn("MCP test-connect failed for '{}' (user={}): {}", targetConfig.name, user, e.message)
      val status = if (e.code == MCPException.REQUEST_TIMEOUT) {
        HttpServletResponse.SC_GATEWAY_TIMEOUT
      } else {
        HttpServletResponse.SC_BAD_GATEWAY
      }
      val message = "MCP connection failed (${e.code}): ${e.message ?: "Unknown MCP error"}"
      if (wantsJson(request)) {
        writeJson(
          response,
          status,
          mapOf("success" to false, "code" to e.code, "error" to message),
        )
      } else {
        response.status = status
        renderPage(response, user, loadServers(user), error = message)
      }
    } catch (e: Exception) {
      log.warn("MCP test-connect failed for '{}' (user={})", targetConfig.name, user, e)
      val message = "Failed to connect to '${targetConfig.name}': ${e.message ?: e.javaClass.simpleName}"
      if (wantsJson(request)) {
        writeJson(
          response,
          HttpServletResponse.SC_BAD_GATEWAY,
          mapOf("success" to false, "error" to message),
        )
      } else {
        response.status = HttpServletResponse.SC_BAD_GATEWAY
        renderPage(response, user, loadServers(user), error = message)
      }
    }
  }

  private fun runTestConnect(config: McpServerConfig, workingDir: File?): TestConnectResult {
    val provider = McpClientToolProvider.stdio(
      name = config.name.ifBlank { "test-mcp-server" },
      command = config.command,
      env = config.env,
      workingDir = workingDir,
      requestTimeout = Duration.ofSeconds(config.timeoutSeconds),
    )
    try {
      provider.connect()
      val tools = provider.listTools()
      return TestConnectResult(
        server = config,
        protocolVersion = provider.protocolVersion,
        serverInfo = provider.serverInfo,
        tools = tools,
      )
    } finally {
      runCatching { provider.close() }
        .onFailure { log.debug("Error closing test MCP client '{}'", config.name, it) }
    }
  }

  private data class ParsedServerResult(
    val config: McpServerConfig? = null,
    val error: String? = null,
  )

  private fun parseServerInput(
    input: RequestInput,
    existing: McpServerConfig?,
    allowBlankName: Boolean = false,
  ): ParsedServerResult {
    val name = (input.str("name")?.trim() ?: existing?.name ?: "").let {
      if (it.isBlank() && allowBlankName) "test-mcp-server" else it
    }
    if (name.isBlank()) {
      return ParsedServerResult(error = "Missing 'name' parameter")
    }

    val transport = (input.str("transport")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
      ?: existing?.transport
      ?: TRANSPORT_STDIO)
    if (transport != TRANSPORT_STDIO) {
      return ParsedServerResult(error = "Unsupported transport '$transport'; only '$TRANSPORT_STDIO' is supported")
    }

    val command = parseCommand(input, existing?.command)
      ?: return ParsedServerResult(error = "Invalid or missing 'command' parameter")
    if (command.isEmpty()) {
      return ParsedServerResult(error = "Command must not be empty")
    }

    val envResult = parseEnv(input, existing?.env ?: emptyMap())
    if (envResult.second != null) {
      return ParsedServerResult(error = envResult.second)
    }

    val workingDir = if (input.has("workingDir")) {
      input.str("workingDir")?.trim()?.takeIf { it.isNotEmpty() }
    } else {
      existing?.workingDir
    }

    val timeoutSeconds = parseTimeoutSeconds(input, existing?.timeoutSeconds ?: DEFAULT_TIMEOUT_SECONDS)
      ?: return ParsedServerResult(error = "Invalid 'timeoutSeconds' parameter (must be a positive integer)")

    val enabled = parseEnabled(input, existing?.enabled ?: true)
    val id = input.str("id")?.trim()?.takeIf { it.isNotEmpty() } ?: existing?.id ?: ""

    return ParsedServerResult(
      config = McpServerConfig(
        id = id,
        name = name,
        transport = transport,
        command = command,
        env = envResult.first,
        workingDir = workingDir,
        timeoutSeconds = timeoutSeconds,
        enabled = enabled,
      )
    )
  }

  private fun parseCommand(input: RequestInput, fallback: List<String>?): List<String>? {
    val jsonCmd = input.jsonNode("command")
    val jsonArgs = input.jsonNode("args")

    if (jsonCmd != null && !jsonCmd.isNull) {
      val base = when {
        jsonCmd.isArray -> jsonCmd.mapNotNull { it.asText()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        jsonCmd.isTextual -> {
          val raw = jsonCmd.asText().trim()
          if (raw.isEmpty()) emptyList()
          else if (jsonArgs != null && !jsonArgs.isNull) listOf(raw)
          else splitShellTokens(raw)
        }

        else -> return null
      }
      val extraArgs = when {
        jsonArgs == null || jsonArgs.isNull -> emptyList()
        jsonArgs.isArray -> jsonArgs.mapNotNull { it.asText()?.takeIf { s -> s.isNotEmpty() } }
        jsonArgs.isTextual -> parseArgsText(jsonArgs.asText())
        else -> return null
      }
      return base + extraArgs
    }

    val paramCmd = input.str("command")?.trim()
    val paramArgs = input.str("args")
    if (paramCmd != null) {
      if (paramCmd.isEmpty()) return emptyList()
      val extraArgs = if (!paramArgs.isNullOrBlank()) parseArgsText(paramArgs) else emptyList()
      val base = if (extraArgs.isNotEmpty()) listOf(paramCmd) else splitShellTokens(paramCmd)
      return base + extraArgs
    }

    return fallback
  }

  private fun parseArgsText(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    return trimmed
      .lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .flatMap { splitShellTokens(it).asSequence() }
      .toList()
  }

  private fun parseEnv(input: RequestInput, fallback: Map<String, String>): Pair<Map<String, String>, String?> {
    val jsonEnv = input.jsonNode("env")
    if (jsonEnv != null && !jsonEnv.isNull) {
      return when {
        jsonEnv.isObject -> {
          val map = linkedMapOf<String, String>()
          jsonEnv.fields().forEach { (k, v) ->
            val key = k.trim()
            if (key.isEmpty()) return emptyMap<String, String>() to "Environment variable key must not be blank"
            map[key] = if (v.isNull) "" else v.asText()
          }
          map to null
        }

        jsonEnv.isTextual -> parseEnvLines(jsonEnv.asText())
        else -> emptyMap<String, String>() to "Invalid 'env' format; expected object or KEY=VALUE lines"
      }
    }
    if (input.has("env")) {
      return parseEnvLines(input.str("env").orEmpty())
    }
    return fallback to null
  }

  private fun parseEnvLines(raw: String): Pair<Map<String, String>, String?> {
    val map = linkedMapOf<String, String>()
    for (line in raw.lineSequence()) {
      val trimmed = line.trim()
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
      val eq = trimmed.indexOf('=')
      if (eq <= 0) {
        return emptyMap<String, String>() to "Invalid environment variable line (expected KEY=VALUE): $trimmed"
      }
      val key = trimmed.substring(0, eq).trim()
      val value = trimmed.substring(eq + 1)
      map[key] = value
    }
    return map to null
  }

  private fun parseTimeoutSeconds(input: RequestInput, fallback: Long): Long? {
    val node = input.jsonNode("timeoutSeconds") ?: input.jsonNode("timeout")
    if (node != null && !node.isNull) {
      val value = when {
        node.isIntegralNumber -> node.asLong()
        node.isTextual -> node.asText().trim().toLongOrNull() ?: return null
        else -> return null
      }
      return value.takeIf { it > 0 }
    }
    val raw = input.str("timeoutSeconds") ?: input.str("timeout")
    if (raw != null) {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) return fallback
      val value = trimmed.toLongOrNull() ?: return null
      return value.takeIf { it > 0 }
    }
    return fallback
  }

  private fun parseEnabled(input: RequestInput, fallback: Boolean): Boolean {
    val node = input.jsonNode("enabled")
    if (node != null && !node.isNull) {
      return when {
        node.isBoolean -> node.asBoolean()
        node.isTextual -> parseBooleanText(node.asText()) ?: fallback
        else -> fallback
      }
    }
    val raw = input.str("enabled")
    if (raw != null) {
      return parseBooleanText(raw) ?: fallback
    }
    if (input.has("enabledPresent")) {
      return false
    }
    return fallback
  }

  private fun parseBooleanText(raw: String): Boolean? = when (raw.trim().lowercase()) {
    "true", "1", "yes", "on" -> true
    "false", "0", "no", "off" -> false
    else -> null
  }

  private fun loadServers(user: User): List<McpServerConfig> = try {
    extractServers(ServiceRouter.getUserSettings(user))
  } catch (e: Exception) {
    log.warn("Failed to load MCP server configurations for {}", user, e)
    emptyList()
  }

  private fun extractServers(settings: UserSettings): List<McpServerConfig> {
    val raw = settings.etc[MCP_SERVERS_KEY] ?: return emptyList()
    return try {
      mapper.convertValue(raw, SERVER_LIST_TYPE)
    } catch (e: Exception) {
      log.warn("Failed to deserialize MCP server configurations from UserSettings", e)
      emptyList()
    }
  }

  private fun saveServers(user: User, settings: UserSettings, servers: List<McpServerConfig>) {
    val serialized = mapper.convertValue(servers, MAP_LIST_TYPE)
    settings.etc[MCP_SERVERS_KEY] = serialized
    ServiceRouter.updateUserSettings(user, settings)
  }

  private fun findServerIndex(servers: List<McpServerConfig>, id: String?, name: String?): Int {
    if (id != null) {
      val byId = servers.indexOfFirst { it.id == id }
      if (byId >= 0) return byId
    }
    if (name != null) {
      return servers.indexOfFirst { it.name.equals(name, ignoreCase = true) }
    }
    return -1
  }

  private inline fun <T> withUserLock(user: User, block: () -> T): T {
    val key = user.id.ifBlank { user.redactedEmail }
    val lock = userLocks.computeIfAbsent(key) { Any() }
    return synchronized(lock) { block() }
  }

  private fun authenticate(request: HttpServletRequest, response: HttpServletResponse): User? {
    val user = ServiceRouter.authenticate(request)
    if (user == null) {
      if (!response.isCommitted) {
        if (wantsJson(request)) {
          writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, mapOf("error" to "Authentication required"))
        } else {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
        }
      }
      return null
    }
    return user
  }

  private fun csrfOk(request: HttpServletRequest, user: User, jsonBody: JsonNode?): Boolean {
    val presented = request.getHeader(CSRF_HEADER)
      ?: request.getParameter("csrf")
      ?: jsonBody?.get("csrf")?.takeIf { it.isTextual }?.asText()
    return user.isSignatureValid(presented)
  }

  private fun readJsonBodyIfPresent(request: HttpServletRequest): JsonNode? {
    if (!isJsonRequest(request)) return null
    val raw = request.reader.readText()
    if (raw.isBlank()) return null
    return mapper.readTree(raw)
  }

  private fun isJsonRequest(request: HttpServletRequest): Boolean =
    request.contentType?.contains("application/json", ignoreCase = true) == true

  private fun wantsJson(request: HttpServletRequest): Boolean {
    if (request.getParameter("format").equals("json", ignoreCase = true)) return true
    val accept = request.getHeader("Accept").orEmpty()
    if (accept.contains("text/html", ignoreCase = true)) return false
    if (accept.contains("application/json", ignoreCase = true) || accept.contains("text/json", ignoreCase = true)) {
      return true
    }
    return isJsonRequest(request)
  }

  private fun writeJson(response: HttpServletResponse, status: Int, body: Any) {
    response.status = status
    response.contentType = "application/json"
    response.characterEncoding = "UTF-8"
    response.setHeader("Cache-Control", "no-store")
    mapper.writeValue(response.writer, body)
  }

  private fun fail(
    request: HttpServletRequest,
    response: HttpServletResponse,
    user: User,
    status: Int,
    message: String,
  ) {
    if (wantsJson(request)) {
      writeJson(response, status, mapOf("error" to message))
    } else {
      response.status = status
      renderPage(response, user, loadServers(user), error = message)
    }
  }

  private fun userJson(user: User) = mapOf(
    "id" to user.id,
    "name" to user.name,
    "email" to user.redactedEmail,
  )

  private fun renderPage(
    response: HttpServletResponse,
    user: User,
    servers: List<McpServerConfig>,
    notice: String? = null,
    error: String? = null,
    testResult: TestConnectResult? = null,
  ) {
    val csrf = user.signature
    response.contentType = "text/html"
    response.characterEncoding = "UTF-8"
    response.setHeader("Cache-Control", "no-store")

    val rows = if (servers.isEmpty()) {
      """<tr><td colspan="7" class="empty">No MCP servers are configured for this account.</td></tr>"""
    } else {
      servers.joinToString("\n") { server ->
        val statusClass = if (server.enabled) "badge enabled" else "badge disabled"
        val statusText = if (server.enabled) "Enabled" else "Disabled"
        val toggleAction = if (server.enabled) "disable" else "enable"
        val toggleLabel = if (server.enabled) "Disable" else "Enable"
        val envSummary = if (server.env.isEmpty()) "-" else server.env.keys.joinToString(", ")
        val cmdText = server.command.joinToString(" ")
        """
        <tr${if (!server.enabled) " class=\"disabled-row\"" else ""}>
          <td><strong>${esc(server.name)}</strong><div class="muted">${esc(server.id)}</div></td>
          <td><code>${esc(server.transport)}</code></td>
          <td><code>${esc(cmdText)}</code></td>
          <td>${esc(server.workingDir ?: "-")}</td>
          <td>${esc(envSummary)}</td>
          <td><span class="$statusClass">$statusText</span><div class="muted">${server.timeoutSeconds}s</div></td>
          <td class="actions">
            <button type="button" onclick="editServer('${esc(server.id)}')">Edit</button>
            <form method="post" class="inline">
              <input type="hidden" name="action" value="test"/>
              <input type="hidden" name="csrf" value="${esc(csrf)}"/>
              <input type="hidden" name="id" value="${esc(server.id)}"/>
              <button type="submit">Test</button>
            </form>
            <form method="post" class="inline">
              <input type="hidden" name="action" value="$toggleAction"/>
              <input type="hidden" name="csrf" value="${esc(csrf)}"/>
              <input type="hidden" name="id" value="${esc(server.id)}"/>
              <button type="submit">$toggleLabel</button>
            </form>
            <form method="post" class="inline" onsubmit="return confirm('Remove MCP server ${esc(server.name)}?')">
              <input type="hidden" name="action" value="remove"/>
              <input type="hidden" name="csrf" value="${esc(csrf)}"/>
              <input type="hidden" name="id" value="${esc(server.id)}"/>
              <button type="submit" class="danger">Remove</button>
            </form>
          </td>
        </tr>
        """.trimIndent()
      }
    }

    val banners = buildString {
      if (error != null) append("""<div class="banner error">${esc(error)}</div>""")
      if (notice != null) append("""<div class="banner notice">${esc(notice)}</div>""")
    }

    val testSection = if (testResult == null) "" else {
      val toolRows = if (testResult.tools.isEmpty()) {
        """<tr><td colspan="2" class="empty">Server returned 0 tools.</td></tr>"""
      } else {
        testResult.tools.joinToString("\n") { tool ->
          val node = mapper.valueToTree<JsonNode>(tool)
          val toolName = node.path("name").asText("")
          val toolDesc = node.path("description").asText("")
          """
          <tr>
            <td><code>${esc(toolName)}</code></td>
            <td>${esc(toolDesc)}</td>
          </tr>
          """.trimIndent()
        }
      }
      """
      <div class="panel">
        <h2>Test Connection Result: ${esc(testResult.server.name)}</h2>
        <p>
          <strong>Protocol:</strong> <code>${esc(testResult.protocolVersion ?: "-")}</code>
          ${testResult.serverInfo?.let { " | <strong>Server Info:</strong> <code>${esc(it.toString())}</code>" } ?: ""}
        </p>
        <table>
          <thead><tr><th>Tool Name</th><th>Description</th></tr></thead>
          <tbody>$toolRows</tbody>
        </table>
      </div>
      """.trimIndent()
    }

    val serversJson = safeJsonForScript(mapper.writeValueAsString(servers))

    response.writer.write(
      """
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <meta name="color-scheme" content="light dark"/>
        <title>MCP Servers</title>
        <!-- Central theme system: tokens, manifest, then runtime manager (order matters). -->
        <link href="/themes.css" id="theme-stylesheet" rel="stylesheet"/>
        <script src="/themes.js"></script>
        <script src="/modules/theme.js"></script>
        <script>
          // Follow palette/mode changes made in other windows (e.g. the parent of an iframe).
          window.addEventListener('storage', function (e) {
            if (!window.ThemeManager || !e.newValue) return;
            if (e.key === ThemeManager.PALETTE_STORAGE_KEY) ThemeManager.setPalette(e.newValue);
            if (e.key === ThemeManager.STORAGE_KEY) ThemeManager.setTheme(e.newValue);
          });
        </script>
        <style>
          body {
            font-family: system-ui, sans-serif; margin: 2rem auto; max-width: 72rem; padding: 0 1rem;
            background: var(--color-canvas, #fff); color: var(--color-text, #222);
          }
          h1, h2 { color: var(--color-heading, var(--color-text, #222)); }
          table { border-collapse: collapse; width: 100%; margin-top: 1rem; }
          th, td { border-bottom: 1px solid var(--color-border, #ddd); padding: .55rem; text-align: left; font-size: .9rem; vertical-align: top; }
          th { color: var(--color-heading, var(--color-text, #222)); }
          tr.disabled-row { opacity: .65; }
          td.empty { text-align: center; color: var(--color-text-muted, #666); padding: 1.25rem; }
          td.actions { white-space: nowrap; }
          .muted { font-size: .78rem; color: var(--color-text-muted, #666); margin-top: .15rem; }
          code { background: var(--color-surface-alt, #f4f4f4); color: var(--color-text, #222); padding: .15rem .35rem; border-radius: 3px; word-break: break-word; }
          .badge { display: inline-block; padding: .15rem .45rem; border-radius: 3px; font-size: .78rem; font-weight: 600; }
          .badge.enabled { background: color-mix(in srgb, var(--color-success, #27ae60) 18%, var(--color-surface, #fff)); color: var(--color-success, #27ae60); }
          .badge.disabled { background: color-mix(in srgb, var(--color-text-muted, #666) 18%, var(--color-surface, #fff)); color: var(--color-text-muted, #666); }
          .banner { padding: .75rem 1rem; border-radius: 4px; margin: .75rem 0; color: var(--color-text, #222); border-left: 4px solid var(--color-border-strong, #999); }
          .banner.error {
            background: color-mix(in srgb, var(--color-danger, #c0392b) 15%, var(--color-surface, #fff));
            border-left-color: var(--color-danger, #c0392b);
          }
          .banner.notice {
            background: color-mix(in srgb, var(--color-brand, #2980b9) 15%, var(--color-surface, #fff));
            border-left-color: var(--color-brand, #2980b9);
          }
          .panel {
            margin-top: 1.5rem; padding: 1rem 1.25rem; border: 1px solid var(--color-border, #ddd);
            border-radius: 6px; background: var(--color-surface, #fff);
          }
          .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr)); gap: .85rem; }
          label.field { display: flex; flex-direction: column; gap: .25rem; font-size: .88rem; font-weight: 500; }
          label.checkbox { display: flex; align-items: center; gap: .45rem; font-size: .9rem; margin-top: .5rem; }
          input[type="text"], input[type="number"], textarea {
            padding: .45rem .6rem; border-radius: 4px; font: inherit;
            background: var(--color-surface, #fff); color: var(--color-text, #222);
            border: 1px solid var(--color-border-strong, #aaa);
          }
          textarea { font-family: ui-monospace, monospace; font-size: .85rem; min-height: 4.5rem; }
          button {
            padding: .4rem .8rem; border-radius: 4px; font: inherit; cursor: pointer;
            background: var(--color-surface, #fff); color: var(--color-text, #222);
            border: 1px solid var(--color-border-strong, #aaa);
          }
          button:hover { border-color: var(--color-brand, #2980b9); }
          button.primary {
            background: var(--color-brand, #2980b9); color: #fff; border-color: var(--color-brand, #2980b9);
          }
          button.danger { color: var(--color-danger, #c0392b); border-color: var(--color-danger, #c0392b); }
          button.danger:hover { color: var(--color-danger-hover, #a93226); border-color: var(--color-danger-hover, #a93226); }
          button:focus-visible, input:focus-visible, textarea:focus-visible {
            outline: none; border-color: var(--color-brand, #2980b9);
            box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-brand, #2980b9) 30%, transparent);
          }
          form.inline { display: inline; }
          .form-actions { display: flex; gap: .6rem; flex-wrap: wrap; margin-top: 1rem; align-items: center; }
          #ajax-test-result { margin-top: 1rem; display: none; }
          html[data-scheme="dark"] .banner, html[data-scheme="dark"] .panel { box-shadow: 0 1px 4px rgba(0, 0, 0, 0.4); }
        </style>
      </head>
      <body>
        <h1>MCP Server Configurations</h1>
        <p>Signed in as <strong>${esc(user.name)}</strong> (<code>${esc(user.redactedEmail)}</code>)</p>
        $banners
        $testSection
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Transport</th>
              <th>Command</th>
              <th>Working Dir</th>
              <th>Env Vars</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
          $rows
          </tbody>
        </table>

        <div class="panel">
          <h2 id="form-title">Add MCP Server</h2>
          <form method="post" id="mcp-form">
            <input type="hidden" name="action" id="field-action" value="add"/>
            <input type="hidden" name="id" id="field-id" value=""/>
            <input type="hidden" name="csrf" id="field-csrf" value="${esc(csrf)}"/>
            <input type="hidden" name="enabledPresent" value="1"/>
            <div class="grid">
              <label class="field">Name
                <input type="text" name="name" id="field-name" placeholder="filesystem" required/>
              </label>
              <label class="field">Command (executable or full command)
                <input type="text" name="command" id="field-command" placeholder="npx" required/>
              </label>
              <label class="field">Working Directory (optional)
                <input type="text" name="workingDir" id="field-workingDir" placeholder="/path/to/workspace"/>
              </label>
              <label class="field">Timeout (seconds)
                <input type="number" name="timeoutSeconds" id="field-timeoutSeconds" min="1" value="$DEFAULT_TIMEOUT_SECONDS" required/>
              </label>
            </div>
            <div class="grid" style="margin-top: .85rem;">
              <label class="field">Arguments (one per line or space-separated)
                <textarea name="args" id="field-args" placeholder="-y&#10;@modelcontextprotocol/server-filesystem&#10;/tmp"></textarea>
              </label>
              <label class="field">Environment Variables (KEY=VALUE per line)
                <textarea name="env" id="field-env" placeholder="API_KEY=..."></textarea>
              </label>
            </div>
            <label class="checkbox">
              <input type="checkbox" name="enabled" id="field-enabled" value="true" checked/>
              Enabled
            </label>
            <div class="form-actions">
              <button type="submit" class="primary" id="submit-btn">Add Server</button>
              <button type="button" onclick="testFormConnection()">Test Connection</button>
              <button type="button" id="cancel-edit-btn" onclick="resetForm()" style="display:none;">Cancel Edit</button>
            </div>
          </form>
          <div id="ajax-test-result"></div>
        </div>

        <script id="mcp-servers-data" type="application/json">$serversJson</script>
        <script>
          const serversById = {};
          try {
            const list = JSON.parse(document.getElementById('mcp-servers-data').textContent || '[]');
            list.forEach(function (s) { serversById[s.id] = s; });
          } catch (e) {}

          function editServer(id) {
            const s = serversById[id];
            if (!s) return;
            document.getElementById('form-title').textContent = 'Edit MCP Server: ' + s.name;
            document.getElementById('field-action').value = 'update';
            document.getElementById('field-id').value = s.id;
            document.getElementById('field-name').value = s.name || '';
            const cmd = Array.isArray(s.command) ? s.command : [];
            document.getElementById('field-command').value = cmd.length > 0 ? cmd[0] : '';
            document.getElementById('field-args').value = cmd.length > 1 ? cmd.slice(1).join('\n') : '';
            document.getElementById('field-workingDir').value = s.workingDir || '';
            document.getElementById('field-timeoutSeconds').value = s.timeoutSeconds || $DEFAULT_TIMEOUT_SECONDS;
            const envLines = Object.entries(s.env || {}).map(function (kv) { return kv[0] + '=' + kv[1]; });
            document.getElementById('field-env').value = envLines.join('\n');
            document.getElementById('field-enabled').checked = !!s.enabled;
            document.getElementById('submit-btn').textContent = 'Update Server';
            document.getElementById('cancel-edit-btn').style.display = 'inline-block';
            document.getElementById('mcp-form').scrollIntoView({ behavior: 'smooth' });
          }

          function resetForm() {
            document.getElementById('form-title').textContent = 'Add MCP Server';
            document.getElementById('field-action').value = 'add';
            document.getElementById('field-id').value = '';
            document.getElementById('mcp-form').reset();
            document.getElementById('field-enabled').checked = true;
            document.getElementById('submit-btn').textContent = 'Add Server';
            document.getElementById('cancel-edit-btn').style.display = 'none';
          }

          async function testFormConnection() {
            const box = document.getElementById('ajax-test-result');
            box.style.display = 'block';
            box.className = 'banner notice';
            box.textContent = 'Testing MCP server connection...';
            const csrf = document.getElementById('field-csrf').value;
            const payload = {
              action: 'test',
              name: document.getElementById('field-name').value || 'test-mcp-server',
              command: document.getElementById('field-command').value,
              args: document.getElementById('field-args').value,
              workingDir: document.getElementById('field-workingDir').value || null,
              timeoutSeconds: Number(document.getElementById('field-timeoutSeconds').value || $DEFAULT_TIMEOUT_SECONDS),
              env: document.getElementById('field-env').value
            };
            try {
              const res = await fetch(window.location.pathname, {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/json',
                  'Accept': 'application/json',
                  'X-CSRF-Token': csrf
                },
                body: JSON.stringify(payload)
              });
              const data = await res.json();
              if (res.ok && data.success) {
                box.className = 'banner notice';
                const toolNames = (data.tools || []).map(function (t) { return t.name; }).join(', ');
                box.textContent = 'Connected (' + (data.protocolVersion || '-') + '). ' +
                  (data.toolCount || 0) + ' tool(s): ' + (toolNames || 'none');
              } else {
                box.className = 'banner error';
                box.textContent = data.error || ('Connection failed (HTTP ' + res.status + ')');
              }
            } catch (err) {
              box.className = 'banner error';
              box.textContent = 'Request failed: ' + err.message;
            }
          }
        </script>
      </body>
      </html>
      """.trimIndent()
    )
  }

  private class RequestInput(
    private val request: HttpServletRequest,
    private val jsonBody: JsonNode?,
  ) {
    fun has(key: String): Boolean =
      request.getParameterMap().containsKey(key) || (jsonBody != null && jsonBody.has(key))

    fun hasNonEmpty(key: String): Boolean {
      val node = jsonBody?.get(key)
      if (node != null && !node.isNull) {
        return if (node.isArray) node.size() > 0 else node.asText().isNotBlank()
      }
      return !request.getParameter(key).isNullOrBlank()
    }

    fun str(key: String): String? {
      val param = request.getParameter(key)
      if (param != null) return param
      val node = jsonBody?.get(key) ?: return null
      return if (node.isNull || node.isContainerNode) null else node.asText()
    }

    fun jsonNode(key: String): JsonNode? = jsonBody?.get(key)
  }

  companion object {
    private val log = LoggerFactory.getLogger(McpConfigServlet::class.java)
    private const val CSRF_HEADER = "X-CSRF-Token"
    private const val MCP_SERVERS_KEY = "mcpServers"
    private const val TRANSPORT_STDIO = "stdio"
    private const val DEFAULT_TIMEOUT_SECONDS = 120L

    private val SERVER_LIST_TYPE = object : TypeReference<List<McpServerConfig>>() {}
    private val MAP_LIST_TYPE = object : TypeReference<List<Map<String, Any?>>>() {}

    internal fun splitShellTokens(input: String): List<String> {
      val tokens = mutableListOf<String>()
      val current = StringBuilder()
      var inSingle = false
      var inDouble = false
      var escaped = false
      var hasToken = false

      for (ch in input) {
        when {
          escaped -> {
            current.append(ch)
            escaped = false
            hasToken = true
          }

          ch == '\\' && !inSingle -> {
            escaped = true
            hasToken = true
          }

          ch == '\'' && !inDouble -> {
            inSingle = !inSingle
            hasToken = true
          }

          ch == '"' && !inSingle -> {
            inDouble = !inDouble
            hasToken = true
          }

          ch.isWhitespace() && !inSingle && !inDouble -> {
            if (hasToken) {
              tokens.add(current.toString())
              current.setLength(0)
              hasToken = false
            }
          }

          else -> {
            current.append(ch)
            hasToken = true
          }
        }
      }
      if (escaped) current.append('\\')
      if (hasToken) tokens.add(current.toString())
      return tokens
    }

    private fun safeJsonForScript(json: String): String = json
      .replace("<", "\\u003c")
      .replace(">", "\\u003e")
      .replace("&", "\\u0026")
      .replace("\u2028", "\\u2028")
      .replace("\u2029", "\\u2029")

    private fun esc(value: String): String = value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
  }
}