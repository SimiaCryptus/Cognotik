package com.simiacryptus.cognotik.kotlin

import com.simiacryptus.cognotik.core.actors.CodingActor
import com.simiacryptus.cognotik.interpreter.Interpreter
import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineBase
import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.slf4j.LoggerFactory
import javax.script.Bindings
import javax.script.CompiledScript
import javax.script.ScriptContext
import javax.script.ScriptException
import kotlin.script.experimental.api.enableScriptsInstancesSharing
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptCompilationConfiguration
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEvaluationConfiguration
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext
import kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223ScriptEngineImpl

open class KotlinInterpreter(
  val defs: Map<String, Any> = mapOf(),
) : Interpreter {

  final override fun getLanguage(): String = "Kotlin"
  override fun getSymbols() = defs

  open val scriptEngine: KotlinJsr223JvmScriptEngineBase
    get() = object : KotlinJsr223JvmScriptEngineFactoryBase() {
      override fun getScriptEngine() = KotlinJsr223ScriptEngineImpl(
        this,
        KotlinJsr223DefaultScriptCompilationConfiguration.with {
          classLoader?.also { classLoader ->
            jvm {
              updateClasspath(
                scriptCompilationClasspathFromContext(
                  classLoader = classLoader,
                  wholeClasspath = true,
                  unpackJarCollections = false
                )
              )
            }
          }
        },
        KotlinJsr223DefaultScriptEvaluationConfiguration.with {
          this.enableScriptsInstancesSharing()
        }
      ) {
        ScriptArgsWithTypes(
          arrayOf(),
          arrayOf()
        )
      }.apply {
        getBindings(ScriptContext.ENGINE_SCOPE).putAll(getSymbols())
      }
    }.scriptEngine

  override fun validate(code: String): Throwable? {
    val wrappedCode = wrapCode(code)
    return try {
      scriptEngine.compile(wrappedCode)
      null
    } catch (ex: ScriptException) {
      wrapException(ex, wrappedCode, code)
    } catch (ex: Throwable) {
      CodingActor.FailedToImplementException(
        cause = ex,
        language = "Kotlin",
        code = code,
      )
    }
  }

  override fun run(code: String): Any? {
    val wrappedCode = wrapCode(code)
    log.debug(
      "Running:\n   ${
        wrappedCode.trimIndent().lineSequence()
          .map {
                when {
                  it.isBlank() -> {
                    when {
                      it.length < "  ".length -> "  "
                      else -> it
                    }
                  }

                  else -> "  " + it
                }
          }
          .joinToString("\n")
      }"
    )
    val bindings: Bindings?
    val compile: CompiledScript
    val scriptEngine: KotlinJsr223JvmScriptEngineBase
    try {
      scriptEngine = this.scriptEngine
      compile = scriptEngine.compile(wrappedCode)
      bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE)
      return kotlinx.coroutines.runBlocking { compile.eval(bindings) }
    } catch (ex: ScriptException) {
      throw wrapException(ex, wrappedCode, code)
    } catch (ex: Throwable) {
      throw CodingActor.FailedToImplementException(
        cause = ex,
        language = "Kotlin",
        code = code,
      )
    }
  }

  protected open fun wrapException(
    cause: ScriptException,
    wrappedCode: String,
    code: String
  ): CodingActor.FailedToImplementException {
    var lineNumber = cause.lineNumber
    var column = cause.columnNumber
    if (lineNumber == -1 && column == -1) {
      val match = Regex("\\(.*:(\\d+):(\\d+)\\)").find(cause.message ?: "")
      if (match != null) {
        lineNumber = match.groupValues[1].toInt()
        column = match.groupValues[2].toInt()
      }
    }
    return CodingActor.FailedToImplementException(
      cause = cause,
      message = errorMessage(
        code = wrappedCode,
        line = lineNumber,
        column = column,
        message = cause.message ?: ""
      ),
      language = "Kotlin",
      code = code,
    )
  }

  override fun wrapCode(code: String): String {
    val out = ArrayList<String>()
    val (imports, otherCode) = code.split("\n").partition { it.trim().startsWith("import ") }
    out.addAll(imports)
    out.addAll(otherCode)
    return out.joinToString("\n")
  }


  companion object {
    private val log = LoggerFactory.getLogger(KotlinInterpreter::class.java)

    fun errorMessage(
      code: String,
      line: Int,
      column: Int,
      message: String
    ) = "```text\n$message at line ${line} column ${column}\n  ${if (line < 0) "" else code.split("\n")[line - 1]}\n  ${
      if (column < 0) "" else " ".repeat(
        column - 1
      ) + "^"
    }\n```".trim()

    // TODO: Make this threadlocal with wrapper methods
    var classLoader: ClassLoader? = KotlinInterpreter::class.java.classLoader

  }
}