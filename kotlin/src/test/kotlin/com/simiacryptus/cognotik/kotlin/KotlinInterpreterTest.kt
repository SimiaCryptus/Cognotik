@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.simiacryptus.cognotik.kotlin

import com.simiacryptus.cognotik.core.actors.CodingActor
import com.simiacryptus.cognotik.interpreter.InterpreterTestBase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KotlinInterpreterTest : InterpreterTestBase() {

  override fun newInterpreter(map: Map<String, Any>) = KotlinInterpreter(map)

  @Test
  fun `test run with kotlin println`() {
    val interpreter = newInterpreter(mapOf())
    val result = interpreter.run("""println("Hello World")""")
    Assertions.assertEquals(null, result)
  }

  @Test
  fun `test validate with kotlin println`() {
    val interpreter = newInterpreter(mapOf())
    val result = interpreter.validate("""println("Hello World")""")
    Assertions.assertEquals(null, result)
  }

  @Test
  fun `test validate with invalid function`() {
    val interpreter = newInterpreter(mapOf())
    @Language("kotlin") val code = """
            fun foo() {
                functionNotDefined()
            }
        """.trimIndent()
    // This should fail because functionNotDefined is not defined...
    val result = interpreter.validate(code)
    Assertions.assertTrue(result is CodingActor.FailedToImplementException)
    try {
      interpreter.run(code)
      Assertions.fail<Any>("Expected exception")
    } catch (e: Exception) {
      Assertions.assertTrue(e is CodingActor.FailedToImplementException)
    }
  }

}