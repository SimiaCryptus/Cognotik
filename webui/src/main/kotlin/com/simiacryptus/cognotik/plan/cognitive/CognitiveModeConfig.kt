package com.simiacryptus.cognotik.plan.cognitive

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase

@JsonTypeIdResolver(CognitiveModeConfig.TypeIdResolver::class)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CUSTOM,
    property = "type",
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    visible = true
)
open class CognitiveModeConfig(
    var type: CognitiveModeType<*>? = null
) {
    class TypeIdResolver : TypeIdResolverBase() {
        override fun idFromValue(value: Any): String? {
            return (value as? CognitiveModeConfig)?.type?.name
        }

        override fun idFromValueAndType(value: Any, suggestedType: Class<*>): String? {
            return idFromValue(value)
        }

        override fun typeFromId(context: DatabindContext, id: String): JavaType {
            val type = CognitiveModeType.valueOf(id)
            return context.constructType(type.configClass)
        }

        override fun getMechanism(): JsonTypeInfo.Id {
            return JsonTypeInfo.Id.CUSTOM
        }
    }
}