package com.simiacryptus.cognotik.text.caveman

/** Coarse grammatical categories used by the optional POS filter (spec 3.3.6 / 4.2). */
enum class PosCategory { NOUN, VERB, ADJECTIVE, ADVERB, NUMBER, PRONOUN, OTHER }

/** Coarse intents recognized by the rule-based grammar layer (spec 3.5). */
enum class Intent { QUESTION, REQUEST, DESIRE, PROBLEM, DESCRIPTION }

/**
 * A single token flowing through the pipeline.
 *
 * @param surface original, untouched surface form (used by POS tagging + explainability)
 * @param text current, possibly normalized/stemmed form
 * @param position index within the original token stream
 * @param sentenceIndex index of the source sentence (used by salience co-occurrence windows)
 * @param domainTerm true when the token matched the active domain dictionary
 * @param pos assigned part of speech, if the POS stage ran
 * @param salience assigned salience score, if the salience stage ran
 */
data class CavemanToken(
  val surface: String,
  val text: String,
  val position: Int,
  val sentenceIndex: Int = 0,
  val domainTerm: Boolean = false,
  val pos: PosCategory? = null,
  val salience: Double? = null,
) {
  val key: String get() = text.lowercase()
}

/** What a stage did to a token; first-class explainability requirement (spec 8.2). */
enum class TokenAction { KEEP, REMOVE, REWRITE, MERGE, ADD }

data class Decision(
  val token: String,
  val action: TokenAction,
  val reason: String,
  val detail: String? = null,
)

data class StageResult(
  val stage: String,
  val enabled: Boolean,
  val tokens: List<String>,
  val decisions: List<Decision> = emptyList(),
)

data class CavemanResult(
  val input: String,
  val output: String,
  val tokens: List<CavemanToken>,
  val intent: Intent? = null,
  val template: String? = null,
  val trace: List<StageResult> = emptyList(),
) {
  /** Human-readable, per-stage attribution of every transformation. */
  fun explain(): String = buildString {
    appendLine("input : $input")
    trace.forEach { stage ->
      appendLine(
        "[${stage.stage}] ${if (stage.enabled) "enabled" else "disabled"} -> " +
            stage.tokens.joinToString(" ")
      )
      stage.decisions.forEach { d ->
        append("    ").append(d.action).append(" '").append(d.token).append("': ").append(d.reason)
        if (d.detail != null) append(" (").append(d.detail).append(")")
        appendLine()
      }
    }
    if (intent != null) appendLine("intent: $intent (template=$template)")
    appendLine("output: $output")
  }
}