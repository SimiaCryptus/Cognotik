package com.simiacryptus.cognotik.apps.parse

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.jopenai.chat.ChatClientInterface
import com.simiacryptus.jopenai.chat.model.ChatModelType
import com.simiacryptus.util.DynamicEnum
import com.simiacryptus.util.DynamicEnumDeserializer
import com.simiacryptus.util.DynamicEnumSerializer

@JsonDeserialize(using = ParsingModelTypeDeserializer::class)
@JsonSerialize(using = ParsingModelTypeSerializer::class)
class ParsingModelType<out T : ParsingModel<*>>(
    name: String
) : DynamicEnum<ParsingModelType<*>>(name) {
    companion object {

        private val modelConstructors =
            mutableMapOf<ParsingModelType<*>, (ChatModelType, Double, ChatClientInterface) -> ParsingModel<*>>()

        val Document = ParsingModelType<ParsingModel<*>>("Document")
        val Code = ParsingModelType<ParsingModel<*>>("Code")
        val Log = ParsingModelType<ParsingModel<*>>("Log")
       val RawText = ParsingModelType<ParsingModel<*>>("RawText")

        init {
            registerConstructor(Document) { model, temp, api -> DocumentParsingModel(model, temp, api) }
            registerConstructor(Code) { model, temp, api -> CodeParsingModel(model, temp, api) }
            registerConstructor(Log) { model, temp, api -> LogDataParsingModel(model, temp, api) }
           registerConstructor(RawText) { model, temp, api -> RawTextParsingModel(api) }
        }

        private fun <T : ParsingModel<*>> registerConstructor(
            modelType: ParsingModelType<T>,
            constructor: (ChatModelType, Double, ChatClientInterface) -> T
        ) {
            modelConstructors[modelType] = constructor
            register(modelType)
        }

        fun values() = values(ParsingModelType::class.java)

        fun getImpl(
            chatModel: ChatModelType,
            temperature: Double,
            modelType: ParsingModelType<*>,
            api: ChatClientInterface
        ): ParsingModel<*> {
            val constructor = modelConstructors[modelType]
                ?: throw RuntimeException("Unknown parsing model type: ${modelType.name}")
            return constructor(chatModel, temperature, api)
        }

        fun valueOf(name: String): ParsingModelType<*> = valueOf(ParsingModelType::class.java, name)
        private fun register(modelType: ParsingModelType<*>) = register(ParsingModelType::class.java, modelType)
    }
}

class ParsingModelTypeSerializer : DynamicEnumSerializer<ParsingModelType<*>>(ParsingModelType::class.java)
class ParsingModelTypeDeserializer : DynamicEnumDeserializer<ParsingModelType<*>>(ParsingModelType::class.java)