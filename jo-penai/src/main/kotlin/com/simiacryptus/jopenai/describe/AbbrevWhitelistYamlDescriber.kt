package com.simiacryptus.jopenai.describe

import com.simiacryptus.jopenai.describe.DescriptorUtil.componentType
import com.simiacryptus.jopenai.describe.DescriptorUtil.isArray
import org.slf4j.LoggerFactory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

open class AbbrevWhitelistYamlDescriber(private vararg val abbreviated: String) : YamlDescriber() {
    private val log = LoggerFactory.getLogger(AbbrevWhitelistYamlDescriber::class.java)
    override fun isAbbreviated(self: Type): Boolean {
//        log.info("Checking if type is abbreviated: {}", self.typeName)
        if (self.typeName in primitives) {
//            log.info("Type is a primitive: {}", self.typeName)
            return false
        } else if (self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>)) {
//            log.info("Type is a parameterized List: {}", self.typeName)
            return isAbbreviated(self.actualTypeArguments[0])
        } else if (self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>)) {
//            log.info("Type is a parameterized Map: {}", self.typeName)
            return isAbbreviated(self.actualTypeArguments[0]) && isAbbreviated(self.actualTypeArguments[1])
        } else if (self.isArray) {
//            log.info("Type is an array: {}", self.typeName)
            return isAbbreviated(self.componentType!!)
        }
        val isAbbreviated = (abbreviated.find { self.typeName.startsWith(it) } == null) || super.isAbbreviated(self)
//        log.info("Type {} is abbreviated: {}", self.typeName, isAbbreviated)
        return isAbbreviated
    }
}