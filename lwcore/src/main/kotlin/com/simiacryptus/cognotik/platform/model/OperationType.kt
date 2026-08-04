package com.simiacryptus.cognotik.platform.model

/**
 * Enumeration of operation types that can be authorized.
 *
 * These operation types represent different levels and kinds of access
 * that can be granted to users for various resources.
 */
enum class OperationType {
  /**
   * Permission to read or view resources.
   * This is typically the most basic level of access.
   */
  Read,

  /**
   * Permission to create, modify, or update resources.
   * This allows users to make changes to existing data.
   */
  Write,

  /**
   * Permission to make resources publicly accessible.
   * This typically allows resources to be accessed without authentication.
   */
  Public,

  /**
   * Permission to share resources with other users.
   * This allows users to grant access to resources they control.
   */
  Share,

  /**
   * Permission to execute or run resources.
   * This is typically used for executable content like scripts or applications.
   */
  Execute,

  /**
   * Permission to permanently remove resources.
   * This is a destructive operation that should be carefully controlled.
   */
  Delete,

  /**
   * Full administrative permissions.
   * This typically grants all other permissions and system-level access.
   */
  Admin,
}