package com.simiacryptus.cognotik.text.validate

import org.slf4j.LoggerFactory.getLogger

object FileValidators {

  val log = getLogger(FileValidators::class.java)

  const val MAX_DIFF_SIZE_CHARS = 100000

  val DIFF_PATTERN = """(?s)(?<![^\n])```diff\n(.*?)\n```""".toRegex()

  val validatorProviders = mutableListOf<(String?) -> GrammarValidator?>({ filename ->
    when (filename?.split('.')?.lastOrNull()) {
      "kt" -> KotlinGrammarValidator()
      "ktm" -> KotlinGrammarValidator()
      "rs" -> RustGrammarValidator()
      "kts" -> KotlinGrammarValidator(isScript = true)
      "py", "pyi", "pyw" -> PythonGrammarValidator()
      "java" -> JavaGrammarValidator()
      "ts", "tsx", "mts", "cts" -> TypeScriptGrammarValidator()
      "js", "mjs", "cjs", "jsx" -> JavaScriptGrammarValidator()
      "json", "json5" -> Json5GrammarValidator()
      "css" -> CssGrammarValidator()
      "html", "htm", "xhtml" -> HtmlGrammarValidator()
      else -> null
    }
  }, { _ -> ParenMatchingValidator() })

  fun getValidator(filename: String?): GrammarValidator {
    return validatorProviders.firstNotNullOf { it(filename) }
  }

}