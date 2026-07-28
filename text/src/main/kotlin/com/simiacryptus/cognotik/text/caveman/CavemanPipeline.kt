package com.simiacryptus.cognotik.text.caveman

import org.slf4j.LoggerFactory

/**
 * Orchestration layer (spec 4.1.4 / 5): composes the configured stages into an ordered,
 * fully inspectable pipeline and exposes the single public entry point.
 *
 * Deterministic by construction: no randomness, no timestamps, no external state, no network.
 *
 */
class CavemanPipeline(val config: CavemanConfig = CavemanConfig()) {

  private val log = LoggerFactory.getLogger(CavemanPipeline::class.java)

  /** Compress [text] to its caveman representation. */
  fun compress(text: String): String = run(text).output

  /** Compress [text], returning the final output plus a per-stage trace. */
  fun run(text: String): CavemanResult {
    val ctx = StageContext(text, config)
    val trace = ArrayList<StageResult>()
    var tokens: List<CavemanToken> = emptyList()

    for (stage in config.stages ?: defaultStages()) {
      if (!stage.isEnabled(config)) {
        if (config.collectTrace) trace.add(StageResult(stage.name, false, tokens.map { it.text }))
        continue
      }
      val outcome = stage.apply(tokens, ctx)
      tokens = outcome.tokens
      if (config.collectTrace) {
        trace.add(StageResult(stage.name, true, tokens.map { it.text }, outcome.decisions))
      }
      if (log.isTraceEnabled) {
        log.trace("stage {} -> [{}]", stage.name, tokens.joinToString(" ") { it.text })
      }
    }

    var output = tokens.joinToString(" ") { it.text }
    var intent: Intent? = null
    var template: String? = null

    if (config.grammarEnabled && tokens.isNotEmpty()) {
      val grammar = config.grammar.apply(text, tokens)
      output = grammar.text
      intent = grammar.signal.intent
      template = grammar.templateId
      if (config.collectTrace) {
        trace.add(
          StageResult(
            "grammar-reconstruction", true, listOf(output),
            listOf(
              Decision(
                output, TokenAction.ADD,
                "intent=${grammar.signal.intent} (${grammar.signal.reason}); flavor=${config.grammar.flavor}",
                "template=${grammar.templateId}"
              )
            )
          )
        )
      }
    } else if (config.collectTrace) {
      trace.add(StageResult("grammar-reconstruction", config.grammarEnabled, listOf(output)))
    }

    return CavemanResult(
      input = text,
      output = output,
      tokens = tokens,
      intent = intent,
      template = template,
      trace = trace,
    )
  }

  companion object {
    /** Canonical stage ordering from spec 3.3 / 5. */
    fun defaultStages(): List<PipelineStage> = listOf(
      TokenizationStage,
      CaseNormalizationStage,
      DomainPhraseMergeStage,
      ShortTokenFilterStage,
      StopwordRemovalStage,
      DomainTermMarkingStage,
      StemmingStage,
      PosFilterStage,
      SalienceStage,
      DeduplicationStage,
    )
  }
}

/** Convenience façade for one-shot usage.
 *
 * val config = CavemanConfig.aggressive(topN = 8)
 *   .withDomains(DomainTermRegistry.DISTRIBUTED_SYSTEMS)
 * val result = Caveman.run("Please explain why our Raft cluster keeps losing quorum.", config)
 * result.output   // -> "explain Raft cluster lose quorum"
 * result.explain() // per-stage, per-token attribution
 *
 * */
object Caveman {
  fun compress(text: String, config: CavemanConfig = CavemanConfig()): String =
    CavemanPipeline(config).compress(text)

  fun run(text: String, config: CavemanConfig = CavemanConfig()): CavemanResult =
    CavemanPipeline(config).run(text)

  fun explain(text: String, config: CavemanConfig = CavemanConfig()): String =
    CavemanPipeline(config).run(text).explain()
}