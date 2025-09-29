package com.simiacryptus.cognotik.interpreter

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.groovy.GroovyCodeRuntime
import com.simiacryptus.cognotik.kotlin.KotlinCodeRuntime
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
        private val runtimeConstructors =
            mutableMapOf<CodeRuntimes, (Map<String, Any>) -> CodeRuntime>()

        val KotlinRuntime = CodeRuntimes(
            "KotlinRuntime",
            "Execute Kotlin code with full JVM access"
        )

        val GroovyRuntime = CodeRuntimes(
            "GroovyRuntime",
            "Execute Groovy code with dynamic scripting capabilities"
        )

        val ProcessRuntime = CodeRuntimes(
            "ProcessRuntime",
            "Execute shell commands and scripts in separate processes"
        )

        init {
            registerConstructor(KotlinRuntime) { defs ->
                KotlinCodeRuntime(defs)
            }
            registerConstructor(GroovyRuntime) { defs ->
                GroovyCodeRuntime(defs as java.util.Map<String, Object>)
            }
            registerConstructor(ProcessRuntime) { defs ->
                ProcessCodeRuntime(defs)
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
                ?: throw RuntimeException("Unknown runtime type: ${runtimeType.name}")
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
class CodeRuntimesDeserializer : DynamicEnumDeserializer<CodeRuntimes>(CodeRuntimes::class.java)