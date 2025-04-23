package com.simiacryptus.util.stm

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

class Serialization {
    private val objectMapper: ObjectMapper
        get() {
            ObjectMapper.findModules()
            val mapper = ObjectMapper()
            return mapper
        }

    fun toJson(obj: Any) = objectMapper.writeValueAsBytes(obj)

    fun <T : Any> fromJson(
        data: ByteArray,
        typeInfo: TypeReference<T>
    ) = objectMapper.readValue(data, typeInfo)

    companion object {
        private val log = LoggerFactory.getLogger(Serialization::class.java)
    }
}

