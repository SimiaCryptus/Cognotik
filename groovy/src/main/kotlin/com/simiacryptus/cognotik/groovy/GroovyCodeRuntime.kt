package com.simiacryptus.cognotik.groovy

import com.simiacryptus.cognotik.interpreter.CodeRuntime
import groovy.lang.GroovyShell
import groovy.lang.Script
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration

open class GroovyCodeRuntime(private val defs: java.util.Map<String, Object>) : CodeRuntime {

    private val shell: GroovyShell

    init {
        try {
            val compilerConfiguration = CompilerConfiguration()
            shell = GroovyShell(compilerConfiguration)
            defs.forEach { key, value ->
                shell.setVariable(key, value)
            }
        } catch (e: Throwable) {
            log.error("Error initializing Groovy shell", e)
            throw RuntimeException("Failed to initialize Groovy shell", e)
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

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(GroovyCodeRuntime::class.java)
    }
}

