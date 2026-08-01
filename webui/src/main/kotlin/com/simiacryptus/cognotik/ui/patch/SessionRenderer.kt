package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.text.ui.ChangeType
import com.simiacryptus.cognotik.text.ui.DiffUIRenderer
import com.simiacryptus.cognotik.text.ui.FileChangeSummary
import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

class SessionRenderer(
  task: SessionTask,
) : DiffUIRenderer {

  private val socketManager = task.ui

  override fun renderSaveButton(
    filepath: Path,
    code: String,
    lang: String,
    onSave: () -> Unit
  ): String {
    log.debug("Rendering save button for file: {}, lang: {}, code length: {}", filepath, lang, code.length)
    val task = socketManager.newTask(root = false)
    lateinit var hrefLink: StringBuilder
    hrefLink = task.complete(task.hrefLink("Save File", classname = "href-link cmd-button") {
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
    onRevert: () -> Unit,
    onForceApply: (() -> Unit)?
  ): String {
    log.debug(
      "Rendering apply diff button for file: {}, diff length: {}, force available: {}",
      filepath, diff.length, onForceApply != null
    )
    lateinit var hrefLink: StringBuilder
    val isApplied = AtomicBoolean(false)

    val task = socketManager.newTask(root = false)
    lateinit var revertHtml: String
    lateinit var applyHtml: String
    var forceHtml = ""

    /** Controls offered whenever the diff is *not* currently applied. */
    fun pendingControls() = applyHtml + forceHtml
    applyHtml = task.hrefLink("Apply Diff", classname = "href-link cmd-button") {
      if (!isApplied.compareAndSet(false, true)) return@hrefLink
      try {
        log.info("Apply diff button clicked for file: {}", filepath)
        onApply()
        hrefLink.set("""<div class="cmd-button">Diff Applied</div>""" + revertHtml)
        task.complete()
        log.info("Diff applied successfully for file: {}", filepath)
      } catch (e: Throwable) {
        isApplied.set(false)
        log.error("Error applying diff to {}: {}", filepath, e.message, e)
        /* Keep the apply controls so the user can retry (optionally ignoring validation). */
        hrefLink.set("""<div class="cmd-button">Error: ${e.message}</div>""" + pendingControls())
        task.error(e)
      }
    }
    if (onForceApply != null) {
      forceHtml = task.hrefLink("Apply (Ignore Validation)", classname = "href-link cmd-button") {
        if (!isApplied.compareAndSet(false, true)) return@hrefLink
        try {
          log.info("Force-apply diff button clicked for file: {}", filepath)
          onForceApply()
          hrefLink.set("""<div class="cmd-button">Diff Applied (validation ignored)</div>""" + revertHtml)
          task.complete()
          log.info("Diff force-applied successfully for file: {}", filepath)
        } catch (e: Throwable) {
          isApplied.set(false)
          log.error("Error force-applying diff to {}: {}", filepath, e.message, e)
          hrefLink.set("""<div class="cmd-button">Error: ${e.message}</div>""" + pendingControls())
          task.error(e)
        }
      }
    }
    revertHtml = task.hrefLink("Revert", classname = "href-link cmd-button") {
      try {
        log.info("Revert button clicked for file: {}", filepath)
        onRevert()
        isApplied.set(false)
        /* Re-render every apply control so the patch can be applied again after a revert. */
        hrefLink.set("""<div class="cmd-button">Reverted</div>""" + pendingControls())
        task.complete()
        log.info("Diff reverted successfully for file: {}", filepath)
      } catch (e: Throwable) {
        log.error("Error reverting diff for {}: {}", filepath, e.message, e)
        hrefLink.append("""<div class="cmd-button">Error: ${e.message}</div>""")
        task.error(e)
      }
    }

    hrefLink = task.complete(pendingControls())!!
    return task.placeholder
  }

  override fun renderAutoApplied(filepath: Path, revertHtml: String): String {
    log.debug("Rendering auto-applied notice for file: {}", filepath)
    return """<div class="cmd-button">Automatically Applied to $filepath</div>$revertHtml"""
  }

  override fun renderWarning(message: String): String {
    log.debug("Rendering warning: {}", message)
    return """<div class="warning">Warning: $message</div>"""
  }

  override fun renderChangeSummary(changes: List<FileChangeSummary>, onApplyAll: (() -> Unit)?): String {
    if (changes.isEmpty()) return ""
    log.debug("Rendering change summary for {} file(s)", changes.size)
    val rows = changes.joinToString("\n") { c ->
      val kind = when (c.changeType) {
        ChangeType.NEW_FILE -> "new file"
        ChangeType.MODIFIED -> "modified"
      }
      val status = when {
        c.applied -> "applied"
        !c.isValid -> "pending (validation failed)"
        else -> "pending"
      }
      "| `${c.relativePath}` | $kind | +${c.linesAdded} / -${c.linesRemoved} | $status |"
    }
    val table = "| File | Change | Lines | Status |\n| --- | --- | --- | --- |\n$rows"
    val pendingCount = changes.count { !it.applied }
    if (onApplyAll == null || pendingCount == 0) return "\n\n### Change Summary\n\n$table\n\n"
    val task = socketManager.newTask(root = false)
    lateinit var hrefLink: StringBuilder
    hrefLink = task.complete(task.hrefLink("Apply All ($pendingCount)", classname = "href-link cmd-button") {
      try {
        log.info("Apply-all clicked for {} pending change(s)", pendingCount)
        onApplyAll()
        hrefLink.set("""<div class="cmd-button">Applied $pendingCount change(s)</div>""")
        task.complete()
      } catch (e: Throwable) {
        log.error("Apply-all failed: {}", e.message, e)
        hrefLink.set("""<div class="cmd-button">Apply All Error: ${e.message}</div>""")
        task.error(e)
      }
    })!!
    return "\n\n### Change Summary\n\n$table\n\n${task.placeholder}\n\n"
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
      file.parentFile?.mkdirs()
      file.writeText(toJson(data, ""))
      log.debug("Patch data recorded to: {}", relativePath)
      val traceLines = (data["trace"] as? Collection<*>)?.map { it.toString() }.orEmpty()
      val traceHtml = if (traceLines.isEmpty()) "" else
        """<details class="verbose"><summary>Trace (${traceLines.size})</summary><pre>${
          traceLines.joinToString("\n") { escapeHtml(it) }
        }</pre></details>"""
      """<a href='fileIndex/${socketManager.sessionId}/$relativePath' target='_blank' class='verbose'>Patch Data</a>$traceHtml"""
    } catch (e: Throwable) {
      log.warn("Failed to record patch data: {}", e.message, e)
      "" // Silently fail for recording - it's non-critical
    }
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(SessionRenderer::class.java)

    /** Minimal recursive JSON writer so structured patch data (e.g. trace line lists) round-trips. */
    internal fun toJson(value: Any?, indent: String): String = when (value) {
      null -> "null"
      is Boolean, is Number -> value.toString()
      is CharSequence -> quote(value.toString())
      is Throwable -> quote("${value::class.java.name}: ${value.message}")
      is Map<*, *> -> if (value.isEmpty()) "{}" else value.entries.joinToString(",\n", "{\n", "\n$indent}") { (k, v) ->
        "$indent  ${quote(k.toString())}: ${toJson(v, "$indent  ")}"
      }

      is Array<*> -> toJson(value.toList(), indent)
      is Iterable<*> -> if (!value.iterator().hasNext()) "[]" else
        value.joinToString(",\n", "[\n", "\n$indent]") { "$indent  ${toJson(it, "$indent  ")}" }

      else -> quote(value.toString())
    }

    internal fun quote(s: String): String = buildString(s.length + 2) {
      append('"')
      for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c == '\b' -> append("\\b")
        c == '\u000C' -> append("\\f")
        c < ' ' -> append("\\u%04x".format(c.code))
        else -> append(c)
      }
      append('"')
    }

    internal fun escapeHtml(s: String): String = s
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
  }
}