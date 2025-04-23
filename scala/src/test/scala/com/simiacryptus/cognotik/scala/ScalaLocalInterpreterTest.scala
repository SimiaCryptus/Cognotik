package com.simiacryptus.cognotik.scala

import com.simiacryptus.cognotik.interpreter.{Interpreter, InterpreterTestBase}

import java.util


class ScalaLocalInterpreterTest extends InterpreterTestBase {
//  override def newInterpreter(map: util.Map[String, AnyRef]): Interpreter = {
//    new ScalaLocalInterpreter(map)
//  }

  override def newInterpreter(map: util.Map[String, _]): Interpreter = new ScalaLocalInterpreter(map.asInstanceOf[util.Map[String, Object]])
}
