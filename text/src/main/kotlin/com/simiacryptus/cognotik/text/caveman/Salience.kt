package com.simiacryptus.cognotik.text.caveman

/**
 * Optional statistical keyword ranking (spec 4.2 / 4.4 "Custom Salience Algorithms").
 * Implementations must be deterministic: no randomness, no wall-clock, no external state.
 */
interface SalienceExtractor {
  val id: String

  /** @return score keyed by [CavemanToken.key]; higher is more salient. */
  fun score(tokens: List<CavemanToken>): Map<String, Double>
}

/**
 * Term-frequency salience, normalized to the most frequent term, with an optional
 * (deterministic) bonus for earlier first occurrence.
 */
class FrequencySalienceExtractor(
  private val firstPositionBonus: Double = 0.0,
) : SalienceExtractor {

  override val id: String = "frequency"

  override fun score(tokens: List<CavemanToken>): Map<String, Double> {
    if (tokens.isEmpty()) return emptyMap()
    val counts = LinkedHashMap<String, Int>()
    val firstIndex = LinkedHashMap<String, Int>()
    tokens.forEachIndexed { i, token ->
      counts[token.key] = (counts[token.key] ?: 0) + 1
      if (!firstIndex.containsKey(token.key)) firstIndex[token.key] = i
    }
    val max = (counts.values.maxOrNull() ?: 1).toDouble()
    val size = tokens.size.toDouble()
    val out = LinkedHashMap<String, Double>()
    counts.forEach { (key, count) ->
      val positional = 1.0 - ((firstIndex[key] ?: 0).toDouble() / size)
      out[key] = count / max + firstPositionBonus * positional
    }
    return out
  }
}

/**
 * Graph-based (TextRank-style) salience over a sliding co-occurrence window, restricted to
 * within-sentence pairs. Uses a fixed iteration count (Jacobi updates from a snapshot) so
 * the result is bit-identical across runs and platforms. Falls back to frequency scoring
 * when the graph has no edges (e.g. single-token input).
 */
class TextRankSalienceExtractor(
  private val window: Int = 3,
  private val iterations: Int = 30,
  private val damping: Double = 0.85,
) : SalienceExtractor {

  override val id: String = "textrank"

  override fun score(tokens: List<CavemanToken>): Map<String, Double> {
    if (tokens.isEmpty()) return emptyMap()
    val nodes = LinkedHashMap<String, Int>()
    tokens.forEach { token -> if (!nodes.containsKey(token.key)) nodes[token.key] = nodes.size }
    val n = nodes.size
    val weights = Array(n) { DoubleArray(n) }
    var edges = 0
    for (i in tokens.indices) {
      var j = i + 1
      while (j < tokens.size && j <= i + window) {
        if (tokens[i].sentenceIndex == tokens[j].sentenceIndex) {
          val a = nodes[tokens[i].key]!!
          val b = nodes[tokens[j].key]!!
          if (a != b) {
            weights[a][b] += 1.0
            weights[b][a] += 1.0
            edges++
          }
        }
        j++
      }
    }
    if (edges == 0) return FrequencySalienceExtractor().score(tokens)

    val outSum = DoubleArray(n) { i -> weights[i].sum() }
    var scores = DoubleArray(n) { 1.0 }
    repeat(iterations) {
      val next = DoubleArray(n) { 1.0 - damping }
      for (i in 0 until n) {
        if (outSum[i] <= 0.0) continue
        for (j in 0 until n) {
          val w = weights[i][j]
          if (w > 0.0) next[j] += damping * (w / outSum[i]) * scores[i]
        }
      }
      scores = next
    }
    val out = LinkedHashMap<String, Double>()
    nodes.forEach { (key, index) -> out[key] = scores[index] }
    return out
  }
}