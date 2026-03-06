package com.simiacryptus.cognotik.diff

import com.simiacryptus.cognotik.util.GrammarValidator
import com.simiacryptus.cognotik.util.KotlinGrammarValidator
import com.simiacryptus.cognotik.util.ParenMatchingValidator

object FileValidators {

    val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(FileValidators::class.java)

    const val MAX_DIFF_SIZE_CHARS = 100000

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

