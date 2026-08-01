package com.simiacryptus.cognotik.text.patch

import com.simiacryptus.cognotik.text.validate.GrammarValidator

data class DiffApplicationResult(
  val newCode: String,
  val errors: List<GrammarValidator.ValidationError>,
  val isValid: Boolean = errors.isEmpty(),
  val validator: GrammarValidator
)