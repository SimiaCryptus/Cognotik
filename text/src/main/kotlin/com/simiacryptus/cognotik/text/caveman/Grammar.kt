package com.simiacryptus.cognotik.text.caveman

/** Result of shallow intent detection; carries the evidence used, for explainability. */
data class IntentSignal(
  val intent: Intent,
  val interrogative: String? = null,
  val leadVerb: String? = null,
  val reason: String = "",
)

/** Rule-based only; must never consult a statistical or learned model (spec 3.5). */
interface IntentClassifier {
  val id: String
  fun classify(text: String, tokens: List<CavemanToken>): IntentSignal
}

data class GrammarInput(
  val originalText: String,
  val tokens: List<CavemanToken>,
  val signal: IntentSignal,
) {
  fun body(): String = tokens.joinToString(" ") { it.text }
  fun bodyDropFirst(): String = tokens.drop(1).joinToString(" ") { it.text }
  fun firstToken(): String? = tokens.firstOrNull()?.text
}

interface GrammarTemplate {
  val id: String
  fun render(input: GrammarInput): String
}

class SimpleGrammarTemplate(
  override val id: String,
  private val renderer: (GrammarInput) -> String,
) : GrammarTemplate {
  override fun render(input: GrammarInput): String = renderer(input)
}

/** Shallow, deterministic intent heuristics: punctuation, interrogatives, lead verbs. */
class HeuristicIntentClassifier(
  private val interrogatives: Set<String> = INTERROGATIVES,
  private val auxiliaries: Set<String> = AUXILIARIES,
  private val imperativeVerbs: Set<String> = IMPERATIVE_VERBS,
  private val problemWords: Set<String> = PROBLEM_WORDS,
  override val id: String = "heuristic-en",
) : IntentClassifier {

  override fun classify(text: String, tokens: List<CavemanToken>): IntentSignal {
    val lower = text.lowercase()
    val words = WORD.findAll(lower).map { it.value }.toList()
    val first = words.firstOrNull()
    val interrogative = words.firstOrNull { it in interrogatives }
    val questionMark = text.trimEnd().endsWith("?")
    val isQuestion = questionMark || first in interrogatives || first in auxiliaries

    var leadVerb: String? = null
    var scanned = 0
    for (word in words) {
      if (scanned >= 5) break
      scanned++
      if (word in SKIPPABLE_LEAD) continue
      if (word in imperativeVerbs) {
        leadVerb = word
        break
      }
      if (word !in SKIPPABLE_LEAD && scanned > 1 && !isQuestion) break
    }

    val desire = DESIRE_PATTERNS.any { lower.contains(it) }
    val problem = words.any { it in problemWords }

    return when {
      desire -> IntentSignal(Intent.DESIRE, interrogative, leadVerb, "matched desire phrase")
      isQuestion -> IntentSignal(
        Intent.QUESTION, interrogative, leadVerb,
        if (questionMark) "trailing question mark" else "leading interrogative/auxiliary"
      )

      leadVerb != null -> IntentSignal(Intent.REQUEST, interrogative, leadVerb, "leading imperative verb '$leadVerb'")
      problem -> IntentSignal(Intent.PROBLEM, interrogative, leadVerb, "matched problem vocabulary")
      else -> IntentSignal(Intent.DESCRIPTION, interrogative, leadVerb, "no intent marker found")
    }
  }

  companion object {
    private val WORD = Regex("[a-z0-9']+")

    val INTERROGATIVES = setOf("what", "why", "how", "when", "where", "who", "whom", "whose", "which")
    val AUXILIARIES =
      setOf("is", "are", "was", "were", "do", "does", "did", "can", "could", "should", "would", "will", "may")
    val SKIPPABLE_LEAD = setOf(
      "please",
      "kindly",
      "can",
      "could",
      "would",
      "you",
      "i",
      "we",
      "to",
      "the",
      "a",
      "an",
      "let",
      "us",
      "me",
      "how"
    )
    val IMPERATIVE_VERBS = setOf(
      "explain", "describe", "summarize", "summarise", "list", "compare", "analyze", "analyse",
      "write", "create", "build", "generate", "implement", "design", "review", "refactor",
      "debug", "optimize", "optimise", "fix", "show", "tell", "give", "make", "help", "find",
      "translate", "convert", "outline", "draft", "critique", "evaluate", "plan"
    )
    val PROBLEM_WORDS = setOf(
      "error", "errors", "fail", "fails", "failed", "failing", "failure", "broken", "breaks",
      "crash", "crashes", "crashed", "bug", "bugs", "problem", "problems", "issue", "issues",
      "exception", "timeout", "regression", "stuck", "hang", "hangs"
    )
    val DESIRE_PATTERNS = listOf("i want", "i need", "we want", "we need", "i would like", "i'd like", "we would like")
  }
}

/**
 * Selects and applies a minimal grammatical scaffold. Fully rule-based; templates are
 * user-extensible, including alternate "flavors" (spec 4.4, 11).
 */
