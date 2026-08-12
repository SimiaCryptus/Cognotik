package com.simiacryptus.cognotik.text.validate

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class FileValidatorsTest {

  /** Triple backtick, expressed via escapes so the literal never appears in source. */
  private val fence = "\u0060\u0060\u0060"

  private lateinit var originalProviders: List<(String?) -> GrammarValidator?>

  @BeforeEach
  fun captureProviders() {
    originalProviders = FileValidators.validatorProviders.toList()
  }

  @AfterEach
  fun restoreProviders() {
    FileValidators.validatorProviders.clear()
    FileValidators.validatorProviders.addAll(originalProviders)
  }

  private fun diffBlock(body: String) = fence + "diff\n" + body + "\n" + fence

  // ---------------------------------------------------------------- constants

  @Test
  fun `logger is initialised`() {
    assertNotNull(FileValidators.log)
    assertEquals(FileValidators::class.java.name, FileValidators.log.name)
  }

  @Test
  fun `max diff size is 100k chars`() {
    assertEquals(100000, FileValidators.MAX_DIFF_SIZE_CHARS)
  }

  @Test
  fun `default provider list has language provider first and generic fallback last`() {
    assertEquals(2, FileValidators.validatorProviders.size)
    assertNull(FileValidators.validatorProviders.first().invoke("Foo.unknownext"))
    assertTrue(FileValidators.validatorProviders.last().invoke(null) is ParenMatchingValidator)
  }

  // ------------------------------------------------------------ getValidator

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
    "Main.kt,               KotlinGrammarValidator",
    "Main.ktm,              KotlinGrammarValidator",
    "build.gradle.kts,      KotlinGrammarValidator",
    "lib.rs,                RustGrammarValidator",
    "main.py,               PythonGrammarValidator",
    "stub.pyi,              PythonGrammarValidator",
    "script.pyw,            PythonGrammarValidator",
    "Main.java,             JavaGrammarValidator",
    "app.ts,                TypeScriptGrammarValidator",
    "app.tsx,               TypeScriptGrammarValidator",
    "app.mts,               TypeScriptGrammarValidator",
    "app.cts,               TypeScriptGrammarValidator",
    "app.js,                JavaScriptGrammarValidator",
    "app.mjs,               JavaScriptGrammarValidator",
    "app.cjs,               JavaScriptGrammarValidator",
    "app.jsx,               JavaScriptGrammarValidator",
    "package.json,          Json5GrammarValidator",
    "tsconfig.json5,        Json5GrammarValidator",
    "site.css,             CssGrammarValidator",
    "index.html,            HtmlGrammarValidator",
    "index.htm,             HtmlGrammarValidator",
    "index.xhtml,           HtmlGrammarValidator",
  )
  fun `getValidator maps known extensions`(filename: String, expectedSimpleName: String) {
    val validator = FileValidators.getValidator(filename.trim())
    assertEquals(expectedSimpleName, validator.javaClass.simpleName)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "notes.txt",
      "image.PNG",
      "archive.tar.gz",
      "Makefile",
      "file.",
      ".gitignore.weird",
      "a.b.c.unknown",
    ]
  )
  fun `getValidator falls back to paren matching for unknown extensions`(filename: String) {
    assertTrue(FileValidators.getValidator(filename) is ParenMatchingValidator)
  }

  @Test
  fun `getValidator falls back for null filename`() {
    assertTrue(FileValidators.getValidator(null) is ParenMatchingValidator)
  }

  @Test
  @DisplayName("extension matching is case sensitive (documented behaviour)")
  fun `extension matching is case sensitive`() {
    assertTrue(FileValidators.getValidator("Main.KT") is ParenMatchingValidator)
    assertTrue(FileValidators.getValidator("Main.Java") is ParenMatchingValidator)
    assertTrue(FileValidators.getValidator("Main.kt") is KotlinGrammarValidator)
  }

  @Test
  fun `getValidator works with paths`() {
    assertTrue(FileValidators.getValidator("src/main/kotlin/Foo.kt") is KotlinGrammarValidator)
    assertTrue(FileValidators.getValidator("C:\\proj\\Foo.java") is JavaGrammarValidator)
  }

  @Test
  @DisplayName("a dot in a directory name defeats extension detection (documented quirk)")
  fun `dotted directory names are not misread as extensions`() {
    assertTrue(FileValidators.getValidator("/home/user.name/Makefile") is ParenMatchingValidator)
  }

  @Test
  @DisplayName("a bare extension-like name is treated as that extension (documented quirk)")
  fun `bare extension name resolves to language validator`() {
    assertTrue(FileValidators.getValidator("kt") is KotlinGrammarValidator)
  }

  @Test
  fun `kts is parsed as a script and kt is not`() {
    assertTrue(isScriptMode(FileValidators.getValidator("build.gradle.kts")))
    assertFalse(isScriptMode(FileValidators.getValidator("Build.kt")))
  }

  @Test
  fun `getValidator returns a fresh instance per call`() {
    val a = FileValidators.getValidator("Main.kt")
    val b = FileValidators.getValidator("Main.kt")
    assertNotSame(a, b)
    assertEquals(a.javaClass, b.javaClass)
  }

  @Test
  fun `getValidator throws when no provider matches`() {
    FileValidators.validatorProviders.clear()
    FileValidators.validatorProviders.add { null }
    assertThrows(NoSuchElementException::class.java) { FileValidators.getValidator("Main.kt") }
  }

  @Test
  fun `first matching provider wins`() {
    val custom = FakeValidator()
    FileValidators.validatorProviders.add(0) { custom }
    assertSame(custom, FileValidators.getValidator("Main.kt"))
    assertSame(custom, FileValidators.getValidator(null))
  }

  // -------------------------------------------------- getLanguageValidator

  @Test
  fun `getLanguageValidator returns language specific validator`() {
    val validator = FileValidators.getLanguageValidator("Main.kt")
    assertTrue(validator is KotlinGrammarValidator)
  }

  @Test
  fun `getLanguageValidator returns null when only the fallback applies`() {
    assertNull(FileValidators.getLanguageValidator("notes.txt"))
    assertNull(FileValidators.getLanguageValidator(null))
    assertNull(FileValidators.getLanguageValidator(""))
  }

  @Test
  fun `getLanguageValidator honours custom providers`() {
    val custom = FakeValidator()
    FileValidators.validatorProviders.add(0) { filename -> custom.takeIf { filename == "weird.zzz" } }
    assertSame(custom, FileValidators.getLanguageValidator("weird.zzz"))
    assertNull(FileValidators.getLanguageValidator("other.zzz"))
  }

  @Test
  fun `isSupported mirrors getLanguageValidator`() {
    listOf("Main.kt", "a.py", "a.java", "a.ts", "a.js", "a.json", "a.css", "a.html", "a.rs")
      .forEach { assertTrue(FileValidators.isSupported(it), "expected $it to be supported") }
    listOf("a.txt", "a.md", null, "", "Makefile")
      .forEach { assertFalse(FileValidators.isSupported(it), "expected $it to be unsupported") }
  }

  // ------------------------------------------------- convenience accessors

  @Test
  fun `extractPublicSymbols delegates to the selected validator`() {
    val symbol = symbol("Foo")
    FileValidators.validatorProviders.add(0) { FakeValidator(symbols = listOf(symbol)) }
    assertEquals(listOf(symbol), FileValidators.extractPublicSymbols("anything.kt", "code"))
  }

  @Test
  fun `extractPublicSymbols is empty for unsupported languages`() {
    assertTrue(FileValidators.extractPublicSymbols("notes.txt", "( unbalanced").isEmpty())
    assertTrue(FileValidators.extractPublicSymbols(null, "whatever").isEmpty())
  }

  @Test
  fun `extractPublicSymbols swallows exceptions and errors`() {
    FileValidators.validatorProviders.add(0) { FakeValidator(failSymbols = true) }
    assertTrue(FileValidators.extractPublicSymbols("Main.kt", "code").isEmpty())
  }

  @Test
  fun `extractPublicSymbols swallows provider failures`() {
    FileValidators.validatorProviders.add(0) { throw IllegalStateException("provider boom") }
    assertTrue(FileValidators.extractPublicSymbols("Main.kt", "code").isEmpty())
  }

  @Test
  fun `extractSymbolReferences delegates to the selected validator`() {
    val reference = reference("bar", qualifier = "foo")
    FileValidators.validatorProviders.add(0) { FakeValidator(references = listOf(reference)) }
    assertEquals(listOf(reference), FileValidators.extractSymbolReferences("anything.kt", "code"))
  }

  @Test
  fun `extractSymbolReferences is empty for unsupported languages`() {
    assertTrue(FileValidators.extractSymbolReferences("notes.txt", "foo.bar").isEmpty())
    assertTrue(FileValidators.extractSymbolReferences(null, "foo.bar").isEmpty())
  }

  @Test
  fun `extractSymbolReferences swallows throwables`() {
    FileValidators.validatorProviders.add(0) { FakeValidator(failReferences = true) }
    assertTrue(FileValidators.extractSymbolReferences("Main.kt", "code").isEmpty())
  }

  @Test
  fun `extractSymbolReferences receives symbols extracted from the same code`() {
    val symbol = symbol("Foo")
    val spy = FakeValidator(symbols = listOf(symbol))
    FileValidators.validatorProviders.add(0) { spy }
    FileValidators.extractSymbolReferences("Main.kt", "code")
    assertEquals(listOf(symbol), spy.lastSymbolsArgument)
  }

  // --------------------------------------------------------- DIFF_PATTERN

  @Nested
  inner class DiffPattern {

    @Test
    fun `matches a block at the very start of the input`() {
      val match = FileValidators.DIFF_PATTERN.find(diffBlock("-a\n+b"))
      assertNotNull(match)
      assertEquals("-a\n+b", match!!.groupValues[1])
    }

    @Test
    fun `matches a block preceded by a newline`() {
      val input = "Here is a patch:\n" + diffBlock("+added")
      val match = FileValidators.DIFF_PATTERN.find(input)
      assertNotNull(match)
      assertEquals("+added", match!!.groupValues[1])
    }

    @Test
    fun `does not match when the fence is not at the start of a line`() {
      assertNull(FileValidators.DIFF_PATTERN.find("prefix " + diffBlock("+added")))
      assertNull(FileValidators.DIFF_PATTERN.find("prefix\tx" + diffBlock("+added")))
    }

    @Test
    fun `captures an empty body`() {
      val match = FileValidators.DIFF_PATTERN.find(fence + "diff\n\n" + fence)
      assertNotNull(match)
      assertEquals("", match!!.groupValues[1])
    }

    @Test
    fun `is non greedy so adjacent blocks are separate matches`() {
      val input = diffBlock("+one") + "\n" + diffBlock("+two")
      val bodies = FileValidators.DIFF_PATTERN.findAll(input).map { it.groupValues[1] }.toList()
      assertEquals(listOf("+one", "+two"), bodies)
    }

    @Test
    fun `dotall lets the body span many lines`() {
      val body = (1..50).joinToString("\n") { "+line $it" }
      val match = FileValidators.DIFF_PATTERN.find(diffBlock(body))
      assertNotNull(match)
      assertEquals(body, match!!.groupValues[1])
    }

    @Test
    fun `requires the diff language tag`() {
      assertNull(FileValidators.DIFF_PATTERN.find(fence + "\n+added\n" + fence))
      assertNull(FileValidators.DIFF_PATTERN.find(fence + "kotlin\nval x = 1\n" + fence))
    }

    @Test
    fun `requires a newline immediately after the tag`() {
      assertNull(FileValidators.DIFF_PATTERN.find(fence + "diff +added\n" + fence))
    }

    @Test
    fun `ignores an unterminated block`() {
      assertNull(FileValidators.DIFF_PATTERN.find(fence + "diff\n+added\n"))
    }
  }

  // ------------------------------------------------------------- utilities

  private fun isScriptMode(validator: GrammarValidator): Boolean {
    val field = validator.javaClass.getDeclaredField("isScript")
    field.isAccessible = true
    return field.getBoolean(validator)
  }

  private fun range() = GrammarValidator.TextRange(0, 1, 1, 0, 1, 1)

  private fun symbol(name: String) = GrammarValidator.SymbolInfo(
    name = name,
    signature = "class $name",
    kind = GrammarValidator.SymbolKind.CLASS,
    range = range(),
  )

  private fun reference(name: String, qualifier: String? = null) = GrammarValidator.SymbolReference(
    name = name,
    text = listOfNotNull(qualifier, name).joinToString("."),
    kind = GrammarValidator.ReferenceKind.MEMBER,
    range = range(),
    qualifier = qualifier,
  )

  private class FakeValidator(
    val symbols: List<GrammarValidator.SymbolInfo> = emptyList(),
    val references: List<GrammarValidator.SymbolReference> = emptyList(),
    val failSymbols: Boolean = false,
    val failReferences: Boolean = false,
  ) : GrammarValidator {
    var lastSymbolsArgument: List<GrammarValidator.SymbolInfo>? = null

    override fun validateGrammar(code: String): List<GrammarValidator.ValidationError> = emptyList()

    override fun extractPublicSymbols(code: String): List<GrammarValidator.SymbolInfo> {
      if (failSymbols) throw IllegalStateException("symbol extraction boom")
      return symbols
    }

    override fun extractSymbolReferences(
      code: String,
      symbols: List<GrammarValidator.SymbolInfo>,
    ): List<GrammarValidator.SymbolReference> {
      lastSymbolsArgument = symbols
      if (failReferences) throw StackOverflowError("reference extraction boom")
      return references
    }
  }
}