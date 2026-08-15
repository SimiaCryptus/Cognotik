package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef

/**
 * Interface for managing authorization and access control within the platform.
 *
 * Authorization is expressed over a ([ResourceRef], [Principal], [OperationType])
 * triple, which — unlike the original `Class<*>`-keyed form — can express
 * instance-scoped permissions such as "may Delete *session S*" and can be
 * serialized into configuration (REVIEW.md §3.5).
 *
 * Error strategy: implementations MUST fail securely by returning `false`.
 * Exceptions are reserved for programmer/configuration errors.
 */
interface AuthorizationInterface {

  /**
   * Determines whether [principal] may perform [operationType] on [resource].
   *
   * @param resource the resource being accessed, or null for a global check
   * @param principal the acting principal ([Principal.Anonymous] for unauthenticated access)
   * @param operationType the requested operation
   * @return true if authorized; false otherwise (implementations must fail securely)
   */
  @Suppress("DEPRECATION")
  fun isAuthorized(
    resource: ResourceRef?,
    principal: Principal,
    operationType: OperationType,
  ): Boolean = isAuthorized(ResourceRef.of(resource?.applicationClass), Principal.of(principal.user), operationType)

  /**
   * Bulk query: which operations may [principal] perform on [resource]?
   *
   * Lets a UI render permission-dependent controls with one call instead of one
   * call per [OperationType].
   */
  fun authorizedOperations(
    resource: ResourceRef?,
    principal: Principal,
  ): Set<OperationType> = OperationType.values().filter { isAuthorized(resource, principal, it) }.toSet()
}