class GrammarTemplateEngine(
  val classifier: IntentClassifier = HeuristicIntentClassifier(),
  private val templates: Map<Intent, GrammarTemplate> = cavemanTemplates(),
  private val fallback: GrammarTemplate = PASSTHROUGH,
  val flavor: String = "caveman",
) {

  data class GrammarOutput(val text: String, val templateId: String, val signal: IntentSignal)

  fun withTemplate(intent: Intent, template: GrammarTemplate): GrammarTemplateEngine =
    GrammarTemplateEngine(classifier, LinkedHashMap(templates).apply { put(intent, template) }, fallback, flavor)

  fun apply(originalText: String, tokens: List<CavemanToken>): GrammarOutput {
    val signal = classifier.classify(originalText, tokens)
    if (tokens.isEmpty()) return GrammarOutput("", "empty", signal)
    val template = templates[signal.intent] ?: fallback
    return GrammarOutput(
      template.render(GrammarInput(originalText, tokens, signal)).trim(),
      template.id,
      signal
    )
  }

  companion object {

    val PASSTHROUGH: GrammarTemplate = SimpleGrammarTemplate("passthrough") { it.body() }

    /** Cheap same-lemma test used to avoid duplicating a verb already present in the body. */
    internal fun looksSameLemma(a: String, b: String): Boolean {
      if (a.equals(b, ignoreCase = true)) return true
      val n = minOf(4, a.length, b.length)
      return n >= 3 && a.regionMatches(0, b, 0, n, ignoreCase = true)
    }

    private fun dropLeadingVerb(input: GrammarInput, verb: String?): String {
      val firstToken = input.firstToken()
      return if (verb != null && firstToken != null && looksSameLemma(firstToken, verb)) {
        input.bodyDropFirst()
      } else {
        input.body()
      }
    }

    /** Baseline "caveman" flavor: telegraphic, verb-first, intent-marked. */
    fun cavemanTemplates(): Map<Intent, GrammarTemplate> = linkedMapOf(
      Intent.QUESTION to SimpleGrammarTemplate("caveman-question") { input ->
        val q = input.signal.interrogative ?: "why"
        "${input.body()} $q?"
      },
      Intent.REQUEST to SimpleGrammarTemplate("caveman-request") { input ->
        val verb = input.signal.leadVerb
        val rest = dropLeadingVerb(input, verb)
        when {
          verb == null -> "explain ${input.body()}"
          rest.isEmpty() -> verb
          else -> "$verb $rest"
        }
      },
      Intent.DESIRE to SimpleGrammarTemplate("caveman-desire") { input ->
        val rest = dropDesireVerb(input)
        "me want ${if (rest.isEmpty()) input.body() else rest}"
      },
      Intent.PROBLEM to SimpleGrammarTemplate("caveman-problem") { input ->
        "${input.body()} cause problem. fix how?"
      },
      Intent.DESCRIPTION to SimpleGrammarTemplate("caveman-description") { input -> input.body() },
    )

    private fun dropDesireVerb(input: GrammarInput): String {
      val first = input.firstToken() ?: return ""
      return if (DESIRE_VERBS.any { looksSameLemma(first, it) }) input.bodyDropFirst() else input.body()
    }

    private val DESIRE_VERBS = listOf("want", "need", "like", "wish")

    /** Terse-imperative flavor: no caveman pronouns, colon-delimited verb. */
    fun terseImperativeTemplates(): Map<Intent, GrammarTemplate> = linkedMapOf(
      Intent.QUESTION to SimpleGrammarTemplate("terse-question") { "${it.body()}?" },
      Intent.REQUEST to SimpleGrammarTemplate("terse-request") { input ->
        val verb = input.signal.leadVerb
        val rest = dropLeadingVerb(input, verb)
        if (verb == null) input.body() else "$verb: $rest"
      },
      Intent.DESIRE to SimpleGrammarTemplate("terse-desire") { "want: ${it.body()}" },
      Intent.PROBLEM to SimpleGrammarTemplate("terse-problem") { "problem: ${it.body()}" },
      Intent.DESCRIPTION to SimpleGrammarTemplate("terse-description") { it.body() },
    )

    fun caveman(classifier: IntentClassifier = HeuristicIntentClassifier()): GrammarTemplateEngine =
      GrammarTemplateEngine(classifier, cavemanTemplates(), PASSTHROUGH, "caveman")

    fun terseImperative(classifier: IntentClassifier = HeuristicIntentClassifier()): GrammarTemplateEngine =
      GrammarTemplateEngine(classifier, terseImperativeTemplates(), PASSTHROUGH, "terse-imperative")

    /** Telegraphic flavor: bare token stream, no scaffold at all. */
    fun telegraphic(classifier: IntentClassifier = HeuristicIntentClassifier()): GrammarTemplateEngine =
      GrammarTemplateEngine(classifier, emptyMap(), PASSTHROUGH, "telegraphic")
  }
}