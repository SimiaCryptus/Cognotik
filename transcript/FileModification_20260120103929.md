# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/AbbrevWhitelistTSDescriber.kt

```
package com.simiacryptus.cognotik.describe

import com.simiacryptus.cognotik.describe.DescriptorUtil.componentType
import com.simiacryptus.cognotik.describe.DescriptorUtil.isArray
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

open class AbbrevWhitelistTSDescriber(private vararg val abbreviated: String) : TypeScriptDescriber() {

    override fun isAbbreviated(self: Type): Boolean = when {
        self.typeName in primitives -> {
            false
        }

        self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>) -> {
            isAbbreviated(self.actualTypeArguments[0])
        }

        self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>) -> {
            isAbbreviated(self.actualTypeArguments[0]) && isAbbreviated(self.actualTypeArguments[1])
        }

        self.isArray -> {
            isAbbreviated(self.componentType!!)
        }

        else -> {
            abbreviated.find { self.typeName.startsWith(it) } == null || super.isAbbreviated(self)
        }
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/AbbrevWhitelistYamlDescriber.kt

```
package com.simiacryptus.cognotik.describe

import com.simiacryptus.cognotik.describe.DescriptorUtil.componentType
import com.simiacryptus.cognotik.describe.DescriptorUtil.isArray
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

open class AbbrevWhitelistYamlDescriber(private vararg val abbreviated: String) : YamlDescriber() {
    override fun isAbbreviated(self: Type): Boolean = when {
        self.typeName in primitives -> false
        self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>) ->
            isAbbreviated(self.actualTypeArguments[0])
        self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>) ->
            isAbbreviated(self.actualTypeArguments[0]) && isAbbreviated(self.actualTypeArguments[1])
        self.isArray -> isAbbreviated(self.componentType!!)
        else -> abbreviated.find { self.typeName.startsWith(it) } == null || super.isAbbreviated(self)
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/Description.kt

```
package com.simiacryptus.cognotik.describe

@Retention(AnnotationRetention.RUNTIME)
annotation class Description(val value: String)

```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/DescriptorUtil.kt

