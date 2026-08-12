package com.simiacryptus.cognotik.text

import com.simiacryptus.cognotik.text.validate.GrammarValidator.*
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Grammar-driven, *resolution-free* reference scanner.
 *
 * Unlike a textual scanner this operates exclusively on an ANTLR parse tree:
 *
 *  * identifiers are identifier **tokens** produced by the language's lexer, so comments,
 *    string/char/template literals, numbers, keywords and operators can never be mistaken
 *    for a name - no masking, no escaping rules, no per-language string fences;
 *  * qualified names (`Foo.bar`, `a::b::c`, `x?.y`) are rebuilt from adjacent terminals
 *    joined by genuine separator tokens;
 *  * the [ReferenceKind] is inferred from the enclosing **rule contexts**
 *    (`importDeclaration`, `useTree`, `typeType`, `annotation`, ...) with the adjacent tokens
 *    used only as a tie-breaker;
 *  * declared names are recognised as the name token of their declaring rule and are
 *    therefore never reported as references;
 *  * offsets, lines and columns come directly from [Token] and are exact by construction.
 *
 * No attempt is made to resolve a reference to a declaration - this is purely grammatical data.
 */
object SymbolReferenceScanner {

  /** Rules that make everything below them part of an import/use/package directive. */
  val DEFAULT_IMPORT_RULES: Regex =
    Regex("import|^package|use(declaration|tree|clause|path|item|alias)|externcrate|^require|^include")

  /** Rules denoting annotation / decorator / attribute usage. */
  val DEFAULT_ANNOTATION_RULES: Regex =
    Regex("annotation|decorator|^outerattribute|^innerattribute|^attr$")

  /** Rules denoting a type position (`typeType`, `userType`, `typeArguments`, `traitBound`, ...). */
  val DEFAULT_TYPE_RULES: Regex =
    Regex("type|inheritance|superclass|superinterfaces|delegationspecifier|traitbound|impltrait|permits")

  /** Rules that declare a name (the name token itself is a declaration, not a reference). */
  val DEFAULT_DECLARATION_RULES: Regex = Regex(
    "declaration$|definition$|declarator|item$|parameter$|param$|binding$|" +
        "^classdef$|^funcdef$|^async_funcdef$|^function_$|^struct|^tuplestruct$|^trait_$|^union_$|" +
        "^enumeration$|^module$|^typealias|^macroinvocationsemi$".let { it }
  )

  /** Immediate child rules that hold the declared name (`identifier`, `simpleIdentifier`, ...). */
  val DEFAULT_NAME_RULES: Regex =
    Regex("^(identifier|simpleidentifier|name|typename|typeidentifier|variabledeclaratorid|declaredname|ident|id)$")

  /** Keywords after which an identifier is (most likely) a type - used when rule names are unhelpful. */
  val DEFAULT_TYPE_KEYWORDS: Set<String> = setOf(
    "new", "is", "as", "extends", "implements", "instanceof", "throws", "impl", "dyn",
    "catch", "except", "typeof", "sizeof", "in",
  )

  /** Names that a grammar may lex as identifiers but which never denote a symbol. */
  val DEFAULT_IGNORED_NAMES: Set<String> = setOf("_", "self", "cls", "this", "super", "it")

  data class Options(
    /**
     * Symbolic token names treated as identifiers. When empty the vocabulary is inspected
     * heuristically (`*IDENT*`, `NAME`, `ID`), which covers every bundled grammar.
     */
    val identifierTokenNames: Set<String> = emptySet(),
    /** Tokens joining the elements of a qualified name. */
    val separatorTokens: Set<String> = setOf(".", "::", "?."),
    /** Tokens that introduce an annotation/attribute when directly preceding a chain. */
    val annotationPrefixTokens: Set<String> = setOf("@", "#[", "#!["),
    val ignoredNames: Set<String> = DEFAULT_IGNORED_NAMES,
    val importRules: Regex = DEFAULT_IMPORT_RULES,
    val annotationRules: Regex = DEFAULT_ANNOTATION_RULES,
    val typeRules: Regex = DEFAULT_TYPE_RULES,
    val declarationRules: Regex = DEFAULT_DECLARATION_RULES,
    val nameRules: Regex = DEFAULT_NAME_RULES,
    val typeKeywords: Set<String> = DEFAULT_TYPE_KEYWORDS,
    /** Guard against pathological trees. */
    val maxAncestorDepth: Int = 32,
    /** Hard cap so a pathological file cannot blow up memory. */
    val maxReferences: Int = 100_000,
  )

