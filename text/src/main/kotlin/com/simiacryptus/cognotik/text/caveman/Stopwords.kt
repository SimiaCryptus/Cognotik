package com.simiacryptus.cognotik.text.caveman

/**
 * Configurable, mergeable stopword set (spec 4.2 "Stopword Provider", 4.4 "Custom Stopword Sets").
 * Immutable and order-stable so that [asSortedList] is deterministic.
 */
class StopwordProvider private constructor(private val words: Set<String>) {

  val size: Int get() = words.size

  fun contains(word: String): Boolean = words.contains(word.lowercase())

  fun plus(more: Iterable<String>): StopwordProvider =
    StopwordProvider(LinkedHashSet(words).apply {
      addAll(more.map { it.trim().lowercase() }.filter { it.isNotEmpty() })
    })

  fun plus(other: StopwordProvider): StopwordProvider = plus(other.words)

  fun minus(fewer: Iterable<String>): StopwordProvider =
    StopwordProvider(LinkedHashSet(words).apply { removeAll(fewer.map { it.trim().lowercase() }.toSet()) })

  fun asSortedList(): List<String> = words.sorted()

  companion object {

    /**
     * Default English function-word list. Interrogatives are included: coarse intent is
     * recovered from the raw text by the grammar layer, not from surviving tokens.
     */
    val ENGLISH_DEFAULT: List<String> = listOf(
      "a", "about", "above", "after", "again", "against", "all", "also", "am", "an", "and", "any",
      "are", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both",
      "but", "by", "can", "cannot", "could", "did", "do", "does", "doing", "done", "down", "during",
      "each", "few", "for", "from", "further", "had", "has", "have", "having", "he", "her", "here",
      "hers", "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its",
      "itself", "just", "kindly", "let", "may", "me", "might", "mine", "more", "most", "must", "my",
      "myself", "no", "nor", "not", "now", "of", "off", "on", "once", "only", "or", "other", "ought",
      "our", "ours", "ourselves", "out", "over", "own", "please", "same", "shall", "she", "should",
      "so", "some", "such", "than", "that", "the", "their", "theirs", "them", "themselves", "then",
      "there", "these", "they", "this", "those", "through", "to", "too", "under", "until", "up",
      "upon", "us", "very", "was", "we", "were", "what", "when", "where", "whether", "which",
      "while", "who", "whom", "whose", "why", "will", "with", "would", "you", "your", "yours",
      "yourself", "yourselves", "s", "t"
    )

    fun english(): StopwordProvider = of(ENGLISH_DEFAULT)

    fun empty(): StopwordProvider = StopwordProvider(emptySet())

    fun of(vararg words: String): StopwordProvider = of(words.toList())

    fun of(words: Iterable<String>): StopwordProvider =
      StopwordProvider(LinkedHashSet(words.map { it.trim().lowercase() }.filter { it.isNotEmpty() }))

    /** Accepts one word per line; `#` starts a comment. */
    fun fromLines(lines: Iterable<String>): StopwordProvider =
      of(lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() })

    fun fromResource(
      path: String,
      loader: ClassLoader = StopwordProvider::class.java.classLoader,
    ): StopwordProvider {
      val stream = loader.getResourceAsStream(path)
        ?: throw IllegalArgumentException("Stopword resource not found: $path")
      return stream.bufferedReader(Charsets.UTF_8).use { fromLines(it.readLines()) }
    }
  }
}