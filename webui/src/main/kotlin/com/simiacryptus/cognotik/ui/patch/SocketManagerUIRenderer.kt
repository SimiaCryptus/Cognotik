package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.nio.file.Path

class SocketManagerUIRenderer(
  private val socketManager: SocketManager,
  private val sessionId: Session
) : DiffUIRenderer {
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(SocketManagerUIRenderer::class.java)
  }

  override fun renderSaveButton(
    filepath: Path,
    code: String,
    lang: String,
    onSave: () -> Unit
  ): String {
    log.debug("Rendering save button for file: {}, lang: {}, code length: {}", filepath, lang, code.length)
    val task = socketManager.newTask(false)
    lateinit var hrefLink: StringBuilder
    @Suppress("AssignedValueIsNeverRead")
    hrefLink = task.complete(socketManager.hrefLink("Save File", classname = "href-link cmd-button") {
      try {
        log.info("Save button clicked for file: {}", filepath)
        onSave()
        hrefLink.set("""<div class="cmd-button">Saved $filepath</div>""")
        task.complete()
        log.info("File saved successfully: {}", filepath)
      } catch (e: Throwable) {
        log.error("Error saving file {}: {}", filepath, e.message, e)
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
    log.debug("Rendering apply diff button for file: {}, diff length: {}", filepath, diff.length)
    val task = socketManager.newTask(false)
    lateinit var hrefLink: StringBuilder
    var isApplied = false

    lateinit var revertHtml: String
    val applyHtml = socketManager.hrefLink("Apply Diff", classname = "href-link cmd-button") {
      if (isApplied) return@hrefLink
      try {
        isApplied = true
        log.info("Apply diff button clicked for file: {}", filepath)
        onApply()
        hrefLink.set("""<div class="cmd-button">Diff Applied</div>""" + revertHtml)
        task.complete()
        log.info("Diff applied successfully for file: {}", filepath)
      } catch (e: Throwable) {
        isApplied = false
        log.error("Error applying diff to {}: {}", filepath, e.message, e)
        hrefLink.set("""<div class="cmd-button">Error: ${e.message}</div>""")
        task.error(e)
      }
    }

    @Suppress("AssignedValueIsNeverRead")
    revertHtml = socketManager.hrefLink("Revert", classname = "href-link cmd-button") {
      try {
        isApplied = false
        log.info("Revert button clicked for file: {}", filepath)
        onRevert()
        hrefLink.set("""<div class="cmd-button">Reverted</div>""" + applyHtml)
        task.complete()
        log.info("Diff reverted successfully for file: {}", filepath)
      } catch (e: Throwable) {
        log.error("Error reverting diff for {}: {}", filepath, e.message, e)
        hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
        task.error(e)
      }
    }

    hrefLink = task.complete(applyHtml)!!
    return task.placeholder
  }

  override fun renderAutoApplied(filepath: Path, revertHtml: String): String {
    log.debug("Rendering auto-applied notice for file: {}", filepath)
    return """<div class="cmd-button">Automatically Applied to $filepath</div>""" + revertHtml
  }

  override fun renderWarning(message: String): String {
    log.debug("Rendering warning: {}", message)
    return """<div class="warning">Warning: $message</div>"""
  }

  override fun recordPatch(data: Map<String, Any?>): String {
    log.debug("Recording patch data with {} entries", data.size)
    return try {
      val relativePath = "patch/${java.util.UUID.randomUUID()}.json"
      val file = socketManager.resolveSystemFile(relativePath)
      if (file == null) {
        log.warn("Could not resolve system file for patch recording: {}", relativePath)
        return ""
      }
      file.writeText(
        data.entries.joinToString(",\n", "{\n", "\n}") { (k, v) ->
          "  \"$k\": ${if (v is String) "\"${v.replace("\"", "\\\"")}\"" else "$v"}"
        }
      )
      log.debug("Patch data recorded to: {}", relativePath)
      val sid = sessionId ?: ""
      """<a href='fileIndex/$sid/$relativePath' target='_blank' class='verbose'>Patch Data</a>"""
    } catch (e: Throwable) {
      log.warn("Failed to record patch data: {}", e.message, e)
      "" // Silently fail for recording - it's non-critical
    }
  }
}