  /**
   * Collect every grammatical reference found in [tree].
   *
   * @param tree parse tree produced by the language's parser (partial trees are fine)
   * @param ruleNames `parser.ruleNames`
   * @param vocabulary `parser.vocabulary`
   * @param symbols declarations of the same source, used to attribute each reference to the
   *   declaration that encloses it
   */
  fun scan(
    tree: ParseTree,
    ruleNames: Array<String>,
    vocabulary: Vocabulary,
    symbols: List<SymbolInfo> = emptyList(),
    options: Options = Options(),
  ): List<SymbolReference> {
    val ctx = Ctx(ruleNames, vocabulary, options)
    val terminals = ArrayList<TerminalNode>()
    collectTerminals(tree, terminals)
    if (terminals.isEmpty()) return emptyList()
    val declared = declaredNameTokens(tree, ctx, HashSet())
    val out = ArrayList<SymbolReference>()
    var i = 0
    while (i < terminals.size && out.size < options.maxReferences) {
      if (!ctx.isIdentifier(terminals[i].symbol)) {
        i++
        continue
      }

      val parts = ArrayList<TerminalNode>().apply { add(terminals[i]) }
      val separators = ArrayList<String>()
      var j = i + 1
      while (j + 1 < terminals.size &&
        (terminals[j].text ?: "") in options.separatorTokens &&
        ctx.isIdentifier(terminals[j + 1].symbol)
      ) {
        separators.add(terminals[j].text)
        parts.add(terminals[j + 1])
        j += 2
      }

      val chainText = buildString {
        parts.forEachIndexed { k, part ->
          if (k > 0) append(separators[k - 1])
          append(part.text)
        }
      }
      val previous = terminals.getOrNull(i - 1)?.text

      for ((idx, part) in parts.withIndex()) {
        val token = part.symbol
        val name = token.text ?: continue
        val next = if (idx == parts.lastIndex) terminals.getOrNull(j)?.text else separators[idx]
        val kind = ctx.referenceKind(part, idx, previous, next)
        // The name of a declaration is not a reference to it (imports are aliases, keep those).
        if (kind != ReferenceKind.IMPORT && token.tokenIndex in declared) continue
        if (name in options.ignoredNames) continue
        out.add(
          SymbolReference(
            name = name,
            text = chainText,
            kind = kind,
            range = rangeOf(token),
            qualifier = if (idx == 0) null else parts.take(idx).joinToString(".") { it.text },
            enclosingSymbol = enclosingOf(symbols, token.startIndex),
          )
        )
        if (out.size >= options.maxReferences) break
      }
      i = j.coerceAtLeast(i + 1)
    }
    return out
  }

  private class Ctx(
    val ruleNames: Array<String>,
    val vocabulary: Vocabulary,
    val options: Options,
  ) {
    private val explicitIdentifiers = options.identifierTokenNames.map { it.uppercase() }.toSet()

    fun ruleName(ctx: ParserRuleContext): String? = ruleNames.getOrNull(ctx.ruleIndex)?.lowercase()

    fun isIdentifier(token: Token): Boolean {
      if (token.type == Token.EOF) return false
      val symbolic = (vocabulary.getSymbolicName(token.type) ?: return false).uppercase()
      if (explicitIdentifiers.isNotEmpty()) return symbolic in explicitIdentifiers
      return symbolic.contains("IDENT") || symbolic == "NAME" || symbolic == "ID"
    }

    /** Enclosing rule names, innermost first. */
    fun ancestors(node: ParseTree): List<String> {
      val out = ArrayList<String>()
      var parent = node.parent as? ParserRuleContext
      var depth = 0
      while (parent != null && depth++ < options.maxAncestorDepth) {
        ruleName(parent)?.let { out.add(it) }
        parent = parent.parent as? ParserRuleContext
      }
      return out
    }

    fun referenceKind(node: TerminalNode, idx: Int, previous: String?, next: String?): ReferenceKind {
      val ancestors = ancestors(node)
      return when {
        ancestors.any { options.importRules.containsMatchIn(it) } -> ReferenceKind.IMPORT
        (idx == 0 && previous in options.annotationPrefixTokens) ||
            ancestors.any { options.annotationRules.containsMatchIn(it) } -> ReferenceKind.ANNOTATION

        next == "(" -> ReferenceKind.CALL
        idx > 0 -> ReferenceKind.MEMBER
        ancestors.any { options.typeRules.containsMatchIn(it) } -> ReferenceKind.TYPE
        previous == ":" || previous == "<" ||
            (previous ?: "").lowercase() in options.typeKeywords -> ReferenceKind.TYPE

        else -> ReferenceKind.IDENTIFIER
      }
    }
  }

