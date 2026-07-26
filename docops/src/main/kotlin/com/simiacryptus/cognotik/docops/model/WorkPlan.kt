package com.simiacryptus.cognotik.docops.model

import com.simiacryptus.cognotik.docops.exec.DocTaskKind

/** Tasks that must run sequentially (they touch overlapping files). */
data class TaskQueue<K : DocTaskKind>(val tasks: List<PlannedTask<K>>) {
  val isEmpty: Boolean get() = tasks.isEmpty()
}

/** The complete, immutable, side-effect-free output of planning. */
data class WorkPlan<K : DocTaskKind>(
  val queues: List<TaskQueue<K>> = emptyList(),
  val skipped: List<BuildOutcome.Skipped<K>> = emptyList(),
  val failed: List<BuildOutcome.Failed<K>> = emptyList(),
) {
  val tasks: List<PlannedTask<K>> get() = queues.flatMap { it.tasks }
  val isEmpty: Boolean get() = queues.all { it.isEmpty }

  /** Narrow a plan to a subset of targets (used by hosts that render a single file). */
  fun filter(predicate: (PlannedTask<K>) -> Boolean): WorkPlan<K> = copy(
    queues = queues.map { TaskQueue(it.tasks.filter(predicate)) }.filter { !it.isEmpty }
  )
}