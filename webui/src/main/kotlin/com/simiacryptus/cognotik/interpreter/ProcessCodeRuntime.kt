package com.simiacryptus.cognotik.interpreter

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

open class ProcessCodeRuntime(
  val timeoutMinutes: Long,
  val workingDir: File,
  val env: Map<String, String>?,
  val lang: String,
  val commandResolver: (User) -> List<String>?,
) : CodeRuntime {

  override val symbols: Map<String, Any> = emptyMap()
  final override val language: String = lang

  override fun validate(code: String): Throwable? {
    return null
  }

  override fun run(code: String, user: User): Any? {
    val command = commandResolver(user)
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
    return when {
      !waitFor -> {
        process.destroy()
        throw RuntimeException("Process execution timed out after $timeoutMinutes minutes; output: $output; error: $error")
      }

      error.isNotEmpty() -> {
        "ERROR:\n\n${error.indent("  ")}\n\nOUTPUT:\n\n${output.indent("  ")}\n"
      }

      else -> {
        output
      }
    }
  }

  companion object {
    val log = LoggerFactory.getLogger(ProcessCodeRuntime::class.java)
  }
}