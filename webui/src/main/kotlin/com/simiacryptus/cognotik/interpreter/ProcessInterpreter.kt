package com.simiacryptus.cognotik.interpreter

import java.util.concurrent.TimeUnit

open class ProcessInterpreter(
    private val defs: Map<String, Any> = mapOf(),
) : Interpreter {

    val command: List<String>
        get() = defs["command"]?.let { command ->
            when (command) {
                is String -> command.split(" ")
                is List<*> -> command.map { it.toString() }
                else -> throw IllegalArgumentException("Invalid command: $command")
            }
        } ?: listOf("bash")

    final override fun getLanguage(): String = defs["language"]?.toString() ?: "bash"
    override fun getSymbols() = defs
    // Default timeout in minutes, can be overridden via defs
    private val timeoutMinutes: Long
        get() = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L

    override fun validate(code: String): Throwable? {

        return null
    }

    override fun run(code: String): Any? {
        val wrappedCode = wrapCode(code.trim())
        val cmd = command.toTypedArray()
        val cwd = defs["workingDir"]?.toString()?.let { java.io.File(it) } ?: java.io.File(".")
        val processBuilder = ProcessBuilder(*cmd).directory(cwd)
        defs["env"]?.let { env -> processBuilder.environment().putAll((env as Map<String, String>)) }
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

            return "ERROR:\n```text\n$error\n```\n\nOUTPUT:\n```text\n$output\n```"
        } else {
            return output
        }
    }

    companion object
}