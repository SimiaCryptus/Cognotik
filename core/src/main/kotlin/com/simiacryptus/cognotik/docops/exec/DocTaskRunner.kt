package com.simiacryptus.cognotik.docops.exec

  import com.simiacryptus.cognotik.docops.DocOpsConfig
  import com.simiacryptus.cognotik.docops.DocOpsHost
  import com.simiacryptus.cognotik.docops.model.PlannedTask
  import com.simiacryptus.cognotik.docops.model.WorkPlan
  import com.simiacryptus.cognotik.docops.status.DocStatusStore
  import com.simiacryptus.cognotik.docops.status.TaskStatus
  import org.slf4j.LoggerFactory
  import java.util.Collections
  import java.util.concurrent.CancellationException
  import java.util.concurrent.CompletableFuture
  import java.util.concurrent.CompletableFuture.allOf
  import java.util.concurrent.ExecutionException
  import java.util.concurrent.TimeUnit
  import java.util.concurrent.TimeoutException
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
        log.info("Nothing to execute (empty plan)")
        return emptyList()
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
        var index = 0
        try {
          while (index < tasks.size) {
            runOne(tasks[index], ctx, cancelFlag, onNewSession, sessions)
            index++
          }
        } catch (e: CancellationException) {
          tasks.drop(index + 1).forEach { status.set(targetKeyOf(it), TaskStatus.CANCELLED) }
        } catch (e: Throwable) {
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
      val effectiveRoot = planned.task.data.root
      val needsRebase = try {
        effectiveRoot.canonicalPath != config.root.canonicalPath
      } catch (e: Exception) {
        log.warn("Failed to compare roots: ${effectiveRoot.absolutePath} vs ${config.root.absolutePath}", e)
        false
      }
      val task = if (needsRebase) {
        log.info("Rebasing task into target folder: ${effectiveRoot.absolutePath}")
        planned.task.rebase(config.root, effectiveRoot)
      } else planned.task
      val rebased = planned.copy(task = task)
      val workingDir = task.data.main_file?.parentFile ?: config.root

      ctx.reset()
      if (cancelFlag.get()) {
        log.info("Cancellation requested before task '$targetKey'")
        status.set(targetKey, TaskStatus.CANCELLED)
        throw CancellationException("Execution cancelled")
      }
      applyPreparation(rebased)
      status.set(targetKey, TaskStatus.RUNNING)
      try {
        ctx.execute(
          DocTaskRequest(
            taskKind = task.taskType,
            message = task.message(),
            executionConfig = configFactory.build(rebased, ctx),
            typeConfig = task.typeConfig,
            patchProcessor = task.patchProcessor,
            workingDir = workingDir,
            timeoutMinutes = config.taskTimeoutMinutes,
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

    companion object {
      private val log = LoggerFactory.getLogger(DocTaskRunner::class.java)
    }
  }