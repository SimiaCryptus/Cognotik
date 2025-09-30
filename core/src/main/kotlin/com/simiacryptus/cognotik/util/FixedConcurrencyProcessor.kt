package com.simiacryptus.cognotik.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

/**
 * A utility class to manage concurrent task execution with a fixed concurrency limit.
 *
 * @param pool The executor service to use for executing tasks.
 * @param concurrencyLimit The maximum number of concurrent tasks allowed.
 */
class FixedConcurrencyProcessor(
    val pool: ExecutorService,
    val concurrencyLimit: Int
) {
    companion object {
        val log = LoggerFactory.getLogger(FixedConcurrencyProcessor::class.java)!!
    }

    init {
        require(concurrencyLimit > 0) { "Concurrency limit must be greater than zero." }
        log.info("Initializing FixedConcurrencyProcessor with concurrency limit of {}", concurrencyLimit)
    }

    private val activeTasks = AtomicInteger(0)
    private val waitingTasks = ConcurrentLinkedQueue<PendingTask<*>>()
    private val taskCounter = AtomicInteger(0)

    private data class PendingTask<T>(
        val taskId: Int,
        val task: () -> T,
        val future: CompletableFuture<T>
    )


    /**
     * Submits a task for execution with concurrency control.
     * The task will be executed when a slot becomes available.
     *
     * @param task The task to execute
     * @return A CompletableFuture representing the pending completion of the task
     */
    fun <T> submit(task: () -> T): CompletableFuture<T> {
        val taskId = taskCounter.incrementAndGet()
        val future = CompletableFuture<T>()
        val pendingTask = PendingTask(taskId, task, future)

        log.debug("Task #{} submitted", taskId)

        // Try to execute immediately if under limit
        if (tryExecuteTask(pendingTask)) {
            log.debug("Task #{} executing immediately", taskId)
        } else {
            // Add to waiting queue
            waitingTasks.offer(pendingTask)
            log.debug("Task #{} added to waiting queue. Queue size: {}", taskId, waitingTasks.size)
        }
        return future
    }

    /**
     * Attempts to execute a task if under the concurrency limit.
     *
     * @return true if the task was executed, false if it should be queued
     */
    private fun <T> tryExecuteTask(pendingTask: PendingTask<T>): Boolean {
        val currentActive = activeTasks.get()
        if (currentActive >= concurrencyLimit) {
            return false
        }
        // Try to increment active count atomically
        if (!activeTasks.compareAndSet(currentActive, currentActive + 1)) {
            // Another thread beat us, retry
            return tryExecuteTask(pendingTask)
        }
        // We got a slot, execute the task
        executeTask(pendingTask)
        return true
    }

    /**
     * Executes a task asynchronously and handles completion.
     */
    private fun <T> executeTask(pendingTask: PendingTask<T>) {
        log.debug(
            "Task #{}: Starting execution. Active tasks: {}",
            pendingTask.taskId, activeTasks.get()
        )
        CompletableFuture.supplyAsync({
            try {
                log.debug(
                    "Task #{}: Executing on thread {}",
                    pendingTask.taskId, Thread.currentThread().name
                )
                val result = pendingTask.task()
                log.debug("Task #{}: Execution completed", pendingTask.taskId)
                result
            } catch (e: Exception) {
                log.error("Task #{}: Execution failed", pendingTask.taskId, e)
                throw e
            }
        }, pool).whenComplete { result, throwable ->
            // Decrement active count
            val newActive = activeTasks.decrementAndGet()
            log.debug(
                "Task #{}: Released slot. Active tasks: {}",
                pendingTask.taskId, newActive
            )
            // Complete the original future
            if (throwable != null) {
                pendingTask.future.completeExceptionally(throwable)
            } else {
                pendingTask.future.complete(result)
            }
            // Try to process next waiting task
            processNextWaitingTask()
        }
    }

    /**
     * Processes the next task from the waiting queue if a slot is available.
     */
    private fun processNextWaitingTask() {
        val nextTask = waitingTasks.poll()
        if (nextTask != null) {
            log.debug("Processing waiting task #{}", nextTask.taskId)
            if (!tryExecuteTask(nextTask)) {
                // If we couldn't execute it (race condition), put it back
                waitingTasks.offer(nextTask)
            }
        }
    }

    /**
     * Gets the current number of active tasks.
     *
     * @return The number of active tasks
     */
    fun getActiveTaskCount(): Int = activeTasks.get()

    /**
     * Gets the current number of waiting tasks.
     *
     * @return The number of waiting tasks
     */
    fun getWaitingTaskCount(): Int = waitingTasks.size

    /**
     * Shuts down the processor, waiting for all tasks to complete.
     */
    fun shutdown() {
        log.info(
            "Shutting down FixedConcurrencyProcessor. Active tasks: {}, Waiting tasks: {}",
            getActiveTaskCount(), getWaitingTaskCount()
        )
        pool.shutdown()
        log.info("FixedConcurrencyProcessor shutdown completed")
    }
}