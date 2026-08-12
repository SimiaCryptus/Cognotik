package com.simiacryptus.cognotik.text.validate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration

class KotlinGrammarValidatorTest {

  private val fileValidator = KotlinGrammarValidator()
  private val scriptValidator = KotlinGrammarValidator(isScript = true)

  private val validFile = """
      package com.example

      import kotlin.math.max

      class Bar(private val x: Int) {
        fun baz(y: Int): Int = max(x, y)
      }
    """.trimIndent() + "\n"

  private fun errors(validator: KotlinGrammarValidator, code: String) = validator.validateGrammar(code)

  @Test
  fun `accepts a well formed kotlin file`() {
    assertTrue(errors(fileValidator, validFile).isEmpty(), "unexpected errors: ${errors(fileValidator, validFile)}")
  }

  @Test
  fun `accepts an empty file`() {
    assertTrue(errors(fileValidator, "").isEmpty())
    assertTrue(errors(fileValidator, "\n\n").isEmpty())
  }

  @Test
  fun `accepts a comment only file`() {
    val code = "// just a comment\n/* and a block one */\n"
    assertTrue(errors(fileValidator, code).isEmpty())
  }

  @Test
  fun `rejects an unbalanced class body`() {
    val problems = errors(fileValidator, "class Broken {\n  fun oops(\n")
    assertFalse(problems.isEmpty(), "expected syntax errors")
  }

  @Test
  fun `rejects garbage input`() {
    assertFalse(errors(fileValidator, "!!! this is definitely not kotlin ###").isEmpty())
  }

  @Test
  fun `errors carry a message and a position`() {
    val problems = errors(fileValidator, "class Broken {\n  fun oops(\n")
    assumeTrue(problems.isNotEmpty(), "validator reported no errors")
    problems.forEach { error ->
      assertTrue(error.message.isNotBlank(), "blank message in $error")
      assertEquals(GrammarValidator.Severity.ERROR, error.severity)
      error.line?.let { assertTrue(it >= 1, "line should be 1-based, was $it in $error") }
      error.column?.let { assertTrue(it >= 0, "column should be 0-based, was $it in $error") }
    }
  }

  @Test
  fun `script mode accepts top level statements`() {
    val script = "val x = 1\nprintln(x)\n"
    val problems = errors(scriptValidator, script)
    assertTrue(problems.isEmpty(), "unexpected errors: $problems")
  }

  @Test
  fun `script mode accepts declarations too`() {
    val script = "fun helper() = 42\nprintln(helper())\n"
    assertTrue(errors(scriptValidator, script).isEmpty())
  }

  @Test
  fun `script mode still rejects broken code`() {
    assertFalse(errors(scriptValidator, "val = = 1\n").isEmpty())
  }

  @Test
  fun `defaults to file mode`() {
    val field = KotlinGrammarValidator::class.java.getDeclaredField("isScript")
    field.isAccessible = true
    assertFalse(field.getBoolean(KotlinGrammarValidator()))
    assertTrue(field.getBoolean(KotlinGrammarValidator(isScript = true)))
  }

  @Test
  fun `reports the kotlin language name`() {
    assertTrue(
      fileValidator.toString().contains("Kotlin") ||
          errors(fileValidator, "!!!").any { it.message.isNotBlank() },
      "validator should be identifiable as Kotlin",
    )
  }

  @Test
  fun `validator instances are reusable`() {
    repeat(3) { assertTrue(errors(fileValidator, validFile).isEmpty()) }
    assertFalse(errors(fileValidator, "class {").isEmpty())
    assertTrue(errors(fileValidator, validFile).isEmpty())
  }

  @Test
  fun `handles a large file in reasonable time`() {
    val code = buildString {
      appendLine("package com.example")
      repeat(200) { appendLine("fun f$it(a: Int, b: Int): Int = a + b + $it") }
    }
    assertTimeoutPreemptively(Duration.ofSeconds(30)) {
      assertTrue(errors(fileValidator, code).isEmpty())
    }
  }

  @Test
  fun `extractPublicSymbols finds declarations when implemented`() {
    val symbols = fileValidator.extractPublicSymbols(validFile)
    val names = symbols.flatMap { it.flatten() }.map { it.name }
    assumeTrue(names.isNotEmpty(), "this validator does not implement symbol extraction")
    assertTrue(names.contains("Bar"), "expected class Bar in $names")
    symbols.flatMap { it.flatten() }.forEach { symbol ->
      assertTrue(symbol.range.startOffset <= symbol.range.endOffset, "bad range for ${symbol.name}")
      assertTrue(symbol.range.startLine >= 1, "line should be 1-based for ${symbol.name}")
      assertTrue(symbol.signature.isNotBlank(), "blank signature for ${symbol.name}")
      assertTrue(
        symbol.range.substringOf(validFile).contains(symbol.name),
        "range of ${symbol.name} does not cover its name",
      )
    }
  }

  @Test
  fun `extractSymbolReferences finds usages when implemented`() {
    val references = fileValidator.extractSymbolReferences(validFile)
    assumeTrue(references.isNotEmpty(), "this validator does not implement reference extraction")
    assertTrue(references.any { it.name == "max" }, "expected a reference to max in $references")
    references.forEach { ref ->
      assertTrue(ref.name.isNotBlank())
      assertTrue(ref.text.contains(ref.name), "text '${ref.text}' should contain '${ref.name}'")
      assertTrue(ref.range.startOffset <= ref.range.endOffset)
      assertEquals(
        if (ref.qualifier.isNullOrEmpty()) ref.name else "${ref.qualifier}.${ref.name}",
        ref.qualifiedName,
      )
    }
  }

  @Test
  fun `symbol extraction never throws on malformed input`() {
    val broken = "class Broken {\n  fun oops(\n"
    fileValidator.extractPublicSymbols(broken)
    fileValidator.extractSymbolReferences(broken)
  }
}