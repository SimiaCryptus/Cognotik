package com.simiacryptus.jopenai.describe

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.common.reflect.TypeToken
import com.simiacryptus.jopenai.describe.DescriptorUtil.componentType
import com.simiacryptus.jopenai.describe.DescriptorUtil.getAllAnnotations
import com.simiacryptus.jopenai.describe.DescriptorUtil.isArray
import com.simiacryptus.util.DynamicEnum
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

open class TypeScriptDescriber : TypeDescriber() {
    companion object {
        val log = LoggerFactory.getLogger(TypeScriptDescriber::class.java)
    }

    override val markupLanguage: String
        get() = "typescript"

    override fun describe(
        rawType: Class<in Nothing>,
        stackMax: Int,
        describedTypes: MutableSet<String>
    ): String {

        if (!describedTypes.add(rawType.name) && rawType.simpleName.lowercase() !in primitives) {
            log.warn("Recursion detected for type: ${rawType.name}, returning 'any'")
            return "any"
        } else if (rawType.simpleName.lowercase() in primitives) {
            return rawType.simpleName.lowercase()
        }

        if (isAbbreviated(rawType) || stackMax <= 0) return "any /* ${rawType.name} */"
        if (rawType.isEnum || DynamicEnum::class.java.isAssignableFrom(rawType)) {

            return """
                enum ${rawType.simpleName} {
                    ${getEnumValues(rawType).joinToString(",\n    ")}
                }
            """.trimIndent()
        }
        val propertiesTs = if (rawType.isKotlinClass()) {
            rawType.kotlin.memberProperties.filter { it.visibility == KVisibility.PUBLIC }.joinToString("\n") {
                val description = getAllAnnotations(rawType, it).filterIsInstance<Description>().firstOrNull()
                val comment = if (description != null) "  /* ${description.value.trim()} */" else ""
                "  ${it.name}: ${toTypeScript(it.returnType.javaType, stackMax - 1, describedTypes)};$comment"
            }
        } else {
            rawType.declaredFields.filter { Modifier.isPublic(it.modifiers) }.joinToString("\n") {
                val description = it.annotations.find { x -> x is Description } as? Description
                val comment = if (description != null) "  /* ${description.value.trim()} */" else ""
                "  ${it.name}: ${toTypeScript(it.genericType, stackMax - 1, describedTypes)};$comment"
            }
        }
        val methodsTs = if (includeMethods) {
            (if (rawType.isKotlinClass()) {
                rawType.kotlin.functions.filter {
                    it.visibility == KVisibility.PUBLIC
                            && !methodBlacklist.contains(it.name)
                            && !it.isOperator && !it.isInfix && !it.isAbstract
                }.map { describe(it, rawType.kotlin, stackMax - 1, false, describedTypes) }
            } else {
                rawType.methods
                    .filter {
                        Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains("$") && !methodBlacklist.contains(
                            it.name
                        )
                    }
                    .map { describe(it, rawType, stackMax - 1) }
            }).joinToString("\n")
        } else ""


        return """
            interface ${rawType.simpleName} {
            ${
            propertiesTs.lineSequence()
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
            ${
            methodsTs.lineSequence()
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
        """.trimIndent().filterEmptyLines()
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

        if (stackMax <= 0) return "

        if (!coverMethods) return ""
        if (clazz != null && clazz.isKotlinClass()) {
            val function = clazz.kotlin.functions.find { it.name == self.name }
            if (function != null) {
                return describe(function, clazz.kotlin, stackMax, true, mutableSetOf())
            }
        }
        val parameterTs = self.parameters.joinToString(", ") {
            "${it.name}: ${
                toTypeScript(
                    it.parameterizedType,
                    stackMax - 1,
                    mutableSetOf()
                )
            }"
        }
        val returnTypeTs = toTypeScript(self.genericReturnType, stackMax - 1, mutableSetOf())
        val description = self.getAnnotation(Description::class.java)?.value?.trim()
        val comment = if (description != null) "  /* $description */" else ""
        return "  ${self.name}($parameterTs): $returnTypeTs;$comment"
    }

    private fun describe(
        self: KFunction<*>,
        concreteClass: KClass<*>,
        stackMax: Int,
        includeOperationID: Boolean = true,
        describedTypes: MutableSet<String>
    ): String {

        val functionTypeRepresentation = "${concreteClass.qualifiedName}::${self.name}"
        if (describedTypes.contains(functionTypeRepresentation) && functionTypeRepresentation !in primitives) return "

        describedTypes.add(functionTypeRepresentation)
        if (stackMax <= 0) return "

        if (!coverMethods) return ""
        val parameterTs = self.parameters.filter { it.name != null }
            .joinToString(", ") { "${it.name}: ${toTypeScript(it.type, stackMax - 1)}" }
        val returnTypeTs = toTypeScript(self.returnType, stackMax - 1)
        val description = (self.annotations.find { x -> x is Description } as? Description)?.value?.trim()
        val comment = if (description != null) "  /* $description */" else ""
        return "  ${self.name}($parameterTs): $returnTypeTs;$comment"
    }

    private fun toTypeScript(self: Type, stackMax: Int, describedTypes: MutableSet<String>): String {
        if (describedTypes.contains(self.toString())) return "any"
        describedTypes.add(self.toString())
        val typeName = self.typeName.substringAfterLast('.').replace('$', '.')
        return when {
            (isAbbreviated(self) || stackMax <= 0) && typeName !in primitives -> "any /* ${self.typeName} */"
            self is Class<*> && (self.isEnum || DynamicEnum::class.java.isAssignableFrom(self)) -> self.simpleName
            typeName in primitives -> typeName
            self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>) ->
                "${toTypeScript(self.actualTypeArguments[0], stackMax - 1, describedTypes)}[]"

            self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>) ->
                "{ [key: ${toTypeScript(self.actualTypeArguments[0], stackMax - 1, describedTypes)}]: ${
                    toTypeScript(
                        self.actualTypeArguments[1],
                        stackMax - 1,
                        describedTypes
                    )
                } }"

            self.isArray -> "${toTypeScript(self.componentType!!, stackMax - 1, describedTypes)}[]"
            self is ParameterizedType -> {
                val rawType = self.rawType as Class<*>
                val typeArgs =
                    self.actualTypeArguments.joinToString(", ") { toTypeScript(it, stackMax - 1, describedTypes) }
                "${rawType.simpleName}<$typeArgs>"
            }

            else -> TypeToken.of(self).rawType.simpleName
        }
    }

    private fun toTypeScript(self: KType, stackMax: Int): String {
        if (isAbbreviated(self.javaType) || stackMax <= 0) return "any /* $self */"
        val typeName = self.toString().substringAfterLast('.').replace('$', '.').lowercase(Locale.getDefault())
        return when {
            typeName in primitives -> typeName
            self.classifier is KClass<*> && ((self.classifier as KClass<*>).isSubclassOf(Enum::class) || (self.classifier as KClass<*>).isSubclassOf(
                DynamicEnum::class
            )) ->
                (self.classifier as KClass<*>).simpleName ?: "any"

            self.javaType.isArray -> "${toTypeScript(self.javaType.componentType!!, stackMax - 1, mutableSetOf())}[]"
            self.arguments.isNotEmpty() -> {
                val rawType = (self.classifier as KClass<*>).simpleName
                val typeArgs = self.arguments.joinToString(", ") { toTypeScript(it.type!!, stackMax - 1) }
                "$rawType<$typeArgs>"
            }

            else -> TypeToken.of(self.javaType).rawType.simpleName
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

    private fun String.filterEmptyLines() = this.split("\n").filter { it.isNotBlank() }.joinToString("\n")
}