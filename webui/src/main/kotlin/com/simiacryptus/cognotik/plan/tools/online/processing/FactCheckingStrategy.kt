package com.simiacryptus.cognotik.plan.tools.online.processing

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.parserCast
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.jsonCast
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class FactCheckingStrategy : PageProcessingStrategy {

    data class FactCheckingConfig(
        @Description("Claims to verify")
        val claims_to_verify: List<String>,
        @Description("Required confidence level (0.0-1.0)")
        val confidence_threshold: Double = 0.8,
        @Description("Stop after finding N supporting sources")
        val required_sources: Int = 3,
        @Description("Stop after finding N contradicting sources")
        val contradiction_threshold: Int = 2
    )

    override val description: String // Describe both the strategy and its configuration
        get() = """Fact-Checking Strategy: Verifies specified claims against web page content. 
      |Configuration options include claims to verify, confidence thresholds, and source requirements.""".trimMargin()

    data class FactCheckResult(
        val claim: String,
        val verdict: FactVerdict,
        val confidence: Double,
        val supporting_evidence: List<Evidence>,
        val contradicting_evidence: List<Evidence>,
        val neutral_evidence: List<Evidence>
    )

    enum class FactVerdict {
        SUPPORTED,
        CONTRADICTED,
        INSUFFICIENT_EVIDENCE,
        MIXED
    }

    data class Evidence(
        val source_url: String,
        val excerpt: String,
        val relevance_score: Double,
        val credibility_score: Double
    )

    private val verificationResults = ConcurrentHashMap<String, MutableList<FactCheckResult>>()

    companion object {
        private val log = LoggerFactory.getLogger(FactCheckingStrategy::class.java)
    }

    override fun processPage(
        url: String,
        content: String,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.PageProcessingResult {
        val config =
            context.executionConfig.content_queries?.parserCast<FactCheckingConfig>(context.orchestrationConfig.defaultFast)
                ?: return PageProcessingStrategy.PageProcessingResult(
                    url = url,
                    pageType = CrawlerAgentTask.PageType.Error,
                    content = "Missing FactCheckingConfig",
                    extractedLinks = null,
                    metadata = emptyMap(),
                    shouldTerminate = true,
                    terminationReason = "Configuration error"
                )

        // Analyze page for each claim
        val pageResults = config.claims_to_verify.map { claim ->
            analyzeClaimEvidence(claim, content, url, context)
        }

        // Update global verification state
        pageResults.forEach { result ->
            verificationResults.getOrPut(result.claim) { mutableListOf() }.add(result)
        }

        // Check if we have enough evidence to terminate
        val shouldTerminate = checkTerminationConditions(config)

        return PageProcessingStrategy.PageProcessingResult(
            url = url,
            pageType = CrawlerAgentTask.PageType.OK,
            content = formatFactCheckResults(pageResults),
            extractedLinks = extractRelevantLinks(content, config.claims_to_verify),
            metadata = mapOf(
                "fact_check_results" to pageResults,
                "claims_analyzed" to config.claims_to_verify.size
            ),
            shouldTerminate = shouldTerminate,
            terminationReason = if (shouldTerminate) "Sufficient evidence gathered" else null
        )
    }

    private fun formatFactCheckResults(results: List<FactCheckResult>): String {
        return buildString {
            results.forEach { result ->
                appendLine("### Claim: ${result.claim}")
                appendLine("**Verdict:** ${result.verdict.name}")
                appendLine("**Confidence:** ${result.confidence}")
                appendLine()
                if (result.supporting_evidence.isNotEmpty()) {
                    appendLine("**Supporting Evidence:**")
                    result.supporting_evidence.forEach { evidence ->
                        appendLine("- ${evidence.excerpt} (relevance: ${evidence.relevance_score})")
                    }
                    appendLine()
                }
                if (result.contradicting_evidence.isNotEmpty()) {
                    appendLine("**Contradicting Evidence:**")
                    result.contradicting_evidence.forEach { evidence ->
                        appendLine("- ${evidence.excerpt} (relevance: ${evidence.relevance_score})")
                    }
                    appendLine()
                }
            }
        }
    }

    private fun extractRelevantLinks(content: String, claims: List<String>): List<CrawlerAgentTask.LinkData> {
        val linkPattern = Pattern.compile("""\[([^]]+)]\(([^)]+)\)""")
        val matcher = linkPattern.matcher(content)
        val links = mutableListOf<CrawlerAgentTask.LinkData>()
        while (matcher.find()) {
            val linkText = matcher.group(1)
            val linkUrl = matcher.group(2)
            // Check if link text is relevant to any claim
            val isRelevant = claims.any { claim ->
                linkText.contains(claim, ignoreCase = true) ||
                        claim.split(" ").any { word -> linkText.contains(word, ignoreCase = true) }
            }
            if (isRelevant) {
                links.add(
                    CrawlerAgentTask.LinkData(
                        url = linkUrl,
                        title = linkText,
                        relevance_score = 80.0
                    )
                )
            }
        }
        return links
    }

    private fun analyzeClaimEvidence(
        claim: String,
        content: String,
        url: String,
        context: PageProcessingStrategy.ProcessingContext
    ): FactCheckResult {
        val prompt = """
            Analyze the following content for evidence related to this claim:

            CLAIM: $claim

            Determine if the content:
            1. Supports the claim
            2. Contradicts the claim
            3. Is neutral/irrelevant

            Extract specific excerpts that serve as evidence.
            Rate the relevance (0.0-1.0) and credibility (0.0-1.0) of the source.
        """.trimIndent()

        val analysis = ParsedAgent(
            prompt = prompt,
            resultClass = FactCheckResult::class.java,
            model = context.orchestrationConfig.defaultFast.getChildClient(context.task),
            parsingChatter = context.orchestrationConfig.defaultFast.getChildClient(context.task)
        ).answer(listOf(content))

        return analysis.obj.copy(
            supporting_evidence = analysis.obj.supporting_evidence.map {
                it.copy(source_url = url)
            },
            contradicting_evidence = analysis.obj.contradicting_evidence.map {
                it.copy(source_url = url)
            }
        )
    }

    private fun checkTerminationConditions(config: FactCheckingConfig): Boolean {
        return config.claims_to_verify.all { claim ->
            val results = verificationResults[claim] ?: return@all false

            val supportCount = results.count { it.verdict == FactVerdict.SUPPORTED }
            val contradictCount = results.count { it.verdict == FactVerdict.CONTRADICTED }

            // Terminate if we have enough supporting OR contradicting evidence
            supportCount >= config.required_sources ||
                    contradictCount >= config.contradiction_threshold
        }
    }

    override fun shouldContinueCrawling(
        currentResults: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.ContinuationDecision {
        val anyTermination = currentResults.any { it.shouldTerminate }

        return PageProcessingStrategy.ContinuationDecision(
            shouldContinue = !anyTermination && context.processedCount.get() < context.maxPages,
            reason = if (anyTermination) {
                currentResults.first { it.shouldTerminate }.terminationReason ?: "Early termination"
            } else {
                "Continue gathering evidence"
            }
        )
    }

    override fun generateFinalOutput(
        results: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): String {
        val config = context.executionConfig.content_queries?.jsonCast<FactCheckingConfig>()
            ?: return "Error: Missing FactCheckingConfig"

        return buildString {
            appendLine("# Fact-Checking Report")
            appendLine()
            appendLine("**Generated:** ${LocalDateTime.now()}")
            appendLine("**Pages Analyzed:** ${results.size}")
            appendLine()

            config.claims_to_verify.forEach { claim ->
                appendLine("## Claim: $claim")
                appendLine()

                val claimResults = verificationResults[claim] ?: emptyList()
                val verdict = determineOverallVerdict(claimResults, config.confidence_threshold)

                appendLine("**Verdict:** ${verdict.name}")
                appendLine()

                appendLine("### Supporting Evidence (${claimResults.flatMap { it.supporting_evidence }.size})")
                claimResults.flatMap { it.supporting_evidence }.forEach { evidence ->
                    appendLine("- [${evidence.source_url}](${evidence.source_url})")
                    appendLine("  - Relevance: ${evidence.relevance_score}")
                    appendLine("  - Credibility: ${evidence.credibility_score}")
                    appendLine("  - Excerpt: \"${evidence.excerpt}\"")
                    appendLine()
                }

                appendLine("### Contradicting Evidence (${claimResults.flatMap { it.contradicting_evidence }.size})")
                claimResults.flatMap { it.contradicting_evidence }.forEach { evidence ->
                    appendLine("- [${evidence.source_url}](${evidence.source_url})")
                    appendLine("  - Relevance: ${evidence.relevance_score}")
                    appendLine("  - Credibility: ${evidence.credibility_score}")
                    appendLine("  - Excerpt: \"${evidence.excerpt}\"")
                    appendLine()
                }

                appendLine("---")
                appendLine()
            }
        }
    }

    private fun determineOverallVerdict(
        results: List<FactCheckResult>,
        threshold: Double
    ): FactVerdict {
        if (results.isEmpty()) return FactVerdict.INSUFFICIENT_EVIDENCE

        val avgConfidence = results.map { it.confidence }.average()
        val supportCount = results.count { it.verdict == FactVerdict.SUPPORTED }
        val contradictCount = results.count { it.verdict == FactVerdict.CONTRADICTED }

        return when {
            avgConfidence < threshold -> FactVerdict.INSUFFICIENT_EVIDENCE
            supportCount > contradictCount * 2 -> FactVerdict.SUPPORTED
            contradictCount > supportCount * 2 -> FactVerdict.CONTRADICTED
            else -> FactVerdict.MIXED
        }
    }

    override fun validateConfig(config: Any?): String? {
        return null
    }
}