package com.simiacryptus.cognotik.docops.exec

import com.simiacryptus.cognotik.docops.DocOpsConfig
import com.simiacryptus.cognotik.docops.DocOpsHost
import com.simiacryptus.cognotik.docops.model.PlannedTask
import com.simiacryptus.cognotik.docops.model.WorkPlan
import com.simiacryptus.cognotik.docops.status.DocStatusStore
import com.simiacryptus.cognotik.docops.status.TaskStatus
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.CompletableFuture.allOf
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns scheduler submission, cancellation, timeouts, the (previously plan-time) target deletion
 * side effect, and every status transition.
 */
class DocTaskRunner<K : DocTaskKind, S : Any>(
  private val config: DocOpsConfig,
  private val host: DocOpsHost<K, S>,
  private val status: DocStatusStore,
  private val configFactory: ExecutionConfigFactory<K, S> =
    ExecutionConfigFactory(config.root, config.templateVarOverrides),
) {

  fun targetKeyOf(planned: PlannedTask<K>): String = planned.target.relativeToOrAbsolute(config.root)

  fun initializeStatus(plan: WorkPlan<K>) {
    status.initialize(plan.tasks.map { targetKeyOf(it) })
    plan.failed.forEach { outcome ->
      status.set(
        outcome.target.relativeToOrAbsolute(config.root),
        TaskStatus.FAILED,
        error = "Planning failed: ${outcome.error.message ?: outcome.error.javaClass.simpleName}",
      )
    }
  }

  fun run(
    plan: WorkPlan<K>,
    scheduler: DocTaskScheduler = host.newScheduler(),
    cancelFlag: AtomicBoolean = AtomicBoolean(false),
    onNewSession: (S) -> Unit = { },
  ): List<S> {
    initializeStatus(plan)
    val sessions: MutableList<S> = Collections.synchronizedList(mutableListOf())
    val queues = plan.queues.filter { !it.isEmpty }
    if (queues.isEmpty()) {
      log.warn(
        "Nothing to execute (empty plan): skipped=${plan.skipped.size}, failed=${plan.failed.size}. " +
            "No target matched any document declaration under ${config.root.absolutePath}."
      )
      plan.skipped.forEach { log.info("  skipped ${it.target}: ${it.reason}") }
      plan.failed.forEach { log.warn("  failed ${it.target}: ${it.error.message ?: it.error.javaClass.simpleName}") }
      return emptyList()
    }
    log.info(
      "Executing ${plan.tasks.size} task(s) in ${queues.size} queue(s); " +
          "overallTimeout=${config.overallTimeoutMinutes}m, taskTimeout=${config.taskTimeoutMinutes}m"
    )
    queues.forEachIndexed { i, queue ->
      log.info("  queue #$i (${queue.tasks.size} task(s)): ${queue.tasks.joinToString { targetKeyOf(it) }}")
    }
    val futures: Array<CompletableFuture<*>> = queues.map { queue ->
      scheduler.submit { runQueue(queue.tasks, cancelFlag, onNewSession, sessions) }
    }.toTypedArray()
    try {
      allOf(*futures).get(config.overallTimeoutMinutes, TimeUnit.MINUTES)
    } catch (e: TimeoutException) {
      log.error("DocOps timed out after ${config.overallTimeoutMinutes} minutes")
      status.markAllRunningAs(TaskStatus.FAILED, "Timed out after ${config.overallTimeoutMinutes} minutes")
      throw e
    } catch (e: ExecutionException) {
      log.error("DocOps execution failed", e)
      status.markAllRunningAs(TaskStatus.FAILED, "Execution failed: ${e.cause?.message ?: e.message}")
      throw e
    } catch (e: InterruptedException) {
      log.error("DocOps interrupted", e)
      status.markAllRunningAs(TaskStatus.CANCELLED, "Interrupted")
      throw e
    }
    log.info("DocOps run finished: ${sessions.size} session(s) started across ${queues.size} queue(s)")
    return sessions.toList()
  }

  private fun runQueue(
    tasks: List<PlannedTask<K>>,
    cancelFlag: AtomicBoolean,
    onNewSession: (S) -> Unit,
    sessions: MutableList<S>,
  ) {
    host.newExecutionContext().use { ctx ->
      if (cancelFlag.get()) {
        log.info("Cancellation requested, skipping queue of ${tasks.size} task(s)")
        tasks.forEach { status.set(targetKeyOf(it), TaskStatus.CANCELLED) }
        return@use
      }
      log.info("Starting queue of ${tasks.size} task(s): ${tasks.joinToString { targetKeyOf(it) }}")
      var index = 0
      try {
        while (index < tasks.size) {
          log.info("Queue progress ${index + 1}/${tasks.size}: ${targetKeyOf(tasks[index])}")
          runOne(tasks[index], ctx, cancelFlag, onNewSession, sessions)
          index++
        }
        log.info("Queue completed: ${tasks.size} task(s)")
      } catch (e: CancellationException) {
        log.info("Queue cancelled at task ${index + 1}/${tasks.size}; cancelling ${tasks.size - index - 1} remaining task(s)")
        tasks.drop(index + 1).forEach { status.set(targetKeyOf(it), TaskStatus.CANCELLED) }
      } catch (e: Throwable) {
        log.error(
          "Queue aborted at task ${index + 1}/${tasks.size} (${tasks.getOrNull(index)?.let { targetKeyOf(it) }}); " +
              "failing ${tasks.size - index - 1} remaining task(s)", e
        )
        tasks.drop(index + 1).forEach {
          status.set(
            targetKeyOf(it),
            TaskStatus.FAILED,
            error = "Queue aborted: ${e.message ?: e.javaClass.simpleName}",
          )
        }
      }
    }
  }

  fun runOne(
    planned: PlannedTask<K>,
    ctx: DocExecutionContext<K, S>,
    cancelFlag: AtomicBoolean,
    onNewSession: (S) -> Unit = { },
    sessions: MutableList<S> = mutableListOf(),
  ) {
    val targetKey = targetKeyOf(planned)
    val task = planned.task
    /*
     * The task's own root (the `folder:` override, otherwise the workspace root) is the single
     * source of truth for path resolution: it is handed to the host as the working directory
     * *and* used by ExecutionConfigFactory to relativize every path in the execution config.
     * TaskBuilder already stored it in `data.root`, so there is nothing to rebase here.
     */
    val workingDir = task.data.root
    if (!isSameFile(workingDir, config.root)) {
      log.info("Task '$targetKey' runs with root override: ${workingDir.absolutePath}")
    }
    log.info(
      "Preparing task '$targetKey': kind=${task.taskType.name}, workingDir=${workingDir.absolutePath}, " +
          "mainFile=${task.data.main_file?.absolutePath}, relatedFiles=${task.data.related_files?.size ?: 0}, " +
          "docFiles=${task.data.doc_files.joinToString { it.name }}"
    )

    ctx.reset()
    if (cancelFlag.get()) {
      log.info("Cancellation requested before task '$targetKey'")
      status.set(targetKey, TaskStatus.CANCELLED)
      throw CancellationException("Execution cancelled")
    }
    applyPreparation(planned)
    status.set(targetKey, TaskStatus.RUNNING)
    try {
      ctx.execute(
        DocTaskRequest(
          taskKind = task.taskType,
          message = task.message(),
          executionConfig = configFactory.build(planned, ctx),
          typeConfig = task.typeConfig,
          patchProcessor = task.patchProcessor,
          workingDir = workingDir,
          timeoutMinutes = config.taskTimeoutMinutes,
          frontmatter = task.frontmatter,
        ),
        object : DocTaskCallbacks<S> {
          override fun onSessionStarted(session: S, sessionId: String) {
            if (cancelFlag.get()) {
              log.info("Cancellation requested during startup of '$targetKey'")
              status.set(targetKey, TaskStatus.CANCELLED, sessionId = sessionId)
              throw CancellationException("Execution cancelled")
            }
            status.set(targetKey, TaskStatus.RUNNING, sessionId = sessionId)
            onNewSession(session)
            sessions += session
          }

          override fun onCompleted(sessionId: String) {
            log.info("Task completed for target '$targetKey' in session $sessionId")
            status.set(targetKey, TaskStatus.COMPLETED, sessionId = sessionId)
          }

          override fun onFailed(error: Throwable) {
            log.warn("Task failed for target '$targetKey': ${error.message}", error)
            status.set(targetKey, TaskStatus.FAILED, error = error.message ?: error.javaClass.simpleName)
          }
        }
      )
    } catch (e: CancellationException) {
      status.set(targetKey, TaskStatus.CANCELLED, error = e.message)
      throw e
    } catch (e: Throwable) {
      log.warn("Error executing task for target '$targetKey'", e)
      status.set(targetKey, TaskStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
      throw e
    }
  }

  /** The only destructive side effect, and it now happens just before execution. */
  private fun applyPreparation(planned: PlannedTask<K>) {
    if (!planned.preparation.deleteTargetBeforeRun) return
    val target = planned.target.file
    if (!target.exists()) return
    log.info("Deleting target file before processing: ${target.absolutePath}")
    if (!target.delete()) log.warn("Failed to delete target file: ${target.absolutePath}")
  }

  private fun isSameFile(a: File, b: File): Boolean = try {
    a.canonicalPath == b.canonicalPath
  } catch (e: Exception) {
    log.warn("Failed to compare paths: ${a.absolutePath} vs ${b.absolutePath}", e)
    a.absolutePath == b.absolutePath
  }


  companion object {
    private val log = LoggerFactory.getLogger(DocTaskRunner::class.java)
  }
}