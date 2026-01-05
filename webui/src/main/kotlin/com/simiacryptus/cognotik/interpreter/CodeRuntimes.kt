package com.simiacryptus.cognotik.interpreter

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.groovy.GroovyCodeRuntime
import com.simiacryptus.cognotik.kotlin.KotlinCodeRuntime
import com.simiacryptus.cognotik.plan.PlanUtil.isWindows
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import java.io.File

@JsonDeserialize(using = CodeRuntimesDeserializer::class)
@JsonSerialize(using = CodeRuntimesSerializer::class)
class CodeRuntimes(
    name: String,
    val description: String? = null,
) : DynamicEnum<CodeRuntimes>(name) {


    companion object {
        private val runtimeConstructors = mutableMapOf<CodeRuntimes, (Map<String, Any>) -> CodeRuntime>()

        val KotlinRuntime = CodeRuntimes(
            "KotlinRuntime", "Execute Kotlin code with full JVM access"
        )

        val GroovyRuntime = CodeRuntimes(
            "GroovyRuntime", "Execute Groovy code with dynamic scripting capabilities"
        )

        val BashRuntime = CodeRuntimes(
            "BashRuntime", "Execute Bash shell scripts (Unix/Linux/Mac)"
        )

        val PowerShellRuntime = CodeRuntimes(
            "PowerShellRuntime", "Execute PowerShell scripts (Windows/Cross-platform)"
        )

        val CmdRuntime = CodeRuntimes(
            "CmdRuntime", "Execute Windows Command Prompt scripts"
        )

        val PythonRuntime = CodeRuntimes(
            "PythonRuntime", "Execute Python scripts"
        )

        val NodeJsRuntime = CodeRuntimes(
            "NodeJsRuntime", "Execute Node.js JavaScript code"
        )
        val RubyRuntime = CodeRuntimes(
            "RubyRuntime", "Execute Ruby scripts"
        )
        val PerlRuntime = CodeRuntimes(
            "PerlRuntime", "Execute Perl scripts"
        )
        val RRuntime = CodeRuntimes(
            "RRuntime", "Execute R scripts"
        )
        val PhpRuntime = CodeRuntimes(
            "PhpRuntime", "Execute PHP scripts"
        )
        val LuaRuntime = CodeRuntimes(
            "LuaRuntime", "Execute Lua scripts"
        )
        val GoRuntime = CodeRuntimes(
            "GoRuntime", "Execute Go code"
        )
        val RustRuntime = CodeRuntimes(
            "RustRuntime", "Execute Rust code"
        )
        val ScalaRuntime = CodeRuntimes(
            "ScalaRuntime", "Execute Scala scripts"
        )


        init {
            registerConstructor(KotlinRuntime) { defs -> KotlinCodeRuntime(defs) }
            registerConstructor(GroovyRuntime) { defs -> GroovyCodeRuntime(defs) }
            registerConstructor(BashRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "bash",
                    command = listOf<String>("bash")
                )
            }
            registerConstructor(PowerShellRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "powershell",
                    command = if (isWindows) {
                        listOf<String>("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "-")
                    } else {
                        listOf<String>("pwsh", "-NoProfile", "-Command", "-")
                    }
                )
            }
            registerConstructor(CmdRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "cmd",
                    command = listOf<String>("cmd", "/c")
                )
            }
            registerConstructor(PythonRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "python",
                    command = listOf<String>(
                        when {
                            isWindows -> "python"
                            else -> "python3"
                        }.resolveTool()
                    )
                )
            }
            registerConstructor(NodeJsRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "javascript",
                    command = listOf<String>("node".resolveTool())
                )
            }
            registerConstructor(RubyRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "ruby",
                    command = listOf<String>("ruby".resolveTool())
                )
            }
            registerConstructor(PerlRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "perl",
                    command = listOf<String>("perl".resolveTool())
                )
            }
            registerConstructor(RRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "r",
                    command = listOf<String>("Rscript".resolveTool())
                )
            }
            registerConstructor(PhpRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "php",
                    command = listOf<String>("php".resolveTool())
                )
            }
            registerConstructor(LuaRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "lua",
                    command = listOf<String>("lua".resolveTool())
                )
            }
            registerConstructor(GoRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "go",
                    command = listOf<String>("go".resolveTool(), "run")
                )
            }
            registerConstructor(RustRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "rust",
                    command = listOf<String>("rust-script".resolveTool())
                )
            }
            registerConstructor(ScalaRuntime) { defs ->
                ProcessCodeRuntime(
                    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
                    workingDir = defs["workingDir"]?.toString()?.let<String, File> { File(it) } ?: File("."),
                    env = defs["env"]?.let<Any, Map<String, String>> { it as Map<String, String> },
                    lang = "scala",
                    command = listOf<String>("scala".resolveTool())
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
    ): CodeRuntimes {
        return super.deserialize(p, ctxt)
    }
}


private fun String.resolveTool() =
    ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools
        .find { it.provider?.getExecutables()?.contains(this) == true }?.resolve(this) ?: this

