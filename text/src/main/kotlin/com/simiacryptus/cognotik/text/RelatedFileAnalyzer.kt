package com.simiacryptus.cognotik.text

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Purely lexical "which files look like this one?" analysis.
 *
 * Every file is turned into a bag of **grammatically extracted** tokens - the declarations
 * ([SymbolIndexer.FileRecord.qualifiedNames]) and the references
 * ([SymbolIndexer.FileRecord.referencedNames]) produced by the language's ANTLR grammar,
 * split at dot boundaries (the full dotted name is kept as an extra token).
 * No raw text, comments or string literals ever take part.
 *
 * The bags are weighted with a standard sub-linear TF x smoothed IDF scheme:
 *
 * ```
 * tf(t,d)  = 1 + ln(weighted occurrences of t in d)      (declarations count double)
 * idf(t)   = ln((1 + N) / (1 + df(t))) + 1
 * w(t,d)   = tf(t,d) * idf(t)                            (L2-normalised per document)
 * score    = cosine(w(d1), w(d2))                        (0 .. 1)
 * ```
 *
 * Tokens that occur in a single file (they can never be shared) and tokens that occur in more
 * than [Options.maxDocumentFrequencyRatio] of the corpus (`String`, `get`, `value`, ...) are
 * discarded, so the surviving signal is dominated by the distinctive names two files share.
 *
 * Scoring uses an inverted index, i.e. only files that share at least one surviving token are
 * ever compared.
 */
object RelatedFileAnalyzer {

  /** Related files stored per file. */
  const val DEFAULT_MAX_RELATED = 10

  /** Minimum cosine similarity worth reporting. */
  const val DEFAULT_MIN_SCORE = 0.05

  /** Highest-IDF shared tokens kept as evidence for each relation. */
  const val DEFAULT_MAX_SHARED_TOKENS = 8

  /** Tokens present in more than this fraction of the corpus carry no signal. */
  const val DEFAULT_MAX_DOCUMENT_FREQUENCY_RATIO = 0.5

