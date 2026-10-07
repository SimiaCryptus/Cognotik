package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Shared helpers for presenting usage data (used by both the REST/HTML servlet and GraphQL).
 */
internal object UsageTokens {

  /**
   * Total tokens = sum of top-level token types only. Sub-types (e.g. Cached is a
   * subset of Prompt, Thinking is a subset of Completion) are not double counted.
   */
  fun totalTokens(counts: Map<TokenTypes, Long>): Long =
    counts.filterKeys { it.parent == null }.values.sum()

  fun totalTokens(usage: ModelSchema.Usage): Long =
    if (usage.counts.isNotEmpty()) totalTokens(usage.counts) else usage.total_tokens

  fun mergeCounts(counts: Iterable<Map<TokenTypes, Long>>): Map<TokenTypes, Long> {
    val merged = LinkedHashMap<TokenTypes, Long>()
    counts.forEach { m -> m.forEach { (k, v) -> merged.merge(k, v, Long::plus) } }
    return merged
  }

  /**
   * Stable column order: Prompt and Completion first (when present), then the remaining
   * types in enum declaration order. Falls back to Prompt/Completion if nothing is present.
   */
  fun orderTokenTypes(present: Collection<TokenTypes>): List<TokenTypes> {
    val presentSet = present.toSet()
    val ordered = mutableListOf<TokenTypes>()
    if (TokenTypes.Prompt in presentSet) ordered.add(TokenTypes.Prompt)
    if (TokenTypes.Completion in presentSet) ordered.add(TokenTypes.Completion)
    TokenTypes.values().forEach { t ->
      if (t in presentSet && t !in ordered) ordered.add(t)
    }
    if (ordered.isEmpty()) {
      ordered.add(TokenTypes.Prompt)
      ordered.add(TokenTypes.Completion)
    }
    return ordered
  }

  /** Default range: last 30 days through tomorrow (exclusive), UTC. */
  fun defaultRange(): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now(ZoneOffset.UTC)
    return today.minusDays(30) to today.plusDays(1)
  }

  fun parseDateOrNull(s: String?): LocalDate? = try {
    if (s.isNullOrBlank()) null else LocalDate.parse(s.trim())
  } catch (e: DateTimeParseException) {
    null
  }

  /** Parses a (from, to) range, falling back to defaults on missing/invalid/inverted input. */
  fun parseRange(fromStr: String?, toStr: String?): Pair<LocalDate, LocalDate> {
    val (defaultFrom, defaultTo) = defaultRange()
    val from = parseDateOrNull(fromStr) ?: defaultFrom
    val to = parseDateOrNull(toStr) ?: defaultTo
    return if (to.isBefore(from)) defaultFrom to defaultTo else from to to
  }
}