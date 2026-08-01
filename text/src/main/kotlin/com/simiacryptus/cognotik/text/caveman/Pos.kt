package com.simiacryptus.cognotik.text.caveman

/** Optional POS classification component (spec 4.2). Tagging is done on surface forms. */
interface PosTagger {
  val id: String

  /** @return one category per input token, in input order. */
  fun tag(tokens: List<CavemanToken>): List<PosCategory>
}

/**
 * Rule-based, model-free POS approximation: closed-class lists plus derivational-suffix
 * heuristics. Deterministic and offline; deliberately coarse (spec 10).
 */
class HeuristicPosTagger : PosTagger {

  override val id: String = "heuristic"

  override fun tag(tokens: List<CavemanToken>): List<PosCategory> =
    tokens.map { classify(it.surface.lowercase()) }

  fun classify(word: String): PosCategory {
    if (word.isEmpty()) return PosCategory.OTHER
    if (NUMBER.matches(word)) return PosCategory.NUMBER
    if (word in PRONOUNS) return PosCategory.PRONOUN
    if (word in VERBS) return PosCategory.VERB
    if (word in ADVERBS) return PosCategory.ADVERB
    return when {
      word.endsWith("ly") && word.length > 4 -> PosCategory.ADVERB
      ADJECTIVE_SUFFIXES.any { word.length > it.length + 2 && word.endsWith(it) } -> PosCategory.ADJECTIVE
      NOUN_SUFFIXES.any { word.length > it.length + 2 && word.endsWith(it) } -> PosCategory.NOUN
      VERB_SUFFIXES.any { word.length > it.length + 2 && word.endsWith(it) } -> PosCategory.VERB
      else -> PosCategory.NOUN
    }
  }

  companion object {
    private val NUMBER = Regex("[0-9]+([.,][0-9]+)*%?")

    private val PRONOUNS = setOf(
      "i", "me", "my", "mine", "we", "us", "our", "ours", "you", "your", "yours",
      "he", "him", "his", "she", "her", "hers", "it", "its", "they", "them", "their", "theirs"
    )

    private val ADVERBS = setOf("very", "quite", "rather", "almost", "always", "never", "often", "soon", "now", "then")

    private val VERBS = setOf(
      "be", "is", "are", "was", "were", "been", "being", "have", "has", "had", "do", "does", "did",
      "make", "made", "get", "got", "go", "goes", "went", "take", "took", "give", "gave", "see",
      "saw", "know", "knew", "think", "thought", "want", "need", "use", "run", "ran", "write",
      "wrote", "read", "build", "built", "find", "found", "show", "tell", "told", "explain",
      "cause", "fail", "fails", "break", "broke", "fix", "add", "remove", "delete", "create",
      "update", "handle", "support", "return", "send", "receive", "store", "load", "save",
      "start", "stop", "work", "help", "compare", "analyze", "analyse", "summarize", "summarise",
      "describe", "list", "generate", "implement", "design", "review", "refactor", "debug",
      "optimize", "optimise", "deploy", "scale", "replicate", "elect", "commit", "merge", "split"
    )

    private val ADJECTIVE_SUFFIXES = listOf(
      "ous", "ful", "ive", "able", "ible", "ical", "ish", "less", "ary", "ic", "al", "y"
    )

    private val NOUN_SUFFIXES = listOf(
      "tion", "sion", "ment", "ness", "ity", "ance", "ence", "ship", "ism", "ist",
      "er", "or", "cy", "age", "ure", "dom", "hood", "logy"
    )

    private val VERB_SUFFIXES = listOf("ing", "ize", "ise", "ify", "ate", "ed")
  }
}