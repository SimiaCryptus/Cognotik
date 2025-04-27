package com.simiacryptus.jopenai.describe

import com.simiacryptus.jopenai.describe.DescriptorUtil.componentType
import com.simiacryptus.jopenai.describe.DescriptorUtil.isArray
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*

abstract class TypeDescriber {
    private val createdBy = Thread.currentThread().stackTrace

    abstract val markupLanguage: String
    abstract val methodBlacklist: Set<String>
    var coverMethods = true
    abstract fun describe(
        rawType: Class<in Nothing>,
        stackMax: Int = 10,
        describedTypes: MutableSet<String> = mutableSetOf()
    ): String

    abstract fun describe(self: Method, clazz: Class<*>? = null, stackMax: Int = 5): String
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
        private val log = LoggerFactory.getLogger(TypeDescriber::class.java)
    }
}