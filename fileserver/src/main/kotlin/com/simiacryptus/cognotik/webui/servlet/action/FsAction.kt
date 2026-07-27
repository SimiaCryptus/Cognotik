package com.simiacryptus.cognotik.webui.servlet.action

import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.webui.servlet.handler.FsApiConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File

/** Machine-readable parameter description surfaced by `/.fsapi/v1/actions`. */
data class ActionParam(
  val name: String,
  val type: String = "string",
  val required: Boolean = false,
  /** "query", "body", "header" or "path". */
  val location: String = "query",
  val description: String = "",
  val default: Any? = null,
) {
  fun describe(): Map<String, Any?> = linkedMapOf(
    "name" to name,
    "type" to type,
    "required" to required,
    "in" to location,
    "description" to description,
    "default" to default
  )
}

/** Everything an [FsAction] handler needs; the root is already validated. */
class FsActionContext(
  val method: String,
  val op: String,
  val req: HttpServletRequest,
  val resp: HttpServletResponse,
  val root: File,
  val config: FsApiConfig,
)

/**
 * An FS API v1 operation, keyed by `"<METHOD> <op>"`.
 *
 * Because this is a [DynamicEnum], downstream modules can add new
 * operations (or replace built-ins) at runtime:
 *
 * ```
 * FsAction.register(FsAction("hash", "GET", "sha256 of a file") { ctx -> ... })
 * ```
 *
 * Registered actions are automatically dispatched by `FsApiHandler` and
 * automatically documented by `GET /.fsapi/v1/actions`.
 */
open class FsAction(
  val op: String,
  method: String,
  val description: String,
  val parameters: List<ActionParam> = emptyList(),
  /** Capability name in `/meta.capabilities` that gates this action, if any. */
  val requiresCapability: String? = null,
  mutating: Boolean? = null,
  val handler: (FsActionContext) -> Unit,
) : DynamicEnum<FsAction>(key(method, op)) {

  val method: String = method.uppercase()

  /** Mutating actions require the `X-Fs-Api` header (CSRF mitigation). */
  val mutating: Boolean = mutating ?: (this.method !in READ_ONLY_METHODS)

  fun describe(): Map<String, Any?> = linkedMapOf(
    "name" to name,
    "op" to op,
    "method" to method,
    "description" to description,
    "mutating" to mutating,
    "capability" to requiresCapability,
    "parameters" to parameters.map { it.describe() }
  )

  companion object {
    private val READ_ONLY_METHODS = setOf("GET", "HEAD", "OPTIONS")

    fun key(method: String, op: String): String = "${method.uppercase()} ${op.trim('/')}"

    fun register(action: FsAction, replace: Boolean = false): FsAction {
      if (replace) unregister(FsAction::class.java, action.name)
      register(FsAction::class.java, action)
      return action
    }

    fun unregister(method: String, op: String): Boolean =
      unregister(FsAction::class.java, key(method, op))

    fun values(): List<FsAction> = values(FsAction::class.java)

    fun find(method: String, op: String): FsAction? {
      val name = key(method, op)
      return values().firstOrNull { it.name == name }
    }
  }
}