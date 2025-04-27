package com.simiacryptus.cognotik.apps.parse

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.util.DynamicEnum
import com.simiacryptus.util.DynamicEnumDeserializer
import com.simiacryptus.util.DynamicEnumSerializer

@JsonDeserialize(using = ParsingModelTypeDeserializer::class)
@JsonSerialize(using = ParsingModelTypeSerializer::class)
class ParsingModelType<out T : ParsingModel<*>>(
    name: String,
    val modelClass: Class<out T>
) : DynamicEnum<ParsingModelType<*>>(name) {
    companion object {
        private val modelConstructors =
            mutableMapOf<ParsingModelType<*>, (ChatModel, Double) -> ParsingModel<*>>()

        val Document = ParsingModelType("Document", DocumentParsingModel::class.java)
        val Code = ParsingModelType("Code", CodeParsingModel::class.java)
        val Log = ParsingModelType("Log", LogDataParsingModel::class.java)

        init {
            registerConstructor(Document) { model, temp -> DocumentParsingModel(model, temp) }
            registerConstructor(Code) { model, temp -> CodeParsingModel(model, temp) }
            registerConstructor(Log) { model, temp -> LogDataParsingModel(model, temp) }
        }

        private fun <T : ParsingModel<*>> registerConstructor(
            modelType: ParsingModelType<T>,
            constructor: (ChatModel, Double) -> T
        ) {
            modelConstructors[modelType] = constructor
            register(modelType)
        }

        fun values() = values(ParsingModelType::class.java)

        fun getImpl(
            chatModel: ChatModel,
            temperature: Double,
            modelType: ParsingModelType<*>
        ): ParsingModel<*> {
            val constructor = modelConstructors[modelType]
                ?: throw RuntimeException("Unknown parsing model type: ${modelType.name}")
            return constructor(chatModel, temperature)
        }

        fun valueOf(name: String): ParsingModelType<*> = valueOf(ParsingModelType::class.java, name)
        private fun register(modelType: ParsingModelType<*>) = register(ParsingModelType::class.java, modelType)
    }
}

class ParsingModelTypeSerializer : DynamicEnumSerializer<ParsingModelType<*>>(ParsingModelType::class.java)
class ParsingModelTypeDeserializer : DynamicEnumDeserializer<ParsingModelType<*>>(ParsingModelType::class.java)