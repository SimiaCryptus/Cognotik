package com.simiacryptus.cognotik.text.validate

interface GrammarValidator {
  fun validateGrammar(code: String): List<ValidationError>

  /**
   * Extract metadata about every publicly visible symbol declared in [code].
   *
   * Implementations that cannot introspect the source (e.g. the paren-matching
   * fallback) simply return an empty list.
   */
  fun extractPublicSymbols(code: String): List<SymbolInfo> = emptyList()
    /**
     * Extract every *grammatical* reference to a symbol in [code].
     *
     * No resolution is attempted: the result simply records "here an identifier chain is used,
     * and this is roughly how it is used". Only validators that actually understand the
     * language can answer this, so the default is an empty list; every ANTLR-backed validator
     * implements it by walking its parse tree (see [com.simiacryptus.cognotik.text.SymbolReferenceScanner]).
     *
     * @param symbols declarations of the same [code], used to attribute each reference to the
     *   declaration that encloses it. Defaults to [extractPublicSymbols].
     */
    fun extractSymbolReferences(
      code: String,
      symbols: List<SymbolInfo> = extractPublicSymbols(code),
    ): List<SymbolReference> = emptyList()


  data class ValidationError(
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val severity: Severity = Severity.ERROR
  )

  /**
   * A declaration discovered in the source.
   *
   * @param name simple (unqualified) name of the symbol
   * @param signature the declaration header, whitespace-collapsed (e.g. `public static void main(String[] args)`)
   * @param kind best-effort classification of the declaration
   * @param range character/line span of the whole declaration
   * @param children nested public symbols (members of a class/interface/module/...)
   */
  data class SymbolInfo(
    val name: String,
    val signature: String,
    val kind: SymbolKind,
    val range: TextRange,
    val children: List<SymbolInfo> = emptyList()
  ) {
    /** Depth-first flattening of this symbol and all of its descendants. */
    fun flatten(): List<SymbolInfo> = listOf(this) + children.flatMap { it.flatten() }

    /** Dotted path of this symbol relative to itself and its [children]. */
    fun qualifiedNames(prefix: String = ""): List<String> {
      val qualified = if (prefix.isEmpty()) name else "$prefix.$name"
      return listOf(qualified) + children.flatMap { it.qualifiedNames(qualified) }
    }
  }
    /**
     * A use (not a declaration) of some name, as found by the grammar - unresolved by design.
     *
     * @param name the single identifier being referenced (`bar` of `foo.bar`)
     * @param text the whole identifier chain as written, whitespace removed (`foo.bar`)
     * @param kind best-effort classification of how the name is used
     * @param range span of [name] itself (not of the whole chain)
     * @param qualifier the dotted prefix inside the chain, or `null` for the head of the chain
     * @param enclosingSymbol dotted name of the declaration containing this reference, if known
     */
    data class SymbolReference(
      val name: String,
      val text: String,
      val kind: ReferenceKind,
      val range: TextRange,
      val qualifier: String? = null,
      val enclosingSymbol: String? = null,
    ) {
      /** `foo.bar` for a qualified reference, otherwise just `bar`. */
      val qualifiedName: String get() = if (qualifier.isNullOrEmpty()) name else "$qualifier.$name"
    }


  /**
   * Character offsets are 0-based and [endOffset] is exclusive.
   * Lines are 1-based, columns are 0-based (matching ANTLR conventions).
   */
  data class TextRange(
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int
  ) {
    val length: Int get() = (endOffset - startOffset).coerceAtLeast(0)
    fun substringOf(code: String): String =
      code.substring(startOffset.coerceIn(0, code.length), endOffset.coerceIn(startOffset, code.length))
  }

  enum class SymbolKind {
    MODULE,
    CLASS,
    INTERFACE,
    TRAIT,
    ENUM,
    OBJECT,
    STRUCT,
    ANNOTATION,
    TYPE_ALIAS,
    FUNCTION,
    CONSTRUCTOR,
    PROPERTY,
    FIELD,
    VARIABLE,
    CONSTANT,
    RULE,
    OTHER,
  }
    /** How an identifier appears to be used at its reference site. */
    enum class ReferenceKind {
      /** Part of an `import` / `use` / `require` / `package` directive. */
      IMPORT,
      /** Annotation or decorator usage (`@Foo`). */
      ANNOTATION,
      /** Used in a type position (`: Foo`, `new Foo`, `extends Foo`, generics, ...). */
      TYPE,
      /** Immediately followed by an argument list. */
      CALL,
      /** Non-head element of a qualified chain (`foo.bar` -> `bar`). */
      MEMBER,
      /** Anything else (plain value/identifier usage). */
      IDENTIFIER,
    }


  enum class Severity {
    ERROR,
  }
}