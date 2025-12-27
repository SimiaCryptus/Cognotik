package com.simiacryptus.cognotik.interpreter

import com.simiacryptus.cognotik.util.EnabledStrategy

interface CodeRuntime : EnabledStrategy {

    fun getLanguage(): String
    fun getSymbols(): Map<String, Any>
    fun run(code: String): Any?
    fun validate(code: String): Throwable?

    fun wrapCode(code: String): String = code
    fun <T : Any> wrapExecution(fn: java.util.function.Supplier<T?>): T? = fn.get()

}