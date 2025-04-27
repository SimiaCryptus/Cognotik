package com.simiacryptus.cognotik.interpreter

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

abstract class InterpreterTestBase {

    @Test
    fun `test run with valid code`() {
        val interpreter = newInterpreter(mapOf())
        val result = interpreter.run("2 + 2")
        Assertions.assertEquals(4, result)
    }

    @Test
    fun `test run with invalid code`() {
        val interpreter = newInterpreter(mapOf())
        assertThrows<Exception> { interpreter.run("2 +") }
    }

    @Test
    fun `test validate with valid code`() {
        val interpreter = newInterpreter(mapOf())
        val result = interpreter.validate("2 + 2")
        Assertions.assertEquals(null, result)
    }

    @Test
    fun `test validate with invalid code`() {
        val interpreter = newInterpreter(mapOf())
        assertThrows<Exception> { with(interpreter.validate("2 +")) { throw this!! } }
    }

    @Test
    open fun `test run with variables`() {
        val interpreter = newInterpreter(mapOf("x" to (2 as Any), "y" to (3 as Any)))
        val result = interpreter.run("x * y")
        Assertions.assertEquals(6, result)
    }

    @Test
    open fun `test validate with variables`() {
        val interpreter = newInterpreter(mapOf("x" to (2 as Any), "y" to (3 as Any)))
        val result = interpreter.validate("x * y")
        Assertions.assertEquals(null, result)
    }

    class FooBar {
        fun bar() = "Foo says Hello World"
    }

    @Test
    fun `test run with tool Any`() {
        val interpreter = newInterpreter(mapOf("tool" to (FooBar() as Any)))
        val result = interpreter.run("tool.bar()")
        Assertions.assertEquals("Foo says Hello World", result)
    }

    @Test
    fun `test validate with tool Any`() {
        val interpreter = newInterpreter(mapOf("tool" to (FooBar() as Any)))
        val result = interpreter.validate("tool.bar()")
        Assertions.assertEquals(null, result)
    }

    @Test
    fun `test run with tool Any and invalid code`() {
        val interpreter = newInterpreter(mapOf("tool" to (FooBar() as Any)))
        assertThrows<Exception> { interpreter.run("tool.baz()") }
    }

    @Test
    open fun `test validate with tool Any and invalid code`() {
        val interpreter = newInterpreter(mapOf("tool" to (FooBar() as Any)))
        assertThrows<Exception> { with(interpreter.validate("tool.baz()")) { throw this!! } }
    }

    @Test
    open fun `test validate with undefined variable`() {
        val interpreter = newInterpreter(mapOf())
        assertThrows<Exception> { with(interpreter.validate("x * y")) { throw this!! } }
    }

    abstract fun newInterpreter(map: Map<String, Any>): Interpreter
}