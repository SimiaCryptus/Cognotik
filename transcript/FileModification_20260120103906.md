# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/DistanceType.kt

```
package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.util.LoggerFactory
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class DistanceType {
    Euclidean {
        override fun distance(contentEmbedding: DoubleArray, promptEmbedding: DoubleArray): Double {
            log.debug("Calculating Euclidean distance")
            return sqrt(
                contentEmbedding.zip(promptEmbedding).map { (a, b) ->
                    (a - b).pow(2)
                }.sum()
            )
        }
    },
    Manhattan {
        override fun distance(contentEmbedding: DoubleArray, promptEmbedding: DoubleArray): Double {
            log.debug("Calculating Manhattan distance")
            return contentEmbedding.zip(promptEmbedding).map { (a, b) -> abs(a - b) }.sum()
        }
    },
    Cosine {
        override fun distance(contentEmbedding: DoubleArray, promptEmbedding: DoubleArray): Double {
            log.debug("Calculating Cosine distance")
            val dotProduct = contentEmbedding.zip(promptEmbedding).map { (a, b) -> a * b }.sum()
            val contentMagnitude = sqrt(contentEmbedding.map { it.pow(2) }.sum())
            val promptMagnitude = sqrt(promptEmbedding.map { it.pow(2) }.sum())
            return 1 - dotProduct / (contentMagnitude * promptMagnitude)
        }
    };

    companion object {
        private val log = LoggerFactory.getLogger(DistanceType::class.java)
    }

    abstract fun distance(
        contentEmbedding: DoubleArray,
        promptEmbedding: DoubleArray
    ): Double
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/EmbedderClient.kt

```
package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema

