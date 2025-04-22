package com.simiacryptus.skyenet.apps.code

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain

class AwsCodingApp : ApplicationServer(
    applicationName = "AWS Coding Assistant v1.1",
    path = "/aws",
) {
    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        val settings = getSettings<Settings>(session, user)
        val region = settings?.region
        val profile = settings?.profile
        CodingAgent(
            api = api,
            dataStorage = dataStorage,
            session = session,
            user = user,
            ui = ui,
            interpreter = KotlinInterpreter::class,
            symbols = getSymbols(region, profile),
            temperature = (settings?.temperature ?: 0.1),
            model = (settings?.model!!),
            mainTask = ui.newTask(),
        ).start(
            userMessage = userMessage,
        )
    }

    data class Settings(
        val region: String? = DefaultAwsRegionProviderChain().region.id(),
        val profile: String? = "default",
        val temperature: Double? = 0.1,
        val model: ChatModel = OpenAIModels.GPT4oMini,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T = Settings() as T

    companion object {

        fun getSymbols(region: String?, profile: String?) = mapOf(
            "awsRegion" to (region ?: DefaultAwsRegionProviderChain().region.id()),
            "awsProfile" to (profile ?: "default"),
        )
    }

}