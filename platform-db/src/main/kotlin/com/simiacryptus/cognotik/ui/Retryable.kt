package com.simiacryptus.cognotik.ui

import com.simiacryptus.cognotik.util.ImmediateExecutorService
import com.simiacryptus.cognotik.util.oneAtATime
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager

open class Retryable(
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
  }${
    task.hrefLink(
      "♻",
      """href-link""",
      null,
      oneAtATime { it: Unit -> retry() })
  }
</div>
"""

  companion object {
    fun ((SessionTask) -> Unit?).async(
      socketManager: SocketManager,
      pool: ImmediateExecutorService = socketManager.pool
    ): (StringBuilder) -> String = {
      val task = socketManager.newTask(false)
      pool.submit {
        this(task)
      }
      task.placeholder
    }
  }
}
