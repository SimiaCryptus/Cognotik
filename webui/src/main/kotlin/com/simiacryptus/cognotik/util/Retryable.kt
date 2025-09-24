package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManagerBase

open class Retryable(
    task: SessionTask,
    val socketManager: SocketManagerBase = task.manager,
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
    }${socketManager.hrefLink(
        "♻",
        """href-link""",
        null,
        ApplicationInterface.Companion.oneAtATime { it: Unit -> retry() })}
</div>
"""

    companion object {
        fun retryable(
            socketManager: SocketManagerBase,
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
