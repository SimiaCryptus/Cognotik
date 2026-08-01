package com.simiacryptus.cognotik.webui.servlet.action

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.defaultUser
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.models
import com.simiacryptus.cognotik.webui.servlet.ApiProviderServlet.Companion.userSettings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The smart/fast model pair used by every agentic surface of the file server
 * (DocOps, AutoFix and the patch chat).
 *
 * Previously this was start-up-only state baked into each action's configuration,
 * so a mount launched without `--smart-model` was permanently useless. It is now
 * *runtime* state: the web UI picks it through `GET|POST {mount}/.fsapi/v1/models`
 * (see [ModelSelectionActions]) and **every action reads it at invocation time**
 * ([smartOr]/[fastOr]), so a change takes effect on the next request with no
 * re-binding and no restart. The value configured at start-up survives only as the
 * fallback passed to those accessors.
 *
 * The contract is deliberately the model **id**, exactly as on the command line,
 * so an id a provider knows but the local registry does not is still usable; the
 * available list is only an affordance.
 *
 * ### User scoping
 * Model *availability* is per-user (it is read from that user's API-key settings),
 * so the resolver installed here takes the [FsActionContext] of the request being
 * served: on a session-backed app server that is the authenticated caller
 * (see [SessionFsRoots.userOf]), on a local mount it is the fixed owner. `null`
 * means "no request in scope" (start-up, CLI) and falls back to the default user.
 */
object ModelSelection {

  @Volatile
  var smart: String? = null
    private set

  @Volatile
  var fast: String? = null
    private set

  private val listeners = CopyOnWriteArrayList<() -> Unit>()

  @Volatile
  private var userFn: (FsActionContext?) -> User = { defaultUser }

  /** Enumerating provider models costs a network round trip, so it is cached per user. */
  private val cache = ConcurrentHashMap<String, Map<String, ChatModel>>()

  /**
   * Publishes the request-scoped user resolver and *overrides* the selection.
   * Hosts (CLI, embedding servers) call this once at start-up; a no-argument lambda
   * still compiles, so `install({ myUser }, ...)` keeps working.
   */
  @Synchronized
  fun install(user: (FsActionContext?) -> User, smart: String? = null, fast: String? = null) {
    userFn = user
    this.smart = smart?.trim()?.ifBlank { null }
    this.fast = fast?.trim()?.ifBlank { null }
    cache.clear()
  }

  /**
   * Same, but *seeds* the selection instead of overriding it: an action installer must
   * not undo a choice the user has already made through the `models` operation.
   */
  @Synchronized
  fun installDefaults(user: (FsActionContext?) -> User, smart: String? = null, fast: String? = null) {
    userFn = user
    if (this.smart == null) this.smart = smart?.trim()?.ifBlank { null }
    if (this.fast == null) this.fast = fast?.trim()?.ifBlank { null }
  }

  /** The user whose credentials/model list apply to [ctx]; never throws. */
  fun userFor(ctx: FsActionContext?): User = try {
    userFn(ctx)
  } catch (e: Exception) {
    System.err.println("warning: could not resolve the request user: ${e.message}")
    defaultUser
  }

  /** Runtime selection wins; the start-up configuration is only the fallback. */
  fun smartOr(fallback: String?): String? = smart ?: fallback?.trim()?.ifBlank { null }

  fun fastOr(fallback: String?): String? = fast ?: fallback?.trim()?.ifBlank { null }

  /** Listeners re-bind the actions; they never run on the caller's behalf. */
  fun onChange(listener: () -> Unit) {
    listeners.add(listener)
  }

  /**
   * `null`/blank means "leave unchanged", so a dialog that only touches one of the
   * two fields does not silently clear the other. Answers true when something
   * actually changed (listeners only fire then).
   */
  @Synchronized
  fun update(smart: String? = null, fast: String? = null): Boolean {
    val nextSmart = smart?.trim()?.ifBlank { null } ?: this.smart
    val nextFast = fast?.trim()?.ifBlank { null } ?: this.fast
    if (nextSmart == this.smart && nextFast == this.fast) return false
    this.smart = nextSmart
    this.fast = nextFast
    for (listener in listeners) {
      try {
        listener()
      } catch (e: Exception) {
        System.err.println("warning: model change listener failed: ${e.message}")
      }
    }
    return true
  }

  fun availableModels(user: User): Map<String, ChatModel> = try {
    user.userSettings().models()
  } catch (e: Exception) {
    System.err.println("warning: could not read user model settings: ${e.message}")
    emptyMap()
  }

  /** Per-user, cached; [refresh] re-queries that user's providers. */
  fun availableModels(ctx: FsActionContext?, refresh: Boolean): Map<String, ChatModel> {
    val user = userFor(ctx)
    val key = cacheKey(user)
    if (!refresh) cache[key]?.takeIf { it.isNotEmpty() }?.let { return it }
    val fresh = availableModels(user)
    if (fresh.isNotEmpty()) cache[key] = fresh
    return if (fresh.isNotEmpty()) fresh else cache[key].orEmpty()
  }

  fun availableModels(refresh: Boolean = false): Map<String, ChatModel> = availableModels(null, refresh)

  fun modelIds(ctx: FsActionContext?, refresh: Boolean): List<String> =
    availableModels(ctx, refresh).values.map { it.modelId }.distinct().sorted()

  fun modelIds(refresh: Boolean = false): List<String> = modelIds(null, refresh)

  /** Drops the enumeration cache (e.g. after API keys change). */
  fun invalidate() = cache.clear()

  fun summary(): String = "smart=${smart ?: "none"} fast=${fast ?: "none"}"

  fun describe(ctx: FsActionContext?, refresh: Boolean): Map<String, Any?> {
    val ids = modelIds(ctx, refresh)
    return linkedMapOf(
      "smart" to smart,
      "fast" to fast,
      "available" to ids,
      "configured" to ids.isNotEmpty(),
    )
  }

  fun describe(refresh: Boolean = false): Map<String, Any?> = describe(null, refresh)

  /* Identity is opaque here; the string form is stable and unique enough for a cache key. */
  private fun cacheKey(user: User): String = user.toString()
}