package com.simiacryptus.cognotik.diff

import com.simiacryptus.cognotik.diff.FileValidators.DIFF_PATTERN

interface PatchProcessor {
    val label: String
    val patchFormatPrompt: String
    fun generatePatch(oldCode: String, newCode: String): String
    fun applyPatch(source: String, patch: String): String


    /**
     * Gets the regex pattern that initiates a code block
     */
    fun getInitiatorPattern(): Regex
    fun apply(
        originalCode: String, response: String, filename: String? = null
    ): DiffApplicationResult {
        val matches = DIFF_PATTERN.findAll(response).distinct()
        var currentCode = originalCode

        val validator = FileValidators.getValidator(filename)
        val originalCodeErrors = validator.validateGrammar(originalCode)
        val newErrors = matches.flatMap { diffBlock ->
            val response: String = diffBlock.groupValues[1]
            try {
                if (response.length > FileValidators.MAX_DIFF_SIZE_CHARS) {
                    throw IllegalArgumentException("Diff size exceeds maximum limit")
                }
                val newCode = applyPatch(currentCode, response).replace("\r", "")
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
            PatchProcessor.log.error("Error applying diff: ${newErrors.joinToString("\n") { it.message }}")
        }
        return DiffApplicationResult(currentCode, newErrors, validator = validator)
    }

    companion object {
        val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(PatchProcessor::class.java)
    }
}
