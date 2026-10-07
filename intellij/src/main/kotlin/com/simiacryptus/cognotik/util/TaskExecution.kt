package com.simiacryptus.cognotik.util

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.SwingUtilities

/**
 * Shared execution / coordination logic for [BgTask] and [ModalTask].
 *
 * - The body runs at most once.
 * - Cancellation (via the progress indicator, [cancel], or IntelliJ's onCancel) completes the result
 *   with an [InterruptedException] and interrupts the worker thread.
 * - The worker thread's interrupt flag is always cleared before it is handed back to the IDE pool.
 */
internal class TaskExecution<T>(
    private val title: String,
    private val body: (ProgressIndicator) -> T,
) {
    private val result = CompletableFuture<T>()
    private val started = CountDownLatch(1)
    private val lock = Any()
    private var worker: Thread? = null

    fun execute(indicator: ProgressIndicator): T? {
        started.countDown()
        if (result.isDone) return null
        synchronized(lock) { worker = Thread.currentThread() }
        val watchdog = UITools.scheduledPool.scheduleWithFixedDelay(
            { if (indicator.isCanceled) cancel() },
            CANCEL_POLL_MS, CANCEL_POLL_MS, TimeUnit.MILLISECONDS
        )
        try {
            val value = body(indicator)
            result.complete(value)
            return value
        } catch (e: ProcessCanceledException) {
            cancel()
            throw e
        } catch (e: Throwable) {
            if (indicator.isCanceled || e.isCancellation()) {
                cancel()
            } else {
                log.warn("Task '$title' failed", e)
                result.completeExceptionally(e)
            }
            return null
        } finally {
            watchdog.cancel(false)
            synchronized(lock) { worker = null }
            // Never leak an interrupt flag into a pooled IDE thread
            Thread.interrupted()
        }
    }

    fun cancel() {
        started.countDown()
        if (result.completeExceptionally(InterruptedException("Task '$title' was cancelled"))) {
            synchronized(lock) { worker?.interrupt() }
        }
    }

    fun fail(error: Throwable) {
        started.countDown()
        result.completeExceptionally(error)
    }

    fun await(): T {
        if (SwingUtilities.isEventDispatchThread() && !result.isDone) {
            log.warn("Blocking the EDT while waiting for task '$title'", Throwable("stack trace"))
        }
        if (!started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            cancel()
            throw TimeoutException("Task '$title' failed to start within $START_TIMEOUT_SECONDS seconds")
        }
        try {
            return result.get(EXECUTION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            cancel()
            throw TimeoutException("Task '$title' did not complete within $EXECUTION_TIMEOUT_MINUTES minutes")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TaskExecution::class.java)
        const val START_TIMEOUT_SECONDS = 60L
        const val EXECUTION_TIMEOUT_MINUTES = 60L
        private const val CANCEL_POLL_MS = 250L
    }
}