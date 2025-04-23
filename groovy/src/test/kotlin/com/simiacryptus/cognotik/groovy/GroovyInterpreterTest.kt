@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.simiacryptus.cognotik.groovy

import com.simiacryptus.cognotik.interpreter.InterpreterTestBase

class GroovyInterpreterTest : InterpreterTestBase() {
  override fun newInterpreter(map: Map<String, Any>) =
    GroovyInterpreter(map.map { it.key to it.value as Object }.toMap().toJavaMap())

}

@Suppress("UNCHECKED_CAST")
private fun <K, V> Map<K, V>.toJavaMap() = HashMap(this) as java.util.Map<K, V>

