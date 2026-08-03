package com.simiacryptus.cognotik.webui.servlet.action

import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
import com.simiacryptus.cognotik.webui.servlet.handler.FsErrors
import com.simiacryptus.cognotik.webui.servlet.handler.FsException
import com.simiacryptus.cognotik.webui.servlet.util.FsJson
import jakarta.servlet.http.HttpServletResponse

/**
 * Model selection as an FS API operation:
 *
 * ```
 * GET  {mount}/.fsapi/v1/models[?refresh=true]
 *   -> { "smart": "...", "fast": "...", "available": [...], "configured": true }
 * POST {mount}/.fsapi/v1/models?smart=<id>&fast=<id>
 *   -> { "kind": "toast", ..., "smart": "...", "fast": "..." }
 * ```
 *
 * The POST carries an [ActionUi], so `GET /.fsapi/v1/actions` advertises it and the
 * SPA registers it as a first-class action - Tools menu, command palette, Alt+Enter -
 * with **no client-side code**. Both parameters are [ActionParam.dynamic], so the
 * generated dialog's two dropdowns are filled by calling this same endpoint with
 * `?resolveParam=<name>` when it opens; the list is therefore always the set of
 * models *the calling user's* API keys actually expose (the user is resolved from the
 * request through [ModelSelection.userFor]), and the current choice is labelled.
 *
 * The chosen pair is stored in that same user's settings by [ModelSelection.update] —
 * this action keeps no copy of it — and DocOps, AutoFix and the patch chat read it
 * back when they are invoked, so a change applies to the very next run and affects
 * nobody else's session.
 *
 * Omitting a parameter leaves it unchanged, which is what makes the "(leave
 * unchanged)" entry of each select meaningful.
 */
object ModelSelectionActions {

  const val MODELS_OP = "models"

  @Synchronized
  fun install() {
    FsAction.register(
      FsAction(
        op = MODELS_OP,
        method = "GET",
        description = "Current smart/fast model selection plus the models available to this user",
        parameters = listOf(
          ActionParam("refresh", "boolean", description = "re-query the configured providers")
        ),
        handler = { ctx -> writeJson(ctx.resp, ModelSelection.describe(ctx, refresh(ctx))) },
      ),
      replace = true
    )
    FsAction.register(
      FsAction(
        op = MODELS_OP,
        method = "POST",
        description = "Choose the models used by DocOps, AutoFix and the patch chat",
        parameters = listOf(
          ActionParam(
            "smart",
            label = "Smart model",
            description = "Primary model: planning, patches and doc-ops",
            dynamic = true,
            placeholder = "(leave unchanged)",
          ),
          ActionParam(
            "fast",
            label = "Fast model",
            description = "Secondary model: cheap, high-volume calls",
            dynamic = true,
            placeholder = "(leave unchanged)",
          ),
        ),
        ui = ActionUi(
          title = "Select Models…",
          icon = "🧠",
          category = "Cognotik",
          menus = listOf(ActionMenu("main/tools", group = "8_models", order = 10)),
          /* Applies to the workspace, not to a selection. */
          selection = ActionSelection(min = 0),
          sendSelection = "none",
        ),
        paramResolvers = mapOf<String, (FsActionContext) -> List<ActionOption>>(
          "smart" to { ctx -> options(ctx, ModelSelection.smartFor(ctx), "smart", refresh(ctx)) },
          "fast" to { ctx -> options(ctx, ModelSelection.fastFor(ctx), "fast", refresh(ctx)) },
        ),
        handler = { ctx -> post(ctx) },
      ),
      replace = true
    )
  }

  private fun post(ctx: FsActionContext) {
    /* '?resolveParam=<name>' fills the dialog instead of running the action. */
    if (FsAction.serveParamResolution(ctx)) return
    val smart = param(ctx, "smart")
    val fast = param(ctx, "fast")
    val known = ModelSelection.modelIds(ctx, false)
    for ((role, value) in listOf("smart" to smart, "fast" to fast)) {
      /* Only reject when we have a list to reject against: an id a provider knows
         but the registry does not is still legitimate (as on the command line). */
      if (value != null && known.isNotEmpty() && value !in known) {
        FsErrors.write(
          ctx.resp,
          FsException(
            FsErrorCode.EINVAL, MODELS_OP, null,
            "unknown $role model '$value'; available: ${known.joinToString(", ")}"
          )
        )
        return
      }
    }
    /* Same user the model list was resolved for, so what was offered is what is stored. */
    val user = ModelSelection.userFor(ctx)
    val changed = ModelSelection.update(user, smart, fast)
    val selected = ModelSelection.smartFor(user)
    val payload = LinkedHashMap<String, Any?>()
    payload["kind"] = "toast"
    payload["severity"] = if (selected == null) "warn" else "info"
    payload["message"] = when {
      selected == null -> "No smart model selected — DocOps, AutoFix and the patch chat will refuse to run"
      changed -> "Models updated — ${ModelSelection.summary(user)} (applies to the next DocOps/AutoFix/patch-chat run)"
      else -> "Models unchanged — ${ModelSelection.summary(user)}"
    }
    payload.putAll(ModelSelection.describe(ctx, false))
    writeJson(ctx.resp, payload)
  }

  private fun options(
    ctx: FsActionContext,
    current: String?,
    role: String,
    refresh: Boolean,
  ): List<ActionOption> {
    val ids = ModelSelection.modelIds(ctx, refresh)
    if (ids.isEmpty()) {
      /* No API key configured: still show what is selected rather than nothing. */
      return listOfNotNull(
        current?.let { ActionOption(it, "$it — current $role", "no providers are configured") }
      )
    }
    return ids.map { id ->
      ActionOption(
        value = id,
        label = if (id == current) "$id — current $role" else id,
        description = if (id == current) "currently selected" else null,
      )
    }
  }

  private fun param(ctx: FsActionContext, name: String): String? =
    ctx.req.getParameter(name)?.trim()?.takeIf { it.isNotEmpty() }

  private fun refresh(ctx: FsActionContext): Boolean =
    ctx.req.getParameter("refresh")?.let {
      it.isEmpty() || it.equals("true", ignoreCase = true) || it == "1"
    } ?: false

  private fun writeJson(resp: HttpServletResponse, payload: Any?) {
    if (resp.isCommitted) return
    resp.status = HttpServletResponse.SC_OK
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(FsJson.stringify(payload))
  }
}

val FsActionContext.user: User
  get() = UserProviderImpl().authenticate(req, resp)
    ?: throw IllegalStateException("Authentication failed but no error response was sent")