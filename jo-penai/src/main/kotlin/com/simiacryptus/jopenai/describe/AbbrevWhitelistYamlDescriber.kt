package com.simiacryptus.jopenai.describe

import com.simiacryptus.jopenai.describe.DescriptorUtil.componentType
import com.simiacryptus.jopenai.describe.DescriptorUtil.isArray
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

open class AbbrevWhitelistYamlDescriber(private vararg val abbreviated: String) : YamlDescriber() {
    override fun isAbbreviated(self: Type): Boolean {

        if (self.typeName in primitives) {

            return false
        } else if (self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>)) {

            return isAbbreviated(self.actualTypeArguments[0])
        } else if (self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>)) {

            return isAbbreviated(self.actualTypeArguments[0]) && isAbbreviated(self.actualTypeArguments[1])
        } else if (self.isArray) {

            return isAbbreviated(self.componentType!!)
        }
        val isAbbreviated = (abbreviated.find { self.typeName.startsWith(it) } == null) || super.isAbbreviated(self)

        return isAbbreviated
    }
}