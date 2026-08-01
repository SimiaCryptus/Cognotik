package com.simiacryptus.cognotik.text.caveman

  import org.junit.jupiter.api.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFalse
  import kotlin.test.assertTrue

  /** Covers the behavior categories enumerated in spec section 9. */
  class CavemanPipelineTest {

    private val keywords = CavemanConfig.keywordsOnly()
    private val porter = PorterStemmer()

    /** 9.1 stopword correctness */
    @Test
    fun `function words removed and content words retained`() {
      val out = Caveman.compress("The quick fix of the broken cluster is needed.", keywords)
      val tokens = out.split(" ")
      assertFalse(tokens.contains("the"))
      assertFalse(tokens.contains("of"))
      assertFalse(tokens.contains("is"))
      assertTrue(tokens.contains("cluster"))
      assertTrue(tokens.contains("need"))
    }

    /** 9.2 domain-term preservation correctness */
    @Test
    fun `domain terms are never stemmed nor removed`() {
      val config = keywords.copy(
        domainTerms = DomainTermRegistry.of("CRDT", "Raft", "IT", "eventual consistency")
      )
      val out = Caveman.compress("CRDT and Raft provide eventual consistency for IT teams.", config)
      assertTrue(out.contains("CRDT"), out)
      assertTrue(out.contains("Raft"), out)
      assertTrue(out.contains("eventual consistency"), out)
      // "it" is a default stopword, but the domain dictionary exempts it.
      assertTrue(out.contains("IT"), out)
    }

    /** 9.3 stemming correctness */
    @Test
    fun `inflectional variants collapse to canonical stems`() {
      assertEquals("run", porter.stem("running"))
      assertEquals("cat", porter.stem("cats"))
      assertEquals("process", porter.stem("processes"))
      assertEquals("connect", porter.stem("connected"))
      assertEquals("Cat", porter.stem("Cats"))
      assertEquals("k8s", porter.stem("k8s")) // non-alphabetic tokens untouched
    }

    /** 9.4 POS filtering correctness */
    @Test
    fun `pos filter retains only allowed categories`() {
      val config = keywords.withPosFilter(PosCategory.NOUN, PosCategory.VERB)
      val out = Caveman.compress("The robot quickly moves the extremely heavy box.", config)
      assertFalse(out.contains("quick"), out)
      assertFalse(out.contains("extreme"), out)
      assertTrue(out.contains("robot"), out)
      assertTrue(out.contains("box"), out)
    }

    /** 9.5 salience extraction correctness */
    @Test
    fun `salience retains only the highest ranked terms`() {
      val config = keywords.withSalience(topN = 2, extractor = FrequencySalienceExtractor())
      val out = Caveman.compress("cache cache cache latency latency network", config)
      assertEquals(listOf(porter.stem("cache"), porter.stem("latency")), out.split(" "))
    }

    /** 9.6 grammar reconstruction correctness */
    @Test
    fun `coarse intent selects the expected template`() {
      val question = Caveman.run("Why does the Raft leader election fail?")
      assertEquals(Intent.QUESTION, question.intent)
      assertEquals("caveman-question", question.template)
      assertTrue(question.output.endsWith("why?"), question.output)

      val request = Caveman.run("Please explain how the consensus algorithm elects a leader.")
      assertEquals(Intent.REQUEST, request.intent)
      assertTrue(request.output.startsWith("explain "), request.output)

      val desire = Caveman.run("I want a summary of the replication log design.")
      assertEquals(Intent.DESIRE, desire.intent)
      assertTrue(desire.output.startsWith("me want "), desire.output)

      val problem = Caveman.run("The scheduler crashes whenever the queue overflows.")
      assertEquals(Intent.PROBLEM, problem.intent)
      assertTrue(problem.output.endsWith("fix how?"), problem.output)
    }

    /** 9.7 end-to-end determinism */
    @Test
    fun `repeated invocations are byte identical`() {
      val text = "The distributed cache keeps failing during leader election; " +
          "the Raft cluster loses quorum and clients observe stale reads."
      val configs = listOf(
        CavemanConfig(),
        keywords,
        CavemanConfig.aggressive(topN = 5).withDomains(DomainTermRegistry.DISTRIBUTED_SYSTEMS),
        CavemanConfig(grammar = GrammarTemplateEngine.terseImperative()),
        CavemanConfig(stemmer = LightEnglishStemmer(), salienceEnabled = true, salienceTopN = 4),
        CavemanConfig(salienceEnabled = true, salienceExtractor = TextRankSalienceExtractor(), salienceTopN = 4),
      )
      configs.forEach { config ->
        val expected = Caveman.compress(text, config)
        repeat(10) { assertEquals(expected, Caveman.compress(text, config)) }
      }
    }

    /** 9.8 configuration isolation */
    @Test
    fun `disabling a stage leaves other stages intact`() {
      val noStemming = Caveman.compress("The clusters are replicating shards.", keywords.copy(stemmingEnabled = false))
      assertTrue(noStemming.contains("clusters"), noStemming)
      assertFalse(noStemming.contains(" the "), noStemming)

      val noStopwords = Caveman.compress("The clusters replicate.", keywords.copy(stopwordRemovalEnabled = false))
      assertTrue(noStopwords.startsWith("the "), noStopwords)

      val noGrammar = Caveman.run("Why did the cluster fail?", keywords)
      assertEquals(null, noGrammar.intent)
      assertFalse(noGrammar.output.endsWith("?"), noGrammar.output)
    }

    @Test
    fun `trace attributes every transformation`() {
      val result = Caveman.run(
        "The Raft leader election failed.",
        CavemanConfig(domainTerms = DomainTermRegistry.of("Raft"))
      )
      val stages = result.trace.map { it.stage }
      assertTrue(stages.contains("tokenization"))
      assertTrue(stages.contains("stopword-removal"))
      assertTrue(stages.contains("stemming"))
      assertTrue(stages.contains("grammar-reconstruction"))
      val stopwordStage = result.trace.first { it.stage == "stopword-removal" }
      assertTrue(stopwordStage.decisions.any { it.token == "the" && it.action == TokenAction.REMOVE })
      val stemStage = result.trace.first { it.stage == "stemming" }
      assertTrue(stemStage.decisions.any { it.token == "Raft" && it.action == TokenAction.KEEP })
      assertTrue(result.explain().contains("output:"))
    }

    @Test
    fun `empty and punctuation only input degrade gracefully`() {
      assertEquals("", Caveman.compress(""))
      assertEquals("", Caveman.compress("   ... !?  "))
      assertEquals("", Caveman.compress("the of and"))
    }

    @Test
    fun `multi sentence input collapses into a single token sequence`() {
      val out = Caveman.compress(
        "The queue overflows. The scheduler restarts. Clients retry forever.",
        keywords
      )
      assertFalse(out.contains("."))
      assertTrue(out.split(" ").size >= 4, out)
    }
  }