```
package com.simiacryptus.cognotik.describe

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.memberFunctions

object DescriptorUtil {

    fun getAllAnnotations(
        rawType: Class<in Nothing>,
        property: KProperty1<out Any, *>,
    ): List<Annotation> =
        property.annotations + (rawType.kotlin.constructors.firstOrNull()?.parameters?.find { x -> x.name == property.name }?.annotations
            ?: listOf())

    val Type.isArray: Boolean
        get() {

            return this is Class<*> && this.isArray
        }

    val Type.componentType: Type?
        get() {

            return when (this) {
                is Class<*> -> if (this.isArray) this.componentType else null
                is ParameterizedType -> this.actualTypeArguments.firstOrNull()
                else -> null
            }
        }

    fun resolveMethodReturnType(concreteClass: KClass<*>, methodName: String): KType {


        val method = concreteClass.memberFunctions.firstOrNull { it.name == methodName }
            ?: throw IllegalArgumentException("Method $methodName not found in class $concreteClass")

        var returnType = method.returnType

        if (returnType.classifier is KTypeParameter) {

            returnType = resolveGenericType(concreteClass, returnType)
        }

        return returnType
    }

    fun resolveGenericType(concreteClass: KClass<*>, kType: KType): KType {

        val classifier = kType.classifier

        if (classifier is KTypeParameter) {

            val typeArgument = concreteClass.typeParameters
                .firstOrNull { it.name == classifier.name }
                ?.let { typeParameter ->

                    concreteClass.supertypes.flatMap { it.arguments }.firstOrNull { argument ->
                        argument.type?.classifier == typeParameter
                    }?.type
                }


            return typeArgument ?: kType
        }

        return kType
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/JsonDescriber.kt

```
package com.simiacryptus.cognotik.describe

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.common.reflect.TypeToken
import com.simiacryptus.cognotik.describe.DescriptorUtil.componentType
import com.simiacryptus.cognotik.describe.DescriptorUtil.isArray
import com.simiacryptus.cognotik.describe.DescriptorUtil.resolveGenericType
import com.simiacryptus.cognotik.describe.YamlDescriber.Companion.log
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.EnabledStrategy
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

    override val markupLanguage: String
        get() = "json"

    override fun describe(
        rawType: Class<in Nothing>,
        instance: Any?,
        stackMax: Int,
        describedTypes: MutableSet<String>
    ): String {

        if (!whitelist.contains(rawType.name)) {

            return """{
                 "type": "object",
                 "class": "${rawType.name}",
                 "allowed": false
               }""".trimIndent()
        }
        if (!describedTypes.add(rawType.name) && rawType.name !in primitives) {

            return "{...}"
        } else if (rawType.simpleName.lowercase() in primitives) {
            return """
            {
              "type": "${rawType.simpleName.lowercase()}"
            }""".trimIndent()
        }
        if (isAbbreviated(rawType) || stackMax <= 0) {

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

        return jsonBody.toString()
    }

    override fun describe(self: Method, clazz: Class<*>?, instance: Any?, stackMax: Int): String {

        val returnType = self.returnType
        clazz ?: return "..."
        val description = getAllAnnotations(clazz, self).find { x -> x is Description } as? Description
        val overrides = (instance as? MethodTypeDescriber)?.getMethodTypes(self.name)
        val parameterJson = self.parameters.mapIndexed { index, parameter ->
            toJson(parameter, stackMax - 1, overrides?.getOrNull(index))
        }.toTypedArray().joinToString(",\n").trim()
        val methodDescription = if (description != null) """
            "description": "${description.value.trim()}",
            ${
            describe(returnType, null, stackMax, mutableSetOf()).lineSequence()
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
            describe(returnType, null, stackMax, mutableSetOf()).lineSequence()
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

    private fun toJson(self: Parameter, stackMax: Int, typeOverride: Type? = null): String {
        if (stackMax <= 0) return "{...}"
        val description = self.getAnnotation(Description::class.java)?.value?.trim()
            ?.let { "\"description\": \"${it.replace("\n", "\\n")}\"," } ?: ""
        return """
        {
          "name": "${self.name}",
          $description
          ${
            toJson(typeOverride ?: self.parameterizedType, stackMax - 1, mutableSetOf()).lineSequence()
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
        instance: Any?,
        stackMax: Int,
        includeOperationID: Boolean = true,
        describedTypes: MutableSet<String>
    ): String {
        val functionTypeRepresentation = "${concreteClass.qualifiedName}::${self.name}"
        if (describedTypes.contains(functionTypeRepresentation) && functionTypeRepresentation !in primitives) return "{...}"
        describedTypes.add(functionTypeRepresentation)
        if (stackMax <= 0) return "{...}"
        if (!coverMethods) return "{}"
        val overrides = (instance as? MethodTypeDescriber)?.getMethodTypes(self.name)
        val parameterJson = self.parameters.filter { it.name != null }
            .mapIndexed { index, kParameter ->
                toJson(kParameter, concreteClass, stackMax - 1, describedTypes, overrides?.getOrNull(index))
            }.toTypedArray().joinToString(",\n").trim()
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
       describedTypes: MutableSet<String>,
        typeOverride: Type? = null
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
            (if (typeOverride != null) toJson(typeOverride, stackMax - 1, describedTypes) else toJson(kType, stackMax - 1, describedTypes)).lineSequence()
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
            describe(TypeToken.of(self).rawType, null, stackMax, describedTypes)
        }
    }

    open fun getEnumValues(clazz: Class<*>): List<String> {
        return when {
            clazz.isEnum -> clazz.enumConstants.filter {
                if (it is EnabledStrategy) it.isEnabled() else true
            }.map { it.toString() }

            DynamicEnum::class.java.isAssignableFrom(clazz) -> {
                DynamicEnum.values(clazz as Class<out DynamicEnum<*>>).filter {
                    if (it is EnabledStrategy) it.isEnabled() else true
                }.map { it.name }
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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/MethodTypeDescriber.kt

```
package com.simiacryptus.cognotik.describe

import java.lang.reflect.Type

interface MethodTypeDescriber {
    fun getMethodTypes(methodName: String): List<Type>
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/TypeDescriber.kt

```
package com.simiacryptus.cognotik.describe

import com.simiacryptus.cognotik.describe.DescriptorUtil.componentType
import com.simiacryptus.cognotik.describe.DescriptorUtil.isArray
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*

abstract class TypeDescriber {

    abstract val markupLanguage: String
    abstract val methodBlacklist: Set<String>
    var coverMethods = true
    abstract fun describe(
        rawType: Class<in Nothing>,
        instance: Any? = null,
        stackMax: Int = 10,
        describedTypes: MutableSet<String> = mutableSetOf()
    ): String

    abstract fun <T, U : T> registerSubType(parentClass: Class<T>, subClass: Class<U>)
    abstract fun <T, U : T> registerSubTypes(parentClass: Class<T>, vararg subClasses: Class<U>)
    abstract fun <T, U : T> clearSubTypes(parentClass: Class<T>)
    abstract fun describe(self: Method, clazz: Class<*>? = null, instance: Any? = null, stackMax: Int = 5): String
    open fun isAbbreviated(self: Type): Boolean {

        val name = self.typeName
        val typeName = self.typeName.substringAfterLast('.').replace('$', '.').lowercase(Locale.getDefault())
        if (typeName in primitives) {

            return false
        } else if (self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>)) {

            return isAbbreviated(self.actualTypeArguments[0])
        } else if (self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>)) {

            return isAbbreviated(self.actualTypeArguments[0]) && isAbbreviated(self.actualTypeArguments[1])
        } else if (self.isArray) {

            return isAbbreviated(self.componentType!!)
        }

        if (name.startsWith("java.")) return true
        if (name.startsWith("kotlin.")) return true
        if (name.startsWith("sun.")) return true
        if (name.startsWith("apache.")) return true
        if (name.startsWith("org.slf4j.")) return true
        if (name.startsWith("com.fasterxml.")) return true
        return false
    }

    companion object {
        val primitives = setOf(
            "boolean",
            "int",
            "integer",
            "number",
            "string",
            "double",
            "float",
            "long",
            "short",
            "byte",
            "char",
            "object"
        )
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/TypeScriptDescriber.kt

```
package com.simiacryptus.cognotik.describe

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.common.reflect.TypeToken
import com.simiacryptus.cognotik.describe.DescriptorUtil.componentType
import com.simiacryptus.cognotik.describe.DescriptorUtil.getAllAnnotations
import com.simiacryptus.cognotik.describe.DescriptorUtil.isArray
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.LoggerFactory
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

    override val markupLanguage: String
        get() = "typescript"

    override fun describe(
        rawType: Class<in Nothing>,
        instance: Any?,
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
                }.map { describe(it, rawType.kotlin, instance, stackMax - 1, false, describedTypes) }
            } else {
                rawType.methods
                    .filter {
                        Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains("$") && !methodBlacklist.contains(
                            it.name
                        )
                    }
                    .map { describe(it, rawType, instance, stackMax - 1) }
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

    override fun describe(self: Method, clazz: Class<*>?, instance: Any?, stackMax: Int): String {

        if (stackMax <= 0) return ""

        if (!coverMethods) return ""
        if (clazz != null && clazz.isKotlinClass()) {
            val function = clazz.kotlin.functions.find { it.name == self.name }
            if (function != null) {
                return describe(function, clazz.kotlin, instance, stackMax, true, mutableSetOf())
            }
        }
        val overrides = (instance as? MethodTypeDescriber)?.getMethodTypes(self.name)
        val parameterTs = self.parameters.mapIndexed { index, parameter ->
            "${parameter.name}: ${
                toTypeScript(
                    overrides?.getOrNull(index) ?: parameter.parameterizedType,
                    stackMax - 1,
                    mutableSetOf()
                )
            }"
        }.joinToString(", ")
        val returnTypeTs = toTypeScript(self.genericReturnType, stackMax - 1, mutableSetOf())
        val description = self.getAnnotation(Description::class.java)?.value?.trim()
        val comment = if (description != null) "  /* $description */" else ""
        return "  ${self.name}($parameterTs): $returnTypeTs;$comment"
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
        if (describedTypes.contains(functionTypeRepresentation) && functionTypeRepresentation !in primitives) return ""

        describedTypes.add(functionTypeRepresentation)
        if (stackMax <= 0) return ""

        if (!coverMethods) return ""
        val overrides = (instance as? MethodTypeDescriber)?.getMethodTypes(self.name)
        val parameterTs = self.parameters.filter { it.name != null }
            .mapIndexed { index, kParameter ->
                val override = overrides?.getOrNull(index)
                "${kParameter.name}: ${if (override != null) toTypeScript(override, stackMax - 1, mutableSetOf()) else toTypeScript(kParameter.type, stackMax - 1)}"
            }.joinToString(", ")
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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/YamlDescriber.kt

```
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
        val registeredSubTypes = getRegisteredSubTypes(rawType)
        val subTypesYaml = if (registeredSubTypes.isNotEmpty()) {
            val subTypeDescriptions = registeredSubTypes.map { subType ->
                subType.simpleName.toString() + ":\n" + describe(
                    subType,
                    null,
                    stackMax - 1,
                    describedTypes.toMutableSet()
                ).indent("  ")
            }
            "subtypes:\n" + subTypeDescriptions.joinToString("\n").indent("  ")
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
                    }\"\n${
                        toYaml.indent("  ")
                    }"
                } else {
                    "${it.name}:\n${
                        toYaml.indent("  ")
                    }"
                }
            }.toTypedArray()
        } else {
            rawType.declaredFields.filter { Modifier.isPublic(it.modifiers) }.map {
                val description =
                    it.annotations.find { x -> x is Description } as? Description

                return@map if (description != null) "${it.name}:\n  description: ${description.value.trim()}\n${
                    toYaml(it.genericType, stackMax - 1, describedTypes).indent("  ")
                }"
                else

                    "${it.name}:\n${
                        toYaml(it.genericType, stackMax - 1, describedTypes).indent("  ")
                    }"
            }.toTypedArray()
        }
        val methodsYaml = (if (rawType.isKotlinClass()) {
            rawType.kotlin.functions.filter {
                it.visibility == KVisibility.PUBLIC
                        && !methodBlacklist.contains(it.name)
                        && !it.isOperator && !it.isInfix && !it.isAbstract
            }.map {
                ("\n" + it.name + ":\n" + describe(
                    it,
                    rawType.kotlin,
                    instance,
                    stackMax - 1,
                    false,
                    describedTypes
                ).indent("  ") + "\n").trim()
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
                        it.name + ":\n" + describe(it, rawType, instance, stackMax - 1).indent("  ")
                    }.toTypedArray()
            } else {
                arrayOf()
            }
        }).toMutableList()
        if (!coverMethods) methodsYaml.clear()