class EmbedderClient(
    private val embeddingClient: EmbeddingClientInterface,
    val model: EmbeddingModel,
    private val onUsage: (LLMModel, ModelSchema.Usage) -> Unit = { _, _ -> }
) : Embedder {
    override fun embed(input: String): DoubleArray {
        val request = ModelSchema.EmbeddingRequest(
            model = model.modelName,
            input = input
        )
        val response = embeddingClient.createEmbedding(request, model)
        if (response.data.isEmpty()) {
            throw IllegalStateException("No embedding data returned")
        }
        response.usage?.let { usage ->
            onUsage(model, usage.copy(cost = model.pricing(usage)))
        }
        EmbeddingModel.log.info("Generated embedding of size ${response.data[0].embedding?.size} for input of length ${input.length}")
        return response.data[0].embedding ?: throw IllegalStateException("Embedding data is null")
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/EmbeddingClientBase.kt

```
package com.simiacryptus.cognotik.embedding

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.HttpClientManager
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.Usage
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService

interface EmbeddingClientInterface {

    fun createEmbedding(
        request: ModelSchema.EmbeddingRequest,
        model: EmbeddingModel
    ): ModelSchema.EmbeddingResponse

}

abstract class SingleProviderEmbeddingClient(
    protected val provider: APIProvider,
    val apiKey: SecureString,
    val apiBase: String = provider.base!!,
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : EmbeddingClientBase(
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
)

abstract class EmbeddingClientBase(
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : HttpClientManager(
    logLevel = logLevel, logStreams = logStreams, workPool = workPool, scheduledPool = scheduledPool
), EmbeddingClientInterface {

    var session: Any? = null
    var user: Any? = null
    var budget: Number? = null

    @Throws(IOException::class, InterruptedException::class)
    fun post(
        url: String,
        json: String,
        apiProvider: APIProvider,
        requestID: String = UUID.randomUUID().toString()
    ): String {
        validatePostRequest(url, json)
        val request = HttpPost(url)
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        authorize(request, apiProvider)
        request.entity = StringEntity(json, Charsets.UTF_8, false)
        return post(request, requestID = requestID)
    }

    private fun validatePostRequest(url: String, json: String) {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(json.isNotBlank()) { "JSON payload cannot be blank" }
        require(url.startsWith("http")) { "URL must be a valid HTTP/HTTPS URL: $url" }
    }

    abstract fun authorize(request: HttpRequest, apiProvider: APIProvider)

    fun post(request: HttpPost, requestID: String = UUID.randomUUID().toString()): String = try {
        withClient<String> {
            log(
                level = Level.DEBUG,
                msg = String.format(
                    "POST %s\nID:%s\nPrefix:\n\t%s\n```\n%s\n```\n",
                    request.uri,
                    requestID,
                    formatEntityForLogging(request.entity),
                    captureCallerStack().indent("  ")
                )
            )
            val response = it.execute(request)
            val entity = response.entity
            if (entity != null) {
                val responseBody = EntityUtils.toString(entity)
                if (responseBody.isBlank()) {
                    throw IOException("Empty response body")
                }
                responseBody
            } else {
                throw IOException("Empty response entity")
            }
        }
    } catch (e: Exception) {
        log.error("Failed to execute POST request to ${request.uri}", e)
        throw e
    }

    private fun formatEntityForLogging(entity: org.apache.hc.core5.http.HttpEntity?): String {
        return try {
            EntityUtils.toString(entity)?.lineSequence()?.map {
                when {
                    it.isBlank() -> if (it.length < "\t".length) "\t" else it
                    else -> "\t$it"
                }
            }?.joinToString("\n").orEmpty()
        } catch (e: Exception) {
            log.warn("Failed to format entity for logging", e)
            "[Unable to format entity for logging]"
        }
    }

    fun onUsage(model: EmbeddingModel, tokens: Usage) {
        log.debug("Usage recorded for session: {}, user: {}, model: {}, tokens: {}", session, user, model, tokens)
    }

    inner class ChildClient() : EmbeddingClientBase(
        logLevel = Level.DEBUG,
        workPool = workPool,
        logStreams = logStreams,
        scheduledPool = scheduledPool
    ) {
        init {
            session = this@EmbeddingClientBase.session
            user = this@EmbeddingClientBase.user
        }

        override fun log(
            level: Level,
            msg: String,
            logStreams: MutableList<BufferedOutputStream>,
            format: Boolean
        ) {
            super.log(level, msg, logStreams, format)
            this@EmbeddingClientBase.log(level, msg)
        }

        override fun authorize(
            request: HttpRequest,
            apiProvider: APIProvider
        ) {
            this@EmbeddingClientBase.authorize(request, apiProvider)
        }

        override fun createEmbedding(
            request: ModelSchema.EmbeddingRequest,
            model: EmbeddingModel
        ): ModelSchema.EmbeddingResponse {
            return this@EmbeddingClientBase.createEmbedding(request, model)
        }

    }

    fun getChildClient(): ChildClient = ChildClient()

    companion object {
        val log = LoggerFactory.getLogger(EmbeddingClientBase::class.java)
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/EmbeddingModel.kt

```
package com.simiacryptus.cognotik.embedding

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.encrypt
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

interface Embedder {
    fun embed(input: String): DoubleArray
}

@JsonDeserialize(using = EmbeddingModelsDeserializer::class)
@JsonSerialize(using = EmbeddingModelsSerializer::class)
open class EmbeddingModel(
    modelName: String = "",
    maxTokens: Int = 0,
    provider: APIProvider? = null,
    private val tokenPricePerK: Double = 0.0,
) : LLMModel(
    modelName = modelName,
    provider = provider,
    maxTotalTokens = maxTokens
) {
    private val log = LoggerFactory.getLogger(EmbeddingModel::class.java)
    override fun toString() = modelName

    override fun pricing(usage: ModelSchema.Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0
        .also { log.info("Calculated pricing for model: $modelName with prompt tokens: ${usage.prompt_tokens}, price: $it") }

    fun instance(
        key: SecureString = "".encrypt,
        base: String = provider?.base ?: "",
        logLevel: Level = Level.DEBUG,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        workPool: ExecutorService = java.util.concurrent.Executors.newFixedThreadPool(8),
        scheduledPool: ListeningScheduledExecutorService = com.google.common.util.concurrent.MoreExecutors.listeningDecorator(
            java.util.concurrent.Executors.newScheduledThreadPool(1)
        ),
        onUsage: (LLMModel, ModelSchema.Usage) -> Unit = { _, _ -> },
    ): EmbedderClient {
        val client = provider?.getEmbeddingClient(
            key = key,
            base = base,
            workPool = workPool,
            logLevel = logLevel,
            logStreams = logStreams,
            scheduledPool = scheduledPool
        ) ?: throw IllegalArgumentException("Unsupported provider: $provider")

        return EmbedderClient(client, this, onUsage)
    }

    companion object {
        val log = LoggerFactory.getLogger(EmbeddingModel::class.java)

        fun values(): Map<String, EmbeddingModel> =
            values.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()

        private val values: MutableMap<String, EmbeddingModel?> by lazy {
            (OpenAIEmbeddingModels.values +
                    OllamaEmbeddingModels.values).toMutableMap()
        }

        init {
            log.info("Initializing EmbeddingModels with predefined models")
        }
    }
}

class EmbeddingModelsSerializer : StdSerializer<EmbeddingModel>(EmbeddingModel::class.java) {
    override fun serialize(value: EmbeddingModel, gen: JsonGenerator, provider: SerializerProvider) {
        val modelKey = EmbeddingModel.values().entries.find { it.value == value }?.key
        gen.writeString(modelKey ?: value.modelName)
    }
}

class EmbeddingModelsDeserializer : JsonDeserializer<EmbeddingModel>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EmbeddingModel {
        return when (p.currentToken) {
            JsonToken.VALUE_STRING -> {
                val modelName = p.readValueAs(String::class.java)
                EmbeddingModel.values().entries.find {
                    it.key == modelName || it.value.modelName == modelName
                }?.value ?: throw IllegalArgumentException("Unknown embedding model: $modelName")
            }

            JsonToken.START_OBJECT -> {
                val node = p.readValueAsTree<JsonNode>()
                val modelName = node.get("modelName")?.asText() ?: node.get("name")?.asText()
                ?: throw IllegalArgumentException("Object format must contain 'modelName' or 'name' field")
                EmbeddingModel.values().entries.find {
                    it.key == modelName || it.value.modelName == modelName
                }?.value ?: throw IllegalArgumentException("Unknown embedding model: $modelName")
            }

            else -> throw IllegalArgumentException("EmbeddingModel must be deserialized from either a string or an object")
        }
    }


}


```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/OllamaEmbeddingClient.kt

```
package com.simiacryptus.cognotik.embedding

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.encrypt
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class OllamaEmbeddingClient(
    apiKey: SecureString = "".encrypt,
    apiBase: String = "http://localhost:11434",
    workPool: ExecutorService = Executors.newCachedThreadPool(),
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(
        Executors.newScheduledThreadPool(
            1
        )
    ),
) : SingleProviderEmbeddingClient(
    provider = APIProvider.valueOf("Ollama"),
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
) {

    override fun authorize(
        request: org.apache.hc.core5.http.HttpRequest,
        apiProvider: APIProvider
    ) {
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        // Ollama typically doesn't require authorization for local instances
        val apiKey = this.apiKey.decrypt
        if (apiKey.isNotBlank()) {
            request.addHeader("Authorization", "Bearer $apiKey")
        }
    }

    override fun createEmbedding(
        request: ModelSchema.EmbeddingRequest,
        model: EmbeddingModel
    ): ModelSchema.EmbeddingResponse {
        validateEmbeddingRequest(request, model)

        return withReliability {
            withPerformanceLogging {
                // Convert OpenAI-style request to Ollama format
                val ollamaRequest = mapOf(
                    "model" to (request.model ?: model.modelName),
                    "prompt" to when {
                        request.input is String -> request.input
                        else -> request.input.toString()
                    }
                )

                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(ollamaRequest)

                val rawResponse = post("$apiBase/api/embeddings", json, provider)
                checkError(rawResponse)

                // Parse Ollama response and convert to OpenAI format
                val ollamaResponse = JsonUtil.objectMapper().readValue(rawResponse, Map::class.java)
                val embeddings = ollamaResponse["embedding"] as? List<Double>
                    ?: throw IllegalStateException("No embeddings found in response")

                val response = ModelSchema.EmbeddingResponse(
                    data = listOf(
                        ModelSchema.EmbeddingData(
                            embedding = embeddings.toDoubleArray(),
                            index = 0,
                            `object` = "embedding"
                        )
                    ),
                    model = request.model ?: model.modelName,
                    `object` = "list",
                    usage = ModelSchema.Usage(
                        prompt_tokens = estimateTokens(request.input.toString()).toLong(),
                        total_tokens = estimateTokens(request.input.toString()).toLong(),
                        completion_tokens = 0
                    )
                )

                if (response.usage != null) {
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!)
                }

                response
            }
        }
    }

    private fun validateEmbeddingRequest(request: ModelSchema.EmbeddingRequest, model: EmbeddingModel) {
        require(request.input.toString().isNotBlank()) { "Embedding request input cannot be blank" }
        require(model.modelName.isNotBlank()) { "Model name cannot be blank" }
    }

    private fun estimateTokens(text: String): Int {
        // Simple token estimation - roughly 4 characters per token
        return (text.length / 4).coerceAtLeast(1)
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/OllamaEmbeddingModels.kt

```
package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.models.APIProvider

object OllamaEmbeddingModels {
    val NomicEmbedText = EmbeddingModel("nomic-embed-text", 2049, APIProvider.Companion.Ollama, 0.00013)
    val values: Map<String, EmbeddingModel> = mapOf(
        "nomic-embed-text" to NomicEmbedText,
    )
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/OpenAIEmbeddingClient.kt

```
package com.simiacryptus.cognotik.embedding

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.encrypt
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OpenAIEmbeddingClient(
    apiKey: SecureString = "".encrypt,
    apiBase: String = "https://api.openai.com/v1",
    workPool: ExecutorService = Executors.newCachedThreadPool(),
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(
        Executors.newScheduledThreadPool(
            1
        )
    )
) : SingleProviderEmbeddingClient(
    provider = APIProvider.valueOf("OpenAI"),
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
) {

    override fun authorize(
        request: org.apache.hc.core5.http.HttpRequest,
        apiProvider: APIProvider
    ) {
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        val apiKey = this.apiKey.decrypt
        if (apiKey.isNotBlank()) {
            request.addHeader("Authorization", "Bearer $apiKey")
        } else {
            throw IllegalStateException("OpenAI API key is required")
        }
    }

    override fun createEmbedding(
        request: ModelSchema.EmbeddingRequest,
        model: EmbeddingModel
    ): ModelSchema.EmbeddingResponse {
        validateEmbeddingRequest(request, model)

        return withReliability {
            withPerformanceLogging {
                // OpenAI embedding request format
                val openAIRequest = mapOf(
                    "model" to (request.model ?: model.modelName),
                    "input" to when {
                        request.input is String -> request.input
                        request.input is List<*> -> request.input
                        else -> listOf(request.input.toString())
                    },
                    "encoding_format" to "float"
                )

                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(openAIRequest)

                val rawResponse = post("$apiBase/embeddings", json, provider)
                checkError(rawResponse)

                // Parse OpenAI response
                val response = JsonUtil.objectMapper().readValue(rawResponse, ModelSchema.EmbeddingResponse::class.java)

                // Validate response
                if (response.data.isEmpty()) {
                    throw IllegalStateException("No embeddings found in response")
                }

                // Update usage with cost calculation
                if (response.usage != null) {
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!)
                }

                response
            }
        }
    }

    private fun validateEmbeddingRequest(request: ModelSchema.EmbeddingRequest, model: EmbeddingModel) {
        require(request.input.toString().isNotBlank()) { "Embedding request input cannot be blank" }
        require(model.modelName.isNotBlank()) { "Model name cannot be blank" }
        val apiKey = this.apiKey.decrypt
        require(apiKey.isNotBlank()) { "OpenAI API key is required" }
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/OpenAIEmbeddingModels.kt

```
package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.models.APIProvider

object OpenAIEmbeddingModels {
    val TextEmbeddingAda002 = EmbeddingModel("text-embedding-ada-002", 8191, APIProvider.Companion.OpenAI, 0.0001)
    val TextEmbedding3Small = EmbeddingModel("text-embedding-3-small", 8191, APIProvider.Companion.OpenAI, 0.00002)
    val TextEmbedding3Large = EmbeddingModel("text-embedding-3-large", 8191, APIProvider.Companion.OpenAI, 0.00013)

    val values: Map<String, EmbeddingModel> = mapOf(
        "text-embedding-ada-002" to TextEmbeddingAda002,
        "text-embedding-3-small" to TextEmbedding3Small,
        "text-embedding-3-large" to TextEmbedding3Large,
    )
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/PromptOptimization.kt

```
package com.simiacryptus.cognotik.embedding

import com.simiacryptus.cognotik.agents.ProxyAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.util.toContentList
import org.slf4j.LoggerFactory
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

open class PromptOptimization(
    /*EmbeddingModel*/
    val embeddingModel: EmbeddingModel,
    /*EmbeddingClientInterface*/
    val embeddingClient: EmbeddingClientInterface,
    val model: ChatInterface,
    private val mutationRate: Double = 0.5,
    private val mutationTypes: Map<String, Double> = mapOf(
        "Rephrase" to 1.0,
        "Randomize" to 1.0,
        "Summarize" to 1.0,
        "Expand" to 1.0,
        "Reorder" to 1.0,
        "Remove Duplicate" to 1.0,
    )
) {

    data class TestCase(val turns: List<Turn>, val retries: Int = 3)

    data class Turn(val userMessage: String, val expectations: List<Expectation>)

    open fun runGeneticGenerations(
        systemPrompts: List<String>,
        testCases: List<TestCase>,
        selectionSize: Int = max(ceil(ln((systemPrompts.size + 1).toDouble()) / ln(2.0)), 3.0)
            .toInt(), // apx ln(N)
        populationSize: Int = max(max(selectionSize, 5), systemPrompts.size),
        generations: Int = 3
    ): List<String> {
        var topPrompts = regenerate(systemPrompts, populationSize)
        for (generation in 0..generations) {
            val scores = topPrompts.map { prompt ->
                prompt to testCases.map { testCase ->
                    evaluate(prompt, testCase)
                }.average()
            }
            if (log.isDebugEnabled) {
                scores.sortedByDescending { it.second }.forEach {
                    log.debug("Scored {}: {}", it.second, it.first.replace("\n", "\\n"))
                }
            }
            if (generation == generations) {
                log.info("Final generation: {}", topPrompts.first())
                break
            } else {
                val survivors = scores.sortedByDescending { it.second }.take(selectionSize).map { it.first }
                topPrompts = regenerate(survivors, populationSize)
                log.info("Generation {}: {}", generation, topPrompts.first())
            }
        }
        return topPrompts
    }

    open fun regenerate(progenetors: List<String>, desiredCount: Int): List<String> {
        val result = listOf<String>().toMutableList()
        result += progenetors
        while (result.size < desiredCount) {
            if (progenetors.size == 1) {
                val selected = progenetors.first()
                val mutated = mutate(selected)
                result += mutated
            } else if (progenetors.size == 0) {
                throw RuntimeException("No survivors")
            } else {
                val a = progenetors.random()
                var b: String
                do {
                    b = progenetors.random()
                } while (a == b)
                val child = recombine(a, b)
                result += child
            }
        }
        return result
    }

    open fun recombine(a: String, b: String): String {
        val temperature = 0.3
        for (retry in 0..3) {
            try {
                val child = geneticApi(temperature.pow(1.0 / (retry + 1))).recombine(Prompt(a), Prompt(b)).prompt
                if (child.contentEquals(a) || child.contentEquals(b)) {
                    log.info("Recombine failure: retry {}", retry)
                    continue
                }
                if (Math.random() < mutationRate) {
                    return mutate(child)
                } else {
                    return child
                }
            } catch (e: Exception) {
                log.warn("Failed to recombine {} + {}", a, b, e)
            }
        }
        return a // Return the original prompt if recombination fails
    }

    open fun mutate(selected: String): String {
        val temperature = 0.3
        for (retry in 0..10) {
            try {
                val directive = getMutationDirective()
                val mutated = geneticApi(temperature.pow(1.0 / (retry + 1))).mutate(Prompt(selected), directive).prompt
                if (mutated.contentEquals(selected)) {
                    log.info("Mutate failure {} ({}): {}", retry, directive, selected.replace("\n", "\\n"))
                    continue
                }
                return mutated
            } catch (e: Exception) {
                log.warn("Failed to mutate {}", selected, e)
            }
        }
        throw RuntimeException("Failed to mutate $selected after multiple retries")
    }

    open fun getMutationDirective(): String {
        val fate = mutationTypes.values.sum() * Math.random()
        var cumulative = 0.0
        for ((key, value) in mutationTypes) {
            cumulative += value
            if (fate < cumulative) {
                return key
            }
        }
        return mutationTypes.keys.random()
    }

    protected interface GeneticApi {
        @Description("Mutate the given prompt; rephrase, make random edits, etc.")
        fun mutate(
            systemPrompt: Prompt,
            directive: String = "Rephrase"
        ): Prompt

        @Description("Recombine the given prompts to produce a third with about the same length; swap phrases, reword, etc.")
        fun recombine(
            systemPromptA: Prompt,
            systemPromptB: Prompt
        ): Prompt

    }

    data class Prompt(
        val prompt: String
    )

    protected open fun geneticApi(temperature: Double = 0.3) = ProxyAgent(
        clazz = GeneticApi::class.java,
        model = model,
        temperature = temperature
    ).create()

    open fun evaluate(systemPrompt: String, testCase: TestCase): Double {
        val steps = run(systemPrompt, testCase)
        return steps.map { it.second }.average()
    }

    open fun run(
        systemPrompt: String,
        testCase: TestCase
    ): List<Pair<ChatResponse, Double>> {
        var chatRequest = ChatRequest(
            model = model.modelType.modelName
        )
        var response = ChatResponse()
        chatRequest = chatRequest.copy(
            messages = chatRequest.messages + ChatMessage(
                Role.system,
                systemPrompt.toContentList()
            )
        )
        return testCase.turns.map { turn ->
            var matched: Boolean
            chatRequest = chatRequest.copy(
                messages = chatRequest.messages + ChatMessage(
                    Role.user,
                    turn.userMessage.toContentList()
                )
            )
            val startTemp = 0.3
            chatRequest = chatRequest.copy(temperature = startTemp)
            for (retry in 0..testCase.retries) {
                response = model.chat(chatRequest.messages)
                matched = turn.expectations.all {
                    it.matches(
                        embeddingModel,
                        embeddingClient,
                        response
                    )
                }
                if (matched) {
                    break
                } else {
                    chatRequest = chatRequest.copy(temperature = startTemp.coerceAtLeast(0.1).pow(1.0 / (retry + 1)))
                    log.info(
                        "Retry {} (T={}): {} / {}\n\t{}", retry, "%.3f".format(chatRequest.temperature),
                        systemPrompt.replace("\n", "\\n"), turn.userMessage,
                        response.choices.first().message?.content?.replace("\n", "\n\t")
                    )
                }
            }
            chatRequest = chatRequest.copy(
                messages = chatRequest.messages + ChatMessage(
                    Role.assistant,
                    (response.choices.first().message?.content ?: "").toContentList()
                )
            )
            response to turn.expectations.map {
                it.score(
                    embeddingModel,
                    embeddingClient,
                    response
                )
            }.average()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PromptOptimization::class.java)
    }

}


abstract class Expectation {
    companion object {
        private val log = LoggerFactory.getLogger(Expectation::class.java)
    }

    abstract fun matches(
        embeddingModel: EmbeddingModel,
        embeddingClient: EmbeddingClientInterface,
        response: ChatResponse
    ): Boolean

    abstract fun score(
        embeddingModel: EmbeddingModel,
        embeddingClient: EmbeddingClientInterface,
        response: ChatResponse
    ): Double


}

```

# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the embedding module.

### /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/README.md
```markdown
# Embedding Module

The `com.simiacryptus.cognotik.embedding` package provides a robust framework for generating and working with text embeddings. It supports multiple providers, distance metrics, and advanced prompt optimization techniques.

## Key Components

### Core Interfaces and Classes

*   **`Embedder`**: A simple interface for converting text into vector representations (`DoubleArray`).
*   **`EmbeddingModel`**: A base class representing specific embedding models (e.g., OpenAI's `text-embedding-3-small`). It handles model metadata, pricing calculations, and serialization.
*   **`DistanceType`**: An enum providing implementations for common vector distance metrics:
    *   **Euclidean**: Standard straight-line distance.
    *   **Manhattan**: Sum of absolute differences.
    *   **Cosine**: Measures the cosine of the angle between vectors (useful for semantic similarity).
*   **`EmbedderClient`**: The primary implementation of the `Embedder` interface, coordinating between a specific model and an API client.

### API Clients

The module uses a provider-based architecture for API interactions:
*   **`EmbeddingClientBase`**: Provides shared logic for HTTP communication, logging, and usage tracking.
*   **`OpenAIEmbeddingClient`**: Implementation for the OpenAI Embeddings API.
*   **`OllamaEmbeddingClient`**: Implementation for local Ollama instances, allowing for private, local embedding generation.

### Prompt Optimization

The **`PromptOptimization`** class implements a genetic algorithm to evolve and improve system prompts. It uses:
*   **Mutation**: Rephrasing, summarizing, or expanding prompts.
*   **Recombination**: Combining successful prompts to create better descendants.
*   **Evaluation**: Scoring prompts based on test cases and expectations.

## Supported Models

### OpenAI
Predefined models available in `OpenAIEmbeddingModels`:
*   `text-embedding-ada-002`
*   `text-embedding-3-small`
*   `text-embedding-3-large`

### Ollama
Predefined models available in `OllamaEmbeddingModels`:
*   `nomic-embed-text`

## Usage Example

### Generating Embeddings

```kotlin
val model = OpenAIEmbeddingModels.TextEmbedding3Small
val embedder = model.instance(apiKey = "your_api_key".encrypt)

val vector = embedder.embed("Hello, world!")
```

### Calculating Similarity

```kotlin
val vec1 = embedder.embed("The cat sat on the mat.")
val vec2 = embedder.embed("A feline rested on the rug.")

val distance = DistanceType.Cosine.distance(vec1, vec2)
println("Cosine Distance: $distance")
```

## Implementation Details

*   **Serialization**: `EmbeddingModel` includes custom Jackson serializers/deserializers to allow models to be easily used in configuration files or API requests.
*   **Reliability**: Clients are designed to work with `HttpClientManager` to provide performance logging and reliability features.
*   **Cost Tracking**: The `onUsage` callback allows applications to track token consumption and costs in real-time.
```

### Summary of Changes
- Created a comprehensive `README.md` for the `com.simiacryptus.cognotik.embedding` package.
- Documented the core interfaces (`Embedder`, `EmbeddingModel`) and utility enums (`DistanceType`).
- Explained the provider architecture (OpenAI and Ollama).
- Highlighted the `PromptOptimization` feature which uses genetic algorithms for prompt engineering.
- Provided clear usage examples for embedding generation and distance calculation.
- Included implementation details regarding serialization and cost tracking.
</details>

                - <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/README.md'>/home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/README.md'>/home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/README.md</a> Updated
