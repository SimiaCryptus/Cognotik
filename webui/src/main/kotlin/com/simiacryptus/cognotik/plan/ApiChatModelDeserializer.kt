package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel

/**
 * Custom deserializer for ApiChatModel that resolves the model from its name
 */
class ApiChatModelDeserializer : JsonDeserializer<ApiChatModel>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ApiChatModel? {
    val modelName = p.readValueAs(String::class.java) ?: return null
    val userSettings = ApplicationServices
      .fileApplicationServices()
      .userSettingsManager
      .getUserSettings()
    val model = userSettings.apis.flatMap {
      it.provider?.getChatModels(it.key ?: "", it.baseUrl) ?: listOf()
    }.firstOrNull {
      it.modelName == modelName || it.name == modelName
    }
    if (model == null) {
      throw IllegalStateException("No API model found for model $modelName")
    }
    val apiData = userSettings.apis.firstOrNull {
      it.provider == model.provider
    }
    if (apiData == null) {
      throw IllegalStateException("No API data found for model $modelName")
    }
    return ApiChatModel(model, apiData)
  }
}