  /** One neighbour of a file, as reported in [SymbolIndexer.FileRecord.relatedFiles]. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class RelatedFile(
    /** Root-relative path of the related file. */
    val path: String = "",
    /** Cosine similarity of the two TF-IDF vectors (0 .. 1), rounded to 4 decimals. */
    val score: Double = 0.0,
    /** Number of surviving tokens the two files have in common. */
    val sharedTokenCount: Int = 0,
    /** The most distinctive (highest-IDF) shared tokens. */
    val sharedTokens: List<String> = emptyList(),
    /** Directory-traversal distance, see [SymbolResolver.pathDistance]. */
    val distance: Int = 0,
  )

  data class Options(
    val maxRelated: Int = DEFAULT_MAX_RELATED,
    val minScore: Double = DEFAULT_MIN_SCORE,
    val maxSharedTokens: Int = DEFAULT_MAX_SHARED_TOKENS,
    val maxDocumentFrequencyRatio: Double = DEFAULT_MAX_DOCUMENT_FREQUENCY_RATIO,
    /** Single-character tokens are noise. */
    val minTokenLength: Int = 2,
    val includeDeclarations: Boolean = true,
    val includeReferences: Boolean = true,
    /** Declaring a name says more about a file than merely mentioning one. */
    val declarationWeight: Double = 2.0,
    val referenceWeight: Double = 1.0,
    /** Skip tokens whose posting list is longer than this (guards against O(N^2) blow-ups). */
    val maxPostingsPerToken: Int = 4096,
  )

  /**
   * Return a copy of [records] where every record carries its [RelatedFile] neighbours.
   * Order is preserved; records without usable tokens simply get an empty list.
   */
  fun analyze(
    records: List<SymbolIndexer.FileRecord>,
    options: Options = Options(),
  ): List<SymbolIndexer.FileRecord> {
    if (options.maxRelated <= 0 || records.size < 2) return records.map { cleared(it) }
    val n = records.size
    val bags = records.map { tokenize(it, options) }

    val documentFrequency = HashMap<String, Int>()
    bags.forEach { bag -> bag.keys.forEach { documentFrequency.merge(it, 1) { a, b -> a + b } } }
    val maxDf = (n * options.maxDocumentFrequencyRatio).toInt().coerceAtLeast(1)
    val idf = HashMap<String, Double>(documentFrequency.size)
    documentFrequency.forEach { (token, df) ->

      if (df in 2..maxDf) idf[token] = ln((1.0 + n) / (1.0 + df)) + 1.0
    }
    if (idf.isEmpty()) return records.map { cleared(it) }

    val vectors: List<Map<String, Double>> = bags.map { bag ->
      val weighted = HashMap<String, Double>()
      bag.forEach { (token, tf) ->
        val inverse = idf[token] ?: return@forEach
        if (tf > 0.0) weighted[token] = (1.0 + ln(tf)) * inverse
      }
      val norm = sqrt(weighted.values.sumOf { it * it })
      if (norm <= 0.0) emptyMap() else weighted.mapValues { (_, v) -> v / norm }
    }

    val postings = HashMap<String, MutableList<Int>>(idf.size)
    vectors.forEachIndexed { i, vector -> vector.keys.forEach { postings.getOrPut(it) { ArrayList() }.add(i) } }

    return records.mapIndexed { i, record ->
      val vector = vectors[i]
      if (vector.isEmpty()) return@mapIndexed cleared(record)
      val scores = HashMap<Int, Double>()
      vector.forEach { (token, weight) ->
        val docs = postings[token] ?: return@forEach
        if (docs.size > options.maxPostingsPerToken) return@forEach
        docs.forEach { j ->
          if (j != i) scores.merge(j, weight * (vectors[j][token] ?: 0.0)) { a, b -> a + b }
        }
      }
      val related = scores.entries.asSequence()
        .filter { it.value >= options.minScore }
        .sortedWith(
          compareByDescending<Map.Entry<Int, Double>> { it.value }
            .thenBy { SymbolResolver.pathDistance(record.path, records[it.key].path) }
            .thenBy { records[it.key].path }
        )
        .take(options.maxRelated)
        .map { (j, score) -> relatedFile(record, records[j], vector, vectors[j], idf, score, options) }
        .toList()
      record.copy(relatedFiles = related)
    }
  }

  /** The weighted token bag of a single record - exposed for tests/tooling. */
  fun tokenize(record: SymbolIndexer.FileRecord, options: Options = Options()): Map<String, Double> {
    val counts = HashMap<String, Double>()
    if (options.includeDeclarations) record.qualifiedNames.forEach {
      add(
        counts,
        it,
        options.declarationWeight,
        options
      )
    }
    if (options.includeReferences) record.referencedNames.forEach { add(counts, it, options.referenceWeight, options) }
    return counts
  }

  private fun add(counts: HashMap<String, Double>, raw: String?, weight: Double, options: Options) {
    val name = SymbolResolver.normalize(raw ?: return)
    if (name.isEmpty()) return
    val parts = name.split('.').filter { it.isNotBlank() }
    if (parts.isEmpty()) return

    if (parts.size > 1) counts.merge(name, weight) { a, b -> a + b }
    parts.forEach { part ->
      if (part.length >= options.minTokenLength) counts.merge(part, weight) { a, b -> a + b }
    }
  }

  private fun relatedFile(
    from: SymbolIndexer.FileRecord,
    to: SymbolIndexer.FileRecord,
    fromVector: Map<String, Double>,
    toVector: Map<String, Double>,
    idf: Map<String, Double>,
    score: Double,
    options: Options,
  ): RelatedFile {
    val shared = fromVector.keys.filter { toVector.containsKey(it) }
    val evidence = shared
      .sortedWith(compareByDescending<String> { idf[it] ?: 0.0 }.thenBy { it })
      .take(options.maxSharedTokens.coerceAtLeast(0))
    return RelatedFile(
      path = to.path,
      score = round4(score.coerceIn(0.0, 1.0)),
      sharedTokenCount = shared.size,
      sharedTokens = evidence,
      distance = SymbolResolver.pathDistance(from.path, to.path),
    )
  }

  private fun cleared(record: SymbolIndexer.FileRecord) =
    if (record.relatedFiles.isEmpty()) record else record.copy(relatedFiles = emptyList())

  private fun round4(value: Double): Double = Math.round(value * 10_000.0) / 10_000.0
}