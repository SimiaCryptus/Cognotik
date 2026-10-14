package com.simiacryptus.cognotik.platform.model

import com.simiacryptus.cognotik.platform.model.ChatModel.ReasoningLevel

/**
 * Single source of truth for encoding a reasoning effort into a model id.
 *
 * Some clients can only select a model by id, so every reasoning-capable model
 * is additionally exposed as "<modelId>-<level>" (e.g. "claude-opus-4-high").
 * Servers/clients stem the suffix back off before calling the upstream API and
 * apply the level via [ChatModel.reasoningLevel].
 */
object ReasoningVariants {

  /** "-low", "-medium", "-high", "-x" ... */
  fun suffix(level: ReasoningLevel): String = "-" + level.name.lowercase()

  /** Returns the reasoning level encoded as a trailing "-<level>" segment, if any (case-insensitive). */
  fun levelFromModelId(modelId: String?): ReasoningLevel? {
    if (modelId.isNullOrBlank()) return null
    val idx = modelId.lastIndexOf('-')
    if (idx <= 0 || idx == modelId.length - 1) return null
    val s = modelId.substring(idx + 1)
    return ReasoningLevel.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
  }

  /** Strips a reasoning suffix, if present. */
  fun stem(modelId: String): String =
    if (levelFromModelId(modelId) != null) modelId.substring(0, modelId.lastIndexOf('-')) else modelId

  /** Returns [modelId] with exactly the given reasoning suffix (or unchanged if [level] is null). */
  fun withLevel(modelId: String, level: ReasoningLevel?): String =
    if (level == null) modelId else stem(modelId) + suffix(level)

  /** Builds the effort-specific variant of a reasoning-capable [model]. */
  fun variant(model: ChatModel, level: ReasoningLevel): ChatModel = ChatModel(
    name = "${model.name} (${level.name})",
    modelId = withLevel(model.modelId, level),
    maxTotalTokens = model.maxTotalTokens,
    maxOutTokens = model.maxOutTokens,
    provider = model.provider,
    tokenPricingPerK = model.tokenPricingPerK,
    supportsTemperature = model.supportsTemperature,
    // The level is fixed by the id; the variant must not be expanded again.
    supportsReasoning = false,
    deprecated = model.deprecated,
    inputModalities = model.inputModalities,
    outputModalities = model.outputModalities,
    reasoningLevel = level
  )

  /**
   * Returns [model] followed by one variant per reasoning level if the model
   * supports reasoning and does not already have a fixed level.
   */
  fun expand(model: ChatModel): List<ChatModel> =
    if (!model.supportsReasoning || model.reasoningLevel != null) listOf(model)
    else listOf(model) + ReasoningLevel.entries.map { variant(model, it) }

  /**
   * Expands all reasoning-capable models. Models already present in [models]
   * (e.g. variants a provider listed itself) take precedence over generated ones.
   */
  fun expandAll(models: List<ChatModel>): List<ChatModel> {
    val existing = models.map { it.modelId }.toSet()
    return models.flatMap { m ->
      expand(m).filter { it === m || it.modelId !in existing }
    }.distinctBy { it.modelId }
  }
}