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
    if (specs.isEmpty()) {
      log.warn("No doc specs supplied to the planner; no targets can be discovered.")
      return TargetIndex.EMPTY
    }
    val contributions = resolvers.flatMap { resolver ->
      val produced = resolver.contributions(specs, ctx)
      val label = resolver.javaClass.simpleName.ifEmpty { resolver.javaClass.name }
      if (produced.isEmpty()) log.info("$label produced 0 contribution(s)")
      else log.info("$label produced ${produced.size} contribution(s)")
      produced
    }
    var index = TargetIndex.of(contributions)
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
    if (index.size == 0) {
      log.warn(
        "No targets discovered from ${specs.size} spec(s) under ${ctx.root.absolutePath}. " +
            "Every declared pattern expanded to zero files - see the per-pattern messages above."
      )
    } else {
      index.entriesSorted().forEach { (target, contributions) ->
        log.info(
          "  target ${target.relativeToOrAbsolute(ctx.root)} <- " +
              contributions.joinToString(", ") { "${it.spec.docFile.name}/${it.kind}" +
                  (if (it.hypothetical) "(hypothetical)" else "") }
        )
      }
    }
    return index
  }

  fun plan(specs: List<DocSpec>, ctx: ResolveContext): WorkPlan<K> {
    val planned = ArrayList<PlannedTask<K>>()
    val skipped = ArrayList<BuildOutcome.Skipped<K>>()
    val failed = ArrayList<BuildOutcome.Failed<K>>()

    for ((target, contributions) in index(specs, ctx).entriesSorted()) {
      when (val outcome = taskBuilder.build(target, contributions, ctx)) {
        is BuildOutcome.Planned -> {
          planned.add(outcome.planned)
          log.info("Planned ${target} (task type ${outcome.planned.task.taskType.name})")
        }

        is BuildOutcome.Skipped -> {
          skipped.add(outcome)
          log.info("Skipped ${target}: ${outcome.reason}")
        }

        is BuildOutcome.Failed -> {
          failed.add(outcome)
          log.warn("Failed to plan ${target}: ${outcome.error.message ?: outcome.error.javaClass.simpleName}")
        }
      }
    }

    val queues = partitioner.partition(planned)
      .map { TaskQueue(sorter.sort(it.tasks)) }
      .filter { !it.isEmpty }

    log.info("Planned ${planned.size} task(s) in ${queues.size} queue(s); skipped=${skipped.size}, failed=${failed.size}")
    if (planned.isEmpty() && skipped.isNotEmpty()) {
      skipped.groupingBy { it.reason }.eachCount().forEach { (reason, count) ->
        log.warn("  no tasks planned - $count target(s) skipped because: $reason")
      }
    }
    return WorkPlan(queues = queues, skipped = skipped, failed = failed)
  }

  companion object {
    private val log = LoggerFactory.getLogger(DocPlanner::class.java)
  }
}