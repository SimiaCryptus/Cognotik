package com.openai.models

/**
 * Represents the set of OpenAI (ChatGPT) models currently offered across
 * Business and Enterprise plans, as documented on the OpenAI pricing page
 * (https://chatgpt.com/pricing).
 *
 * Each model is associated with:
 *  - a stable [id] used for API/config references
 *  - a human-readable [displayName] shown in UI/pricing tables
 *  - a [tier] indicating whether it is an "Instant" (fast, low-latency)
 *    model or a "Reasoning"/"Thinking" model with a larger context window
 *  - flags indicating availability on Business and Enterprise plans
 *
 * This file should be kept in sync with the official pricing/comparison
 * table. When OpenAI releases new model versions, add a new enum entry
 * rather than mutating existing ones, to preserve historical references.
 */
enum class OpenAIModelTier {
    INSTANT,
    REASONING,
    LEGACY
}

data class OpenAIModel(
    val id: String,
    val displayName: String,
    val tier: OpenAIModelTier,
    val availableOnBusiness: Boolean = true,
    val availableOnEnterprise: Boolean = true,
    /**
     * Whether usage of this model is unlimited* (subject to abuse guardrails)
     * or flexible** (credits can be purchased for additional access).
     */
    val isUnlimitedUsage: Boolean = false,
    val isFlexibleUsage: Boolean = false
)

/**
 * Canonical registry of OpenAI models available under Business/Enterprise
 * plans as of the latest pricing update.
 */
object OpenAIModels {

    val GPT_5_4 = OpenAIModel(
        id = "gpt-5.4",
        displayName = "GPT-5.4",
        tier = OpenAIModelTier.INSTANT,
        isUnlimitedUsage = true
    )

    val GPT_5_5_INSTANT = OpenAIModel(
        id = "gpt-5.5-instant",
        displayName = "GPT-5.5 Instant",
        tier = OpenAIModelTier.INSTANT,
        isUnlimitedUsage = true
    )

    val GPT_5_6_SOL = OpenAIModel(
        id = "gpt-5.6-sol",
        displayName = "GPT-5.6 Sol",
        tier = OpenAIModelTier.REASONING,
        isFlexibleUsage = true
    )

    val GPT_5_6_SOL_PRO = OpenAIModel(
        id = "gpt-5.6-sol-pro",
        displayName = "GPT-5.6 Sol Pro",
        tier = OpenAIModelTier.REASONING,
        isFlexibleUsage = true
    )

    val GPT_5_6_TERRA = OpenAIModel(
        id = "gpt-5.6-terra",
        displayName = "GPT-5.6 Terra",
        tier = OpenAIModelTier.REASONING,
        isFlexibleUsage = true
    )

    val GPT_5_6_LUNA = OpenAIModel(
        id = "gpt-5.6-luna",
        displayName = "GPT-5.6 Luna",
        tier = OpenAIModelTier.REASONING,
        isFlexibleUsage = true
    )

    val GPT_5_THINKING_MINI = OpenAIModel(
        id = "gpt-5-thinking-mini",
        displayName = "GPT-5 Thinking Mini",
        tier = OpenAIModelTier.REASONING,
        isFlexibleUsage = true
    )

    val LEGACY_MODELS = OpenAIModel(
        id = "legacy-models",
        displayName = "Legacy models",
        tier = OpenAIModelTier.LEGACY
    )

    /**
     * All models currently listed in the Business/Enterprise pricing
     * comparison table, in the order they appear on the pricing page.
     */
    val ALL: List<OpenAIModel> = listOf(
        GPT_5_5_INSTANT,
        GPT_5_6_SOL,
        GPT_5_6_SOL_PRO,
        GPT_5_6_TERRA,
        GPT_5_6_LUNA,
        GPT_5_THINKING_MINI,
        LEGACY_MODELS
    )

    fun findById(id: String): OpenAIModel? = ALL.find { it.id == id }
}

/**
 * Context window sizes (in tokens, approximate) per model family and plan,
 * as shown in the "Models" section of the pricing comparison table.
 */
data class ContextWindowInfo(
    val totalContextWindow: String,
    val inputMaximumPages: String
)

object OpenAIContextWindows {
    val INSTANT_BUSINESS = ContextWindowInfo(
        totalContextWindow = "54K",
        inputMaximumPages = "~40 pages"
    )

    val INSTANT_ENTERPRISE = ContextWindowInfo(
        totalContextWindow = "128K",
        inputMaximumPages = "~250 pages"
    )

    val REASONING_BUSINESS = ContextWindowInfo(
        totalContextWindow = "256K",
        inputMaximumPages = "~320 pages"
    )

    val REASONING_ENTERPRISE = ContextWindowInfo(
        totalContextWindow = "256K",
        inputMaximumPages = "~320 pages"
    )
}

/**
 * Plan-level metadata (pricing, response times) referenced alongside the
 * model registry above.
 */
enum class OpenAIPlan(val displayName: String, val responseTime: String) {
    BUSINESS("Business", "Fast"),
    ENTERPRISE("Enterprise", "Fastest")
}