// Build the final YAML output with subtypes
        return when {
            propertiesYaml.isEmpty() && methodsYaml.isEmpty() && subTypesYaml.isEmpty() -> "type: object\nclass: \"${rawType.name}\""
            propertiesYaml.isEmpty() && subTypesYaml.isEmpty() -> "type: object\nclass: " + rawType.name + "\nmethods:\n" +
                    methodsYaml.joinToString("\n").indent("  ")

            propertiesYaml.isEmpty() && methodsYaml.isEmpty() -> "type: object\nclass: " + rawType.name + "\n" + subTypesYaml
            methodsYaml.isEmpty() && subTypesYaml.isEmpty() -> "type: object\nclass: " + rawType.name + "\nproperties:\n" +
                    propertiesYaml.joinToString("\n").indent("  ")

            propertiesYaml.isEmpty() -> "type: object\nclass: " + rawType.name + "\nmethods:\n" +
                    methodsYaml.joinToString("\n").indent("  ") + "\n" + subTypesYaml

            methodsYaml.isEmpty() -> "type: object\nclass: " + rawType.name + "\nproperties:\n" +
                    propertiesYaml.joinToString("\n").indent("  ") + "\n" + subTypesYaml

            subTypesYaml.isEmpty() -> "type: object\nclass: " + rawType.name + "\nproperties:\n" +
                    propertiesYaml.joinToString("\n").indent("  ") + "\nmethods:\n" +
                    methodsYaml.joinToString("\n").indent("  ")

            else -> "type: object\nclass: " + rawType.name + "\nproperties:\n" +
                    propertiesYaml.joinToString("\n").indent("  ") + "\nmethods:\n" +
                    methodsYaml.joinToString("\n").indent("  ") + "\n" + subTypesYaml
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

        val responseYaml = ("responses:\n  application/json:\n    schema:\n" + returnTypeYaml.indent("      ")).trim().filterEmptyLines()
        val buffer = StringBuffer()
        buffer.append("operationId: ${self.name}\n")
        if (description != null) {
            buffer.append("description: ${description.trim()}\n")
        }
        if (parameterYaml.isNotBlank()) {
            buffer.append(
                "parameters:\n" + parameterYaml.indent("  ") + "\n"
            )
        }
        buffer.append("$responseYaml\n")
        return buffer.toString()
    }

    private fun toYaml(self: Parameter, stackMax: Int, typeOverride: Type? = null) = if (stackMax <= 0) "..."
    else ("- name: " + self.name + "\n  " + (self.getAnnotation(Description::class.java)?.value?.trim()
        ?.let { "description: " + it.replace("\n", "\\n") } ?: "") + "\n" +
            toYaml(typeOverride ?: self.parameterizedType, stackMax - 1, mutableSetOf()).indent("  ")
            ).filterEmptyLines()

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
        val returnTypeYaml = toYaml(self.returnType, stackMax - 1, describedTypes).trim()
        val description = (self.annotations.find { x -> x is Description } as? Description)
            ?.let { "description: ${it.value.trim().replace("\n", "\\n")}" } ?: ""
        val operationID = if (includeOperationID) "operationId: ${self.name}" else ""
        return (operationID + "\n" + description + "\nparameters:\n" + parameterYaml.indent("  ") +
                "\nresponses:\n  application/json:\n    schema:\n" + returnTypeYaml.indent("      ")
                ).filterEmptyLines()
    }

    private fun toYaml(
        self: KParameter,
        concreteClass: KClass<*>,
        stackMax: Int, describedTypes: MutableSet<String>,
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
        val string = if (typeOverride != null) toYaml(typeOverride, stackMax - 1, describedTypes).indent("  ")
        else toYaml(kType, stackMax - 1, describedTypes).indent("  ")
        return ("- name: " + self.name + "\n  " + description + "\n" + string + "\n  " + defaultValueInfo).filterEmptyLines()
    }

    private fun toYaml(self: Type, stackMax: Int, describedTypes: MutableSet<String>): String {
        val typeName = self.typeName.substringAfterLast('.').replace('$', '.')
        if (describedTypes.contains(self.toString()) && typeName.lowercase() !in primitives) return "..."
        describedTypes.add(self.toString())
        return if ((isAbbreviated(self) || stackMax <= 0) && typeName.lowercase() !in primitives)
            "type: object\nclass: ${self.typeName}".filterEmptyLines()
        else if (self is Class<*> && (self.isEnum || DynamicEnum::class.java.isAssignableFrom(self))) {
            ("type: enum\nclass: ${self.typeName}\nvalues:\n" + getEnumValues(self).joinToString("\n") { "  - $it" }).filterEmptyLines()
        } else if (typeName.lowercase() in primitives) {
            "type: ${typeName.lowercase()}"
        } else if (self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            ("type: array\nitems:\n" + toYaml(self.actualTypeArguments[0], stackMax - 1, describedTypes).indent("  ")
                    ).filterEmptyLines()
        } else if (self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            ("type: map\nkeys:\n" + toYaml(self.actualTypeArguments[0], stackMax - 1, describedTypes).indent("  ") +
                    "\nvalues:\n" + toYaml(self.actualTypeArguments[1], stackMax - 1, describedTypes).indent("  ")
                    ).filterEmptyLines()
        } else if (self.isArray) {
            ("type: array\nitems:\n" + toYaml(self.componentType!!, stackMax - 1, describedTypes).indent("  ")
                    ).filterEmptyLines()
        } else {
            describe(TypeToken.of(self).rawType, null, stackMax, describedTypes)
        }
    }

    private fun toYaml(self: KType, stackMax: Int, describedTypes: MutableSet<String>): String {
        if (isAbbreviated(self.javaType) || stackMax <= 0) return "type: object\nclass: \"$self\"".filterEmptyLines()
            .trim()
        val typeName = self.toString().substringAfterLast('.').replace('$', '.').lowercase(Locale.getDefault())
        if (typeName in primitives) {
            return "type: $typeName"
        }
        return toYaml(self.javaType, stackMax, describedTypes)
    }

    open fun getEnumValues(clazz: Class<*>): List<String> {
        return when {
            clazz.isEnum -> clazz.enumConstants
                .filter { if (it is EnabledStrategy) it.isEnabled() else true }
                .map { it.toString() }

            DynamicEnum::class.java.isAssignableFrom(clazz) -> {
                DynamicEnum.values(clazz as Class<out DynamicEnum<*>>)
                    .filter { if (it is EnabledStrategy) it.isEnabled() else true }
                    .map { it.name }
            }

            else -> emptyList()
        }
    }

    private fun String.filterEmptyLines() = this.split("\n").filter { it.isNotBlank() }.joinToString("\n").trim()
}

