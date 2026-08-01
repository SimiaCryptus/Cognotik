package com.simiacryptus.cognotik.text.caveman

data class StageContext(val originalText: String, val config: CavemanConfig)

data class StageOutcome(val tokens: List<CavemanToken>, val decisions: List<Decision> = emptyList())

/**
 * One discrete, independently testable transformation (spec 2.3 composability).
 * Stages must be stateless and side-effect free.
 */
interface PipelineStage {
  val name: String
  fun isEnabled(config: CavemanConfig): Boolean = true
  fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome
}

object TokenizationStage : PipelineStage {
  override val name: String = "tokenization"
  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome =
    StageOutcome(
      ctx.config.analyzer.tokenize(ctx.originalText).map {
        CavemanToken(surface = it.text, text = it.text, position = it.position, sentenceIndex = it.sentenceIndex)
      }
    )
}

object CaseNormalizationStage : PipelineStage {
  override val name: String = "case-normalization"
  override fun isEnabled(config: CavemanConfig): Boolean = config.lowercase
  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome {
    val decisions = ArrayList<Decision>()
    val out = tokens.map { token ->
      val lower = token.text.lowercase()
      if (lower != token.text) decisions.add(Decision(token.text, TokenAction.REWRITE, "case normalization", lower))
      token.copy(text = lower)
    }
    return StageOutcome(out, decisions)
  }
}

/**
 * Merges multi-word domain phrases into single tokens BEFORE stopword removal, so phrases
 * containing function words (e.g. "two-phase commit", "eventual consistency") survive intact.
 */
object DomainPhraseMergeStage : PipelineStage {
  override val name: String = "domain-phrase-merge"
  override fun isEnabled(config: CavemanConfig): Boolean =
    config.domainTermsEnabled && config.domainTerms.maxPhraseLength > 1

  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome {
    val registry = ctx.config.domainTerms
    val keys = tokens.map { it.key }
    val out = ArrayList<CavemanToken>(tokens.size)
    val decisions = ArrayList<Decision>()
    var i = 0
    while (i < tokens.size) {
      val len = registry.matchPhrase(keys, i)
      if (len > 1) {
        val slice = tokens.subList(i, i + len)
        val joinedKey = keys.subList(i, i + len).joinToString(" ")
        val surface = slice.joinToString(" ") { it.surface }
        val canonical = registry.canonicalFor(joinedKey) ?: surface
        val text = if (ctx.config.preserveDomainTermCase) canonical else canonical.lowercase()
        out.add(
          CavemanToken(
            surface = surface,
            text = text,
            position = slice.first().position,
            sentenceIndex = slice.first().sentenceIndex,
            domainTerm = true,
          )
        )
        decisions.add(Decision(surface, TokenAction.MERGE, "matched multi-word domain term", text))
        i += len
      } else {
        out.add(tokens[i])
        i++
      }
    }
    return StageOutcome(out, decisions)
  }
}

object ShortTokenFilterStage : PipelineStage {
  override val name: String = "short-token-filter"
  override fun isEnabled(config: CavemanConfig): Boolean = config.minTokenLength > 1
  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome {
    val min = ctx.config.minTokenLength
    val decisions = ArrayList<Decision>()
    val out = tokens.filter { token ->
      val keep = token.domainTerm || token.text.length >= min
      if (!keep) decisions.add(Decision(token.text, TokenAction.REMOVE, "shorter than minTokenLength=$min"))
      keep
    }
    return StageOutcome(out, decisions)
  }
}

/**
 * Removes function words. Domain terms are exempt even when they collide with the stopword
 * list (spec 3.4: "never removed by stopword filtering").
 */
object StopwordRemovalStage : PipelineStage {
  override val name: String = "stopword-removal"
  override fun isEnabled(config: CavemanConfig): Boolean = config.stopwordRemovalEnabled
  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome {
    val stopwords = ctx.config.stopwords
    val registry = ctx.config.domainTerms
    val domainsOn = ctx.config.domainTermsEnabled
    val decisions = ArrayList<Decision>()
    val out = ArrayList<CavemanToken>(tokens.size)
    for (token in tokens) {
      val isStopword = stopwords.contains(token.text)
      val exempt =
        token.domainTerm || (domainsOn && (registry.contains(token.text) || registry.contains(token.surface)))
      when {
        isStopword && exempt ->
          decisions.add(Decision(token.text, TokenAction.KEEP, "domain-dictionary exemption from stopword removal"))

        isStopword -> {
          decisions.add(Decision(token.text, TokenAction.REMOVE, "matched stopword list"))
          continue
        }
      }
      out.add(token)
    }
    return StageOutcome(out, decisions)
  }
}

/** Flags tokens as domain terms, exempting them from stemming and restoring canonical case. */
object DomainTermMarkingStage : PipelineStage {
  override val name: String = "domain-term-marking"
  override fun isEnabled(config: CavemanConfig): Boolean = config.domainTermsEnabled
  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome {
    val registry = ctx.config.domainTerms
    val decisions = ArrayList<Decision>()
    val out = tokens.map { token ->
      if (token.domainTerm) return@map token
      val canonical = registry.canonicalFor(token.text) ?: registry.canonicalFor(token.surface)
      if (canonical == null) {
        token
      } else {
        val text = if (ctx.config.preserveDomainTermCase) canonical else canonical.lowercase()
        decisions.add(Decision(token.text, TokenAction.KEEP, "matched domain dictionary", text))
        token.copy(text = text, domainTerm = true)
      }
    }
    return StageOutcome(out, decisions)
  }
}

