package com.simiacryptus.cognotik.interpreter

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

open class ProcessCodeRuntime(
    private val defs: Map<String, Any>,
    val timeoutMinutes: Long = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
    val workingDir: File = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
    val env: Map<String, String>? = defs["env"]?.let { env -> env as Map<String, String> },
    val lang: String = defs["language"]?.toString() ?: "unknown",
    val command: List<String>? = defs["command"]?.let { command ->
        when (command) {
            is String -> command.split(" ")
            is List<*> -> command.map { it.toString() }
            else -> throw IllegalArgumentException("Invalid command: $command")
        }
    },
) : CodeRuntime {

    override fun getSymbols(): Map<String, Any> = /*defs*/ emptyMap()
    final override fun getLanguage(): String = lang

    override fun validate(code: String): Throwable? {
        return null
    }

    override fun run(code: String): Any? {
        val command = command
        if (command == null) {
            throw IllegalArgumentException("No command specified in defs")
        }
        val wrappedCode = wrapCode(code.trim())
        val processBuilder = ProcessBuilder(*command.toTypedArray<String>()).directory(workingDir)
        env?.let { env -> processBuilder.environment().putAll(env) }
        val process = processBuilder.start()

        process.outputStream.write(wrappedCode.toByteArray())
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()

        val waitFor = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
        if (!waitFor) {
            process.destroy()
            throw RuntimeException("Process execution timed out after $timeoutMinutes minutes; output: $output; error: $error")
        } else if (error.isNotEmpty()) {
            return "ERROR:\n\n${error.indent("  ")}\n\nOUTPUT:\n\n${output.indent("  ")}\n"
        } else {
            return output
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(ProcessCodeRuntime::class.java)
    }
}