private fun String.indent(string: String) = this.lineSequence().map {
    when {
        it.isBlank() -> {
            when {
                it.length < string.length -> string
                else -> it
            }
        }

        else -> string + it
    }
}.joinToString("\n")
```

# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the generated `README.md` for the `com.simiacryptus.cognotik.describe` package.

### /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/README.md
```markdown
# Type Description System

The `com.simiacryptus.cognotik.describe` package provides a robust reflection-based framework for generating structured representations of Kotlin and Java types. These descriptions are primarily used to communicate data structures and API contracts to Large Language Models (LLMs) or to generate documentation.

## Core Components

### TypeDescriber
The abstract base class for all describers. It defines the common logic for:
- **Primitive Mapping**: Standardizes basic types (int, string, boolean, etc.).
- **Recursion Control**: Uses a `stackMax` and a set of `describedTypes` to prevent infinite loops in circular references.
- **Abbreviation**: Automatically truncates descriptions for standard library types (e.g., `java.*`, `kotlin.*`) to keep outputs concise.

### Implementations
- **`JsonDescriber`**: Generates a JSON-based schema representation. It includes a whitelist mechanism to restrict which classes are fully described for security and brevity.
- **`YamlDescriber`**: Produces a YAML representation similar to OpenAPI/Swagger schemas. It supports describing properties, methods, and registered subtypes.
- **`TypeScriptDescriber`**: Generates TypeScript interface and enum definitions, making it ideal for bridging the gap between backend types and frontend/LLM contexts.

