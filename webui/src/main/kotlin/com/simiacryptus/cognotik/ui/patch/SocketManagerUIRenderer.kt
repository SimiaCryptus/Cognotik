package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.nio.file.Path

class SocketManagerUIRenderer(
  private val socketManager: SocketManager,
  private val sessionId: Session
) : DiffUIRenderer {
  override fun renderSaveButton(
    filepath: Path,
    code: String,
    lang: String,
    onSave: () -> Unit
  ): String {
    val task = socketManager.newTask(false)
    lateinit var hrefLink: StringBuilder
    @Suppress("AssignedValueIsNeverRead")
    hrefLink = task.complete(socketManager.hrefLink("Save File", classname = "href-link cmd-button") {
      try {
        onSave()
        hrefLink.set("""<div class="cmd-button">Saved $filepath</div>""")
        task.complete()
      } catch (e: Throwable) {
        hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
        task.error(e)
      }
    })!!
    return task.placeholder
  }

  override fun renderApplyDiffButton(
    filepath: Path,
    diff: String,
    onApply: () -> Unit,
    onRevert: () -> Unit
  ): String {
    val task = socketManager.newTask(false)
    lateinit var hrefLink: StringBuilder
    var isApplied = false

    lateinit var revertHtml: String
    val applyHtml = socketManager.hrefLink("Apply Diff", classname = "href-link cmd-button") {
      if (isApplied) return@hrefLink
      try {
        isApplied = true
        onApply()
        hrefLink.set("""<div class="cmd-button">Diff Applied</div>""" + revertHtml)
        task.complete()
      } catch (e: Throwable) {
        isApplied = false
        hrefLink.set("""<div class="cmd-button">Error: ${e.message}</div>""")
        task.error(e)
      }
    }

    @Suppress("AssignedValueIsNeverRead")
    revertHtml = socketManager.hrefLink("Revert", classname = "href-link cmd-button") {
      try {
        isApplied = false
        onRevert()
        hrefLink.set("""<div class="cmd-button">Reverted</div>""" + applyHtml)
        task.complete()
      } catch (e: Throwable) {
        hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
        task.error(e)
      }
    }

    hrefLink = task.complete(applyHtml)!!
    return task.placeholder
  }

  override fun renderAutoApplied(filepath: Path, revertHtml: String): String {
    return """<div class="cmd-button">Automatically Applied to $filepath</div>""" + revertHtml
  }

  override fun renderWarning(message: String): String {
    return """<div class="warning">Warning: $message</div>"""
  }

  override fun recordPatch(data: Map<String, Any?>): String {
    return try {
      val relativePath = "patch/${java.util.UUID.randomUUID()}.json"
      socketManager.resolveSystemFile(relativePath)?.writeText(
        data.entries.joinToString(",\n", "{\n", "\n}") { (k, v) ->
          "  \"$k\": ${if (v is String) "\"${v.replace("\"", "\\\"")}\"" else "$v"}"
        }
      )
      val sid = sessionId ?: ""
      """<a href='fileIndex/$sid/$relativePath' target='_blank' class='verbose'>Patch Data</a>"""
    } catch (e: Throwable) {
      "" // Silently fail for recording - it's non-critical
    }
  }
}