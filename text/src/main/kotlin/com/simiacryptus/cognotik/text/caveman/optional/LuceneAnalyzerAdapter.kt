package com.simiacryptus.cognotik.text.caveman.optional

import com.simiacryptus.cognotik.text.caveman.RawToken
import com.simiacryptus.cognotik.text.caveman.TextAnalyzer
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute

/**
 * Optional adapter that delegates the Analysis Layer to any Lucene [Analyzer] chain
 * (spec 4.4 "Custom Analyzer Chains", 6 "General-purpose IR analysis toolkit").
 *
 * Lucene is a **compileOnly** dependency of this module: consumers that want this adapter
 * must add lucene-core (and typically lucene-analysis-common) to their own runtime
 * classpath. Guard usage with [isAvailable] when the classpath is not known statically.
 *
 * Note: Lucene token streams do not expose sentence boundaries, so all tokens are reported
 * in sentence 0. Salience co-occurrence windows therefore span the whole input.
 */
class LuceneAnalyzerAdapter(
  private val analyzer: Analyzer,
  private val field: String = "text",
  override val id: String = "lucene",
) : TextAnalyzer {

  override fun tokenize(text: String): List<RawToken> {
    val out = ArrayList<RawToken>()
    analyzer.tokenStream(field, text).use { stream ->
      val term = stream.addAttribute(CharTermAttribute::class.java)
      val offset = stream.addAttribute(OffsetAttribute::class.java)
      stream.reset()
      var position = 0
      while (stream.incrementToken()) {
        out.add(RawToken(term.toString(), position++, 0, offset.startOffset(), offset.endOffset()))
      }
      stream.end()
    }
    return out
  }

  companion object {
    val isAvailable: Boolean by lazy {
      try {
        Class.forName("org.apache.lucene.analysis.Analyzer")
        true
      } catch (e: Throwable) {
        false
      }
    }
  }
}