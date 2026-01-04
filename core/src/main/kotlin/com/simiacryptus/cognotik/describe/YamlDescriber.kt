package com.simiacryptus.cognotik.describe

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.common.reflect.TypeToken
import com.simiacryptus.cognotik.describe.DescriptorUtil.componentType
import com.simiacryptus.cognotik.describe.DescriptorUtil.getAllAnnotations
import com.simiacryptus.cognotik.describe.DescriptorUtil.isArray
import com.simiacryptus.cognotik.describe.DescriptorUtil.resolveGenericType
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.EnabledStrategy
import com.simiacryptus.cognotik.util.LoggerFactory
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

    // Map to track registered sub-implementations for each parent class
    protected val subTypeRegistry: MutableMap<Class<*>, MutableList<Class<*>>> = mutableMapOf()

    /**
     * Register a sub-implementation for a parent class
     * @param parentClass The parent/interface class
     * @param subClass The sub-implementation class
     */
    override fun <T, U : T> registerSubType(parentClass: Class<T>, subClass: Class<U>) {
        subTypeRegistry.getOrPut(parentClass) { mutableListOf() }.add(subClass)
        log.debug("Registered subtype ${subClass.name} for parent ${parentClass.name}")
    }

    /**
     * Register multiple sub-implementations for a parent class
     * @param parentClass The parent/interface class
     * @param subClasses The sub-implementation classes
     */
    override fun <T, U : T> registerSubTypes(parentClass: Class<T>, vararg subClasses: Class<U>) {
        subClasses.forEach { registerSubType(parentClass, it) }
    }

    override fun <T, U : T> clearSubTypes(parentClass: Class<T>) {
        subTypeRegistry.remove(parentClass)
        log.debug("Cleared subtypes for parent ${parentClass.name}")
    }

    /**
     * Get all registered sub-implementations for a parent class
     * @param parentClass The parent/interface class
     * @return List of registered sub-implementation classes
     */
    open fun getRegisteredSubTypes(parentClass: Class<*>): List<Class<*>> {
        return subTypeRegistry[parentClass]?.toList() ?: emptyList()
    }


    override val markupLanguage: String
        get() = "yaml"

    override fun describe(
        rawType: Class<in Nothing>,
        instance: Any?,
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
            return "type: enumeration\nvalues:\n" + getEnumValues(rawType).joinToString("\n") { "  - $it" }
        }
        // Check if there are registered sub-types for this class
        val registeredSubTypes = getRegisteredSubTypes(rawType)
        val subTypesYaml = if (registeredSubTypes.isNotEmpty()) {
            val subTypeDescriptions = registeredSubTypes.map { subType ->
                val subTypeDesc = describe(subType as Class<in Nothing>, null, stackMax - 1, describedTypes.toMutableSet())
                "${subType.simpleName}:\n  ${
                    subTypeDesc.lineSequence()
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
            "subtypes:\n  ${
                subTypeDescriptions.joinToString("\n").lineSequence()
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
            ""
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
                    describe(it, rawType.kotlin, instance, stackMax - 1, false, describedTypes).lineSequence()
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
                            describe(it, rawType, instance, stackMax - 1).lineSequence()
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

        // Build the final YAML output with subtypes
        if (propertiesYaml.isEmpty() && methodsYaml.isEmpty() && subTypesYaml.isEmpty()) {
            return "type: object\nclass: \"${rawType.name}\""
        }
        if (propertiesYaml.isEmpty() && subTypesYaml.isEmpty()) {
            return "type: object\nclass: ${rawType.name}\nmethods:\n  ${
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
        if (propertiesYaml.isEmpty() && methodsYaml.isEmpty()) {
            return "type: object\nclass: ${rawType.name}\n${subTypesYaml}"
        }
        if (methodsYaml.isEmpty() && subTypesYaml.isEmpty()) {
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
            }"
        }
        if (propertiesYaml.isEmpty()) {
            return "type: object\nclass: ${rawType.name}\nmethods:\n  ${
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
            }\n${subTypesYaml}"
        }
        if (methodsYaml.isEmpty()) {
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
            }\n${subTypesYaml}"
        }
        if (subTypesYaml.isEmpty()) {
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
        }\n${subTypesYaml}"
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

    override fun describe(self: Method, clazz: Class<*>?, instance: Any?, stackMax: Int): String {
        if (stackMax <= 0) return "..."
        if (!coverMethods) return ""

        if (clazz != null && clazz.isKotlinClass()) {
            val function = clazz.kotlin.functions.find { it.name == self.name }
            if (function != null) {
                return describe(function, clazz.kotlin, instance, stackMax, true, mutableSetOf())
            }
        }
        val overrides = (instance as? MethodTypeDescriber)?.getMethodTypes(self.name)
        val parameterYaml = self.parameters.mapIndexed { index, parameter ->
            toYaml(parameter, stackMax - 1, overrides?.getOrNull(index))
        }.toTypedArray().joinToString("\n").trim()
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

    private fun toYaml(self: Parameter, stackMax: Int, typeOverride: Type? = null): String {
        if (stackMax <= 0) return "..."
        val description = self.getAnnotation(Description::class.java)?.value?.trim()
            ?.let { "description: " + it.replace("\n", "\\n") } ?: ""
        return "- name: ${self.name}\n  ${description}\n  ${
            toYaml(typeOverride ?: self.parameterizedType, stackMax - 1, mutableSetOf()).lineSequence()
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
        instance: Any?,
        stackMax: Int,
        includeOperationID: Boolean = true,
        describedTypes: MutableSet<String>
    ): String {
        val functionTypeRepresentation = "${concreteClass.qualifiedName}::${self.name}"
        if (describedTypes.contains(functionTypeRepresentation) && functionTypeRepresentation !in primitives) return "..."
        describedTypes.add(functionTypeRepresentation)
        if (stackMax <= 0) return "..."
        if (!coverMethods) return ""
        val overrides = (instance as? MethodTypeDescriber)?.getMethodTypes(self.name)
        val parameterYaml = self.parameters.filter { it.name != null }
            .mapIndexed { index, kParameter ->
                toYaml(kParameter, concreteClass, stackMax - 1, describedTypes, overrides?.getOrNull(index))
            }.toTypedArray().joinToString("\n").trim()
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
       describedTypes: MutableSet<String>,
        typeOverride: Type? = null
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
            (if (typeOverride != null) toYaml(typeOverride, stackMax - 1, describedTypes) else toYaml(kType, stackMax - 1)).lineSequence()
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
            describe(TypeToken.of(self).rawType, null, stackMax, describedTypes)
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
            clazz.isEnum -> clazz.enumConstants
                .filter { constant ->
                    if (constant is EnabledStrategy) constant.isEnabled() else true
                }
                .map { it.toString() }

            DynamicEnum::class.java.isAssignableFrom(clazz) -> {
                DynamicEnum.values(clazz as Class<out DynamicEnum<*>>)
                    .filter { dynamicEnum ->
                        if (dynamicEnum is EnabledStrategy) dynamicEnum.isEnabled() else true
                    }
                    .map { it.name }
            }

            else -> emptyList()
        }
    }

    private fun String.filterEmptyLines() = this.split("\n").filter { it.isNotBlank() }.joinToString("\n").trim()
}