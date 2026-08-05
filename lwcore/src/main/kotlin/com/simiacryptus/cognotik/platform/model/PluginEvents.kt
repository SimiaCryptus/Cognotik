package com.simiacryptus.cognotik.platform.model

/**
 * Well-known event topics for the PluginManager pub/sub event router.
 * Plugins can publish/subscribe to these topics without depending on specific servlet classes.
 */
object PluginEvents {
  /**
   * Topic for registering an authorization chain.
   * Payload: [AuthChainRegistration]
   */
  const val REGISTER_AUTH_CHAIN = "plugin.authChain.register"

  /**
   * Topic for unregistering an authorization chain.
   * Payload: [String] — the chain name to remove
   */
  const val UNREGISTER_AUTH_CHAIN = "plugin.authChain.unregister"

  /**
   * Topic used by the generic change-notification mechanism
   * (`EventBus.onChange` / `EventBus.triggerChangeNotification`).
   * Payload: none (null).
   */
  const val CHANGE_NOTIFICATION = "plugin.change"

  /** Typed key for [REGISTER_AUTH_CHAIN]. */
  @JvmField
  val REGISTER_AUTH_CHAIN_TOPIC: Topic<AuthChainRegistration> =
    Topic(REGISTER_AUTH_CHAIN, AuthChainRegistration::class.java)

  /** Typed key for [UNREGISTER_AUTH_CHAIN]. */
  @JvmField
  val UNREGISTER_AUTH_CHAIN_TOPIC: Topic<String> =
    Topic(UNREGISTER_AUTH_CHAIN, String::class.java)

  /**
   * Data class for authorization chain registration events.
   *
   * @param name unique name for the authorization chain
   * @param chain the authorization chain instance; prefer passing an
   *              [AuthorizationChain] so subscribers do not have to blind-cast
   */
  data class AuthChainRegistration(
    val name: String,
    val chain: Any
  ) {
    /** [chain] narrowed to the typed contract, or null if the plugin supplied something else. */
    val typedChain: AuthorizationChain? get() = chain as? AuthorizationChain
  }
}