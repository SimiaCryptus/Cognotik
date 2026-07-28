package com.simiacryptus.cognotik.text.caveman

/** Output of the tokenization step; the only language-aware contract in the module. */
data class RawToken(
  val text: String,
  val position: Int,
  val sentenceIndex: Int,
  val start: Int,
  val end: Int,
)

/**
 * Swappable analyzer chain (spec 4.2 / 4.5). ALL language-specific behavior lives behind
 * this interface, so Salience / Grammar / Orchestration layers stay language-agnostic.
 */
interface TextAnalyzer {
  val id: String
  fun tokenize(text: String): List<RawToken>
}

/**
 * Zero-dependency English analyzer: Unicode word tokenization plus shallow sentence
 * segmentation. Tolerates incidental punctuation without failing (spec 3.1).
 */
class DefaultEnglishAnalyzer(
  private val wordPattern: Regex = DEFAULT_WORD_PATTERN,
  private val sentenceTerminators: String = ".!?;\n",
  override val id: String = "default-english",
) : TextAnalyzer {

  override fun tokenize(text: String): List<RawToken> {
    val out = ArrayList<RawToken>()
    var sentence = 0
    var cursor = 0
    var position = 0
    for (match in wordPattern.findAll(text)) {
      val start = match.range.first
      var sawTerminator = false
      var i = cursor
      while (i < start) {
        if (text[i] in sentenceTerminators) sawTerminator = true
        i++
      }
      if (sawTerminator && out.isNotEmpty()) sentence++
      out.add(RawToken(match.value, position++, sentence, start, match.range.last + 1))
      cursor = match.range.last + 1
    }
    return out
  }

  companion object {
    /** Words may contain internal apostrophes, hyphens, dots and underscores (e.g. "TF-IDF", "k8s.io"). */
    val DEFAULT_WORD_PATTERN: Regex =
      Regex("[\\p{L}\\p{N}]+(?:['\u2019._\\-][\\p{L}\\p{N}]+)*")
  }
}