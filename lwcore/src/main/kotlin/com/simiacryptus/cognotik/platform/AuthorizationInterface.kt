package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.User

/**
 * Interface for managing authorization and access control within the platform.
 *
 * This interface provides a contract for implementing various authorization strategies
 * to control user access to different resources and operations within applications.
 *
 * Implementations of this interface should handle the logic for determining whether
 * a user has the necessary permissions to perform specific operations on resources
 * associated with particular application classes.
 *

 * @see AuthorizationManager for the default file-based implementation
 */
interface AuthorizationInterface {

  /**
   * Determines whether a user is authorized to perform a specific operation.
   *
   * This method checks if the given user has the necessary permissions to perform
   * the specified operation type. The authorization can be scoped to a specific
   * application class, allowing for fine-grained access control at the application level.
   *
   * The implementation may use various strategies for authorization, such as:
   * - File-based permission lists
   * - Database-backed access control lists (ACLs)
   * - Role-based access control (RBAC)
   * - Attribute-based access control (ABAC)
   *
   * @param applicationClass The class of the application for which authorization is being checked.
   *                        Can be null for global authorization checks that are not specific
   *                        to any particular application.
   * @param user The user for whom authorization is being checked. Can be null to represent
   *             anonymous or unauthenticated access.
   * @param operationType The type of operation for which authorization is being requested.
   *                      This determines what kind of access is being checked.
   *
   * @return true if the user is authorized to perform the operation, false otherwise.
   *         In case of any errors during authorization checking, implementations should
   *         typically return false to fail securely.
   *
   * @throws SecurityException May be thrown by implementations if there are critical
   *                          security violations or configuration errors.
   */

  fun isAuthorized(
    applicationClass: Class<*>?,
    user: User?,
    operationType: OperationType,
  ): Boolean
}