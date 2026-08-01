package com.simiacryptus.cognotik.docops.plan

import com.simiacryptus.cognotik.docops.model.TargetContribution
import com.simiacryptus.cognotik.docops.model.TargetPath

/** Immutable `target -> contributions` index. Replaces the four ad-hoc match maps. */
class TargetIndex private constructor(
  private val map: Map<TargetPath, List<TargetContribution>>,
) {

  val targets: Set<TargetPath> get() = map.keys
  val size: Int get() = map.size

  operator fun get(target: TargetPath): List<TargetContribution> = map[target] ?: emptyList()

  /** Deterministic iteration order (sorted by case-normalized key). */
  fun entriesSorted(): List<Pair<TargetPath, List<TargetContribution>>> =
    map.entries.sortedBy { it.key }.map { it.key to it.value }

  fun merge(more: List<TargetContribution>): TargetIndex {
    if (more.isEmpty()) return this
    val merged = LinkedHashMap<TargetPath, MutableList<TargetContribution>>()
    map.forEach { (k, v) -> merged[k] = v.toMutableList() }
    more.forEach { merged.getOrPut(it.target) { mutableListOf() }.add(it) }
    return TargetIndex(merged.mapValues { it.value.toList() })
  }

  companion object {
    val EMPTY = TargetIndex(emptyMap())

    fun of(contributions: List<TargetContribution>): TargetIndex {
      val grouped = LinkedHashMap<TargetPath, MutableList<TargetContribution>>()
      contributions.forEach { grouped.getOrPut(it.target) { mutableListOf() }.add(it) }
      return TargetIndex(grouped.mapValues { it.value.toList() })
    }
  }
}