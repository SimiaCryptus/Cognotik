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
  /** Human label for generated dialogs; defaults to [name]. */
  val label: String? = null,
  /** Non-empty turns the generated control into a select. */
  val options: List<String> = emptyList(),
) {
  fun describe(): Map<String, Any?> = linkedMapOf(
    "name" to name,
    "type" to type,
    "required" to required,
    "in" to location,
    "description" to description,
    "default" to default,
    "label" to label,
    "options" to options
  )

  /** Shape expected by the web UI's `ui.form()` parameter schema. */
  fun uiParam(): Map<String, Any?> = linkedMapOf(
    "id" to name,
    "label" to (label ?: name),
    "type" to when {
      options.isNotEmpty() -> "enum"
      type == "boolean" -> "boolean"
      type == "int" || type == "long" || type == "number" -> "number"
      else -> "string"
    },
    "options" to options.ifEmpty { null },
    "required" to required,
    "default" to default,
    "help" to description.ifBlank { null }
  )
}

/** One menu placement; anchors/groups follow docs/ui.md §19.1. */
data class ActionMenu(val anchor: String, val group: String = "5_tools", val order: Int = 100)

/** Selection contract mirrored by the client's `presentationFor`. */
data class ActionSelection(
  val min: Int = 0,
  val max: Int? = null,
  val kinds: List<String> = listOf("file", "dir"),
)

/**
 * Optional presentation for an [FsAction]. When present the action is emitted
 * in the `actions` array of `GET /.fsapi/v1/actions`, which the web UI turns
 * into a first-class action (menus, palette, Alt+Enter) with no JavaScript.
 */
data class ActionUi(
  val title: String,
  val icon: String? = null,
  val category: String? = null,
  val menus: List<ActionMenu> = emptyList(),
  val selection: ActionSelection = ActionSelection(),
  val paletteHidden: Boolean = false,
  /** Parameters supplied by the invocation context rather than the dialog. */
  val hiddenParams: Set<String> = emptySet(),
  /** "none" | "paths" | "first" | "folder". */
  val sendSelection: String = "none",
  /** Query parameter the selection is written to. */
  val selectionParam: String = "path",
) {
  fun describe(): Map<String, Any?> = linkedMapOf(
    "title" to title,
    "icon" to icon,
    "category" to category,
    "paletteHidden" to paletteHidden,
    "sendSelection" to sendSelection
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
  /** Optional web-UI presentation; null = API-only. */
  val ui: ActionUi? = null,
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
    "parameters" to parameters.map { it.describe() },
    "ui" to ui?.describe()
  )

  /**
   * Client-shaped descriptor consumed by `app/contributions.js`. The `endpoint`
   * block is what makes the round trip self-describing: the browser never has
   * to know that, say, `docops` is a POST that takes repeated `path` values.
   */
  fun uiDescriptor(): Map<String, Any?>? {
    val presentation = ui ?: return null
    val id = op.trim('/').replace('/', '.').ifEmpty { method.lowercase() }
    return linkedMapOf(
      "id" to "server.$id",
      "title" to presentation.title,
      "icon" to presentation.icon,
      "category" to (presentation.category ?: "Server"),
      "description" to description,
      "paletteHidden" to presentation.paletteHidden,
      "requires" to listOfNotNull(requiresCapability),
      "menus" to presentation.menus.map {
        linkedMapOf("anchor" to it.anchor, "group" to it.group, "order" to it.order)
      },
      "selection" to linkedMapOf(
        "min" to presentation.selection.min,
        "max" to presentation.selection.max,
        "kinds" to presentation.selection.kinds
      ),
      "params" to parameters.filter { it.name !in presentation.hiddenParams }.map { it.uiParam() },
      "endpoint" to linkedMapOf(
        "op" to op,
        "method" to method,
        "sendSelection" to presentation.sendSelection,
        "selectionParam" to presentation.selectionParam
      )
    )
  }

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