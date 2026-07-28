package com.simiacryptus.cognotik.text.caveman.optional

import com.simiacryptus.cognotik.text.caveman.CavemanToken
import com.simiacryptus.cognotik.text.caveman.PosCategory
import com.simiacryptus.cognotik.text.caveman.PosTagger
import opennlp.tools.postag.POSModel
import opennlp.tools.postag.POSTagger
import opennlp.tools.postag.POSTaggerME
import java.io.InputStream

/**
 * Optional adapter for the maximum-entropy / perceptron POS taggers shipped with Apache
 * OpenNLP (spec 6 "Natural language POS tagging toolkit"). These are classical statistical
 * models — not neural networks — and run fully offline from a local model file.
 *
 * OpenNLP is a **compileOnly** dependency: consumers must supply opennlp-tools plus a model
 * on their own classpath. Tagging is applied to surface forms, so this remains correct even
 * when placed after the stemming stage.
 */
class OpenNlpPosTagger(
  private val tagger: POSTagger,
  override val id: String = "opennlp",
) : PosTagger {

  override fun tag(tokens: List<CavemanToken>): List<PosCategory> {
    if (tokens.isEmpty()) return emptyList()
    val surfaces = Array(tokens.size) { tokens[it].surface }
    return tagger.tag(surfaces).map { mapPennTag(it) }
  }

  companion object {

    /** Loads e.g. `en-pos-maxent.bin`; the stream is fully consumed and closed by OpenNLP. */
    fun fromModel(model: InputStream): OpenNlpPosTagger = OpenNlpPosTagger(POSTaggerME(POSModel(model)))

    fun mapPennTag(tag: String): PosCategory = when {
      tag.startsWith("NN") -> PosCategory.NOUN
      tag.startsWith("VB") || tag == "MD" -> PosCategory.VERB
      tag.startsWith("JJ") -> PosCategory.ADJECTIVE
      tag.startsWith("RB") -> PosCategory.ADVERB
      tag == "CD" -> PosCategory.NUMBER
      tag.startsWith("PRP") || tag == "WP" -> PosCategory.PRONOUN
      else -> PosCategory.OTHER
    }

    val isAvailable: Boolean by lazy {
      try {
        Class.forName("opennlp.tools.postag.POSTaggerME")
        true
      } catch (e: Throwable) {
        false
      }
    }
  }
}