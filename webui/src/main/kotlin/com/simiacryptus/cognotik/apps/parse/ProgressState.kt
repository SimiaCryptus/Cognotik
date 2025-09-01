package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.cognotik.webui.session.SessionTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ProgressState private constructor(
    private val task: SessionTask?,
    private val totalItems: AtomicInteger = AtomicInteger(0),
    private val completedItems: AtomicInteger = AtomicInteger(0),
    private val lastUpdateTime: AtomicLong = AtomicLong(System.currentTimeMillis())
) {
    fun add(completed: Double, total: Double) {
        completedItems.addAndGet(completed.toInt())
        totalItems.addAndGet(total.toInt())
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime.get() > UPDATE_INTERVAL_MS) {
            updateProgress()
            lastUpdateTime.set(now)
        }
    }

    private fun updateProgress() {
        val total = totalItems.get()
        val completed = completedItems.get()
        if (total > 0) {
            val percentage = (completed * 100.0 / total).toInt()
            val progressBar = buildString {
                append("[")
                val filled = percentage / 2
                repeat(filled) { append("█") }
                repeat(50 - filled) { append("░") }
                append("] $percentage% ($completed/$total)")
            }
            task?.add(progressBar)
        }
    }

    fun complete() {
        completedItems.set(totalItems.get())
        updateProgress()
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 500L
        fun progressBar(task: SessionTask): ProgressState {
            return ProgressState(task)
        }

        fun noOp(): ProgressState {
            return ProgressState(null)
        }
    }
}