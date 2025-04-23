package com.simiacryptus.cognotik.core.util

import com.simiacryptus.cognotik.core.util.GrammarValidator.ValidationError

data class DiffApplicationResult(
  val newCode: String,
  val errors: List<ValidationError>,
  val isValid: Boolean = errors.isEmpty(),
  val validator: GrammarValidator
)


class SimpleDiffApplier {
  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(SimpleDiffApplier::class.java)
    
    private const val MAX_DIFF_SIZE_CHARS = 100000
    
    val DIFF_PATTERN = """(?s)(?<![^\n])```diff\n(.*?)\n```""".toRegex()
    
    val validatorProviders = mutableListOf<(String?) -> GrammarValidator?>({ filename ->
      when (filename?.split('.')?.lastOrNull()) {
        "kt" -> KotlinGrammarValidator()
        else -> null
      }
    }, { _ -> ParenMatchingValidator() })
    
    fun getValidator(filename: String?): GrammarValidator {
      return validatorProviders.firstNotNullOf { it(filename) }
    }
    
  }
  
  private fun validateDiffSize(diff: String): Boolean {
    return diff.length <= MAX_DIFF_SIZE_CHARS
  }
  
  fun apply(
    originalCode: String, response: String, filename: String? = null
  ): DiffApplicationResult {
    val matches = DIFF_PATTERN.findAll(response).distinct()
    var currentCode = originalCode
    // Use language validator if available, otherwise use fallback
    val validator = Companion.getValidator(filename)
    val originalCodeErrors = validator.validateGrammar(originalCode)
    val newErrors = matches.flatMap { diffBlock ->
      val diffVal: String = diffBlock.groupValues[1]
      try {
        if (!validateDiffSize(diffVal)) {
          throw IllegalArgumentException("Diff size exceeds maximum limit")
        }
        val newCode = IterativePatchUtil.applyPatch(currentCode, diffVal).replace("\r", "")
        // Validate using appropriate validator
        val validationErrors = validator.validateGrammar(newCode)
        currentCode = newCode
        return@flatMap validationErrors
      } catch (e: Throwable) {
        return@flatMap emptyList()
      }
    }.toList().filter {
      originalCodeErrors.none { originalError ->
        it.message == originalError.message
      }
    }
    if (newErrors.isNotEmpty()) {
      log.error("Error applying diff: ${newErrors.joinToString("\n") { it.message }}")
    }
    return DiffApplicationResult(currentCode, newErrors, validator=validator)
  }
}