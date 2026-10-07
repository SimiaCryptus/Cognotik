package com.simiacryptus.cognotik.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.function.Supplier

/**
 * Modal variant of [BgTask]. Same semantics for [get]: task exceptions are rethrown,
 * cancellation surfaces as [InterruptedException].
 */
class ModalTask<T>(
    project: Project,
    title: String,
    canBeCancelled: Boolean,
    val task: (ProgressIndicator) -> T
) : Task.WithResult<T, Exception>(project, title, canBeCancelled), Supplier<T> {

    private val execution = TaskExecution(title, task)

    override fun compute(indicator: ProgressIndicator): T? = execution.execute(indicator)

    override fun onCancel() {
        super.onCancel()
        execution.cancel()
    }

    override fun get(): T = execution.await()
}