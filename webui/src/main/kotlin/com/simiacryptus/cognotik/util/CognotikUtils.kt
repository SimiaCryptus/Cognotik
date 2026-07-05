package com.simiacryptus.cognotik.util

import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.FileApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.defaultUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.util.concurrent.Executors

object CognotikUtils {
  @JvmStatic
  fun fileApplicationServices(): FileApplicationServices {
    return ApplicationServices.fileApplicationServices(ApplicationServicesConfig.dataStorageRoot)
  }

  @JvmStatic
  fun user(): User {
    return defaultUser
  }

  @JvmStatic
  fun userSettings(): UserSettings {
    return fileApplicationServices().userSettingsManager.getUserSettings(user())
  }

  @JvmStatic
  fun relativize(root: File, file: File): File {
    return File(root.toURI().relativize(file.toURI()).getPath())
  }

  @JvmStatic
  fun getName(model: ApiChatModel): String? {
    return if (model.provider != null) model.provider.name else "null"
  }

  @JvmStatic
  fun getChatModel(chatModel: ChatModel): ApiChatModel {
    return ApiChatModel(chatModel, getApi(chatModel.provider))
  }

  @JvmStatic
  fun getApi(provider: APIProvider?): ApiData? {
    return getApi(if (provider != null) provider.name else null)
  }

  private val log: Logger = LoggerFactory.getLogger(CognotikUtils::class.java)

  @JvmStatic
  fun getInterface(model: ApiChatModel, session: Session): ChatInterface {
    val api = getApi(getName(model))

    val resolvedModel = model.model
    requireNotNull(resolvedModel) { "No model found for provider: " + getName(model) }

    val apiKey = if (api != null) api.key else null
    requireNotNull(apiKey) { "No API key found for provider: " + getName(model) }

    return resolvedModel.instance(
      apiKey,
      api?.apiBase ?: throw IllegalArgumentException("No API found for provider: ${model.provider?.name}"),
      Level.INFO,
      mutableListOf(),
      Executors.newCachedThreadPool(),
      1.0,
      MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
      session = session,
      user = user(),
    )
  }

  @JvmStatic
  fun getApi(providerName: String?): ApiData? {
    return userSettings().apis.stream()
      .filter { apiData: ApiData? ->
        if (apiData!!.provider == null) return@filter false
        apiData.provider.name == providerName
      }
      .findFirst().orElse(null)
  }

  @JvmStatic
  fun configureEnvironmentalKeys() {
    check(!APIProvider.values().isEmpty()) { "No API providers configured" }
    val userSettingsManager =
      ApplicationServices.fileApplicationServices(ApplicationServicesConfig.dataStorageRoot).userSettingsManager
    val user = user()
    val userSettings = userSettingsManager.getUserSettings(user)
    var anythingChanged = false
//    anythingChanged = anythingChanged or setProvider(userSettings, "GOOGLE_API_KEY", APIProvider.Gemini)
//    anythingChanged = anythingChanged or setProvider(userSettings, "OPENAI_API_KEY", APIProvider.OpenAI)
//    anythingChanged = anythingChanged or setProvider(userSettings, "ANTHROPIC_API_KEY", APIProvider.Anthropic)
//    anythingChanged = anythingChanged or setProvider(userSettings, "GROQ_API_KEY", APIProvider.Groq)
    if (anythingChanged) {
      log.info("Updating user settings with new API keys.")
      userSettingsManager.updateUserSettings(user, userSettings)
    } else {
      log.info("No API keys found in environment variables.")
    }
  }

  @JvmStatic
  fun setProvider(userSettings: UserSettings, keyName: String?, provider: APIProvider): Boolean {
    if (System.getenv(keyName) != null) {
      log.info("Configuring API key for provider: " + provider.name)
      val apis: MutableList<ApiData> = userSettings.apis
      // find any existing entry for this provider and remove it
      apis.removeIf { apiData: ApiData? -> apiData!!.provider!!.name == provider.name }
      // add new entry
      apis.add(
        ApiData(
          provider.name,
          SecureString(System.getenv(keyName)),
          provider.base,
          provider
        )
      )
      return true
    } else {
      return false
    }
  }
}