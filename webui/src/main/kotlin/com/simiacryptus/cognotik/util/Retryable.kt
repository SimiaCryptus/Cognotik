package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask

open class Retryable(
    val ui: ApplicationInterface,
    task: SessionTask,
    val process: (StringBuilder) -> String
) : TabbedDisplay(task) {

    init {
        init()
    }

    open fun init() {
        val tabLabel = label(size)
        set(tabLabel, SessionTask.spinner)
        set(tabLabel, process(container))
    }

    fun retry() {
        val idx = tabs.size
        val label = label(idx)
        val content = StringBuilder("Retrying..." + SessionTask.spinner)
        tabs.add(label to content)
        update()
        val newResult = process(content)
        content.clear()
        set(label, newResult)
    }

    override fun renderTabButtons(): String = """
<div class="tabs">${
        tabs.withIndex().joinToString("\n") { (index, pair) ->
            renderButton(index, pair.first)
        }
    }${ui.hrefLink("♻") { retry() }}
</div>
"""

    companion object {
        private const val serialVersionUID: Long = 1L
        private val log = org.slf4j.LoggerFactory.getLogger(Retryable::class.java)
        fun retryable(
            ui: ApplicationInterface,
            pool: ImmediateExecutorService = ui.socketManager?.pool
                ?: throw IllegalStateException("No socket manager available"),
            task: SessionTask = ui.newTask(true),
            fn: (SessionTask) -> Unit
        ) {
            Retryable(ui, task) {
                val task = ui.newTask(false)
                pool.submit { fn(task) }
                task.placeholder
            }
        }
    }
}
