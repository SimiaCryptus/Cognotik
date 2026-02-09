package com.simiacryptus.cognotik

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simiacryptus.cognotik.audio.AudioModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.slf4j.Logger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService

open class TranscriptionClient(
    protected var key: String,
    protected val apiBase: String,
    logLevel: Level = Level.TRACE,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    workPool: ExecutorService,
    scheduledPool: ListeningScheduledExecutorService,
    val provider: APIProvider = APIProvider.Companion.OpenAI,
) : HttpClientManager(
    logLevel = logLevel,
    logStreams = logStreams,
    workPool = workPool,
    scheduledPool = scheduledPool
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TranscriptionClient::class.java)
    }

    protected fun post(request: HttpPost): String = withClient { EntityUtils.toString(it.execute(request).entity) }

    open fun transcription(wavAudio: ByteArray, prompt: String = "", audioModel: AudioModels): String =
        withReliability {
            withPerformanceLogging {
                val url = "${apiBase}/audio/transcriptions"
                val request = HttpPost(url)
                request.addHeader("Accept", "application/json")
                provider.authorize(request, key, apiBase)
                val entity = MultipartEntityBuilder.create()
                entity.setMode(HttpMultipartMode.EXTENDED)
                entity.addBinaryBody("file", wavAudio, ContentType.create("audio/x-wav"), "audio.wav")
                entity.addTextBody("model", audioModel.modelId)
                entity.addTextBody("response_format", "json")
                if (prompt.isNotEmpty()) entity.addTextBody("prompt", prompt)
                request.entity = entity.build()
                val response = post(request)
                log.info("Transcription response received")
                val jsonObject = Gson().fromJson(response, JsonObject::class.java)
                if (jsonObject.has("error")) {
                    val errorObject = jsonObject.getAsJsonObject("error")
                    throw RuntimeException(IOException(errorObject["message"].asString))
                }
                try {
                    val result =
                        JsonUtil.objectMapper().readValue(response, ModelSchema.TranscriptionResult::class.java)
                    result.text ?: ""
                } catch (e: Exception) {
                    jsonObject.get("text").asString ?: ""
                }
            }
        }

}