  private fun collectTerminals(node: ParseTree, out: MutableList<TerminalNode>) {
    if (node is ErrorNode) return
    if (node is TerminalNode) {
      if (node.symbol?.type != Token.EOF) out.add(node)
      return
    }
    for (i in 0 until node.childCount) collectTerminals(node.getChild(i), out)
  }

  /** Token indices of every name *declared* by a declaration rule in [node]. */
  private fun declaredNameTokens(node: ParseTree, ctx: Ctx, out: MutableSet<Int>): Set<Int> {
    if (node is ParserRuleContext) {
      val rule = ctx.ruleName(node)
      if (rule != null && ctx.options.declarationRules.containsMatchIn(rule)) {
        declaredNameToken(node, ctx)?.let { out.add(it) }
      }
    }
    for (i in 0 until node.childCount) declaredNameTokens(node.getChild(i), ctx, out)
    return out
  }

  /**
   * The declared name of [declaration]: either an identifier token owned directly by the rule
   * (`'class' Identifier ...`) or the first identifier of an immediate name sub-rule
   * (`... simpleIdentifier functionValueParameters`).
   */
  private fun declaredNameToken(declaration: ParserRuleContext, ctx: Ctx): Int? {
    for (i in 0 until declaration.childCount) {
      val child = declaration.getChild(i)
      if (child is TerminalNode && child !is ErrorNode && ctx.isIdentifier(child.symbol)) {
        return child.symbol.tokenIndex
      }
    }
    for (i in 0 until declaration.childCount) {
      val child = declaration.getChild(i) as? ParserRuleContext ?: continue
      val rule = ctx.ruleName(child) ?: continue
      if (!ctx.options.nameRules.containsMatchIn(rule)) continue
      firstIdentifier(child, ctx)?.let { return it }
    }
    return null
  }

  private fun firstIdentifier(node: ParseTree, ctx: Ctx): Int? {
    if (node is ErrorNode) return null
    if (node is TerminalNode) return if (ctx.isIdentifier(node.symbol)) node.symbol.tokenIndex else null
    for (i in 0 until node.childCount) firstIdentifier(node.getChild(i), ctx)?.let { return it }
    return null
  }

  private fun rangeOf(token: Token): TextRange {
    val start = token.startIndex.coerceAtLeast(0)
    val length = token.text?.length ?: 0
    return TextRange(
      startOffset = start,
      endOffset = (token.stopIndex + 1).coerceAtLeast(start),
      startLine = token.line,
      startColumn = token.charPositionInLine,
      endLine = token.line,
      endColumn = token.charPositionInLine + length,
    )
  }

  /** Deepest declaration whose range contains [offset], as a dotted name. */
  private fun enclosingOf(
    symbols: List<SymbolInfo>,
    offset: Int,
    prefix: String = "",
  ): String? {
    for (s in symbols) {
      if (offset >= s.range.startOffset && offset < s.range.endOffset) {
        val qualified = if (prefix.isEmpty()) s.name else "$prefix.${s.name}"
        return enclosingOf(s.children, offset, qualified) ?: qualified
      }
    }
    return null
  }
}