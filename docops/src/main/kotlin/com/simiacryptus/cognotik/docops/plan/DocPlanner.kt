package com.simiacryptus.cognotik.docops.plan

import com.simiacryptus.cognotik.docops.exec.DocTaskKind
import com.simiacryptus.cognotik.docops.model.*
import com.simiacryptus.cognotik.docops.schedule.DependencySorter
import com.simiacryptus.cognotik.docops.schedule.QueuePartitioner
import org.slf4j.LoggerFactory

/**
 * Pure planning: resolvers -> fixpoint expansion -> [TaskBuilder] -> partition -> sort.
 * Nothing here mutates the workspace (target deletion is deferred to the runner).
 */
class DocPlanner<K : DocTaskKind>(
  private val taskBuilder: TaskBuilder<K>,
  private val resolvers: List<TargetResolver> = defaultTargetResolvers,
  private val partitioner: QueuePartitioner<K> = QueuePartitioner(),
  private val sorter: DependencySorter<K> = DependencySorter(),
  private val maxDepth: Int = 10,
) {

  /** Fixpoint over hypothetical transform destinations (`a.proto -> a.kt -> a.docs.md`). */
  fun index(specs: List<DocSpec>, ctx: ResolveContext): TargetIndex {
    var index = TargetIndex.of(resolvers.flatMap { it.contributions(specs, ctx) })
    var frontier: Collection<TargetPath> = index.targets.toList()
    var depth = 0
    while (frontier.isNotEmpty()) {
      if (depth >= maxDepth) {
        log.warn("Recursive planning reached max depth ($maxDepth); this usually means a circular transform rule.")
        break
      }
      val discovered = TransformTargetResolver.hypothetical(specs, frontier, index.targets)
      if (discovered.isEmpty()) break
      log.info("Depth $depth: discovered ${discovered.size} transitive target(s)")
      index = index.merge(discovered)
      frontier = discovered.map { it.target }.distinct()
      depth++
    }
    log.info("Planning discovered ${index.size} target(s)")
    return index
  }

  fun plan(specs: List<DocSpec>, ctx: ResolveContext): WorkPlan<K> {
    val planned = ArrayList<PlannedTask<K>>()
    val skipped = ArrayList<BuildOutcome.Skipped<K>>()
    val failed = ArrayList<BuildOutcome.Failed<K>>()

    for ((target, contributions) in index(specs, ctx).entriesSorted()) {
      when (val outcome = taskBuilder.build(target, contributions, ctx)) {
        is BuildOutcome.Planned -> planned.add(outcome.planned)
        is BuildOutcome.Skipped -> skipped.add(outcome)
        is BuildOutcome.Failed -> failed.add(outcome)
      }
    }

    val queues = partitioner.partition(planned)
      .map { TaskQueue(sorter.sort(it.tasks)) }
      .filter { !it.isEmpty }

    log.info("Planned ${planned.size} task(s) in ${queues.size} queue(s); skipped=${skipped.size}, failed=${failed.size}")
    return WorkPlan(queues = queues, skipped = skipped, failed = failed)
  }

  companion object {
    private val log = LoggerFactory.getLogger(DocPlanner::class.java)
  }
}