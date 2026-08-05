package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef
import com.simiacryptus.cognotik.platform.model.User

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
   * Determines whether a user is authorized to perform a specific operation.
   *
   * @param applicationClass The class of the application for which authorization is being checked,
   *                         or null for global checks.
   * @param user The user for whom authorization is being checked, or null for anonymous access.
   * @param operationType The type of operation for which authorization is being requested.
   * @return true if the user is authorized to perform the operation, false otherwise.
   * @deprecated Keys policy on JVM type identity and cannot express per-resource
   *             permissions; use the [ResourceRef]/[Principal] overload.
   */
  @Deprecated(
    "Keys policy on JVM type identity and cannot express instance-scoped permissions.",
    ReplaceWith("isAuthorized(ResourceRef.of(applicationClass), Principal.of(user), operationType)")
  )
  fun isAuthorized(
    applicationClass: Class<*>?,
    user: User?,
    operationType: OperationType,
  ): Boolean = isAuthorized(ResourceRef.of(applicationClass), Principal.of(user), operationType)

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
  ): Boolean = isAuthorized(resource?.applicationClass, principal.user, operationType)

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