package com.simiacryptus.cognotik.webui.servlet.util

/**
 * Minimal, dependency-free JSON reader/writer used by the FS API v1 endpoints.
 *
 * Deliberately tiny: the wire protocol only needs objects, arrays, strings,
 * numbers, booleans and null. File bytes never travel through here except in
 * /batch (where they are explicitly base64).
 */
object MiniJson {

  /** Wrapper that splices already-serialized JSON into an output document. */
  class Raw(val json: String)

  fun parse(text: String): Any? {
    val parser = Parser(text)
    val value = parser.value()
    parser.ws()
    if (!parser.done()) throw IllegalArgumentException("Trailing JSON content at offset ${parser.pos}")
    return value
  }

  @Suppress("UNCHECKED_CAST")
  fun parseObject(text: String?): Map<String, Any?> {
    if (text.isNullOrBlank()) return emptyMap()
    val value = parse(text)
    return (value as? Map<String, Any?>) ?: throw IllegalArgumentException("Expected a JSON object body")
  }

  fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

  fun string(map: Map<String, Any?>, key: String): String? = when (val v = map[key]) {
    null -> null
    is String -> v
    else -> v.toString()
  }

  fun boolean(map: Map<String, Any?>, key: String, default: Boolean = false): Boolean = when (val v = map[key]) {
    null -> default
    is Boolean -> v
    is String -> v.equals("true", ignoreCase = true) || v == "1"
    is Number -> v.toDouble() != 0.0
    else -> default
  }

  fun long(map: Map<String, Any?>, key: String): Long? = when (val v = map[key]) {
    null -> null
    is Number -> v.toLong()
    is String -> v.trim().toLongOrNull()
    else -> null
  }

  fun int(map: Map<String, Any?>, key: String): Int? = long(map, key)?.toInt()

  @Suppress("UNCHECKED_CAST")
  fun list(map: Map<String, Any?>, key: String): List<Any?> = when (val v = map[key]) {
    null -> emptyList()
    is List<*> -> v
    else -> listOf(v)
  }

  fun escape(value: String): String {
    val sb = StringBuilder(value.length + 2)
    sb.append('"')
    for (ch in value) {
      when {
        ch == '"' -> sb.append("\\\"")
        ch == '\\' -> sb.append("\\\\")
        ch == '\n' -> sb.append("\\n")
        ch == '\r' -> sb.append("\\r")
        ch == '\t' -> sb.append("\\t")
        ch == '\b' -> sb.append("\\b")
        ch == '\u000C' -> sb.append("\\f")
        ch.code < 0x20 || ch.code == 0x7F -> sb.append("\\u").append(String.format("%04x", ch.code))
        else -> sb.append(ch)
      }
    }
    sb.append('"')
    return sb.toString()
  }

  private fun write(sb: StringBuilder, value: Any?) {
    when (value) {
      null -> sb.append("null")
      is Raw -> sb.append(value.json)
      is String -> sb.append(escape(value))
      is Boolean -> sb.append(value.toString())
      is Int, is Long, is Short, is Byte -> sb.append(value.toString())
      is Float -> write(sb, value.toDouble())
      is Double -> {
        if (!value.isFinite()) sb.append("null")
        else if (value == Math.floor(value) && Math.abs(value) < 1e15) sb.append(value.toLong().toString())
        else sb.append(value.toString())
      }

      is Map<*, *> -> {
        sb.append('{')
        var first = true
        for ((k, v) in value) {
          if (!first) sb.append(',')
          first = false
          sb.append(escape(k?.toString() ?: "null")).append(':')
          write(sb, v)
        }
        sb.append('}')
      }

      is Array<*> -> write(sb, value.toList())
      is Iterable<*> -> {
        sb.append('[')
        var first = true
        for (v in value) {
          if (!first) sb.append(',')
          first = false
          write(sb, v)
        }
        sb.append(']')
      }

      else -> sb.append(escape(value.toString()))
    }
  }

  private class Parser(private val src: String) {
    var pos = 0
    fun done() = pos >= src.length
    fun ws() {
      while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    fun value(): Any? {
      ws()
      if (done()) throw IllegalArgumentException("Unexpected end of JSON input")
      return when (src[pos]) {
        '{' -> obj()
        '[' -> arr()
        '"' -> str()
        't' -> literal("true", true)
        'f' -> literal("false", false)
        'n' -> literal("null", null)
        else -> num()
      }
    }

    private fun literal(text: String, value: Any?): Any? {
      if (!src.startsWith(text, pos)) throw IllegalArgumentException("Invalid JSON literal at offset $pos")
      pos += text.length
      return value
    }

    private fun obj(): Map<String, Any?> {
      expect('{')
      val map = LinkedHashMap<String, Any?>()
      ws()
      if (peek() == '}') {
        pos++; return map
      }
      while (true) {
        ws()
        val key = str()
        ws()
        expect(':')
        map[key] = value()
        ws()
        when (peek()) {
          ',' -> pos++
          '}' -> {
            pos++; return map
          }

          else -> throw IllegalArgumentException("Expected ',' or '}' at offset $pos")
        }
      }
    }

    private fun arr(): List<Any?> {
      expect('[')
      val list = ArrayList<Any?>()
      ws()
      if (peek() == ']') {
        pos++; return list
      }
      while (true) {
        list.add(value())
        ws()
        when (peek()) {
          ',' -> pos++
          ']' -> {
            pos++; return list
          }

          else -> throw IllegalArgumentException("Expected ',' or ']' at offset $pos")
        }
      }
    }

    private fun str(): String {
      expect('"')
      val sb = StringBuilder()
      while (true) {
        if (done()) throw IllegalArgumentException("Unterminated JSON string")
        when (val ch = src[pos++]) {
          '"' -> return sb.toString()
          '\\' -> {
            if (done()) throw IllegalArgumentException("Unterminated JSON escape")
            when (val esc = src[pos++]) {
              '"' -> sb.append('"')
              '\\' -> sb.append('\\')
              '/' -> sb.append('/')
              'b' -> sb.append('\b')
              'f' -> sb.append('\u000C')
              'n' -> sb.append('\n')
              'r' -> sb.append('\r')
              't' -> sb.append('\t')
              'u' -> {
                if (pos + 4 > src.length) throw IllegalArgumentException("Truncated unicode escape")
                sb.append(src.substring(pos, pos + 4).toInt(16).toChar())
                pos += 4
              }

              else -> throw IllegalArgumentException("Invalid escape '\\$esc' at offset ${pos - 1}")
            }
          }

          else -> sb.append(ch)
        }
      }
    }

    private fun num(): Any {
      val start = pos
      if (peek() == '-' || peek() == '+') pos++
      var isDouble = false
      while (!done()) {
        val c = src[pos]
        if (c in '0'..'9') pos++
        else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
          isDouble = true; pos++
        } else break
      }
      val text = src.substring(start, pos)
      if (text.isEmpty()) throw IllegalArgumentException("Invalid JSON value at offset $start")
      return if (isDouble) text.toDouble() else (text.toLongOrNull() ?: text.toDouble())
    }

    private fun peek(): Char = if (done()) '\u0000' else src[pos]
    private fun expect(ch: Char) {
      ws()
      if (done() || src[pos] != ch) throw IllegalArgumentException("Expected '$ch' at offset $pos")
      pos++
    }
  }
}