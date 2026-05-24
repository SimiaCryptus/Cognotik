package com.simiacryptus.cognotik.chat.model

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.TokenTypes
import com.simiacryptus.cognotik.models.ModelSchema.Usage
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ChatModel(
    val name: String = "",
    modelId: String = name,
    maxTotalTokens: Int = -1,
    maxOutTokens: Int = maxTotalTokens,
    provider: APIProvider? = null,
    /**
     * Pricing per 1k tokens, keyed by token type.
     * Any token type not present in this map will be treated as having an average price
     * (computed from the prices that are present) when encountered in usage.
     */
    val tokenPricingPerK: Map<TokenTypes, Double> = emptyMap(),
    val supportsTemperature: Boolean = true,
    val supportsReasoning: Boolean = false,
    val deprecated: Boolean = false,
    val inputModalities: Set<ChatMessageModality>,
    val outputModalities: Set<ChatMessageModality>,
) : LLMModel(
    modelId = modelId,
    maxTotalTokens = maxTotalTokens,
    maxOutTokens = maxOutTokens,
    provider = provider,
) {

    /**
     * Backwards-compatible constructor using explicit prompt/completion prices.
     */
    constructor(
        name: String = "",
        modelId: String = name,
        maxTotalTokens: Int = -1,
        maxOutTokens: Int = maxTotalTokens,
        provider: APIProvider? = null,
        inputTokenPricePerK: Double = 0.0,
        outputTokenPricePerK: Double = inputTokenPricePerK,
        supportsTemperature: Boolean = true,
        supportsReasoning: Boolean = false,
        deprecated: Boolean = false,
        inputModalities: Set<ChatMessageModality>,
        outputModalities: Set<ChatMessageModality>,
    ) : this(
        name = name,
        modelId = modelId,
        maxTotalTokens = maxTotalTokens,
        maxOutTokens = maxOutTokens,
        provider = provider,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to inputTokenPricePerK,
            TokenTypes.Completion to outputTokenPricePerK,
        ),
        supportsTemperature = supportsTemperature,
        supportsReasoning = supportsReasoning,
        deprecated = deprecated,
        inputModalities = inputModalities,
        outputModalities = outputModalities,
    )

    /**
     * Backwards-compatible accessor for the prompt (input) token price per 1k tokens.
     */
    val inputTokenPricePerK: Double
        get() = tokenPricingPerK[TokenTypes.Prompt] ?: 0.0

    /**
     * Backwards-compatible accessor for the completion (output) token price per 1k tokens.
     */
    val outputTokenPricePerK: Double
        get() = tokenPricingPerK[TokenTypes.Completion] ?: 0.0

    override fun toString() = modelId

    override fun pricing(usage: Usage): Double {
        val counts = usage.counts
        // Compute cost for each accounted token type using the pricing map.
        val perTypeCosts: Map<TokenTypes, Double> = counts.mapValues { (type, count) ->
            (tokenPricingPerK[type] ?: 0.0) * count
        }
        val accountedTokens = counts.entries.filter { tokenPricingPerK.containsKey(it.key) }.sumOf { it.value }
        val accountedCost = perTypeCosts.values.sum()

        // Estimate cost for any unaccounted tokens using the average of known prices.
        val unaccountedTokens = (usage.total_tokens - accountedTokens).coerceAtLeast(0)
        val averagePricePerK = if (tokenPricingPerK.isNotEmpty()) {
            tokenPricingPerK.values.average()
        } else 0.0
        val estimatedUnaccountedCost = unaccountedTokens * averagePricePerK

        val totalCost = (accountedCost + estimatedUnaccountedCost) / 1000.0
        // TODO: Downgrade this to debug once we have more confidence in the accuracy of the cost estimation
        log.info(
            "Calculating cost for model ${modelId}: " +
                    perTypeCosts.entries.joinToString(", ") { (type, cost) ->
                        val count = counts.getOrDefault(type, 0)
                        val price = tokenPricingPerK[type] ?: 0.0
                        "$type Tokens: $count at ${price}/k = ${String.format("%.4f", cost / 1000.0)}"
                    } +
                    ", Unaccounted Tokens: $unaccountedTokens estimated at ${
                        String.format("%.4f", estimatedUnaccountedCost / 1000.0)
                    }, Total Cost: ${String.format("%.4f", totalCost)}; " +
                    "for token counts ${counts.entries.joinToString(", ") { "${it.key}: ${it.value}" }}"
        )
        return totalCost
    }

    fun instance(
        key: SecureString,
        base: String = provider?.base!!,
        logLevel: Level = Level.DEBUG,
        logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
        workPool: ExecutorService = Executors.newFixedThreadPool(4),
        temperature: Double = 0.1,
        scheduledPool: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(
            Executors.newScheduledThreadPool(
                1
            )
        ),
        session: Session,
        user: User,
    ): ChatInterface = ChatInterface(
        logStreams = logStreams,
        key = key,
        base = base,
        logLevel = logLevel,
        temperature = temperature,
        provider = provider!!,
        modelType = this,
        workPool = workPool,
        scheduledPool = scheduledPool,
        session = session,
        onUsage = { model, usage, data -> ON_USAGE(model, usage, user, session, data) },
    )

    companion object {
        var ON_USAGE: (LLMModel, Usage, User, Session, ModelSchema.UsageData?) -> Unit =
            { model, usage, user, session, data ->
                log.info(
                    "Model: ${model.modelId}, Prompt Tokens: ${
                        usage.counts.getOrDefault(
                            TokenTypes.Prompt,
                            0
                        )
                    }, Completion Tokens: ${
                        usage.counts.getOrDefault(
                            TokenTypes.Completion,
                            0
                        )
                    }, Total Tokens: ${usage.total_tokens}, User: ${user.id}"
                )
            }
        val NULL: ChatModel = ChatModel(
            name = "NULL",
            modelId = "NULL",
            tokenPricingPerK = mapOf(
                TokenTypes.Prompt to 0.0,
                TokenTypes.Completion to 0.0,
            ),
            inputModalities = setOf(ChatMessageModality.TEXT),
            outputModalities = setOf(ChatMessageModality.TEXT)
        )
        val log = getLogger(ChatModel::class.java)
    }
}