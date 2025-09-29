package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.OpenAIClient
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import java.util.concurrent.Executors

class IdeaOpenAIClient : OpenAIClient(
    key = "",
    apiBase = "",
    workPool = Executors.newCachedThreadPool(),
    scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(
        AppSettingsState.currentSession,
        UserSettingsManager.defaultUser
    ),
) {

    override fun onUsage(model: AIModel?, tokens: ModelSchema.Usage) {
        ApplicationServices.fileApplicationServices(AppSettingsState.Companion.pluginHome).usageManager.incrementUsage(
            AppSettingsState.currentSession,
            UserSettingsManager.defaultUser, model!!, tokens
        )
    }

    companion object {

        val instance by lazy {
            IdeaOpenAIClient()
        }
        val log = LoggerFactory.getLogger(IdeaOpenAIClient::class.java)
    }
}