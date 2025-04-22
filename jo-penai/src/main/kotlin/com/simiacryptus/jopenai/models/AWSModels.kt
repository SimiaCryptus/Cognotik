package com.simiacryptus.jopenai.models
import org.slf4j.LoggerFactory

object AWSModels {
    private val log = LoggerFactory.getLogger(AWSModels::class.java)
    init {
        log.info("Initializing AWSModels with predefined chat models.")
    }

    val AWSLLaMA31_405bChat = ChatModel(
        name = "AWSLLaMA31_405bChat",
        modelName = "meta.llama3-1-405b-instruct-v1:0",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.00195,
        outputTokenPricePerK = 0.00256
    )
    val AWSLLaMA31_70bChat = ChatModel(
        name = "AWSLLaMA31_70bChat",
        modelName = "meta.llama3-1-70b-instruct-v1:0",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.00195,
        outputTokenPricePerK = 0.00256
    )
    val AWSLLaMA31_8bChat = ChatModel(
        name = "AWSLLaMA31_8bChat",
        modelName = "meta.llama3-1-8b-instruct-v1:0",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.00195,
        outputTokenPricePerK = 0.00256
    )
    
    val Mistral7bInstructV02 = ChatModel(
        name = "Mistral7bInstructV02",
        modelName = "mistral.mistral-7b-instruct-v0:2",
        maxTotalTokens = 32 * 1024,
        maxOutTokens = 2 * 1024,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.0002
    )
    val Mixtral8x7bInstructV01AWS = ChatModel(
        name = "Mixtral8x7bInstructV01AWS",
        modelName = "mistral.mixtral-8x7b-instruct-v0:1",
        maxTotalTokens = 32 * 1024,
        maxOutTokens = 2 * 1024,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.00045,
        outputTokenPricePerK = 0.0007
    )
    val MistralLarge2402 = ChatModel(
        name = "MistralLarge2402",
        modelName = "mistral.mistral-large-2402-v1:0",
        maxTotalTokens = 32 * 1024,
        maxOutTokens = 4000,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.008,
        outputTokenPricePerK = 0.024
    )
    val MistralLarge2407 = ChatModel(
        name = "MistralLarge2407",
        modelName = "mistral.mistral-large-2407-v1:0",
        maxTotalTokens = 32 * 1024,
        maxOutTokens = 4000,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.008,
        outputTokenPricePerK = 0.024
    )

