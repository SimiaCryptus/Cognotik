package com.simiacryptus.jopenai.describe

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.common.reflect.TypeToken
import com.simiacryptus.jopenai.describe.DescriptorUtil.componentType
import com.simiacryptus.jopenai.describe.DescriptorUtil.isArray
import com.simiacryptus.jopenai.describe.DescriptorUtil.resolveGenericType
import com.simiacryptus.util.DynamicEnum
import org.slf4j.LoggerFactory
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

open class JsonDescriber(
    private val whitelist: MutableSet<String> = setOf(
        "com.simiacryptus",
        "com.simiacryptus"
    ).toMutableSet()
) : TypeDescriber() {
    companion object {
        private val log = LoggerFactory.getLogger(JsonDescriber::class.java)
    }

    override val markupLanguage: String
        get() = "json"

    override fun describe(
        rawType: Class<in Nothing>,
        stackMax: Int,
        describedTypes: MutableSet<String>
    ): String {
//        log.info("Starting description of type: ${rawType.name} with stackMax: $stackMax")
        if (!whitelist.contains(rawType.name)) {
//            log.error("Type ${rawType.name} is not in the whitelist")
            return """{
                 "type": "object",
                 "class": "${rawType.name}",
                 "allowed": false
               }""".trimIndent()
        }
        if (!describedTypes.add(rawType.name) && rawType.name !in primitives) {
//            log.warn("Recursion detected for type: ${rawType.name}, returning placeholder")
            return "{...}"
        } else if (rawType.simpleName.lowercase() in primitives) {
            return """
            {
              "type": "${rawType.simpleName.lowercase()}"
            }""".trimIndent()
        }
        if (isAbbreviated(rawType) || stackMax <= 0) {
//            log.warn("Abbreviating description for type: ${rawType.name} due to stackMax: $stackMax")
            return """{
            {
              "type": "object",
              "class": "${rawType.name}"
            }
            """.trimIndent()
        }
        if (rawType.isEnum || DynamicEnum::class.java.isAssignableFrom(rawType)) {
            return """
            {
              "type": "enum",
              "values": [
                ${getEnumValues(rawType).joinToString(",\n") { "\"$it\"" }}
              ]
            }
            """.trimIndent()
        }
        val propertiesJson = if (rawType.isKotlinClass()) {
            rawType.kotlin.memberProperties.filter { it.visibility == KVisibility.PUBLIC }.joinToString(",\n") {
                val description =
                    DescriptorUtil.getAllAnnotations(rawType, it).find { x -> x is Description } as? Description
                val propertyDescription = if (description != null) """
                    "${it.name}": {
                      "description": "${description.value.trim()}",
                      ${
                    toJson(it.returnType.javaType, stackMax - 1, describedTypes).lineSequence()
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
                }
                    }
                    """.trimIndent().trim() else """
                    "${it.name}": {
                      ${
                    toJson(it.returnType.javaType, stackMax - 1, describedTypes).lineSequence()
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
                }
                    }
                    """.trimIndent().trim()
                propertyDescription
            }
        } else {
            rawType.declaredFields.filter { Modifier.isPublic(it.modifiers) }.joinToString(",\n") {
                val description =
                    it.annotations.find { x -> x is Description } as? Description
                val fieldDescription = if (description != null) """
                "${it.name}": {
                  "description": "${description.value.trim()}",
                  ${
                    toJson(it.genericType, stackMax - 1, describedTypes).lineSequence()
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
                }
                """.trimIndent() else """
                "${it.name}": {
                  ${
                    toJson(it.genericType, stackMax - 1, describedTypes).lineSequence()
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
                }
                """.trimIndent()
                fieldDescription
            }
        }
        val methodsJson = (if (rawType.isKotlinClass()) {
            rawType.kotlin.functions.filter {
                it.visibility == KVisibility.PUBLIC
                        && !methodBlacklist.contains(it.name)
                        && !it.isOperator && !it.isInfix && !it.isAbstract
            }.joinToString(",\n") {
                """
            "${it.name}": {
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
            }
            """.trimIndent().trim()
            }
        } else {
            if (includeMethods) {
                rawType.methods
                    .filter {
                        Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains("$") && !methodBlacklist.contains(
                            it.name
                        )
                    }
                    .joinToString(",\n") {
                        """
                "${it.name}": {
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
                }
                """.trimIndent().trim()
                    }
            } else {
                ""
            }
        }).ifEmpty { "" }
        val jsonBody = StringBuilder()
        jsonBody.append(
            """
            {
              "type": "object",
              "class": "${rawType.name}",
            """.trimIndent()
        )
        if (propertiesJson.isNotEmpty()) {
            jsonBody.append(
                """
              "properties": {
                $propertiesJson
              },
            """.trimIndent()
            )
        }
        if (methodsJson.isNotEmpty()) {
            jsonBody.append(
                """
              "methods": {
                $methodsJson
              }
            """.trimIndent()
            )
        }
        jsonBody.append("\n}")
//        log.info("Completed description for type: ${rawType.name}")
        return jsonBody.toString()
    }

    override fun describe(self: Method, clazz: Class<*>?, stackMax: Int): String {
//        log.info("Describing method: ${self.name} in class: ${clazz?.name}")
        val returnType = self.returnType
        clazz ?: return "..."
        val description = getAllAnnotations(clazz, self).find { x -> x is Description } as? Description
        val parameterJson = self.parameters.map { toJson(it, stackMax - 1) }.toTypedArray().joinToString(",\n").trim()
        val methodDescription = if (description != null) """
            "description": "${description.value.trim()}",
            ${
            describe(returnType, stackMax, mutableSetOf()).lineSequence()
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
            """.trimIndent().trim() else """
            ${
            describe(returnType, stackMax, mutableSetOf()).lineSequence()
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
            """.trimIndent()
        return """
            {
              "type": "method",
              "class": "${clazz.name ?: "unknown"}",
              "name": "${self.name}",
              "parameters": [$parameterJson],
              $methodDescription
            }
            """.trimIndent()
    }

    private fun getAllAnnotations(clazz: Class<*>, self: Method): List<Annotation> {
        return (self.annotations + (clazz.kotlin.constructors.firstOrNull()?.parameters?.find { x -> x.name == self.name }?.annotations
            ?: listOf())
                ).toList()
    }

    private fun toJson(self: Parameter, stackMax: Int): String {
        if (stackMax <= 0) return "{...}"
        val description = self.getAnnotation(Description::class.java)?.value?.trim()
            ?.let { "\"description\": \"${it.replace("\n", "\\n")}\"," } ?: ""
        return """
        {
          "name": "${self.name}",
          $description
          ${
            toJson(self.parameterizedType, stackMax - 1, mutableSetOf()).lineSequence()
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
        }
        """.trimIndent()
    }

    private fun describe(
        self: KFunction<*>,
        concreteClass: KClass<*>,
        stackMax: Int,
        includeOperationID: Boolean = true,
        describedTypes: MutableSet<String>
    ): String {
        val functionTypeRepresentation = "${concreteClass.qualifiedName}::${self.name}"
        if (describedTypes.contains(functionTypeRepresentation) && functionTypeRepresentation !in primitives) return "{...}"
        describedTypes.add(functionTypeRepresentation)
        if (stackMax <= 0) return "{...}"
        if (!coverMethods) return "{}"
        val parameterJson = self.parameters.filter { it.name != null }
            .map { toJson(it, concreteClass, stackMax - 1, describedTypes) }.toTypedArray().joinToString(",\n").trim()
        val returnTypeJson = toJson(self.returnType, stackMax - 1, describedTypes).trim()
        val description = (self.annotations.find { x -> x is Description } as? Description)
            ?.let { "\"description\": \"${it.value.trim().replace("\n", "\\n")}\"," } ?: ""
        val operationID = if (includeOperationID) "\"operationId\": \"${self.name}\"," else ""
        return """
        {
          $operationID
          $description
          "parameters": [
            $parameterJson
          ],
          "returnType": $returnTypeJson
        }
        """.trimIndent()
    }

    private fun toJson(
        self: KParameter,
        concreteClass: KClass<*>,
        stackMax: Int,
        describedTypes: MutableSet<String>
    ): String {
        val parameterTypeRepresentation = "${concreteClass.qualifiedName}::${self.name}/${self.type}"
        if (describedTypes.contains(parameterTypeRepresentation) && parameterTypeRepresentation !in primitives) return "{...}"
        describedTypes.add(parameterTypeRepresentation)
        if (stackMax <= 0) return "{...}"
        val kType = resolveGenericType(concreteClass, self.type)
        val description = (self.annotations.find { it is Description } as? Description)?.value?.trim()
            ?.let { "\"description\": \"${it.replace("\n", "\\n")}\"," } ?: ""
        val defaultValueInfo = if (self.isOptional) "\"required\": false" else "\"required\": true"
        return """
        {
          "name": "${self.name}",
          $description
          ${
            toJson(kType, stackMax - 1, describedTypes).lineSequence()
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
        },
          $defaultValueInfo
        }
        """.trimIndent()
    }

    private fun toJson(self: KType, stackMax: Int, describedTypes: MutableSet<String>): String {
        return toJson(self.javaType, stackMax, describedTypes)
    }

    private fun toJson(self: Type, stackMax: Int, describedTypes: MutableSet<String>): String {
        if (describedTypes.contains(self.toString())) return "{...}"
        describedTypes.add(self.toString())
        val typeName = self.typeName.substringAfterLast('.').replace('$', '.')
        return if ((isAbbreviated(self) || stackMax <= 0) && typeName !in primitives) """
        {
          "type": "object",
          "class": "${self.typeName}"
        }
        """.trimIndent()
        else if (self is Class<*> && (self.isEnum || DynamicEnum::class.java.isAssignableFrom(self))) {
            val enumConstants = getEnumValues(self).joinToString(",\n") { "\"$it\"" }
            """
            {
              "type": "enum",
              "values": [
                $enumConstants
              ]
            }
            """.trimIndent()
        } else if (typeName in primitives) {
            """
            {
              "type": "$typeName"
            }
            """.trimIndent()
        } else if (self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            """
            {
              "type": "array",
              "items": ${toJson(self.actualTypeArguments[0], stackMax - 1, describedTypes)}
            }
            """.trimIndent()
        } else if (self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            """
            {
              "type": "map",
              "keys": ${toJson(self.actualTypeArguments[0], stackMax - 1, describedTypes)},
              "values": ${toJson(self.actualTypeArguments[1], stackMax - 1, describedTypes)}
            }
            """.trimIndent()
        } else if (self.isArray) {
            """
            {
              "type": "array",
              "items": ${toJson(self.componentType!!, stackMax - 1, describedTypes)}
            }
            """.trimIndent()
        } else {
            describe(TypeToken.of(self).rawType, stackMax, describedTypes)
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

}