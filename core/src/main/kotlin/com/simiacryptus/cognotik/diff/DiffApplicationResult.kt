package com.simiacryptus.cognotik.diff

import com.simiacryptus.cognotik.util.GrammarValidator

data class DiffApplicationResult(
  val newCode: String,
  val errors: List<GrammarValidator.ValidationError>,
  val isValid: Boolean = errors.isEmpty(),
  val validator: GrammarValidator
)