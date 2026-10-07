package com.simiacryptus.cognotik.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.function.Supplier

/**
 * Background task whose result can be retrieved (blocking) via [get].
 * Exceptions thrown by [task] are rethrown from [get]; cancellation surfaces as [InterruptedException].
 */
class BgTask<T>(
    project: Project,
    title: String,
    canBeCancelled: Boolean,
    val task: (ProgressIndicator) -> T
) : Task.Backgroundable(project, title, canBeCancelled, DEAF), Supplier<T> {

    private val execution = TaskExecution(title, task)

    override fun run(indicator: ProgressIndicator) {
        execution.execute(indicator)
    }

    override fun onCancel() {
        super.onCancel()
        execution.cancel()
    }

    override fun get(): T = execution.await()
}