package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import java.util.concurrent.*

/**
 * An [ExecutorService] that executes tasks immediately using a cached pool of threads.
 *
 * In contrast to typical Java executor services, this implementation does not queue tasks unless the MAXIMUM thread count is reached, only then queues tasks.
 * The core thread count is set to 0, meaning that threads are created as needed and will be terminated when idle with the stable state of the pool being 0 threads.
 *
 * @param session The session associated with this executor service.
 * @param user The user associated with this executor service, if any.
 */
class ImmediateExecutorService(
  private val session: Session,
  private val user: User?
) : ExecutorService {
  val threadFactory = RecordingThreadFactory(session, user)
  private val executor = ThreadPoolExecutor(
    0, // Core pool size of 0 means no threads are kept alive when idle
    Integer.MAX_VALUE, // Maximum pool size
    60L, TimeUnit.SECONDS, // Thread keep-alive time when idle
    SynchronousQueue<Runnable>(), // Queue for tasks
    threadFactory // Thread factory
  )
  
  override fun execute(command: Runnable) {
    executor.execute(command)
  }

  override fun shutdown() {
    executor.shutdown()
  }

  override fun shutdownNow(): MutableList<Runnable> {
    threadFactory.threads.filter { it.isAlive }.forEach { it.interrupt() }
    return executor.shutdownNow()
  }

  override fun isShutdown(): Boolean {
    return executor.isShutdown
  }

  override fun isTerminated(): Boolean {
    return executor.isTerminated
  }

  override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
    return executor.awaitTermination(timeout, unit)
  }

  override fun <T : Any?> submit(task: Callable<T>): Future<T> {
    return executor.submit(task)
  }

  override fun <T : Any?> submit(task: Runnable, result: T): Future<T> {
    return executor.submit(task, result)
  }

  override fun submit(task: Runnable): Future<*> {
    return executor.submit(task)
  }

  override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>): MutableList<Future<T>> {
    return executor.invokeAll(tasks)
  }

  override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit): MutableList<Future<T>> {
    return executor.invokeAll(tasks, timeout, unit)
  }

  override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>): T {
    return executor.invokeAny(tasks)
  }

  override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit): T {
    return executor.invokeAny(tasks, timeout, unit)
  }

}