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
     * Data class for authorization chain registration events.
     *
     * @param name unique name for the authorization chain
     * @param chain the authorization chain instance (expected to be [com.simiacryptus.cognotik.auth.AuthorizationChain])
     */
    data class AuthChainRegistration(
        val name: String,
        val chain: Any
    )
}