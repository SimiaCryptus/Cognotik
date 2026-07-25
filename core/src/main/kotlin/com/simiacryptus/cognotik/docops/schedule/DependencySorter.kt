package com.simiacryptus.cognotik.docops.schedule

  import com.simiacryptus.cognotik.docops.exec.DocTaskKind
  import com.simiacryptus.cognotik.docops.model.PlannedTask
  import com.simiacryptus.cognotik.docops.model.TargetPath
  import org.slf4j.LoggerFactory

  /**
   * Kahn's algorithm over "my `related_files` contain your `main_file`". Index based (so duplicate
   * value-equal tasks cannot collapse) and deterministic: ready tasks and cycle-breaks are chosen by
   * target order, so a shuffled input yields a stable output.
   */
  class DependencySorter<K : DocTaskKind> {

    fun sort(tasks: List<PlannedTask<K>>): List<PlannedTask<K>> {
      if (tasks.size < 2) return tasks

      val indexByTarget = HashMap<String, Int>()
      tasks.forEachIndexed { i, task -> indexByTarget.putIfAbsent(task.target.key, i) }

      val deps: List<Set<Int>> = tasks.mapIndexed { i, task ->
        (task.task.data.related_files ?: emptyList())
          .mapNotNull { related ->
            try {
              indexByTarget[TargetPath.of(related).key]
            } catch (e: Exception) {
              log.warn("Failed to resolve related file path: $related", e)
              null
            }
          }
          .filter { it != i }
          .toSet()
      }

      val byTargetOrder = tasks.indices.sortedWith(compareBy({ tasks[it].target.key }, { it }))
      val done = HashSet<Int>()
      val remaining = LinkedHashSet(byTargetOrder)
      val result = ArrayList<PlannedTask<K>>(tasks.size)

      while (remaining.isNotEmpty()) {
        val ready = remaining.filter { deps[it].all { dep -> dep in done } }
        if (ready.isNotEmpty()) {
          ready.forEach { i ->
            result.add(tasks[i])
            done.add(i)
          }
          remaining.removeAll(ready.toSet())
        } else {
          val broken = remaining.minWithOrNull(
            compareBy({ deps[it].count { dep -> dep !in done } }, { tasks[it].target.key })
          )!!
          log.warn("Dependency cycle detected, breaking cycle by processing: ${tasks[broken].target}")
          result.add(tasks[broken])
          done.add(broken)
          remaining.remove(broken)
        }
      }
      log.info("Sorted ${tasks.size} task(s) by dependencies")
      return result
    }

    companion object {
      private val log = LoggerFactory.getLogger(DependencySorter::class.java)
    }
  }