package com.simiacryptus.cognotik.image

 import com.simiacryptus.cognotik.models.ModelSchema
 import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.OpenAIClient
import com.google.common.util.concurrent.MoreExecutors
import org.slf4j.event.Level
import java.util.concurrent.Executors

class ImageClient(
    apiKey: String,
    private val apiBase: String
) {
    private val client = OpenAIClient(
        key = apiKey,
        apiBase = apiBase,
        workPool = MoreExecutors.newDirectExecutorService(),
        scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
        logLevel = Level.INFO
    )

    fun generate(
        request: ModelSchema.ImageGenerationRequest,
        model: ImageModel
    ): ModelSchema.ImageGenerationResponse {
        return client.createImage(request)
    }

    companion object {
        private val clients = mutableMapOf<String, ImageClient>()

        @Synchronized
        fun getClient(
            apiKey: String,
            apiBase: String,
            provider: APIProvider
        ): ImageClient {
            val key = "$provider:$apiBase"
            return clients.getOrPut(key) {
                ImageClient(apiKey, apiBase)
            }
        }

        fun generate(
            request: ModelSchema.ImageGenerationRequest,
            model: ImageModel,
            apiKey: String
        ): ModelSchema.ImageGenerationResponse {
            val client = getClient(apiKey, model.provider?.base!!, model.provider)
            return client.generate(request, model)
        }
    }
}