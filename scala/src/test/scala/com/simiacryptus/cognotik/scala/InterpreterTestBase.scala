package com.simiacryptus.cognotik.scala

import com.simiacryptus.cognotik.interpreter.Interpreter
import org.junit.jupiter.api.Assertions.{assertEquals, assertNull, assertThrows}
import org.junit.jupiter.api.Test

import java.util
import com.simiacryptus.cognotik.scala.InterpreterTestBase.FooBar
import scala.jdk.CollectionConverters._

abstract class InterpreterTestBase {

  @Test
  def testRunWithValidCode(): Unit = {
    val interpreter = newInterpreter(Map.empty[String, Any].asJava)
    val result = interpreter.run("2 + 2")
    assertEquals(4, result)
  }

  @Test
  def testRunWithInvalidCode(): Unit = {
    val interpreter = newInterpreter(Map.empty[String, Any].asJava)
    assertThrows(classOf[Exception], () => interpreter.run("2 +"))
  }

  @Test
  def testValidateWithValidCode(): Unit = {
    val interpreter = newInterpreter(Map.empty[String, Any].asJava)
    val result = interpreter.validate("2 + 2")
    assertNull(result)
  }

  @Test
  def testValidateWithInvalidCode(): Unit = {
    val interpreter = newInterpreter(Map.empty[String, Any].asJava)
    assertThrows(classOf[Exception], () => {
      val validationResult = interpreter.validate("2 +")
      if (validationResult != null) throw validationResult
    })
  }

  @Test
  def testRunWithVariables(): Unit = {
    val interpreter = newInterpreter(Map("x" -> 2, "y" -> 3).asInstanceOf[Map[String, Any]].asJava)
    val result = interpreter.run("x * y")
    assertEquals(6, result)
  }

  @Test
  def testValidateWithVariables(): Unit = {
    val interpreter = newInterpreter(Map("x" -> 2, "y" -> 3).asInstanceOf[Map[String, Any]].asJava)
    val result = interpreter.validate("x * y")
    assertNull(result)
  }


  @Test
  def testRunWithToolAny(): Unit = {
    val interpreter = newInterpreter(Map("tool" -> new FooBar()).asInstanceOf[Map[String, Any]].asJava)
    val result = interpreter.run("tool.bar()")
    assertEquals("Foo says Hello World", result)
  }

  @Test
  def testValidateWithToolAny(): Unit = {
    val interpreter = newInterpreter(Map("tool" -> new FooBar()).asInstanceOf[Map[String, Any]].asJava)
    val result = interpreter.validate("tool.bar()")
    assertNull(result)
  }

  @Test
  def testRunWithToolAnyAndInvalidCode(): Unit = {
    val interpreter = newInterpreter(Map("tool" -> new FooBar()).asInstanceOf[Map[String, Any]].asJava)
    assertThrows(classOf[Exception], () => interpreter.run("tool.baz()"))
  }

  @Test
  def testValidateWithToolAnyAndInvalidCode(): Unit = {
    val interpreter = newInterpreter(Map("tool" -> new FooBar()).asInstanceOf[Map[String, Any]].asJava)
    assertThrows(classOf[Exception], () => {
      val validationResult = interpreter.validate("tool.baz()")
      if (validationResult != null) throw validationResult
    })
  }

  @Test
  def testValidateWithUndefinedVariable(): Unit = {
    val interpreter = newInterpreter(Map.empty[String, Any].asJava)
    assertThrows(classOf[Exception], () => {
      val validationResult = interpreter.validate("x * y")
      if (validationResult != null) throw validationResult
    })
  }

  // Note: The input map type is java.util.Map[String, _] to match the existing ScalaLocalInterpreterTest
  // but the internal logic uses Scala maps converted to Java maps for testing convenience.
  // The specific implementation (ScalaLocalInterpreterTest) handles the cast to Map[String, Object].
  def newInterpreter(map: util.Map[String, _]): Interpreter
}
object InterpreterTestBase {
  class FooBar {
    def bar(): String = "Foo says Hello World"
  }
}