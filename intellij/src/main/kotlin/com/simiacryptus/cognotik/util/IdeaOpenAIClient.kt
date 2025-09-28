package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.OpenAIClient
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import org.apache.hc.core5.http.HttpRequest
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

    override fun onUsage(model: AIModel?, tokens: ApiModel.Usage) {
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