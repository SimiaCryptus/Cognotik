package com.simiacryptus.cognotik.webui.servlet.action

import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.webui.servlet.handler.GitOperationHandler
import java.io.File

/**
 * Parameter bag for a [GitAction]; sourced either from form parameters
 * (`?gitAction=...`) or from a JSON body (`POST /.fsapi/v1/git`).
 */
class GitActionContext(
  val gitRoot: File,
  val params: Map<String, Any?> = emptyMap(),
) {
  fun param(name: String): String? = params[name]?.toString()?.takeIf { it.isNotBlank() }
  fun param(name: String, default: String): String = param(name) ?: default
  fun required(name: String): String =
    param(name) ?: throw IllegalArgumentException("Missing required parameter '$name'")

  fun flag(name: String, default: Boolean = false): Boolean =
    param(name)?.let { it.equals("true", ignoreCase = true) || it == "1" } ?: default

  fun git(vararg command: String): String = GitOperationHandler.executeCommand(gitRoot, *command)
}

/**
 * A git operation exposed by the file server. Extensible in the same way as
 * [FsAction]:
 *
 * ```
 * GitAction.register(GitAction("fetch", "git fetch --all", mutating = true) { ctx ->
 *   mapOf("output" to ctx.git("git", "fetch", "--all"))
 * })
 * ```
 */
open class GitAction(
  name: String,
  val description: String,
  val parameters: List<ActionParam> = emptyList(),
  val mutating: Boolean = false,
  val handler: (GitActionContext) -> Map<String, Any?>,
) : DynamicEnum<GitAction>(name) {

  fun describe(): Map<String, Any?> = linkedMapOf(
    "name" to name,
    "description" to description,
    "mutating" to mutating,
    "parameters" to parameters.map { it.describe() }
  )

  companion object {
    fun register(action: GitAction, replace: Boolean = false): GitAction {
      if (replace) unregister(GitAction::class.java, action.name)
      register(GitAction::class.java, action)
      return action
    }

    fun unregister(name: String): Boolean = unregister(GitAction::class.java, name)

    fun values(): List<GitAction> = values(GitAction::class.java)

    fun find(name: String): GitAction? = values().firstOrNull { it.name == name }
  }
}