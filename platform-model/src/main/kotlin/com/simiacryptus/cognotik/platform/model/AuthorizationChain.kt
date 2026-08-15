package com.simiacryptus.cognotik.platform.model

/**
 * Minimal, framework-free contract for a pluggable authorization chain link.
 *
 * Declared here so that [PluginEvents.AuthChainRegistration] payloads can be typed
 * rather than passed as `Any` (REVIEW.md §3.8).
 */
interface AuthorizationChain {

  /** Unique name of this chain, used for registration/unregistration. */
  val name: String

  /**
   * @return true to allow, false to deny, or null to defer to the next link in the chain
   */
  fun isAuthorized(
    resource: ResourceRef?,
    principal: Principal,
    operationType: OperationType,
  ): Boolean?
}