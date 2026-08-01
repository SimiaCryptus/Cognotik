package com.simiacryptus.cognotik.interpreter

import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.EnabledStrategy

interface CodeRuntime : EnabledStrategy {
  val language: String
  val symbols: Map<String, Any>
  fun run(code: String, user: User): Any?
  fun validate(code: String): Throwable?
  fun wrapCode(code: String): String = code
  fun <T : Any> wrapExecution(fn: java.util.function.Supplier<T?>): T? = fn.get()

}