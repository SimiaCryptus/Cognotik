@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.groovy.GroovyCodeRuntime
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class GroovyInterpreterTest : InterpreterTestBase() {

    override fun newInterpreter(map: Map<String, Any>) = GroovyCodeRuntime(map)

//    @Test
//    fun `test run with groovy println`() {
//        val interpreter = newInterpreter(mapOf())
//        val result = interpreter.run("""println("Hello World")""")
//        Assertions.assertEquals(null, result)
//    }

    @Test
    fun `test validate with groovy println`() {
        val interpreter = newInterpreter(mapOf())
        val result = interpreter.validate("""println("Hello World")""")
        Assertions.assertEquals(null, result)
    }

    @Test
    fun `test validate with invalid function`() {
        val interpreter = newInterpreter(mapOf())

        @Language("groovy") val code = """
            fun invalidFunction() {
                sfjogsvrnfo~ffjj (
            }
        """.trimIndent()

        val result = interpreter.validate(code)
        Assertions.assertInstanceOf(org.codehaus.groovy.control.MultipleCompilationErrorsException::class.java, result)
        try {
            interpreter.run(code, ApplicationServicesConfig.defaultUser)
            Assertions.fail<Any>("Expected exception")
        } catch (e: Exception) {
            Assertions.assertTrue(e is org.codehaus.groovy.control.MultipleCompilationErrorsException)
        }
    }

}