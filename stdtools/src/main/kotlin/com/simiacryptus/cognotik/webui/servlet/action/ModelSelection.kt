package com.simiacryptus.cognotik.webui.servlet.action

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
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
  * The selection is **per-user persistent state and nothing else**: it is read from and
  * written to `UserSettings.smartModel` / `UserSettings.fastModel` through
  * [ApplicationServices.fileApplicationServices]'s `userSettingsManager`, so
 *
 *  * two users of the same app server no longer overwrite each other's choice,
 *  * the choice outlives a restart, and
 *  * there is exactly one writer ([update]) — the `models` action no longer keeps a
 *    private copy in sync.
 *
  * Every action reads the value **at invocation time** ([smartFor]/[fastFor]), so a
  * change takes effect on the next request with no re-binding. There is no in-memory
  * fallback pair and no static/start-up default: the per-user settings service is the
  * only source of a selection, so the only way to choose a model is to store one
  * (the `models` action, or the settings UI).
 *
 * The contract is deliberately the model **id**, exactly as on the command line,
 * so an id a provider knows but the local registry does not is still usable; the
 * available list is only an affordance.
 *
 * ### User scoping
 * Both *availability* (API keys) and now *selection* are per-user, resolved from the
 * [FsActionContext] of the request being served: on a session-backed app server that
 * is the authenticated caller (see [SessionFsRoots.userOf]), on a local mount it is
 * the fixed owner. `null` means "no request in scope" (start-up, CLI) and falls back
 * to the default user.
 */
object ModelSelection {

  private val listeners = CopyOnWriteArrayList<() -> Unit>()

  @Volatile
  private var userFn: (FsActionContext?) -> User = { defaultUser }



  /** Enumerating provider models costs a network round trip, so it is cached per user. */
  private val cache = ConcurrentHashMap<String, Map<String, ChatModel>>()

  private fun settingsManager() = ApplicationServices.fileApplicationServices().userSettingsManager

  /**
    * Publishes the request-scoped user resolver. Hosts (CLI, embedding servers) call
    * this once at start-up.
   *
    * Nothing about the *selection* is configured here: it is read from (and written to)
    * the user settings service on demand.
   */
  @Synchronized
  fun install(user: (FsActionContext?) -> User) {
    userFn = user
    cache.clear()
  }


  /** The user whose credentials/model list/selection apply to [ctx]; never throws. */
  fun userFor(ctx: FsActionContext?): User = try {
    userFn(ctx)
  } catch (e: Exception) {
    System.err.println("warning: could not resolve the request user: ${e.message}")
    defaultUser
  }

  /* ---------------------------------------------------------------- reading */

   fun smartFor(user: User): String? = stored(user, smart = true)

   fun fastFor(user: User): String? = stored(user, smart = false)

  fun smartFor(ctx: FsActionContext?): String? = smartFor(userFor(ctx))

  fun fastFor(ctx: FsActionContext?): String? = fastFor(userFor(ctx))







  private fun stored(user: User, smart: Boolean): String? = try {
    val settings = settingsManager().getUserSettings(user)
    (if (smart) settings.smartModel else settings.fastModel).normalize()
  } catch (e: Exception) {
    System.err.println("warning: could not read the stored model selection: ${e.message}")
    null
  }

  /* ---------------------------------------------------------------- writing */

  /** Listeners re-bind the actions; they never run on the caller's behalf. */
  fun onChange(listener: () -> Unit) {
    listeners.add(listener)
  }

  /**
   * `null`/blank means "leave unchanged", so a dialog that only touches one of the
   * two fields does not silently clear the other. Answers true when something
   * actually changed (listeners only fire then), in which case the new pair has
   * already been persisted for [user].
   */
  @Synchronized
  fun update(user: User, smart: String? = null, fast: String? = null): Boolean {
    val settings = settingsManager().getUserSettings(user)
     val currentSmart = settings.smartModel.normalize()
     val currentFast = settings.fastModel.normalize()
    val nextSmart = smart.normalize() ?: currentSmart
    val nextFast = fast.normalize() ?: currentFast
    if (nextSmart == currentSmart && nextFast == currentFast) return false
    settingsManager().updateUserSettings(
      user,
      settings.copy(
        smartModel = nextSmart ?: settings.smartModel,
        fastModel = nextFast ?: settings.fastModel,
      )
    )
    for (listener in listeners) {
      try {
        listener()
      } catch (e: Exception) {
        System.err.println("warning: model change listener failed: ${e.message}")
      }
    }
    return true
  }


  fun update(ctx: FsActionContext?, smart: String? = null, fast: String? = null): Boolean =
    update(userFor(ctx), smart, fast)

  /* ------------------------------------------------------------ availability */

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

  fun invalidate(user: User) {
    cache.remove(cacheKey(user))
  }

  /* -------------------------------------------------------------- reporting */

  fun summary(user: User): String = "smart=${smartFor(user) ?: "none"} fast=${fastFor(user) ?: "none"}"

  fun summary(): String = summary(userFor(null))

  fun describe(ctx: FsActionContext?, refresh: Boolean): Map<String, Any?> {
    val user = userFor(ctx)
    val ids = modelIds(ctx, refresh)
    return linkedMapOf(
      "smart" to smartFor(user),
      "fast" to fastFor(user),
      "available" to ids,
      "configured" to ids.isNotEmpty(),
    )
  }

  fun describe(refresh: Boolean = false): Map<String, Any?> = describe(null, refresh)

  private fun String?.normalize(): String? = this?.trim()?.ifBlank { null }

  /* Identity is opaque here; the string form is stable and unique enough for a cache key. */
  private fun cacheKey(user: User): String = user.toString()
}