object StemmingStage : PipelineStage {
  override val name: String = "stemming"
  override fun isEnabled(config: CavemanConfig): Boolean =
    config.stemmingEnabled && config.stemmer !== NoOpStemmer

  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome {
    val stemmer = ctx.config.stemmer
    val decisions = ArrayList<Decision>()
    val out = tokens.map { token ->
      if (token.domainTerm) {
        decisions.add(Decision(token.text, TokenAction.KEEP, "domain term exempt from stemming"))
        token
      } else {
        val stem = stemmer.stem(token.text)
        if (stem != token.text) {
          decisions.add(Decision(token.text, TokenAction.REWRITE, "stemmed by ${stemmer.id}", stem))
          token.copy(text = stem)
        } else {
          token
        }
      }
    }
    return StageOutcome(out, decisions)
  }
}

object PosFilterStage : PipelineStage {
  override val name: String = "pos-filter"
  override fun isEnabled(config: CavemanConfig): Boolean = config.posFilterEnabled
  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome {
    if (tokens.isEmpty()) return StageOutcome(tokens)
    val tags = ctx.config.posTagger.tag(tokens)
    require(tags.size == tokens.size) {
      "PosTagger ${ctx.config.posTagger.id} returned ${tags.size} tags for ${tokens.size} tokens"
    }
    val allowed = ctx.config.allowedPos
    val decisions = ArrayList<Decision>()
    val out = ArrayList<CavemanToken>(tokens.size)
    tokens.forEachIndexed { i, token ->
      val pos = tags[i]
      val tagged = token.copy(pos = pos)
      when {
        token.domainTerm -> {
          decisions.add(Decision(token.text, TokenAction.KEEP, "domain term retained regardless of POS", pos.name))
          out.add(tagged)
        }

        allowed.contains(pos) -> out.add(tagged)
        else -> decisions.add(Decision(token.text, TokenAction.REMOVE, "POS $pos not in allow-list $allowed"))
      }
    }
    return StageOutcome(out, decisions)
  }
}

object SalienceStage : PipelineStage {
  override val name: String = "salience-extraction"
  override fun isEnabled(config: CavemanConfig): Boolean = config.salienceEnabled
  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome {
    if (tokens.isEmpty()) return StageOutcome(tokens)
    val config = ctx.config
    val scores = config.salienceExtractor.score(tokens)
    val decisions = ArrayList<Decision>()
    var kept: List<CavemanToken> = tokens.map { it.copy(salience = scores[it.key] ?: 0.0) }

    val threshold = config.salienceThreshold
    if (threshold != null) {
      kept = kept.filter { token ->
        val keep = (config.salienceAlwaysKeepDomainTerms && token.domainTerm) ||
            (token.salience ?: 0.0) >= threshold
        if (!keep) {
          decisions.add(
            Decision(token.text, TokenAction.REMOVE, "salience ${format(token.salience)} below threshold $threshold")
          )
        }
        keep
      }
    }

    val topN = config.salienceTopN
    if (topN != null && topN > 0) {
      val ranked = kept
        .distinctBy { it.key }
        .sortedWith(compareByDescending<CavemanToken> { it.salience ?: 0.0 }.thenBy { it.position })
      val allowed = LinkedHashSet(ranked.take(topN).map { it.key })
      if (config.salienceAlwaysKeepDomainTerms) allowed.addAll(kept.filter { it.domainTerm }.map { it.key })
      kept = kept.filter { token ->
        val keep = allowed.contains(token.key)
        if (!keep) {
          decisions.add(
            Decision(token.text, TokenAction.REMOVE, "outside top-$topN salience ranking", format(token.salience))
          )
        }
        keep
      }
    }

    if (config.reorderBySalience) {
      kept = kept.sortedWith(compareByDescending<CavemanToken> { it.salience ?: 0.0 }.thenBy { it.position })
      decisions.add(Decision("*", TokenAction.REWRITE, "tokens reordered by descending salience"))
    }
    return StageOutcome(kept, decisions)
  }

  private fun format(value: Double?): String = if (value == null) "n/a" else String.format("%.6f", value)
}

object DeduplicationStage : PipelineStage {
  override val name: String = "deduplication"
  override fun isEnabled(config: CavemanConfig): Boolean = config.deduplicate
  override fun apply(tokens: List<CavemanToken>, ctx: StageContext): StageOutcome {
    val seen = HashSet<String>()
    val decisions = ArrayList<Decision>()
    val out = tokens.filter { token ->
      val fresh = seen.add(token.key)
      if (!fresh) decisions.add(Decision(token.text, TokenAction.REMOVE, "duplicate of earlier token"))
      fresh
    }
    return StageOutcome(out, decisions)
  }
}