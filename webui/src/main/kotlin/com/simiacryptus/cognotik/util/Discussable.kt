package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.models.ApiModel.Role
import java.util.concurrent.Callable
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class Discussable<T : Any>(
  private val task: SessionTask,
  private val userMessage: () -> String,
  private val initialResponse: (String) -> T,
  private val outputFn: (T) -> String,
  private val ui: ApplicationInterface,
  private val reviseResponse: (List<Pair<String, Role>>) -> T,
  private val atomicRef: AtomicReference<T> = AtomicReference(),
  private val semaphore: Semaphore = Semaphore(0),
  private val heading: String
) : Callable<T> {

  val tabs = object : TabbedDisplay(task) {
    override fun renderTabButtons() = """
<div class="tabs">
${
      tabs.withIndex().joinToString("\n")
      { (index: Int, t: Pair<String, StringBuilder>) ->
        renderButton(index, t.first)
      }
    }
${
      ui.hrefLink("♻") {
        val newTask = ui.newTask(false)
        val header = newTask.header("Retrying...", 4)
        val idx: Int = size
        this.set(label(idx), newTask.placeholder)
        main(idx, newTask)
        //this.selectedTab = idx
        header?.clear()
        newTask.complete()
      }
    } 
</div>
"""
  }
  private val acceptGuard = AtomicBoolean(false)

  private fun main(tabIndex: Int, task: SessionTask) {
    log.info("Starting main function for tabIndex: $tabIndex")
    try {
      val history = mutableListOf<Pair<String, Role>>()
      val userMessage = userMessage()
      //log.info("User message: $userMessage")
      history.add(userMessage to Role.user)
      val design = initialResponse(userMessage)
      //log.info("Initial response generated: $design")
      val rendered = outputFn(design)
      //log.info("Rendered output: $rendered")
      history.add(rendered to Role.assistant)
      val tabContent = task.add(rendered)!!
      val feedbackForm = feedbackForm(tabIndex, tabContent, design, history, task)
      tabContent.append("\n" + feedbackForm.placeholder)
      task.complete()
    } catch (e: Throwable) {
      log.error("Error in discussable", e)
      task.error(ui, e)
      task.complete(ui.hrefLink("🔄 Retry") {
        main(tabIndex = tabIndex, task = task)
      })
    }
  }

  private fun feedbackForm(
    tabIndex: Int?,
    tabContent: StringBuilder,
    design: T,
    history: List<Pair<String, Role>>,
    task: SessionTask,
  ) = ui.newTask(false).apply {
    log.info("Creating feedback form for tabIndex: $tabIndex")
    val feedbackSB = add("<div />")!!
    feedbackSB.clear()
    feedbackSB.append(
      """
<div style="display: flex; flex-direction: column;">
${acceptLink(tabIndex, tabContent, design, feedbackSB, feedbackTask = this)}
</div>
${textInput(design, tabContent, history, task, feedbackSB, feedbackTask = this)}
"""
    )
    complete()
  }

  private fun acceptLink(
    tabIndex: Int?,
    tabContent: StringBuilder,
    design: T,
    feedbackSB: StringBuilder,
    feedbackTask: SessionTask,
  ) = ui.hrefLink("Accept", classname = "href-link cmd-button") {
    log.info("Accept link clicked for tabIndex: $tabIndex")
    feedbackSB.clear()
    feedbackTask.complete()
    accept(tabIndex, tabContent, design)
  }

  private fun textInput(
    design: T,
    tabContent: StringBuilder,
    history: List<Pair<String, Role>>,
    task: SessionTask,
    feedbackSB: StringBuilder,
    feedbackTask: SessionTask,
  ): String {
    val feedbackGuard = AtomicBoolean(false)
    return ui.textInput { userResponse ->
      log.info("User response received: $userResponse")
      if (feedbackGuard.getAndSet(true)) return@textInput
      val prev = feedbackSB.toString()
      try {
        feedbackSB.clear()
        feedbackTask.complete()
        feedback(tabContent, userResponse, history, design, task)
      } catch (e: Exception) {
        log.error("Error processing user feedback", e)
        task.error(ui, e)
        feedbackSB.set(prev)
        feedbackTask.complete()
        throw e
      } finally {
        feedbackGuard.set(false)
      }
    }
  }

  private fun feedback(
    tabContent: StringBuilder,
    userResponse: String,
    history: List<Pair<String, Role>>,
    design: T,
    task: SessionTask,
  ) {
    log.info("Processing feedback for user response: $userResponse")
    var history = history
    history = history + (userResponse to Role.user)
    val newValue = (tabContent.toString()
        + "<div class=\"user-message\">"
        + renderMarkdown(userResponse, ui = ui)
        + "</div>")
    tabContent.set(newValue)
    val stringBuilder = task.add("Processing...")
    tabs.update()
    val newDesign = reviseResponse(history)
    log.info("Revised design: $newDesign")
    val newTask = ui.newTask(root = false)
    tabContent.set(newValue + "\n" + newTask.placeholder)
    tabs.update()
    stringBuilder?.clear()
    task.complete()
    Retryable(ui, newTask) {
      outputFn(newDesign) + "\n" + feedbackForm(
        tabIndex = null,
        tabContent = it,
        design = newDesign,
        history = history,
        task = newTask
      ).placeholder
    }
  }

  private fun accept(tabIndex: Int?, tabContent: StringBuilder, design: T) {
    log.info("Accepting design for tabIndex: $tabIndex")
    if (acceptGuard.getAndSet(true)) {
      return
    }
    try {
      //if (null != tabIndex) tabs.selectedTab = tabIndex
      tabContent.apply {
        val prevTab = toString()
        set(prevTab)
        tabs.update()
      }
    } catch (e: Exception) {
      log.error("Error accepting design", e)
      task.error(ui, e)
      acceptGuard.set(false)
      throw e
    }
    atomicRef.set(design)
    semaphore.release()
  }

  override fun call(): T {
    try {
      //log.info("Calling Discussable with heading: $heading")
      if (heading.isNotBlank()) task.echo(heading)
      val idx = tabs.size
      val newTask = ui.newTask(false)
      val header = newTask.header("Processing...", 4)
      tabs[tabs.label(idx)] = newTask.placeholder
      try {
        main(idx, newTask)
        //tabs.selectedTab = idx
        semaphore.acquire()
      } catch (e: Throwable) {
        log.error("Error in main function", e)
        task.error(ui, e)
      } finally {
        header?.clear()
        newTask.complete()
      }
      log.info("Returning result from Discussable")
      return atomicRef.get()
    } catch (e: Exception) {
      log.warn(
        """
Error in Discussable
${e.message}
""", e
      )
      task.error(ui, e)
      return null as T
    }
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(Discussable::class.java)
  }
}

fun java.lang.StringBuilder.set(newValue: String) {
  clear()
  append(newValue)
}