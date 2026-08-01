package com.simiacryptus.cognotik.text.caveman

/**
 * Single configuration object controlling every stage (spec 4.3). Every field has a default,
 * every stage is independently toggleable, and no field carries mutable or ambient state,
 * which is what makes the pipeline deterministic (spec 8.1).
 */
data class CavemanConfig(

  /** Analysis layer -------------------------------------------------------------- */
  val analyzer: TextAnalyzer = DefaultEnglishAnalyzer(),
  val lowercase: Boolean = true,
  val minTokenLength: Int = 1,

  val stopwordRemovalEnabled: Boolean = true,
  val stopwords: StopwordProvider = StopwordProvider.english(),

  val domainTermsEnabled: Boolean = true,
  val domainTerms: DomainTermRegistry = DomainTermRegistry.empty(),
  val preserveDomainTermCase: Boolean = true,

  val stemmingEnabled: Boolean = true,
  val stemmer: Stemmer = PorterStemmer(),

  val posFilterEnabled: Boolean = false,
  val posTagger: PosTagger = HeuristicPosTagger(),
  val allowedPos: Set<PosCategory> = setOf(PosCategory.NOUN, PosCategory.VERB),

  /** Salience layer -------------------------------------------------------------- */
  val salienceEnabled: Boolean = false,
  val salienceExtractor: SalienceExtractor = FrequencySalienceExtractor(),
  val salienceTopN: Int? = null,
  val salienceThreshold: Double? = null,
  val salienceAlwaysKeepDomainTerms: Boolean = true,
  val reorderBySalience: Boolean = false,

  /** Grammar layer --------------------------------------------------------------- */
  val grammarEnabled: Boolean = true,
  val grammar: GrammarTemplateEngine = GrammarTemplateEngine.caveman(),

  /** Orchestration --------------------------------------------------------------- */
  val deduplicate: Boolean = true,
  val collectTrace: Boolean = true,
  /** Overrides the canonical stage ordering when non-null (spec 2.4 extensibility). */
  val stages: List<PipelineStage>? = null,
) {

  fun withDomains(vararg registries: DomainTermRegistry): CavemanConfig =
    copy(domainTerms = registries.fold(domainTerms) { acc, r -> acc.plus(r) }, domainTermsEnabled = true)

  fun withExtraStopwords(vararg words: String): CavemanConfig =
    copy(stopwords = stopwords.plus(words.toList()))

  fun withoutStopwords(vararg words: String): CavemanConfig =
    copy(stopwords = stopwords.minus(words.toList()))

  fun withSalience(topN: Int? = null, threshold: Double? = null, extractor: SalienceExtractor = salienceExtractor) =
    copy(salienceEnabled = true, salienceTopN = topN, salienceThreshold = threshold, salienceExtractor = extractor)

  fun withPosFilter(vararg allowed: PosCategory): CavemanConfig =
    copy(posFilterEnabled = true, allowedPos = if (allowed.isEmpty()) allowedPos else allowed.toSet())

  companion object {
    /** Maximum compression: POS filter + salience + caveman grammar. */
    fun aggressive(topN: Int = 8): CavemanConfig = CavemanConfig(
      posFilterEnabled = true,
      salienceEnabled = true,
      salienceExtractor = TextRankSalienceExtractor(),
      salienceTopN = topN,
      reorderBySalience = false,
    )

    /** Analysis-only: no POS filtering, no salience, no grammar scaffold. */
    fun keywordsOnly(): CavemanConfig = CavemanConfig(grammarEnabled = false)
  }
}