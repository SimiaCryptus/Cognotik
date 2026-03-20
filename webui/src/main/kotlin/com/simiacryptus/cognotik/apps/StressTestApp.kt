package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import kotlin.random.Random

class StressTestApp(
  applicationName: String = "UI Stress Test",
  path: String = "/stressTest",
) : ApplicationServer(
  applicationName = applicationName,
  path = path,
  showMenubar = true
) {
  var wasRun = false
  override fun userMessage(
    session: Session,
    user: User,
    userMessage: String,
    ui: SocketManager
  ) {
    if (wasRun) {
      return
    }
    wasRun = true
    val task = ui.newTask()
    task.add("# UI Stress Test".renderMarkdown(true))
    createNestedTabs(task, ui, 3)
  }

  private fun createNestedTabs(task: SessionTask, ui: SocketManager, depth: Int) {
    if (depth <= 0) {
      createComplexDiagram(task)
      createAndUpdatePlaceholders(task, ui)
      return
    }

    val tabDisplay = TabbedDisplay(task)

    (1..2).forEach { i ->
      val subTask = ui.newTask(false)
      tabDisplay["Tab $i"] = subTask.placeholder
      createNestedTabs(subTask, ui, depth - 1)
    }
    tabDisplay.update()
  }

  private fun createComplexDiagram(task: SessionTask) {
    val mermaidDiagram = """
            ```mermaid
            graph TD
                A[Start] --> B{Is it?}
                B -->|Yes| C[OK]
                C --> D[Rethink]
                D --> B
                B ---->|No| E[End]
            ```
        """.trimIndent()
    task.add("## Complex Diagram\n$mermaidDiagram".renderMarkdown(true))
  }

  private fun createAndUpdatePlaceholders(task: SessionTask, ui: SocketManager) {
    val placeholders = (1..5).map { ui.newTask(false) }

    placeholders.forEach { placeholder ->
      task.add(placeholder.placeholder)
    }

    repeat(10) { iteration ->
      placeholders.forEach { placeholder ->
        val content = "Placeholder content: Iteration $iteration, Random: ${Random.nextInt(100)}"
        placeholder.add(content.renderMarkdown(true))

      }
    }
    placeholders.forEach { it.complete() }
    task.complete()
  }

}