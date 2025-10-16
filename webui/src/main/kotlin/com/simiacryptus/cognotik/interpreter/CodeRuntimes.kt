package com.simiacryptus.cognotik.interpreter

 import com.fasterxml.jackson.core.JsonParser
 import com.fasterxml.jackson.databind.DeserializationContext
 import com.fasterxml.jackson.databind.annotation.JsonDeserialize
 import com.fasterxml.jackson.databind.annotation.JsonSerialize
 import com.simiacryptus.cognotik.groovy.GroovyCodeRuntime
 import com.simiacryptus.cognotik.kotlin.KotlinCodeRuntime
 import com.simiacryptus.cognotik.util.DynamicEnum
 import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
 import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import java.util.Locale

 @JsonDeserialize(using = CodeRuntimesDeserializer::class)
 @JsonSerialize(using = CodeRuntimesSerializer::class)
 class CodeRuntimes(
    name: String,
    val description: String? = null,
 ) : DynamicEnum<CodeRuntimes>(name) {

    companion object {
        private val runtimeConstructors =
            mutableMapOf<CodeRuntimes, (Map<String, Any>) -> CodeRuntime>()
        private val isWindows = System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")
        private val isMac = System.getProperty("os.name").lowercase(Locale.getDefault()).contains("mac")
        private val isLinux = System.getProperty("os.name").lowercase(Locale.getDefault()).contains("nux")


        val KotlinRuntime = CodeRuntimes(
            "KotlinRuntime",
            "Execute Kotlin code with full JVM access"
        )

        val GroovyRuntime = CodeRuntimes(
            "GroovyRuntime",
            "Execute Groovy code with dynamic scripting capabilities"
        )

        val BashRuntime = CodeRuntimes(
            "BashRuntime",
            "Execute Bash shell scripts (Unix/Linux/Mac)"
        )

        val PowerShellRuntime = CodeRuntimes(
            "PowerShellRuntime",
            "Execute PowerShell scripts (Windows/Cross-platform)"
        )

        val CmdRuntime = CodeRuntimes(
            "CmdRuntime",
            "Execute Windows Command Prompt scripts"
        )

        val PythonRuntime = CodeRuntimes(
            "PythonRuntime",
            "Execute Python scripts"
        )

        val NodeJsRuntime = CodeRuntimes(
            "NodeJsRuntime",
            "Execute Node.js JavaScript code"
        )

        init {
            registerConstructor(KotlinRuntime) { defs ->
                KotlinCodeRuntime(defs)
            }
            registerConstructor(GroovyRuntime) { defs ->
                GroovyCodeRuntime(defs as java.util.Map<String, Object>)
            }
            registerConstructor(BashRuntime) { defs ->
                ProcessCodeRuntime(defs + mapOf(
                    "command" to listOf("bash"),
                    "language" to "bash"
                ))
            }
            registerConstructor(PowerShellRuntime) { defs ->
                val command = if (isWindows) {
                    listOf("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "-")
                } else {
                    listOf("pwsh", "-NoProfile", "-Command", "-")
                }
                ProcessCodeRuntime(defs + mapOf(
                    "command" to command,
                    "language" to "powershell"
                ))
            }
            registerConstructor(CmdRuntime) { defs ->
                ProcessCodeRuntime(defs + mapOf(
                    "command" to listOf("cmd", "/c"),
                    "language" to "cmd"
                ))
            }
            registerConstructor(PythonRuntime) { defs ->
                val pythonCmd = when {
                    isWindows -> "python"
                    else -> "python3"
                }
                ProcessCodeRuntime(defs + mapOf(
                    "command" to listOf(pythonCmd),
                    "language" to "python"
                ))
            }
            registerConstructor(NodeJsRuntime) { defs ->
                ProcessCodeRuntime(defs + mapOf(
                    "command" to listOf("node"),
                    "language" to "javascript"
                ))
            }
        }

        fun registerConstructor(
            runtime: CodeRuntimes,
            constructor: (Map<String, Any>) -> CodeRuntime
        ) {
            runtimeConstructors[runtime] = constructor
            register(runtime)
        }

        fun values() = values(CodeRuntimes::class.java)

        fun getRuntime(
            runtimeType: CodeRuntimes,
            defs: Map<String, Any> = mapOf()
        ): CodeRuntime {
            val constructor = runtimeConstructors[runtimeType]
            if (constructor == null) {
                throw RuntimeException("Unknown runtime type: ${runtimeType.name}")
            }
            return constructor(defs)
        }

        fun getRuntime(
            runtimeName: String,
            defs: Map<String, Any> = mapOf()
        ) = getRuntime(valueOf(runtimeName), defs)

        fun valueOf(name: String): CodeRuntimes = valueOf(CodeRuntimes::class.java, name)
        private fun register(runtime: CodeRuntimes) = register(CodeRuntimes::class.java, runtime)
    }
}

class CodeRuntimesSerializer : DynamicEnumSerializer<CodeRuntimes>(CodeRuntimes::class.java)
class CodeRuntimesDeserializer : DynamicEnumDeserializer<CodeRuntimes>(CodeRuntimes::class.java) {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): CodeRuntimes {
        return super.deserialize(p, ctxt)
    }
}