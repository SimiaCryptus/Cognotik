package com.simiacryptus.cognotik.diff

data class PatchResult(
  val newCode: String,
  val isValid: Boolean,
  val error: String? = null,
)