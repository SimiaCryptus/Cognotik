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