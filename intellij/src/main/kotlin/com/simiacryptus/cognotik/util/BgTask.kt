package com.simiacryptus.cognotik.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

class BgTask<T>(
    project: Project, title: String, canBeCancelled: Boolean, val task: (ProgressIndicator) -> T
) : Task.Backgroundable(project, title, canBeCancelled, DEAF), Supplier<T> {
    private val taskLog = LoggerFactory.getLogger(BgTask::class.java)

    private val result = AtomicReference<T>()
    private val isError = AtomicBoolean(false)
    private val error = AtomicReference<Throwable>()
    private val startSemaphore = Semaphore(0)
    private val completeSemaphore = Semaphore(0)
    private val completed = AtomicBoolean(false)
    private val threadList = Collections.synchronizedList(ArrayList<Thread>())
    private val cancelled = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val lock = Object()

    override fun run(indicator: ProgressIndicator) {
        taskLog.debug("Starting run() for BgTask: $title")
        synchronized(lock) {

            if (!started.compareAndSet(false, true)) return
            if (completed.get() || cancelled.get()) {

                startSemaphore.release()
                completeSemaphore.release()
                return
            }
        }
        startSemaphore.release()
        val currentThread = Thread.currentThread()

        threadList.add(currentThread)
        val scheduledFuture = UITools.scheduledPool.scheduleAtFixedRate({
            if (indicator.isCanceled) {

                cancelled.set(true)
                threadList.forEach { it.interrupt() }
            }
        }, 0, 1, TimeUnit.SECONDS)
        try {
            synchronized(lock) {
                if (completed.get() || cancelled.get()) {

                    completeSemaphore.release()
                    return
                }
            }

            val result = task(indicator)
            this.result.set(result)

        } catch (e: Throwable) {
            taskLog.error("Error executing task", e)
            log.info("Error running task", e)
            error.set(e)
            isError.set(true)
        } finally {
            synchronized(lock) {

                completed.set(true)
                completeSemaphore.release()
            }

            threadList.remove(currentThread)
            scheduledFuture.cancel(true)
        }
    }

    override fun get(): T {
        taskLog.debug("Attempting to get task result")
        try {

            val startAcquired = startSemaphore.tryAcquire(5, TimeUnit.SECONDS)

            synchronized(lock) {
                if (!started.get() || !startAcquired) {
                    taskLog.error("Task timed out or never started")
                    cancelled.set(true)
                    throw TimeoutException("Task failed to start after 5 seconds")
                }
            }

            val completeAcquired = completeSemaphore.tryAcquire(3000, TimeUnit.SECONDS)

            if (!completeAcquired) {
                taskLog.error("Task execution timed out")
                cancelled.set(true)
                throw TimeoutException("Task execution timed out after 30 seconds")
            }
        } finally {
            startSemaphore.release()
            completeSemaphore.release()
        }
        synchronized(lock) {

            if (!completed.get()) {
                throw IllegalStateException(
                    "Task not completed" +
                            (if (cancelled.get()) " (cancelled)" else "")
                )
            }
            if (isError.get()) {
                val e = error.get() ?: RuntimeException("Unknown error occurred")
                taskLog.error("Task failed with error", e)
                throw e
            }
            if (cancelled.get()) {

                throw InterruptedException("Task was cancelled")
            }
            taskLog.debug("Returning successful task result")
            return result.get() ?: throw IllegalStateException("No result available")
        }
    }

    override fun onCancel() {
        taskLog.debug("Task cancelled")
        super.onCancel()
        synchronized(lock) {
            cancelled.set(true)
            threadList.forEach { it.interrupt() }
            startSemaphore.release()
            completeSemaphore.release()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BgTask::class.java)
    }
}