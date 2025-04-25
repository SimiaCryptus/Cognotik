package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.webui.session.SessionTask
import java.util.*

open class TabbedDisplay(
  val task: SessionTask,
  val tabs: MutableList<Pair<String, StringBuilder>> = mutableListOf(),
  val additionalClasses: String = "",
  val closable: Boolean = true
) {
  var selectedTab: Int = 0

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(TabbedDisplay::class.java)
  }

  val size: Int get() = tabs.size
  val tabId = UUID.randomUUID()
  private fun render() = if (tabs.isEmpty()) "<div/>" else {
    """
  <div class="${(additionalClasses.split(" ").toSet() + setOf("tabs-container")).filter { it.isNotEmpty() }.joinToString(" ")}" id="$tabId">
  ${renderTabButtons()}
  ${
      tabs.toTypedArray().withIndex().joinToString("\n")
      { (idx, t) -> renderContentTab(t, idx) }
    }
  </div>
  """
  }

  val container: StringBuilder by lazy {
    log.debug("Initializing container with rendered content")
    task.add(render())!!
  }

  protected open fun renderTabButtons() = """<div class="tabs">${
    tabs.toTypedArray().withIndex().joinToString("\n") { (idx, pair) ->
      renderButton(idx, pair.first)
    }
  }</div>"""

  protected open fun renderButton(idx: Int, label: String): String {
    val buttonHtml = if (idx == selectedTab) {
      """<button class="tab-button active" data-for-tab="$idx">$label</button>"""
    } else {
      """<button class="tab-button" data-for-tab="$idx">$label</button>"""
    }
    val closeButton = if (idx <= 1 || !closable) "" else task.hrefLink("✖️") {
      tabs.removeAt(idx)
      update()
    }
    return buttonHtml + closeButton
  }

  protected open fun renderContentTab(t: Pair<String, StringBuilder>, idx: Int) = """<div class="${
    (additionalClasses.split(" ") + setOf("tab-content") + when {
      idx == selectedTab -> setOf("active")
      else -> emptySet()
    }).filter { it.isNotEmpty() }.joinToString(" ")
  }" data-tab="$idx">${t.second}</div>"""


  operator fun get(i: String) = tabs.toMap()[i]
  operator fun set(name: String, content: String) =
    when (val index = find(name)) {
      null -> {
        log.debug("Adding new tab: $name")
        val stringBuilder = StringBuilder(content)
        tabs.add(name to stringBuilder)
        update()
        stringBuilder
      }

      else -> {
        log.debug("Updating existing tab: $name")
        val stringBuilder = tabs[index].second
        stringBuilder.clear()
        stringBuilder.append(content)
        update()
        stringBuilder
      }
    }

  fun find(name: String) = tabs.withIndex().firstOrNull { it.value.first == name }?.index

  open fun label(i: Int): String {
    return "${tabs.size + 1}"
  }
  open fun delete(name: String): Boolean {
    log.debug("Deleting tab: $name")
    val index = find(name)
    return if (index != null) {
      tabs.removeAt(index)
      update()
      true
    } else {
      false
    }
  }


  open fun clear() {
    log.debug("Clearing all tabs")
    tabs.clear()
    update()
  }

  open fun update() {
    log.debug("Updating container content")
    synchronized(container) {
      if (tabs.isNotEmpty() && (selectedTab < 0 || selectedTab >= tabs.size)) {
        selectedTab = 0
      }
      container.clear()
      container.append(render())
    }
    task.complete()
  }

}