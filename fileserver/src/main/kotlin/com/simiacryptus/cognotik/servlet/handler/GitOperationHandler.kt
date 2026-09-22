package com.simiacryptus.cognotik.webui.servlet.handler
import com.simiacryptus.cognotik.platform.model.User

import com.simiacryptus.cognotik.webui.servlet.util.FsJson
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Thin HTTP adapter over the [GitActions] registry: the actual behaviour of
 * each operation lives in a `GitAction` DynamicEnum constant, so the set of
 * supported operations is extensible and self-describing
 * (`?gitAction=describe` or `GET /.fsapi/v1/actions`).
 */
object GitOperationHandler {
  private val log = LoggerFactory.getLogger(GitOperationHandler::class.java)

  fun isGitRepository(dir: File?): Boolean {
    if (dir == null) return false
    var current: File? = dir
    while (current != null) {
      if (File(current, ".git").exists()) return true
      current = current.parentFile
    }
    return false
  }

   fun handleGitOperation(
     req: HttpServletRequest,
     resp: HttpServletResponse,
     gitRoot: File?,
     user: User? = null,
     writeAllowed: Boolean = user != null,
   ) {
    GitActions.install()
    val action = req.getParameter("gitAction") ?: ""
    if (gitRoot == null || !gitRoot.exists()) {
      writeJson(
        resp, HttpServletResponse.SC_BAD_REQUEST,
        linkedMapOf("success" to false, "message" to "Git root directory not found")
      )
      return
    }
    val params: Map<String, Any?> = req.parameterMap.mapValues { (_, values) -> values.firstOrNull() }
    try {
      val payload = linkedMapOf<String, Any?>("success" to true)
       payload.putAll(GitActions.execute(action, params, gitRoot, user, writeAllowed))
      writeJson(resp, HttpServletResponse.SC_OK, payload)
     } catch (e: GitAccessDeniedException) {
       log.warn("Denied git operation '$action': ${e.message}")
       writeJson(
         resp, HttpServletResponse.SC_FORBIDDEN,
         linkedMapOf("success" to false, "message" to (e.message ?: "Authentication required"))
       )
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid git operation '$action': ${e.message}")
      writeJson(
        resp, HttpServletResponse.SC_BAD_REQUEST,
        linkedMapOf(
          "success" to false,
          "message" to (e.message ?: "Invalid git action"),
          "available" to GitActions.names()
        )
      )
    } catch (e: Exception) {
      log.error("Error executing git operation: $action", e)
      writeJson(
        resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
        linkedMapOf("success" to false, "message" to (e.message ?: "Unknown error"))
      )
    }
  }

  fun writeJson(resp: HttpServletResponse, status: Int, payload: Any?) {
    if (resp.isCommitted) return
    resp.status = status
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(FsJson.stringify(payload))
  }

  fun executeCommand(workDir: File, vararg command: String): String {
    log.debug("Executing command in ${workDir.absolutePath}: ${command.joinToString(" ")}")
    val processBuilder = ProcessBuilder(*command)
      .directory(workDir)
      .redirectErrorStream(true)
    val process = processBuilder.start()
    val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      log.warn("Command exited with code $exitCode: $output")
    }
    return output
  }
}