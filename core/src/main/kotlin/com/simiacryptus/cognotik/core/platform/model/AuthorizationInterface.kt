package com.simiacryptus.cognotik.core.platform.model

interface AuthorizationInterface {
  enum class OperationType {
    Read,
    Write,
    Public,
    Share,
    Execute,
    Delete,
    Admin,
    GlobalKey,
  }

  fun isAuthorized(
    applicationClass: Class<*>?,
    user: User?,
    operationType: OperationType,
  ): Boolean
}