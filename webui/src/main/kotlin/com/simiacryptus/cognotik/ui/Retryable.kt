package com.simiacryptus.cognotik.ui

import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.util.oneAtATime
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.util.concurrent.ExecutorService

open class Retryable(
  task: ISessionTask,
  val process: (StringBuilder) -> String
) : TabbedDisplay(task) {

  init {
    init()
  }

  open fun init() {
    val tabLabel = label(size)
    set(tabLabel, ISessionTask.spinner)
    set(tabLabel, process(container))
  }

  fun retry() {
    val idx = tabs.size
    val label = label(idx)
    val content = StringBuilder("Retrying..." + ISessionTask.spinner)
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
      handler = oneAtATime { it: Unit -> retry() })
  }
</div>
"""

  companion object {
    fun ((ISessionTask) -> Unit?).async(
      socketManager: SocketManager,
      pool: ExecutorService = socketManager.pool
    ): (StringBuilder) -> String = {
      val task = socketManager.newTask(false)
      pool.submit {
        this(task)
      }
      task.placeholder
    }
    fun ((ISessionTask) -> Unit?).async(
      socketManager: ISessionTask,
      pool: ExecutorService = socketManager.pool
    ): (StringBuilder) -> String = {
      val task = socketManager.newTask(false)
      pool.submit {
        this(task)
      }
      task.placeholder
    }
  }
}
