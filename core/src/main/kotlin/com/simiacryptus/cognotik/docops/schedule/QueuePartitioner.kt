package com.simiacryptus.cognotik.docops.schedule

  import com.simiacryptus.cognotik.docops.exec.DocTaskKind
  import com.simiacryptus.cognotik.docops.model.PlannedTask
  import com.simiacryptus.cognotik.docops.model.TargetPath
  import com.simiacryptus.cognotik.docops.model.TaskQueue
  import org.slf4j.LoggerFactory

  /**
   * Conservative overlap avoidance: a task that touches an already-seen target or related file
   * starts a new queue. Queues run concurrently, tasks within a queue run sequentially.
   */
  class QueuePartitioner<K : DocTaskKind> {

    fun partition(tasks: List<PlannedTask<K>>): List<TaskQueue<K>> {
      val queues = mutableListOf<MutableList<PlannedTask<K>>>()
      val seen = HashSet<String>()
      for (task in tasks) {
        val targetKey = task.target.key
        val relatedKeys = (task.task.data.related_files ?: emptyList()).map { TargetPath.of(it).key }
        val overlaps = targetKey in seen || relatedKeys.any { it in seen }
        if (overlaps || queues.isEmpty()) queues.add(mutableListOf(task)) else queues.last().add(task)
        seen.add(targetKey)
        seen.addAll(relatedKeys)
      }
      log.info("Separated ${tasks.size} task(s) into ${queues.size} queue(s) based on file relationships")
      return queues.map { TaskQueue(it.toList()) }
    }

    companion object {
      private val log = LoggerFactory.getLogger(QueuePartitioner::class.java)
    }
  }