package com.simiacryptus.cognotik.util

import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
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
  private val semaphore = Semaphore(concurrencyLimit)
  private val activeThreads = mutableListOf<Thread>()
  private val waitingThreads = mutableListOf<Thread>()
  private val taskCounter = AtomicInteger(0)
  
  /**
   * Submits a task for execution with concurrency control.
   * The task will be executed when a permit is available from the semaphore.
   *
   * @param task The task to execute
   * @return A Future representing the pending completion of the task
   */
  fun <T> submit(task: () -> T): Future<T> = pool.submit<T> {
    val taskId = taskCounter.incrementAndGet()
    val currentThread = Thread.currentThread()
    log.debug("Task #{} submitted on thread {}", taskId, currentThread.name)
    try {
      synchronized(waitingThreads) {
        waitingThreads.add(currentThread)
        log.debug(
          "Task #{}: Thread {} added to waiting list. Current waiting count: {}",
          taskId, currentThread.name, waitingThreads.size
        )
      }
      log.debug(
        "Task #{}: Attempting to acquire semaphore permit. Available permits: {}",
        taskId, semaphore.availablePermits()
      )
      semaphore.acquire()
      log.debug(
        "Task #{}: Semaphore permit acquired. Remaining permits: {}",
        taskId, semaphore.availablePermits()
      )
      synchronized(waitingThreads) {
        waitingThreads.remove(currentThread)
        log.debug(
          "Task #{}: Thread {} removed from waiting list. Current waiting count: {}",
          taskId, currentThread.name, waitingThreads.size
        )
      }
      synchronized(activeThreads) {
        activeThreads.add(currentThread)
        log.debug(
          "Task #{}: Thread {} added to active list. Current active count: {}",
          taskId, currentThread.name, activeThreads.size
        )
      }
      log.debug("Task #{}: Executing task on thread {}", taskId, currentThread.name)
      val result = task()
      log.debug("Task #{}: Task execution completed on thread {}", taskId, currentThread.name)
      result
    } finally {
      log.debug("Task #{}: Releasing semaphore permit", taskId)
      semaphore.release()
      log.debug(
        "Task #{}: Semaphore permit released. Available permits: {}",
        taskId, semaphore.availablePermits()
      )
      synchronized(activeThreads) {
        activeThreads.remove(currentThread)
        log.debug(
          "Task #{}: Thread {} removed from active list. Current active count: {}",
          taskId, currentThread.name, activeThreads.size
        )
      }
      synchronized(waitingThreads) {
        waitingThreads.remove(currentThread)
        log.debug(
          "Task #{}: Thread {} removed from waiting list (cleanup). Current waiting count: {}",
          taskId, currentThread.name, waitingThreads.size
        )
      }
    }
  }
  
  /**
   * Gets the current number of active threads.
   *
   * @return The number of active threads
   */
  fun getActiveThreadCount(): Int = synchronized(activeThreads) { activeThreads.size }
  
  /**
   * Gets the current number of waiting threads.
   *
   * @return The number of waiting threads
   */
  fun getWaitingThreadCount(): Int = synchronized(waitingThreads) { waitingThreads.size }
  
  /**
   * Gets the current number of available permits.
   *
   * @return The number of available permits
   */
  fun getAvailablePermits(): Int = semaphore.availablePermits()
  
  /**
   * Shuts down the processor, waiting for all tasks to complete.
   */
  fun shutdown() {
    log.info(
      "Shutting down FixedConcurrencyProcessor. Active threads: {}, Waiting threads: {}",
      getActiveThreadCount(), getWaitingThreadCount()
    )
    pool.shutdown()
    log.info("FixedConcurrencyProcessor shutdown completed")
  }
}