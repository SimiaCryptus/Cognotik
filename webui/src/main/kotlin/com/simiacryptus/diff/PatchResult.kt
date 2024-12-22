package com.simiacryptus.diff

data class PatchResult(
  val newCode: String,
  val isValid: Boolean,
  val error: String? = null,
)