package com.simiacryptus.cognotik.text.validate

import com.simiacryptus.cognotik.text.SymbolReferenceScanner
import com.simiacryptus.cognotik.text.validate.GrammarValidator.*
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.slf4j.LoggerFactory.getLogger

/**
 * Common plumbing for grammar validators backed by an ANTLR lexer/parser pair.
 *
 * Sub classes only need to supply the lexer/parser factories and the start rule
 * to invoke; all error collection (including line/column information) is handled
 * here, as is best-effort public symbol extraction.
 */
abstract class AntlrGrammarValidator<L : Lexer, P : Parser>(
  private val languageName: String
) : GrammarValidator {
  companion object {
    private val log = getLogger(AntlrGrammarValidator::class.java)

    private const val MAX_HEADER_TOKENS = 256
    private const val MAX_MODIFIER_LOOKBEHIND = 8
    private const val MAX_WRAPPER_DEPTH = 3

    private val WHITESPACE = """\s+""".toRegex()

    /** Tokens that terminate a declaration header (i.e. start the body). */
    private val BODY_START_TOKENS = setOf("{", ";", "=>")

    /** Token symbolic names that terminate a declaration header (indentation based grammars). */
    private val LINE_BREAK_TOKENS = setOf("NEWLINE", "INDENT", "DEDENT")

    /** Tokens after which a value declaration's name can no longer appear. */
    private val NAME_STOP_TOKENS = setOf("=", ":", "->")

    private val NON_PUBLIC_MODIFIERS = setOf("private", "protected", "internal", "fileprivate")

    private val TYPE_KEYWORDS = setOf(
      "class", "interface", "enum", "record", "struct", "trait", "object",
      "union", "module", "mod", "namespace", "annotation", "type", "typealias"
    )

    /** Kinds whose bodies may declare further public symbols. */
    private val CONTAINER_KINDS = setOf(
      SymbolKind.MODULE,
      SymbolKind.CLASS,
      SymbolKind.INTERFACE,
      SymbolKind.TRAIT,
      SymbolKind.ENUM,
      SymbolKind.OBJECT,
      SymbolKind.STRUCT,
      SymbolKind.ANNOTATION,
    )

    /**
     * Maps (lower-cased) ANTLR rule names to symbol kinds. Keys from all supported
     * grammars are merged here; unknown keys are simply never matched.
     */
    val DEFAULT_SYMBOL_RULES: Map<String, SymbolKind> = mapOf(

      "classdeclaration" to SymbolKind.CLASS,
      "normalclassdeclaration" to SymbolKind.CLASS,
      "classorinterfacedeclaration" to SymbolKind.CLASS,
      "recorddeclaration" to SymbolKind.CLASS,
      "classdef" to SymbolKind.CLASS,
      "interfacedeclaration" to SymbolKind.INTERFACE,
      "normalinterfacedeclaration" to SymbolKind.INTERFACE,
      "annotationinterfacedeclaration" to SymbolKind.ANNOTATION,
      "annotationtypedeclaration" to SymbolKind.ANNOTATION,
      "enumdeclaration" to SymbolKind.ENUM,
      "enumeration" to SymbolKind.ENUM,
      "objectdeclaration" to SymbolKind.OBJECT,
      "struct_" to SymbolKind.STRUCT,
      "structstruct" to SymbolKind.STRUCT,
      "tuplestruct" to SymbolKind.STRUCT,
      "union_" to SymbolKind.STRUCT,
      "trait_" to SymbolKind.TRAIT,
      "traitdeclaration" to SymbolKind.TRAIT,
      "module" to SymbolKind.MODULE,
      "namespacedeclaration" to SymbolKind.MODULE,
      "typealias" to SymbolKind.TYPE_ALIAS,
      "typealiasdeclaration" to SymbolKind.TYPE_ALIAS,

      "functiondeclaration" to SymbolKind.FUNCTION,
      "functiondefinition" to SymbolKind.FUNCTION,
      "function_" to SymbolKind.FUNCTION,
      "methoddeclaration" to SymbolKind.FUNCTION,
      "interfacemethoddeclaration" to SymbolKind.FUNCTION,
      "methoddefinition" to SymbolKind.FUNCTION,
      "funcdef" to SymbolKind.FUNCTION,
      "async_funcdef" to SymbolKind.FUNCTION,
      "constructordeclaration" to SymbolKind.CONSTRUCTOR,
      "secondaryconstructor" to SymbolKind.CONSTRUCTOR,

      "propertydeclaration" to SymbolKind.PROPERTY,
      "propertymemberdeclaration" to SymbolKind.PROPERTY,
      "fielddeclaration" to SymbolKind.FIELD,
      "staticitem" to SymbolKind.FIELD,
      "constantdeclaration" to SymbolKind.CONSTANT,
      "constantitem" to SymbolKind.CONSTANT,

      "ruleset" to SymbolKind.RULE,
      "knownruleset" to SymbolKind.RULE,
    )
  }

  protected abstract fun createLexer(input: CharStream): L
  protected abstract fun createParser(tokens: TokenStream): P

  /** Invoke the start rule of the grammar, returning the resulting parse tree. */
  protected abstract fun parse(parser: P): ParserRuleContext?

  /** Rule-name (lower case) to symbol kind mapping; override to tune per language. */
  protected open val symbolRules: Map<String, SymbolKind> get() = DEFAULT_SYMBOL_RULES

  override fun validateGrammar(code: String): List<ValidationError> {
    return try {
      val errorCollector = ErrorCollector()
      val lexer = createLexer(CharStreams.fromString(code)).apply {
        removeErrorListeners()
        addErrorListener(errorCollector)
      }
      val parser = createParser(CommonTokenStream(lexer)).apply {
        removeErrorListeners()
        addErrorListener(errorCollector)
      }
      parse(parser)
      errorCollector.errors.toList()
    } catch (e: Throwable) {
      log.error("Error validating $languageName grammar", e)
      listOf(
        ValidationError(
          message = "Error validating $languageName grammar: ${e.message}",
          severity = Severity.ERROR
        )
      )
    }
  }

  /**
   * Best-effort extraction of the public API declared in [code]. Parse errors are
   * tolerated (ANTLR's default error recovery is used), so partial sources still
   * yield whatever could be recognised.
   */
  override fun extractPublicSymbols(code: String): List<SymbolInfo> {
    return try {
      val (parser, tree) = parseQuietly(code)
      if (tree == null) return emptyList()
      collectSymbols(tree, Scan(parser.ruleNames, parser.vocabulary, code))
    } catch (e: Throwable) {
      log.warn("Error extracting $languageName symbols: ${e.message}", e)
      emptyList()
    }
  }

  /**
   * Grammar-driven reference extraction: the source is parsed with the real grammar and the
   * resulting tree is walked by [com.simiacryptus.cognotik.text.SymbolReferenceScanner], so literals, comments and keywords
   * are classified by the lexer rather than guessed at.
   */
  override fun extractSymbolReferences(code: String, symbols: List<SymbolInfo>): List<SymbolReference> {
    return try {
      val (parser, tree) = parseQuietly(code)
      if (tree == null) return emptyList()
      SymbolReferenceScanner.scan(tree, parser.ruleNames, parser.vocabulary, symbols, referenceOptions)
    } catch (e: Throwable) {
      log.warn("Error extracting $languageName references: ${e.message}", e)
      emptyList()
    }
  }

  /** Reference-scanner tuning; override for language specific token/rule naming. */
  protected open val referenceOptions: SymbolReferenceScanner.Options
    get() = SymbolReferenceScanner.Options()

  /**
   * Parse [code] with all error listeners removed, relying on ANTLR's default error recovery so
   * that partial/invalid sources still yield a (possibly incomplete) tree.
   */
  private fun parseQuietly(code: String): Pair<P, ParserRuleContext?> {
    val lexer = createLexer(CharStreams.fromString(code)).apply { removeErrorListeners() }
    val parser = createParser(CommonTokenStream(lexer)).apply { removeErrorListeners() }
    return parser to parse(parser)
  }


  private class Scan(
    val ruleNames: Array<String>,
    val vocabulary: Vocabulary,
    val code: String
  )

  private fun collectSymbols(node: ParseTree, scan: Scan): List<SymbolInfo> {
    val result = mutableListOf<SymbolInfo>()
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) as? ParserRuleContext ?: continue
      val kind = kindOf(child, scan)
      if (kind == null || isPassThroughWrapper(child, scan)) {
        result += collectSymbols(child, scan)
        continue
      }
      val prefix = modifierTokens(child)
      val header = headerTokens(child, scan.vocabulary)
      val tokens = prefix + header
      val name = symbolName(child, kind, tokens, scan.vocabulary, scan.code)
      if (name.isNullOrBlank()) {
        result += collectSymbols(child, scan)
        continue
      }
      if (!isPublicDeclaration(kind, name, tokens)) continue
      result += SymbolInfo(
        name = name,
        signature = signatureOf(prefix, header, child, scan.code),
        kind = kind,
        range = rangeOf(prefix, child),
        children = if (kind in CONTAINER_KINDS) collectSymbols(child, scan) else emptyList()
      )
    }
    return result
  }

  /** `classDeclaration : normalClassDeclaration | ...` style single-child wrappers. */
  private fun isPassThroughWrapper(ctx: ParserRuleContext, scan: Scan): Boolean {
    if (ctx.childCount != 1) return false
    val only = ctx.getChild(0) as? ParserRuleContext ?: return false
    return kindOf(only, scan) != null
  }

  private fun kindOf(ctx: ParserRuleContext, scan: Scan): SymbolKind? =
    scan.ruleNames.getOrNull(ctx.ruleIndex)?.lowercase()?.let { symbolRules[it] }

  /**
   * Tokens of the declaration up to (but excluding) the start of its body.
   */
  private fun headerTokens(ctx: ParserRuleContext, vocabulary: Vocabulary): List<Token> {
    val out = mutableListOf<Token>()
    visitTokens(ctx) { token ->
      val symbolic = (vocabulary.getSymbolicName(token.type) ?: "").uppercase()
      when {
        token.type == Token.EOF -> false
        symbolic in LINE_BREAK_TOKENS -> false
        token.text in BODY_START_TOKENS -> false
        else -> {
          out.add(token)
          out.size < MAX_HEADER_TOKENS
        }
      }
    }
    return out
  }

  /**
   * Modifier/visibility tokens that live in a wrapping rule rather than in the
   * declaration itself (e.g. Rust's `visItem : visibility? function_`).
   */
  private fun modifierTokens(ctx: ParserRuleContext): List<Token> {
    val out = mutableListOf<Token>()
    var child: ParserRuleContext = ctx
    var parent = child.parent as? ParserRuleContext
    var depth = 0
    while (parent != null && depth++ < MAX_WRAPPER_DEPTH) {
      val from = parent.start?.tokenIndex ?: break
      val until = child.start?.tokenIndex ?: break
      val gap = until - from
      if (gap !in 1..MAX_MODIFIER_LOOKBEHIND) break
      visitTokens(parent) { token ->
        if (token.tokenIndex in from until until) out.add(token)
        token.tokenIndex < until
      }
      child = parent
      parent = parent.parent as? ParserRuleContext
    }
    return out.distinctBy { it.tokenIndex }.sortedBy { it.tokenIndex }
  }

  private fun visitTokens(node: ParseTree, visitor: (Token) -> Boolean): Boolean {
    if (node is TerminalNode) return visitor(node.symbol)
    for (i in 0 until node.childCount) if (!visitTokens(node.getChild(i), visitor)) return false
    return true
  }

  private fun signatureOf(
    prefix: List<Token>,
    header: List<Token>,
    ctx: ParserRuleContext,
    code: String
  ): String {
    val start = (prefix.firstOrNull() ?: ctx.start)?.startIndex ?: 0
    val end = ((header.lastOrNull() ?: ctx.start)?.stopIndex ?: start) + 1
    return code.substring(start.coerceIn(0, code.length), end.coerceIn(start, code.length))
      .replace(WHITESPACE, " ").trim()
  }

  private fun rangeOf(prefix: List<Token>, ctx: ParserRuleContext): TextRange {
    val first = prefix.firstOrNull() ?: ctx.start
    val last = ctx.stop ?: ctx.start
    val start = first?.startIndex ?: 0
    return TextRange(
      startOffset = start,
      endOffset = (last?.stopIndex ?: start) + 1,
      startLine = first?.line ?: 0,
      startColumn = first?.charPositionInLine ?: 0,
      endLine = last?.line ?: 0,
      endColumn = (last?.charPositionInLine ?: 0) + (last?.text?.length ?: 0)
    )
  }

  /** Heuristic name resolution; override for language specific edge cases. */
  protected open fun symbolName(
    ctx: ParserRuleContext,
    kind: SymbolKind,
    tokens: List<Token>,
    vocabulary: Vocabulary,
    code: String
  ): String? = when (kind) {
    SymbolKind.RULE -> tokens.textIn(code)
    SymbolKind.FUNCTION, SymbolKind.CONSTRUCTOR ->
      functionName(tokens, vocabulary) ?: if (kind == SymbolKind.CONSTRUCTOR) "constructor" else null

    SymbolKind.PROPERTY, SymbolKind.FIELD, SymbolKind.VARIABLE, SymbolKind.CONSTANT -> {
      val paren = tokens.indexOfFirst { it.text == "(" }
      val stop = tokens.indexOfFirst { it.text in NAME_STOP_TOKENS }
      if (paren > 0 && (stop < 0 || paren < stop)) {
        functionName(tokens, vocabulary) ?: valueName(tokens, vocabulary)
      } else {
        valueName(tokens, vocabulary)
      }
    }

    else -> typeName(tokens, vocabulary)
  }

  /** The identifier immediately preceding the parameter list, skipping generics. */
  private fun functionName(tokens: List<Token>, vocabulary: Vocabulary): String? {
    val open = tokens.indexOfFirst { it.text == "(" }
    if (open <= 0) return null
    var i = open - 1
    if (tokens[i].text == ">" || tokens[i].text == ">>") {

      var depth = 0
      while (i >= 0) {
        when (tokens[i].text) {
          ">" -> depth++
          ">>" -> depth += 2
          "<" -> depth--
          "<<" -> depth -= 2
        }
        i--
        if (depth <= 0) break
      }
    }
    while (i >= 0 && !isIdentifier(tokens[i], vocabulary)) i--
    return if (i >= 0) tokens[i].text else null
  }

  /** The last identifier before an initializer/type annotation (`= 1`, `: Int`). */
  private fun valueName(tokens: List<Token>, vocabulary: Vocabulary): String? {
    val cut = tokens.indexOfFirst { it.text in NAME_STOP_TOKENS }
    val head = if (cut > 0) tokens.subList(0, cut) else tokens
    return head.lastOrNull { isIdentifier(it, vocabulary) }?.text
      ?: tokens.firstOrNull { isIdentifier(it, vocabulary) }?.text
  }

  /** The identifier following the declaration keyword (`class Foo`, `struct Foo`). */
  private fun typeName(tokens: List<Token>, vocabulary: Vocabulary): String? {
    val keyword = tokens.indexOfFirst { (it.text ?: "").lowercase() in TYPE_KEYWORDS }
    if (keyword >= 0) {
      for (i in keyword + 1 until tokens.size) {
        if (isIdentifier(tokens[i], vocabulary)) return tokens[i].text
      }
    }
    return tokens.firstOrNull { isIdentifier(it, vocabulary) }?.text
  }

  private fun isIdentifier(token: Token, vocabulary: Vocabulary): Boolean {
    val symbolic = (vocabulary.getSymbolicName(token.type) ?: return false).uppercase()
    return symbolic.contains("IDENT") || symbolic == "NAME"
  }

  private fun List<Token>.textIn(code: String): String? {
    if (isEmpty()) return null
    val start = first().startIndex.coerceIn(0, code.length)
    val end = (last().stopIndex + 1).coerceIn(start, code.length)
    return code.substring(start, end).replace(WHITESPACE, " ").trim().takeIf { it.isNotEmpty() }
  }

  /**
   * Visibility heuristic: a declaration is public unless it carries an explicit
   * non-public modifier. Languages with different defaults (Rust, Python) override this.
   */
  protected open fun isPublicDeclaration(
    kind: SymbolKind,
    name: String,
    tokens: List<Token>
  ): Boolean = tokens.none { (it.text ?: "").lowercase() in NON_PUBLIC_MODIFIERS }

  protected class ErrorCollector : BaseErrorListener() {
    val errors = mutableListOf<ValidationError>()
    override fun syntaxError(
      recognizer: Recognizer<*, *>?,
      offendingSymbol: Any?,
      line: Int,
      charPositionInLine: Int,
      msg: String?,
      e: RecognitionException?
    ) {
      errors.add(
        ValidationError(
          message = msg ?: "Syntax error",
          line = line,
          column = charPositionInLine,
          severity = Severity.ERROR
        )
      )
    }
  }
}