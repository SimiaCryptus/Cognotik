@file:Suppress("UNCHECKED_CAST")

package com.simiacryptus.cognotik.interpreter

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.groovy.GroovyCodeRuntime
import com.simiacryptus.cognotik.kotlin.KotlinCodeRuntime
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import com.simiacryptus.cognotik.util.resolveTool
import java.io.File
import java.util.Locale.getDefault

@JsonDeserialize(using = CodeRuntimesDeserializer::class)
@JsonSerialize(using = CodeRuntimesSerializer::class)
class CodeRuntimes(
  name: String,
  val description: String? = null,
  val extension: String? = null
) : DynamicEnum<CodeRuntimes>(name) {


  companion object {
    private val runtimeConstructors = mutableMapOf<CodeRuntimes, (Map<String, Any>) -> CodeRuntime>()

    val KotlinRuntime = CodeRuntimes(
      "KotlinRuntime", "Execute Kotlin code with full JVM access", "kts"
    )

    val GroovyRuntime = CodeRuntimes(
      "GroovyRuntime", "Execute Groovy code with dynamic scripting capabilities", "groovy"
    )

    val BashRuntime = CodeRuntimes(
      "BashRuntime", "Execute Bash shell scripts (Unix/Linux/Mac)", "sh"
    )

    val PowerShellRuntime = CodeRuntimes(
      "PowerShellRuntime", "Execute PowerShell scripts (Windows/Cross-platform)", "ps1"
    )

    val CmdRuntime = CodeRuntimes(
      "CmdRuntime", "Execute Windows Command Prompt scripts", "bat"
    )

    val PythonRuntime = CodeRuntimes(
      "PythonRuntime", "Execute Python scripts", "py"
    )

    val NodeJsRuntime = CodeRuntimes(
      "NodeJsRuntime", "Execute Node.js JavaScript code", "js"
    )
    val RubyRuntime = CodeRuntimes(
      "RubyRuntime", "Execute Ruby scripts", "rb"
    )
    val PerlRuntime = CodeRuntimes(
      "PerlRuntime", "Execute Perl scripts", "pl"
    )
    val RRuntime = CodeRuntimes(
      "RRuntime", "Execute R scripts", "R"
    )
    val PhpRuntime = CodeRuntimes(
      "PhpRuntime", "Execute PHP scripts", "php"
    )
    val LuaRuntime = CodeRuntimes(
      "LuaRuntime", "Execute Lua scripts", "lua"
    )
    val GoRuntime = CodeRuntimes(
      "GoRuntime", "Execute Go code", "go"
    )
    val RustRuntime = CodeRuntimes(
      "RustRuntime", "Execute Rust code", "rs"
    )
    val ScalaRuntime = CodeRuntimes(
      "ScalaRuntime", "Execute Scala scripts", "scala"
    )


    init {
      registerConstructor(KotlinRuntime) { defs -> KotlinCodeRuntime(defs) }
      registerConstructor(GroovyRuntime) { defs -> GroovyCodeRuntime(defs) }
      registerConstructor(BashRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "bash",
          commandResolver = { listOf("bash").map { it.resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())!! } }
        )
      }
      registerConstructor(PowerShellRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "powershell",
          commandResolver = {
            if (System.getProperty("os.name").lowercase(getDefault()).contains("windows")) {
              listOf("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "-")
            } else {
              listOf("pwsh", "-NoProfile", "-Command", "-")
            }.map { it.resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())!! }
          }
        )
      }
      registerConstructor(CmdRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "cmd",
          commandResolver = { listOf("cmd", "/c").map { it.resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())!! } }
        )
      }
      registerConstructor(PythonRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "python",
          commandResolver = {
            val string = when {
              System.getProperty("os.name").lowercase(getDefault()).contains("windows") -> "python"
              else -> "python3"
            }
            listOf(
              string.resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())
                ?: throw IllegalArgumentException("Executable '${string}' not found relative to root '${ApplicationServices.fileApplicationServices().rootDir}' or on system PATH")
            )
          }
        )
      }
      registerConstructor(NodeJsRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "javascript",
          commandResolver = { listOf(
            "node".resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())
              ?: throw IllegalArgumentException("Executable '${"node"}' not found relative to root '${ApplicationServices.fileApplicationServices().rootDir}' or on system PATH")
          ) }
        )
      }
      registerConstructor(RubyRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "ruby",
          commandResolver = { listOf(
            "ruby".resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())
              ?: throw IllegalArgumentException("Executable '${"ruby"}' not found relative to root '${ApplicationServices.fileApplicationServices().rootDir}' or on system PATH")
          ) }
        )
      }
      registerConstructor(PerlRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "perl",
          commandResolver = { listOf(
            "perl".resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())
              ?: throw IllegalArgumentException("Executable '${"perl"}' not found relative to root '${ApplicationServices.fileApplicationServices().rootDir}' or on system PATH")
          ) }
        )
      }
      registerConstructor(RRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "r",
          commandResolver = { listOf(
            "Rscript".resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())
              ?: throw IllegalArgumentException("Executable '${"Rscript"}' not found relative to root '${ApplicationServices.fileApplicationServices().rootDir}' or on system PATH")
          ) }
        )
      }
      registerConstructor(PhpRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "php",
          commandResolver = { listOf(
            "php".resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())
              ?: throw IllegalArgumentException("Executable '${"php"}' not found relative to root '${ApplicationServices.fileApplicationServices().rootDir}' or on system PATH")
          ) }
        )
      }
      registerConstructor(LuaRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "lua",
          commandResolver = { listOf(
            "lua".resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())
              ?: throw IllegalArgumentException("Executable '${"lua"}' not found relative to root '${ApplicationServices.fileApplicationServices().rootDir}' or on system PATH")
          ) }
        )
      }
      registerConstructor(GoRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "go",
          commandResolver = { listOf("go", "run").map { it.resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())!! } }
        )
      }
      registerConstructor(RustRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "rust",
          commandResolver = { listOf(
            "rust-script"
          ).map { it.resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())!! } }
        )
      }
      registerConstructor(ScalaRuntime) { defs ->
        ProcessCodeRuntime(
          timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
          workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
          env = defs["env"]?.let { it as Map<String, String> },
          lang = "scala",
          commandResolver = { listOf(
            "scala".resolveTool(ApplicationServicesConfig.dataStorageRoot.toPath())
              ?: throw IllegalArgumentException("Executable '${"scala"}' not found relative to root '${ApplicationServices.fileApplicationServices().rootDir}' or on system PATH")
          ) }
        )
      }
    }

    fun registerConstructor(
      runtime: CodeRuntimes, constructor: (Map<String, Any>) -> CodeRuntime
    ) {
      runtimeConstructors[runtime] = constructor
      register(runtime)
    }

    fun values() = values(CodeRuntimes::class.java)

    fun getRuntime(
      runtimeType: CodeRuntimes, params: Map<String, Any> = mapOf()
    ): CodeRuntime {
      val constructor = runtimeConstructors[runtimeType]
      if (constructor == null) {
        throw RuntimeException("Unknown runtime type: ${runtimeType.name}")
      }
      return constructor(params)
    }

    fun valueOf(name: String): CodeRuntimes = valueOf(CodeRuntimes::class.java, name)
    private fun register(runtime: CodeRuntimes) = register(CodeRuntimes::class.java, runtime)
  }
}

class CodeRuntimesSerializer : DynamicEnumSerializer<CodeRuntimes>(CodeRuntimes::class.java)
class CodeRuntimesDeserializer : DynamicEnumDeserializer<CodeRuntimes>(CodeRuntimes::class.java) {
  override fun deserialize(
    p: JsonParser, ctxt: DeserializationContext
  ): CodeRuntimes? {
    return super.deserialize(p, ctxt)
  }
}


