package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.simiacryptus.cognotik.platform.model.ApiChatModel

/**
 * Custom serializer for ApiChatModel that only serializes the model name
 */
class ApiChatModelSerializer : JsonSerializer<ApiChatModel>() {
    override fun serialize(value: ApiChatModel?, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value == null) {
            gen.writeNull()
        } else {
            gen.writeString(value.model?.modelName ?: value.model?.name)
        }
    }
}