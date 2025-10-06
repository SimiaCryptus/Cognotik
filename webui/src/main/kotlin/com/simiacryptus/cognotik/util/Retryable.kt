package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager

open class Retryable(
    task: SessionTask,
    val socketManager: SocketManager = task.ui,
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
    }${
        socketManager.hrefLink(
            "♻",
            """href-link""",
            null,
            oneAtATime { it: Unit -> retry() })
    }
</div>
"""

    companion object {
        fun retryable(
            socketManager: SocketManager,
            pool: ImmediateExecutorService = socketManager.pool,
            task: SessionTask = socketManager.newTask(true),
            fn: (SessionTask) -> Unit
        ) {
            Retryable(task) {
                val task = socketManager.newTask(false)
                pool.submit { fn(task) }
                task.placeholder
            }
        }
    }
}
