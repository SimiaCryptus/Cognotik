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

        init {
            registerConstructor(KotlinRuntime) { defs -> KotlinCodeRuntime(defs) }
            registerConstructor(GroovyRuntime) { defs -> GroovyCodeRuntime(defs as java.util.Map<String, Object>) }
            registerConstructor(BashRuntime) { defs -> BashCodeRuntime(defs) }
            registerConstructor(PowerShellRuntime) { defs -> PowerShellCodeRuntime(defs) }
            registerConstructor(CmdRuntime) { defs -> CmdCodeRuntime(defs) }
            registerConstructor(PythonRuntime) { defs -> PythonCodeRuntime(defs) }
            registerConstructor(NodeJsRuntime) { defs -> NodeJsCodeRuntime(defs) }
        }

        fun registerConstructor(
            runtime: CodeRuntimes, constructor: (Map<String, Any>) -> CodeRuntime
        ) {
            runtimeConstructors[runtime] = constructor
            register(runtime)
        }

        fun values() = values(CodeRuntimes::class.java)

        fun getRuntime(
            runtimeType: CodeRuntimes, defs: Map<String, Any> = mapOf()
        ): CodeRuntime {
            val constructor = runtimeConstructors[runtimeType]
            if (constructor == null) {
                throw RuntimeException("Unknown runtime type: ${runtimeType.name}")
            }
            return constructor(defs)
        }

        fun getRuntime(
            runtimeName: String, defs: Map<String, Any> = mapOf()
        ) = getRuntime(valueOf(runtimeName), defs)

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


private fun String.resolveTool() = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools
    .find { it.provider?.getExecutables()?.contains(this) == true }?.resolve(this) ?: this

class BashCodeRuntime(defs: Map<String, Any> = emptyMap()) : ProcessCodeRuntime(
    defs + mapOf(
        "command" to listOf("bash"), "language" to "bash"
    )
)

class PowerShellCodeRuntime(defs: Map<String, Any> = emptyMap()) : ProcessCodeRuntime(
    defs + mapOf(
        "command" to if (isWindows) {
            listOf<String>("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "-")
        } else {
            listOf<String>("pwsh", "-NoProfile", "-Command", "-")
        }, "language" to "powershell"
    )
)

class CmdCodeRuntime(defs: Map<String, Any> = emptyMap()) : ProcessCodeRuntime(
    defs + mapOf(
        "command" to listOf("cmd", "/c"), "language" to "cmd"
    )
)

class PythonCodeRuntime(defs: Map<String, Any> = emptyMap()) : ProcessCodeRuntime(
    defs + mapOf(
        "command" to listOf(
            when {
                isWindows -> "python"
                else -> "python3"
            }.resolveTool()
        ), "language" to "python"
    )
)

class NodeJsCodeRuntime(defs: Map<String, Any> = emptyMap()) : ProcessCodeRuntime(
    defs + mapOf(
        "command" to listOf("node".resolveTool()), "language" to "javascript"
    )
)
