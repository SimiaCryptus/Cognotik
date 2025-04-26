package com.simiacryptus.jopenai.describe

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.common.reflect.TypeToken
import com.simiacryptus.jopenai.describe.DescriptorUtil.componentType
import com.simiacryptus.jopenai.describe.DescriptorUtil.getAllAnnotations
import com.simiacryptus.jopenai.describe.DescriptorUtil.isArray
import com.simiacryptus.jopenai.describe.DescriptorUtil.resolveGenericType
import com.simiacryptus.util.DynamicEnum
import org.slf4j.LoggerFactory
import java.lang.reflect.*
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

open class YamlDescriber : TypeDescriber() {
    companion object {
        val log = LoggerFactory.getLogger(YamlDescriber::class.java)
    }

    init {
        log.info("YamlDescriber initialized with markupLanguage: $markupLanguage")
    }


    override val markupLanguage: String
        get() = "yaml"

    override fun describe(
        rawType: Class<in Nothing>,
        stackMax: Int,
        describedTypes: MutableSet<String>
    ): String {
        if (!describedTypes.add(rawType.name) && rawType.simpleName.lowercase() !in primitives) {
            return "..."
        } else if (rawType.simpleName.lowercase() in primitives) {
            return "type: ${rawType.simpleName.lowercase()}"
        }
        if (isAbbreviated(rawType) || stackMax <= 0) return "\ntype: object\nclass: ${rawType.name}".trim()
        if (rawType.isEnum || DynamicEnum::class.java.isAssignableFrom(rawType)) {
            return """
          type: enumeration
          values:
          """.trimIndent() + getEnumValues(rawType).joinToString("\n") { "  - $it" }
        }
        val propertiesYaml = if (rawType.isKotlinClass()) {
            rawType.kotlin.memberProperties.filter { it.visibility == KVisibility.PUBLIC }.map {
                val description =
                    getAllAnnotations(rawType, it).filterIsInstance<Description>().firstOrNull()
                val toYaml = toYaml(it.returnType.javaType, stackMax - 1, describedTypes)
                if (description != null) {
                    "${it.name}:\n  description: \"${
                        description.value.trim().replace("\"", "\\\"")
                    }\"\n  ${
                        toYaml.lineSequence()
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
                } else {
                    "${it.name}:\n  ${
                        toYaml.lineSequence()
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
                }
            }.toTypedArray()
        } else {
            rawType.declaredFields.filter { Modifier.isPublic(it.modifiers) }.map {
                val description =
                    it.annotations.find { x -> x is Description } as? Description
                return@map if (description != null) "${it.name}:\n  description: ${description.value.trim()}\n  ${
                    toYaml(
                        it.genericType,
                        stackMax - 1,
                        describedTypes
                    ).lineSequence()
                        .map<String, String> {
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
                        .joinToString<String>("\n")
                }"
                else
                    "${it.name}:\n  ${
                        toYaml(it.genericType, stackMax - 1, describedTypes).lineSequence()
                            .map<String, String> {
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
                            .joinToString<String>("\n")
                    }"
            }.toTypedArray()
        }
        val methodsYaml = (if (rawType.isKotlinClass()) {
            rawType.kotlin.functions.filter {
                it.visibility == KVisibility.PUBLIC
                        && !methodBlacklist.contains(it.name)
                        && !it.isOperator && !it.isInfix && !it.isAbstract
            }.map {
                """
${it.name}:
  ${
                    describe(it, rawType.kotlin, stackMax - 1, false, describedTypes).lineSequence()
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
                }
                """.trim()
            }.toTypedArray()
        } else {
            if (includeMethods) {
                rawType.methods
                    .filter {
                        Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains("$") && !methodBlacklist.contains(
                            it.name
                        )
                    }
                    .map {
                        """
${it.name}:
  ${
                            describe(it, rawType, stackMax - 1).lineSequence()
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
                        }
                        """.trim()
                    }.toTypedArray()
            } else {
                arrayOf()
            }
        }).toMutableList()
        if (!coverMethods) methodsYaml.clear()
        if (propertiesYaml.isEmpty() && methodsYaml.isEmpty()) return "type: object\nclass: \"${rawType.name}\""
        if (propertiesYaml.isEmpty()) return "type: object\nclass: ${rawType.name}\nmethods:\n  ${
            methodsYaml.joinToString("\n").lineSequence()
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
        if (methodsYaml.isEmpty()) return "type: object\nclass: ${rawType.name}\nproperties:\n  ${
            propertiesYaml.joinToString("\n").lineSequence()
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
        return "type: object\nclass: ${rawType.name}\nproperties:\n  ${
            propertiesYaml.joinToString("\n").lineSequence()
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
        }\nmethods:\n  ${
            methodsYaml.joinToString("\n").lineSequence()
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
    }

    open val includeMethods: Boolean = true
    override val methodBlacklist = setOf(
        "equals",
        "hashCode",
        "copy",
        "toString",
        "valueOf",
        "wait",
        "notify",
        "notifyAll",
        "getClass",
        "invokeMethod"
    )

    override fun describe(self: Method, clazz: Class<*>?, stackMax: Int): String {
        if (stackMax <= 0) return "..."
        if (!coverMethods) return ""
        // If implClass is a Kotlin class, resolve the KFunction and call the other describe method
        if (clazz != null && clazz.isKotlinClass()) {
            val function = clazz.kotlin.functions.find { it.name == self.name }
            if (function != null) {
                return describe(function, clazz.kotlin, stackMax, true, mutableSetOf())
            }
        }
        val parameterYaml = self.parameters.map { toYaml(it, stackMax - 1) }.toTypedArray().joinToString("\n").trim()
        val returnTypeYaml = toYaml(self.genericReturnType, stackMax - 1, mutableSetOf()).trim()
        val description = self.getAnnotation(Description::class.java)?.value?.trim()?.replace("\"", "\\\"")
        val responseYaml = "responses:\n  application/json:\n    schema:\n      ${
            returnTypeYaml.lineSequence()
                .map {
                    when {
                        it.isBlank() -> {
                            when {
                                it.length < "      ".length -> "      "
                                else -> it
                            }
                        }

                        else -> "      " + it
                    }
                }
                .joinToString("\n")
        }".trim().filterEmptyLines()
        val buffer = StringBuffer()
        buffer.append("operationId: ${self.name}\n")
        if (description != null) {
            buffer.append("description: ${description.trim()}\n")
        }
        if (parameterYaml.isNotBlank()) {
            buffer.append(
                "parameters:\n  ${
                    parameterYaml.lineSequence()
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
                }\n")
        }
        buffer.append("$responseYaml\n")
        return buffer.toString()
    }

    private fun toYaml(self: Parameter, stackMax: Int): String {
        if (stackMax <= 0) return "..."
        val description = self.getAnnotation(Description::class.java)?.value?.trim()
            ?.let { "description: " + it.replace("\n", "\\n") } ?: ""
        return "- name: ${self.name}\n  ${description}\n  ${
            toYaml(self.parameterizedType, stackMax - 1, mutableSetOf()).lineSequence()
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
        }".filterEmptyLines()
    }

    private fun describe(
        self: KFunction<*>,
        concreteClass: KClass<*>,
        stackMax: Int,
        includeOperationID: Boolean = true,
        describedTypes: MutableSet<String>
    ): String {
        val functionTypeRepresentation = "${concreteClass.qualifiedName}::${self.name}"
        if (describedTypes.contains(functionTypeRepresentation) && functionTypeRepresentation !in primitives) return "..."
        describedTypes.add(functionTypeRepresentation)
        if (stackMax <= 0) return "..."
        if (!coverMethods) return ""
        val parameterYaml = self.parameters.filter { it.name != null }
            .map { toYaml(it, concreteClass, stackMax - 1, describedTypes) }.toTypedArray().joinToString("\n").trim()
        val returnTypeYaml = toYaml(self.returnType, stackMax - 1).trim()
        val description = (self.annotations.find { x -> x is Description } as? Description)
            ?.let { "description: ${it.value.trim().replace("\n", "\\n")}" } ?: ""
        val operationID = if (includeOperationID) "operationId: ${self.name}" else ""
        return "${operationID}\n${description}\nparameters:\n  ${
            parameterYaml.lineSequence()
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
        }\nresponses:\n  application/json:\n    schema:\n      ${
            returnTypeYaml.lineSequence()
                .map {
                    when {
                        it.isBlank() -> {
                            when {
                                it.length < "      ".length -> "      "
                                else -> it
                            }
                        }

                        else -> "      " + it
                    }
                }
                .joinToString("\n")
        }".filterEmptyLines()
    }

    private fun toYaml(
        self: KParameter,
        concreteClass: KClass<*>,
        stackMax: Int,
        describedTypes: MutableSet<String>
    ): String {
        val parameterTypeRepresentation = "${concreteClass.qualifiedName}::${self.name}/${self.type}"
        if (describedTypes.contains(parameterTypeRepresentation) && parameterTypeRepresentation !in primitives) return "..."
        describedTypes.add(parameterTypeRepresentation)
        if (stackMax <= 0) return "..."
        val kType = resolveGenericType(concreteClass, self.type)
        val description = (self.annotations.find { it is Description } as? Description)?.value?.trim()
            ?.let { "description: " + it.replace("\n", "\\n") } ?: ""
        val defaultValueInfo = if (self.isOptional) "required: false" else "required: true"
        return "- name: ${self.name}\n  ${description}\n  ${
            toYaml(kType, stackMax - 1).lineSequence()
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
        }\n  ${defaultValueInfo}".filterEmptyLines()
    }


    private fun toYaml(self: Type, stackMax: Int, describedTypes: MutableSet<String>): String {
        if (describedTypes.contains(self.toString())) return self.toString()
        describedTypes.add(self.toString())
        val typeName = self.typeName.substringAfterLast('.').replace('$', '.')
        return if ((isAbbreviated(self) || stackMax <= 0) && typeName !in primitives) "type: object\nclass: ${self.typeName}".filterEmptyLines()
        else if (self is Class<*> && (self.isEnum || DynamicEnum::class.java.isAssignableFrom(self))) {
            val enumConstants = getEnumValues(self).joinToString("\n") { "  - $it" }
            "type: enum\nvalues:\n$enumConstants".filterEmptyLines()
        } else if (typeName in primitives) {
            "type: $typeName"
        } else if (self is Class<*> && (self.isEnum || DynamicEnum::class.java.isAssignableFrom(self))) {
            val enumConstants = getEnumValues(self).joinToString("\n") { "  - $it" }
            "type: enum\nvalues:\n$enumConstants".filterEmptyLines()
        } else if (self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            "type: array\nitems:\n  ${
                toYaml(self.actualTypeArguments[0], stackMax - 1, describedTypes).lineSequence()
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
            }".filterEmptyLines()
        } else if (self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            "type: map\nkeys:\n  ${
                toYaml(self.actualTypeArguments[0], stackMax - 1, describedTypes).lineSequence()
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
            }\nvalues:\n  ${
                toYaml(
                    self.actualTypeArguments[1],
                    stackMax - 1,
                    describedTypes
                ).lineSequence()
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
            }".filterEmptyLines()
        } else if (self.isArray) {
            "type: array\nitems:\n  ${
                toYaml(self.componentType!!, stackMax - 1, describedTypes).lineSequence()
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
            }".filterEmptyLines()
        } else {
            describe(TypeToken.of(self).rawType, stackMax, describedTypes)
        }
    }

    private fun toYaml(self: KType, stackMax: Int): String {
        if (isAbbreviated(self.javaType) || stackMax <= 0) return "type: object\nclass: \"$self\"".filterEmptyLines()
            .trim()
        val typeName = self.toString().substringAfterLast('.').replace('$', '.').lowercase(Locale.getDefault())
        return if (typeName in primitives) {
            "type: $typeName"
        } else if (self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            "type: array\nitems:\n  ${
                toYaml(self.actualTypeArguments[0], stackMax - 1, mutableSetOf()).lineSequence()
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
            }".filterEmptyLines()
        } else if (self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            "type: map\nkeys:\n  ${
                toYaml(self.actualTypeArguments[0], stackMax - 1, mutableSetOf()).replace(
                    "\n",
                    "\n  "
                )
            }\nvalues:  \n  ${
                toYaml(self.actualTypeArguments[1], stackMax - 1, mutableSetOf()).lineSequence()
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
            }".filterEmptyLines()
        } else if (self.classifier is KClass<*> && ((self.classifier as KClass<*>).isSubclassOf(Enum::class) || (self.classifier as KClass<*>).isSubclassOf(
                DynamicEnum::class
            ))
        ) {
            val enumConstants = getEnumValues((self.classifier as KClass<*>).java).joinToString("\n") { "  - $it" }
            "type: enum\nvalues:\n$enumConstants".filterEmptyLines()
        } else if (self.javaType.isArray) {
            "type: array\nitems:\n  ${
                toYaml(self.javaType.componentType!!, stackMax - 1, mutableSetOf()).lineSequence()
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
            }".filterEmptyLines()
        } else {
            describe(TypeToken.of(self.javaType).rawType, stackMax)
        }
    }

    open fun getEnumValues(clazz: Class<*>): List<String> {
        return when {
            clazz.isEnum -> clazz.enumConstants.map { it.toString() }
            DynamicEnum::class.java.isAssignableFrom(clazz) -> {
                DynamicEnum.values(clazz as Class<out DynamicEnum<*>>).map { it.name }
            }

            else -> emptyList()
        }
    }

    private fun String.filterEmptyLines() = this.split("\n").filter { it.isNotBlank() }.joinToString("\n").trim()
}