    val AmazonTitanTextLiteV1 = ChatModel(
        name = "AmazonTitanTextLiteV1",
        modelName = "amazon.titan-text-lite-v1",
        maxTotalTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.0003,
        outputTokenPricePerK = 0.0004
    )
    val AmazonTitanTextExpressV1 = ChatModel(
        name = "AmazonTitanTextExpressV1",
        modelName = "amazon.titan-text-express-v1",
        maxTotalTokens = 8192,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.0008,
        outputTokenPricePerK = 0.0016
    )
    val Claude3OpusAWS = ChatModel(
        name = "Claude3OpusAWS",
        modelName = "anthropic.claude-3-opus-20240229-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 15.0 / 1000.0,
        outputTokenPricePerK = 75.0 / 1000.0
    )
    val CohereCommandTextV14 = ChatModel(
        name = "CohereCommandTextV14",
        modelName = "cohere.command-text-v14",
        maxTotalTokens = 4000,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.002
    )
    val AI21J2UltraV1 = ChatModel(
        name = "AI21J2UltraV1",
        modelName = "ai21.j2-ultra-v1",
        maxTotalTokens = 8191,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.0125,
        outputTokenPricePerK = 0.0125
    )
    val AI21J2MidV1 = ChatModel(
        name = "AI21J2MidV1",
        modelName = "ai21.j2-mid-v1",
        maxTotalTokens = 8191,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.0188,
        outputTokenPricePerK = 0.0188
    )
    val Claude35Sonnet = ChatModel(
        name = "Claude3SonnetAWS",
        modelName = "anthropic.claude-3-5-sonnet-20240620-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.003,
        outputTokenPricePerK = 0.015
    )
    val Claude37Sonnet = ChatModel(
        name = "Claude37SonnetAWS",
        modelName = "anthropic.claude-3-7-sonnet-20250219-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.003,
        outputTokenPricePerK = 0.015
    )
    val Claude3Sonnet = ChatModel(
        name = "Claude3SonnetAWS",
        modelName = "anthropic.claude-3-sonnet-20240229-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.003,
        outputTokenPricePerK = 0.015
    )
    val Claude3Haiku = ChatModel(
        name = "Claude3HaikuAWS",
        modelName = "anthropic.claude-3-haiku-20240307-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.00025,
        outputTokenPricePerK = 0.000125
    )
    val Claude35Haiku = ChatModel(
        name = "Claude35HaikuAWS",
        modelName = "anthropic.claude-3-5-haiku-20241022-v1:0",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.00025,
        outputTokenPricePerK = 0.000125
    )
    val LLaMA38bInstructAWS = ChatModel(
        name = "LLaMA38bInstructAWS",
        modelName = "meta.llama3-8b-instruct-v1:0",
        maxTotalTokens = 8192,
        maxOutTokens = 2048,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.0005, // Assuming pricing, adjust as necessary
        outputTokenPricePerK = 0.0015  // Assuming pricing, adjust as necessary
    )
    val LLaMA370bInstructAWS = ChatModel(
        name = "LLaMA370bInstructAWS",
        modelName = "meta.llama3-70b-instruct-v1:0",
        maxTotalTokens = 8192,
        maxOutTokens = 2048,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.0005, // Assuming pricing, adjust as necessary
        outputTokenPricePerK = 0.0015  // Assuming pricing, adjust as necessary
    )
    val AmazonNovaProV1 = ChatModel(
        name = "AmazonNovaProV1",
        modelName = "amazon.nova-pro-v1:0",
        maxTotalTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.001, // Assumed pricing, adjust as needed
        outputTokenPricePerK = 0.002  // Assumed pricing, adjust as needed
    )
    val AmazonNovaLiteV1 = ChatModel(
        name = "AmazonNovaLiteV1",
        modelName = "amazon.nova-lite-v1:0",
        maxTotalTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.0005, // Assumed pricing, adjust as needed
        outputTokenPricePerK = 0.001   // Assumed pricing, adjust as needed
    )
    val AmazonNovaMicroV1 = ChatModel(
        name = "AmazonNovaMicroV1",
        modelName = "amazon.nova-micro-v1:0",
        maxTotalTokens = 4096,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.00025, // Assumed pricing, adjust as needed
        outputTokenPricePerK = 0.0005   // Assumed pricing, adjust as needed
    )
    val DeepseekLLMR1DistillQwen32b = ChatModel(
        name = "DeepseekLLMR1DistillQwen32b",
        modelName = "deepseek-llm-r1-distill-qwen-32b",
        maxTotalTokens = 8192,
        provider = APIProvider.AWS,
        inputTokenPricePerK = 0.0010, // Assumed pricing, adjust as necessary
        outputTokenPricePerK = 0.0020  // Assumed pricing, adjust as necessary
    )
    val values = mapOf(
        "LLaMA370bInstructAWS" to LLaMA370bInstructAWS,
        "AmazonNovaProV1" to AmazonNovaProV1,
        "AmazonNovaLiteV1" to AmazonNovaLiteV1,
        "AmazonNovaMicroV1" to AmazonNovaMicroV1,
        "AWSLLaMA31_405bChat" to AWSLLaMA31_405bChat,
        "AWSLLaMA31_70bChat" to AWSLLaMA31_70bChat,
        "AWSLLaMA31_8bChat" to AWSLLaMA31_8bChat,
        "Mistral7bInstructV02" to Mistral7bInstructV02,
        "Mixtral8x7bInstructV01AWS" to Mixtral8x7bInstructV01AWS,
        "MistralLarge2402" to MistralLarge2402,
        "MistralLarge2407" to MistralLarge2407,
        "AmazonTitanTextLiteV1" to AmazonTitanTextLiteV1,
        "AmazonTitanTextExpressV1" to AmazonTitanTextExpressV1,
        "Claude3OpusAWS" to Claude3OpusAWS,
        "CohereCommandTextV14" to CohereCommandTextV14,
        "AI21J2UltraV1" to AI21J2UltraV1,
        "AI21J2MidV1" to AI21J2MidV1,
        "Claude35SonnetAWS" to Claude35Sonnet,
        "Claude3SonnetAWS" to Claude3Sonnet,
        "Claude3HaikuAWS" to Claude3Haiku,
        "Claude35HaikuAWS" to Claude35Haiku,
        "Claude37SonnetAWS" to Claude37Sonnet,
        "LLaMA38bInstructAWS" to LLaMA38bInstructAWS,
        "DeepseekLLMR1DistillQwen32b" to DeepseekLLMR1DistillQwen32b,
    )
}