package com.simiacryptus.cognotik.groovy

import com.simiacryptus.cognotik.interpreter.Interpreter
import groovy.lang.GroovyShell
import groovy.lang.Script
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration

open class GroovyInterpreter(private val defs: java.util.Map<String, Object>) : Interpreter {

    private val shell: GroovyShell

    init {
        val compilerConfiguration = CompilerConfiguration()
        shell = GroovyShell(compilerConfiguration)
        defs.forEach { key, value ->
            shell.setVariable(key, value)
        }
    }

    override fun getLanguage(): String {
        return "groovy"
    }

    override fun getSymbols() = defs as Map<String, Any>

    override fun run(code: String): Any? {
        val wrapExecution = wrapExecution {
            try {
                val script: Script = shell.parse(wrapCode(code))
                script.run()
            } catch (e: CompilationFailedException) {
                throw e
            }
        }
        return wrapExecution
    }

    override fun validate(code: String): Exception? {
        shell.parse(wrapCode(code))
        return null
    }
}

