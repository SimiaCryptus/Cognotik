package com.simiacryptus.skyenet.core.util

data class DiffApplicationResult(
  val newCode: String,
  val validationResults: Map<String, Boolean>,
  val error: String? = null,
  val isValid: Boolean = validationResults.all { it.value }
)


class SimpleDiffApplier {
  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(SimpleDiffApplier::class.java)
    
    private const val MAX_DIFF_SIZE_CHARS = 100000
    
    val DIFF_PATTERN = """(?s)(?<![^\n])```diff\n(.*?)\n```""".toRegex()
    
    val validatorProviders = mutableListOf<(String?) -> GrammarValidator?>(
      { filename ->
        when (filename?.split('.')?.lastOrNull()) {
          "kt" -> KotlinGrammarValidator()
          else -> null
        }
      },
      { _ -> ParenMatchingValidator() }
    )
    
    fun getValidator(filename: String?): GrammarValidator {
      return validatorProviders.firstNotNullOf { it(filename) }
    }
    
  }
  
  private fun validateDiffSize(diff: String): Boolean {
    return diff.length <= MAX_DIFF_SIZE_CHARS
  }
  
  fun apply(
    code: String, response: String, filename: String? = null
  ): DiffApplicationResult {
    val matches = DIFF_PATTERN.findAll(response).distinct()
    var currentCode = code
    // Use language validator if available, otherwise use fallback
    val validator = Companion.getValidator(filename)
    val initialIsValid = validator.validateGrammar(code)
    
    matches.mapNotNull { diffBlock ->
      val diffVal: String = diffBlock.groupValues[1]
      try {
        if (!validateDiffSize(diffVal)) {
          throw IllegalArgumentException("Diff size exceeds maximum limit")
        }
        val newCode = IterativePatchUtil.applyPatch(currentCode, diffVal).replace("\r", "")
        // Validate using appropriate validator
        val validationErrors = validator.validateGrammar(newCode)
        if (validationErrors.isNotEmpty()) {
          val fileExtension = filename?.split('.')?.lastOrNull()
          val errorMessages = validationErrors.joinToString("\n") { error ->
            "${error.severity}: ${error.message}" + 
            (error.line?.let { " at line $it" } ?: "") +
            (error.column?.let { " column $it" } ?: "")
          }
          throw IllegalStateException(
            if (fileExtension != null) "Invalid syntax after applying diff for file type $fileExtension:\n$errorMessages"
            else "Validation errors after applying diff:\n$errorMessages"
          )
        }
        currentCode = newCode
        return@mapNotNull null
      } catch (e: Throwable) {
        return@mapNotNull e
      }
    }.toList().apply {
      if (isNotEmpty()) {
        val error = joinToString("\n") { it.message ?: "Unknown error" }
        log.error("Error applying diff: $error")
        return DiffApplicationResult(
          currentCode,
          mapOf("basic" to validator.validateGrammar(currentCode).isEmpty()),
          error,
          false
        )
      }
    }
    
    return DiffApplicationResult(
      currentCode,
      mapOf("basic" to validator.validateGrammar(currentCode).isEmpty()),
      null,
      true
    )
  }
  
}