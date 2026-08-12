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

  /**
   * The validator dedicated to this file's language, or `null` when no language-specific
   * validator matches (i.e. only the generic [ParenMatchingValidator] fallback would apply).
   */
  fun getLanguageValidator(filename: String?): GrammarValidator? =
    validatorProviders.firstNotNullOfOrNull { it(filename) }?.takeUnless { it is ParenMatchingValidator }

  /** True when a language-specific validator exists for [filename]. */
  fun isSupported(filename: String?): Boolean = getLanguageValidator(filename) != null


  /**
   * Convenience accessor: extract the public symbols of [code] using the validator
   * selected for [filename]. Returns an empty list when the language is unsupported.
   */
  fun extractPublicSymbols(filename: String?, code: String): List<GrammarValidator.SymbolInfo> = try {
    getValidator(filename).extractPublicSymbols(code)
  } catch (e: Throwable) {
    log.warn("Error extracting symbols from $filename", e)
    emptyList()
  }

  /**
   * Convenience accessor: extract the (unresolved) symbol references of [code] using the
   * validator selected for [filename]. Returns an empty list on any failure.
   */
  fun extractSymbolReferences(filename: String?, code: String): List<GrammarValidator.SymbolReference> = try {
    getValidator(filename).extractSymbolReferences(code)
  } catch (e: Throwable) {
    log.warn("Error extracting references from $filename", e)
    emptyList()
  }


}