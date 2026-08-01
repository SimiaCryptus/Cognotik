package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.model.User
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The smart/fast model pair used by every agentic surface of the file server
 * (DocOps, AutoFix and the patch chat).
 *
 * Previously this was start-up-only state baked into each action's configuration,
 * so a mount launched without `--smart-model` was permanently useless. It is now
 * *runtime* state: the web UI picks it through `GET|POST {mount}/.fsapi/v1/models`
 * (see [ModelSelectionActions]) and every registered toolchain is re-bound.
 *
 * The contract is deliberately the model **id**, exactly as on the command line,
 * so an id a provider knows but the local registry does not is still usable; the
 * available list is only an affordance.
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
  private var userFn: () -> User = { CliSupport.defaultUser() }

  /** Enumerating provider models costs a network round trip, so it is cached. */
  @Volatile
  private var cache: Map<String, ChatModel> = emptyMap()

  @Synchronized
  fun install(user: () -> User, smart: String? = null, fast: String? = null) {
    userFn = user
    this.smart = smart?.trim()?.ifBlank { null }
    this.fast = fast?.trim()?.ifBlank { null }
  }

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

  fun availableModels(refresh: Boolean = false): Map<String, ChatModel> {
    if (!refresh) {
      if (cache.isNotEmpty()) return cache
      /* Reuse whatever the server already resolved at start-up. */
      FileServerCli.available.takeIf { it.isNotEmpty() }?.let {
        cache = it
        return it
      }
    }
    val fresh = try {
      CliSupport.availableModels(userFn())
    } catch (e: Exception) {
      System.err.println("warning: could not list models: ${e.message}")
      emptyMap()
    }
    if (fresh.isNotEmpty()) {
      cache = fresh
      FileServerCli.available = fresh
    }
    return if (fresh.isNotEmpty()) fresh else cache
  }

  fun modelIds(refresh: Boolean = false): List<String> =
    availableModels(refresh).values.map { it.modelId }.distinct().sorted()

  fun summary(): String = "smart=${smart ?: "none"} fast=${fast ?: "none"}"

  fun describe(refresh: Boolean = false): Map<String, Any?> {
    val ids = modelIds(refresh)
    return linkedMapOf(
      "smart" to smart,
      "fast" to fast,
      "available" to ids,
      "configured" to ids.isNotEmpty(),
    )
  }
}