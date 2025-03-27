package com.simiacryptus.skyenet.core.util

import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.ExecutorService
import java.util.concurrent.FutureTask

/**
 * A utility class to manage concurrent task execution with a fixed concurrency limit.
 *
 * @param pool The executor service to use for executing tasks.
 * @param concurrencyLimit The maximum number of concurrent tasks allowed.
 * @param queue An optional blocking queue for managing tasks (default is a linked blocking queue).
 */
class FixedConcurrencyProcessor(
  val pool: ExecutorService,
  val concurrencyLimit: Int,
  val queue: java.util.concurrent.BlockingQueue<Runnable> = java.util.concurrent.LinkedBlockingQueue<Runnable>()
) {
  private val semaphore = Semaphore(concurrencyLimit)
  
  /**
   * Submits a task for execution with concurrency control.
   * The task will be executed when a permit is available from the semaphore.
   *
   * @param task The task to execute
   * @return A Future representing the pending completion of the task
   */
  fun <T> submit(task: () -> T): Future<T> {
    val futureTask = FutureTask<T> {
      try {
        semaphore.acquire()
        task()
      } finally {
        semaphore.release()
      }
    }
    pool.execute(futureTask)
    return futureTask
  }
  /**
   * Shuts down the processor, waiting for all tasks to complete.
   */
  fun shutdown() {
    pool.shutdown()
  }
  /**
   * Returns the number of available permits in the semaphore.
   * 
   * @return The number of available permits
   */
  fun availablePermits(): Int {
    return semaphore.availablePermits()
  }
}