### Specialized Describers
- **`AbbrevWhitelistTSDescriber` / `AbbrevWhitelistYamlDescriber`**: These classes allow you to provide a list of package prefixes that should *not* be abbreviated, ensuring detailed descriptions for specific internal modules while keeping others compact.

## Metadata and Annotations

### `@Description`
A runtime annotation that can be applied to classes, properties, or methods. Describers extract this value to provide human-readable (and LLM-readable) documentation within the generated output.

```kotlin
@Description("A user profile object")
data class User(
    @Description("The unique identifier") val id: Int,
    val name: String
)
```

## Advanced Features

### Polymorphism Support
The system supports describing polymorphic interfaces via `registerSubType`. When a parent class is encountered, the describer can include details about its known implementations.

```kotlin
describer.registerSubType(Shape::class.java, Circle::class.java)
describer.registerSubType(Shape::class.java, Square::class.java)
```

### Method Description
Describers can traverse class methods, documenting parameters and return types. This is particularly useful for generating "Tool" or "Function" definitions for LLM agents.

### Dynamic Enums
In addition to standard Java Enums, the system supports `DynamicEnum` and respects `EnabledStrategy` to filter available values at runtime.

## Utilities

- **`DescriptorUtil`**: A collection of reflection helpers for resolving generic types, finding annotations across Kotlin properties and constructor parameters, and handling array component types.
- **`MethodTypeDescriber`**: An interface that allows instances to provide dynamic type overrides for method parameters, useful when runtime types differ from static signatures.

## Usage Example

```kotlin
val describer = TypeScriptDescriber()
val description = describer.describe(MyDataClass::class.java)
println(description)
```
```

***

### Summary of Changes

- **Created `README.md`**: Established a comprehensive overview of the `com.simiacryptus.cognotik.describe` package.
- **Documented Core Classes**: Explained the roles of `TypeDescriber`, `JsonDescriber`, `YamlDescriber`, and `TypeScriptDescriber`.
- **Highlighted Features**: Detailed the recursion handling, abbreviation logic, and polymorphic subtype registration.
- **Annotation Usage**: Provided context for the `@Description` annotation and how it integrates with the reflection system.
- **Technical Details**: Mentioned `DescriptorUtil` and `MethodTypeDescriber` for advanced integration scenarios.

This documentation serves as a guide for developers using the Cognotik core library to generate type-safe schemas for LLM interactions or documentation.
</details>

                - <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/README.md'>/home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/README.md'>/home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/describe/README.md</a> Updated
