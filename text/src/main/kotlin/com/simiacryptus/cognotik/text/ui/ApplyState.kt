package com.simiacryptus.cognotik.text.ui

import java.time.Instant

sealed class ApplyState {
  object Pending : ApplyState()
  data class Applied(
    val originalCode: String,
    val newCode: String,
    val timestamp: Instant
  ) : ApplyState()

  data class Reverted(val code: String) : ApplyState()
  data class Failed(val error: Throwable) : ApplyState()
}