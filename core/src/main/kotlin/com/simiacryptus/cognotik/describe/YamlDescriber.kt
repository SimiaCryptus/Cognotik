package com.simiacryptus.cognotik.describe

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.common.reflect.TypeToken
import com.simiacryptus.cognotik.platform.Description
import com.simiacryptus.cognotik.describe.DescriptorUtil.componentType
import com.simiacryptus.cognotik.describe.DescriptorUtil.getAllAnnotations
import com.simiacryptus.cognotik.describe.DescriptorUtil.isArray
import com.simiacryptus.cognotik.describe.DescriptorUtil.resolveGenericType
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.EnabledStrategy
import org.slf4j.LoggerFactory
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

    val responseYaml =
      ("responses:\n  application/json:\n    schema:\n" + returnTypeYaml.indent("      ")).trim().filterEmptyLines()
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