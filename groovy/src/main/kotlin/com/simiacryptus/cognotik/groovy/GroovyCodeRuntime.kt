package com.simiacryptus.cognotik.groovy

import com.simiacryptus.cognotik.interpreter.CodeRuntime
import groovy.lang.GroovyShell
import groovy.lang.Script
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration

open class GroovyCodeRuntime(private val defs: java.util.Map<String, Object>) : CodeRuntime {

    private val shell: GroovyShell
    private val console = StringBuilder()

    init {
        try {
            val compilerConfiguration = CompilerConfiguration()
            shell = GroovyShell(compilerConfiguration)
            defs.forEach { key, value ->
                shell.setVariable(key, value)
            }
            shell.setVariable("out", java.io.PrintWriter(object : java.io.Writer() {
                override fun write(cbuf: CharArray, off: Int, len: Int) {
                    console.append(cbuf, off, len)
                }
                override fun flush() {}
                override fun close() {}
            }))
        } catch (e: Throwable) {
            log.error("Error initializing Groovy shell", e)
            throw RuntimeException("Failed to initialize Groovy shell", e)
        }
    }

    override val language : String get() {
        return "groovy"
    }

    override val symbols = defs as Map<String, Any>

    override fun run(code: String): Any? {
        val wrapExecution = wrapExecution {
            try {
                val scriptText = wrapCode(code)
                val script: Script = shell.parse(scriptText)
                val run = script.run()
                if (console.isNotEmpty()) "$run\nConsole Output:\n$console" else run
            } catch (e: CompilationFailedException) {
                throw e
            }
        }
        return wrapExecution
    }

    override fun validate(code: String): Exception? {
        return try {
            wrapExecution {
                shell.parse(wrapCode(code))
            }
            null
        } catch (e: Exception) {
            e
        }
    }

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(GroovyCodeRuntime::class.java)
    }
}