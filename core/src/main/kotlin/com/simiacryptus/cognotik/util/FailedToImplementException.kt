package com.simiacryptus.cognotik.util

class FailedToImplementException(
  cause: Throwable? = null,
  message: String = "Failed to implement",
  val language: String? = null,
  val code: String? = null,
  val prefix: String? = null,
) : RuntimeException(message, cause)