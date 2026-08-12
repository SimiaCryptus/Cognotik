package com.simiacryptus.cognotik.text.validate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GrammarValidatorContractTest {

  /** Minimal implementation exercising only the mandatory member. */
  private class MinimalValidator : GrammarValidator {
    override fun validateGrammar(code: String): List<GrammarValidator.ValidationError> =
      if (code.isBlank()) listOf(GrammarValidator.ValidationError("empty")) else emptyList()
  }

  private fun range(
    start: Int = 0,
    end: Int = 10,
    startLine: Int = 1,
    startColumn: Int = 0,
    endLine: Int = 1,
    endColumn: Int = 10,
  ) = GrammarValidator.TextRange(start, end, startLine, startColumn, endLine, endColumn)

  private fun symbol(
    name: String,
    kind: GrammarValidator.SymbolKind = GrammarValidator.SymbolKind.CLASS,
    children: List<GrammarValidator.SymbolInfo> = emptyList(),
  ) = GrammarValidator.SymbolInfo(name, "signature of $name", kind, range(), children)

  @Nested
  inner class Defaults {
    @Test
    fun `extractPublicSymbols defaults to empty`() {
      assertTrue(MinimalValidator().extractPublicSymbols("class Foo").isEmpty())
    }

    @Test
    fun `extractSymbolReferences defaults to empty`() {
      assertTrue(MinimalValidator().extractSymbolReferences("foo.bar").isEmpty())
    }

    @Test
    fun `extractSymbolReferences default symbols argument comes from extractPublicSymbols`() {
      val declared = listOf(symbol("Foo"))
      var captured: List<GrammarValidator.SymbolInfo>? = null
      val validator = object : GrammarValidator {
        override fun validateGrammar(code: String) = emptyList<GrammarValidator.ValidationError>()
        override fun extractPublicSymbols(code: String) = declared
        override fun extractSymbolReferences(
          code: String,
          symbols: List<GrammarValidator.SymbolInfo>,
        ): List<GrammarValidator.SymbolReference> {
          captured = symbols
          return emptyList()
        }
      }
      validator.extractSymbolReferences("code")
      assertEquals(declared, captured)
    }

    @Test
    fun `validateGrammar is the only required member`() {
      assertTrue(MinimalValidator().validateGrammar("ok").isEmpty())
      assertEquals(1, MinimalValidator().validateGrammar("   ").size)
    }
  }

  @Nested
  inner class ValidationErrors {
    @Test
    fun `defaults are null position and ERROR severity`() {
      val error = GrammarValidator.ValidationError("boom")
      assertNull(error.line)
      assertNull(error.column)
      assertEquals(GrammarValidator.Severity.ERROR, error.severity)
    }

    @Test
    fun `is a value type`() {
      assertEquals(
        GrammarValidator.ValidationError("boom", 3, 7),
        GrammarValidator.ValidationError("boom", 3, 7),
      )
      assertNotEquals(
        GrammarValidator.ValidationError("boom", 3, 7),
        GrammarValidator.ValidationError("boom", 3, 8),
      )
    }

    @Test
    fun `severity enum has a single ERROR value`() {
      assertEquals(listOf("ERROR"), GrammarValidator.Severity.entries.map { it.name })
    }
  }

  @Nested
  inner class TextRanges {
    @Test
    fun `length is the offset delta`() {
      assertEquals(10, range(5, 15).length)
      assertEquals(0, range(5, 5).length)
    }

    @Test
    fun `length never goes negative`() {
      assertEquals(0, range(15, 5).length)
    }

    @Test
    fun `substringOf extracts the covered text`() {
      val code = "val answer = 42"
      assertEquals("answer", range(4, 10).substringOf(code))
    }

    @Test
    fun `substringOf clamps out of range offsets`() {
      val code = "abc"
      assertEquals("abc", range(0, 999).substringOf(code))
      assertEquals("", range(999, 1000).substringOf(code))
      assertEquals("", range(-5, -1).substringOf(code))
      assertEquals("", range(2, 1).substringOf(code))
    }

    @Test
    fun `substringOf tolerates empty code`() {
      assertEquals("", range(0, 10).substringOf(""))
    }
  }

  @Nested
  inner class SymbolInfos {
    @Test
    fun `children default to empty`() {
      assertTrue(symbol("Foo").children.isEmpty())
    }

    @Test
    fun `flatten walks depth first`() {
      val tree = symbol(
        "Outer",
        children = listOf(
          symbol("Inner", children = listOf(symbol("deepest", GrammarValidator.SymbolKind.FUNCTION))),
          symbol("sibling", GrammarValidator.SymbolKind.PROPERTY),
        ),
      )
      assertEquals(listOf("Outer", "Inner", "deepest", "sibling"), tree.flatten().map { it.name })
    }

    @Test
    fun `flatten of a leaf is itself`() {
      val leaf = symbol("Foo")
      assertEquals(listOf(leaf), leaf.flatten())
    }

    @Test
    fun `qualifiedNames builds dotted paths`() {
      val tree = symbol(
        "Outer",
        children = listOf(symbol("Inner", children = listOf(symbol("run", GrammarValidator.SymbolKind.FUNCTION)))),
      )
      assertEquals(listOf("Outer", "Outer.Inner", "Outer.Inner.run"), tree.qualifiedNames())
    }

    @Test
    fun `qualifiedNames honours an explicit prefix`() {
      val tree = symbol("Bar", children = listOf(symbol("baz", GrammarValidator.SymbolKind.FUNCTION)))
      assertEquals(listOf("com.foo.Bar", "com.foo.Bar.baz"), tree.qualifiedNames("com.foo"))
    }

    @Test
    fun `qualifiedNames ignores an empty prefix`() {
      assertEquals(listOf("Bar"), symbol("Bar").qualifiedNames(""))
    }

    @Test
    fun `flatten and qualifiedNames stay aligned`() {
      val tree = symbol(
        "A",
        children = listOf(symbol("b"), symbol("c", children = listOf(symbol("d")))),
      )
      assertEquals(tree.flatten().size, tree.qualifiedNames().size)
    }

    @Test
    fun `symbol kinds cover the documented taxonomy`() {
      val names = GrammarValidator.SymbolKind.entries.map { it.name }
      assertTrue(
        names.containsAll(
          listOf(
            "MODULE", "CLASS", "INTERFACE", "TRAIT", "ENUM", "OBJECT", "STRUCT", "ANNOTATION",
            "TYPE_ALIAS", "FUNCTION", "CONSTRUCTOR", "PROPERTY", "FIELD", "VARIABLE",
            "CONSTANT", "RULE", "OTHER",
          )
        ),
        "missing kinds in $names",
      )
    }
  }

  @Nested
  inner class SymbolReferences {
    private fun reference(
      name: String,
      qualifier: String? = null,
      enclosingSymbol: String? = null,
      kind: GrammarValidator.ReferenceKind = GrammarValidator.ReferenceKind.IDENTIFIER,
    ) = GrammarValidator.SymbolReference(
      name = name,
      text = listOfNotNull(qualifier, name).joinToString("."),
      kind = kind,
      range = range(),
      qualifier = qualifier,
      enclosingSymbol = enclosingSymbol,
    )

    @Test
    fun `qualifiedName prefixes the qualifier when present`() {
      assertEquals("foo.bar", reference("bar", qualifier = "foo").qualifiedName)
      assertEquals("a.b.c", reference("c", qualifier = "a.b").qualifiedName)
    }

    @Test
    fun `qualifiedName is the bare name when unqualified`() {
      assertEquals("bar", reference("bar").qualifiedName)
      assertEquals("bar", reference("bar", qualifier = "").qualifiedName)
    }

    @Test
    fun `optional fields default to null`() {
      val ref = GrammarValidator.SymbolReference(
        name = "bar",
        text = "bar",
        kind = GrammarValidator.ReferenceKind.IDENTIFIER,
        range = range(),
      )
      assertNull(ref.qualifier)
      assertNull(ref.enclosingSymbol)
      assertEquals("bar", ref.qualifiedName)
    }

    @Test
    fun `is a value type`() {
      assertEquals(reference("bar", "foo", "Outer"), reference("bar", "foo", "Outer"))
      assertNotEquals(reference("bar", "foo"), reference("bar", "fooz"))
    }

    @Test
    fun `reference kinds cover the documented taxonomy`() {
      assertEquals(
        listOf("IMPORT", "ANNOTATION", "TYPE", "CALL", "MEMBER", "IDENTIFIER"),
        GrammarValidator.ReferenceKind.entries.map { it.name },
      )
    }
  }
}