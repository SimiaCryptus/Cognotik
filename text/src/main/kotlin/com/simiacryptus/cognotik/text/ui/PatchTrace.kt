package com.simiacryptus.cognotik.text.ui

import org.slf4j.Logger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An in-memory log collector scoped to a single unit of patch work (e.g. one call to
 * [DiffInstrumentor.renderDiffBlock]). Every message is forwarded to the underlying slf4j
 * logger *and* retained so it can be embedded in the "Patch Data" dump produced by
 * [DiffUIRenderer.recordPatch].
 *
 * The method signatures deliberately mirror slf4j's ({}-style placeholders, optional trailing
 * [Throwable]), so an existing block of `log.debug(...)` call sites can be captured simply by
 * shadowing the class-level logger with a local `val log = PatchTrace(...)`.
 *
 * Traces may be nested via [child]/[parent] so that per-file traces can include the
 * enclosing (e.g. filename-resolution) context via [linesWithParents].
 */
class PatchTrace(
  val label: String,
  private val delegate: Logger? = null,
  private val parent: PatchTrace? = null,
  private val maxEntries: Int = 2000,
  private val maxMessageLength: Int = 4000,
) {

  data class Entry(
    val offsetMs: Long,
    val level: String,
    val message: String,
    val error: Throwable? = null,
  ) {
    override fun toString(): String = buildString {
      append('+').append(offsetMs).append("ms ").append(level).append(' ').append(message)
      error?.let { append(" | ").append(it::class.java.simpleName).append(": ").append(it.message) }
    }
  }

  private val startedAt: Instant = Instant.now()
  private val records = CopyOnWriteArrayList<Entry>()

  /** Creates a nested trace which shares the slf4j delegate and keeps this trace as context. */
  fun child(label: String): PatchTrace = PatchTrace(label, delegate, this, maxEntries, maxMessageLength)

  fun trace(message: String, vararg args: Any?) {
    val (text, error) = format(message, args)
    if (error != null) delegate?.trace(text, error) else delegate?.trace(text)
    record("TRACE", text, error)
  }

  fun debug(message: String, vararg args: Any?) {
    val (text, error) = format(message, args)
    if (error != null) delegate?.debug(text, error) else delegate?.debug(text)
    record("DEBUG", text, error)
  }

  fun info(message: String, vararg args: Any?) {
    val (text, error) = format(message, args)
    if (error != null) delegate?.info(text, error) else delegate?.info(text)
    record("INFO", text, error)
  }

  fun warn(message: String, vararg args: Any?) {
    val (text, error) = format(message, args)
    if (error != null) delegate?.warn(text, error) else delegate?.warn(text)
    record("WARN", text, error)
  }

  fun error(message: String, vararg args: Any?) {
    val (text, error) = format(message, args)
    if (error != null) delegate?.error(text, error) else delegate?.error(text)
    record("ERROR", text, error)
  }

  /** Entries recorded by this trace only. */
  fun entries(): List<Entry> = records.toList()

  /** Rendered lines for this trace only. */
  fun lines(): List<String> = records.map { "[$label] $it" }

  /** Rendered lines for all ancestor traces followed by this trace's own lines. */
  fun linesWithParents(): List<String> = (parent?.linesWithParents() ?: emptyList()) + lines()

  /** Convenience single-string rendering, including ancestors. */
  fun renderText(): String = linesWithParents().joinToString("\n")

  fun isEmpty(): Boolean = records.isEmpty() && (parent?.isEmpty() ?: true)

  private fun record(level: String, text: String, error: Throwable?) {
    if (records.size >= maxEntries) return
    records.add(
      Entry(
        offsetMs = Duration.between(startedAt, Instant.now()).toMillis(),
        level = level,
        message = if (text.length > maxMessageLength) text.take(maxMessageLength) + "…(truncated)" else text,
        error = error
      )
    )
  }

  /** Applies slf4j-style `{}` substitution, extracting a trailing [Throwable] argument if present. */
  private fun format(message: String, args: Array<out Any?>): Pair<String, Throwable?> {
    if (args.isEmpty()) return message to null
    val placeholders = countPlaceholders(message)
    val error = if (args.size > placeholders) args.last() as? Throwable else null
    val substitutions = if (error != null) args.size - 1 else args.size
    val sb = StringBuilder(message.length + 32)
    var argIndex = 0
    var i = 0
    while (i < message.length) {
      if (message[i] == '{' && i + 1 < message.length && message[i + 1] == '}' && argIndex < substitutions) {
        sb.append(stringify(args[argIndex++]))
        i += 2
      } else {
        sb.append(message[i])
        i++
      }
    }
    while (argIndex < substitutions) sb.append(' ').append(stringify(args[argIndex++]))
    return sb.toString() to error
  }

  private fun countPlaceholders(message: String): Int {
    var count = 0
    var i = 0
    while (i < message.length - 1) {
      if (message[i] == '{' && message[i + 1] == '}') {
        count++
        i += 2
      } else i++
    }
    return count
  }

  private fun stringify(value: Any?): String = when (value) {
    null -> "null"
    is Throwable -> "${value::class.java.simpleName}: ${value.message}"
    is Array<*> -> value.joinToString(", ", "[", "]") { stringify(it) }
    else -> value.toString()
  }
}